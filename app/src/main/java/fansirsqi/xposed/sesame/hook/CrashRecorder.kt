package fansirsqi.xposed.sesame.hook

import android.os.Process
import android.util.Log
import fansirsqi.xposed.sesame.util.Log as SesameLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 宿主进程未捕获异常记录器
 *
 * 正常路径：install() 在 handleLoadPackage 最早期执行一次（<1ms，之后零开销）。
 * 崩滞路径：一次性 daemon 线程把完整堆栈写入 error 日志，最多等待 300ms，
 * 无论写入成败最终都委托原 handler，保持宿主原崩溃行为不变。
 *
 * 只抓 Java/Kotlin 层未捕获异常；Native 崩溃与外部 SIGKILL 不在覆盖范围。
 */
object CrashRecorder {
    private const val TAG = "CrashRecorder"
    private const val WAIT_MS = 300L

    private val installed = AtomicBoolean(false)
    private val recorded = AtomicBoolean(false)

    @Volatile
    private var originHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var ourHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * 安装记录器（进程内仅一次，重复调用为空操作）
     */
    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        val handler = Thread.UncaughtExceptionHandler { thread, throwable -> handle(thread, throwable) }
        ourHandler = handler
        originHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(handler)
        // 预热行：确认安装成功，同时保证崩溃路径依赖的 logback 在此刻必然已初始化
        SesameLog.runtime(TAG, "宿主崩溃记录器已安装")
    }

    /**
     * Application.attach 后补检：框架可能在 install 之后才设置默认 handler 把记录器顶掉，
     * 被顶掉时重新装回（origin 保持 install 时捕获的原 handler，避免二次链式引用成环）
     */
    @JvmStatic
    fun ensureInstalled() {
        val handler = ourHandler ?: return
        if (installed.get() && Thread.getDefaultUncaughtExceptionHandler() !== handler) {
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    private fun handle(thread: Thread, throwable: Throwable) {
        try {
            // 连环崩溃只记录第一条
            if (recorded.compareAndSet(false, true)) {
                val latch = CountDownLatch(1)
                val writer = Thread({
                    try {
                        val stack = Log.getStackTraceString(throwable)
                        SesameLog.error(TAG, "宿主进程未捕获异常 thread=${thread.name} type=${throwable.javaClass.name}\n$stack")
                    } catch (t: Throwable) {
                        // 日志写入失败不能影响崩溃流程
                    } finally {
                        latch.countDown()
                    }
                }, "CrashRecorder-writer")
                writer.isDaemon = true
                writer.start()
                latch.await(WAIT_MS, TimeUnit.MILLISECONDS)
            }
        } catch (t: Throwable) {
            // ignore
        } finally {
            val origin = originHandler
            if (origin != null) {
                origin.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }
}
