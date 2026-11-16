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
        val deviceId: String,
        val customHttpUa: String? = null,
        val marketingName: String? = null
    ) {
        fun eleUa(appId: String = ElemeAuth.APP_ID): String =
            "RenderWay/miniProgram MiniAppId/$appId MiniAppVersion/12.1.53 DeviceId/$deviceId AppName/ap $brandModel Android/$release AlipayClient/$alipayVer AliApp(AP/$alipayVer) userMode/student channel/mini_app subChannel/miniapp.alipay subSubChannel/miniapp.alipay.et"

        fun httpUa(): String =
            if (!customHttpUa.isNullOrBlank()) {
                customHttpUa
            } else {
                val deviceToken = if (model.startsWith(brand, ignoreCase = true)) {
                    model
                } else if (brand.equals("vivo", ignoreCase = true) || brand.equals("iQOO", ignoreCase = true)) {
                    "$brand $model"
                } else {
                    model
                }
                "Mozilla/5.0 (Linux; U; Android $release; zh-CN; $deviceToken Build/$buildId) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/$chromeVer UCBrowser/$ucVer Mobile Safari/537.36 AlipayDefined(nt:wifi,ws:$screenWs) AliApp(AP/$alipayVer) AlipayClient/$alipayVer Language/zh-Hans useStatusBar/true isConcaveScreen/false Region/CN Channel/${brand.lowercase()} GCanvas/1.9.38 Nebula SDK/1.8.1000 Nebula Ariver/1.0.0"
            }

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
            if (!customHttpUa.isNullOrBlank()) jo.put("customHttpUa", customHttpUa)
            if (!marketingName.isNullOrBlank()) jo.put("marketingName", marketingName)
            return jo.toString()
        }

        fun summary(): String {
            val name = marketingName?.takeIf { it.isNotBlank() } ?: brandModel
            return if (!customHttpUa.isNullOrBlank()) {
                "自定义UA ($name, Android $release)"
            } else {
                "$name ($brandModel, Android $release, Chrome/$chromeVer, UC/$ucVer)"
            }
        }

        companion object {
            fun fromJson(jsonStr: String): DeviceProfile? {
                return runCatching {
                    val jo = JSONObject(jsonStr)
                    val alipayVer = jo.optString("alipayVer").ifEmpty {
                        getSafeAlipayVersion("12.12.30.8000")
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
                        deviceId = jo.getString("deviceId"),
                        customHttpUa = jo.optString("customHttpUa").takeIf { it.isNotEmpty() },
                        marketingName = jo.optString("marketingName").takeIf { it.isNotEmpty() }
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
        val marketingName: String,
        val brand: String,
        val model: String,
        val brandModel: String,
        val defaultRelease: String,
        val buildId: String,
        val defaultScreen: String = "412|0|915"
    )

    // =========================================================================
    // 近几年真实热门市售手机机型库（涵盖 K70/K80 等热门类型及其各个细分实体机型）
    // =========================================================================

    // 1. Redmi K70 / K80 热门系列（即用户专门指定的“K70类型”及其具体真实机型）
    private val FAMILY_REDMI_K = listOf(
        DeviceTemplate("Redmi K70", "Redmi", "2311DRK48C", "Redmi/Xiaomi,2311DRK48C", "14", "UKQ1.230804.001", "412|0|915"),
        DeviceTemplate("Redmi K70 Pro", "Redmi", "23117RK66C", "Redmi/Xiaomi,23117RK66C", "14", "UKQ1.230804.001", "412|0|915"),
        DeviceTemplate("Redmi K70 至尊版", "Redmi", "2407FRK8EC", "Redmi/Xiaomi,2407FRK8EC", "14", "UKQ1.231003.002", "393|0|873"),
        DeviceTemplate("Redmi K70E", "Redmi", "2311FRAFDC", "Redmi/Xiaomi,2311FRAFDC", "14", "UKQ1.230804.001", "393|0|873"),
        DeviceTemplate("Redmi K80", "Redmi", "24117RK2CC", "Redmi/Xiaomi,24117RK2CC", "15", "VKQ1.240723.001", "412|0|915"),
        DeviceTemplate("Redmi K80 Pro", "Redmi", "24122RKC7C", "Redmi/Xiaomi,24122RKC7C", "15", "VKQ1.240723.001", "412|0|915")
    )

    // 2. 小米 14 / 15 旗舰系列
    private val FAMILY_XIAOMI_NUM = listOf(
        DeviceTemplate("Xiaomi 14", "Xiaomi", "23127PN0CC", "Xiaomi/Xiaomi,23127PN0CC", "14", "UKQ1.230804.001", "393|0|873"),
        DeviceTemplate("Xiaomi 14 Pro", "Xiaomi", "23116PN5BC", "Xiaomi/Xiaomi,23116PN5BC", "14", "UKQ1.230804.001", "412|0|915"),
        DeviceTemplate("Xiaomi 14 Ultra", "Xiaomi", "24031PN0DC", "Xiaomi/Xiaomi,24031PN0DC", "14", "AP3A.241005.015", "412|0|915"),
        DeviceTemplate("Xiaomi 15", "Xiaomi", "24129PN74C", "Xiaomi/Xiaomi,24129PN74C", "15", "VKQ1.240723.001", "393|0|873"),
        DeviceTemplate("Xiaomi 15 Pro", "Xiaomi", "24101PNB7C", "Xiaomi/Xiaomi,24101PNB7C", "15", "VKQ1.240723.001", "412|0|915")
    )

    // 3. vivo X100 / X200 / iQOO 系列
    private val FAMILY_VIVO_IQOO = listOf(
        DeviceTemplate("vivo X100", "vivo", "V2309A", "vivo/vivo,V2309A", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("vivo X100 Pro", "vivo", "V2324A", "vivo/vivo,V2324A", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("vivo X100 Ultra", "vivo", "V2366GA", "vivo/vivo,V2366GA", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("vivo X100s", "vivo", "V2359A", "vivo/vivo,V2359A", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("vivo X200", "vivo", "V2415A", "vivo/vivo,V2415A", "15", "AP3A.241005.015", "412|0|915"),
        DeviceTemplate("vivo X200 Pro", "vivo", "V2405A", "vivo/vivo,V2405A", "15", "AP3A.241005.015", "412|0|915"),
        DeviceTemplate("vivo X200 Pro mini", "vivo", "V2419A", "vivo/vivo,V2419A", "15", "AP3A.241005.015", "393|0|873"),
        DeviceTemplate("iQOO 12", "iQOO", "V2307A", "iQOO/vivo,V2307A", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("iQOO 13", "iQOO", "V2408A", "iQOO/vivo,V2408A", "15", "AP3A.241005.015", "412|0|915"),
        DeviceTemplate("iQOO Neo9", "iQOO", "V2338A", "iQOO/vivo,V2338A", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("iQOO Neo9S Pro+", "iQOO", "V2402A", "iQOO/vivo,V2402A", "14", "AP3A.240905.015", "412|0|915")
    )

    // 4. OPPO Find X7 / X8 / 一加 12 / 13 / Ace 系列
    private val FAMILY_OPPO_ONEPLUS = listOf(
        DeviceTemplate("OPPO Find X7", "OPPO", "PHZ110", "OPPO/OPPO,PHZ110", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("OPPO Find X7 Ultra", "OPPO", "PHY110", "OPPO/OPPO,PHY110", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("OPPO Find X8", "OPPO", "PKB110", "OPPO/OPPO,PKB110", "15", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("OPPO Find X8 Pro", "OPPO", "PKC110", "OPPO/OPPO,PKC110", "15", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("OnePlus 12", "OnePlus", "PJD110", "OnePlus/OnePlus,PJD110", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("OnePlus 13", "OnePlus", "PJZ110", "OnePlus/OnePlus,PJZ110", "15", "AP3A.241005.015", "412|0|915"),
        DeviceTemplate("OnePlus Ace 3", "OnePlus", "PJE110", "OnePlus/OnePlus,PJE110", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("OnePlus Ace 3 Pro", "OnePlus", "PJX110", "OnePlus/OnePlus,PJX110", "14", "AP3A.240905.015", "412|0|915"),
        DeviceTemplate("OnePlus Ace 5", "OnePlus", "PKG110", "OnePlus/OnePlus,PKG110", "15", "AP3A.241005.015", "412|0|915")
    )

    // 5. HONOR Magic 6 / 7 系列
    private val FAMILY_HONOR = listOf(
        DeviceTemplate("HONOR Magic6", "HONOR", "BVL-AN00", "HONOR/HONOR,BVL-AN00", "14", "HONORBVL-AN00", "412|0|915"),
        DeviceTemplate("HONOR Magic6 Pro", "HONOR", "BVL-AN10", "HONOR/HONOR,BVL-AN10", "14", "HONORBVL-AN10", "412|0|915"),
        DeviceTemplate("HONOR Magic7", "HONOR", "PTP-AN60", "HONOR/HONOR,PTP-AN60", "15", "HONORPTP-AN60", "412|0|915"),
        DeviceTemplate("HONOR Magic7 Pro", "HONOR", "PTP-AN10", "HONOR/HONOR,PTP-AN10", "15", "HONORPTP-AN10", "412|0|915"),
        DeviceTemplate("HONOR Magic V3", "HONOR", "FCP-AN10", "HONOR/HONOR,FCP-AN10", "14", "HONORFCP-AN10", "448|0|996"),
        DeviceTemplate("HONOR 200 Pro", "HONOR", "ELP-AN00", "HONOR/HONOR,ELP-AN00", "14", "HONORELP-AN00", "412|0|915")
    )

    // 6. HUAWEI Mate 60 / 70 / Pura 70 系列
    private val FAMILY_HUAWEI = listOf(
        DeviceTemplate("HUAWEI Mate 60 Pro", "HUAWEI", "ALN-AL00", "HUAWEI/HUAWEI,ALN-AL00", "14", "HUAWEIALN-AL00", "412|0|915"),
        DeviceTemplate("HUAWEI Mate 70 Pro", "HUAWEI", "HBN-AL00", "HUAWEI/HUAWEI,HBN-AL00", "15", "HUAWEIHBN-AL00", "412|0|915"),
        DeviceTemplate("HUAWEI Pura 70", "HUAWEI", "ADY-AL00", "HUAWEI/HUAWEI,ADY-AL00", "14", "HUAWEIADY-AL00", "412|0|915"),
        DeviceTemplate("HUAWEI Pura 70 Pro", "HUAWEI", "HBN-AL10", "HUAWEI/HUAWEI,HBN-AL10", "14", "HUAWEIHBN-AL10", "412|0|915"),
        DeviceTemplate("HUAWEI Pura 70 Ultra", "HUAWEI", "HBP-AL00", "HUAWEI/HUAWEI,HBP-AL00", "14", "HUAWEIHBP-AL00", "412|0|915")
    )

    // 7. Samsung Galaxy S24 / S25 系列
    private val FAMILY_SAMSUNG = listOf(
        DeviceTemplate("Galaxy S24", "samsung", "SM-S9210", "samsung/samsung,SM-S9210", "14", "UP1A.241005.007", "384|0|853"),
        DeviceTemplate("Galaxy S24 Ultra", "samsung", "SM-S9280", "samsung/samsung,SM-S9280", "14", "UP1A.241005.007", "412|0|915"),
        DeviceTemplate("Galaxy S25", "samsung", "SM-S9310", "samsung/samsung,SM-S9310", "15", "UP1A.241005.007", "384|0|853"),
        DeviceTemplate("Galaxy S25+", "samsung", "SM-S9360", "samsung/samsung,SM-S9360", "15", "UP1A.241005.007", "412|0|915"),
        DeviceTemplate("Galaxy S25 Ultra", "samsung", "SM-S9380", "samsung/samsung,SM-S9380", "15", "UP1A.241005.007", "412|0|915")
    )

    // 所有近几年真实热门机型汇总池
    private val ALL_REAL_MODELS: List<DeviceTemplate> =
        FAMILY_REDMI_K + FAMILY_XIAOMI_NUM + FAMILY_VIVO_IQOO + FAMILY_OPPO_ONEPLUS + FAMILY_HONOR + FAMILY_HUAWEI + FAMILY_SAMSUNG

    private fun generateDynamicChromeVer(): String {
        val major = RandomUtil.nextInt(156, 159) // 156 ~ 158 (2026年最新)
        val build = RandomUtil.nextInt(7100, 7350)
        val patch = RandomUtil.nextInt(30, 180)
        return "$major.0.$build.$patch"
    }

    private fun generateDynamicUcVer(): String {
        val sub1 = RandomUtil.nextInt(2, 6)
        val sub2 = RandomUtil.nextInt(0, 9)
        val build = RandomUtil.nextInt(1520, 1690)
        return "17.$sub1.$sub2.$build"
    }

    private fun generateRandomDeviceId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/"
        return (1..24).map { chars[RandomUtil.nextInt(0, chars.length)] }.joinToString("")
    }

    private fun generateDynamicTemplate(mode: Int): DeviceTemplate {
        return when (mode) {
            3 -> FAMILY_REDMI_K[RandomUtil.nextInt(0, FAMILY_REDMI_K.size)]
            4 -> FAMILY_XIAOMI_NUM[RandomUtil.nextInt(0, FAMILY_XIAOMI_NUM.size)]
            5 -> FAMILY_VIVO_IQOO[RandomUtil.nextInt(0, FAMILY_VIVO_IQOO.size)]
            6 -> FAMILY_OPPO_ONEPLUS[RandomUtil.nextInt(0, FAMILY_OPPO_ONEPLUS.size)]
            7 -> FAMILY_HONOR[RandomUtil.nextInt(0, FAMILY_HONOR.size)]
            8 -> FAMILY_HUAWEI[RandomUtil.nextInt(0, FAMILY_HUAWEI.size)]
            9 -> FAMILY_SAMSUNG[RandomUtil.nextInt(0, FAMILY_SAMSUNG.size)]
            else -> {
                // mode == 2 或其他: 🎲 近几年全系真实热门机型随机选取
                ALL_REAL_MODELS[RandomUtil.nextInt(0, ALL_REAL_MODELS.size)]
            }
        }
    }

    @JvmStatic
    fun getSafeAlipayVersion(fallback: String = "12.12.30.8000"): String {
        return try {
            fansirsqi.xposed.sesame.hook.context.AppContext.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun generateDynamicProfile(mode: Int = 2): DeviceProfile {
        val tpl = generateDynamicTemplate(mode)
        val alipayVer = getSafeAlipayVersion("12.12.26.8100")
        return DeviceProfile(
            brand = tpl.brand,
            model = tpl.model,
            brandModel = tpl.brandModel,
            release = tpl.defaultRelease,
            buildId = tpl.buildId,
            alipayVer = alipayVer,
            chromeVer = generateDynamicChromeVer(),
            ucVer = generateDynamicUcVer(),
            screenWs = tpl.defaultScreen,
            deviceId = generateRandomDeviceId(),
            marketingName = tpl.marketingName
        )
    }

    /**
     * 智能解析并提取自定义 User-Agent 字符串，构造完整适配的 DeviceProfile
     */
    fun parseCustomUa(rawUa: String, fallbackAlipayVer: String = "12.12.26.8100"): DeviceProfile {
        val ua = rawUa.trim()

        // 1. 提取 Android 版本
        val releaseRegex = Regex("""Android\s+([0-9\.]+)""", RegexOption.IGNORE_CASE)
        val release = releaseRegex.find(ua)?.groupValues?.get(1)?.trim() ?: "14"

        // 2. 提取 Build ID (Build/xxx)
        val buildRegex = Regex("""Build/([a-zA-Z0-9_\.\-]+)""", RegexOption.IGNORE_CASE)
        val buildId = buildRegex.find(ua)?.groupValues?.get(1)?.trim() ?: "AP3A.241005.015"

        // 3. 提取机型 Model
        val modelMatch = Regex("""Android\s+[0-9\.]+;\s*(?:[a-zA-Z_\-]+;\s*)?([a-zA-Z0-9_\-\s,/]+?)(?:\s+Build|[;\)])""", RegexOption.IGNORE_CASE).find(ua)
        var rawModel = modelMatch?.groupValues?.get(1)?.trim() ?: ""
        if (rawModel.isEmpty() || rawModel.equals("wv", ignoreCase = true) || rawModel.equals("Mobile", ignoreCase = true)) {
            val alt = Regex(""";\s*([a-zA-Z0-9_\-]+(?:/[a-zA-Z0-9_\-]+)?)\s*(?:Build|\))""", RegexOption.IGNORE_CASE).find(ua)
            rawModel = alt?.groupValues?.get(1)?.trim() ?: "2311DRK48C"
        }

        // 4. 提取支付宝版本
        val alipayVerMatch = Regex("""AlipayClient/([0-9\.]+)""", RegexOption.IGNORE_CASE).find(ua)
            ?: Regex("""AliApp\(AP/([0-9\.]+)""", RegexOption.IGNORE_CASE).find(ua)
        val alipayVer = alipayVerMatch?.groupValues?.get(1) ?: fallbackAlipayVer

        // 5. 提取 Chrome 版本
        val chromeVerMatch = Regex("""Chrome/([0-9\.]+)""", RegexOption.IGNORE_CASE).find(ua)
        val chromeVer = chromeVerMatch?.groupValues?.get(1) ?: "157.0.7150.128"

        // 6. 提取 UCBrowser 版本
        val ucVerMatch = Regex("""UCBrowser/([0-9\.]+)""", RegexOption.IGNORE_CASE).find(ua)
        val ucVer = ucVerMatch?.groupValues?.get(1) ?: "17.4.2.1610"

        // 7. 提取屏幕分辨率 ws:412|0|915
        val wsMatch = Regex("""ws:([0-9\|]+)""", RegexOption.IGNORE_CASE).find(ua)
        val screenWs = wsMatch?.groupValues?.get(1) ?: "412|0|915"

        // 8. 推导 Brand 与 Model
        val brand: String
        val model: String
        val brandModel: String
        if (rawModel.contains("/")) {
            val parts = rawModel.split("/")
            brand = parts[0]
            model = parts.getOrNull(1)?.substringAfter(",") ?: rawModel
            brandModel = rawModel
        } else {
            model = rawModel
            brand = when {
                model.startsWith("23", true) || model.startsWith("24", true) || model.startsWith("25", true) -> {
                    if (model.contains("RK", true) || model.contains("RA", true)) "Redmi" else "Xiaomi"
                }
                model.startsWith("V2", true) -> "vivo"
                model.startsWith("PK", true) || model.startsWith("PJ", true) || model.startsWith("PH", true) -> "OPPO"
                model.startsWith("PTP", true) || model.startsWith("BVL", true) || model.startsWith("FCP", true) -> "HONOR"
                model.startsWith("HBN", true) || model.startsWith("ALN", true) || model.startsWith("HBP", true) -> "HUAWEI"
                model.startsWith("SM-", true) -> "samsung"
                else -> "Android"
            }
            brandModel = if (brand.equals("Android", ignoreCase = true)) model else "$brand/$brand,$model"
        }

        val deviceId = generateRandomDeviceId()
        return DeviceProfile(
            brand = brand,
            model = model,
            brandModel = brandModel,
            release = release,
            buildId = buildId,
            alipayVer = alipayVer,
            chromeVer = chromeVer,
            ucVer = ucVer,
            screenWs = screenWs,
            deviceId = deviceId,
            customHttpUa = ua,
            marketingName = "自定义UA ($model)"
        )
    }

    /**
     * 获取系统原生默认设备配置
     */
    private fun getDefaultNativeProfile(): DeviceProfile {
        val alipayVer = getSafeAlipayVersion("12.12.30.8000")
        val releaseVer = android.os.Build.VERSION.RELEASE.ifEmpty { "14" }
        val model = android.os.Build.MODEL.ifEmpty { "V2238A" }
        val brand = android.os.Build.BRAND.ifEmpty { "vivo" }
        val brandModel = if (model.startsWith(brand, ignoreCase = true)) "$brand/$model" else "$brand/$brand,$model"
        return DeviceProfile(
            brand = brand,
            model = model,
            brandModel = brandModel,
            release = releaseVer,
            buildId = android.os.Build.ID.ifEmpty { "UP1A.231005.007" },
            alipayVer = alipayVer,
            chromeVer = "130.0.6723.107",
            ucVer = "17.4.2.1610",
            screenWs = "412|0|915",
            deviceId = "asdLYB2rw6kDAN+qYdXj9afD",
            marketingName = "系统原版默认"
        )
    }

    /**
     * 重置会话指纹缓存
     */
    fun resetSessionProfile() {
        sessionProfile = null
    }

    @Volatile
    private var lastUaChangeTimestamp: Long = 0L
    private const val UA_CHANGE_COOLDOWN_MS = 10 * 60 * 1000L // 10分钟修改冷却防风控

    /**
     * 获取近几年真实热门机型的完整真实 UA
     */
    @JvmStatic
    fun getRandomRealDeviceUa(): String {
        return runCatching {
            generateDynamicProfile(2).httpUa()
        }.getOrDefault("Mozilla/5.0 (Linux; U; Android 14; zh-CN; V2238A; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.107 UCBrowser/17.4.2.1610 Mobile Safari/537.36 AliApp(AP/12.12.30.8000) AlipayClient/12.12.30.8000 Language/zh-Hans Region/CN bLanguage/zh-Hans")
    }

    /**
     * 检查是否处于 UA 修改冷却期
     */
    @JvmStatic
    fun checkUaChangeCooldown(newUa: String): Pair<Boolean, String> {
        val currentUa = ElemeTask.getElemeCustomUa().value.orEmpty().trim()
        val trimmedNew = newUa.trim()
        // 内容未改变直接通过
        if (currentUa == trimmedNew) {
            return Pair(true, "")
        }
        val now = System.currentTimeMillis()
        val elapsed = now - lastUaChangeTimestamp
        if (lastUaChangeTimestamp > 0 && elapsed < UA_CHANGE_COOLDOWN_MS) {
            val remainMins = Math.max(1L, (UA_CHANGE_COOLDOWN_MS - elapsed + 59999L) / 60000L)
            return Pair(false, "频繁更换设备指纹易触发阿里风控检测，修改冷却中，请于 ${remainMins} 分钟后再试！")
        }
        return Pair(true, "")
    }

    @JvmStatic
    fun recordUaChanged() {
        lastUaChangeTimestamp = System.currentTimeMillis()
        resetSessionProfile()
    }

    /**
     * 获取指定或当前生效 UA 的结构化解析信息（供 Web 或 Native UI 弹窗实时预览展示）
     */
    @JvmStatic
    fun getProfileJson(rawUa: String? = null): String {
        return runCatching {
            val profile = if (!rawUa.isNullOrBlank()) {
                parseCustomUa(rawUa)
            } else {
                getActiveProfile()
            }
            val obj = JSONObject().apply {
                put("model", profile.marketingName?.takeIf { it.isNotBlank() } ?: profile.model)
                put("brandModel", profile.brandModel)
                put("system", "Android ${profile.release} (Build/${profile.buildId})")
                put("kernel", "Chrome ${profile.chromeVer} / UC ${profile.ucVer}")
                put("screen", profile.screenWs)
                put("alipayVer", profile.alipayVer)
                put("ua", profile.httpUa())
                put("isCustom", !rawUa.isNullOrBlank())
            }
            obj.toString()
        }.getOrElse {
            "{\"model\":\"系统原版默认\",\"brandModel\":\"vivo/V2238A\",\"system\":\"Android 14\",\"kernel\":\"Chrome / UC\",\"screen\":\"412|0|915\",\"alipayVer\":\"12.12.30.8000\",\"ua\":\"\",\"isCustom\":false}"
        }
    }

    /**
     * 解析任务领奖或抽奖响应中的奖品描述文案
     */
    @JvmStatic
    fun extractPrizeDesc(root: JSONObject?): String {
        if (root == null) return ""
        val data = root.optJSONObject("data") ?: root
        val rewards = data.optJSONArray("rewards")
        if (rewards != null && rewards.length() > 0) {
            val parts = mutableListOf<String>()
            for (i in 0 until rewards.length()) {
                val r = rewards.optJSONObject(i) ?: continue
                val name = r.optString("prizeName").ifEmpty { r.optString("name") }
                val amount = r.optString("prizeAmount").ifEmpty { r.optString("amount") }.ifEmpty { r.optString("prizeValue") }
                val unit = r.optString("prizeUnit").ifEmpty { r.optString("unit") }
                if (amount.isNotEmpty() && name.isNotEmpty()) {
                    parts.add("$amount$unit$name")
                } else if (amount.isNotEmpty()) {
                    parts.add("$amount$unit")
                } else if (name.isNotEmpty()) {
                    parts.add(name)
                }
            }
            if (parts.isNotEmpty()) return parts.joinToString(", ")
        }
        val prizeName = data.optString("prizeName").ifEmpty { data.optString("name") }
        if (prizeName.isNotEmpty()) return prizeName
        val couponName = data.optString("couponName")
        if (couponName.isNotEmpty()) return couponName
        return ""
    }

    /**
     * 手动触发随机生成近几年真实市售热门机型（如 Redmi K70 系列、小米15、vivo X100 等）的真实完整 UA
     * 仅在用户在 UI 点击【🎲 随机生成真实设备UA】按钮时触发，日常任务执行绝不会变动
     */
    @JvmStatic
    @JvmOverloads
    fun generateAndSaveRandomProfile(context: android.content.Context? = null): DeviceProfile {
        val newProfile = generateDynamicProfile(2)
        val generatedUa = newProfile.httpUa()

        // 同步填入 ElemeTask 的自定义 UA 配置并持久化保存
        ElemeTask.getElemeCustomUa().value = generatedUa
        recordUaChanged()
        runCatching {
            fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
        }

        // 更新当前会话生效指纹
        sessionProfile = newProfile

        if (context != null) {
            runCatching {
                val displayName = newProfile.marketingName?.takeIf { it.isNotBlank() } ?: newProfile.model
                val msg = "已从近几年真实热门机型库中随机生成：\n\n" +
                        "【机型品名】$displayName (${newProfile.model})\n" +
                        "【系统版本】Android ${newProfile.release} (Build/${newProfile.buildId})\n" +
                        "【浏览器内核】Chrome ${newProfile.chromeVer} / UC ${newProfile.ucVer}\n" +
                        "【屏幕分辨率】${newProfile.screenWs}\n\n" +
                        "【生成的完整 User-Agent】:\n$generatedUa\n\n" +
                        "✅ 已自动填入【User-Agent设备设置】中并持久化保存！日常执行将稳定使用该 UA。"

                android.app.AlertDialog.Builder(context)
                    .setTitle("真实设备 UA 生成成功")
                    .setMessage(msg)
                    .setPositiveButton(fansirsqi.xposed.sesame.R.string.ok, null)
                    .create()
                    .show()
            }.onFailure {
                android.widget.Toast.makeText(context, "已生成并保存真实设备UA: ${newProfile.summary()}", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        return newProfile
    }

    /**
     * 原生对话框：详情展示、UA查看与编辑、弹窗内点击随机生成、保存前防频改校验合一
     */
    /**
     * 获取当前生效设备的简短型号名称（如 "vivo X100", "Xiaomi 15", "系统默认"）
     */
    @JvmStatic
    fun getCurrentDeviceShortName(): String {
        return runCatching {
            val customUa = ElemeTask.getElemeCustomUa().value.orEmpty().trim()
            if (customUa.isEmpty()) {
                "系统默认"
            } else {
                val p = parseCustomUa(customUa)
                p.marketingName?.takeIf { it.isNotBlank() } ?: p.model
            }
        }.getOrDefault("系统默认")
    }

    fun isDarkThemeMode(context: android.content.Context?): Boolean {
        if (context == null) return false
        return runCatching {
            fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme.shouldUseDarkTheme()
        }.getOrElse {
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    /**
     * 弹窗查看当前生效的设备指纹详情、一键随机生成近几年真实热门机型 UA 并支持自定义编辑
     * 完美适配深浅色（夜间）模式，按钮文本纯净化
     */
    @JvmStatic
    @JvmOverloads
    fun showActiveProfileDialog(context: android.content.Context? = null) {
        if (context == null) return
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return

        val currentUa = ElemeTask.getElemeCustomUa().value.orEmpty().trim()
        val profile = getActiveProfile()
        val displayName = profile.marketingName?.takeIf { it.isNotBlank() } ?: profile.model

        val detailMsg = "【当前生效设备详情】\n" +
                "• 识别机型: $displayName (${profile.brandModel})\n" +
                "• 系统版本: Android ${profile.release} (Build/${profile.buildId})\n" +
                "• 浏览器内核: Chrome ${profile.chromeVer} / UC ${profile.ucVer}\n" +
                "• 屏幕分辨率: ${profile.screenWs}\n\n" +
                "【User-Agent 自定义与生成】:\n" +
                "(可点击下方“随机生成”快速切换真实热门机型，最后点击“保存”生效)"

        // 深浅色模式判断与颜色动态适配
        val isNight = isDarkThemeMode(context)
        val primaryTextColor = if (isNight) android.graphics.Color.parseColor("#E0E0E0") else android.graphics.Color.parseColor("#212121")
        val secondaryTextColor = if (isNight) android.graphics.Color.parseColor("#B0BEC5") else android.graphics.Color.parseColor("#555555")
        val positiveButtonColor = if (isNight) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#2E7D32")
        val neutralButtonColor = if (isNight) android.graphics.Color.parseColor("#64B5F6") else android.graphics.Color.parseColor("#1976D2")
        val negativeButtonColor = if (isNight) android.graphics.Color.parseColor("#B0BEC5") else android.graphics.Color.parseColor("#546E7A")

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val tvInfo = android.widget.TextView(context).apply {
            text = detailMsg
            textSize = 13f
            setTextColor(secondaryTextColor)
            setPadding(0, 0, 0, 15)
            setLineSpacing(4f, 1f)
        }

        val edt = android.widget.EditText(context).apply {
            minLines = 4
            maxLines = 10
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            isScrollContainer = true
            setText(currentUa.ifEmpty { profile.httpUa() })
            textSize = 12f
            setTextColor(primaryTextColor)
            setHintTextColor(if (isNight) android.graphics.Color.parseColor("#78909C") else android.graphics.Color.parseColor("#9E9E9E"))
            hint = "留空使用系统默认，支持粘贴自定义UA"
        }

        layout.addView(tvInfo)
        layout.addView(edt)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("User-Agent 设备设置")
            .setView(layout)
            .setNeutralButton("随机生成", null)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            val btnPos = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            val btnNeu = dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)
            val btnNeg = dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)

            btnPos?.setTextColor(positiveButtonColor)
            btnNeu?.setTextColor(neutralButtonColor)
            btnNeg?.setTextColor(negativeButtonColor)

            btnNeu?.setOnClickListener {
                val newUa = getRandomRealDeviceUa()
                edt.setText(newUa)
                val newProfile = parseCustomUa(newUa)
                val newDisplayName = newProfile.marketingName?.takeIf { it.isNotBlank() } ?: newProfile.model
                tvInfo.text = "【已重新随机生成设备】\n" +
                        "• 识别机型: $newDisplayName (${newProfile.brandModel})\n" +
                        "• 系统版本: Android ${newProfile.release} (Build/${newProfile.buildId})\n" +
                        "• 浏览器内核: Chrome ${newProfile.chromeVer} / UC ${newProfile.ucVer}\n" +
                        "• 屏幕分辨率: ${newProfile.screenWs}\n\n" +
                        "提示: 尚未保存！请点击下方“保存”按钮方可真正生效使用。"
                fansirsqi.xposed.sesame.hook.Toast.show("已生成新机型UA，点击“保存”生效")
            }

            btnPos?.setOnClickListener {
                val inputUa = edt.text?.toString()?.trim().orEmpty()
                val (canChange, reason) = checkUaChangeCooldown(inputUa)
                if (!canChange) {
                    fansirsqi.xposed.sesame.hook.Toast.show(reason)
                    return@setOnClickListener
                }
                ElemeTask.getElemeCustomUa().value = inputUa
                recordUaChanged()
                runCatching {
                    fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
                }
                fansirsqi.xposed.sesame.hook.Toast.show("设备 User-Agent 已保存生效！")
                dialog.dismiss()
            }
        }

        runCatching { dialog.show() }
    }

    /**
     * 弹窗展示当前饿了么兑换商城的商品列表、所需星数与补货状态
     * 全面防御 Activity 生命周期销毁、BadTokenException、窗口泄露与跨线程闪退
     */
    fun showMallListDialog(context: android.content.Context? = null) {
        if (context == null) return
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        // 安全展示现代化加载对话框（增加 try-catch 容灾，支持用户返回取消，避免死锁）
        val isNight = isDarkThemeMode(context)
        val primaryTextColor = if (isNight) android.graphics.Color.parseColor("#E0E0E0") else android.graphics.Color.parseColor("#212121")
        val positiveButtonColor = if (isNight) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#2E7D32")
        val neutralButtonColor = if (isNight) android.graphics.Color.parseColor("#64B5F6") else android.graphics.Color.parseColor("#1976D2")

        var progressDialog: android.app.Dialog? = null
        runCatching {
            val loadingView = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(60, 50, 60, 50)
                gravity = android.view.Gravity.CENTER_VERTICAL
                val progressBar = android.widget.ProgressBar(context).apply {
                    val pLp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { rightMargin = 40 }
                    layoutParams = pLp
                }
                val textView = android.widget.TextView(context).apply {
                    text = "正在拉取饿了么兑换商城数据..."
                    textSize = 15f
                    setTextColor(primaryTextColor)
                }
                addView(progressBar)
                addView(textView)
            }
            progressDialog = androidx.appcompat.app.AlertDialog.Builder(context)
                .setView(loadingView)
                .setCancelable(true)
                .create().apply {
                    setCanceledOnTouchOutside(false)
                    show()
                }
        }.onFailure {
            fansirsqi.xposed.sesame.hook.Toast.show("正在拉取商城数据，请稍候...")
        }

        Thread {
            try {
                val initCookie = ElemeAuth.ensureCookie()
                if (initCookie.isEmpty()) {
                    mainHandler.post {
                        runCatching { progressDialog?.dismiss() }
                        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return@post
                        runCatching {
                            androidx.appcompat.app.AlertDialog.Builder(context)
                                .setTitle("兑换商城")
                                .setMessage("未获取到饿了么有效登录凭据，请确保已绑定账号或在支付宝打开饿了么完成授权。")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                    return@Thread
                }
                val cookie = ElemeAuth.validateOrRefreshCookie(initCookie) ?: initCookie
                val mallResp = LuckyStarRpcCall.queryMallList(cookie)
                val propResp = LuckyStarRpcCall.queryProperty(cookie)

                // 提取星星数
                val data = propResp?.optJSONObject("data") ?: propResp
                val prop = data?.optJSONObject("property") ?: data?.optJSONObject("data")?.optJSONObject("property")
                val starAmount = prop?.optJSONObject("data")?.optJSONObject("STAR")?.optInt("amount", 0)
                    ?: prop?.optInt("amount", 0) ?: 0

                // 提取商城商品
                val mallData = mallResp?.optJSONObject("data") ?: mallResp
                if (mallResp != null) {
                    fansirsqi.xposed.sesame.entity.ElemeMallEntity.updateFromMallResponse(mallResp)
                }
                val mallItems = fansirsqi.xposed.sesame.entity.ElemeMallEntity.getList().filter { it.id != "0" }

                val sb = StringBuilder()
                sb.append("当前幸运星余额: ").append(starAmount).append(" 星\n\n")
                if (mallItems.isNotEmpty()) {
                    sb.append("【商城商品列表】:\n")
                    for ((idx, item) in mallItems.withIndex()) {
                        val title = if (item.pureTitle.isNotEmpty()) item.pureTitle else item.name
                        val statusText = when {
                            item.gapAmount > 0 -> "差 ${item.gapAmount} 星"
                            item.exchangeStatus == "PROPERTY_NOT_ENOUGH" -> "星数不足"
                            item.exchangeStatus == "SOLD_OUT" -> "今日已兑完"
                            item.exchangeStatus in listOf("", "INIT", "EXCHANGE_ABLE", "CAN_EXCHANGE", "ONLINE") -> "可兑换"
                            else -> item.exchangeStatus
                        }
                        sb.append("${idx + 1}. ").append(title)
                            .append(" (").append(item.consumeAmount).append("星) -> ").append(statusText).append("\n")
                        sb.append("   • 秒杀/补货: ").append(item.timeDesc.ifEmpty { "每日按时补货 / 随时可兑" }).append("\n")
                        if (item.ruleDesc.isNotEmpty()) {
                            sb.append("   • 规则: ").append(item.ruleDesc).append("\n")
                        }
                    }
                    sb.append("\n提示: 您可在【目标兑换商品】中勾选需要自动兑换的商品，开启【自动兑换商城商品】后达到门槛将自动为您兑换！")
                } else {
                    sb.append("暂未查询到商城商品，请稍后重试。")
                }

                mainHandler.post {
                    runCatching { progressDialog?.dismiss() }
                    if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return@post
                    runCatching {
                        val scrollView = android.widget.ScrollView(context).apply {
                            isFillViewport = true
                        }
                        val container = android.widget.LinearLayout(context).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(40, 24, 40, 24)
                        }
                        scrollView.addView(container)

                        // 顶部幸运星资产与操作提示
                        val headerTv = android.widget.TextView(context).apply {
                            text = "当前幸运星余额: $starAmount 星\n\n提示: 点击任意商品可查看秒杀兑换时间表、使用规则与完整详情"
                            textSize = 13.5f
                            setTextColor(if (isNight) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#2E7D32"))
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            setPadding(0, 0, 0, 20)
                        }
                        container.addView(headerTv)

                        if (mallItems.isEmpty()) {
                            val emptyTv = android.widget.TextView(context).apply {
                                text = "暂未查询到商城商品，请稍后重试。"
                                textSize = 13.5f
                                setTextColor(primaryTextColor)
                                setPadding(0, 20, 0, 20)
                            }
                            container.addView(emptyTv)
                        } else {
                            val secondaryColor = if (isNight) android.graphics.Color.parseColor("#B0BEC5") else android.graphics.Color.parseColor("#666666")
                            val accentColor = if (isNight) android.graphics.Color.parseColor("#64B5F6") else android.graphics.Color.parseColor("#1976D2")
                            val dividerColor = if (isNight) android.graphics.Color.parseColor("#37474F") else android.graphics.Color.parseColor("#E0E0E0")

                            for ((idx, item) in mallItems.withIndex()) {
                                val itemLayout = android.widget.LinearLayout(context).apply {
                                    orientation = android.widget.LinearLayout.VERTICAL
                                    setPadding(20, 18, 20, 18)
                                    isClickable = true
                                    isFocusable = true
                                    val outValue = android.util.TypedValue()
                                    if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                                        setBackgroundResource(outValue.resourceId)
                                    }
                                }

                                val title = if (item.pureTitle.isNotEmpty()) item.pureTitle else item.name
                                val statusText = when {
                                    item.gapAmount > 0 -> "差 ${item.gapAmount} 星"
                                    item.exchangeStatus == "PROPERTY_NOT_ENOUGH" -> "星数不足"
                                    item.exchangeStatus == "SOLD_OUT" -> "今日已抢光"
                                    item.exchangeStatus in listOf("", "INIT", "EXCHANGE_ABLE", "CAN_EXCHANGE", "ONLINE") -> "可兑换"
                                    else -> item.exchangeStatus
                                }

                                val tvTitle = android.widget.TextView(context).apply {
                                    text = "${idx + 1}. $title (${item.consumeAmount}星) ➔ $statusText"
                                    textSize = 14f
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                    setTextColor(primaryTextColor)
                                }

                                val tvTime = android.widget.TextView(context).apply {
                                    val time = item.timeDesc.ifEmpty { "每日按时补货 / 随时可兑" }
                                    text = "⏰ 秒杀/补货: $time"
                                    textSize = 12f
                                    setTextColor(secondaryColor)
                                    setPadding(0, 4, 0, 2)
                                }

                                val tvHint = android.widget.TextView(context).apply {
                                    text = "查看完整详情与门槛规则 ➔"
                                    textSize = 11.5f
                                    setTextColor(accentColor)
                                    setPadding(0, 2, 0, 0)
                                }

                                itemLayout.addView(tvTitle)
                                itemLayout.addView(tvTime)
                                itemLayout.addView(tvHint)

                                itemLayout.setOnClickListener {
                                    showMallItemDetailDialog(context, item, isNight)
                                }

                                container.addView(itemLayout)

                                val divider = android.view.View(context).apply {
                                    layoutParams = android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                                    ).apply { setMargins(10, 4, 10, 4) }
                                    setBackgroundColor(dividerColor)
                                }
                                container.addView(divider)
                            }

                            val tipTv = android.widget.TextView(context).apply {
                                text = "\n提示: 您可在【目标兑换商品】中勾选需要自动兑换的商品，达到门槛后将自动为您兑换！"
                                textSize = 12f
                                setTextColor(secondaryColor)
                                setPadding(0, 10, 0, 10)
                            }
                            container.addView(tipTv)
                        }

                        val d = androidx.appcompat.app.AlertDialog.Builder(context)
                            .setTitle("饿了么兑换商城")
                            .setView(scrollView)
                            .setPositiveButton("我知道了", null)
                            .setNeutralButton("复制全部") { _, _ ->
                                runCatching {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    if (cm != null) {
                                        val clipData = android.content.ClipData.newPlainText("ElemeMallList", sb.toString())
                                        cm.setPrimaryClip(clipData)
                                        fansirsqi.xposed.sesame.hook.Toast.show("已复制全部内容到剪贴板！")
                                    }
                                }
                            }
                            .create()

                        d.setOnShowListener {
                            d.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(positiveButtonColor)
                            d.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setTextColor(neutralButtonColor)
                        }
                        d.show()
                    }
                }
            } catch (e: Throwable) {
                mainHandler.post {
                    runCatching { progressDialog?.dismiss() }
                    if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return@post
                    runCatching {
                        androidx.appcompat.app.AlertDialog.Builder(context)
                            .setTitle("兑换商城")
                            .setMessage("查询失败: ${e.message}")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }
        }.start()
    }

    /**
     * 单个商品的完整详情弹窗（展示秒杀兑换时间表、使用规则、门槛与库存）
     */
    private fun showMallItemDetailDialog(
        context: android.content.Context,
        item: fansirsqi.xposed.sesame.entity.ElemeMallEntity,
        isNight: Boolean
    ) {
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return

        val primaryTextColor = if (isNight) android.graphics.Color.parseColor("#E0E0E0") else android.graphics.Color.parseColor("#212121")
        val positiveButtonColor = if (isNight) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#2E7D32")
        val neutralButtonColor = if (isNight) android.graphics.Color.parseColor("#64B5F6") else android.graphics.Color.parseColor("#1976D2")

        val detailText = item.fullDetailText
        val scrollView = android.widget.ScrollView(context)
        val tv = android.widget.TextView(context).apply {
            text = detailText
            textSize = 13.5f
            setTextColor(primaryTextColor)
            setPadding(50, 30, 50, 20)
            setLineSpacing(6f, 1f)
            setTextIsSelectable(true)
        }
        scrollView.addView(tv)

        val displayTitle = if (item.pureTitle.isNotEmpty()) item.pureTitle else item.name
        val detailDialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(displayTitle)
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制详情") { _, _ ->
                runCatching {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    if (cm != null) {
                        val clipData = android.content.ClipData.newPlainText("ElemeItemDetail", detailText)
                        cm.setPrimaryClip(clipData)
                        fansirsqi.xposed.sesame.hook.Toast.show("商品详情已复制到剪贴板！")
                    }
                }
            }
            .create()

        detailDialog.setOnShowListener {
            detailDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(positiveButtonColor)
            detailDialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setTextColor(neutralButtonColor)
        }

        runCatching { detailDialog.show() }
    }

    /**
     * 获取当前生效的设备指纹配置
     * 默认使用系统原版真实指纹；若用户设置了自定义 UA 则使用自定义 UA
     */
    fun getActiveProfile(): DeviceProfile {
        sessionProfile?.let { return it }

        val alipayVer = getSafeAlipayVersion("12.12.30.8000")
        val customUa = runCatching {
            ElemeTask.getElemeCustomUa().value.orEmpty().trim()
        }.getOrDefault("")

        val profile = if (customUa.isNotEmpty()) {
            parseCustomUa(customUa, alipayVer)
        } else {
            getDefaultNativeProfile()
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
                val isExpectedBizStatus = ret.contains("FAIL_BIZ_CHANCE_DAY_NO_ENOUGH_REMAIN") ||
                        ret.contains("ACT_LOTTERY_NOT_WINNING") ||
                        ret.contains("REPEAT") || ret.contains("ALREADY") ||
                        errorCode.contains("CHANCE") || errorCode.contains("NOT_WINNING")
                if (!isExpectedBizStatus) {
                    Log.other("饿了么", "❌ [$api] 响应失败: $readableErr\n完整响应:\n$resp")
                }
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
