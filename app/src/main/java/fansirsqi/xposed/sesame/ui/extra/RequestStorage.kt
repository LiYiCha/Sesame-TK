package fansirsqi.xposed.sesame.ui.extra

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * 请求存储工具类
 *
 * 用于保存和加载 RPC 请求列表到文件中。
 * 优化：使用 Kotlin 特性简化代码，提高性能
 */
object RequestStorage {
    private val objectMapper = ObjectMapper()
    private const val PREFS_NAME = "RpcRequests"
    private const val KEY_REQUESTS = "requests"

    /**
     * 加载请求列表
     * 使用 runCatching 简化异常处理
     */
    fun load(context: Context): List<RequestItem> {
        val prefs = getPreferences(context)
        val json = prefs.getString(KEY_REQUESTS, null) ?: return emptyList()
        return runCatching {
            objectMapper.readValue<List<RequestItem>>(json)
        }.getOrElse { emptyList() }
    }

    /**
     * 保存请求列表
     * 使用 Kotlin DSL 简化 SharedPreferences 编辑
     */
    fun save(context: Context, list: List<RequestItem>) {
        val prefs = getPreferences(context)
        val json = objectMapper.writeValueAsString(list)
        prefs.edit {
            putString(KEY_REQUESTS, json)
        }
    }

    /**
     * 获取 SharedPreferences 实例
     * 提取为私有方法，便于复用
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}