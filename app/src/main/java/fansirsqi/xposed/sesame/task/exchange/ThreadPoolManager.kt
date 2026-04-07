package fansirsqi.xposed.sesame.task.exchange

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 线程池管理类
 */
object ThreadPoolManager {
    /**
     * 网络请求专用线程池
     */
    @JvmField
    val NETWORK_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(30)

    /**
     * 定时任务调度器
     */
    @JvmField
    val RETRY_SCHEDULER: ScheduledExecutorService = Executors.newScheduledThreadPool(10)

    /**
     * 优雅关闭所有线程池
     * 应在应用退出时调用
     */
    @JvmStatic
    fun shutdown() {
        shutdownExecutor(NETWORK_EXECUTOR, "NETWORK_EXECUTOR")
        shutdownExecutor(RETRY_SCHEDULER, "RETRY_SCHEDULER")
    }

    /**
     * 关闭单个线程池
     */
    private fun shutdownExecutor(executor: ExecutorService?, name: String) {
        if (executor == null || executor.isShutdown) {
            return
        }

        executor.shutdown()
        try {
            // 等待5秒让任务完成
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                // 如果5秒后还有任务未完成，强制关闭
                executor.shutdownNow()
                // 再等待2秒确认关闭
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("线程池 $name 未能正常关闭")
                }
            }
        } catch (e: InterruptedException) {
            // 如果等待被中断，强制关闭
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
