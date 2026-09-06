package com.updater.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.updater.utils.ApkCleanupManager
import com.updater.utils.UpdaterLog

/**
 * 监听应用自身覆盖替换与外部附属包安装事件的广播接收器
 * 100% 确保安装成功生效后才触发物理删除，杜绝任何定时器误判
 */
class PackageInstalledReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        try {
            when (action) {
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    UpdaterLog.i("收到自身被覆盖安装激活广播 (ACTION_MY_PACKAGE_REPLACED)，执行安装包清理")
                    ApkCleanupManager.cleanInstalledApks(context)
                }
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                    val dataString = intent.dataString
                    val pkgName = dataString?.removePrefix("package:")
                    if (!pkgName.isNullOrBlank()) {
                        UpdaterLog.i("收到外部应用安装广播: $pkgName，检查是否需要清理")
                        ApkCleanupManager.cleanInstalledApkForPackage(context, pkgName)
                    }
                }
            }
        } catch (e: Throwable) {
            UpdaterLog.e("PackageInstalledReceiver 处理异常", e)
        }
    }
}
