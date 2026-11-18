package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Files
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

    // 会话级缓存：最近一次网络拉取到的幸运星数量（打开商城弹窗不请求网络，仅展示该值；null 表示尚未拉取过）
    @Volatile
    private var lastKnownStarAmount: Int? = null

    // 单次会话级指纹缓存：确保单次任务执行生命周期内所有接口使用完全相同的真实设备指纹
    @Volatile
    private var sessionProfile: DeviceProfile? = null
    private var sessionProfileUa: String? = null

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
                "Mozilla/5.0 (Linux; Android $release; $deviceToken Build/$buildId; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/$chromeVer UCBrowser/$ucVer Mobile Safari/537.36 AlipayDefined(nt:wifi,ws:$screenWs) AliApp(AP/$alipayVer) AlipayClient/$alipayVer Language/zh-Hans useStatusBar/true isConcaveScreen/false Region/CN Channel/${brand.lowercase()} GCanvas/1.9.38 Nebula SDK/1.8.1000 Nebula Ariver/1.0.0"
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

    private fun generateProfileFromTemplate(tpl: DeviceTemplate): DeviceProfile {
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

    private fun generateDynamicProfile(mode: Int = 2): DeviceProfile {
        return generateProfileFromTemplate(generateDynamicTemplate(mode))
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

        // 9. 在真实机型库中反查 marketingName（让 UI 显示 "Redmi K70 Pro" 而不是型号代码）
        val knownMarketingName = ALL_REAL_MODELS.firstOrNull {
            it.model.equals(model, ignoreCase = true) || it.brandModel.endsWith(model, ignoreCase = true)
        }?.marketingName

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
            marketingName = knownMarketingName ?: "自定义UA ($model)"
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
        sessionProfileUa = null
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

    // UA 变更历史去重缓存：记录上次已持久化的键与 UA 原文，避免每次读取配置都重复写盘
    @Volatile
    private var lastRecordedUaKey: String? = null
    @Volatile
    private var lastRecordedUaValue: String? = null
    private const val UA_HISTORY_MAX = 20 // 历史记录最大保留条数
    // UA 属设备级设置，历史记录全局共享（不按支付宝账号隔离），避免切换账号后找不到之前保存的 UA
    private const val UA_HISTORY_KEY = "Eleme_UA_History_Global"

    /**
     * 记录 UA 变更历史（按账号持久化到 DataStore，JSON 数组，最新在前，去重且上限 20 条）
     * 记录内容包括 UA 原文、记录时间与机型展示名（「营销名-型号代码」格式），供 Web 设置页查看与一键回填
     */
    @JvmStatic
    fun recordUaHistory(rawUa: String?) {
        runCatching {
            val ua = rawUa?.trim().orEmpty()
            val key = UA_HISTORY_KEY
            // 会话级去重：同一账号同一 UA 不重复记录
            if (lastRecordedUaKey == key && lastRecordedUaValue == ua) {
                return
            }
            val existing = DataStore.get(key, String::class.java).orEmpty().ifEmpty { "[]" }
            val arr = org.json.JSONArray(existing)
            // 磁盘级去重：最新一条与本次相同则跳过（覆盖跨进程重复保存的场景）
            if (arr.length() > 0 && arr.optJSONObject(0)?.optString("ua") == ua) {
                lastRecordedUaKey = key
                lastRecordedUaValue = ua
                return
            }
            // 机型展示名：空 UA 视为回到系统默认，非空 UA 用「营销名-型号代码」格式
            val modelDisplay = if (ua.isEmpty()) {
                "系统原版默认"
            } else {
                val p = parseCustomUa(ua)
                val name = p.marketingName?.takeIf { it.isNotBlank() }
                if (name != null && !name.contains(p.model)) "$name-${p.model}" else p.model
            }
            val entry = JSONObject()
            entry.put("ua", ua)
            entry.put("time", System.currentTimeMillis())
            entry.put("model", modelDisplay)
            val newArr = org.json.JSONArray()
            newArr.put(entry)
            for (i in 0 until arr.length()) {
                val old = arr.optJSONObject(i) ?: continue
                if (old.optString("ua") == ua) continue
                newArr.put(old)
                if (newArr.length() >= UA_HISTORY_MAX) break
            }
            DataStore.put(key, newArr.toString())
            lastRecordedUaKey = key
            lastRecordedUaValue = ua
        }
    }

    /**
     * 获取 UA 变更历史（JSON 数组字符串，最新在前；元素含 ua/time/model 字段，供 Web 设置页展示与回填）
     */
    @JvmStatic
    fun getUaHistory(): String {
        return runCatching {
            val global = DataStore.get(UA_HISTORY_KEY, String::class.java).orEmpty()
            val legacy = DataStore.get(ElemeAuth.getUidKey("Eleme_UA_History"), String::class.java).orEmpty()
            // 兼容读取旧版本按账号存储的历史：新键为空时才回退旧键，保证切换账号后仍能找回之前保存的 UA
            global.ifEmpty { legacy }.ifEmpty { "[]" }
        }.getOrDefault("[]")
    }

    /**
     * 清空 UA 变更历史
     */
    @JvmStatic
    fun clearUaHistory() {
        runCatching {
            DataStore.put(UA_HISTORY_KEY, "[]")
            lastRecordedUaKey = null
            lastRecordedUaValue = null
        }
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
                // 机型展示格式统一为「营销名-型号代码」（如 Redmi K70E-2311FRAFDC），代码变化即指纹已更换
                val displayModel = run {
                    val name = profile.marketingName?.takeIf { it.isNotBlank() }
                    if (name != null && !name.contains(profile.model)) "$name-${profile.model}" else profile.model
                }
                put("model", displayModel)
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
     * 获取机型库全部可选机型名称（JSON 数组字符串，供 Web 设置页下拉框使用）
     */
    @JvmStatic
    fun getDeviceModelList(): String {
        return runCatching {
            val arr = org.json.JSONArray()
            for (tpl in ALL_REAL_MODELS) {
                arr.put(tpl.marketingName)
            }
            arr.toString()
        }.getOrDefault("[]")
    }

    /**
     * 按指定机型生成一份新的真实 UA（仅返回字符串，不落库不生效；是否保存由用户在编辑弹窗点击保存决定）
     * @param marketingName 机型库中的机型名称；传空或未匹配到则从全部机型中随机选取
     */
    @JvmStatic
    @JvmOverloads
    fun generateUaByModel(marketingName: String? = null): String {
        return runCatching {
            val name = marketingName?.trim().orEmpty()
            val tpl = if (name.isEmpty()) {
                generateDynamicTemplate(2)
            } else {
                ALL_REAL_MODELS.firstOrNull { it.marketingName.equals(name, ignoreCase = true) }
                    ?: generateDynamicTemplate(2)
            }
            generateProfileFromTemplate(tpl).httpUa()
        }.getOrDefault("")
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
     * 原生机型查看弹窗（精简版）：仅显示当前生效机型，UA 的编辑与按机型生成统一在 Web 设置页完成
     */
    @JvmStatic
    @JvmOverloads
    fun showActiveProfileDialog(context: android.content.Context? = null) {
        if (context == null) return
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return

        val profile = getActiveProfile()
        // 机型展示格式统一为「营销名-型号代码」（如 Redmi K70E-2311FRAFDC）
        val displayName = run {
            val name = profile.marketingName?.takeIf { it.isNotBlank() }
            if (name != null && !name.contains(profile.model)) "$name-${profile.model}" else profile.model
        }
        val source = if (ElemeTask.getElemeCustomUa().value.orEmpty().trim().isEmpty()) "系统默认" else "自定义 UA"
        val detailMsg = "当前机型：$displayName\nUA 来源：$source"

        val isNight = isDarkThemeMode(context)
        val primaryTextColor = if (isNight) android.graphics.Color.parseColor("#E0E0E0") else android.graphics.Color.parseColor("#212121")
        val positiveButtonColor = if (isNight) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#2E7D32")

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("设备机型")
            .setMessage(detailMsg)
            .setPositiveButton("确定", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(positiveButtonColor)
        }
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(primaryTextColor)

        runCatching { dialog.show() }
    }

    /**
     * 从磁盘直读当前账号 status.json 的 flagList（设置进程与钩子进程内存隔离，
     * 直接读文件才能拿到钩子进程写入的当日任务完成标记）
     */
    private fun readTodayFlags(): Set<String> {
        return runCatching {
            val f = Files.getStatusFile(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid) ?: return emptySet()
            val json = Files.readFromFile(f)
            if (json.isBlank()) return emptySet()
            val arr = JSONObject(json).optJSONArray("flagList") ?: return emptySet()
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val v = arr.optString(i)
                if (v.isNotEmpty()) set.add(v)
            }
            set
        }.getOrDefault(emptySet())
    }

    /**
     * 原生账号状态弹窗：展示支付宝/饿了么账号信息、CK 凭据有效期与来源、当前设备指纹、
     * 风控名单数量与今日任务完成状态；底部提供「校验并刷新CK」手动触发会话校验与 72h 换票自愈
     */
    @JvmStatic
    @JvmOverloads
    fun showAccountStatusDialog(context: android.content.Context? = null) {
        if (context == null) return
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return

        val isNight = isDarkThemeMode(context)
        val primaryTextColor = if (isNight) android.graphics.Color.parseColor("#E0E0E0") else android.graphics.Color.parseColor("#212121")
        val positiveButtonColor = if (isNight) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#2E7D32")
        val neutralButtonColor = if (isNight) android.graphics.Color.parseColor("#64B5F6") else android.graphics.Color.parseColor("#1976D2")

        // CK 凭据状态描述
        val cookieStatus = ElemeAuth.getCookieStatus()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val ckText = when {
            !cookieStatus.exists -> "不存在（尚未绑定或从未刷新过）"
            cookieStatus.valid -> {
                val remainHours = Math.max(1L, (cookieStatus.expiresAt - System.currentTimeMillis()) / 3600000L)
                "有效（至 ${fmt.format(java.util.Date(cookieStatus.expiresAt))}，剩余约 ${remainHours} 小时）"
            }
            else -> "已过期或临期（将在下次任务执行时自动刷新）"
        }

        // 当前设备指纹（「营销名-型号代码」格式）
        val profile = getActiveProfile()
        val displayName = run {
            val name = profile.marketingName?.takeIf { it.isNotBlank() }
            if (name != null && !name.contains(profile.model)) "$name-${profile.model}" else profile.model
        }
        val uaSource = if (ElemeTask.getElemeCustomUa().value.orEmpty().trim().isEmpty()) "系统默认" else "自定义 UA"

        // 风控名单数量
        val riskCount = runCatching { ElemeTask.getElemeRiskTasks().value.size }.getOrDefault(0)

        // 今日任务完成标记（直读磁盘，保证跨进程准确）
        val flags = readTodayFlags()
        fun flagDone(flag: String) = if (flags.contains(flag)) "已完成" else "未完成"
        val todayText = "• 幸运星全流程: ${flagDone(FreeOrderTask.FLAG_FLOW_DONE)}\n" +
                "• 抽免单任务: ${flagDone(FreeOrderTask.FLAG_FREEORDER_DONE)}\n" +
                "• 下单免单抽奖: ${if (flags.contains(FreeOrderTask.FLAG_ORDER_FREE)) "已抽" else "未抽"}\n" +
                "• 抽免单签到: ${flagDone(FreeOrderTask.FLAG_SIGNIN)}\n" +
                "• 外卖爆红包: ${if (flags.contains("Eleme_BurstRed_Done")) "已领取" else "未领取"}"

        // 支付宝账号仅显示脱敏 UID，不展示昵称/姓名
        val alipayUidRaw = fansirsqi.xposed.sesame.util.maps.UserMap.currentUid.orEmpty()
        val alipayUid = if (alipayUidRaw.length > 8) alipayUidRaw.take(4) + "***" + alipayUidRaw.takeLast(4) else alipayUidRaw.ifEmpty { "未知" }
        val elemeInfoLine = ElemeAuth.getElemeAccountLine()
        val detailMsg = "【账号信息】\n" +
                "• $elemeInfoLine\n" +
                "• 支付宝: $alipayUid\n\n" +
                "【CK 凭据状态】\n" +
                "• 状态: $ckText\n" +
                "• 来源: ${cookieStatus.source}\n\n" +
                "【设备指纹】\n" +
                "• 当前机型: $displayName\n" +
                "• UA 来源: $uaSource\n\n" +
                "【风控与今日状态】\n" +
                "• 风控名单: $riskCount 个任务\n" +
                todayText

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("账号状态")
            .setMessage(detailMsg)
            .setPositiveButton("关闭", null)
            .setNeutralButton("校验并刷新CK") { _, _ ->
                Thread {
                    val result = runCatching {
                        val ck = ElemeAuth.peekCookie()
                        ElemeAuth.validateOrRefreshCookie(ck) != null
                    }.getOrDefault(false)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        fansirsqi.xposed.sesame.hook.Toast.show(
                            if (result) "CK 校验通过或已自动刷新自愈" else "CK 校验失败，请在支付宝打开饿了么小程序重新授权"
                        )
                    }
                }.start()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(positiveButtonColor)
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setTextColor(neutralButtonColor)
            // 账号状态文本（含 CK 信息）允许长按选中复制
            dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextIsSelectable(true)
        }
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(primaryTextColor)

        runCatching { dialog.show() }
    }

    /**
     * 弹窗展示饿了么幸运星兑换商城商品列表、所需星数与补货状态
     * 打开弹窗仅渲染本地缓存数据，绝不主动发起网络请求；仅当用户点击「拉取最新」按钮时才请求网络
     * 全面防御 Activity 生命周期销毁、BadTokenException、窗口泄露与跨线程闪退
     * @param fetchFirst true 表示由用户手动触发网络拉取（展示加载框并在成功后刷新弹窗）
     */
    fun showMallListDialog(context: android.content.Context? = null, fetchFirst: Boolean = false) {
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

        // 本地缓存优先：直接渲染本地缓存商品列表，绝不主动请求网络。
        // 星数取最近一次拉取的持久化快照（每日任务/手动刷新写入），刷新时间一并展示。
        if (!fetchFirst) {
            val cachedItems = fansirsqi.xposed.sesame.entity.ElemeMallEntity.getList().filter { it.id != "0" }
            val cachedStars = fansirsqi.xposed.sesame.entity.ElemeMallEntity.getLastStarAmount()
            val starDisplay = if (cachedStars >= 0) "$cachedStars" else "未获取（点「拉取最新」获取）"
            val refreshMs = fansirsqi.xposed.sesame.entity.ElemeMallEntity.getLastRefreshTime()
            val refreshDisplay = if (refreshMs > 0) {
                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(refreshMs))
            } else {
                "从未刷新"
            }
            showMallContentDialog(context, isNight, starDisplay, cachedItems, refreshDisplay)
            return
        }

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

                // 提取星星数并记入会话级缓存（供不请求网络的缓存弹窗展示）
                val data = propResp?.optJSONObject("data") ?: propResp
                val prop = data?.optJSONObject("property") ?: data?.optJSONObject("data")?.optJSONObject("property")
                val starAmount = prop?.optJSONObject("data")?.optJSONObject("STAR")?.optInt("amount", 0)
                    ?: prop?.optInt("amount", 0) ?: 0
                lastKnownStarAmount = starAmount

                // 提取商城商品并更新本地持久化缓存
                if (mallResp != null) {
                    fansirsqi.xposed.sesame.entity.ElemeMallEntity.updateFromMallResponse(mallResp)
                    // 手动刷新也记录快照，刷新时间与星数同步落盘
                    fansirsqi.xposed.sesame.entity.ElemeMallEntity.recordFetchSnapshot(
                        System.currentTimeMillis(), starAmount
                    )
                }
                val mallItems = fansirsqi.xposed.sesame.entity.ElemeMallEntity.getList().filter { it.id != "0" }

                mainHandler.post {
                    runCatching { progressDialog?.dismiss() }
                    if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return@post
                    runCatching {
                        val nowDisplay = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                        showMallContentDialog(context, isNight, "$starAmount", mallItems, nowDisplay)
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
     * 构建商城商品列表滚动视图（顶部资产汇总条 + 商品卡片 + 底部提示）
     * @param starDisplay 顶部汇总条展示的幸运星文本（缓存模式下可能为「未获取」占位提示）
     */
    private fun buildMallScrollView(
        context: android.content.Context,
        isNight: Boolean,
        starDisplay: String,
        mallItems: List<fansirsqi.xposed.sesame.entity.ElemeMallEntity>,
        refreshDisplay: String = ""
    ): android.widget.ScrollView {
        val primaryTextColor = if (isNight) android.graphics.Color.parseColor("#E0E0E0") else android.graphics.Color.parseColor("#212121")
        val secondaryColor = if (isNight) android.graphics.Color.parseColor("#B0BEC5") else android.graphics.Color.parseColor("#666666")
        // 星数是否已知：差额「差 N 星」由服务端按当时星数算出，星数未获取时该差额已过期，不得展示以免自相矛盾
        val starKnown = starDisplay.toIntOrNull() != null
        val accentColor = if (isNight) android.graphics.Color.parseColor("#64B5F6") else android.graphics.Color.parseColor("#1976D2")
        val okColor = if (isNight) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#34A853")
        val lackColor = if (isNight) android.graphics.Color.parseColor("#FFB74D") else android.graphics.Color.parseColor("#FF976A")
        val barBgColor = if (isNight) android.graphics.Color.parseColor("#232427") else android.graphics.Color.parseColor("#F4F6F8")
        val cardBorderColor = if (isNight) android.graphics.Color.parseColor("#383A3E") else android.graphics.Color.parseColor("#EBEDF0")

        val scrollView = android.widget.ScrollView(context).apply {
            isFillViewport = true
        }
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        scrollView.addView(container)

        // 顶部资产汇总条（圆角底色：当前幸运星 + 商品数 + 已选自动兑换数）
        val summaryBar = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(28, 18, 28, 18)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(barBgColor)
                cornerRadius = 16f
            }
        }
        // 汇总条用单个 TextView + Spannable 一行显示，避免多个子 View 在窄屏被挤成竖排
        val selectedCount = mallItems.count { item ->
            (runCatching { ElemeTask.getElemeExchangeItems().value }.getOrNull() ?: emptySet<String>()).contains(item.id)
        }
        val summaryText = android.text.SpannableStringBuilder().apply {
            fun appendSeg(label: String, value: String, valueColor: Int) {
                append(label)
                val s = length
                append(value)
                setSpan(android.text.style.ForegroundColorSpan(valueColor), s, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            appendSeg("幸运星 ", starDisplay, accentColor)
            appendSeg("  ·  商品 ", "${mallItems.size}", accentColor)
            appendSeg("  ·  已选 ", "$selectedCount", okColor)
            if (refreshDisplay.isNotEmpty()) {
                appendSeg("  ·  刷新 ", refreshDisplay, secondaryColor)
            }
        }
        summaryBar.addView(android.widget.TextView(context).apply {
            text = summaryText
            textSize = 13f
            setTextColor(secondaryColor)
            setSingleLine(true)
        })
        container.addView(summaryBar)

        if (mallItems.isEmpty()) {
            val emptyTv = android.widget.TextView(context).apply {
                text = "\n本地暂无商城商品缓存，请点击下方「拉取最新」手动获取实时数据（弹窗打开绝不自动请求网络）。"
                textSize = 13.5f
                setTextColor(secondaryColor)
                setPadding(0, 24, 0, 24)
            }
            container.addView(emptyTv)
        } else {
            for (item in mallItems) {
                val title = if (item.pureTitle.isNotEmpty()) item.pureTitle else item.name
                val statusPair = when {
                    // 仅当星数已知时才展示差额，否则该差额是上次拉取的过期快照，与顶部「未获取」矛盾
                    starKnown && item.gapAmount > 0 -> "差 ${item.gapAmount} 星" to lackColor
                    starKnown && item.exchangeStatus == "PROPERTY_NOT_ENOUGH" -> "星数不足" to lackColor
                    item.exchangeStatus == "SOLD_OUT" -> "已抢光" to secondaryColor
                    item.exchangeStatus in listOf("", "INIT", "EXCHANGE_ABLE", "CAN_EXCHANGE", "ONLINE") -> "可兑换" to okColor
                    else -> item.exchangeStatus to secondaryColor
                }

                // 商品卡片：圆角边框容器 + 三段着色富文本标题（名称/星数/状态）
                val card = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(24, 20, 24, 20)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.TRANSPARENT)
                        setStroke(2, cardBorderColor)
                        cornerRadius = 18f
                    }
                    isClickable = true
                    isFocusable = true
                }

                val tvTitle = android.widget.TextView(context).apply {
                    val span = android.text.SpannableStringBuilder()
                    var s0 = span.length; span.append(title)
                    span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s0, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s0 = span.length; span.append("  [${item.consumeAmount}星]")
                    span.setSpan(android.text.style.ForegroundColorSpan(accentColor), s0, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s0 = span.length; span.append("  (${statusPair.first})")
                    span.setSpan(android.text.style.ForegroundColorSpan(statusPair.second), s0, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = span
                    textSize = 14f
                    setTextColor(primaryTextColor)
                    setLineSpacing(2f, 1f)
                }

                // 仅当服务端返回真实补货/秒杀时间才显示「补货: 时间」；无数据时不显示该行，避免无意义兜底文案
                card.addView(tvTitle)
                if (item.timeDesc.isNotEmpty()) {
                    val tvTime = android.widget.TextView(context).apply {
                        text = "补货: ${item.timeDesc}"
                        textSize = 12f
                        setTextColor(secondaryColor)
                        setPadding(0, 6, 0, 0)
                    }
                    card.addView(tvTime)
                }
                if (item.stockDesc.isNotEmpty()) {
                    val tvStock = android.widget.TextView(context).apply {
                        text = "库存: ${item.stockDesc}"
                        textSize = 11.5f
                        setTextColor(secondaryColor)
                        setPadding(0, 3, 0, 0)
                    }
                    card.addView(tvStock)
                }

                // 自动兑换勾选框：直接读写【目标兑换商品】勾选集合并实时落盘
                val cbExchange = android.widget.CheckBox(context).apply {
                    text = "自动兑换"
                    textSize = 12.5f
                    val selected = runCatching { ElemeTask.getElemeExchangeItems().value }.getOrNull() ?: emptySet<String>()
                    isChecked = selected.contains(item.id)
                    setTextColor(if (isChecked) okColor else secondaryColor)
                    setPadding(0, 6, 0, 0)
                    setOnCheckedChangeListener { _, checked ->
                        // 全链路容灾：任何异常（配置未加载、UID 为空、磁盘写入失败）都不得冒泡导致设置页闪退
                        runCatching {
                            val set = LinkedHashSet(
                                runCatching { ElemeTask.getElemeExchangeItems().value }.getOrNull() ?: emptySet<String>()
                            )
                            if (checked) set.add(item.id) else set.remove(item.id)
                            ElemeTask.getElemeExchangeItems().value = set
                            setTextColor(if (checked) okColor else secondaryColor)
                            // 配置落盘放在后台线程，避免在主线程做磁盘 IO 卡顿/ANR
                            Thread {
                                runCatching {
                                    fansirsqi.xposed.sesame.data.Config.save(
                                        fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true
                                    )
                                }.onFailure { t ->
                                    fansirsqi.xposed.sesame.util.Log.error("ElemeMall", "勾选兑换商品保存失败: ${t.message}")
                                }
                            }.start()
                            fansirsqi.xposed.sesame.hook.Toast.show(if (checked) "已勾选自动兑换: $title" else "已取消自动兑换: $title")
                        }.onFailure { t ->
                            fansirsqi.xposed.sesame.util.Log.error("ElemeMall", "勾选兑换商品异常: ${t.message}")
                        }
                    }
                }
                card.addView(cbExchange)

                val tvHint = android.widget.TextView(context).apply {
                    text = "点击卡片查看完整详情与门槛规则"
                    textSize = 11.5f
                    setTextColor(accentColor)
                    setPadding(0, 5, 0, 0)
                }
                card.addView(tvHint)

                card.setOnClickListener {
                    showMallItemDetailDialog(context, item, isNight)
                }

                container.addView(
                    card,
                    android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 12, 0, 0) }
                )
            }

            val tipTv = android.widget.TextView(context).apply {
                text = "\n提示: 在商品卡片内勾选「自动兑换」（实时保存），并开启【兑换商品】开关后，星数达标将自动为您兑换！"
                textSize = 12f
                setTextColor(secondaryColor)
                setPadding(0, 10, 0, 10)
            }
            container.addView(tipTv)
        }
        return scrollView
    }

    /**
     * 展示商城列表弹窗（底部按钮：关闭 / 拉取最新；「拉取最新」才发起网络请求）
     */
    private fun showMallContentDialog(
        context: android.content.Context,
        isNight: Boolean,
        starDisplay: String,
        mallItems: List<fansirsqi.xposed.sesame.entity.ElemeMallEntity>,
        refreshDisplay: String = ""
    ) {
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return
        val positiveButtonColor = if (isNight) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#2E7D32")
        val neutralButtonColor = if (isNight) android.graphics.Color.parseColor("#64B5F6") else android.graphics.Color.parseColor("#1976D2")

        runCatching {
            val scrollView = buildMallScrollView(context, isNight, starDisplay, mallItems, refreshDisplay)
            val d = androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("幸运星兑换商城")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                // 必须声明 Neutral 按钮，否则 Dialog 不会创建它，OnShowListener 里取到的为 null，刷新入口消失
                .setNeutralButton("拉取最新") { _, _ ->
                    runCatching { showMallListDialog(context, true) }
                }
                .create()
            d.setOnShowListener {
                d.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(positiveButtonColor)
                d.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setTextColor(neutralButtonColor)
            }
            d.show()
        }
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
     * 缓存键绑定自定义 UA 原文：Web 端保存新 UA 后无需重启即可立即生效
     */
    fun getActiveProfile(): DeviceProfile {
        val customUa = runCatching {
            ElemeTask.getElemeCustomUa().value.orEmpty().trim()
        }.getOrDefault("")

        // 记录实际生效 UA 的变更历史（内部含去重：仅在 UA 相对上次记录发生变化时写盘）
        recordUaHistory(customUa)

        sessionProfile?.let { if (sessionProfileUa == customUa) return it }

        val alipayVer = getSafeAlipayVersion("12.12.30.8000")
        val profile = if (customUa.isNotEmpty()) {
            parseCustomUa(customUa, alipayVer)
        } else {
            getDefaultNativeProfile()
        }

        sessionProfile = profile
        sessionProfileUa = customUa
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
                // 聚合 ret/errorCode/errorMsg 三处，判定是否为可预期的业务状态
                val allErr = "$ret $errorCode $errorMessage $readableErr"
                val isExpectedBizStatus = listOf(
                    "FAIL_BIZ_CHANCE_DAY_NO_ENOUGH_REMAIN", "ACT_LOTTERY_NOT_WINNING",
                    "REPEAT", "ALREADY", "CHANCE", "NOT_WINNING",
                    // 签到模式不匹配与接口下线：由 signIn 统一降级处理
                    "REWARD_MODE_NOT_SIGNIN_AND_RECEIVE", "API_NOT_FOUNDED", "NOT_FOUNDED",
                    // 阶段未达成：可重试的临时状态，不是错误
                    "STAGE_NOT_FINISH", "STAGE_COUNT_NOT_ENOUGH", "TASK_NOT_FINISH",
                    // 风控拦截：业务层已单独记入名单并打印，此处不重复刷屏
                    "405::行为受限", "417::"
                ).any { allErr.contains(it) }
                // 预期业务状态只写 error 日志（供排查），不写运行日志刷屏；真实错误则两处都记
                if (isExpectedBizStatus) {
                    Log.error("ElemeMtopUtil", err)
                } else {
                    Log.error("ElemeMtopUtil", err)
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
