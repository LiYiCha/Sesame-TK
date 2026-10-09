package fansirsqi.xposed.sesame.ui.theme.alipay

import android.content.Context
import android.content.Intent
import android.net.Uri
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 主题包数据仓库：磁盘扫描、导入导出、槽位表构建与缓存。
 * 所有文件访问都在 IO 线程，界面层只拿结果。
 */
class AlipayThemeRepository(private val context: Context) {

    private val slotCache: MutableMap<String, AlipayThemeSlots> = ConcurrentHashMap()

    /**
     * 扫描外部存储里可用的主题包
     */
    suspend fun scanAvailablePacks(): List<AlipayThemePack> = withContext(Dispatchers.IO) {
        val themesDir = File(AlipayThemeConstants.EXTERNAL_STORAGE_PATH, AlipayThemeConstants.THEMES_FOLDER)
        if (!themesDir.exists() || !themesDir.isDirectory) return@withContext emptyList()

        val selectedPackId = getSelectedPackId()
        val packs = mutableListOf<AlipayThemePack>()

        themesDir.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val packId = dir.name

            ensureThemeInfoExists(dir, packId)

            val meta = readThemeMetadata(dir)
            val existingFiles = dir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            val slots = AlipayThemeSlots.from(dir, meta, existingFiles)
            slotCache[packId] = slots

            packs.add(
                AlipayThemePack(
                    packId = packId,
                    name = displayName(meta, packId),
                    subtitle = if (meta.skinId.isNotEmpty()) meta.skinId else packId,
                    skinId = meta.skinId,
                    coverPath = findPreviewImage(dir)?.absolutePath,
                    storagePath = dir.absolutePath,
                    isSelected = packId == selectedPackId,
                    slotTotal = slots.total,
                    slotDarkVariant = slots.darkVariant,
                    slotMissing = slots.missing
                )
            )
        }

