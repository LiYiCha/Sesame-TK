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
            // Ignore and safely default to false
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



        // Hook system WebViewClient for Tmall Seckill Auto-Submit
        try {
            val systemClientClass = Class.forName("android.webkit.WebViewClient")
            XposedBridge.hookAllMethods(systemClientClass, "onPageFinished", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.args[0]
                    val url = param.args[1] as? String ?: return
                    handleWebPageFinished(view, url)
                }
            })
            Log.runtime(TAG, "✅ Hook android.webkit.WebViewClient.onPageFinished 成功")
        } catch (t: Throwable) {
            Log.runtime(TAG, "❌ Hook android.webkit.WebViewClient 失败: ${t.message}")
        }

        // Hook UC WebViewClient for Tmall Seckill Auto-Submit
        try {
            val ucClientClass = classLoader.loadClass("com.uc.webview.export.WebViewClient")
            XposedBridge.hookAllMethods(ucClientClass, "onPageFinished", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.args[0]
                    val url = param.args[1] as? String ?: return
                    handleWebPageFinished(view, url)
                }
            })
            Log.runtime(TAG, "✅ Hook UC WebViewClient.onPageFinished 成功")
        } catch (t: Throwable) {
            Log.runtime(TAG, "❌ Hook UC WebViewClient 失败: ${t.message}")
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
                val targetClass =
                    classLoader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate")
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

            // 3. Hook AlipayLogin 生命周期（状态监控）
            try {
                val loginActivityClass = classLoader.loadClass(General.CURRENT_USING_ACTIVITY)
                XposedHelpers.findAndHookMethod(
                    loginActivityClass,
                    "onResume",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.setAlipayLoginActive(
                                true
                            )
                        }
                    })
                XposedHelpers.findAndHookMethod(
                    loginActivityClass,
                    "onDestroy",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.setAlipayLoginActive(
                                false
                            )
                        }
                    })
                Log.runtime(TAG, "✅ 延迟 Hook AlipayLogin 生命周期成功")
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ 延迟 Hook AlipayLogin 生命周期失败: ${t.message}")
            }
        }

        @JvmStatic
        private fun handleWebPageFinished(view: Any, url: String) {
            if (url.contains("pages.tmall.com/wow/wt/act/lm-pages")) {
                Log.runtime(TAG, "🚀 检测到进入天猫提单页: ${'$'}url，准备注入自动提交订单脚本")
                
                val jsCode = """
                    (function() {
                        var count = 0;
                        var timer = setInterval(function() {
                            count++;
                            if (count > 600) {
                                clearInterval(timer);
                                return;
                            }
                            var btn = document.querySelector('.submit-btn') || 
                                      document.querySelector('.submitBtn') ||
                                      document.querySelector('[class*="submit"]') ||
                                      Array.from(document.querySelectorAll('button, div, span')).find(el => {
                                          return el.textContent && el.textContent.includes('提交订单');
                                      });
                            if (btn) {
                                if (btn.disabled || btn.getAttribute('disabled') !== null || btn.classList.contains('disabled')) {
                                    return;
                                }
                                if (typeof btn.click === 'function') {
                                    btn.click();
                                } else {
                                    var event = new MouseEvent('click', { bubbles: true, cancelable: true });
                                    btn.dispatchEvent(event);
                                }
                                clearInterval(timer);
                            }
                        }, 50);
                    })()
                """.trimIndent()
                
                try {
                    XposedHelpers.callMethod(view, "loadUrl", "javascript:${'$'}jsCode")
                    Log.runtime(TAG, "✅ 自动提交订单脚本已成功注入 WebView")
                } catch (e: Exception) {
                    Log.runtime(TAG, "❌ 注入自动提交订单脚本失败: ${e.message}")
                }
            }
        }
    }
}
