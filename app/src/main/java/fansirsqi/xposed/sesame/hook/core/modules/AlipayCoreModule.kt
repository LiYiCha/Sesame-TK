package fansirsqi.xposed.sesame.hook.core.modules

import android.content.Context
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import fansirsqi.xposed.sesame.data.RunType
import fansirsqi.xposed.sesame.data.ViewAppInfo
import fansirsqi.xposed.sesame.hook.core.HookModule
import fansirsqi.xposed.sesame.util.Log

class AlipayCoreModule : HookModule {
    private val TAG = "AlipayCoreModule"

    override fun onHandleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if ("fansirsqi.xposed.sesame" == lpparam.packageName) {
            try {
                val viewAppInfoClass = lpparam.classLoader.loadClass("fansirsqi.xposed.sesame.data.ViewAppInfo")
                XposedHelpers.callStaticMethod(viewAppInfoClass, "setRunType", RunType.ACTIVE.code)
            } catch (e: Exception) {
                try {
                    ViewAppInfo.setRunType(RunType.ACTIVE)
                } catch (ex: Exception) {
                    Log.printStackTrace(ex)
                }
            }
        }
    }

    override fun onPostAppAttach(context: Context, classLoader: ClassLoader) {
        Log.runtime(TAG, "AlipayCoreModule: Application attached.")
    }
}
