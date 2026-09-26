package fansirsqi.xposed.sesame.hook.core.modules

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.hook.core.HookModule
import fansirsqi.xposed.sesame.util.Log

class MiscHookModule : HookModule {
    private val TAG = "MiscHookModule"

    private fun isCalledFromUI(): Boolean {
        try {
            val stack = Thread.currentThread().stackTrace
            for (element in stack) {
                val name = element.className ?: continue
                if (name.contains("android.app.Activity") || 
                    name.contains("androidx.fragment.app") || 
                    name.contains("android.support.v4.app") || 
                    name.contains("FragmentManager") ||
                    name.contains("FragmentTransaction")) {
                    return true
                }
            }
        } catch (t: Throwable) {
            // Ignore
        }
        return false
    }

    override fun onHandleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (General.PACKAGE_NAME != lpparam.packageName) return
        val classLoader = lpparam.classLoader

        // hook FgBgMonitorImpl (在 main dex 中，可立即 hook，增加 UI 调用栈保护)
        val fgBgClass = "com.alipay.mobile.common.fgbg.FgBgMonitorImpl"
        try {
            val fgBgHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isCalledFromUI()) return
                    param.result = false
                }
            }
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackground", fgBgHook)
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackground", Boolean::class.javaPrimitiveType, fgBgHook)
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackgroundV2", fgBgHook)
            Log.runtime(TAG, "hook FgBgMonitorImpl successfully with UI safety check")
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook FgBgMonitorImpl err: ${t.message}")
        }

        // hook MiscUtils (在 main dex 中，可立即 hook，增加 UI 调用栈保护)
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk",
                Context::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isCalledFromUI()) return
                        param.result = true
                    }
                })
            Log.runtime(TAG, "hook MiscUtils successfully with UI safety check")
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook MiscUtils err")
        }



        // 移除对全局系统 android.webkit.WebViewClient 的 Hook，避免引发 Native 内存异常和冷启动干扰

    }

    companion object {
        private const val TAG = "MiscHookModule"
        private val bundleHooksRegistered = java.util.concurrent.atomic.AtomicBoolean(false)


        @JvmStatic
        fun delayRegisterBundleHooks(classLoader: ClassLoader) {
            if (!bundleHooksRegistered.compareAndSet(false, true)) {
                return
            }
            Log.runtime(TAG, "开始执行延迟的动态 bundle 及 Activity Hook 注册...")


            // 2. Hook CDPBService
            try {
                val cdpbServiceClass = classLoader.loadClass("com.alipay.cdpb.api.CDPBService")
                val is3PTSpacesMethod =
                    XposedHelpers.findMethodExactIfExists(cdpbServiceClass, "is3PTSpaces")
                if (is3PTSpacesMethod != null) {
                    XposedBridge.hookMethod(
                        is3PTSpacesMethod,
                        XC_MethodReplacement.returnConstant(false)
                    )
                    Log.runtime(TAG, "✅ 延迟 Hook CDPBService 成功")
                }
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ 延迟 Hook CDPBService 失败: ${t.message}")
            }

        }
    }

    override fun onPostAppAttach(context: Context, classLoader: ClassLoader) {
        try {
            val app = context.applicationContext as? android.app.Application
            app?.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: android.app.Activity) {
                    if (activity.javaClass.name == General.CURRENT_USING_ACTIVITY) {
                        fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.setAlipayLoginActive(true)
                    }
                }
                override fun onActivityDestroyed(activity: android.app.Activity) {
                    if (activity.javaClass.name == General.CURRENT_USING_ACTIVITY) {
                        fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.setAlipayLoginActive(false)
                    }
                }
                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
                override fun onActivityStarted(activity: android.app.Activity) {}
                override fun onActivityPaused(activity: android.app.Activity) {}
                override fun onActivityStopped(activity: android.app.Activity) {}
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            })
            Log.runtime(TAG, "✅ 成功使用 ActivityLifecycleCallbacks 监听 AlipayLogin 生命周期")
        } catch (t: Throwable) {
            Log.runtime(TAG, "❌ 监听 AlipayLogin 生命周期失败: ${t.message}")
        }
    }
}
