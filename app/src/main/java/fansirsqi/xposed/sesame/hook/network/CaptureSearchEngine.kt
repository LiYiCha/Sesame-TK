package fansirsqi.xposed.sesame.hook.network

import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import java.io.File

/**
 * 全文搜索引擎。
 *
 * 扫描 .jsonl 文件，逐行解码后匹配所有字段。
 * 结果上限 200 条，按时间倒序排列。
 */
object CaptureSearchEngine {

    private const val TAG = "CaptureSearchEngine"

    /** 搜索命中上限 */
    private const val MAX_RESULTS = 200

    /**
     * 在指定文件中搜索。
     *
     * @param query  搜索关键词
     * @param files  要搜索的 .jsonl 文件列表
     * @return 匹配的 [CaptureRecord] 列表（时间倒序）
     */
    fun search(query: String, files: List<File>): List<CaptureRecord> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<CaptureRecord>()
        val lowerQuery = query.lowercase()

        for (file in files) {
            if (results.size >= MAX_RESULTS) break
            if (!file.exists()) continue

            try {
                file.forEachLine { line ->
                    if (results.size >= MAX_RESULTS) return@forEachLine
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEachLine

                    val record = JsonUtil.parseObject(trimmed, CaptureRecord::class.java) ?: return@forEachLine

                    if (matches(record, lowerQuery)) {
                        results.add(record)
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "搜索文件失败: ${file.name}, ${e.message}")
            }
        }

        return results.sortedByDescending { it.timestamp }
    }

    /**
     * 检查一条记录是否匹配搜索关键词。
     * 匹配范围：url / host / path / queryParams / requestHeaders / requestBody
     *          / responseHeaders / responseBody / category / method
     */
    private fun matches(record: CaptureRecord, lowerQuery: String): Boolean {
        // 直接字段
        if (record.url.lowercase().contains(lowerQuery)) return true
        if (record.host.lowercase().contains(lowerQuery)) return true
        if (record.path.lowercase().contains(lowerQuery)) return true
        if (record.method.lowercase().contains(lowerQuery)) return true
        if (record.category.lowercase().contains(lowerQuery)) return true

        // query params
        if (record.queryParams.any { (k, v) ->
                k.lowercase().contains(lowerQuery) || v.lowercase().contains(lowerQuery)
            }) return true

        // request headers
        if (record.requestHeaders.any { (k, v) ->
                k.lowercase().contains(lowerQuery) || v.lowercase().contains(lowerQuery)
            }) return true

        // request body
        record.requestBody?.let {
            if (it.lowercase().contains(lowerQuery)) return true
        }

        // response headers
        if (record.responseHeaders.any { entry ->
                entry.key.lowercase().contains(lowerQuery) || entry.value.lowercase().contains(lowerQuery)
            }) return true

        // response body
        record.responseBody?.let {
            if (it.lowercase().contains(lowerQuery)) return true
        }

        return false
    }
}
