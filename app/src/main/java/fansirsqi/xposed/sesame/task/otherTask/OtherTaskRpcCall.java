package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fansirsqi.xposed.sesame.util.RandomUtil;

public class OtherTaskRpcCall extends BaseTaskRpcCall {

    public static String goldBillIndex() {
        return RequestManager.requestString("com.alipay.wealthgoldtwa.needle.goldbill.index", "[{\"pageTemplateCode\":\"H5_GOLDBILL\",\"params\":{\"client_pkg_version\":\"0.0.5\"},\"url\":\"https://68687437.h5app.alipay.com/www/index.html\"}]");
    }
    public static String goldBillTrigger(String str) {
        return RequestManager.requestString("com.alipay.wealthgoldtwa.goldbill.v4.task.trigger", "[{\"goldBillTaskTransferVersion\":\"v2\",\"taskId\":\"" + str + "\"}]");
    }
    public static String taskQueryPush(String str) {
        return RequestManager.requestString("com.alipay.wealthgoldtwa.needle.taskQueryPush", "[{\"mode\":1,\"taskId\":\"" + str + "\"}]");
    }

    public static String queryTaskList() {
        return RequestManager.requestString("alipay.promoprod.task.query.queryTaskList", "[      {\n            \"consultAccessFlag\": true,\n            \"planId\": \"AP17187348\"\n        }]");
    }

    public static String signup(String str, String str2) {
        String str3 = "\"taskCenId\":\"AP17187348\",\"taskId\":\"" + str2 + "\"";
        if (!str.isEmpty()) {
            str3 = str3 + ",\"extInfo\":{\"gplusItem\":\"" + str + "\"}";
        }
        return RequestManager.requestString("alipay.promoprod.task.query.signup", "[{" + str3 + "}]");
    }

    public static String complete(String str) {
        return RequestManager.requestString("alipay.promoprod.applet.complete", "[{\"appletId\":\"" + str + "\"}]");
    }


    public static JSONObject trigger() throws JSONException {
        return new JSONObject(RequestManager.requestString("alipay.mobile.ipsponsorprod.consume.gold.index.promo.trigger", "[{\"appSource\":\"consumeGold\",\"cacheMap\":{\"FAVORITE_CONSUME_GOLD\":\"" + UserMap.getCurrentUid() + "\"},\"alipayAppVersion\":\"10.6.20.9000\",\"appClient\":\"Android\",\"clientTraceId\":\"" + UUID.randomUUID().toString() + "\",\"clientVersion\":\"6.5.0\",\"favoriteStatus\":\"Favorite\",\"requestId\":\"" + RandomUtil.getRandomString(16).toUpperCase() + System.currentTimeMillis() + "\"}]"));
    }

}
