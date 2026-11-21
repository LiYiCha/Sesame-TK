package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.hook.core.ModuleScope
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class GameCenterGold : BaseCommTask() {

    companion object {
        private const val GIT = "9e159d58cce04c13a"
        private const val APP_ID = "2021003125685383"
        private const val SOURCE = "yxzx_mc_xiutxz04"
        private const val FRONT_END_VERSION = "20260820"
        private const val P2E_VERSION = ""

        @Volatile
        private var playJob: Job? = null

        /**
         * 停止60s玩游戏后台协程
         */
        fun stopPlayJob() {
            try {
                playJob?.cancel()
            } catch (_: Exception) {
            } finally {
                playJob = null
            }
        }
    }

    init {
        displayName = "游戏中心金币🍧"
        keyEnum = CompletedKeyEnum.GameCenterGoldTask
    }

    override fun handle() {
        try {
            val isTaskEnabled = OtherTask2.getGameCenterGold().value
            val isCashExchangeEnabled = OtherTask2.getGameCenterGoldCashExchange().value

            val needSignIn = isTaskEnabled && !Status.hasFlagToday("GameCenterGold_SignIn_Completed")
            val needBrowse = isTaskEnabled && !Status.hasTemporaryStatusValid("GameCenterGold_Browse_Limit")
            val needPlay = isTaskEnabled && !Status.hasFlagToday("GameCenterGold_Play60s_Completed")
            val needExchange = isCashExchangeEnabled

            if (!needSignIn && !needBrowse && !needPlay && !needExchange) {
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

            // 每日一次打印当前金币资产额度
            if (!Status.hasFlagToday("GameCenterGold_Asset_Logged")) {
                val asset = homeData.optJSONObject("assetModuleVO")
                if (asset != null) {
                    val goldAmount = asset.optLong("goldCoinAmount", 0L)
                    val cashAmount = asset.optString("afterCashAmount", "0")
                    Log.other("$displayName: 当前金币余额: $goldAmount (约${cashAmount}元现金)")
                    Status.setFlagToday("GameCenterGold_Asset_Logged")
                }
            }

            // 处理签到
            if (needSignIn) {
                handleSignIn(homeData)
                TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))
            }

            // 处理金币抽奖与重新抽奖
            if (isTaskEnabled) {
                handleDrawGoldCoin(homeData)
                TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))
            }

            // 2. 处理浏览任务 (VIEW_TASK) - 每次做任务前先刷新列表
            if (needBrowse) {
                processViewTasks()
                // 限制浏览任务频率为每1小时执行一次
                Status.setTemporaryStatusWithExpiry("GameCenterGold_Browse_Limit", 1000 * 60 * 60)
            }

            // 3. 处理玩游戏60秒任务 (先判断是否有待完成任务，确认存在才拉起协程后台异步执行)
            if (needPlay && hasPendingPlay60sTask()) {
                launchPlay60sTasksAsync()
            }

            // 4. 领取阶段里程碑奖励
            if (isTaskEnabled) {
                handleMilestoneRewards()
            }

            // 5. 主流程联动自动兑换：读取兑换开关状态判断是否自动兑换
            if (OtherTask2.getGameCenterGoldCashExchange().value) {
                handleCashExchange()
            }

        } catch (e: Exception) {
            Log.error(TAG, "执行异常: ${e.message}")
            Log.printStackTrace(e)
        }
    }

    /**
     * 1. 访问首页 RPC (全面对齐最新真机抓包参数)
     */
    private fun queryHomePage(): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.queryHomePage"
        val params = "[{\"__git\":\"$GIT\",\"appId\":\"$APP_ID\",\"canAddHome\":false,\"deviceLevel\":\"high\",\"frontEndVersion\":\"$FRONT_END_VERSION\",\"p2eVersion\":\"$P2E_VERSION\",\"screenType\":10,\"source\":\"$SOURCE\",\"subscribePanelCheck\":false,\"unityDeviceLevel\":\"high\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 2. 获取任务列表 RPC (对齐最新 sessionId、frontEndVersion、source 与 appId)
     */
    private fun queryTaskList(): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.queryTaskList"
        val sessionId = System.currentTimeMillis().toString()
        val params = "[{\"__git\":\"$GIT\",\"appId\":\"$APP_ID\",\"deviceLevel\":\"high\",\"frontEndVersion\":\"$FRONT_END_VERSION\",\"p2eVersion\":\"$P2E_VERSION\",\"panelLaunchableCheckMap\":{\"SET_HEAD_TASK\":false},\"sessionId\":\"$sessionId\",\"setHeadPanelCheck\":false,\"source\":\"$SOURCE\",\"subscribePanelCheck\":false,\"unityDeviceLevel\":\"high\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 3. 上报展示的任务 RPC
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
    private fun platformTaskSignUp(taskId: String, taskToken: String, actionChannel: String): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.platformTaskSignUp"
        val params = "[{\"actionChannel\":\"$actionChannel\",\"activityId\":\"P2E_PLATFORM_TASK\",\"source\":\"$SOURCE\",\"taskId\":\"$taskId\",\"taskToken\":\"$taskToken\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 5. 完成浏览任务
     */
    private fun platformTaskComplete(taskId: String, taskToken: String, actionChannel: String): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.platformTaskComplete"
        val params = "[{\"actionChannel\":\"$actionChannel\",\"activityId\":\"P2E_PLATFORM_TASK\",\"source\":\"$SOURCE\",\"taskId\":\"$taskId\",\"taskToken\":\"$taskToken\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 6. 领取浏览任务奖励 (对齐最新 appId、p2eVersion 与 oriChInfo)
     */
    private fun gameP2eTaskReceive(taskId: String, taskToken: String, taskType: String): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eTaskReceive"
        val type = if (taskType.isNotEmpty()) taskType else "PLATFORM_TRAN_TASK"
        val tokenParam = if (taskToken.isNotEmpty()) ",\"taskToken\":\"$taskToken\"" else ""
        val params = "[{\"__git\":\"$GIT\",\"actionChannel\":\"taskList\",\"activityId\":\"P2E_PLATFORM_TASK\",\"appId\":\"$APP_ID\",\"oriChInfo\":\"$SOURCE\",\"p2eVersion\":\"$P2E_VERSION\",\"source\":\"$SOURCE\",\"taskId\":\"$taskId\"$tokenParam,\"taskType\":\"$type\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 6b. 领取阶段里程碑任务奖励
     */
    private fun receiveTaskMileStoneReward(taskMileStoneId: String, outBizNo: String, sign: String): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.receiveTaskMileStoneReward"
        val params = "[{\"__git\":\"$GIT\",\"oriChInfo\":\"$SOURCE\",\"outBizNo\":\"$outBizNo\",\"sign\":\"$sign\",\"source\":\"$SOURCE\",\"taskMileStoneId\":\"$taskMileStoneId\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 7. 60秒游戏咨询 (启动游戏)
     */
    private fun gameP2eFloatingBallConsult(gameId: String, gameModuleId: String): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallConsult"
        val params = "[{\"__git\":\"$GIT\",\"appId\":\"$APP_ID\",\"gameId\":\"$gameId\",\"gameModuleId\":\"$gameModuleId\",\"p2eVersion\":\"$P2E_VERSION\",\"source\":\"$SOURCE\",\"trafficDriverId\":\"\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 8. 60秒游戏结算 RPC
     */
    private fun gameP2eFloatingBallComplete(gameId: String, gameModuleId: String): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallComplete"
        val params = "[{\"appId\":\"$APP_ID\",\"floatingBallTypeList\":[\"P2E_GAME_BROWSE_TASK_FLOATING_BALL\"],\"gameId\":\"$gameId\",\"gameModuleId\":\"$gameModuleId\",\"oriChInfo\":\"$SOURCE\",\"p2eVersion\":\"$P2E_VERSION\",\"source\":\"$SOURCE\",\"trafficDriverId\":\"\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 9. 时长上报 RPC
     */
    private fun submitUserPlayDurationAction(gameAppId: String, playTime: Int): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction"
        val params = "[{\"gameAppId\":\"$gameAppId\",\"playTime\":$playTime,\"source\":\"yxzx_mc_xasqsr68\",\"statisticTag\":\"\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 10. 查询金币兑换/提现页面
     */
    private fun queryGoldExgPrizePage(): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.queryGoldExgPrizePage"
        val params = "[{\"__git\":\"$GIT\",\"appId\":\"$APP_ID\",\"p2eVersion\":\"$P2E_VERSION\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 11. 兑换提现现金红包 RPC (必须包含 withdrawalTierType 参数)
     */
    private fun doGoldExchangePrize(prizeConfigId: String, outBizNo: String, sendSign: String, sendSignNew: String, withdrawalTierType: String): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.doGoldExchangePrize"
        val params = "[{\"__git\":\"$GIT\",\"appId\":\"$APP_ID\",\"outBizNo\":\"$outBizNo\",\"p2eVersion\":\"$P2E_VERSION\",\"prizeConfigId\":\"$prizeConfigId\",\"sendSign\":\"$sendSign\",\"sendSignNew\":\"$sendSignNew\",\"withdrawalTierType\":\"$withdrawalTierType\"}]"
        return RequestManager.requestString(method, params)
    }

    /**
     * 处理所有的浏览任务 (做任务前先刷新列表，完成后再次刷新，动态响应状态)
     */
    private fun processViewTasks() {
        val maxIterations = 20
        var loopCount = 0
        val completedTaskIds = mutableSetOf<String>()

        while (loopCount < maxIterations) {
            loopCount++
            try {
                // 每次循环重新拉取最新的任务列表
                val listRes = queryTaskList()
                if (listRes.isNullOrEmpty()) break
                val listJson = JSONObject(listRes)
                if (!listJson.optBoolean("success")) break
                val data = listJson.optJSONObject("data") ?: break

                val exposedTaskModuleVO = data.optJSONObject("exposedTaskModuleVO")
                val exposedTaskList = exposedTaskModuleVO?.optJSONArray("exposedTaskList")
                if (loopCount == 1 && exposedTaskList != null && exposedTaskList.length() > 0) {
                    reportExposedTasks(exposedTaskList)
                }

                val platformTaskList = data.optJSONArray("platformTaskList")

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

                // 若没找到，从 exposedTaskList 找
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
                val taskType = targetTask.optString("taskType", "PLATFORM_TRAN_TASK")
                val goldCoinAmount = targetTask.optInt("goldCoinAmount", 0)

                var success = false
                if (taskStatus == "UN_SIGNUP") {
                    val signupRes = platformTaskSignUp(taskId, taskToken, actionChannel)
                    if (signupRes.isNotEmpty() && JSONObject(signupRes).optBoolean("success")) {
                        val sleepTime = RandomUtil.nextLong(16000, 18000)
                        TimeUtil.sleep(sleepTime)
                        val completeRes = platformTaskComplete(taskId, taskToken, actionChannel)
                        if (completeRes.isNotEmpty() && JSONObject(completeRes).optBoolean("success")) {
                            success = true
                        }
                    }
                } else if (taskStatus == "SIGNUP_COMPLETED" || taskStatus == "SIGNUP_COMPLETE") {
                    val sleepTime = RandomUtil.nextLong(16000, 18000)
                    TimeUtil.sleep(sleepTime)
                    val completeRes = platformTaskComplete(taskId, taskToken, actionChannel)
                    if (completeRes.isNotEmpty() && JSONObject(completeRes).optBoolean("success")) {
                        success = true
                    }
                } else if (taskStatus == "COMPLETED") {
                    success = true
                }

                if (success) {
                    TimeUtil.sleep(RandomUtil.nextLong(1000, 1500))
                    val receiveRes = gameP2eTaskReceive(taskId, taskToken, taskType)
                    if (receiveRes.isNotEmpty()) {
                        val receiveJson = JSONObject(receiveRes)
                        if (receiveJson.optBoolean("success")) {
                            val rData = receiveJson.optJSONObject("data")
                            val earned = rData?.optInt("coinAmount", goldCoinAmount) ?: goldCoinAmount
                            Log.other("$displayName: 浏览任务[$title]已完成✅, 获得金币🪙: $earned")
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
     * 检查是否存在待完成的60秒玩游戏任务槽位
     * 若全部槽位已完成，自动设置当日完成标记并返回 false
     */
    private fun hasPendingPlay60sTask(): Boolean {
        return try {
            val listRes = queryTaskList()
            if (listRes.isNullOrEmpty()) return false
            val listJson = JSONObject(listRes)
            if (!listJson.optBoolean("success")) return false
            val data = listJson.optJSONObject("data") ?: return false
            val adTaskModule = data.optJSONObject("adTaskModule") ?: return false
            val gameModuleId = adTaskModule.optString("gameModuleId")
            if (gameModuleId.isEmpty()) return false
            val taskProgress = adTaskModule.optJSONObject("taskProgress") ?: return false
            val slots = taskProgress.optJSONArray("slots") ?: return false

            var hasUnfinished = false
            for (i in 0 until slots.length()) {
                val slot = slots.getJSONObject(i)
                val status = slot.optString("status")
                if (status != "FINISHED") {
                    hasUnfinished = true
                    break
                }
            }

            if (!hasUnfinished && slots.length() > 0) {
                Status.setFlagToday("GameCenterGold_Play60s_Completed")
                return false
            }
            hasUnfinished
        } catch (e: Exception) {
            Log.error(TAG, "检查60s游戏任务状态失败: ${e.message}")
            false
        }
    }

    /**
     * 启动60s玩游戏后台协程
     */
    private fun launchPlay60sTasksAsync() {
        if (playJob?.isActive == true) {
            Log.runtime(TAG, "$displayName: 60s游戏任务后台协程正在运行中，跳过重复启动")
            return
        }

        playJob = ModuleScope.launch(Dispatchers.IO) {
            try {
                Log.runtime(TAG, "$displayName: 启动60s游戏任务后台协程")
                processPlay60sTasks()
                handleMilestoneRewards()
            } catch (_: CancellationException) {
                Log.runtime(TAG, "$displayName: 60s游戏任务后台协程已取消")
            } catch (e: Exception) {
                Log.error(TAG, "$displayName: 60s游戏任务协程异常: ${e.message}")
            } finally {
                playJob = null
            }
        }
    }

    /**
     * 处理玩游戏60秒任务（协程挂起非阻塞）
     */
    private suspend fun processPlay60sTasks() {
        val maxIterations = 20
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

                var allFinished = true
                var targetSlotIdx = -1
                for (i in 0 until slots.length()) {
                    val slot = slots.getJSONObject(i)
                    val status = slot.optString("status")
                    if (status != "FINISHED") {
                        allFinished = false
                        if (targetSlotIdx == -1) {
                            targetSlotIdx = i
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

                // 收集可玩游戏列表
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
                                if (!gameIds.contains(gameId)) gameIds.add(gameId)
                                if (appId.isNotEmpty()) gameIdToAppId[gameId] = appId
                            }
                        }
                    }
                }

                val storeKey = "GameCenterGold_Play60s_UsedGamesMap"
                val today = TimeUtil.getFormatDate()
                val uid = UserMap.currentUid ?: ""
                val mapKey = "${uid}_$today"
                val usedMap = DataStore.getOrCreate<MutableMap<String, List<String>>>(storeKey)

                // 移除历史天数据
                val iterator = usedMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!entry.key.endsWith("_$today")) {
                        iterator.remove()
                    }
                }

                val todayPlayedGames = (usedMap[mapKey] ?: emptyList()).toMutableList()
                var selectedGameId = ""
                for (gid in gameIds) {
                    if (!todayPlayedGames.contains(gid)) {
                        selectedGameId = gid
                        break
                    }
                }

                if (selectedGameId.isEmpty()) {
                    Log.other("$displayName: 今日暂无可用的新游戏，结束60s任务")
                    break
                }

                val appId = gameIdToAppId[selectedGameId] ?: ""
                if (appId.isEmpty()) {
                    todayPlayedGames.add(selectedGameId)
                    usedMap[mapKey] = todayPlayedGames
                    DataStore.put(storeKey, usedMap)
                    continue
                }

                Log.other("$displayName: 启动游戏[$selectedGameId] (槽位: $slotDesc)...")

                val consultRes = gameP2eFloatingBallConsult(selectedGameId, gameModuleId)
                if (consultRes.isNotEmpty() && JSONObject(consultRes).optBoolean("success")) {
                    delay(30000)
                    submitUserPlayDurationAction(appId, 30)

                    val remainTime = RandomUtil.nextLong(32000, 35000)
                    val playTimeSec = (remainTime / 1000).toInt()
                    delay(remainTime)
                    submitUserPlayDurationAction(appId, playTimeSec)

                    val completeRes = gameP2eFloatingBallComplete(selectedGameId, gameModuleId)
                    if (completeRes.isNotEmpty() && JSONObject(completeRes).optBoolean("success")) {
                        Log.other("$displayName: 槽位[$slotDesc]玩游戏60s任务完成✅")
                        todayPlayedGames.add(selectedGameId)
                        usedMap[mapKey] = todayPlayedGames
                        DataStore.put(storeKey, usedMap)
                        handleMilestoneRewards()
                    } else {
                        Log.error(TAG, "槽位[$slotDesc]提交完成RPC失败: $completeRes")
                    }

                    queryHomePage()
                    delay(RandomUtil.nextLong(1000, 2000))
                } else {
                    Log.other("$displayName: 游戏 $selectedGameId 启动咨询失败，跳过该游戏")
                    todayPlayedGames.add(selectedGameId)
                    usedMap[mapKey] = todayPlayedGames
                    DataStore.put(storeKey, usedMap)
                    delay(3000)
                }

            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (e: Exception) {
                Log.error(TAG, "$displayName: 处理60s游戏任务单次异常: ${e.message}")
                delay(3000)
            }
        }
    }

    /**
     * 自动兑换提现现金红包 (独立开关: gameCenterGoldCashExchange)
     * 若金额不全或金币不足以兑换，自动关闭开关并退出
     */
    private fun handleCashExchange() {
        if (!OtherTask2.getGameCenterGoldCashExchange().value) return
        try {
            val exgRes = queryGoldExgPrizePage()
            if (exgRes.isNullOrEmpty()) {
                Log.error(TAG, "$displayName: 查询金币提现页面失败")
                return
            }
            val exgJson = JSONObject(exgRes)
            if (!exgJson.optBoolean("success")) {
                val errorMsg = exgJson.optString("errorMsg", "接口返回失败")
                Log.error(TAG, "$displayName: 查询金币提现页面失败: $errorMsg")
                return
            }

            val dataObj = exgJson.optJSONObject("data") ?: return
            val assetModule = dataObj.optJSONObject("assetModuleVO")
            val currentGold = assetModule?.optString("goldAmount") ?: "0"
            val rmbAmount = assetModule?.optString("rmbAmount") ?: "0"

            val cashModule = dataObj.optJSONObject("cashExchangeModule")
            val hasExchangeable = cashModule?.optBoolean("hasExchangeablePrize", false) ?: false
            val prizesArray = cashModule?.optJSONArray("prizes")

            if (!hasExchangeable || prizesArray == null || prizesArray.length() == 0) {
                Log.runtime(TAG, "当前金币($currentGold，约${rmbAmount}元)无可兑换金额(金额不全或金币不足)，自动关闭【游戏金币|兑换现金】开关并退出")
                OtherTask2.disableGameCenterGoldCashExchange("当前无可兑换金额(金额不全或金币不足)")
                return
            }

            // 筛选当前处于 CAN_EXG 状态的奖品
            val canExgPrizes = mutableListOf<JSONObject>()
            for (i in 0 until prizesArray.length()) {
                val prize = prizesArray.getJSONObject(i)
                val prizeStatus = prize.optString("prizeStatus")
                if (prizeStatus == "CAN_EXG") {
                    canExgPrizes.add(prize)
                }
            }

            if (canExgPrizes.isEmpty()) {
                Log.runtime(TAG, "当前金币($currentGold)无可兑换现金奖品(金额不全)，自动关闭【游戏金币|兑换现金】开关并退出")
                OtherTask2.disableGameCenterGoldCashExchange("当前无可兑换现金奖品(金额不全)")
                return
            }

            // 关键优化：按提现金额从大到小降序排序（10元大额档位第一优先）
            canExgPrizes.sortWith { a, b ->
                val amountA = a.optString("prizeAmount", "0").toDoubleOrNull() ?: 0.0
                val amountB = b.optString("prizeAmount", "0").toDoubleOrNull() ?: 0.0
                amountB.compareTo(amountA)
            }

            val minAmount = OtherTask2.getGameCenterGoldCashMinAmount().value ?: 0
            var exchangedCount = 0
            for (prize in canExgPrizes) {
                val prizeAmountStr = prize.optString("prizeAmount", "0")
                val prizeAmount = prizeAmountStr.toDoubleOrNull() ?: 0.0
                if (minAmount > 0 && prizeAmount < minAmount) {
                    Log.other("$displayName: 忽略低于最低门槛(${minAmount}元)的奖品【${prizeAmountStr}元】")
                    continue
                }

                val confirmPopup = prize.optJSONObject("confirmPopup")
                val consumeGold = confirmPopup?.optString("consumeGold") ?: prize.optString("consumeGold", "0")
                val prizeConfigId = prize.optString("prizeConfigId")
                val outBizNo = prize.optString("outBizNo")
                val sendSign = prize.optString("sendSign")
                val sendSignNew = prize.optString("sendSignNew")
                val withdrawalTierType = prize.optString("withdrawalTierType")

                if (prizeConfigId.isNotEmpty() && outBizNo.isNotEmpty()) {
                    Log.other("$displayName: 发现可提现金额【${prizeAmountStr}元】(消耗金币: $consumeGold, 档位: $withdrawalTierType)，正在发起提现...")
                    val doRes = doGoldExchangePrize(prizeConfigId, outBizNo, sendSign, sendSignNew, withdrawalTierType)
                    if (doRes.isNotEmpty()) {
                        val doJson = JSONObject(doRes)
                        if (doJson.optBoolean("success")) {
                            val resData = doJson.optJSONObject("data")
                            val arrivalTips = resData?.optString("arrivalTips", "现金已发放至【支付宝-余额】")
                            Log.other("$displayName: 现金提现成功🎉 [${prizeAmountStr}元] $arrivalTips")
                            exchangedCount++
                            TimeUtil.sleep(2000)
                        } else {
                            val errorMsg = doJson.optString("errorMsg", doRes)
                            Log.error(TAG, "提现[${prizeAmountStr}元]失败: $errorMsg")
                        }
                    }
                }
            }

            if (exchangedCount > 0) {
                Status.setFlagToday("GameCenterGold_CashExchange_Completed")
                Log.runtime(TAG, "已成功兑换 $exchangedCount 笔现金红包，已无更多可提现金额，自动关闭【游戏金币|兑换现金】开关并退出")
                OtherTask2.disableGameCenterGoldCashExchange("已完成兑换，已无剩余可提现金额")
            } else {
                Log.runtime(TAG, "未完成任何现金提现，保留【游戏金币|兑换现金】开关供下次任务重试")
            }
        } catch (e: Exception) {
            Log.error(TAG, "处理金币提现兑换异常: ${e.message}")
        }
    }

    /**
     * 处理游戏中心金币签到
     */
    private fun handleSignIn(homeData: JSONObject) {
        try {
            if (Status.hasFlagToday("GameCenterGold_SignIn_Completed")) return

            val signUpModuleVO = homeData.optJSONObject("signUpModuleVO")
            if (signUpModuleVO == null) {
                Status.setFlagToday("GameCenterGold_SignIn_Completed")
                return
            }

            val popupStatus = signUpModuleVO.optJSONObject("signUpPopupModuleVO")?.optString("popupStatus") ?: ""
            if (popupStatus == "COMPLETE_TODAY_RECORD" || popupStatus == "RECEIVED_TODAY_RECORD") {
                Log.other("$displayName: 查询到今日已签到")
                Status.setFlagToday("GameCenterGold_SignIn_Completed")
                return
            }

            val signRecordList = signUpModuleVO.optJSONArray("signRecordVOList")
            if (signRecordList != null) {
                for (i in 0 until signRecordList.length()) {
                    val record = signRecordList.optJSONObject(i) ?: continue
                    if (record.optBoolean("isToday", false) && record.optString("signUpStatus") == "SIGNED") {
                        Status.setFlagToday("GameCenterGold_SignIn_Completed")
                        return
                    }
                }
            }

            val date = signUpModuleVO.optString("date")
            val index = signUpModuleVO.optInt("index", -1)
            val signSequenceId = signUpModuleVO.optString("signSequenceId")

            if (date.isEmpty() || index == -1 || signSequenceId.isEmpty()) {
                Status.setFlagToday("GameCenterGold_SignIn_Completed")
                return
            }

            val res = signIn(date, index, signSequenceId)
            if (!res.isNullOrEmpty()) {
                val resJson = JSONObject(res)
                if (resJson.optBoolean("success")) {
                    Log.other("$displayName: 签到成功✅")
                }
            }
            Status.setFlagToday("GameCenterGold_SignIn_Completed")
        } catch (e: Exception) {
            Log.error(TAG, "签到异常: ${e.message}")
            Status.setFlagToday("GameCenterGold_SignIn_Completed")
        }
    }

    private fun signIn(date: String, index: Int, signSequenceId: String): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.signIn"
        val params = "[{\"__git\":\"$GIT\",\"date\":\"$date\",\"index\":$index,\"signSequenceId\":\"$signSequenceId\",\"source\":\"$SOURCE\"}]"
        return RequestManager.requestString(method, params)
    }

    private fun drawGold(): String {
        val method = "com.alipay.gamecenteruprod.biz.rpc.p2e.drawGold"
        val params = "[{\"__git\":\"$GIT\"}]"
        return RequestManager.requestString(method, params)
    }

    private fun handleDrawGoldCoin(homeData: JSONObject) {
        try {
            val drawGoldCoinModuleVO = homeData.optJSONObject("drawGoldCoinModuleVO") ?: return
            val status = drawGoldCoinModuleVO.optString("status") ?: ""
            if (status == "INIT" || status == "NOT_DRAWN" || status == "FULFILL_FAILED") {
                val res = drawGold()
                if (!res.isNullOrEmpty()) {
                    val resJson = JSONObject(res)
                    if (resJson.optBoolean("success")) {
                        val mainTitle = resJson.optJSONObject("data")?.optJSONObject("popupInfoVO")?.optString("mainTitle") ?: ""
                        Log.other("$displayName: 金币抽奖成功🎉 $mainTitle")
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "金币抽奖异常: ${e.message}")
        }
    }

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
                        val rewardRes = receiveTaskMileStoneReward(taskMileStoneId, outBizNo, sign)
                        if (rewardRes.isNotEmpty()) {
                            val resJson = JSONObject(rewardRes)
                            if (resJson.optBoolean("success")) {
                                Log.other("$displayName: 阶段奖励[$taskMileStoneId]领取成功✅, 获得金币🪙: $goldCoinAmount")
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
