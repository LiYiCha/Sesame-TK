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
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.task.otherTask2.logisticsinteraction.baoguoRpcCall
import java.util.Calendar
import java.util.Locale

class PackageExchangeEX : BaseFlashSaleTask() {

    companion object {
        private const val TAG = "包裹兑换EX🎁"
        private const val CACHE_KEY_BAOGUO_ITEMS = "baoguo_exchange_items_cache"

        /**
         * 供 UI 主线程调用，只读 DataStore 缓存，不做任何网络请求。
         */
        @JvmStatic
        fun getExchangeItemListForUI(): List<MemberBenefit> {
            try {
                val cacheContent = DataStore.get(CACHE_KEY_BAOGUO_ITEMS, String::class.java)
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
                Log.error(TAG, "从 DataStore 读取包裹兑换缓存异常: ${e.message}")
            }
            return listOf(MemberBenefit("", "暂无数据 (请确保已开启开关并进入支付宝以自动加载列表)"))
        }

        /**
         * 从 API 刷新商品列表并写入缓存（只能在后台线程调用）
         */
        @JvmStatic
        fun refreshItemsFromAPI(): List<MemberBenefit> {
            return try {
                val response = baoguoRpcCall.listGoods()
                val json = JSONObject(response)
                if (!json.optBoolean("success")) {
                    Log.error(TAG, "⚠️ 获取包裹兑换商品列表失败: ${json.optString("errorCode")}")
                    return emptyList()
                }
                val itemArray = json.optJSONArray("data") ?: return emptyList()
                val benefits = mutableListOf<MemberBenefit>()
                val itemsList = JSONArray()

                for (i in 0 until itemArray.length()) {
                    val itemObj = itemArray.getJSONObject(i)
                    val itemId = itemObj.optString("itemId")
                    val itemTitle = itemObj.optString("itemTitle")
                    val salePrice = itemObj.optInt("salePrice", 0)
                    if (itemId.isEmpty()) continue

                    val displayName = "$itemTitle [${salePrice}积分]"
                    benefits.add(MemberBenefit(itemId, displayName))
                    itemsList.put(JSONObject().apply {
                        put("id", itemId)
                        put("name", displayName)
                        put("salePrice", salePrice)
                    })
                }

                if (benefits.isNotEmpty()) {
                    val cacheData = JSONObject().apply {
                        put("items", itemsList)
                        put("timestamp", System.currentTimeMillis())
                    }
                    DataStore.put(CACHE_KEY_BAOGUO_ITEMS, cacheData.toString())
                }
                benefits
            } catch (e: Exception) {
                Log.error(TAG, "获取包裹商品列表异常: ${e.message}")
                emptyList()
            }
        }
    }

    // 由外部注入的字段
    var enablePackageExchange: BooleanModelField? = null
    var wakeUpMinuteBefore: IntegerModelField? = null
    var packageExchangeList: SelectModelField? = null

    override fun isConcurrentMode(): Boolean = true

    override fun getName(): String = "包裹兑换EX🎁"
    override fun getGroup(): ModelGroup = ModelGroup.EXCHANGE
    override fun getIcon(): String = "AntSports.png"

    override fun getWakeUpConfigField(): IntegerModelField? = wakeUpMinuteBefore

    override fun buildExchangeItems(): List<ExchangeItem> {
        try {
            // 1. 获取选中的商品ID
            val selectedIds = packageExchangeList?.value?.filter { it.isNotEmpty() }
            if (selectedIds.isNullOrEmpty()) {
                Log.other("$TAG ❌ 未选择任何兑换商品，任务终止")
                return emptyList()
            }

            // 2. 查询积分
            val accountRes = baoguoRpcCall.queryAccount()
            if (accountRes.isEmpty()) {
                Log.error(TAG, "查询账户积分失败，终止包裹兑换")
                return emptyList()
            }
            val accountObj = JSONObject(accountRes)
            if (!accountObj.optBoolean("success", false)) {
                Log.error(TAG, "查询账户积分接口返回错误: ${accountObj.optString("errorCode")}")
                return emptyList()
            }
            val balanceStr = accountObj.optJSONObject("data")?.optString("balance", "0") ?: "0"
            val balance = balanceStr.toIntOrNull() ?: 0
            Log.other("$TAG 当前账户积分余额: $balance")

            // 3. 从 API 拉取最新的全部商品（拿到最新的 salePrice 积分售价）
            val listGoodsRes = baoguoRpcCall.listGoods()
            val listGoodsObj = JSONObject(listGoodsRes)
            if (!listGoodsObj.optBoolean("success", false)) {
                Log.error(TAG, "拉取包裹兑换商品列表失败，包裹兑换终止")
                return emptyList()
            }
            val itemArray = listGoodsObj.optJSONArray("data") ?: JSONArray()
            val apiItemsMap = mutableMapOf<String, Pair<String, Int>>() // itemId -> (title, salePrice)
            for (i in 0 until itemArray.length()) {
                val itemObj = itemArray.getJSONObject(i)
                val itemId = itemObj.optString("itemId")
                val itemTitle = itemObj.optString("itemTitle")
                val salePrice = itemObj.optInt("salePrice", 0)
                if (itemId.isNotEmpty()) {
                    apiItemsMap[itemId] = Pair(itemTitle, salePrice)
                }
            }

            // 4. 筛选满足积分的选中商品
            val validItems = mutableListOf<ExchangeItem>()
            for (selectedId in selectedIds) {
                val itemInfo = apiItemsMap[selectedId]
                if (itemInfo != null) {
                    val title = itemInfo.first
                    val salePrice = itemInfo.second
                    if (balance >= salePrice) {
                        // 积分足够，加入待兑换项
                        validItems.add(ExchangeItem(selectedId, 0.0, salePrice, title))
                        Log.other("$TAG ✓ 商品 [${title}] 满足积分要求（当前余额: $balance, 所需: $salePrice）")
                    } else {
                        Log.other("$TAG ✗ 商品 [${title}] 积分不足（当前余额: $balance, 所需: $salePrice），跳过")
                    }
                } else {
                    Log.other("$TAG ✗ 选中的商品 ID [$selectedId] 在今日商品列表中未找到，可能已被下架")
                }
            }

            if (validItems.isEmpty()) {
                Log.other("$TAG 没有满足积分要求的有效兑换商品，包裹兑换流程结束")
                return emptyList()
            }

            return validItems
        } catch (e: Exception) {
            Log.error(TAG, "构建包裹兑换列表异常: ${e.message}")
            return emptyList()
        }
    }

