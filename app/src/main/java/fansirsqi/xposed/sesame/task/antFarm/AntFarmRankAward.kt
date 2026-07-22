package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.data.Config
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject

class AntFarmRankAward {
    companion object {
        private val TAG = AntFarmRankAward::class.java.simpleName
    }

    fun run(antFarmRankAwardField: BooleanModelField? = null) {
        try {
            Log.runtime(TAG, "开始检查并领取排位奖励")
            val response = AntFarmRpcCall.enterCompetitionAwardPage()
            if (response.isNullOrBlank()) {
                Log.error(TAG, "排位奖励🏆[获取奖励页面失败, 响应为空]")
                return
            }
            val jo = JSONObject(response)
            val memo = jo.optString("memo")
            val resultCode = jo.optString("resultCode")
            if (memo.contains("未开启") || memo.contains("功能未开启") || "FUNCTION_NOT_OPEN" == resultCode) {
                Log.runtime(TAG, "排位奖励🏆[功能未开启，已自动关闭排位奖励开关]")
                antFarmRankAwardField?.setValue(false)
                Config.save(UserMap.getCurrentUid(), false)
                return
            }
            if (!ResChecker.checkRes(TAG, jo)) {
                Log.error(TAG, "排位奖励🏆[获取奖励页面失败: $memo]")
                return
            }

            val levelAwardInfoList = jo.optJSONArray("levelAwardInfoList")
            if (levelAwardInfoList == null || levelAwardInfoList.length() == 0) {
                Log.runtime(TAG, "排位奖励🏆[没有发现等级奖励列表]")
                return
            }

            var claimedCount = 0
            for (i in 0 until levelAwardInfoList.length()) {
                val awardInfo = levelAwardInfoList.optJSONObject(i) ?: continue
                val status = awardInfo.optString("status")
                val rightsId = awardInfo.optString("rightsId")
                val levelName = awardInfo.optString("levelName")
                
                if (status == "unreceived") {
                    Log.runtime(TAG, "发现未领取的等级奖励: $levelName (rightsId: $rightsId)")
                    val claimRes = AntFarmRpcCall.receiveDonationLevelReward(rightsId)
                    if (!claimRes.isNullOrBlank()) {
                        val claimJo = JSONObject(claimRes)
                        if (ResChecker.checkRes(TAG, claimJo)) {
                            val levelNameAward = claimJo.optString("levelName", levelName)
                            val awardListStr = StringBuilder()
                            val levelAwardList = claimJo.optJSONArray("levelAwardList")
                            if (levelAwardList != null) {
                                for (j in 0 until levelAwardList.length()) {
                                    val award = levelAwardList.optJSONObject(j) ?: continue
                                    val name = award.optString("awardName")
                                    val num = award.optInt("awardNum")
                                    if (awardListStr.isNotEmpty()) awardListStr.append(", ")
                                    awardListStr.append("$name x$num")
                                }
                            }
                            Log.farm("排位奖励🏆[成功领取 $levelNameAward 奖励: $awardListStr]")
                            claimedCount++
                        } else {
                            Log.error(TAG, "排位奖励🏆[领取 $levelName 奖励失败: ${claimJo.optString("memo")}]")
                        }
                    } else {
                        Log.error(TAG, "排位奖励🏆[领取 $levelName 奖励失败, 响应为空]")
                    }
                }
            }
            if (claimedCount == 0) {
                Log.runtime(TAG, "检查完毕，没有可领取的排位奖励")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "AntFarmRankAward.run err:", t)
        }
    }
}
