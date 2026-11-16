package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.ElemeRiskTaskEntity
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TaskBlacklist
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock

/**
 * 饿了么抽免单自动化任务
 * 包含：
 * 1. 连续签到攒免单打卡（签到获取抽奖次数：[抽免单] 连续签到打卡: +1 抽奖机会）
 * 2. 推进全量互动浏览与打点任务（做任务获取抽奖次数：全量推进 PAGEVIEW 与 THIRD 任务，完成后 [抽免单] [任务名]: +1 抽奖机会）
 * 3. 夺宝抽免单（消耗抽奖机会，调用 mtop.alsc.interact.playapp.reward.lottery.draw，中奖通报并发送通知）
 * 4. 下单赢免单抽奖（mtop.alsc.interact.et.orderfree.draw 订单抽奖）
 * 5. 协同标记（与幸运星任务共用 FLAG_SIGNIN 与抽奖状态，杜绝重复与浪费）
 */
object FreeOrderTask {
    private const val TAG = "FreeOrderTask"
    const val FLAG_ORDER_FREE = "Eleme_OrderFree_Done"
    const val FLAG_SIGNIN = "Eleme_SignIn_Done"
    const val FLAG_LOTTERY_DONE = "Eleme_LuckyStar_Lottery_Done"
    private val lock = ReentrantLock()

