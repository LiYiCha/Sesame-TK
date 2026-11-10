package fansirsqi.xposed.sesame.task.other

import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.other.haojia.HaoJiaWuyou
import fansirsqi.xposed.sesame.task.other.wufu.WuFu2026
import fansirsqi.xposed.sesame.util.Log

class OtherTask0 : ModelTask() {
    override fun getName(): String = "其他任务0"

    override fun getGroup(): ModelGroup = ModelGroup.OTHER

    override fun getIcon(): String = ""

    /** @brief 好家无忧卡 开关 */
    private var haojiaWuyou: BooleanModelField? = null

    /** @brief 五福2026 开关 */
    private var wufu2026: BooleanModelField? = null

    override fun getFields(): ModelFields {
        val fields = ModelFields()
        fields.addField(
            BooleanModelField(
                "haojiaWuyou", "好家无忧卡", false
            ).apply { haojiaWuyou = this }
        )
        fields.addField(
            BooleanModelField(
                "wufu2026", "五福2026", false
            ).apply { wufu2026 = this }
        )

        // 统一小游戏HTTP上报与开宝箱控制开关
        fields.addField(gameForest)
        fields.addField(gameFarm)
        fields.addField(gameOrchard)
        fields.addField(gameGoldenBean)

        return fields
    }

    override suspend fun runSuspend() {
        try {
            if (haojiaWuyou?.value == true) {
                HaoJiaWuyou.start()
            }
            if (wufu2026?.value == true) {
                WuFu2026.start()
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    companion object {
        const val TAG = "OtherTask0"

        @JvmField
        val gameForest = BooleanModelField("gameForest", "森林乐园小车车 (自动开宝箱)", false)

        @JvmField
        val gameFarm = BooleanModelField("gameFarm", "庄园乐园对对碰 (自动开宝箱)", false)

        @JvmField
        val gameOrchard = BooleanModelField("gameOrchard", "农场乐园上车车 (自动砸金蛋)", false)

        @JvmField
        val gameGoldenBean = BooleanModelField("gameGoldenBean", "金豆夺宝小游戏 (自动砸金蛋)", false)
    }
}
