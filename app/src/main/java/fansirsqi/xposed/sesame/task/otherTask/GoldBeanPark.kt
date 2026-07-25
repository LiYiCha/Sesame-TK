package fansirsqi.xposed.sesame.task.otherTask

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.task.antOrchard.GameTask
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
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
            val syncRes = goldenBeanSync(listOf("JAR_INFO", "SIGN", "MARKETING_POPUP", "TASK_LIST"))
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

            // 3. 拉取最新任务列表并执行
            val syncTaskRes = goldenBeanSync(listOf("JAR_INFO", "TASK_LIST"))
            val taskList = syncTaskRes.optJSONArray("taskList")
            if (taskList != null && taskList.length() > 0) {
                val userId = ApplicationHook.getUserId() ?: ""
                for (i in 0 until taskList.length()) {
                    val task = taskList.getJSONObject(i)
                    val taskId = task.optString("taskId")
                    val taskType = task.optString("taskType")
                    val taskStatus = task.optString("taskStatus")
                    val actionType = task.optString("actionType")
                    val taskSceneCode = task.optString("sceneCode", "GOLDENBEAN")
                    val displayConfig = task.optJSONObject("taskDisplayConfig")
                    val title = displayConfig?.optString("title") ?: taskId

                    // 已完成且已领取的任务跳过 (仅当已真正领取 DONE/RECEIVED 时跳过)
                    if (taskStatus == "DONE" || taskStatus == "RECEIVED") {
                        continue
                    }

                    // 1. 抽签任务
                    if (actionType == "FORTUNE_DRAW" || taskType == "FORTUNE_DRAW" || taskId == "FORTUNE_DRAW") {
                        if (!Status.hasFlagToday("goldBeanPark::fortuneDraw")) {
                            val drawRes = goldenBeanFortuneDraw()
                            if (drawRes.optBoolean("success")) {
                                Status.setFlagToday("goldBeanPark::fortuneDraw")
                                val incCount = extractAwardBeanCount(drawRes)
                                Log.other(TAG, "金豆抽签成功+$incCount 金豆")
                            } else {
                                Log.other(TAG, "金豆抽签: ${drawRes.optString("resultDesc", "完成")}")
                                Status.setFlagToday("goldBeanPark::fortuneDraw")
                            }
                        }
                        delay(1000 + (0..1000).random().toLong())
                        continue
                    }

                    // 2. 如果任务已在服务端处于完成待领取状态 (FINISHED / UNRECEIVE / TO_GET)
                    val isFinished = taskStatus == "FINISHED" || taskStatus == "FINISH" || taskStatus == "FINISHED_UNCLAIMED" || taskStatus == "TO_GET" || taskStatus == "UNRECEIVE"

                    // 如果任务在服务端处于未打卡状态 (taskStatus == "TODO")，处理纯 RPC/内部浏览搜索任务，跳过外部 APP 跳转任务 (KUAISHOU/TOUTIAO/DOWNLOAD) 及复杂合成游戏任务
                    if (!isFinished) {
                        val type = displayConfig?.optString("type") ?: ""
                        val triggerType = task.optString("triggerType", "TASK_COMPLETE")
                        
                        // 跳过外部 APP 跳转类任务
                        val isExternalAppTask = taskType.contains("KUAISHOU") || taskType.contains("TOUTIAO") || 
                                                taskId.contains("KUAISHOU") || taskId.contains("TOUTIAO") ||
                                                type.contains("APP") || actionType == "JUMP_APP"
                        if (isExternalAppTask) {
                            continue
                        }

                        // 3. 优先使用金豆乐园专属 RPC (com.alipay.goldenbean.trigger) 触发任务完成
                        val triggerRes = goldenBeanTrigger(taskId, if (triggerType.isEmpty()) "TASK_COMPLETE" else triggerType)
                        if (!triggerRes.optBoolean("success")) {
                            // 回退使用通用 Task 引擎触发
                            finishTaskAntOrchard(taskType, userId, taskSceneCode)
                        }
                        delay(1000 + (0..1000).random().toLong())
                    }

                    // 4. 统一领取任务奖励 (适用于刚打卡完成的搜索任务，以及已处于 FINISHED 待领取的对对碰/游戏任务)
                    var awardRes = receiveTaskAwardAntOrchard(taskType, taskSceneCode)
                    if (!awardRes.optBoolean("success") && taskSceneCode != "GOLDENBEAN") {
                        awardRes = receiveTaskAwardAntOrchard(taskType, "GOLDENBEAN")
                    }
                    if (awardRes.optBoolean("success")) {
                        val incCount = extractAwardBeanCount(awardRes)
                        Log.other(TAG, "完成任务[$title]+$incCount 金豆")
                    } else {
                        val desc = awardRes.optString("desc", awardRes.optString("resultDesc", "结果未知"))
                        Log.other(TAG, "领取任务[$title]: $desc")
                    }
                    delay(1000 + (0..1000).random().toLong())
                }
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
                                Log.other(TAG, "金豆乐园开宝箱/金蛋成功获得+$totalEarned 金豆")
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
        var count = res.optInt("incAwardCount", res.optInt("awardCount", res.optInt("awardAmount", res.optInt("amount", res.optInt("incBeanCount", 0)))))
        if (count == 0 && res.has("data")) {
            val data = res.optJSONObject("data")
            if (data != null) {
                count = data.optInt("incAwardCount", data.optInt("awardCount", data.optInt("awardAmount", data.optInt("amount", 0))))
            }
        }
        return count
    }

    private fun finishTaskAntOrchard(taskType: String, userId: String, sceneCode: String = "GOLDENBEAN"): JSONObject {
        val method = "com.alipay.antieptask.finishTaskantorchard"
        val req = JSONObject()
        req.put("bizType", "MASTER")
        req.put("finishBusinessInfo", JSONObject().put("bizType", "MASTER"))
        req.put("outBizNo", "$userId${System.currentTimeMillis()}")
        req.put("sceneCode", if (sceneCode.isEmpty()) "GOLDENBEAN" else sceneCode)
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

    private fun receiveTaskAwardAntOrchard(taskType: String, sceneCode: String = "GOLDENBEAN"): JSONObject {
        val method = "com.alipay.antieptask.receiveTaskAwardantorchard"
        val req = JSONObject()
        req.put("bizInfo", JSONObject().put("bizType", "MASTER"))
        req.put("bizType", "MASTER")
        req.put("ignoreLimit", true)
        req.put("sceneCode", if (sceneCode.isEmpty()) "GOLDENBEAN" else sceneCode)
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
}
