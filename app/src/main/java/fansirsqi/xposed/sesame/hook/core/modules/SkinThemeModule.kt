package fansirsqi.xposed.sesame.hook.core.modules

import android.content.Context
import fansirsqi.xposed.sesame.hook.core.HookModule
import fansirsqi.xposed.sesame.hook.skin.SkinHook
import fansirsqi.xposed.sesame.hook.theme.ThemeHookV2
import fansirsqi.xposed.sesame.hook.theme.ThemeManager
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.Log

class SkinThemeModule : HookModule {
    private val TAG = "SkinThemeModule"

    override fun onHandleLoadPackage(lpparam: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam) {
        try {
            SkinHook.setupHooks(lpparam.classLoader)
            ThemeHookV2.setupHooks(lpparam.classLoader)
        } catch (_: Throwable) {}
    }

    override fun onPostAppAttach(context: Context, classLoader: ClassLoader) {
        // 初始化皮肤模块 hooks
        try {
            SkinHook.setupHooks(classLoader)
            SkinHook.updateHooks(BaseModel.enableSkinModule.value)
        } catch (t: Throwable) {
            Log.runtime(TAG, "皮肤模块初始化异常:$t")
            Log.printStackTrace(TAG, t)
        }

        // 初始化主题Hook模块（动态版本）
        try {
            ThemeHookV2.setupHooks(classLoader)
            ThemeHookV2.applyHooks(BaseModel.enableSkinModule.value)
        } catch (t: Throwable) {
            Log.runtime(TAG, "主题Hook模块初始化异常:$t")
            Log.printStackTrace(TAG, t)
        }

        // 处理主题操作
        try {
            ThemeManager.handleThemeOperations()
            // 如果启用了主题模块，启动监控
            if (BaseModel.enableMonitorSkinModule.value) {
                ThemeManager.startOperationMonitor()
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "主题操作处理异常")
            Log.printStackTrace(TAG, t)
        }
    }
}
