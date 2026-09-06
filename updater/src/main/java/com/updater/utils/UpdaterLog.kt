package com.updater.utils

/**
 * 更新日志委托接口（依赖倒置，解耦模块依赖）
 */
interface IUpdaterLogger {
    fun i(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}

/**
 * 更新模块精简日志门面
 * 严禁任何高频循环日志输出，仅输出关键核心生命周期
 * 支持由主工程直接注入项目日志实现（避免反射与循环依赖）
 */
object UpdaterLog {

    private const val TAG = "SesameUpdater"
    private var loggerDelegate: IUpdaterLogger? = null

    /**
     * 依赖注入：由主工程在启动时直接注册 Log 实现
     */
    fun setLogger(logger: IUpdaterLogger) {
        loggerDelegate = logger
    }

    fun i(msg: String) {
        val delegate = loggerDelegate
        if (delegate != null) {
            delegate.i(TAG, msg)
        } else {
            android.util.Log.i(TAG, msg)
        }
    }

    fun e(msg: String, throwable: Throwable? = null) {
        val delegate = loggerDelegate
        if (delegate != null) {
            delegate.e(TAG, msg, throwable)
        } else {
            android.util.Log.e(TAG, msg, throwable)
        }
    }
}
