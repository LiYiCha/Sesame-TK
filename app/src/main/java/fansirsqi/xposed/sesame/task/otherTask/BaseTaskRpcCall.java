package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.StringUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class BaseTaskRpcCall {
    public static String taskQuery(String str) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.task.taskQuery", "[{\"appletId\":\"" + str +"\"}]");
    }
    public static String taskQuery2(String str) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.task.taskQuery", "[{\"appletId\":\"AP12202921\",\"completedBottom\":true,\"taskIds\":[]}]");
    }

    public static String taskTrigger(String str, String str2, String str3) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.task.taskTrigger", "[{\"appletId\":\"" + str + "\",\"stageCode\":\"" + str2 + "\",\"taskCenId\":\"" + str3 + "\"}]");
    }

    public static String signInTrigger(String str) {
        return RequestManager.requestString("com.alipay.loanpromoweb.promo.signin.trigger", "[{\"extInfo\":{},\"sceneId\":\"" + str + "\"}]");
    }

    public static void doTask(String str, String str2, String str3) {
        final String TO_RECEIVE = "TO_RECEIVE";
        final String SUCCESS = "success";
        final String NONE_SIGNUP = "NONE_SIGNUP";

        try {
            JSONObject taskQueryResponse;
            if (str.equals("AP12202921")){
                 taskQueryResponse = new JSONObject(taskQuery2(str));
            }else{
                 taskQueryResponse = new JSONObject(taskQuery(str));
            }

            if (!taskQueryResponse.optBoolean(SUCCESS)) {
                Log.error(str2 + ".查询taskQuery失败err:" + taskQueryResponse.optString("resultDesc"));
                return;
            }

            JSONArray taskDetailList = taskQueryResponse.getJSONObject("result").getJSONArray("taskDetailList");
            for (int i = 0; i < taskDetailList.length(); i++) {
                JSONObject taskDetail = taskDetailList.getJSONObject(i);
                if (!"USER_TRIGGER".equals(taskDetail.getString("sendCampTriggerType"))) {
                    continue;
                }

                String taskProcessStatus = taskDetail.getString("taskProcessStatus");
                if ("RECEIVE_SUCCESS".equalsIgnoreCase(taskProcessStatus)){
                    continue;
                }
                String taskId = taskDetail.getString("taskId");
                String taskTitle = JsonUtil.getValueByPath(taskDetail, "taskExtProps.TASK_MORPHO_DETAIL.title");

                // Handle different task process statuses
                JSONObject triggerResponse;
                switch (taskProcessStatus) {
                    case TO_RECEIVE:
                        triggerResponse = new JSONObject(taskTrigger(taskId, "receive", str));
                        if (!triggerResponse.optBoolean(SUCCESS)) {
                            Log.error(str2 + ".doTask.receive", triggerResponse.optString("resultDesc"));
                        }
                        break;

                    case NONE_SIGNUP:
                        triggerResponse = new JSONObject(taskTrigger(taskId, "signup", str));
                        if (!triggerResponse.optBoolean(SUCCESS)) {
                            Log.error(str2 + ".doTask.signup", triggerResponse.optString("resultDesc"));
                        }
                        break;

                    default:
                        break;
                }

                // Final task completion check and trigger
                if (!"SIGNUP_COMPLETE".equals(taskProcessStatus) && !NONE_SIGNUP.equals(taskProcessStatus) && !TO_RECEIVE.equals(taskProcessStatus)) {
                    Log.other(str3 + "[" + taskTitle + "]任务已经完成");
                } else {
                    triggerResponse = new JSONObject(taskTrigger(taskId, "send", str));
                    if (!triggerResponse.optBoolean(SUCCESS)) {
                        Log.error(str2 + ".doTask.send", triggerResponse.optString("resultDesc"));
                    }
                    Log.other(str3 + "[" + taskTitle + "]任务完成");
                }
                TimeUtil.sleep(RandomUtil.nextInt(5000, 7000));
            }
        } catch (Throwable th) {
            Log.error(str2, "doTask err:");
            Log.printStackTrace(str2, th);
        }
    }



    public static JSONObject programInvoke(Map<String, Object> map) throws JSONException {
        JSONObject jSONObject = new JSONObject(RequestManager.requestString("alipay.imasp.program.programInvoke", StringUtil.getJsonString(map)));
        if (jSONObject.getBoolean("isSuccess")) {
            return jSONObject;
        }
        Log.error("BaseTaskRpcCall.programInvoke err " + map);
        return null;
    }
}
