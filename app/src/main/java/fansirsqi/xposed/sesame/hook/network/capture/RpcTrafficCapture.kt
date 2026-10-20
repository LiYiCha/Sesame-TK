package fansirsqi.xposed.sesame.hook.network.capture

import android.os.SystemClock
import fansirsqi.xposed.sesame.hook.network.capture.PendingRegistry.HttpBodyObservation
import fansirsqi.xposed.sesame.hook.network.capture.PendingRegistry.PendingHostRequest
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.CaptureFilter
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 抓包引擎门面：hook 支付宝网络封装层，请求与回调天然成对，记录追加到日志体系 capture 分类。
 *
 * hook 面拆分：RPC 封装层见 [RpcHooks]，HTTP/小程序见 [HttpHooks]，
 * pending 关联表见 [PendingRegistry]，对象快照见 [HttpSnapshot]。
 */
object RpcTrafficCapture {
    private const val TAG = "RpcTrafficCapture"
    private const val MAX_PAYLOAD_CHARS = 16_000
    private const val DISABLED_SOURCES_KEY = "RpcTrafficCapture_DisabledSources"

    enum class TrafficSource(val title: String) {
        ARIVER_RPC("RPC"),
        H5_RPC("H5 RPC"),
        MINI_HTTP("HTTP/小程序")
    }

    enum class TrafficPhase { REQUEST, RESPONSE, ERROR }

    class RpcTrafficEvent internal constructor(
        val phase: TrafficPhase,
        val source: TrafficSource,
        val method: String,
        val requestId: String? = null,
        val payload: String? = null,
        val elapsedMs: Long = -1L,
        val note: String? = null,
        val correlationStatus: String? = null
    ) {
        fun copy(
            phase: TrafficPhase = this.phase,
            payload: String? = this.payload,
            note: String? = this.note
        ): RpcTrafficEvent = RpcTrafficEvent(phase, source, method, requestId, payload, elapsedMs, note, correlationStatus)
    }

    @Volatile
    private var captureEnabledProvider: () -> Boolean = { BaseModel.enableHttpCapture.value }

    private val installLock = Any()
    private val eventSequence = AtomicLong(0L)

    @Volatile
    private var disabledSourcesCache: Set<String> = emptySet()

    @Volatile
    private var disabledSourcesExpiresAtMs = 0L

    private val structuredReferenceKeys = setOf(
        "taskType", "sceneCode", "taskStatus", "taskProgress", "taskRequire", "taskId", "taskGroupId",
        "game_id", "gameAppId", "algGameId", "actionType", "finishTaskActionType", "chInfo", "playTime",
        "resultCode", "resultDesc", "success", "awardCount", "incAwardCount", "rightsTimes",
        "alreadyReceiveAwardCount", "receiveStatus", "propGroup", "propType", "propId", "holdsNum",
        "userDayLeftAmount", "type", "apiName", "apiVersion", "appId", "outBizNo", "adBizNo", "taskToken"
    )

    private val structuredReferenceContainers = listOf(
        "", "taskBaseInfo", "taskInfo", "prodPlayParam", "prodPlayParam.taskCategorization", "data", "result",
        "params", "fetchedData", "fetchedData.params"
    )

    // ==================== 安装入口 ====================

    @JvmOverloads
    fun install(classLoader: ClassLoader, enabledProvider: () -> Boolean = { BaseModel.enableHttpCapture.value }): Boolean {
        captureEnabledProvider = enabledProvider
        PendingRegistry.cleanupTick = {
            drainCollectedPendingRequests()
            expirePendingRequests()
        }
        var installedAny = runCatching { HttpHooks.installMiniHttpHooks(classLoader) }
            .onFailure { runCatching { Log.runtime(TAG, "安装 HTTP 抓包失败: ${it.message}") } }
            .getOrDefault(false)
        synchronized(installLock) {
            val rpcInstalled = RpcHooks.installRpcBridgeHook(classLoader)
            val mtopInstalled = RpcHooks.installMtopHook(classLoader)
            if (rpcInstalled || mtopInstalled) {
                RpcHooks.installAriverResponseHook(classLoader)
                installedAny = true
            }
            if (RpcHooks.installH5Hook(classLoader)) {
                installedAny = true
            }
            Log.runtime(TAG, if (installedAny) "抓包 Hook 安装完成" else "未找到可安装的抓包 Hook")
            return installedAny
        }
    }

