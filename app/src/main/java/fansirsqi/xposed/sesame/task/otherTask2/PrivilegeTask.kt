package fansirsqi.xposed.sesame.task.otherTask2

import org.json.JSONException
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil

/*
    青春特权任务
 */
class PrivilegeTask {
    companion object {
        private const val TAG = "青春特权🌸"
        private const val YOUTH_PRIVILEGE_PREFIX = "青春特权🌸"
        private const val STUDENT_SIGN_PREFIX = "青春特权🧧"

        /**
         * 青春特权--任务
         */
        private fun isInRestrictedPeriod(): Boolean {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val minute = Calendar.getInstance().get(Calendar.MINUTE)
            return (hour == 9 && minute >= 50) || (hour == 10 && minute <= 2)
        }

        // 全局单线程池
        private val singleThreadExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
            Thread(r, "YouthPrivilegeTaskThread").apply {
                isDaemon = false // 设置为非守护线程
            }
        }

        private val isTaskRunning = AtomicBoolean(false)

        fun executeProcessStudentTasks() {
            if (!isTaskRunning.compareAndSet(false, true)) {
                Log.record("$TAG 青春特权任务已在执行中，跳过本次执行")
                return
            }

            singleThreadExecutor.submit {
                try {
                    processStudentTasks()
                } catch (e: Exception) {
                    Log.printStackTrace("$TAG 青春特权任务执行异常", e)
                } finally {
                    isTaskRunning.set(false)
                }
            }
        }

        private fun processStudentTasks() {
            try {
                // 记录每个任务的连续失败次数，防止死循环
                val taskErrorCounts = HashMap<String, Int>()

                // 外层循环：每次处理完一个任务后重新拉取任务列表，模拟真实操作节奏
                while (true) {

                    waitForDuration(RandomUtil.nextLong(1500, 1600))
                    // ── Step 1：查询最新任务列表 ──────────────────────────────
                    val queryResponse = CommonRequest().queryTaskModel("ch_appcenter__chsub_9patch", false)
                    val queryResult = JSONObject(queryResponse)
                    waitForDuration(RandomUtil.nextLong(2500, 2600))
                    // 检查查询是否成功
                    if (queryResult.optString("resultCode") != "SUCCESS") {
                        Log.error(TAG, "任务查询失败：$queryResult")
                        Log.forest(TAG, "任务查询失败：" + queryResult.optString("resultDesc"))
                        return
                    }

                    val feedsTaskVO = queryResult.optJSONObject("studentTaskModule")
                    if (feedsTaskVO == null) {
                        Log.error(TAG, "未找到任务模块")
                        return
                    }

                    val taskList = feedsTaskVO
                        .optJSONArray("taskGroupList")
                        .optJSONObject(0)
                        .optJSONArray("taskList")

                    // ── Step 2：单次遍历——找第一个可处理的任务 ───────────────
                    var foundPending = false // 本轮是否还存在未完成任务

                    for (i in 0 until taskList.length()) {
                        val task        = taskList.optJSONObject(i)
                        val taskCode    = task.optString("taskCode")
                        val taskSource  = task.optString("taskSource")
                        val taskType    = task.optString("taskType")
                        val taskName    = task.optString("taskName")
                        val taskStatus  = task.optString("taskStatus")
                        val taskBizId   = task.optString("taskBizId")
                        val prizeAmount = task.optString("prizeAmount", "0")

                        if (taskStatus == "COMPLETE") continue // 已完成，跳过

                        // 当前任务尚未完成，说明整体还有工作要做
                        foundPending = true

                        // 跳过异常累积过多的任务
                        val errorCount = taskErrorCounts[taskName] ?: 0
                        if (errorCount >= 3) {
                            Log.record("$STUDENT_SIGN_PREFIX 跳过异常过多的任务：$taskName（已失败 $errorCount 次）")
                            continue
                        }

                        try {
                            // ── Step 2a：报名阶段（TO_APPLY = 尚未报名）──────
                            if (taskStatus == "TO_APPLY") {
                                taskSignUp(taskBizId, taskCode, taskSource, taskType)
                                waitForDuration(RandomUtil.nextLong(6000, 7000))
                                // 报名后视为进入 PROCESSING，继续 complete
                            }

                            // ── Step 2b：提交完成（PROCESSING / 刚报名完）────
                            val completeResponse = CommonRequest().taskComplete(taskBizId, taskCode, taskSource, taskType)
                            val completeResult   = JSONObject(completeResponse)
                            waitForDuration(RandomUtil.nextLong(1200, 2600))

                            if (completeResult.optString("resultCode") == "SUCCESS") {
                                Log.forest("$STUDENT_SIGN_PREFIX 完成[$taskName]+[${prizeAmount}]豆子")
                                // ✅ 成功：立即 break，回到外层 while 重新拉取任务列表
                                break
                            } else {
                                val desc = completeResult.optString("resultDesc", "未知原因")
                                Log.error(TAG, "任务[$taskName]完成失败：$desc")
                                taskErrorCounts[taskName] = errorCount + 1
                            }
                        } catch (e: Exception) {
                            val newCount = errorCount + 1
                            taskErrorCounts[taskName] = newCount
                            Log.error(TAG, "任务[$taskName]异常（第 $newCount 次）：${e.message}")
                        }
                    }

                    // ── Step 3：退出条件 ──────────────────────────────────────
                    if (!foundPending) {
                        // 所有任务都是 COMPLETE，今日完成
                        //Status.setTemporaryStatusWithExpiry("privilegeTask_completed_temp", 7200000)
                        Log.forest("$STUDENT_SIGN_PREFIX 所有任务已完成🏆")
                        return
                    }
                    // foundPending == true 但内层 for 跑完也没有 break（全部被跳过/失败）
                    // 避免单个任务持续失败时无限空转：若所有待处理任务都已达到错误上限则退出
                    val pendingTasks = (0 until taskList.length())
                        .map { taskList.optJSONObject(it) }
                        .filter { it.optString("taskStatus") != "COMPLETE" }
                    val allPendingExhausted = pendingTasks.all { t ->
                        (taskErrorCounts[t.optString("taskName")] ?: 0) >= 3
                    }
                    if (allPendingExhausted) {
                        Log.record("$STUDENT_SIGN_PREFIX 剩余任务全部因异常被跳过，退出执行")
                        return
                    }
                    // 否则继续外层 while（等待下一轮 query）
                }
            } catch (e: JSONException) {
                Log.printStackTrace("$TAG 青春特权--任务处理异常", e)
            }
        }

