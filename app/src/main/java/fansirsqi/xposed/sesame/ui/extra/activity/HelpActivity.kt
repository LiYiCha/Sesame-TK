package fansirsqi.xposed.sesame.ui.extra.activity

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.UUID
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdateSource
import com.updater.model.UpdateSourceType
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme
import fansirsqi.xposed.sesame.util.AppUpdaterManager
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.PermissionUtil
import fansirsqi.xposed.sesame.util.ToastUtil
import java.io.File
import java.util.*

class HelpActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SesameTheme {
                HelpScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val configManager = remember { UpdaterConfigManager(context) }

    // 状态管理
    var updateMode by remember { mutableIntStateOf(configManager.updateMode) }
    var selectedSourceId by remember { mutableStateOf(configManager.selectedSourceId) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showClearLogDialog by remember { mutableStateOf(false) }

    // 存储与日志大小状态
    var storageRefreshTrigger by remember { mutableIntStateOf(0) }
    val storageInfo = remember(storageRefreshTrigger) { calculateStorageInfo() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "帮助与设置",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // 1. 【核心置顶】版本与更新设置模块
            item {
                UpdateSettingsCard(
                    configManager = configManager,
                    updateMode = updateMode,
                    selectedSourceId = selectedSourceId,
                    onUpdateModeChanged = { newMode ->
                        updateMode = newMode
                        configManager.updateMode = newMode
                        Toast.makeText(
                            context,
                            if (newMode == UpdaterConfigManager.UPDATE_MODE_MANUAL) "已设为：手动更新 (仅点击时检查)" else "已设为：自动更新 (启动时静默检测)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onOpenSourceDialog = { showSourceDialog = true },
                    onCheckUpdateClick = {
                        AppUpdaterManager.checkUpdateManual(context)
                    },
                    onOpenDownloadListClick = {
                        AppUpdaterManager.openDownloadList(context)
                    }
                )
            }

            // 2. 系统与设备信息模块
            item {
                SystemInfoCard()
            }

            // 3. 存储与日志管理模块
            item {
                StorageAndLogCard(
                    storageInfo = storageInfo,
                    onClearBackupClick = { showClearLogDialog = true }
                )
            }

            // 4. 权限状态模块
            item {
                PermissionStatusCard()
            }

            // 5. 常见问题解答 FAQ
            item {
                FaqCard()
            }
        }
    }

    // 更新源切换与管理对话框
    if (showSourceDialog) {
        UpdateSourceDialog(
            configManager = configManager,
            currentSourceId = selectedSourceId,
            onDismiss = { showSourceDialog = false },
            onSourceSelected = { newSource ->
                selectedSourceId = newSource.id
                configManager.selectedSourceId = newSource.id
                showSourceDialog = false
                Toast.makeText(context, "已切换更新源：${newSource.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 清理备份日志确认对话框
    if (showClearLogDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogDialog = false },
            title = { Text(text = "清理备份日志", fontWeight = FontWeight.Bold) },
            text = { Text(text = "将清除除今日外的所有备份日志文件，此操作不可撤销，确定清理吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearLogDialog = false
                        val result = clearBackupLogs(context)
                        Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        storageRefreshTrigger++
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("立即清除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearLogDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 1. 软件更新与配置置顶卡片
 */
@Composable
private fun UpdateSettingsCard(
    configManager: UpdaterConfigManager,
    updateMode: Int,
    selectedSourceId: String,
    onUpdateModeChanged: (Int) -> Unit,
    onOpenSourceDialog: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onOpenDownloadListClick: () -> Unit
) {
    val activeSource = remember(selectedSourceId) { configManager.getSelectedSource() }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // 模块头部与版本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "版本与更新设置",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 立即检查更新按钮
                Button(
                    onClick = onCheckUpdateClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "检查更新", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 更新检测模式选择
            Text(
                text = "更新检测模式",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 手动模式单选框
            UpdateModeOptionItem(
                title = "手动更新 (默认)",
                description = "仅在点击检查更新时发起网络请求，日常零后台消耗",
                isSelected = updateMode == UpdaterConfigManager.UPDATE_MODE_MANUAL,
                onClick = { onUpdateModeChanged(UpdaterConfigManager.UPDATE_MODE_MANUAL) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 自动模式单选框
            UpdateModeOptionItem(
                title = "自动更新",
                description = "应用每次启动时后台静默检测，有新版本主动提醒",
                isSelected = updateMode == UpdaterConfigManager.UPDATE_MODE_AUTO,
                onClick = { onUpdateModeChanged(UpdaterConfigManager.UPDATE_MODE_AUTO) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 当前更新源概览与切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable(onClick = onOpenSourceDialog)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前生效更新源",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = activeSource?.name ?: "Cloudflare 官方源",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = activeSource?.url ?: "https://cicha.de5.net",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = onOpenSourceDialog,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("切换源", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 更新包与配套应用下载管理列表入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .clickable(onClick = onOpenDownloadListClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "更新包与配套应用列表",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "查看已下载安装包、断点续传及外部目录",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onOpenDownloadListClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("打开列表", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 更新模式单选项卡片
 */
@Composable
private fun UpdateModeOptionItem(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 2. 系统与设备信息卡片
 */
@Composable
private fun SystemInfoCard() {
    val runtime = Runtime.getRuntime()
    val maxMemory = runtime.maxMemory()
    val totalMemory = runtime.totalMemory()
    val freeMemory = runtime.freeMemory()
    val usedMemory = totalMemory - freeMemory

    SectionCard(
        title = "系统环境与设备信息",
        icon = Icons.Rounded.PhoneAndroid
    ) {
        InfoRow("Android 版本", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        InfoRow("设备机型", "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoRow("系统架构", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown")
        InfoRow("JVM 内存使用", "${formatFileSize(usedMemory)} / ${formatFileSize(maxMemory)}")
    }
}

/**
 * 3. 存储与日志管理卡片
 */
@Composable
private fun StorageAndLogCard(
    storageInfo: StorageInfoData,
    onClearBackupClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    SectionCard(
        title = "存储与日志管理",
        icon = Icons.Rounded.Folder
    ) {
        InfoRow("配置目录", storageInfo.configPath, onClick = {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("ConfigPath", storageInfo.configPath))
            Toast.makeText(context, "配置路径已复制", Toast.LENGTH_SHORT).show()
        })
        InfoRow("配置大小", storageInfo.configSize)
        InfoRow("日志目录", storageInfo.logPath, onClick = {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("LogPath", storageInfo.logPath))
            Toast.makeText(context, "日志路径已复制", Toast.LENGTH_SHORT).show()
        })
        InfoRow("实时日志大小", storageInfo.logSize)
        InfoRow("备份日志大小", storageInfo.bakSize)
        InfoRow("总占用容量", storageInfo.totalSize)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onClearBackupClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("清理旧备份日志")
            }
        }
    }
}

/**
 * 4. 权限状态卡片
 */
@Composable
private fun PermissionStatusCard() {
    val context = LocalContext.current
    val hasStoragePermission = remember { PermissionUtil.checkFilePermissions(context) }
    val storageState = remember { Environment.getExternalStorageState() }

    SectionCard(
        title = "权限与存储环境",
        icon = Icons.Rounded.Security
    ) {
        InfoRow("文件读写权限", if (hasStoragePermission) "正常获取" else "未获取 (需授权)")
        InfoRow("外部存储状态", storageState)
        InfoRow("应用内部私有目录", context.filesDir?.absolutePath ?: "不可用")
    }
}

/**
 * 5. 常见问题解答 FAQ
 */
@Composable
private fun FaqCard() {
    val faqs = remember {
        listOf(
            "模块不生效怎么办？" to "请确认 LSPosed / Xposed 框架中已勾选并激活本模块，作用域已正确包含目标宿主应用，且重启过宿主应用进程。",
            "更新安装包下载后去哪里了？" to "安装包保存在公共媒体目录 Android/media/fansirsqi.xposed.sesame/update/ 下。下载完成后，界面会直接提供【打开目录】按钮，方便通过文件管理器提取或管理。",
            "更新检测模式有什么区别？" to "【手动更新】为默认模式，日常零网络请求与打扰，仅在点击时检查；【自动更新】在每次应用冷启动时静默检查更新并在发现新版时提醒。",
            "安装完成后安装包会自动删除吗？" to "是的！当系统检测到应用已升级生效后，会自动触发物理删除清理；若未安装则坚决完好保留，再次进入时支持 0 流量秒级复用。",
            "日志文件过大怎么办？" to "系统会自动轮转清理 7 天前的过期日志。您也可以在本页面的存储管理中随时点击【清理旧备份日志】释放存储空间。"
        )
    }

    SectionCard(
        title = "常见问题解答 (FAQ)",
        icon = Icons.AutoMirrored.Rounded.Help
    ) {
        faqs.forEachIndexed { index, (q, a) ->
            FaqAccordionItem(question = q, answer = a)
            if (index < faqs.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/**
 * FAQ 折叠手风琴条目
 */
@Composable
private fun FaqAccordionItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = question,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                text = answer,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * 通用卡片容器
 */
@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

/**
 * 更新源管理弹窗
 */
@Composable
private fun UpdateSourceDialog(
    configManager: UpdaterConfigManager,
    currentSourceId: String,
    onDismiss: () -> Unit,
    onSourceSelected: (UpdateSource) -> Unit
) {
    val context = LocalContext.current
    var sources by remember { mutableStateOf(configManager.getSources()) }
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择生效更新源", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                sources.forEach { source ->
                    val isSelected = source.id == currentSourceId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                            .clickable { onSourceSelected(source) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSourceSelected(source) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = source.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (source.type == UpdateSourceType.CLOUDFLARE_R2) " CF " else " GitHub ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (source.type == UpdateSourceType.CLOUDFLARE_R2) Color(0xFFF6821F) else Color(0xFF24292E))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = source.url,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (!source.isPreset) {
                            IconButton(
                                onClick = {
                                    configManager.deleteSource(source.id)
                                    sources = configManager.getSources()
                                    if (source.id == currentSourceId) {
                                        configManager.getSelectedSource()?.let { onSourceSelected(it) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("添加自定义更新源", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("完成")
            }
        }
    )

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(UpdateSourceType.CLOUDFLARE_R2) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(text = "添加自定义更新源", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("源名称") },
                        placeholder = { Text("例如：备用镜像源") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("源地址 URL") },
                        placeholder = { Text("例如：https://cicha.de5.net") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("更新源类型", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedType = UpdateSourceType.CLOUDFLARE_R2 }
                                .padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedType == UpdateSourceType.CLOUDFLARE_R2,
                                onClick = { selectedType = UpdateSourceType.CLOUDFLARE_R2 }
                            )
                            Text("Cloudflare", fontSize = 13.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedType = UpdateSourceType.GITHUB_RELEASES }
                                .padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedType == UpdateSourceType.GITHUB_RELEASES,
                                onClick = { selectedType = UpdateSourceType.GITHUB_RELEASES }
                            )
                            Text("GitHub", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newName.trim()
                        val url = newUrl.trim()
                        if (name.isEmpty() || url.isEmpty()) {
                            Toast.makeText(context, "请填写完整的名称与URL", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val newSource = UpdateSource(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            url = url,
                            type = selectedType,
                            isPreset = false
                        )
                        configManager.addSource(newSource)
                        sources = configManager.getSources()
                        onSourceSelected(newSource)
                        showAddDialog = false
                        Toast.makeText(context, "更新源添加成功并已生效", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("保存并应用")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// 辅助数据与格式化方法
private data class StorageInfoData(
    val configPath: String,
    val configSize: String,
    val logPath: String,
    val logSize: String,
    val bakSize: String,
    val totalSize: String
)

private fun calculateStorageInfo(): StorageInfoData {
    val configDir = Files.CONFIG_DIR
    val logDir = Files.LOG_DIR
    val bakDir = File(logDir, "bak")

    fun getDirSize(d: File?): Long {
        return try {
            d?.walk()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        } catch (_: Exception) { 0L }
    }

    val cSize = getDirSize(configDir)
    val lSize = getDirSize(logDir)
    val bSize = getDirSize(bakDir)

    return StorageInfoData(
        configPath = configDir?.absolutePath ?: "未初始化",
        configSize = formatFileSize(cSize),
        logPath = logDir?.absolutePath ?: "未初始化",
        logSize = formatFileSize(lSize),
        bakSize = formatFileSize(bSize),
        totalSize = formatFileSize(cSize + lSize + bSize)
    )
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${String.format(Locale.getDefault(), "%.2f", size / 1024.0)} KB"
        size < 1024 * 1024 * 1024 -> "${String.format(Locale.getDefault(), "%.2f", size / (1024.0 * 1024.0))} MB"
        else -> "${String.format(Locale.getDefault(), "%.2f", size / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

private fun clearBackupLogs(context: Context): String {
    return try {
        if (!PermissionUtil.checkFilePermissions(context)) {
            return "缺少存储权限"
        }
        val logDir = Files.LOG_DIR ?: return "日志目录未初始化"
        val bakDir = File(logDir, "bak")
        if (!bakDir.exists() || !bakDir.isDirectory) {
            return "未找到备份日志目录"
        }

        val cal = Calendar.getInstance()
        val todayStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

        var deleted = 0
        var failed = 0

        bakDir.walk().filter { it.isFile }.forEach { file ->
            if (!file.name.contains(todayStr)) {
                if (file.delete()) deleted++ else failed++
            }
        }

        "已清理备份日志: $deleted 个，保留今日文件"
    } catch (e: Exception) {
        "清理异常: ${e.message}"
    }
}
