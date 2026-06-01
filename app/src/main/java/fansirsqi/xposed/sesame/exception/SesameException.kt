package fansirsqi.xposed.sesame.exception

/**
 * Sesame-TK 基础异常类
 */
open class SesameException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * RPC 调用相关异常
 */
class RpcException(
    val methodName: String,
    val errorCode: String? = null,
    val errorMsg: String? = null,
    message: String = "RPC call failed: $methodName",
    cause: Throwable? = null
) : SesameException(message, cause)

/**
 * 验证码/滑块异常
 */
class CaptchaException(message: String) : SesameException(message)

/**
 * 未授权/会话过期异常
 */
class UnauthorizedException(message: String = "Session expired or unauthorized") : SesameException(message)
