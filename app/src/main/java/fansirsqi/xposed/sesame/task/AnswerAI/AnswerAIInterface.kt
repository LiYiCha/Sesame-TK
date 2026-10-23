package fansirsqi.xposed.sesame.task.AnswerAI

/**
 * AI答题服务接口（标准 OpenAI 兼容实现由 [OpenAIService] 提供）
 */
interface AnswerAIInterface {

    /**
     * 获取AI回答的原始文本
     *
     * @param text 问题内容
     * @return AI回答文本，获取失败返回空字符串
     */
    fun getAnswerStr(text: String): String

    /**
     * 从候选答案中选择答案
     *
     * @param title 问题标题
     * @param answerList 候选答案列表
     * @return 选中的答案索引，未找到合适的答案返回 -1
     */
    fun getAnswer(title: String, answerList: List<String>): Int

    /**
     * 获取当前模型名称，用于日志展示
     *
     * @return 模型名称
     */
    fun getModelName(): String

    /**
     * 释放资源，实现类应在此方法中清理所有使用的资源
     */
    fun release() {
        // 默认空实现
    }
}
