package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject

class GameCenterGold : BaseCommTask() {

    init {
        displayName = "游戏中心金币🍧"
        keyEnum = CompletedKeyEnum.GameCenterGoldTask
    }

    override fun handle() {
        try {
            // 分别检查各项任务是否需要执行
            val needSignIn = !Status.hasFlagToday("GameCenterGold_SignIn_Completed")
            val needBrowse = !Status.hasTemporaryStatusValid("GameCenterGold_Browse_Limit")
            val needPlay = !Status.hasFlagToday("GameCenterGold_Play60s_Completed")

            if (!needSignIn && !needBrowse && !needPlay) {
                return
            }

            // 1. 初始化/登录 - queryHomePage
            val homePageRes = queryHomePage()
            if (homePageRes.isNullOrEmpty()) {
                Log.error(TAG, "访问游戏中心首页失败")
                return
            }

            val homePageJson = JSONObject(homePageRes)
            if (!homePageJson.optBoolean("success")) {
                Log.error(TAG, "访问游戏中心首页失败: $homePageRes")
                return
            }

            val homeData = homePageJson.optJSONObject("data") ?: return

            // 处理签到
            if (needSignIn) {
                handleSignIn(homeData)
                TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))
            }

            // 处理金币抽奖与重新抽奖
            handleDrawGoldCoin(homeData)
            TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))

            // 2. 获取任务列表 - queryTaskList
            val taskListRes = queryTaskList()
            if (taskListRes.isNullOrEmpty()) {
                Log.error(TAG, "获取任务列表为空")
                return
            }

            val taskListJson = JSONObject(taskListRes)
            if (!taskListJson.optBoolean("success")) {
                Log.error(TAG, "获取任务列表失败: $taskListRes")
                return
            }

            val data = taskListJson.optJSONObject("data") ?: return

            // 搜集并上报 exposedTaskList (模拟客户端行为，放在做任务之前处理)
            val exposedTaskModuleVO = data.optJSONObject("exposedTaskModuleVO")
            if (exposedTaskModuleVO != null) {
                val exposedTaskList = exposedTaskModuleVO.optJSONArray("exposedTaskList")
                if (exposedTaskList != null && exposedTaskList.length() > 0) {
                    reportExposedTasks(exposedTaskList)
                }
            }

            // 3. 处理浏览任务 (VIEW_TASK)
            if (needBrowse) {
                processViewTasks()
                // 限制浏览任务频率为每1小时执行一次
                Status.setTemporaryStatusWithExpiry("GameCenterGold_Browse_Limit", 1000 * 60 * 60)
            }

            // 4. 处理玩游戏60秒任务
            if (needPlay) {
                processPlay60sTasks()
            }

            // 5. 领取阶段奖励里程碑
            handleMilestoneRewards()

        } catch (e: Exception) {
            Log.error(TAG, "执行异常: ${e.message}")
            Log.printStackTrace(e)
        }
    }

    /**
     * 1. 访问首页 RPC
     */
    private fun queryHomePage(): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.queryHomePage"
        val params = "[{\"canAddHome\":false,\"deviceLevel\":\"high\",\"screenType\":10,\"source\":\"ch_appcenter__chsub_9patch\",\"unityDeviceLevel\":\"high\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 2. 获取任务列表 RPC
     */
    private fun queryTaskList(): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.queryTaskList"
        val sessionId = System.currentTimeMillis().toString()
        val params = "[{\"deviceLevel\":\"high\",\"panelLaunchableCheckMap\":{\"SET_HEAD_TASK\":false},\"sessionId\":\"$sessionId\",\"setHeadPanelCheck\":false,\"source\":\"ch_appcenter__chsub_9patch\",\"unityDeviceLevel\":\"high\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 3. 上报展示的任务 RPC (做任务之前上报，模拟客户端可见行为)
     */
    private fun reportExposedTasks(exposedTaskList: JSONArray) {
        try {
            val list = JSONArray()
            for (i in 0 until exposedTaskList.length()) {
                val task = exposedTaskList.getJSONObject(i)
                val item = JSONObject()
                item.put("taskId", task.optString("taskId"))
                item.put("taskType", task.optString("taskType"))
                list.put(item)
            }
            val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.reportExposedTasks"
            val params = "[{\"exposedTaskList\":$list}]"
            RequestManager.requestString(method, params)
        } catch (e: Exception) {
            Log.error(TAG, "上报展示任务异常: ${e.message}")
        }
    }

    /**
     * 4. 报名浏览任务
     */
    private fun platformTaskSignUp(taskId: String, taskToken: String, actionChannel: String): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.platformTaskSignUp"
        val params = "[{\"actionChannel\":\"$actionChannel\",\"activityId\":\"P2E_PLATFORM_TASK\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskId\":\"$taskId\",\"taskToken\":\"$taskToken\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 5. 完成浏览任务
     */
    private fun platformTaskComplete(taskId: String, taskToken: String, actionChannel: String): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.platformTaskComplete"
        val params = "[{\"actionChannel\":\"$actionChannel\",\"activityId\":\"P2E_PLATFORM_TASK\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskId\":\"$taskId\",\"taskToken\":\"$taskToken\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 6. 领取浏览任务奖励
     */
    private fun gameP2eTaskReceive(taskId: String, taskToken: String): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eTaskReceive"
        val params = "[{\"actionChannel\":\"taskList\",\"activityId\":\"P2E_PLATFORM_TASK\",\"oriChInfo\":\"ch_appcenter__chsub_9patch\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskId\":\"$taskId\",\"taskToken\":\"$taskToken\",\"taskType\":\"PLATFORM_TRAN_TASK\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 6b. 领取阶段里程碑任务奖励
     */
    private fun receiveTaskMileStoneReward(taskMileStoneId: String, outBizNo: String, sign: String): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.receiveTaskMileStoneReward"
        val params = "[{\"__git\":\"9e159d58cce04c13a\",\"oriChInfo\":\"ch_appcenter__chsub_9patch\",\"outBizNo\":\"$outBizNo\",\"sign\":\"$sign\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskMileStoneId\":\"$taskMileStoneId\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 7. 60秒游戏咨询 (启动游戏)
     */
    private fun gameP2eFloatingBallConsult(gameId: String, gameModuleId: String): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallConsult"
        val params = "[{\"__git\":\"9e159d58cce04c13a\",\"gameId\":\"$gameId\",\"gameModuleId\":\"$gameModuleId\",\"source\":\"ch_appcenter__chsub_9patch\",\"trafficDriverId\":\"\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 处理所有的浏览任务 (每次做完自动刷新列表以防遗漏)
     */
    private fun processViewTasks() {
        val maxIterations = 15
        var loopCount = 0
        val completedTaskIds = mutableSetOf<String>()

        while (loopCount < maxIterations) {
            loopCount++
            try {
                val listRes = queryTaskList()
                if (listRes.isNullOrEmpty()) break
                val listJson = JSONObject(listRes)
                if (!listJson.optBoolean("success")) break
                val data = listJson.optJSONObject("data") ?: break

                val platformTaskList = data.optJSONArray("platformTaskList")
                val exposedTaskModuleVO = data.optJSONObject("exposedTaskModuleVO")
                val exposedTaskList = exposedTaskModuleVO?.optJSONArray("exposedTaskList")

                // 找出一个可做的 VIEW_TASK
                var targetTask: JSONObject? = null
                var actionChannel = "taskList"

                // 优先从 platformTaskList 找
                if (platformTaskList != null) {
                    for (i in 0 until platformTaskList.length()) {
                        val task = platformTaskList.getJSONObject(i)
                        val taskId = task.optString("taskId")
                        val actionType = task.optString("actionType")
                        val taskStatus = task.optString("taskStatus")
                        if (actionType == "VIEW_TASK" && taskStatus != "FINISHED" && taskStatus != "RECEIVED" && !completedTaskIds.contains(taskId)) {
                            targetTask = task
                            actionChannel = "taskList"
                            break
                        }
                    }
                }

                // 如果没找到，从 exposedTaskList 找
                if (targetTask == null && exposedTaskList != null) {
                    for (i in 0 until exposedTaskList.length()) {
                        val task = exposedTaskList.getJSONObject(i)
                        val taskId = task.optString("taskId")
                        val actionType = task.optString("actionType")
                        val taskStatus = task.optString("taskStatus")
                        if (actionType == "VIEW_TASK" && taskStatus != "FINISHED" && taskStatus != "RECEIVED" && !completedTaskIds.contains(taskId)) {
                            targetTask = task
                            actionChannel = "exposedTaskModule"
                            break
                        }
                    }
                }

                if (targetTask == null) {
                    break // 没有可做的浏览任务了
                }

                val taskId = targetTask.optString("taskId")
                val taskToken = targetTask.optString("taskToken")
                val title = targetTask.optString("title")
                val taskStatus = targetTask.optString("taskStatus")
                val goldCoinAmount = targetTask.optInt("goldCoinAmount", 0)

                var success = false
                if (taskStatus == "UN_SIGNUP") {
                    val signupRes = platformTaskSignUp(taskId, taskToken, actionChannel)
                    if (signupRes != null && JSONObject(signupRes).optBoolean("success")) {
                        val sleepTime = RandomUtil.nextLong(16000, 18000)
                        TimeUtil.sleep(sleepTime)
                        val completeRes = platformTaskComplete(taskId, taskToken, actionChannel)
                        if (completeRes != null && JSONObject(completeRes).optBoolean("success")) {
                            success = true
                        }
                    }
                } else if (taskStatus == "SIGNUP_COMPLETED" || taskStatus == "SIGNUP_COMPLETE") {
                    val sleepTime = RandomUtil.nextLong(16000, 18000)
                    TimeUtil.sleep(sleepTime)
                    val completeRes = platformTaskComplete(taskId, taskToken, actionChannel)
                    if (completeRes != null && JSONObject(completeRes).optBoolean("success")) {
                        success = true
                    }
                } else if (taskStatus == "COMPLETED") {
                    success = true
                }

                if (success) {
                    TimeUtil.sleep(RandomUtil.nextLong(1000, 1500))
                    val receiveRes = gameP2eTaskReceive(taskId, taskToken)
                    if (receiveRes != null) {
                        val receiveJson = JSONObject(receiveRes)
                        if (receiveJson.optBoolean("success")) {
                            val rData = receiveJson.optJSONObject("data")
                            val earned = rData?.optInt("coinAmount", goldCoinAmount) ?: goldCoinAmount
                            Log.other("$displayName: 任务[$title]已完成, 获得金币🪙: $earned")
                        }
                    }
                }
                completedTaskIds.add(taskId)
                TimeUtil.sleep(RandomUtil.nextLong(1500, 2500))
            } catch (e: Exception) {
                Log.error(TAG, "处理浏览任务单次异常: ${e.message}")
                TimeUtil.sleep(3000)
            }
        }
    }

    /**
     * 处理玩游戏60秒任务
     */
    private fun processPlay60sTasks() {
        val maxIterations = 6 // 降低循环次数，避免过多请求
        var loopCount = 0

        while (loopCount < maxIterations) {
            loopCount++
            try {
                val listRes = queryTaskList()
                if (listRes.isNullOrEmpty()) break
                val listJson = JSONObject(listRes)
                if (!listJson.optBoolean("success")) break

                val data = listJson.optJSONObject("data") ?: break
                val adTaskModule = data.optJSONObject("adTaskModule") ?: break
                val gameModuleId = adTaskModule.optString("gameModuleId")
                if (gameModuleId.isEmpty()) break

                val taskProgress = adTaskModule.optJSONObject("taskProgress") ?: break
                val slots = taskProgress.optJSONArray("slots") ?: break

                // 检查是否所有槽位都已经完成
                var allFinished = true
                var targetSlotIdx = -1
                var slotStatus = ""
                for (i in 0 until slots.length()) {
                    val slot = slots.getJSONObject(i)
                    val status = slot.optString("status")
                    if (status != "FINISHED") {
                        allFinished = false
                        if (targetSlotIdx == -1) {
                            targetSlotIdx = i
                            slotStatus = status
                        }
                    }
                }

                if (allFinished) {
                    Log.other("$displayName: 60s游戏任务全部槽位已完成")
                    Status.setFlagToday("GameCenterGold_Play60s_Completed")
                    break
                }

                if (targetSlotIdx == -1) break

                val slotDesc = slots.getJSONObject(targetSlotIdx).optString("desc")

                // 搜集可玩游戏列表
                val gameIds = mutableListOf<String>()
                val gameIdToAppId = mutableMapOf<String, String>()
                val platformGameTaskModule = data.optJSONObject("platformGameTaskModule")
                if (platformGameTaskModule != null) {
                    val gameTaskList = platformGameTaskModule.optJSONArray("gameTaskList")
                    if (gameTaskList != null) {
                        for (i in 0 until gameTaskList.length()) {
                            val game = gameTaskList.getJSONObject(i)
                            val gameId = game.optString("gameId")
                            val appId = game.optString("appId")
                            if (gameId.isNotEmpty()) {
                                if (!gameIds.contains(gameId)) {
                                    gameIds.add(gameId)
                                }
                                if (appId.isNotEmpty()) {
                                    gameIdToAppId[gameId] = appId
                                }
                            }
                        }
                    }
                }

                // 获取 DataStore 中的游戏完成记录 map 并自动清理历史日期，实现数据自清理
                val storeKey = "GameCenterGold_Play60s_UsedGamesMap"
                val today = TimeUtil.getFormatDate()
                val uid = UserMap.currentUid ?: ""
                val mapKey = "${uid}_$today"
                val usedMap = DataStore.getOrCreate<MutableMap<String, List<String>>>(storeKey)

                // 清理历史日期数据，实现自清理 (只保留各账号今天的数据)
                val iterator = usedMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!entry.key.endsWith("_$today")) {
                        iterator.remove()
                    }
                }

                // 获取今天已玩的游戏列表
                val todayPlayedGames = (usedMap[mapKey] ?: emptyList()).toMutableList()

                // 选择一个今天未曾使用过的 gameId，模拟玩不同游戏
                var selectedGameId = ""
                for (gid in gameIds) {
                    if (!todayPlayedGames.contains(gid)) {
                        selectedGameId = gid
                        break
                    }
                }

                if (selectedGameId.isEmpty()) {
                    Log.other("$displayName: 没有今天可玩的未玩过游戏 (游戏列表: $gameIds)，结束60s任务")
                    break
                }

                val appId = gameIdToAppId[selectedGameId] ?: ""
                if (appId.isEmpty()) {
                    Log.error(TAG, "游戏 $selectedGameId 的 appId 为空，跳过并标记已使用")
                    todayPlayedGames.add(selectedGameId)
                    usedMap[mapKey] = todayPlayedGames
                    DataStore.put(storeKey, usedMap)
                    continue
                }

                Log.other("$displayName: 游戏[$selectedGameId] (Slot: $slotDesc)...")

                // 发起 consult
                val consultRes = gameP2eFloatingBallConsult(selectedGameId, gameModuleId)
                if (consultRes != null && JSONObject(consultRes).optBoolean("success")) {
                    // 模拟在游戏中玩 30 秒
                    TimeUtil.sleep(30000)
                    
                    // 上报第一阶段时长 30s
                    submitUserPlayDurationAction(appId, 30)
                    
                    // 模拟再玩 32-35 秒以符合 60s 倒计时
                    val remainTime = RandomUtil.nextLong(32000, 35000)
                    val playTimeSec = (remainTime / 1000).toInt()
                    TimeUtil.sleep(remainTime)
                    
                    // 上报第二阶段增量时长 (如 32-35s)
                    submitUserPlayDurationAction(appId, playTimeSec)
                    
                    // 提交任务完成 (FloatingBallComplete)
                    val completeRes = gameP2eFloatingBallComplete(selectedGameId, gameModuleId)
                    if (completeRes != null && JSONObject(completeRes).optBoolean("success")) {
                        Log.other("$displayName: 槽位[$slotDesc]玩游戏60s任务成功✅")
                        // 标记该游戏ID今天已完成，防重复运行
                        todayPlayedGames.add(selectedGameId)
                        usedMap[mapKey] = todayPlayedGames
                        DataStore.put(storeKey, usedMap)
                    } else {
                        Log.error(TAG, "槽位[$slotDesc]提交完成RPC失败: $completeRes")
                    }
                    
                    // 60s游戏结束，触发下发金币和气泡奖励曝光
                    queryHomePage()
                    TimeUtil.sleep(RandomUtil.nextLong(1000, 2000))
                    queryBySpaceCodeList()
                    TimeUtil.sleep(RandomUtil.nextLong(1000, 1500))
                    spaceFeedback()
                    TimeUtil.sleep(RandomUtil.nextLong(1000, 2000))
                } else {
                    Log.error(TAG, "启动游戏 $selectedGameId 咨询失败: $consultRes")
                    Log.other("$displayName: 游戏 $selectedGameId 启动咨询失败，今天跳过该游戏")
                    todayPlayedGames.add(selectedGameId)
                    usedMap[mapKey] = todayPlayedGames
                    DataStore.put(storeKey, usedMap)
                    TimeUtil.sleep(5000)
                }

            } catch (e: Exception) {
                Log.error(TAG, "$displayName: 处理60s游戏任务单次异常: ${e.message}")
                TimeUtil.sleep(5000)
            }
        }
    }

    /**
     * 处理游戏中心金币签到
     * 先通过 queryHomePage 返回的 signUpModuleVO 查询签到状态，确认未签到且参数齐全后才执行签到
     * 签到功能下架、参数缺失等情况直接标记跳过，避免反复无效重试
     */
    private fun handleSignIn(homeData: JSONObject) {
        try {
            if (Status.hasFlagToday("GameCenterGold_SignIn_Completed")) {
                return
            }

            // 1. 查询签到模块，判断签到功能是否可用
            val signUpModuleVO = homeData.optJSONObject("signUpModuleVO")
            if (signUpModuleVO == null) {
                Log.other("$displayName: 签到模块不存在（可能已下架），跳过今日签到")
                Status.setFlagToday("GameCenterGold_SignIn_Completed")
                return
            }

            // 2. 检查 popupStatus（在 signUpPopupModuleVO 子对象里，不是 signUpModuleVO 顶层）
            val popupStatus = signUpModuleVO.optJSONObject("signUpPopupModuleVO")?.optString("popupStatus") ?: ""
            if (popupStatus == "COMPLETE_TODAY_RECORD" || popupStatus == "RECEIVED_TODAY_RECORD") {
                Log.other("$displayName: 查询到今日已签到(popupStatus=$popupStatus)")
                Status.setFlagToday("GameCenterGold_SignIn_Completed")
                return
            }

            // 2b. 额外检查 signRecordVOList，找到今天已签到的记录
            val signRecordList = signUpModuleVO.optJSONArray("signRecordVOList")
            if (signRecordList != null) {
                for (i in 0 until signRecordList.length()) {
                    val record = signRecordList.optJSONObject(i) ?: continue
                    if (record.optBoolean("isToday", false) && record.optString("signUpStatus") == "SIGNED") {
                        Log.other("$displayName: signRecord 显示今日已签到")
                        Status.setFlagToday("GameCenterGold_SignIn_Completed")
                        return
                    }
                }
            }

            // 3. 检查签到参数是否齐全
            val date = signUpModuleVO.optString("date")
            val index = signUpModuleVO.optInt("index", -1)
            val signSequenceId = signUpModuleVO.optString("signSequenceId")

            if (date.isEmpty() || index == -1 || signSequenceId.isEmpty()) {
                Log.other("$displayName: 签到参数不完整（签到暂不可用），跳过今日签到")
                Status.setFlagToday("GameCenterGold_SignIn_Completed")
                return
            }

            // 4. 参数齐全，执行签到
            Log.runtime(TAG, "开始游戏中心签到: date=$date, index=$index")
            val res = signIn(date, index, signSequenceId)
            if (!res.isNullOrEmpty()) {
                val resJson = JSONObject(res)
                if (resJson.optBoolean("success")) {
                    Log.other("$displayName: 签到成功✅")
                } else {
                    Log.error(TAG, "签到失败: $res")
                }
            }
            // 无论执行结果如何，标记已处理，避免反复重试
            Status.setFlagToday("GameCenterGold_SignIn_Completed")
        } catch (e: Exception) {
            Log.error(TAG, "签到异常: ${e.message}")
            Status.setFlagToday("GameCenterGold_SignIn_Completed")
        }
    }

    /**
     * 8. 签到 RPC
     */
    private fun signIn(date: String, index: Int, signSequenceId: String): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.signIn"
        val params = "[{\"__git\":\"9e159d58cce04c13a\",\"date\":\"$date\",\"index\":$index,\"signSequenceId\":\"$signSequenceId\",\"source\":\"ch_appcenter__chsub_9patch\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 8b. 金币抽奖/重新抽奖 RPC
     */
    private fun drawGold(): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.drawGold"
        val params = "[{\"__git\":\"9e159d58cce04c13a\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 处理金币抽奖与重新抽奖
     */
    private fun handleDrawGoldCoin(homeData: JSONObject) {
        try {
            val drawGoldCoinModuleVO = homeData.optJSONObject("drawGoldCoinModuleVO") ?: return
            val status = drawGoldCoinModuleVO.optString("status") ?: ""
            if (status == "INIT" || status == "NOT_DRAWN" || status == "FULFILL_FAILED") {
                Log.other("$displayName: 开始金币抽奖/重新抽奖, 当前状态: $status")
                val res = drawGold()
                if (!res.isNullOrEmpty()) {
                    val resJson = JSONObject(res)
                    if (resJson.optBoolean("success")) {
                        val dataObj = resJson.optJSONObject("data")
                        val mainTitle = dataObj?.optJSONObject("popupInfoVO")?.optString("mainTitle") ?: ""
                        Log.other("$displayName: 金币抽奖成功🎉 $mainTitle")
                    } else {
                        Log.error(TAG, "金币抽奖失败: $res")
                    }
                }
            } else {
                Log.runtime(TAG, "金币抽奖跳过，当前状态为: $status")
            }
        } catch (e: Exception) {
            Log.error(TAG, "金币抽奖异常: ${e.message}")
        }
    }

    /**
     * 9. 查询 Space 气泡奖励 (关键: 触发和得到任务奖励)
     */
    private fun queryBySpaceCodeList(): String {
        val method = "com.alipay.gameucdp.space.queryBySpaceCodeList"
        val params = "[{\"deviceLevel\":\"high\",\"source\":\"ch_appcenter__chsub_9patch\",\"sourceTab\":\"p2e\",\"spaceCodeList\":[\"p2e_ucdp_layer\"],\"unityDeviceLevel\":\"high\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 10. 上报 Space 曝光反馈
     */
    private fun spaceFeedback(): String {
        val method = "com.alipay.gameucdp.space.feedback"
        val params = "[{\"feedBackList\":[{\"creativeId\":\"p2e_browse_task_complete\",\"deliverUnitId\":\"p2e#p2e\",\"spaceCode\":\"p2e_ucdp_layer\",\"type\":\"EXPOSE\"}]}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 11. 时长上报 RPC
     */
    private fun submitUserPlayDurationAction(gameAppId: String, playTime: Int): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction"
        val params = "[{\"gameAppId\":\"$gameAppId\",\"playTime\":$playTime,\"source\":\"yxzx_mc_xasqsr68\",\"statisticTag\":\"\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 12. 60秒游戏结算 RPC
     */
    private fun gameP2eFloatingBallComplete(gameId: String, gameModuleId: String): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallComplete"
        val params = "[{\"floatingBallTypeList\":[\"P2E_GAME_BROWSE_TASK_FLOATING_BALL\"],\"gameId\":\"$gameId\",\"gameModuleId\":\"$gameModuleId\",\"oriChInfo\":\"ch_appcenter__chsub_9patch\",\"source\":\"ch_appcenter__chsub_9patch\",\"trafficDriverId\":\"\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 获取最新任务列表并领取可领取的阶段里程碑奖励
     */
    private fun handleMilestoneRewards() {
        try {
            val listRes = queryTaskList()
            if (listRes.isNullOrEmpty()) return
            val listJson = JSONObject(listRes)
            if (!listJson.optBoolean("success")) return
            val data = listJson.optJSONObject("data") ?: return

            val taskMileStoneRewardVO = data.optJSONObject("taskMileStoneRewardVO") ?: return
            val taskMileStoneList = taskMileStoneRewardVO.optJSONArray("taskMileStoneList") ?: return

            for (i in 0 until taskMileStoneList.length()) {
                val milestone = taskMileStoneList.getJSONObject(i)
                val status = milestone.optString("status")
                if (status == "COMPLETED") {
                    val taskMileStoneId = milestone.optString("taskMileStoneId")
                    val outBizNo = milestone.optString("outBizNo")
                    val sign = milestone.optString("sign")
                    val goldCoinAmount = milestone.optInt("goldCoinAmount", 0)
                    if (taskMileStoneId.isNotEmpty() && outBizNo.isNotEmpty() && sign.isNotEmpty()) {
                        Log.other("$displayName: 发现可领取的阶段奖励[$taskMileStoneId]")
                        val rewardRes = receiveTaskMileStoneReward(taskMileStoneId, outBizNo, sign)
                        if (rewardRes != null) {
                            val resJson = JSONObject(rewardRes)
                            if (resJson.optBoolean("success")) {
                                Log.other("$displayName: 阶段奖励[$taskMileStoneId]领取成功✅, 获得金币🪙: $goldCoinAmount")
                            } else {
                                Log.error(TAG, "阶段奖励[$taskMileStoneId]领取失败: $rewardRes")
                            }
                        }
                        TimeUtil.sleep(RandomUtil.nextLong(1500, 2500))
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "处理阶段里程碑奖励异常: ${e.message}")
        }
    }
}
