package fansirsqi.xposed.sesame.task.otherTask2.logisticsinteraction

import fansirsqi.xposed.sesame.hook.RequestManager.requestString

object baoguoRpcCall {
    //=============================
    // 包裹游历

    // 任务查询
    fun queryTaskList(taskCenInfo: String): String {
        val method = "alipay.promoprod.task.listQuery"
        val params = "[{\"consultAccessFlag\":true,\"taskCenInfo\":\"$taskCenInfo\"}]"
        return requestString(method, params)
    }

    // 任务处理 (报名/完成)
    fun handleTask(appletId: String, stageCode: String, taskCenInfo: String): String {
        val method = "alipay.promoprod.applet.trigger"
        val params = "[{\"appletId\":\"$appletId\",\"retryFlag\":\"true\"," +
                "\"stageCode\":\"$stageCode\",\"taskCenInfo\":\"$taskCenInfo\"}]"
        return requestString(method, params)
    }

    // 签到
    fun signIn(taskId: String): String {
        val method = "alipay.mobile.logisticinteraction.game.mainTask.signUp"
        val params = "[{\"taskId\":\"$taskId\"}]"
        return requestString(method, params)
    }

    // 收取获取全部奖励
    fun receiveAllRewards(): String {
        val method = "alipay.mobile.logisticsinteraction.game.parcelMap.receiveAllRewards"
        val params = "[{}]"
        return requestString(method, params)
    }

    // 查询任务奖励
    fun queryTaskRewards(): String {
        val method = "alipay.mobile.logisticsinteraction.game.parcelMap.queryTaskRewards"
        val params = "[{}]"
        return requestString(method, params)
    }

    // 查询帐户信息
    fun queryAccount(): String {
        val method = "alipay.mobile.logisticsinteraction.account.queryPointAccount"
        val params = "[{}]"
        return requestString(method, params)
    }

    // 查询商品详情
    fun queryDetail(itemId: String): String {
        val method = "alipay.mobile.logisticinteraction.benefit.queryGoodsDetail"
        val params = "[{\"itemId\":\"$itemId\"}]"
        return requestString(method, params)
    }

    // 兑换商品
    fun createRedemption(itemId: String): String {
        val method = "alipay.mobile.logisticinteraction.benefit.createRedemption"
        val params = "[{\"itemId\":\"$itemId\",\"itemType\":\"DRAW_CAMP_PRIZE\"}]"
        return requestString(method, params)
    }

    // 兑换商品列表
    fun listGoods(): String {
        val method = "alipay.mobile.logisticinteraction.benefit.listGoods"
        val params = "[{\"pageNum\":1,\"pageSize\":50}]"
        return requestString(method, params)
    }
}