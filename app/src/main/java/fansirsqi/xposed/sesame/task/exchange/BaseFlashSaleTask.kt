package fansirsqi.xposed.sesame.task.exchange

import android.annotation.SuppressLint
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.TaskCommon
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.resource.ForegroundHelper
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil

abstract class BaseFlashSaleTask : ModelTask() {

    companion object {
        // 兑换时间偏移量
        private const val EXCHANGE_DEADLINE_OFFSET = 2000L // 2秒偏移量

        @JvmField
        val maxWaitTimeField = IntegerModelField("maxWaitTime", "最大等待时间(分钟)", 60, 1, 180)

        @JvmField
        val exchangeOffsetField = IntegerModelField("exchangeOffset", "兑换持续时间(毫秒)", 2000, 1, 60000)

        @JvmField
        val waitTimeField = IntegerModelField("waitTime", "提前时间(毫秒)", 200, 1, 3000)
    }

    // 唤醒锁 - 使用 AtomicReference 保证线程安全
    private val wakeUpLatchRef = AtomicReference<CountDownLatch>()

    // 唤醒任务ID - 用于取消唤醒
    private val wakeUpTidRef = AtomicReference<String>()

    // 任务是否已被取消 - 使用 AtomicBoolean 保证原子性
    private val taskCancelled = AtomicBoolean(false)

    // 获取唤醒时间字段
    protected abstract fun getWakeUpConfigField(): IntegerModelField?

    // 构建兑换项
    protected abstract fun buildExchangeItems(): List<ExchangeItem>

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(maxWaitTimeField)
        modelFields.addField(exchangeOffsetField)
        modelFields.addField(waitTimeField)
        return modelFields
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
            // 重置任务状态
            taskCancelled.set(false)
            wakeUpLatchRef.set(CountDownLatch(1)) // 每次运行时重新创建

            // 检查任务是否需要运行
            if (!shouldExecuteTask()) return

            // 计算目标时间和截止时间
            val serverTime = System.currentTimeMillis()
            val targetTime = calculateTargetTime(serverTime)

            // 计算截止时间
            val offset = exchangeOffsetField.value
            val deadline = if (offset != null && offset != 0) {
                targetTime + offset
            } else {
                targetTime + EXCHANGE_DEADLINE_OFFSET
            }

            // 检查是否超过最大等待时间
            val now = System.currentTimeMillis()
            if (targetTime - now > maxWaitTimeField.value * 60 * 1000L) {
                Log.other("${name}⏰ 距离目标时间超过${maxWaitTimeField.value}分钟，跳过本次")
                return
            }

            if (System.currentTimeMillis() > deadline + 12000) {
                Log.other("${name}⏰ 当前时间已超过截止时间，不再执行")
                if (!Status.hasFlagToday(completedKey)) {
                    Status.setFlagToday(completedKey)
                }
                return
            }

            // 预构建兑换项和参数
            val validItems = prepareExchangeItems()
            if (validItems.isEmpty()) {
                Log.other("${name}没有有效的兑换项，任务终止")
                return
            }

            // 添加唤醒任务
            addWakeUpTaskIfNeeded(targetTime)

            // 判断是否应该退出主线程（即：已添加唤醒任务，且当前时间远早于唤醒时间）
            var shouldWaitForWakeUp = false

            getWakeUpConfigField()?.let { wakeUpField ->
                val wakeUpTime = targetTime - wakeUpField.value * 60 * 1000L
                if (hasChildTask("WAKEUP_${javaClass.simpleName}")) {
                    // 已添加唤醒任务，并且当前时间仍远早于唤醒时间
                    if (System.currentTimeMillis() < wakeUpTime) {
                        shouldWaitForWakeUp = true
                    }
                }
            }

            // ── 前台保活：防止系统在等待期间杀死进程 ──────────────────
            name?.let { ForegroundHelper.startForeground(it, targetTime) }

