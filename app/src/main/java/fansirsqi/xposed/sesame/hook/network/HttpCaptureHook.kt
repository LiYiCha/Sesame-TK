package fansirsqi.xposed.sesame.hook.network

import android.util.Base64
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.NetworkUtils
import java.util.UUID

/**
 * HTTP/HTTPS 抓包 Hook 核心类
 */
object HttpCaptureHook {
    private const val TAG = "HttpCaptureHook"
    private const val CLASS_HTTP_WORKER = "com.alipay.mobile.common.transport.http.HttpWorker"
    private const val CLASS_HTTP_URL_REQUEST = "com.alipay.mobile.common.transport.http.HttpUrlRequest"
    private const val CLASS_HTTP_URL_RESPONSE = "com.alipay.mobile.common.transport.http.HttpUrlResponse"
    private const val CLASS_TRANSPORT_SERVICE_IMPL = "com.alipay.mobile.nebulax.integration.mpaas.proxy.impl.TransportServiceImpl"

    /** 提取阶段的最大字节限制 (10MB)，防止超过该大小导致 OOM */
    private const val MAX_EXTRACT_SIZE = 10 * 1024 * 1024
    /** 内联存储最大限制，配合提取限制 */
    private const val MAX_INLINE_SIZE = MAX_EXTRACT_SIZE

    @JvmStatic
    fun setup(classLoader: ClassLoader) {
        try {
            fansirsqi.xposed.sesame.util.DataStore.init(fansirsqi.xposed.sesame.util.Files.CONFIG_DIR)
        } catch (e: Throwable) {
            Log.capture(TAG, "初始化 DataStore 失败: ${e.message}")
        }
        hookAlipayHttpWorker(classLoader)
        hookStandardHttpConnection()
        hookOkHttpTraffic(classLoader)
        hookARiverTraffic(classLoader)
    }

