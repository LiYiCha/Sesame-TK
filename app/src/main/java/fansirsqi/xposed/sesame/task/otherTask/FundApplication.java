package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class FundApplication extends OtherTask {
    private static final String TAG = "摇红包💊";
    private int executeIntervalInt = 2000;

    public void handle(int i) throws JSONException {
        try {
            if (Status.hasFlagToday("FundApplication")){
                return;
            }
            this.executeIntervalInt = i;
            JSONObject response = new JSONObject(recommend());
            if (!response.optBoolean("success")) {
                return;
            }
            JSONObject content = (JSONObject) JsonUtil.getValueByPathObject(response, "model.modules.[0].content");
            if (content == null) {
                return;
            }
            // 处理签到任务
            String lightTaskId = JsonUtil.getValueByPath(content, "lightFireArea.lightTaskId");
            if (!lightTaskId.isEmpty()) {
                signIn(lightTaskId);
            }

            JSONObject mainArea = content.getJSONObject("mainArea");

            // 处理 giftinocenterTask 任务
            String taskCenterIdKey = JsonUtil.getValueByPath(content, "taskArea.taskCenterIdKey");
            if (!Status.hasFlagToday(CompletedKeyEnum.GiftinocenterTask.name())) {
                if (giftinocenterTask(taskCenterIdKey)) {
                    Status.setFlagToday(CompletedKeyEnum.GiftinocenterTask.name());
                    TimeUtil.sleep(RandomUtil.nextInt(4000, 7000));
                }
                giftinocenter(mainArea.optString("initCampId"));
            }

            // 摇红包任务
            String certificateTmplId = mainArea.getString("certificateTmplId");
            String mainActiveId = mainArea.getString("mainActiveId");
            certificate(certificateTmplId, mainActiveId);

        } catch (JSONException e) {
            Log.error(TAG, "handle出错:" + e);
        }
    }

    //  签到
    private void signIn(String str) throws JSONException {
        if (Status.hasFlagToday(CompletedKeyEnum.FundApplicationSignIn.name())) {
            return;
        }

        HashMap<String, Object> params = new HashMap<>();
        params.put("appletId", str);
        params.put("stageCode", "send");
        params.put("source", "giftinocenter");

        JSONObject response = new JSONObject(OtherTaskRpcCall.appletTrigger(params));

        if (response.getBoolean("success")) {
            Log.other("摇红包💊签到成功");
            Status.setFlagToday(CompletedKeyEnum.FundApplicationSignIn.name());
        } else {
            Log.error(TAG, "signIn.appletTrigger" + response.optString("resultDesc"));
        }
    }

    //摇红包
    private void giftinocenter(String str) throws JSONException {
        JSONObject json = new JSONObject(promokernelTrigger(str));

        if (!json.optBoolean("success", false)) {
            //Log.error(TAG, "进行摇红包出错json:" + json);
            Log.error(TAG, "进行摇红包出错json:" + json.optString("errorMsg"));
            return;
        }

        JSONObject prizeSendInfo = (JSONObject) JsonUtil.getValueByPathObject(json, "prizeSendInfo.[0]");
        if (prizeSendInfo == null) {
            return;
        }

        String activityId = JsonUtil.getValueByPath(prizeSendInfo, "prizeProperty.activityId");

        if (activityId.isEmpty()) {
            Log.other("摇红包🌶获得[" + prizeSendInfo.optString("prizeName") + "]");
            TimeUtil.sleep(3012);
            return;
        }

        JSONObject jSONObject3 = new JSONObject(giftMatch(activityId, "", "", "", ""));
        if (!jSONObject3.optBoolean("success")) {
            Log.error(TAG, "进行摇红包出错jSONObject3:" + jSONObject3);
            return;
        }

        JSONObject bcActivityVO = jSONObject3.getJSONObject("bcActivityVO");
        if (bcActivityVO.optBoolean("activityEnd")) {
            return;
        }

        JSONObject taskVO = bcActivityVO.getJSONObject("taskVO");
        if (!"inComplete".equals(taskVO.getString("taskState"))) {
            return;
        }

        JSONObject taskParams = taskVO.getJSONObject("taskParams");
        String taskToken = taskParams.getString("taskToken");

        if (taskToken.isEmpty()) {
            return;
        }

        String url = taskParams.getString("url");
        Matcher matcher = Pattern.compile("appId=([^&]+)").matcher(url);
        String appId = matcher.find() ? matcher.group(1) : "2018081461095002";

        JSONObject jSONObject7 = new JSONObject(giftMatch(activityId, appId, url, taskToken, "MERCHANT_MINI_APP"));
        if (!jSONObject7.optBoolean("success")) {
            Log.error(TAG, "giftInoCenter.giftMatch1" + jSONObject7.optString("resultDesc"));
            return;
        }

        JSONObject bcTaskVO = (JSONObject) JsonUtil.getValueByPathObject(jSONObject7, "bcActivityVO.taskVO");
        if (bcTaskVO == null || !"complete".equals(bcTaskVO.getString("taskState"))) {
            Log.other("任务非complete");
            return;
        }

        JSONObject jSONObject9 = new JSONObject(giftComplete(activityId, appId, url, bcTaskVO.getString("taskParams.taskToken")));
        if (jSONObject9.getBoolean("success")) {
            Log.other("摇红包🎁获得[" + JsonUtil.getValueByPath(jSONObject9, "assetVOs.[0].showAmount") + "]元");
            TimeUtil.sleep(3012);
        } else {
            Log.error(TAG, "giftInoCenter.giftMatch1" + jSONObject9.optString("resultDesc"));
        }
    }


    //完成任务
    private boolean giftinocenterTask(String taskCenInfo) {
        try {
            //
            JSONObject response = new JSONObject(OtherTaskRpcCall.taskListQuery(taskCenInfo));
            if (!response.has("success")) {
                Log.error(TAG, "giftinocenterTask.taskListQuery: " + response.optString("resultDesc"));
                return false;
            }
            if (!response.has("taskDetailList")) {
                Log.error(TAG, "摇红包没有taskDetailList'");
                return false;
            }
            JSONArray taskDetailList = response.getJSONArray("taskDetailList");

            for (int i = 0; i < taskDetailList.length(); i++) {
                JSONObject taskDetail = taskDetailList.getJSONObject(i);

                if (!taskDetail.has("taskProcessStatus")) {
                    Log.error(TAG, "没有 'taskProcessStatus' in taskDetailList[" + i + "]");
                    continue;
                }

                String taskProcessStatus = taskDetail.getString("taskProcessStatus");
                String triggerType = taskDetail.getString("sendCampTriggerType");

                if ("RECEIVE_SUCCESS".equals(taskProcessStatus) || "EVENT_TRIGGER".equals(triggerType)) {
                    continue;
                }
                String taskId = taskDetail.getString("taskId");
                // 领取任务
                appletTask(taskId, taskCenInfo);
                // 执行任务参数构建

                // 调用任务触发接口
                JSONObject triggerResponse = null;

                JSONObject taskMaterial =taskDetail.optJSONObject("taskMaterial");
                String bizId = taskMaterial.optString("bizId","");
                String taskMainTitle = taskMaterial.optString("taskMainTitle","");
                //处理任务
                if (!bizId.isEmpty()){
                    triggerResponse = new JSONObject(OtherTaskRpcCall.taskFinish(bizId));
                } else {
                    HashMap<String, Object> params2 = new HashMap<>();
                    params2.put("appletId", taskId);
                    params2.put("stageCode", "send");
                    params2.put("source", "giftinocenter");
                    params2.put("taskCenId", taskCenInfo);
                    params2.put("chinfo", "bc_sydoudi");
                    params2.put("outBizNo", taskId + System.currentTimeMillis());
                    triggerResponse = new JSONObject(OtherTaskRpcCall.appletTrigger(params2));
                    if (!triggerResponse.optBoolean("success")){
                        HashMap<String, Object> params = new HashMap<>();
                        params.put("appletId", taskId);
                        params.put("stageCode", "send");
                        params.put("source", "giftinocenter");
                        params.put("taskCenId", taskCenInfo);
                        params.put("chinfo", "bcyx_dytx");
                        params.put("outBizNo", taskId + System.currentTimeMillis());
                        triggerResponse = new JSONObject(OtherTaskRpcCall.taskTrigger(params));
                    }
                }

                if (triggerResponse.optBoolean("success")) {
                    String taskTitle = JsonUtil.getValueByPath(taskDetail, "taskMaterial.taskMainTitle");
                    Log.other("摇红包💊完成[" + taskTitle + "]");
                    TimeUtil.sleep(RandomUtil.nextInt(7000, 9000));
                } else {
                    Log.error(TAG, "摇红包["+taskMainTitle+"]任务执行错误: " + triggerResponse.optString("resultDesc"));
                    continue;
                }
            }

            TimeUtil.sleep(this.executeIntervalInt);
            return true;

        } catch (Throwable th) {
            Log.error(TAG, "摇红包任务错误:");
            Log.printStackTrace(TAG, th);
            return false;
        } finally {
            TimeUtil.sleep(this.executeIntervalInt);
        }
    }

    //领取任务
    private void appletTask(String taskId, String taskCenInfo) {
        String method = "alipay.promoprod.applet.trigger";
        String s = RequestManager.requestString(method,
                "[{\"appletId\":\""+taskId+"\",\"chinfo\":\"bc_sydoudi\",\"outBizNo\":\""+taskId+ System.currentTimeMillis()+"\"," +
                        "\"source\":\"giftinocenter\",\"stageCode\":\"send\",\"taskCenInfo\":\""+taskCenInfo+"\"}]");
    }
    // FundApplication.java
    private void certificate(String str, String str2) {
        try {
            String response = RequestManager.requestString("alipay.giftinocenter.camp.campActivity.certificateNum", "[{\"certTemplateId\":\"" + str + "\"}]");
            JSONObject jSONObject = new JSONObject(response);
            if (1009 == jSONObject.optInt("error")){
                Log.other(TAG, "摇红包网络错误，风控中!!!!!!!");
                Status.setFlagToday("FundApplication");
                return;
            }
            if (!jSONObject.optBoolean("success", false)) {
                Log.error(TAG, "查询摇红包次数失败: " + jSONObject.optString("resultDesc", "Unknown error"));
                return;
            }
            int availableNum = jSONObject.optInt("availableNum", 0);
            for (int i = 0; i < availableNum; i++) {
                giftinocenter(str2);
                TimeUtil.sleep(RandomUtil.nextInt(7000, 9000));
            }
        } catch (JSONException e) {
            Log.error(TAG, "查询摇红包次数JSON错误: " + e.getMessage());
        } catch (Exception e) {
            Log.error(TAG, "查询摇红包次数错误: " + e.getMessage());
        }
    }



    private String recommend() {
        return RequestManager.requestString("alipay.fundapplication.op.module.recommend", "[{\"bizCode\":\"RED_ENVELOPE\",\"factors\":{\"chInfo\":\"bcyx_dytx\"},\"moduleCodes\":[\"INTERACT_PROMO\"],\"system\":\"fundapplication\"}]");
    }

    private String promokernelTrigger(String str) {
        return RequestManager.requestString("alipay.promoprod.camp.promokernel.trigger", "[{\"campInfo\":\"" + str + "\"}]");
    }

    private String giftMatch(String str, String str2, String str3, String str4, String str5) {
        return RequestManager.requestString("alipay.giftinocenter.gift.activity.match", "[{\"activityId\":\"" + str + "\",\"extInfoMap\":{\"checkMode\":\"N\",\"groupInstanceId\":\"\",\"merchantAppId\":\"" + str2 + "\",\"merchantPageUrl\":\"" + str3 + "\",\"taskToken\":\"" + str4 + "\"},\"solutionCode\":\"" + str5 + "\",\"specialCode\":\"\"}]");
    }

    private String giftComplete(String str, String str2, String str3, String str4) {
        return RequestManager.requestString("alipay.giftinocenter.gift.activity.complete", "[{\"activityId\":\"" + str + "\",\"extInfoMap\":{\"checkMode\":\"N\",\"groupInstanceId\":\"\",\"merchantAppId\":\"" + str2 + "\",\"merchantPageUrl\":\"" + str3 + "\",\"taobaoLiveUserId\":\"\",\"taskToken\":\"" + str4 + "\"}        }]");
    }
}
