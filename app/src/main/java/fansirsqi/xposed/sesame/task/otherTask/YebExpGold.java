package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.TaskBlacklist;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fansirsqi.xposed.sesame.util.TimeUtil;

/**
 * 余额宝体验金任务
 *
 * 双任务源归并执行（移植自 Sesame-AG dev AntMemberYebExpGoldWorkflow）：
 * - PROMO_TASK_LIST：com.alipay.yebpromobff.promosdk2024.task.query / task.complete / task.queryTaskByTaskId
 * - MAIN_QUERY：com.alipay.yebscenebff.needle.yebExpGold.queryMain / promosdk.index.forward（固定 appletId）
 * 同一任务按标题指纹归并成组，执行动作时 PROMO 源优先；执行后按源回查任务状态确认领取。
 */
public class YebExpGold extends BaseCommTask {
    private static final String TAG = "余额宝体验金🌭";

    /** MAIN_QUERY 源任务 forward 固定 appletId 与版本 */
    private static final String MAIN_QUERY_APPLET_ID = "AP12183159";
    private static final int MAIN_QUERY_TASK_VERSION = 2;
    /** 签到 playId */
    private static final String SIGN_IN_PLAY_ID = "PLAY102253251";
    /** queryMain 渠道信息 */
    private static final String CH_INFO = "ch_url-https://render.alipay.com/p/yuyan/180020010001282160/index.html";
    /** queryMain 任务策略 */
    private static final String TASK_STRATEGY_CODE = "YEB_TRIAL_ASSET_TASK_BLOCK_REC";
    /** 兑换活动参数（对齐 Sesame-AG dev 成功兑换抓包，活动参数固定） */
    private static final String EXCHANGE_CAMP_ID = "CP152735172";
    private static final String EXCHANGE_PRIZE_ID = "PZ1144215101";

    private static final String SOURCE_PROMO = "PROMO";
    private static final String SOURCE_MAIN = "MAIN";

    private static final String FLAG_SIGN = "yebExpGold::sign";
    private static final String FLAG_EXCHANGE = "yebExpGold::exchange";
    private static final String FLAG_TASK_PREFIX = "yebExpGold::task:";

    private int executeIntervalInt;

    /** 任务条目：来源 + 原始任务 JSON */
    private static class TaskEntry {
        final String taskId;
        final String title;
        final String source;
        final JSONObject task;

        TaskEntry(String taskId, String title, String source, JSONObject task) {
            this.taskId = taskId;
            this.title = title;
            this.source = source;
            this.task = task;
        }
    }

    @Override
    protected void handle() throws JSONException {
    }

    // ==================== 入口 ====================

