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
        // 初始化皮肤和主题模块 ClassLoader
        try {
            SkinHook.setupHooks(classLoader)
            ThemeHookV2.setupHooks(classLoader)
        } catch (t: Throwable) {
            Log.runtime(TAG, "SkinThemeModule setupHooks 异常: $t")
        }
    }
}
