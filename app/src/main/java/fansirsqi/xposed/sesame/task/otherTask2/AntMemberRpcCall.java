package fansirsqi.xposed.sesame.task.otherTask2;


import org.json.JSONObject;

import fansirsqi.xposed.sesame.entity.RpcEntity;
import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.RandomUtil;
import java.util.UUID;

public class AntMemberRpcCall {
    public static String executeTask(String str, String str2) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.executeTask",
                "[{\"bizOutNo\":\"" + (System.currentTimeMillis() - 16000) + "\",\"bizParam\":\"" + str + "\",\"bizSubType\":\"" + str2 + "\",\"bizType\":\"BROWSE\"}]");
    }

//    public static Boolean check() {
//        boolean z = true;
//        RpcEntity requestObject = RequestManager.requestObject("alipay.antmember.biz.rpc.member.h5.queryPointCert", "[{\"page\":1,\"pageSize\":8}]", 1, 0);
//        if (requestObject == null || requestObject.getHasError().booleanValue()) {
//            z = false;
//        }
//        return Boolean.valueOf(z);
//    }

    public static String queryPointCert(int i, int i2) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.member.h5.queryPointCert", "[{\"page\":" + i + ",\"pageSize\":" + i2 + "}]");
    }

    public static String receivePointByUser(String str) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.member.h5.receivePointByUser", "[{\"certId\":" + str + "}]");
    }

    public static String rpcCall_signIn() {
        return ApplicationHook.requestString("alipay.kbmemberprod.action.signIn", "[{\"sceneCode\":\"KOUBEI_INTEGRAL\",\"source\":\"ALIPAY_TAB\",\"version\":\"2.0\"}]");
    }

    public static String applyTask(String str, Long l) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.applyTask", "[{\"darwinExpParams\":{\"darwinName\":\"" + str + "\"},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"taskConfigId\":" + l + "}]");
    }

    //新方法？
    public static String applyTask2(Long l) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.applyTask", "[{\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"taskConfigId\":\"" + l + "\"}]");
    }

    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }

    public static String ngfeUpdate(String str) {
        return ApplicationHook.requestString("com.alipay.csprod.prom.camp.ngfe.update", "[{\"tagCode\":\"" + str + "\"}]");
    }

    public static String queryAllStatusTaskList() {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.queryAllStatusTaskList",
                "[{\"sourceBusiness\":\"signInAd\"}]");
    }

    //新会员任务列表方法
    public static String queryAllStatusTaskListNew() {
        return ApplicationHook.requestString("com.alipay.amic.memtask.h5.MemTaskListQueryFacade.queryAllStatusTaskList",
                "[{\"source\":\"signInAd\"}]");
    }
