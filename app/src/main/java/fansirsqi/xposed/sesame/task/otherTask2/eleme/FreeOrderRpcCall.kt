package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.hook.internal.LocationHelper
import org.json.JSONObject

/**
 * 饿了么外卖爆红包 RPC 封装（mtop.alsc.upp.tbet.send）
 *
 * 说明：下单免单抽奖与互动中心首页已统一收敛到 LuckyStarRpcCall
 * （drawOrderFree / getHomepageData），本类只保留爆红包领取这一独立接口。
 */
object FreeOrderRpcCall {
    const val API_TBET_SEND = "mtop.alsc.upp.tbet.send"
    const val ASAC_TBET_SEND = "alscIEHa9RGen8cRSFu1rN"

    private fun getCoordinates(): Pair<Double, Double> {
        val loc = LocationHelper.getLocation()
        val lat = loc?.optString("latitude")?.toDoubleOrNull() ?: 31.2304
        val lng = loc?.optString("longitude")?.toDoubleOrNull() ?: 121.4737
        return Pair(lat, lng)
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
