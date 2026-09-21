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
import fansirsqi.xposed.sesame.util.GlobalThreadPools
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
                        // resume 处理包含文件 IO 与可能的全量初始化，不在主线程执行
                        GlobalThreadPools.execute {
                            handleActivityResume()
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

    private fun handleActivityResume() {
        try {
            if (TaskScheduler.isStopped()) {
                return
            }
            val targetUid = AppContext.getUserId()
            if (targetUid == null) {
                Log.runtime("用户未登录")
                Toast.show("用户未登录")
                return
            }
            fansirsqi.xposed.sesame.util.Files.saveActiveUser(targetUid)
            if (!LifecycleManager.isInit()) {
                LifecycleManager.initHandler(true)
                return
            }
            val currentUid = UserMap.currentUid
            if (targetUid != currentUid) {
                if (currentUid != null) {
                    LifecycleManager.initHandler(true)
                    Log.runtime("用户已切换")
                    Toast.show("用户已切换")
                    return
                }
                UserMap.initUser(targetUid)
            }
            if (LifecycleManager.isOffline()) {
                LifecycleManager.setOffline(false)
                TaskScheduler.executeTask()
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

        // 宿主 Service.onCreate 回调必须快速返回，重初始化全部移到后台线程
        GlobalThreadPools.execute {
            try {
                val cl = AppContext.getClassLoader()
                if (cl != null) {
                    SecurityBodyHelper.init(cl)
                    LocationHelper.init(cl)
                    SmartSchedulerManager.initialize(appService.applicationContext)
                    AlipayMiniMarkHelper.init(cl)
                    AuthCodeHelper.init(cl)
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
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
            }
        }
    }

    private fun handleServiceDestroy(service: Service) {
        Log.runtime("目标应用前台服务被销毁")
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
        // 不做自动重启自愈。把服务销毁当作"异常退出需要恢复"会引发
        // 反复复活（重启风暴叠加任务风暴），放大支付宝风控误判风险
    }
}
