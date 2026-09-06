package com.updater.config

import android.content.Context
import android.content.SharedPreferences
import com.updater.model.UpdateInfo
import com.updater.model.UpdatePackage
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
        private const val KEY_CACHED_UPDATE_INFO = "key_cached_update_info"
        private const val KEY_ADMIN_TOKEN = "key_admin_token"
        private const val KEY_ADMIN_USERNAME = "key_admin_username"
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
     * 管理员 Token 与认证信息
     */
    var adminToken: String
        get() = sp.getString(KEY_ADMIN_TOKEN, "") ?: ""
        set(value) = sp.edit().putString(KEY_ADMIN_TOKEN, value).apply()

    var adminUsername: String
        get() = sp.getString(KEY_ADMIN_USERNAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_ADMIN_USERNAME, value).apply()

    val isAdminLoggedIn: Boolean
        get() = adminToken.isNotBlank()

    fun logoutAdmin() {
        sp.edit().remove(KEY_ADMIN_TOKEN).remove(KEY_ADMIN_USERNAME).apply()
    }

    /**
     * 初始化或合并预设更新源
     */
    fun ensureDefaultSources(defaults: List<UpdateSource>) {
        val existing = getSources()
        val customSources = existing.filter { !it.isPreset }
        val updatedList = mutableListOf<UpdateSource>()
        
        // 预设源始终同步最新配置
        updatedList.addAll(defaults)
        // 保留用户添加的自定义源
        for (c in customSources) {
            if (updatedList.none { it.id == c.id }) {
                updatedList.add(c)
            }
        }
        
        saveSources(updatedList)

        if (selectedSourceId.isEmpty() || updatedList.none { it.id == selectedSourceId }) {
            if (updatedList.isNotEmpty()) {
                selectedSourceId = updatedList[0].id
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

    /**
     * 持久化缓存最新获取到的更新详情与配套包列表
     * 解决退出页面或冷启动后下载列表丢失的问题
     */
    fun saveCachedUpdateInfo(info: UpdateInfo) {
        try {
            val obj = JSONObject().apply {
                put("appId", info.appId)
                put("appName", info.appName)
                put("latestVersionCode", info.latestVersionCode)
                put("latestVersionName", info.latestVersionName)
                put("updateLog", info.updateLog)
                put("isForceUpdate", info.isForceUpdate)
                put("lastUpdated", info.lastUpdated)

                val pkgs = JSONArray()
                for (p in info.packages) {
                    val pObj = JSONObject().apply {
                        put("packageId", p.packageId)
                        put("packageName", p.packageName)
                        put("versionName", p.versionName)
                        put("versionCode", p.versionCode)
                        put("description", p.description)
                        put("downloadUrl", p.downloadUrl)
                        put("apkSize", p.apkSize)
                        put("apkMd5", p.apkMd5)
                    }
                    pkgs.put(pObj)
                }
                put("packages", pkgs)
            }
            sp.edit().putString(KEY_CACHED_UPDATE_INFO, obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 读取本地持久化缓存的更新信息与配套包列表
     */
    fun getCachedUpdateInfo(): UpdateInfo? {
        val jsonStr = sp.getString(KEY_CACHED_UPDATE_INFO, null) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            val pkgs = mutableListOf<UpdatePackage>()
            val pkgsArr = obj.optJSONArray("packages")
            if (pkgsArr != null) {
                for (i in 0 until pkgsArr.length()) {
                    val pObj = pkgsArr.getJSONObject(i)
                    pkgs.add(
                        UpdatePackage(
                            packageId = pObj.optString("packageId"),
                            packageName = pObj.optString("packageName"),
                            versionName = pObj.optString("versionName"),
                            versionCode = pObj.optInt("versionCode"),
                            description = pObj.optString("description"),
                            downloadUrl = pObj.optString("downloadUrl"),
                            apkSize = pObj.optLong("apkSize"),
                            apkMd5 = pObj.optString("apkMd5")
                        )
                    )
                }
            }
            UpdateInfo(
                appId = obj.optString("appId"),
                appName = obj.optString("appName"),
                latestVersionCode = obj.optInt("latestVersionCode"),
                latestVersionName = obj.optString("latestVersionName"),
                updateLog = obj.optString("updateLog"),
                isForceUpdate = obj.optBoolean("isForceUpdate"),
                packages = pkgs,
                lastUpdated = obj.optLong("lastUpdated")
            )
        } catch (e: Exception) {
            null
        }
    }
}
