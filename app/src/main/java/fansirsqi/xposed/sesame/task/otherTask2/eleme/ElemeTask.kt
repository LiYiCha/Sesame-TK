package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.ElemeMallEntity
import fansirsqi.xposed.sesame.entity.ElemeRiskTaskEntity
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.model.CustomSettings
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.EmptyModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.StringModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.TaskCommon
import fansirsqi.xposed.sesame.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

class ElemeTask : ModelTask() {

    private var taskExecutor: ExecutorService? = null
    @Volatile
    private var currentFuture: Future<*>? = null
    private val executionLock = ReentrantLock()
    private val isTaskRunning = AtomicBoolean(false)

    override fun getName(): String = "饿了么"

    private val startTime = StringModelField("startTime", "开始执行时间(关闭:-1)", "0700")

    companion object {
        private const val TAG = "饿了么"

        @JvmField
        val elemeLuckyStar = BooleanModelField(
            "elemeLuckyStar", "幸运星任务", false, "自动完成饿了么幸运星浏览、点击与阶段奖励领取"
        )

        @JvmField
        val elemeFreeOrder = BooleanModelField(
            "elemeFreeOrder", "抽免单任务", false, "自动执行饿了么下单赢免单抽奖（每日一次订单抽奖，协同签到）"
        )

        @JvmField
        val elemeBurstRed = BooleanModelField(
            "elemeBurstRed", "外卖爆红包", false, "自动领取每日外卖 3 张 5 元爆红包"
        )

        @JvmField
        val elemeAccountStatus = EmptyModelField(
            "elemeAccountStatus",
            "账号状态"
        ) { context ->
            ElemeMtopUtil.showAccountStatusDialog(context)
        }

        @JvmField
        val elemeRefreshMall = EmptyModelField(
            "elemeRefreshMall",
            "兑换商城"
        ) { context ->
            ElemeMtopUtil.showMallListDialog(context)
        }

        @JvmField
        val elemeExchangeItems = SelectModelField(
            "elemeExchangeItems",
            "目标兑换商品",
            LinkedHashSet<String>(),
            ElemeMallEntity::getList,
            "允许自动兑换的目标商品 ID 集合。本字段已在设置页隐藏，请通过【兑换商城】弹窗内每个商品的「自动兑换」勾选框维护（实时保存）。"
        )

        @JvmField
        val elemeCustomUa = StringModelField(
            "elemeCustomUa",
            "设备UA",
            "",
            "点击编辑设备 UA：弹窗内实时显示当前生效机型，可从真实机型库中指定机型生成，或从「历史记录」一键回填之前生效过的 UA，确认后保存生效；留空使用本机系统原版指纹。每次保存自动记录历史（最多 20 条）。保存后立即生效。"
        )

        @JvmField
        val elemeQuizActId = StringModelField(
            "elemeQuizActId",
            "答题活动actId",
            "",
            "趣味话题答题（猜价格/猜一猜）的活动 ID。该 ID 不在幸运星任务列表的返回中，只能从答题页 URL 上读取，因此需手动填写；留空则跳过答题，不发起任何答题请求。"
        )

        @JvmField
        val elemeRiskTasks = SelectModelField(
            "elemeRiskTasks",
            "风控名单",
            LinkedHashSet(listOf("47780001", "49020001", "47774001", "48596001")),
            ElemeRiskTaskEntity::getList,
            "勾选的任务在执行与领奖时自动跳过；执行中命中 405/417 风控或任务已下线时会自动加入此名单。取消勾选即可重新测试，再次失败仍会自动加回。"
        )

        @JvmStatic
        fun getElemeExchangeItems(): SelectModelField = elemeExchangeItems

        @JvmStatic
        fun getElemeCustomUa(): StringModelField = elemeCustomUa

        @JvmStatic
        fun getElemeRiskTasks(): SelectModelField = elemeRiskTasks

        @JvmStatic
        fun getElemeQuizActId(): StringModelField = elemeQuizActId

        @JvmStatic
        fun disableElemeLuckyStar(reason: String) {
            Log.other("饿了么", "[幸运星] 饿了么幸运星不可用($reason)，自动关闭【幸运星任务】开关")
            try {
                elemeLuckyStar.value = false
                fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
            } catch (t: Throwable) {
                Log.error(TAG, "关闭饿了么幸运星开关异常: " + t.message)
            }
        }

        @JvmStatic
        fun disableElemeFreeOrder(reason: String) {
            Log.other("饿了么", "[抽免单] 饿了么免单不可用($reason)，自动关闭【抽免单任务】开关")
            try {
                elemeFreeOrder.value = false
                fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
            } catch (t: Throwable) {
                Log.error(TAG, "关闭饿了么抽免单开关异常: " + t.message)
            }
        }

        @JvmStatic
        fun disableElemeBurstRed(reason: String) {
            Log.other("饿了么", "[爆红包] 饿了么爆红包不可用($reason)，自动关闭【外卖爆红包】开关")
            try {
                elemeBurstRed.value = false
                fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true)
            } catch (t: Throwable) {
                Log.error(TAG, "关闭饿了么爆红包开关异常: " + t.message)
            }
        }
    }

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        // 「目标兑换商品」已由【兑换商城】弹窗内的勾选框承载，设置页隐藏该字段（仍保留配置持久化）
        elemeExchangeItems.isHidden = true
        // 账号状态/兑换商城/设备UA/风控名单统一在「饿了么」页面内展示（管理入口不再单独拆页）
        modelFields.addField(startTime)
        modelFields.addField(elemeLuckyStar)
        modelFields.addField(elemeFreeOrder)
        modelFields.addField(elemeBurstRed)
        modelFields.addField(elemeAccountStatus)
        modelFields.addField(elemeRefreshMall)
        modelFields.addField(elemeExchangeItems)
        modelFields.addField(elemeCustomUa)
        modelFields.addField(elemeQuizActId)
        modelFields.addField(elemeRiskTasks)
        return modelFields
    }

    override fun getGroup(): ModelGroup = ModelGroup.ELEME

    override fun getIcon(): String = "Default.png"

    override fun runJava() {
        if (!isTaskRunning.compareAndSet(false, true)) {
            return
        }

        // 局部快照：避免 stopTask() 在判空与 submit 之间把 taskExecutor 置 null 引发竞态空指针
        val executor = synchronized(this) {
            val current = taskExecutor
            if (current == null || current.isShutdown) {
                Executors.newCachedThreadPool().also { taskExecutor = it }
            } else {
                current
            }
        }

        currentFuture = executor.submit {
            executionLock.lock()
            try {
                // 每轮任务开始复位账号级风控标记：
                // 同一轮内任一子任务命中 USER_RISK 后，后续子任务的一切请求都会被拦截
                ElemeMtopUtil.resetAccountRiskFlag()

                if (elemeFreeOrder.value) {
                    FreeOrderTask.handle()
                }

                if (elemeBurstRed.value && !Status.hasFlagToday("Eleme_BurstRed_Done")) {
                    BurstRedTask.handle()
                }

                if (elemeLuckyStar.value) {
                    LuckyStarTask.handle()
                }
            } catch (t: Throwable) {
                if (t is java.util.concurrent.CancellationException || t is InterruptedException || t.cause is InterruptedException) {
                    Log.runtime(TAG, "饿了么任务已停止运行")
                } else {
                    Log.error("$TAG 运行出错：$t")
                    Log.printStackTrace(t)
                }
            } finally {
                isTaskRunning.set(false)
                executionLock.unlock()
            }
        }
    }

    override fun stopTask() {
        currentFuture?.let {
            if (!it.isDone) {
                it.cancel(true)
            }
        }
        taskExecutor?.let {
            if (!it.isShutdown) {
                it.shutdownNow()
                taskExecutor = null
            }
        }
        isTaskRunning.set(false)
    }

    override fun check(): Boolean {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.runtime("⏸ 当前为只收能量时间【" + BaseModel.energyTime.value + "】，停止执行" + name + "任务！")
            return false
        }
        if (TaskCommon.IS_MODULE_SLEEP_TIME) {
            Log.runtime("💤 模块休眠时间【" + BaseModel.modelSleepTime.value + "】停止执行" + name + "任务！")
            return false
        }
        // 「开始执行时间」此前只作为展示字段，框架从不读取，导致把它当作生效开关会误判。
        // 此处接入判定：格式 HHmm（如 0700），早于该时刻不执行；留空或填 -1 表示不限制。
        return !isBeforeStartTime()
    }

    /** 当前是否早于配置的开始执行时间（-1/空 视为不限制） */
    private fun isBeforeStartTime(): Boolean {
        val raw = startTime.value?.trim().orEmpty()
        if (raw.isEmpty() || raw == "-1") return false
        val hm = raw.padStart(4, '0')
        val hour = hm.take(2).toIntOrNull() ?: return false
        val minute = hm.takeLast(2).toIntOrNull() ?: return false
        if (hour !in 0..23 || minute !in 0..59) return false
        val cal = java.util.Calendar.getInstance()
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return nowMinutes < hour * 60 + minute
    }
}
