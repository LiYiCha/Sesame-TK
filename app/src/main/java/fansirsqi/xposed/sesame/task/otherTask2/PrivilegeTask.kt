package fansirsqi.xposed.sesame.task.otherTask2

import org.json.JSONArray
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

        private const val FLAG_CHECK_IN = "youth_privilege_check_in_done"
        private const val FLAG_TRIAL_PRIZE = "youth_privilege_trial_prize_done"
        private const val FLAG_MONTHLY_PRIVILEGE = "youth_privilege_monthly_privilege_done"

        private fun isYouthSuccess(response: JSONObject): Boolean =
            response.optString("resultCode", response.optString("code")) == "SUCCESS"

        /**
         * 青春特权--任务
         */
        private fun isInRestrictedPeriod(): Boolean {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val minute = Calendar.getInstance().get(Calendar.MINUTE)
            return (hour == 9 && minute >= 50) || (hour == 10 && minute <= 2)
        }

        // 全局单线程池（惰性创建：被停止销毁后，下次执行自动重建）
        @Volatile
        private var singleThreadExecutor: ExecutorService? = null

        private val isTaskRunning = AtomicBoolean(false)

        private fun getExecutor(): ExecutorService {
            var e = singleThreadExecutor
            if (e == null || e.isShutdown) {
                synchronized(PrivilegeTask::class.java) {
                    e = singleThreadExecutor
                    if (e == null || e.isShutdown) {
                        e = Executors.newSingleThreadExecutor { r ->
                            Thread(r, "YouthPrivilegeTaskThread").apply {
                                isDaemon = false // 设置为非守护线程
                            }
                        }
                        singleThreadExecutor = e
                    }
                }
            }
            return e!!
        }

        fun executeProcessStudentTasks() {
            if (!isTaskRunning.compareAndSet(false, true)) {
                Log.runtime("$TAG 青春特权任务已在执行中，跳过本次执行")
                return
            }

            getExecutor().submit {
                try {
                    processStudentTasks()
                } catch (e: Exception) {
                    Log.printStackTrace("$TAG 青春特权任务执行异常", e)
                } finally {
                    isTaskRunning.set(false)
                }
            }
        }

        /**
         * 停止任务：中断执行线程并销毁线程池（下次执行自动重建）。
         * 任务循环内的 waitForDuration 感知中断后抛出异常退出，防止 while(true) 空转。
         */
        @JvmStatic
        fun stopTask() {
            singleThreadExecutor?.shutdownNow()
            singleThreadExecutor = null
            isTaskRunning.set(false)
            Log.runtime(TAG, "青春特权任务已停止")
        }

        private fun processStudentTasks() {
            try {
                // ── 1. 签到（优先于任务）───────────────────────────────────────
                handleCheckIn()
                waitForDuration(RandomUtil.nextLong(1000, 2000))

                // 记录每个任务的连续失败次数，防止死循环
                val taskErrorCounts = HashMap<String, Int>()

                // 外层循环：每次处理完一个任务后重新拉取任务列表，模拟真实操作节奏
                while (true) {

                    waitForDuration(RandomUtil.nextLong(1500, 1600))
                    // ── Step 1：查询最新任务列表 ──────────────────────────────
                    val queryResponse = CommonRequest().queryTaskModel()
                    val queryResult = JSONObject(queryResponse)
                    waitForDuration(RandomUtil.nextLong(2500, 2600))
                    // 检查查询是否成功
                    if (!isYouthSuccess(queryResult)) {
                        val errMsg = queryResult.optString("resultMessage", queryResult.optString("resultDesc", "查询失败"))
                        Log.error(TAG, "任务查询失败：$errMsg")
                        Log.forest(TAG, "任务查询失败：$errMsg")
                        return
                    }

                    val studentTaskModule = queryResult.optJSONObject("studentTaskModule")
                    if (studentTaskModule == null) {
                        Log.error(TAG, "未找到任务模块 (studentTaskModule)")
                        return
                    }

                    val taskGroupList = studentTaskModule.optJSONArray("taskGroupList")
                    if (taskGroupList == null || taskGroupList.length() == 0) {
                        Log.forest("$STUDENT_SIGN_PREFIX 所有任务已完成🏆")
                        claimMonthlyPrivilege()
                        claimTrialPrize()
                        return
                    }

                    val firstGroup = taskGroupList.optJSONObject(0)
                    val taskList = firstGroup?.optJSONArray("taskList")
                    if (taskList == null || taskList.length() == 0) {
                        Log.forest("$STUDENT_SIGN_PREFIX 所有任务已完成🏆")
                        claimMonthlyPrivilege()
                        claimTrialPrize()
                        return
                    }

                    // ── Step 2：单次遍历——找第一个可处理的任务 ───────────────
                    var foundPending = false // 本轮是否还存在未完成任务

                    for (i in 0 until taskList.length()) {
                        val task        = taskList.optJSONObject(i)
                        val taskCode    = task.optString("taskCode")
                        val taskSource  = task.optString("taskSource")
                        val taskType    = task.optString("taskType")
                        val taskName    = task.optString("taskName")
                        val taskStatus  = task.optString("taskStatus")
                        val prizeAmount = task.optString("prizeAmount", "0")

                        if (taskStatus == "COMPLETE" || taskStatus == "FINISHED" || taskStatus == "DONE") continue // 已完成，跳过

                        // 当前任务尚未完成，说明整体还有工作要做
                        foundPending = true

                        // 跳过异常累积过多的任务
                        val errorCount = taskErrorCounts[taskName] ?: 0
                        if (errorCount >= 3) {
                            Log.runtime("$STUDENT_SIGN_PREFIX 跳过异常过多的任务：$taskName（已失败 $errorCount 次）")
                            continue
                        }

                        try {
                            // ── Step 2a：报名阶段（TO_APPLY = 尚未报名）──────
                            if (taskStatus == "TO_APPLY") {
                                taskSignUp(taskCode, taskSource, taskType)
                                waitForDuration(RandomUtil.nextLong(6000, 7000))
                                // 报名后视为进入 PROCESSING，继续 complete
                            }

                            // ── Step 2b：提交完成（PROCESSING / 刚报名完）────
                            val completeResponse = CommonRequest().taskComplete(taskCode, taskSource, taskType)
                            val completeResult   = JSONObject(completeResponse)
                            waitForDuration(RandomUtil.nextLong(1200, 2600))

                            if (isYouthSuccess(completeResult)) {
                                Log.forest("$STUDENT_SIGN_PREFIX 完成[$taskName]+[${prizeAmount}]豆子")
                                // ✅ 成功：立即 break，回到外层 while 重新拉取任务列表
                                break
                            } else {
                                val desc = completeResult.optString("resultMessage", completeResult.optString("resultDesc", ""))
                                Log.error(TAG, "任务[$taskName]完成失败：" + if (desc.isEmpty()) completeResult.toString() else desc)
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
                        Log.forest("$STUDENT_SIGN_PREFIX 所有任务已完成🏆")
                        claimMonthlyPrivilege()
                        claimTrialPrize()
                        return
                    }
                    val pendingTasks = (0 until taskList.length())
                        .map { taskList.optJSONObject(it) }
                        .filter {
                            val st = it.optString("taskStatus")
                            st != "COMPLETE" && st != "FINISHED" && st != "DONE"
                        }
                    val allPendingExhausted = pendingTasks.all { t ->
                        (taskErrorCounts[t.optString("taskName")] ?: 0) >= 3
                    }
                    if (allPendingExhausted) {
                        Log.runtime("$STUDENT_SIGN_PREFIX 剩余任务全部因异常被跳过，退出执行")
                        claimMonthlyPrivilege()
                        claimTrialPrize()
                        return
                    }
                }
            } catch (e: JSONException) {
                Log.printStackTrace("$TAG 青春特权--任务处理异常", e)
            }
        }

        // ────────────────────────────────────────────────────────────────
        //  签到流程（对齐 AG YouthPrivilege.handleCheckIn）
        // ────────────────────────────────────────────────────────────────
        private fun handleCheckIn() {
            if (Status.hasFlagToday(FLAG_CHECK_IN)) return
            try {
                val model = JSONObject(CommonRequest().queryCheckInModel())
                if (!isYouthSuccess(model)) {
                    Log.error(TAG, "青春特权签到模型查询失败:$model")
                    return
                }
                val checkInInfo = model.optJSONObject("studentCheckInInfo") ?: run {
                    Log.error(TAG, "未找到 studentCheckInInfo")
                    return
                }
                val action = checkInInfo.optString("action")
                val checkInDate = checkInInfo.optString("checkInDate")
                val checkInSumDays = checkInInfo.optInt("checkInSumDays", 0)

                when (action) {
                    "CHECK_IN" -> {
                        val result = JSONObject(CommonRequest().checkIn())
                        if (!isYouthSuccess(result)) {
                            Log.error(TAG, "青春特权签到执行失败:$result")
                            return
                        }
                        Log.forest("$STUDENT_SIGN_PREFIX 签到✅第${checkInSumDays + 1}天")
                        // 回查确认
                        Thread.sleep(800)
                        confirmCheckInAfterAction()
                    }
                    "CHECKED_IN" -> {
                        Status.setFlagToday(FLAG_CHECK_IN)
                        Log.forest("$STUDENT_SIGN_PREFIX 今日已签到#已签${checkInSumDays}天")
                    }
                    else -> {
                        Log.runtime("$TAG 签到状态未知 action=$action")
                    }
                }
            } catch (e: Exception) {
                Log.printStackTrace("$TAG 签到异常", e)
            }
        }

        private fun confirmCheckInAfterAction() {
            try {
                val confirmation = JSONObject(CommonRequest().queryCheckInModel())
                if (!isYouthSuccess(confirmation)) return
                val action = confirmation.optJSONObject("studentCheckInInfo")?.optString("action").orEmpty()
                if (action == "CHECKED_IN") {
                    Status.setFlagToday(FLAG_CHECK_IN)
                } else {
                    Log.runtime("$TAG 签到回查未确认 action=$action")
                }
            } catch (_: Exception) {}
        }

        // ────────────────────────────────────────────────────────────────
        //  报名/完成
        // ────────────────────────────────────────────────────────────────
        private fun taskSignUp(taskCode: String, taskSource: String, taskType: String) {
            try {
                val response = JSONObject(CommonRequest().taskSignUp(taskCode, taskSource, taskType))
                if (!isYouthSuccess(response)) {
                    val desc = response.optString("resultDesc", response.optString("resultMessage", ""))
                    if (desc.isNotEmpty()) Log.error(TAG, "任务报名失败:$desc")
                }
            } catch (e: Exception) {
                Log.error(TAG, "任务报名异常：$taskCode, ${e.message}")
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

        // ────────────────────────────────────────────────────────────────
        //  青春体验金（对齐 AG YouthPrivilege.claimTrialPrize）
        // ────────────────────────────────────────────────────────────────
        private fun claimTrialPrize() {
            if (Status.hasFlagToday(FLAG_TRIAL_PRIZE)) return
            try {
                // 1. 查询触发前
                val beforeDaily = queryTrialAwards(month = false)
                val beforeMonthly = queryTrialAwards(month = true)

                // 2. 触发领取
                val triggerResp = JSONObject(CommonRequest().triggerTrialPrize())
                if (!triggerResp.optBoolean("success")) {
                    Log.error(TAG, "青春体验金触发失败: ${triggerResp.optString("resultDesc", "")} raw=$triggerResp")
                    return
                }
                val sendOrderIds = JSONArray()
                val results = triggerResp.optJSONArray("result")
                for (i in 0 until (results?.length() ?: 0)) {
                    val id = results?.optJSONObject(i)?.optString("sendOrderId").orEmpty()
                    if (id.isNotBlank()) sendOrderIds.put(id)
                }
                Thread.sleep(1500)

                // 3. 回查确认
                val afterDaily = queryTrialAwards(month = false)
                val afterMonthly = queryTrialAwards(month = true)
                val allConfirmed = (afterDaily + afterMonthly).filter { confirmedTrial(it) }

                var gotCount = 0
                for (i in 0 until sendOrderIds.length()) {
                    val oid = sendOrderIds.getString(i)
                    if (allConfirmed.any { it.optString("sendOrderId") == oid }) gotCount++
                }
                if (gotCount > 0) {
                    Log.forest("$STUDENT_SIGN_PREFIX 青春体验金领取✅[$gotCount]张")
                } else if (sendOrderIds.length() > 0) {
                    Log.runtime("$TAG 青春体验金触发了${sendOrderIds.length()}张但未回查到确认")
                }
                Status.setFlagToday(FLAG_TRIAL_PRIZE)
            } catch (e: Exception) {
                Log.printStackTrace("$TAG 青春体验金领取异常", e)
            }
        }

        private fun queryTrialAwards(month: Boolean): List<JSONObject> {
            return try {
                val response = JSONObject(CommonRequest().queryTrialPrizes(month))
                val list = response.optJSONArray("result") ?: return emptyList()
                if (!response.optBoolean("success")) return emptyList()
                (0 until list.length()).mapNotNull { list.optJSONObject(it) }
            } catch (_: Exception) { emptyList() }
        }

        private fun confirmedTrial(item: JSONObject): Boolean {
            val vouchers = item.optJSONArray("vouchers") ?: return false
            if (vouchers.length() == 0) return false
            return (0 until vouchers.length()).any {
                vouchers.optJSONObject(it)?.optString("voucher_id").orEmpty().isNotBlank()
            }
        }

        // ────────────────────────────────────────────────────────────────
        //  青春100 月权益（对齐 AG MonthlyPrivilegeAdapter）
        // ────────────────────────────────────────────────────────────────
        private fun claimMonthlyPrivilege() {
            if (Status.hasFlagToday(FLAG_MONTHLY_PRIVILEGE)) return
            try {
                val response = JSONObject(CommonRequest().queryYouth100())
                if (!isYouthSuccess(response)) {
                    val msg = response.optString("resultMessage", response.optString("resultDesc", ""))
                    if (msg.contains("授权资金信息后即可使用")) {
                        // 未授权资金 = 青春100未开通，属正常状态：打一次运行时日志并标记当天已处理，避免重复查询报错
                        Log.runtime("$STUDENT_SIGN_PREFIX 青春100未开通（$msg），跳过月权益领取")
                        Status.setFlagToday(FLAG_MONTHLY_PRIVILEGE)
                    } else {
                        Log.error(TAG, "青春100查询失败: $response")
                    }
                    return
                }
                val feeds = response.optJSONArray("feeds") ?: run {
                    Status.setFlagToday(FLAG_MONTHLY_PRIVILEGE)
                    return
                }
                var claimCount = 0
                for (i in 0 until feeds.length()) {
                    val feed = feeds.optJSONObject(i) ?: continue
                    val items = feed.optJSONArray("items") ?: continue
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j) ?: continue
                        // 已领取则跳过
                        if (item.optString("received") == "true" || item.optBoolean("received")) continue
                        val itemId = item.optString("id")
                        val moduleCode = item.optString("sceneCode").ifBlank { feed.optString("sceneCode") }
                        if (itemId.isBlank() || moduleCode.isBlank()) continue
                        val r = JSONObject(CommonRequest().receiveMonthlyPrivilege(itemId, moduleCode))
                        if (isYouthSuccess(r)) {
                            claimCount++
                            Log.forest("$STUDENT_SIGN_PREFIX 青春月权益领取✅[${item.optString("itemTitle", itemId)}]")
                        } else {
                            val desc = r.optString("resultMessage", r.optString("resultDesc", ""))
                            if (desc.isNotEmpty()) Log.error(TAG, "月权益领取失败: $desc")
                        }
                        Thread.sleep(500)
                    }
                }
                if (claimCount == 0) {
                    Log.forest("$STUDENT_SIGN_PREFIX 青春月权益已领完🏆")
                }
                Status.setFlagToday(FLAG_MONTHLY_PRIVILEGE)
            } catch (e: Exception) {
                Log.printStackTrace("$TAG 青春月权益领取异常", e)
            }
        }

        // 等待方法（中断后向上抛出，确保 while(true) 任务循环能退出而不是继续空转）
        private fun waitForDuration(duration: Long) {
            try {
                Thread.sleep(duration)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("$TAG 任务等待被中断，退出执行", e)
            }
        }
    }
}
