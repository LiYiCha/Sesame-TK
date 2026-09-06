package com.updater.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.updater.Updater
import com.updater.db.DownloadDatabaseHelper
import com.updater.db.DownloadTask
import com.updater.download.ForegroundDownloadService
import com.updater.model.UpdateInfo
import com.updater.model.UpdatePackage
import com.updater.utils.ApkCleanupManager
import com.updater.utils.ApkInstaller
import com.updater.utils.UpdatePathManager
import com.updater.utils.UpdaterLog
import java.io.File

class DownloadManagerActivity : Activity() {

    private lateinit var dbHelper: DownloadDatabaseHelper
    private var updateInfo: UpdateInfo? = null
    
    private val packageViews = HashMap<String, PackageViewHolder>()
    private val tasks = HashMap<String, DownloadTask>()

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

        var info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("update_info", UpdateInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("update_info") as? UpdateInfo
        }

        if (info == null) {
            info = Updater.lastUpdateInfo
        }
        updateInfo = info

        val rootView = createRootLayout()
        setContentView(rootView)
        
        // 启动时在后台静默对账清理已安装包，保留未安装包供复用
        Thread {
            ApkCleanupManager.checkAndCleanOnStartup(this@DownloadManagerActivity)
        }.start()

        initPackageTasks()
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
        // 从外部安装器返回时自动对账清理已安装完成的 APK
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
            // 没有在线 updateInfo 时，从本地数据库加载所有已存在的下载任务
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

        // 1. 防重复点击：若已经在下载中，直接忽略
        if (task.status == DownloadTask.STATUS_DOWNLOADING) {
            return
        }

        // 2. 单例限制：禁止并发下载，检查是否有其他包正在下载
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
        val baseHost = intent.getStringExtra("base_host") ?: "https://yourdomain.com"
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

    // --- Programmatic Layout Builder ---
    
