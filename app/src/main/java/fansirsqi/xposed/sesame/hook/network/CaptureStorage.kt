package fansirsqi.xposed.sesame.hook.network

import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * JSON Lines 格式存储。
 *
 * 目录结构：
 *   captures/
 *     2026-05-05.jsonl        ← 当天活跃文件
 *     2026-05-05.1.jsonl      ← 自动轮转（超过 50MB 后编号）
 *     2026-05-04.jsonl
 *     ...
 *
 * 每行一条完整的 [CaptureRecord] JSON (compact)。
 */
object CaptureStorage {

    private const val TAG = "CaptureStorage"
    private const val DIR_NAME = "captures"

    /** 单个文件最大大小 (50MB) */
    private const val MAX_FILE_SIZE = 50L * 1024 * 1024

    /** body 内联最大字节数 (10MB)，与捕获上限保持一致 */
    const val MAX_BODY_INLINE = 10 * 1024 * 1024

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ── 目录 ─────────────────────────────────

    @JvmStatic
    fun getDir(): File {
        val dir = File(Files.MAIN_DIR, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @JvmStatic
    fun getBodiesDir(): File {
        val dir = File(getDir(), "bodies")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ── 写入 ─────────────────────────────────

    /** 写入锁，防止多线程写文件行交错 */
    private val writeLock = Any()

    /**
     * 保存一条完整的抓包记录（线程安全）。
     * 序列化为单行 JSON，追加写入当天的 .jsonl 文件。
     */
    @JvmStatic
    fun save(record: CaptureRecord) {
        synchronized(writeLock) {
            try {
                // 💡 攻克 OOM：大文件外置化
                val processedRecord = handleLargeBody(record)
                
                val dateStr = dateFormat.format(Date(processedRecord.timestamp))
                val json = JsonUtil.formatJson(processedRecord, false)
                val file = getActiveFile(dateStr)
                file.parentFile?.mkdirs()
                val writer = FileWriter(file, true)
                try {
                    writer.write(json)
                    writer.write("\n")
                    writer.flush()
                } finally {
                    writer.close()
                }
            } catch (e: Exception) {
                Log.error(TAG, "保存记录失败: ${e.message}")
            }
        }
    }

    private fun handleLargeBody(record: CaptureRecord): CaptureRecord {
        var reqBody = record.requestBody
        var reqBase64 = record.requestBodyBase64
        var resBody = record.responseBody
        var resBase64 = record.responseBodyBase64
        var changed = false

        // 阈值设为 512KB，超过即外置
        val threshold = 512 * 1024

        if (reqBody != null && reqBody.length > threshold) {
            saveBodyToFile("${record.id}_req.txt", reqBody)
            reqBody = "[Large Body: ${reqBody.length} bytes, Click to load]"
            changed = true
        }
        if (reqBase64 != null && reqBase64.length > threshold) {
            saveBodyToFile("${record.id}_req_b64.txt", reqBase64)
            reqBase64 = "[Large Base64: ${reqBase64.length} bytes]"
            changed = true
        }
        if (resBody != null && resBody.length > threshold) {
            saveBodyToFile("${record.id}_res.txt", resBody)
            resBody = "[Large Body: ${resBody.length} bytes, Click to load]"
            changed = true
        }
        if (resBase64 != null && resBase64.length > threshold) {
            saveBodyToFile("${record.id}_res_b64.txt", resBase64)
            resBase64 = "[Large Base64: ${resBase64.length} bytes]"
            changed = true
        }

        return if (changed) record.copy(
            requestBody = reqBody,
            requestBodyBase64 = reqBase64,
            responseBody = resBody,
            responseBodyBase64 = resBase64
        ) else record
    }

    private fun saveBodyToFile(filename: String, content: String) {
        try {
            val file = File(getBodiesDir(), filename)
            file.writeText(content)
        } catch (e: Exception) {
            Log.error(TAG, "外置 Body 保存失败: ${e.message}")
        }
    }

    /**
     * 获取当天的活跃写入文件。
     * 如果当前文件超过 50MB，自动轮转到下一个编号。
     */
    private fun getActiveFile(dateStr: String): File {
        val dir = getDir()
        var index = 0
        var file: File
        do {
            val name = if (index == 0) "$dateStr.jsonl" else "$dateStr.$index.jsonl"
            file = File(dir, name)
            if (!file.exists()) break
            if (file.length() < MAX_FILE_SIZE) break
            index++
        } while (true)
        return file
    }

    // ── 读取 ─────────────────────────────────

    /**
     * 按日期加载所有记录（按时间倒序）。
     */
    @JvmStatic
    fun loadByDate(dateStr: String): List<CaptureRecord> {
        val records = mutableListOf<CaptureRecord>()
        val dir = getDir()
        val files = dir.listFiles { f ->
            f.name.startsWith("$dateStr") && (f.name == "$dateStr.jsonl" || f.name.matches(Regex("$dateStr\\.\\d+\\.jsonl")))
        } ?: return records

        for (file in files.sortedBy { it.name }) {
            try {
                file.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        val record = JsonUtil.parseObject(trimmed, CaptureRecord::class.java)
                        if (record != null) records.add(record)
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "读取文件失败: ${file.name}, ${e.message}")
            }
        }
        return records.sortedByDescending { it.timestamp }
    }

    /**
     * 根据 ID 和日期查找单条记录。
     */
    @JvmStatic
    fun loadById(id: String, dateStr: String): CaptureRecord? {
        val dir = getDir()
        val files = dir.listFiles { f ->
            f.name.startsWith("$dateStr") && f.name.endsWith(".jsonl")
        } ?: return null

        for (file in files.sortedBy { it.name }) {
            try {
                // 使用 useLines 避免 readLines() 加载整个文件到内存
                val found = file.useLines { lines ->
                    lines.find { line -> line.contains("\"id\":\"$id\"") }
                }
                if (found != null) {
                    val record = JsonUtil.parseObject(found.trim(), CaptureRecord::class.java) ?: return null
                    return restoreLargeBody(record)
                }
            } catch (e: Exception) {
                Log.error(TAG, "查找记录失败: ${e.message}")
            }
        }
        return null
    }

    private fun restoreLargeBody(record: CaptureRecord): CaptureRecord {
        var reqBody = record.requestBody
        var reqBase64 = record.requestBodyBase64
        var resBody = record.responseBody
        var resBase64 = record.responseBodyBase64
        var changed = false

        if (reqBody != null && reqBody.startsWith("[Large Body:")) {
            readBodyFromFile("${record.id}_req.txt")?.let { reqBody = it; changed = true }
        }
        if (reqBase64 != null && reqBase64.startsWith("[Large Base64:")) {
            readBodyFromFile("${record.id}_req_b64.txt")?.let { reqBase64 = it; changed = true }
        }
        if (resBody != null && resBody.startsWith("[Large Body:")) {
            readBodyFromFile("${record.id}_res.txt")?.let { resBody = it; changed = true }
        }
        if (resBase64 != null && resBase64.startsWith("[Large Base64:")) {
            readBodyFromFile("${record.id}_res_b64.txt")?.let { resBase64 = it; changed = true }
        }

        return if (changed) record.copy(
            requestBody = reqBody,
            requestBodyBase64 = reqBase64,
            responseBody = resBody,
            responseBodyBase64 = resBase64
        ) else record
    }

    private fun readBodyFromFile(filename: String): String? {
        return try {
            val file = File(getBodiesDir(), filename)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Log.error(TAG, "外置 Body 读取失败: ${e.message}")
            null
        }
    }

    // ── 日期列表 ─────────────────────────────

    /**
     * 列出所有有数据的日期（倒序）。
     */
    @JvmStatic
    fun listDates(): List<String> {
        val dir = getDir()
        val dateSet = mutableSetOf<String>()
        dir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.endsWith(".jsonl")) {
                val datePart = name.substringBefore(".")
                if (datePart.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    dateSet.add(datePart)
                }
            }
        }
        return dateSet.sortedDescending()
    }

    /**
     * 获取所有 jsonl 文件列表（用于搜索）。
     */
    @JvmStatic
    fun listAllFiles(): List<File> {
        val dir = getDir()
        return (dir.listFiles()?.filter { it.name.endsWith(".jsonl") } ?: emptyList())
            .sortedByDescending { it.name }
    }

    // ── 清理 ─────────────────────────────────

    /**
     * 清除指定日期的所有数据。
     */
    @JvmStatic
    fun clear(dateStr: String) {
        val dir = getDir()
        dir.listFiles { f ->
            f.name.startsWith("$dateStr") && f.name.endsWith(".jsonl")
        }?.forEach { it.delete() }
    }

    /**
     * 清除所有数据。
     */
    @JvmStatic
    fun clearAll() {
        val dir = getDir()
        dir.listFiles()?.filter { it.name.endsWith(".jsonl") }?.forEach { it.delete() }
    }
}
