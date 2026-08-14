package fansirsqi.xposed.sesame.ui.theme.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import fansirsqi.xposed.sesame.util.DataStore
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableIntStateOf

object HolidayTheme {

    fun getCurrentTimePhase(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..7 -> "dawn"         // 05:00-07:59
            in 8..16 -> "day"          // 08:00-16:59
            in 17..19 -> "sunset"      // 17:00-19:59
            in 20..21 -> "dusk"        // 20:00-21:59
            else -> "midnight"         // 22:00-04:59
        }
    }

    data class SkyColors(val top: Color, val bottom: Color, val surfaceAlpha: Float = 0.8f)
    
    fun getSkyColors(): SkyColors {
        return when (getCurrentTimePhase()) {
            "dawn" -> SkyColors(Color(0xFFB3E5FC), Color(0xFFFFAB91), 0.8f)
            "day" -> SkyColors(Color(0xFF81D4FA), Color(0xFFE1F5FE), 0.85f)
            "sunset" -> SkyColors(Color(0xFF7E57C2), Color(0xFFFF7043), 0.75f)
            "dusk" -> SkyColors(Color(0xFF37474F), Color(0xFFFF5722), 0.7f)
            else -> SkyColors(Color(0xFF1A237E), Color(0xFF0D0D2B), 0.7f)
        }
    }

    val themeVersion = mutableIntStateOf(0)
    val themeObservers = mutableListOf<() -> Unit>()

    fun notifyThemeChanged() {
        themeObservers.forEach { it.invoke() }
    }

    fun applyGlobalNightMode() {
        val mode = getDarkMode()
        val nightMode = when {
            mode == "light" -> AppCompatDelegate.MODE_NIGHT_NO
            mode == "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            mode == "schedule" -> {
                if (shouldUseDarkTheme()) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            }
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    data class ThemeColors(
        val mainColor: Color,
        val bgColor: Color,
        val cardBgColor: Color,
        val textColor: Color,
        val activeColor: Color,
        val title: String,
        val story: String
    )

    val HOLIDAY_THEMES = mapOf(
        "default" to ThemeColors(
            mainColor = Color(0xFF4F7A5C), bgColor = Color(0xFFE0EEE8), cardBgColor = Color(0xFFE0EEE8), textColor = Color(0xFF2D3436), activeColor = Color(0xFF8AA88E),
            title = "欢迎",
            story = "种豆南山下，草盛豆苗稀。晨兴理荒秽，带月荷锄归。"
        ),
        "new_year" to ThemeColors(
            mainColor = Color(0xFF5B8DB8), bgColor = Color(0xFFD6ECF0), cardBgColor = Color(0xFFD6ECF0), textColor = Color(0xFF37474F), activeColor = Color(0xFF8FB0C8),
            title = "元旦",
            story = "律回岁晚冰霜少，春到人间草木知。便觉眼前生意满，东风吹水绿参差。"
        ),
        "valentine" to ThemeColors(
            mainColor = Color(0xFFDB7093), bgColor = Color(0xFFFFF0F5), cardBgColor = Color(0xFFFFF0F5), textColor = Color(0xFFDB7093), activeColor = Color(0xFFF8BBD0),
            title = "情人节",
            story = "相见时难别亦难，东风无力百花残。春蚕到死丝方尽，蜡炬成灰泪始干。"
        ),
        "labor_day" to ThemeColors(
            mainColor = Color(0xFFC09A5E), bgColor = Color(0xFFFFF3E0), cardBgColor = Color(0xFFFFF3E0), textColor = Color(0xFF5C4A32), activeColor = Color(0xFF9A8A6E),
            title = "劳动节",
            story = "锄禾日当午，汗滴禾下土。谁知盘中餐，粒粒皆辛苦。"
        ),
        "mothers_day" to ThemeColors(
            mainColor = Color(0xFFC87888), bgColor = Color(0xFFFBE9E7), cardBgColor = Color(0xFFFBE9E7), textColor = Color(0xFF5C3A3A), activeColor = Color(0xFFA890A0),
            title = "母亲节",
            story = "慈母手中线，游子身上衣。谁言寸草心，报得三春晖。"
        ),
        "fathers_day" to ThemeColors(
            mainColor = Color(0xFF6E8494), bgColor = Color(0xFFECEFF1), cardBgColor = Color(0xFFECEFF1), textColor = Color(0xFF37474F), activeColor = Color(0xFF9E8A7A),
            title = "父亲节",
            story = "哀哀父母，生我劬劳。欲报之德，昊天罔极。"
        ),
        "childrens_day" to ThemeColors(
            mainColor = Color(0xFF5CA8D8), bgColor = Color(0xFFFDEAF2), cardBgColor = Color(0xFFFDEAF2), textColor = Color(0xFF3D3D3D), activeColor = Color(0xFFF5A8C8),
            title = "儿童节",
            story = "草长莺飞二月天，拂堤杨柳醉春烟。儿童散学归来早，忙趁东风放纸鸢。"
        ),
        "national_day" to ThemeColors(
            mainColor = Color(0xFFB22222), bgColor = Color(0xFFFFF1F0), cardBgColor = Color(0xFFFFF1F0), textColor = Color(0xFF600000), activeColor = Color(0xFFFF4D4F),
            title = "国庆",
            story = "江山如此多娇，引无数英雄竞折腰。俱往矣，数风流人物，还看今朝。"
        ),
        "spring_festival" to ThemeColors(
            mainColor = Color(0xFFB22222), bgColor = Color(0xFFFFF1F0), cardBgColor = Color(0xFFFFF1F0), textColor = Color(0xFF600000), activeColor = Color(0xFFFF4D4F),
            title = "春节",
            story = "爆竹声中一岁除，春风送暖入屠苏。千门万户曈曈日，总把新桃换旧符。"
        ),
        "new_years_eve" to ThemeColors(
            mainColor = Color(0xFFA83A3A), bgColor = Color(0xFFFFE8E8), cardBgColor = Color(0xFFFFE8E8), textColor = Color(0xFF4E2A2A), activeColor = Color(0xFFC08A50),
            title = "除夕",
            story = "今岁今宵尽，明年明日催。寒随一夜去，春逐五更来。"
        ),
        "dragon_boat" to ThemeColors(
            mainColor = Color(0xFF6A994E), bgColor = Color(0xFFCAD2C5), cardBgColor = Color(0xFFCAD2C5), textColor = Color(0xFF354F52), activeColor = Color(0xFF84A98C),
            title = "端午",
            story = "亦余心之所善兮，虽九死其犹未悔。路漫漫其修远兮，吾将上下而求索。"
        ),
        "qixi" to ThemeColors(
            mainColor = Color(0xFF9A7CB8), bgColor = Color(0xFFF3E5F5), cardBgColor = Color(0xFFF3E5F5), textColor = Color(0xFF4A3A5C), activeColor = Color(0xFFC07888),
            title = "七夕",
            story = "金风玉露一相逢，便胜却人间无数。两情若是久长时，又岂在朝朝暮暮。"
        ),
        "mid_autumn" to ThemeColors(
            mainColor = Color(0xFFF9773B), bgColor = Color(0xFFFFF7E6), cardBgColor = Color(0xFFFFF7E6), textColor = Color(0xFF5F2700), activeColor = Color(0xFFFCA452),
            title = "中秋",
            story = "人有悲欢离合，月有阴晴圆缺。但愿人长久，千里共婵娟。"
        ),
        "double_ninth" to ThemeColors(
            mainColor = Color(0xFFC08A50), bgColor = Color(0xFFFFF3E0), cardBgColor = Color(0xFFFFF3E0), textColor = Color(0xFF5C4030), activeColor = Color(0xFF8A9E6E),
            title = "重阳",
            story = "独在异乡为异客，每逢佳节倍思亲。遥知兄弟登高处，遍插茱萸少一人。"
        )
    )

    private val LUNAR_HOLIDAYS_MAP = mapOf(
        2026 to mapOf(
            "new_years_eve" to "02-16",
            "spring_festival_start" to "02-17", "spring_festival_end" to "02-23",
            "dragon_boat" to "06-19",
            "qixi" to "08-19",
            "mid_autumn" to "09-25",
            "double_ninth" to "10-18"
        ),
        2027 to mapOf(
            "new_years_eve" to "02-05",
            "spring_festival_start" to "02-06", "spring_festival_end" to "02-12",
            "dragon_boat" to "06-09",
            "qixi" to "08-08",
            "mid_autumn" to "09-15",
            "double_ninth" to "10-08"
        ),
        2028 to mapOf(
            "new_years_eve" to "01-25",
            "spring_festival_start" to "01-26", "spring_festival_end" to "02-01",
            "dragon_boat" to "05-28",
            "qixi" to "08-26",
            "mid_autumn" to "10-03",
            "double_ninth" to "10-26"
        ),
        2029 to mapOf(
            "new_years_eve" to "02-12",
            "spring_festival_start" to "02-13", "spring_festival_end" to "02-19",
            "dragon_boat" to "06-16",
            "qixi" to "08-16",
            "mid_autumn" to "09-22",
            "double_ninth" to "10-16"
        ),
        2030 to mapOf(
            "new_years_eve" to "02-02",
            "spring_festival_start" to "02-03", "spring_festival_end" to "02-09",
            "dragon_boat" to "06-05",
            "qixi" to "08-05",
            "mid_autumn" to "09-12",
            "double_ninth" to "10-05"
        )
    )

    fun checkTodayHoliday(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) // 0-11
        val date = calendar.get(Calendar.DATE)
        val mm = String.format("%02d", month + 1)
        val dd = String.format("%02d", date)
        val todayStr = "$mm-$dd"

        // A. 固定公历节日
        if (todayStr == "01-01") return "new_year"
        if (todayStr == "02-14") return "valentine"
        if (todayStr == "05-01") return "labor_day"
        if (todayStr == "06-01") return "childrens_day"
        if (todayStr >= "10-01" && todayStr <= "10-07") return "national_day"

        // B. 相对公历节日 (母亲节/父亲节)
        if (month == Calendar.MAY) {
            var sunCount = 0
            val temp = Calendar.getInstance()
            for (d in 1..date) {
                temp.set(year, Calendar.MAY, d)
                if (temp.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) sunCount++
            }
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && sunCount == 2) return "mothers_day"
        }
        if (month == Calendar.JUNE) {
            var sunCount = 0
            val temp = Calendar.getInstance()
            for (d in 1..date) {
                temp.set(year, Calendar.JUNE, d)
                if (temp.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) sunCount++
            }
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && sunCount == 3) return "fathers_day"
        }

        // C. 农历节日查表匹配
        val lunarMap = LUNAR_HOLIDAYS_MAP[year]
        if (lunarMap != null) {
            if (todayStr == lunarMap["new_years_eve"]) return "new_years_eve"
            val start = lunarMap["spring_festival_start"]
            val end = lunarMap["spring_festival_end"]
            if (start != null && end != null && todayStr >= start && todayStr <= end) return "spring_festival"
            if (todayStr == lunarMap["dragon_boat"]) return "dragon_boat"
            if (todayStr == lunarMap["qixi"]) return "qixi"
            if (todayStr == lunarMap["mid_autumn"]) return "mid_autumn"
            if (todayStr == lunarMap["double_ninth"]) return "double_ninth"
        }

        return "default"
    }

    fun getThemeMode(): String {
        return try {
            DataStore.get("custom_theme_mode", String::class.java) ?: "auto"
        } catch (e: Exception) {
            "auto"
        }
    }

    fun getDarkMode(): String {
        return try {
            DataStore.get("dark_mode_ui", String::class.java) ?: "auto"
        } catch (e: Exception) {
            "auto"
        }
    }

    fun setDarkMode(mode: String, applyImmediately: Boolean = true) {
        try {
            DataStore.put("dark_mode_ui", mode)
            themeVersion.intValue++
            if (applyImmediately) {
                applyGlobalNightMode()
            }
            notifyThemeChanged()
        } catch (_: Exception) {}
    }

    /** 判断当前是否需要深色模式（含时间调度逻辑） */
    fun shouldUseDarkTheme(): Boolean {
        return when (getDarkMode()) {
            "light" -> false
            "dark" -> true
            "schedule" -> {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                hour in 18..23 || hour in 0..5
            }
            else -> false
        }
    }

    /** 随一天时间变换的主题色 */
    val TIME_THEMES = mapOf(
        "dawn" to ThemeColors(      // 5:00-8:00 黎明·暖橙
            mainColor = Color(0xFFFF7043), bgColor = Color(0xFFFFF3E0),
            cardBgColor = Color(0xFFFFFBF5), textColor = Color(0xFF3E2723),
            activeColor = Color(0xFFE64A19), title = "黎明", story = "一日之计在于晨"
        ),
        "morning" to ThemeColors(    // 8:00-12:00 上午·青绿
            mainColor = Color(0xFF66BB6A), bgColor = Color(0xFFE8F5E9),
            cardBgColor = Color(0xFFF5FFF5), textColor = Color(0xFF1B5E20),
            activeColor = Color(0xFF43A047), title = "上午", story = "盛年不重来，一日难再晨"
        ),
        "noon" to ThemeColors(       // 12:00-14:00 正午·明蓝
            mainColor = Color(0xFF42A5F5), bgColor = Color(0xFFE3F2FD),
            cardBgColor = Color(0xFFF5FBFF), textColor = Color(0xFF0D47A1),
            activeColor = Color(0xFF1E88E5), title = "正午", story = "午安，注意休息"
        ),
        "afternoon" to ThemeColors(  // 14:00-18:00 午后·暖金
            mainColor = Color(0xFFFFA726), bgColor = Color(0xFFFFF8E1),
            cardBgColor = Color(0xFFFFFDF5), textColor = Color(0xFF4E342E),
            activeColor = Color(0xFFF57C00), title = "午后", story = "午后时光，悠闲自在"
        ),
        "dusk" to ThemeColors(       // 18:00-20:00 黄昏·橘红
            mainColor = Color(0xFFEF5350), bgColor = Color(0xFFFFF0F0),
            cardBgColor = Color(0xFFFFF5F5), textColor = Color(0xFF3E1010),
            activeColor = Color(0xFFC62828), title = "黄昏", story = "夕阳无限好，只是近黄昏"
        ),
        "night" to ThemeColors(      // 20:00-5:00 夜晚·深紫
            mainColor = Color(0xFF7E57C2), bgColor = Color(0xFF1A1025),
            cardBgColor = Color(0xFF241535), textColor = Color(0xFFE1BEE7),
            activeColor = Color(0xFFB388FF), title = "夜晚", story = "月落乌啼霜满天，江枫渔火对愁眠"
        )
    )

    /** 根据当前时间返回对应的时段主题 */
    fun getTimeTheme(): ThemeColors? {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..7 -> TIME_THEMES["dawn"]
            in 8..11 -> TIME_THEMES["morning"]
            in 12..13 -> TIME_THEMES["noon"]
            in 14..17 -> TIME_THEMES["afternoon"]
            in 18..19 -> TIME_THEMES["dusk"]
            else -> TIME_THEMES["night"]
        }
    }

    /** 预设配色套餐（主色, 背景色） */
    val PRESET_COMBOS = listOf(
        listOf("#FF6B6B", "#FFF0EE"),  // 珊瑚红
        listOf("#4ECDC4", "#E8FAF8"),  // 青碧绿
        listOf("#FF8C42", "#FFF3E8"),  // 暖橘
        listOf("#7C3AED", "#F3EEFF"),  // 紫罗兰
        listOf("#0EA5E9", "#E6F4FB"),  // 天蓝
        listOf("#F59E0B", "#FFFBEB"),  // 琥珀金
        listOf("#10B981", "#ECFDF5"),  // 翡翠绿
        listOf("#EC4899", "#FDF2F8"),  // 玫瑰粉
        listOf("#6366F1", "#EEF2FF"),  // 靛蓝
        listOf("#14B8A6", "#F0FDFA"),  // 青蓝
        listOf("#F97316", "#FFF7ED"),  // 夕阳橙
        listOf("#8B5CF6", "#F5F3FF"),  // 淡紫
    )

    /** 随机获取一组配色 */
    fun getRandomCombo(): List<String> {
        return PRESET_COMBOS.random()
    }

    fun getCustomColor(): String {
        return try {
            DataStore.get("custom_theme_color", String::class.java) ?: "#E64000"
        } catch (e: Exception) {
            "#E64000"
        }
    }

    fun getUseHolidayIcons(): Boolean {
        return try {
            DataStore.get("custom_theme_use_holiday_icons", java.lang.Boolean::class.java) as? Boolean ?: true
        } catch (e: Exception) {
            true
        }
    }

    fun getUseAnimalIcons(): Boolean {
        return try {
            DataStore.get("custom_theme_use_animal_icons", java.lang.Boolean::class.java) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun saveThemeConfig(mode: String, color: String) {
        try {
            DataStore.put("custom_theme_mode", mode)
            DataStore.put("custom_theme_color", color)
            themeVersion.intValue++
            notifyThemeChanged()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun saveThemeConfigEx(mode: String, color: String, useHoliday: Boolean, useAnimal: Boolean) {
        try {
            DataStore.put("custom_theme_mode", mode)
            DataStore.put("custom_theme_color", color)
            DataStore.put("custom_theme_use_holiday_icons", useHoliday)
            DataStore.put("custom_theme_use_animal_icons", useAnimal)
            themeVersion.intValue++
            notifyThemeChanged()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun createCustomThemeColors(mainHex: String): ThemeColors {
        val color = try {
            Color(android.graphics.Color.parseColor(mainHex))
        } catch (e: Exception) {
            Color(0xFFE64000) // fallback
        }
        
        // Blend 8% color + 92% white for background
        val r = color.red * 0.08f + 1f * 0.92f
        val g = color.green * 0.08f + 1f * 0.92f
        val b = color.blue * 0.08f + 1f * 0.92f
        val bg = Color(r, g, b, 1f)
        
        // Text color is dark gray
        val text = Color(0xFF1A1A1A)
        
        return ThemeColors(
            mainColor = color,
            bgColor = bg,
            cardBgColor = bg,
            textColor = text,
            activeColor = color,
            title = "自定义",
            story = "苔花如米小，也学牡丹开。"
        )
    }

    fun isColorLight(color: Color): Boolean {
        val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        return luminance > 0.6f
    }

    fun getHolidayColors(): ThemeColors? {
        val mode = getThemeMode()
        return when {
            mode == "auto" -> {
                val holiday = checkTodayHoliday()
                HOLIDAY_THEMES[holiday] ?: HOLIDAY_THEMES["default"]
            }
            mode == "custom" -> {
                createCustomThemeColors(getCustomColor())
            }
            else -> {
                HOLIDAY_THEMES[mode] ?: HOLIDAY_THEMES["default"]
            }
        }
    }

    /** 获取当前生效的主题色（时段 or 节日 or 自定义），永不返回 null */
    fun getActiveThemeColors(): ThemeColors {
        return if (getDarkMode() == "schedule") {
            getTimeTheme() ?: HOLIDAY_THEMES["default"]!!
        } else {
            getHolidayColors() ?: HOLIDAY_THEMES["default"]!!
        }
    }

    /** 单一数据源：根据系统深色标志解析完整调色板，浅色/深色统一 */
    fun resolvePalette(isSystemDark: Boolean): ThemePalette {
        val dark = when (getDarkMode()) {
            "light" -> false
            "dark" -> true
            "schedule" -> shouldUseDarkTheme()
            else -> isSystemDark
        }
        val colors = getActiveThemeColors()
        return if (dark) buildDarkPalette(colors) else buildLightPalette(colors)
    }

    fun getHolidayColorScheme(darkTheme: Boolean): ColorScheme {
        val colors = getHolidayColors() ?: HOLIDAY_THEMES["default"]!!
        return (if (darkTheme) buildDarkPalette(colors) else buildLightPalette(colors)).toColorScheme()
    }

    fun getNextHolidayInfo(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val date = calendar.get(Calendar.DATE)
        val todayCode = month * 100 + date

        val solarHolidays = listOf(
            Triple(101, "元旦", "01-01"),
            Triple(214, "情人节", "02-14"),
            Triple(501, "劳动节", "05-01"),
            Triple(601, "儿童节", "06-01"),
            Triple(1001, "国庆节", "10-01")
        )

        val lunarMap = LUNAR_HOLIDAYS_MAP[year]
        val lunarHolidays = mutableListOf<Triple<Int, String, String>>()
        if (lunarMap != null) {
            fun addLunar(key: String, name: String) {
                val d = lunarMap[key] ?: return
                val parts = d.split("-")
                if (parts.size == 2) {
                    val code = parts[0].toInt() * 100 + parts[1].toInt()
                    lunarHolidays.add(Triple(code, name, d))
                }
            }
            addLunar("new_years_eve", "除夕")
            addLunar("spring_festival_start", "春节")
            addLunar("dragon_boat", "端午节")
            addLunar("qixi", "七夕节")
            addLunar("mid_autumn", "中秋节")
            addLunar("double_ninth", "重阳节")
        }

        val allHolidays = (solarHolidays + lunarHolidays).sortedBy { it.first }
        for (h in allHolidays) {
            if (h.first > todayCode) {
                return "${h.second} (${h.third})"
            }
        }
        return "元旦 (次年01-01)"
    }
}

// ===== 深色模式主题色推导（Material 3 tone 80 柔和主色 + 色调化深色表面） =====

private fun Color.rgbToHsl(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        red -> (green - blue) / d + (if (green < blue) 6f else 0f)
        green -> (blue - red) / d + 2f
        else -> (red - green) / d + 4f
    } / 6f
    return floatArrayOf(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val s2 = s.coerceIn(0f, 1f)
    val l2 = l.coerceIn(0f, 1f)
    if (s2 == 0f) return Color(l2, l2, l2, 1f)
    val q = if (l2 < 0.5f) l2 * (1f + s2) else l2 + s2 - l2 * s2
    val p = 2f * l2 - q
    fun hue(t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }
    val h2 = (h % 1f + 1f) % 1f
    return Color(hue(h2 + 1f / 3f), hue(h2), hue(h2 - 1f / 3f), 1f)
}

/** 统一调色板：XML 端和 Compose 端都从这里取色，保证三端一致 */
data class ThemePalette(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color
) {
    fun toColorScheme(): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = primary, onPrimary = onPrimary,
                primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
                secondary = secondary, onSecondary = onSecondary,
                background = background, onBackground = onBackground,
                surface = surface, onSurface = onSurface,
                surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
                surfaceContainerLowest = background,
                surfaceContainerLow = surface,
                surfaceContainer = surface,
                surfaceContainerHigh = surface,
                surfaceContainerHighest = surfaceVariant
            )
        } else {
            lightColorScheme(
                primary = primary, onPrimary = onPrimary,
                primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
                secondary = secondary, onSecondary = onSecondary,
                background = background, onBackground = onBackground,
                surface = surface, onSurface = onSurface,
                surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
                surfaceContainerLowest = background,
                surfaceContainerLow = surface,
                surfaceContainer = surface,
                surfaceContainerHigh = surface,
                surfaceContainerHighest = surfaceVariant
            )
        }
    }
}

/**
 * 浅色调色板：每个节日内部「同色相三级渐变」
 * - 页面背景 = 传统色淡化（同色相、极浅）
 * - 卡片 = 传统色原色
 * - 强调 primary = 传统色加深（低饱和中亮度）
 */
private fun buildLightPalette(colors: HolidayTheme.ThemeColors): ThemePalette {
    val background = androidx.compose.ui.graphics.lerp(Color.White, colors.bgColor, 0.55f)
    val surface = colors.cardBgColor
    val surfaceVariant = androidx.compose.ui.graphics.lerp(colors.bgColor, colors.mainColor, 0.18f)
    return ThemePalette(
        isDark = false,
        primary = colors.mainColor, onPrimary = Color.White,
        primaryContainer = colors.bgColor, onPrimaryContainer = colors.mainColor,
        secondary = colors.activeColor, onSecondary = Color.White,
        background = background, onBackground = colors.textColor,
        surface = surface, onSurface = colors.textColor,
        surfaceVariant = surfaceVariant, onSurfaceVariant = colors.textColor.copy(alpha = 0.75f)
    )
}

/**
 * 深色调色板（主题色浸染式）：
 * - 背景/卡片 = 当前主题色的「低亮度深调」（深红→深红黑、深绿→深绿黑、深蓝→深蓝黑），
 *   整个深色界面由主题色贯穿，不再使用固定墨绿，避免与主题色色相割裂
 * - 文字 = 带主题色相的浅色，与背景同色系
 * - 强调色 primary = 主题色亮化版（按钮/开关/链接/图标，大面积透出主题色）
 * - onPrimary = 页面背景色，按钮文字与整体呼应
 */
private fun buildDarkPalette(colors: HolidayTheme.ThemeColors): ThemePalette {
    val mainHsl = colors.mainColor.rgbToHsl()
    val h = mainHsl[0]
    val s = mainHsl[1]

    // 背景与卡片：主题色低亮度深调，保留色相与中等饱和
    val background = hslToColor(h, (s * 0.55f).coerceIn(0f, 1f), 0.05f)
    val surface = hslToColor(h, (s * 0.50f).coerceIn(0f, 1f), 0.10f)
    val surfaceVariant = hslToColor(h, (s * 0.45f).coerceIn(0f, 1f), 0.17f)

    // 文字：浅色、低饱和、带主题色相
    val onDark = hslToColor(h, (s * 0.22f).coerceIn(0f, 1f), 0.90f)
    val onSurfaceVariant = hslToColor(h, (s * 0.30f).coerceIn(0f, 1f), 0.72f)

    // 强调色：主题色亮化（按钮/开关/链接）
    val primary = hslToColor(h, (s * 0.85f).coerceIn(0f, 1f), 0.70f)
    val onPrimary = background   // 按钮文字用页面背景色，全局呼应

    // 容器：主题色原色
    val primaryContainer = colors.mainColor
    val onPrimaryContainer = if (HolidayTheme.isColorLight(primaryContainer)) Color(0xFF101010) else onDark

    // 辅助色：主题色中调
    val secondary = hslToColor(h, (s * 0.65f).coerceIn(0f, 1f), 0.55f)
    val onSecondary = background

    return ThemePalette(
        isDark = true,
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        background = background, onBackground = onDark,
        surface = surface, onSurface = onDark,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant
    )
}
