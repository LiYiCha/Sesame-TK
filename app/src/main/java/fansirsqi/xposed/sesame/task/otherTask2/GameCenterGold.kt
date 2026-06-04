package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil
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
                processViewTasks(data)
                // 限制浏览任务频率为每2小时执行一次
                Status.setTemporaryStatusWithExpiry("GameCenterGold_Browse_Limit", 1000 * 60 * 60 * 2)
            }

            // 4. 处理玩游戏60秒任务
            if (needPlay) {
                processPlay60sTasks()
            }

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
        val params = "[{\"actionChannel\":\"$actionChannel\",\"activityId\":\"P2E_PLATFORM_TASK\",\"taskId\":\"$taskId\",\"taskToken\":\"$taskToken\"}]"
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
     * 7. 60秒游戏咨询 (启动游戏)
     */
    private fun gameP2eFloatingBallConsult(gameId: String, gameModuleId: String): String? {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallConsult"
        val params = "[{\"gameId\":\"$gameId\",\"gameModuleId\":\"$gameModuleId\",\"source\":\"ch_appcenter__chsub_9patch\",\"trafficDriverId\":\"\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 处理所有的浏览任务
     */
    private fun processViewTasks(data: JSONObject) {
        val taskSet = mutableSetOf<String>()
        val allTasks = JSONArray()

        // 收集 platformTaskList 中的浏览任务
        val platformTaskList = data.optJSONArray("platformTaskList")
        if (platformTaskList != null) {
            for (i in 0 until platformTaskList.length()) {
                val task = platformTaskList.getJSONObject(i)
                val taskId = task.optString("taskId")
                if (taskId.isNotEmpty()) {
                    allTasks.put(task)
                    taskSet.add(taskId)
                }
            }
        }

        // 收集 exposedTaskList 中的浏览任务，避免重复
        val exposedTaskModuleVO = data.optJSONObject("exposedTaskModuleVO")
        if (exposedTaskModuleVO != null) {
            val exposedTaskList = exposedTaskModuleVO.optJSONArray("exposedTaskList")
            if (exposedTaskList != null) {
                for (i in 0 until exposedTaskList.length()) {
                    val task = exposedTaskList.getJSONObject(i)
                    val taskId = task.optString("taskId")
                    if (taskId.isNotEmpty() && !taskSet.contains(taskId)) {
                        allTasks.put(task)
                        taskSet.add(taskId)
                    }
                }
            }
        }

        for (i in 0 until allTasks.length()) {
            try {
                val task = allTasks.getJSONObject(i)
                val actionType = task.optString("actionType")
                val taskStatus = task.optString("taskStatus")
                val taskId = task.optString("taskId")
                val taskToken = task.optString("taskToken")
                val title = task.optString("title")
                val goldCoinAmount = task.optInt("goldCoinAmount", 0)

                // 只处理 VIEW_TASK 类型
                if (actionType != "VIEW_TASK") {
                    continue
                }

                // 判断是否已完成
                if (taskStatus == "FINISHED" || taskStatus == "RECEIVED") {
                    continue
                }

                // 确定 actionChannel (如果是 exposedTaskList 则用 exposedTaskModule 或者是 taskList)
                var actionChannel = "taskList"
                if (exposedTaskModuleVO != null) {
                    val exposedTaskList = exposedTaskModuleVO.optJSONArray("exposedTaskList")
                    if (exposedTaskList != null) {
                        for (j in 0 until exposedTaskList.length()) {
                            if (exposedTaskList.getJSONObject(j).optString("taskId") == taskId) {
                                actionChannel = "exposedTaskModule"
                                break
                            }
                        }
                    }
                }

                var success = false

                if (taskStatus == "UN_SIGNUP") {
                    // 1. 报名
                    val signupRes = platformTaskSignUp(taskId, taskToken, actionChannel)
                    if (signupRes != null && JSONObject(signupRes).optBoolean("success")) {
                        // 2. 浏览等待十几秒 (15.5-18.5s)
                        val sleepTime = RandomUtil.nextLong(15500, 18500)
                        TimeUtil.sleep(sleepTime)
                        // 3. 完成
                        val completeRes = platformTaskComplete(taskId, taskToken, actionChannel)
                        if (completeRes != null && JSONObject(completeRes).optBoolean("success")) {
                            success = true
                        }
                    }
                } else if (taskStatus == "SIGNUP_COMPLETED" || taskStatus == "SIGNUP_COMPLETE") {
                    // 已报名，直接浏览并完成
                    val sleepTime = RandomUtil.nextLong(15500, 18500)
                    TimeUtil.sleep(sleepTime)
                    val completeRes = platformTaskComplete(taskId, taskToken, actionChannel)
                    if (completeRes != null && JSONObject(completeRes).optBoolean("success")) {
                        success = true
                    }
                } else if (taskStatus == "COMPLETED") {
                    // 已完成，可以直接领奖
                    success = true
                }

                if (success) {
                    // 领取奖励
                    TimeUtil.sleep(RandomUtil.nextLong(1000, 2000))
                    val receiveRes = gameP2eTaskReceive(taskId, taskToken)
                    if (receiveRes != null) {
                        val receiveJson = JSONObject(receiveRes)
                        if (receiveJson.optBoolean("success")) {
                            val rData = receiveJson.optJSONObject("data")
                            val earned = rData?.optInt("coinAmount", goldCoinAmount) ?: goldCoinAmount
                            Log.other("$displayName: 完成[$title]-金币🪙: $earned")
                        }
                    }
                }

                TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))

            } catch (e: Exception) {
                Log.error(TAG, "$displayName: 处理浏览任务[$i]异常: ${e.message}")
            }
        }
    }

    /**
     * 处理玩游戏60秒任务
     */
    private fun processPlay60sTasks() {
        val maxIterations = 10
        var loopCount = 0
        val usedGameIds = mutableSetOf<String>()

        while (loopCount < maxIterations) {
            loopCount++
            try {
                // 每次循环重新获取最新的任务列表，以防止由于状态变化导致重复处理
                val listRes = queryTaskList()
                if (listRes.isNullOrEmpty()) {
                    break
                }
                val listJson = JSONObject(listRes)
                if (!listJson.optBoolean("success")) {
                    break
                }

                val data = listJson.optJSONObject("data") ?: break
                val adTaskModule = data.optJSONObject("adTaskModule") ?: break
                val gameModuleId = adTaskModule.optString("gameModuleId")
                if (gameModuleId.isEmpty()) {
                    break
                }

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

                if (targetSlotIdx == -1) {
                    break
                }

                val slotDesc = slots.getJSONObject(targetSlotIdx).optString("desc")

                // 搜集可玩游戏列表
                val gameIds = mutableListOf<String>()
                val platformGameTaskModule = data.optJSONObject("platformGameTaskModule")
                if (platformGameTaskModule != null) {
                    val gameTaskList = platformGameTaskModule.optJSONArray("gameTaskList")
                    if (gameTaskList != null) {
                        for (i in 0 until gameTaskList.length()) {
                            val game = gameTaskList.getJSONObject(i)
                            val gameId = game.optString("gameId")
                            if (gameId.isNotEmpty() && !gameIds.contains(gameId)) {
                                gameIds.add(gameId)
                            }
                        }
                    }
                }

                // 备选静态游戏ID
                val fallbackGames = listOf("qyjzfm", "jhwg")
                for (fallback in fallbackGames) {
                    if (!gameIds.contains(fallback)) {
                        gameIds.add(fallback)
                    }
                }

                // 选择一个在本轮中未曾使用过的 gameId，模拟玩不同游戏
                var selectedGameId = ""
                for (gid in gameIds) {
                    if (!usedGameIds.contains(gid)) {
                        selectedGameId = gid
                        break
                    }
                }

                // 如果全部都被使用过了，则清空已用集合，循环选择
                if (selectedGameId.isEmpty() && gameIds.isNotEmpty()) {
                    usedGameIds.clear()
                    selectedGameId = gameIds[0]
                }

                if (selectedGameId.isEmpty()) {
                    break
                }

                usedGameIds.add(selectedGameId)
                Log.other("$displayName: 60s游戏槽位[$slotDesc] (状态: $slotStatus) -> 游戏: $selectedGameId")

                // 发起 consult
                val consultRes = gameP2eFloatingBallConsult(selectedGameId, gameModuleId)
                if (consultRes != null && JSONObject(consultRes).optBoolean("success")) {
                    val playTime = RandomUtil.nextLong(62000, 70000)
                    Log.other("$displayName: 运行游戏 $selectedGameId，等待 ${playTime / 1000.0}秒...")
                    TimeUtil.sleep(playTime)
                } else {
                    Log.error(TAG, "启动游戏 $selectedGameId 咨询失败: $consultRes")
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
     */
    private fun handleSignIn(homeData: JSONObject) {
        try {
            if (Status.hasFlagToday("GameCenterGold_SignIn_Completed")) {
                return
            }

            val signUpModuleVO = homeData.optJSONObject("signUpModuleVO") ?: return
            val popupStatus = signUpModuleVO.optString("popupStatus")
            
            // 如果已签到，跳过
            if (popupStatus == "COMPLETED" || popupStatus == "SIGNED") {
                Status.setFlagToday("GameCenterGold_SignIn_Completed")
                return
            }

            val date = signUpModuleVO.optString("date")
            val index = signUpModuleVO.optInt("index", -1)
            val signSequenceId = signUpModuleVO.optString("signSequenceId")

            if (date.isNotEmpty() && index != -1 && signSequenceId.isNotEmpty()) {
                Log.record(TAG, "开始游戏中心签到: date=$date, index=$index")
                val res = signIn(date, index, signSequenceId)
                if (!res.isNullOrEmpty()) {
                    val resJson = JSONObject(res)
                    if (resJson.optBoolean("success")) {
                        Log.other("$displayName: 签到成功✅")
                        Status.setFlagToday("GameCenterGold_SignIn_Completed")
                    } else {
                        Log.error(TAG, "签到失败: $res")
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "签到异常: ${e.message}")
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
}
