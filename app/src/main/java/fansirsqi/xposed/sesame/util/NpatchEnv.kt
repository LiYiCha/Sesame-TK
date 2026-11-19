package fansirsqi.xposed.sesame.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * NPatch 运行环境探测。
 *
 * NPatch 有两种修补方式：
 * - 集成模式（--embed）：模块 APK 被放进目标应用 assets，模块代码与设置页都在目标进程内运行，
 *   配置目录天然与运行侧共享，这是当前唯一「开箱即用」的方式。
 * - 本地模式（--manager）：模块作为独立 APK 安装，目标应用由管理器按作用域加载它。
 *   此时模块设置页运行在模块自己的进程，与目标进程不共享私有目录，
 *   配置互通必须走 NPatch Remote API（Remote Preferences / Remote Files）。
 *
 * 本类只做只读探测，不依赖 NPatch SDK，缺失时安全降级；用于设置页展示与引导。
 */
object NpatchEnv {

    private const val TAG = "NpatchEnv"

    /** NPatch 管理器包名 */
    const val MANAGER_PACKAGE = "top.nkbe.npatch"

    /** 管理器暴露给模块 App 的 Remote Provider authority */
    const val REMOTE_AUTHORITY = "top.nkbe.npatch.remote"

    /** 管理器是否已安装（需要 AndroidManifest 中声明 <queries><package>，否则 Android 11+ 查不到） */
    @JvmStatic
    fun isManagerInstalled(context: Context?): Boolean {
        if (context == null) return false
        return try {
            context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Remote Provider 是否可解析（需要 <queries><provider android:authorities=...>）；找不到时返回 null，不抛异常 */
    @JvmStatic
    fun isRemoteProviderAvailable(context: Context?): Boolean {
        if (context == null) return false
        return context.packageManager.resolveContentProvider(REMOTE_AUTHORITY, 0) != null
    }

    /**
     * NPatch 运行模式：本地模式 / 集成模式。
     *
     * 判据是当前进程名：
     * 目标是支付宝进程 → 模块被嵌入目标 APK（集成模式）；
     * 是模块自身进程且管理器已安装 → 模块独立安装、由管理器下发作用域（本地模式）。
     * 两种情况都不成立时返回「未知」，并记录一次日志，避免静默显示错误结论。
     */
    @JvmStatic
    fun modeLabel(context: Context?, processName: String): String {
        val target = fansirsqi.xposed.sesame.data.General.PACKAGE_NAME
        val module = fansirsqi.xposed.sesame.data.General.MODULE_PACKAGE_NAME
        if (processName == target || processName.startsWith("$target:")) {
            return "集成模式"
        }
        if (processName == module || processName.startsWith("$module:")) {
            if (isManagerInstalled(context)) {
                return "本地模式"
            }
            Log.runtime(TAG, "无法识别 NPatch 模式：模块进程存在但未检测到管理器")
            return "未知"
        }
        Log.runtime(TAG, "无法识别 NPatch 模式：未知进程 $processName")
        return "未知"
    }

    /**
     * 供设置页展示的一行环境摘要。
     *
     * 只陈述可验证的事实：管理器是否安装、Remote Provider 是否可解析。
     * 不做「当前进程是否已被修补」的猜测——那需要框架侧握手，猜测性判定会误导用户。
     */
    @JvmStatic
    fun summary(context: Context?): String {
        val manager = isManagerInstalled(context)
        val provider = isRemoteProviderAvailable(context)
        return when {
            manager && provider -> "NPatch 管理器已安装（Remote Provider 可用）"
            manager -> "NPatch 管理器已安装（Remote Provider 不可用，需 NPatch ≥ v1.0.7）"
            else -> "未检测到 NPatch 管理器"
        }
    }
}
