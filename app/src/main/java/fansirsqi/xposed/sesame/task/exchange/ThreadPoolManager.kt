package fansirsqi.xposed.sesame.task.exchange

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * 线程池管理类
 *
 * 池子按需惰性创建；"停止运行"时调用 [shutdownNow] 立即中断在途任务，
 * 下次运行时通过访问器自动重建，避免池子被关闭后任务无法执行。
 */
object ThreadPoolManager {

    @Volatile
    private var networkExecutor: ExecutorService? = null

    @Volatile
    private var retryScheduler: ScheduledExecutorService? = null

    /**
     * 网络请求专用线程池（惰性创建，被停止后自动重建）
     */
    @get:JvmStatic
    val NETWORK_EXECUTOR: ExecutorService
        get() {
            var e = networkExecutor
            if (e == null || e.isShutdown) {
                synchronized(this) {
                    e = networkExecutor
                    if (e == null || e.isShutdown) {
                        e = Executors.newFixedThreadPool(30)
                        networkExecutor = e
                    }
                }
            }
            return e!!
        }

    /**
     * 定时任务调度器（惰性创建，被停止后自动重建）
     */
    @get:JvmStatic
    val RETRY_SCHEDULER: ScheduledExecutorService
        get() {
            var s = retryScheduler
            if (s == null || s.isShutdown) {
                synchronized(this) {
                    s = retryScheduler
                    if (s == null || s.isShutdown) {
                        s = Executors.newScheduledThreadPool(10)
                        retryScheduler = s
                    }
                }
            }
            return s!!
        }

    /**
     * 立即中断所有在途任务并销毁线程池（用于"停止运行"）。
     * 池子将在下次访问 NETWORK_EXECUTOR / RETRY_SCHEDULER 时自动重建。
     */
    @JvmStatic
    fun shutdownNow() {
        networkExecutor?.shutdownNow()
        networkExecutor = null
        retryScheduler?.shutdownNow()
        retryScheduler = null
    }

    /**
     * 优雅关闭所有线程池
     * 应在应用退出时调用
     */
    @JvmStatic
    fun shutdown() {
        val ne = networkExecutor
        if (ne != null) shutdownExecutor(ne, "NETWORK_EXECUTOR")
        val rs = retryScheduler
        if (rs != null) shutdownExecutor(rs, "RETRY_SCHEDULER")
        shutdownNow()
    }

    /**
     * 关闭单个线程池
     */
    private fun shutdownExecutor(executor: ExecutorService, name: String) {
        if (executor.isShutdown) {
            return
        }

        executor.shutdown()
        try {
            // 等待5秒让任务完成
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                // 如果5秒后还有任务未完成，强制关闭
                executor.shutdownNow()
                // 再等待2秒确认关闭
                if (!executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
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
