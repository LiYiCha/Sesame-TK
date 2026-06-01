package fansirsqi.xposed.sesame.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object FileExportUtil {

    /**
     * 导出内容到 Download/sesame-capture 目录
     */
    fun exportToFile(context: Context, fileName: String, content: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, fileName, content)
        } else {
            saveViaFileApi(fileName, content)
        }
    }

    private fun saveViaMediaStore(context: Context, fileName: String, content: String): Uri? {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/sesame-capture"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        
        uri?.let {
            resolver.openOutputStream(it)?.use { os ->
                os.write(content.toByteArray())
            }
        }
        return uri
    }

    private fun saveViaFileApi(fileName: String, content: String): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sesame-capture")
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, fileName)
        return try {
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }
}
