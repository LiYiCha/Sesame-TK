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
            return
        }

        if (!lock.tryLock()) {
            return
        }

        try {
            val initCookie = ElemeAuth.ensureCookie()
            if (initCookie.isEmpty() || !ElemeAuth.isBound()) {
                val reason = if (initCookie.isEmpty()) "未获取到有效登录凭证(账号未绑定或换票失败)" else "当前未关联有效饿了么账号"
                Log.other("饿了么", "[爆红包] $reason，自动关闭【外卖爆红包】开关并退出")
                ElemeTask.disableElemeBurstRed(reason)
                return
            }

            // 1. 严格校验会话有效性，若已失效则自动换票自愈刷新
            val cookie = ElemeAuth.validateOrRefreshCookie(initCookie)
            if (cookie.isNullOrEmpty()) {
                Log.other("饿了么", "[爆红包] ⚠️ 饿了么会话校验未通过且自动刷新失败，请在支付宝手动打开饿了么小程序完成授权")
                return
            }

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
                    Log.other("饿了么", "[爆红包] 🎉 成功领取: $count 张 $reduction 元 $name")
                }
            }

            Status.setFlagToday(FLAG_TODAY)
        } catch (t: Throwable) {
            Log.other("饿了么", "[爆红包] ❌ 执行异常: ${t.message}")
        } finally {
            lock.unlock()
        }
    }
}
