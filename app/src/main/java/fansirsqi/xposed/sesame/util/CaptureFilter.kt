package fansirsqi.xposed.sesame.util

/**
 * 抓包/RPC 过滤统一入口（单一数据源）
 *
 * - 内置噪音：代码硬编码（BUILTIN_NOISE），RPC 调试 / HTTP 抓包 / 网络拦截各端始终生效，用户不可增删
 * - 用户关键词：存储于 DataStore[KEY]（逗号分隔字符串），通过抓包列表页的黑名单管理界面增删
 */
object CaptureFilter {
    private const val TAG = "CaptureFilter"

    /** DataStore 存储键（用户自定义关键词） */
    const val KEY = "CaptureFilter"

    /** 内置噪音关键词：始终过滤，子串匹配，忽略大小写 */
    val BUILTIN_NOISE = listOf(
        "alipay.pushcore",      // 推送绑定上报
        "alipay.client",
        "alipay.mappconfig",    // 小程序容器检查等（含 appContainerCheck）
        "log.alipay.com",       // 日志上报（原默认黑名单项）
        "mdap.alipay.com",      // 埋点监控（原默认黑名单项）
        "wireless.audit",
        "locate.service",
        "uploadlog",
        "log.upload",
        "behavior.logs",
        "behaviorlog",
        "diagnose",
        "reportactive",
        "monitor",
        "telemetry"
    )


    /**
     * 用户自定义关键词列表（仅读取 DataStore[KEY]，无迁移逻辑）
     */
    fun getKeywords(): List<String> {
        return try {
            val raw = DataStore.get(KEY, String::class.java)
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * 判断文本是否命中过滤规则（内置噪音 + 用户关键词，子串匹配，忽略大小写）
     */
    fun isFiltered(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val lower = text.lowercase()
        if (BUILTIN_NOISE.any { lower.contains(it) }) return true
        return try {
            val raw = DataStore.get(KEY, String::class.java)
            if (!raw.isNullOrBlank()) {
                raw.split(",").any { kw ->
                    val k = kw.trim().lowercase()
                    k.isNotEmpty() && lower.contains(k)
                }
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 新增关键词（自动去重，忽略大小写）
     */
    fun add(keyword: String): Boolean {
        val kw = keyword.trim()
        if (kw.isEmpty()) return false
        val current = getKeywords()
        if (current.any { it.equals(kw, ignoreCase = true) }) return true
        return try {
            DataStore.put(KEY, (current + kw).joinToString(","))
            true
        } catch (t: Throwable) {
            Log.error(TAG, "add err: ${t.message}")
            false
        }
    }

    /**
     * 移除关键词，返回是否存在并成功写入
     */
    fun remove(keyword: String): Boolean {
        val current = getKeywords()
        val updated = current.filter { !it.equals(keyword.trim(), ignoreCase = true) }
        if (updated.size == current.size) return false
        return try {
            DataStore.put(KEY, updated.joinToString(","))
            true
        } catch (t: Throwable) {
            Log.error(TAG, "remove err: ${t.message}")
            false
        }
    }
}
