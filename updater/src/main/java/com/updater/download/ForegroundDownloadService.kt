package com.updater.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.updater.db.DownloadDatabaseHelper
import com.updater.db.DownloadTask
import com.updater.utils.ApkInstaller
import com.updater.utils.UpdaterLog
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ForegroundDownloadService : Service() {

    companion object {
        const val ACTION_START = "com.updater.download.action.START"
        const val ACTION_PAUSE = "com.updater.download.action.PAUSE"
        
        const val BROADCAST_ACTION = "com.updater.download.broadcast.PROGRESS"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_DOWNLOADED = "extra_downloaded"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_ERROR = "extra_error"
        const val EXTRA_SPEED = "extra_speed" // 实时下载速度 (字节/秒)

        private const val CHANNEL_ID = "updater_download_channel"
        private const val NOTIFICATION_ID = 1024

        /** 失败详情弹窗中展示的响应体最大字符数，避免超长响应撑爆弹窗 */
        private const val MAX_ERROR_BODY_CHARS = 2000

        /** 网络异常（超时 / 连接中断等）时的最大自动续传重试次数 */
        private const val MAX_RETRY_COUNT = 3

        /** 重试基础退避间隔（毫秒）：第 n 次重试等待 n 倍 */
        private const val RETRY_DELAY_BASE_MS = 1000L
    }

    // 保证单线程单任务顺序执行，避免并发下载
    private val executor = Executors.newSingleThreadExecutor()
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private val activeTasks = ConcurrentHashMap<String, DownloadTask>()
    private lateinit var dbHelper: DownloadDatabaseHelper

    /**
     * 下载专用客户端。
     * readTimeout 指的是“两次收到数据之间的最大间隔”（默认仅 10 秒），
     * 慢速 CDN/移动网络稍有抖动就会被误判为超时，因此放宽到 60 秒；
     * 同时不设置 callTimeout（保持 0），避免大文件被整体时长上限中断。
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        dbHelper = DownloadDatabaseHelper(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        
        val action = intent.action
        val task = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("task", DownloadTask::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("task") as? DownloadTask
        }

        if (task != null) {
            when (action) {
                ACTION_START -> {
                    // 确保前台服务通知立即展示，避免 Android 8+ 前台服务超时异常
                    showForegroundNotification()
                    startDownloadTask(task)
                }
                ACTION_PAUSE -> {
                    pauseDownloadTask(task.id)
                }
            }
        }

        return START_NOT_STICKY
    }

    /**
     * 提取服务端真实响应内容用于失败弹窗展示。
     * 只取响应体本身（失败原因都在这里），响应体为空时回退为状态行，保证原因不为空。
     */
    private fun buildHttpErrorMessage(response: Response): String {
        val body = try {
            response.body?.string()?.trim()?.take(MAX_ERROR_BODY_CHARS).orEmpty()
        } catch (_: Exception) {
            ""
        }
        return body.ifBlank { "HTTP ${response.code} ${response.message}".trim() }
    }

    private fun isApkFullyReady(task: DownloadTask, file: File): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        if (task.totalBytes > 0 && file.length() != task.totalBytes) return false
        if (!task.fileMd5.isNullOrBlank() && !ApkInstaller.verifyApkMd5(file, task.fileMd5)) return false
        return try {
            val info = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            info != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun startDownloadTask(task: DownloadTask) {
        // 1. 防重复点击：若该任务已在运行，直接忽略
        if (activeTasks.containsKey(task.id)) return

        // 2. 单例下载：若已有任务在下载中，不并发下载
        if (activeTasks.isNotEmpty()) return

        // 3. 幂等前置检查：若本地已有完整合法安装包，直接复用唤起安装，免去网络请求
        val targetFile = File(task.savePath)
        if (isApkFullyReady(task, targetFile)) {
            task.status = DownloadTask.STATUS_COMPLETED
            task.downloadedBytes = targetFile.length()
            dbHelper.insertOrUpdateTask(task)
            dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_COMPLETED)
            sendProgressBroadcast(task)
            UpdaterLog.i("命中本地完整安装包，直接复用: ${targetFile.name}")
            ApkInstaller.installApk(this, targetFile)
            checkStopService()
            return
        }

        activeTasks[task.id] = task
        dbHelper.insertOrUpdateTask(task)
        
        showForegroundNotification()

        executor.submit {
            runDownload(task)
        }
    }

    private fun pauseDownloadTask(taskId: String) {
        val call = activeCalls[taskId]
        call?.cancel()
        activeCalls.remove(taskId)
        
        val task = activeTasks[taskId]
        if (task != null) {
            task.status = DownloadTask.STATUS_PAUSED
            dbHelper.updateTaskProgress(taskId, task.downloadedBytes, DownloadTask.STATUS_PAUSED)
            sendProgressBroadcast(task)
            activeTasks.remove(taskId)
        }
        
        checkStopService()
    }

    /**
     * 下载任务入口：负责重试调度与任务收尾。
     *
     * 网络类异常（超时 / 连接中断等）最多自动续传重试 [MAX_RETRY_COUNT] 次，
     * 每次基于已下载字节重新发起 Range 请求，避免一次抖动就整包失败；
     * HTTP 业务错误、用户暂停、重试耗尽都会立即结束。
     */
    private fun runDownload(task: DownloadTask) {
        val tempFile = File(task.savePath)
        val parentDir = tempFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        task.status = DownloadTask.STATUS_DOWNLOADING
        dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_DOWNLOADING)
        sendProgressBroadcast(task)

        try {
            var attempt = 0
            while (true) {
                // 已被用户暂停 / 任务已移除：立即结束，不重试也不标记失败
                if (isCancelledOrPaused(task)) return

                try {
                    downloadOnce(task, tempFile)
                    return
                } catch (e: Exception) {
                    if (isCancelledOrPaused(task) || activeCalls[task.id]?.isCanceled() == true) return

                    attempt++
                    // 非网络类异常（如 HTTP 错误文案）不重试；网络异常最多重试 MAX_RETRY_COUNT 次
                    if (e !is IOException || attempt > MAX_RETRY_COUNT) {
                        markDownloadFailed(task, e)
                        return
                    }

                    UpdaterLog.i("下载异常，第 $attempt/$MAX_RETRY_COUNT 次续传重试: ${e.javaClass.simpleName} ${e.message}")
                    task.status = DownloadTask.STATUS_DOWNLOADING
                    dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_DOWNLOADING)
                    sendProgressBroadcast(task)
                    try {
                        // 递增退避，给网络恢复留出时间
                        Thread.sleep(RETRY_DELAY_BASE_MS * attempt)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
            }
        } finally {
            activeCalls.remove(task.id)
            activeTasks.remove(task.id)
            checkStopService()
        }
    }

    private fun isCancelledOrPaused(task: DownloadTask): Boolean =
        task.status == DownloadTask.STATUS_PAUSED || !activeTasks.containsKey(task.id)

    /**
     * 单次下载尝试：按当前已下载字节发起 Range 续传请求并写入文件。
     * 网络类异常向上抛出交由重试调度处理；HTTP 错误、MD5 校验、唤起安装在此收尾。
     */
    private fun downloadOnce(task: DownloadTask, tempFile: File) {
        val downloaded = tempFile.length()
        task.downloadedBytes = downloaded

        UpdaterLog.i("开始下载: ${tempFile.name}, 当前偏移量: $downloaded 字节")

        val requestBuilder = Request.Builder()
            .url(task.url)

        if (downloaded > 0) {
            requestBuilder.addHeader("Range", "bytes=$downloaded-")
        }

        val config = com.updater.config.UpdaterConfigManager(this)
        val token = config.adminToken
        // 鉴权头仅用于自有 CF 网盘源；GitHub 直链/代理下载严禁携带，
        // 避免把管理 Token 泄漏给第三方代理服务
        val isThirdPartyUrl = task.url.startsWith("https://github.com/", ignoreCase = true) ||
                task.url.contains("/https://github.com/", ignoreCase = true)
        if (token.isNotEmpty() && !isThirdPartyUrl) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val call = client.newCall(requestBuilder.build())
        activeCalls[task.id] = call

        val response = call.execute()
        if (!response.isSuccessful && response.code != 206) {
            // 直接把服务端真实响应交给失败弹窗展示，不再使用可能失真的固定文案
            throw Exception(buildHttpErrorMessage(response))
        }

        val responseBody = response.body ?: throw Exception("响应体为空，无法读取数据")

        val raf = RandomAccessFile(tempFile, "rw")
        if (response.code == 206) {
            raf.seek(downloaded)
        } else {
            // 服务端不支持断点续传或返回全量，自动截断避免乱码追加
            raf.setLength(0)
            task.downloadedBytes = 0
        }

        val inputStream = responseBody.byteStream()
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var lastUpdate = System.currentTimeMillis()
        var lastSpeedBytes = task.downloadedBytes
        var lastSpeedTime = System.currentTimeMillis()

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                task.downloadedBytes += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdate > 500) {
                    lastUpdate = now
                    val speedBps = if (now > lastSpeedTime) {
                        (task.downloadedBytes - lastSpeedBytes) * 1000 / (now - lastSpeedTime)
                    } else 0L
                    lastSpeedBytes = task.downloadedBytes
                    lastSpeedTime = now
                    dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_DOWNLOADING)
                    sendProgressBroadcast(task, null, speedBps)
                    updateNotification(task, speedBps)
                }
            }
        } finally {
            raf.close()
            inputStream.close()
        }

        if (ApkInstaller.verifyApkMd5(tempFile, task.fileMd5)) {
            task.status = DownloadTask.STATUS_COMPLETED
            task.errorMsg = null
            dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_COMPLETED, null)
            sendProgressBroadcast(task)
            UpdaterLog.i("下载完成且校验通过: ${tempFile.name}")
            // 尝试直接调起系统安装 (隔离异常，坚决不污染已完成的下载状态)
            try {
                ApkInstaller.installApk(this, tempFile)
            } catch (installEx: Throwable) {
                UpdaterLog.e("下载完成后调起安装提示异常: ${installEx.message}", installEx)
            }
        } else {
            val actualMd5 = ApkInstaller.calculateFileMd5(tempFile)
            val md5Err = "MD5 校验不匹配 (期望: ${task.fileMd5.take(8)}..., 实际: ${actualMd5.take(8)}...)"
            task.status = DownloadTask.STATUS_FAILED
            task.errorMsg = md5Err
            dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_FAILED, md5Err)
            sendProgressBroadcast(task, md5Err)
            UpdaterLog.e("MD5 校验失败: ${tempFile.name}, $md5Err")
        }
    }

    /**
     * 记录失败原因并广播给界面（弹窗会原样展示该内容）。
     */
    private fun markDownloadFailed(task: DownloadTask, e: Exception) {
        val detailError = when {
            e is java.net.UnknownHostException -> "无法解析域名，请检查网络连接"
            e is java.net.SocketTimeoutException -> "网络连接或读取超时（已自动续传重试 $MAX_RETRY_COUNT 次）"
            e is java.net.ConnectException -> "连接服务器失败: ${e.message ?: "连接被拒绝"}"
            !e.message.isNullOrBlank() -> e.message!!
            else -> e.javaClass.simpleName
        }
        task.status = DownloadTask.STATUS_FAILED
        task.errorMsg = detailError
        dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_FAILED, detailError)
        sendProgressBroadcast(task, detailError)
        UpdaterLog.e("下载异常: $detailError", e)
    }

    private fun sendProgressBroadcast(task: DownloadTask, errorMsg: String? = null, speedBps: Long = 0) {
        val intent = Intent(BROADCAST_ACTION).apply {
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_DOWNLOADED, task.downloadedBytes)
            putExtra(EXTRA_TOTAL, task.totalBytes)
            putExtra(EXTRA_STATUS, task.status)
            putExtra(EXTRA_ERROR, errorMsg)
            putExtra(EXTRA_SPEED, speedBps)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "文件下载服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "展示网盘更新与配套模块的下载进度"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在准备下载")
            .setContentText("请稍候...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(task: DownloadTask, speedBps: Long = 0) {
        val progressPercent = if (task.totalBytes > 0) ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()) * 100).toInt() else 0
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载: ${task.title}")
            .setContentText("进度: $progressPercent%  ·  ${formatSpeed(speedBps)}")
            .setProgress(100, progressPercent, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "--"
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var size = bytesPerSec.toDouble()
        var i = 0
        while (size >= 1024 && i < units.size - 1) {
            size /= 1024
            i++
        }
        return String.format("%.1f %s", size, units[i])
    }

    private fun checkStopService() {
        if (activeTasks.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        for (call in activeCalls.values) {
            try {
                call.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeCalls.clear()
        activeTasks.clear()
        executor.shutdownNow()
        super.onDestroy()
    }
}
