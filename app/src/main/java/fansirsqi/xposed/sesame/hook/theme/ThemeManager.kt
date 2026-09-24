package fansirsqi.xposed.sesame.hook.theme

import fansirsqi.xposed.sesame.hook.context.AppContext
import fansirsqi.xposed.sesame.ui.theme.ThemeMetadata
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap
import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 主题管理器
 *
 * 负责处理主题的导出、删除、更新操作
 * 不需要Hook，只需要文件替换
 */
object ThemeManager {

    private const val TAG = "ThemeManager"

    // 支付宝内部存储路径 (动态获取，支持分身应用)
    val INTERNAL_STORAGE_PATH: String
        get() = AppContext.getAppContext()?.let { File(it.filesDir, "skin_center_dir").absolutePath }
            ?: "/data/data/com.eg.android.AlipayGphone/files/skin_center_dir"

    // 外部存储路径（SD卡）
    private val EXTERNAL_STORAGE_PATH: String
        get() = "${android.os.Environment.getExternalStorageDirectory().absolutePath}/Android/media/com.eg.android.AlipayGphone/YC_THEME"

    // 主题文件夹路径
    private const val THEMES_FOLDER = "themes"
    private const val EXPORTED_THEMES_FOLDER = "exported_themes"
    private const val SELECTED_THEME_FILE = "selected_theme"

    // SCCacheInfoModel 的候选类路径（不同版本包路径可能不同）
    private val SC_CACHE_MODEL_CLASSES = arrayOf(
        "com.alipay.mobile.skincenter.model.SCCacheInfoModel",
        "com.alipay.mobile.skincenter.manage.SCCacheInfoModel"
    )

    // 缓存 Map 识别用的场景键（与 cached_skin_info_v2 的 JSON 键一致）
    private val SCENE_KEYS = setOf("theme", "ltp", "aptrip", "widget", "emoji", "atmospheric_aptrip")

    // 官方换肤广播（SCConstants.SKIN_THEME_UPDATED），UI 层接收后走 SkinStyleHelper 应用新皮肤
    private const val ACTION_SKIN_THEME_UPDATED = "com.alipay.skincenter.skinUpdated.theme"

    /**
     * 获取当前用户ID
     *
     * 优先从 UserMap 获取，如果失败则扫描 skin_center_dir 目录
     */
    private fun getCurrentUserId(): String? {
        try {
            // 优先从 UserMap 获取
            val userId = UserMap.currentUid
            if (userId != null && userId.isNotEmpty()) {
                return userId
            }

            // 备用方案：扫描 skin_center_dir 目录
            val skinCenterDir = File(INTERNAL_STORAGE_PATH)
            if (!skinCenterDir.exists() || !skinCenterDir.isDirectory) {
                Log.runtime(TAG, "skin_center_dir 目录不存在")
                return null
            }

            // 查找第一个数字开头的目录（用户ID通常是数字）
            val userDirs = skinCenterDir.listFiles { file ->
                file.isDirectory && file.name.matches(Regex("^\\d+$"))
            }

            if (userDirs != null && userDirs.isNotEmpty()) {
                val foundUserId = userDirs[0].name
                Log.runtime(TAG, "从文件系统扫描到用户ID: $foundUserId")
                return foundUserId
            }

            Log.runtime(TAG, "未找到用户ID目录")
            return null
        } catch (e: Exception) {
            Log.runtime(TAG, "获取用户ID失败: ${e.message}")
            return null
        }
    }

    /**
     * 立即直接导出主题
     *
     * 响应 UI 层导出操作，由 IPC 广播触发，无需等待特定页面
     */
    fun exportThemesDirectly(targetUserId: String? = null): Pair<Boolean, String> {
        return try {
            val userId = targetUserId ?: getCurrentUserId()
            if (userId == null) {
                return Pair(false, "无法获取用户ID")
            }
            val userThemeDir = File(INTERNAL_STORAGE_PATH, userId)
            if (!userThemeDir.exists()) {
                return Pair(false, "主题目录不存在: ${userThemeDir.absolutePath}")
            }
            executeExportOperation(userId, userThemeDir)
        } catch (e: Exception) {
            Pair(false, "导出失败: ${e.message}")
        }
    }

