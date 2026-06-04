package fansirsqi.xposed.sesame.task.exchange

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import fansirsqi.xposed.sesame.entity.MemberBenefit
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.TimeUtil
import java.util.Collections
import java.util.LinkedHashMap

class PrivilegeEX : BaseFlashSaleTask(), YouthPrivilegeSupport {

    companion object {
        private const val TAG = "青春特权EX🎓"

        // 动态缓存兑换项，使用 LRU 缓存限制大小
        @JvmField
        val ITEM_MAP: MutableMap<String, ExchangeItem> =
            Collections.synchronizedMap(object : LinkedHashMap<String, ExchangeItem>(10, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExchangeItem>?): Boolean {
                    return size > 10 // 最多保留10个兑换项
                }
            })

        // DataStore 缓存键
        private const val CACHE_KEY_EXCHANGE_ITEMS = "privilege_exchange_items_cache"
        private const val CACHE_EXPIRE_TIME = 24 * 60 * 60 * 1000L // 24小时过期

        /**
         * 构建兑换项给UI（直接使用 DataStore）
         */
        @JvmStatic
        @SuppressLint("NewApi")
        fun getExchangeItemListForUI(): List<MemberBenefit> {
            // 此函数在 UI 主线程被调用（作为 SelectListFunc），绝对不能做网络请求。
            // 只从 DataStore 缓存读取数据；网络刷新由 FlashSaleModule 预加载机制负责。
            try {
                val cacheContent = DataStore.get(
                    CACHE_KEY_EXCHANGE_ITEMS,
                    String::class.java
                )

                if (cacheContent != null && cacheContent.isNotEmpty()) {
                    val cacheData = JSONObject(cacheContent)
                    val itemsArray = cacheData.optJSONArray("items")
                    // 无论缓存是否过期，只要有数据就返回给 UI 显示
                    if (itemsArray != null && itemsArray.length() > 0) {
                        val benefits = mutableListOf<MemberBenefit>()
                        for (i in 0 until itemsArray.length()) {
                            val item = itemsArray.getJSONObject(i)
                            val id = item.optString("id")
                            val name = item.optString("name")
                            if (id.isNotEmpty()) {
                                benefits.add(MemberBenefit(id, name))
                            }
                        }
                        return benefits
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "从 DataStore 读取缓存时发生异常: ${e.message}")
            }
            return emptyList()
        }

        /**
         * 手动刷新兑换项列表（供UI调用）
         */
        @JvmStatic
        @SuppressLint("NewApi")
        fun refreshExchangeItemsFromAPI(): List<MemberBenefit> {
            val benefits = mutableListOf<MemberBenefit>()

            try {
                // 动态查询可用的兑换项
                val method = "alipay.membertangram.biz.rpc.student.queryCashExchangeInfoResult"
                val data = "[{\"chInfo\":\"ch_appcenter__chsub_9patch\",\"skipTaskModule\":false}]"
                val responseStr = RequestManager.requestString(method, data)
                val response = JSONObject(responseStr)

                if (response.optBoolean("success", false)) {
                    val cashExchangeInfoVOList = response.optJSONArray("cashExchangeInfoVOList")
                    if (cashExchangeInfoVOList != null) {
                        for (i in 0 until cashExchangeInfoVOList.length()) {
                            val exchangeInfo = cashExchangeInfoVOList.getJSONObject(i)
                            val exchangeType = exchangeInfo.optString("exchangeType")
                            val prizeInfoVOList = exchangeInfo.optJSONArray("prizeInfoVOList")

                            if (prizeInfoVOList != null) {
                                for (j in 0 until prizeInfoVOList.length()) {
                                    val prizeInfo = prizeInfoVOList.getJSONObject(j)
                                    val benefitId = prizeInfo.optString("benefitId")
                                    val prizeDesc = prizeInfo.optString("prizeDesc")
                                    val prizePrice = prizeInfo.optString("prizePrice")

                                    if (benefitId.isNotEmpty()) {
                                        // 构建显示名称
                                        var displayName = "${prizeDesc}元"
                                        when (exchangeType) {
                                            "SMALL_CASH_EXCHANGE" -> displayName += " (小额)"
                                            "LARGE_CASH_EXCHANGE" -> displayName += " (大额)"
                                        }

                                        benefits.add(MemberBenefit(benefitId, displayName))

                                        // 同时更新 ITEM_MAP 缓存（使用 synchronized 避免竞态）
                                        synchronized(ITEM_MAP) {
                                            if (!ITEM_MAP.containsKey(benefitId)) {
                                                try {
                                                    val price = prizePrice.toDouble()
                                                    val item = createExchangeItemStatic(benefitId, price)
                                                    if (item != null) {
                                                        ITEM_MAP[benefitId] = item
                                                    }
                                                } catch (e: Exception) {
                                                    Log.error(TAG, "解析兑换项失败: $benefitId")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 保存到 DataStore
                    if (benefits.isNotEmpty()) {
                        val cacheData = JSONObject()
                        val itemsArray = JSONArray()

                        for (benefit in benefits) {
                            val item = JSONObject().apply {
                                put("id", benefit.id)
                                put("name", benefit.name)
                            }
                            itemsArray.put(item)
                        }

                        cacheData.put("items", itemsArray)
                        cacheData.put("timestamp", System.currentTimeMillis())

                        DataStore.put(
                            CACHE_KEY_EXCHANGE_ITEMS,
                            cacheData.toString()
                        )

                        //Log.other("$TAG✅ 成功获取并缓存 ${benefits.size} 个兑换项")
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "获取UI兑换项列表时发生异常: ${e.message}")
            }

            // 如果动态获取失败，返回空列表
            if (benefits.isEmpty()) {
                Log.error(TAG, "⚠️ 无法获取兑换项列表，请检查网络连接")
            }

            return benefits
        }

        /**
         * 静态方法：根据benefitId和价格创建ExchangeItem
         */
        private fun createExchangeItemStatic(benefitId: String, price: Double): ExchangeItem? {
            return try {
                when {
                    benefitId.startsWith("small_") -> {
                        // 小额红包，面额通常等于价格，数量为面额*10（单位为分）
                        val count = (price * 10).toInt()
                        ExchangeItem(benefitId, price, count)
                    }
                    benefitId.startsWith("large") -> {
                        // 大额红包，面额等于价格，数量也等于面额
                        val count = price.toInt()
                        ExchangeItem(benefitId, price, count)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.error(TAG, "创建ExchangeItem时发生异常: ${e.message}")
                null
            }
        }

        /**
         * 获取兑换项详细名称（支持动态benefitId）
         */
        @JvmStatic
        fun getItemDisplayName(item: ExchangeItem?): String {
            if (item == null || item.code == null) {
                return "未知红包"
            }

            // 动态解析 benefitId，支持任意后缀（如 _2, _3, _4 等）
            return when {
                item.code.startsWith("small_") -> {
                    // 解析small类型红包的面额：small_0.1_3 -> 0.1
                    val parts = item.code.split("_")
                    if (parts.size >= 2) {
                        try {
                            val amount = parts[1].toDouble()
                            "${amount}红包"
                        } catch (e: NumberFormatException) {
                            "${item.value}红包"
                        }
                    } else {
                        "${item.value}红包"
                    }
                }
                item.code.startsWith("large") -> {
                    "${item.value.toInt()}红包"
                }
                else -> "${item.value}红包"
            }
        }
    }

    // 由外部注入的字段
    var isSmallExchange: Boolean = false
    var privilege: BooleanModelField? = null // 青春特权大额
    var privilegeSmall: BooleanModelField? = null // 青春特权小额
    var enablePrivilegeList: BooleanModelField? = null // 列表模式
    var wakeUpMinuteBefore: IntegerModelField? = null // 唤醒时间
    var youthPrivilegeList: SelectModelField? = null //兑换列表

    override fun isConcurrentMode(): Boolean {
        return true // 默认启用并发模式
    }

    override fun getName(): String {
        return "青春特权EX🎓"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.EXCHANGE
    }

    override fun getIcon(): String {
        return "AntSports.png"
    }

    @SuppressLint("NewApi")
    override fun buildExchangeItems(): List<ExchangeItem> {
        // 优化：无论什么模式，都先获取最新的可用兑换项
        //Log.other("$TAG 正在获取最新的可用兑换项...")
        val availableItems = queryAvailableExchangeItems()

        if (availableItems.isEmpty()) {
            Log.error(TAG, "⚠️ 无法获取可用兑换项，请检查网络连接或稍后重试")
            return emptyList()
        }

        Log.other("$TAG 成功获取 ${availableItems.size} 个可用兑换项")

        // 更新 ITEM_MAP 缓存，确保最新数据
        synchronized(ITEM_MAP) {
            for (item in availableItems) {
                ITEM_MAP[item.code] = item
            }
        }

        // 条件1：是否启用了权益列表模式
        if (enablePrivilegeList?.value == true && youthPrivilegeList?.value != null) {
            val selectedNames = getYouthPrivilegeSelectedNames()

            // 检查是否有选中的项目
            if (selectedNames.isNotEmpty()) {
                val result = mutableListOf<ExchangeItem>()

                // 按照用户选择的顺序添加项目
                for (code in selectedNames) {
                    // 从最新获取的可用项目中查找
                    val item = availableItems.find { it.code == code }

                    if (item != null) {
                        result.add(item)
                        Log.other("$TAG ✓ 找到用户选择的兑换项: ${getItemDisplayName(item)}")
                    } else {
                        Log.other("$TAG ✗ 用户选择的兑换项不可用: $code")
                    }
                }

                if (result.isNotEmpty()) {
                    Log.other("$TAG 按用户选择顺序构建兑换项: ${result.size} 项")
                    return result
                } else {
                    Log.other("$TAG 用户选择的兑换项均不可用，回退到默认小额/大额模式")
                }
            } else {
                Log.other("$TAG 未选择具体权益，回退到默认小额/大额模式")
            }
        }

        // 条件2：未启用权益列表模式或回退情况，按小额/大额模式过滤
        return filterItemsByMode(availableItems)
    }

    /**
     * 根据小额/大额模式过滤兑换项
     */
    private fun filterItemsByMode(availableItems: List<ExchangeItem>): List<ExchangeItem> {
        val filteredItems = mutableListOf<ExchangeItem>()
        val isSmallMode = isSmallExchange

        for (item in availableItems) {
            if (isSmallMode && item.code.startsWith("small_")) {
                filteredItems.add(item)
            } else if (!isSmallMode && item.code.startsWith("large_")) {
                filteredItems.add(item)
            }
        }

        if (filteredItems.isNotEmpty()) {
            val modeType = if (isSmallMode) "小额" else "大额"
            Log.other("$TAG 按${modeType}模式过滤，获得 ${filteredItems.size} 个可兑换项")
        } else {
            Log.error(TAG, "⚠️ 当前模式下没有可用的兑换项")
        }

        return filteredItems
    }

    @SuppressLint("NewApi")
    override fun getYouthPrivilegeSelectedNames(): List<String> {
        if (youthPrivilegeList == null || youthPrivilegeList?.value == null) {
            Log.error("⚠️ 权益列表字段未初始化")
            return emptyList()
        }

        // 统一清理格式，保持顺序
        return youthPrivilegeList?.value
            ?.map { it.replace("\\s+".toRegex(), "") } // 移除所有空白
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    override fun getTargetHour(): Long {
        return if (isSmallExchange) 0 else 10
    }

    override fun tryExchange(item: ExchangeItem): Boolean {
        return try {
            item.exchangeParams = buildExchangeParams(item)
            // 新增校验点
            val params = item.exchangeParams
            if (params == null || params.length() == 0) {
                Log.error(TAG, "⚠️ 参数预构建失败：空参数 | 兑换项：${item.code}")
                return false
            }

            // 增加参数格式校验
            val benefit = params.getJSONObject(0)
            if (!benefit.has("benefitId")) {
                Log.error(TAG, "❌ 参数格式错误：缺少benefitId字段")
                return false
            }
            Log.other("$TAG ✅ 预构建参数: ${item.value}")
            true
        } catch (e: Exception) {
            Log.error(TAG, "构建参数失败：${e.message}")
            false
        }
    }

    private fun buildExchangeParams(item: ExchangeItem): JSONArray {
        val benefit = JSONObject().apply {
            put("benefitId", item.code)
        }

        return JSONArray().apply {
            put(benefit)
        }
    }

    @SuppressLint("NewApi")
    override fun sendExchangeRequestAsync(params: JSONArray, item: ExchangeItem): Boolean {
        return try {
            val method = if (isSmallExchange) {
                "alipay.membertangram.biz.rpc.student.smallCashExchangeTrigger"
            } else {
                "alipay.membertangram.biz.rpc.student.largeCashExchangeTrigger"
            }

            Log.debug("[${item.value}]请求发送时间: ${TimeUtil.getCommonDate(System.currentTimeMillis())}")
            val res = RequestManager.requestString(method, params.toString())
            val response = JSONObject(res)

            if (response.optBoolean("success")) {
                Log.other("${name}✅ 成功兑换:${getItemDisplayName(item)}")
                Notify.sendNewNotification(name, "成功兑换:${getItemDisplayName(item)}")
                true
            } else {
                Log.other("$TAG❌ 兑换${item.value}失败:${response.optString("resultDesc", "请稍等哦，马上出来")}")
                false
            }
        } catch (e: Exception) {
            Log.error(TAG, "兑换[${item.code}]时发生异常:${e.message}")
            false
        }
    }

    override fun sendExchangeRequest(params: JSONArray, item: ExchangeItem): Boolean {
        return try {
            if (params == null) {
                Log.error(TAG, "⚠️ 无法发送空参数请求，兑换项：${item.code}")
                return false
            }
            val method = if (isSmallExchange) {
                "alipay.membertangram.biz.rpc.student.smallCashExchangeTrigger"
            } else {
                "alipay.membertangram.biz.rpc.student.largeCashExchangeTrigger"
            }

            Log.debug("[${item.value}]请求发送时间: ${TimeUtil.getCommonDate(System.currentTimeMillis())}")
            val res = RequestManager.requestString(method, params.toString())
            val response = JSONObject(res)

            if (response.optBoolean("success")) {
                Log.other("${name}✅ 成功兑换:${getItemDisplayName(item)}")
                Notify.sendNewNotification(name, "成功兑换:${getItemDisplayName(item)}")
                true
            } else {
                Log.other("$TAG❌ 兑换${item.value}失败:${response.optString("resultDesc", "请稍等哦，马上出来")}")
                false
            }
        } catch (e: Exception) {
            Log.error(TAG, "兑换[${item.code}]时发生异常:${e.message}")
            false
        }
    }

    /**
     * 查询余额
     */
    @Throws(JSONException::class)
    private fun queryAmount(): String {
        val method = "alipay.membertangram.biz.rpc.student.queryCashExchangeInfoResult"
        val data = "[{\"chInfo\":\"ch_appcenter__chsub_9patch\",\"skipTaskModule\":false}]"
        val res = JSONObject(RequestManager.requestString(method, data))
        var amount = ""
        if (res.optBoolean("success")) {
            val info = res.optJSONObject("studentCheckInAmountInfo")
            if (info != null) {
                amount = info.optString("totalAmount", "")
            }
        }
        return amount
    }

    /**
     * 查询可兑换的权益列表
     */
    private fun queryAvailableExchangeItems(): List<ExchangeItem> {
        val availableItems = mutableListOf<ExchangeItem>()
        try {
            val method = "alipay.membertangram.biz.rpc.student.queryCashExchangeInfoResult"
            val data = "[{\"chInfo\":\"ch_appcenter__chsub_9patch\",\"skipTaskModule\":false}]"
            val responseStr = RequestManager.requestString(method, data)
            val response = JSONObject(responseStr)

            if (response.optBoolean("success", false)) {
                val cashExchangeInfoVOList = response.optJSONArray("cashExchangeInfoVOList")
                if (cashExchangeInfoVOList != null) {
                    for (i in 0 until cashExchangeInfoVOList.length()) {
                        val exchangeInfo = cashExchangeInfoVOList.getJSONObject(i)
                        val prizeInfoVOList = exchangeInfo.optJSONArray("prizeInfoVOList")

                        if (prizeInfoVOList != null) {
                            for (j in 0 until prizeInfoVOList.length()) {
                                val prizeInfo = prizeInfoVOList.getJSONObject(j)
                                val benefitId = prizeInfo.optString("benefitId")
                                val prizePrice = prizeInfo.optString("prizePrice")
                                val status = prizeInfo.optString("status")

                                // 只添加可兑换的项目（状态为EXCHANGE）
                                if ("EXCHANGE" == status && benefitId.isNotEmpty()) {
                                    // 根据benefitId创建对应的ExchangeItem
                                    val item = createExchangeItem(benefitId, prizePrice)
                                    if (item != null) {
                                        availableItems.add(item)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "查询可兑换权益列表时发生异常: ${e.message}")
        }
        return availableItems
    }

    /**
     * 根据benefitId和价格创建ExchangeItem
     */
    private fun createExchangeItem(benefitId: String, prizePrice: String): ExchangeItem? {
        return try {
            // 如果ITEM_MAP中已存在该benefitId，直接使用
            if (ITEM_MAP.containsKey(benefitId)) {
                return ITEM_MAP[benefitId]
            }

            // 根据价格解析创建新的ExchangeItem
            val price = prizePrice.toDouble()
            when {
                benefitId.startsWith("small_") -> {
                    // 小额红包，面额通常等于价格，数量为面额*10（单位为分）
                    val count = (price * 10).toInt()
                    val item = ExchangeItem(benefitId, price, count)
                    // 同时添加到ITEM_MAP中，供后续使用
                    ITEM_MAP[benefitId] = item
                    item
                }
                benefitId.startsWith("large") -> {
                    // 大额红包，面额等于价格，数量也等于面额
                    val count = price.toInt()
                    val item = ExchangeItem(benefitId, price, count)
                    // 同时添加到ITEM_MAP中，供后续使用
                    ITEM_MAP[benefitId] = item
                    item
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.error(TAG, "创建ExchangeItem时发生异常: ${e.message}")
            null
        }
    }

    override val exchangeMode: ExchangeMode
        get() = ExchangeMode.SINGLE

    override val completedKey: String
        get() = if (isSmallExchange) {
            CompletedKeyEnum.privilegeEXSmall.name
        } else {
            CompletedKeyEnum.privilegeEXNew.name
        }

    override fun getWakeUpConfigField(): IntegerModelField? {
        return wakeUpMinuteBefore
    }

    override fun check(): Boolean {
        return super.check()
    }

    override fun getFields(): ModelFields {
        // 不再定义任何字段，由 FlashSaleModule 统一管理
        return ModelFields()
    }
}
