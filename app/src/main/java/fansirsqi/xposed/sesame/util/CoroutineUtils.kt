package fansirsqi.xposed.sesame.util

import fansirsqi.xposed.sesame.hook.scheduler.TaskScheduler
import kotlinx.coroutines.*

/**
 * 协程工具类
 * 
 * 提供协程相关的通用功能，用于替代传统的线程操作
 */
object CoroutineUtils {
    
    /**
     * 协程安全的延迟方法
     * 
     * 在协程环境中使用 delay()，在非协程环境中降级到 Thread.sleep()
     * 
     * @param millis 延迟毫秒数
     */
    @JvmStatic
    suspend fun delayCompat(millis: Long) {
        if (TaskScheduler.isStopped()) {
            throw CancellationException("任务已被用户停止，终止延迟等待")
        }
        try {
            kotlinx.coroutines.delay(millis)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.printStackTrace("协程延迟异常", e)
            // 如果协程延迟失败，降级到线程休眠
            try {
                Thread.sleep(millis)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
    
    /**
     * 兼容性延迟方法（同步版本）
     *
     * 在当前线程中执行延迟，自动处理协程和非协程环境。
     * 分段休眠并在每段开始前检查全局停止状态：任务被用户停止后立即抛出取消异常，
     * 确保蚂蚁森林/庄园等基于本方法的循环任务能在停止广播后及时退出，
     * 而不是睡满整个延迟周期再继续（runBlocking 脱离 Job 树，无法感知协程取消）。
     */
    @JvmStatic
    fun sleepCompat(millis: Long) {
        var remaining = millis
        while (remaining > 0) {
            if (TaskScheduler.isStopped()) {
                throw CancellationException("任务已被用户停止，终止延迟等待")
            }
            val step = if (remaining > SLEEP_CHUNK_MS) SLEEP_CHUNK_MS else remaining
            try {
                runBlocking {
                    delay(step)
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                // 降级到传统的 Thread.sleep()
                try {
                    Thread.sleep(step)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Log.runtime("CoroutineUtils", "延迟被中断: ${ie.message}")
                    throw CancellationException("延迟被中断: ${ie.message}")
                }
            }
            remaining -= step
        }
    }

    /** 分段休眠的步长（毫秒），保证停止状态最长该时长内被感知 */
    private const val SLEEP_CHUNK_MS = 200L
    
    /**
     * 在指定调度器上运行协程
     */
    @JvmStatic
    fun runOnDispatcher(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return CoroutineScope(dispatcher + SupervisorJob()).launch {
            try {
                block()
            } catch (e: Exception) {
                Log.printStackTrace("协程执行异常", e)
            }
        }
    }
    
    /**
     * 在IO调度器上运行协程
     */
    @JvmStatic
    fun runOnIO(block: suspend CoroutineScope.() -> Unit): Job {
        return runOnDispatcher(Dispatchers.IO, block)
    }
    
    /**
     * 在计算调度器上运行协程
     */
    @JvmStatic
    fun runOnComputation(block: suspend CoroutineScope.() -> Unit): Job {
        return runOnDispatcher(Dispatchers.Default, block)
    }
    
    /**
     * 同步执行协程代码块
     * 
     * 警告：此方法会阻塞当前线程，仅在必要时使用
     */
    @JvmStatic
    fun <T> runBlockingSafe(
        timeout: Long = 30000, // 30秒默认超时
        block: suspend CoroutineScope.() -> T
    ): T? {
        return try {
            runBlocking {
                withTimeout(timeout) {
                    block()
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.error("CoroutineUtils", "协程执行超时: ${timeout}ms")
            null
        } catch (e: Exception) {
            Log.printStackTrace("协程同步执行异常", e)
            null
        }
    }
}
