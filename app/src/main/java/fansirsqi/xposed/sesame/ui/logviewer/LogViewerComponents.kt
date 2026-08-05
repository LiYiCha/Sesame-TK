package fansirsqi.xposed.sesame.ui.logviewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import android.widget.TextView
import android.util.TypedValue
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb

/**
 * 搜索面板（重设计 — 蓝色装饰条）
 */
@Composable
fun SearchPanel(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf(uiState.searchKeyword) }
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 左侧浅色装饰条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("搜索", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入搜索关键字…", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.isRegexSearch,
                        onClick = {
                            viewModel.toggleRegexSearch()
                            if (uiState.searchKeyword.isNotEmpty()) viewModel.performSearch()
                        },
                        label = { Text("正则", fontSize = 12.sp) },
                        shape = RoundedCornerShape(20.dp)
                    )
                    FilterChip(
                        selected = uiState.isCaseSensitive,
                        onClick = {
                            viewModel.toggleCaseSensitive()
                            if (uiState.searchKeyword.isNotEmpty()) viewModel.performSearch()
                        },
                        label = { Text("区分大小写", fontSize = 12.sp) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            viewModel.setSearchKeyword(searchText)
                            viewModel.performSearch()
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("搜索", fontSize = 13.sp)
                    }
                    IconButton(
                        onClick = { viewModel.searchPrev() },
                        enabled = uiState.searchResults.isNotEmpty(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "上一个", modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = { viewModel.searchNext() },
                        enabled = uiState.searchResults.isNotEmpty(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "下一个", modifier = Modifier.size(22.dp))
                    }
                    TextButton(
                        onClick = { viewModel.clearSearch() },
                        modifier = Modifier.height(42.dp)
                    ) { Text("清除", fontSize = 12.sp) }
                }

                if (uiState.searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${uiState.currentSearchIndex + 1}/${uiState.searchResults.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 筛选面板（左侧浅绿色装饰条）
 */
@Composable
fun FilterPanel(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    onDismiss: () -> Unit
) {
    var filterText by remember { mutableStateOf(uiState.filterKeyword) }
    val accentColor = Color(0xFF10B981)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("筛选", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入筛选关键字…", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp)
                )

                if (uiState.isCaptureLog) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.toggleShowH5() }) {
                            Checkbox(checked = uiState.showH5, onCheckedChange = { viewModel.toggleShowH5() }, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("H5 容器", fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.toggleShowBottom() }) {
                            Checkbox(checked = uiState.showBottom, onCheckedChange = { viewModel.toggleShowBottom() }, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("底层 RPC", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.setFilterKeyword(filterText) },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("应用筛选", fontSize = 13.sp) }
                    TextButton(
                        onClick = {
                            filterText = ""
                            viewModel.clearFilter()
                        },
                        modifier = Modifier.height(42.dp)
                    ) { Text("清除", fontSize = 12.sp) }
                }
            }
        }
    }
}

/**
 * 日志级别过滤面板（左侧浅琥珀色装饰条）
 */
@Composable
fun LogLevelFilterPanel(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    onDismiss: () -> Unit
) {
    val accentColor = Color(0xFFF59E0B)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日志级别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))

                LogViewerViewModel.LogLevel.entries.forEach { level ->
                    val levelColor = when (level) {
                        LogViewerViewModel.LogLevel.ERROR -> Color(0xFFEF4444)
                        LogViewerViewModel.LogLevel.WARN -> Color(0xFFF59E0B)
                        LogViewerViewModel.LogLevel.INFO -> Color(0xFF10B981)
                        LogViewerViewModel.LogLevel.DEBUG -> Color(0xFF6B7280)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleLogLevel(level) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = level in uiState.enabledLogLevels,
                            onCheckedChange = { viewModel.toggleLogLevel(level) },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(levelColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(level.displayName, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 状态栏（重设计）
 */
@Composable
fun StatusBar(
    uiState: LogViewerViewModel.UiState,
    viewModel: LogViewerViewModel,
    onScrollToTop: () -> Unit = {},
    onScrollToBottom: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onScrollToTop, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.VerticalAlignTop, "直达顶部", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onScrollToBottom, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.VerticalAlignBottom, "直达底部", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.width(6.dp))

                Text("自动滚动", style = MaterialTheme.typography.labelSmall)
                Switch(
                    checked = uiState.autoScroll,
                    onCheckedChange = { viewModel.toggleAutoScroll() },
                    modifier = Modifier.height(20.dp).scale(0.75f)
                )
            }
        }
    }
}

