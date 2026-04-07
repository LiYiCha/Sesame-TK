package fansirsqi.xposed.sesame.task.otherTask;


import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class YebSceneBffish extends BaseCommTask {

    @Override
    protected void handle() {
        if (!Status.hasFlagToday(CompletedKeyEnum.YebSceneBff.name())) {
            sign();
            incomePlusFeedTaskList();
            receiveFood();
            receiveFoodAndGold();
            index();
            queryPrizeRedemptionInfo();
        }
    }

    private void receiveFood() {
        try {
            String method = "com.alipay.yebscenebff.needle.incomePlus.index";

        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
            Log.error(this.displayName + "receiveFood error: " + th);
        }
    }

    public YebSceneBffish() {
        this.displayName = "余额宝养鱼🎁";
        this.hoursKeyEnum = CompletedKeyEnum.YebSceneBff;
    }
    private void incomePlusFeedTaskList() {
        try {
            JSONObject response = requestString("com.alipay.yebscenebff.needle.incomePlusFeedTaskList", "");
            if (response != null && response.optBoolean("success")) {
                Object uncompletedTasksObj = JsonUtil.getValueByPathObject(response, "result.taskListInfo.uncompletedList");
                if (uncompletedTasksObj != null) {
                    JSONArray uncompletedTasks = (JSONArray) uncompletedTasksObj;
                    String baseParams = "\"appName\": \"yebscenebff\",\"withJson\": false,";
                    String method = "com.alipay.yebscenebff.promosdk.index.forward";

                    for (int i = 0; i < uncompletedTasks.length(); i++) {
                        JSONObject task = uncompletedTasks.getJSONObject(i);
                        String taskId = task.getString("taskId");
                        String appletId = task.getString("appletId");
                        String taskProcessStatus = task.getString("taskProcessStatus");

                        // 构建任务参数
                        String taskParams = baseParams + "\"path\": \"task.";
                        String extParams = "\",\"taskId\": \"" + taskId + "\",\"version\": 2}";

                        // 根据任务状态执行不同操作
                        if ("NONE_SIGNUP".equals(taskProcessStatus)) {
                            // 触发任务
                            requestString(method, taskParams + "trigger\",\"extParams\": {\"appletId\": \"" + appletId + extParams);
                        } else {
                            // 完成任务
                            requestString(method, taskParams + "complete\",\"extParams\": {\"appletId\": \"" + appletId + "\",\"expectSendStatus\": \"RECOMMEND\",\"taskId\": \"" + taskId + extParams);
                        }

                        // 领取任务奖励
                        JSONObject receiveResult = requestString(method, taskParams + "receive\",\"extParams\": {\"appletId\": \"" + appletId + extParams);
                        if (receiveResult.optBoolean("success")) {
                            Object taskDetailObj = JsonUtil.getValueByPathObject(task, "taskExtProps.TASK_MORPHO_DETAIL");
                            if (taskDetailObj != null) {
                                JSONObject taskDetail = (JSONObject) taskDetailObj;
                                String title = taskDetail.optString("title");
                                String subTitle = taskDetail.optString("subTitle");
                                Log.other(this.displayName + "完成[" + title + "]");
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.error(this.displayName + "incomePlusFeedTaskList error: " + th);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }


    private void queryPrizeRedemptionInfo() {
        try {
            JSONObject response = requestStringAllNew("com.alipay.yebscenebff.needle.incomePlus.queryPrizeRedemptionInfo", "[null]");
            if (response != null && response.getBoolean("success")) {
                JSONObject result = response.getJSONObject("result");
                int currentGoldAmount = Integer.parseInt(result.getString("currentGoldAmount"));
                JSONArray prizeList = result.getJSONArray("prizeList");

                for (int i = 0; i < prizeList.length(); i++) {
                    JSONObject prize = prizeList.getJSONObject(i);
                    if ("VALID".equals(prize.getString("status"))) {
                        int prizeAmount = Integer.parseInt(prize.getString("amount"));
                        if (currentGoldAmount >= prizeAmount) {
                            String title = prize.getString("title");
                            String prizeId = prize.getString("prizeId");
                            String campId = prize.getString("campId");
                            int prizeValue = prize.getInt("prizeValue");

                            String redeemParams = "\"amount\": " + prizeAmount + ",\"campId\": \"" + campId + "\",\"prizeId\": \"" + prizeId + "\"";
                            JSONObject redeemResult = requestString("com.alipay.yebscenebff.needle.incomePlus.redeemPrize", redeemParams);
                            if (redeemResult != null) {
                                Log.other(this.displayName + "金币兑换[" + prizeValue + title + "]");
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.error(this.displayName + "queryPrizeRedemptionInfo error: " + th);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }


    private void sign() {
        try {
            String entrance = "INCOME_PLUS_SIGN_IN_AWARD";
            String signParams = "\"playActionCode\":\"SIGN_IN_CALENDAR_RECALL\",\"playEntrance\":\"" + entrance + "\"";
            JSONObject response = requestString("com.alipay.yebscenebff.needle.registration.query", signParams);

            if (response != null) {
                Object prizeDetailListObj = JsonUtil.getValueByPathObject(response, "result.prizeDetailList");
                if (prizeDetailListObj != null) {
                    JSONArray prizeDetailList = (JSONArray) prizeDetailListObj;

                    for (int i = 0; i < prizeDetailList.length(); i++) {
                        JSONObject prizeDetail = prizeDetailList.getJSONObject(i);
                        String signStatus = prizeDetail.getString("signStatus");

                        // 跳过未开始和已签到的状态
                        if ("NOT_STARTED".equals(signStatus) || "SIGNED_IN".equals(signStatus)) {
                            continue;
                        }

                        String prizeDayText = prizeDetail.getString("prizeDayText");
                        String prizeName = prizeDetail.getString("prizeName");
                        String prizeAmountText = prizeDetail.getString("prizeAmountText");
                        String prizeId = prizeDetail.getString("prizeId");

                        String triggerParams = "\"playActionCode\": \"SIGNIN_TRIGGER\",\"playEntrance\": \"" + entrance + "\",\"prizeId\": \"" + prizeId + "\"";
                        JSONObject triggerResult = requestString("com.alipay.yebscenebff.needle.registration.trigger", triggerParams);
                        if (triggerResult != null) {
                            Log.other(this.displayName + "签到成功[" + prizeDayText + "]获得[" + prizeName + prizeAmountText + "]");
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.error(this.displayName + "sign error: " + th);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }


    private void index() {
        try {
            String indexParams = "\"bizScenario\":\"YEB_HOME\",\"newScene\":\"otherTask\",\"version\":\"V1\"";
            JSONObject response = requestString("com.alipay.yebscenebff.needle.incomePlus.index", indexParams);

            if (response != null && response.optBoolean("success")) {
                JSONObject result = response.getJSONObject("result");
                String contractId = result.getString("contractId");

                // 领取金球
                JSONArray coinOrderList = result.getJSONArray("coinOrderList");
                receiveGold(coinOrderList, contractId);

                // 喂鱼
                String foodAmount = JsonUtil.getValueByPath(result, "foodAmount.amount");
                if (!foodAmount.isEmpty() && !"0".equals(foodAmount)) {
                    String feedParams = "\"amount\":\"" + foodAmount + "\",\"contractId\": \"" + contractId + "\"";
                    JSONObject feedResult = requestString("com.alipay.yebscenebff.needle.incomePlus.feedingFish", feedParams);
                    if (feedResult != null) {
                        Log.other(this.displayName + "喂鱼成功[" + foodAmount + "]");
                        Status.setFlagToday(CompletedKeyEnum.YebSceneBff.name());
                    }
                }
            }
        } catch (Throwable th) {
            Log.error(this.displayName + "index error: " + th);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }


    private void receiveFoodAndGold() {
        try {
            String data = "[{\"bizScenario\":\"YEB_HOME\",\"newScene\":\"oldGold\",\"pkgVersion\":\"0.0.2\",\"version\":\"V1\"}]";
            String method = "com.alipay.yebscenebff.needle.incomePlus.index";

            String response = RequestManager.requestString(method, data);
            if (response != null) {
                JSONObject json = new JSONObject(response);
                if (json != null && json.optBoolean("success")) {
                    JSONObject result = json.getJSONObject("result");
                    String incomeFood = result.optString("incomeFood", "");
                    String contractId = result.optString("contractId", "");

                    // 领取每日饲料
                    if (!"".equals(incomeFood)) {
                        String feedMethod = "com.alipay.yebscenebff.needle.incomePlus.receiveIncomeFood";
                        String feedData = "[{\"amount\":\"" + incomeFood + "\",\"contractId\":\"" + contractId + "\"}]";
                        String feedResponse = RequestManager.requestString(feedMethod, feedData);
                        if (feedResponse != null) {
                            Log.other(this.displayName + "每日鱼饲料领取成功[" + incomeFood + "]");
                        }
                    }

                    // 领取金球
                    JSONArray coinOrderList = result.optJSONArray("coinOrderList");
                    if (coinOrderList == null || coinOrderList.length() == 0) {
                        Log.other(displayName + "没有有效的 coinOrderList");
                        Log.error(displayName + "coinOrderList无效：" + coinOrderList);
                        return;
                    }

                    // 遍历 coinOrderList，只处理 orderStatus 为 "I" 的项
                    for (int i = 0; i < coinOrderList.length(); i++) {
                        JSONObject item = coinOrderList.getJSONObject(i);
                        String orderStatus = item.optString("orderStatus");

                        if ("I".equals(orderStatus)) {
                            String orderId = item.optString("orderId");
                            String amount = item.optString("amount");

                            if (!TextUtils.isEmpty(orderId) && !TextUtils.isEmpty(amount)) {
                                String goldMethod = "com.alipay.yebscenebff.needle.incomePlus.receiveGold";
                                String goldData = "[{\"contractId\":\"" + contractId + "\",\"orderId\":\"" + orderId + "\"}]";
                                String goldResponse = RequestManager.requestString(goldMethod, goldData);
                                if (goldResponse != null) {
                                    Log.other(this.displayName + "金球领取成功[" + amount + "]");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.error(displayName + "领取鱼饲料或者金球出错:" + e);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }


    private void receiveGold(JSONArray coinOrderList, String contractId) {
        try {
            for (int i = 0; i < coinOrderList.length(); i++) {
                JSONObject order = coinOrderList.optJSONObject(i);
                if (order == null) continue;

                String orderStatus = order.optString("orderStatus");
                String whenReceiveCoinDate = order.optString("whenReceiveCoinDate");

                if ("I".equals(orderStatus) && !whenReceiveCoinDate.isEmpty()) {
                    if (TimeUtil.isAfter(whenReceiveCoinDate)) {
                        String amount = order.getString("amount");
                        String orderId = order.getString("orderId");

                        String receiveParams = "\"contractId\": \"" + contractId + "\",\"orderId\": \"" + orderId + "\"";
                        JSONObject receiveResult = requestString("com.alipay.yebscenebff.needle.incomePlus.receiveGold", receiveParams);
                        if (receiveResult != null) {
                            Log.other(this.displayName + "领取[" + amount + "个金泡泡]");
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.error(this.displayName + "receiveGold error: " + th);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }



}