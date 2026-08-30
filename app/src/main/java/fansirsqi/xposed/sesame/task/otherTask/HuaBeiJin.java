package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class HuaBeiJin extends BaseCommTask {

    public HuaBeiJin() {
        this.displayName = "花呗金💴";
    }

    @Override
    protected void handle() {
        signIn();
    }

    private void signIn() {
        if(Status.hasFlagToday("HuaBeiJin:Sign")) return;

        TimeUtil.sleep(RandomUtil.nextInt(1000, 2000));
        try {
            String queryRes = RequestManager.requestString(
                    "com.alipay.pcreditrecweb.needle.hbjQuerySignInfo",
                    "[{}]"
            );
            if (queryRes.isEmpty()) {
                Log.error("花呗金--查询签到信息响应为空");
                return;
            }
            JSONObject queryJo = new JSONObject(queryRes);
            if (!queryJo.optBoolean("success", false)) {
                Log.error("花呗金--查询签到信息失败: " + queryJo.optString("errorMsg", queryRes));
                return;
            }
            JSONObject result = queryJo.optJSONObject("result");
            if (result == null) {
                Log.error("花呗金--签到数据结构异常");
                return;
            }
            if (!result.optBoolean("accountExists", true) || result.optBoolean("notAllow", false)) {
                Log.other(this.displayName + "未开通花呗或暂不支持签到");
                return;
            }
            if (result.optBoolean("todaySignIn", false)) {
                Log.other(this.displayName + "今日已签到");
                return;
            }

            String playId = result.optString("playId", "");
            if (playId.isEmpty()) {
                Log.error("花呗金--未获取到 playId");
                return;
            }

            TimeUtil.sleep(RandomUtil.nextInt(1500, 3000));
            String signInRes = RequestManager.requestString(
                    "com.alipay.pcreditrecweb.needle.hbjSignIn",
                    "[{\"playId\":\"" + playId + "\"}]"
            );
            if (signInRes.isEmpty()) {
                Log.error("花呗金--签到响应为空");
                return;
            }
            JSONObject signInJo = new JSONObject(signInRes);
            if (signInJo.optBoolean("success", false) || "SUCCESS".equals(signInJo.optString("errorCode"))) {
                JSONObject signInResult = signInJo.optJSONObject("result");
                int price = 1;
                if (signInResult != null) {
                    JSONArray rewardInfos = signInResult.optJSONArray("rewardInfos");
                    if (rewardInfos != null && rewardInfos.length() > 0) {
                        price = rewardInfos.getJSONObject(0).optInt("price", 1);
                    }
                }
                Log.other(this.displayName + "签到成功获得[" + price + "花呗金]");
            } else {
                Log.other(this.displayName + "签到失败: " + signInJo.optString("errorMsg", "未知错误"));
            }
        } catch (JSONException e) {
            Log.error("花呗金JSON解析异常: " + e);
        } catch (Throwable th) {
            Log.error(this.TAG, "花呗金异常："+th);
        }
        Status.setFlagToday("HuaBeiJin:Sign");
    }
}