    private fun createRootLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F6F9"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Title Bar
        val titleBar = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56)).apply {
                elevation = dpToPx(4).toFloat()
            }
            setBackgroundColor(Color.WHITE)
        }
        
        val btnBack = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val lp = RelativeLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                addRule(RelativeLayout.CENTER_VERTICAL)
                leftMargin = dpToPx(8)
            }
            layoutParams = lp
            setOnClickListener { finish() }
        }
        titleBar.addView(btnBack)

        val btnSettings = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val lp = RelativeLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
                rightMargin = dpToPx(8)
            }
            layoutParams = lp
            setOnClickListener {
                SourceSettingsDialog.show(this@DownloadManagerActivity)
            }
        }
        titleBar.addView(btnSettings)

        val txtTitle = TextView(this).apply {
            text = "系统更新与配套应用"
            textSize = 18f
            setTextColor(Color.parseColor("#212529"))
            typeface = Typeface.DEFAULT_BOLD
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            layoutParams = lp
        }
        titleBar.addView(txtTitle)
        root.addView(titleBar)

        // Scroll Container
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        // 1. Version Card / Header Card
        val cardBackground = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dpToPx(12).toFloat()
        }
        
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(20)
            }
            elevation = dpToPx(2).toFloat()
        }

        val info = updateInfo
        if (info != null) {
            val txtAppName = TextView(this).apply {
                text = info.appName
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#333333"))
            }
            headerCard.addView(txtAppName)

            val txtVersion = TextView(this).apply {
                text = "最新版本: v${info.latestVersionName} (Build ${info.latestVersionCode})"
                textSize = 14f
                setTextColor(Color.parseColor("#667eea"))
                setPadding(0, dpToPx(4), 0, 0)
            }
            headerCard.addView(txtVersion)

            val divider = View(this).apply {
                setBackgroundColor(Color.parseColor("#E9ECEF"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                    topMargin = dpToPx(12)
                    bottomMargin = dpToPx(12)
                }
            }
            headerCard.addView(divider)

            val txtChangelogTitle = TextView(this).apply {
                text = "更新内容:"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#495057"))
            }
            headerCard.addView(txtChangelogTitle)

            val txtChangelog = TextView(this).apply {
                text = info.updateLog.ifEmpty { "优化了用户体验和细节。" }
                textSize = 13f
                setTextColor(Color.parseColor("#6C757D"))
                setPadding(0, dpToPx(6), 0, 0)
            }
            headerCard.addView(txtChangelog)
        } else {
            val txtHeaderTitle = TextView(this).apply {
                text = "安装包与配套文件下载中心"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#333333"))
            }
            headerCard.addView(txtHeaderTitle)

            val txtHeaderDesc = TextView(this).apply {
                text = "在此管理本地下载的更新安装包与配套应用组件。"
                textSize = 13f
                setTextColor(Color.parseColor("#6C757D"))
                setPadding(0, dpToPx(6), 0, 0)
            }
            headerCard.addView(txtHeaderDesc)
        }
        scrollContent.addView(headerCard)

        // 2. Packages Title
        val txtPackagesTitle = TextView(this).apply {
            text = if (info != null) "配套安装包列表" else "本地安装包任务列表"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#495057"))
            setPadding(dpToPx(4), 0, 0, dpToPx(10))
        }
        scrollContent.addView(txtPackagesTitle)

        // 3. Packages Cards List
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
            // 空状态提示
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(20), dpToPx(36), dpToPx(20), dpToPx(36))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dpToPx(12).toFloat()
                }
            }
            val txtEmpty = TextView(this).apply {
                text = "暂无待管理或已下载的安装包"
                textSize = 14f
                setTextColor(Color.parseColor("#868E96"))
                gravity = Gravity.CENTER
            }
            emptyCard.addView(txtEmpty)
            scrollContent.addView(emptyCard)
        }

        scrollView.addView(scrollContent)
        root.addView(scrollView)
        return root
    }

    private fun createPackageCard(taskId: String, title: String, sizeBytes: Long, description: String): View {
        val cardBackground = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dpToPx(10).toFloat()
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(12)
            }
            elevation = dpToPx(1.5f).toFloat()
        }

        // Title Row
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val txtPkgName = TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#343A40"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        titleRow.addView(txtPkgName)

        val txtSize = TextView(this).apply {
            text = if (sizeBytes > 0) formatSize(sizeBytes) else ""
            textSize = 13f
            setTextColor(Color.parseColor("#6C757D"))
        }
        titleRow.addView(txtSize)
        card.addView(titleRow)

        // Description
        val txtDesc = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#868E96"))
            setPadding(0, dpToPx(4), 0, dpToPx(10))
        }
        card.addView(txtDesc)

        // Progress Bar
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(4)).apply {
                bottomMargin = dpToPx(8)
            }
        }
        card.addView(progressBar)

        // Bottom Actions Row
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val txtStatus = TextView(this).apply {
            text = "状态: 未下载"
            textSize = 12f
            setTextColor(Color.parseColor("#6C757D"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        actionsRow.addView(txtStatus)

        val btnOpenDir = Button(this).apply {
            text = "打开目录"
            textSize = 12f
            setTextColor(Color.parseColor("#495057"))
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36)).apply {
                rightMargin = dpToPx(8)
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
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36)).apply {
                rightMargin = dpToPx(8)
            }
            layoutParams = lp
            setOnClickListener { deleteDownload(taskId) }
        }
        actionsRow.addView(btnDelete)

        val btnAction = Button(this).apply {
            text = "下载"
            textSize = 13f
            setTextColor(Color.WHITE)
            
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#667eea"))
                cornerRadius = dpToPx(6).toFloat()
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(dpToPx(80), dpToPx(36))
            
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
                holder.txtStatus.setTextColor(Color.parseColor("#6C757D"))
                holder.btnAction.text = "下载"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.GONE
                holder.btnOpenDir.visibility = View.GONE
                setButtonColor(holder.btnAction, "#667eea")
            }
            DownloadTask.STATUS_DOWNLOADING -> {
                holder.progressBar.visibility = View.VISIBLE
                val progressPercent = if (task.totalBytes > 0) ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()) * 100).toInt() else 0
                holder.progressBar.progress = progressPercent
                holder.txtStatus.text = "下载中: $progressPercent%"
                holder.txtStatus.setTextColor(Color.parseColor("#667eea"))
                holder.btnAction.text = "暂停"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.GONE
                holder.btnOpenDir.visibility = View.GONE
                setButtonColor(holder.btnAction, "#E0A800")
            }
            DownloadTask.STATUS_PAUSED -> {
                holder.progressBar.visibility = View.VISIBLE
                val progressPercent = if (task.totalBytes > 0) ((task.downloadedBytes.toDouble() / task.totalBytes.toDouble()) * 100).toInt() else 0
                holder.progressBar.progress = progressPercent
                holder.txtStatus.text = "已暂停 ($progressPercent%)"
                holder.txtStatus.setTextColor(Color.parseColor("#6C757D"))
                holder.btnAction.text = "继续"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.GONE
                setButtonColor(holder.btnAction, "#667eea")
            }
            DownloadTask.STATUS_COMPLETED -> {
                holder.progressBar.visibility = View.GONE
                holder.txtStatus.text = "下载完成"
                holder.txtStatus.setTextColor(Color.parseColor("#28A745"))
                holder.btnAction.text = "安装"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.VISIBLE
                setButtonColor(holder.btnAction, "#28A745")
            }
            DownloadTask.STATUS_FAILED -> {
                holder.progressBar.visibility = View.GONE
                holder.txtStatus.text = "下载失败: ${errorMsg ?: "未知错误"}"
                holder.txtStatus.setTextColor(Color.parseColor("#DC3545"))
                holder.btnAction.text = "重试"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnOpenDir.visibility = View.GONE
                setButtonColor(holder.btnAction, "#DC3545")
            }
        }
    }

    private fun setButtonColor(button: Button, colorHex: String) {
        val bg = button.background as? GradientDrawable
        bg?.setColor(Color.parseColor(colorHex))
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
