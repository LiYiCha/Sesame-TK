package fansirsqi.xposed.sesame.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.hook.network.CaptureClassifier
import fansirsqi.xposed.sesame.hook.network.CaptureSearchEngine
import fansirsqi.xposed.sesame.hook.network.CaptureStorage
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

class CaptureListViewModel : ViewModel() {

    private val TAG = "CaptureListViewModel"

    /** 所有已加载的原始记录 */
    private val _allRecords = MutableStateFlow<List<CaptureRecord>>(emptyList())

    /** 当前查看的日期 */
    private val _viewingDate = MutableStateFlow("")
    val viewingDate: StateFlow<String> = _viewingDate

    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** 分类筛选 (null = 全部) */
    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter

    /** 是否全局搜索模式 */
    val isGlobalSearch = MutableStateFlow(false)

    /** 黑名单关键词 */
    private val _blacklist = MutableStateFlow<List<String>>(emptyList())
    val blacklist: StateFlow<List<String>> = _blacklist

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** 是否有更多数据可加载 */
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore

    /** 自动滚动 */
    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll

    /** 当前日期加载的完整列表缓存（用于分页，避免重复读磁盘） */
    private var cachedFullList: List<CaptureRecord> = emptyList()
    private var pageOffset = 0
    private val PAGE_SIZE = 50
    private val MAX_MEMORY_RECORDS = 500 // 内存中保留的最大记录数

    /** 全局搜索结果 */
    private val _globalSearchResults = MutableStateFlow<List<CaptureRecord>>(emptyList())
    val globalSearchResults: StateFlow<List<CaptureRecord>> = _globalSearchResults

    init {
        refreshBlacklist()
    }

