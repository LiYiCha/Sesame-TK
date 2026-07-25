package fansirsqi.xposed.sesame.task.other

import fansirsqi.xposed.sesame.entity.OtherEntityProvider.listCreditOptions
import fansirsqi.xposed.sesame.entity.OtherEntityProvider.listCreditTaskOptions
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectAndCountModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.model.modelFieldExt.ChoiceModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.task.other.credit2101.Credit2101
import fansirsqi.xposed.sesame.task.other.haojia.HaoJiaWuyou
import fansirsqi.xposed.sesame.task.other.wufu.WuFu2026
import fansirsqi.xposed.sesame.task.other.gametest.GameTestTask
import fansirsqi.xposed.sesame.util.Log

class OtherTask0 : ModelTask() {
    override fun getName(): String {
        return "其他任务0"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.OTHER
    }

    override fun getIcon(): String {
        return ""
    }

    /** @brief 信用2101 游戏开关 */
    private var credit2101: BooleanModelField? = null

    /** @brief 信用2101 事件列表 */
    private var creditEventOptions: SelectAndCountModelField? = null

    /** @brief 信用2101 任务列表 */
    private var credit2101OTaskOptions: SelectModelField? = null


    /** @brief 好家无忧卡 开关 */
    private var haojiaWuyou: BooleanModelField? = null

    /** @brief 五福2026 开关 */
    private var wufu2026: BooleanModelField? = null

    /** @brief 游戏测试 开关 */
    private var testGameTask: BooleanModelField? = null

    /** @brief 游戏测试 类型选择 */
    private var testGameSelect: ChoiceModelField? = null

    /** @brief 游戏测试 上报蛋数 */
    private var testGameEggCount: IntegerModelField? = null


    override fun getFields(): ModelFields {
        val fields = ModelFields()
        fields.addField(
            BooleanModelField(
                "credit2101", "信用2101", false
            ).apply { credit2101 = this })

        fields.addField(
            SelectModelField(
                "credit2101Options",
                "信用2101 | 任务选项",
                LinkedHashSet<String?>(),
                listCreditTaskOptions()
            ).also { credit2101OTaskOptions = it })

        fields.addField(
            SelectAndCountModelField(
                "CreditOptions",
                "信用2101 | 事件类型",
                LinkedHashMap<String?, Int?>(),
                listCreditOptions(),
                "设置运行次数(-1为不限制)"
            ).also {
                creditEventOptions = it
            })

        // 新增好家无忧卡开关
        fields.addField(
            BooleanModelField(
                "haojiaWuyou", "好家无忧卡", false
            ).apply { haojiaWuyou = this }
        )

        // 新增五福2026开关
        fields.addField(
            BooleanModelField(
                "wufu2026", "五福2026", false
            ).apply { wufu2026 = this }
        )

        // 手动游戏 HTTP 上报测试控制选项
        fields.addField(
            BooleanModelField(
                "testGameTask", "手动测试 | 游戏HTTP上报", false
            ).apply { testGameTask = this }
        )
        fields.addField(
            ChoiceModelField(
                "testGameSelect", "测试游戏 | 选择目标类型", 0,
                arrayOf(
                    "金豆对对碰 (zfb_ddply)",
                    "金豆吃草草 (zfb_nccmx)",
                    "会员对对碰 (xlyy_WJCNJT)",
                    "庄园对对碰 (zhuangyuan)",
                    "农场上车车 (ncscc)",
                    "森林小车车 (slxcc)",
                    "森林救援队 (sljyd)"
                ),
                "选择要手动单步测试上报的小游戏"
            ).apply { testGameSelect = this }
        )
        fields.addField(
            IntegerModelField(
                "testGameEggCount", "测试游戏 | 手动上报蛋数", 1
            ).apply { testGameEggCount = this }
        )

        return fields
    }

    override suspend fun runSuspend() {
        try {
            if (credit2101!!.value) {
                Credit2101.doCredit2101(credit2101OTaskOptions!!,creditEventOptions!!)
            }
            // 执行好家无忧卡任务
            if (haojiaWuyou!!.value) {
                HaoJiaWuyou.start()
            }

            // 2026五福
            if(wufu2026!!.value){
                WuFu2026.start()
            }

            // 手动游戏 HTTP 上报测试
            if (testGameTask?.value == true) {
                GameTestTask.start(testGameTask, testGameSelect, testGameEggCount)
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    companion object {
        const val TAG = "OtherTask"
        fun run() {
            // TODO: 添加其他任务
        }
    }
}