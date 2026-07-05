package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.task.antMember.AntMemberRpcCall
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SesamePigeon {
    companion object {
        private const val TAG = "🐦芝麻大表鸽"
        private val executionLock = ReentrantLock()
        @Volatile
        private var isRunning = false
        private const val FLAG_KEY = "OnceDaily::SesamePigeonFinished"
    }


    fun run() {
        if (Status.hasFlagToday(FLAG_KEY)) {
            return
        }
        if (isRunning) {
            return
        }

        executionLock.withLock {
            if (isRunning || Status.hasFlagToday(FLAG_KEY)) {
                return
            }

            isRunning = true
            try {
                handlePigeonFeed()
            } catch (e: Exception) {
                Log.error(TAG, "执行异常: $e")
            } finally {
                isRunning = false
            }
        }
    }

    private fun handlePigeonFeed() {
        try {
            val homeRes = AntMemberRpcCall.Zmxy.Pigeon.queryHome()
            val homeJo = JSONObject(homeRes)
            if (homeJo.optBoolean("success")) {
                val data = homeJo.optJSONObject("data")
                if (data == null) {
                    Status.setFlagToday(FLAG_KEY)
                    return
                }
                
                val activityEnded = data.optBoolean("activityEnded", false)
                val activityDegrade = data.optBoolean("activityDegrade", false)
                if (activityEnded || activityDegrade) {
                    Status.setFlagToday(FLAG_KEY)
                    return
                }

                val todayFed = data.optBoolean("todayFed", false)
                val canFeed = data.optBoolean("canFeed", false)
                val checkedDays = data.optInt("checkedDays", 0)

                if (todayFed) {
                    Log.other("芝麻大表鸽🐦[今日已打卡] | 连续打卡 $checkedDays 天")
                    Status.setFlagToday(FLAG_KEY)
                } else if (canFeed) {
                    val feedRes = AntMemberRpcCall.Zmxy.Pigeon.feed()
                    val feedJo = JSONObject(feedRes)
                    if (feedJo.optBoolean("success") && feedJo.optBoolean("data")) {
                        Log.other("芝麻大表鸽🐦[打卡成功] | 连续打卡 ${checkedDays + 1} 天")
                        Status.setFlagToday(FLAG_KEY)
                    } else {
                        Log.error(TAG, "打卡失败: " + feedJo.optString("resultView"))
                    }
                }
            } else {
                Log.error(TAG, "查询失败: " + homeJo.optString("resultView"))
            }
        } catch (e: Exception) {
            Log.error(TAG, "大表鸽任务异常: $e")
        }
    }
}
