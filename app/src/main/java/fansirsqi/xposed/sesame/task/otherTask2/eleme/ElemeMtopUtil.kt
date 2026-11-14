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
 * 支持多主流机型设备指纹库（vivo、Xiaomi、OPPO、HONOR、HUAWEI、Samsung）真实动态随机生成与轮换
 */
object ElemeMtopUtil {
    private const val TAG = "ElemeMtopUtil"
    const val APP_KEY = "12574478"
    const val JSV = "2.4.12"

    @Volatile
    var lastError: String? = null
    @Volatile
    var lastResponse: String? = null

    // 单次会话级指纹缓存：确保单次任务执行生命周期内所有接口使用完全相同的真实设备指纹
    @Volatile
    private var sessionProfile: DeviceProfile? = null

    data class DeviceProfile(
        val brand: String,
        val model: String,
        val brandModel: String,
        val release: String,
        val buildId: String,
        val alipayVer: String,
        val chromeVer: String,
        val ucVer: String,
        val screenWs: String,
        val deviceId: String
    ) {
        fun eleUa(appId: String = ElemeAuth.APP_ID): String =
            "RenderWay/miniProgram MiniAppId/$appId MiniAppVersion/12.1.53 DeviceId/$deviceId AppName/ap $brandModel Android/$release AlipayClient/$alipayVer AliApp(AP/$alipayVer) userMode/student channel/mini_app subChannel/miniapp.alipay subSubChannel/miniapp.alipay.et"

        fun httpUa(): String =
            "Mozilla/5.0 (Linux; U; Android $release; zh-CN; $brandModel Build/$buildId) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/$chromeVer UCBrowser/$ucVer Mobile Safari/537.36 AlipayDefined(nt:wifi,ws:$screenWs) AliApp(AP/$alipayVer) AlipayClient/$alipayVer Language/zh-Hans useStatusBar/true isConcaveScreen/false Region/CN Channel/${brand.lowercase()} GCanvas/1.9.38 Nebula SDK/1.8.1000 Nebula Ariver/1.0.0"

        fun toJson(): String {
            val jo = JSONObject()
            jo.put("brand", brand)
            jo.put("model", model)
            jo.put("brandModel", brandModel)
            jo.put("release", release)
            jo.put("buildId", buildId)
            jo.put("alipayVer", alipayVer)
            jo.put("chromeVer", chromeVer)
            jo.put("ucVer", ucVer)
            jo.put("screenWs", screenWs)
            jo.put("deviceId", deviceId)
            return jo.toString()
        }

        fun summary(): String = "$brandModel (Android $release, Chrome/$chromeVer, UC/$ucVer)"

        companion object {
            fun fromJson(jsonStr: String): DeviceProfile? {
                return runCatching {
                    val jo = JSONObject(jsonStr)
                    val alipayVer = jo.optString("alipayVer").ifEmpty {
                        ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.30.8000"
                    }
                    DeviceProfile(
                        brand = jo.getString("brand"),
                        model = jo.getString("model"),
                        brandModel = jo.getString("brandModel"),
                        release = jo.getString("release"),
                        buildId = jo.getString("buildId"),
                        alipayVer = alipayVer,
                        chromeVer = jo.getString("chromeVer"),
                        ucVer = jo.getString("ucVer"),
                        screenWs = jo.getString("screenWs"),
                        deviceId = jo.getString("deviceId")
                    )
                }.getOrNull()
            }
        }
    }

    data class MtopResult(
        val isSuccess: Boolean,
        val isNotLogin: Boolean,
        val errorCode: String = "",
        val errorMsg: String = "",
        val data: JSONObject? = null,
        val rawJson: JSONObject? = null
    )

    private data class DeviceTemplate(
        val brand: String,
        val model: String,
        val brandModel: String,
        val defaultRelease: String,
        val buildId: String
    )

    // =========================================================================
    // 厂商真实入网与研发规范动态规则引擎（无限生成合法真实设备，绝不会用完）
    // =========================================================================

    private fun generateVivoTemplate(): DeviceTemplate {
        val isIqoo = RandomUtil.nextInt(0, 3) == 0
        val brand = if (isIqoo) "iQOO" else "vivo"
        val manufacturer = "vivo"
        val year = RandomUtil.nextInt(24, 27) // 24, 25, 26
        val batch = "%02d".format(RandomUtil.nextInt(1, 45)) // 01 ~ 44
        val suffix = if (RandomUtil.nextInt(0, 5) == 0) "DA" else "A"
        val model = "V$year$batch$suffix"
        val brandModel = if (isIqoo) "iQOO/$manufacturer,$model" else "vivo/$manufacturer,$model"
        val release = if (year >= 26 && RandomUtil.nextInt(0, 2) == 0) "16" else "15"
        val buildId = generateBuildId(release, "vivo")
        return DeviceTemplate(brand, model, brandModel, release, buildId)
    }

