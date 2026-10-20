package fansirsqi.xposed.sesame.hook.network.capture

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * pending 请求登记表：按回调对象弱引用挂链，重叠标记 ambiguous，超时由清扫任务终态化。
 */
internal object PendingRegistry {
    const val PENDING_RESPONSE_TIMEOUT_MS = 60_000L

    class WeakIdentityKey internal constructor(callback: Any, queue: ReferenceQueue<Any>? = null) :
        WeakReference<Any>(callback, queue) {
        private val identityHashCode = System.identityHashCode(callback)

        override fun hashCode(): Int = identityHashCode

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WeakIdentityKey) return false
            val current = get() ?: return false
            return current === other.get()
        }
    }

    class PendingHostRequest internal constructor(
        val requestId: String,
        val method: String,
        val requestPayload: String?,
        val responseBeforeRequest: String? = null,
        val startedAtMs: Long,
        val source: RpcTrafficCapture.TrafficSource,
        val startedAtElapsedMs: Long = SystemClock.elapsedRealtime()
    ) {
        @Volatile
        var lastActivityElapsedMs: Long = startedAtElapsedMs

        @Volatile
        var callbackKept = false

        @Volatile
        var httpMethod: String? = null
    }

    class HttpBodyObservation internal constructor(
        val pending: PendingHostRequest,
        val streamIdentity: Int,
        val afterNoBodyResponse: Boolean
    ) {
        var position = 0L
        var markPosition: Long? = null

        @Volatile
        var incomplete = false
        var finished = false

        @Volatile
        var lastActivityElapsedMs: Long = SystemClock.elapsedRealtime()
    }

    class PendingRequestLinks internal constructor() {
        var body: HttpBodyObservation? = null
        val requests = mutableListOf<PendingHostRequest>()
        var completed = false
        var ambiguous = false
    }

    // 清扫任务触发时回调宿主引擎（输出 TIMEOUT/CONTEXT_LOST 终态事件）
    @Volatile
    var cleanupTick: (() -> Unit)? = null

    private val pendingLock = Any()
    private val pendingRequestQueue = ReferenceQueue<Any>()
    private val pendingByCallback = ConcurrentHashMap<WeakIdentityKey, PendingRequestLinks>()
    private val pendingById = ConcurrentHashMap<String, PendingHostRequest>()
    private val pendingCleanupHandler by lazy { Handler(Looper.getMainLooper()) }
    private val pendingCleanup = Runnable {
        runCatching { cleanupTick?.invoke() }
    }

    fun put(callback: Any, pending: PendingHostRequest) {
        synchronized(pendingLock) {
            if (pendingById[pending.requestId] !== pending) return
            val links = pendingByCallback.getOrPut(WeakIdentityKey(callback, pendingRequestQueue)) {
                PendingRequestLinks()
            }
            if (links.requests.any { it.requestId == pending.requestId }) return
            if (links.completed) {
                links.completed = false
                links.ambiguous = false
            }
            if (links.requests.isNotEmpty()) {
                links.ambiguous = true
            }
            links.requests.add(pending)
        }
    }

    fun peek(callback: Any): PendingHostRequest? = synchronized(pendingLock) {
        val links = pendingByCallback[WeakIdentityKey(callback)] ?: return@synchronized null
        if (links.ambiguous) null else links.requests.singleOrNull()
    }

    fun remove(callback: Any, requestId: String? = null): PendingHostRequest? =
        synchronized(pendingLock) {
            val key = WeakIdentityKey(callback)
            val links = pendingByCallback[key] ?: return@synchronized null
            val pending = if (requestId == null) {
                if (links.ambiguous) null else links.requests.singleOrNull()
            } else {
                links.requests.firstOrNull { it.requestId == requestId }
            }
            if (pending != null) links.requests.remove(pending)
            if (links.requests.isEmpty()) pendingByCallback.remove(key, links)
            pending
        }

    fun removeAll(callback: Any): List<PendingHostRequest> {
        val requests = synchronized(pendingLock) {
            pendingByCallback.remove(WeakIdentityKey(callback))?.requests?.toList().orEmpty()
        }
        requests.forEach { take(it) }
        return requests
    }

    fun take(pending: PendingHostRequest, completed: Boolean = false): Boolean = synchronized(pendingLock) {
        if (!pendingById.remove(pending.requestId, pending)) return@synchronized false
        pendingByCallback.values.forEach { links ->
            if (links.body?.pending === pending && links.body?.afterNoBodyResponse == false) links.body = null
            if (links.requests.remove(pending) && links.requests.isEmpty()) {
                links.completed = completed
                if (!completed) links.ambiguous = true
            }
        }
        scheduleCleanup()
        true
    }

    /** 请求登记进入超时跟踪（recordHostRequest 侧） */
    fun registerTracked(pending: PendingHostRequest) {
        synchronized(pendingLock) {
            pendingById[pending.requestId] = pending
            scheduleCleanup()
        }
    }

    /** 非终态响应：校验归属并刷新活跃时间；已不在跟踪则返回 false */
    fun touchTracked(pending: PendingHostRequest): Boolean = synchronized(pendingLock) {
        if (pendingById[pending.requestId] !== pending) return@synchronized false
        pending.lastActivityElapsedMs = SystemClock.elapsedRealtime()
        scheduleCleanup()
        true
    }

    /** BridgeCallback 响应侧读取关联状态：completed/ambiguous/requestIds */
    fun linksSnapshot(callback: Any): Triple<Boolean, Boolean, List<String>>? = synchronized(pendingLock) {
        pendingByCallback[WeakIdentityKey(callback)]?.let {
            Triple(it.completed, it.ambiguous, it.requests.map { request -> request.requestId })
        }
    }

    /** Future/流观察入口读取关联状态：唯一 pending/completed/ambiguous */
    fun linksOf(obj: Any): Triple<PendingHostRequest?, Boolean, Boolean>? = synchronized(pendingLock) {
        pendingByCallback[WeakIdentityKey(obj)]?.let { links ->
            Triple(
                if (links.ambiguous) null else links.requests.singleOrNull(),
                links.completed,
                links.ambiguous
            )
        }
    }

    fun isCompleted(obj: Any): Boolean = synchronized(pendingLock) {
        pendingByCallback[WeakIdentityKey(obj)]?.completed == true
    }

    fun bodyOf(stream: Any): HttpBodyObservation? = synchronized(pendingLock) {
        pendingByCallback[WeakIdentityKey(stream)]?.takeUnless { it.ambiguous }?.body
    }

    /** 将响应流绑定到 pending；共享流出现多归属时标记 ambiguous */
    fun bindBody(stream: Any, observation: HttpBodyObservation) {
        synchronized(pendingLock) {
            val links = pendingByCallback.getOrPut(WeakIdentityKey(stream, pendingRequestQueue)) {
                PendingRequestLinks()
            }
            if (!links.ambiguous && links.body == null) {
                links.body = observation
                scheduleCleanup()
            } else if (links.ambiguous || links.body?.pending !== observation.pending) {
                links.body = null
                links.ambiguous = true
            }
        }
    }

    fun markBodyFinished(body: HttpBodyObservation) {
        synchronized(pendingLock) {
            body.finished = true
            pendingByCallback.values.forEach { if (it.body === body) it.body = null }
            scheduleCleanup()
        }
    }

    /** body 仍有未完成的挂链（recordHttpBody 的 attached 判定） */
    fun isBodyAttached(body: HttpBodyObservation): Boolean = synchronized(pendingLock) {
        pendingByCallback.values.any { it.body === body && !it.ambiguous }
    }

    /** 同一 pending 是否还有其他未完成 body 观测 */
    fun hasOtherActiveBody(body: HttpBodyObservation): Boolean = synchronized(pendingLock) {
        pendingByCallback.values.any {
            val other = it.body
            other != null && other !== body && other.pending === body.pending && !other.finished
        }
    }

    /** 取流入口防重判定：该 pending 已绑定未完成的实际流 */
    fun isObservingStream(pending: PendingHostRequest): Boolean = synchronized(pendingLock) {
        pendingByCallback.values.any {
            it.body?.let { body -> body.pending === pending && !body.finished } == true
        }
    }

    fun clearAll() {
        synchronized(pendingLock) {
            pendingCleanupHandler.removeCallbacks(pendingCleanup)
            pendingByCallback.clear()
            pendingById.clear()
            while (pendingRequestQueue.poll() != null) {
                // 清空旧引用
            }
        }
    }

    /** 回收已被 GC 的弱引用上下文，返回随之失联的请求与 body 观测 */
    fun drainCollected(): Pair<List<PendingHostRequest>, List<HttpBodyObservation>> {
        val lost = mutableListOf<PendingHostRequest>()
        val lostBodies = mutableListOf<HttpBodyObservation>()
        synchronized(pendingLock) {
            while (true) {
                val key = pendingRequestQueue.poll() as? WeakIdentityKey ?: break
                val links = pendingByCallback.remove(key) ?: continue
                links.body?.takeIf { it.afterNoBodyResponse }?.let(lostBodies::add)
                links.requests.forEach { pending ->
                    val stillLinked = pendingByCallback.any { (otherKey, otherLinks) ->
                        otherKey.get() != null && pending in otherLinks.requests
                    }
                    if (!stillLinked && take(pending)) lost.add(pending)
                }
            }
        }
        return lost to lostBodies
    }

    /** 超时候选：过期 body 观测与过期请求 */
    fun expireCandidates(): Pair<List<HttpBodyObservation>, List<PendingHostRequest>> {
        val now = SystemClock.elapsedRealtime()
        val bodies = synchronized(pendingLock) {
            pendingByCallback.values.mapNotNull { it.body }.filter {
                it.afterNoBodyResponse && now - it.lastActivityElapsedMs >= PENDING_RESPONSE_TIMEOUT_MS
            }.distinct()
        }
        val requests = pendingById.values.toList()
            .filter { now - it.lastActivityElapsedMs >= PENDING_RESPONSE_TIMEOUT_MS }
        return bodies to requests
    }

    private fun scheduleCleanup() {
        pendingCleanupHandler.removeCallbacks(pendingCleanup)
        val nextActivity = (pendingById.values.map { it.lastActivityElapsedMs } +
            pendingByCallback.values.mapNotNull { it.body?.lastActivityElapsedMs }).minOrNull() ?: return
        val delay = (nextActivity + PENDING_RESPONSE_TIMEOUT_MS - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
        pendingCleanupHandler.postDelayed(pendingCleanup, minOf(delay, 5_000L))
    }
}
