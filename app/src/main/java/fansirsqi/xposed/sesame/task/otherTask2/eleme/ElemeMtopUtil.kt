package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.util.Log
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 饿了么 H5 MTOP 签名与请求协议工具
 * 基于真实抓包逆向算法：sign = md5("&" + t + "&12574478&" + data)
 */
object ElemeMtopUtil {
    private const val TAG = "ElemeMtopUtil"
    const val APP_KEY = "12574478"
    const val JSV = "2.4.12"
    const val TTID = "2021005151656223@alipay_iphone_10.13.0"

    /**
     * 计算阿里 H5 MTOP 签名：md5("&" + t + "&" + appKey + "&" + data)
     */
    fun calculateSign(timestamp: Long, dataJson: String, token: String = ""): String {
        val raw = "$token&$timestamp&$APP_KEY&$dataJson"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 发送饿了么 MTOP 请求（严格遵循饿了么网关协议与鉴权规范）
     */
    fun send(
        api: String,
        dataJson: String,
        cookie: String,
        method: String = "GET",
        version: String = "1.0",
        extraHeaders: Map<String, String>? = null
    ): JSONObject? {
        val timestamp = System.currentTimeMillis()
        val sign = calculateSign(timestamp, dataJson)
        val encodedData = URLEncoder.encode(dataJson, "UTF-8")

        val baseUrl = "https://waimai-guide.ele.me/h5/$api/$version/2.0/?jsv=$JSV&appKey=$APP_KEY&t=$timestamp&sign=$sign&api=$api&dataType=json&method=$method&timeout=10000&v=$version&type=originaljson&ttid=$TTID&accountSite=eleme&enableCookie=true&prefetchTimeout=10000&mpHost=waimai-guide.ele.me&_bx-m=0.0.15"

        val headers = HashMap<String, String>()
        headers["x-tap"] = "ap"
        headers["Referer"] = "https://${ElemeAuth.APP_ID}.hybrid.alipay-eco.com/"
        headers["x-ele-ua"] = "RenderWay/miniProgram MiniAppId/${ElemeAuth.APP_ID} MiniAppVersion/12.1.53 Channel/mini_app"
        if (extraHeaders != null) headers.putAll(extraHeaders)

        val resp = if (method.equals("POST", ignoreCase = true)) {
            headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8"
            val postBody = "data=$encodedData"
            ElemeHttpUtil.post(baseUrl, postBody, cookie, headers)
        } else {
            val getUrl = "$baseUrl&data=$encodedData"
            ElemeHttpUtil.get(getUrl, cookie, headers)
        } ?: return null

        return runCatching {
            val root = JSONObject(resp)
            val ret = root.optJSONArray("ret")?.optString(0).orEmpty()
            if (ret.startsWith("SUCCESS")) {
                root.optJSONObject("data") ?: root
            } else {
                Log.error(TAG, "[$api] 响应失败: $ret, body: $resp")
                if (ret.contains("SESSION_EXPIRED") || ret.contains("NOT_LOGIN") || ret.contains("TOKEN_EMPTY") || ret.contains("SID_INVALID")) {
                    Log.runtime(TAG, "检测到饿了么登录态失效[$ret]，自动清除 Cookie 缓存以便下次自愈刷新")
                    ElemeAuth.invalidate()
                }
                null
            }
        }.onFailure {
            Log.error(TAG, "[$api] 解析 JSON 异常: ${it.message}")
        }.getOrNull()
    }
}
