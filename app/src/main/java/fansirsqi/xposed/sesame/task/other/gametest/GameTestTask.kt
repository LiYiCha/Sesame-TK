package fansirsqi.xposed.sesame.task.other.gametest

import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.ChoiceModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.task.antOrchard.GameTask
import fansirsqi.xposed.sesame.util.Log

object GameTestTask {
    private const val TAG = "OtherTask"

    suspend fun start(
        testGameTask: BooleanModelField?,
        testGameSelect: ChoiceModelField?,
        testGameEggCount: IntegerModelField?
    ) {
        if (testGameTask?.value != true) return
        val gameIdx = testGameSelect?.value ?: 0
        var eggCount = testGameEggCount?.value ?: 1
        if (eggCount <= 0) eggCount = 1

        val targetGame = when (gameIdx) {
            0 -> GameTask.GoldenBean_ddply
            1 -> GameTask.GoldenBean_nccmx
            2 -> GameTask.Member_ddply
            3 -> GameTask.Farm_ddply
            4 -> GameTask.Orchard_ncscc
            5 -> GameTask.Forest_slxcc
            6 -> GameTask.Forest_sljyd
            else -> GameTask.GoldenBean_ddply
        }

        Log.other(TAG, "🧪 收到手动测试指令，开始测试游戏【${targetGame.title}】，目标蛋数: $eggCount")
        try {
            targetGame.report(eggCount)
            Log.other(TAG, "✅ 游戏【${targetGame.title}】手动测试执行结束")
        } catch (e: Exception) {
            Log.error(TAG, "❌ 游戏【${targetGame.title}】手动测试异常: $e")
        } finally {
            testGameTask?.value = false
        }
    }
}
