package fansirsqi.xposed.sesame.util;
import fansirsqi.xposed.sesame.hook.scheduler.TaskScheduler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public class ThreadUtil {
    private static final String TAG = ThreadUtil.class.getSimpleName();
    public static void shutdownAndWait(Thread thread, long timeout, TimeUnit unit) {
        if (thread != null) {
            thread.interrupt();
            if (timeout > -1L) {
                try {
                    thread.join(unit.toMillis(timeout));
                } catch (InterruptedException e) {
                    Log.runtime(TAG, "thread shutdownAndWait err:");
                    Log.printStackTrace(TAG, e);
                }
            }
        }
    }
    public static void shutdownNow(ExecutorService pool) {
        if (pool != null && !pool.isShutdown()) {
            pool.shutdownNow();
        }
    }
    /**
     * 使当前线程暂停指定的毫秒数。
     * 中断处理：若因全局停止（TaskScheduler.isStopped）引起，仅打印 runtime 日志，
     * 避免 error 通知和异常栈；否则按原有逻辑处理。
     *
     * @param millis 毫秒数。
     */
    public static void sleep(long millis) {
        boolean stopped = TaskScheduler.isStopped();
        if (Thread.currentThread().isInterrupted()) {
            if (stopped) {
                Log.runtime("ThreadUtil", "任务已停止，跳过 sleep");
            } else {
                Log.system("ThreadUtil", "Thread already interrupted, skipping sleep");
            }
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // 恢复中断状态，让调用方可以检测到中断
            Thread.currentThread().interrupt();
            if (TaskScheduler.isStopped()) {
                Log.runtime("ThreadUtil", "任务已停止，sleep 被中断");
            } else {
                Log.printStackTrace("ThreadUtil Thread sleep interrupted", e);
            }
        }
    }
    public boolean shutdownAndAwaitTermination(ExecutorService pool) {
        try {
            shutdownAndAwaitTermination(pool, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.runtime(TAG, "thread shutdownAndWait err:");
            Log.printStackTrace(TAG, e);
            return false;
        }
        return true;
    }
    public static void shutdownAndAwaitTermination(ExecutorService pool, long timeout, TimeUnit unit) {
        if (pool != null && !pool.isShutdown()) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                    if (!pool.awaitTermination(timeout, unit)) {
                        Log.runtime(TAG, "thread pool can't close");
                    }
                }
            } catch (InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 创建并启动一个线程执行任务。
     *
     * @param task 要执行的任务。
     */
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    public static void execute(Runnable task) {
        executor.execute(task);
    }
}