/**
 * 转换 Compose AnnotatedString 到 Android 原生 SpannableString
 */
fun AnnotatedString.toSpannableString(): SpannableString {
    val spannable = SpannableString(this.text)
    this.spanStyles.forEach { range ->
        val start = range.start
        val end = range.end
        val style = range.item
        if (style.color != Color.Unspecified) {
            spannable.setSpan(
                ForegroundColorSpan(style.color.toArgb()),
                start,
                end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (style.background != Color.Unspecified) {
            spannable.setSpan(
                BackgroundColorSpan(style.background.toArgb()),
                start,
                end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (style.fontWeight == FontWeight.Bold) {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
    return spannable
}

/**
 * 搜索高亮状态数据类
 */
@Stable
data class SearchHighlightState(
    val keyword: String,
    val results: List<LogViewerViewModel.SearchResult>,
    val currentIndex: Int
)

/**
 * 单行日志行组件 (提取为独立 Composable 以支持 Compose 跳过机制，防止重复重组)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogLineRow(
    index: Int,
    line: String,
    isSelected: Boolean,
    originalIndex: Int,
    effectiveFontSize: Int,
    isSelectionMode: Boolean,
    searchHighlightState: SearchHighlightState,
    isRpcActive: Boolean = false,
    onLineClick: () -> Unit,
    onLineLongClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val lineAnnotatedString = remember(line, searchHighlightState) {
        buildAnnotatedString {
            val keyword = searchHighlightState.keyword
            val results = searchHighlightState.results
            val currentIndex = searchHighlightState.currentIndex
            val currentResult = if (currentIndex in results.indices) results[currentIndex] else null

            val lineResults = if (results.isNotEmpty() && keyword.isNotEmpty()) {
                results.filter { it.lineIndex == originalIndex }
            } else {
                emptyList()
            }

            val levelColor = when {
                line.contains("ERROR", ignoreCase = true) || line.contains("SEVERE", ignoreCase = true) || line.contains("FATAL", ignoreCase = true) -> Color(0xFFEF5350)
                line.contains("WARN", ignoreCase = true) || line.contains("WARNING", ignoreCase = true) -> Color(0xFFFFB74D)
                line.contains("DEBUG", ignoreCase = true) || line.contains("TRACE", ignoreCase = true) -> Color(0xFF90A4AE)
                line.contains("INFO", ignoreCase = true) -> Color(0xFF81C784)
                else -> Color.Unspecified
            }

            if (lineResults.isNotEmpty()) {
                var lastCharIndex = 0
                lineResults.sortedBy { it.charIndex }.forEach { result ->
                    val preText = line.substring(lastCharIndex, result.charIndex)
                    if (preText.isNotEmpty()) {
                        if (levelColor != Color.Unspecified) {
                            withStyle(style = SpanStyle(color = levelColor)) {
                                append(preText)
                            }
                        } else {
                            append(preText)
                        }
                    }

                    val isCurrent = currentResult?.let {
                        it.lineIndex == result.lineIndex &&
                                it.charIndex == result.charIndex &&
                                it.length == result.length
                    } == true
                    withStyle(
                        style = SpanStyle(
                            background = if (isCurrent) Color.Yellow else Color(0x4DFFFF00),
                            color = if (isCurrent) Color.Red else Color.Unspecified,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    ) {
                        append(line.substring(result.charIndex, result.charIndex + result.length))
                    }
                    lastCharIndex = result.charIndex + result.length
                }

                if (lastCharIndex < line.length) {
                    val postText = line.substring(lastCharIndex)
                    if (levelColor != Color.Unspecified) {
                        withStyle(style = SpanStyle(color = levelColor)) {
                            append(postText)
                        }
                    } else {
                        append(postText)
                    }
                }
            } else {
                if (levelColor != Color.Unspecified) {
                    withStyle(style = SpanStyle(color = levelColor)) {
                        append(line)
                    }
                } else {
                    append(line)
                }
            }
        }
    }

    val rowBgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        isRpcActive -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBgColor)
            .combinedClickable(
                onClick = onLineClick,
                onLongClick = onLineLongClick
            )
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        Text(
            text = lineAnnotatedString,
            fontFamily = FontFamily.Monospace,
            fontSize = effectiveFontSize.sp,
            lineHeight = (effectiveFontSize + 4).sp,
            color = MaterialTheme.colorScheme.onBackground,
            softWrap = true
        )
    }
}

/**
 * 日志内容显示（支持双指缩放、快速滚动条、搜索结果自动滚动和多选复制）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogContent(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    lazyListState: LazyListState
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 双指缩放状态
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val effectiveFontSize = (uiState.fontSize * zoomScale).roundToInt().coerceIn(6, 36)

    // 行详情弹窗状态
    var activeDetailLine by remember { mutableStateOf<String?>(null) }
    var activeDetailBlock by remember { mutableStateOf<RpcBlock?>(null) }

    // 自动滚动到底部
    LaunchedEffect(uiState.displayedLines.size, uiState.autoScroll, uiState.searchKeyword) {
        if (uiState.autoScroll && uiState.displayedLines.isNotEmpty() && uiState.searchKeyword.isEmpty()) {
            try {
                lazyListState.scrollToItem(uiState.displayedLines.size - 1)
            } catch (e: Exception) {
                // 忽略异常
            }
        }
    }

    // 自动滚动到当前搜索结果
    LaunchedEffect(uiState.currentSearchIndex, uiState.searchResults, uiState.displayedLineIndices) {
        if (uiState.currentSearchIndex >= 0 && uiState.searchResults.isNotEmpty()) {
            val currentResult = uiState.searchResults[uiState.currentSearchIndex]
            val targetLineIndex = currentResult.lineIndex

            // 找到该行在显示列表中的索引
            val displayIndex = uiState.displayedLineIndices.indexOf(targetLineIndex)

            if (displayIndex >= 0) {
                coroutineScope.launch {
                    try {
                        lazyListState.animateScrollToItem(displayIndex)
                    } catch (e: Exception) {
                        // 忽略滚动异常
                    }
                }
            }
        }
    }

    // 缩放结束后持久化字体大小
    LaunchedEffect(zoomScale) {
        if (zoomScale != 1f) {
            delay(500L)
            val newSize = (uiState.fontSize * zoomScale).roundToInt().coerceIn(6, 36)
            if (newSize != uiState.fontSize) {
                viewModel.setFontSize(newSize)
            }
            zoomScale = 1f
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var prevDistance = 0f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val p1 = event.changes[0]
                                val p2 = event.changes[1]
                                val curDist = (p1.position - p2.position).getDistance()
                                if (prevDistance > 0f && curDist > 0f) {
                                    val change = curDist / prevDistance
                                    zoomScale = (zoomScale * change).coerceIn(0.5f, 4f)
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                                prevDistance = curDist
                            } else {
                                prevDistance = 0f
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        ) {
            itemsIndexed(
                items = uiState.displayedLines,
                key = { index, _ -> uiState.displayedLineIndices.getOrNull(index) ?: index }
            ) { index, line ->
                val isSelected = index in uiState.selectedIndices
                val originalIndex = uiState.displayedLineIndices.getOrNull(index) ?: index

                val highlightState = remember(uiState.searchKeyword, uiState.searchResults, uiState.currentSearchIndex) {
                    SearchHighlightState(
                        keyword = uiState.searchKeyword,
                        results = uiState.searchResults,
                        currentIndex = uiState.currentSearchIndex
                    )
                }

                // 稳定回调 lambda 从而避免重组
                val onLineClick = remember(index, line, uiState.isSelectionMode, uiState.displayedLines) {
                    {
                        if (uiState.isSelectionMode) {
                            viewModel.toggleLineSelection(index)
                        } else {
                            activeDetailLine = line
                            activeDetailBlock = findRpcBlockAround(uiState.displayedLines, index)
                        }
                    }
                }

                val onLineLongClick = remember(index, uiState.isSelectionMode, uiState.lastSelectedIndex) {
                    {
                        if (!uiState.isSelectionMode) {
                            viewModel.toggleSelectionMode(true)
                            viewModel.toggleLineSelection(index)
                        } else {
                            val last = uiState.lastSelectedIndex
                            if (last != null) {
                                viewModel.selectRange(last, index)
                            } else {
                                viewModel.toggleLineSelection(index)
                            }
                        }
                    }
                }

                val onCheckedChange = remember(index) {
                    { _: Boolean ->
                        viewModel.toggleLineSelection(index)
                    }
                }

                LogLineRow(
                    index = index,
                    line = line,
                    isSelected = isSelected,
                    originalIndex = originalIndex,
                    effectiveFontSize = effectiveFontSize,
                    isSelectionMode = uiState.isSelectionMode,
                    searchHighlightState = highlightState,
                    isRpcActive = uiState.rpcActiveLineIndex == index,
                    onLineClick = onLineClick,
                    onLineLongClick = onLineLongClick,
                    onCheckedChange = onCheckedChange
                )
            }
        }

        // 快速滚动条
        FastScrollbar(
            lazyListState = lazyListState,
            totalItems = uiState.displayedLines.size,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 2.dp)
        )
    }

    // 详情对话框
    val detailLine = activeDetailLine
    if (detailLine != null) {
        LineDetailDialog(
            viewModel = viewModel,
            line = detailLine,
            block = activeDetailBlock,
            onDismiss = {
                activeDetailLine = null
                activeDetailBlock = null
            }
        )
    }
}

/**
 * 快速滚动条组件（基于 LazyListState + Custom PointerInput Tracker）
 */
@Composable
fun FastScrollbar(
    lazyListState: LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    if (totalItems <= 0) return
    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableIntStateOf(0) }

    val firstVisibleIndex = lazyListState.firstVisibleItemIndex
    val visibleItemsCount = lazyListState.layoutInfo.visibleItemsInfo.size

    if (visibleItemsCount >= totalItems) return

    val scrollFraction = (firstVisibleIndex.toFloat() / (totalItems - visibleItemsCount)).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var showScrollbar by remember { mutableStateOf(true) }

    LaunchedEffect(lazyListState.isScrollInProgress, isDragging) {
        if (lazyListState.isScrollInProgress || isDragging) {
            showScrollbar = true
        } else {
            delay(2000L)
            showScrollbar = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (showScrollbar || isDragging) 0.85f else 0f,
        animationSpec = tween(durationMillis = if (showScrollbar || isDragging) 150 else 600),
        label = "scrollbar_alpha"
    )

    val minThumbHeightPx = 40f
    val thumbHeightPx = ((visibleItemsCount.toFloat() / totalItems) * trackHeightPx).coerceAtLeast(minThumbHeightPx)
    val maxThumbOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

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

    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val currentTotalItems by rememberUpdatedState(totalItems)
    val currentVisibleItemsCount by rememberUpdatedState(visibleItemsCount)
    val currentMaxThumbOffset by rememberUpdatedState(maxThumbOffset)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)

    Box(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height }
            .alpha(alpha)
            .pointerInput(Unit) {
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        showScrollbar = true
                        
                        val initialY = down.position.y
                        val halfThumb = currentThumbHeightPx / 2
                        var currentY = (initialY - halfThumb).coerceIn(0f, currentMaxThumbOffset)
                        localDragOffset = currentY
                        
                        val fraction = if (currentMaxThumbOffset > 0) currentY / currentMaxThumbOffset else 0f
                        val targetIndex = (fraction * (currentTotalItems - currentVisibleItemsCount)).roundToInt().coerceIn(0, currentTotalItems - 1)
                        
                        scrollJob?.cancel()
                        scrollJob = coroutineScope.launch {
                            lazyListState.scrollToItem(targetIndex)
                        }
                        down.consume()
                        
                        var dragEvent = down
                        while (true) {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }
                            if (!anyPressed) break
                            
                            val pointerChange = event.changes.firstOrNull { it.id == dragEvent.id } ?: event.changes.first()
                            if (pointerChange.pressed) {
                                if (pointerChange.positionChanged()) {
                                    pointerChange.consume()
                                    val diffY = pointerChange.position.y - dragEvent.position.y
                                    currentY = (currentY + diffY).coerceIn(0f, currentMaxThumbOffset)
                                    localDragOffset = currentY
                                    
                                    val currentFraction = if (currentMaxThumbOffset > 0) currentY / currentMaxThumbOffset else 0f
                                    val targetIdx = (currentFraction * (currentTotalItems - currentVisibleItemsCount)).roundToInt().coerceIn(0, currentTotalItems - 1)
                                    
                                    scrollJob?.cancel()
                                    scrollJob = coroutineScope.launch {
                                        lazyListState.scrollToItem(targetIdx)
                                    }
                                }
                                dragEvent = pointerChange
                            } else {
                                break
                            }
                        }
                    } finally {
                        isDragging = false
                    }
                }
            }
    ) {
        // 轨道背景
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .align(Alignment.Center)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(1.5.dp)
                )
        )

        // 滑块
        val density = androidx.compose.ui.platform.LocalDensity.current

        Box(
            modifier = Modifier
                .width(if (isDragging) 8.dp else 4.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .offset(y = with(density) { thumbOffset.toDp() })
                .align(Alignment.TopCenter)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (isDragging) 0.95f else 0.6f
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

/**
 * 快速滚动条组件（基于 ScrollState）
 * - 拖拽滑块快速定位
 * - 滚动时自动显示，空闲后自动隐藏
 */
@Composable
fun FastScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var trackHeightPx by remember { mutableIntStateOf(0) }
    val maxValue = scrollState.maxValue

    // 无法滚动时，仅通过 Box 占位测量高度
    if (maxValue <= 0 || trackHeightPx <= 0) {
        Box(
            modifier = modifier
                .width(24.dp)
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = it.height }
        )
        if (maxValue <= 0) return
    }

    val totalHeightPx = maxValue + trackHeightPx
    val visibleRatio = trackHeightPx.toFloat() / totalHeightPx
    if (visibleRatio >= 0.99f) return

    val scrollFraction = (scrollState.value.toFloat() / maxValue).coerceIn(0f, 1f)

    // 自动隐藏逻辑
    val isScrolling = scrollState.isScrollInProgress
    var isDragging by remember { mutableStateOf(false) }
    var showScrollbar by remember { mutableStateOf(true) }

    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            showScrollbar = true
        } else {
            delay(2000L)
            showScrollbar = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (showScrollbar || isDragging) 0.85f else 0f,
        animationSpec = tween(durationMillis = if (showScrollbar || isDragging) 150 else 600),
        label = "scrollbar_alpha"
    )

    // 滑块尺寸
    val minThumbHeightPx = with(density) { 40.dp.toPx() }
    val thumbHeightPx = (visibleRatio * trackHeightPx).coerceAtLeast(minThumbHeightPx)
    val maxThumbOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    var localDragOffset by remember { mutableFloatStateOf(0f) }

    val thumbOffset = if (isDragging) {
        localDragOffset.coerceIn(0f, maxThumbOffset)
    } else {
        scrollFraction * maxThumbOffset
    }

    // 未拖动时同步滚动进度
    LaunchedEffect(scrollFraction, maxThumbOffset, isDragging) {
        if (!isDragging) {
            localDragOffset = scrollFraction * maxThumbOffset
        }
    }

    val currentMaxThumbOffset by rememberUpdatedState(maxThumbOffset)
    val currentMaxValue by rememberUpdatedState(maxValue)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)

    Box(
        modifier = modifier
            .width(24.dp)
            .onSizeChanged { trackHeightPx = it.height }
            .alpha(alpha)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    showScrollbar = true

                    val initialY = down.position.y
                    val halfThumb = currentThumbHeightPx / 2
                    var currentY = (initialY - halfThumb).coerceIn(0f, currentMaxThumbOffset)
                    localDragOffset = currentY

                    val initialFraction = if (currentMaxThumbOffset > 0) currentY / currentMaxThumbOffset else 0f
                    val targetScrollValue = (initialFraction * currentMaxValue).roundToInt().coerceIn(0, currentMaxValue)
                    coroutineScope.launch {
                        scrollState.scrollTo(targetScrollValue)
                    }

                    var dragEvent = down
                    do {
                        val event = awaitPointerEvent()
                        val dragChange = event.changes.firstOrNull { it.id == dragEvent.id }
                        if (dragChange != null && dragChange.pressed) {
                            if (dragChange.positionChanged()) {
                                dragChange.consume()
                                val diffY = dragChange.position.y - dragEvent.position.y
                                currentY = (currentY + diffY).coerceIn(0f, currentMaxThumbOffset)
                                localDragOffset = currentY

                                val fraction = if (currentMaxThumbOffset > 0) currentY / currentMaxThumbOffset else 0f
                                val scrollValue = (fraction * currentMaxValue).roundToInt().coerceIn(0, currentMaxValue)
                                coroutineScope.launch {
                                    scrollState.scrollTo(scrollValue)
                                }
                            }
                            dragEvent = dragChange
                        }
                    } while (event.changes.any { it.pressed })

                    isDragging = false
                }
            }
    ) {
        // 轨道背景
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .align(Alignment.Center)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(1.5.dp)
                )
        )

        // 滑块
        val thumbWidthDp = if (isDragging) 8.dp else 4.dp
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
        val thumbOffsetDp = with(density) { thumbOffset.toDp() }

        Box(
            modifier = Modifier
                .width(thumbWidthDp)
                .height(thumbHeightDp)
                .offset(y = thumbOffsetDp)
                .align(Alignment.TopCenter)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (isDragging) 0.95f else 0.6f
                    ),
                    shape = RoundedCornerShape(thumbWidthDp / 2)
                )
        )
    }
}

