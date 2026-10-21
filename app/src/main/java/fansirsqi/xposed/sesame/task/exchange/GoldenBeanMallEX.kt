package fansirsqi.xposed.sesame.task.exchange

import fansirsqi.xposed.sesame.entity.MemberBenefit
import fansirsqi.xposed.sesame.entity.MapperEntity
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/**
 * 金豆商城秒杀
 *
 * 按所选商品在开抢整点用金豆罐兑换农场金豆商城商品；
 * 实付/外部下单等需付款的商品在构建兑换项时即被过滤，库存与余量交由服务端裁决。
 */
class GoldenBeanMallEX : BaseFlashSaleTask() {

    companion object {
        private const val TAG = "金豆夺宝·农场🎡"
        private const val CACHE_KEY_GOLDENBEAN_MALL_ITEMS = "goldenbean_mall_exchange_items_cache"
        private const val BIZ_TYPE = "MASTER"
        private const val SOURCE = "babafarm"
        private const val VERSION = "20260901.01"
        private const val SCENE_CODE = "ANTORCHARD_JINDOU_MALL"
        private const val PAGE_SIZE = 20
        private const val MAX_PAGES = 20
        private const val EXCHANGE_METHOD = "com.alipay.antcommonweal.exchange.h5.exchangeBenefit"
        private const val DARWIN_SCENE_LIST =
            "[\"indexLayoutTwo\",\"taskFlowHandGuide\",\"indexLoadingOptimization\"]"

        private class MallRow(id: String, name: String) : MapperEntity() {
            init {
                this.id = id
                this.name = name
            }
        }

        private data class MallOption(val row: MallRow, val spu: JSONObject, val sku: JSONObject)

        /**
         * 供 UI 主线程调用，只读 DataStore 缓存，不做任何网络请求。
         */
        @JvmStatic
        fun getExchangeItemListForUI(): List<MemberBenefit> {
            try {
                val cacheContent = DataStore.get(CACHE_KEY_GOLDENBEAN_MALL_ITEMS, String::class.java)
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
                Log.error(TAG, "读取金豆商城商品缓存异常: ${e.message}")
            }
            return listOf(MemberBenefit("", "暂无数据 (请确保已开启开关并自动加载列表)"))
        }

        /**
         * 从 API 刷新金豆商城商品列表并写入缓存（只能在后台线程调用）
         */
        @JvmStatic
        fun refreshItemsFromAPI(): List<MemberBenefit> {
            return try {
                val catalog = loadCatalog()
                if (catalog.isEmpty()) {
                    Log.error(TAG, "⚠️ 获取金豆商城商品目录为空")
                    return emptyList()
                }
                val benefits = mutableListOf<MemberBenefit>()
                val itemsList = JSONArray()
                for ((id, option) in catalog) {
                    benefits.add(MemberBenefit(id, option.row.name))
                    itemsList.put(JSONObject().apply {
                        put("id", id)
                        put("name", option.row.name)
                    })
                }
                val cacheData = JSONObject().apply {
                    put("items", itemsList)
                    put("timestamp", System.currentTimeMillis())
                }
                DataStore.put(CACHE_KEY_GOLDENBEAN_MALL_ITEMS, cacheData.toString())
                benefits
            } catch (e: Exception) {
                Log.error(TAG, "获取金豆商城商品列表异常: ${e.message}")
                emptyList()
            }
        }

        /** 分页拉取全量商品目录 */
        private fun loadCatalog(): Map<String, MallOption> {
            val options = linkedMapOf<String, MallOption>()
            var startIndex = 0
            var pages = 0
            while (pages < MAX_PAGES) {
                pages++
                val response = itemList(startIndex)
                if (!isSuccess(response)) {
                    Log.error(
                        TAG,
                        "金豆商城目录查询失败 startIndex=$startIndex code=${failureCode(response)} desc=${failureDesc(response)}",
                    )
                    break
                }
                val items = response.optJSONArray("itemInfoVOList") ?: break
                for (i in 0 until items.length()) {
                    val spu = items.optJSONObject(i) ?: continue
                    val skus = spu.optJSONArray("skuModelList") ?: continue
                    for (j in 0 until skus.length()) {
                        val sku = skus.optJSONObject(j) ?: continue
                        val spuId = spu.optString("spuId")
                        val skuId = sku.optString("skuId")
                        if (spuId.isBlank() || skuId.isBlank()) continue
                        options["$spuId:$skuId"] = MallOption(buildRow(spu, sku), spu, sku)
                    }
                }
                if (!response.optBoolean("hasMore", false)) break
                val next = response.optInt("nextStartIndex", startIndex)
                if (next <= startIndex) break
                startIndex = next
            }
            return options
        }

        private fun itemList(startIndex: Int): JSONObject =
            request(
                "com.alipay.antiep.itemList",
                JSONObject().apply {
                    put("bizType", BIZ_TYPE)
                    put("pageSize", PAGE_SIZE)
                    put("requestType", "RPC")
                    put("sceneCode", SCENE_CODE)
                    put("source", BIZ_TYPE)
                    put("startIndex", startIndex)
                    put("version", VERSION)
                },
            )

        private fun buildRow(spu: JSONObject, sku: JSONObject): MallRow {
            val title = listOf(spu.optString("spuName"), sku.optString("skuName"))
                .filter { it.isNotBlank() }
                .joinToString(" / ")
            val cost = sku.optJSONObject("price")?.optLong("cent", -1L) ?: -1L
            val stock = spu.optInt("remainStockCounts", -1)
            val dayLeft = sku.optInt("userDayLeftAmount", -1)
            val info = mutableListOf<String>()
            if (cost >= 0) info += "${BigDecimal.valueOf(cost, 2).stripTrailingZeros().toPlainString()}金豆"
            if (stock >= 0) info += "库存$stock"
            if (dayLeft >= 0) info += "今日余${dayLeft}次"
            val name = if (info.isEmpty()) title else "$title（${info.joinToString("，")}）"
            return MallRow("${spu.optString("spuId")}:${sku.optString("skuId")}", name)
        }

        private fun request(method: String, requestData: JSONObject): JSONObject = try {
            JSONObject(RequestManager.requestString(method, JSONArray().put(requestData).toString()))
        } catch (e: Exception) {
            JSONObject().put("clientError", e.toString())
        }

        private fun isSuccess(response: JSONObject): Boolean =
            response.optBoolean("success", false) ||
                response.optString("resultCode") in setOf("100", "SUCCESS") ||
                response.optString("code") == "100000000"

        private fun failureCode(response: JSONObject): String =
            response.optString("resultCode").ifBlank { response.optString("code") }.ifBlank { "EMPTY" }

        private fun failureDesc(response: JSONObject): String =
            response.optString("resultDesc")
                .ifBlank { response.optString("desc") }
                .ifBlank { response.optString("clientError") }
    }

