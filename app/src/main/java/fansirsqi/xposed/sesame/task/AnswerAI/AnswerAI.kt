package fansirsqi.xposed.sesame.task.AnswerAI

import android.content.Context
import android.os.Handler
import android.os.Looper
import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.EmptyModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.StringModelField
import fansirsqi.xposed.sesame.ui.StringDialog
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * AI答题（标准 OpenAI 兼容格式）
 * 只需在 modelList 中填入 baseUrl + apiKey + model 即可接入任意兼容厂商，
 * 支持配置多个模型并按顺序自动切换
 */
class AnswerAI : Model() {

    private val aiModelList = StringModelField("aiModelList", "AI模型配置(JSON)", DEFAULT_MODEL_LIST)
    private val fetchModels = EmptyModelField(
        "fetchModels", "获取模型列表(自动填写)",
        EmptyModelField.ContextConsumer { context -> fetchModelList(context) }
    )
    private val activeModel = StringModelField(
        "activeModel", "当前模型名称(留空=顺序切换)", "",
        "填 modelList 中某个配置的 name 则固定使用该模型；留空则按列表顺序故障转移"
    )
    private val systemPrompt = StringModelField("aiSystemPrompt", "系统提示词", DEFAULT_SYSTEM_PROMPT)
    private val timeout = IntegerModelField("aiTimeout", "超时时间(秒)", 60, 10, 300)
    private val maxAttempts = IntegerModelField("aiMaxAttempts", "失败切换次数", 3, 1, 10)

    override fun getName(): String = "AI答题"

    override fun getGroup(): ModelGroup = ModelGroup.OTHER

    override fun getIcon(): String = "AnswerAI.svg"

    override fun getFields(): ModelFields = ModelFields().apply {
        addField(aiModelList)
        addField(fetchModels)
        addField(activeModel)
        addField(systemPrompt)
        addField(timeout)
        addField(maxAttempts)
    }

