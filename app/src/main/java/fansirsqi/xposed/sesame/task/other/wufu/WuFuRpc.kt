package fansirsqi.xposed.sesame.task.other.wufu

import fansirsqi.xposed.sesame.hook.RequestManager

class WuFuRpc {

    // 查询套马次数
    fun queryGameInfo(): String{
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.gameplay.lasso.queryMainPage",
            "[{\"currentStep\":1,\"deviceLevel\":\"high\",\"p2eFullScreen\":false," +
                    "\"source\":\"cy26wfzhc_mc_xadoni21\",\"unityDeviceLevel\":\"high\"}]")
    }

    // 套福袋游戏开局
    fun startGame(): String{
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.gameplay.lasso.startGame",
            "[{\"currentStep\":1}]")
    }

    // 套马预提交
    fun preCommit(roundId: String): String{
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.gameplay.lasso.preSettleGame",
            "[{\"currentStep\":1,\"deviceLevel\":\"high\"," +
                    "\"extraMultiplePrizeItemNum\":1,\"extraPrizeItemNum\":0,\"extraTimeItemNum\":0," +
                    "\"highLevelSuccessHitNum\":8,\"highLevelSuccessPullNum\":8,\"lowLevelSuccessHitNum\":1," +
                    "\"lowLevelSuccessPullNum\":1,\"middleLevelSuccessHitNum\":5,\"middleLevelSuccessPullNum\":5," +
                    "\"operateNum\":19,\"prizeAmount\":370,\"rewardDoubleNum\":0," +
                    "\"roundId\":\"$roundId\",\"unityDeviceLevel\":\"high\"}]")
    }

    // 套马最终提交
    fun finishCommit(roundId: String): String{
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.gameplay.lasso.settleGame",
            "[{\"currentStep\":1,\"deviceLevel\":\"high\"," +
                    "\"extraMultiplePrizeItemNum\":0,\"extraPrizeItemNum\":0,\"extraTimeItemNum\":0," +
                    "\"highLevelSuccessHitNum\":8,\"highLevelSuccessPullNum\":8,\"lowLevelSuccessHitNum\":1," +
                    "\"lowLevelSuccessPullNum\":1,\"middleLevelSuccessHitNum\":5,\"middleLevelSuccessPullNum\":5," +
                    "\"operateNum\":19,\"prizeAmount\":370,\"rewardDoubleNum\":0," +
                    "\"roundId\":\"$roundId\",\"unityDeviceLevel\":\"high\"}]")
    }

    // 查询主任务列表
    fun queryMainTaskList(): String{
        return RequestManager.requestString("com.alipay.wufumain.biz.wufu2026.queryMainTaskPanel",
            "[{\"screenReaderVersion\":false}]")
    }
    // 主任务完成和领取奖励
    fun mainTaskFinish(scene: String,taskId: String): String{
        return RequestManager.requestString("com.alipay.wufumain.biz.wufu2026.receiveTaskPrize",
            "[{\"commercial\":false,\"scene\":\"$scene\",\"taskId\":\"$taskId\"}]")
    }
}