    fun handle() {
        if (!ElemeTask.elemeFreeOrder.value) {
            return
        }

        if (Status.hasFlagToday(FLAG_ORDER_FREE)) {
            return
        }

        if (!lock.tryLock()) {
            return
        }

        try {
            val initCookie = ElemeAuth.ensureCookie()
            if (initCookie.isEmpty() || !ElemeAuth.isBound()) {
                val reason = if (initCookie.isEmpty()) "未获取到有效登录凭证(账号未绑定或换票失败)" else "当前未关联有效饿了么账号"
                Log.other("饿了么", "[抽免单] $reason，自动关闭【抽免单任务】开关并退出")
                ElemeTask.disableElemeFreeOrder(reason)
                return
            }

            val cookie = ElemeAuth.validateOrRefreshCookie(initCookie)
            if (cookie.isNullOrEmpty()) {
                Log.other("饿了么", "[抽免单] ⚠️ 饿了么会话校验未通过且自动刷新失败，请在支付宝手动打开饿了么小程序完成授权")
                return
            }

            // 1. 获取互动中心首页聚合数据
            val homepageResp = FreeOrderRpcCall.queryCenterHomepage(cookie)
            val rootData = homepageResp?.optJSONObject("data") ?: homepageResp
            val dataObj = rootData?.optJSONObject("data") ?: rootData

            // 2. 连续签到攒免单打卡（签到获取抽奖次数）
            if (!Status.hasFlagToday(FLAG_SIGNIN)) {
                try {
                    val signInObj = dataObj?.optJSONObject("signIn")?.optJSONObject("data")
                    val dtos = signInObj?.optJSONArray("signInQueryPrizeDTOS")
                    var todayDto: JSONObject? = null
                    if (dtos != null) {
                        for (k in 0 until dtos.length()) {
                            val d = dtos.optJSONObject(k) ?: continue
                            if (d.optBoolean("today", false)) {
                                todayDto = d
                                break
                            }
                        }
                    }
                    val signedToday = todayDto?.optBoolean("signIn", false) ?: signInObj?.optBoolean("signIn", false) ?: false
                    if (signedToday) {
                        Status.setFlagToday(FLAG_SIGNIN)
                    } else {
                        val copyId = todayDto?.optJSONObject("extInfo")?.optString("copyId")?.takeIf { it.isNotEmpty() }
                            ?: signInObj?.optJSONObject("extInfo")?.optString("copyId").orEmpty()
                        if (copyId.isNotEmpty()) {
                            val strategy = signInObj?.optString("signInRewardStrategy").orEmpty()
                            val awards = todayDto?.optJSONObject("extInfo")?.optJSONArray("stageAward")
                            val award = awards?.optJSONObject(0)
                            Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                            val signRes = LuckyStarRpcCall.signIn(cookie, copyId, strategy, award)
                            if (signRes.isSuccess || signRes.errorCode.contains("REPEAT") || signRes.errorCode.contains("ALREADY") || signRes.errorMsg.contains("已签到")) {
                                Status.setFlagToday(FLAG_SIGNIN)
                                Log.other("饿了么", "[抽免单] 连续签到打卡: +1 抽奖机会")
                            }
                        } else {
                            Status.setFlagToday(FLAG_SIGNIN)
                        }
                    }
                } catch (e: Throwable) {
                    Log.error(TAG, "签到打卡异常: ${e.message}")
                }
            }

            // 3. 推进全量互动浏览与打点任务（做任务获得抽奖次数）
            try {
                val taskListResp = LuckyStarRpcCall.queryTaskList(cookie)
                val taskArray = extractTaskArray(taskListResp)
                if (taskArray != null && taskArray.length() > 0) {
                    var finishedCount = 0
                    var blocked = false

                    for (i in 0 until taskArray.length()) {
                        val task = taskArray.optJSONObject(i) ?: continue
                        val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                        val missionXId = task.optString("missionXId")
                        val title = taskTitle(task)
                        val receiveStatus = task.optString("receiveStatus")
                        val rewardStatus = task.optString("rewardStatus")
                        val actionConfig = task.optJSONObject("actionConfig")
                        val actionType = actionConfig?.optString("actionType").orEmpty()

                        if (missionDefId <= 0) continue

                        // 已领取的任务跳过
                        if (receiveStatus == "HAVERECEIVED" || rewardStatus == "SUCCESS" || rewardStatus == "RECEIVED") {
                            continue
                        }

                        // ORDER（下单类）与 P2P（助力拉新类）属于非自动化任务，直接跳过
                        if (actionType == "ORDER" || actionType == "P2P") {
                            continue
                        }

                        // 风控跳过名单与用户自选黑名单过滤（静默跳过）
                        val riskTasks: Set<String> = ElemeTask.getElemeRiskTasks().value ?: emptySet()
                        val inRiskTaskSet = riskTasks.contains(missionDefId.toString())
                        val inGlobalBlacklist = TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(missionDefId.toString())
                        if (inRiskTaskSet || inGlobalBlacklist) {
                            Log.runtime(TAG, "[抽免单] 🛡️ 跳过风控名单任务: [$missionDefId - $title]")
                            continue
                        }

                        // 若任务已处于可领奖状态，直接领奖
                        if (LuckyStarRpcCall.isTaskRewardable(task)) {
                            val stages = LuckyStarRpcCall.getRewardStages(task)
                            if (stages.isNotEmpty()) {
                                for (st in stages) {
                                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task, st)
                                    if (prizeRes.isSuccess) {
                                        Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                    }
                                }
                            } else {
                                val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task, null)
                                if (prizeRes.isSuccess) {
                                    Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                }
                            }
                            continue
                        }

                        // 浏览任务（PAGEVIEW）推进
                        val actionValue = actionConfig?.optJSONObject("actionValue")
                        val pageSpm = actionValue?.optString("pageSpm").orEmpty()
                        val sceneCode = actionValue?.optString("scenceCode").orEmpty()

                        if (actionType == "PAGEVIEW" && pageSpm.isNotEmpty()) {
                            if (blocked) continue
                            val pageStageTime = actionValue?.optInt("pageStageTime")?.takeIf { it > 0 } ?: if (title.contains("15秒")) 15 else 10
                            Thread.sleep(pageStageTime * 1000L + RandomUtil.nextGaussianLong(500, 1500))

                            val viewRes = LuckyStarRpcCall.reportPageView(
                                cookie = cookie,
                                missionDefId = missionDefId,
                                pageSpm = pageSpm,
                                viewTime = pageStageTime + RandomUtil.nextInt(0, 3),
                                instanceId = task.optString("instanceId").takeIf { it.isNotEmpty() }
                            )
                            if (viewRes.isSuccess) {
                                Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(cookie, missionDefId)
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                val stages = LuckyStarRpcCall.getRewardStages(refreshedTask)
                                if (stages.isNotEmpty()) {
                                    for (st in stages) {
                                        val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, st)
                                        if (prizeRes.isSuccess) {
                                            Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                            finishedCount++
                                        }
                                    }
                                } else {
                                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null)
                                    if (prizeRes.isSuccess) {
                                        Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                        finishedCount++
                                    }
                                }
                            } else {
                                val msg = viewRes.errorMsg
                                if (viewRes.errorCode == "405" || msg.contains("405") || msg.contains("行为受限") || msg.contains("受限")) {
                                    val reason = "405::行为受限"
                                    ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                    TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, "405", viewRes.errorMsg)
                                    Log.other("饿了么", "[抽免单] 🛡️ 任务[$missionDefId - $title]因【$reason】已加入风控名单，停止浏览")
                                    blocked = true
                                }
                            }
                            Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
                        } else if (actionType == "THIRD" && (sceneCode == "commercial_click" || sceneCode == "inter_change" || title.contains("点击") || title.contains("店铺"))) {
                            val total = actionValue?.optInt("finalStageCount")?.takeIf { it > 0 }
                                ?: task.optJSONObject("stage")?.optInt("targetCount")?.takeIf { it > 0 }
                                ?: if (title.contains("3个") || title.contains("3次")) 3 else 1
                            val done = task.optJSONObject("stage")?.optInt("count") ?: 0
                            val need = (total - done).coerceAtLeast(0).coerceAtMost(3)
                            for (step in 1..need) {
                                val triggerRes = LuckyStarRpcCall.reportTrigger(cookie, missionDefId, missionXId)
                                if (!triggerRes.isSuccess) {
                                    if (triggerRes.errorCode == "417" || triggerRes.errorMsg.contains("417") || triggerRes.errorMsg.contains("作弊") ||
                                        triggerRes.errorCode == "405" || triggerRes.errorMsg.contains("行为受限")) {
                                        val reason = if (triggerRes.errorCode == "417" || triggerRes.errorMsg.contains("417")) "417::防作弊打点拦截" else "405::行为受限"
                                        ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                        TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, triggerRes.errorCode, triggerRes.errorMsg)
                                        Log.other("饿了么", "[抽免单] 🛡️ 任务[$missionDefId - $title]因【$reason】已加入风控名单")
                                    }
                                    break
                                }
                                Thread.sleep(RandomUtil.nextGaussianLong(1500, 2200))
                            }
                            if (need > 0) {
                                Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(cookie, missionDefId)
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                val stages = LuckyStarRpcCall.getRewardStages(refreshedTask)
                                if (stages.isNotEmpty()) {
                                    for (st in stages) {
                                        val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, st)
                                        if (prizeRes.isSuccess) {
                                            Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                            finishedCount++
                                        }
                                    }
                                } else {
                                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null)
                                    if (prizeRes.isSuccess) {
                                        Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                        finishedCount++
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.error(TAG, "免单任务推进异常: ${e.message}")
            }

