package fansirsqi.xposed.sesame.hook.network.model

/**
 * 一条完整抓包记录 — 对标 Charles / Fiddler 的请求视图。
 *
 * 设计原则：
 * - 所有字段自包含，不引用外部文件
 * - 文本 body 和二进制 body 用不同字段，语义清晰
 * - [queryParams] 从 URL 解析，不依赖字符串搜索
 * - 兼容 Jackson 序列化（KotlinModule + NON_NULL）
 */
data class CaptureRecord(
    /** 唯一标识 */
    val id: String = "",

    // ── 请求行 ──────────────────────────────
    /** 完整请求 URL (含查询参数) */
    val url: String = "",
    /** HTTP 方法: GET / POST / ... */
    val method: String = "",
    /** 主机名, 从 URL 解析 */
    val host: String = "",
    /** URL 路径部分, e.g. /v1/task/list */
    val path: String = "",
    /** 从 URL 解析的查询参数 key-value */
    val queryParams: Map<String, String> = emptyMap(),

    // ── 请求 ───────────────────────────────
    /** 请求头 key-value */
    val requestHeaders: Map<String, String> = emptyMap(),
    /** 请求体 (UTF-8 解码后的文本), 无法解码时为空, 二进制数据见 [requestBodyBase64] */
    val requestBody: String? = null,
    /** 请求体 base64 编码 (仅二进制/图片体), 与 [requestBody] 互斥 */
    val requestBodyBase64: String? = null,
    /** 请求体原始字节数 */
    val requestBodySize: Int = 0,

    // ── 响应 ───────────────────────────────
    /** HTTP 状态码 */
    val statusCode: Int = 0,
    /** 响应头 key-value */
    val responseHeaders: Map<String, String> = emptyMap(),
    /** 响应 Content-Type, 从头中提取 */
    val contentType: String? = null,
    /** 响应体 (UTF-8 解码后的文本) */
    val responseBody: String? = null,
    /** 响应体 base64 编码 (仅二进制/图片), 与 [responseBody] 互斥 */
    val responseBodyBase64: String? = null,
    /** 响应体原始字节数 */
    val responseBodySize: Int = 0,

    // ── 时间 ───────────────────────────────
    /** 请求发起时间戳 (ms) */
    val timestamp: Long = 0L,
    /** 响应耗时 (ms) */
    val duration: Long = 0L,

    // ── 分类 ───────────────────────────────
    /** 业务分类: 任务 / 打卡 / 奖励 / 森林 / ... */
    val category: String = "",

    // ── 标记 ───────────────────────────────
    /** body 是否因超过阈值被截断 */
    val isTruncated: Boolean = false,
    /** 异常/错误信息 */
    val errorMessage: String? = null
)
