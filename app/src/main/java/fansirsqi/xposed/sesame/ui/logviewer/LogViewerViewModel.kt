package fansirsqi.xposed.sesame.ui.logviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * 日志查看器 ViewModel
 *
 * 功能：
 * - 日志显示和实时更新
 * - 搜索功能（支持正则表达式、大小写敏感）
 * - 筛选功能
 * - 日志级别过滤（ERROR/WARN/INFO/DEBUG）
 * - 虚拟滚动优化
 */
class LogViewerViewModel : ViewModel() {

    companion object {
        private const val TAG = "LogViewerViewModel"
        private const val POLL_INTERVAL = 1000L // 文件轮询间隔（毫秒）
        private const val PREF_FONT_SIZE = "log_viewer_font_size"
        private const val DEFAULT_FONT_SIZE = 9 // 默认字体大小改为9sp
        private const val MAX_RENDER_LINES = 150000
    }

    /**
     * 日志级别枚举
     */
    enum class LogLevel(val displayName: String, val pattern: Regex) {
        ERROR("ERROR", Regex("\\b(ERROR|SEVERE|FATAL)\\b", RegexOption.IGNORE_CASE)),
        WARN("WARN", Regex("\\b(WARN|WARNING)\\b", RegexOption.IGNORE_CASE)),
        INFO("INFO", Regex("\\bINFO\\b", RegexOption.IGNORE_CASE)),
        DEBUG("DEBUG", Regex("\\b(DEBUG|TRACE|VERBOSE)\\b", RegexOption.IGNORE_CASE))
    }

    /**
     * UI 状态数据类
     */
    data class UiState(
        val displayedLines: List<String> = emptyList(),
        val displayedLineIndices: List<Int> = emptyList(), // 保存过滤后每一行在原始文本中的索引
        val filterKeyword: String = "",
        val searchKeyword: String = "",
        val searchResults: List<SearchResult> = emptyList(),
        val currentSearchIndex: Int = -1,
        val enabledLogLevels: Set<LogLevel> = LogLevel.entries.toSet(),
        val isRegexSearch: Boolean = false,
        val isCaseSensitive: Boolean = false,
        val autoScroll: Boolean = true,
        val statusMessage: String = "就绪",
        val isLoading: Boolean = false,
        val fontSize: Int = DEFAULT_FONT_SIZE, // 使用默认字体大小
        val showH5: Boolean = true,
        val showBottom: Boolean = true,
        val isCaptureLog: Boolean = false,
        val showLineCopyButton: Boolean = false,
        val isSelectionMode: Boolean = false,
        val selectedIndices: Set<Int> = emptySet(),
        val lastSelectedIndex: Int? = null
    )

