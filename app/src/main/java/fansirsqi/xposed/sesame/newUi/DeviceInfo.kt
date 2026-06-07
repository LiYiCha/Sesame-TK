package fansirsqi.xposed.sesame.newui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme
import fansirsqi.xposed.sesame.ui.BaseActivity
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.BuildConfig

class PreviewDeviceInfoProvider : PreviewParameterProvider<Map<String, String>> {
    override val values: Sequence<Map<String, String>> = sequenceOf(
        mapOf(
            "型号" to "Pixel 6",
            "产品" to "Google Pixel",
            "Android ID" to "abcd1234567890ef",
            "系统" to "Android 13 (33)",
            "构建" to "UQ1A.230105.002 S1B51",
            "OTA" to "OTA-12345",
            "SN" to "SN1234567890",
            "模块版本" to "v1.0.0-release 📦",
            "构建日期" to "2023-10-01 12:00 ⏰"
        )
    )
}

@Composable
fun DeviceInfoCard(info: Map<String, String>, oneWord: String? = null) {
    var themeMode by remember { mutableStateOf(HolidayTheme.getThemeMode()) }
    var customColor by remember { mutableStateOf(HolidayTheme.getCustomColor()) }
    var useHolidayIcons by remember { mutableStateOf(HolidayTheme.getUseHolidayIcons()) }
    var useAnimalIcons by remember { mutableStateOf(HolidayTheme.getUseAnimalIcons()) }
    var showDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    LaunchedEffect(themeMode, customColor, useHolidayIcons, useAnimalIcons) {
        val activity = context as? BaseActivity
        activity?.updateToolbarTheme()
    }
    
    val holidayColors = remember(themeMode, customColor) {
        val mode = themeMode
        when {
            mode == "auto" -> {
                val holiday = HolidayTheme.checkTodayHoliday()
                if (holiday == "default") HolidayTheme.HOLIDAY_THEMES["default"] else HolidayTheme.HOLIDAY_THEMES[holiday]
            }
            mode == "custom" -> {
                HolidayTheme.createCustomThemeColors(customColor)
            }
            else -> {
                HolidayTheme.HOLIDAY_THEMES[mode]
            }
        }
    }
    
    val darkTheme = isSystemInDarkTheme()
    val localColorScheme = if (holidayColors != null) {
        if (darkTheme) {
            darkColorScheme(
                primary = holidayColors.activeColor,
                onPrimary = Color.Black,
                primaryContainer = holidayColors.mainColor,
                onPrimaryContainer = Color.White,
                secondary = holidayColors.activeColor,
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
                primary = holidayColors.mainColor,
                onPrimary = Color.White,
                primaryContainer = holidayColors.bgColor,
                onPrimaryContainer = holidayColors.mainColor,
                secondary = holidayColors.activeColor,
                onSecondary = Color.White,
                background = holidayColors.bgColor,
                onBackground = holidayColors.textColor,
                surface = holidayColors.cardBgColor,
                onSurface = holidayColors.textColor,
                surfaceVariant = holidayColors.bgColor,
                onSurfaceVariant = holidayColors.textColor.copy(alpha = 0.7f)
            )
        }
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(colorScheme = localColorScheme) {
        val accentColor = MaterialTheme.colorScheme.onSurface
        val brandColor = MaterialTheme.colorScheme.primary
        val containerColor = MaterialTheme.colorScheme.primaryContainer

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val pagerState = rememberPagerState(pageCount = { 2 })

            Column {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp)
                ) { page ->
                    when (page) {
                        0 -> {
                            val holidayTitle = holidayColors?.title ?: "🎉 欢迎使用 Sesame-TK"
                            val holidayStory = holidayColors?.story ?: "“岁月静好，芝麻常伴。” 模块已正常加载，愿您今天也有好心情！保持童心与好奇，探索生活的精彩。"

                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = containerColor,
                                        shape = CircleShape,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Celebration,
                                                contentDescription = null,
                                                tint = brandColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = holidayTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                        Text(
                                            text = "每日寄语与节日时间表",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(containerColor.copy(alpha = 0.25f))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = holidayStory,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = accentColor.copy(alpha = 0.85f),
                                            lineHeight = 20.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "📆 下一节日：${HolidayTheme.getNextHolidayInfo()}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = brandColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val modeDesc = when {
                                        themeMode == "auto" -> {
                                            val today = HolidayTheme.checkTodayHoliday()
                                            val names = mapOf(
                                                "default" to "日常模式", "new_year" to "元旦", "valentine" to "情人节",
                                                "labor_day" to "劳动节", "mothers_day" to "母亲节", "fathers_day" to "父亲节",
                                                "childrens_day" to "儿童节", "national_day" to "国庆节", "spring_festival" to "春节",
                                                "new_years_eve" to "除夕", "dragon_boat" to "端午节", "qixi" to "七夕节",
                                                "mid_autumn" to "中秋节", "double_ninth" to "重阳节"
                                            )
                                            "自动跟随 (${names[today] ?: today})"
                                        }
                                        themeMode == "custom" -> "自定义配色 ($customColor)"
                                        else -> {
                                            val names = mapOf(
                                                "new_year" to "元旦", "valentine" to "情人节", "labor_day" to "劳动节",
                                                "childrens_day" to "儿童节", "dragon_boat" to "端午节", "mid_autumn" to "中秋节",
                                                "spring_festival" to "春节", "national_day" to "国庆节"
                                            )
                                            names[themeMode] ?: themeMode
                                        }
                                    }
                                    
                                    Column {
                                        Text(
                                            text = "当前主题配色：",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = modeDesc,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = brandColor
                                        )
                                    }
                                    
                                    Button(
                                        onClick = { showDialog = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Palette,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("切换主题", fontSize = 12.sp, color = Color.White)
                                    }
                                }

                                if (!oneWord.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = containerColor.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = oneWord,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif),
                                            color = accentColor.copy(alpha = 0.7f),
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = containerColor,
                                        shape = CircleShape,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Smartphone,
                                                contentDescription = null,
                                                tint = brandColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "设备状态",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                        Text(
                                            text = "当前环境及模块运行信息",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                info.forEach { (label, value) ->
                                    DeviceInfoRow(label, value, accentColor)
                                }
                            }
                        }
                    }
                }

                // Indicators
                Row(
                    Modifier
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(2) { iteration ->
                        val color = if (pagerState.currentPage == iteration) brandColor else containerColor.copy(alpha = 0.4f)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(6.dp)
                        )
                    }
                }
            }
        }
        
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("主题样式与色彩配置", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Switch Options
                        Text("功能开关：", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.LightGray.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        useHolidayIcons = !useHolidayIcons
                                        HolidayTheme.saveThemeConfigEx(themeMode, customColor, useHolidayIcons, useAnimalIcons)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = useHolidayIcons,
                                    onCheckedChange = {
                                        useHolidayIcons = it ?: true
                                        HolidayTheme.saveThemeConfigEx(themeMode, customColor, useHolidayIcons, useAnimalIcons)
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("启用节日限定任务图标", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        useAnimalIcons = !useAnimalIcons
                                        HolidayTheme.saveThemeConfigEx(themeMode, customColor, useHolidayIcons, useAnimalIcons)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = useAnimalIcons,
                                    onCheckedChange = {
                                        useAnimalIcons = it ?: false
                                        HolidayTheme.saveThemeConfigEx(themeMode, customColor, useHolidayIcons, useAnimalIcons)
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("启用萌宠主题图标 (WebView)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Option 1: Follow Today
                        Surface(
                            color = if (themeMode == "auto") brandColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (themeMode == "auto") brandColor else Color.LightGray.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                themeMode = "auto"
                                HolidayTheme.saveThemeConfigEx("auto", customColor, useHolidayIcons, useAnimalIcons)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = themeMode == "auto", onClick = {
                                    themeMode = "auto"
                                    HolidayTheme.saveThemeConfigEx("auto", customColor, useHolidayIcons, useAnimalIcons)
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("自动跟随今日节日", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    val todayHoliday = HolidayTheme.checkTodayHoliday()
                                    val names = mapOf(
                                        "default" to "日常模式", "new_year" to "元旦", "valentine" to "情人节",
                                        "labor_day" to "劳动节", "mothers_day" to "母亲节", "fathers_day" to "父亲节",
                                        "childrens_day" to "儿童节", "national_day" to "国庆节", "spring_festival" to "春节",
                                        "new_years_eve" to "除夕", "dragon_boat" to "端午节", "qixi" to "七夕节",
                                        "mid_autumn" to "中秋节", "double_ninth" to "重阳节"
                                    )
                                    Text("当前检测到: ${names[todayHoliday] ?: todayHoliday}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                        
                        // Option 2: Fixed Holidays
                        Text("固定节日主题配色：", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        val holidays = listOf(
                            "new_year" to "元旦",
                            "valentine" to "情人",
                            "labor_day" to "劳动",
                            "childrens_day" to "儿童",
                            "dragon_boat" to "端午",
                            "mid_autumn" to "中秋",
                            "spring_festival" to "春节",
                            "national_day" to "国庆"
                        )
                        
                        val chunkedHolidays = holidays.chunked(3)
                        chunkedHolidays.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowItems.forEach { (code, name) ->
                                    val isSelected = themeMode == code
                                    Surface(
                                        color = if (isSelected) brandColor.copy(alpha = 0.15f) else Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) brandColor else Color.LightGray.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).clickable {
                                            themeMode = code
                                            HolidayTheme.saveThemeConfigEx(code, customColor, useHolidayIcons, useAnimalIcons)
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val holidayColor = HolidayTheme.HOLIDAY_THEMES[code]
                                            if (holidayColor != null) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(holidayColor.mainColor))
                                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(holidayColor.bgColor))
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size < 3) {
                                    repeat(3 - rowItems.size) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        
                        // Option 3: Custom Color
                        Text("自定义色彩主题：", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Surface(
                            color = if (themeMode == "custom") brandColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (themeMode == "custom") brandColor else Color.LightGray.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                themeMode = "custom"
                                HolidayTheme.saveThemeConfigEx("custom", customColor, useHolidayIcons, useAnimalIcons)
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = themeMode == "custom", onClick = {
                                        themeMode = "custom"
                                        HolidayTheme.saveThemeConfigEx("custom", customColor, useHolidayIcons, useAnimalIcons)
                                    })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("启用自定义色彩", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val presets = listOf(
                                    "#E64000", "#0077B6", "#2C6E49", "#FF758F", 
                                    "#E91E63", "#E65100", "#9C27B0", "#009688"
                                )
                                val chunkedPresets = presets.chunked(4)
                                chunkedPresets.forEach { rowPresets ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowPresets.forEach { hex ->
                                            val isCurrentColor = customColor.lowercase() == hex.lowercase()
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                                    .clickable {
                                                        themeMode = "custom"
                                                        customColor = hex
                                                        HolidayTheme.saveThemeConfigEx("custom", hex, useHolidayIcons, useAnimalIcons)
                                                    }
                                                    .let {
                                                        if (isCurrentColor && themeMode == "custom") {
                                                            it.border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                                                        } else {
                                                            it
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showDialog = false }) {
                        Text("完成")
                    }
                }
            )
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String, accentColor: Color) {
    var showFull by remember { mutableStateOf(false) }
    
    val isSensitive = label == "Verify ID"
    val displayValue = if (isSensitive && !showFull) "••••••••••••" else value
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .let { 
                if (isSensitive) it.clickable { showFull = !showFull } else it
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor.copy(alpha = 0.6f),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accentColor.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f)
        )
        
        if (isSensitive) {
            Icon(
                if (showFull) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = accentColor.copy(alpha = 0.5f)
            )
        }
    }
}

object DeviceInfoUtil {

    fun showInfo(vid: String): Map<String, String> {
        fun getProp(prop: String): String {
            return try {
                val clazz = Class.forName("android.os.SystemProperties")
                val getMethod = clazz.getMethod("get", String::class.java)
                getMethod.invoke(null, prop) as? String ?: ""
            } catch (_: Exception) {
                ""
            }
        }

        fun getDeviceName(): String {
            val candidates = listOf(
                "ro.vendor.oplus.market.enname", //oneplus
                "ro.vendor.oplus.market.name",//realme
                "ro.product.marketname",//xiaomi
                "ro.vivo.market.name", //vivo
                "ro.oppo.market.name", //oppo
                "ro.product.odm.device",
                "ro.product.brand"
            )
            for (prop in candidates) {
                val value = getProp(prop)
                if (value.isNotBlank()) return value
            }
            return "${Build.BRAND} ${Build.MODEL}"
        }

        return mapOf(
            "Product" to "${Build.MANUFACTURER} ${Build.PRODUCT}",
            "Device" to getDeviceName(),
            "Android Version" to "${Build.VERSION.RELEASE} SDK (${Build.VERSION.SDK_INT})",
            "OS Build" to "${Build.DISPLAY}",
            "Verify ID" to vid,
            "Module Version" to "${BuildConfig.VERSION}.${BuildConfig.BUILD_TYPE} 📦",
            "Module Build" to "${BuildConfig.BUILD_DATE} ${BuildConfig.BUILD_TIME} ⏰"
        )
    }
}
