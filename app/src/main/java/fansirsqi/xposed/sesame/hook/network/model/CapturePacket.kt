package fansirsqi.xposed.sesame.hook.network.model

import java.io.Serializable
import java.util.UUID

/**
 * 网络捕获包模型
 */
data class CapturePacket(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "",
    var method: String = "",
    var host: String = "",
    var startTime: Long = System.currentTimeMillis(),
    var endTime: Long = 0,
    var duration: Long = 0,
    var requestHeaders: Map<String, String>? = null,
    var responseHeaders: Map<String, String>? = null,
    var responseCode: Int = 0,
    var requestBodyFile: String? = null,
    var responseBodyFile: String? = null,
    var isImage: Boolean = false,
    var contentType: String? = null,
    var protocol: String = "HTTP", // HTTP or RPC
    var errorMessage: String? = null
) : Serializable
