package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil

class UgShooping {
    private val TAG = "天天领现金"
    private var blackList = hashSetOf<String>()  // 任务黑名单
    private val skipTaskList = hashSetOf<String>("下单领购物金") // 跳过任务
    private var taskFailureCount = mutableMapOf<String, Int>() // 记录任务失败次数

    fun handle() {
        // 签到时间在7点之前，不执行任务
        val hour = TimeUtil.getHourOfDay()
        if (hour < 7) return

        // 初始化黑名单
        initBlackList()

        // 首页
        CheckHome()

        // 签到
        if (!Status.hasFlagToday("ugShooping_signin")) {
            doSignIn()
        }

        // 任务处理
        handleTask()
    }

    // 初始化黑名单
    private fun initBlackList() {
        val storedBlackList = DataStore.get("ugShopping_blackList", Set::class.java)
        if (storedBlackList != null) {
            blackList = storedBlackList as HashSet<String>
        }
    }

    // 签到?
    private fun doSignIn() {
        try {
            var result = CommonRequest().ugShoopingSignIn()
            if (result.optBoolean("success")) {
                val rewardAmount = result.optString("rewardAmount")
                Log.other(TAG, "签到成功，获得${rewardAmount}元")
            } else {
                Log.error(TAG, "签到失败:${result}")
            }
            Status.setFlagToday("ugShooping_signin")
        } catch (e: Exception) {
            Log.error(TAG, "签到失败:${e}")
        }
    }

    // 任务处理
    private fun handleTask() {
        queryTaskList()
    }

    // 任务列表
    private fun queryTaskList() {
        var ugShoopingTaskList = CommonRequest().ugShoopingTaskList()
        if (ugShoopingTaskList.optBoolean("success")) {
            val gwjTaskDTO = ugShoopingTaskList.optJSONObject("gwjTaskDTO")
            val promoTaskList = gwjTaskDTO?.optJSONArray("promoTaskList")
            if (promoTaskList != null) {
                for (i in 0 until promoTaskList.length()) {
                    val promoTask = promoTaskList.optJSONObject(i)
                    val taskCode = promoTask.optString("taskCode")
                    val subTaskCode = promoTask.optString("subTaskCode", "")
                    val taskTitle = promoTask.optString("taskTitle")
                    //val taskType = promoTask.optString("taskType")
                    val taskStatus = promoTask.optString("taskStatus")

                    // 跳过已完成的任务
                    if (taskStatus == "FINISHED") continue

                    // 跳过需要跳过的任务
                    if (shouldSkipTask(taskTitle)) continue

                    val result = doTask(taskCode, subTaskCode, taskTitle)
                    if (!result) {
                        // 记录失败次数
                        val failureCount = taskFailureCount.getOrDefault(taskTitle, 0) + 1
                        taskFailureCount[taskTitle] = failureCount

                        // 失败2次，加入黑名单
                        if (failureCount >= 2) {
                            blackList.add(taskTitle)
                            DataStore.put("ugShopping_blackList", blackList)
                            Log.record(TAG, "任务[${taskTitle}]已失败${failureCount}次，加入黑名单")
                        }
                    } else {
                        // 成功则清除失败计数
                        taskFailureCount.remove(taskTitle)
                    }
                    TimeUtil.sleep(RandomUtil.nextLong(5000, 9000))
                    //更新下任务？
                    CheckHome()
                }
            }
        }
    }

    // 判断是否应该跳过任务
    private fun shouldSkipTask(taskTitle: String): Boolean {
        // 检查是否在黑名单中
        if (blackList.contains(taskTitle)) {
            //Log.other(TAG, "任务[${taskTitle}]在黑名单中，跳过执行")
            return true
        }

        // 检查是否在跳过列表中
        if (skipTaskList.contains(taskTitle)) {
            //Log.other(TAG, "任务[${taskTitle}]在跳过列表中，跳过执行")
            return true
        }

        return false
    }

    // 任务处理
    private fun doTask(taskCode: String, subTaskCode: String, taskTitle: String): Boolean {
        try {
            var result = CommonRequest().ugShoopingTaskHandle(taskCode, subTaskCode)
            if (result.optBoolean("success")) {
                val rewardAmount = result.optString("rewardAmount")
                Log.other(TAG, "完成[${taskTitle}]获得${rewardAmount}元")
                return true
            } else {
                Log.error(TAG, "任务[${taskTitle}]失败:${result}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "任务[${taskTitle}]异常:${e}")
        }
        return false
    }

    // 首页？
    private fun CheckHome() {
        CommonRequest().getExperimentResult1()
        CommonRequest().getExperimentResult2()
        CommonRequest().ugShoopingTaskList()

    }
}
