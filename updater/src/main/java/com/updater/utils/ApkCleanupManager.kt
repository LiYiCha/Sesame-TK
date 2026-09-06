package com.updater.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.updater.db.DownloadDatabaseHelper
import java.io.File

object ApkCleanupManager {

    /**
     * 清理已完成安装的 APK 安装包（用于自身覆盖安装生效后或冷启动对账）
     */
    fun cleanInstalledApks(context: Context) {
        val updateDir = UpdatePathManager.getUpdateDir(context)
        if (!updateDir.exists() || !updateDir.isDirectory) return

        val files = updateDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) } ?: return
        if (files.isEmpty()) return

        val pm = context.packageManager
        val dbHelper = DownloadDatabaseHelper(context)

        for (apkFile in files) {
            try {
                val archiveInfo: PackageInfo? = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                if (archiveInfo == null) {
                    continue
                }

                val targetPkg = archiveInfo.packageName
                val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    archiveInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    archiveInfo.versionCode.toLong()
                }

                // 检查系统当前是否已安装此包
                val installedInfo: PackageInfo? = try {
                    pm.getPackageInfo(targetPkg, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }

                if (installedInfo != null) {
                    val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        installedInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        installedInfo.versionCode.toLong()
                    }

                    // 核心判断：只有在系统运行的版本已经 >= 待安装的 APK 版本时，才证明安装已完成并生效！
                    if (installedVersionCode >= apkVersionCode) {
                        if (apkFile.delete()) {
                            UpdaterLog.i("已成功检测到应用更新生效，安全删除安装包: ${apkFile.name}")
                            dbHelper.deleteTaskBySavePath(apkFile.absolutePath)
                        }
                    } else {
                        // 系统版本仍然低于该 APK，说明用户尚未安装，坚决保留以供随时复用！
                        UpdaterLog.i("保留尚未安装的更新包供复用: ${apkFile.name} (目标Build: $apkVersionCode, 当前Build: $installedVersionCode)")
                    }
                }
            } catch (e: Throwable) {
                UpdaterLog.e("检查清理安装包异常: ${apkFile.name}", e)
            }
        }
    }

    /**
     * 针对指定包名清理已安装的 APK 文件（用于收到 ACTION_PACKAGE_ADDED / REPLACED 广播时）
     */
    fun cleanInstalledApkForPackage(context: Context, packageName: String) {
        val updateDir = UpdatePathManager.getUpdateDir(context)
        if (!updateDir.exists() || !updateDir.isDirectory) return

        val files = updateDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) } ?: return
        val pm = context.packageManager
        val dbHelper = DownloadDatabaseHelper(context)

        for (apkFile in files) {
            try {
                val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0) ?: continue
                if (archiveInfo.packageName == packageName) {
                    val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        archiveInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        archiveInfo.versionCode.toLong()
                    }

                    val installedInfo: PackageInfo? = try {
                        pm.getPackageInfo(packageName, 0)
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }

                    if (installedInfo != null) {
                        val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            installedInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            installedInfo.versionCode.toLong()
                        }

                        // 只有系统已生效版本 >= 待安装版本，才物理删除
                        if (installedVersionCode >= apkVersionCode) {
                            if (apkFile.delete()) {
                                UpdaterLog.i("指定包安装完成并生效，安全删除安装包: ${apkFile.name} ($packageName)")
                                dbHelper.deleteTaskBySavePath(apkFile.absolutePath)
                            }
                        } else {
                            UpdaterLog.i("指定包系统版本仍低于文件版本，保留供复用: ${apkFile.name}")
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * 冷启动时安全无感对账并清理
     */
    fun checkAndCleanOnStartup(context: Context) {
        try {
            cleanInstalledApks(context)
        } catch (e: Throwable) {
            UpdaterLog.e("启动对账清理异常", e)
        }
    }
}
