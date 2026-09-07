package com.updater.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * 官方配色自适应工具
 * 严格遵照 Sesame-TK 官方设计规范，主色采用浅绿方案（拒绝深绿）：
 * 浅绿主色：#A5D6A7，浅绿容器：#E9F5E9
 * 白天：浅绿强调 #A5D6A7，背景 #F4F4F4，卡片 #FFFFFF，主文字 #1A1A1A
 * 夜间：浅绿强调 #A5D6A7，背景 #121212，卡片 #1E1E1E，主文字 #FFFFFF
 * 
 * 纯原生代码逻辑计算，彻底解耦 XML 与主题属性解析，杜绝任何崩溃闪退。
 */
object ThemeUtils {

    fun toColorStateList(@ColorInt color: Int): ColorStateList {
        return ColorStateList.valueOf(color)
    }

    /**
     * 官方色彩方案（浅绿主题）
     */
    class M3Palette(val context: Context) {
        val isNight: Boolean = try {
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } catch (_: Throwable) {
            false
        }

        // 品牌主色：统一采用浅绿色
        val primary: Int = Color.parseColor("#A5D6A7")
        val onPrimary: Int = Color.parseColor("#1B3320") // 浅绿底搭配深墨绿字，高对比度清晰易读
        val primaryContainer: Int = if (isNight) Color.parseColor("#1B3320") else Color.parseColor("#E9F5E9")
        val onPrimaryContainer: Int = if (isNight) Color.parseColor("#A5D6A7") else Color.parseColor("#1B3320")

        // 界面背景与卡片表面
        val surface: Int = if (isNight) Color.parseColor("#1E1E1E") else Color.WHITE
        val background: Int = if (isNight) Color.parseColor("#121212") else Color.parseColor("#F4F4F4")
        val onSurface: Int = if (isNight) Color.parseColor("#FFFFFF") else Color.parseColor("#1A1A1A")
        val surfaceVariant: Int = if (isNight) Color.parseColor("#252525") else Color.parseColor("#F5F5F5")
        val onSurfaceVariant: Int = if (isNight) Color.parseColor("#9E9E9E") else Color.parseColor("#757575")

        // 边框与分割线
        val outline: Int = if (isNight) Color.parseColor("#333333") else Color.parseColor("#DFDFDF")
        val outlineVariant: Int = if (isNight) Color.parseColor("#2C2C2C") else Color.parseColor("#EEEEEE")

        // 错误与危险状态
        val error: Int = if (isNight) Color.parseColor("#EF5350") else Color.parseColor("#C62828")
        val onError: Int = Color.WHITE
        val errorContainer: Int = if (isNight) Color.parseColor("#3B1B1B") else Color.parseColor("#FFEBEE")
        val onErrorContainer: Int = if (isNight) Color.parseColor("#FFCDD2") else Color.parseColor("#C62828")

        // 次级状态与警告
        val tertiary: Int = if (isNight) Color.parseColor("#FF7043") else Color.parseColor("#E64000")
        val onTertiary: Int = Color.WHITE
        val tertiaryContainer: Int = if (isNight) Color.parseColor("#33201B") else Color.parseColor("#FFF3E0")
        val onTertiaryContainer: Int = if (isNight) Color.parseColor("#FFB74D") else Color.parseColor("#E64000")

        val secondary: Int = primary
        val onSecondary: Int = onPrimary
        val secondaryContainer: Int = primaryContainer
        val onSecondaryContainer: Int = onPrimaryContainer
    }
}
