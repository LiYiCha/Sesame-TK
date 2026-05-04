package fansirsqi.xposed.sesame.ui.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

import fansirsqi.xposed.sesame.util.NetworkUtils

class NetworkDetailViewModel : ViewModel() {

    private val _requestBody = MutableStateFlow<String?>(null)
    val requestBody: StateFlow<String?> = _requestBody

    private val _responseBody = MutableStateFlow<String?>(null)
    val responseBody: StateFlow<String?> = _responseBody

    private val _responseImage = MutableStateFlow<Bitmap?>(null)
    val responseImage: StateFlow<Bitmap?> = _responseImage

    fun loadBodies(packet: CapturePacket) {
        viewModelScope.launch(Dispatchers.IO) {
            // 重置状态
            _requestBody.value = null
            _responseBody.value = null
            _responseImage.value = null

            // 加载请求体
            packet.requestBodyFile?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    try {
                        val bytes = file.readBytes()
                        val raw = NetworkUtils.bytesToString(bytes)
                        _requestBody.value = formatIfJson(raw)
                    } catch (e: Exception) {
                        _requestBody.value = "加载失败: ${e.message}"
                    }
                } else {
                    _requestBody.value = "(文件已被清理或不存在)"
                }
            }

            // 加载响应体
            packet.responseBodyFile?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    if (packet.isImage) {
                        try {
                            _responseImage.value = BitmapFactory.decodeFile(path)
                        } catch (e: Exception) {
                            _responseBody.value = "图片解码失败"
                        }
                    } else {
                        try {
                            val bytes = file.readBytes()
                            val raw = NetworkUtils.bytesToString(bytes)
                            _responseBody.value = formatIfJson(raw)
                        } catch (e: Exception) {
                            _responseBody.value = "加载失败: ${e.message}"
                        }
                    }
                } else {
                    _responseBody.value = "(文件已被清理或不存在)"
                }
            }
        }
    }

    private fun formatIfJson(raw: String): String {
        if (raw.isBlank()) return raw
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                val obj = JsonUtil.parseObject(raw, Any::class.java)
                JsonUtil.formatJson(obj)
            } else {
                raw
            }
        } catch (e: Exception) {
            raw
        }
    }
}