    // 由 FlashSaleModule 注入的配置字段
    var enableGoldenBeanMall: BooleanModelField? = null
    var goldenBeanMallList: SelectModelField? = null
    var targetHourField: IntegerModelField? = null

    override fun getName(): String = "金豆商城秒杀🎡"
    override fun getGroup(): ModelGroup = ModelGroup.EXCHANGE
    override fun getIcon(): String = "AntSports.png"

    /** 目标开抢整点：读取 UI 设置的整点（0-23点，默认0点） */
    override fun getTargetHour(): Long {
        val hour = targetHourField?.value ?: 0
        return hour.toLong()
    }

    override val exchangeMode: ExchangeMode
        get() = ExchangeMode.MULTI

    override val completedKey: String
        get() = "GoldenBeanMallEX"

    override fun buildExchangeItems(): List<ExchangeItem> {
        try {
            // 1. 获取选中的商品ID
            val selectedIds = goldenBeanMallList?.value?.filter { it.isNotEmpty() }
            if (selectedIds.isNullOrEmpty()) {
                Log.other("$TAG ❌ 未选择任何秒杀/兑换商品，任务终止")
                return emptyList()
            }

            // 2. 拉取最新商品目录
            val catalog = loadCatalog()
            if (catalog.isEmpty()) {
                Log.error(TAG, "金豆商城目录为空，任务终止")
                return emptyList()
            }

            // 3. 构建兑换条目，过滤需付款/外部下单的商品
            val resultList = mutableListOf<ExchangeItem>()
            for (id in selectedIds) {
                val option = catalog[id]
                if (option == null) {
                    Log.error(TAG, "金豆商城所选规格已不在当前目录: $id")
                    continue
                }
                val cost = option.sku.optJSONObject("price")?.optLong("cent", -1L) ?: -1L
                val reason = staticUnavailableReason(option, cost)
                if (reason != null) {
                    Log.other(TAG, "金豆商城[${option.row.name}]跳过：$reason")
                    continue
                }
                resultList.add(ExchangeItem(id, 0.0, cost.toInt(), option.row.name))
            }
            return resultList
        } catch (e: Exception) {
            Log.error(TAG, "构建金豆商城兑换条目失败: ${e.message}")
            return emptyList()
        }
    }

