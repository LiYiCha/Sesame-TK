package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.hook.internal.LocationHelper
import org.json.JSONObject

/**
 * 饿了么免单/外卖秒杀券 RPC 封装
 * 严格对齐真实 H5 MTOP 接口规范（waimai-guide.ele.me 与淘免单 ETMD）
 */
object FreeOrderRpcCall {
    const val API_CONSULT_UNIT = "mtop.alsc.upp.market.timelimitdraw.consultunit"
    const val API_TBET_SEND = "mtop.alsc.upp.tbet.send"
    const val API_PAGE_RECALL = "mtop.ele.rights.page.recall"

    // 淘免单（ETMD）专属核心业务接口
    const val API_QUERY_SIGN_INFO = "mtop.koubei.interactioncenter.sign.component.querysigninfo"
    const val API_RECORD_SIGN_IN = "mtop.koubei.interactioncenter.sign.component.recordsignin"
    const val API_ETMD_HOMEPAGE = "mtop.alsc.interact.et.md.homepage"
    const val API_ICON_DRAW = "mtop.ele.growth.fission.client.etmd.et.icondraw"

    private const val BIZ_SCENE_ETMD = "ETMD"
    private const val ETMD_VERSION = "1.10.26"
    private const val UNION_PARAMS = "scene=bc3900efd5aa428f895605cdeabca7e8"

    private fun getCoordinates(): Pair<Double, Double> {
        val loc = LocationHelper.getLocation()
        val lat = loc?.optString("latitude")?.toDoubleOrNull() ?: 25.265211
        val lng = loc?.optString("longitude")?.toDoubleOrNull() ?: 110.335188
        return Pair(lat, lng)
    }

    /**
     * 查询淘免单签到信息（获取 copyId 及当天打卡状态）
     */
    fun querySignInfo(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE_ETMD)
            put("latitude", lat)
            put("longitude", lng)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_QUERY_SIGN_INFO, data.toString(), cookie, method = "GET")
    }

    /**
     * 淘免单每日打卡签到（每次打卡赠送 1~5 次免单抽奖机会 MD_CHANCE）
     */
    fun recordSignIn(cookie: String, copyId: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("copyId", copyId)
            put("bizScene", BIZ_SCENE_ETMD)
            put("latitude", lat)
            put("longitude", lng)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_RECORD_SIGN_IN, data.toString(), cookie, method = "GET", version = "1.1")
    }

    /**
     * 淘免单主页数据查询（获取当前抽奖机会、主卡片状态与开奖倒计时 tips）
     */
    fun queryEtmdHomepage(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE_ETMD)
            put("version", ETMD_VERSION)
            put("modules", """["mainPage"]""")
            put("unionParams", UNION_PARAMS)
            put("latitude", lat)
            put("longitude", lng)
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_ETMD_HOMEPAGE, data.toString(), cookie, method = "GET")
    }

    /**
     * 淘免单核心抽奖接口（触发点击“立即抽免单”/“继续抽免单”）
     */
    fun iconDraw(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", BIZ_SCENE_ETMD)
            put("version", ETMD_VERSION)
            put("unionParams", UNION_PARAMS)
            put("latitude", lat)
            put("longitude", lng)
            put("instance", "INNER")
        }
        val headers = mapOf("asac" to "alsc6AD8Rni21gsqJ5rceg")
        return ElemeMtopUtil.send(API_ICON_DRAW, data.toString(), cookie, method = "GET", extraHeaders = headers)
    }

    /**
     * 限时抽外卖秒杀券 / 免单排期咨询
     */
    fun consultUnit(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizCode", "TIME_LIMIT_DRAW_NEW")
            put("marketType", "TIME_LIMIT_DRAW")
            put("encryptSceneCode", "71cclR57CFNhphC9bF4eTA==")
            put("scheduleIds", """["2607298507304601043"]""")
            put("latitude", lat)
            put("longitude", lng)
            put("spPageId", "26090924198822617")
            put("instance", "INNER")
        }
        val headers = mapOf("asac" to "3A25826AHO2X78TBM3QXTQ")
        return ElemeMtopUtil.send(API_CONSULT_UNIT, data.toString(), cookie, method = "POST", extraHeaders = headers)
    }

    /**
     * 领取 3 张 5 元爆红包（免单与外卖专享权益）
     */
    fun sendBet(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val uid = runCatching { fansirsqi.xposed.sesame.util.maps.UserMap.currentUid }.getOrNull().orEmpty()
        val extObj = JSONObject().apply {
            put("subSubChannel", "miniapp.alipay.et")
            put("popup_source_from", "APP_READY")
            put("tbet_no_login", "false")
            put("tbsid", "")
            if (uid.isNotEmpty()) {
                put("alipayUserId", uid)
            }
        }
        val data = JSONObject().apply {
            put("ext", extObj.toString())
            put("bizCode", "ALIPAY_WAIMAI")
            put("longitude", lng)
            put("latitude", lat)
            put("instance", "INNER")
        }
        val headers = mapOf("asac" to "alscIEHa9RGen8cRSFu1rN")
        return ElemeMtopUtil.send(API_TBET_SEND, data.toString(), cookie, method = "GET", extraHeaders = headers)
    }

    /**
     * 免单主页召回与打卡
     */
    fun pageRecall(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("screenNum", "1")
            put("locationDTO", "{\"latitude\":\"$lat\",\"longitude\":\"$lng\"}")
            put("sceneCode", "ENGAGE_HUB")
            put("instance", "INNER")
        }
        return ElemeMtopUtil.send(API_PAGE_RECALL, data.toString(), cookie, method = "GET")
    }
}
