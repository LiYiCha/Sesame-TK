package fansirsqi.xposed.sesame.task.otherTask2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.antMember.AntMember;
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class KuaiDiFuLiJia extends MemberNew {
    private static final String TAG = "快递积分任务🎁";
    private int executeIntervalInt = 2000;

    private void listQuery(String str) {
        String str2 = "listQuery err ";
        try {
            JSONObject stringBuilder = new JSONObject(ApplicationHook.requestString("alipay.promoprod.task.listQuery", "[{\"consultAccessFlag\":true,\"taskCenInfo\":\"" + str + "\"}]"));
            if (stringBuilder.optBoolean("success")) {
                JSONArray jSONArray = stringBuilder.getJSONArray("taskDetailList");
                for (int i = 0; i < jSONArray.length(); i++) {
                    stringBuilder = jSONArray.getJSONObject(i);
                    String optString = stringBuilder.optString("taskId");
                    String optString2 = stringBuilder.optString("taskProcessStatus");
                    stringBuilder.optString("sendCampTriggerType");
                    String valueByPath = JsonUtil.getValueByPath(stringBuilder, "taskMaterial.taskCenInfo");
                    if (!valueByPath.isEmpty()) {
                        str = valueByPath;
                    }
                    if (!"RECEIVE_SUCCESS".equalsIgnoreCase(optString2)) {
                        trigger(optString, "signup", str);
                        stringBuilder = trigger(optString, "send", str);
                        if (stringBuilder != null || stringBuilder.optBoolean("success")) {
                            Log.other(TAG+"完成[" + JsonUtil.getValueByPath(stringBuilder, "prizeSendInfo.[0].prizeName") + "🎉]");
                        }
                    }
                }
                TimeUtil.sleep(RandomUtil.nextInt(10000,15000));
                return;
            }
            Log.error(TAG,"查询出错:"+ stringBuilder);
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            Log.error(TAG, str2+th);
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }
    private void listQuery2(String str) {
        String str2 = "listQuery2 err ";
        try {
            JSONObject stringBuilder = new JSONObject(ApplicationHook.requestString("alipay.promoprod.task.listQuery",
                    "[{\"consultAccessFlag\":true,\"extInfo\":{\"componentCode\":\"musi_test\"},\"taskCenInfo\":\""+str+"\"}]"));
            if (stringBuilder.optBoolean("success")) {
                JSONArray jSONArray = stringBuilder.getJSONArray("taskDetailList");
                for (int i = 0; i < jSONArray.length(); i++) {
                    stringBuilder = jSONArray.getJSONObject(i);
                    String optString = stringBuilder.optString("taskId");
                    String optString2 = stringBuilder.optString("taskProcessStatus");
                    stringBuilder.optString("sendCampTriggerType");
                    String valueByPath = JsonUtil.getValueByPath(stringBuilder, "taskMaterial.taskCenInfo");
                    if (!valueByPath.isEmpty()) {
                        str = valueByPath;
                    }
                    if (!"RECEIVE_SUCCESS".equals(optString2)) {
                        trigger(optString, "signup", str);
                        stringBuilder = trigger(optString, "send", str);
                        if (stringBuilder != null || stringBuilder.optBoolean("success")) {
                            Log.other(TAG+"完成✅[" + JsonUtil.getValueByPath(stringBuilder, "prizeSendInfo.[0].prizeName") + "🎉]");
                        }
                    }
                }
                TimeUtil.sleep(RandomUtil.nextInt(10000,15000));
                return;
            }
            Log.error(TAG,"查询2出错:"+ stringBuilder);
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            Log.error(TAG, "listQuery2 err:"+th);
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private JSONObject trigger(String str, String str2, String str3) throws JSONException {
        JSONObject stringBuilder = new JSONObject(RequestManager.requestString("alipay.promoprod.applet.trigger",
                "[{\"appletId\":\""+str+"\"," +
                        "\"stageCode\":\""+str2+"\"," +
                        "\"taskCenInfo\":\""+str3+"\"}]"));
        if (stringBuilder.optBoolean("success")) {
            return stringBuilder;
        }
        Log.error(TAG, "❌快递积分["+str+"]任务错误,原数据:" + stringBuilder);
        return null;
    }

    public void handle() {
        try {
            if (Status.hasFlagToday(CompletedKeyEnum.KuaiDiFuLiJia.name())) {
                return;
            }
            listQuery("MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA");
            listQuery2("MZVPQ0DScvD6NjaPJzk8iCCWtq%2FRt4kh");
            Status.setFlagToday(CompletedKeyEnum.KuaiDiFuLiJia.name());
            TimeUtil.sleep(this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep(this.executeIntervalInt);
        }finally {
            Status.setFlagToday(CompletedKeyEnum.KuaiDiFuLiJia.name());
            TimeUtil.sleep(this.executeIntervalInt);
        }
    }
}