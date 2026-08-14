package fansirsqi.xposed.sesame.hook.theme

import fansirsqi.xposed.sesame.hook.context.AppContext
import fansirsqi.xposed.sesame.ui.theme.ThemeMetadata
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap
import java.io.File

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
        get() = "${android.os.Environment.getExternalStorageDirectory().absolutePath}/Android/media/com.eg.android.AlipayGphone/000_HOHO_THEME_CENTER"

    // 主题文件夹路径
    private const val THEMES_FOLDER = "themes"
    private const val EXPORTED_THEMES_FOLDER = "exported_themes"
    private const val SELECTED_THEME_FILE = "selected_theme"

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
     * 立即直接删除主题缓存
     *
     * 响应 UI 层删除操作，由 IPC 广播触发
     */
    fun deleteThemeCacheDirectly(targetUserId: String? = null): Pair<Boolean, String> {
        return try {
            val userId = targetUserId ?: getCurrentUserId()
            if (userId == null) {
                return Pair(false, "无法获取用户ID")
            }
            val userThemeDir = File(INTERNAL_STORAGE_PATH, userId)
            if (!userThemeDir.exists()) {
                return Pair(false, "主题目录不存在: ${userThemeDir.absolutePath}")
            }
            userThemeDir.deleteRecursively()
            Log.runtime(TAG, "✓ 主题缓存已删除")
            Pair(true, "主题缓存已删除")
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
     * 执行更新操作
     *
     * 完整模拟支付宝内部切换主题的流程：
     * 1. 删除旧的自定义主题（可选）
     * 2. 导入新主题文件
     * 3. 更新 SharedPreferences
     * 4. 清除内存缓存
     * 5. 重新读取缓存
     * 6. 刷新 UI
     */
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

                //*** *** 步骤3: 更新 SharedPreferences
                updateSharedPreferences(userId, selectedThemeId, updatedThemeInfo)

                // 步骤4: 清除内存缓存 (没用到也成功)
                clearMemoryCache()

                // 步骤5: 重新读取缓存 (没用到也成功)
                reloadCache()

                //*** *** 步骤6: 刷新 UI
                notifySkinChanged()

                Log.runtime(TAG, "✅ 主题切换成功: ${updatedThemeInfo.name}")
                if (!quiet) showToast("主题已切换: ${updatedThemeInfo.name}")

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
     * 复制目录内容
     *
     * 将源目录的所有文件复制到目标目录（不包括子目录）
     */
    private fun copyDirectoryContents(source: File, destination: File) {
        if (!source.exists() || !source.isDirectory) {
            return
        }

        if (!destination.exists()) {
            destination.mkdirs()
        }

        source.listFiles()?.forEach { file ->
            if (file.isFile) {
                val destFile = File(destination, file.name)
                copyFile(file, destFile)
            }
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
     * 将主题信息写入 SharedPreferences，指向新主题
     */
    private fun updateSharedPreferences(userId: String, themeId: String, themeInfo: ThemeInfo) {
        try {
            val context = AppContext.getAppContext()
            if (context == null) {
                Log.runtime(TAG, "✗ 无法获取 Context")
                return
            }

            // 构造缓存信息
            val cacheInfo = mapOf(
                "theme" to mapOf(
                    "usageScene" to "theme",
                    "skinId" to themeInfo.skinId,
                    "userSkinId" to themeId,  // 关键：指向新主题目录
                    "userId" to userId,
                    "md5" to themeInfo.md5,
                    "appSquareMd5" to themeInfo.md5,
                    "cacheTime" to (themeInfo.cacheTime + 365 * 24 * 3600),
                    "versionLimit" to themeInfo.versionLimit,
                    "isDiySkin" to false,
                    "name" to themeInfo.name,
                    "expireDate" to themeInfo.expireDate,
                    "skinType" to themeInfo.skinType,
                    "materialId" to "",
                    "diyExpiredTime" to Long.MAX_VALUE
                )
            )

            // 序列化为 JSON
            val json = JsonUtil.formatJson(cacheInfo)

            // 使用 Android 标准 SharedPreferences API
            val prefs = context.getSharedPreferences("prefs_skincenter_file", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("cached_skin_info_v2#$userId", json)
                .apply()

            Log.runtime(TAG, "✓ 已更新 SharedPreferences")
            Log.runtime(TAG, "   userSkinId: $themeId")
            Log.runtime(TAG, "   key: cached_skin_info_v2#$userId")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 更新 SharedPreferences 失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 清除内存缓存
     *
     * 清除 SCInnerManager 的内存缓存 this.g 中的 theme 条目
     */
    private fun clearMemoryCache() {
        try {
            val classLoader = AppContext.getClassLoader()
            if (classLoader == null) {
                Log.runtime(TAG, "✗ 无法获取 ClassLoader")
                return
            }

            val scInnerManagerClass = classLoader.loadClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager"
            )

            // 获取单例实例（方法名是 m()，不是 getInstance()）
            val getInstanceMethod = scInnerManagerClass.getDeclaredMethod("m")
            val instance = getInstanceMethod.invoke(null)

            // 获取内存缓存 Map (字段名: g)
            val gField = scInnerManagerClass.getDeclaredField("g")
            gField.isAccessible = true
            val cacheMap = gField.get(instance) as? MutableMap<*, *>

            if (cacheMap != null) {
                // 清除 theme 缓存
                cacheMap.remove("theme")
                Log.runtime(TAG, "✓ 已清除内存缓存")
            } else {
                Log.runtime(TAG, "⚠️ 无法获取内存缓存 Map")
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 清除内存缓存失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 重新读取缓存
     *
     * 调用 SCInnerManager.K() 方法，从 SharedPreferences 重新加载缓存
     */
    private fun reloadCache() {
        try {
            val classLoader = AppContext.getClassLoader()
            if (classLoader == null) {
                Log.runtime(TAG, "✗ 无法获取 ClassLoader")
                return
            }

            val scInnerManagerClass = classLoader.loadClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager"
            )

            // 获取单例实例（方法名是 m()，不是 getInstance()）
            val getInstanceMethod = scInnerManagerClass.getDeclaredMethod("m")
            val instance = getInstanceMethod.invoke(null)

            // 调用 K() 方法重新读取缓存
            val kMethod = scInnerManagerClass.getDeclaredMethod("K")
            kMethod.invoke(instance)

            Log.runtime(TAG, "✓ 已重新读取缓存")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 重新读取缓存失败: ${e.message}")
            Log.printStackTrace(TAG, e)
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