//    public static String queryAllStatusTaskListNew() {
//        long time = System.currentTimeMillis();
//        return RequestManager.requestString("com.alipay.amic.memtask.h5.MemTaskListQueryFacade.queryAllStatusTaskList",
//                "{\"__apiCallStartTime\":"+time+",\"__apiNativeCallId\":\"native_1484\"," +
//                        "\"operationType\":\"com.alipay.amic.memtask.h5.MemTaskListQueryFacade.queryAllStatusTaskList\"," +
//                        "\"requestData\":[{\"source\":\"signInAd\"}]}");
//    }


    public static String queryMemberSigninCalendar() {
        return ApplicationHook.requestString("com.alipay.amic.biz.rpc.signin.h5.queryMemberSigninCalendar", "[{\"autoSignIn\":true,\"invitorUserId\":\"\",\"sceneCode\":\"QUERY\"}]");
    }

    public static String signPageTaskList() {
        String session = UUID.randomUUID().toString();
        // pageNo在2-3之间随机选择，因为pageNo=1时列表为空
        int pageNo = RandomUtil.nextInt(2, 3);
        return ApplicationHook.requestString("com.alipay.amic.memtask.h5.MemTaskListQueryFacade.signPageTaskList",
                "[{\"pageNo\":" + pageNo + ",\"pageSize\":8,\"session\":\"" + session + "\"," +
                        "\"source\":\"antmember\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\"," +
                        "\"unid\":\"\"},\"spaceCode\":\"ant_member_xlight_task\",\"switchNormal\":true," +
                        "\"taskTopConfigId\":\"\"}]");
    }

    public static String transcodeCheck() {
        return ApplicationHook.requestString("alipay.mrchservbase.mrchbusiness.sign.transcode.check", "[{}]");
    }

    //初始化？
    public static String queryVajraPositionCarouselMessage() {
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.component.h5.queryVajraPositionCarouselMessage",
                "[{\"relatedChannel\":\"MEMBER_POINT_ACTIVITY\",\"sceneCode\":\"\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"ch_appcenter__chsub_9patch\",\"unid\":\"\"}}]");
    }

    public static String queryVajraPositionCarouselMessageNew() {
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryHomeVajraInfo",
                "[{\"extInfo\":{},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"ch_appcenter__chsub_9patch\",\"unid\":\"\"}}]");
    }

    //攒积分赚现金活动投放
    public static String PlayConsultFacadeConsult() {
        return ApplicationHook.requestString("com.alipay.amic.biz.rpc.activity.h5.PlayConsultFacade.consult",
                "[{\"operation\":\"consultSignInVersion\",\"playId\":\"PLAY202412061191152295\",\"source\":\"alipaymember\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"ch_appcenter__chsub_9patch\",\"unid\":\"\"}}]");
    }

    public static String commonTransFatigue() {
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.component.h5.commonTrans.fatigue",
                "[{\"sceneCode\":\"FAMY0I925T\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"ch_appcenter__chsub_9patch\",\"unid\":\"\"}}]");
    }

    public static String queryReSignInCardInfo() {
        return ApplicationHook.requestString("com.alipay.amic.biz.rpc.signin.h5.queryReSignInCardInfo",
                "[{}]");
    }

    public static String queryCommonDeliveryInfo() {
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryCommonDeliveryInfo",
                "[{\"limit\":1,\"relatedChannel\":\"point-sign-in\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"ch_appcenter__chsub_9patch\",\"unid\":\"\"},\"targetCode\":\"H5_PAGE_CONFIG\"}]");
    }

    public static String queryTaskList() {
        return ApplicationHook.requestString("com.alipay.amic.memtask.h5.MemTaskListQueryFacade.queryTaskList",
                "[{\"source\":\"antmember_wish_pool\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"ch_appcenter__chsub_9patch\",\"unid\":\"\"}}]");
    }

    // querySimpleIndex
    public static String querySimpleIndex() {
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.member.h5.querySimpleIndex",
                "[{\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]");
    }

    //游戏任务
    public static String queryGameTaskList() {
        return ApplicationHook.requestString("com.alipay.amic.biz.rpc.activity.h5.PlayConsultFacade.consult",
                "[{\"operation\":\"consultGameCenter\",\"params\":{\"deviceLevel\":\"high\",\"unityDeviceLevel\":\"high\"},\"playId\":\"PLAY202404281383002382\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"ch_appcenter__chsub_9patch\",\"unid\":\"\"}}]");
    }

    // 累积任务
    public static String queryAccumulateTask() {
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.membertask.h5.queryTaskList",
                "[{\"relatedChannel\":\"MEMBERPOINT\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]");
    }

    //领取累积任务积分
    public static String receivePointAward(String taskProcessId, String awardRelatedOutBizNo) {
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.membertask.h5.award",
                "[{\"awardRelatedOutBizNo\":\"" + awardRelatedOutBizNo + "\"," +
                        "\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}," +
                        "\"taskProcessId\":\"" + taskProcessId + "\"}]");
    }

    //
    public static String adTaskFinish(String bizId) {
        return ApplicationHook.requestString("com.alipay.adtask.biz.mobilegw.service.task.finish",
                "[{\"bizId\":\"" + bizId + "\",\"extendInfo\":{}}]");
    }

    //=================会员宝箱
    public static String querySignFloatingBall() {
        return RequestManager.requestString("com.alipay.amic.biz.rpc.signin.h5.querySignFloatingBall",
                "[{\"extMap\":{},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"}}]");
    }

