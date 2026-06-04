package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class PlayConsultFacade {
    private val TAG = "会员|转盘"
    private val PLAYID = "PLAY202509080150799963" //活动ID
    private var strategyId: String? = "202602270515234629"  //抽奖ID？
    private val method = "com.alipay.amic.biz.rpc.activity.h5.PlayConsultFacade.consult" //方法名
    // 黑名单
    private var adIdBlackList: MutableList<String> = mutableListOf(
        "20100019", "274076402", "275975947", "259674711", "263875506",
        "20100019","267279189","271293135","27635615","32002001"
    )
    @Volatile
    private var CERTNUM: Int? = 0 // 抽奖次数，使用volatile保证可见性
    private val lotteryMutex = Mutex() // 抽奖互斥锁，确保线程安全
    private val taskMutex = Mutex() // 任务处理互斥锁，防止并发执行任务

    @Volatile
    private var hasError1009 = false

    /**
     * 使用协程进行处理，然后由java直接调用
     */
    private val handlerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun handleAsync(count: Int) {
        handlerScope.launch {
            taskMutex.withLock {
                try {
                    handle(count)
                } catch (e: Exception) {
                    Log.error(TAG, "handleAsync error: ${e}")
                }
            }
        }
    }

    /**
     * 1009限流与风控检测
     */
    private fun check1009(response: JSONObject): Boolean {
        val error = response.optInt("error", 0)
        val errorMsg = response.optString("errorMessage", "")
        if (error == 1009 || errorMsg.contains("人气太旺")) {
            hasError1009 = true
            Log.error(TAG, "触发1009限流/风控，暂停转盘任务")
            Status.setTemporaryStatusWithExpiry("MemberLuckyWheel_Cooldown", 1 * 60 * 60 * 1000) // 1小时冷却
            return true
        }
        return false
    }

    /**
     * 协程处理
     */
    suspend fun handle(count: Int) {
        hasError1009 = false
        if (!handleInfo(force = true)){
            return
        }
        if (hasError1009) return

        //处理任务
        processMultipleRounds(count)
        if (hasError1009) return

        delay(2000 + (0..1000).random().toLong() )
        //更新抽奖次数
        if (handleInfo(force = false)) {
            if (hasError1009) return
            handleConsult()
        }

        // 成功执行完毕且没有发生1009风控，则设置30分钟冷却
        if (!hasError1009) {
            Status.setTemporaryStatusWithExpiry("MemberLuckyWheel_Cooldown", 30 * 60 * 1000)
        }
    }

    /**
     * 处理多轮任务
     */
    private suspend fun processMultipleRounds(maxRounds: Int) {
        try {
            if (maxRounds <= 0) {
                Log.record(TAG, "任务轮数为0，跳过任务处理")
                return
            }

            Log.record(TAG, "开始处理多轮任务，最大轮数: $maxRounds")
            var completedRounds = 0
            var hasMoreTasks = true

            for (round in 1..maxRounds) {
                Log.record(TAG, "开始第${round}轮任务处理")
                
                // 再次检查是否有任务可执行，避免无效轮次
                if (!hasMoreTasks) {
                    Log.record(TAG, "没有更多任务需要处理，提前结束")
                    break
                }

                // 执行任务
                val taskResult = todoTask()
                if (hasError1009) {
                    break
                }

                if (taskResult) {
                    completedRounds++
                    Log.record(TAG, "第${round}轮任务处理完成")
                    
                    // 如果不是最后一轮，等待后继续
                    if (round < maxRounds) {
                        Log.record(TAG, "第${round}轮完成，准备下一轮")
                        // 轮次间延迟，避免请求过于频繁
                        delay(3000 + (0..2000).random().toLong())
                    }
                } else {
                    Log.record(TAG, "第${round}轮任务处理失败或无任务，停止后续轮次")
                    hasMoreTasks = false
                    break
                }
            }

            Log.record(TAG, "多轮任务处理完成，实际完成轮数: $completedRounds/$maxRounds")
        } catch (e: Exception) {
            Log.error(TAG, "processMultipleRounds error: ${e}")
        }
    }

    /**
     * 获取转盘信息
     */
    private suspend fun handleInfo(force: Boolean = false): Boolean {
        try {
            val consultCountInfo = queryConsultCountInfo()
            if (check1009(consultCountInfo)) {
                return false
            }
            if(consultCountInfo.optBoolean("success")){
                val resultData = consultCountInfo.optJSONObject("resultData")
                val lotteryMachineInfo = resultData?.optJSONObject("lotteryMachineInfo")
                val activityUpdateText = lotteryMachineInfo?.optString("activityUpdateText")
                strategyId = lotteryMachineInfo?.optString("strategyId",strategyId)
                
                // 线程安全地更新抽奖次数
                val newCertNum = lotteryMachineInfo?.optInt("certNum", 0) ?: 0
                CERTNUM = newCertNum
                
                if (force) {
                    Log.record(TAG, "${activityUpdateText}|抽奖次数:${CERTNUM}")
                }
                return true
            }else{
                Log.error(TAG, "获取转盘信息失败:${consultCountInfo}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "handleInfo error:${e}")
        }
        return false
    }

    private suspend fun todoTask(): Boolean {
        try {
            val queryConsult = queryConsult()
            if (check1009(queryConsult)) {
                return false
            }
            if (queryConsult.optBoolean("success")){
                val resultData = queryConsult.optJSONObject("resultData")
                if (resultData != null){
                    val newBlackList = mutableListOf<String>()
                    val adTaskList = resultData.optJSONArray("adTaskList")

                    if (adTaskList != null && adTaskList.length() > 0) {
                        var completedTasks = 0
                        val totalTasks = adTaskList.length()
                        Log.record(TAG, "发现 $totalTasks 个任务")

                        for (i in 0 until adTaskList.length()) {
                            val adTask = adTaskList.optJSONObject(i)
                            val isAdTask = adTask.optBoolean("adTask")
                            val currentCount = adTask.optInt("currentCount")
                            val targetCount = adTask.optInt("targetCount")
                            val status = adTask.optString("status")

                            val extInfo = adTask.optJSONObject("extInfo")
                            if (extInfo != null){
                                val adBizId = extInfo.optString("ad_biz_id")
                                val adID = extInfo.optString("AD_ID")
                                newBlackList.add(adID)

                                // 优化：安全判断任务是否已完成（防止重复做已完成的广告或普通任务）
                                if ("COMPLETED" == status || "FINISHED" == status || 
                                    (!isAdTask && currentCount >= targetCount) || 
                                    (targetCount > 0 && currentCount >= targetCount)) {
                                    Log.record(TAG, "任务已完成/跳过: ${adTask.optJSONObject("simpleTaskConfig")?.optString("title","")}")
                                    continue
                                }

                                val simpleTaskConfig = adTask.optJSONObject("simpleTaskConfig")
                                val title = simpleTaskConfig?.optString("title","")

                                Log.record(TAG, "开始执行任务[${title}] (${i+1}/$totalTasks)")

                                // 任务间隔时间随机化
                                val baseDelay = 10000L + (0..5000).random()
                                delay(baseDelay)

                                val finishResponse = finishTask(adBizId)
                                if (check1009(finishResponse)) {
                                    return false
                                }
                                delay(1000 + (0..1000).random().toLong() ) // 随机延迟查询结果

                                val taskResult = queryTaskResult(adBizId)
                                if (check1009(taskResult)) {
                                    return false
                                }
                                if (taskResult.optBoolean("success")){
                                    Log.record(TAG, "完成[${title}]")
                                    completedTasks++
                                } else {
                                    Log.error(TAG, "完成[${title}]失败:${taskResult}")
                                    continue
                                }

                                // 风控：每完成一定数量任务后增加额外延迟
                                if (completedTasks % 3 == 0) {
                                    Log.record(TAG, "已完成 $completedTasks 个任务，增加风控延迟")
                                    delay(2000 + (0..2000).random().toLong() )
                                }
                            }
                        }

                        // 更新黑名单
                        adIdBlackList.clear()
                        adIdBlackList.addAll(newBlackList)

                        Log.record(TAG, "本轮任务处理完成，完成任务数: $completedTasks/$totalTasks")
                        
                        // 如果有完成的任务，返回true表示可能还有下一轮
                        return completedTasks > 0
                    }else{
                        Log.record(TAG, "当前没有可执行的任务")
                        return false
                    }
                }
            } else {
                Log.error(TAG, "查询任务列表失败:${queryConsult}")
                return false
            }
        } catch (e: Exception) {
            Log.error(TAG, "todoTask error:${e}")
            return false
        }
        return true
    }

    /**
     * 抽奖 - 线程安全版本
     */
    private suspend fun handleConsult() {
        // 使用互斥锁确保抽奖操作的原子性
        lotteryMutex.withLock {
            try {
                var lotteryCount = CERTNUM ?: 0
                
                if (lotteryCount <= 0) {
                    Log.record(TAG, "抽奖次数不足，跳过抽奖")
                    return
                }

                Log.record(TAG, "开始抽奖，当前次数: $lotteryCount")
                
                var i = 1
                while (i <= lotteryCount && CERTNUM ?: 0 > 0) {
                    // 每次抽奖前再次检查次数
                    val remainingCertNum = CERTNUM ?: 0
                    if (remainingCertNum <= 0) {
                        Log.record(TAG, "抽奖次数已用完，停止抽奖")
                        break
                    }

                    val result = consult()
                    if (check1009(result)) {
                        break
                    }
                    if (result.optBoolean("success")) {
                        val resultData = result.optJSONObject("resultData")
                        val wangZhuanLotteryResultInfo =
                            resultData?.optJSONObject("wangZhuanLotteryResultInfo")
                        val prizeTextForLotteryResult =
                            wangZhuanLotteryResultInfo?.optString("prizeTextForLotteryResult")
                        Log.record(TAG, "第${i}次抽奖成功[${prizeTextForLotteryResult}]")
                    } else {
                        Log.error(TAG, "第${i}次抽奖失败:${result}")
                    }
                    
                    // 抽奖后递减次数
                    CERTNUM = CERTNUM?.minus(1)
                    i++
                    
                    // 抽奖间隔延迟
                    delay(6000 + (0..1000).random().toLong())
                }
                
                Log.record(TAG, "抽奖完成，剩余次数: ${CERTNUM ?: 0}")
            } catch (e: Exception) {
                Log.error(TAG, "handleConsult error: ${e}")
            }
        }
    }

    // 查询任务列表
    private suspend fun queryConsult(blackList: List<String> = adIdBlackList): JSONObject {
        val actualBlackList = blackList.ifEmpty {
            // 从adIdBlackList中随机获取4个参数
            if (adIdBlackList.size >= 4) {
                adIdBlackList.shuffled().take(4)
            } else {
                adIdBlackList
            }
        }

        val paramsObj = if (actualBlackList.isEmpty()) {
            "{}"
        } else {
            val blackListJson = actualBlackList.joinToString(",") { "\"$it\"" }
            "{\"adIdBlackList\":[${blackListJson}]}"
        }

        val params = "[{\"operation\":\"task_consult\",\"params\":${paramsObj},\"playId\":\"$PLAYID\",\"source\":\"\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //查询任务结果
    private suspend fun queryTaskResult(taskId: String): JSONObject {
        val params = "[{\"operation\":\"task_consult\",\"params\":{\"taskId\":\"$taskId\"},\"playId\":\"$PLAYID\",\"source\":\"\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //查询转盘信息
    private suspend fun queryConsultCountInfo(): JSONObject {
        val params = "[{\"operation\":\"consult\",\"playId\":\"$PLAYID\",\"source\":\"\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //完成任务
    private suspend fun finishTask(taskId: String): JSONObject {
        val method = "com.alipay.adtask.biz.mobilegw.service.task.finish"
        val params = "[{\"bizId\":\"${taskId}\",\"extendInfo\":{}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //抽奖
    private suspend fun consult(): JSONObject {
        val method = "com.alipay.amic.biz.rpc.activity.h5.PlayTriggerFacade.trigger"
        val params = "[{\"operation\":\"trigger\",\"params\":{\"strategyId\":\"$strategyId\"},\"playId\":\"$PLAYID\",\"source\":\"\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
}