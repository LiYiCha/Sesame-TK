package fansirsqi.xposed.sesame.hook.core

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage
import fansirsqi.xposed.sesame.util.Log

/**
 * Hook 模块管理器，负责模块的注册和生命周期分发
 */
object HookModuleManager {
    private val modules = mutableListOf<HookModule>()

    fun registerModule(module: HookModule) {
        modules.add(module)
    }

    fun dispatchHandleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        modules.forEach {
            try {
                it.onHandleLoadPackage(lpparam)
            } catch (t: Throwable) {
                Log.runtime("Module handleLoadPackage error: ${it.javaClass.simpleName}")
                Log.printStackTrace(t)
            }
        }
    }

    fun dispatchPreAppAttach(context: Context, classLoader: ClassLoader) {
        modules.forEach {
            try {
                it.onPreAppAttach(context, classLoader)
            } catch (t: Throwable) {
                Log.runtime("Module onPreAppAttach error: ${it.javaClass.simpleName}")
                Log.printStackTrace(t)
            }
        }
    }

    fun dispatchPostAppAttach(context: Context, classLoader: ClassLoader) {
        modules.forEach {
            try {
                it.onPostAppAttach(context, classLoader)
            } catch (t: Throwable) {
                Log.runtime("Module onPostAppAttach error: ${it.javaClass.simpleName}")
                Log.printStackTrace(t)
            }
        }
    }
}