    fun newRequestId(): String = UUID.randomUUID().toString()

    // ==================== 来源开关（抓包设置页使用） ====================

    fun getDisabledSources(): Set<String> = readDisabledSources()

    fun setSourceEnabled(name: String, enabled: Boolean) {
        val current = readDisabledSources()
        val updated = if (enabled) current - name else current + name
        runCatching { DataStore.put(DISABLED_SOURCES_KEY, updated.joinToString(",")) }
        disabledSourcesCache = updated
        disabledSourcesExpiresAtMs = System.currentTimeMillis() + 1_000L
    }

    private fun readDisabledSources(): Set<String> {
        val now = System.currentTimeMillis()
        if (now < disabledSourcesExpiresAtMs) return disabledSourcesCache
        val set = runCatching {
            DataStore.get(DISABLED_SOURCES_KEY, String::class.java)
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())
        disabledSourcesCache = set
        disabledSourcesExpiresAtMs = now + 1_000L
        return set
    }

    internal fun isSourceEnabled(source: TrafficSource): Boolean = source.name !in readDisabledSources()

    internal fun isCaptureEnabled(): Boolean {
        val enabled = runCatching { captureEnabledProvider.invoke() }.getOrDefault(false)
        if (!enabled) {
            PendingRegistry.clearAll()
        }
        return enabled
    }

    // ==================== 记录与派发 ====================

    internal fun recordHostRequest(pending: PendingHostRequest) {
        if (!isCaptureEnabled() || isFiltered(pending.method, pending.requestPayload)) {
            return
        }
        drainCollectedPendingRequests()
        emitEvent(
            RpcTrafficEvent(
                phase = TrafficPhase.REQUEST,
                source = pending.source,
                method = pending.method,
                requestId = pending.requestId,
                payload = pending.requestPayload
            )
        )
        PendingRegistry.registerTracked(pending)
    }

    internal fun recordHostResponse(
        pending: PendingHostRequest,
        responsePayload: String?,
        terminal: Boolean = true,
        note: String? = null
    ): Boolean {
        if (!isCaptureEnabled()) return false
        val recorded = if (terminal) {
            PendingRegistry.take(pending, completed = true)
        } else {
            PendingRegistry.touchTracked(pending)
        }
        if (!recorded) return false
        emitEvent(
            RpcTrafficEvent(
                phase = TrafficPhase.RESPONSE,
                source = pending.source,
                method = pending.method,
                requestId = pending.requestId,
                payload = responsePayload,
                elapsedMs = SystemClock.elapsedRealtime() - pending.startedAtElapsedMs,
                note = note ?: if (terminal) null else "callback_kept",
                correlationStatus = "MATCHED"
            )
        )
        if (terminal) forwardHookData(pending, responsePayload)
        return true
    }

    internal fun recordHostError(
        pending: PendingHostRequest,
        note: String,
        correlationStatus: String? = null
    ) {
        if (!isCaptureEnabled()) return
        if (!PendingRegistry.take(pending, completed = correlationStatus == null || correlationStatus == "CANCELLED")) return
        emitEvent(
            RpcTrafficEvent(
                phase = TrafficPhase.ERROR,
                source = pending.source,
                method = pending.method,
                requestId = pending.requestId,
                elapsedMs = SystemClock.elapsedRealtime() - pending.startedAtElapsedMs,
                note = note,
                correlationStatus = correlationStatus ?: "MATCHED"
            )
        )
    }

    internal fun recordPendingResponse(callback: Any, responsePayload: String, terminal: Boolean = true): Boolean {
        if (!isCaptureEnabled()) {
            PendingRegistry.removeAll(callback)
            return false
        }
        val pending = PendingRegistry.peek(callback) ?: return false
        if (!terminal) pending.callbackKept = true
        return recordHostResponse(pending, responsePayload, terminal)
    }

