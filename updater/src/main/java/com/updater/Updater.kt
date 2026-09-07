package com.updater

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Toast
import android.text.method.LinkMovementMethod
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdateInfo
import com.updater.model.UpdatePackage
import com.updater.model.UpdateSource
import com.updater.model.UpdateSourceType
import com.updater.ui.DownloadManagerActivity
import com.updater.ui.SourceSettingsDialog
import com.updater.utils.MarkdownUtils
import com.updater.utils.UpdaterLog
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class Updater private constructor(
    private val context: Context,
    private val appId: String,
    private val defaultSources: List<UpdateSource>
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val handler = Handler(Looper.getMainLooper())
    val configManager = UpdaterConfigManager(context)
    private var lastOpenCenterTime: Long = 0

    init {
        // 初始化并持久化默认预设更新源
        configManager.ensureDefaultSources(defaultSources)
    }

    companion object {
        private val isChecking = java.util.concurrent.atomic.AtomicBoolean(false)

        @Volatile
        var lastUpdateInfo: UpdateInfo? = null

        @Volatile
        private var defaultInstance: Updater? = null

        fun getInstance(context: Context): Updater {
            val existing = defaultInstance
            if (existing != null) return existing
            synchronized(this) {
                val existingSync = defaultInstance
                if (existingSync != null) return existingSync

                val config = UpdaterConfigManager(context)
                val builder = Builder(context)
                val sources = config.getSources()
                if (sources.isNotEmpty()) {
                    for (s in sources) {
                        if (s.type == UpdateSourceType.CLOUDFLARE_R2) {
                            builder.addCloudflareSource(s.name, s.url, s.id == config.selectedSourceId, s.downloadHost)
                        } else {
                            builder.addGitHubSource(s.name, s.url, s.id == config.selectedSourceId)
                        }
                    }
                } else {
                    builder.addCloudflareSource("Cloudflare 官方源", "https://cicha.de5.net", isDefault = true)
                    builder.addGitHubSource("GitHub 官方发布源", "https://github.com/LiYiCha/Sesame-TK", isDefault = false)
                }
                val created = builder.build()
                defaultInstance = created
                return created
            }
        }

        /**
         * 语义化版本比对：
         * 提取版本数字进行数值逐级比较
         * 返回 > 0 表示 v1 > v2，< 0 表示 v1 < v2，== 0 表示相等
         */
        fun compareSemanticVersions(v1: String, v2: String): Int {
            fun parseNumbers(v: String): List<Int> {
                val clean = v.trim().removePrefix("v").removePrefix("V")
                return clean.split(".", "-", "_").mapNotNull { part ->
                    part.takeWhile { it.isDigit() }.toIntOrNull()
                }
            }
            val list1 = parseNumbers(v1)
            val list2 = parseNumbers(v2)
            val maxLen = maxOf(list1.size, list2.size)
            for (i in 0 until maxLen) {
                val n1 = list1.getOrElse(i) { 0 }
                val n2 = list2.getOrElse(i) { 0 }
                if (n1 != n2) {
                    return n1.compareTo(n2)
                }
            }
            return 0
        }

        /**
         * 判断远程版本是否确实新于本地当前运行版本
         * 避免因仅比较虚构的 fake versionCode 造成已安装最新版仍死循环弹窗提示
         */
        fun isNewerVersion(
            remoteVersionName: String,
            remoteVersionCode: Int,
            localVersionName: String,
            localVersionCode: Long
        ): Boolean {
            val cmp = compareSemanticVersions(remoteVersionName, localVersionName)
            if (cmp > 0) return true
            if (cmp < 0) return false

            // 当主语义版本号一致时（如均为 0.4.6）：
            // 只有当两者均为合法且差值处于正常小范围步进的真实 versionCode 时，才支持构建号递增更新
            // 彻底杜绝 GitHub 虚构的 406 假代码误判大于本地系统 versionCode 31
            if (remoteVersionCode > 0 && localVersionCode > 0) {
                val diff = remoteVersionCode - localVersionCode
                if (diff in 1..20) {
                    return true
                }
            }
            return false
        }

        class Builder(private val context: Context) {
            private var appId: String = context.packageName
            private val sources = mutableListOf<UpdateSource>()
            private var defaultSourceId: String = ""

            fun setAppId(appId: String) = apply { this.appId = appId }

            /**
             * 添加或设置 Cloudflare Pages R2 更新源
             */
            fun setBaseHost(baseHost: String) = apply {
                addCloudflareSource("Cloudflare 官方源", baseHost, isDefault = true)
            }

            fun addCloudflareSource(name: String, baseHost: String, isDefault: Boolean = false, downloadHost: String? = null) = apply {
                val cleanUrl = baseHost.trim().trimEnd('/')
                val id = "cf_${cleanUrl.hashCode()}"
                val source = UpdateSource(
                    id = id,
                    name = name,
                    url = cleanUrl,
                    type = UpdateSourceType.CLOUDFLARE_R2,
                    downloadHost = downloadHost,
                    isPreset = true
                )
                sources.removeAll { it.id == id }
                sources.add(source)
                if (isDefault || defaultSourceId.isEmpty()) {
                    defaultSourceId = id
                }
            }

            /**
             * 添加 GitHub Releases 官方发布源（支持 github.com/owner/repo 或完整 API 地址）
             */
            fun addGitHubSource(name: String, repoOrUrl: String, isDefault: Boolean = false) = apply {
                val cleanUrl = repoOrUrl.trim().trimEnd('/')
                val id = "gh_${cleanUrl.hashCode()}"
                val source = UpdateSource(
                    id = id,
                    name = name,
                    url = cleanUrl,
                    type = UpdateSourceType.GITHUB_RELEASES,
                    isPreset = true
                )
                sources.removeAll { it.id == id }
                sources.add(source)
                if (isDefault || defaultSourceId.isEmpty()) {
                    defaultSourceId = id
                }
            }

            /**
             * 自定义下载加速域名（如 CDN 优选节点域名）
             */
            fun setDownloadHost(downloadHost: String) = apply {
                if (sources.isNotEmpty()) {
                    sources[0].downloadHost = downloadHost
                }
            }
            fun setCustomDomain(customDomain: String) = setDownloadHost(customDomain)

            fun build(): Updater {
                if (sources.isEmpty()) {
                    throw IllegalStateException("至少需要配置一个更新源（如调用 setBaseHost 或 addGitHubSource）")
                }
                val updater = Updater(context.applicationContext, appId, sources)
                if (defaultSourceId.isNotEmpty() && updater.configManager.selectedSourceId.isEmpty()) {
                    updater.configManager.selectedSourceId = defaultSourceId
                }
                return updater
            }
        }
    }

    /**
     * 判断传入的 UpdateInfo 是否确实新于本地当前版本
     */
    fun isNewerThanLocal(context: Context, updateInfo: UpdateInfo): Boolean {
        val localVersionName = getLocalVersionName(context)
        val localVersionCode = getLocalVersionCode(context)
        return isNewerVersion(
            remoteVersionName = updateInfo.latestVersionName,
            remoteVersionCode = updateInfo.latestVersionCode,
            localVersionName = localVersionName,
            localVersionCode = localVersionCode
        )
    }

    /**
     * 模式一：应用启动时自动检测更新
     * 仅在用户开启了「启动时自动检查更新」设置时触发，静默检测，有新版本才弹窗
     */
    fun checkUpdateOnStartup(activityContext: Context) {
        if (!configManager.isAutoCheckOnStartup) {
            // 默认关闭，用户未开启则直接跳过
            return
        }

        checkUpdate(
            onUpdateAvailable = { updateInfo ->
                if (isNewerThanLocal(activityContext, updateInfo)) {
                    showUpdateDialog(activityContext, updateInfo)
                }
            },
            onNoUpdate = {},
            onError = {}
        )
    }

    /**
     * 模式二：用户手动点击触发检测更新
     * 会给出明确的交互反馈（“正在检查...”、“当前已是最新版本”或更新弹窗）
     */
    fun checkUpdateManual(activityContext: Context) {
        try {
            Toast.makeText(activityContext.applicationContext, "正在检查更新...", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {}

        checkUpdate(
            onUpdateAvailable = { updateInfo ->
                if (isNewerThanLocal(activityContext, updateInfo)) {
                    showUpdateDialog(activityContext, updateInfo)
                } else {
                    try {
                        Toast.makeText(activityContext.applicationContext, "当前已是最新版本 (${updateInfo.latestVersionName})", Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {}
                }
            },
            onNoUpdate = {
                try {
                    Toast.makeText(activityContext.applicationContext, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {}
            },
            onError = { error ->
                try {
                    Toast.makeText(activityContext.applicationContext, "检查更新失败: $error", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {}
            }
        )
    }

    /**
     * 兼容原有的无感知检查更新并弹窗方法
     */
    fun checkAndShowUpdateDialog(activityContext: Context) {
        checkUpdate(
            onUpdateAvailable = { updateInfo ->
                if (isNewerThanLocal(activityContext, updateInfo)) {
                    showUpdateDialog(activityContext, updateInfo)
                }
            },
            onNoUpdate = {},
            onError = { error ->
                handler.post {
                    try {
                        Toast.makeText(activityContext.applicationContext, "检查更新失败: $error", Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {}
                }
            }
        )
    }

    /**
     * 打开更新源与自动更新设置弹窗
     */
    fun openSourceSettingsDialog(activityContext: Context, onSourceChanged: (() -> Unit)? = null) {
        SourceSettingsDialog.show(activityContext, onSourceChanged)
    }

    /**
     * 核心检查更新方法（单线程互斥，避免并发重复点击）
     */
    fun checkUpdate(
        onUpdateAvailable: (UpdateInfo) -> Unit,
        onNoUpdate: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isChecking.compareAndSet(false, true)) {
            handler.post { onError("正在检查更新中，请勿重复点击") }
            return
        }

        val wrappedOnAvailable: (UpdateInfo) -> Unit = {
            isChecking.set(false)
            onUpdateAvailable(it)
        }
        val wrappedOnNoUpdate: () -> Unit = {
            isChecking.set(false)
            onNoUpdate()
        }
        val wrappedOnError: (String) -> Unit = {
            isChecking.set(false)
            onError(it)
        }

        val source = configManager.getSelectedSource()
        if (source == null) {
            handler.post { wrappedOnError("未配置有效更新源") }
            return
        }

        when (source.type) {
            UpdateSourceType.CLOUDFLARE_R2 -> checkCloudflareR2(source, wrappedOnAvailable, wrappedOnNoUpdate, wrappedOnError)
            UpdateSourceType.GITHUB_RELEASES -> checkGitHubRelease(source, wrappedOnAvailable, wrappedOnNoUpdate, wrappedOnError)
        }
    }

    /**
     * Cloudflare Pages + R2 更新检测
     */
    private fun checkCloudflareR2(
        source: UpdateSource,
        onUpdateAvailable: (UpdateInfo) -> Unit,
        onNoUpdate: () -> Unit,
        onError: (String) -> Unit
    ) {
        val baseHost = source.url.trimEnd('/')
        val url = "$baseHost/api/update?app_id=$appId"

        val requestBuilder = Request.Builder().url(url).get()
        val token = configManager.adminToken
        if (token.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { onError(e.message ?: "网络连接失败") }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    val code = response.code
                    val msg = if (code == 401) "HTTP 401 (更新源未授权或需鉴权)" else "HTTP $code"
                    handler.post { onError(msg) }
                    return
                }

                try {
                    val json = JSONObject(bodyStr)
                    
                    if (json.has("hasUpdate") && !json.optBoolean("hasUpdate", true)) {
                        handler.post { onNoUpdate() }
                        return
                    }

                    if (json.has("error")) {
                        val errMsg = json.optString("error")
                        handler.post { onError(errMsg) }
                        return
                    }

                    val appIdVal = json.optString("appId")
                    val appName = json.optString("appName")
                    val latestVersionCode = json.optInt("latestVersionCode", 0)
                    val latestVersionName = json.optString("latestVersionName")
                    val updateLog = json.optString("updateLog")
                    val isForceUpdate = json.optBoolean("isForceUpdate")
                    val lastUpdated = json.optLong("lastUpdated")

                    if (appIdVal.isEmpty() || (latestVersionCode <= 0 && latestVersionName.isEmpty())) {
                        handler.post { onNoUpdate() }
                        return
                    }
                    
                    val packagesList = ArrayList<UpdatePackage>()
                    val packagesArray = json.optJSONArray("packages")
                    if (packagesArray != null) {
                        for (i in 0 until packagesArray.length()) {
                            val pkgJson = packagesArray.getJSONObject(i)
                            packagesList.add(
                                UpdatePackage(
                                    packageId = pkgJson.optString("packageId"),
                                    packageName = pkgJson.optString("packageName"),
                                    versionName = pkgJson.optString("versionName"),
                                    versionCode = pkgJson.optInt("versionCode"),
                                    description = pkgJson.optString("description"),
                                    downloadUrl = pkgJson.optString("downloadUrl"),
                                    apkSize = pkgJson.optLong("apkSize"),
                                    apkMd5 = pkgJson.optString("apkMd5")
                                )
                            )
                        }
                    }

                    // 兼容传统单包后端格式 (根节点直接声明 downloadUrl)
                    if (packagesList.isEmpty()) {
                        val singleUrl = json.optString("downloadUrl")
                        if (singleUrl.isNotEmpty()) {
                            packagesList.add(
                                UpdatePackage(
                                    packageId = "main",
                                    packageName = if (appName.isNotEmpty()) "$appName 安装包" else "标准安装包",
                                    versionName = latestVersionName,
                                    versionCode = latestVersionCode,
                                    description = "标准版安装包",
                                    downloadUrl = singleUrl,
                                    apkSize = json.optLong("apkSize", 0L),
                                    apkMd5 = json.optString("apkMd5", "")
                                )
                            )
                        }
                    }

                    val updateInfo = UpdateInfo(
                        appId = appIdVal,
                        appName = appName,
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        updateLog = updateLog,
                        isForceUpdate = isForceUpdate,
                        packages = packagesList,
                        lastUpdated = lastUpdated
                    )

                    lastUpdateInfo = updateInfo
                    configManager.saveCachedUpdateInfo(updateInfo)
                    handler.post { onUpdateAvailable(updateInfo) }
                } catch (e: Exception) {
                    handler.post { onError("数据解析错误: ${e.message}") }
                }
            }
        })
    }

    /**
     * GitHub Releases 最新发布版本检测
     */
    private fun checkGitHubRelease(
        source: UpdateSource,
        onUpdateAvailable: (UpdateInfo) -> Unit,
        onNoUpdate: () -> Unit,
        onError: (String) -> Unit
    ) {
        var apiUrl = source.url.trim().trimEnd('/')
        if (apiUrl.startsWith("https://github.com/")) {
            val repoPath = apiUrl.removePrefix("https://github.com/")
            val parts = repoPath.split("/")
            if (parts.size >= 2) {
                apiUrl = "https://api.github.com/repos/${parts[0]}/${parts[1]}/releases/latest"
            }
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { onError(e.message ?: "GitHub 连接失败") }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    handler.post { onError("GitHub HTTP ${response.code}") }
                    return
                }

                try {
                    val json = JSONObject(bodyStr)
                    val tagName = json.optString("tag_name")
                    val releaseName = json.optString("name").ifEmpty { tagName }
                    val body = json.optString("body")
                    val isDraft = json.optBoolean("draft", false)

                    if (isDraft || tagName.isEmpty()) {
                        handler.post { onNoUpdate() }
                        return
                    }

                    val cleanVersionName = tagName.removePrefix("v").removePrefix("V")
                    val latestVersionCode = parseVersionCode(cleanVersionName, body)

                    val packagesList = ArrayList<UpdatePackage>()
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                packagesList.add(
                                    UpdatePackage(
                                        packageId = "gh_asset_${asset.optLong("id", i.toLong())}",
                                        packageName = name,
                                        versionName = cleanVersionName,
                                        versionCode = latestVersionCode,
                                        description = "GitHub Release 发布文件: $name",
                                        downloadUrl = asset.optString("browser_download_url"),
                                        apkSize = asset.optLong("size", 0L),
                                        apkMd5 = ""
                                    )
                                )
                            }
                        }
                    }

                    if (packagesList.isEmpty()) {
                        handler.post { onNoUpdate() }
                        return
                    }

                    val updateInfo = UpdateInfo(
                        appId = appId,
                        appName = releaseName,
                        latestVersionCode = latestVersionCode,
                        latestVersionName = cleanVersionName,
                        updateLog = body,
                        isForceUpdate = false,
                        packages = packagesList,
                        lastUpdated = System.currentTimeMillis()
                    )

                    lastUpdateInfo = updateInfo
                    configManager.saveCachedUpdateInfo(updateInfo)
                    handler.post { onUpdateAvailable(updateInfo) }
                } catch (e: Exception) {
                    handler.post { onError("GitHub 数据解析失败: ${e.message}") }
                }
            }
        })
    }

    /**
     * 智能从版本号或更新日志解析 VersionCode
     */
    private fun parseVersionCode(versionName: String, updateLog: String): Int {
        // 1. 优先从日志中提取显式标记，如 versionCode: 30 或 versionCode=30
        val regex = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(updateLog)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() ?: 1
        }

        // 2. 从语义化版本号转换 (如 0.4.3 -> 403, 1.2.0 -> 10200)
        try {
            val parts = versionName.split(".").mapNotNull { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull()
            }
            if (parts.size >= 3) {
                return parts[0] * 10000 + parts[1] * 100 + parts[2]
            } else if (parts.size == 2) {
                return parts[0] * 100 + parts[1]
            } else if (parts.size == 1) {
                return parts[0]
            }
        } catch (_: Exception) {}

        return 1
    }

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun showUpdateDialog(context: Context, updateInfo: UpdateInfo) {
        val activity = findActivity(context)
        if (activity != null) {
            if (activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)) {
                return
            }
        }
        val targetContext = activity ?: context
        try {
            val updateMessage = if (updateInfo.updateLog.isNotBlank()) {
                MarkdownUtils.renderMarkdown(targetContext, updateInfo.updateLog)
            } else {
                "检测到新版本发布。"
            }

            val builder = MaterialAlertDialogBuilder(targetContext).apply {
                setTitle("发现新版本 v${updateInfo.latestVersionName}")
                setMessage(updateMessage)
                setCancelable(!updateInfo.isForceUpdate)

                setPositiveButton("查看") { dialog, _ ->
                    dialog.dismiss()
                    openDownloadCenter(targetContext, updateInfo)
                }

                if (!updateInfo.isForceUpdate) {
                    setNegativeButton("稍后") { dialog, _ ->
                        dialog.dismiss()
                    }
                }
            }

            val dialog = builder.create()
            dialog.setOnShowListener {
                try {
                    val density = targetContext.resources.displayMetrics.density
                    fun dp(v: Int) = (v * density + 0.5f).toInt()

                    // 支持 Markdown 超链接点击跳转
                    val messageView = dialog.findViewById<TextView>(android.R.id.message)
                    messageView?.movementMethod = LinkMovementMethod.getInstance()

                    val palette = com.updater.ui.ThemeUtils.M3Palette(targetContext)

                    // 1. 立即查看按钮：实心主题主色底、文字对比度高、加粗圆角
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(palette.onPrimary)
                        val bg = GradientDrawable().apply {
                            setColor(palette.primary)
                            cornerRadius = dp(20).toFloat()
                        }
                        background = bg
                        setPadding(dp(18), dp(8), dp(18), dp(8))
                        val lp = layoutParams as? ViewGroup.MarginLayoutParams
                        lp?.leftMargin = dp(8)
                        layoutParams = lp
                    }

                    // 2. 稍后再说按钮：清晰线框轮廓、表面次色背景、与主题统一
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(palette.onSurface)
                        val bg = GradientDrawable().apply {
                            setColor(palette.surfaceVariant)
                            setStroke(dp(1), palette.outline)
                            cornerRadius = dp(20).toFloat()
                        }
                        background = bg
                        setPadding(dp(16), dp(8), dp(16), dp(8))
                    }
                } catch (_: Throwable) {}
            }

            dialog.show()

            if (updateInfo.isForceUpdate) {
                dialog.setOnCancelListener {
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        } catch (e: Throwable) {
            UpdaterLog.e("显示更新对话框异常: ${e.message}", e)
            // 兜底策略：如果因为 Context 或主题原因无法弹出对话框，直接打开下载管理中心，保证信息正常呈现
            openDownloadCenter(targetContext, updateInfo)
        }
    }

    fun openDownloadCenter(context: Context, updateInfo: UpdateInfo? = null) {
        val now = System.currentTimeMillis()
        if (now - lastOpenCenterTime < 800) {
            return
        }
        lastOpenCenterTime = now

        val targetInfo = updateInfo ?: lastUpdateInfo ?: configManager.getCachedUpdateInfo()
        val currentSource = configManager.getSelectedSource()
        val intent = Intent(context, DownloadManagerActivity::class.java).apply {
            if (targetInfo != null) {
                putExtra("update_info", targetInfo)
            }
            putExtra("base_host", currentSource?.url ?: "")
            if (!currentSource?.downloadHost.isNullOrEmpty()) {
                putExtra("download_host", currentSource?.downloadHost)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun getLocalVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getLocalVersionCode(context: Context): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0
        }
    }
}
