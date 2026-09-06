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
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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

        private const val CHANNEL_ID = "updater_download_channel"
        private const val NOTIFICATION_ID = 1024
    }

    // 保证单线程单任务顺序执行，避免并发下载
    private val executor = Executors.newSingleThreadExecutor()
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private val activeTasks = ConcurrentHashMap<String, DownloadTask>()
    private lateinit var dbHelper: DownloadDatabaseHelper
    private val client = OkHttpClient()

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

    private fun runDownload(task: DownloadTask) {
        task.status = DownloadTask.STATUS_DOWNLOADING
        dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_DOWNLOADING)
        sendProgressBroadcast(task)

        val tempFile = File(task.savePath)
        val parentDir = tempFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val downloaded = tempFile.length()
        task.downloadedBytes = downloaded

        UpdaterLog.i("开始单线程下载: ${tempFile.name}, 当前偏移量: $downloaded 字节")

        val requestBuilder = Request.Builder()
            .url(task.url)
        
        if (downloaded > 0) {
            requestBuilder.addHeader("Range", "bytes=$downloaded-")
        }

        val call = client.newCall(requestBuilder.build())
        activeCalls[task.id] = call

        try {
            val response = call.execute()
            if (!response.isSuccessful && response.code != 206) {
                throw Exception("HTTP ${response.code}")
            }

            val responseBody = response.body ?: throw Exception("响应体为空")

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

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    raf.write(buffer, 0, bytesRead)
                    task.downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500) {
                        lastUpdate = now
                        dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_DOWNLOADING)
                        sendProgressBroadcast(task)
                        updateNotification(task)
                    }
                }
            } finally {
                raf.close()
                inputStream.close()
            }

            if (ApkInstaller.verifyApkMd5(tempFile, task.fileMd5)) {
                task.status = DownloadTask.STATUS_COMPLETED
                dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_COMPLETED)
                sendProgressBroadcast(task)
                UpdaterLog.i("下载完成且校验通过: ${tempFile.name}")
                // 尝试直接调起系统安装 (隔离异常，坚决不污染已完成的下载状态)
                try {
                    ApkInstaller.installApk(this, tempFile)
                } catch (installEx: Throwable) {
                    UpdaterLog.e("下载完成后调起安装提示异常: ${installEx.message}", installEx)
                }
            } else {
                task.status = DownloadTask.STATUS_FAILED
                dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_FAILED)
                sendProgressBroadcast(task, "MD5 校验失败")
                UpdaterLog.e("MD5 校验失败: ${tempFile.name}")
            }

        } catch (e: Exception) {
            if (!call.isCanceled()) {
                task.status = DownloadTask.STATUS_FAILED
                dbHelper.updateTaskProgress(task.id, task.downloadedBytes, DownloadTask.STATUS_FAILED)
                sendProgressBroadcast(task, e.message ?: "下载错误")
                UpdaterLog.e("下载异常: ${e.message}", e)
            }
        } finally {
            activeCalls.remove(task.id)
            activeTasks.remove(task.id)
            checkStopService()
        }
    }

    private fun sendProgressBroadcast(task: DownloadTask, errorMsg: String? = null) {
        val intent = Intent(BROADCAST_ACTION).apply {
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_DOWNLOADED, task.downloadedBytes)
            putExtra(EXTRA_TOTAL, task.totalBytes)
            putExtra(EXTRA_STATUS, task.status)
            putExtra(EXTRA_ERROR, errorMsg)
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

    private fun updateNotification(task: DownloadTask) {
        val progressPercent = if (task.totalBytes > 0) ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()) * 100).toInt() else 0
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载: ${task.title}")
            .setContentText("下载进度: $progressPercent%")
            .setProgress(100, progressPercent, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
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
