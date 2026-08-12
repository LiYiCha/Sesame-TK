
package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TaskBlacklist
import fansirsqi.xposed.sesame.task.TaskStatus
import fansirsqi.xposed.sesame.util.GlobalThreadPools.sleepCompat
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 森林寻宝任务处理类 (每天自动执行, 完成后标记)
 */
class ForestChouChouLe {

    companion object {
        private const val TAG = "ForestChouChouLe"
        private const val SOURCE = "IPtask"

        // 场景代码常量
        private const val SCENE_NORMAL = "ANTFOREST_NORMAL_DRAW"
        private const val SCENE_ACTIVITY = "ANTFOREST_ACTIVITY_DRAW"

        // 屏蔽的任务类型关键词
        private val BLOCKED_TYPES = setOf(
            "FOREST_NORMAL_DRAW_SHARE",
            "FOREST_ACTIVITY_DRAW_SHARE"
        )

        // 屏蔽的任务名称关键词
        private val BLOCKED_NAMES = setOf("开宝箱")

        // 游戏内达成类任务关键字（必须在游戏内杀怪/通关/付费，无法自动化）
        private val IN_GAME_ACHIEVEMENT_KEYWORDS = setOf(
            "击杀", "通关", "关卡", "合成", "怪物", "充值", "消费", "等级", "过关", "胜"
        )

        // 无需重试的错误码（已完成/次数上限）
        private const val TASK_AWARD_ALREADY_FINISHED = "400000030"
        private const val TASK_ALREADY_FINISHED = "2600000016"
        private const val TASK_RIGHTS_LIMIT = "400000012"

        /**
         * 抽奖场景数据类
         */
        private data class Scene(
            val id: String,
            val code: String,
            val name: String,
            val flag: String
        ) {
            val taskCode get() = "${code}_TASK"
        }

        // 扩展函数：简化 JSON 解析和检查
        private fun String.toJson(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()
        private fun JSONObject.check(): Boolean = ResChecker.checkRes(TAG, this)

        // 动态获取抽奖场景配置
        private fun getScenes(): List<Scene> {
            val defaultScenes = listOf(
                Scene("2026051801", SCENE_NORMAL, "森林寻宝", "forest::chouChouLe::normal::completed"),
                Scene("20260607", SCENE_ACTIVITY, "森林寻宝IP", "forest::chouChouLe::activity::completed")
            )

            return runCatching {
                val scenes = mutableListOf<Scene>()
                // 使用普通场景查询
                val response = AntForestRpcCall.enterDrawActivityopengreen("", SCENE_NORMAL, SOURCE).toJson() ?: return@runCatching defaultScenes

                if (response.optBoolean("success", false)) {
                    val drawSceneGroups = response.optJSONArray("drawSceneGroups") ?: return@runCatching defaultScenes

                    for (i in 0 until drawSceneGroups.length()) {
                        val sceneGroup = drawSceneGroups.optJSONObject(i) ?: continue
                        val drawActivity = sceneGroup.optJSONObject("drawActivity") ?: continue

                        val activityId = drawActivity.optString("activityId")
                        val sceneCode = drawActivity.optString("sceneCode")
                        val name = sceneGroup.optString("name", "未知活动")

                        val flag = when (sceneCode) {
                            SCENE_NORMAL -> "forest::chouChouLe::normal::completed"
                            SCENE_ACTIVITY -> "forest::chouChouLe::activity::completed"
                            else -> "forest::chouChouLe::${sceneCode.lowercase(Locale.getDefault())}::completed"
                        }
                        scenes.add(Scene(activityId, sceneCode, name, flag))
                    }
                }
                if (scenes.isEmpty()) defaultScenes else scenes
            }.getOrElse {
                Log.printStackTrace(TAG, "获取抽奖场景配置失败, 使用默认配置", it)
                defaultScenes
            }
        }
    }

    private val taskTryCount = ConcurrentHashMap<String, AtomicInteger>()

    fun chouChouLe() {
        runCatching {
            val scenes = getScenes()
            if (scenes.all { Status.hasFlagToday(it.flag) }) {
//                Log.runtime("⏭️ 今天所有森林寻宝任务已完成, 跳过执行")
                return
            }

//            Log.runtime("开始处理森林寻宝, 共 ${scenes.size} 个场景")
            scenes.forEach {
                processScene(it)
                sleepCompat(RandomUtil.nextInt(1000, 2001).toLong())
            }
        }.onFailure { Log.printStackTrace(TAG, "执行异常", it) }
    }

    private fun processScene(s: Scene) = runCatching {
        if (Status.hasFlagToday(s.flag)) {
//            Log.runtime("⏭️ ${s.name} 今天已完成, 跳过")
            return@runCatching
        }

//        Log.runtime("👉 开始处理: ${s.name}")

        // 1. 检查活动有效期
        val enterResp = AntForestRpcCall.enterDrawActivityopengreen(s.id, s.code, SOURCE).toJson()
        if (enterResp == null || !enterResp.check()) return@runCatching

        val drawActivity = enterResp.optJSONObject("drawActivity")
        if (drawActivity != null) {
            val now = System.currentTimeMillis()
            val startTime = drawActivity.optLong("startTime")
            val endTime = drawActivity.optLong("endTime")
            if (now !in startTime..endTime) {
//                Log.runtime("⛔ ${s.name} 活动不在有效期内, 跳过")
                return@runCatching
            }
        }

        // 2. 循环处理任务 (执行 -> 领取)
        processTasksLoop(s)

        // 3. 执行抽奖
        processLottery(s)

        // 4. 最终检查完成状态
        checkCompletion(s)

    }.onFailure { Log.printStackTrace(TAG, "${s.name} 处理异常", it) }

    /**
     * 循环处理任务列表
     */
    private fun processTasksLoop(s: Scene) {
        repeat(3) { loop ->
//            Log.runtime("${s.name} 第 ${loop + 1} 轮任务检查")
            val tasksResp = AntForestRpcCall.listTaskopengreen(s.taskCode, SOURCE).toJson() ?: return@repeat
            if (!tasksResp.check()) return@repeat

            val taskList = tasksResp.optJSONArray("taskInfoList") ?: return@repeat
            var hasChange = false

            for (i in 0 until taskList.length()) {
                val task = taskList.optJSONObject(i) ?: continue
                if (processSingleTask(s, task)) {
                    hasChange = true
                }
            }

            if (!hasChange) {
//                Log.runtime("${s.name} 本轮无任务状态变更, 结束任务循环")
                return
            }
            if (loop < 2) sleepCompat(RandomUtil.nextInt(2000, 3001).toLong())
        }
    }

    /**
     * 执行抽奖逻辑
     */
    private fun processLottery(s: Scene) {
        val enterResp = AntForestRpcCall.enterDrawActivityopengreen(s.id, s.code, SOURCE).toJson() ?: return
        if (!enterResp.check()) return

        val drawAsset = enterResp.optJSONObject("drawAsset") ?: return
        var balance = drawAsset.optInt("blance", 0)
        val total = drawAsset.optInt("totalTimes", 0)

        Log.runtime("${s.name} 剩余抽奖次数: $balance / $total")

        var retry = 0
        // 最多抽50次，防止死循环
        while (balance > 0 && retry < 50) {
            retry++
            Log.runtime("${s.name} 第 $retry 次抽奖")

            val drawResp = AntForestRpcCall.drawopengreen(s.id, s.code, SOURCE, UserMap.currentUid).toJson()
            if (drawResp == null || !drawResp.check()) {
                break
            }

            balance = drawResp.optJSONObject("drawAsset")?.optInt("blance", 0) ?: 0
            val prize = drawResp.optJSONObject("prizeVO")
            if (prize != null) {
                val name = prize.optString("prizeName", "未知奖品")
                val num = prize.optInt("prizeNum", 1)
                Log.forest("${s.name} 🎁 [获得: $name * $num] 剩余次数: $balance")
            }

            if (balance > 0) sleepCompat(RandomUtil.nextInt(2000, 3001).toLong())
        }
    }

    /**
     * 检查是否所有任务都已完成，并设置 Flag
     */
    private fun checkCompletion(s: Scene) {
        val resp = AntForestRpcCall.listTaskopengreen(s.taskCode, SOURCE).toJson() ?: return
        if (!resp.check()) return

        val taskList = resp.optJSONArray("taskInfoList") ?: return
        var total = 0
        var completed = 0
        var allDone = true

        for (i in 0 until taskList.length()) {
            val task = taskList.optJSONObject(i) ?: continue
            val baseInfo = task.optJSONObject("taskBaseInfo") ?: continue

            val taskType = baseInfo.optString("taskType")
            val taskStatus = baseInfo.optString("taskStatus")
            val bizInfoStr = baseInfo.optString("bizInfo")
            val taskName = if (bizInfoStr.isNotEmpty()) {
                JSONObject(bizInfoStr).optString("title", taskType)
            } else taskType

            if (isBlockedTask(taskType, taskName)) continue

            total++
            if (taskStatus == TaskStatus.RECEIVED.name) {
                completed++
            } else {
                allDone = false
//                Log.runtime("${s.name} 未完成: $taskName [$taskStatus]")
            }
        }

        Log.runtime("${s.name} 进度: $completed / $total")
        if (allDone) {
            Status.setFlagToday(s.flag)
            val msg = if (total > 0) "全部完成" else "无有效任务"
            Log.runtime("✅ ${s.name} $msg ($completed/$total)")
        } else {
            Log.runtime("⚠️ ${s.name} 未全部完成")
        }
    }

    private fun isBlockedTask(taskType: String, taskName: String, desc: String = "", taskProdPlayType: String = "", prodPlayParam: String = ""): Boolean {
        if (BLOCKED_TYPES.any { taskType.contains(it) } ||
            BLOCKED_NAMES.any { taskName.contains(it) } ||
            TaskBlacklist.isTaskInBlacklist(taskType) ||
            TaskBlacklist.isTaskInBlacklist(taskName)) {
            return true
        }

        // 拦截游戏内达成类任务 (taskProdPlayType == "OTHER" 且属于游戏 IAP 或包含杀怪/通关关键字)
        if (taskProdPlayType == "OTHER" && (prodPlayParam.contains("\"IAP\"") || IN_GAME_ACHIEVEMENT_KEYWORDS.any { desc.contains(it) || taskName.contains(it) })) {
            return true
        }
        return false
    }

    /**
     * 处理单个任务分发
     * @return 任务状态是否有变更
     */
    private fun processSingleTask(s: Scene, task: JSONObject): Boolean {
        val baseInfo = task.optJSONObject("taskBaseInfo") ?: return false
        val bizInfoStr = baseInfo.optString("bizInfo")
        val bizInfo = if (bizInfoStr.isNotEmpty()) JSONObject(bizInfoStr) else JSONObject()

        val taskName = bizInfo.optString("title", "未知任务")
        val taskDesc = bizInfo.optString("desc", "")
        val taskCode = baseInfo.optString("sceneCode")
        val taskStatus = baseInfo.optString("taskStatus")
        val taskType = baseInfo.optString("taskType")
        val taskProdPlayType = baseInfo.optString("taskProdPlayType", "")
        val prodPlayParamStr = baseInfo.optString("prodPlayParam", "")

        var timeCount = 0
        if (prodPlayParamStr.isNotEmpty()) {
            val paramJson = prodPlayParamStr.toJson()
            if (paramJson != null) {
                timeCount = paramJson.optInt("timeCount", 0)
            }
        }

        if (isBlockedTask(taskType, taskName, taskDesc, taskProdPlayType, prodPlayParamStr)) return false

//        Log.runtime("${s.name} 任务: $taskName [$taskStatus]")

        return when (taskStatus) {
            TaskStatus.TODO.name -> handleTodoTask(s, taskName, taskCode, taskType, taskProdPlayType, timeCount)
            TaskStatus.FINISHED.name -> handleFinishedTask(s, taskName, taskCode, taskType)
            else -> false
        }
    }

    private fun handleTodoTask(s: Scene, name: String, code: String, type: String, playType: String = "", timeCount: Int = 0): Boolean {
        return if (type == "NORMAL_DRAW_EXCHANGE_VITALITY") {
            // 活力值兑换
            Log.runtime("${s.name} 兑换活力值: $name")
            val res = AntForestRpcCall.exchangeTimesFromTaskopengreen(s.id, s.code, SOURCE, code, type).toJson()
            if (res != null && res.check()) {
                Log.forest("${s.name} 🧾 $name 兑换成功")
                true
            } else false
        } else if (playType == "VISIT_FLOAT_BALL" || timeCount > 0 || type.contains("LLRW")) {
            // 游戏倒计时浏览任务 (如 玩一玩狂暴西游 浏览30s)
            val waitSec = if (timeCount > 0) timeCount else 30
            sleepCompat(waitSec * 1000L)

            val result = AntForestRpcCall.finishTaskopengreen(type, code)
            val resJson = result.toJson()
            if (resJson != null && resJson.check()) {
                Log.forest("${s.name} 🧾 浏览[$name]完成")
                true
            } else {
                val errorCode = resJson?.optString("code", "") ?: ""
                if (errorCode in listOf(TASK_AWARD_ALREADY_FINISHED, TASK_ALREADY_FINISHED, TASK_RIGHTS_LIMIT)) {
                    return false
                }
                val count = taskTryCount.computeIfAbsent(type) { AtomicInteger(0) }.incrementAndGet()
                Log.error(TAG, "${s.name} 任务失败($count): $name")
                if (resJson != null) {
                    val errorMsg = resJson.optString("desc", "")
                    if (errorCode.isNotEmpty() || errorMsg.isNotEmpty()) {
                        TaskBlacklist.autoAddToBlacklist(type, name, errorCode, errorMsg)
                    }
                }
                false
            }
        } else if (type.startsWith("FOREST_NORMAL_DRAW") || type.startsWith("FOREST_ACTIVITY_DRAW") || type.endsWith("_ACTIVITY") || type.endsWith("_NORMAL")) {
            // 普通任务
           // Log.runtime("${s.name} 执行任务(模拟耗时): $name")
            sleepCompat(RandomUtil.nextInt(3000, 4001).toLong())

            val result = if (type.contains("XLIGHT")) {
                AntForestRpcCall.finishTask4Chouchoule(type, code)
            } else {
                AntForestRpcCall.finishTaskopengreen(type, code)
            }

            val resJson = result.toJson()
            if (resJson != null && resJson.check()) {
                Log.forest("${s.name} 🧾 $name")
                true
            } else {
                // 检查是否是可忽略的错误码（已完成/次数上限）
                val errorCode = resJson?.optString("code", "") ?: ""
                if (errorCode in listOf(TASK_AWARD_ALREADY_FINISHED, TASK_ALREADY_FINISHED, TASK_RIGHTS_LIMIT)) {
                    return false // 静默跳过，不记错不黑名单
                }
                val count = taskTryCount.computeIfAbsent(type) { AtomicInteger(0) }.incrementAndGet()
                Log.error(TAG, "${s.name} 任务失败($count): $name")
                if (resJson != null) {
                    val errorMsg = resJson.optString("desc", "")
                    if (errorCode.isNotEmpty() || errorMsg.isNotEmpty()) {
                        TaskBlacklist.autoAddToBlacklist(type, name, errorCode, errorMsg)
                    }
                }
                false
            }
        } else {
            false
        }
    }

    private fun handleFinishedTask(s: Scene, name: String, code: String, type: String): Boolean {
//        Log.runtime("${s.name} 领取奖励: $name")
        sleepCompat(RandomUtil.nextInt(3000, 4001).toLong())
        val res = AntForestRpcCall.receiveTaskAwardopengreen(SOURCE, code, type).toJson()
        return if (res != null && res.check()) {
            Log.forest("${s.name} 🧾 $name 奖励领取成功")
            true
        } else {
            val errorCode = res?.optString("code", "") ?: ""
            if (errorCode in listOf(TASK_AWARD_ALREADY_FINISHED, TASK_ALREADY_FINISHED)) {
                return true // 已领取过，视为处理成功
            }
            Log.error(TAG, "${s.name} 奖励领取失败: $name")
            false
        }
    }
}
