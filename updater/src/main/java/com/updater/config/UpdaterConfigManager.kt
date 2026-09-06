package com.updater.config

import android.content.Context
import android.content.SharedPreferences
import com.updater.model.UpdateSource
import com.updater.model.UpdateSourceType
import org.json.JSONArray
import org.json.JSONObject

class UpdaterConfigManager(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("updater_preferences", Context.MODE_PRIVATE)

    companion object {
        const val UPDATE_MODE_MANUAL = 0 // 手动更新 (默认)
        const val UPDATE_MODE_AUTO = 1   // 自动更新 (启动静默检测)

        private const val KEY_UPDATE_MODE = "key_update_mode"
        private const val KEY_AUTO_CHECK_ON_STARTUP = "key_auto_check_on_startup"
        private const val KEY_SELECTED_SOURCE_ID = "key_selected_source_id"
        private const val KEY_SOURCES_LIST = "key_sources_list"
    }

    /**
     * 更新检测模式：
     * 0: UPDATE_MODE_MANUAL 手动更新（默认，仅用户主动点击时检测）
     * 1: UPDATE_MODE_AUTO   自动更新（应用启动时后台静默检测，有新版本才提醒）
     */
    var updateMode: Int
        get() {
            return if (sp.contains(KEY_UPDATE_MODE)) {
                sp.getInt(KEY_UPDATE_MODE, UPDATE_MODE_MANUAL)
            } else {
                if (sp.getBoolean(KEY_AUTO_CHECK_ON_STARTUP, false)) UPDATE_MODE_AUTO else UPDATE_MODE_MANUAL
            }
        }
        set(value) {
            sp.edit()
                .putInt(KEY_UPDATE_MODE, value)
                .putBoolean(KEY_AUTO_CHECK_ON_STARTUP, value == UPDATE_MODE_AUTO)
                .apply()
        }

    /**
     * 是否在 App 启动时自动检查更新
     * 默认值为 false（即默认手动更新）
     */
    var isAutoCheckOnStartup: Boolean
        get() = updateMode == UPDATE_MODE_AUTO
        set(value) {
            updateMode = if (value) UPDATE_MODE_AUTO else UPDATE_MODE_MANUAL
        }

    /**
     * 当前选中的活跃更新源 ID
     */
    var selectedSourceId: String
        get() = sp.getString(KEY_SELECTED_SOURCE_ID, "") ?: ""
        set(value) = sp.edit().putString(KEY_SELECTED_SOURCE_ID, value).apply()

    /**
     * 初始化或合并预设更新源
     */
    fun ensureDefaultSources(defaults: List<UpdateSource>) {
        val existing = getSources().toMutableList()
        var changed = false

        if (existing.isEmpty()) {
            existing.addAll(defaults)
            changed = true
        } else {
            for (def in defaults) {
                if (existing.none { it.id == def.id }) {
                    existing.add(def)
                    changed = true
                }
            }
        }

        if (changed) {
            saveSources(existing)
        }

        if (selectedSourceId.isEmpty() || existing.none { it.id == selectedSourceId }) {
            if (existing.isNotEmpty()) {
                selectedSourceId = existing[0].id
            }
        }
    }

    /**
     * 获取所有已保存的更新源列表
     */
    fun getSources(): List<UpdateSource> {
        val jsonStr = sp.getString(KEY_SOURCES_LIST, null) ?: return emptyList()
        val list = mutableListOf<UpdateSource>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val typeStr = obj.optString("type", UpdateSourceType.CLOUDFLARE_R2.name)
                val type = try {
                    UpdateSourceType.valueOf(typeStr)
                } catch (e: Exception) {
                    UpdateSourceType.CLOUDFLARE_R2
                }
                list.add(
                    UpdateSource(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        type = type,
                        downloadHost = obj.optString("downloadHost").ifEmpty { null },
                        isPreset = obj.optBoolean("isPreset", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * 保存更新源列表
     */
    fun saveSources(sources: List<UpdateSource>) {
        val jsonArray = JSONArray()
        for (s in sources) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("type", s.type.name)
                put("downloadHost", s.downloadHost ?: "")
                put("isPreset", s.isPreset)
            }
            jsonArray.put(obj)
        }
        sp.edit().putString(KEY_SOURCES_LIST, jsonArray.toString()).apply()
    }

    /**
     * 获取当前生效的更新源
     */
    fun getSelectedSource(): UpdateSource? {
        val sources = getSources()
        if (sources.isEmpty()) return null
        return sources.find { it.id == selectedSourceId } ?: sources.firstOrNull()
    }

    /**
     * 添加自定义更新源
     */
    fun addSource(source: UpdateSource) {
        val list = getSources().toMutableList()
        list.add(source)
        saveSources(list)
        // 自动切换为刚添加的源
        selectedSourceId = source.id
    }

    /**
     * 删除更新源（预设源不允许删除）
     */
    fun deleteSource(sourceId: String): Boolean {
        val list = getSources().toMutableList()
        val target = list.find { it.id == sourceId } ?: return false
        if (target.isPreset) return false // 内置源不可删

        list.remove(target)
        saveSources(list)

        // 如果被删除的是当前选中的源，自动回退到第一个
        if (selectedSourceId == sourceId) {
            selectedSourceId = list.firstOrNull()?.id ?: ""
        }
        return true
    }
}
