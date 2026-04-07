//package fansirsqi.xposed.sesame.task.antOrchard;
//import android.annotation.SuppressLint;
//
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import fansirsqi.xposed.sesame.hook.RequestManager;
//
//public class AntOrchardRpcCall {
//    private static final String VERSION = "20251209.01";
//    public static String orchardIndex() {
//        return RequestManager.requestString("com.alipay.antfarm.orchardIndex",
//                "[{\"inHomepage\":\"true\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    // 能获取到金蛋的请求
//    public static String orchardIndexEgg() {
//        return RequestManager.requestString("com.alipay.antfarm.orchardIndex",
//                "[{\"commonDegradeResult\":{\"deviceLevel\":\"high\",\"resultReason\":0,\"resultType\":0},\"darwinSceneList\":[\"gameListTwoOptimize\",\"hd_mode\",\"yebTreeTalk\",\"transferPopupYebSwitchMainTree\",\"yebLotteryPlus\",\"teamPlantNewStyle\",\"taskDarwGroup2\",\"awardPreviewExp\",\"storage\"],\"growthExtInfo\":\"\",\"growthTask\":\"\",\"inHomepage\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"useWua\":\"\"," +
//                        "\"version\":\""+ VERSION +"\"}]");
//    }
//    public static String mowGrassInfo() {
//        return RequestManager.requestString("com.alipay.antorchard.mowGrassInfo",
//                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"showRanking\":true,\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String batchHireAnimalRecommend(String orchardUserId) {
//        return RequestManager.requestString("com.alipay.antorchard.batchHireAnimalRecommend",
//                "[{\"orchardUserId\":\"" + orchardUserId
//                        + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"sceneType\":\"weed\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String batchHireAnimal(List<String> recommendGroupList) {
//        return RequestManager.requestString("com.alipay.antorchard.batchHireAnimal",
//                "[{\"recommendGroupList\":[" + String.join(",", recommendGroupList)
//                        + "],\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"sceneType\":\"weed\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String extraInfoGet() {
//        return RequestManager.requestString("com.alipay.antorchard.extraInfoGet",
//                "[{\"from\":\"entry\",\"requestType\":\"NORMAL\",\"sceneCode\":\"FUGUO\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String extraInfoSet() {
//        return RequestManager.requestString("com.alipay.antorchard.extraInfoSet",
//                "[{\"bizCode\":\"fertilizerPacket\",\"bizParam\":{\"action\":\"queryCollectFertilizerPacket\"},\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String querySubplotsActivity(String treeLevel) {
//        return RequestManager.requestString("com.alipay.antorchard.querySubplotsActivity",
//                "[{\"activityType\":[\"WISH\",\"BATTLE\",\"HELP_FARMER\",\"DEFOLIATION\",\"CAMP_TAKEOVER\"],\"inHomepage\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"treeLevel\":\""
//                        + treeLevel + "\",\"version\":\"" + VERSION + "\"}]");
//    }
//    public static String triggerSubplotsActivity(String activityId, String activityType, String optionKey) {
//        return RequestManager.requestString("com.alipay.antorchard.triggerSubplotsActivity",
//                "[{\"activityId\":\"" + activityId + "\",\"activityType\":\"" + activityType
//                        + "\",\"optionKey\":\"" + optionKey
//                        + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String receiveOrchardRights(String activityId, String activityType) {
//        return RequestManager.requestString("com.alipay.antorchard.receiveOrchardRights",
//                "[{\"activityId\":\"" + activityId + "\",\"activityType\":\"" + activityType
//                        + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    /* 七日礼包 */
//    public static String drawLottery() {
//        return RequestManager.requestString("com.alipay.antorchard.drawLottery",
//                "[{\"lotteryScene\":\"receiveLotteryPlus\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String orchardSyncIndex() {
//        return RequestManager.requestString("com.alipay.antorchard.orchardSyncIndex",
//                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"syncIndexTypes\":\"QUERY_MAIN_ACCOUNT_INFO\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//
//    public static String orchardSyncIndex(String wua) {
//        return RequestManager.requestString("com.alipay.antorchard.orchardSyncIndex",
//                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"syncIndexTypes\":\"LIMITED_TIME_CHALLENGE\",\"version\":\""
//                        + VERSION + "\",\"wua\":\"" + wua + "\"}]");
//    }
//    // usebatchSpread 是批量施肥
//    public static String orchardSpreadManure(String wua) {
//        return RequestManager.requestString("com.alipay.antfarm.orchardSpreadManure",
//                "[{\"plantScene\":\"main\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\"," +
//                        "\"usebatchSpread\":false,\"version\":\""
//                        + VERSION + "\",\"wua\":\"" + wua + "\"}]");
//    }
//    /**
//     * 施肥
//     * @param wua 用户标识
//     * @param source 来源标识，可自定义
//     */
//    public static String orchardSpreadManure(String wua,String source,boolean useBatchSpread){
//        return RequestManager.requestString(
//            "com.alipay.antfarm.orchardSpreadManure",
//            "[{\"plantScene\":\"main\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\""+source+"\",\"useBatchSpread\":"+useBatchSpread+",\"version\":\""+VERSION+"\",\"wua\":\""+wua+"\"}]"
//        );
//    }
//
//    /**
//     * 施肥（简化版本，默认不批量）
//     * @param wua 用户标识
//     * @param source 来源标识
//     */
//    public static String orchardSpreadManure(String wua, String source){
//        return orchardSpreadManure(wua, source, false);
//    }
//    public static String receiveTaskAward(String sceneCode, String taskType) {
//        return RequestManager.requestString("com.alipay.antiep.receiveTaskAward",
//                "[{\"ignoreLimit\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"" + sceneCode
//                        + "\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskType\":\""
//                        + taskType + "\",\"version\":\"" + VERSION + "\"}]");
//    }
//    //查询任务列表
//    public static String orchardListTask() {
//        return RequestManager.requestString("com.alipay.antfarm.orchardListTask",
//                "[{\"plantHiddenMMC\":\"false\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//     public static String orchardListTask2() {
//            return RequestManager.requestString("com.alipay.antfarm.orchardListTask",
//                    "[{\"enableSwitchSceneList\":[\"main\",\"yeb\"],\"enableTeamType\":[\"team\"],\"hasYebActivityEntrance\":true,\"plantHiddenMMC\":\"false\",\"requestType\":\"NORMAL\"," +
//                            "\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""+VERSION+"\"}]");
//    }
//    //获取1500广告
//    public static String getAdTaskXlight1500Ad() {
//        return RequestManager.requestString("com.alipay.adexchange.ad.facade.xlightPlugin",
//                "[{\"positionRequest\":{\"extMap\":{},\"referInfo\":{},\"searchInfo\":{},\"spaceCode\":\"NCDEKLLRW_FEEDS_20250610165651\"},\"sdkPageInfo\":{\"adComponentType\":\"FEEDS\",\"adComponentVersion\":\"4.28.66\",\"enableFusion\":true,\"networkType\":\"WIFI\",\"pageFrom\":\"ch_url-https://render.alipay.com/p/yuyan/180020010001263018/game.html\",\"pageNo\":1,\"pageUrl\":\"https://render.alipay.com/p/yuyan/180020010001256918/multi-stage-task.html?caprMode=sync&spaceCodeFeeds=NCDEKLLRW_FEEDS_20250610165651&usePlayLink=true\",\"session\":\"u_eebd6_99d82\",\"unionAppId\":\"2060090000304921\",\"usePlayLink\":\"true\",\"xlightRuntimeSDKversion\":\"4.28.66\",\"xlightSDKType\":\"h5\",\"xlightSDKVersion\":\"4.28.66\"}}]");
//    }
//    //完成1500广告任务
//    public static String finishAdTaskXlight1500Ad(String playBizId) {
//        return RequestManager.requestString("com.alipay.adtask.biz.mobilegw.service.interaction.finish",
//                "[{\"extendInfo\":{\"iepTaskSceneCode\":\"ANTFARM_ORCHARD_TASK_V2\",\"iepTaskType\":\"ORCHARD_DENGHUO_20250610165651\"}," +
//                        "\"playBizId\":\""+playBizId+"\",\"playEventInfo\":{\"endOrder\":0,\"eventStep\":15,\"eventStepType\":\"duration\",\"hasLoopEvent\":false,\"maxLoopCount\":0,\"noticeMediaFinish\":false,\"order\":0,\"playingEventType\":\"BROWSE\",\"rewardId\":72400004,\"rewardNumber\":1500,\"rewardRenderInfo\":{\"rewardDisplayAmount\":\"1500\",\"rewardDisplayText\":\"肥料\",\"rewardIcon\":\"https://mdn.alipayobjects.com/huamei_ouecfj/afts/img/A*7k0KSZNvtZ4AAAAAAAAAAAAADsqsAQ/original\",\"rewardUnitDisplayText\":\"\"}},\"source\":\"adx\"}]");
//    }
//    //获取500广告（动态参数）
//    @SuppressLint("NewApi")
//    public static String getAdTaskXlight500Ad(String referToken, String spaceCodeFeeds) {
//        if (referToken == null) referToken = "";
//        if (spaceCodeFeeds == null || spaceCodeFeeds.isEmpty()) spaceCodeFeeds = "BABA_FARM_TASK_task_70000";
//        String encodedToken = URLEncoder.encode(referToken, StandardCharsets.UTF_8);
//        String params = "[{\"positionRequest\":{\"extMap\":{},\"referInfo\":{\"referToken\":\"" + referToken + "\"},\"searchInfo\":{},\"spaceCode\":\"" + spaceCodeFeeds + "\"},\"sdkPageInfo\":{\"adComponentType\":\"FEEDS\",\"adComponentVersion\":\"4.28.66\",\"enableFusion\":true,\"networkType\":\"WIFI\",\"pageFrom\":\"ch_url-https://render.alipay.com/p/yuyan/180020010001263018/game.html\",\"pageNo\":1,\"pageUrl\":\"https://render.alipay.com/p/yuyan/180020010001256918/multi-stage-task.html?spaceCodeFeeds=" + spaceCodeFeeds + "&tokenFeeds=" + encodedToken + "&usePlayLink=true\",\"session\":\"u_bf546_99dbd\",\"unionAppId\":\"2060090000304921\",\"usePlayLink\":\"true\",\"xlightRuntimeSDKversion\":\"4.28.66\",\"xlightSDKType\":\"h5\",\"xlightSDKVersion\":\"4.28.66\"}}]";
//        return RequestManager.requestString("com.alipay.adexchange.ad.facade.xlightPlugin", params);
//    }
//
//
//    //完成500广告任务
//    public static String finishAdTaskXlight500Ad(String playBizId) {
//        return RequestManager.requestString("com.alipay.adtask.biz.mobilegw.service.interaction.finish",
//                "[{\"extendInfo\":{},\"playBizId\":\""+playBizId+"\",\"playEventInfo\":{\"endOrder\":0,\"eventStep\":15,\"eventStepType\":\"duration\",\"hasLoopEvent\":false,\"maxLoopCount\":0,\"noticeMediaFinish\":false,\"order\":0,\"playingEventType\":\"BROWSE\",\"rewardId\":19300002,\"rewardNumber\":500,\"rewardRenderInfo\":{\"rewardDisplayAmount\":\"500\",\"rewardDisplayText\":\"肥料\",\"rewardIcon\":\"https://mdn.alipayobjects.com/huamei_ouecfj/afts/img/A*7k0KSZNvtZ4AAAAAAAAAAAAADsqsAQ/original\",\"rewardUnitDisplayText\":\"\"}},\"source\":\"adx\"}]");
//    }
//    //获取1000广告
//    public static String getAdTaskXlight1000Ad() {
//        return RequestManager.requestString("com.alipay.adexchange.ad.facade.xlightPlugin",
//                "[{\"positionRequest\":{\"extMap\":{\"canDoTaskTimesLimit\":\"1\"},\"referInfo\":{},\"searchInfo\":{\"rangeFilter\":\"goodsPrice:-\",\"tabKey\":\"all\"},\"spaceCode\":\"NCSKLLRW1_FEEDS_20250610165945\"},\"sdkPageInfo\":{\"adComponentType\":\"FEEDS\",\"adComponentVersion\":\"4.28.66\",\"enableFusion\":true,\"networkType\":\"WIFI\",\"pageFrom\":\"ch_url-https://render.alipay.com/p/yuyan/180020010001263018/game.html\",\"pageNo\":1,\"pageUrl\":\"https://render.alipay.com/p/yuyan/180020010001256918/multi-stage-task.html?caprMode=sync&spaceCodeFeeds=NCSKLLRW1_FEEDS_20250610165945&usePlayLink=true\",\"session\":\"u_51d43_b1b0c\",\"unionAppId\":\"2060090000304921\",\"usePlayLink\":\"true\",\"xlightRuntimeSDKversion\":\"4.28.66\",\"xlightSDKType\":\"h5\",\"xlightSDKVersion\":\"4.28.66\"}}]");
//
//    }
//    //完成1000广告任务
//    public static String finishAdTaskXlight1000Ad(String playBizId) {
//        return RequestManager.requestString("com.alipay.adtask.biz.mobilegw.service.interaction.finish",
//                "[{\"extendInfo\":{\"iepTaskSceneCode\":\"ANTFARM_ORCHARD_TASK_V2\",\"iepTaskType\":\"ORCHARD_DENGHUO_20250610165945\"}," +
//                        "\"playBizId\":\""+playBizId+"\",\"playEventInfo\":{\"endOrder\":0,\"eventStep\":15,\"eventStepType\":\"duration\",\"hasLoopEvent\":false,\"maxLoopCount\":0,\"noticeMediaFinish\":false,\"order\":0,\"playingEventType\":\"BROWSE\",\"rewardId\":72400002,\"rewardNumber\":1000,\"rewardRenderInfo\":{\"rewardDisplayAmount\":\"1000\",\"rewardDisplayText\":\"肥料\",\"rewardIcon\":\"https://mdn.alipayobjects.com/huamei_ouecfj/afts/img/A*7k0KSZNvtZ4AAAAAAAAAAAAADsqsAQ/original\",\"rewardUnitDisplayText\":\"\"}},\"source\":\"adx\"}]");
//    }
//    //森林落叶
//    public static String orchardIndexLuoye(){
//        return RequestManager.requestString("com.alipay.antfarm.orchardIndex",
//                "[{\"commonDegradeResult\":{\"deviceLevel\":\"high\",\"resultReason\":0,\"resultType\":0},\"darwinSceneList\":[\"gameListTwoOptimize\",\"hd_mode\",\"gameCenterEntranceDot\",\"treeTalkCountDown\",\"taskListDimensionConfig\",\"quickPopupBtn\",\"yebTreeTalk\",\"transferPopupYebSwitchMainTree\",\"yebLotteryPlus\",\"hideHelpEndTime\",\"renderWithPriority\",\"fastRewardNewStyle\",\"teamPlantPosition\",\"npcOrchardGuide\",\"teamPlantNewStyle\"],\"growthExtInfo\":\"\",\"growthTask\":\"\",\"inHomepage\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"senlinluoye\",\"useWua\":\"\",\"version\":\""+VERSION+"\"}]");
//    }
//    public static String orchardRefinedOperation() {
//        return RequestManager.requestString("com.alipay.antorchard.refinedOperation",
//                "[{\"actionId\":\"ENTERORCHARD\",\"inHomepage\":\"true\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"senlinluoye\",\"version\":\""+VERSION+"\"}]");
//    }
//    //完成落叶任务
//    public static String triggerSubplotsActivity() {
//        return RequestManager.requestString("com.alipay.antorchard.triggerSubplotsActivity",
//                "[{\"activityId\":\"antorchard_defoliation\",\"activityType\":\"DEFOLIATION\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"senlinluoye\",\"version\":\""+VERSION+"\"}]");
//    }
//    //农场签到
//    public static String orchardSign() {
//        return RequestManager.requestString("com.alipay.antfarm.orchardSign",
//                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"signScene\":\"ANTFARM_ORCHARD_SIGN_V2\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String finishTask(String userId, String sceneCode, String taskType) {
//        return RequestManager.requestString("com.alipay.antiep.finishTask",
//                "[{\"outBizNo\":\"" + userId + System.currentTimeMillis()
//                        + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"" + sceneCode
//                        + "\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskType\":\""
//                        + taskType + "\",\"userId\":\"" + userId + "\",\"version\":\"" + VERSION
//                        + "\"}]");
//    }
//    //小组件访问
//    public static String receiveOrchardVisitAward(){
//        return RequestManager.requestString("com.alipay.antorchard.receiveOrchardVisitAward",
//                "[{\"diversionSource\":\"widget\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\"," +
//                        "\"source\":\"widget_indie_android\",\"version\":\""+VERSION+"\"}]");
//    }
//
//    public static String receiveOrchardVisitAward(String diversionSource, String source){
//        return RequestManager.requestString("com.alipay.antorchard.receiveOrchardVisitAward",
//                "[{\"diversionSource\":\"" + diversionSource + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\"," +
//                        "\"source\":\"" + source + "\",\"version\":\""+VERSION+"\"}]");
//    }
//    public static String triggerTbTask(String taskId, String taskPlantType) {
//        return RequestManager.requestString("com.alipay.antfarm.triggerTbTask",
//                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskId\":\""
//                        + taskId + "\",\"taskPlantType\":\"" + taskPlantType
//                        + "\",\"version\":\"" + VERSION + "\"}]");
//    }
//    public static String orchardSelectSeed() {
//        return RequestManager.requestString("com.alipay.antfarm.orchardSelectSeed",
//                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"seedCode\":\"rp\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    /* 砸金蛋 */
//    public static String queryGameCenter() {
//        return RequestManager.requestString("com.alipay.antorchard.queryGameCenter",
//                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String newQueryGameCenter(){
//        String method = "com.alipay.antorchard.queryGameCenter";
//        String params = "[{\"queryGameCenterTheme\":true,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""+VERSION+"\"}]";
//        return RequestManager.requestString(method, params);
//    }
//    public static String noticeGame(String appId) {
//        return RequestManager.requestString("com.alipay.antorchard.noticeGame",
//                "[{\"appId\":\"" + appId
//                        + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//    public static String submitUserAction(String gameId) {
//        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserAction",
//                "[{\"actionCode\":\"enterGame\",\"gameId\":\"" + gameId
//                        + "\",\"paladinxVersion\":\"2.0.13\",\"source\":\"gameFramework\"}]");
//    }
//    public static String submitUserPlayDurationAction(String gameAppId, String source) {
//        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction",
//                "[{\"gameAppId\":\"" + gameAppId + "\",\"playTime\":32,\"source\":\"" + source
//                        + "\",\"statisticTag\":\"\"}]");
//    }
//    public static String smashedGoldenEgg(int count) {
//        return RequestManager.requestString("com.alipay.antorchard.smashedGoldenEgg",
//                "[{\"batchSmashCount\":" + count + ",\"requestType\":\"NORMAL\",\"seneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
//                        + VERSION
//                        + "\"}]");
//    }
//    /* 助力好友 */
////  public static String shareP2P() {
////        return ApplicationHook.requestString("com.alipay.antiep.shareP2P",
////                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM_ORCHARD_SHARE_P2P\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\""
////                        + VERSION + "\"}]");
////    }
//    public static String achieveBeShareP2P(String shareId) {
//        return RequestManager.requestString("com.alipay.antiep.achieveBeShareP2P",
//                "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM_ORCHARD_SHARE_P2P\",\"shareId\":\""
//                        + shareId
//                        + "\",\"source\":\"share\",\"version\":\""
//                        + VERSION + "\"}]");
//    }
//}