    /** 商品固有属性判定：需实付、外部下单或权益方式不明的商品不自动兑换 */
    private fun staticUnavailableReason(option: MallOption, cost: Long): String? {
        val spu = option.spu
        val sku = option.sku
        val money = sku.optJSONObject("moneyPrice")
        val cash = if (!sku.has("moneyPrice") || sku.isNull("moneyPrice")) 0L else money?.optLong("cent", -1L) ?: -1L
        val externalOrder = spu.optString("outSpuType").isNotBlank() || spu.optString("outSpuId").isNotBlank() ||
            sku.optString("outSpuOrderLink").isNotBlank() || sku.optString("outSkuId").isNotBlank()
        return when {
            cost < 0L -> "未取得有效金豆罐价格"
            cash < 0L -> "实付金额未明确"
            cash > 0L || externalOrder -> "需要实付或外部下单"
            sku.optString("rightsConfigId").isBlank() -> "未明确内部权益领取方式"
            else -> null
        }
    }

    override fun tryExchange(item: ExchangeItem): Boolean {
        return try {
            val catalog = loadCatalog()
            val option = catalog[item.code]
            if (option == null) {
                Log.error(TAG, "未在最新目录中查找到规格: ${item.code}")
                return false
            }
            // requestId 预生成一次，deadline 内重试复用，服务端幂等防重复下单
            item.exchangeParams = JSONArray().put(JSONObject().apply {
                put("bizType", BIZ_TYPE)
                put("requestId", UUID.randomUUID().toString())
                put("sceneCode", SCENE_CODE)
                put("skuId", option.sku.optString("skuId"))
                put("source", BIZ_TYPE)
                put("spuId", option.spu.optString("spuId"))
                put("version", VERSION)
            })
            Log.other("$TAG ✅ 成功构建 [${item.name}] 兑换参数")
            true
        } catch (e: Exception) {
            Log.error(TAG, "构建兑换参数异常 [${item.name}]: ${e.message}")
            false
        }
    }

    override fun sendExchangeRequest(params: JSONArray, item: ExchangeItem): Boolean {
        return try {
            val responseStr = RequestManager.requestString(EXCHANGE_METHOD, params.toString())
            if (responseStr.isNullOrEmpty()) {
                Log.other("$TAG ❌ 兑换 [${item.name}] 返回为空")
                return false
            }
            val response = JSONObject(responseStr)
            if (isSuccess(response)) {
                Log.other("$TAG 🎉 金豆商城兑换成功[${item.name}][订单${response.optString("orderNo")}]")
                true
            } else {
                Log.error(
                    TAG,
                    "金豆商城兑换未成功[${item.name}] code=${failureCode(response)} desc=${failureDesc(response)}",
                )
                false
            }
        } catch (e: Exception) {
            Log.error(TAG, "金豆商城兑换请求异常[${item.name}]: ${e.message}")
            false
        }
    }
}
