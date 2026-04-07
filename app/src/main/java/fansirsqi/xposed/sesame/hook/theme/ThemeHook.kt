package fansirsqi.xposed.sesame.hook.theme

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.util.Log
import java.io.File

/**
 * 主题Hook管理器
 *
 * 完整的主题替换Hook方案，解决以下问题：
 * 1. MD5校验失败导致主题被重新下载
 * 2. 缓存时间过期导致主题失效
 * 3. 内存缓存未更新需要重启
 * 4. 主题只能显示一段时间
 *
 * Hook策略：
 * - Hook缓存读取：注入自定义主题信息到内存缓存
 * - Hook MD5校验：绕过MD5验证
 * - Hook时间戳检查：防止缓存过期
 * - Hook hasEnableSkin：强制启用主题
 * - Hook文件路径：指向自定义主题目录
 *
 * @author fansirsqi
 */
object ThemeHook {

    private const val TAG = "ThemeHook"

    // 自定义主题配置
    private const val CUSTOM_SKIN_ID = "CUSTOM_THEME_HOHO"
    private const val CUSTOM_USER_SKIN_ID = "20260206003520010000120077249371"
    private const val CUSTOM_MD5 = "HOHO_CUSTOM_MD5_NO_CHECK"

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
            //Log.runtime(TAG, "⛔ 主题Hook已关闭")
            isHooked = false
            return
        }

        if (isHooked) {
            Log.runtime(TAG, "⚠️ 主题Hook已经应用")
            return
        }

        try {
            Log.runtime(TAG, "🎨 开始应用主题Hook...")

            // 1. Hook缓存读取 - 注入自定义主题信息
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
     * Hook 1: 缓存读取
     *
     * Hook: SCInnerManager.K() - readSkinInfoFromLocalCache
     * 目的：在读取缓存后，注入自定义主题信息到内存缓存
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

                            // 创建自定义主题缓存信息
                            val cacheInfoClass = XposedHelpers.findClass(
                                "com.alipay.mobile.skincenter.model.SCCacheInfoModel",
                                classLoader
                            )

                            val customCache = cacheInfoClass.newInstance()

                            // 设置关键字段
                            XposedHelpers.setObjectField(customCache, "usageScene", "theme")
                            XposedHelpers.setObjectField(customCache, "skinId", CUSTOM_SKIN_ID)
                            XposedHelpers.setObjectField(customCache, "userSkinId", CUSTOM_USER_SKIN_ID)
                            XposedHelpers.setObjectField(customCache, "userId", currentUserId)
                            XposedHelpers.setObjectField(customCache, "md5", CUSTOM_MD5)
                            XposedHelpers.setObjectField(customCache, "appSquareMd5", CUSTOM_MD5)

                            // 设置缓存时间为未来时间（100年后），防止过期
                            val futureTime = System.currentTimeMillis() + (100L * 365 * 24 * 60 * 60 * 1000)
                            XposedHelpers.setLongField(customCache, "cacheTime", futureTime)

                            // 设置版本限制为最低版本
                            XposedHelpers.setObjectField(customCache, "versionLimit", "10.8.20.0000")

                            // 设置为非DIY皮肤
                            XposedHelpers.setBooleanField(customCache, "isDiySkin", false)

                            // 注入到内存缓存
                            cacheMap["theme"] = customCache

                            Log.runtime(TAG, "✅ 已注入自定义主题缓存信息")

                        } catch (e: Exception) {
                            Log.runtime(TAG, "❌ 注入缓存失败: ${e.message}")
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
     * Hook 2: MD5校验
     *
     * Hook: SCConfigUtil.m() - MD5时间戳检查
     * 目的：对自定义主题返回false（不过期）
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
                        // 对所有主题返回false（不过期）
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
     *
     * Hook: SCConfigUtil相关的时间检查方法
     * 目的：防止缓存被判定为过期
     */
    private fun hookTimeCheck(classLoader: ClassLoader) {
        try {
            // Hook回滚检查
            XposedHelpers.findAndHookMethod(
                "com.alipay.mobile.skincenter.util.SCConfigUtil",
                classLoader,
                "l", // isThemeSkinRollBack
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        // 返回false，表示不回滚
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
     *
     * Hook: SCInnerManager.y() - hasEnableSkin
     * 目的：对theme场景强制返回true
     */
    private fun hookHasEnableSkin(classLoader: ClassLoader) {
        try {
            val scInnerManagerClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                scInnerManagerClass,
                "y", // hasEnableSkin方法
                String::class.java,
                Map::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val scene = param.args[0] as? String
                        if (scene == "theme") {
                            // 强制返回true，启用主题
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
     *
     * Hook: SCInnerManager.q() - 获取主题文件路径
     * 目的：指向自定义主题目录
     */
    private fun hookFilePath(classLoader: ClassLoader) {
        try {
            val scInnerManagerClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.manage.SCInnerManager",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                scInnerManagerClass,
                "q", // 获取主题文件路径
                File::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val scene = param.args[1] as? String
                        if (scene == "theme") {
                            val baseDir = param.args[0] as? File
                            if (baseDir != null) {
                                // 返回自定义主题目录
                                val customThemeDir = File(baseDir, "theme/$CUSTOM_USER_SKIN_ID")
                                param.result = customThemeDir
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
     *
     * Hook: SCMetaModel.loadResSync - 加载资源
     * 目的：确保从自定义主题目录加载资源
     */
    private fun hookResourceLoad(classLoader: ClassLoader) {
        try {
            val scMetaModelClass = XposedHelpers.findClass(
                "com.alipay.mobile.skincenter.model.SCMetaModel",
                classLoader
            )

            // Hook loadResSync方法
            XposedHelpers.findAndHookMethod(
                scMetaModelClass,
                "loadResSync",
                String::class.java, // position
                Map::class.java,    // map
                Boolean::class.javaPrimitiveType, // z
                Boolean::class.javaPrimitiveType, // z2
                String::class.java, // str
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val metaModel = param.thisObject
                            val scene = XposedHelpers.getObjectField(metaModel, "scene") as? String

                            if (scene == "theme") {
                                // 确保使用自定义主题的skinId
                                XposedHelpers.setObjectField(metaModel, "skinId", CUSTOM_SKIN_ID)
                                XposedHelpers.setObjectField(metaModel, "userSkinId", CUSTOM_USER_SKIN_ID)
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
            // 方法1: 从UserMap获取
            val userMapClass = classLoader.loadClass("fansirsqi.xposed.sesame.util.maps.UserMap")
            val currentUid = XposedHelpers.getStaticObjectField(userMapClass, "currentUid") as? String
            if (!currentUid.isNullOrEmpty()) {
                return currentUid
            }

            // 方法2: 扫描skin_center_dir目录
            val skinCenterDir = File("/data/data/com.eg.android.AlipayGphone/files/skin_center_dir")
            if (skinCenterDir.exists()) {
                val userDirs = skinCenterDir.listFiles { file ->
                    file.isDirectory && file.name.matches(Regex("^\\d+$"))
                }
                if (userDirs != null && userDirs.isNotEmpty()) {
                    return userDirs[0].name
                }
            }

            null
        } catch (e: Exception) {
            Log.runtime(TAG, "⚠️ 获取用户ID失败: ${e.message}")
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
