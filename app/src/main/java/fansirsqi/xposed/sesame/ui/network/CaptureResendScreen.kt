package fansirsqi.xposed.sesame.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureResendScreen(viewModel: CaptureResendViewModel, onBack: () -> Unit) {
    val method by viewModel.method.collectAsState()
    val url by viewModel.url.collectAsState()
    val headers by viewModel.headers.collectAsState()
    val body by viewModel.body.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val result by viewModel.result.collectAsState()

    var showImport by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }

    val appBarBg = MaterialTheme.colorScheme.primaryContainer
    val appBarContent = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入原始请求", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("粘贴原始 HTTP 请求文本或 cURL 命令", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = importText, onValueChange = { importText = it }, modifier = Modifier.fillMaxWidth().height(200.dp), placeholder = { Text("POST /api HTTP/1.1\nHost: ...") })
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.importRawRequest(importText); showImport = false; importText = "" }) { Text("解析并填充") } },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("请求编辑器", fontWeight = FontWeight.Bold, color = appBarContent, fontSize = 15.sp) },
                navigationIcon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(interactionSource, null) { onBack() }.padding(horizontal = 8.dp)) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = appBarContent, modifier = Modifier.size(20.dp)) }
                        Text("返回", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = appBarContent.copy(alpha = 0.7f))
                    }
                },
                actions = {
                    if (isSending) { CircularProgressIndicator(Modifier.size(20.dp).padding(end = 16.dp), strokeWidth = 2.dp, color = appBarContent) }
                    else {
                        val context = LocalContext.current
                        Button(onClick = { viewModel.sendRequest(context) }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = appBarContent, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                            Icon(Icons.Rounded.Send, null, Modifier.size(13.dp), tint = Color.White); Spacer(Modifier.width(3.dp)); Text("发送", fontSize = 11.sp, color = Color.White)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = appBarContent, modifier = Modifier.size(18.dp)) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("导入解析", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Rounded.Input, null, modifier = Modifier.size(18.dp)) }, onClick = { showMenu = false; showImport = true })
                                DropdownMenuItem(text = { Text("清空全部", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }, onClick = { viewModel.clear(); showMenu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBg)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── 请求行 ──
            item {
                Surface(color = surfaceColor, shape = RoundedCornerShape(10.dp), shadowElevation = 1.dp) {
                    Column(Modifier.padding(10.dp)) {
                        Text("请求行", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = onSurface)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MethodDropdown(method) { viewModel.method.value = it }
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(value = url, onValueChange = { viewModel.url.value = it }, modifier = Modifier.weight(1f), label = { Text("URL", fontSize = 11.sp) }, singleLine = true, shape = RoundedCornerShape(6.dp), textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                        }
                    }
                }
            }
            // ── 请求头 ──
            item {
                Surface(color = surfaceColor, shape = RoundedCornerShape(10.dp), shadowElevation = 1.dp) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("请求头", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = onSurface)
                            IconButton(onClick = { viewModel.addHeader() }, modifier = Modifier.size(20.dp)) { Icon(Icons.Rounded.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                        }
                        headers.forEachIndexed { i, (k, v) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = k,
                                    onValueChange = { viewModel.updateHeader(i, it, v) },
                                    modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = onSurface),
                                    singleLine = true
                                )
                                Text(":", Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                                BasicTextField(
                                    value = v,
                                    onValueChange = { viewModel.updateHeader(i, k, it) },
                                    modifier = Modifier.weight(1.8f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = onSurface),
                                    singleLine = true
                                )
                                IconButton(onClick = { viewModel.removeHeader(i) }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) { Icon(Icons.Rounded.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (headers.isEmpty()) Text("无自定义 Header", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            // ── 请求体 ──
            item {
                Surface(color = surfaceColor, shape = RoundedCornerShape(10.dp), shadowElevation = 1.dp) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("请求体", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = onSurface)
                            TextButton(
                                onClick = { viewModel.triggerManualUnescape() },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Build, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("去除转义", fontSize = 10.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = body, onValueChange = { viewModel.updateBody(it) }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 250.dp), placeholder = { Text("JSON 或文本...", fontSize = 11.sp) }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), shape = RoundedCornerShape(6.dp))
                    }
                }
            }
            // ── 响应回显 ──
            if (result != null) {
                val res = result!!
                val statusLabel = if (res.isSuccess) "成功" else "失败"
                item {
                    val accentColor = if (res.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Surface(color = surfaceColor, shape = RoundedCornerShape(10.dp), shadowElevation = 1.dp) {
                        Column(Modifier.padding(10.dp)) {
                            Text("响应回显", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("$statusLabel  ${res.code}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = accentColor)
                                Text("${res.duration}ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Headers", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = onSurface)
                            SelectionContainer { Text(res.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline) }
                            Spacer(Modifier.height(6.dp))
                            Text("Body", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = onSurface)
                            Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(6.dp)) {
                                SelectionContainer {
                                    Text(
                                        res.body.ifEmpty { "(无内容)" },
                                        modifier = Modifier.padding(8.dp).fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun MethodDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
    val bg = MaterialTheme.colorScheme.primaryContainer
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Box {
        Surface(onClick = { expanded = true }, color = bg, shape = RoundedCornerShape(6.dp), modifier = Modifier.width(65.dp).height(40.dp)) {
            Row(Modifier.padding(horizontal = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(selected, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = fg)
                Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(14.dp), tint = fg)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            methods.forEach { m -> DropdownMenuItem(text = { Text(m, fontSize = 13.sp) }, onClick = { onSelected(m); expanded = false }) }
        }
    }
}
