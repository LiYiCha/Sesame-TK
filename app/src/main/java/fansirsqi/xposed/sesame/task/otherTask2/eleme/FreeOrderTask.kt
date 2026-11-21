package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.ElemeRiskTaskEntity
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TaskBlacklist
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock

/**
 * 饿了么天天免单（ETMD）自动化任务
 *
 * 业务协议严格对齐 tb2.log 抓包与真实小程序交互闭环：
 * 1. 前置生命周期初始化：checkLogin -> queryEtmdHomepage (组件外显) -> querySubscribeRemind (订阅提醒)
 * 2. 连续签到攒免单打卡：querysigninfo 获取 copyId -> recordsignin (v1.1) 打卡获得免单抽奖机会 (+1 机会)
 * 3. 免单主会场开奖：queryEtmdHomepage (modules=["mainPage"]) -> drawEtmdIcon 消耗机会开奖 (命中免单发系统通知)
 * 4. 推进免单专属任务：querytask (Collection 2602, ETMD_CHANNEL) 推进落地页/浏览/店铺/打点任务 -> receivePrize 领奖 (+1 机会)
 * 5. 任务后闭环抽奖：将做任务新获得的抽奖机会全部消耗完毕，绝不浪费
 * 6. 下单赢免单抽奖兼容：orderFreeDraw 每日抽奖
 */
object FreeOrderTask {
    private const val TAG = "FreeOrderTask"

    /** 幸运星日常任务兼容标记 */
    const val FLAG_SIGNIN = "Eleme_SignIn_Done"
    const val FLAG_LOTTERY_DONE = "Eleme_LuckyStar_Lottery_Done"

    /** 天天免单签到打卡（攒抽奖机会）：每日单次 */
    const val FLAG_ETMD_SIGNIN = "Eleme_Etmd_SignIn_Done"
    /** 下单免单抽奖：当日抽中/抽成功一次后置位 */
    const val FLAG_ORDER_FREE = "Eleme_OrderFree_Done"

    private val lock = ReentrantLock()

