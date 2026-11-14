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

    // 真实主流 Android 设备特征指纹模板库（全面覆盖 vivo、小米/红米、OPPO/一加、荣耀、华为、三星）
    private val DEVICE_TEMPLATES = listOf(
        // vivo / iQOO
        DeviceTemplate("vivo", "V2309A", "vivo/vivo,V2309A", "14", "UP1A.231005.007"),
        DeviceTemplate("vivo", "V2324A", "vivo/vivo,V2324A", "14", "UP1A.231005.007"),
        DeviceTemplate("vivo", "V2339A", "vivo/vivo,V2339A", "14", "UP1A.231005.007"),
        DeviceTemplate("vivo", "V2307A", "vivo/vivo,V2307A", "14", "UP1A.231005.007"),
        DeviceTemplate("vivo", "V2238A", "vivo/vivo,V2238A", "13", "TKQ1.220829.002"),
        DeviceTemplate("vivo", "V2241A", "vivo/vivo,V2241A", "13", "TKQ1.220829.002"),
        // Xiaomi / Redmi
        DeviceTemplate("Xiaomi", "23116PN5BC", "Xiaomi/Xiaomi,23116PN5BC", "14", "UKQ1.230804.001"),
        DeviceTemplate("Xiaomi", "23127PN0CC", "Xiaomi/Xiaomi,23127PN0CC", "14", "UKQ1.230804.001"),
        DeviceTemplate("Xiaomi", "24031PN0DC", "Xiaomi/Xiaomi,24031PN0DC", "14", "UKQ1.230804.001"),
        DeviceTemplate("Redmi", "2311DRK48C", "Redmi/Xiaomi,2311DRK48C", "14", "UKQ1.230804.001"),
        DeviceTemplate("Redmi", "23117RK66C", "Redmi/Xiaomi,23117RK66C", "14", "UKQ1.230804.001"),
        DeviceTemplate("Xiaomi", "2210132C", "Xiaomi/Xiaomi,2210132C", "13", "TKQ1.220829.002"),
        // OPPO / OnePlus
        DeviceTemplate("OPPO", "PHY110", "OPPO/OPPO,PHY110", "14", "UKQ1.230924.001"),
        DeviceTemplate("OPPO", "PHT110", "OPPO/OPPO,PHT110", "14", "UKQ1.230924.001"),
        DeviceTemplate("OnePlus", "PJE110", "OnePlus/OnePlus,PJE110", "14", "UKQ1.230924.001"),
        DeviceTemplate("OnePlus", "PJF110", "OnePlus/OnePlus,PJF110", "14", "UKQ1.230924.001"),
        DeviceTemplate("OPPO", "PGEM10", "OPPO/OPPO,PGEM10", "13", "TP1A.220905.001"),
        // HONOR
        DeviceTemplate("HONOR", "BVL-AN00", "HONOR/HONOR,BVL-AN00", "14", "HONORBVL-AN00"),
        DeviceTemplate("HONOR", "BVL-AN16", "HONOR/HONOR,BVL-AN16", "14", "HONORBVL-AN16"),
        DeviceTemplate("HONOR", "VER-AN00", "HONOR/HONOR,VER-AN00", "13", "HONORVER-AN00"),
        DeviceTemplate("HONOR", "FRI-AN00", "HONOR/HONOR,FRI-AN00", "13", "HONORFRI-AN00"),
        // HUAWEI
        DeviceTemplate("HUAWEI", "ALN-AL00", "HUAWEI/HUAWEI,ALN-AL00", "12", "HUAWEIALN-AL00"),
        DeviceTemplate("HUAWEI", "ALN-AL10", "HUAWEI/HUAWEI,ALN-AL10", "12", "HUAWEIALN-AL10"),
        DeviceTemplate("HUAWEI", "HBP-AL00", "HUAWEI/HUAWEI,HBP-AL00", "12", "HUAWEIHBP-AL00"),
        // Samsung
        DeviceTemplate("samsung", "SM-S9280", "samsung/samsung,SM-S9280", "14", "UP1A.231005.007"),
        DeviceTemplate("samsung", "SM-S9210", "samsung/samsung,SM-S9210", "14", "UP1A.231005.007"),
        DeviceTemplate("samsung", "SM-S9180", "samsung/samsung,SM-S9180", "13", "TP1A.220624.014")
    )

    private val CHROME_VERSIONS = listOf("118.0.0.0", "121.0.0.0", "124.0.0.0", "126.0.0.0", "128.0.0.0", "130.0.0.0")
    private val UC_VERSIONS = listOf("16.3.8.1402", "16.2.8.1396", "16.5.0.1420", "15.9.6.1360", "16.0.4.1378")
    private val SCREEN_RESOLUTIONS = listOf("384|0|853", "393|0|873", "412|0|915", "360|0|800", "432|0|960", "390|0|844")

    private fun generateRandomDeviceId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/"
        return (1..24).map { chars[RandomUtil.nextInt(0, chars.length)] }.joinToString("")
    }

    private fun buildProfileFromTemplate(tpl: DeviceTemplate): DeviceProfile {
        val alipayVer = ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.30.8000"
        val chromeVer = CHROME_VERSIONS[RandomUtil.nextInt(0, CHROME_VERSIONS.size)]
        val ucVer = UC_VERSIONS[RandomUtil.nextInt(0, UC_VERSIONS.size)]
        val screenWs = SCREEN_RESOLUTIONS[RandomUtil.nextInt(0, SCREEN_RESOLUTIONS.size)]
        val deviceId = generateRandomDeviceId()
        return DeviceProfile(
            brand = tpl.brand,
            model = tpl.model,
            brandModel = tpl.brandModel,
            release = tpl.defaultRelease,
            buildId = tpl.buildId,
            alipayVer = alipayVer,
            chromeVer = chromeVer,
            ucVer = ucVer,
            screenWs = screenWs,
            deviceId = deviceId
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
        val newProfile = buildProfileFromTemplate(DEVICE_TEMPLATES[RandomUtil.nextInt(0, DEVICE_TEMPLATES.size)])
        val json = newProfile.toJson()

        // 存入 OtherTask2 的配置字段并持久化保存
        fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeCustomProfile().value = json
        runCatching {
            fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
        }

        // 更新当前生效指纹
        sessionProfile = newProfile

        val logText = "[设备指纹] 🎲 用户点击生成新设备指纹: ${newProfile.summary()} | DeviceId: ${newProfile.deviceId}"
        Log.other("饿了么", logText)

        if (context != null) {
            runCatching {
                android.app.AlertDialog.Builder(context)
                    .setTitle("随机设备指纹生成成功")
                    .setMessage(
                        "已生成并保存全新设备指纹：\n\n" +
                        "【品牌机型】${newProfile.brandModel}\n" +
                        "【系统版本】Android ${newProfile.release} (Build/${newProfile.buildId})\n" +
                        "【浏览器内核】Chrome ${newProfile.chromeVer} / UC ${newProfile.ucVer}\n" +
                        "【屏幕分辨率】${newProfile.screenWs}\n" +
                        "【设备标识ID】${newProfile.deviceId}\n\n" +
                        "✅ 已固定保存至配置！日常任务将固定使用此设备指纹，不会随意变动。"
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
     * 1: 🎲 随机生成设备指纹（日常完全固定使用已保存指纹，仅在用户点击【生成】按钮时重新生成）
     * 2: vivo 系列固定机型 (vivo X100 Pro)
     * 3: Xiaomi 系列固定机型 (Xiaomi 14)
     * 4: OPPO 系列固定机型 (OPPO Find X7)
     * 5: HONOR 系列固定机型 (HONOR Magic6)
     */
    fun getActiveProfile(): DeviceProfile {
        sessionProfile?.let { return it }

        val mode = runCatching {
            fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeUaMode().value ?: 1
        }.getOrDefault(1)

        val alipayVer = ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.30.8000"

        val profile = when (mode) {
            0 -> {
                val releaseVer = android.os.Build.VERSION.RELEASE.ifEmpty { "16" }
                val model = android.os.Build.MODEL.ifEmpty { "V2238A" }
                val brand = android.os.Build.BRAND.ifEmpty { "vivo" }
                val brandModel = if (model.startsWith(brand, ignoreCase = true)) "$brand/$model" else "$brand/$brand,$model"
                DeviceProfile(brand, model, brandModel, releaseVer, "BP1A.250305.019", alipayVer, "118.0.0.0", "16.3.8.1402", "384|0|853", "asdLYB2rw6kDAN+qYdXj9afD")
            }
            2 -> {
                DeviceProfile("vivo", "V2309A", "vivo/vivo,V2309A", "14", "UP1A.231005.007", alipayVer, "128.0.0.0", "16.3.8.1402", "384|0|853", "asdLYB2rw6kDAN+qYdXj9afD")
            }
            3 -> {
                DeviceProfile("Xiaomi", "23116PN5BC", "Xiaomi/Xiaomi,23116PN5BC", "14", "UKQ1.230804.001", alipayVer, "128.0.0.0", "16.3.8.1402", "393|0|873", "asdLYB2rw6kDAN+qYdXj9afD")
            }
            4 -> {
                DeviceProfile("OPPO", "PHY110", "OPPO/OPPO,PHY110", "14", "UKQ1.230924.001", alipayVer, "128.0.0.0", "16.3.8.1402", "412|0|915", "asdLYB2rw6kDAN+qYdXj9afD")
            }
            5 -> {
                DeviceProfile("HONOR", "BVL-AN00", "HONOR/HONOR,BVL-AN00", "14", "HONORBVL-AN00", alipayVer, "128.0.0.0", "16.3.8.1402", "384|0|853", "asdLYB2rw6kDAN+qYdXj9afD")
            }
            else -> {
                // mode == 1: 🎲 随机生成设备指纹（日常完全固定，仅在用户点击生成按钮时更新）
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
