package fansirsqi.xposed.sesame.task.otherTask

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil
import org.json.JSONObject

class ContentDayTask {
    private val TAG = "视频|每日任务"

    fun handle() {
        if (Status.hasFlagToday("ContentDayTask")) return
        val hour = TimeUtil.getHourOfDay()
        if (hour < 7) return
        // 模拟操作
        check()
        // 任务查询
        taskQuery()
        Status.setFlagToday("ContentDayTask")
    }

    private fun check() {
        runCatching {
            OtherCommonRpc().videoQueryTask()
            TimeUtil.sleep(RandomUtil.nextLong(1000, 2000))
            OtherCommonRpc().videoCheckStatus()
            TimeUtil.sleep(RandomUtil.nextLong(1000, 2000))
            OtherCommonRpc().videoTaskCenter()
        }.onFailure {
            Log.error(TAG, "check失败:${it}")
        }
    }


    private fun taskQuery(){
        runCatching {
            val taskQueryResult = OtherCommonRpc().videoTaskCenter()
            if(taskQueryResult.getBoolean("success")){
                // 解析任务列表
                val taskList = taskQueryResult.getJSONArray("taskList")

                for (i in 0 until taskList.length()) {
                    val task = taskList.getJSONObject(i)

                    // 检查是否有rewardParams字段
                    if (task.has("rewardParams")) {
                        val rewardParamsStr = task.getString("rewardParams")
                        val taskText = task.optString("taskText")
                        val taskType = task.optString("taskType")
                        val rewardParams = JSONObject(rewardParamsStr)

                        // 获取cp值作为taskActivityId
                        if (rewardParams.has("cp")) {
                            val taskActivityId = rewardParams.getString("cp")

                            //先领取任务
                            OtherCommonRpc().videoReceiveTask(rewardParams, taskType)
                            TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))
                            // 调用完成任务的方法
                            completeTask(rewardParams,taskActivityId, taskText)
                        }
                        //模拟操作
                        check()
                    }
                }
            }
        }.onFailure {
            Log.error(TAG, "任务查询失败:${it}")
        }
    }

    private fun completeTask(rewardParams: JSONObject, taskActivityId: String, taskText: String) {
        runCatching {
            // 调用两个完成任务的接口
            OtherCommonRpc().videoCompleteTask1(rewardParams,taskActivityId)
            TimeUtil.sleep(RandomUtil.nextLong(1000, 2000))
            val result = OtherCommonRpc().videoCompleteTask2(rewardParams,taskActivityId)
            if(result.optInt("resultCode")==200){
                val amount = result.optString("amount","0")
                Log.other(TAG, "完成[$taskText]+$amount")
            }
        }.onFailure {
            Log.error(TAG, "[$taskText]错误：${it}")
        }
    }
}