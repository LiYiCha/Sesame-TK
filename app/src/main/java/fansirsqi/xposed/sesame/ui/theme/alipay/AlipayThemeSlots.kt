package fansirsqi.xposed.sesame.ui.theme.alipay

import java.io.File

enum class SlotKind { Color, Image, Video }

enum class SlotGroup { NavBar, TabBar, HomeAction, PaymentCode, Other }

/**
 * meta.json 里的一个渲染槽位；标签取自槽位自带 description，界面侧不维护中文映射表
 */
data class ThemeSlot(
    val position: String,
    val kind: SlotKind,
    val group: SlotGroup,
    val label: String,
    val colorHex: String?,
    val darkColorHex: String?,
    val lightFileName: String?,
    val darkFileName: String?,
    val present: Boolean
)

/**
 * 导航头预览的一个可选项；sharedPages 是与它共用同一套素材的其它页面
 */
data class NavVariant(val page: String, val label: String, val sharedPages: List<String>)

/**
 * 一张素材图与其服务的多个槽位
 */
data class ImageGroup(val fileName: String, val positions: List<String>, val present: Boolean)

/**
 * ltp/ 子目录是独立的付款码皮肤，有自己的资源文件与渐变配置
 */
data class PaymentCodeSlots(
    val description: String?,
    val logoFile: File?,
    val backgroundFile: File?,
    val maskFile: File?,
    val gradientStartHex: String?,
    val gradientEndHex: String?
) {
    val available: Boolean get() = logoFile != null || backgroundFile != null
}

/**
 * 主题包槽位总表。由仓库在 IO 线程构建并缓存，界面只读。
 */
