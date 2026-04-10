package fansirsqi.xposed.sesame.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fansirsqi.xposed.sesame.BuildConfig;
import fansirsqi.xposed.sesame.model.BaseModel;

/**
 * 日志工具类，负责初始化和管理各种类型的日志记录器，并提供日志输出方法。
 */
public class Log {
    private static final String TAG = "[" + BuildConfig.BUILD_TAG + BuildConfig.BUILD_NUMBER + "]";
    private static final Logger RUNTIME_LOGGER;
    private static final Logger SYSTEM_LOGGER;
    private static final Logger RECORD_LOGGER;
    private static final Logger DEBUG_LOGGER;
    private static final Logger FOREST_LOGGER;
    private static final Logger FARM_LOGGER;
    private static final Logger OTHER_LOGGER;
    private static final Logger ERROR_LOGGER;
    private static final Logger CAPTURE_LOGGER;
    private static final Logger HTTP_LOGGER;

    static {
        Logback.configureLogbackDirectly();
        RUNTIME_LOGGER = LoggerFactory.getLogger("runtime");
        SYSTEM_LOGGER = LoggerFactory.getLogger("system");
        RECORD_LOGGER = LoggerFactory.getLogger("record");
        DEBUG_LOGGER = LoggerFactory.getLogger("debug");
        FOREST_LOGGER = LoggerFactory.getLogger("forest");
        FARM_LOGGER = LoggerFactory.getLogger("farm");
        OTHER_LOGGER = LoggerFactory.getLogger("other");
        ERROR_LOGGER = LoggerFactory.getLogger("error");
        CAPTURE_LOGGER = LoggerFactory.getLogger("capture");
        HTTP_LOGGER = LoggerFactory.getLogger("http");
    }
    private static final String separator = "==================================================";
    private static String truncateLogMessage(String message) {
        if (message.length() > 16) {
            return message.substring(0, 16) + "...";
        }
        return message;
    }

    public static void system(String message) {
        SYSTEM_LOGGER.info(TAG + "{}", message);
    }

    public static void system(String TAG, String message) {
        system("[" + TAG + "]: " + message);
    }

    public static void runtime(String message) {
        system(message);
        RUNTIME_LOGGER.info(TAG + "{}", message);
    }

    public static void runtime(String TAG, String message) {
        runtime("[" + TAG + "]: " + message);
    }

    public static void record(String message) {
        runtime(message);
        if (BaseModel.getRecordLog().getValue()) {
            RECORD_LOGGER.info(TAG + "{}", message);
        }
    }

    public static void record(String TAG, String message) {
        record("[" + TAG + "]: " + message);
    }

    public static void forest(String message) {
        record(message);
        FOREST_LOGGER.info("{}", message);
    }

    public static void forest(String TAG, String message) {
        forest("[" + TAG + "]: " + message);
    }

    public static void farm(String message) {
        record(message);
        FARM_LOGGER.info("{}", message);
    }

    public static void farm(String TAG, String message) {
        farm("[" + TAG + "]: " + message);
    }

    public static void other(String message) {
        record(message);
        OTHER_LOGGER.info("{}", message);
    }

    public static void other(String TAG, String message) {
        other("[" + TAG + "]: " + message);
    }

    public static void debug(String message) {
        runtime(message);
        DEBUG_LOGGER.info("{}", message);
        // 添加结束分隔符
        DEBUG_LOGGER.info(separator);
    }

    public static void debug(String TAG, String message) {
        debug("[" + TAG + "]: " + message);
        DEBUG_LOGGER.info(separator);
    }

    public static void error(String message) {
        if (BaseModel.getErrNotify().getValue()) {
            Notify.sendNewNotification("‼️芝麻粒运行时发生异常，详情查看异常日志", message);
        }
        runtime(message);
        ERROR_LOGGER.error(TAG + "{}", message);

        // 添加结束分隔符
        ERROR_LOGGER.error(separator);
    }

    public static void error(String TAG, String message) {
        error("[" + TAG + "]: " + message);
        // 添加结束分隔符
        ERROR_LOGGER.error(separator);
    }

    public static void capture(String message) {
        CAPTURE_LOGGER.info(TAG + "{}", message);
    }

    public static void capture(String TAG, String message) {
        capture("[" + TAG + "]: " + message);
    }

    public static void http(String message) {
        HTTP_LOGGER.info("{}", message);
    }

    public static void http(String TAG, String message) {
        http("[" + TAG + "]: " + message);
    }

    public static void printStackTrace(Throwable th) {
        String stackTrace = "error: " + android.util.Log.getStackTraceString(th);
        error(stackTrace);
    }

    public static void printStackTrace(String msg, Throwable th) {
        String stackTrace = "Throwable error: " + android.util.Log.getStackTraceString(th);
        error(msg, stackTrace);
    }

    public static void printStackTrace(String TAG, String msg, Throwable th) {
        String stackTrace = "[" + TAG + "] Throwable error: " + android.util.Log.getStackTraceString(th);
        error(msg, stackTrace);
    }

    public static void printStackTrace(Exception e) {
        String stackTrace = "Exception error: " + android.util.Log.getStackTraceString(e);
        error(stackTrace);
    }

    public static void printStackTrace(String msg, Exception e) {
        String stackTrace = "Throwable error: " + android.util.Log.getStackTraceString(e);
        error(msg, stackTrace);
    }

    public static void printStackTrace(String TAG, String msg, Exception e) {
        String stackTrace = "[" + TAG + "] Throwable error: " + android.util.Log.getStackTraceString(e);
        error(msg, stackTrace);
    }
}