//    public static String getBallBoxAD() {
//        return RequestManager.requestString("com.alipay.adexchange.ad.facade.xlightPlugin",
//                "[{\"positionRequest\":{\"extMap\":{\"xlightPlayInstanceId\":\"\"},\"referInfo\":{},\"searchInfo\":{}," +
//                        "\"spaceCode\":\"HY_QIANDAO_FEEDS\"},\"sdkPageInfo\":{\"adComponentType\":\"FEEDS\"," +
//                        "\"adComponentVersion\":\"4.29.9\",\"enableFusion\":true,\"networkType\":\"WIFI\"," +
//                        "\"pageFrom\":\"ch_url-https://render.alipay.com/p/yuyan/180020380000000023/home-page.html\"," +
//                        "\"pageNo\":1,\"pageUrl\":\"https://render.alipay.com/p/yuyan/180020010001254515/point-sign-in.html?caprMode=sync&chInfo=memberHomePage_myTab&innerSource=&pageFrom=HOME_PAGE&source=myTab&sourcePassMap=%7B%7D&unid=&useCache=YES\"," +
//                        "\"session\":\"u_3b420_b6898\",\"unionAppId\":\"68687805\",\"xlightRuntimeSDKversion\":\"4.29.9\",\"xlightSDKType\":\"h5\",\"xlightSDKVersion\":\"4.29.9\"}}]");
//    }

    // 完成开宝箱
    public static String triggerSignFloatingBall(String bizNo) {
        return RequestManager.requestString("com.alipay.amic.biz.rpc.signin.h5.triggerSignFloatingBall",
                "[{\"bizNo\":\""+bizNo+"\",\"extMap\":{},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"taskType\":\"MULTIPLE_TIMER_TASK\"}]");
    }


    // 查询宝箱广告任务
    public static String querySignFloatingBallAdTask(String bizNo) {
        return RequestManager.requestString("com.alipay.amic.biz.rpc.signin.h5.querySignFloatingBallAdTask",
                "[{\"adType\":\"AD_VIDEO_TASK\",\"bizNo\":\""+bizNo+"\",\"extMap\":{},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"}}]");
    }
    // 完成宝箱广告任务
    public static String triggerAdTask(String bizNo) {
        return RequestManager.requestString("com.alipay.amic.biz.rpc.signin.h5.triggerSignFloatingBall",
                "[{\"bizNo\":\""+bizNo+"\",\"extMap\":{},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"taskType\":\"AD_VIDEO_TASK\"}]");
    }

    // ================= 芝麻信用 Zmxy =================
    public static class Zmxy {
        private static final String VERSION = "2025-10-22";

        // 芝麻粒炼金
        public static class Alchemy {
            /**
             * 芝麻炼金/积分首页
             */
            public static String alchemyQueryHome() {
                return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryHome",
                        "[{}]");
            }

            /**
             * 芝麻炼金-执行炼金
             */
            public static String alchemyExecute() {
                return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.alchemy", "[{}]");
            }

            /**
             * 芝麻炼金-签到列表查询
             * @param sceneCode "zml" 对应芝麻粒福利签到, "alchemy" 对应芝麻炼金签到
             */
            public static String alchemyQueryCheckIn(String sceneCode) {
                return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists",
                        "[{\"sceneCode\":\"" + sceneCode + "\",\"version\":\"" + VERSION + "\"}]");
            }

            /**
             * 芝麻炼金-查询时段任务 (午饭/晚饭)
             */
            public static String queryTimeLimitedTask() {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask",
                        "[{}]");
            }

            /**
             * 芝麻炼金-完成时段任务 (午饭/晚饭)
             */
            public static String completeTimeLimitedTask(String templateId) {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask",
                        "[{\"templateId\":\"" + templateId + "\"}]");
            }

            /**
             * 芝麻炼金-查询信用反馈
             */
            public static String queryCreditFeedback() {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.queryCreditFeedback",
                        "[{\"queryPotential\":false,\"size\":20,\"status\":\"UNCLAIMED\"}]");
            }

            /**
             * 芝麻炼金-收集信用反馈（一键领取）
             */
            public static String collectCreditFeedback() {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback",
                        "[{\"collectAll\":true,\"status\":\"UNCLAIMED\"}]");
            }

            /**
             * 芝麻炼金-查询上次操作任务
             */
            public static String queryLastOperateTask() {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryLastOperateTask",
                        "[{\"version\":\"alchemy\"}]");
            }

            /**
             * 芝麻炼金-查询入口列表
             */
            public static String queryEntryList(String version) {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryEntryList",
                        "[{\"version\":\"" + version + "\"}]");
            }

            /**
             * 芝麻炼金-签到任务完成
             * @param checkInDate yyyyMMdd格式日期
             * @param sceneCode "zml" 或 "alchemy"
             */
            public static String completeCheckInTask(String checkInDate, String sceneCode) {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask",
                        "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"" + sceneCode + "\"}]");
            }

            /**
             * 芝麻炼金-查询任务列表V3
             */
            public static String queryListV3() {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3",
                        "[{\"chInfo\":\"\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"searchSubscribeTask\":true,\"version\":\"alchemy\"}]");
            }

            /**
             * 芝麻炼金-参加活动（领取任务）
             */
            public static String joinActivity(String templateId) {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity",
                        "[{\"chInfo\":\"seasameList\",\"joinFromOuter\":false,\"sceneCode\":\"alchemy\",\"templateId\":\"" + templateId + "\"}]");
            }

            /**
             * 芝麻炼金-领取奖励
             */
            public static String claimAward() {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.claimAward",
                        "[{}]");
            }
        }
    }
}