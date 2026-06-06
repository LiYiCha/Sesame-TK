package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.DataStore;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class LuckCard extends BaseCommTask {

    private static HashSet<String> tasksLuckCardLocal = new HashSet<>();
    public LuckCard() {
        this.displayName = "好运卡 🎯";
    }

    @Override
    protected void handle() {
        try {
            long hour = TimeUtil.getHourOfDay();
            if (hour < 7) {
                return;
            }
            if (!Status.hasFlagToday(CompletedKeyEnum.LuckCard.name())){
                consult();
            }
            newQueryTask();
        } catch (Exception e) {
            Log.printStackTrace(e);
        } finally {
            // 确保无论是否成功，都执行一次延时
            sleep();
        }
    }

    private void consult() throws Exception {
        Set<String> tasksLuckCard = DataStore.INSTANCE.get("blacklistedTasks_LuckCard", Set.class);
        if (tasksLuckCard==null){
            tasksLuckCard = new HashSet<>();
        }

        JSONObject request = requestString("com.alipay.pcreditcardweb.activity.LuckCard.consult", "");
        if (request == null) {
            Log.error(displayName+"[consult.request]debug--"+request);
            return;
        }
        int errorCode = request.optInt("error");
        if (errorCode == 1009 || errorCode == 48 || errorCode == 6004) {
            Log.other(displayName + " 请先过滑块验证!!! ");
            Status.setTemporaryStatusWithExpiry("LuckCard", 1000 * 60 * 5);
            Notify.sendNewNotification(displayName, "请先过滑块验证!!!5分钟后可重新执行");
            return;
        }
        String taskCenterId = JsonUtil.getValueByPath(request, "result.taskInfo.taskCenterId");
        if (taskCenterId.isEmpty()) {
            return;
        }

        sleep();

        JSONObject taskListResponse = new JSONObject(RequestManager.requestString("com.alipay.pcreditcardweb.activity.LuckCard.queryTaskList", "[{}]"));
        if (!taskListResponse.optBoolean("success")) {
            Log.error(displayName+"[consult.taskListResponse]debug--"+taskListResponse);
            return;
        }

        JSONArray taskArray = taskListResponse.getJSONArray("result");
        if (taskArray == null || taskArray.length() == 0) {
            return;
        }

        for (int i = 0; i < taskArray.length(); i++) {
            JSONObject task = taskArray.getJSONObject(i);
            String status = task.getString("taskProcessStatus");
            if ("RECEIVE_SUCCESS".equals(status)) {
                continue;
            }

            JSONObject extProps = task.getJSONObject("taskExtProps");
            //跳过不是浏览的任务
            if ("TRANSFORMER".equals(JsonUtil.getValueByPath(extProps, "TASK_TYPE"))) {
                continue;
            }
            //完成任务中心的任务
            if ("WAITING_TIME".equals(JsonUtil.getValueByPath(extProps, "TASK_TYPE"))){
                if (!Status.hasFlagToday(CompletedKeyEnum.GameCenterTaskGameCenter.name())) {
                    boolean gameCenter = gameCenter();
                    if (gameCenter) {
                        Status.setFlagToday(CompletedKeyEnum.GameCenterTaskGameCenter.name());
                    }
                }
            }

            String taskId = task.getString("taskId");
            // 跳过黑名单的任务
            if(tasksLuckCard.contains(taskId)){
                continue;
            }
            String subTitle = JsonUtil.getValueByPath(extProps, "TASK_MORPHO_DETAIL.subTitle");

            String stageCode = "NONE_SIGNUP".equals(status) ? "receive" : "send";
            String userCategory = "NONE_SIGNUP".equals(status) ? "toDayNewUser" : "tomorrowUser";

            String requestBody = String.format(
                    "\"pzConfig\":{\"name\":\"任务奖励\"},\"taskCamp\":{\"appletId\":\"%s\",\"stageCode\":\"%s\",\"taskCenId\":\"%s\"},\"userCategory\":\"%s\"",
                    taskId, stageCode, taskCenterId, userCategory);

            JSONObject triggerResponse = requestString(
                    "com.alipay.pcreditcardweb.activity.LuckCard.taskTrigger", requestBody);

            if (triggerResponse.optBoolean("success")) {
                Log.other(this.displayName + "完成[" + subTitle + "]");
                sleep();
            }else{
                Log.error(displayName+"[triggerResponse]完成任务失败--"+triggerResponse);
                tasksLuckCard.add(taskId);
                DataStore.INSTANCE.put("blacklistedTasks_LuckCard", tasksLuckCard);
            }
        }

        sleep();
        Status.setFlagToday(CompletedKeyEnum.LuckCard.name());
    }

    private void newQueryTask(){
        Set<String> tasksLuckCard = DataStore.INSTANCE.get("blacklistedTasks_LuckCard", Set.class);
        if (tasksLuckCard==null){
            tasksLuckCard = tasksLuckCardLocal;
        }
        String method = "com.alipay.pcreditbfweb.sdk.task.query";
        String params = "[{\"appletId\":\"PCCP_2024120204226387059\",\"bizScene\":\"HAOYUNKA_DAILY\",\"bizSceneFrom\":\"creditCard\"," +
                "\"extInfo\":{\"version\":1},\"requestFrom\":\"pccp\"}]";
        try {
            JSONObject request = new JSONObject(RequestManager.requestString(method, params));
            if (request.optBoolean("success")){
                JSONObject data = request.optJSONObject("data");
                JSONObject result = data.optJSONObject("result");
                JSONArray taskListResult = result.optJSONArray("taskListResult");
                for (int i = 0; i < taskListResult.length(); i++) {
                    JSONObject task = taskListResult.getJSONObject(i);
                    String taskStatus = task.optString("taskStatus");
                    String taskId = task.optString("taskId");
                    //获取任务类型
                    JSONObject taskExtProps = task.optJSONObject("taskExtProps");
                    String TASK_TYPE = taskExtProps.optString("TASK_TYPE");
                    //通过不是浏览任务
                    if (!TASK_TYPE.equals("BROWSER") || !taskStatus.equals("NOT_DONE") || tasksLuckCard.contains(taskId)){
                        continue;
                    }
                    //完成任务
                    boolean b = doNewTask(taskId);
                    if (!b){
                        tasksLuckCardLocal.add(taskId);
                        DataStore.INSTANCE.put("blacklistedTasks_LuckCard", tasksLuckCardLocal);
                        Log.runtime(displayName+"任务["+taskId+"]已加入黑名单");
                    }
                }
            }else{
                Log.error(displayName+"[newQueryTask.request]debug--"+request);
            }
        } catch (JSONException e) {
            Log.error(displayName+"[newQueryTask.request]debug--"+e);
        }
    }
    private boolean doNewTask(String appletId) {
        TimeUtil.sleep(RandomUtil.nextInt(1000, 3000));
        String method = "com.alipay.pcreditbfweb.sdk.task.trigger";

        // 动态生成 outBizNo，格式为 appletId +8位随机数 = 16位随机数字
        String outBizNo = appletId + RandomUtil.nextInt(10000000, 99999999);

        String params = "[{\"appletId\":\"" + appletId + "\",\"bizScene\":\"HAOYUNKA_DAILY\",\"bizSceneFrom\":\"creditCard\"," +
                "\"extInfo\":{\"name\":\"任务奖励\",\"ticketCampId\":\"CP182329534\",\"version\":1},\"needleParam\":{}," +
                "\"outBizNo\":\"" + outBizNo + "\",\"pccpId\":\"PCCP_2024120204226387059\",\"retryFlag\":true," +
                "\"stageCode\":\"send\",\"taskCentId\":\"AP16247630\",\"triggerRule\":\"TASK_LUCKY_TICKET_TRIGGER\"}]";
        try {
            JSONObject request = new JSONObject(RequestManager.requestString(method, params));
            if (request.optBoolean("success")) {
                JSONObject data = request.optJSONObject("data");
                JSONObject result = data.optJSONObject("result");
                String taskShowInfoStr = result.optString("taskShowInfo");

                // 解析 taskShowInfo 数组
                try {
                    JSONArray taskShowInfoArray = new JSONArray(taskShowInfoStr);
                    if (taskShowInfoArray.length() > 0) {
                        JSONObject cardInfoObj = taskShowInfoArray.getJSONObject(0);
                        String id = cardInfoObj.optString("id");
                        // 使用获取到的 id 调用 openCard 方法
                        if (id != null && !id.isEmpty()) {
                            openCard(id);
                        }
                    }
                } catch (JSONException e) {
                    Log.error(displayName + "[doNewTask.taskShowInfo]解析失败--" + e);
                }
                return true;
            } else {
                Log.error(displayName + "[doNewTask.request]debug--" + request);
            }
        } catch (JSONException e) {
            Log.error(displayName + "[doNewTask.request]debug--" + e);
        }
        return false;
    }

    private boolean openCard(String id){
        String method = "com.alipay.pcreditcardmarket.openLuckyCard";
        String params = "[{\"cardIds\":[\""+id+"\"],\"requestFrom\":\"pcreditcardweb\"}]";
        try {
            TimeUtil.sleep(RandomUtil.nextInt(1000, 3000));
            JSONObject s = new JSONObject(RequestManager.requestString(method, params));
            if (s.optBoolean("success")){
                JSONObject result = s.optJSONObject("result");
                JSONArray openCards = result.optJSONArray("openCards");
                JSONObject cardInfo = openCards.optJSONObject(0);
                String title = cardInfo.optString("title");
                Log.other(displayName + "获得[" + title + "]");
                return true;
            }else{
                Log.error(displayName+"[openCard.request]debug--"+s);
            }
        }catch (JSONException e){
            Log.error(displayName+"[openCard.request]debug--"+e);
        }
        return false;
    }

    //浏览15s游戏中心
    private boolean gameCenter() throws Exception {
        boolean isSuccess = false;
        JSONObject request = requestString("com.alipay.pcreditbfweb.sdk.task.trigger",
                "[{\"appletId\":\"AP13283002\"," +
                        "\"bizScene\":\"HAOYUNKA_DAILY\",\"bizSceneFrom\":\"creditCard\",\"extInfo\":{\"name\":\"任务奖励\"," +
                        "\"ticketCampId\":\"CP182329534\"},\"needleParam\":{},\"outBizNo\":\"AP1328300229106083\"," +
                        "\"pccpId\":\"PCCP_2024120204226387059\",\"retryFlag\":true,\"stageCode\":\"send\"," +
                        "\"taskCentId\":\"AP16247630\",\"taskType\":\"WAITING_TIME\",\"triggerRule\":\"TASK_LUCKY_TICKET_TRIGGER\"}]");
        if (request == null) {
            Log.error(displayName+"[gameCenter.request]debug--"+request);
            return false;
        }
        int errorCode = request.optInt("error");
        if (errorCode == 1009 || errorCode == 48 || errorCode == 6004) {
            Log.other(displayName + "错误 error --原因: " +request);
        }
        if (request.optBoolean("success")){
            Log.other(displayName + "完成[浏览15s游戏中心]");
            isSuccess = true;
        }
        return isSuccess;
    }
    /**
     * 统一延时方法
     */
    private void sleep() {
        TimeUtil.sleep(RandomUtil.nextInt(1000, 3000));
    }
}
