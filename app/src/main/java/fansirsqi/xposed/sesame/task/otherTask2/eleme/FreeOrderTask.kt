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
    /** 签到：每日单次 */
    const val FLAG_SIGNIN = "Eleme_SignIn_Done"
    /** 夺宝/抽奖：每日次数用完后置位，当日不再请求 */
    const val FLAG_LOTTERY_DONE = "Eleme_LuckyStar_Lottery_Done"
    /** 下单免单抽奖：当日抽中/抽成功一次后置位 */
    const val FLAG_ORDER_FREE = "Eleme_OrderFree_Done"
    private val lock = ReentrantLock()

    fun handle() {
        if (!ElemeTask.elemeFreeOrder.value) {
            return
        }
        // 不再使用「全流程完成/抽免单完成」等入口标记屏蔽任务：
        // 每次触发都重新拉取任务列表并处理，是否重复由服务端状态与各接口自身幂等决定。

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
            val homepageResp = LuckyStarRpcCall.getHomepageData(cookie)
            // 对齐 幸运星.py：d = r["data"]，业务字段（resource/signIn/orderFreeDraw）均在 d 下。
            // 注意不可再下钻一层，否则 lotteries/orderFreeDraw 全部取不到，导致「获得次数却不抽奖」。
            val dataObj = homepageResp?.optJSONObject("data") ?: homepageResp

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
                        // 首页已明确返回今日已签到，直接标记，无需再请求
                        Status.setFlagToday(FLAG_SIGNIN)
                    } else if (signInObj != null) {
                        // copyId 缺失时不阻断签到（对齐 幸运星.py），也绝不据此置位标记，
                        // 否则首页数据抖动一次就会把当天签到永久标记完成
                        val copyId = todayDto?.optJSONObject("extInfo")?.optString("copyId").orEmpty()
                            .ifEmpty { signInObj.optJSONObject("extInfo")?.optString("copyId").orEmpty() }
                        val strategy = signInObj.optString("signInRewardStrategy")
                        val awards = todayDto?.optJSONObject("extInfo")?.optJSONArray("stageAward")
                        val award = awards?.optJSONObject(0)
                        Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                        val signRes = LuckyStarRpcCall.signIn(cookie, copyId, strategy, award)
                        if (signRes.isSuccess) {
                            Status.setFlagToday(FLAG_SIGNIN)
                            Log.other("饿了么", "[抽免单] 连续签到打卡: +1 抽奖机会")
                        } else if (signRes.errorCode.contains("REPEAT") || signRes.errorCode.contains("ALREADY") ||
                            signRes.errorMsg.contains("已签到")
                        ) {
                            Status.setFlagToday(FLAG_SIGNIN)
                        }
                        // 其余失败不置位：属可重试状态，下一轮触发会继续尝试
                    }
                } catch (e: Throwable) {
                    Log.error(TAG, "签到打卡异常: ${e.message}")
                }
            }

            // 3. 推进全量互动浏览与打点任务（做任务获得抽奖次数）
            try {
                val taskListResp = LuckyStarRpcCall.queryTaskList(cookie)
                val taskArray = LuckyStarRpcCall.extractTaskArray(taskListResp)
                if (taskArray != null && taskArray.length() > 0) {
                    var finishedCount = 0
                    var blocked = false

                    for (i in 0 until taskArray.length()) {
                        // 账号已被平台标记风控时立即中止，避免继续发请求加重标记
                        if (ElemeMtopUtil.accountRiskFlagged) break
                        val task = taskArray.optJSONObject(i) ?: continue
                        val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                        val missionXId = task.optString("missionXId")
                        val title = LuckyStarRpcCall.taskTitle(task)
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

                        // 风控名单与用户自选黑名单过滤（静默跳过，不打印日志）
                        val riskTasks: Set<String> = ElemeTask.getElemeRiskTasks().value ?: emptySet()
                        val inRiskTaskSet = riskTasks.contains(missionDefId.toString())
                        val inGlobalBlacklist = TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(missionDefId.toString())
                        if (inRiskTaskSet || inGlobalBlacklist) {
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
                                    } else {
                                        handlePrizeFailure(missionDefId, title, prizeRes)
                                    }
                                }
                            } else {
                                val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task, null)
                                if (prizeRes.isSuccess) {
                                    Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                } else {
                                    handlePrizeFailure(missionDefId, title, prizeRes)
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
                            // 停留时长对齐 幸运星.py：有 pageStageTime 取 max(3, 值)，缺失时随机 8~16 秒
                            val pageStageTime = actionValue?.optInt("pageStageTime")?.let { maxOf(3, it) }
                                ?: RandomUtil.nextInt(8, 16)
                            Thread.sleep(pageStageTime * 1000L + RandomUtil.nextGaussianLong(500, 1500))

                            val viewRes = LuckyStarRpcCall.reportPageView(
                                cookie = cookie,
                                missionDefId = missionDefId,
                                pageSpm = pageSpm,
                                viewTime = pageStageTime + RandomUtil.nextInt(0, 3),
                                instanceId = task.optString("instanceId").takeIf { it.isNotEmpty() },
                                collectionId = task.optString("missionCollectionId")
                                    .ifEmpty { task.optString("missionCollectId") }
                                    .ifEmpty { LuckyStarRpcCall.COLLECTION_ID }
                            )
                            if (viewRes.isSuccess) {
                                Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(cookie, missionDefId)
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                val stages = LuckyStarRpcCall.getRewardStages(refreshedTask)
                                // 兜底领奖仅限无阶段配置的单节点任务；带阶段配置但尚无已完成阶段时（如 0/3），
                                // 贸然领奖只会收到 STAGE_NOT_FINISH，属正常推进状态，静默等待下一轮即可
                                val staged = refreshedTask.optJSONArray("missionStageDTOS")?.length() ?: 0
                                if (stages.isNotEmpty()) {
                                    for (st in stages) {
                                        val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, st)
                                        if (prizeRes.isSuccess) {
                                            Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                            finishedCount++
                                        } else {
                                            handlePrizeFailure(missionDefId, title, prizeRes)
                                        }
                                    }
                                } else if (staged == 0 && LuckyStarRpcCall.isTaskRewardable(refreshedTask)) {
                                    // 无阶段配置的单节点任务：仍须校验任务本身到达可领态，避免未完成就领奖报 STAGE_NOT_FINISH
                                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null)
                                    if (prizeRes.isSuccess) {
                                        Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                        finishedCount++
                                    } else {
                                        handlePrizeFailure(missionDefId, title, prizeRes)
                                    }
                                }
                            } else {
                                val msg = viewRes.errorMsg
                                // 失败必须带任务名，否则通用错误日志无法对应到具体任务
                                Log.other("饿了么", "[抽免单] ❌ 浏览[$title]失败: ${viewRes.errorCode}::${msg}")
                                if (viewRes.errorCode == "405" || msg.contains("405") || msg.contains("行为受限") || msg.contains("受限")) {
                                    val reason = "405::行为受限"
                                    ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                    TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, "405", viewRes.errorMsg)
                                    Log.other("饿了么", "[抽免单] 🛡️ 任务[$missionDefId - $title]因【$reason】已加入风控名单，停止浏览")
                                    blocked = true
                                }
                            }
                            Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
                        } else if (actionType == "THIRD" && (sceneCode == "commercial_click" || sceneCode == "inter_change")) {
                            // 对齐幸运星.py _is_trigger：仅站内点击类任务可协议触发上报；
                            // 闲鱼看视频等外部结算任务（scenceCode=third）由服务端结算进度，页面端从不调 trigger，
                            // 贸然上报必然 417 防作弊拦截，且会误拉黑本来可正常领奖的任务
                            val total = actionValue?.optInt("finalStageCount")?.takeIf { it > 0 }
                                ?: task.optJSONObject("stage")?.optInt("targetCount")?.takeIf { it > 0 }
                                ?: 1 // 对齐 幸运星.py：仅取 finalStageCount，缺失默认 1，不按标题猜次数
                            val done = task.optJSONObject("stage")?.optInt("count") ?: 0
                            val need = (total - done).coerceAtLeast(0).coerceAtMost(3)
                            // 标记本任务是否已被风控拉黑：拉黑后立即终止该任务的一切后续请求（包括领奖）
                            var riskBlockedThisTask = false
                            for (step in 1..need) {
                                // 对齐幸运星.py：使用任务自身的 missionCollectionId，避免场景不匹配被服务端过滤
                                val triggerRes = LuckyStarRpcCall.reportTrigger(
                                    cookie,
                                    missionDefId,
                                    missionXId,
                                    task.optString("missionCollectionId").ifEmpty { LuckyStarRpcCall.COLLECTION_ID }
                                )
                                if (!triggerRes.isSuccess) {
                                    if (triggerRes.errorCode == "417" || triggerRes.errorMsg.contains("417") || triggerRes.errorMsg.contains("作弊") ||
                                        triggerRes.errorCode == "405" || triggerRes.errorMsg.contains("行为受限")) {
                                        val reason = if (triggerRes.errorCode == "417" || triggerRes.errorMsg.contains("417")) "417::防作弊打点拦截" else "405::行为受限"
                                        ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                        TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, triggerRes.errorCode, triggerRes.errorMsg)
                                        Log.other("饿了么", "[抽免单] 🛡️ 任务[$missionDefId - $title]因【$reason】已加入风控名单")
                                        riskBlockedThisTask = true
                                    } else {
                                        // 非风控类失败也必须带任务名，保证日志可追溯
                                        Log.other("饿了么", "[抽免单] ❌ 点击[$title]触发失败: ${triggerRes.errorCode}::${triggerRes.errorMsg}")
                                    }
                                    break
                                }
                                Thread.sleep(RandomUtil.nextGaussianLong(1500, 2200))
                            }
                            // 已被风控拉黑的任务不再刷新进度、不再领奖，避免对已受限任务重复请求
                            if (need > 0 && !riskBlockedThisTask) {
                                Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(cookie, missionDefId)
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                val stages = LuckyStarRpcCall.getRewardStages(refreshedTask)
                                // 同上：带阶段配置但无已完成阶段时不领奖，避免 STAGE_NOT_FINISH 误报
                                val staged = refreshedTask.optJSONArray("missionStageDTOS")?.length() ?: 0
                                if (stages.isNotEmpty()) {
                                    for (st in stages) {
                                        val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, st)
                                        if (prizeRes.isSuccess) {
                                            Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                            finishedCount++
                                        } else {
                                            handlePrizeFailure(missionDefId, title, prizeRes)
                                        }
                                    }
                                } else if (staged == 0 && LuckyStarRpcCall.isTaskRewardable(refreshedTask)) {
                                    // 无阶段配置的单节点任务：仍须校验任务本身到达可领态，避免未完成就领奖报 STAGE_NOT_FINISH
                                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null)
                                    if (prizeRes.isSuccess) {
                                        Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                        finishedCount++
                                    } else {
                                        handlePrizeFailure(missionDefId, title, prizeRes)
                                    }
                                }
                            }
                        }
                    }
                }

                // 趣味话题答题：actId 不在任务列表返回中（只在答题页 URL 上），
                // 仅在设置页【答题活动actId】填写后执行一次，未填写不发任何请求
                if (!ElemeMtopUtil.accountRiskFlagged) {
                    ElemeQuiz.run(cookie, "[抽免单]")
                }
            } catch (e: Throwable) {
                Log.error(TAG, "免单任务推进异常: ${e.message}")
            }

            // 4. 夺宝抽免单（每日单次任务，当日次数用完后置位标记，整天不再请求）
            try {
                if (!Status.hasFlagToday(FLAG_LOTTERY_DONE)) {
                    val resourceData = dataObj?.optJSONObject("resource")?.optJSONObject("data")
                    val lotteries = resourceData?.optJSONArray("INTERACT_CENTER_LOTTERY")
                        ?: dataObj?.optJSONArray("lotteries")
                    if (lotteries != null && lotteries.length() > 0) {
                        var drawInterrupted = false
                        for (j in 0 until lotteries.length()) {
                            if (drawInterrupted) break
                            val lot = lotteries.optJSONObject(j) ?: continue
                            // 对齐 幸运星.py：actId 或 componentId 缺失的组件直接跳过，不发无效请求
                            if (lot.optString("actId").isEmpty() || lot.optString("componentId").isEmpty()) continue
                            var drawCount = 0
                            val maxDraws = 1 // 对齐 幸运星.py：ELM_DRAW_TIMES 默认 1，每个组件抽一次，失败即停
                            while (drawCount < maxDraws) {
                                drawCount++
                                val drawRes = LuckyStarRpcCall.drawLottery(cookie, lot)
                                if (drawRes.isSuccess) {
                                    val prize = ElemeMtopUtil.extractPrizeDesc(drawRes.data ?: drawRes.rawJson)
                                    if (prize.isNotEmpty()) {
                                        Log.other("饿了么", "[抽免单] 🎁 夺宝抽免单: $prize")
                                        if (prize.contains("免单")) {
                                            Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在夺宝抽奖中成功命中【$prize】！请及时使用。")
                                        }
                                    } else {
                                        val rawResp = drawRes.rawJson?.toString() ?: drawRes.data?.toString() ?: "无响应内容"
                                        Log.other("饿了么", "[抽免单] 🎁 夺宝抽免单: 奖励描述缺失，完整响应:\n$rawResp")
                                    }
                                    Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                } else {
                                    // 判定语义对齐 幸运星.py draw_lottery：
                                    // 次数类 NO_ENOUGH_REMAIN|CHANCE|已抽完|次数|日剩；未中奖类 未中奖|NO_DECISION_STRATEGY|NOT_WINNING
                                    val msg = "${drawRes.errorCode} ${drawRes.errorMsg}"
                                    val detail = msg.substringAfterLast("::").take(20)
                                    if (Regex("NO_ENOUGH_REMAIN|CHANCE|已抽完|次数|日剩", RegexOption.IGNORE_CASE).containsMatchIn(msg)) {
                                        Log.other("饿了么", "[抽免单] ⏸️ 夺宝次数已用完(${detail.ifEmpty { "日剩余机会不足" }})")
                                        Status.setFlagToday(FLAG_LOTTERY_DONE)
                                        drawInterrupted = true
                                    } else if (Regex("未中奖|NO_DECISION_STRATEGY|NOT_WINNING", RegexOption.IGNORE_CASE).containsMatchIn(msg)) {
                                        Log.other("饿了么", "[抽免单] 🎰 夺宝未中奖")
                                    } else {
                                        Log.other("饿了么", "[抽免单] 🎰 夺宝未成功: ${detail.ifEmpty { msg.take(30) }}")
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.error(TAG, "夺宝抽免单异常: ${e.message}")
            }

            // 5. 下单赢免单抽奖（每日单次任务，当日抽成功一次即置位，整天不再重复请求）
            if (!Status.hasFlagToday(FLAG_ORDER_FREE)) {
                Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
                val orderFreeData = dataObj?.optJSONObject("orderFreeDraw")
                val ofRes = LuckyStarRpcCall.drawOrderFree(cookie, orderFreeData)
                if (ofRes.isSuccess) {
                    Status.setFlagToday(FLAG_ORDER_FREE)
                    val prize = ElemeMtopUtil.extractPrizeDesc(ofRes.data ?: ofRes.rawJson)
                    if (prize.isNotEmpty()) {
                        Log.other("饿了么", "[抽免单] 🎲 下单免单抽奖: $prize")
                        if (prize.contains("免单")) {
                            Log.other("饿了么", "[抽免单] 🎉 恭喜成功抽中免单大奖: $prize")
                            Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在下单免单抽奖中成功命中【$prize】！请及时使用。")
                        }
                    } else {
                        // 服务端未返回奖励描述时直接打印完整响应，便于核实实际抽奖结果
                        val rawResp = ofRes.rawJson?.toString() ?: ofRes.data?.toString() ?: "无响应内容"
                        Log.other("饿了么", "[抽免单] 🎲 下单免单抽奖: 奖励描述缺失，完整响应:\n$rawResp")
                    }
                }
                // 失败不置位、也不再臆测失败原因（对齐 py：orderfree_draw 失败仅静默处理），
                // 下次触发仍会重试，实际错误可由 ElemeMtopUtil 的统一错误日志查看
            }

            // 任务执行完毕，不再置位任何入口标记，下次触发仍会重新拉取并处理
        } catch (t: Throwable) {
            val msg = "[抽免单] ❌ 执行异常: ${t.message}"
            Log.error(TAG, msg)
            Log.other("饿了么", msg)
        } finally {
            lock.unlock()
        }
    }


    /**
     * 领奖失败统一处理：识别任务已关闭或不可用（累计阶段永远无法达成、活动下线等），自动加入风控名单停止重试
     */
    private fun handlePrizeFailure(missionDefId: Long, title: String, res: ElemeMtopUtil.MtopResult) {
        if (ElemeRiskTaskEntity.isTaskClosedError(res.errorCode, res.errorMsg)) {
            ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, "任务已关闭或阶段无法达成")
            Log.other("饿了么", "[抽免单] 任务[$missionDefId - $title]已关闭或不可用，已自动加入风控名单，后续执行将自动跳过")
        }
    }

    private fun findTask(root: JSONObject?, missionDefId: Long): JSONObject? {
        if (root == null) return null
        val array = LuckyStarRpcCall.extractTaskArray(root) ?: return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val defId = item.optLong("missionDefId").takeIf { it > 0 } ?: item.optLong("id")
            if (defId == missionDefId) {
                return item
            }
        }
        return null
    }

}