    public void handle(int interval) {
        this.executeIntervalInt = interval;
        if (Status.hasFlagToday(CompletedKeyEnum.YebExpGold.name())) {
            return;
        }
        try {
            boolean handled = false;

            // 1. 券凭证：查询 → 转换 → 兑换 → 激活
            handled = handleCertVoucherFlow() || handled;

            // 2. 主查询
            JSONObject mainResponse = queryMain(false, null);
            if (mainResponse == null) {
                yebTrialAsset();
                return;
            }
            JSONObject resultData = mainResponse.optJSONObject("resultData");
            if (resultData == null) {
                Log.system(TAG, "余额宝体验金任务查询失败: " + getErrorDesc(mainResponse));
                yebTrialAsset();
                return;
            }

            // 3. 签到
            handled = trySignIn(resultData) || handled;

            // 4. 双源任务归并执行
            handled = handleTasksDualSource(resultData) || handled;

            // 5. 余额兑换（固定活动参数 + subThreshold 门槛）
            handled = handleExchange(resultData) || handled;

            // 6. 激活体验金
            yebTrialAsset();

            if (handled) {
                Status.setFlagToday(CompletedKeyEnum.YebExpGold.name());
            }
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    // ==================== 签到 ====================

    private boolean trySignIn(JSONObject resultData) {
        try {
            if (Status.hasFlagToday(FLAG_SIGN)) {
                return false;
            }
            JSONObject signItem = findTodaySignItem(resultData);
            if (signItem == null) {
                return false;
            }
            JSONObject signInfo = signItem.optJSONObject("signInfo");
            String signStatus = signInfo == null ? "" : signInfo.optString("signStatus").toUpperCase();
            if (!"TO_SIGNED".equals(signStatus) && !"UNSIGNED".equals(signStatus)) {
                Status.setFlagToday(FLAG_SIGN);
                return false;
            }
            String amount = "";
            JSONObject prizeInfo = signItem.optJSONObject("prizeInfo");
            if (prizeInfo != null && prizeInfo.opt("prizeAmount") != null) {
                amount = prizeInfo.opt("prizeAmount").toString();
            }
            String title = amount.isEmpty() ? "余额宝体验金签到" : "余额宝体验金签到(" + amount + "元)";
            if (TaskBlacklist.isTaskInBlacklist(title)) {
                Log.system(TAG, "任务在自动跳过列表(黑名单)中，跳过[" + title + "]");
                Status.setFlagToday(FLAG_SIGN);
                return false;
            }
            JSONObject signResponse = requestString(
                    "com.alipay.yebscenebff.needle.yebExpGold.signIn",
                    "\"signInPlayId\":\"" + SIGN_IN_PLAY_ID + "\"");
            if (signResponse == null) {
                Log.system(TAG, "余额宝体验金签到失败，请手动完成");
                return false;
            }
            logSignInRewards(amount, signResponse);
            Status.setFlagToday(FLAG_SIGN);
            return true;
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
            return false;
        }
    }

    private static JSONObject findTodaySignItem(JSONObject resultData) {
        JSONObject signInData = resultData.optJSONObject("signInData");
        JSONArray list = signInData == null ? null : signInData.optJSONArray("list");
        if (list == null) {
            return null;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject signInfo = item.optJSONObject("signInfo");
            String signDateDesc = signInfo == null ? "" : signInfo.optString("signDateDesc");
            if ("TODAY".equals(signDateDesc) || item.optString("displayDate").contains("今天")) {
                return item;
            }
        }
        return null;
    }

    private void logSignInRewards(String fallbackAmount, JSONObject response) {
        try {
            JSONObject rd = response.optJSONObject("resultData");
            rd = rd == null ? null : rd.optJSONObject("resultData");
            JSONArray prizeOrderList = rd == null ? null : rd.optJSONArray("prizeOrderDTOList");
            if (prizeOrderList != null && prizeOrderList.length() > 0) {
                for (int i = 0; i < prizeOrderList.length(); i++) {
                    JSONObject order = prizeOrderList.optJSONObject(i);
                    if (order == null) {
                        continue;
                    }
                    JSONObject memo = order.optJSONObject("customMemo");
                    String amount = memo == null ? "" : memo.optString("PRIZE_AMOUNT");
                    String unit = memo == null ? "" : memo.optString("PRIZE_UNIT");
                    String prizeName = order.optString("prizeName");
                    String rewardText;
                    if (!amount.isEmpty()) {
                        rewardText = amount + (unit.isEmpty() ? "元" : unit);
                    } else if (!prizeName.isEmpty()) {
                        rewardText = prizeName;
                    } else if (!fallbackAmount.isEmpty()) {
                        rewardText = fallbackAmount + "元";
                    } else {
                        rewardText = "成功";
                    }
                    Log.other("余额宝体验金💰[签到成功]#" + rewardText);
                }
                return;
            }
            Log.other("余额宝体验金💰[签到成功]#" + (fallbackAmount.isEmpty() ? "成功" : fallbackAmount + "元"));
        } catch (Throwable ignored) {
        }
    }

    // ==================== 双源任务 ====================

    private boolean handleTasksDualSource(JSONObject resultData) {
        try {
            Map<String, List<TaskEntry>> groups = new LinkedHashMap<>();

            // PROMO 源：promosdk2024.task.query
            JSONObject promoResponse = requestString(
                    "com.alipay.yebpromobff.promosdk2024.task.query",
                    "\"needTriggerPrize\":false,\"playActionCode\":\"TASK_LIST_CONSULT\",\"playEntrance\":\"HYQ_TASK_LIST_ENTRANCE_2\"");
            if (promoResponse == null) {
                Log.system(TAG, "余额宝体验金任务列表查询失败");
            } else {
                JSONObject promoResult = promoResponse.optJSONObject("result");
                JSONArray taskDetailList = promoResult == null ? null : promoResult.optJSONArray("taskDetailList");
                if (taskDetailList != null) {
                    for (int i = 0; i < taskDetailList.length(); i++) {
                        JSONObject task = taskDetailList.optJSONObject(i);
                        if (task != null) {
                            mergeTask(groups, task, SOURCE_PROMO);
                        }
                    }
                }
            }

            // MAIN 源：queryMain 响应树内递归收集带状态的任务节点
            collectTasks(resultData, groups, SOURCE_MAIN);

            boolean handled = false;
            List<String> manualTitles = new ArrayList<>();
            for (List<TaskEntry> group : groups.values()) {
                TaskEntry action = pickAction(group);
                if (action == null || action.title.isEmpty()) {
                    continue;
                }
                if (isGroupHandledToday(group)) {
                    continue;
                }
                if (TaskBlacklist.isTaskInBlacklist(action.title)) {
                    Log.system(TAG, "任务在自动跳过列表(黑名单)中，跳过[" + action.title + "]");
                    continue;
                }
                String status = runStatus(action.task);
                if (!"not_done".equals(status) && !"not_sign".equals(status) && !"sign".equals(status)) {
                    continue;
                }
                if (tryExecuteTask(action, group, status, action.title)) {
                    handled = true;
                } else {
                    manualTitles.add(action.title);
                }
                TimeUtil.sleep((long) this.executeIntervalInt);
            }

            // 领奖闭环：taskData.completeList
            handled = claimCompleteList(resultData, groups) || handled;

            if (!manualTitles.isEmpty()) {
                StringBuilder titles = new StringBuilder();
                for (String t : manualTitles) {
                    if (titles.length() > 0) {
                        titles.append("、");
                    }
                    titles.append(t);
                }
                Log.other("余额宝体验金任务待手动完成: " + titles);
            }
            return handled;
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
            return false;
        }
    }

    private boolean tryExecuteTask(TaskEntry entry, List<TaskEntry> group, String status, String title) {
        try {
            JSONObject response = executeTask(entry, status);
            if (response != null && isActionSuccess(response)) {
                logRewards(title, response);
                markGroupHandled(group);
                return true;
            }
            // 按源回查任务状态
            JSONObject verified = queryTaskByEntry(entry);
            if (verified != null && isTaskReceived(verified)) {
                Log.other("余额宝体验金🍿[" + title + "]已完成");
                markGroupHandled(group);
                return true;
            }
            return false;
        } catch (Throwable th) {
            return false;
        }
    }

    /** 按源分发执行：PROMO → promosdk2024.task.complete；MAIN → promosdk.index.forward（固定 appletId） */
    private JSONObject executeTask(TaskEntry entry, String status) {
        if (SOURCE_PROMO.equals(entry.source)) {
            return requestString(
                    "com.alipay.yebpromobff.promosdk2024.task.complete",
                    "\"appName\":\"yebpromobff\",\"outBizNo\":\"" + entry.taskId + "-" + System.currentTimeMillis()
                            + "-\",\"playActionCode\":\"TASK_COMPLETE\",\"playEntrance\":\"HYQ_TASK_LIST_ENTRANCE_2\",\"taskId\":\""
                            + entry.taskId + "\"");
        }
        // MAIN 源：not_sign/sign → task.trigger，其余 → task.complete
        String path = ("not_sign".equals(status) || "sign".equals(status)) ? "task.trigger" : "task.complete";
        return forwardTask(entry.taskId, path);
    }

    /** MAIN 源任务统一走 promosdk.index.forward，appletId 固定 AP12183159、version 2 */
    private JSONObject forwardTask(String taskId, String path) {
        try {
            JSONObject params = new JSONObject();
            params.put("appletId", MAIN_QUERY_APPLET_ID);
            params.put("taskId", taskId);
            params.put("version", MAIN_QUERY_TASK_VERSION);
            JSONObject args = new JSONObject();
            args.put("params", params);
            args.put("path", path);
            return requestString("com.alipay.yebscenebff.promosdk.index.forward", args.toString());
        } catch (JSONException e) {
            return null;
        }
    }

    /** 按源回查任务：PROMO → queryTaskByTaskId；MAIN → queryMain(queryComplete=true, taskId) 定向查询 */
    private JSONObject queryTaskByEntry(TaskEntry entry) {
        try {
            if (SOURCE_PROMO.equals(entry.source)) {
                JSONObject response = requestString(
                        "com.alipay.yebpromobff.promosdk2024.task.queryTaskByTaskId",
                        "\"appName\":\"yebpromobff\",\"playActionCode\":\"TASK_STATUS_QUERY\",\"playEntrance\":\"HYQ_TASK_LIST_ENTRANCE_2\",\"taskId\":\""
                                + entry.taskId + "\"");
                if (response == null) {
                    return null;
                }
                JSONObject result = response.optJSONObject("result");
                JSONArray taskDetailList = result == null ? null : result.optJSONArray("taskDetailList");
                if (taskDetailList == null) {
                    return null;
                }
                for (int i = 0; i < taskDetailList.length(); i++) {
                    JSONObject task = taskDetailList.optJSONObject(i);
                    if (task != null && entry.taskId.equals(task.optString("taskId").trim())) {
                        return task;
                    }
                }
                return null;
            }
            JSONObject response = queryMain(true, entry.taskId);
            if (response == null) {
                return null;
            }
            return findTaskByIdInNode(response.optJSONObject("resultData"), entry.taskId);
        } catch (Throwable th) {
            return null;
        }
    }

    /** 领奖闭环：处理 queryMain 响应中已完成待领取的任务 */
    private boolean claimCompleteList(JSONObject resultData, Map<String, List<TaskEntry>> groups) {
        try {
            JSONObject taskData = resultData.optJSONObject("taskData");
            JSONArray completeList = taskData == null ? null : taskData.optJSONArray("completeList");
            if (completeList == null) {
                return false;
            }
            boolean claimed = false;
            for (int i = 0; i < completeList.length(); i++) {
                JSONObject rewardItem = completeList.optJSONObject(i);
                if (rewardItem == null) {
                    continue;
                }
                String rawTaskId = rewardItem.optString("taskId").trim();
                if (rawTaskId.isEmpty()) {
                    continue;
                }
                List<TaskEntry> group = findGroupForRewardItem(rawTaskId, rewardItem, groups);
                if (group != null && isGroupHandledToday(group)) {
                    continue;
                }
                String title = getCompletedTitle(rewardItem, group, rawTaskId);
                if (TaskBlacklist.isTaskInBlacklist(title)) {
                    Log.system(TAG, "任务在自动跳过列表(黑名单)中，跳过[" + title + "]");
                    continue;
                }

                TaskEntry action = group == null ? null : pickAction(group);
                String actionTaskId = action == null || action.taskId.isEmpty() ? rawTaskId : action.taskId;

                // 已完成待领奖：PROMO → task.complete；MAIN → task.trigger
                JSONObject response;
                if (action != null && SOURCE_PROMO.equals(action.source)) {
                    response = requestString(
                            "com.alipay.yebpromobff.promosdk2024.task.complete",
                            "\"appName\":\"yebpromobff\",\"outBizNo\":\"" + actionTaskId + "-" + System.currentTimeMillis()
                                    + "-\",\"playActionCode\":\"TASK_COMPLETE\",\"playEntrance\":\"HYQ_TASK_LIST_ENTRANCE_2\",\"taskId\":\""
                                    + actionTaskId + "\"");
                } else {
                    response = forwardTask(actionTaskId, "task.trigger");
                }

                if (response != null && isActionSuccess(response)) {
                    logRewards(title, response);
                    if (group != null) {
                        markGroupHandled(group);
                    } else {
                        Status.setFlagToday(FLAG_TASK_PREFIX + rawTaskId);
                    }
                    claimed = true;
                } else {
                    JSONObject verified = queryTaskByEntry(new TaskEntry(actionTaskId, title,
                            action != null ? action.source : SOURCE_MAIN, null));
                    if (verified != null && isTaskReceived(verified)) {
                        Log.other("余额宝体验金🍿[" + title + "]已完成");
                        if (group != null) {
                            markGroupHandled(group);
                        } else {
                            Status.setFlagToday(FLAG_TASK_PREFIX + rawTaskId);
                        }
                        claimed = true;
                    } else {
                        Log.system(TAG, "余额宝体验金任务领取失败[" + title + "]");
                    }
                }
                TimeUtil.sleep(500L);
            }
            return claimed;
        } catch (Throwable th) {
            return false;
        }
    }

    // ==================== 兑换 ====================

    /** 余额兑换 + 激活（固定活动参数，subThreshold 门槛判断） */
    private boolean handleExchange(JSONObject resultData) {
        try {
            if (Status.hasFlagToday(FLAG_EXCHANGE)) {
                return false;
            }
            String balanceText = resultData.optString("balance");
            double balance;
            try {
                balance = Double.parseDouble(balanceText);
            } catch (NumberFormatException e) {
                return false;
            }
            if (balance <= 0.0d) {
                Status.setFlagToday(FLAG_EXCHANGE);
                return false;
            }
            double threshold = optDouble(resultData, "subThreshold");
            if (threshold > 0.0d && balance < threshold) {
                Log.other("余额宝体验金未达兑换门槛: 当前" + balanceText + "，最低需" + threshold);
                Status.setFlagToday(FLAG_EXCHANGE);
                return false;
            }

            String bizOrderNo = UserMap.getCurrentUid() + System.currentTimeMillis();
            JSONObject exchangeResponse = requestString(
                    "com.alipay.yebscenebff.expgold.index.exchange",
                    "\"bizOrderNo\":\"" + bizOrderNo + "\",\"campId\":\"" + EXCHANGE_CAMP_ID
                            + "\",\"exchangeAmount\":\"" + balanceText + "\",\"prizeId\":\"" + EXCHANGE_PRIZE_ID + "\"");
            if (exchangeResponse == null) {
                Log.system(TAG, "余额宝体验金兑换失败");
                return false;
            }
            JSONObject exchangeResult = exchangeResponse.optJSONObject("result");
            String couponId = exchangeResult == null ? "" : exchangeResult.optString("equityNo");
            if (couponId.isEmpty()) {
                Log.system(TAG, "余额宝体验金兑换成功但缺少激活凭证");
                return false;
            }
            JSONObject activeResponse = requestString(
                    "alipay.yebprod.promo.yebTrial.active",
                    "\"couponId\":\"" + couponId + "\",\"equityType\":\"voucher\",\"type\":\"YEB_TRIAL\"");
            if (activeResponse == null) {
                Log.system(TAG, "余额宝体验金激活失败");
                return false;
            }
            String amountText = balanceText;
            JSONObject amountObj = activeResponse.optJSONObject("amount");
            if (amountObj != null && amountObj.opt("amount") != null) {
                amountText = amountObj.opt("amount").toString();
            }
            String confirmDate = activeResponse.optString("confirmDate");
            String profitDate = activeResponse.optString("profitDate");
            StringBuilder extra = new StringBuilder();
            if (!confirmDate.isEmpty()) {
                extra.append("[确认:").append(confirmDate).append("]");
            }
            if (!profitDate.isEmpty()) {
                extra.append("[收益:").append(profitDate).append("]");
            }
            Log.other("余额宝体验金💰[兑换激活]#" + amountText + "元" + extra);
            Status.setFlagToday(FLAG_EXCHANGE);
            return true;
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
            return false;
        }
    }

    // ==================== 券凭证（保留原有链路，兑换参数对齐新活动） ====================

    private boolean handleCertVoucherFlow() {
        try {
            // 1. 查询可用体验金凭证
            JSONObject queryRes = requestString("alipay.yebprod.query.queryYebTrialCertVoucher",
                    "\"component\":\"PROMO_ACTIVITY\",\"sortType\":\"drawTime\",\"source\":\"QIANAPP\"," +
                            "\"voucherTemplateIdList\":[\"202312260007300180780087H5IR\",\"2026011300073001807800H1558H\"]");
            if (queryRes == null) return false;

            JSONObject queryResult = queryRes.optJSONObject("result");
            JSONArray equityList = queryResult == null ? null : queryResult.optJSONArray("equityList");
            if (equityList == null || equityList.length() == 0) return false;

            boolean hasCanUse = false;
            for (int i = 0; i < equityList.length(); i++) {
                if ("CAN_USE".equalsIgnoreCase(equityList.getJSONObject(i).optString("equityStatus"))) {
                    hasCanUse = true;
                    break;
                }
            }
            if (!hasCanUse) return false;

            // 2. 转换凭证
            JSONObject convertRes = requestString("com.alipay.yebscenebff.needle.yebExpGoldVoucherConvert",
                    "\"convertType\":\"all\",\"isShowExchangeModal\":true");
            TimeUtil.sleep((long) this.executeIntervalInt);

            // 3. 对转换结果执行兑换+激活
            JSONArray convertResults = convertRes == null ? null : convertRes.optJSONArray("convertResults");
            if (convertResults == null) return false;

            boolean handled = false;
            for (int i = 0; i < convertResults.length(); i++) {
                JSONObject item = convertResults.getJSONObject(i).optJSONObject("value");
                if (item != null && item.optBoolean("success")) {
                    double amount = item.optDouble("amount", 0);
                    if (amount > 0) {
                        exchange(amount);
                        TimeUtil.sleep((long) this.executeIntervalInt);
                        handled = true;
                    }
                }
            }
            return handled;
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
            return false;
        }
    }

    private void exchange(double d) {
        try {
            String bizOrderNo = UserMap.getCurrentUid() + System.currentTimeMillis();
            JSONObject requestString = requestString(
                    "com.alipay.yebscenebff.expgold.index.exchange",
                    "\"bizOrderNo\":\"" + bizOrderNo + "\",\"campId\":\"" + EXCHANGE_CAMP_ID
                            + "\",\"exchangeAmount\":\"" + d + "\",\"prizeId\":\"" + EXCHANGE_PRIZE_ID + "\"");
            if (requestString == null) {
                TimeUtil.sleep((long) this.executeIntervalInt);
                return;
            }
            JSONObject result = requestString.optJSONObject("result");
            active(result == null ? "" : result.optString("equityNo"), true);
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private void active(String str, boolean z) throws JSONException {
        StringBuilder stringBuilder = new StringBuilder("\"couponId\":\"");
        stringBuilder.append(str);
        stringBuilder.append("\",\"type\":\"YEB_TRIAL\"");
        if (z) {
            stringBuilder.append(",\"equityType\":\"voucher\"");
        }
        JSONObject requestString = requestString("alipay.yebprod.promo.yebTrial.active", stringBuilder.toString());
        if (requestString != null) {
            StringBuilder logText = new StringBuilder("余额宝体验金🌭成功使用[");
            logText.append(requestString.optJSONObject("amount") == null
                    ? "" : requestString.optJSONObject("amount").optString("amount"));
            logText.append("元]开始计算收益[");
            logText.append(requestString.optString("confirmDate"));
            logText.append("]第一笔收益到账[");
            logText.append(requestString.optString("profitDate"));
            logText.append("]");
            Log.other(logText.toString());
        }
    }

    private void yebTrialAsset() {
        try {
            JSONObject response = requestString("alipay.yebprod.promo.yebTrialAsset", "");
            if (response == null) {
                TimeUtil.sleep((long) this.executeIntervalInt);
                return;
            }
            JSONArray trialInfoList = response.optJSONArray("trialInfoList");
            if (trialInfoList == null) {
                TimeUtil.sleep((long) this.executeIntervalInt);
                return;
            }
            for (int i = 0; i < trialInfoList.length(); i++) {
                JSONObject item = trialInfoList.getJSONObject(i);
                if (!"A".equals(item.optString("status"))) {
                    active(item.optString("trialId"), false);
                    TimeUtil.sleep((long) this.executeIntervalInt);
                }
            }
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    // ==================== RPC ====================

    /** queryMain（可选按 taskId 定向查询） */
    private JSONObject queryMain(boolean queryComplete, String taskId) {
        try {
            String taskPayload;
            if (taskId == null || taskId.isEmpty()) {
                taskPayload = "{\"downgrade\":false,\"queryComplete\":" + queryComplete
                        + ",\"strategyCode\":\"" + TASK_STRATEGY_CODE + "\"}";
            } else {
                taskPayload = "{\"downgrade\":false,\"queryComplete\":" + queryComplete
                        + ",\"startTime\":" + System.currentTimeMillis()
                        + ",\"strategyCode\":\"" + TASK_STRATEGY_CODE + "\",\"taskId\":\"" + taskId + "\"}";
            }
            return requestString(
                    "com.alipay.yebscenebff.needle.yebExpGold.queryMain",
                    "\"chInfo\":\"" + CH_INFO + "\",\"signIn\":{\"daysOfQuerySignInData\":21,\"displaySignInTextList\":"
                            + "[{\"value\":\"持\"},{\"value\":\"续\"},{\"value\":\"签\"},{\"value\":\"到\"},{\"value\":\"可\"},{\"value\":\"领\"},{\"value\":\"\"}],"
                            + "\"downgrade\":false,\"todayRedDotText\":\"戳这里\",\"tomorrowRedDotText\":\"\"},\"task\":" + taskPayload);
        } catch (Throwable th) {
            return null;
        }
    }

    public JSONObject requestString(String method, String params) {
        try {
            String response = ApplicationHook.requestString(method, "[{" + params + "}]");
            if (response == null) {
                return null;
            }
            JSONObject jo = new JSONObject(response);
            if (isSuccess(jo)) {
                return jo;
            }
            Log.system(TAG, "requestString err " + params);
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    // ==================== 归并辅助 ====================

    private static void collectTasks(Object node, Map<String, List<TaskEntry>> groups, String source) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            String taskId = obj.optString("taskId").trim();
            if (!taskId.isEmpty() && hasTrackableStatus(obj)) {
                mergeTask(groups, obj, source);
            }
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                collectTasks(obj.opt(keys.next()), groups, source);
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                collectTasks(arr.opt(i), groups, source);
            }
        }
    }

    private static void mergeTask(Map<String, List<TaskEntry>> groups, JSONObject task, String source) {
        String taskId = task.optString("taskId").trim();
        if (taskId.isEmpty()) {
            return;
        }
        String title = getTaskTitle(task, taskId);
        String fingerprint = title.trim().replaceAll("\\s+", " ");
        List<TaskEntry> group = groups.get(fingerprint);
        if (group == null) {
            group = new ArrayList<>();
            groups.put(fingerprint, group);
        }
        for (TaskEntry entry : group) {
            if (entry.taskId.equals(taskId)) {
                return;
            }
        }
        group.add(new TaskEntry(taskId, title, source, task));
    }

    /** 执行动作优先 PROMO 源，否则取组内第一个 */
    private static TaskEntry pickAction(List<TaskEntry> group) {
        for (TaskEntry entry : group) {
            if (SOURCE_PROMO.equals(entry.source)) {
                return entry;
            }
        }
        return group.isEmpty() ? null : group.get(0);
    }

    private static boolean isGroupHandledToday(List<TaskEntry> group) {
        for (TaskEntry entry : group) {
            if (Status.hasFlagToday(FLAG_TASK_PREFIX + entry.taskId)) {
                return true;
            }
        }
        return false;
    }

    private static void markGroupHandled(List<TaskEntry> group) {
        for (TaskEntry entry : group) {
            Status.setFlagToday(FLAG_TASK_PREFIX + entry.taskId);
        }
    }

    private static List<TaskEntry> findGroupForRewardItem(
            String taskId, JSONObject rewardItem, Map<String, List<TaskEntry>> groups) {
        for (List<TaskEntry> group : groups.values()) {
            for (TaskEntry entry : group) {
                if (taskId.equals(entry.taskId)) {
                    return group;
                }
            }
        }
        String title = getCompletedTitle(rewardItem, null, taskId);
        if (title.isEmpty()) {
            return null;
        }
        String normalized = title.trim().replaceAll("\\s+", " ");
        for (List<TaskEntry> group : groups.values()) {
            if (!group.isEmpty()
                    && group.get(0).title.trim().replaceAll("\\s+", " ").equals(normalized)) {
                return group;
            }
        }
        return null;
    }

    private static JSONObject findTaskByIdInNode(Object node, String taskId) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            if (taskId.equals(obj.optString("taskId").trim()) && hasTrackableStatus(obj)) {
                return obj;
            }
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                JSONObject matched = findTaskByIdInNode(obj.opt(keys.next()), taskId);
                if (matched != null) {
                    return matched;
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject matched = findTaskByIdInNode(arr.opt(i), taskId);
                if (matched != null) {
                    return matched;
                }
            }
        }
        return null;
    }

    // ==================== 状态解析 ====================

    private static boolean hasTrackableStatus(JSONObject task) {
        return task.has("simplifiedStatus") || !task.optString("taskProcessStatus").isEmpty();
    }

    /** 归一化任务运行状态：complete / not_done / not_sign / sign / "" */
    private static String runStatus(JSONObject task) {
        String simplified = task.optString("simplifiedStatus").trim().toLowerCase();
        if (!simplified.isEmpty()) {
            return simplified;
        }
        switch (task.optString("taskProcessStatus").trim().toUpperCase()) {
            case "RECEIVE_SUCCESS":
            case "HAS_RECEIVED":
            case "RECEIVED":
            case "DONE":
            case "COMPLETE":
            case "COMPLETED":
            case "SUCCESS":
                return "complete";
            case "NOT_DONE":
            case "WAIT_COMPLETE":
            case "SIGNUP_COMPLETE":
            case "SIGNUP_COMPLETED":
            case "PROCESSING":
                return "not_done";
            case "NONE_SIGNUP":
            case "UN_SIGNUP":
            case "SIGNUP_EXPIRED":
                return "not_sign";
            default:
                return "";
        }
    }

    private static boolean isTaskReceived(JSONObject task) {
        return "complete".equals(runStatus(task))
                || "RECEIVE_SUCCESS".equals(task.optString("taskProcessStatus").toUpperCase());
    }

    private static boolean isSuccess(JSONObject jo) {
        return jo.optBoolean("success")
                || "100".equals(jo.optString("resultCode"))
                || "100000000".equals(jo.optString("code"));
    }

    private static boolean isActionSuccess(JSONObject response) {
        if (isSuccess(response)) {
            return true;
        }
        if (response.optInt("resultStatus", Integer.MIN_VALUE) == 1) {
            return true;
        }
        return hasSendSuccess(response);
    }

    private static boolean hasSendSuccess(JSONObject response) {
        JSONArray resultObjList = response.optJSONArray("resultObj");
        if (resultObjList != null) {
            for (int i = 0; i < resultObjList.length(); i++) {
                JSONObject resultItem = resultObjList.optJSONObject(i);
                if (resultItem == null) {
                    continue;
                }
                JSONArray prizeSendDetails = resultItem.optJSONArray("prizeSendDetails");
                if (prizeSendDetails == null) {
                    continue;
                }
                for (int j = 0; j < prizeSendDetails.length(); j++) {
                    JSONObject detail = prizeSendDetails.optJSONObject(j);
                    if (detail != null && "SUCCESS".equalsIgnoreCase(detail.optString("sendStatus"))) {
                        return true;
                    }
                }
            }
        }
        JSONObject result = response.optJSONObject("result");
        JSONArray prizeSendOrderList = result == null ? null : result.optJSONArray("prizeSendOrderList");
        if (prizeSendOrderList != null) {
            for (int i = 0; i < prizeSendOrderList.length(); i++) {
                JSONObject prizeOrder = prizeSendOrderList.optJSONObject(i);
                if (prizeOrder != null && "SUCCESS".equalsIgnoreCase(prizeOrder.optString("sendStatus"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getErrorDesc(JSONObject response) {
        String desc = response.optString("resultDesc");
        if (desc.isEmpty()) desc = response.optString("resultView");
        if (desc.isEmpty()) desc = response.optString("errorMessage");
        if (desc.isEmpty()) desc = response.optString("memo");
        if (desc.isEmpty()) desc = response.optString("desc");
        if (desc.isEmpty()) desc = response.optString("message");
        if (desc.isEmpty()) desc = response.toString();
        return desc;
    }

    private static String getTaskTitle(JSONObject task, String defaultTitle) {
        String title = task.optString("title");
        if (title.isEmpty()) {
            title = task.optString("taskMainTitle");
        }
        if (title.isEmpty()) {
            JSONObject extProps = task.optJSONObject("taskExtProps");
            if (extProps != null) {
                title = extProps.optString("title");
            }
        }
        if (title.isEmpty()) {
            title = defaultTitle;
        }
        return title;
    }

    private static String getCompletedTitle(JSONObject rewardItem, List<TaskEntry> group, String defaultTitle) {
        JSONObject ext = rewardItem.optJSONObject("ext");
        JSONObject morpho = ext == null ? null : ext.optJSONObject("TASK_MORPHO_DETAIL");
        if (morpho != null) {
            String title = morpho.optString("title");
            if (title.isEmpty()) {
                title = morpho.optString("taskMainTitle");
            }
            if (!title.isEmpty()) {
                return title;
            }
        }
        if (group != null && !group.isEmpty()) {
            return group.get(0).title;
        }
        return defaultTitle;
    }

    private static double optDouble(JSONObject jo, String key) {
        Object v = jo == null ? null : jo.opt(key);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof String) {
            try {
                return Double.parseDouble((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0d;
    }

    private void logRewards(String title, JSONObject response) {
        try {
            JSONArray resultObjList = response.optJSONArray("resultObj");
            if (resultObjList != null && resultObjList.length() > 0) {
                List<String> rewardNames = new ArrayList<>();
                for (int i = 0; i < resultObjList.length(); i++) {
                    JSONObject resultItem = resultObjList.optJSONObject(i);
                    if (resultItem == null) {
                        continue;
                    }
                    JSONArray prizeSendDetails = resultItem.optJSONArray("prizeSendDetails");
                    if (prizeSendDetails == null) {
                        continue;
                    }
                    for (int j = 0; j < prizeSendDetails.length(); j++) {
                        JSONObject detail = prizeSendDetails.optJSONObject(j);
                        if (detail == null) {
                            continue;
                        }
                        String prizeName = "";
                        JSONObject prizeBaseInfo = detail.optJSONObject("prizeBaseInfo");
                        if (prizeBaseInfo != null) {
                            prizeName = prizeBaseInfo.optString("prizeName");
                        }
                        if (prizeName.isEmpty()) {
                            JSONObject extInfo = detail.optJSONObject("extInfo");
                            if (extInfo != null) {
                                prizeName = extInfo.optString("promoPrizeName");
                            }
                        }
                        if (prizeName.isEmpty()) {
                            JSONObject extInfo = detail.optJSONObject("extInfo");
                            if (extInfo != null) {
                                prizeName = extInfo.optString("title");
                            }
                        }
                        if (!prizeName.isEmpty()) {
                            rewardNames.add(prizeName);
                        }
                    }
                }
                if (!rewardNames.isEmpty()) {
                    for (String prizeName : rewardNames) {
                        Log.other("余额宝体验金💰[" + title + "]#" + prizeName);
                    }
                    return;
                }
            }
            JSONObject result = response.optJSONObject("result");
            JSONArray prizeSendOrderList = result == null ? null : result.optJSONArray("prizeSendOrderList");
            if (prizeSendOrderList != null && prizeSendOrderList.length() > 0) {
                for (int i = 0; i < prizeSendOrderList.length(); i++) {
                    JSONObject prizeOrder = prizeSendOrderList.optJSONObject(i);
                    if (prizeOrder == null) {
                        continue;
                    }
                    String prizeName = prizeOrder.optString("prizeName");
                    if (!prizeName.isEmpty()) {
                        Log.other("余额宝体验金💰[" + title + "]#" + prizeName);
                    } else {
                        Log.other("余额宝体验金💰[" + title + "]");
                    }
                }
                return;
            }
            Log.other("余额宝体验金💰[" + title + "]");
        } catch (Throwable ignored) {
        }
    }
}
