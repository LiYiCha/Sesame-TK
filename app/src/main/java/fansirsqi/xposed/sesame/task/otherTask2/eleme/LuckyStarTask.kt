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
 * 饿了么幸运星日常自动化任务
 * 支持自动签到、做任务（15s浏览、多步计数3次）、领奖励、星数统计
 * 单例设计与并发互斥锁保护，防止多线程冲突
 */
object LuckyStarTask {
    private const val TAG = "LuckyStarTask"
    private const val FLAG_SIGNIN = FreeOrderTask.FLAG_SIGNIN
    private const val FLAG_LOTTERY_DONE = FreeOrderTask.FLAG_LOTTERY_DONE
    private const val FLAG_ORDER_FREE = FreeOrderTask.FLAG_ORDER_FREE
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
            // 任务开始仅输出账号信息与设备 UA；幸运星数量统一在执行结束后输出。
            // 账号信息不打印昵称/姓名（脱敏 UID 即可），避免日志泄露个人信息
            val alipayUidRaw = fansirsqi.xposed.sesame.util.maps.UserMap.currentUid.orEmpty()
            val alipayUidLog = if (alipayUidRaw.length > 8) alipayUidRaw.take(4) + "***" + alipayUidRaw.takeLast(4) else alipayUidRaw.ifEmpty { "未知" }
            Log.other("饿了么", "[幸运星] 账号: 支付宝 $alipayUidLog | ${ElemeAuth.getElemeAccountLine()}")
            Log.other("饿了么", "[幸运星] 设备UA(${ElemeMtopUtil.getUaSourceLabel()}): ${ElemeMtopUtil.getActiveProfile().httpUa()}")
            val homepageResp = LuckyStarRpcCall.getHomepageData(cookie)
            val hpData = homepageResp?.optJSONObject("data") ?: homepageResp
            val initialStars = extractStarAmount(hpData)

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
                        // 首页已明确返回今日已签到，直接标记，无需再请求
                        Status.setFlagToday(FLAG_SIGNIN)
                    } else if (signinData != null) {
                        // copyId 缺失时不阻断签到（对齐 幸运星.py：copyId 仅在有值时作为入参附加），
                        // 也绝不据此置位标记——否则首页数据抖动一次就会把当天签到永久标记完成
                        val copyId = todayDto?.optJSONObject("extInfo")?.optString("copyId").orEmpty()
                            .ifEmpty { signinData.optJSONObject("extInfo")?.optString("copyId").orEmpty() }
                        val strategy = signinData.optString("signInRewardStrategy")
                        val awards = todayDto?.optJSONObject("extInfo")?.optJSONArray("stageAward")
                        val award = awards?.optJSONObject(0)
                        val signRes = LuckyStarRpcCall.signIn(cookie, copyId, strategy, award)
                        when {
                            signRes.isSuccess -> {
                                Status.setFlagToday(FLAG_SIGNIN)
                                val rewText = extractSignInReward(signRes.data)
                                val rewMsg = if (rewText.isNotEmpty()) ": $rewText" else ""
                                Log.other("饿了么", "[幸运星] 签到打卡$rewMsg")
                            }
                            signRes.errorCode.contains("REPEAT") || signRes.errorCode.contains("ALREADY") ||
                                signRes.errorMsg.contains("已签到") -> Status.setFlagToday(FLAG_SIGNIN)
                            // 失败不置位：签到失败属可重试状态，下一轮触发会继续尝试；但必须留下完整响应便于定位
                            else -> {
                                val rawResp = signRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: signRes.errorMsg
                                val detail = "[幸运星] ❌ 签到失败: ${signRes.errorCode}::${signRes.errorMsg}\n完整响应:\n$rawResp"
                                Log.error(TAG, detail)
                                Log.other("饿了么", detail)
                            }
                        }
                        Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
                    }
                }
            } catch (e: Throwable) {
                Log.error(TAG, "幸运星签到异常: ${e.message}")
            }

            // 3.2 夺宝/抽奖（首页 INTERACT_CENTER_LOTTERY 组件）
            // 夺宝属每日单次任务：当日抽完即置位标记整天跳过，避免周期性触发反复请求接口
            try {
                if (!Status.hasFlagToday(FLAG_LOTTERY_DONE)) {
                    val resourceCpns = hpData?.optJSONObject("resource")?.optJSONObject("data")
                    val lotteries = resourceCpns?.optJSONArray("INTERACT_CENTER_LOTTERY")
                    if (lotteries != null && lotteries.length() > 0) {
                        var drawInterrupted = false
                        for (k in 0 until lotteries.length()) {
                            if (drawInterrupted) break
                            val lot = lotteries.optJSONObject(k) ?: continue
                            // 对齐 幸运星.py：actId 或 componentId 缺失的组件直接跳过，不发无效请求
                            if (lot.optString("actId").isEmpty() || lot.optString("componentId").isEmpty()) continue
                            var drawCount = 0
                            val maxDraws = 1 // 对齐 幸运星.py：ELM_DRAW_TIMES 默认 1，每个组件抽一次，失败即停
                            while (drawCount < maxDraws) {
                                drawCount++
                                val drawRes = LuckyStarRpcCall.drawLottery(cookie, lot)
                                if (drawRes.isSuccess) {
                                    val prize = ElemeMtopUtil.extractPrizeDesc(drawRes.data)
                                    if (prize.isNotEmpty()) {
                                        Log.other("饿了么", "[幸运星] 🎁 夺宝抽奖: $prize")
                                        if (prize.contains("免单")) {
                                            Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在夺宝抽奖中成功命中【$prize】！请及时使用。")
                                        }
                                    } else {
                                        // 服务端未返回奖励描述时直接打印完整响应，便于核实实际抽奖结果
                                        val rawResp = drawRes.rawJson?.toString() ?: drawRes.data?.toString() ?: "无响应内容"
                                        Log.other("饿了么", "[幸运星] 🎁 夺宝抽奖: 奖励描述缺失，完整响应:\n$rawResp")
                                    }
                                    Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                } else {
                                    // 判定语义对齐 幸运星.py draw_lottery：
                                    // 次数类 NO_ENOUGH_REMAIN|CHANCE|已抽完|次数|日剩；未中奖类 未中奖|NO_DECISION_STRATEGY|NOT_WINNING
                                    val msg = "${drawRes.errorCode} ${drawRes.errorMsg}"
                                    val detail = msg.substringAfterLast("::").take(20)
                                    if (Regex("NO_ENOUGH_REMAIN|CHANCE|已抽完|次数|日剩", RegexOption.IGNORE_CASE).containsMatchIn(msg)) {
                                        Log.other("饿了么", "[幸运星] ⏸️ 夺宝次数已用完(${detail.ifEmpty { "日剩余机会不足" }})")
                                        Status.setFlagToday(FLAG_LOTTERY_DONE)
                                        drawInterrupted = true
                                    } else if (Regex("未中奖|NO_DECISION_STRATEGY|NOT_WINNING", RegexOption.IGNORE_CASE).containsMatchIn(msg)) {
                                        Log.other("饿了么", "[幸运星] 🎰 夺宝未中奖")
                                    } else {
                                        // 既非次数耗尽也非明确未中奖：属异常失败，输出完整响应便于定位
                                        val rawResp = drawRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: "无响应内容"
                                        val detail = "[幸运星] 🎰 夺宝未成功: ${drawRes.errorCode}::${drawRes.errorMsg}\n完整响应:\n$rawResp"
                                        Log.error(TAG, detail)
                                        Log.other("饿了么", detail)
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.error(TAG, "幸运星夺宝抽奖异常: ${e.message}")
            }

            // 3.3 下单免单抽奖组件（每日单次任务，当日已抽则跳过）
            try {
                if (!Status.hasFlagToday(FLAG_ORDER_FREE)) {
                    val orderFreeData = hpData?.optJSONObject("orderFreeDraw")
                    if (orderFreeData != null) {
                        val ofRes = LuckyStarRpcCall.drawOrderFree(cookie, orderFreeData)
                        if (ofRes.isSuccess) {
                            Status.setFlagToday(FLAG_ORDER_FREE)
                            val prize = ElemeMtopUtil.extractPrizeDesc(ofRes.data)
                            if (prize.isNotEmpty()) {
                                Log.other("饿了么", "[幸运星] 🎲 下单免单抽奖: $prize")
                                if (prize.contains("免单")) {
                                    Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在下单免单抽奖中成功命中【$prize】！请及时使用。")
                                }
                            } else {
                                // 服务端未返回奖励描述时直接打印完整响应，便于核实实际抽奖结果
                                val rawResp = ofRes.rawJson?.toString() ?: ofRes.data?.toString() ?: "无响应内容"
                                Log.other("饿了么", "[幸运星] 🎲 下单免单抽奖: 奖励描述缺失，完整响应:\n$rawResp")
                            }
                        } else {
                            // 失败不置位：无资格/时机未到属临时状态，下次触发仍会重试。
                            // 该接口失败频率高，完整响应写入 runtime 日志留痕，不刷屏 UI 日志
                            val rawResp = ofRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: ofRes.errorMsg
                            Log.runtime(TAG, "[幸运星] 下单免单抽奖未成功: ${ofRes.errorCode}::${ofRes.errorMsg}\n完整响应:\n$rawResp")
                        }
                        Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
                    }
                }
            } catch (e: Throwable) {
                Log.error(TAG, "幸运星下单免单抽奖异常: ${e.message}")
            }

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

            val taskArray = LuckyStarRpcCall.extractTaskArray(taskListResp)
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
                    // 账号已被平台标记风控时立即中止第一轮，避免继续发请求加重标记
                    if (ElemeMtopUtil.accountRiskFlagged) break
                    val task = taskArray.optJSONObject(i) ?: continue
                    val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                    val missionXId = task.optString("missionXId")
                    val title = LuckyStarRpcCall.taskTitle(task)
                    val receiveStatus = task.optString("receiveStatus")
                    val rewardStatus = task.optString("rewardStatus")

                    if (missionDefId <= 0) continue

                    // 已领取的任务跳过
                    if (receiveStatus == "HAVERECEIVED" || rewardStatus == "SUCCESS" || rewardStatus == "RECEIVED") {
                        continue
                    }

                    // UI 风控名单与用户自选黑名单过滤（静默跳过，不打印日志）
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
                        // 停留时长对齐 幸运星.py：任务给出 pageStageTime 时取 max(3, 值)，缺失时随机 8~16 秒，
                        // 不使用标题关键字猜测，避免自造行为特征
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
                                val reason = "405::行为受限"
                                ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, "405", viewRes.errorMsg)
                                Log.other("饿了么", "[幸运星] 🛡️ 任务[$missionDefId - $title]因【$reason】已自动加入风控名单，后续执行将自动跳过")
                                Log.other("饿了么", "[幸运星] ⛔ 检测到接口行为受限 (405::行为受限)，为保护账号安全，停止今日后续浏览埋点")
                                break
                            }
                        }
                        Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                    } else if (actionType == "THIRD" && (sceneCode == "commercial_click" || sceneCode == "inter_change")) {
                        // 对齐幸运星.py _is_trigger：仅站内点击类任务可协议触发；
                        // 闲鱼看视频等外部结算任务（scenceCode=third）不调 trigger，只领已完成阶段
                        val total = actionValue?.optInt("finalStageCount")?.takeIf { it > 0 }
                            ?: task.optJSONObject("stage")?.optInt("targetCount")?.takeIf { it > 0 }
                            ?: 1 // 对齐 幸运星.py：仅取 finalStageCount，缺失默认 1，不按标题猜次数
                        val done = task.optJSONObject("stage")?.optInt("count") ?: 0
                        val need = (total - done).coerceAtLeast(0).coerceAtMost(3)
                        Log.other("饿了么", "[幸运星] 点击[$title] 需${total}次/已${done}次/补${need}次")
                        // 触发是否全部成功：失败（含被风控拉黑）不得计入任务完成
                        var triggerAllSuccess = need == 0
                        for (step in 1..need) {
                            val triggerRes = LuckyStarRpcCall.reportTrigger(
                                cookie = cookie,
                                missionDefId = missionDefId,
                                missionXId = missionXId,
                                // 对齐幸运星.py：使用任务自身的 missionCollectionId，避免场景不匹配被过滤
                                collectionId = task.optString("missionCollectionId").ifEmpty { LuckyStarRpcCall.COLLECTION_ID }
                            )
                            if (!triggerRes.isSuccess) {
                                val rawResp = triggerRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: triggerRes.errorMsg
                                val errDetail = "[幸运星] ❌ 触发[$title]失败 ($step/$need): ${triggerRes.errorCode}::${triggerRes.errorMsg}\n完整响应:\n$rawResp"
                                Log.error(TAG, errDetail)
                                Log.other("饿了么", errDetail)
                                if (triggerRes.errorCode == "417" || triggerRes.errorMsg.contains("417") || triggerRes.errorMsg.contains("作弊") ||
                                    triggerRes.errorCode == "405" || triggerRes.errorMsg.contains("行为受限")) {
                                    val reason = if (triggerRes.errorCode == "417" || triggerRes.errorMsg.contains("417")) "417::防作弊打点拦截" else "405::行为受限"
                                    ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                    TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, triggerRes.errorCode, triggerRes.errorMsg)
                                    Log.other("饿了么", "[幸运星] 🛡️ 任务[$missionDefId - $title]因【$reason】已自动加入风控名单，后续执行将自动跳过")
                                }
                                break
                            }
                            triggerAllSuccess = true
                            Thread.sleep(RandomUtil.nextGaussianLong(1800, 2600))
                        }
                        if (need > 0 && triggerAllSuccess) {
                            Log.other("饿了么", "[幸运星] 完成[$title]")
                            finishedCount++
                            hasRewardable = true
                        }
                    }
                }
            }

            // ==== 趣味话题答题：actId 不在任务列表返回中，仅在设置页【答题活动actId】填写后才执行一次 ====
            // 答题奖励在 quiz.award 内直接发放，不参与下方的任务领奖轮次，因此不计入 finishedCount
            if (!ElemeMtopUtil.accountRiskFlagged) {
                ElemeQuiz.run(cookie, "[幸运星]")
            }

            // 若有推进成功的任务或有待领奖任务，才进入第二轮领奖；无任务或已全部完成时直接跳过第二轮
            if (taskArray != null && (finishedCount > 0 || hasRewardable)) {
                // ==== 第二轮: 刷新任务进度并领取奖励 ====
                Thread.sleep(RandomUtil.nextGaussianLong(1500, 2500))
                // 与第一轮使用同一个 missionCollectionId（取自首页聚合），避免刷新查询换集合导致进度不一致
                val refreshedResp = LuckyStarRpcCall.queryTaskList(cookie, collectionId = missionCollectionId)
                val refreshedArray = LuckyStarRpcCall.extractTaskArray(refreshedResp) ?: taskArray

                for (i in 0 until refreshedArray.length()) {
                    // 账号级风控命中后不再继续领奖
                    if (ElemeMtopUtil.accountRiskFlagged) break
                    val task = refreshedArray.optJSONObject(i) ?: continue
                    val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                    val title = LuckyStarRpcCall.taskTitle(task)

                    if (missionDefId <= 0) continue

                    // 对齐幸运星.py：ORDER（下单类）与 P2P（助力拉新类）属于非自动化任务，绝不领奖、不报错，直接跳过
                    val actionConfig = task.optJSONObject("actionConfig")
                    val actionType = actionConfig?.optString("actionType").orEmpty()
                    if (actionType == "ORDER" || actionType == "P2P") {
                        continue
                    }

                    // UI 风控名单与用户自选黑名单过滤（静默跳过，不打印日志）
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
                                val rewardDesc = ElemeMtopUtil.extractPrizeDesc(prizeRes.data ?: prizeRes.rawJson)
                                val descText = if (rewardDesc.isNotEmpty()) " $rewardDesc" else ""
                                Log.other("饿了么", "[幸运星] [$title]$descText")
                                rewardedCount++
                            } else {
                                val rawResp = prizeRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: prizeRes.errorMsg
                                val errDetail = "[幸运星] ❌ 领奖[$title]失败: ${prizeRes.errorCode}::${prizeRes.errorMsg}\n完整响应:\n$rawResp"
                                Log.error(TAG, errDetail)
                                Log.other("饿了么", errDetail)
                                if (prizeRes.errorCode == "405" || prizeRes.errorCode == "417" || prizeRes.errorMsg.contains("受限") || prizeRes.errorMsg.contains("作弊")) {
                                    val reason = if (prizeRes.errorCode == "417" || prizeRes.errorMsg.contains("417")) "417::防作弊领奖拦截" else "405::行为受限"
                                    ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                    TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, prizeRes.errorCode, prizeRes.errorMsg)
                                    Log.other("饿了么", "[幸运星] 🛡️ 任务[$missionDefId - $title]因【$reason】已自动加入风控名单，后续执行将自动跳过")
                                } else if (ElemeRiskTaskEntity.isTaskClosedError(prizeRes.errorCode, prizeRes.errorMsg)) {
                                    // 累计奖励类任务有时会被平台关闭（阶段永远无法达成），自动加入风控名单停止重试
                                    ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, "任务已关闭或阶段无法达成")
                                    Log.other("饿了么", "[幸运星] 任务[$missionDefId - $title]已关闭或不可用，已自动加入风控名单，后续执行将自动跳过")
                                }
                            }
                            Thread.sleep(RandomUtil.nextGaussianLong(1200, 2200))
                        }
                    } else if (LuckyStarRpcCall.isTaskRewardable(task)) {
                        val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task, null)
                        if (prizeRes.isSuccess) {
                            val rewardDesc = ElemeMtopUtil.extractPrizeDesc(prizeRes.data ?: prizeRes.rawJson)
                            val descText = if (rewardDesc.isNotEmpty()) " $rewardDesc" else ""
                            Log.other("饿了么", "[幸运星] [$title]$descText")
                            rewardedCount++
                        } else {
                            val rawResp = prizeRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: prizeRes.errorMsg
                            val errDetail = "[幸运星] ❌ 领奖[$title]失败: ${prizeRes.errorCode}::${prizeRes.errorMsg}\n完整响应:\n$rawResp"
                            Log.error(TAG, errDetail)
                            Log.other("饿了么", errDetail)
                                if (prizeRes.errorCode == "405" || prizeRes.errorCode == "417" || prizeRes.errorMsg.contains("受限") || prizeRes.errorMsg.contains("作弊")) {
                                    val reason = if (prizeRes.errorCode == "417" || prizeRes.errorMsg.contains("417")) "417::防作弊领奖拦截" else "405::行为受限"
                                    ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                    TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, prizeRes.errorCode, prizeRes.errorMsg)
                                    Log.other("饿了么", "[幸运星] 🛡️ 任务[$missionDefId - $title]因【$reason】已自动加入风控名单，后续执行将自动跳过")
                                } else if (ElemeRiskTaskEntity.isTaskClosedError(prizeRes.errorCode, prizeRes.errorMsg)) {
                                    // 累计奖励类任务有时会被平台关闭（阶段永远无法达成），自动加入风控名单停止重试
                                    ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, "任务已关闭或阶段无法达成")
                                    Log.other("饿了么", "[幸运星] 任务[$missionDefId - $title]已关闭或不可用，已自动加入风控名单，后续执行将自动跳过")
                                }
                            }
                            Thread.sleep(RandomUtil.nextGaussianLong(1200, 2200))
                        }
                    }
                }

            val finalProperty = LuckyStarRpcCall.queryProperty(cookie)
            val finalStars = extractStarAmount(finalProperty)
            val delta = finalStars - initialStars

            // 今日收益统计（对齐 幸运星.py wallet()：明细中 detailType=GRANT 且日期为今天的 amount 合计）
            val todayGain = runCatching {
                val detailResp = LuckyStarRpcCall.queryPropertyDetail(cookie) ?: return@runCatching null
                val d = detailResp.optJSONObject("data") ?: detailResp
                val list = d.optJSONArray("list") ?: return@runCatching null
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(java.util.Date())
                var got = 0
                for (i in 0 until list.length()) {
                    val it = list.optJSONObject(i) ?: continue
                    if (!it.optString("gmtCreateStr").startsWith(today)) continue
                    if (it.optString("detailType") != "GRANT") continue
                    got += it.optString("amount").toDoubleOrNull()?.toInt() ?: 0
                }
                got
            }.getOrNull()

            // 3. 兑换商城查询（每日拉取最新商品目录并缓存；实际兑换由统一秒杀框架 ElemeMallEX 处理）
            try {
                val mallResp = LuckyStarRpcCall.queryMallList(cookie)
                val mallData = mallResp?.optJSONObject("data") ?: mallResp
                val items = mallData?.optJSONArray("list")
                if (items != null && items.length() > 0) {
                    fansirsqi.xposed.sesame.entity.ElemeMallEntity.updateFromMallResponse(mallResp)
                    // 每日首次运行拉取成功后记录快照（刷新时间+实时星数），供商城弹窗显示「刷新时间」与星数，
                    // 避免打开弹窗总是显示「未获取」
                    fansirsqi.xposed.sesame.entity.ElemeMallEntity.recordFetchSnapshot(
                        System.currentTimeMillis(), finalStars
                    )

                    // 自动兑换已迁移到统一秒杀框架 ElemeMallEX（整点前200ms唤醒、可配置开抢整点），
                    // 此处只负责每日拉取并缓存商品目录，不再直接兑换，避免两处重复扣星
                } else {
                    // 拉取失败不能静默：否则商城弹窗一直显示旧缓存，用户无从判断是接口异常还是无商品
                    val err = ElemeMtopUtil.lastError ?: "接口未返回商品列表"
                    val raw = ElemeMtopUtil.lastResponse ?: "无原始响应"
                    val detail = "[幸运星] ❌ 拉取兑换商城失败: $err\n完整响应:\n$raw"
                    Log.error(TAG, detail)
                    Log.other("饿了么", detail)
                }
            } catch (t: Throwable) {
                Log.error(TAG, "拉取兑换商城异常: ${t.message}")
            }

            // 运行结束后统一输出最终幸运星数量（无论有无收益）
            runCatching {
                val deltaText = if (delta > 0) " (+$delta)" else if (delta < 0) " ($delta)" else ""
                val gainText = if (todayGain != null) ", 今日收益: +$todayGain" else ""
                Log.other("饿了么", "[幸运星] 🌟 执行完毕: 完成 $finishedCount 个任务, 领奖 $rewardedCount 次, 最终幸运星: $finalStars$deltaText$gainText")
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
