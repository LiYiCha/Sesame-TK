package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

/**
 * 饿了么淘免单自动化任务（ETMD）
 * 支持签到领抽奖机会、主页状态同步、免单真机抽奖（iconDraw）、单例与线程互斥防并发、无次数/次数未变安全关闭防死循环
 */
object FreeOrderTask {
    private const val TAG = "FreeOrderTask"
    private const val FLAG_TODAY = "Eleme_FreeOrder_Done"
    private const val MAX_DRAW_ATTEMPTS = 5
    private val lock = ReentrantLock()
    private val CHANCE_PATTERN = Pattern.compile("当前还有(\\d+)次抽奖机会")

    /**
     * 提取主页或抽奖返回中的 popupShareWinV2 节点
     */
    private fun extractPopup(resp: JSONObject?): JSONObject? {
        if (resp == null) return null
        val rootData = resp.optJSONObject("data") ?: resp
        // 抽奖响应层级: data.popupShareWinV2
        val directPopup = rootData.optJSONObject("popupShareWinV2")
        if (directPopup != null) return directPopup
        // 主页响应层级: data.data.mainPage.data.popupShareWinV2
        return rootData.optJSONObject("data")
            ?.optJSONObject("mainPage")
            ?.optJSONObject("data")
            ?.optJSONObject("popupShareWinV2")
    }

    /**
     * 解析当前剩余可用抽免单次数
     */
    private fun parseDrawChance(resp: JSONObject?): Int {
        if (resp == null) return 0
        val rootData = resp.optJSONObject("data") ?: resp
        val errorCode = rootData.optString("errorCode", "")
        val errorMessage = rootData.optString("errorMessage", "")
        val floatIcon = rootData.optJSONObject("floatIcon")
        val typeEnum = floatIcon?.optString("typeEnum").orEmpty()

        // 1. 服务端明确返回无抽奖机会/卡片已领完错误码
        if (errorCode == "23" || errorMessage == "NO_COUPON_LEFT" || typeEnum.contains("NO_WIN_NO_CHANCE")) {
            return 0
        }

        val popup = extractPopup(resp) ?: return 0
        val mainCards = popup.optJSONArray("mainCard") ?: return 0
        val firstCard = mainCards.optJSONObject(0) ?: return 0
        val buttonObj = firstCard.optJSONObject("button")
        val action = buttonObj?.optString("action").orEmpty()
        val btnText = buttonObj?.optString("buttonText").orEmpty()
        val subBtnText = buttonObj?.optString("subButtonText").orEmpty()

        // 2. 按钮显式禁用或文本明确为 0 次
        if (action == "disable" || subBtnText.contains("0次") || btnText.contains("做任务得抽免单机会")) {
            return 0
        }

        // 3. 正则匹配具体数字，例如 "当前还有2次抽奖机会"
        val matcher = CHANCE_PATTERN.matcher(subBtnText)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull() ?: 0
        }

        // 4. 按钮为可抽奖状态，保底计 1 次
        if (action == "draw" || btnText == "立即抽" || btnText == "继续抽免单") {
            return 1
        }

