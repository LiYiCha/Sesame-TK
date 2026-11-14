package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

/**
 * 饿了么 H5 MTOP 签名与请求协议工具
 * 基于真实抓包逆向算法：sign = md5("&" + t + "&12574478&" + data)
 * 支持多主流机型设备指纹库（vivo、Xiaomi、OPPO、HONOR）切换与随机轮换防风控
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

    data class DeviceProfile(
        val brand: String,
        val model: String,
        val release: String,
        val buildId: String = "BP1A.250305.019",
        val alipayVer: String = "12.12.30.8000",
        val deviceId: String = "atIrzF9s9okDAMFZV1uAZf1x"
    ) {
        fun brandModel(): String = if (model.startsWith(brand, ignoreCase = true)) "$brand/$model" else "$brand/$brand,$model"

        fun eleUa(appId: String): String =
            "RenderWay/miniProgram MiniAppId/$appId MiniAppVersion/12.1.53 DeviceId/$deviceId AppName/ap ${brandModel()} Android/$release AlipayClient/$alipayVer AliApp(AP/$alipayVer) userMode/student channel/mini_app subChannel/miniapp.alipay subSubChannel/miniapp.alipay.et"

        fun httpUa(): String =
            "Mozilla/5.0 (Linux; U; Android $release; zh-CN; ${brandModel()} Build/$buildId) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/118.0.0.0 UCBrowser/16.3.8.1402 Mobile Safari/537.36 AlipayDefined(nt:wifi,ws:384|0|853) AliApp(AP/$alipayVer) AlipayClient/$alipayVer Language/zh-Hans useStatusBar/true isConcaveScreen/false Region/CN Channel/${brand.lowercase()} GCanvas/1.9.38 Nebula SDK/1.8.1000 Nebula Ariver/1.0.0"
    }

    data class MtopResult(
        val isSuccess: Boolean,
        val isNotLogin: Boolean,
        val errorCode: String = "",
        val errorMsg: String = "",
        val data: JSONObject? = null,
        val rawJson: JSONObject? = null
    )

    /**
     * 获取当前生效的设备指纹配置（受 OtherTask2.elemeUaMode 控制）
     * 0: 系统默认
     * 1: vivo X100 Pro
     * 2: Xiaomi 14
     * 3: OPPO Find X7
     * 4: HONOR Magic6
     * 5: 随机轮换 (每次执行随机挑选机型)
     */
    fun getActiveProfile(): DeviceProfile {
        val alipayVer = ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.30.8000"
        val mode = runCatching {
            fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeUaMode().value ?: 0
        }.getOrDefault(0)

        val presetProfiles = listOf(
            DeviceProfile("vivo", "V2309A", "14", "UP1A.231005.007", alipayVer, "asdLYB2rw6kDAN+qYdXj9afD"),         // vivo X100 Pro
            DeviceProfile("Xiaomi", "23116PN5BC", "14", "UKQ1.230804.001", alipayVer, "atIrzF9s9okDAMFZV1uAZf1x"),     // Xiaomi 14
            DeviceProfile("OPPO", "PHY110", "14", "UKQ1.230924.001", alipayVer, "bnJszG9s1okDAMFZY2uAZg2y"),          // OPPO Find X7
            DeviceProfile("HONOR", "BVL-AN00", "14", "HONORBVL-AN00", alipayVer, "coKtzH9s2okDAMFZz3uAZh3z")          // HONOR Magic6
        )

        return when (mode) {
            1 -> presetProfiles[0]
            2 -> presetProfiles[1]
            3 -> presetProfiles[2]
            4 -> presetProfiles[3]
            5 -> presetProfiles[RandomUtil.nextInt(0, presetProfiles.size)]
            else -> {
                val releaseVer = android.os.Build.VERSION.RELEASE.ifEmpty { "16" }
                val model = android.os.Build.MODEL.ifEmpty { "V2238A" }
                val brand = android.os.Build.BRAND.ifEmpty { "vivo" }
                DeviceProfile(brand, model, releaseVer, "BP1A.250305.019", alipayVer, "asdLYB2rw6kDAN+qYdXj9afD")
            }
        }
    }

    fun getCurrentHttpUa(): String = getActiveProfile().httpUa()

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
     * 执行饿了么 MTOP 请求并返回结构化结果（严格对齐真机抓包 URL、Header 与协议规范）
     */
    fun execute(
        api: String,
        dataJson: String,
        cookie: String,
        method: String = "GET",
        version: String = "1.0",
        extraHeaders: Map<String, String>? = null
    ): MtopResult {
        lastError = null
        lastResponse = null

        val profile = getActiveProfile()
        val brandModel = profile.brandModel()
        val releaseVer = profile.release
        val alipayVer = profile.alipayVer

        // 严格遵循阿里 Aladdin 引擎规范，保证 instance 参数完整
        val enrichedDataJson = runCatching {
            val obj = JSONObject(dataJson)
            if (!obj.has("instance")) obj.put("instance", "INNER")
            if (obj.has("longitude") && obj.has("latitude") && !obj.has("locationInfos")) {
                val lng = obj.optString("longitude")
                val lat = obj.optString("latitude")
                if (lng.isNotEmpty() && lat.isNotEmpty()) {
                    obj.put("locationInfos", "[\"{\\\"lng\\\":\\\"$lng\\\",\\\"lat\\\":\\\"$lat\\\"}\"]")
                }
            }
            obj.toString()
        }.getOrDefault(dataJson)

        val timestamp = System.currentTimeMillis()
        val sign = calculateSign(timestamp, enrichedDataJson)
        val encodedData = URLEncoder.encode(enrichedDataJson, "UTF-8")
        val encodedTtid = URLEncoder.encode(TTID, "UTF-8")

        // 严格遵循真机抓包 tb.log 第 55/1483 行规范，杜绝拼入不存在的 platform/product/prodVersion 等非标 query
        val isPost = method.equals("POST", ignoreCase = true)
        val commonQuery = "jsv=$JSV&appKey=$APP_KEY&t=$timestamp&sign=$sign&api=$api&dataType=json&method=${if (isPost) "POST" else "GET"}&timeout=10000&v=$version&type=originaljson&ttid=$encodedTtid&accountSite=eleme&enableCookie=true&prefetchTimeout=10000&mpHost=waimai-guide.ele.me&_bx-m=0.0.15"
        val requestUrl = if (isPost) {
            "https://waimai-guide.ele.me/h5/$api/$version/2.0/?$commonQuery"
        } else {
            "https://waimai-guide.ele.me/h5/$api/$version/2.0/?$commonQuery&data=$encodedData"
        }

        val headers = HashMap<String, String>()
        headers["x-tap"] = "ap"
        headers["Accept"] = "application/json"
        headers["Referer"] = "https://${ElemeAuth.APP_ID}.hybrid.alipay-eco.com/"
        headers["x-secext-city"] = "98"
        headers["x-decode-ua"] = "false"
        val reqId = "${UUID.randomUUID().toString().replace("-", "").uppercase()}|$timestamp"
        headers["x-eleme-requestid"] = reqId
        headers["x-ele-ua"] = profile.eleUa(ElemeAuth.APP_ID)
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
            return MtopResult(isSuccess = false, isNotLogin = false, errorMsg = err)
        }
        lastResponse = resp

        return runCatching {
            val root = JSONObject(resp)
            val ret = root.optJSONArray("ret")?.optString(0).orEmpty()
            val dataObj = root.optJSONObject("data")
            var errorCode = dataObj?.optString("errorCode")?.takeIf { it.isNotEmpty() }
                ?: root.optString("errorCode", "")
            var errorMessage = dataObj?.optString("errorMessage")?.takeIf { it.isNotEmpty() }
                ?: dataObj?.optString("errorMsg")?.takeIf { it.isNotEmpty() }
                ?: root.optString("errorMsg", "")

            // 深度解析阿里百川风控与阶段发奖拦截: data.REWARD_UPP 节点
            val rewardUpp = dataObj?.optJSONObject("REWARD_UPP")
            if (rewardUpp != null) {
                val keys = rewardUpp.keys()
                val uppErrors = mutableListOf<String>()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = rewardUpp.optString(k)
                    uppErrors.add(if (v.isNotEmpty()) "$k: $v" else k)
                }
                if (uppErrors.isNotEmpty()) {
                    val uppDetail = uppErrors.joinToString("; ")
                    errorMessage = if (errorMessage.isNotEmpty()) "$errorMessage ($uppDetail)" else uppDetail
                    if (errorCode.isEmpty()) {
                        errorCode = "RISK_USER"
                    }
                }
            }

            val isNotLogin = ret.contains("SESSION_EXPIRED") || ret.contains("NOT_LOGIN") ||
                    ret.contains("TOKEN_EMPTY") || ret.contains("SID_INVALID") ||
                    errorCode == "000502" || errorMessage.contains("需要登录") || errorMessage.contains("未登录") ||
                    resp.contains("\"errorCode\":\"000502\"")

            if (isNotLogin) {
                Log.other("饿了么", "[$api] 会话已失效(需要重新登录)，自动清除本地凭据以便刷新")
                ElemeAuth.invalidate()
                val err = "会话失效: 需要登录"
                lastError = err
                return MtopResult(isSuccess = false, isNotLogin = true, errorCode = errorCode.ifEmpty { "000502" }, errorMsg = err, data = dataObj, rawJson = root)
            }

            if (ret.startsWith("SUCCESS") && (errorCode.isEmpty() || errorCode == "SUCCESS" || errorCode == "0")) {
                MtopResult(isSuccess = true, isNotLogin = false, data = dataObj ?: root, rawJson = root)
            } else {
                val readableErr = when {
                    errorMessage.isNotEmpty() && ret.isNotEmpty() -> "$ret ($errorMessage)"
                    errorMessage.isNotEmpty() -> errorMessage
                    else -> ret
                }
                val err = "[$api] 响应失败: $readableErr"
                lastError = err
                // 使用 Log.other 打印，确保主界面日志面板完整展示错误与风控拦截信息
                Log.other("饿了么", err)
                MtopResult(isSuccess = false, isNotLogin = false, errorCode = errorCode, errorMsg = readableErr, data = dataObj, rawJson = root)
            }
        }.onFailure {
            val err = "[$api] 解析 JSON 异常: ${it.message}"
            lastError = err
            Log.other("饿了么", err)
        }.getOrDefault(MtopResult(isSuccess = false, isNotLogin = false, errorMsg = lastError.orEmpty()))
    }

    /**
     * 发送饿了么 MTOP 请求（兼容旧调用，直接返回 data 节点）
     */
    fun send(
        api: String,
        dataJson: String,
        cookie: String,
        method: String = "GET",
        version: String = "1.0",
        extraHeaders: Map<String, String>? = null
    ): JSONObject? {
        val result = execute(api, dataJson, cookie, method, version, extraHeaders)
        return if (result.isSuccess) result.data else null
    }
}
