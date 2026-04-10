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
    private val BODY_DIR = File(BASE_DIR, "bodies")

    init {
        Files.ensureDir(BASE_DIR)
        Files.ensureDir(BODY_DIR)
    }

    /**
     * 保存抓包数据 (Logback 架构)
     */
    fun save(packet: CapturePacket, reqBody: ByteArray?, resBody: ByteArray?) {
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(packet.startTime))
            val timeStr = SimpleDateFormat("HHmmss_SSS", Locale.getDefault()).format(Date(packet.startTime))
            
            val shortId = if (packet.id.length >= 8) packet.id.substring(0, 8) else packet.id
            val baseFileName = "${timeStr}_${shortId}"

            // 1. 保存请求体/响应体 (Binary)
            saveBody(BODY_DIR, "${baseFileName}_req.bin", reqBody)?.let { packet.requestBodyFile = it }
            saveBody(BODY_DIR, "${baseFileName}_res.bin", resBody)?.let { 
                packet.responseBodyFile = it
                if (packet.contentType?.contains("image", ignoreCase = true) == true) {
                    packet.isImage = true
                }
            }

            // 2. 元数据通过 Logback 存储，解决跨进程写入权限难题
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
            Log.capture(TAG, "❌ 写入数据体失败: ${e.message} 路径: ${name}")
            null
        }
    }

    /**
     * 获取所有日期（扫描 Logback 备份文件）
     */
    fun getDailyFolders(): List<String> {
        val folders = mutableSetOf<String>()
        // 1. 今日
        folders.add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
        
        // 2. 历史回溯 (扫描 bak 目录下的 http 备份文件)
        val bakDir = File(Files.LOG_DIR, "bak")
        if (bakDir.exists()) {
            bakDir.listFiles { f -> f.name.startsWith("http-") }?.forEach { f ->
                // 文件名格式如: http-2024-04-10.0.log
                val match = Regex("http-(\\d{4}-\\d{2}-\\d{2})").find(f.name)
                match?.groupValues?.get(1)?.let { folders.add(it) }
            }
        }
        return folders.toList().sortedDescending()
    }

    /**
     * 从 Logback 日志中提取指定日期的抓包列表
     */
    fun getPacketsForDate(dateStr: String): List<CapturePacket> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val logFiles = mutableListOf<File>()
        
        // 如果是今天，首先读取活跃日志文件
        if (dateStr == today) {
            logFiles.add(File(Files.LOG_DIR, "http.log"))
        }
        
        // 读取该日期的所有备份记录
        val bakDir = File(Files.LOG_DIR, "bak")
        bakDir.listFiles { f -> f.name.contains("http-$dateStr") }?.let {
            logFiles.addAll(it)
        }

        val packets = mutableListOf<CapturePacket>()
        
        for (file in logFiles) {
            if (!file.exists()) continue
            try {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val jsonStart = line.indexOf("{")
                        if (jsonStart != -1) {
                            val json = line.substring(jsonStart)
                            JsonUtil.parseObject(json, CapturePacket::class.java)?.let {
                                packets.add(it)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "解析日志文件失败: ${file.name}, ${e.message}")
            }
        }
        
        // 仅在返回前做一次按时间排序，确保 UI 展示有序
        return packets.sortedByDescending { it.startTime }
    }

    /**
     * 清空所有记录
     */
    fun clearAll(): Boolean {
        return try {
            // 1. 清空二进制存储
            Files.delFile(BASE_DIR)
            Files.ensureDir(BASE_DIR)
            Files.ensureDir(BODY_DIR)
            
            // 2. 清空抓包日志
            val httpLog = File(Files.LOG_DIR, "http.log")
            if (httpLog.exists()) httpLog.writeText("")
            
            // 3. 清空备份日志
            val bakDir = File(Files.LOG_DIR, "bak")
            bakDir.listFiles { f -> f.name.startsWith("http-") }?.forEach { it.delete() }
            
            true
        } catch (e: Exception) {
            Log.capture(TAG, "清空记录失败: ${e.message}")
            false
        }
    }
}
