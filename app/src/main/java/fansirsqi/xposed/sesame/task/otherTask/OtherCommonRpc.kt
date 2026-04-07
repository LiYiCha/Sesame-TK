package fansirsqi.xposed.sesame.task.otherTask

import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil
import org.json.JSONObject

class OtherCommonRpc {

    //========视频|每日任务=========
    // 查询任务
    fun videoQueryTask():JSONObject{
        val method = "alipay.content.interact.task.query"
        val params = "[{\"pageType\":\"index\",\"tab3SpecialVer\":\"student\",\"taskExt\":\"{\\\"fromTab3BottomBar\\\":true,\\\"openTab3\\\":true,\\\"retryCount\\\":0}\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //检查
    fun videoCheckStatus():JSONObject{
        val method = "alipay.content.interact.task.notice.check"
        val params = "[{\"scene\":\"GENERAL_NEW\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //任务中心
    fun videoTaskCenter():JSONObject{
        val method = "alipay.content.interact.task.center"
        val params = "[{}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //领取每日任务
//    fun videoReceiveTask(rewardParams: JSONObject, taskType: String):JSONObject{
//        val method = "alipay.content.interact.task.cmd"
//        val params = "[{\"cmdSignature\":\"{\\\"cp\\\":\\\"CP132754784\\\",\\\"ct\\\":\\\"register\\\"," +
//                "\\\"di\\\":20250927,\\\"er\\\":\\\"c8c4803cfd564a049b5d5fd1671335fd\\\"," +
//                "\\\"ot\\\":\\\"onePart_viewHuiyuanNew1\\\",\\\"ts\\\":1758974361,\\\"tt\\\":\\\"onePart\\\"}\",\"cmdType\":\"register\"}]"
//        return JSONObject(RequestManager.requestString(method, params))
//    }
    fun videoReceiveTask(rewardParams: JSONObject, taskActivityId: String): JSONObject {
        val method = "alipay.content.interact.task.cmd"

        // 从rewardParams中提取参数，没有则使用默认值
        val cp = taskActivityId
        val ct = rewardParams.optString("ct", "register")
        val di = TimeUtil.getDateStr2() // 今天的日期
        val er = rewardParams.optString("er", "")
        val ot = rewardParams.optString("ot", "")
        val ts = System.currentTimeMillis()
        val tt = rewardParams.optString("tt", "onePart")

        // 动态构建cmdSignature
        val cmdSignature = JSONObject()
        cmdSignature.put("cp", cp)
        cmdSignature.put("ct", ct)
        cmdSignature.put("di", di)
        cmdSignature.put("er", er)
        cmdSignature.put("ot", ot)
        cmdSignature.put("ts", ts)
        cmdSignature.put("tt", tt)

        val params = "[{\"cmdSignature\":\"${cmdSignature.toString().replace("\"", "\\\"")}\",\"cmdType\":\"$ct\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
//    //完成任务1
//    fun videoCompleteTask1(rewardParams: JSONObject, taskActivityId: String):JSONObject{
//        val method = "alipay.content.interact.task.reward"
//        val params = "[{\"rewardParams\":\"{\\\"cp\\\":\\\"CP132754784\\\",\\\"er\\\":\\\"d296d539f25f50ddf7fff579a29dd6a4\\\",\\\"lti\\\":\\\"0b26c19c17589743930732170e4418\\\",\\\"osc\\\":0,\\\"ot\\\":\\\"onePart_viewHuiyuanNew1\\\",\\\"s\\\":1,\\\"sc\\\":3,\\\"t\\\":20700031,\\\"ts\\\":1758974393,\\\"tsq\\\":0,\\\"tt\\\":\\\"onePart\\\"}\"," +
//                "\"taskActivityId\":\"CP132754784\",\"taskSource\":\"onePart\",\"taskType\":\"onePart\"}]"
//        return JSONObject(RequestManager.requestString(method, params))
//    }
//    //完成任务2
//    fun videoCompleteTask2(rewardParams: JSONObject, taskActivityId: String):JSONObject{
//        val method = "alipay.content.interact.task.reward"
//        val params = "[{\"rewardParams\":\"{\\\"cp\\\":\\\"CP132754784\\\",\\\"er\\\":\\\"d6f7bb227a4764915244f5a20ae75a8b\\\",\\\"et\\\":\\\"prizeSend\\\",\\\"lti\\\":\\\"0b26c19c17589743975842630e4418\\\",\\\"osc\\\":0,\\\"ot\\\":\\\"onePart_viewHuiyuanNew1\\\",\\\"s\\\":1,\\\"sc\\\":3,\\\"t\\\":20700031,\\\"ts\\\":1758974398,\\\"tsq\\\":0,\\\"tt\\\":\\\"onePart\\\"}\"," +
//                "\"taskActivityId\":\"CP132754784\",\"taskType\":\"onePart\"}]"
//        return JSONObject(RequestManager.requestString(method, params))
//    }
    // 完成任务1 - 提交任务
    fun videoCompleteTask1(rewardParams: JSONObject, taskActivityId: String): JSONObject {
        return createRewardParams(rewardParams, taskActivityId, false)
    }

    // 完成任务2 - 奖励发送
    fun videoCompleteTask2(rewardParams: JSONObject, taskActivityId: String): JSONObject {
        return createRewardParams(rewardParams, taskActivityId, true)
    }

    /**
     * 创建标准化的任务完成参数
     * @param baseParams 基础参数对象
     * @param activityId 任务活动ID
     * @param isPrizeSend 是否为奖励发放类型
     */
    private fun createRewardParams(
        baseParams: JSONObject,
        activityId: String,
        isPrizeSend: Boolean
    ): JSONObject {
        // 复制原始参数并添加必要字段
        val rewardParams = JSONObject(baseParams.toString()).apply {
            put("lti", generateLtiParameter())
            put("osc", 0)
            put("s", 1)
            put("sc", 3)
            put("t", 20700031)
            put("tsq", 0)
            if (isPrizeSend) put("et", "prizeSend")
        }

        // 构建请求参数JSON
        val params = JSONObject().apply {
            put("rewardParams", rewardParams.toString())
            put("taskActivityId", activityId)
            put("taskType", baseParams.getString("tt"))
            if (!isPrizeSend) put("taskSource", baseParams.getString("tt"))
        }

        return JSONObject(RequestManager.requestString(
            "alipay.content.interact.task.reward",
            "[${params.toString()}]"
        ))
    }

    /**
     * 生成符合规范的LTI参数
     * 格式: 固定前缀 + 时间戳 + 随机数
     */
    private fun generateLtiParameter(): String {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = RandomUtil.nextInt(100000, 999999)
        return "0b26c19c${timestamp}${randomSuffix}"
    }
}