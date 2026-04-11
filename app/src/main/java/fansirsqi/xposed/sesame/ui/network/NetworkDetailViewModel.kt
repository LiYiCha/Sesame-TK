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

class NetworkDetailViewModel : ViewModel() {

    private val _requestBody = MutableStateFlow<String?>(null)
    val requestBody: StateFlow<String?> = _requestBody

    private val _responseBody = MutableStateFlow<String?>(null)
    val responseBody: StateFlow<String?> = _responseBody

    private val _responseImage = MutableStateFlow<Bitmap?>(null)
    val responseImage: StateFlow<Bitmap?> = _responseImage

    fun loadBodies(packet: CapturePacket) {
        viewModelScope.launch(Dispatchers.IO) {
            // 加载请求体
            packet.requestBodyFile?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val raw = file.readText()
                    _requestBody.value = formatIfJson(raw)
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
                        val raw = file.readText()
                        _responseBody.value = formatIfJson(raw)
                    }
                }
            }
        }
    }

    private fun formatIfJson(raw: String): String {
        return try {
            val obj = JsonUtil.parseObject(raw, Any::class.java)
            JsonUtil.formatJson(obj)
        } catch (e: Exception) {
            raw
        }
    }
}
