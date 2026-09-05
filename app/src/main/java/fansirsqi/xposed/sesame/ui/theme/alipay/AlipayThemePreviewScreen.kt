package fansirsqi.xposed.sesame.ui.theme.alipay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 支付宝主题在 Sesame-TK 中的实机 Compose 渲染效果组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlipayThemePreviewScreen(
    theme: ParsedAlipayTheme,
    onBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var selectedTab by remember { mutableIntStateOf(0) }
    var useMeHeader by remember { mutableStateOf(false) }

    val activeThemeColor = if (isDark) theme.darkThemeColor else theme.themeColor
    val activeHeaderBitmap = if (useMeHeader) {
        if (isDark) theme.darkMeHeaderBgBitmap else theme.meHeaderBgBitmap
    } else {
        if (isDark) theme.darkHeaderBgBitmap else theme.headerBgBitmap
    }
    val activeTabBarBitmap = if (isDark) theme.darkTabBarBgBitmap else theme.tabBarBgBitmap

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(theme.name.ifEmpty { "主题效果预览" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { useMeHeader = !useMeHeader }) {
                        Text(if (useMeHeader) "切首页顶栏" else "切我的顶栏")
                    }
                }
            )
        },
        bottomBar = {
            // 带有主题背景的 Bottom Navigation Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            ) {
                // 背景图
                if (activeTabBarBitmap != null) {
                    Image(
                        bitmap = activeTabBarBitmap.asImageBitmap(),
                        contentDescription = "TabBar Background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {}
                }

                // Tab 按钮列表
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple(0, "首页", "tab_bar_home_icon"),
                        Triple(1, "理财", "tab_bar_wealth_icon"),
                        Triple(2, "生活", "tab_bar_life_icon"),
                        Triple(3, "消息", "tab_bar_msg_icon"),
                        Triple(4, "我的", "tab_bar_mime_icon")
                    )

                    tabs.forEach { (index, title, iconPrefix) ->
                        val isSelected = (selectedTab == index)
                        val iconKey = if (isSelected) "${iconPrefix}_selected" else "${iconPrefix}_normal"
                        val iconBmp = theme.tabIcons[iconKey]

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (iconBmp != null) {
                                Image(
                                    bitmap = iconBmp.asImageBitmap(),
                                    contentDescription = title,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) activeThemeColor else if (isDark) Color(0xFFCCCCCC) else theme.textColorNormal
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 顶部 Header 区（保持 750:412 原始比例 206dp，包含插画与操作按钮）
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(206.dp)
                ) {
                    if (activeHeaderBitmap != null) {
                        Image(
                            bitmap = activeHeaderBitmap.asImageBitmap(),
                            contentDescription = "Header Background",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillWidth
                        )
                    }

                    // 覆盖快捷操作按钮
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val actions = listOf(
                            Pair("扫一扫", "home_scan_icon"),
                            Pair("付款码", "home_pay_icon"),
                            Pair("收钱", "home_collect_icon"),
                            Pair("出行", "home_transport_icon"),
                            Pair("卡包", "home_pocket_icon")
                        )

                        actions.forEach { (label, key) ->
                            val bmp = theme.actionIcons[key]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = label,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 2. 主题能量卡片（融入渐变与主题色）
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(theme.gradientStart, theme.gradientEnd)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "今日已收取能量",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${theme.name} 定制版",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp
                                    )
                                }
                                theme.ltpLogoBitmap?.let { logo ->
                                    Image(
                                        bitmap = logo.asImageBitmap(),
                                        contentDescription = "Logo",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "+ 862 g",
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // 3. 任务卡片展示
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "自动化功能状态",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val taskList = listOf(
                            Pair("🌲 蚂蚁森林双击收", "下次查询: 07:00:00 (就绪)"),
                            Pair("🐔 蚂蚁庄园自动喂鸡", "剩余饲料: 1250g · 进食中"),
                            Pair("🌊 神奇海洋寻宝", "今日巡护已完成 (4/4)")
                        )

                        taskList.forEach { (taskName, taskDesc) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(taskName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(taskDesc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = activeThemeColor,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "运行中",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
