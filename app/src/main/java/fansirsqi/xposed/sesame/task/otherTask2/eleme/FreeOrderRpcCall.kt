package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.hook.internal.LocationHelper
import org.json.JSONObject

/**
 * 饿了么免单/互动中心 RPC 封装
 * 严格对齐真实 H5 MTOP 接口规范（tb.ele.me 互动中心与下单赢免单）
 */
object FreeOrderRpcCall {
    const val API_CENTER_HOMEPAGE = "mtop.alsc.interact.et.interact.center.homepage"
    const val API_RECORD_SIGN_IN = "mtop.alsc.interact.playapp.signin.component.recordsignin"
    const val API_ORDER_FREE_DRAW = "mtop.alsc.interact.et.orderfree.draw"
    const val API_LOTTERY_DRAW = "mtop.alsc.interact.playapp.reward.lottery.draw"

    const val API_CONSULT_UNIT = "mtop.alsc.upp.market.timelimitdraw.consultunit"
    const val API_TBET_SEND = "mtop.alsc.upp.tbet.send"
    const val API_PAGE_RECALL = "mtop.ele.rights.page.recall"

    // 阿里风控 asac token
    const val ASAC_SIGNIN = "alsc3Lhy681SA5TT4iHgL3"
    const val ASAC_ORDER_FREE_DRAW = "alscrX2P4rmvl0NhmQgr9x"
    const val ASAC_LOTTERY_DRAW = "alscdZLQXT9DEAlXvQQOLO"

    private fun getCoordinates(): Pair<Double, Double> {
        val loc = LocationHelper.getLocation()
        val lat = loc?.optString("latitude")?.toDoubleOrNull() ?: 31.2304
        val lng = loc?.optString("longitude")?.toDoubleOrNull() ?: 121.4737
        return Pair(lat, lng)
    }

    /**
     * 查询互动中心免单主页数据（包含连续签到状态、免单卡状态、下单免单与夺宝入口）
     */
    fun queryCenterHomepage(cookie: String): JSONObject? {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", "interact_center")
            put("version", "1.4.27")
            put("modules", """["signIn","property","mdCard","orderFreeDraw","orderFreeMarquee","exchangeV2","exchangeBarrage"]""")
            put("latitude", lat)
            put("longitude", lng)
            put("instance", "INNER")
        }
        val headers = mapOf("x-tap" to "wx")
        return ElemeMtopUtil.send(API_CENTER_HOMEPAGE, data.toString(), cookie, method = "GET", version = "1.0", extraHeaders = headers)
    }

    /**
     * 连续签到攒免单每日打卡（对齐幸运星.py规范：支持传入今日奖励参数与版本自愈）
     */
    fun recordSignIn(
        cookie: String,
        copyId: String,
        award: JSONObject? = null
    ): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", "interact_center")
            put("latitude", lat)
            put("longitude", lng)
            if (copyId.isNotEmpty()) {
                put("copyId", copyId)
            }
            if (award != null) {
                listOf("actCode", "sceneCode", "prizeMark", "signInDay").forEach { key ->
                    val v = award.optString(key)
                    if (v.isNotEmpty()) {
                        put(key, v)
                    }
                }
            }
        }
        val headers = mapOf("asac" to ASAC_SIGNIN, "x-tap" to "wx")
        var res = ElemeMtopUtil.execute(API_RECORD_SIGN_IN, data.toString(), cookie, method = "GET", version = "1.0", extraHeaders = headers)
        if (!res.isSuccess && !res.errorMsg.contains("已签到") && !res.errorCode.contains("REPEAT")) {
            res = ElemeMtopUtil.execute(API_RECORD_SIGN_IN, data.toString(), cookie, method = "GET", version = "1.1", extraHeaders = headers)
        }
        return res
    }

    /**
     * 下单赢免单抽奖（mtop.alsc.interact.et.orderfree.draw）
     */
    fun orderFreeDraw(cookie: String): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("bizScene", "order_free_draw")
            put("version", "1.4.2")
            put("longitude", lng.toString())
            put("latitude", lat.toString())
        }
        val headers = mapOf("asac" to ASAC_ORDER_FREE_DRAW, "x-tap" to "wx")
        return ElemeMtopUtil.execute(API_ORDER_FREE_DRAW, data.toString(), cookie, method = "GET", version = "1.0", extraHeaders = headers)
    }

    /**
     * 夺宝 / 免单机会抽奖（mtop.alsc.interact.playapp.reward.lottery.draw）
     */
    fun lotteryDraw(cookie: String, actId: String, componentId: String): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val data = JSONObject().apply {
            put("actId", actId)
            put("componentId", componentId)
            put("bizScene", "INTERACT_CENTER_LOT")
            put("bizCode", "INTERACT_CENTER_LOT")
            put("longitude", lng.toString())
            put("latitude", lat.toString())
            put("extParams", "{\"desc\":\"幸运福利\"}")
        }
        val headers = mapOf("asac" to ASAC_LOTTERY_DRAW, "x-tap" to "wx")
        return ElemeMtopUtil.execute(API_LOTTERY_DRAW, data.toString(), cookie, method = "GET", version = "1.0", extraHeaders = headers)
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
