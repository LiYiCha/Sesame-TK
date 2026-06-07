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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

            if (uiState.isCaptureLog) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.toggleShowH5() }
                    ) {
                        Checkbox(
                            checked = uiState.showH5,
                            onCheckedChange = { viewModel.toggleShowH5() },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("H5 容器", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.toggleShowBottom() }
                    ) {
                        Checkbox(
                            checked = uiState.showBottom,
                            onCheckedChange = { viewModel.toggleShowBottom() },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("底层 RPC", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    }
                }
            }

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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "自动滚动",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.width(2.dp))
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
 * 日志内容显示（支持双指缩放、快速滚动条、搜索结果自动滚动和文本选择）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogContent(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 双指缩放状态
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val effectiveFontSize = (uiState.fontSize * zoomScale).roundToInt().coerceIn(6, 36)

    // 整篇日志的 AnnotatedString，只在数据变动时重构，保证极大性能
    val annotatedString = remember(uiState.displayedLines, uiState.searchResults, uiState.currentSearchIndex, uiState.searchKeyword) {
        buildAnnotatedString {
            val lines = uiState.displayedLines
            val indices = uiState.displayedLineIndices
            val results = uiState.searchResults
            val currentIndex = uiState.currentSearchIndex
            val keyword = uiState.searchKeyword

            val resultsByLine = if (results.isNotEmpty() && keyword.isNotEmpty()) {
                results.groupBy { it.lineIndex }
            } else {
                null
            }

            lines.forEachIndexed { index, line ->
                val originalIndex = indices.getOrNull(index) ?: index
                val lineResults = resultsByLine?.get(originalIndex)

                // 区分日志级别配色
                val levelColor = when {
                    line.contains("ERROR", ignoreCase = true) || line.contains("SEVERE", ignoreCase = true) || line.contains("FATAL", ignoreCase = true) -> Color(0xFFEF5350)
                    line.contains("WARN", ignoreCase = true) || line.contains("WARNING", ignoreCase = true) -> Color(0xFFFFB74D)
                    line.contains("DEBUG", ignoreCase = true) || line.contains("TRACE", ignoreCase = true) -> Color(0xFF90A4AE)
                    line.contains("INFO", ignoreCase = true) -> Color(0xFF81C784)
                    else -> Color.Unspecified
                }

                if (lineResults != null && lineResults.isNotEmpty()) {
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

                        val isCurrent = results.indexOf(result) == currentIndex
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

                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    val spannableString = remember(annotatedString) {
        annotatedString.toSpannableString()
    }

    // 自动滚动到底部
    LaunchedEffect(scrollState.maxValue, uiState.autoScroll) {
        if (uiState.autoScroll) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // 自动滚动到当前搜索结果
    LaunchedEffect(uiState.currentSearchIndex) {
        if (uiState.currentSearchIndex >= 0 && uiState.searchResults.isNotEmpty()) {
            val currentResult = uiState.searchResults[uiState.currentSearchIndex]
            val targetLineIndex = currentResult.lineIndex

            // 找到该行在显示列表中的索引
            val displayIndex = uiState.displayedLineIndices.indexOf(targetLineIndex)

            if (displayIndex >= 0) {
                val lineSpacingPx = with(density) { (effectiveFontSize + 5).sp.toPx() }
                val targetScrollValue = (displayIndex * lineSpacingPx).roundToInt()
                val scrollOffset = (targetScrollValue - with(density) { 200.dp.toPx() }).roundToInt().coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(scrollOffset)
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
        val textColor = MaterialTheme.colorScheme.onBackground
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
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
                },
            factory = { context ->
                TextView(context).apply {
                    setTextIsSelectable(true)
                    typeface = Typeface.MONOSPACE
                }
            },
            update = { textView ->
                textView.text = spannableString
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveFontSize.toFloat())
                textView.setTextColor(textColor.toArgb())
                val spacingExtra = with(density) { 5.dp.toPx() }
                textView.setLineSpacing(spacingExtra, 1.0f)
            }
        )

        // 快速滚动条
        FastScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 2.dp)
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
