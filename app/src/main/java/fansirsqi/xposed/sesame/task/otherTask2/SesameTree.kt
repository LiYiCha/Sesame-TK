package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.ResChecker
import org.json.JSONObject
import org.json.JSONArray

class SesameTree {
    private val TAG = "芝麻树🌱"

    private var blackList = hashSetOf<String>("邀请","下单","开通") // 黑名单


    init {
        // 获取错误缓存
        try {
            val saved = DataStore.get("sesameTree_blackList", Set::class.java)
            if (saved != null) {
                @Suppress("UNCHECKED_CAST")
                (saved as? Set<*>)?.filterIsInstance<String>()?.let { blackList.addAll(it) }
            }
        } catch (e: Exception) {
            Log.error(TAG, "初始化黑名单缓存异常: $e")
        }
    }
    fun handle() {
        try {
            // 首页
            queryHome()
            
            // 1. 执行首页的所有任务 (包括浏览任务和复访任务)
            doHomeTasks()

            // 3. 处理原有任务逻辑
            queryTaskList()

            // 2. 执行常规列表任务 (赚净化值列表)
            doRentGreenTasks()

            //查询树情况
            if (!Status.hasFlagToday("sesameTree_queryTreeInfo")) {
                queryTreeInfo()
            }

        } catch (e: RuntimeException) {
            if (e.message == "NETWORK_ERROR_48") {
                Log.error(TAG, "网络不可用(error 48)，停止后续任务执行！")
            } else {
                Log.error(TAG, "handle任务异常:${e}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "handle任务异常:${e}")
        }
    }
    fun handleUpgradeTree(){
        //升级树
        if (!Status.hasFlagToday("sesameTree_upgrade")) {
            upgradeTree()
        }
    }

    // 查询首页
    private fun queryHome() {
        try {
            val home = CommonRequest().sesameHome()
            if (!home.optBoolean("success")) {
                Log.error(TAG, "查询首页失败:${home}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "查询首页异常:${e}")
        }
    }


    // 查询任务列表
    private fun queryTaskList() {

        // 获取任务列表
        val taskLists = CommonRequest().sesameTaskList()

        try {
            // 提取任务数据
            val taskDetailLists = extractTaskDetailLists(taskLists)

            // 处理每个任务
            processTasks(taskDetailLists, blackList as Set<String>)

        } catch (e: Exception) {
            Log.error(TAG, "处理任务列表异常:${e}")
        }
    }

    // 提取任务详情列表
    private fun extractTaskDetailLists(response: JSONObject): JSONArray? {
        return when {
            response.has("success") && response.optBoolean("success") -> {
                val extInfo = response.optJSONObject("extInfo")
                extInfo?.optJSONObject("taskDetailList")?.optJSONArray("taskDetailList")
            }
            response.has("resData") -> {
                val resData = response.optJSONObject("resData")
                if (resData?.optBoolean("success") == true) {
                    val extInfo = resData.optJSONObject("extInfo")
                    extInfo?.optJSONObject("taskDetailList")?.optJSONArray("taskDetailList")
                } else {
                    Log.error(TAG, "[resData]查询任务列表失败:${resData}")
                    null
                }
            }
            else -> {
                Log.error(TAG, "[tasklists]查询任务列表失败:${response}")
                null
            }
        }
    }

    // 处理任务列表
    private fun processTasks(taskDetailLists: JSONArray?, blackCache: Set<String>) {
        if (taskDetailLists == null) return

        for (i in 0 until taskDetailLists.length()) {
            try {
                val task = taskDetailLists.getJSONObject(i)

                // 获取基础参数
                val needSignUp = task.optBoolean("needSignUp")
                val taskBaseInfo = task.optJSONObject("taskBaseInfo")
                var taskId = taskBaseInfo?.optString("appletId") ?: ""
                if (taskId.isEmpty()) {
                    taskId = task.optString("taskId") ?: ""
                }
                if (taskId.isEmpty()||taskId==null) {
                    continue
                }
                val taskProcessStatus = task.optString("taskProcessStatus")

                // 额外参数
                val taskMaterial = task.optJSONObject("taskMaterial")
                val title = taskMaterial?.optString("title") ?: ""
                val taskType = taskMaterial?.optString("taskType") ?: ""

                // 跳过任务判断
                val canAccess = task.optBoolean("canAccess", true)
                if (shouldSkipTask(needSignUp, taskProcessStatus, taskType, title, blackCache, canAccess)) {
                    continue
                }

                val needReceive = task.optBoolean("needManuallyReceiveAward", true)

                // 执行任务
                executeTask(taskId, title, needReceive)

            } catch (e: Exception) {
                Log.error(TAG, "处理单个任务异常:${e}")
            }
        }
    }

    // 判断是否跳过任务 - 优化版本
    private fun shouldSkipTask(
        needSignUp: Boolean,
        taskProcessStatus: String,
        taskType: String,
        title: String,
        blackCache: Set<String>,
        canAccess: Boolean
    ): Boolean {
        // 如果受中奖限制/频次限制，则跳过
        if (!canAccess) {
            return true
        }
        // 如果需要报名、已完成或不是浏览器任务，则跳过
        if (needSignUp ||
            taskProcessStatus != "NOT_DONE") {
            return true
        }
        val allowedTypes = setOf("BROWSER", "DIVERSION", "CONTINUE_SIGN_TASK")
        if (taskType !in allowedTypes) {
            return true // 跳过
        }

        // 检查是否在黑名单中
        if (blackList.any { title.contains(it) } ||
            blackCache.any { title.contains(it) }) {
            return true
        }

        return false
    }


    // 判断是否是网络错误 (error 48)
    private fun isNetworkError(res: JSONObject?): Boolean {
        if (res == null) return false
        val error = res.optInt("error", 0)
        val errorNo = res.optInt("errorNo", 0)
        val errorMsg = res.optString("errorMessage", "")
        return error == 48 || errorNo == 3 || errorMsg.contains("网络不可用") || errorMsg.contains("网络")
    }

    // 执行任务
    private fun executeTask(taskId: String, title: String, needReceive: Boolean = true) {
        try {
            // 延迟15-16s
            TimeUtil.sleep(RandomUtil.nextLong(15000, 16000))

            // 完成任务 - 发送
            val sendSuccess = completeTask(taskId, "send")

            if (sendSuccess) {
                if (!needReceive) {
                    Log.other(TAG, "完成[${title}]")
                    return
                }
                TimeUtil.sleep(RandomUtil.nextLong(1500, 1600))
                // 完成任务 - 领取
                val receiveSuccess = completeTask(taskId, "receive")
                if (receiveSuccess) {
                    Log.other(TAG, "完成[${title}]")
                }
            } else {
                // 任务失败，加入黑名单
                blackList.add(title)
                DataStore.put("sesameTree_blackList", blackList)
                Log.other(TAG, "任务[${title}]失败，已加入黑名单")
            }
        } catch (e: RuntimeException) {
            if (e.message == "NETWORK_ERROR_48") {
                Log.other(TAG, "任务[${title}]执行因网络异常中断，不加入黑名单")
                throw e // 继续抛出以终止整个 handle() 流程
            } else {
                Log.error(TAG, "处理单个任务异常:${e}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "处理单个任务异常:${e}")
        }
    }

    // 完成任务
    private fun completeTask(taskId: String, stageCode: String): Boolean {
        return try {
            val complete = CommonRequest().sesameTaskHandle(taskId, stageCode)
            if (complete.optBoolean("success")) {
                true
            } else {
                if (isNetworkError(complete)) {
                    throw RuntimeException("NETWORK_ERROR_48")
                }
                Log.error(TAG, "[completeTask]任务失败:${complete}")
                false
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            Log.error(TAG, "[completeTask]任务异常:${e}")
            false
        }
    }

    /**
     * 处理首页返回的任务 (含浏览任务和状态列表任务)
     */
    private fun doHomeTasks() {
        try {
            val res = ZhimaTreeRpcCall.zhimaTreeHomePage()
            if (res == null) return

            val json = JSONObject(res)
            if (ResChecker.checkRes(TAG, json)) {
                val result = json.optJSONObject("extInfo")
                if (result == null) return
                val queryResult = result.optJSONObject("zhimaTreeHomePageQueryResult")
                if (queryResult == null) return

                // 1. 处理 browseTaskList (如：芝麻树首页每日_浏览任务)
                val browseList = queryResult.optJSONArray("browseTaskList")
                if (browseList != null) {
                    for (i in 0 until browseList.length()) {
                        processSingleTask(browseList.getJSONObject(i))
                    }
                }

                // 2. 处理 taskStatusList (如：芝麻树复访任务70净化值)
                val statusList = queryResult.optJSONArray("taskStatusList")
                if (statusList != null) {
                    for (i in 0 until statusList.length()) {
                        processSingleTask(statusList.getJSONObject(i))
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "处理首页任务异常: ${e.message}")
        }
    }

    /**
     * 处理赚净化值列表任务
     */
    private fun doRentGreenTasks() {
        try {
            val res = ZhimaTreeRpcCall.queryRentGreenTaskList()
            if (res == null) return

            val json = JSONObject(res)
            if (ResChecker.checkRes(TAG, json)) {
                val extInfo = json.optJSONObject("extInfo")
                if (extInfo == null) return

                val taskDetailListObj = extInfo.optJSONObject("taskDetailList")
                if (taskDetailListObj == null) return

                val tasks = taskDetailListObj.optJSONArray("taskDetailList")
                if (tasks == null) return

                for (i in 0 until tasks.length()) {
                    processSingleTask(tasks.getJSONObject(i))
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "处理净化值任务异常: ${e.message}")
        }
    }

    /**
     * 处理单个任务对象的逻辑
     */
    private fun processSingleTask(task: JSONObject) {
        try {
            val taskBaseInfo = task.optJSONObject("taskBaseInfo")
            if (taskBaseInfo == null) return

            // 过滤受中奖限制/频次限制等无法访问的任务
            val canAccess = task.optBoolean("canAccess", true)
            if (!canAccess) {
                return
            }

            var taskId = taskBaseInfo.optString("appletId")
            // 有些任务ID在taskId字段，有些在appletId，做个兼容
            if (taskId.isNullOrEmpty()) {
                taskId = task.optString("taskId")
                if (taskId.isNullOrEmpty()) return
            }

            var title = taskBaseInfo.optString("appletName")
            if (title.isEmpty()) title = taskBaseInfo.optString("title", taskId)

            val status = task.optString("taskProcessStatus")

            // 过滤掉明显无法自动完成的任务（如包含邀请、下单、开通），但保留复访任务
            if (title.contains("邀请") || title.contains("下单") || title.contains("开通")) {
                return
            }

            // 过滤非浏览器/非关注等任务类型 (如小游戏转化任务 COMP_TRANS / TRANSFORMER 等)
            val taskMaterial = task.optJSONObject("taskMaterial")
            val taskType = task.optString("taskType").takeIf { it.isNotEmpty() }
                ?: taskMaterial?.optString("taskType")
                ?: taskBaseInfo.optString("appletType")
                ?: ""

            val allowedTypes = setOf("BROWSER", "DIVERSION", "CONTINUE_SIGN_TASK")
            if (taskType.isNotEmpty() && taskType !in allowedTypes) {
                return
            }

            // 解析奖励信息
            val prizeName = getPrizeName(task)
            val needReceive = task.optBoolean("needManuallyReceiveAward", true)

            when (status) {
                "NOT_DONE", "SIGNUP_COMPLETE" -> {
                    // SIGNUP_COMPLETE 通常表示已报名但未做，或者对于复访任务表示可以去完成
                    Log.runtime("芝麻树🌳[开始任务] $title${if (prizeName.isNotEmpty()) " ($prizeName)" else ""}")
                    if (performTask(taskId, title, prizeName, needReceive)) {
                        // 任务完成
                    }
                }
                "TO_RECEIVE" -> {
                    // 待领取状态
                    if (doTaskAction(taskId, "receive", title)) {
                        val logMsg = "芝麻树🌳[领取奖励] $title #${if (prizeName.isNotEmpty()) prizeName else "奖励已领取"}"
                        Log.forest(logMsg) // 输出到 forest
                    }
                }
            }
        } catch (e: RuntimeException) {
            if (e.message == "NETWORK_ERROR_48") throw e
            Log.error(TAG, "处理单个任务异常: ${e.message}")
        } catch (e: Exception) {
            Log.error(TAG, "处理单个任务异常: ${e.message}")
        }
    }

    /**
     * 解析奖励信息
     */
    private fun getPrizeName(task: JSONObject): String {
        try {
            // 尝试从不同字段获取奖励信息
            val prizeInfo = task.optJSONObject("prizeInfo") ?: return ""
            
            // 常见的奖励字段
            val prizeName = prizeInfo.optString("prizeName") 
                ?: prizeInfo.optString("prizeDesc")
                ?: prizeInfo.optString("awardName")
                ?: prizeInfo.optString("awardDesc")
                ?: ""
            
            // 如果有数量信息，也加上
            val prizeNum = prizeInfo.optString("prizeNum")
            return if (prizeNum.isNotEmpty() && prizeNum != "0") {
                "$prizeName x$prizeNum"
            } else {
                prizeName
            }
        } catch (e: Exception) {
            Log.error(TAG, "解析奖励信息异常: ${e.message}")
            return ""
        }
    }

    /**
     * 执行任务动作：去完成 -> 等待 -> 领取
     */
    private fun performTask(taskId: String, title: String, prizeName: String, needReceive: Boolean): Boolean {
        try {
            if (title.contains("复访")) {
                // 复访任务直接领取即可，无需发送 "send"
                if (doTaskAction(taskId, "receive", title)) {
                    val logMsg = "芝麻树🌳[完成任务] $title #${if (prizeName.isNotEmpty()) prizeName else "奖励已领取"}"
                    Log.forest(logMsg)
                    return true
                }
                return false
            }

            // 发送"去完成"指令
            if (doTaskAction(taskId, "send", title)) {
                if (!needReceive) {
                    val logMsg = "芝麻树🌳[完成任务] $title #${if (prizeName.isNotEmpty()) prizeName else "已完成"}"
                    Log.forest(logMsg)
                    return true
                }

                var waitTime = 16000 // 默认等待16秒，覆盖大多数浏览任务
                try {
                    Thread.sleep(waitTime.toLong())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                // 发送"领取"指令
                if (doTaskAction(taskId, "receive", title)) {
                    val logMsg = "芝麻树🌳[完成任务] $title #${if (prizeName.isNotEmpty()) prizeName else "奖励已领取"}"
                    Log.forest(logMsg) // 这里输出到 forest
                    return true
                }
            }
        } catch (e: RuntimeException) {
            if (e.message == "NETWORK_ERROR_48") throw e
            Log.error(TAG, "执行任务异常: ${e.message}")
        } catch (e: Exception) {
            Log.error(TAG, "执行任务异常: ${e.message}")
        }
        return false
    }

    /**
     * 执行任务动作
     */
    private fun doTaskAction(taskId: String, action: String, title: String = ""): Boolean {
        try {
            val result = CommonRequest().sesameTaskHandle(taskId, action)
            if (result.optBoolean("success")) {
                return true
            } else {
                if (isNetworkError(result)) {
                    throw RuntimeException("NETWORK_ERROR_48")
                }
                val titleStr = if (title.isNotEmpty()) "[$title]" else ""
                Log.error(TAG, "❌ 任务${titleStr}动作[$action]失败: ${result}")
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            val titleStr = if (title.isNotEmpty()) "[$title]" else ""
            Log.error(TAG, "任务${titleStr}动作[$action]异常: ${e.message}")
        }
        return false
    }

    // 升级树
    private fun upgradeTree() {
        try {
            do {
                // 先查询树信息
                val treeInfo = CommonRequest().sesameTreeInfo()
                if (!treeInfo.optBoolean("success")) {
                    Log.error(TAG, "❌ 查询树信息失败: ${treeInfo}")
                    break
                }

                val extInfo = treeInfo.optJSONObject("extInfo")
                val zhimaTreeHomePageQueryResult = extInfo.optJSONObject("zhimaTreeHomePageQueryResult")
                    ?: throw IllegalStateException("zhimaTreeHomePageQueryResult is null")

                // 检查净化值是否足够
                val purificationScore = zhimaTreeHomePageQueryResult.optInt("purificationScore", 0)
                if (purificationScore < 100) {
                    Log.runtime(TAG, "❌ 净化值不足，当前: ${purificationScore}，需要: 100")
                    break
                }

                // 获取 trashList
                val treesArray = zhimaTreeHomePageQueryResult.optJSONArray("trees")
                if (treesArray == null || treesArray.length() == 0) {
                    Log.error(TAG, "❌ 树信息为空")
                    break
                }

                val tree = treesArray.getJSONObject(0)
                val trashList = tree.optJSONArray("trashList")

                val treeLevel = tree.optInt("treeLevel", 0)
                val topLevel = tree.optInt("topLevel", 0)
                val currentLevelProcessState = tree.optInt("currentLevelProcessState", 0)

                if (trashList != null && trashList.length() > 0) {
                    // 有垃圾项的情况：清理垃圾
                    val trashItem = trashList.getJSONObject(0)
                    val trashCode = trashItem.optString("trashCode", "")
                    val trashCampId = trashItem.optString("relateCampId", "")

                    if (trashCode.isEmpty() || trashCampId.isEmpty()) {
                        Log.error(TAG, "❌ 获取垃圾信息失败，trashCode: $trashCode, trashCampId: $trashCampId")
                        break
                    }

                    Log.other(TAG, "🔄 开始清理垃圾，当前净化值: $purificationScore")

                    // 执行清理垃圾
                    val upgrade = CommonRequest().sesameTreeUpgrade(trashCampId, trashCode)
                    if (upgrade.optBoolean("success")) {
                        Log.other(TAG, "✅ 清理垃圾成功，进度${currentLevelProcessState}%|$treeLevel/$topLevel 等级")
                    } else {
                        Log.error(TAG, "❌ 清理垃圾失败: ${upgrade}")
                        break
                    }
                } else {
                    // 没有垃圾项的情况：执行浇水
                    Log.other(TAG, "🔄 开始浇水，当前净化值: $purificationScore")

                    val upgrade = CommonRequest().sesameTreeClick()
                    if (upgrade.optBoolean("success")) {
                        Log.other(TAG, "✅ 升级成功，进度${currentLevelProcessState}%|$treeLevel/$topLevel 等级")
                    } else {
                        Log.error(TAG, "❌ 升级失败: ${upgrade}")
                        break
                    }
                }

                // 每次操作后短暂延迟，避免请求过于频繁
                TimeUtil.sleep(RandomUtil.nextLong(3000, 5000))

            } while (true)

        } catch (e: Exception) {
            Log.error(TAG, "❌ 升级树异常: ${e.message}")
        }finally {
            Status.setFlagToday("sesameTree_upgrade")
        }
    }


    // 查询树情况
    private fun queryTreeInfo() {
        try {
            val treeInfo = CommonRequest().sesameTreeInfo()
            if (treeInfo.optBoolean("success")) {
                val extInfo = treeInfo.optJSONObject("extInfo")
                val zhimaTreeHomePageQueryResult = extInfo.optJSONObject("zhimaTreeHomePageQueryResult")

                if (zhimaTreeHomePageQueryResult != null) {
                    // 获取所需字段
                    val purificationScore = zhimaTreeHomePageQueryResult.optInt("purificationScore", 0)
                    val trees = zhimaTreeHomePageQueryResult.optJSONArray("trees")

                    if (trees != null && trees.length() > 0) {
                        val tree = trees.getJSONObject(0)
                        val currentLevelProcessState = tree.optInt("currentLevelProcessState", 0)
                        val topLevel = tree.optInt("topLevel", 0)
                        val treeLevel = tree.optInt("treeLevel", 0)
                        val accountEnergy = zhimaTreeHomePageQueryResult.optString("accountEnergy", "未知")
                        val remainPurificationClickNum = tree.optInt("remainPurificationClickNum", 0)

                        // 友好打印信息
                        Log.runtime(TAG, "🌳 芝麻树信息")
                        Log.runtime(TAG, "💧 净化值: $purificationScore")
                        Log.runtime(TAG, "📈 当前等级进度: $currentLevelProcessState%")
                        Log.runtime(TAG, "🌳 当前树等级: $treeLevel/$topLevel")
                        Log.runtime(TAG, "⚡ 剩余净化次数: $remainPurificationClickNum")
                        Log.runtime(TAG, "🍃 总能量: $accountEnergy")

                        // 显示可清理的垃圾项
                        val trashList = tree.optJSONArray("trashList")
                        if (trashList != null && trashList.length() > 0) {
                            Log.runtime(TAG, "🗑️ 可清理垃圾数 ${trashList.length()}:")
                        }

                        // 检查是否可以升级
                        if (purificationScore >= 100) {
                            Log.runtime(TAG, "✨ 净化值充足，可以升级树")
                        } else {
                            Log.runtime(TAG, "⚠️ 净化值不足，无法升级树")
                        }
                    }
                }
            } else {
                Log.error(TAG, "查询树信息失败: ${treeInfo}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "查询树信息异常: ${e.message}")
        }finally {
            Status.setFlagToday("sesameTree_queryTreeInfo")
        }
    }

}
