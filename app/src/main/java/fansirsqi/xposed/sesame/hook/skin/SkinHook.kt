package fansirsqi.xposed.sesame.hook.skin

import android.os.Environment
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.context.AppContext
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 皮肤模块 Hook 管理器
 *
 * 负责注册和管理支付宝皮肤相关的 Xposed Hooks
 * 主要功能：
 * 1. 修改会员等级显示
 * 2. 加载自定义皮肤资源
 *
 * 注意：此类只 hook 必要的方法，避免与 ApplicationHook 冲突
 */
object SkinHook {
    private const val TAG = "SkinHook"

    // 存储路径常量
    private val EXTERNAL_STORAGE_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.eg.android.AlipayGphone/000_HOHO_ALIPAY_SKIN"
    private const val SKIN_DIR_IN_ALIPAY = "/data/data/com.eg.android.AlipayGphone/files/onsitepay_skin_dir/HOHO"

    // Hook 状态标记
    @Volatile
    private var hooked = false

    // 数据库更新标记（确保只更新一次）- 使用 AtomicBoolean 防止竞态条件
    private val isDbUpdated = AtomicBoolean(false)

    // 皮肤操作执行标记（确保同一时间只有一个线程在执行操作）
    private val isOperationRunning = AtomicBoolean(false)

    /**
     * 保存ClassLoader供后续使用
     */
    private var savedClassLoader: ClassLoader? = null

    /**
     * 初始化Hook系统
     *
     * @param classLoader 目标应用的ClassLoader
     */
    @JvmStatic
    fun setupHooks(classLoader: ClassLoader) {
        savedClassLoader = classLoader

        // 注意：此时配置文件还未加载，不能立即应用Hook
        // 实际的Hook应用会在BaseModel.boot()中进行
    }

    /**
     * 动态更新Hook开关状态
     *
     * @param enabled 是否启用皮肤模块
     */
    @JvmStatic
    fun updateHooks(enabled: Boolean) {
        val classLoader = savedClassLoader
        if (classLoader == null) {
            Log.error(TAG, "❌ ClassLoader未初始化，请先调用setupHooks()")
            return
        }

        //Log.runtime(TAG, "📝 更新皮肤模块Hook状态:")
        //Log.runtime(TAG, "  皮肤模块: ${if (enabled) "✅ 开启" else "⛔ 关闭"}")

        // 先卸载所有现有Hook
        unhook()

        // 根据开关状态重新Hook
        if (enabled) {
            try {

                // Hook 会员等级转换
                hookMemberGradeConversion(classLoader)

                // Hook 登录结果中的会员等级
                hookLoginResultMemberGrade(classLoader)

                // Hook Activity.onCreate 直接修改数据库
                hookActivityOnCreate(classLoader)

                // Hook 皮肤资源加载
                hookSkinResourceLoading(classLoader)

                hooked = true
            } catch (t: Throwable) {
                Log.runtime(TAG, "✗ 皮肤模块Hook注册失败")
                Log.printStackTrace(TAG, t)
            }
        } else {
            //Log.runtime(TAG, "  ⚠️ 皮肤模块已关闭")
        }
    }

    /**
     * 清理 Hooks
     *
     * 重置 hook 状态标记
     */
    @JvmStatic
    fun unhook() {
        hooked = false
    }

