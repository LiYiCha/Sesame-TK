package fansirsqi.xposed.sesame.task.exchange

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONObject
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import java.util.Calendar

class MerchantEX : BaseFlashSaleTask() {

    companion object {
        private const val TAG = "商家十积分秒杀👑"

        // 秒杀有效时长（30秒）
        private const val SECKILL_VALID_DURATION = 30000L
    }

    // 配置字段（由 FlashSaleModule 注入）
    var enableMerchantEX: BooleanModelField? = null // 是否启用该任务
    var wakeUpMinuteBefore: IntegerModelField? = null // 唤醒提前时间

    override fun isConcurrentMode(): Boolean {
        return true // 默认启用并发模式
    }

    override fun getName(): String {
        return "商家十积分秒杀👑"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.EXCHANGE
    }

    override fun getIcon(): String {
        return "AntSports.png"
    }

    override fun buildExchangeItems(): List<ExchangeItem> {
        return try {
            val items = mutableListOf<ExchangeItem>()

            // 请求查询兑换列表
            val queryResult = RequestManager.requestString(
                "alipay.mrchservbase.mrchpoint.ttms.query",
                "[{\"compId\": \"MRCH_POINT_TTMS_QUERY\",\"extInfo\": {\"seckillVersion\": \"6\"} }]"
            )
            val queryJson = JSONObject(queryResult)

            val productCode = JsonUtil.getValueByPath(queryJson, "data.productInfo.productCode")
            if (productCode.isEmpty()) return emptyList()

            val detailResult = RequestManager.requestString(
                "alipay.mrchservbase.mrchpoint.ttms.page.detail",
                "[{\"compId\": \"MRCH_POINT_TTMS_PAGE_DETAIL\",\"extInfo\": {\"channelSource\": \"zcjMall\",\"filterCondition\": {\"lottery\": \"Y\",\"seckill\": \"Y\"},\"productCode\":\"$productCode\",\"seckillPushRoundInstanceId\":\"\"}}]"
            )
            val detailJson = JSONObject(detailResult)

            val seckillData = detailJson.getJSONObject("data").getJSONObject("seckill")
            val itemInfoArray = seckillData.getJSONArray("itemInfo")
            val roundInfoArray = seckillData.getJSONArray("seckillingRoundInfo")

            // 构建索引 Map 以提高查找性能
            val itemIndex = mutableMapOf<String, JSONObject>()
            for (k in 0 until itemInfoArray.length()) {
                val item = itemInfoArray.getJSONObject(k)
                val key = "${item.optString("channelItemCode")}_${item.optString("benefitItemCode")}"
                itemIndex[key] = item
            }

            for (i in 0 until roundInfoArray.length()) {
                val roundInfo = roundInfoArray.getJSONObject(i)
                val startTime = roundInfo.getLong("roundStartTime")
                val nowTime = roundInfo.getLong("rightNow")
                val selectedFlag = roundInfo.optInt("selectedFlag", 0)

                if (selectedFlag == 1 &&
                    roundInfo.getLong("roundEndTime") >= nowTime &&
                    nowTime - startTime <= SECKILL_VALID_DURATION) {

                    val roundItemInfo = roundInfo.getJSONArray("roundItemInfo")
                    for (j in roundItemInfo.length() - 1 downTo 0) {
                        val item = roundItemInfo.getJSONObject(j)
                        val roundInstanceId = item.getString("roundInstanceId")
                        val channelItemCode = item.getString("channelItemCode")
                        val benefitItemCode = item.getString("benefitItemCode")

                        val itemName = findItemName(itemIndex, channelItemCode, benefitItemCode)
                        val pointAmount = findPointAmount(itemIndex, channelItemCode, benefitItemCode)

                        items.add(SeckillExchangeItem(channelItemCode, itemName, pointAmount, roundInstanceId, startTime))
                    }
                }
            }

            items
        } catch (e: Exception) {
            Log.error(TAG, "构建兑换项失败：${e.message}")
            emptyList()
        }
    }

    private fun findItemName(itemIndex: Map<String, JSONObject>, channelItemCode: String, benefitItemCode: String): String {
        val key = "${channelItemCode}_$benefitItemCode"
        val item = itemIndex[key]
        return item?.optString("itemName") ?: "未知兑换名"
    }

