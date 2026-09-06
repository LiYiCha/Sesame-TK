package fansirsqi.xposed.sesame.util

import android.content.Context
import com.updater.Updater
import com.updater.utils.ApkCleanupManager
import fansirsqi.xposed.sesame.data.General

/**
 * 芝麻粒更新管理门面
 * 整合 Cloudflare Pages + R2 与 GitHub 双源更新体系
 */
object AppUpdaterManager {

    private const val TAG = "AppUpdater"
    private var updaterInstance: Updater? = null

    /**
     * 获取或初始化更新器单例
     */
    @Synchronized
    fun getUpdater(context: Context): Updater {
        val existing = updaterInstance
        if (existing != null) {
            return existing
        }

        val appContext = context.applicationContext
        val builder = Updater.Companion.Builder(appContext)
            .setAppId(General.MODULE_PACKAGE_NAME)
            // 预设 1：Cloudflare Pages R2 更新源（可由用户在界面自定义）
            .addCloudflareSource(
                name = "Cloudflare 官方源",
                baseHost = "https://update.fansirsqi.com",
                isDefault = true
            )
            // 预设 2：GitHub Releases 官方开源源
            .addGitHubSource(
                name = "GitHub 官方发布源",
                repoOrUrl = "https://github.com/fansirsqi/Sesame-TK",
                isDefault = false
            )

        val newUpdater = builder.build()
        updaterInstance = newUpdater
        return newUpdater
    }

    /**
     * 应用启动时调用：
     * 1. 自动对账清理已安装新版的残留 APK（未安装的完好保留供 0 流量秒级复用）
     * 2. 若用户在更新设置中开启了「启动时自动检查更新」，则执行后台静默检测
     */
    fun initAndCheckOnStartup(context: Context) {
        try {
            // 启动时版本对账
            ApkCleanupManager.checkAndCleanOnStartup(context)

            // 检查启动自动更新
            val updater = getUpdater(context)
            updater.checkUpdateOnStartup(context)
        } catch (e: Throwable) {
            Log.runtime(TAG, "启动更新对账异常: ${e.message}")
        }
    }

    /**
     * 手动触发检查更新（在菜单项点击时触发）
     */
    fun checkUpdateManual(context: Context) {
        try {
            val updater = getUpdater(context)
            updater.checkUpdateManual(context)
        } catch (e: Throwable) {
            Log.runtime(TAG, "手动检查更新异常: ${e.message}")
        }
    }

    /**
     * 打开更新源配置对话框
     */
    fun openSourceSettings(context: Context) {
        try {
            val updater = getUpdater(context)
            updater.openSourceSettingsDialog(context)
        } catch (e: Throwable) {
            Log.runtime(TAG, "打开更新配置异常: ${e.message}")
        }
    }
}