    internal fun tryRecordResponseFromField(callback: Any) {
        if (!isCaptureEnabled()) {
            PendingRegistry.removeAll(callback)
            return
        }
        val pending = PendingRegistry.peek(callback) ?: return
        if (pending.callbackKept) return
        val responsePayload = readAriverResponseField(callback)
        if (!responsePayload.isNullOrBlank() && responsePayload != pending.responseBeforeRequest) {
            recordPendingResponse(callback, responsePayload)
        }
    }

    internal fun recordHttpBody(
        body: HttpBodyObservation,
        payload: JSONObject,
        note: String,
        terminal: Boolean = false,
        complete: Boolean? = true
    ) {
        if (body.finished || !isCaptureEnabled()) return
        if (!PendingRegistry.isBodyAttached(body) && !(terminal && body.afterNoBodyResponse)) return
        body.lastActivityElapsedMs = SystemClock.elapsedRealtime()
        payload.put("streamIdentity", body.streamIdentity)
            .put("bodySource", if (body.afterNoBodyResponse) "representation_after_no_body_response" else "response_stream")
        val bodyComplete = if (body.incomplete) false else complete
        if (terminal) {
            payload.put("complete", bodyComplete ?: JSONObject.NULL)
                .put("position", if (body.position < 0) JSONObject.NULL else body.position)
        }
        if (body.afterNoBodyResponse) {
            emitEvent(
                RpcTrafficEvent(
                    phase = TrafficPhase.RESPONSE,
                    source = body.pending.source,
                    method = body.pending.method,
                    requestId = body.pending.requestId,
                    payload = payload.toString(),
                    note = note,
                    correlationStatus = "MATCHED"
                )
            )
        } else {
            val awaitingOtherBody = PendingRegistry.hasOtherActiveBody(body)
            val recorded = recordHostResponse(
                body.pending,
                payload.toString(),
                terminal && bodyComplete != false && !awaitingOtherBody,
                note
            )
            if (terminal && bodyComplete == false && recorded) {
                recordHostError(body.pending, note, "BODY_INCOMPLETE")
            }
        }
        if (terminal) {
            PendingRegistry.markBodyFinished(body)
        }
    }

    internal fun emitEvent(event: RpcTrafficEvent) {
        if (isFiltered(event.method, event.payload)) return
        val raw = event.payload
        var truncated = false
        var payloadOut: Any = JSONObject.NULL
        if (raw != null) {
            payloadOut = if (raw.length > MAX_PAYLOAD_CHARS) {
                truncated = true
                raw.substring(0, MAX_PAYLOAD_CHARS)
            } else {
                raw
            }
        }
        val message = JSONObject().apply {
            put("schema", "rpc-capture")
            put("eventId", UUID.randomUUID().toString())
            put("requestId", event.requestId ?: JSONObject.NULL)
            put("timestampMs", System.currentTimeMillis())
            put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
            put("sequence", eventSequence.incrementAndGet())
            put("source", event.source.name)
            put("phase", event.phase.name)
            put("method", event.method)
            put("elapsedMs", event.elapsedMs)
            put("refs", extractStructuredRefs(payloadOut))
            put("payload", if (payloadOut is String) parsePayloadForEvent(payloadOut) else payloadOut)
            if (truncated) {
                put("payloadTruncated", true)
                put("payloadLength", raw!!.length)
            }
            if (!event.note.isNullOrBlank()) put("note", event.note)
            if (!event.correlationStatus.isNullOrBlank()) put("correlationStatus", event.correlationStatus)
        }.toString()
        Log.capture(message)
    }

    private fun isFiltered(method: String?, payload: String?): Boolean =
        CaptureFilter.isFiltered(method) || CaptureFilter.isFiltered(payload)

