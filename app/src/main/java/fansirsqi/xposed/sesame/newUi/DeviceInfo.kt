package fansirsqi.xposed.sesame.newui

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.BuildConfig
import kotlinx.coroutines.launch

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

/**
 * 主题实时预览卡片 — 脉冲按钮 + 打字机故事 + 实时时钟
 */
@Composable
private fun LivePreviewCard(holidayColors: HolidayTheme.ThemeColors?, dark: Boolean) {
    val main = holidayColors?.mainColor ?: if (dark) Color(0xFF90CAF9) else Color(0xFF1976D2)
    val bg = holidayColors?.bgColor ?: if (dark) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)
    val textColor = holidayColors?.textColor ?: if (dark) Color(0xFFE0E0E0) else Color(0xFF212121)
    val story = holidayColors?.story ?: ""
    val title = holidayColors?.title ?: ""

    // 实时时钟
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val hour = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
    val min = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.MINUTE)
    val periodName = when (hour) { in 5..7 -> "🌅 黎明"; in 8..11 -> "☀️ 上午"; in 12..13 -> "☀️ 正午"; in 14..17 -> "🌤️ 午后"; in 18..19 -> "🌅 黄昏"; else -> "🌙 夜晚" }
    val timeStr = "${hour.toString().padStart(2,'0')}:${min.toString().padStart(2,'0')}"

    // 打字机效果
    var displayedChars by remember { mutableIntStateOf(0) }
    LaunchedEffect(story) {
        displayedChars = 0
        story.forEachIndexed { i, _ ->
            kotlinx.coroutines.delay(60)
            displayedChars = i + 1
        }
    }

    // 脉冲动画
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 标题行：预览标签 + 实时时钟
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("实时预览", fontSize = 11.sp, color = textColor.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(periodName, fontSize = 11.sp, color = main, fontWeight = FontWeight.Medium)
                    Text(" · $timeStr", fontSize = 11.sp, color = textColor.copy(alpha = 0.4f))
                }
            }
            Spacer(Modifier.height(10.dp))

            Surface(
                color = bg,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌳 蚂蚁森林", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Surface(color = main.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                            Text("今日", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = main)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // 打字机故事
                    if (story.isNotEmpty()) {
                        Text(
                            story.take(displayedChars),
                            fontSize = 12.sp,
                            color = textColor.copy(alpha = 0.65f),
                            lineHeight = 18.sp
                        )
                        if (displayedChars < story.length) {
                            Text("▍", fontSize = 12.sp, color = main)
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    Text("已收集 158g 绿色能量", fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                            .background(main.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(0.73f).height(6.dp)
                                .background(main, RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("73%", fontSize = 10.sp, color = main, modifier = Modifier.align(Alignment.End))
                    Spacer(Modifier.height(10.dp))

                    // 脉冲按钮
                    Surface(
                        color = main,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally).scale(pulseScale)
                    ) {
                        Text(
                            "去收集",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceInfoCard(info: Map<String, String>, oneWord: String? = null) {
    val currentThemeVersion = HolidayTheme.themeVersion.intValue
    var themeMode by rememberSaveable(currentThemeVersion) { mutableStateOf(HolidayTheme.getThemeMode()) }
    var customColor by rememberSaveable(currentThemeVersion) { mutableStateOf(HolidayTheme.getCustomColor()) }
    var useHolidayIcons by rememberSaveable(currentThemeVersion) { mutableStateOf(HolidayTheme.getUseHolidayIcons()) }
    var useAnimalIcons by rememberSaveable(currentThemeVersion) { mutableStateOf(HolidayTheme.getUseAnimalIcons()) }
    var darkMode by rememberSaveable(currentThemeVersion) { mutableStateOf(HolidayTheme.getDarkMode()) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showLinePicker by rememberSaveable { mutableStateOf(false) }
    var showAnimalPicker by rememberSaveable { mutableStateOf(false) }
    var showPlantPicker by rememberSaveable { mutableStateOf(false) }
    
    val context = LocalContext.current
    LaunchedEffect(themeMode, customColor, useHolidayIcons, useAnimalIcons, darkMode) {
        val activity = context as? BaseActivity
        activity?.updateToolbarTheme()
    }
    
    val holidayColors = remember(themeMode, customColor, darkMode) {
        if (darkMode == "schedule") {
            HolidayTheme.getTimeTheme()
        } else {
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
    }
    
    val darkTheme = darkMode.let { mode ->
        when (mode) {
            "light" -> false
            "dark" -> true
            "schedule" -> HolidayTheme.shouldUseDarkTheme()
            else -> isSystemInDarkTheme()
        }
    }
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
            val pagerState = rememberPagerState(pageCount = { 3 })

            Column {
                // 读取生态系统开关状态，强制 Compose 在此处订阅依赖
                val isEcoEnabled = fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.isEcoEnabled
                
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp)
                ) { page ->
                    when (page) {
                        0 -> {
                            val timePhase = fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme.getCurrentTimePhase()
                            val dynamicGreeting = when (timePhase) {
                                "dawn" -> "🌅 晨光微露，早安！"
                                "day" -> "☀️ 艳阳高照，午安！"
                                "sunset" -> "🌇 晚霞绚烂，傍晚好！"
                                "midnight" -> "🌌 夜深了，请注意休息。"
                                else -> "🎉 欢迎使用 Sesame-TK"
                            }
                            val holidayTitle = holidayColors?.title ?: dynamicGreeting
                            val holidayStory = holidayColors?.story ?: "“岁月静好，芝麻常伴。” 模块已正常加载，愿您今天也有好心情！保持童心与好奇，探索生活的精彩。"

                            val coroutineScope = rememberCoroutineScope()
                            var animType by remember { mutableStateOf(0) }
                            val iconScale = remember { androidx.compose.animation.core.Animatable(1f) }
                            val iconRotation = remember { androidx.compose.animation.core.Animatable(0f) }
                            val iconFlip = remember { androidx.compose.animation.core.Animatable(0f) }

                            var animalAnimType by remember { mutableStateOf(0) }
                            val animalScale = remember { androidx.compose.animation.core.Animatable(1f) }
                            val animalRotation = remember { androidx.compose.animation.core.Animatable(0f) }
                            val animalFlip = remember { androidx.compose.animation.core.Animatable(0f) }

                            val sky = fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme.getSkyColors()
                            val isSchedule = darkMode == "schedule" || (darkMode == "auto" && themeMode == "auto")
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isSchedule) {
                                            Modifier.background(
                                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                    colors = listOf(sky.top.copy(alpha = 0.25f), sky.bottom.copy(alpha = 0.12f))
                                                )
                                            )
                                        } else Modifier
                                    )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = containerColor,
                                        shape = CircleShape,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (isEcoEnabled && fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentLineIcon != null) {
                                                Box(
                                                    modifier = Modifier.pointerInput(Unit) {
                                                        detectTapGestures(
                                                            onTap = { 
                                                                if (animType == 0) {
                                                                    animType = (1..3).random()
                                                                    coroutineScope.launch {
                                                                        when (animType) {
                                                                            1 -> { iconScale.animateTo(0.6f, androidx.compose.animation.core.tween(100)); iconScale.animateTo(1.3f, androidx.compose.animation.core.spring(dampingRatio = 0.4f)); iconScale.animateTo(1f) }
                                                                            2 -> { iconRotation.animateTo(-30f, androidx.compose.animation.core.tween(50)); iconRotation.animateTo(30f, androidx.compose.animation.core.tween(100)); iconRotation.animateTo(0f, androidx.compose.animation.core.spring()) }
                                                                            3 -> { iconFlip.animateTo(360f, androidx.compose.animation.core.tween(500)); iconFlip.snapTo(0f) }
                                                                        }
                                                                        animType = 0
                                                                    }
                                                                }
                                                            },
                                                            onLongPress = { showLinePicker = true }
                                                        )
                                                    },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    coil.compose.AsyncImage(
                                                        model = coil.request.ImageRequest.Builder(context)
                                                            .data(fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentLineIcon)
                                                            .apply {
                                                                if (fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentLineIcon?.endsWith(".svg", ignoreCase = true) == true) {
                                                                    decoderFactory(coil.decode.SvgDecoder.Factory())
                                                                }
                                                            }
                                                            .build(),
                                                        contentDescription = "Ecosystem Line Icon",
                                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                                                        alpha = 0.8f,
                                                        modifier = Modifier.size(20.dp).graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value; rotationZ = iconRotation.value; rotationY = iconFlip.value }
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    Icons.Rounded.Celebration,
                                                    contentDescription = null,
                                                    tint = brandColor,
                                                    modifier = Modifier.size(20.dp).graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value; rotationZ = iconRotation.value; rotationY = iconFlip.value }.pointerInput(Unit) {
                                                        detectTapGestures(
                                                            onTap = { 
                                                                if (animType == 0) {
                                                                    animType = (1..3).random()
                                                                    coroutineScope.launch {
                                                                        when (animType) {
                                                                            1 -> { iconScale.animateTo(0.6f, androidx.compose.animation.core.tween(100)); iconScale.animateTo(1.3f, androidx.compose.animation.core.spring(dampingRatio = 0.4f)); iconScale.animateTo(1f) }
                                                                            2 -> { iconRotation.animateTo(-30f, androidx.compose.animation.core.tween(50)); iconRotation.animateTo(30f, androidx.compose.animation.core.tween(100)); iconRotation.animateTo(0f, androidx.compose.animation.core.spring()) }
                                                                            3 -> { iconFlip.animateTo(360f, androidx.compose.animation.core.tween(500)); iconFlip.snapTo(0f) }
                                                                        }
                                                                        animType = 0
                                                                    }
                                                                }
                                                            },
                                                            onLongPress = { showLinePicker = true }
                                                        )
                                                    }
                                                )
                                            }
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(containerColor.copy(alpha = 0.25f))
                                    )
                                    Column(modifier = Modifier.padding(12.dp)) {
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
                                    if (isEcoEnabled) {
                                        Box(
                                            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 10.dp).pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = { 
                                                        if (animalAnimType == 0) {
                                                            animalAnimType = (1..3).random()
                                                            coroutineScope.launch {
                                                                when (animalAnimType) {
                                                                    1 -> { animalScale.animateTo(0.6f, androidx.compose.animation.core.tween(100)); animalScale.animateTo(1.3f, androidx.compose.animation.core.spring(dampingRatio = 0.4f)); animalScale.animateTo(1f) }
                                                                    2 -> { animalRotation.animateTo(-30f, androidx.compose.animation.core.tween(50)); animalRotation.animateTo(30f, androidx.compose.animation.core.tween(100)); animalRotation.animateTo(0f, androidx.compose.animation.core.spring()) }
                                                                    3 -> { animalFlip.animateTo(360f, androidx.compose.animation.core.tween(500)); animalFlip.snapTo(0f) }
                                                                }
                                                                animalAnimType = 0
                                                            }
                                                        }
                                                    },
                                                    onLongPress = { showAnimalPicker = true }
                                                )
                                            }
                                        ) {
                                            fansirsqi.xposed.sesame.ui.theme.app.EcosystemCardDecorator(modifier = Modifier.graphicsLayer { scaleX = animalScale.value; scaleY = animalScale.value; rotationZ = animalRotation.value; rotationY = animalFlip.value })
                                        }
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
                        2 -> {
                            val coroutineScope = rememberCoroutineScope()
                            var ecoAnimType by remember { mutableStateOf(0) }
                            val ecoScale = remember { androidx.compose.animation.core.Animatable(1f) }
                            val ecoRotation = remember { androidx.compose.animation.core.Animatable(0f) }
                            val ecoFlip = remember { androidx.compose.animation.core.Animatable(0f) }

                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = containerColor,
                                        shape = CircleShape,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentAnimal != null) {
                                                Box(
                                                    modifier = Modifier.pointerInput(Unit) {
                                                        detectTapGestures(
                                                            onTap = { 
                                                                fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.shuffle()
                                                                if (ecoAnimType == 0) {
                                                                    ecoAnimType = (1..3).random()
                                                                    coroutineScope.launch {
                                                                        when (ecoAnimType) {
                                                                            1 -> { ecoScale.animateTo(0.6f, androidx.compose.animation.core.tween(100)); ecoScale.animateTo(1.3f, androidx.compose.animation.core.spring(dampingRatio = 0.4f)); ecoScale.animateTo(1f) }
                                                                            2 -> { ecoRotation.animateTo(-30f, androidx.compose.animation.core.tween(50)); ecoRotation.animateTo(30f, androidx.compose.animation.core.tween(100)); ecoRotation.animateTo(0f, androidx.compose.animation.core.spring()) }
                                                                            3 -> { ecoFlip.animateTo(360f, androidx.compose.animation.core.tween(500)); ecoFlip.snapTo(0f) }
                                                                        }
                                                                        ecoAnimType = 0
                                                                    }
                                                                }
                                                            },
                                                            onLongPress = { showAnimalPicker = true }
                                                        )
                                                    },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    coil.compose.AsyncImage(
                                                        model = coil.request.ImageRequest.Builder(context)
                                                            .data(fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentAnimal)
                                                            .apply {
                                                                if (fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentAnimal?.endsWith(".svg", ignoreCase = true) == true) {
                                                                    decoderFactory(coil.decode.SvgDecoder.Factory())
                                                                }
                                                            }
                                                            .build(),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = ecoScale.value; scaleY = ecoScale.value; rotationZ = ecoRotation.value; rotationY = ecoFlip.value }
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    Icons.Rounded.Eco,
                                                    contentDescription = null,
                                                    tint = brandColor,
                                                    modifier = Modifier.size(24.dp).graphicsLayer { scaleX = ecoScale.value; scaleY = ecoScale.value; rotationZ = ecoRotation.value; rotationY = ecoFlip.value }.pointerInput(Unit) {
                                                        detectTapGestures(
                                                            onTap = { 
                                                                fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.shuffle()
                                                                if (ecoAnimType == 0) {
                                                                    ecoAnimType = (1..3).random()
                                                                    coroutineScope.launch {
                                                                        when (ecoAnimType) {
                                                                            1 -> { ecoScale.animateTo(0.6f, androidx.compose.animation.core.tween(100)); ecoScale.animateTo(1.3f, androidx.compose.animation.core.spring(dampingRatio = 0.4f)); ecoScale.animateTo(1f) }
                                                                            2 -> { ecoRotation.animateTo(-30f, androidx.compose.animation.core.tween(50)); ecoRotation.animateTo(30f, androidx.compose.animation.core.tween(100)); ecoRotation.animateTo(0f, androidx.compose.animation.core.spring()) }
                                                                            3 -> { ecoFlip.animateTo(360f, androidx.compose.animation.core.tween(500)); ecoFlip.snapTo(0f) }
                                                                        }
                                                                        ecoAnimType = 0
                                                                    }
                                                                }
                                                            },
                                                            onLongPress = { showAnimalPicker = true }
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "生态守护舱",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                        Text(
                                            text = "大自然的馈赠，生命的气息",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor.copy(alpha = 0.6f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    androidx.compose.material3.Switch(
                                        checked = isEcoEnabled,
                                        onCheckedChange = { fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.saveEcoEnabled(it) },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(
                                            checkedThumbColor = brandColor,
                                            checkedTrackColor = brandColor.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    containerColor.copy(alpha = 0.4f),
                                                    containerColor.copy(alpha = 0.1f)
                                                )
                                            )
                                        )
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = { 
                                                    fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.shuffle()
                                                    if (ecoAnimType == 0) {
                                                        ecoAnimType = (1..3).random()
                                                        coroutineScope.launch {
                                                            when (ecoAnimType) {
                                                                1 -> { ecoScale.animateTo(0.6f, androidx.compose.animation.core.tween(100)); ecoScale.animateTo(1.3f, androidx.compose.animation.core.spring(dampingRatio = 0.4f)); ecoScale.animateTo(1f) }
                                                                2 -> { ecoRotation.animateTo(-30f, androidx.compose.animation.core.tween(50)); ecoRotation.animateTo(30f, androidx.compose.animation.core.tween(100)); ecoRotation.animateTo(0f, androidx.compose.animation.core.spring()) }
                                                                3 -> { ecoFlip.animateTo(360f, androidx.compose.animation.core.tween(500)); ecoFlip.snapTo(0f) }
                                                            }
                                                            ecoAnimType = 0
                                                        }
                                                    }
                                                },
                                                onLongPress = { showAnimalPicker = true }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                                    val breathScale by infiniteTransition.animateFloat(
                                        initialValue = 0.95f,
                                        targetValue = 1.05f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(2000, easing = androidx.compose.animation.core.EaseInOutSine),
                                            repeatMode = RepeatMode.Reverse
                                        )
                                    )
                                    
                                    val finalScale = ecoScale.value * breathScale
                                    
                                    if (fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentAnimal != null) {
                                        coil.compose.AsyncImage(
                                            model = coil.request.ImageRequest.Builder(context)
                                                .data(fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentAnimal)
                                                .apply {
                                                    if (fansirsqi.xposed.sesame.ui.theme.app.EcosystemManager.currentAnimal?.endsWith(".svg", ignoreCase = true) == true) {
                                                        decoderFactory(coil.decode.SvgDecoder.Factory())
                                                    }
                                                }
                                                .build(),
                                            contentDescription = "Ecosystem Centerpiece",
                                            modifier = Modifier.size(80.dp).graphicsLayer { scaleX = finalScale; scaleY = finalScale; rotationZ = ecoRotation.value; rotationY = ecoFlip.value }
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.Eco,
                                            contentDescription = null,
                                            tint = brandColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(80.dp).graphicsLayer { scaleX = finalScale; scaleY = finalScale; rotationZ = ecoRotation.value; rotationY = ecoFlip.value }
                                        )
                                    }
                                    
                                    Text(
                                        text = "轻触切换 • 长按图鉴",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor.copy(alpha = 0.4f),
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                                    )
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
                    repeat(3) { iteration ->
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
                        // ========== 实时预览卡片 ==========
                        LivePreviewCard(holidayColors, darkTheme)

                        // ========== 暗黑模式 ==========
                        Text("暗黑模式：", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "auto" to "跟随系统",
                                "light" to "浅色",
                                "dark" to "深色",
                                "schedule" to "日出日落"
                            ).forEach { (mode, label) ->
                                val selected = darkMode == mode
                                Surface(
                                    color = if (selected) brandColor else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).clickable {
                                        darkMode = mode
                                        HolidayTheme.setDarkMode(mode)
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (darkMode == "schedule" || (darkMode == "auto" && themeMode == "auto")) {
                            val timePhase = fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme.getCurrentTimePhase()
                            val phaseName = when (timePhase) {
                                "dawn" -> "🌅 晨曦"
                                "day" -> "☀️ 白昼"
                                "sunset" -> "🌇 晚霞"
                                "midnight" -> "🌌 子夜"
                                else -> "自动"
                            }
                            Text(
                                "当前时段: $phaseName", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = brandColor,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))

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

                                // 🎲 随机配色按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        color = brandColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable {
                                            val r = kotlin.random.Random
                                            val randomHex = String.format("#FF%06X", 0xFFFFFF and r.nextInt())
                                            customColor = randomHex
                                            themeMode = "custom"
                                            HolidayTheme.saveThemeConfigEx("custom", customColor, useHolidayIcons, useAnimalIcons)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Rounded.Palette,
                                                contentDescription = "Random Color",
                                                tint = brandColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("随机配色", fontSize = 12.sp, color = brandColor, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
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
        if (showLinePicker) {
            fansirsqi.xposed.sesame.ui.theme.app.SVGSelectorDialog(type = "lines") { showLinePicker = false }
        }
        if (showAnimalPicker) {
            fansirsqi.xposed.sesame.ui.theme.app.SVGSelectorDialog(type = "animal") { showAnimalPicker = false }
        }
        if (showPlantPicker) {
            fansirsqi.xposed.sesame.ui.theme.app.SVGSelectorDialog(type = "plants") { showPlantPicker = false }
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

    fun showInfo(vid: String, activeUser: String = "未登录"): Map<String, String> {
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

        val infoMap = LinkedHashMap<String, String>()
        infoMap["当前账号"] = activeUser
        infoMap["Product"] = "${Build.MANUFACTURER} ${Build.PRODUCT}"
        infoMap["Device"] = getDeviceName()
        infoMap["Android Version"] = "${Build.VERSION.RELEASE} SDK (${Build.VERSION.SDK_INT})"
        infoMap["OS Build"] = "${Build.DISPLAY}"
        infoMap["Verify ID"] = vid
        infoMap["Module Version"] = "${BuildConfig.VERSION}.${BuildConfig.BUILD_TYPE} 📦"
        infoMap["Module Build"] = "${BuildConfig.BUILD_DATE} ${BuildConfig.BUILD_TIME} ⏰"
        return infoMap
    }
}
