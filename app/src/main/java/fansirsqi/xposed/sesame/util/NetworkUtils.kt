package fansirsqi.xposed.sesame.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

object NetworkUtils {

    /**
     * 判断数据是否为 GZIP 格式
     */
    fun isGzip(data: ByteArray): Boolean {
        if (data.size < 2) return false
        val header = (data[0].toInt() and 0xff) or ((data[1].toInt() and 0xff) shl 8)
        return header == GZIPInputStream.GZIP_MAGIC
    }

    /**
     * 解压 GZIP 数据
     */
    fun decompressGzip(data: ByteArray): ByteArray? {
        if (!isGzip(data)) return data
        
        return try {
            val bais = ByteArrayInputStream(data)
            val gzis = GZIPInputStream(bais)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (gzis.read(buffer).also { len = it } != -1) {
                baos.write(buffer, 0, len)
            }
            baos.toByteArray()
        } catch (e: Exception) {
            Log.error("NetworkUtils", "GZIP 解压失败: ${e.message}")
            null
        }
    }
    
    /**
     * 尝试将字节数组转为字符串，自动处理 GZIP
     */
    fun bytesToString(data: ByteArray): String {
        val decompressed = decompressGzip(data) ?: return "[Decompression Failed]"
        return String(decompressed, Charsets.UTF_8)
    }
}
