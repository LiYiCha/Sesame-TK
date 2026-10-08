package fansirsqi.xposed.sesame.hook.resource

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import fansirsqi.xposed.sesame.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 为一次后台工作提供独立的 CPU 唤醒锁。
 *
 * 每次 acquire 返回独立的 [WakeLockLease]，调用方持有并关闭自己的 lease，
 * 避免并发执行时一个调用方错误释放（或重复获取跳过）另一个调用方的唤醒锁。
 * 锁一律带 timeout，即使调用方异常路径忘记 close，系统也会在超时后自动释放。
 */
object WakeLockManager {
    private const val TAG = "WakeLockManager"

    class WakeLockLease internal constructor(
        private val wakeLock: PowerManager.WakeLock?,
        private val source: String,
        private val acquiredAtElapsedMs: Long,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            val heldMs = (SystemClock.elapsedRealtime() - acquiredAtElapsedMs).coerceAtLeast(0L)
            runCatching {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            }.onFailure { error ->
                Log.printStackTrace(TAG, "释放唤醒锁失败[source=$source]", error)
            }
            Log.runtime(TAG, "🔑 唤醒锁已释放[source=$source held=${heldMs}ms]")
        }
    }

    @SuppressLint("WakelockTimeout")
    @JvmStatic
    fun acquire(context: Context, timeoutMs: Long, source: String): WakeLockLease {
        val acquiredAt = SystemClock.elapsedRealtime()
        val wakeLock = runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SesameTK::TaskWakeLock:$source")
                .apply {
                    setReferenceCounted(false)
                    acquire(timeoutMs)
                }
        }.onFailure { error ->
            Log.printStackTrace(TAG, "获取唤醒锁失败[source=$source]", error)
        }.getOrNull()

        if (wakeLock != null) {
            Log.runtime(TAG, "🔒 唤醒锁已获取[source=$source timeout=${timeoutMs}ms]")
        }
        return WakeLockLease(wakeLock, source, acquiredAt)
    }
}