    /**
     * 搜索结果数据类
     */
    data class SearchResult(
        val lineIndex: Int,
        val charIndex: Int,
        val length: Int
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var watchJob: Job? = null
    private var progressiveRenderJob: Job? = null
    private var raf: RandomAccessFile? = null
    private var watchingFile: File? = null

    private val allLines = ArrayList<String>()
    private var endsWithNewline = true

    init {
        // 从持久化存储加载字体大小
        loadFontSize()
    }

    /**
     * 从 DataStore 加载字体大小
     */
    private fun loadFontSize() {
        try {
            val savedSize = fansirsqi.xposed.sesame.util.DataStore.get(
                PREF_FONT_SIZE,
                Int::class.java
            ) ?: DEFAULT_FONT_SIZE
            _uiState.update { it.copy(fontSize = savedSize) }
        } catch (e: Exception) {
            Log.error(TAG, "加载字体大小失败: ${e.message}")
        }
    }

    /**
     * 保存字体大小到 DataStore
     */
    private fun saveFontSize(size: Int) {
        try {
            fansirsqi.xposed.sesame.util.DataStore.put(PREF_FONT_SIZE, size)
        } catch (e: Exception) {
            Log.error(TAG, "保存字体大小失败: ${e.message}")
        }
    }

    /**
     * 设置完整日志文本
     */
    fun setFullText(text: String) {
        if (text.isEmpty()) {
            synchronized(allLines) {
                allLines.clear()
                endsWithNewline = true
            }
            applyFilters()
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val lines = mutableListOf<String>()
            var hasH5 = false
            var hasBottom = false
            val rawLines = text.split('\n')
            rawLines.forEachIndexed { i, line ->
                if (i == rawLines.size - 1 && line.isEmpty() && text.endsWith("\n")) {
                    return@forEachIndexed
                }
                if (line.contains("[H5] ========================>")) {
                    hasH5 = true
                }
                if (line.contains("[BOTTOM] ========================>")) {
                    hasBottom = true
                }
                lines.add(line)
            }
            synchronized(allLines) {
                allLines.clear()
                allLines.addAll(lines)
                endsWithNewline = text.endsWith("\n")
            }
            _uiState.update { state ->
                state.copy(
                    isCaptureLog = hasH5 || hasBottom,
                    statusMessage = "日志已加载"
                )
            }
            applyFilters()
        }
    }

    /**
     * 从文件加载日志（流式读取，防OOM）
     */
    fun loadFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "加载中...") }
            try {
                val lines = ArrayList<String>()
                var hasH5 = false
                var hasBottom = false

                file.bufferedReader(Charsets.UTF_8).useLines { sequence ->
                    sequence.forEach { line ->
                        if (line.contains("[H5] ========================>")) {
                            hasH5 = true
                        }
                        if (line.contains("[BOTTOM] ========================>")) {
                            hasBottom = true
                        }
                        lines.add(line)
                    }
                }

                val endsWithNl = if (file.length() > 0) {
                    try {
                        RandomAccessFile(file, "r").use { raf ->
                            raf.seek(file.length() - 1)
                            raf.read() == '\n'.code
                        }
                    } catch (e: Exception) {
                        true
                    }
                } else {
                    true
                }

                synchronized(allLines) {
                    allLines.clear()
                    allLines.addAll(lines)
                    endsWithNewline = endsWithNl
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isCaptureLog = hasH5 || hasBottom,
                        statusMessage = "已加载 ${lines.size} 行"
                    )
                }
                applyFilters()
            } catch (e: Exception) {
                Log.error(TAG, "加载文件失败: ${e.message}")
                _uiState.update { it.copy(isLoading = false, statusMessage = "加载失败: ${e.message}") }
            }
        }
    }

    fun appendLog(chunk: String) {
        if (chunk.isEmpty()) return
        val rawLines = chunk.split('\n')
        if (rawLines.isEmpty()) return

        synchronized(allLines) {
            var startIdx = 0
            if (!endsWithNewline && allLines.isNotEmpty()) {
                val lastIdx = allLines.size - 1
                allLines[lastIdx] = allLines[lastIdx] + rawLines[0]
                startIdx = 1
            }

            var hasH5 = false
            var hasBottom = false

            for (i in startIdx until rawLines.size) {
                val line = rawLines[i]
                if (i == rawLines.size - 1 && line.isEmpty() && chunk.endsWith("\n")) {
                    break
                }
                if (line.contains("[H5] ========================>")) {
                    hasH5 = true
                }
                if (line.contains("[BOTTOM] ========================>")) {
                    hasBottom = true
                }
                allLines.add(line)
            }

            endsWithNewline = chunk.endsWith("\n")

            // 限制内存
            val maxLines = 150000
            if (allLines.size > maxLines) {
                val toRemove = allLines.size - 100000
                if (toRemove > 0) {
                    allLines.subList(0, toRemove).clear()
                }
            }

            _uiState.update { state ->
                state.copy(
                    isCaptureLog = state.isCaptureLog || hasH5 || hasBottom
                )
            }
        }
        applyFilters()
    }

    /**
     * 清空日志
     */
    fun clearLog() {
        synchronized(allLines) {
            allLines.clear()
            endsWithNewline = true
        }
        _uiState.update {
            UiState(statusMessage = "已清空显示")
        }
    }

    /**
     * 设置筛选关键字
     */
    fun setFilterKeyword(keyword: String) {
        _uiState.update { it.copy(filterKeyword = keyword) }
        applyFilters()
    }

    /**
     * 清除筛选
     */
    fun clearFilter() {
        _uiState.update { it.copy(filterKeyword = "") }
        applyFilters()
    }

    /**
     * 设置搜索关键字
     */
    fun setSearchKeyword(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
    }

    /**
     * 执行搜索
     */
    fun performSearch() {
        val state = _uiState.value
        val keyword = state.searchKeyword.trim()
        if (keyword.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val results = mutableListOf<SearchResult>()
                val lines = synchronized(allLines) { allLines.toList() }

                if (state.isRegexSearch) {
                    // 正则表达式搜索
                    val options = if (state.isCaseSensitive) {
                        emptySet()
                    } else {
                        setOf(RegexOption.IGNORE_CASE)
                    }
                    val regex = try {
                        Regex(keyword, options)
                    } catch (e: Exception) {
                        Log.error(TAG, "正则表达式错误: ${e.message}")
                        return@launch
                    }

                    lines.forEachIndexed { lineIndex, line ->
                        regex.findAll(line).forEach { match ->
                            results.add(
                                SearchResult(
                                    lineIndex = lineIndex,
                                    charIndex = match.range.first,
                                    length = match.value.length
                                )
                            )
                        }
                    }
                } else {
                    // 普通搜索
                    lines.forEachIndexed { lineIndex, line ->
                        val searchText = if (state.isCaseSensitive) line else line.lowercase()
                        val searchKeyword = if (state.isCaseSensitive) keyword else keyword.lowercase()

                        var index = searchText.indexOf(searchKeyword)
                        while (index != -1) {
                            results.add(
                                SearchResult(
                                    lineIndex = lineIndex,
                                    charIndex = index,
                                    length = keyword.length
                                )
                            )
                            index = searchText.indexOf(searchKeyword, index + 1)
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        searchResults = results,
                        currentSearchIndex = if (results.isNotEmpty()) 0 else -1,
                        statusMessage = if (results.isNotEmpty()) {
                            "找到 ${results.size} 个结果"
                        } else {
                            "未找到结果"
                        }
                    )
                }
            } catch (e: Exception) {
                Log.error(TAG, "搜索失败: ${e.message}")
            }
        }
    }

    /**
     * 下一个搜索结果
     */
    fun searchNext() {
        _uiState.update { state ->
            if (state.searchResults.isEmpty()) return@update state
            val nextIndex = (state.currentSearchIndex + 1) % state.searchResults.size
            state.copy(
                currentSearchIndex = nextIndex,
                statusMessage = "${nextIndex + 1}/${state.searchResults.size}"
            )
        }
    }

    /**
     * 上一个搜索结果
     */
    fun searchPrev() {
        _uiState.update { state ->
            if (state.searchResults.isEmpty()) return@update state
            val prevIndex = (state.currentSearchIndex - 1 + state.searchResults.size) % state.searchResults.size
            state.copy(
                currentSearchIndex = prevIndex,
                statusMessage = "${prevIndex + 1}/${state.searchResults.size}"
            )
        }
    }

    /**
     * 清除搜索
     */
    fun clearSearch() {
        _uiState.update {
            it.copy(
                searchKeyword = "",
                searchResults = emptyList(),
                currentSearchIndex = -1,
                statusMessage = "就绪"
            )
        }
    }

    /**
     * 切换日志级别过滤
     */
    fun toggleLogLevel(level: LogLevel) {
        _uiState.update { state ->
            val newLevels = if (level in state.enabledLogLevels) {
                state.enabledLogLevels - level
            } else {
                state.enabledLogLevels + level
            }
            state.copy(enabledLogLevels = newLevels)
        }
        applyFilters()
    }

    /**
     * 切换正则表达式搜索
     */
    fun toggleRegexSearch() {
        _uiState.update { it.copy(isRegexSearch = !it.isRegexSearch) }
    }

    /**
     * 切换大小写敏感
     */
    fun toggleCaseSensitive() {
        _uiState.update { it.copy(isCaseSensitive = !it.isCaseSensitive) }
    }

    /**
     * 切换自动滚动
     */
    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScroll = !it.autoScroll) }
    }

    /**
     * 增大字体
     */
    fun increaseFontSize() {
        _uiState.update { state ->
            val newSize = (state.fontSize + 1).coerceAtMost(24)
            saveFontSize(newSize)
            state.copy(fontSize = newSize)
        }
    }

    /**
     * 减小字体
     */
    fun decreaseFontSize() {
        _uiState.update { state ->
            val newSize = (state.fontSize - 1).coerceAtLeast(8)
            saveFontSize(newSize)
            state.copy(fontSize = newSize)
        }
    }

    /**
     * 重置字体大小
     */
    fun resetFontSize() {
        saveFontSize(DEFAULT_FONT_SIZE)
        _uiState.update { it.copy(fontSize = DEFAULT_FONT_SIZE) }
    }

    /**
     * 直接设置字体大小（用于双指缩放）
     */
    fun setFontSize(size: Int) {
        val clamped = size.coerceIn(6, 36)
        saveFontSize(clamped)
        _uiState.update { it.copy(fontSize = clamped) }
    }

    fun toggleShowH5() {
        _uiState.update { it.copy(showH5 = !it.showH5) }
        applyFilters()
    }

    fun toggleShowBottom() {
        _uiState.update { it.copy(showBottom = !it.showBottom) }
        applyFilters()
    }

    fun toggleShowLineCopyButton() {
        _uiState.update { it.copy(showLineCopyButton = !it.showLineCopyButton) }
    }

    private fun applyFilters() {
        progressiveRenderJob?.cancel()
        viewModelScope.launch(Dispatchers.Default) {
            val stateSnapshot = _uiState.value
            val lines = synchronized(allLines) { allLines.toList() }

            val filteredLines = ArrayList<String>(lines.size)
            val filteredIndices = ArrayList<Int>(lines.size)

            var currentBlockType = 0 // 0: none/normal, 1: H5, 2: BOTTOM
            lines.forEachIndexed { index, line ->
                var processedLine = line
                if (line.contains("[H5] ========================>")) {
                    currentBlockType = 1
                } else if (line.contains("[BOTTOM] ========================>")) {
                    currentBlockType = 2
                }

                val skipBlock = (currentBlockType == 1 && !stateSnapshot.showH5) ||
                    (currentBlockType == 2 && !stateSnapshot.showBottom)

                if (processedLine.startsWith("[H5] ")) {
                    processedLine = processedLine.substring(5)
                } else if (processedLine.startsWith("[BOTTOM] ")) {
                    processedLine = processedLine.substring(9)
                }

                if (line.contains("<========================")) {
                    currentBlockType = 0
                }

                if (skipBlock) {
                    return@forEachIndexed
                }

                val matchesFilter = stateSnapshot.filterKeyword.isEmpty() ||
                    processedLine.contains(stateSnapshot.filterKeyword, ignoreCase = true)

                val matchesLevel = if (stateSnapshot.enabledLogLevels.size == LogLevel.entries.size) {
                    true
                } else {
                    stateSnapshot.enabledLogLevels.any { level ->
                        level.pattern.containsMatchIn(processedLine)
                    }
                }

                if (matchesFilter && matchesLevel) {
                    filteredLines.add(processedLine)
                    filteredIndices.add(index)
                }
            }

            val baseMsg = if (stateSnapshot.filterKeyword.isNotEmpty() ||
                stateSnapshot.enabledLogLevels.size < LogLevel.entries.size ||
                !stateSnapshot.showH5 || !stateSnapshot.showBottom
            ) {
                "筛选结果: ${filteredLines.size}/${lines.size} 行"
            } else {
                "共 ${lines.size} 行"
            }

            val renderTotal = filteredLines.size.coerceAtMost(MAX_RENDER_LINES)
            val droppedLines = filteredLines.size - renderTotal
            val startIndex = (filteredLines.size - renderTotal).coerceAtLeast(0)
            val renderSourceLines = filteredLines.subList(startIndex, filteredLines.size)
            val renderSourceIndices = filteredIndices.subList(startIndex, filteredIndices.size)

            withContext(Dispatchers.Main) {
                progressiveRenderJob?.cancel()
                if (renderTotal == 0) {
                    _uiState.update {
                        it.copy(
                            displayedLines = emptyList(),
                            displayedLineIndices = emptyList(),
                            statusMessage = baseMsg
                        )
                    }
                    return@withContext
                }

                val truncateSuffix = if (droppedLines > 0) {
                    " (为保证流畅已隐藏更早 ${droppedLines} 行)"
                } else {
                    ""
                }

                _uiState.update {
                    it.copy(
                        displayedLines = renderSourceLines.toList(),
                        displayedLineIndices = renderSourceIndices.toList(),
                        statusMessage = baseMsg + truncateSuffix
                    )
                }
            }
        }
    }

    /**
     * 开始监听文件变化
     */
    fun startWatchingFile(path: String) {
        stopWatchingFile()

        val file = File(path)
        if (!file.exists()) {
            Log.error(TAG, "文件不存在: $path")
            return
        }

        watchingFile = file

        viewModelScope.launch(Dispatchers.IO) {
            try {
                raf = RandomAccessFile(file, "r").apply {
                    // 从文件末尾开始（只看新增内容）
                    seek(file.length())
                }

                watchJob = launch {
                    while (isActive) {
                        try {
                            val currentFile = watchingFile ?: break
                            val currentRaf = raf ?: break

                            val newLen = currentFile.length()

                            // 文件被清空或轮转：回到开头
                            if (newLen < currentRaf.filePointer) {
                                currentRaf.seek(0)
                            }

                            if (newLen > currentRaf.filePointer) {
                                val chunk = ByteArray((newLen - currentRaf.filePointer).toInt())
                                currentRaf.readFully(chunk)
                                val text = String(chunk, Charsets.UTF_8)

                                withContext(Dispatchers.Main) {
                                    appendLog(text)
                                }
                            }
                        } catch (e: Exception) {
                            Log.error(TAG, "文件监听错误: ${e.message}")
                        }

                        delay(POLL_INTERVAL)
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "启动文件监听失败: ${e.message}")
            }
        }
    }

    /**
     * 停止监听文件变化
     */
    fun stopWatchingFile() {
        watchJob?.cancel()
        watchJob = null
        progressiveRenderJob?.cancel()
        progressiveRenderJob = null

        try {
            raf?.close()
        } catch (e: Exception) {
            Log.error(TAG, "关闭文件失败: ${e.message}")
        }
        raf = null
        watchingFile = null
    }

    fun toggleSelectionMode(enabled: Boolean) {
        _uiState.update { 
            it.copy(
                isSelectionMode = enabled,
                selectedIndices = if (enabled) it.selectedIndices else emptySet(),
                lastSelectedIndex = if (enabled) it.lastSelectedIndex else null
            )
        }
    }

    fun toggleLineSelection(index: Int) {
        _uiState.update { state ->
            val newSelected = if (index in state.selectedIndices) {
                state.selectedIndices - index
            } else {
                state.selectedIndices + index
            }
            state.copy(
                selectedIndices = newSelected,
                lastSelectedIndex = index
            )
        }
    }

    fun selectRange(from: Int, to: Int) {
        _uiState.update { state ->
            val range = if (from <= to) from..to else to..from
            val newSelected = state.selectedIndices + range.toSet()
            state.copy(
                selectedIndices = newSelected,
                lastSelectedIndex = to
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                selectedIndices = emptySet(),
                lastSelectedIndex = null
            )
        }
    }

    fun copySelectedLines(context: android.content.Context) {
        val state = _uiState.value
        val indices = state.selectedIndices.sorted()
        if (indices.isEmpty()) return
        
        val textToCopy = indices.mapNotNull { idx ->
            state.displayedLines.getOrNull(idx)
        }.joinToString("\n")
        
        try {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("selected_logs", textToCopy))
            fansirsqi.xposed.sesame.util.ToastUtil.showToast(context, "已复制选中的 ${indices.size} 行日志")
        } catch (e: Exception) {
            Log.error(TAG, "复制失败: ${e.message}")
        }
        
        clearSelection()
    }

    fun findBoundaryIndices(): List<Int> {
        val state = _uiState.value
        val list = mutableListOf<Int>()
        state.displayedLines.forEachIndexed { index, line ->
            if (line.contains("========================>")) {
                list.add(index)
            }
        }
        return list
    }

    // formatLongLines has been removed as chunking is done on the fly

    override fun onCleared() {
        super.onCleared()
        stopWatchingFile()
    }
}

