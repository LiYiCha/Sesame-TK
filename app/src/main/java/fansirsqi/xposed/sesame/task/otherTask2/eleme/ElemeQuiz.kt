package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil

/**
 * 趣味话题答题（猜价格 / 猜一猜）
 *
 * 接口链：quiz.query 取题 → quiz.submit 提交 → quiz.award 领奖，三者均为 v1.0 + GET。
 *
 * actId 说明：答题活动 ID 不在幸运星任务列表（querytask）的返回中出现，只能从答题页 URL 上读取。
 * 因此这里不接受任何「从任务配置里提取 actId」的推断，改由设置页【答题活动actId】显式提供；
 * 未填写时整段跳过，不发起任何答题请求（与 幸运星.py 的 ELM_QUIZ_ACTID 行为一致）。
 *
 * 提交策略：取第一个选项提交，服务端不校验答案对错，仅按答题计次发放奖励。
 */
object ElemeQuiz {

    private const val TAG = "ElemeQuiz"

    /** 当日答题已完成标记：幸运星与抽免单共用同一答题活动，成功后当日不再重复提交 */
    private const val FLAG_QUIZ_DONE = "elemeQuiz::done"

    /**
     * 执行一次完整答题流程（每个账号每日一次）。
     *
     * @param cookie 饿了么有效凭据
     * @param label  日志前缀，用于区分调用方（[幸运星] / [抽免单]）
     * @return true 表示提交并领奖成功
     */
    @JvmStatic
    fun run(cookie: String, label: String): Boolean {
        val actId = ElemeTask.getElemeQuizActId().value.orEmpty().trim()
        if (actId.isEmpty()) {
            return false
        }
        if (Status.hasFlagToday(FLAG_QUIZ_DONE)) {
            return false
        }

        val qResp = LuckyStarRpcCall.quizQuery(cookie, actId)
        if (qResp == null) {
            Log.runtime(TAG, "$label 答题取题失败: ${ElemeMtopUtil.lastError ?: "无响应"}")
            return false
        }

        val d = qResp.optJSONObject("data") ?: qResp
        val copyId = d.optString("copyId")
        val question = d.optJSONObject("questionDTO")
            ?: d.optJSONArray("questionList")?.optJSONObject(0)
        val questionId = question?.opt("questionId")
        val options = question?.optJSONArray("optionList") ?: question?.optJSONArray("options")
        val firstOption = options?.optJSONObject(0)
        val optionId = firstOption?.opt("optionId") ?: firstOption?.opt("id")

        if (questionId == null || optionId == null) {
            Log.other("饿了么", "$label 答题: 今日无题或题目结构异常，跳过")
            return false
        }

        val subRes = LuckyStarRpcCall.quizSubmit(cookie, actId, copyId, questionId, optionId)
        if (!subRes.isSuccess) {
            val rawResp = subRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: subRes.errorMsg
            Log.error(TAG, "$label ❌ 答题提交失败: ${subRes.errorCode}::${subRes.errorMsg}\n完整响应:\n$rawResp")
            handleRisk(subRes, label)
            return false
        }
        Log.other("饿了么", "$label 答题: 已提交答案")

        Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
        val awardRes = LuckyStarRpcCall.quizAward(cookie, actId, copyId)
        if (!awardRes.isSuccess) {
            val rawResp = awardRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: awardRes.errorMsg
            Log.error(TAG, "$label ❌ 答题领奖失败: ${awardRes.errorCode}::${awardRes.errorMsg}\n完整响应:\n$rawResp")
            handleRisk(awardRes, label)
            return false
        }

        val prize = ElemeMtopUtil.extractPrizeDesc(awardRes.data).ifEmpty { "领奖成功" }
        Log.other("饿了么", "$label 答题: $prize")
        // 仅在领奖成功时置位，避免一次偶发失败占掉当日唯一一次机会
        Status.setFlagToday(FLAG_QUIZ_DONE)
        return true
    }

    /**
     * 405 行为受限 / 417 防作弊只做日志提示。
     * 答题没有对应的任务 ID，写入任务级风控名单无意义（名单项用于按 missionDefId 跳过任务），
     * 因此不污染名单，仅提示用户当前账号已被平台限制。
     */
    private fun handleRisk(res: ElemeMtopUtil.MtopResult, label: String) {
        val msg = res.errorMsg
        val isRisk = res.errorCode == "405" || msg.contains("405") || msg.contains("受限") ||
            res.errorCode == "417" || msg.contains("417") || msg.contains("作弊")
        if (!isRisk) return
        Log.other("饿了么", "$label ⚠️ 答题被平台限制(${res.errorCode})，本日不再重试答题")
    }
}
