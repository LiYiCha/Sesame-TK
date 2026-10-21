package fansirsqi.xposed.sesame.task.exchange

import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.TaskCommon
import fansirsqi.xposed.sesame.hook.context.AppContext
import fansirsqi.xposed.sesame.hook.resource.ForegroundHelper
import fansirsqi.xposed.sesame.hook.resource.WakeLockManager
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil

abstract class BaseFlashSaleTask : ModelTask() {

    companion object {
        /** 每个商品失败后的重试次数 */
        private const val MAX_RETRY = 3

        /** 唤醒锁在等待时长之外的额外持有缓冲（覆盖兑换阶段） */
        private const val LEASE_BUFFER_MS = 30 * 60 * 1000L

        /** 等待分片时长，保证可及时响应中断 */
        private const val SLEEP_SLICE_MS = 60_000L

        /** 发车提前量：在整点前该毫秒数发出请求，利用网络往返让服务端在整点收到 */
        @JvmField
        val waitTimeField = IntegerModelField("waitTime", "开抢提前量(毫秒)", 200, 0, 3000)
    }

    // 构建兑换项
    protected abstract fun buildExchangeItems(): List<ExchangeItem>

    override fun getFields(): ModelFields {
        val fields = ModelFields()
        fields.addField(waitTimeField)
        return fields
    }

    override fun check(): Boolean {
        return when {
            TaskCommon.IS_ENERGY_TIME -> {
                Log.runtime("⏸ 当前为只收能量时间【${BaseModel.energyTime.value}】，停止执行${name}任务！")
                false
            }
            TaskCommon.IS_MODULE_SLEEP_TIME -> {
                Log.runtime("💤 模块休眠时间【${BaseModel.modelSleepTime.value}】停止执行${name}任务！")
                false
            }
            else -> true
        }
    }

    override fun runJava() {
        asyncRun()
    }

    fun asyncRun() {
        try {
            if (!shouldExecuteTask()) return

            val targetTime = calculateTargetTime(System.currentTimeMillis())

            // 预构建兑换项和参数
            val items = prepareExchangeItems()
            if (items.isEmpty()) {
                Log.other("${name}没有有效的兑换项，任务终止")
                return
            }

            // 发车时间 = 目标整点 - 提前量
            val advanceMs = (waitTimeField.value ?: 200).toLong()
            val fireTime = (targetTime - advanceMs).coerceAtLeast(0L)

            // ── 前台保活 + 唤醒锁：防止等待整点期间进程被杀或 CPU 休眠 ──
            name?.let { ForegroundHelper.startForeground(it, targetTime) }
            val waitMs = (fireTime - System.currentTimeMillis()).coerceAtLeast(0L)
            val wakeLease = AppContext.getService()?.applicationContext?.let {
                WakeLockManager.acquire(it, waitMs + LEASE_BUFFER_MS, "FlashSale_${javaClass.simpleName}")
            }

            try {
                sleepUntilTarget(targetTime, fireTime)
                executeAll(items)
                if (!Status.hasFlagToday(completedKey)) {
                    Status.setFlagToday(completedKey)
                }
            } finally {
                // ── 无论成功、失败、超时，都确保停止前台保活并释放唤醒锁 ──
                ForegroundHelper.stopForeground()
                wakeLease?.close()
            }
        } catch (e: Exception) {
            Log.error(name, "兑换异常：${e.message}")
        }
    }

    // 检查任务是否需要运行
    private fun shouldExecuteTask(): Boolean {
        if (Status.hasFlagToday(completedKey)) {
            Log.runtime("${name}任务已标记为完成，跳过执行")
            return false
        }
        if (TaskCommon.IS_MODULE_SLEEP_TIME) {
            Log.other("${name}模块休眠期间自动终止")
            return false
        }
        return true
    }

