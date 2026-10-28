package fansirsqi.xposed.sesame.hook.libxposed

import android.util.Log
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.hook.ApplicationHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * libxposed 现代管线入口（META-INF/xposed/java_init.list 指向本类）。
 *
 * 仅做生命周期桥接，全部 hook 逻辑沿用 de.robv 实现经 ApplicationHook.startHook 启动；
 * module.prop 的 targetApiVersion=101 保证现代管线下 legacy 桥可用（102 起被框架禁用）。
 * 不做宿主身份强校验，保持 NPatch 等免 root 集成模式兼容。
 */
class LibXposedRuntime : XposedModule() {
    private var processName: String? = null
    private var loaded = false
    private var packageReady = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        if (loaded) {
            log(Log.WARN, TAG, "Ignoring duplicate onModuleLoaded callback")
            return
        }
        loaded = true
        processName = param.processName
        val fwName = runCatching { frameworkName }.getOrDefault("Unknown")
        val fwVer = runCatching { frameworkVersion }.getOrDefault("")
        val apiVer = runCatching { apiVersion }.getOrDefault(0)
        log(Log.INFO, TAG, "Loaded process=${param.processName} framework=$fwName api=$apiVer")

        runCatching {
            fansirsqi.xposed.sesame.util.XpFrameworkDetector.setLibXposedFramework(fwName, fwVer, apiVer)
            fansirsqi.xposed.sesame.util.Log.runtime(TAG, fansirsqi.xposed.sesame.util.XpFrameworkDetector.getLogDescription() + " | 进程: ${param.processName}")
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!loaded || packageReady) return
        val packageName = param.packageName
        if (packageName != General.PACKAGE_NAME && packageName != MODULE_PACKAGE_NAME) return
        packageReady = true
        try {
            ApplicationHook().startHook(packageName, processName, param.classLoader)
            log(Log.INFO, TAG, "Hooked $packageName in process $processName via libxposed")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Hook failed process=$processName ${t.javaClass.simpleName}", t)
        } finally {
            // detach 是 API 102 独有；100/101 框架无此实现，跳过即可——onPackageReady 旗标已防重复回调
            if (getApiVersion() >= 102) {
                detach()
            }
        }
    }

    private companion object {
        const val TAG = "LibXposedRuntime"
        const val MODULE_PACKAGE_NAME = "fansirsqi.xposed.sesame"
    }
}
