package com.updater.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 * 更新安装包存储目录管理。
 */
object UpdatePathManager {

    /** 通配 MIME：让 MT / 文件管理器等第三方应用都能进入「用其他应用打开」的候选列表。 */
    private const val MIME_ANY = "*/*"

    /**
     * 获取更新安装包与附加包的标准存储目录
     * 默认定位至 /storage/emulated/0/Android/media/{packageName}/update/
     */
    fun getUpdateDir(context: Context): File {
        val storageDir = File(
            Environment.getExternalStorageDirectory(),
            "Android" + File.separator + "media" + File.separator + context.packageName + File.separator + "update"
        )
        if (!storageDir.exists()) {
            try {
                storageDir.mkdirs()
            } catch (_: Exception) {}
        }
        if (storageDir.exists() && storageDir.canWrite()) {
            return storageDir
        }

        // 容错降级
        val mediaDirs = context.externalMediaDirs
        if (mediaDirs.isNotEmpty() && mediaDirs[0] != null) {
            val fallback = File(mediaDirs[0], "update")
            if (!fallback.exists()) fallback.mkdirs()
            return fallback
        }

        val externalFiles = context.getExternalFilesDir("update")
        if (externalFiles != null) {
            if (!externalFiles.exists()) externalFiles.mkdirs()
            return externalFiles
        }

        val internalDir = File(context.filesDir, "update")
        if (!internalDir.exists()) internalDir.mkdirs()
        return internalDir
    }

    /**
     * 用其他应用打开下载好的安装包（弹出系统选择器）。
     *
     * 实现方式与日志页已验证可用的入口一致：FileProvider 生成 content:// URI，
     * 并配合通配 MIME，使 MT、文件管理器等第三方应用都会出现在候选列表中。
     */
    fun openApkWithOtherApp(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.updater.provider", apkFile)
        } catch (_: Exception) {
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, MIME_ANY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 部分应用需要 ClipData 才能拿到临时读权限
            clipData = ClipData.newRawUri(apkFile.name, apkUri)
        }

        try {
            val chooser = Intent.createChooser(intent, "用其他应用打开").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            // 无可用应用时静默忽略
        }
    }
}
