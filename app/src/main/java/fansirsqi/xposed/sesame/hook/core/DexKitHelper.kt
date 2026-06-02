package fansirsqi.xposed.sesame.hook.core

import android.content.Context
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Method

/**
 * DexKit 辅助类，提供异步类/方法搜索与缓存机制
 */
object DexKitHelper {
    private const val TAG = "DexKitHelper"
    private var dexKitBridge: DexKitBridge? = null
    private val methodCache = mutableMapOf<String, Method>()
    private val classCache = mutableMapOf<String, Class<*>>()

    /**
     * 初始化 DexKit
     * @param apkPath 目标 APK 路径
     */
    suspend fun initialize(apkPath: String) = withContext(Dispatchers.IO) {
        try {
            if (dexKitBridge != null) return@withContext
            
            dexKitBridge = DexKitBridge.create(apkPath)
            Log.runtime(TAG, "DexKitBridge created for $apkPath")
        } catch (t: Throwable) {
            Log.runtime(TAG, "DexKit initialization failed")
            Log.printStackTrace(TAG, t)
        }
    }

    /**
     * 异步通过特征搜索方法
     */
//    suspend fun findMethodByCode(pattern: String): Method? = withContext(Dispatchers.IO) {
//        // TODO: Implement code pattern search
//        null
//    }

    /**
     * 异步搜索类
     */
    suspend fun findClass(className: String, classLoader: ClassLoader): Class<*>? = withContext(Dispatchers.IO) {
        if (classCache.containsKey(className)) return@withContext classCache[className]
        
        try {
            val clazz = classLoader.loadClass(className)
            classCache[className] = clazz
            return@withContext clazz
        } catch (e: Exception) {
            return@withContext null
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        dexKitBridge?.close()
        dexKitBridge = null
        methodCache.clear()
        classCache.clear()
    }
}