    private fun forwardHookData(pending: PendingHostRequest, responsePayload: String?) {
        runCatching {
            if (pending.source == TrafficSource.MINI_HTTP) return
            if (BaseModel.sendHookData.value != true) return
            val url = BaseModel.sendHookDataUrl.value?.trim().orEmpty()
            if (url.isEmpty()) return
            val body = JSONObject()
                .put("TimeStamp", pending.startedAtMs)
                .put("Method", pending.method)
                .put("Params", pending.requestPayload ?: JSONObject.NULL)
                .put("Data", responsePayload ?: JSONObject.NULL)
                .put("Source", pending.source.name)
                .put("RequestId", pending.requestId)
            GlobalThreadPools.execute {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        Log.runtime(TAG, "转发抓包数据失败: HTTP $code")
                    }
                    connection.disconnect()
                }.onFailure {
                    Log.runtime(TAG, "转发抓包数据失败: ${it.message}")
                }
            }
        }
    }

    // ==================== 清扫与超时终态 ====================

    private fun drainCollectedPendingRequests() {
        val (lost, lostBodies) = PendingRegistry.drainCollected()
        lostBodies.forEach { body ->
            synchronized(body) {
                body.incomplete = true
                recordHttpBody(body, JSONObject(), "body_context_collected", true)
            }
        }
        lost.forEach { pending ->
            emitEvent(
                RpcTrafficEvent(
                    phase = TrafficPhase.ERROR,
                    source = pending.source,
                    method = pending.method,
                    requestId = pending.requestId,
                    elapsedMs = SystemClock.elapsedRealtime() - pending.startedAtElapsedMs,
                    note = "capture_context_collected",
                    correlationStatus = "CONTEXT_LOST"
                )
            )
        }
    }

    private fun expirePendingRequests() {
        val (expiredBodies, expiredRequests) = PendingRegistry.expireCandidates()
        expiredBodies.forEach { body ->
            synchronized(body) {
                body.incomplete = true
                recordHttpBody(body, JSONObject(), "body_observation_timeout", true)
            }
        }
        expiredRequests.forEach { pending ->
            recordHostError(pending, "capture_response_timeout", "TIMEOUT")
        }
    }

    // ==================== 工具 ====================

    private fun parsePayloadForEvent(payload: String): Any = runCatching {
        val trimmed = payload.trim()
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> payload
        }
    }.getOrDefault(payload)

    private fun extractStructuredRefs(payload: Any?): JSONObject {
        val refs = JSONObject()
        val root = payload as? JSONObject ?: return refs
        structuredReferenceContainers.forEach { containerPath ->
            val container = objectAtPath(root, containerPath) ?: return@forEach
            structuredReferenceKeys.forEach { key ->
                if (!refs.has(key) && container.has(key) && !container.isNull(key)) {
                    refs.put(key, container.opt(key))
                }
            }
        }
        return refs
    }

    private fun objectAtPath(root: JSONObject, path: String): JSONObject? {
        if (path.isEmpty()) return root
        var current = root
        path.split('.').forEach { key ->
            current = current.optJSONObject(key) ?: return null
        }
        return current
    }

    internal fun extractMtopApiName(payload: String?): String? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(payload)
            listOf("apiName", "params.apiName", "fetchedData.params.apiName")
                .firstNotNullOfOrNull { path -> stringAtPath(root, path) }
        }.getOrNull()
    }

    private fun stringAtPath(root: JSONObject, path: String): String? {
        val separator = path.lastIndexOf('.')
        val container = if (separator < 0) {
            root
        } else {
            objectAtPath(root, path.substring(0, separator)) ?: return null
        }
        return container.optString(path.substring(separator + 1))
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    internal fun extractH5Response(responseObject: Any?): String? {
        val target = responseObject ?: return null
        return runCatching {
            HttpSnapshot.findMethod(target.javaClass, "getResponse", 0)?.invoke(target) as? String
        }.getOrNull()
    }

    private fun readAriverResponseField(callback: Any): String? = runCatching {
        HttpSnapshot.findField(callback.javaClass, "mJSONResponse")?.get(callback)?.toString()
    }.getOrNull()
}
