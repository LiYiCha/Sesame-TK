package fansirsqi.xposed.sesame.hook.network.capture

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import fansirsqi.xposed.sesame.hook.network.capture.PendingRegistry.PendingHostRequest
import fansirsqi.xposed.sesame.util.Log
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** RPC 封装层 hook 面：RpcBridgeExtension / MtopBridgeExtention / BridgeCallback / H5RpcUtil。 */
internal object RpcHooks {
    private const val TAG = "RpcHooks"

    private const val JSON_OBJECT_NAME = "com.alibaba.fastjson.JSONObject"
    internal const val BRIDGE_CALLBACK_IFACE = "com.alibaba.ariver.engine.api.bridge.extension.BridgeCallback"
    private const val RPC_BRIDGE_CLASS = "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension"
    private const val MTOP_BRIDGE_CLASS = "com.alibaba.ariver.jsapi.mtop.MtopBridgeExtention"
    private const val DEFAULT_BRIDGE_CALLBACK_CLASS = "com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback"
    private const val BRIDGE_RESPONSE_CLASS = "com.alibaba.ariver.engine.api.bridge.extension.BridgeResponse"
    private const val H5_RPC_CLASS = "com.alipay.mobile.nebulaappproxy.api.rpc.H5RpcUtil"

    private val rpcBridgeHookInstalled = AtomicBoolean(false)
    private val mtopHookInstalled = AtomicBoolean(false)
    private val h5HookInstalled = AtomicBoolean(false)

    private val responseHookCalls = ThreadLocal<IdentityHashMap<Any, Boolean>>()

    private class CaptureSlot : ThreadLocal<ArrayDeque<Any?>>() {
        override fun initialValue(): ArrayDeque<Any?> = ArrayDeque()
    }

    private val hookSlot = CaptureSlot()

