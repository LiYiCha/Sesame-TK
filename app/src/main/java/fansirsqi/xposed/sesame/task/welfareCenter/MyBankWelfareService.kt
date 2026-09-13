package fansirsqi.xposed.sesame.task.welfareCenter

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.MapperEntity
import fansirsqi.xposed.sesame.entity.MemberBenefit
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.IdMapManager
import fansirsqi.xposed.sesame.util.maps.MyBankWelfareBenefitMap
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.LinkedHashMap
import java.util.Locale

/**
 * 网商银行福利金高阶业务服务
 * 包含：余额与奖励明细查询、任务中心闭环（报名/领奖/回查）、纯福利金点付权益安全兑换
 */
object MyBankWelfareService {
    private const val TAG = "网商银行🏦"
    private const val BUSINESS_NAME = "网商银行福利金"
    private const val TASK_CENTER_ID = "AP1269301"
    private const val WELFARE_PURCHASE_TYPE = "MYBK_FULIJIN_POINT_PAY"
    private val SUPPORTED_TRIGGER_TYPES = setOf("USER_TRIGGER", "EVENT_TRIGGER")

    data class MyBankExchangeCandidate(
        val itemId: String,
        val itemName: String,
        val benefitId: String,
        val pointNeeded: String,
        val purchaseType: String,
        val needPay: Boolean,
        val snapshotId: String,
        val consultCode: String,
        val isAvailable: Boolean,
        val safetyReason: String,
        val itemSource: String = "PROMO",
        val requestSourceInfo: String = "",
        val sourcePassMap: JSONObject? = null
    )

