package com.updater

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdateInfo
import com.updater.model.UpdatePackage
import com.updater.model.UpdateSource
import com.updater.model.UpdateSourceType
import com.updater.ui.DownloadManagerActivity
import com.updater.ui.SourceSettingsDialog
import com.updater.utils.UpdaterLog
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class Updater private constructor(
    private val context: Context,
    private val appId: String,
    private val defaultSources: List<UpdateSource>
) {

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    val configManager = UpdaterConfigManager(context)

    init {
        // 初始化并持久化默认预设更新源
        configManager.ensureDefaultSources(defaultSources)
    }

    companion object {
        @Volatile
        var lastUpdateInfo: UpdateInfo? = null

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
     * 模式一：应用启动时自动检测更新
     * 仅在用户开启了「启动时自动检查更新」设置时触发，静默检测，有新版本才弹窗
     */
    fun checkUpdateOnStartup(activityContext: Context) {
        if (!configManager.isAutoCheckOnStartup) {
            // 默认关闭，用户未开启则直接跳过
            return
        }

        val currentVersionCode = getLocalVersionCode(activityContext)
        checkUpdate(
            onUpdateAvailable = { updateInfo ->
                if (updateInfo.latestVersionCode > currentVersionCode) {
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
        Toast.makeText(activityContext, "正在检查更新...", Toast.LENGTH_SHORT).show()
        val currentVersionCode = getLocalVersionCode(activityContext)

        checkUpdate(
            onUpdateAvailable = { updateInfo ->
                if (updateInfo.latestVersionCode > currentVersionCode) {
                    showUpdateDialog(activityContext, updateInfo)
                } else {
                    Toast.makeText(activityContext, "当前已是最新版本 (${updateInfo.latestVersionName})", Toast.LENGTH_SHORT).show()
                }
            },
            onNoUpdate = {
                Toast.makeText(activityContext, "当前已是最新版本", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(activityContext, "检查更新失败: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * 兼容原有的无感知检查更新并弹窗方法
     */
    fun checkAndShowUpdateDialog(activityContext: Context) {
        val currentVersionCode = getLocalVersionCode(activityContext)
        checkUpdate(
            onUpdateAvailable = { updateInfo ->
                if (updateInfo.latestVersionCode > currentVersionCode) {
                    showUpdateDialog(activityContext, updateInfo)
                }
            },
            onNoUpdate = {},
            onError = { error ->
                handler.post {
                    Toast.makeText(activityContext, "检查更新失败: $error", Toast.LENGTH_SHORT).show()
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
     * 核心异步检查更新方法（根据当前激活的更新源类型自动分发）
     */
    fun checkUpdate(
        onUpdateAvailable: (UpdateInfo) -> Unit,
        onNoUpdate: () -> Unit,
        onError: (String) -> Unit
    ) {
        val source = configManager.getSelectedSource()
        if (source == null) {
            handler.post { onError("未配置有效的更新源") }
            return
        }

        when (source.type) {
            UpdateSourceType.CLOUDFLARE_R2 -> checkCloudflareR2(source, onUpdateAvailable, onNoUpdate, onError)
            UpdateSourceType.GITHUB_RELEASES -> checkGitHubRelease(source, onUpdateAvailable, onNoUpdate, onError)
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

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { onError(e.message ?: "网络连接失败") }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    handler.post { onError("HTTP ${response.code}") }
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

                    if (appIdVal.isEmpty() || latestVersionCode <= 0) {
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

    private fun showUpdateDialog(context: Context, updateInfo: UpdateInfo) {
        if (context is android.app.Activity) {
            if (context.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && context.isDestroyed)) {
                return
            }
        }
        try {
            val builder = AlertDialog.Builder(context).apply {
                setTitle("发现新版本 v${updateInfo.latestVersionName}")
                setMessage(updateInfo.updateLog.ifEmpty { "检测到新版本发布，可进入下载中心获取更新。" })
                setCancelable(!updateInfo.isForceUpdate)
                
                setPositiveButton("立即查看") { dialog, _ ->
                    dialog.dismiss()
                    openDownloadCenter(context, updateInfo)
                }
                
                if (!updateInfo.isForceUpdate) {
                    setNegativeButton("稍后再说") { dialog, _ ->
                        dialog.dismiss()
                    }
                }
            }
            
            val dialog = builder.create()
            dialog.show()

            if (updateInfo.isForceUpdate) {
                dialog.setOnCancelListener {
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        } catch (e: Throwable) {
            UpdaterLog.e("显示更新对话框异常", e)
        }
    }

    fun openDownloadCenter(context: Context, updateInfo: UpdateInfo? = null) {
        val targetInfo = updateInfo ?: lastUpdateInfo
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
