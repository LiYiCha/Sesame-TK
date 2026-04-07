package fansirsqi.xposed.sesame.util.extra

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.mmkv.MMKV
import fansirsqi.xposed.sesame.ui.extra.RequestItem

/**
 * 请求存储工具类
 *
 * 用于保存和加载 RPC 请求列表到 MMKV 存储中。
 */
object RequestStorage {
    private const val MMKV_ID = "rpc_requests"
    private const val KEY_REQUESTS = "requests"
    private val objectMapper = jacksonObjectMapper()

    @JvmStatic
    fun loadRequests(context: Context): MutableList<RequestItem> {
        val mmkv = MMKV.mmkvWithID(MMKV_ID, Context.MODE_PRIVATE)
        val json = mmkv.decodeString(KEY_REQUESTS) ?: return mutableListOf()
        return try {
            objectMapper.readValue(json)
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @JvmStatic
    fun saveRequests(context: Context, list: List<RequestItem>) {
        val mmkv = MMKV.mmkvWithID(MMKV_ID, Context.MODE_PRIVATE)
        val json = objectMapper.writeValueAsString(list)
        mmkv.encode(KEY_REQUESTS, json)
    }
}