package fansirsqi.xposed.sesame.util

import android.content.Context
import com.updater.Updater
import com.updater.ui.UpdaterThemeConfig
import com.updater.utils.ApkCleanupManager
import com.updater.utils.IUpdaterLogger
import com.updater.utils.UpdaterLog
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme

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
        
        // 注入项目统一日志输出器
        UpdaterLog.setLogger(object : IUpdaterLogger {
            override fun i(tag: String, msg: String) {
                Log.runtime(tag, msg)
            }

            override fun e(tag: String, msg: String, throwable: Throwable?) {
                Log.runtime(tag, "ERROR: $msg ${throwable?.message ?: ""}")
                if (throwable != null) {
                    Log.printStackTrace(throwable)
                }
            }
        })

        // 注入宿主主题：更新模块页面跟随主模块 SesameTheme/HolidayTheme 换肤体系
        // 读取 themeVersion 订阅换肤变更，主题切换时模块页面自动重组同步
        UpdaterThemeConfig.schemeProvider = { dark ->
            try {
                HolidayTheme.themeVersion.intValue
                HolidayTheme.resolvePalette(dark).toColorScheme()
            } catch (_: Throwable) {
                null
            }
        }

        val builder = Updater.Companion.Builder(appContext)
            .setAppId(General.MODULE_PACKAGE_NAME)
            // 预设 1：Cloudflare Pages R2 官方源
            .addCloudflareSource(
                name = "Cloudflare 官方源",
                baseHost = "https://cicha.de5.net",
                isDefault = true
            )
            // 预设 2：GitHub Releases 官方开源发布源
            .addGitHubSource(
                name = "GitHub 官方发布源",
                repoOrUrl = "https://github.com/LiYiCha/Sesame-TK",
                isDefault = false
            )

        val newUpdater = builder.build()
        updaterInstance = newUpdater
        return newUpdater
    }

    /**
     * 应用启动时调用：
     * 1. 后台对账清理已安装生效的安装包（应用被强停时收不到安装广播，此处兜底）；
     * 2. 若用户在更新设置中开启了「启动时自动检查更新」，则执行后台静默检测。
     *
     * 清理采用“版本号预筛 + APK 内容 MD5 精确比对”，只删除与系统当前生效版本
     * 完全一致的构建，不会误删尚未安装的新包。
     */
    fun initAndCheckOnStartup(context: Context) {
        val appContext = context.applicationContext
        Thread {
            try {
                ApkCleanupManager.cleanInstalledApks(appContext)
            } catch (e: Throwable) {
                Log.runtime(TAG, "启动安装包对账异常: ${e.message}")
            }
            try {
                val updater = getUpdater(appContext)
                updater.checkUpdateOnStartup(context)
            } catch (e: Throwable) {
                Log.runtime(TAG, "启动更新检测异常: ${e.message}")
            }
        }.start()
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
     * 打开更新包下载管理列表中心
     */
    fun openDownloadList(context: Context) {
        try {
            val updater = getUpdater(context)
            updater.openDownloadCenter(context)
        } catch (e: Throwable) {
            Log.runtime(TAG, "打开下载列表异常: ${e.message}")
        }
    }
}
