package fansirsqi.xposed.sesame.task.exchange

import android.annotation.SuppressLint
import fansirsqi.xposed.sesame.data.Status
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
    private val enablePrivilege = BooleanModelField("enablePrivilege", "青春特权兑换-大额", false)
    private val enablePrivilegeSmall = BooleanModelField("enablePrivilegeSmall", "青春特权兑换-小额", false)
    private val enablePrivilegeList = BooleanModelField("enablePrivilegeList", "青春特权列表方式", false)
    private val youthPrivilegeList = SelectModelField(
        "youthPrivilegeList",
        "青春特权 | 权益列表",
        linkedSetOf(),
        PrivilegeEX::getExchangeItemListForUI
    )
    private val enablePackageExchange = BooleanModelField("enablePackageExchange", "包裹兑换", false)
    private val packageExchangeList = SelectModelField(
        "packageExchangeList",
        "包裹兑换 | 商品列表",
        linkedSetOf(),
        PackageExchangeEX::getExchangeItemListForUI
    )
    private val wakeUpMinuteBefore = IntegerModelField("wakeUpMinuteBefore", "唤醒提前时间(分钟)", 2, 1, 30)
    private val enableConcurrent = BooleanModelField("enableConcurrent", "启用并发兑换", false)

    override fun isConcurrentMode(): Boolean {
        return enableConcurrent.value
    }

    // 子任务实例
    private val neverLandEX = NeverLandEX()
    // 大额和小额各用独立实例，避免并发执行时共享 AtomicBoolean/AtomicReference 状态相互干扰
    protected val privilegeEX = PrivilegeEX()       // 青春特权大额（10点）
    protected val privilegeSmallEX = PrivilegeEX()  // 青春特权小额（0点）
    protected val packageExchangeEX = PackageExchangeEX()

    // 每个子任务的执行状态控制
    @Volatile
    private var privilegeTaskFuture: Future<*>? = null
    @Volatile
    private var privilegeSmallTaskFuture: Future<*>? = null
    @Volatile
    private var neverLandTaskFuture: Future<*>? = null
    @Volatile
    private var packageExchangeTaskFuture: Future<*>? = null

    companion object {
        private val TASK_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(10)

        private const val FLASH_SALE_LIST = "flash_sale_list"
        private const val NEVERLAND_LIST = "neverland_list"
        private const val BAOGUO_LIST = "baoguo_list"

        // 防止重复提交预加载任务
        private val isPreloading = AtomicBoolean(false)
        private val isNeverLandPreloading = AtomicBoolean(false)
        private val isBaoguoPreloading = AtomicBoolean(false)
    }

    override fun getWakeUpConfigField(): IntegerModelField? {
        return null
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
        neverLandEX.wakeUpMinuteBefore = wakeUpMinuteBefore
        neverLandEX.neverLandList = neverLandList

        // 青春特权大额
        privilegeEX.isSmallExchange = false
        privilegeEX.privilege = enablePrivilege
        privilegeEX.privilegeSmall = enablePrivilegeSmall
        privilegeEX.wakeUpMinuteBefore = wakeUpMinuteBefore
        privilegeEX.enablePrivilegeList = enablePrivilegeList
        privilegeEX.youthPrivilegeList = youthPrivilegeList

        // 青春特权小额（共享配置字段，运行状态独立）
        privilegeSmallEX.isSmallExchange = true
        privilegeSmallEX.privilege = enablePrivilege
        privilegeSmallEX.privilegeSmall = enablePrivilegeSmall
        privilegeSmallEX.wakeUpMinuteBefore = wakeUpMinuteBefore
        privilegeSmallEX.enablePrivilegeList = enablePrivilegeList
        privilegeSmallEX.youthPrivilegeList = youthPrivilegeList

        // 包裹兑换
        packageExchangeEX.enablePackageExchange = enablePackageExchange
        packageExchangeEX.wakeUpMinuteBefore = wakeUpMinuteBefore
        packageExchangeEX.packageExchangeList = packageExchangeList
    }

    /**
     * 检查并调度预加载任务（一天执行一次）
     * enablePrivilegeList 默认 false，此检查同时防止 init{} 阶段 Status 未就绪导致崩溃
     */
    private fun schedulePreloadIfNeeded() {
        // 青春特权列表预加载
        if (enablePrivilegeList.value &&
            !Status.hasFlagToday(FLASH_SALE_LIST) &&
            isPreloading.compareAndSet(false, true)) {
            TASK_EXECUTOR.submit {
                try {
                    if (performPreload()) Status.setFlagToday(FLASH_SALE_LIST)
                } finally {
                    isPreloading.set(false)
                }
            }
        }
        // 健康岛商品列表预加载
        if (enableNeverLandList.value &&
            !Status.hasFlagToday(NEVERLAND_LIST) &&
            isNeverLandPreloading.compareAndSet(false, true)) {
            TASK_EXECUTOR.submit {
                try {
                    val items = NeverLandEX.refreshItemsFromAPI()
                    if (items.isNotEmpty()) {
                        Log.other("$TAG 健康岛预加载成功: ${items.size} 项")
                        Status.setFlagToday(NEVERLAND_LIST)
                    } else {
                        Log.other("$TAG 健康岛预加载结果为空，下次运行时重试")
                    }
                } finally {
                    isNeverLandPreloading.set(false)
                }
            }
        }
        // 包裹兑换商品列表预加载
        if (enablePackageExchange.value &&
            !Status.hasFlagToday(BAOGUO_LIST) &&
            isBaoguoPreloading.compareAndSet(false, true)) {
            TASK_EXECUTOR.submit {
                try {
                    val items = PackageExchangeEX.refreshItemsFromAPI()
                    if (items.isNotEmpty()) {
                        Log.other("$TAG 包裹兑换列表预加载成功: ${items.size} 项")
                        Status.setFlagToday(BAOGUO_LIST)
                    } else {
                        Log.other("$TAG 包裹兑换列表预加载结果为空，下次运行时重试")
                    }
                } finally {
                    isBaoguoPreloading.set(false)
                }
            }
        }
    }

    /**
     * 执行实际的预加载操作（在后台线程调用）
     * 直接调用 refreshExchangeItemsFromAPI()，因为 getExchangeItemListForUI() 已改为只读缓存
     */
    private fun performPreload(): Boolean {
        try {
            val items = PrivilegeEX.refreshExchangeItemsFromAPI()
            if (items.isNotEmpty()) {
                Log.other("$TAG 青村特权成功预加载 ${items.size} 个兑换项")
                return true
            } else {
                Log.other("$TAG 预加载兑换项列表为空，可能需要检查网络连接，请重试。")
            }
        } catch (e: Exception) {
            Log.error("$TAG 预加载兑换项列表失败: ${e.message}")
        }
        return false
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
        fields.addField(enablePrivilege)
        fields.addField(enablePrivilegeSmall)
        fields.addField(enablePrivilegeList)
        fields.addField(youthPrivilegeList)
        fields.addField(enablePackageExchange)
        fields.addField(packageExchangeList)
        fields.addField(wakeUpMinuteBefore)
        fields.addField(enableConcurrent)
        return fields
    }

    override fun check(): Boolean {
        return super.check()
    }

    override fun runJava() {
        if (!check()) return

        // 同步最新配置到子实例，并按需触发预加载
        neverLandEX.neverLandList = neverLandList
        privilegeEX.youthPrivilegeList = youthPrivilegeList
        privilegeEX.enablePrivilegeList = enablePrivilegeList
        privilegeSmallEX.youthPrivilegeList = youthPrivilegeList
        privilegeSmallEX.enablePrivilegeList = enablePrivilegeList
        packageExchangeEX.packageExchangeList = packageExchangeList
        schedulePreloadIfNeeded()

        if (enablePrivilege.value) submitPrivilegeTask()
        if (enablePrivilegeSmall.value) submitPrivilegeSmallTask()
        if (enableNeverLand.value) submitNeverLandTask()
        if (enablePackageExchange.value) submitPackageExchangeTask()
    }

    /**
     * 检查任务是否正在运行
     */
    private fun isTaskRunning(taskFuture: Future<*>?): Boolean {
        return taskFuture != null && !taskFuture.isDone && !taskFuture.isCancelled
    }

    /**
     * 提交青春特权大额任务（早上10点）
     */
    private fun submitPrivilegeTask() {
        if (isTaskRunning(privilegeTaskFuture)) {
            Log.runtime("[FlashSaleModule🚀]青春特权大额任务已在运行中，跳过重复提交")
            return
        }

        try {
            privilegeTaskFuture = TASK_EXECUTOR.submit {
                try {
                    privilegeEX.prepare()
                    privilegeEX.asyncRun()
                } catch (e: Exception) {
                    Log.error("[FlashSaleModule🚀]青春特权大额任务异常", e.message)
                } finally {
                    privilegeTaskFuture = null
                }
            }
        } catch (e: Exception) {
            Log.error("[FlashSaleModule🚀]青春特权大额任务提交异常", e.message)
        }
    }

    /**
     * 提交青春特权小额任务（晚上0点）
     * 使用独立的 privilegeSmallEX 实例，避免与大额任务共用状态相互干扰
     */
    private fun submitPrivilegeSmallTask() {
        if (isTaskRunning(privilegeSmallTaskFuture)) {
            Log.runtime("[FlashSaleModule🚀]青春特权小额任务已在运行中，跳过重复提交")
            return
        }

        try {
            privilegeSmallTaskFuture = TASK_EXECUTOR.submit {
                try {
                    privilegeSmallEX.prepare()
                    privilegeSmallEX.asyncRun()
                } catch (e: Exception) {
                    Log.error("[FlashSaleModule🚀]青春特权小额任务异常", e.message)
                } finally {
                    privilegeSmallTaskFuture = null
                }
            }
        } catch (e: Exception) {
            Log.error("[FlashSaleModule🚀]青春特权小额任务提交异常", e.message)
        }
    }

    /**
     * 提交健康岛任务
     */
    private fun submitNeverLandTask() {
        if (isTaskRunning(neverLandTaskFuture)) {
            Log.runtime("[FlashSaleModule🚀]健康岛任务已在运行中，跳过重复提交")
            return
        }

        try {
            neverLandTaskFuture = TASK_EXECUTOR.submit {
                try {
                    neverLandEX.prepare()
                    neverLandEX.asyncRun()
                } catch (e: Exception) {
                    Log.error("[FlashSaleModule🚀]健康岛任务异常", e.message)
                } finally {
                    neverLandTaskFuture = null
                }
            }
        } catch (e: Exception) {
            Log.error("[FlashSaleModule🚀]健康岛任务提交异常", e.message)
        }
    }

    /**
     * 提交包裹兑换任务（0点, 10点, 18点）
     */
    private fun submitPackageExchangeTask() {
        if (isTaskRunning(packageExchangeTaskFuture)) {
            Log.runtime("[FlashSaleModule🚀]包裹兑换任务已在运行中，跳过重复提交")
            return
        }

        try {
            packageExchangeTaskFuture = TASK_EXECUTOR.submit {
                try {
                    packageExchangeEX.prepare()
                    packageExchangeEX.asyncRun()
                } catch (e: Exception) {
                    Log.error("[FlashSaleModule🚀]包裹兑换任务异常", e.message)
                } finally {
                    packageExchangeTaskFuture = null
                }
            }
        } catch (e: Exception) {
            Log.error("[FlashSaleModule🚀]包裹兑换任务提交异常", e.message)
        }
    }

    override fun sendExchangeRequestAsync(params: JSONArray, item: ExchangeItem): Boolean {
        return false
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
