package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TaskBlacklist
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock

/**
 * 饿了么抽免单自动化任务
 * 包含：
 * 1. 连续签到攒免单每日打卡（输出：[抽免单] 签到: +1 抽奖次数）
 * 2. 免单专属浏览任务（如 47202001【抽惊喜免单】）推进与奖励领取
 * 3. 彻底黑名单过滤所有下单/购买等不可自动化的消费任务
 * 4. 免单夺宝机会抽奖（mtop.alsc.interact.playapp.reward.lottery.draw，中奖才报，无机会静默）
 * 5. 今日完成标记（防重复轮询刷屏）
 */
object FreeOrderTask {
    private const val TAG = "FreeOrderTask"
    private const val FLAG_TODAY = "Eleme_FreeOrder_Done"
    private val lock = ReentrantLock()

    fun handle() {
        if (!ElemeTask.elemeFreeOrder.value || Status.hasFlagToday(FLAG_TODAY)) {
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
                val raw = ElemeMtopUtil.lastResponse ?: "无原始响应"
                val msg = "[抽免单] ❌ 拉取免单中心数据失败: $err\n完整响应:\n$raw"
                Log.error(TAG, msg)
                Log.other("饿了么", msg)
                return
            }

            val rootData = homepageResp.optJSONObject("data") ?: homepageResp
            val dataObj = rootData.optJSONObject("data") ?: rootData
            val signInObj = dataObj.optJSONObject("signIn")?.optJSONObject("data")
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
            val copyId = todayDto?.optJSONObject("extInfo")?.optString("copyId")?.takeIf { it.isNotEmpty() }
                ?: signInObj?.optJSONObject("extInfo")?.optString("copyId").orEmpty()
            val signedToday = todayDto?.optBoolean("signIn", false) ?: signInObj?.optBoolean("signIn", false) ?: false
            val awards = todayDto?.optJSONObject("extInfo")?.optJSONArray("stageAward")
            val award = awards?.optJSONObject(0)

            // 3. 连续签到攒免单打卡（对齐幸运星.py：[抽免单] 签到: +1 抽奖次数，已签到静默）
            if (!signedToday && copyId.isNotEmpty()) {
                Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                val signRes = FreeOrderRpcCall.recordSignIn(cookie, copyId, award)
                if (signRes.isSuccess) {
                    Log.other("饿了么", "[抽免单] 签到: +1 抽奖次数")
                } else {
                    val errMsg = signRes.errorMsg
                    if (!errMsg.contains("已签到") && !signRes.errorCode.contains("REPEAT")) {
                        val rawResp = signRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: errMsg
                        val errDetail = "[抽免单] ❌ 签到打卡失败: ${signRes.errorCode}::$errMsg\n完整响应:\n$rawResp"
                        Log.error(TAG, errDetail)
                        Log.other("饿了么", errDetail)
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

                    val actionConfig = task.optJSONObject("actionConfig")
                    val actionType = actionConfig?.optString("actionType").orEmpty()

                    // 对齐幸运星.py规范：ORDER/P2P类型直接跳过，绝不按名称关键词误杀浏览任务
                    if (actionType == "ORDER" || actionType == "P2P") {
                        continue
                    }
                    if (TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(missionDefId.toString())) {
                        continue
                    }

                    // 仅聚焦免单相关任务（如 47202001【抽惊喜免单】）
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
                            Log.other("饿了么", "[抽免单] [$title]: +1 抽奖次数")
                        } else {
                            val rawResp = prizeRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: prizeRes.errorMsg
                            val errDetail = "[抽免单] ❌ 领取免单任务奖励失败: [$title] -> ${prizeRes.errorCode}::${prizeRes.errorMsg}\n完整响应:\n$rawResp"
                            Log.error(TAG, errDetail)
                            Log.other("饿了么", errDetail)
                        }
                    } else {
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
                                Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                                val refreshResp = LuckyStarRpcCall.queryTaskList(cookie, missionDefId)
                                val refreshedTask = findTask(refreshResp, missionDefId) ?: task
                                val prizeRes = LuckyStarRpcCall.receivePrize(cookie, refreshedTask, null)
                                if (prizeRes.isSuccess) {
                                    Log.other("饿了么", "[抽免单] [$title]: +1 抽奖次数")
                                } else {
                                    val rawResp = prizeRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: prizeRes.errorMsg
                                    val errDetail = "[抽免单] ❌ 领取免单任务奖励失败: [$title] -> ${prizeRes.errorCode}::${prizeRes.errorMsg}\n完整响应:\n$rawResp"
                                    Log.error(TAG, errDetail)
                                    Log.other("饿了么", errDetail)
                                }
                            } else {
                                val rawResp = viewRes.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: viewRes.errorMsg
                                val errDetail = "[抽免单] ❌ 浏览上报失败: [$title] -> ${viewRes.errorCode}::${viewRes.errorMsg}\n完整响应:\n$rawResp"
                                Log.error(TAG, errDetail)
                                Log.other("饿了么", errDetail)
                            }
                        }
                    }
                }
            }

            // 5. 下单类免单任务直接黑名单跳过；此处仅处理免单夺宝/抽奖组件（mtop.alsc.interact.playapp.reward.lottery.draw）
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
                            if (prize.isNotEmpty()) {
                                Log.other("饿了么", "[抽免单] 中奖: $prize")
                            }
                        }
                    }
                }
            }

            // 6. 外卖小程序打卡召回
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
