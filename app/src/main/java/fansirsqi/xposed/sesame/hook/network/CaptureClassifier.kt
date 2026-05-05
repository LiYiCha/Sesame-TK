package fansirsqi.xposed.sesame.hook.network

import java.net.URI

/**
 * 请求分类器 + URL 解析器。
 *
 * 分类规则按优先级排序，先匹配到就返回。URL 转为小写后做 contains 检测。
 */
object CaptureClassifier {

    /**
     * 分类规则，按优先级从高到低排列。
     * 每个规则包含分类名和一组匹配关键词。
     */
    private val rules = listOf(
        Rule("任务", listOf("task", "mission", "quest", "usermission", "acceptmission")),
        Rule("打卡", listOf("checkin", "signin", "punch", "clock", "dodailysign")),
        Rule("奖励", listOf("reward", "prize", "award", "bonus", "bubble", "receive")),
        Rule("森林", listOf("antforest", "forest", "tree", "energy", "friendwater")),
        Rule("庄园", listOf("farm", "chicken", "feed", "animal")),
        Rule("蚂蚁", listOf("ocean", "live", "anthome", "answer")),
        Rule("会员", listOf("member", "vip", "grade", "level")),
        Rule("登录", listOf("login", "auth", "session", "token")),
        Rule("查询", listOf("query", "search", "list", "get", "detail", "info", "homepage")),
        Rule("提交", listOf("submit", "create", "update", "deduction", "donate", "exchange")),
    )

    /** 默认分类 */
    private const val DEFAULT_CATEGORY = "其他"

    /**
     * 根据 URL 判断请求分类。
     */
    fun classify(url: String): String {
        if (url.isBlank()) return DEFAULT_CATEGORY
        val lower = url.lowercase()
        for (rule in rules) {
            if (rule.keywords.any { lower.contains(it) }) {
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

    /**
     * URL 解析结果。
     */
    data class ParsedUrl(
        val host: String = "",
        val path: String = "",
        val queryParams: Map<String, String> = emptyMap()
    )

    /**
     * 分类规则定义。
     */
    private data class Rule(
        val category: String,
        val keywords: List<String>
    )
}
