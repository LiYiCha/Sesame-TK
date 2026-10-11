package fansirsqi.xposed.sesame.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 选择其他应用打开目录，做法与 UpdatePathManager.openApkWithOtherApp 一致：
 * FileProvider 生成 content:// URI + 通配 MIME + ClipData，只对目录内已存在的文件生成 URI，
 * 不再创建占位文件。
 */
object FolderLauncher {

    private const val TAG = "FolderLauncher"

    fun open(context: Context, path: String) {
        val targetFile = File(path).candidateFile()
        if (targetFile == null) {
            Log.error(TAG, "目录内无可用文件: $path")
            ToastUtil.showToast(context, "路径: $path")
            return
        }

        val contentUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
        } catch (e: Exception) {
            Log.error(TAG, "生成 URI 失败: ${e.message}")
            ToastUtil.showToast(context, "路径: ${targetFile.absolutePath}")
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "*/*")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
            clipData = ClipData.newRawUri(targetFile.name, contentUri)
        }

        try {
            val chooser = Intent.createChooser(intent, "选择其他应用打开")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Log.error(TAG, "无可用应用: ${e.message}")
            ToastUtil.showToast(context, "路径: ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.error(TAG, "选择应用打开失败: ${e.message}")
            ToastUtil.showToast(context, "路径: ${targetFile.absolutePath}")
        }
    }

    /**
     * FileProvider 只能对实际文件生成可用 URI：目录内有文件则取最新的一个，
     * 只有子目录时递归取子目录内最新的一个，全为空时返回 null。
     */
    private fun File.candidateFile(): File? {
        val files = listFiles() ?: return null
        files.filter { it.isFile }
            .maxByOrNull { it.lastModified() }
            ?.let { return it }
        return files.filter { it.isDirectory }.firstNotNullOfOrNull { it.candidateFile() }
    }
}
