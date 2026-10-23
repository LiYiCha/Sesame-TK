package fansirsqi.xposed.sesame.task.AnswerAI

import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 标准 OpenAI 兼容（/chat/completions）AI 服务实现
 * 支持任意兼容厂商（DeepSeek/通义/Kimi/智谱/Gemini OpenAI 兼容端点/OneAPI 中转等）
 * 支持多模型配置、失败按顺序自动切换、思考模型 reasoning_content 回落
 */
class OpenAIService(
    configs: List<AiModelConfig>,
    /** 指定使用的配置下标，-1 表示按列表顺序故障转移 */
    private val activeIndex: Int,
    systemPromptText: String?,
    timeoutSecondsValue: Int,
    maxAttemptsValue: Int
) : AnswerAIInterface {

    companion object {
        private const val TAG = "OpenAIService"
        private const val CONTENT_TYPE = "application/json"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        private const val DEFAULT_SYSTEM_PROMPT = "你是一个拥有丰富的知识，并且能根据知识回答问题的专家。"
        private const val CONTENT_PATH = "choices.[0].message.content"
        private const val REASONING_PATH = "choices.[0].message.reasoning_content"

        /** 匹配单个数字序号（选项一般不超过9个，避免误匹配答案内容中的多位数字） */
        private val INDEX_PATTERN: Pattern = Pattern.compile("(?<![0-9])([1-9])(?![0-9])")

        /** 共享 OkHttpClient，按超时时间缓存，避免每次请求重复创建 */
        private val CLIENT_CACHE = ConcurrentHashMap<Int, OkHttpClient>()

        private fun getClient(timeoutSeconds: Int): OkHttpClient =
            CLIENT_CACHE.computeIfAbsent(timeoutSeconds) { t ->
                OkHttpClient.Builder()
                    .connectTimeout(t.toLong(), TimeUnit.SECONDS)
                    .writeTimeout(t.toLong(), TimeUnit.SECONDS)
                    .readTimeout(t.toLong(), TimeUnit.SECONDS)
                    .build()
            }

        private fun normalizeBaseUrl(baseUrl: String?): String {
            val url = baseUrl?.trim().orEmpty().trimEnd('/')
            return url.ifEmpty { DEFAULT_BASE_URL }
        }

        private fun abbreviate(text: String?): String {
            val trimmed = text?.trim().orEmpty()
            return if (trimmed.length > 200) trimmed.substring(0, 200) + "..." else trimmed
        }

        /**
         * 获取模型列表（GET {baseUrl}/models，OpenAI 标准接口）
         *
         * @param baseUrl 服务地址
         * @param apiKey API Key
         * @param timeoutSeconds 超时时间（秒）
         * @return 模型列表结果，失败时 error 字段带原因
         */
        fun fetchModels(baseUrl: String?, apiKey: String?, timeoutSeconds: Int): ModelsResult {
            val result = ModelsResult()
            val url = normalizeBaseUrl(baseUrl) + "/models"
            val builder = Request.Builder().url(url).get()
            if (!apiKey.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
            }
            try {
                getClient(maxOf(10, timeoutSeconds)).newCall(builder.build()).execute().use { response ->
                    val json = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        result.error = "HTTP ${response.code} ${abbreviate(json)}"
                        Log.runtime(TAG, "获取模型列表失败：${result.error}")
                        return result
                    }
                    val trimmed = json.trim()
                    val data = if (trimmed.startsWith("[")) {
                        // 兼容根节点直接为数组的格式
                        JSONArray(trimmed)
                    } else {
                        val jsonObject = JSONObject(trimmed)
                        // 兼容 {"data":[...]} 与 {"models":[...]} 格式
                        jsonObject.optJSONArray("data") ?: jsonObject.optJSONArray("models")
                    }
                    if (data == null) {
                        result.error = "响应中未找到模型列表"
                        return result
                    }
                    val models = ArrayList<String>()
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optString("id", "")
                        if (id.isNotEmpty()) {
                            models.add(id)
                        }
                    }
                    result.models = models
                    if (models.isEmpty()) {
                        result.error = "响应中未解析到模型"
                    }
                }
            } catch (e: Exception) {
                result.error = e.message
                Log.printStackTrace(TAG, e)
            }
            return result
        }
    }

    /** 规范化后的模型配置（baseUrl 去尾斜杠并兜底默认值） */
    private val configList: List<AiModelConfig> = configs.map { it.copy(baseUrl = normalizeBaseUrl(it.baseUrl)) }
    private val systemPrompt: String = systemPromptText?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_SYSTEM_PROMPT
    private val timeoutSeconds: Int = maxOf(10, timeoutSecondsValue)
    private val maxAttempts: Int = maxOf(1, maxAttemptsValue)

    override fun getModelName(): String {
        val config = currentConfig() ?: return ""
        return "${config.name} | ${config.model}"
    }

    private fun currentConfig(): AiModelConfig? {
        if (configList.isEmpty()) {
            return null
        }
        return configList[if (activeIndex >= 0) activeIndex % configList.size else 0]
    }

    override fun getAnswerStr(text: String): String {
        if (text.isEmpty() || configList.isEmpty()) {
            return ""
        }
        val prompt = removeControlCharacters(text)
        val total = configList.size
        val start = if (activeIndex >= 0) activeIndex else 0
        val attempts = minOf(maxAttempts, total)
        for (i in 0 until attempts) {
            val config = configList[(start + i) % total]
            try {
                val result = request(config, prompt)
                if (result.isNotEmpty()) {
                    return result
                }
                Log.runtime(TAG, "模型[${config.name}]未返回有效内容，尝试下一个模型")
            } catch (e: Exception) {
                Log.runtime(TAG, "模型[${config.name}]请求失败：${e.message}")
            }
        }
        return ""
    }

    private fun request(config: AiModelConfig, prompt: String): String {
        val client = getClient(timeoutSeconds)
        val body = buildRequestJson(config, prompt).toString().toRequestBody(CONTENT_TYPE.toMediaTypeOrNull())
        val builder = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .post(body)
            .addHeader("Content-Type", CONTENT_TYPE)
        if (!config.apiKey.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer ${config.apiKey!!.trim()}")
        }
        client.newCall(builder.build()).execute().use { response ->
            val json = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.runtime(TAG, "模型[${config.name}]HTTP ${response.code} 响应：$json")
                return ""
            }
            val jsonObject = JSONObject(json)
            var content: String? = JsonUtil.getValueByPath(jsonObject, CONTENT_PATH)
            if (content.isNullOrBlank()) {
                // 思考模型（如 deepseek-reasoner）正文可能在 reasoning_content
                content = JsonUtil.getValueByPath(jsonObject, REASONING_PATH)
            }
            return content?.trim().orEmpty()
        }
    }

    private fun buildRequestJson(config: AiModelConfig, text: String): JSONObject {
        val requestJson = JSONObject()
        requestJson.put("model", config.model)

        val messages = JSONArray()
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", systemPrompt)
        messages.put(systemMessage)

        val userMessage = JSONObject()
        userMessage.put("role", "user")
        userMessage.put("content", text)
        messages.put(userMessage)

        requestJson.put("messages", messages)
        requestJson.put("stream", false)
        return requestJson
    }

    /**
     * 移除控制字符（保留换行与制表符）
     */
    private fun removeControlCharacters(text: String): String =
        text.replace(Regex("\\p{Cntrl}&&[^\n\t]"), "")

    override fun getAnswer(title: String, answerList: List<String>): Int {
        if (title.isEmpty() || answerList.isEmpty()) {
            return -1
        }
        val answerStr = buildString {
            answerList.forEachIndexed { index, answer ->
                append(index + 1).append(".[").append(answer).append("]\n")
            }
        }
        val question = "问题：$title\n\n答案列表：\n\n$answerStr\n\n请只返回答案列表中的序号"
        return matchAnswer(getAnswerStr(question), answerList)
    }

    /**
     * 答案解析：纯序号优先，其次提取文本中的单位数序号，最后按答案内容模糊匹配
     */
    private fun matchAnswer(answerResult: String?, answerList: List<String>): Int {
        if (answerResult.isNullOrBlank()) {
            return -1
        }
        val result = answerResult.trim()
        // 1. 纯序号，如 "2"
        result.toIntOrNull()?.let { number ->
            if (number in 1..answerList.size) {
                return number - 1
            }
        }
        // 2. 提取文本中的第一个单位数序号，如 "答案是1"、"选 2"
        val matcher = INDEX_PATTERN.matcher(result)
        if (matcher.find()) {
            val index = matcher.group(1)?.toIntOrNull()?.minus(1) ?: -1
            if (index in answerList.indices) {
                return index
            }
        }
        // 3. 模糊匹配答案内容
        answerList.forEachIndexed { index, answer ->
            if (result.contains(answer)) {
                return index
            }
        }
        Log.runtime(TAG, "AI回答无法匹配选项：$result")
        return -1
    }

    /**
     * 模型列表获取结果
     */
    class ModelsResult {
        var models: List<String> = emptyList()
        var error: String? = null

        val isSuccess: Boolean
            get() = error == null && models.isNotEmpty()
    }
}
