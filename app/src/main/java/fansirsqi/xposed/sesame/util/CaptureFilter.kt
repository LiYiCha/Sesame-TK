package fansirsqi.xposed.sesame.util

/**
 * 抓包/RPC 过滤统一入口（单一数据源）
 *
 * - 预设噪音：提供默认系统级噪音关键词（DEFAULT_NOISE），默认启用，支持用户在设置中自由增删
 * - 用户关键词：统一存储于 DataStore[KEY]（逗号分隔字符串），用户可随时修改或一键恢复默认
 */
object CaptureFilter {
    private const val TAG = "CaptureFilter"

    /** DataStore 存储键（黑名单关键词） */
    const val KEY = "CaptureFilter"

    /** 默认预设噪音关键词：精确匹配系统噪音，不含宽泛的 alipay.client 避免误杀业务 */
    val DEFAULT_NOISE = listOf(
        "alipay.pushcore",
        "alipay.mappconfig",
        "log.alipay.com",
        "mdap.alipay.com",
        "diagnose.alipay.com",
        "wireless.audit",
        "locate.service",
        "uploadlog",
        "log.upload",
        "behavior.logs",
        "behaviorlog",
        "diagnose",
        "reportactive",
        "monitor",
        "telemetry",
        "alipay.client.interfere.config.get",
        "alipay.client.getDynamicBundle",
        "alipay.client.getUnionResource"
    )

    /** 兼容旧代码引用 */
    val BUILTIN_NOISE = DEFAULT_NOISE

    /**
     * 获取当前生效的过滤关键词列表（首次使用默认加载 DEFAULT_NOISE）
     */
    fun getKeywords(): List<String> {
        return try {
            val raw = DataStore.get(KEY, String::class.java)
            if (raw == null) {
                // 首次未配置：初始化为默认推荐噪音
                val defaultStr = DEFAULT_NOISE.joinToString(",")
                DataStore.put(KEY, defaultStr)
                DEFAULT_NOISE
            } else if (raw.isBlank()) {
                emptyList()
            } else {
                raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (_: Throwable) {
            DEFAULT_NOISE
        }
    }

    /**
     * 判断文本是否命中过滤规则（子串匹配，忽略大小写）
     */
    fun isFiltered(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val lower = text.lowercase()
        val keywords = getKeywords()
        return keywords.any { kw ->
            val k = kw.trim().lowercase()
            k.isNotEmpty() && lower.contains(k)
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
     * 移除关键词（支持删除任意预设或自定义关键词）
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

    /**
     * 恢复为默认推荐噪音
     */
    fun resetToDefault(): Boolean {
        return try {
            DataStore.put(KEY, DEFAULT_NOISE.joinToString(","))
            true
        } catch (t: Throwable) {
            Log.error(TAG, "resetToDefault err: ${t.message}")
            false
        }
    }
}
