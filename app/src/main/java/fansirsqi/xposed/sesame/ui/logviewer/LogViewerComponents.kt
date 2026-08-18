package fansirsqi.xposed.sesame.ui.logviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import fansirsqi.xposed.sesame.util.ToastUtil

/**
 * 搜索栏
 */
@Composable
fun SearchPanel(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    onDismiss: () -> Unit
) {
    SearchPanelContent(
        searchKeyword = uiState.searchKeyword,
        isRegexSearch = uiState.isRegexSearch,
        isCaseSensitive = uiState.isCaseSensitive,
        currentSearchIndex = uiState.currentSearchIndex,
        totalSearchResults = uiState.searchResults.size,
        onSearchChange = { keyword ->
            viewModel.setSearchKeyword(keyword)
            if (keyword.isNotEmpty()) {
                viewModel.performSearch()
            } else {
                viewModel.clearSearch()
            }
        },
        onToggleRegex = {
            viewModel.toggleRegexSearch()
            if (uiState.searchKeyword.isNotEmpty()) viewModel.performSearch()
        },
        onToggleCase = {
            viewModel.toggleCaseSensitive()
            if (uiState.searchKeyword.isNotEmpty()) viewModel.performSearch()
        },
        onSearchPrev = { viewModel.searchPrev() },
        onSearchNext = { viewModel.searchNext() },
        onClearSearch = { viewModel.clearSearch() },
        onDismiss = onDismiss
    )
}

/**
 * 搜索栏无状态 2 行内容组件
 */
