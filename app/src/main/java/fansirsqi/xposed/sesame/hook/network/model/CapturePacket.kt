package fansirsqi.xposed.sesame.hook.network.model

import java.io.Serializable
import java.util.UUID

/**
 * 网络捕获包模型
 */
data class CapturePacket(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "",
    val method: String = "",
    val host: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0,
    val duration: Long = 0,
    var requestHeaders: Map<String, String>? = null,
    var responseHeaders: Map<String, String>? = null,
    val responseCode: Int = 0,
    var requestBodyFile: String? = null,
    var responseBodyFile: String? = null,
    var isImage: Boolean = false,
    val contentType: String? = null,
    val protocol: String = "HTTP", // HTTP or RPC
    val errorMessage: String? = null
) : Serializable