            // 4. 夺宝抽免单（消耗获得的抽奖机会进行抽奖）
            try {
                if (!Status.hasFlagToday(FLAG_LOTTERY_DONE)) {
                    val resourceData = dataObj?.optJSONObject("resource")?.optJSONObject("data")
                    val lotteries = resourceData?.optJSONArray("INTERACT_CENTER_LOTTERY")
                        ?: dataObj?.optJSONArray("lotteries")
                    if (lotteries != null && lotteries.length() > 0) {
                        for (j in 0 until lotteries.length()) {
                            val lot = lotteries.optJSONObject(j) ?: continue
                            var drawCount = 0
                            val maxDraws = 3
                            while (drawCount < maxDraws) {
                                drawCount++
                                val drawRes = LuckyStarRpcCall.drawLottery(cookie, lot)
                                if (drawRes.isSuccess) {
                                    val prize = ElemeMtopUtil.extractPrizeDesc(drawRes.data ?: drawRes.rawJson).ifEmpty { "奖品" }
                                    Log.other("饿了么", "[抽免单] 🎁 夺宝抽免单: $prize")
                                    if (prize.contains("免单")) {
                                        Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在夺宝抽奖中成功命中【$prize】！请及时使用。")
                                    }
                                    Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                } else {
                                    val msg = drawRes.errorMsg
                                    if (msg.contains("已用完") || msg.contains("不足") || msg.contains("日剩") ||
                                        drawRes.errorCode.contains("NO_ENOUGH") || drawRes.errorCode.contains("LIMIT") ||
                                        drawRes.errorCode.contains("CHANCE")) {
                                        val subMsg = msg.substringAfterLast("::").take(20).ifEmpty { "日剩余机会不足" }
                                        Log.other("饿了么", "[抽免单] ⏸️ 夺宝次数已用完 ($subMsg)")
                                        Status.setFlagToday(FLAG_LOTTERY_DONE)
                                        break
                                    } else {
                                        break
                                    }
                                }
                            }
                            if (Status.hasFlagToday(FLAG_LOTTERY_DONE)) {
                                break
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.error(TAG, "夺宝抽免单异常: ${e.message}")
            }

            // 5. 下单赢免单抽奖（每日仅抽一次订单抽奖）
            Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
            val orderFreeData = dataObj?.optJSONObject("orderFreeDraw")
            val ofRes = FreeOrderRpcCall.orderFreeDraw(cookie, orderFreeData)
            Status.setFlagToday(FLAG_ORDER_FREE)

            if (ofRes.isSuccess) {
                val prize = ElemeMtopUtil.extractPrizeDesc(ofRes.data ?: ofRes.rawJson).ifEmpty { "奖励" }
                Log.other("饿了么", "[抽免单] 🎲 下单免单抽奖: $prize")
                if (prize.contains("免单")) {
                    Log.other("饿了么", "[抽免单] 🎉 恭喜成功抽中免单大奖: $prize")
                    Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在下单免单抽奖中成功命中【$prize】！请及时使用。")
                }
            } else {
                val msg = ofRes.errorMsg
                if (msg.contains("已抽") || msg.contains("不足") || msg.contains("无资格") || msg.contains("未下单") || ofRes.errorCode.contains("LIMIT")) {
                    Log.other("饿了么", "[抽免单] 🎲 下单免单抽奖: 暂无待抽订单或今日已抽")
                } else {
                    Log.other("饿了么", "[抽免单] 🎲 下单免单抽奖: 未中奖或暂无待抽订单")
                }
            }
        } catch (t: Throwable) {
            val msg = "[抽免单] ❌ 执行异常: ${t.message}"
            Log.error(TAG, msg)
            Log.other("饿了么", msg)
        } finally {
            lock.unlock()
        }
    }

    private fun extractTaskArray(root: JSONObject?): JSONArray? {
        if (root == null) return null
        val data = root.optJSONObject("data") ?: root
        return data.optJSONArray("mlist")
            ?: data.optJSONArray("missionStageDTOList")
            ?: data.optJSONArray("missionList")
    }

    private fun findTask(root: JSONObject?, missionDefId: Long): JSONObject? {
        if (root == null) return null
        val array = extractTaskArray(root) ?: return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val defId = item.optLong("missionDefId").takeIf { it > 0 } ?: item.optLong("id")
            if (defId == missionDefId) {
                return item
            }
        }
        return null
    }

    private fun taskTitle(task: JSONObject): String {
        val pinned = task.optJSONObject("ext")?.optJSONObject("extValue")?.optJSONObject("pinnedTaskTitle")?.optString("value")
        if (!pinned.isNullOrEmpty()) return pinned
        val name = task.optString("missionName").ifEmpty {
            task.optString("showTitle").ifEmpty {
                task.optString("taskName")
            }
        }
        return if (name.isNotEmpty()) name else task.optLong("missionDefId").toString()
    }
}
