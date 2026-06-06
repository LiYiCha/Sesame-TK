package fansirsqi.xposed.sesame.hook.network

import android.util.Base64
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.NetworkUtils
import java.lang.reflect.Field
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * HTTP/HTTPS 抓包 Hook 核心类
 */
object HttpCaptureHook {
    private const val TAG = "HttpCaptureHook"
    private const val CLASS_HTTP_WORKER = "com.alipay.mobile.common.transport.http.HttpWorker"
    private const val CLASS_HTTP_URL_REQUEST = "com.alipay.mobile.common.transport.http.HttpUrlRequest"
    private const val CLASS_HTTP_URL_RESPONSE = "com.alipay.mobile.common.transport.http.HttpUrlResponse"
    private const val CLASS_H5_HTTP_WORKER = "com.alipay.mobile.common.transport.h5.H5HttpWorker"
    private const val CLASS_H5_HTTP_PLUGIN = "com.alipay.mobile.nebulacore.plugin.H5HttpPlugin"
    private const val CLASS_H2_CONNECTION = "com.alipay.mobile.common.transport.http.inner.AndroidH2UrlConnection"
    private const val CLASS_TRANSPORT_SERVICE_IMPL = "com.alipay.mobile.nebulax.integration.mpaas.proxy.impl.TransportServiceImpl"
    private const val CLASS_DTN_HTTP_CLIENT = "com.alipay.mobile.dtnadapter.api.DtnHttpClient"

    private const val MAX_EXTRACT_SIZE = 10 * 1024 * 1024

    private var isInstalled = false
    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val missingFields = ConcurrentHashMap.newKeySet<String>()
    private val methodCache = ConcurrentHashMap<String, String>()
    private val hookedClasses = ConcurrentHashMap.newKeySet<String>()

