package com.updater.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.text.method.LinkMovementMethod
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
import java.util.concurrent.atomic.AtomicBoolean

class DownloadManagerActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_ADMIN_MANAGE = 9101
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AdminUploadDialog.handleActivityResult(this, requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ADMIN_MANAGE) {
            doRefreshUpdates()
        }
    }

    private fun openAdminManagerActivity() {
        val currentAppId = updateInfo?.appId ?: packageName
        val intent = Intent(this, AdminManagerActivity::class.java).apply {
            putExtra(AdminManagerActivity.EXTRA_APP_ID, currentAppId)
        }
        startActivityForResult(intent, REQUEST_CODE_ADMIN_MANAGE)
    }

    private lateinit var dbHelper: DownloadDatabaseHelper
    private lateinit var configManager: UpdaterConfigManager
    private var updateInfo: UpdateInfo? = null

    private val packageViews = HashMap<String, PackageViewHolder>()
    private val tasks = HashMap<String, DownloadTask>()

    private lateinit var rootView: LinearLayout
    private lateinit var statusBarSpacer: View
    private lateinit var titleBar: LinearLayout
    private lateinit var scrollContent: LinearLayout

    // 单线程刷新保护
    private val isRefreshingUpdates = AtomicBoolean(false)

    // 主题动态调色板（完美对接主项目 AppTheme 与 Material3 DayNight 模式）
    private var isNightMode: Boolean = false
    private lateinit var palette: ThemeUtils.M3Palette
    private var colorBg: Int = 0
    private var colorCard: Int = 0
    private var colorTextPrimary: Int = 0
    private var colorTextSecondary: Int = 0
    private var colorBrand: Int = 0
    private var colorBorder: Int = 0
    private var colorCardInner: Int = 0
    private var colorError: Int = 0
    private var colorWarning: Int = 0

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val taskId = intent.getStringExtra(ForegroundDownloadService.EXTRA_TASK_ID) ?: return
            val downloaded = intent.getLongExtra(ForegroundDownloadService.EXTRA_DOWNLOADED, 0)
            val status = intent.getIntExtra(ForegroundDownloadService.EXTRA_STATUS, DownloadTask.STATUS_PENDING)
            val error = intent.getStringExtra(ForegroundDownloadService.EXTRA_ERROR)

            val task = tasks[taskId]
            if (task != null) {
                task.downloadedBytes = downloaded
                task.status = status
                if (error != null) {
                    task.errorMsg = error
                }
                updateViewHolder(taskId, task, error)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            dbHelper = DownloadDatabaseHelper(this)
            configManager = UpdaterConfigManager(this)

            initThemeColors()

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

            rootView = createRootLayout()
            setContentView(rootView)
            setupSystemBar()

            Thread {
                try {
                    ApkCleanupManager.checkAndCleanOnStartup(this@DownloadManagerActivity)
                } catch (_: Throwable) {}
            }.start()

            try {
                initPackageTasks()
            } catch (e: Throwable) {
                UpdaterLog.e("初始化任务列表失败", e)
            }
        } catch (e: Throwable) {
            UpdaterLog.e("DownloadManagerActivity 初始化异常", e)
            try {
                setContentView(createSafeFallbackView(e.message ?: "页面加载异常"))
            } catch (_: Throwable) {
                Toast.makeText(this, "进入更新管理失败: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun createSafeFallbackView(errorMessage: String): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            setBackgroundColor(if (isNightMode) Color.BLACK else Color.WHITE)
        }
        val tvTitle = TextView(this).apply {
            text = "下载管理页面加载异常"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(12))
        }
        val tvMsg = TextView(this).apply {
            text = errorMessage
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(24))
        }
        val btnRetry = TextView(this).apply {
            text = "重试"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#A5D6A7"))
            }
            setTextColor(Color.parseColor("#1B3320"))
            setPadding(dpToPx(24), dpToPx(10), dpToPx(24), dpToPx(10))
            setOnClickListener { recreate() }
        }
        val btnBack = TextView(this).apply {
            text = "返回上一页"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(12))
            setOnClickListener { finish() }
        }
        root.addView(tvTitle)
        root.addView(tvMsg)
        root.addView(btnRetry)
        root.addView(btnBack)
        return root
    }

    private fun initThemeColors() {
        try {
            isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            palette = ThemeUtils.M3Palette(this)
            colorBg = palette.surface
            colorCard = palette.surface
            colorTextPrimary = palette.onSurface
            colorTextSecondary = palette.onSurfaceVariant
            colorBrand = palette.primary
            colorBorder = palette.outlineVariant
            colorCardInner = palette.surfaceVariant
            colorError = palette.error
            colorWarning = palette.tertiary
        } catch (_: Throwable) {
            isNightMode = false
            colorBg = if (isNightMode) Color.parseColor("#121212") else Color.parseColor("#F5F5F5")
            colorCard = if (isNightMode) Color.parseColor("#1E1E1E") else Color.WHITE
            colorTextPrimary = if (isNightMode) Color.WHITE else Color.BLACK
            colorTextSecondary = if (isNightMode) Color.LTGRAY else Color.DKGRAY
            colorBrand = Color.parseColor("#A5D6A7")
            colorBorder = if (isNightMode) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
            colorCardInner = if (isNightMode) Color.parseColor("#2C2C2C") else Color.parseColor("#F0F0F0")
            colorError = Color.parseColor("#B3261E")
            colorWarning = Color.parseColor("#E65100")
        }
    }

    private fun setupSystemBar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = Color.TRANSPARENT
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val decor = window.decorView ?: return
                val controller = WindowCompat.getInsetsController(window, decor)
                controller.isAppearanceLightStatusBars = !isNightMode
            }
        } catch (_: Throwable) {}
    }

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            ApkCleanupManager.cleanInstalledApks(this@DownloadManagerActivity)
            syncTasksFromDb()
        }
    }

    override fun onResume() {
        super.onResume()
        ApkCleanupManager.cleanInstalledApks(this)
        syncTasksFromDb()
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

    private fun initPackageTasks() {
        val apkDir = UpdatePathManager.getUpdateDir(this)
        val info = updateInfo
        if (info != null && info.packages.isNotEmpty()) {
            for (pkg in info.packages) {
                val taskId = getTaskId(pkg.downloadUrl)
                var task = dbHelper.getTask(taskId)
                if (task == null) {
                    val cleanUrl = pkg.downloadUrl.substringBefore("?")
                    val rawFileName = cleanUrl.substringAfterLast("/").ifEmpty { "app_${pkg.packageId}.apk" }
                    val fileName = if (rawFileName.endsWith(".apk", ignoreCase = true)) rawFileName else "$rawFileName.apk"
                    val saveFile = File(apkDir, fileName)

                    task = DownloadTask(
                        id = taskId,
                        url = getAbsoluteUrl(pkg.downloadUrl),
                        savePath = saveFile.absolutePath,
                        title = pkg.packageName,
                        totalBytes = pkg.apkSize,
                        downloadedBytes = 0,
                        status = DownloadTask.STATUS_PENDING,
                        fileMd5 = pkg.apkMd5
                    )
                }
                reconcileTaskFileState(task)
                tasks[taskId] = task
                updateViewHolder(taskId, task)
            }
        } else {
            val localTasks = dbHelper.getAllTasks()
            for (task in localTasks) {
                reconcileTaskFileState(task)
                tasks[task.id] = task
                updateViewHolder(task.id, task)
            }
        }
    }

    private fun reconcileTaskFileState(task: DownloadTask) {
        val file = File(task.savePath)
        if (file.exists() && file.length() > 0) {
            val lengthMatches = (task.totalBytes <= 0 || file.length() >= task.totalBytes)
            val md5Valid = task.fileMd5.isBlank() || ApkInstaller.verifyApkMd5(file, task.fileMd5)
            if (task.status == DownloadTask.STATUS_COMPLETED || (lengthMatches && md5Valid)) {
                task.status = DownloadTask.STATUS_COMPLETED
                task.downloadedBytes = file.length()
                dbHelper.insertOrUpdateTask(task)
            } else if (task.status != DownloadTask.STATUS_DOWNLOADING) {
                task.downloadedBytes = file.length()
                dbHelper.insertOrUpdateTask(task)
            }
        } else {
            if (task.status == DownloadTask.STATUS_COMPLETED) {
                task.status = DownloadTask.STATUS_PENDING
                task.downloadedBytes = 0
                dbHelper.insertOrUpdateTask(task)
            }
        }
    }

    private fun syncTasksFromDb() {
        for ((taskId, task) in tasks) {
            val dbTask = dbHelper.getTask(taskId)
            if (dbTask != null) {
                task.status = dbTask.status
                task.downloadedBytes = dbTask.downloadedBytes
                reconcileTaskFileState(task)
                updateViewHolder(taskId, task)
            }
        }
    }

    private fun startDownload(taskId: String) {
        val task = tasks[taskId] ?: return

        if (task.status == DownloadTask.STATUS_COMPLETED) {
            ApkInstaller.installApk(this, File(task.savePath))
            return
        }

        if (task.status == DownloadTask.STATUS_DOWNLOADING) {
            return
        }

        val currentDownloading = tasks.values.find { it.status == DownloadTask.STATUS_DOWNLOADING && it.id != taskId }
        if (currentDownloading != null) {
            Toast.makeText(this, "已有任务正在下载，请等待完成或暂停", Toast.LENGTH_SHORT).show()
            return
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

        task.status = DownloadTask.STATUS_DOWNLOADING
        updateViewHolder(taskId, task)
    }

    private fun pauseDownload(taskId: String) {
        val task = tasks[taskId] ?: return
        val serviceIntent = Intent(this, ForegroundDownloadService::class.java).apply {
            action = ForegroundDownloadService.ACTION_PAUSE
            putExtra("task", task)
        }
        startService(serviceIntent)

        task.status = DownloadTask.STATUS_PAUSED
        updateViewHolder(taskId, task)
    }

    private fun deleteDownload(taskId: String) {
        val task = tasks[taskId] ?: return
        pauseDownload(taskId)

        val file = File(task.savePath)
        if (file.exists()) {
            file.delete()
        }

        task.status = DownloadTask.STATUS_PENDING
        task.downloadedBytes = 0
        dbHelper.deleteTask(taskId)

        updateViewHolder(taskId, task)
    }

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

    // --- 执行手动刷新更新列表逻辑（单线程互斥保护） ---
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
                rebuildContentLayout()
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

    // --- 界面构建逻辑（解决状态栏挤压、按钮统一样式、沉浸式适配） ---

    private fun getStatusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dpToPx(24)
    }

    private fun createRootLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. 状态栏独立占位 Spacer：通过 WindowInsets 动态精准绑定高度，彻底杜绝状态栏重叠与刘海遮挡
        statusBarSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, getStatusBarHeight())
            setBackgroundColor(colorCard)
        }
        root.addView(statusBarSpacer)

        // 2. Title Bar：水平线性排布，中间标题自动占据剩余宽度 (weight=1)，绝不与右侧按钮发生物理挤压或重叠
        titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52)).apply {
                elevation = dpToPx(1.5f).toFloat()
            }
            setBackgroundColor(colorCard)
            setPadding(dpToPx(6), 0, dpToPx(12), 0)
        }

        // 返回按钮：高度统一为 32dp，使用矢量向左箭头，与右侧操作按钮保持严格居中对齐
        val btnBack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(dpToPx(6), 0, dpToPx(8), 0)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            layoutParams = lp

            val normalBg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.TRANSPARENT)
            }
            val pressedBg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(palette.primaryContainer)
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
                addState(intArrayOf(), normalBg)
            }
            isClickable = true
            isFocusable = true

            // 返回箭头：矢量居中绘制，规避字符基线错位
            val ivArrow = ImageView(this@DownloadManagerActivity).apply {
                val arrowSize = dpToPx(18)
                val arrowDrawable = BackArrowDrawable(colorTextPrimary, dpToPx(2.2f).toFloat())
                setImageDrawable(arrowDrawable)
                val ivLp = LinearLayout.LayoutParams(arrowSize, arrowSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    rightMargin = dpToPx(2)
                }
                layoutParams = ivLp
            }

            // 返回文字：与箭头严密垂直居中
            val txtLabel = TextView(this@DownloadManagerActivity).apply {
                text = "返回"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextPrimary)
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
                val tvLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                layoutParams = tvLp
            }

            addView(ivArrow)
            addView(txtLabel)

            setOnClickListener { finish() }
        }
        titleBar.addView(btnBack)

        // 标题占据剩余空间并支持省略与长按管理员入口
        val txtTitle = TextView(this).apply {
            text = "更新与下载"
            textSize = 16f
            setTextColor(colorTextPrimary)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                leftMargin = dpToPx(6)
                rightMargin = dpToPx(6)
                gravity = Gravity.CENTER_VERTICAL
            }
            layoutParams = lp
            setOnLongClickListener {
                openAdminManagerActivity()
                true
            }
        }
        titleBar.addView(txtTitle)

        // 右侧操作区：统一样式的按钮组（刷新、源设置、安装包管理）
        val rightActionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            layoutParams = lp
        }

        fun createHeaderButton(label: String, isPrimary: Boolean, onClick: () -> Unit): TextView {
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                if (isPrimary) {
                    setColor(colorBrand)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dpToPx(1), colorBorder)
                }
            }
            return TextView(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isPrimary) palette.onPrimary else colorTextPrimary)
                gravity = Gravity.CENTER
                background = bg
                isClickable = true
                isFocusable = true
                setPadding(dpToPx(8), 0, dpToPx(8), 0)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                    rightMargin = dpToPx(5)
                }
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        val btnRefresh = createHeaderButton("刷新", isPrimary = true) {
            doRefreshUpdates()
        }
        val btnSettings = createHeaderButton("源设置", isPrimary = false) {
            SourceSettingsDialog.show(this@DownloadManagerActivity) {
                doRefreshUpdates()
            }
        }
        val btnUpload = createHeaderButton("管理", isPrimary = false) {
            openAdminManagerActivity()
        }
        (btnUpload.layoutParams as? LinearLayout.LayoutParams)?.rightMargin = 0

        rightActionLayout.addView(btnRefresh)
        rightActionLayout.addView(btnSettings)
        rightActionLayout.addView(btnUpload)

        titleBar.addView(rightActionLayout)
        root.addView(titleBar)

        // Scroll Container
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
            isFillViewport = true
        }

        scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(24))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        // 精准适配状态栏与底部手势条 / 虚拟导航栏
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (statusBarHeight > 0) {
                statusBarSpacer.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    statusBarHeight
                )
            }
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            scrollContent.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(24) + navBarHeight)
            insets
        }

        scrollView.addView(scrollContent)
        root.addView(scrollView)

        rebuildContentLayout()

        return root
    }

    private fun rebuildContentLayout() {
        scrollContent.removeAllViews()
        packageViews.clear()

        // 1. 精简版版本信息卡片 (使用原生 LinearLayout + GradientDrawable 彻底杜绝 Theme 属性缺失闪退)
        val info = updateInfo
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(colorCard)
                cornerRadius = dpToPx(14f).toFloat()
                setStroke(dpToPx(1), colorBorder)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dpToPx(1.5f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(14)
            }
        }

        val headerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        headerCard.addView(headerContent)

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val txtAppName = TextView(this).apply {
            text = info?.appName ?: "芝麻粒 (Sesame-TK)"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topRow.addView(txtAppName)

        val activeSource = configManager.getSelectedSource()
        val txtSourceBadge = TextView(this).apply {
            text = " ${activeSource?.name ?: "官方源"} "
            textSize = 10f
            setTextColor(palette.onPrimaryContainer)
            background = GradientDrawable().apply {
                setColor(palette.primaryContainer)
                cornerRadius = dpToPx(6).toFloat()
            }
            setPadding(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3))
        }
        topRow.addView(txtSourceBadge)
        headerContent.addView(topRow)

        if (info != null) {
            val txtVersionTag = TextView(this).apply {
                text = "最新版本: v${info.latestVersionName} (${info.latestVersionCode})"
                textSize = 12f
                setTextColor(colorBrand)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(4), 0, 0)
            }
            headerContent.addView(txtVersionTag)

            val txtChangelog = TextView(this).apply {
                val mdContent = try {
                    if (info.updateLog.isNotBlank()) {
                        MarkdownUtils.renderMarkdown(this@DownloadManagerActivity, info.updateLog)
                    } else {
                        "优化了用户体验和细节。"
                    }
                } catch (_: Throwable) {
                    info.updateLog.ifBlank { "优化了用户体验和细节。" }
                }
                text = mdContent
                textSize = 12f
                setTextColor(colorTextSecondary)
                setPadding(0, dpToPx(6), 0, 0)
                try {
                    movementMethod = LinkMovementMethod.getInstance()
                } catch (_: Throwable) {}
                setLineSpacing(dpToPx(2).toFloat(), 1.0f)
            }
            headerContent.addView(txtChangelog)
        } else {
            val txtDesc = TextView(this).apply {
                text = "管理应用及配套安装包"
                textSize = 12f
                setTextColor(colorTextSecondary)
                setPadding(0, dpToPx(4), 0, 0)
            }
            headerContent.addView(txtDesc)
        }
        scrollContent.addView(headerCard)

        // 2. 安装包列表标题
        val txtListTitle = TextView(this).apply {
            text = "安装包列表"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextSecondary)
            setPadding(dpToPx(4), 0, 0, dpToPx(8))
        }
        scrollContent.addView(txtListTitle)

        // 3. 安装包列表卡片
        if (info != null && info.packages.isNotEmpty()) {
            for (pkg in info.packages) {
                val pkgCard = createPackageCard(getTaskId(pkg.downloadUrl), pkg.packageName, pkg.apkSize, pkg.description)
                scrollContent.addView(pkgCard)
            }
        } else if (tasks.isNotEmpty()) {
            for ((id, task) in tasks) {
                val pkgCard = createPackageCard(id, task.title, task.totalBytes, "本地安装包: ${File(task.savePath).name}")
                scrollContent.addView(pkgCard)
            }
        } else {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(colorCard)
                    cornerRadius = dpToPx(14f).toFloat()
                    setStroke(dpToPx(1), colorBorder)
                }
                setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            }
            val txtEmpty = TextView(this).apply {
                text = "暂无安装包，可点击右上角「刷新」检测"
                textSize = 13f
                setTextColor(colorTextSecondary)
                gravity = Gravity.CENTER
            }
            emptyCard.addView(txtEmpty)
            scrollContent.addView(emptyCard)
        }
    }

    private fun createCardButton(label: String, isPrimary: Boolean, strokeColor: Int = colorBorder, textColor: Int = colorTextPrimary): TextView {
        val bg = GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            if (isPrimary) {
                setColor(colorBrand)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(1), strokeColor)
            }
        }
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            this.setTextColor(if (isPrimary) palette.onPrimary else textColor)
            gravity = Gravity.CENTER
            background = bg
            isClickable = true
            isFocusable = true
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
        }
    }

    private fun createPackageCard(taskId: String, title: String, sizeBytes: Long, description: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(colorCard)
                cornerRadius = dpToPx(14f).toFloat()
                setStroke(dpToPx(1), colorBorder)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dpToPx(1.5f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(10)
            }
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        card.addView(cardContent)

        // 标题与大小行
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val txtPkgName = TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        titleRow.addView(txtPkgName)

        val txtSize = TextView(this).apply {
            text = if (sizeBytes > 0) formatSize(sizeBytes) else ""
            textSize = 12f
            setTextColor(colorTextSecondary)
        }
        titleRow.addView(txtSize)
        cardContent.addView(titleRow)

        // 描述
        val txtDesc = TextView(this).apply {
            text = description
            textSize = 11f
            setTextColor(colorTextSecondary)
            setPadding(0, dpToPx(3), 0, dpToPx(6))
        }
        cardContent.addView(txtDesc)

        // 原生水平进度条（完全隔离 MDC 主题属性缺失崩溃）
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            try {
                val progressBg = GradientDrawable().apply {
                    setColor(colorCardInner)
                    cornerRadius = dpToPx(3).toFloat()
                }
                val progressFg = GradientDrawable().apply {
                    setColor(colorBrand)
                    cornerRadius = dpToPx(3).toFloat()
                }
                val clipFg = ClipDrawable(progressFg, Gravity.START, ClipDrawable.HORIZONTAL)
                val layerDrawable = LayerDrawable(arrayOf(progressBg, clipFg)).apply {
                    setId(0, android.R.id.background)
                    setId(1, android.R.id.progress)
                }
                progressDrawable = layerDrawable
            } catch (_: Throwable) {}
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6)).apply {
                bottomMargin = dpToPx(8)
            }
            layoutParams = lp
        }
        cardContent.addView(progressBar)

        // 底部状态与操作按钮行
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val txtStatus = TextView(this).apply {
            text = "未下载"
            textSize = 12f
            setTextColor(colorTextSecondary)
            setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        actionsRow.addView(txtStatus)

        val btnOpenDir = createCardButton("打开", isPrimary = false, strokeColor = colorBorder, textColor = colorTextPrimary).apply {
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                rightMargin = dpToPx(6)
            }
            layoutParams = lp
            setOnClickListener {
                val task = tasks[taskId] ?: return@setOnClickListener
                val targetDir = File(task.savePath).parentFile ?: UpdatePathManager.getUpdateDir(this@DownloadManagerActivity)
                UpdatePathManager.openUpdateDirectory(this@DownloadManagerActivity, targetDir)
            }
        }
        actionsRow.addView(btnOpenDir)

        val btnDelete = createCardButton("删除", isPrimary = false, strokeColor = colorError, textColor = colorError).apply {
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                rightMargin = dpToPx(6)
            }
            layoutParams = lp
            setOnClickListener { deleteDownload(taskId) }
        }
        actionsRow.addView(btnDelete)

        val btnAction = createCardButton("下载", isPrimary = true).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32))
            layoutParams = lp
            setOnClickListener {
                val task = tasks[taskId] ?: return@setOnClickListener
                when (task.status) {
                    DownloadTask.STATUS_DOWNLOADING -> pauseDownload(taskId)
                    DownloadTask.STATUS_PENDING, DownloadTask.STATUS_PAUSED, DownloadTask.STATUS_FAILED -> startDownload(taskId)
                    DownloadTask.STATUS_COMPLETED -> ApkInstaller.installApk(this@DownloadManagerActivity, File(task.savePath))
                }
            }
        }
        actionsRow.addView(btnAction)
        cardContent.addView(actionsRow)

        packageViews[taskId] = PackageViewHolder(progressBar, txtStatus, btnAction, btnDelete, btnOpenDir)
        return card
    }

    private fun updateViewHolder(taskId: String, task: DownloadTask, errorMsg: String? = null) {
        val holder = packageViews[taskId] ?: return

        when (task.status) {
            DownloadTask.STATUS_PENDING -> {
                holder.progressBar.visibility = View.GONE
                holder.txtStatus.text = "未下载"
                holder.txtStatus.setTextColor(colorTextSecondary)
                holder.txtStatus.isClickable = false
                holder.txtStatus.setOnClickListener(null)
                holder.btnAction.text = "下载"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.GONE
                holder.btnOpenDir.visibility = View.GONE
                setButtonBgColor(holder.btnAction, colorBrand)
            }
            DownloadTask.STATUS_DOWNLOADING -> {
                holder.progressBar.visibility = View.VISIBLE
                val progressPercent = if (task.totalBytes > 0) ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()) * 100).toInt() else 0
                holder.progressBar.progress = progressPercent
                holder.txtStatus.text = "下载中: $progressPercent%"
                holder.txtStatus.setTextColor(colorBrand)
                holder.txtStatus.isClickable = false
                holder.txtStatus.setOnClickListener(null)
                holder.btnAction.text = "暂停"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.GONE
                holder.btnOpenDir.visibility = View.GONE
                setButtonBgColor(holder.btnAction, colorWarning)
            }
            DownloadTask.STATUS_PAUSED -> {
                holder.progressBar.visibility = View.VISIBLE
                val progressPercent = if (task.totalBytes > 0) ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()) * 100).toInt() else 0
                holder.progressBar.progress = progressPercent
                holder.txtStatus.text = "已暂停 ($progressPercent%)"
                holder.txtStatus.setTextColor(colorTextSecondary)
                holder.txtStatus.isClickable = false
                holder.txtStatus.setOnClickListener(null)
                holder.btnAction.text = "继续"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.GONE
                setButtonBgColor(holder.btnAction, colorBrand)
            }
            DownloadTask.STATUS_COMPLETED -> {
                holder.progressBar.visibility = View.GONE
                holder.txtStatus.text = "已就绪"
                holder.txtStatus.setTextColor(colorBrand)
                holder.txtStatus.isClickable = false
                holder.txtStatus.setOnClickListener(null)
                holder.btnAction.text = "安装"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.VISIBLE
                setButtonBgColor(holder.btnAction, colorBrand)
            }
            DownloadTask.STATUS_FAILED -> {
                holder.progressBar.visibility = View.GONE
                val finalErr = errorMsg ?: task.errorMsg ?: "网络连接异常"
                holder.txtStatus.text = "下载失败 (点击查看详情)"
                holder.txtStatus.setTextColor(colorError)
                holder.txtStatus.isClickable = true
                holder.txtStatus.setOnClickListener {
                    showErrorDetailsDialog(task, finalErr)
                }
                holder.btnAction.text = "重试"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.GONE
                setButtonBgColor(holder.btnAction, colorError)
            }
        }
    }

    private fun showErrorDetailsDialog(task: DownloadTask, errorMsg: String) {
        val details = "名称: ${task.title}\n地址: ${task.url}\n路径: ${task.savePath}\n原因: $errorMsg"

        val tv = TextView(this).apply {
            text = details
            textSize = 13f
            setTextColor(colorTextPrimary)
            setTextIsSelectable(true)
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            setLineSpacing(dpToPx(3).toFloat(), 1.0f)
        }

        val scroll = ScrollView(this).apply {
            addView(tv)
        }

        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("失败详情")
                .setView(scroll)
                .setPositiveButton("复制") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("下载错误详情", details)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("关闭", null)
                .show()
        } catch (_: Throwable) {
            try {
                android.app.AlertDialog.Builder(this)
                    .setTitle("失败详情")
                    .setView(scroll)
                    .setPositiveButton("复制") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("下载错误详情", details)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("关闭", null)
                    .show()
            } catch (_: Throwable) {}
        }
    }

    private fun setButtonBgColor(button: TextView, color: Int) {
        button.background = GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            setColor(color)
        }
        button.setTextColor(if (color == colorBrand) palette.onPrimary else Color.WHITE)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    private data class PackageViewHolder(
        val progressBar: ProgressBar,
        val txtStatus: TextView,
        val btnAction: TextView,
        val btnDelete: TextView,
        val btnOpenDir: TextView
    )

    private class BackArrowDrawable(private val color: Int, private val strokeWidthPx: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@BackArrowDrawable.color
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            path.reset()
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            // 居中绘制向左精细折角：尖端在左侧，上下对称，完全垂直水平居中
            val arrowW = w * 0.36f
            val arrowH = h * 0.50f
            path.moveTo(cx + arrowW * 0.45f, cy - arrowH * 0.5f)
            path.lineTo(cx - arrowW * 0.55f, cy)
            path.lineTo(cx + arrowW * 0.45f, cy + arrowH * 0.5f)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
