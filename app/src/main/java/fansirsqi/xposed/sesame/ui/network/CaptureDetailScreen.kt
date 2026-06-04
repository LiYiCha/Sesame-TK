package fansirsqi.xposed.sesame.ui.network

import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import fansirsqi.xposed.sesame.util.JsonUtil
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureDetailScreen(
    detailViewModel: CaptureDetailViewModel,
    onBack: () -> Unit,
    isNewRequest: Boolean = false
) {
    val record by detailViewModel.record.collectAsState()
    val reqLines by detailViewModel.requestLines.collectAsState()
    val resLines by detailViewModel.responseLines.collectAsState()
    val reqBodyRaw by detailViewModel.requestBodyRaw.collectAsState()
    val resImage by detailViewModel.responseImage.collectAsState()
    val loadError by detailViewModel.loadError.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resendViewModel: CaptureResendViewModel = viewModel()
    val interactionSource = remember { MutableInteractionSource() }

    var showExport by remember { mutableStateOf(false) }
    var isResendMode by remember { mutableStateOf(isNewRequest) }

    // 缓存颜色引用，避免重复访问 composable getter
    val appBarBg = MaterialTheme.colorScheme.primaryContainer
    val appBarContent = MaterialTheme.colorScheme.onPrimaryContainer
    val indicatorColor = remember { Color(0xFF4CAF50) }

    // 情况 1: 新建请求模式 -> 返回键直接回列表首页
    if (isResendMode && isNewRequest) {
        BackHandler { onBack() }
        CaptureResendScreen(viewModel = resendViewModel.apply { initFromRecord(CaptureRecord(id = "", url = "https://", method = "GET"), "") }, onBack = onBack)
        return
    }

    // 情况 2: 修改记录重发模式 -> 返回键回到详情预览
    if (isResendMode && record != null) {
        BackHandler { isResendMode = false }
        val body = reqBodyRaw ?: ""
        CaptureResendScreen(viewModel = resendViewModel.apply { initFromRecord(record!!, body) }, onBack = { isResendMode = false })
        return
    }

    val tabs = remember { listOf("概览", "请求", "响应") }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(appBarBg).statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(record?.host ?: "详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appBarContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(record?.url ?: "", style = MaterialTheme.typography.labelSmall, color = appBarContent.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(interactionSource, null, onClick = onBack).padding(horizontal = 8.dp)
                        ) {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = appBarContent, modifier = Modifier.size(20.dp))
                            }
                            Text("返回", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = appBarContent.copy(alpha = 0.7f))
                        }
                    },
                    actions = {
                        if (record != null && !isResendMode) record?.let { rec ->
                            ActionChip(Icons.Rounded.Link, "复制URL", appBarContent) {
                                val clip = ClipData.newPlainText("URL", rec.url)
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                                Toast.makeText(context, "URL 已复制", Toast.LENGTH_SHORT).show()
                            }
                            ActionChip(Icons.Rounded.Terminal, "导出", appBarContent) { showExport = true }
                            ActionChip(Icons.Rounded.Replay, "重发", appBarContent) {
                                resendViewModel.initFromRecord(rec, reqBodyRaw ?: "")
                                isResendMode = true
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                TabRow(selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent, divider = {},
                    indicator = { tabPos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPos[pagerState.currentPage]), height = 3.dp, color = indicatorColor) }
                ) {
                    tabs.forEachIndexed { i, t ->
                        val selected = pagerState.currentPage == i
                        Tab(selected = selected, onClick = remember(i) { { scope.launch { pagerState.animateScrollToPage(i) } } },
                            text = {
                                Text(t,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) appBarContent else appBarContent.copy(alpha = 0.5f))
                            })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { padding ->
        if (record == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(loadError ?: "加载中...", color = MaterialTheme.colorScheme.outline)
            }
            return@Scaffold
        }

        val rec = record!!

        HorizontalPager(state = pagerState, modifier = Modifier.padding(padding).fillMaxSize()) { page ->
            when (page) {
                0 -> OverviewTab(rec)
                1 -> RequestTab(rec, reqLines, reqBodyRaw ?: "") {
                    resendViewModel.initFromRecord(rec, reqBodyRaw ?: "")
                    isResendMode = true
                }
                2 -> ResponseTab(rec, resLines, resImage)
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
    val opType = rec.requestHeaders["Operation-Type"] ?: rec.requestHeaders["operation-type"]
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rec.isTruncated) {
            item(key = "trunc") {
                Surface(color = Color(0x33FF9800), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF9800))
                        Spacer(Modifier.width(8.dp))
                        Text("响应体超出大小限制，已被截断", color = Color(0xFFFF9800), fontSize = 12.sp)
                    }
                }
            }
        }
        if (rec.errorMessage != null) {
            item(key = "error") {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        item(key = "basic") { Section("基本信息", Icons.Rounded.Info) {
            DetailLine("请求方法", rec.method)
            DetailLine("完整URL", rec.url)
            DetailLine("Host", rec.host)
            DetailLine("Path", rec.path)
            if (opType != null) DetailLine("操作类型", opType)
        } }
        item(key = "resp") { Section("响应信息", Icons.Rounded.Analytics) {
            DetailLine("状态码", "${rec.statusCode}")
            DetailLine("Content-Type", rec.contentType ?: "(未获取到)")
            DetailLine("耗时", "${rec.duration}ms")
        } }
        item(key = "headers_summary") { Section("头部统计") {
            DetailLine("请求头数量", "${rec.requestHeaders.size}")
            DetailLine("查询参数", "${rec.queryParams.size}")
        } }
        item(key = "size") { Section("数据大小", Icons.Rounded.SaveAlt) {
            DetailLine("请求体", "${rec.requestBodySize} B")
            DetailLine("响应体", "${rec.responseBodySize} B")
        } }
        item(key = "time") { Section("时间", Icons.Rounded.Schedule) {
            DetailLine("捕获时间", rec.formattedFullTime)
        } }
    }
}

