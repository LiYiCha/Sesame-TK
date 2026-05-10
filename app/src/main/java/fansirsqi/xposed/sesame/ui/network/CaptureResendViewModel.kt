package fansirsqi.xposed.sesame.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CaptureResendViewModel : ViewModel() {

    val method = MutableStateFlow("GET")
    val url = MutableStateFlow("")
    val headers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val body = MutableStateFlow("")
    private var originalRecord: CaptureRecord? = null

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    val batchCount = MutableStateFlow(1) // 批量重发次数

    private val _result = MutableStateFlow<ResendResult?>(null)
    val result: StateFlow<ResendResult?> = _result

    private val client = OkHttpClient()

    fun initFromRecord(record: CaptureRecord, initialBody: String?) {
        this.originalRecord = record
        method.value = record.method.ifEmpty { "GET" }
        url.value = record.url
        body.value = initialBody ?: ""
        headers.value = record.requestHeaders.map { it.key to it.value }
    }

    fun clear() {
        method.value = "GET"
        url.value = ""
        body.value = ""
        headers.value = emptyList()
        _result.value = null
    }

    fun sendRequest(context: android.content.Context) {
        if (_isSending.value || url.value.isBlank()) return
        val count = batchCount.value
        viewModelScope.launch(Dispatchers.IO) {
            _isSending.value = true
            _result.value = null
            
            var lastResult: ResendResult? = null
            var successCount = 0
            
            for (i in 0 until count) {
                if (count > 1 && i > 0) {
                    // 模拟随机延迟：800ms ~ 3000ms
                    kotlinx.coroutines.delay((800..3000).random().toLong())
                }
                
                try {
                    val currentResult = executeSingleRequest(context)
                    lastResult = currentResult
                    if (currentResult.isSuccess) successCount++
                } catch (e: Exception) {
                    lastResult = ResendResult(-1, emptyMap(), "Error at $i: ${e.message}", 0, false)
                }
                
                if (count > 1) {
                    _result.value = ResendResult(
                        lastResult?.code ?: 0,
                        lastResult?.headers ?: emptyMap(),
                        "批量重发进度: ${i + 1}/$count (成功: $successCount)\n---\n${lastResult?.body}",
                        lastResult?.duration ?: 0,
                        lastResult?.isSuccess ?: false
                    )
                }
            }
            
            if (count == 1) {
                _result.value = lastResult
            }
            _isSending.value = false
        }
    }

    private fun executeSingleRequest(context: android.content.Context): ResendResult {
        val start = System.currentTimeMillis()
        try {
            val contentType = headers.value.find { it.first.equals("Content-Type", true) }?.second ?: "application/json"
            val mediaType = contentType.toMediaTypeOrNull()
            val reqBuilder = Request.Builder().url(url.value)
            
            val finalBody = if (body.value.isEmpty() && originalRecord?.requestBodyBase64 != null) {
                try {
                    android.util.Base64.decode(originalRecord!!.requestBodyBase64, android.util.Base64.NO_WRAP)
                } catch (_: Exception) { null }
            } else body.value.toByteArray()

            val reqBody = if (method.value == "GET" || method.value == "HEAD" || (finalBody == null || finalBody.isEmpty())) null
            else finalBody.toRequestBody(mediaType)
            
            reqBuilder.method(method.value, reqBody)
            
            var isRpc = false
            var rpcMethod = ""
            headers.value.forEach { (k, v) ->
                if (k.isNotBlank()) {
                    val lowerK = k.lowercase()
                    if (lowerK != "content-type" && lowerK != "content-length" && 
                        lowerK != "host" && lowerK != "connection") {
                        reqBuilder.header(k, v)
                    }
                    if (lowerK == "operation-type") {
                        isRpc = true
                        rpcMethod = v
                    }
                }
            }

            if (isRpc && rpcMethod.isNotBlank()) {
                val intent = android.content.Intent("com.eg.android.AlipayGphone.sesame.rpctest")
                intent.putExtra("method", rpcMethod)
                intent.putExtra("data", body.value)
                intent.putExtra("type", "Rpc")
                intent.setPackage("com.eg.android.AlipayGphone") 
                context.sendBroadcast(intent)
                
                return ResendResult(
                    code = 200,
                    headers = mapOf("X-Sesame-Proxy" to "Broadcast Sent"),
                    body = "已通过 RPC 代理发送广播。由于广播是异步的，请在抓包列表查看最新的响应结果。",
                    duration = System.currentTimeMillis() - start,
                    isSuccess = true
                )
            } else {
                client.newCall(reqBuilder.build()).execute().use { response ->
                    return ResendResult(
                        code = response.code,
                        headers = response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                        body = response.body?.string() ?: "",
                        duration = System.currentTimeMillis() - start,
                        isSuccess = response.isSuccessful
                    )
                }
            }
        } catch (e: Exception) {
            return ResendResult(-1, emptyMap(), "请求异常: ${e.message}", System.currentTimeMillis() - start, false)
        }
    }

    fun addHeader() { headers.value = headers.value + ("" to "") }
    fun removeHeader(index: Int) {
        val list = headers.value.toMutableList()
        if (index in list.indices) { list.removeAt(index); headers.value = list }
    }
    fun updateHeader(index: Int, key: String, value: String) {
        val list = headers.value.toMutableList()
        if (index in list.indices) { list[index] = key to value; headers.value = list }
    }

    fun importRawRequest(raw: String) {
        if (raw.isBlank()) return
        val trimmed = raw.trim()
        if (trimmed.startsWith("curl", true)) { parseCurl(trimmed); return }
        val lines = raw.lines()
        if (lines.isEmpty()) return
        val parts = lines[0].trim().split(" ")
        if (parts.size >= 2) {
            method.value = parts[0].uppercase()
            url.value = if (parts[1].startsWith("http")) parts[1] else parts[1]
        }
        val newHeaders = mutableListOf<Pair<String, String>>()
        var bodyStart = -1
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) { bodyStart = i + 1; break }
            if (line.contains(":")) newHeaders.add(line.substringBefore(":").trim() to line.substringAfter(":").trim())
        }
        if (newHeaders.isNotEmpty()) headers.value = newHeaders
        if (bodyStart in 1..lines.size) body.value = lines.subList(bodyStart, lines.size).joinToString("\n")
    }

    private fun parseCurl(curl: String) {
        val urlRegex = "(https?://[^'\"\\s]+)".toRegex()
        urlRegex.find(curl)?.let { url.value = it.value }
        method.value = when {
            curl.contains("-X POST", true) || curl.contains("--data", true) || curl.contains("-d ", true) -> "POST"
            curl.contains("-X PUT", true) -> "PUT"
            curl.contains("-X DELETE", true) -> "DELETE"
            else -> "GET"
        }
        val headerRegex = "-H\\s+['\"]([^'\"]+)['\"]".toRegex()
        val found = headerRegex.findAll(curl).map { val c = it.groupValues[1]; c.substringBefore(":").trim() to c.substringAfter(":").trim() }.toList()
        if (found.isNotEmpty()) headers.value = found
        val bodyRegex = "(-d|--data|--data-raw)\\s+['\"](.*?)['\"](?=\\s+-[HXA]|\\s*$)".toRegex(RegexOption.DOT_MATCHES_ALL)
        bodyRegex.find(curl)?.let { body.value = it.groupValues[2] }
    }

    data class ResendResult(val code: Int, val headers: Map<String, String>, val body: String, val duration: Long, val isSuccess: Boolean)
}
