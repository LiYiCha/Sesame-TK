package fansirsqi.xposed.sesame.task.otherTask

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.task.antOrchard.GameTask
import fansirsqi.xposed.sesame.task.common.GameCenterPlayRpcCall
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 金豆夺宝配置
 *
 * 农场版与炼金版金豆夺宝的接口结构完全相同，仅参数不同，
 * 通过替换 bizType/source/version/sceneCode 即可复用同一套任务流程
 */
data class BeanScene(
    val bizType: String,
    val sourceMain: String,
    val sourceTask: String,
    val version: String,
    val defaultSceneCode: String,
    val indexDarwinSceneList: String,
    val flagPrefix: String
) {
    companion object {
        /** 农场版金豆夺宝（芭芭农场入口） */
        val FARM = BeanScene(
            bizType = "MASTER",
            sourceMain = "babafarm",
            sourceTask = "index_baping",
            version = "20260901.01",
            defaultSceneCode = "GOLDEN_BEAN_MASTER_TASK",
            indexDarwinSceneList = "[\"indexLayoutTwo\",\"taskFlowHandGuide\",\"indexLoadingOptimization\"]",
            flagPrefix = "goldBeanPark"
        )

        /** 炼金版金豆夺宝（芝麻炼金入口） */
        val ZHIMA = BeanScene(
            bizType = "ZHIMA",
            sourceMain = "lianjin",
            sourceTask = "lianjin",
            version = "20260901.01",
            defaultSceneCode = "GOLDEN_BEAN_ZHIMA_LIST",
            indexDarwinSceneList = "[\"indexLayoutTwo\",\"taskFlowHandGuide\",\"indexLoadingOptimization\"]",
            flagPrefix = "alchemyGoldenBean"
        )
    }
}

/**
 * 金豆夺宝 🎡
 *
 * @param manureExchangeAmount 农场版：肥料换豆量（-1 全换，0 关闭，>0 按配置量）
 * @param sesameExchangeAmount 炼金版：芝麻粒换豆量（-1 全换，0 关闭，>0 按配置量），与农场版互不影响
 * @param scene 金豆场景（农场版/炼金版），决定全部 RPC 的 bizType/source/version/sceneCode
 */
