package com.updater.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.updater.admin.AdminRepository
import org.json.JSONObject

/** 上传进度弹窗状态 */
data class UploadProgressInfo(val percent: Int, val message: String)

/** 通用错误/结果弹窗状态 */
data class AdminDialogInfo(val title: String, val message: String)

/**
 * 安装包与发布管理页面（无状态组合函数集合）
 *
 * 拆分结构：
 * - AdminManagerScreen：页面骨架 + 状态装配
 * - LoginCard / AdminStatusCard / UploadCard / PackageListHeader / PackageItemCard：内容组件
 * - ConfirmDeleteDialog / BusyDialog / UploadProgressDialog / AdminErrorDialog：对话框
 *
 * 所有交互通过回调上抛，业务执行由 Activity 协同 AdminRepository 完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManagerScreen(
    baseHost: String,
    targetAppId: String,
    adminUsername: String,
    loggedIn: Boolean,
    packages: List<JSONObject>,
    selectedFile: AdminRepository.PickedApk?,
    pkgName: String,
    pkgDesc: String,
    loginInFlight: Boolean,
    busyMessage: String?,
    uploadProgress: UploadProgressInfo?,
    dialogInfo: AdminDialogInfo?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onPickFile: () -> Unit,
    onPkgNameChange: (String) -> Unit,
    onPkgDescChange: (String) -> Unit,
    onStartUpload: () -> Unit,
    onDeletePackage: (JSONObject, Int) -> Unit,
    onDismissDialog: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Pair<JSONObject, Int>?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("安装包与发布管理", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh) { Text("刷新") }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                baseHost.isBlank() -> {
                    item {
                        Text(
                            text = "未配置 Cloudflare 更新源，请先在更新源设置中添加有效的 Cloudflare 节点链接。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                !loggedIn -> {
                    item {
                        LoginCard(
                            initialUsername = adminUsername.ifBlank { "admin" },
                            loginInFlight = loginInFlight,
                            onLogin = onLogin
                        )
                    }
                }
                else -> {
                    item {
                        AdminStatusCard(
                            username = adminUsername,
                            targetAppId = targetAppId,
                            onLogout = onLogout
                        )
                    }
                    item {
                        UploadCard(
                            selectedFile = selectedFile,
                            pkgName = pkgName,
                            pkgDesc = pkgDesc,
                            onPkgNameChange = onPkgNameChange,
                            onPkgDescChange = onPkgDescChange,
                            onPickFile = onPickFile,
                            onStartUpload = onStartUpload
                        )
                    }
                    item { PackageListHeader(packages.size) }
                    if (packages.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "当前模块暂无已发布的安装包",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(packages) { index, pkg ->
                            PackageItemCard(
                                pkg = pkg,
                                onDelete = { pendingDelete = pkg to index }
                            )
                        }
                    }
                }
            }
        }

        // 删除确认
        pendingDelete?.let { (pkg, index) ->
            ConfirmDeleteDialog(
                pkgName = pkg.optString("packageName", "安装包"),
                onConfirm = {
                    pendingDelete = null
                    onDeletePackage(pkg, index)
                },
                onDismiss = { pendingDelete = null }
            )
        }

        // 忙碌提示（删除中）
        busyMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("正在删除") },
                text = { Text(msg) },
                confirmButton = {}
            )
        }

        // 上传进度
        uploadProgress?.let { progress ->
            UploadProgressDialog(progress)
        }

        // 结果/错误弹窗
        dialogInfo?.let { info ->
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text(info.title) },
                text = { Text(info.message) },
                confirmButton = {
                    TextButton(onClick = onDismissDialog) { Text("确定") }
                }
            )
        }
    }
}

@Composable
private fun LoginCard(
    initialUsername: String,
    loginInFlight: Boolean,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "管理员登录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "请输入 Cloudflare 网盘后台管理员账号与密码以管理安装包。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                label = { Text("账号") },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text("密码") },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onLogin(username.trim(), password.trim()) },
                enabled = !loginInFlight,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(if (loginInFlight) "正在登录..." else "登 录", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AdminStatusCard(username: String, targetAppId: String, onLogout: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "管理员: $username  (关联模块: $targetAppId)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onLogout) {
                Text("退出登录", fontSize = 12.sp, color = colors.error)
            }
        }
    }
}

@Composable
private fun UploadCard(
    selectedFile: AdminRepository.PickedApk?,
    pkgName: String,
    pkgDesc: String,
    onPkgNameChange: (String) -> Unit,
    onPkgDescChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onStartUpload: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "上传新安装包",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            // 已选文件信息框
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = selectedFile?.let {
                            "已选: ${it.fileName}\n大小: ${formatFileSize(it.sizeBytes)}  |  MD5: ${it.md5.take(8)}..."
                        } ?: "未选择本地 APK 文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onPickFile,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(
                            if (selectedFile == null) "选择本地 APK" else "更换 APK 文件",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pkgName,
                onValueChange = onPkgNameChange,
                singleLine = true,
                label = { Text("包显示名称 (如: 主程序 / 扩展包)") },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = pkgDesc,
                onValueChange = onPkgDescChange,
                singleLine = true,
                label = { Text("更新说明 (选填)") },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStartUpload,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text("开始分片上传并挂钩发布", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PackageListHeader(count: Int) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            text = "已发布安装包列表 ($count)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "误上传或废弃的安装包可直接点击右侧删除，将自动同步删除网盘物理 APK 并更新清单。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun PackageItemCard(pkg: JSONObject, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pkgName = pkg.optString("packageName", "安装包")
    val pkgDesc = pkg.optString("description", "")
    val downloadUrl = pkg.optString("downloadUrl", "")
    val sizeBytes = pkg.optLong("apkSize", 0)
    val md5 = pkg.optString("apkMd5", "")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pkgName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = colors.error
                    ),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("删除", fontSize = 11.sp)
                }
            }
            if (pkgDesc.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pkgDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            val md5Short = if (md5.length > 8) md5.take(8) else md5
            Text(
                text = "大小: ${formatFileSize(sizeBytes)}  |  MD5: $md5Short\n路径: $downloadUrl",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(pkgName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除安装包") },
        text = {
            Text(
                text = "确定要删除【$pkgName】吗？\n\n• 网盘存储桶中的物理 APK 文件将被同步删除\n• 更新发布清单将同步移除此项，客户端更新列表不再显示",
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("确定删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun UploadProgressDialog(progress: UploadProgressInfo) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("分片直传") },
        text = {
            Column {
                Text(
                    text = progress.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (progress.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${progress.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {}
    )
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var sizeD = size.toDouble()
    var i = 0
    while (sizeD >= 1024 && i < units.size - 1) {
        sizeD /= 1024
        i++
    }
    return String.format("%.1f %s", sizeD, units[i])
}
