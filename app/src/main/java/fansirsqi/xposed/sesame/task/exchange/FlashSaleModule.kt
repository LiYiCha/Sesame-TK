package fansirsqi.xposed.sesame.task.exchange

import android.annotation.SuppressLint
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.MemberBenefit
import org.json.JSONArray
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class FlashSaleModule : BaseFlashSaleTask() {

    private val TAG = "兑换任务🚀"

    // 配置字段
    private val enableNeverLand = BooleanModelField("enableNeverLand", "健康岛兑换", false)
    private val enableNeverLandList = BooleanModelField("enableNeverLandList", "健康岛列表方式", false)
    private val neverLandList = SelectModelField(
        "neverLandList",
        "健康岛 | 商品列表",
        linkedSetOf(),
        NeverLandEX::getExchangeItemListForUI
    )
    private val enablePackageExchange = BooleanModelField("enablePackageExchange", "包裹兑换", false)
    private val packageExchangeList = SelectModelField(
        "packageExchangeList",
        "包裹兑换 | 商品列表",
        linkedSetOf(),
        PackageExchangeEX::getExchangeItemListForUI
    )
    private val enableGameCenterGold = BooleanModelField("enableGameCenterGold", "游戏中心金币秒杀", false)
    private val enableGameCenterGoldList = BooleanModelField("enableGameCenterGoldList", "游戏中心金币列表方式", false)
    private val gameCenterGoldList = SelectModelField(
        "gameCenterGoldList",
        "游戏中心金币 | 商品列表",
        linkedSetOf(),
        GameCenterGoldEX::getExchangeItemListForUI
    )
    private val gameCenterGoldTargetHour = IntegerModelField(
        "gameCenterGoldTargetHour",
        "游戏中心金币 | 秒杀开抢整点(0-23点)",
        20, 0, 23
    )
    private val enableGoldenBeanMall = BooleanModelField("goldenBeanMallExchange", "金豆商城秒杀", false)
    private val goldenBeanMallList = SelectModelField(
        "goldenBeanMallList",
        "金豆商城 | 商品列表",
        linkedSetOf(),
        GoldenBeanMallEX::getExchangeItemListForUI
    )
    private val goldenBeanMallTargetHour = IntegerModelField(
        "goldenBeanMallTargetHour",
        "金豆商城 | 秒杀开抢整点(0-23点)",
        0, 0, 23
    )

    // 子任务实例
    private val neverLandEX = NeverLandEX()
    protected val packageExchangeEX = PackageExchangeEX()
    protected val gameCenterGoldEX = GameCenterGoldEX()
    protected val goldenBeanMallEX = GoldenBeanMallEX()

    // 每个子任务的执行状态控制（key 为任务名，synchronized 保护）
    private val subTaskFutures = HashMap<String, Future<*>>()

    companion object {
        private val TASK_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(10)

        private const val NEVERLAND_LIST = "neverland_list"
        private const val BAOGUO_LIST = "baoguo_list"
        private const val GAMECENTER_GOLD_LIST = "gamecenter_gold_list"
        private const val GOLDENBEAN_MALL_LIST = "goldenbean_mall_list"

        // 防止重复提交预加载任务
        private val isNeverLandPreloading = AtomicBoolean(false)
        private val isBaoguoPreloading = AtomicBoolean(false)
        private val isGameCenterGoldPreloading = AtomicBoolean(false)
        private val isGoldenBeanMallPreloading = AtomicBoolean(false)
    }

    override fun buildExchangeItems(): List<ExchangeItem> {
        return emptyList()
    }

    init {
        initializeSubTasks()
        schedulePreloadIfNeeded()
    }

    /**
     * 初始化子任务字段，大额和小额实例共享相同配置字段引用，但运行时状态独立
     */
    private fun initializeSubTasks() {
        // 健康岛
        neverLandEX.enableNeverLandEX = enableNeverLand
        neverLandEX.neverLandList = neverLandList

        // 包裹兑换
        packageExchangeEX.enablePackageExchange = enablePackageExchange
        packageExchangeEX.packageExchangeList = packageExchangeList

        // 游戏中心金币秒杀
        gameCenterGoldEX.enableGameCenterGold = enableGameCenterGold
        gameCenterGoldEX.gameCenterGoldList = gameCenterGoldList
        gameCenterGoldEX.targetHourField = gameCenterGoldTargetHour

        // 金豆商城秒杀
        goldenBeanMallEX.enableGoldenBeanMall = enableGoldenBeanMall
        goldenBeanMallEX.goldenBeanMallList = goldenBeanMallList
        goldenBeanMallEX.targetHourField = goldenBeanMallTargetHour
    }

    /**
     * 检查并调度预加载任务（一天执行一次）
     * 此检查同时防止 init{} 阶段 Status 未就绪导致崩溃
     */
    private fun schedulePreloadIfNeeded() {
        if (fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.getRpcBridge() == null) {
            return
        }
        schedulePreloadOnce("健康岛", enableNeverLandList, NEVERLAND_LIST, isNeverLandPreloading) {
            NeverLandEX.refreshItemsFromAPI()
        }
        schedulePreloadOnce("包裹兑换列表", enablePackageExchange, BAOGUO_LIST, isBaoguoPreloading) {
            PackageExchangeEX.refreshItemsFromAPI()
        }
        schedulePreloadOnce("游戏中心金币列表", enableGameCenterGoldList, GAMECENTER_GOLD_LIST, isGameCenterGoldPreloading) {
            GameCenterGoldEX.refreshItemsFromAPI()
        }
        schedulePreloadOnce("金豆商城列表", enableGoldenBeanMall, GOLDENBEAN_MALL_LIST, isGoldenBeanMallPreloading) {
            GoldenBeanMallEX.refreshItemsFromAPI()
        }
    }

    /** 预加载商品列表：开关开启且当日未置旗标才执行，成功才置旗标，空结果下次运行重试 */
    private fun schedulePreloadOnce(
        name: String,
        switch: BooleanModelField,
        flagKey: String,
        loadingFlag: AtomicBoolean,
        refresh: () -> List<MemberBenefit>,
    ) {
        if (!switch.value || Status.hasFlagToday(flagKey) || !loadingFlag.compareAndSet(false, true)) {
            return
        }
        TASK_EXECUTOR.submit {
            try {
                val items = refresh()
                if (items.isNotEmpty()) {
                    Log.other("$TAG ${name}预加载成功: ${items.size} 项")
                    Status.setFlagToday(flagKey)
                } else {
                    Log.other("$TAG ${name}预加载结果为空，下次运行时重试")
                }
            } finally {
                loadingFlag.set(false)
            }
        }
    }

    override fun getName(): String {
        return "兑换任务"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.EXCHANGE
    }

    override fun getIcon(): String {
        return "AntSports.png"
    }

    @SuppressLint("NewApi")
    override fun getFields(): ModelFields {
        val fields = super.getFields() ?: ModelFields()
        fields.addField(enableNeverLand)
        fields.addField(enableNeverLandList)
        fields.addField(neverLandList)
        fields.addField(enablePackageExchange)
        fields.addField(packageExchangeList)
        fields.addField(enableGameCenterGold)
        fields.addField(enableGameCenterGoldList)
        fields.addField(gameCenterGoldList)
        fields.addField(gameCenterGoldTargetHour)
        fields.addField(enableGoldenBeanMall)
        fields.addField(goldenBeanMallList)
        fields.addField(goldenBeanMallTargetHour)
        return fields
    }

    override fun runJava() {
        if (!check()) return

        // 同步最新配置到子实例，并按需触发预加载
        neverLandEX.neverLandList = neverLandList
        packageExchangeEX.packageExchangeList = packageExchangeList
        gameCenterGoldEX.gameCenterGoldList = gameCenterGoldList
        gameCenterGoldEX.targetHourField = gameCenterGoldTargetHour
        goldenBeanMallEX.goldenBeanMallList = goldenBeanMallList
        goldenBeanMallEX.targetHourField = goldenBeanMallTargetHour
        schedulePreloadIfNeeded()

        if (enableNeverLand.value) submitSubTask("健康岛", neverLandEX)
        if (enablePackageExchange.value) submitSubTask("包裹兑换", packageExchangeEX)
        if (enableGameCenterGold.value) submitSubTask("游戏金币秒杀", gameCenterGoldEX)
        if (enableGoldenBeanMall.value) submitSubTask("金豆商城秒杀", goldenBeanMallEX)
    }

    /**
     * 提交子任务：Future 查重防重复提交，运行结束自动移除记录
     */
    private fun submitSubTask(name: String, task: BaseFlashSaleTask) {
        synchronized(subTaskFutures) {
            if (isTaskRunning(subTaskFutures[name])) {
                Log.runtime("[FlashSaleModule🚀]${name}任务已在运行中，跳过重复提交")
                return
            }

            try {
                subTaskFutures[name] = TASK_EXECUTOR.submit {
                    try {
                        task.prepare()
                        task.asyncRun()
                    } catch (e: Exception) {
                        Log.error("[FlashSaleModule🚀]${name}任务异常", e.message)
                    } finally {
                        synchronized(subTaskFutures) { subTaskFutures.remove(name) }
                    }
                }
            } catch (e: Exception) {
                Log.error("[FlashSaleModule🚀]${name}任务提交异常", e.message)
            }
        }
    }

    /**
     * 检查任务是否正在运行
     */
    private fun isTaskRunning(taskFuture: Future<*>?): Boolean {
        return taskFuture != null && !taskFuture.isDone && !taskFuture.isCancelled
    }

    override val exchangeMode: ExchangeMode?
        get() = null

    override val completedKey: String
        get() = "FlashSaleModule"

    override fun tryExchange(item: ExchangeItem): Boolean {
        return false
    }

    override fun sendExchangeRequest(params: JSONArray, item: ExchangeItem): Boolean {
        return false
    }
}
