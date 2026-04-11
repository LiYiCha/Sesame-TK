package fansirsqi.xposed.sesame.hook.network

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
import java.net.URI

/**
 * HTTP/HTTPS 抓包 Hook 核心类
 * 采用组件化设计，支持多种 Hook 点扩展。
 */
object HttpCaptureHook {
    private const val TAG = "HttpCaptureHook"
    private const val CLASS_HTTP_WORKER = "com.alipay.mobile.common.transport.http.HttpWorker"
    private const val CLASS_HTTP_URL_REQUEST = "com.alipay.mobile.common.transport.http.HttpUrlRequest"
    private const val CLASS_HTTP_URL_RESPONSE = "com.alipay.mobile.common.transport.http.HttpUrlResponse"

    private const val MAX_BODY_SIZE = 5 * 1024 * 1024 // 5MB 限制

    @JvmStatic
    fun setup(classLoader: ClassLoader) {
        // 在 Hook 之前，确保配置已从 DataStore 加载到当前进程 (支付宝)
        try {
            fansirsqi.xposed.sesame.util.DataStore.init(fansirsqi.xposed.sesame.util.Files.CONFIG_DIR)
        } catch (e: Throwable) {
            Log.capture(TAG, "初始化 DataStore 失败: ${e.message}")
        }
        hookAlipayHttpWorker(classLoader)
    }

    /**
     * Hook 支付宝核心 HttpWorker，捕获绝大多数内部业务请求
     */
    private fun hookAlipayHttpWorker(classLoader: ClassLoader) {
        try {
            val handleResponseMethod = XposedHelpers.findMethodExact(
                CLASS_HTTP_WORKER, classLoader, "handleResponse",
                CLASS_HTTP_URL_REQUEST, "org.apache.http.HttpResponse", "int", "java.lang.String"
            )

            XposedBridge.hookMethod(handleResponseMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 在请求发起前记录绝对时间
                    XposedHelpers.setAdditionalInstanceField(param, "capture_start_time", System.currentTimeMillis())
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    // 动态检查开关状态
                    if (!BaseModel.enableHttpCapture.value) return

                    try {
                        val request = param.args[0] ?: return
                        val response = param.result ?: return
                        
                        if (response.javaClass.name != CLASS_HTTP_URL_RESPONSE) return

                        val startTime = XposedHelpers.getAdditionalInstanceField(param, "capture_start_time") as? Long ?: System.currentTimeMillis()

                        // 同步处理，确保能捕获可能的 I/O 异常
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

    /**
     * 解析并保存支付宝流量数据
     */
    private fun captureAlipayTraffic(request: Any, response: Any, startTime: Long) {
        try {
            // --- 提取请求信息 ---
            val url = XposedHelpers.callMethod(request, "getUrl")?.toString() ?: "unknown"
            val method = XposedHelpers.callMethod(request, "getRequestMethod")?.toString() ?: "UNKNOWN"
            
            var currentHost = "unknown"
            try {
                val uri = URI(url)
                currentHost = uri.host ?: ""
                if (currentHost.isEmpty() && url.contains("://")) {
                    currentHost = url.substringAfter("://").substringBefore("/").substringBefore("?")
                }
            } catch (e: Exception) {
                currentHost = "invalid-url"
            }

            // --- 域名过滤逻辑 ---
            val filterKeywords = BaseModel.httpCaptureFilter.value
            if (!filterKeywords.isNullOrBlank()) {
                val keywords = filterKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val match = keywords.find { currentHost.contains(it, ignoreCase = true) }
                if (match != null) {
                    Log.capture(TAG, "🚫 拦截黑名单请求: $currentHost (匹配关键词: $match)")
                    return
                }
            }

            val reqHeadersMap = mutableMapOf<String, String>()
            val reqHeadersList = XposedHelpers.callMethod(request, "getHeaders") as? List<*>
            reqHeadersList?.forEach { header ->
                if (header != null) {
                    val name = XposedHelpers.callMethod(header, "getName")?.toString()
                    val value = XposedHelpers.callMethod(header, "getValue")?.toString()
                    if (name != null) reqHeadersMap[name] = value ?: ""
                }
            }
            
            // 提取请求体
            val reqDataRaw = XposedHelpers.callMethod(request, "getReqData") as? ByteArray
            var errorMsg: String? = null
            val reqData = if (reqDataRaw != null && reqDataRaw.size > MAX_BODY_SIZE) {
                errorMsg = "Request body too large (${reqDataRaw.size} bytes), skipped."
                null
            } else {
                reqDataRaw
            }
            
            // --- 提取响应信息 ---
            val responseCode = XposedHelpers.callMethod(response, "getCode") as? Int ?: 0
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            // 提取响应头
            val resHeadersMap = mutableMapOf<String, String>()
            val httpUrlHeader = XposedHelpers.callMethod(response, "getHeader")
            if (httpUrlHeader != null) {
                val headersMapRaw = try {
                    XposedHelpers.getObjectField(httpUrlHeader, "mHeaders") as? Map<*, *>
                } catch (e: Throwable) {
                    null
                }
                headersMapRaw?.forEach { (k, v) ->
                    if (k != null) resHeadersMap[k.toString()] = v?.toString() ?: ""
                }
            }
            val contentType = resHeadersMap["Content-Type"] ?: resHeadersMap["content-type"]
            
            // 提取响应体
            val resDataRaw = try {
                XposedHelpers.getObjectField(response, "mResData") as? ByteArray
            } catch (e: Throwable) {
                null
            }
            val resData = if (resDataRaw != null && resDataRaw.size > MAX_BODY_SIZE) {
                val msg = "Response body too large (${resDataRaw.size} bytes), capture skipped to prevent OOM."
                errorMsg = if (errorMsg == null) msg else "$errorMsg\n$msg"
                null
            } else {
                resDataRaw
            }

            // --- 创建不可变数据包 ---
            val packet = CapturePacket(
                url = url,
                method = method,
                host = currentHost,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                requestHeaders = reqHeadersMap,
                responseHeaders = resHeadersMap,
                responseCode = responseCode,
                errorMessage = errorMsg,
                contentType = contentType,
                protocol = "HTTP"
            )

            // --- 文件持久化 ---
            CaptureFileManager.save(packet, reqData, resData)

        } catch (e: Exception) {
            // 静默处理
        }
    }
}
