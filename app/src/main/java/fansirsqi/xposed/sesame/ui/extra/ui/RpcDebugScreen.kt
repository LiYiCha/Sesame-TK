package fansirsqi.xposed.sesame.ui.extra.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fansirsqi.xposed.sesame.ui.extra.Callbacks
import fansirsqi.xposed.sesame.ui.extra.RequestItem
import fansirsqi.xposed.sesame.ui.extra.viewmodel.RpcDebugViewModel

/**
 * RPC 调试工具
 *
 * @param vm ViewModel
 * @param callbacks 回调
 */
@Composable
private fun RpcDebugScreen(vm: RpcDebugViewModel, callbacks: Callbacks) {
    val title by vm.title.collectAsState()
    val method by vm.method.collectAsState()
    val data by vm.data.collectAsState()
    val result by vm.result.collectAsState()
    val zoomed by vm.zoomed.collectAsState()
    val items by vm.items.collectAsState()
    val editingItem by vm.editingItem.collectAsState()

    val scroll = rememberScrollState()
    var showDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .heightIn(min = 300.dp, max = 600.dp)
            .padding(16.dp)
            .verticalScroll(scroll)
    ) {
        // 请求信息标题
        Text(
            text = "RPC 调试工具",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        // 请求标题
        OutlinedTextField(
            value = title,
            onValueChange = { vm.updateTitle(it) },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = method,
            onValueChange = { vm.updateMethod(it) },
            label = { Text("Method") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Data(JSON)", style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = { vm.triggerManualUnescape() },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text("去除转义", fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = data,
            onValueChange = { vm.updateData(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
        )
        Spacer(Modifier.height(12.dp))
        // 第一行按钮：主要操作
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val newItem = RequestItem(
                        title = title.ifBlank { "未命名" },
                        method = method,
                        data = data
                    )
                    vm.add(newItem)
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
            Button(
                onClick = { callbacks.onSend(-1) },
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f)
            ) { Text("测试") }
            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f)
            ) { Text("导入") }
        }
        Spacer(Modifier.height(8.dp))
        // 第二行按钮：日志查看
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { callbacks.onToggle(1) },
                modifier = Modifier.weight(1f)
            ) { Text("抓包日志") }
            Button(
                onClick = { callbacks.onToggle(2) },
                modifier = Modifier.weight(1f)
            ) { Text("请求日志") }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 240.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = result.ifBlank { "等待结果…" },
                style = if (zoomed) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showDialog = true }) { Text("查看全部") }
        }
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                confirmButton = { TextButton(onClick = { showDialog = false }) { Text("关闭") } },
                title = { Text("查看全部") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SelectionContainer {
                            Text(
                                text = result.ifBlank { "暂无内容" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        RequestListScreen(vm, callbacks)

        if (editingItem != null) {
            var editTitle by remember(editingItem) { mutableStateOf(editingItem?.title ?: "") }
            var editDescription by remember(editingItem) { mutableStateOf(editingItem?.description ?: "") }
            var editMethod by remember(editingItem) { mutableStateOf(editingItem?.method ?: "") }
            var editData by remember(editingItem) { mutableStateOf(editingItem?.data ?: "") }
            AlertDialog(
                onDismissRequest = { vm.dismissEditDialog() },
                confirmButton = {
                    TextButton(onClick = { vm.updateEditingItem(editTitle, editDescription, editMethod, editData) }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissEditDialog() }) { Text("取消") }
                },
                title = { Text("编辑请求") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") })
                        OutlinedTextField(value = editDescription, onValueChange = { editDescription = it }, label = { Text("Description") })
                        OutlinedTextField(
                            value = editMethod,
                            onValueChange = {
                                editMethod = it
                            },
                            label = { Text("Method") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Data(JSON)", style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = { editData = vm.unescapeString(editData) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("去除转义", fontSize = 10.sp)
                            }
                        }
                        OutlinedTextField(
                            value = editData,
                            onValueChange = {
                                editData = it
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                        )
                    }
                }
            )
        }

        // 导入对话框
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = {
                    showImportDialog = false
                    importText = ""
                    importResult = ""
                },
                confirmButton = {
                    TextButton(onClick = {
                        val (success, fail) = vm.importFromJson(importText)
                        importResult = "导入成功: $success 个，失败: $fail 个"
                        if (fail == 0) {
                            // 全部成功，关闭对话框
                            showImportDialog = false
                            importText = ""
                            importResult = ""
                        }
                    }) { Text("导入") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImportDialog = false
                        importText = ""
                        importResult = ""
                    }) { Text("取消") }
                },
                title = { Text("批量导入请求") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "支持两种格式：\n" +
                                    "1. 新格式：{\"Name\":\"标题\",\"Description\":\"描述\",\"methodName\":\"方法\",\"requestData\":[{}]}\n" +
                                    "2. 现有格式：{\"title\":\"标题\",\"method\":\"方法\",\"data\":\"数据\"}\n\n" +
                                    "可以粘贴多个 JSON 对象（连续粘贴或用数组包裹）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it },
                            label = { Text("JSON 内容") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp),
                            maxLines = 10
                        )
                        if (importResult.isNotEmpty()) {
                            Text(
                                text = importResult,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (importResult.contains("失败: 0"))
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    }
}

object RpcDebugScreenBinder {
    @SuppressLint("StateFlowValueCalledInComposition")
    @JvmStatic
    fun bindFullScreen(composeView: ComposeView, initial: List<RequestItem>, callbacks: Callbacks) {
        composeView.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val vm: RpcDebugViewModel = viewModel()
                // 只在首次为空时加载初始
                if (vm.items.value.isEmpty()) vm.load(initial)
                RpcDebugScreen(vm, callbacks)
            }
            }
        }
    }
}