            try {
                // 如果满足上述条件，就退出主线程，等待唤醒
                if (shouldWaitForWakeUp) {
                    Log.other("${name}⏰ 尚未到达唤醒时间，主线程等待唤醒...")
                    try {
                        val latch = wakeUpLatchRef.get() // 原子性获取
                        if (latch != null) {
                            val awaitSuccess = latch.await(maxWaitTimeField.value.toLong(), TimeUnit.MINUTES)
                            if (!awaitSuccess) {
                                Log.other("${name}⏰ 等待唤醒超时，终止任务")
                                return
                            }
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        Log.error(name, "主线程等待唤醒中断：${e.message}")
                        return
                    }

                    // 检查任务是否被取消
                    if (taskCancelled.get() || Thread.currentThread().isInterrupted) {
                        Log.other("${name}⏰ 任务已被取消")
                        return
                    }
                }

                // 如果当前时间已经很接近目标时间，跳过等待直接兑换
                val now2 = System.currentTimeMillis()
                if (targetTime - now2 <= exchangeOffsetField.value) {
                    Log.other("${name}⏰ 时间已接近目标时间，跳过等待，直接执行兑换")
                } else {
                    waitForTargetTime(targetTime)
                }

                // 执行兑换逻辑
                if (isConcurrentMode()) {
                    executeConcurrent(validItems, deadline)
                } else {
                    executeSequential(validItems, deadline)
                }

                // 标记任务完成
                if (!Status.hasFlagToday(completedKey)) {
                    Status.setFlagToday(completedKey)
                }
            } finally {
                // ── 无论成功、失败、超时，都确保停止前台保活 ────────────
                ForegroundHelper.stopForeground()
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

    // 添加唤醒任务
    private fun addWakeUpTaskIfNeeded(targetTime: Long) {
        getWakeUpConfigField()?.let { wakeUpField ->
            val wakeUpTime = targetTime - wakeUpField.value * 60 * 1000L
            val now = System.currentTimeMillis()

            if (now < wakeUpTime) {
                val wakeUpTid = "WAKEUP_${javaClass.simpleName}"
                // 保存唤醒任务ID，用于取消
                wakeUpTidRef.set(wakeUpTid)

                // 获取当前 latch 引用，避免回调中的竞态条件
                val currentLatch = wakeUpLatchRef.get()

                // 使用系统级精确唤醒（AlarmManager），确保在 Doze 模式下也能准时唤醒
                val alarmSet = ApplicationHook.scheduleExactAlarm(wakeUpTid, wakeUpTime) {
                    Log.other("${name}⏰ 系统精确唤醒执行成功，准备进入兑换")
                    currentLatch?.countDown() // 使用捕获的引用，避免竞态
                }

                if (alarmSet) {
                    Log.other("${name}⏰ 已设置系统精确唤醒，将在 [${TimeUtil.getCommonDate(wakeUpTime)}] 唤醒")
                } else {
                    // 如果系统唤醒设置失败，回退到协程子任务方式
                    Log.other("${name}⏰ 系统精确唤醒设置失败，使用协程子任务方式")
                    addChildTask(ChildModelTask(wakeUpTid, "秒杀唤醒", {
                        Log.other("${name}⏰ 协程唤醒任务执行成功，准备进入兑换")
                        currentLatch?.countDown() // 使用捕获的引用，避免竞态
                    }, wakeUpTime))
                }

                Log.other("${name}⏰ 已添加唤醒任务，将在 [${TimeUtil.getCommonDate(wakeUpTime)}] 唤醒")
            } else {
                Log.other("${name}⏰ 当前时间已超过唤醒时间，准备进入兑换")
                // 直接释放锁，让主线程继续
                val latch = wakeUpLatchRef.get()
                latch?.countDown()
            }
        }
    }

    /**
     * 取消任务
     */
    fun cancelTask() {
        taskCancelled.set(true)

        // 取消系统精确唤醒
        val wakeUpTid = wakeUpTidRef.get()
        if (wakeUpTid != null) {
            ApplicationHook.cancelExactAlarm(wakeUpTid)
        }

        // 释放等待的线程
        val latch = wakeUpLatchRef.getAndSet(null)
        latch?.countDown()

        // 停止前台保活
        ForegroundHelper.stopForeground()
    }

    // 等待目标时间（简化版）
    private fun waitForTargetTime(targetTime: Long) {
        try {
            val now = System.currentTimeMillis()
            val actualTargetTime = targetTime - waitTimeField.value
            val remaining = actualTargetTime - now

            Log.debug("⏳ 开始等待：${TimeUtil.getCommonDate(now)} ➜ ${TimeUtil.getCommonDate(actualTargetTime)}" +
                    " (目标时间: ${TimeUtil.getCommonDate(targetTime)}, 提前: ${waitTimeField.value}ms)")

            if (remaining <= 0) {
                Log.other("${name}⚠️ 目标时间已过，跳过等待")
                return
            }

            // 阶段1：粗略等待 - 休眠到目标时间前5ms
            val coarseSleepTime = maxOf(0, remaining - 5)
            if (coarseSleepTime > 0) {
                Thread.sleep(coarseSleepTime)
            }

            // 阶段2：精细等待 - 最后5ms使用1ms循环
            while (System.currentTimeMillis() < actualTargetTime) {
                val spinRemaining = actualTargetTime - System.currentTimeMillis()
                if (spinRemaining > 1) {
                    Thread.sleep(1)
                }
                // 最后1ms使用busy-wait自旋，提高精度
            }

            Log.debug("${name}✅已到达目标时间前${waitTimeField.value}毫秒:${TimeUtil.getCommonDate(actualTargetTime)}")

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.other("${name}💤 等待被中断")
        } catch (e: Exception) {
            Log.error(name, "等待发生异常:${e.message}")
        }
    }

    // 并发兑换
    private fun executeConcurrent(items: List<ExchangeItem>, deadline: Long) {
        if (taskCancelled.get() || Thread.currentThread().isInterrupted) {
            Log.other("${name}⏰ 任务已取消，跳过兑换")
            return
        }

        val date = TimeUtil.getCommonDate(System.currentTimeMillis())
        Log.debug("${name}⏰当前[$date]开始兑换")

        val successFlag = AtomicBoolean(false)
        val futures = mutableListOf<Future<*>>()

        // 重构：为每一个兑换项创建 5 个并行的抢兑线程，实现真正的高并发多播秒杀
        val concurrentThreadCount = 5
        for (item in items) {
            for (t in 0 until concurrentThreadCount) {
                val future = ThreadPoolManager.NETWORK_EXECUTOR.submit {
                    if (successFlag.get() || taskCancelled.get()) {
                        return@submit // 如果已经成功兑换或任务被取消，直接返回
                    }

                    // 错峰发送：子线程之间微调间隔 (每个错开20ms)，使请求均匀排入服务器接收队列
                    if (t > 0) {
                        try {
                            Thread.sleep((t * 20).toLong())
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return@submit
                        }
                    }

                    // 持续重复请求直到成功或超时
                    performExchangeAsync(item, successFlag, deadline)
                }
                futures.add(future)
            }
        }

        // 等待所有任务完成或超时（确保每个 Future 都被正确处理）
        try {
            for (future in futures) {
                if (taskCancelled.get()) break
                val remainingTime = deadline - System.currentTimeMillis()
                if (remainingTime > 0) {
                    try {
                        future.get(remainingTime, TimeUnit.MILLISECONDS)
                    } catch (e: TimeoutException) {
                        Log.debug("${name}⏰ 单个任务超时")
                    } catch (e: Exception) {
                        if (!taskCancelled.get()) {
                            Log.debug("${name} 单个任务异常: ${e.javaClass.simpleName}")
                        }
                    }
                }
            }
        } finally {
            // 取消所有未完成的任务
            for (future in futures) {
                if (!future.isDone) {
                    future.cancel(true)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun performExchangeAsync(item: ExchangeItem, successFlag: AtomicBoolean, deadline: Long) {
        // 持续重复请求直到成功或超时
        while (!successFlag.get() && !item.isExchanged() && !taskCancelled.get()
            && !Thread.currentThread().isInterrupted && System.currentTimeMillis() < deadline) {
            // 发起异步兑换请求
            try {
                item.exchangeParams?.let { params ->
                    val success = sendExchangeRequestAsync(params, item)
                    if (success) {
                        item.markAsExchanged()
                        if (exchangeMode == ExchangeMode.SINGLE) {
                            val wasFalse = successFlag.compareAndSet(false, true)
                            if (wasFalse) {
                                Log.other("🛑 单例模式，已成功兑换 [${item.value}]，终止其他任务")
                            }
                        }
                        return
                    }
                }
            } catch (e: Exception) {
                // 检查是否是中断导致的异常（包括被包装的InterruptedException）
                if (e.cause is InterruptedException || Thread.currentThread().isInterrupted) {
                    Thread.currentThread().interrupt()
                    break
                }
                // 只有在非取消状态下才打印错误日志
                if (!taskCancelled.get()) {
                    Log.error(name, "兑换请求异常 [${item.code}]: ${e.message}")
                }
            }

            // 检查是否成功兑换
            if (item.isExchanged()) {
                if (exchangeMode == ExchangeMode.SINGLE) {
                    val wasFalse = successFlag.compareAndSet(false, true)
                    if (wasFalse) {
                        Log.other("🛑 单例模式，已成功兑换 [${item.value}]，终止其他任务")
                    }
                }
                return
            }

            // 检查是否已超时或被取消
            if (System.currentTimeMillis() >= deadline || taskCancelled.get()) {
                break
            }

            // 短暂休眠避免过度请求（优化：从1ms增加到5ms，减少CPU占用）
            try {
                Thread.sleep(5)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    // 顺序模式执行
    private fun executeSequential(items: List<ExchangeItem>, deadline: Long) {
        if (taskCancelled.get() || Thread.currentThread().isInterrupted) {
            Log.other("${name}⏰ 任务已取消，跳过兑换")
            return
        }

        val successFlag = AtomicBoolean(false)
        while (System.currentTimeMillis() < deadline && !successFlag.get()
            && !taskCancelled.get() && !Thread.currentThread().isInterrupted) {
            for (item in items) {
                if (successFlag.get() || System.currentTimeMillis() > deadline
                    || taskCancelled.get() || Thread.currentThread().isInterrupted) break

                item.exchangeParams?.let { params ->
                    val result = sendExchangeRequest(params, item)
                    if (result) {
                        item.markAsExchanged()
                        if (exchangeMode == ExchangeMode.SINGLE) {
                            successFlag.set(true)
                            Log.other("🛑 单例模式，终止其他任务")
                            return
                        }
                    }
                }
            }
        }
    }

    abstract fun sendExchangeRequestAsync(params: JSONArray, item: ExchangeItem): Boolean

    protected abstract val exchangeMode: ExchangeMode?

    protected open fun isConcurrentMode(): Boolean {
        return true
    }

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

        fun isExchanged(): Boolean {
            return exchanged.get()
        }

        fun getRatio(): Double {
            return value / cost
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
