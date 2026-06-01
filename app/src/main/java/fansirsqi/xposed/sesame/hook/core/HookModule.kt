package fansirsqi.xposed.sesame.hook.core

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook 模块接口，定义了不同生命周期阶段的 Hook 回调
 */
interface HookModule {
    /**
     * 在 handleLoadPackage 阶段执行的 Hook
     */
    fun onHandleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {}

    /**
     * 在 Application.attach 之前执行的 Hook (Pre-Attach)
     */
    fun onPreAppAttach(context: Context, classLoader: ClassLoader) {}

    /**
     * 在 Application.attach 之后执行的 Hook (Post-Attach)
     */
    fun onPostAppAttach(context: Context, classLoader: ClassLoader) {}
}
