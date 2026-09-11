package fansirsqi.xposed.sesame.task.antOrchard

import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import org.json.JSONObject

/**
 * 农场抽抽乐（芭芭农场抽奖）
 *
 * 移植自 Sesame-AG d2cf4c89：
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
    }

    /** 农场抽抽乐主入口 */
    fun run(userId: String) {
        try {
            if (userId.isBlank()) {
                Log.error(TAG, "农场抽抽乐缺少 userId")
                return
            }

            // 1. 进入活动，获取 activityId
            val entry = JSONObject(AntOrchardRpcCall.enterDrawActivity())
            if (!ResChecker.checkRes(TAG, entry)) {
                Log.error(TAG, "农场抽抽乐活动查询失败 raw=$entry")
                return
            }
            val activityId = entry.optJSONObject("drawActivity")?.optString("activityId").orEmpty()
            if (activityId.isBlank()) {
                Log.error(TAG, "农场抽抽乐缺少 activityId")
                return
            }

            // 2. 任务流：完成 TODO 任务、领取 FINISHED 任务奖励，循环直到无进展
            runTaskFlow()

            // 3. 同步抽奖余额
            val synced = JSONObject(AntOrchardRpcCall.syncDrawBalance(activityId))
            if (!ResChecker.checkRes(TAG, synced)) {
                Log.error(TAG, "农场抽抽乐余额同步失败 raw=$synced")
                return
            }
            var balance = synced.optJSONObject("drawAsset")?.optInt("blance", -1) ?: -1
            if (balance < 0) {
                Log.error(TAG, "农场抽抽乐同步余额无效 raw=$synced")
                return
            }

            // 4. 循环抽光
            while (balance > 0) {
                val response = JSONObject(AntOrchardRpcCall.batchDraw(activityId, balance, userId))
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
                if (balance > 0) CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "农场抽抽乐处理异常:", t)
        }
    }

    /**
     * 任务流：TODO → finishDrawTask；FINISHED → receiveDrawTaskAward；RECEIVED → 跳过
     * 每轮处理后重新查询，直到无进展或达到轮次上限
     */
    private fun runTaskFlow() {
        var round = 0
        while (round < MAX_TASK_ROUNDS) {
            round++
            val response = JSONObject(AntOrchardRpcCall.listDrawTasks())
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
                when (base.optString("taskStatus")) {
                    "TODO" -> {
                        // 浏览类任务需要按 prodPlayParam.timeCount 真实等待
                        if (base.optString("taskProdPlayType") == "VISIT_FLOAT_BALL") {
                            val playParam = runCatching { JSONObject(base.optString("prodPlayParam")) }.getOrNull()
                            val seconds = playParam?.optLong("timeCount", 0L) ?: 0L
                            if (seconds > 0) CoroutineUtils.sleepCompat(seconds * 1000)
                        }
                        val finishRes = JSONObject(AntOrchardRpcCall.finishDrawTask(sceneCode, taskType))
                        if (ResChecker.checkRes(TAG, finishRes)) {
                            Log.farm("农场抽抽乐🧾[任务: $title]")
                            progressed = true
                        } else {
                            Log.error(TAG, "农场抽抽乐任务完成失败[$title] raw=$finishRes")
                        }
                        CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
                    }

                    "FINISHED" -> {
                        val awardRes = JSONObject(AntOrchardRpcCall.receiveDrawTaskAward(sceneCode, taskType))
                        if (ResChecker.checkRes(TAG, awardRes)) {
                            val count = awardRes.optInt("incAwardCount")
                            Log.farm("农场抽抽乐🎁[领取任务奖励: $title] 增加${count}次机会")
                            progressed = true
                        } else {
                            Log.error(TAG, "农场抽抽乐任务领奖失败[$title] raw=$awardRes")
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