    /**
     * 删除自定义主题
     *
     * 响应 UI 层删除操作，由 IPC 广播触发
     * 只删除带 theme_info.json 的导入主题目录，不动支付宝官方皮肤缓存
     */
    fun deleteThemeCacheDirectly(targetUserId: String? = null): Pair<Boolean, String> {
        return try {
            val userId = targetUserId ?: getCurrentUserId()
            if (userId == null) {
                return Pair(false, "无法获取用户ID")
            }
            val themeBaseDir = File(File(INTERNAL_STORAGE_PATH, userId), "theme")
            if (!themeBaseDir.isDirectory) {
                return Pair(false, "主题目录不存在: ${themeBaseDir.absolutePath}")
            }
            val customThemes = themeBaseDir.listFiles { file ->
                file.isDirectory && File(file, "theme_info.json").exists()
            }
            if (customThemes.isNullOrEmpty()) {
                return Pair(false, "没有可删除的自定义主题")
            }
            var deletedCount = 0
            customThemes.forEach { theme ->
                if (theme.deleteRecursively()) {
                    Log.runtime(TAG, "✓ 已删除自定义主题: ${theme.name}")
                    deletedCount++
                } else {
                    Log.runtime(TAG, "✗ 删除自定义主题失败: ${theme.name}")
                }
            }
            Log.runtime(TAG, "✓ 自定义主题删除完成: $deletedCount/${customThemes.size}")
            Pair(true, "已删除 $deletedCount 个自定义主题")
        } catch (e: Exception) {
            Pair(false, "删除失败: ${e.message}")
        }
    }

    /**
     * 立即直接应用（更新）主题
     *
     * 响应 UI 层更新操作，由 IPC 广播触发
     */
    fun applyThemeDirectly(targetUserId: String? = null): Pair<Boolean, String> {
        return try {
            val userId = targetUserId ?: getCurrentUserId()
            if (userId == null) {
                return Pair(false, "无法获取用户ID")
            }
            val userThemeDir = File(INTERNAL_STORAGE_PATH, userId)
            if (!userThemeDir.exists()) {
                return Pair(false, "主题目录不存在: ${userThemeDir.absolutePath}")
            }
            // 静默执行，结果由 UI 层提示
            applyTheme(userId, userThemeDir, quiet = true)
            Pair(true, "主题更新已执行")
        } catch (e: Exception) {
            Pair(false, "更新失败: ${e.message}")
        }
    }

    /**
     * 执行导出操作
     *
     * 将支付宝内部的主题导出到SD卡（追加模式）
     * 每个主题包含自己的 ltp 资源，形成独立的主题包
     */
    private fun executeExportOperation(userId: String, userThemeDir: File): Pair<Boolean, String> {
        try {
            if (!userThemeDir.exists()) {
                Log.runtime(TAG, "✗ 主题导出失败: 目录不存在")
                showToast("主题导出失败: 目录不存在")
                return Pair(false, "目录不存在")
            }

            val exportDir = File(EXTERNAL_STORAGE_PATH, EXPORTED_THEMES_FOLDER)
            exportDir.mkdirs()
            val targetDir = File(exportDir, userId)
            targetDir.mkdirs()

            // 获取 ltp 源目录
            val ltpSourceDir = File(userThemeDir, "ltp")
            val hasLtp = ltpSourceDir.exists() && ltpSourceDir.isDirectory

            // 导出 theme 目录下的每个主题
            val themeSourceDir = File(userThemeDir, "theme")
            if (!themeSourceDir.exists() || !themeSourceDir.isDirectory) {
                Log.runtime(TAG, "✗ 主题导出失败: theme 目录不存在")
                showToast("主题导出失败: theme 目录不存在")
                return Pair(false, "theme 目录不存在")
            }

            val themeDirs = themeSourceDir.listFiles { file -> file.isDirectory }
            if (themeDirs == null || themeDirs.isEmpty()) {
                Log.runtime(TAG, "✗ 主题导出失败: 未找到主题目录")
                showToast("主题导出失败: 未找到主题目录")
                return Pair(false, "未找到主题目录")
            }

            var exportedCount = 0
            themeDirs.forEach { themeDir ->
                try {
                    val themeId = themeDir.name
                    val themeTargetDir = File(targetDir, themeId)

                    // 复制主题资源
                    copyDirectory(themeDir, themeTargetDir)

                    // 将 ltp 复制到主题目录中
                    if (hasLtp) {
                        val ltpTargetDir = File(themeTargetDir, "ltp")
                        copyDirectory(ltpSourceDir, ltpTargetDir)
                    }

                    Log.runtime(TAG, "✓ 已导出主题: $themeId")
                    exportedCount++
                } catch (e: Exception) {
                    Log.runtime(TAG, "✗ 导出主题失败 (${themeDir.name}): ${e.message}")
                }
            }

            if (exportedCount > 0) {
                val message = "✓ 主题导出成功\n已导出 $exportedCount 个主题"
                Log.runtime(TAG, message)
                showToast(message)
                return Pair(true, message)
            } else {
                val message = "✗ 主题导出失败: 没有成功导出任何主题"
                Log.runtime(TAG, message)
                showToast(message)
                return Pair(false, message)
            }
        } catch (e: Exception) {
            val message = "✗ 主题导出失败: ${e.message}"
            Log.runtime(TAG, message)
            showToast(message)
            return Pair(false, message)
        }
    }

