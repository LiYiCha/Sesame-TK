package fansirsqi.xposed.sesame.ui.network

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.ui.theme.app.SesameColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureDetailScreen(
    viewModel: CaptureDetailViewModel,
    onBack: () -> Unit
) {
    val record by viewModel.record.collectAsState()
    val reqBodyText by viewModel.requestBodyDisplay.collectAsState()
    val resBodyText by viewModel.responseBodyDisplay.collectAsState()
    val reqBodyRaw by viewModel.requestBodyRaw.collectAsState()
    val resImage by viewModel.responseImage.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val resendViewModel: CaptureResendViewModel = viewModel()

    var showExport by remember { mutableStateOf(false) }
    var isResendMode by remember { mutableStateOf(false) }

    if (isResendMode) {
        record?.let { rec ->
            CaptureResendScreen(
                viewModel = resendViewModel.apply { initFromRecord(rec, reqBodyRaw ?: "") },
                onBack = { isResendMode = false }
            )
            return
        }
    }

    val tabs = listOf("概览", "请求", "响应")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val appBarBg = MaterialTheme.colorScheme.primaryContainer
    val appBarContent = MaterialTheme.colorScheme.onPrimaryContainer

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.background(appBarBg).statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(record?.host ?: "详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appBarContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(record?.url ?: "", style = MaterialTheme.typography.labelSmall, color = appBarContent.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = appBarContent) }
                    },
                    actions = {
                        if (record != null && !isResendMode) {
                            ActionChip(Icons.Rounded.Link, "复制URL", appBarContent) {
                                val clip = ClipData.newPlainText("URL", record!!.url)
                                context.getSystemService(Context.CLIPBOARD_SERVICE).let { (it as android.content.ClipboardManager).setPrimaryClip(clip) }
                                Toast.makeText(context, "URL 已复制", Toast.LENGTH_SHORT).show()
                            }
                            ActionChip(Icons.Rounded.Terminal, "导出", appBarContent) { showExport = true }
                            ActionChip(Icons.Rounded.Replay, "重发", appBarContent) {
                                resendViewModel.initFromRecord(record!!, reqBodyRaw ?: "")
                                isResendMode = true
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                TabRow(selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent, divider = {},
                    indicator = { tabPos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPos[pagerState.currentPage]), height = 3.dp, color = Color(0xFF4CAF50)) }) {
                    tabs.forEachIndexed { i, t ->
                        Tab(selected = pagerState.currentPage == i, onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text = { Text(t, fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Normal, color = if (pagerState.currentPage == i) appBarContent else appBarContent.copy(alpha = 0.5f)) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { padding ->
        if (record == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(loadError ?: "加载中...", color = MaterialTheme.colorScheme.outline)
            }
            return@Scaffold
        }

        val rec = record!!

        HorizontalPager(state = pagerState, modifier = Modifier.padding(padding).fillMaxSize()) { page ->
            when (page) {
                0 -> OverviewTab(rec)
                1 -> RequestTab(rec, reqBodyText ?: "加载中...", reqBodyRaw ?: "") {
                    resendViewModel.initFromRecord(rec, reqBodyRaw ?: "")
                    isResendMode = true
                }
                2 -> ResponseTab(rec, resBodyText ?: "加载中...", resImage)
            }
        }
    }

    if (showExport) {
        ExportDialog(record!!, reqBodyRaw ?: "", onDismiss = { showExport = false })
    }
}

// ── Overview Tab ─────────────────────────────

@Composable
private fun OverviewTab(rec: CaptureRecord) {
    val timeFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Truncation warning
        if (rec.isTruncated) {
            item {
                Surface(color = Color(0x33FF9800), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF9800))
                        Spacer(Modifier.width(8.dp))
                        Text("响应体超出大小限制，已被截断", color = Color(0xFFFF9800), fontSize = 12.sp)
                    }
                }
            }
        }
        if (rec.errorMessage != null) {
            item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("捕获异常", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            Text(rec.errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item { Section("基本信息", Icons.Rounded.Info) { DetailLine("请求方法", rec.method); DetailLine("URL", rec.url); DetailLine("Host", rec.host); DetailLine("Path", rec.path) } }
        item { Section("请求参数") { rec.queryParams.forEach { (k, v) -> DetailLine(k, v) }; if (rec.queryParams.isEmpty()) Text("无查询参数", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp) } }
        item { Section("响应信息", Icons.Rounded.Analytics) { DetailLine("状态码", "${rec.statusCode}"); DetailLine("Content-Type", rec.contentType ?: "-"); DetailLine("耗时", "${rec.duration}ms") } }
        item { Section("时间", Icons.Rounded.Schedule) { DetailLine("捕获时间", timeFmt.format(Date(rec.timestamp))) } }
        item { DetailLine("请求体大小", "${rec.requestBodySize} B"); DetailLine("响应体大小", "${rec.responseBodySize} B") }
    }
}

// ── Request Tab ──────────────────────────────

@Composable
private fun RequestTab(rec: CaptureRecord, displayBody: String, rawBody: String, onResend: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("请求行", Icons.Rounded.Info) { DetailLine("Method", rec.method); DetailLine("URL", rec.url) } }
        if (rec.queryParams.isNotEmpty()) {
            item { Section("查询参数") { rec.queryParams.forEach { (k, v) -> DetailLine(k, v) } } }
        }
        if (rec.requestHeaders.isNotEmpty()) {
            item { Section("请求头", Icons.Rounded.NorthEast) { rec.requestHeaders.forEach { (k, v) -> DetailLine(k, v) } } }
        }
        item {
            Text("请求体 (${rec.requestBodySize} B)", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            CodeBlock(displayBody)
        }
        item {
            Button(onClick = onResend, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.EditNote, null); Spacer(Modifier.width(8.dp)); Text("修改并重发")
            }
        }
    }
}

// ── Response Tab ─────────────────────────────

@Composable
private fun ResponseTab(rec: CaptureRecord, displayBody: String, image: Bitmap?) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rec.responseHeaders.isNotEmpty()) {
            item { Section("响应头", Icons.Rounded.SouthWest) { rec.responseHeaders.forEach { (k, v) -> DetailLine(k, v) } } }
        }
        item {
            Text("响应体 (${rec.responseBodySize} B)", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (image != null) {
                Image(bitmap = image.asImageBitmap(), contentDescription = "响应图片", modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), contentScale = ContentScale.Fit)
            } else {
                CodeBlock(displayBody)
            }
        }
    }
}

