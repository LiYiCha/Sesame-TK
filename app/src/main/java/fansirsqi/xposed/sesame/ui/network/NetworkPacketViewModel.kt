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
import java.io.File
import java.io.RandomAccessFile
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map

class NetworkPacketViewModel : ViewModel() {

    private val TAG = "NetworkPacketViewModel"
    private val POLL_INTERVAL = 1000L

    private val _allPackets = MutableStateFlow<List<CapturePacket>>(emptyList())
    
    private val _viewingDate = MutableStateFlow<String>("")
    val viewingDate: StateFlow<String> = _viewingDate

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _blacklist = MutableStateFlow<List<String>>(emptyList())
    val blacklist: StateFlow<List<String>> = _blacklist

    init {
        refreshBlacklist()
    }

    private fun refreshBlacklist() {
        val filter = fansirsqi.xposed.sesame.model.BaseModel.httpCaptureFilter.value ?: ""
        _blacklist.value = filter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun toggleBlacklist(domain: String) {
        val current = _blacklist.value.toMutableList()
        if (current.contains(domain)) {
            current.remove(domain)
        } else {
            current.add(domain)
        }
        updateBlacklist(current)
    }

    fun renameBlacklist(old: String, new: String) {
        val current = _blacklist.value.toMutableList()
        val index = current.indexOf(old)
        if (index != -1 && new.isNotBlank()) {
            current[index] = new.trim()
            updateBlacklist(current)
        }
    }

    fun updateBlacklist(newList: List<String>) {
        val filterStr = newList.distinct().joinToString(",")
        fansirsqi.xposed.sesame.model.BaseModel.httpCaptureFilter.value = filterStr
        fansirsqi.xposed.sesame.util.DataStore.put(
            fansirsqi.xposed.sesame.model.BaseModel.httpCaptureFilter.code,
            filterStr
        )
        _blacklist.value = newList
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll

    private var watchJob: Job? = null
    private var raf: RandomAccessFile? = null

    val displayPackets: StateFlow<List<CapturePacket>> = combine(_allPackets, _searchQuery, _blacklist) { packets, query, bl ->
        val searchFiltered = if (query.isBlank()) {
            packets
        } else {
            val q = query.lowercase()
            packets.filter {
                it.url.lowercase().contains(q) ||
                it.host.lowercase().contains(q) ||
                it.method.lowercase().contains(q)
            }
        }
        
        if (bl.isEmpty()) {
            searchFiltered
        } else {
            searchFiltered.filter { packet ->
                bl.none { keyword -> packet.host.contains(keyword, ignoreCase = true) }
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

    private val PAGE_SIZE = 50
    private var _rawLines: List<String> = emptyList()
    private var _currentOffset = 0

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore

    fun loadData(dateStr: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            
            val today = CaptureFileManager.getTodayDate()
            val finalDate = if (!dateStr.isNullOrBlank()) {
                dateStr
            } else {
                val folders = CaptureFileManager.getDailyFolders()
                if (folders.contains(today)) today else folders.firstOrNull() ?: today
            }

            _viewingDate.value = finalDate
            _rawLines = CaptureFileManager.getRawLinesForDate(finalDate)
            _currentOffset = 0
            
            val firstPage = loadNextPage(PAGE_SIZE)
            _allPackets.value = firstPage
            _hasMore.value = _currentOffset < _rawLines.size
            
            _isLoading.value = false

            if (finalDate == today) startWatching() else stopWatching()
        }
    }

    fun loadMore() {
        if (!_hasMore.value || _isLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val nextPage = loadNextPage(PAGE_SIZE)
            if (nextPage.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _allPackets.value = _allPackets.value + nextPage
                }
            }
            _hasMore.value = _currentOffset < _rawLines.size
            _isLoading.value = false
        }
    }

    private fun loadNextPage(size: Int): List<CapturePacket> {
        val end = (_currentOffset + size).coerceAtMost(_rawLines.size)
        if (_currentOffset >= end) return emptyList()
        val subList = _rawLines.subList(_currentOffset, end)
        _currentOffset = end
        return subList.mapNotNull { CaptureFileManager.parseLine(it) }
    }

    private var fileObserver: android.os.FileObserver? = null

    private fun startWatching() {
        stopWatching()
        val logFile = File(Files.LOG_DIR, "http.log")
        if (!logFile.exists()) return

        try {
            raf = RandomAccessFile(logFile, "r").apply {
                seek(logFile.length())
            }
            
            val observer = object : android.os.FileObserver(logFile.parent!!, android.os.FileObserver.MODIFY) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == "http.log") {
                        readNewLines()
                    }
                }
            }
            observer.startWatching()
            fileObserver = observer
        } catch (e: Exception) {
            Log.error(TAG, "实时监听初始化失败: ${e.message}")
        }
    }