    /**
     * 自动恢复主题（如果缺失）
     */
    fun restoreThemeIfMissing(userId: String) {
        try {
            val selectedThemeFile = File(EXTERNAL_STORAGE_PATH, SELECTED_THEME_FILE)
            if (!selectedThemeFile.exists()) return

            val selectedThemeId = selectedThemeFile.readText().trim()
            if (selectedThemeId.isEmpty()) return

            val userThemeDir = File(INTERNAL_STORAGE_PATH, userId)
            val themeBaseDir = File(userThemeDir, "theme")
            val targetThemeDir = File(themeBaseDir, selectedThemeId)

            // 检查目标主题目录是否存在，以及核心配置文件是否存在
            if (!targetThemeDir.exists() || !File(targetThemeDir, "theme_info.json").exists()) {
                Log.runtime(TAG, "⚠️ 当前选中主题文件丢失或不完整 ($selectedThemeId)，尝试自动恢复...")
                applyTheme(userId, userThemeDir, quiet = true)
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ 检查自动恢复时出错: ${e.message}")
        }
    }

    /**
     * 执行更新/应用操作
     *
     * @param userId 用户ID
     * @param userThemeDir 用户主题基础目录
     * @param quiet 是否静默模式（不显示 Toast）
     */
    fun applyTheme(userId: String, userThemeDir: File, quiet: Boolean = false) {
        try {
            val selectedThemeFile = File(EXTERNAL_STORAGE_PATH, SELECTED_THEME_FILE)
            if (!selectedThemeFile.exists()) {
                Log.runtime(TAG, "✗ 主题更新失败: 未选择主题")
                if (!quiet) showToast("主题更新失败: 未选择主题")
                return
            }

            val selectedThemeId = selectedThemeFile.readText().trim()
            if (selectedThemeId.isEmpty()) {
                Log.runtime(TAG, "✗ 主题更新失败: 主题ID为空")
                if (!quiet) showToast("主题更新失败: 主题ID为空")
                return
            }

            val sourceThemeDir = File(EXTERNAL_STORAGE_PATH, "$THEMES_FOLDER/$selectedThemeId")
            if (!sourceThemeDir.exists()) {
                Log.runtime(TAG, "✗ 主题更新失败: 主题不存在")
                if (!quiet) showToast("主题更新失败: 主题不存在")
                return
            }

            // 创建theme基础目录
            val themeBaseDir = File(userThemeDir, "theme")
            if (!themeBaseDir.exists()) {
                themeBaseDir.mkdirs()
            }

            // 步骤1: 删除旧的自定义主题（有 theme_info.json 的主题）
            try {
                val existingCustomThemes = themeBaseDir.listFiles { file ->
                    file.isDirectory && File(file, "theme_info.json").exists()
                }
                existingCustomThemes?.forEach { oldTheme ->
                    if (oldTheme.name != selectedThemeId) {
                        oldTheme.deleteRecursively()
                        Log.runtime(TAG, "✓ 已删除旧的自定义主题: ${oldTheme.name}")
                    }
                }
            } catch (e: Exception) {
                Log.runtime(TAG, "⚠️ 删除旧主题失败: ${e.message}")
            }

            //*** *** 步骤2: 导入新主题文件
            val targetThemeDir = File(themeBaseDir, selectedThemeId)
            try {
                if (targetThemeDir.exists()) {
                    targetThemeDir.deleteRecursively()
                }

                copyDirectory(sourceThemeDir, targetThemeDir)
                Log.runtime(TAG, "✓ 已复制主题文件到: ${targetThemeDir.absolutePath}")

                // 读取并更新 theme_info.json
                val themeInfoFile = File(targetThemeDir, "theme_info.json")
                if (!themeInfoFile.exists()) {
                    Log.runtime(TAG, "✗ theme_info.json 不存在")
                    if (!quiet) showToast("主题更新失败: theme_info.json 不存在")
                    return
                }

                // 手动解析 JSON，避免 Jackson 反序列化 Kotlin data class 的问题
                val json = JsonUtil.parseObject(themeInfoFile.readText(), Map::class.java) as Map<String, Any>
                val themeInfo = ThemeInfo.fromMap(json)
                val updatedThemeInfo = themeInfo.copy(
                    userId = userId,
                    cacheTime = System.currentTimeMillis() / 1000
                )
                themeInfoFile.writeText(JsonUtil.formatJson(updatedThemeInfo))

                Log.runtime(TAG, "✓ 主题信息:")
                Log.runtime(TAG, "   名称: ${updatedThemeInfo.name}")
                Log.runtime(TAG, "   主题ID: $selectedThemeId")
                Log.runtime(TAG, "   MD5: ${updatedThemeInfo.md5}")

                //*** *** 步骤3: 更新 SharedPreferences（key 为 cached_skin_info_v2<userId>，与 SCInnerManager 读取格式一致）
                val mergedJson = updateSharedPreferences(userId, selectedThemeId, updatedThemeInfo)

                if (mergedJson == null) {
                    Log.runtime(TAG, "✗ 主题缓存写入失败，终止主题应用")
                    showToast("主题更新失败: 缓存写入失败")
                    return
                }

                // 步骤4: 重载 SkinCenter 内存缓存并通知 UI 应用主题
                reloadAndNotifySkinCenter(mergedJson)

                Log.runtime(TAG, "✅ 主题切换成功: ${updatedThemeInfo.name}")
                showToast("主题已切换:${updatedThemeInfo.name}")

            } catch (e: Exception) {
                Log.runtime(TAG, "✗ 主题更新失败: ${e.message}")
                Log.printStackTrace(TAG, e)
                if (!quiet) showToast("主题更新失败: ${e.message}")
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 主题更新失败: ${e.message}")
            Log.printStackTrace(TAG, e)
            if (!quiet) showToast("主题更新失败: ${e.message}")
        }
    }

    /**
     * 复制目录
     *
     * 递归复制整个目录及其内容
     */
    private fun copyDirectory(source: File, destination: File) {
        if (!source.exists()) return

        if (source.isDirectory) {
            // 创建目标目录
            if (!destination.exists()) {
                destination.mkdirs()
            }

            // 复制所有子文件和子目录
            source.listFiles()?.forEach { file ->
                val destFile = File(destination, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    copyFile(file, destFile)
                }
            }
        } else {
            // 复制单个文件
            copyFile(source, destination)
        }
    }

    /**
     * 复制文件
     *
     * 将源文件复制到目标位置
     * 使用缓冲区优化大文件复制性能
     *
     * @throws Exception 复制失败时抛出异常
     */
    private fun copyFile(source: File, destination: File) {
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                // 使用 32KB 缓冲区，提升大文件复制性能
                input.copyTo(output, bufferSize = 32 * 1024)
            }
        }
    }

