package fansirsqi.xposed.sesame.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 打开目录，做法照搬 LogViewerComposeActivity.openLogDirectory，只把日志目录换成入参。
 */
object FolderLauncher {

    private const val TAG = "FolderLauncher"

    fun open(context: Context, path: String){
        val dir = File(path)
        if (!dir.exists()) {
            try { dir.mkdirs() } catch (_: Exception) {}
        }

        // FileProvider 只能针对实际文件生成可用 URI，因此优先取目录内的文件
        val targetFile = dir.listFiles()?.firstOrNull { it.isFile }
            ?: File(dir, "open_holder.txt").apply {
                if (!exists()) try { createNewFile() } catch (_: Exception) {}
            }

        return try {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "text/*")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            val chooser = Intent.createChooser(intent, "选择其他应用打开")
            chooser.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.error(TAG, "选择应用打开失败: ${e.message}")
        }
    }
}
