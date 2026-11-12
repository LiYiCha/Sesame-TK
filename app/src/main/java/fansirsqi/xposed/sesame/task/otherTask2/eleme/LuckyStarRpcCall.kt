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
    const val API_SIGNIN = "mtop.alsc.interact.playapp.signin.component.signinandreceive"
    const val API_RECORDSIGNIN = "mtop.alsc.interact.playapp.signin.component.recordsignin"

    const val ASAC_SIGNIN = "alsc3Lhy681SA5TT4iHgL3"
    const val ASAC_PRIZE = "alscadOjfleDPawx9zVoT0"
    const val ASAC_PAGEVIEW = "alscFS8BNTO6jivYS7XOAM"

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
     * 判断任务是否存在可领奖阶段
     */
    fun getRewardStage(task: JSONObject): JSONObject? {
        val stages = task.optJSONArray("missionStageDTOS") ?: return null
        for (i in 0 until stages.length()) {
            val st = stages.optJSONObject(i) ?: continue
            val sa = st.optJSONObject("sourceAction")
            val subtype = sa?.optString("actionSubtype").orEmpty()
            val subSubType = sa?.optString("actionSubSubType").orEmpty()
            val status = st.optString("status")
            val rewardStatus = st.optString("rewardStatus")
            if (status == "FINISH" && rewardStatus != "SUCCESS" && rewardStatus != "RECEIVED"
                && subtype != "NO_REWARD" && subSubType != "REWARD_SEND_AUTO" && subSubType != "NOTIFY_REACH_STAGE"
            ) {
                return st
            }
        }
        return null
    }

    /**
     * 判断任务是否已达成领奖条件（排除未完成却标记为 TORECEIVE 的初始态）
     */
    fun isTaskRewardable(task: JSONObject): Boolean {
        // 1. 如果存在阶段配置，必须有阶段处于 FINISH 且未领奖
        val stage = getRewardStage(task)
        if (stage != null) return true

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
    fun receivePrize(cookie: String, task: JSONObject): JSONObject? {
        val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
        val missionXId = task.optString("missionXId")
        val collectionId = task.optString("missionCollectionId").ifEmpty {
            task.optString("missionCollectId", COLLECTION_ID)
        }
        val instanceId = task.optString("instanceId")
        val stage = getRewardStage(task)

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
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        val headers = mapOf("asac" to ASAC_PRIZE)
        return ElemeMtopUtil.send(API_RECEIVE_PRIZE, data.toString(), cookie, method = "GET", extraHeaders = headers)
    }

    /**
     * 领取任务奖励（参数严格对齐真实抓包规范）
     */
    fun receivePrize(
        cookie: String,
        missionId: Long,
        missionXId: String,
        collectionId: String = COLLECTION_ID
    ): JSONObject? {
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
        return ElemeMtopUtil.send(API_RECEIVE_PRIZE, data.toString(), cookie, method = "GET", extraHeaders = headers)
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
    ): JSONObject? {
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
        return ElemeMtopUtil.send(API_PAGEVIEW, data.toString(), cookie, method = "GET", version = "1.1", extraHeaders = headers)
    }

    /**
     * THIRD 动作/点击任务事件推进（如“在页面内点击3个店铺”）
     */
    fun reportTrigger(
        cookie: String,
        missionDefId: Long,
        missionXId: String,
        collectionId: String = COLLECTION_ID
    ): JSONObject? {
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
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_TRIGGER, data.toString(), cookie, method = "GET", version = "1.1")
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
     * 幸运星签到并领取奖励（支持双协议自动回退与参数动态提取）
     */
    fun signIn(
        cookie: String,
        copyId: String = "",
        strategy: String = "",
        award: JSONObject? = null
    ): Pair<Boolean, String> {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE)
            put("latitude", lat)
            put("longitude", lng)
            if (copyId.isNotEmpty()) {
                put("copyId", copyId)
            }
            if (award != null) {
                val actCode = award.optString("actCode")
                val sceneCode = award.optString("sceneCode")
                val prizeMark = award.optString("prizeMark")
                val signInDay = award.optString("signInDay")
                if (actCode.isNotEmpty()) put("actCode", actCode)
                if (sceneCode.isNotEmpty()) put("sceneCode", sceneCode)
                if (prizeMark.isNotEmpty()) put("prizeMark", prizeMark)
                if (signInDay.isNotEmpty()) put("signInDay", signInDay)
            }
        }
        val headers = mapOf("asac" to ASAC_SIGNIN)
        val order = if (strategy == "SIGNIN_AND_RECEIVE") {
            listOf(Pair(API_SIGNIN, "1.0"), Pair(API_RECORDSIGNIN, "1.1"))
        } else {
            listOf(Pair(API_RECORDSIGNIN, "1.1"), Pair(API_SIGNIN, "1.0"))
        }

        for ((api, ver) in order) {
            val resp = ElemeMtopUtil.send(api, data.toString(), cookie, method = "GET", version = ver, extraHeaders = headers)
            val ret = resp?.optJSONArray("ret")?.optString(0).orEmpty()
            if (ret.startsWith("SUCCESS")) {
                val rewards = resp?.optJSONObject("data")?.optJSONArray("rewards")
                val total = if (rewards != null && rewards.length() > 0) {
                    val r = rewards.optJSONObject(0)
                    r?.optString("prizeValue")?.ifEmpty { r.optString("equityValue") } ?: ""
                } else ""
                val desc = if (total.isNotEmpty()) "+$total 幸运星" else "签到成功"
                return Pair(true, desc)
            }
            if (ret.contains("已签到") || ret.contains("REPEAT") || ret.contains("ALREADY")) {
                return Pair(true, "今日已签到")
            }
        }
        return Pair(false, "签到未成功")
    }
}
