package fansirsqi.xposed.sesame.hook

import android.content.Context
import fansirsqi.xposed.sesame.hook.context.AppContext
import fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager
import fansirsqi.xposed.sesame.hook.scheduler.TaskScheduler
import android.content.Intent
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.maps.UserMap

class ExtendHandle {

    companion object {
        private const val ACTION_RERUN = "rerun" // 重新运行
        private const val ACTION_CONTINUE = "continue" // 继续运行
        private const val ACTION_PAUSE = "pause" // 暂停运行
        private const val ACTION_STOP = "stop" // 停止运行

        /**
         * 处理重新运行或继续运行逻辑
         */
        @JvmStatic
        fun handleReRun(actionType: String) {
            try {
                when (actionType) {
                    ACTION_RERUN -> {
                        TaskScheduler.setPaused(false)
                        // 重新运行：强制启动任务
                        TaskScheduler.executeTask()
                        Log.runtime("[ReRunReceiver]任务已重新启动✅")
                        Toast.show("任务已重新执行", true)
                    }
                    ACTION_CONTINUE -> {
                        TaskScheduler.setPaused(false)
                        // 继续运行：检查任务是否正在运行
                        if (!TaskScheduler.isExecuting()) {
                            TaskScheduler.executeTask()
                            Log.runtime("[ContinueRunReceiver]任务已继续执行✅")
                            Toast.show("任务已继续执行", true)
                        } else {
                            Log.runtime("[ContinueRunReceiver]任务已在运行中")
                            Toast.show("任务已在运行中", true)
                        }
                    }
                    ACTION_PAUSE -> {
                        TaskScheduler.setPaused(true)
                        fansirsqi.xposed.sesame.task.ModelTask.stopAllTask()
                        Log.runtime("[PauseRunReceiver]任务已暂停⏸")
                        Toast.show("任务已暂停", true)
                    }
                    ACTION_STOP -> {
                        TaskScheduler.setPaused(false)
                        TaskScheduler.shutdownExecutors()
                        fansirsqi.xposed.sesame.task.ModelTask.stopAllTask()
                        Log.runtime("[StopRunReceiver]任务已停止运行🛑")
                        Toast.show("任务已停止并清除", true)
                    }
                }
            } catch (e: Exception) {
                Log.error("[ReRunReceiver]处理重新执行任务请求时出错❌: ${e.message}")
                Log.printStackTrace("ApplicationHook.ReRunReceiver", e)
            }
        }

        /**
         * 处理状态检查请求
         * @param context 上下文
         */
        @JvmStatic
        fun handleCheckStatus(context: Context) {
            try {
                val statusInfo = buildString {
                    append("======= 芝麻粒运行状态检查 =======\n")

                    // 检查支付宝运行状态
                    val isAlipayRunning = AppContext.getService() != null && context != null
                    append("应用运行状态: ${if (isAlipayRunning) "✅ 运行中" else "❌ 未运行"}\n")

                    // 检查模块状态
                    append("模块Hook状态: ${if (ApplicationHook.isHooked()) "✅ 已Hook" else "❌ 未Hook"}\n")
                    append("模块初始化状态: ${if (LifecycleManager.isInit()) "✅ 已初始化" else "❌ 未初始化"}\n")

                    // 检查主任务调度器状态
                    val isSchedulerExecuting = TaskScheduler.isExecuting()
                    append("主任务调度器: ${if (isSchedulerExecuting) "✅ 运行中" else "❌ 已停止"}\n")

                    // 检查具体任务运行状态
                    append("\n--- 任务详细状态 ---\n")
                    val modelArray = fansirsqi.xposed.sesame.model.Model.modelArray
                    var runningTaskCount = 0
                    val runningTasks = mutableListOf<String>()

                    modelArray?.forEach { model ->
                        if (model is fansirsqi.xposed.sesame.task.ModelTask) {
                            if (model.isRunning) {
                                runningTaskCount++
                                runningTasks.add(model.getName() ?: "未知任务")
                            }
                        }
                    }

                    append("正在运行的任务数: $runningTaskCount\n")
                    if (runningTasks.isNotEmpty()) {
                        append("运行中的任务:\n")
                        runningTasks.forEach { taskName ->
                            append("  - $taskName\n")
                        }
                    }

                    // 检查子任务状态
                    append("\n--- 子任务状态 ---\n")
                    val waitingChildTaskCount = fansirsqi.xposed.sesame.task.ModelTask.ChildModelTask.getWaitingCount()
                    append("等待中的子任务数: $waitingChildTaskCount\n")

                    if (waitingChildTaskCount > 0) {
                        val waitingTasks = fansirsqi.xposed.sesame.task.ModelTask.ChildModelTask.getWaitingTasks()
                        append("等待中的子任务列表:\n")
                        waitingTasks.forEach { childTask ->
                            val remainingTime = childTask.execTime - System.currentTimeMillis()
                            val remainingSeconds = (remainingTime / 1000).coerceAtLeast(0)
                            append("  - ${childTask.id} (剩余: ${remainingSeconds}秒)\n")
                        }
                    }

                    // 显示版本信息
                    append("\n--- 系统信息 ---\n")
                    append("模块版本: ${ApplicationHook.getModelVersion()}\n")

                    // 显示用户信息
                    val currentUid = UserMap.currentUid
                    append("当前用户ID: ${currentUid ?: "未登录"}\n")

                    // 显示其他状态
                    append("离线状态: ${if (LifecycleManager.isOffline()) "✅ 离线" else "❌ 在线"}\n")
                    append("重登录次数: ${ApplicationHook.getReLoginCount().get()}\n")

                    append("================================")
                }

                Log.runtime(statusInfo)
                Toast.show("状态检查完成，详情请查看日志", true)

            } catch (e: Exception) {
                Log.runtime("状态检查失败: ${e.message}")
                Log.printStackTrace("ExtendHandle", e)
                Toast.show("状态检查失败: ${e.message}", false)
            }
        }

        @JvmStatic
        fun handleFetchMemberGoodsList(context: Context, deliveryId: String = "94000SR2025120515775004", pageNum: Int = 1) {
            GlobalThreadPools.execute {
                try {
                    Log.runtime("开始获取会员商品列表, deliveryId: $deliveryId, page: $pageNum")
                    
                    // Expand All Goods ID to include both active campaign IDs
                    val deliveryIds = if (deliveryId == "94000SR2023102305988003") {
                        "\"94000SR2024110510425045\",\"94000SR2023102305988003\""
                    } else {
                        "\"$deliveryId\""
                    }
                    
                    val params = "[{\"blackIds\":[],\"deliveryIdList\":[$deliveryIds],\"filterCityCode\":false,\"filterExchangeTime\":true,\"filterPointNoEnough\":false,\"filterStockNoEnough\":false,\"filterTimesLimit\":true,\"filterTimesLimitForPromo\":true,\"pageNum\":$pageNum,\"pageSize\":18,\"point\":21264,\"previewCopyDbId\":\"\",\"queryType\":\"DELIVERY_ID_LIST\",\"shandieComponentId\":\"\",\"source\":\"手端\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"},\"topIds\":[],\"uniqueId\":\"\"}]"
                    val response = RequestManager.requestString(
                        "com.alipay.alipaymember.biz.rpc.config.h5.queryShandieEntityList",
                        params
                    )
                    
                    if (response.isNullOrEmpty()) {
                        Log.error("获取会员商品列表失败：返回为空, deliveryId: $deliveryId")
                        val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                            putExtra("deliveryId", deliveryId)
                        }
                        context.sendBroadcast(intent)
                        return@execute
                    }
                    
                    try {
                        val jo = org.json.JSONObject(response)
                        val benefits = jo.optJSONArray("benefits")
                        if (benefits == null || benefits.length() == 0) {
                            Log.runtime("获取的会员商品列表为空，不覆盖本地缓存, pageNum: $pageNum")
                            val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                                putExtra("deliveryId", deliveryId)
                                putExtra("reason", "no_more")
                            }
                            context.sendBroadcast(intent)
                            return@execute
                        }
                    } catch (ex: Exception) {
                        Log.error("校验商品列表空包异常: ${ex.message}")
                    }
                    
                    val goodsFile = Files.getMemberGoodsListFile(deliveryId)
                    if (pageNum == 1) {
                        Files.write2File(response, goodsFile)
                    } else {
                        try {
                            if (goodsFile.exists()) {
                                val existingContent = Files.readFromFile(goodsFile)
                                if (!existingContent.isNullOrEmpty()) {
                                    val existingJo = org.json.JSONObject(existingContent)
                                    val existingBenefits = existingJo.optJSONArray("benefits") ?: org.json.JSONArray()
                                    
                                    val newJo = org.json.JSONObject(response)
                                    val newBenefits = newJo.optJSONArray("benefits") ?: org.json.JSONArray()
                                    
                                    // Merge benefits
                                    for (i in 0 until newBenefits.length()) {
                                        existingBenefits.put(newBenefits.get(i))
                                    }
                                    existingJo.put("benefits", existingBenefits)
                                    Files.write2File(existingJo.toString(), goodsFile)
                                } else {
                                    Files.write2File(response, goodsFile)
                                }
                            } else {
                                Files.write2File(response, goodsFile)
                            }
                        } catch (ex: Exception) {
                            Log.error("合并分页数据异常: ${ex.message}")
                            Files.write2File(response, goodsFile)
                        }
                    }
                    Log.runtime("会员商品列表已保存到本地: ${goodsFile.absolutePath}")
                    
                    val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.success").apply {
                        putExtra("deliveryId", deliveryId)
                    }
                    context.sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.error("获取会员商品列表异常: ${e.message}")
                    Log.printStackTrace("ExtendHandle.handleFetchMemberGoodsList", e)
                    val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                        putExtra("deliveryId", deliveryId)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }

        @JvmStatic
        fun handleQueryBenefitDetail(context: Context, benefitId: String) {
            GlobalThreadPools.execute {
                try {
                    Log.runtime("开始查询商品详情规格, benefitId: $benefitId")
                    val params = "[{\"benefitId\":\"$benefitId\",\"cityCode\":\"450300\",\"miniAppId\":\"\",\"requestSourceInfo\":\"来源\",\"sourcePassMap\":{\"innerSource\":\"来源\",\"source\":\"\",\"unid\":\"\"}}]"
                    val response = RequestManager.requestString(
                        "com.alipay.alipaymember.biz.rpc.config.h5.querySingleBenefitDetail",
                        params
                    )
                    
                    if (response.isNullOrEmpty()) {
                        Log.error("查询商品详情规格失败：返回为空")
                        return@execute
                    }
                    
                    val jo = org.json.JSONObject(response)
                    val benefitDetail = jo.optJSONObject("benefitDetail")
                    val skuInfoList = benefitDetail?.optJSONArray("skuInfoList")
                    
                    val skuIdsList = ArrayList<String>()
                    var fetchedSkuId = "-1"
                    
                    if (skuInfoList != null && skuInfoList.length() > 0) {
                        for (i in 0 until skuInfoList.length()) {
                            val skuObj = skuInfoList.optJSONObject(i)
                            if (skuObj != null) {
                                val sId = skuObj.optString("skuId", "-1")
                                if (sId != "-1") {
                                    if (fetchedSkuId == "-1") {
                                        fetchedSkuId = sId
                                    }
                                    var sPrice = skuObj.optString("price", "")
                                    if (sPrice.isEmpty()) {
                                        sPrice = skuObj.optString("priceYuan", "")
                                    }
                                    if (sPrice.isEmpty()) {
                                        sPrice = benefitDetail.optString("priceYuan", "0.00")
                                    }
                                    
                                    var sPoints = skuObj.optInt("pointPrice", 0)
                                    if (sPoints == 0) {
                                        sPoints = skuObj.optInt("points", 0)
                                    }
                                    if (sPoints == 0) {
                                        val pricePresentation = benefitDetail.optJSONObject("pricePresentation")
                                        sPoints = pricePresentation?.optInt("point", 0) ?: 0
                                    }
                                    skuIdsList.add("$sId|$sPrice|$sPoints")
                                }
                            }
                        }
                    }
                    
                    if (fetchedSkuId != "-1") {
                        Log.runtime("成功查询到规格列表: $skuIdsList")
                        val intent = Intent("fansirsqi.xposed.sesame.queryBenefitDetail.success").apply {
                            putExtra("benefitId", benefitId)
                            putExtra("skuId", fetchedSkuId)
                            putStringArrayListExtra("skuIds", skuIdsList)
                        }
                        context.sendBroadcast(intent)
                    } else {
                        Log.error("该商品详情中未包含规格列表")
                    }
                } catch (e: Exception) {
                    Log.error("查询商品详情规格异常: ${e.message}")
                    Log.printStackTrace("ExtendHandle.handleQueryBenefitDetail", e)
                }
            }
        }
    }
}
