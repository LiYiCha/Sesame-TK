package fansirsqi.xposed.sesame.task.otherTask;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.DataStore;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class HuaHuaKa extends BaseCommTask {
    private String certId;
    public HuaHuaKa() {
        this.productCode = "HUA_HUA_CARD_NORMAL_23Y06";
        this.sceneCode = "HUA_HUA_CARD";
        this.displayName = "花花卡💴";
        //this.hoursKeyEnum = CompletedKeyEnum.HuaHuaKa;
    }
    private Set<String> blackList = new HashSet<>(Arrays.asList("去定制专属logo","去设计品牌logo"
    ,"去设计logo购买")); //黑名单
    // 失败计数 - 临时记录
    private Map<String, Integer> taskFailureCount = new HashMap<>();

    private final String productCode;
    private final String sceneCode;
    protected void handle() {
        int hour = TimeUtil.getHourOfDay();
        if (hour < 7 || hour >= 23) {
            return;
        }
        initBlackList(); //初始化黑名单
        indexTrigger();//签到
        queryV2();//查询并执行任务
        index();//翻牌
    }
    //初始化黑名单
    @SuppressWarnings("unchecked")
    private void initBlackList() {
        //读取黑名单
        Set<String> getblackList = DataStore.INSTANCE.get("huahuaka_blackList", Set.class);
         if (getblackList != null) {
             blackList = getblackList;
             DataStore.INSTANCE.put("huahuaka_blackList", blackList);
         }
         //读取失败计数
        Map<String, Integer> savedCount = DataStore.INSTANCE.get("huahuaka_task_failure_count", Map.class);
        if (savedCount != null) {
            taskFailureCount = savedCount;
        }
    }
    // 失败计数管理方法
    private void handleTaskFailure(String title) {
        int failCount = taskFailureCount.getOrDefault(title, 0) + 1;
        taskFailureCount.put(title, failCount);

        // 保存到持久化存储
        DataStore.INSTANCE.put("huahuaka_task_failure_count", taskFailureCount);

        // 如果失败次数达到阈值，加入黑名单
        if (failCount >= 2) {
            blackList.add(title);
            DataStore.INSTANCE.put("huahuaka_blackList", blackList);
            Log.runtime(displayName + "任务[" + title + "]失败" + failCount + "次，已加入黑名单");
        }
    }

    // 成功时清除计数
    private void handleTaskSuccess(String title) {
        if (taskFailureCount.containsKey(title)) {
            taskFailureCount.remove(title);
            DataStore.INSTANCE.put("huahuaka_task_failure_count", taskFailureCount);
        }
    }

    private void queryV2() {
        try {
            JSONObject requestString = requestString("com.alipay.pcreditbfweb.sdk.task.queryV2", "\"requestFrom\": \"pccp\",\"scene\":\"HUA_HUA_CARD\"");
            if (requestString != null && requestString.getBoolean("success")) {
                JSONArray taskList = requestString.getJSONArray("data");

                for (int i = 0; i < taskList.length(); i++) {
                    JSONObject task = taskList.getJSONObject(i);

                    // 获取任务类型，判断是否是 look 类型
                    String taskType = task.optString("taskType");
                    //跳过其他任务
                    if ("other".equals(taskType)) {
                        continue;
                    }

                    //处理look任务
                    if ("look".equals(taskType)) {
                        processTask(task, false);
                        continue;
                    }

                    //处理广告任务
                    String taskSource = task.optString("taskSource", "");
                    if (taskSource.equals("XLIGHT_TASK")) {
                        processTask(task, true);
                    }
                }
                TimeUtil.sleep((long) this.executeIntervalInt);
            }
        } catch (Throwable th) {
            Log.error(displayName + "queryV2 error: " + th);
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    /**
     * 统一处理任务逻辑
     * @param task 任务对象
     * @param isAdTask 是否为广告任务
     */
    private void processTask(JSONObject task, boolean isAdTask) {
        try {
            // 提取通用任务信息
            if (this.certId == null || this.certId.isEmpty()) {
                this.certId = JsonUtil.getValueByPath(task, "taskBaseInfo.prizeInfos.[0].extInfo.CERT_TEMPLATE_ID");
            }

            String taskId = task.getString("taskId");
            String taskCenId = task.getString("taskCenId");
            String taskStatus = task.getString("taskStatus");
            String title = JsonUtil.getValueByPath(task, "taskShowInfo.title");
            // 跳过黑名单任务
            if (blackList.contains(title)) {
                return;
            }

            // 如果未完成报名，则先报名
            if ("NONE_SIGNUP".equals(taskStatus)) {
                trigger(taskCenId, taskId, "signup");
            }

            boolean taskFinished = true;
            // 根据任务类型执行不同的完成逻辑
            if (isAdTask) {
                String bizId = JsonUtil.getValueByPath(task, "taskShowInfo.bizId");
                taskFinished = finishTask(bizId);
            } else {
                trigger(taskCenId, taskId, "send");
            }

            if (taskFinished) {
                handleTaskSuccess(title);
                Log.other(this.displayName + "完成[" + title + "]");

                // 广告任务需要特殊等待时间
                if (isAdTask) {
                    TimeUtil.sleep(RandomUtil.nextInt(15000, 17000)); // 18秒等待时间
                }

                // 完成立即领取奖励
                claimTaskReward(taskCenId, taskId);
            } else {
                // 任务失败，增加失败计数
                handleTaskFailure(title);
            }

            // 普通任务完成后暂停2秒
            if (!isAdTask) {
                TimeUtil.sleep(2000);
            } else {
                // 广告任务完成后暂停2秒
                TimeUtil.sleep(2000);
            }
        } catch (Exception e) {
            // 异常处理中也增加失败计数
            String title = JsonUtil.getValueByPath(task, "taskShowInfo.title");
            if (title != null) {
                handleTaskFailure(title);
            }
            Log.error(displayName + "处理任务失败: " + e.getMessage());
        }
    }

    /**
     * 领取单个任务奖励
     * @param taskCenId 任务中心ID
     * @param taskId 任务ID
     */
    private void claimTaskReward(String taskCenId, String taskId) {
        try {
            JSONArray taskCenIds = new JSONArray();
            JSONArray taskIds = new JSONArray();
            taskCenIds.put(taskCenId);
            taskIds.put(taskId);

            JSONObject awardResponse = requestString("com.alipay.pcreditbfweb.sdk.task.award",
                    "\"taskCenIds\":" + taskCenIds + ",\"taskIds\":" + taskIds);
            if (awardResponse != null) {
                JSONArray resultData = (JSONArray) JsonUtil.getValueByPathObject(awardResponse, "data.resultData");
                if (resultData != null) {
                    for (int i = 0; i < resultData.length(); i++) {
                        JSONObject prizeObj = resultData.getJSONObject(i);
                        String prizeName = JsonUtil.getValueByPath(prizeObj, "prizeSendOrderList.[0].prizeName");
                        Log.other(this.displayName + "获得[" + prizeName + "]");
                    }
                }
            }
        } catch (Exception e) {
            Log.error(displayName + "领取任务奖励失败: " + e.getMessage());
        }
    }


    private boolean finishTask(String bizId) {
        String method = "com.alipay.adtask.biz.mobilegw.service.task.finish";
        String params = "[{\"bizId\":\""+bizId+"\",\"extendInfo\":{}}]";
        try {
            JSONObject result = new JSONObject(RequestManager.requestString(method, params));
            if (result.optBoolean("success")) {
                return true;
            } else {
                Log.error(displayName+"完成广告任务失败: "+result);
                return false;
            }
        } catch (JSONException e) {
            Log.error(TAG+"完成广告任务失败: "+e);
            return false;
        }
    }

    private void queryApplayer(){
        String method = "com.alipay.adtask.biz.mobilegw.service.applayer.query";
        String params = "[{\"spaceCode\":\"42_2024021924200082660\"}]";
    }
    public static long convertToTimestamp(String isoTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // 设置为 UTC 时区
        try {
            Date date = sdf.parse(isoTime);
            return date.getTime(); // 得到 13 位时间戳
        } catch (ParseException e) {
            e.printStackTrace();
            return -1; // 出错返回 -1
        }
    }
    private void campConsult(String startTime,String endTime) {
        long startTimestamp = convertToTimestamp(startTime);
        long endTimestamp = convertToTimestamp(endTime);
        TimeUtil.sleep(1000);
        String str2 = "\"args\":";
        // 定义所有阶段ID
        String[] campIds = {
                "HHK_SINGLE_CAMP_1",
                "HHK_SINGLE_CAMP_2",
                "HHK_SINGLE_CAMP_3"
        };

        for (String campId : campIds) {
            try {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("endTime", endTimestamp);
                jSONObject.put("playId", campId);
                jSONObject.put("requestFrom", "pccp");
                jSONObject.put("startTime", startTimestamp);

                String requestString0 = RequestManager.requestString(
                        "com.alipay.pcreditbfweb.drpc.collect.campConsult",
                        "[{\"endTime\":" + endTimestamp
                                + ",\"playId\":\"" + campId
                                + "\",\"requestFrom\":\"pccp\""
                                + ",\"startTime\":" + startTimestamp + "}]"
                );
                JSONObject requestString = new JSONObject(requestString0);
                if (requestString != null) {
                    JSONArray jSONArray = (JSONArray) JsonUtil.getValueByPathObject(requestString, "data.result.result.nodeList");
                    if (jSONArray != null) {
                        for (int i = 0; i < jSONArray.length(); i++) {
                            JSONObject jSONObject2 = jSONArray.getJSONObject(i);
                            String title = jSONObject2.getString("title");
                            String status = jSONObject2.getString("status");
                            String nodeId = JsonUtil.getValueByPath(jSONObject2, "config.id");

                            if (!(nodeId.isEmpty() || "RECEIVE".equals(status) || "DISABLE".equals(status))) {
                                jSONObject.put("nodeId", nodeId);
                                requestString("com.alipay.pcreditbfweb.drpc.collect.campTrigger", str2 + jSONObject);
                                Log.other(this.displayName + "任务阶段奖励领取[" + title + "]");
                            }
                        }
                        Status.setFlagToday("HuaHuaKaCollect");
                    }
                }

                TimeUtil.sleep((long) this.executeIntervalInt);

            } catch (Throwable th) {
                Log.error("campConsult error: " + campId+"因为:"+ th);
                TimeUtil.sleep((long) this.executeIntervalInt);
            }
        }
    }

    private String buildIndexParams() {
        return String.format(
                "\"certId\":\"%s\",\"productCode\":\"%s\",\"productCodeFlop\":\"CARD_HUA_HUA_CARD_23Y06\",\"sceneCode\":\"%s\",\"sceneCodeFlop\":\"CARD_HUA_HUA_CARD\"",
                this.certId, this.productCode, this.sceneCode
        );
    }
    private JSONObject refreshFragments() throws JSONException {
        return requestString("com.alipay.pcreditbfweb.promo.hhk.index.refreshFragments",
                "\"productCode\":\"HUA_HUA_CARD_NORMAL_23Y06\",\"sceneCode\":\"HUA_HUA_CARD\"");
    }

    private void index() {
        try {
            String str = "CARD_HUA_HUA_CARD_23Y06";
            String str2 = "CARD_HUA_HUA_CARD";
            if (this.certId != null && !this.certId.isEmpty()) {
                int i = 3;
                do {
                    JSONObject requestString = requestString("com.alipay.pcreditbfweb.promo.hhk.index", buildIndexParams());
                    if (requestString != null) {
                        requestString = requestString.getJSONObject("data");
                        int remainingTimes = requestString.getInt("remainingTimes");

                        if (remainingTimes != 0) {
                            Log.other(this.displayName + "还可翻[" + remainingTimes + "]次");
                            JSONArray cardPrizes = requestString.getJSONArray("cardPrizes");

                            while (remainingTimes > 0) {
                                boolean cardFlipped = false;
                                for (int i3 = 0; i3 < cardPrizes.length(); i3++) {
                                    if (remainingTimes <= 0) break;

                                    JSONObject jSONObject = cardPrizes.getJSONObject(i3);
                                    if (!jSONObject.getBoolean("isOpen")) {
                                        boolean z = jSONObject.getBoolean("isNewPageBegin");
                                        int position = Integer.parseInt(JsonUtil.getValueByPath(jSONObject, "position.index"));

                                        int flopResult = flopCard(str, str2, position, z);
                                        if (flopResult == 0) { // 成功
                                            cardFlipped = true;
                                            remainingTimes--;
                                            TimeUtil.sleep(2000);
                                            break;
                                        }
                                        else if (flopResult == 2) { // 需要重试
                                            i--;
                                            TimeUtil.sleep(2000);
                                            cardFlipped = true; // 标记为已翻卡，继续循环
                                            break;
                                        }
                                        else { // 其他错误
                                            Log.other(this.displayName + "翻卡异常，继续尝试");
                                            cardFlipped = true; // 标记为已翻卡，继续循环
                                            break;
                                        }
                                    }
                                }

                                if (!cardFlipped) break;

                                // 只刷新碎片信息，不重新查询主页
                                JSONObject refreshData = refreshFragments();
                                if (refreshData != null && refreshData.optBoolean("success")) {
                                    JSONArray charNumberList = (JSONArray) JsonUtil.getValueByPathObject(
                                            refreshData, "data.charNumberList");
                                    merge(charNumberList); // 检查是否需要合卡
                                }

                                // 更新剩余次数
                                JSONObject homeRefresh = requestString("com.alipay.pcreditbfweb.promo.hhk.index", buildIndexParams());
                                if (homeRefresh != null) {
                                    requestString = homeRefresh.getJSONObject("data");
                                    remainingTimes = requestString.getInt("remainingTimes");
                                }
                            }
                        }
                    }
                    TimeUtil.sleep((long) this.executeIntervalInt);
                } while (--i > 0);
            }
        } catch (Throwable th) {
            Log.error(displayName + "index error: " + th);
        }
    }
    private boolean merge(JSONArray jSONArray) throws JSONException {
        if (jSONArray == null || jSONArray.length() == 0) {
            Log.other(this.displayName + "碎片列表为空，无法合卡");
            return false;
        }

        // 检查所有碎片是否 ≥1
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject fragment = jSONArray.getJSONObject(i);
            int number = fragment.getInt("number");
            if (number == 0) {
                return false; // 只要有一个不满足就返回false
            }
        }

        // 所有碎片都 ≥1 才执行合卡
        Log.other(this.displayName + "开始尝试合卡...");

        JSONObject mergeResponse = requestString(
                "com.alipay.pcreditbfweb.promo.hhk.index.merge",
                "\"productCode\":\"HUA_HUA_CARD_NORMAL_23Y06\",\"sceneCode\":\"HUA_HUA_CARD\""
        );

        if (mergeResponse != null && mergeResponse.has("data")) {
            JSONObject data = mergeResponse.getJSONObject("data");
            String campId = data.getString("campId");
            String bizNo = data.getString("bizNo");

            JSONObject prizeResponse = requestString(
                    "com.alipay.pcreditbfweb.drpc.pageQueryPrizeSendOrderLite",
                    "\"args\":{\"campIds\":[\"" + campId + "\"],\"outBizNo\":\"" + bizNo + "\",\"pageNum\":1,\"perPageSize\":10}"
            );

            if (prizeResponse != null) {
                String prizeName = JsonUtil.getValueByPath(prizeResponse, "data.result.resultData.dataList.[0].prizeName");
                Log.other(this.displayName + "合卡成功，获得[" + prizeName + "]");
                return true;
            }
        }

        return false;
    }


    private int flopCard(String str, String str2, int i, boolean z) {
        try {
            JSONObject requestString = requestString(
                    "com.alipay.pcreditbfweb.promo.hhk.index.flopcard",
                    "\"isNewPageBegin\":" + z +
                            ",\"lineIndex\":" + i +
                            ",\"productCode\":\"" + str +
                            "\",\"sceneCode\":\"" + str2 + "\""
            );

            if (requestString != null) {
                // 记录获得的卡片
                Log.other(this.displayName + "翻卡获得[" +
                        JsonUtil.getValueByPath(requestString, "data.playPrizeList.[0].prizeName") + "]");

                // 刷新碎片信息（保持原有方式，不使用refreshFragments方法）
                JSONObject refreshData = requestString(
                        "com.alipay.pcreditbfweb.promo.hhk.index.refreshFragments",
                        "\"productCode\":\"HUA_HUA_CARD_NORMAL_23Y06\",\"sceneCode\":\"HUA_HUA_CARD\""
                );

                if (refreshData != null && refreshData.optBoolean("success")) {
                    // 更新碎片状态
                    JSONArray charNumberList = (JSONArray) JsonUtil.getValueByPathObject(
                            refreshData, "data.charNumberList"
                    );

                    // 返回最新的合卡状态
                    return merge(charNumberList) ? 0 : 2;
                }
            }

            return 2; // 默认需要重试
        } catch (Throwable th) {
            Log.error(this.displayName + "翻卡异常: " + th.getMessage());
            return 1; // 错误状态
        }
    }


    private void indexTrigger() {
        try {
            if (!Status.hasFlagToday(CompletedKeyEnum.HuaHuaKaSign.name())) {
                JSONObject requestString = requestString("com.alipay.pcreditbfweb.promo.index.trigger", "\"campId\": \"CP14460747\"");
                if (requestString != null) {
                    Log.other(this.displayName + "签到成功[" + JsonUtil.getValueByPath(requestString, "data.prizeSendOrderList.[0].prizeName") + "]");
                    Status.setFlagToday(CompletedKeyEnum.HuaHuaKaSign.name());
                    TimeUtil.sleep((long) this.executeIntervalInt);
                    return;
                }
            }
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private boolean trigger(String str, String str2, String str3) throws JSONException {
        boolean z = false;
        try {
            if (requestString("com.alipay.pcreditbfweb.sdk.task.trigger", "\"appletId\": \"" + str2 + "\",\"outBizNo\": \"" + str2 + TimeUtil.getMinuteTimestamp() + "\",\"taskCenId\": \"" + str + "\",\"retryFlag\": true,\"stageCode\":\"" + str3 + "\"") != null) {
                z = true;
            }
            TimeUtil.sleep((long) this.executeIntervalInt);
            return z;
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
            throw th;
        }
    }




}