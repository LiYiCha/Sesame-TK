package com.updater.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.MessageDigest

object ApkInstaller {

    /**
     * 计算文件的 MD5 值
     */
    fun calculateFileMd5(file: File): String {
        if (!file.exists()) return ""
        try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            val fis = FileInputStream(file)
            var read: Int
            while (fis.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
            fis.close()
            val md5sum = digest.digest()
            val bigInt = BigInteger(1, md5sum)
            var hex = bigInt.toString(16)
            while (hex.length < 32) {
                hex = "0$hex"
            }
            return hex
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * 校验文件 MD5 是否与预期一致
     */
    fun verifyApkMd5(file: File, expectedMd5: String): Boolean {
        val cleanExpected = expectedMd5.trim().trim('\"').trim('\'')
        // 为空或为 S3/R2 分片上传生成的 ETag (形如 hash-part_num) 时跳过校验
        if (cleanExpected.isEmpty() || cleanExpected.contains("-")) return true
        val actualMd5 = calculateFileMd5(file)
        return actualMd5.equals(cleanExpected, ignoreCase = true)
    }

    /**
     * 安全地执行 APK 的安装
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "安装包不存在", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
            if (!hasInstallPermission) {
                Toast.makeText(context, "请先授权允许安装未知来源应用", Toast.LENGTH_LONG).show()
                val packageUri = Uri.parse("package:${context.packageName}")
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.updater.provider",
                    apkFile
                )
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            }
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "无法启动系统安装程序: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