    private fun readNewLines() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentLen = File(Files.LOG_DIR, "http.log").length()
                val currentPointer = raf?.filePointer ?: 0L
                
                if (currentLen < currentPointer) raf?.seek(0)
                if (currentLen > currentPointer) {
                    var line = raf?.readLine()
                    while (line != null) {
                        val utf8Line = String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                        CaptureFileManager.parseLine(utf8Line)?.let { packet ->
                            withContext(Dispatchers.Main) {
                                _allPackets.value = listOf(packet) + _allPackets.value
                            }
                        }
                        line = raf?.readLine()
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "读取新行失败: ${e.message}")
            }
        }
    }

    private fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
        try { raf?.close() } catch (e: Exception) { }
        raf = null
    }

    fun clearCurrentDateLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val date = _viewingDate.value
            CaptureFileManager.clearForDate(date)
            _rawLines = emptyList()
            _currentOffset = 0
            _allPackets.value = emptyList()
            _isLoading.value = false
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            CaptureFileManager.clearAll()
            _rawLines = emptyList()
            _currentOffset = 0
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

    fun addTestData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val today = CaptureFileManager.getTodayDate()
                val dateDir = File(CaptureFileManager.getCaptureDir(), today)
                if (!dateDir.exists()) dateDir.mkdirs()

                for (i in 1..5) {
                    val id = UUID.randomUUID().toString()
                    val reqBodyFile = File(dateDir, "req_$id.bin")
                    reqBodyFile.writeText("{\"test_key\": \"mock_request_data_$i\", \"action\": \"resend_test\"}")
                    val resBodyFile = File(dateDir, "res_$id.bin")
                    resBodyFile.writeText("{\"status\": \"success\", \"message\": \"Mock response $i\"}")

                    val packet = CapturePacket(
                        id = id,
                        url = "https://api.example.com/v1/user/profile?user_id=12345&token=mock_$i",
                        method = if (i % 2 == 0) "POST" else "GET",
                        host = "api.example.com",
                        startTime = System.currentTimeMillis() - (i * 1000),
                        responseCode = 200,
                        protocol = "HTTP/1.1",
                        contentType = "application/json",
                        requestHeaders = mapOf(
                            "User-Agent" to "Sesame-TK/0.2.8",
                            "Cookie" to "session_id=mock_$i"
                        ),
                        responseHeaders = mapOf("Content-Type" to "application/json"),
                        requestBodyFile = reqBodyFile.absolutePath,
                        responseBodyFile = resBodyFile.absolutePath,
                        duration = (100..500).random().toLong()
                    )
                    val metadataJson = fansirsqi.xposed.sesame.util.JsonUtil.formatJson(packet, false)
                    fansirsqi.xposed.sesame.util.Log.http(metadataJson)
                }
                withContext(Dispatchers.Main) { loadData(today) }
            } catch (e: Exception) {
                Log.error(TAG, "生成模拟数据异常: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWatching()
    }
}
