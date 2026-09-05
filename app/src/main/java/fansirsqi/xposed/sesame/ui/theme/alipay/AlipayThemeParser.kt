package fansirsqi.xposed.sesame.ui.theme.alipay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import fansirsqi.xposed.sesame.ui.theme.ThemeMetadata
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import java.io.File

/**
 * 支付宝主题数据模型（供 Sesame-TK Compose UI 渲染使用）
 */
data class ParsedAlipayTheme(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val themeColor: Color = Color(0xFF78CFF4),
    val darkThemeColor: Color = Color(0xFF67A9E7),
    val textColorNormal: Color = Color(0xFF000000),
    val textColorSelected: Color = Color(0xFF000000),
    val headerBgBitmap: Bitmap? = null,
    val darkHeaderBgBitmap: Bitmap? = null,
    val meHeaderBgBitmap: Bitmap? = null,
    val darkMeHeaderBgBitmap: Bitmap? = null,
    val tabBarBgBitmap: Bitmap? = null,
    val darkTabBarBgBitmap: Bitmap? = null,
    val tabIcons: Map<String, Bitmap> = emptyMap(),
    val actionIcons: Map<String, Bitmap> = emptyMap(),
    val gradientStart: Color = Color(0xFF0ECFFF),
    val gradientEnd: Color = Color(0xFF45B2FF),
    val ltpLogoBitmap: Bitmap? = null
)

/**
 * 支付宝主题解析器
 *
 * 负责扫描并读取目标支付宝主题文件夹（如 SDresource/2088... 下的皮肤包），
 * 解码 meta.json 配置与无扩展名的图片资源，转换为 Compose 可直接渲染的主题对象。
 */
object AlipayThemeParser {

    private const val TAG = "AlipayThemeParser"

