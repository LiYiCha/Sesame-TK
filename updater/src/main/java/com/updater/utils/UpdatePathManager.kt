package com.updater.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File

object UpdatePathManager {

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
     * 打开更新目录（复用项目成熟的 4 重兼容唤起文件管理器机制）
     */
    fun openUpdateDirectory(context: Context, updateDir: File = getUpdateDir(context)) {
        if (!updateDir.exists()) {
            try {
                updateDir.mkdirs()
            } catch (_: Exception) {}
        }

        val relativePath = updateDir.absolutePath
            .replaceFirst("^/storage/emulated/0/", "")
            .replaceFirst("^/sdcard/", "")
        val encodedPath = Uri.encode("primary:$relativePath")
        val docUri = Uri.parse("content://com.android.externalstorage.documents/document/$encodedPath")

        val intents = listOf(
            // 1. 系统 DocumentsUI 直达
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            // 2. SAF 树形结构直达
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra("android.provider.extra.INITIAL_URI", docUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 3. 适配 MT 管理器 / 第三方文件管理器的 resource/folder 协议
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(updateDir), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 4. 适配 inode/directory 协议
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(updateDir), "inode/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }

        try {
            val chooser = Intent.createChooser(intents.last(), "选择文件管理器打开更新目录")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "更新目录: ${updateDir.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}
