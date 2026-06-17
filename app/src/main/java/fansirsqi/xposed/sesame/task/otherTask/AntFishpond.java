package fansirsqi.xposed.sesame.task.otherTask;

import static java.util.UUID.randomUUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.antOrchard.AntOrchardRpcCall;
import fansirsqi.xposed.sesame.util.GlobalThreadPools;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import kotlinx.serialization.json.Json;

/** @noinspection unchecked*/
public class AntFishpond extends BaseCommTask {
    private Integer fishCount = 0;
    private Integer leftFishTimes = 0;//还剩需要捕鱼次数才可以领取鱼竿
    private Integer rodCount = 0; // 鱼竿总数

    private String fishData = "";
    // 不做游戏任务
    private static final Pattern NC_GAME_PATTERN =Pattern.compile("FISHPOND_NCLY_GAME_.+_PLAY");
    private static final Pattern NC_GAME_PATTERN2 =Pattern.compile("FISHPOND_NCLY_GAME_.+_PLAYO");
    
    // 失败任务缓存，避免重复尝试
    private final Set<String> failedTaskCache = new HashSet<>();

    private final List<String> notTaskIds = new ArrayList<>() {
        {
            //add("NORMAL_WANYOUXI"); //砸金蛋
            add("cy25wf_yt_dgwyx30");
            add("ANTFISHPOND_WECHAT_SHARE"); //微信分享
            //add("NORMAL_NONGCHANGFUFANG"); //进入鱼塘
            // 新增需要过滤的任务ID

        }
    };

    private String getData() {
        return getData("GameCenter");
    }

    private String getData(String str) {
        return MessageFormat.format("\"requestType\": \"NORMAL\",\n\"sceneCode\": \"{0}\",\n\"source\": \"ch_appcollect__chsub_my-myFavorite\",\n\"version\": \"20260211.01\"", str);
    }

    public AntFishpond() {
        this.displayName = "福气鱼塘🐟";
        //this.hoursKeyEnum = CompletedKeyEnum.AntFishpond;
    }

    @Override
    protected void handle() throws JSONException {
        if(Status.hasFlagToday(CompletedKeyEnum.FinshTask.name())) {
            return;
        }
        // 提前检查钓鱼限制，避免不必要的任务提交
        final boolean canFish = !Status.hasFlagToday("antFishpondAngle::FishingLimit");

        GlobalThreadPools.INSTANCE.submit(() -> {
            try {
                TimeUtil.sleep(RandomUtil.nextInt(5000, 6000));
                fishpondExchangeReward();
                TimeUtil.sleep(RandomUtil.nextInt(5000, 6000));
                for (int i = 0; i < 2; i++) {
                    TimeUtil.sleep(RandomUtil.nextInt(5000, 6000));
                    listTask();
                    TimeUtil.sleep(RandomUtil.nextInt(5000, 6000));
                }
                TimeUtil.sleep(RandomUtil.nextInt(5000, 6000));
                triggerSubplotsActivity();
                TimeUtil.sleep(RandomUtil.nextInt(5000, 6000));
                // 使用提前检查的结果
                if (canFish) {
                    fishpondAngle();
                }
            } catch (Exception e) {
                Log.printStackTrace(TAG, "handle 执行异常", e);
            }
        });
    }