// ── Request Tab ──────────────────────────────

@Composable
private fun RequestTab(rec: CaptureRecord, lines: List<String>, rawBody: String, onResend: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 请求行（原始格式）
        item(key = "rline") {
            val reqLine = "${rec.method} ${rec.path}${if (rec.queryParams.isNotEmpty()) "?" + rec.queryParams.entries.joinToString("&") { "${it.key}=${it.value}" } else ""}"
            Section("请求行", Icons.Rounded.Info) {
                DetailLine("Method", rec.method)
                DetailLine("URL", rec.url)
                if (rec.queryParams.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("原始行: $reqLine HTTP/1.1", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        // 请求头
        item(key = "rhead") {
            Section("请求头 (${rec.requestHeaders.size})", Icons.Rounded.NorthEast) {
                if (rec.requestHeaders.isEmpty()) {
                    Text("(无)", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                } else {
                    rec.requestHeaders.forEach { (k, v) -> DetailLine(k, v) }
                }
            }
        }
        // 请求体
        item(key = "rbody") {
            Text("请求体 (${rec.requestBodySize} B)${if (rec.requestBody != null || rec.requestBodyBase64 != null) "" else " · 无内容"}",
                fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            val isBinary = rec.requestBody == null && rec.requestBodyBase64 != null
            CodeBlock(lines, if (isBinary) rec.requestBodyBase64 else null, isRpc = rec.requestHeaders["Operation-Type"] != null)
        }
        // 修改并重发
        item(key = "rbtn") {
            Button(onClick = onResend, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.EditNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("修改并重发")
            }
        }
    }
}

// ── Response Tab ─────────────────────────────

@Composable
private fun ResponseTab(rec: CaptureRecord, lines: List<String>, image: Bitmap?) {
    val isRpc = rec.requestHeaders["Operation-Type"] != null || rec.requestHeaders["operation-type"] != null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 响应体 (精简后的唯一区块)
        item(key = "sbody") {
            Text("响应体 (${rec.responseBodySize} B)${if (rec.responseBody != null || rec.responseBodyBase64 != null) "" else " · 无内容"}",
                fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (image != null) {
                Image(bitmap = image.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), contentScale = ContentScale.Fit)
            } else {
                val isBinary = rec.responseBody == null && rec.responseBodyBase64 != null
                CodeBlock(lines, if (isBinary) rec.responseBodyBase64 else null, isRpc = isRpc)
            }
        }
    }
}

// ── Code Block — 全量 LazyColumn 虚拟化，永不卡顿 ──

@Composable
private fun CodeBlock(lines: List<String>, base64Data: String? = null, isRpc: Boolean = false) {
    var viewMode by remember { mutableIntStateOf(if (base64Data != null) 1 else 0) } // 0: Text, 1: Hex
    var searchText by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 如果是 RPC，尝试对 Body 进行 Pretty Print 格式化
    val displayLines = remember(lines, isRpc, viewMode) {
        if (isRpc && viewMode == 0 && lines.size == 1) {
            try {
                val json = JsonUtil.formatJson(lines[0])
                json.split("\n")
            } catch (_: Exception) { lines }
        } else lines
    }

    if (lines.size <= 1 && lines.firstOrNull().orEmpty().isBlank()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("(无内容)", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
        }
        return
    }

    // 搜索匹配逻辑
    val matches = remember(displayLines, searchText) {
        if (searchText.isBlank()) emptyList<Pair<Int, Int>>()
        else {
            val result = mutableListOf<Pair<Int, Int>>()
            displayLines.forEachIndexed { lineIdx, line ->
                var start = 0
                while (true) {
                    val index = line.indexOf(searchText, start, ignoreCase = true)
                    if (index == -1) break
                    result.add(lineIdx to index)
                    start = index + searchText.length
                }
            }
            result
        }
    }
    var currentMatchIdx by remember { mutableIntStateOf(0) }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (base64Data != null) {
                    TabRow(selectedTabIndex = viewMode, containerColor = Color.Transparent, modifier = Modifier.width(120.dp).height(32.dp), divider = {}) {
                        Tab(selected = viewMode == 0, onClick = { viewMode = 0 }) { Text("文本", fontSize = 10.sp) }
                        Tab(selected = viewMode == 1, onClick = { viewMode = 1 }) { Text("Hex", fontSize = 10.sp) }
                    }
                } else {
                    Text("文本视图", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                }
                
                Spacer(Modifier.weight(1f))
                
                if (searchActive) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it; currentMatchIdx = 0 },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                    )
                    if (matches.isNotEmpty()) {
                        Text("${currentMatchIdx + 1}/${matches.size}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        IconButton(onClick = {
                            currentMatchIdx = (currentMatchIdx - 1 + matches.size) % matches.size
                            val match = matches[currentMatchIdx]
                            scope.launch { listState.animateScrollToItem(match.first) }
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowUp, null, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = {
                            currentMatchIdx = (currentMatchIdx + 1) % matches.size
                            val match = matches[currentMatchIdx]
                            scope.launch { listState.animateScrollToItem(match.first) }
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = { searchActive = false; searchText = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                    }
                } else {
                    IconButton(onClick = { searchActive = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            Box(Modifier.heightIn(max = 600.dp)) {
                val finalLines = if (viewMode == 1 && base64Data != null) {
                    remember(base64Data) { formatHex(base64Data) }
                } else displayLines

                LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
                    items(finalLines.size, key = { it }) { idx ->
                        val line = finalLines[idx]
                        SelectionContainer {
                            if (searchText.isNotBlank() && line.contains(searchText, ignoreCase = true)) {
                                val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                                    var start = 0
                                    while (true) {
                                        val index = line.indexOf(searchText, start, ignoreCase = true)
                                        if (index == -1) {
                                            append(line.substring(start))
                                            break
                                        }
                                        append(line.substring(start, index))
                                        val isCurrent = matches.getOrNull(currentMatchIdx) == (idx to index)
                                        withStyle(androidx.compose.ui.text.SpanStyle(
                                            background = if (isCurrent) Color(0xFFFF9800).copy(alpha = 0.8f) else Color.Yellow.copy(alpha = 0.5f),
                                            color = Color.Black
                                        )) {
                                            append(line.substring(index, index + searchText.length))
                                        }
                                        start = index + searchText.length
                                    }
                                }
                                Text(
                                    text = annotatedString,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            } else {
                                Text(
                                    text = line.ifEmpty { " " },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    if (finalLines.size > 200) {
                        item {
                            val totalChars = remember(finalLines) { finalLines.sumOf { it.length + 1 } }
                            Text(
                                "— ${finalLines.size} 行 · ${"%,d".format(totalChars)} 字符 —",
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                Scrollbar(listState, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            }
        }
    }
}

/** 格式化 Hex 视图 */
private fun formatHex(base64: String): List<String> {
    return try {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        val result = mutableListOf<String>()
        for (i in bytes.indices step 16) {
            val end = minOf(i + 16, bytes.size)
            val chunk = bytes.sliceArray(i until end)
            val hexPart = chunk.joinToString(" ") { "%02X".format(it) }.padEnd(47)
            val asciiPart = chunk.joinToString("") { if (it in 32..126) it.toInt().toChar().toString() else "." }
            result.add("%04X  %s  %s".format(i, hexPart, asciiPart))
        }
        result
    } catch (_: Exception) { listOf("(Hex 转换失败)") }
}

/** LazyListState 滚动条 */
@Composable
private fun BoxScope.Scrollbar(
    listState: LazyListState,
    color: Color
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) return

    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val visibleItemsCount = visibleItemsInfo.size
    val firstVisibleItemIndex = listState.firstVisibleItemIndex
    val visibleRatio = visibleItemsCount.toFloat() / totalItems
    if (visibleRatio >= 0.99f) return

    val maxScrollIndex = (totalItems - visibleItemsCount).coerceAtLeast(1)
    val scrollFraction = (firstVisibleItemIndex.toFloat() / maxScrollIndex).coerceIn(0f, 1f)

    var trackHeightPx by remember { mutableIntStateOf(0) }
    val minThumbHeightPx = with(density) { 30.dp.toPx() }
    val thumbHeightPx = (visibleRatio * trackHeightPx).coerceAtLeast(minThumbHeightPx)
    val maxThumbOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    var isDragging by remember { mutableStateOf(false) }
    var localDragOffset by remember { mutableFloatStateOf(0f) }

    val thumbOffset = if (isDragging) {
        localDragOffset.coerceIn(0f, maxThumbOffset)
    } else {
        scrollFraction * maxThumbOffset
    }

    LaunchedEffect(scrollFraction, maxThumbOffset, isDragging) {
        if (!isDragging) {
            localDragOffset = scrollFraction * maxThumbOffset
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(24.dp) // 增大触摸区域
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(maxThumbOffset, totalItems, maxScrollIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    
                    val initialY = down.position.y
                    val halfThumb = thumbHeightPx / 2
                    var currentY = (initialY - halfThumb).coerceIn(0f, maxThumbOffset)
                    localDragOffset = currentY
                    
                    val initialFraction = if (maxThumbOffset > 0) currentY / maxThumbOffset else 0f
                    val targetIndex = (initialFraction * maxScrollIndex).roundToInt().coerceIn(0, totalItems - 1)
                    scope.launch {
                        listState.scrollToItem(targetIndex)
                    }

                    var dragEvent = down
                    do {
                        val event = awaitPointerEvent()
                        val dragChange = event.changes.firstOrNull { it.id == dragEvent.id }
                        if (dragChange != null && dragChange.pressed) {
                            if (dragChange.positionChanged()) {
                                dragChange.consume()
                                val diffY = dragChange.position.y - dragEvent.position.y
                                currentY = (currentY + diffY).coerceIn(0f, maxThumbOffset)
                                localDragOffset = currentY
                                
                                val fraction = if (maxThumbOffset > 0) currentY / maxThumbOffset else 0f
                                val index = (fraction * maxScrollIndex).roundToInt().coerceIn(0, totalItems - 1)
                                scope.launch {
                                    listState.scrollToItem(index)
                                }
                            }
                            dragEvent = dragChange
                        }
                    } while (event.changes.any { it.pressed })
                    
                    isDragging = false
                }
            }
    ) {
        // 绘制背景槽
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .width(4.dp)
                .fillMaxHeight()
                .background(color.copy(alpha = 0.05f), RoundedCornerShape(2.dp))
        )

        // 绘制滑块
        val thumbWidthDp = if (isDragging) 6.dp else 4.dp
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
        val thumbOffsetDp = with(density) { thumbOffset.toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .offset(y = thumbOffsetDp)
                .width(thumbWidthDp)
                .height(thumbHeightDp)
                .background(
                    color = if (isDragging) color.copy(alpha = 0.9f) else color,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/** ScrollState 滚动条 */
@Composable
private fun BoxScope.Scrollbar(
    scrollState: ScrollState,
    color: Color
) {
    if (scrollState.maxValue == 0) return
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(4.dp)
            .padding(vertical = 2.dp)
            .drawBehind {
                val ratio = scrollState.value.toFloat() / scrollState.maxValue
                val viewportRatio = scrollState.viewportSize.toFloat() / (scrollState.maxValue + scrollState.viewportSize)
                val thumbHeight = (size.height * viewportRatio).coerceAtLeast(20f)
                val thumbTop = (size.height - thumbHeight) * ratio
                drawRoundRect(color, topLeft = Offset(0f, thumbTop), size = Size(size.width, thumbHeight), cornerRadius = CornerRadius(2f, 2f))
            }
    )
}

// ── Reusable Components ──────────────────────

@Composable
private fun Section(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp) {
            Column(Modifier.padding(10.dp)) { content() }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, Modifier.width(90.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        SelectionContainer { Text(value, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
    }
}

// ── Export Dialog ────────────────────────────

@Composable
private fun ExportDialog(rec: CaptureRecord, body: String, onDismiss: () -> Unit) {
    var selectedLang by remember { mutableIntStateOf(0) }
    val languages = remember { listOf("Python", "cURL", "JavaScript") }
    val context = LocalContext.current

    val code = remember(selectedLang, rec.id, body) {
        when (selectedLang) {
            0 -> generatePython(rec, body)
            1 -> generateCurl(rec, body)
            else -> generateJs(rec, body)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成脚本代码", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TabBar(languages, selectedLang) { selectedLang = it }
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    SelectionContainer {
                        Text(code, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
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

@Composable
private fun TabBar(tabs: List<String>, selectedIdx: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selectedIdx, containerColor = Color.Transparent) {
        tabs.forEachIndexed { i, lang ->
            Tab(selected = selectedIdx == i, onClick = remember(i) { { onSelect(i) } },
                text = { Text(lang, fontSize = 12.sp) })
        }
    }
}

// ── Code Generation ──────────────────────────

private fun generateCurl(rec: CaptureRecord, body: String): String {
    val sb = StringBuilder("curl -X ${rec.method} '${rec.url}'")
    rec.requestHeaders.forEach { (k, v) ->
        if (!k.equals("Content-Length", true))
            sb.append(" \\\n  -H '${k}: ${v.replace("'", "'\\''")}'")
    }
    if (body.isNotBlank()) sb.append(" \\\n  --data-raw '${body.replace("'", "'\\''")}'")
    return sb.toString()
}

private fun generateJs(rec: CaptureRecord, body: String): String {
    val headers = rec.requestHeaders.entries.joinToString(",\n") { "    \"${it.key}\": \"${it.value}\"" }
    return "fetch(\"${rec.url}\", {\n  \"method\": \"${rec.method}\",\n  \"headers\": {\n$headers\n  }${if (body.isNotBlank()) ",\n  \"body\": `${"$body"}`" else ""}\n});"
}

private fun generatePython(rec: CaptureRecord, body: String): String {
    val sb = StringBuilder("import requests\n\nurl = \"${rec.url}\"\nheaders = {\n")
    rec.requestHeaders.forEach { (k, v) ->
        if (!k.equals("Content-Length", true)) sb.append("  \"$k\": \"$v\",\n")
    }
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
