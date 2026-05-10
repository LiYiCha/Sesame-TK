package fansirsqi.xposed.sesame.ui.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.hook.network.CaptureStorage
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CaptureDetailViewModel : ViewModel() {

    private val _record = MutableStateFlow<CaptureRecord?>(null)
    val record: StateFlow<CaptureRecord?> = _record

    /** 预分行后的请求体行列表（IO 线程计算，避免主线程 split 卡顿） */
    private val _requestLines = MutableStateFlow<List<String>>(emptyList())
    val requestLines: StateFlow<List<String>> = _requestLines

    private val _responseLines = MutableStateFlow<List<String>>(emptyList())
    val responseLines: StateFlow<List<String>> = _responseLines

    /** 原始请求体文本（用于重发） */
    private val _requestBodyRaw = MutableStateFlow<String?>(null)
    val requestBodyRaw: StateFlow<String?> = _requestBodyRaw

    private val _responseImage = MutableStateFlow<Bitmap?>(null)
    val responseImage: StateFlow<Bitmap?> = _responseImage

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    fun loadRecord(id: String, date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 最多重试 3 次，应对写-读竞态
            var rec: CaptureRecord? = null
            for (attempt in 0..2) {
                rec = CaptureStorage.loadById(id, date)
                if (rec != null) break
                if (attempt < 2) kotlinx.coroutines.delay(150)
                else {
                    // 最后一次尝试：直接扫所有文件的最新行
                    val files = CaptureStorage.listAllFiles()
                    for (f in files) {
                        try {
                            val lastLine = f.readLines().lastOrNull { it.contains("\"id\":\"$id\"") }
                            if (lastLine != null) {
                                rec = fansirsqi.xposed.sesame.util.JsonUtil.parseObject(lastLine.trim(), CaptureRecord::class.java)
                                if (rec != null) break
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            _record.value = rec
            if (rec == null) { _loadError.value = "记录未找到 (id=$id)"; return@launch }

            val reqCt = rec.requestHeaders["Content-Type"] ?: rec.requestHeaders["content-type"]
            processAndSplit(rec.requestBody, rec.requestBodyBase64, reqCt).let { (raw, lines) ->
                _requestBodyRaw.value = raw
                _requestLines.value = lines
            }

            val ct = rec.contentType ?: ""
            if (ct.startsWith("image/", ignoreCase = true)) {
                decodeImage(rec.responseBodyBase64 ?: rec.responseBody)
            } else {
                processAndSplit(rec.responseBody, rec.responseBodyBase64, ct).let { (_, lines) ->
                    _responseLines.value = lines
                }
            }
        }
    }

    /** 解码 body → (rawText, preSplitLines) — 在 IO 线程完成 */
    private fun processAndSplit(
        textBody: String?,
        base64Body: String?,
        contentType: String?
    ): Pair<String, List<String>> {
        val rawBytes: ByteArray? = when {
            base64Body != null -> try { Base64.decode(base64Body, Base64.NO_WRAP) } catch (_: Exception) { null }
            else -> null
        }
        if (rawBytes == null || rawBytes.isEmpty()) {
            val empty = if (textBody.isNullOrEmpty()) "(无内容)" else textBody
            return empty to empty.split('\n')
        }
        val decompressed = NetworkUtils.decompressGzip(rawBytes) ?: rawBytes

        val isText = contentType != null && (
            contentType.startsWith("text/", ignoreCase = true) ||
            contentType.contains("json", ignoreCase = true) ||
            contentType.contains("xml", ignoreCase = true) ||
            contentType.contains("html", ignoreCase = true) ||
            contentType.contains("javascript", ignoreCase = true) ||
            contentType.contains("form-urlencoded", ignoreCase = true)
        )

        if (isText) {
            val text = try { String(decompressed, Charsets.UTF_8) } catch (_: Exception) { null }
            if (text != null && isPrintable(text)) {
                val formatted = formatText(text, contentType)
                return formatted to formatted.split('\n')
            }
        }

        val hex = buildHexDump(decompressed, contentType)
        return "[Binary: ${decompressed.size} bytes]" to hex.split('\n')
    }

    private fun buildHexDump(data: ByteArray, contentType: String?): String {
        val ctLabel = contentType?.let { " ($it)" } ?: ""
        val sb = StringBuilder()
        sb.appendLine("══════════════════════════════════════════════")
        sb.appendLine("  二进制数据${ctLabel}，${data.size} 字节")
        sb.appendLine("══════════════════════════════════════════════")
        val prev = minOf(data.size, 512)
        var i = 0
        while (i < prev) {
            val hex = StringBuilder(); val asc = StringBuilder(); var j = 0
            while (j < 16 && (i + j) < prev) {
                val b = data[i + j].toInt() and 0xFF
                hex.append(String.format("%02X ", b))
                asc.append(if (b in 0x20..0x7E) b.toChar() else '.')
                if (j == 7) hex.append(' '); j++
            }
            val needLen = if (j > 7) 49 else 48
            val pad = " ".repeat((needLen - hex.length).coerceAtLeast(0))
            sb.appendLine("  ${String.format("%08X", i)}  $hex$pad |$asc|"); i += 16
        }
        if (data.size > prev) sb.appendLine("  ... (前 $prev 字节，共 ${data.size} 字节)")
        if (contentType?.contains("protobuf", ignoreCase = true) == true)
            sb.appendLine("  💡 Protobuf 数据可使用 protoc --decode_raw 解析")
        return sb.toString()
    }

    private fun formatText(text: String, contentType: String?): String {
        if (text.isBlank()) return text
        return try {
            val t = text.trim()
            if (t.startsWith("{") || t.startsWith("[")) {
                val obj = JsonUtil.parseObject(t, Any::class.java)
                JsonUtil.formatJson(obj)
            } else if (contentType?.contains("form-urlencoded", true) == true) {
                t.split("&").joinToString("\n") { p ->
                    val idx = p.indexOf("="); if (idx == -1) p else "${p.substring(0, idx)} = ${p.substring(idx + 1)}"
                }
            } else text
        } catch (_: Exception) { "⚠ 格式化失败:\n$text" }
    }

    private fun isPrintable(text: String): Boolean {
        if (text.isEmpty()) return true
        var non = 0; val max = minOf(text.length, 4096)
        for (i in 0 until max) {
            val c = text[i]; if (c < 0x20.toChar() && c != '\n' && c != '\r' && c != '\t') non++
        }
        return non.toFloat() / max < 0.05f
    }

    private fun decodeImage(bodyText: String?) {
        if (bodyText == null) return
        try {
            val b = Base64.decode(bodyText, Base64.NO_WRAP)
            _responseImage.value = BitmapFactory.decodeByteArray(b, 0, b.size)
        } catch (_: Exception) { _loadError.value = "图片解码失败" }
    }
}
