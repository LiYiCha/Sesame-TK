package fansirsqi.xposed.sesame.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import android.content.Context
import android.net.ConnectivityManager
import fansirsqi.xposed.sesame.hook.context.AppContext

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
     * 解压 Deflate 数据
     */
    fun decompressDeflate(data: ByteArray): ByteArray? {
        return try {
            val inflater = java.util.zip.Inflater(true) // true for nowrap (headerless)
            inflater.setInput(data)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                baos.write(buffer, 0, count)
            }
            baos.toByteArray()
        } catch (e: Exception) {
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

    /**
     * 检查网络是否可用
     */
    fun isNetworkAvailable(): Boolean {
        val context = AppContext.getAppContext() ?: return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = cm.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 获取网络类型名称
     */
    fun getNetworkType(): String {
        val context = AppContext.getAppContext() ?: return "Unknown"
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = cm.activeNetworkInfo
            if (networkInfo != null && networkInfo.isConnected) {
                networkInfo.typeName
            } else {
                "No Network"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