    /**
     * 从指定主题文件夹加载
     */
    fun parseThemeDirectory(dir: File): ParsedAlipayTheme? {
        if (!dir.exists() || !dir.isDirectory) {
            Log.runtime(TAG, "主题目录不存在: ${dir.absolutePath}")
            return null
        }

        try {
            val metaFile = File(dir, "meta.json")
            val ltpMetaFile = File(dir, "ltp/meta.json")

            var skinId = dir.name
            var skinDesc = "支付宝定制皮肤"
            var themeColor = Color(0xFF78CFF4)
            var darkThemeColor = Color(0xFF67A9E7)
            var textColorNormal = Color(0xFF000000)
            var textColorSelected = Color(0xFF000000)
            var gradStart = Color(0xFF0ECFFF)
            var gradEnd = Color(0xFF45B2FF)

            // 1. 读取主 meta.json
            if (metaFile.exists()) {
                val json = metaFile.readText(Charsets.UTF_8)
                val meta = JsonUtil.parseObject(json, ThemeMetadata::class.java)
                if (meta != null) {
                    if (meta.skinId.isNotEmpty()) skinId = meta.skinId
                    if (meta.description.isNotEmpty()) skinDesc = meta.description

                    meta.resource.forEach { res ->
                        when (res.position) {
                            "tab_bar_theme_color" -> {
                                res.color?.let { themeColor = parseHexColor(it, themeColor) }
                                res.darkColor?.let { darkThemeColor = parseHexColor(it, darkThemeColor) }
                            }
                            "tab_bar_text_color_normal" -> {
                                res.color?.let { textColorNormal = parseHexColor(it, textColorNormal) }
                            }
                            "tab_bar_text_color_selected" -> {
                                res.color?.let { textColorSelected = parseHexColor(it, textColorSelected) }
                            }
                        }
                    }
                }
            }

            // 2. 读取 ltp/meta.json (付款码/LTP专属信息)
            if (ltpMetaFile.exists()) {
                try {
                    val ltpJson = ltpMetaFile.readText(Charsets.UTF_8)
                    val map = JsonUtil.parseObject(ltpJson, Map::class.java)
                    if (map != null) {
                        (map["description"] as? String)?.let { skinDesc = it }
                        (map["skinId"] as? String)?.let { skinId = it }
                        val resList = map["resource"] as? List<Map<String, Any>>
                        resList?.forEach { item ->
                            val gradient = item["gradient"] as? Map<String, Any>
                            if (gradient != null) {
                                (gradient["start"] as? String)?.let { gradStart = parseHexColor(it, gradStart) }
                                (gradient["end"] as? String)?.let { gradEnd = parseHexColor(it, gradEnd) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.runtime(TAG, "解析 ltp meta 失败: ${e.message}")
                }
            }

            // 3. 解码主要背景图片
            val homeNaviBg = decodeBitmap(File(dir, "home_navi_bg"))
            val darkHomeNaviBg = decodeBitmap(File(dir, "dark#home_navi_bg"))
            val meNaviBg = decodeBitmap(File(dir, "me_navi_bg"))
            val darkMeNaviBg = decodeBitmap(File(dir, "dark#me_navi_bg"))
            val tabBarBg = decodeBitmap(File(dir, "tab_bar_bg_200"))
            val darkTabBarBg = decodeBitmap(File(dir, "dark#tab_bar_bg_200"))
            val ltpLogo = decodeBitmap(File(dir, "ltp/logo"))

            // 4. 解码 Tab 图标
            val tabIconKeys = listOf(
                "tab_bar_home_icon_normal", "tab_bar_home_icon_selected",
                "tab_bar_wealth_icon_normal", "tab_bar_wealth_icon_selected",
                "tab_bar_life_icon_normal", "tab_bar_life_icon_selected",
                "tab_bar_msg_icon_normal", "tab_bar_msg_icon_selected",
                "tab_bar_mime_icon_normal", "tab_bar_mime_icon_selected"
            )
            val tabIcons = mutableMapOf<String, Bitmap>()
            for (key in tabIconKeys) {
                decodeBitmap(File(dir, key))?.let { tabIcons[key] = it }
            }

            // 5. 解码头部功能图标 (扫一扫、付款等)
            val actionIconKeys = listOf(
                "home_scan_icon", "home_pay_icon", "home_collect_icon",
                "home_transport_icon", "home_pocket_icon"
            )
            val actionIcons = mutableMapOf<String, Bitmap>()
            for (key in actionIconKeys) {
                decodeBitmap(File(dir, key))?.let { actionIcons[key] = it }
            }

            return ParsedAlipayTheme(
                id = skinId,
                name = skinDesc.replace(Regex("^\\d+天-"), "").replace("-静态主题皮肤套装", ""),
                description = skinDesc,
                themeColor = themeColor,
                darkThemeColor = darkThemeColor,
                textColorNormal = textColorNormal,
                textColorSelected = textColorSelected,
                headerBgBitmap = homeNaviBg,
                darkHeaderBgBitmap = darkHomeNaviBg ?: homeNaviBg,
                meHeaderBgBitmap = meNaviBg,
                darkMeHeaderBgBitmap = darkMeNaviBg ?: meNaviBg,
                tabBarBgBitmap = tabBarBg,
                darkTabBarBgBitmap = darkTabBarBg ?: tabBarBg,
                tabIcons = tabIcons,
                actionIcons = actionIcons,
                gradientStart = gradStart,
                gradientEnd = gradEnd,
                ltpLogoBitmap = ltpLogo
            )
        } catch (e: Exception) {
            Log.runtime(TAG, "解析主题失败: ${e.message}")
            return null
        }
    }

    private fun decodeBitmap(file: File): Bitmap? {
        if (!file.exists() || !file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.runtime(TAG, "解码图片失败: ${file.name}, error: ${e.message}")
            null
        }
    }

    private fun parseHexColor(hex: String, fallback: Color): Color {
        return try {
            val clean = hex.trim().replace("#", "")
            val colorInt = when (clean.length) {
                6 -> (0xFF000000 or clean.toLong(16)).toInt()
                8 -> clean.toLong(16).toInt()
                else -> return fallback
            }
            Color(colorInt)
        } catch (e: Exception) {
            fallback
        }
    }
}
