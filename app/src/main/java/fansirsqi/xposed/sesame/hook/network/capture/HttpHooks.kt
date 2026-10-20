package fansirsqi.xposed.sesame.hook.network.capture

import dalvik.system.BaseDexClassLoader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import fansirsqi.xposed.sesame.hook.network.capture.PendingRegistry.HttpBodyObservation
import fansirsqi.xposed.sesame.hook.network.capture.PendingRegistry.PendingHostRequest
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** HTTP 侧 hook 面：小程序 AsyncRequestHandler / XRiver / H5NetworkManager→Future→取流观察。 */
internal object HttpHooks {
    private const val TAG = "HttpHooks"

    private const val MINI_REQUEST_CLASS = "com.alipay.android.phone.wallet.aompnetwork.request.AsyncRequestHandler"
    private const val MINI_REQUEST_MODEL = "com.alipay.android.phone.wallet.aompnetwork.request.RequestModel"
    private const val MINI_PARAMS_MODEL = "com.alipay.android.phone.wallet.aompnetwork.request.ParamsModel"
    private const val MINI_RESPONSE_CLASS = "com.alipay.android.phone.event.ariver.network.HttpRequestResponseTrigger"
    private const val H5_NETWORK_CLASS = "com.alipay.mobile.common.transport.h5.H5NetworkManager"
    private const val XRIVER_CLASS = "com.alibaba.xriver.android.proxy.CRVRequestProxy"
    private const val ARIVER_PAGE_CLASS = "com.alibaba.ariver.app.api.Page"
    private const val FASTJSON_CLASS = "com.alibaba.fastjson.JSONObject"
    private const val CRV_PAGE_CLASS = "com.alibaba.xriver.android.node.CRVPage"

    private val classLoaderListenerInstalled = AtomicBoolean(false)

    private val methodHooks = ConcurrentHashMap<java.lang.reflect.Method, Boolean>()
    private val miniClassInstallations = ConcurrentHashMap<Class<*>, Boolean>()
    private val miniClassFailures = ConcurrentHashMap.newKeySet<Class<*>>()

    private val responseHookCalls = ThreadLocal<IdentityHashMap<Any, Boolean>>()
    private val bodyAccessorCalls = ThreadLocal<IdentityHashMap<PendingHostRequest, Boolean>>()

    private class CaptureSlot : ThreadLocal<ArrayDeque<Any?>>() {
        override fun initialValue(): ArrayDeque<Any?> = ArrayDeque()
    }

    private val hookSlot = CaptureSlot()

    fun installMiniHttpHooks(classLoader: ClassLoader): Boolean {
        var installed = false
        listOf(MINI_REQUEST_CLASS, MINI_RESPONSE_CLASS, H5_NETWORK_CLASS, XRIVER_CLASS).forEach { name ->
            runCatching {
                if (installMiniHttpClass(Class.forName(name, false, classLoader))) installed = true
            }.onFailure {
                runCatching { Log.runtime(TAG, "HTTP 入口暂未安装: $name ${it.message}") }
            }
        }
        if (!installed) {
            installed = installClassLoaderListener(classLoader)
        }
        return installed
    }