    /**
     * Hook 会员等级转换方法
     *
     * Hook: com.alipay.mobile.onsitepay9.utils.MergeMemberGradeEnum.convertMemberGrade
     * 功能：修改会员等级的显示
     */
    private fun hookMemberGradeConversion(classLoader: ClassLoader) {
        try {
            val memberGradeEnumClass = XposedHelpers.findClass(
                "com.alipay.mobile.onsitepay9.utils.MergeMemberGradeEnum",
                classLoader
            )

            // 添加防抖：记录上次修改时间，避免短时间内重复打印日志
            var lastLogTime = 0L

            XposedHelpers.findAndHookMethod(
                "com.alipay.mobile.onsitepay9.utils.MergeMemberGradeEnum",
                classLoader,
                "convertMemberGrade",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val currentGrade = getCurrentMemberGrade()
                        if (currentGrade != "original") {
                            val gradeField = when (currentGrade) {
                                "primary" -> "PRIMARY"
                                "golden" -> "GOLDEN"
                                "platinum" -> "PLATINUM"
                                "diamond" -> "DIAMOND"
                                else -> return
                            }

                            try {
                                val gradeValue = XposedHelpers.getStaticObjectField(
                                    memberGradeEnumClass,
                                    gradeField
                                )

                                // 检查原始返回值是否已经是目标等级
                                val originalResult = param.result
                                if (originalResult != null && originalResult.toString() == gradeField) {
                                    // 已经是目标等级，无需修改
                                    return
                                }

                                param.result = gradeValue

                                // 防抖：只在距离上次日志超过5秒时才打印
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastLogTime > 5000) {
                                    Log.runtime(TAG, "✓ 会员等级已修改: $currentGrade")
                                    lastLogTime = currentTime
                                }
                            } catch (e: Exception) {
                                Log.printStackTrace(TAG, e)
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 会员等级转换Hook注册失败")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * Hook 登录结果中的会员等级
     *
     * Hook: com.alipay.mobilegw.biz.shared.processer.login.UserLoginResult.getExtResAttrs
     * 功能：修改登录返回数据中的会员等级
     */
    private fun hookLoginResultMemberGrade(classLoader: ClassLoader) {
        try {
            // 添加防抖：记录上次修改时间
            var lastLogTime = 0L

            XposedHelpers.findAndHookMethod(
                "com.alipay.mobilegw.biz.shared.processer.login.UserLoginResult",
                classLoader,
                "getExtResAttrs",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val map = param.result as? MutableMap<String, String> ?: return

                        if (map.containsKey("memberGrade")) {
                            val currentGrade = getCurrentMemberGrade()
                            if (currentGrade != "original") {
                                // 检查当前等级是否已经是目标等级
                                val existingGrade = map["memberGrade"]
                                if (existingGrade == currentGrade) {
                                    return
                                }

                                map["memberGrade"] = currentGrade

                                // 防抖：只在距离上次日志超过5秒时才打印
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastLogTime > 5000) {
                                    Log.runtime(TAG, "✓ 登录数据会员等级已修改: $currentGrade")
                                    lastLogTime = currentTime
                                }
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 登录结果Hook注册失败")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * Hook 皮肤资源加载
     *
     * Hook: com.alipay.mobile.onsitepaystatic.ConfigUtilBiz.getFacePaySkinModel
     * 功能：加载自定义皮肤资源
     */
    private fun hookSkinResourceLoading(classLoader: ClassLoader) {
        try {
            val ospSkinModelClass = classLoader.loadClass(
                "com.alipay.mobile.onsitepaystatic.skin.OspSkinModel"
            )

            // 添加防抖：记录上次加载时间
            var lastLoadTime = 0L

            XposedHelpers.findAndHookMethod(
                "com.alipay.mobile.onsitepaystatic.ConfigUtilBiz",
                classLoader,
                "getFacePaySkinModel",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val skinDirInAlipay = File(SKIN_DIR_IN_ALIPAY)
                        val skinActivated = File(EXTERNAL_STORAGE_PATH, "actived")

                        // 处理皮肤操作（导出、删除、更新）
                        handleSkinOperations(skinDirInAlipay)

                        // 如果皮肤已激活且存在，则加载自定义皮肤
                        if (skinDirInAlipay.exists() && skinActivated.exists()) {
                            val availableSkins = searchAvailableSkins(SKIN_DIR_IN_ALIPAY)

                            if (availableSkins.isNotEmpty()) {
                                // 读取用户选择的皮肤，如果没有选择则使用第一个
                                val selectedSkin = getSelectedSkinName() ?: availableSkins.firstOrNull() ?: return

                                // 构建皮肤模型 JSON
                                val skinModelJson = """
                                    {
                                        "md5": "HOHO_MD5",
                                        "minWalletVersion": "10.2.23.0000",
                                        "outDirName": "HOHO/$selectedSkin",
                                        "skinId": "HOHO_CUSTOMIZED",
                                        "skinStyleId": "Sesame Skin",
                                        "userId": "HOHO"
                                    }
                                """.trimIndent()

                                try {
                                    val skinModel = JsonUtil.parseObject(skinModelJson, ospSkinModelClass)
                                    param.result = skinModel

                                    // 防抖：只在距离上次日志超过3秒时才打印
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastLoadTime > 3000) {
                                        Log.runtime(TAG, "✓ 自定义皮肤已应用: $selectedSkin")
                                        lastLoadTime = currentTime
                                    }
                                } catch (e: Exception) {
                                    Log.printStackTrace(TAG, e)
                                }
                            }
                        }
                    }
                }
            )

            //Log.runtime(TAG, "✓ 皮肤资源加载Hook注册成功")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 皮肤资源加载Hook注册失败")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 处理皮肤操作
     *
     * 处理导出、删除、更新等皮肤操作
     * 通过检查控制文件夹来执行相应的操作
     * 使用 AtomicBoolean 确保同一时间只有一个线程在执行操作
     *
     * @param skinDirInAlipay 支付宝内部的皮肤目录
     */
    private fun handleSkinOperations(skinDirInAlipay: File) {
        // 使用 compareAndSet 确保只有一个线程能执行操作
        // 如果已经有线程在执行（值为 true），则跳过
        if (!isOperationRunning.compareAndSet(false, true)) {
            return
        }

        try {
            val externalSkinDir = File(EXTERNAL_STORAGE_PATH)
            val exportDir = File(externalSkinDir, "export")
            val deleteDir = File(externalSkinDir, "delete")
            val updateDir = File(externalSkinDir, "update")

            // 处理导出操作（导出整个 onsitepay_skin_dir 目录）
            if (exportDir.exists()) {
                val alipaySkinsRoot = File("/data/data/com.eg.android.AlipayGphone/files/onsitepay_skin_dir")

                if (!alipaySkinsRoot.exists()) {
                    Log.runtime(TAG, "✗ 皮肤导出失败: 目录不存在")
                } else {
                    val exportTargetDir = File(externalSkinDir, "exported_skins")
                    if (exportTargetDir.exists()) {
                        exportTargetDir.deleteRecursively()
                    }
                    exportTargetDir.mkdirs()

                    var exportCount = 0
                    alipaySkinsRoot.listFiles()?.forEach { skinFolder ->
                        if (skinFolder.isDirectory) {
                            try {
                                val targetDir = File(exportTargetDir, skinFolder.name)
                                copyDirectory(skinFolder, targetDir)
                                exportCount++
                            } catch (e: Exception) {
                                // 静默失败，避免日志过多
                            }
                        }
                    }

                    if (exportCount > 0) {
                        Log.runtime(TAG, "✓ 皮肤已导出: $exportCount 个目录")
                    }
                }

                exportDir.deleteRecursively()
            }

            // 处理删除操作
            if (deleteDir.exists()) {
                if (skinDirInAlipay.exists()) {
                    skinDirInAlipay.deleteRecursively()
                    Log.runtime(TAG, "✓ 皮肤缓存已删除")
                }
                deleteDir.deleteRecursively()
            }

            // 处理更新操作
            if (updateDir.exists()) {
                if (skinDirInAlipay.exists()) {
                    skinDirInAlipay.deleteRecursively()
                }

                if (externalSkinDir.exists()) {
                    if (!skinDirInAlipay.exists()) {
                        skinDirInAlipay.mkdirs()
                    }

                    copyDirectoryContents(externalSkinDir, skinDirInAlipay)

                    if (skinDirInAlipay.exists()) {
                        Log.runtime(TAG, "✓ 皮肤缓存已更新")
                    } else {
                        Log.runtime(TAG, "✗ 皮肤更新失败")
                    }
                } else {
                    Log.runtime(TAG, "✗ 皮肤更新失败: 源目录不存在")
                }
                updateDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ 皮肤操作异常: ${e.message}")
            Log.printStackTrace(TAG, e)
        } finally {
            // 操作完成后重置标志，允许下次操作
            isOperationRunning.set(false)
        }
    }

    /**
     * 复制目录内容
     *
     * 将源目录的所有内容复制到目标目录（不包括源目录本身）
     * 这与原代码的 copy 方法行为一致
     *
     * @param source 源目录
     * @param destination 目标目录
     */
    private fun copyDirectoryContents(source: File, destination: File) {
        if (!source.exists() || !source.isDirectory) {
            return
        }

        if (!destination.exists()) {
            destination.mkdirs()
        }

        source.listFiles()?.forEach { file ->
            val destFile = File(destination, file.name)
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                copyFile(file, destFile)
            }
        }
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
            FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            // 静默失败
        }
    }

    /**
     * 搜索可用皮肤
     *
     * 扫描皮肤目录，查找所有可用的皮肤文件夹
     * 排除控制文件夹（update、actived、delete、level_*）
     *
     * @param path 皮肤目录路径
     * @return 可用皮肤列表
     */
    private fun searchAvailableSkins(path: String): List<String> {
        val resultList = mutableListOf<String>()
        val dir = File(path)

        if (!dir.exists() || !dir.isDirectory) {
            return resultList
        }

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val name = file.name
                // 排除控制文件夹
                if (name != "update" && 
                    name != "actived" && 
                    name != "delete" && 
                    !name.startsWith("level_")) {
                    resultList.add(name)
                }
            }
        }

        return resultList
    }

    /**
     * Hook Activity.onCreate 直接修改数据库
     *
     * Hook: android.app.Activity.onCreate
     * 功能：直接修改支付宝本地数据库中的会员等级，使变化立即生效
     */
    private fun hookActivityOnCreate(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                classLoader,
                "onCreate",
                android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val currentGrade = getCurrentMemberGrade()
                            if (currentGrade == "original") {
                                return
                            }

                            if (!isDbUpdated.compareAndSet(false, true)) {
                                return
                            }

                            val context = param.thisObject as? android.content.Context ?: return
                            val dbFile = context.getDatabasePath("alipayclient.db")

                            if (dbFile.exists()) {
                                try {
                                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                                        dbFile.path,
                                        null,
                                        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                                    )

                                    db.use {
                                        val cursor = it.rawQuery(
                                            "SELECT memberGrade FROM 'main'.'userinfo' LIMIT 1",
                                            null
                                        )

                                        var needUpdate = true
                                        cursor.use { c ->
                                            if (c.moveToFirst()) {
                                                val existingGrade = c.getString(0)
                                                if (existingGrade == currentGrade) {
                                                    needUpdate = false
                                                }
                                            }
                                        }

                                        if (needUpdate) {
                                            it.execSQL("UPDATE 'main'.'userinfo' SET 'memberGrade' = '$currentGrade'")
                                            Log.runtime(TAG, "✓ 数据库会员等级已更新: $currentGrade")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.runtime(TAG, "✗ 数据库更新失败: ${e.message}")
                                    isDbUpdated.set(false)
                                }
                            } else {
                                isDbUpdated.set(false)
                            }
                        } catch (e: Exception) {
                            Log.printStackTrace(TAG, e)
                            isDbUpdated.set(false)
                        }
                    }
                }
            )

            //Log.runtime(TAG, "✓ Activity.onCreate Hook注册成功")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Activity.onCreate Hook注册失败")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 获取当前会员等级
     *
     * 通过检查文件系统中的 level_ 文件夹来确定当前选中的会员等级
     *
     * @return 会员等级标识（primary/golden/platinum/diamond/original）
     */
    private fun getCurrentMemberGrade(): String {
        val grades = arrayOf("primary", "golden", "platinum", "diamond")

        for (grade in grades) {
            val folder = File(EXTERNAL_STORAGE_PATH, "level_$grade")
            if (folder.exists()) {
                return grade
            }
        }

        return "original"
    }

    /**
     * 获取用户选择的皮肤名称
     *
     * 从 selected_skin 文件读取用户选择的皮肤
     *
     * @return 皮肤名称，如果没有选择则返回 null
     */
    private fun getSelectedSkinName(): String? {
        return try {
            val selectedFile = File(EXTERNAL_STORAGE_PATH, "selected_skin")
            if (selectedFile.exists()) {
                selectedFile.readText().trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            null
        }
    }
}
