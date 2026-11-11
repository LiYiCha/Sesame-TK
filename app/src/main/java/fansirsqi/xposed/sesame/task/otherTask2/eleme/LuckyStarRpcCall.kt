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
            val alipayVer = fansirsqi.xposed.sesame.hook.ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.30.8000"
            put("platform", "android")
            put("product", "ap")
            put("prodVersion", alipayVer)
            put("ttid", ElemeMtopUtil.TTID)
            put("longitude", lng)
            put("latitude", lat)
            put("locationInfos", locInfo)
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_QUERY_TASK, data.toString(), cookie, method = "GET")
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
            val alipayVer = fansirsqi.xposed.sesame.hook.ApplicationHook.getAlipayVersion()?.versionString?.takeIf { it.isNotEmpty() } ?: "12.12.30.8000"
            put("platform", "android")
            put("product", "ap")
            put("prodVersion", alipayVer)
            put("ttid", ElemeMtopUtil.TTID)
            put("longitude", lng)
            put("latitude", lat)
            put("locationInfos", locInfo)
            put("spPageId", SP_PAGE_ID)
            put("instance", "INNER")
        }
        val headers = mapOf("asac" to "alscadOjfleDPawx9zVoT0")
        return ElemeMtopUtil.send(API_RECEIVE_PRIZE, data.toString(), cookie, method = "GET", extraHeaders = headers)
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
        val data = JSONObject().apply {
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_CHECK_LOGIN, data.toString(), cookie, method = "POST")
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
}
