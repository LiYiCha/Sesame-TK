package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import org.json.JSONArray
import org.json.JSONObject

/** 森林每日随机换装：按槽位从背包筛选可用装扮随机穿戴，主页回查确认后置当日旗标 */
object ForestDress {
    private const val TAG = "ForestDress"
    private const val FLAG_DAILY_DRESS = "forest::dailyDress"

    fun run() {
        if (Status.hasFlagToday(FLAG_DAILY_DRESS)) return
        try {
            val positions = linkedMapOf(
                "treeMain" to "tree__main", "bgSkyCloud" to "bg__sky_cloud", "bgSky0" to "bg__sky_0",
                "bgGroundA" to "bg__ground_a", "bgGroundB" to "bg__ground_b", "bgGroundC" to "bg__ground_c",
            )
            val selected = linkedMapOf<String, String>()
            for ((query, position) in positions) {
                val envelope = JSONObject(AntForestRpcCall.listUserDressForBackpack(query))
                val data = envelope.optJSONObject("resData") ?: envelope
                if (!ResChecker.checkRes(TAG, data)) return
                val owned = data.optJSONArray("userHoldDressVOList") ?: continue
                val options = (0 until owned.length()).map { owned.getJSONObject(it) }.filter {
                    it.optInt("remainNum") > 0 && it.optLong("expireTime") > System.currentTimeMillis() &&
                        it.optString("dressType").isNotBlank() && it.optJSONArray("posList")?.let { slots ->
                            (0 until slots.length()).any { index -> slots.optString(index) == position }
                        } == true
                }
                options.randomOrNull()?.let { selected[position] = it.getString("dressType") }
            }
            if (selected.isEmpty()) return
            val response = JSONObject(AntForestRpcCall.wearDress(JSONArray(selected.values.toList())))
                .let { it.optJSONObject("resData") ?: it }
            if (!ResChecker.checkRes(TAG, response)) return
            val home = JSONObject(AntForestRpcCall.queryHomePage()).let { it.optJSONObject("resData") ?: it }
            if (!ResChecker.checkRes(TAG, home)) return
            val dresses = home.optJSONObject("indexDressVO")?.optJSONArray("dressDetailList")
            val worn = (0 until (dresses?.length() ?: 0)).mapNotNull { dresses?.optJSONObject(it) }
                .associate { it.optString("position") to it.optString("dressType") }
            if (selected.all { (position, type) -> worn[position] == type }) {
                Status.setFlagToday(FLAG_DAILY_DRESS)
                Log.forest("森林每日换装已确认，共${selected.size}个槽位")
            } else {
                Log.error(TAG, "穿戴已受理，主页尚未确认全部槽位")
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException || t is InterruptedException) throw t
            Log.printStackTrace(TAG, "每日换装失败", t)
        }
    }
}
