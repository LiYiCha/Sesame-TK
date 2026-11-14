package fansirsqi.xposed.sesame.util

import android.content.Context
import android.content.pm.PackageManager
import fansirsqi.xposed.sesame.data.ViewAppInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

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
            val env = getEnvironmentSummary()
            ViewAppInfo.xpEnvSummary = env
            Files.saveXpEnv(env)
        }
    }

    /**
     * 探测传统 Xposed 管线下的框架环境
     */
    @JvmStatic
    fun detectTraditional(classLoader: ClassLoader?, context: Context? = null): String {
        return runCatching {
            var framework = detectFrameworkName(classLoader, context)
            val bridgeVersion = getBridgeVersion()
            if (bridgeVersion > 0) {
                framework = "$framework (v$bridgeVersion)"
            }
            if (detectedPipeline != "libxposed") {
                detectedPipeline = "xposed_init"
                detectedFramework = framework
                ViewAppInfo.xpFrameworkVersion = framework
            }
            val env = getEnvironmentSummary()
            ViewAppInfo.xpEnvSummary = env
            Files.saveXpEnv(env)
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
                return@runCatching detectedFramework
            }
            val stored = ViewAppInfo.xpFrameworkVersion
            if (!stored.isNullOrBlank()) {
                return@runCatching stored
            }
            val envFromFile = Files.getXpEnv()
            if (!envFromFile.isNullOrBlank()) {
                val parts = envFromFile.split("|").map { it.trim() }
                if (parts.size >= 2 && parts[1].isNotBlank()) {
                    return@runCatching parts[1]
                }
                return@runCatching envFromFile
            }
            "未激活 / 未检测到"
        }.getOrDefault("未激活 / 未检测到")
    }

    /**
     * 获取环境完整日志描述（用于冷启动日志与UI卡片展示）
     */
    @JvmStatic
    fun getEnvironmentSummary(): String {
        return runCatching {
            if (detectedFramework.isNotBlank()) {
                val pipeline = when (detectedPipeline) {
                    "libxposed" -> "现代管线 (libxposed)"
                    "xposed_init" -> "传统管线 (xposed_init)"
                    else -> "自动探测"
                }
                return@runCatching "$pipeline | $detectedFramework"
            }
            val inMemory = ViewAppInfo.xpEnvSummary
            if (inMemory.isNotBlank() && inMemory != "未激活 / 未检测到") {
                return@runCatching inMemory
            }
            val fileSaved = Files.getXpEnv()
            if (!fileSaved.isNullOrBlank()) {
                ViewAppInfo.xpEnvSummary = fileSaved
                return@runCatching fileSaved
            }
            // 尝试在当前进程轻量探测一次（避免在模块自身进程内有框架却未初始化的漏检）
            val runtimeFw = detectFrameworkName(XpFrameworkDetector::class.java.classLoader, null)
            if (runtimeFw != "Xposed" && runtimeFw != "未知框架") {
                val summary = "传统管线 (xposed_init) | $runtimeFw"
                ViewAppInfo.xpEnvSummary = summary
                Files.saveXpEnv(summary)
                return@runCatching summary
            }
            "未激活 / 未检测到"
        }.getOrDefault("未激活 / 未检测到")
    }

    @JvmStatic
    fun getLogDescription(): String {
        return runCatching {
            val summary = getEnvironmentSummary()
            "[XP环境] $summary"
        }.getOrDefault("[XP环境] 未检测到活跃框架")
    }

    private fun detectFrameworkName(classLoader: ClassLoader?, context: Context?): String {
        // 1. 优先检查 NPatch / LSPatch 应用元数据 (AndroidManifest meta-data)
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

        // 2. 检查当前进程内存 maps 中是否有注入的 SO 文件
        runCatching {
            val mapFw = detectFromProcMaps()
            if (mapFw != null) return mapFw
        }

        // 3. 检查 ClassLoader 类名特征
        runCatching {
            var curCl = classLoader ?: XpFrameworkDetector::class.java.classLoader
            while (curCl != null) {
                val clName = curCl.javaClass.name
                if (clName.contains("NPClassLoader", ignoreCase = true)) return "NPatch (免Root内置)"
                if (clName.contains("LSPClassLoader", ignoreCase = true)) return "LSPatch (免Root内置)"
                if (clName.contains("VectorClassLoader", ignoreCase = true)) return "NPatch / Vector"
                curCl = curCl.parent
            }
        }

        // 4. 检查注入类（依次检查传入 ClassLoader、自身 ClassLoader、上下文 ClassLoader）
        val loaders = listOfNotNull(
            classLoader,
            XpFrameworkDetector::class.java.classLoader,
            Thread.currentThread().contextClassLoader,
            ClassLoader.getSystemClassLoader()
        )

        for (loader in loaders) {
            if (hasClass(loader, "npatch.loader.HookLoader")) {
                return "NPatch (免Root内置)"
            }
            if (hasClass(loader, "org.lsposed.lspatch.loader.HookLoader") ||
                hasClass(loader, "org.lsposed.lspatch.loader.LSPClassLoader")
            ) {
                return "NPatch / LSPatch (免Root内置)"
            }
            if (hasClass(loader, "org.lsposed.lspd.core.Startup") ||
                hasClass(loader, "org.lsposed.lspd.service.LSPService")
            ) {
                return "LSPosed (Root)"
            }
            if (hasClass(loader, "org.meowcat.edxposed.EdXposed") ||
                hasClass(loader, "com.solohsu.android.edxp.EdXposed")
            ) {
                return "EdXposed"
            }
            if (hasClass(loader, "com.swift.sandhook.SandHook")) {
                return "SandHook"
            }
            if (hasClass(loader, "me.weishu.exp.Bridge")) {
                return "TaiChi (太极)"
            }
        }

        // 5. 检查全局常见系统类
        if (hasClass("org.lsposed.lspd.core.Startup") || hasClass("org.lsposed.lspd.service.LSPService")) {
            return "LSPosed (Root)"
        }
        if (hasClass("org.meowcat.edxposed.EdXposed") || hasClass("com.solohsu.android.edxp.EdXposed")) {
            return "EdXposed"
        }
        if (hasClass("com.swift.sandhook.SandHook")) {
            return "SandHook"
        }
        if (hasClass("me.weishu.exp.Bridge")) {
            return "TaiChi (太极)"
        }
        if (hasClass("de.robv.android.xposed.XposedBridge")) {
            return "Xposed"
        }

        return "未知框架"
    }

    private fun detectFromProcMaps(): String? {
        val mapsFile = File("/proc/self/maps")
        if (!mapsFile.canRead()) return null
        BufferedReader(FileReader(mapsFile)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val str = line ?: continue
                if (str.contains("libnpatch.so")) return "NPatch (免Root内置)"
                if (str.contains("liblspatch.so")) return "LSPatch (免Root内置)"
                if (str.contains("libvector.so")) return "NPatch / Vector"
                if (str.contains("liblspd.so")) return "LSPosed (Root)"
                if (str.contains("libsandhook.so")) return "SandHook"
                if (str.contains("libedxp.so")) return "EdXposed"
            }
        }
        return null
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
