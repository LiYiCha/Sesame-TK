package com.updater.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdateSource
import com.updater.model.UpdateSourceType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 更新源配置与检测模式设置（Jetpack Compose 版）
 *
 * 功能与旧 View 版完全一致：
 * 1. 更新检测模式切换（手动 / 启动静默检测）
 * 2. GitHub 下载加速代理配置（对接自建 CF Worker gh-proxy）
 * 3. 活跃更新源选择、添加与删除（预设源不可删）
 * 4. 源地址延迟测试（CF 源测服务根地址，GitHub 源测 api.github.com）
 *
 * 必须在 Compose 组合环境内调用（由 DownloadManagerActivity 通过状态驱动渲染）。
 */
@Composable
fun SourceSettingsDialogHost(
    onDismiss: () -> Unit,
    onSourceChanged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val configManager = remember { UpdaterConfigManager(context) }

    var sources by remember { mutableStateOf(configManager.getSources()) }
    var selectedId by remember { mutableStateOf(configManager.selectedSourceId) }
    var updateMode by remember { mutableStateOf(configManager.updateMode) }
    var proxyHost by remember { mutableStateOf(configManager.githubProxyHost) }
    var showAddDialog by remember { mutableStateOf(false) }
    val latencyMap = remember { mutableStateMapOf<String, String>() }

    fun refreshSources(notify: Boolean) {
        sources = configManager.getSources()
        selectedId = configManager.selectedSourceId
        if (notify) onSourceChanged?.invoke()
    }

    fun runLatencyTests() {
        val main = Handler(Looper.getMainLooper())
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        for (source in sources) {
            latencyMap[source.id] = "测速中..."
            val testUrl = if (source.type == UpdateSourceType.CLOUDFLARE_R2) {
                source.url.trim().trimEnd('/')
            } else {
                "https://api.github.com"
            }
            val start = System.currentTimeMillis()
            client.newCall(Request.Builder().url(testUrl).head().build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    main.post { latencyMap[source.id] = "超时" }
                }

                override fun onResponse(call: Call, response: Response) {
                    val ms = System.currentTimeMillis() - start
                    response.close()
                    main.post { latencyMap[source.id] = "$ms ms" }
                }
            })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. 更新检测模式
                Text(
                    text = "更新检测模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                ModeRadioRow(
                    selected = updateMode == UpdaterConfigManager.UPDATE_MODE_MANUAL,
                    label = "仅手动检测（推荐，仅在主动点击时检查）"
                ) {
                    updateMode = UpdaterConfigManager.UPDATE_MODE_MANUAL
                    configManager.updateMode = UpdaterConfigManager.UPDATE_MODE_MANUAL
                    Toast.makeText(context, "已切换为仅手动检测", Toast.LENGTH_SHORT).show()
                }
                ModeRadioRow(
                    selected = updateMode == UpdaterConfigManager.UPDATE_MODE_AUTO,
                    label = "启动时静默检测（打开应用时后台轻量检测）"
                ) {
                    updateMode = UpdaterConfigManager.UPDATE_MODE_AUTO
                    configManager.updateMode = UpdaterConfigManager.UPDATE_MODE_AUTO
                    Toast.makeText(context, "已开启启动时静默检测", Toast.LENGTH_SHORT).show()
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                // 2. GitHub 下载加速代理
                Text(
                    text = "GitHub 下载加速代理（可选）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "填入 gh-proxy 类代理域名（如自建 CF Worker: https://xxx.workers.dev），下载 GitHub Release 直链时自动改写为「代理域名/原始URL」加速转发；留空则直连。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = proxyHost,
                    onValueChange = { proxyHost = it },
                    singleLine = true,
                    placeholder = { Text("https://your-gh-proxy.workers.dev", fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                // 3. 活跃更新源选择 + 测速
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "活跃更新源选择",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { runLatencyTests() }) { Text("测速", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(4.dp))

                for (source in sources) {
                    val isSelected = source.id == selectedId
                    SourceCard(
                        source = source,
                        selected = isSelected,
                        latency = latencyMap[source.id],
                        onSelect = {
                            configManager.selectedSourceId = source.id
                            refreshSources(notify = true)
                        },
                        onDelete = {
                            if (configManager.deleteSource(source.id)) {
                                Toast.makeText(context, "更新源已删除", Toast.LENGTH_SHORT).show()
                                refreshSources(notify = true)
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text("+ 添加更新源", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val oldProxy = configManager.githubProxyHost
                val newProxy = proxyHost.trim()
                configManager.githubProxyHost = newProxy
                if (oldProxy != newProxy) {
                    onSourceChanged?.invoke()
                }
                onDismiss()
            }) { Text("完成") }
        }
    )

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdded = { source ->
                configManager.addSource(source)
                Toast.makeText(context, "更新源已添加", Toast.LENGTH_SHORT).show()
                refreshSources(notify = true)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ModeRadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SourceCard(
    source: UpdateSource,
    selected: Boolean,
    latency: String?,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) BorderStroke(1.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (source.type == UpdateSourceType.CLOUDFLARE_R2) colors.tertiary else colors.secondary,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = if (source.type == UpdateSourceType.CLOUDFLARE_R2) " CF R2 " else " GitHub ",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = latency ?: "--",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (!source.isPreset) {
                TextButton(onClick = onDelete) {
                    Text("删除", fontSize = 11.sp, color = colors.error)
                }
            }
        }
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdded: (UpdateSource) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var isGithub by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加更新源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("源名称") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("服务地址或仓库 URL") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !isGithub,
                        onClick = { isGithub = false }
                    )
                    Text("Cloudflare Pages R2", style = MaterialTheme.typography.bodySmall)
                    RadioButton(
                        selected = isGithub,
                        onClick = { isGithub = true },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    Text("GitHub Releases", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || url.isBlank()) {
                    Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                onAdded(
                    UpdateSource(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        url = url.trim(),
                        type = if (isGithub) UpdateSourceType.GITHUB_RELEASES else UpdateSourceType.CLOUDFLARE_R2,
                        isPreset = false
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