@Composable
fun SearchPanelContent(
    searchKeyword: String,
    isRegexSearch: Boolean,
    isCaseSensitive: Boolean,
    currentSearchIndex: Int,
    totalSearchResults: Int,
    onSearchChange: (String) -> Unit,
    onToggleRegex: () -> Unit,
    onToggleCase: () -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
    onClearSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember(searchKeyword) { mutableStateOf(searchKeyword) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = containerColor,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // 第一行：输入框 + 清除 + 关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchText.isEmpty()) {
                        Text(
                            "输入搜索关键字 (实时查找)...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            onSearchChange(it)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(primaryColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            searchText = ""
                            onClearSearch()
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "清除输入",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭搜索",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 第二行：控制胶囊 + 匹配导航
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧开关：正则 & 大小写
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier.clickable { onToggleRegex() },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isRegexSearch) primaryColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            0.5.dp,
                            if (isRegexSearch) primaryColor.copy(alpha = 0.6f) else Color.Transparent
                        )
                    ) {
                        Text(
                            ".* 正则",
                            fontSize = 11.5.sp,
                            fontWeight = if (isRegexSearch) FontWeight.Bold else FontWeight.Normal,
                            color = if (isRegexSearch) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.clickable { onToggleCase() },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCaseSensitive) primaryColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            0.5.dp,
                            if (isCaseSensitive) primaryColor.copy(alpha = 0.6f) else Color.Transparent
                        )
                    ) {
                        Text(
                            "Aa 区分大小写",
                            fontSize = 11.5.sp,
                            fontWeight = if (isCaseSensitive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCaseSensitive) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // 右侧：匹配结果计数与上一条/下一条
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (totalSearchResults > 0) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "${currentSearchIndex + 1}/$totalSearchResults",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            )
                        }
                    } else if (searchText.isNotEmpty()) {
                        Text("无匹配", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = onSearchPrev,
                        enabled = totalSearchResults > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "上一个",
                            modifier = Modifier.size(18.dp),
                            tint = if (totalSearchResults > 0) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = onSearchNext,
                        enabled = totalSearchResults > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "下一个",
                            modifier = Modifier.size(18.dp),
                            tint = if (totalSearchResults > 0) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 筛选面板
 */
@Composable
fun FilterPanel(
    viewModel: LogViewerViewModel,
    uiState: LogViewerViewModel.UiState,
    onDismiss: () -> Unit
) {
    FilterPanelContent(
        filterKeyword = uiState.filterKeyword,
        isCaptureLog = uiState.isCaptureLog,
        showH5 = uiState.showH5,
        showBottom = uiState.showBottom,
        onFilterChange = { viewModel.setFilterKeyword(it) },
        onToggleH5 = { viewModel.toggleShowH5() },
        onToggleBottom = { viewModel.toggleShowBottom() },
        onClearFilter = { viewModel.clearFilter() },
        onDismiss = onDismiss
    )
}

/**
 * 筛选栏无状态 2 行内容组件
 */
@Composable
fun FilterPanelContent(
    filterKeyword: String,
    isCaptureLog: Boolean,
    showH5: Boolean,
    showBottom: Boolean,
    onFilterChange: (String) -> Unit,
    onToggleH5: () -> Unit,
    onToggleBottom: () -> Unit,
    onClearFilter: () -> Unit,
    onDismiss: () -> Unit
) {
    var filterText by remember(filterKeyword) { mutableStateOf(filterKeyword) }
    val filterColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = containerColor,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // 第一行：关键字过滤输入框 + 清空 + 关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.FilterList,
                    contentDescription = null,
                    tint = filterColor,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (filterText.isEmpty()) {
                        Text(
                            "按关键字实时过滤日志行...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = filterText,
                        onValueChange = {
                            filterText = it
                            onFilterChange(it)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(filterColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filterText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            filterText = ""
                            onClearFilter()
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "清除关键字",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭筛选",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 第二行：抓包分类切换胶囊 & 状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCaptureLog) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier.clickable { onToggleH5() },
                            shape = RoundedCornerShape(6.dp),
                            color = if (showH5) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(0.5.dp, if (showH5) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent)
                        ) {
                            Text(
                                "🌐 H5 容器",
                                fontSize = 11.5.sp,
                                fontWeight = if (showH5) FontWeight.Bold else FontWeight.Normal,
                                color = if (showH5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier.clickable { onToggleBottom() },
                            shape = RoundedCornerShape(6.dp),
                            color = if (showBottom) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(0.5.dp, if (showBottom) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent)
                        ) {
                            Text(
                                "⚡ 底层 RPC",
                                fontSize = 11.5.sp,
                                fontWeight = if (showBottom) FontWeight.Bold else FontWeight.Normal,
                                color = if (showBottom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        if (filterText.isEmpty()) "支持输入任意文本实时过滤" else "已应用过滤",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (filterText.isNotEmpty()) {
                    Text(
                        "重置过滤",
                        fontSize = 11.5.sp,
                        color = filterColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                filterText = ""
                                onClearFilter()
                            }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 状态栏
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
    // 超长行截断保护：超过阈值时折叠显示，避免 Compose Text 布局计算卡死主线程
    val MAX_DISPLAY_LENGTH = 2000
    val isLongLine = line.length > MAX_DISPLAY_LENGTH
    var isExpanded by remember(line) { mutableStateOf(false) }
    val displayLine = if (isLongLine && !isExpanded) {
        line.substring(0, MAX_DISPLAY_LENGTH)
    } else {
        line
    }
    // levelColor 只检查行首 200 字符，避免在超长行上做全文 contains
    val colorCheckStr = if (line.length > 200) line.substring(0, 200) else line
    // 根据主题深浅选择对比度合适的颜色
    val isDarkTheme = isSystemInDarkTheme()

    val lineAnnotatedString = remember(displayLine, searchHighlightState) {
        buildAnnotatedString {
            val keyword = searchHighlightState.keyword
            val results = searchHighlightState.results
            val currentIndex = searchHighlightState.currentIndex
            val currentResult = if (currentIndex in results.indices) results[currentIndex] else null

            val lineResults = if (results.isNotEmpty() && keyword.isNotEmpty()) {
                results.filter { it.lineIndex == originalIndex && it.charIndex < displayLine.length }
            } else {
                emptyList()
            }

            val levelColor = when {
                // 错误日志：printStackTrace 输出 "error:" / "Throwable error:" / "Exception error:" 前缀
                colorCheckStr.contains("error:", ignoreCase = true) || 
                colorCheckStr.contains("Throwable error:", ignoreCase = true) || 
                colorCheckStr.contains("Exception error:", ignoreCase = true) -> 
                    if (isDarkTheme) Color(0xFFEF9A9A) else Color(0xFFC62828)
                // RPC 抓包标记行
                colorCheckStr.startsWith("[BOTTOM]", ignoreCase = true) || 
                colorCheckStr.startsWith("[H5]", ignoreCase = true) -> 
                    if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1565C0)
                // RPC 结构行
                colorCheckStr.startsWith("Method:", ignoreCase = true) -> 
                    if (isDarkTheme) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                colorCheckStr.startsWith("Data:", ignoreCase = true) -> 
                    if (isDarkTheme) Color(0xFFCE93D8) else Color(0xFF6A1B9A)
                colorCheckStr.startsWith("Params:", ignoreCase = true) -> 
                    if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFE65100)
                colorCheckStr.startsWith("TimeStamp:", ignoreCase = true) || 
                colorCheckStr.startsWith("<===") -> 
                    if (isDarkTheme) Color(0xFFB0BEC5) else Color(0xFF546E7A)
                else -> Color.Unspecified
            }

            if (lineResults.isNotEmpty()) {
                var lastCharIndex = 0
                lineResults.sortedBy { it.charIndex }.forEach { result ->
                    val preText = displayLine.substring(lastCharIndex, result.charIndex)
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
                        append(displayLine.substring(result.charIndex, result.charIndex + result.length))
                    }
                    lastCharIndex = result.charIndex + result.length
                }

                if (lastCharIndex < displayLine.length) {
                    val postText = displayLine.substring(lastCharIndex)
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
                        append(displayLine)
                    }
                } else {
                    append(displayLine)
                }
            }

            // 截断提示
            if (isLongLine && !isExpanded) {
                withStyle(style = SpanStyle(color = Color(0xFFFFB74D), fontStyle = FontStyle.Italic)) {
                    append("\n... [已截断 ${line.length - MAX_DISPLAY_LENGTH} 字符，点击展开全部]")
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
                onClick = {
                    // 超长行点击切换展开/折叠
                    if (isLongLine) isExpanded = !isExpanded
                    onLineClick()
                },
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
                    .padding(end = 2.dp)
                    .size(width = 20.dp, height = 0.dp) // 极简占位：强制压缩宽高以缩小边距
                    .wrapContentSize(unbounded = true) // 允许超出边界绘制而不撑高、撑宽父 Row
                    .scale(0.8f)
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
                .padding(
                    start = 8.dp, 
                    end = 24.dp, 
                    top = 8.dp, 
                    bottom = if (uiState.isSelectionMode) 100.dp else 8.dp
                )
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

                // 稳定回调 lambda 从而避免重组（不再将 displayedLines 作为 key，避免全量重组）
                val onLineClick = remember(index, uiState.isSelectionMode) {
                    {
                        if (uiState.isSelectionMode) {
                            viewModel.toggleLineSelection(index)
                        } else {
                            // 延迟到点击时读取最新的 displayedLines，避免闭包捕获导致重组
                            val currentLines = viewModel.uiState.value.displayedLines
                            activeDetailLine = currentLines.getOrNull(index) ?: line
                            activeDetailBlock = findRpcBlockAround(currentLines, index)
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
    var trackHeightPx by remember { mutableIntStateOf(0) }

    val firstVisibleIndex = lazyListState.firstVisibleItemIndex
    val layoutInfo = lazyListState.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    val visibleItemsCount = visibleItemsInfo.size

    if (visibleItemsCount >= totalItems) return

    // 采用像素级滚动偏移 + 末尾精确判定，解决「页面到底但滑块未到底」的问题
    val scrollFraction = run {
        if (visibleItemsInfo.isEmpty() || totalItems <= 1) return@run 0f

        val lastItem = visibleItemsInfo.last()
        val viewportEnd = layoutInfo.viewportEndOffset

        // 精确判断是否已到底：最后一个可见 item 是列表末尾且底边在视口内
        if (lastItem.index == totalItems - 1 && lastItem.offset + lastItem.size <= viewportEnd) {
            return@run 1f
        }

        val firstItem = visibleItemsInfo.first()
        val itemFraction = if (firstItem.size > 0) {
            (-firstItem.offset).toFloat() / firstItem.size.toFloat()
        } else 0f

        val maxScrollIndex = (totalItems - visibleItemsCount).coerceAtLeast(1)
        val exactIndex = firstVisibleIndex.toFloat() + itemFraction.coerceIn(0f, 1f)
        (exactIndex / maxScrollIndex).coerceIn(0f, 1f)
    }

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
    // 放弃使用剧烈波动的 visibleItemsCount，改用相对固定的视野容量（如20行）计算出稳定的滑块大小
    val thumbHeightPx = (trackHeightPx * (20f / totalItems.coerceAtLeast(20))).coerceIn(minThumbHeightPx, maxOf(minThumbHeightPx, trackHeightPx / 2f))
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

    val currentTotalItems by rememberUpdatedState(totalItems)
    val currentMaxThumbOffset by rememberUpdatedState(maxThumbOffset)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)

    // 拖拽目标索引：pointer 事件只更新此值，由单独的 LaunchedEffect 驱动滚动，避免频繁 cancel/launch
    var dragTargetIndex by remember { mutableIntStateOf(-1) }

    // 单个长期协程监听 dragTargetIndex 变化并驱动滚动，不再每帧 cancel/launch
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        var lastScrolledIndex = -1
        while (isDragging) {
            val target = dragTargetIndex
            if (target >= 0 && target != lastScrolledIndex) {
                lastScrolledIndex = target
                lazyListState.scrollToItem(target)
            }
            delay(16L) // ~60fps 帧同步
        }
    }

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
                        dragTargetIndex = (fraction * (currentTotalItems - 1)).roundToInt().coerceIn(0, currentTotalItems - 1)
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
                                    dragTargetIndex = (currentFraction * (currentTotalItems - 1)).roundToInt().coerceIn(0, currentTotalItems - 1)
                                }
                                dragEvent = pointerChange
                            } else {
                                break
                            }
                        }
                    } finally {
                        isDragging = false
                        dragTargetIndex = -1
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
        val density = LocalDensity.current

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
    val context = LocalContext.current
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
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("复制请求", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                        if (!block.method.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, block.method)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("复制 Method", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                        if (!block.params.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, block.params)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("复制 Params", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
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
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("复制 Data", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, block.rawText)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("复制全文", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
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
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("搜索 Method", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                        if (!block.params.isNullOrEmpty()) {
                            TextButton(
                                onClick = {
                                    val searchKey = if (block.params.length > 50) block.params.take(50) else block.params
                                    viewModel.setSearchKeyword(searchKey)
                                    viewModel.performSearch()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("搜索 Params", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
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
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("复制整行", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        OutlinedButton(
                            onClick = {
                                val searchKey = if (line.length > 50) line.take(50) else line
                                viewModel.setSearchKeyword(searchKey)
                                viewModel.performSearch()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("搜索整行", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("关闭", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
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

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("copied_text", text))
        ToastUtil.showToast(context, "已复制到剪贴板")
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

    // 自动追踪滑动：根据当前页面视野的最顶端日志，寻找距离它最近的 RPC 锚点
    LaunchedEffect(lazyListState.firstVisibleItemIndex, boundaryIndices) {
        if (boundaryIndices.isNotEmpty()) {
            val currentTopIndex = lazyListState.firstVisibleItemIndex
            var bestIndex = 0
            for (i in boundaryIndices.indices) {
                if (boundaryIndices[i] <= currentTopIndex) {
                    bestIndex = i
                } else {
                    break
                }
            }
            if (trackedIndex != bestIndex) {
                trackedIndex = bestIndex
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
    val context = LocalContext.current

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
