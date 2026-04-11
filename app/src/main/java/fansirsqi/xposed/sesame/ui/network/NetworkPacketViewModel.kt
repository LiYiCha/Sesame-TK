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

class NetworkPacketViewModel : ViewModel() {

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

    // 最终展示的响应式列表
    val displayPackets: StateFlow<List<CapturePacket>> = combine(_allPackets, _searchQuery) { packets, query ->
        if (query.isBlank()) {
            packets
        } else {
            val q = query.lowercase()
            packets.filter {
                it.url?.lowercase()?.contains(q) == true ||
                it.host?.lowercase()?.contains(q) == true ||
                it.method?.lowercase()?.contains(q) == true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 加载数据。如果 dateStr 为空，则自动寻找最佳日期（今日优先，否则寻找最近的历史记录）
     */
    fun loadData(dateStr: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            
            val finalDate = if (!dateStr.isNullOrBlank()) {
                dateStr
            } else {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val folders = CaptureFileManager.getDailyFolders()
                if (folders.contains(today)) {
                    today
                } else {
                    folders.firstOrNull() ?: today // 获取最新的文件夹（通常列表是倒序的）
                }
            }

            _viewingDate.value = finalDate
            _allPackets.value = CaptureFileManager.getPacketsForDate(finalDate)
            _isLoading.value = false
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            CaptureFileManager.clearAll()
            _allPackets.value = emptyList()
            _isLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