    private val dispatchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CaptureDispatcher")
    }

    private fun getCachedField(clazz: Class<*>, fieldName: String): Field? {
        val key = "${clazz.name}#$fieldName"
        if (missingFields.contains(key)) return null
        var field = fieldCache[key]
        if (field == null) {
            field = XposedHelpers.findFieldIfExists(clazz, fieldName)
            if (field != null) {
                field.isAccessible = true
                fieldCache[key] = field
            } else {
                missingFields.add(key)
            }
        }
        return field
    }

    @JvmStatic
    fun setup(classLoader: ClassLoader) {
        setup(classLoader, false)
    }

    @JvmStatic
    fun setup(classLoader: ClassLoader, force: Boolean) {
        if (!force && !BaseModel.enableHttpCapture.value) return
        if (isInstalled) return
        isInstalled = true

        hookAlipayTraffic(classLoader)
        hookH5Plugin(classLoader)
        hookStandardHttpConnection(classLoader)
        hookOkHttpTraffic(classLoader)
        hookARiverTraffic(classLoader)
        bypassBifrostAndForceProxy(classLoader)
        hookDtnTraffic(classLoader)
    }

    private fun bypassBifrostAndForceProxy(classLoader: ClassLoader) {
        // 1. 禁用 TCP 直连 (强制降级为标准 HTTP/HTTPS 协议)
        val workerClasses = listOf(
            "com.alipay.mobile.common.transport.http.HttpWorker",
            "com.alipay.mobile.common.transport.rpc.RpcHttpWorker",
            "com.alipay.mobile.common.transport.h5.H5HttpWorker"
        )
        workerClasses.forEach { className ->
            try {
                val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "isCanUseExtTransport",
                    "com.alipay.mobile.common.transport.context.TransportContext",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.error(TAG, "Hook $className.isCanUseExtTransport 失败: ${e.message}")
            }
        }

        // 2. 强制开启系统代理 (绕过 NO_PROXY 屏蔽)
        try {
            val requestClass = XposedHelpers.findClassIfExists("com.alipay.mobile.common.transport.http.HttpUrlRequest", classLoader)
            if (requestClass != null) {
                XposedHelpers.findAndHookMethod(
                    requestClass,
                    "isCapture",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
            }
        } catch (e: Throwable) {
            Log.error(TAG, "Hook HttpUrlRequest.isCapture 失败: ${e.message}")
        }

        // 3. 强制开启 UC 内核的代理委托 (使其网络请求委托给 Java 层发送)
        try {
            val ucSettingsClass = XposedHelpers.findClassIfExists("com.uc.webview.export.extension.UCSettings", classLoader)
            if (ucSettingsClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(
                        ucSettingsClass,
                        "setEnableUCProxy",
                        Boolean::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.args[0] = true
                            }
                        }
                    )
                    Log.runtime(TAG, "Hook UCSettings.setEnableUCProxy 成功")
                } catch (e: Throwable) {
                    Log.error(TAG, "Hook UCSettings.setEnableUCProxy 失败: ${e.message}")
                }
                try {
                    XposedHelpers.findAndHookMethod(
                        ucSettingsClass,
                        "setForceUCProxy",
                        Boolean::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.args[0] = true
                            }
                        }
                    )
                    Log.runtime(TAG, "Hook UCSettings.setForceUCProxy 成功")
                } catch (e: Throwable) {
                    Log.error(TAG, "Hook UCSettings.setForceUCProxy 失败: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.error(TAG, "Hook UCSettings 异常: ${e.message}")
        }
    }

    private fun hookAlipayTraffic(classLoader: ClassLoader) {
        val workerClasses = listOf(CLASS_HTTP_WORKER, CLASS_H5_HTTP_WORKER)
        workerClasses.forEach { className ->
            try {
                val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
                XposedHelpers.findAndHookMethod(clazz, "call", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val request = getRequestFromWorker(param.thisObject)
                            if (request != null) {
                                val id = UUID.randomUUID().toString()
                                XposedHelpers.setAdditionalInstanceField(param.thisObject, "worker_capture_id", id)
                                val startTime = System.currentTimeMillis()
                                XposedHelpers.setAdditionalInstanceField(request, "capture_id", id)
                                XposedHelpers.setAdditionalInstanceField(request, "capture_start_time", startTime)
                                val url = XposedHelpers.callMethod(request, "getUrl")?.toString() ?: ""
                                val record = CaptureRecord(id = id, url = url, method = XposedHelpers.callMethod(request, "getRequestMethod")?.toString() ?: "GET", timestamp = startTime, statusCode = 0, isPending = true, category = CaptureClassifier.classify(url, null))
                                dispatchRecord(record, skipSave = true)
                            }
                        } catch (_: Throwable) {}
                    }
                })

                XposedHelpers.findAndHookMethod(clazz, "handleResponse", CLASS_HTTP_URL_REQUEST, "org.apache.http.HttpResponse", Int::class.javaPrimitiveType, String::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val request = param.args[0] ?: return
                            val response = param.result ?: return
                            
                            val id = XposedHelpers.getAdditionalInstanceField(param.thisObject, "worker_capture_id") as? String 
                                ?: XposedHelpers.getAdditionalInstanceField(request, "capture_id") as? String 
                                ?: return
                            val startTime = XposedHelpers.getAdditionalInstanceField(request, "capture_start_time") as? Long ?: System.currentTimeMillis()
                            
                            val resHeader = try { XposedHelpers.callMethod(response, "getHeader") } catch (_: Throwable) { null }
                            val code = try { XposedHelpers.callMethod(response, "getCode") as? Int ?: 0 } catch (_: Throwable) { 0 }

                            val hasMInputStream = try { XposedHelpers.findField(response.javaClass, "mInputStream") != null } catch (_: Throwable) { false }
                            if (hasMInputStream) {
                                val originalStream = try { XposedHelpers.getObjectField(response, "mInputStream") as? java.io.InputStream } catch (_: Throwable) { null }
                                if (originalStream != null) {
                                    if (originalStream !is CaptureInputStream) {
                                        val captureStream = CaptureInputStream(originalStream) { data ->
                                            captureFinalTraffic(request, resHeader, code, "", data, startTime, id)
                                        }
                                        XposedHelpers.setObjectField(response, "mInputStream", captureStream)
                                    }
                                } else {
                                    // 304, 204 or failed H5 requests fallback capture
                                    captureFinalTraffic(request, resHeader, code, "", null, startTime, id)
                                }
                            } else {
                                var resData = try { XposedHelpers.callMethod(response, "getResData") as? ByteArray } catch (_: Throwable) { null }
                                if (resData == null) {
                                    resData = try { XposedHelpers.getObjectField(response, "mResData") as? ByteArray } catch (_: Throwable) { null }
                                }
                                captureFinalTraffic(request, resHeader, code, "", resData, startTime, id)
                            }
                        } catch (_: Throwable) {}
                    }
                })
            } catch (_: Throwable) {}
        }
    }

    private fun getRequestFromWorker(worker: Any): Any? {
        val clazz = worker.javaClass
        listOf("mOriginRequest", "mRequest", "request").forEach { fieldName ->
            try {
                val field = getCachedField(clazz, fieldName)
                if (field != null) return field.get(worker)
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun hookH5Plugin(classLoader: ClassLoader) {
        try {
            val pluginClass = XposedHelpers.findClassIfExists(CLASS_H5_HTTP_PLUGIN, classLoader) ?: return
            XposedHelpers.findAndHookMethod(pluginClass, "httpRequest", "com.alipay.mobile.h5container.api.H5Event", "com.alipay.mobile.h5container.api.H5BridgeContext", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val event = param.args[0] ?: return
                        val params = XposedHelpers.callMethod(event, "getParam") as? org.json.JSONObject ?: return
                        val url = params.optString("url")
                    } catch (_: Throwable) {}
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookStandardHttpConnection(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(java.net.URL::class.java, "openConnection", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val conn = param.result as? java.net.HttpURLConnection ?: return
                    val className = conn.javaClass.name
                    ensurePendingBroadcast(conn)
                    if ((className.contains("okhttp") || className.contains("alipay")) && !hookedClasses.contains(className)) {
                        synchronized(hookedClasses) {
                            if (!hookedClasses.contains(className)) {
                                hookSpecificHttpImpl(conn.javaClass)
                                hookedClasses.add(className)
                            }
                        }
                    }
                }
            })
            listOf("com.android.okhttp.internal.huc.HttpURLConnectionImpl", "com.android.okhttp.internal.huc.HttpsURLConnectionImpl").forEach { className ->
                try {
                    val connClass = XposedHelpers.findClassIfExists(className, null) ?: return@forEach
                    hookSpecificHttpImpl(connClass)
                    hookedClasses.add(className)
                } catch (_: Throwable) {}
            }
            // Hook Alipay's custom AndroidH2UrlConnection
            try {
                val h2Class = XposedHelpers.findClassIfExists(CLASS_H2_CONNECTION, classLoader)
                if (h2Class != null) {
                    hookSpecificHttpImpl(h2Class)
                    hookedClasses.add(CLASS_H2_CONNECTION)
                    Log.runtime(TAG, "Hook AndroidH2UrlConnection 成功")
                }
            } catch (e: Throwable) {
                Log.error(TAG, "Hook AndroidH2UrlConnection 异常: ${e.message}")
            }
        } catch (_: Throwable) {}
    }

    private fun hookSpecificHttpImpl(connClass: Class<*>) {
        try {
            val hookStream = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    ensurePendingBroadcast(param.thisObject as java.net.HttpURLConnection)
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val is_ = param.result as? java.io.InputStream ?: return
                    if (is_ is CaptureInputStream) return
                    val captureStream = CaptureInputStream(is_) { data ->
                        XposedHelpers.setAdditionalInstanceField(param.thisObject, "captured_response_body", data)
                        triggerStandardCapture(param.thisObject as java.net.HttpURLConnection)
                    }
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_stream_obj", captureStream)
                    param.result = captureStream
                }
            }
            XposedHelpers.findAndHookMethod(connClass, "connect", object : XC_MethodHook() { override fun beforeHookedMethod(param: MethodHookParam) { ensurePendingBroadcast(param.thisObject as java.net.HttpURLConnection) } })
            XposedHelpers.findAndHookMethod(connClass, "disconnect", object : XC_MethodHook() { override fun afterHookedMethod(param: MethodHookParam) { triggerStandardCapture(param.thisObject as java.net.HttpURLConnection) } })
            XposedHelpers.findAndHookMethod(connClass, "getOutputStream", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    ensurePendingBroadcast(param.thisObject as java.net.HttpURLConnection)
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val os = param.result as? java.io.OutputStream ?: return
                    val buffer = java.io.ByteArrayOutputStream()
                    param.result = object : java.io.OutputStream() {
                        override fun write(b: Int) { os.write(b); buffer.write(b) }
                        override fun write(b: ByteArray) { os.write(b); buffer.write(b) }
                        override fun write(b: ByteArray, off: Int, len: Int) { os.write(b, off, len); buffer.write(b, off, len) }
                        override fun flush() { os.flush() }
                        override fun close() { os.close(); XposedHelpers.setAdditionalInstanceField(param.thisObject, "captured_request_body", buffer.toByteArray()) }
                    }
                }
            })
            XposedHelpers.findAndHookMethod(connClass, "getInputStream", hookStream)
            try { XposedHelpers.findAndHookMethod(connClass, "getErrorStream", hookStream) } catch (_: Throwable) {}
            
            // Hook getResponseCode to immediately capture response metadata once headers are received
            try {
                XposedHelpers.findAndHookMethod(connClass, "getResponseCode", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val code = param.result as? Int ?: return
                        if (code > 0) {
                            triggerStandardCapture(param.thisObject as java.net.HttpURLConnection)
                        }
                    }
                })
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    private fun hookOkHttpTraffic(classLoader: ClassLoader) {
        val okHttpPrefixes = listOf("okhttp3", "com.alipay.mobile.common.transport.okhttp")
        okHttpPrefixes.forEach { prefix ->
            try {
                val realCallClass = XposedHelpers.findClassIfExists("$prefix.RealCall", classLoader) ?: return@forEach
                val callbackClass = XposedHelpers.findClassIfExists("$prefix.Callback", classLoader) ?: return@forEach
                
                // Hook execute (同步)
                XposedHelpers.findAndHookMethod(realCallClass, "execute", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val startTime = System.currentTimeMillis()
                        XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_start_time", startTime)
                        try {
                            val request = XposedHelpers.callMethod(param.thisObject, "request") ?: return
                            val id = UUID.randomUUID().toString()
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_id", id)
                            val url = XposedHelpers.callMethod(request, "url").toString()
                            val method = XposedHelpers.callMethod(request, "method").toString()
                            dispatchRecord(CaptureRecord(id = id, url = url, method = method, timestamp = startTime, statusCode = 0, isPending = true), true)
                        } catch (_: Exception) {}
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val response = param.result ?: return
                            val request = XposedHelpers.callMethod(param.thisObject, "request") ?: return
                            val startTime = XposedHelpers.getAdditionalInstanceField(param.thisObject, "capture_start_time") as? Long ?: System.currentTimeMillis()
                            val id = XposedHelpers.getAdditionalInstanceField(param.thisObject, "capture_id") as? String ?: UUID.randomUUID().toString()
                            captureOkHttpTraffic(request, response, startTime, id)
                        } catch (_: Throwable) {}
                    }
                })

                // Hook enqueue (异步)
                XposedHelpers.findAndHookMethod(realCallClass, "enqueue", callbackClass, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val originalCallback = param.args[0] ?: return
                        val startTime = System.currentTimeMillis()
                        val request = XposedHelpers.callMethod(param.thisObject, "request") ?: return
                        val id = UUID.randomUUID().toString()
                        XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_id", id)
                        XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_start_time", startTime)
                        
                        param.args[0] = java.lang.reflect.Proxy.newProxyInstance(classLoader, arrayOf(callbackClass)) { _, method, args ->
                            if (method.name == "onResponse" && args != null && args.size >= 2) {
                                captureOkHttpTraffic(request, args[1]!!, startTime, id)
                            }
                            method.invoke(originalCallback, *(args ?: emptyArray()))
                        }
                    }
                })
            } catch (_: Throwable) {}
        }
    }

    private fun captureOkHttpTraffic(request: Any, response: Any, startTime: Long, id: String) {
        try {
            val url = XposedHelpers.callMethod(request, "url").toString()
            val method = XposedHelpers.callMethod(request, "method").toString()
            val reqHeadersObj = XposedHelpers.callMethod(request, "headers")
            val reqHeadersMap = mutableMapOf<String, String>()
            val size = XposedHelpers.callMethod(reqHeadersObj, "size") as Int
            for (i in 0 until size) { reqHeadersMap[XposedHelpers.callMethod(reqHeadersObj, "name", i) as String] = XposedHelpers.callMethod(reqHeadersObj, "value", i) as String }
            var reqBody: String? = null
            var reqBodyBase64: String? = null
            var reqBodySize = 0
            val reqBodyObj = XposedHelpers.callMethod(request, "body")
            if (reqBodyObj != null) {
                try {
                    val bufferClass = XposedHelpers.findClass("okio.Buffer", reqBodyObj.javaClass.classLoader)
                    val buffer = XposedHelpers.newInstance(bufferClass)
                    XposedHelpers.callMethod(reqBodyObj, "writeTo", buffer)
                    val bytes = XposedHelpers.callMethod(buffer, "readByteArray") as ByteArray
                    reqBodySize = bytes.size
                    val (b, s) = processBody(bytes, reqHeadersMap["Content-Encoding"] ?: reqHeadersMap["content-encoding"])
                    reqBody = b; reqBodyBase64 = s
                } catch (_: Exception) {}
            }
            val code = XposedHelpers.callMethod(response, "code") as Int
            val resHeadersObj = XposedHelpers.callMethod(response, "headers")
            val resHeadersMap = mutableMapOf<String, String>()
            val resSize = XposedHelpers.callMethod(resHeadersObj, "size") as Int
            for (i in 0 until resSize) { resHeadersMap[XposedHelpers.callMethod(resHeadersObj, "name", i) as String] = XposedHelpers.callMethod(resHeadersObj, "value", i) as String }
            var resBody: String? = null
            var resBodyBase64: String? = null
            var resBodySize = 0
            val resBodyObj = XposedHelpers.callMethod(response, "body")
            if (resBodyObj != null) {
                try {
                    val contentType = XposedHelpers.callMethod(resBodyObj, "contentType")
                    val bytes = XposedHelpers.callMethod(resBodyObj, "bytes") as ByteArray
                    resBodySize = bytes.size
                    val newBody = XposedHelpers.callStaticMethod(XposedHelpers.findClass("okhttp3.ResponseBody", response.javaClass.classLoader), "create", contentType, bytes)
                    XposedHelpers.setObjectField(response, "body", newBody)
                    val (b, s) = processBody(bytes, resHeadersMap["Content-Encoding"] ?: resHeadersMap["content-encoding"])
                    resBody = b; resBodyBase64 = s
                } catch (_: Exception) {}
            }
            val parsed = CaptureClassifier.parse(url)
            dispatchRecord(CaptureRecord(id = id, timestamp = startTime, url = url, method = method, host = parsed.host, path = parsed.path, queryParams = parsed.queryParams, requestHeaders = reqHeadersMap, requestBody = reqBody, requestBodyBase64 = reqBodyBase64, requestBodySize = reqBodySize, statusCode = code, responseBody = resBody, responseBodyBase64 = resBodyBase64, responseBodySize = resBodySize, responseHeaders = resHeadersMap, duration = System.currentTimeMillis() - startTime, category = CaptureClassifier.classify(url, reqHeadersMap["Operation-Type"] ?: reqHeadersMap["operation-type"], reqBody)))
        } catch (_: Exception) {}
    }

    private fun ensurePendingBroadcast(connection: java.net.HttpURLConnection) {
        if (XposedHelpers.getAdditionalInstanceField(connection, "capture_id") != null) return
        val id = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        XposedHelpers.setAdditionalInstanceField(connection, "capture_id", id)
        XposedHelpers.setAdditionalInstanceField(connection, "capture_start_time", startTime)
        try {
            val url = try { connection.url.toString() } catch (_: Throwable) { "unknown" }
            val method = try { connection.requestMethod } catch (_: Throwable) { "GET" }
            dispatchRecord(CaptureRecord(id = id, url = url, method = method, timestamp = startTime, statusCode = 0, isPending = true), true)
        } catch (_: Throwable) {}
    }

    private fun triggerStandardCapture(connection: java.net.HttpURLConnection) {
        try {
            val id = XposedHelpers.getAdditionalInstanceField(connection, "capture_id") as? String ?: UUID.randomUUID().toString().also { XposedHelpers.setAdditionalInstanceField(connection, "capture_id", it) }
            val startTime = XposedHelpers.getAdditionalInstanceField(connection, "capture_start_time") as? Long ?: System.currentTimeMillis()
            val url = try { connection.url.toString() } catch (_: Throwable) { "unknown" }
            val method = try { connection.requestMethod } catch (_: Throwable) { "GET" }
            val stream = XposedHelpers.getAdditionalInstanceField(connection, "capture_stream_obj") as? CaptureInputStream
            val resData = XposedHelpers.getAdditionalInstanceField(connection, "captured_response_body") as? ByteArray ?: stream?.getCapturedData()
            val headers = try { connection.requestProperties.mapValues { it.value.joinToString(", ") } } catch (_: Exception) { emptyMap() }
            var code = try { connection.responseCode } catch (_: Exception) { 0 }
            if (code <= 0) {
                // 尝试从 Header 状态行解析 (HTTP/1.1 200 OK)
                try {
                    val statusLine = connection.getHeaderField(null)
                    if (statusLine != null && statusLine.contains(" ")) {
                        val parts = statusLine.split(" ")
                        if (parts.size >= 2) code = parts[1].toInt()
                    }
                } catch (_: Exception) {}
            }
            
            val resHeaders = try { connection.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") } } catch (_: Exception) { emptyMap() }
            val (resBody, resBodyBase64) = processBody(resData, resHeaders["Content-Encoding"] ?: resHeaders["content-encoding"])
            val reqData = XposedHelpers.getAdditionalInstanceField(connection, "captured_request_body") as? ByteArray
            val (reqBody, reqBodyBase64) = processBody(reqData)
            val requestBodySize = reqData?.size ?: 0
            val responseBodySize = resData?.size ?: 0
            dispatchRecord(CaptureRecord(id = id, timestamp = startTime, url = url, method = method, requestHeaders = headers, requestBody = reqBody, requestBodyBase64 = reqBodyBase64, requestBodySize = requestBodySize, statusCode = code, responseBody = resBody, responseBodyBase64 = resBodyBase64, responseBodySize = responseBodySize, responseHeaders = resHeaders, duration = System.currentTimeMillis() - startTime, category = CaptureClassifier.classify(url, headers["Operation-Type"] ?: headers["operation-type"], reqBody)))
        } catch (_: Throwable) {}
    }

    private fun captureFinalTraffic(request: Any, resHeader: Any?, code: Int, msg: String?, resData: ByteArray?, startTime: Long, id: String) {
        try {
            val urlMethod = methodCache["req_url"] ?: listOf("getUrl", "getUri", "url", "getURL").firstOrNull { name -> try { XposedHelpers.callMethod(request, name); true } catch (_: Throwable) { false } }?.also { methodCache["req_url"] = it }
            val url = if (urlMethod != null) try { XposedHelpers.callMethod(request, urlMethod)?.toString() ?: "unknown" } catch (_: Throwable) { "unknown" } else "unknown"
            val methodAttr = methodCache["req_method"] ?: listOf("getRequestMethod", "getMethod").firstOrNull { name -> try { XposedHelpers.callMethod(request, name); true } catch (_: Throwable) { false } }?.also { methodCache["req_method"] = it }
            val method = if (methodAttr != null) try { XposedHelpers.callMethod(request, methodAttr) as? String ?: "UNKNOWN" } catch (_: Throwable) { "UNKNOWN" } else "UNKNOWN"
            val parsed = CaptureClassifier.parse(url)
            val reqHeaders = mutableMapOf<String, String>()
            val reqHeadersListMethod = methodCache["req_headers_list"] ?: listOf("getHeaders", "getHeaderList", "headers").firstOrNull { name -> try { XposedHelpers.callMethod(request, name) as? List<*>; true } catch (_: Throwable) { false } }?.also { methodCache["req_headers_list"] = it }
            val reqHeadersList = if (reqHeadersListMethod != null) try { XposedHelpers.callMethod(request, reqHeadersListMethod) as? List<*> } catch (_: Throwable) { null } else null
            reqHeadersList?.forEach { header -> if (header != null) { val name = try { XposedHelpers.callMethod(header, "getName")?.toString() ?: XposedHelpers.callMethod(header, "getKey")?.toString() } catch (_: Throwable) { null }; val value = try { XposedHelpers.callMethod(header, "getValue")?.toString() } catch (_: Throwable) { null }; if (name != null) reqHeaders[name] = value ?: "" } }
            val reqDataRaw = try { XposedHelpers.callMethod(request, "getReqData") as? ByteArray } catch (_: Throwable) { null }
            val (reqBody, reqBodyBase64) = processBody(reqDataRaw, reqHeaders["Content-Encoding"] ?: reqHeaders["content-encoding"])
            val requestBodySize = reqDataRaw?.size ?: 0
            val responseHeaders = mutableMapOf<String, String>()
            if (resHeader != null) {
                if (resHeader is Array<*>) {
                    resHeader.forEach { header ->
                        if (header != null) {
                            val name = try { XposedHelpers.callMethod(header, "getName")?.toString() } catch (_: Throwable) { null }
                            val value = try { XposedHelpers.callMethod(header, "getValue")?.toString() } catch (_: Throwable) { null }
                            if (name != null) responseHeaders[name] = value ?: ""
                        }
                    }
                } else {
                    try {
                        val headersMap = XposedHelpers.callMethod(resHeader, "getHeaders") as? Map<*, *>
                        headersMap?.forEach { (k, v) -> if (k != null) responseHeaders[k.toString()] = v?.toString() ?: "" }
                    } catch (_: Throwable) {
                        try {
                            val headersField = getCachedField(resHeader.javaClass, "mHeaders")
                            (headersField?.get(resHeader) as? Map<*, *>)?.forEach { (k, v) -> if (k != null) responseHeaders[k.toString()] = v?.toString() ?: "" }
                        } catch (_: Throwable) {}
                    }
                }
            }
            val (resBody, resBodyBase64) = processBody(resData, responseHeaders["Content-Encoding"] ?: responseHeaders["content-encoding"])
            val responseBodySize = resData?.size ?: 0
            dispatchRecord(CaptureRecord(id = id, timestamp = startTime, url = url, method = method, host = parsed.host, path = parsed.path, queryParams = parsed.queryParams, requestHeaders = reqHeaders, requestBody = reqBody, requestBodyBase64 = reqBodyBase64, requestBodySize = requestBodySize, statusCode = code, responseBody = resBody, responseBodyBase64 = resBodyBase64, responseBodySize = responseBodySize, responseHeaders = responseHeaders, duration = System.currentTimeMillis() - startTime, category = CaptureClassifier.classify(url, reqHeaders["Operation-Type"] ?: reqHeaders["operation-type"], reqBody)))
        } catch (_: Throwable) {}
    }

    private fun processBody(data: ByteArray?, contentEncoding: String? = null): Pair<String?, String?> {
        if (data == null || data.isEmpty()) return Pair(null, null)
        var decompressed = if (NetworkUtils.isGzip(data)) NetworkUtils.decompressGzip(data) ?: data else NetworkUtils.decompressDeflate(data) ?: data
        if (contentEncoding?.contains("gzip", ignoreCase = true) == true) decompressed = NetworkUtils.decompressGzip(decompressed) ?: decompressed
        else if (contentEncoding?.contains("deflate", ignoreCase = true) == true) decompressed = NetworkUtils.decompressDeflate(decompressed) ?: decompressed
        for (charset in listOf(Charsets.UTF_8, Charsets.US_ASCII, Charsets.ISO_8859_1)) { try { val text = String(decompressed, charset); if (isPrintableText(text)) return Pair(if (text.contains("%") && text.contains("=") && text.length < 5000) try { java.net.URLDecoder.decode(text, "UTF-8") } catch (_: Throwable) { text } else text, null) } catch (_: Throwable) {} }
        return Pair(null, Base64.encodeToString(decompressed, Base64.NO_WRAP))
    }

    private fun isPrintableText(text: String): Boolean {
        if (text.isEmpty()) return true
        var nonPrintable = 0
        val maxCheck = minOf(text.length, 4096)
        for (i in 0 until maxCheck) { val c = text[i]; if (c < 0x20.toChar() && c != '\n' && c != '\r' && c != '\t') nonPrintable++ }
        return nonPrintable.toFloat() / maxCheck < 0.05f
    }

    private fun hookARiverTraffic(classLoader: ClassLoader) {
        try {
            val serviceImpl = XposedHelpers.findClassIfExists(CLASS_TRANSPORT_SERVICE_IMPL, classLoader) ?: return
            XposedHelpers.findAndHookMethod(serviceImpl, "httpRequest", "com.alibaba.ariver.kernel.common.network.http.RVHttpRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val startTime = System.currentTimeMillis()
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_start_time", startTime)
                    try {
                        val request = param.args[0] ?: return
                        val id = UUID.randomUUID().toString()
                        XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_id", id)
                        val url = XposedHelpers.callMethod(request, "getUrl") as String
                        val method = XposedHelpers.callMethod(request, "getMethod") as String
                        dispatchRecord(CaptureRecord(id = id, url = url, method = method, timestamp = startTime, statusCode = 0, isPending = true), true)
                    } catch (_: Throwable) {}
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val request = param.args[0] ?: return
                        val response = param.result ?: return
                        val startTime = XposedHelpers.getAdditionalInstanceField(param.thisObject, "capture_start_time") as? Long ?: System.currentTimeMillis()
                        val url = XposedHelpers.callMethod(request, "getUrl") as String
                        val method = XposedHelpers.callMethod(request, "getMethod") as String
                        val reqHeaders = XposedHelpers.callMethod(request, "getHeaders") as? Map<String, String> ?: emptyMap()
                        val reqData = XposedHelpers.callMethod(request, "getRequestData") as? ByteArray
                        val code = XposedHelpers.callMethod(response, "getStatusCode") as Int
                        val resHeaders = XposedHelpers.callMethod(response, "getHeaders") as? Map<String, List<String>> ?: emptyMap()
                        val flatResHeaders = resHeaders.mapValues { it.value.joinToString(", ") }
                        val originalStream = XposedHelpers.callMethod(response, "getResStream") as? java.io.InputStream
                        val id = XposedHelpers.getAdditionalInstanceField(param.thisObject, "capture_id") as? String ?: UUID.randomUUID().toString()
                        if (originalStream != null) {
                            if (originalStream !is CaptureInputStream) {
                                XposedHelpers.callMethod(response, "setResStream", CaptureInputStream(originalStream) { data ->
                                    val (reqB, reqB64) = processBody(reqData, reqHeaders["Content-Encoding"] ?: reqHeaders["content-encoding"])
                                    val (resB, resB64) = processBody(data, flatResHeaders["Content-Encoding"] ?: flatResHeaders["content-encoding"])
                                    val parsed = CaptureClassifier.parse(url)
                                    val requestBodySize = reqData?.size ?: 0
                                    val responseBodySize = data.size
                                    dispatchRecord(CaptureRecord(id = id, url = url, method = method, host = parsed.host, path = parsed.path, queryParams = parsed.queryParams, requestHeaders = reqHeaders, requestBody = reqB, requestBodyBase64 = reqB64, requestBodySize = requestBodySize, statusCode = code, responseBody = resB, responseBodyBase64 = resB64, responseBodySize = responseBodySize, responseHeaders = flatResHeaders, timestamp = startTime, duration = System.currentTimeMillis() - startTime, category = CaptureClassifier.classify(url, reqHeaders["Operation-Type"] ?: reqHeaders["operation-type"], reqB)))
                                })
                            }
                        } else {
                            // 保底：若无输入流（304 或 异常），立即上报结果
                            val (reqB, reqB64) = processBody(reqData, reqHeaders["Content-Encoding"] ?: reqHeaders["content-encoding"])
                            val parsed = CaptureClassifier.parse(url)
                            val requestBodySize = reqData?.size ?: 0
                            dispatchRecord(CaptureRecord(id = id, url = url, method = method, host = parsed.host, path = parsed.path, queryParams = parsed.queryParams, requestHeaders = reqHeaders, requestBody = reqB, requestBodyBase64 = reqB64, requestBodySize = requestBodySize, statusCode = code, responseBody = null, responseBodyBase64 = null, responseBodySize = 0, responseHeaders = flatResHeaders, timestamp = startTime, duration = System.currentTimeMillis() - startTime, category = CaptureClassifier.classify(url, reqHeaders["Operation-Type"] ?: reqHeaders["operation-type"], reqB)))
                        }
                    } catch (_: Throwable) {}
                }
            })
        } catch (_: Throwable) {}
    }

    private var saveReceiverRegistered = false

    private fun isMainProcess(context: android.content.Context): Boolean {
        return try {
            val processName = getProcessName(context)
            context.packageName == processName
        } catch (_: Throwable) {
            true
        }
    }

    private fun getProcessName(context: android.content.Context): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return android.app.Application.getProcessName()
        }
        return try {
            val clazz = Class.forName("android.app.ActivityThread")
            val currentActivityThread = clazz.getDeclaredMethod("currentActivityThread").invoke(null)
            val getProcessName = clazz.getDeclaredMethod("getProcessName")
            getProcessName.invoke(currentActivityThread) as String
        } catch (_: Throwable) {
            context.packageName
        }
    }

    private fun registerSaveReceiver(context: android.content.Context) {
        if (saveReceiverRegistered) return
        synchronized(this) {
            if (saveReceiverRegistered) return
            try {
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                        if (intent.action == "fansirsqi.xposed.sesame.SAVE_CAPTURE") {
                            val json = intent.getStringExtra("record_json") ?: return
                            val skipSave = intent.getBooleanExtra("skip_save", false)
                            val rec = fansirsqi.xposed.sesame.util.JsonUtil.parseObject(json, CaptureRecord::class.java) ?: return
                            dispatchRecordDirect(rec, skipSave)
                        }
                    }
                }
                val filter = android.content.IntentFilter("fansirsqi.xposed.sesame.SAVE_CAPTURE")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                saveReceiverRegistered = true
                Log.runtime(TAG, "Registered SAVE_CAPTURE receiver successfully")
            } catch (e: Throwable) {
                Log.error(TAG, "Register SAVE_CAPTURE receiver failed: ${e.message}")
            }
        }
    }

    private fun dispatchRecordDirect(record: CaptureRecord, skipSave: Boolean) {
        dispatchExecutor.execute {
            try {
                val processed = if (skipSave) record else CaptureStorage.save(record)
                val context = fansirsqi.xposed.sesame.hook.context.AppContext.getAppContext()
                if (context != null) {
                    val intent = android.content.Intent("fansirsqi.xposed.sesame.NEW_CAPTURE")
                    val metadataOnly = processed.copy(requestBody = if (processed.requestBody != null && processed.requestBody!!.length > 1000) "[Large Body...]" else processed.requestBody, requestBodyBase64 = null, responseBody = if (processed.responseBody != null && processed.responseBody!!.length > 1000) "[Large Body...]" else processed.responseBody, responseBodyBase64 = null)
                    intent.putExtra("record_json", fansirsqi.xposed.sesame.util.JsonUtil.formatJson(metadataOnly, false))
                    intent.putExtra("is_update", processed.statusCode != 0)
                    context.sendBroadcast(intent)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun dispatchRecord(record: CaptureRecord, skipSave: Boolean = false) {
        val context = fansirsqi.xposed.sesame.hook.context.AppContext.getAppContext()
        if (context == null) {
            dispatchRecordDirect(record, skipSave)
            return
        }

        if (isMainProcess(context)) {
            registerSaveReceiver(context)
            dispatchRecordDirect(record, skipSave)
        } else {
            try {
                val intent = android.content.Intent("fansirsqi.xposed.sesame.SAVE_CAPTURE")
                intent.putExtra("record_json", fansirsqi.xposed.sesame.util.JsonUtil.formatJson(record, false))
                intent.putExtra("skip_save", skipSave)
                context.sendBroadcast(intent)
            } catch (e: Throwable) {
                dispatchRecordDirect(record, skipSave)
            }
        }
    }

    private fun hookDtnTraffic(classLoader: ClassLoader) {
        try {
            val dtnClientClass = XposedHelpers.findClassIfExists(CLASS_DTN_HTTP_CLIENT, classLoader) ?: return
            XposedHelpers.findAndHookMethod(
                dtnClientClass,
                "executeHttpRequest",
                "com.alipay.mobile.common.transport.http.HttpUrlRequest",
                "com.alipay.mobile.common.transport.context.TransportContext",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val request = param.args[0] ?: return
                            val id = UUID.randomUUID().toString()
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, "dtn_capture_id", id)
                            XposedHelpers.setAdditionalInstanceField(request, "capture_id", id)
                            val startTime = System.currentTimeMillis()
                            XposedHelpers.setAdditionalInstanceField(request, "capture_start_time", startTime)
                            
                            val url = XposedHelpers.callMethod(request, "getUrl")?.toString() ?: ""
                            val record = CaptureRecord(
                                id = id,
                                url = url,
                                method = XposedHelpers.callMethod(request, "getRequestMethod")?.toString() ?: "GET",
                                timestamp = startTime,
                                statusCode = 0,
                                isPending = true,
                                category = CaptureClassifier.classify(url, null)
                            )
                            dispatchRecord(record, skipSave = true)
                        } catch (_: Throwable) {}
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val request = param.args[0] ?: return
                            val response = param.result ?: return
                            
                            val id = XposedHelpers.getAdditionalInstanceField(param.thisObject, "dtn_capture_id") as? String 
                                ?: XposedHelpers.getAdditionalInstanceField(request, "capture_id") as? String 
                                ?: return
                            val startTime = XposedHelpers.getAdditionalInstanceField(request, "capture_start_time") as? Long ?: System.currentTimeMillis()
                            
                            val statusLine = XposedHelpers.callMethod(response, "getStatusLine")
                            val code = if (statusLine != null) XposedHelpers.callMethod(statusLine, "getStatusCode") as? Int ?: 0 else 0
                            val resHeader = try { XposedHelpers.callMethod(response, "getAllHeaders") } catch (_: Throwable) { null }
                            
                            val entity = try { XposedHelpers.callMethod(response, "getEntity") } catch (_: Throwable) { null }
                            if (entity != null) {
                                val originalStream = try { XposedHelpers.callMethod(entity, "getContent") as? java.io.InputStream } catch (_: Throwable) { null }
                                if (originalStream != null) {
                                    if (originalStream !is CaptureInputStream) {
                                        val captureStream = CaptureInputStream(originalStream) { data ->
                                            captureFinalTraffic(request, resHeader, code, "", data, startTime, id)
                                        }
                                        try {
                                            XposedHelpers.callMethod(entity, "setContent", captureStream)
                                        } catch (_: Throwable) {
                                            try {
                                                XposedHelpers.setObjectField(entity, "content", captureStream)
                                            } catch (_: Throwable) {}
                                        }
                                    }
                                } else {
                                    captureFinalTraffic(request, resHeader, code, "", null, startTime, id)
                                }
                            } else {
                                captureFinalTraffic(request, resHeader, code, "", null, startTime, id)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
            Log.runtime(TAG, "Hook DtnHttpClient 成功")
        } catch (e: Throwable) {
            Log.error(TAG, "Hook DtnHttpClient 失败: ${e.message}")
        }
    }

    private class CaptureInputStream(inputStream: java.io.InputStream, private val onClose: (ByteArray) -> Unit) : java.io.FilterInputStream(inputStream) {
        private val memBuffer = java.io.ByteArrayOutputStream()
        private var fileBuffer: java.io.File? = null
        private var fileOut: java.io.FileOutputStream? = null
        private var totalSize = 0L
        private var isClosed = false
        fun getCapturedData(): ByteArray { synchronized(this) { if (fileBuffer != null) return try { fileBuffer!!.readBytes() } catch (_: Throwable) { memBuffer.toByteArray() } ; return memBuffer.toByteArray() } }
        override fun read(): Int { val b = super.read(); if (b != -1) updateBuffer(byteArrayOf(b.toByte()), 0, 1) else checkComplete(); return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int { val n = super.read(b, off, len); if (n > 0) updateBuffer(b, off, n) else if (n == -1) checkComplete(); return n }
        private fun updateBuffer(b: ByteArray, off: Int, len: Int) { synchronized(this) { if (totalSize > MAX_EXTRACT_SIZE) return; totalSize += len; if (fileOut != null) try { fileOut?.write(b, off, len) } catch (_: Throwable) {} else if (memBuffer.size() + len > 1024 * 1024) { try { val temp = java.io.File.createTempFile("cap_", ".tmp", fansirsqi.xposed.sesame.hook.context.AppContext.getAppContext()?.cacheDir); fileBuffer = temp; val fos = java.io.FileOutputStream(temp); fos.write(memBuffer.toByteArray()); fos.write(b, off, len); fileOut = fos; memBuffer.reset() } catch (_: Throwable) { memBuffer.write(b, off, len) } } else memBuffer.write(b, off, len) } }
        private fun checkComplete() { synchronized(this) { if (isClosed) return; isClosed = true; try { fileOut?.close() } catch (_: Throwable) {}; onClose(getCapturedData()); try { fileBuffer?.delete() } catch (_: Throwable) {} } }
        override fun close() { super.close(); checkComplete() }
    }
}
