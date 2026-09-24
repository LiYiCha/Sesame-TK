package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.data.Config
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.min

/**
 * 爱心鸡结号（庄园周年捐蛋活动）
 *
 * 总开关开启后自动报名活动、领取活动任务奖励、周年蛋糕食品、爱心值档位奖励；
 * 捐蛋模式单独控制是否耗蛋捐蛋：
 * - 关闭：不捐蛋，只做任务与领奖
 * - 仅任务：只花完成捐蛋任务（首捐/组队/个人累计）所需的最少蛋，不冲榜、不冲档位
 * - 激进：在周结算前冲周榜第一（按反超额外捐蛋数计算），耗蛋不封顶
 * - 稳定：按剩余周结算次数均摊累计奖励缺口，只做有确定收益的耗蛋，达标即停
 *
 * 活动结束后自动关闭总开关；执行时机跟随庄园任务的运行节奏。
 */
object AntFarmLoveChickenGathering {

    private const val TAG = "AntFarmLoveChickenGathering"
    private val FARM_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 活动快照 */
    private class ActivitySnapshot(response: JSONObject) {
        private val conf = response.getJSONObject("donationCompetitionActivityConf")
        val activityId: String = conf.getString("activityId")
        val projectId: String = conf.getString("projectId")
        val startTimeMs: Long = conf.getLong("startTime")
        val endTimeMs: Long = conf.getLong("endTime")
        private val rankHome = response.optJSONObject("donationRankHomeInfo")
        val rankRoundId: String = rankHome?.optString("rankRoundId").orEmpty()
        val participated: Boolean = response.optBoolean("isParticipateCompetition")

        /** 排行匹配中（未分组），此时无法按名次竞争 */
        val matching: Boolean = rankHome == null || rankHome.optString("status") == "MATCHING"
        val settleStartTime: LocalTime = LocalTime.parse(conf.getString("settleStartTime"))

        /** 本轮周结算时间：优先使用服务端倒计时，否则取本周日结算开始时间 */
        val settleAtMs: Long
        /** 直接贡献截止：活动结束时间与结束日 20:00 取较早者 */
        val directContributionEndMs: Long

        init {
            val now = ZonedDateTime.now(FARM_ZONE)
            val calendarSettle = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toLocalDate()
                .atTime(settleStartTime).atZone(FARM_ZONE).toInstant().toEpochMilli()
            val countdown = rankHome?.optLong("settleCountDown", -1L) ?: -1L
            settleAtMs = if (countdown > 0 && calendarSettle > System.currentTimeMillis())
                System.currentTimeMillis() + countdown else calendarSettle
            val end = Instant.ofEpochMilli(endTimeMs).atZone(FARM_ZONE)
            directContributionEndMs = minOf(endTimeMs, end.toLocalDate().atTime(20, 0)
                .atZone(FARM_ZONE).toInstant().toEpochMilli())
        }
    }

    /** 活动任务页：任务列表与全局参数（捐蛋上限、队伍进度等） */
    private class TaskPage(val tasks: List<JSONObject>, val paramMap: JSONObject)

    /** 档位奖励快照：当前爱心值与最高档位阈值 */
    private class RewardInfo(val contribution: Int, val maxThreshold: Int)

    /** 排行成员 */
    private class RankMember(val userId: String, val rankOrder: Int, val donationNum: Int, val rewardContributionNum: Int?)

    /** 排行快照 */
    private class RankSnapshot(val self: RankMember, val members: List<RankMember>) {
        val opponents: List<RankMember> get() = members.filter { it.userId != self.userId }
    }

    /** 捐蛋计划 */
    private class DonationPlan(val amount: Int, val reason: String)

