package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil
import org.json.JSONObject

/**
 * 民生之家
 */
class LifeMsgProd {

    private val TAG = "民生之家🏡"
    private var accessId :String = ""  // 用户访问id
    private var skipTaskList = hashSetOf<String>("开通","收藏民生之家","收藏","添加","领取","支付","添加支付宝",
        "绑定","绑定缴电费户号","爱有行动", "人人参与", "伸出援手", "捐赠", "捐助","冲手机流量","充话费","缴纳本月")

    fun handle() {
        val hours = TimeUtil.getHourOfDay()
        if (hours < 7) {
            return
        }
        try {
            // 先获取 accessId
            if (!getAccessId()) {
                return
            }
            initBlackTaskList()

            // 签到
            if (!Status.hasFlagToday("lifeMsgProdSignIn")) {
                doSign()
            }
            // 任务处理
            handleTask()
            // 查询用户信息
            if(!Status.hasFlagToday("lifeMsgProdUserInfo")){
                queryUserInfo()
            }
            // 游戏任务
            if(!Status.hasFlagToday("lifeMsgProdGame")){
                doGame()
            }
            // 活动能量
            if(!Status.hasFlagToday("lifeMsgProdActivityEnergy")){
                doActivityEnergy()
                pickUpEnergy()
            }
            //处理建筑升级
            if(!Status.hasFlagToday("lifeMsgProdEnergy_not_enough")){
                //建筑全部升级完成了
                if (!Status.hasFlagToday("LifeMsgProd_doNewBuilding")) {
                    handleBuilding()
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "handle error:${e}")
        }
    }

    //全局初始化黑名单
    private fun initBlackTaskList() {
        val storedBlackList = DataStore.get("blackTaskList_lifeMsgProd", Set::class.java) as? Set<String>
        if (storedBlackList != null) {
            skipTaskList.addAll(storedBlackList)
        }
    }
    private fun queryUserInfo() {
        if(!Status.hasFlagToday("lifeMsgProdUserInfo")) {
            val userInfo = CommonRequest().lifeMsgProdUserInfo()
            if (!userInfo.optBoolean("success")) {
                Log.error(TAG, "查询用户信息失败:${userInfo}")
                return
            }
            val data = userInfo.optJSONObject("data")
            val energyQuantity = data.optInt("energyQuantity")
            Log.other(TAG, "当前能量值:${energyQuantity}")
            Status.setFlagToday("lifeMsgProdUserInfo")
        }
    }

    // 提取获取 accessId 的方法
    private fun getAccessId(): Boolean {
        val prodTaskList = CommonRequest().lifeMsgProdTaskList()
        if (prodTaskList.optBoolean("isSuccess")) {
            val components = prodTaskList.optJSONObject("components")
            if (components != null) {
                val data = components.optJSONObject("independent_component_task_reward_02379846_independent_component_task_reward_query")
                if (!data.optBoolean("isSuccess")) {
                    Log.error(TAG, "获取任务列表失败:${prodTaskList}")
                    return false
                }
                val content = data.optJSONObject("content")

                // 获取用户访问信息用于请求
                accessId = content.optString("accessId", "")
                if (accessId.isEmpty()) {
                    Log.other(TAG, "获取用户访问信息失败,accessId为空")
                    Log.error(TAG, "获取用户访问信息失败:${prodTaskList}")
                    return false
                }
                return true
            }
        } else {
            Log.error(TAG, "获取任务列表失败:${prodTaskList}")
            return false
        }
        return false
    }

    //签到
    private fun doSign() {
        val signIn = CommonRequest().lifeMsgProdSignIn(accessId)
        val components = signIn.optJSONObject("components")
        val data = components.optJSONObject("independent_component_sign_in_02378042_independent_component_sign_in")
        if (data != null) {
            if (data.optBoolean("isSuccess")) {
                Log.other(TAG, "签到成功")
            } else {
                Log.error(TAG, "签到失败:${signIn}")
            }
        }
        Status.setFlagToday("lifeMsgProdSignIn")
    }

    //处理任务
    private fun handleTask() {


        val prodTaskList = CommonRequest().lifeMsgProdTaskList()
        if (prodTaskList.optBoolean("isSuccess")) {
            val components = prodTaskList.optJSONObject("components")
            if (components != null) {
                val data = components.optJSONObject("independent_component_task_reward_02379846_independent_component_task_reward_query")
                if (!data.optBoolean("isSuccess")) {
                    Log.error(TAG, "获取任务列表失败:${prodTaskList}")
                    return
                }
                val content = data.optJSONObject("content")

                //任务列表
                val playTaskOrderInfoList = content.optJSONArray("playTaskOrderInfoList")
                for (i in 0 until playTaskOrderInfoList.length()) {
                    val taskList = playTaskOrderInfoList.optJSONObject(i)

                    //任务详情
                    val templateExtInfo = taskList.optJSONObject("templateExtInfo")
                    val activityName = templateExtInfo.optString("activityName")
                    //跳过任务
                    if (activityName == null || isSkipTask(activityName)) {
                        continue
                    }
                    //任务参数
                    val code = taskList.optString("code")
                    val recordNo = taskList.optString("recordNo")
                    val taskStatus = taskList.optString("taskStatus")
                    if (!taskStatus.equals("claim") || taskStatus.equals("finish")) {
                        continue
                    }
                    //处理任务
                    val doTask = doTask(code, recordNo)
                    if (doTask) {
                        Log.other(TAG, "完成[${activityName}]")
                    } else {
                        Log.other(TAG, "完成失败[${activityName}]")
                        skipTaskList.add(activityName)
                        DataStore.put("blackTaskList_lifeMsgProd", skipTaskList)
                    }
                    TimeUtil.sleep(RandomUtil.nextLong(5000, 7000))
                }
            }
        } else {
            Log.error(TAG, "获取任务列表失败:${prodTaskList}")
        }
    }


    //处理任务
    private fun doTask(code:String ,recordNo:String):Boolean{
        val taskHandle = CommonRequest().lifeMsgProdTaskHandle(code,recordNo,accessId)
        if(isSuccess(taskHandle)) {
            return  true
        }else{
            return false
        }
    }

    // 游戏任务
    // 游戏任务
    private fun doGame() {
        // 检查是否已经执行过游戏任务
        if (Status.hasFlagToday("lifeMsgProdGame")) {
            return
        }

        try {
            var successCount = 0
            var limitCount = 0

            // 第一个游戏活动
            val campInfo1 = "ybjsS1zkl776C6HQuFLWYyE%2BmP3qFggJ"
            val gameTask1 = CommonRequest().lifeMsgProdGame(campInfo1)
            if (gameTask1.optBoolean("success")) {
                Log.other(TAG, "游戏1成功")
                successCount++
            } else {
                var errorCode = gameTask1.optString("errorCode", "")
                if (errorCode == "10001011") {
                    Log.other(TAG, "游戏1失败: 次数超过限制")
                    limitCount++
                } else {
                    Log.error(TAG, "游戏1失败:${gameTask1}")
                }
            }

            // 添加延迟，避免请求过于频繁
            TimeUtil.sleep(RandomUtil.nextLong(15000, 16000))

            // 第二个游戏活动
            val campInfo2 = "ybjsS1zkl77Rco2lmHriOlGKB0P0HaO4"
            val gameTask2 = CommonRequest().lifeMsgProdGame(campInfo2)
            if (gameTask2.optBoolean("success")) {
                Log.other(TAG, "游戏2成功")
                successCount++
            } else {
                var errorCode = gameTask2.optString("errorCode", "")
                if (errorCode == "10001011") {
                    Log.other(TAG, "游戏2失败: 次数超过限制")
                    limitCount++
                } else {
                    Log.error(TAG, "游戏2失败:${gameTask2}")
                }
            }

            // 如果至少有2个游戏成功或者都达到了次数限制，则标记为已完成
            if (successCount > 1 || limitCount == 2) {
                Status.setFlagToday("lifeMsgProdGame")
            }

        } catch (e: Exception) {
            Log.error(TAG, "执行游戏任务异常:${e}")
        }
    }


    //处理活动能量
    private fun doActivityEnergy(){
        if (Status.hasFlagToday("lifeMsgProdActivityEnergy")) {
            return
        }
        try {
            val activityEnergy = CommonRequest().lifeMsgProdActiveQuery()
            val components = activityEnergy.optJSONObject("components")
            val data = components.optJSONObject("independent_component_luckdraw_02379583_independent_component_luck_draw_query")
            if (data.optBoolean("isSuccess")) {
                val content = data.optJSONObject("content")
                val luckDrawTemplates = content.optJSONArray("luckDrawTemplates")
                for (i in 0 until luckDrawTemplates.length()) {
                    val tasks = luckDrawTemplates.optJSONObject(i)
                    val playLuckDrawTemplateInfo = tasks.optJSONObject("playLuckDrawTemplateInfo")

                    //跳过已经领取的
                    val consultResult = playLuckDrawTemplateInfo.optJSONObject("consultResult")
                    val errorCode = consultResult.optString("errorCode", "")
                    if(errorCode.equals("10001011")){
                        continue
                    }
                    //获取任务名
                    val displayInfo = playLuckDrawTemplateInfo.optJSONObject("displayInfo")
                    val activityName = displayInfo.optString("activityName", "")
                    //领取活跃奖励
                    val code = playLuckDrawTemplateInfo.optString("code", "")
                    if (code.isNotEmpty()){
                        //领取活跃奖励
                        val active = CommonRequest().lifeMsgProdActive(code)

                        if (!active.optBoolean("isSuccess")){
                            Log.error(TAG, "领取活跃奖励[$activityName]失败:${active}")
                            break
                        }

                        val components = active.optJSONObject("components")
                        val data = components.optJSONObject("independent_component_luckdraw_02379583_industry_luckdraw_action")
                        if(data.optBoolean("isSuccess")){
                            Log.other(TAG, "完成[$activityName]")
                        }else{
                            Log.error(TAG, "完成[$activityName]失败:${data}")
                        }
                    }
                    TimeUtil.sleep(RandomUtil.nextLong(15000, 17000))
                }
            }else{
                Log.error(TAG, "获取活跃能量失败:${activityEnergy}")
            }
        }catch (e:Exception){
            Log.error(TAG, "获取活跃能量异常:${e}")
        }finally {
            Status.setFlagToday("lifeMsgProdActivityEnergy")
        }
    }
    //一键领取活跃奖励
    private fun pickUpEnergy(){
        val energy = CommonRequest().lifeMsgProdPickUpEnergy()
        if(energy.has("components")){
            val components = energy.getJSONObject("components")
            val data = components.optJSONObject("independent_component_luckdraw_02570447_industry_luckdraw_action")
            if (data != null) {
                if(data.optBoolean("isSuccess")){
                    Log.other(TAG, "一键领取活跃奖励成功")
                }else{
                    Log.error(TAG, "一键领取活跃奖励失败:${energy}")
                }
            }
        }
    }

    //处理建筑任务
    fun handleBuilding(){
        var retryCount = 0
        val maxRetries = 3  // 重试次数改为3次
        var consecutiveFailures = 0  // 连续失败次数
        val maxConsecutiveFailures = 3  // 最大连续失败次数

        while (consecutiveFailures < maxConsecutiveFailures && retryCount < maxRetries) {
            try {
                //建筑基础信息
                var buildingId:Int = 0  //必须
                var currCost:Int = 0
                var totalCost:Int = 0
                var groupId:Int = 0  //必须
                var hasPrized:Boolean = false //是否领取奖励了
                var mainTitle:String = "" //建筑名字
                var status:String = ""  //状态

                //查询建筑情况
                val curBuilding = CommonRequest().lifeMsgProdBuildingQuery()
                if (curBuilding.optBoolean("success")){
                    val data = curBuilding.optJSONObject("data")
                    buildingId = data.optInt("buildingId")
                    currCost = data.optInt("currCost")
                    groupId = data.optInt("groupId")
                    hasPrized = data.optBoolean("hasPrized")
                    mainTitle = data.optString("mainTitle")
                    totalCost = data.optInt("totalCost")
                    status = data.optString("status")
                }else{
                    Log.error(TAG, "获取建筑信息失败:${curBuilding}")
                    consecutiveFailures++
                    retryCount++
                    continue
                }

                //判断必备条件是否存在
                if(buildingId==0 || groupId==0 || mainTitle.isEmpty() || status.isEmpty()){
                    consecutiveFailures++
                    retryCount++
                    continue
                }

                //检查是否需要抽红包（每完成5个建筑,每五个建筑为一组）
                var finishedCount = queryBuildingFinishNumber(groupId)
                // 如果已完成建筑数是5的倍数，且当前建筑已经完成并领取奖励，可能需要手动抽红包
                if (finishedCount > 0 && finishedCount % 5 == 0 &&
                    (status.equals("FINISH") || currCost >= totalCost) && hasPrized) {
                    Log.other(TAG, "已完成${finishedCount}个建筑，需要手动抽红包")
                    Notify.sendNewNotification("民生之家", "已完成${finishedCount}个建筑，请检查是否需要手动抽红包")
                    return
                }

                //如果满级了且未领取奖励就领取奖励
                if((status.equals("FINISH") || currCost>=totalCost) && !hasPrized){
                    //领取奖励
                    if (doPrize(mainTitle)) {
                        TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))
                        consecutiveFailures = 0  // 成功后重置失败计数
                        continue // 领取奖励后重新检查状态
                    } else {
                        consecutiveFailures++
                        retryCount++
                        continue
                    }
                }

                //如果没有满级就进行升级
                if(currCost<totalCost){
                    //查询当前能量
                    var resultUserInfo = CommonRequest().lifeMsgProdUserInfo()
                    var energyQuantity:Int = 0
                    if (resultUserInfo.optBoolean("success")){
                        val data = resultUserInfo.optJSONObject("data")
                        energyQuantity = data.optInt("energyQuantity",0)
                    }

                    //根据能量值决定升级策略
                    var upgradeEnergy = 0
                    if(energyQuantity>=1000){
                        upgradeEnergy = 1000
                    } else if(energyQuantity>=100){
                        upgradeEnergy = 100
                    } else {
                        Log.other(TAG, "能量不足，当前能量:${energyQuantity}")
                        Status.setFlagToday("lifeMsgProdEnergy_not_enough")
                        break // 能量不足时退出循环
                    }

                    //执行升级
                    if(doUpgrade(buildingId, upgradeEnergy, mainTitle, groupId)) {
                        TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))
                        consecutiveFailures = 0  // 成功后重置失败计数
                        continue // 升级成功后重新检查状态
                    } else {
                        consecutiveFailures++
                        retryCount++
                        continue
                    }
                } else {
                    //满级并领取奖励后进行抽建筑
                    if (doNewBuilding()) {
                        TimeUtil.sleep(RandomUtil.nextLong(2000, 3000))
                        consecutiveFailures = 0  // 成功后重置失败计数
                        continue // 抽建筑后重新检查状态
                    } else {
                        consecutiveFailures++
                        retryCount++
                        continue
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "处理建筑任务异常:${e}")
                consecutiveFailures++
                retryCount++
            }
        }

        if (consecutiveFailures >= maxConsecutiveFailures) {
            Log.record(TAG, "建筑任务连续失败次数过多，停止执行")
        } else if (retryCount >= maxRetries) {
            Log.record(TAG, "建筑任务达到最大重试次数")
        }
    }


    //升级建筑
    private fun doUpgrade(buildingId:Int,energyQuantity:Int,mainTitle:String,groupId: Int):Boolean{
        val upgrade = CommonRequest().lifeMsgProdBuildingUpgrade(buildingId,energyQuantity)
        if(upgrade.optBoolean("success")){
            var data = upgrade.optJSONObject("data")
            val canGroupPrize = data.optBoolean("canGroupPrize", false) //抽红包
            val canPrize = data.optBoolean("canPrize", false) //是否可以领取奖励
            val energyQuantity = data.optInt("energyQuantity") //当前能量

            val report = CommonRequest().lifeMsgProdBehaviorReport(buildingId, groupId)
            if (!report.optBoolean("success")) {
                Log.error(TAG, "上报[$mainTitle]失败:${report}")
            }
            Log.other(TAG, "升级[$mainTitle]--当前能量[$energyQuantity]")

            // 如果有红包奖励，记录提醒并退出程序处理
            if (canGroupPrize) {
                Log.other(TAG, "满级了,有红包奖励，请手动处理")
                Notify.sendNewNotification("民生之家", "满级了请手动去抽红包")
                return true
            }
            // 如果可以领取建筑奖励，标记需要在下次循环中处理
            if (canPrize) {
                Log.other(TAG, "有可领取的建筑奖励")
            }
            TimeUtil.sleep(RandomUtil.nextLong(5000, 7000))
            return true
        }else{
            Log.error(TAG, "升级[$mainTitle]失败:${upgrade}")
            return false
        }
    }

    //抽建筑
    private fun doNewBuilding() :Boolean{
        if(Status.hasFlagToday("LifeMsgProd_doNewBuilding")){
            return  false
        }
        //存放可选择的建筑id
        val buildingList = arrayListOf<Int>()

        //先获取全部建筑列表--然后筛选出未完成的建筑加入到数组中
        val allBuilding = CommonRequest().lifeMsgProdBuildingList()
        if (allBuilding.optBoolean("success")){
            val data = allBuilding.optJSONArray("data")
            for (i in 0 until data.length()) {
                val tasks = data.optJSONObject(i)
                val buildingId = tasks.optInt("buildingId")
                val status = tasks.optString("status")

                //筛选出未完成的建筑
                if (!status.equals("FINISH")){
                    buildingList.add(buildingId)
                }
            }

            //如果有可选建筑则随机抽取一个
            if(buildingList.isNotEmpty()){
                //随机进行抽取
                val buildingId = buildingList.random()
                //选择建筑
                val choose = CommonRequest().lifeMsgProdBuildingChoose(buildingId)
                if (choose.optBoolean("success")){
                    Log.other(TAG, "选择建筑成功,建筑ID[$buildingId]")
                    return true
                }else{
                    Log.error(TAG, "选择建筑失败:${choose}")
                }
            } else {
                Log.other(TAG, "没有可选择的建筑")
                Status.setFlagToday("LifeMsgProd_doNewBuilding")
            }
        }else{
            Log.error(TAG, "获取建筑列表失败:${allBuilding}")
        }
        return false
    }

    //领取建筑奖励
    fun doPrize(mainTitle:String):Boolean{
        val prize = CommonRequest().lifeMsgProdBuildingReward(accessId)
        val components = prize.optJSONObject("components")
        val data = components.optJSONObject("independent_component_luckdraw_02378770_industry_luckdraw_action")
        if(data.optBoolean("isSuccess")){
            Log.other(TAG, "领取[$mainTitle]建筑奖励成功")
            return true
        }else{
            Log.error(TAG, "领取[$mainTitle]建筑奖励失败:${data}")
            return false
        }
    }

    //查询建筑完成数量
    fun queryBuildingFinishNumber(groupId: Int):Int{
         try {
            val buildingFinish = CommonRequest().lifeMsgProdBuildingListFinished(groupId)
            if (buildingFinish.optBoolean("success")){
                val data = buildingFinish.optJSONArray("data")
                if (data != null) {
                    return data.length()
                } else {
                    Log.other(TAG, "建筑完成数量响应为空, groupId: $groupId")
                    return 0
                }
            }else{
                Log.error(TAG, "查询建筑完成数量失败:${buildingFinish}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "查询建筑完成数量异常:${e}")
        }
        return 0
    }



    //跳过任务
    private fun isSkipTask(taskName:String,):Boolean{
        // 完全匹配
        if (taskName in skipTaskList){
            return true
        }

        // 不跳过包含"浏览"或"逛一逛"的任务，因为这些通常是可完成的任务
        if (taskName.contains("浏览") || taskName.contains("逛一逛")) {
            return false
        }

        // 特殊处理支付宝就业相关任务
        if (taskName.contains("支付宝就业") && !taskName.contains("添加")) {
            return false
        }

        // 部分匹配 - 检查任务名是否包含跳过列表中的关键词
        for (keyword in skipTaskList) {
            // 使用更精确的匹配方式，避免部分包含导致的误判
            if (taskName.contains(keyword) &&
                // 确保不是因为包含"添加"而跳过了其他任务
                !(keyword == "添加" && taskName.contains("支付宝就业")) &&
                // 确保不是因为包含通用关键词而跳过了"浏览"或"逛一逛"任务
                !(keyword == "添加" && (taskName.contains("浏览") || taskName.contains("逛一逛")))) {
                return true
            }
        }

        return false
    }



    //处理结果
    private fun isSuccess(response: JSONObject):Boolean{
        val components = response.optJSONObject("components")
        if(components == null){
            return false
        }
        val data = components.optJSONObject("independent_component_task_reward_02379846_independent_component_task_reward_process")
        if(data == null){
            return false
        }
        val isSuccess = data.optBoolean("isSuccess")
        if(isSuccess){
            return true
        }else {
            val errorMsg = data.optString("errorMsg")
            Log.record(TAG, "处理失败:${errorMsg}")
        }
        return false
    }
}