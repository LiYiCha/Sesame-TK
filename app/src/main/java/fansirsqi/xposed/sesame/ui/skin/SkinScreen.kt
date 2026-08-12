package fansirsqi.xposed.sesame.ui.skin

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.BuildConfig
import java.io.File

/**
 * 皮肤设置主屏幕
 *
 * 使用 Jetpack Compose 构建的现代化声明式 UI
 * 采用 Material Design 3 设计规范
 */
@Composable
fun SkinScreen(viewModel: SkinViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // 首次运行隐私说明对话框
    if (state.isFirstRun) {
        PrivacyDialog(onDismiss = { viewModel.markNotFirstRun() })
    }

    // 渐变背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 20.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部标题
            item {
                TopHeader()
            }

            // 版本信息
            item {
                VersionCard()
            }

            // 会员等级选择
            item {
                MemberGradeCard(
                    selectedGrade = state.selectedGrade,
                    onGradeSelected = { viewModel.updateMemberGrade(it) }
                )
            }

            // 操作按钮组
            item {
                OperationsCard(
                    operationStates = state.operationStates,
                    onToggle = { operation ->
                        // 仅用于 ACTIVATE 操作的开关
                        viewModel.toggleOperation(operation)
                    },
                    onExecute = { operation ->
                        // 用于 EXPORT, DELETE, UPDATE 操作的按钮执行
                        viewModel.executeOperation(operation) { success, message ->
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            // 皮肤选择
            item {
                SkinSelectorCard(
                    availableSkins = state.availableSkins,
                    selectedSkinName = state.selectedSkinName,
                    onSkinSelected = { viewModel.selectSkin(it) },
                    onRefresh = { viewModel.loadAvailableSkins() },
                    onImport = {
                        // 触发导入ZIP操作，由Activity处理
                        (context as? SkinActivity)?.startImportSkin()
                    },
                    onImportDirectory = {
                        // 触发导入目录操作，由Activity处理
                        (context as? SkinActivity)?.startImportSkinFromDirectory()
                    },
                    onViewDetail = { skinName ->
                        // 跳转到皮肤详情页
                        val intent = Intent(context, SkinDetailActivity::class.java).apply {
                            putExtra("skinName", skinName)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 下载资源包
            item {
                DownloadCard(
                    downloadState = state.downloadState,
                    isResourceInstalled = state.isResourceInstalled,
                    onDownload = { viewModel.downloadResource() }
                )
            }

            // 底部操作
            item {
                BottomActions(
                    isResourceInstalled = state.isResourceInstalled,
                    onOpenFolder = {
                        val path = viewModel.getResourceFolderPath()
                        val file = File(path)
                        if (file.exists()) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(path), "*/*")
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "选择文件浏览器"))
                            } catch (e: Exception) {
                                // 处理错误
                            }
                        }
                    },
                    onOpenGithub = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SkinConstants.GITHUB_REPO_URL))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

/**
 * 隐私说明对话框
 */
@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("隐私说明") },
        text = {
            Text(
                "本应用不会收集、不会上传任何用户信息或使用数据。\n\n" +
                        "应用仅在本地运行，不会与任何服务器通信（除非您主动点击\"下载资源包\"按钮从 Github 下载资源）。\n\n" +
                        "所有操作均在您的设备本地完成，请放心使用。"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}

/**
 * 顶部标题
 */
@Composable
private fun TopHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE1D9D2), // RGB 225/217/210
                        Color(0xFFD2FFFB)  // RGB 210/255/251
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = "皮肤管理",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF131313) // 深蓝色，在浅色背景上清晰可见
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "自定义支付宝付款码皮肤",
                fontSize = 14.sp,
                color = Color(0xFF424242) // 深灰色
            )
        }
    }
}

/**
 * 版本信息卡片
 */
@Composable
private fun VersionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF2FE7D6),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Version: ${BuildConfig.VERSION_NAME}",
                fontSize = 14.sp,
                color = Color(0xFF616161)
            )
        }
    }
}

/**
 * 会员等级选择卡片
 */
@Composable
private fun MemberGradeCard(
    selectedGrade: MemberGrade,
    onGradeSelected: (MemberGrade) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "会员等级",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF131313)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 下拉选择器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable { expanded = true }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedGrade.displayName,
                        fontSize = 16.sp,
                        color = Color(0xFF131313)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF7E57C2)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    MemberGrade.values().forEach { grade ->
                        DropdownMenuItem(
                            text = { Text(grade.displayName) },
                            onClick = {
                                onGradeSelected(grade)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
