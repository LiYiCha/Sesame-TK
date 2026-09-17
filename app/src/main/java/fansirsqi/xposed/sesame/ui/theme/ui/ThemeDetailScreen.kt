package fansirsqi.xposed.sesame.ui.theme.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import fansirsqi.xposed.sesame.ui.theme.ThemeInfo
import fansirsqi.xposed.sesame.ui.theme.ThemeViewModel
import fansirsqi.xposed.sesame.ui.theme.alipay.AlipayThemeParser
import fansirsqi.xposed.sesame.ui.theme.alipay.ParsedAlipayTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 主题详情界面
 *
 * 设计：Header 大图 → 基本信息 → 真实资源预览网格 → 模拟 TabBar → 操作按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDetailScreen(
    themeInfo: ThemeInfo,
    viewModel: ThemeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 异步解析主题详情
    var themeDetail by remember { mutableStateOf<ParsedAlipayTheme?>(null) }
    var isParsing by remember { mutableStateOf(false) }

    LaunchedEffect(themeInfo.themeId) {
        isParsing = true
        val dir = File(themeInfo.storagePath)
        themeDetail = withContext(Dispatchers.IO) {
            AlipayThemeParser.parseThemeDirectory(dir)
        }
        isParsing = false
    }

    // 删除确认对话框
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header 大图
            DetailHeader(themeInfo = themeInfo, detail = themeDetail, isParsing = isParsing)

            // 2. 基本信息卡片
            BasicInfoCard(themeInfo = themeInfo, detail = themeDetail)

            // 3. 资源预览网格（真实解析）
            if (themeDetail != null) {
                ResourcePreviewSection(detail = themeDetail!!, storagePath = themeInfo.storagePath)
            }

            // 4. 模拟 TabBar
            if (themeDetail != null) {
                MockTabBar(detail = themeDetail!!)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. 操作按钮
            DetailActionBar(
                isSelected = themeInfo.isSelected,
                onSwitch = {
                    viewModel.selectTheme(themeInfo.themeId)
                    onBack()
                },
                onPreview = { /* TODO: 打开预览 */ },
                onDelete = { showDeleteDialog = true }
            )
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = {
                Text("确定要删除主题「${themeInfo.name}」吗？此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTheme(themeInfo.themeId) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            if (success) onBack()
                        }
                    }
                ) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════
// 1. Header 大图区
// ═══════════════════════════════════════════════

@Composable
private fun DetailHeader(
    themeInfo: ThemeInfo,
    detail: ParsedAlipayTheme?,
    isParsing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // 大图区域（用 headerBg / logo 占位）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            ) {
                // 如果有 logo，显示在中间
                detail?.ltpLogoBitmap?.let { logo ->
                    Image(
                        bitmap = logo.asImageBitmap(),
                        contentDescription = "主题 Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                if (isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 名称区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = themeInfo.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (themeInfo.isSelected) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text("使用中")
                                }
                            }
                        )
                    }
                }

                Text(
                    text = themeInfo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// 2. 基本信息卡片
// ═══════════════════════════════════════════════

@Composable
private fun BasicInfoCard(
    themeInfo: ThemeInfo,
    detail: ParsedAlipayTheme?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "基本信息",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            InfoRow(label = "主题 ID", value = themeInfo.themeId)
            InfoRow(label = "存储路径", value = themeInfo.storagePath, monospace = true)

            detail?.let { d ->
                if (d.id.isNotEmpty()) {
                    InfoRow(label = "皮肤 ID", value = d.id)
                }
                InfoRow(label = "资源数量", value = "${d.tabIcons.size + d.actionIcons.size} 项")
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    showColorSwatch: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 72.dp),
            maxLines = 1
        )

        if (showColorSwatch) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color = value.parseAlipayColor())
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ═══════════════════════════════════════════════
// 3. 资源预览网格（真实解析）
// ═══════════════════════════════════════════════

