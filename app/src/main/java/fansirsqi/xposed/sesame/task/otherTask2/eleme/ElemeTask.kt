package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.ElemeRiskTaskEntity
import fansirsqi.xposed.sesame.model.BaseModel
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
            "elemeFreeOrder", "抽免单任务", false, "自动执行饿了么淘免单打卡、做任务赚次数与免单抽奖"
        )

        @JvmField
        val elemeBurstRed = BooleanModelField(
            "elemeBurstRed", "外卖爆红包", false, "自动领取每日外卖 3 张 5 元爆红包"
        )

        @JvmField
        val elemeCustomUa = StringModelField(
            "elemeCustomUa",
            "自定义User-Agent",
            "",
            "【可选】若留空则使用系统原版真实指纹；也可在此粘贴完整真实UA生效。\n【Redmi K70 真实 UA 示例】:\nMozilla/5.0 (Linux; U; Android 14; zh-CN; 2311DRK48C Build/UKQ1.230804.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.107 UCBrowser/17.4.2.1610 Mobile Safari/537.36 AlipayDefined(nt:wifi,ws:412|0|915) AliApp(AP/12.12.30.8000) AlipayClient/12.12.30.8000 Language/zh-Hans useStatusBar/true isConcaveScreen/false Region/CN Channel/xiaomi GCanvas/1.9.38 Nebula SDK/1.8.1000 Nebula Ariver/1.0.0"
        )

        @JvmField
        val elemeGenerateUa = EmptyModelField(
            "elemeGenerateUa",
            "🎲 随机生成真实设备UA",
            "点击生成",
            "确定要从近几年市售真实手机（如 Redmi K70 系列、小米15、vivo X100 等）中随机生成一部真实机型的完整 UA 吗？\n\n（生成后将自动填入上方的【自定义User-Agent】中并持久化保存，日常任务执行绝不会自动变动）"
        ) { context ->
            ElemeMtopUtil.generateAndSaveRandomProfile(context)
        }

        @JvmField
        val elemeViewProfile = EmptyModelField(
            "elemeViewProfile",
            "查看当前生效设备详情",
            "点击查看",
            "查看当前实际生效的设备指纹与完整 User-Agent"
        ) { context ->
            ElemeMtopUtil.showActiveProfileDialog(context)
        }

        @JvmField
        val elemeRiskTasks = SelectModelField(
            "elemeRiskTasks",
            "风控跳过名单",
            LinkedHashSet(listOf("47780001", "49020001", "47774001", "48596001")),
            ElemeRiskTaskEntity::getList,
            "勾选的任务将在执行与领奖时自动跳过（默认已勾选百川风控任务）。若需重新测试，取消勾选即可重新请求。"
        )

        @JvmStatic
        fun getElemeLuckyStar(): BooleanModelField = elemeLuckyStar

        @JvmStatic
        fun getElemeFreeOrder(): BooleanModelField = elemeFreeOrder

        @JvmStatic
        fun getElemeBurstRed(): BooleanModelField = elemeBurstRed

        @JvmStatic
        fun getElemeCustomUa(): StringModelField = elemeCustomUa

        @JvmStatic
        fun getElemeGenerateUa(): EmptyModelField = elemeGenerateUa

        @JvmStatic
        fun getElemeViewProfile(): EmptyModelField = elemeViewProfile

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
        modelFields.addField(startTime)
        modelFields.addField(elemeLuckyStar)
        modelFields.addField(elemeFreeOrder)
        modelFields.addField(elemeBurstRed)
        modelFields.addField(elemeCustomUa)
        modelFields.addField(elemeGenerateUa)
        modelFields.addField(elemeViewProfile)
        modelFields.addField(elemeRiskTasks)
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
                if (elemeFreeOrder.value && !Status.hasFlagToday("Eleme_FreeOrder_Done")) {
                    FreeOrderTask.handle()
                }

                if (elemeBurstRed.value && !Status.hasFlagToday("Eleme_BurstRed_Done")) {
                    BurstRedTask.handle()
                }

                if (elemeLuckyStar.value && !Status.hasFlagToday("Eleme_LuckyStar_Done")) {
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
