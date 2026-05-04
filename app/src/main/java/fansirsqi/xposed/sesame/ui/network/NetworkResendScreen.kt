package fansirsqi.xposed.sesame.ui.network

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
fun NetworkResendScreen(
    viewModel: NetworkResendViewModel,
    onBack: () -> Unit
) {
    val method by viewModel.method.collectAsState()
    val url by viewModel.url.collectAsState()
    val headers by viewModel.headers.collectAsState()
    val body by viewModel.body.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val result by viewModel.resendResult.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var rawImportText by remember { mutableStateOf("") }

    val appBarBg = MaterialTheme.colorScheme.primaryContainer
    val appBarContent = MaterialTheme.colorScheme.onPrimaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary

    @Composable
    fun AppBarIconWithText(
        icon: @Composable () -> Unit,
        label: String,
        onClick: () -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ).padding(horizontal = 8.dp)
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = appBarContent.copy(alpha = 0.7f)
            )
        }
    }

    if (showImportDialog) {
        // ... (AlertDialog 代码保持不变)
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入原始请求", color = primaryColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("请粘贴原始 HTTP 请求文本 (包括请求行和 Header)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rawImportText,
                        onValueChange = { rawImportText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        placeholder = { Text("POST /api/path HTTP/1.1\nHost: example.com\n...") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importRawRequest(rawImportText)
                    showImportDialog = false
                    rawImportText = ""
                }) {
                    Text("解析并填充", color = primaryColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("请求编辑器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appBarContent) },
                navigationIcon = {
                    AppBarIconWithText(
                        icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = appBarContent, modifier = Modifier.size(20.dp)) },
                        label = "返回",
                        onClick = onBack
                    )
                },
                actions = {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = appBarContent)
                        Spacer(modifier = Modifier.width(16.dp))
                    } else {
                        Button(
                            onClick = { viewModel.sendRequest() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = appBarContent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.Send, null, modifier = Modifier.size(14.dp), tint = appBarBg)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("发送", fontSize = 12.sp, color = appBarBg)
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Box {
                            AppBarIconWithText(
                                icon = { Icon(Icons.Rounded.MoreVert, null, tint = appBarContent, modifier = Modifier.size(20.dp)) },
                                label = "更多",
                                onClick = { showMenu = true }
                            )
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导入解析") },
                                    leadingIcon = { Icon(Icons.Rounded.Input, null, tint = appBarContent) },
                                    onClick = { 
                                        showMenu = false
                                        showImportDialog = true 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("清空全部", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { 
                                        showMenu = false
                                        // 可以添加清空逻辑
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 请求行配置
            item {
                SectionCard(title = "🌐 请求行") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        MethodDropdown(method) { viewModel.method.value = it }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { viewModel.url.value = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("URL") },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // 2. Header 配置
            item {
                SectionCard(
                    title = "🔑 请求头 (Headers)",
                    action = {
                        IconButton(onClick = { viewModel.addHeader() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    headers.forEachIndexed { index, (k, v) ->
                        HeaderEditRow(
                            key = k,
                            value = v,
                            onKeyChange = { viewModel.updateHeader(index, it, v) },
                            onValueChange = { viewModel.updateHeader(index, k, it) },
                            onRemove = { viewModel.removeHeader(index) }
                        )
                        if (index < headers.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = SesameColors.Background)
                        }
                    }
                    if (headers.isEmpty()) {
                        Text("无自定义 Header", style = MaterialTheme.typography.labelSmall, color = SesameColors.TextTertiary)
                    }
                }
            }

            // 3. Body 配置
            item {
                SectionCard(title = "📝 请求体 (Body)") {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { viewModel.body.value = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp),
                        placeholder = { Text("JSON 或文本内容...") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // 4. 响应结果展示
            if (result != null) {
                item {
                    SectionCard(
                        title = "📡 响应回显 (Response)",
                        titleColor = if (result!!.isSuccess) SesameColors.Success else SesameColors.Error
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Status: ${result!!.code}", fontWeight = FontWeight.Bold, color = if (result!!.isSuccess) SesameColors.Success else SesameColors.Error)
                            Text("${result!!.duration}ms", style = MaterialTheme.typography.labelSmall, color = SesameColors.TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Response Headers:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        SelectionContainer {
                            Text(
                                text = result!!.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = SesameColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Response Body:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = SesameColors.Background,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val code = result!!.body
                            val annotatedString = remember(code) {
                                androidx.compose.ui.text.buildAnnotatedString {
                                    val keyRegex = "\"([^\"]+)\"\\s*:".toRegex()
                                    var lastIndex = 0
                                    keyRegex.findAll(code).forEach { match ->
                                        append(code.substring(lastIndex, match.range.first))
                                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))) {
                                            append(match.value)
                                        }
                                        lastIndex = match.range.last + 1
                                    }
                                    if (lastIndex < code.length) append(code.substring(lastIndex))
                                }
                            }
                            
                            SelectionContainer {
                                Text(
                                    text = annotatedString,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionCard(
    title: String, 
    titleColor: Color = SesameColors.TextMain,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
            action?.invoke()
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) { content() }
        }
    }
}

@Composable
fun MethodDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
    
    Box {
        Surface(
            onClick = { expanded = true },
            color = Color(0xFFE8F5E9),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.width(75.dp).height(48.dp) // 减小宽度
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp), // 减小内边距
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), // 减小字体
                    color = Color(0xFF2E7D32)
                )
                Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            methods.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m, fontSize = 13.sp) }, // 菜单项字体也调小点
                    onClick = {
                        onSelected(m)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HeaderEditRow(key: String, value: String, onKeyChange: (String) -> Unit, onValueChange: (String) -> Unit, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = key,
            onValueChange = onKeyChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Key", fontSize = 12.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
            textStyle = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(":", color = SesameColors.TextTertiary)
        Spacer(modifier = Modifier.width(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1.5f),
            placeholder = { Text("Value", fontSize = 12.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
            textStyle = MaterialTheme.typography.bodySmall
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Rounded.RemoveCircleOutline, null, tint = SesameColors.Error, modifier = Modifier.size(16.dp))
        }
    }
}