    private void listTask() {
        try {
            // 每天清理一次失败缓存
            if (!Status.hasFlagToday("antFishpond_clearFailedCache")) {
                failedTaskCache.clear();
                Status.setFlagToday("antFishpond_clearFailedCache");
            }
            
            JSONObject requestString = requestString("com.alipay.antfishpond.listTask", getData());
            refinedOperation();
            if (requestString == null) {
                return;
            }
            if (!Status.hasFlagToday(CompletedKeyEnum.AntFishpondSign.name())) {
                sign(JsonUtil.getValueByPathObject(requestString, "signInfo.list"));
            }
            JSONArray jSONArray = requestString.getJSONArray("taskList");
            for (int i = 0; i < jSONArray.length(); i++) {
                finishTask(jSONArray.getJSONObject(i));
                TimeUtil.sleep(RandomUtil.nextInt(15000, 16000));
            }
        } catch (Throwable th) {
            Log.error(displayName+"listTask--error:"+th);
        }
    }
    private void triggerSubplotsActivity() {
        try {
            // 定义常量避免魔法字符串
            final String ACTIVITY_TYPE_TOMORROW_ROD = "TOMORROW_ROD";
            final String ACTION_RECEIVE_AWARD = "receiveAward";
            final String EXTEND_AWARD_COUNT = "awardCount";
            final String EXTEND_RECEIVED_ROD_COUNT = "receivedRodCount";

            String result = RequestManager.requestString("com.alipay.antfishpond.querySubplotsActivity",
                    "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"GameCenter\",\"source\":\"ch_appcollect__chsub_my-myFavorite\",\"version\":\"20260211.01\"}]");
            JSONObject requestString = new JSONObject(result);
            if (requestString == null) return;

            JSONArray jSONArray = requestString.getJSONArray("subplotsActivityList");
            if (jSONArray == null || jSONArray.length() == 0){
                return;
            }
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject jSONObject = jSONArray.getJSONObject(i);
                String activityType = jSONObject.getString("activityType");
                String status = jSONObject.optString("status");

                // 只处理待办状态
                if (!"TODO".equals(status) && !"TODAY_TODO".equals(status)) continue;

                // 使用 switch 简化类型判断
                switch (activityType) {
                    case ACTIVITY_TYPE_TOMORROW_ROD: {
                        Object obj = 2; // 固定类型标识
                        String set = "";

                        // 嵌套逻辑扁平化
                        if (obj != null) {
                            if (obj.equals(2)) {
                                set = EXTEND_RECEIVED_ROD_COUNT;
                                String optString = "FINISH";

                                // 核心业务逻辑
                                String reward = JsonUtil.getValueByPath(
                                        requestString("com.alipay.antfishpond.triggerSubplotsActivity",
                                                getData() + ",\"activityType\": \"" + activityType + "\",\"actionType\": \"" + optString + "\""),
                                        "triggerSubplotsActivity.extend." + set);

                                if (reward != null && !reward.isEmpty()) {
                                    Log.other(displayName + "领取奖励[" + reward + "根钓竿]");
                                }
                            }
                        }
                        break;
                    }

                    // 其他类型预留扩展
                    case "FISH_ACTIVITY":
                        break;
                    case "GIFT_BOX":
                        gifiBox();
                        break;

                    default:
                        Log.other(displayName + "未知活动类型: " + activityType);
                }
            }
        } catch (Throwable th) {
            Log.error(this.TAG+"钓鱼trigger方法出错:"+th);
        }
    }

    //领取钓鱼次数的奖励
    private void receiveTriggerSub(){
        String method = "com.alipay.antfishpond.triggerSubplotsActivity";
        String params = "[{\"actionType\":\"receiveAward\",\"activityType\":\"FISH_ACTIVITY\",\"requestType\":\"NORMAL\"," +
                "\"sceneCode\":\"GameCenter\",\"source\":\"ch_alipaysearch__chsub_normal\",\"version\":\"20240722.01\"}]";
        try{
            JSONObject rpcEntity = new JSONObject(RequestManager.requestString(method, params));
            if (rpcEntity.optBoolean("success")){
                Log.other(this.displayName + "领取鱼竿+1");
            }else {
                Log.error(this.displayName+".receiveTriggerSub 领取失败:"+rpcEntity);
            }
        } catch (JSONException e) {
            Log.error(this.displayName+".receiveTriggerSub JSON解析错误:"+e);
        }
    }

    //每日开宝箱
    private void gifiBox() {
        String method = "com.alipay.antfishpond.triggerSubplotsActivity";
        String data = "[{\"actionType\":\"receiveAward\",\"activityType\":\"GIFT_BOX\",\"requestType\":\"NORMAL\",\"sceneCode\":\"GameCenter\",\"source\":\"ch_alipaysearch__chsub_normal\",\"version\":\"20240722.01\"}]";
        String rpcEntity = RequestManager.requestString(method, data);
       if (rpcEntity != null){
           JSONObject jsonObject = null;
           try {
               jsonObject = new JSONObject(rpcEntity);
           } catch (JSONException e) {
               Log.error(this.displayName+".gifiBox JSON解析错误:"+e);
           }
           if (jsonObject.optBoolean("success")){
               Log.other(this.displayName + "完成每日开宝箱");
           }
       }
    }

    private void sign(Object obj) {
        if (obj == null) {
            return;
        }
        try {
            JSONArray jSONArray = (JSONArray) obj;
            String str = "";
            boolean z = false;
            int i = 0;
            while (true) {
                if (i >= jSONArray.length()) {
                    break;
                }
                JSONObject jSONObject = jSONArray.getJSONObject(i);
                if (jSONObject.getBoolean("today")) {
                    str = jSONObject.getString("signKey");
                    z = jSONObject.getBoolean("signed");
                    break;
                }
                i++;
            }
            if (z || str.isEmpty() || requestString("com.alipay.antfishpond.sign", getData() + ",\"signKey\": \"" + str + "\"") == null) {
                return;
            }
            Log.other(this.displayName + "签到成功");
            Status.setFlagToday(CompletedKeyEnum.AntFishpondSign.name());
        } catch (Exception e) {
            Log.printStackTrace(this.TAG, e);
        }
    }

    private void finishTask(JSONObject jSONObject) {
        try {
            String taskId = jSONObject.getString("taskId");
            String taskStatus = jSONObject.getString("taskStatus");
            String sceneCode = jSONObject.getString("sceneCode");
            int rightsTimes = jSONObject.getInt("rightsTimes");
            int rightsTimesLimit = jSONObject.getInt("rightsTimesLimit");
            String actionType = jSONObject.getString("actionType");
            int remainingTimes = rightsTimesLimit - rightsTimes;
            String taskData = getData(sceneCode) + ",\"taskType\":\"" + taskId + "\"";

            JSONObject taskDisplayConfig = jSONObject.getJSONObject("taskDisplayConfig");
            String title = taskDisplayConfig.optString("title", "未知任务");

            // 检查任务是否已经完成或领取
            if ("RECEIVED".equals(taskStatus)) {
                // 任务奖励已领取，跳过
                return;
            }

            if ("FINISHED".equals(taskStatus)) {
                // 任务已完成，可以领取奖励
                Log.other(this.displayName + "领取[" + title + "]");
                receiveTaskAward(taskData);
                return;
            }

            // 任务未完成，继续处理
            if ("GOFISH".equals(actionType)) {
                this.fishCount = Integer.valueOf(jSONObject.getInt("taskRequire") - jSONObject.getInt("taskProgress"));
                this.fishData = taskData;
                return;
            }

            // 检查任务是否在黑名单中
            if (this.notTaskIds.contains(taskId) || NC_GAME_PATTERN.matcher(taskId).matches()
                    || NC_GAME_PATTERN2.matcher(taskId).matches() || remainingTimes == 0
                    || fansirsqi.xposed.sesame.util.TaskBlacklist.isTaskInBlacklist(taskId)
                    || fansirsqi.xposed.sesame.util.TaskBlacklist.isTaskInBlacklist(title)) {
                return;
            }
            
            // 检查是否在失败缓存中
            if (failedTaskCache.contains(taskId)) {
                return;
            }

            //浏览任务
            if (taskId.equalsIgnoreCase("GYG_XLIGHT_JX_BUSINEES_3_SUPPLY") && "TODO".equals(taskStatus)) {
                String targetUrl = taskDisplayConfig.optString("targeUrl");
                // 使用正则表达式提取 pwPreBizId
                Pattern pattern = Pattern.compile("pwPreBizId=([^&]+)");
                Matcher matcher = pattern.matcher(targetUrl);
                String pwPreBizId = null;
                if (matcher.find()) {
                    pwPreBizId = matcher.group(1); // 获取匹配到的 pwPreBizId 值
                }
                viewProductTask(pwPreBizId);
                return;
            }

            if ("NORMAL_WANYOUXI".equals(taskId) && "TODO".equals(taskStatus)) {
                //敲金蛋任务
                kqGoldEgg();
                return;
            }
            if ("NORMAL_NONGCHANGFUFANG".equals(taskId) && "TODO".equals(taskStatus)) {
                //进入鱼塘任务
                if (entryFishpond()) {
                    receiveTaskAward(taskData);
                }
                return;
            }

            if ("SHARE".equals(actionType) && "TODO".equals(taskStatus)) {
                if (batchInviteP2P(remainingTimes)) {
                    receiveTaskAward(taskData);
                }
            } else if ("TODO".equals(taskStatus)) {
                JSONObject json = requestString("com.alipay.antiep.finishTask", taskData + ",\"outBizNo\":\"" + UserMap.getCurrentUid() + System.currentTimeMillis() + "\"");
                TimeUtil.sleep(500L);
                if (json != null && json.optBoolean("success")) {
                    Log.other(this.displayName + "完成[" + title + "]");
                    // 完成后立即尝试领取奖励
                    receiveTaskAward(taskData);
                } else if (json != null && !json.optBoolean("success")) {
                    String errorCode = json.optString("code", "");
                    String errorMsg = json.optString("desc", "");
                    
                    // 如果任务已完成或已领取，不记录为错误
                    if (!("400000030".equals(errorCode) || "400000005".equals(errorCode))) {
                        Log.error(this.TAG + "完成任务失败[" + title + "]:" + errorMsg);
                        
                        // 如果是不支持RPC调用的任务，加入失败缓存
                        if (errorMsg.contains("不支持rpc调用") || errorMsg.contains("不支持") || "200000006".equals(errorCode)) {
                            failedTaskCache.add(taskId);
                            fansirsqi.xposed.sesame.util.TaskBlacklist.autoAddToBlacklist(taskId, title, errorCode, errorMsg);
                            //Log.runtime(this.displayName + "任务[" + title + "]加入失败缓存，避免重复尝试");
                        }
                    }
                } else {
                    // json为null的情况，也可能是失败
                    Log.error(this.TAG + "完成任务失败[" + title + "]:响应为空");
                    failedTaskCache.add(taskId);
                    fansirsqi.xposed.sesame.util.TaskBlacklist.autoAddToBlacklist(taskId, title, "RESPONSE_NULL", "响应为空");
                    //Log.runtime(this.displayName + "任务[" + title + "]加入失败缓存，避免重复尝试");
                }
            }
        } catch (Exception e) {
            Log.error(this.TAG + "完成任务异常: " + e);
        }
    }


    /**
     * 进入鱼塘
     */
    private boolean entryFishpond() {
        String params = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"GameCenter\",\"source\":\"farmpool\",\"version\":\"20240722.01\"}]";
        try{
            JSONObject rpcEntity = new JSONObject(RequestManager.requestString(
                    "com.alipay.antfishpond.fishpondIndex", params));
            if (rpcEntity.optBoolean("success")){
                Log.other(this.displayName + "完成[从农场进入鱼塘成功]");
                return true;
            }else{
                Log.error(this.displayName + "完成[从农场进入鱼塘失败]:"+rpcEntity);
            }
        } catch (Exception e) {
            Log.printStackTrace(this.TAG, e);
        }
        return false;
    }

    /**
     * 敲金蛋
     */
    private void kqGoldEgg() {
        if (!Status.hasFlagToday("kqGoldEgg")){
            return;
        }
        // 查询农场的游戏中心
        AntOrchardRpcCall.INSTANCE.newQueryGameCenter();
        TimeUtil.sleep(RandomUtil.nextInt(500, 1000));

        String method = "com.alipay.antorchard.smashedGoldenEgg";
        String data = "[{\"batchSmashCount\":1,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"fish_task_game\",\"version\":\"20251209.01\"}]";
        String rpcEntity = RequestManager.requestString(method, data);
        try {
            JSONObject jsonObject = new JSONObject(rpcEntity);
            if (jsonObject.optBoolean("success")) {
                TimeUtil.sleep(RandomUtil.nextInt(1500, 21000));
                String awardMethod = "com.alipay.antiep.receiveTaskAward";
                String awardData = "[{\"ignoreLimit\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFISHPOND_TASK\",\"source\":\"farmpool\",\"taskType\":\"NORMAL_WANYOUXI\",\"version\":\"20240722.01\"}]";
                JSONObject result = new JSONObject(RequestManager.requestString(awardMethod, awardData));
                if (result.optBoolean("success")) {
                        Log.other(this.displayName + "领取敲金蛋任务奖励");
                } else {
                    String errorCode = result.optString("code", "");
                    String errorMsg = result.optString("desc", "");
                    String resultCode = result.optString("resultCode", "");
                    // 如果任务已领取，不记录为错误
                    if (!"400000005".equals(errorCode)) {
                        Log.error(this.displayName + "领取敲金蛋奖励失败: " + errorMsg);
                    } else if (!resultCode.isEmpty()) {
                        Log.error(this.displayName + "领取敲金蛋奖励失败: " + resultCode);
                    }else {
                        Log.error(this.displayName + "敲金蛋任务失败: " + errorMsg);
                    }
                }
            }else {
                Log.error(this.displayName + ".kqGoldEgg 失败:" + jsonObject);
            }
        }catch (Exception e){
            Log.error(this.displayName + ".kqGoldEgg 错误:" + e);
        }finally {
            Status.setFlagToday("kqGoldEgg");
        }
    }


    /**
     * 浏览商品3次
     * @param pwPreBizId
     */
    private void viewProductTask(String pwPreBizId) {
        try {
            // 动态生成 outBizNo
            String taskId = "GYG_XLIGHT_JX_BUSINEES_3_SUPPLY";
            long timestamp = System.currentTimeMillis(); // 当前时间戳
            String randomStr = randomUUID().toString().replaceAll("-", "").substring(0, 8); // 8位随机字符串

            String outBizNo = MessageFormat.format("{0}_{1}_{2}", taskId, timestamp, randomStr);

            // 构建请求参数
            String data = "[{\"finishBusinessInfo\":{\"pwPreBizId\":\""+pwPreBizId+"\"},\"outBizNo\":\"" + outBizNo + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFISHPOND_TASK\",\"source\":\"ADBASICLIB\",\"taskType\":\"GYG_XLIGHT_JX_BUSINEES_3_SUPPLY\"}]";

            String s = RequestManager.requestString("com.alipay.antiep.finishTask", data);
            if (s != null) {
                JSONObject json = new JSONObject(s);
                if (json != null && json.optBoolean("success")) {
                    Log.other(this.displayName + "[逛一逛精选商品]成功");
                    TimeUtil.sleep(RandomUtil.nextInt(15000,16000));
                    // 成功完成任务后尝试领取奖励
                    String taskData = getData("ANTFISHPOND_TASK") + ",\"taskType\":\"GYG_XLIGHT_JX_BUSINEES_3_SUPPLY\"";
                    receiveTaskAward(taskData);
                } else if (json != null && !json.optBoolean("success")) {
                    String errorCode = json.optString("code", "");
                    String errorMsg = json.optString("desc", "");
                    // 如果任务已完成或已领取，不记录为错误
                    if (!("400000030".equals(errorCode) || "400000005".equals(errorCode))) {
                        Log.error(this.TAG + "完成逛商品任务失败: " + errorMsg);
                    }
                }
            }
        } catch (Exception e) {
            Log.error(this.TAG + "任务完成出错:" + e);
        }
    }



    private void receiveTaskAward(String str) {
        try {
            JSONObject json = requestString("com.alipay.antiep.receiveTaskAward", str + ",\"ignoreLimit\":false");
            if (json != null) {
                if (json.optBoolean("success")) {
                    // 领取成功
                    return;
                } else {
                    String errorCode = json.optString("code", "");
                    String errorMsg = json.optString("desc", "");

                    // 处理已领取或已完结的情况，不记录为错误
                    if ("400000005".equals(errorCode) || "400000030".equals(errorCode)) {
                        // 任务已领取或已完结，这是正常情况，不需要特别处理
                        return;
                    } else {
                        // 其他错误情况记录日志
                        Log.error(this.TAG + "领取任务奖励出错: " + errorMsg + " (code: " + errorCode + ")");
                    }
                }
            }
        } catch (Exception e) {
            Log.error(this.TAG + "领取任务奖励出错:" + e);
        }
    }


    @SuppressWarnings("unchecked")
    private boolean batchInviteP2P(int i) {
        //邀请
        try {
            TimeUtil.sleep(RandomUtil.nextInt(1000,2200));
            Set<String> set = (Set<String>) this.mapHandler.get("antFishpondList");
            if (set != null && !set.isEmpty()) {
                String data = getData("ANTFISHPOND_SHARE_P2P");
                String str = "";
                JSONArray jSONArray = new JSONArray();
                ArrayList<String> arrayList = new ArrayList<>(set);
                for (int i2 = 0; i2 < i; i2++) {
                    TimeUtil.sleep(RandomUtil.nextInt(15000,20000));
                    if (i2 < arrayList.size()) {
                        str = arrayList.get(i2);
                    }
                    //创建JSON对象
                    JSONObject jSONObject = new JSONObject();
                    jSONObject.put("beInvitedUserId", str); //用户id
                    jSONArray.put(jSONObject);
                    //进行邀请
                    JSONObject json = requestString("com.alipay.antiep.batchInviteP2P", data + ",\"inviteP2PVOList\":" + jSONArray);
                    if (!json.optBoolean("success")) {
                        return false;
                    }
                }
            }else{
                return false;
            }
            return true;
        } catch (Exception e) {
           Log.error(this.displayName+"钓鱼邀请任务出错:"+e);
           return false;
        }
    }

    private void fishpondAngle() {
        try {
            if (Boolean.TRUE.equals(this.mapHandler.get("fishpondAngle"))) {
                String value = OtherTask.getFishpondToken().getValue();
                if (fishpondSyncIndex()) {
                    return;
                }

                int errorCount = 0;
                int fishingCount = 0;
                String baseRequestData = getData();
                //循环钓鱼
                do {
                    try {
                        TimeUtil.sleep(RandomUtil.nextInt(1000, 2000));
                        String requestData = baseRequestData + ",\"riskToken\":" +
                                (value.startsWith("\"") ? value : "\"" + value + "\"");
                        JSONObject response = requestString("com.alipay.antfishpond.fishpondAngle", requestData);
                        if (response == null) {
                            return;
                        }
                        if (Status.hasFlagToday("antFishpondAngle::FishingLimit")) {
                            return;
                        }
                        String fishpondResult = fishpondAngle(response);
                        if (fishpondResult != null && !"1".equals(fishpondResult)) {
                            JSONObject positioningResponse = requestString(
                                    "com.alipay.antfishpond.fishpondAngleRodPositioning",
                                    "\"areaType\": \"SPECIAL_BIG_ZONE\",\"bizNo\": \"" + fishpondResult + "\"," + requestData
                            );

                            if (positioningResponse == null) {
                                return;
                            }
                            fishpondResult = fishpondAngle(positioningResponse);
                        }
                        fishingCount++;

                        // 检查是否需要领取钓鱼任务奖励
                        if (this.fishCount != null && fishingCount == this.fishCount.intValue()) {
                            receiveTaskAward(this.fishData);
                        }

                        // 检查是否需要触发子活动
                        if (this.leftFishTimes != null && fishingCount == this.leftFishTimes.intValue()) {
                            triggerSubplotsActivity();
                        }
                        // 检查是否达到领取鱼竿的条件
                        updateFishpondSyncIndex();

                        if (fishpondResult == null) {
                            return;
                        }
                        TimeUtil.sleep(RandomUtil.nextInt(1000, 2000));
                    } catch (Throwable th) {
                        Log.printStackTrace(this.TAG, th);
                        errorCount++;
                    }
                } while (errorCount <= 3);
            }
        } catch (Exception e) {
            Log.error(this.TAG, "钓鱼出错 err: " + e.getMessage());
        }
    }




    private String fishpondAngle(JSONObject jSONObject) {
        try {
            int i = jSONObject.optInt("rodSumCount",0);
            if (i==0){
                Log.error(this.displayName + "钓鱼出错:"+jSONObject);
                return null;
            }
            //提取其他参数
            JSONObject jSONObject2 = jSONObject.getJSONObject("angleResultInfo");
            JSONObject optJSONObject = jSONObject2.optJSONObject("angleAdInfo");
            if (optJSONObject != null) {
                String adBizNo = optJSONObject.optString("adBizNo");
                String taskId = optJSONObject.optString("taskId");
                if (!adBizNo.isEmpty() && !taskId.isEmpty()) {
                    GlobalThreadPools.INSTANCE.execute(() -> {
                        try {
                            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
                            fishpondAdNotice(adBizNo);
                            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
                            finishExtraTask(adBizNo, taskId);
                        } catch (Exception e) {
                            Log.error(this.TAG, "Extra reward task execution failed: " + e.getMessage());
                        }
                    });
                }
            }
            String string = jSONObject2.getString("fishWeight");
            String string2 = jSONObject2.getString("fishType");
            String string3 = jSONObject2.getString("bizNo");
            if (!string.isEmpty() && "0.01".equals(string)) {
                Log.other(this.displayName + "token失效，停止自动钓鱼，手动钓一次鱼后将自动更新token");
                OtherTask.getFishpondToken().setValue("");
                OtherTask.getFishpondAngle().setValue(false);
                return null;
            }
            if ("WELFARE_FISH".equals(string2)) {
                return string3;
            }
            Log.other(this.displayName + "钓鱼获得[" + jSONObject2.getString("fishName") + "]+" + string + "剩余" + i + "次");
            if (i == 0) {
                return null;
            }
            return "1";
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
            return null;
        }
    }

    private boolean fishpondSyncIndex() {
        try {
            JSONObject requestString = requestString("com.alipay.antfishpond.fishpondSyncIndex", getData() + ",\"syncTypeList\":[\"FISH_ACTIVITY\",\"TASK_DISPLAY\"]");
            if (requestString == null) {
                return false;
            }
            int i = requestString.getInt("rodSumCount");
            String valueByPath = JsonUtil.getValueByPath(requestString, "fishActivity.leftFishTimes");
            if (!valueByPath.isEmpty()) {
                this.leftFishTimes = Integer.valueOf(Integer.parseInt(valueByPath));
            }
            Object valueByPathObject = JsonUtil.getValueByPathObject(requestString, "roundInfo.fishAssetInfo");
            if (valueByPathObject != null) {
                JSONObject jSONObject = (JSONObject) valueByPathObject;
                Log.other(this.displayName + "目标[" + jSONObject.getString("targetFishWeight") + "]当前[" + jSONObject.getString("currentFishWeight") + "]剩余[" + jSONObject.getString("diffFishWeight") + "]");
                if (Objects.equals(jSONObject.getString("targetFishWeight"), jSONObject.getString("currentFishWeight"))){
                    fishpondExchangeReward();
                }
            }
            return i == 0;
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
            return false;
        }
    }
    private void updateFishpondSyncIndex() {
        try {
            String method = "com.alipay.antfishpond.fishpondSyncIndex";
            String params = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"GameCenter\",\"source\":\"ch_alipaysearch__chsub_normal\"," +
                    "\"syncTypeList\":[\"FISH_ACTIVITY\",\"TASK_DISPLAY\",\"TOMORROW_ROD\"],\"version\":\"20240722.01\"}]";
            JSONObject json = new JSONObject(RequestManager.requestString(method, params));
            if (json.optBoolean("success")) {
                // 从响应中提取 fishActivity 对象
                JSONObject fishActivity = json.optJSONObject("fishActivity");
                if (fishActivity != null) {
                    // 检查是否存在 leftFishTimes 字段
                    if (fishActivity.has("leftFishTimes")) {
                        int leftFishTimesValue = fishActivity.optInt("leftFishTimes", -1);
                        if (leftFishTimesValue > 0) {
                            this.leftFishTimes = leftFishTimesValue;
                            //Log.runtime(displayName + "还需要钓几次得鱼竿: " + this.leftFishTimes);
                        } else if (leftFishTimesValue == 0) {
                            receiveTriggerSub();
                        } else {
                            Log.error(this.TAG, "获取 leftFishTimes 值无效: " + json);
                        }
                    } else {
                        // 如果没有 leftFishTimes 字段，检查 status 是否为 FINISHED
                        String status = fishActivity.optString("status", "");
                        if ("FINISHED".equals(status)) {
                            receiveTriggerSub();
                        }
                    }
                }
            } else {
                Log.error(this.TAG, "更新鱼塘钓鱼次数失败: " + json);
            }
        } catch (Throwable th) {
            Log.error(this.TAG, "更新鱼塘钓鱼次数出错: " + th);
        }
    }





    private void fishpondExchangeReward() {
        JSONObject requestString;
        try {
            JSONObject requestString2 = requestString("com.alipay.antfishpond.fishpondIndex", getData());
            refinedOperation();
            if (requestString2 == null || !String.valueOf(true).equals(JsonUtil.getValueByPath(requestString2, "roundInfo.canExchange")) || (requestString = requestString("com.alipay.antfishpond.fishpondExchangeReward", getData())) == null) {
                return;
            }
            JSONObject jSONObject = requestString.getJSONObject("exchangeRewardResult");
            String str = this.displayName + jSONObject.optString("title") + jSONObject.optString("targetRewardCount");
            String title = "\uD83E\uDDE7鱼塘兑换奖励：";
            Log.other(str);
            Notify.sendNewNotification(title, str);
            Log.other(displayName+"鱼塘兑换成功\uD83E\uDDE7");
        } catch (Throwable th) {
            Log.printStackTrace(this.TAG, th);
        }
    }

    private void fishpondAdNotice(String adBizNo) {
        try {
            String params = "[{\"adBizNo\":\"" + adBizNo + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"GameCenter\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"20260211.01\"}]";
            RequestManager.requestString("com.alipay.antfishpond.fishpondAdNotice", params);
        } catch (Exception e) {
            Log.error(this.TAG, "fishpondAdNotice Error: " + e.getMessage());
        }
    }

    private void finishExtraTask(String adBizNo, String taskId) {
        try {
            String outBizNo = UserMap.getCurrentUid() + System.currentTimeMillis();
            String params = "[{\"finishBusinessInfo\":{\"pwPreBizId\":\"" + adBizNo + "\"},\"outBizNo\":\"" + outBizNo + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFISHPOND_ANGLE_RESULT_AD\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskType\":\"" + taskId + "\",\"version\":\"0.2.2406061508.39\"}]";
            JSONObject response = new JSONObject(RequestManager.requestString("com.alipay.antiep.finishTask", params));
            if (response.optBoolean("success")) {
                Log.other(this.displayName + "额外奖励任务完成成功");
            } else {
                Log.error(this.displayName + "额外奖励任务完成失败: " + response.optString("desc"));
            }
        } catch (Exception e) {
            Log.error(this.TAG, "finishExtraTask Error: " + e.getMessage());
        }
    }

    private void refinedOperation() {
        try {
            String params = "[{\"actionId\":\"ENTER_FISHPOND_POP\",\"requestType\":\"NORMAL\",\"sceneCode\":\"GameCenter\",\"source\":\"ch_appcollect__chsub_my-myFavorite\",\"version\":\"20260211.01\"}]";
            RequestManager.requestString("com.alipay.antfishpond.refinedOperation", params);
        } catch (Exception e) {
            Log.error(this.TAG, "refinedOperation Error: " + e.getMessage());
        }
    }
}
