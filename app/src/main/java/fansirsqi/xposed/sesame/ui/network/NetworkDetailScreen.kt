package fansirsqi.xposed.sesame.ui.network

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.ui.theme.app.SesameColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDetailScreen(
    viewModel: NetworkDetailViewModel,
    packet: CapturePacket,
    onBack: () -> Unit
) {
    val resendViewModel: NetworkResendViewModel = viewModel()
    var isResendMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }
    val scope = rememberCoroutineScope()
    
    var showExportDialog by remember { mutableStateOf(false) }
    val requestBody by viewModel.requestBody.collectAsState()

    val tabs = listOf("概览", "请求", "响应")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    LaunchedEffect(packet.id) {
        viewModel.loadBodies(packet)
        if (packet.url == "https://") {
            resendViewModel.initFromPacket(packet, "")
            isResendMode = true
        }
    }

    if (isResendMode) {
        NetworkResendScreen(
            viewModel = resendViewModel,
            onBack = { if (packet.url == "https://") onBack() else isResendMode = false }
        )
        return
    }

    val mintBg = Color(0xFFE9F5E9) // 柔和薄荷绿
    val deepGreen = Color(0xFF2D5A27) // 深森林绿

    Scaffold(
        containerColor = SesameColors.Background,
        topBar = {
            Column(modifier = Modifier.background(mintBg).statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = packet.host ?: "数据包详情",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = deepGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = packet.url ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = deepGreen.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { 
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = deepGreen) 
                        }
                    },
                    actions = {
                        if (!isResendMode) {
                            ActionItem(Icons.Rounded.Link, "复制 URL", deepGreen) {
                                val clip = android.content.ClipData.newPlainText("Sesame URL", packet.url ?: "")
                                clipboardManager.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "URL 已复制", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            ActionItem(Icons.Rounded.Terminal, "代码脚本", deepGreen) {
                                showExportDialog = true
                            }
                            ActionItem(Icons.Rounded.Replay, "重发/模拟", deepGreen) {
                                // 直接从 ViewModel 获取最新值，避免界面延迟
                                val currentBody = viewModel.requestBody.value ?: ""
                                resendViewModel.initFromPacket(packet, currentBody)
                                isResendMode = true
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 3.dp,
                            color = Color(0xFF4CAF50) // 鲜亮绿指示器
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { 
                                Text(
                                    title, 
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if(pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if(pagerState.currentPage == index) deepGreen else deepGreen.copy(alpha = 0.5f)
                                ) 
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { page ->
            when (page) {
                0 -> OverviewTab(packet)
                1 -> FullRequestTab(packet, viewModel.requestBody) {
                    resendViewModel.initFromPacket(packet, requestBody)
                    isResendMode = true
                }
                2 -> FullResponseTab(packet, viewModel)
            }
        }
    }

    if (showExportDialog) {
        CodeExportDialog(packet = packet, body = requestBody, onDismiss = { showExportDialog = false })
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = SesameColors.TextSecondary)
    }
}

@Composable
private fun CodeExportDialog(packet: CapturePacket, body: String?, onDismiss: () -> Unit) {
    var selectedLang by remember { mutableIntStateOf(0) }
    val languages = listOf("Python", "cURL", "JavaScript")
    val context = LocalContext.current
    // 直接获取系统原生剪贴板管理器
    val clipboardManager = remember { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }

    val code = when (selectedLang) {
        0 -> generatePythonScript(packet, body)
        1 -> generateCurlCommand(packet, body)
        else -> generateJsFetch(packet, body)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成脚本代码", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedLang, containerColor = Color.Transparent) {
                    languages.forEachIndexed { index, lang ->
                        Tab(selected = selectedLang == index, onClick = { selectedLang = index }, text = { Text(lang, fontSize = 12.sp) })
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFF1F3F4),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = code,
                            modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                // 使用原生 ClipData 方式
                val clip = android.content.ClipData.newPlainText("Sesame Code", code)
                clipboardManager.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "代码已复制", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("复制并关闭") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun OverviewTab(packet: CapturePacket) {
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            DetailSection(title = "概览", icon = Icons.Rounded.Analytics) {
                DetailRow("Method", packet.method ?: "-")
                DetailRow("Response Code", packet.responseCode.toString())
                DetailRow("Duration", "${packet.duration}ms")
                DetailRow("Protocol", packet.protocol ?: "-")
            }
        }
        item {
            DetailSection(title = "时间线", icon = Icons.Rounded.History) {
                DetailRow("开始时间", timeFormat.format(Date(packet.startTime)))
            }
        }
    }
}

@Composable
private fun FullRequestTab(packet: CapturePacket, bodyFlow: StateFlow<String?>, onResend: () -> Unit) {
    val body by bodyFlow.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            DetailSection(title = "请求行", icon = Icons.Rounded.Info) {
                DetailRow("Method", packet.method ?: "-")
                DetailRow("URL", packet.url ?: "-")
            }
        }
        if (!packet.requestHeaders.isNullOrEmpty()) {
            item {
                DetailSection(title = "请求头", icon = Icons.Rounded.NorthEast) {
                    packet.requestHeaders?.forEach { (k, v) -> DetailRow(k, v) }
                }
            }
        }
        item {
            DetailSection(title = "请求体", icon = Icons.Rounded.Code) {
                CodeView(body ?: "No Body")
            }
        }
        item {
            Button(onClick = onResend, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.EditNote, null)
                Spacer(Modifier.width(8.dp))
                Text("修改并重发")
            }
        }
    }
}

@Composable
private fun FullResponseTab(packet: CapturePacket, viewModel: NetworkDetailViewModel) {
    val body by viewModel.responseBody.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!packet.responseHeaders.isNullOrEmpty()) {
            item {
                DetailSection(title = "响应头", icon = Icons.Rounded.SouthWest) {
                    packet.responseHeaders?.forEach { (k, v) -> DetailRow(k, v) }
                }
            }
        }
        item {
            DetailSection(title = "响应体", icon = Icons.Rounded.Code) {
                CodeView(body ?: "No Body")
            }
        }
    }
}