    private fun hookAlipayHttpWorker(classLoader: ClassLoader) {
        try {
            val handleResponseMethod = XposedHelpers.findMethodExact(
                CLASS_HTTP_WORKER, classLoader, "handleResponse",
                CLASS_HTTP_URL_REQUEST, "org.apache.http.HttpResponse", "int", "java.lang.String"
            )

            XposedBridge.hookMethod(handleResponseMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedHelpers.setAdditionalInstanceField(param, "capture_start_time", System.currentTimeMillis())
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!BaseModel.enableHttpCapture.value) return
                    try {
                        val request = param.args[0] ?: return
                        val response = param.result ?: return
                        if (response.javaClass.name != CLASS_HTTP_URL_RESPONSE) return

                        val startTime = XposedHelpers.getAdditionalInstanceField(param, "capture_start_time") as? Long
                            ?: System.currentTimeMillis()
                        captureAlipayTraffic(request, response, startTime)
                    } catch (e: Throwable) {
                        Log.capture(TAG, "HttpWorker Hook 执行异常: ${e.message}")
                    }
                }
            })
        } catch (e: Throwable) {
            Log.capture(TAG, "注册 HttpWorker Hook 失败: ${e.message}")
        }
    }

    private fun hookStandardHttpConnection() {
        try {
            // Hook 标准 Java 网络库，捕捉 GameTask, Credit2101 等请求
            val connClass = XposedHelpers.findClass("com.android.okhttp.internal.huc.HttpURLConnectionImpl", null)
            XposedHelpers.findAndHookMethod(connClass, "execute", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_start_time", System.currentTimeMillis())
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    // 仅标记开始时间，实际抓取移至流关闭或 disconnect
                }
            })

            XposedHelpers.findAndHookMethod(connClass, "disconnect", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!BaseModel.enableHttpCapture.value) return
                    triggerStandardCapture(param.thisObject as java.net.HttpURLConnection)
                }
            })

            XposedHelpers.findAndHookMethod(connClass, "getOutputStream", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!BaseModel.enableHttpCapture.value) return
                    val os = param.result as? java.io.OutputStream ?: return
                    val buffer = java.io.ByteArrayOutputStream()
                    param.result = object : java.io.OutputStream() {
                        override fun write(b: Int) { os.write(b); buffer.write(b) }
                        override fun write(b: ByteArray) { os.write(b); buffer.write(b) }
                        override fun write(b: ByteArray, off: Int, len: Int) { os.write(b, off, len); buffer.write(b, off, len) }
                        override fun flush() { os.flush() }
                        override fun close() { 
                            os.close()
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, "captured_request_body", buffer.toByteArray())
                        }
                    }
                }
            })

            // 💡 修复：拦截 getInputStream / getErrorStream，不直接读取，而是返回代理流
            val hookStream = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!BaseModel.enableHttpCapture.value) return
                    val `is` = param.result as? java.io.InputStream ?: return
                    val buffer = java.io.ByteArrayOutputStream()
                    param.result = object : java.io.FilterInputStream(`is`) {
                        override fun read(): Int {
                            val b = super.read()
                            if (b != -1 && buffer.size() < MAX_EXTRACT_SIZE) buffer.write(b)
                            return b
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            val r = super.read(b, off, len)
                            if (r != -1 && buffer.size() < MAX_EXTRACT_SIZE) buffer.write(b, off, r)
                            return r
                        }
                        override fun close() {
                            super.close()
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, "captured_response_body", buffer.toByteArray())
                            // 在流关闭时触发完整记录保存
                            triggerStandardCapture(param.thisObject as java.net.HttpURLConnection)
                        }
                    }
                }
            }
            XposedHelpers.findAndHookMethod(connClass, "getInputStream", hookStream)
            XposedHelpers.findAndHookMethod(connClass, "getErrorStream", hookStream)
        } catch (e: Throwable) {
            // Log.capture(TAG, "注册标准 HTTP Hook 失败: ${e.message}")
        }
    }

    private fun hookOkHttpTraffic(classLoader: ClassLoader) {
        try {
            val realCallClass = XposedHelpers.findClass("okhttp3.RealCall", classLoader)
            XposedHelpers.findAndHookMethod(realCallClass, "execute", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_start_time", System.currentTimeMillis())
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!BaseModel.enableHttpCapture.value) return
                    try {
                        val response = param.result ?: return
                        val request = XposedHelpers.callMethod(param.thisObject, "request") ?: return
                        val startTime = XposedHelpers.getAdditionalInstanceField(param.thisObject, "capture_start_time") as? Long 
                            ?: System.currentTimeMillis()
                        
                        captureOkHttpTraffic(request, response, startTime)
                    } catch (e: Throwable) {
                        Log.capture(TAG, "OkHttp Hook 执行异常: ${e.message}")
                    }
                }
            })
        } catch (_: Throwable) {}
    }

    private fun captureOkHttpTraffic(request: Any, response: Any, startTime: Long) {
        try {
            val url = XposedHelpers.callMethod(request, "url").toString()
            val method = XposedHelpers.callMethod(request, "method").toString()
            
            // 提取 Request Headers
            val reqHeadersObj = XposedHelpers.callMethod(request, "headers")
            val reqHeadersMap = mutableMapOf<String, String>()
            val size = XposedHelpers.callMethod(reqHeadersObj, "size") as Int
            for (i in 0 until size) {
                val name = XposedHelpers.callMethod(reqHeadersObj, "name", i) as String
                val value = XposedHelpers.callMethod(reqHeadersObj, "value", i) as String
                reqHeadersMap[name] = value
            }

            // 提取 Request Body
            val reqBodyObj = XposedHelpers.callMethod(request, "body")
            var reqBody: String? = null
            var reqBase64: String? = null
            if (reqBodyObj != null) {
                try {
                    val bufferClass = XposedHelpers.findClass("okio.Buffer", reqBodyObj.javaClass.classLoader)
                    val buffer = XposedHelpers.newInstance(bufferClass)
                    XposedHelpers.callMethod(reqBodyObj, "writeTo", buffer)
                    val bytes = XposedHelpers.callMethod(buffer, "readByteArray") as ByteArray
                    val (b, s) = processBody(bytes)
                    reqBody = b; reqBase64 = s
                } catch (_: Exception) {}
            }

            // 提取 Response
            val code = XposedHelpers.callMethod(response, "code") as Int
            val resHeadersObj = XposedHelpers.callMethod(response, "headers")
            val resHeadersMap = mutableMapOf<String, String>()
            val resSize = XposedHelpers.callMethod(resHeadersObj, "size") as Int
            for (i in 0 until resSize) {
                val name = XposedHelpers.callMethod(resHeadersObj, "name", i) as String
                val value = XposedHelpers.callMethod(resHeadersObj, "value", i) as String
                resHeadersMap[name] = value
            }

            // 提取 Response Body (克隆流，防止影响业务)
            var resBody: String? = null
            var resBodyBase64: String? = null
            try {
                val resBodyObj = XposedHelpers.callMethod(response, "body")
                if (resBodyObj != null) {
                    val contentLength = XposedHelpers.callMethod(resBodyObj, "contentLength") as Long
                    if (contentLength > MAX_EXTRACT_SIZE) {
                        resBody = "[响应体过大: $contentLength bytes]"
                    } else {
                        val contentType = XposedHelpers.callMethod(resBodyObj, "contentType")
                        val bytes = XposedHelpers.callMethod(resBodyObj, "bytes") as ByteArray
                        val newBody = XposedHelpers.callStaticMethod(XposedHelpers.findClass("okhttp3.ResponseBody", response.javaClass.classLoader), "create", contentType, bytes)
                        XposedHelpers.setObjectField(response, "body", newBody)
                        
                        val (b, s) = processBody(bytes)
                        resBody = b; resBodyBase64 = s
                    }
                }
            } catch (_: Exception) {}

            val record = CaptureRecord(
                id = UUID.randomUUID().toString(),
                timestamp = startTime,
                url = url,
                method = method,
                requestHeaders = reqHeadersMap,
                requestBody = reqBody,
                requestBodyBase64 = reqBase64,
                statusCode = code,
                responseHeaders = resHeadersMap,
                responseBody = resBody,
                responseBodyBase64 = resBodyBase64,
                duration = System.currentTimeMillis() - startTime
            )
            dispatchRecord(record)
        } catch (e: Exception) {
            Log.capture(TAG, "captureOkHttpTraffic 异常: ${e.message}")
        }
    }
    

    private fun triggerStandardCapture(connection: java.net.HttpURLConnection) {
        val startTime = XposedHelpers.getAdditionalInstanceField(connection, "capture_start_time") as? Long 
            ?: System.currentTimeMillis()
            
        val url = connection.url.toString()
        val method = connection.requestMethod
        
        // 提取 Headers
        val headers = connection.requestProperties.mapValues { it.value.joinToString(", ") }
        
        // 提取状态码和响应头
        val code = try { connection.responseCode } catch (_: Exception) { -1 }
        val resHeaders = connection.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") }
        
        // 💡 从缓存中提取响应体
        val resData = XposedHelpers.getAdditionalInstanceField(connection, "captured_response_body") as? ByteArray
        val (resBody, resBase64) = processBody(resData)
        
        // 提取请求体
        val reqData = XposedHelpers.getAdditionalInstanceField(connection, "captured_request_body") as? ByteArray
        val (reqBody, reqBase64) = processBody(reqData)

        // ── 分发保存 ──
        val record = CaptureRecord(
            id = UUID.randomUUID().toString(),
            timestamp = startTime,
            url = url,
            method = method,
            requestHeaders = headers,
            requestBody = reqBody,
            requestBodyBase64 = reqBase64,
            statusCode = code,
            responseHeaders = resHeaders,
            responseBody = resBody,
            responseBodyBase64 = resBase64,
            duration = System.currentTimeMillis() - startTime
        )
        dispatchRecord(record)
    }

    private fun captureAlipayTraffic(request: Any, response: Any, startTime: Long) {
        try {
            // ── 提取 URL / Method ──
            val url = XposedHelpers.callMethod(request, "getUrl")?.toString() ?: "unknown"
            val method = XposedHelpers.callMethod(request, "getRequestMethod")?.toString() ?: "UNKNOWN"

            // ── 解析 URL ──
            val parsed = CaptureClassifier.parse(url)
            val host = parsed.host.ifEmpty { "unknown" }

            // ── 辅助函数：安全解码 ──
            fun safeDecode(value: String?): String {
                if (value == null) return ""
                if (!value.contains("%")) return value // 无编码直接返回
                return try {
                    java.net.URLDecoder.decode(value, "UTF-8")
                } catch (_: Throwable) { value }
            }

            // ── 提取请求头 ──
            val reqHeaders = mutableMapOf<String, String>()
            val reqHeadersList = XposedHelpers.callMethod(request, "getHeaders") as? List<*>
            reqHeadersList?.forEach { header ->
                if (header != null) {
                    val name = XposedHelpers.callMethod(header, "getName")?.toString()
                    val value = XposedHelpers.callMethod(header, "getValue")?.toString()
                    if (name != null) reqHeaders[name] = safeDecode(value)
                }
            }
            val operationType = reqHeaders["Operation-Type"] ?: reqHeaders["operation-type"]

            // ── 黑名单过滤 ──
            val filterKeywords = BaseModel.httpCaptureFilter.value
            if (!filterKeywords.isNullOrBlank()) {
                val keywords = filterKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (keywords.any { host.contains(it, ignoreCase = true) }) return
            }

            // ── 分类 ──
            val category = CaptureClassifier.classify(url, operationType)

            // ── 提取请求体 ──
            var errorMsg: String? = null
            // 尝试多种方式获取请求体
            val reqDataRaw = (XposedHelpers.callMethod(request, "getReqData") as? ByteArray)
                ?: (XposedHelpers.getObjectField(request, "mReqData") as? ByteArray)
            
            var reqBody: String? = null
            var reqBodyBase64: String? = null
            var reqBodySize = 0
            var isTruncated = false

            if (reqDataRaw != null) {
                if (reqDataRaw.size > MAX_EXTRACT_SIZE) {
                    errorMsg = "请求体过大 (${reqDataRaw.size} bytes)，跳过"
                } else {
                    reqBodySize = reqDataRaw.size
                    val processed = processBody(reqDataRaw)
                    reqBody = processed.first
                    reqBodyBase64 = processed.second
                }
            }

            // ── 提取响应 ──
            val statusCode = XposedHelpers.callMethod(response, "getCode") as? Int ?: 0
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // 提取响应头
            val resHeaders = mutableMapOf<String, String>()
            val httpUrlHeader = XposedHelpers.callMethod(response, "getHeader")
            if (httpUrlHeader != null) {
                try {
                    val headersMapRaw = XposedHelpers.getObjectField(httpUrlHeader, "mHeaders") as? Map<*, *>
                    headersMapRaw?.forEach { (k, v) ->
                        if (k != null) {
                            resHeaders[k.toString()] = safeDecode(v?.toString())
                        }
                    }
                } catch (e: Throwable) {
                    Log.error(TAG, "响应头提取失败: ${e.message}")
                }
            }
            val contentType = resHeaders["Content-Type"] ?: resHeaders["content-type"]

            // 提取响应体
            val resDataRaw = try {
                XposedHelpers.getObjectField(response, "mResData") as? ByteArray
            } catch (_: Throwable) { null }

            var resBody: String? = null
            var resBodyBase64: String? = null
            var resBodySize = 0

            if (resDataRaw != null) {
                if (resDataRaw.size > MAX_EXTRACT_SIZE) {
                    val msg = "响应体过大 (${resDataRaw.size} bytes)，跳过"
                    errorMsg = if (errorMsg == null) msg else "$errorMsg\n$msg"
                } else {
                    resBodySize = resDataRaw.size
                    val processed = processBody(resDataRaw)
                    resBody = processed.first
                    resBodyBase64 = processed.second
                    // 不再此处截断，交给 CaptureStorage 自动处理外置
                }
            }

            // ── 构建记录 ──
            val record = CaptureRecord(
                id = UUID.randomUUID().toString(),
                url = url,
                method = method,
                host = host,
                path = parsed.path,
                queryParams = parsed.queryParams,
                requestHeaders = reqHeaders,
                requestBody = reqBody,
                requestBodyBase64 = reqBodyBase64,
                requestBodySize = reqBodySize,
                statusCode = statusCode,
                responseHeaders = resHeaders,
                contentType = contentType,
                responseBody = resBody,
                responseBodyBase64 = resBodyBase64,
                responseBodySize = resBodySize,
                timestamp = startTime,
                duration = duration,
                category = category,
                isTruncated = isTruncated,
                errorMessage = errorMsg
            )

            // ── 持久化（异步执行，避免阻塞宿主应用线程） ──
            Thread {
                try {
                    CaptureStorage.save(record)
                } catch (e: Throwable) {
                    Log.error(TAG, "异步保存失败: ${e.message}")
                }
            }.start()

        } catch (e: Exception) {
            Log.error(TAG, "捕获异常: ${e.message}")
        }
    }

    /**
     * 处理 body ByteArray：
     * - 尝试 GZIP 解压
     * - 尝试 UTF-8 解码为文本 → 返回 (text, null)
     * - 无法解码 → 返回 (null, base64)
     */
    private fun processBody(data: ByteArray?): Pair<String?, String?> {
        if (data == null || data.isEmpty()) return Pair(null, null)

        // 1. GZIP 解压
        val decompressed = NetworkUtils.decompressGzip(data) ?: data

        // 2. 尝试 UTF-8 解码
        return try {
            val text = String(decompressed, Charsets.UTF_8)
            // 检查是否为可打印文本
            if (isPrintableText(text)) {
                val decoded = if (text.contains("%") && text.contains("=")) {
                    try { java.net.URLDecoder.decode(text, "UTF-8") } catch (_: Throwable) { text }
                } else text
                Pair(decoded, null)
            } else {
                Pair(null, Base64.encodeToString(decompressed, Base64.NO_WRAP))
            }
        } catch (_: Exception) {
            Pair(null, Base64.encodeToString(decompressed, Base64.NO_WRAP))
        }
    }

    /**
     * 判断字符串是否为可打印文本（排除二进制数据被误判为 UTF-8）。
     * 如果包含过多控制字符（换行/tab 除外），视为二进制。
     */
    private fun isPrintableText(text: String): Boolean {
        if (text.isEmpty()) return true
        var nonPrintable = 0
        val maxCheck = minOf(text.length, 4096)
        for (i in 0 until maxCheck) {
            val c = text[i]
            if (c < 0x20.toChar() && c != '\n' && c != '\r' && c != '\t') {
                nonPrintable++
            }
        }
        return nonPrintable.toFloat() / maxCheck < 0.05f
    }
    private fun hookARiverTraffic(classLoader: ClassLoader) {
        try {
            val serviceImpl = XposedHelpers.findClass("com.alipay.mobile.nebulax.integration.mpaas.proxy.impl.TransportServiceImpl", classLoader)
            XposedHelpers.findAndHookMethod(serviceImpl, "httpRequest", "com.alibaba.ariver.kernel.common.network.http.RVHttpRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, "capture_start_time", System.currentTimeMillis())
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!BaseModel.enableHttpCapture.value) return
                    try {
                        val request = param.args[0] ?: return
                        val response = param.result ?: return
                        
                        val startTime = XposedHelpers.getAdditionalInstanceField(param.thisObject, "capture_start_time") as? Long 
                            ?: System.currentTimeMillis()
                            
                        val url = XposedHelpers.callMethod(request, "getUrl") as String
                        val method = XposedHelpers.callMethod(request, "getMethod") as String
                        val reqHeaders = XposedHelpers.callMethod(request, "getHeaders") as? Map<String, String> ?: emptyMap()
                        val reqData = XposedHelpers.callMethod(request, "getRequestData") as? ByteArray
                        
                        val code = XposedHelpers.callMethod(response, "getStatusCode") as Int
                        val resHeaders = XposedHelpers.callMethod(response, "getHeaders") as? Map<String, List<String>> ?: emptyMap()
                        val flatResHeaders = resHeaders.mapValues { it.value.joinToString(", ") }
                        
                        val originalStream = XposedHelpers.callMethod(response, "getResStream") as? java.io.InputStream
                        if (originalStream != null && originalStream !is CaptureInputStream) {
                            val id = UUID.randomUUID().toString()
                            val captureStream = CaptureInputStream(originalStream) { data: ByteArray ->
                                val (reqBody, reqBase64) = processBody(reqData)
                                val (resBody, resBase64) = processBody(data)
                                
                                val record = CaptureRecord(
                                    id = id,
                                    timestamp = startTime,
                                    url = url,
                                    method = method,
                                    requestHeaders = reqHeaders,
                                    requestBody = reqBody,
                                    requestBodyBase64 = reqBase64,
                                    statusCode = code,
                                    responseHeaders = flatResHeaders,
                                    responseBody = resBody,
                                    responseBodyBase64 = resBase64,
                                    duration = System.currentTimeMillis() - startTime
                                )
                                dispatchRecord(record)
                            }
                            XposedHelpers.callMethod(response, "setResStream", captureStream)
                        }
                    } catch (e: Throwable) {
                        Log.capture(TAG, "ARiver Hook 异常: ${e.message}")
                    }
                }
            })

            // ── Hook 下载请求 ──
            XposedHelpers.findAndHookMethod(CLASS_TRANSPORT_SERVICE_IMPL, classLoader, "addDownload",
                "com.alibaba.ariver.kernel.common.network.download.RVDownloadRequest",
                "com.alibaba.ariver.kernel.common.network.download.RVDownloadCallback",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val request = param.args[0] ?: return
                            val url = XposedHelpers.callMethod(request, "getDownloadUrl") as? String ?: return
                            val fileName = XposedHelpers.callMethod(request, "getDownloadFileName") as? String ?: "unknown"
                            
                            val record = CaptureRecord(
                                id = UUID.randomUUID().toString(),
                                url = url,
                                method = "GET",
                                requestHeaders = mapOf("X-Download-File" to fileName),
                                statusCode = 200,
                                responseHeaders = mapOf("Content-Type" to "application/octet-stream"),
                                responseBody = "[Download Initiated: $fileName]",
                                category = "Download",
                                timestamp = System.currentTimeMillis()
                            )
                            dispatchRecord(record)
                        } catch (e: Throwable) {
                            Log.error(TAG, "Download Hook 异常: ${e.message}")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.error(TAG, "未找到 ARiver 传输类: ${e.message}")
        }
    }

    /**
     * 记录分发中心：统一处理记录的异步保存与全局过滤
     */
    private fun dispatchRecord(record: CaptureRecord) {
        Thread {
            try {
                CaptureStorage.save(record)
            } catch (e: Throwable) {
                Log.error(TAG, "记录分发保存失败 [ID: ${record.id}]: ${e.message}")
            }
        }.start()
    }

    /**
     * 辅助类：拦截输入流数据
     * 采用双重缓冲：小数据留内存，大数据溢出到临时文件，彻底解决 OOM
     */
    private class CaptureInputStream(
        inputStream: java.io.InputStream,
        private val onClose: (ByteArray) -> Unit
    ) : java.io.FilterInputStream(inputStream) {
        private val memBuffer = java.io.ByteArrayOutputStream()
        private var fileBuffer: java.io.File? = null
        private var fileOut: java.io.FileOutputStream? = null
        private var totalSize = 0L
        private var isClosed = false
        
        private val MAX_MEM_SIZE = 1 * 1024 * 1024 // 1MB 内存门槛
        private val MAX_TOTAL_SIZE = 10 * 1024 * 1024 // 10MB 总上限

        private fun writeData(b: Int) {
            if (totalSize >= MAX_TOTAL_SIZE) return
            totalSize++
            try {
                if (fileOut != null) {
                    fileOut?.write(b)
                } else if (memBuffer.size() < MAX_MEM_SIZE.toLong()) {
                    memBuffer.write(b)
                } else {
                    switchToDisk()
                    fileOut?.write(b)
                }
            } catch (_: Exception) {}
        }

        private fun writeData(b: ByteArray, off: Int, len: Int) {
            val toWrite = Math.min(len.toLong(), MAX_TOTAL_SIZE - totalSize).toInt()
            if (toWrite <= 0) return
            totalSize += toWrite
            try {
                if (fileOut != null) {
                    fileOut?.write(b, off, toWrite)
                } else if (memBuffer.size() + toWrite < MAX_MEM_SIZE) {
                    memBuffer.write(b, off, toWrite)
                } else {
                    switchToDisk()
                    fileOut?.write(b, off, toWrite)
                }
            } catch (_: Exception) {}
        }

        private fun switchToDisk() {
            try {
                val temp = java.io.File.createTempFile("sesame_cap_", ".tmp")
                val out = java.io.FileOutputStream(temp)
                out.write(memBuffer.toByteArray())
                memBuffer.reset()
                fileBuffer = temp
                fileOut = out
            } catch (e: Exception) {
                Log.error("HttpCaptureHook", "切换磁盘缓冲失败: ${e.message}")
            }
        }

        override fun read(): Int {
            val b = super.read()
            if (b != -1) writeData(b)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val r = super.read(b, off, len)
            if (r != -1) writeData(b, off, r)
            return r
        }

        override fun close() {
            super.close()
            if (!isClosed) {
                isClosed = true
                try {
                    fileOut?.close()
                    val finalData = if (fileBuffer != null) {
                        val bytes = fileBuffer!!.readBytes()
                        fileBuffer!!.delete()
                        bytes
                    } else {
                        memBuffer.toByteArray()
                    }
                    onClose(finalData)
                } catch (e: Exception) {
                    Log.error("HttpCaptureHook", "流关闭回调异常: ${e.message}")
                }
            }
        }
    }
}
