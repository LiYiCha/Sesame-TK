package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.task.antOrchard.GameTask
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import org.json.JSONObject

/**
 * 森林游戏中心（charitygamecenter / bizType=ANTFOREST）统一入口。
 *
 * 背景：小游戏上报（GameTask.Forest_slxcc / Forest_sljyd）只负责把"玩了几次"报给游戏中心，
 * 玩够之后服务端才会吐出可领取的宝箱额度；额度必须再调一次 drawGameCenterAward 才会变成奖励。
 * 原实现只在流程开头领取"手头已有"的额度，上报产生的额度无人领取，导致奖励一直挂着。
 *
 * 这里统一封装：查额度 → 上报 → 重查 → 领取，保证上报后一定跟一次领取。
 */
object ForestGameCenter {
    private const val TAG = "ForestGameCenter"

    /** 森林乐园里可用于刷额度的小游戏，任选其一上报即可（额度按渠道 lianyun_senlin_leyuan 计） */
    private val FOREST_GAMES = listOf(GameTask.Forest_slxcc, GameTask.Forest_sljyd)

    /**
     * 归一化后的游戏中心额度（已按 quotaPerTime 换算成"宝箱个数"）。
     *
     * quotaPerTime 存在时：quota 是积分/额度单位，需除以 quotaPerTime 才是宝箱个数；
     * 字段缺失时：quota 本身就是宝箱个数，按 1 处理（与庄园、金豆保持同一口径）。
     */
    data class DrawRights(
        val canUse: Int,
        val limit: Int,
        val used: Int,
    ) {
        /** 今日还可以刷出多少个宝箱 */
        val remain: Int get() = (limit - used).coerceAtLeast(0)
    }

    /** 查询森林游戏中心额度 */
    fun queryDrawRights(): DrawRights? {
        return try {
            val raw = AntForestRpcCall.queryGameList()
            val jo = JSONObject(raw)
            if (!ResChecker.checkRes("$TAG[queryGameList]", jo)) {
                Log.runtime(TAG, "queryGameList 失败: ${jo.optString("desc")}")
                return null
            }
            val drawRights = jo.optJSONObject("gameCenterDrawRights") ?: return null
            val perTime = drawRights.optInt("quotaPerTime", 1).coerceAtLeast(1)
            DrawRights(
                canUse = drawRights.optInt("quotaCanUse", 0) / perTime,
                limit = drawRights.optInt("quotaLimit", 0) / perTime,
                used = drawRights.optInt("usedQuota", 0) / perTime,
            )
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryDrawRights err:", t)
            null
        }
    }

    /**
     * 领取当前可开的宝箱。
     *
     * @param count 宝箱个数，<=0 时不处理
     * @return 实际领取到的宝箱个数
     */
    fun openBoxes(count: Int): Int {
        if (count <= 0) return 0
        return try {
            val raw = AntForestRpcCall.drawGameCenterAward(count)
            if (!ResChecker.checkRes("$TAG[drawAward]", raw)) {
                Log.runtime(TAG, "开启宝箱失败: $raw")
                return 0
            }
            val drawJo = JSONObject(raw)
            val resData = drawJo.optJSONObject("resData") ?: drawJo
            if (resData.optString("desc") != "success" && !drawJo.optBoolean("success")) {
                Log.runtime(TAG, "领奖请求失败: $raw")
                return 0
            }

            val awardList = resData.optJSONArray("gameCenterDrawAwardList")
            var totalEnergy = 0
            val otherAwards = mutableListOf<String>()
            if (awardList != null) {
                for (i in 0 until awardList.length()) {
                    val award = awardList.getJSONObject(i)
                    val type = award.optString("awardType")
                    val name = award.optString("awardName")
                    val count = award.optInt("awardCount")
                    if (type == "ENERGY") {
                        totalEnergy += count
                    } else {
                        otherAwards.add("${name}x${count}")
                    }
                }
            }

            val logMsg = StringBuilder("[开宝箱]x$count ")
            if (totalEnergy > 0) logMsg.append("获得能量: ${totalEnergy}g")
            if (otherAwards.isNotEmpty()) {
                if (totalEnergy > 0) logMsg.append(", ")
                logMsg.append("其他: ${otherAwards.joinToString("/")}")
            }
            Log.forest(logMsg.toString())
            awardList?.length() ?: count
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "openBoxes err:", t)
            0
        }
    }

    /**
     * 查询额度并领取所有可开的宝箱（不刷额度，只领取已产生的）。
     */
    fun openAvailableBoxes(): Int {
        val rights = queryDrawRights() ?: return 0
        if (rights.canUse <= 0) return 0
        Log.runtime(TAG, "森林游戏中心🎁[待领取宝箱 ${rights.canUse} 个]")
        return openBoxes(rights.canUse)
    }

    /**
     * 上报小游戏并立即领取产生的宝箱。
     *
     * @param remain 需要刷出的宝箱个数
     * @param prefer 指定上报的小游戏，null 时在森林乐园的小游戏里随机挑一个
     */
    fun reportAndDraw(remain: Int, prefer: GameTask? = null) {
        if (remain <= 0) return
        val task = prefer ?: FOREST_GAMES.random()
        Log.forest("森林游戏中心🎮[上报${task.title} x$remain]")
        task.report(remain)
        // 上报后额度需要服务端结算，稍作等待再重查领取
        CoroutineUtils.sleepCompat(1500)
        openAvailableBoxes()
    }
}
