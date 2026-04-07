package fansirsqi.xposed.sesame.hook.resource;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;

import fansirsqi.xposed.sesame.util.Log;

/**
 * WakeLock 管理器
 * 自动管理 WakeLock 的获取和释放，防止资源泄漏
 */
public class WakeLockManager {
    private static final String TAG = WakeLockManager.class.getSimpleName();
    private static PowerManager.WakeLock wakeLock;
    private static boolean isAcquired = false;
    private static final Object lock = new Object();

    /**
     * 获取 WakeLock
     *
     * @param context 上下文
     * @param tag WakeLock 标签
     */
    @SuppressLint("WakelockTimeout")
    public static void acquire(Context context, String tag) {
        synchronized (lock) {
            if (isAcquired) {
                Log.runtime(TAG, "WakeLock 已经被获取，跳过重复获取");
                return;
            }

            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
                    wakeLock.acquire();
                    isAcquired = true;
                    Log.runtime(TAG, "WakeLock 已获取: " + tag);
                }
            } catch (Exception e) {
                Log.error(TAG, "获取 WakeLock 失败: " + e.getMessage());
                Log.printStackTrace(TAG, e);
            }
        }
    }

    /**
     * 释放 WakeLock
     */
    public static void release() {
        synchronized (lock) {
            if (!isAcquired) {
                Log.runtime(TAG, "WakeLock 未被获取，无需释放");
                return;
            }

            try {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                    Log.runtime(TAG, "WakeLock 已释放");
                }
            } catch (Exception e) {
                Log.error(TAG, "释放 WakeLock 失败: " + e.getMessage());
                Log.printStackTrace(TAG, e);
            } finally {
                wakeLock = null;
                isAcquired = false;
            }
        }
    }

    /**
     * 检查 WakeLock 是否被持有
     *
     * @return true 如果 WakeLock 被持有
     */
    public static boolean isHeld() {
        synchronized (lock) {
            return isAcquired && wakeLock != null && wakeLock.isHeld();
        }
    }

    /**
     * 强制释放 WakeLock（用于清理资源泄漏）
     */
    public static void forceRelease() {
        synchronized (lock) {
            try {
                if (wakeLock != null) {
                    if (wakeLock.isHeld()) {
                        wakeLock.release();
                        Log.runtime(TAG, "WakeLock 已强制释放");
                    }
                    wakeLock = null;
                }
            } catch (Exception e) {
                Log.error(TAG, "强制释放 WakeLock 失败: " + e.getMessage());
                Log.printStackTrace(TAG, e);
            } finally {
                isAcquired = false;
            }
        }
    }

    /**
     * 检查资源泄漏
     * 如果 WakeLock 被持有但不应该被持有，则记录警告
     */
    public static void checkLeak() {
        synchronized (lock) {
            if (isAcquired && wakeLock != null && wakeLock.isHeld()) {
                Log.error(TAG, "⚠️ 检测到 WakeLock 资源泄漏！WakeLock 仍然被持有");
            }
        }
    }
}