    private fun parseJsonOrNull(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    /**
     * 查询并打印福利金可用余额
     */
    @JvmStatic
    fun logPointBalance() {
        try {
            val responseStr = WelfareCenterRpcCall.queryPointBalance()
            val response = parseJsonOrNull(responseStr) ?: return
            if (!response.optBoolean("success", false)) return
            val result = response.optJSONObject("result")
            val pointBalance = result?.opt("pointBalance")?.toString()
                ?: response.opt("pointBalance")?.toString() ?: ""
            if (pointBalance.isNotBlank()) {
                Log.runtime("$TAG 当前可用福利金: ${formatDecimalAmount(pointBalance)}")
            }
        } catch (e: Exception) {
            Log.error(TAG, "logPointBalance err: ${e.message}")
        }
    }

    /**
     * 查询并打印可用虚拟福利金明细
     */
    @JvmStatic
    fun logVirtualProfits() {
        try {
            val responseStr = WelfareCenterRpcCall.queryEnableVirtualProfitV2("PLAY102815727")
            val response = parseJsonOrNull(responseStr) ?: return
            if (!response.optBoolean("success", false)) return
            val profitList = response.optJSONObject("result")?.optJSONArray("virtualProfitList") ?: return
            if (profitList.length() == 0) return

            for (i in 0 until profitList.length()) {
                val profit = profitList.optJSONObject(i) ?: continue
                val sceneDesc = profit.optString("sceneDesc").ifBlank {
                    profit.optJSONObject("sceneDTO")?.optString("sceneDesc").orEmpty()
                }
                val reward = profit.optString("reward").ifBlank {
                    profit.optString("pointShowValue")
                }.ifBlank {
                    extractAmountText(profit.opt("point"))
                }
                val desc = if (sceneDesc.isNotBlank()) sceneDesc else "福利金奖励"
                val rewardText = if (reward.isNotBlank()) "${formatDecimalAmount(reward)}福利金" else ""
                Log.runtime("$TAG 🎁 $desc: $rewardText")
            }
        } catch (e: Exception) {
            Log.error(TAG, "logVirtualProfits err: ${e.message}")
        }
    }

    /**
     * 执行福利金任务中心 (AP1269301) 任务流
     */
    @JvmStatic
    fun doTaskCenter() {
        if (Status.hasFlagToday("welfareCenterTaskDone")) {
            return
        }
        try {
            var round = 0
            val maxRounds = 5
            var hasUnfinishedTask = false

            while (round < maxRounds) {
                round++
                val queryStr = WelfareCenterRpcCall.taskQuery(TASK_CENTER_ID)
                val queryRes = parseJsonOrNull(queryStr) ?: break
                if (!queryRes.optBoolean("success", false)) {
                    Log.error(TAG, "福利金任务查询失败: ${extractResponseMessage(queryRes)}")
                    break
                }

                val taskDetailList = queryRes.optJSONObject("result")?.optJSONArray("taskDetailList")
                if (taskDetailList == null || taskDetailList.length() == 0) {
                    break
                }

                var roundActionCount = 0
                hasUnfinishedTask = false

                for (i in 0 until taskDetailList.length()) {
                    val taskDetail = taskDetailList.optJSONObject(i) ?: continue
                    val taskId = taskDetail.optString("taskId").trim()
                    if (taskId.isBlank()) continue

                    val triggerType = taskDetail.optString("sendCampTriggerType").trim()
                    if (triggerType.isNotBlank() && triggerType !in SUPPORTED_TRIGGER_TYPES) {
                        continue
                    }

                    val morphoDetail = parseJsonOrNull(
                        taskDetail.optJSONObject("taskExtProps")?.optString("TASK_MORPHO_DETAIL")
                    ) ?: JSONObject()
                    val title = morphoDetail.optString("title").trim()
                        .ifBlank { morphoDetail.optString("taskMainTitle").trim() }
                        .ifBlank { taskDetail.optString("taskTitle").trim() }
                        .ifBlank { taskId }

                    val status = taskDetail.optString("taskProcessStatus").trim().uppercase(Locale.ROOT)
                    when (status) {
                        "NONE_SIGNUP" -> {
                            // 需先报名
                            val triggerStr = WelfareCenterRpcCall.taskTrigger(taskId, "signup", TASK_CENTER_ID)
                            roundActionCount++
                            val triggerRes = parseJsonOrNull(triggerStr)
                            if (triggerRes != null && triggerRes.optBoolean("success")) {
                                Log.runtime("$TAG 🎯 [$title] 报名成功")
                            }
                            TimeUtil.sleep(600)
                        }
                        "SIGNUP_COMPLETE" -> {
                            // 报名完成，尝试发奖/完成
                            val triggerStr = WelfareCenterRpcCall.taskTrigger(taskId, "send", TASK_CENTER_ID)
                            roundActionCount++
                            val triggerRes = parseJsonOrNull(triggerStr)
                            if (triggerRes != null && triggerRes.optBoolean("success")) {
                                Log.runtime("$TAG 🎯 [$title] 领奖/完成成功")
                            }
                            TimeUtil.sleep(600)
                        }
                        "RECEIVE_SUCCESS" -> {
                            // 已完成领奖
                        }
                        else -> {
                            hasUnfinishedTask = true
                        }
                    }
                }

                if (roundActionCount == 0) {
                    break
                }
                TimeUtil.sleep(800)
            }

            if (!hasUnfinishedTask) {
                Status.setFlagToday("welfareCenterTaskDone")
            }
        } catch (e: Exception) {
            Log.error(TAG, "doTaskCenter err: ${e.message}")
        }
    }

    /**
     * 供设置页 SelectModelField 调用的选项获取方法
     */
    @JvmStatic
    fun refreshExchangeOptionsForSettings(): List<MapperEntity> {
        return try {
            val candidates = queryMyBankWelfareExchangeData()
            val benefitMap = IdMapManager.getInstance(MyBankWelfareBenefitMap::class.java)
            val resultList = mutableListOf<MapperEntity>()
            candidates.values.forEach { candidate ->
                val displayName = if (candidate.isAvailable) {
                    "${candidate.itemName} [${candidate.pointNeeded}福利金]"
                } else {
                    "${candidate.itemName} [${candidate.safetyReason}]"
                }
                benefitMap.add(candidate.itemId, displayName)
                resultList.add(MemberBenefit(candidate.itemId, displayName))
            }
            benefitMap.save(UserMap.currentUid)
            resultList
        } catch (e: Exception) {
            Log.error(TAG, "refreshExchangeOptionsForSettings err: ${e.message}")
            emptyList()
        }
    }

    /**
     * 查询网商银行会员权益并解析为 Candidate Map
     */
    @JvmStatic
    fun queryMyBankWelfareExchangeData(): LinkedHashMap<String, MyBankExchangeCandidate> {
        val candidateMap = LinkedHashMap<String, MyBankExchangeCandidate>()
        var pageNum = 1
        var totalCount = Int.MAX_VALUE
        val pageSize = 20

        while ((pageNum - 1) * pageSize < totalCount) {
            val responseStr = WelfareCenterRpcCall.queryItemsInMemberV2(pageNum, pageSize)
            val response = parseJsonOrNull(responseStr) ?: break
            if (!response.optBoolean("success", false)) break

            val pageItems = response.optJSONObject("result")?.optJSONObject("pageItems") ?: break
            val dataList = pageItems.optJSONArray("dataList") ?: break
            if (dataList.length() == 0) break

            for (i in 0 until dataList.length()) {
                val rawItem = dataList.optJSONObject(i) ?: continue
                val candidate = buildMyBankExchangeCandidate(rawItem) ?: continue
                candidateMap.putIfAbsent(candidate.itemId, candidate)
            }
            totalCount = pageItems.optInt("totalCount", totalCount)
            if (dataList.length() < pageSize) break
            pageNum++
        }
        return candidateMap
    }

    /**
     * 解析单个权益项，进行安全性过滤
     */
    private fun buildMyBankExchangeCandidate(rawItem: JSONObject): MyBankExchangeCandidate? {
        val nestedItemInfo = parseNestedItemInfo(rawItem)
        val itemId = rawItem.optString("itemId").trim()
            .ifBlank { rawItem.optJSONObject("itemConfigDTO")?.optString("itemId").orEmpty().trim() }
            .ifBlank { nestedItemInfo?.optString("itemId").orEmpty().trim() }
        if (itemId.isBlank()) return null

        val itemInfoDTO = rawItem.optJSONObject("itemInfoDTO")
            ?: nestedItemInfo?.optJSONObject("itemInfoDTO")
            ?: rawItem.optJSONObject("itemConfigDTO")?.optJSONObject("pkgBenefitConfig")
        val itemName = itemInfoDTO?.optString("itemName").orEmpty()
            .ifBlank { rawItem.optString("itemName") }
            .ifBlank { rawItem.optJSONObject("itemConfigDTO")?.optString("itemMainTitle").orEmpty() }
            .ifBlank { itemId }

        val purchaseInfoList = rawItem.optJSONArray("itemPurchaseInfoList")
            ?: nestedItemInfo?.optJSONArray("itemPurchaseInfoList")
            ?: JSONArray()
        val purchaseInfo = findPreferredPurchaseInfo(purchaseInfoList)
        val purchaseType = purchaseInfo?.optString("purchaseType").orEmpty().trim()
        val needPay = purchaseInfo?.optBoolean("needPay", false) == true
        val pointNeeded = extractAmountText(purchaseInfo?.opt("pointAmount"))
            .ifBlank { formatPointAmountFromMap(purchaseInfo?.optJSONObject("pointAmountMap")) }
        val cashAmount = extractAmountText(purchaseInfo?.opt("cashAmount"))

        val itemType = rawItem.optString("itemType").trim()
            .ifBlank { itemInfoDTO?.optJSONArray("itemType")?.optString(0).orEmpty().trim() }
        val jumpLink = rawItem.optString("itemJumpLink").trim()
        val consult = rawItem.optJSONObject("itemConsultDTO")
        val consultCode = consult?.optString("resultCode").orEmpty().trim()
        val consultSuccess = consult?.optBoolean("success", consultCode.isEmpty()) ?: true

        val benefitId = rawItem.optString("benefitId").trim()
            .ifBlank { nestedItemInfo?.optJSONArray("itemBenefitList")?.optJSONObject(0)?.opt("benefitId")?.toString().orEmpty().trim() }
        val snapshotId = rawItem.opt("snapshotId")?.toString().orEmpty().trim()
            .ifBlank { nestedItemInfo?.opt("snapshotId")?.toString().orEmpty().trim() }

        val storageList = rawItem.optJSONArray("itemStorageList")
            ?: nestedItemInfo?.optJSONArray("itemStorageList")
        val isOutOfStock = reviewStorageOutOfStock(storageList)

        val safetyReason = when {
            consultCode == "FAIL_ACCOUNT_AMOUNT" -> "福利金不足"
            isOutOfStock -> "库存不足"
            benefitId.isBlank() -> "缺少benefitId"
            purchaseType.isBlank() -> "缺少purchaseType"
            purchaseType != WELFARE_PURCHASE_TYPE -> "非纯福利金点付"
            needPay -> "需支付现金"
            hasPositiveAmount(cashAmount) -> "需现金补差"
            jumpLink.isNotBlank() -> "存在外部跳转"
            !consultSuccess && consultCode.isNotBlank() -> "咨询不满足:$consultCode"
            itemType.isNotBlank() && itemType != "BENEFIT_ITEM" -> "非权益红包"
            else -> ""
        }
        val isAvailable = safetyReason.isEmpty()

        return MyBankExchangeCandidate(
            itemId = itemId,
            itemName = itemName,
            benefitId = benefitId,
            pointNeeded = pointNeeded,
            purchaseType = purchaseType,
            needPay = needPay,
            snapshotId = snapshotId,
            consultCode = consultCode,
            isAvailable = isAvailable,
            safetyReason = safetyReason
        )
    }

    /**
     * 执行已勾选的网商银行福利金权益兑换
     */
    @JvmStatic
    fun doWelfareExchange(selectedIds: Set<String>?) {
        if (selectedIds == null || selectedIds.isEmpty()) return
        if (Status.hasFlagToday("myBankWelfareExchangeAllDone")) return

        val remainingIds = selectedIds.filter { !Status.hasFlagToday("myBankWelfareEx_${it}") }
        if (remainingIds.isEmpty()) {
            Status.setFlagToday("myBankWelfareExchangeAllDone")
            return
        }

        try {
            val candidates = queryMyBankWelfareExchangeData()
            for (id in remainingIds) {
                val candidate = candidates[id]
                if (candidate == null) {
                    Log.runtime("$TAG 兑换项 [$id] 本次列表未返回，跳过")
                    continue
                }
                if (!candidate.isAvailable) {
                    Log.runtime("$TAG 跳过 [${candidate.itemName}]#${candidate.safetyReason}")
                    Status.setFlagToday("myBankWelfareEx_${id}")
                    continue
                }

                // 1. 详情复核
                val detailStr = WelfareCenterRpcCall.querySingleBenefitDetail(candidate.benefitId, "", null)
                val detailResp = parseJsonOrNull(detailStr)
                if (detailResp == null || (!detailResp.optBoolean("success", false) && detailResp.optString("resultCode") != "SUCCESS")) {
                    Log.error(TAG, "权益详情查询失败: ${candidate.itemName} ${detailResp?.let { extractResponseMessage(it) } ?: "空响应"}")
                    continue
                }

                // 2. 确认单复核
                val confirmStr = WelfareCenterRpcCall.queryPromoBenefitOrderConfirmInfo(candidate.benefitId, "", null)
                val confirmResp = parseJsonOrNull(confirmStr)
                if (confirmResp == null || (!confirmResp.optBoolean("success", false) && confirmResp.optString("resultCode") != "SUCCESS")) {
                    Log.error(TAG, "兑换确认单查询失败: ${candidate.itemName} ${confirmResp?.let { extractResponseMessage(it) } ?: "空响应"}")
                    continue
                }

                // 3. 提交兑换
                val exchangeStr = WelfareCenterRpcCall.exchangeMemberBenefit(candidate.benefitId, candidate.itemId, "", null)
                val exchangeResp = parseJsonOrNull(exchangeStr)
                if (exchangeResp == null) continue
                val exchangeSuccess = exchangeResp.optBoolean("success", false) || exchangeResp.optString("resultCode") == "SUCCESS"
                if (!exchangeSuccess) {
                    val msg = extractResponseMessage(exchangeResp)
                    Log.error(TAG, "兑换失败 [${candidate.itemName}]: $msg")
                    if (msg.contains("已兑换") || msg.contains("已领取") || msg.contains("不足") || msg.contains("受限")) {
                        Status.setFlagToday("myBankWelfareEx_${id}")
                    }
                    continue
                }

                val orderId = exchangeResp.optString("orderId").trim()
                    .ifBlank { JsonUtil.getValueByPath(exchangeResp, "result.orderId") }
                    .ifBlank { JsonUtil.getValueByPath(exchangeResp, "result.outBizNo") }
                Log.runtime("$TAG 兑换 [${candidate.itemName}] 成功！消耗 ${candidate.pointNeeded} 福利金")
                Status.setFlagToday("myBankWelfareEx_${id}")

                // 4. 订单结果回查
                if (orderId.isNotBlank()) {
                    runCatching {
                        val orderStr = WelfareCenterRpcCall.querySingleExchangeOrderDetail(candidate.benefitId, "PROMO", orderId, null)
                        val orderResp = parseJsonOrNull(orderStr)
                        if (orderResp != null) {
                            val detail = orderResp.optJSONObject("exchangeOrderDetailConfigInfo")
                                ?: orderResp.optJSONObject("result")?.optJSONObject("exchangeOrderDetailConfigInfo")
                            val status = detail?.optString("orderStatus").orEmpty().ifBlank { detail?.optString("status").orEmpty() }
                            Log.runtime("$TAG 订单状态 [${candidate.itemName}]: ${status.ifBlank { "已提交" }}")
                        }
                    }
                }
                TimeUtil.sleep(1000)
            }
            Status.setFlagToday("myBankWelfareExchangeAllDone")
        } catch (e: Exception) {
            Log.error(TAG, "doWelfareExchange err: ${e.message}")
        }
    }

    private fun parseNestedItemInfo(rawItem: JSONObject): JSONObject? {
        val itemInfo = rawItem.optJSONObject("itemConfigDTO")
            ?.optJSONObject("pkgBenefitConfig")
            ?.optJSONObject("extInfo")
            ?.optString("itemInfo")
            .orEmpty()
        return parseJsonOrNull(itemInfo)
    }

    private fun findPreferredPurchaseInfo(purchaseInfoList: JSONArray?): JSONObject? {
        if (purchaseInfoList == null || purchaseInfoList.length() == 0) return null
        var fallback: JSONObject? = null
        for (i in 0 until purchaseInfoList.length()) {
            val purchaseInfo = purchaseInfoList.optJSONObject(i) ?: continue
            if (fallback == null) fallback = purchaseInfo
            if (purchaseInfo.optString("purchaseType").trim() == WELFARE_PURCHASE_TYPE) {
                return purchaseInfo
            }
        }
        return fallback
    }

    private fun formatPointAmountFromMap(pointAmountMap: JSONObject?): String {
        if (pointAmountMap == null) return ""
        val directValue = pointAmountMap.opt(WELFARE_PURCHASE_TYPE)
        if (directValue != null) return formatDecimalAmount(directValue.toString())
        val keys = pointAmountMap.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = pointAmountMap.opt(key) ?: continue
            val text = formatDecimalAmount(value.toString())
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun hasPositiveAmount(rawAmount: String?): Boolean {
        return rawAmount?.trim()?.toBigDecimalOrNull()?.signum() == 1
    }

    private fun reviewStorageOutOfStock(storageList: JSONArray?): Boolean {
        if (storageList == null || storageList.length() == 0) return false
        var maxRemain = -1L
        var hasExplicit = false
        for (i in 0 until storageList.length()) {
            val storage = storageList.optJSONObject(i) ?: continue
            val remain = storage.optLong("remainInventory", Long.MIN_VALUE)
            if (remain != Long.MIN_VALUE) {
                hasExplicit = true
                if (remain > maxRemain) maxRemain = remain
            }
        }
        return hasExplicit && maxRemain <= 0L
    }

    private fun extractResponseMessage(response: JSONObject): String {
        return response.optString("resultDesc")
            .ifBlank { response.optString("memo") }
            .ifBlank { response.optString("desc") }
            .ifBlank { response.optString("errorMsg") }
            .ifBlank { response.optString("errorMessage") }
            .ifBlank { response.optString("message") }
            .ifBlank { JsonUtil.getValueByPath(response, "result.resultDesc") }
            .ifBlank { JsonUtil.getValueByPath(response, "result.errorMsg") }
            .ifBlank { response.toString() }
    }

    private fun formatDecimalAmount(rawAmount: String?): String {
        val normalized = rawAmount?.trim().orEmpty()
        if (normalized.isEmpty()) return ""
        return runCatching {
            BigDecimal(normalized).stripTrailingZeros().toPlainString()
        }.getOrDefault(normalized)
    }

    private fun extractAmountText(value: Any?): String {
        return when (value) {
            is JSONObject -> formatDecimalAmount(value.optString("amount"))
            else -> formatDecimalAmount(value?.toString())
        }
    }
}
