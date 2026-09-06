package com.updater.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.core.view.ViewCompat
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

class DownloadManagerActivity : Activity() {

    private lateinit var dbHelper: DownloadDatabaseHelper
    private lateinit var configManager: UpdaterConfigManager
    private var updateInfo: UpdateInfo? = null

    private val packageViews = HashMap<String, PackageViewHolder>()
    private val tasks = HashMap<String, DownloadTask>()

    private lateinit var rootView: LinearLayout
    private lateinit var titleBar: RelativeLayout
    private lateinit var scrollContent: LinearLayout

    // 主题动态调色板（适配主项目 SesameTheme 白天 / 深色模式）
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

        // 优先从内存单例或本地持久化缓存恢复数据，彻底解决退出重进数据丢失为空的问题
        if (info == null) {
            info = Updater.lastUpdateInfo ?: configManager.getCachedUpdateInfo()
        }
        updateInfo = info
        if (info != null) {
            configManager.saveCachedUpdateInfo(info)
        }

        rootView = createRootLayout()
        setContentView(rootView)

        // 后台静默对账清理已安装完成的历史包
        Thread {
            ApkCleanupManager.checkAndCleanOnStartup(this@DownloadManagerActivity)
        }.start()

        initPackageTasks()
    }

    private fun initThemeColors() {
        isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        colorBg = if (isNightMode) Color.parseColor("#121212") else Color.parseColor("#F4F4F4")
        colorCard = if (isNightMode) Color.parseColor("#1E1E1E") else Color.WHITE
        colorTextPrimary = if (isNightMode) Color.parseColor("#FFFFFF") else Color.parseColor("#1A1A1A")
        colorTextSecondary = if (isNightMode) Color.parseColor("#9E9E9E") else Color.parseColor("#666666")
        colorBrand = if (isNightMode) Color.parseColor("#4CAF50") else Color.parseColor("#2D5A27")
        colorBorder = if (isNightMode) Color.parseColor("#2D2D2D") else Color.parseColor("#E0E0E0")
        colorCardInner = if (isNightMode) Color.parseColor("#252525") else Color.parseColor("#F8F9FA")
    }

    private fun setupSystemBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = window.decorView.systemUiVisibility
                flags = if (!isNightMode) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                window.decorView.systemUiVisibility = flags
            }
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
            Toast.makeText(this, "已有任务【${currentDownloading.title}】正在下载中，请等待完成或先暂停", Toast.LENGTH_SHORT).show()
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

    // --- 执行手动刷新更新列表逻辑 ---
    private fun doRefreshUpdates() {
        Toast.makeText(applicationContext, "正在刷新更新信息...", Toast.LENGTH_SHORT).show()
        val updater = Updater.getInstance(this)
        updater.checkUpdate(
            onUpdateAvailable = { newInfo ->
                updateInfo = newInfo
                Updater.lastUpdateInfo = newInfo
                configManager.saveCachedUpdateInfo(newInfo)
                rebuildContentLayout()
                initPackageTasks()

                val localName = getLocalVersionName()
                val localCode = getLocalVersionCode()
                if (Updater.isNewerVersion(newInfo.latestVersionName, newInfo.latestVersionCode, localName, localCode)) {
                    Toast.makeText(applicationContext, "发现新版本 v${newInfo.latestVersionName}，列表已刷新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, "当前已是最新版本 (${newInfo.latestVersionName})", Toast.LENGTH_SHORT).show()
                }
            },
            onNoUpdate = {
                Toast.makeText(applicationContext, "当前已是最新版本，无新组件", Toast.LENGTH_SHORT).show()
            },
            onError = { err ->
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

    // --- 界面构建逻辑（精简、高颜值、沉浸式 Edge-to-Edge） ---

    private fun createRootLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Title Bar（适配沉浸式 Insets 与状态栏高度）
        titleBar = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                elevation = dpToPx(2).toFloat()
            }
            setBackgroundColor(colorCard)
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        }

        // 1. 明确的返回按钮（严禁用易混淆的还原/旋转图标，换用标准的文字与箭头返回按键）
        val btnBack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), dpToPx(6), dpToPx(10), dpToPx(6))
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, dpToPx(38)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
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

        // 2. 标题居中
        val txtTitle = TextView(this).apply {
            text = "更新与下载中心"
            textSize = 17f
            setTextColor(colorTextPrimary)
            typeface = Typeface.DEFAULT_BOLD
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            layoutParams = lp
        }
        titleBar.addView(txtTitle)

        // 3. 右侧操作区：刷新按钮 + 源设置按钮
        val rightActionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            layoutParams = lp
        }

        val btnRefresh = Button(this).apply {
            text = "刷新"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            val bg = GradientDrawable().apply {
                setColor(colorBrand)
                cornerRadius = dpToPx(14).toFloat()
            }
            background = bg
            val lp = LinearLayout.LayoutParams(dpToPx(56), dpToPx(30)).apply {
                rightMargin = dpToPx(6)
            }
            layoutParams = lp
            setOnClickListener {
                doRefreshUpdates()
            }
        }
        rightActionLayout.addView(btnRefresh)

        val btnSettings = Button(this).apply {
            text = "源设置"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
            val bg = GradientDrawable().apply {
                setColor(colorCardInner)
                setStroke(dpToPx(1), colorBorder)
                cornerRadius = dpToPx(14).toFloat()
            }
            background = bg
            val lp = LinearLayout.LayoutParams(dpToPx(60), dpToPx(30))
            layoutParams = lp
            setOnClickListener {
                SourceSettingsDialog.show(this@DownloadManagerActivity) {
                    doRefreshUpdates()
                }
            }
        }
        rightActionLayout.addView(btnSettings)

        titleBar.addView(rightActionLayout)
        root.addView(titleBar)

        // Scroll Container
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        }

        scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(24))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        scrollView.addView(scrollContent)
        root.addView(scrollView)

        // 沉浸式边距适配（解决顶部系统状态栏挤压与底部导航栏遮挡）
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            titleBar.setPadding(dpToPx(12), statusBars.top + dpToPx(6), dpToPx(12), dpToPx(6))
            root.setPadding(0, 0, 0, navBars.bottom)
            insets
        }

        rebuildContentLayout()

        return root
    }

    private fun rebuildContentLayout() {
        scrollContent.removeAllViews()
        packageViews.clear()

        // 1. 精简版版本信息卡片
        val info = updateInfo
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(colorCard)
                cornerRadius = dpToPx(12).toFloat()
            }
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(14)
            }
            elevation = dpToPx(1f).toFloat()
        }

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
        headerCard.addView(topRow)

        if (info != null) {
            val txtVersionTag = TextView(this).apply {
                text = "最新版本：v${info.latestVersionName} (Build ${info.latestVersionCode})"
                textSize = 12f
                setTextColor(colorBrand)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(4), 0, 0)
            }
            headerCard.addView(txtVersionTag)

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
            headerCard.addView(txtChangelog)
        } else {
            val txtDesc = TextView(this).apply {
                text = "在此管理下载的更新安装包与配套应用组件，支持断点续传与离线复用。"
                textSize = 12f
                setTextColor(colorTextSecondary)
                setPadding(0, dpToPx(4), 0, 0)
            }
            headerCard.addView(txtDesc)
        }
        scrollContent.addView(headerCard)

        // 2. 安装包列表标题
        val txtListTitle = TextView(this).apply {
            text = "配套安装包与组件列表"
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
                setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
                background = GradientDrawable().apply {
                    setColor(colorCard)
                    cornerRadius = dpToPx(12).toFloat()
                }
            }
            val txtEmpty = TextView(this).apply {
                text = "暂无待下载或已缓存的安装包，可点击右上角「刷新」检测"
                textSize = 13f
                setTextColor(colorTextSecondary)
                gravity = Gravity.CENTER
            }
            emptyCard.addView(txtEmpty)
            scrollContent.addView(emptyCard)
        }
    }

    private fun createPackageCard(taskId: String, title: String, sizeBytes: Long, description: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(colorCard)
                cornerRadius = dpToPx(10).toFloat()
            }
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(10)
            }
            elevation = dpToPx(1f).toFloat()
        }

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
        card.addView(titleRow)

        // 描述
        val txtDesc = TextView(this).apply {
            text = description
            textSize = 11f
            setTextColor(colorTextSecondary)
            setPadding(0, dpToPx(3), 0, dpToPx(6))
        }
        card.addView(txtDesc)

        // 进度条
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(4)).apply {
                bottomMargin = dpToPx(6)
            }
        }
        card.addView(progressBar)

        // 底部状态与操作按钮行
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val txtStatus = TextView(this).apply {
            text = "状态: 未下载"
            textSize = 12f
            setTextColor(colorTextSecondary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        actionsRow.addView(txtStatus)

        val btnOpenDir = Button(this).apply {
            text = "打开目录"
            textSize = 12f
            setTextColor(colorTextPrimary)
            val bg = GradientDrawable().apply {
                setColor(colorCardInner)
                setStroke(dpToPx(1), colorBorder)
                cornerRadius = dpToPx(6).toFloat()
            }
            background = bg
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

        val btnDelete = Button(this).apply {
            text = "删除"
            textSize = 12f
            setTextColor(Color.parseColor("#DC3545"))
            val bg = GradientDrawable().apply {
                setColor(if (isNightMode) Color.parseColor("#2A1C1C") else Color.parseColor("#FFF0F0"))
                cornerRadius = dpToPx(6).toFloat()
            }
            background = bg
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                rightMargin = dpToPx(6)
            }
            layoutParams = lp
            setOnClickListener { deleteDownload(taskId) }
        }
        actionsRow.addView(btnDelete)

        val btnAction = Button(this).apply {
            text = "下载"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)

            val btnBg = GradientDrawable().apply {
                setColor(colorBrand)
                cornerRadius = dpToPx(6).toFloat()
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(dpToPx(72), dpToPx(32))

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
        card.addView(actionsRow)

        packageViews[taskId] = PackageViewHolder(progressBar, txtStatus, btnAction, btnDelete, btnOpenDir)
        return card
    }

    private fun updateViewHolder(taskId: String, task: DownloadTask, errorMsg: String? = null) {
        val holder = packageViews[taskId] ?: return

        when (task.status) {
            DownloadTask.STATUS_PENDING -> {
                holder.progressBar.visibility = View.GONE
                holder.txtStatus.text = "状态: 未下载"
                holder.txtStatus.setTextColor(colorTextSecondary)
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
                holder.btnAction.text = "继续"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.GONE
                setButtonBgColor(holder.btnAction, colorBrand)
            }
            DownloadTask.STATUS_COMPLETED -> {
                holder.progressBar.visibility = View.GONE
                holder.txtStatus.text = "已就绪 (0流量复用)"
                holder.txtStatus.setTextColor(colorBrand)
                holder.btnAction.text = "安装"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.VISIBLE
                setButtonBgColor(holder.btnAction, colorBrand)
            }
            DownloadTask.STATUS_FAILED -> {
                holder.progressBar.visibility = View.GONE
                holder.txtStatus.text = "下载失败: ${errorMsg ?: "网络异常"}"
                holder.txtStatus.setTextColor(Color.parseColor("#DC3545"))
                holder.btnAction.text = "重试"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.GONE
                setButtonBgColor(holder.btnAction, Color.parseColor("#DC3545"))
            }
        }
    }

    private fun setButtonBgColor(button: Button, color: Int) {
        val bg = button.background as? GradientDrawable
        bg?.setColor(color)
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
        val btnAction: Button,
        val btnDelete: Button,
        val btnOpenDir: Button
    )
}