    fun run(farm: AntFarm) {
        try {
            val queried = queryActivity() ?: return
            var activity = queried.first
            var snapshot = queried.second ?: return
            val now = System.currentTimeMillis()
            if (now >= snapshot.endTimeMs) {
                Log.farm(TAG, "爱心鸡结号🥚[活动已结束，模式开关关闭]")
                farm.loveChickenGathering?.setObjectValue(false)
                Config.save(UserMap.currentUid, false)
                return
            }
            if (now < snapshot.startTimeMs) {
                Log.farm(TAG, "爱心鸡结号🥚[活动未开始]")
                return
            }
            // 自动报名
            if (!snapshot.participated) {
                if (!participate(activity)) return
                val reQueried = queryActivity() ?: return
                activity = reQueried.first
                snapshot = reQueried.second ?: return
                if (!snapshot.participated) {
                    Log.farm(TAG, "爱心鸡结号🥚[报名后仍未生效，等待下次执行]")
                    return
                }
            }
            // 活动任务
            val page = handleTasks(snapshot)
            // 周年蛋糕
            handleAnnGift(activity, farm)
            // 档位奖励
            val rewards = handleRewards(snapshot)
            // 捐蛋
            handleDonation(farm, snapshot, page, rewards)
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "run err:", t)
        }
    }

    /** 查询活动入口，返回 响应 to 快照；非爱心鸡结号入口时快照为 null */
    private fun queryActivity(): Pair<JSONObject, ActivitySnapshot?>? {
        val response = try {
            JSONObject(AntFarmRpcCall.enterDonationCompetitionRank())
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "enterDonationCompetitionRank err:", t)
            return null
        }
        if (!ResChecker.checkRes(TAG, response)) {
            Log.farm(TAG, "爱心鸡结号🥚[查询活动失败] ${response.optString("memo", response.optString("resultDesc"))}")
            return null
        }
        val snapshot = parseActivity(response)
        if (snapshot == null) {
            Log.farm(TAG, "当前入口不是爱心鸡结号活动，跳过")
            return null
        }
        return response to snapshot
    }

    private fun parseActivity(response: JSONObject): ActivitySnapshot? {
        return try {
            if (response.optJSONObject("donationCompetitionActivityConf")?.has("projectId") == true)
                ActivitySnapshot(response) else null
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "parseActivity err:", t)
            null
        }
    }

    private fun participate(activity: JSONObject): Boolean {
        Log.farm(TAG, "爱心鸡结号🥚[自动报名]")
        val join = try {
            JSONObject(AntFarmRpcCall.participateCompetition())
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "participateCompetition err:", t)
            return false
        }
        if (!ResChecker.checkRes(TAG, join)) {
            Log.farm(TAG, "爱心鸡结号🥚[报名失败] ${join.optString("memo", join.optString("resultDesc"))}")
            return false
        }
        return true
    }

    /** 领取活动任务奖励，返回任务页供捐蛋策略使用 */
    private fun handleTasks(snapshot: ActivitySnapshot): TaskPage? {
        val response = try {
            JSONObject(AntFarmRpcCall.listCompetitionTask())
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "listCompetitionTask err:", t)
            return null
        }
        if (!ResChecker.checkRes(TAG, response)) {
            Log.farm(TAG, "爱心鸡结号🥚[查询任务失败] ${response.optString("memo", response.optString("resultDesc"))}")
            return null
        }
        val taskList = response.optJSONArray("taskList") ?: return null
        val paramMap = response.optJSONObject("paramMap") ?: return null
        val scene = response.optString("taskSceneCode").ifBlank { "ANTFARM_PK_COMPETITION" }
        val tasks = (0 until taskList.length()).mapNotNull { taskList.optJSONObject(it) }
        for (task in tasks) {
            val taskType = task.optString("taskType")
            if (taskType.isBlank()) continue
            if (task.optString("taskStatus") != "FINISHED") continue
            val awardCount = task.optInt("canReceiveAwardCount", 0)
            if (awardCount <= 0) continue
            val receive = try {
                JSONObject(AntFarmRpcCall.receiveTaskAwardAntFarm(scene, taskType, awardCount))
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "receiveTaskAwardAntFarm err:", t)
                continue
            }
            if (ResChecker.checkRes(TAG, receive)) {
                Log.farm(TAG, "爱心鸡结号🥚[领取任务奖励] $taskType x$awardCount")
            } else {
                Log.farm(TAG, "爱心鸡结号🥚[领取任务奖励失败] $taskType: ${receive.optString("memo", receive.optString("resultDesc"))}")
            }
        }
        return TaskPage(tasks, paramMap)
    }

    /** 领取周年蛋糕食品 */
    private fun handleAnnGift(activity: JSONObject, farm: AntFarm) {
        val info = activity.optJSONObject("ann9thCompetitionInfo") ?: return
        if (!info.optBoolean("ann9thActivitySwitch")) return
        if (info.optString("ann9thCakeStatus") != "PENDING") return
        val receive = try {
            JSONObject(AntFarmRpcCall.getAnnGift())
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "getAnnGift err:", t)
            return
        }
        if (ResChecker.checkRes(TAG, receive)) {
            Log.farm(TAG, "爱心鸡结号🥚[领取周年蛋糕食品]")
            farm.lcSyncFarmStatus()
        } else {
            Log.farm(TAG, "爱心鸡结号🥚[领取周年蛋糕失败] ${receive.optString("memo", receive.optString("resultDesc"))}")
        }
    }

    /** 领取爱心值档位奖励（status=unclaimed），返回奖励快照 */
    private fun handleRewards(snapshot: ActivitySnapshot): RewardInfo? {
        val response = try {
            JSONObject(AntFarmRpcCall.enterCompetitionAwardPage())
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "enterCompetitionAwardPage err:", t)
            return null
        }
        if (!ResChecker.checkRes(TAG, response)) {
            Log.farm(TAG, "爱心鸡结号🥚[查询档位奖励失败] ${response.optString("memo", response.optString("resultDesc"))}")
            return null
        }
        if (response.optJSONObject("donationCompetitionActivityConf")?.optString("activityId") != snapshot.activityId) {
            return null
        }
        val contribution = response.optJSONObject("userDonationLevelInfo")?.optInt("userContributionNum", 0) ?: 0
        val list = response.optJSONArray("levelAwardInfoList") ?: return RewardInfo(contribution, 0)
        var maxThreshold = 0
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val threshold = item.optInt("levelContributionTotalNum", 0)
            if (threshold > maxThreshold) maxThreshold = threshold
            if (item.optString("status") != "unclaimed") continue
            val rightsId = item.optString("rightsId")
            if (rightsId.isBlank()) continue
            val receive = try {
                JSONObject(AntFarmRpcCall.receiveDonationLevelReward(rightsId))
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "receiveDonationLevelReward err:", t)
                continue
            }
            if (ResChecker.checkRes(TAG, receive)) {
                Log.farm(TAG, "爱心鸡结号🥚[领取档位奖励] 爱心值$threshold")
            } else {
                Log.farm(TAG, "爱心鸡结号🥚[领取档位奖励失败] 爱心值$threshold: ${receive.optString("memo", receive.optString("resultDesc"))}")
            }
        }
        return RewardInfo(contribution, maxThreshold)
    }

    /** 按策略向活动项目捐蛋（捐蛋模式=关闭时跳过，仅做任务与领奖） */
    private fun handleDonation(farm: AntFarm, snapshot: ActivitySnapshot, page: TaskPage?, rewards: RewardInfo?) {
        if ((farm.loveChickenMode?.value ?: 0) == 0) {
            Log.farm(TAG, "爱心鸡结号🥚[捐蛋模式关闭]，本次不捐蛋")
            return
        }
        if (page == null || rewards == null) return
        if (System.currentTimeMillis() >= snapshot.endTimeMs) return
        // 排名信息（结算前且匹配完成）
        var rank: RankSnapshot? = null
        if (!snapshot.matching && System.currentTimeMillis() < snapshot.settleAtMs && snapshot.rankRoundId.isNotBlank()) {
            rank = queryRank(snapshot)
        }
        val plan = selectDonation(farm, snapshot, page, rank, rewards)
        if (plan.amount <= 0) {
            Log.farm(TAG, "爱心鸡结号🥚[${plan.reason}]，本次不捐蛋")
            return
        }
        Log.farm(TAG, "爱心鸡结号🥚[${plan.reason}]，计划捐蛋${plan.amount}")
        // 补蛋：收蛋 → 新蛋卡 → 特殊食品
        val eggs = replenishEggs(farm, plan.amount)
        if (eggs < plan.amount) {
            Log.farm(TAG, "爱心鸡结号🥚[蛋数不足] 当前${eggs}颗，需要${plan.amount}颗，等待后续产出")
            return
        }
        // 捐赠
        val response = try {
            JSONObject(AntFarmRpcCall.donationToLoveChickenProject(snapshot.projectId, plan.amount))
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "donationToLoveChickenProject err:", t)
            return
        }
        val donationDetails = response.optJSONObject("donation")
        val actualAmount = donationDetails?.optInt("donationAmount", 0) ?: 0
        if (ResChecker.checkRes(TAG, response) && donationDetails != null && actualAmount > 0 &&
            response.optString("donationUserRecordId").isNotBlank()
        ) {
            Log.farm(TAG, "爱心鸡结号❤️[捐蛋成功] 数量$actualAmount")
            farm.lcSyncFarmStatus()
        } else {
            Log.farm(TAG, "爱心鸡结号🥚[捐蛋失败] ${response.optString("memo", response.optString("resultDesc", response.toString()))}")
        }
    }

    /** 补蛋：同步状态 → 收蛋 → 新蛋卡 → 特殊食品，返回补蛋后可用蛋数 */
    private fun replenishEggs(farm: AntFarm, target: Int): Int {
        farm.lcSyncFarmStatus()
        var eggs = farm.lcCurrentEggs()
        if (eggs >= target) return eggs
        farm.lcHarvestEggs()
        eggs = farm.lcCurrentEggs()
        if (eggs >= target) return eggs
        if (farm.lcNewEggCardEnabled()) {
            if (farm.lcUseNewEggCard()) {
                farm.lcSyncFarmStatus()
                eggs = farm.lcCurrentEggs()
            }
        }
        if (eggs >= target) return eggs
        if (farm.lcSpecialFoodEnabled() && !farm.lcOwnerAnimalSleeping()) {
            eggs = farm.lcUseSpecialFood((target - eggs).coerceAtLeast(1))
        }
        return eggs
    }

    /** 捐蛋量决策：激进冲榜 / 稳定均摊 */
    private fun selectDonation(
        farm: AntFarm, snapshot: ActivitySnapshot, page: TaskPage, rank: RankSnapshot?, rewards: RewardInfo,
    ): DonationPlan {
        val now = System.currentTimeMillis()
        val mode = farm.loveChickenMode?.value ?: 0
        val stable = mode == 3
        val margin = (farm.loveChickenOvertakeAmount?.value ?: 1).coerceAtLeast(1)
        val selfTotal = rank?.self?.donationNum ?: 0
        val tasks = page.tasks
        val paramMap = page.paramMap

        fun needToPass(member: RankMember): Int =
            (member.donationNum.toLong() + margin - selfTotal).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

        // 日常爱心值任务需求
        val activeTasks = tasks.filter {
            it.optString("taskStatus") == "TODO" && it.optLong("taskEntityExpireTime", Long.MAX_VALUE) > now
        }
        val personal = if (now < snapshot.directContributionEndMs && activeTasks.any {
                it.optString("taskType") == "USER_ACCUMLATED_DONATION_TASK"
            }
        ) (paramMap.optInt("userActivityDonateMax") - paramMap.optInt("userActivityDonateTotal")).coerceAtLeast(0) else 0
        val firstTask = activeTasks.firstOrNull { it.optString("taskType") == "USER_FIRST_DONATION_TASK" }
        val teamThreshold = paramMap.optInt("teamTaskThreshold")
        val teamTask = if (!snapshot.matching && now < snapshot.settleAtMs) activeTasks.firstOrNull {
            it.optInt("taskThreshold", -1) == teamThreshold && teamThreshold >= 100
        } else null
        val teamNeed = if (teamTask != null) (teamThreshold - paramMap.optInt("teamDonateTotal")).coerceAtLeast(0) else 0
        val teamReward = teamTask?.optInt("canReceiveAwardCount", 0) ?: 0
        val firstReward = firstTask?.optInt("canReceiveAwardCount", 0) ?: 0

        // 捐蛋任务所需的最少蛋量（首捐 1 颗 / 组队差额 / 个人累计差额取最小）
        val taskNeed = listOf(personal, if (firstTask != null) 1 else 0, teamNeed).filter { it > 0 }.minOrNull() ?: 0

        // 仅任务：只花完成捐蛋任务所需的最少蛋，不冲榜、不冲档位
        if (mode == 1) {
            return DonationPlan(taskNeed, "仅完成捐蛋任务")
        }

        // 激进模式：先满足日常爱心值任务，再冲周榜第一
        if (!stable) {
            val rankNeed = if (rank != null) maxOf(if (selfTotal == 0) 1 else 0,
                rank.opponents.maxOfOrNull(::needToPass) ?: 0) else 0
            val need = if (taskNeed > 0) taskNeed else rankNeed
            val reason = if (taskNeed > 0) "日常爱心值任务" else if (rank != null) "激进周榜第一(余量$margin)" else "排名不可用，暂不捐蛋"
            return DonationPlan(need.coerceAtLeast(0), reason)
        }

        // 稳定模式：按剩余周结算次数均摊累计奖励缺口
        val gap = (rewards.maxThreshold - rewards.contribution).coerceAtLeast(0)
        if (gap == 0) return DonationPlan(0, "稳定目标已达成")
        var rounds = 0
        var settlement = ZonedDateTime.now(FARM_ZONE).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .toLocalDate().atTime(snapshot.settleStartTime).atZone(FARM_ZONE)
        while (settlement.toInstant().toEpochMilli() <= snapshot.endTimeMs) {
            if (settlement.toInstant().toEpochMilli() > now) rounds++
            settlement = settlement.plusWeeks(1)
        }
        val weeklyNeed = if (rounds > 0) ceil(gap.toDouble() / rounds).toInt() else gap

        fun rankReward(eggs: Int): Int? {
            if (rank == null || rounds == 0 || now >= snapshot.settleAtMs) return 0
            if (eggs == 0 && selfTotal > 0) return rank.self.rewardContributionNum
            if (selfTotal + eggs <= 0) return 0
            val position = rank.opponents.count { it.donationNum >= selfTotal + eggs } + 1
            return (rank.members + rank.self).firstOrNull { it.rankOrder == position && it.rewardContributionNum != null }?.rewardContributionNum
        }

        fun gain(eggs: Int): Int = min(eggs, personal) + (if (eggs > 0) firstReward else 0) +
            (if (teamNeed > 0 && eggs >= teamNeed) teamReward else 0) + (rankReward(eggs) ?: 0)

        val currentRankReward = if (rounds > 0 && now < snapshot.settleAtMs && selfTotal > 0) rank?.self?.rewardContributionNum else 0
        if (currentRankReward != null && currentRankReward >= weeklyNeed && firstTask == null) {
            return DonationPlan(0, "预计周奖励${currentRankReward}已覆盖本周目标${weeklyNeed}")
        }

        val candidates = sortedSetOf(0)
        if (firstTask != null) candidates += 1
        if (teamNeed > 0) candidates += teamNeed
        if (personal > 0) candidates += personal
        if (rank != null) {
            if (selfTotal == 0) candidates += 1
            rank.opponents.forEach { candidates += needToPass(it) }
        }
        for (base in candidates.toList()) {
            val required = (weeklyNeed - gain(base)).coerceAtLeast(0)
            if (base < personal) candidates += base + minOf(required, personal - base)
        }
        val sufficient = candidates.filter { gain(it) >= weeklyNeed }
        val chosen = if (sufficient.isNotEmpty()) sufficient.minWithOrNull(compareBy<Int> { it }.thenByDescending { gain(it) })!!
        else candidates.sortedWith(compareByDescending<Int> { gain(it) }.thenBy { it }).firstOrNull() ?: 0
        val amount = if (firstTask != null && chosen == 0) 1 else chosen
        return DonationPlan(amount, "稳定目标${weeklyNeed}，已到账${rewards.contribution}，剩余${rounds}次周结算")
    }

    /** 查询全部成员捐蛋排行（含分页） */
    private fun queryRank(snapshot: ActivitySnapshot): RankSnapshot? {
        return try {
            val response = JSONObject(AntFarmRpcCall.queryAllMemberRankInfo())
            if (!ResChecker.checkRes(TAG, response)) return null
            if (response.optJSONObject("donationCompetitionActivityConf")?.optString("activityId") != snapshot.activityId) return null
            var home = response.getJSONObject("donationRankHomeInfo")
            if (home.optString("rankRoundId") != snapshot.rankRoundId || home.optString("status") == "MATCHING") return null
            fun member(item: JSONObject) = RankMember(
                item.getString("userId"), item.getInt("rankOrder"), item.getInt("donationNum"),
                if (item.has("rewardContributionNum") && !item.isNull("rewardContributionNum")) item.getInt("rewardContributionNum") else null,
            )
            val self = member(home.getJSONObject("selfDonationRank"))
            val members = LinkedHashMap<String, RankMember>()
            val pages = HashSet<String>()
            var pageNo = 1
            while (true) {
                val list = home.getJSONArray("userDonationRankList")
                if (!pages.add(list.toString())) {
                    Log.farm(TAG, "爱心鸡结号🥚[榜单分页重复，停止查询]")
                    return null
                }
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i)?.let { member(it) } ?: continue
                    val previous = members[item.userId]
                    members[item.userId] = if (item.rewardContributionNum == null && previous != null && previous.rankOrder == item.rankOrder)
                        RankMember(item.userId, item.rankOrder, item.donationNum, previous.rewardContributionNum) else item
                }
                if (!home.optBoolean("hasMore")) break
                val page = JSONObject(AntFarmRpcCall.queryPageRankInfo(snapshot.rankRoundId, pageNo++))
                if (!ResChecker.checkRes(TAG, page)) return null
                home = page.getJSONObject("donationRankHomeInfo")
                if (home.optString("rankRoundId") != snapshot.rankRoundId) return null
            }
            RankSnapshot(self, members.values.toList())
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryRank err:", t)
            null
        }
    }
}
