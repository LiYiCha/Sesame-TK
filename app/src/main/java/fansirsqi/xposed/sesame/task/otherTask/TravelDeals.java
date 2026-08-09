package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
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

/**
 * 出行特惠（行程能量）
 */

public class TravelDeals extends BaseCommTask {
    private String displayName = "出行特惠🚗";
    private Set<String> skippedTasks = new HashSet<>(Arrays.asList(
            "开会员享最高10G流量",
            "完成办流量卡月享150G",
            "充话费最高立减1元",
            "办流量卡月享150G",
            "浏览30秒商品橱窗",
            "领15天贷款免息福利"
    ));
    @Override
    protected void handle() throws JSONException {
        long hour = TimeUtil.getHourOfDay();
        if (hour < 7 ) {
            return;
        }
        // 初始化黑名单
        initBlackList();
        TimeUtil.sleep(RandomUtil.nextInt(1000, 3000)); // 暂停2到3秒
        if (!Status.hasTemporaryStatusValid("TravelDeals")) {
            if (!Status.hasFlagToday(CompletedKeyEnum.TravelSign.name())) {
                Sign();
            }
            if (!Status.hasFlagToday(CompletedKeyEnum.TravelTask.name())) {
                doTask();
            }
        }
    }
    private void initBlackList(){
        Set<String> blackList = DataStore.INSTANCE.get("travelDeals_blackList", Set.class);
        if (blackList != null) {
            skippedTasks.addAll(blackList);
        }
    }

    private void doTask() {
        String method = "alipay.imasp.program.programInvoke";
        String[] tabs = new String[]{"tab1", "tab2"};

        //做任务
        for (String tab : tabs) { // 按顺序处理每个 tab
            String response = queryTask(method, tab); // 查询任务列表
            if (response == null) {
                Log.other(displayName, "查询任务列表失败: 请先过滑块验证!!!5分钟后可重新执行");
                Status.setTemporaryStatusWithExpiry("travelDeals", 1000 * 60 * 10);
                Notify.sendNewNotification(displayName, "请先过滑块验证!!!10分钟后可重新执行");
                return; // 如果查询失败，跳过当前 tab
            }
            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000)); // 暂停2到3秒

