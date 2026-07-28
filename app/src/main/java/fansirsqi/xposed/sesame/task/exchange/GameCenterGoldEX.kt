package fansirsqi.xposed.sesame.task.exchange

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONObject
import fansirsqi.xposed.sesame.entity.MemberBenefit
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log

class GameCenterGoldEX : BaseFlashSaleTask() {

    companion object {
        private const val TAG = "游戏中心金币EX🎮"
        private const val CACHE_KEY_GAMECENTER_GOLD_ITEMS = "gamecenter_gold_exchange_items_cache"

        private const val QUERY_METHOD = "com.alipay.gamecenteruprod.biz.rpc.p2e.queryGoldExgPrizePage"
        private const val EXCHANGE_METHOD = "com.alipay.gamecenteruprod.biz.rpc.p2e.doGoldMallPrizeExchange"
        private const val QUERY_PARAMS = "[{\"__git\":\"9e159d58cce04c13a\"}]"
        private const val ACTIVITY_ID = "CY26_JULY_WALK_GRID"

        /**
         * 供 UI 主线程调用，只读 DataStore 缓存，不做任何网络请求。
         */
        @JvmStatic
        fun getExchangeItemListForUI(): List<MemberBenefit> {
            try {
                val cacheContent = DataStore.get(CACHE_KEY_GAMECENTER_GOLD_ITEMS, String::class.java)
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
                Log.error(TAG, "从 DataStore 读取游戏中心金币缓存异常: ${e.message}")
            }
            return listOf(MemberBenefit("", "暂无数据 (请确保已开启开关并自动加载列表)"))
        }

        /**
         * 从 API 刷新游戏中心金币兑换商品列表并写入缓存（只能在后台线程调用）
         */
        @JvmStatic
        fun refreshItemsFromAPI(): List<MemberBenefit> {
            return try {
                val response = RequestManager.requestString(QUERY_METHOD, QUERY_PARAMS)
                if (response.isNullOrEmpty()) {
                    Log.error(TAG, "⚠️ 获取游戏中心金币商品列表返回为空")
                    return emptyList()
                }
                val json = JSONObject(response)
                if (!json.optBoolean("success", false)) {
                    Log.error(TAG, "⚠️ 获取游戏中心金币商品列表失败: ${json.optString("errorMsg")}")
                    return emptyList()
                }
                val dataObj = json.optJSONObject("data") ?: return emptyList()
                val mallModule = dataObj.optJSONObject("mallModule") ?: return emptyList()
                val prizesArray = mallModule.optJSONArray("prizes") ?: return emptyList()

                val benefits = mutableListOf<MemberBenefit>()
                val itemsList = JSONArray()

                for (i in 0 until prizesArray.length()) {
                    val prize = prizesArray.getJSONObject(i)
                    val prizeId = prize.optString("prizeId")
                    val name = prize.optString("name")
                    val consumeGoldAmount = prize.optInt("consumeGoldAmount", 0)
                    val sendSign = prize.optString("sendSign")
                    val outBizNo = prize.optString("outBizNo")
                    val prizeType = prize.optString("prizeType")
                    val stockText = prize.optString("stockText", "")

                    if (prizeId.isEmpty()) continue

                    val isLafite = "LAFITE_PRIZE" == prizeType
                    val suffix = if (isLafite) " [需0.01元支付下单]" else ""
                    val stockDesc = if (stockText.isNotEmpty()) " ($stockText)" else ""
                    val displayName = "$name [${consumeGoldAmount}金币]$stockDesc$suffix"

                    benefits.add(MemberBenefit(prizeId, displayName))
                    itemsList.put(JSONObject().apply {
                        put("id", prizeId)
                        put("name", displayName)
                        put("prizeName", name)
                        put("consumeGoldAmount", consumeGoldAmount)
                        put("sendSign", sendSign)
                        put("outBizNo", outBizNo)
                        put("prizeType", prizeType)
                        put("stockText", stockText)
                    })
                }

                if (benefits.isNotEmpty()) {
                    val cacheData = JSONObject().apply {
                        put("items", itemsList)
                        put("timestamp", System.currentTimeMillis())
                    }
                    DataStore.put(CACHE_KEY_GAMECENTER_GOLD_ITEMS, cacheData.toString())
                }
                benefits
            } catch (e: Exception) {
                Log.error(TAG, "获取游戏中心金币商品列表异常: ${e.message}")
                emptyList()
            }
        }
    }

    // 由外部注入的配置字段
    var enableGameCenterGold: BooleanModelField? = null
    var wakeUpMinuteBefore: IntegerModelField? = null
    var gameCenterGoldList: SelectModelField? = null
    var targetHourField: IntegerModelField? = null

    override fun isConcurrentMode(): Boolean = true

    override fun getName(): String = "游戏中心金币EX🎮"
    override fun getGroup(): ModelGroup = ModelGroup.EXCHANGE
    override fun getIcon(): String = "AntSports.png"

    override fun getWakeUpConfigField(): IntegerModelField? = wakeUpMinuteBefore

    /** 目标开抢整点：读取 UI 设置的整点（0-23点，默认20点） */
    override fun getTargetHour(): Long {
        val hour = targetHourField?.value ?: 20
        return hour.toLong()
    }

