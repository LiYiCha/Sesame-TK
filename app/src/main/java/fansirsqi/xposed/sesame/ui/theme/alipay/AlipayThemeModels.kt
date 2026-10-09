package fansirsqi.xposed.sesame.ui.theme.alipay

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 主题包列表项：一个磁盘目录 = 一个可应用的支付宝主题包
 */
data class AlipayThemePack(
    val packId: String,
    val name: String,
    val subtitle: String,
    val skinId: String,
    val coverPath: String?,
    val storagePath: String,
    val isSelected: Boolean,
    val slotTotal: Int,
    val slotDarkVariant: Int,
    val slotMissing: Int
)

/**
 * 主题状态
 */
data class AlipayThemeState(
    val availablePacks: List<AlipayThemePack> = emptyList(),
    val selectedPackId: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

/**
 * meta.json 的数据绑定：字段名与支付宝皮肤协议一致，键含 `#` 故必须显式 @JsonProperty
 */
data class AlipayThemeResource(
    val position: String,
    val type: String,
    val color: String? = null,
    val image: String? = null,
    val lottie: String? = null,
    val lottieVideo: String? = null,
    val description: String? = null,
    val metaList: List<AlipayThemeResourceMeta>? = null,
    @JsonProperty("dark#color")
    val darkColor: String? = null,
    @JsonProperty("dark#image")
    val darkImage: String? = null
)

data class AlipayThemeResourceMeta(
    val image: String? = null,
    val aspectRatio: Int? = null,
    @JsonProperty("dark#image")
    val darkImage: String? = null
)

/**
 * meta.json 根结构。hook 侧 ThemeHookV2 也读这个模型，字段勿随意增删。
 */
data class AlipayThemeMetadata(
    val skinId: String = "",
    val description: String = "",
    val resource: List<AlipayThemeResource> = emptyList()
)

/**
 * ltp/meta.json：付款码皮肤有自己的一套 resource，渐变端点不在主协议内
 */
data class LtpMeta(
    val skinId: String = "",
    val description: String = "",
    val resource: List<LtpResource> = emptyList()
)

data class LtpResource(
    val position: String = "",
    val description: String? = null,
    val gradient: LtpGradient? = null
)

data class LtpGradient(
    val start: String? = null,
    val end: String? = null
)