    private fun generateXiaomiTemplate(): DeviceTemplate {
        val isRedmi = RandomUtil.nextInt(0, 2) == 0
        val brand = if (isRedmi) "Redmi" else "Xiaomi"
        val manufacturer = "Xiaomi"
        val year = RandomUtil.nextInt(24, 27) // 24, 25, 26
        val month = "%02d".format(RandomUtil.nextInt(1, 13)) // 01 ~ 12
        val lineDigit = RandomUtil.nextInt(1, 10) // 1 ~ 9
        val lineCodes = listOf("PN", "RK", "PC", "PY", "RA", "DR")
        val lineCode = lineCodes[RandomUtil.nextInt(0, lineCodes.size)]
        val seq = "%02d".format(RandomUtil.nextInt(1, 99))
        val model = "$year$month$lineDigit$lineCode$seq" + "C"
        val brandModel = "$brand/$manufacturer,$model"
        val release = if (year >= 26 && RandomUtil.nextInt(0, 2) == 0) "16" else "15"
        val buildId = if (RandomUtil.nextInt(0, 2) == 0) {
            "VKQ1." + "%02d%02d%02d".format(year, RandomUtil.nextInt(1, 13), RandomUtil.nextInt(1, 29)) + ".001"
        } else {
            generateBuildId(release, "Xiaomi")
        }
        return DeviceTemplate(brand, model, brandModel, release, buildId)
    }

    private fun generateOppoTemplate(): DeviceTemplate {
        val type = RandomUtil.nextInt(0, 3)
        val (brand, manufacturer) = when (type) {
            0 -> Pair("OnePlus", "OnePlus")
            1 -> Pair("realme", "realme")
            else -> Pair("OPPO", "OPPO")
        }
        val letters = listOf("PK", "PJ", "PL", "PM", "PN")
        val letter3 = "ABCDEFGHTUVWXYZ"[RandomUtil.nextInt(0, 15)]
        val code = letters[RandomUtil.nextInt(0, letters.size)] + letter3
        val num = listOf("110", "120", "130", "010", "020")[RandomUtil.nextInt(0, 5)]
        val model = if (type == 1) "RMX" + RandomUtil.nextInt(3800, 4100) else "$code$num"
        val brandModel = "$brand/$manufacturer,$model"
        val release = "15"
        val buildId = generateBuildId(release, "OPPO")
        return DeviceTemplate(brand, model, brandModel, release, buildId)
    }

    private fun generateHonorTemplate(): DeviceTemplate {
        val brand = "HONOR"
        val manufacturer = "HONOR"
        val prefixes = listOf("PTP", "FCP", "ELP", "BVL", "VER", "AMP", "BRN", "CLK", "MAA", "NTH", "ANY")
        val pre = prefixes[RandomUtil.nextInt(0, prefixes.size)]
        val sub = listOf("00", "10", "60", "16")[RandomUtil.nextInt(0, 4)]
        val model = "$pre-AN$sub"
        val brandModel = "$brand/$manufacturer,$model"
        val release = "15"
        val buildId = "HONOR$model"
        return DeviceTemplate(brand, model, brandModel, release, buildId)
    }

    private fun generateHuaweiTemplate(): DeviceTemplate {
        val brand = "HUAWEI"
        val manufacturer = "HUAWEI"
        val prefixes = listOf("HBN", "HBP", "ALN", "BRA", "ADN", "CET", "NOH", "FIN")
        val pre = prefixes[RandomUtil.nextInt(0, prefixes.size)]
        val sub = listOf("00", "10", "20")[RandomUtil.nextInt(0, 3)]
        val net = if (RandomUtil.nextInt(0, 2) == 0) "AL" else "AN"
        val model = "$pre-$net$sub"
        val brandModel = "$brand/$manufacturer,$model"
        val release = "14"
        val buildId = "HUAWEI$model"
        return DeviceTemplate(brand, model, brandModel, release, buildId)
    }

    private fun generateSamsungTemplate(): DeviceTemplate {
        val brand = "samsung"
        val manufacturer = "samsung"
        val series = listOf("S", "A", "W", "F")[RandomUtil.nextInt(0, 4)]
        val num = when (series) {
            "S" -> listOf(938, 936, 931, 928, 926, 921, 948, 946, 941)[RandomUtil.nextInt(0, 9)]
            "A" -> listOf(556, 566, 356, 366)[RandomUtil.nextInt(0, 4)]
            "W" -> listOf(2025, 2026)[RandomUtil.nextInt(0, 2)]
            else -> listOf(956, 966)[RandomUtil.nextInt(0, 2)]
        }
        val model = "SM-$series$num" + if (series == "W") "" else "0"
        val brandModel = "$brand/$manufacturer,$model"
        val release = "15"
        val buildId = "UP1A." + "%02d%02d%02d".format(RandomUtil.nextInt(24, 26), RandomUtil.nextInt(1, 13), RandomUtil.nextInt(1, 29)) + ".007"
        return DeviceTemplate(brand, model, brandModel, release, buildId)
    }

