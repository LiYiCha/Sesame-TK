package fansirsqi.xposed.sesame.task.welfareCenter;


import org.json.JSONArray;

import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.otherTask.BaseTaskRpcCall;
import fansirsqi.xposed.sesame.util.JsonUtil;

public class WelfareCenterRpcCall extends BaseTaskRpcCall {
    public static String campTrigger(String str, String str2) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.camp.trigger", "[{" + (!str2.isEmpty() ? "\"campId\": \"CP15205657\"," + str2 : "\"campId\": \"CP15205657\"") + "}]");
    }

    public static String queryCert(String[] strArr) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.cert.query", "[{\"certTemplateIdSet\":" + JsonUtil.formatJson(strArr) + "}]");
    }

    public static String batchUseVirtualProfit(JSONArray jSONArray) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.virtualProfit.batchUseVirtualProfit", "[{\"virtualProfitIdList\":" + jSONArray + "}]");
    }

    public static String playTrigger(String str) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.playcenter.playTrigger.trigger", "[{\"extInfo\":{},\"operation\":\"MYBK_DACU_INTERACTIVE_ZHB\",\"playId\":\"" + str + "\"}]");
    }

    public static String pointBanlance(String str) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.group.point.pointBanlanceV2", "[{\"queryExpireEndDate\": \"" + str + "\",\"sceneCode\": \"SUPER930\"}]");
    }

    public static String queryEnableVirtualProfitV2(String str) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.virtualProfit.queryEnableVirtualProfitV2", "[{\"firstSceneCode\":[],\"profitType\":\"ANTBANK_WELFARE_POINT\",\"sceneCode\":[\"FULICenter_JKJML\",\"FULICenter_JZN\",\"BC3_BC3V1\",\"BC3_BC3V2\",\"BC3_BC3V3\",\"SQB_SQBV0\",\"SQB_SQBV1\",\"SQB_SQBV2\",\"SQB_SQBV3\",\"SQB_SQBV4\",\"SQB_SQBV5\",\"SQB_SQBV6\",\"SQB_SQBV7\",\"SQB_SQBV8\",\"SQB_SQBV9\",\"SQB_SQBV10\",\"SQB_SQBV11\",\"SQB_SQBSIGN\",\"FULICenter_JKJQW\",\"FULICenter_WSWF\",\"FULICenter_FLKZS\",\"FULICenter_KGJXBBF\",\"FULICenter_AXHZXB\",\"FULICenter_BBF\",\"FULICenter_V1\",\"FULICenter_V2\",\"FULICenter_V3\",\"FULICenter_V4\",\"FULICenter_V5\",\"FULICenter_V6\",\"FULICenter_V7\",\"FULICenter_YulibaoAUM\",\"FULICenter_PayByMybank\",\"FULICenter_DepositAUM\",\"FULICenter_YYYYH\",\"FULICenter_QYZ\",\"FULICenter_V7PLUS\",\"FULICenter_V6PLUS\",\"FULICenter_V5PLUS\",\"FULICenter_V8\",\"FULICenter_V9\",\"FULICenter_V10\",\"FULICenter_LCCZ\",\"FULICenter_LCTZ\",\"FULICenter_shequn\",\"FULICenter_shizhounian\",\"HarvestCard_HarvestCardGold1\",\"HarvestCard_HarvestCardGold2\",\"HarvestCard_HarvestCardGold3\",\"BC3_BC3SILVER\",\"HarvestCard_SILVER\",\"FULICenter_ylbxianshi\",\"FULICenter_FLR\",\"FULICenter_yiliaowenda\",\"FULICenter_HarvestCardNormal\",\"MybankMembe_MybankMemberSILVER\",\"MybankMembe_MybankMemberGOLD1\",\"MybankMembe_MybankMemberGOLD2\",\"MybankMembe_MybankMemberGOLD3\",\"LicaiKaimenhong_LicaiKaimenhongYueqianli\",\"Kaimenhong_MemberGOLD3\",\"Kaimenhong_MemberGOLD2\",\"Kaimenhong_MemberGOLD1\",\"Kaimenhong_MemberSILVER\",\"LEYEKA_LEYEKAGOLD3\",\"LEYEKA_LEYEKAGOLD2\",\"LEYEKA_LEYEKAGOLD1\",\"LEYEKA_LEYEKAYINKA\",\"BC3_DIAMOND\",\"LEYEKA_LEYEKADIAMOND\",\"HarvestCard_HarvestCardDiamond\"],\"signInSceneId\":\"" + str + "\"}]");
    }

    public static String signinPlay() {
        return RequestManager.requestString("com.alipay.loanpromoweb.member.play.signinPlay", "[{\"channel\": \"miniApp\",\"needMultiple\": false,\"operation\": \"signConsult\",\"playId\": \"PLAY100177545\"}]");
    }

    public static String trigger() {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.camp.trigger", "[{\"campId\": \"CP15205657\",\"extParams\": {\"bkPointUseMemo\": \"抽奖消耗\",\"pcbfcCertMemo\": \"FULICenterUSE\"}}]");
    }

    public static String taskQuery(String appletId) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.task.taskQuery", "[{\"appletId\":\"" + appletId + "\"}]");
    }

    public static String taskTrigger(String appletId, String stageCode, String taskCenId) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.task.taskTrigger", "[{\"appletId\":\"" + appletId + "\",\"stageCode\":\"" + stageCode + "\",\"taskCenId\":\"" + taskCenId + "\"}]");
    }

    public static String queryPointBalance() {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.group.point.pointBanlanceV2", "[{\"sceneCode\":\"SUPER930\"}]");
    }

    public static String queryItemsInMemberV2(int pageNum, int perPageSize) {
        return RequestManager.requestString("com.alipay.loanpromoweb.member.benefits.queryItemsInMemberV2",
                "[{\"campId\":\"BSCP202209161036790608000055160000G\",\"homeQuery\":false,\"pageNum\":" + pageNum + ",\"perPageSize\":\"" + perPageSize + "\",\"sceneCode\":\"MYBK_SUPER_930\",\"tabId\":\"BSLB202209161036790633000055640000G\"}]");
    }

    private static org.json.JSONObject buildMemberSourcePassMap() {
        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("innerSource", "");
            obj.put("source", "mytab");
            obj.put("unid", "");
            return obj;
        } catch (Exception e) {
            return new org.json.JSONObject();
        }
    }

    private static org.json.JSONObject copyMemberSourcePassMap(org.json.JSONObject sourcePassMap) {
        if (sourcePassMap == null) {
            return buildMemberSourcePassMap();
        }
        try {
            return new org.json.JSONObject(sourcePassMap.toString());
        } catch (Exception e) {
            return buildMemberSourcePassMap();
        }
    }

    public static String querySingleBenefitDetail(String benefitId, String requestSourceInfo, org.json.JSONObject sourcePassMap) {
        try {
            org.json.JSONObject args = new org.json.JSONObject();
            args.put("benefitId", benefitId);
            args.put("cityCode", "440100");
            args.put("miniAppId", "");
            if (requestSourceInfo != null && !requestSourceInfo.trim().isEmpty()) {
                args.put("requestSourceInfo", requestSourceInfo);
            }
            args.put("sourcePassMap", copyMemberSourcePassMap(sourcePassMap));
            return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.config.h5.querySingleBenefitDetail", new JSONArray().put(args).toString());
        } catch (Exception e) {
            return "";
        }
    }

    public static String queryPromoBenefitOrderConfirmInfo(String benefitId, String requestSourceInfo, org.json.JSONObject sourcePassMap) {
        try {
            org.json.JSONObject args = new org.json.JSONObject();
            args.put("benefitId", benefitId);
            if (requestSourceInfo != null && !requestSourceInfo.trim().isEmpty()) {
                args.put("requestSourceInfo", requestSourceInfo);
            }
            args.put("sourcePassMap", copyMemberSourcePassMap(sourcePassMap));
            return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryPromoBenefitOrderConfirmInfo", new JSONArray().put(args).toString());
        } catch (Exception e) {
            return "";
        }
    }

    public static String exchangeMemberBenefit(String benefitId, String itemId, String requestSourceInfo, org.json.JSONObject sourcePassMap) {
        try {
            org.json.JSONObject exchangeSourcePassMap = copyMemberSourcePassMap(sourcePassMap);
            exchangeSourcePassMap.put("alipayClientVersion", "10.8.20.8000");
            exchangeSourcePassMap.put("mobileOsType", "Android");

            org.json.JSONObject args = new org.json.JSONObject();
            args.put("benefitId", benefitId);
            args.put("cityCode", "440100");
            args.put("exchangeType", "POINT_PAY");
            if (itemId != null && !itemId.trim().isEmpty()) {
                args.put("itemId", itemId);
            }
            args.put("miniAppId", "");
            args.put("orderSource", "");
            args.put("requestId", "requestId" + System.currentTimeMillis());
            if (requestSourceInfo != null && !requestSourceInfo.trim().isEmpty()) {
                args.put("requestSourceInfo", requestSourceInfo);
            }
            args.put("sourcePassMap", exchangeSourcePassMap);
            args.put("userOutAccount", "");
            return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", new JSONArray().put(args).toString());
        } catch (Exception e) {
            return "";
        }
    }

    public static String querySingleExchangeOrderDetail(String benefitId, String bizType, String outBizNo, org.json.JSONObject sourcePassMap) {
        try {
            org.json.JSONObject args = new org.json.JSONObject();
            args.put("benefitId", benefitId);
            args.put("bizType", bizType);
            args.put("miniAppId", "");
            args.put("outBizNo", outBizNo);
            args.put("sourcePassMap", copyMemberSourcePassMap(sourcePassMap));
            return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.querySingleExchangeOrderDetail", new JSONArray().put(args).toString());
        } catch (Exception e) {
            return "";
        }
    }
}