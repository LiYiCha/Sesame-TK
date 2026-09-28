package fansirsqi.xposed.sesame.task.otherTask;

import android.os.Build;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.data.Status;
import org.json.JSONArray;
import org.json.JSONObject;

public class Salaryday extends BaseCommTask {
    public Salaryday() {
        this.displayName = "红包雨🍞";
        this.hoursKeyEnum = CompletedKeyEnum.Salaryday;
    }

    private void exchangeYebExp() {
        String str = "红包雨🎮提取体验金[";
        String str2 = "\"amount\":\"";
        try {
            JSONObject homePageQuery = homePageQuery();
            if (homePageQuery != null) {
                int i = homePageQuery.getInt("yebExpAmount");
                if (i >= 300) {
                    StringBuilder stringBuilder = new StringBuilder(str2);
                    stringBuilder.append(i);
                    stringBuilder.append("\",\"consumeOutBizNo\":\"");
                    stringBuilder.append(homePageQuery.getString("consumeOutBizNo"));
                    stringBuilder.append("\"");
                    if (requestString("com.alipay.yebpromobff.salaryday.prize.exchangeYebExp", stringBuilder.toString()) != null) {
                        String stringBuilder2 = str + i +
                                "]";
                        Log.other(stringBuilder2);
                        TimeUtil.sleep(this.executeIntervalInt);
                        return;
                    }
                }
            }
            TimeUtil.sleep(this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep(this.executeIntervalInt);
        }
    }

    private JSONObject homePageQuery() {
        try {
            String stringBuilder = "\"channel\":\"jijinshichang\",\"deviceType\":\"" + Build.MODEL +
                    "\",\"disableCampConsult\":true,\"disableRecPrize\":1,\"queryType\":\"REFRESH\",\"recPrizeFeatures\":{\"FIN_TRIGGER_CONTEXT\":{\"recBlockCode\":\"915_SALARY_DAY_PROMO_BLOCK\"},\"RECOMMEND_MODE\":\"ALLOW_MULTI_RECOMMEND\"},\"sceneId\":\"YEB_SALARY_DAY\"";
            JSONObject requestString = requestString("com.alipay.yebpromobff.salaryday.main.homePageQuery", stringBuilder);
            if (requestString == null) {
                return null;
            }
            requestString = requestString.getJSONObject("result");
            return !"ACTIVE".equals(requestString.getString("activityStatus")) ? null : requestString;
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
            return null;
        }
    }

    private void payGameChance() {
        try {
            JSONObject homePageQuery = homePageQuery();
            if (homePageQuery != null) {
                int i = homePageQuery.getInt("gameChanceCount");
                if (i != 0) {
                    int i2 = homePageQuery.getInt("joinGameTimes");
                    while (i > 0) {
                        int i3 = i2 + 1;
                        String stringBuilder = "\"bigPrizeCampId\":\"CP10155344\",\"centAmount\":500,\"coinCount\":40,\"consumeCampId\":\"CP13154913\",\"playTimes\":" +
                                i3;
                        JSONObject requestString = requestString("com.alipay.yebpromobff.salaryday.prize.trigger", stringBuilder);
                        if (requestString != null) {
                            String stringBuilder2 = "红包雨🎮获得体验金[" +
                                    JsonUtil.getValueByPath(requestString, "result.lotteryPrize.amount") +
                                    "]";
                            Log.other(stringBuilder2);
                            i--;
                            TimeUtil.sleep(this.executeIntervalInt);
                            i2 = i3;
                        }
                    }
                    TimeUtil.sleep(this.executeIntervalInt);
                    return;
                }
            }
            TimeUtil.sleep(this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep(this.executeIntervalInt);
        }
    }

    private void salarydayTask() {
        String str = "\",\"taskId\":\"";
        String str2 = "\"taskCenId\":\"";
        try {
            JSONObject homePageQuery = homePageQuery();
            if (homePageQuery == null) {
                TimeUtil.sleep(this.executeIntervalInt);
                return;
            }
            JSONArray jSONArray = homePageQuery.getJSONArray("taskDetailList");
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject jSONObject = jSONArray.getJSONObject(i);
                String string = jSONObject.getString("taskProcessStatus");
                if (!"NONE_SIGNUP".equals(string)) {
                    if (!"SIGNUP_EXPIRED".equals(string)) {
                        string = jSONObject.getString("taskId");
                        String string2 = jSONObject.getString("appletId");
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(str2);
                        stringBuilder.append(string2);
                        stringBuilder.append(str);
                        stringBuilder.append(string);
                        stringBuilder.append("\"");
                        if (requestString("com.alipay.yebpromobff.salaryday.task.complete", stringBuilder.toString()) != null) {
                            stringBuilder = new StringBuilder();
                            stringBuilder.append(str2);
                            stringBuilder.append(string2);
                            stringBuilder.append(str);
                            stringBuilder.append(string);
                            stringBuilder.append("\",\"version\":2");
                            jSONObject = requestString("com.alipay.yebpromobff.common.task.queryTaskByTaskId", stringBuilder.toString());
                            if (jSONObject != null) {
                                String stringBuilder2 = "红包雨🎮完成[" +
                                        JsonUtil.getValueByPath(jSONObject, "result.taskDetailList.[0].taskName") +
                                        "]";
                                Log.other(stringBuilder2);
                                TimeUtil.sleep(this.executeIntervalInt);
                            }
                        }
                    }
                }
            }
            TimeUtil.sleep(this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep(this.executeIntervalInt);
        }
    }

    private void sign() {
        try {
            JSONObject requestString = requestString("com.alipay.yebpromobff.salaryday.prize.shareAndCheckin", "\"type\": \"checkin\"");
            if (requestString != null) {
                String stringBuilder = this.displayName +
                        "签到成功，获得[" +
                        JsonUtil.getValueByPath(requestString, "result.checkinAmount") +
                        "]";
                Log.other(stringBuilder);
                Status.setFlagToday(CompletedKeyEnum.SalarydaySign.name());
            }
        } catch (Exception e) {
            Log.printStackTrace(this.TAG, e);
        }
    }

    protected void handle() {
        if (!Status.hasFlagToday(CompletedKeyEnum.SalarydaySign.name())) {
            sign();
        }
        salarydayTask();
        payGameChance();
        exchangeYebExp();
    }
}