        return 0
    }

    fun handle() {
        if (Status.hasFlagToday(FLAG_TODAY)) {
            Log.runtime(TAG, "今日饿了么免单任务已完成，跳过执行")
            return
        }

        if (!lock.tryLock()) {
            Log.runtime(TAG, "已有饿了么抽免单任务正在执行中，跳过本次并发调用")
            return
        }

        try {
            Log.runtime(TAG, "==== 开始执行饿了么抽免单任务 ====")
            val cookie = ElemeAuth.ensureCookie()
            if (cookie.isEmpty() || !ElemeAuth.isBound()) {
                val reason = if (cookie.isEmpty()) "未获取到有效登录凭证(账号未绑定或换票失败)" else "当前未关联有效饿了么账号"
                Log.runtime(TAG, "$reason，自动关闭【饿了么|抽免单】开关并退出")
                fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.disableElemeFreeOrder(reason)
                return
            }
            Log.runtime(TAG, "当前账号: ${ElemeAuth.getAccountSummary()}")

            // 1. 会话校验（拟真网络环境第一步）
            LuckyStarRpcCall.checkLogin(cookie)
            Thread.sleep(RandomUtil.nextGaussianLong(400, 800))

            // 2. 淘免单签到打卡（每日签到赠送抽免单机会）
            val signInfoResp = FreeOrderRpcCall.querySignInfo(cookie)
            val signData = signInfoResp?.optJSONObject("data")?.optJSONObject("data")
            val isSigned = signData?.optBoolean("signIn", false) ?: false
            val copyId = signData?.optJSONObject("extInfo")?.optString("copyId").orEmpty()

            if (!isSigned && copyId.isNotEmpty()) {
                Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
                val signResult = FreeOrderRpcCall.recordSignIn(cookie, copyId)
                if (signResult != null) {
                    val rewardList = signResult.optJSONObject("data")?.optJSONObject("data")?.optJSONArray("stageReward")
                    val prizeCount = rewardList?.length() ?: 1
                    Log.runtime(TAG, "🎉 淘免单今日签到成功，已获取 $prizeCount 次抽免单机会！")
                } else {
                    Log.runtime(TAG, "淘免单签到请求完成")
                }
            } else if (isSigned) {
                Log.runtime(TAG, "淘免单今日已签到打卡")
            }
            Thread.sleep(RandomUtil.nextGaussianLong(600, 1200))

            // 3. 淘免单主页数据查询（读取开奖倒计时与当前抽奖次数）
            var mainCardTips = ""
            var currentChance = 0
            val homepageResp = FreeOrderRpcCall.queryEtmdHomepage(cookie)
            if (homepageResp != null) {
                val popup = extractPopup(homepageResp)
                mainCardTips = popup?.optString("mainCardTips").orEmpty()
                currentChance = parseDrawChance(homepageResp)
                val tipsText = if (mainCardTips.isNotEmpty()) "，开奖提示: [$mainCardTips]" else ""
                Log.runtime(TAG, "淘免单主页拉取成功，当前可用抽奖机会: $currentChance$tipsText")
            } else {
                Log.runtime(TAG, "淘免单主页拉取未返回有效数据(网络超时或异常)，跳过本次执行")
                return
            }
            Thread.sleep(RandomUtil.nextGaussianLong(800, 1500))

            val homeErrorCode = homepageResp?.optString("errorCode").orEmpty()
            val homeErrorMsg = homepageResp?.optString("errorMessage").orEmpty()
            val isActivityClosed = homeErrorCode == "ACTIVITY_CLOSED" ||
                    homeErrorCode == "ACTIVITY_NOT_EXIST" ||
                    homeErrorMsg.contains("活动已结束") ||
                    homeErrorMsg.contains("活动未开启") ||
                    homeErrorMsg.contains("业务未开启")
            if (isActivityClosed) {
                val reason = if (homeErrorMsg.isNotEmpty()) homeErrorMsg else "活动已结束或未开启"
                Log.runtime(TAG, "淘免单活动不可用($reason)，自动关闭【饿了么|抽免单】开关并退出")
                fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.disableElemeFreeOrder(reason)
                Status.setFlagToday(FLAG_TODAY)
                return
            }

            if (currentChance <= 0) {
                Log.runtime(TAG, "今日暂无可用抽免单机会，结束今日抽免单任务")
                Status.setFlagToday(FLAG_TODAY)
                return
            }

            // 4. 执行淘免单抽奖（对齐真机交互接口 mtop.ele.growth.fission.client.etmd.et.icondraw）
            for (attempt in 1..MAX_DRAW_ATTEMPTS) {
                val prevChance = currentChance
                Log.runtime(TAG, "开始第 $attempt 次发起免单抽奖 (记录抽前机会: $prevChance)...")
                val drawResp = FreeOrderRpcCall.iconDraw(cookie)
                if (drawResp == null) {
                    Log.runtime(TAG, "第 $attempt 次抽免单未返回有效响应，终止本次抽奖")
                    break
                }

                val data = drawResp.optJSONObject("data") ?: drawResp
                val errorCode = data.optString("errorCode", "")
                val errorMessage = data.optString("errorMessage", "")
                val isDrawClosed = errorCode == "ACTIVITY_CLOSED" || errorCode == "ACTIVITY_NOT_EXIST" || errorMessage.contains("活动已结束") || errorMessage.contains("活动未开启")
                if (isDrawClosed) {
                    val reason = if (errorMessage.isNotEmpty()) errorMessage else "活动已结束或未开启"
                    Log.runtime(TAG, "淘免单活动不可用($reason)，自动关闭【饿了么|抽免单】开关并退出")
                    fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.disableElemeFreeOrder(reason)
                    Status.setFlagToday(FLAG_TODAY)
                    return
                }
                val floatIcon = data.optJSONObject("floatIcon")
                val typeEnum = floatIcon?.optString("typeEnum").orEmpty()
                val popup = extractPopup(drawResp)
                val drawResult = popup?.optString("drawResult").orEmpty()
                val currentTips = popup?.optString("mainCardTips").takeIf { !it.isNullOrEmpty() } ?: mainCardTips

                val mainCards = popup?.optJSONArray("mainCard")
                val firstCard = mainCards?.optJSONObject(0)
                val couponName = firstCard?.optString("couponName").orEmpty()
                val showStatus = firstCard?.optString("showStatus").orEmpty()
                val number = firstCard?.optInt("number", 0) ?: 0
                val buttonObj = firstCard?.optJSONObject("button")
                val subBtnText = buttonObj?.optString("subButtonText").orEmpty()
                val cardType = data.optString("cardType", "")

                val subCards = popup?.optJSONArray("subCard")
                val subCard = subCards?.optJSONObject(0)
                val subCouponName = subCard?.optString("couponName").orEmpty()
                val subThreshold = subCard?.optString("threshold").orEmpty()

                // (1) 严格未中奖 / 无抽奖机会判定（对齐真机抓包 [56]）
                val isNotWon = errorCode == "23" || errorMessage == "NO_COUPON_LEFT" ||
                               typeEnum.contains("NO_WIN") ||
                               drawResult == "NOT_DRAWN" ||
                               showStatus == "NOT_DRAWN" ||
                               number == 0

                // (2) 真正抽中免单券判定
                val isTrueWin = !isNotWon && (number > 0 || showStatus == "DRAWN") &&
                                errorCode != "23" && !typeEnum.contains("NO_WIN") &&
                                (couponName.contains("免单") || cardType == "COUPON_FREE")

                if (isTrueWin) {
                    val awardName = if (couponName.isNotEmpty()) couponName else "免单券"
                    Log.other("闪购免单🎉[恭喜中奖!] 真正抽中免单券: $awardName (数量: $number)！今日免单任务完成")
                    Status.setFlagToday(FLAG_TODAY)
                    return
                }

                // 解析抽奖后的剩余机会
                val nextChance = parseDrawChance(drawResp)

                // (3) 抽奖后次数未减少/未发生变化 -> 自动退出并关闭抽奖开关（防死循环与无意义调度）
                if (nextChance >= prevChance) {
                    val reason = if (subBtnText.isNotEmpty()) subBtnText else if (errorMessage.isNotEmpty()) errorMessage else "无可用抽奖机会"
                    val tipsStr = if (currentTips.isNotEmpty()) " (提示: $currentTips)" else ""
                    Log.runtime(TAG, "第 $attempt 次抽免单后可用次数未发生变化(抽前: $prevChance, 抽后: $nextChance, $reason$tipsStr)，自动关闭【饿了么|抽免单】开关并退出")
                    fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.disableElemeFreeOrder("抽奖后次数未变化(前:$prevChance,后:$nextChance,$reason)")
                    Status.setFlagToday(FLAG_TODAY)
                    return
                }

                // (4) 次数正常减少（消耗了 1 次机会）
                val awardText = if (couponName.isNotEmpty() && !isNotWon) "，获得奖品: [$couponName]" else ""
                val subAward = if (subCouponName.isNotEmpty()) "，伴随获取: [$subCouponName - $subThreshold]" else ""
                Log.runtime(TAG, "第 $attempt 次抽免单完成(已消耗1次机会，剩余次数: $nextChance)$awardText$subAward")
                currentChance = nextChance

                // 机会已用完，正常结束本次抽奖流程
                if (currentChance <= 0) {
                    Log.runtime(TAG, "可用抽免单机会已全部用完，结束本次抽奖")
                    break
                }

                if (attempt < MAX_DRAW_ATTEMPTS) {
                    Thread.sleep(RandomUtil.nextGaussianLong(2000, 3500))
                }
            }
            Thread.sleep(RandomUtil.nextGaussianLong(800, 1500))

            // 5. 辅助打卡召回（拉取外卖小程序进站打卡）
            FreeOrderRpcCall.pageRecall(cookie)

            // 6. 限时秒杀排期巡检（整点秒杀满减券，如满60减20）
            val consultResp = FreeOrderRpcCall.consultUnit(cookie)
            if (consultResp != null) {
                val schedList = (consultResp.optJSONObject("data") ?: consultResp).optJSONObject("result")?.optJSONArray("scheduleInfoList")
                if (schedList != null && schedList.length() > 0) {
                    val sched = schedList.optJSONObject(0)
                    Log.runtime(TAG, "外卖限时秒杀排期巡检: ${sched?.optString("status")}, 场次ID: ${sched?.optString("scheduleId")}")
                }
            }

            Status.setFlagToday(FLAG_TODAY)
            Log.runtime(TAG, "==== 饿了么免单任务执行完毕 ====")
        } catch (t: Throwable) {
            Log.error(TAG, "执行饿了么免单任务异常: ${t.message}")
        } finally {
            lock.unlock()
        }
    }
}
