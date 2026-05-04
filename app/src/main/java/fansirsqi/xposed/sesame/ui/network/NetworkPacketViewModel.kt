package fansirsqi.xposed.sesame.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.hook.network.CaptureFileManager
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class NetworkPacketViewModel : ViewModel() {

    private val TAG = "NetworkPacketViewModel"
    private val POLL_INTERVAL = 1000L

    private val _allPackets = MutableStateFlow<List<CapturePacket>>(emptyList())
    
    // 当前正在展示的日期
    private val _viewingDate = MutableStateFlow<String>("")
    val viewingDate: StateFlow<String> = _viewingDate

    // 搜索关键字状态
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 自动滚动
    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll

    private var watchJob: Job? = null
    private var raf: RandomAccessFile? = null

    // 最终展示的响应式列表
    val displayPackets: StateFlow<List<CapturePacket>> = combine(_allPackets, _searchQuery) { packets, query ->
        if (query.isBlank()) {
            packets
        } else {
            val q = query.lowercase()
            packets.filter {
                it.url.lowercase().contains(q) ||
                it.host.lowercase().contains(q) ||
                it.method.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statistics: StateFlow<NetworkStats> = displayPackets.map { packets ->
        val total = packets.size
        val success = packets.count { it.responseCode in 200..299 }
        val error = packets.count { it.responseCode >= 400 || it.responseCode == 0 }
        val rate = if (total > 0) (success.toFloat() / total * 100).toInt() else 0
        NetworkStats(total, success, error, rate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkStats())

    data class NetworkStats(
        val total: Int = 0,
        val success: Int = 0,
        val error: Int = 0,
        val successRate: Int = 0
    )

    /**
     * 加载数据。如果 dateStr 为空，则自动寻找最佳日期（今日优先，否则寻找最近的历史记录）
     */
    fun loadData(dateStr: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val finalDate = if (!dateStr.isNullOrBlank()) {
                dateStr
            } else {
                val folders = CaptureFileManager.getDailyFolders()
                if (folders.contains(today)) {
                    today
                } else {
                    folders.firstOrNull() ?: today
                }
            }

            _viewingDate.value = finalDate
            _allPackets.value = CaptureFileManager.getPacketsForDate(finalDate)
            _isLoading.value = false

            // 如果是今天，开启实时监听
            if (finalDate == today) {
                startWatching()
            } else {
                stopWatching()
            }
        }
    }

    private fun startWatching() {
        stopWatching()
        val logFile = File(Files.LOG_DIR, "http.log")
        if (!logFile.exists()) return

        watchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                raf = RandomAccessFile(logFile, "r").apply {
                    seek(logFile.length())
                }

                while (isActive) {
                    val currentLen = logFile.length()
                    val currentPointer = raf?.filePointer ?: 0L

                    if (currentLen < currentPointer) {
                        raf?.seek(0)
                    }

                    if (currentLen > currentPointer) {
                        var line = raf?.readLine()
                        while (line != null) {
                            // readLine() 读取的是 ISO-8859-1，需要转回 UTF-8
                            val utf8Line = String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                            CaptureFileManager.parseLine(utf8Line)?.let { packet ->
                                withContext(Dispatchers.Main) {
                                    _allPackets.value = listOf(packet) + _allPackets.value
                                }
                            }
                            line = raf?.readLine()
                        }
                    }
                    delay(POLL_INTERVAL)
                }
            } catch (e: Exception) {
                Log.error(TAG, "实时监听失败: ${e.message}")
            }
        }
    }

    private fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        try {
            raf?.close()
        } catch (e: Exception) { }
        raf = null
    }

    fun clearCurrentDateLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val date = _viewingDate.value
            CaptureFileManager.clearForDate(date)
            _allPackets.value = emptyList()
            _isLoading.value = false
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            CaptureFileManager.clearAll()
            _allPackets.value = emptyList()
            _isLoading.value = false
        }
    }

    fun toggleAutoScroll() {
        _autoScroll.value = !_autoScroll.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getDailyFolders(): List<String> {
        return CaptureFileManager.getDailyFolders()
    }

    override fun onCleared() {
        super.onCleared()
        stopWatching()
    }
}
