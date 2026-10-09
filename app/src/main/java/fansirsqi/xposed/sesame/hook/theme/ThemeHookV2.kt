package fansirsqi.xposed.sesame.hook.theme

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.context.AppContext
import fansirsqi.xposed.sesame.ui.theme.alipay.AlipayThemeMetadata
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap
import java.io.File
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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

    // "开关未开启"提示的进程内一次性标志，避免 boot 高频刷屏
    @Volatile
    private var disabledLogged = false

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
            Log.runtime(TAG, "❌ ClassLoader未初始化")
            return
        }

        if (!enabled) {
            // 进程内只提示一次：开关未开是 hooks 全部未安装的最常见原因
            if (!disabledLogged) {
                Log.runtime(TAG, "⛔ 主题Hook未启用（皮肤模块开关未开启），官方门检/校验不会被拦截")
                disabledLogged = true
            }
            isHooked = false
            return
        }

        if (isHooked) {
            //Log.runtime(TAG, "⚠️ 主题Hook已经应用")
            return
        }

        // 各 hook 独立安装：单个失败只记录 error，不影响其余 hook
        hookCacheRead(classLoader)
        hookMd5Check(classLoader)
        hookTimeCheck(classLoader)
        hookHasEnableSkin(classLoader)
        hookFilePath(classLoader)
        hookResourceLoad(classLoader)

        isHooked = true
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

            // 本版读缓存方法为 G()，旧版为 K()（readSkinInfoFromLocalCache），按版本探测
            val hookedName = listOf("G", "K").firstOrNull { name ->
                runCatching {
                    XposedHelpers.findAndHookMethod(
                        scInnerManagerClass,
                        name,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                injectCustomCache(classLoader, param.thisObject)
                            }
                        }
                    )
                    true
                }.getOrElse { false }
            }
            if (hookedName == null) {
                Log.runtime(TAG, "✗ Hook缓存读取失败: 未找到 G()/K() 方法（混淆名版本漂移）")
            } else {
                Log.runtime(TAG, "✓ Hook缓存读取成功 ($hookedName: readSkinInfoFromLocalCache)")
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook缓存读取异常: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 在 G()/K() 读缓存后注入动态主题条目到内存缓存
     */
    private fun injectCustomCache(classLoader: ClassLoader, manager: Any) {
        try {
            // 内存缓存 Map：动态扫描 ConcurrentHashMap 实例字段（本版字段名为 e，旧版为 g，不硬编码）
            val cacheMap = findCacheMapField(manager) ?: run {
                Log.runtime(TAG, "⚠️ 无法定位内存缓存Map字段（ConcurrentHashMap）")
                return
            }

            // 获取当前用户ID
            val currentUserId = getCurrentUserId(classLoader) ?: run {
                Log.runtime(TAG, "⚠️ 无法获取当前用户ID，跳过注入")
                return
            }

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

            // 持久化到磁盘：防止支付宝清理缓存后主题丢失需重新设置
            persistCacheToDisk(classLoader, cacheMap)

        } catch (e: Exception) {
            Log.runtime(TAG, "❌ 注入缓存失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 动态定位 SCInnerManager 中存放场景缓存模型的 ConcurrentHashMap 实例字段
     * （识别依据：含 theme/ltp/aptrip 等场景键，或为空但类型唯一匹配）
     */
    private fun findCacheMapField(manager: Any): MutableMap<String, Any>? {
        val candidates = mutableListOf<MutableMap<String, Any>>()
        for (field in manager.javaClass.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue
            if (field.type != ConcurrentHashMap::class.java) continue
            field.isAccessible = true
            val map = field.get(manager) as? MutableMap<String, Any> ?: continue
            if (map.keys.any { it in setOf("theme", "ltp", "aptrip", "emoji", "widget") }) return map
            candidates.add(map)
        }
        return candidates.firstOrNull()
    }

    // 缓存区
    @Volatile
    private var cachedThemeInfo: ThemeInfo? = null
    @Volatile
    private var cachedUserId: String? = null
    private var lastThemeFileModified: Long = 0

    /**
     * 持久化主题缓存到 SharedPreferences
     *
     * 支付宝可能周期性清理皮肤缓存（内存 Map g 与 cached_skin_info_v2 持久层），
     * 导致自定义主题丢失、需要重新设置。
     * 直接序列化实际注入到内存 Map g 的 SCCacheInfoModel（与内存内容严格一致），
     * 写回 prefs_skincenter_file 的 cached_skin_info_v2#userId，
     * 这样即使缓存被清理，下次 K() 重读持久层时主题信息依然存在。
     */
    private fun persistCacheToDisk(classLoader: ClassLoader, cacheMap: Map<String, Any>) {
        try {
            val customCache = cacheMap["theme"] ?: return
            val userId = getCurrentUserId(classLoader) ?: return

            // 直接从实际注入的 SCCacheInfoModel 读取字段，保证与内存内容一致
            val cacheInfo = mapOf(
                "theme" to mapOf(
                    "usageScene" to XposedHelpers.getObjectField(customCache, "usageScene"),
                    "skinId" to XposedHelpers.getObjectField(customCache, "skinId"),
                    "userSkinId" to XposedHelpers.getObjectField(customCache, "userSkinId"),
                    "userId" to XposedHelpers.getObjectField(customCache, "userId"),
                    "md5" to XposedHelpers.getObjectField(customCache, "md5"),
                    "appSquareMd5" to XposedHelpers.getObjectField(customCache, "appSquareMd5"),
                    "cacheTime" to XposedHelpers.getLongField(customCache, "cacheTime"),
                    "versionLimit" to XposedHelpers.getObjectField(customCache, "versionLimit"),
                    "isDiySkin" to XposedHelpers.getBooleanField(customCache, "isDiySkin"),
                    "name" to XposedHelpers.getObjectField(customCache, "name"),
                    "expireDate" to XposedHelpers.getObjectField(customCache, "expireDate"),
                    "skinType" to XposedHelpers.getObjectField(customCache, "skinType"),
                    "materialId" to XposedHelpers.getObjectField(customCache, "materialId"),
                    "diyExpiredTime" to XposedHelpers.getLongField(customCache, "diyExpiredTime")
                )
            )

            val context = AppContext.getAppContext() ?: return
            val prefs = context.getSharedPreferences(
                "prefs_skincenter_file",
                Context.MODE_PRIVATE
            )
            // 官方真实 key 为 "cached_skin_info_v2" + userId 无分隔符直拼（带 # 的 key 官方不读取）
            val cacheKey = "cached_skin_info_v2$userId"

            // 合并写入：只替换 theme 场景，保留 aptrip/ltp 等其他场景
            val themeValue = cacheInfo.getValue("theme")
            val merged: LinkedHashMap<String, Any> = try {
                val existing = prefs.getString(cacheKey, null)
                if (!existing.isNullOrEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    LinkedHashMap(JsonUtil.parseObject(existing, Map::class.java) as Map<String, Any>)
                } else {
                    LinkedHashMap()
                }
            } catch (e: Exception) {
                Log.runtime(TAG, "⚠️ 解析旧缓存失败，将只写入 theme 场景: ${e.message}")
                LinkedHashMap()
            }
            merged["theme"] = themeValue

            prefs.edit()
                .putString(cacheKey, JsonUtil.formatJson(merged))
                .apply()

            Log.runtime(TAG, "💾 已持久化主题缓存到 SharedPreferences (key=$cacheKey, 场景: ${merged.keys.joinToString("/")})")
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ 持久化主题缓存失败: ${e.message}")
        }
    }

    /**
     * 动态读取主题信息 - 增加内存缓存优化
     */
    private fun loadThemeInfo(userId: String): ThemeInfo? {
        try {
            val themeBaseDir = File(ThemeManager.INTERNAL_STORAGE_PATH, "$userId/theme")
            if (!themeBaseDir.exists()) themeBaseDir.mkdirs()

            // 1. 寻找主题目录
            val themeDirs = themeBaseDir.listFiles { it.isDirectory }
            if (themeDirs.isNullOrEmpty()) {
                Log.runtime(TAG, "✗ theme 目录下无任何主题目录: ${themeBaseDir.absolutePath}，尝试恢复后跳过注入")
                ThemeManager.restoreThemeIfMissing(userId)
                return null
            }

            val themeDir = themeDirs.filter { File(it, "theme_info.json").exists() }.maxByOrNull { it.lastModified() }
                ?: themeDirs[0]

            val themeInfoFile = File(themeDir, "theme_info.json")

            // 2. 检查内存缓存是否有效 (根据文件修改时间判断)
            val currentModified = if (themeInfoFile.exists()) themeInfoFile.lastModified() else 0
            if (cachedThemeInfo != null && lastThemeFileModified == currentModified && currentModified != 0L) {
                return cachedThemeInfo
            }

            // 3. 缓存失效，重新读取
            if (themeInfoFile.exists()) {
                try {
                    val json = JsonUtil.parseObject(themeInfoFile.readText(), Map::class.java) as Map<String, Any>
                    val themeInfo = ThemeInfo.fromMap(json)
                    
                    // 更新缓存
                    cachedThemeInfo = themeInfo
                    lastThemeFileModified = currentModified
                    
                    Log.runtime(TAG, "⚡ 主题配置已更新 (I/O): ${themeInfo.name}")
                    return themeInfo
                } catch (e: Exception) {
                    Log.runtime(TAG, "⚠️ 解析theme_info.json失败: ${e.message}")
                }
            }

            // 4. 回退方案 (不进行内存缓存，因为元数据可能不稳定)
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
                    JsonUtil.parseObject(metaFile.readText(), AlipayThemeMetadata::class.java)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            val themeId = themeDir.name
            val cacheTime = System.currentTimeMillis() / 1000

            // 生成过期日期（100年后）
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.YEAR, 100)
            val expireDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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
        // 回滚门拦截（关键）：SCConfigUtil.i()=isThemeSkinRollBack，读服务端 skin_center_theme_rollbackV2 配置，
        // 返回 true 时 hasEnableSkin 直接拒绝主题——本地数据再正确也会被否决，必须恒 false
        try {
            XposedHelpers.findAndHookMethod(
                "com.alipay.mobile.skincenter.util.SCConfigUtil",
                classLoader,
                "i",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return false
                    }
                }
            )
            Log.runtime(TAG, "✓ Hook回滚门成功 (SCConfigUtil.i: isThemeSkinRollBack→false)")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook回滚门失败: ${e.message}")
        }

        // MD5 校验方法（本版签名未知，按旧版 m(String,long) 探测，失败不阻塞其他 hook）
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
            Log.runtime(TAG, "✓ Hook MD5校验成功 (SCConfigUtil.m)")
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook MD5校验失败（本版可能无此签名，md5 校验需靠数据侧对齐）: ${e.message}")
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
            Log.runtime(TAG, "✗ Hook时间戳检查失败（本版可能无此签名）: ${e.message}")
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

            // 本版 hasEnableSkin 为 v(String, Map)，旧版为 y(String, Map)，按版本探测
            val hookedName = listOf("v", "y").firstOrNull { name ->
                runCatching {
                    XposedHelpers.findAndHookMethod(
                        scInnerManagerClass,
                        name,
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
                    true
                }.getOrElse { false }
            }
            if (hookedName == null) {
                Log.runtime(TAG, "✗ Hook hasEnableSkin失败: 未找到 v()/y() 方法（混淆名版本漂移）")
            } else {
                Log.runtime(TAG, "✓ Hook hasEnableSkin成功 ($hookedName: theme 场景恒 true)")
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook hasEnableSkin异常: ${e.message}")
            Log.printStackTrace(TAG, e)
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
            Log.runtime(TAG, "✗ Hook文件路径失败（本版方法名可能已漂移，渲染层将按官方路径规则解析）: ${e.message}")
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
            Log.runtime(TAG, "✗ Hook资源加载失败（本版可能无此签名）: ${e.message}")
        }
    }

    /**
     * 获取当前用户ID - 增加内存缓存优化
     */
    private fun getCurrentUserId(classLoader: ClassLoader): String? {
        // 1. 优先使用内存缓存
        if (!cachedUserId.isNullOrEmpty()) {
            return cachedUserId
        }

        try {
            // 2. 方案1：从 UserMap 获取
            try {
                val currentUid = UserMap.currentUid
                if (!currentUid.isNullOrEmpty()) {
                    cachedUserId = currentUid
                    return currentUid
                }
            } catch (e: Exception) {}

            // 3. 方案2：从支付宝内部工具获取
            try {
                val scCommonUtilClass = classLoader.loadClass("com.alipay.mobile.skincenter.util.SCCommonUtil")
                val userId = XposedHelpers.callStaticMethod(scCommonUtilClass, "getCurrentUserId") as? String
                if (!userId.isNullOrEmpty()) {
                    cachedUserId = userId
                    return userId
                }
            } catch (e: Exception) {}

            return null
        } catch (e: Exception) {
            return null
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
