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

    /** 提取阶段的最大字节限制 (5MB)，防止 OOM */
    private const val MAX_EXTRACT_SIZE = 5 * 1024 * 1024
    /** 内联存储的最大字节限制 (200KB)，超出截断 */
    private const val MAX_INLINE_SIZE = 200 * 1024

    @JvmStatic
    fun setup(classLoader: ClassLoader) {
        try {
            fansirsqi.xposed.sesame.util.DataStore.init(fansirsqi.xposed.sesame.util.Files.CONFIG_DIR)
        } catch (e: Throwable) {
            Log.capture(TAG, "初始化 DataStore 失败: ${e.message}")
        }
        hookAlipayHttpWorker(classLoader)
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
                        Log.capture(TAG, "Hook执行异常: ${e.message}")
                    }
                }
            })
        } catch (e: Throwable) {
            Log.capture(TAG, "注册 Hook 异常: ${e.message}")
        }
    }

    private fun captureAlipayTraffic(request: Any, response: Any, startTime: Long) {
        try {
            // ── 提取 URL / Method ──
            val url = XposedHelpers.callMethod(request, "getUrl")?.toString() ?: "unknown"
            val method = XposedHelpers.callMethod(request, "getRequestMethod")?.toString() ?: "UNKNOWN"

            // ── 解析 URL ──
            val parsed = CaptureClassifier.parse(url)
            val host = parsed.host.ifEmpty { "unknown" }

            // ── 黑名单过滤 ──
            val filterKeywords = BaseModel.httpCaptureFilter.value
            if (!filterKeywords.isNullOrBlank()) {
                val keywords = filterKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (keywords.any { host.contains(it, ignoreCase = true) }) return
            }

            // ── 分类 ──
            val category = CaptureClassifier.classify(url)

            // ── 提取请求头 ──
            val reqHeaders = mutableMapOf<String, String>()
            val reqHeadersList = XposedHelpers.callMethod(request, "getHeaders") as? List<*>
            reqHeadersList?.forEach { header ->
                if (header != null) {
                    val name = XposedHelpers.callMethod(header, "getName")?.toString()
                    val value = XposedHelpers.callMethod(header, "getValue")?.toString()
                    if (name != null) reqHeaders[name] = value ?: ""
                }
            }

            // ── 提取请求体 ──
            var errorMsg: String? = null
            val reqDataRaw = XposedHelpers.callMethod(request, "getReqData") as? ByteArray
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
                    if (reqBodySize > MAX_INLINE_SIZE) {
                        isTruncated = true
                        if (reqBody != null) reqBody = reqBody!!.take(MAX_INLINE_SIZE)
                        if (reqBodyBase64 != null) reqBodyBase64 = reqBodyBase64!!.take(MAX_INLINE_SIZE)
                    }
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
                        if (k != null) resHeaders[k.toString()] = v?.toString() ?: ""
                    }
                } catch (_: Throwable) {}
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
                    if (resBodySize > MAX_INLINE_SIZE) {
                        isTruncated = true
                        if (resBody != null) resBody = resBody!!.take(MAX_INLINE_SIZE)
                        if (resBodyBase64 != null) resBodyBase64 = resBodyBase64!!.take(MAX_INLINE_SIZE)
                    }
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

            // ── 持久化 ──
            CaptureStorage.save(record)

        } catch (_: Exception) {}
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
                Pair(text, null)
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
}
