package fansirsqi.xposed.sesame.hook.network

import android.content.Context
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 抓包文件管理器，负责网络捕获数据的持久化存储与管理。
 */
object CaptureFileManager {
    private const val TAG = "CaptureFileManager"
    private val BASE_DIR = File(Files.MAIN_DIR, "capture")
    init {
        Files.ensureDir(BASE_DIR)
    }

    fun getCaptureDir(): File = BASE_DIR
    
    fun getTodayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /**
     * 保存抓包数据 (Logback 架构)
     */
    fun save(packet: CapturePacket, reqBody: ByteArray?, resBody: ByteArray?) {
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(packet.startTime))
            val timeStr = SimpleDateFormat("HHmmss_SSS", Locale.getDefault()).format(Date(packet.startTime))
            
            // 按日期分子目录存储 Body，方便管理和清理
            val dailyDir = File(BASE_DIR, dateStr)
            if (!dailyDir.exists()) dailyDir.mkdirs()

            val shortId = if (packet.id.length >= 8) packet.id.substring(0, 8) else packet.id
            val baseFileName = "${timeStr}_${shortId}"

            // 保存到日期目录
            saveBody(dailyDir, "${baseFileName}_req.bin", reqBody)?.let { packet.requestBodyFile = it }
            saveBody(dailyDir, "${baseFileName}_res.bin", resBody)?.let { 
                packet.responseBodyFile = it
                if (packet.contentType?.contains("image", ignoreCase = true) == true) {
                    packet.isImage = true
                }
            }

            // 元数据通过 Logback 存储 (http.log 现在是纯 JSON 行)
            val metadataJson = JsonUtil.formatJson(packet, false)
            Log.http(metadataJson)

        } catch (e: Exception) {
            Log.capture(TAG, "💾 保存失败: ${e.message}")
        }
    }

    private fun saveBody(dir: File, name: String, data: ByteArray?): String? {
        if (data == null || data.isEmpty()) return null
        return try {
            val file = File(dir, name)
            file.writeBytes(data)
            file.absolutePath
        } catch (e: Exception) {
            Log.capture(TAG, "Body 保存失败: ${e.message}")
            null
        }
    }

    /**
     * 获取历史抓包日期的文件夹列表
     */
    fun getDailyFolders(): List<String> {
        val folders = BASE_DIR.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        return folders?.map { it.name }?.sortedDescending() ?: emptyList()
    }

    /**
     * 获取指定日期的所有日志文件（包括当前和备份）
     */
    private fun getLogFilesForDate(dateStr: String): List<File> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val logFiles = mutableListOf<File>()
        
        if (dateStr == today) {
            logFiles.add(File(Files.LOG_DIR, "http.log"))
        }
        
        val bakDir = File(Files.LOG_DIR, "bak")
        bakDir.listFiles { f -> f.name.contains("http-$dateStr") }?.let {
            logFiles.addAll(it)
        }
        return logFiles
    }

    /**
     * 解析单行日志为数据包对象
     */
    fun parseLine(line: String): CapturePacket? {
        if (line.isBlank()) return null
        return try {
            val jsonStart = line.indexOf("{")
            val jsonEnd = line.lastIndexOf("}")
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                // 提取 JSON 并去除前后空白符
                val json = line.substring(jsonStart, jsonEnd + 1).trim()
                JsonUtil.parseObject(json, CapturePacket::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.error(TAG, "解析日志行失败: ${e.message} | 行内容: $line")
            null
        }
    }

    /**
     * 获取指定日期的所有原始日志行（按时间倒序：最新的在前）
     */
    fun getRawLinesForDate(dateStr: String): List<String> {
        val logFiles = getLogFilesForDate(dateStr)
        val allLines = mutableListOf<String>()
        
        // Logback 的 http.log 是最新的，bak 文件是旧的
        // 我们按顺序读取，每读完一个文件就反转其内部行（如果需要最新的在前）
        // 或者更简单：全部读取后按文件顺序排列，由于 http.log 总是最新的，
        // 我们应该先处理 http.log，然后处理 bak 文件（按索引从小到大）
        
        for (file in logFiles) {
            if (!file.exists()) continue
            try {
                val lines = file.readLines()
                // 文件内部是正序的（最新的在最后），所以反转
                allLines.addAll(lines.reversed())
            } catch (e: Exception) {
                Log.error(TAG, "读取日志文件失败: ${file.name}, ${e.message}")
            }
        }
        return allLines
    }

    /**
     * 从 Logback 日志中提取指定日期的抓包列表 (旧方法，保留兼容性或用于小数据量)
     */
    fun getPacketsForDate(dateStr: String): List<CapturePacket> {
        return getRawLinesForDate(dateStr).mapNotNull { parseLine(it) }
    }

    /**
     * 清空指定日期的记录
     */
    fun clearForDate(dateStr: String): Boolean {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            
            // 1. 删除备份日志
            val bakDir = File(Files.LOG_DIR, "bak")
            bakDir.listFiles { f -> f.name.contains("http-$dateStr") }?.forEach { it.delete() }
            
            // 2. 如果是今天，清空活跃日志
            if (dateStr == today) {
                val httpLog = File(Files.LOG_DIR, "http.log")
                if (httpLog.exists()) httpLog.writeText("")
            }
            
            // 3. 物理删除该日期的所有 Body 文件夹
            val dailyDir = File(BASE_DIR, dateStr)
            if (dailyDir.exists()) {
                fansirsqi.xposed.sesame.util.Files.delFile(dailyDir)
            }
            
            true
        } catch (e: Exception) {
            Log.capture(TAG, "清空日期 $dateStr 记录失败: ${e.message}")
            false
        }
    }

    /**
     * 清空所有记录
     */
    fun clearAll(): Boolean {
        return try {
            // 1. 清空二进制存储
            Files.delFile(BASE_DIR)
            Files.ensureDir(BASE_DIR)
            
            // 2. 清空抓包日志
            val httpLog = File(Files.LOG_DIR, "http.log")
            if (httpLog.exists()) httpLog.writeText("")
            
            // 3. 清空备份日志
            val bakDir = File(Files.LOG_DIR, "bak")
            bakDir.listFiles { f -> f.name.startsWith("http-") }?.forEach { it.delete() }
            
            true
        } catch (e: Exception) {
            Log.capture(TAG, "清空所有记录失败: ${e.message}")
            false
        }
    }
}