/**
 * RPC 抓包结构与解析助手
 */
data class RpcBlock(
    val method: String?,
    val params: String?,
    val data: String?,
    val rawText: String
)

fun findRpcBlockAround(lines: List<String>, clickedIndex: Int): RpcBlock? {
    var start = -1
    // 向上扫描最多 50 行寻找请求起点
    for (i in clickedIndex downTo (clickedIndex - 50).coerceAtLeast(0)) {
        if (lines[i].contains("========================>")) {
            start = i
            break
        }
        if (i < clickedIndex && lines[i].contains("<========================")) {
            break
        }
    }
    if (start == -1) return null

    var end = -1
    // 向下扫描最多 100 行寻找请求终点
    for (i in clickedIndex until (clickedIndex + 100).coerceAtMost(lines.size)) {
        if (lines[i].contains("<========================")) {
            end = i
            break
        }
        if (i > clickedIndex && lines[i].contains("========================>")) {
            break
        }
    }
    if (end == -1) return null

    val blockLines = lines.subList(start, end + 1)
    val rawText = blockLines.joinToString("\n")

    var method: String? = null
    var params: String? = null
    var data: String? = null

    blockLines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("Method:")) {
            method = trimmed.substring("Method:".length).trim()
        } else if (trimmed.startsWith("Params:")) {
            params = trimmed.substring("Params:".length).trim()
        } else if (trimmed.startsWith("Data:")) {
            data = trimmed.substring("Data:".length).trim()
        }
    }

    return RpcBlock(method, params, data, rawText)
}

