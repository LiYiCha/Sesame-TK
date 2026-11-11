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
    const val TTID = "2021005151656223@alipay_android_12.1.53"

    @Volatile
    var lastError: String? = null
    @Volatile
    var lastResponse: String? = null

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
        lastError = null
        lastResponse = null

        val alipayVer = fansirsqi.xposed.sesame.hook.ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.30.8000"
        val releaseVer = android.os.Build.VERSION.RELEASE.ifEmpty { "16" }
        val model = android.os.Build.MODEL.ifEmpty { "V2238A" }
        val brand = android.os.Build.BRAND.ifEmpty { "vivo" }
        val brandModel = if (model.startsWith(brand, ignoreCase = true)) "$brand/$model" else "$brand/$brand,$model"

        // 仅在任务增长体系 (growth.task) 中智能补全阿里 Aladdin 投放引擎特征参数，避免污染其他标准会话接口
        val enrichedDataJson = runCatching {
            val obj = JSONObject(dataJson)
            if (!obj.has("instance")) obj.put("instance", "INNER")
            if (api.contains("growth.task")) {
                if (!obj.has("platform")) obj.put("platform", "android")
                if (!obj.has("product")) obj.put("product", "ap")
                if (!obj.has("prodVersion")) obj.put("prodVersion", alipayVer)
                if (!obj.has("ttid")) obj.put("ttid", TTID)
                if (!obj.has("locationInfos") && obj.has("longitude") && obj.has("latitude")) {
                    val lng = obj.optString("longitude")
                    val lat = obj.optString("latitude")
                    if (lng.isNotEmpty() && lat.isNotEmpty()) {
                        obj.put("locationInfos", "[\"{\\\"lng\\\":\\\"$lng\\\",\\\"lat\\\":\\\"$lat\\\"}\"]")
                    }
                }
            }
            obj.toString()
        }.getOrDefault(dataJson)

        val timestamp = System.currentTimeMillis()
        val sign = calculateSign(timestamp, enrichedDataJson)
        val encodedData = URLEncoder.encode(enrichedDataJson, "UTF-8")
        val encodedTtid = URLEncoder.encode(TTID, "UTF-8")

        // 严格遵循真实抓包 URL 规范，杜绝未编码字符与非标 query 拼接
        val isPost = method.equals("POST", ignoreCase = true)
        val requestUrl = if (isPost) {
            "https://waimai-guide.ele.me/h5/$api/$version/2.0/?jsv=$JSV&appKey=$APP_KEY&t=$timestamp&sign=$sign&api=$api&dataType=json&method=POST&timeout=10000&v=$version&type=originaljson&ttid=$encodedTtid&accountSite=eleme&enableCookie=true&prefetchTimeout=10000&mpHost=waimai-guide.ele.me&_bx-m=0.0.15"
        } else {
            "https://waimai-guide.ele.me/h5/$api/$version/2.0/?jsv=$JSV&appKey=$APP_KEY&t=$timestamp&sign=$sign&api=$api&dataType=json&method=GET&timeout=10000&v=$version&type=originaljson&ttid=$encodedTtid&accountSite=eleme&enableCookie=true&prefetchTimeout=10000&mpHost=waimai-guide.ele.me&data=$encodedData&_bx-m=0.0.15"
        }

        val headers = HashMap<String, String>()
        headers["x-tap"] = "ap"
        headers["Accept"] = "application/json"
        headers["Referer"] = "https://${ElemeAuth.APP_ID}.hybrid.alipay-eco.com/"
        headers["x-secext-city"] = "98"
        headers["x-ele-ua"] = "RenderWay/miniProgram MiniAppId/${ElemeAuth.APP_ID} MiniAppVersion/12.1.53 DeviceId/asdLYB2rw6kDAN+qYdXj9afD AppName/ap $brandModel Android/$releaseVer AlipayClient/$alipayVer AliApp(AP/$alipayVer) userMode/student channel/mini_app subChannel/miniapp.alipay subSubChannel/miniapp.alipay.et"
        if (extraHeaders != null) headers.putAll(extraHeaders)

        val resp = if (isPost) {
            headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8"
            val postBody = "data=$encodedData"
            ElemeHttpUtil.post(requestUrl, postBody, cookie, headers)
        } else {
            ElemeHttpUtil.get(requestUrl, cookie, headers)
        }
        if (resp == null) {
            val err = "[$api] 网络请求失败，未收到响应内容"
            lastError = err
            Log.other("饿了么", err)
            return null
        }
        lastResponse = resp

        return runCatching {
            val root = JSONObject(resp)
            val ret = root.optJSONArray("ret")?.optString(0).orEmpty()
            if (ret.startsWith("SUCCESS")) {
                root.optJSONObject("data") ?: root
            } else {
                val err = "[$api] 响应失败: $ret, 完整报文: $resp"
                lastError = err
                Log.other("饿了么", err)
                if (ret.contains("SESSION_EXPIRED") || ret.contains("NOT_LOGIN") || ret.contains("TOKEN_EMPTY") || ret.contains("SID_INVALID")) {
                    Log.other("饿了么", "检测到饿了么登录态失效[$ret]，自动清除 Cookie 缓存以便下次自愈刷新")
                    ElemeAuth.invalidate()
                }
                null
            }
        }.onFailure {
            val err = "[$api] 解析 JSON 异常: ${it.message}, 原始报文: $resp"
            lastError = err
            Log.other("饿了么", err)
        }.getOrNull()
    }
}
