package fansirsqi.xposed.sesame.hook.core.modules

import android.content.Context
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

        // hook "com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate"
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate", classLoader, "matchVersion",
                classLoader.loadClass(General.H5PAGE_NAME), Map::class.java, String::class.java,
                XC_MethodReplacement.returnConstant(false))
            Log.runtime(TAG, "hook matchVersion successfully")
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook matchVersion err")
            Log.printStackTrace(TAG, t)
        }

        // Hook CDPB 服务
        try {
            val cdpbServiceClass = XposedHelpers.findClassIfExists("com.alipay.cdpb.api.CDPBService", classLoader)
            if (cdpbServiceClass != null) {
                val is3PTSpacesMethod = XposedHelpers.findMethodExactIfExists(cdpbServiceClass, "is3PTSpaces")
                if (is3PTSpacesMethod != null) {
                    XposedBridge.hookMethod(is3PTSpacesMethod, XC_MethodReplacement.returnConstant(false))
                }
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook CDPB 服务异常$t")
            Log.printStackTrace(TAG, t)
        }

        // hook FgBgMonitorImpl
        val fgBgClass = "com.alipay.mobile.common.fgbg.FgBgMonitorImpl"
        try {
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackground", XC_MethodReplacement.returnConstant(false))
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackground", Boolean::class.javaPrimitiveType, XC_MethodReplacement.returnConstant(false))
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackgroundV2", XC_MethodReplacement.returnConstant(false))
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook FgBgMonitorImpl err")
        }

        // hook MiscUtils
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk",
                Context::class.java, XC_MethodReplacement.returnConstant(true))
            Log.runtime(TAG, "hook MiscUtils successfully")
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook MiscUtils err")
        }
    }
}
