package fansirsqi.xposed.sesame.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class NetworkResendViewModel : ViewModel() {

    // 可编辑的请求状态
    val method = MutableStateFlow("GET")
    val url = MutableStateFlow("")
    val headers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val body = MutableStateFlow("")

    // 响应状态
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _resendResult = MutableStateFlow<ResendResult?>(null)
    val resendResult: StateFlow<ResendResult?> = _resendResult

    private val client = OkHttpClient()

    /**
     * 初始化编辑器，从原始数据包中加载
     */
    fun initFromPacket(packet: CapturePacket, initialBody: String?) {
        method.value = packet.method ?: "GET"
        url.value = packet.url ?: ""
        body.value = initialBody ?: ""
        headers.value = packet.requestHeaders?.map { it.key to it.value } ?: emptyList()
    }

    /**
     * 执行重发请求
     */
    fun sendRequest() {
        if (_isSending.value || url.value.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isSending.value = true
            _resendResult.value = null
            val startTime = System.currentTimeMillis()

            try {
                // 1. 尝试从 Header 中获取 Content-Type
                val contentTypeStr = headers.value.find { it.first.equals("Content-Type", true) }?.second 
                    ?: "application/json"
                val mediaType = contentTypeStr.toMediaTypeOrNull()

                // 2. 构建请求
                val requestBuilder = Request.Builder().url(url.value)
                
                // 处理 Method 和 Body
                val requestBody = if (method.value == "GET" || method.value == "HEAD" || body.value.isEmpty()) {
                    null
                } else {
                    body.value.toRequestBody(mediaType)
                }
                requestBuilder.method(method.value, requestBody)

                // 注入 Headers (排除 Content-Type，因为 OkHttp 会在 Body 中处理它)
                headers.value.forEach { (k, v) ->
                    if (k.isNotBlank() && !k.equals("Content-Type", true)) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                val request = requestBuilder.build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    val responseHeaders = response.headers.toMultimap().mapValues { it.value.joinToString(", ") }
                    
                    _resendResult.value = ResendResult(
                        code = response.code,
                        headers = responseHeaders,
                        body = responseBody,
                        duration = System.currentTimeMillis() - startTime,
                        isSuccess = response.isSuccessful
                    )
                }
            } catch (e: Exception) {
                _resendResult.value = ResendResult(
                    code = -1,
                    headers = emptyMap(),
                    body = "请求失败: ${e.message}",
                    duration = System.currentTimeMillis() - startTime,
                    isSuccess = false
                )
            } finally {
                _isSending.value = false
            }
        }
    }

    fun addHeader() {
        headers.value = headers.value + ("" to "")
    }

    fun removeHeader(index: Int) {
        val list = headers.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            headers.value = list
        }
    }

    fun updateHeader(index: Int, key: String, value: String) {
        val list = headers.value.toMutableList()
        if (index in list.indices) {
            list[index] = key to value
            headers.value = list
        }
    }

    /**
     * 导入并解析原始 HTTP 请求文本 (支持 Raw HTTP 和 cURL)
     */
    fun importRawRequest(rawText: String) {
        if (rawText.isBlank()) return
        
        val trimmedText = rawText.trim()
        
        // --- 1. 支持 cURL 命令解析 ---
        if (trimmedText.startsWith("curl", ignoreCase = true)) {
            parseCurl(trimmedText)
            return
        }

        val lines = rawText.lines()
        if (lines.isEmpty()) return

        // --- 2. 原始 HTTP 报文解析 ---
        // 解析请求行 (e.g. POST /api/v1/user HTTP/1.1)
        val firstLine = lines[0].trim()
        val parts = firstLine.split(" ")
        if (parts.size >= 2) {
            method.value = parts[0].uppercase()
            val potentialUrl = parts[1]
            if (potentialUrl.startsWith("http")) {
                url.value = potentialUrl
            } else if (url.value.startsWith("http")) {
                val base = url.value.substringBefore("/", "http://localhost")
                url.value = if (potentialUrl.startsWith("/")) "$base$potentialUrl" else "$base/$potentialUrl"
            } else {
                url.value = potentialUrl
            }
        }

        // 解析 Headers 和 Body
        val newHeaders = mutableListOf<Pair<String, String>>()
        var bodyStartIndex = -1
        
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                bodyStartIndex = i + 1
                break
            }
            if (line.contains(":")) {
                val key = line.substringBefore(":").trim()
                val value = line.substringAfter(":").trim()
                newHeaders.add(key to value)
            }
        }
        
        if (newHeaders.isNotEmpty()) headers.value = newHeaders
        if (bodyStartIndex != -1 && bodyStartIndex < lines.size) {
            body.value = lines.subList(bodyStartIndex, lines.size).joinToString("\n")
        }
    }

    /**
     * 极简 cURL 解析逻辑 (支持提取 URL, Method, Headers)
     */
    private fun parseCurl(curl: String) {
        // 提取 URL (寻找单引号或双引号包裹的 http 字符串)
        val urlRegex = "(https?://[^'\"\\s]+)".toRegex()
        val urlMatch = urlRegex.find(curl)
        urlMatch?.let { url.value = it.value }

        // 提取 Method
        method.value = when {
            curl.contains("-X POST", true) || curl.contains("--data", true) || curl.contains("-d ", true) -> "POST"
            curl.contains("-X PUT", true) -> "PUT"
            curl.contains("-X DELETE", true) -> "DELETE"
            else -> "GET"
        }

        // 提取 Headers (简单匹配 -H "Key: Value")
        val headerRegex = "-H\\s+['\"]([^'\"]+)['\"]".toRegex()
        val matches = headerRegex.findAll(curl)
        val newHeaders = matches.map {
            val content = it.groupValues[1]
            val key = content.substringBefore(":").trim()
            val value = content.substringAfter(":").trim()
            key to value
        }.toList()
        
        if (newHeaders.isNotEmpty()) {
            headers.value = newHeaders
        }

        // 提取 Body (支持 -d '...' 或 --data '...')
        val bodyRegex = "(-d|--data|--data-raw)\\s+['\"](.*?)['\"](?=\\s+-[HXA]|\\s*$)".toRegex(RegexOption.DOT_MATCHES_ALL)
        val bodyMatch = bodyRegex.find(curl)
        bodyMatch?.let { body.value = it.groupValues[2] }
    }

    data class ResendResult(
        val code: Int,
        val headers: Map<String, String>,
        val body: String,
        val duration: Long,
        val isSuccess: Boolean
    )
}
