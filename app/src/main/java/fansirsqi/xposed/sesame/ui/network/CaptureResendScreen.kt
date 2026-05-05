package fansirsqi.xposed.sesame.ui.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.ui.theme.app.SesameColors

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

    val appBarBg = MaterialTheme.colorScheme.primaryContainer
    val appBarContent = MaterialTheme.colorScheme.onPrimaryContainer

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入原始请求", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("粘贴原始 HTTP 请求文本或 cURL 命令", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                title = { Text("请求编辑器", fontWeight = FontWeight.Bold, color = appBarContent) },
                navigationIcon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(MutableInteractionSource(), null) { onBack() }.padding(horizontal = 8.dp)) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = appBarContent, modifier = Modifier.size(20.dp)) }
                        Text("返回", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = appBarContent.copy(alpha = 0.7f))
                    }
                },
                actions = {
                    if (isSending) { CircularProgressIndicator(Modifier.size(20.dp).padding(end = 16.dp), strokeWidth = 2.dp, color = appBarContent) }
                    else {
                        Button(onClick = { viewModel.sendRequest() }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = appBarContent), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) {
                            Icon(Icons.Rounded.Send, null, Modifier.size(14.dp), tint = appBarBg); Spacer(Modifier.width(4.dp)); Text("发送", fontSize = 12.sp, color = appBarBg)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = appBarContent) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("导入解析") }, leadingIcon = { Icon(Icons.Rounded.Input, null) }, onClick = { showMenu = false; showImport = true })
                                DropdownMenuItem(text = { Text("清空全部", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBg)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Method + URL
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
                    Column(Modifier.padding(12.dp)) {
                        Text("请求行", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MethodDropdown(method) { viewModel.method.value = it }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(value = url, onValueChange = { viewModel.url.value = it }, modifier = Modifier.weight(1f), label = { Text("URL") }, singleLine = true, shape = RoundedCornerShape(8.dp))
                        }
                    }
                }
            }
            // Headers
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("请求头", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            IconButton(onClick = { viewModel.addHeader() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.AddCircle, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                        headers.forEachIndexed { i, (k, v) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = k, onValueChange = { viewModel.updateHeader(i, it, v) }, modifier = Modifier.weight(1f), placeholder = { Text("Key") }, singleLine = true)
                                Text(":", Modifier.padding(horizontal = 4.dp), color = SesameColors.TextTertiary)
                                OutlinedTextField(value = v, onValueChange = { viewModel.updateHeader(i, k, it) }, modifier = Modifier.weight(1.5f), placeholder = { Text("Value") }, singleLine = true)
                                IconButton(onClick = { viewModel.removeHeader(i) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.RemoveCircleOutline, null, tint = SesameColors.Error, modifier = Modifier.size(16.dp)) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (headers.isEmpty()) Text("无自定义 Header", fontSize = 11.sp, color = SesameColors.TextTertiary)
                    }
                }
            }
            // Body
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
                    Column(Modifier.padding(12.dp)) {
                        Text("请求体", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = body, onValueChange = { viewModel.body.value = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp), placeholder = { Text("JSON 或文本...") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), shape = RoundedCornerShape(8.dp))
                    }
                }
            }
            // Response
            if (result != null) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
                        Column(Modifier.padding(12.dp)) {
                            Text("响应回显", fontWeight = FontWeight.Bold, color = if (result!!.isSuccess) SesameColors.Success else SesameColors.Error, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Status: ${result!!.code}", fontWeight = FontWeight.Bold, color = if (result!!.isSuccess) SesameColors.Success else SesameColors.Error)
                                Text("${result!!.duration}ms", fontSize = 11.sp, color = SesameColors.TextSecondary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Headers:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            SelectionContainer { Text(result!!.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }, fontSize = 10.sp, color = SesameColors.TextSecondary) }
                            Spacer(Modifier.height(8.dp))
                            Text("Body:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Surface(color = SesameColors.Background, shape = RoundedCornerShape(8.dp)) {
                                val code = result!!.body
                                val annotated = remember(code) {
                                    buildAnnotatedString {
                                        val keyRegex = "\"([^\"]+)\"\\s*:".toRegex()
                                        var last = 0
                                        keyRegex.findAll(code).forEach { m ->
                                            append(code.substring(last, m.range.first))
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))) { append(m.value) }
                                            last = m.range.last + 1
                                        }
                                        if (last < code.length) append(code.substring(last))
                                    }
                                }
                                SelectionContainer { Text(annotated, modifier = Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun MethodDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
    Box {
        Surface(onClick = { expanded = true }, color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp), modifier = Modifier.width(75.dp).height(48.dp)) {
            Row(Modifier.padding(horizontal = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(selected, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(16.dp), tint = Color(0xFF2E7D32))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            methods.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { onSelected(m); expanded = false }) }
        }
    }
}
