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

    override fun onHandleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (General.PACKAGE_NAME != lpparam.packageName) return
        val classLoader = lpparam.classLoader

        // hook FgBgMonitorImpl (在 main dex 中，可立即 hook)
        val fgBgClass = "com.alipay.mobile.common.fgbg.FgBgMonitorImpl"
        try {
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackground", XC_MethodReplacement.returnConstant(false))
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackground", Boolean::class.javaPrimitiveType, XC_MethodReplacement.returnConstant(false))
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackgroundV2", XC_MethodReplacement.returnConstant(false))
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook FgBgMonitorImpl err")
        }

        // hook MiscUtils (在 main dex 中，可立即 hook)
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk",
                Context::class.java, XC_MethodReplacement.returnConstant(true))
            Log.runtime(TAG, "hook MiscUtils successfully")
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook MiscUtils err")
        }
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

            // 1. Hook H5AppRpcUpdate
            try {
                val targetClass = classLoader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate")
                val h5PageClass = classLoader.loadClass(General.H5PAGE_NAME)
                XposedHelpers.findAndHookMethod(
                    targetClass, "matchVersion",
                    h5PageClass, Map::class.java, String::class.java,
                    XC_MethodReplacement.returnConstant(false)
                )
                Log.runtime(TAG, "✅ 延迟 Hook H5AppRpcUpdate 成功")
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ 延迟 Hook H5AppRpcUpdate 失败: ${t.message}")
            }

            // 2. Hook CDPBService
            try {
                val cdpbServiceClass = classLoader.loadClass("com.alipay.cdpb.api.CDPBService")
                val is3PTSpacesMethod = XposedHelpers.findMethodExactIfExists(cdpbServiceClass, "is3PTSpaces")
                if (is3PTSpacesMethod != null) {
                    XposedBridge.hookMethod(is3PTSpacesMethod, XC_MethodReplacement.returnConstant(false))
                    Log.runtime(TAG, "✅ 延迟 Hook CDPBService 成功")
                }
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ 延迟 Hook CDPBService 失败: ${t.message}")
            }

            // 3. Hook AlipayLogin 生命周期（状态监控）
            try {
                val loginActivityClass = classLoader.loadClass(General.CURRENT_USING_ACTIVITY)
                XposedHelpers.findAndHookMethod(loginActivityClass, "onResume", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.setAlipayLoginActive(true)
                    }
                })
                XposedHelpers.findAndHookMethod(loginActivityClass, "onDestroy", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.setAlipayLoginActive(false)
                    }
                })
                Log.runtime(TAG, "✅ 延迟 Hook AlipayLogin 生命周期成功")
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ 延迟 Hook AlipayLogin 生命周期失败: ${t.message}")
            }

            // 4. Hook EngineLoader.loadLibSync 防止 UnsatisfiedLinkError 崩溃
            try {
                val engineLoaderClass = classLoader.loadClass("com.alipay.android.phone.xriver.bundlex.engine.EngineLoader")
                XposedBridge.hookAllMethods(engineLoaderClass, "loadLibSync", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args != null && param.args.isNotEmpty()) {
                            Log.runtime(TAG, "EngineLoader.loadLibSync 正在加载: ${param.args[0]}")
                        }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.hasThrowable() && param.throwable is UnsatisfiedLinkError) {
                            val t = param.throwable
                            Log.runtime(TAG, "捕获到 loadLibSync 异常: ${t.message}")
                            try {
                                val msg = t.message
                                var soName: String? = null
                                if (msg != null) {
                                    val start = msg.indexOf("library \"")
                                    if (start != -1) {
                                        val end = msg.indexOf("\"", start + 9)
                                        if (end != -1) {
                                            soName = msg.substring(start + 9, end)
                                        }
                                    }
                                }
                                if (soName != null) {
                                    val context = fansirsqi.xposed.sesame.hook.context.AppContext.getAppContext()
                                    if (context != null) {
                                        val rootDir = context.filesDir.parentFile
                                        if (rootDir != null && rootDir.exists()) {
                                            val soFile = findSoFile(rootDir, soName)
                                            if (soFile != null) {
                                                System.load(soFile.absolutePath)
                                                Log.runtime(TAG, "✅ 手动成功载入 JNI 库: ${soFile.absolutePath}")
                                                param.throwable = null
                                                return
                                            }
                                        }
                                    }
                                }
                            } catch (ex: Throwable) {
                                Log.runtime(TAG, "手动载入 JNI 库失败: ${ex.message}")
                            }
                            // 即使手动加载失败，也吞掉异常，因为这是后台线程异步加载，防止直接闪退
                            param.throwable = null
                        }
                    }
                })
                Log.runtime(TAG, "✅ Hook EngineLoader.loadLibSync 成功")
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ Hook EngineLoader.loadLibSync 失败: ${t.message}")
            }
        }

        private fun findSoFile(dir: java.io.File, name: String): java.io.File? {
            val files = dir.listFiles() ?: return null
            for (f in files) {
                if (f.isDirectory) {
                    val res = findSoFile(f, name)
                    if (res != null) return res
                } else if (f.name == name) {
                    return f
                }
            }
            return null
        }
    }
}
