package fansirsqi.xposed.sesame.hook.resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import fansirsqi.xposed.sesame.util.Log;

/**
 * ExecutorService 资源管理器
 * 提供完善的关闭逻辑和资源泄漏检测
 */
public class ExecutorManager {
    private static final String TAG = ExecutorManager.class.getSimpleName();

    // 资源泄漏检测
    private static final AtomicLong activeExecutors = new AtomicLong(0);
    private static final AtomicLong activeSchedulers = new AtomicLong(0);

    /**
     * 注册 ExecutorService（用于泄漏检测）
     */
    public static void registerExecutor() {
        activeExecutors.incrementAndGet();
        // Log.runtime(TAG, "ExecutorService 已注册，当前活跃数: " + count);
    }

    /**
     * 注册 ScheduledExecutorService（用于泄漏检测）
     */
    public static void registerScheduler() {
        activeSchedulers.incrementAndGet();
        // Log.runtime(TAG, "ScheduledExecutorService 已注册，当前活跃数: " + count);
    }

    /**
     * 优雅关闭 ExecutorService
     *
     * @param executor 要关闭的执行器
     * @param name 执行器名称（用于日志）
     * @param timeoutSeconds 超时时间（秒）
     * @return true 如果成功关闭
     */
    public static boolean shutdownExecutor(ExecutorService executor, String name, int timeoutSeconds) {
        if (executor == null) {
            Log.runtime(TAG, name + " 为 null，无需关闭");
            return true;
        }

        if (executor.isShutdown()) {
            Log.runtime(TAG, name + " 已经关闭");
            return true;
        }

        try {
            Log.runtime(TAG, "开始关闭 " + name);

            // 1. 停止接受新任务
            executor.shutdown();

            // 2. 等待现有任务完成
            if (executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.runtime(TAG, name + " 已优雅关闭");
                activeExecutors.decrementAndGet();
                return true;
            }

            // 3. 超时后强制关闭
            Log.runtime(TAG, name + " 等待超时，强制关闭");
            executor.shutdownNow();

            // 4. 再次等待
            if (executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.runtime(TAG, name + " 已强制关闭");
                activeExecutors.decrementAndGet();
                return true;
            }

            // 5. 仍然无法关闭
            Log.error(TAG, "⚠️ " + name + " 无法关闭，可能存在资源泄漏");
            return false;

        } catch (InterruptedException e) {
            Log.error(TAG, name + " 关闭时被中断");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            activeExecutors.decrementAndGet();
            return false;
        } catch (Exception e) {
            Log.error(TAG, name + " 关闭时发生异常: " + e.getMessage());
            Log.printStackTrace(TAG, e);
            return false;
        }
    }

    /**
     * 优雅关闭 ScheduledExecutorService
     *
     * @param scheduler 要关闭的调度器
     * @param name 调度器名称（用于日志）
     * @param timeoutSeconds 超时时间（秒）
     * @return true 如果成功关闭
     */
    public static boolean shutdownScheduler(ScheduledExecutorService scheduler, String name, int timeoutSeconds) {
        if (scheduler == null) {
            Log.runtime(TAG, name + " 为 null，无需关闭");
            return true;
        }

        if (scheduler.isShutdown()) {
            Log.runtime(TAG, name + " 已经关闭");
            return true;
        }

        try {
            Log.runtime(TAG, "开始关闭 " + name);

            // 1. 停止接受新任务
            scheduler.shutdown();

            // 2. 等待现有任务完成
            if (scheduler.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.runtime(TAG, name + " 已优雅关闭");
                activeSchedulers.decrementAndGet();
                return true;
            }

            // 3. 超时后强制关闭
            Log.runtime(TAG, name + " 等待超时，强制关闭");
            scheduler.shutdownNow();

            // 4. 再次等待
            if (scheduler.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.runtime(TAG, name + " 已强制关闭");
                activeSchedulers.decrementAndGet();
                return true;
            }

            // 5. 仍然无法关闭
            Log.error(TAG, "⚠️ " + name + " 无法关闭，可能存在资源泄漏");
            return false;

        } catch (InterruptedException e) {
            Log.error(TAG, name + " 关闭时被中断");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            activeSchedulers.decrementAndGet();
            return false;
        } catch (Exception e) {
            Log.error(TAG, name + " 关闭时发生异常: " + e.getMessage());
            Log.printStackTrace(TAG, e);
            return false;
        }
    }

    /**
     * 检查资源泄漏
     * 如果有未关闭的执行器，记录警告
     */
    public static void checkLeak() {
        /*
        long executorCount = activeExecutors.get();
        long schedulerCount = activeSchedulers.get();

        if (executorCount > 0) {
            Log.error(TAG, "⚠️ 检测到 ExecutorService 资源泄漏！活跃数: " + executorCount);
        }

        if (schedulerCount > 0) {
            Log.error(TAG, "⚠️ 检测到 ScheduledExecutorService 资源泄漏！活跃数: " + schedulerCount);
        }

        if (executorCount == 0 && schedulerCount == 0) {
            Log.runtime(TAG, "✓ 未检测到 ExecutorService 资源泄漏");
        }
        */
    }

    /**
     * 获取当前活跃的执行器数量
     */
    public static long getActiveExecutorCount() {
        return activeExecutors.get();
    }

    /**
     * 获取当前活跃的调度器数量
     */
    public static long getActiveSchedulerCount() {
        return activeSchedulers.get();
    }

    /**
     * 重置计数器（用于测试或重新初始化）
     */
    public static void resetCounters() {
        activeExecutors.set(0);
        activeSchedulers.set(0);
        Log.runtime(TAG, "资源计数器已重置");
    }
}
