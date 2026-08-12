package fansirsqi.xposed.sesame.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 主题模块数据仓库
 *
 * 负责处理所有与文件系统相关的操作
 * 提供数据访问接口给 ViewModel 使用
 */
class ThemeRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(ThemeConstants.PREFS_NAME, Context.MODE_PRIVATE)


    /**
     * 执行主题操作
     *
     * 创建操作请求文件夹，操作将在支付宝启动时执行
     *
     * @param operation 操作类型
     * @return Pair<Boolean, String> 成功标志和提示消息
     */
    suspend fun executeOperation(operation: ThemeOperation): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 确保父目录存在
            File(ThemeConstants.EXTERNAL_STORAGE_PATH).mkdirs()

            // 先清理所有旧的操作文件夹，避免多个操作同时执行
            ThemeOperation.values().forEach { op ->
                val oldFile = File(op.filePath)
                if (oldFile.exists()) {
                    oldFile.deleteRecursively()
                }
            }

            // 创建当前操作请求文件夹
            val file = File(operation.filePath)
            file.mkdirs()

            // 根据操作类型返回不同的提示消息
            val message = when (operation) {
                ThemeOperation.EXPORT -> "导出请求已创建\n打开支付宝时自动执行\n导出路径: ${ThemeConstants.EXTERNAL_STORAGE_PATH}/${ThemeConstants.EXPORTED_THEMES_FOLDER}"
                ThemeOperation.DELETE -> "删除请求已创建\n打开支付宝时自动执行"
                ThemeOperation.UPDATE -> "更新请求已创建\n打开支付宝时自动执行"
            }

            Pair(true, message)
        } catch (e: Exception) {
            Pair(false, "操作失败: ${e.message}")
        }
    }

    /**
     * 获取操作的当前状态
     *
     * @param operation 操作类型
     * @return true=已启用，false=未启用
     */
    fun getOperationState(operation: ThemeOperation): Boolean {
        return File(operation.filePath).exists()
    }

    /**
     * 获取所有操作的状态
     */
    fun getAllOperationStates(): Map<ThemeOperation, Boolean> {
        return ThemeOperation.values().associateWith { getOperationState(it) }
    }

    /**
     * 立即导出主题
     *
     * 点击时直接尝试导出，无需等待或依赖去到特定页面
     *
     * @return Pair<Boolean, String> 成功标志和提示消息
     */
    suspend fun exportThemeNow(context: Context? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val directResult = fansirsqi.xposed.sesame.hook.theme.ThemeManager.exportThemesDirectly()
        if (directResult.first) {
            directResult
        } else {
            // 如果在宿主/UI进程由于进程权限无法直接读取，发送 IPC 广播触发支付宝 Hook 进程直接导出
            try {
                val ctx = context ?: fansirsqi.xposed.sesame.hook.context.AppContext.getAppContext()
                if (ctx != null) {
                    val intent = android.content.Intent("com.eg.android.AlipayGphone.sesame.exportTheme")
                    ctx.sendBroadcast(intent)
                    Pair(true, "⚡ 已发送 IPC 广播，触发主题即时导出")
                } else {
                    // TODO: 新方案验证通过后删除以下回退
                    // executeOperation(ThemeOperation.EXPORT)
                    Pair(false, "Context 不可用，导出失败")
                }
            } catch (e: Exception) {
                // TODO: 新方案验证通过后删除以下回退
                // executeOperation(ThemeOperation.EXPORT)
                Pair(false, "导出异常: ${e.message}")
            }
        }
    }

    /**
     * 删除主题（仅删除文件）
     *
     * @param themeId 主题ID
     * @return Pair<Boolean, String> 成功标志和提示消息
     */
    suspend fun deleteTheme(themeId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val themesDir = File(ThemeConstants.EXTERNAL_STORAGE_PATH, ThemeConstants.THEMES_FOLDER)
            val themeDir = File(themesDir, themeId)

            if (!themeDir.exists()) {
                return@withContext Pair(false, "主题不存在")
            }

            themeDir.deleteRecursively()
            return@withContext Pair(true, "主题已删除")
        } catch (e: Exception) {
            return@withContext Pair(false, "删除失败: ${e.message}")
        }
    }

    /**
     * 扫描可用主题
     *
     * 扫描外部存储的主题文件夹
     * 同时检查并生成theme_info.json（如果不存在）
     *
     * @return 主题信息列表
     */
    suspend fun scanAvailableThemes(): List<ThemeInfo> = withContext(Dispatchers.IO) {
        val themesDir = File(ThemeConstants.EXTERNAL_STORAGE_PATH, ThemeConstants.THEMES_FOLDER)
        if (!themesDir.exists() || !themesDir.isDirectory) {
            return@withContext emptyList()
        }

        val selectedThemeId = getSelectedThemeId()
        val themes = mutableListOf<ThemeInfo>()

        themesDir.listFiles()?.forEach { themeDir ->
            if (themeDir.isDirectory) {
                val themeId = themeDir.name

                // 检查并生成theme_info.json（如果不存在）
                ensureThemeInfoExists(themeDir, themeId)

                // 读取 meta.json
                val metadata = readThemeMetadata(themeDir)

                // 查找预览图
                val previewPath = findPreviewImage(themeDir)

                // 使用 description 作为主题名称，skinId 作为副标题
                val themeName = metadata.description.ifEmpty { themeId }
                val themeSubtitle = if (metadata.skinId.isNotEmpty()) {
                    "主题ID: ${metadata.skinId}"
                } else {
                    "文件夹: $themeId"
                }

                themes.add(
                    ThemeInfo(
                        themeId = themeId,
                        name = themeName,
                        description = themeSubtitle,
                        previewImagePath = previewPath,
                        isSelected = themeId == selectedThemeId
                    )
                )
            }
        }

        return@withContext themes
    }

    /**
     * 读取主题元数据
     *
     * 从 meta.json 文件读取主题信息
     *
     * @param themeDir 主题目录
     * @return 主题元数据
     */
    fun readThemeMetadata(themeDir: File): ThemeMetadata {
        val metaFile = File(themeDir, "meta.json")
        if (!metaFile.exists()) {
            Log.runtime("ThemeRepository", "meta.json 不存在: ${themeDir.absolutePath}")
            return ThemeMetadata()
        }

        return try {
            val jsonContent = metaFile.readText()
            val metadata = JsonUtil.parseObject(jsonContent, ThemeMetadata::class.java)
            Log.runtime("ThemeRepository", "成功解析主题元数据: ${metadata.description} (${metadata.skinId})")
            metadata
        } catch (e: Exception) {
            Log.error("ThemeRepository", "解析 meta.json 失败: $e")
            ThemeMetadata()
        }
    }

    /**
     * 查找预览图
     *
     * 查找主题目录中的预览图文件
     * 优先查找背景图片（无扩展名或带扩展名）
     *
     * @param themeDir 主题目录
     * @return 预览图路径，如果不存在则返回 null
     */
    private fun findPreviewImage(themeDir: File): String? {
        // 优先查找支付宝主题的常见背景图片（无扩展名）
        val backgroundCandidates = listOf(
            "home_navi_bg",        // 首页顶部背景图
            "me_navi_bg",          // 我的顶部背景图
            "tab_bar_bg_200",      // tabBar背景图
            "tab_bar_bg",          // tabBar背景图（备用）
            "background_16x9",     // 通用背景图
            "background_2x1",
            "background_4x3",
            "logo",
            "mask"
        )

        for (filename in backgroundCandidates) {
            val file = File(themeDir, filename)
            if (file.exists() && file.isFile) {
                return file.absolutePath
            }
        }

        // 备用：查找带扩展名的图片文件
        val imageCandidates = listOf(
            "home_navi_bg.png",
            "me_navi_bg.png",
            "tab_bar_bg_200.png",
            "tab_bar_bg.png",
            "preview.png",
            "preview.jpg"
        )

        for (filename in imageCandidates) {
            val file = File(themeDir, filename)
            if (file.exists()) {
                return file.absolutePath
            }
        }

        return null
    }

    /**
     * 获取当前选中的主题ID
     *
     * 从 selected_theme 文件读取
     *
     * @return 主题ID，如果没有选择则返回 null
     */
    fun getSelectedThemeId(): String? {
        val selectedFile = File(ThemeConstants.EXTERNAL_STORAGE_PATH, ThemeConstants.SELECTED_THEME_FILE)
        if (!selectedFile.exists()) {
            return null
        }

        return try {
            selectedFile.readText().trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 选择主题
     *
     * 将选择的主题ID保存到 selected_theme 文件
     *
     * @param themeId 主题ID
     */
    suspend fun selectTheme(themeId: String) = withContext(Dispatchers.IO) {
        val selectedFile = File(ThemeConstants.EXTERNAL_STORAGE_PATH, ThemeConstants.SELECTED_THEME_FILE)

        // 确保目录存在
        selectedFile.parentFile?.mkdirs()

        // 写入主题ID
        selectedFile.writeText(themeId)
    }

    /**
     * 从URI导入主题ZIP文件
     *
     * @param uri ZIP文件的URI
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun importThemeFromZip(uri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 获取原始文件名作为主题ID
            val originalFileName = getFileNameFromUri(uri)
            val themeId = originalFileName?.removeSuffix(".zip") ?: "theme_${System.currentTimeMillis()}"

            // 创建临时文件
            val tempZipFile = File(context.cacheDir, "temp_theme_${System.currentTimeMillis()}.zip")

            // 从URI复制到临时文件
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Pair(false, "无法读取文件")

            // 创建临时解压目录
            val tempExtractDir = File(context.cacheDir, "temp_theme_extract_${System.currentTimeMillis()}")
            tempExtractDir.mkdirs()

            // 解压ZIP文件
            try {
                val zipFile = net.lingala.zip4j.ZipFile(tempZipFile)
                zipFile.extractAll(tempExtractDir.absolutePath)
            } catch (e: Exception) {
                tempZipFile.delete()
                tempExtractDir.deleteRecursively()
                return@withContext Pair(false, "ZIP文件解压失败: ${e.message}")
            }

            // 验证主题结构并找到主题文件夹
            val themeFolder = findThemeFolder(tempExtractDir)
            if (themeFolder == null) {
                tempZipFile.delete()
                tempExtractDir.deleteRecursively()
                return@withContext Pair(false, "未找到有效的主题文件（需要包含meta.json）")
            }

            // 目标路径
            val targetDir = File(ThemeConstants.EXTERNAL_STORAGE_PATH, "${ThemeConstants.THEMES_FOLDER}/$themeId")

            // 如果目标已存在，删除
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }

            // 确保父目录存在
            targetDir.parentFile?.mkdirs()

            // 复制主题文件夹到目标位置
            copyDirectory(themeFolder, targetDir)

            // 清理临时文件
            tempZipFile.delete()
            tempExtractDir.deleteRecursively()

            Pair(true, "主题导入成功: $themeId")
        } catch (e: Exception) {
            Pair(false, "导入失败: ${e.message}")
        }
    }

    /**
     * 查找主题文件夹
     *
     * 在解压目录中递归查找包含主题文件的文件夹
     *
     * @param extractDir 解压目录
     * @return 主题文件夹，如果未找到则返回null
     */
    private fun findThemeFolder(extractDir: File): File? {
        // 检查解压目录本身是否是主题文件夹
        if (isValidThemeFolder(extractDir)) {
            return extractDir
        }

        // 递归检查所有子文件夹
        extractDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // 先检查当前子文件夹是否是有效主题文件夹
                if (isValidThemeFolder(file)) {
                    return file
                }
                // 如果不是，递归搜索其子目录
                val found = findThemeFolder(file)
                if (found != null) {
                    return found
                }
            }
        }

        return null
    }

    /**
     * 验证是否是有效的主题文件夹
     *
     * 检查文件夹是否包含meta.json
     *
     * @param folder 要检查的文件夹
     * @return true=有效，false=无效
     */
    private fun isValidThemeFolder(folder: File): Boolean {
        return File(folder, "meta.json").exists()
    }

    /**
     * 复制目录
     *
     * 递归复制整个目录及其内容
     *
     * @param source 源目录
     * @param destination 目标目录
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
     *
     * @param source 源文件
     * @param destination 目标文件
     */
    private fun copyFile(source: File, destination: File) {
        try {
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            // 忽略复制错误
        }
    }

    /**
     * 从URI导入主题目录
     *
     * @param uri 目录的URI
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun importThemeFromDirectory(uri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 使用 DocumentFile 访问目录
            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            if (documentFile == null || !documentFile.isDirectory) {
                return@withContext Pair(false, "无效的目录")
            }

            // 获取原始目录名作为主题ID
            val themeId = documentFile.name ?: "theme_${System.currentTimeMillis()}"

            // 创建临时目录
            val tempDir = File(context.cacheDir, "temp_theme_dir_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            // 复制目录内容到临时目录
            copyDocumentFileToLocal(documentFile, tempDir)

            // 验证主题结构
            val themeFolder = findThemeFolder(tempDir)
            if (themeFolder == null) {
                tempDir.deleteRecursively()
                return@withContext Pair(false, "未找到有效的主题文件（需要包含meta.json）")
            }

            // 目标路径
            val targetDir = File(ThemeConstants.EXTERNAL_STORAGE_PATH, "${ThemeConstants.THEMES_FOLDER}/$themeId")

            // 如果目标已存在，删除
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }

            // 确保父目录存在
            targetDir.parentFile?.mkdirs()

            // 复制主题文件夹到目标位置
            copyDirectory(themeFolder, targetDir)

            // 清理临时文件
            tempDir.deleteRecursively()

            Pair(true, "主题导入成功: $themeId")
        } catch (e: Exception) {
            Pair(false, "导入失败: ${e.message}")
        }
    }

    /**
     * 复制 DocumentFile 到本地目录
     */
    private fun copyDocumentFileToLocal(documentFile: androidx.documentfile.provider.DocumentFile, targetDir: File) {
        if (documentFile.isDirectory) {
            targetDir.mkdirs()
            documentFile.listFiles().forEach { child ->
                val childTarget = File(targetDir, child.name ?: return@forEach)
                copyDocumentFileToLocal(child, childTarget)
            }
        } else {
            context.contentResolver.openInputStream(documentFile.uri)?.use { input ->
                targetDir.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    /**
     * 从URI获取文件名
     *
     * @param uri 文件URI
     * @return 文件名，如果无法获取则返回null
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 确保theme_info.json存在
     *
     * 检查主题目录中是否有theme_info.json，如果没有则生成
     *
     * @param themeDir 主题目录
     * @param themeId 主题ID
     */
    private fun ensureThemeInfoExists(themeDir: File, themeId: String) {
        try {
            val themeInfoFile = File(themeDir, "theme_info.json")

            // 如果已存在，不需要重新生成
            if (themeInfoFile.exists()) {
                return
            }

            // 读取meta.json
            val metadata = readThemeMetadata(themeDir)

            // 计算MD5
            val md5 = calculateThemeMd5(themeDir)

            // 生成过期日期（100年后）
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.YEAR, 100)
            val expireDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(calendar.time)

            // 生成缓存时间（当前时间戳，秒）
            val cacheTime = System.currentTimeMillis() / 1000

            // 创建ThemeInfo对象（Hook层的）
            val themeInfo = fansirsqi.xposed.sesame.hook.theme.ThemeInfo(
                themeId = themeId,
                skinId = metadata.skinId.ifEmpty { themeId },
                userSkinId = themeId,
                userId = "",  // 在扫描时不知道userId，更新时会填充
                md5 = md5,
                appSquareMd5 = md5,
                cacheTime = cacheTime,
                expireDate = expireDate,
                diyExpiredTime = 0,
                versionLimit = "10.8.20.0000",
                skinType = "INST_UNLIMITED",
                name = metadata.description.ifEmpty { themeId },
                materialId = "",
                isDiySkin = false,
                usageScene = "theme"
            )

            // 保存到theme_info.json
            themeInfoFile.writeText(fansirsqi.xposed.sesame.util.JsonUtil.formatJson(themeInfo))
        } catch (e: Exception) {
            Log.error("ThemeRepository", "生成theme_info.json失败: e")
        }
    }

    /**
     * 计算主题目录的MD5
     *
     * 遍历所有文件，计算组合MD5
     *
     * @param themeDir 主题目录
     * @return MD5字符串
     */
    private fun calculateThemeMd5(themeDir: File): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")

            // 遍历所有文件，按路径排序后计算MD5
            themeDir.walkTopDown()
                .filter { it.isFile && it.name != "theme_info.json" }  // 排除theme_info.json本身
                .sortedBy { it.absolutePath }
                .forEach { file ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            md.update(buffer, 0, bytesRead)
                        }
                    }
                }

            // 转换为十六进制字符串
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.error("ThemeRepository", "计算MD5失败: $e")
            // 返回一个基于主题目录名的固定哈希
            "THEME_${themeDir.name.hashCode().toString(16).padStart(32, '0')}"
        }
    }
}
