package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 饿了么专有 HTTP 请求工具
 */
object ElemeHttpUtil {
    private const val TAG = "ElemeHttpUtil"

    fun get(urlStr: String, cookie: String, extraHeaders: Map<String, String>? = null): String? {
        return execute(urlStr, "GET", null, cookie, extraHeaders)
    }

    fun post(urlStr: String, body: String, cookie: String, extraHeaders: Map<String, String>? = null): String? {
        return execute(urlStr, "POST", body, cookie, extraHeaders)
    }

    private fun execute(
        urlStr: String,
        method: String,
        body: String?,
        cookie: String,
        extraHeaders: Map<String, String>?
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", ElemeAuth.DEFAULT_UA)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Cookie", cookie)
                setRequestProperty("Referer", "https://${ElemeAuth.APP_ID}.hybrid.alipay-eco.com/")
                extraHeaders?.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    if (!extraHeaders.orEmpty().keys.any { it.equals("Content-Type", ignoreCase = true) }) {
                        setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                    }
                    OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { it.write(body) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() }
            if (code !in 200..299) {
                Log.error(TAG, "$method $urlStr HTTP error $code: $resp")
            }
            resp
        } catch (t: Throwable) {
            Log.error(TAG, "$method $urlStr failed: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
