package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock

/**
 * 饿了么抽免单自动化任务
 * 包含：
 * 1. 连续签到攒免单每日打卡
 * 2. 免单专属任务（如 47202001【抽惊喜免单】）推进与奖励领取
 * 3. 下单赢免单抽奖（mtop.alsc.interact.et.orderfree.draw）
 * 4. 免单夺宝机会抽奖（mtop.alsc.interact.playapp.reward.lottery.draw）
 * 5. 今日完成标记（防重复轮询刷屏）
 */
object FreeOrderTask {
    private const val TAG = "FreeOrderTask"
    private const val FLAG_TODAY = "Eleme_FreeOrder_Done"
    private val lock = ReentrantLock()

    fun handle() {
        if (Status.hasFlagToday(FLAG_TODAY)) {
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

            // 1. 严格校验会话有效性，若已失效则自动换票自愈刷新
            val cookie = ElemeAuth.validateOrRefreshCookie(initCookie)
            if (cookie.isNullOrEmpty()) {
                Log.other("饿了么", "[抽免单] ⚠️ 饿了么会话校验未通过且自动刷新失败，请在支付宝手动打开饿了么小程序完成授权")
                return
            }

            // 2. 获取互动中心免单主页数据
            val homepageResp = FreeOrderRpcCall.queryCenterHomepage(cookie)
            if (homepageResp == null) {
                val err = ElemeMtopUtil.lastError ?: "主页拉取失败"
                Log.other("饿了么", "[抽免单] ❌ 拉取免单中心数据失败: $err")
                return
            }

            val rootData = homepageResp.optJSONObject("data") ?: homepageResp
            val dataObj = rootData.optJSONObject("data") ?: rootData
            val signInObj = dataObj.optJSONObject("signIn")?.optJSONObject("data")
            val copyId = signInObj?.optJSONObject("extInfo")?.optString("copyId").orEmpty()
            val signedToday = signInObj?.optBoolean("signIn", false) ?: false

            // 3. 连续签到攒免单打卡
            if (!signedToday && copyId.isNotEmpty()) {
                Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                val signRes = FreeOrderRpcCall.recordSignIn(cookie, copyId)
                if (signRes.isSuccess) {
                    val rewards = signRes.data?.optJSONArray("rewards")
                    val starVal = rewards?.optJSONObject(0)?.optString("prizeValue", "238") ?: "238"
                    Log.other("饿了么", "[抽免单] 🎉 连续签到攒免单打卡成功 (+$starVal 幸运星/免单权益)")
                } else {
                    val errMsg = signRes.errorMsg
                    if (!errMsg.contains("已签到") && !signRes.errorCode.contains("REPEAT")) {
                        Log.other("饿了么", "[抽免单] 签到打卡: $errMsg")
                    }
                }
            }

            // 4. 推进免单专属任务（如【抽惊喜免单】任务，ID 47202001）
            Thread.sleep(RandomUtil.nextGaussianLong(600, 1200))
            val taskResp = LuckyStarRpcCall.queryTaskList(cookie)
            val taskArray = extractTaskArray(taskResp)
            if (taskArray != null && taskArray.length() > 0) {
                for (i in 0 until taskArray.length()) {
                    val task = taskArray.optJSONObject(i) ?: continue
                    val title = task.optString("showTitle").ifEmpty { task.optString("name") }
                    val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                    if (missionDefId <= 0) continue

                    // 仅聚焦免单相关任务
                    val isFreeOrderTask = title.contains("免单") || missionDefId == 47202001L
                    if (!isFreeOrderTask) continue

                    val receiveStatus = task.optString("receiveStatus")
                    val rewardStatus = task.optString("rewardStatus")
                    if (receiveStatus == "HAVERECEIVED" || rewardStatus == "SUCCESS" || rewardStatus == "RECEIVED") {
                        continue
                    }

                    if (LuckyStarRpcCall.isTaskRewardable(task)) {
                        val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task, null)
                        if (prizeRes.isSuccess) {
                            val desc = extractPrizeDesc(prizeRes.data ?: prizeRes.rawJson)
                            val descText = if (desc.isNotEmpty()) " +$desc" else ""
                            Log.other("饿了么", "[抽免单] 成功领取免单任务奖励: [$title]$descText")
                        }
                    } else {
                        val actionConfig = task.optJSONObject("actionConfig")
                        val actionType = actionConfig?.optString("actionType").orEmpty()
                        val actionValue = actionConfig?.optJSONObject("actionValue")
                        val pageSpm = actionValue?.optString("pageSpm").orEmpty()

                        if (actionType == "PAGEVIEW" && pageSpm.isNotEmpty()) {
                            val viewTime = actionValue?.optInt("pageStageTime")?.takeIf { it > 0 } ?: 10
                            Thread.sleep(viewTime * 1000L + RandomUtil.nextGaussianLong(500, 1500))
                            val viewRes = LuckyStarRpcCall.reportPageView(
                                cookie = cookie,
                                missionDefId = missionDefId,
                                pageSpm = pageSpm,
                                viewTime = viewTime,
                                instanceId = task.optString("instanceId").takeIf { it.isNotEmpty() }
                            )
                            if (viewRes.isSuccess) {
                                Log.other("饿了么", "[抽免单] 完成浏览任务: [$title]")
                                Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(cookie, missionDefId)
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null)
                                if (prizeRes.isSuccess) {
                                    val desc = extractPrizeDesc(prizeRes.data ?: prizeRes.rawJson)
                                    val descText = if (desc.isNotEmpty()) " +$desc" else ""
                                    Log.other("饿了么", "[抽免单] 成功领取免单任务奖励: [$title]$descText")
                                }
                            }
                        }
                    }
                }
            }

            // 5. 下单赢免单抽奖（mtop.alsc.interact.et.orderfree.draw）
            Thread.sleep(RandomUtil.nextGaussianLong(600, 1200))
            val orderFreeRes = FreeOrderRpcCall.orderFreeDraw(cookie)
            if (orderFreeRes.isSuccess) {
                val prize = extractPrizeDesc(orderFreeRes.data ?: orderFreeRes.rawJson)
                val prizeDesc = if (prize.isNotEmpty()) "，获得: $prize" else ""
                Log.other("饿了么", "[抽免单] 🎟 下单赢免单抽奖成功$prizeDesc")
            }

            // 6. 夺宝 / 免单机会抽奖（mtop.alsc.interact.playapp.reward.lottery.draw）
            val lotteries = dataObj.optJSONArray("lotteries")
            if (lotteries != null && lotteries.length() > 0) {
                for (j in 0 until lotteries.length()) {
                    val lot = lotteries.optJSONObject(j) ?: continue
                    val actId = lot.optString("actId")
                    val compId = lot.optString("componentId")
                    if (actId.isNotEmpty() && compId.isNotEmpty()) {
                        Thread.sleep(RandomUtil.nextGaussianLong(800, 1500))
                        val lotRes = FreeOrderRpcCall.lotteryDraw(cookie, actId, compId)
                        if (lotRes.isSuccess) {
                            val prize = extractPrizeDesc(lotRes.data ?: lotRes.rawJson)
                            Log.other("饿了么", "[抽免单] 🎰 免单夺宝中奖: $prize")
                        }
                    }
                }
            }

            // 7. 外卖小程序打卡召回
            FreeOrderRpcCall.pageRecall(cookie)

            Status.setFlagToday(FLAG_TODAY)
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

    private fun extractPrizeDesc(resp: JSONObject?): String {
        return ElemeMtopUtil.extractPrizeDesc(resp)
    }
}