data class AlipayThemeSlots(
    val packDir: File,
    val skinId: String,
    val packDescription: String,
    val slots: List<ThemeSlot>,
    val payment: PaymentCodeSlots? = null
) {
    private val byPosition: Map<String, ThemeSlot> = slots.associateBy { it.position }

    val total: Int get() = slots.size

    val darkVariant: Int get() = slots.count { it.darkColorHex != null || it.darkFileName != null }

    val missing: Int get() = slots.count { !it.present }

    fun slot(position: String): ThemeSlot? = byPosition[position]

    /** 暗色槽位缺失时回退亮色，与支付宝自身的取值顺序一致 */
    fun colorHex(position: String, dark: Boolean): String? {
        val slot = byPosition[position] ?: return null
        return if (dark) slot.darkColorHex ?: slot.colorHex else slot.colorHex
    }

    fun file(position: String, dark: Boolean): File? {
        val slot = byPosition[position] ?: return null
        if (!slot.present) return null
        val name = if (dark) slot.darkFileName ?: slot.lightFileName else slot.lightFileName
        return name?.let { File(packDir, it) }
    }

    /** 导航头由 <页面>_navi_<后缀> 成组构成，页面集合来自槽位而非固定清单 */
    private val navPages: List<String>
        get() = slots.mapNotNull { NAV_PAGE_PATTERN.matchEntire(it.position)?.groupValues?.get(1) }.distinct()

    fun navPosition(page: String, suffix: String): String = "${page}_navi_$suffix"

    /** tab 键与顺序都来自 meta，包里有几个就渲染几个 */
    val tabKeys: List<String>
        get() = slots.mapNotNull { TAB_PATTERN.matchEntire(it.position)?.groupValues?.get(1) }.distinct()

    /**
     * 导航头预览的可选项：按「素材是否真的不同」归并。
     * 多数包让理财/生活/消息共用首页背景，铺 5 个选择器只是占地方；暗色下素材分布会变化，故按模式计算。
     */
    fun navVariants(dark: Boolean): List<NavVariant> {
        val order = mutableListOf<NavVariant>()
        val index = LinkedHashMap<String, Int>()
        navPages.forEach { page ->
            val identity = listOf(
                file(navPosition(page, "bg"), dark)?.name ?: "缺素材",
                colorHex(navPosition(page, "theme_color"), dark) ?: "",
                colorHex(navPosition(page, "theme_fg_color"), dark) ?: ""
            ).joinToString("|")
            val at = index[identity]
            if (at == null) {
                index[identity] = order.size
                order.add(NavVariant(page, navLabel(page), listOf(page)))
            } else {
                order[at] = order[at].copy(sharedPages = order[at].sharedPages + page)
            }
        }
        return order
    }

    /** 页面名来自槽位自带描述（如「首页顶部背景图」），取「顶部」之前一段；无该词时退回键名 */
    fun navLabel(page: String): String {
        val raw = slot(navPosition(page, "bg"))?.label ?: return page
        return raw.substringBefore("顶部").ifBlank { page }
    }

    val colorSlots: List<ThemeSlot> get() = slots.filter { it.kind == SlotKind.Color }

    val missingSlots: List<ThemeSlot> get() = slots.filter { !it.present }

    /** 同一张图常被多个槽位共用，按文件名去重后一份素材只出现一次 */
    val uniqueImages: List<ImageGroup>
        get() = slots.filter { it.kind != SlotKind.Color && it.lightFileName != null }
            .groupBy { it.lightFileName }
            .map { (name, group) ->
                ImageGroup(
                    fileName = name ?: "",
                    positions = group.map { it.position },
                    present = group.first().present
                )
            }

    fun tabIcon(key: String, selected: Boolean, dark: Boolean): File? =
        file("tab_bar_${key}_icon_${if (selected) "selected" else "normal"}", dark)

    fun tabLabel(key: String): String =
        byPosition["tab_bar_${key}_icon_normal"]?.label
            ?.substringBefore("tab")
            ?.takeIf { it.isNotBlank() }
            ?: key

    val actionSlots: List<ThemeSlot> get() = slots.filter { it.group == SlotGroup.HomeAction }

    val grouped: Map<SlotGroup, List<ThemeSlot>>
        get() = SlotGroup.entries.mapNotNull { g ->
            slots.filter { it.group == g }.takeIf { it.isNotEmpty() }?.let { g to it }
        }.toMap()

    companion object {
        private val NAV_PAGE_PATTERN = Regex("^(.+)_navi_(?:bg|mask|theme_color|theme_fg_color)$")
        private val TAB_PATTERN = Regex("^tab_bar_(.+)_icon_(?:normal|selected)$")

        fun from(dir: File, meta: AlipayThemeMetadata, existingFiles: Set<String>): AlipayThemeSlots =
            AlipayThemeSlots(
                packDir = dir,
                skinId = meta.skinId,
                packDescription = meta.description,
                slots = meta.resource.map { resource -> resource.toThemeSlot(existingFiles) }
            )
    }
}

private fun AlipayThemeResource.toThemeSlot(existingFiles: Set<String>): ThemeSlot {
    val metaFirst = metaList?.firstOrNull()
    val light = metaFirst?.image ?: image?.takeIf { it.isNotEmpty() } ?: position
    val dark = darkImage ?: metaFirst?.darkImage ?: "dark#$light"
    val kind = when (type) {
        "color" -> SlotKind.Color
        "image" -> SlotKind.Image
        else -> SlotKind.Video
    }
    return ThemeSlot(
        position = position,
        kind = kind,
        group = groupOf(position),
        label = description?.takeIf { it.isNotBlank() } ?: position,
        colorHex = color,
        darkColorHex = darkColor,
        lightFileName = if (kind == SlotKind.Color) null else light,
        darkFileName = if (kind != SlotKind.Color && existingFiles.contains(dark)) dark else null,
        present = if (kind == SlotKind.Color) color != null else existingFiles.contains(light)
    )
}

private fun groupOf(position: String): SlotGroup = when {
    position.contains("_navi_") -> SlotGroup.NavBar
    position.startsWith("tab_bar_") -> SlotGroup.TabBar
    position.startsWith("home_") -> SlotGroup.HomeAction
    position.startsWith("ltp") || position.contains("pay_code") -> SlotGroup.PaymentCode
    else -> SlotGroup.Other
}