    private fun installClassLoaderListener(classLoader: ClassLoader): Boolean {
        if (!classLoaderListenerInstalled.compareAndSet(false, true)) return true
        val loaderClasses = linkedSetOf<Class<*>>(ClassLoader::class.java, BaseDexClassLoader::class.java)
        var current: Class<*>? = classLoader.javaClass
        while (current != null && ClassLoader::class.java.isAssignableFrom(current)) {
            loaderClasses.add(current)
            current = current.superclass
        }
        val targetNames = setOf(MINI_REQUEST_CLASS, MINI_RESPONSE_CLASS, H5_NETWORK_CLASS, XRIVER_CLASS)
        var installed = false
        loaderClasses.forEach { loaderClass ->
            runCatching {
                loaderClass.declaredMethods.filter { method ->
                    method.name == "loadClass" && method.parameterTypes.firstOrNull() == String::class.java
                }.forEach { method ->
                    if (methodHooks.putIfAbsent(method, false) != null) return@forEach
                    runCatching {
                        method.isAccessible = true
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val result = param.result as? Class<*> ?: return
                                if (result.name in targetNames) {
                                    runCatching { installMiniHttpClass(result) }
                                        .onFailure { Log.runtime(TAG, "延迟安装 HTTP 抓包失败: ${it.message}") }
                                }
                            }
                        })
                        methodHooks[method] = true
                        installed = true
                    }.onFailure {
                        methodHooks.remove(method, false)
                    }
                }
            }
        }
        if (installed) Log.runtime(TAG, "已安装 HTTP 类加载监听")
        return installed
    }

    private fun installMiniHttpClass(targetClass: Class<*>): Boolean {
        if (miniClassFailures.contains(targetClass)) return false
        when (targetClass.name) {
            H5_NETWORK_CLASS -> return installH5NetworkHook(targetClass)
            XRIVER_CLASS -> return installXriverHttpHooks(targetClass)
        }
        val isRequest = when (targetClass.name) {
            MINI_REQUEST_CLASS -> true
            MINI_RESPONSE_CLASS -> false
            else -> return false
        }
        if (miniClassInstallations.putIfAbsent(targetClass, true) == true) return true
        val methodName = if (isRequest) "processAsyncRequest" else "onHandleResponse"
        val methods = linkedMapOf<List<Class<*>>, java.lang.reflect.Method>()
        var owner: Class<*>? = targetClass
        while (owner != null && owner != Any::class.java) {
            owner.declaredMethods.filter {
                it.name == methodName && !java.lang.reflect.Modifier.isAbstract(it.modifiers)
            }.forEach { methods.putIfAbsent(it.parameterTypes.toList(), it) }
            owner = owner.superclass
        }
        if (methods.isEmpty()) {
            miniClassFailures.add(targetClass)
            return false
        }
        var anyInstalled = false
        methods.values.forEach { method ->
            if (hookMiniHttpMethod(targetClass, method, isRequest)) anyInstalled = true
        }
        return anyInstalled
    }

    private fun hookMiniHttpMethod(targetClass: Class<*>, method: java.lang.reflect.Method, isRequest: Boolean): Boolean {
        if (methodHooks.putIfAbsent(method, false) != null) return methodHooks[method] == true
        return runCatching {
            method.isAccessible = true
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    var context: RpcCallContext? = null
                    runCatching {
                        val args = param.args.toList()
                        if (isRequest) {
                            if (RpcTrafficCapture.isCaptureEnabled() &&
                                RpcTrafficCapture.isSourceEnabled(RpcTrafficCapture.TrafficSource.MINI_HTTP)
                            ) {
                                val callbackIndex = method.parameterTypes.indexOfFirst { it.name == RpcHooks.BRIDGE_CALLBACK_IFACE }
                                val callback = param.args.getOrNull(callbackIndex)
                                val dataArguments = method.parameterTypes.indices.filter { index ->
                                    val parameter = method.parameterTypes[index]
                                    parameter.name == MINI_REQUEST_MODEL || parameter.name == MINI_PARAMS_MODEL
                                }.toSet()
                                val payload = HttpSnapshot.arguments(method, args, dataArguments)
                                val modelIndex = method.parameterTypes.indexOfFirst { it.name == MINI_REQUEST_MODEL }
                                val transportRequest = param.args.getOrNull(modelIndex)?.let { model ->
                                    HttpSnapshot.findField(model.javaClass, "h5HttpUrlRequest")?.get(model)
                                }
                                val completed = transportRequest != null && PendingRegistry.isCompleted(transportRequest)
                                if (!completed) {
                                    val existing = transportRequest?.let { PendingRegistry.peek(it) }
                                    val pending = existing ?: PendingHostRequest(
                                        requestId = RpcTrafficCapture.newRequestId(),
                                        method = "${targetClass.name}#${method.name}",
                                        requestPayload = payload,
                                        responseBeforeRequest = callback?.let { readAriverResponseField(it) },
                                        startedAtMs = System.currentTimeMillis(),
                                        source = RpcTrafficCapture.TrafficSource.MINI_HTTP
                                    ).also { RpcTrafficCapture.recordHostRequest(it) }
                                    transportRequest?.let { PendingRegistry.put(it, pending) }
                                    if (callback != null) {
                                        RpcHooks.installAriverResponseClass(callback.javaClass)
                                        PendingRegistry.put(callback, pending)
                                    }
                                    context = RpcCallContext(pending, callback)
                                }
                            }
                        } else {
                            if (RpcTrafficCapture.isCaptureEnabled() &&
                                RpcTrafficCapture.isSourceEnabled(RpcTrafficCapture.TrafficSource.MINI_HTTP)
                            ) {
                                RpcTrafficCapture.emitEvent(
                                    RpcTrafficCapture.RpcTrafficEvent(
                                        phase = RpcTrafficCapture.TrafficPhase.RESPONSE,
                                        source = RpcTrafficCapture.TrafficSource.MINI_HTTP,
                                        method = param.args.getOrNull(0) as? String ?: "${targetClass.name}#${method.name}",
                                        payload = HttpSnapshot.arguments(method, args),
                                        correlationStatus = "UNMATCHED"
                                    )
                                )
                            }
                        }
                    }
                    hookSlot.get()!!.addLast(context)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = hookSlot.get()!!.pollLast() as? RpcCallContext ?: return
                    val pending = context.pending ?: return
                    runCatching {
                        if (param.hasThrowable()) {
                            RpcTrafficCapture.recordHostError(pending, "captured method threw ${param.throwable.javaClass.simpleName}")
                        } else {
                            context.callback?.let { RpcTrafficCapture.tryRecordResponseFromField(it) }
                        }
                    }
                }
            })
            methodHooks[method] = true
            Log.runtime(TAG, "已安装小程序 HTTP ${if (isRequest) "请求" else "响应"}抓包: $method; loader=${targetClass.classLoader}")
            true
        }.onFailure {
            methodHooks.remove(method, false)
            runCatching { Log.runtime(TAG, "安装小程序 HTTP 抓包失败: $method ${it.stackTraceToString()}") }
        }.getOrDefault(false)
    }

    private fun installXriverHttpHooks(targetClass: Class<*>): Boolean {
        if (miniClassInstallations.putIfAbsent(targetClass, true) == true) return true
        return try {
            val loader = targetClass.classLoader
            val methods = listOf(
                targetClass.getDeclaredMethod(
                    "handleRequest",
                    Class.forName(ARIVER_PAGE_CLASS, false, loader),
                    Class.forName(FASTJSON_CLASS, false, loader),
                    java.lang.Long.TYPE
                ),
                targetClass.getDeclaredMethod(
                    "handleResponse",
                    String::class.java,
                    String::class.java,
                    Class.forName(CRV_PAGE_CLASS, false, loader)
                )
            )
            methods.forEach { method ->
                val isRequest = method.name == "handleRequest"
                val expectedReturnType = if (isRequest) java.lang.Boolean.TYPE else java.lang.Void.TYPE
                require(java.lang.reflect.Modifier.isStatic(method.modifiers) && method.returnType == expectedReturnType) {
                    "XRiver HTTP 签名不符: $method"
                }
                if (methodHooks.putIfAbsent(method, false) != null) return@forEach
                runCatching {
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            var event: RpcTrafficCapture.RpcTrafficEvent? = null
                            runCatching {
                                if (RpcTrafficCapture.isCaptureEnabled() &&
                                    RpcTrafficCapture.isSourceEnabled(RpcTrafficCapture.TrafficSource.MINI_HTTP)
                                ) {
                                    event = RpcTrafficCapture.RpcTrafficEvent(
                                        phase = if (isRequest) RpcTrafficCapture.TrafficPhase.REQUEST else RpcTrafficCapture.TrafficPhase.RESPONSE,
                                        source = RpcTrafficCapture.TrafficSource.MINI_HTTP,
                                        method = "${targetClass.name}#${method.name}",
                                        requestId = if (isRequest) RpcTrafficCapture.newRequestId() else null,
                                        payload = HttpSnapshot.arguments(method, param.args.toList()),
                                        note = if (isRequest) "response_correlation_unavailable" else null,
                                        correlationStatus = if (isRequest) null else "UNMATCHED"
                                    ).also { RpcTrafficCapture.emitEvent(it) }
                                }
                            }
                            if (isRequest) hookSlot.get()!!.addLast(event)
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!isRequest) return
                            val event = hookSlot.get()!!.pollLast() as? RpcTrafficCapture.RpcTrafficEvent ?: return
                            if (param.hasThrowable()) {
                                runCatching {
                                    RpcTrafficCapture.emitEvent(
                                        event.copy(
                                            phase = RpcTrafficCapture.TrafficPhase.ERROR,
                                            payload = null,
                                            note = "${param.throwable.javaClass.name}: ${param.throwable.message.orEmpty()}"
                                        )
                                    )
                                }
                            }
                        }
                    })
                    methodHooks[method] = true
                    Log.runtime(TAG, "已安装 XRiver HTTP 抓包: $method; loader=$loader")
                }.onFailure {
                    methodHooks.remove(method, false)
                    runCatching { Log.runtime(TAG, "安装 XRiver HTTP 抓包失败: $method ${it.stackTraceToString()}") }
                }
            }
            methods.all { methodHooks[it] == true }
        } catch (error: Throwable) {
            miniClassInstallations.remove(targetClass, true)
            runCatching { Log.runtime(TAG, "安装 XRiver HTTP 抓包失败: ${targetClass.name} ${error.stackTraceToString()}") }
            false
        }
    }

    private fun installH5NetworkHook(targetClass: Class<*>): Boolean {
        if (miniClassInstallations.putIfAbsent(targetClass, true) == true) return true
        return try {
            val methods = linkedMapOf<List<Class<*>>, java.lang.reflect.Method>()
            var owner: Class<*>? = targetClass
            while (owner != null && owner != Any::class.java) {
                owner.declaredMethods.filter {
                    it.name == "enqueue" && it.parameterCount == 1 &&
                        Future::class.java.isAssignableFrom(it.returnType) && !java.lang.reflect.Modifier.isAbstract(it.modifiers)
                }.forEach { methods.putIfAbsent(it.parameterTypes.toList(), it) }
                owner = owner.superclass
            }
            if (methods.isEmpty()) error("${targetClass.name}#enqueue 未找到 Request -> Future 签名")
            methods.values.forEach { method ->
                if (methodHooks.putIfAbsent(method, false) != null) return@forEach
                runCatching {
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            var pending: PendingHostRequest? = null
                            runCatching {
                                val requestObject = param.args.getOrNull(0)
                                if (requestObject != null && RpcTrafficCapture.isCaptureEnabled() &&
                                    RpcTrafficCapture.isSourceEnabled(RpcTrafficCapture.TrafficSource.MINI_HTTP)
                                ) {
                                    val payload = HttpSnapshot.arguments(method, param.args.toList(), setOf(0))
                                    val httpMethod = HttpSnapshot.findField(requestObject.javaClass, "requestMethod")
                                        ?.get(requestObject) as? String
                                    val existing = PendingRegistry.peek(requestObject)
                                    val request = existing ?: PendingHostRequest(
                                        requestId = RpcTrafficCapture.newRequestId(),
                                        method = "${targetClass.name}#enqueue",
                                        requestPayload = payload,
                                        startedAtMs = System.currentTimeMillis(),
                                        source = RpcTrafficCapture.TrafficSource.MINI_HTTP
                                    ).also { RpcTrafficCapture.recordHostRequest(it) }
                                    if (existing != null) {
                                        RpcTrafficCapture.emitEvent(
                                            RpcTrafficCapture.RpcTrafficEvent(
                                                phase = RpcTrafficCapture.TrafficPhase.REQUEST,
                                                source = request.source,
                                                method = request.method,
                                                requestId = request.requestId,
                                                payload = payload,
                                                note = "transport_request",
                                                correlationStatus = "MATCHED"
                                            )
                                        )
                                    }
                                    request.httpMethod = httpMethod
                                    PendingRegistry.put(requestObject, request)
                                    pending = request
                                }
                            }
                            hookSlot.get()!!.addLast(pending)
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            val pending = hookSlot.get()!!.pollLast() as? PendingHostRequest ?: return
                            runCatching {
                                if (param.hasThrowable()) {
                                    RpcTrafficCapture.recordHostError(
                                        pending,
                                        "transport enqueue threw ${param.throwable.javaClass.simpleName}"
                                    )
                                } else {
                                    val result = param.result
                                    if (result is Future<*>) {
                                        installHttpFutureHooks(result.javaClass)
                                        PendingRegistry.put(result, pending)
                                    } else {
                                        RpcTrafficCapture.recordHostError(pending, "capture_future_missing", "NO_CALLBACK")
                                    }
                                }
                            }
                        }
                    })
                    methodHooks[method] = true
                    Log.runtime(TAG, "已安装公共 HTTP 请求抓包: $method; loader=${targetClass.classLoader}")
                }.onFailure {
                    methodHooks.remove(method, false)
                    runCatching { Log.runtime(TAG, "安装公共 HTTP 请求抓包失败: $method ${it.stackTraceToString()}") }
                }
            }
            methods.values.all { methodHooks[it] == true }
        } catch (error: Throwable) {
            miniClassInstallations.remove(targetClass, true)
            runCatching { Log.runtime(TAG, "安装公共 HTTP 请求抓包失败: ${targetClass.name} ${error.stackTraceToString()}") }
            false
        }
    }

    private fun installHttpFutureHooks(futureClass: Class<*>) {
        val methods = linkedMapOf<Pair<String, List<Class<*>>>, java.lang.reflect.Method>()
        var owner: Class<*>? = futureClass
        while (owner != null && owner != Any::class.java) {
            owner.declaredMethods.filter {
                !java.lang.reflect.Modifier.isAbstract(it.modifiers) && !java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    ((it.name == "get" && (it.parameterCount == 0 ||
                        (it.parameterTypes.toList() == listOf(Long::class.javaPrimitiveType, TimeUnit::class.java)))) ||
                        (it.name == "cancel" && it.parameterTypes.toList() == listOf(Boolean::class.javaPrimitiveType)))
            }.forEach { methods.putIfAbsent(it.name to it.parameterTypes.toList(), it) }
            owner = owner.superclass
        }
        if (methods.isEmpty()) return
        methods.values.forEach { method ->
            if (methodHooks.putIfAbsent(method, false) != null) return@forEach
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        var observed: Triple<PendingHostRequest?, Boolean, Boolean>? = null
                        runCatching {
                            val future = param.thisObject
                            if (future != null) {
                                val links = PendingRegistry.linksOf(future)
                                if (links != null && !links.second) {
                                    val calls = responseHookCalls.get()
                                        ?: IdentityHashMap<Any, Boolean>().also(responseHookCalls::set)
                                    if (calls.put(future, true) == null) observed = links
                                }
                            }
                        }
                        hookSlot.get()!!.addLast(observed)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val observed = hookSlot.get()!!.pollLast()
                            as? Triple<PendingHostRequest?, Boolean, Boolean> ?: return
                        val future = param.thisObject ?: return
                        val pending = observed.first
                        try {
                            if (param.hasThrowable()) {
                                val error = param.throwable
                                val waitOnly = error is InterruptedException || error is TimeoutException
                                if (pending != null && method.name == "get" && !waitOnly) {
                                    RpcTrafficCapture.recordHostError(
                                        pending,
                                        error.stackTraceToString(),
                                        if (error is CancellationException) "CANCELLED" else null
                                    )
                                } else {
                                    RpcTrafficCapture.emitEvent(
                                        RpcTrafficCapture.RpcTrafficEvent(
                                            phase = RpcTrafficCapture.TrafficPhase.ERROR,
                                            source = RpcTrafficCapture.TrafficSource.MINI_HTTP,
                                            method = pending?.method ?: "${futureClass.name}#${method.name}",
                                            requestId = pending?.requestId,
                                            note = error.stackTraceToString(),
                                            correlationStatus = when {
                                                error is InterruptedException -> "WAIT_INTERRUPTED"
                                                error is TimeoutException -> "WAIT_TIMEOUT"
                                                method.name == "cancel" -> "CANCEL_FAILED"
                                                else -> "UNMATCHED"
                                            }
                                        )
                                    )
                                }
                            } else {
                                val completed = PendingRegistry.isCompleted(future)
                                if (!completed) {
                                    if (method.name == "cancel") {
                                        if (param.result == true && pending != null) {
                                            RpcTrafficCapture.recordHostError(pending, "request_cancelled", "CANCELLED")
                                        }
                                    } else if (pending != null) {
                                        observeHttpResponseBody(pending, param.result)
                                    } else {
                                        RpcTrafficCapture.emitEvent(
                                            RpcTrafficCapture.RpcTrafficEvent(
                                                phase = RpcTrafficCapture.TrafficPhase.RESPONSE,
                                                source = RpcTrafficCapture.TrafficSource.MINI_HTTP,
                                                method = "${futureClass.name}#get",
                                                payload = HttpSnapshot.snapshot(param.result, IdentityHashMap(), expandFields = true).toString(),
                                                note = "response_correlation_unavailable",
                                                correlationStatus = if (observed.third) "AMBIGUOUS" else "UNMATCHED"
                                            )
                                        )
                                    }
                                }
                            }
                        } finally {
                            val calls = responseHookCalls.get()
                            calls?.remove(future)
                            if (calls?.isEmpty() == true) responseHookCalls.remove()
                        }
                    }
                })
                methodHooks[method] = true
                Log.runtime(TAG, "已安装 HTTP Future 观察: $method; loader=${futureClass.classLoader}")
            }.onFailure {
                methodHooks.remove(method, false)
                runCatching { Log.runtime(TAG, "安装 HTTP Future 观察失败: $method ${it.stackTraceToString()}") }
            }
        }
    }

    private fun observeHttpResponseBody(pending: PendingHostRequest, value: Any?) {
        if (!RpcTrafficCapture.isCaptureEnabled()) return
        val calls = bodyAccessorCalls.get() ?: IdentityHashMap<PendingHostRequest, Boolean>().also(bodyAccessorCalls::set)
        val alreadyObserving = calls.put(pending, true) != null
        try {
            val streams = mutableListOf<InputStream>()
            val bodyObjects = mutableListOf<Any>()
            val snapshot = HttpSnapshot.snapshot(value, IdentityHashMap(), streams, expandFields = true, bodyObjects = bodyObjects)
            val status = ((snapshot as? JSONObject)?.optJSONObject("properties")
                ?.optJSONObject("getStatusLine")?.optJSONObject("properties")
                ?.opt("getStatusCode") as? Number)?.toInt()
            val noBody = (pending.httpMethod == "HEAD" && value != null) || status == 204 || status == 205 || status == 304 ||
                (status != null && status in 100..199)
            val buffered = value is ByteArray || value is String || (value is ByteBuffer && value.position() == 0)
            val terminal = noBody || buffered
            val note = when {
                noBody -> "network_response_has_no_body"
                buffered -> "response_body_buffered"
                else -> "response_body_pending"
            }
            val payload = (snapshot as? JSONObject ?: JSONObject().put("body", snapshot))
                .put("complete", if (terminal) true else JSONObject.NULL).toString()
            if (!RpcTrafficCapture.recordHostResponse(pending, payload, terminal, note)) {
                RpcTrafficCapture.emitEvent(
                    RpcTrafficCapture.RpcTrafficEvent(
                        phase = RpcTrafficCapture.TrafficPhase.RESPONSE,
                        source = pending.source,
                        method = pending.method,
                        requestId = pending.requestId,
                        payload = payload,
                        note = "capture_context_expired; $note",
                        correlationStatus = "LATE_RESPONSE"
                    )
                )
                return
            }
            if (buffered && !noBody) return
            if (!noBody && streams.isEmpty()) {
                if (value != null && value !is InputStream) bodyObjects.add(value)
                val observedObjects = IdentityHashMap<Any, Boolean>()
                bodyObjects.forEach { bodyObject ->
                    if (observedObjects.put(bodyObject, true) == null) {
                        PendingRegistry.put(bodyObject, pending)
                        installHttpBodyAccessorHooks(bodyObject.javaClass)
                    }
                }
            }
            val observedStreams = IdentityHashMap<InputStream, Boolean>()
            streams.forEach { stream ->
                if (observedStreams.put(stream, true) == null) {
                    val installed = installHttpStreamHooks(stream.javaClass)
                    if (!installed) {
                        RpcTrafficCapture.emitEvent(
                            RpcTrafficCapture.RpcTrafficEvent(
                                phase = RpcTrafficCapture.TrafficPhase.ERROR,
                                source = pending.source,
                                method = pending.method,
                                requestId = pending.requestId,
                                note = "stream_observer_incomplete: ${stream.javaClass.name}",
                                correlationStatus = "BODY_OBSERVER_UNAVAILABLE"
                            )
                        )
                    }
                    if (!noBody) PendingRegistry.put(stream, pending)
                    PendingRegistry.bindBody(
                        stream,
                        HttpBodyObservation(pending, System.identityHashCode(stream), noBody).also {
                            it.incomplete = !installed
                        }
                    )
                }
            }
        } finally {
            if (!alreadyObserving) calls.remove(pending)
            if (calls.isEmpty()) bodyAccessorCalls.remove()
        }
    }

    private fun installHttpBodyAccessorHooks(bodyClass: Class<*>) {
        if (miniClassFailures.contains(bodyClass)) return
        val contracts = listOf("org.apache.http.HttpResponse", "org.apache.http.HttpEntity").mapNotNull { name ->
            runCatching { Class.forName(name, false, bodyClass.classLoader) }.getOrNull()
        }
        val methods = bodyClass.methods.filter { method ->
            !java.lang.reflect.Modifier.isStatic(method.modifiers) && !java.lang.reflect.Modifier.isAbstract(method.modifiers) &&
                method.parameterCount == 0 &&
                (InputStream::class.java.isAssignableFrom(method.returnType) ||
                    contracts.any { it.isAssignableFrom(method.returnType) })
        }
        if (methods.isEmpty()) {
            miniClassFailures.add(bodyClass)
            return
        }
        methods.forEach { method ->
            if (methodHooks.putIfAbsent(method, false) != null) return@forEach
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        var observing: PendingHostRequest? = null
                        runCatching {
                            val owner = param.thisObject
                            val pending = owner?.let { PendingRegistry.peek(it) }
                            if (owner != null && pending != null && RpcTrafficCapture.isCaptureEnabled()) {
                                if (!PendingRegistry.isObservingStream(pending)) {
                                    val calls = bodyAccessorCalls.get()
                                        ?: IdentityHashMap<PendingHostRequest, Boolean>().also(bodyAccessorCalls::set)
                                    if (calls.put(pending, true) == null) observing = pending
                                }
                            }
                        }
                        hookSlot.get()!!.addLast(observing)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val pending = hookSlot.get()!!.pollLast() as? PendingHostRequest ?: return
                        try {
                            if (param.hasThrowable()) {
                                runCatching {
                                    RpcTrafficCapture.recordHostError(
                                        pending,
                                        "body_accessor_error: ${param.throwable.stackTraceToString()}",
                                        "BODY_INCOMPLETE"
                                    )
                                }
                            } else {
                                runCatching { observeHttpResponseBody(pending, param.result) }
                            }
                        } finally {
                            val calls = bodyAccessorCalls.get()
                            calls?.remove(pending)
                            if (calls?.isEmpty() == true) bodyAccessorCalls.remove()
                        }
                    }
                })
                methodHooks[method] = true
                Log.runtime(TAG, "已安装 HTTP 实体/取流观察: $method; loader=${bodyClass.classLoader}")
            }.onFailure {
                methodHooks.remove(method, false)
                runCatching { Log.runtime(TAG, "安装 HTTP 实体/取流观察失败: $method ${it.stackTraceToString()}") }
            }
        }
    }

    private fun installHttpStreamHooks(streamClass: Class<*>): Boolean {
        if (miniClassFailures.contains(streamClass)) return false
        val methods = streamClass.methods.filter { method ->
            if (java.lang.reflect.Modifier.isAbstract(method.modifiers) || java.lang.reflect.Modifier.isStatic(method.modifiers)) {
                return@filter false
            }
            val types = method.parameterTypes.toList()
            when (method.name) {
                "read" -> types.isEmpty() || types == listOf(ByteArray::class.java) ||
                    types == listOf(ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                "skip" -> types == listOf(Long::class.javaPrimitiveType)
                "mark" -> types == listOf(Int::class.javaPrimitiveType)
                "reset", "close" -> types.isEmpty()
                else -> false
            }
        }
        if (methods.isEmpty()) {
            miniClassFailures.add(streamClass)
            return false
        }
        methods.forEach { method ->
            if (methodHooks.putIfAbsent(method, false) != null) return@forEach
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        var body: HttpBodyObservation? = null
                        runCatching {
                            val stream = param.thisObject
                            if (stream != null) {
                                val observed = PendingRegistry.bodyOf(stream)
                                if (observed != null && RpcTrafficCapture.isCaptureEnabled()) {
                                    val calls = responseHookCalls.get()
                                        ?: IdentityHashMap<Any, Boolean>().also(responseHookCalls::set)
                                    if (calls.put(observed.pending, true) == null) body = observed
                                }
                            }
                        }
                        hookSlot.get()!!.addLast(body)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val body = hookSlot.get()!!.pollLast() as? HttpBodyObservation ?: return
                        try {
                            if (param.hasThrowable()) {
                                runCatching {
                                    synchronized(body) {
                                        body.incomplete = true
                                        RpcTrafficCapture.recordHttpBody(
                                            body,
                                            JSONObject().put("error", param.throwable.stackTraceToString()),
                                            "body_operation_error",
                                            method.name == "read" || method.name == "close"
                                        )
                                    }
                                }
                            } else {
                                runCatching {
                                    synchronized(body) {
                                        when (method.name) {
                                            "read" -> {
                                                val count = param.result as Int
                                                if (count < 0) {
                                                    RpcTrafficCapture.recordHttpBody(body, JSONObject(), "body_eof", true)
                                                } else if (method.parameterCount == 0 || count > 0) {
                                                    val bytes = if (method.parameterCount == 0) {
                                                        byteArrayOf(count.toByte())
                                                    } else {
                                                        val buffer = param.args[0] as ByteArray
                                                        val offset = if (method.parameterCount == 3) param.args[1] as Int else 0
                                                        buffer.copyOfRange(offset, offset + count)
                                                    }
                                                    val payload = JSONObject()
                                                        .put("offset", if (body.position < 0) JSONObject.NULL else body.position)
                                                        .put("bytes", HttpSnapshot.snapshot(bytes, IdentityHashMap()))
                                                    if (body.position >= 0) body.position += bytes.size
                                                    RpcTrafficCapture.recordHttpBody(body, payload, "body_chunk")
                                                }
                                            }
                                            "skip" -> {
                                                val skipped = param.result as Long
                                                if (skipped > 0) {
                                                    body.incomplete = true
                                                    val payload = JSONObject()
                                                        .put("offset", if (body.position < 0) JSONObject.NULL else body.position)
                                                        .put("skipped", skipped)
                                                    if (body.position >= 0) body.position += skipped
                                                    RpcTrafficCapture.recordHttpBody(body, payload, "body_skipped")
                                                }
                                            }
                                            "mark" -> body.markPosition = body.position.takeIf { it >= 0 }
                                            "reset" -> {
                                                val position = body.markPosition
                                                if (position == null) {
                                                    body.incomplete = true
                                                    body.position = -1L
                                                } else {
                                                    body.position = position
                                                }
                                                RpcTrafficCapture.recordHttpBody(
                                                    body,
                                                    JSONObject().put("offset", position ?: JSONObject.NULL),
                                                    "body_reset"
                                                )
                                            }
                                            "close" -> RpcTrafficCapture.recordHttpBody(
                                                body,
                                                JSONObject(),
                                                if (body.incomplete) "body_closed_before_eof" else "body_closed_completion_unknown",
                                                terminal = true,
                                                complete = null
                                            )
                                        }
                                    }
                                }
                            }
                        } finally {
                            val calls = responseHookCalls.get()
                            calls?.remove(body.pending)
                            if (calls?.isEmpty() == true) responseHookCalls.remove()
                        }
                    }
                })
                methodHooks[method] = true
            }.onFailure {
                methodHooks.remove(method, false)
            }
        }
        return methods.any { it.name == "read" } && methods.all { methodHooks[it] == true }
    }

    private fun readAriverResponseField(callback: Any): String? = runCatching {
        HttpSnapshot.findField(callback.javaClass, "mJSONResponse")?.get(callback)?.toString()
    }.getOrNull()

    private class RpcCallContext(val pending: PendingHostRequest?, val callback: Any?)
}