    /**
     * 显示 Toast 提示
     *
     * @param message 提示消息
     */
    private fun showToast(message: String) {
        try {
            val context = AppContext.getAppContext()
            if (context != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "Toast 显示失败: ${e.message}")
        }
    }

    /**
     * 更新 SharedPreferences
     *
     * 按 SCInnerManager 的读取格式写入：
     * - key: "cached_skin_info_v2" + userId（无分隔符直接拼接，见 SCCommonUtil.putString）
     * - value: 全场景合并 JSON {aptrip/atmospheric_aptrip/emoji/ltp/theme/widget}，只替换 theme 场景，不覆盖其它场景
     * - 另写 "cached_skin_theme" + userId = skinId
     *
     * @return 合并后的完整缓存 JSON 字符串（供内存手动填充兜底使用），失败返回 null
     */
    private fun updateSharedPreferences(userId: String, themeId: String, themeInfo: ThemeInfo): String? {
        try {
            val context = AppContext.getAppContext()
            if (context == null) {
                Log.runtime(TAG, "✗ 无法获取 Context")
                return null
            }

            val runtimeUserId = getRuntimeUserId()
            if (runtimeUserId == null) {
                Log.runtime(TAG, "✗ 获取支付宝运行时用户ID失败，终止主题缓存写入")
                return null
            }
            val effectiveUserId = runtimeUserId

            // 构造 theme 场景缓存条目（字段与官方缓存格式一致）
            val themeEntry = mapOf(
                "cacheTime" to (System.currentTimeMillis() / 1000),
                "diyExpiredTime" to 0L,
                "expireDate" to themeInfo.expireDate,
                "isDiySkin" to false,
                "materialId" to "",
                "md5" to themeInfo.md5,
                "name" to themeInfo.name,
                "skinId" to themeInfo.skinId,
                "skinType" to themeInfo.skinType,
                "usageScene" to "theme",
                "userId" to effectiveUserId,
                "userSkinId" to themeId,
                "versionLimit" to themeInfo.versionLimit
            )

            val prefs = context.getSharedPreferences("prefs_skincenter_file", android.content.Context.MODE_PRIVATE)
            val cacheKey = "cached_skin_info_v2$effectiveUserId"

            // 读取旧缓存并合并，避免冲掉 aptrip/ltp/widget 等其它场景
            val merged: LinkedHashMap<String, Any> = try {
                val existing = prefs.getString(cacheKey, null)
                if (!existing.isNullOrEmpty()) {
                    LinkedHashMap(JsonUtil.parseObject(existing, Map::class.java) as Map<String, Any>)
                } else {
                    LinkedHashMap()
                }
            } catch (e: Exception) {
                Log.runtime(TAG, "⚠️ 解析旧缓存失败，将只写入 theme 场景: ${e.message}")
                LinkedHashMap()
            }
            merged["theme"] = themeEntry

            val json = JsonUtil.formatJson(merged)
            prefs.edit()
                .putString(cacheKey, json)
                .putString("cached_skin_theme$effectiveUserId", themeInfo.skinId)
                .apply()

            Log.runtime(TAG, "✓ 已更新 SharedPreferences")
            Log.runtime(TAG, "   userSkinId: $themeId")
            Log.runtime(TAG, "   key: $cacheKey (场景: ${merged.keys.joinToString("/")})")
            Log.runtime(TAG, "   cached_skin_theme: ${themeInfo.skinId}")
            return json
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 更新 SharedPreferences 失败: ${e.message}")
            Log.printStackTrace(TAG, e)
            return null
        }
    }