    private fun generateBuildId(release: String, brand: String): String {
        val prefix = if (release == "16") {
            listOf("BP1A", "BP2A", "BP3A")[RandomUtil.nextInt(0, 3)]
        } else {
            listOf("AP3A", "AP4A", "AQ1A")[RandomUtil.nextInt(0, 3)]
        }
        val yy = RandomUtil.nextInt(24, 26) // 24, 25
        val mm = "%02d".format(RandomUtil.nextInt(1, 13))
        val dd = "%02d".format(RandomUtil.nextInt(1, 29))
        val rev = "%03d".format(RandomUtil.nextInt(1, 30))
        return "$prefix.$yy$mm$dd.$rev"
    }

    private fun generateDynamicChromeVer(): String {
        val major = RandomUtil.nextInt(154, 159) // 154 ~ 158 (2026年下半年最新)
        val build = RandomUtil.nextInt(7000, 7350)
        val patch = RandomUtil.nextInt(20, 180)
        return "$major.0.$build.$patch"
    }

    private fun generateDynamicUcVer(): String {
        val sub1 = RandomUtil.nextInt(2, 6)
        val sub2 = RandomUtil.nextInt(0, 9)
        val build = RandomUtil.nextInt(1520, 1690)
        return "17.$sub1.$sub2.$build"
    }

    private fun generateDynamicScreenWs(): String {
        val widths = listOf(360, 384, 393, 400, 412, 432, 440)
        val heights = listOf(780, 800, 853, 873, 900, 915, 960, 976)
        val w = widths[RandomUtil.nextInt(0, widths.size)]
        val h = heights[RandomUtil.nextInt(0, heights.size)]
        return "$w|0|$h"
    }

