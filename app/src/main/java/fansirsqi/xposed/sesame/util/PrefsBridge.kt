package fansirsqi.xposed.sesame.util

import android.content.Context
import de.robv.android.xposed.XSharedPreferences
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.hook.context.AppContext

/**
 * 配置在「模块设置页进程」与「目标应用注入进程」之间的共享通道。
 *
 * 设置页把配置镜像写入模块自身的 SharedPreferences；注入进程用 XSharedPreferences 读取同一份文件。
 * 跨 UID 的读取由框架的文件服务完成（Vector：VectorServiceClient.getPrefsPath + SELinuxHelper），
 * 因此这是 NPatch 本地模式下传统模块的标准做法，不需要 NPatch Remote API，也不需要连接管理器。
 *
 * 方向是单向的：设置页写、注入进程只读。注入进程的写入一律落在本地文件（见 Config），不回写这里。
 */
object PrefsBridge {

    private const val TAG = "PrefsBridge"

    /** SharedPreferences 文件名，需与 LSPosed 的 xposedsharedprefs 声明配合 */
    const val PREFS_NAME = "sesame_config"

    private const val KEY_PREFIX = "config_"

    /**
     * 模块 App 的上下文，只在模块进程（设置页）存在。
     * 注入进程运行在支付宝内，Application 不是 MyApplication，这里保持 null。
     */
    @Volatile
    private var moduleContext: Context? = null

    /** 由 MyApplication.onCreate 调用 */
    @JvmStatic
    fun init(context: Context) {
        moduleContext = context.applicationContext
    }

    private fun keyOf(userId: String?): String =
        KEY_PREFIX + if (userId.isNullOrEmpty() || userId == "默认") "default" else userId

    /**
     * 设置页保存配置后调用：把 JSON 镜像到模块 SharedPreferences。
     *
     * @return 是否写入成功；注入进程与未初始化的进程返回 false
     */
    @JvmStatic
    fun writeConfig(userId: String?, json: String?): Boolean {
        if (json.isNullOrEmpty()) {
            return false
        }
        // 注入进程里的 Context 属于支付宝，写进去也不会被设置页读到
        if (AppContext.isHooked()) {
            return false
        }
        val context = moduleContext ?: return false
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keyOf(userId), json)
            .commit()
    }

    /**
     * 注入进程读取设置页保存的配置。
     *
     * @return 配置 JSON；未注入、文件不可读或没有该账号的配置时返回 null，由调用方回退本地文件
     */
    @JvmStatic
    fun readConfig(userId: String?): String? {
        if (!AppContext.isHooked()) {
            return null
        }
        val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, PREFS_NAME)
        if (!prefs.file.canRead()) {
            Log.runtime(TAG, "模块配置不可读: ${prefs.file.absolutePath}")
            return null
        }
        return prefs.getString(keyOf(userId), null)
    }

    /**
     * 供任务日志使用的一行状态：当前配置来自哪里。
     * 只有注入进程有意义，设置页调用返回「设置页进程」。
     */
    @JvmStatic
    fun sourceLabel(userId: String?): String {
        if (!AppContext.isHooked()) {
            return "设置页进程"
        }
        val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, PREFS_NAME)
        if (!prefs.file.canRead()) {
            return "本地文件（模块配置不可读）"
        }
        return if (prefs.getString(keyOf(userId), null) != null) {
            "模块 SharedPreferences"
        } else {
            "本地文件（模块配置无此账号）"
        }
    }
}