            try {
                JSONObject result = new JSONObject(response);

                // 检查错误码
                int errorCode = result.optInt("error");
                if (errorCode == 1009 || errorCode == 48 || errorCode == 6004) {
                    Log.other(displayName, " 错误 error: " + result.optString("errorMessage"));
                    TimeUtil.sleep(RandomUtil.nextInt(10000, 13000));
                    Status.setTemporaryStatusWithExpiry("travelDeals", 1000 * 60 * 5);
                    return;
                }

                // 获取任务列表
                JSONArray playTaskOrderInfoList = (JSONArray) JsonUtil.getValueByPathObject(result,
                        "components.trip_benefit_mileage_task_independent_component_task_reward_query.content.playTaskOrderInfoList");

                if (playTaskOrderInfoList == null || playTaskOrderInfoList.length() == 0) {
                    continue;
                }
                int totalTasks = playTaskOrderInfoList.length();
                int completedCount = 0;
                // 遍历任务列表
                for (int i = 0; i < playTaskOrderInfoList.length(); i++) {
                    JSONObject task = playTaskOrderInfoList.getJSONObject(i);
                    String code = task.optString("code");
                    String recordNo = task.optString("recordNo");
                    String activityName = task.optJSONObject("displayInfo").optString("activityName");
                    String taskStatus = task.optString("taskStatus");
                    String advanceType = task.optString("advanceType");

                    // 跳过已完成的任务
                    if ("finish".equals(taskStatus) || skippedTasks.contains(activityName)) {
                        completedCount++;
                        continue;
                    }
                    TimeUtil.sleep(RandomUtil.nextInt(20000, 30000)); // 暂停20到30秒
                    // 处理 eventPush 类型任务
                    if ("eventPush".equals(advanceType)) {
                        // 提取 xlight 对象
                        JSONObject xlight = task.optJSONObject("xlight");
                        if (xlight == null) {
                            Log.error(displayName, "响应数据中缺少 xlight 字段"+activityName);
                            skippedTasks.add(activityName);
                            DataStore.INSTANCE.put("travelDeals_blackList", skippedTasks);
                            return;
                        }
                        String bizId = xlight.optString("bizId","");
                        if (bizId.isEmpty()) {
                            Log.error(displayName, "任务缺少 bizId: " + activityName);
                            skippedTasks.add(activityName);
                            DataStore.INSTANCE.put("travelDeals_blackList", skippedTasks);
                            continue;
                        }

                        // 调用新方法完成任务
                        boolean resultTask = completeEventPushTask(bizId, activityName);
                        if (!resultTask) {
                            skippedTasks.add(activityName);
                            DataStore.INSTANCE.put("travelDeals_blackList", skippedTasks);
                        }
                    } else {
                        long outBizNo = System.currentTimeMillis(); // 使用当前时间戳作为 outBizNo

                        try {
                            subTask(method, code, String.valueOf(outBizNo), recordNo, activityName);
                        } catch (JSONException e) {
                            Log.error(displayName,activityName+ "任务失败: " + e.getMessage());
                            return;
                        }
                    }
                }
                // 遍历结束后判断是否全部完成
                if (completedCount == totalTasks && totalTasks > 0) {
                    Log.other(displayName, "所有任务已完成");
                    Status.setFlagToday(CompletedKeyEnum.TravelTask.name());
                }
            } catch (JSONException e) {
                Log.error(displayName, "解析任务列表失败: tab=" + tab + ", 错误信息=" + e.getMessage());
                TimeUtil.sleep(RandomUtil.nextInt(10000, 11000));
            }
        }
    }

    /**
     * 完成 eventPush 类型的任务
     */
    private boolean completeEventPushTask(String bizId, String activityName) {
        // 定义方法和参数
        String method = "com.alipay.adtask.biz.mobilegw.service.task.finish";
        String params = "[{\"bizId\":\"" + bizId + "\",\"extendInfo\":{}}]";

        // 调用接口完成任务
        String response = RequestManager.requestString(method, params);
        if (response == null || response.isEmpty()) {
            Log.error(displayName, "完成 eventPush 任务失败: 请先过滑块验证");
            Status.setTemporaryStatusWithExpiry("travelDeals", 1000 * 60 * 5);
            Notify.sendNewNotification(displayName, "请先过滑块验证!!!5分钟后可重新执行");
            return  false;
        }

        try {
            // 解析 JSON 响应
            JSONObject res = new JSONObject(response);

            if (!res.optBoolean("success")){
                Log.error(displayName, "完成 eventPush 任务失败: " + res);
                return  false;
            }

            // 提取业务内容字段
            JSONObject bizContent = res.optJSONObject("bizContent");
            if (bizContent == null) {
                Log.error(displayName, "业务内容字段缺失: " + activityName);
                skippedTasks.add(activityName);
                DataStore.INSTANCE.put("travelDeals_blackList", skippedTasks);
                return false;
            }

            // 提取奖励信息
            String pointValue = bizContent.optString("point", "未知");

            // 打印日志
            Log.other(displayName, "完成[" + activityName +
                    "]奖励: " + pointValue +"\uD83D\uDE80");


        } catch (JSONException e) {
            Log.error(displayName, "解析 eventPush 任务结果失败: " + e.getMessage());
        }
        return true;
    }


    private void subTask(String method, String code, String outBizNo, String recordNo, String activityName) throws JSONException {
        String params = "[{\"channel\":\"ch_alipaysearch__chsub_normal\",\"cityCode\":\"450500\"," +
                "\"components\":{\"trip_benefit_mileage_task_independent_component_task_reward_process\":" +
                "{\"code\":\"" + code + "\",\"outBizNo\":" + outBizNo + ",\"recordNo\":\"" + recordNo + "\"}}," +
                "\"extInfo\":{},\"operationParamIdentify\":\"independent_component_program2024081501631546\"}]";

        String s = RequestManager.requestString(method, params);
        if (s == null || s.isEmpty()) {
            Log.error(displayName, "完成任务失败: 请先过滑块验证!");
            Status.setTemporaryStatusWithExpiry("traveDeals",   1000*60*5);
            Notify.sendNewNotification(displayName, "请先过滑块验证!!!5分钟后可重新执行");
            return;
        }
        try {
            JSONObject res = new JSONObject(s);
            if (res == null) {
                Log.error(displayName, "完成任务失败: 返回值无法解析为JSON");
                return;
            }
            if (1009 == res.optInt("error")) {
                Log.other(displayName+activityName + "错误 error: " + res.optString("errorMessage"));
                throw new RuntimeException(displayName+res.optString("errorMessage"));
            } else if (48 == res.optInt("error")) {
                Log.other(displayName, "错误 error: " + res.optString("errorMessage"));
                return;
            } else if (6004 == res.optInt("error")) {
                Log.other(displayName, "错误 error: " + res.optString("errorMessage"));
                return;
            }

            JSONObject bizContent = (JSONObject) JsonUtil.getValueByPathObject(res,
                    "components.trip_benefit_mileage_task_independent_component_task_reward_process.content.processedTask.detailInfo.rightSendResult.record.bizContent");

            if (bizContent == null) {
                Log.error(displayName, activityName + "业务内容字段缺失原因:" + res);
                return;
            }

            String pointValue = bizContent.getString("point");
            Log.other(displayName, "完成[" + activityName + "]+" + pointValue+"里程\uD83D\uDE80");

        } catch (JSONException e) {
            Log.error(displayName, "解析任务结果失败: " + e.getMessage());
            throw e; // 抛出异常以便上层捕获
        }
    }


    private String queryTask(String method, String tab) {
        String params = "[{\"channel\":\"ch_alipaysearch__chsub_normal\",\"cityCode\":\"450500\"," +
                "\"components\":{\"trip_benefit_mileage_task_independent_component_task_reward_query\":" +
                "{\"tab\":\"" + tab + "\"}},\"extInfo\":{\"alipayAppVersion\":\"10.7.20.8000\",\"osName\":\"Android\"}," +
                "\"operationParamIdentify\":\"independent_component_program2024081501631546\"}]";

        return RequestManager.requestString(method, params);
    }


    public void Sign() {
        String querySignMethod = "alipay.imasp.scene.contentQuery";
        String result = RequestManager.requestString(querySignMethod,
                "[{\"booth\":[\"bigTripSign\"],\"extParams\":{\"signRewardBooth\":\"bigTripSignReward\"},\"scene\":\"\",\"touchPoint\":\"bigTrip\"}]");

        if (result == null || result.isEmpty()) {
            Log.error(displayName, "查询签到信息失败,请先过滑块验证");
            Status.setTemporaryStatusWithExpiry("travelDealsSign", 1000 * 60 * 5);
            Notify.sendNewNotification(displayName, "请先过滑块验证!!!5分钟后可重新执行");
            return;
        }

        try {
            JSONObject res = new JSONObject(result);
            if (res == null) {
                Log.error(displayName, "查询签到信息失败: 返回值无法解析为JSON");
                return;
            }

            if (1009 == res.optInt("error")) {
                Log.other(displayName, "错误 error: " + res.optString("errorMessage"));
                return;
            } else if (48 == res.optInt("error")) {
                Log.other(displayName, "错误 error: " + res.optString("errorMessage"));
                return;
            }else if (6004 == res.optInt("error")) {
                Log.other(displayName, "错误 error: " + res.optString("errorMessage"));
                return;
            }

            String contentId = JsonUtil.getValueByPath(res, "contentInfos.bigTripSign.[0].contentId");
            if (contentId == null || contentId.isEmpty()) {
                Log.error(displayName, "查询签到信息失败: 缺少 'contentId' 字段");
                return;
            }

            String s = RequestManager.requestString("alipay.imasp.scene.userapply",
                    "[{\"booth\":\"bigTripSign\",\"contentId\":\"" + contentId + "\",\"extParams\":{\"signRewardBooth\":\"bigTripSignReward\"},\"scene\":\"trip\",\"touchPoint\":\"bigTrip\"}]");

            Log.other(displayName, "签到成功");
            Status.setFlagToday(CompletedKeyEnum.TravelSign.name());
        } catch (JSONException e) {
            Log.error(displayName, "Json解析错误: " + e.getMessage());
        }
    }

}
