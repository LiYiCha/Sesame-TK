package fansirsqi.xposed.sesame.ui.network

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import fansirsqi.xposed.sesame.hook.network.capture.RpcTrafficCapture
import fansirsqi.xposed.sesame.ui.logviewer.LogViewerComposeActivity
import fansirsqi.xposed.sesame.util.CaptureFilter
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.ToastUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val disabledSources = remember { mutableStateOf(RpcTrafficCapture.getDisabledSources().toMutableSet()) }
    val keywords = remember { mutableStateOf(CaptureFilter.getKeywords()) }
    val showBlacklistDialog = remember { mutableStateOf(false) }
    // 页面内仅暂存选择，退出页面时一次性提交，避免反复切换触发 hook 反复装卸
    val storedMode = remember { mutableStateOf(RpcTrafficCapture.getCaptureMode()) }
    var draftMode by remember { mutableStateOf(storedMode.value) }
    val storedDecrypt = remember { mutableStateOf(RpcTrafficCapture.isContentEncryptEnabled()) }
    var decryptDraft by remember { mutableStateOf(storedDecrypt.value) }
    val modePending = draftMode != storedMode.value
    val decryptPending = decryptDraft != storedDecrypt.value
    val pending = modePending || decryptPending
    val commitMode = {
        if (modePending) {
            RpcTrafficCapture.setCaptureMode(draftMode)
            storedMode.value = draftMode
        }
        if (decryptPending) {
            RpcTrafficCapture.setContentEncryptEnabled(decryptDraft)
            storedDecrypt.value = decryptDraft
        }
        onBack()
    }

    // 系统返回与顶部返回共用提交路径，保证退出页面才生效
    BackHandler { commitMode() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("抓包设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = { commitMode() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = "当前状态",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val appliedMode = RpcTrafficCapture.getAppliedMode()
                        val running = appliedMode != RpcTrafficCapture.CaptureMode.OFF
                        Text(
                            text = if (running) {
                                "运行中 · ${when (appliedMode) {
                                    RpcTrafficCapture.CaptureMode.RPC_DEBUG -> "RPC 调试"
                                    RpcTrafficCapture.CaptureMode.HTTPS_DEBUG -> "HTTPS 调试"
                                    RpcTrafficCapture.CaptureMode.OFF -> ""
                                }}"
                            } else {
                                "未运行"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (running) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pending || appliedMode != storedMode.value) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (pending) "退出页面后生效" else "应用中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "抓包模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            listOf(
                Triple(RpcTrafficCapture.CaptureMode.OFF, "关闭", "卸载全部抓包 hook，零回调零开销"),
                Triple(RpcTrafficCapture.CaptureMode.RPC_DEBUG, "RPC 调试", "文本日志：H5 与底层 RPC 的请求/响应"),
                Triple(RpcTrafficCapture.CaptureMode.HTTPS_DEBUG, "HTTPS 调试", "结构化抓包引擎，覆盖 RPC / H5 / HTTP 全通道")
            ).forEach { (mode, title, desc) ->
                item {
                    val selected = draftMode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { draftMode = mode },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { draftMode = mode }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            // 抓包范围与加解密是引擎（HTTPS 调试）专属，仅该模式下显示
            if (draftMode == RpcTrafficCapture.CaptureMode.HTTPS_DEBUG) {
                item {
                    Text(
                        text = "抓包范围",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(RpcTrafficCapture.TrafficSource.entries.toList()) { source ->
                    val enabled = source.name !in disabledSources.value
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = source.title, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    RpcTrafficCapture.setSourceEnabled(source.name, checked)
                                    val updated = disabledSources.value.toMutableSet()
                                    if (checked) updated.remove(source.name) else updated.add(source.name)
                                    disabledSources.value = updated
                                }
                            )
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "解密加密请求",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "解出业务 RPC 明文，事件标记 enc",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = decryptDraft,
                                onCheckedChange = { decryptDraft = it }
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBlacklistDialog.value = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "黑名单过滤",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${keywords.value.size} 个关键词，命中不写入记录（同时作用于 RPC 调试过滤）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "管理",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val intent = Intent(context, LogViewerComposeActivity::class.java).apply {
                            data = ("file://" + Files.getCaptureLogFile().absolutePath).toUri()
                            putExtra("canClear", true)
                            putExtra("nextLine", false)
                            putExtra("showTest", false)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看抓包日志")
                }
            }
            item {
                Text(
                    text = "记录写入 capture 日志；黑名单同时作用于两种模式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showBlacklistDialog.value) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBlacklistDialog.value = false },
            title = { Text("黑名单关键词") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("新增关键词（子串匹配，忽略大小写）") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (CaptureFilter.add(input)) {
                                keywords.value = CaptureFilter.getKeywords()
                                input = ""
                            }
                        }) { Text("添加") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (keywords.value.isEmpty()) {
                        Text(
                            text = "无关键词（全部记录不过滤）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            items(keywords.value) { keyword ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = keyword,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        if (CaptureFilter.remove(keyword)) {
                                            keywords.value = CaptureFilter.getKeywords()
                                        }
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "删除 $keyword")
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        if (CaptureFilter.resetToDefault()) {
                            keywords.value = CaptureFilter.getKeywords()
                            ToastUtil.makeText("已恢复默认预设", android.widget.Toast.LENGTH_SHORT)
                        }
                    }) { Text("恢复默认预设") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlacklistDialog.value = false }) { Text("关闭") }
            }
        )
    }
}
