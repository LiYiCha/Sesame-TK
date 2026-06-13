package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.hook.RequestManager.requestString
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class NpcChicken {
    private val TAG = "🐥NPC小鸡"
    private val VERSION = "1.8.2302070202.46"
    private var ownerFarmId: String = ""
    // 缓存可雇佣（或已配置）的 NPC 小鸡信息（enterFarm 返回）
    private var npcHires: JSONArray? = null
    // 当前是否已雇佣 NPC 小鸡（enterFarm 返回 hireNPCAnimalPopInfoVO 是否存在）

    fun run(ownerFarmId: String){

        if (Status.hasFlagToday("NpcChicken_temp")){
            return
        }
        try {
            // 获取小鸡的farmId
            this.ownerFarmId = ownerFarmId

            initFarm()

            goldChicken()
        } catch (e: Exception) {
            Log.error("$TAG.run异常:$e")
        } finally {

        }
    }

    /**
     * 黄金小鸡及同类小鸡任务（按关键词泛化处理）
     */
    private fun goldChicken() {
        try {
            // 查询当前农场全部小鸡状态（含 npcBizReward）
            val goldResult = syncAnimalStatus(ownerFarmId, "SYNC_RESUME", "QUERY_ALL")
            val goldJson = JSONObject(goldResult)
            if (!goldJson.optBoolean("success")) {
                Log.error("$TAG.goldChicken查询异常:$goldJson")
                return
            }

            val subFarmVO = goldJson.optJSONObject("subFarmVO")
            val animals = subFarmVO?.optJSONArray("animals") ?: JSONArray()

            // 汇总打印（带当前奖励值）并处理关注的小鸡
            Log.farm("$TAG 当前小鸡状态（含NPC奖励进度）：")

            var hasNpcChicken = false
            for (i in 0 until animals.length()){
                val animal = animals.getJSONObject(i)
                val name = animal.optString("name")
                val animalId = animal.optString("animalId") //唯一ID
                val currentFarmId = animal.optString("currentFarmId")
                val masterFarmId = animal.optString("masterFarmId")
                val npcBizReward = animal.optDouble("npcBizReward", 0.0)
                val subAnimalType = animal.optString("subAnimalType")

                // 检查是否是NPC小鸡
                if (subAnimalType != "NPC") {
                    continue
                }else{
                    hasNpcChicken = true
                }
                // 打印小鸡状态
                Log.farm(" - $name 奖:${formatDouble(npcBizReward)}")

                // 根据 hire 配置找到对应任务场景与阈值（按animalId匹配）
                val match = findHireByAnimalId(animalId)
                if (match == null){
                    Log.farm("$TAG 未在雇佣配置中找到[$name]，跳过")
                    continue
                }

                val taskSceneCode = match.optString("taskSceneCode")
                val bizRewardName = match.optString("bizRewardName")
                val bizRewardThreshold = match.optDouble("bizRewardThreshold", 0.0)
                val directBizRewardAfterHire = match.optDouble("directBizRewardAfterHire", 0.0)
                val hireDuration = match.optInt("hireDuration")
                val hireCoolDownDays = match.optInt("hireCoolDownDays")

                // 友好打印：合并展示（当前奖励/阈值）
                Log.farm("$TAG $name taskSceneCode:$taskSceneCode P:${formatDouble(npcBizReward)}/${formatDouble(bizRewardThreshold)} 初:${formatDouble(directBizRewardAfterHire)} 期:${formatDaysFromSeconds(hireDuration)} 冷:${hireCoolDownDays}天")

                // 执行completeGoldTask前先检测是否达到或者超过了
                val nearFullBefore = isNearOrOverThreshold(npcBizReward, bizRewardThreshold)
                if (nearFullBefore){
                    Log.farm("$TAG [$name] 奖励超过/达到阈值，执行领取奖励")
                    try {
                        receiveAndSendBackAnimal(animalId,currentFarmId,masterFarmId)
                    } catch (e: Exception){
                        Log.error("$TAG.sendBackAnimal执行异常:$e")
                    }
                    continue
                }

                // 没有超过则去完成任务（传入 taskSceneCode）
                try {
                    completeGoldTask(taskSceneCode)
                } catch (e: Exception){
                    Log.error("$TAG.completeGoldTask执行异常:$e")
                }

                // 执行完后也检测一遍
                try {
                    // 重新查询小鸡状态
                    val afterResult = syncAnimalStatus(ownerFarmId, "SYNC_RESUME", "QUERY_ALL")
                    val afterJson = JSONObject(afterResult)
                    if (afterJson.optBoolean("success")) {
                        val afterSubFarmVO = afterJson.optJSONObject("subFarmVO")
                        val afterAnimals = afterSubFarmVO?.optJSONArray("animals") ?: JSONArray()
                        
                        // 找到对应的小鸡
                        for (j in 0 until afterAnimals.length()){
                            val afterAnimal = afterAnimals.getJSONObject(j)
                            if (afterAnimal.optString("animalId") == animalId) {
                                val afterNpcBizReward = afterAnimal.optDouble("npcBizReward", 0.0)
                                val nearFullAfter = isNearOrOverThreshold(afterNpcBizReward, bizRewardThreshold)
                                if (nearFullAfter){
                                    Log.farm("$TAG [$name] 完成任务后奖励超过/达到阈值，执行领取奖励")
                                    try {
                                        receiveAndSendBackAnimal(animalId,afterAnimal.optString("currentFarmId"),afterAnimal.optString("masterFarmId"))
                                    } catch (e: Exception){
                                        Log.error("$TAG.sendBackAnimal执行异常:$e")
                                    }
                                }
                                break
                            }
                        }
                    }
                } catch (e: Exception){
                    Log.error("$TAG 完成任务后检测异常:$e")
                }

                // 间隔一下，避免触发频控
                GlobalThreadPools.sleep(RandomUtil.nextInt(3000, 5000).toLong())
            }

            // 如果没有NPC小鸡，直接返回
            if (!hasNpcChicken) {
                Log.farm("$TAG 当前无NPC小鸡，可以去手动雇佣")
                return
            }
        } catch (e: Exception) {
            Log.error("$TAG.GoldenChicken查询/处理异常:$e")
        }
    }


    /**
     * 领取小鸡奖励
     */
    fun receiveAndSendBackAnimal(animalId: String,currentFarmId: String,masterFarmId: String){
        val method = "com.alipay.antfarm.sendBackAnimal"
        val params = "[{\"animalId\":\"$animalId\"," +
                "\"currentFarmId\":\"$currentFarmId\"," +
                "\"masterFarmId\":\"$masterFarmId\"," +
                "\"receiveNPCReward\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"sendType\":\"NORMAL\",\"source\":\"H5\",\"version\":\"1.8.2302070202.46\"}]"
        try{
            val result = JSONObject(RequestManager.requestString(method, params))
            if (result.optBoolean("success")) {
                Log.farm("$TAG 领取NPC小鸡奖励成功")
            }else{
                Log.error("$TAG 领取NPC小鸡奖励失败:${result}")
            }
        }catch (e: Exception){
            Log.error("$TAG.sendBackAnimal异常:$e")
        }
    }

    /**
     * 完成黄金小鸡任务（由调用方传入 taskSceneCode）
     */
    private fun completeGoldTask(taskSceneCode: String) {
        try {
            val s = listGoldenChickenTask(taskSceneCode)
            val taskJson = JSONObject(s)
            if (taskJson.getBoolean("success")) {
                val farmTaskList = taskJson.getJSONArray("farmTaskList")
                for (i in 0 until farmTaskList.length()) {
                    val taskListJson = farmTaskList.getJSONObject(i)
                    val awardCount = taskListJson.optInt("awardCount")
                    val bizKey = taskListJson.optString("bizKey")
                    val taskStatus = taskListJson.optString("taskStatus")
                    val title = taskListJson.optString("title")
                    val desc = taskListJson.optString("desc")

                    // 领取奖励在任务完成且未领取状态下
                    if (taskStatus == "FINISHED") {
                        val s2 = receiveGoldenChickenAward(bizKey,taskSceneCode)
                        val jsonObject = JSONObject(s2)
                        if (jsonObject.optBoolean("success")) {
                            Log.farm("$TAG 领取[$title]成功:$desc")
                        }
                    }
                    // 做任务
                    else if (taskStatus == "TODO") {
                        val s1 = doGoldenChickenTask(bizKey,taskSceneCode)
                        val jsonObject = JSONObject(s1)
                        if (jsonObject.optBoolean("success")) {
                            Log.farm("$TAG 完成[$title]")
                        }
                        GlobalThreadPools.sleep(RandomUtil.nextInt(2000, 3000).toLong())
                        val s12 = receiveGoldenChickenAward(bizKey,taskSceneCode)
                        val jsonObject1 = JSONObject(s12)
                        if (jsonObject1.optBoolean("success")) {
                            Log.farm("$TAG 领取[$title]成功:$desc")
                        }
                    }

                    GlobalThreadPools.sleep(RandomUtil.nextInt(5000, 7000).toLong())
                }
            }
        } catch (e: Exception) {
            Log.error("$TAG.completeGoldTask异常:$e")
        }
    }

    private fun initFarm(){
        val userId = UserMap.currentUid
        val jo = JSONObject(AntFarmRpcCall.enterFarm(userId, userId))
        if (ResChecker.checkRes(TAG + "进入庄园失败:", jo)) {
            //获取全部npc小鸡配置（任务、阈值等）
            npcHires = jo.optJSONArray("hireNPCAnimalConfigInfoList")
            // 跳过打印
            if (Status.hasFlagToday("NpcChicken")){
                return
            }
            Log.farm("$TAG 进入庄园成功，获取NPC配置成功。")
            // 友好打印 NPC 配置列表（不含实时奖励值）
            Log.runtime("$TAG===================================")
            Log.runtime("$TAG 可用NPC配置：")
            val hires = npcHires

            if (hires != null) {
                Log.farm("$TAG 获取到${hires.length()}个NPC配置")
                for (i in 0 until hires.length()){
                    val hire = hires.getJSONObject(i)
                    val animalId = hire.optString("animalId")
                    val bizRewardName = hire.optString("bizRewardName")
                    val hireDuration = hire.optInt("hireDuration")
                    val hireCoolDownDays = hire.optInt("hireCoolDownDays")
                    val bizRewardThreshold = hire.optDouble("bizRewardThreshold")
                    val directBizRewardAfterHire = hire.optDouble("directBizRewardAfterHire")
                    val taskSceneCode = hire.optString("taskSceneCode")
                    val npcAnimalType = hire.optString("npcAnimalType")

                    Log.runtime("$TAG 名称:$bizRewardName 场景:$taskSceneCode 满值:${formatDouble(bizRewardThreshold)} 初:${formatDouble(directBizRewardAfterHire)} 期间:${formatDaysFromSeconds(hireDuration)} 冷却:${hireCoolDownDays}天 型:$npcAnimalType ID:$animalId")
                }
                Log.runtime("$TAG===================================")
                Status.setFlagToday("NpcChicken")
            } else {
                Log.farm("$TAG 未获取到NPC配置列表，可能的原因：1.接口返回数据格式变化 2.服务器问题")
                Log.runtime("$TAG 原始返回数据: ${jo.toString()}")
            }
        } else {
            Log.farm("$TAG 进入庄园失败，无法获取NPC配置")
        }
    }

    private fun findHireByAnimalId(animalId: String?): JSONObject? {
        val hires = npcHires ?: return null
        if (animalId.isNullOrEmpty()) return null

        for (i in 0 until hires.length()){
            val hire = hires.getJSONObject(i)
            val configAnimalId = hire.optString("animalId")

            // 直接通过animalId匹配
            if (configAnimalId == animalId) {
                return hire
            }
        }
        return null
    }



    private fun isNearOrOverThreshold(current: Double, threshold: Double): Boolean {
        if (threshold <= 0.0) return false
        // 等于或超过
        return current >= threshold
    }

    private fun formatDaysFromSeconds(seconds: Int): String {
        if (seconds <= 0) return "0天"
        val days = seconds / 86400
        return days.toString() + "天"
    }

    private fun formatDouble(d: Double): String {
        return if (d == 0.0) "0" else String.format("%.2f", d)
    }

    /**
     * 雇佣黄金小鸡
     */
    private val  source= "licaixiaoji_2025_3"
    fun hireGoldenChicken(): String {
        val method = "com.alipay.antfarm.hireAnimal"
        val data =
            "[{\"hireActionType\":\"HIRE_IN_SELF_FARM\",\"hireAnimalId\":\"20250725105101013088000000000007\"," +
                    "\"isNpcAnimal\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," +
                    "\"source\":\"$source\",\"version\":\"$VERSION\"}]"
        return requestString(method, data)
    }

    /**
     * 黄金小鸡领取奖励
     */
    fun receiveGoldenChickenAward(taskId: String,taskSceneCode: String): String {
        val method = "com.alipay.antfarm.receiveFarmTaskAward"
        val data =
            "[{\"awardType\":\"NPC_ANIMAL_FOOD\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," +
                    "\"source\":\"H5\",\"taskId\":\"$taskId\",\"taskSceneCode\":\"$taskSceneCode\"," +
                    "\"version\":\"$VERSION\"}]"
        return requestString(method, data)
    }

    /**
     * 获取黄金小鸡任务列表
     */
    fun listGoldenChickenTask(taskSceneCode: String): String {
        val method = "com.alipay.antfarm.listFarmTask"
        val data =
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\",\"source\":\"H5\"," +
                    "\"taskSceneCode\":\"$taskSceneCode\",\"version\":\"$VERSION\"}]"
        return requestString(method, data)
    }

    /**
     * 做黄金小鸡任务
     */
    fun doGoldenChickenTask(taskId: String,taskSceneCode: String): String {
        return requestString(
            "com.alipay.antfarm.doFarmTask",
            "[{\"bizKey\":\"$taskId\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," +
                    "\"source\":\"H5\",\"taskSceneCode\":\"$taskSceneCode\",\"version\":\"$VERSION\"}]"
        )
    }
    @Throws(JSONException::class)
    fun syncAnimalStatus(farmId: String?, operTag: String?, operType: String?): String {
        val args = JSONObject()
        args.put("farmId", farmId)
        args.put("operTag", operTag)
        args.put("operType", operType)
        args.put("requestType", "NORMAL")
        args.put("sceneCode", "ANTFARM")
        args.put("source", "H5")
        args.put("version", VERSION)
        val params = "[" + args + "]"
        return requestString("com.alipay.antfarm.syncAnimalStatus", params)
    }
    @Throws(JSONException::class)
    fun syncAnimalStatus2(farmId: String?, operTag: String?, operType: String?): String {
        val params = "[{\"farmId\":\"$farmId\",\"operTag\":\"SYNC__NPC_TASKLIST_INIT\"," +
                "\"operType\":\"QUERY_FARM_INFO\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," +
                "\"source\":\"licaixiaoji_2025_1\",\"version\":\"1.8.2302070202.46\"}]"
        return requestString("com.alipay.antfarm.syncAnimalStatus", params)
    }

    // ========== 智能调度相关 ==========

    // NPC 配置数据类
    private data class NpcSmartConfig(
        val animalId: String,
        val source: String,
        val nickName: String,
        val taskSceneCode: String,
        val coolDownDays: Int,
        val rewardThreshold: Double
    )

    // NPC 配置映射（昵称 -> 配置）
    private val NPC_CONFIG_MAP = mapOf(
        "黄金鸡" to NpcSmartConfig(
            animalId = "20250725105101013088000000000004",
            source = "licaixiaoji_2025_1",
            nickName = "黄金鸡",
            taskSceneCode = "ANTFARM_CAIFU_NPC_TASK",
            coolDownDays = 4,
            rewardThreshold = 2888.0
        ),
        "芝麻大表鸽" to NpcSmartConfig(
            animalId = "20250901105101013088000000000006",
            source = "zhimaxiaoji_lianjin",
            nickName = "芝麻大表鸽",
            taskSceneCode = "ANTFARM_ZHIMA_NPC_TASK",
            coolDownDays = 1,
            rewardThreshold = 88.0
        ),
        "农场小鸡" to NpcSmartConfig(
            animalId = "20250613105101013088000000000002",
            source = "feiliaoji_202507",
            nickName = "农场小鸡",
            taskSceneCode = "ANTFARM_ORCHARD_NPC_TASK",
            coolDownDays = 7,
            rewardThreshold = 500.0
        )
    )

    // NPC 雇佣记录
    private data class NpcHireRecord(
        val config: NpcSmartConfig,
        val lastSendBackTime: Long = 0L
    ) {
        fun isInCoolDown(): Boolean {
            if (lastSendBackTime == 0L) return false
            val coolDownMillis = config.coolDownDays * 24 * 60 * 60 * 1000L
            return System.currentTimeMillis() - lastSendBackTime < coolDownMillis
        }

        fun getRemainingCoolDownHours(): Double {
            if (!isInCoolDown()) return 0.0
            val coolDownMillis = config.coolDownDays * 24 * 60 * 60 * 1000L
            val elapsed = System.currentTimeMillis() - lastSendBackTime
            val remaining = coolDownMillis - elapsed
            return remaining / (60.0 * 60 * 1000)
        }
    }

    /**
     * 智能调度器主入口
     */
    fun runSmartScheduler(ownerFarmId: String, selectedNpcNames: List<String>) {
        try {
            this.ownerFarmId = ownerFarmId

            // 1. 初始化农场，获取NPC配置
            initFarm()

            // 2. 转换为配置对象列表
            val selectedConfigs = selectedNpcNames.mapNotNull { NPC_CONFIG_MAP[it] }

            if (selectedConfigs.isEmpty()) {
                Log.runtime(TAG, "智能调度🤖[未选择有效的NPC]")
                return
            }

            Log.runtime(TAG, "智能调度🤖[已选择: ${selectedConfigs.joinToString(", ") { it.nickName }}]")

            // 3. 加载历史记录
            val records = loadNpcRecords(selectedConfigs)

            // 4. 查询当前农场状态
            val currentNpc = getCurrentNpc()

            // 5. 执行智能调度
            executeSmartSchedule(selectedConfigs, records, currentNpc)

        } catch (e: Exception) {
            Log.error("$TAG.runSmartScheduler异常:$e")
        }
    }

    /**
     * 获取当前农场的NPC信息
     */
    private fun getCurrentNpc(): JSONObject? {
        try {
            val syncRes = syncAnimalStatus(ownerFarmId, "SYNC_NPC", "QUERY_FARM_INFO")
            val joSync = JSONObject(syncRes)

            if (!joSync.optBoolean("success")) {
                return null
            }

            val subFarmVO = joSync.optJSONObject("subFarmVO")
            val animals = subFarmVO?.optJSONArray("animals") ?: return null

            for (i in 0 until animals.length()) {
                val animal = animals.getJSONObject(i)
                if ("NPC" == animal.optString("subAnimalType")) {
                    return animal
                }
            }
        } catch (e: Exception) {
            Log.error("$TAG.getCurrentNpc异常:$e")
        }
        return null
    }

    /**
     * 执行智能调度逻辑
     */
    private fun executeSmartSchedule(
        configs: List<NpcSmartConfig>,
        records: Map<String, NpcHireRecord>,
        currentNpc: JSONObject?
    ) {
        // 场景1：当前没有NPC
        if (currentNpc == null) {
            Log.runtime(TAG, "智能调度🤖[当前无NPC，开始选择雇佣]")
            hireNextAvailableNpc(configs, records)
            return
        }

        // 场景2：当前有NPC
        val currentAnimalId = currentNpc.optString("animalId")
        val currentName = currentNpc.optString("name", "未知NPC")
        val currentReward = currentNpc.optDouble("npcBizReward", 0.0)

        // 找到当前NPC的配置
        val currentConfig = configs.find { it.animalId == currentAnimalId }

        if (currentConfig == null) {
            // 当前NPC不在选中列表中，遣返并雇佣新的
            Log.runtime(TAG, "智能调度🤖[当前NPC[$currentName]不在选中列表，执行遣返]")
            sendBackNpcAndRecord(currentNpc, null)
            GlobalThreadPools.sleep(2000)
            hireNextAvailableNpc(configs, records)
            return
        }

        // 检查是否满产
        val isFull = currentReward >= currentConfig.rewardThreshold

        if (isFull) {
            Log.farm("智能调度🤖[$currentName 已满产($currentReward/${currentConfig.rewardThreshold})，领取并切换]")

            // 遣返并记录时间
            sendBackNpcAndRecord(currentNpc, currentConfig)

            // 重新加载记录
            val updatedRecords = loadNpcRecords(configs)

            // 雇佣下一个可用的NPC
            GlobalThreadPools.sleep(2000)
            hireNextAvailableNpc(configs, updatedRecords)
        } else {
            // 未满产，执行任务
            Log.runtime(TAG, "智能调度🤖[$currentName 工作中... 进度:$currentReward/${currentConfig.rewardThreshold}]")

            // 执行对应的任务
            try {
                completeGoldTask(currentConfig.taskSceneCode)

                // 任务完成后再次检查是否满产
                val updatedNpc = getCurrentNpc()
                if (updatedNpc != null) {
                    val updatedReward = updatedNpc.optDouble("npcBizReward", 0.0)
                    if (updatedReward >= currentConfig.rewardThreshold) {
                        Log.farm("智能调度🤖[$currentName 完成任务后已满产，领取并切换]")
                        sendBackNpcAndRecord(updatedNpc, currentConfig)
                        GlobalThreadPools.sleep(2000)
                        val updatedRecords = loadNpcRecords(configs)
                        hireNextAvailableNpc(configs, updatedRecords)
                    }
                }
            } catch (e: Exception) {
                Log.error("$TAG.executeSmartSchedule任务执行异常:$e")
            }
        }
    }

    /**
     * 雇佣下一个可用的NPC
     */
    private fun hireNextAvailableNpc(configs: List<NpcSmartConfig>, records: Map<String, NpcHireRecord>) {
        var animals = getFarmAnimals()
        var animalCount = animals.length()
        
        if (animalCount >= 3) {
            Log.farm(TAG, "智能调度🤖[当前小鸡数已满 ($animalCount)，尝试赶走别人的小鸡释放位置]")
            trySendBackGuestChickens(animals)
            // 赶走之后重新获取
            animals = getFarmAnimals()
            animalCount = animals.length()
        }
        
        if (animalCount >= 3) {
            Log.farm(TAG, "智能调度🤖[庄园无小鸡位置，先不雇佣]")
            return
        }

        // 按优先级查找第一个不在冷却期的NPC
        for (config in configs) {
            val record = records[config.nickName]
            if (record == null || !record.isInCoolDown()) {
                // 可以雇佣
                Log.runtime(TAG, "智能调度🤖[准备雇佣${config.nickName}]")
                if (hireNpcSmart(config)) {
                    Log.farm("智能调度🤖[成功雇佣${config.nickName}]")
                    return
                } else {
                    Log.runtime(TAG, "智能调度🤖[雇佣${config.nickName}失败]")
                }
            } else {
                val remainingHours = record.getRemainingCoolDownHours()
                Log.runtime(TAG, "智能调度🤖[${config.nickName}冷却中，剩余${String.format("%.1f", remainingHours)}小时]")
            }
        }

        Log.runtime(TAG, "智能调度🤖[所有NPC都在冷却期或雇佣失败]")
    }

    /**
     * 遣返NPC并记录时间
     */
    private fun sendBackNpcAndRecord(npcJson: JSONObject, config: NpcSmartConfig?) {
        try {
            val animalId = npcJson.optString("animalId")
            val currentFarmId = npcJson.optString("currentFarmId")
            val masterFarmId = npcJson.optString("masterFarmId")

            receiveAndSendBackAnimal(animalId, currentFarmId, masterFarmId)

            // 记录遣返时间
            if (config != null) {
                saveNpcRecord(config, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.error("$TAG.sendBackNpcAndRecord异常:$e")
        }
    }

    /**
     * 雇佣NPC（智能调度版本）
     */
    private fun hireNpcSmart(config: NpcSmartConfig): Boolean {
        try {
            val method = "com.alipay.antfarm.hireAnimal"
            val data = "[{\"hireActionType\":\"HIRE_IN_SELF_FARM\",\"hireAnimalId\":\"${config.animalId}\"," +
                    "\"isNpcAnimal\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM\"," +
                    "\"source\":\"${config.source}\",\"version\":\"$VERSION\"}]"
            val result = JSONObject(requestString(method, data))
            return result.optBoolean("success")
        } catch (e: Exception) {
            Log.error("$TAG.hireNpcSmart异常:$e")
        }
        return false
    }

    /**
     * 加载NPC记录
     */
    private fun loadNpcRecords(configs: List<NpcSmartConfig>): Map<String, NpcHireRecord> {
        val records = mutableMapOf<String, NpcHireRecord>()
        try {
            // 从 DataStore 读取所有 NPC 的冷却记录
            val coolDownData = DataStore.getOrCreate<Map<String, Long>>("NpcCoolDownRecords")

            for (config in configs) {
                val lastTime = coolDownData[config.nickName] ?: 0L
                records[config.nickName] = NpcHireRecord(config, lastTime)
            }
        } catch (e: Exception) {
            Log.error("$TAG.loadNpcRecords异常:$e")
            // 如果加载失败，返回空记录
            for (config in configs) {
                records[config.nickName] = NpcHireRecord(config, 0L)
            }
        }
        return records
    }

    /**
     * 保存NPC记录
     */
    private fun saveNpcRecord(config: NpcSmartConfig, sendBackTime: Long) {
        try {
            // 读取现有数据
            val coolDownData = DataStore.getOrCreate<MutableMap<String, Long>>("NpcCoolDownRecords")

            // 更新指定 NPC 的记录
            coolDownData[config.nickName] = sendBackTime

            // 保存回 DataStore
            DataStore.put("NpcCoolDownRecords", coolDownData)

            Log.runtime(TAG, "智能调度🤖[记录${config.nickName}遣返时间，冷却${config.coolDownDays}天]")
        } catch (e: Exception) {
            Log.error("$TAG.saveNpcRecord异常:$e")
        }
    }

    /**
     * 获取庄园里的小鸡列表
     */
    private fun getFarmAnimals(): JSONArray {
        try {
            val syncRes = syncAnimalStatus(ownerFarmId, "SYNC_RESUME", "QUERY_ALL")
            val joSync = JSONObject(syncRes)
            if (joSync.optBoolean("success")) {
                val subFarmVO = joSync.optJSONObject("subFarmVO")
                return subFarmVO?.optJSONArray("animals") ?: JSONArray()
            }
        } catch (e: Exception) {
            Log.error("$TAG.getFarmAnimals异常:$e")
        }
        return JSONArray()
    }

    /**
     * 驱赶别的窃食小鸡
     */
    private fun trySendBackGuestChickens(animals: JSONArray) {
        for (i in 0 until animals.length()) {
            val animal = animals.getJSONObject(i)
            val subAnimalType = animal.optString("subAnimalType")
            val masterFarmId = animal.optString("masterFarmId")

            // 别人的小鸡：masterFarmId 不等于我们，且 subAnimalType 是 GUEST (偷吃鸡)
            if (masterFarmId.isNotEmpty() && masterFarmId != ownerFarmId && subAnimalType == "GUEST") {
                val animalId = animal.optString("animalId")
                val currentFarmId = animal.optString("currentFarmId")
                val nickname = animal.optString("name", "别人的小鸡")
                Log.farm("$TAG 庄园位置不足，尝试驱赶: $nickname ($animalId)")
                try {
                    // 使用常规驱赶
                    val s = AntFarmRpcCall.sendBackAnimal("常规", animalId, currentFarmId, masterFarmId)
                    val result = JSONObject(s)
                    if (result.optBoolean("success")) {
                        Log.farm("$TAG 成功赶走小鸡: $nickname")
                    } else {
                        Log.runtime(TAG, "赶小鸡失败: ${result.optString("memo")}")
                    }
                } catch (e: Exception) {
                    Log.error("$TAG 赶小鸡异常: $e")
                }
            }
        }
    }
}
