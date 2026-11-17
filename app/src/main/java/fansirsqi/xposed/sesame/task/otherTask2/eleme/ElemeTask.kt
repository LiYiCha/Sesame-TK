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

    protected var executeIntervalInt: Int = 6000
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
        val elemeAutoExchange = BooleanModelField(
            "elemeAutoExchange",
            "兑换商品",
            false,
            "开启后，当拥有的幸运星满足兑换条件时，自动兑换【兑换商城】弹窗内勾选「自动兑换」的商品（为防误扣资产，未勾选的商品绝不兑换，星数不足绝不请求）。"
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
            "点击编辑设备 UA：弹窗内实时显示当前生效机型，可从真实机型库中指定机型生成，或从「历史记录」一键回填之前生效过的 UA，确认后保存生效；留空使用本机系统原版指纹。每次保存自动记录历史（最多 20 条）。修改有 10 分钟冷却防风控。"
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
        fun getElemeLuckyStar(): BooleanModelField = elemeLuckyStar

        @JvmStatic
        fun getElemeFreeOrder(): BooleanModelField = elemeFreeOrder

        @JvmStatic
        fun getElemeBurstRed(): BooleanModelField = elemeBurstRed

        @JvmStatic
        fun getElemeAutoExchange(): BooleanModelField = elemeAutoExchange

        @JvmStatic
        fun getElemeExchangeItems(): SelectModelField = elemeExchangeItems

        @JvmStatic
        fun getElemeRefreshMall(): EmptyModelField = elemeRefreshMall

        @JvmStatic
        fun getElemeAccountStatus(): EmptyModelField = elemeAccountStatus

        @JvmStatic
        fun getElemeCustomUa(): StringModelField = elemeCustomUa

        @JvmStatic
        fun getElemeRiskTasks(): SelectModelField = elemeRiskTasks

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
        // 账号状态/兑换商城/设备UA/风控名单已拆分至「饿了么管理」页面（ElemeManageTask），
        // 字段实例仍在本 companion 中，运行时读写引用不变
        modelFields.addField(startTime)
        modelFields.addField(elemeLuckyStar)
        modelFields.addField(elemeFreeOrder)
        modelFields.addField(elemeBurstRed)
        modelFields.addField(elemeAutoExchange)
        modelFields.addField(elemeExchangeItems)
        return modelFields
    }

    override fun getGroup(): ModelGroup = ModelGroup.ELEME

    override fun getIcon(): String = "Default.png"

    override fun runJava() {
        if (!isTaskRunning.compareAndSet(false, true)) {
            return
        }

        if (taskExecutor == null || taskExecutor!!.isShutdown) {
            taskExecutor = Executors.newCachedThreadPool()
        }

        currentFuture = taskExecutor!!.submit {
            executionLock.lock()
            try {
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
        return if (TaskCommon.IS_ENERGY_TIME) {
            Log.runtime("⏸ 当前为只收能量时间【" + BaseModel.energyTime.value + "】，停止执行" + name + "任务！")
            false
        } else if (TaskCommon.IS_MODULE_SLEEP_TIME) {
            Log.runtime("💤 模块休眠时间【" + BaseModel.modelSleepTime.value + "】停止执行" + name + "任务！")
            false
        } else {
            true
        }
    }
}
