package fansirsqi.xposed.sesame.task.welfareCenter;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class WelfareCenter extends ModelTask {
    private static final String TAG = "网商银行🏦";
    private static final String displayName = "网商银行🏦";
    private static final int DEFAULT_INTERVAL = 3000;

    // 使用原子操作保证线程安全
    private final AtomicInteger executeIntervalInt = new AtomicInteger(DEFAULT_INTERVAL);

    // 配置字段
    private final BooleanModelField assignDateExpirePoint;
    private final IntegerModelField executeInterval = new IntegerModelField("executeInterval", "执行间隔(毫秒)", executeIntervalInt.get());
    private final BooleanModelField welfareCenterProfit;
    private final BooleanModelField welfareCenterTask;
    private final BooleanModelField welfareCenterWSLuckDraw;
    private final BooleanModelField welfareCenterWSTask;
    private final BooleanModelField welfarefinedu;
    private final BooleanModelField wenLiBao;

    public WelfareCenter() {
        this.welfareCenterProfit = new BooleanModelField("welfareCenterProfit", "福利金领奖", false);
        this.welfareCenterTask = new BooleanModelField("welfareCenterTask", "福利金任务", false);
        this.welfareCenterWSTask = new BooleanModelField("welfareCenterWSTask", "网商银行任务", false);
        this.welfareCenterWSLuckDraw = new BooleanModelField("welfareCenterWSLuckDraw", "网商银行发发日抽奖", false);
        this.assignDateExpirePoint = new BooleanModelField("assignDateExpirePoint", "快过期抽奖", false);
        this.welfarefinedu = new BooleanModelField("welfarefinedu", "金融教育基地 | 学分", false);
        this.wenLiBao = new BooleanModelField("wenLiBao", "稳利宝", false);
    }
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(this.executeInterval);
        modelFields.addField(this.welfareCenterProfit);
        modelFields.addField(this.welfareCenterTask);
        modelFields.addField(this.welfareCenterWSTask);
        modelFields.addField(this.welfareCenterWSLuckDraw);
        modelFields.addField(this.assignDateExpirePoint);
        modelFields.addField(this.wenLiBao);
        modelFields.addField(this.welfarefinedu);
        return modelFields;
    }

    @Override
    public boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.runtime("⏸ 当前为只收能量时间【" + BaseModel.getEnergyTime().getValue() + "】，停止执行" + getName() + "任务！");
            return false;
        } else if (TaskCommon.IS_MODULE_SLEEP_TIME) {
            Log.runtime("💤 模块休眠时间【" + BaseModel.getModelSleepTime().getValue() + "】停止执行" + getName() + "任务！");
            return false;
        } else {
            return true;
        }
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public String getIcon() {
        return "";
    }

    @Override
    public String getName() {
        return "网商银行";
    }
    private void executeWithDelay(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        } finally {
            TimeUtil.sleep(executeIntervalInt.get());
        }
    }

    //  本月快过期的进行抽奖
    private void assignDateExpirePoint() {
        if (!this.assignDateExpirePoint.getValue()) {
            return;
        }

        executeWithDelay(() -> {
            try {
                String nextMonthFirstDay = TimeUtil.getNextMonthFirstDay(); // yyyy-MM-dd
                String s = WelfareCenterRpcCall.pointBanlance(nextMonthFirstDay);
                if ( s == null  || s.isEmpty()){
                    return;
                }
                JSONObject response = new JSONObject(s);
                if (!response.optBoolean("success")) {
                    Log.error(TAG + "网商银行🏦查询余额信息失败" + response);
                    return;
                }

                JSONObject result = response.getJSONObject("result");
                JSONObject expirePoint = result.optJSONObject("assignDateExpirePoint");

                int pointBalance = result.optInt("pointBalance");
                int currentYearExpirePoint = result.optInt("currentYearExpirePoint");

                if (expirePoint == null) {
                    Log.runtime(TAG + "总福利金[" + pointBalance + "]今年快过期[" + currentYearExpirePoint + "]本月过期[无]");
                } else {
                    String expireKey = nextMonthFirstDay.replace("-", "");
                    int expireValue = expirePoint.optInt(expireKey, 0);
                    Log.runtime(TAG + "总福利金[" + pointBalance + "]今年快过期[" + currentYearExpirePoint + "]本月过期[" + expireValue + "]");
                }

//                String expireKey = nextMonthFirstDay.replace("-", "");
//                int optInt = expirePoint != null ? expirePoint.optInt(expireKey, 0) : 0;
//
//                if (optInt <= 0) {
//                    return;
//                }
//
//                optInt /= 300;
//                String extParams = "{\"bkPointUseMemo\": \"抽奖消耗\",\"pcbfcCertMemo\": \"FULICenterUSE\"}";
//
//                for (int i = 0; i <= optInt; i++) {
//                    JSONObject drawResponse = new JSONObject(WelfareCenterRpcCall.campTrigger("CP15205657", extParams));
//
//                    if (drawResponse.getBoolean("success")) {
//                        String prizeName = JsonUtil.getValueByPath(drawResponse, "result.prizes.[0].prizeName");
//                        Log.error(TAG + "抽奖获得[" + prizeName + "]");
//                        TimeUtil.sleep((long) executeIntervalInt.get());
//                    }
//                }

            } catch (Exception e) {
                Log.error(TAG + "执行失败，错误信息：" + e.getMessage());
            }
        });
    }


    //  批量使用虚拟福利金
    private void batchUseVirtualProfit() {
        executeWithDelay(() -> {
            try {
                String sceneCode = "PLAY102815727";
                JSONObject response = new JSONObject(WelfareCenterRpcCall.queryEnableVirtualProfitV2(sceneCode));

                if (!response.optBoolean("success")) {
                    Log.error(TAG + ".batchUseVirtualProfit err " + response.optString("resultDesc"));
                    return;
                }

                JSONArray profitList = response.getJSONObject("result").getJSONArray("virtualProfitList");

                for (int i = 0; i < profitList.length(); i++) {
                    JSONObject item = profitList.getJSONObject(i);

                    if ("signin".equals(item.optString("type"))) {
                        signIn(sceneCode);
                    } else {
                        JSONArray ids = item.optJSONArray("virtualProfitIds");
                        if (ids != null && ids.length() > 0) {
                            JSONObject useResponse = new JSONObject(WelfareCenterRpcCall.batchUseVirtualProfit(ids));

                            if (useResponse.getBoolean("success")) {
                                Log.runtime(String.format("网商银行🏦福利金[%s]%s×%d",
                                        item.getString("sceneDesc"),
                                        item.getString("reward"),
                                        ids.length()));
                            } else {
                                Log.error(TAG + ".batchUseVirtualProfit err " + useResponse.optString("resultDesc"));
                            }
                        }
                    }
                }

            } catch (Exception e) {
                Log.error(TAG + ".batchUseVirtualProfit error: ", String.valueOf(e));
            }
        });
    }

    //  发发日抽扭蛋
    private void playTrigger() {
        executeWithDelay(() -> {
            try {
                JSONObject response = new JSONObject(WelfareCenterRpcCall.queryCert(new String[]{"CT02048186", "CT32675397"}));
                if (!response.optBoolean("success")) {
                    Log.error(TAG + ".发发日抽扭蛋 err " + response.optString("resultDesc"));
                    return;
                }
                JSONObject cert = (JSONObject) JsonUtil.getValueByPathObject(response, "result.cert");
                if (cert == null) {
                    return;
                }

                Iterator<String> keys = cert.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    int count = cert.getInt(key);

                    for (int i = 0; i < count; i++) {
                        String triggerResponse = WelfareCenterRpcCall.playTrigger("PLAY100576638");
                        TimeUtil.sleep(500);

                        JSONObject result = new JSONObject(triggerResponse);
                        if (result.optBoolean("success")) {
                            JSONArray prizes = (JSONArray) JsonUtil.getValueByPathObject(result, "result.extInfo.result.sendResult.prizeSendOrderList");
                            if (prizes != null) {
                                for (int j = 0; j < prizes.length(); j++) {
                                    JSONObject prize = prizes.getJSONObject(j);
                                    Log.runtime("网商银行🏦获得[" + prize.getString("prizeName") + "]");
                                }
                            }
                        } else {
                            Log.error(TAG + ".发发日抽扭蛋 err " + result.optString("resultDesc"));
                        }
                    }
                }

            } catch (Exception e) {
                Log.error(TAG + ".发发日抽扭蛋 error: ", String.valueOf(e));
            }
        });
    }

    //  签到
    private void signIn(String sceneCode) {
        executeWithDelay(() -> {
            try {
                JSONObject response = new JSONObject(WelfareCenterRpcCall.signInTrigger(sceneCode));

                if (response.getBoolean("success")) {
                    Log.runtime(String.format("网商银行🏦福利金[签到成功]%s",
                            JsonUtil.getValueByPath(response, "result.prizeOrderDTOList.[0].price")));
                } else {
                    Log.error(TAG + ".signIn err: " + response.optString("resultDesc"));
                }

            } catch (Exception e) {
                Log.error(TAG + ".signIn error: ", String.valueOf(e));
            }
        });
    }

    // 签到
    private void signinPlay() {
        try {
            if (!Status.hasFlagToday(CompletedKeyEnum.WelfareCenterSigninPlay.name())) {
                JSONObject response = new JSONObject(WelfareCenterRpcCall.signinPlay());

                if (response.optBoolean("success")) {
                    Log.runtime(String.format("网商银行🏦签到[%s]",
                            JsonUtil.getValueByPath(response, "result.todaySignInfo.signPrizeSentPoint.point")));
                    Status.setFlagToday(CompletedKeyEnum.WelfareCenterSigninPlay.name());
                } else if (response.optBoolean("signNotAdmit", false) && !response.optBoolean("canRetry", false)) {
                    Log.runtime("网商银行🏦签到已完成(今日)");
                    Status.setFlagToday(CompletedKeyEnum.WelfareCenterSigninPlay.name());
                } else if (!response.optBoolean("canRetry", false)) {
                    Log.error(TAG + ".signinPlay err: " + response.optString("resultDesc"));
                }
            }
        } catch (Exception e) {
            Log.error(TAG + ".signinPlay exception: ", String.valueOf(e));
        }
    }

    @Override
    public void runJava() {
//        if(Status.hasFlagToday(CompletedKeyEnum.WelfareCenterTask.name())) {
//            return;
//        }
        long hour = TimeUtil.getHourOfDay();
        if (hour < 7 ) {
            return;
        }
        // 合并配置更新逻辑
        int intervalValue = Math.max(
                ((Integer) this.executeInterval.getValue()).intValue(),
                executeIntervalInt.get());
        executeIntervalInt.set(intervalValue);

        // 顺序执行各任务模块
        if (this.welfareCenterTask.getValue()) {
            WelfareCenterRpcCall.doTask("AP1269301", TAG, "网商银行🏦福利金");
        }

//        if (this.welfareCenterWSTask.getValue()) {
//            WelfareCenterRpcCall.doTask("AP12202921", TAG, displayName);
//        }

        if (this.welfareCenterWSLuckDraw.getValue()) {
            playTrigger();
        }

        if (this.welfareCenterProfit.getValue()) {
            batchUseVirtualProfit();
            signinPlay();
        }
        if (this.welfarefinedu.getValue()){
            new Finedu().handle();
        }
        if (this.wenLiBao.getValue()){
            new WenLiBao().handle();
        }
        assignDateExpirePoint();
        //Status.setFlagToday(CompletedKeyEnum.WelfareCenterTask.name());
    }


}