@Composable
fun LineDetailDialog(
    viewModel: LogViewerViewModel,
    line: String,
    block: RpcBlock?,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (block != null) "RPC 抓包详情" else "日志行详情",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("长按下方文本可自由选取内容", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (block != null) {
                        if (!block.method.isNullOrEmpty()) {
                            DetailSection("Method", block.method)
                        }
                        if (!block.params.isNullOrEmpty()) {
                            DetailSection("Params", block.params)
                        }
                        if (!block.data.isNullOrEmpty()) {
                            DetailSection("Data (Response)", block.data)
                        }
                    } else {
                        Text(line, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (block != null) {
                    // 第一行：主要操作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!block.method.isNullOrEmpty() && !block.params.isNullOrEmpty()) {
                            Button(
                                onClick = {
                                    copyToClipboard(context, "Method: ${block.method}\nParams: ${block.params}")
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("复制请求") }
                        }
                        if (!block.method.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, block.method)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("复制 Method") }
                        }
                        if (!block.params.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, block.params)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("复制 Params") }
                        }
                    }
                    // 第二行：次要操作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!block.data.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, block.data)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("复制 Data") }
                        }
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, block.rawText)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("复制全文") }
                    }
                    // 第三行：搜索操作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!block.method.isNullOrEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.setSearchKeyword(block.method)
                                    viewModel.performSearch()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("搜索 Method") }
                        }
                        if (!block.params.isNullOrEmpty()) {
                            TextButton(
                                onClick = {
                                    val searchKey = if (block.params.length > 50) block.params.take(50) else block.params
                                    viewModel.setSearchKeyword(searchKey)
                                    viewModel.performSearch()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("搜索 Params") }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { copyToClipboard(context, line); onDismiss() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("复制整行") }
                        OutlinedButton(
                            onClick = {
                                val searchKey = if (line.length > 50) line.take(50) else line
                                viewModel.setSearchKeyword(searchKey)
                                viewModel.performSearch()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("搜索整行") }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun DetailSection(label: String, content: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    try {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("copied_text", text))
        fansirsqi.xposed.sesame.util.ToastUtil.showToast(context, "已复制到剪贴板")
    } catch (e: Exception) {
        // ignore
    }
}

/**
 * 上下一个请求与响应导航浮动组件（点击追踪 + 高亮，无滚动冲突）
 */
@Composable
fun RequestNavigator(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier
) {
    val boundaryIndices = remember(uiState.displayedLines) {
        viewModel.findBoundaryIndices()
    }

    if (boundaryIndices.isEmpty()) return

    // 纯点击追踪，不与滚动位置联动，避免动画过程中竞争跳回
    var trackedIndex by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // 跳转到指定 RPC 条目并高亮（状态同步立即生效，滚动在协程中异步执行）
    fun jumpTo(index: Int) {
        if (index !in boundaryIndices.indices) return
        trackedIndex = index
        val lineIdx = boundaryIndices[index]
        // 状态更新是同步的，下一帧立即应用高亮
        viewModel.updateRpcActiveLine(lineIdx)
        // 滚动检查放到协程里，避免 layoutInfo 调用阻塞状态提交
        coroutineScope.launch {
            val visibleInfo = lazyListState.layoutInfo.visibleItemsInfo
            if (visibleInfo.none { it.index == lineIdx }) {
                lazyListState.scrollToItem(lineIdx)
            }
        }
    }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showOutlineDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, _, _ ->
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            IconButton(
                onClick = {
                    val target = if (trackedIndex - 1 < 0) boundaryIndices.size - 1 else trackedIndex - 1
                    jumpTo(target)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "上一个请求", modifier = Modifier.size(22.dp))
            }

            Text(
                text = "${trackedIndex + 1}/${boundaryIndices.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { showOutlineDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(
                onClick = {
                    val target = if (trackedIndex + 1 >= boundaryIndices.size) 0 else trackedIndex + 1
                    jumpTo(target)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "下一个请求", modifier = Modifier.size(22.dp))
            }
        }
    }

    if (showOutlineDialog) {
        RequestOutlineDialog(
            boundaryIndices = boundaryIndices,
            displayedLines = uiState.displayedLines,
            lazyListState = lazyListState,
            currentIndex = trackedIndex,
            onDismiss = { showOutlineDialog = false },
            onJumpTo = { index -> jumpTo(index) }
        )
    }
}

