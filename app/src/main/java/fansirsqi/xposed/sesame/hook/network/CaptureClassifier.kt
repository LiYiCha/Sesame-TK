package fansirsqi.xposed.sesame.hook.network

import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import java.io.File
import java.net.URI

/**
 * 请求分类器 + URL 解析器。
 *
 * 分类规则按优先级排序，先匹配到就返回。
 * 支持从 [captures/classifier_rules.json] 加载用户自定义规则，
 * 文件不存在时使用内置默认规则。
 *
 * 自定义规则 JSON 格式：
 * [
 *   {"category": "标签名", "keywords": ["kw1", "kw2", ...]},
 *   ...
 * ]
 */
object CaptureClassifier {

    private const val DEFAULT_CATEGORY = "其他"

    private var customRules: List<Rule>? = null

    /** 所有分类名称缓存 */
    private var categoryNamesCache: List<String>? = null

    /**
     * 获取当前生效的规则列表（自定义优先，无自定义则用默认）。
     */
    private fun getRules(): List<Rule> {
        val cached = customRules
        if (cached != null) return cached
        return loadRules()
    }

    /**
     * (重新)加载分类规则：先尝试从 captures/classifier_rules.json 读取自定义规则，
     * 不存在则使用内置默认规则。
     */
    fun loadRules(): List<Rule> {
        val rulesFile = File(CaptureStorage.getDir(), "classifier_rules.json")
        if (rulesFile.exists()) {
            try {
                val text = rulesFile.readText().trim()
                if (text.isNotEmpty()) {
                    val list = JsonUtil.parseList(text, RawRule::class.java)
                    if (list != null && list.isNotEmpty()) {
                        val parsed = list.map { Rule(it.category, it.keywords.map { kw -> kw.lowercase() }) }
                        customRules = parsed
                        categoryNamesCache = parsed.map { it.category } + DEFAULT_CATEGORY
                        return parsed
                    }
                }
            } catch (_: Exception) {}
        }
        // 回退到默认规则
        val defaults = DEFAULT_RULES
        customRules = null
        categoryNamesCache = defaults.map { it.category } + DEFAULT_CATEGORY
        return defaults
    }

    /** 获取当前所有分类名称列表 */
    fun getCategoryNames(): List<String> {
        if (categoryNamesCache == null) loadRules()
        return categoryNamesCache ?: DEFAULT_RULES.map { it.category } + DEFAULT_CATEGORY
    }

    /**
     * 根据 URL + 可选的操作类型判断请求分类。
     * 对于 RPC 请求（URL 都是 gw.htm），主要靠 [operationType] 匹配。
     */
    fun classify(url: String, operationType: String? = null, body: String? = null): String {
        val combined = buildString {
            if (!url.isBlank()) {
                append(url.lowercase())
                append(" ")
            }
            operationType?.let { 
                append(it.lowercase()) 
                append(" ")
            }
            body?.let { 
                // 仅取前 500 个字符进行匹配，平衡性能与准确度
                append(it.take(500).lowercase()) 
            }
        }
        
        if (combined.isBlank()) return DEFAULT_CATEGORY
        
        for (rule in getRules()) {
            if (rule.keywords.any { combined.contains(it) }) {
                return rule.category
            }
        }
        return DEFAULT_CATEGORY
    }

    /**
     * 从完整 URL 解析出 host / path / queryParams。
     */
    fun parse(url: String): ParsedUrl {
        if (url.isBlank()) return ParsedUrl()
        try {
            val uri = URI(url)
            val host = uri.host ?: ""
            val path = uri.rawPath ?: ""
            val queryParams = parseQueryParams(uri.rawQuery)
            return ParsedUrl(
                host = host,
                path = path,
                queryParams = queryParams
            )
        } catch (_: Exception) {
            // 解析失败时手工提取
            val afterScheme = url.substringAfter("://", url)
            val host = afterScheme.substringBefore("/").substringBefore("?")
            val remaining = afterScheme.substringAfter("/", "")
            val path = if (remaining.isNotEmpty()) "/${remaining.substringBefore("?")}" else "/"
            val queryString = afterScheme.substringAfter("?", "")
            val queryParams = parseQueryParams(queryString)
            return ParsedUrl(host, path, queryParams)
        }
    }

    /**
     * 解析查询字符串为 Map。
     */
    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return try {
            query.split("&")
                .mapNotNull { part ->
                    val idx = part.indexOf("=")
                    if (idx == -1) {
                        if (part.isNotEmpty()) part to ""
                        else null
                    } else {
                        val key = part.substring(0, idx)
                        val value = part.substring(idx + 1)
                        if (key.isNotEmpty() || value.isNotEmpty()) key to value
                        else null
                    }
                }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ── 内置默认规则 ────────────────────────

    private val DEFAULT_RULES = listOf(
        // ── RPC Operation-Type 匹配（优先） ──
        Rule("任务", listOf("userMission", "task", "mission", "quest", "acceptmission", "doTask", "finishTask")),
        Rule("打卡", listOf("checkin", "signIn", "punch", "clock", "doDailySign", "continuousSign")),
        Rule("奖励", listOf("reward", "prize", "award", "bubble", "receive", "point", "exchange", "redeem")),
        Rule("森林", listOf("antforest", "forest", "tree", "energy", "friend", "prop", "fertilizer")),
        Rule("庄园", listOf("farm", "chicken", "feed", "animal", "prop", "collect")),
        Rule("果园", listOf("orchard", "fruit", "water", "tree", "antorchard")),
        Rule("蚂蚁", listOf("ocean", "antHome", "live", "answerPop", "greenway", "sports")),
        Rule("会员", listOf("member", "vip", "grade", "level", "benefit", "rights", "privilege")),
        Rule("登录", listOf("login", "auth", "session", "token", "verify", "authtoken")),
        Rule("配置", listOf("switch", "config", "getDynamicBundle", "getUnionResource", "setting", "init")),
        Rule("查询", listOf("query", "search", "list", "get", "detail", "info", "homePage", "index")),
        Rule("提交", listOf("submit", "create", "update", "deduction", "donate", "save", "sync")),
        Rule("小程序", listOf("nebula", "h5", "jsapi", "tinyapp", "miniapp", "appx")),
        Rule("游戏", listOf("game", "unity", "cocos", "canvas", "gametask")),
    )

    // ── 数据结构 ────────────────────────────

    /** 分类规则定义。 */
    data class Rule(
        val category: String,
        val keywords: List<String>
    )

    /** JSON 反序列化的原始规则格式。 */
    private data class RawRule(
        val category: String = "",
        val keywords: List<String> = emptyList()
    )

    /** URL 解析结果。 */
    data class ParsedUrl(
        val host: String = "",
        val path: String = "",
        val queryParams: Map<String, String> = emptyMap()
    )
}
