package fansirsqi.xposed.sesame.ui.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.scaffold.update.UpdateChecker
import com.scaffold.update.checker.UpdateCheckResponse
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 更新管理器 - 负责 UI 交互
 *
 * @param context Android 上下文
 * @param config 更新配置
 * @param coroutineScope 协程作用域
 */
class UpdateManager(
    private val context: Context,
    private val config: UpdateConfig,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = "UpdateManager"
    private val updateChecker: UpdateChecker
    private var progressDialog: AlertDialog? = null

    /**
     * 兼容旧版本的构造函数
     */
    constructor(
        context: Context,
        baseUrl: String,
        appId: String,
        channel: String = "beta",
        coroutineScope: CoroutineScope
    ) : this(context, UpdateConfig(baseUrl, appId, channel), coroutineScope)

    init {
        val currentVersion = extractVersion(BuildConfig.VERSION)
        updateChecker = UpdateChecker(config.baseUrl, config.appId, currentVersion, config.channel)
    }

    /**
     * 从 BuildConfig.VERSION 提取版本号
     * 例如: "v0.2.7-beta-b74p" -> "0.2.7"
     */
    private fun extractVersion(version: String): String {
        return version.removePrefix("v").substringBefore("-")
    }

    /**
     * 检查更新（自动显示对话框）
     */
    fun checkForUpdates() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.runtime("开始检查更新...")
                val response = updateChecker.checkForUpdate()

                if (response == null) {
                    Log.runtime("无法连接到更新服务器，请检查网络连接")
                    return@launch
                }

                if (response.updateAvailable) {
                    Log.runtime("发现新版本: ${response.latestVersion}")
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(response)
                    }
                } else {
                    Log.runtime("当前已是最新版本")
                }
            } catch (e: Exception) {
                // 网络异常是正常情况（后端未启动、离线等），不打印堆栈跟踪
                Log.runtime("检查更新失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    /**
     * 显示更新对话框
     */
    private fun showUpdateDialog(response: UpdateCheckResponse) {
        val message = buildString {
            append("发现新版本：${response.latestVersion}\n")
            append("当前版本：${response.currentVersion}\n")
            response.fileSize?.let { append("文件大小：${formatBytes(it)}\n") }
            append("\n")
            if (!response.releaseNotes.isNullOrBlank()) {
                append("更新内容：\n${response.releaseNotes}")
            }
        }

        AlertDialog.Builder(context)
            .setTitle("发现新版本")
            .setMessage(message)
            .setCancelable(!response.forceUpdate)
            .setPositiveButton("立即更新") { _, _ -> downloadAndInstall(response) }
            .apply { if (!response.forceUpdate) setNegativeButton("稍后", null) }
            .show()
    }

    /**
     * 下载并安装更新
     */
    private fun downloadAndInstall(response: UpdateCheckResponse) {
        val installerFile = response.files?.firstOrNull { it.fileType == "installer" }
        if (installerFile == null) {
            Toast.makeText(context, "更新信息错误", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 显示进度对话框
                withContext(Dispatchers.Main) {
                    progressDialog = AlertDialog.Builder(context)
                        .setTitle("下载更新")
                        .setMessage("正在下载...")
                        .setCancelable(false)
                        .create()
                    progressDialog?.show()
                }

                // 准备下载目录
                val downloadDir = Environment.getExternalStoragePublicDirectory(config.downloadDir)
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                val destinationFile = File(downloadDir, installerFile.fileName)
                if (destinationFile.exists()) {
                    Log.runtime("删除旧文件: ${destinationFile.name}")
                    destinationFile.delete()
                }

                Log.runtime("开始下载，fileKey: ${installerFile.fileKey}")

                // 下载文件
                val success = updateChecker.downloadFile(
                    fileKey = installerFile.fileKey,
                    destinationFile = destinationFile,
                    onProgress = { downloaded, total, percent ->
                        coroutineScope.launch(Dispatchers.Main) {
                            progressDialog?.setMessage(
                                "正在下载：$percent% (${formatBytes(downloaded)} / ${formatBytes(total)})"
                            )
                        }
                    }
                )

                if (!success) {
                    withContext(Dispatchers.Main) {
                        progressDialog?.dismiss()
                        Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.runtime("下载完成，开始验证文件")

                // 验证文件
                val verified = updateChecker.verifyFile(
                    file = destinationFile,
                    expectedMd5 = installerFile.md5,
                    expectedSha256 = installerFile.sha256
                )

                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()

                    if (verified) {
                        Log.runtime("文件验证成功，开始安装")
                        installApk(destinationFile)
                    } else {
                        Log.runtime("文件验证失败")
                        destinationFile.delete()
                        Toast.makeText(context, "文件校验失败，请重新下载", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                // 网络异常是正常情况（后端未启动、离线等），不打印堆栈跟踪
                Log.runtime("下载更新失败: ${e.message ?: "未知错误"}")
                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    Toast.makeText(context, "下载失败，请检查网络连接", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 安装 APK
     */
    private fun installApk(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apkFile
                )
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(
                    android.net.Uri.fromFile(apkFile),
                    "application/vnd.android.package-archive"
                )
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 安装失败是正常情况（权限问题、系统限制等），不打印堆栈跟踪
            Log.runtime("安装失败: ${e.message ?: "未知错误"}")
            Toast.makeText(context, "安装失败，请手动安装", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 格式化字节数
     */
    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    /**
     * 清理旧的 APK 文件
     * 删除下载目录中的旧版本 APK 文件
     *
     * @return 删除的文件数量
     */
    fun cleanOldApkFiles(): Int {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(config.downloadDir)
            if (!downloadDir.exists()) {
                Log.runtime("下载目录不存在")
                return 0
            }

            val apkFiles = downloadDir.listFiles { file ->
                file.isFile && file.extension == "apk"
            }

            if (apkFiles.isNullOrEmpty()) {
                Log.runtime("没有找到 APK 文件")
                return 0
            }

            // 按修改时间排序，保留最新的一个
            val sortedFiles = apkFiles.sortedByDescending { it.lastModified() }
            val filesToDelete = sortedFiles.drop(1) // 保留最新的，删除其他的

            var deletedCount = 0
            filesToDelete.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                    Log.runtime("删除旧文件: ${file.name}")
                }
            }

            Log.runtime("清理完成，删除了 $deletedCount 个文件")
            deletedCount
        } catch (e: Exception) {
            // 文件操作失败是正常情况（权限问题、文件被占用等），不打印堆栈跟踪
            Log.runtime("清理文件失败: ${e.message ?: "未知错误"}")
            0
        }
    }
}
