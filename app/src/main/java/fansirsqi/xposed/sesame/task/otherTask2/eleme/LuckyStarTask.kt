package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
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
    private const val FLAG_TODAY = "Eleme_LuckyStar_Done"
    private val lock = ReentrantLock()

    private val BLACKLIST_KEYWORDS = listOf(
        "下单", "购买", "买单", "开卡", "月卡", "SVIP", "超级会员", "付款", "好友", "充值"
    )

    fun handle() {
        if (Status.hasFlagToday(FLAG_TODAY)) {
            Log.runtime(TAG, "今日饿了么幸运星任务已完成，跳过执行")
            return
        }

        if (!lock.tryLock()) {
            Log.runtime(TAG, "已有饿了么幸运星任务正在执行中，跳过本次并发调用")
            return
        }

        try {
            Log.runtime(TAG, "==== 开始执行饿了么幸运星任务 ====")
            val cookie = ElemeAuth.ensureCookie()
            if (cookie.isEmpty() || !ElemeAuth.isBound()) {
                val reason = if (cookie.isEmpty()) "未获取到有效登录凭证(账号未绑定或换票失败)" else "当前未关联有效饿了么账号"
                Log.runtime(TAG, "$reason，自动关闭【饿了么|幸运星】开关并退出")
                fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.disableElemeLuckyStar(reason)
                return
            }
            Log.runtime(TAG, "当前账号: ${ElemeAuth.getAccountSummary()}")

            // 1. 拟真环境初始化：会话校验、页面动效与气泡/弹窗组件加载、签到订阅弹窗查询
            LuckyStarRpcCall.checkLogin(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(400, 800))

            LuckyStarRpcCall.initPageComponents(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(600, 1200))

            LuckyStarRpcCall.querySubscribeRemind(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(500, 900))

            // 2. 查询初始资产
            val initProperty = LuckyStarRpcCall.queryProperty(cookie)
            val initialStars = extractStarAmount(initProperty)
            Log.runtime(TAG, "当前幸运星余额: $initialStars")

            // 3. 查询全量任务列表
            val taskListResp = LuckyStarRpcCall.queryTaskList(cookie)
            if (taskListResp == null) {
                Log.error(TAG, "获取任务列表失败")
                return
            }

            val taskArray = extractTaskArray(taskListResp)
            if (taskArray == null || taskArray.length() == 0) {
                Log.runtime(TAG, "当前无可执行任务")
                return
            }

            var finishedCount = 0
            var rewardedCount = 0

            for (i in 0 until taskArray.length()) {
                val task = taskArray.optJSONObject(i) ?: continue
                val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                var missionXId = task.optString("missionXId")
                val title = task.optString("showTitle").ifEmpty { task.optString("name") }
                val status = task.optString("status")
                val receiveStatus = task.optString("receiveStatus")
                val rewardStatus = task.optString("rewardStatus")

                if (missionDefId <= 0) continue

                // 已领取的任务跳过
                if (receiveStatus == "HAVERECEIVED" || rewardStatus == "SUCCESS" || rewardStatus == "RECEIVED") {
                    continue
                }

                // 黑名单过滤（接入通用 TaskBlacklist 与非自动化关键词，阻止不支持的任务）
                val inGlobalBlacklist = TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(missionDefId.toString())
                val inKeywordBlacklist = BLACKLIST_KEYWORDS.any { title.contains(it) }
                if (inGlobalBlacklist || inKeywordBlacklist) {
                    Log.runtime(TAG, "跳过黑名单/不支持的任务: [$title]")
                    continue
                }

                Log.runtime(TAG, "发现待处理任务: [$title] (DefId: $missionDefId, 状态: $status/$receiveStatus)")

                // 判断是否已达成可领奖状态
                val isReadyToClaim = status == "FINISH" ||
                        receiveStatus == "TORECEIVE" ||
                        receiveStatus == "WAITRECEIVE" ||
                        rewardStatus == "WAITING_RECEIVE"

                if (isReadyToClaim) {
                    val prizeResp = LuckyStarRpcCall.receivePrize(cookie, missionDefId, missionXId)
                    val rewardDesc = extractPrizeDesc(prizeResp)
                    if (rewardDesc.isNotEmpty()) {
                        Log.runtime(TAG, "成功领取任务奖励: [$title] -> $rewardDesc")
                        rewardedCount++
                        finishedCount++
                    } else {
                        Log.runtime(TAG, "领取任务奖励返回: [$title] -> ${prizeResp?.optJSONArray("ret")?.optString(0) ?: "未知"}")
                    }
                    Thread.sleep(RandomUtil.nextGaussianLong(1000, 2000))
                    continue
                }

                // 计数型任务（如“在页面内点击3个店铺”）
                val actionConfig = task.optJSONObject("actionConfig")
                val actionType = actionConfig?.optString("actionType").orEmpty()
                val actionValue = actionConfig?.optJSONObject("actionValue")
                val targetCount = actionValue?.optInt("finalStageCount")?.takeIf { it > 1 }
                    ?: task.optJSONObject("stage")?.optInt("targetCount")
                    ?: if (title.contains("3个") || title.contains("3次")) 3 else 1

                var currentCount = task.optJSONObject("stage")?.optInt("count") ?: 0

                if (targetCount > 1) {
                    var retry = 0
                    while (currentCount < targetCount && retry < targetCount + 2) {
                        retry++
                        Log.runtime(TAG, "执行计数任务进度: [$title] ($currentCount/$targetCount)...")
                        Thread.sleep(RandomUtil.nextGaussianLong(1500, 2500))

                        // 精准刷新单任务状态获取最新进度与动态 missionXId
                        val refreshed = LuckyStarRpcCall.queryTaskList(cookie, missionDefId)
                        val refreshedTask = findTask(refreshed, missionDefId)
                        if (refreshedTask != null) {
                            currentCount = refreshedTask.optJSONObject("stage")?.optInt("count") ?: (currentCount + 1)
                            val newXId = refreshedTask.optString("missionXId")
                            if (newXId.isNotEmpty()) missionXId = newXId
                            val curStatus = refreshedTask.optString("status")
                            val curRecv = refreshedTask.optString("receiveStatus")
                            if (curStatus == "FINISH" || curRecv == "TORECEIVE" || curRecv == "WAITRECEIVE") {
                                break
                            }
                        } else {
                            currentCount++
                        }
                    }
                } else {
                    // 普通浏览或跳转任务
                    val pageStageTime = actionValue?.optInt("pageStageTime") ?: if (title.contains("15秒")) 15 else 3
                    Log.runtime(TAG, "执行浏览任务: [$title]，模拟浏览 ${pageStageTime} 秒...")
                    Thread.sleep(pageStageTime * 1000L + RandomUtil.nextGaussianLong(500, 1500))

                    // 刷新任务状态获取最新 missionXId
                    val refreshed = LuckyStarRpcCall.queryTaskList(cookie, missionDefId)
                    val refreshedTask = findTask(refreshed, missionDefId)
                    if (refreshedTask != null) {
                        val newXId = refreshedTask.optString("missionXId")
                        if (newXId.isNotEmpty()) missionXId = newXId
                    }
                }

                // 尝试领取任务奖励
                val prizeResp = LuckyStarRpcCall.receivePrize(cookie, missionDefId, missionXId)
                val rewardDesc = extractPrizeDesc(prizeResp)
                if (rewardDesc.isNotEmpty()) {
                    Log.runtime(TAG, "成功完成并领取奖励: [$title] -> $rewardDesc")
                    rewardedCount++
                    finishedCount++
                } else {
                    val retArray = prizeResp?.optJSONArray("ret")
                    val retMsg = retArray?.optString(0).orEmpty()
                    Log.runtime(TAG, "任务未达成完成条件或暂不可领奖: [$title]${if (retMsg.isNotEmpty()) " -> $retMsg" else ""}")
                    // 对明确不支持、失败或需人工介入的任务，自动记入当日黑名单阻止后续重复无效执行
                    if (retMsg.contains("FAIL") || retMsg.contains("NOT_SUPPORT") || retMsg.contains("LIMIT")) {
                        TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, "RECEIVE_FAIL", retMsg)
                    }
                }
                Thread.sleep(RandomUtil.nextGaussianLong(1200, 2200))
            }

            // 3. 统计最终资产并更新状态
            val finalProperty = LuckyStarRpcCall.queryProperty(cookie)
            val finalStars = extractStarAmount(finalProperty)
            val delta = finalStars - initialStars

            Log.runtime(TAG, "==== 饿了么幸运星任务执行完毕: 完成 $finishedCount 个任务, 成功领奖 $rewardedCount 次, 当前星数: $finalStars (+$delta) ====")
            Status.setFlagToday(FLAG_TODAY)
        } catch (t: Throwable) {
            Log.error(TAG, "执行饿了么幸运星任务异常: ${t.message}")
        } finally {
            lock.unlock()
        }
    }

    private fun extractTaskArray(root: JSONObject): JSONArray? {
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
        if (rlist != null && rlist.length() > 0) {
            val first = rlist.optJSONObject(0)
            val upp = first?.optJSONObject("uppPrizeResult")
            val title = upp?.optJSONObject("materialInfo")?.optString("title")
            val discount = upp?.optJSONObject("discountInfo")?.optString("amount")
            if (!title.isNullOrEmpty()) return title
            if (!discount.isNullOrEmpty()) return "+$discount 幸运星"
            val valStr = first?.optString("value")
            if (!valStr.isNullOrEmpty()) return "+$valStr 幸运星"
            return "成功"
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