    /**
     * 重载 SkinCenter 内存缓存并通知 UI 应用主题
     *
     * 链路对齐支付宝官方流程：
     * 1. 拿 SCInnerManager 单例（类型扫描定位，避免硬编码混淆名跨版本失效）
     * 2. 调读缓存方法（本版为 G()，旧版为 K()，即 readSkinInfoFromLocalCache）把 prefs 刷新进内存
     * 3. 读缓存失败时手动填充缓存 Map（等价实现）
     * 4. 调 notifyThemeSkin（本版为 C(String)，旧版为 G(String)）触发主题应用
     * 5. 兜底调稳定的 AntSkinRenderManager.notifySkinChanged() 刷新已注册的渲染视图
     *
     * @param mergedJson 合并后的完整缓存 JSON，手动填充兜底时使用，可为 null
     */
    private fun reloadAndNotifySkinCenter(mergedJson: String?) {
        val classLoader = AppContext.getClassLoader()
        if (classLoader == null) {
            Log.runtime(TAG, "✗ 无法获取 ClassLoader")
            return
        }

        val managerClass = try {
            classLoader.loadClass("com.alipay.mobile.skincenter.manage.SCInnerManager")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 无法加载 SCInnerManager: ${e.message}")
            return
        }

        // 1. 定位单例
        val instance = findSingletonInstance(managerClass)
        if (instance == null) {
            Log.runtime(TAG, "✗ 无法定位 SCInnerManager 单例")
            notifySkinChanged()
            return
        }

        // 2. 从 prefs 重载内存缓存（G()=本版 / K()=旧版 readSkinInfoFromLocalCache）
        val reloaded = listOf("G", "K").any { name ->
            runCatching {
                val method = managerClass.getDeclaredMethod(name)
                method.isAccessible = true
                method.invoke(instance)
                Log.runtime(TAG, "✓ 已重载内存缓存 ($name: readSkinInfoFromLocalCache)")
                true
            }.getOrElse { false }
        }

        // 3. 重载失败时手动填充缓存 Map（等价实现：解析 JSON 为 SCCacheInfoModel 后按场景写入）
        if (!reloaded && mergedJson != null) {
            if (manualPopulateCache(managerClass, instance, mergedJson)) {
                Log.runtime(TAG, "✓ 已手动填充内存缓存")
            } else {
                Log.runtime(TAG, "✗ 手动填充内存缓存失败")
            }
        } else if (!reloaded) {
            Log.runtime(TAG, "✗ 内存缓存重载失败且无合并 JSON 可用于兜底")
        }

        // 3.5 构建 SCMetaModel 渲染元数据（对齐官方 m(scene, model, true)，渲染层依赖它取新皮肤 meta）
        buildMetaModel(managerClass, instance, mergedJson)

        // 4. 触发主题应用（C(String)=本版 / G(String)=旧版 notifyThemeSkin）
        val notified = listOf("C", "G").any { name ->
            runCatching {
                val method = managerClass.getDeclaredMethod(name, String::class.java)
                method.isAccessible = true
                method.invoke(instance, null as Any?)
                Log.runtime(TAG, "✓ 已触发 notifyThemeSkin ($name)")
                true
            }.getOrElse { false }
        }
        if (!notified) {
            Log.runtime(TAG, "⚠️ 未找到 notifyThemeSkin 入口，降级为 notifySkinChanged")
        }

        // 5. 兜底：notifySkinChanged 名称未被混淆，刷新已注册的渲染视图
        notifySkinChanged()

        // 6. 兜底：手动发送 skinUpdated.theme 本地广播（官方 UI 换肤的最终触发器，C() 链路静默失败时由它驱动刷新）
        sendThemeUpdatedBroadcast(mergedJson)
    }

