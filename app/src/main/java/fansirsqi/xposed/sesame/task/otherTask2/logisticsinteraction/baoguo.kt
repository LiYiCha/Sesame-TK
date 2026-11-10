package fansirsqi.xposed.sesame.task.otherTask2.logisticsinteraction

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TaskBlacklist
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object baoguo {

    private const val TAG = "📦包裹游历"
    private val isRunning = AtomicBoolean(false)

    // 核心任务中心 (NRgSSvWpCuA 为真实包裹游历任务，E31OtnKddQY 为扩展任务)
    private val TASK_CEN_INFOS = listOf(
        "MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA",
        "MZVPQ0DScvD6NjaPJzk8iE31OtnKddQY"
    )

    fun handle() {
        if (!isRunning.compareAndSet(false, true)) {
            Log.runtime(TAG, "已有包裹任务正在执行中，跳过本次并发调用")
            return
        }

        GlobalThreadPools.execute {
            try {
                executeTaskFlow()
            } catch (e: Exception) {
                Log.error(TAG, "包裹任务流程执行异常: ${e.message}")
            } finally {
                isRunning.set(false)
            }
        }
    }

    private fun signIn() {
        try {
            if (Status.hasFlagToday("baoguoSign")) return

            val signRes = baoguoRpcCall.signIn("AP11344756")
            if (signRes.isNotEmpty()) {
                val signJo = JSONObject(signRes)
                if (signJo.optBoolean("success")) {
                    Log.other(TAG, "每日签到任务执行成功✅")
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "签到任务执行异常: ${e.message}")
        } finally {
            Status.setFlagToday("baoguoSign")
        }
    }

    private fun executeTaskFlow() {
        // 1. 每日签到
        signIn()
        Thread.sleep(RandomUtil.nextLong(1500, 2500))

        var hasDoneAnyTask = false

        // 2. 依次查询并执行任务中心任务
        for (tci in TASK_CEN_INFOS) {
            try {
                val listRes = baoguoRpcCall.queryTaskList(tci)
                if (listRes.isEmpty()) continue
                val listJo = JSONObject(listRes)
                if (!listJo.optBoolean("success")) continue

                val taskDetailList = listJo.optJSONArray("taskDetailList") ?: continue
                if (taskDetailList.length() == 0) continue

                for (i in 0 until taskDetailList.length()) {
                    val task = taskDetailList.getJSONObject(i)
                    val taskId = task.optString("taskId")
                    val status = task.optString("taskProcessStatus")
                    val needSignUp = task.optBoolean("needSignUp")
                    val canAccess = task.optBoolean("canAccess")
                    val taskType = task.optString("taskType")

                    val material = task.optJSONObject("taskMaterial")
                    val title = material?.optString("taskMainTitle") ?: "未知任务"

                    if (!canAccess) continue
                    if (status == "RECEIVE_SUCCESS" || status == "DONE") continue

                    // 黑名单过滤（标题或任务ID命中黑名单直接跳过）
                    if (TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(taskId)) {
                        continue
                    }

                    // 过滤无法通过 RPC 模拟的下单/真实交易事件规则任务
                    if (taskType == "TRANSFORMER" && (title.contains("订单") || title.contains("搬家") || title.contains("跑腿") || title.contains("拉货") || title.contains("通关") || title.contains("玩一玩") || title.contains("消除") || title.contains("游戏"))) {
                        TaskBlacklist.autoAddToBlacklist(taskId, title, "10000005", "promoprod不允许完成事件规则任务")
                        continue
                    }

                    // Step A: 报名 (如需要)
                    if (needSignUp && status == "NONE_SIGNUP") {
                        val signupRes = baoguoRpcCall.handleTask(taskId, "signup", tci)
                        val signupJo = runCatching { JSONObject(signupRes) }.getOrNull()
                        val signupOk = signupJo?.optBoolean("success") ?: false
                        if (!signupOk) {
                            val errCode = signupJo?.optString("errorCode").orEmpty()
                            val errMsg = signupJo?.optString("errorMsg").orEmpty()
                            Log.runtime(TAG, "任务[$title]报名未成功($errCode - $errMsg)，跳过触发完成")
                            TaskBlacklist.autoAddToBlacklist(taskId, title, errCode, errMsg)
                            continue
                        }
                        // 报名成功后等待拟真间隔
                        Thread.sleep(RandomUtil.nextLong(2000, 3500))
                    }

                    // Step B: 完成任务 (Trigger Send)
                    val triggerRes = baoguoRpcCall.handleTask(taskId, "send", tci)
                    val triggerJo = runCatching { JSONObject(triggerRes) }.getOrNull()
                    if (triggerJo != null && triggerJo.optBoolean("success")) {
                        Log.other(TAG, "完成任务[$title]✅")
                        hasDoneAnyTask = true
                    } else if (triggerJo != null) {
                        val errorCode = triggerJo.optString("errorCode")
                        val errorMsg = triggerJo.optString("errorMsg")
                        Log.error(TAG, "任务[$title]触发失败: $triggerRes")
                        TaskBlacklist.autoAddToBlacklist(taskId, title, errorCode, errorMsg)
                    }

                    Thread.sleep(RandomUtil.nextLong(2500, 4500))
                }
            } catch (e: Exception) {
                Log.error(TAG, "处理任务中心[$tci]异常: ${e.message}")
            }
        }

        // 3. 棋盘游历（投骰子）
        handleBoardRoll()
        Thread.sleep(RandomUtil.nextLong(1500, 2500))

        // 4. 一键领取包裹地图所有奖励
        val rewardRes = baoguoRpcCall.receiveAllRewards()
        if (rewardRes.isNotEmpty()) {
            val rewardJo = runCatching { JSONObject(rewardRes) }.getOrNull()
            if (rewardJo != null && rewardJo.optBoolean("success")) {
                Log.other(TAG, "包裹奖励一键领取成功🎉")
            }
        }

        // 5. 查询帐户积分
        queryAccount()
    }

    /**
     * 棋盘游历（掷骰子）
     */
    private fun handleBoardRoll() {
        try {
            val latestRes = baoguoRpcCall.queryLatestBoard()
            if (latestRes.isEmpty()) return
            val latestJo = JSONObject(latestRes)
            if (!latestJo.optBoolean("success")) return

            val data = latestJo.optJSONObject("data")
            val orderId = data?.optString("orderId").orEmpty()
            if (orderId.isEmpty()) return

            val remainRollTimes = data?.optInt("remainRollTimes", 0) ?: 0
            if (remainRollTimes <= 0) return

            Log.runtime(TAG, "发现可用掷骰子次数: $remainRollTimes")
            for (i in 1..minOf(remainRollTimes, 10)) {
                val rollRes = baoguoRpcCall.rollBoard(orderId)
                if (rollRes.isNotEmpty()) {
                    val rollJo = JSONObject(rollRes)
                    if (rollJo.optBoolean("success")) {
                        Log.other(TAG, "第 $i 次掷骰子游历成功🎲")
                    } else {
                        break
                    }
                }
                Thread.sleep(RandomUtil.nextLong(2000, 3500))
            }
        } catch (e: Exception) {
            Log.error(TAG, "棋盘掷骰子异常: ${e.message}")
        }
    }

    private fun queryAccount() {
        try {
            if (Status.hasFlagToday("baoguo_account_last_query")) return
            val accountRes = baoguoRpcCall.queryAccount()
            if (accountRes.isNotEmpty()) {
                val accountJo = JSONObject(accountRes)
                if (accountJo.optBoolean("success")) {
                    val data = accountJo.optJSONObject("data")
                    val total = data?.optString("total") ?: "0"
                    val balance = data?.optString("balance") ?: "0"
                    Log.other(TAG, "当前/总余额: $balance / $total")
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "账户查询异常: ${e.message}")
        } finally {
            Status.setFlagToday("baoguo_account_last_query")
        }
    }
}