    private fun generateRandomDeviceId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/"
        return (1..24).map { chars[RandomUtil.nextInt(0, chars.length)] }.joinToString("")
    }

    private fun generateDynamicTemplate(mode: Int): DeviceTemplate {
        return when (mode) {
            2 -> generateVivoTemplate()
            3 -> generateXiaomiTemplate()
            4 -> generateOppoTemplate()
            5 -> generateHonorTemplate()
            6 -> generateSamsungTemplate()
            7 -> generateHuaweiTemplate()
            else -> {
                // mode == 1: 全品牌无限动态轮换
                when (RandomUtil.nextInt(0, 6)) {
                    0 -> generateVivoTemplate()
                    1 -> generateXiaomiTemplate()
                    2 -> generateOppoTemplate()
                    3 -> generateHonorTemplate()
                    4 -> generateSamsungTemplate()
                    else -> generateHuaweiTemplate()
                }
            }
        }
    }

    private fun generateDynamicProfile(mode: Int = 1): DeviceProfile {
        val tpl = generateDynamicTemplate(mode)
        val alipayVer = ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.26.8100"
        return DeviceProfile(
            brand = tpl.brand,
            model = tpl.model,
            brandModel = tpl.brandModel,
            release = tpl.defaultRelease,
            buildId = tpl.buildId,
            alipayVer = alipayVer,
            chromeVer = generateDynamicChromeVer(),
            ucVer = generateDynamicUcVer(),
            screenWs = generateDynamicScreenWs(),
            deviceId = generateRandomDeviceId()
        )
    }

    /**
     * 重置会话指纹缓存
     */
    fun resetSessionProfile() {
        sessionProfile = null
    }

    /**
     * 手动触发生成并保存全新随机真实设备指纹（仅在用户在 UI 点击生成按钮时调用）
     */
    fun generateAndSaveRandomProfile(context: android.content.Context? = null): DeviceProfile {
        val mode = runCatching {
            fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeUaMode().value ?: 1
        }.getOrDefault(1)

        val newProfile = generateDynamicProfile(mode)
        val json = newProfile.toJson()

        // 存入 OtherTask2 的配置字段并持久化保存
        fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeCustomProfile().value = json
        runCatching {
            fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
        }

        // 更新当前生效指纹
        sessionProfile = newProfile

        val logText = "[设备指纹] 🎲 动态规则引擎已生成新设备指纹: ${newProfile.summary()} | DeviceId: ${newProfile.deviceId}"
        Log.other("饿了么", logText)

        if (context != null) {
            runCatching {
                android.app.AlertDialog.Builder(context)
                    .setTitle("无限随机设备指纹生成成功")
                    .setMessage(
                        "已根据 2026 厂商真实入网规范动态生成全新设备：\n\n" +
                        "【品牌机型】${newProfile.brandModel}\n" +
                        "【系统版本】Android ${newProfile.release} (Build/${newProfile.buildId})\n" +
                        "【浏览器内核】Chrome ${newProfile.chromeVer} / UC ${newProfile.ucVer}\n" +
                        "【屏幕分辨率】${newProfile.screenWs}\n" +
                        "【设备标识ID】${newProfile.deviceId}\n\n" +
                        "✅ 动态规则引擎生成完毕，并固定保存至配置！日常任务将持续使用此指纹，不会变动。"
                    )
                    .setPositiveButton(fansirsqi.xposed.sesame.R.string.ok, null)
                    .create()
                    .show()
            }.onFailure {
                android.widget.Toast.makeText(context, "已生成并保存新设备指纹:\n${newProfile.brandModel} (Android ${newProfile.release})", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        return newProfile
    }

    /**
     * 获取当前生效的设备指纹配置（受 OtherTask2.elemeUaMode 控制）
     * 0: 系统默认 (本机系统/vivo V2238A)
     * 1~7: 动态指纹（日常完全固定使用已保存指纹，仅在用户点击【生成】按钮时重新生成，或手动在UI配置中修改）
     */
    fun getActiveProfile(): DeviceProfile {
        sessionProfile?.let { return it }

        val mode = runCatching {
            fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeUaMode().value ?: 1
        }.getOrDefault(1)

        val alipayVer = ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.26.8100"

        val profile = if (mode == 0) {
            val releaseVer = android.os.Build.VERSION.RELEASE.ifEmpty { "16" }
            val model = android.os.Build.MODEL.ifEmpty { "V2238A" }
            val brand = android.os.Build.BRAND.ifEmpty { "vivo" }
            val brandModel = if (model.startsWith(brand, ignoreCase = true)) "$brand/$model" else "$brand/$brand,$model"
            DeviceProfile(brand, model, brandModel, releaseVer, "BP1A.250305.019", alipayVer, "157.0.7150.128", "17.4.2.1610", "384|0|853", "asdLYB2rw6kDAN+qYdXj9afD")
        } else {
            // mode >= 1: 读取已持久化保存的动态指纹
            val savedJson = runCatching {
                fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeCustomProfile().value.orEmpty()
            }.getOrDefault("")
            val saved = if (savedJson.isNotEmpty()) DeviceProfile.fromJson(savedJson) else null
            if (saved != null) {
                saved
            } else {
                // 若初次运行尚未生成过，初始化生成一次并持久化保存
                generateAndSaveRandomProfile(null)
            }
        }

        sessionProfile = profile
        return profile
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
        val ttid = "2021005151656223@alipay_android_${profile.alipayVer}"

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
        val encodedTtid = URLEncoder.encode(ttid, "UTF-8")

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
        headers["User-Agent"] = profile.httpUa()
        if (extraHeaders != null) headers.putAll(extraHeaders)

        val resp = if (isPost) {
            headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8"
            val postBody = "data=$encodedData"
            ElemeHttpUtil.post(requestUrl, postBody, cookie, headers)
        } else {
            ElemeHttpUtil.get(requestUrl, cookie, headers)
        }

        if (resp == null) {
            val err = "[$api] 网络请求失败，未收到响应内容 (URL: $requestUrl)"
            lastError = err
            Log.error("ElemeMtopUtil", err)
            Log.other("饿了么", "❌ $err")
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
                Log.error("ElemeMtopUtil", "[$api] 会话已失效(需要重新登录)! 完整响应:\n$resp")
                Log.other("饿了么", "[$api] ⚠️ 会话已失效(需要重新登录)，自动清除本地凭据以便刷新\n完整响应:\n$resp")
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
                val err = "[$api] 响应失败: $readableErr\n完整响应:\n$resp"
                lastError = err
                // 使用 Log.error 记录异常日志与完整报文
                Log.error("ElemeMtopUtil", err)
                // 在主任务日志中输出错误并附带完整原始响应
                Log.other("饿了么", "❌ [$api] 响应失败: $readableErr\n完整响应:\n$resp")
                MtopResult(isSuccess = false, isNotLogin = false, errorCode = errorCode, errorMsg = readableErr, data = dataObj, rawJson = root)
            }
        }.onFailure {
            val err = "[$api] 解析 JSON 异常: ${it.message}\n原始响应:\n$resp"
            lastError = err
            Log.error("ElemeMtopUtil", err)
            Log.other("饿了么", "❌ $err")
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