    override val exchangeMode: ExchangeMode?
        get() = ExchangeMode.MULTI

    override val completedKey: String
        get() = "GameCenterGoldEX"

    override fun buildExchangeItems(): List<ExchangeItem> {
        try {
            // 1. 获取选中的商品ID
            val selectedIds = gameCenterGoldList?.value?.filter { it.isNotEmpty() }
            if (selectedIds.isNullOrEmpty()) {
                Log.other("$TAG ❌ 未选择任何秒杀/兑换商品，任务终止")
                return emptyList()
            }

            // 2. 从 API 重新拉取最新商品与加签 sendSign
            val response = RequestManager.requestString(QUERY_METHOD, QUERY_PARAMS)
            if (response.isNullOrEmpty()) {
                Log.error(TAG, "拉取游戏中心金币商品列表失败，任务终止")
                return emptyList()
            }
            val json = JSONObject(response)
            if (!json.optBoolean("success", false)) {
                Log.error(TAG, "拉取游戏中心金币商品列表返回错误: ${json.optString("errorMsg")}")
                return emptyList()
            }
            val dataObj = json.optJSONObject("data") ?: return emptyList()
            val mallModule = dataObj.optJSONObject("mallModule") ?: return emptyList()
            val prizesArray = mallModule.optJSONArray("prizes") ?: JSONArray()

            val apiItemsMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until prizesArray.length()) {
                val prize = prizesArray.getJSONObject(i)
                val prizeId = prize.optString("prizeId")
                if (prizeId.isNotEmpty()) {
                    apiItemsMap[prizeId] = prize
                }
            }

            // 3. 构建秒杀条目 (按照 UI 设置的整点开抢)
            val resultList = mutableListOf<ExchangeItem>()
            val targetHour = getTargetHour()
            for (prizeId in selectedIds) {
                val prizeObj = apiItemsMap[prizeId] ?: continue
                val prizeName = prizeObj.optString("name")
                val consumeGoldAmount = prizeObj.optInt("consumeGoldAmount", 0)

                val item = ExchangeItem(prizeId, 0.0, consumeGoldAmount, prizeName)
                resultList.add(item)
                Log.other("$TAG 已成功构建 ${targetHour}:00 秒杀条目: [$prizeName]")
            }
            return resultList
        } catch (e: Exception) {
            Log.error(TAG, "构建秒杀条目失败: ${e.message}")
            return emptyList()
        }
    }

    override fun tryExchange(item: ExchangeItem): Boolean {
        return try {
            val response = RequestManager.requestString(QUERY_METHOD, QUERY_PARAMS)
            if (response.isNullOrEmpty()) {
                Log.error(TAG, "获取商品最新加签失败，prizeId: ${item.code}")
                return false
            }
            val json = JSONObject(response)
            val dataObj = json.optJSONObject("data") ?: return false
            val mallModule = dataObj.optJSONObject("mallModule") ?: return false
            val prizesArray = mallModule.optJSONArray("prizes") ?: return false

            var prizeObj: JSONObject? = null
            for (i in 0 until prizesArray.length()) {
                val prize = prizesArray.getJSONObject(i)
                if (prize.optString("prizeId") == item.code) {
                    prizeObj = prize
                    break
                }
            }
            if (prizeObj == null) {
                Log.error(TAG, "未在最新商品列表中查找到 prizeId: ${item.code}")
                return false
            }

            val sendSign = prizeObj.optString("sendSign")
            val outBizNo = prizeObj.optString("outBizNo")
            val prizeType = prizeObj.optString("prizeType")

            val requestParamObj = JSONObject().apply {
                put("__git", "9e159d58cce04c13a")
                put("activityId", ACTIVITY_ID)
                put("prizeId", item.code)
                put("sendSign", sendSign)
                put("outBizNo", outBizNo)
                put("prizeType", prizeType) // 附带类型供后续判定
            }
            val paramsArray = JSONArray().put(requestParamObj)
            item.exchangeParams = paramsArray
            Log.other("$TAG ✅ 成功构建 [${item.name}] 最新秒杀参数")
            true
        } catch (e: Exception) {
            Log.error(TAG, "构建秒杀参数异常 [${item.name}]: ${e.message}")
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
            val resJson = JSONObject(responseStr)
            if (resJson.optBoolean("success", false)) {
                val reqObj = params.optJSONObject(0)
                val prizeType = reqObj?.optString("prizeType", "") ?: ""
                if ("LAFITE_PRIZE" == prizeType) {
                    Log.other("$TAG 🎉 成功秒杀到实物下单资格 [${item.name}]！请在7天内进入支付宝兑换记录完成 0.01 元支付包邮寄送！")
                } else {
                    Log.other("$TAG 🎉 成功秒杀/兑换到奖品 [${item.name}]！")
                }
                true
            } else {
                val errorMsg = resJson.optString("errorMsg", resJson.optString("desc", "未知错误"))
                Log.other("$TAG ⚠️ 兑换 [${item.name}] 失败: $errorMsg")
                false
            }
        } catch (e: Exception) {
            Log.error(TAG, "兑换请求异常 [${item.name}]: ${e.message}")
            false
        }
    }

    override fun sendExchangeRequestAsync(params: JSONArray, item: ExchangeItem): Boolean {
        return sendExchangeRequest(params, item)
    }
}