        packs
    }

    /**
     * 列表卡片直接复用扫描时已建好的槽位表，避免组合期读盘
     */
    fun peekSlots(packId: String): AlipayThemeSlots? = slotCache[packId]

    /**
     * 读取单个主题包的槽位表；结果按包缓存，反复进出详情页不再重复读盘
     */
    suspend fun loadSlots(packId: String): AlipayThemeSlots? = withContext(Dispatchers.IO) {
        slotCache[packId]?.let { if (it.packDir.exists()) return@withContext it else slotCache.remove(packId) }

        val dir = themeDir(packId)
        if (!dir.exists() || !dir.isDirectory) return@withContext null

        val existing = dir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val slots = AlipayThemeSlots.from(dir, readThemeMetadata(dir), existing).let {
            it.copy(payment = loadPaymentSlots(dir))
        }
        slotCache[packId] = slots
        slots
    }

    fun readThemeMetadata(dir: File): AlipayThemeMetadata {
        val metaFile = File(dir, "meta.json")
        if (!metaFile.exists()) {
            Log.runtime(TAG, "meta.json 不存在: ${dir.absolutePath}")
            return AlipayThemeMetadata()
        }
        return try {
            JsonUtil.parseObject(metaFile.readText(), AlipayThemeMetadata::class.java) ?: AlipayThemeMetadata()
        } catch (e: Exception) {
            Log.error(TAG, "解析 meta.json 失败: ${e.message}")
            AlipayThemeMetadata()
        }
    }

    fun getSelectedPackId(): String? {
        val selectedFile = File(AlipayThemeConstants.EXTERNAL_STORAGE_PATH, AlipayThemeConstants.SELECTED_THEME_FILE)
        if (!selectedFile.exists()) return null
        return try {
            selectedFile.readText().trim()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun selectPack(packId: String) = withContext(Dispatchers.IO) {
        val selectedFile = File(AlipayThemeConstants.EXTERNAL_STORAGE_PATH, AlipayThemeConstants.SELECTED_THEME_FILE)
        selectedFile.parentFile?.mkdirs()
        selectedFile.writeText(packId)
    }

    /**
     * 选中并立即推送给支付宝进程：写选择文件只是第一步，不触发 apply 主题不会生效
     */
    suspend fun applyPack(packId: String): Pair<Boolean, String> {
        selectPack(packId)
        return executeThemeAction(AlipayThemeOperation.UPDATE)
    }

    /**
     * 主题操作走 IPC 广播：先试同进程直执行，失败则交给支付宝进程内的接收器
     */
    suspend fun executeThemeAction(operation: AlipayThemeOperation): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val directResult = when (operation) {
                AlipayThemeOperation.EXPORT -> fansirsqi.xposed.sesame.hook.theme.ThemeManager.exportThemesDirectly()
                AlipayThemeOperation.DELETE -> fansirsqi.xposed.sesame.hook.theme.ThemeManager.deleteThemeCacheDirectly()
                AlipayThemeOperation.UPDATE -> fansirsqi.xposed.sesame.hook.theme.ThemeManager.applyThemeDirectly()
            }

            if (directResult.first) {
                directResult
            } else {
                sendThemeActionBroadcast(operation)
            }
        }

    private fun sendThemeActionBroadcast(operation: AlipayThemeOperation): Pair<Boolean, String> = try {
        val intent = Intent(AlipayThemeConstants.THEME_OPERATION_ACTION)
            .putExtra(AlipayThemeConstants.EXTRA_OPERATION, operation.name)
        context.sendBroadcast(intent)
        // 广播是异步单向投递，此处只能确认「已发出」，不能声称已执行
        Pair(true, "已发送${operation.displayName}请求")
    } catch (e: Exception) {
        Log.error(TAG, "发送${operation.displayName}广播失败: ${e.message}")
        Pair(false, "${operation.displayName}失败: ${e.message}")
    }

    suspend fun deletePack(packId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val dir = themeDir(packId)
            if (!dir.exists()) return@withContext Pair(false, "主题不存在")
            val removed = dir.deleteRecursively() || !dir.exists()
            slotCache.remove(packId)
            if (removed) Pair(true, "主题已删除") else Pair(false, "删除失败：部分文件无法移除")
        } catch (e: Exception) {
            Pair(false, "删除失败: ${e.message}")
        }
    }

    suspend fun importFromZip(uri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "temp_theme_${System.currentTimeMillis()}.zip")
        val tempExtract = File(context.cacheDir, "temp_theme_extract_${System.currentTimeMillis()}")
        try {
            val fileName = getFileNameFromUri(uri)
            val packId = fileName?.removeSuffix(".zip")?.removeSuffix(".ZIP")
                ?: "theme_${System.currentTimeMillis()}"

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZip).use { output -> input.copyTo(output) }
            } ?: return@withContext Pair(false, "无法读取文件")

            tempExtract.mkdirs()
            try {
                net.lingala.zip4j.ZipFile(tempZip).extractAll(tempExtract.absolutePath)
            } catch (e: Exception) {
                return@withContext Pair(false, "ZIP 解压失败: ${e.message}")
            }

            installExtractedTheme(tempExtract, packId).also { clearTemp(tempZip, tempExtract) }
        } catch (e: Exception) {
            clearTemp(tempZip, tempExtract)
            Pair(false, "导入失败: ${e.message}")
        }
    }

    suspend fun importFromDirectory(uri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "temp_theme_dir_${System.currentTimeMillis()}")
        try {
            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                ?: return@withContext Pair(false, "无效的目录")
            if (!documentFile.isDirectory) return@withContext Pair(false, "无效的目录")

            val packId = documentFile.name ?: "theme_${System.currentTimeMillis()}"
            tempDir.mkdirs()
            copyDocumentTree(documentFile, tempDir)

            installExtractedTheme(tempDir, packId).also { tempDir.deleteRecursively() }
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            Pair(false, "导入失败: ${e.message}")
        }
    }

    /**
     * 校验目录结构并落盘；复制失败必须反馈给调用方，不能静默报成功
     */
    private fun installExtractedTheme(sourceRoot: File, packId: String): Pair<Boolean, String> {
        val themeFolder = findThemeFolder(sourceRoot)
            ?: return Pair(false, "未找到有效的主题文件（需要包含 meta.json）")

        val targetDir = File(
            File(AlipayThemeConstants.EXTERNAL_STORAGE_PATH, AlipayThemeConstants.THEMES_FOLDER), packId
        )
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.parentFile?.mkdirs()

        val failures = copyDirectory(themeFolder, targetDir)
        return if (failures.isEmpty()) {
            Pair(true, "主题导入成功: $packId")
        } else {
            Pair(false, "部分文件复制失败(${failures.size}): ${failures.take(3).joinToString()}")
        }
    }

    private fun findThemeFolder(dir: File): File? {
        if (isValidThemeFolder(dir)) return dir
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                if (isValidThemeFolder(child)) return child
                findThemeFolder(child)?.let { return it }
            }
        }
        return null
    }

    private fun isValidThemeFolder(dir: File): Boolean = File(dir, "meta.json").exists()

    private fun copyDirectory(source: File, destination: File): List<String> {
        val failures = mutableListOf<String>()
        if (!source.exists()) {
            failures.add(source.name)
            return failures
        }
        if (!source.isDirectory) {
            copyFile(source, destination).let { if (it != null) failures.add(it) }
            return failures
        }
        if (!destination.exists()) destination.mkdirs()
        source.listFiles()?.forEach { child ->
            failures += copyDirectory(child, File(destination, child.name))
        }
        return failures
    }

    private fun copyFile(source: File, destination: File): String? = try {
        source.inputStream().use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        null
    } catch (e: Exception) {
        Log.error(TAG, "复制文件失败 ${destination.name}: ${e.message}")
        destination.name
    }

    private fun copyDocumentTree(
        documentFile: androidx.documentfile.provider.DocumentFile,
        targetDir: File
    ) {
        if (documentFile.isDirectory) {
            targetDir.mkdirs()
            documentFile.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                copyDocumentTree(child, File(targetDir, name))
            }
        } else {
            context.contentResolver.openInputStream(documentFile.uri)?.use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun clearTemp(vararg files: File) = files.forEach { it.delete() }

    private fun themeDir(packId: String) = File(
        File(AlipayThemeConstants.EXTERNAL_STORAGE_PATH, AlipayThemeConstants.THEMES_FOLDER), packId
    )

    /**
     * 缺 theme_info.json 时按目录内容生成一份；applyTheme 读不到这个文件会直接中止换肤
     */
    private fun ensureThemeInfoExists(dir: File, packId: String) {
        val themeInfoFile = File(dir, "theme_info.json")
        if (themeInfoFile.exists()) return

        try {
            val meta = readThemeMetadata(dir)
            val md5 = calculateThemeMd5(dir)
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.YEAR, 100)
            val expireDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(calendar.time)

            val themeInfo = fansirsqi.xposed.sesame.hook.theme.ThemeInfo(
                themeId = packId,
                skinId = meta.skinId.ifEmpty { packId },
                userSkinId = packId,
                userId = "",
                md5 = md5,
                appSquareMd5 = md5,
                cacheTime = System.currentTimeMillis() / 1000,
                expireDate = expireDate,
                diyExpiredTime = 0,
                versionLimit = "10.8.20.0000",
                skinType = "INST_UNLIMITED",
                name = displayName(meta, packId),
                materialId = "",
                isDiySkin = false,
                usageScene = "theme"
            )
            themeInfoFile.writeText(JsonUtil.formatJson(themeInfo))
        } catch (e: Exception) {
            Log.error(TAG, "生成 theme_info.json 失败: ${e.message}")
        }
    }

    private fun calculateThemeMd5(dir: File): String = try {
        val md = java.security.MessageDigest.getInstance("MD5")
        dir.walkTopDown()
            .filter { it.isFile && it.name != "theme_info.json" }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        md.update(buffer, 0, read)
                    }
                }
            }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.error(TAG, "计算 MD5 失败: ${e.message}")
        "THEME_${dir.name.hashCode().toString(16).padStart(32, '0')}"
    }

    /**
     * meta.description 形如「30天-奶龙-日常生活-静态主题皮肤套装」，去掉时效与套装配饰词做展示名
     */
    private fun displayName(meta: AlipayThemeMetadata, fallback: String): String =
        meta.description
            .replace(Regex("^\\d+天-"), "")
            .replace("-静态主题皮肤套装", "")
            .replace("-皮肤套装", "")
            .ifBlank { fallback }

    private fun findPreviewImage(dir: File): File? {
        val candidates = listOf(
            "home_navi_bg", "me_navi_bg", "tab_bar_bg_200", "tab_bar_bg",
            "background_16x9", "background_2x1", "background_4x3", "logo", "mask",
            "home_navi_bg.png", "me_navi_bg.png", "preview.png", "preview.jpg"
        )
        return candidates.map { File(dir, it) }.firstOrNull { it.isFile }
    }

    private fun loadPaymentSlots(dir: File): PaymentCodeSlots? {
        val ltpDir = File(dir, "ltp")
        if (!ltpDir.isDirectory) return null

        val existing = ltpDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        if (existing.isEmpty()) return null

        var description: String? = null
        var startHex: String? = null
        var endHex: String? = null

        val metaFile = File(ltpDir, "meta.json")
        if (metaFile.exists()) {
            try {
                val ltpMeta = JsonUtil.parseObject(metaFile.readText(), LtpMeta::class.java)
                description = ltpMeta?.description
                ltpMeta?.resource?.forEach { res ->
                    res.gradient?.let {
                        startHex = it.start ?: startHex
                        endHex = it.end ?: endHex
                    }
                }
            } catch (e: Exception) {
                Log.runtime(TAG, "解析 ltp meta 失败: ${e.message}")
            }
        }

        return PaymentCodeSlots(
            description = description,
            logoFile = existing.takeFile("logo", ltpDir),
            backgroundFile = existing.takeFile("background_16x9", ltpDir)
                ?: existing.takeFile("background_2x1", ltpDir)
                ?: existing.takeFile("background_4x3", ltpDir),
            maskFile = existing.takeFile("mask", ltpDir),
            gradientStartHex = startHex,
            gradientEndHex = endHex
        )
    }

    private fun Set<String>.takeFile(name: String, dir: File): File? = if (contains(name)) File(dir, name) else null

    private companion object {
        const val TAG = "AlipayThemeRepository"
    }
}
