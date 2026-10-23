package fansirsqi.xposed.sesame.task.AnswerAI

/**
 * 单个AI模型配置（标准 OpenAI 兼容格式）
 * 通过 JSON 数组形式配置在 AnswerAI 的 modelList 字段中，可配置多个模型实现故障转移
 */
data class AiModelConfig(
    /** 配置备注名，用于日志展示与多模型选择（留空时取模型名） */
    var name: String? = null,
    /** 服务地址（OpenAI 兼容 BaseUrl），如 https://api.deepseek.com/v1 */
    var baseUrl: String? = null,
    /** API Key（Bearer 鉴权），部分本地服务可留空 */
    var apiKey: String? = null,
    /** 模型名称，如 deepseek-chat / qwen-turbo / gpt-4o-mini */
    var model: String? = null
)
