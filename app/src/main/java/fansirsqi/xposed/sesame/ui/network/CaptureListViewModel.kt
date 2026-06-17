package fansirsqi.xposed.sesame.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.hook.network.CaptureClassifier
import fansirsqi.xposed.sesame.hook.network.CaptureSearchEngine
import fansirsqi.xposed.sesame.hook.network.CaptureStorage
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.JsonUtil
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

    /** 状态筛选 (null: 全部, 1: 成功, 2: 错误) */
    private val _statusFilter = MutableStateFlow<Int?>(null)
    val statusFilter: StateFlow<Int?> = _statusFilter

    /** 是否多选模式 */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    /** 已选择的 ID */
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds

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
        combine(_allRecords, _globalSearchResults, isGlobalSearch, _searchQuery, _categoryFilter, _statusFilter, _blacklist) { args ->
            val records = args[0] as List<CaptureRecord>
            val globals = args[1] as List<CaptureRecord>
            val isGlobal = args[2] as Boolean
            val query = args[3] as String
            val cat = args[4] as String?
            val stat = args[5] as Int?
            val bl = args[6] as List<String>
            
            var filtered = if (isGlobal) globals else {
                if (query.isNotBlank()) cachedFullList else records
            }

            // 状态码筛选
            if (stat != null) {
                filtered = if (stat == 1) {
                    filtered.filter { it.statusCode in 200..299 }
                } else {
                    filtered.filter { it.statusCode < 200 || it.statusCode >= 300 }
                }
            }

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
                    record.statusCode.toString().contains(q) ||
                    record.requestBody?.lowercase()?.contains(q) == true ||
                    record.responseBody?.lowercase()?.contains(q) == true ||
                    record.queryParams.any { (k, v) -> k.lowercase().contains(q) || v.lowercase().contains(q) } ||
                    record.requestHeaders.any { (k, v) -> k.lowercase().contains(q) || v.lowercase().contains(q) } ||
                    record.responseHeaders.any { (k, v) -> k.lowercase().contains(q) || v.lowercase().contains(q) }
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

    data class Stats(val total: Int = 0, val success: Int = 0, val error: Int = 0)

    // ── 全量统计数据 ───────────────────────
    val stats: StateFlow<Stats> =
        combine(_allRecords, _globalSearchResults, isGlobalSearch, _categoryFilter, _blacklist) { _, globals, isGlobal, cat, bl ->
            val sourceList = if (isGlobal) globals else cachedFullList
            
            var filtered = sourceList
            if (cat != null) {
                filtered = filtered.filter { it.category == cat }
            }
            if (bl.isNotEmpty()) {
                filtered = filtered.filter { record ->
                    bl.none { kw -> record.host.contains(kw, ignoreCase = true) }
                }
            }
            
            val total = filtered.size
            val success = filtered.count { it.statusCode in 200..299 }
            val error = filtered.count { it.statusCode >= 400 || it.statusCode == 0 }
            
            Stats(total, success, error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats())

    // ── 黑名单管理 ──────────────────────────

    private fun refreshBlacklist() {
        val filter = BaseModel.httpCaptureFilter.value ?: ""
        _blacklist.value = filter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun toggleBlacklist(domain: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val current = _blacklist.value.toMutableList()
            if (current.contains(domain)) current.remove(domain) else current.add(domain)
            saveBlacklist(current)
        }
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

    /** 处理实时广播回来的记录 */
    fun addRecordFromJson(json: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val record = fansirsqi.xposed.sesame.util.JsonUtil.parseObject(json, CaptureRecord::class.java)
                if (record != null) {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    // 仅处理当天的实时数据
                    if (_viewingDate.value == today) {
                        withContext(Dispatchers.Main) {
                            val cachedMutable = cachedFullList.toMutableList()
                            val cachedIdx = cachedMutable.indexOfFirst { it.id == record.id }
                            if (cachedIdx != -1) {
                                cachedMutable[cachedIdx] = record
                            } else {
                                cachedMutable.add(0, record)
                            }
                            cachedFullList = cachedMutable

                            val current = _allRecords.value.toMutableList()
                            val existingIdx = current.indexOfFirst { it.id == record.id }
                            
                            if (existingIdx != -1) {
                                // 💡 优化：如果 ID 已存在（如：从 PENDING 转为完成状态），则替换该条目
                                current[existingIdx] = record
                                _allRecords.value = current
                            } else {
                                // 💡 新增：新请求发起，插到最前面
                                val newList = (listOf(record) + current).take(MAX_MEMORY_RECORDS)
                                _allRecords.value = newList
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.capture(TAG, "解析实时广播数据失败: ${e.message}")
            }
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
    // 💡 已移除旧版文件轮询逻辑，现已全面切换至 BroadcastReceiver 实时广播分发中心。
    // 这极大地降低了磁盘 I/O 消耗，并解决了在高频抓包下的数据同步延迟问题。

    // ── 操作 ───────────────────────────────

    fun updateSearchQuery(q: String) { _searchQuery.value = q }

    fun setCategoryFilter(cat: String?) { _categoryFilter.value = cat }

    fun setStatusFilter(stat: Int?) { _statusFilter.value = stat }

    fun toggleSelection(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun deleteSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = _selectedIds.value
            cachedFullList = cachedFullList.filter { it.id !in ids }
            _allRecords.value = _allRecords.value.filter { it.id !in ids }
            // 💡 物理删除由底层存储管理或下次加载时生效，这里仅从内存清除
            clearSelection()
        }
    }

    fun exportSelected(): String {
        val ids = _selectedIds.value
        val records = if (isGlobalSearch.value) _globalSearchResults.value else _allRecords.value
        val selected = records.filter { it.id in ids }
        return JsonUtil.formatJson(selected)
    }

    fun toggleAutoScroll() { _autoScroll.value = !_autoScroll.value }

    fun clearCurrentDate() {
        viewModelScope.launch(Dispatchers.IO) {
            CaptureStorage.clear(_viewingDate.value)
            
            // 同步清空内存状态
            withContext(Dispatchers.Main) {
                _allRecords.value = emptyList()
                _globalSearchResults.value = emptyList()
                cachedFullList = emptyList()
                pageOffset = 0
                _hasMore.value = false
                
                // 如果是全局模式，清除后尝试重新扫描（以防还有其他日期的记录）
                if (isGlobalSearch.value) {
                    searchAllDates(searchQuery.value)
                }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            CaptureStorage.clearAll()
            withContext(Dispatchers.Main) {
                _allRecords.value = emptyList()
                _globalSearchResults.value = emptyList()
                cachedFullList = emptyList()
                pageOffset = 0
                _hasMore.value = false
            }
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
    }
}
