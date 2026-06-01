package fansirsqi.xposed.sesame.hook.core.modules

import android.app.Activity
import android.app.Service
import android.content.Context
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.data.RunType
import fansirsqi.xposed.sesame.data.ViewAppInfo
import fansirsqi.xposed.sesame.hook.Toast
import fansirsqi.xposed.sesame.hook.broadcast.SesameReceiver
import fansirsqi.xposed.sesame.hook.context.AppContext
import fansirsqi.xposed.sesame.hook.core.HookModule
import fansirsqi.xposed.sesame.hook.internal.AlipayMiniMarkHelper
import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper
import fansirsqi.xposed.sesame.hook.internal.LocationHelper
import fansirsqi.xposed.sesame.hook.internal.SecurityBodyHelper
import fansirsqi.xposed.sesame.hook.keepalive.SmartSchedulerManager
import fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager
import fansirsqi.xposed.sesame.hook.network.HttpCaptureHook
import fansirsqi.xposed.sesame.hook.scheduler.TaskScheduler
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap

class LifecycleModule : HookModule {
    private val TAG = "LifecycleModule"

    override fun onHandleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (General.PACKAGE_NAME != lpparam.packageName) return
        val classLoader = lpparam.classLoader

        // hook LauncherActivity.onResume
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", classLoader, "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        Handler(Looper.getMainLooper()).post {
                            handleActivityResume(activity)
                        }
                    }
                })
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook login err")
        }

        // hook Service.onCreate
        try {
            XposedHelpers.findAndHookMethod(Service::class.java, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val appService = param.thisObject as Service
                        if (General.CURRENT_USING_SERVICE != appService.javaClass.canonicalName) return
                        handleServiceCreate(appService)
                    }
                })
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook service onCreate err")
        }

        // hook Service.onDestroy
        try {
            XposedHelpers.findAndHookMethod(Service::class.java, "onDestroy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val service = param.thisObject as Service
                        if (General.CURRENT_USING_SERVICE != service.javaClass.canonicalName) return
                        handleServiceDestroy(service)
                    }
                })
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook service onDestroy err")
        }
    }

    private fun handleActivityResume(activity: Activity) {
        try {
            val targetUid = AppContext.getUserId()
            if (targetUid == null) {
                Log.record("用户未登录")
                Toast.show("用户未登录")
                return
            }
            if (!LifecycleManager.isInit()) {
                LifecycleManager.initHandler(true)
                return
            }
            val currentUid = UserMap.currentUid
            if (targetUid != currentUid) {
                if (currentUid != null) {
                    LifecycleManager.initHandler(true)
                    Log.record("用户已切换")
                    Toast.show("用户已切换")
                    return
                }
                UserMap.initUser(targetUid)
            }
            if (LifecycleManager.isOffline()) {
                LifecycleManager.setOffline(false)
                TaskScheduler.executeTask()
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        activity.finish()
                    } catch (t: Throwable) {
                        Log.printStackTrace(TAG, t)
                    }
                }, 300)
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
        }
    }

    private fun handleServiceCreate(appService: Service) {
        Log.runtime(TAG, "Service onCreate")
        AppContext.setContext(appService.applicationContext)
        AppContext.setService(appService)
        AppContext.setMainHandler(Handler(Looper.getMainLooper()))

        val cl = AppContext.getClassLoader()
        if (cl != null) {
            SecurityBodyHelper.init(cl)
            LocationHelper.init(cl)
            SmartSchedulerManager.initialize(appService.applicationContext)
            AlipayMiniMarkHelper.init(cl)
            AuthCodeHelper.init(cl)
            AuthCodeHelper.getAuthCode("2021005114632037")
        }

        SesameReceiver.register(appService, object : SesameReceiver.BroadcastCallback {
            override fun onInitHandler(force: Boolean) {
                LifecycleManager.initHandler(force)
            }
            override fun onReLogin() {
                LifecycleManager.reLogin()
            }
        })

        LifecycleManager.initHandler(true)
    }

    private fun handleServiceDestroy(service: Service) {
        Log.record("目标应用前台服务被销毁")
        Toast.show("目标应用前台服务被销毁")
        LifecycleManager.destroyHandler(true)

        try {
            val viewAppInfoClass = service.classLoader.loadClass("fansirsqi.xposed.sesame.data.ViewAppInfo")
            XposedHelpers.callStaticMethod(viewAppInfoClass, "setRunType", RunType.DISABLE.code)
        } catch (e: Exception) {
            try {
                ViewAppInfo.setRunType(RunType.DISABLE)
            } catch (ex: Exception) {
                Log.printStackTrace(ex)
            }
        }
        
        try {
            service.sendBroadcast(android.content.Intent("com.eg.android.AlipayGphone.sesame.restart"))
        } catch (t: Throwable) {}
    }
}