// ── Code View ───────────────────────────────

/**
 * 分段渲染 body 文本:
 * - < 4KB: 直接 Text
 * - 4KB ~ 200KB: LazyColumn 按行虚拟化
 * - > 200KB: 仅显示前 500 行 + 加载全部按钮
 */
@Composable
private fun CodeBlock(text: String) {
    if (text.isBlank()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("(无内容)", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
        }
        return
    }

    val size = text.length
    val lines = remember(text) { text.split('\n') }
    var showAll by remember { mutableStateOf(false) }

    when {
        size < 4000 || showAll -> {
            // 小文本或用户点击了"加载全部" → 一次性渲染
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        text = text,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        size < 200000 && !showAll -> {
            // 中等文本 → LazyColumn 按行虚拟化
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(lines) { line ->
                        SelectionContainer {
                            Text(
                                text = line.ifEmpty { " " },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        else -> {
            // 超大文本 → 只显示前 500 行
            val preview = lines.take(500).joinToString("\n")
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "⚠ 内容较大 (${"%,d".format(size)} 字符)，仅显示前 500 行",
                        color = Color(0xFFFF9800),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = preview,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    TextButton(onClick = { showAll = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("加载全部内容")
                    }
                }
            }
        }
    }
}

// ── Reusable Components ──────────────────────

@Composable
private fun Section(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp) {
            Column(modifier = Modifier.padding(10.dp)) { content() }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.width(90.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        SelectionContainer {
            Text(value, fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 8.sp, color = SesameColors.TextSecondary)
    }
}

// ── Export Dialog ────────────────────────────

@Composable
private fun ExportDialog(rec: CaptureRecord, body: String, onDismiss: () -> Unit) {
    var selectedLang by remember { mutableIntStateOf(0) }
    val languages = listOf("Python", "cURL", "JavaScript")
    val context = LocalContext.current

    val code = when (selectedLang) {
        0 -> generatePython(rec, body)
        1 -> generateCurl(rec, body)
        else -> generateJs(rec, body)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成脚本代码", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedLang, containerColor = Color.Transparent) {
                    languages.forEachIndexed { i, lang -> Tab(selected = selectedLang == i, onClick = { selectedLang = i }, text = { Text(lang, fontSize = 12.sp) }) }
                }
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    SelectionContainer {
                        Text(code, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clip = ClipData.newPlainText("Code", code)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("复制并关闭") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun generateCurl(rec: CaptureRecord, body: String): String {
    val sb = StringBuilder("curl -X ${rec.method} '${rec.url}'")
    rec.requestHeaders.forEach { (k, v) -> if (!k.equals("Content-Length", true)) sb.append(" \\\n  -H '$k: ${v.replace("'", "'\\''")}'") }
    if (body.isNotBlank()) sb.append(" \\\n  --data-raw '${body.replace("'", "'\\''")}'")
    return sb.toString()
}

private fun generateJs(rec: CaptureRecord, body: String): String {
    val headers = rec.requestHeaders.entries.joinToString(",\n") { "    \"${it.key}\": \"${it.value}\"" }
    return "fetch(\"${rec.url}\", {\n  \"method\": \"${rec.method}\",\n  \"headers\": {\n$headers\n  }${if (body.isNotBlank()) ",\n  \"body\": `${body}`" else ""}\n});"
}

private fun generatePython(rec: CaptureRecord, body: String): String {
    val sb = StringBuilder("import requests\n\nurl = \"${rec.url}\"\nheaders = {\n")
    rec.requestHeaders.forEach { (k, v) -> if (!k.equals("Content-Length", true)) sb.append("  \"$k\": \"$v\",\n") }
    sb.append("}\n")
    if (body.isNotBlank()) {
        sb.append("payload = \"\"\"$body\"\"\"\n")
        sb.append("response = requests.${rec.method.lowercase()}(url, headers=headers, data=payload)\n")
    } else {
        sb.append("response = requests.${rec.method.lowercase()}(url, headers=headers)\n")
    }
    sb.append("print(response.text)")
    return sb.toString()
}
