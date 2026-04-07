package fansirsqi.xposed.sesame.ui.extra.viewmodel

import androidx.lifecycle.ViewModel
import fansirsqi.xposed.sesame.ui.extra.RequestItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RPC 调试 ViewModel
 * 负责：
 * - 请求列表
 * - 输入状态（title/method/data）
 * - 结果文本/放大状态
 */
class RpcDebugViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<RequestItem>>(emptyList())
    val items: StateFlow<List<RequestItem>> = _items.asStateFlow()

    // 输入与结果状态
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _method = MutableStateFlow("")
    val method: StateFlow<String> = _method.asStateFlow()

    private val _data = MutableStateFlow("")
    val data: StateFlow<String> = _data.asStateFlow()

    private val _result = MutableStateFlow("")
    val result: StateFlow<String> = _result.asStateFlow()

    private val _zoomed = MutableStateFlow(false)
    val zoomed: StateFlow<Boolean> = _zoomed.asStateFlow()

    /**
     * 生成下一个唯一 id
     * 统一的 id 生成策略，确保 id 唯一性
     */
    private fun generateNextId(): Int {
        return (_items.value.maxOfOrNull { it.id } ?: 0) + 1
    }

    /**
     * 验证 id 唯一性（用于调试）
     * 检测并记录重复的 id
     */
    private fun validateUniqueIds() {
        val ids = _items.value.map { it.id }
        val duplicates = ids.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            android.util.Log.e("RpcDebugViewModel", "发现重复的 id: ${duplicates.keys}")
        }
    }

    fun load(initial: List<RequestItem>) {
        // 使用安全的 id 分配策略，避免 id 冲突
        var maxId = initial.maxOfOrNull { it.id } ?: 0
        val assigned = initial.map { item ->
            if (item.id == 0) {
                maxId++
                item.copy(id = maxId)
            } else {
                item
            }
        }
        _items.value = assigned
        validateUniqueIds()
    }

    fun toggleExpand(id: Int) {
        _items.value = _items.value.map { if (it.id == id) it.copy(expanded = !it.expanded) else it }
    }

    fun add(item: RequestItem) {
        // 自动为 id=0 的项分配唯一 id
        val newItem = if (item.id == 0) {
            item.copy(id = generateNextId())
        } else {
            item
        }
        _items.value = _items.value + newItem
        validateUniqueIds()
    }

    fun update(item: RequestItem) {
        _items.value = _items.value.map { if (it.id == item.id) item else it }
    }

    fun delete(id: Int) { _items.value = _items.value.filterNot { it.id == id } }

    private val _editingItem = MutableStateFlow<RequestItem?>(null)
    val editingItem: StateFlow<RequestItem?> = _editingItem.asStateFlow()

    fun showEditDialog(item: RequestItem) {
        _editingItem.value = item
    }

    fun dismissEditDialog() {
        _editingItem.value = null
    }

    fun updateEditingItem(title: String, description: String, method: String, data: String) {
        _editingItem.value?.let { currentItem ->
            _items.value = _items.value.map {
                if (it.id == currentItem.id) {
                    it.copy(title = title, description = description, method = method, data = data)
                } else it
            }
            _editingItem.value = null
        }
    }

    fun duplicate(id: Int) {
        val src = _items.value.firstOrNull { it.id == id } ?: return
        val copy = src.copy(id = generateNextId(), title = src.title + "-副本")
        _items.value = _items.value + copy
        validateUniqueIds()
    }

    fun getItemById(id: Int): RequestItem? = _items.value.firstOrNull { it.id == id }
    fun getItems(): List<RequestItem> = _items.value
    fun updateItem(id: Int, block: (RequestItem) -> RequestItem) {
        _items.value = _items.value.map { if (it.id == id) block(it) else it }
    }

    // 结果与输入更新
    fun updateResult(text: String) { _result.value = text }
    fun updateTitle(text: String) { _title.value = text }
    fun updateMethod(text: String) { _method.value = text }
    fun updateData(text: String) { _data.value = text }
    fun toggleZoom() { _zoomed.value = !_zoomed.value }

    /**
     * 从 JSON 文本批量导入请求
     * 支持两种格式：
     * 1. 现有格式：{"id":0,"title":"","method":"","data":""}
     * 2. 新格式：{"Name":"","Description":"","methodName":"","requestData":[]}
     *
     * 支持批量导入：粘贴多个 JSON 对象（用逗号分隔或换行分隔）
     *
     * @param jsonText JSON 文本
     * @return Pair<成功数量, 失败数量>
     */
    fun importFromJson(jsonText: String): Pair<Int, Int> {
        var successCount = 0
        var failCount = 0

        try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()

            // 尝试解析为 JSON 数组
            val jsonObjects = mutableListOf<String>()

            // 预处理：尝试将文本分割成多个 JSON 对象
            val trimmed = jsonText.trim()

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // 如果是 JSON 数组格式
                try {
                    val array = mapper.readTree(trimmed)
                    if (array.isArray) {
                        array.forEach { jsonObjects.add(it.toString()) }
                    }
                } catch (e: Exception) {
                    // 不是有效的 JSON 数组，尝试其他方式
                }
            }

            if (jsonObjects.isEmpty()) {
                // 尝试按 "}{" 分割（多个 JSON 对象连在一起）
                val parts = trimmed.split("}{")
                if (parts.size > 1) {
                    parts.forEachIndexed { index, part ->
                        val fixed = when {
                            index == 0 -> "$part}"
                            index == parts.size - 1 -> "{$part"
                            else -> "{$part}"
                        }
                        jsonObjects.add(fixed)
                    }
                } else {
                    // 使用状态机解析多个 JSON 对象
                    // 支持：多个换行、逗号分隔、任意空白字符分隔
                    val extracted = extractJsonObjects(trimmed)
                    if (extracted.size > 1) {
                        // 找到多个 JSON 对象
                        jsonObjects.addAll(extracted)
                    } else {
                        // 单个 JSON 对象
                        jsonObjects.add(trimmed)
                    }
                }
            }

            // 解析每个 JSON 对象
            jsonObjects.forEach { jsonStr ->
                try {
                    val jsonNode = mapper.readTree(jsonStr)

                    // 判断是哪种格式
                    val item = if (jsonNode.has("Name") || jsonNode.has("methodName")) {
                        // 新格式
                        val importFormat = mapper.treeToValue(jsonNode, fansirsqi.xposed.sesame.ui.extra.ImportRequestFormat::class.java)
                        importFormat.toRequestItem()
                    } else if (jsonNode.has("title") && jsonNode.has("method")) {
                        // 现有格式
                        mapper.treeToValue(jsonNode, RequestItem::class.java)
                    } else {
                        null
                    }

                    if (item != null) {
                        add(item)
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RpcDebugViewModel", "解析 JSON 失败: ${e.message}")
                    failCount++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RpcDebugViewModel", "导入失败: ${e.message}")
            failCount++
        }

        return Pair(successCount, failCount)
    }

    /**
     * 使用状态机从文本中提取多个 JSON 对象
     * 支持：多个换行、逗号分隔、任意空白字符分隔
     *
     * @param text 包含一个或多个 JSON 对象的文本
     * @return 提取出的 JSON 对象列表
     */
    private fun extractJsonObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0  // 大括号嵌套深度
        var inString = false  // 是否在字符串内
        var escapeNext = false  // 下一个字符是否被转义
        var startIndex = -1  // 当前 JSON 对象的起始位置

        for (i in text.indices) {
            val char = text[i]

            when {
                escapeNext -> {
                    // 跳过被转义的字符
                    escapeNext = false
                }
                char == '\\' && inString -> {
                    // 转义字符
                    escapeNext = true
                }
                char == '"' && !escapeNext -> {
                    // 字符串的开始或结束
                    inString = !inString
                }
                char == '{' && !inString -> {
                    // 进入一个新的大括号
                    if (depth == 0) {
                        startIndex = i  // 记录 JSON 对象的起始位置
                    }
                    depth++
                }
                char == '}' && !inString -> {
                    // 退出一个大括号
                    depth--
                    if (depth == 0 && startIndex != -1) {
                        // 一个完整的 JSON 对象结束
                        val jsonObject = text.substring(startIndex, i + 1)
                        result.add(jsonObject)
                        startIndex = -1
                    }
                }
            }
        }

        return result
    }
}