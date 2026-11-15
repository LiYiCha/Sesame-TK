package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.hook.internal.LocationHelper
import org.json.JSONObject

/**
 * 饿了么幸运星 RPC 调用封装
 * 统一复用 ElemeMtopUtil（支持 H5 MTOP 签名与请求协议）
 */
object LuckyStarRpcCall {
    const val API_QUERY_TASK = "mtop.ele.biz.growth.task.core.querytask"
    const val API_RECEIVE_PRIZE = "mtop.ele.biz.growth.task.core.receiveprize"
    const val API_HOMEPAGE = "mtop.alsc.interact.et.interact.center.homepage"
    const val API_CHECK_LOGIN = "mtop.alsc.user.session.ele.check.login"
    const val API_SUBSCRIBE_REMIND = "mtop.eleme.notify.subscribeRemindService.querySubscribeRemind"
    const val API_PAGEVIEW = "mtop.ele.biz.growth.task.event.pageview"
    const val API_TRIGGER = "mtop.ele.biz.growth.task.event.trigger"

    // 签到与打卡
    const val API_SIGNIN = "mtop.alsc.interact.playapp.signin.component.signinandreceive"
    const val API_RECORDSIGNIN = "mtop.alsc.interact.playapp.signin.component.recordsignin"

    // 夺宝/抽奖
    const val API_LOTTERY_DRAW = "mtop.alsc.interact.playapp.reward.lottery.draw"
    const val API_ORDERFREE_DRAW = "mtop.alsc.interact.et.orderfree.draw"

    // 兑换商城与资产
    const val API_MALL_LIST = "mtop.alsc.interact.playapp.reward.right.mallrecall"
    const val API_PROPERTY_DETAIL = "mtop.koubei.interaction.center.common.querypropertydetail"

    // 前置营销弹窗与权益召回（对齐 tb.log 真实生命周期）
    const val API_CARD_DATA_RECALL = "mtop.alsc.card.cardDataRecallFacade"
    const val API_RIGHTS_PAGE_RECALL = "mtop.ele.rights.page.recall"
    const val API_RIGHTS_COMPONENT_RECALL = "mtop.ele.rights.component.recall"
    const val API_CONSULT_UNIT = "mtop.alsc.upp.market.timelimitdraw.consultunit"

    const val ASAC_PRIZE = "alscadOjfleDPawx9zVoT0"
    const val ASAC_PAGEVIEW = "alscFS8BNTO6jivYS7XOAM"
    const val ASAC_TRIGGER = "2A21607NIIT1ND5C4YXJ6C"
    const val ASAC_SIGNIN = "alsc3Lhy681SA5TT4iHgL3"
    const val ASAC_LOTTERY_DRAW = "alscdZLQXT9DEAlXvQQOLO"
    const val ASAC_ORDER_FREE_DRAW = "alscrX2P4rmvl0NhmQgr9x"
    const val ASAC_MALL_EXCHANGE = "alsc5KvbdX5mHl3sdv4guV"

    const val COLLECTION_ID = "3112"
    private const val BIZ_SCENE = "interact_center"
    private const val SP_PAGE_ID = "26051124184460076"

    private fun getCoordinates(): Pair<String, String> {
        val loc = LocationHelper.getLocation()
        val lat = loc?.optString("latitude")?.takeIf { it.isNotEmpty() } ?: "25.274027"
        val lng = loc?.optString("longitude")?.takeIf { it.isNotEmpty() } ?: "110.299078"
        return Pair(lat, lng)
    }

    /**
     * 查询任务列表（支持全量列表或单任务精准刷新）
     * @param missionId 若指定则仅刷新该任务状态
     */
    fun queryTaskList(cookie: String, missionId: Long? = null, collectionId: String = COLLECTION_ID): JSONObject? {
        val (lat, lng) = getCoordinates()
        val locInfo = "[\"{\\\"lng\\\":\\\"$lng\\\",\\\"lat\\\":\\\"$lat\\\"}\"]"
        val data = JSONObject().apply {
            put("missionCollectionId", collectionId)
            put("bizScene", BIZ_SCENE)
            put("launchChannels", "[]")
            if (missionId != null && missionId > 0) {
                put("missionIds", "[$missionId]")
            }
            put("accountPlan", "HAVANA_COMMON")
            put("longitude", lng)
            put("latitude", lat)
            put("locationInfos", locInfo)
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_QUERY_TASK, data.toString(), cookie, method = "GET")
    }

