package fansirsqi.xposed.sesame.task.otherTask2.logisticsinteraction

import android.net.Uri
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.task.antOrchard.AntOrchardRpcCall
import fansirsqi.xposed.sesame.task.common.GameCenterPlayRpcCall
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

                    // 检查是否为小游戏时长类任务
                    val gameContract = resolveBaoguoGameContract(task, material, title)
                    if (gameContract != null) {
                        // 若曾经被旧版本误加入黑名单，主动解除
                        if (TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(taskId)) {
                            TaskBlacklist.removeFromBlacklist(taskId, title)
                        }
                    } else if (TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(taskId)) {
                        continue
                    }

                    // 过滤无法通过 RPC 模拟的下单/真实交易事件规则任务（实物服务类）
                    if (taskType == "TRANSFORMER" && (title.contains("订单") || title.contains("搬家") || title.contains("跑腿") || title.contains("拉货"))) {
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

                    // Step B: 若为小游戏类任务，上报小游戏浏览/游玩时长
                    if (gameContract != null) {
                        try {
                            Log.other(TAG, "包裹游历📦[上报小游戏时长]#$title 时长: ${gameContract.playTime}秒, appId: ${gameContract.gameAppId}")
                            val ack = GameCenterPlayRpcCall.submitForAck(gameContract)
                            if (ack.accepted) {
                                Log.other(TAG, "包裹游历📦[时长上报已接受]#$title")
                            }
                        } catch (e: Throwable) {
                            Log.error(TAG, "包裹游历📦上报游戏时长失败: $e")
                        }
                        Thread.sleep(RandomUtil.nextLong(2500, 4000))
                    }

                    // Step C: 完成任务 (Trigger Send)
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
        val rewardRes = if (hasDoneAnyTask) {
            baoguoRpcCall.receiveAllRewards()
        } else {
            ""
        }
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

    /**
     * 解析包裹游历小游戏时长契约
     */
    private fun resolveBaoguoGameContract(task: JSONObject, material: JSONObject?, title: String): GameCenterPlayRpcCall.Contract? {
        val taskExtProps = task.optJSONObject("taskExtProps")
        val morphoDetail = taskExtProps?.optString("TASK_MORPHO_DETAIL")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

        val taskGotoUrl = material?.optString("taskGotoUrl").orEmpty().ifBlank {
            morphoDetail?.optString("taskGotoUrl").orEmpty()
        }
        val uri = taskGotoUrl.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val cdpQueryParams = uri?.getQueryParameter("cdpQueryParams").orEmpty()
        val cdpUri = cdpQueryParams.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse("https://dummy?$it") }.getOrNull() }
        val originalMap = sequenceOf(
            cdpUri?.getQueryParameter("originalMap"),
            uri?.getQueryParameter("originalMap"),
        ).firstOrNull { !it.isNullOrBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

        val durationContract = GameCenterPlayRpcCall.resolveContract(task, material, taskExtProps, morphoDetail, originalMap)
        if (durationContract != null) return durationContract

        // 通关/充值等事件型游戏任务（非时长类），不强行伪造时长上报
        val originalTaskId = originalMap?.optString("taskId").orEmpty()
        val isPassOrConversionTask = originalTaskId.contains("game_pass", ignoreCase = true) ||
            originalTaskId.contains("pass_every", ignoreCase = true) ||
            originalTaskId.contains("recharge", ignoreCase = true) ||
            title.contains("通关") ||
            title.contains("通过") ||
            title.contains("充值") ||
            title.contains("首充")
        if (isPassOrConversionTask) return null

        val isGame = title.contains("玩") || title.contains("游戏") || title.contains("消除")
        val isGameUrl = taskGotoUrl.contains("gameCenter", ignoreCase = true) ||
            taskGotoUrl.contains("outer_multi_type_task", ignoreCase = true) ||
            taskGotoUrl.contains("MINI_APP_PROMOLAYER", ignoreCase = true)

        if (!isGame && !isGameUrl) return null

        val appId = sequenceOf(
            uri?.getQueryParameter("appId"),
            uri?.getQueryParameter("gameAppId"),
            cdpUri?.getQueryParameter("appId"),
            cdpUri?.getQueryParameter("gameAppId"),
            originalMap?.optString("appId"),
            originalMap?.optString("gameAppId"),
            task.optString("appId"),
            material?.optString("appId")
        ).firstOrNull { !it.isNullOrBlank() } ?: return null

        val source = sequenceOf(
            uri?.getQueryParameter("chInfo"),
            uri?.getQueryParameter("source"),
            cdpUri?.getQueryParameter("chInfo"),
            cdpUri?.getQueryParameter("source"),
            originalMap?.optString("subScene"),
        ).firstOrNull { !it.isNullOrBlank() } ?: "wdkd_mc_xjajah06"

        val textCandidates = listOfNotNull(
            title,
            material?.optString("subTitle"),
            material?.optString("taskDesc"),
            morphoDetail?.optString("subTitle"),
            morphoDetail?.optString("taskDesc"),
            task.optString("taskDesc"),
        ).filter { it.isNotBlank() }

        val parsedSeconds = textCandidates.firstNotNullOfOrNull { GameCenterPlayRpcCall.parseDurationSeconds(it) }
            ?: queryFloatingBallDuration(appId, source)
            ?: return null

        return GameCenterPlayRpcCall.Contract(appId, parsedSeconds, source)
    }

    private fun queryFloatingBallDuration(gameAppId: String, chInfo: String): Int? =
        runCatching {
            val res = AntOrchardRpcCall.queryOptionalPlayFloatingBall(gameAppId, chInfo)
            if (res.isBlank()) return@runCatching null
            val jo = JSONObject(res)
            val taskList = jo.optJSONObject("floatingBallPlayInfo")?.optJSONArray("taskList") ?: return@runCatching null
            for (i in 0 until taskList.length()) {
                val item = taskList.optJSONObject(i) ?: continue
                val params = item.optJSONArray("multiStageVisitFloatBallParams")
                if (params != null) {
                    for (j in 0 until params.length()) {
                        val t = params.optJSONObject(j)?.optInt("timeCount", 0) ?: 0
                        if (t > 0) return@runCatching t
                    }
                }
                val t = item.optInt("timeCount", 0).takeIf { it > 0 }
                    ?: item.optInt("playTime", 0).takeIf { it > 0 }
                if (t != null) return@runCatching t
            }
            null
        }.getOrNull()
}
