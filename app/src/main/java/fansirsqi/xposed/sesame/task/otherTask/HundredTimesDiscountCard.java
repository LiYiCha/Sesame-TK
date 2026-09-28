package fansirsqi.xposed.sesame.task.otherTask;


import org.json.JSONArray;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.util.Log;

public class HundredTimesDiscountCard extends BaseCommTask {
    public HundredTimesDiscountCard() {
        this.displayName = "百次立减卡🐟";
        this.hoursKeyEnum = CompletedKeyEnum.HundredTimesDiscountCard;
    }

    private void finishTask(JSONObject jSONObject) {
        String str = "\"taskId\": \"";
        String str2 = "\"appletId\": \"";
        try {
            String string = jSONObject.getString("taskCenId");
            String string2 = jSONObject.getString("taskId");
            String string3 = jSONObject.getString("taskStatus");
            jSONObject = jSONObject.getJSONObject("taskExtProps");
            String str3 = "\"";
            if ("NOT_DONE".equals(string3)) {
                StringBuilder stringBuilder = new StringBuilder(str2);
                stringBuilder.append(string2);
                stringBuilder.append("\",\"stageCode\": \"send\",\"taskCenId\": \"");
                stringBuilder.append(string);
                stringBuilder.append(str3);
                if (requestString("alipay.promoprod.applet.trigger", stringBuilder.toString()) == null) {
                    return;
                }
            } else if ("NONE_SIGNUP".equals(string3)) {
                return;
            }
            StringBuilder stringBuilder2 = new StringBuilder(str);
            stringBuilder2.append(string2);
            stringBuilder2.append(str3);
            if (requestString("alipay.ofpgrowth.hundredtimesdiscountcard.task.receive", stringBuilder2.toString()) != null) {
                String stringBuilder3 = this.displayName +
                        "完成[" +
                        jSONObject.optString("taskTitle") +
                        "]获得" +
                        jSONObject.optString("prizeCount") +
                        "次";
                Log.other(stringBuilder3);
            }
        } catch (Exception e) {
            Log.printStackTrace(this.TAG, e);
        }
    }

    private void listQuery() {
        try {
            JSONObject requestString = requestString("alipay.ofpgrowth.hundredtimesdiscountcard.task.listquery", "\"extInfo\": {\"needFilterTaskTypeList\": [\"\",\"\"]}");
            if (requestString != null) {
                requestString = requestString.getJSONObject("data");
                signIn(requestString.optJSONObject("signInTaskInfo"));
                JSONArray optJSONArray = requestString.optJSONArray("taskList");
                if (optJSONArray != null) {
                    for (int i = 0; i < optJSONArray.length(); i++) {
                        finishTask(optJSONArray.getJSONObject(i));
                    }
                }
            }
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
        }
    }

    private void signIn(JSONObject jSONObject) {
        String str = "\"";
        if (jSONObject != null) {
            try {
                String string = jSONObject.getString("taskCenterId");
                String str2 = "";
                JSONArray jSONArray = jSONObject.getJSONArray("taskDetailList");
                for (int i = 0; i < jSONArray.length(); i++) {
                    JSONObject jSONObject2 = jSONArray.getJSONObject(i);
                    if (jSONObject2.getBoolean("hasToday")) {
                        if (!"HAS_COMPLETED".equals(jSONObject2.getString("status"))) {
                            str2 = jSONObject2.getString("taskId");
                        } else {
                            return;
                        }
                    }
                }
                if (!str2.isEmpty()) {
                    String stringBuilder = "\"appletId\": \"" +
                            str2 +
                            "\",\"stageCode\": \"send\",\"taskCenId\": \"" +
                            string +
                            str;
                    requestString("alipay.promoprod.applet.trigger", stringBuilder);
                    String stringBuilder2 = "\"chInfo\": \"signInTask\",\"taskId\": \"" +
                            str2 +
                            str;
                    jSONObject = requestString("alipay.ofpgrowth.hundredtimesdiscountcard.task.receive", stringBuilder2);
                    if (jSONObject != null) {
                        String stringBuilder3 = this.displayName +
                                "签到成功+" +
                                jSONObject.optString("modelAmount") +
                                jSONObject.optString("modelUnit");
                        Log.other(stringBuilder3);
                    }
                }
            } catch (Throwable th) {
                Log.printStackTrace(this.TAG, th);
            }
        }
    }

    protected void handle() {
        listQuery();
    }
}