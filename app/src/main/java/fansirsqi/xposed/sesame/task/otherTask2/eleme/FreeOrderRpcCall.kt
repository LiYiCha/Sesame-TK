package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.hook.internal.LocationHelper
import org.json.JSONObject

/**
 * 饿了么免单/订单抽奖 RPC 封装
 * 严格对齐真实 H5 MTOP 接口规范（tb.ele.me 互动中心与下单赢免单抽奖）
 */
object FreeOrderRpcCall {
    const val API_CENTER_HOMEPAGE = "mtop.alsc.interact.et.interact.center.homepage"
    const val API_ORDER_FREE_DRAW = "mtop.alsc.interact.et.orderfree.draw"
    const val API_TBET_SEND = "mtop.alsc.upp.tbet.send"
    const val ASAC_ORDER_FREE_DRAW = "alscrX2P4rmvl0NhmQgr9x"
    const val ASAC_TBET_SEND = "alscIEHa9RGen8cRSFu1rN"

    private fun getCoordinates(): Pair<Double, Double> {
        val loc = LocationHelper.getLocation()
        val lat = loc?.optString("latitude")?.toDoubleOrNull() ?: 31.2304
        val lng = loc?.optString("longitude")?.toDoubleOrNull() ?: 121.4737
        return Pair(lat, lng)
    }

    /**
     * 下单赢免单抽奖（mtop.alsc.interact.et.orderfree.draw）
     * 严格对齐 幸运星.py orderfree_draw 规范（每日仅抽一次，支持透传 cityId）
     */
    fun orderFreeDraw(cookie: String, orderFreeData: JSONObject? = null): ElemeMtopUtil.MtopResult {
        val (lat, lng) = getCoordinates()
        val cityId = orderFreeData?.optJSONObject("data")?.optJSONObject("extInfo")?.optString("cityId").orEmpty()
        val data = JSONObject().apply {
            put("bizScene", "order_free_draw")
            put("version", "1.4.2")
            put("longitude", lng.toString())
            put("latitude", lat.toString())
            if (cityId.isNotEmpty()) {
                put("cityId", cityId)
            }
        }
        val headers = mapOf("asac" to ASAC_ORDER_FREE_DRAW)
        return ElemeMtopUtil.execute(API_ORDER_FREE_DRAW, data.toString(), cookie, method = "GET", version = "1.0", extraHeaders = headers)
    }

    /**
     * 查询互动中心主页（包含 orderFreeDraw 与连续签到配置）
     */
    fun queryCenterHomepage(cookie: String): JSONObject? {
        return LuckyStarRpcCall.getHomepageData(cookie)
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
            put("longitude", lng.toString())
            put("latitude", lat.toString())
            put("instance", "INNER")
        }
        val headers = mapOf("asac" to ASAC_TBET_SEND)
        return ElemeMtopUtil.send(API_TBET_SEND, data.toString(), cookie, method = "GET", version = "1.0", extraHeaders = headers)
    }
}
