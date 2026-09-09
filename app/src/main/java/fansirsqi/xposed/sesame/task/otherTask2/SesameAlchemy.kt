package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.task.otherTask2.AntMemberRpcCall
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.GlobalThreadPools.sleepCompat
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SesameAlchemy {
    companion object {
        private val executionLock = ReentrantLock()
        @Volatile
        private var isRunning = false
    }
    private val TAG = "⚗️芝麻炼金"
    private var skipTaskList = hashSetOf<String>(
                "去AQApp对话一次",
                "坚持签到领奖励",
                "每日施肥领水果",
                "芝麻租赁下单得芝麻粒",
        )
    private val version = "2025-10-22" //版本号

    /** 任务连续失败计数: title -> count */
    private val failureCountMap = mutableMapOf<String, Int>()

    /** 可重试的错误模式（网络/服务端暂时不可用，不应计入失败） */
    private val retryableErrorPatterns = listOf(
        "REMOTE_INVOKE_EXCEPTION", "SYSTEM_ERROR", "system error",
        "系统繁忙", "稍后重试", "网络异常", "timeout", "TIMEOUT",
        "RPC_FAILED", "rpc failed", "服务不可用",
    )
    fun run(){
        // 防止并发执行
        if (isRunning) {
            Log.runtime(TAG, "任务正在执行中，跳过本次执行")
            return
        }

        executionLock.withLock {
            if (isRunning) {
                Log.runtime(TAG, "任务已在执行中，跳过本次执行")
                return
            }

            isRunning = true
            try {
                // 执行逻辑
                init() //初始化
                initBlackTaskList() //初始化黑名单
                initFailureCount() //加载失败计数
                querySignIn() //签到
                claimNextDayAward() //领取次日奖励
                doHomeTask() //首页任务
                getAdTask() //芝麻信用广告任务
                queryFanBao() //饭补
                alchemyExecute() //自动炼金（内含检查/任务获取体力）
                openTreasureBox() //开启炼金宝箱（内含得到更多任务与二次开箱）
                handleTask() //处理剩余日常任务
                collectAlchemyCredit() //一键收取芝麻粒
            } catch (e: Exception) {
                Log.error(TAG, "执行过程中发生异常: $e")
            } finally {
                isRunning = false
            }
        }
    }


    //全局初始化黑名单
    private fun initBlackTaskList() {
        val storedBlackList = DataStore.get("blackTaskList_SesameAlchemy", Set::class.java) as? Set<String>
        if (storedBlackList != null) {
            skipTaskList.addAll(storedBlackList)
        }
    }
    //处理任务
    fun handleTask(){
        try {
            queryTaskLists()
            queryCollectTask()
        }catch (e: Exception){
            Log.error(TAG, "handleTask: $e")
        }
    }

    private fun alchemyExecute(){

        // ================= Step 1: 自动炼金 (消耗芝麻粒升级) =================
        val homeRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryHome()
        val homeJo = JSONObject(homeRes)
        if (homeJo.optBoolean("success")) {
            val data = homeJo.optJSONObject("data")
            if (data != null) {
                val staminaStatus = data.optString("staminaStatus", "")
                val staminaCurrent = data.optInt("staminaCurrent", 0)
                val hasBottleQuota = data.optBoolean("hasBottleQuota", true)
                if (staminaStatus == "EXHAUSTED" || staminaCurrent == 0) {
                    val ok = ensureStamina(hasBottleQuota)
                    if (!ok) {
                        Log.other("芝麻炼金⚗️体力已耗尽且无法恢复，结束本次炼金")
                        return
                    }
                }
                var zmlBalance = data.optInt("zmlBalance", 0) // 当前芝麻粒
                val cost = data.optInt("alchemyCostZml", 5) // 单次消耗
                var capReached = data.optBoolean("capReached", false) // 是否达到上限
                var currentLevel = data.optInt("currentLevel", 0)
                var freeAlchemyNum = data.optInt("freeAlchemyNum", 0) // 免费炼金次数
                var paidAlchemyCount = 0

                // 计算安全上限：免费次数 + 芝麻粒可支撑的次数
                val maxAttempts = freeAlchemyNum + (if (capReached) 0 else zmlBalance / cost)
                var attemptCount = 0

                // 循环炼金逻辑
                while (attemptCount < maxAttempts && !capReached) {
                    sleepCompat(1500)
                    val alchemyRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyExecute()
                    val alchemyJo = JSONObject(alchemyRes)
                    attemptCount++

                    if (alchemyJo.optBoolean("success")) {
                        val alData = alchemyJo.optJSONObject("data")
                        if (alData != null) {
                            val levelUp = alData.optBoolean("levelUp", false)
                            val levelFull = alData.optBoolean("levelFull", false)
                            val goldNum = alData.optInt("goldNum", 0)

                            if (levelUp) currentLevel++
                            if (levelFull) capReached = true

                            Log.other(
                                "芝麻炼金⚗️[炼金成功]" +
                                        "#消耗" + (if (freeAlchemyNum > 0) "免费" else cost.toString() + "粒") +
                                        " | 获得" + goldNum + "金" +
                                        " | 当前等级Lv." + currentLevel +
                                        (if (levelUp) "（升级🎉）" else "") +
                                        (if (levelFull) "（满级🏆）" else "")
                            )

                            if (freeAlchemyNum > 0) {
                                freeAlchemyNum--
                            } else {
                                zmlBalance -= cost
                                paidAlchemyCount++
                            }

                            // 满级红包提现
                            val roundStatus = alData.optString("roundStatus", "")
                            if (roundStatus == "WAIT_WITHDRAW") {
                                handleAlchemyWithdraw()
                            }
                        } else {
                            break
                        }
                    } else {
                        val resultView = alchemyJo.optString("resultView", "")
                        val resultCode = alchemyJo.optString("resultCode", "")
                        if (resultView.contains("CAP_REACHED") || resultView.contains("CURRENT_LEVEL_MAX")) {
                            capReached = true
                            Log.other("芝麻炼金⚗️已达上限")
                        } else if (resultView.contains("体力") || resultCode.contains("STAMINA") || resultCode.contains("EXHAUSTED")) {
                            Log.other("芝麻炼金⚗️炼金过程中体力耗尽，尝试恢复体力...")
                            val recovered = ensureStamina(hasBottleQuota = true)
                            if (recovered) {
                                sleepCompat(1500)
                                continue // 体力恢复成功，继续炼金
                            } else {
                                Log.other("芝麻炼金⚗️体力恢复失败或无可用配额，退出炼金")
                                break
                            }
                        } else {
                            Log.runtime(TAG, "芝麻炼金失败: $resultView")
                            break
                        }
                    }
                }
                if (capReached) {
                    Status.setFlagToday("alchemyCapReached")
                }
            }
        } else {
            Log.runtime(TAG, "芝麻炼金首页查询失败")
        }
    }

    /** 满级红包提现 */
    private fun handleAlchemyWithdraw() {
        try {
            if (Status.hasFlagToday("alchemyWithdraw")) return
            val method = "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.withdraw"
            val params = "[{}]"
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")) {
                val amount = result.optJSONObject("data")?.optString("amount", "") ?: ""
                Log.other("芝麻炼金⚗️满级红包提现成功" + (if (amount.isNotEmpty()) " +$amount" else ""))
                Status.setFlagToday("alchemyWithdraw")
            } else {
                Log.error(TAG, "满级红包提现失败: $result")
            }
        } catch (e: Exception) {
            Log.error(TAG, "handleAlchemyWithdraw: $e")
        }
    }


    //芝麻炼金
    //初始化
    private fun init(){
        try {
            // 初始化任务列表？
            AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryCheckIn("alchemy")
            // 查询饭补
            AntMemberRpcCall.Zmxy.Alchemy.queryTimeLimitedTask()
            sleepCompat(1000)
            // 查询主页
            AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryHome()
            sleepCompat(1000)
            // queryCreditFeedback
            AntMemberRpcCall.Zmxy.Alchemy.queryCreditFeedback()
            // 查询上次操作任务
            AntMemberRpcCall.Zmxy.Alchemy.queryLastOperateTask()
            sleepCompat(1000)
            // queryEntryList
            AntMemberRpcCall.Zmxy.Alchemy.queryEntryList(version)
            sleepCompat(1000)
        }catch (e: Exception){
            Log.error(TAG, "init: $e")
        }
    }

    private fun collectTask(){
        try {
            val result = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.collectCreditFeedback())
            if (result.optBoolean("success")){
               Log.other("$TAG 一键领取成功")
            }else{
                Log.error(TAG, "一键领取失败: $result")
            }
        }catch (e: Exception){
            Log.error(TAG, "collectTask: $e")
        }
    }
    /** 芝麻炼金-领取次日奖励 */
    private fun claimNextDayAward() {
        try {
            if (Status.hasFlagToday("SesameAlchemy_NextDayAward")) return
            val awardRes = AntMemberRpcCall.Zmxy.Alchemy.claimAward()
            val jo = JSONObject(awardRes)
            if (jo.optBoolean("success")) {
                val data = jo.optJSONObject("data")
                var gotNum = 0
                if (data != null) {
                    val arr = data.optJSONArray("alchemyAwardSendResultVOS")
                    if (arr != null && arr.length() > 0) {
                        gotNum = arr.optJSONObject(0)?.optInt("pointNum", 0) ?: 0
                    }
                }
                if (gotNum > 0) {
                    Log.other("芝麻炼金⚗️[次日奖励领取成功]#获得 $gotNum 粒")
                } else {
                    Log.runtime("芝麻炼金⚗️[次日奖励无奖励] 已领取或无可领奖励")
                }
                Status.setFlagToday("SesameAlchemy_NextDayAward")
            } else {
                Log.runtime(TAG, "次日奖励领取结果: ${jo.optString("resultView")}")
                Status.setFlagToday("SesameAlchemy_NextDayAward")
            }
        } catch (e: Exception) {
            Log.error(TAG, "claimNextDayAward: $e")
            Status.setFlagToday("SesameAlchemy_NextDayAward")
        }
    }

    /** 检查背包并使用体力药水 */
    private fun useStaminaFromBag(): Boolean {
        try {
            val itemsRes = AntMemberRpcCall.Zmxy.Alchemy.queryAvailableItems()
            val itemsJo = JSONObject(itemsRes)
            if (itemsJo.optBoolean("success")) {
                val data = itemsJo.optJSONObject("data") ?: return false
                val items = data.optJSONArray("items") ?: return false
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val itemId = item.optString("itemId")
                    val itemType = item.optString("itemType")
                    if (itemType == "BOTTLE" && itemId.isNotEmpty()) {
                        val useRes = AntMemberRpcCall.Zmxy.Alchemy.useItem(itemId, itemType)
                        val useJo = JSONObject(useRes)
                        if (useJo.optBoolean("success") && useJo.optJSONObject("data")?.optBoolean("success") == true) {
                            Log.other("芝麻炼金⚗️[使用体力药水成功]#恢复体力🚀")
                            sleepCompat(1000)
                            return true
                        } else {
                            Log.runtime(TAG, "使用体力药水失败: ${useJo.optString("resultView")}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "useStaminaFromBag: $e")
        }
        return false
    }

    /**
     * 确保拥有体力：
     * 1. 优先使用背包中现有的体力药水
     * 2. 若背包无药水且有配额，做任务获取体力药水并使用
     */
    private fun ensureStamina(hasBottleQuota: Boolean = true): Boolean {
        try {
            // 1. 先检查背包现有药水
            if (useStaminaFromBag()) {
                return true
            }

            // 2. 如果背包无药水，检查是否有兑换药水配额
            if (!hasBottleQuota) {
                Log.runtime(TAG, "今日体力药水兑换配额已用完")
                return false
            }

            Log.other("芝麻炼金⚗️体力不足，正在通过做任务获取体力药水...")
            val listRes = AntMemberRpcCall.Zmxy.Alchemy.queryListV3()
            val listJo = JSONObject(listRes)
            if (!listJo.optBoolean("success")) {
                Log.error(TAG, "queryListV3 失败: $listRes")
                return false
            }

            val data = listJo.optJSONObject("data") ?: return false
            val toCompleteVOS = data.optJSONArray("toCompleteVOS") ?: return false

            for (i in 0 until toCompleteVOS.length()) {
                val task = toCompleteVOS.optJSONObject(i) ?: continue
                val bizType = task.optString("bizType")
                val title = task.optString("title")
                val templateId = task.optString("templateId")

                if (skipTaskList.contains(title)) continue
                if (task.optBoolean("shareAssist", false)) continue

                if (bizType == "LIFE_RECORD") {
                    val recordId = joinActivity(templateId)
                    if (recordId.isEmpty()) continue

                    sleepCompat(9000 + (Math.random() * 1000).toLong())
                    feedbackTask(templateId, "BOTTLE")
                    sleepCompat(6000 + (Math.random() * 1000).toLong())
                    val (pushSuccess, pushErr) = pushActivity(recordId, title)
                    if (pushSuccess) {
                        handleTaskSuccess(title)
                        Log.other("芝麻炼金⚗️[获取体力任务完成]#$title")
                        sleepCompat(1500)
                        if (useStaminaFromBag()) {
                            return true
                        }
                    } else {
                        handleTaskFailure(title, pushErr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "ensureStamina: $e")
        }
        return false
    }

    /** 开启芝麻炼金宝箱（含“得到更多”任务与二次开箱） */
    private fun openTreasureBox() {
        try {
            val queryRes = AntMemberRpcCall.Zmxy.Alchemy.queryTreasureBox()
            val queryJo = JSONObject(queryRes)
            if (queryJo.optBoolean("success")) {
                val data = queryJo.optJSONObject("data") ?: return
                val hasBox = data.optBoolean("hasBox", false)
                val rewardAmountStr = data.optString("rewardAmount", "0")
                val rewardAmount = rewardAmountStr.toIntOrNull() ?: data.optInt("rewardAmount", 0)
                if (hasBox && rewardAmount > 0) {
                    val openRes = AntMemberRpcCall.Zmxy.Alchemy.openTreasureBox()
                    val openJo = JSONObject(openRes)
                    if (openJo.optBoolean("success")) {
                        val openData = openJo.optJSONObject("data")
                        val got = openData?.optInt("rewardAmount", rewardAmount) ?: rewardAmount
                        Log.other("芝麻炼金⚗️[开启宝箱成功]#获得芝麻粒 +$got")

                        // 开启宝箱后处理“得到更多”
                        handleBoxGetMore()
                    } else {
                        Log.runtime(TAG, "开启宝箱失败: ${openJo.optString("resultView")}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "openTreasureBox: $e")
        }
    }

    /** 宝箱“得到更多”：做1个任务后再次开箱 */
    private fun handleBoxGetMore() {
        try {
            sleepCompat(1500)
            val listRes = AntMemberRpcCall.Zmxy.Alchemy.queryListV3()
            val listJo = JSONObject(listRes)
            if (!listJo.optBoolean("success")) return
            val data = listJo.optJSONObject("data") ?: return
            val toCompleteVOS = data.optJSONArray("toCompleteVOS") ?: return

            var finishedTask = false
            for (i in 0 until toCompleteVOS.length()) {
                val task = toCompleteVOS.optJSONObject(i) ?: continue
                val bizType = task.optString("bizType")
                val title = task.optString("title")
                val templateId = task.optString("templateId")

                if (skipTaskList.contains(title)) continue
                if (task.optBoolean("shareAssist", false)) continue

                if (bizType == "AD_TASK") {
                    val logExtMap = task.optJSONObject("logExtMap") ?: continue
                    val bizId = logExtMap.optString("bizId")
                    Log.other("芝麻炼金⚗️开宝箱得到更多，正在完成广告任务[$title]...")
                    val (adSuccess, adErr) = finishAdTask(bizId, title)
                    if (adSuccess) {
                        handleTaskSuccess(title)
                        finishedTask = true
                        break
                    } else {
                        handleTaskFailure(title, adErr)
                    }
                } else if (bizType == "LIFE_RECORD") {
                    Log.other("芝麻炼金⚗️开宝箱得到更多，正在完成任务[$title]...")
                    val recordId = joinActivity(templateId)
                    if (recordId.isEmpty()) continue
                    sleepCompat(9000 + (Math.random() * 1000).toLong())
                    feedbackTask(templateId)
                    sleepCompat(6000 + (Math.random() * 1000).toLong())
                    val (pushSuccess, pushErr) = pushActivity(recordId, title)
                    if (pushSuccess) {
                        handleTaskSuccess(title)
                        finishedTask = true
                        break
                    } else {
                        handleTaskFailure(title, pushErr)
                    }
                }
            }

            if (finishedTask) {
                sleepCompat(2000)
                val queryRes = AntMemberRpcCall.Zmxy.Alchemy.queryTreasureBox()
                val queryJo = JSONObject(queryRes)
                if (queryJo.optBoolean("success")) {
                    val boxData = queryJo.optJSONObject("data") ?: return
                    val rewardAmountStr = boxData.optString("rewardAmount", "0")
                    val rewardAmount = rewardAmountStr.toIntOrNull() ?: boxData.optInt("rewardAmount", 0)
                    if (rewardAmount > 0) {
                        val openRes = AntMemberRpcCall.Zmxy.Alchemy.openTreasureBox()
                        val openJo = JSONObject(openRes)
                        if (openJo.optBoolean("success")) {
                            val got = openJo.optJSONObject("data")?.optInt("rewardAmount", rewardAmount) ?: rewardAmount
                            Log.other("芝麻炼金⚗️[开启宝箱“得到更多”成功]#获得额外芝麻粒 +$got")
                        } else {
                            Log.runtime(TAG, "再次开启宝箱失败: ${openJo.optString("resultView")}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "handleBoxGetMore: $e")
        }
    }

    /** 一键收取炼金反馈芝麻粒 */
    private fun collectAlchemyCredit() {
        try {
            val queryRes = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.queryCreditFeedback())
            if (!queryRes.optBoolean("success")) return
            val vos = queryRes.optJSONArray("creditFeedbackVOS")
            if (vos == null || vos.length() == 0) return
            val collectRes = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.collectCreditFeedback())
            if (collectRes.optBoolean("success")) {
                val totalPoints = collectRes.optJSONObject("data")?.optInt("totalPoints", 0) ?: 0
                Log.other("$TAG 一键收取芝麻粒 $totalPoints 粒 (共 ${vos.length()} 项)")
            }
        } catch (e: Exception) {
            Log.error(TAG, "collectAlchemyCredit: $e")
        }
    }

    private fun queryCollectTask(){
        try {
            val result = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.queryCreditFeedback())
            if (result.optBoolean("success")){
                val creditFeedbackVOS = result.optJSONArray("creditFeedbackVOS")
                if (creditFeedbackVOS != null && creditFeedbackVOS.length() > 0) {
                    // 领取芝麻粒
                    collectTask()
                }
            }else{
                Log.error(TAG, "查询待领取芝麻粒任务失败:$result")
            }
        }catch (e: Exception){
            Log.error(TAG, "queryCollectTask: $e")
        }
    }
    private fun signIn(){
        try {
            if (Status.hasFlagToday("SesameAlchemy_SignIn")){
                return
            }
            // 动态获取今日日期，格式为 yyyyMMdd
            val today = TimeUtil.getDateStr2().replace("-", "")
            val result = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.completeCheckInTask(today, "alchemy"))
            if (result.optBoolean("success")){
                val data = result.getJSONObject("data")
                val zmlNum = data.optInt("zmlNum")
                Log.other(TAG, "签到成功 +$zmlNum")
            }else{
                Log.error(TAG, "签到失败: $result")
            }
            Status.setFlagToday("SesameAlchemy_SignIn")
        }catch (e: Exception){
            Log.error(TAG, "signIn: $e")
        }
    }

    private fun querySignIn(){
        try {
            if (Status.hasFlagToday("SesameAlchemy_SignIn")){
                return
            }
            val result = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.queryEntryList(version))
            if (result.optBoolean("success")){
                val data = result.getJSONObject("data")
                val entryList = data.getJSONArray("entryList")
                for (i in 0 until entryList.length()){
                    val entry = entryList.getJSONObject(i)
                    val title = entry.optString("title")
                    val entryCode = entry.optString("entryCode") //CHECK_IN_TASK
                    if (title.contains("签到")&& entry.optBoolean("showBadge")){
                        //执行签到
                        signIn()
                    }
                }
            }else{
                Log.error(TAG, "查询签到失败")
            }
        }catch (e: Exception){
            Log.error(TAG, "querySignIn: $e")
        }
    }

    // 获取任务列表
    private fun queryTaskLists(){
        try {
            val processedTasks = mutableSetOf<String>() // 记录已处理的任务
            var hasNewTasks = true
            var loopCount = 0
            val maxLoop = 5 // 最大循环次数
            var todo = true // 是否需要完成任务
            if (Status.hasFlagToday("SesameAlchemy")){
                 todo = false // 如果已经标记完成，则不需要完成
            }

            while (hasNewTasks && loopCount < maxLoop) {
                hasNewTasks = false
                loopCount++

                val result = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.queryListV3())
                if (result.optBoolean("success")){
                    val data = result.getJSONObject("data")
                    // 多任务源：toCompleteVOS + waitJoinTaskVOS + waitCompleteTaskVOS
                    val allTasks = org.json.JSONArray()
                    listOf("toCompleteVOS", "waitJoinTaskVOS", "waitCompleteTaskVOS").forEach { key ->
                        val arr = data.optJSONArray(key)
                        if (arr != null) {
                            for (j in 0 until arr.length()) {
                                allTasks.put(arr.getJSONObject(j))
                            }
                        }
                    }

                    var allTasksProcessed = true

                    for (i in 0 until allTasks.length()){
                        val task = allTasks.getJSONObject(i)
                        val bizType = task.optString("bizType")
                        val title = task.optString("title")
                        val templateId = task.optString("templateId")

                        // 跳过已处理/黑名单/助力型任务
                        if (processedTasks.contains(templateId) || skipTaskList.contains(title)) {
                            continue
                        }
                        if (task.optBoolean("shareAssist", false)) continue

                        //广告任务
                        if (bizType.equals("AD_TASK")){
                            allTasksProcessed = false
                            val logExtMap = task.getJSONObject("logExtMap")
                            val bizId = logExtMap.optString("bizId")
                            val (adSuccess, adErr) = finishAdTask(bizId,title)
                            if (adSuccess) handleTaskSuccess(title) else handleTaskFailure(title, adErr)
                            hasNewTasks = true
                        }else if (bizType.equals("LIFE_RECORD") && todo){
                            allTasksProcessed = false
                            val recordId = joinActivity(templateId)
                            if (recordId.isEmpty()){
                                processedTasks.add(templateId)
                                continue
                            }
                            sleepCompat(10000 + (Math.random() * 1000).toLong())
                            feedbackTask(templateId)
                            sleepCompat(6000 + (Math.random() * 1000).toLong())
                            val (pushSuccess, pushErr) = pushActivity(recordId, title)
                            if (pushSuccess) handleTaskSuccess(title) else handleTaskFailure(title, pushErr)

                            processedTasks.add(templateId)
                            hasNewTasks = true

                            Log.runtime("$TAG 已处理 LIFE_RECORD 任务: $title (templateId: $templateId)")
                        }
                        sleepCompat(7000 + (Math.random() * 1000).toLong())
                    }

                    if (allTasksProcessed && !hasNewTasks) {
                        break
                    }
                }
            }

            if (loopCount < maxLoop) {
                if (todo) {
                    Status.setFlagToday("SesameAlchemy")
                    Log.other("$TAG 芝麻炼金任务全部完成")
                }
            }
        }catch (e: Exception){
            Log.error(TAG, "queryTaskLists: $e")
        }
    }

    // --- 智能黑名单：仅业务拒绝连续失败3次才加入 ---

    private fun initFailureCount() {
        try {
            val stored: String = DataStore.get("alchemyFailureCount", String::class.java) ?: ""
            if (stored.isNotEmpty()) {
                stored.split("|").filter { it.contains("=") }.forEach {
                    val parts = it.split("=", limit = 2)
                    failureCountMap[parts[0]] = parts[1].toIntOrNull() ?: 0
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "initFailureCount: $e")
        }
    }

    private fun saveFailureCount() {
        try {
            val encoded = failureCountMap.entries.joinToString("|") { "${it.key}=${it.value}" }
            DataStore.put("alchemyFailureCount", encoded)
        } catch (e: Exception) {
            Log.error(TAG, "saveFailureCount: $e")
        }
    }

    /** 返回 true 表示错误可重试（网络/服务端瞬态），不计入黑名单 */
    private fun isRetryableError(errorResponse: String): Boolean {
        if (errorResponse.isEmpty()) return true // 空响应通常是网络问题
        return retryableErrorPatterns.any { errorResponse.contains(it, ignoreCase = true) }
    }

    /** 任务失败时调用 */
    private fun handleTaskFailure(title: String, errorResponse: String) {
        if (isRetryableError(errorResponse)) return
        val count = failureCountMap.getOrDefault(title, 0) + 1
        failureCountMap[title] = count
        saveFailureCount()
        if (count >= 3) {
            skipTaskList.add(title)
            Log.other("$TAG [$title]连续失败${count}次，已加入黑名单")
        }
    }

    /** 任务成功时调用，重置失败计数 */
    private fun handleTaskSuccess(title: String) {
        if (failureCountMap.remove(title) != null) {
            saveFailureCount()
        }
    }

    //领取任务
    private fun joinActivity(templateId: String): String{
        var recordId = ""
        try {
            val result = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.joinActivity(templateId))

            if (result.optBoolean("success")){
                val data = result.getJSONObject("data")
                recordId = data.optString("recordId")
                return recordId
            }else{
                Log.error(TAG, "领取任务[$templateId]失败: ${result}")
            }

        }catch (e: Exception){
            Log.error(TAG, "joinActivity: $e")
        }
        return recordId
    }

    // taskFeedback
    private fun feedbackTask(templateId: String, changeRewardType: String? = null){
        try {
            val resultStr = if (changeRewardType != null) {
                AntMemberRpcCall.Zmxy.Alchemy.taskFeedback(templateId, changeRewardType)
            } else {
                AntMemberRpcCall.Zmxy.Alchemy.taskFeedback(templateId)
            }
            val result = JSONObject(resultStr)
            if (result.optBoolean("success")){
                //Log.other("$TAG 回调[$templateId]成功")
            }else{
                Log.error("$TAG 回调[$templateId]失败: ${result}")
            }
        }catch (e: Exception){
            Log.error(TAG, "feedbackTask: $e")
        }
    }
    // 完成任务
    private fun pushActivity(recordId: String, title: String): Pair<Boolean, String> {
        try {
            val result = JSONObject(AntMemberRpcCall.Zmxy.Alchemy.pushActivity(recordId))
            if (result.optBoolean("success")){
                Log.other("$TAG 完成[$title]")
                return Pair(true, "")
            }else{
                val err = result.optString("resultDesc", "")
                Log.error(TAG,"任务[$title]失败: ${result}")
                return Pair(false, err)
            }
        }catch (e: Exception){
            Log.error(TAG, "pushActivity: $e")
            return Pair(false, e.message ?: "")
        }
    }
    //饭补
    private fun getFanBao(templateId: String){
        try {
            val method = "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask"
            val params = "[{\"templateId\":\"$templateId\"}]"
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")){
                val data = result.getJSONObject("data")
                val zmlNum = data.optInt("zmlNum")
                Log.other(TAG, "领取饭补成功 +$zmlNum")
            }else{
                Log.error(TAG, "领取饭补失败${result}")
            }
        }catch (e: Exception){
            Log.error(TAG, "getFanBao: $e")
        }
    }
    // 查询饭补
    private fun queryFanBao(){
        try {
            val method = "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask"
            val params = "[{}]"
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")){
                val data = result.getJSONObject("data")
                val timeLimitedTaskVO = data.getJSONObject("timeLimitedTaskVO")
                val state = timeLimitedTaskVO.optInt("state") //1-可以去完成 2-还没有到时间
                //还没有到时间
                if(state == 2){
                    return
                }
                val startHour = timeLimitedTaskVO.optInt("startHour") //开始时间
                val endHour = timeLimitedTaskVO.optInt("endHour") //结束时间
                //获取当前小时
                val currentHour = TimeUtil.getHourOfDay()
                if(currentHour < startHour || currentHour > endHour){
                    return
                }else{
                    val templateId = timeLimitedTaskVO.optString("templateId")
                    //领取饭补
                    getFanBao(templateId)
                }
            }else{
                Log.error(TAG, "查询饭补失败${result}")
            }
        }catch (e: Exception){
            Log.error(TAG, "queryFanBao: $e")
        }
    }

    //芝麻信用广告任务
    private fun getAdTask(){
        try {
            val method = "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3"
            val params = "[{\"abTestKey\":\"v1\"}]"
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")){
                val data = result.getJSONObject("data")
                val toCompleteVOS = data.getJSONArray("toCompleteVOS")
                for (i in 0 until toCompleteVOS.length()){
                    val taskList = toCompleteVOS.getJSONObject(i)
                    val bizType = taskList.optString("bizType")
                    if (bizType.equals("AD_TASK")){
                        val logExtMap = taskList.getJSONObject("logExtMap")
                        val bizId = logExtMap.optString("bizId")
                        val title = logExtMap.optString("title")
                        finishAdTask(bizId,title)
                    }
                    sleepCompat(16000)
                }
            }else{
                Log.error(TAG, "queryListV3 失败: $result")
            }
        }catch (e: Exception){
            Log.error(TAG, "getAdTask: $e")
        }
    }

    private fun finishAdTask(bizId: String,title: String): Pair<Boolean, String> {
        try {
            val method = "com.alipay.adtask.biz.mobilegw.service.task.finish"
            val params = "[{\"bizId\":\"$bizId\",\"extendInfo\":{}}]"
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")){
                Log.other(TAG, "完成[$title]")
                return Pair(true, "")
            }else{
                val err = result.optString("resultDesc", "")
                Log.error(TAG, "完成[$title]失败: $result")
                return Pair(false, err)
            }
        }catch (e: Exception){
            Log.error(TAG, "finishAdTask: $e")
            return Pair(false, e.message ?: "")
        }
    }

    //查询签到任务情况
    private fun querySignInfo(){
        try {
            val method = "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists"
            val params = "[{\"sceneCode\":\"alchemy\",\"version\":\"2025-10-22\"}]"
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")){
                val data = result.getJSONObject("data")
                val currentDateCheckInTaskVO = data.getJSONObject("currentDateCheckInTaskVO")
                val status = currentDateCheckInTaskVO.optString("status")
                val dayNum = currentDateCheckInTaskVO.optString("dayNum")
                val awardPoint = currentDateCheckInTaskVO.optInt("awardPoint",0)
                val awardPrize = currentDateCheckInTaskVO.optJSONObject("awardPrize")

                if (status.equals("COMPLETED")){
                    return
                }else{
                    if (awardPrize != null){
                        val desc = awardPrize.optString("desc")
                        Log.other(TAG, "第$dayNum 天 签到成功 +$awardPoint $desc")
                    }
                    //签到
                    signIn()
                }
            }else{
                Log.error(TAG, "查询签到任务失败${result}")
            }
        }catch (e: Exception){
            Log.error(TAG, "querySignInfo: $e")
        }
    }


    // ==================芝麻信用首页 ==================
    private fun doHomeTask(){
        if (Status.hasFlagToday("doHomeTask_queryMinor")){
            return
        }
        val method = "com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV7RpcManager.queryMinor"
        val params = "[{\"invokeSource\":\"zmHome\"}]"
        try {
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")){
                val browseTaskVO = result.optJSONObject("browseTaskVO")
                if (browseTaskVO == null){
                    return
                }
                val templateId = browseTaskVO.optString("templateId","")
                if (templateId != ""){
                    //领取任务
                    val recordId = joinActivity(templateId)
                    if (recordId.isEmpty()){
                        return
                    }
                    sleepCompat(10000 + (Math.random() * 1000).toLong())
                    // 回调任务
                    feedbackTask(templateId)
                    sleepCompat(6000 + (Math.random() * 1000).toLong())
                    // 完成任务
                    pushActivity(recordId, "芝麻信用首页浏览任务")
                }

            }
        }catch (e: Exception){
            Log.error(TAG, "doHomeTask: $e")
        }finally {
            Status.setFlagToday("doHomeTask_queryMinor")
        }
    }
}