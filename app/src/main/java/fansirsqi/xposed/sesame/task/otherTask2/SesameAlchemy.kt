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
                // 原有的执行逻辑保持不变
                init() //初始化
                initBlackTaskList() //初始化黑名单
                querySignIn() //签到
                doHomeTask() //首页任务
                getAdTask() //芝麻信用广告任务
                queryFanBao() //饭补
                handleTask() //处理任务
                alchemyExecute() //自动炼金
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
            GlobalThreadPools.execute {
                queryTaskLists()
                queryCollectTask()
            }
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
                var zmlBalance = data.optInt("zmlBalance", 0) // 当前芝麻粒
                val cost = data.optInt("alchemyCostZml", 5) // 单次消耗
                var capReached = data.optBoolean("capReached", false) // 是否达到上限
                var currentLevel = data.optInt("currentLevel", 0)

                // 循环炼金逻辑
                while (zmlBalance >= cost && !capReached) {
                    sleepCompat(1500)
                    val alchemyRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyExecute()
                    val alchemyJo = JSONObject(alchemyRes)

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
                                        "#消耗" + cost + "粒" +
                                        " | 获得" + goldNum + "金" +
                                        " | 当前等级Lv." + currentLevel +
                                        (if (levelUp) "（升级🎉）" else "") +
                                        (if (levelFull) "（满级🏆）" else "")
                            )
                            zmlBalance -= cost
                        } else {
                            break
                        }
                    } else {
                        Log.runtime(TAG, "芝麻炼金失败: " + alchemyJo.optString("resultView"))
                        break
                    }
                }
            }
        } else {
            Log.runtime(TAG, "芝麻炼金首页查询失败")
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
            val maxLoop = 3 // 最大循环次数，防止无限循环
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
                    val taskLists = data.getJSONArray("toCompleteVOS")
                    var allTasksProcessed = true // 标记是否所有任务都已处理

                    for (i in 0 until taskLists.length()){
                        val task = taskLists.getJSONObject(i)
                        val bizType = task.optString("bizType")
                        val title = task.optString("title")
                        val templateId = task.optString("templateId")

                        // 如果任务已处理过，跳过
                        if (processedTasks.contains(templateId) ||skipTaskList.contains(title)) {
                            continue
                        }

                        //广告任务
                        if (bizType.equals("AD_TASK")){
                            allTasksProcessed = false // 发现未处理的任务
                            val logExtMap = task.getJSONObject("logExtMap")
                            val bizId = logExtMap.optString("bizId")
                            finishAdTask(bizId,title)
                            hasNewTasks = true // 标记有新任务被处理，需要重新检查
                        }else if (bizType.equals("LIFE_RECORD") && todo){
                            allTasksProcessed = false // 发现未处理的任务
                            // 领取任务
                            val recordId = joinActivity(templateId)
                            if (recordId.isEmpty()){
                                processedTasks.add(templateId) // 标记为已处理（即使失败）
                                continue
                            }
                            sleepCompat(10000 + (Math.random() * 1000).toLong())
                            // 回调任务
                            feedbackTask(templateId)
                            sleepCompat(6000 + (Math.random() * 1000).toLong())
                            // 完成任务
                            pushActivity(recordId, title)

                            // 将任务添加到已处理列表
                            processedTasks.add(templateId)
                            hasNewTasks = true // 标记有新任务被处理，需要重新检查

                            Log.runtime("$TAG 已处理 LIFE_RECORD 任务: $title (templateId: $templateId)")
                        }
                        //协程随机休眠
                        sleepCompat(7000 + (Math.random() * 1000).toLong())
                    }

                    // 如果所有任务都已处理，且没有新的任务需要处理，则退出循环
                    if (allTasksProcessed && !hasNewTasks) {
                        break
                    }
                }
            }

            // 如果循环正常结束（不是因为达到最大循环次数），说明所有任务已完成
            if (loopCount < maxLoop) {
                if (todo) { // 仅当原本需要完成任务时，才设置完成标记
                    Status.setFlagToday("SesameAlchemy")
                    Log.other("$TAG 芝麻炼金任务全部完成")
                }
            }
        }catch (e: Exception){
            Log.error(TAG, "queryTaskLists: $e")
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
    private fun feedbackTask(templateId: String){
        try {
            val method = "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback"
            val params = "[{\"actionType\":\"TO_COMPLETE\",\"templateId\":\"$templateId\"," +
                    "\"version\":\"alchemy\"}]"
            val result = JSONObject(RequestManager.requestString(method, params))
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
    private fun pushActivity(recordId: String, title: String){
        try {
            val method = "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity"
            val params = "[{\"recordId\":\"$recordId\"}]"
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")){
                Log.other("$TAG 完成[$title]")
            }else{
                Log.error(TAG,"任务[$title]失败: ${result}")
            }
        }catch (e: Exception){
            Log.error(TAG, "pushActivity: $e")
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

    private fun finishAdTask(bizId: String,title: String){
        try {
            val method = "com.alipay.adtask.biz.mobilegw.service.task.finish"
            val params = "[{\"bizId\":\"$bizId\",\"extendInfo\":{}}]"
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")){
                Log.other(TAG, "完成[$title]")
            }else{
                Log.error(TAG, "完成[$title]失败: $result")
            }
        }catch (e: Exception){
            Log.error(TAG, "finishAdTask: $e")
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