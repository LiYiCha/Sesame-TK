package fansirsqi.xposed.sesame.ui.skin

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * 皮肤模块数据仓库
 *
 * 负责处理所有与文件系统、网络下载相关的操作
 * 提供数据访问接口给 ViewModel 使用
 */
class SkinRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(SkinConstants.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取是否首次运行
     */
    fun isFirstRun(): Boolean {
        return prefs.getBoolean(SkinConstants.KEY_FIRST_RUN, true)
    }

    /**
     * 标记已经不是首次运行
     */
    fun markNotFirstRun() {
        prefs.edit().putBoolean(SkinConstants.KEY_FIRST_RUN, false).apply()
    }

    /**
     * 获取当前选中的会员等级
     *
     * 通过检查文件系统中的 level_ 文件夹来确定
     */
    fun getCurrentMemberGrade(): MemberGrade {
        MemberGrade.values().forEach { grade ->
            if (grade.folderName != null) {
                val folder = File(SkinConstants.EXTERNAL_STORAGE_PATH, grade.folderName)
                if (folder.exists()) {
                    return grade
                }
            }
        }
        return MemberGrade.ORIGINAL
    }

    /**
     * 更新会员等级
     *
     * 删除所有现有的 level_ 文件夹，然后创建新的
     *
     * @param grade 新的会员等级
     */
    suspend fun updateMemberGrade(grade: MemberGrade) = withContext(Dispatchers.IO) {
        // 删除所有 level_ 文件夹
        MemberGrade.values().forEach { g ->
            if (g.folderName != null) {
                val folder = File(SkinConstants.EXTERNAL_STORAGE_PATH, g.folderName)
                if (folder.exists()) {
                    folder.deleteRecursively()
                }
            }
        }

        // 如果不是"原有"，创建新的 level_ 文件夹
        if (grade.folderName != null) {
            val newFolder = File(SkinConstants.EXTERNAL_STORAGE_PATH, grade.folderName)
            newFolder.mkdirs()
        }
    }

    /**
     * 执行皮肤操作
     *
     * 创建操作请求文件夹，操作将在支付宝加载皮肤时执行
     *
     * @param operation 操作类型
     * @return Pair<Boolean, String> 成功标志和提示消息
     */
    suspend fun executeOperation(operation: SkinOperation): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 确保父目录存在
            File(SkinConstants.EXTERNAL_STORAGE_PATH).mkdirs()

            // 先清理所有旧的操作文件夹，避免多个操作同时执行
            SkinOperation.values().forEach { op ->
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
                SkinOperation.EXPORT -> "导出请求已创建\n打开支付宝付款码时自动执行\n导出路径: ${SkinConstants.EXTERNAL_STORAGE_PATH}/exported_skins"
                SkinOperation.DELETE -> "删除请求已创建\n打开支付宝付款码时自动执行"
                SkinOperation.UPDATE -> "更新请求已创建\n打开支付宝付款码时自动执行"
                SkinOperation.ACTIVATE -> "激活状态已切换"
            }

            Pair(true, message)
        } catch (e: Exception) {
            Pair(false, "操作失败: ${e.message}")
        }
    }

    /**
     * 切换操作文件的状态
     *
     * 如果文件存在则删除，不存在则创建
     *
     * @param operation 操作类型
     * @return 操作后的状态（true=存在，false=不存在）
     */
    suspend fun toggleOperation(operation: SkinOperation): Boolean = withContext(Dispatchers.IO) {
        val file = File(operation.filePath)
        if (file.exists()) {
            file.deleteRecursively()
            false
        } else {
            // 确保父目录存在
            File(SkinConstants.EXTERNAL_STORAGE_PATH).mkdirs()
            file.mkdirs()
            true
        }
    }

    /**
     * 获取操作的当前状态
     *
     * @param operation 操作类型
     * @return true=已启用，false=未启用
     */
    fun getOperationState(operation: SkinOperation): Boolean {
        return File(operation.filePath).exists()
    }

    /**
     * 获取所有操作的状态
     */
    fun getAllOperationStates(): Map<SkinOperation, Boolean> {
        return SkinOperation.values().associateWith { getOperationState(it) }
    }

    /**
     * 检查资源包是否已安装
     */
    fun isResourceInstalled(): Boolean {
        val skinFolder = File(SkinConstants.EXTRACT_PATH, "000_HOHO_ALIPAY_SKIN")
        return skinFolder.exists() && skinFolder.isDirectory
    }

    /**
     * 下载并解压资源包
     *
     * 使用 Flow 来报告下载进度
     *
     * @return Flow<DownloadState> 下载状态流
     */
    fun downloadAndExtractResource(): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Downloading(0))

            // 下载文件
            val url = URL(SkinConstants.DOWNLOAD_URL)
            val connection = url.openConnection()
            connection.connect()

            val fileLength = connection.contentLength
            val tempFile = File(SkinConstants.EXTRACT_PATH, "temp.zip")

            // 确保目录存在
            tempFile.parentFile?.mkdirs()

            BufferedInputStream(url.openStream()).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val data = ByteArray(1024)
                    var total = 0L
                    var count: Int

                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        val progress = ((total * 100) / fileLength).toInt()
                        emit(DownloadState.Downloading(progress))
                        output.write(data, 0, count)
                    }
                }
            }

            // 解压文件
            emit(DownloadState.Downloading(100))
            unzip(tempFile.absolutePath, SkinConstants.EXTRACT_PATH)

            // 删除临时文件
            tempFile.delete()

            emit(DownloadState.Success)
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * 解压 ZIP 文件
     *
     * @param zipFilePath ZIP 文件路径
     * @param destDirectory 目标目录
     */
    private fun unzip(zipFilePath: String, destDirectory: String) {
        try {
            val zipFile = ZipFile(zipFilePath)
            zipFile.extractAll(destDirectory)
        } catch (e: Exception) {
            throw Exception("Failed to extract zip file: ${e.message}")
        }
    }

    /**
     * 获取资源文件夹路径
     */
    fun getResourceFolderPath(): String {
        return File(SkinConstants.EXTRACT_PATH, "000_HOHO_ALIPAY_SKIN").absolutePath
    }

    /**
     * 扫描可用皮肤
     *
     * 扫描皮肤目录，读取每个皮肤的信息
     *
     * @return 皮肤信息列表
     */
    suspend fun scanAvailableSkins(): List<SkinInfo> = withContext(Dispatchers.IO) {
        val skinDir = File(SkinConstants.EXTERNAL_STORAGE_PATH)
        if (!skinDir.exists() || !skinDir.isDirectory) {
            return@withContext emptyList()
        }

        val selectedSkin = getSelectedSkinName()
        val skins = mutableListOf<SkinInfo>()

        skinDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val name = file.name
                // 排除控制文件夹
                if (name != "update" && 
                    name != "actived" && 
                    name != "delete" && 
                    name != "export" &&
                    !name.startsWith("level_")) {
                    
                    // 读取 meta.json
                    val metadata = readSkinMetadata(file)
                    
                    // 查找预览图
                    val previewPath = findPreviewImage(file)
                    
                    skins.add(
                        SkinInfo(
                            name = name,
                            description = metadata.description.ifEmpty { name },
                            themeColor = metadata.themeColor,
                            previewImagePath = previewPath,
                            isSelected = name == selectedSkin
                        )
                    )
                }
            }
        }

        return@withContext skins
    }

    /**
     * 读取皮肤元数据
     *
     * 从 meta.json 文件读取皮肤的描述和主题色
     *
     * @param skinDir 皮肤目录
     * @return 皮肤元数据
     */
    fun readSkinMetadata(skinDir: File): SkinMetadata {
        val metaFile = File(skinDir, "meta.json")
        if (!metaFile.exists()) {
            return SkinMetadata()
        }

        return try {
            val jsonContent = metaFile.readText()
            fansirsqi.xposed.sesame.util.JsonUtil.parseObject(jsonContent, SkinMetadata::class.java)
        } catch (e: Exception) {
            SkinMetadata()
        }
    }

    /**
     * 查找预览图
     *
     * 查找皮肤目录中的预览图文件
     * 优先使用 background_2x1.png，其次是 background_16x9.png
     *
     * @param skinDir 皮肤目录
     * @return 预览图路径，如果不存在则返回 null
     */
    private fun findPreviewImage(skinDir: File): String? {
        val candidates = listOf(
            "background_2x1.png",
            "background_2x1",
            "background_16x9.png",
            "background_16x9"
        )

        for (filename in candidates) {
            val file = File(skinDir, filename)
            if (file.exists()) {
                return file.absolutePath
            }
        }

        return null
    }

    /**
     * 获取当前选中的皮肤名称
     *
     * 从 selected_skin 文件读取
     *
     * @return 皮肤名称，如果没有选择则返回 null
     */
    fun getSelectedSkinName(): String? {
        val selectedFile = File(SkinConstants.EXTERNAL_STORAGE_PATH, "selected_skin")
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
     * 选择皮肤
     *
     * 将选择的皮肤名称保存到 selected_skin 文件
     *
     * @param skinName 皮肤名称
     */
    suspend fun selectSkin(skinName: String) = withContext(Dispatchers.IO) {
        val selectedFile = File(SkinConstants.EXTERNAL_STORAGE_PATH, "selected_skin")

        // 确保目录存在
        selectedFile.parentFile?.mkdirs()

        // 写入皮肤名称
        selectedFile.writeText(skinName)
    }

    /**
     * 从URI导入皮肤ZIP文件
     *
     * @param uri ZIP文件的URI
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun importSkinFromZip(uri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 获取原始文件名作为皮肤名称
            val originalFileName = getFileNameFromUri(uri)
            val skinName = originalFileName?.removeSuffix(".zip") ?: "skin_${System.currentTimeMillis()}"

            // 创建临时文件
            val tempZipFile = File(context.cacheDir, "temp_skin_${System.currentTimeMillis()}.zip")

            // 从URI复制到临时文件
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Pair(false, "无法读取文件")

            // 创建临时解压目录
            val tempExtractDir = File(context.cacheDir, "temp_skin_extract_${System.currentTimeMillis()}")
            tempExtractDir.mkdirs()

            // 解压ZIP文件
            try {
                val zipFile = ZipFile(tempZipFile)
                zipFile.extractAll(tempExtractDir.absolutePath)
            } catch (e: Exception) {
                tempZipFile.delete()
                tempExtractDir.deleteRecursively()
                return@withContext Pair(false, "ZIP文件解压失败: ${e.message}")
            }

            // 验证皮肤结构并找到皮肤文件夹
            val skinFolder = findSkinFolder(tempExtractDir)
            if (skinFolder == null) {
                tempZipFile.delete()
                tempExtractDir.deleteRecursively()
                return@withContext Pair(false, "未找到有效的皮肤文件（需要包含meta.json或背景图片）")
            }

            // 目标路径
            val targetDir = File(SkinConstants.EXTERNAL_STORAGE_PATH, skinName)

            // 如果目标已存在，删除
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }

            // 确保父目录存在
            targetDir.parentFile?.mkdirs()

            // 复制皮肤文件夹到目标位置
            copyDirectory(skinFolder, targetDir)

            // 清理临时文件
            tempZipFile.delete()
            tempExtractDir.deleteRecursively()

            Pair(true, skinName)
        } catch (e: Exception) {
            Pair(false, "导入失败: ${e.message}")
        }
    }

    /**
     * 查找皮肤文件夹
     *
     * 在解压目录中递归查找包含皮肤文件的文件夹
     *
     * @param extractDir 解压目录
     * @return 皮肤文件夹，如果未找到则返回null
     */
    private fun findSkinFolder(extractDir: File): File? {
        // 检查解压目录本身是否是皮肤文件夹
        if (isValidSkinFolder(extractDir)) {
            return extractDir
        }

        // 递归检查所有子文件夹
        extractDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // 先检查当前子文件夹是否是有效皮肤文件夹
                if (isValidSkinFolder(file)) {
                    return file
                }
                // 如果不是，递归搜索其子目录
                val found = findSkinFolder(file)
                if (found != null) {
                    return found
                }
            }
        }

        return null
    }

    /**
     * 验证是否是有效的皮肤文件夹
     *
     * 检查文件夹是否包含meta.json或背景图片
     *
     * @param folder 要检查的文件夹
     * @return true=有效，false=无效
     */
    private fun isValidSkinFolder(folder: File): Boolean {
        // 检查是否有meta.json
        if (File(folder, "meta.json").exists()) {
            return true
        }

        // 检查是否有背景图片
        val backgroundFiles = listOf(
            "background_2x1.png",
            "background_16x9.png",
            "background_4x3.png"
        )

        return backgroundFiles.any { File(folder, it).exists() }
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
     * 从URI导入皮肤目录
     *
     * @param uri 目录的URI
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun importSkinFromDirectory(uri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 使用 DocumentFile 访问目录
            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            if (documentFile == null || !documentFile.isDirectory) {
                return@withContext Pair(false, "无效的目录")
            }

            // 获取原始目录名作为皮肤名称
            val skinName = documentFile.name ?: "skin_${System.currentTimeMillis()}"

            // 创建临时目录
            val tempDir = File(context.cacheDir, "temp_skin_dir_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            // 复制目录内容到临时目录
            copyDocumentFileToLocal(documentFile, tempDir)

            // 验证皮肤结构
            val skinFolder = findSkinFolder(tempDir)
            if (skinFolder == null) {
                tempDir.deleteRecursively()
                return@withContext Pair(false, "未找到有效的皮肤文件（需要包含meta.json或背景图片）")
            }

            // 目标路径
            val targetDir = File(SkinConstants.EXTERNAL_STORAGE_PATH, skinName)

            // 如果目标已存在，删除
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }

            // 确保父目录存在
            targetDir.parentFile?.mkdirs()

            // 复制皮肤文件夹到目标位置
            copyDirectory(skinFolder, targetDir)

            // 清理临时文件
            tempDir.deleteRecursively()

            Pair(true, "皮肤导入成功: $skinName")
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
}
