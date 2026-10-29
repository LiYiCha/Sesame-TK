package fansirsqi.xposed.sesame.hook.network.capture

import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
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
        MINI_HTTP("HTTP/小程序"),
        MTOP_NATIVE("MTOP")
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
        val correlationStatus: String? = null,
        /** 该 payload 来自加解密 hook 命中（即此请求走内容加密），true 表示已解出明文 */
        val enc: Boolean = false
    ) {
        fun copy(
            phase: TrafficPhase = this.phase,
            payload: String? = this.payload,
            note: String? = this.note
        ): RpcTrafficEvent = RpcTrafficEvent(phase, source, method, requestId, payload, elapsedMs, note, correlationStatus, enc)
    }

    private val installLock = Any()

    /**
     * 引擎 hook 安装态：true=已安装（动态补装点位可登记 Unhook），false=已卸载（动态补装点位自行回收）。
     * 关闭态所有 hook 已卸载，网络事件不再进入任何回调。
     */
    @Volatile
    private var installed = false
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

    // ==================== 抓包模式（DataStore 持久化单值，天然互斥；抓包设置页退出时写入、宿主经 NIO 监听热装卸 hook） ====================

    enum class CaptureMode { OFF, RPC_DEBUG, HTTPS_DEBUG }

    const val CAPTURE_MODE_KEY = "RpcTrafficCapture_Mode"

    fun getCaptureMode(): CaptureMode = runCatching {
        DataStore.get(CAPTURE_MODE_KEY, String::class.java)?.let { CaptureMode.valueOf(it) }
    }.getOrNull() ?: CaptureMode.OFF

    fun setCaptureMode(mode: CaptureMode) = DataStore.put(CAPTURE_MODE_KEY, mode.name)

    /** 实际已应用模式，仅主进程 apply 成功后回写；设置页状态卡读取此值显示真实运行态 */
    const val APPLIED_MODE_KEY = "RpcTrafficCapture_Applied"

    fun getAppliedMode(): CaptureMode = runCatching {
        DataStore.get(APPLIED_MODE_KEY, String::class.java)?.let { CaptureMode.valueOf(it) }
    }.getOrNull() ?: CaptureMode.OFF

    fun setAppliedMode(mode: CaptureMode) = DataStore.put(APPLIED_MODE_KEY, mode.name)

    /** 加解密明文 hook 子开关（HTTPS 调试模式内的可选项，随模式退出页面提交、宿主独立装卸） */
    const val CONTENT_ENCRYPT_KEY = "RpcTrafficCapture_ContentEncrypt"

    fun isContentEncryptEnabled(): Boolean = DataStore.get(CONTENT_ENCRYPT_KEY, Boolean::class.java) ?: false

    fun setContentEncryptEnabled(enabled: Boolean) = DataStore.put(CONTENT_ENCRYPT_KEY, enabled)

    fun applyContentEncrypt(enabled: Boolean) = HttpHooks.setContentEncryptEnabled(enabled)

    // ==================== 安装 / 卸载 ====================

    /** hook 安装态，供 [trackOrRollback] 与动态补装点位读取 */
    internal fun isHookActive(): Boolean = installed

    /**
     * 登记 Unhook 并双重检查：卸载先置 false 再扫尾，补装点位据此自行回收，
     * 避免卸载竞态遗留孤儿 hook（关闭态仍产生回调）。
     */
    internal fun trackOrRollback(sink: MutableCollection<XC_MethodHook.Unhook>, unhook: XC_MethodHook.Unhook): Boolean {
        if (installed) {
            sink.add(unhook)
            if (installed) return true
            sink.remove(unhook)
        }
        runCatching { unhook.unhook() }
        return false
    }

    fun install(classLoader: ClassLoader): Boolean {
        synchronized(installLock) {
            if (installed) return true
            installed = true
            PendingRegistry.cleanupTick = {
                drainCollectedPendingRequests()
                expirePendingRequests()
            }
            var installedAny = runCatching { HttpHooks.installMiniHttpHooks(classLoader) }
                .onFailure { runCatching { Log.runtime(TAG, "安装 HTTP 抓包失败: ${it.message}") } }
                .getOrDefault(false)
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

    /**
     * 卸载全部引擎 hook：先关闸（动态补装点位自行回收），再扫尾 unhook 并清空关联表。
     * 关闭态零回调、零事件级检查开销。
     */
    fun uninstall() {
        synchronized(installLock) {
            if (!installed) return
            installed = false
            RpcHooks.uninstallAll()
            HttpHooks.uninstallAll()
            PendingRegistry.clearAll()
            Log.runtime(TAG, "抓包 Hook 已卸载")
        }
    }

    fun newRequestId(): String = UUID.randomUUID().toString()

    /** 引擎是否已安装。供 MtopSender 的发送 hook 回调低成本早退用（关闭态不做任何反射探测）。 */
    fun isEngineInstalled(): Boolean = installed

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

    // ==================== 记录与派发 ====================

    internal fun recordHostRequest(pending: PendingHostRequest) {
        if (isFiltered(pending.method, pending.requestPayload)) {
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
        val pending = PendingRegistry.peek(callback) ?: return false
        if (!terminal) pending.callbackKept = true
        return recordHostResponse(pending, responsePayload, terminal)
    }

    internal fun tryRecordResponseFromField(callback: Any) {
        val pending = PendingRegistry.peek(callback) ?: return
        if (pending.callbackKept) return
        val responsePayload = readAriverResponseField(callback)
        if (!responsePayload.isNullOrBlank() && responsePayload != pending.responseBeforeRequest) {
            recordPendingResponse(callback, responsePayload)
        }
    }

    /**
     * MTOP 原生层（mtopsdk MtopBuilder sync/async 发送 hook）事件入口，由 [fansirsqi.xposed.sesame.hook.MtopSender] 调用。
     *
     * 与 JS 桥层不同，这里是同步调用且请求/响应在手，无需经 PendingRegistry 关联：
     * responsePayload 非空发 REQUEST+RESPONSE 事件对；为空（asyncRequest 或 build 兜底）只发 REQUEST 事件。
     * 引擎未安装时直接返回，零开销。
     */
    fun recordMtopNative(
        apiName: String?,
        requestPayload: String?,
        responsePayload: String?,
        elapsedMs: Long = -1L,
        note: String? = null
    ) {
        if (!installed || apiName.isNullOrBlank()) return
        if (!isSourceEnabled(TrafficSource.MTOP_NATIVE)) return
        val requestId = newRequestId()
        emitEvent(
            RpcTrafficEvent(
                phase = TrafficPhase.REQUEST,
                source = TrafficSource.MTOP_NATIVE,
                method = apiName,
                requestId = requestId,
                payload = requestPayload,
                note = note
            )
        )
        if (responsePayload != null) {
            emitEvent(
                RpcTrafficEvent(
                    phase = TrafficPhase.RESPONSE,
                    source = TrafficSource.MTOP_NATIVE,
                    method = apiName,
                    requestId = requestId,
                    payload = responsePayload,
                    elapsedMs = elapsedMs,
                    correlationStatus = "MATCHED"
                )
            )
        }
    }

    internal fun recordHttpBody(
        body: HttpBodyObservation,
        payload: JSONObject,
        note: String,
        terminal: Boolean = false,
        complete: Boolean? = true
    ) {
        if (body.finished) return
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
            if (event.enc) put("enc", true)
            // 密文嗅探：UTF-8 解出替换符说明 payload 非正常文本，疑似未解密的密文
            if (payloadOut is String && payloadOut.contains('\uFFFD')) put("suspectEnc", true)
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