    /**
     * 构建 SCMetaModel 渲染元数据
     *
     * 对齐官方链路：SCInnerManager.m(scene, model, true) 在 notifyThemeSkin 之前调用，
     * 渲染层通过 SCMetaModel 获取新皮肤的 meta 信息，缺失会导致广播发出后渲染层拿不到资源路径
     *
     * @return 是否构建成功
     */
    private fun buildMetaModel(managerClass: Class<*>, instance: Any, mergedJson: String?): Boolean {
        return try {
            // theme 场景模型：优先取内存缓存，缺失时用宿主 fastjson 从合并 JSON 解析
            val themeModel = findCacheMap(managerClass, instance)?.get("theme")
                ?: parseThemeModelFromJson(mergedJson)
            if (themeModel == null) {
                Log.runtime(TAG, "✗ 无法获取 theme 场景缓存模型，跳过 SCMetaModel 构建")
                return false
            }
            val mMethod = managerClass.getDeclaredMethod(
                "m", String::class.java, themeModel.javaClass, java.lang.Boolean.TYPE
            )
            mMethod.isAccessible = true
            mMethod.invoke(instance, "theme", themeModel, true)
            Log.runtime(TAG, "✓ 已构建 SCMetaModel (m: theme)")
            true
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ 构建 SCMetaModel 失败（版本签名可能不同）: ${e.message}")
            false
        }
    }

