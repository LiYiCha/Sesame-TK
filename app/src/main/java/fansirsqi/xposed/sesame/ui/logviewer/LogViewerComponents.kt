package fansirsqi.xposed.sesame.ui.logviewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 搜索面板（紧凑设计）
 */
@Composable
fun SearchPanel(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf(uiState.searchKeyword) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 搜索输入框 - 不设置固定高度，让其自适应
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "输入搜索关键字...",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(4.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 搜索选项
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FilterChip(
                    selected = uiState.isRegexSearch,
                    onClick = {
                        viewModel.toggleRegexSearch()
                        if (uiState.searchKeyword.isNotEmpty()) {
                            viewModel.performSearch()
                        }
                    },
                    label = { Text("正则", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
                    modifier = Modifier.height(24.dp)
                )
                FilterChip(
                    selected = uiState.isCaseSensitive,
                    onClick = {
                        viewModel.toggleCaseSensitive()
                        if (uiState.searchKeyword.isNotEmpty()) {
                            viewModel.performSearch()
                        }
                    },
                    label = { Text("区分大小写", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 搜索按钮和导航
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        viewModel.setSearchKeyword(searchText)
                        viewModel.performSearch()
                    },
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("搜索", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }

                IconButton(
                    onClick = { viewModel.searchPrev() },
                    enabled = uiState.searchResults.isNotEmpty(),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "上一个", modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = { viewModel.searchNext() },
                    enabled = uiState.searchResults.isNotEmpty(),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "下一个", modifier = Modifier.size(18.dp))
                }

                TextButton(
                    onClick = { viewModel.clearSearch() },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("清除", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
            }

            // 搜索结果统计
            if (uiState.searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${uiState.currentSearchIndex + 1}/${uiState.searchResults.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 筛选面板（超紧凑设计）
 */
@Composable
fun FilterPanel(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    onDismiss: () -> Unit
) {
    var filterText by remember { mutableStateOf(uiState.filterKeyword) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "筛选",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 筛选输入框 - 不设置固定高度，让其自适应
            TextField(
                value = filterText,
                onValueChange = { filterText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "输入筛选关键字...",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(4.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.setFilterKeyword(filterText)
                    },
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("应用筛选", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }

                TextButton(
                    onClick = {
                        filterText = ""
                        viewModel.clearFilter()
                    },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("清除", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * 日志级别过滤面板（超紧凑设计）
 */
@Composable
fun LogLevelFilterPanel(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "日志级别",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 日志级别选项
            LogViewerViewModel.LogLevel.entries.forEach { level ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleLogLevel(level) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = level in uiState.enabledLogLevels,
                        onCheckedChange = { viewModel.toggleLogLevel(level) },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = level.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * 状态栏（超紧凑设计）
 */
@Composable
fun StatusBar(
    uiState: LogViewerViewModel.UiState,
    viewModel: LogViewerViewModel
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f, fill = false)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自动滚动",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
                Switch(
                    checked = uiState.autoScroll,
                    onCheckedChange = { viewModel.toggleAutoScroll() },
                    modifier = Modifier
                        .height(16.dp)
                        .scale(0.7f)
                )
            }
        }
    }
}

/**
 * 日志内容显示（支持双指缩放、快速滚动条、搜索结果自动滚动和文本选择）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogContent(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 双指缩放状态
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val effectiveFontSize = (uiState.fontSize * zoomScale).roundToInt().coerceIn(6, 36)

    // 自动滚动到底部
    LaunchedEffect(uiState.displayedLines.size, uiState.autoScroll) {
        if (uiState.autoScroll && uiState.displayedLines.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.displayedLines.size - 1)
            }
        }
    }

    // 自动滚动到当前搜索结果
    LaunchedEffect(uiState.currentSearchIndex) {
        if (uiState.currentSearchIndex >= 0 && uiState.searchResults.isNotEmpty()) {
            val currentResult = uiState.searchResults[uiState.currentSearchIndex]
            val targetLineIndex = currentResult.lineIndex

            // 找到该行在显示列表中的索引
            val fullLines = uiState.fullLogText.split('\n')
            var displayIndex = 0
            for (i in 0 until targetLineIndex) {
                if (i < fullLines.size) {
                    val line = fullLines[i]
                    val matchesFilter = uiState.filterKeyword.isEmpty() ||
                                       line.contains(uiState.filterKeyword, ignoreCase = true)
                    val matchesLevel = if (uiState.enabledLogLevels.size == LogViewerViewModel.LogLevel.entries.size) {
                        true
                    } else {
                        uiState.enabledLogLevels.any { level -> level.pattern.containsMatchIn(line) }
                    }
                    if (matchesFilter && matchesLevel) {
                        displayIndex++
                    }
                }
            }

            // 滚动到目标位置（居中显示）
            if (displayIndex < uiState.displayedLines.size) {
                coroutineScope.launch {
                    listState.animateScrollToItem(
                        index = displayIndex,
                        scrollOffset = -200 // 偏移量，使目标行显示在屏幕中间
                    )
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 使用 SelectionContainer 启用文本选择和复制功能
        androidx.compose.foundation.text.selection.SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 8.dp, end = 20.dp) // 右侧给滚动条留空间
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var prevDistance = 0f
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
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
                    },
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = uiState.displayedLines,
                    key = { index, _ -> index }
                ) { index, line ->
                    LogLine(
                        line = line,
                        lineIndex = index,
                        searchResults = uiState.searchResults,
                        currentSearchIndex = uiState.currentSearchIndex,
                        searchKeyword = uiState.searchKeyword,
                        fontSize = effectiveFontSize
                    )
                }
            }
        }

        // 快速滚动条
        FastScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 2.dp)
        )
    }
}

/**
 * 快速滚动条组件
 * - 拖拽滑块快速定位
 * - 点击轨道直接跳转
 * - 滚动时自动显示，空闲后自动隐藏
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val totalItems = listState.layoutInfo.totalItemsCount
    val visibleItems = listState.layoutInfo.visibleItemsInfo

    // 不需要滚动条的情况
    if (totalItems <= 0 || visibleItems.isEmpty()) return
    val visibleRatio = visibleItems.size.toFloat() / totalItems
    if (visibleRatio >= 0.99f) return

    // 滚动进度
    val maxScrollIndex = (totalItems - visibleItems.size).coerceAtLeast(1)
    val scrollFraction = (listState.firstVisibleItemIndex.toFloat() / maxScrollIndex).coerceIn(0f, 1f)

    // 自动隐藏逻辑
    val isScrolling = listState.isScrollInProgress
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

    // 轨道高度（像素）
    var trackHeightPx by remember { mutableIntStateOf(0) }

    // 滑块尺寸
    val minThumbHeightPx = with(density) { 40.dp.toPx() }
    val thumbHeightPx = (visibleRatio * trackHeightPx).coerceAtLeast(minThumbHeightPx)
    val maxThumbOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffset = scrollFraction * maxThumbOffset

    val draggableState = rememberDraggableState { delta ->
        if (maxThumbOffset > 0) {
            val newOffset = (thumbOffset + delta).coerceIn(0f, maxThumbOffset)
            val newFraction = newOffset / maxThumbOffset
            val targetIndex = (newFraction * maxScrollIndex).roundToInt()
                .coerceIn(0, (totalItems - 1).coerceAtLeast(0))
            coroutineScope.launch {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .width(16.dp)
            .onSizeChanged { trackHeightPx = it.height }
            .alpha(alpha)
            .draggable(
                orientation = Orientation.Vertical,
                state = draggableState,
                onDragStarted = { offset ->
                    isDragging = true
                    showScrollbar = true
                    // 点击轨道任意位置直接跳转
                    if (trackHeightPx > 0) {
                        val frac = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                        val target = (frac * (totalItems - 1)).roundToInt()
                            .coerceIn(0, (totalItems - 1).coerceAtLeast(0))
                        coroutineScope.launch {
                            listState.scrollToItem(target)
                        }
                    }
                },
                onDragStopped = { isDragging = false }
            )
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
 * 单行日志显示
 */
@Composable
fun LogLine(
    line: String,
    lineIndex: Int,
    searchResults: List<LogViewerViewModel.SearchResult>,
    currentSearchIndex: Int,
    searchKeyword: String,
    fontSize: Int = 11
) {
    // 查找当前行的搜索结果
    val lineResults = searchResults.filter { it.lineIndex == lineIndex }

    val annotatedText = if (lineResults.isNotEmpty() && searchKeyword.isNotEmpty()) {
        buildAnnotatedString {
            var lastIndex = 0

            lineResults.sortedBy { it.charIndex }.forEach { result ->
                // 添加高亮前的文本
                append(line.substring(lastIndex, result.charIndex))

                // 添加高亮文本
                val isCurrent = searchResults.indexOf(result) == currentSearchIndex
                withStyle(
                    style = SpanStyle(
                        background = if (isCurrent) Color.Yellow else Color(0x4DFFFF00),
                        color = if (isCurrent) Color.Red else Color.Unspecified,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                ) {
                    append(line.substring(result.charIndex, result.charIndex + result.length))
                }

                lastIndex = result.charIndex + result.length
            }

            // 添加剩余文本
            if (lastIndex < line.length) {
                append(line.substring(lastIndex))
            }
        }
    } else {
        buildAnnotatedString { append(line) }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 5).sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}
