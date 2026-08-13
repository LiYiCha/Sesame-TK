package fansirsqi.xposed.sesame.ui.theme.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import fansirsqi.xposed.sesame.ui.theme.ThemeInfo
import fansirsqi.xposed.sesame.ui.theme.ThemeOperation
import fansirsqi.xposed.sesame.ui.theme.ThemeViewModel

/**
 * 主题中心主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    viewModel: ThemeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val operationStates by viewModel.operationStates.collectAsState()

    // 导航状态：当前查看的主题详情
    var selectedThemeForDetail by remember { mutableStateOf<ThemeInfo?>(null) }

    // 主题列表展开状态
    var isThemeListExpanded by remember { mutableStateOf(false) }
    val maxCollapsedThemes = 5

    // 如果选中了主题，显示详情页面
    selectedThemeForDetail?.let { theme ->
        ThemeDetailScreen(
            themeInfo = theme,
            viewModel = viewModel,
            onBack = { selectedThemeForDetail = null }
        )
        return
    }

    // ZIP 文件选择器
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importTheme(it) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // 目录选择器
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.importThemeFromDirectory(it) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 20.dp)
            ) {
            // 操作卡片
            item {
                OperationsCard(
                    operationStates = operationStates,
                    onExecute = { operation ->
                        viewModel.executeOperation(operation) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            // 主题列表标题
            item {
                Text(
                    text = "可用主题",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // 主题列表
            if (state.availableThemes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "暂无可用主题\n请导入主题包或从支付宝导出现有主题",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // 根据展开状态决定显示的主题数量
                val displayThemes = if (isThemeListExpanded) {
                    state.availableThemes
                } else {
                    state.availableThemes.take(maxCollapsedThemes)
                }

                items(displayThemes) { theme ->
                    ThemeItem(
                        theme = theme,
                        onSelect = { selectedThemeForDetail = theme },
                        onDelete = {
                            viewModel.deleteTheme(theme.themeId) { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 如果主题数量超过限制，显示展开/收起按钮
                if (state.availableThemes.size > maxCollapsedThemes) {
                    item {
                        TextButton(
                            onClick = { isThemeListExpanded = !isThemeListExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isThemeListExpanded) {
                                    "收起 ▲"
                                } else {
                                    "查看全部 ${state.availableThemes.size} 个主题 ▼"
                                },
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 导入主题按钮
            item {
                Button(
                    onClick = { zipPickerLauncher.launch("application/zip") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("导入主题包 (ZIP)")
                }
            }

            // 导入主题目录按钮
            item {
                Button(
                    onClick = { directoryPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("导入主题目录")
                }
            }

            // 使用说明
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "使用说明",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "1. 导出：从支付宝导出现有主题到SD卡\n" +
                                    "2. 导入：从ZIP文件或目录导入新主题\n" +
                                    "3. 选择：点击主题卡片选择要应用的主题\n" +
                                    "4. 更新：将选中的主题推送到支付宝\n" +
                                    "5. 重启支付宝查看效果",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // 主题存储位置提示
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "主题存储位置",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "/storage/emulated/0/Android/media/\ncom.eg.android.AlipayGphone/\n000_HOHO_THEME_CENTER/themes",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 加载指示器
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
}

/**
 * 现代化主题卡片
 *
 * 采用扩展功能页面的样式设计
 * 支持预览图片显示和选中状态动画
 */
@Composable
private fun ThemeItem(
    theme: ThemeInfo,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    // 动画状态
    val animatedElevation by animateDpAsState(
        targetValue = if (theme.isSelected) 8.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = animatedElevation,
                shape = RoundedCornerShape(20.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 预览图片
            if (theme.previewImagePath != null) {
                Image(
                    painter = rememberAsyncImagePainter(theme.previewImagePath),
                    contentDescription = "主题预览",
                    modifier = Modifier
                        .size(80.dp, 60.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 占位符
                Box(
                    modifier = Modifier
                        .size(80.dp, 60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "主题",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 主题信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (theme.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = theme.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            // 删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除主题",
                    tint = MaterialTheme.colorScheme.error
                )
            }

            // 选中指示器
            if (theme.isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}