    /**
     * 用宿主 fastjson 从合并缓存 JSON 解析 theme 场景的 SCCacheInfoModel
     */
    private fun parseThemeModelFromJson(mergedJson: String?): Any? {
        if (mergedJson.isNullOrEmpty()) return null
        return try {
            val classLoader = AppContext.getClassLoader() ?: return null
            val modelClass = SC_CACHE_MODEL_CLASSES.firstNotNullOfOrNull {
                runCatching { classLoader.loadClass(it) }.getOrNull()
            } ?: return null
            val parseMethod = classLoader.loadClass("com.alibaba.fastjson.JSON")
                .getMethod("parseObject", String::class.java, Class::class.java)
            val root = JsonUtil.parseObject(mergedJson, Map::class.java) as? Map<*, *> ?: return null
            val themeJson = root["theme"] ?: return null
            parseMethod.invoke(null, JsonUtil.formatJson(themeJson), modelClass)
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ 解析 theme 缓存模型失败: ${e.message}")
            null
        }
    }

    /**
     * 识别 SCInnerManager 中按场景存放缓存模型的 Map
     * （含 theme/ltp/aptrip 等场景键的 ConcurrentHashMap 实例字段）
     */
    private fun findCacheMap(managerClass: Class<*>, instance: Any): MutableMap<Any?, Any?>? {
        for (field in managerClass.declaredFields) {
            if (Modifier.isStatic(field.modifiers) || field.type != ConcurrentHashMap::class.java) continue
            field.isAccessible = true
            val map = field.get(instance) as? MutableMap<Any?, Any?> ?: continue
            if (map.keys.any { it is String && it in SCENE_KEYS }) return map
        }
        return null
    }

    /**
     * 手动发送 skinUpdated.theme 本地广播
     *
     * 对齐官方 c/a Runnable 的收尾动作：通过 LocalBroadcastManager 发送
     * "com.alipay.skincenter.skinUpdated.theme"（extras 与官方一致：
     * skinId/userSkinId/hasEnableSkin/materialId），UI 层接收后走
     * SkinStyleHelper 应用新皮肤，是换肤生效的最终触发器
     */
    private fun sendThemeUpdatedBroadcast(mergedJson: String?) {
        try {
            val root = mergedJson?.let {
                JsonUtil.parseObject(it, Map::class.java) as? Map<*, *>
            }?.get("theme") as? Map<*, *> ?: return

            val context = AppContext.getAppContext()
            val classLoader = AppContext.getClassLoader()
            if (context == null || classLoader == null) return

            val intent = android.content.Intent(ACTION_SKIN_THEME_UPDATED).apply {
                putExtra("skinId", root["skinId"] as? String ?: "")
                putExtra("userSkinId", root["userSkinId"] as? String ?: "")
                putExtra("hasEnableSkin", true)
                putExtra("materialId", root["materialId"] as? String ?: "")
            }

            val lbmClass = runCatching {
                classLoader.loadClass("android.support.v4.content.LocalBroadcastManager")
            }.getOrNull() ?: run {
                Log.runtime(TAG, "⚠️ 未找到 LocalBroadcastManager，跳过广播兜底")
                return
            }
            val lbm = lbmClass.getMethod("getInstance", android.content.Context::class.java)
                .invoke(null, context)
            lbmClass.getMethod("sendBroadcast", android.content.Intent::class.java)
                .invoke(lbm, intent)
            Log.runtime(TAG, "✓ 已发送 skinUpdated.theme 广播 (skinId=${root["skinId"]})")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 发送 skinUpdated.theme 广播失败: ${e.message}")
        }
    }

    /**
     * 反射获取支付宝运行时的当前用户ID（SCCommonUtil.getCurrentUserId()）
     *
     * 官方 v()/I() 会校验缓存 userId 与运行时用户一致，不一致则静默拒绝换肤，
     * 因此写入缓存的 userId 必须以运行时取值为准
     */
    private fun getRuntimeUserId(): String? {
        return try {
            val classLoader = AppContext.getClassLoader() ?: return null
            val utilClass = classLoader.loadClass("com.alipay.mobile.skincenter.util.SCCommonUtil")
            val method = utilClass.getMethod("getCurrentUserId")
            method.isAccessible = true
            method.invoke(null) as? String
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ 反射获取运行时 userId 失败: ${e.message}")
            null
        }
    }

