package fansirsqi.xposed.sesame.util

import android.content.Context
import android.content.pm.PackageManager
import fansirsqi.xposed.sesame.data.ViewAppInfo

/**
 * Xposed 框架及运行管线探测器
 * 所有方法均具备严格的异常防御，绝不导致宿主或模块进程闪退。
 */
object XpFrameworkDetector {
    private const val TAG = "XpFrameworkDetector"

    @Volatile
    private var detectedFramework: String = ""

    @Volatile
    private var detectedPipeline: String = "" // "libxposed" 或 "xposed_init"

    /**
     * 设置由现代 LibXposed 管线提供的框架信息
     */
    @JvmStatic
    fun setLibXposedFramework(frameworkName: String?, frameworkVersion: String?, apiVersion: Int) {
        runCatching {
            detectedPipeline = "libxposed"
            val name = frameworkName?.takeIf { it.isNotBlank() } ?: "LibXposed"
            val ver = frameworkVersion?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
            val api = if (apiVersion > 0) " (API $apiVersion)" else ""
            val summary = "$name$ver$api"
            detectedFramework = summary
            ViewAppInfo.xpFrameworkVersion = summary
        }
    }

    /**
     * 探测传统 Xposed 管线下的框架环境
     */
    @JvmStatic
    fun detectTraditional(classLoader: ClassLoader?, context: Context? = null): String {
        return runCatching {
            detectedPipeline = "xposed_init"
            var framework = detectFrameworkName(classLoader, context)
            val bridgeVersion = getBridgeVersion()
            if (bridgeVersion > 0) {
                framework = "$framework (v$bridgeVersion)"
            }
            detectedFramework = framework
            ViewAppInfo.xpFrameworkVersion = framework
            framework
        }.getOrDefault("Xposed (传统管线)").also {
            if (detectedFramework.isBlank()) {
                detectedFramework = it
                ViewAppInfo.xpFrameworkVersion = it
            }
        }
    }

    /**
     * 获取当前探测结果摘要（保证安全非空，不抛异常）
     */
    @JvmStatic
    fun getFrameworkSummary(): String {
        return runCatching {
            if (detectedFramework.isNotBlank()) {
                return detectedFramework
            }
            val stored = ViewAppInfo.xpFrameworkVersion
            if (!stored.isNullOrBlank()) {
                return stored
            }
            "未激活 / 未检测到"
        }.getOrDefault("未激活 / 未检测到")
    }

    /**
     * 获取环境完整日志描述（用于冷启动日志输出）
     */
    @JvmStatic
    fun getLogDescription(): String {
        return runCatching {
            val pipeline = when (detectedPipeline) {
                "libxposed" -> "现代管线 (libxposed)"
                "xposed_init" -> "传统管线 (xposed_init)"
                else -> "自动探测"
            }
            val fw = getFrameworkSummary()
            "[XP环境] 入口: $pipeline | 框架: $fw"
        }.getOrDefault("[XP环境] 未检测到活跃框架")
    }

    private fun detectFrameworkName(classLoader: ClassLoader?, context: Context?): String {
        // 1. 优先检查 NPatch / LSPatch 应用元数据
        if (context != null) {
            runCatching {
                val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                val meta = appInfo.metaData
                if (meta != null) {
                    if (meta.containsKey("npatch")) return "NPatch (免Root内置)"
                    if (meta.containsKey("lspatch")) return "LSPatch (免Root内置)"
                }
            }
        }

        // 2. 检查 NPatch / LSPatch 注入类
        if (classLoader != null) {
            if (hasClass(classLoader, "npatch.loader.HookLoader")) {
                return "NPatch (免Root内置)"
            }
            if (hasClass(classLoader, "org.lsposed.lspatch.loader.HookLoader") ||
                hasClass(classLoader, "org.lsposed.lspatch.loader.LSPClassLoader")
            ) {
                return "NPatch / LSPatch (免Root内置)"
            }
        }

        // 3. 检查系统级 LSPosed
        if (hasClass("org.lsposed.lspd.core.Startup") || hasClass("org.lsposed.lspd.service.LSPService")) {
            return "LSPosed (Root)"
        }

        // 4. 检查 EdXposed
        if (hasClass("org.meowcat.edxposed.EdXposed") || hasClass("com.solohsu.android.edxp.EdXposed")) {
            return "EdXposed"
        }

        // 5. 检查 SandHook
        if (hasClass("com.swift.sandhook.SandHook")) {
            return "SandHook"
        }

        // 6. 检查 太极
        if (hasClass("me.weishu.exp.Bridge")) {
            return "TaiChi (太极)"
        }

        return "Xposed"
    }

    private fun hasClass(className: String): Boolean {
        return try {
            Class.forName(className)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasClass(classLoader: ClassLoader, className: String): Boolean {
        return try {
            classLoader.loadClass(className)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getBridgeVersion(): Int {
        return try {
            val clazz = Class.forName("de.robv.android.xposed.XposedBridge")
            val field = clazz.getField("XPOSED_BRIDGE_VERSION")
            field.getInt(null)
        } catch (_: Throwable) {
            0
        }
    }
}