    override fun boot(classLoader: ClassLoader?) {
        try {
            enable = getEnableField().value
            initializeAIService()
        } catch (e: Exception) {
            Log.error(TAG, "初始化AI服务失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    private fun initializeAIService() {
        // 先释放旧的服务资源
        answerAIInterface?.let {
            it.release()
            answerAIInterface = null
        }
        val configList = parseModelList(aiModelList.value)
        if (configList.isEmpty()) {
            Log.error(TAG, "AI模型配置为空或格式错误，请检查 aiModelList 配置")
            return
        }
        var activeIndex = -1
        val active = activeModel.value?.trim()
        if (!active.isNullOrEmpty()) {
            configList.forEachIndexed { index, config ->
                if (activeIndex < 0 && active == config.name) {
                    activeIndex = index
                }
            }
            if (activeIndex < 0) {
                Log.runtime(TAG, "未找到名为[$active]的模型配置，使用顺序切换模式")
            }
        }
        answerAIInterface = OpenAIService(
            configList, activeIndex, systemPrompt.value, timeout.value, maxAttempts.value
        )
        val mode = if (activeIndex >= 0) configList[activeIndex].name else "顺序切换"
        Log.runtime("初始化AI服务：共加载[${configList.size}]个模型配置，模式[$mode]")
    }

    /**
     * 解析模型配置 JSON（兼容数组与单个对象两种格式），并规范化过滤无效项
     */
    private fun parseModelList(json: String?): List<AiModelConfig> {
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        val list = mutableListOf<AiModelConfig>()
        try {
            JsonUtil.parseList(json, AiModelConfig::class.java)?.let { list.addAll(it) }
        } catch (e: Exception) {
            // 兼容用户填写单个 JSON 对象的格式
            try {
                JsonUtil.parseObject(json, AiModelConfig::class.java)?.let { list.add(it) }
            } catch (e2: Exception) {
                Log.printStackTrace(TAG, e2)
            }
        }
        return list.filter { !it.model.isNullOrBlank() }.map { config ->
            config.apply {
                baseUrl = baseUrl?.trim()?.trimEnd('/')?.ifEmpty { OpenAIService.DEFAULT_BASE_URL }
                if (name.isNullOrBlank()) {
                    name = model
                }
            }
        }
    }

    /**
     * 获取模型列表：请求当前配置的 /models 接口，弹窗选择后自动写回 model 字段
     * 目标配置为 activeModel 指定的条目，未指定时为第一条
     */
    private fun fetchModelList(context: Context) {
        val array = try {
            JSONArray(aiModelList.value.orEmpty())
        } catch (e: JSONException) {
            ToastUtil.showToast(context, "模型配置格式错误，请先填写正确的JSON")
            return
        }
        if (array.length() == 0) {
            ToastUtil.showToast(context, "请先在模型配置中填写至少一条(baseUrl/apiKey)")
            return
        }
        var targetIndex = 0
        val active = activeModel.value?.trim()
        if (!active.isNullOrEmpty()) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item != null && active == item.optString("name")) {
                    targetIndex = i
                    break
                }
            }
        }
        val target: JSONObject = array.optJSONObject(targetIndex) ?: run {
            ToastUtil.showToast(context, "目标模型配置不是有效的JSON对象")
            return
        }
        val baseUrl = target.optString("baseUrl", "").ifBlank { OpenAIService.DEFAULT_BASE_URL }
            .trim().trimEnd('/')
        val apiKey = target.optString("apiKey", "")
        val timeoutSeconds = timeout.value
        ToastUtil.showToast(context, "正在获取模型列表...")
        Thread {
            val result = OpenAIService.fetchModels(baseUrl, apiKey, timeoutSeconds)
            Handler(Looper.getMainLooper()).post {
                if (!result.isSuccess) {
                    ToastUtil.showToast(context, "获取模型失败：${result.error}（详见日志）")
                    return@post
                }
                val models = result.models
                val items = models.toTypedArray<CharSequence>()
                StringDialog.showSelectionDialog(
                    context, "选择模型 | $baseUrl", items,
                    { _, which ->
                        target.put("model", models[which])
                        aiModelList.setConfigValue(array.toString())
                        ToastUtil.showToast(context, "已设置模型：${models[which]}")
                        Log.runtime(TAG, "获取模型列表成功，已选择[${models[which]}]")
                        // 立即生效，无需重启
                        initializeAIService()
                    },
                    "取消", null
                )
            }
        }.start()
    }

    companion object {
        private const val TAG = "AnswerAI"
        private const val QUESTION_LOG_FORMAT = "题目📒 [%s] | 选项: %s"
        private const val AI_ANSWER_LOG_FORMAT = "AI回答🧠 [%s] | 模型: [%s]"
        private const val NORMAL_ANSWER_LOG_FORMAT = "普通回答🤖 [%s]"
        private const val ERROR_AI_ANSWER = "AI回答异常：无法获取有效答案，请检查AI服务配置是否正确"
        private const val DEFAULT_SYSTEM_PROMPT = "你是一个拥有丰富的知识，并且能根据知识回答问题的专家。"
        private const val DEFAULT_MODEL_LIST =
            "[{\"name\":\"DeepSeek\",\"baseUrl\":\"https://api.deepseek.com/v1\",\"apiKey\":\"\",\"model\":\"deepseek-chat\"}]"

        private var enable = false
        private var answerAIInterface: AnswerAIInterface? = null

        /**
         * AI 获取答案
         *
         * @param text 问题
         * @param answerList 答案列表
         * @param flag 日志类型
         * @return 答案
         */
        @JvmStatic
        fun getAnswer(text: String?, answerList: List<String>?, flag: String?): String {
            if (text == null || answerList == null) {
                selectloger(flag, "问题或答案列表为空")
                return ""
            }
            var answerStr = ""
            try {
                selectloger(flag, String.format(QUESTION_LOG_FORMAT, text, answerList))
                val service = answerAIInterface
                if (enable && service != null) {
                    val answer = service.getAnswer(text, answerList)
                    if (answer in answerList.indices) {
                        answerStr = answerList[answer]
                        selectloger(flag, String.format(AI_ANSWER_LOG_FORMAT, answerStr, service.getModelName()))
                    } else {
                        Log.error(ERROR_AI_ANSWER)
                    }
                } else if (answerList.isNotEmpty()) {
                    answerStr = answerList[0]
                    selectloger(flag, String.format(NORMAL_ANSWER_LOG_FORMAT, answerStr))
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "AI获取答案异常:", t)
            }
            return answerStr
        }

        private fun selectloger(flag: String?, msg: String) {
            when (flag) {
                "farm" -> Log.farm(msg)
                "forest" -> Log.forest(msg)
                else -> Log.other(msg)
            }
        }
    }
}
