package fansirsqi.xposed.sesame.task.antOrchard

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.TaskBlacklist
import org.json.JSONObject

/**
 * 农场抽抽乐（芭芭农场抽奖）
 *
 * enterDrawActivity → 任务流（完成 TODO / 领取 FINISHED 奖励）→ syncDrawBalance → batchDraw 循环抽光
 *
 * 响应关键字段（注意服务端余额字段拼写为 blance）：
 * - drawActivity.activityId
 * - drawAsset.blance
 * - taskInfoList[].taskBaseInfo: taskType / sceneCode / bizInfo(title) / taskStatus / taskProdPlayType / prodPlayParam
 * - drawResultList[].prizeVO.prizeName / prizeNum
 */
class OrchardChouChouLe(private val executeIntervalInt: Int) {

    companion object {
        private const val TAG = "OrchardChouChouLe"
        private const val MAX_TASK_ROUNDS = 10
        private const val MAX_DRAW_ROUNDS = 20
    }

    /** 农场抽抽乐主入口 */
    fun run(userId: String) {
        try {
            if (userId.isBlank()) {
                Log.error(TAG, "农场抽抽乐缺少 userId")
                return
            }
            if (Status.hasFlagToday(StatusFlags.FLAG_ANTORCHARD_DRAW_ACTIVITY_PROBED)) return

            // 1. 进入活动，获取 activityId
            val entry = JSONObject(AntOrchardRpcCall.enterDrawActivity())
            if (markActivityClosed(entry)) return
            val entrySuccess = if (entry.has("success")) entry.optBoolean("success")
                else entry.optString("code") == "100000000" || ResChecker.checkRes(TAG, entry)
            if (!entrySuccess) {
                Log.error(TAG, "农场抽抽乐活动查询失败 raw=$entry")
                return
            }
            val activity = entry.optJSONObject("drawActivity") ?: JSONObject()
            val activityId = activity.optString("activityId")
            if (activityId.isBlank()) {
                Log.error(TAG, "农场抽抽乐缺少 activityId")
                return
            }
            val now = System.currentTimeMillis()
            if (activity.optLong("endTime") > 0 && now >= activity.optLong("endTime")) {
                Status.setFlagToday(StatusFlags.FLAG_ANTORCHARD_DRAW_ACTIVITY_PROBED)
                Log.farm("农场抽抽乐活动已到结束时间，今日不再探测")
                return
            }
            if (activity.optLong("startTime") > now) return

            // 2. 任务流：完成 TODO 任务、领取 FINISHED 任务奖励，循环直到无进展
            runTaskFlow()
            if (Status.hasFlagToday(StatusFlags.FLAG_ANTORCHARD_DRAW_ACTIVITY_PROBED)) return

            // 3. 同步抽奖余额
            val synced = JSONObject(AntOrchardRpcCall.syncDrawBalance(activityId))
            if (markActivityClosed(synced)) return
            if (!ResChecker.checkRes(TAG, synced)) {
                Log.error(TAG, "农场抽抽乐余额同步失败 raw=$synced")
                return
            }
            var balance = synced.optJSONObject("drawAsset")?.optInt("blance", -1) ?: -1
            if (balance < 0) {
                Log.error(TAG, "农场抽抽乐同步余额无效 raw=$synced")
                return
            }
            if (balance == 0) {
                Log.farm("农场抽抽乐: 当前暂无可抽奖次数")
            }

            // 4. 循环抽光（带轮次熔断保护）
            var drawRounds = 0
            while (balance > 0 && drawRounds < MAX_DRAW_ROUNDS) {
                drawRounds++
                val previousBalance = balance
                val response = JSONObject(AntOrchardRpcCall.batchDraw(activityId, balance, userId))
                if (markActivityClosed(response)) return
                if (!ResChecker.checkRes(TAG, response)) {
                    Log.error(TAG, "农场抽抽乐抽奖失败 activityId=$activityId times=$balance raw=$response")
                    return
                }
                val prizes = response.optJSONArray("drawResultList")
                if (prizes != null) {
                    for (index in 0 until prizes.length()) {
                        val prize = prizes.optJSONObject(index)?.optJSONObject("prizeVO") ?: continue
                        Log.farm("农场抽抽乐🎁[${prize.optString("prizeName")}] × ${prize.optInt("prizeNum", 1)}")
                    }
                }
                balance = response.optJSONObject("drawAsset")?.optInt("blance", -1) ?: -1
                if (balance < 0) {
                    Log.error(TAG, "农场抽抽乐抽奖后余额无效 raw=$response")
                    return
                }
                Log.farm("农场抽抽乐剩余次数: $balance")
                if (balance >= previousBalance) {
                    Log.error(TAG, "农场抽抽乐余额未推进，保留后续回查 raw=$response")
                    return
                }
                if (balance > 0) CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
            }

            if (drawRounds >= MAX_DRAW_ROUNDS && balance > 0) {
                Log.error(TAG, "农场抽抽乐抽奖达到最大轮次熔断上限($MAX_DRAW_ROUNDS)，已安全退出")
            }

            // 抽奖流程顺利走完后标记今日完成，避免农场轮询频繁空跑
            if (balance <= 0) {
                Status.setFlagToday(StatusFlags.FLAG_ANTORCHARD_CHOUCHOULE_DONE)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "农场抽抽乐处理异常:", t)
        }
    }

