package fansirsqi.xposed.sesame.ui.extra.activity

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.ui.update.UpdateConfig
import fansirsqi.xposed.sesame.ui.update.UpdateManager
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.PermissionUtil
import java.io.File
import java.util.Calendar

class HelpActivity : BaseActivity() {

    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        try {
            // 初始化更新管理器
            updateManager = UpdateManager(
                context = this,
                config = UpdateConfig.DEFAULT,
                coroutineScope = lifecycleScope
            )

            initializeViews()
            setupExpandableSections()
            setupUpdateChecker()
        } catch (e: Exception) {
            Log.printStackTrace(e)
            Toast.makeText(this, "加载帮助信息失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onContentChanged() {
        // 手动处理 Toolbar
        try {
            val toolbar = findViewById<Toolbar>(R.id.x_toolbar)
            if (toolbar != null) {
                setSupportActionBar(toolbar)
                toolbar.setContentInsetsAbsolute(0, 0)
                toolbar.title = baseTitle
                toolbar.subtitle = baseSubtitle
                // 移除返回箭头显示
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                supportActionBar?.title = "帮助"
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initializeViews() {
        val configPathText = findViewById<TextView>(R.id.config_path_text)
        val logPathText = findViewById<TextView>(R.id.log_path_text)
        val logDetailsText = findViewById<TextView>(R.id.log_details_text)
        val logExpireText = findViewById<TextView>(R.id.log_expire_text)

        // 安全地设置文本内容
        configPathText.text = safeGetPath(Files.CONFIG_DIR, "配置目录未初始化")
        logPathText.text = safeGetPath(Files.LOG_DIR, "日志目录未初始化")
        logDetailsText.text = buildLogPathsInfo()
        logExpireText.text = """
            日志最大保留天数: 7天
            单个日志文件最大大小: 50MB
            总日志容量上限: 100MB
            日志文件会自动按日期和大小进行轮转
        """.trimIndent()
    }

    @SuppressLint("SetTextI18n")
    private fun setupExpandableSections() {
        // 基础信息部分
        findViewById<View>(R.id.config_header).setOnClickListener {
            toggleSection(R.id.config_content, R.id.config_arrow)
        }

        findViewById<View>(R.id.log_header).setOnClickListener {
            toggleSection(R.id.log_content, R.id.log_arrow)
        }

        findViewById<View>(R.id.log_details_header).setOnClickListener {
            toggleSection(R.id.log_details_content, R.id.log_details_arrow)
        }

        findViewById<View>(R.id.log_expire_header).setOnClickListener {
            toggleSection(R.id.log_expire_content, R.id.log_expire_arrow)
        }

        // 存储空间信息
        findViewById<View>(R.id.storage_header).setOnClickListener {
            toggleSection(R.id.storage_content, R.id.storage_arrow)
        }
        findViewById<TextView>(R.id.storage_info_text).text = getStorageInfo()

        findViewById<View>(R.id.btn_clear_log_bak).setOnClickListener {
            showConfirmClearLogBak()
        }

        // 权限状态
        findViewById<View>(R.id.permission_header).setOnClickListener {
            toggleSection(R.id.permission_content, R.id.permission_arrow)
        }
        findViewById<TextView>(R.id.permission_info_text).text = getPermissionInfo()

        // 系统环境信息
        findViewById<View>(R.id.system_header).setOnClickListener {
            toggleSection(R.id.system_content, R.id.system_arrow)
        }
        findViewById<TextView>(R.id.system_info_text).text = getSystemInfo()

        // 常见问题解答
        findViewById<View>(R.id.faq_header).setOnClickListener {
            toggleSection(R.id.faq_content, R.id.faq_arrow)
        }
        findViewById<TextView>(R.id.faq_info_text).text = getFAQInfo()
    }


    private fun toggleSection(contentId: Int, arrowId: Int) {
        val content = findViewById<LinearLayout>(contentId)
        val arrow = findViewById<TextView>(arrowId)

        if (content.visibility == View.GONE) {
            content.visibility = View.VISIBLE
            arrow.text = "▲"
        } else {
            content.visibility = View.GONE
            arrow.text = "▼"
        }
    }

    private fun getStorageInfo(): String {
        return try {
            val configDir = Files.CONFIG_DIR
            val logDir = Files.LOG_DIR
            val bakDir = if (logDir != null) File(logDir, "bak") else null

            val configSize = getDirectorySize(configDir)
            val logSize = getDirectorySize(logDir)
            val bakSize = getDirectorySize(bakDir)

            """
        配置目录: ${configDir?.absolutePath ?: "未初始化"}
        配置目录大小: ${formatFileSize(configSize)}
        
        日志目录: ${logDir?.absolutePath ?: "未初始化"}
        日志目录大小: ${formatFileSize(logSize)}
        
        备份日志目录: ${bakDir?.absolutePath ?: "未初始化"}
        备份日志目录大小: ${formatFileSize(bakSize)}
        
        总占用空间: ${formatFileSize(configSize + logSize + bakSize)}
        """.trimIndent()
        } catch (e: Exception) {
            "获取存储信息失败: ${e.message}"
        }
    }


    private fun getDirectorySize(dir: File?): Long {
        return try {
            dir?.walk()
                ?.filter { it.isFile }
                ?.sumOf { it.length() }
                ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${String.format("%.2f", size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${String.format("%.2f", size / (1024.0 * 1024.0))} MB"
            else -> "${String.format("%.2f", size / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    private fun getPermissionInfo(): String {
        return try {
            val hasFilePermission = PermissionUtil.checkFilePermissions(this)
            val storageState = Environment.getExternalStorageState()

            """
            文件读写权限: ${if (hasFilePermission) "已获取" else "未获取"}
            外部存储状态: $storageState
            应用存储目录: ${filesDir?.absolutePath}
            外部存储目录: ${getExternalFilesDir(null)?.absolutePath ?: "不可用"}
            """.trimIndent()
        } catch (e: Exception) {
            "获取权限信息失败: ${e.message}"
        }
    }

    private fun getSystemInfo(): String {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory

            """
            Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            设备型号: ${Build.MODEL}
            设备制造商: ${Build.MANUFACTURER}
            设备品牌: ${Build.BRAND}
            
            JVM最大内存: ${formatFileSize(maxMemory)}
            JVM已分配内存: ${formatFileSize(totalMemory)}
            JVM已使用内存: ${formatFileSize(usedMemory)}
            JVM可用内存: ${formatFileSize(maxMemory - usedMemory)}
            """.trimIndent()
        } catch (e: Exception) {
            "获取系统信息失败: ${e.message}"
        }
    }

    private fun getFAQInfo(): String {
        return """
            Q: 权限获取失败怎么办？
            A: 请在系统设置中手动授予应用存储权限，或尝试重启应用后重新授权。

            Q: 日志文件过大怎么办？
            A: 系统会自动清理7天前的日志，也可手动清理不需要的日志文件。

            Q: 配置文件损坏如何恢复？
            A: 可以尝试删除配置目录下的config_v2.json文件，重新启动应用会生成默认配置。

            Q: 模块不生效怎么办？
            A: 请确认Xposed框架已正确激活模块，并重启支付宝应用。

            Q: 如何查看详细运行日志？
            A: 在主界面可以通过菜单查看各类日志文件，或使用文件管理器访问日志目录。
            
            Q: 如何备份配置文件？
            A: 可以复制配置目录下的config_v2.json文件到安全位置进行备份。
            """.trimIndent()
    }

    private fun safeGetPath(file: File?, defaultText: String): String {
        return try {
            file?.absolutePath ?: defaultText
        } catch (e: Exception) {
            defaultText
        }
    }

    private fun buildLogPathsInfo(): String {
        return try {
            val sb = StringBuilder()
            val logDirPath = safeGetPath(Files.LOG_DIR, "日志目录未初始化")

            listOf(
                "runtime" to "运行日志",
                "system" to "系统日志",
                "record" to "记录日志",
                "debug" to "调试日志",
                "forest" to "森林日志",
                "farm" to "农场日志",
                "other" to "其他日志",
                "error" to "错误日志",
                "capture" to "抓包日志"
            ).forEach { (logName, description) ->
                sb.append("$description: $logDirPath/$logName.log\n")
            }
            sb.toString()
        } catch (e: Exception) {
            "无法获取日志路径信息: ${e.message}"
        }
    }

    private fun showConfirmClearLogBak() {
        try {
            AlertDialog.Builder(this)
                .setTitle("确认清理")
                .setMessage("将清除备份日志（不清除今天），是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续") { _, _ ->
                    showSecondConfirmClearLogBak()
                }
                .show()
        } catch (e: Exception) {
            Log.printStackTrace(e)
            Toast.makeText(this, "无法显示确认窗口: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSecondConfirmClearLogBak() {
        try {
            AlertDialog.Builder(this)
                .setTitle("再次确认")
                .setMessage("此操作不可撤销，将清除备份目录中除今天外的日志文件，确定继续吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除") { _, _ ->
                    val result = clearBackupLogs()
                    Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                    // 清理后刷新显示
                    findViewById<TextView>(R.id.storage_info_text).text = getStorageInfo()
                }
                .show()
        } catch (e: Exception) {
            Log.printStackTrace(e)
            Toast.makeText(this, "无法显示确认窗口: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearBackupLogs(): String {
        return try {
            // 验证权限和目录
            val bakDir = validateBackupDirectory() ?: return "验证失败"

            // 计算今日时间范围
            val todayRange = calculateTodayTimeRange()

            // 删除旧备份文件
            val result = deleteOldBackupFiles(bakDir, todayRange)

            "已清理: ${result.deleted} 个，失败: ${result.failed} 个"
        } catch (e: Exception) {
            Log.printStackTrace(e)
            "清理失败: ${e.message}"
        }
    }

    /**
     * 验证备份目录是否存在且可访问
     */
    private fun validateBackupDirectory(): File? {
        if (!PermissionUtil.checkFilePermissions(this)) {
            Toast.makeText(this, "缺少存储权限", Toast.LENGTH_SHORT).show()
            return null
        }

        val logDir = Files.LOG_DIR
        if (logDir == null) {
            Toast.makeText(this, "日志目录未初始化", Toast.LENGTH_SHORT).show()
            return null
        }

        val bakDir = File(logDir, "bak")
        if (!bakDir.exists() || !bakDir.isDirectory) {
            Toast.makeText(this, "未找到备份日志目录", Toast.LENGTH_SHORT).show()
            return null
        }

        return bakDir
    }

    /**
     * 计算今日的时间范围
     */
    private fun calculateTodayTimeRange(): TodayTimeRange {
        val cal = Calendar.getInstance()
        val todayStr = String.format(
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis
        val endOfToday = startOfToday + 24L * 60 * 60 * 1000 - 1

        return TodayTimeRange(startOfToday, endOfToday, todayStr)
    }

    /**
     * 删除旧备份文件（保留今日文件）
     */
    private fun deleteOldBackupFiles(bakDir: File, todayRange: TodayTimeRange): DeleteResult {
        var deleted = 0
        var failed = 0

        fun isTodayFile(file: File): Boolean {
            val lastModified = file.lastModified()
            val byTime = lastModified in todayRange.startTime..todayRange.endTime
            val byName = file.name.contains(todayRange.dateString)
            return byTime || byName
        }

        fun deleteRecursively(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
                // 删除空目录（非今日目录）
                if (file.listFiles()?.isEmpty() == true && !isTodayFile(file)) {
                    file.delete()
                }
            } else {
                // 删除非今日文件
                if (!isTodayFile(file)) {
                    if (file.delete()) {
                        deleted++
                    } else {
                        failed++
                    }
                }
            }
        }

        bakDir.listFiles()?.forEach { deleteRecursively(it) }

        return DeleteResult(deleted, failed)
    }

    /**
     * 今日时间范围数据类
     */
    private data class TodayTimeRange(
        val startTime: Long,
        val endTime: Long,
        val dateString: String
    )

    /**
     * 删除结果数据类
     */
    private data class DeleteResult(
        val deleted: Int,
        val failed: Int
    )

    /**
     * 设置更新检查功能
     */
    private fun setupUpdateChecker() {
        // 在系统信息卡片后添加更新检查按钮
        try {
            val systemCard = findViewById<View>(R.id.system_header)?.parent?.parent as? androidx.cardview.widget.CardView
            if (systemCard != null) {
                val parentLayout = systemCard.parent as? LinearLayout
                val index = parentLayout?.indexOfChild(systemCard) ?: -1

                if (parentLayout != null && index >= 0) {
                    // 创建更新检查卡片
                    val updateCard = createUpdateCheckCard()
                    parentLayout.addView(updateCard, index + 1)
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
            Toast.makeText(this, "添加更新检查功能失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 创建更新检查卡片
     */
    private fun createUpdateCheckCard(): View {
        val cardView = androidx.cardview.widget.CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 48
            }
            radius = 8f
            cardElevation = 4f
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // 标题
        val titleText = TextView(this).apply {
            text = "检查更新"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.colorPrimary, theme))
        }

        // 说明文本
        val descText = TextView(this).apply {
            text = "点击按钮检查应用更新"
            textSize = 14f
            setPadding(0, 24, 0, 48)
        }

        // 更新按钮
        val updateButton = Button(this).apply {
            text = "检查更新"
            setOnClickListener {
                checkForUpdates()
            }
        }

        contentLayout.addView(titleText)
        contentLayout.addView(descText)
        contentLayout.addView(updateButton)
        cardView.addView(contentLayout)

        return cardView
    }

    /**
     * 检查更新
     */
    private fun checkForUpdates() {
        try {
            Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show()
            updateManager.checkForUpdates()
        } catch (e: Exception) {
            Log.printStackTrace(e)
            Toast.makeText(this, "检查更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