@Composable
fun RequestOutlineDialog(
    boundaryIndices: List<Int>,
    displayedLines: List<String>,
    lazyListState: LazyListState,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onJumpTo: ((Int) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("RPC 请求大纲", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                val dialogLazyListState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0)
                )
                LazyColumn(
                    state = dialogLazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(boundaryIndices) { index, lineIndex ->
                        val line = displayedLines.getOrNull(lineIndex) ?: ""
                        var methodName = line
                        for (offset in 0..5) {
                            val targetIdx = lineIndex + offset
                            if (targetIdx < displayedLines.size) {
                                val l = displayedLines[targetIdx].trim()
                                if (l.startsWith("Method:")) {
                                    methodName = l.substring("Method:".length).trim()
                                    break
                                }
                            }
                        }

                        val isCurrent = index == currentIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onJumpTo?.invoke(index)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = if (isCurrent)
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else
                                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = methodName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 多选模式悬浮底栏（重设计）
 */
@Composable
fun SelectionActionBar(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    modifier: Modifier = Modifier
) {
    if (!uiState.isSelectionMode) return
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：已选计数
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = uiState.selectedIndices.size == uiState.displayedLines.size && uiState.displayedLines.isNotEmpty(),
                    onCheckedChange = {
                        if (uiState.selectedIndices.size == uiState.displayedLines.size) viewModel.clearSelection()
                        else viewModel.selectAll()
                    },
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "${uiState.selectedIndices.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // 右侧：操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.selectAll() },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) { Text("全选", fontSize = 12.sp, maxLines = 1) }
                TextButton(
                    onClick = { viewModel.invertSelection() },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) { Text("反选", fontSize = 12.sp, maxLines = 1) }
                TextButton(
                    onClick = { viewModel.clearSelection() },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) { Text("取消", fontSize = 12.sp, maxLines = 1) }
                FilledTonalButton(
                    onClick = { viewModel.copySelectedLines(context) },
                    enabled = uiState.selectedIndices.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(12.dp))
                    Text("复制", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * 根据 Y 坐标查找 LazyList 中对应 item 的显示索引
 * 用于拖拽多选时判断手指下方是哪个日志行
 */
private fun findItemIndexAtY(state: LazyListState, y: Float): Int? {
    return state.layoutInfo.visibleItemsInfo
        .firstOrNull { y >= it.offset && y <= it.offset + it.size }
        ?.index
}
