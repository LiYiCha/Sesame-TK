package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock

/**
 * 饿了么抽免单自动化任务（订单抽奖）
 * 严格对齐 幸运星.py orderfree_draw 规范：
 * 1. 每日仅执行一次订单抽奖（Flag: Eleme_OrderFree_Done）
 * 2. 自动拉取互动中心最新 orderFreeDraw 入口并执行抽奖
 * 3. 抽中免单发送系统强提醒，未中奖或无订单资格静默处理
 * 4. 协同连续签到攒免单打卡（若今日尚未签到，则协助完成打卡）
 */
object FreeOrderTask {
    private const val TAG = "FreeOrderTask"
    const val FLAG_ORDER_FREE = "Eleme_OrderFree_Done"
    const val FLAG_SIGNIN = "Eleme_SignIn_Done"
    private val lock = ReentrantLock()

    fun handle() {
        if (!ElemeTask.elemeFreeOrder.value) {
            return
        }

        if (Status.hasFlagToday(FLAG_ORDER_FREE)) {
            return
        }

        if (!lock.tryLock()) {
            return
        }

        try {
            val initCookie = ElemeAuth.ensureCookie()
            if (initCookie.isEmpty() || !ElemeAuth.isBound()) {
                val reason = if (initCookie.isEmpty()) "未获取到有效登录凭证(账号未绑定或换票失败)" else "当前未关联有效饿了么账号"
                Log.other("饿了么", "[抽免单] $reason，自动关闭【抽免单任务】开关并退出")
                ElemeTask.disableElemeFreeOrder(reason)
                return
            }

            val cookie = ElemeAuth.validateOrRefreshCookie(initCookie)
            if (cookie.isNullOrEmpty()) {
                Log.other("饿了么", "[抽免单] ⚠️ 饿了么会话校验未通过且自动刷新失败，请在支付宝手动打开饿了么小程序完成授权")
                return
            }

            // 1. 获取互动中心首页聚合数据
            val homepageResp = FreeOrderRpcCall.queryCenterHomepage(cookie)
            val rootData = homepageResp?.optJSONObject("data") ?: homepageResp
            val dataObj = rootData?.optJSONObject("data") ?: rootData

            // 2. 连续签到攒免单打卡（若今日尚未打卡，则协助完成一次，对齐幸运星.py规范）
            if (!Status.hasFlagToday(FLAG_SIGNIN)) {
                try {
                    val signInObj = dataObj?.optJSONObject("signIn")?.optJSONObject("data")
                    val dtos = signInObj?.optJSONArray("signInQueryPrizeDTOS")
                    var todayDto: JSONObject? = null
                    if (dtos != null) {
                        for (k in 0 until dtos.length()) {
                            val d = dtos.optJSONObject(k) ?: continue
                            if (d.optBoolean("today", false)) {
                                todayDto = d
                                break
                            }
                        }
                    }
                    val signedToday = todayDto?.optBoolean("signIn", false) ?: signInObj?.optBoolean("signIn", false) ?: false
                    if (signedToday) {
                        Status.setFlagToday(FLAG_SIGNIN)
                    } else {
                        val copyId = todayDto?.optJSONObject("extInfo")?.optString("copyId")?.takeIf { it.isNotEmpty() }
                            ?: signInObj?.optJSONObject("extInfo")?.optString("copyId").orEmpty()
                        if (copyId.isNotEmpty()) {
                            val strategy = signInObj?.optString("signInRewardStrategy").orEmpty()
                            val awards = todayDto?.optJSONObject("extInfo")?.optJSONArray("stageAward")
                            val award = awards?.optJSONObject(0)
                            Thread.sleep(RandomUtil.nextGaussianLong(400, 800))
                            val signRes = LuckyStarRpcCall.signIn(cookie, copyId, strategy, award)
                            if (signRes.isSuccess || signRes.errorCode.contains("REPEAT") || signRes.errorCode.contains("ALREADY") || signRes.errorMsg.contains("已签到")) {
                                Status.setFlagToday(FLAG_SIGNIN)
                                Log.other("饿了么", "[抽免单] 连续签到打卡: 成功")
                            }
                        } else {
                            Status.setFlagToday(FLAG_SIGNIN)
                        }
                    }
                } catch (e: Throwable) {
                    Log.error(TAG, "签到打卡异常: ${e.message}")
                }
            }

            // 3. 订单抽奖（下单赢免单抽奖，每日仅抽一次，对齐幸运星.py规范）
            Thread.sleep(RandomUtil.nextGaussianLong(500, 1000))
            val orderFreeData = dataObj?.optJSONObject("orderFreeDraw")
            val ofRes = FreeOrderRpcCall.orderFreeDraw(cookie, orderFreeData)
            Status.setFlagToday(FLAG_ORDER_FREE)

            if (ofRes.isSuccess) {
                val prize = ElemeMtopUtil.extractPrizeDesc(ofRes.data ?: ofRes.rawJson).ifEmpty { "奖励" }
                Log.other("饿了么", "[抽免单] 🎲 订单抽奖: $prize")
                if (prize.contains("免单")) {
                    Log.other("饿了么", "[抽免单] 🎉 恭喜成功抽中免单大奖: $prize")
                    Notify.sendPrizeNotification("🎉 饿了么免单中奖通知", "恭喜在订单抽奖中成功命中【$prize】！请及时使用。")
                }
            } else {
                val msg = ofRes.errorMsg
                if (msg.contains("已抽") || msg.contains("不足") || msg.contains("无资格") || msg.contains("未下单") || ofRes.errorCode.contains("LIMIT")) {
                    Log.other("饿了么", "[抽免单] 🎲 订单抽奖已完成 (今日已抽或暂无待抽订单)")
                } else {
                    Log.other("饿了么", "[抽免单] 🎲 订单抽奖: 未中奖或暂无待抽订单")
                }
            }
        } catch (t: Throwable) {
            val msg = "[抽免单] ❌ 执行异常: ${t.message}"
            Log.error(TAG, msg)
            Log.other("饿了么", msg)
        } finally {
            lock.unlock()
        }
    }
}
