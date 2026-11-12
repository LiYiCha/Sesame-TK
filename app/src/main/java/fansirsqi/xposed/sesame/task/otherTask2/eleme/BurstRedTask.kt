package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.util.Log
import java.util.concurrent.locks.ReentrantLock

/**
 * 饿了么外卖专享爆红包领取任务（独立于免单活动）
 * 单例设计与并发互斥锁保护，防止多线程冲突
 */
object BurstRedTask {
    private const val TAG = "BurstRedTask"
    private const val FLAG_TODAY = "Eleme_BurstRed_Done"
    private val lock = ReentrantLock()

    fun handle() {
        if (Status.hasFlagToday(FLAG_TODAY)) {
            Log.other("饿了么", "[爆红包] 今日饿了么爆红包已领取，跳过执行")
            return
        }

        if (!lock.tryLock()) {
            Log.other("饿了么", "[爆红包] 已有饿了么爆红包任务正在执行中，跳过本次并发调用")
            return
        }

        try {
            Log.other("饿了么", "[爆红包] ==== 开始执行饿了么爆红包任务 ====")
            val initCookie = ElemeAuth.ensureCookie()
            if (initCookie.isEmpty() || !ElemeAuth.isBound()) {
                val reason = if (initCookie.isEmpty()) "未获取到有效登录凭证(账号未绑定或换票失败)" else "当前未关联有效饿了么账号"
                Log.other("饿了么", "[爆红包] $reason，自动关闭【饿了么|爆红包】开关并退出")
                fansirsqi.xposed.sesame.task.otherTask2.OtherTask2.disableElemeBurstRed(reason)
                return
            }

            // 1. 严格校验会话有效性，若已失效则自动换票自愈刷新
            val cookie = ElemeAuth.validateOrRefreshCookie(initCookie)
            if (cookie.isNullOrEmpty()) {
                Log.other("饿了么", "[爆红包] ⚠️ 饿了么会话校验未通过且自动刷新失败，请在支付宝手动打开饿了么小程序完成授权")
                return
            }
            Log.other("饿了么", "[爆红包] 当前账号: ${ElemeAuth.getAccountSummary()}")

            // 领取外卖小程序首页/进站专享 3 张 5 元爆红包
            val sendResp = FreeOrderRpcCall.sendBet(cookie)
            if (sendResp != null) {
                val data = sendResp.optJSONObject("data") ?: sendResp
                val coupon = data.optJSONObject("resultObj")
                    ?.optJSONObject("eatPluginResult")
                    ?.optJSONObject("EAT_PAID_COUPON_SEND")

                if (coupon != null) {
                    val count = coupon.optString("count", "3")
                    val reduction = coupon.optString("reductionYuan", "5")
                    val name = coupon.optString("rightName", "爆红包")
                    Log.other("饿了么", "[爆红包] 🎉 成功领取外卖专享爆红包: $count 张 $reduction 元 $name")
                } else {
                    Log.other("饿了么", "[爆红包] 外卖爆红包已领取或今日无待领权益")
                }
            } else {
                Log.other("饿了么", "[爆红包] 外卖爆红包领取请求未返回有效数据")
            }

            Status.setFlagToday(FLAG_TODAY)
            Log.other("饿了么", "[爆红包] ==== 饿了么爆红包任务执行完毕 ====")
        } catch (t: Throwable) {
            Log.other("饿了么", "[爆红包] 执行饿了么爆红包任务异常: ${t.message}")
        } finally {
            lock.unlock()
        }
    }
}