@Composable
private fun CodeView(code: String) {
    val annotatedString = remember(code) {
        buildAnnotatedString {
            val keyRegex = "\"([^\"]+)\"\\s*:".toRegex()
            var lastIndex = 0
            
            keyRegex.findAll(code).forEach { match ->
                append(code.substring(lastIndex, match.range.first))
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))) {
                    append(match.value)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < code.length) append(code.substring(lastIndex))
        }
    }

    Surface(color = Color(0xFFF8F9FA), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = annotatedString,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun DetailSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SesameColors.TextDisabled)) {
            Column(modifier = Modifier.padding(12.dp)) { content() }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.labelMedium, color = SesameColors.TextSecondary)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun generateCurlCommand(packet: CapturePacket, body: String?): String {
    val sb = StringBuilder("curl -X ${packet.method} '${packet.url}'")
    packet.requestHeaders?.forEach { (k, v) -> if (!k.equals("Content-Length", true)) sb.append(" \\\n  -H '$k: ${v.replace("'", "'\\''")}'") }
    if (!body.isNullOrBlank()) sb.append(" \\\n  --data-raw '${body.replace("'", "'\\''")}'")
    return sb.toString()
}

private fun generateJsFetch(packet: CapturePacket, body: String?): String {
    val headers = packet.requestHeaders?.entries?.joinToString(",\n") { "    \"${it.key}\": \"${it.value}\"" } ?: ""
    return "fetch(\"${packet.url}\", {\n  \"method\": \"${packet.method}\",\n  \"headers\": {\n$headers\n  }${if (!body.isNullOrBlank()) ",\n  \"body\": `${body}`" else ""}\n});"
}

private fun generatePythonScript(packet: CapturePacket, body: String?): String {
    val sb = StringBuilder("import requests\n\nurl = \"${packet.url}\"\nheaders = {\n")
    packet.requestHeaders?.forEach { (k, v) -> if (!k.equals("Content-Length", true)) sb.append("  \"$k\": \"$v\",\n") }
    sb.append("}\n")
    if (!body.isNullOrBlank()) {
        sb.append("payload = \"\"\"$body\"\"\"\n")
        sb.append("response = requests.${packet.method?.lowercase()}(url, headers=headers, data=payload)\n")
    } else {
        sb.append("response = requests.${packet.method?.lowercase()}(url, headers=headers)\n")
    }
    sb.append("print(response.text)")
    return sb.toString()
}