class GoldBeanPark @JvmOverloads constructor(
    private val manureExchangeAmount: Int = -1,
    private val sesameExchangeAmount: Int = 0,
    private val scene: BeanScene = BeanScene.FARM
) {
    private val TAG = "金豆夺宝🎡"

    companion object {
        private const val THEMES_FOLDER = "themes"
        private const val MINER_SOURCE = "ch_url-https://render.alipay.com/p/yuyan/180020010001291350/index.html"

        /** 炼金版入口：供芝麻炼金模块调用，做签到、抽财运签、换量任务及芝麻粒换金豆 */
        fun forAlchemy(exchangeAmount: Int = 0): GoldBeanPark = GoldBeanPark(sesameExchangeAmount = exchangeAmount, scene = BeanScene.ZHIMA)
    }

    private val fullSyncTypes = listOf(
        "JAR_INFO", "SIGN", "MARKETING_POPUP", "TASK_LIST", "FORTUNE_DRAW",
        "EXCHANGE_MANURE", "FARM_TASK", "GAME_CENTER_FOR_INDEX", "DRAINAGE", "SPROUT_INFO"
    )
    private val taskSyncTypes = listOf(
        "JAR_INFO", "TASK_LIST", "FORTUNE_DRAW", "EXCHANGE_MANURE", "FARM_TASK",
        "GAME_CENTER_FOR_INDEX", "DRAINAGE", "SPROUT_INFO"
    )

    fun run() {
        val hour = TimeUtil.getHourOfDay()
        if (hour < 7 || Status.hasFlagToday("${scene.flagPrefix}::allTask")) {
            return
        }
        // 通过 GlobalThreadPools 执行：纳入统一追踪，"停止运行"时随 cancelAll 一并取消
        GlobalThreadPools.execute {
            try {
                handleGoldBeanPark()
            } catch (e: Exception) {
                Log.error(TAG, "handleGoldBeanPark error: $e")
            }
        }
    }

    private suspend fun handleGoldBeanPark() {
        try {
            // 1. 首页初始化与数据同步
            val indexRes = goldenBeanIndex()
            handleMarketingPopup(indexRes)
            if (scene == BeanScene.FARM) {
                listTopItemsByScene()
            }
            val syncRes = goldenBeanSync(fullSyncTypes)
            handleMarketingPopup(syncRes)
            if (!syncRes.optBoolean("success", true) && syncRes.has("resultDesc")) {
                Log.error(TAG, "金豆同步异常: ${syncRes.optString("resultDesc")}")
            }

            // 2. 金豆签到
            doSign()

            // 3. 任务处理：每完成/领取一个任务就重新 sync，模拟真实操作节奏
            doTaskLoop()

            // 以下为农场版专属（炼金版金豆页无对应接口语义）
            if (scene != BeanScene.FARM) {
                Status.setFlagToday("${scene.flagPrefix}::allTask")
                return
            }

            // 4. 金豆对对碰游戏自动上报与开金蛋/开宝箱 (charitygamecenter)
            if (!Status.hasFlagToday("${scene.flagPrefix}::gameFinished")) {
                val gameListRes = queryCharityGameList()
                if (gameListRes.optBoolean("success") || gameListRes.optString("desc") == "SUCCESS") {
                    val drawRights = gameListRes.optJSONObject("gameCenterDrawRights")
                    if (drawRights != null) {
                        var quotaCanUse = drawRights.optInt("quotaCanUse", 0)
                        val quotaLimit = drawRights.optInt("quotaLimit", 20)
                        val usedQuota = drawRights.optInt("usedQuota", 0)

                        val remainToTask = quotaLimit - usedQuota
                        if (remainToTask > 0 && quotaCanUse < remainToTask) {
                            Log.other(TAG, "金豆夺宝宝箱/金蛋进度 $usedQuota/$quotaLimit，自动执行【金豆对对碰/吃草草】上报补齐...")
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
                                Log.other(TAG, "金豆夺宝砸蛋成功获得+$totalEarned 金豆")
                            }
                        }

                        if (usedQuota >= quotaLimit || remainToTask <= 0) {
                            Status.setFlagToday("${scene.flagPrefix}::gameFinished")
                        }
                    }
                }
            }

            // 4.1 小游戏完成后二次回查任务，自动领取因对对碰小游戏完成而达标的任务奖励
            doTaskLoop()

            // 5. 肥料换豆（农场版）
            handleExchange()

            // 6. 金猫矿工
            handleMiner()

            // 标记农场版任务顺利完成
            Status.setFlagToday("${scene.flagPrefix}::allTask")

            // 7. 炼金版金豆夺宝（签到、抽签、换量任务及芝麻粒换金豆）
            if (sesameExchangeAmount != 0 || !Status.hasFlagToday("${BeanScene.ZHIMA.flagPrefix}::allTask")) {
                runCatching {
                    forAlchemy(sesameExchangeAmount).runAlchemyBeanTasks()
                }.onFailure {
                    Log.error(TAG, "炼金版金豆夺宝执行异常: $it")
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "handleGoldBeanPark error: $e")
        }
    }

    /**
     * 营销弹窗任务自动触发与领奖
     */
    private suspend fun handleMarketingPopup(response: JSONObject) {
        val marketingTask = response.optJSONObject("marketingPopupTask") ?: return
        val taskId = marketingTask.optString("taskId").trim()
        val triggerType = marketingTask.optString("triggerType").trim().ifBlank { "MARKETING_POPUP_CLICKED" }
        if (taskId.isBlank()) return
        try {
            val triggerRes = goldenBeanTrigger(taskId, triggerType)
            if (triggerRes.optBoolean("success", true) || triggerRes.optString("resultCode") in setOf("100", "SUCCESS")) {
                val beanCount = extractAwardBeanCount(triggerRes)
                if (beanCount > 0) {
                    Log.other(TAG, "金豆营销弹窗领奖成功: +$beanCount 金豆 (taskId=$taskId)")
                } else {
                    Log.other(TAG, "金豆营销弹窗触发完成: taskId=$taskId")
                }
                delay(500)
                goldenBeanSync(listOf("MARKETING_POPUP"))
            }
        } catch (e: Exception) {
            Log.error(TAG, "handleMarketingPopup error: $e")
        }
    }

    /**
     * 炼金版入口：做签到、抽财运签、换量任务及芝麻粒换金豆，
     * 不含农场版专属的对对碰/金猫矿工
     */
    suspend fun runAlchemyBeanTasks() {
        if (Status.hasFlagToday("${scene.flagPrefix}::allTask")) {
            return
        }
        try {
            val indexRes = goldenBeanIndex()
            handleMarketingPopup(indexRes)
            val syncRes = goldenBeanSync(fullSyncTypes)
            handleMarketingPopup(syncRes)
            doSign()
            doTaskLoop()
            handleExchange()
            Status.setFlagToday("${scene.flagPrefix}::allTask")
        } catch (e: Exception) {
            Log.error(TAG, "runAlchemyBeanTasks error: $e")
        }
    }

    private suspend fun doSign() {
        if (Status.hasFlagToday("${scene.flagPrefix}::sign")) return
        try {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val signRes = goldenBeanSign(todayStr)
            if (signRes.optBoolean("success")) {
                Status.setFlagToday("${scene.flagPrefix}::sign")
                val incCount = extractAwardBeanCount(signRes)
                if (incCount > 0) {
                    Log.other(TAG, "金豆签到成功+$incCount 金豆")
                } else {
                    Log.other(TAG, "金豆签到: ${signRes.optString("desc", signRes.optString("resultDesc", "成功"))}")
                }
            } else {
                val desc = signRes.optString("resultDesc", signRes.optString("desc", ""))
                if (desc.contains("已签") || desc.contains("重复") || desc.contains("签过")) {
                    Status.setFlagToday("${scene.flagPrefix}::sign")
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "doSign error: $e")
        }
    }

    private suspend fun doTaskLoop() {
        try {
            while (true) {
                // ── 拉取最新任务列表 ──
                val syncTaskRes = goldenBeanSync(taskSyncTypes)
                val taskList = syncTaskRes.optJSONArray("taskList")
                if (taskList == null || taskList.length() == 0) break

                var hasWorkDone = false

                for (i in 0 until taskList.length()) {
                    val task = taskList.getJSONObject(i)
                    val taskId = task.optString("taskId")
                    val taskType = task.optString("taskType").ifEmpty { taskId }
                    val taskStatus = task.optString("taskStatus")
                    val actionType = task.optString("actionType")
                    val taskSceneCode = task.optString("sceneCode", scene.defaultSceneCode)
                    val displayConfig = task.optJSONObject("taskDisplayConfig")
                    val title = displayConfig?.optString("title") ?: taskId
                    val type = displayConfig?.optString("type") ?: ""

                    // 已完成且已领取的任务跳过
                    if (taskStatus == "DONE" || taskStatus == "RECEIVED") continue

                    // 1. 抽签任务（一次性，不需 re-sync）
                    if (actionType == "FORTUNE_DRAW" || taskType.contains("FORTUNE_DRAW") || taskId.contains("FORTUNE_DRAW")) {
                        if (!Status.hasFlagToday("${scene.flagPrefix}::fortuneDraw")) {
                            val drawRes = goldenBeanFortuneDraw(taskId)
                            if (drawRes.optBoolean("success")) {
                                Status.setFlagToday("${scene.flagPrefix}::fortuneDraw")
                                val stickName = drawRes.optJSONObject("fortuneStick")?.optString("name")
                                val tagMsg = if (!stickName.isNullOrEmpty()) "[${stickName}签]" else ""
                                val incCount = extractAwardBeanCount(drawRes)
                                Log.other(TAG, "金豆抽签成功$tagMsg+$incCount 金豆")
                            } else {
                                Log.other(TAG, "金豆抽签: ${drawRes.optString("resultDesc", "完成")}")
                                Status.setFlagToday("${scene.flagPrefix}::fortuneDraw")
                            }
                        }
                        continue
                    }

                    // 2. 待领奖状态 (FINISHED)：领奖后 re-sync
                    if (taskStatus == "FINISHED") {
                        var awardRes = receiveTaskAwardAntOrchard(taskType, taskSceneCode)
                        if (!awardRes.optBoolean("success") && taskSceneCode != scene.defaultSceneCode) {
                            awardRes = receiveTaskAwardAntOrchard(taskType, scene.defaultSceneCode)
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

                        val gameContract = GameCenterPlayRpcCall.resolveContract(task, displayConfig)
                        if (gameContract != null) {
                            Log.other(TAG, "检测到小游戏任务[$title]，开始上报时长(${gameContract.playTime}s)...")
                            val ack = GameCenterPlayRpcCall.submitForAck(gameContract)
                            if (ack.accepted) {
                                Log.other(TAG, "小游戏时长上报成功[$title]，准备完成任务")
                            } else {
                                Log.other(TAG, "小游戏时长上报响应[$title]: ${ack.raw}，尝试继续完成")
                            }
                            delay(1500 + (0..500).random().toLong())
                        }

                        val userId = UserMap.currentUid ?: ""
                        val finishRes = finishTaskAntOrchard(taskType, userId, taskSceneCode)

                        if (finishRes.optBoolean("success")) {
                            delay(1000 + (0..1000).random().toLong())
                            var awardRes = receiveTaskAwardAntOrchard(taskType, taskSceneCode)
                            if (!awardRes.optBoolean("success") && taskSceneCode != scene.defaultSceneCode) {
                                awardRes = receiveTaskAwardAntOrchard(taskType, scene.defaultSceneCode)
                            }
                            if (awardRes.optBoolean("success")) {
                                val incCount = extractAwardBeanCount(awardRes)
                                Log.other(TAG, "领取任务[$title]+$incCount 金豆")
                                hasWorkDone = true
                                break
                            } else {
                                val errorMsg = awardRes.optString("errorMsg", awardRes.optString("desc", awardRes.optString("resultDesc", awardRes.toString())))
                                Log.error(TAG, "任务领奖失败[$title]: $errorMsg")
                            }
                        } else {
                            val desc = finishRes.optString("desc", finishRes.optString("resultDesc", ""))
                            if (desc.isNotEmpty()) {
                                Log.error(TAG, "完成任务[$title]响应: $desc")
                            }
                        }
                        delay(2000 + (0..1000).random().toLong())
                        continue
                    }
                }

                // 本轮未处理任何任务，退出 while
                if (!hasWorkDone) break
            }
        } catch (e: Exception) {
            Log.error(TAG, "doTaskLoop error: $e")
        }
    }

    // --- 金豆夺宝 RPC 调方 ---

    private fun goldenBeanIndex(): JSONObject {
        val method = "com.alipay.goldenbean.index"
        val req = JSONObject()
        req.put("bizType", scene.bizType)
        req.put("darwinSceneList", JSONArray(scene.indexDarwinSceneList))
        req.put("source", scene.sourceMain)
        req.put("version", scene.version)
        val params = JSONArray().put(req).toString()
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
        req.put("bizType", scene.bizType)
        req.put("source", scene.sourceMain)
        req.put("syncTypeList", syncTypeArr)
        req.put("version", scene.version)
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
        req.put("bizType", scene.bizType)
        req.put("signKey", signKey)
        req.put("source", scene.sourceMain)
        req.put("version", scene.version)
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun goldenBeanFortuneDraw(taskId: String = ""): JSONObject {
        val method = "com.alipay.goldenbean.fortuneDraw"
        val req = JSONObject()
        req.put("bizType", scene.bizType)
        req.put("source", scene.sourceMain)
        if (taskId.isNotBlank()) {
            req.put("taskId", taskId)
        }
        req.put("version", scene.version)
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
        req.put("bizType", scene.bizType)
        req.put("source", scene.sourceMain)
        req.put("taskId", taskId)
        req.put("triggerType", triggerType)
        req.put("version", scene.version)
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun extractAwardBeanCount(res: JSONObject): Int {
        var count = res.optInt("beanDelta", res.optInt("incAwardCount", res.optInt("awardCount", res.optInt("awardAmount", res.optInt("amount", res.optInt("incBeanCount", 0))))))
        if (count == 0 && res.has("awardInfo")) {
            val awardInfo = res.optJSONObject("awardInfo")
            if (awardInfo != null) {
                count = awardInfo.optInt("deltaAwardCount", awardInfo.optInt("totalAwardCount", 0))
            }
        }
        if (count == 0 && res.has("data")) {
            val data = res.optJSONObject("data")
            if (data != null) {
                count = data.optInt("beanDelta", data.optInt("incAwardCount", data.optInt("awardCount", data.optInt("awardAmount", data.optInt("amount", 0)))))
            }
        }
        return count
    }

    private fun finishTaskAntOrchard(taskType: String, userId: String, sceneCode: String = scene.defaultSceneCode): JSONObject {
        val method = "com.alipay.antieptask.finishTaskantorchard"
        val req = JSONObject()
        req.put("bizType", scene.bizType)
        req.put("finishBusinessInfo", JSONObject().put("bizType", scene.bizType))
        req.put("outBizNo", "$userId${System.currentTimeMillis()}")
        val targetScene = if (sceneCode.isEmpty() || sceneCode == "GOLDENBEAN") scene.defaultSceneCode else sceneCode
        req.put("sceneCode", targetScene)
        req.put("source", scene.sourceTask)
        req.put("taskType", taskType)
        req.put("version", scene.version)
        val params = JSONArray().put(req).toString()
        return try {
            JSONObject(RequestManager.requestString(method, params))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun receiveTaskAwardAntOrchard(taskType: String, sceneCode: String = scene.defaultSceneCode): JSONObject {
        val method = "com.alipay.antieptask.receiveTaskAwardantorchard"
        val req = JSONObject()
        req.put("bizInfo", JSONObject().put("bizType", scene.bizType))
        req.put("bizType", scene.bizType)
        req.put("ignoreLimit", true)
        val targetScene = if (sceneCode.isEmpty() || sceneCode == "GOLDENBEAN") scene.defaultSceneCode else sceneCode
        req.put("sceneCode", targetScene)
        req.put("source", scene.sourceTask)
        req.put("taskType", taskType)
        req.put("version", scene.version)
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
     * 检查金豆夺宝任务是否处于黑名单中（无法通过纯 RPC 完成的支付/理财/跳转/订阅类任务）
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
        val blackListKeywords = setOf("肥料", "首页", "提醒", "支付", "攒钱", "余额宝")
        return blackListKeywords.any { title.contains(it) }
    }

    // --- 资产换金豆（农场肥料换豆 / 芝麻粒换豆） ---

    private suspend fun handleExchange() {
        // 农场版（肥料）与炼金版（芝麻粒）使用各自独立的换豆量字段，互不影响
        val configuredAmount = if (scene == BeanScene.ZHIMA) sesameExchangeAmount else manureExchangeAmount
        if (configuredAmount == 0 || Status.hasFlagToday("${scene.flagPrefix}::exchange")) return
        try {
            val indexRes = goldenBeanIndex()
            val exchangeInfo = indexRes.optJSONObject("manureExchangeInfo") ?: return
            val pageOpened = exchangeInfo.optBoolean("pageOpened")
            if (!pageOpened) return

            // 农场版专有校验：需同时开通农场并绑定淘宝
            if (scene == BeanScene.FARM) {
                val farmOpened = exchangeInfo.optBoolean("farmOpened")
                val taobaoBinding = exchangeInfo.optBoolean("taobaoBinding")
                if (!farmOpened || !taobaoBinding) return
            }

            val currentManure = exchangeInfo.optInt("currentManure", 0)
            val minExchangeAmount = exchangeInfo.optInt("minExchangeAmount", 80)
            val remainQuota = exchangeInfo.optInt("remainQuota", 0)
            val manurePrice = exchangeInfo.optInt("manurePrice", 1)
            val beanReward = exchangeInfo.optInt("beanReward", 80)

            if (remainQuota <= 0 || currentManure <= 0) return

            // 计算当前资产最多可兑换的金豆数
            val maxCanExchangeBeans = if (manurePrice > 0 && beanReward > 0) {
                (currentManure / manurePrice) * beanReward
            } else {
                currentManure
            }
            if (maxCanExchangeBeans < minExchangeAmount) return

            // -1 全换（受每日配额与资产上限限制），>0 按配置量兑换
            var toExchange = if (configuredAmount == -1) {
                minOf(maxCanExchangeBeans, remainQuota)
            } else {
                minOf(configuredAmount, minOf(maxCanExchangeBeans, remainQuota))
            }

            // 按单次步长 beanReward 向下对齐整倍数
            if (beanReward > 0) {
                toExchange = (toExchange / beanReward) * beanReward
            }
            if (toExchange < minExchangeAmount) return

            val assetName = if (scene == BeanScene.ZHIMA) "芝麻粒" else "肥料"
            val costAsset = if (beanReward > 0) (toExchange / beanReward) * manurePrice else toExchange

            goldenBeanSync(listOf("JAR_INFO", "EXCHANGE_MANURE", "TASK_LIST"))
            val exchangeRes = goldenBeanManureExchange(toExchange)
            if (!exchangeRes.optBoolean("success", true)) {
                Log.error(TAG, "$assetName 换金豆失败: ${exchangeRes.optString("resultDesc", exchangeRes.optString("desc", "未知错误"))}")
                return
            }
            delay(1000)
            val afterRes = goldenBeanSync(listOf("JAR_INFO", "EXCHANGE_MANURE", "TASK_LIST"))

            val afterInfo = afterRes.optJSONObject("manureExchangeInfo")
            val afterManure = afterInfo?.optInt("currentManure", -1) ?: exchangeRes.optInt("remainManure", -1)
            val afterQuota = afterInfo?.optInt("remainQuota", -1) ?: exchangeRes.optInt("remainQuota", -1)
            Log.other(TAG, "$assetName 换金豆成功: 消耗 $costAsset $assetName，获得 +$toExchange 金豆 (剩余$assetName: $afterManure，剩余配额: $afterQuota)")
            Status.setFlagToday("${scene.flagPrefix}::exchange")
        } catch (e: Exception) {
            Log.error(TAG, "handleExchange error: $e")
        }
    }

    // --- 金猫矿工 ---

    private suspend fun handleMiner() {
        try {
            val indexRes = goldenBeanMinerIndex()
            if (!indexRes.optBoolean("enabled", false)) return
            val minerInfo = indexRes.optJSONObject("minerInfo") ?: return
            val taskProgress = minerInfo.optJSONObject("taskProgress") ?: return
            if (!taskProgress.optBoolean("canGrab", false)) return

            // 已抓取的 itemId
            val grabbedItemIds = mutableSetOf<String>()
            val progress = minerInfo.optJSONObject("progress")
            val alreadyGrabbed = progress?.optJSONArray("grabbedItemIds") ?: JSONArray()
            for (i in 0 until alreadyGrabbed.length()) {
                alreadyGrabbed.optString(i).takeIf { it.isNotBlank() }?.let(grabbedItemIds::add)
            }
            // 可抓取的 BEAN 类 itemId
            val beanItemIds = mutableListOf<String>()
            val items = minerInfo.optJSONObject("currentLevel")?.optJSONArray("items") ?: return
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val itemId = item.optString("itemId").trim()
                if (item.optString("type") == "BEAN" && itemId.isNotBlank() && itemId !in grabbedItemIds) {
                    beanItemIds.add(itemId)
                }
            }

            var candidateIndex = 0
            var remainingTimes = taskProgress.optInt("remainingTimes", 0)
            var canGrab = taskProgress.optBoolean("canGrab", false)
            while (canGrab && remainingTimes > 0) {
                val itemId = beanItemIds.getOrNull(candidateIndex)
                val expectedResult = if (itemId.isNullOrBlank()) "EMPTY" else "BEAN"
                val grabRes = goldenBeanMinerGrab(expectedResult, itemId.orEmpty())
                if (!grabRes.optBoolean("success", true)) {
                    Log.error(TAG, "金猫矿工抓取失败: ${grabRes.optString("resultDesc", "未知错误")}")
                    return
                }
                if (grabRes.optBoolean("needAd", false)) {
                    Log.other(TAG, "金猫矿工[服务端要求广告，跳过]")
                    return
                }
                // 抓取后回查金豆罐状态
                delay(300)
                goldenBeanSync(listOf("JAR_INFO"))
                if (expectedResult == "BEAN") candidateIndex++
                val updatedProgress = grabRes.optJSONObject("taskProgress") ?: return
                val updatedRemaining = updatedProgress.optInt("remainingTimes", remainingTimes)
                if (updatedRemaining >= remainingTimes) return
                remainingTimes = updatedRemaining
                canGrab = updatedProgress.optBoolean("canGrab", false)
                Log.other(TAG, "金猫矿工抓取成功 remainingTimes=$remainingTimes canGrab=$canGrab")
            }
            // 最终回查
            val finalRes = goldenBeanMinerIndex()
            val finalProgress = finalRes.optJSONObject("minerInfo")?.optJSONObject("taskProgress")
            if (finalProgress != null) {
                Log.other(TAG, "金猫矿工完成 canGrab=${finalProgress.optBoolean("canGrab", false)} remainingTimes=${finalProgress.optInt("remainingTimes", -1)}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "handleMiner error: $e")
        }
    }

    // --- 新增 RPC 方法 ---

    private fun goldenBeanManureExchange(exchangeBeanAmount: Int): JSONObject {
        val method = "com.alipay.goldenbean.manureExchange"
        val req = JSONObject()
        req.put("bizType", scene.bizType)
        req.put("exchangeBeanAmount", exchangeBeanAmount)
        req.put("source", scene.sourceMain)
        req.put("version", scene.version)
        return try {
            JSONObject(RequestManager.requestString(method, JSONArray().put(req).toString()))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun goldenBeanMinerIndex(): JSONObject {
        val method = "com.alipay.goldenbean.miner.index"
        val req = JSONObject()
        req.put("bizType", scene.bizType)
        req.put("source", MINER_SOURCE)
        req.put("version", scene.version)
        return try {
            JSONObject(RequestManager.requestString(method, JSONArray().put(req).toString()))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun goldenBeanMinerGrab(
        grabResult: String,
        itemId: String = ""
    ): JSONObject {
        val method = "com.alipay.goldenbean.miner.grab"
        val req = JSONObject()
        req.put("bizType", scene.bizType)
        req.put("grabId", java.util.UUID.randomUUID().toString())
        req.put("grabResult", grabResult)
        if (itemId.isNotBlank()) {
            req.put("itemId", itemId)
        }
        req.put("source", MINER_SOURCE)
        req.put("version", scene.version)
        return try {
            JSONObject(RequestManager.requestString(method, JSONArray().put(req).toString()))
        } catch (e: Exception) {
            JSONObject()
        }
    }
}
