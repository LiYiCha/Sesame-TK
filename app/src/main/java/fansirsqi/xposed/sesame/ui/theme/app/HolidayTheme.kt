package fansirsqi.xposed.sesame.ui.theme.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import fansirsqi.xposed.sesame.util.DataStore

object HolidayTheme {
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
        "new_year" to ThemeColors(
            mainColor = Color(0xFF0077B6), bgColor = Color(0xFFF0F8FF), cardBgColor = Color.White, textColor = Color(0xFF0A2540), activeColor = Color(0xFF00B4D8),
            title = "✨ 元旦快乐 · 岁律更新",
            story = "“新元肇启，华章日新。” 告别旧岁，迎接崭新的旅程。Sesame-TK 伴您开启新的一年。"
        ),
        "valentine" to ThemeColors(
            mainColor = Color(0xFFE91E63), bgColor = Color(0xFFFCE4EC), cardBgColor = Color.White, textColor = Color(0xFF4A0E17), activeColor = Color(0xFFF06292),
            title = "💖 浪漫相约 · 爱意满怀",
            story = "“执子之手，与子偕老。” 在温暖甜蜜的日子里，祝福每一份真挚的相伴。"
        ),
        "labor_day" to ThemeColors(
            mainColor = Color(0xFFF77F00), bgColor = Color(0xFFFFF8F0), cardBgColor = Color.White, textColor = Color(0xFF212529), activeColor = Color(0xFFFCBF49),
            title = "🛠️ 致敬劳动 · 礼赞平凡",
            story = "“民生在勤，勤则不匮。” 每一份汗水，都是对生活的热爱。今天，给自己放个松。"
        ),
        "mothers_day" to ThemeColors(
            mainColor = Color(0xFFFF758F), bgColor = Color(0xFFFFF0F3), cardBgColor = Color.White, textColor = Color(0xFF3F0C1F), activeColor = Color(0xFFFF85A1),
            title = "🌸 感恩母爱 · 温馨港湾",
            story = "“谁言寸草心，报得三春晖。” 母爱无私，温润如水。别忘了向妈妈道声辛苦。"
        ),
        "fathers_day" to ThemeColors(
            mainColor = Color(0xFF1D3557), bgColor = Color(0xFFF1FAEE), cardBgColor = Color.White, textColor = Color(0xFF1D3557), activeColor = Color(0xFF457B9D),
            title = "👔 父爱如山 · 巍峨深沉",
            story = "“父爱无言，重如青山。” 他用宽阔的肩膀，为我们撑起了一片风雨无阻的天空。"
        ),
        "childrens_day" to ThemeColors(
            mainColor = Color(0xFFFF6B8B), bgColor = Color(0xFFFFF0F2), cardBgColor = Color.White, textColor = Color(0xFF2B2B2B), activeColor = Color(0xFFFF8A9F),
            title = "🎈 六一相伴 · 童心未泯",
            story = "“愿你历尽沧桑，归来仍是少年。” 保持好奇，留住天真，祝童心不老的你节日快乐！"
        ),
        "national_day" to ThemeColors(
            mainColor = Color(0xFFD62828), bgColor = Color(0xFFFFF5F5), cardBgColor = Color.White, textColor = Color(0xFF1A1A1A), activeColor = Color(0xFFF77F00),
            title = "🇨🇳 盛世华诞 · 锦绣中华",
            story = "“神州万里江山秀，红旗招展展宏图。” 祝伟大的祖国繁荣昌盛，国泰民安！"
        ),
        "spring_festival" to ThemeColors(
            mainColor = Color(0xFFD00000), bgColor = Color(0xFFFFF3E0), cardBgColor = Color.White, textColor = Color(0xFF3E2723), activeColor = Color(0xFFFFB300),
            title = "🧧 新春大吉 · 万事如意",
            story = "“千门万户曈曈日，总把新桃换旧符。” 新春新气象，祝您阖家幸福，万事顺遂！"
        ),
        "new_years_eve" to ThemeColors(
            mainColor = Color(0xFFD32F2F), bgColor = Color(0xFFFFF3E3), cardBgColor = Color.White, textColor = Color(0xFF4E1A1A), activeColor = Color(0xFFFF9100),
            title = "🏮 除夕守岁 · 阖家团圆",
            story = "“一夜连双岁，五更分二年。” 辞旧迎新除夕夜，团团圆圆守岁时，祝岁岁常安！"
        ),
        "dragon_boat" to ThemeColors(
            mainColor = Color(0xFF2C6E49), bgColor = Color(0xFFE8F5E9), cardBgColor = Color.White, textColor = Color(0xFF1C3A27), activeColor = Color(0xFF4F9D69),
            title = "🌿 端午安康 · 粽香四溢",
            story = "“轻汗微微透碧纨，明朝端午浴芳兰。” 挂艾草，吃香粽，享安康。"
        ),
        "qixi" to ThemeColors(
            mainColor = Color(0xFFEC4899), bgColor = Color(0xFFFDF2F8), cardBgColor = Color.White, textColor = Color(0xFF47182F), activeColor = Color(0xFFF472B6),
            title = "🌌 七夕鹊桥 · 银河相会",
            story = "“两情若是久长时，又岂在朝朝暮暮。” 星汉灿烂，鹊桥飞架，愿深情不被辜负。"
        ),
        "mid_autumn" to ThemeColors(
            mainColor = Color(0xFFFBC02D), bgColor = Color(0xFFFFFDE7), cardBgColor = Color.White, textColor = Color(0xFF1E1B4B), activeColor = Color(0xFFF1C40F),
            title = "🌕 中秋团圆 · 月满人间",
            story = "“但愿人长久，千里共婵娟。” 桂花飘香，圆月高悬。无论身处何方，共赏此轮明月。"
        ),
        "double_ninth" to ThemeColors(
            mainColor = Color(0xFFE65100), bgColor = Color(0xFFFFF3E0), cardBgColor = Color.White, textColor = Color(0xFF3E2723), activeColor = Color(0xFFFB8C00),
            title = "🍂 重阳登高 · 岁月敬老",
            story = "“遥知兄弟登高处，遍插茱萸少一人。” 岁岁重阳，今又重阳。祝老人们安康长寿。"
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
            cardBgColor = Color.White,
            textColor = text,
            activeColor = color,
            title = "✨ 自定义色彩主题",
            story = "“独一无二，自成一派。” 模块已应用您专属定制的色彩主题，展现独特个性格调。"
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
                if (holiday == "default") null else HOLIDAY_THEMES[holiday]
            }
            mode == "custom" -> {
                createCustomThemeColors(getCustomColor())
            }
            else -> {
                HOLIDAY_THEMES[mode]
            }
        }
    }

    fun getHolidayColorScheme(darkTheme: Boolean): ColorScheme? {
        val colors = getHolidayColors() ?: return null
        
        return if (darkTheme) {
            darkColorScheme(
                primary = colors.activeColor,
                onPrimary = Color.Black,
                primaryContainer = colors.mainColor,
                onPrimaryContainer = Color.White,
                secondary = colors.activeColor,
                onSecondary = Color.Black,
                background = Color(0xFF121212),
                onBackground = Color(0xFFE0E0E0),
                surface = Color(0xFF1E1E1E),
                onSurface = Color(0xFFE0E0E0),
                surfaceVariant = Color(0xFF2C2C2C),
                onSurfaceVariant = Color(0xFFBDBDBD)
            )
        } else {
            lightColorScheme(
                primary = colors.mainColor,
                onPrimary = Color.White,
                primaryContainer = colors.bgColor,
                onPrimaryContainer = colors.mainColor,
                secondary = colors.activeColor,
                onSecondary = Color.White,
                background = colors.bgColor,
                onBackground = colors.textColor,
                surface = colors.cardBgColor,
                onSurface = colors.textColor,
                surfaceVariant = colors.bgColor,
                onSurfaceVariant = colors.textColor.copy(alpha = 0.7f)
            )
        }
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
