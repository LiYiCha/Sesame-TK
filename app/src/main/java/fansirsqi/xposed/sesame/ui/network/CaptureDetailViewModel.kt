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

    /** 格式化后的请求体 (用于展示) */
    private val _requestBodyDisplay = MutableStateFlow<String?>(null)
    val requestBodyDisplay: StateFlow<String?> = _requestBodyDisplay

    /** 格式化后的响应体 (用于展示) */
    private val _responseBodyDisplay = MutableStateFlow<String?>(null)
    val responseBodyDisplay: StateFlow<String?> = _responseBodyDisplay

    /** 原始请求体文本 (用于重发) */
    private val _requestBodyRaw = MutableStateFlow<String?>(null)
    val requestBodyRaw: StateFlow<String?> = _requestBodyRaw

    /** 响应图片 */
    private val _responseImage = MutableStateFlow<Bitmap?>(null)
    val responseImage: StateFlow<Bitmap?> = _responseImage

    /** 加载错误提示 */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    fun loadRecord(id: String, date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rec = CaptureStorage.loadById(id, date)
            _record.value = rec

            if (rec == null) {
                _loadError.value = "记录未找到"
                return@launch
            }

            // 处理请求体
            processBody(
                body = rec.requestBody,
                bodyBase64 = rec.requestBodyBase64,
                displayFlow = _requestBodyDisplay,
                rawFlow = _requestBodyRaw,
                isImage = false
            )

            // 处理响应体
            val isImage = rec.contentType?.startsWith("image/", ignoreCase = true) == true
            if (isImage) {
                decodeImage(rec.responseBodyBase64 ?: rec.responseBody)
            } else {
                processBody(
                    body = rec.responseBody,
                    bodyBase64 = rec.responseBodyBase64,
                    displayFlow = _responseBodyDisplay,
                    rawFlow = null,
                    isImage = false
                )
            }
        }
    }

    private fun processBody(
        body: String?,
        bodyBase64: String?,
        displayFlow: MutableStateFlow<String?>,
        rawFlow: MutableStateFlow<String?>?,
        isImage: Boolean
    ) {
        val text = body ?: bodyBase64?.let {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            // 尝试 GZIP 解压 + UTF-8 解码
            val decompressed = NetworkUtils.decompressGzip(bytes) ?: bytes
            try { String(decompressed, Charsets.UTF_8) } catch (_: Exception) { "[Binary: ${bytes.size} bytes]" }
        } ?: "(无内容)"

        // rawFlow 存原始文本 (不格式化)
        rawFlow?.value = text

        // displayFlow 存格式化后的
        displayFlow.value = formatForDisplay(text)
    }

    private fun decodeImage(bodyText: String?) {
        if (bodyText == null) return
        try {
            val bytes = Base64.decode(bodyText, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            _responseImage.value = bitmap
        } catch (_: Exception) {
            _loadError.value = "图片解码失败"
        }
    }

    /**
     * 格式化用于展示：
     * - JSON → pretty-print
     * - 失败 → 返回原始
     */
    private fun formatForDisplay(text: String): String {
        if (text.isBlank()) return text
        // 尝试 JSON 格式化
        return try {
            val trimmed = text.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                val obj = JsonUtil.parseObject(trimmed, Any::class.java)
                JsonUtil.formatJson(obj)
            } else {
                text
            }
        } catch (_: Exception) {
            // 格式化失败，返回原始内容 + 提示
            if (text.length > 100) "⚠ 格式化失败，显示原始内容:\n\n$text" else text
        }
    }
}
