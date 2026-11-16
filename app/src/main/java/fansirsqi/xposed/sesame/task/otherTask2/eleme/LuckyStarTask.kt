package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TaskBlacklist
import org.json.JSONArray
import org.json.JSONObject

import java.util.concurrent.locks.ReentrantLock

/**
 * 饿了么幸运星日常自动化任务
 * 支持自动签到、做任务（15s浏览、多步计数3次）、领奖励、星数统计
 * 单例设计与并发互斥锁保护，防止多线程冲突
 */
object LuckyStarTask {
    private const val TAG = "LuckyStarTask"
    private const val FLAG_SIGNIN = FreeOrderTask.FLAG_SIGNIN
    private const val FLAG_LOTTERY_DONE = "Eleme_LuckyStar_Lottery_Done"
    private val lock = ReentrantLock()

    fun handle() {
        if (!ElemeTask.elemeLuckyStar.value) {
            return
        }

        if (!lock.tryLock()) {
            return
        }

        try {
            val initCookie = ElemeAuth.ensureCookie()
            if (initCookie.isEmpty() || !ElemeAuth.isBound()) {
                val reason = if (initCookie.isEmpty()) "未获取到有效登录凭证(账号未绑定或换票失败)" else "当前未关联有效饿了么账号"
                Log.other("饿了么", "[幸运星] $reason，自动关闭【幸运星任务】开关并退出")
                ElemeTask.disableElemeLuckyStar(reason)
                return
            }

            // 1. 严格校验会话有效性，若已失效则自动换票自愈刷新
            val cookie = ElemeAuth.validateOrRefreshCookie(initCookie)
            if (cookie.isNullOrEmpty()) {
                Log.other("饿了么", "[幸运星] ⚠️ 饿了么会话校验未通过且自动刷新失败，请在支付宝手动打开饿了么小程序完成授权")
                return
            }

            // 2. 拟真前置生命周期：会话状态检查 -> 前置营销弹窗与权益召回 -> 页面动效/弹窗模态任务组件 -> 订阅提醒
            LuckyStarRpcCall.checkLogin(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(300, 500))
            LuckyStarRpcCall.preflightMarketing(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(300, 500))
            LuckyStarRpcCall.initPageComponents(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(300, 500))
            LuckyStarRpcCall.querySubscribeRemind(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(400, 700))

            // 3. 首页聚合：获取星数、签到、夺宝/抽奖及任务集合 ID
            val homepageResp = LuckyStarRpcCall.getHomepageData(cookie)
            val hpData = homepageResp?.optJSONObject("data") ?: homepageResp
            val initialStars = extractStarAmount(hpData)
            if (initialStars > 0) {
                Log.other("饿了么", "[幸运星] ⭐ 现有幸运星: $initialStars")
            }

            // 3.1 幸运星每日打卡签到（签到需要加 Flag）
            try {
                if (!Status.hasFlagToday(FLAG_SIGNIN)) {
                    val signinData = hpData?.optJSONObject("signIn")?.optJSONObject("data")
                    val dtos = signinData?.optJSONArray("signInQueryPrizeDTOS")
                    var todayDto: JSONObject? = null
                    if (dtos != null) {
                        for (i in 0 until dtos.length()) {
                            val d = dtos.optJSONObject(i) ?: continue
                            if (d.optBoolean("today", false)) {
                                todayDto = d
                                break
                            }
                        }
                    }
                    val signedToday = todayDto?.optBoolean("signIn", false) ?: false
                    if (signedToday) {
                        Status.setFlagToday(FLAG_SIGNIN)
                    } else {
                        val copyId = todayDto?.optJSONObject("extInfo")?.optString("copyId")
                            ?: signinData?.optJSONObject("extInfo")?.optString("copyId").orEmpty()
                        if (copyId.isEmpty()) {
                            Status.setFlagToday(FLAG_SIGNIN)
                        } else {
                            val strategy = signinData?.optString("signInRewardStrategy").orEmpty()
                            val awards = todayDto?.optJSONObject("extInfo")?.optJSONArray("stageAward")
                            val award = awards?.optJSONObject(0)
                            val signRes = LuckyStarRpcCall.signIn(cookie, copyId, strategy, award)
                            if (signRes.isSuccess) {
                                Status.setFlagToday(FLAG_SIGNIN)
                                val rewText = extractSignInReward(signRes.data)
                                val rewMsg = if (rewText.isNotEmpty()) ": $rewText" else ""
                                Log.other("饿了么", "[幸运星] 📅 签到打卡$rewMsg")
                            } else if (signRes.errorCode.contains("REPEAT") || signRes.errorCode.contains("ALREADY") || signRes.errorMsg.contains("已签到")) {
                                Status.setFlagToday(FLAG_SIGNIN)
                            }
                        }
                        Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
                    }
                }
            } catch (e: Throwable) {
                Log.error(TAG, "幸运星签到异常: ${e.message}")
            }

            // 3.2 夺宝/抽奖（首页 INTERACT_CENTER_LOTTERY 组件）
            try {
                if (!Status.hasFlagToday(FLAG_LOTTERY_DONE)) {
                    val resourceCpns = hpData?.optJSONObject("resource")?.optJSONObject("data")
                    val lotteries = resourceCpns?.optJSONArray("INTERACT_CENTER_LOTTERY")
                    if (lotteries != null && lotteries.length() > 0) {
                        for (k in 0 until lotteries.length()) {
                            val lot = lotteries.optJSONObject(k) ?: continue
                            var drawCount = 0
                            val maxDraws = 3 // 单组件最多连抽 3 次保护
                            while (drawCount < maxDraws) {
                                drawCount++
                                val drawRes = LuckyStarRpcCall.drawLottery(cookie, lot)
                                if (drawRes.isSuccess) {
                                    val prize = extractLotteryPrize(drawRes.data)
                                    if (prize.isNotEmpty()) {
                                        Log.other("饿了么", "[幸运星] 🎁 夺宝抽奖: $prize")
                                        if (prize.contains("免单")) {
                                            Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在夺宝抽奖中成功命中【$prize】！请及时使用。")
                                        }
                                    }
                                    Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                } else {
                                    val msg = drawRes.errorMsg
                                    if (msg.contains("已用完") || msg.contains("不足") || msg.contains("日剩") ||
                                        drawRes.errorCode.contains("NO_ENOUGH") || drawRes.errorCode.contains("LIMIT") ||
                                        drawRes.errorCode.contains("CHANCE")) {
                                        val subMsg = msg.substringAfterLast("::").take(20).ifEmpty { "日剩余机会不足" }
                                        Log.other("饿了么", "[幸运星] ⏸️ 夺宝次数已用完 ($subMsg)")
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
                Log.error(TAG, "幸运星夺宝抽奖异常: ${e.message}")
            }

            // 3.3 下单免单抽奖组件（若今日尚未执行）
            try {
                if (!Status.hasFlagToday(FreeOrderTask.FLAG_ORDER_FREE)) {
                    val orderFreeData = hpData?.optJSONObject("orderFreeDraw")
                    if (orderFreeData != null) {
                        val ofRes = LuckyStarRpcCall.drawOrderFree(cookie, orderFreeData)
                        Status.setFlagToday(FreeOrderTask.FLAG_ORDER_FREE)
                        if (ofRes.isSuccess) {
                            val prize = extractLotteryPrize(ofRes.data).ifEmpty { "奖励" }
                            Log.other("饿了么", "[幸运星] 🎲 下单免单抽奖: $prize")
                            if (prize.contains("免单")) {
                                Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在下单免单抽奖中成功命中【$prize】！请及时使用。")
                            }
                        }
                        Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
                    }
                }
            } catch (_: Throwable) {}

            // 提取任务集合 ID
            val resourceCpns = hpData?.optJSONObject("resource")?.optJSONObject("data")
            val interactResource = resourceCpns?.optJSONArray("INTERACT_RESOURCE_CPN")
            val firstResource = interactResource?.optJSONObject(0)
            val missionCollectionId = firstResource?.optString("missionCollectionId")?.takeIf { it.isNotEmpty() } ?: LuckyStarRpcCall.COLLECTION_ID

            // 4. 查询全量任务列表
            val taskListResp = LuckyStarRpcCall.queryTaskList(cookie, collectionId = missionCollectionId)
            if (taskListResp == null) {
                val err = ElemeMtopUtil.lastError ?: "接口请求失败或未返回有效数据"
                val raw = ElemeMtopUtil.lastResponse ?: "无原始响应"
                val msg = "[幸运星] ❌ 获取任务列表失败: $err\n完整响应:\n$raw"
                Log.error(TAG, msg)
                Log.other("饿了么", msg)
                return
            }

            val taskArray = extractTaskArray(taskListResp)
            var finishedCount = 0
            var rewardedCount = 0
            var hasRewardable = false
            var blockedCount = 0

            if (taskArray != null && taskArray.length() > 0) {
                // 第一轮统计分类
                val kinds = mutableMapOf<String, Int>()
                for (i in 0 until taskArray.length()) {
                    val t = taskArray.optJSONObject(i) ?: continue
                    val at = t.optJSONObject("actionConfig")?.optString("actionType")?.ifEmpty { "?" } ?: "?"
                    kinds[at] = (kinds[at] ?: 0) + 1
                }
                val kindsDesc = kinds.toSortedMap().map { "${it.key}×${it.value}" }.joinToString(", ")
                Log.other("饿了么", "[幸运星] 📋 第一轮: ${taskArray.length()} 个任务 ($kindsDesc), 开始推进")

                // ==== 第一轮: 遍历执行可自动化的浏览与点击任务 ====
                for (i in 0 until taskArray.length()) {
                    val task = taskArray.optJSONObject(i) ?: continue
                    val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                    val missionXId = task.optString("missionXId")
                    val title = taskTitle(task)
                    val receiveStatus = task.optString("receiveStatus")
                    val rewardStatus = task.optString("rewardStatus")

                    if (missionDefId <= 0) continue

                    // 已领取的任务跳过
                    if (receiveStatus == "HAVERECEIVED" || rewardStatus == "SUCCESS" || rewardStatus == "RECEIVED") {
                        continue
                    }

                    // UI 风控跳过名单与用户自选黑名单过滤（静默跳过）
                    val riskTasks: Set<String> = ElemeTask.getElemeRiskTasks().value ?: emptySet()
                    val inRiskTaskSet = riskTasks.contains(missionDefId.toString())
                    val inGlobalBlacklist = TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(missionDefId.toString())
                    if (inRiskTaskSet || inGlobalBlacklist) {
                        continue
                    }

                    // 若任务已处于可领奖状态，标记待领奖
                    if (LuckyStarRpcCall.isTaskRewardable(task)) {
                        hasRewardable = true
                        continue
                    }

                    // 严格遵循幸运星.py规范：按 actionConfig 分发，非 ORDER/P2P，绝不按名称关键词误杀浏览任务
                    val actionConfig = task.optJSONObject("actionConfig")
                    val actionType = actionConfig?.optString("actionType").orEmpty()
                    if (actionType == "ORDER" || actionType == "P2P") {
                        continue
                    }
                    val actionValue = actionConfig?.optJSONObject("actionValue")
                    val pageSpm = actionValue?.optString("pageSpm").orEmpty()
                    val sceneCode = actionValue?.optString("scenceCode").orEmpty()

                    if (actionType == "PAGEVIEW" && pageSpm.isNotEmpty()) {
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
                            Log.other("饿了么", "[幸运星] 完成[$title]")
                            finishedCount++
                            hasRewardable = true
                        } else {
                            val rawResp = viewRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: viewRes.errorMsg
                            val errDetail = "[幸运星] ❌ 浏览[$title]失败: ${viewRes.errorCode}::${viewRes.errorMsg}\n完整响应:\n$rawResp"
                            Log.error(TAG, errDetail)
                            Log.other("饿了么", errDetail)
                            val msg = viewRes.errorMsg
                            if (viewRes.errorCode == "405" || msg.contains("405") || msg.contains("行为受限") || msg.contains("受限")) {
                                Log.other("饿了么", "[幸运星] ⛔ 检测到接口行为受限 (405::行为受限)，为保护账号安全，停止今日后续浏览埋点")
                                break
                            }
                        }
                        Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                    } else if (actionType == "THIRD" && (sceneCode == "commercial_click" || sceneCode == "inter_change" || title.contains("点击") || title.contains("店铺"))) {
                        val total = actionValue?.optInt("finalStageCount")?.takeIf { it > 0 }
                            ?: task.optJSONObject("stage")?.optInt("targetCount")?.takeIf { it > 0 }
                            ?: if (title.contains("3个") || title.contains("3次")) 3 else 1
                        val done = task.optJSONObject("stage")?.optInt("count") ?: 0
                        val need = (total - done).coerceAtLeast(0).coerceAtMost(3)
                        Log.other("饿了么", "[幸运星] 点击[$title] 需${total}次/已${done}次/补${need}次")
                        for (step in 1..need) {
                            val triggerRes = LuckyStarRpcCall.reportTrigger(
                                cookie = cookie,
                                missionDefId = missionDefId,
                                missionXId = missionXId
                            )
                            if (!triggerRes.isSuccess) {
                                val rawResp = triggerRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: triggerRes.errorMsg
                                val errDetail = "[幸运星] ❌ 触发[$title]失败 ($step/$need): ${triggerRes.errorCode}::${triggerRes.errorMsg}\n完整响应:\n$rawResp"
                                Log.error(TAG, errDetail)
                                Log.other("饿了么", errDetail)
                                break
                            }
                            Thread.sleep(RandomUtil.nextGaussianLong(1800, 2600))
                        }
                        if (need > 0) {
                            Log.other("饿了么", "[幸运星] 完成[$title]")
                            finishedCount++
                            hasRewardable = true
                        }
                    }
                }
            }

            // 若有推进成功的任务或有待领奖任务，才进入第二轮领奖；无任务或已全部完成时直接跳过第二轮
            if (taskArray != null && (finishedCount > 0 || hasRewardable)) {
                // ==== 第二轮: 刷新任务进度并领取奖励 ====
                Thread.sleep(RandomUtil.nextGaussianLong(1500, 2500))
                val refreshedResp = LuckyStarRpcCall.queryTaskList(cookie)
                val refreshedArray = extractTaskArray(refreshedResp) ?: taskArray

                for (i in 0 until refreshedArray.length()) {
                    val task = refreshedArray.optJSONObject(i) ?: continue
                    val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                    val title = taskTitle(task)

                    if (missionDefId <= 0) continue

                    // 对齐幸运星.py：ORDER（下单类）与 P2P（助力拉新类）属于非自动化任务，绝不领奖、不报错，直接跳过
                    val actionConfig = task.optJSONObject("actionConfig")
                    val actionType = actionConfig?.optString("actionType").orEmpty()
                    if (actionType == "ORDER" || actionType == "P2P") {
                        continue
                    }

                    // UI 风控跳过名单与用户自选黑名单过滤（静默跳过）
                    val riskTasks: Set<String> = ElemeTask.getElemeRiskTasks().value ?: emptySet()
                    val inRiskTaskSet = riskTasks.contains(missionDefId.toString())
                    val inGlobalBlacklist = TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(missionDefId.toString())
                    if (inRiskTaskSet || inGlobalBlacklist) {
                        continue
                    }

                    // 支持单节点与多阶段任务批量领奖
                    val stages = LuckyStarRpcCall.getRewardStages(task)
                    if (stages.isNotEmpty()) {
                        for (stage in stages) {
                            val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task, stage)
                            if (prizeRes.isSuccess) {
                                val rewardDesc = extractPrizeDesc(prizeRes.data ?: prizeRes.rawJson)
                                val descText = if (rewardDesc.isNotEmpty()) " $rewardDesc" else ""
                                Log.other("饿了么", "[幸运星] [$title]$descText")
                                rewardedCount++
                            } else {
                                val rawResp = prizeRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: prizeRes.errorMsg
                                val errDetail = "[幸运星] ❌ 领奖[$title]失败: ${prizeRes.errorCode}::${prizeRes.errorMsg}\n完整响应:\n$rawResp"
                                Log.error(TAG, errDetail)
                                Log.other("饿了么", errDetail)
                            }
                            Thread.sleep(RandomUtil.nextGaussianLong(1200, 2200))
                        }
                    } else if (LuckyStarRpcCall.isTaskRewardable(task)) {
                        val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task, null)
                        if (prizeRes.isSuccess) {
                            val rewardDesc = extractPrizeDesc(prizeRes.data ?: prizeRes.rawJson)
                            val descText = if (rewardDesc.isNotEmpty()) " $rewardDesc" else ""
                            Log.other("饿了么", "[幸运星] [$title]$descText")
                            rewardedCount++
                        } else {
                            val rawResp = prizeRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: prizeRes.errorMsg
                            val errDetail = "[幸运星] ❌ 领奖[$title]失败: ${prizeRes.errorCode}::${prizeRes.errorMsg}\n完整响应:\n$rawResp"
                            Log.error(TAG, errDetail)
                            Log.other("饿了么", errDetail)
                        }
                        Thread.sleep(RandomUtil.nextGaussianLong(1200, 2200))
                    }
                }
            }

            val finalProperty = LuckyStarRpcCall.queryProperty(cookie)
            val finalStars = extractStarAmount(finalProperty)
            val delta = finalStars - initialStars

            // 3. 兑换商城查询与自动兑换（严格对齐 幸运星.py mall/wallet 规范与白名单安全机制）
            try {
                val mallResp = LuckyStarRpcCall.queryMallList(cookie)
                val mallData = mallResp?.optJSONObject("data") ?: mallResp
                val items = mallData?.optJSONArray("list")
                if (items != null && items.length() > 0) {
                    fansirsqi.xposed.sesame.entity.ElemeMallEntity.updateFromMallResponse(mallResp)

                    // 4. 自动兑换逻辑（严格仅兑换用户勾选商品，星数不足绝不请求，单次最多兑 1 件保护资产）
                    if (ElemeTask.getElemeAutoExchange().value) {
                        val selectedIds: Set<String> = ElemeTask.getElemeExchangeItems().value ?: emptySet()
                        if (selectedIds.isNotEmpty()) {
                            val curStars = finalStars
                            for (k in 0 until items.length()) {
                                val it = items.optJSONObject(k) ?: continue
                                val exchangeId = it.optString("exchangeId").ifEmpty {
                                    it.optLong("exchangeId").toString()
                                }
                                val ex = it.optJSONObject("exchangeInfo")
                                val cost = ex?.optInt("consumeAmount") ?: 0
                                val status = ex?.optString("exchangeStatus").orEmpty()
                                val name = it.optJSONObject("materialInfo")?.optString("title")
                                    ?: it.optJSONObject("bizExtInfo")?.optString("exchangeIntroduction").orEmpty()

                                if (selectedIds.contains(exchangeId) &&
                                    (status in listOf("", "INIT", "EXCHANGE_ABLE", "CAN_EXCHANGE", "ONLINE")) &&
                                    cost in 1..curStars
                                ) {
                                    val cached = fansirsqi.xposed.sesame.entity.ElemeMallEntity.getItem(exchangeId)
                                    val actId = it.optString("exchangeActId").ifEmpty { it.optString("actId") }.ifEmpty { cached?.actId.orEmpty() }
                                    val colId = it.optString("exchangeCollectionId").ifEmpty { it.optString("collectionId") }.ifEmpty { cached?.exchangeCollectionId.orEmpty() }
                                    val actCode = it.optString("actCode").ifEmpty { cached?.actCode.orEmpty() }
                                    Thread.sleep(RandomUtil.nextGaussianLong(1000, 2000))
                                    val exRes = LuckyStarRpcCall.mallExchange(cookie, actId, colId, exchangeId, actCode)
                                    if (exRes.isSuccess) {
                                        Log.other("饿了么", "[幸运星] 🎉 成功兑换商品: $name (-$cost 星)")
                                        Notify.sendPrizeNotification("🎉 饿了么商品兑换成功", "成功使用 $cost 幸运星兑换【$name】！请及时使用。")
                                        break
                                    } else {
                                        val rawResp = exRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: exRes.errorMsg
                                        val errDetail = "[幸运星] ❌ 兑换[$name]失败: ${exRes.errorCode}::${exRes.errorMsg}\n完整响应:\n$rawResp"
                                        Log.error(TAG, errDetail)
                                        Log.other("饿了么", errDetail)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}

            if (rewardedCount > 0 || delta > 0) {
                val deltaText = if (delta > 0) " (+$delta)" else ""
                Log.other("饿了么", "[幸运星] 🌟 执行完毕: 完成 $finishedCount 个任务, 领奖 $rewardedCount 次, 最终星数: $finalStars$deltaText")
            }
        } catch (t: Throwable) {
            val errMsg = "[幸运星] ❌ 执行饿了么幸运星任务异常: ${t.message}"
            Log.error(TAG, errMsg)
            Log.printStackTrace(TAG, errMsg, t)
            Log.other("饿了么", errMsg)
        } finally {
            lock.unlock()
        }
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

    private fun extractLotteryPrize(data: JSONObject?): String {
        if (data == null) return "奖励"
        val items = mutableListOf<JSONObject>()
        val arr = data.optJSONArray("rlist") ?: data.optJSONArray("prizeList") ?: data.optJSONArray("rewards")
        if (arr != null && arr.length() > 0) {
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                items.add(it)
            }
        } else {
            items.add(data)
        }
        val out = mutableListOf<String>()
        for (it in items) {
            val dis = it.optJSONObject("discountInfo")
            val info = it.optJSONObject("materialInfo") ?: it.optJSONObject("rightMaterialInfoDTO")
            val amount = dis?.optString("reductionYuan")?.ifEmpty { null }
                ?: dis?.optString("reduction")?.ifEmpty { null }
                ?: dis?.optString("amount")?.ifEmpty { null }
                ?: it.optString("prizeValue").ifEmpty { null }
            val btype = dis?.optString("benefitType")?.ifEmpty { null } ?: it.optString("prizeType")
            var name = info?.optString("title")
                ?.ifEmpty { it.optString("prizeName") }
                ?.ifEmpty { it.optString("name") }
                ?.ifEmpty { it.optString("title") }.orEmpty()
            if (name.isEmpty()) {
                name = when (btype) {
                    "VIRTUAL_INTEGRAL", "STAR" -> "幸运星"
                    "COUPON" -> "优惠券"
                    "RED_PACKET" -> "红包"
                    else -> if (btype.isNotEmpty()) btype else "奖励"
                }
            }
            if (btype == "VIRTUAL_INTEGRAL") {
                val yuan = dis?.optString("reductionYuan").orEmpty()
                val yuanText = if (yuan.isNotEmpty()) "（${yuan}元）" else ""
                out.add("+${amount ?: ""} 幸运星$yuanText")
            } else if (name.isNotEmpty() && !amount.isNullOrEmpty() && !name.contains(amount)) {
                out.add("$name +$amount")
            } else if (name.isNotEmpty()) {
                out.add(name)
            }
        }
        return if (out.isNotEmpty()) out.joinToString("、") else "奖励"
    }

    private fun extractSignInReward(data: JSONObject?): String {
        if (data == null) return ""
        val rewards = data.optJSONArray("rewards") ?: return ""
        var total = 0
        var ptype = "幸运星"
        for (i in 0 until rewards.length()) {
            val r = rewards.optJSONObject(i) ?: continue
            val v = r.optDouble("prizeValue", r.optDouble("equityValue", 0.0)).toInt()
            total += v
            if (r.optString("prizeType") != "STAR") {
                ptype = r.optString("prizeType", "奖励")
            }
        }
        return if (total > 0) "+$total $ptype" else ""
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

    private fun extractPrizeDesc(resp: JSONObject?): String {
        if (resp == null) return ""
        val data = resp.optJSONObject("data") ?: resp
        val rlist = data.optJSONArray("rlist")
            ?: data.optJSONArray("rewards")
            ?: data.optJSONArray("rewardList")
            ?: data.optJSONArray("prizeList")
        if (rlist != null && rlist.length() > 0) {
            val out = mutableListOf<String>()
            for (i in 0 until rlist.length()) {
                val it = rlist.optJSONObject(i) ?: continue
                var title = it.optJSONObject("materialInfo")?.optString("title")
                    ?: it.optJSONObject("rightMaterialInfoDTO")?.optString("title")
                    ?: it.optJSONObject("prizeMaterialInfo")?.optString("title")
                    ?: it.optJSONObject("uppPrizeResult")?.optJSONObject("materialInfo")?.optString("title")
                    ?: it.optString("name")
                val ptype = it.optString("prizeType")
                if (title.isNullOrEmpty()) {
                    title = when (ptype) {
                        "STAR" -> "幸运星"
                        "INTEGRAL_PROPERTY" -> "积分"
                        "COUPON" -> "优惠券"
                        "RED_PACKET" -> "红包"
                        else -> if (ptype.isNotEmpty()) ptype else "奖励"
                    }
                }
                val value = it.optString("prizeValue").ifEmpty {
                    it.optString("equityValue").ifEmpty {
                        it.optString("value").ifEmpty {
                            it.optJSONObject("uppPrizeResult")?.optJSONObject("discountInfo")?.optString("amount").orEmpty()
                        }
                    }
                }
                if (value.isNotEmpty() && !title.contains(value)) {
                    out.add("$title +$value")
                } else if (title.isNotEmpty()) {
                    out.add(title)
                }
            }
            if (out.isNotEmpty()) {
                return out.joinToString("、")
            }
        }
        val ret = resp.optJSONArray("ret")?.optString(0).orEmpty()
        return if (ret.startsWith("SUCCESS")) "成功" else ""
    }

    private fun extractStarAmount(root: JSONObject?): Int {
        if (root == null) return 0
        val data = root.optJSONObject("data") ?: root
        val prop = data.optJSONObject("property") ?: data.optJSONObject("data")?.optJSONObject("property")
        val starObj = prop?.optJSONObject("data")?.optJSONObject("STAR")
        if (starObj != null) {
            return starObj.optInt("amount", 0)
        }
        return prop?.optInt("amount", 0)
            ?: data.optInt("currentProperty", 0)
    }
}
