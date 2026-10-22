package fansirsqi.xposed.sesame.hook.resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import fansirsqi.xposed.sesame.util.Log;

/**
 * ExecutorService 资源管理器
 * 提供完善的关闭逻辑
 */
public class ExecutorManager {
    private static final String TAG = ExecutorManager.class.getSimpleName();

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
                return true;
            }

            // 3. 超时后强制关闭
            Log.runtime(TAG, name + " 等待超时，强制关闭");
            executor.shutdownNow();

            // 4. 再次等待
            if (executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.runtime(TAG, name + " 已强制关闭");
                return true;
            }

            // 5. 仍然无法关闭
            Log.error(TAG, "⚠️ " + name + " 无法关闭，可能存在资源泄漏");
            return false;

        } catch (InterruptedException e) {
            Log.error(TAG, name + " 关闭时被中断");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
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
                return true;
            }

            // 3. 超时后强制关闭
            Log.runtime(TAG, name + " 等待超时，强制关闭");
            scheduler.shutdownNow();

            // 4. 再次等待
            if (scheduler.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.runtime(TAG, name + " 已强制关闭");
                return true;
            }

            // 5. 仍然无法关闭
            Log.error(TAG, "⚠️ " + name + " 无法关闭，可能存在资源泄漏");
            return false;

        } catch (InterruptedException e) {
            Log.error(TAG, name + " 关闭时被中断");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            Log.error(TAG, name + " 关闭时发生异常: " + e.getMessage());
            Log.printStackTrace(TAG, e);
            return false;
        }
    }
}
