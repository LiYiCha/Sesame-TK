package fansirsqi.xposed.sesame.task.antOrchard

import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.RandomUtil
import org.json.JSONArray
import org.json.JSONObject

object AntOrchardRpcCall {
    private const val VERSION = "20260721.01"

    fun orchardIndex(): String {
        return RequestManager.requestString("com.alipay.antfarm.orchardIndex",
            "[{\"inHomepage\":\"true\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\""
                    + VERSION + "\"}]");
    }

    /**
     * 获取额外信息（包含每日肥料、施肥礼盒）
     * @param from 来源：entry(首页), water(施肥后)
     */
    fun extraInfoGet(from: String = "entry"): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.extraInfoGet",
            "[{\"from\":\"$from\",\"requestType\":\"NORMAL\",\"sceneCode\":\"FUGUO\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\"$VERSION\"}]"
        )
    }

    fun extraInfoSet(): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.extraInfoSet",
            "[{\"bizCode\":\"fertilizerPacket\",\"bizParam\":{\"action\":\"queryCollectFertilizerPacket\"},\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\"$VERSION\"}]"
        )
    }

    // 修改：增加 LIMITED_TIME_CHALLENGE 和 LOTTERY_PLUS 类型
    fun querySubplotsActivity(treeLevel: String): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.querySubplotsActivity",
            "[{\"activityType\":[\"WISH\",\"BATTLE\",\"HELP_FARMER\",\"DEFOLIATION\",\"CAMP_TAKEOVER\",\"LIMITED_TIME_CHALLENGE\",\"LOTTERY_PLUS\"],\"inHomepage\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"treeLevel\":\"$treeLevel\",\"version\":\"$VERSION\"}]"
        )
    }

    fun triggerSubplotsActivity(activityId: String, activityType: String, optionKey: String): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.triggerSubplotsActivity",
            "[{\"activityId\":\"$activityId\",\"activityType\":\"$activityType\",\"optionKey\":\"$optionKey\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\"$VERSION\"}]"
        )
    }

    fun receiveOrchardRights(activityId: String, activityType: String): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.receiveOrchardRights",
            "[{\"activityId\":\"$activityId\",\"activityType\":\"$activityType\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\"$VERSION\"}]"
        )
    }

    /* 七日礼包 */
    fun drawLottery(): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.drawLottery",
            "[{\"lotteryScene\":\"receiveLotteryPlus\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\"$VERSION\"}]"
        )
    }

    /**
     * 切换种植场景
     * @param plantScene main(果树) 或 yeb(摇钱树)
     */
    fun switchPlantScene(plantScene: String): String {
        return RequestManager.requestString(
            "com.alipay.antorchard.switchPlantScene",
            "[{\"plantScene\":\"$plantScene\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\"$VERSION\"}]"
        )
    }

    /**
     * 施肥
     * @param wua 用户标识
     * @param source 来源标识，可自定义
     * @param useBatchSpread 一键5次
     * @param plantScene 场景：main 或 yeb
     */
    fun orchardSpreadManure(wua: String, source: String, useBatchSpread: Boolean = false, plantScene: String = "main"): String {
        return RequestManager.requestString(
            "com.alipay.antfarm.orchardSpreadManure",
            "[{\"plantScene\":\"$plantScene\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"$source\",\"useBatchSpread\":$useBatchSpread,\"version\":\"$VERSION\",\"wua\":\"$wua\"}]"
        )
    }

    fun receiveTaskAward(sceneCode: String, taskType: String): String {
        return RequestManager.requestString(
            "com.alipay.antiep.receiveTaskAward",
            "[{\"ignoreLimit\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"$sceneCode\",\"source\":\"ch_alipaysearch__chsub_normal\",\"taskType\":\"$taskType\",\"version\":\"$VERSION\"}]"
        )
    }

    fun orchardListTask(): String {
        return RequestManager.requestString(
            "com.alipay.antfarm.orchardListTask",
            "[{\"addWidget\":false,\"appMode\":\"normal\",\"enableSwitchSceneList\":[\"main\",\"yeb\"],\"enableTeamType\":[\"help\",\"team\"],\"hasYebActivityEntrance\":true,\"plantHiddenMMC\":\"false\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\"$VERSION\"}]"
        )
    }

    fun orchardSign(): String {
        return RequestManager.requestString(
            "com.alipay.antfarm.orchardSign",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"signScene\":\"ANTFARM_ORCHARD_SIGN_V2\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\"$VERSION\"}]"
        )
    }

    /**
     * 农场去到金豆首页领取100金豆
     */
    fun orchardToGoldenBeanIndex(): String{
        return RequestManager.requestString(
            "com.alipay.goldenbean.index",
            "[{\"bizType\":\"MASTER\",\"darwinSceneList\":[\"indexLayoutTwo\",\"indexPreRequestCacheAB\",\"taskFlowHandGuide\"],\"source\":\"babafarm\",\"version\":\"20260803.01\"}]"
        )
    }

    fun finishTask(userId: String, sceneCode: String, taskType: String): String {
        return RequestManager.requestString(
            "com.alipay.antiep.finishTask",
            "[{\"outBizNo\":\"${userId}${System.currentTimeMillis()}\",\"requestType\":\"NORMAL\",\"sceneCode\":\"$sceneCode\",\"source\":\"ch_appcenter__chsub_commonapp\",\"taskType\":\"$taskType\",\"userId\":\"$userId\",\"version\":\"$VERSION\"}]"
        )
    }

    fun finishTaskH5(sceneCode: String, taskType: String): String {
        val outBizNo = "${taskType}_${System.currentTimeMillis()}_${RandomUtil.getRandomString(8)}"
        return RequestManager.requestString(
            "com.alipay.antiep.finishTask",
            "[{\"outBizNo\":\"$outBizNo\",\"requestType\":\"H5\",\"sceneCode\":\"$sceneCode\",\"source\":\"H5\",\"taskType\":\"$taskType\"}]"
        )
    }

    fun submitUserPlayDurationAction(gameAppId: String, playTime: Int = 30, source: String = "bbnc_mc_xbioen04"): String {
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction",
            "[{\"gameAppId\":\"$gameAppId\",\"playTime\":$playTime,\"source\":\"$source\",\"statisticTag\":\"\"}]"
        )
    }

    fun submitGameEvent(appId: String, eventCode: String = "GAME_PLAY_TIME", playDuration: Int = 30): String {
        val extInfo = JSONObject().put("playDuration", playDuration)
        val data = JSONArray().put(
            JSONObject()
                .put("appId", appId)
                .put("eventCode", eventCode)
                .put("bizScene", "ANTFARM_ORCHARD")
                .put("extInfo", extInfo)
        ).toString()
        return RequestManager.requestString("com.alipay.gameevent.biz.rpc.submitEvent", data)
    }

    fun triggerTbTask(taskId: String, taskPlantType: String = "ANTIEP", source: String = "ch_appcenter__chsub_commonapp"): String {
        return RequestManager.requestString(
            "com.alipay.antfarm.triggerTbTask",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"$source\",\"taskId\":\"$taskId\",\"taskPlantType\":\"$taskPlantType\",\"version\":\"$VERSION\"}]"
        )
    }

    fun queryOptionalPlay(): String {
        val data = """
            [{
                "bizType":"ANTORCHARD",
                "commonDegradeFilterRequest":{
                    "deviceLevel":"high",
                    "platform":"Android",
                    "unityDeviceLevel":"high"
                },
                "playTypeList":["TOP_UP_COUPON","TASK_TRIGGER"],
                "requestType":"RPC",
                "sceneCode":"ORCHARD",
                "source":"H5",
                "version":"10.8.50"
            }]
        """.trimIndent()
        return RequestManager.requestString("com.alipay.charitygamecenter.queryOptionalPlay", data)
    }

    /**
     * 查询浮动球试玩任务（FLOATING_BALL）的梯队配置。
     * 返回 floatingBallPlayInfo.taskList[].multiStageVisitFloatBallParams，
     * 各梯队 timeCount 依次为 0/15/15/30/60/60/60/300（以服务端实际返回为准）。
     */
    fun queryOptionalPlayFloatingBall(gameAppId: String, chInfo: String): String {
        val data = """
            [{
                "chinfo":"$chInfo",
                "commonDegradeFilterRequest":{
                    "appMode":"normal",
                    "deviceLevel":"high",
                    "unityDeviceLevel":"high"
                },
                "currentGameAppId":"$gameAppId",
                "playTypeList":["FLOATING_BALL"],
                "requestType":"PRC",
                "sceneCode":"FLOATING_BALL_$chInfo",
                "source":"H5",
                "version":"1.0"
            }]
        """.trimIndent()
        return RequestManager.requestString("com.alipay.charitygamecenter.queryOptionalPlay", data)
    }

    fun finishTaskLeyuan(taskType: String, sceneCode: String, outBizNo: String): String {
        val data = """
            [{
                "outBizNo": "$outBizNo",
                "requestType": "RPC",
                "sceneCode": "$sceneCode",
                "source": "ADBASICLIB",
                "taskType": "$taskType"
            }]
        """.trimIndent()
        return RequestManager.requestString("com.alipay.antiep.finishTask", data)
    }

    fun receiveTaskAwardantorchard(taskType: String, awardCount: Int): String {
        val data = """
            [{
                "awardCountForReceive": $awardCount,
                "ignoreLimit": true,
                "requestType": "RPC",
                "sceneCode": "ANTORCHARD_LEYUAN_DAILY_TASK",
                "source": "antorchard",
                "taskType": "$taskType"
            }]
        """.trimIndent()
        return RequestManager.requestString("com.alipay.antieptask.receiveTaskAwardantorchard", data)
    }

    //砸蛋
    fun smashedGoldenEgg(count: Int): String {
        val jsonArgs = """
        [
            {
                "batchSmashCount": $count,
                "requestType": "NORMAL",
                "sceneCode": "ORCHARD",
                "source": "ch_appcenter__chsub_commonapp",
                "version": "$VERSION"
            }
        ]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.smashedGoldenEgg",
            jsonArgs
        )
    }

    /**
     * 收取果园回访奖励
     * @param diversionSource 引流来源（如：widget、tmall）
     * @param source 具体来源（如：widget_shoufei、upgrade_tmall_exchange_task）
     * @return 请求结果字符串
     */
    fun receiveOrchardVisitAward(
        diversionSource: String,
        source: String
    ): String {
        val requestParams = """
        [{"diversionSource":"$diversionSource",
          "requestType":"NORMAL",
          "sceneCode":"ORCHARD",
          "source":"$source",
          "version":"$VERSION"}]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.receiveOrchardVisitAward",
            requestParams
        )
    }

    fun orchardSyncIndex(Wua: String): String {
        val jsonArgs = """
         [{
             "requestType": "NORMAL",
             "sceneCode": "ORCHARD",
             "source": "ch_appcenter__chsub_commonapp",
             "syncIndexTypes": "LIMITED_TIME_CHALLENGE",
             "useWua": true,
             "version": "$VERSION",
             "wua": "$Wua"
         }]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.orchardSyncIndex",
            jsonArgs
        )
    }

    /**
     * 限时挑战同步索引（commonapp 渠道版，供限时挑战含 GAME_CENTER 玩游戏子任务使用）。
     * 参数对齐支付宝 H5 页面实际请求（含 appMode，不含 useWua）。
     */
    fun orchardSyncIndexCommonApp(Wua: String): String {
        val jsonArgs = """
         [{
             "appMode": "normal",
             "requestType": "NORMAL",
             "sceneCode": "ORCHARD",
             "source": "ch_appcenter__chsub_commonapp",
             "syncIndexTypes": "LIMITED_TIME_CHALLENGE",
             "version": "$VERSION",
             "wua": "$Wua"
         }]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.orchardSyncIndex",
            jsonArgs
        )
    }

    fun noticeGame(appId: String): String {
        val jsonArgs = """
          [{
             "appId": "2021004165643274",
             "requestType": "NORMAL",
             "sceneCode": "ORCHARD",
             "source": "ch_appcenter__chsub_commonapp",
             "version": "$VERSION"
         }]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.antorchard.noticeGame",
            jsonArgs
        )
    }

    fun achieveBeShareP2P(shareId: String): String {
        return RequestManager.requestString(
            "com.alipay.antiep.achieveBeShareP2P",
            "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM_ORCHARD_SHARE_P2P\",\"shareId\":\"$shareId\",\"source\":\"share\",\"version\":\"$VERSION\"}]"
        )
    }

    /* 摇钱树收余额奖励 */
    fun moneyTreeTrigger(): String {
        return RequestManager.requestString(
            "com.alipay.yebbffweb.needle.yebHome.moneyTree.trigger",
            "[{\"sceneType\":\"default\",\"type\":\"trigger\"}]"
        )
    }
    fun newQueryGameCenter(): String {
        val method = "com.alipay.antorchard.queryGameCenter";
        val params = "[{\"queryGameCenterTheme\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_commonapp\",\"version\":\""+VERSION+"\"}]";
        return RequestManager.requestString(method, params);
    }

    fun queryCallAppSchema(sceneCode: String): String {
        return RequestManager.requestString(
            "alipay.antmember.callApp.queryCallAppSchema",
            "[{\"direct\":\"OUT\",\"sceneCode\":\"$sceneCode\"}]"
        )
    }

    fun rouseRuleCheck(
        appIdSource: String,
        extInfo: String,
        operate: String,
        originalUrl: String,
        targetUrl: String,
        urlSource: String
    ): String {
        try {
            val ja = org.json.JSONArray()
            val jo = org.json.JSONObject()
            jo.put("appIdSource", appIdSource)
            jo.put("extInfo", extInfo)
            jo.put("operate", operate)
            jo.put("originalUrl", originalUrl)
            jo.put("targetUrl", targetUrl)
            jo.put("urlSource", urlSource)
            ja.put(jo)
            return RequestManager.requestString(
                "alipay.antstarship.appdownload.rouse.rule.check",
                ja.toString()
            )
        } catch (e: Exception) {
            return ""
        }
    }
}