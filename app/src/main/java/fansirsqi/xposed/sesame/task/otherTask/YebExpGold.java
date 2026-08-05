package fansirsqi.xposed.sesame.task.otherTask;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class YebExpGold extends BaseCommTask{
    private static final String TAG = "余额宝体验金🌭";
    private int executeIntervalInt;

    private void active(String str, boolean z) throws JSONException {
        StringBuilder stringBuilder;
        StringBuilder stringBuilder2 = new StringBuilder("\"couponId\":\"");
        stringBuilder2.append(str);
        stringBuilder2.append("\",\"type\":\"YEB_TRIAL\"");
        str = stringBuilder2.toString();
        if (z) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str);
            stringBuilder.append(",\"equityType\":\"voucher\"");
            str = stringBuilder.toString();
        }
        JSONObject requestString = requestString("alipay.yebprod.promo.yebTrial.active", str);
        if (requestString != null) {
            stringBuilder = new StringBuilder("余额宝体验金🌭成功使用[");
            stringBuilder.append(JsonUtil.getValueByPath(requestString, "amount.amount"));
            stringBuilder.append("元]开始计算收益[");
            stringBuilder.append(requestString.getString("confirmDate"));
            stringBuilder.append("]第一笔收益到账[");
            stringBuilder.append(requestString.getString("profitDate"));
            stringBuilder.append("]");
            Log.other(stringBuilder.toString());
        }
    }

    private void doTask(String str, String str2, String str3) {
        String str4 = "\",\"taskId\":\"";
        String str5 = "AP16200213";
        String str6 = "余额宝体验金🍿完成[";
        String str7 = "\"appletId\":\"";
        String str8 = "\"params\":{\"appletId\":\"";
        try {
            String str9 = "complete";
            if (str5.equals(str2)) {
                str9 = "trigger";
            }
            StringBuilder stringBuilder = new StringBuilder(str8);
            stringBuilder.append(str);
            stringBuilder.append(str4);
            stringBuilder.append(str2);
            stringBuilder.append("\",\"version\":2},\"path\":\"task.");
            stringBuilder.append(str9);
            stringBuilder.append("\"");
            JSONObject requestString = requestString("com.alipay.yebscenebff.promosdk.index.forward", stringBuilder.toString());
            if (requestString == null) {
                TimeUtil.sleep((long) this.executeIntervalInt);
                return;
            }
            if (str5.equals(str2)) {
                StringBuilder stringBuilder2 = new StringBuilder(str7);
                stringBuilder2.append(str);
                stringBuilder2.append(str4);
                stringBuilder2.append(str2);
                stringBuilder2.append("\",\"activityScene\":\"yuebaotiyanjin\"");
                requestString("com.alipay.fincommonbff.needle.activityTaskReport", stringBuilder2.toString());
            }
            JSONObject jSONObject = (JSONObject) JsonUtil.getValueByPathObject(requestString, "result.prizeSendOrderList.[0].prizeCustomDisplayInfoDTO.extInfo");
            StringBuilder stringBuilder3 = new StringBuilder(str6);
            stringBuilder3.append(str3);
            stringBuilder3.append("]");
            if (jSONObject == null) {
                str = "";
            } else {
                StringBuilder stringBuilder4 = new StringBuilder();
                stringBuilder4.append(jSONObject.getString("PRIZE_AMOUNT"));
                stringBuilder4.append(jSONObject.getString("PRIZE_UNIT"));
                str = stringBuilder4.toString();
            }
            stringBuilder3.append(str);
            Log.other(stringBuilder3.toString());
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private void exchange(double d) {
        try {
            StringBuilder stringBuilder = new StringBuilder("\"bizOrderNo\":\"");
            stringBuilder.append(UserMap.getCurrentUid());
            stringBuilder.append(System.currentTimeMillis());
            stringBuilder.append("\",\"campId\":\"CP111609176\",\"exchangeAmount\":\"");
            stringBuilder.append(d);
            stringBuilder.append("\",\"prizeId\":\"PZ129283652\"");
            JSONObject requestString = requestString("com.alipay.yebscenebff.expgold.index.exchange", stringBuilder.toString());
            if (requestString == null) {
                TimeUtil.sleep((long) this.executeIntervalInt);
                return;
            }
            active(JsonUtil.getValueByPath(requestString, "result.equityNo"), true);
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    @Override
    protected void handle() throws JSONException {

    }

    public JSONObject requestString(String str, String str2) throws JSONException {
        StringBuilder stringBuilder = new StringBuilder("[{");
        stringBuilder.append(str2);
        stringBuilder.append("}]");
        JSONObject stringBuilder2 = new JSONObject(ApplicationHook.requestString(str, stringBuilder.toString()));
        if (stringBuilder2.getBoolean("success")) {
            return stringBuilder2;
        }
        str = TAG;
        stringBuilder = new StringBuilder("requestString err ");
        stringBuilder.append(str2);
        Log.system(str, stringBuilder.toString());
        return null;
    }

    private void signIn(String str, JSONArray jSONArray) throws JSONException {
        if (jSONArray != null) {
            boolean z = false;
            int i = 0;
            while (i < jSONArray.length()) {
                try {
                    JSONObject jSONObject = jSONArray.getJSONObject(i);
                    if ("今天".equals(jSONObject.optString("displayDate"))) {
                        z = "SIGNED".equals(JsonUtil.getValueByPath(jSONObject, "signInfo.signStatus"));
                        break;
                    }
                    i++;
                } catch (Throwable th) {
                    TimeUtil.sleep((long) this.executeIntervalInt);
                }
            }
            if (!z) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("\"signInPlayId\":\"");
                stringBuilder.append(str);
                stringBuilder.append("\"");
                JSONObject requestString = requestString("com.alipay.yebscenebff.needle.yebExpGold.signIn", stringBuilder.toString());
                if (requestString != null) {
                    requestString = (JSONObject) JsonUtil.getValueByPathObject(requestString, "resultData.resultData.prizeOrderDTOList.[0].customMemo");
                    StringBuilder stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("余额宝体验金🍟签到成功[");
                    if (requestString == null) {
                        str = "";
                    } else {
                        stringBuilder = new StringBuilder();
                        stringBuilder.append(requestString.getString("PRIZE_AMOUNT"));
                        stringBuilder.append(requestString.getString("PRIZE_UNIT"));
                        str = stringBuilder.toString();
                    }
                    stringBuilder2.append(str);
                    stringBuilder2.append("]");
                    Log.system(stringBuilder2.toString());
                    TimeUtil.sleep((long) this.executeIntervalInt);
                    return;
                }
            }
        }
        TimeUtil.sleep((long) this.executeIntervalInt);
    }

    private void taskQuery() {
        try {
            StringBuilder stringBuilder = new StringBuilder("\"chInfo\":\"ch_url-https://render.alipay.com/p/yuyan/180020010001282160/index.html\",\"signIn\":{\"daysOfQuerySignInData\":21,\"displaySignInTextList\":[{\"value\":\"持\"},{\"value\":\"续\"},{\"value\":\"签\"},{\"value\":\"到\"},{\"value\":\"可\"},{\"value\":\"领\"},{\"value\":\"\"}],\"downgrade\":false,\"todayRedDotText\":\"戳这里\",\"tomorrowRedDotText\":\"\"},\"task\":{\"downgrade\":false,\"queryComplete\":false,\"startTime\":");
            stringBuilder.append(System.currentTimeMillis());
            stringBuilder.append(",\"strategyCode\":\"YEB_TRIAL_ASSET_TASK_BLOCK_REC\"}");
            JSONObject requestString = requestString("com.alipay.yebscenebff.needle.yebExpGold.queryMain", stringBuilder.toString());
            if (requestString != null) {
                requestString = requestString.getJSONObject("resultData");
                double d = requestString.getDouble("balance");
                if (d > 300.0d) {
                    exchange(d);
                }
                signIn("PLAY102253251", (JSONArray) JsonUtil.getValueByPathObject(requestString, "signInData.list"));
                JSONArray jSONArray = (JSONArray) JsonUtil.getValueByPathObject(requestString, "taskData.taskListRes.resultData.taskList");
                if (jSONArray != null) {
                    for (int i = 0; i < jSONArray.length(); i++) {
                        JSONObject jSONObject = jSONArray.getJSONObject(i);
                        doTask(jSONObject.getString("appletId"), jSONObject.getString("taskId"), jSONObject.getString("title"));
                    }
                    Status.setFlagToday(CompletedKeyEnum.YebExpGold.name());
                    TimeUtil.sleep((long) this.executeIntervalInt);
                    return;
                }
            }
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private void yebTrialAsset() {
        String str = "yebTrialAsset err ";
        String str2;
        try {
            JSONObject stringBuilder = new JSONObject(ApplicationHook.requestString("alipay.yebprod.promo.yebTrialAsset", "[null]"));
            if (stringBuilder.getBoolean("success")) {
                JSONArray jSONArray = stringBuilder.getJSONArray("trialInfoList");
                for (int i = 0; i < jSONArray.length(); i++) {
                    JSONObject jSONObject = jSONArray.getJSONObject(i);
                    if (!"A".equals(jSONObject.getString("status"))) {
                        active(jSONObject.getString("trialId"), false);
                        TimeUtil.sleep((long) this.executeIntervalInt);
                    }
                }
                TimeUtil.sleep((long) this.executeIntervalInt);
                return;
            }
            str2 = TAG;
            StringBuilder stringBuilder2 = new StringBuilder(str);
            stringBuilder2.append(stringBuilder.optBoolean("resultDesc"));
            Log.system(str2, stringBuilder2.toString());
            TimeUtil.sleep((long) this.executeIntervalInt);
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    public void handle(int i) {
        this.executeIntervalInt = i;
        if (!Status.hasFlagToday(CompletedKeyEnum.YebExpGold.name())) {
            handleCertVoucherFlow();
            handlePromoTaskList();
            taskQuery();
            yebTrialAsset();
        }
    }

    // --- 券凭证转换+自动兑换+激活 ---
    private void handleCertVoucherFlow() {
        try {
            // 1. 查询可用体验金凭证
            JSONObject queryRes = requestString("alipay.yebprod.query.queryYebTrialCertVoucher",
                "\"component\":\"PROMO_ACTIVITY\",\"sortType\":\"drawTime\",\"source\":\"QIANAPP\"," +
                "\"voucherTemplateIdList\":[\"202312260007300180780087H5IR\",\"2026011300073001807800H1558H\"]");
            if (queryRes == null) return;

            JSONArray equityList = queryRes.optJSONObject("result") != null ?
                queryRes.optJSONObject("result").optJSONArray("equityList") : null;
            if (equityList == null || equityList.length() == 0) return;

            boolean hasCanUse = false;
            for (int i = 0; i < equityList.length(); i++) {
                if ("CAN_USE".equalsIgnoreCase(equityList.getJSONObject(i).optString("equityStatus"))) {
                    hasCanUse = true; break;
                }
            }
            if (!hasCanUse) return;

            // 2. 转换凭证
            JSONObject convertRes = requestString("com.alipay.yebscenebff.needle.yebExpGoldVoucherConvert",
                "\"convertType\":\"all\",\"isShowExchangeModal\":true");
            TimeUtil.sleep((long) this.executeIntervalInt);

            // 3. 对转换结果执行兑换+激活
            JSONArray convertResults = convertRes != null ? convertRes.optJSONArray("convertResults") : null;
            if (convertResults == null) return;

            for (int i = 0; i < convertResults.length(); i++) {
                JSONObject item = convertResults.getJSONObject(i).optJSONObject("value");
                if (item != null && item.optBoolean("success")) {
                    double amount = item.optDouble("amount", 0);
                    if (amount > 0) {
                        exchange(amount);
                        TimeUtil.sleep((long) this.executeIntervalInt);
                    }
                }
            }
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    // --- promo任务列表（独立任务源） ---
    private void handlePromoTaskList() {
        try {
            JSONObject response = requestString("com.alipay.yebpromobff.promosdk2024.task.query",
                "\"needTriggerPrize\":false,\"playActionCode\":\"TASK_LIST_CONSULT\",\"playEntrance\":\"HYQ_TASK_LIST_ENTRANCE_2\"");
            if (response == null) return;

            JSONArray taskDetailList = response.optJSONObject("result") != null ?
                response.optJSONObject("result").optJSONArray("taskDetailList") : null;
            if (taskDetailList == null || taskDetailList.length() == 0) return;

            for (int i = 0; i < taskDetailList.length(); i++) {
                JSONObject task = taskDetailList.getJSONObject(i);
                String taskId = task.optString("taskId").trim();
                if (taskId.isEmpty()) continue;

                String status = task.optString("simplifiedStatus",
                    task.optString("taskProcessStatus", ""));
                if ("COMPLETE".equalsIgnoreCase(status) || "RECEIVE_SUCCESS".equalsIgnoreCase(status)) continue;

                String title = task.optString("title", taskId);
                String outBizNo = taskId + "-" + System.currentTimeMillis() + "-";
                JSONObject completeRes = requestString("com.alipay.yebpromobff.promosdk2024.task.complete",
                    "\"appName\":\"yebpromobff\",\"outBizNo\":\"" + outBizNo +
                    "\",\"playActionCode\":\"TASK_COMPLETE\",\"playEntrance\":\"HYQ_TASK_LIST_ENTRANCE_2\",\"taskId\":\"" + taskId + "\"");
                if (completeRes != null && completeRes.optBoolean("success")) {
                    Log.other("余额宝体验金🍿完成推广任务[" + title + "]");
                }
                TimeUtil.sleep((long) this.executeIntervalInt);
            }
        } catch (Throwable th) {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }
}
