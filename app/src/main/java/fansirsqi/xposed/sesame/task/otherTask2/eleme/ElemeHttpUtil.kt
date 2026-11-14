package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 饿了么专有 HTTP 请求工具
 * 针对阿里/饿了么网关优化：短连接(Connection: close)防 Connection Reset、智能自动重试与完善日志
 */
object ElemeHttpUtil {
    private const val CONNECT_TIMEOUT = 8000
    private const val READ_TIMEOUT = 10000
    private const val MAX_RETRIES = 1

    fun get(urlStr: String, cookie: String, extraHeaders: Map<String, String>? = null): String? {
        return executeWithRetry(urlStr, "GET", null, cookie, extraHeaders)
    }

    fun post(urlStr: String, body: String, cookie: String, extraHeaders: Map<String, String>? = null): String? {
        return executeWithRetry(urlStr, "POST", body, cookie, extraHeaders)
    }

    private fun executeWithRetry(
        urlStr: String,
        method: String,
        body: String?,
        cookie: String,
        extraHeaders: Map<String, String>?
    ): String? {
        var attempts = 0
        while (attempts <= MAX_RETRIES) {
            val (resp, needRetry) = executeOnce(urlStr, method, body, cookie, extraHeaders)
            if (resp != null) {
                return resp
            }
            if (!needRetry || attempts >= MAX_RETRIES) {
                break
            }
            attempts++
            try {
                Thread.sleep(300)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return null
    }

    private fun executeOnce(
        urlStr: String,
        method: String,
        body: String?,
        cookie: String,
        extraHeaders: Map<String, String>?
    ): Pair<String?, Boolean> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = true
                useCaches = false
                // 关闭长连接，防止复用被阿里 CDN 主动断开的废弃 TCP 连接导致 Connection reset
                setRequestProperty("Connection", "close")
                val currentUa = extraHeaders?.get("User-Agent") ?: ElemeMtopUtil.getCurrentHttpUa()
                setRequestProperty("User-Agent", currentUa)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                if (cookie.isNotEmpty()) {
                    setRequestProperty("Cookie", cookie)
                }
                setRequestProperty("Referer", "https://${ElemeAuth.APP_ID}.hybrid.alipay-eco.com/")
                extraHeaders?.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    if (!extraHeaders.orEmpty().keys.any { it.equals("Content-Type", ignoreCase = true) }) {
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    }
                    OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { it.write(body) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() }
            if (code !in 200..299) {
                Log.other("饿了么", "$method HTTP 异常状态码: $code, 响应内容: $resp")
                Pair(null, code >= 500)
            } else {
                Pair(resp, false)
            }
        } catch (t: Throwable) {
            val errMsg = t.message ?: t.javaClass.simpleName
            Log.other("饿了么", "$method 网络请求异常: $errMsg (URL: ${urlStr.substringBefore('?')})")
            Pair(null, true)
        } finally {
            conn?.disconnect()
        }
    }
}