    /** 活动失效信号（到期或不可重试的停止码）：置当日旗标，当天不再探测 */
    private fun markActivityClosed(response: JSONObject): Boolean {
        if (response.optBoolean("activityExpire") ||
            (response.optString("code") == "2600000010" && !response.optBoolean("retriable", true))
        ) {
            Status.setFlagToday(StatusFlags.FLAG_ANTORCHARD_DRAW_ACTIVITY_PROBED)
            Log.farm("农场抽抽乐活动已失效，今日不再探测")
            return true
        }
        return false
    }

    /**
     * 任务流：TODO → finishDrawTask；FINISHED → receiveDrawTaskAward；RECEIVED → 跳过
     * 每轮处理后重新查询，直到无进展或达到轮次上限
     */
    private fun runTaskFlow() {
        var round = 0
        val failedTasks = mutableSetOf<String>()
        while (round < MAX_TASK_ROUNDS) {
            round++
            val response = JSONObject(AntOrchardRpcCall.listDrawTasks())
            if (markActivityClosed(response)) return
            if (!ResChecker.checkRes(TAG, response)) {
                Log.error(TAG, "农场抽抽乐任务查询失败 raw=$response")
                return
            }
            val tasks = response.optJSONArray("taskInfoList") ?: return
            var progressed = false
            var skipped = 0
            for (index in 0 until tasks.length()) {
                val task = tasks.optJSONObject(index) ?: continue
                val base = task.optJSONObject("taskBaseInfo") ?: continue
                val taskType = base.optString("taskType")
                val sceneCode = base.optString("sceneCode")
                if (taskType.isBlank() || sceneCode.isBlank()) continue
                val bizInfo = base.optString("bizInfo")
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                val title = bizInfo?.optString("title")?.takeIf { it.isNotBlank() } ?: taskType

                // 黑名单任务直接跳过
                if (TaskBlacklist.isTaskInBlacklist(title)) {
                    Log.farm("农场抽抽乐: 任务在黑名单中，跳过[$title]")
                    skipped++
                    continue
                }

                when (base.optString("taskStatus")) {
                    "TODO" -> {
                        // 之前已失败的任务，当次流程不再重复尝试
                        if (failedTasks.contains(taskType)) {
                            skipped++
                            continue
                        }
                        val finishRes = JSONObject(AntOrchardRpcCall.finishDrawTask(sceneCode, taskType))
                        if (markActivityClosed(finishRes)) return
                        if (ResChecker.checkRes(TAG, finishRes)) {
                            Log.farm("农场抽抽乐🧾[任务: $title]")
                            progressed = true
                        } else {
                            Log.error(TAG, "农场抽抽乐任务完成失败[$title] raw=$finishRes")
                            failedTasks.add(taskType)
                        }
                        CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
                    }

                    "FINISHED" -> {
                        val awardRes = JSONObject(AntOrchardRpcCall.receiveDrawTaskAward(sceneCode, taskType))
                        if (markActivityClosed(awardRes)) return
                        if (ResChecker.checkRes(TAG, awardRes)) {
                            val count = awardRes.optInt("incAwardCount")
                            Log.farm("农场抽抽乐🎁[领取任务奖励: $title] 增加${count}次机会")
                            progressed = true
                        } else {
                            Log.error(TAG, "农场抽抽乐任务领奖失败[$title] raw=$awardRes")
                            failedTasks.add(taskType)
                        }
                        CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
                    }

                    else -> skipped++
                }
            }
            if (!progressed) {
                if (skipped > 0) {
                    Log.runtime(TAG, "农场抽抽乐剩余 $skipped 个非可操作任务")
                }
                return
            }
        }
    }
}
