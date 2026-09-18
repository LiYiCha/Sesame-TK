package fansirsqi.xposed.sesame.ui.skin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/**
 * 皮肤设置主屏幕（精简版）
 *
 * 网格为主体，底层操作折叠到右上角「⋮」菜单，会员等级与总开关合并为一张设置卡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinScreen(
    viewModel: SkinViewModel,
    onImport: () -> Unit,
    onImportDirectory: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var confirmDelete by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // 皮肤系统开关
    val skinEnabled = state.operationStates[SkinOperation.ACTIVATE] ?: false

    // 通用操作执行（删除需二次确认）
    val runOperation: (SkinOperation) -> Unit = { operation ->
        if (operation == SkinOperation.DELETE) {
            confirmDelete = true
        } else {
            viewModel.executeOperation(operation) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
    // 确认后的删除
    fun confirmDeleteSkin() {
        confirmDelete = false
        viewModel.executeOperation(SkinOperation.DELETE) { _, msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // 打开资源文件夹
    fun openFolder() {
        val file = java.io.File(viewModel.getResourceFolderPath())
        if (file.exists()) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.absolutePath.toUri(), "*/*")
            }
            try {
                context.startActivity(Intent.createChooser(intent, "选择文件浏览器"))
            } catch (e: Exception) {
                // 忽略
            }
        } else {
            Toast.makeText(context, "资源文件夹不存在，请先下载资源包", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("皮肤管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAvailableSkins() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("导出现有皮肤") },
                                onClick = { showMoreMenu = false; runOperation(SkinOperation.EXPORT) }
                            )
                            DropdownMenuItem(
                                text = { Text("更新皮肤缓存") },
                                onClick = { showMoreMenu = false; runOperation(SkinOperation.UPDATE) }
                            )
                            DropdownMenuItem(
                                text = { Text("删除皮肤缓存") },
                                onClick = { showMoreMenu = false; confirmDelete = true }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("打开资源文件夹") },
                                onClick = { showMoreMenu = false; openFolder() }
                            )
                            DropdownMenuItem(
                                text = { Text("查看 GitHub 项目") },
                                onClick = {
                                    showMoreMenu = false
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(SkinConstants.GITHUB_REPO_URL))
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 快捷导入行
            item {
                QuickImportRow(
                    onImport = onImport,
                    onImportDirectory = onImportDirectory
                )
            }

            // 2. 已安装皮肤
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已安装皮肤",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.availableSkins.isNotEmpty()) {
                        Text(
                            text = "${state.availableSkins.size} 个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. 皮肤网格 or 空态
            item {
                SkinSelectorCard(
                    availableSkins = state.availableSkins,
                    selectedSkinName = state.selectedSkinName,
                    isEnabled = skinEnabled,
                    onSkinSelected = { name ->
                        viewModel.selectSkin(name)
                        Toast.makeText(
                            context,
                            if (skinEnabled) "已选择皮肤：$name\n重新打开付款码生效" else "皮肤已选择，但自定义皮肤未启用",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onViewDetail = { skinName ->
                        context.startActivity(
                            Intent(context, SkinDetailActivity::class.java).apply {
                                putExtra("skinName", skinName)
                            }
                        )
                    }
                )
            }

            // 4. 设置卡（总开关 + 会员等级）
            item {
                SettingsCard(
                    isEnabled = skinEnabled,
                    onToggleActivate = {
                        viewModel.toggleOperation(SkinOperation.ACTIVATE)
                        Toast.makeText(
                            context,
                            if (skinEnabled) "自定义皮肤系统已禁用" else "已启用自定义皮肤系统",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    selectedGrade = state.selectedGrade,
                    onGradeSelected = { viewModel.updateMemberGrade(it) }
                )
            }

            // 5. 资源包
            item {
                ResourceCard(
                    isInstalled = state.isResourceInstalled,
                    downloadState = state.downloadState,
                    onDownload = { viewModel.downloadResource() }
                )
            }

            // 6. 提示
            item {
                Text(
                    text = "重新打开付款码使更改生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }

    // 删除皮肤缓存二次确认
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("确认删除") },
            text = { Text("将删除支付宝内的皮肤缓存并强制重新加载，此操作不可撤销。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteSkin() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 快捷导入行：导入 ZIP / 导入目录
 */
@Composable
private fun QuickImportRow(
    onImport: () -> Unit,
    onImportDirectory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionChip(
            text = "导入 ZIP",
            onClick = onImport,
            modifier = Modifier.weight(1f)
        )
        ActionChip(
            text = "导入目录",
            onClick = onImportDirectory,
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
        modifier = modifier.height(48.dp),
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
 * 设置卡：总开关 + 会员等级（两行合并成一张卡）
 */
@Composable
private fun SettingsCard(
    isEnabled: Boolean,
    onToggleActivate: () -> Unit,
    selectedGrade: MemberGrade,
    onGradeSelected: (MemberGrade) -> Unit
) {
    var gradeExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 启用自定义皮肤
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "启用自定义皮肤",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isEnabled) "已启用" else "未启用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleActivate() }
                )
            }

            HorizontalDivider()

            // 会员等级
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { gradeExpanded = true }
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700)
                        )
                        Column {
                            Text(
                                text = "会员等级",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = selectedGrade.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = gradeExpanded,
                    onDismissRequest = { gradeExpanded = false }
                ) {
                    MemberGrade.entries.forEach { grade ->
                        DropdownMenuItem(
                            text = { Text(grade.displayName) },
                            onClick = {
                                onGradeSelected(grade)
                                gradeExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 资源包：单行状态 + 下载
 */
@Composable
private fun ResourceCard(
    isInstalled: Boolean,
    downloadState: DownloadState,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "资源包",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    val subtitle = when (downloadState) {
                        is DownloadState.Downloading -> "下载中 ${downloadState.progress}%"
                        is DownloadState.Success -> "已安装"
                        is DownloadState.Error -> "下载失败"
                        else -> if (isInstalled) "已安装" else "未安装，下载供导入使用"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (downloadState is DownloadState.Downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Button(
                    onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isInstalled) "重新下载" else "下载")
                }
            }
        }
    }
}