@Composable
private fun ResourcePreviewSection(
    detail: ParsedAlipayTheme,
    storagePath: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "资源预览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${detail.tabIcons.size + detail.actionIcons.size} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 构建可显示的资源列表
            val resources = buildResourceList(detail, storagePath)

            if (resources.isEmpty()) {
                Text(
                    text = "暂无可预览的图片资源",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 3 列网格
                val rows = resources.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { res ->
                                ResourceCell(
                                    filePath = res.first,
                                    label = res.second,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // 补齐空位
                            repeat(3 - row.size) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 从 ParsedAlipayTheme 构建可显示的资源文件列表
 * 返回 (完整路径, 标签) 对
 */
private fun buildResourceList(
    detail: ParsedAlipayTheme,
    storagePath: String
): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val dir = File(storagePath)

    // 各 tab 的 normal + selected 图标
    detail.tabIcons.forEach { key, _ ->
        val fileName = key.removePrefix("tab_bar_").removeSuffix("_normal").removeSuffix("_selected")
        val isNormal = key.endsWith("_normal")
        val label = when (fileName) {
            "home" -> "首页"
            "wealth" -> "理财"
            "life" -> "生活"
            "msg" -> "消息"
            "mime" -> "我的"
            else -> fileName
        }
        val iconLabel = if (isNormal) "$label 图标" else "$label 选中"
        val file = File(dir, key)
        if (file.exists()) {
            result.add(file.absolutePath to iconLabel)
        }
    }

    // 首页按钮图标
    detail.actionIcons.forEach { key, _ ->
        val label = when (key) {
            "home_scan_icon" -> "扫一扫"
            "home_pay_icon" -> "付款码"
            "home_collect_icon" -> "收钱"
            "home_transport_icon" -> "出行"
            "home_pocket_icon" -> "卡包"
            else -> key
        }
        val file = File(dir, key)
        if (file.exists()) {
            result.add(file.absolutePath to "$label 图标")
        }
    }

    // Header 背景
    detail.headerBgBitmap?.let {
        val file = File(dir, "home_navi_bg")
        if (file.exists()) {
            result.add(file.absolutePath to "首页背景")
        }
    }

    return result
}

@Composable
private fun ResourceCell(
    filePath: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // 尝试加载图片
            val file = File(filePath)
            if (file.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(filePath),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════
// 4. 模拟 TabBar
// ═══════════════════════════════════════════════

@Composable
private fun MockTabBar(detail: ParsedAlipayTheme) {
    // Tab 名称 → 图标 key 前缀，与 AlipayThemeParser 的 tabIconKeys 对应
    val tabs = listOf(
        "首页" to "tab_bar_home_icon",
        "理财" to "tab_bar_wealth_icon",
        "生活" to "tab_bar_life_icon",
        "消息" to "tab_bar_msg_icon",
        "我的" to "tab_bar_mime_icon"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "模拟 TabBar 效果",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 模拟底部 TabBar：优先用主题自带的 TabBar 背景图，否则用主题色
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = detail.themeColor
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    detail.tabBarBgBitmap?.let { bg ->
                        Image(
                            bitmap = bg.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, (label, iconPrefix) ->
                            TabBarMockItem(
                                label = label,
                                normalIcon = detail.tabIcons["${iconPrefix}_normal"],
                                selectedIcon = detail.tabIcons["${iconPrefix}_selected"],
                                isSelected = index == 0,
                                textColorSelected = detail.textColorSelected,
                                textColorNormal = detail.textColorNormal,
                                fallbackColor = detail.themeColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBarMockItem(
    label: String,
    normalIcon: android.graphics.Bitmap?,
    selectedIcon: android.graphics.Bitmap?,
    isSelected: Boolean,
    textColorSelected: Color,
    textColorNormal: Color,
    fallbackColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 图标：优先用主题真实图标，缺失时用首字母占位
        val icon = if (isSelected) selectedIcon ?: normalIcon else normalIcon
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        color = if (isSelected) {
                            fallbackColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) textColorSelected else textColorNormal
        )
    }
}

// ═══════════════════════════════════════════════
// 5. 操作按钮
// ═══════════════════════════════════════════════

@Composable
private fun DetailActionBar(
    isSelected: Boolean,
    onSwitch: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            OutlinedButton(
                onClick = onPreview,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("预览效果")
            }
        } else {
            Button(
                onClick = onSwitch,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("应用此主题")
            }
        }

        OutlinedButton(
            onClick = onDelete,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "删除",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ═══════════════════════════════════════════════
// 辅助
// ═══════════════════════════════════════════════

/**
 * 将 Compose Color 的 toString() 解析为 Color
 */
private fun String.parseAlipayColor(): Color =
    try {
        val hex = removePrefix("Color(0x").removeSuffix(")").trim()
        val argb = hex.toLong(16)
        Color(argb)
    } catch (e: Exception) {
        Color(0xFF78CFF4)
    }
