package fansirsqi.xposed.sesame.task.otherTask;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GoldTicket extends BaseCommTask {
    public GoldTicket() {
        this.displayName = "黄金票🕌";
        //this.hoursKeyEnum = CompletedKeyEnum.GoldTicket;
    }

    private void getRankTasks() {
        for (int i = 0; i <= 3; i++) {
            try {
                String requestBody = String.format(
                        "\"holdingBoardBaseInfo\": {\n" +
                                "    \"activityBizDate\": \"%s\",\n" +
                                "    \"holdLevel\": \"TEN\",\n" +
                                "    \"holdStyle\": \"ALL\",\n" +
                                "    \"profitType\": \"AMOUNT\",\n" +
                                "    \"timeRange\": \"MONTH\",\n" +
                                "    \"type\": \"ACTIVITY\"\n" +
                                "},\"iphoneSystem\": \"14\"",
                        TimeUtil.DATE_FORMAT_THREAD_LOCAL);

                JSONObject response = requestString("com.alipay.promobffweb.needle.getRankTasks", requestBody);
                if (response == null) {
                    break;
                }

                String groupType = response.getString("groupType");
                if ("TODAY_DONE".equals(groupType)) {
                    break;
                }

                String campId = response.optString("unlockTaskCertCampId");
                String taskId = response.getString("taskId");
                String taskCenId = response.getString("taskCenId");
                String taskType = response.getString("taskType");
                String buttonDesc = response.getString("buttonDesc");

                String completeRequestBody = String.format(
                        "\"holdingBoardBaseInfo\": {\n" +
                                "    \"activityBizDate\": \"%s\",\n" +
                                "    \"holdLevel\": \"TEN\",\n" +
                                "    \"holdStyle\": \"ALL\",\n" +
                                "    \"profitType\": \"AMOUNT\",\n" +
                                "    \"timeRange\": \"MONTH\",\n" +
                                "    \"type\": \"ACTIVITY\"\n" +
                                "},\"campId\": \"%s\",\"groupType\": \"%s\",\"taskCenId\": \"%s\",\"taskId\": \"%s\",\"taskType\": \"%s\"",
                        TimeUtil.DATE_FORMAT_THREAD_LOCAL, campId, groupType, taskCenId, taskId, taskType);

                JSONObject completeResponse = requestString("com.alipay.promobffweb.needle.rankCompletTask", completeRequestBody);
                if (completeResponse != null && completeResponse.optBoolean("success")) {
                    Log.other(this.displayName + "完成[" + buttonDesc + "]获得" +
                            completeResponse.optString("prizeNumber") +
                            completeResponse.optString("prizeTitleSubfix"));
                }

            } catch (Throwable th) {
                Log.error( this.displayName + ".getRankTasks error: ", String.valueOf(th));
            }

            TimeUtil.sleep((long) this.executeIntervalInt);
        }

        TimeUtil.sleep((long) this.executeIntervalInt);
    }


    private void goldBillCollect(String str) {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(str);
            stringBuilder.append("\"trigger\":\"Y\"");
            JSONObject requestString = requestString("com.alipay.wealthgoldtwa.goldbill.v2.index.collect", stringBuilder.toString());
            if (requestString != null && requestString.optBoolean("success")) {
                JSONArray jSONArray = requestString.getJSONObject("result").getJSONArray("collectedList");
                int length = jSONArray.length();
                if (length != 0) {
                    for (int i = 0; i < length; i++) {
                        StringBuilder stringBuilder2 = new StringBuilder();
                        stringBuilder2.append(this.displayName);
                        stringBuilder2.append("[");
                        stringBuilder2.append(jSONArray.getString(i));
                        stringBuilder2.append("]");
                        Log.other(stringBuilder2.toString());
                    }
                    TimeUtil.sleep((long) this.executeIntervalInt);
                    return;
                }
            }
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private void goldTicket() {
        String str = "success";
        try {
            JSONObject jSONObject = new JSONObject(OtherTaskRpcCall.goldBillIndex());
            String str2 = "resultDesc";
            if (jSONObject.optBoolean(str)) {
                JSONArray jSONArray = jSONObject.getJSONObject("result").getJSONArray("cardModel");
                for (int i = 0; i < jSONArray.length(); i++) {
                    JSONObject jSONObject2 = jSONArray.getJSONObject(i);
                    String string = jSONObject2.getString("cardTypeId");

                    if ("H5_GOLDBILL_TASK".equals(string)) {
                        JSONArray jSONArray2 = (JSONArray) JsonUtil.getValueByPathObject(jSONObject2, "dataModel.jsonResult.tasks.todo");
                        if (jSONArray2 != null) {
                            for (int i2 = 0; i2 < jSONArray2.length(); i2++) {
                                JSONObject jSONObject3 = jSONArray2.getJSONObject(i2);
                                String string2 = jSONObject3.getString("title");

                                if (JsonUtil.getValueByPath(jSONObject3, "extInfo.morphoDetail.task_type").contains("TERM_LIFE_INSERANCE")) {
                                    String string3 = jSONObject3.getString("taskId");
                                    JSONObject jSONObject4 = new JSONObject(OtherTaskRpcCall.goldBillTrigger(string3));
                                    StringBuilder stringBuilder;

                                    if (jSONObject4.optBoolean(str)) {
                                        JSONObject jSONObject5 = new JSONObject(OtherTaskRpcCall.taskQueryPush(string3));
                                        TimeUtil.sleep(3000);
                                        if (jSONObject5.optBoolean(str)) {
                                            Log.other(this.displayName + "[" + string2 + "]" + jSONObject3.getString("subTitle"));
                                            TimeUtil.sleep(1500);
                                        } else {
                                            Log.error(this.TAG + ".goldTicket.taskQueryPush", jSONObject5.optString(str2));
                                        }
                                    } else {
                                        Log.error(this.TAG + ".goldTicket.goldBillTrigger", jSONObject4.optString(str2));
                                    }
                                }
                            }
                        }
                    }
                }
                TimeUtil.sleep((long) this.executeIntervalInt);
            } else {
                Log.error(this.TAG + ".goldTicket.goldBillIndex", jSONObject.optString(str2));
                TimeUtil.sleep((long) this.executeIntervalInt);
            }
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }


    private void submit() {
        try {
            JSONObject requestString = requestString("com.alipay.wealthgoldtwa.goldbill.consume.query", "\"client_pkg_version\": \"0.0.8\"");
            if (requestString == null || !requestString.optBoolean("success")) {
                return;
            }

            JSONObject result = requestString.getJSONObject("result");
            JSONObject goldbillInfo = result.getJSONObject("goldbillInfo");
            JSONArray goldProducts = result.getJSONArray("goldProducts");

            if (goldbillInfo.getInt("availableAmount") < 100 || goldProducts.length() == 0) {
                return;
            }

            int exchangeAmount = goldbillInfo.getInt("exchangeAmount");
            String exchangeMoney = goldbillInfo.getString("exchangeMoney");
            String productId = goldProducts.getJSONObject(0).getString("productId");

            StringBuilder submitData = new StringBuilder("\"amount\": ")
                    .append(exchangeAmount)
                    .append(",\"money\": \"")
                    .append(exchangeMoney)
                    .append("\",\"prizeName\": \"黄金\",\"prizeType\": \"GOLD\",\"productId\": \"")
                    .append(productId)
                    .append("\"");

            JSONObject submitResponse = requestString("com.alipay.wealthgoldtwa.goldbill.consume.submit", submitData.toString());
            if (submitResponse == null || !submitResponse.optBoolean("success")) {
                return;
            }

            String writeOffNo = JsonUtil.getValueByPath(submitResponse, "result.writeOffNo");
            if (writeOffNo.isEmpty()) {
                return;
            }

            StringBuilder resultData = new StringBuilder("\"writeOffNo\": \"")
                    .append(writeOffNo)
                    .append("\"");

            if (requestString("com.alipay.wealthgoldtwa.goldbill.v4.consume.result", resultData.toString()) != null) {
                Log.other(this.displayName + "提取成功[" + exchangeMoney + "元]");
            }

        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
        }
    }


    private void triggerBigPrize() {
        try {
            JSONObject requestStringAllNew = requestStringAllNew("com.alipay.wealthgoldtwa.needle.task.triggerBigPrize", "[null]");
            if (requestStringAllNew != null && requestStringAllNew.optBoolean("success")) {
                String valueByPath = JsonUtil.getValueByPath(requestStringAllNew, "result.bigPrizeInfo.prizeName");
                Log.other(displayName+"领礼包["+valueByPath+"]");
            }
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
        }
    }

    private void wealthgoldtwa() {
        try {
            for (int i = 0; i < 3; i++) {
                JSONObject requestString = requestString("com.alipay.wealthgoldtwa.needle.v2.index", "\"bizScene\": \"gold\",\"chInfo\": \"gold\",\"forceNewVersion\": 0,\"taskId\": \"\"");
                if (requestString == null  || !requestString.optBoolean("success")) {
                    continue;
                }

                Object upsertDataTask = JsonUtil.getValueByPathObject(requestString, "result.upsertData.task.tasks.todo");
                if (!(upsertDataTask instanceof JSONArray)) {
                    continue;
                }

                JSONArray jSONArray = (JSONArray) upsertDataTask;
                int length = jSONArray.length();
                if (length == 0) {
                    continue;
                }

                for (int i2 = 0; i2 < length; i2++) {
                    JSONObject jSONObject = jSONArray.getJSONObject(i2);
                    String taskId = jSONObject.getString("taskId");
                    String title = jSONObject.getString("title");
                    String amount = jSONObject.getString("amount");
                    String taskType = jSONObject.getString("taskType");
                    if (!taskType.equalsIgnoreCase("BROWSE")){
                        continue;
                    }

                    String triggerData = "\"taskId\":\"" + taskId + "\"";
                    if (requestString("com.alipay.wealthgoldtwa.goldbill.v4.task.trigger", triggerData) == null) {
                        continue;
                    }

                    String queryData = "\"mode\": 1,\"taskId\":\"" + taskId + "\"";
                    JSONObject jsonObject = requestString("com.alipay.wealthgoldtwa.needle.taskQueryPush", queryData);
                    TimeUtil.sleep(3000);
                    if (jsonObject != null && jsonObject.optBoolean("success")) {
                        Log.other(this.displayName + "完成[" + title + "]+" + amount);
                        TimeUtil.sleep((long) this.executeIntervalInt);
                        triggerBigPrize();
                    }
                }

                TimeUtil.sleep((long) this.executeIntervalInt);
            }
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
        }
    }


    private void weekModeSignIn() {
        try {
            JSONObject requestString = requestString("com.alipay.finaggexpbff.flow.h5Query", "\"options\":{\"applicationCode\":\"EQUITY-FH-V2\",\"workflowCode\":\"weekModeSignIn\"}");
            if (requestString != null && requestString.optBoolean("SUCCESS")) {
                requestString = (JSONObject) JsonUtil.getValueByPathObject(requestString, "result.prizeInfo.basePrize");
                if (requestString != null && requestString.optBoolean("success")) {
                    String price = requestString.getString("price");
                    String unit = requestString.getString("unit");
                    Log.other(displayName+"签到获得["+price+"|"+unit+"]");
                    TimeUtil.sleep((long) this.executeIntervalInt);
                    return;
                }
            }
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private void weeklyWelfare() {
        try {
            JSONObject requestString = requestString("com.alipay.finaggexpbff.needle.weeklyWelfare.index", "\"chInfo\": \"goldbill\",\"modeBitMask\": 513");
            if (requestString == null || requestString.optBoolean("success")) {
                return;
            }
            Object timelineObj = JsonUtil.getValueByPathObject(requestString, "result.upsertData.sign.timeline");
            if (!(timelineObj instanceof JSONArray)) {
                return;
            }
            JSONArray jSONArray = (JSONArray) timelineObj;
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject jSONObject = jSONArray.getJSONObject(i);
                if (jSONObject.optBoolean("isToday") && !jSONObject.optBoolean("signed")) {
                    String prizeNum = jSONObject.getString("prizeNum");

                    String postData = "\"basePrize\": " +
                            jSONObject.getString("basePrizeNum") + ",\"prizeNum\": \"" +
                            prizeNum + "\",\"type\": \"SIGN\"";
                    JSONObject jsonObject = requestString("com.alipay.finaggexpbff.needle.weeklyWelfare.trigger", postData);
                    if (jsonObject!= null&& jsonObject.optBoolean("success")) {
                        Log.other(this.displayName + "每周福利签到获得[" + prizeNum + "]");
                        TimeUtil.sleep((long) this.executeIntervalInt);
                        return;
                    }
                }
            }

            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
            TimeUtil.sleep(1500);
        }
    }

    private void newIndexTask(){
        try {
            String methodIndex = "com.alipay.finaggexpbff.needle.welfareCenter.index";
            String param = "[{}]";
            JSONObject res = new JSONObject(RequestManager.requestString(methodIndex, param));
            if (res.optBoolean("success")) {
                JSONObject result = res.optJSONObject("result");
                JSONObject goldbillTasks = result.optJSONObject("goldbillTasks");
                JSONArray todo = goldbillTasks.optJSONArray("todo");
                for (int i = 0; i < todo.length(); i++) {
                    TimeUtil.sleep(RandomUtil.nextInt(15000,16000));
                    JSONObject task = todo.getJSONObject(i);
                    String taskId = task.getString("id");
                    String title = task.getString("title");
                    String taskType = task.getString("taskType");
                    String amount = task.getString("amount");
                    if (!taskType.equalsIgnoreCase("BROWSE")){
                        continue;
                    }
                    String s = todoTask(taskId);
                    JSONObject json = new JSONObject(s);
                    if (json.optBoolean("success")){
                        Log.other(displayName + "完成[" + title + "]+" + amount);
                    }
                }
            }
        }catch (JSONException  e){
            Log.error(TAG + "[.newIndexTask]任务异常: " + e);
        }
    }
    private String todoTask(String taskId){
        String method = "com.alipay.wealthgoldtwa.needle.taskQueryPush";
        String params = "[{\"mode\":1,\"taskId\":\""+taskId+"\"}]";
        return RequestManager.requestString(method, params);
    }

    protected void handle() {
        //Log.other(displayName + "开始执行");
        try {
                newIndexTask();
                weekModeSignIn();
                //getRankTasks();
                weeklyWelfare();
                //goldBillCollect("\"campId\":\"CP1417744\",\"directModeDisableCollect\":true,\"from\":\"antfarm\",");
                goldTicket();
                wealthgoldtwa();
                //goldBillCollect("");
                submit();
        } catch (Throwable th){
            Log.printStackTrace(displayName, th);
        }finally {
            Status.setFlagToday("GoldTicket_TaskCompleted");;
            //Log.other(displayName+"执行完毕");
        }
    }
}