package fansirsqi.xposed.sesame.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
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
    private var responseReceiver: android.content.BroadcastReceiver? = null

    fun initFromRecord(record: CaptureRecord, initialBody: String?) {
        this.originalRecord = record
        method.value = record.method.ifEmpty { "GET" }
        url.value = record.url
        body.value = initialBody ?: ""
        headers.value = record.requestHeaders.map { it.key to it.value }
    }

    private fun registerReceiver(context: android.content.Context) {
        if (responseReceiver != null) return
        responseReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                if (intent.action == "com.eg.android.AlipayGphone.sesame.rpcresponse") {
                    val resultText = intent.getStringExtra("result")
                    if (resultText != null) {
                        _result.value = ResendResult(
                            code = 200,
                            headers = mapOf("Source" to "RPC Broadcast Response"),
                            body = resultText,
                            duration = 0,
                            isSuccess = true
                        )
                        _isSending.value = false
                        this@CaptureResendViewModel.unregisterReceiver(context)
                    }
                }
            }
        }
        val filter = android.content.IntentFilter("com.eg.android.AlipayGphone.sesame.rpcresponse")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(responseReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(responseReceiver, filter)
        }
    }

    private fun unregisterReceiver(context: android.content.Context) {
        responseReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        responseReceiver = null
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
        
        // 如果是单次 RPC 重发，注册监听器以获取异步回调
        val isRpc = headers.value.any { it.first.equals("Operation-Type", true) }
        if (isRpc && count == 1) {
            registerReceiver(context)
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSending.value = true
            _result.value = null
            
            var lastResult: ResendResult? = null
            var successCount = 0
            
            for (i in 0 until count) {
                if (count > 1 && i > 0) {
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
                // 如果是 RPC，不需要立即结束 isSending，等待广播回调（或超时）
                if (!isRpc) {
                    _result.value = lastResult
                    _isSending.value = false
                } else {
                    // RPC 模式下，如果 15 秒没收到广播，则视为超时
                    _result.value = lastResult // 先显示“已发送”提示
                    kotlinx.coroutines.delay(15000)
                    if (_isSending.value) {
                        _result.value = ResendResult(
                            -1, emptyMap(), "等待 RPC 响应超时。请检查抓包列表确认请求是否成功。", 0, false
                        )
                        _isSending.value = false
                        unregisterReceiver(context)
                    }
                }
            } else {
                _isSending.value = false
            }
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
                // 💡 必须设置包名，且确保接收端已处理此广播
                intent.setPackage("com.eg.android.AlipayGphone") 
                context.sendBroadcast(intent)
                
                return ResendResult(
                    code = 200,
                    headers = mapOf("X-Sesame-Proxy" to "Broadcast Sent"),
                    body = "已发送 RPC 模拟请求广播，正在等待回调...",
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

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时清理 receiver 是保险做法，但通常 context 会先失效
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
        // 处理 cURL 风格的换行符
        val cleanedRaw = raw.replace("\\\n", " ").replace("\\\r\n", " ")
        val trimmed = cleanedRaw.trim()
        if (trimmed.startsWith("curl", true)) {
            parseCurl(trimmed)
            return
        }
        val lines = cleanedRaw.lines()
        if (lines.isEmpty()) return
        
        // 解析第一行: METHOD URL HTTP/1.1
        val firstLineParts = lines[0].trim().split("\\s+".toRegex())
        if (firstLineParts.size >= 2) {
            method.value = firstLineParts[0].uppercase()
            url.value = firstLineParts[1]
        }
        
        val newHeaders = mutableListOf<Pair<String, String>>()
        var i = 1
        // 解析 Header 直到遇到空行
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                break
            }
            if (line.contains(":")) {
                newHeaders.add(line.substringBefore(":").trim() to line.substringAfter(":").trim())
            }
            i++
        }
        if (newHeaders.isNotEmpty()) headers.value = newHeaders
        
        // 剩余部分全部作为 Body
        if (i < lines.size) {
            body.value = lines.subList(i, lines.size).joinToString("\n")
        } else if (i == lines.size && lines.last().isEmpty() && lines.size > 2) {
            // 处理只有一个空行结尾的情况
            body.value = ""
        }
    }

    private fun parseCurl(curl: String) {
        // 1. 处理换行符和多余空格
        var normalized = curl.replace("\\\n", " ").replace("\\\r\n", " ").trim()
        
        // 2. 处理 Bash 风格的单引号转义: '\'' -> 替换为特殊占位符再处理
        val QUOTE_PLACEHOLDER = "___SESAME_QUOTE___"
        normalized = normalized.replace("'\\''", QUOTE_PLACEHOLDER)

        // 3. 提取 URL
        // 支持无引号、单引号、双引号包裹的 URL
        val urlRegex = "(?:^|\\s)(?:-X\\s+\\w+\\s+)?(['\"]?)(https?://[^'\"\\s]+)\\1".toRegex()
        urlRegex.find(normalized)?.let { 
            url.value = it.groupValues[2].replace(QUOTE_PLACEHOLDER, "'")
        }

        // 4. 提取方法
        method.value = when {
            normalized.contains("-X POST", true) || 
            normalized.contains("--data", true) || 
            normalized.contains("-d ", true) ||
            normalized.contains("--data-raw", true) ||
            normalized.contains("--data-binary", true) -> "POST"
            normalized.contains("-X PUT", true) -> "PUT"
            normalized.contains("-X DELETE", true) -> "DELETE"
            normalized.contains("-X PATCH", true) -> "PATCH"
            else -> "GET"
        }

        // 5. 提取 Headers
        val headerRegex = "-H\\s+(['\"])(.*?)\\1".toRegex()
        val foundHeaders = headerRegex.findAll(normalized).map { 
            val content = it.groupValues[2].replace(QUOTE_PLACEHOLDER, "'")
            content.substringBefore(":").trim() to content.substringAfter(":").trim()
        }.filter { it.first.isNotEmpty() }.toList()
        if (foundHeaders.isNotEmpty()) headers.value = foundHeaders

        // 6. 提取 Body
        // 优化正则以支持 --data-raw 和多重 data 段，且处理嵌套引号
        val bodyRegex = "(?:--data|--data-raw|--data-binary|-d)\\s+(['\"])(.*?)\\1".toRegex(RegexOption.DOT_MATCHES_ALL)
        val bodyParts = bodyRegex.findAll(normalized).map { 
            it.groupValues[2].replace(QUOTE_PLACEHOLDER, "'")
        }.toList()
        
        if (bodyParts.isNotEmpty()) {
            body.value = bodyParts.joinToString("&")
        }
    }

    data class ResendResult(val code: Int, val headers: Map<String, String>, val body: String, val duration: Long, val isSuccess: Boolean)
}