        private fun taskSignUp(taskBizId: String, taskCode: String, taskSource: String, taskType: String): String {
            return try {
                // 发送请求
                val response = CommonRequest().taskSignUp(taskBizId, taskCode, taskSource, taskType)
                val result = JSONObject(response)

                // 检查结果
                val resultCode = result.optString("resultCode")
                val resultDesc = result.optString("resultDesc", "报名成功")

                if (resultCode.equals("SUCCESS", ignoreCase = true)) {
                    //Log.record(STUDENT_SIGN_PREFIX + "任务报名成功：" + taskCode);
                    //Log.forest(STUDENT_SIGN_PREFIX + "任务报名成功：" + taskCode);
                } else {
                    Log.error(TAG, "任务报名失败:$resultDesc")
                }

                response
            } catch (e: Exception) {
                Log.error(TAG, "任务报名异常：$taskCode, 异常信息：${e.message}")
                ""
            }
        }

        fun taskPointPrize() {
            try {
                val s = CommonRequest().triggerPointPrize()
                val result = JSONObject(s)
                if (result.getBoolean("success")) {
                    val amount = result.getString("amount")
                    Log.forest("$STUDENT_SIGN_PREFIX 浏览15s[$amount]豆子")
                    Status.setFlagToday(CompletedKeyEnum.taskPointPrize.name)
                }
            } catch (e: JSONException) {
                Log.printStackTrace("$TAG 青春特权--任务处理异常", e)
            }
        }

        // 等待方法
        private fun waitForDuration(duration: Long) {
            try {
                Thread.sleep(duration)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.error(TAG, "等待被中断")
            }
        }
    }
}