    /**
     * 按类型定位 SCInnerManager 单例
     *
     * 优先扫静态字段（类型为自身），其次扫无参静态方法（返回类型为自身）
     */
    private fun findSingletonInstance(managerClass: Class<*>): Any? {
        try {
            for (field in managerClass.declaredFields) {
                if (Modifier.isStatic(field.modifiers) && field.type == managerClass) {
                    field.isAccessible = true
                    return field.get(null)
                }
            }
            for (method in managerClass.declaredMethods) {
                if (Modifier.isStatic(method.modifiers) && method.parameterTypes.isEmpty() && method.returnType == managerClass) {
                    return method.invoke(null)
                }
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 定位单例异常: ${e.message}")
        }
        return null
    }

    /**
     * 手动填充内存缓存（读缓存方法调用失败时的兜底）
     *
     * 1. 用宿主 fastjson 把各场景 JSON 解析为 SCCacheInfoModel
     * 2. 写入缓存 Map（识别依据：当前含 theme/ltp/aptrip 等场景键的 ConcurrentHashMap 实例字段）
     * 3. 置位"缓存已加载" AtomicBoolean 标记
     */
    private fun manualPopulateCache(managerClass: Class<*>, instance: Any, mergedJson: String): Boolean {
        return try {
            val classLoader = AppContext.getClassLoader() ?: return false
            val fastjsonClass = classLoader.loadClass("com.alibaba.fastjson.JSON")
            val parseMethod = fastjsonClass.getMethod("parseObject", String::class.java, Class::class.java)
            val modelClass = SC_CACHE_MODEL_CLASSES.firstNotNullOfOrNull { classLoader.loadClass(it) }
                ?: run {
                    Log.runtime(TAG, "✗ 无法加载 SCCacheInfoModel")
                    return false
                }

            val root = JsonUtil.parseObject(mergedJson, Map::class.java) as? Map<*, *> ?: return false
            val sceneModels = root.entries.mapNotNull { (scene, value) ->
                try {
                    scene as String to parseMethod.invoke(null, JsonUtil.formatJson(value), modelClass)
                } catch (e: Exception) {
                    Log.runtime(TAG, "⚠️ 解析场景 $scene 失败: ${e.message}")
                    null
                }
            }
            if (sceneModels.isEmpty()) return false

            // 识别缓存 Map：含场景键的 ConcurrentHashMap 实例字段
            val mapField = managerClass.declaredFields
                .filter { !Modifier.isStatic(it.modifiers) && it.type == ConcurrentHashMap::class.java }
                .firstOrNull { field ->
                    field.isAccessible = true
                    val map = field.get(instance) as? Map<*, *>
                    map != null && map.keys.any { it is String && it in SCENE_KEYS }
                } ?: managerClass.declaredFields.firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.type == ConcurrentHashMap::class.java
                }
            if (mapField == null) {
                Log.runtime(TAG, "✗ 未找到缓存 Map 字段")
                return false
            }
            mapField.isAccessible = true
            val cacheMap = mapField.get(instance) as? MutableMap<Any?, Any?> ?: return false
            sceneModels.forEach { (scene, model) -> cacheMap[scene] = model }

            // 置位"缓存已加载"标记
            managerClass.declaredFields
                .firstOrNull { !Modifier.isStatic(it.modifiers) && it.type == AtomicBoolean::class.java }
                ?.let { field ->
                    field.isAccessible = true
                    (field.get(instance) as? AtomicBoolean)?.set(true)
                }
            true
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 手动填充异常: ${e.message}")
            Log.printStackTrace(TAG, e)
            false
        }
    }

    /**
     * 通知皮肤更换
     *
     * 调用 AntSkinRenderManager.notifySkinChanged() 实现动态刷新
     */
    private fun notifySkinChanged() {
        try {
            val classLoader = AppContext.getClassLoader()
            if (classLoader == null) {
                Log.runtime(TAG, "✗ 无法获取 ClassLoader")
                return
            }

            val antSkinRenderManagerClass = classLoader.loadClass("com.alipay.mobile.skincenter.manage.AntSkinRenderManager")
            val notifySkinChangedMethod = antSkinRenderManagerClass.getDeclaredMethod("notifySkinChanged")
            notifySkinChangedMethod.invoke(null)

            Log.runtime(TAG, "✓ 已通知 UI 刷新")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 通知 UI 刷新失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

}
