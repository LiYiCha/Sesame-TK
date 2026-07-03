package fansirsqi.xposed.sesame.task.otherTask2.logisticsinteraction

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TaskBlacklist
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

object baoguo {

    private const val TAG = "📦包裹游历"
    private var currentJob: Job? = null
    private val mutex = Mutex()
    private const val taskCenInfo = "MZVPQ0DScvD6NjaPJzk8iE31OtnKddQY"

    fun handle() {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (currentJob?.isActive == true) {
                    return@launch
                }
                currentJob = launch {
                    try {
                        executeTaskFlow()
                    } catch (e: Exception) {
                        Log.error(TAG, "包裹任务流程执行异常: ${e.message}")
                    } finally {
                        currentJob = null
                    }
                }
            }
        }
    }
    private fun sigin(){
        try {
            if (Status.hasFlagToday("baoguoSign"))return

            val signRes = baoguoRpcCall.signIn("AP11344756")
            val signJo = JSONObject(signRes)
            if (signJo.optBoolean("success")) {
                Log.other(TAG, "每日签到任务执行成功")
            }
        } catch (e: Exception) {
            Log.error(TAG, "签到任务执行异常")
        }
        Status.setFlagToday("baoguoSign")
    }

    private suspend fun executeTaskFlow() {

        // 1. 优先执行特定签到任务 (如果 ID 稳定)
        sigin()
        delay(2000)

        // 2. 获取通用任务列表
        val listRes = baoguoRpcCall.queryTaskList(taskCenInfo)
        val listJo = JSONObject(listRes)
        if (!listJo.optBoolean("success")) {
            Log.error(TAG, "获取任务列表失败: $listRes")
            return
        }

        var isDoTask = false // 是否完成任务了？
        val taskDetailList = listJo.optJSONArray("taskDetailList")
        if (taskDetailList == null || taskDetailList.length() == 0) {
            Log.other(TAG, "当前无可用任务。")
        } else {
            for (i in 0 until taskDetailList.length()) {
                val task = taskDetailList.getJSONObject(i)
                val taskId = task.optString("taskId")
                val status = task.optString("taskProcessStatus")
                val needSignUp = task.optBoolean("needSignUp")
                val canAccess = task.optBoolean("canAccess")

                val material = task.optJSONObject("taskMaterial")
                val title = material?.optString("taskMainTitle") ?: "未知任务"

                if (!canAccess) {
                    continue
                }

                if (status == "RECEIVE_SUCCESS" || status == "DONE") {
                    continue
                }

                if (TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(taskId)) {
                    continue
                }

                // Step A: 报名 (如果需要)
                if (needSignUp && status == "NONE_SIGNUP") {
                    val signupRes = baoguoRpcCall.handleTask(taskId, "signup", taskCenInfo)

                    delay(2000)
                }

                // Step B: 完成任务 (Trigger Send)
                val triggerRes = baoguoRpcCall.handleTask(taskId, "send", taskCenInfo)
                val triggerJo = JSONObject(triggerRes)
                if (triggerJo.optBoolean("success")) {
                    Log.other(TAG, "完成[$title]")
                } else {
                    Log.error(TAG, "任务[$title]触发失败: $triggerRes")
                    val errorCode = triggerJo.optString("errorCode")
                    val errorMsg = triggerJo.optString("errorMsg")
                    TaskBlacklist.autoAddToBlacklist(taskId, title, errorCode, errorMsg)
                }

                // 随机延迟 2-4 秒，模仿人工
                delay((2000..4000).random().toLong())
                // 设置完成任务了
                if(!isDoTask){
                    isDoTask = true
                }
            }
        }

        // 3. 领取所有奖励
        if(isDoTask) {
            val rewardRes = baoguoRpcCall.receiveAllRewards()
            val rewardJo = JSONObject(rewardRes)
            if (rewardJo.optBoolean("success")) {
                Log.other(TAG, "包裹奖励一键领取成功。")
            } else {
                Log.error(TAG, "包裹奖励领取结果: ${rewardJo.optString("errorCode")}")
            }
        }

        // 4. 查询帐户 (一天一次)
        queryAccount()
    }
    private fun queryAccount() {
        try {
            if (Status.hasFlagToday("baoguo_account_last_query")) return
            val accountRes = baoguoRpcCall.queryAccount()
            val accountJo = JSONObject(accountRes)
            if (accountJo.optBoolean("success")) {
                val data = accountJo.optJSONObject("data")
                val total = data?.optString("total") ?: "0"
                val balance = data?.optString("balance") ?: "0"
                Log.other(TAG, "当前/总余额: $balance / $total")
            }
        } catch (e: Exception) {
            Log.error(TAG, "账户查询异常: ${e.message}")
        }
        Status.setFlagToday("baoguo_account_last_query")
    }
}


