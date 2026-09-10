package com.updater.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.updater.Updater
import com.updater.config.UpdaterConfigManager
import com.updater.db.DownloadDatabaseHelper
import com.updater.db.DownloadTask
import com.updater.download.ForegroundDownloadService
import com.updater.model.UpdateInfo
import com.updater.model.UpdatePackage
import com.updater.utils.ApkCleanupManager
import com.updater.utils.ApkInstaller
import com.updater.utils.MarkdownUtils
import com.updater.utils.UpdatePathManager
import com.updater.utils.UpdaterLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 更新与下载中心（Jetpack Compose 版）
 *
 * UI 采用 Material3 + 动态取色，与宿主 App 主题体系一致；
 * 业务逻辑（任务对账、下载控制、广播同步、安装与清理策略）与旧版完全一致。
 * 清理策略：仅在系统安装生效广播后精准清理，页面生命周期内绝不扫描删除。
 */
class DownloadManagerActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_ADMIN_MANAGE = 9101
    }

    // ---------- 业务状态 ----------

    private lateinit var dbHelper: DownloadDatabaseHelper
    private lateinit var configManager: UpdaterConfigManager

    private var updateInfo by mutableStateOf<UpdateInfo?>(null)
    private val tasks = mutableStateMapOf<String, DownloadTask>()
    private val speedMap = mutableStateMapOf<String, Long>() // taskId -> 实时速度 (字节/秒)
    private val isRefreshingUpdates = AtomicBoolean(false)
    private var apkCacheSize by mutableStateOf(0L)
    private var isActivityResumed = false
    private val autoInstalledTasks = mutableSetOf<String>()

    private var errorDialog by mutableStateOf<ErrorDialogData?>(null)
    private var showSourceSettings by mutableStateOf(false)

    private data class ErrorDialogData(val title: String, val details: String)

    // ---------- 生命周期 ----------

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        apkCacheSize = getDownloadedApkCacheSize()
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun openAdminManagerActivity() {
        val currentAppId = updateInfo?.appId ?: packageName
        val intent = Intent(this, AdminManagerActivity::class.java).apply {
            putExtra(AdminManagerActivity.EXTRA_APP_ID, currentAppId)
        }
        startActivityForResult(intent, REQUEST_CODE_ADMIN_MANAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            dbHelper = DownloadDatabaseHelper(this)
            configManager = UpdaterConfigManager(this)

            // 优先从内存单例与本地持久化安全读取，彻底规避 Intent 反序列化崩溃
            var info: UpdateInfo? = Updater.lastUpdateInfo ?: try {
                configManager.getCachedUpdateInfo()
            } catch (_: Throwable) { null }

            if (info == null) {
                try {
                    info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra("update_info", UpdateInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getSerializableExtra("update_info") as? UpdateInfo
                    }
                } catch (_: Throwable) {}
            }

            updateInfo = info
            if (info != null) {
                try {
                    configManager.saveCachedUpdateInfo(info)
                } catch (_: Throwable) {}
            }

            try {
                initPackageTasks()
            } catch (e: Throwable) {
                UpdaterLog.e("初始化任务列表失败", e)
            }

            enableEdgeToEdge()
            setContent {
                UpdaterComposeTheme {
                    DownloadCenterScreen()
                }
            }
        } catch (e: Throwable) {
            UpdaterLog.e("DownloadManagerActivity 初始化异常", e)
            Toast.makeText(this, "进入更新管理失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ---------- 广播同步 ----------

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val taskId = intent.getStringExtra(ForegroundDownloadService.EXTRA_TASK_ID) ?: return
            val downloaded = intent.getLongExtra(ForegroundDownloadService.EXTRA_DOWNLOADED, 0)
            val status = intent.getIntExtra(ForegroundDownloadService.EXTRA_STATUS, DownloadTask.STATUS_PENDING)
            val error = intent.getStringExtra(ForegroundDownloadService.EXTRA_ERROR)
            val speed = intent.getLongExtra(ForegroundDownloadService.EXTRA_SPEED, 0)

            val task = tasks[taskId] ?: return
            if (status == DownloadTask.STATUS_DOWNLOADING) speedMap[taskId] = speed else speedMap.remove(taskId)
            val prevStatus = task.status
            tasks[taskId] = task.copy(
                downloadedBytes = downloaded,
                status = status,
                errorMsg = error ?: task.errorMsg
            )
            if (status == DownloadTask.STATUS_COMPLETED) {
                apkCacheSize = getDownloadedApkCacheSize()
                if (prevStatus != DownloadTask.STATUS_COMPLETED && isActivityResumed && !autoInstalledTasks.contains(taskId)) {
                    autoInstalledTasks.add(taskId)
                    installApk(taskId)
                }
            }
        }
    }

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            // 仅在系统确认某包真正安装/覆盖生效后，对该包做精准清理，绝不在页面恢复时全量扫描
            val pkgName = intent.dataString?.removePrefix("package:")
            if (!pkgName.isNullOrBlank()) {
                ApkCleanupManager.cleanInstalledApkForPackage(this@DownloadManagerActivity, pkgName)
            }
            syncTasksFromDb()
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            val filter = IntentFilter(ForegroundDownloadService.BROADCAST_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(progressReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(progressReceiver, filter)
            }
        } catch (_: Exception) {}

        try {
            val installFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(installReceiver, installFilter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(installReceiver, installFilter)
            }
        } catch (_: Exception) {}

        syncTasksFromDb()
    }

    override fun onStop() {
        try {
            unregisterReceiver(progressReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(installReceiver)
        } catch (_: Exception) {}
        super.onStop()
    }

    // ---------- 任务对账与持久化 ----------

    private fun initPackageTasks() {
        val apkDir = UpdatePathManager.getUpdateDir(this)
        val info = updateInfo
        if (info != null && info.packages.isNotEmpty()) {
            for (pkg in info.packages) {
                val taskId = getTaskId(pkg.downloadUrl)
                val expectedUrl = getAbsoluteUrl(pkg.downloadUrl)
                var task = dbHelper.getTask(taskId)
                if (task == null) {
                    val cleanUrl = pkg.downloadUrl.substringBefore("?")
                    val rawFileName = cleanUrl.substringAfterLast("/").ifEmpty { "app_${pkg.packageId}.apk" }
                    val fileName = if (rawFileName.endsWith(".apk", ignoreCase = true)) rawFileName else "$rawFileName.apk"
                    val saveFile = File(apkDir, fileName)

                    task = DownloadTask(
                        id = taskId,
                        url = expectedUrl,
                        savePath = saveFile.absolutePath,
                        title = pkg.packageName,
                        totalBytes = pkg.apkSize,
                        downloadedBytes = 0,
                        status = DownloadTask.STATUS_PENDING,
                        fileMd5 = pkg.apkMd5
                    )
                } else if (task.status != DownloadTask.STATUS_DOWNLOADING && task.url != expectedUrl) {
                    task = task.copy(url = expectedUrl)
                    dbHelper.insertOrUpdateTask(task)
                }
                tasks[taskId] = reconcileTaskFileState(task)
            }
        } else {
            val localTasks = dbHelper.getAllTasks()
            for (task in localTasks) {
                tasks[task.id] = reconcileTaskFileState(task)
            }
        }
    }

    private fun reconcileTaskFileState(task: DownloadTask): DownloadTask {
        val file = File(task.savePath)
        return if (file.exists() && file.length() > 0) {
            val lengthMatches = (task.totalBytes <= 0 || file.length() >= task.totalBytes)
            val md5Valid = task.fileMd5.isBlank() || ApkInstaller.verifyApkMd5(file, task.fileMd5)
            val updated = task.copy()
            if (task.status == DownloadTask.STATUS_COMPLETED || (lengthMatches && md5Valid)) {
                updated.status = DownloadTask.STATUS_COMPLETED
                updated.downloadedBytes = file.length()
            } else if (task.status != DownloadTask.STATUS_DOWNLOADING) {
                updated.downloadedBytes = file.length()
            }
            dbHelper.insertOrUpdateTask(updated)
            updated
        } else {
            if (task.status == DownloadTask.STATUS_COMPLETED) {
                val updated = task.copy(status = DownloadTask.STATUS_PENDING, downloadedBytes = 0)
                dbHelper.insertOrUpdateTask(updated)
                updated
            } else {
                task
            }
        }
    }

    private fun syncTasksFromDb() {
        for ((taskId, _) in tasks) {
            val dbTask = dbHelper.getTask(taskId) ?: continue
            tasks[taskId] = reconcileTaskFileState(dbTask)
        }
    }

    // ---------- 下载控制 ----------

    private fun installApk(taskId: String) {
        val task = tasks[taskId] ?: return
        val file = File(task.savePath)
        if (file.exists()) {
            ApkInstaller.installApk(this, file)
        }
    }

    private fun startDownload(taskId: String) {
        val originalTask = tasks[taskId] ?: return

        if (originalTask.status == DownloadTask.STATUS_COMPLETED) {
            ApkInstaller.installApk(this, File(originalTask.savePath))
            return
        }

        if (originalTask.status == DownloadTask.STATUS_DOWNLOADING) {
            return
        }

        val currentDownloading = tasks.values.find { it.status == DownloadTask.STATUS_DOWNLOADING && it.id != taskId }
        if (currentDownloading != null) {
            Toast.makeText(this, "已有任务正在下载，请等待完成或暂停", Toast.LENGTH_SHORT).show()
            return
        }

        val freshUrl = getAbsoluteUrl(originalTask.url)
        val task = if (originalTask.url != freshUrl) {
            val updated = originalTask.copy(url = freshUrl)
            dbHelper.insertOrUpdateTask(updated)
            tasks[taskId] = updated
            updated
        } else {
            originalTask
        }

        val serviceIntent = Intent(this, ForegroundDownloadService::class.java).apply {
            action = ForegroundDownloadService.ACTION_START
            putExtra("task", task)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        tasks[taskId] = task.copy(status = DownloadTask.STATUS_DOWNLOADING)
    }

    private fun pauseDownload(taskId: String) {
        val task = tasks[taskId] ?: return
        val serviceIntent = Intent(this, ForegroundDownloadService::class.java).apply {
            action = ForegroundDownloadService.ACTION_PAUSE
            putExtra("task", task)
        }
        startService(serviceIntent)

        speedMap.remove(taskId)
        tasks[taskId] = task.copy(status = DownloadTask.STATUS_PAUSED)
    }

    private fun deleteDownload(taskId: String) {
        val task = tasks[taskId] ?: return
        pauseDownload(taskId)

        val file = File(task.savePath)
        if (file.exists()) {
            file.delete()
        }

        tasks[taskId] = task.copy(status = DownloadTask.STATUS_PENDING, downloadedBytes = 0)
        dbHelper.deleteTask(taskId)
    }

    // ---------- 更新检测 ----------

    private fun doRefreshUpdates() {
        if (!isRefreshingUpdates.compareAndSet(false, true)) {
            Toast.makeText(applicationContext, "正在检查更新，请勿重复点击", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(applicationContext, "正在检查更新...", Toast.LENGTH_SHORT).show()
        val updater = Updater.getInstance(this)
        updater.checkUpdate(
            onUpdateAvailable = { newInfo ->
                isRefreshingUpdates.set(false)
                updateInfo = newInfo
                Updater.lastUpdateInfo = newInfo
                configManager.saveCachedUpdateInfo(newInfo)
                initPackageTasks()

                val localName = getLocalVersionName()
                val localCode = getLocalVersionCode()
                if (Updater.isNewerVersion(newInfo.latestVersionName, newInfo.latestVersionCode, localName, localCode)) {
                    Toast.makeText(applicationContext, "发现新版本 v${newInfo.latestVersionName}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                }
            },
            onNoUpdate = {
                isRefreshingUpdates.set(false)
                Toast.makeText(applicationContext, "当前已是最新版本", Toast.LENGTH_SHORT).show()
            },
            onError = { err ->
                isRefreshingUpdates.set(false)
                Toast.makeText(applicationContext, "刷新失败: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun getLocalVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            pInfo.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun getLocalVersionCode(): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            0
        }
    }

    // ---------- 工具 ----------

    private fun getTaskId(url: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString(16)
        }
    }

    private fun getAbsoluteUrl(relativeUrl: String): String {
        // GitHub Release 直链加速：若配置了代理（如自建 CF Worker gh-proxy 或 ghproxy.net），
        // 改写为业界通用的 "代理域名/原始完整URL" 格式，由 Worker 流式转发下载体
        var proxyHost = try {
            configManager.githubProxyHost.trim().trimEnd('/')
        } catch (_: Throwable) { "" }
        if (proxyHost.isNotEmpty() && !proxyHost.startsWith("http://", ignoreCase = true) && !proxyHost.startsWith("https://", ignoreCase = true)) {
            proxyHost = "https://$proxyHost"
        }

        // 提取真实的 GitHub 原目标地址（避免被重复拼接或嵌套代理）
        val cleanRelative = if (relativeUrl.contains("/https://github.com/", ignoreCase = true)) {
            relativeUrl.substring(relativeUrl.indexOf("https://github.com/", ignoreCase = true))
        } else if (relativeUrl.contains("/http://github.com/", ignoreCase = true)) {
            "https://" + relativeUrl.substring(relativeUrl.indexOf("http://github.com/", ignoreCase = true) + "http://".length)
        } else {
            relativeUrl
        }

        if (proxyHost.isNotEmpty() && cleanRelative.startsWith("https://github.com/", ignoreCase = true)) {
            return "$proxyHost/$cleanRelative"
        }
        if (cleanRelative.startsWith("https://github.com/", ignoreCase = true)) {
            return cleanRelative
        }

        val customDownloadHost = intent.getStringExtra("download_host")
        if (!customDownloadHost.isNullOrEmpty()) {
            val host = customDownloadHost.trimEnd('/')
            val path = if (relativeUrl.startsWith("http://", ignoreCase = true) || relativeUrl.startsWith("https://", ignoreCase = true)) {
                try {
                    val uri = java.net.URI(relativeUrl)
                    uri.rawPath + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                } catch (e: Exception) {
                    "/" + relativeUrl.substringAfter("://").substringAfter("/", "")
                }
            } else {
                "/" + relativeUrl.removePrefix("/")
            }
            return "$host$path"
        }

        if (relativeUrl.startsWith("http", ignoreCase = true)) return relativeUrl
        val baseHost = intent.getStringExtra("base_host") ?: "https://cicha.de5.net"
        val host = baseHost.trimEnd('/')
        return "$host/" + relativeUrl.removePrefix("/")
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var sizeD = size.toDouble()
        var i = 0
        while (sizeD >= 1024 && i < units.size - 1) {
            sizeD /= 1024
            i++
        }
        return String.format("%.1f %s", sizeD, units[i])
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

    private fun getDownloadedApkCacheSize(): Long {
        return try {
            val apkDir = UpdatePathManager.getUpdateDir(this)
            apkDir.listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }
                ?.sumOf { it.length() } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun cleanDownloadedApkCache(): Long {
        return try {
            val apkDir = UpdatePathManager.getUpdateDir(this)
            var freedBytes = 0L
            apkDir.listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }
                ?.forEach { file ->
                    freedBytes += file.length()
                    file.delete()
                }
            freedBytes
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatTimestamp(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        return try {
            val now = System.currentTimeMillis()
            val calNow = Calendar.getInstance().apply { timeInMillis = now }
            val calTarget = Calendar.getInstance().apply { timeInMillis = timestampMs }
            val isToday = calNow.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) &&
                    calNow.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)
            val isSameYear = calNow.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR)
            when {
                isToday -> SimpleDateFormat("今天 HH:mm", Locale.CHINA).format(Date(timestampMs))
                isSameYear -> SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestampMs))
                else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestampMs))
            }
        } catch (_: Exception) {
            ""
        }
    }

    // ================= Compose UI =================

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    private fun DownloadCenterScreen() {
        val colors = MaterialTheme.colorScheme

        Scaffold(
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "更新与下载",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { openAdminManagerActivity() }
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(onClick = { doRefreshUpdates() }) { Text("刷新") }
                        TextButton(onClick = { showSourceSettings = true }) { Text("源设置") }
                        TextButton(onClick = { openAdminManagerActivity() }) { Text("管理") }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { UpdateInfoCard() }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "安装包列表",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant
                        )
                        if (apkCacheSize > 0L) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.primary.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable {
                                    val freed = cleanDownloadedApkCache()
                                    apkCacheSize = getDownloadedApkCacheSize()
                                    syncTasksFromDb()
                                    Toast.makeText(applicationContext, "已清理本地安装包缓存，释放 ${formatSize(freed)}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "已占用 ${formatSize(apkCacheSize)} (点击清理)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
                taskItems()
            }

            errorDialog?.let { data ->
                ErrorDetailsDialog(data) { errorDialog = null }
            }

            if (showSourceSettings) {
                SourceSettingsDialogHost(
                    onDismiss = { showSourceSettings = false },
                    onSourceChanged = { doRefreshUpdates() }
                )
            }
        }
    }

    @Composable
    private fun UpdateInfoCard() {
        val colors = MaterialTheme.colorScheme
        val info = updateInfo
        var isLogExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info?.appName ?: "芝麻粒 (Sesame-TK)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.primaryContainer
                    ) {
                        Text(
                            text = configManager.getSelectedSource()?.name ?: "官方源",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (info != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "最新版本: v${info.latestVersionName} (${info.latestVersionCode})",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                        val timeStr = formatTimestamp(info.lastUpdated)
                        if (timeStr.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "发布于 $timeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val logText = info.updateLog.ifBlank { "优化了用户体验和细节。" }
                    val isLong = logText.length > 200 || logText.count { it == '\n' } > 5
                    Box(
                        modifier = if (isLong && !isLogExpanded) {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    ) {
                        MarkdownText(
                            markdown = logText,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (isLong) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isLogExpanded = !isLogExpanded },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isLogExpanded) "收起说明 ▲" else "查看完整更新说明 ▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "管理应用及配套安装包",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
        val rendered = remember(markdown) {
            try {
                MarkdownUtils.renderMarkdown(this@DownloadManagerActivity, markdown)
            } catch (_: Throwable) {
                markdown
            }
        }
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextView(ctx).apply {
                    movementMethod = LinkMovementMethod.getInstance()
                    textSize = 13f
                }
            },
            update = { it.text = rendered }
        )
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.taskItems() {
        val info = updateInfo
        if (info != null && info.packages.isNotEmpty()) {
            for (pkg in info.packages) {
                val taskId = getTaskId(pkg.downloadUrl)
                item(key = taskId) {
                    TaskCard(
                        taskId = taskId,
                        title = pkg.packageName,
                        nominalSize = pkg.apkSize,
                        description = pkg.description,
                        updatedAt = pkg.updatedAt,
                        expectedMd5 = pkg.apkMd5
                    )
                }
            }
        } else if (tasks.isNotEmpty()) {
            for ((id, task) in tasks) {
                item(key = id) {
                    TaskCard(
                        taskId = id,
                        title = task.title,
                        nominalSize = task.totalBytes,
                        description = "本地安装包: ${File(task.savePath).name}",
                        updatedAt = File(task.savePath).takeIf { it.exists() }?.lastModified() ?: 0L,
                        expectedMd5 = ""
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "暂无安装包，可点击右上角「刷新」检测",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun TaskCard(
        taskId: String,
        title: String,
        nominalSize: Long,
        description: String,
        updatedAt: Long = 0L,
        expectedMd5: String = ""
    ) {
        val colors = MaterialTheme.colorScheme
        val task = tasks[taskId]
        val status = task?.status ?: DownloadTask.STATUS_PENDING
        val speedBps = speedMap[taskId] ?: 0L

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                // 标题行：名称 + 大小
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (nominalSize > 0) {
                        Text(
                            text = formatSize(nominalSize),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val effectiveTime = if (updatedAt > 0L) updatedAt else (task?.let { File(it.savePath).takeIf { f -> f.exists() }?.lastModified() } ?: 0L)
                    val timeStr = formatTimestamp(effectiveTime)
                    if (timeStr.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.outline
                        )
                    }
                }

                // 进度条（下载中 / 已暂停可见）
                val showProgress = status == DownloadTask.STATUS_DOWNLOADING || status == DownloadTask.STATUS_PAUSED
                if (showProgress && task != null && task.totalBytes > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()).coerceIn(0.0, 1.0)).toFloat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (status == DownloadTask.STATUS_DOWNLOADING) colors.primary else colors.outline
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatSize(task.downloadedBytes)} / ${formatSize(task.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 状态行 + 操作按钮行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = statusLabel(status, task, speedBps),
                                style = MaterialTheme.typography.labelMedium,
                                color = when (status) {
                                    DownloadTask.STATUS_DOWNLOADING, DownloadTask.STATUS_COMPLETED -> colors.primary
                                    DownloadTask.STATUS_FAILED -> colors.error
                                    else -> colors.onSurfaceVariant
                                },
                                fontWeight = if (status == DownloadTask.STATUS_FAILED) FontWeight.Bold else FontWeight.Normal,
                                modifier = if (status == DownloadTask.STATUS_FAILED && task != null) {
                                    Modifier.combinedClickable(onClick = {
                                        errorDialog = ErrorDialogData(
                                            title = "失败详情",
                                            details = "名称: ${task.title}\n地址: ${task.url}\n路径: ${task.savePath}\n原因: ${task.errorMsg ?: "网络连接异常"}"
                                        )
                                    })
                                } else {
                                    Modifier
                                }
                            )
                            if (status == DownloadTask.STATUS_COMPLETED && expectedMd5.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "✔ MD5已校验",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                    TaskActionButtons(taskId, status)
                }
            }
        }
    }

    @Composable
    private fun statusLabel(status: Int, task: DownloadTask?, speedBps: Long): String {
        return when (status) {
            DownloadTask.STATUS_PENDING -> "未下载"
            DownloadTask.STATUS_DOWNLOADING -> {
                val pct = if (task != null && task.totalBytes > 0) {
                    ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()) * 100).toInt()
                } else 0
                if (speedBps > 0) "下载中: $pct% · ${formatSpeed(speedBps)}" else "下载中: $pct%"
            }
            DownloadTask.STATUS_PAUSED -> {
                val pct = if (task != null && task.totalBytes > 0) {
                    ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()) * 100).toInt()
                } else 0
                "已暂停 ($pct%)"
            }
            DownloadTask.STATUS_COMPLETED -> "已就绪"
            else -> "下载失败 (点击查看详情)"
        }
    }

    @Composable
    private fun TaskActionButtons(taskId: String, status: Int) {
        val colors = MaterialTheme.colorScheme
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (status) {
                DownloadTask.STATUS_PENDING -> {
                    PrimaryActionButton("下载") { startDownload(taskId) }
                }
                DownloadTask.STATUS_DOWNLOADING -> {
                    PrimaryActionButton("暂停", container = colors.tertiary) { pauseDownload(taskId) }
                }
                DownloadTask.STATUS_PAUSED -> {
                    SecondaryActionButton("删除", container = colors.error, content = colors.error) {
                        deleteDownload(taskId)
                    }
                    PrimaryActionButton("继续") { startDownload(taskId) }
                }
                DownloadTask.STATUS_COMPLETED -> {
                    SecondaryActionButton("删除", container = colors.error, content = colors.error) {
                        deleteDownload(taskId)
                    }
                    SecondaryActionButton("打开", container = colors.outline, content = colors.onSurface) {
                        val task = tasks[taskId]
                        val targetDir = task?.let { File(it.savePath).parentFile }
                            ?: UpdatePathManager.getUpdateDir(this@DownloadManagerActivity)
                        UpdatePathManager.openUpdateDirectory(this@DownloadManagerActivity, targetDir)
                    }
                    PrimaryActionButton("安装") {
                        tasks[taskId]?.let { ApkInstaller.installApk(this@DownloadManagerActivity, File(it.savePath)) }
                    }
                }
                DownloadTask.STATUS_FAILED -> {
                    SecondaryActionButton("删除", container = colors.error, content = colors.error) {
                        deleteDownload(taskId)
                    }
                    PrimaryActionButton("重试", container = colors.error) { startDownload(taskId) }
                }
            }
        }
    }

    @Composable
    private fun PrimaryActionButton(
        text: String,
        container: Color = MaterialTheme.colorScheme.primary,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = container),
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(text, fontSize = 12.sp)
        }
    }

    @Composable
    private fun SecondaryActionButton(
        text: String,
        container: Color,
        content: Color,
        onClick: () -> Unit
    ) {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = content
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, container.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(text, fontSize = 12.sp)
        }
    }

    @Composable
    private fun ErrorDetailsDialog(data: ErrorDialogData, onDismiss: () -> Unit) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(data.title) },
            text = {
                Text(
                    text = data.details,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("下载错误详情", data.details))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {}
                    onDismiss()
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        )
    }
}