    fun handle() {
        if (!ElemeTask.elemeFreeOrder.value) {
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

            // 0. 前置环境初始化（严格对齐 tb2.log Line 2246~2279 真实生命周期）
            LuckyStarRpcCall.checkLoginResult(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(300, 600))
            LuckyStarRpcCall.queryEtmdHomepage(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(300, 600))
            LuckyStarRpcCall.querySubscribeRemind(cookie, sceneCode = "ETMD", bizScene = "ETMD", version = "1.10.27")
            Thread.sleep(RandomUtil.nextGaussianLong(300, 600))

            // 1. 连续签到攒免单打卡（签到打卡获得免单抽奖机会）
            if (!Status.hasFlagToday(FLAG_ETMD_SIGNIN)) {
                try {
                    val signInfoResp = LuckyStarRpcCall.queryEtmdSignInfo(cookie)
                    val signData = signInfoResp?.optJSONObject("data")?.optJSONObject("data")
                    val signed = signData?.optBoolean("signIn", false) ?: false
                    val extInfo = signData?.optJSONObject("extInfo")
                    val copyId = extInfo?.optString("copyId").orEmpty()

                    if (signed) {
                        Status.setFlagToday(FLAG_ETMD_SIGNIN)
                    } else if (copyId.isNotEmpty()) {
                        Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                        val signRes = LuckyStarRpcCall.recordEtmdSignIn(cookie, copyId)
                        if (signRes.isSuccess) {
                            Status.setFlagToday(FLAG_ETMD_SIGNIN)
                            val ext = signRes.data?.optJSONObject("ext")
                            val continuous = ext?.optString("continuousSignInDays") ?: "1"
                            val prizeValue = signRes.data?.optJSONArray("stageReward")?.optJSONObject(0)?.optString("prizeValue") ?: "1"
                            Log.other("饿了么", "[抽免单] 📅 连续签到打卡: +${prizeValue} 抽奖机会 (已连签${continuous}天)")
                        } else if (signRes.errorCode.contains("REPEAT") || signRes.errorCode.contains("ALREADY") ||
                            signRes.errorMsg.contains("已签到")
                        ) {
                            Status.setFlagToday(FLAG_ETMD_SIGNIN)
                        } else {
                            Log.other("饿了么", "[抽免单] 签到打卡返回: ${signRes.errorCode}::${signRes.errorMsg}")
                        }
                    }
                } catch (e: Throwable) {
                    Log.error(TAG, "免单签到打卡异常: ${e.message}")
                }
            }

            // 2. 进入免单主会场拉取抽奖配置，并消耗当前已有抽奖机会
            var currentUnionParams = LuckyStarRpcCall.ETMD_DEFAULT_UNION_PARAMS
            var availableDraws = 0
            try {
                Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                val mainPageResp = LuckyStarRpcCall.queryEtmdHomepage(cookie, modules = "[\"mainPage\"]")
                val mainPageData = mainPageResp?.optJSONObject("data")?.optJSONObject("data")
                    ?.optJSONObject("mainPage")?.optJSONObject("data")
                if (mainPageData != null) {
                    val popup = mainPageData.optJSONObject("popupShareWinV2")
                    availableDraws = popup?.optJSONObject("freeCardInfo")?.optInt("count") ?: 0
                    val union = mainPageData.optString("unionParams")
                    if (union.isNotEmpty()) {
                        currentUnionParams = union
                    }
                }

                if (availableDraws > 0) {
                    Log.other("饿了么", "[抽免单] 当前可用抽免单机会: ${availableDraws} 次，开始开奖...")
                    availableDraws = runDrawLottery(cookie, currentUnionParams, availableDraws)
                }
            } catch (e: Throwable) {
                Log.error(TAG, "主会场抽奖异常: ${e.message}")
            }

            // 3. 推进免单专属任务（做任务获得抽奖机会，Collection 2602, ETMD_CHANNEL）
            var earnedChances = 0
            try {
                Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                val taskListResp = LuckyStarRpcCall.queryTaskList(
                    cookie = cookie,
                    collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID,
                    bizScene = LuckyStarRpcCall.ETMD_CHANNEL_SCENE
                )
                val taskArray = LuckyStarRpcCall.extractTaskArray(taskListResp)
                if (taskArray != null && taskArray.length() > 0) {
                    var blocked = false

                    for (i in 0 until taskArray.length()) {
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
                        if (receiveStatus == "HAVERECEIVED" || rewardStatus == "SUCCESS" || rewardStatus == "RECEIVED") {
                            continue
                        }
                        if (actionType == "ORDER" || actionType == "P2P") {
                            continue
                        }

                        val riskTasks: Set<String> = ElemeTask.getElemeRiskTasks().value ?: emptySet()
                        if (riskTasks.contains(missionDefId.toString()) ||
                            TaskBlacklist.isTaskInBlacklist(title) ||
                            TaskBlacklist.isTaskInBlacklist(missionDefId.toString())
                        ) {
                            continue
                        }

                        // 任务已处于可领奖状态
                        if (LuckyStarRpcCall.isTaskRewardable(task)) {
                            val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task, null, LuckyStarRpcCall.ETMD_CHANNEL_SCENE)
                            if (prizeRes.isSuccess) {
                                Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                earnedChances++
                            } else {
                                if (handlePrizeFailure(missionDefId, title, prizeRes)) blocked = true
                            }
                            continue
                        }

                        val actionValue = actionConfig?.optJSONObject("actionValue")
                        val pageSpm = actionValue?.optString("pageSpm").orEmpty()
                        val sceneCode = actionValue?.optString("scenceCode").orEmpty()
                        val alipayLink = task.optJSONObject("ext")?.optJSONObject("extValue")?.optString("alipayActionLink").orEmpty()

                        val isShopClick = (actionType == "THIRD" && sceneCode == "commercial_click") ||
                                (title.contains("店铺") && (title.contains("点击") || title.contains("逛")))

                        if (isShopClick) {
                            val o2iAction = if (alipayLink.contains("o2i_action=")) {
                                Regex("""o2i_action=([^&]+)""").find(alipayLink)?.groupValues?.get(1) ?: "GY_TASK_$missionDefId"
                            } else "GY_TASK_$missionDefId"

                            if (alipayLink.isNotEmpty()) {
                                LuckyStarRpcCall.reportAlscPageRecall(
                                    cookie = cookie,
                                    pageUrl = alipayLink,
                                    missionDefId = missionDefId,
                                    collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID,
                                    bizScene = LuckyStarRpcCall.ETMD_CHANNEL_SCENE
                                )
                                Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                            }

                            val shops = LuckyStarRpcCall.DEFAULT_SHOPS
                            val total = actionValue?.optInt("finalStageCount")?.takeIf { it > 0 }
                                ?: task.optJSONObject("stage")?.optInt("targetCount")?.takeIf { it > 0 }
                                ?: 3
                            val done = task.optJSONObject("stage")?.optInt("count") ?: 0
                            val need = (total - done).coerceAtLeast(0).coerceAtMost(3)
                            var clickSuccess = need == 0

                            for (step in 1..need) {
                                val shopId = shops.getOrNull(step - 1) ?: shops.random()
                                val clickRes = LuckyStarRpcCall.reportAdShopClick(cookie, shopId, o2iAction)
                                if (clickRes.isSuccess) {
                                    LuckyStarRpcCall.reportStoreEnter(cookie, shopId)
                                    clickSuccess = true
                                } else {
                                    clickSuccess = false
                                    break
                                }
                                Thread.sleep(RandomUtil.nextGaussianLong(1500, 2500))
                            }

                            if (need > 0 && clickSuccess) {
                                Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(
                                    cookie = cookie,
                                    missionId = missionDefId,
                                    collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID,
                                    bizScene = LuckyStarRpcCall.ETMD_CHANNEL_SCENE
                                )
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                if (LuckyStarRpcCall.isTaskRewardable(refreshedTask)) {
                                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null, LuckyStarRpcCall.ETMD_CHANNEL_SCENE)
                                    if (prizeRes.isSuccess) {
                                        Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                        earnedChances++
                                    } else {
                                        if (handlePrizeFailure(missionDefId, title, prizeRes)) blocked = true
                                    }
                                }
                            }
                        } else if (actionType == "PAGEVIEW" || (pageSpm.isNotEmpty() || alipayLink.isNotEmpty())) {
                            if (blocked) continue
                            val hasModLanding = alipayLink.isNotEmpty() && alipayLink.contains("/wow/alsc/mod/")
                            val pageStageTime = actionValue?.optInt("pageStageTime")?.let { maxOf(3, it) }
                                ?: RandomUtil.nextInt(8, 16)

                            if (hasModLanding) {
                                Log.other("饿了么", "[抽免单] 🌐 访问落地页[$title] (模拟浏览 ${pageStageTime} 秒)")
                                LuckyStarRpcCall.reportAlscPageRecall(
                                    cookie = cookie,
                                    pageUrl = alipayLink,
                                    missionDefId = missionDefId,
                                    collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID,
                                    bizScene = LuckyStarRpcCall.ETMD_CHANNEL_SCENE
                                )
                                Thread.sleep(pageStageTime * 1000L + RandomUtil.nextGaussianLong(500, 1500))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(
                                    cookie = cookie,
                                    missionId = missionDefId,
                                    collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID,
                                    bizScene = LuckyStarRpcCall.ETMD_CHANNEL_SCENE
                                )
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                if (LuckyStarRpcCall.isTaskRewardable(refreshedTask)) {
                                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null, LuckyStarRpcCall.ETMD_CHANNEL_SCENE)
                                    if (prizeRes.isSuccess) {
                                        Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                        earnedChances++
                                    } else {
                                        if (handlePrizeFailure(missionDefId, title, prizeRes)) blocked = true
                                    }
                                }
                            } else if (pageSpm.isNotEmpty()) {
                                Thread.sleep(pageStageTime * 1000L + RandomUtil.nextGaussianLong(500, 1500))
                                val viewRes = LuckyStarRpcCall.reportPageView(
                                    cookie = cookie,
                                    missionDefId = missionDefId,
                                    pageSpm = pageSpm,
                                    instanceId = task.optString("instanceId").takeIf { it.isNotEmpty() },
                                    collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID,
                                    bizScene = LuckyStarRpcCall.ETMD_CHANNEL_SCENE
                                )
                                if (viewRes.isSuccess) {
                                    Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                    val refreshResp = LuckyStarRpcCall.queryTaskList(
                                        cookie = cookie,
                                        missionId = missionDefId,
                                        collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID,
                                        bizScene = LuckyStarRpcCall.ETMD_CHANNEL_SCENE
                                    )
                                    val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                    if (LuckyStarRpcCall.isTaskRewardable(refreshedTask)) {
                                        val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null, LuckyStarRpcCall.ETMD_CHANNEL_SCENE)
                                        if (prizeRes.isSuccess) {
                                            Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                            earnedChances++
                                        } else {
                                            if (handlePrizeFailure(missionDefId, title, prizeRes)) blocked = true
                                        }
                                    }
                                } else {
                                    val is405 = viewRes.errorCode == "405" || viewRes.errorMsg.contains("405") || viewRes.errorMsg.contains("行为受限")
                                    val rawResp = viewRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: viewRes.errorMsg
                                    Log.other("饿了么", "[抽免单] ❌ 浏览[$title]失败: ${viewRes.errorCode}::${viewRes.errorMsg}\n完整响应:\n$rawResp")
                                    if (is405) {
                                        val reason = "405::行为受限"
                                        ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, reason)
                                        TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, "405", viewRes.errorMsg)
                                        Log.other("饿了么", "[抽免单] 🛡️ 任务[$missionDefId - $title]因【$reason】已加入风控名单，停止浏览")
                                        blocked = true
                                    }
                                }
                            }
                            Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
                        } else if (actionType == "THIRD" && sceneCode == "inter_change") {
                            val triggerRes = LuckyStarRpcCall.reportTrigger(
                                cookie = cookie,
                                missionDefId = missionDefId,
                                missionXId = missionXId,
                                collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID
                            )
                            if (triggerRes.isSuccess) {
                                Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(
                                    cookie = cookie,
                                    missionId = missionDefId,
                                    collectionId = LuckyStarRpcCall.ETMD_COLLECTION_ID,
                                    bizScene = LuckyStarRpcCall.ETMD_CHANNEL_SCENE
                                )
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                if (LuckyStarRpcCall.isTaskRewardable(refreshedTask)) {
                                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null, LuckyStarRpcCall.ETMD_CHANNEL_SCENE)
                                    if (prizeRes.isSuccess) {
                                        Log.other("饿了么", "[抽免单] [$title]: +1 抽奖机会")
                                        earnedChances++
                                    } else {
                                        if (handlePrizeFailure(missionDefId, title, prizeRes)) blocked = true
                                    }
                                }
                            }
                            Thread.sleep(RandomUtil.nextGaussianLong(1000, 1800))
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.error(TAG, "免单专属任务推进异常: ${e.message}")
            }

            // 4. 任务推进完成后，消耗新赢取到的抽免单机会（闭环开奖）
            if (earnedChances > 0) {
                Log.other("饿了么", "[抽免单] 任务推进完成，新获得 ${earnedChances} 次抽免单机会，开始开奖...")
                runDrawLottery(cookie, currentUnionParams, earnedChances)
            }

            // 5. 下单赢免单抽奖（若有下单机会且当日未抽过）
            if (!Status.hasFlagToday(FLAG_ORDER_FREE)) {
                try {
                    val homepageResp = LuckyStarRpcCall.getHomepageData(cookie)
                    val dataObj = homepageResp?.optJSONObject("data") ?: homepageResp
                    val orderFreeData = dataObj?.optJSONObject("orderFreeDraw")
                    if (orderFreeData != null) {
                        Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
                        val ofRes = LuckyStarRpcCall.drawOrderFree(cookie, orderFreeData)
                        if (ofRes.isSuccess) {
                            Status.setFlagToday(FLAG_ORDER_FREE)
                            val prize = ElemeMtopUtil.extractPrizeDesc(ofRes.data ?: ofRes.rawJson)
                            if (prize.isNotEmpty()) {
                                Log.other("饿了么", "[抽免单] 🎲 下单免单抽奖: $prize")
                                if (prize.contains("免单")) {
                                    Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在下单免单抽奖中成功命中【$prize】！请及时使用。")
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.error(TAG, "下单免单抽奖异常: ${e.message}")
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

    /**
     * 循环消耗抽免单机会进行开奖
     */
    private fun runDrawLottery(cookie: String, unionParams: String, initialCount: Int): Int {
        var count = initialCount
        var maxDraws = 10
        while (count > 0 && maxDraws > 0) {
            maxDraws--
            Thread.sleep(RandomUtil.nextGaussianLong(1500, 2500))
            val drawRes = LuckyStarRpcCall.drawEtmdIcon(cookie, unionParams = unionParams)
            if (drawRes.isSuccess) {
                val dataObj = drawRes.data
                val floatIcon = dataObj?.optJSONObject("floatIcon")
                val typeEnum = floatIcon?.optString("typeEnum").orEmpty()
                val btnText = floatIcon?.optString("buttonText").orEmpty()
                val freeCard = dataObj?.optJSONObject("popupShareWinV2")?.optJSONObject("freeCardInfo")
                val remain = freeCard?.optInt("count") ?: (count - 1)
                count = remain

                val prizeDesc = ElemeMtopUtil.extractPrizeDesc(dataObj)
                if (prizeDesc.isNotEmpty() && !prizeDesc.contains("未中奖") && !prizeDesc.contains("NO_COUPON")) {
                    Log.other("饿了么", "[抽免单] 🎉 抽免单命中奖励: $prizeDesc (剩余: ${count}次)")
                    if (prizeDesc.contains("免单")) {
                        Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在天天抽免单中成功命中【$prizeDesc】！请及时使用。")
                    }
                } else {
                    val statusText = if (btnText.isNotEmpty()) btnText else "未中奖"
                    Log.other("饿了么", "[抽免单] 🎲 抽免单: 未中奖 ($statusText, 剩余: ${count}次)")
                }

                if (typeEnum == "NO_WIN_NO_CHANCE" || count <= 0) {
                    break
                }
            } else {
                val rawResp = drawRes.rawJson?.toString() ?: drawRes.errorMsg
                Log.other("饿了么", "[抽免单] 🎲 抽免单未成功: ${drawRes.errorCode}::${drawRes.errorMsg}\n完整响应:\n$rawResp")
                break
            }
        }
        return count
    }

    /**
     * 领奖失败统一处理：识别任务已关闭或不可用，自动加入风控名单停止重试
     * @return 若被百川风控拦截返回 true，指示调用方跳过本轮后续领奖
     */
    private fun handlePrizeFailure(missionDefId: Long, title: String, res: ElemeMtopUtil.MtopResult): Boolean {
        if (ElemeRiskTaskEntity.isTaskClosedError(res.errorCode, res.errorMsg)) {
            ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title, "任务已关闭或阶段无法达成")
            Log.other("饿了么", "[抽免单] 任务[$missionDefId - $title]已关闭或不可用，已自动加入风控名单，后续执行将自动跳过")
            return false
        }
        val rawResp = res.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: res.errorMsg
        val isBaichuan = rawResp.contains("RISK_USER") || rawResp.contains("NO_DECISION_ITEM") || rawResp.contains("风控用户")
        if (isBaichuan) {
            Log.other("饿了么", "[抽免单] 🛡️ 任务[$missionDefId - $title]领奖被百川风控拦截: 风控用户(RISK_USER)，本轮跳过领奖")
            return true
        }
        val detail = "[抽免单] ❌ 领奖[$title]失败: ${res.errorCode}::${res.errorMsg}\n完整响应:\n$rawResp"
        Log.error(TAG, detail)
        Log.other("饿了么", detail)
        return false
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
