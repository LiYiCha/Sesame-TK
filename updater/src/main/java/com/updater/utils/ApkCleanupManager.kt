package com.updater.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.updater.db.DownloadDatabaseHelper
import java.io.File
import java.util.concurrent.Executors

/**
 * 已安装生效的安装包清理器。
 *
 * 【判定原则】只有当“系统当前已安装的 APK”与“待清理的下载 APK”确认为**同一构建**时才删除。
 * 仅比较 versionCode 无法区分“同一天多次发版 / 同版本重建”的不同构建，
 * 会把刚下载、尚未安装的新包误判为“已安装”而误删；因此这里采用
 * “版本号预筛 + APK 内容 MD5 精确比对”的双重校验。
 *
 * 【线程模型】APK 哈希计算耗时较长，全部清理均在内部单线程池执行，
 * 广播接收器 / 主线程调用不会阻塞；需要刷新界面时通过 [cleanInstalledApkForPackage]
 * 的 onFinished 回调回到主线程。
 */
object ApkCleanupManager {

    private val cleanupExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "apk-cleanup").apply { isDaemon = true }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 清理已完成安装的 APK 安装包（全量扫描 + 构建对账）。
     *
     * 用于应用启动时的兜底对账：应用被强停等场景下收不到安装广播，
     * 由本方法在启动后补齐清理。异步执行，调用方无需关心线程。
     */
    fun cleanInstalledApks(context: Context) {
        val appContext = context.applicationContext
        cleanupExecutor.execute {
            try {
                cleanInstalledApksInternal(appContext)
            } catch (t: Throwable) {
                UpdaterLog.e("全量安装包对账异常", t)
            }
        }
    }

    /**
     * 针对指定包名清理已安装的 APK 文件（用于收到 ACTION_PACKAGE_ADDED / REPLACED 广播时）。
     *
     * @param onFinished 清理完成后在主线程回调（可用于刷新下载列表界面）
     */
    fun cleanInstalledApkForPackage(
        context: Context,
        packageName: String,
        onFinished: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        cleanupExecutor.execute {
            try {
                cleanInstalledApkForPackageInternal(appContext, packageName)
            } catch (t: Throwable) {
                UpdaterLog.e("清理安装包异常: $packageName", t)
            } finally {
                if (onFinished != null) {
                    mainHandler.post(onFinished)
                }
            }
        }
    }

    // ---------- 内部实现（工作线程） ----------

    private fun cleanInstalledApksInternal(context: Context) {
        val updateDir = UpdatePathManager.getUpdateDir(context)
        val apkFiles = listApkFiles(updateDir) ?: return
        val pm = context.packageManager

        for (apkFile in apkFiles) {
            try {
                val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0) ?: continue
                val targetPkg = archiveInfo.packageName ?: continue
                if (isInstalledSameBuild(context, targetPkg, apkFile, archiveInfo)) {
                    deleteApk(context, apkFile)
                } else {
                    UpdaterLog.i("保留尚未安装生效的更新包: ${apkFile.name}")
                }
            } catch (t: Throwable) {
                UpdaterLog.e("检查清理安装包异常: ${apkFile.name}", t)
            }
        }
    }

    private fun cleanInstalledApkForPackageInternal(context: Context, packageName: String) {
        val updateDir = UpdatePathManager.getUpdateDir(context)
        val apkFiles = listApkFiles(updateDir) ?: return
        val pm = context.packageManager

        for (apkFile in apkFiles) {
            try {
                val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0) ?: continue
                if (archiveInfo.packageName != packageName) continue
                if (isInstalledSameBuild(context, packageName, apkFile, archiveInfo)) {
                    deleteApk(context, apkFile)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun listApkFiles(updateDir: File): Array<File>? {
        if (!updateDir.exists() || !updateDir.isDirectory) return null
        val files = updateDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
        return if (files.isNullOrEmpty()) null else files
    }

    /**
     * 判断 [apkFile] 对应的应用是否已安装且与系统当前生效版本为同一构建。
     *
     * - 已安装 versionCode < APK versionCode：明确尚未安装，保留；
     * - 已安装 versionCode >= APK versionCode：再用已安装 APK 与下载 APK 的 MD5 精确比对，
     *   完全一致才允许删除；无法读取已安装 APK 时退化为“严格更高版本才删除”，宁保留不误删。
     */
    private fun isInstalledSameBuild(
        context: Context,
        packageName: String,
        apkFile: File,
        archiveInfo: PackageInfo
    ): Boolean {
        val pm = context.packageManager
        val installedInfo = try {
            pm.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }

        val apkVersionCode = archiveInfo.longVersionCodeCompat()
        val installedVersionCode = installedInfo.longVersionCodeCompat()

        // 系统版本低于待安装包，说明用户尚未安装，坚决保留以供复用
        if (installedVersionCode < apkVersionCode) return false

        val installedApkPath = installedInfo.applicationInfo?.sourceDir
        val installedMd5 = installedApkPath
            ?.takeIf { it.isNotEmpty() }
            ?.let { ApkInstaller.calculateFileMd5(File(it)) }
            .orEmpty()
        val downloadedMd5 = ApkInstaller.calculateFileMd5(apkFile)

        if (installedMd5.isNotEmpty() && downloadedMd5.isNotEmpty()) {
            // 内容完全一致 → 确认同一构建，已安装生效
            return installedMd5.equals(downloadedMd5, ignoreCase = true)
        }

        // 无法读取已安装 APK（分包 / 权限受限场景）：仅在系统版本严格更高时删除
        return installedVersionCode > apkVersionCode
    }

    private fun deleteApk(context: Context, apkFile: File) {
        if (!apkFile.delete()) {
            UpdaterLog.i("安装包删除失败: ${apkFile.name}")
            return
        }
        UpdaterLog.i("检测到系统已安装同一构建，安全删除安装包: ${apkFile.name}")
        try {
            DownloadDatabaseHelper(context).deleteTaskBySavePath(apkFile.absolutePath)
        } catch (t: Throwable) {
            UpdaterLog.e("清理下载记录失败: ${apkFile.name}", t)
        }
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
}
