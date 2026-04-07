package fansirsqi.xposed.sesame.hook;

import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.XposedHelpers;
import fansirsqi.xposed.sesame.util.Log;

/**
 * 类检查工具类
 */
public class ClassChecker {
    private static final String TAG = "ClassChecker";
    private static final long MAX_WAIT_TIME = 5 * 60 * 1000; // 5分钟
    private static final long CHECK_INTERVAL = 5000; // 5秒检查一次

    public interface ClassCheckCallback {
        void onCheckComplete(boolean allClassesExist);
    }

    public static void waitForClasses(ClassLoader classLoader, String[] requiredClasses, ClassCheckCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        long startTime = System.currentTimeMillis();

        Thread checkThread = new Thread(() -> {
            while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME) {
                if (checkRequiredClassesExist(classLoader, requiredClasses)) {
                    mainHandler.post(() -> callback.onCheckComplete(true));
                    return;
                }

                try {
                    Thread.sleep(CHECK_INTERVAL);
                } catch (InterruptedException e) {
                    Log.printStackTrace(e);
                    mainHandler.post(() -> callback.onCheckComplete(false));
                    return;
                }
            }

            // 超时，仍然回调
            mainHandler.post(() -> callback.onCheckComplete(false));
        });

        checkThread.start();
    }

    private static boolean checkRequiredClassesExist(ClassLoader classLoader, String[] requiredClasses) {
        for (String className : requiredClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
                if (clazz == null) {
                    Log.runtime(TAG, "类不存在，等待重试: " + className);
                    return false;
                }
            } catch (Exception e) {
                Log.runtime(TAG, "检查类时出错: " + className + ", 错误: " + e.getMessage());
                return false;
            }
        }
        return true;
    }
}
