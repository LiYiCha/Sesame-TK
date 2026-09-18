package fansirsqi.xposed.sesame.ui.theme.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import fansirsqi.xposed.sesame.ui.theme.ThemeInfo
import fansirsqi.xposed.sesame.ui.theme.ThemeOperation
import fansirsqi.xposed.sesame.ui.theme.ThemeViewModel

/**
 * 主题中心主界面
 *
 * 设计：当前主题 Hero 卡 → 快速操作行 → 2 列主题网格 / 空态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    viewModel: ThemeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // 当前查看的主题详情
    var selectedThemeForDetail by remember { mutableStateOf<ThemeInfo?>(null) }

    // 待确认删除的主题
    var themeToDelete by remember { mutableStateOf<ThemeInfo?>(null) }

    // 更多操作菜单
    var showMoreMenu by remember { mutableStateOf(false) }

    // 导入 ZIP 文件
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importTheme(it) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // 导入目录
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.importThemeFromDirectory(it) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // 当前选中的主题（用于 Hero 卡）
    val selectedTheme = state.availableThemes.find { it.isSelected }

    BackHandler {
        when {
            selectedThemeForDetail != null -> selectedThemeForDetail = null
            else -> onBack()
        }
    }

    // 详情页覆盖
    selectedThemeForDetail?.let { theme ->
        ThemeDetailScreen(
            themeInfo = theme,
            viewModel = viewModel,
            onBack = { selectedThemeForDetail = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (state.availableThemes.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showMoreMenu = !showMoreMenu }) {
                                Text(
                                    "⋮",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (showMoreMenu) {
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("导出主题") },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.executeThemeAction(
                                                ThemeOperation.EXPORT
                                            ) { _, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("更新主题缓存") },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.executeThemeAction(
                                                ThemeOperation.UPDATE
                                            ) { _, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除主题缓存") },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.executeThemeAction(
                                                ThemeOperation.DELETE
                                            ) { _, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        // 关闭菜单的外层点击
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .let {
                    if (showMoreMenu) {
                        it.pointerDown {
                            showMoreMenu = false
                            true
                        }
                    } else {
                        it
                    }
                }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 当前主题 Hero 卡
                if (selectedTheme != null) {
                    item {
                        CurrentThemeHeroCard(theme = selectedTheme)
                    }
                }

                // 2. 快速操作行
                item {
                    QuickActionRow(
                        onExport = {
                            viewModel.executeThemeAction(
                                ThemeOperation.EXPORT
                            ) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onImportZip = { zipPickerLauncher.launch("application/zip") },
                        onImportDir = { directoryPickerLauncher.launch(null) }
                    )
                }

                // 3. 列表标题
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "可用主题",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.availableThemes.isNotEmpty()) {
                            Text(
                                text = "${state.availableThemes.size} 个",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 4. 空态 or 网格
                if (state.availableThemes.isEmpty()) {
                    item {
                        EmptyThemeCard(
                            onExport = {
                                viewModel.executeThemeAction(
                                    ThemeOperation.EXPORT
                                ) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onImportZip = { zipPickerLauncher.launch("application/zip") },
                            onImportDir = { directoryPickerLauncher.launch(null) }
                        )
                    }
                } else {
                    item {
                        ThemeGrid(
                            themes = state.availableThemes,
                            onSelect = { selectedThemeForDetail = it },
                            onDelete = { theme ->
                                themeToDelete = theme
                            }
                        )
                    }
                }
            }

            // 加载指示器
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // 错误提示
            state.errorMessage?.let { error ->
                LaunchedEffect(error) {
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }

    // 删除二次确认对话框
    themeToDelete?.let { theme ->
        AlertDialog(
            onDismissRequest = { themeToDelete = null },
            title = { Text("确认删除") },
            text = {
                Text("确定要删除主题「${theme.name}」吗？将删除主题文件夹内的全部文件，此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        themeToDelete = null
                        viewModel.deleteTheme(theme.themeId) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { themeToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 当前主题 Hero 卡
 *
 * 突出显示正在使用的主题，带 "使用中" 角标和主题色描边
 */
@Composable
private fun CurrentThemeHeroCard(theme: ThemeInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "当前主题",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    letterSpacing = 1.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "使用中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = theme.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = theme.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
                    .copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 快速操作行：导出 / 导入ZIP / 导入目录
 */
@Composable
private fun QuickActionRow(
    onExport: () -> Unit,
    onImportZip: () -> Unit,
    onImportDir: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionChip(
            text = "导出",
            onClick = onExport,
            modifier = Modifier.weight(1f)
        )
        ActionChip(
            text = "导入 ZIP",
            onClick = onImportZip,
            modifier = Modifier.weight(1f)
        )
        ActionChip(
            text = "导入目录",
            onClick = onImportDir,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 单个操作 Chip
 */
@Composable
private fun ActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 空态卡片：引导导入
 */
@Composable
private fun EmptyThemeCard(
    onExport: () -> Unit,
    onImportZip: () -> Unit,
    onImportDir: () -> Unit
) {
    // 引导图标上方的导入方式选择菜单
    var showImportMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 引导图标（可点击，弹出导入方式菜单）
            Box {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        )
                        .clickable { showImportMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Light
                    )
                }

                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("导入 ZIP 皮肤包") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showImportMenu = false
                            onImportZip()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导入主题目录") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            showImportMenu = false
                            onImportDir()
                        }
                    )
                }
            }

            Text(
                text = "暂无主题",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "从支付宝导出已有主题，或导入 ZIP 皮肤包",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onExport,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("一键导出")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportZip,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("导入 ZIP")
                }
                OutlinedButton(
                    onClick = onImportDir,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("导入目录")
                }
            }
        }
    }
}

/**
 * 主题 2 列网格
 *
 * 双列网格布局让卡片按内容自适应高度
 */
@Composable
private fun ThemeGrid(
    themes: List<ThemeInfo>,
    onSelect: (ThemeInfo) -> Unit,
    onDelete: (ThemeInfo) -> Unit
) {
    // 非懒加载双列布局：外层 LazyColumn 已负责滚动，
    // 嵌套 LazyVerticalStaggeredGrid 会因无界高度约束崩溃
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val columns = themes.chunked((themes.size + 1) / 2)
        columns.forEach { columnThemes ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                columnThemes.forEach { theme ->
                    ThemeGridCard(
                        theme = theme,
                        onSelect = { onSelect(theme) },
                        onDelete = { onDelete(theme) }
                    )
                }
            }
        }
    }
}

/**
 * 单个主题网格卡片
 */
@Composable
private fun ThemeGridCard(
    theme: ThemeInfo,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (theme.isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (theme.isSelected) 4.dp else 2.dp
        )
    ) {
        Column {
            // 封面图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (theme.previewImagePath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(theme.previewImagePath),
                        contentDescription = "主题封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 占位：渐变 + 图标
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎨",
                            fontSize = 32.sp
                        )
                    }
                }

                // 选中角标
                if (theme.isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "使用中",
                            modifier = Modifier
                                .size(12.dp)
                                .padding(4.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // 信息区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = theme.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 详情按钮
                    TextButton(
                        onClick = onSelect,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "查看详情",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 删除图标
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// 辅助：点击任意位置关闭菜单
private fun Modifier.pointerDown(
    onDown: () -> Boolean
): Modifier = clickable {
    onDown()
}
