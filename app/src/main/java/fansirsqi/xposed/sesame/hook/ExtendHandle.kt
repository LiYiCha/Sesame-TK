package fansirsqi.xposed.sesame.hook

import android.content.Context
import android.content.Intent
import fansirsqi.xposed.sesame.hook.keepalive.SmartSchedulerManager
import fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager
import fansirsqi.xposed.sesame.hook.scheduler.AlarmScheduler
import fansirsqi.xposed.sesame.hook.scheduler.TaskScheduler
import fansirsqi.xposed.sesame.hook.theme.ThemeManager
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.antForest.EnergyWaitingManager
import fansirsqi.xposed.sesame.task.exchange.ThreadPoolManager
import fansirsqi.xposed.sesame.task.otherTask2.PrivilegeTask
import fansirsqi.xposed.sesame.task.otherTask2.SeckillScheduler
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Notify
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ExtendHandle {

    companion object {
        private const val ACTION_RERUN = "rerun" // 重新运行
        private const val ACTION_CONTINUE = "continue" // 继续运行
        private const val ACTION_PAUSE = "pause" // 暂停运行
        private const val ACTION_STOP = "stop" // 停止运行

        // 会员商品列表抓取常量（对齐 temp/会员商品.log 真实请求）
        private const val ALL_GOODS_DELIVERY_ID = "94000SR2023102305988003" // "全部商品" Tab
        private val ALL_GOODS_DELIVERY_IDS = listOf(
            "94000SR2024110510425045",
            "94000SR2025091714812006",
            "94000SR2023102305988003"
        )
        // "全部商品"按积分区间分区（queryDeliveryZoneDetail 的 lowerPoint/upperPoint）
        private val ZONE_POINT_RANGES = listOf(0 to 501, 501 to 3000, 3001 to 10000, 10001 to 99999999)
        private const val SEARCH_CITY_CODE = "450300"
        private const val SEARCH_CLIENT_VERSION = "12.12.26.8100"

        // uniqueId 会话缓存：同一会话（deliveryId/zone 维度）内固定，翻页不重新生成（对齐日志真实请求）
        private val sessionUniqueIds = ConcurrentHashMap<String, String>()

        /**
         * 处理统一的主题操作广播
         *
         * 用 extra "operation" 区分具体操作（EXPORT/DELETE/UPDATE），
         * 根据操作类型调用 ThemeManager 对应的直接执行方法。
         */
        @JvmStatic
        fun handleThemeOperation(intent: Intent) {
            val operation = intent.getStringExtra("operation")
            if (operation.isNullOrEmpty()) {
                Log.error("ThemeManager", "主题操作广播缺少 operation 参数")
                return
            }
            val userId = intent.getStringExtra("userId")
            try {
                val res: Pair<Boolean, String> = when (operation) {
                    "EXPORT" -> ThemeManager.exportThemesDirectly(userId)
                    "DELETE" -> ThemeManager.deleteThemeCacheDirectly(userId)
                    "UPDATE" -> ThemeManager.applyThemeDirectly(userId)
                    else -> {
                        Log.runtime("ThemeManager", "未知的主题操作: $operation")
                        return
                    }
                }
                Log.runtime("ThemeManager", "主题操作 [$operation]: ${res.second}")
            } catch (th: Throwable) {
                Log.error("ThemeManager", "主题操作 [$operation] 异常: ${th.message}")
            }
        }

        /**
         * 统一处理会员/秒杀类操作（同步商品列表/查询权益详情/同步秒杀任务）
         *
         * 用 extra "operation" 区分具体操作，参数解析、判空、异常统一收口于此。
         */
        @JvmStatic
        fun handleMemberOperation(context: Context, intent: Intent) {
            when (intent.getStringExtra("operation")) {
                "FETCH_GOODS_LIST" -> {
                    val deliveryId = intent.getStringExtra("deliveryId")?.takeIf { it.isNotEmpty() }
                        ?: "94000SR2025120515775004"
                    val pageNum = intent.getIntExtra("pageNum", 1)
                    val zoneIndex = intent.getIntExtra("zoneIndex", -1)
                    handleFetchMemberGoodsList(context, deliveryId, pageNum, zoneIndex)
                }
                "SEARCH_GOODS" -> {
                    val query = intent.getStringExtra("query")?.takeIf { it.isNotEmpty() }
                    val pageNum = intent.getIntExtra("pageNum", 1)
                    if (query.isNullOrEmpty()) {
                        Log.error("搜索会员商品异常: 缺少 query 参数")
                    } else {
                        handleSearchMemberGoods(context, query, pageNum)
                    }
                }
                "QUERY_BENEFIT_DETAIL" -> {
                    val benefitId = intent.getStringExtra("benefitId")
                    if (benefitId.isNullOrEmpty()) {
                        Log.error("查询规格详情异常: 缺少 benefitId 参数")
                    } else {
                        handleQueryBenefitDetail(context, benefitId)
                    }
                }
                "SYNC_SECKILL_TASKS" -> SeckillScheduler.syncTasks(context)
                else -> Log.runtime("未知的会员操作: ${intent.getStringExtra("operation")}")
            }
        }

        /**
         * 处理重新运行或继续运行逻辑
         */
        @JvmStatic
        fun handleReRun(actionType: String) {
            try {
                when (actionType) {
                    ACTION_RERUN -> {
                        TaskScheduler.setStopped(false)
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
                        ModelTask.stopAllTask()
                        Log.runtime("[PauseRunReceiver]任务已暂停⏸")
                        Toast.show("任务已暂停", true)
                    }
                    ACTION_STOP -> {
                        Log.runtime("[StopRunReceiver]已发送任务停止信号，正在清理后台任务")

                        // 第一层：阻止调度器再次创建任务，并清除暂停态
                        TaskScheduler.setStopped(true)
                        TaskScheduler.setPaused(false)

                        // 第二层：停止主任务循环 + 批量启动流程 + 所有已启动的 ModelTask
                        // （LifecycleManager.stopHandler 内部已调用 ModelTask.stopAllTask，不再重复调用）
                        LifecycleManager.stopHandler()

                        // 第三层：停止通过 GlobalThreadPools 创建的任务（只取消任务，不取消作用域）
                        GlobalThreadPools.cancelAll()

                        // 第四层：停止生命周期和定时调度
                        TaskScheduler.shutdownExecutors()

                        // 短暂等待，让已下发取消信号的在途任务（协程/线程）有时间响应退出，
                        // 减少后续资源清理时仍在执行导致的异常打印
                        try { Thread.sleep(300) } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }

                        // 第五层：停止脱离统一生命周期的独立任务资源
                        // 每一步独立 try-catch，避免某个资源清理异常阻塞后续清理
                        try { PrivilegeTask.stopTask() } catch (_: Throwable) {}
                        try { ThreadPoolManager.shutdownNow() } catch (_: Throwable) {}
                        try { EnergyWaitingManager.clearAllWaitingTasks() } catch (_: Throwable) {}
                        try { SmartSchedulerManager.cancelAll() } catch (_: Throwable) {}
                        try { SmartSchedulerManager.cleanup() } catch (_: Throwable) {}
                        try { AlarmScheduler.unsetWakenAtTimeAlarm() } catch (_: Throwable) {}
                        try { AlarmScheduler.cancelAllExactAlarms() } catch (_: Throwable) {}
                        try {
                            Notify.setStatusTextDisabled()
                            Notify.updateNextExecText(-1)
                        } catch (_: Throwable) {}
                        Log.runtime("[StopRunReceiver]停止信号已全部下发，后台任务将陆续退出")
                        Toast.show("任务已停止并清除", true)
                    }
                }
            } catch (e: Exception) {
                Log.error("[ReRunReceiver]处理重新执行任务请求时出错❌: ${e.message}")
                Log.printStackTrace("ApplicationHook.ReRunReceiver", e)
            }
        }

        @JvmStatic
        fun handleFetchMemberGoodsList(
            context: Context,
            deliveryId: String = "94000SR2025120515775004",
            pageNum: Int = 1,
            zoneIndex: Int = -1
        ) {
            GlobalThreadPools.execute {
                try {
                    if (deliveryId == ALL_GOODS_DELIVERY_ID) {
                        fetchZoneGoodsList(context, deliveryId, pageNum, zoneIndex)
                    } else {
                        fetchCategoryGoodsList(context, deliveryId, pageNum)
                    }
                } catch (e: Exception) {
                    Log.error("获取会员商品列表异常: ${e.message}")
                    Log.printStackTrace("ExtendHandle.handleFetchMemberGoodsList", e)
                    val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                        putExtra("deliveryId", deliveryId)
                        putExtra("zoneIndex", zoneIndex)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }

        /**
         * 分类请求参数按抓包逐场景对齐（queryShandieEntityList）：
         * - filterTimesLimit / filterTimesLimitForPromo：真实请求固定 true
         * - filterExchangeTime：日常抢兑 true（只显示当前可兑换），万分好物/联名周边 false（保留定时秒杀商品）
         * - topIds：运营置顶商品，服务端只在请求带 topIds 时才下发置顶位（万分好物置顶了露营椅）
         */
        private val CATEGORY_EXTRA_PARAMS: Map<String, CategoryParams> = mapOf(
            "94000SR2025120515775004" to CategoryParams(filterExchangeTime = true, topIds = emptyList()),
            "94000SR2025120515776001" to CategoryParams(filterExchangeTime = false, topIds = listOf("202504010127705210")), // 置顶:【户外好物】会员定制露营椅
            "94000SR2025120515776002" to CategoryParams(filterExchangeTime = false, topIds = emptyList())
        )

        /** 分类扩展参数：filterExchangeTime 与置顶 topIds（按抓包配置，运营换品时更新 topIds） */
        private data class CategoryParams(val filterExchangeTime: Boolean, val topIds: List<String>)

        /**
         * 分类商品列表（日常抢兑/万分好物/联名周边），走 queryShandieEntityList。
         */
        private fun fetchCategoryGoodsList(context: Context, deliveryId: String, pageNum: Int) {
            // 动态获取真实会员积分，若接口失败优雅兜底为 99999（filterPointNoEnough 为 false 不影响商品下发）
            val point = queryMemberPoint().let { if (it >= 0) it else 99999 }

            var curPage = pageNum
            var hasSavedAny = false

            while (curPage <= 6) {
                // uniqueId 会话内固定（同一分类翻页时不变，对齐日志真实请求）
                val uniqueId = sessionUniqueIds.getOrPut("cat_$deliveryId") {
                    System.currentTimeMillis().toString() + deliveryId
                }
                // 请求参数按抓包对齐：filterTimesLimit/ForPromo 固定 true；filterExchangeTime 与 topIds 分场景配置
                val cfg = CATEGORY_EXTRA_PARAMS[deliveryId] ?: CategoryParams(filterExchangeTime = false, topIds = emptyList())
                val topIdsJson = cfg.topIds.joinToString(",") { "\"$it\"" }
                val params = "[{\"blackIds\":[],\"deliveryIdList\":[\"$deliveryId\"],\"filterCityCode\":false,\"filterExchangeTime\":${cfg.filterExchangeTime},\"filterPointNoEnough\":false,\"filterStockNoEnough\":false,\"filterTimesLimit\":true,\"filterTimesLimitForPromo\":true,\"pageNum\":$curPage,\"pageSize\":18,\"point\":$point,\"previewCopyDbId\":\"\",\"queryType\":\"DELIVERY_ID_LIST\",\"shandieComponentId\":\"\",\"source\":\"来源\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"},\"topIds\":[$topIdsJson],\"uniqueId\":\"$uniqueId\"}]"
                val response = requestStringWithRetry(
                    "com.alipay.alipaymember.biz.rpc.config.h5.queryShandieEntityList",
                    params
                )

                if (response.isEmpty()) {
                    if (!hasSavedAny) {
                        Log.error("获取会员商品列表失败：返回为空, deliveryId: $deliveryId, page: $curPage")
                        val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                            putExtra("deliveryId", deliveryId)
                        }
                        context.sendBroadcast(intent)
                        return
                    }
                    // 翻页中途失败：如实上报，避免"同步成功"假象掩盖缺页
                    Log.error("获取会员商品列表不完整：第 $curPage 页重试后仍为空, deliveryId: $deliveryId")
                    val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                        putExtra("deliveryId", deliveryId)
                        putExtra("partial", true)
                    }
                    context.sendBroadcast(intent)
                    return
                }

                try {
                    val jo = org.json.JSONObject(response)
                    val benefits = jo.optJSONArray("benefits")
                    if (benefits == null || benefits.length() == 0) {
                        if (!hasSavedAny) {
                            Log.runtime("获取的会员商品列表为空，不覆盖本地缓存, pageNum: $curPage")
                            val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                                putExtra("deliveryId", deliveryId)
                                putExtra("reason", "no_more")
                            }
                            context.sendBroadcast(intent)
                            return
                        }
                        break
                    }
                    saveMemberGoodsResponse(response, "cat_$deliveryId", curPage)
                    hasSavedAny = true

                    val next = jo.optInt("nextPageNum", 0)
                    // 用户从第1页发起同步时，自动拉取后续所有页面，确保整个分类全部商品完整入库
                    if (pageNum == 1 && next > curPage) {
                        curPage = next
                        // 翻页间隔放宽，降低触发服务端限流返回空的概率
                        Thread.sleep(800)
                    } else {
                        break
                    }
                } catch (ex: Exception) {
                    Log.error("解析会员商品列表异常: ${ex.message}")
                    val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                        putExtra("deliveryId", deliveryId)
                        if (hasSavedAny) putExtra("partial", true)
                    }
                    context.sendBroadcast(intent)
                    return
                }
            }

            if (hasSavedAny) {
                val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.success").apply {
                    putExtra("deliveryId", deliveryId)
                }
                context.sendBroadcast(intent)
            }
        }

        /**
         * "全部商品"分区列表，走 queryDeliveryZoneDetail。
         * 按广播传入的 zoneIndex 对应积分区间（0-501 / 501-3000 / 3001-10000 / 10001+）。
         * 从第1页发起同步时自动拉取该分区全量数据。
         */
        private fun fetchZoneGoodsList(context: Context, deliveryId: String, pageNum: Int, zoneIndex: Int) {
            if (zoneIndex < 0 || zoneIndex >= ZONE_POINT_RANGES.size) {
                Log.error("获取全部商品失败：zoneIndex 非法: $zoneIndex")
                return
            }
            val (lowerPoint, upperPoint) = ZONE_POINT_RANGES[zoneIndex]

            val deliveryIdsJson = ALL_GOODS_DELIVERY_IDS.joinToString(",") { "\"$it\"" }
            var curPage = pageNum
            var hasSavedAny = false

            while (curPage <= 6) {
                // uniqueId 会话内固定（同一分区翻页时不变，对齐日志真实请求）
                val uniqueId = sessionUniqueIds.getOrPut("zone_${deliveryId}_$zoneIndex") {
                    System.currentTimeMillis().toString() + lowerPoint + "and" + upperPoint + "INTELLIGENT_SORT" + ALL_GOODS_DELIVERY_IDS.joinToString(",")
                }
                val params = "[{\"deliveryIdList\":[$deliveryIdsJson],\"lowerPoint\":$lowerPoint,\"pageNum\":$curPage,\"pageSize\":18,\"queryNoReserve\":true,\"resourceCardChannel\":\"ZERO_EXCHANGE_CHANNEL\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"},\"startPageFirstQuery\":false,\"topIdList\":[\"202412231259661040\"],\"uniqueId\":\"$uniqueId\",\"upperPoint\":$upperPoint,\"withPointRange\":true}]"
                val response = requestStringWithRetry(
                    "com.alipay.alipaymember.biz.rpc.config.h5.queryDeliveryZoneDetail",
                    params
                )

                if (response.isEmpty()) {
                    if (!hasSavedAny) {
                        Log.error("获取全部商品分区失败：返回为空, zone: $zoneIndex, page: $curPage")
                        val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                            putExtra("deliveryId", deliveryId)
                            putExtra("zoneIndex", zoneIndex)
                        }
                        context.sendBroadcast(intent)
                        return
                    }
                    // 翻页中途失败：如实上报，避免"同步成功"假象掩盖缺页
                    Log.error("获取全部商品分区不完整：第 $curPage 页重试后仍为空, zone: $zoneIndex")
                    val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                        putExtra("deliveryId", deliveryId)
                        putExtra("zoneIndex", zoneIndex)
                        putExtra("partial", true)
                    }
                    context.sendBroadcast(intent)
                    return
                }

                // 统一响应结构为 {benefits:[...], nextPageNum}
                val normalized = normalizeZoneResponse(response)
                if (normalized == null || normalized.optJSONArray("benefits")?.length() == 0) {
                    if (!hasSavedAny) {
                        Log.runtime("获取的全部商品分区列表为空，不覆盖本地缓存, zone: $zoneIndex, page: $curPage")
                        val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed").apply {
                            putExtra("deliveryId", deliveryId)
                            putExtra("zoneIndex", zoneIndex)
                            putExtra("reason", "no_more")
                        }
                        context.sendBroadcast(intent)
                        return
                    }
                    break
                }

                saveMemberGoodsResponse(normalized.toString(), "zone_${deliveryId}_$zoneIndex", curPage)
                hasSavedAny = true

                val next = normalized.optInt("nextPageNum", 0)
                if (pageNum == 1 && next > curPage) {
                    curPage = next
                    // 翻页间隔放宽，降低触发服务端限流返回空的概率
                    Thread.sleep(800)
                } else {
                    break
                }
            }

            if (hasSavedAny) {
                val intent = Intent("fansirsqi.xposed.sesame.fetchMemberGoodsList.success").apply {
                    putExtra("deliveryId", deliveryId)
                    putExtra("zoneIndex", zoneIndex)
                }
                context.sendBroadcast(intent)
            }
        }

        /**
         * 服务端搜索会员商品，走 benefitSearchV2（参数对齐日志）。
         */
        @JvmStatic
        fun handleSearchMemberGoods(context: Context, query: String, pageNum: Int = 1) {
            GlobalThreadPools.execute {
                try {
                    val now = System.currentTimeMillis()
                    val params = "[{\"cityCode\":\"$SEARCH_CITY_CODE\",\"clientOs\":\"Android\",\"clientVersion\":\"$SEARCH_CLIENT_VERSION\",\"lbs\":\"\",\"pageNum\":$pageNum,\"pageSize\":10,\"paramsMap\":{},\"prePageAllZeroStock\":false,\"previewCopyDbId\":\"\",\"query\":\"$query\",\"recommend\":false,\"searchId\":\"${now}&memberSearch\",\"sessionId\":\"memberSearch&$now\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]"
                    val response = RequestManager.requestString(
                        "com.alipay.alipaymember.biz.rpc.config.h5.benefitSearchV2",
                        params
                    )

                    if (response.isEmpty()) {
                        Log.error("搜索会员商品失败：返回为空")
                        val intent = Intent("fansirsqi.xposed.sesame.searchMemberGoods.failed").apply {
                            putExtra("query", query)
                        }
                        context.sendBroadcast(intent)
                        return@execute
                    }

                    val normalized = normalizeSearchResponse(response)
                    if (normalized == null) {
                        Log.error("搜索会员商品响应解析异常")
                        val intent = Intent("fansirsqi.xposed.sesame.searchMemberGoods.failed").apply {
                            putExtra("query", query)
                        }
                        context.sendBroadcast(intent)
                        return@execute
                    }

                    saveMemberGoodsResponse(normalized.toString(), "search", pageNum)

                    val intent = Intent("fansirsqi.xposed.sesame.searchMemberGoods.success").apply {
                        putExtra("query", query)
                        putExtra("hasNext", normalized.optInt("nextPageNum", 0) > 0)
                    }
                    context.sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.error("搜索会员商品异常: ${e.message}")
                    Log.printStackTrace("ExtendHandle.handleSearchMemberGoods", e)
                    val intent = Intent("fansirsqi.xposed.sesame.searchMemberGoods.failed").apply {
                        putExtra("query", query)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }

        /**
         * 带重试的 RPC 请求：翻页请求偶发返回空（限流/瞬断）时重试，避免静默丢页导致本地缓存不完整。
         */
        private fun requestStringWithRetry(method: String, params: String, attempts: Int = 3): String {
            var lastError: Exception? = null
            repeat(attempts) { attempt ->
                try {
                    val res = RequestManager.requestString(method, params)
                    if (res.isNotEmpty()) return res
                } catch (e: Exception) {
                    lastError = e
                }
                if (attempt < attempts - 1) {
                    Thread.sleep(1000L * (attempt + 1))
                }
            }
            Log.error("RPC 请求重试 $attempts 次后仍失败: $method, ${lastError?.message ?: "返回为空"}")
            return ""
        }

        /** 查询会员积分（queryMemberInfo 响应顶层 pointBalance） */
        private fun queryMemberPoint(): Int {
            return try {
                val response = RequestManager.requestString(
                    "com.alipay.alipaymember.biz.rpc.member.h5.queryMemberInfo",
                    "[{\"needExpirePoint\":true,\"needGrade\":true,\"needPoint\":true,\"queryScene\":\"POINT_EXCHANGE_SCENE\",\"source\":\"POINT_EXCHANGE_SCENE\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]"
                )
                if (response.isEmpty()) return -1
                val jo = org.json.JSONObject(response)
                jo.optInt("pointBalance", -1)
            } catch (e: Exception) {
                Log.error("查询会员积分异常: ${e.message}")
                -1
            }
        }

        /** 将 queryDeliveryZoneDetail 响应统一为 {benefits:[...], nextPageNum}，按 benefitId 去重 */
        private fun normalizeZoneResponse(response: String): org.json.JSONObject? {
            return try {
                val jo = org.json.JSONObject(response)
                val benefits = org.json.JSONArray()
                val seen = HashSet<String>()
                jo.optJSONArray("briefConfigInfos")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i)
                        if (item != null) {
                            val bid = item.optString("benefitId", "")
                            if (bid.isNotEmpty() && seen.add(bid)) {
                                benefits.put(item)
                            }
                        }
                    }
                }
                jo.optJSONArray("entityInfoList")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val entity = arr.optJSONObject(i)
                        val benefit = entity?.optJSONObject("benefitInfo")
                        if (benefit != null) {
                            val bid = benefit.optString("benefitId", "")
                            if (bid.isNotEmpty() && seen.add(bid)) {
                                benefits.put(benefit)
                            }
                        }
                    }
                }
                org.json.JSONObject().apply {
                    put("benefits", benefits)
                    put("nextPageNum", jo.optInt("nextPageNum", 0))
                }
            } catch (e: Exception) {
                Log.error("解析全部商品分区响应异常: ${e.message}")
                null
            }
        }

        /** 将 benefitSearchV2 响应统一为 {benefits:[...], nextPageNum}（entityInfoList[].benefitInfo） */
        private fun normalizeSearchResponse(response: String): org.json.JSONObject? {
            return try {
                val jo = org.json.JSONObject(response)
                val benefits = org.json.JSONArray()
                jo.optJSONArray("entityInfoList")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val entity = arr.optJSONObject(i)
                        val benefit = entity?.optJSONObject("benefitInfo")
                        if (benefit != null) {
                            benefits.put(benefit)
                        }
                    }
                }
                org.json.JSONObject().apply {
                    put("benefits", benefits)
                    put("nextPageNum", jo.optInt("nextPageNum", 0))
                }
            } catch (e: Exception) {
                Log.error("解析搜索响应异常: ${e.message}")
                null
            }
        }

        /**
         * 分文件保存（商品池 goods.json + 列表索引 lists.json）：
         * 1. benefits 按 benefitId 写入商品池（同一商品只存一份，跨分类共用）
         * 2. 列表引用：第 1 页覆盖 ids，后续页合并去重，并更新 nextPageNum
         * 3. 清理未被任何列表引用的商品，防止池无限膨胀
         * 4. 只有商品池变化才写 goods.json，列表索引每次都写（写放大最小）
         */
        private fun saveMemberGoodsResponse(response: String, listKey: String, pageNum: Int) {
            // 共享锁：与 SeckillActivity 的规格写回互斥，避免并发"读-改-写"互相覆盖
            synchronized(Files.memberGoodsLock()) {
                try {
                    val goodsFile = Files.getMemberGoodsPoolFile()
                    val listsFile = Files.getMemberGoodsListFile()
                    val goods = readMemberGoodsJsonFile(goodsFile)
                    val lists = readMemberGoodsJsonFile(listsFile)

                    val jo = org.json.JSONObject(response)
                    val benefits = jo.optJSONArray("benefits") ?: org.json.JSONArray()
                    val nextPageNum = jo.optInt("nextPageNum", 0)

                    // 1) 商品池：按 benefitId 写入，同一商品只保留一份
                    var poolChanged = false
                    val newIds = org.json.JSONArray()
                    for (i in 0 until benefits.length()) {
                        val benefit = benefits.optJSONObject(i) ?: continue
                        val bid = benefit.optString("benefitId", "")
                        if (bid.isEmpty()) continue
                        goods.put(bid, benefit)
                        newIds.put(bid)
                        poolChanged = true
                    }

                    // 2) 列表引用：第 1 页覆盖，后续页合并去重
                    val listJo = lists.optJSONObject(listKey) ?: org.json.JSONObject().also { lists.put(listKey, it) }
                    val ids = if (pageNum == 1) {
                        org.json.JSONArray()
                    } else {
                        listJo.optJSONArray("ids") ?: org.json.JSONArray()
                    }
                    for (i in 0 until newIds.length()) {
                        val bid = newIds.optString(i)
                        if (!containsId(ids, bid)) {
                            ids.put(bid)
                        }
                    }
                    listJo.put("ids", ids)
                    listJo.put("nextPageNum", nextPageNum)

                    // 3) 清理未被任何列表引用的商品，防止商品池无限膨胀
                    val referenced = HashSet<String>()
                    lists.keys().forEach { key ->
                        lists.optJSONObject(key)?.optJSONArray("ids")?.let { refIds ->
                            for (i in 0 until refIds.length()) {
                                referenced.add(refIds.optString(i))
                            }
                        }
                    }
                    val iter = goods.keys()
                    while (iter.hasNext()) {
                        val bid = iter.next()
                        if (!referenced.contains(bid)) {
                            iter.remove()
                            poolChanged = true
                        }
                    }

                    // 4) 分别原子写：只有商品池变化才写商品池，列表索引每次写
                    if (poolChanged) {
                        Files.write2FileAtomic(goods.toString(), goodsFile)
                    }
                    Files.write2FileAtomic(lists.toString(), listsFile)
                } catch (ex: Exception) {
                    Log.error("保存会员商品缓存异常: ${ex.message}")
                }
            }
        }

        private fun readMemberGoodsJsonFile(file: java.io.File): org.json.JSONObject {
            if (file.exists()) {
                val content = Files.readFromFile(file)
                if (content.isNotEmpty()) {
                    return try {
                        org.json.JSONObject(content)
                    } catch (e: Exception) {
                        Log.error("解析会员商品缓存文件异常: ${e.message}")
                        org.json.JSONObject()
                    }
                }
            }
            return org.json.JSONObject()
        }

        private fun containsId(ids: org.json.JSONArray, bid: String): Boolean {
            for (i in 0 until ids.length()) {
                if (ids.optString(i) == bid) return true
            }
            return false
        }

        @JvmStatic
        fun handleQueryBenefitDetail(context: Context, benefitId: String) {
            GlobalThreadPools.execute {
                try {
                    val params = "[{\"benefitId\":\"$benefitId\",\"cityCode\":\"450300\",\"miniAppId\":\"\",\"requestSourceInfo\":\"myTab\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"}}]"
                    val response = RequestManager.requestString(
                        "com.alipay.alipaymember.biz.rpc.config.h5.querySingleBenefitDetail",
                        params
                    )
                    
                    if (response.isEmpty()) {
                        Log.error("查询商品详情规格失败：返回为空")
                        return@execute
                    }
                    
                    val jo = org.json.JSONObject(response)
                    val benefitDetail = jo.optJSONObject("benefitDetail")
                    val skuInfoList = benefitDetail?.optJSONArray("skuInfoList")
                    val simpleSkus = benefitDetail?.optJSONArray("simpleSkus")
                    
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
                    
                    if (fetchedSkuId == "-1" && simpleSkus != null && simpleSkus.length() > 0) {
                        for (i in 0 until simpleSkus.length()) {
                            val skuObj = simpleSkus.optJSONObject(i)
                            if (skuObj != null) {
                                val sId = skuObj.optString("sku_id", "").takeIf { it.isNotEmpty() }
                                    ?: skuObj.optString("skuId", "-1")
                                if (sId != "-1") {
                                    if (fetchedSkuId == "-1") {
                                        fetchedSkuId = sId
                                    }
                                    
                                    val priceCent = skuObj.optDouble("price_cent", -1.0)
                                    val sPrice = if (priceCent >= 0) {
                                        String.format(Locale.US, "%.2f", priceCent / 100.0)
                                    } else {
                                        val p = skuObj.optString("price", "")
                                        if (p.isNotEmpty()) p else benefitDetail.optString("priceYuan", "0.00")
                                    }
                                    
                                    var sPoints = skuObj.optInt("points", 0)
                                    if (sPoints == 0) {
                                        val pricePresentation = benefitDetail.optJSONObject("pricePresentation")
                                        sPoints = pricePresentation?.optInt("point", 0) ?: 0
                                    }
                                    if (sPoints == 0) {
                                        val pointDisplay = benefitDetail.optJSONObject("pointPriceForDisplay")
                                        sPoints = pointDisplay?.optInt("minPoint", 0) ?: 0
                                    }
                                    skuIdsList.add("$sId|$sPrice|$sPoints")
                                }
                            }
                        }
                    }
                    
                    // 无规格也要回包（skuId 传 "-1"），直达按钮据此兜底详情页而不是无限等待
                    val intent = Intent("fansirsqi.xposed.sesame.queryBenefitDetail.success").apply {
                        putExtra("benefitId", benefitId)
                        putExtra("skuId", fetchedSkuId)
                        putStringArrayListExtra("skuIds", skuIdsList)
                    }
                    context.sendBroadcast(intent)
                    if (fetchedSkuId == "-1") {
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
