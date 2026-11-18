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
    const val API_RECORD_SIGNIN = "mtop.alsc.interact.playapp.signin.component.recordsignin"

    // 夺宝/抽奖
    const val API_LOTTERY_DRAW = "mtop.alsc.interact.playapp.reward.lottery.draw"
    const val API_ORDERFREE_DRAW = "mtop.alsc.interact.et.orderfree.draw"

    // 兑换商城与资产
    /** 幸运星资产明细（今日收益统计），参数与 幸运星.py wallet() 一致 */
    const val API_PROPERTY_DETAIL = "mtop.koubei.interaction.center.common.querypropertydetail"
    const val API_MALL_LIST = "mtop.alsc.interact.playapp.reward.right.mallrecall"
    const val API_MALL_EXCHANGE = "mtop.alsc.interact.playapp.reward.right.exchange"

    // 前置营销弹窗与权益召回（对齐 tb.log 真实生命周期）
    const val API_CARD_DATA_RECALL = "mtop.alsc.card.cardDataRecallFacade"
    const val API_RIGHTS_PAGE_RECALL = "mtop.ele.rights.page.recall"

    // 趣味话题答题/猜价格（对齐 幸运星.py quiz_query/quiz_submit/quiz_award）
    const val API_QUIZ_QUERY = "mtop.alsc.interact.playapp.quiz.query"
    const val API_QUIZ_SUBMIT = "mtop.alsc.interact.playapp.quiz.submit"
    const val API_QUIZ_AWARD = "mtop.alsc.interact.playapp.quiz.award"

    const val ASAC_PRIZE = "alscadOjfleDPawx9zVoT0"
    const val ASAC_PAGEVIEW = "alscFS8BNTO6jivYS7XOAM"
    const val ASAC_SIGNIN = "alsc3Lhy681SA5TT4iHgL3"
    const val ASAC_LOTTERY_DRAW = "alscdZLQXT9DEAlXvQQOLO"
    const val ASAC_ORDER_FREE_DRAW = "alscrX2P4rmvl0NhmQgr9x"
    const val ASAC_MALL_EXCHANGE = "alsc5KvbdX5mHl3sdv4guV"
    const val ASAC_QUIZ_AWARD = "alsc0Ue9L6Q0YtTZjC3Ovx"

    const val COLLECTION_ID = "3112"
    const val PAGE_VERSION = "1.4.2"
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
            put("sync", true)
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
        // 对齐 幸运星.py：asac 仅随 data 传递，不额外携带 header（避免多带 header 触发风控）
        return ElemeMtopUtil.execute(API_RECEIVE_PRIZE, data.toString(), cookie, method = "GET")
    }

    /**
     * 浏览任务埋点上报（模拟停留并提交事件）
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
            put("sync", true)
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
        val headers = mapOf("asac" to ASAC_PAGEVIEW)
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
            // 对齐幸运星.py：collectionId 必须使用任务自身的 missionCollectionId，
            // 抽免单等非幸运星集合的任务固定传幸运星集合 ID 会因场景不匹配被服务端过滤
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
            put("version", PAGE_VERSION)
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
     * 页面动效、气泡、弹窗任务组件加载（模拟页面渲染环境，对应 tb.log 第 1030 行）
     */
    fun initPageComponents(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val cpnCodes = "[\"PLAY_NOTICE_CPN\",\"INTERACT_RESOURCE_CPN\",\"MORE_MENU_CPN\",\"STAR_MSG_CONTENT_CPN\",\"PLAY_RESOURCE_CPN\",\"INTERACT_CENTER_SKIN_COMPONENT\",\"INTERACT_CENTER_BUBBLE\",\"INTERACT_CENTER_DRAW_BUBBLE\",\"INTERACT_CENTER_LOTTERY\",\"INTERACT_CENTER_CDP_CONFIG\",\"INTERACT_CENTER_MODAL_TASK\"]"
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("version", PAGE_VERSION)
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
     * 从 querytask 响应中取出任务数组（各接口返回的键名不一致，按真实抓包顺序回退）。
     * 原本在 LuckyStarTask 与 FreeOrderTask 中各有一份逐字相同的副本，统一到此处。
     */
    @JvmStatic
    fun extractTaskArray(root: JSONObject?): org.json.JSONArray? {
        if (root == null) return null
        val data = root.optJSONObject("data") ?: root
        return data.optJSONArray("mlist")
            ?: data.optJSONArray("missionStageDTOList")
            ?: data.optJSONArray("missionList")
    }

    /**
     * 按 missionDefId 在任务数组中定位任务对象。
     */
    @JvmStatic
    fun findTask(root: JSONObject?, missionDefId: Long): JSONObject? {
        val array = extractTaskArray(root) ?: return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val defId = item.optLong("missionDefId").takeIf { it > 0 } ?: item.optLong("id")
            if (defId == missionDefId) return item
        }
        return null
    }

    /**
     * 任务展示标题：优先 pinnedTaskTitle，其次多个候选字段，最后退化为 missionDefId；
     * 标题中的 {current}/{total} 模板占位符用真实进度替换。
     */
    @JvmStatic
    fun taskTitle(task: JSONObject): String {
        val pinned = task.optJSONObject("ext")?.optJSONObject("extValue")
            ?.optJSONObject("pinnedTaskTitle")?.optString("value")
        var name = if (!pinned.isNullOrEmpty()) pinned else task.optString("missionName").ifEmpty {
            task.optString("showTitle").ifEmpty { task.optString("taskName") }
        }
        if (name.isEmpty()) name = task.optLong("missionDefId").toString()
        if (name.contains("{current}") || name.contains("{total}")) {
            val stageObj = task.optJSONObject("stage")
            val cur = stageObj?.optInt("count") ?: 0
            val total = stageObj?.optInt("targetCount")?.takeIf { it > 0 }
                ?: task.optJSONObject("actionConfig")?.optJSONObject("actionValue")
                    ?.optInt("finalStageCount")?.takeIf { it > 0 }
                ?: 0
            name = name.replace("{current}", cur.toString()).replace("{total}", total.toString())
        }
        return name
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
                put("sceneCode", "ET_RIGHTS_CENTER")
                put("instance", "INNER")
            }
            val headers = mapOf("asac" to "alscYrvd9HqSzc56DHTYMB")
            ElemeMtopUtil.send(API_RIGHTS_PAGE_RECALL, pageRecallData.toString(), cookie, method = "POST", extraHeaders = headers)
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
            put("version", PAGE_VERSION)
            put("modules", """["signIn","property","mdCard","orderFreeDraw","orderFreeMarquee","exchangeBarrage","exchangeV2"]""")
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
     * 幸运星打卡签到（严格对齐 幸运星.py signin 规范，支持 signinandreceive 与 recordsignin 智能降级）
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
            // 对齐 幸运星.py signin：copyId 仅在有值时附加，缺失不阻断签到
            if (copyId.isNotEmpty()) put("copyId", copyId)
            // 对齐 py 注释「实测必传」：今日奖励的 actCode / sceneCode / prizeMark / signInDay，
            // 缺任意一个都会返回 FAIL_BIZ_REWARD_MODE_NOT_SIGNIN_AND_RECEIVE
            if (award != null) {
                award.optString("actCode").takeIf { it.isNotEmpty() }?.let { put("actCode", it) }
                award.optString("sceneCode").takeIf { it.isNotEmpty() }?.let { put("sceneCode", it) }
                award.optString("prizeMark").takeIf { it.isNotEmpty() }?.let { put("prizeMark", it) }
                award.optString("signInDay").takeIf { it.isNotEmpty() }?.let { put("signInDay", it) }
            }
        }
        val headers = mapOf("asac" to ASAC_SIGNIN)
        // 接口尝试顺序严格对齐 幸运星.py signin：
        //   strategy == "SIGNIN_AND_RECEIVE" -> signinandreceive(1.0) 在前，recordsignin(1.1) 兜底
        //   其余                              -> recordsignin(1.1) 在前，signinandreceive(1.0) 兜底
        // py 只对 recordsignin 使用 v1.1，不存在 v1.0 的额外探测。
        val order = if (strategy == "SIGNIN_AND_RECEIVE") {
            listOf(Pair(API_SIGNIN, "1.0"), Pair(API_RECORD_SIGNIN, "1.1"))
        } else {
            listOf(Pair(API_RECORD_SIGNIN, "1.1"), Pair(API_SIGNIN, "1.0"))
        }

        var lastResult = ElemeMtopUtil.MtopResult(isSuccess = false, isNotLogin = false, errorMsg = "未执行")
        for ((api, ver) in order) {
            lastResult = ElemeMtopUtil.execute(api, data.toString(), cookie, method = "GET", version = ver, extraHeaders = headers)
            if (lastResult.isSuccess) {
                break
            }
            val err = "${lastResult.errorCode} ${lastResult.errorMsg}"
            if (err.contains("已签到") || err.contains("REPEAT") || err.contains("ALREADY")) {
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
        // 对齐 幸运星.py draw_lottery：缺 actId 或 componentId 的组件不发起请求
        if (actId.isEmpty() || componentId.isEmpty()) {
            return ElemeMtopUtil.MtopResult(
                isSuccess = false, isNotLogin = false,
                errorCode = "INVALID_LOTTERY_COMPONENT",
                errorMsg = "抽奖组件缺少 actId/componentId，已跳过"
            )
        }
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
            // 对齐 幸运星.py：payload 携带 _kj() 展开的 version 页面版本号
            put("version", PAGE_VERSION)
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
     * 查询幸运星资产明细（严格对齐 幸运星.py wallet() 的 property_detail 调用）。
     *
     * 该接口虽未出现在 tb.log 抓包中（抓包期间未打开明细页），但 py 实跑日志
     * 明确返回「资产:今日 +728」，证明接口真实可用，用于统计当日 GRANT 类收益。
     */
    @JvmStatic
    fun queryPropertyDetail(cookie: String, pageNo: Int = 1, pageSize: Int = 20): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("version", PAGE_VERSION)
            put("latitude", lat)
            put("longitude", lng)
            put("convertType", "INTERACT_CENTER")
            put("pageNo", pageNo)
            put("pageSize", pageSize)
        }
        return ElemeMtopUtil.send(API_PROPERTY_DETAIL, data.toString(), cookie, method = "GET", version = "1.0")
    }

    /**
     * 查询兑换商城列表（参数对齐 tb.log 抓包的 mallrecall 真实请求：
     * bizScene、version、propertyBizType=STAR、tabType=RECOMMEND、pageNo、pageSize、经纬度、spPageId、instance=INNER）
     */
    @JvmStatic
    fun queryMallList(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("version", PAGE_VERSION)
            put("propertyBizType", "STAR")
            put("tabType", "RECOMMEND")
            put("pageNo", 1)
            put("pageSize", 10)
            put("latitude", lat)
            put("longitude", lng)
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_MALL_LIST, data.toString(), cookie, method = "GET")
    }

    /**
     * 发起幸运星兑换商城商品兑换（严格对齐 幸运星.py mall_exchange 的入参集合）。
     *
     * 说明：该接口在 tb.log 抓包中未出现（抓包期间未发生兑换），实跑脚本也因星数不足未真正提交过，
     * 因此入参以 幸运星.py 为准，不额外附加 spPageId/instance，避免引入未经验证的字段。
     */
    fun mallExchange(
        cookie: String,
        actId: String,
        exchangeCollectionId: String,
        exchangeId: String,
        actCode: String
    ): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("version", PAGE_VERSION)
            put("latitude", lat)
            put("longitude", lng)
            if (actId.isNotEmpty()) put("actId", actId)
            if (exchangeCollectionId.isNotEmpty()) put("exchangeCollectionId", exchangeCollectionId)
            if (exchangeId.isNotEmpty()) put("exchangeId", exchangeId)
            if (actCode.isNotEmpty()) put("actCode", actCode)
            put("propertyBizType", "STAR")
        }
        val headers = mapOf("asac" to ASAC_MALL_EXCHANGE)
        return ElemeMtopUtil.execute(API_MALL_EXCHANGE, data.toString(), cookie, method = "GET", version = "1.0", extraHeaders = headers)
    }

    /**
     * 趣味话题答题/猜价格取题（对齐 幸运星.py quiz.query 规范）
     * @param actId 答题活动 ID（从任务 actionValue 或页面 URL 提取）
     * @param bizSceneSource 业务场景来源，默认 INTERACT_CENTER
     * @return 响应 data（含 copyId、questionDTO/questionList、answeredCount 等），失败返回 null
     */
    fun quizQuery(cookie: String, actId: String, bizSceneSource: String = "INTERACT_CENTER"): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("actId", actId)
            put("bizScene", bizSceneSource)
            put("source", bizSceneSource)
            put("latitude", lat)
            put("longitude", lng)
        }
        return ElemeMtopUtil.send(API_QUIZ_QUERY, data.toString(), cookie, method = "GET", version = "1.0")
    }

    /**
     * 趣味话题答题/猜价格提交答案（对齐 幸运星.py quiz.submit 规范）
     * @param questionId 题目 ID（questionDTO.questionId）
     * @param optionId 选项 ID（optionList 第一个选项，服务端不校验对错只计次）
     */
    fun quizSubmit(
        cookie: String,
        actId: String,
        copyId: String,
        questionId: Any,
        optionId: Any,
        bizSceneSource: String = "INTERACT_CENTER"
    ): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("actId", actId)
            put("bizScene", bizSceneSource)
            put("source", bizSceneSource)
            put("copyId", copyId)
            put("questionId", questionId)
            put("optionId", optionId)
            put("latitude", lat)
            put("longitude", lng)
        }
        return ElemeMtopUtil.execute(API_QUIZ_SUBMIT, data.toString(), cookie, method = "GET", version = "1.0")
    }

    /**
     * 趣味话题答题/猜价格领奖（对齐 幸运星.py quiz.award 规范）
     */
    fun quizAward(
        cookie: String,
        actId: String,
        copyId: String,
        bizSceneSource: String = "INTERACT_CENTER"
    ): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("actId", actId)
            put("bizScene", bizSceneSource)
            put("source", bizSceneSource)
            put("copyId", copyId)
            put("latitude", lat)
            put("longitude", lng)
        }
        val headers = mapOf("asac" to ASAC_QUIZ_AWARD)
        return ElemeMtopUtil.execute(API_QUIZ_AWARD, data.toString(), cookie, method = "GET", version = "1.0", extraHeaders = headers)
    }
}