    // 预构建兑换项和参数
    private fun prepareExchangeItems(): List<ExchangeItem> {
        val items = buildExchangeItems()
        val validItems = mutableListOf<ExchangeItem>()
        for (item in items) {
            if (tryExchange(item)) {
                validItems.add(item)
            } else {
                Log.error(name, "⚠️ 参数预构建失败: ${item.code}")
            }
        }
        return validItems
    }

    // 等待到发车时间（目标整点 - 提前量，分片 sleep，可响应中断）
    private fun sleepUntilTarget(targetTime: Long, fireTime: Long) {
        if (System.currentTimeMillis() >= fireTime) {
            Log.other("${name}⏰ 当前已过开抢整点，直接兑换")
            return
        }
        val advanceText = if (targetTime > fireTime) "，提前${targetTime - fireTime}ms发车" else ""
        Log.other("${name}⏰ 等待开抢整点 [${TimeUtil.getCommonDate(targetTime)}]$advanceText")
        while (true) {
            val remaining = fireTime - System.currentTimeMillis()
            if (remaining <= 0) break
            try {
                Thread.sleep(minOf(remaining, SLEEP_SLICE_MS))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.other("${name}💤 等待被中断")
                return
            }
        }
    }

    // 顺序兑换：每个商品失败重试 MAX_RETRY 次；SINGLE 模式任一成功即停止
    private fun executeAll(items: List<ExchangeItem>) {
        for (item in items) {
            val success = exchangeWithRetry(item)
            if (success && exchangeMode == ExchangeMode.SINGLE) {
                Log.other("${name} 单例模式，已成功兑换，停止后续商品")
                break
            }
        }
    }

    private fun exchangeWithRetry(item: ExchangeItem): Boolean {
        for (attempt in 1..MAX_RETRY) {
            val params = item.exchangeParams ?: return false
            try {
                if (sendExchangeRequest(params, item)) {
                    item.markAsExchanged()
                    return true
                }
            } catch (e: Exception) {
                Log.error(name, "兑换请求异常 [${item.code}]: ${e.message}")
            }
            if (attempt < MAX_RETRY) {
                try {
                    Thread.sleep(RandomUtil.nextInt(1000, 2000).toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        Log.error(name, "兑换失败(已重试${MAX_RETRY}次) [${item.name.ifEmpty { item.code }}]")
        return false
    }

    protected abstract val exchangeMode: ExchangeMode?

    protected open fun getTargetHour(): Long {
        return 10
    }

    protected abstract val completedKey: String

    protected abstract fun tryExchange(item: ExchangeItem): Boolean

    protected abstract fun sendExchangeRequest(params: JSONArray, item: ExchangeItem): Boolean

    open class ExchangeItem(
        val code: String,
        val value: Double,
        val cost: Int,
        /** 商品名称，用于日志显示，默认为空（向后兼容）*/
        val name: String = ""
    ) {
        @Volatile
        var exchangeParams: JSONArray? = null

        private val exchanged = AtomicBoolean(false)

        fun markAsExchanged(): Boolean {
            return exchanged.compareAndSet(false, true)
        }

        /** 有商品名时优先展示名称，否则退化为 "价格[积分]" 格式 */
        override fun toString(): String {
            return if (name.isNotEmpty()) {
                "$name(${String.format(Locale.CHINA, "%.1f", value)}元/${cost}积分)"
            } else {
                String.format(Locale.CHINA, "%.1f元[%d积分]", value, cost)
            }
        }
    }

    enum class ExchangeMode {
        SINGLE,
        MULTI
    }

    protected fun calculateTargetTime(serverTime: Long): Long {
        val calendar = Calendar.getInstance(Locale.CHINA)
        calendar.timeInMillis = serverTime

        val targetHour = getTargetHour().toInt()

        if (targetHour == 0) {
            // 如果目标小时为0，则设置目标时间为明日0点整
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            if (serverTime >= calendar.timeInMillis) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        } else {
            // 设置目标时间为指定小时整点整(今日)
            calendar.set(Calendar.HOUR_OF_DAY, targetHour)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }

        return calendar.timeInMillis
    }
}
