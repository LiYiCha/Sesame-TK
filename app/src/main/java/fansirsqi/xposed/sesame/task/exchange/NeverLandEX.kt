package fansirsqi.xposed.sesame.task.exchange

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONObject
import fansirsqi.xposed.sesame.entity.MemberBenefit
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil

class NeverLandEX : BaseFlashSaleTask() {

    companion object {
        private const val TAG = "健康岛EX🍰"
        private const val PAY_COUPON_KEYWORD = "支付通用红包"
        private const val CACHE_KEY_NEVERLAND_ITEMS = "neverland_exchange_items_cache"
        private const val QUERY_METHOD = "com.alipay.neverland.biz.rpc.queryFlashSaleItemList"
        private const val ORDER_METHOD = "com.alipay.neverland.biz.rpc.createOrder"
        /** 渠道来源，跟 App 内部登录一致，修改时需确认实际报文 */
        private const val CH_INFO = "ch_appid-20001003"
        /**
         * 城市码。如果健康岛活动按城市区分，这里需要动态获取用户实际城市码。
         * 目前先用全国通用常用码手动预设, 如果兑换失败可尝试修改为本地码。
         * 常用城市码: 杭州330100 北京110100 上海310000 广州440100
         */
        private const val CITY_CODE = "330100"

        /**
         * 供 UI 主线程调用，只读 DataStore 缓存，不做任何网络请求。
         * 缓存由 refreshItemsFromAPI() 或 getDynamicExchangeList() 写入。
         */
        @JvmStatic
        fun getExchangeItemListForUI(): List<MemberBenefit> {
            try {
                val cacheContent = DataStore.get(CACHE_KEY_NEVERLAND_ITEMS, String::class.java)
                if (!cacheContent.isNullOrEmpty()) {
                    val cacheData = JSONObject(cacheContent)
                    val itemsArray = cacheData.optJSONArray("items")
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
                Log.error(TAG, "从 DataStore 读取健康岛缓存异常: ${e.message}")
            }
            return emptyList()
        }

        /**
         * 从 API 刷新商品列表并写入缓存（只能在后台线程调用）
         * 支付通用红包排在最前面，其余按价值降序。
         */
        @JvmStatic
        fun refreshItemsFromAPI(): List<MemberBenefit> {
            return try {
                val response = RequestManager.requestString(QUERY_METHOD, "[{}]")
                val json = JSONObject(response)
                if (!json.optBoolean("success")) {
                    Log.error(TAG, "⚠️ 获取健康岛商品列表失败: ${json.optString("message")}")
                    return emptyList()
                }
                val itemVOList = json.optJSONObject("data")?.optJSONArray("itemVOList")
                    ?: return emptyList()

                parseToBenefits(itemVOList).also { benefits ->
                    if (benefits.isNotEmpty()) saveBenefitsToCache(benefits)
                }
            } catch (e: Exception) {
                Log.error(TAG, "获取健康岛商品列表异常: ${e.message}")
                emptyList()
            }
        }

        /**
         * 将 itemVOList 解析为 MemberBenefit 列表，支付通用红包排在前面
         */
        private fun parseToBenefits(itemVOList: JSONArray): List<MemberBenefit> {
            val couponItems = mutableListOf<Pair<Double, MemberBenefit>>()  // 红包类型
            val otherItems = mutableListOf<Pair<Double, MemberBenefit>>()   // 其他类型
            for (i in 0 until itemVOList.length()) {
                val itemVO = itemVOList.getJSONObject(i)
                val itemId = itemVO.optString("itemId")
                val itemName = itemVO.optString("itemName")
                val value = itemVO.optDouble("originalPrice", 0.0)
                val cost = itemVO.optInt("couponSalePrice", 0)
                if (itemId.isEmpty() || value <= 0 || cost <= 0) continue
                val displayName = "$itemName ${value}元 [${cost}积分]"
                val benefit = MemberBenefit(itemId, displayName)
                // itemSpecialType == "coupon" 就是红包，排在前面
                if ("coupon" == itemVO.optString("itemSpecialType")) {
                    couponItems.add(value to benefit)
                } else {
                    otherItems.add(value to benefit)
                }
            }
            // 红包按价値降序 + 其他商品按价値降序
            return couponItems.sortedByDescending { it.first }.map { it.second } +
                    otherItems.sortedByDescending { it.first }.map { it.second }
        }

        /**
         * 将 MemberBenefit 列表持久化到 DataStore
         */
        private fun saveBenefitsToCache(benefits: List<MemberBenefit>) {
            try {
                val itemsArray = JSONArray()
                for (benefit in benefits) {
                    itemsArray.put(JSONObject().apply {
                        put("id", benefit.id)
                        put("name", benefit.name)
                    })
                }
                val cacheData = JSONObject().apply {
                    put("items", itemsArray)
                    put("timestamp", System.currentTimeMillis())
                }
                DataStore.put(CACHE_KEY_NEVERLAND_ITEMS, cacheData.toString())
            } catch (e: Exception) {
                Log.error(TAG, "保存健康岛缓存失败: ${e.message}")
            }
        }
    }

    // 由外部（FlashSaleModule）注入的字段
    var enableNeverLandEX: BooleanModelField? = null
    var wakeUpMinuteBefore: IntegerModelField? = null
    /** 用户在 UI 中选择要兑换的商品 itemId 集合 */
    var neverLandList: SelectModelField? = null

    override fun isConcurrentMode(): Boolean = true

    override fun getName(): String = "健康岛EX🍰"
    override fun getGroup(): ModelGroup = ModelGroup.EXCHANGE
    override fun getIcon(): String = "AntSports.png"

    /**
     * 每次执行都从 API 重新获取，避免 ExchangeItem.exchanged 状态跨天复用。
     * 优先级：用户列表选择 > 支付通用红包（默认）> 全部商品
     */
    override fun buildExchangeItems(): List<ExchangeItem> {
        return try {
            val allItems = getDynamicExchangeList()
            if (allItems.isEmpty()) {
                Log.other("$TAG 当前没有可兑换的健康岛商品")
                return emptyList()
            }

            // 用户在 UI 中有明确选择时，按其选择过滤
            val selectedIds = neverLandList?.value?.filter { it.isNotEmpty() }
            if (!selectedIds.isNullOrEmpty()) {
                val selectedItems = selectedIds.mapNotNull { id ->
                    allItems.find { it.code == id }
                }
                if (selectedItems.isNotEmpty()) {
                    Log.other("$TAG 按用户选择构建兑换项: ${selectedItems.size} 项")
                    return selectedItems
                }
                Log.other("$TAG 用户选择的商品在今日列表中不存在，回退到默认模式")
            }

            // 默认：getDynamicExchangeList 已将支付通用红包排在前面
            Log.other("$TAG 默认模式（支付通用红包优先）: ${allItems.size} 项")
            allItems
        } catch (e: Exception) {
            Log.error(TAG, "获取兑换列表失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 从 API 获取全部商品，并同步写入 DataStore 缓存供 UI 使用。
     * coupon 类型（红包）排在前面，其他类型商品排在后面，各组内按价値降序。
     */
    private fun getDynamicExchangeList(): List<ExchangeItem> {
        val response = RequestManager.requestString(QUERY_METHOD, "[{}]")
        val json = JSONObject(response)

        if (!json.optBoolean("success")) {
            throw Exception("接口返回失败: ${json.optString("message")}")
        }

        val itemVOList = json.optJSONObject("data")?.optJSONArray("itemVOList")
            ?: return emptyList()
        if (itemVOList.length() == 0) return emptyList()

        val couponItems = mutableListOf<Pair<Double, ExchangeItem>>()   // 红包类型
        val otherItems = mutableListOf<Pair<Double, ExchangeItem>>()    // 其他类型
        val couponBenefits = mutableListOf<Pair<Double, MemberBenefit>>()
        val otherBenefits = mutableListOf<Pair<Double, MemberBenefit>>()

        for (i in 0 until itemVOList.length()) {
            val itemVO = itemVOList.getJSONObject(i)
            // 获取全部商品，不过滤类型
            val itemId = itemVO.optString("itemId")
            val itemName = itemVO.optString("itemName")
            val value = itemVO.optDouble("originalPrice", 0.0)
            val cost = itemVO.optInt("couponSalePrice", 0)
            if (itemId.isEmpty() || value <= 0 || cost <= 0) continue

            val exchangeItem = ExchangeItem(itemId, value, cost, itemName)
            val displayName = "$itemName ${value}元 [${cost}积分]"
            val benefit = MemberBenefit(itemId, displayName)

            // 用 itemSpecialType 判断是否是红包（比名称匹配更准确）
            if ("coupon" == itemVO.optString("itemSpecialType")) {
                couponItems.add(value to exchangeItem)
                couponBenefits.add(value to benefit)
            } else {
                otherItems.add(value to exchangeItem)
                otherBenefits.add(value to benefit)
            }
        }

        // 红包在前（按价値降序），其他商品在后（按价値降序）
        val sortedItems = couponItems.sortedByDescending { it.first }.map { it.second } +
                otherItems.sortedByDescending { it.first }.map { it.second }

        // 缓存顺序与实际执行顺序保持一致
        val sortedBenefits = couponBenefits.sortedByDescending { it.first }.map { it.second } +
                otherBenefits.sortedByDescending { it.first }.map { it.second }
        if (sortedBenefits.isNotEmpty()) saveBenefitsToCache(sortedBenefits)

        return sortedItems
    }

    override fun tryExchange(item: ExchangeItem): Boolean {
        return try {
            item.exchangeParams = buildExchangeParams(item)
            Log.debug("预构建兑换参数: $item")
            true
        } catch (e: Exception) {
            Log.error(TAG, "构建参数失败（$item）：${e.message}")
            false
        }
    }

    private fun buildExchangeParams(item: ExchangeItem): JSONArray {
        val benefit = JSONObject().apply {
            put("benefitId", "")
            put("chInfo", CH_INFO)
            put("cityCode", CITY_CODE)    // 如兑换失败，可尝试改为本地城市码
            put("itemId", item.code)
        }
        return JSONArray().apply { put(benefit) }
    }

    override fun sendExchangeRequest(params: JSONArray, item: ExchangeItem): Boolean {
        return try {
            Log.debug("请求兑换[$item]: ${TimeUtil.getCommonDate(System.currentTimeMillis())}")
            Log.debug("请求参数: $params")
            val res = RequestManager.requestString(ORDER_METHOD, params.toString())
            val response = JSONObject(res)
            if (response.optBoolean("success")) {
                Log.other("$TAG ✅ 成功兑换: $item")
                true
            } else {
                Log.other("$TAG ❌ 兑换失败: $item")
                Log.error("$TAG ❌ 失败详情: $res")
                false
            }
        } catch (e: Exception) {
            Log.error(TAG, "兑换[$item]时发生异常: ${e.message}")
            false
        }
    }

    @SuppressLint("NewApi")
    override fun sendExchangeRequestAsync(params: JSONArray, item: ExchangeItem): Boolean {
        return sendExchangeRequest(params, item) // 逻辑完全相同，复用
    }

    override val exchangeMode: ExchangeMode get() = ExchangeMode.MULTI
    override val completedKey: String get() = CompletedKeyEnum.neverLandEX.name
    override fun getWakeUpConfigField(): IntegerModelField? = wakeUpMinuteBefore
    override fun check(): Boolean {
        // enableNeverLandEX 未启用时直接跳过，防止单独调用时缺少保护
        if (enableNeverLandEX?.value == false) return false
        return super.check()
    }

    override fun getFields(): ModelFields {
        // 字段由 FlashSaleModule 统一管理
        return ModelFields()
    }
}
