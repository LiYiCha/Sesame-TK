package fansirsqi.xposed.sesame.task.otherTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
import fansirsqi.xposed.sesame.util.DataStore;
import fansirsqi.xposed.sesame.task.antSports.AntSportsRpcCall;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.ResChecker;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.data.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NeverLand extends BaseCommTask {
    private List<Integer> levelIds;
    private String mapId;
    private final String branchId;
    // 任务错误缓存
    private static final String TASK_ERROR_CACHE_PREFIX = "NeverLandTaskError_";
    private static final String BUBBLE_TASK_ERROR_CACHE_PREFIX = "NeverLandBubbleTaskError_";
    private static final long ERROR_CACHE_DURATION = 3 * 60 * 60 * 1000; // 3小时缓存
    private static Set< String> blackList = new HashSet<>();

    public NeverLand() {
        this.mapId = "MM17";
        this.branchId = "MASTER";
        this.levelIds = new ArrayList<>();
        this.displayName = "悦动健康岛🍰";
        //this.hoursKeyEnum = CompletedKeyEnum.Neverland;
    }

    private void mapStageReward(int i) {
        try {
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.mapStageReward", "\"branchId\": \"MASTER\",\"level\": " + i + ",\"source\": \"jkddicon\",\"mapId\": \"" + this.mapId + "\"");
            if (requestString != null) {
                Object valueByPathObject = JsonUtil.getValueByPathObject(requestString, "data.receiveResult.prizes.[0]");
                if (valueByPathObject != null) {
                    requestString = (JSONObject) valueByPathObject;
                    Log.other(this.displayName + "领取地图" + i + "阶段奖励[" + requestString.optString("modifyCount") + requestString.optString("title") + "]");
                }
            }
        } catch (Exception e) {
            Log.error(this.displayName + "领取地图" + i + "阶段奖励失败: " + e);
        }
    }

    private void offlineAward() {
        try {
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.offlineAward", "\"isAdvertisement\":false,\"source\":\"jkddicon\"");
            if (requestString != null) {
                JSONObject dataObj = requestString.optJSONObject("data");
                if (dataObj == null|| dataObj.length()==0) {
                    Log.error(TAG, this.displayName + "offlineAward 数据为空");
                    return;
                }

                JSONArray optJSONArray = dataObj.optJSONArray("userItems");
                if (optJSONArray != null) {
                    for (int i = 0; i < optJSONArray.length(); i++) {
                        JSONObject jSONObject = optJSONArray.getJSONObject(i);
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(this.displayName);
                        stringBuilder.append("领取离线奖励[");
                        stringBuilder.append(JsonUtil.getValueByPath(jSONObject, "modifyCount"));
                        stringBuilder.append("]能量");
                        Log.other(stringBuilder.toString());
                    }
                }
            }
        } catch (Exception e) {
           Log.error(this.displayName+"领取离线奖励失败: "+e);
        }
    }

    private boolean queryBaseinfo() {
        try {
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.queryBaseinfo", "\"branchId\": \"MASTER\",\"source\":\"jkddicon\"");
            if (requestString.optBoolean("success")) {
                String s = requestString.optJSONObject("data").optString("mapId");
                this.mapId = s;
                return true;
            }else{
                Log.error(this.displayName+"查询基础信息失败: "+requestString);
            }
        } catch (Exception e) {
            Log.error(this.displayName+"查询基础信息失败: "+e.getMessage());
        }
        return false;
    }

    private void queryBubbleTask() {
        try {
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.queryBubbleTask", "\"source\":\"jkddicon\"");
            if (requestString != null && requestString.optBoolean("success")) {
                // 添加空值检查
                JSONObject data = requestString.optJSONObject("data");
                if (data == null) {
                    Log.error(TAG, this.displayName + "queryBubbleTask 数据为空");
                    return;
                }

                JSONArray bubbleTaskVOS = data.optJSONArray("bubbleTaskVOS");
                if (bubbleTaskVOS == null) {
                    Log.error(TAG, this.displayName + "bubbleTaskVOS 数组为空");
                    return;
                }

                JSONArray validRecordIds = new JSONArray();
                TimeUtil.sleep(6000);
                String title = "未知任务";

                // 只收集状态为 TO_RECEIVE 的有效任务
                for (int i = 0; i < bubbleTaskVOS.length(); i++) {
                    JSONObject taskVO = bubbleTaskVOS.optJSONObject(i);
                    if (taskVO == null) continue; // 添加空值检查

                    String recordId = taskVO.optString("medEnergyBallInfoRecordId");
                    String status = taskVO.optString("bubbleTaskStatus");
                    title = taskVO.optString("title", "未知任务");

                    // 检查该recordId是否在错误缓存中
                    String bubbleTaskErrorKey = BUBBLE_TASK_ERROR_CACHE_PREFIX + recordId;
                    if (Status.hasTemporaryStatusValid(bubbleTaskErrorKey)) {
                        Log.runtime(this.displayName + "能量球[" + title + "]已在错误缓存中，跳过处理");
                        continue;
                    }

                    // 只处理状态为 TO_RECEIVE 的任务
                    if ("TO_RECEIVE".equals(status) && !recordId.isEmpty()) {
                        validRecordIds.put(recordId);
                    }
                }

                if (validRecordIds.length() != 0) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("\"source\": \"jkddicon\",\"medEnergyBallInfoRecordIds\": ");
                    stringBuilder.append(validRecordIds);
                    TimeUtil.sleep(3000L);
                    requestString = requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", stringBuilder.toString());

                    if (requestString != null && requestString.optBoolean("success")) {
                        JSONObject dataObj = requestString.optJSONObject("data");
                        if (dataObj != null) {
                            int changeAmount = dataObj.optInt("changeAmount", 0);
                            Log.other(this.displayName + "领取[" + title + "]奖励[" + changeAmount + "]能量");
                        } else {
                            Log.error(TAG, this.displayName + "领取能量错误:" + requestString);
                        }
                    } else {
                        // 处理具体的错误情况
                        if (requestString != null) {
                            String errorCode = requestString.optString("errorCode", "");
                            String errorMsg = requestString.optString("errorMsg", "");
                            Log.error(this.TAG, "领取能量球失败：" + errorMsg + " (" + errorCode + ")");

                            // 如果是状态校验错误，设置临时状态避免重复执行
                            if ("ENERGY_BALL_STATUS_IS_INVALID_ERROR".equals(errorCode)) {
                                Log.runtime(this.displayName + "能量球状态校验错误，暂停30分钟");
                                Status.setTemporaryStatusWithExpiry("NeverLandPickTemp30", 30 * 60 * 1000);

                                // 将这些recordId加入错误缓存
                                for (int i = 0; i < validRecordIds.length(); i++) {
                                    try {
                                        String recordId = validRecordIds.getString(i);
                                        String bubbleTaskErrorKey = BUBBLE_TASK_ERROR_CACHE_PREFIX + recordId;
                                        Status.setTemporaryStatusWithExpiry(bubbleTaskErrorKey, 30 * 60 * 1000);
                                    } catch (JSONException e) {
                                        // 忽略异常
                                    }
                                }
                                return;
                            }
                        } else {
                            Log.error(this.TAG, "领取能量球失败：空响应"+requestString);
                        }
                        return;
                    }

                } else {
                    //Log.other(this.displayName + "无可领取的能量球");
                    Status.setTemporaryStatusWithExpiry("NeverLandPickTemp30", 30 * 60 * 1000);
                    return;
                }
                TimeUtil.sleep(RandomUtil.nextInt(15000, 20000));
            }
        } catch (Exception e) {
            Log.error(this.displayName+"查询能量球任务失败: "+e);
        }
    }




    private boolean queryMapStageRewardInfo() {
        try {
            String params = String.format("\"branchId\": \"%s\",\"source\": \"jkddicon\",\"mapId\": \"%s\"",
                    this.branchId, this.mapId);

            JSONObject response = requestString("com.alipay.neverland.biz.rpc.queryMapStageRewardInfo", params);
            if (response == null || !response.optBoolean("success")) {
                return false;
            }

            JSONObject data = response.optJSONObject("data");
            if (data == null) {
                Log.error(TAG, this.displayName + "queryMapStageRewardInfo 数据为空");
                return false;
            }

            JSONArray results = data.optJSONArray("specialActivityQueryResults");
            if (results == null || results.length() == 0) {
                Log.error(TAG, this.displayName + "specialActivityQueryResults 数组为空或无数据");
                return false;
            }

            boolean hasUnclaimed = false;
            // 遍历所有阶段奖励，检查是否有可领取的
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) continue;

                String status = JsonUtil.getValueByPath(item, "functionVO.code");
                if ("TO_RECEIVE".equals(status)) {
                    // 发现可领取奖励
                    int levelId = i + 1;
                    this.levelIds.add(levelId);
                    mapStageReward(levelId);
                    hasUnclaimed = true;
                }
            }

            // 只有当没有未领取奖励时，才返回false
            return hasUnclaimed;

        } catch (Exception e) {
            Log.error(this.displayName + "查询地图任务失败: " + e.getMessage());
            return false;
        }
    }



    private void queryMaps() {
        try {
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.queryMaps", "\"branchId\": \"MASTER\",\"source\":\"jkddicon\"");
            if (requestString.optBoolean("success")) {
                JSONArray jSONArray = requestString.getJSONObject("data").getJSONArray("commonMap");
                for (int i = 0; i < jSONArray.length(); i++) {
                    JSONObject jSONObject = jSONArray.getJSONObject(i);
                    if ("DOING".equals(jSONObject.optString("status"))) {
                        this.mapId = jSONObject.optString("mapId");
                        jSONObject.getJSONArray("reward");
                        break;
                    }
                }
            }else {
                Log.error(this.displayName+"queryMaps查询地图任务失败: "+requestString);
            }
        } catch (Exception e) {
           Log.error(this.displayName+"queryMaps查询地图任务失败: "+e);
        }
    }

    private void queryTaskCenter() {
        try {
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.queryTaskCenter", "\"apDid\": \"N/JRfNgEj3Ry133cmrJZBVMIAFW92dVqjEyrrpB7ims=\",\"cityCode\": \"110100\",\"deviceLevel\": \"high\",\"source\": \"jkddicon\"");
            if (requestString != null && requestString.optBoolean("success")) {
                Object valueByPathObject = JsonUtil.getValueByPathObject(requestString, "data.taskCenterTaskVOS");
                if (valueByPathObject != null) {
                    JSONArray jSONArray = (JSONArray) valueByPathObject;
                    for (int i = 0; i < jSONArray.length(); i++) {
                        JSONObject jSONObject = jSONArray.getJSONObject(i);
                        String string = jSONObject.getString("taskStatus");
                        String string2 = jSONObject.getString("taskType");
                        if (!"RECEIVE_SUCCESS".equals(string)&&!"TO_RECEIVE".equals(string)) {
                            if (!"GAME_TASK".equals(string2)) {
                                boolean bresult = taskSend(jSONObject);
                                if (!bresult){
                                    break;
                                }
                                TimeUtil.sleep((long) this.executeIntervalInt);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.error(this.displayName+"queryTaskCenter查询任务失败: "+e);
        }
    }

    private void queryTaskInfo() {
        while (true) {
            try {
                JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.queryTaskInfo", "\"source\":\"health-island\",\"type\":\"LIGHT_FEEDS_TASK\"");
                if (requestString != null &&  requestString.optBoolean("success")) {
                    Object valueByPathObject = JsonUtil.getValueByPathObject(requestString, "data.taskInfos.[0]");
                    if (valueByPathObject != null) {
                        requestString = (JSONObject) valueByPathObject;
                        String string = requestString.getString("encryptValue");
                        int i = requestString.getInt("energyNum");
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("\"encryptValue\": \"");
                        stringBuilder.append(string);
                        stringBuilder.append("\",\"energyNum\":\"");
                        stringBuilder.append(i);
                        stringBuilder.append("\",\"source\":\"jkdwodesign\",\"type\":\"LIGHT_FEEDS_TASK\"");
                        if (requestString("com.alipay.neverland.biz.rpc.energyReceive", stringBuilder.toString()) != null) {
                            StringBuilder stringBuilder2 = new StringBuilder();
                            stringBuilder2.append(this.displayName);
                            stringBuilder2.append("任务获得[");
                            stringBuilder2.append(i);
                            stringBuilder2.append("]能量");
                            Log.other(stringBuilder2.toString());
                        } else {
                            return;
                        }
                    }
                    return;
                }
                return;
            } catch (Exception e) {
                Log.error(this.displayName+"queryTaskInfo查询任务失败: "+e);
                return;
            }
        }
    }

    private void sign() {
        try {
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.querySign", "\"source\": \"jkdwodesign\"");
            if (requestString != null && requestString.optBoolean("success")) {
                Object valueByPathObject = JsonUtil.getValueByPathObject(requestString, "data.days");
                if (valueByPathObject != null) {
                    JSONArray jSONArray = (JSONArray) valueByPathObject;
                    for (int i = 0; i < jSONArray.length(); i++) {
                        JSONObject jSONObject = jSONArray.getJSONObject(i);
                        if (jSONObject.getBoolean("current")) {
                            if (!jSONObject.getBoolean("signIn")) {
                                requestString = requestString("com.alipay.neverland.biz.rpc.takeSign", "\"source\":\"jkddicon\"");
                                if (requestString != null) {
                                    StringBuilder stringBuilder = new StringBuilder();
                                    stringBuilder.append(this.displayName);
                                    stringBuilder.append("签到成功，获得[");
                                    stringBuilder.append(JsonUtil.getValueByPath(requestString, "data.userItems.[0].modifyCount"));
                                    stringBuilder.append("]");
                                    Log.other(stringBuilder.toString());
                                } else {
                                    return;
                                }
                            }
                            Status.setFlagToday(CompletedKeyEnum.NeverlandSign.name());
                        }
                    }
                }
            }
        } catch (Exception e) {
           Log.error(this.displayName+"sign签到失败: "+e);
        }
    }


    private boolean taskSend(JSONObject jSONObject) {
        try {
            String taskId = jSONObject.optString("taskId", "");
            String taskTitle = jSONObject.optString("title", "未知任务");

            // 检查任务是否已在错误缓存中
            String taskErrorKey = TASK_ERROR_CACHE_PREFIX + taskId;
            if (Status.hasTemporaryStatusValid(taskErrorKey)||blackList.contains(taskId)) {
                //Log.other(this.displayName + "任务[" + taskTitle + "]已在错误缓存中，跳过执行");
                return true; // 返回true继续执行其他任务
            }

            if ("LIGHT_TASK".equals(jSONObject.getString("taskType")) && jSONObject.has("logExtMap")) {
                    xlightPlugin(jSONObject);
                    return true;
            }
            TimeUtil.sleep(3000);
            jSONObject.put("scene", "MED_TASK_HALL");
            String jSONObject2 = jSONObject.toString();
            JSONObject jsonObject = requestStringFalse("com.alipay.neverland.biz.rpc.taskSend", jSONObject2.substring(1, jSONObject2.length() - 1));
            if (jsonObject == null || !jsonObject.optBoolean("success")){
                if(jsonObject==null){
                    return false;
                }
                String errorCode = jsonObject.optString("errorCode", "");
                String errorMsg = jsonObject.optString("errorMsg", "");

                // 如果是任务推进失败，添加到错误缓存
                if ("TASK_TRIGGER_ERROR".equals(errorCode) || errorMsg.contains("任务推进失败")) {
                    Log.error(TAG, ".taskSend失败:[" + taskTitle + "]" + errorMsg);
                    blackList.add(taskId);
                    DataStore.INSTANCE.put("NeverLandBlackList", blackList);
                    return false;
                } else {
                    Log.error(TAG, ".taskSend错误:[" + taskTitle + "]" + jsonObject);
                    // 对于其他错误，也缓存一段时间
                    Status.setTemporaryStatusWithExpiry(taskErrorKey, ERROR_CACHE_DURATION);
                    return false;
                }
            }
            jSONObject.put("progress", 1);
            jSONObject.put("source", "jkdwodesign");
            jSONObject2 = jSONObject.toString();
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.taskReceive", jSONObject2.substring(1, jSONObject2.length() - 1));
            TimeUtil.sleep(3000);
            if (requestString != null && requestString.optBoolean("success")) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(this.displayName);
                stringBuilder.append("完成[");
                stringBuilder.append(jSONObject.getString("title"));
                stringBuilder.append("]+");
                stringBuilder.append(JsonUtil.getValueByPath(requestString, "data.userItems.[0].modifyCount"));
                Log.other(stringBuilder.toString());
            }
        } catch (Exception e) {
            Log.error(TAG,".taskSend错误:"+e);
            // 发生异常时也缓存任务，避免连续失败
            try {
                String taskId = jSONObject.optString("taskId", "");
                blackList.add(taskId);
                DataStore.INSTANCE.put("blacklistedTasks", blackList);
            } catch (Exception ex) {
                // 忽略缓存异常
            }
        }
        return true;
    }

    private boolean walkGrid() {
        try {
            if (!this.mapId.isEmpty()) {
                JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.walkGrid",
                        "\"branchId\": \"MASTER\",\"drilling\": false,\"mapId\": \"" + this.mapId + "\",\"source\":\"jkddicon\"");

                if (requestString != null && requestString.getBoolean("success")) {
                    JSONObject data = requestString.getJSONObject("data");
                    if (data == null) {
                        Log.error(this.displayName + "walkGrid错误: 无效的data");
                        return false;
                    }

                    int leftCount = data.optInt("leftCount", 0);

                    // 安全获取userItems[0]
                    JSONArray userItems = data.optJSONArray("userItems");
                    JSONObject userItem = (userItems != null && userItems.length() > 0)
                            ? userItems.optJSONObject(0) : null;

                    // 安全获取starData
                    JSONObject starData = data.optJSONObject("starData");

                    // 构建日志信息
                    String step = JsonUtil.getValueByPath(data, "mapAwards.[0].step");
                    String logMessage = this.displayName + "跳跳跳，前进[" + step + "]步]剩余能量：" + leftCount;

                    if (userItem != null) {
                        String modifyCount = userItem.optString("modifyCount", "0");
                        String name = userItem.optString("name", "");
                        logMessage += "，获得[" + modifyCount + name + "]";
                    }

                    Log.other(logMessage);

                    // 安全获取starData中的值
                    int curr = starData != null ? starData.optInt("curr", 0) : 0;
                    int count = starData != null ? starData.optInt("count", 0) : 0;
                    int rewardLevel = starData != null ? starData.optInt("rewardLevel", 0) : 0;

                    // 安全获取count值
                    int itemCount = 0;
                    if (userItem != null) {
                        itemCount = userItem.optInt("count", 0);
                    }

                    Log.other(displayName + "奖励等级[" + rewardLevel + "]-红包[" + itemCount + "]-星星[" + curr + "/" + count + "⭐]");

                    if (leftCount >= 5) {
                        TimeUtil.sleep(RandomUtil.nextInt(500, 1000));
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    String errorMsg = requestString.optString("errorMsg");
                    Log.error(TAG, ".walkGrid错误:" + (!errorMsg.isEmpty() ? errorMsg : requestString));
                    return false;
                }
            }
        }catch(Exception e){
               Log.error(TAG,".walkGrid错误:"+e);
            }
        return false;
    }


    private void xlightPlugin(JSONObject jSONObject) {
        try {
            JSONObject logExtMap = jSONObject.getJSONObject("logExtMap");
            String title = jSONObject.optString("title", "未知任务");
            String bizId = logExtMap.getString("bizId");

            StringBuilder stringBuilder = new StringBuilder("\"bizId\": \"");
            stringBuilder.append(bizId).append("\"");

            JSONObject response = requestString("com.alipay.adtask.biz.mobilegw.service.task.finish", stringBuilder.toString());
            TimeUtil.sleep(3000);
            if (response != null && response.optBoolean("success")) {
                String prizeCount = JsonUtil.getValueByPath(jSONObject, "prizes.[0].prizeCount");
                queryBubbleTask();
                Log.other(this.displayName + "完成[" + title + "]+[" + prizeCount + "]");
            } else {
               Log.other(this.displayName+"失败["+title+"]");
            }
        } catch (Exception e) {
            Log.error(TAG, ".xlightPlugin错误:" + e);
        }
    }

    private void exchangePrize() {
        try {
            JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.queryExchangeModule", "\"assetType\": \"RED_PACKAGE_PIECE\",\"source\": \"jkddicon\"");
            if (requestString != null && requestString.getBoolean("success")) {
                requestString = requestString.getJSONObject("data");
                int parseInt = Integer.parseInt(requestString.getJSONObject("mediumModule").optString("expiringAmount"));
                if (parseInt > 0) {
                    requestString = requestString.getJSONObject("exchangePrizeModule");
                    String string = requestString.getString("campId");
                    JSONArray jSONArray = requestString.getJSONArray("exchangePrizes");
                    for (int length = jSONArray.length() - 1; length >= 0; length--) {
                        JSONObject jSONObject = jSONArray.getJSONObject(length);
                        String string2 = jSONObject.getString("statusCode");
                        int i = jSONObject.getInt("consumeMediumAmount");
                        if (!"POINT_NOT_ENOUGH".equals(string2)) {
                            if (i - parseInt <= 500) {
                                string2 = jSONObject.getString("prizeId");
                                String string3 = jSONObject.getString("prizeName");
                                if (requestString("com.alipay.neverland.biz.rpc.doMediumExchangePrize", "\"assetType\": \"RED_PACKAGE_PIECE\",\"prizeId\": \"" + string2 + "\",\"campId\": \"" + string + "\",\"source\": \"jkddicon\"") != null) {
                                    String str = this.displayName + "快过期红包碎片兑换[" + string3 + "]，请及时使用~";
                                    Log.other(str);
                                    String title = "健康岛兑换奖励：";
                                    Notify.sendNewNotification(title, str);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.error(TAG, ".exchangePrize错误:" + e);
        }
    }
    private void receiveSpecialPrize() {
        try {
            if (!Status.hasFlagToday(CompletedKeyEnum.NeverLandSpecial.name())) {
                int i = 0;
                while (i < 2) {
                    JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.receiveSpecialPrize", "\"medPrizeIds\":[\"benefitCenterActivityPrize1\"],\"sceneType\":\"BENEFIT_CENTER\"");
                    if (requestString != null) {
                        JSONObject data = requestString.optJSONObject("data");
                        int num = 0;
                        if (data != null) {
                            num = data.optInt("modifyCount");
                        }
                        Log.other(this.displayName + "获得特别奖[" + num + "]");

                        i++;
                    }
                }
                Status.setFlagToday(CompletedKeyEnum.NeverLandSpecial.name());
                return;
            }
            Status.setFlagToday(CompletedKeyEnum.NeverLandSpecial.name());
        } catch (Exception e) {
            Log.printStackTrace(this.TAG, e);
        } catch (Throwable th) {
            Status.setFlagToday(CompletedKeyEnum.NeverLandSpecial.name());
        }
    }
    private void viewDailyAds() {
        try {
            if (!Status.hasFlagToday(CompletedKeyEnum.NeverLandDailyAds.name())) {
                int i = 0;
                while (i < 7) {
                    JSONObject requestString = requestString("com.alipay.neverland.biz.rpc.viewDailyAds", "\"source\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\"");
                    if (requestString != null &&  requestString.getBoolean("success")) {
                        Object valueByPathObject = JsonUtil.getValueByPathObject(requestString, "data.userItems");
                        if (valueByPathObject != null) {
                            JSONArray jSONArray = (JSONArray) valueByPathObject;
                            for (int i2 = 0; i2 < jSONArray.length(); i2++) {
                                JSONObject jSONObject = jSONArray.getJSONObject(i2);
                                Log.other(this.displayName + "每日广告[" + jSONObject.optInt("modifyCount") + "]" + jSONObject.optString("name"));
                            }
                        }
                        i++;
                    }
                }
                Status.setFlagToday(CompletedKeyEnum.NeverLandDailyAds.name());
                return;
            }
            Status.setFlagToday(CompletedKeyEnum.NeverLandDailyAds.name());
        } catch (Exception e) {
            Log.printStackTrace(this.TAG, e);
        } catch (Throwable th) {
            Status.setFlagToday(CompletedKeyEnum.NeverLandDailyAds.name());
        }
    }

    // 假设你有获取时间的方法，这里假设你获取到的时间是 9:30
    private static final String FLAG_MORNING = "neverLandOfflineAward_morning";
    private static final String FLAG_AFTERNOON = "neverLandOfflineAward_afternoon";
    private static final String FLAG_NIGHT = "neverLandOfflineAward_night";

    private String getCurrentTimePeriod() {
        int hour = TimeUtil.getHourOfDay();
        if (hour >= 6 && hour < 12) {
            return FLAG_MORNING;
        } else if (hour >= 12 && hour < 18) {
            return FLAG_AFTERNOON;
        } else {
            return FLAG_NIGHT;
        }
    }


    /**
     * 每日答题
     * @return
     */
    private String DayQuiz() {
        String method = "alipay.iblib.channel.data";

        // 使用当前时间戳
        long currentTimestamp = System.currentTimeMillis();

        String s = RequestManager.requestString(method,
                "[{\"activityCode\":\"query_quiz_block_detail\",\"activityId\":\"2023041700010001\",\"body\":{\"bizType\":\"DAILY_QUIZ\",\"cityCode\":\"450500\",\"queryDate\":" + currentTimestamp + ",\"scene\":\"single_day\",\"schemeParams\":{\"queryPrizeParams\":true}},\"version\":\"2.0\"}]");

        try {
            JSONObject json = new JSONObject(s);
            if (json.optBoolean("success")) {
                // 提取 awardTaskIdList
                JSONArray awardTaskIdArray = Objects.requireNonNull(Objects.requireNonNull(json.getJSONObject("data")
                                        .optJSONObject("response"))
                                .optJSONObject("prizeParams"))
                        .optJSONArray("awardTaskIdList");

                List<String> awardTaskIdList = new ArrayList<>();
                if (awardTaskIdArray != null) {
                    for (int i = 0; i < awardTaskIdArray.length(); i++) {
                        awardTaskIdList.add(awardTaskIdArray.getString(i));
                    }
                }

                // 提取 answerResult 和 contentTitle
                JSONArray quizActivityList = json.getJSONObject("data")
                        .getJSONObject("response")
                        .getJSONArray("quizActivityList");

                if (quizActivityList.length() > 0) {
                    JSONObject firstQuiz = quizActivityList.getJSONObject(0);
                    String answerResult = firstQuiz.getString("answerResult");
                    String contentTitle = firstQuiz.getString("contentTitle");

                    // 使用当前时间戳
                    long gmtAnswer = System.currentTimeMillis();
                    long gmtStartAnswer = gmtAnswer - RandomUtil.nextInt(2000, 6000);

                    String s2 = RequestManager.requestString(method,
                            "[{\"activityCode\":\"answer_quiz\",\"activityId\":\"2023041700030001\",\"body\":{\"answer\":\""+answerResult+"\",\"bizType\":\"DAILY_QUIZ\",\"gmtAnswer\":" + gmtAnswer + ",\"gmtStartAnswer\":" + gmtStartAnswer + ",\"quizId\":\"2025010200452894\"},\"version\":\"2.0\"}]");

                    JSONObject json2 = new JSONObject(s2);
                    if (json2.optBoolean("success")) {
                        Log.other(displayName + "题目[" + contentTitle+"]答案:"+answerResult);
                        String method2 = "com.alipay.medpromo.biz.rpc.trigger";

                        // 奖励列表
                        JSONArray awardTaskIdJsonArray = new JSONArray(awardTaskIdList);

                        // 领取奖励
                        if (!awardTaskIdList.isEmpty()) {
                            String s3 = RequestManager.requestString(method2,
                                    "[{" +
                                            "\"medPromTaskIds\":" + awardTaskIdJsonArray.toString() + "," +
                                            "\"stageCode\":\"send\"," +
                                            "\"version\":\"2.0\"" +
                                            "}]");

                            // 可选：处理响应结果
                            JSONObject json3 = new JSONObject(s3);
                            if (json3.optBoolean("success")) {
                                Log.other(displayName + "答题奖励领取成功");
                            } else {
                                Log.error(TAG, displayName + "答题奖励领取失败: " + json3.optString("errorMsg", "未知错误"));
                            }
                        } else {
                            Log.other(displayName + "没有可领取的答题奖励");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.error(displayName + "每日答题出错:"+e);
        }finally {
            Status.setFlagToday("DayQuiz");
        }
        return s;
    }
    /**
     * 处理初始化状态(INIT)的能量球任务
     */
    private void handleInitBubbleTasks() {
        try {
            if(Status.hasFlagToday("energyReceive")){
                return;
            }           JSONObject json = new JSONObject(RequestManager.requestString("com.alipay.neverland.biz.rpc.queryBubbleTask",
                   "[{\"source\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\",\"sportsAuthed\":true}]"));
           if (json.optBoolean("success")) {
               JSONObject data = json.getJSONObject("data");
               JSONArray bubbleTaskVOS = data.getJSONArray("bubbleTaskVOS");
               for (int i = 0; i < bubbleTaskVOS.length(); i++) {
                   JSONObject task = bubbleTaskVOS.getJSONObject(i);
                   String bubbleTaskStatus = task.optString("bubbleTaskStatus");
                   boolean bubbleTopEnable = task.optBoolean("bubbleTopEnable");
                   String taskId = task.optString("taskId","");
                   if ("INIT".equals(bubbleTaskStatus) && !bubbleTopEnable &&taskId.equals("AD_BALL")) {
                       String encryptValue = task.optString("encryptValue","");
                       String energyNum = task.optString("energyNum","");
                       String title = task.optString("title","");
                       if (!encryptValue.isEmpty()){
                           boolean result = energyReceive(encryptValue,energyNum);
                           if (result){
                               Log.other(displayName + "完成["+title+"]能量球任务");
                           }else {
                               //退出循环
                               return;
                           }
                       }
                   }
               }
           }
        } catch (Exception e) {
            Log.error(TAG, ".handleInitBubbleTasks错误:"+e);
        }
    }
    //新方法完成和领取广告任务奖励？
    private boolean energyReceive(String encryptValue,String energyNum){
        try {
            JSONObject json = new JSONObject(RequestManager.requestString("com.alipay.neverland.biz.rpc.energyReceive",
                    "[{\"encryptValue\":\""+encryptValue+"\",\"energyNum\":\""+energyNum+"\",\"lightTaskId\":\"adBubble\",\"source\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\",\"type\":\"LIGHT_FEEDS_TASK\"}]"));
            if (json.optBoolean("success")){
                return true;
            }else {
                String errorCode = json.optString("errorCode", "");
                String errorMsg = json.optString("errorMsg", "未知错误");
                Log.error(TAG, "领取能量失败: " + errorMsg + " (" + errorCode + ")");

                // 任务已达上限错误
                if ("ACTIVITY_IS_LIMITED_ERROR".equals(errorCode)) {
                    Log.other(displayName + "任务已达上限");
                    Status.setFlagToday("energyReceive");
                }
                return false;
            }
        } catch (Exception e) {
            Log.error(TAG, "handleInitBubbleTasks错误:"+e);
        }
        return false;
    }

    //新的地图查询和建筑
    private boolean queryMapsInfoNew(boolean isJump) {
        try {
            JSONObject json = new JSONObject(RequestManager.requestString("com.alipay.neverland.biz.rpc.queryMapInfoNew",
                    "[{\"branchId\":\""+branchId+"\",\"mapId\":\""+mapId+"\",\"source\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\"}]"));
            if (json.optBoolean("success")){
                JSONObject data = json.getJSONObject("data");
                mapId = data.optString("mapId");
                String mapName = data.optString("mapName");
                String mapStatus = data.optString("mapStatus");
                int mapEnergyFinal = data.optInt("mapEnergyFinal");
                int mapEnergyProcess = data.optInt("mapEnergyProcess");
                if (mapStatus.equals("DOING")){
                    JSONObject stageInfo = data.getJSONObject("stageInfo");
                    int buildingIndex = stageInfo.optInt("buildingIndex");
                    if(!isJump) {
                        Log.other(displayName + "地图[" + mapId + "]" + mapName + "正在建造中,第" + buildingIndex + "个建筑,当前进度" + mapEnergyProcess + "/" + mapEnergyFinal);
                    }
                    return true;
                }else if (mapEnergyFinal == mapEnergyProcess){
                    Log.other(displayName + "地图["+mapId+"]"+mapName+"建造完成");
                }
            }else{
                Log.error(TAG, "查询地图信息失败: " + json);
            }
        }catch (Exception e){
            Log.error(TAG, "queryMapsInfoNew错误:"+e);
        }
        return false;
    }
    private boolean buildMap(int multiNum) {
        try {
            JSONObject json = new JSONObject(RequestManager.requestString("com.alipay.neverland.biz.rpc.build",
                    "[{\"branchId\":\""+branchId+"\",\"mapId\":\""+mapId+"\",\"multiNum\":"+multiNum+",\"source\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\"}]"));
            if (json.optBoolean("success")) {
                if (handleResponse(json)) {
                    return true;
                }
            }else{
                Log.error(TAG, "建造地图失败: " + json);
            }
        } catch (Exception e) {
            Log.error(TAG, "buildMap错误:"+e);
        }
        return false;
    }


    private boolean handleResponse(JSONObject response) {
        try {
            JSONObject data = response.getJSONObject("data");

            // 解析建造前后的能量信息
//            JSONObject beforeStageInfo = data.getJSONObject("beforeStageInfo");
//            int beforeEnergy = beforeStageInfo.getInt("buildingEnergyProcess"); //进度前
            JSONObject endStageInfo = data.getJSONObject("endStageInfo");
            int Process = endStageInfo.getInt("buildingEnergyProcess"); //进度后
            int buildingEnergyFinal = endStageInfo.getInt("buildingEnergyFinal"); //满
            int buildingIndex = endStageInfo.getInt("buildingIndex"); //第几个建筑
            Log.other(displayName + "建筑"+buildingIndex+"建造完成["+Process+"/"+buildingEnergyFinal+"]");
            // 解析奖励信息
            JSONArray rewards = data.getJSONArray("rewards");
            if (rewards.length() > 0) {
                for (int i = 0; i < rewards.length(); i++) {
                    JSONObject reward = rewards.getJSONObject(i);
                    String title = reward.getString("title"); // "g健康能量"
                    Log.other(displayName + "获得: " + title + "🎉");
                }
            }
            return true;
        } catch (Exception e) {
            Log.error(TAG, "解析响应失败: " + e);
        }
        return false;
    }
    /**
     * 新版跳一跳功能
     * 支持无限跳跃模式和指定次数跳跃模式
     * 优化了能量检查和奖励领取逻辑
     */
    /**
     * 新版跳一跳功能
     * 支持无限跳跃模式和指定次数跳跃模式
     * 优化了能量检查和奖励领取逻辑
     */
    private void handleJumpNew() {
        if (!Status.hasFlagToday(CompletedKeyEnum.NeverlandJump.name())) {
            // 获取配置项
            IntegerModelField jumpTimes = OtherTask.getNeverLandJumpTIme(); // 每次跳跃次数
            BooleanModelField less = OtherTask.getNeverLandJumpLess();  // 跳一跳不设置今日状态

            int jumpCount = jumpTimes.getValue(); // 获取跳跃次数
            boolean isLess = less.getValue();

            if (jumpCount > 0) {
                // 用户能量余额
                String amount = queryAmount();
                if (amount != null && !amount.isEmpty()) {
                    int currentAmount = Integer.parseInt(amount);
                    if (currentAmount < 5) {
                        Log.other(displayName + "当前能量不足5，无法跳跃");
                        return;
                    }

                    // 根据能量值确定multiNum
                    int multiNum = calculateMultiNum(currentAmount);
                    if (multiNum <= 0) {
                        Log.other(displayName + "能量不足以支持任何倍数(5/10/50)跳跃");
                        return;
                    }

                    // 优先执行指定次数跳跃
                    for (int i = 0; i < jumpCount; i++) {
                        try {
                            // 执行跳跃
                            if (!buildMap(multiNum)) {
                                Log.runtime(TAG, "建造失败，停止跳跃");
                                break;
                            }
                            TimeUtil.sleep(2000); // 每次跳跃间隔2秒

                            // 每10次跳跃检查一次建造状态
                            if ((i + 1) % 10 == 0) {
                                queryMapsInfoNew(true); // 检查建造状态
                            }
                        } catch (Exception e) {
                            Log.error(TAG, "跳一跳执行异常: " + e);
                            break;
                        }
                    }

                } else if (amount == null) {
                    return;
                }
            }
            // 设置今日状态
            if (!isLess) {
                Log.other(displayName + "今日跳一跳任务已完成");
                Status.setFlagToday(CompletedKeyEnum.NeverlandJump.name());
            }
        }
    }

    /**
     * 根据当前能量计算合适的multiNum
     * @param currentAmount 当前能量值
     * @return 合适的multiNum值
     */
    private int calculateMultiNum(int currentAmount) {
        if (currentAmount >= 50) {
            return 5;
        } else if (currentAmount >= 10) {
            return 2;
        } else if (currentAmount >= 5) {
            return 1;
        } else {
            return 0; // 能量不足
        }
    }

    /**
     * 旧版跳一跳功能
     * 优化了能量检查和奖励领取逻辑
     */
    private void handleWalkGrid() {
        //跳一跳
        if (!Status.hasFlagToday(CompletedKeyEnum.NeverlandJump.name())) {
            // 获取配置项
            IntegerModelField jumpTimes = OtherTask.getNeverLandJumpTIme(); // 每次跳跃次数
            BooleanModelField less = OtherTask.getNeverLandJumpLess();  // 跳一跳不设置今日状态

            int jumpCount = jumpTimes.getValue(); // 获取跳跃次数
            boolean isLess = less.getValue();

            if (jumpCount > 0) {
                // 用户能量余额
                String amount = queryAmount();
                if (amount != null && !amount.isEmpty()) {
                    int currentAmount = Integer.parseInt(amount);
                    if (currentAmount < 5) {
                        Log.other(displayName + "当前能量不足5，无法跳跃");
                        return;
                    }
                }else if (amount == null) {
                    return;
                }
                // 优先执行指定次数跳跃
                for (int i = 0; i < jumpCount; i++) {
                    if (!walkGrid()) {
                        Log.runtime(TAG, "剩余能量不足，停止跳跃");
                        break;
                    }
                    // 在适当间隔检查地图奖励，而不是每次跳跃前检查
                    if (i % 10 == 0) {  // 每5次跳跃检查一次地图奖励
                        if (!queryMapStageRewardInfo()) {
                            Log.other(this.displayName + "地图阶段奖励已领完，注意手动切换地图");
                        }
                    }
                    TimeUtil.sleep(2000);
                }
            }
            if (!isLess) {
                Log.other(displayName + "今日跳一跳任务已完成");
                Status.setFlagToday(CompletedKeyEnum.NeverlandJump.name());
            }
        }
    }
    //查询能量余额
    private String queryAmount(){
        try {
            JSONObject json = new JSONObject(RequestManager.requestString("com.alipay.neverland.biz.rpc.queryUserAccount",
                    "[{\"source\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\"}]"));
            if (json.optBoolean("success")) {
                JSONObject data = json.getJSONObject("data");
                String balance = data.optString("balance");
                Log.other(displayName + "当前能量: " + balance);
                return balance;
            }else{
                Log.error(TAG, "查询能量余额失败: " + json);
                return null;
            }
        } catch (JSONException e) {
            Log.error(TAG, "查询能量余额失败: " + e);
        }
        return null;
    }

    // 检查地图情况
    private String queryBaseinfoNew() {
        try {
             //0. 先通过 queryBaseinfo 判断是否为新玩法（建造）
            JSONObject baseInfo = new JSONObject(AntSportsRpcCall.NeverlandRpcCall.INSTANCE.queryBaseinfo());
            if (!ResChecker.checkRes(TAG + "查询基础信息失败:", baseInfo)
                    || !baseInfo.optBoolean("success", false)
                    || baseInfo.optJSONObject("data") == null) {
                Log.error(TAG, "queryBaseinfo raw=" + baseInfo);
                return "fail";
            }
            JSONObject baseData = baseInfo.getJSONObject("data");
            Boolean newGame = baseData.optBoolean("newGame", false);
            if (newGame) {
                return "true";
            }else{
                return "false";
            }
        } catch (JSONException e) {
            Log.error(TAG, "queryBaseinfoNew error: " + e);
        }
        return "fail";
    }
    // 初始化黑名单
    private void initBlackList() {
        blackList = DataStore.INSTANCE.get("NeverLandBlackList",  Set.class);
    }

    @Override
    protected void handle() {
        try {
            if (!Status.hasFlagToday(CompletedKeyEnum.NeverlandSign.name())) {
                sign();
                if (!Status.hasFlagToday("DayQuiz")) {
                    DayQuiz();
                }
                exchangePrize();
            }
            // 初始化黑名单
            initBlackList();

            TimeUtil.sleep((long) this.executeIntervalInt);
            receiveSpecialPrize();
            TimeUtil.sleep((long) this.executeIntervalInt);
            viewDailyAds();
            TimeUtil.sleep((long) this.executeIntervalInt);
            boolean b = queryBaseinfo();
            if (!b) {
                return;
            }

            queryTaskCenter();
            TimeUtil.sleep((long) this.executeIntervalInt);
            // 处理任务--气泡
            handleInitBubbleTasks();

            queryTaskInfo();
            TimeUtil.sleep((long) this.executeIntervalInt);
            if (!Status.hasTemporaryStatusValid("NeverLandPickTemp30")) {
                queryBubbleTask();
            }
            TimeUtil.sleep((long) this.executeIntervalInt);

            String flagKey = getCurrentTimePeriod();

            if (!Status.hasFlagToday(flagKey)) {
                offlineAward();
                Status.setFlagToday(flagKey);
            }
            TimeUtil.sleep((long) this.executeIntervalInt);

            // 状态/是否执行跳一跳
            if (!Status.hasFlagToday(CompletedKeyEnum.NeverlandJump.name())
            && OtherTask.getNeverLandJump().getValue()) {
                // 从 mapHandler 中获取配置项
                //boolean isOldVersionEnabled = Optional.ofNullable((Boolean) mapHandler.get("neverLandJump")).orElse(false);
                //boolean isNewVersionEnabled = Optional.ofNullable((Boolean) mapHandler.get("neverLandJumpNew")).orElse(false);

                String s = queryBaseinfoNew();
                // 判断并执行对应版本的跳一跳
                if (s.equals("true")) {
                    //Log.other(displayName + "启用新版跳一跳");
                    // 新版地图查询
                    queryMapsInfoNew(false);
                    handleJumpNew(); // 执行新版跳一跳
                } else if (s.equals("false")) {
                    //Log.other(displayName + "启用旧版跳一跳");
                    // 旧版地图查询
                    queryMaps();
                    handleWalkGrid(); // 执行旧版跳一跳
                }
            }


        }catch (Exception e) {
            Log.error(TAG, displayName + "handle出错:"+e);
        }

    }
}
