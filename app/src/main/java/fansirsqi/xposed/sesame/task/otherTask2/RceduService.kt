package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.GlobalThreadPools.sleepCompat
import fansirsqi.xposed.sesame.util.Log

/**
 * 多懂一点小程序|
 */
class RceduService {
    private val TAG = "📚多懂一点"
    private var userCode = ""
    fun handle(){
        val userCode = queryUserInfo()
        if (userCode.isNotEmpty()){
            if (!Status.hasFlagToday("rceduSignIn")) {
                doSignin()
            }
            if (!Status.hasFlagToday("rceduDoDailyTask")) {
                doDailyTaskQuery()
            }
        }
        Status.setFlagToday("rceduService_handle")
    }

    private fun doDailyTaskQuery() {
        val result = CommonRequest().dailyTaskQuery()
        try {
            if (result.optBoolean("success")){
                val result = result.optJSONObject("result")
                val dailyTasks = result.optJSONArray("dailyTasks")
                for (i in 0 until dailyTasks.length()) {
                    val task = dailyTasks.optJSONObject(i)
                    val code = task.optString("code")
                    val status = task.optString("status")
                    if(!status.equals("RECEIVED")){
                        CommonRequest().dailyTaskHandle(code, "WAIT_RECEIVE")
                        // 兑换成功后立即尝试领取奖励
                        sleepCompat(3000L)
                        CommonRequest().dailyTaskHandle(code, "RECEIVED")
                        Log.other(TAG, "完成日常任务[$code]")
                    }
                }
            }else{
                Log.error(TAG, "查询日常任务失败:[ $result ]")
            }
        }catch (e: Exception){
            Log.error(TAG, "查询日常任务失败:[ $e ]")
        }finally {
            Status.setFlagToday("rceduDoDailyTask")
        }
    }

    /**
     * 查询用户信息
     */
    private fun queryUserInfo(): String{
        val result = CommonRequest().rceduQueryUserInfo()
        try {
            if (result.optBoolean("success")){
                val userInfo = result.optJSONObject("result")
                if (userInfo != null) {
                    this.userCode = userInfo.optString("code","")
                    return this.userCode
                }
            }else{
                Log.error(TAG, "查询用户信息失败:[ $result ]")
            }
        }catch (e: Exception){
            Log.error(TAG, "查询用户信息异常:[ $e ]")
        }
        return ""
    }
    /**
     * 签到
     */
    private fun doSignin(){
        val result = CommonRequest().rceduSignIn(this.userCode)
        try {
            if (result.optBoolean("success")){
                Log.other(TAG, "签到成功")
            }else{
                Log.error(TAG, "签到失败:[ $result ]")
            }
        } catch (e: Exception) {
            Log.error(TAG, "签到异常:[ $e ]")
        } finally {
            Status.setFlagToday("rceduSignIn")
        }
    }
}