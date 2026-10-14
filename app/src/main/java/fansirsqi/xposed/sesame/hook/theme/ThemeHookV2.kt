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

    // 支付宝皮肤中心根目录名（SCInnerManager.s()：filesDir/skin_center_dir/<userId>）
    private const val SKIN_CENTER_DIR = "skin_center_dir"

    // SCInnerManager 中存放 SCCacheInfoModel 的实例字段名（本版 e，旧版 g）。
    // 注意：该类有 b/e/f/g 四个 ConcurrentHashMap，其中 b 是 SCMetaModel Map、key 同样是场景名，
    // 不能靠「含 theme 键」来猜，否则会把缓存写进 meta map。
    private val CACHE_MAP_FIELD_NAMES = listOf("e", "g")

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
        val critical = linkedMapOf(
            "缓存读取" to runCatching { hookCacheRead(classLoader) }.getOrDefault(false),
            "回滚门" to runCatching { hookRollbackGate(classLoader) }.getOrDefault(false),
            "hasEnableSkin" to runCatching { hookHasEnableSkin(classLoader) }.getOrDefault(false),
            "文件保护" to runCatching { hookProtectThemeFiles(classLoader) }.getOrDefault(false),
            "文件路径" to runCatching { hookFilePath(classLoader) }.getOrDefault(false),
            "资源加载" to runCatching { hookResourceLoad(classLoader) }.getOrDefault(false)
        )

        val failed = critical.filterValues { !it }
        if (failed.isEmpty()) {
            isHooked = true
            // 成功只打一行汇总，各 hook 内部成功日志降级 debug
            Log.runtime(TAG, "✓ 主题Hook安装完成: 缓存读取/回滚门/hasEnableSkin/文件保护/文件路径/资源加载")
            // 重绘通知不在这里调度：只有真正注入主题缓存后才补发（见 injectCustomCache）
        } else {
            Log.runtime(TAG, "❌ 主题Hook关键项${failed.size}/${critical.size}失败: ${failed.keys.joinToString("/")}")
            // 保险：打印官方类的真实方法清单到 debug 日志，便于比对混淆名漂移
            dumpSkinCenterMethods(classLoader)
            // 关键项全灭时保持未挂钩状态，允许下次配置同步时重试
            isHooked = failed.size != critical.size
        }
    }

    /**
     * 打印 skincenter 关键类的全部方法签名到 debug 日志（混淆名漂移排查用）
     */
    private fun dumpSkinCenterMethods(classLoader: ClassLoader) {
        val classNames = listOf(
            "com.alipay.mobile.skincenter.manage.SCInnerManager",
            "com.alipay.mobile.skincenter.util.SCConfigUtil",
            "com.alipay.mobile.skincenter.model.SCMetaModel"
        )
        for (className in classNames) {
            try {
                val clazz = classLoader.loadClass(className)
                val signatures = clazz.declaredMethods.map { m ->
                    val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                    val staticMark = if (Modifier.isStatic(m.modifiers)) "static " else ""
                    "${staticMark}${m.name}($params): ${m.returnType.simpleName}"
                }.sorted()
                Log.debug(TAG, "📋 方法清单 $className (${signatures.size} 个):")
                Log.debug(TAG, signatures.joinToString("\n"))
            } catch (t: Throwable) {
                Log.debug(TAG, "📋 方法清单 $className 加载失败: ${t.message}")
            }
        }
    }

    /**
     * Hook 1: 缓存读取（动态版本）
     *
     * Hook: SCInnerManager.K() - readSkinInfoFromLocalCache
     * 目的：在读取缓存后，注入动态主题信息到内存缓存
     */
    private fun hookCacheRead(classLoader: ClassLoader): Boolean {
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
            }
            return hookedName != null
        } catch (e: Exception) {
            Log.runtime(TAG, "✗ Hook缓存读取异常: ${e.message}")
            Log.printStackTrace(TAG, e)
            return false
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

            // 注入内容与内存中现有条目一致时跳过整个注入块（含 SP 重写），
            // 避免官方每次调 G() 都刷屏
            val effectiveUserId = ThemeManager.resolveEffectiveUserId(themeInfo.userId)
            val existing = cacheMap["theme"]
            if (existing != null && runCatching {
                    XposedHelpers.getObjectField(existing, "skinId") == themeInfo.skinId &&
                            XposedHelpers.getObjectField(existing, "userSkinId") == themeInfo.userSkinId &&
                            XposedHelpers.getObjectField(existing, "md5") == themeInfo.md5 &&
                            XposedHelpers.getObjectField(existing, "userId") == effectiveUserId
                }.getOrDefault(false)) {
                return
            }

            // 创建自定义主题缓存信息
            val cacheInfoClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.model.SCCacheInfoModel",
                classLoader
            )

            val customCache = cacheInfoClass.newInstance()

            // 使用动态读取的真实数据设置字段，并手动延长缓存有效期防止过期
            // userId 统一取运行时账号，避免与 persistCacheToDisk 写入持久层的值不一致
            XposedHelpers.setObjectField(customCache, "usageScene", themeInfo.usageScene)
            XposedHelpers.setObjectField(customCache, "skinId", themeInfo.skinId)
            XposedHelpers.setObjectField(customCache, "userSkinId", themeInfo.userSkinId)
            XposedHelpers.setObjectField(customCache, "userId", effectiveUserId)
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

            Log.runtime(TAG, "✅ 已注入动态主题缓存: ${themeInfo.name} (skinId=${themeInfo.skinId}, md5=${themeInfo.md5})")

            // 持久化到磁盘：防止支付宝清理缓存后主题丢失需重新设置
            persistCacheToDisk(classLoader, cacheMap)

            // 数据原本不存在（冷启动首次注入/SP被清后自愈），补发一次官方重绘通知
            // 去重命中时不会走到这里——此时缓存数据早已就位，视图拉样式自然可见主题，无需通知
            scheduleBootRenderNotify()

        } catch (e: Exception) {
            Log.runtime(TAG, "❌ 注入缓存失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    // 进程内仅补发一次重绘通知：仅真实注入成功后调度（去重命中不调度），双闸保证只执行一次
    @Volatile
    private var bootRenderNotified = false

    @Volatile
    private var bootNotifyRunning = false

    /**
     * 真实注入主题缓存后补发一次官方重绘通知。
     *
     * 官方皮肤解析是拉模式（SkinStyleHelper 视图查询时实时读缓存），冷启动数据就位后
     * 首次查询即见主题，因此通知只在「数据原本不存在、本次真实注入」时需要——
     * 补发 C() + notifySkinChanged + skinUpdated 广播，让缓存注入在首帧之后的场景也能刷新。
     *
     * 延时 2s 是为等首页完成首帧渲染；走后台线程避免在主线程做 reload 的文件 IO。
     * bootRenderNotified/bootNotifyRunning 双闸保证进程内仅执行一次，重复调用为零成本 no-op。
     */
    private fun scheduleBootRenderNotify() {
        if (bootRenderNotified || bootNotifyRunning) return
        bootNotifyRunning = true
        Thread {
            try {
                Thread.sleep(2000L)
                ThemeManager.notifyAppliedThemeForRender()
                bootRenderNotified = true
            } catch (t: Throwable) {
                Log.runtime(TAG, "⚠️ 冷启动补发主题重绘通知失败: ${t.message}")
            } finally {
                bootNotifyRunning = false
            }
        }.start()
    }

    /**
     * 动态定位 SCInnerManager 中存放场景缓存模型（SCCacheInfoModel）的 ConcurrentHashMap 实例字段
     *
     * 优先按已确认的字段名取（本版 e / 旧版 g）；字段名漂移时按 value 类型兜底，
     * 绝不能按「key 是否为场景名」判断——SCMetaModel 缓存 Map(b) 的 key 同样是场景名。
     */
    private fun findCacheMapField(manager: Any): MutableMap<String, Any>? {
        val fields = manager.javaClass.declaredFields

        for (name in CACHE_MAP_FIELD_NAMES) {
            val field = fields.firstOrNull {
                it.name == name && !Modifier.isStatic(it.modifiers) &&
                        it.type == ConcurrentHashMap::class.java
            } ?: continue
            field.isAccessible = true
            val map = field.get(manager) as? MutableMap<String, Any>
            if (map != null) return map
        }

        // 兜底：value 全部是 SCCacheInfoModel 的那个才是缓存 Map
        val cacheInfoClass = runCatching {
            manager.javaClass.classLoader
                .loadClass("com.alipay.mobile.skincenter.model.SCCacheInfoModel")
        }.getOrNull() ?: return null
        for (field in fields) {
            if (Modifier.isStatic(field.modifiers) || field.type != ConcurrentHashMap::class.java) continue
            field.isAccessible = true
            val map = field.get(manager) as? MutableMap<String, Any> ?: continue
            val values = map.values
            if (values.isNotEmpty() && values.all { cacheInfoClass.isInstance(it) }) return map
        }
        return null
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

            // 内容无变化时跳过写入，避免每次 G() 都触发磁盘 IO
            val json = JsonUtil.formatJson(merged)
            if (prefs.getString(cacheKey, null) == json) return

            prefs.edit()
                .putString(cacheKey, json)
                .apply()
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ 持久化主题缓存失败: ${e.message}")
        }
    }

    @Volatile
    private var restoring = false

    /**
     * 异步触发主题文件恢复
     *
     * 恢复要拷贝整个主题包（几十 MB），而 loadThemeInfo 会在渲染路径
     * （SCInnerManager.o 解析资源目录）上被调用，同步执行会阻塞主线程。
     * applyTheme 内部已带 @Synchronized，这里再加单次触发保护，避免多个渲染回调重复拷贝。
     */
    private fun requestThemeRestore(userId: String) {
        if (restoring) return
        restoring = true
        Thread {
            try {
                ThemeManager.restoreThemeIfMissing(userId)
            } catch (t: Throwable) {
                Log.runtime(TAG, "⚠️ 自动恢复主题失败: ${t.message}")
            } finally {
                restoring = false
            }
        }.start()
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
                // 目录被清空时必须让内存缓存失效，否则下面会返回指向已删目录的旧主题信息，
                // 注入一个空壳缓存条目，官方链路照样渲染不出主题
                cachedThemeInfo = null
                lastThemeFileModified = 0L
                Log.runtime(TAG, "✗ theme 目录下无任何主题目录: ${themeBaseDir.absolutePath}，尝试恢复后跳过注入")
                requestThemeRestore(userId)
                return null
            }

            val themeDir = themeDirs.filter { File(it, "theme_info.json").exists() }.maxByOrNull { it.lastModified() }
                ?: themeDirs[0]

            val themeInfoFile = File(themeDir, "theme_info.json")

            // 2. 检查内存缓存是否有效 (根据文件修改时间 + 目录是否仍存在判断)
            val currentModified = if (themeInfoFile.exists()) themeInfoFile.lastModified() else 0
            if (cachedThemeInfo != null && lastThemeFileModified == currentModified && currentModified != 0L) {
                // 缓存指向的主题目录可能已被官方删除，命中前先确认它还在
                if (themeDirs.any { it.name == cachedThemeInfo!!.themeId }) {
                    return cachedThemeInfo
                }
                cachedThemeInfo = null
                lastThemeFileModified = 0L
            }

            // 3. 缓存失效，重新读取
            if (themeInfoFile.exists()) {
                try {
                    val json = JsonUtil.parseObject(themeInfoFile.readText(), Map::class.java) as Map<String, Any>
                    val themeInfo = ThemeInfo.fromMap(json)
                    
                    // 更新缓存
                    cachedThemeInfo = themeInfo
                    lastThemeFileModified = currentModified

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
                // versionLimit 参与官方版本闸门校验，必须对齐运行时支付宝版本
                versionLimit = ThemeManager.currentWalletVersion() ?: "10.8.20.0000",
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
     * Hook 2: 回滚门拦截（关键）
     *
     * SCConfigUtil.i()=isThemeSkinRollBack，读服务端 skin_center_theme_rollbackV2 配置，
     * 返回 true 时 hasEnableSkin 直接拒绝主题——本地数据再正确也会被否决，必须恒 false。
     *
     * 注：旧版探测的 SCConfigUtil.m(String,long)/l() 在本版已不存在（md5 比对内联到
     * SCInnerManager 更新逻辑、时间戳检查仅 DIY 皮肤路径），已移除，md5 校验靠数据侧对齐。
     */
    private fun hookRollbackGate(classLoader: ClassLoader): Boolean {
        // findAndHookMethod 抛的是 NoSuchMethodError（Error 不是 Exception），必须接 Throwable
        return try {
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
            true
        } catch (t: Throwable) {
            Log.runtime(TAG, "✗ Hook回滚门失败: ${t.message}")
            false
        }
    }

    /**
     * Hook 3: hasEnableSkin检查
     */
    private fun hookHasEnableSkin(classLoader: ClassLoader): Boolean {
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
            }
            return hookedName != null
        } catch (t: Throwable) {
            Log.runtime(TAG, "✗ Hook hasEnableSkin异常: ${t.message}")
            Log.printStackTrace(TAG, t)
            return false
        }
    }

    /**
     * Hook 4: 主题目录保护（关键）
     *
     * 官方删除皮肤资源的唯一入口：SCFileHelper.a(File) 递归删除。
     * SCInnerManager.unzipScenesRes(I) 在处理资源更新时，会对「本次服务端下发场景列表里没有」的场景
     * 执行 SCFileHelper.a(new File(skin_center_dir/<uid>, usageScene))，把整个场景目录连根删掉，
     * 随后再调 b(scene) 清空 SCMetaModel 缓存。
     * 伪造的主题服务端永远不会下发，因此每次 ltp/aptrip 等场景更新都会把 theme 目录删干净。
     *
     * 这里在删除入口做短路：路径命中 skin_center_dir/<uid>/theme 且选中主题仍完整时跳过删除。
     * 其它删除（zip 临时文件、残留资源文件、其它场景）一律放行。
     */
    private fun hookProtectThemeFiles(classLoader: ClassLoader): Boolean {
        try {
            val scFileHelperClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.util.SCFileHelper",
                classLoader
            )
            // a(File)：未混淆的稳定方法名，整个 skincenter 的删除都收敛到这里
            XposedHelpers.findAndHookMethod(
                scFileHelperClass,
                "a",
                File::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val target = param.args[0] as? File ?: return
                        if (!isSkinCenterThemeDir(target)) return
                        val userId = target.parentFile?.name
                        if (ThemeManager.isSelectedThemeIntact(userId)) {
                            // void 方法置 null 结果即短路，不再执行官方删除
                            param.result = null
                            Log.runtime(TAG, "🛡 已阻止官方删除主题目录: ${target.absolutePath}")
                        }
                    }
                }
            )

            // b(String)=removeMetaCache：目录删不掉时，也不能让它清空 theme 的 SCMetaModel。
            // 重建 meta 的官方入口 m() 只在 unzipScenesRes 的场景循环里被调用，
            // 而那个循环不含 theme（服务端不下发），清空后没有任何官方代码会为 theme 重建。
            val scInnerManagerClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager",
                classLoader
            )
            runCatching {
                XposedHelpers.findAndHookMethod(
                    scInnerManagerClass,
                    "b",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val scene = param.args[0] as? String ?: return
                            if (scene != "theme") return
                            if (!ThemeManager.isSelectedThemeIntact()) return
                            param.result = null
                            Log.runtime(TAG, "🛡 已阻止官方清空 theme 的 SCMetaModel 缓存")
                        }
                    }
                )
                true
            }.getOrElse {
                // 非关键项：旧版为 b(String)，本版可能漂移，清空 meta 影响小于整个目录被删
                Log.runtime(TAG, "⚠️ theme 的 SCMetaModel 清空拦截未挂上: ${it.message}")
                false
            }

            return true
        } catch (t: Throwable) {
            Log.runtime(TAG, "✗ Hook文件保护失败（主题目录可能被官方清理）: ${t.message}")
            return false
        }
    }

    /**
     * 是否是 skin_center_dir/<userId>/theme 这一层场景目录
     * （只拦这一层，其内部的单资源文件删除仍放行）
     */
    private fun isSkinCenterThemeDir(file: File): Boolean {
        if (file.name != "theme") return false
        val userDir = file.parentFile ?: return false
        return userDir.parentFile?.name == SKIN_CENTER_DIR
    }

    /**
     * Hook 5: 文件路径
     */
    private fun hookFilePath(classLoader: ClassLoader): Boolean {
        try {
            val scInnerManagerClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager",
                classLoader
            )

            // 本版路径解析方法为 o(File, String, String)，旧版为 q（q 现为 (String,String,J)V 签名，不可用）
            XposedHelpers.findAndHookMethod(
                scInnerManagerClass,
                "o",
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
            return true
        } catch (t: Throwable) {
            // 方法不存在时抛 NoSuchMethodError（Error），必须接 Throwable
            Log.runtime(TAG, "✗ Hook文件路径失败（本版方法名可能已漂移，渲染层将按官方路径规则解析）: ${t.message}")
            return false
        }
    }

    /**
     * Hook 6: 资源加载
     */
    private fun hookResourceLoad(classLoader: ClassLoader): Boolean {
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
            return true
        } catch (t: Throwable) {
            // 方法不存在时抛 NoSuchMethodError（Error），必须接 Throwable
            Log.runtime(TAG, "✗ Hook资源加载失败（本版可能无此签名）: ${t.message}")
            return false
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
