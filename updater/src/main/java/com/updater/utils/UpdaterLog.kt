package com.updater.utils

import java.lang.reflect.Method

/**
 * 更新模块精简日志门面
 * 优先反射复用主项目 fansirsqi.xposed.sesame.util.Log
 * 严禁任何高频循环日志输出，仅输出关键核心生命周期
 */
object UpdaterLog {

    private const val TAG = "SesameUpdater"
    private var logClass: Class<*>? = null
    private var logInfoMethod: Method? = null
    private var logErrorMethod: Method? = null

    init {
        try {
            logClass = Class.forName("fansirsqi.xposed.sesame.util.Log")
            logInfoMethod = logClass?.getMethod("i", String::class.java, String::class.java)
            logErrorMethod = logClass?.getMethod("e", String::class.java, String::class.java, Throwable::class.java)
        } catch (_: Throwable) {}
    }

    fun i(msg: String) {
        try {
            if (logInfoMethod != null) {
                logInfoMethod?.invoke(null, TAG, msg)
            }
        } catch (_: Throwable) {}
    }

    fun e(msg: String, throwable: Throwable? = null) {
        try {
            if (logErrorMethod != null && throwable != null) {
                logErrorMethod?.invoke(null, TAG, msg, throwable)
            } else if (logInfoMethod != null) {
                logInfoMethod?.invoke(null, TAG, "ERROR: $msg")
            }
        } catch (_: Throwable) {}
    }
}
