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
        val fullLogText: String = "",
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
        val fontSize: Int = DEFAULT_FONT_SIZE // 使用默认字体大小
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
    private var raf: RandomAccessFile? = null
    private var watchingFile: File? = null

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
        _uiState.update { state ->
            state.copy(
                fullLogText = text,
                statusMessage = "日志已加载"
            )
        }
        applyFilters()
    }

    /**
     * 追加日志文本
     */
    fun appendLog(chunk: String) {
        if (chunk.isEmpty()) return
        _uiState.update { state ->
            state.copy(fullLogText = state.fullLogText + chunk)
        }
        applyFilters()
    }

    /**
     * 清空日志
     */
    fun clearLog() {
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
                val lines = state.fullLogText.split('\n')

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

    private fun applyFilters() {
        viewModelScope.launch(Dispatchers.Default) {
            val state = _uiState.value
            val lines = state.fullLogText.split('\n')

            val filteredLines = mutableListOf<String>()
            val filteredIndices = mutableListOf<Int>()

            lines.forEachIndexed { index, line ->
                // 应用关键字筛选
                val matchesFilter = state.filterKeyword.isEmpty() ||
                                   line.contains(state.filterKeyword, ignoreCase = true)

                // 应用日志级别过滤
                val matchesLevel = if (state.enabledLogLevels.size == LogLevel.entries.size) {
                    true // 所有级别都启用，不需要过滤
                } else {
                    state.enabledLogLevels.any { level ->
                        level.pattern.containsMatchIn(line)
                    }
                }

                if (matchesFilter && matchesLevel) {
                    filteredLines.add(line)
                    filteredIndices.add(index)
                }
            }

            _uiState.update {
                it.copy(
                    displayedLines = filteredLines,
                    displayedLineIndices = filteredIndices,
                    statusMessage = if (state.filterKeyword.isNotEmpty() ||
                                      state.enabledLogLevels.size < LogLevel.entries.size) {
                        "筛选结果: ${filteredLines.size}/${lines.size} 行"
                    } else {
                        "就绪"
                    }
                )
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

        try {
            raf?.close()
        } catch (e: Exception) {
            Log.error(TAG, "关闭文件失败: ${e.message}")
        }
        raf = null
        watchingFile = null
    }

    override fun onCleared() {
        super.onCleared()
        stopWatchingFile()
    }
}