    /** 展示用的过滤后列表 */
    val displayRecords: StateFlow<List<CaptureRecord>> =
        combine(_allRecords, _searchQuery, _categoryFilter, _blacklist) { records, query, cat, bl ->
            var filtered = records

            // 分类筛选
            if (cat != null) {
                filtered = filtered.filter { it.category == cat }
            }

            // 搜索
            if (query.isNotBlank()) {
                val q = query.lowercase()
                filtered = filtered.filter { record ->
                    record.displayTitle.lowercase().contains(q) ||
                    record.url.lowercase().contains(q) ||
                    record.host.lowercase().contains(q) ||
                    record.method.lowercase().contains(q) ||
                    record.category.lowercase().contains(q) ||
                    record.requestBody?.lowercase()?.contains(q) == true ||
                    record.responseBody?.lowercase()?.contains(q) == true
                }
            }

            // 黑名单
            if (bl.isNotEmpty()) {
                filtered = filtered.filter { record ->
                    bl.none { kw -> record.host.contains(kw, ignoreCase = true) }
                }
            }

            filtered
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 黑名单管理 ──────────────────────────

    private fun refreshBlacklist() {
        val filter = BaseModel.httpCaptureFilter.value ?: ""
        _blacklist.value = filter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun toggleBlacklist(domain: String) {
        val current = _blacklist.value.toMutableList()
        if (current.contains(domain)) current.remove(domain) else current.add(domain)
        saveBlacklist(current)
    }

    private fun saveBlacklist(list: List<String>) {
        val str = list.distinct().joinToString(",")
        BaseModel.httpCaptureFilter.value = str
        fansirsqi.xposed.sesame.util.DataStore.put(BaseModel.httpCaptureFilter.code, str)
        _blacklist.value = list
    }

    // ── 数据加载 ───────────────────────────

    fun loadData(dateStr: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            isGlobalSearch.value = false

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dates = CaptureStorage.listDates()
            val finalDate = dateStr ?: if (dates.contains(today)) today else dates.firstOrNull() ?: today

            _viewingDate.value = finalDate
            pageOffset = 0
            cachedFullList = CaptureStorage.loadByDate(finalDate)

            _allRecords.value = cachedFullList.take(PAGE_SIZE)
            _hasMore.value = cachedFullList.size > PAGE_SIZE
            pageOffset = PAGE_SIZE

            if (finalDate == today) startWatching() else stopWatching()
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (!_hasMore.value || _isLoading.value) return
        viewModelScope.launch(Dispatchers.Main) {
            val next = cachedFullList.drop(pageOffset).take(PAGE_SIZE)
            if (next.isNotEmpty()) {
                pageOffset += next.size
                _allRecords.value = _allRecords.value + next
            }
            _hasMore.value = pageOffset < cachedFullList.size
        }
    }

    // ── 全局搜索 ───────────────────────────

    fun searchAllDates(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            if (query.isBlank()) {
                // 空查询：加载所有日期的全部记录
                val allDates = CaptureStorage.listDates()
                val all = mutableListOf<CaptureRecord>()
                for (date in allDates) {
                    if (all.size >= 200) break
                    all.addAll(CaptureStorage.loadByDate(date).take(200 - all.size))
                }
                _globalSearchResults.value = all.sortedByDescending { it.timestamp }
            } else {
                val files = CaptureStorage.listAllFiles()
                val results = CaptureSearchEngine.search(query, files)
                _globalSearchResults.value = results
            }
            _isLoading.value = false
        }
    }

    // ── 实时监听 ────────────────────────────

    private var watchJob: Job? = null
    private var raf: RandomAccessFile? = null

    // ── 实时监听 ────────────────────────────

    private var isWatching = false

    private fun startWatching() {
        stopWatching()
        isWatching = true
        val date = _viewingDate.value
        val file = File(CaptureStorage.getDir(), "$date.jsonl")

        viewModelScope.launch(Dispatchers.IO) {
            if (!file.exists()) return@launch
            try {
                raf = RandomAccessFile(file, "r").apply { seek(file.length()) }

                while (isActive && isWatching) {
                    try {
                        val currentLen = file.length()
                        val pointer = raf?.filePointer ?: 0L

                        if (currentLen < pointer) raf?.seek(0)
                        if (currentLen > pointer) {
                            val line = raf?.readLine()
                            if (line != null) {
                                val record = fansirsqi.xposed.sesame.util.JsonUtil.parseObject(
                                    line.trim(), CaptureRecord::class.java
                                )
                                if (record != null) {
                                    withContext(Dispatchers.Main) {
                                        val newList = (listOf(record) + _allRecords.value).take(MAX_MEMORY_RECORDS)
                                        _allRecords.value = newList
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    delay(500L)
                }
            } catch (e: Exception) {
                Log.error(TAG, "实时监听失败: ${e.message}")
            }
        }
    }

    private fun stopWatching() {
        isWatching = false
        try { raf?.close() } catch (_: Exception) {}
        raf = null
        watchJob?.cancel()
        watchJob = null
    }

    // ── 操作 ───────────────────────────────

    fun updateSearchQuery(q: String) { _searchQuery.value = q }

    fun setCategoryFilter(cat: String?) { _categoryFilter.value = cat }

    fun toggleAutoScroll() { _autoScroll.value = !_autoScroll.value }

    fun clearCurrentDate() {
        viewModelScope.launch(Dispatchers.IO) {
            CaptureStorage.clear(_viewingDate.value)
            _allRecords.value = emptyList()
            cachedFullList = emptyList()
            pageOffset = 0
            _hasMore.value = false
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            CaptureStorage.clearAll()
            _allRecords.value = emptyList()
            cachedFullList = emptyList()
            pageOffset = 0
            _hasMore.value = false
        }
    }

    fun getDates(): List<String> = CaptureStorage.listDates()

    fun getAllCategories(): List<String> = CaptureClassifier.getCategoryNames()

    /**
     * 重新加载分类规则（用户编辑 rules.json 后调用）。
     */
    fun reloadClassifier() {
        CaptureClassifier.loadRules()
    }

    /**
     * 生成模拟测试数据并切换到当天视图。
     */
    fun addTestData() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val mockRecords = listOf(
                CaptureRecord(
                    id = UUID.randomUUID().toString(),
                    url = "https://api.example.com/v1/userMission/list?userId=12345",
                    method = "POST", host = "api.example.com", path = "/v1/userMission/list",
                    queryParams = mapOf("userId" to "12345"),
                    requestHeaders = mapOf("Content-Type" to "application/json", "Cookie" to "session=mock1"),
                    requestBody = """{"action":"getMissionList","page":1,"size":20}""",
                    requestBodySize = 44,
                    statusCode = 200, contentType = "application/json",
                    responseHeaders = mapOf("Content-Type" to "application/json"),
                    responseBody = """{"success":true,"data":[{"missionId":"m001","name":"签到任务"},{"missionId":"m002","name":"浇水任务"}]}""",
                    responseBodySize = 102, timestamp = System.currentTimeMillis(), duration = 156,
                    category = CaptureClassifier.classify("https://api.example.com/v1/userMission/list?userId=12345")
                ),
                CaptureRecord(
                    id = UUID.randomUUID().toString(),
                    url = "https://api.example.com/v1/checkin/doDailySign?activityId=act001",
                    method = "POST", host = "api.example.com", path = "/v1/checkin/doDailySign",
                    queryParams = mapOf("activityId" to "act001"),
                    requestHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    requestBody = "userId=12345&timestamp=${System.currentTimeMillis()}",
                    requestBodySize = 45,
                    statusCode = 200, contentType = "application/json",
                    responseHeaders = mapOf("Content-Type" to "application/json"),
                    responseBody = """{"success":true,"data":{"signInCount":7,"reward":{"type":"coupon","value":5}}}""",
                    responseBodySize = 85, timestamp = System.currentTimeMillis() - 2000, duration = 89,
                    category = CaptureClassifier.classify("https://api.example.com/v1/checkin/doDailySign?activityId=act001")
                ),
                CaptureRecord(
                    id = UUID.randomUUID().toString(),
                    url = "https://api.example.com/v1/reward/receive?rewardId=rw001",
                    method = "GET", host = "api.example.com", path = "/v1/reward/receive",
                    queryParams = mapOf("rewardId" to "rw001"),
                    requestHeaders = mapOf("User-Agent" to "Sesame-TK/0.3.0"),
                    statusCode = 200, contentType = "application/json",
                    responseHeaders = mapOf("Content-Type" to "application/json"),
                    responseBody = """{"success":true,"data":{"rewardType":"greenEnergy","amount":30}}""",
                    responseBodySize = 70, timestamp = System.currentTimeMillis() - 4000, duration = 45,
                    category = CaptureClassifier.classify("https://api.example.com/v1/reward/receive?rewardId=rw001")
                ),
                CaptureRecord(
                    id = UUID.randomUUID().toString(),
                    url = "https://api.example.com/v1/antforest/queryFriendEnergy",
                    method = "POST", host = "api.example.com", path = "/v1/antforest/queryFriendEnergy",
                    requestHeaders = mapOf("Content-Type" to "application/json"),
                    requestBody = """{"friendIds":["userA","userB","userC"]}""",
                    requestBodySize = 40,
                    statusCode = 500, contentType = "application/json",
                    responseHeaders = mapOf("Content-Type" to "application/json"),
                    responseBody = """{"error":"internal server error","code":500}""",
                    responseBodySize = 48, timestamp = System.currentTimeMillis() - 6000, duration = 1200,
                    category = CaptureClassifier.classify("https://api.example.com/v1/antforest/queryFriendEnergy")
                ),
            )
            for (rec in mockRecords) {
                CaptureStorage.save(rec)
            }
            withContext(Dispatchers.Main) { loadData(today) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWatching()
    }
}
