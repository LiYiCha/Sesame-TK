package fansirsqi.xposed.sesame.hook.theme

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.ui.theme.ThemeMetadata
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import java.io.File

/**
 * 主题Hook管理器 V2 - 动态版本
 *
 * 完整的动态主题替换方案，解决以下问题：
 * 1. MD5校验失败导致主题被重新下载
 * 2. 缓存时间过期导致主题失效
 * 3. 内存缓存未更新需要重启
 * 4. 主题只能显示一段时间
 *
 * 核心改进：
 * - 不再使用固定常量，而是动态读取主题信息
 * - 从ThemeManager预处理的theme_info.json读取真实数据
 * - 计算真实的MD5，而不是假的
 * - 支持持久化到磁盘（可选）
 *
 * @author fansirsqi
 */
object ThemeHookV2 {

    private const val TAG = "ThemeHookV2"

    // Hook状态
    @Volatile
    private var isHooked = false

    // 保存ClassLoader
    private var savedClassLoader: ClassLoader? = null

    /**
     * 初始化Hook系统
     */
    @JvmStatic
    fun setupHooks(classLoader: ClassLoader) {
        savedClassLoader = classLoader
    }

    /**
     * 应用所有主题Hook
     */
    @JvmStatic
    fun applyHooks(enabled: Boolean) {
        val classLoader = savedClassLoader
        if (classLoader == null) {
            Log.error(TAG, "❌ ClassLoader未初始化")
            return
        }

        if (!enabled) {
            Log.runtime(TAG, "⛔ 主题Hook已关闭")
            isHooked = false
            return
        }

        if (isHooked) {
            Log.runtime(TAG, "⚠️ 主题Hook已经应用")
            return
        }

        try {
            //Log.runtime(TAG, "🎨 开始应用主题Hook...")

            // 1. Hook缓存读取 - 注入动态主题信息
            hookCacheRead(classLoader)

            // 2. Hook MD5校验 - 绕过MD5验证
            hookMd5Check(classLoader)

            // 3. Hook时间戳检查 - 防止缓存过期
            hookTimeCheck(classLoader)

            // 4. Hook hasEnableSkin - 强制启用主题
            hookHasEnableSkin(classLoader)

            // 5. Hook文件路径 - 指向自定义主题目录
            hookFilePath(classLoader)

            // 6. Hook资源加载 - 确保加载自定义资源
            hookResourceLoad(classLoader)

            isHooked = true
            Log.runtime(TAG, "✅ 主题Hook应用成功")

        } catch (e: Exception) {
            Log.runtime(TAG, "❌ 主题Hook应用失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * Hook 1: 缓存读取（动态版本）
     *
     * Hook: SCInnerManager.K() - readSkinInfoFromLocalCache
     * 目的：在读取缓存后，注入动态主题信息到内存缓存
     */
    private fun hookCacheRead(classLoader: ClassLoader) {
        try {
            val scInnerManagerClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                scInnerManagerClass,
                "K", // readSkinInfoFromLocalCache方法
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val manager = param.thisObject

                            // 获取内存缓存Map: Map<String, SCCacheInfoModel> g
                            val cacheMap = XposedHelpers.getObjectField(manager, "g") as? MutableMap<String, Any>
                            if (cacheMap == null) {
                                Log.runtime(TAG, "⚠️ 无法获取缓存Map")
                                return
                            }

                            // 获取当前用户ID
                            val currentUserId = getCurrentUserId(classLoader) ?: return

                            // 动态读取主题信息
                            val themeInfo = loadThemeInfo(currentUserId)
                            if (themeInfo == null) {
                                Log.runtime(TAG, "⚠️ 未找到主题信息，跳过注入")
                                return
                            }

                            // 创建自定义主题缓存信息
                            val cacheInfoClass = XposedHelpers.findClass(
                                "com.alipay.mobile.skincenter.model.SCCacheInfoModel",
                                classLoader
                            )

                            val customCache = cacheInfoClass.newInstance()

                            // 使用动态读取的真实数据设置字段，并手动延长缓存有效期防止过期
                            XposedHelpers.setObjectField(customCache, "usageScene", themeInfo.usageScene)
                            XposedHelpers.setObjectField(customCache, "skinId", themeInfo.skinId)
                            XposedHelpers.setObjectField(customCache, "userSkinId", themeInfo.userSkinId)
                            XposedHelpers.setObjectField(customCache, "userId", themeInfo.userId)
                            XposedHelpers.setObjectField(customCache, "md5", themeInfo.md5)
                            XposedHelpers.setObjectField(customCache, "appSquareMd5", themeInfo.appSquareMd5)
                            // 加上10年的有效时间，防止缓存过期被自动清除
                            XposedHelpers.setLongField(customCache, "cacheTime", themeInfo.cacheTime + 10L * 365 * 24 * 3600)
                            XposedHelpers.setObjectField(customCache, "versionLimit", themeInfo.versionLimit)
                            XposedHelpers.setBooleanField(customCache, "isDiySkin", themeInfo.isDiySkin)
                            XposedHelpers.setObjectField(customCache, "name", themeInfo.name)
                            XposedHelpers.setObjectField(customCache, "expireDate", themeInfo.expireDate)
                            XposedHelpers.setObjectField(customCache, "skinType", themeInfo.skinType)
                            XposedHelpers.setObjectField(customCache, "materialId", themeInfo.materialId)
                            // 设置自定义过期时间为最大值
                            XposedHelpers.setLongField(customCache, "diyExpiredTime", Long.MAX_VALUE)

                            // 注入到内存缓存
                            cacheMap["theme"] = customCache

                            Log.runtime(TAG, "✅ 已注入动态主题缓存: ${themeInfo.name}")
                            Log.runtime(TAG, "   主题ID: ${themeInfo.themeId}")
                            Log.runtime(TAG, "   皮肤ID: ${themeInfo.skinId}")
                            Log.runtime(TAG, "   MD5: ${themeInfo.md5}")

                            // 可选：持久化到磁盘
                            // persistCacheToDisk(classLoader, cacheMap)

                        } catch (e: Exception) {
                            Log.runtime(TAG, "❌ 注入缓存失败: ${e.message}")
                            Log.printStackTrace(TAG, e)
                        }
                    }
                }
            )

            Log.runtime(TAG, "✓ Hook缓存读取成功")

        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook缓存读取失败: ${e.message}")
            throw e
        }
    }

    /**
     * 动态读取主题信息
     *
     * 优先从theme_info.json读取预处理的数据
     * 如果不存在，回退到动态读取meta.json
     *
     * @param userId 用户ID
     * @return 主题信息，如果未找到则返回null
     */
    private fun loadThemeInfo(userId: String): ThemeInfo? {
        try {
            // 1. 找到主题目录
            val themeBaseDir = File("/data/data/com.eg.android.AlipayGphone/files/skin_center_dir/$userId/theme")
            if (!themeBaseDir.exists()) {
                Log.runtime(TAG, "⚠️ 主题目录不存在: ${themeBaseDir.absolutePath}")
                return null
            }

            // 2. 查找所有主题目录
            val themeDirs = themeBaseDir.listFiles { it.isDirectory }
            if (themeDirs == null || themeDirs.isEmpty()) {
                Log.runtime(TAG, "⚠️ 未找到主题目录")
                return null
            }

            // 3. 优先查找有 theme_info.json 的主题（自定义主题）
            val customThemes = themeDirs.filter { File(it, "theme_info.json").exists() }

            val themeDir = if (customThemes.isNotEmpty()) {
                // 如果有多个自定义主题，取最新的（按修改时间排序）
                customThemes.maxByOrNull { it.lastModified() }!!
            } else {
                // 如果没有自定义主题，取第一个（可能是官方主题）
                themeDirs[0]
            }

            val themeInfoFile = File(themeDir, "theme_info.json")

            // 4. 优先读取预处理的theme_info.json
            if (themeInfoFile.exists()) {
                try {
                    // 手动解析 JSON，避免 Jackson 反序列化 Kotlin data class 的问题
                    val json = JsonUtil.parseObject(themeInfoFile.readText(), Map::class.java) as Map<String, Any>
                    val themeInfo = ThemeInfo.fromMap(json)
                    Log.runtime(TAG, "✓ 从theme_info.json加载主题信息: ${themeInfo.name}")
                    return themeInfo
                } catch (e: Exception) {
                    Log.runtime(TAG, "⚠️ 解析theme_info.json失败: ${e.message}")
                }
            }

            // 5. 回退：动态读取meta.json（兼容性）
            Log.runtime(TAG, "⚠️ theme_info.json不存在，回退到动态读取")
            return loadThemeInfoFromMeta(themeDir, userId)

        } catch (e: Exception) {
            Log.runtime(TAG, "❌ 加载主题信息失败: ${e.message}")
            return null
        }
    }

    /**
     * 从meta.json动态读取主题信息（回退方案）
     */
    private fun loadThemeInfoFromMeta(themeDir: File, userId: String): ThemeInfo? {
        try {
            val metaFile = File(themeDir, "meta.json")
            val metadata = if (metaFile.exists()) {
                try {
                    JsonUtil.parseObject(metaFile.readText(), ThemeMetadata::class.java)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            val themeId = themeDir.name
            val cacheTime = System.currentTimeMillis() / 1000

            // 生成过期日期（100年后）
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.YEAR, 100)
            val expireDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(calendar.time)

            return ThemeInfo(
                themeId = themeId,
                skinId = metadata?.skinId ?: "$themeId",
                userSkinId = themeId,
                userId = userId,
                md5 = themeId.hashCode().toString(16),
                appSquareMd5 = themeId.hashCode().toString(16),
                cacheTime = cacheTime,
                expireDate = expireDate,
                diyExpiredTime = 0,
                versionLimit = "10.8.20.0000",
                skinType = "INST_UNLIMITED",
                name = metadata?.description ?: themeId,
                materialId = "",
                isDiySkin = false,
                usageScene = "theme"
            )
        } catch (e: Exception) {
            Log.runtime(TAG, "❌ 从meta.json加载失败: ${e.message}")
            return null
        }
    }

    /**
     * Hook 2: MD5校验
     */
    private fun hookMd5Check(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.alipay.mobile.skincenter.util.SCConfigUtil",
                classLoader,
                "m",
                String::class.java,
                Long::class.javaPrimitiveType,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return false
                    }
                }
            )
            Log.runtime(TAG, "✓ Hook MD5校验成功")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook MD5校验失败: ${e.message}")
            throw e
        }
    }

    /**
     * Hook 3: 时间戳检查
     */
    private fun hookTimeCheck(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.alipay.mobile.skincenter.util.SCConfigUtil",
                classLoader,
                "l",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return false
                    }
                }
            )
            Log.runtime(TAG, "✓ Hook时间戳检查成功")
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ Hook时间戳检查失败（可能不影响功能）: ${e.message}")
        }
    }

    /**
     * Hook 4: hasEnableSkin检查
     */
    private fun hookHasEnableSkin(classLoader: ClassLoader) {
        try {
            val scInnerManagerClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                scInnerManagerClass,
                "y",
                String::class.java,
                Map::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val scene = param.args[0] as? String
                        if (scene == "theme") {
                            param.result = true
                        }
                    }
                }
            )
            Log.runtime(TAG, "✓ Hook hasEnableSkin成功")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook hasEnableSkin失败: ${e.message}")
            throw e
        }
    }

    /**
     * Hook 5: 文件路径
     */
    private fun hookFilePath(classLoader: ClassLoader) {
        try {
            val scInnerManagerClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                scInnerManagerClass,
                "q",
                File::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val scene = param.args[1] as? String
                        if (scene == "theme") {
                            // 动态读取userSkinId
                            val userId = getCurrentUserId(classLoader)
                            if (userId != null) {
                                val themeInfo = loadThemeInfo(userId)
                                if (themeInfo != null) {
                                    val baseDir = param.args[0] as? File
                                    if (baseDir != null) {
                                        val customThemeDir = File(baseDir, "theme/${themeInfo.userSkinId}")
                                        param.result = customThemeDir
                                    }
                                }
                            }
                        }
                    }
                }
            )
            Log.runtime(TAG, "✓ Hook文件路径成功")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook文件路径失败: ${e.message}")
            throw e
        }
    }

    /**
     * Hook 6: 资源加载
     */
    private fun hookResourceLoad(classLoader: ClassLoader) {
        try {
            val scMetaModelClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.model.SCMetaModel",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                scMetaModelClass,
                "loadResSync",
                String::class.java,
                Map::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val metaModel = param.thisObject
                            val scene = XposedHelpers.getObjectField(metaModel, "scene") as? String

                            if (scene == "theme") {
                                val userId = getCurrentUserId(classLoader)
                                if (userId != null) {
                                    val themeInfo = loadThemeInfo(userId)
                                    if (themeInfo != null) {
                                        XposedHelpers.setObjectField(metaModel, "skinId", themeInfo.skinId)
                                        XposedHelpers.setObjectField(metaModel, "userSkinId", themeInfo.userSkinId)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // 静默失败
                        }
                    }
                }
            )
            Log.runtime(TAG, "✓ Hook资源加载成功")
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ Hook资源加载失败（可能不影响功能）: ${e.message}")
        }
    }

    /**
     * 获取当前用户ID
     */
    private fun getCurrentUserId(classLoader: ClassLoader): String? {
        return try {
            // 方案1：直接访问 UserMap（在 Xposed 环境中，我们的类已经被注入）
            try {
                val currentUid = fansirsqi.xposed.sesame.util.maps.UserMap.currentUid
                if (!currentUid.isNullOrEmpty()) {
                    Log.runtime(TAG, "✓ 从 UserMap 获取用户ID: $currentUid")
                    return currentUid
                }
            } catch (e: Exception) {
                Log.runtime(TAG, "⚠️ 从 UserMap 获取用户ID失败: ${e.message}")
            }

            // 方案2：从支付宝的类中获取
            try {
                val scCommonUtilClass = classLoader.loadClass("com.alipay.mobile.skincenter.util.SCCommonUtil")
                val getCurrentUserIdMethod = scCommonUtilClass.getDeclaredMethod("getCurrentUserId")
                val userId = getCurrentUserIdMethod.invoke(null) as? String
                if (!userId.isNullOrEmpty()) {
                    Log.runtime(TAG, "✓ 从 SCCommonUtil 获取用户ID: $userId")
                    return userId
                }
            } catch (e: Exception) {
                Log.runtime(TAG, "⚠️ 从 SCCommonUtil 获取用户ID失败: ${e.message}")
            }

            // 方案3：从文件系统扫描（回退）
            val skinCenterDir = File("/data/data/com.eg.android.AlipayGphone/files/skin_center_dir")
            if (skinCenterDir.exists()) {
                val userDirs = skinCenterDir.listFiles { file ->
                    file.isDirectory && file.name.matches(Regex("^\\d+$"))
                }
                if (userDirs != null && userDirs.isNotEmpty()) {
                    val userId = userDirs[0].name
                    Log.runtime(TAG, "✓ 从文件系统扫描获取用户ID: $userId")
                    return userId
                }
            }

            Log.runtime(TAG, "⚠️ 所有方法都无法获取用户ID")
            null
        } catch (e: Exception) {
            Log.runtime(TAG, "❌ 获取用户ID失败: ${e.message}")
            Log.printStackTrace(TAG, e)
            null
        }
    }

    /**
     * 清理Hook
     */
    @JvmStatic
    fun unhook() {
        isHooked = false
    }
}