    private fun findPointAmount(itemIndex: Map<String, JSONObject>, channelItemCode: String, benefitItemCode: String): Int {
        val key = "${channelItemCode}_$benefitItemCode"
        val item = itemIndex[key]
        return item?.optInt("pointAmount", 10) ?: 0
    }

    override fun tryExchange(item: ExchangeItem): Boolean {
        return try {
            item.exchangeParams = buildExchangeParams(item as SeckillExchangeItem)
            true
        } catch (e: Exception) {
            Log.error(TAG, "参数构建失败：${e.message}")
            false
        }
    }

    private fun buildExchangeParams(item: SeckillExchangeItem): JSONArray {
        val extInfo = JSONObject().apply {
            put("channelSource", "zcjMall")
            put("exchangeStartTime", "")
            put("itemCode", item.channelItemCode)
            put("moneyAmount", "0")
            put("pointAmount", "10")
            put("roundInstanceId", item.roundInstanceId)
        }

        val request = JSONObject().apply {
            put("compId", "MRCH_POINT_ITEM_SECKILL")
            put("extInfo", extInfo)
        }

        return JSONArray().apply {
            put(request)
        }
    }

    override fun sendExchangeRequest(params: JSONArray, baseItem: ExchangeItem): Boolean {
        val item = baseItem as SeckillExchangeItem
        return try {
            Log.debug("[${item.displayName}]请求:${TimeUtil.getCommonDate(System.currentTimeMillis())}")
            val res = RequestManager.requestString("alipay.mrchservbase.mrchpoint.item.seckill", params.toString())
            val response = JSONObject(res)

            val result = JsonUtil.getValueByPath(response, "data.seckillingResult.result")
            if ("FAILED" == result) {
                Log.other("$TAG❌ 秒杀失败: ${item.displayName}")
                false
            } else {
                Log.other("$TAG✅ 秒杀成功: ${item.displayName}")
                true
            }
        } catch (e: Exception) {
            Log.error(TAG, "兑换[${item.displayName}]时发生异常:${e.message}")
            false
        }
    }

    @SuppressLint("NewApi")
    override fun sendExchangeRequestAsync(params: JSONArray, baseItem: ExchangeItem): Boolean {
        val item = baseItem as SeckillExchangeItem
        return try {
            Log.debug("[${item.displayName}]请求:${TimeUtil.getCommonDate(System.currentTimeMillis())}")
            val res = RequestManager.requestString("alipay.mrchservbase.mrchpoint.item.seckill", params.toString())
            val response = JSONObject(res)

            val result = JsonUtil.getValueByPath(response, "data.seckillingResult.result")
            if ("FAILED" == result) {
                Log.other("$TAG❌ 秒杀失败: ${item.displayName}")
                false
            } else {
                Log.other("$TAG✅ 秒杀成功: ${item.displayName}")
                true
            }
        } catch (e: Exception) {
            Log.error(TAG, "兑换[${item.displayName}]时发生异常:${e.message}")
            false
        }
    }

    override val completedKey: String
        get() = "MerchantEX"

    override fun getWakeUpConfigField(): IntegerModelField? {
        return wakeUpMinuteBefore
    }

    override val exchangeMode: ExchangeMode
        get() = ExchangeMode.SINGLE

    override fun getFields(): ModelFields {
        // 不在此定义字段，由 FlashSaleModule 统一管理
        return ModelFields()
    }

    override fun getTargetHour(): Long {
        // 定义支持的时间段列表
        val targetHours = listOf(10, 16, 22)

        // 获取当前时间的小时数
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // 遍历时间段列表，找到第一个大于当前小时的时间段
        for (hour in targetHours) {
            if (hour > currentHour) {
                return hour.toLong()
            }
        }

        // 如果当前时间已经超过所有时间段，返回最早的时间段（次日）
        return targetHours[0].toLong()
    }

    // 自定义扩展兑换项类
    class SeckillExchangeItem(
        val channelItemCode: String,
        val displayName: String,
        pointAmount: Int,
        val roundInstanceId: String,
        val triggerTime: Long
    ) : ExchangeItem(channelItemCode, pointAmount.toDouble(), pointAmount)
}