    /**
     * 获取任务当前处于已达成(FINISH)且待领奖(TODO)的所有阶段
     */
    fun getRewardStages(task: JSONObject): List<JSONObject> {
        val stages = task.optJSONArray("missionStageDTOS") ?: return emptyList()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until stages.length()) {
            val st = stages.optJSONObject(i) ?: continue
            val sa = st.optJSONObject("sourceAction")
            val subtype = sa?.optString("actionSubtype").orEmpty()
            val subSubType = sa?.optString("actionSubSubType").orEmpty()
            val status = st.optString("status")
            val rewardStatus = st.optString("rewardStatus")
            if (status == "FINISH" && rewardStatus != "SUCCESS" && rewardStatus != "RECEIVED"
                && rewardStatus != "FAIL" && rewardStatus != "EXPIRED" && rewardStatus != "HAVERECEIVED"
                && subtype != "NO_REWARD" && subSubType != "REWARD_SEND_AUTO" && subSubType != "NOTIFY_REACH_STAGE"
            ) {
                list.add(st)
            }
        }
        return list
    }

    /**
     * 判断任务是否存在可领奖阶段
     */
    fun getRewardStage(task: JSONObject): JSONObject? = getRewardStages(task).firstOrNull()

    /**
     * 判断任务是否已达成领奖条件（排除未完成却标记为 TORECEIVE 的初始态）
     */
    fun isTaskRewardable(task: JSONObject): Boolean {
        // 1. 如果存在阶段配置，必须有阶段处于 FINISH 且未领奖
        if (getRewardStages(task).isNotEmpty()) return true

        // 2. 如果无阶段配置或者是单节点任务
        val stages = task.optJSONArray("missionStageDTOS")
        if (stages == null || stages.length() == 0) {
            val status = task.optString("status")
            val rewardStatus = task.optString("rewardStatus")
            val receiveStatus = task.optString("receiveStatus")
            if (status == "FINISH" && rewardStatus != "SUCCESS" && rewardStatus != "RECEIVED" && rewardStatus != "HAVERECEIVED") {
                return true
            }
            if (rewardStatus == "WAITING_RECEIVE" || (status == "FINISH" && receiveStatus == "TORECEIVE")) {
                return true
            }
        }
        return false
    }

    /**
     * 智能领取任务奖励（自动适配多阶段 stageCount 与单节点参数）
     */
    fun receivePrize(cookie: String, task: JSONObject, targetStage: JSONObject? = null): ElemeMtopUtil.MtopResult {
        val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
        val missionXId = task.optString("missionXId")
        val collectionId = task.optString("missionCollectionId").ifEmpty {
            task.optString("missionCollectId", COLLECTION_ID)
        }
        val instanceId = task.optString("instanceId")
        val stage = targetStage ?: getRewardStage(task)

        val (lat, lng) = getCoordinates()
        val locInfo = "[\"{\\\"lng\\\":\\\"$lng\\\",\\\"lat\\\":\\\"$lat\\\"}\"]"
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("accountPlan", "HAVANA_COMMON")
            put("longitude", lng)
            put("latitude", lat)
            put("locationInfos", locInfo)
            put("missionCollectionId", collectionId.toIntOrNull() ?: 3112)
            put("missionId", missionDefId)
            put("asac", ASAC_PRIZE)
            if (missionXId.isNotEmpty()) {
                put("missionXId", missionXId)
            }
            if (instanceId.isNotEmpty()) {
                put("instanceId", instanceId)
            }
            if (stage != null) {
                if (stage.has("stageCount")) {
                    put("count", stage.optInt("stageCount"))
                }
                if (stage.has("stageSum")) {
                    put("sum", stage.optInt("stageSum"))
                }
            } else {
                put("count", 1)
                put("extInfo", "{\"triggerScene\":\"MISSION_LIST\"}")
            }
        }
        val headers = mapOf("asac" to ASAC_PRIZE)
        return ElemeMtopUtil.execute(API_RECEIVE_PRIZE, data.toString(), cookie, method = "GET", extraHeaders = headers)
    }

    /**
     * 领取任务奖励（参数严格对齐真实抓包规范）
     */
    fun receivePrize(
        cookie: String,
        missionId: Long,
        missionXId: String,
        collectionId: String = COLLECTION_ID
    ): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val locInfo = "[\"{\\\"lng\\\":\\\"$lng\\\",\\\"lat\\\":\\\"$lat\\\"}\"]"
        val data = JSONObject().apply {
            put("missionCollectionId", collectionId.toIntOrNull() ?: 3112)
            put("missionId", missionId)
            put("missionXId", missionXId)
            put("extInfo", "{\"triggerScene\":\"MISSION_LIST\"}")
            put("count", 1)
            put("bizScene", BIZ_SCENE)
            put("accountPlan", "HAVANA_COMMON")
            put("longitude", lng)
            put("latitude", lat)
            put("locationInfos", locInfo)
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        val headers = mapOf("asac" to ASAC_PRIZE)
        return ElemeMtopUtil.execute(API_RECEIVE_PRIZE, data.toString(), cookie, method = "GET", extraHeaders = headers)
    }

    /**
     * 浏览任务埋点上报（模拟停留 15 秒并提交事件）
     */
    fun reportPageView(
        cookie: String,
        missionDefId: Long,
        pageSpm: String,
        viewTime: Int = 15,
        instanceId: String? = null,
        collectionId: String = COLLECTION_ID
    ): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val locInfo = "[\"{\\\"lng\\\":\\\"$lng\\\",\\\"lat\\\":\\\"$lat\\\"}\"]"
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("accountPlan", "HAVANA_COMMON")
            put("longitude", lng)
            put("latitude", lat)
            put("locationInfos", locInfo)
            put("collectionId", collectionId.toLongOrNull() ?: 3112L)
            put("missionId", missionDefId)
            put("pageFrom", pageSpm)
            put("actionCode", "PAGEVIEW")
            put("viewTime", viewTime)
            if (!instanceId.isNullOrEmpty()) {
                put("instanceId", instanceId)
            }
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        val headers = mapOf("asac" to "alscFS8BNTO6jivYS7XOAM")
        return ElemeMtopUtil.execute(API_PAGEVIEW, data.toString(), cookie, method = "GET", version = "1.1", extraHeaders = headers)
    }

    /**
     * THIRD 动作/点击任务事件推进（如“在页面内点击3个店铺”）
     */
    fun reportTrigger(
        cookie: String,
        missionDefId: Long,
        missionXId: String,
        collectionId: String = COLLECTION_ID
    ): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val now = System.currentTimeMillis()
        val data = JSONObject().apply {
            put("latitude", lat)
            put("longitude", lng)
            put("riskScene", "duobao_external")
            put("bizScene", missionDefId.toString())
            put("eventType", "THIRD")
            put("eventSubType", "EVENT_FINISH")
            put("eventId", now)
            put("eventIdemPotent", now)
            put("eventTime", now)
            put("triggerType", "SYNC")
            put("collectionId", collectionId)
            put("missionId", missionDefId.toString())
            put("missionXId", missionXId)
        }
        return ElemeMtopUtil.execute(API_TRIGGER, data.toString(), cookie, method = "GET", version = "1.1")
    }

    /**
     * 查询互动中心主页与星星余额
     */
    fun queryProperty(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("version", "1.4.22")
            put("modules", "[\"property\"]")
            put("latitude", lat)
            put("longitude", lng)
            put("cityName", "")
            put("channel", "ES0021178012")
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_HOMEPAGE, data.toString(), cookie, method = "GET", version = "1.0")
    }

    /**
     * 会话状态校验（模拟页面进入第一步，对应 tb.log 第 56/72/988 行）
     */
    fun checkLogin(cookie: String): JSONObject? {
        val res = checkLoginResult(cookie)
        return if (res.isSuccess) res.data else null
    }

    /**
     * 会话状态校验（返回结构化 MtopResult）
     */
    fun checkLoginResult(cookie: String): ElemeMtopUtil.MtopResult {
        val data = JSONObject().apply {
            put("instance", "INNER")
        }
        return ElemeMtopUtil.execute(API_CHECK_LOGIN, data.toString(), cookie, method = "POST")
    }

    /**
     * 校验 checkLogin 响应是否为有效登录态
     */
    fun isLoginSuccess(resp: JSONObject?): Boolean {
        if (resp == null) return false
        val ret = resp.optJSONArray("ret")?.optString(0).orEmpty()
        val data = resp.optJSONObject("data") ?: resp
        val errorCode = data.optString("errorCode", "")
        val errorMsg = data.optString("errorMessage", "")
        if (errorCode == "000502" || errorMsg.contains("需要登录") || errorMsg.contains("未登录")) return false
        if (ret.contains("SESSION_EXPIRED") || ret.contains("NOT_LOGIN")) return false
        return ret.startsWith("SUCCESS") || data.optBoolean("isLogin", false) || data.optBoolean("login", false) || data.has("userId")
    }

    /**
     * 页面动效、气泡、弹窗任务组件加载（模拟页面渲染环境，对应 tb.log 第 1030 行）
     */
    fun initPageComponents(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val cpnCodes = "[\"PLAY_NOTICE_CPN\",\"INTERACT_RESOURCE_CPN\",\"MORE_MENU_CPN\",\"STAR_MSG_CONTENT_CPN\",\"PLAY_RESOURCE_CPN\",\"INTERACT_CENTER_SKIN_COMPONENT\",\"INTERACT_CENTER_BUBBLE\",\"INTERACT_CENTER_DRAW_BUBBLE\",\"INTERACT_CENTER_LOTTERY\",\"INTERACT_CENTER_CDP_CONFIG\",\"INTERACT_CENTER_MODAL_TASK\"]"
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("version", "1.4.22")
            put("cpnCodes", cpnCodes)
            put("latitude", lat)
            put("longitude", lng)
            put("cityName", "")
            put("channel", "ES0021178012")
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_HOMEPAGE, data.toString(), cookie, method = "GET", version = "1.0")
    }

    /**
     * 查询签到提醒与弹窗服务（模拟页面弹窗行为，对应 tb.log 第 1508 行）
     */
    fun querySubscribeRemind(cookie: String): JSONObject? {
        val data = JSONObject().apply {
            put("sceneCode", "TaobaoFlashxyxqd")
            put("dimensionList", "[\"DEFAULT\"]")
            put("channel", "ALIPAY")
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_SUBSCRIBE_REMIND, data.toString(), cookie, method = "GET")
    }

    /**
     * 前置营销弹窗与权益召回全链路（对齐 tb.log 第 107~140 行真实生命周期）
     */
    fun preflightMarketing(cookie: String) {
        val (lat, lng) = getCoordinates()

        // 1. 卡片营销数据召回（获取首页营销活动、弹窗 cardIds）
        try {
            val cardData = JSONObject().apply {
                put("cardIds", "[\"hPD1755590981175\",\"Dde1763974474769\",\"XHK1755590830057\",\"BFd1760926092920\",\"TzZ1755161222114\",\"pFH1765806298465\",\"yfp1766631235508\"]")
                put("bizSource", "alsc-activity")
                put("instance", "INNER")
            }
            ElemeMtopUtil.send(API_CARD_DATA_RECALL, cardData.toString(), cookie, method = "GET")
        } catch (_: Throwable) {}

        // 2. 页面权益召回 (rights.page.recall)
        try {
            val pageRecallData = JSONObject().apply {
                put("screenNum", "1")
                put("locationDTO", "{\"latitude\":\"$lat\",\"longitude\":\"$lng\"}")
                put("sceneCode", "ENGAGE_HUB")
                put("instance", "INNER")
            }
            ElemeMtopUtil.send(API_RIGHTS_PAGE_RECALL, pageRecallData.toString(), cookie, method = "POST")
        } catch (_: Throwable) {}

        // 3. 组件权益召回 (rights.component.recall)
        try {
            val compRecallData = JSONObject().apply {
                put("sceneCode", "ENGAGE_HUB")
                put("instance", "INNER")
            }
            ElemeMtopUtil.send(API_RIGHTS_COMPONENT_RECALL, compRecallData.toString(), cookie, method = "POST")
        } catch (_: Throwable) {}

        // 4. 限时抽奖/营销弹窗咨询 (timelimitdraw.consultunit)
        try {
            val consultData = JSONObject().apply {
                put("bizScene", "UPP_TIMELIMITDRAW")
                put("latitude", lat)
                put("longitude", lng)
                put("instance", "INNER")
            }
            ElemeMtopUtil.send(API_CONSULT_UNIT, consultData.toString(), cookie, method = "POST")
        } catch (_: Throwable) {}
    }

    /**
     * 获取首页聚合完整数据（包括星星余额、签到配置、夺宝组件、下单抽奖等）
     */
    fun getHomepageData(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val cpnCodes = "[\"PLAY_NOTICE_CPN\",\"INTERACT_RESOURCE_CPN\",\"MORE_MENU_CPN\",\"STAR_MSG_CONTENT_CPN\",\"PLAY_RESOURCE_CPN\",\"INTERACT_CENTER_SKIN_COMPONENT\",\"INTERACT_CENTER_BUBBLE\",\"INTERACT_CENTER_DRAW_BUBBLE\",\"INTERACT_CENTER_LOTTERY\",\"INTERACT_CENTER_CDP_CONFIG\",\"INTERACT_CENTER_MODAL_TASK\"]"
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("version", "1.4.22")
            put("cpnCodes", cpnCodes)
            put("latitude", lat)
            put("longitude", lng)
            put("cityName", "")
            put("channel", "ES0021178012")
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_HOMEPAGE, data.toString(), cookie, method = "GET", version = "1.0")
    }

    /**
     * 幸运星打卡签到（对齐 幸运星.py signin 规范）
     */
    fun signIn(
        cookie: String,
        copyId: String,
        strategy: String,
        award: JSONObject?
    ): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("latitude", lat)
            put("longitude", lng)
            if (copyId.isNotEmpty()) {
                put("copyId", copyId)
            }
            if (award != null) {
                if (award.has("actCode")) put("actCode", award.optString("actCode"))
                if (award.has("sceneCode")) put("sceneCode", award.optString("sceneCode"))
                if (award.has("prizeMark")) put("prizeMark", award.optString("prizeMark"))
                if (award.has("signInDay")) put("signInDay", award.optInt("signInDay"))
            }
        }
        val headers = mapOf("asac" to ASAC_SIGNIN)
        val order = if (strategy == "SIGNIN_AND_RECEIVE") {
            listOf(API_SIGNIN to "1.0", API_RECORDSIGNIN to "1.1")
        } else {
            listOf(API_RECORDSIGNIN to "1.1", API_SIGNIN to "1.0")
        }

        var lastResult = ElemeMtopUtil.MtopResult(isSuccess = false, isNotLogin = false, errorCode = "NOT_RUN", errorMsg = "未执行")
        for ((api, ver) in order) {
            val res = ElemeMtopUtil.execute(api, data.toString(), cookie, method = "GET", version = ver, extraHeaders = headers)
            if (res.isSuccess) return res
            lastResult = res
            if (res.errorCode.contains("REPEAT") || res.errorCode.contains("ALREADY") || res.errorMsg.contains("已签到")) {
                break
            }
        }
        return lastResult
    }

    /**
     * 幸运星夺宝抽奖（对齐 幸运星.py draw_lottery 规范）
     */
    fun drawLottery(cookie: String, lotteryComponent: JSONObject): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val actId = lotteryComponent.optString("actId")
        val componentId = lotteryComponent.optString("componentId")
        val data = JSONObject().apply {
            put("actId", actId)
            put("componentId", componentId)
            put("bizScene", "INTERACT_CENTER_LOT")
            put("bizCode", "INTERACT_CENTER_LOT")
            put("longitude", lng)
            put("latitude", lat)
            put("extParams", "{\"desc\":\"幸运福利\"}")
        }
        val headers = mapOf("asac" to ASAC_LOTTERY_DRAW)
        return ElemeMtopUtil.execute(API_LOTTERY_DRAW, data.toString(), cookie, method = "GET", extraHeaders = headers)
    }

    /**
     * 下单免单抽奖（对齐 幸运星.py orderfree_draw 规范）
     */
    fun drawOrderFree(cookie: String, orderFreeData: JSONObject?): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val dataObj = orderFreeData?.optJSONObject("data")
        val cityId = dataObj?.optJSONObject("extInfo")?.optString("cityId").orEmpty()
        val data = JSONObject().apply {
            put("bizScene", "order_free_draw")
            put("longitude", lng)
            put("latitude", lat)
            if (cityId.isNotEmpty()) {
                put("cityId", cityId)
            }
        }
        val headers = mapOf("asac" to ASAC_ORDER_FREE_DRAW)
        return ElemeMtopUtil.execute(API_ORDERFREE_DRAW, data.toString(), cookie, method = "GET", extraHeaders = headers)
    }

    /**
     * 查询兑换商城列表（对齐 幸运星.py mall 规范）
     */
    fun queryMallList(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("version", "1.4.22")
            put("propertyBizType", "STAR")
            put("pageNo", 1)
            put("pageSize", 10)
            put("latitude", lat)
            put("longitude", lng)
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_MALL_LIST, data.toString(), cookie, method = "GET")
    }
}
