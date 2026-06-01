package fansirsqi.xposed.sesame.hook.core.modules

import android.content.Context
import fansirsqi.xposed.sesame.hook.CaptchaHook
import fansirsqi.xposed.sesame.hook.core.HookModule
import fansirsqi.xposed.sesame.util.Log

class CaptchaModule : HookModule {
    private val TAG = "CaptchaModule"

    override fun onHandleLoadPackage(lpparam: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam) {
        try {
            CaptchaHook.setupHook(lpparam.classLoader)
        } catch (_: Throwable) {}
    }

    override fun onPreAppAttach(context: Context, classLoader: ClassLoader) {
        try {
            CaptchaHook.setupHook(classLoader)
        } catch (t: Throwable) {
            Log.runtime(TAG, "验证码Hook初始化失败")
            Log.printStackTrace(TAG, t)
        }
    }
}