    fun installRpcBridgeHook(classLoader: ClassLoader): Boolean {
        if (rpcBridgeHookInstalled.get()) return true
        return runCatching {
            val bridgeClass = Class.forName(RPC_BRIDGE_CLASS, false, classLoader)
            val rpcMethod = HttpSnapshot.findMethod(bridgeClass, "rpc", 16, String::class.java)
                ?: error("RpcBridgeExtension#rpc 未找到")
            XposedBridge.hookMethod(rpcMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    var context: RpcCallContext? = null
                    runCatching {
                        val args = param.args
                        val methodName = args.getOrNull(0) as? String
                        val callback = args.getOrNull(15)
                        if (RpcTrafficCapture.isCaptureEnabled() &&
                            RpcTrafficCapture.isSourceEnabled(RpcTrafficCapture.TrafficSource.ARIVER_RPC) &&
                            !methodName.isNullOrBlank()
                        ) {
                            val pending = PendingHostRequest(
                                requestId = RpcTrafficCapture.newRequestId(),
                                method = methodName,
                                requestPayload = args.getOrNull(4)?.toString(),
                                responseBeforeRequest = callback?.let(::readAriverResponseField),
                                startedAtMs = System.currentTimeMillis(),
                                source = RpcTrafficCapture.TrafficSource.ARIVER_RPC
                            ).also { request ->
                                RpcTrafficCapture.recordHostRequest(request)
                                if (callback != null) {
                                    installAriverResponseClass(callback.javaClass)
                                    PendingRegistry.put(callback, request)
                                } else {
                                    RpcTrafficCapture.recordHostError(request, "callback_missing", "NO_CALLBACK")
                                }
                            }
                            context = RpcCallContext(pending, callback)
                        }
                    }
                    hookSlot.get()!!.addLast(context)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = hookSlot.get()!!.pollLast() as? RpcCallContext ?: return
                    val pending = context.pending ?: return
                    runCatching {
                        if (param.hasThrowable()) {
                            context.callback?.let { PendingRegistry.remove(it, pending.requestId) }
                            RpcTrafficCapture.recordHostError(
                                pending,
                                "RpcBridgeExtension.rpc threw ${param.throwable.javaClass.simpleName}"
                            )
                        } else {
                            context.callback?.let { RpcTrafficCapture.tryRecordResponseFromField(it) }
                        }
                    }
                }
            })
            rpcBridgeHookInstalled.set(true)
            Log.runtime(TAG, "已安装 RpcBridgeExtension#rpc 抓包")
            true
        }.onFailure {
            Log.runtime(TAG, "安装 RpcBridgeExtension#rpc 抓包失败: ${it.message}")
        }.getOrDefault(false)
    }

    fun installMtopHook(classLoader: ClassLoader): Boolean {
        if (mtopHookInstalled.get()) return true
        return runCatching {
            val bridgeClass = Class.forName(MTOP_BRIDGE_CLASS, false, classLoader)
            val jsonClass = Class.forName(JSON_OBJECT_NAME, false, classLoader)
            val callbackClass = Class.forName(BRIDGE_CALLBACK_IFACE, false, classLoader)
            val mtopMethod = (bridgeClass.methods.asSequence() + bridgeClass.declaredMethods.asSequence())
                .firstOrNull { method ->
                    method.name == "sendMtop" &&
                        method.parameterTypes.any(jsonClass::isAssignableFrom) &&
                        method.parameterTypes.any(callbackClass::isAssignableFrom)
                }?.apply { isAccessible = true }
                ?: error("MtopBridgeExtention#sendMtop 未找到可关联的请求签名")
            val payloadIndex = mtopMethod.parameterTypes.indexOfFirst(jsonClass::isAssignableFrom)
            val callbackIndex = mtopMethod.parameterTypes.indexOfFirst(callbackClass::isAssignableFrom)
            XposedBridge.hookMethod(mtopMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    var context: RpcCallContext? = null
                    runCatching {
                        val args = param.args
                        val callback = args.getOrNull(callbackIndex)
                        if (RpcTrafficCapture.isCaptureEnabled() &&
                            RpcTrafficCapture.isSourceEnabled(RpcTrafficCapture.TrafficSource.ARIVER_RPC)
                        ) {
                            val requestPayload = args.getOrNull(payloadIndex)?.toString()
                            val pending = PendingHostRequest(
                                requestId = RpcTrafficCapture.newRequestId(),
                                method = RpcTrafficCapture.extractMtopApiName(requestPayload) ?: "mtop.sendMtop",
                                requestPayload = requestPayload,
                                responseBeforeRequest = callback?.let(::readAriverResponseField),
                                startedAtMs = System.currentTimeMillis(),
                                source = RpcTrafficCapture.TrafficSource.ARIVER_RPC
                            ).also { request ->
                                RpcTrafficCapture.recordHostRequest(request)
                                if (callback != null) {
                                    installAriverResponseClass(callback.javaClass)
                                    PendingRegistry.put(callback, request)
                                } else {
                                    RpcTrafficCapture.recordHostError(request, "MtopBridgeExtention.sendMtop callback_missing", "NO_CALLBACK")
                                }
                            }
                            context = RpcCallContext(pending, callback)
                        }
                    }
                    hookSlot.get()!!.addLast(context)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = hookSlot.get()!!.pollLast() as? RpcCallContext ?: return
                    val pending = context.pending ?: return
                    runCatching {
                        if (param.hasThrowable()) {
                            context.callback?.let { PendingRegistry.remove(it, pending.requestId) }
                            RpcTrafficCapture.recordHostError(
                                pending,
                                "MtopBridgeExtention.sendMtop threw ${param.throwable.javaClass.simpleName}"
                            )
                        } else {
                            context.callback?.let { RpcTrafficCapture.tryRecordResponseFromField(it) }
                        }
                    }
                }
            })
            mtopHookInstalled.set(true)
            Log.runtime(TAG, "已安装 MtopBridgeExtention#sendMtop 抓包")
            true
        }.onFailure {
            Log.runtime(TAG, "安装 MtopBridgeExtention#sendMtop 抓包失败: ${it.message}")
        }.getOrDefault(false)
    }

    fun installAriverResponseHook(classLoader: ClassLoader): Boolean = runCatching {
        installAriverResponseClass(
            Class.forName(DEFAULT_BRIDGE_CALLBACK_CLASS, false, classLoader)
        )
    }.onFailure {
        runCatching { Log.runtime(TAG, "安装 BridgeCallback 抓包失败: ${it.stackTraceToString()}") }
    }.getOrDefault(false)

    fun installAriverResponseClass(callbackClass: Class<*>): Boolean = runCatching {
        val methods = linkedMapOf<Pair<String, List<Class<*>>>, java.lang.reflect.Method>()
        var owner: Class<*>? = callbackClass
        while (owner != null && owner != Any::class.java) {
            owner.declaredMethods.filter { method ->
                !java.lang.reflect.Modifier.isAbstract(method.modifiers) && !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    ((method.name == "sendJSONResponse" &&
                        method.parameterTypes.firstOrNull()?.name == JSON_OBJECT_NAME &&
                        (method.parameterCount == 1 ||
                            (method.parameterCount == 2 && method.parameterTypes[1] == Boolean::class.javaPrimitiveType))) ||
                        (method.name == "sendBridgeResponse" && method.parameterCount == 1 &&
                            method.parameterTypes[0].name == BRIDGE_RESPONSE_CLASS))
            }.forEach { methods.putIfAbsent(it.name to it.parameterTypes.toList(), it) }
            owner = owner.superclass
        }
        methods.values.forEach { method ->
            if (!markMethodHooking(method)) return@forEach
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val callback = param.thisObject
                        val calls = responseHookCalls.get() ?: IdentityHashMap<Any, Boolean>().also(responseHookCalls::set)
                        val outermost = callback != null && calls.put(callback, true) == null
                        hookSlot.get()!!.addLast(outermost)
                        if (!outermost) return
                        runCatching {
                            if (!RpcTrafficCapture.isCaptureEnabled()) return@runCatching
                            val result = param.args.getOrNull(0)
                            val responsePayload = if (method.name == "sendBridgeResponse") {
                                val bridgeResult = result
                                    ?: method.parameterTypes[0].getField("SUCCESS").get(null)
                                bridgeResult?.let { HttpSnapshot.findMethod(it.javaClass, "get", 0)?.invoke(it)?.toString() }
                            } else {
                                result?.toString()
                            }
                            val kept = if (method.parameterCount == 2) {
                                param.args.getOrNull(1) as? Boolean ?: false
                            } else {
                                HttpSnapshot.findMethod(callback.javaClass, "isSticky", 0)?.invoke(callback) as? Boolean ?: false
                            }
                            val matched = responsePayload != null &&
                                RpcTrafficCapture.recordPendingResponse(callback, responsePayload, terminal = !kept)
                            val links = PendingRegistry.linksSnapshot(callback)
                            if (!matched && links?.first != true && responsePayload != null) {
                                RpcTrafficCapture.emitEvent(
                                    RpcTrafficCapture.RpcTrafficEvent(
                                        phase = RpcTrafficCapture.TrafficPhase.RESPONSE,
                                        source = RpcTrafficCapture.TrafficSource.ARIVER_RPC,
                                        method = RpcTrafficCapture.extractMtopApiName(responsePayload) ?: "unknown",
                                        payload = responsePayload,
                                        note = if (links?.second == true) "ambiguous_callback: ${links.third}" else null,
                                        correlationStatus = if (links?.second == true) "AMBIGUOUS" else "UNMATCHED"
                                    )
                                )
                            }
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val outermost = hookSlot.get()!!.pollLast() as? Boolean ?: false
                        if (!outermost) return
                        val callback = param.thisObject
                        if (callback != null) {
                            val calls = responseHookCalls.get()
                            calls?.remove(callback)
                            if (calls?.isEmpty() == true) responseHookCalls.remove()
                        }
                    }
                })
                markMethodHooked(method)
                Log.runtime(TAG, "已安装 BridgeCallback 响应抓包: $method; loader=${callbackClass.classLoader}")
            }.onFailure {
                unmarkMethodHooking(method)
                runCatching { Log.runtime(TAG, "安装 BridgeCallback 响应抓包失败: $method ${it.stackTraceToString()}") }
            }
        }
        methods.isNotEmpty() && methods.values.all { isMethodHooked(it) }
    }.onFailure {
        runCatching { Log.runtime(TAG, "读取 BridgeCallback 接口失败: ${callbackClass.name} ${it.stackTraceToString()}") }
    }.getOrDefault(false)

    fun installH5Hook(classLoader: ClassLoader): Boolean {
        if (h5HookInstalled.get()) return true
        return runCatching {
            val rpcUtilClass = Class.forName(H5_RPC_CLASS, false, classLoader)
            val rpcCallMethod = HttpSnapshot.findMethod(rpcUtilClass, "rpcCall", 13, String::class.java)
                ?: HttpSnapshot.findMethod(rpcUtilClass, "rpcCall", 12, String::class.java)
                ?: error("H5RpcUtil#rpcCall 未找到")
            XposedBridge.hookMethod(rpcCallMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    var pending: PendingHostRequest? = null
                    runCatching {
                        val args = param.args
                        val methodName = args.getOrNull(0) as? String
                        if (RpcTrafficCapture.isCaptureEnabled() &&
                            RpcTrafficCapture.isSourceEnabled(RpcTrafficCapture.TrafficSource.H5_RPC) &&
                            !methodName.isNullOrBlank()
                        ) {
                            pending = PendingHostRequest(
                                requestId = RpcTrafficCapture.newRequestId(),
                                method = methodName,
                                requestPayload = args.getOrNull(1)?.toString(),
                                startedAtMs = System.currentTimeMillis(),
                                source = RpcTrafficCapture.TrafficSource.H5_RPC
                            ).also { RpcTrafficCapture.recordHostRequest(it) }
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
                                "H5RpcUtil.rpcCall threw ${param.throwable.javaClass.simpleName}"
                            )
                        } else {
                            val responsePayload = RpcTrafficCapture.extractH5Response(param.result)
                            if (responsePayload != null) {
                                RpcTrafficCapture.recordHostResponse(pending, responsePayload)
                            } else {
                                RpcTrafficCapture.recordHostError(pending, "H5Response.getResponse 返回空", "EMPTY_RESPONSE")
                            }
                        }
                    }
                }
            })
            h5HookInstalled.set(true)
            Log.runtime(TAG, "已安装 H5RpcUtil#rpcCall 抓包")
            true
        }.onFailure {
            Log.runtime(TAG, "安装 H5RpcUtil#rpcCall 抓包失败: ${it.message}")
        }.getOrDefault(false)
    }

    private fun readAriverResponseField(callback: Any): String? = runCatching {
        HttpSnapshot.findField(callback.javaClass, "mJSONResponse")?.get(callback)?.toString()
    }.getOrNull()

    // ===== method 级 hook 状态（false=占位安装中，true=已装） =====

    private val methodHooks = ConcurrentHashMap<java.lang.reflect.Method, Boolean>()

    private fun markMethodHooking(method: java.lang.reflect.Method): Boolean =
        methodHooks.putIfAbsent(method, false) == null

    private fun markMethodHooked(method: java.lang.reflect.Method) {
        methodHooks[method] = true
    }

    private fun unmarkMethodHooking(method: java.lang.reflect.Method) {
        methodHooks.remove(method, false)
    }

    private fun isMethodHooked(method: java.lang.reflect.Method): Boolean = methodHooks[method] == true

    private class RpcCallContext(val pending: PendingHostRequest?, val callback: Any?)
}
