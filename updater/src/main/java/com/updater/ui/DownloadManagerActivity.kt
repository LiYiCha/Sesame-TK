package com.updater.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AdminUploadDialog.handleActivityResult(this, requestCode, resultCode, data)
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
    private var colorBg: Int = 0
    private var colorCard: Int = 0
    private var colorTextPrimary: Int = 0
    private var colorTextSecondary: Int = 0
    private var colorBrand: Int = 0
    private var colorBorder: Int = 0
    private var colorCardInner: Int = 0

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
        dbHelper = DownloadDatabaseHelper(this)
        configManager = UpdaterConfigManager(this)

        initThemeColors()
        setupSystemBar()

        var info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("update_info", UpdateInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("update_info") as? UpdateInfo
        }

        if (info == null) {
            info = Updater.lastUpdateInfo ?: configManager.getCachedUpdateInfo()
        }
        updateInfo = info
        if (info != null) {
            configManager.saveCachedUpdateInfo(info)
        }

        rootView = createRootLayout()
        setContentView(rootView)

        Thread {
            ApkCleanupManager.checkAndCleanOnStartup(this@DownloadManagerActivity)
        }.start()

        initPackageTasks()
    }

    private fun resolveColorAttr(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(attr, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                tv.data
            } else {
                try {
                    ContextCompat.getColor(this, tv.resourceId)
                } catch (_: Throwable) {
                    fallback
                }
            }
        } else {
            fallback
        }
    }

    private fun initThemeColors() {
        isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val defaultBg = if (isNightMode) Color.parseColor("#121212") else Color.parseColor("#F4F4F4")
        val defaultCard = if (isNightMode) Color.parseColor("#1E1E1E") else Color.WHITE
        val defaultTextPrimary = if (isNightMode) Color.parseColor("#FFFFFF") else Color.parseColor("#1A1A1A")
        val defaultTextSecondary = if (isNightMode) Color.parseColor("#9E9E9E") else Color.parseColor("#666666")
        val defaultBrand = if (isNightMode) Color.parseColor("#4CAF50") else Color.parseColor("#2D5A27")
        val defaultBorder = if (isNightMode) Color.parseColor("#2D2D2D") else Color.parseColor("#E0E0E0")
        val defaultCardInner = if (isNightMode) Color.parseColor("#252525") else Color.parseColor("#F8F9FA")

        colorBg = resolveColorAttr(android.R.attr.colorBackground, defaultBg)
        colorCard = resolveColorAttr(com.google.android.material.R.attr.colorSurface, defaultCard)
        colorTextPrimary = resolveColorAttr(android.R.attr.textColorPrimary, defaultTextPrimary)
        colorTextSecondary = resolveColorAttr(android.R.attr.textColorSecondary, defaultTextSecondary)
        colorBrand = resolveColorAttr(com.google.android.material.R.attr.colorPrimary, defaultBrand)
        colorBorder = resolveColorAttr(com.google.android.material.R.attr.colorOutline, defaultBorder)
        colorCardInner = resolveColorAttr(com.google.android.material.R.attr.colorSurfaceVariant, defaultCardInner)
    }

    private fun setupSystemBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isNightMode
        }
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

        // 返回按钮
        val btnBack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), dpToPx(6), dpToPx(8), dpToPx(6))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36))
            layoutParams = lp

            val txtArrow = TextView(this@DownloadManagerActivity).apply {
                text = "‹"
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextPrimary)
                setPadding(0, 0, dpToPx(2), dpToPx(2))
            }
            val txtLabel = TextView(this@DownloadManagerActivity).apply {
                text = "返回"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextPrimary)
            }
            addView(txtArrow)
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
                leftMargin = dpToPx(4)
                rightMargin = dpToPx(6)
            }
            layoutParams = lp
            setOnLongClickListener {
                val currentAppId = updateInfo?.appId ?: packageName
                AdminUploadDialog.show(this@DownloadManagerActivity, currentAppId) {
                    doRefreshUpdates()
                }
                true
            }
        }
        titleBar.addView(txtTitle)

        // 右侧操作区：统一样式的按钮组（刷新、源设置、上传包）
        val rightActionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        fun createHeaderButton(label: String, isPrimary: Boolean, onClick: () -> Unit): MaterialButton {
            return if (isPrimary) {
                MaterialButton(this).apply {
                    text = label
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    cornerRadius = dpToPx(8)
                    setBackgroundColor(colorBrand)
                    setTextColor(Color.WHITE)
                    setPadding(dpToPx(8), 0, dpToPx(8), 0)
                    minWidth = dpToPx(44)
                    insetTop = 0
                    insetBottom = 0
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                        rightMargin = dpToPx(5)
                    }
                    layoutParams = lp
                    setOnClickListener { onClick() }
                }
            } else {
                MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = label
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    cornerRadius = dpToPx(8)
                    strokeWidth = dpToPx(1)
                    strokeColor = ColorStateList.valueOf(colorBorder)
                    setTextColor(colorTextPrimary)
                    setPadding(dpToPx(8), 0, dpToPx(8), 0)
                    minWidth = dpToPx(44)
                    insetTop = 0
                    insetBottom = 0
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                        rightMargin = dpToPx(5)
                    }
                    layoutParams = lp
                    setOnClickListener { onClick() }
                }
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
        val btnUpload = createHeaderButton("上传", isPrimary = false) {
            val currentAppId = updateInfo?.appId ?: packageName
            AdminUploadDialog.show(this@DownloadManagerActivity, currentAppId) {
                doRefreshUpdates()
            }
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

        // 沉浸式边距适配（动态调整状态栏 Spacer 高度，根部贴合导航栏）
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val topInset = if (statusBars.top > 0) statusBars.top else getStatusBarHeight()
            if (statusBarSpacer.layoutParams.height != topInset) {
                statusBarSpacer.layoutParams.height = topInset
                statusBarSpacer.requestLayout()
            }
            root.setPadding(0, 0, 0, navBars.bottom)
            insets
        }

        rebuildContentLayout()

        return root
    }

    private fun rebuildContentLayout() {
        scrollContent.removeAllViews()
        packageViews.clear()

        // 1. 精简版版本信息卡片 (MaterialCardView)
        val info = updateInfo
        val headerCard = MaterialCardView(this).apply {
            radius = dpToPx(14f).toFloat()
            strokeWidth = dpToPx(1)
            strokeColor = colorBorder
            setCardBackgroundColor(colorCard)
            cardElevation = dpToPx(1f).toFloat()
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
            setTextColor(colorBrand)
            background = GradientDrawable().apply {
                setColor(if (isNightMode) Color.parseColor("#1B3320") else Color.parseColor("#E8F5E9"))
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
                val mdContent = if (info.updateLog.isNotBlank()) {
                    MarkdownUtils.renderMarkdown(this@DownloadManagerActivity, info.updateLog)
                } else {
                    "优化了用户体验和细节。"
                }
                text = mdContent
                textSize = 12f
                setTextColor(colorTextSecondary)
                setPadding(0, dpToPx(6), 0, 0)
                movementMethod = LinkMovementMethod.getInstance()
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
            val emptyCard = MaterialCardView(this).apply {
                radius = dpToPx(14f).toFloat()
                strokeWidth = dpToPx(1)
                strokeColor = colorBorder
                setCardBackgroundColor(colorCard)
                cardElevation = dpToPx(1f).toFloat()
            }
            val emptyContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            }
            val txtEmpty = TextView(this).apply {
                text = "暂无安装包，可点击右上角「刷新」检测"
                textSize = 13f
                setTextColor(colorTextSecondary)
                gravity = Gravity.CENTER
            }
            emptyContent.addView(txtEmpty)
            emptyCard.addView(emptyContent)
            scrollContent.addView(emptyCard)
        }
    }

    private fun createPackageCard(taskId: String, title: String, sizeBytes: Long, description: String): View {
        val card = MaterialCardView(this).apply {
            radius = dpToPx(14f).toFloat()
            strokeWidth = dpToPx(1)
            strokeColor = colorBorder
            setCardBackgroundColor(colorCard)
            cardElevation = dpToPx(1f).toFloat()
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

        // Material 3 线性进度条
        val progressBar = LinearProgressIndicator(this).apply {
            trackCornerRadius = dpToPx(3)
            trackThickness = dpToPx(4)
            setIndicatorColor(colorBrand)
            trackColor = colorCardInner
            max = 100
            progress = 0
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
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

        val btnOpenDir = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "打开"
            textSize = 12f
            cornerRadius = dpToPx(8)
            strokeWidth = dpToPx(1)
            strokeColor = ColorStateList.valueOf(colorBorder)
            setTextColor(colorTextPrimary)
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            minWidth = dpToPx(48)
            insetTop = 0
            insetBottom = 0
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

        val btnDelete = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "删除"
            textSize = 12f
            cornerRadius = dpToPx(8)
            strokeWidth = dpToPx(1)
            strokeColor = ColorStateList.valueOf(Color.parseColor("#DC3545"))
            setTextColor(Color.parseColor("#DC3545"))
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            minWidth = dpToPx(48)
            insetTop = 0
            insetBottom = 0
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                rightMargin = dpToPx(6)
            }
            layoutParams = lp
            setOnClickListener { deleteDownload(taskId) }
        }
        actionsRow.addView(btnDelete)

        val btnAction = MaterialButton(this).apply {
            text = "下载"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            cornerRadius = dpToPx(8)
            setBackgroundColor(colorBrand)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            minWidth = dpToPx(64)
            insetTop = 0
            insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32))

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
                setButtonBgColor(holder.btnAction, Color.parseColor("#E0A800"))
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
                holder.txtStatus.setTextColor(Color.parseColor("#DC3545"))
                holder.txtStatus.isClickable = true
                holder.txtStatus.setOnClickListener {
                    showErrorDetailsDialog(task, finalErr)
                }
                holder.btnAction.text = "重试"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.GONE
                setButtonBgColor(holder.btnAction, Color.parseColor("#DC3545"))
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

        MaterialAlertDialogBuilder(this)
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
    }

    private fun setButtonBgColor(button: MaterialButton, color: Int) {
        button.setBackgroundColor(color)
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
        val progressBar: LinearProgressIndicator,
        val txtStatus: TextView,
        val btnAction: MaterialButton,
        val btnDelete: MaterialButton,
        val btnOpenDir: MaterialButton
    )
}
