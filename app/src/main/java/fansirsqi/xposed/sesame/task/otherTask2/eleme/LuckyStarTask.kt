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
    private val lock = ReentrantLock()

    private val BLACKLIST_KEYWORDS = listOf(
        "下单", "购买", "买单", "开卡", "月卡", "SVIP", "超级会员", "付款", "好友", "充值"
    )

    fun handle() {
        if (!lock.tryLock()) {
            Log.other("饿了么", "[幸运星] 已有饿了么幸运星任务正在执行中，跳过本次并发调用")
            return
        }

        try {
            Log.other("饿了么", "[幸运星] ==== 开始执行饿了么幸运星任务 ====")
            val initCookie = ElemeAuth.ensureCookie()
            if (initCookie.isEmpty() || !ElemeAuth.isBound()) {
                val reason = if (initCookie.isEmpty()) "未获取到有效登录凭证(账号未绑定或换票失败)" else "当前未关联有效饿了么账号"
                Log.other("饿了么", "[幸运星] $reason，自动关闭【饿了么|幸运星】开关并退出")
                fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.disableElemeLuckyStar(reason)
                return
            }

            // 1. 严格校验会话有效性，若已失效则自动换票自愈刷新
            val cookie = ElemeAuth.validateOrRefreshCookie(initCookie)
            if (cookie.isNullOrEmpty()) {
                Log.other("饿了么", "[幸运星] ⚠️ 饿了么会话校验未通过且自动刷新失败，请在支付宝手动打开饿了么小程序完成授权")
                return
            }
            val profile = ElemeMtopUtil.getActiveProfile()
            Log.other("饿了么", "[幸运星] 当前账号: ${ElemeAuth.getAccountSummary()} | 设备指纹: ${profile.brandModel()} (Android ${profile.release})")

            // 2. 拟真环境初始化：页面动效组件加载与订阅提醒
            LuckyStarRpcCall.initPageComponents(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(400, 700))
            LuckyStarRpcCall.querySubscribeRemind(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(400, 700))

            // 3. 查询初始资产
            val initProperty = LuckyStarRpcCall.queryProperty(cookie)
            val initialStars = extractStarAmount(initProperty)
            Log.other("饿了么", "[幸运星] 当前幸运星余额: $initialStars")

            // 4. 查询全量任务列表
            val taskListResp = LuckyStarRpcCall.queryTaskList(cookie)
            if (taskListResp == null) {
                val err = ElemeMtopUtil.lastError ?: "接口请求失败或未返回有效数据"
                Log.other("饿了么", "[幸运星] ❌ 获取任务列表失败: $err")
                return
            }

            val taskArray = extractTaskArray(taskListResp)
            if (taskArray == null || taskArray.length() == 0) {
                Log.other("饿了么", "[幸运星] 当前无可执行任务")
                return
            }

            var finishedCount = 0
            var rewardedCount = 0

            // ==== 第一轮: 遍历执行可自动化的浏览与点击任务 ====
            Log.other("饿了么", "[幸运星] ==== 第一轮: 开始执行浏览与点击任务 ====")
            for (i in 0 until taskArray.length()) {
                val task = taskArray.optJSONObject(i) ?: continue
                val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                val missionXId = task.optString("missionXId")
                val title = task.optString("showTitle").ifEmpty { task.optString("name") }
                val receiveStatus = task.optString("receiveStatus")
                val rewardStatus = task.optString("rewardStatus")

                if (missionDefId <= 0) continue

                // 已领取的任务跳过
                if (receiveStatus == "HAVERECEIVED" || rewardStatus == "SUCCESS" || rewardStatus == "RECEIVED") {
                    continue
                }

                // 黑名单与 UI 风控跳过过滤
                val riskTasks: Set<String> = fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeRiskTasks().value ?: emptySet()
                val inRiskTaskSet = riskTasks.contains(missionDefId.toString())
                val inGlobalBlacklist = TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(missionDefId.toString())
                val inKeywordBlacklist = BLACKLIST_KEYWORDS.any { title.contains(it) }
                if (inRiskTaskSet || inGlobalBlacklist || inKeywordBlacklist) {
                    val reason = if (inRiskTaskSet) "UI风控跳过名单(已勾选)" else if (inGlobalBlacklist) "通用黑名单" else "关键词过滤"
                    Log.other("饿了么", "[幸运星] 任务[$title](ID:$missionDefId)已在跳过名单中($reason)，跳过执行")
                    continue
                }

                // 若任务已处于可领奖状态，无需重复做任务，留待第二轮统一领取
                if (LuckyStarRpcCall.isTaskRewardable(task)) {
                    Log.other("饿了么", "[幸运星] 任务已达成: [$title] (DefId: $missionDefId)，待第二轮统一领奖")
                    continue
                }

                val actionConfig = task.optJSONObject("actionConfig")
                val actionType = actionConfig?.optString("actionType").orEmpty()
                val actionValue = actionConfig?.optJSONObject("actionValue")
                val pageSpm = actionValue?.optString("pageSpm").orEmpty()
                val sceneCode = actionValue?.optString("scenceCode").orEmpty()

                if (actionType == "PAGEVIEW" && pageSpm.isNotEmpty()) {
                    val pageStageTime = actionValue?.optInt("pageStageTime")?.takeIf { it > 0 } ?: if (title.contains("15秒")) 15 else 10
                    Log.other("饿了么", "[幸运星] 执行浏览任务: [$title]，模拟浏览 ${pageStageTime} 秒...")
                    Thread.sleep(pageStageTime * 1000L + RandomUtil.nextGaussianLong(500, 1500))

                    val viewRes = LuckyStarRpcCall.reportPageView(
                        cookie = cookie,
                        missionDefId = missionDefId,
                        pageSpm = pageSpm,
                        viewTime = pageStageTime + RandomUtil.nextInt(0, 3),
                        instanceId = task.optString("instanceId").takeIf { it.isNotEmpty() }
                    )
                    if (viewRes.isSuccess) {
                        Log.other("饿了么", "[幸运星] 浏览上报成功: [$title]")
                        finishedCount++
                    } else {
                        Log.other("饿了么", "[幸运星] 浏览上报失败: [$title] -> ${viewRes.errorCode}::${viewRes.errorMsg}")
                        if (viewRes.errorMsg.contains("405") || viewRes.errorMsg.contains("行为受限") || viewRes.errorMsg.contains("风控")) {
                            Log.other("饿了么", "[幸运星] 🛡️ 任务[$title](ID:$missionDefId)浏览埋点遭遇风控拦截，已自动加入UI风控跳过名单")
                            fansirsqi.xposed.sesame.entity.ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title)
                            fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeRiskTasks().value?.add(missionDefId.toString())
                            runCatching {
                                fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
                            }
                        }
                    }
                    Thread.sleep(RandomUtil.nextGaussianLong(1200, 2000))
                } else if (actionType == "THIRD" && (sceneCode == "commercial_click" || sceneCode == "inter_change" || title.contains("点击") || title.contains("店铺"))) {
                    val total = actionValue?.optInt("finalStageCount")?.takeIf { it > 0 }
                        ?: task.optJSONObject("stage")?.optInt("targetCount")?.takeIf { it > 0 }
                        ?: if (title.contains("3个") || title.contains("3次")) 3 else 1
                    val done = task.optJSONObject("stage")?.optInt("count") ?: 0
                    val need = (total - done).coerceAtLeast(0).coerceAtMost(3)
                    Log.other("饿了么", "[幸运星] 执行动作任务: [$title]，总需 $total 次 / 已做 $done 次 / 需推进 $need 次")
                    for (step in 1..need) {
                        val triggerRes = LuckyStarRpcCall.reportTrigger(
                            cookie = cookie,
                            missionDefId = missionDefId,
                            missionXId = missionXId
                        )
                        if (triggerRes.isSuccess) {
                            Log.other("饿了么", "[幸运星] 动作推进成功: [$title] ($step/$need)")
                        } else {
                            Log.other("饿了么", "[幸运星] 动作推进失败: [$title] ($step/$need) -> ${triggerRes.errorCode}::${triggerRes.errorMsg}")
                            if (triggerRes.errorMsg.contains("风控") || triggerRes.errorMsg.contains("405") || triggerRes.errorCode.contains("RISK")) {
                                Log.other("饿了么", "[幸运星] 🛡️ 任务[$title](ID:$missionDefId)动作推进遭遇风控拦截，已自动加入UI风控跳过名单")
                                fansirsqi.xposed.sesame.entity.ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title)
                                fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeRiskTasks().value?.add(missionDefId.toString())
                                runCatching {
                                    fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
                                }
                            }
                            break
                        }
                        Thread.sleep(RandomUtil.nextGaussianLong(1800, 2600))
                    }
                    if (need > 0) finishedCount++
                } else {
                    Log.other("饿了么", "[幸运星] 跳过非自动化任务: [$title] (actionType: $actionType)")
                }
            }

            // ==== 第二轮: 重新刷新任务列表并批量领取奖励 ====
            Log.other("饿了么", "[幸运星] ==== 第二轮: 刷新任务进度并领取奖励 ====")
            Thread.sleep(RandomUtil.nextGaussianLong(1500, 2500))
            val refreshedResp = LuckyStarRpcCall.queryTaskList(cookie)
            val refreshedArray = extractTaskArray(refreshedResp) ?: taskArray

            for (i in 0 until refreshedArray.length()) {
                val task = refreshedArray.optJSONObject(i) ?: continue
                val missionDefId = task.optLong("missionDefId").takeIf { it > 0 } ?: task.optLong("id")
                val title = task.optString("showTitle").ifEmpty { task.optString("name") }

                if (missionDefId <= 0) continue

                // UI 风控跳过名单与通用黑名单过滤
                val riskTasks: Set<String> = fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeRiskTasks().value ?: emptySet()
                val inRiskTaskSet = riskTasks.contains(missionDefId.toString())
                val inGlobalBlacklist = TaskBlacklist.isTaskInBlacklist(title) || TaskBlacklist.isTaskInBlacklist(missionDefId.toString())
                if (inRiskTaskSet || inGlobalBlacklist) {
                    val reason = if (inRiskTaskSet) "UI风控跳过名单(已勾选)" else "通用黑名单"
                    Log.other("饿了么", "[幸运星] 领奖跳过: [$title](ID:$missionDefId) 处于$reason，跳过领奖")
                    continue
                }

                // 仅对真正达成领奖条件的任务调用领奖（智能匹配阶段参数）
                if (LuckyStarRpcCall.isTaskRewardable(task)) {
                    val prizeRes = LuckyStarRpcCall.receivePrize(cookie, task)
                    if (prizeRes.isSuccess) {
                        val rewardDesc = extractPrizeDesc(prizeRes.data ?: prizeRes.rawJson)
                        Log.other("饿了么", "[幸运星] 成功领取任务奖励: [$title] -> $rewardDesc")
                        rewardedCount++
                    } else {
                        val isRisk = prizeRes.errorCode.contains("RISK") ||
                                prizeRes.errorMsg.contains("风控") ||
                                prizeRes.errorMsg.contains("NO_DECISION_ITEM") ||
                                prizeRes.errorMsg.contains("RECEIVE_ALL_ERROR") ||
                                prizeRes.errorMsg.contains("405") ||
                                prizeRes.errorMsg.contains("安全校验")

                        if (isRisk) {
                            Log.other("饿了么", "[幸运星] 🛡️ 任务[$title](ID:$missionDefId)触发风控拦截(${prizeRes.errorMsg})，已自动加入UI风控跳过名单")
                            fansirsqi.xposed.sesame.entity.ElemeRiskTaskEntity.recordRiskTask(missionDefId.toString(), title)
                            fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.getElemeRiskTasks().value?.add(missionDefId.toString())
                            runCatching {
                                fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
                            }
                            TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, prizeRes.errorCode, prizeRes.errorMsg)
                        } else {
                            Log.other("饿了么", "[幸运星] 领取任务奖励失败: [$title] -> ${prizeRes.errorCode}::${prizeRes.errorMsg}")
                            if (prizeRes.errorCode.contains("NOT_EXIST") || prizeRes.errorCode.contains("NOT_FINISH") || prizeRes.errorMsg.contains("LIMIT")) {
                                TaskBlacklist.autoAddToBlacklist(missionDefId.toString(), title, prizeRes.errorCode, prizeRes.errorMsg)
                            }
                        }
                    }
                    Thread.sleep(RandomUtil.nextGaussianLong(1200, 2200))
                }
            }

            // 3. 统计最终资产并更新状态
            val finalProperty = LuckyStarRpcCall.queryProperty(cookie)
            val finalStars = extractStarAmount(finalProperty)
            val delta = finalStars - initialStars

            Log.other("饿了么", "[幸运星] ==== 饿了么幸运星任务执行完毕: 完成 $finishedCount 个任务, 成功领奖 $rewardedCount 次, 当前星数: $finalStars (+$delta) ====")
        } catch (t: Throwable) {
            Log.other("饿了么", "[幸运星] 执行饿了么幸运星任务异常: ${t.message}")
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
        if (resp == null) return ""
        val data = resp.optJSONObject("data") ?: resp
        val rlist = data.optJSONArray("rlist")
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
