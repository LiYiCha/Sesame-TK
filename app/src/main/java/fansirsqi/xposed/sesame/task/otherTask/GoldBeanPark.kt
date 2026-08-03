package fansirsqi.xposed.sesame.task.otherTask

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.task.antOrchard.GameTask
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 金豆乐园 🎡
 */
class GoldBeanPark {
    private val TAG = "金豆乐园🎡"

    fun run() {
        val hour = TimeUtil.getHourOfDay()
        if (hour < 7 || Status.hasFlagToday("goldBeanPark::allTask")) {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleGoldBeanPark()
            } catch (e: Exception) {
                Log.error(TAG, "handleGoldBeanPark error: $e")
            }
        }
        Status.setFlagToday("goldBeanPark::allTask")
    }

    private suspend fun handleGoldBeanPark() {
        try {
            // 1. 首页初始化与数据同步
            goldenBeanIndex()
            listTopItemsByScene()
            val syncRes = goldenBeanSync(listOf("JAR_INFO", "SIGN", "MARKETING_POPUP", "TASK_LIST", "FORTUNE_DRAW", "EXCHANGE_MANURE", "FARM_TASK", "GAME_CENTER_FOR_INDEX", "DRAINAGE", "SPROUT_INFO"))
            if (!syncRes.optBoolean("success", true) && syncRes.has("resultDesc")) {
                Log.error(TAG, "金豆同步异常: ${syncRes.optString("resultDesc")}")
            }

            // 2. 金豆签到 (增加每日标记限制，防止多次重复执行)
            if (!Status.hasFlagToday("goldBeanPark::sign")) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val signRes = goldenBeanSign(todayStr)
                if (signRes.optBoolean("success")) {
                    Status.setFlagToday("goldBeanPark::sign")
                    val incCount = extractAwardBeanCount(signRes)
                    if (incCount > 0) {
                        Log.other(TAG, "金豆签到成功+$incCount 金豆")
                    } else {
                        val desc = signRes.optString("desc", signRes.optString("resultDesc", "成功"))
                        Log.other(TAG, "金豆签到: $desc")
                    }
                } else {
                    val desc = signRes.optString("resultDesc", signRes.optString("desc", ""))
                    if (desc.contains("已签") || desc.contains("重复") || desc.contains("签过")) {
                        Status.setFlagToday("goldBeanPark::sign")
                    }
                }
            }

            // 3. 任务处理：每完成/领取一个任务就重新 sync，模拟真实操作节奏
            while (true) {
                // ── 拉取最新任务列表 ──
                val syncTaskRes = goldenBeanSync(listOf("JAR_INFO", "TASK_LIST", "FORTUNE_DRAW", "EXCHANGE_MANURE", "FARM_TASK", "GAME_CENTER_FOR_INDEX", "DRAINAGE", "SPROUT_INFO"))
                val taskList = syncTaskRes.optJSONArray("taskList")
                if (taskList == null || taskList.length() == 0) break

                var hasWorkDone = false

                for (i in 0 until taskList.length()) {
                    val task = taskList.getJSONObject(i)
                    val taskId = task.optString("taskId")
                    val taskType = task.optString("taskType").ifEmpty { taskId }
                    val taskStatus = task.optString("taskStatus")
                    val actionType = task.optString("actionType")
                    val taskSceneCode = task.optString("sceneCode", "GOLDENBEAN")
                    val displayConfig = task.optJSONObject("taskDisplayConfig")
                    val title = displayConfig?.optString("title") ?: taskId
                    val type = displayConfig?.optString("type") ?: ""

                    // 已完成且已领取的任务跳过
                    if (taskStatus == "DONE" || taskStatus == "RECEIVED") continue

                    // 1. 抽签任务（一次性，不需 re-sync）
                    if (actionType == "FORTUNE_DRAW" || taskType == "FORTUNE_DRAW" || taskId == "FORTUNE_DRAW") {
                        if (!Status.hasFlagToday("goldBeanPark::fortuneDraw")) {
                            val drawRes = goldenBeanFortuneDraw()
                            if (drawRes.optBoolean("success")) {
                                Status.setFlagToday("goldBeanPark::fortuneDraw")
                                val incCount = drawRes.optInt("beanDelta", 0)
                                Log.other(TAG, "金豆抽签成功+$incCount 金豆")
                            } else {
                                Log.other(TAG, "金豆抽签: ${drawRes.optString("resultDesc", "完成")}")
                                Status.setFlagToday("goldBeanPark::fortuneDraw")
                            }
                        }
                        continue
                    }

                    // 2. 待领奖状态 (FINISHED)：领奖后 re-sync
                    if (taskStatus == "FINISHED") {
                        var awardRes = receiveTaskAwardAntOrchard(taskType, taskSceneCode)
                        if (!awardRes.optBoolean("success") && taskSceneCode != "GOLDEN_BEAN_MASTER_TASK") {
                            awardRes = receiveTaskAwardAntOrchard(taskType, "GOLDEN_BEAN_MASTER_TASK")
                        }
                        if (awardRes.optBoolean("success")) {
                            val incCount = extractAwardBeanCount(awardRes)
                            Log.other(TAG, "领取[$title]+$incCount 金豆")
                            hasWorkDone = true
                            break
                        } else {
                            val desc = awardRes.optString("desc", awardRes.optString("resultDesc", "结果未知"))
                            Log.other(TAG, "领取任务失败[$title]: $desc")
                        }
                        continue
                    }

                    // 3. 广告浏览任务 (xlight)：独立 RPC，每次完成需 re-sync 拿新 bizId
                    if (taskStatus == "TODO" && type == "xlight") {
                        val spmExtend = task.optJSONObject("spmExtend")
                        val xlightMap = spmExtend?.optJSONObject("xlightLogExtMap")
                        val bizId = xlightMap?.optString("bizId")
                        if (bizId.isNullOrEmpty()) continue

                        val adRes = finishAdTask(bizId, xlightMap?.optJSONObject("extendInfo"))
                        if (adRes.optBoolean("success")) {
                            val reward = adRes.optJSONObject("extendInfo")?.optJSONObject("rewardInfo")
                            val amount = reward?.optString("rewardAmount", "0") ?: "0"
                            val taskTitle = adRes.optJSONObject("extendInfo")?.optJSONObject("taskInfo")?.optString("taskTitle", title) ?: title
                            Log.other(TAG, "广告完成[$taskTitle]+$amount 金豆")
                            hasWorkDone = true
                            break
                        } else {
                            Log.other(TAG, "广告任务失败[$title]: ${adRes.optString("errMsg", "未知错误")}")
                        }
                        delay(13000 + (0..1000).random().toLong())
                        continue
                    }

                    // 4. 未打卡状态：finishTask + 领奖后 re-sync
                    if (taskStatus == "TODO") {
                        if (isBlacklistedTask(taskId, taskType, actionType, type, title)) continue

                        val userId = UserMap.currentUid ?: ""
                        val finishRes = finishTaskAntOrchard(taskType, userId, taskSceneCode)

                        if (finishRes.optBoolean("success")) {
                            delay(1000 + (0..1000).random().toLong())
                            var awardRes = receiveTaskAwardAntOrchard(taskType, taskSceneCode)
                            if (!awardRes.optBoolean("success") && taskSceneCode != "GOLDEN_BEAN_MASTER_TASK") {
                                awardRes = receiveTaskAwardAntOrchard(taskType, "GOLDEN_BEAN_MASTER_TASK")
                            }
                            if (awardRes.optBoolean("success")) {
                                val incCount = extractAwardBeanCount(awardRes)
                                Log.other(TAG, "完成[$title]+$incCount 金豆")
                                hasWorkDone = true
                                break
                            } else {
                                val errorMsg = awardRes.optString("errorMsg", awardRes.optString("desc", awardRes.optString("resultDesc", awardRes.toString())))
                                Log.error(TAG, "任务失败[$title]: $errorMsg")
                            }
                        }
                        delay(2000 + (0..1000).random().toLong())
                        continue
                    }
                }

                // 本轮未处理任何任务，退出 while
                if (!hasWorkDone) break
            }

            // 4. 金豆对对碰游戏自动上报与开金蛋/开宝箱 (charitygamecenter)
            if (!Status.hasFlagToday("goldBeanPark::gameFinished")) {
                val gameListRes = queryCharityGameList()
                if (gameListRes.optBoolean("success") || gameListRes.optString("desc") == "SUCCESS") {
                    val drawRights = gameListRes.optJSONObject("gameCenterDrawRights")
                    if (drawRights != null) {
                        var quotaCanUse = drawRights.optInt("quotaCanUse", 0)
                        val quotaLimit = drawRights.optInt("quotaLimit", 20)
                        val usedQuota = drawRights.optInt("usedQuota", 0)

                        val remainToTask = quotaLimit - usedQuota
                        if (remainToTask > 0 && quotaCanUse == 0) {
                            Log.other(TAG, "金豆乐园宝箱/金蛋进度 $usedQuota/$quotaLimit，自动执行【金豆对对碰/吃草草】上报补齐...")
                            try {
                                GameTask.GoldenBean_ddply.report(remainToTask)
                            } catch (e: Exception) {
                                Log.error(TAG, "GoldenBean_ddply report error: $e")
                            }
                            try {
                                GameTask.GoldenBean_nccmx.report(remainToTask)
                            } catch (e: Exception) {
                                Log.error(TAG, "GoldenBean_nccmx report error: $e")
                            }
                            delay(2000)
                            val refreshRes = queryCharityGameList()
                            quotaCanUse = refreshRes.optJSONObject("gameCenterDrawRights")?.optInt("quotaCanUse") ?: remainToTask
                        }

                        if (quotaCanUse > 0) {
                            val drawRes = drawCharityGameCenterAward(quotaCanUse)
                            if (drawRes.optBoolean("success") || drawRes.optString("desc") == "SUCCESS") {
                                val awardList = drawRes.optJSONArray("gameCenterDrawAwardList")
                                var totalEarned = 0
                                if (awardList != null) {
                                    for (k in 0 until awardList.length()) {
                                        val item = awardList.getJSONObject(k)
                                        totalEarned += item.optInt("awardCount", 0)
                                    }
                                }
                                Log.other(TAG, "金豆乐园砸蛋成功获得+$totalEarned 金豆")
                            }
                        }

                        if (usedQuota >= quotaLimit || remainToTask <= 0) {
                            Status.setFlagToday("goldBeanPark::gameFinished")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.error(TAG, "handleGoldBeanPark error: $e")
        }
    }

    // --- 金豆乐园 RPC 调方 ---

    private fun goldenBeanIndex(): JSONObject {
        val method = "com.alipay.goldenbean.index"
        val params = "[{\"bizType\":\"MASTER\",\"darwinSceneList\":[],\"source\":\"babafarm\",\"version\":\"20260723.01\"}]"
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun goldenBeanSync(syncTypeList: List<String>): JSONObject {
        val method = "com.alipay.goldenbean.sync"
        val syncTypeArr = JSONArray()
        for (item in syncTypeList) {
            syncTypeArr.put(item)
        }
        val req = JSONObject()
        req.put("bizType", "MASTER")
        req.put("source", "babafarm")
        req.put("syncTypeList", syncTypeArr)
        req.put("version", "20260723.01")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun goldenBeanSign(signKey: String): JSONObject {
        val method = "com.alipay.goldenbean.sign"
        val req = JSONObject()
        req.put("bizType", "MASTER")
        req.put("signKey", signKey)
        req.put("source", "babafarm")
        req.put("version", "20260723.01")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun goldenBeanFortuneDraw(): JSONObject {
        val method = "com.alipay.goldenbean.fortuneDraw"
        val req = JSONObject()
        req.put("bizType", "MASTER")
        req.put("source", "babafarm")
        req.put("version", "20260723.01")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun goldenBeanTrigger(taskId: String, triggerType: String): JSONObject {
        val method = "com.alipay.goldenbean.trigger"
        val req = JSONObject()
        req.put("bizType", "MASTER")
        req.put("source", "babafarm")
        req.put("taskId", taskId)
        req.put("triggerType", triggerType)
        req.put("version", "20260723.01")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun extractAwardBeanCount(res: JSONObject): Int {
        var count = res.optInt("beanDelta", res.optInt("incAwardCount", res.optInt("awardCount", res.optInt("awardAmount", res.optInt("amount", res.optInt("incBeanCount", 0))))))
        if (count == 0 && res.has("data")) {
            val data = res.optJSONObject("data")
            if (data != null) {
                count = data.optInt("beanDelta", data.optInt("incAwardCount", data.optInt("awardCount", data.optInt("awardAmount", data.optInt("amount", 0)))))
            }
        }
        return count
    }

    private fun finishTaskAntOrchard(taskType: String, userId: String, sceneCode: String = "GOLDEN_BEAN_MASTER_TASK"): JSONObject {
        val method = "com.alipay.antieptask.finishTaskantorchard"
        val req = JSONObject()
        req.put("bizType", "MASTER")
        req.put("finishBusinessInfo", JSONObject().put("bizType", "MASTER"))
        req.put("outBizNo", "$userId${System.currentTimeMillis()}")
        val targetScene = if (sceneCode.isEmpty() || sceneCode == "GOLDENBEAN") "GOLDEN_BEAN_MASTER_TASK" else sceneCode
        req.put("sceneCode", targetScene)
        req.put("source", "index_baping")
        req.put("taskType", taskType)
        req.put("version", "20260723.01")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun receiveTaskAwardAntOrchard(taskType: String, sceneCode: String = "GOLDEN_BEAN_MASTER_TASK"): JSONObject {
        val method = "com.alipay.antieptask.receiveTaskAwardantorchard"
        val req = JSONObject()
        req.put("bizInfo", JSONObject().put("bizType", "MASTER"))
        req.put("bizType", "MASTER")
        req.put("ignoreLimit", true)
        val targetScene = if (sceneCode.isEmpty() || sceneCode == "GOLDENBEAN") "GOLDEN_BEAN_MASTER_TASK" else sceneCode
        req.put("sceneCode", targetScene)
        req.put("source", "index_baping")
        req.put("taskType", taskType)
        req.put("version", "20260723.01")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun finishAdTask(bizId: String, extendInfo: JSONObject? = null): JSONObject {
        val method = "com.alipay.adtask.biz.mobilegw.service.task.finish"
        val req = JSONObject().apply { put("bizId", bizId) }
        if (extendInfo != null && extendInfo.length() > 0) {
            req.put("extendInfo", extendInfo)
        }
        return try {
            val params = JSONArray().put(req)
            JSONObject(RequestManager.requestString(method, params.toString()))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun queryCharityGameList(): JSONObject {
        val method = "com.alipay.charitygamecenter.queryGameList"
        val req = JSONObject()
        req.put("bizType", "GOLDENBEAN")
        val degrade = JSONObject()
        degrade.put("deviceLevel", "high")
        degrade.put("platform", "Android")
        degrade.put("unityDeviceLevel", "high")
        req.put("commonDegradeFilterRequest", degrade)
        req.put("requestType", "RPC")
        req.put("sceneCode", "GOLDENBEAN")
        req.put("source", "index_baping")
        req.put("version", "12.12.1.8000")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun drawCharityGameCenterAward(batchDrawCount: Int): JSONObject {
        val method = "com.alipay.charitygamecenter.drawGameCenterAward"
        val req = JSONObject()
        req.put("batchDrawCount", batchDrawCount)
        req.put("bizType", "GOLDENBEAN")
        req.put("requestType", "RPC")
        req.put("sceneCode", "GOLDENBEAN")
        req.put("source", "index_baping")
        req.put("version", "20260723.01")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun listTopItemsByScene(): JSONObject {
        val method = "com.alipay.antiep.listTopItemsByScene"
        val req = JSONObject()
        req.put("bizType", "MASTER")
        req.put("itemSceneList", JSONArray().put("OPERATION_STRATEGY"))
        req.put("requestType", "RPC")
        req.put("sceneCode", "ANTORCHARD_JINDOU_MALL")
        req.put("source", "MASTER")
        req.put("subChannel", "babafarm")
        req.put("version", "20260723.01")
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /**
     * 检查金豆乐园任务是否处于黑名单中（无法通过纯 RPC 完成的支付/理财/跳转/订阅类任务）
     */
    private fun isBlacklistedTask(
        taskId: String,
        taskType: String,
        actionType: String,
        type: String,
        title: String
    ): Boolean {
        // 1. 行为与类型黑名单 (外部跳转、支付、理财)
        if (actionType == "VISIT" || actionType == "JUMP_APP" || type.contains("APP") || 
            type == "XIANSHANGZHIFU" || type == "XIANXIAZHIFU" || type == "YUEBAO") {
            return true
        }

        // 2. 第三方合作与 App 跳转黑名单
        if (taskType.contains("KUAISHOU") || taskType.contains("TOUTIAO") ||
            taskId.contains("KUAISHOU") || taskId.contains("TOUTIAO")) {
            return true
        }

        // 3. 标题关键字黑名单 (已知非 RPC 任务: 肥料兑换, 首页添加, 消息提醒, 支付, 攒钱, 余额宝)
        val blackListKeywords = setOf("肥料", "首页", "提醒", "支付", "攒钱", "余额宝", "小游戏")
        return blackListKeywords.any { title.contains(it) }
    }
}
