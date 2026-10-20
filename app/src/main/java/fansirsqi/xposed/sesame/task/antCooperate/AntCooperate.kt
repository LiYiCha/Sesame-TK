package fansirsqi.xposed.sesame.task.antCooperate

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.entity.CooperateEntity.Companion.getList
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectAndCountModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.TaskCommon
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.CooperateMap
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject

class AntCooperate : ModelTask() {
    /**
     * 获取任务名称
     *
     * @return 合种任务名称
     */
    override fun getName(): String {
        return "蚂蚁森林合种" //保留这个全称
    }

    /**
     * 获取任务分组
     *
     * @return 森林分组
     */
    override fun getGroup(): ModelGroup {
        return ModelGroup.FOREST
    }

    /**
     * 获取任务图标
     *
     * @return 合种任务图标文件名
     */
    override fun getIcon(): String {
        return "AntCooperate.png"
    }

    private val cooperateWater = BooleanModelField("cooperateWater", "合种浇水|开启", false)
    private val cooperateWaterList = SelectAndCountModelField(
        "cooperateWaterList",
        "合种浇水列表",
        LinkedHashMap<String?, Int?>(),
        { getList() },
        "打开上面的开关后执行一次后再重新回来应该能加载出来"
    )
    private val cooperateWaterTotalLimitList = SelectAndCountModelField(
        "cooperateWaterTotalLimitList",
        "浇水总量限制列表",
        LinkedHashMap<String?, Int?>(),
        { getList() },
        "当浇满后理论不会再浇了"
    )
    private val cooperateSendCooperateBeckon = BooleanModelField("cooperateSendCooperateBeckon", "合种 | 召唤队友浇水| 仅队长 ", false)
    private val loveCooperateWater = BooleanModelField("loveCooperateWater", "真爱合种 | 浇水", false)
    private val loveCooperateWaterNum = IntegerModelField("loveCooperateWaterNum", "真爱合种 | 每日浇水总量(g)", 20)
    private val teamCooperateWaterNum = IntegerModelField("teamCooperateWaterNum", "组队合种 | 浇水克数(0为关闭，10-5000)", 0)
    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(cooperateWater)
        modelFields.addField(cooperateWaterList)
        modelFields.addField(cooperateWaterTotalLimitList)
        modelFields.addField(cooperateSendCooperateBeckon)
        // 真爱合种配置
        modelFields.addField(loveCooperateWater)
        modelFields.addField(loveCooperateWaterNum)
        // 组队合种配置
        modelFields.addField(teamCooperateWaterNum)
        return modelFields
    }

    /**
     * 执行合种任务的主要逻辑
     */
    override suspend fun runSuspend() {
        try {
            Log.runtime(TAG, "执行开始-$name")

            // 1. 真爱合种
            if (loveCooperateWater.value) {
                loveCooperateWater()
            }

            // 2. 组队合种
            if (teamCooperateWaterNum.value > 0) {
                teamCooperateWater()

            }
            // 3. 普通合种
            if (cooperateWater.value) {
                val queryUserCooperatePlantList = JSONObject(AntCooperateRpcCall.queryUserCooperatePlantList())
                if (ResChecker.checkRes(TAG, queryUserCooperatePlantList)) {
                    // 1. 获取当前能量，设为 var，因为浇水后本地需要扣减，否则下一个合种会误判能量充足
                    var userCurrentEnergy = queryUserCooperatePlantList.getInt("userCurrentEnergy")
                    val cooperatePlants = queryUserCooperatePlantList.getJSONArray("cooperatePlants")
                    Log.runtime(TAG, "获取合种列表成功: ${cooperatePlants.length()} 颗合种")
                    for (i in 0 until cooperatePlants.length()) {
                        var plant = cooperatePlants.getJSONObject(i)
                        val cooperationId = plant.getString("cooperationId")
                        // 补全缺失的合种名称信息
                        if (!plant.has("name")) {
                            val detailResp = AntCooperateRpcCall.queryCooperatePlant(cooperationId)
                            plant = JSONObject(detailResp).getJSONObject("cooperatePlant")
                        }

                        val name = plant.getString("name")
                        val admin = plant.getString("admin")

                        // 2. 合种打招呼逻辑 (独立判断，不影响浇水主流程)
                        if (cooperateSendCooperateBeckon.value && UserMap.currentUid == admin) {
                            cooperateSendCooperateBeckon(cooperationId, name)
                        }

                        // 3. 记录合种信息到本地 Map
                        CooperateMap.getInstance(CooperateMap::class.java).add(cooperationId, name)

                        // 4. 检查是否满足“今日是否可浇水”的本地状态缓存
                        if (!Status.canCooperateWaterToday(UserMap.currentUid, cooperationId)) {
                            // Log.runtime(TAG, "$name 今日已标记为不可浇水/已浇完")
                            continue
                        }

                        // 获取服务端限制
                        val waterDayLimit = plant.getInt("waterDayLimit") // 今日剩余可浇水量
                        val waterLimit = plant.getJSONObject("cooperateTemplate").getInt("waterLimit") // 每日总上限
                        // val watered = waterLimit - waterDayLimit
                        Log.runtime(TAG, "获取合种[$name] 浇水信息: 剩余可浇 $waterDayLimit g / 总限制 $waterLimit g")

                        // 5. 获取配置
                        val configPerRound = cooperateWaterList.value[cooperationId] // 本轮配置浇水量
                        val configTotalLimit = cooperateWaterTotalLimitList.value[cooperationId] // 配置的总浇水上限(累计)

                        if (configPerRound == null) {
                            Log.runtime(TAG, "浇水列表中没有为[$name]配置，跳过")
                            continue
                        }

                        // 6. 计算本轮目标浇水量 (Target Water)
                        var planToWater: Int

                        if (configTotalLimit == null) {
                            // 逻辑保持原意：如果没有配置总限制，则直接把今日剩余额度拉满
                            Log.runtime(TAG, "未配置 $name 限制总浇水，目标为填满今日可浇水量（服务端或本地限制）")
                            planToWater = waterDayLimit
                        } else {
                            Log.runtime(TAG, "载入配置 $name 限制总浇水[$configTotalLimit]g")
                            val totalWatered = getTotalWatering(cooperationId) // 获取已累计浇水

                            if (totalWatered < 0) {
                                Log.runtime(TAG, "无法获取用户[${UserMap.currentUid}]的累计浇水数据，跳过 $name")
                                continue
                            }

                            val remainingQuota = configTotalLimit - totalWatered
                            if (remainingQuota <= 0) {
                                Log.forest("$name 累计浇水已达标($totalWatered/$configTotalLimit)，跳过")
                                continue
                            }

                            planToWater = remainingQuota
                        }

                        // 7. 最终数值修正 (核心优化：统一使用 min 逻辑)
                        // 实际浇水量 = Min(计划量, 今日剩余可浇量, 当前背包能量)
                        var actualWater = planToWater

                        if (actualWater > waterDayLimit) actualWater = waterDayLimit
                        if (actualWater > configPerRound) actualWater = configPerRound
                        if (actualWater > userCurrentEnergy) actualWater = userCurrentEnergy

                        Log.runtime(TAG, "[$name] 结算: 计划 $planToWater, 剩余限额 $waterDayLimit, 背包 $userCurrentEnergy -> 实际: $actualWater")

                        // 8. 执行浇水
                        if (actualWater > 0) {
                            cooperateWater(cooperationId, actualWater, name)
                            // !!! 关键修正：本地扣除能量，供下一次循环判断使用 !!!
                            userCurrentEnergy -= actualWater
                        } else {
                            Log.runtime(TAG, "浇水列表中没有为[$name]配置")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
        } finally {
            CooperateMap.getInstance(CooperateMap::class.java).save(UserMap.currentUid)
            Log.runtime(TAG, "执行结束-$name")
        }
    }

    // 真爱合种：读取本人今日已浇水量，记录缺失返回 -1
    private fun loveTeamWateredAmount(teamInfo: JSONObject): Int {
        val userId = UserMap.currentUid?.takeIf { it.isNotBlank() } ?: return -1
        val todayWaterMap = teamInfo.optJSONObject("waterInfo")?.optJSONObject("todayWaterMap") ?: return -1
        return if (todayWaterMap.has(userId)) todayWaterMap.optInt(userId, -1) else 0
    }

    // 真爱合种逻辑：以服务端今日已浇量补足每日目标，浇水后回查确认
    private fun loveCooperateWater() {
        try {
            val target = loveCooperateWaterNum.value ?: 0
            if (target < 20) {
                Log.forest("真爱合种目标不足单次最小浇水量20g，不执行")
                return
            }

            val queryLoveHome = try {
                JSONObject(AntCooperateRpcCall.queryLoveHome())
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "真爱合种响应JSON解析失败", e)
                return
            }

            if (!ResChecker.checkRes(TAG, queryLoveHome)) {
                return
            }

            val teamInfo = queryLoveHome.optJSONObject("teamInfo")
            if (teamInfo == null) {
                Log.error(TAG, "未找到真爱合种队伍信息，可能是未开启或结构变更")
                return
            }

            val teamId = queryLoveHome.optJSONObject("userInfo")?.optString("teamId").orEmpty()
                .ifBlank { teamInfo.optString("teamId") }
            val teamStatus = teamInfo.optString("teamStatus")
            if (teamId.isBlank() || teamStatus.isBlank()) {
                Log.error(TAG, "真爱合种缺少队伍标识或状态：$queryLoveHome")
                return
            }
            if (teamStatus != "ACTIVATED") {
                Log.forest("真爱合种当前状态[$teamStatus]，尚未激活")
                return
            }

            val myWateredAmount = loveTeamWateredAmount(teamInfo)
            if (myWateredAmount < 0) {
                Log.error(TAG, "真爱合种缺少今日浇水记录：$teamInfo")
                return
            }

            val waterAmount = target - myWateredAmount
            if (waterAmount < 20) {
                Log.forest("真爱合种今日已浇${myWateredAmount}g / 目标${target}g，剩余额度不足20g")
                if (myWateredAmount >= target) Status.setFlagToday("love::teamWater")
                return
            }

            try {
                val waterJo = JSONObject(AntCooperateRpcCall.loveTeamWater(teamId, waterAmount))
                if (!ResChecker.checkRes(TAG, waterJo)) {
                    Log.error(TAG, "真爱合种浇水响应未确认，将查询实际进度：$waterJo")
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "真爱合种浇水响应异常，将查询实际进度", e)
            }

            val confirmation = JSONObject(AntCooperateRpcCall.queryLoveHome())
            if (!ResChecker.checkRes(TAG, confirmation)) {
                Log.error(TAG, "真爱合种浇水后无法确认状态，下轮重试：${confirmation.optString("resultCode")}")
                return
            }
            val confirmedWateredAmount = confirmation.optJSONObject("teamInfo")?.let(::loveTeamWateredAmount) ?: 0
            if (confirmedWateredAmount > myWateredAmount) {
                Log.forest("真爱合种💖今日已浇${confirmedWateredAmount}g / 目标${target}g，本轮增加${confirmedWateredAmount - myWateredAmount}g")
                if (confirmedWateredAmount >= target) Status.setFlagToday("love::teamWater")
            } else {
                Log.error(TAG, "真爱合种浇水后未确认今日浇水记录，下轮重试")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "loveCooperateWater 异常:", t)
        }
    }

    // 组队合种浇水逻辑：今日已浇量取服务端成员真值，浇水后回查确认
    private fun teamCooperateWater() {
        var switchedToTeamMode = false
        try {
            val userDailyTarget = teamCooperateWaterNum.value ?: 0
            if (userDailyTarget < 10) {
                Log.forest("组队合种目标不足单次最小浇水量10g，不执行")
                return
            }

            val homeJo = JSONObject(AntCooperateRpcCall.queryHomePage())
            if (!ResChecker.checkRes(TAG, homeJo)) {
                Log.forest("queryHomePage 返回异常")
                return
            }

            val teamId = homeJo.optJSONObject("teamHomeResult")
                ?.optJSONObject("teamBaseInfo")
                ?.optString("teamId")
                ?.takeIf { it.isNotBlank() }
            if (teamId == null) {
                Log.forest("未获取到组队合种 TeamID")
                return
            }

            val memberResponse = JSONObject(AntCooperateRpcCall.queryTeamMemberList(teamId))
            if (!ResChecker.checkRes(TAG, memberResponse)) return
            val self = memberResponse.optJSONArray("memberList")?.let { list ->
                (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                    .firstOrNull { it.optBoolean("self") || it.optString("userId") == UserMap.currentUid }
            }
            val todayUsed = self?.optInt("todayEnergy", -1) ?: -1
            if (todayUsed < 0) {
                Log.error(TAG, "组队合种缺少本人的今日浇水记录：$memberResponse")
                return
            }
            Status.setIntFlagToday(StatusFlags.FLAG_TEAM_WATER_DAILY_COUNT, todayUsed)

            val userRemainingQuota = userDailyTarget - todayUsed
            if (userRemainingQuota < 10) {
                Log.forest("组队合种今日已浇${todayUsed}g / 目标${userDailyTarget}g，剩余不足10g")
                return
            }

            if (homeJo.optString("nextAction") !in setOf("Team", "Cultivate")) {
                Log.error(TAG, "组队合种无法确认当前森林模式：${homeJo.optString("nextAction")}")
                return
            }

            val currentEnergy = homeJo.optJSONObject("userBaseInfo")?.optInt("currentEnergy", -1) ?: -1
            if (currentEnergy < 0) {
                Log.error(TAG, "组队合种缺少可用能量，未执行浇水")
                return
            }
            if (currentEnergy < 10) {
                Log.forest("当前能量不足10g (${currentEnergy}g)，无法浇水")
                return
            }

            if (!isTeam(homeJo)) {
                switchedToTeamMode = true
                val modeResponse = JSONObject(AntCooperateRpcCall.updateUserConfig(true))
                if (!ResChecker.checkRes(TAG, modeResponse)) {
                    Log.error(TAG, "切换组队模式失败：${modeResponse.optString("resultDesc")}")
                    return
                }
                val switchedHome = JSONObject(AntCooperateRpcCall.queryHomePage())
                if (!ResChecker.checkRes(TAG, switchedHome) || !isTeam(switchedHome)) {
                    Log.error(TAG, "未确认切换到团队模式：$switchedHome")
                    return
                }
                Log.forest("已临时切换至组队浇水模式")
            }

            val miscJo = JSONObject(AntCooperateRpcCall.queryMiscInfo("teamCanWaterCount", teamId))
            if (!ResChecker.checkRes(TAG, miscJo)) {
                Log.forest("queryMiscInfo 查询失败")
                return
            }
            val serverRemaining = miscJo.optJSONObject("combineHandlerVOMap")
                ?.optJSONObject("teamCanWaterCount")
                ?.optInt("waterCount", -1) ?: -1
            if (serverRemaining < 0) {
                Log.error(TAG, "组队合种查询未返回剩余浇水额度：$miscJo")
                return
            }

            Log.forest("组队状态检查: 目标剩余${userRemainingQuota}g | 官方剩余${serverRemaining}g | 背包能量${currentEnergy}g")
            if (serverRemaining < 10) {
                Log.forest("官方限制今日无可浇水额度，跳过")
                return
            }

            val finalWaterAmount = userRemainingQuota
                .coerceAtMost(serverRemaining)
                .coerceAtMost(currentEnergy)
            if (finalWaterAmount < 10) {
                Log.forest("计算后浇水量(${finalWaterAmount}g)低于最小限制10g，不执行")
                return
            }

            try {
                val waterJo = JSONObject(AntCooperateRpcCall.teamWater(teamId, finalWaterAmount))
                if (!ResChecker.checkRes(TAG, waterJo)) Log.error(TAG, "组队浇水响应未确认，将查询实际进度：$waterJo")
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "组队浇水响应异常，将查询实际进度", e)
            }

            val confirmation = JSONObject(AntCooperateRpcCall.queryTeamMemberList(teamId))
            if (!ResChecker.checkRes(TAG, confirmation)) return
            val confirmedSelf = confirmation.optJSONArray("memberList")?.let { list ->
                (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                    .firstOrNull { it.optBoolean("self") || it.optString("userId") == UserMap.currentUid }
            }
            val confirmedTotal = confirmedSelf?.optInt("todayEnergy", -1) ?: -1
            if (confirmedTotal > todayUsed) {
                Status.setIntFlagToday(StatusFlags.FLAG_TEAM_WATER_DAILY_COUNT, confirmedTotal)
                Log.forest("组队合种🌲今日已浇${confirmedTotal}g / 目标${userDailyTarget}g，本轮增加${confirmedTotal - todayUsed}g")
            } else {
                Log.error(TAG, "组队浇水后未确认进度，未增加本地计数：$confirmation")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "teamCooperateWater 异常:", t)
        } finally {
            if (switchedToTeamMode) {
                restorePersonalCooperateMode()
            }
        }
    }

    // 临时切到组队模式后，恢复运行前的个人模式并回查确认
    private fun restorePersonalCooperateMode() {
        try {
            val response = JSONObject(AntCooperateRpcCall.updateUserConfig(false))
            if (ResChecker.checkRes(TAG, response)) {
                val home = JSONObject(AntCooperateRpcCall.queryHomePage())
                if (ResChecker.checkRes(TAG, home) && home.optString("nextAction") == "Cultivate") {
                    Log.forest("已恢复运行前的个人模式")
                } else {
                    Log.error(TAG, "未确认森林恢复个人模式：$home")
                }
            } else {
                Log.error(TAG, "恢复个人模式失败：${response.optString("resultDesc")}")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "restorePersonalCooperateMode 异常:", t)
        }
    }

    companion object {
        private val TAG: String = AntCooperate::class.java.simpleName


        /**
         * 判断是否为团队模式
         *
         * @param homeObj 用户主页的JSON对象
         * @return 是否为团队
         */
        private fun isTeam(homeObj: JSONObject): Boolean {
            // nextAction=="Team" 为组队模式，"Cultivate" 为个人模式
            return "Team" == homeObj.optString("nextAction", "")
        }

        /**
         * 合种浇水
         */
        private fun cooperateWater(coopId: String, count: Int, name: String) {
            try {
                val jo = JSONObject(AntCooperateRpcCall.cooperateWater(UserMap.currentUid, coopId, count))
                if (ResChecker.checkRes(TAG, jo)) {
                    Log.forest("合种浇水🚿[" + name + "]" + jo.getString("barrageText"))
                    Status.cooperateWaterToday(UserMap.currentUid, coopId)
                } else {
                    Log.error(TAG, "浇水失败[" + name + "]: " + jo.getString("resultDesc"))
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "cooperateWater err:", t)
            }
        }

        /**
         * 计算合种需要浇水的克数
         */
        private fun getTotalWatering(coopId: String?): Int {
            try {
                val jo = JSONObject(AntCooperateRpcCall.queryCooperateRank("A", coopId))
                if (ResChecker.checkRes(TAG, jo)) {
                    val jaList = jo.getJSONArray("cooperateRankInfos")
                    for (i in 0..<jaList.length()) {
                        val joItem = jaList.getJSONObject(i)
                        val userId = joItem.getString("userId")
                        if (userId == UserMap.currentUid) {
                            // 未获取到累计浇水量 返回 -1 不执行浇水
                            val energySummation = joItem.optInt("energySummation", -1)
                            if (energySummation >= 0) {
                                Log.runtime(TAG, "当前用户[$userId]的累计浇水能量: $energySummation")
                            }
                            return energySummation
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "计算合种需要浇水的克数err", t)
            }
            Log.error(TAG, "合种获取累计浇水量失败")
            return -1 // 未获取到累计浇水量，停止浇水
        }

        /**
         * 召唤队友浇水（仅队长）
         */
        private fun cooperateSendCooperateBeckon(cooperationId: String, name: String) {
            try {
                if (TimeUtil.isNowBeforeTimeStr("1800")) {
                    return
                }
                var jo = JSONObject(AntCooperateRpcCall.queryCooperateRank("D", cooperationId))
                if (ResChecker.checkRes(TAG, jo)) {
                    val cooperateRankInfos = jo.getJSONArray("cooperateRankInfos")
                    for (i in 0..<cooperateRankInfos.length()) {
                        val rankInfo = cooperateRankInfos.getJSONObject(i)
                        if (rankInfo.getBoolean("canBeckon")) {
                            jo = JSONObject(AntCooperateRpcCall.sendCooperateBeckon(rankInfo.getString("userId"), cooperationId))
                            if (ResChecker.checkRes(TAG, jo)) {
                                Log.forest("合种🚿[" + name + "]#召唤队友[" + rankInfo.getString("displayName") + "]成功")
                            }
                            TimeUtil.sleepCompat(300)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "召唤队友和种错误：", t)
            }
        }
    }
}