    override fun tryExchange(item: ExchangeItem): Boolean {
        return try {
            // queryGoodsDetail是单个物品情况
            val detailRes = baoguoRpcCall.queryDetail(item.code)
            if (detailRes.isEmpty()) {
                Log.error(TAG, "获取商品详情失败，itemId: ${item.code}")
                return false
            }
            val detailObj = JSONObject(detailRes)
            if (!detailObj.optBoolean("success", false)) {
                Log.error(TAG, "商品详情接口返回错误: ${detailObj.optString("errorCode")} itemId: ${item.code}")
                return false
            }
            val dataObj = detailObj.optJSONObject("data")
            if (dataObj == null) {
                Log.error(TAG, "商品详情 data 为空，itemId: ${item.code}")
                return false
            }
            val itemType = dataObj.optString("itemType", "DRAW_CAMP_PRIZE")

            // 拼装兑换参数
            val params = JSONArray().apply {
                put(JSONObject().apply {
                    put("itemId", item.code)
                    put("itemType", itemType)
                })
            }
            item.exchangeParams = params
            Log.other("$TAG ✅ 成功预构建商品 [${item.name}] 的兑换参数")
            true
        } catch (e: Exception) {
            Log.error(TAG, "构建包裹兑换参数失败: ${e.message}")
            false
        }
    }

    override fun sendExchangeRequest(params: JSONArray, item: ExchangeItem): Boolean {
        return try {
            Log.debug("包裹兑换发送[$item]: ${TimeUtil.getCommonDate(System.currentTimeMillis())}")
            val itemId = item.code
            val itemType = params.getJSONObject(0).optString("itemType", "DRAW_CAMP_PRIZE")
            
            // createRedemption是执行兑换的情况
            val method = "alipay.mobile.logisticinteraction.benefit.createRedemption"
            val rpcParams = "[{\"itemId\":\"$itemId\",\"itemType\":\"$itemType\"}]"
            val res = RequestManager.requestString(method, rpcParams)
            val response = JSONObject(res)

            if (response.optBoolean("success", false)) {
                Log.other("$TAG ✅ 成功兑换: ${item.name}")
                true
            } else {
                Log.other("$TAG ❌ 兑换失败: ${item.name} | ${response.optString("errorCode")}")
                false
            }
        } catch (e: Exception) {
            Log.error(TAG, "发送包裹兑换请求异常: ${e.message}")
            false
        }
    }

    @SuppressLint("NewApi")
    override fun sendExchangeRequestAsync(params: JSONArray, item: ExchangeItem): Boolean {
        return sendExchangeRequest(params, item) // 并发异步流程也使用相同的发送逻辑
    }

    override val exchangeMode: ExchangeMode get() = ExchangeMode.SINGLE

    override val completedKey: String
        get() {
            val serverTime = System.currentTimeMillis()
            val targetTime = calculateTargetTime(serverTime)
            val dateStr = TimeUtil.getCommonDate(targetTime).split(" ")[0].replace("-", "") // yyyyMMdd
            return "packageExchangeEX_${dateStr}_${getTargetHour()}"
        }

    override fun getTargetHour(): Long {
        val targetHours = listOf(0, 10, 18)
        val calendar = Calendar.getInstance(Locale.CHINA)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        // 找出今日未过且有 buffer 的下一个兑换小时整点
        for (hour in targetHours) {
            if (hour > currentHour || (hour == currentHour && currentMinute < 1)) {
                return hour.toLong()
            }
        }
        // 如果今日都已经过去，下一个肯定是明日的0点（即 0点）
        return 0L
    }

    override fun check(): Boolean {
        if (enablePackageExchange?.value == false) return false
        return super.check()
    }

    override fun getFields(): ModelFields {
        return ModelFields() // 字段在 FlashSaleModule 中统一添加与管理
    }
}
