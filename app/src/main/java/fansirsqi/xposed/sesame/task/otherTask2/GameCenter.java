package fansirsqi.xposed.sesame.task.otherTask2;

import android.annotation.SuppressLint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.entity.task.GameCenterExchangePrize;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask;
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class GameCenter extends BaseCommTask {
    private static final String displayName = "游戏中心️️️☄️";
    @Override
    protected void handle() {
        try {
            if (Status.hasTemporaryStatusValid("GameCenter")){
                return;
            }
            //查询任务列表
            String s = queryTaskList();
            if (s == null || s.isEmpty()){
                Log.other(displayName+"查询任务列表失败");
                return;
            }
            try{
                JSONObject json = new JSONObject(s);
                if (json==null||!json.optBoolean("success")){
                    return;
                }
                JSONObject data = json.getJSONObject("data");
                JSONArray taskModuleList = data.getJSONArray("taskModuleList");
                for (int i = 0; i < taskModuleList.length(); i++) {
                    JSONObject taskModule = taskModuleList.getJSONObject(i);
                    JSONArray taskList = taskModule.getJSONArray("taskList");
                    for (int j = 0; j < taskList.length(); j++) {
                        JSONObject task = taskList.getJSONObject(j);
                        String actionType = task.optString("actionType", "未知任务类型");
                        String subTitle = task.optString("subTitle");
                        String taskId = task.optString("taskId");
                        if (actionType.equals("VIEW")){
                            Integer prizeAmount = task.optInt("prizeAmount",0);
                            //领取任务
                            signUp(taskId);
                            TimeUtil.sleep(RandomUtil.nextInt(15000,16000));
                            //完成任务
                            doTask(taskId);
                            Log.other(displayName+"["+subTitle+"]完成,获得["+prizeAmount+"]玩乐豆");
                            TimeUtil.sleep(RandomUtil.nextInt(1500,1600));
                        }
                        if (subTitle.equals("免费抽高级套装")||subTitle.equals("去淘宝特价版逛一逛")){
                            //去淘宝
                            signUp(taskId);
                            TimeUtil.sleep(RandomUtil.nextInt(15000,16000));
                            callApp();
                        }
                        if (subTitle.contains("访问游戏中心")||subTitle.contains("首页")){
                            //访问游戏中心
                            signUp(taskId);
                            TimeUtil.sleep(RandomUtil.nextInt(1000,3000));
                            clickTask();
                        }
                    }

                }
            }catch (JSONException e){
                Log.other(displayName+"任务处理异常: " + e.getMessage());
            }

            //收取玩乐豆
            try {
                String received = receiveTask();
                if (received == null || received.isEmpty()) {
                    return;
                }
                JSONObject reData = new JSONObject(received);
                if (reData.getBoolean("success")) {
                    JSONObject ReData = reData.getJSONObject("data");
                    String totalAmount = ReData.getString("totalAmount");
                    Log.other(displayName+"收取["+totalAmount+"]玩乐豆");
                    Status.setFlagToday(CompletedKeyEnum.GameCenterTask.name());
                }
            }  catch (Exception e) {
                Log.other(displayName+"收取任务异常: " + e.getMessage());
            }
            //查询玩乐豆
            queryPoint();
            Status.setTemporaryStatusWithExpiry("GameCenter",  1000 * 60 * 60 * 2);
        }catch (Exception e){
            Log.other(displayName+"任务处理异常: " + e.getMessage());
        }
    }

    //从首页访问
    private void clickTask() {
        String method = "com.alipay.gameclub.biz.rpc.home.queryHomePage";
        String data1 = "[{\"deviceLevel\":\"high\",\"recommend\":false,\"source\":\"returngongge\",\"unityDeviceLevel\":\"high\"}]}";
        String method2 = "com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBenefitAggPage";
        String data2 = "[{\"source\":\"returngongge\",\"sourceTab\":\"luckydraw\",\"surpriseBoxEnable\":true}]";
        RequestManager.requestString(method, data1);
        TimeUtil.sleep(RandomUtil.nextInt(1000,2000));
        RequestManager.requestString(method2, data2);
        Log.other(displayName+"完成[访问首页]");
    }
    //去淘宝浏览15s
    private void callApp(){
        String method = "alipay.antmember.callApp.queryCallAppSchema";
        String data = "[{\"sceneCode\":\"gamecenter_taobaolife_0530\"}]";//免费抽高级套餐
        String data2 = "[{\"sceneCode\":\"gamecenter_taobaolife_0605\"}]";//去淘宝特价版
        String s = RequestManager.requestString(method, data);
        TimeUtil.sleep(RandomUtil.nextInt(1000,6000));
        String s2 = RequestManager.requestString(method, data2);
        //Log.other(displayName+"完成[去淘宝]");
    }

    private String queryTaskList() {
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryModularTaskList",
                "[{\"deviceLevel\":\"high\",\"source\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\",\"sourceTab\":\"luckydraw\",\"unityDeviceLevel\":\"high\"}]");
    }

    private String signUp(String taskId){
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSignup",
                "[{\"source\":\"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity\"," +
                        "\"taskId\":\""+taskId+"\"}]");
    }

    private String doTask(String taskId){
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSend",
                "[{\"taskId\":\""+taskId+"\"}]");
    }

    private String receiveTask(){
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall",
                "[{}]"
        );
    }
    private void queryPoint(){
        if (Status.hasFlagToday("GameCenter_QueryPoint")){
            return;
        }
        String s = RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBenefitAggPage",
                "[{\"source\":\"returngongge\",\"sourceTab\":\"luckydraw\",\"surpriseBoxEnable\":true}]"
        );
        if (s ==  null || s.equals("")){
            return ;
        }
        try{
            JSONObject json = new JSONObject(s);
            if (json.optBoolean("success")){
                JSONObject  data = json.optJSONObject("data");
                JSONObject pointModule = data.optJSONObject("pointModule");
                //余额
                String availableAmount = pointModule.optString("availableAmount","0");
                String expireTipText = pointModule.optString("expireTipText");
                Log.other(displayName+"用户玩乐豆["+availableAmount+"]"+expireTipText);

                if (expireTipText != null && !expireTipText.isEmpty()) {
                    // 使用正则表达式提取即将过期的玩乐豆数量和过期时间
                    Pattern pattern = Pattern.compile("你有\\$$([0-9]+)\\$$玩乐豆即将在\\$$([^)]+)\\$$24点过期");
                    Matcher matcher = pattern.matcher(expireTipText);
                    if (matcher.find()) {
                        int expireAmount = Integer.parseInt(matcher.group(1));
                        String expireDateStr = matcher.group(2);

                        // 判断当前日期是否是过期日期
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年M月d日", Locale.getDefault());
                        Date expireDate = sdf.parse(expireDateStr);
                        Calendar expireCalendar = Calendar.getInstance();
                        expireCalendar.setTime(expireDate);
                        //获取兑换项
                        List<GameCenterExchangePrize> exchangeModule = getExchangeModule(data);
                        if (TimeUtil.isToday(expireCalendar)) {
                            Log.other(displayName + "今天是玩乐豆过期日期,自动兑换: " + expireDateStr);
                            // 如果今天是过期日期，则进行兑换操作
                            exchangePrizes(expireAmount, exchangeModule);
                        }
                    }
                }

            }
        }catch (JSONException e1){
            Log.error(displayName+"查询玩乐豆失败ParseException1:"+e1);
        } catch (ParseException e2) {
           Log.error(displayName+"查询玩乐豆失败ParseException2:"+e2);
        }finally {
            Status.setFlagToday("GameCenter_QueryPoint");
        }
    }

    //查询兑换项
    private List<GameCenterExchangePrize> getExchangeModule(JSONObject data) {
        List<GameCenterExchangePrize> exchangePrizesList = new ArrayList<>();
        try {
            JSONObject exchangePrizeModule = data.optJSONObject("exchangePrizeModule");
            JSONArray exchangePrizes = exchangePrizeModule.optJSONArray("exchangePrizes");

            if (exchangePrizes != null) {
                for (int i = 0; i < exchangePrizes.length(); i++) {
                    JSONObject prize = exchangePrizes.getJSONObject(i);
                    boolean exchangeButtonStatus = prize.optBoolean("exchangeButtonStatus", false);

                    if (exchangeButtonStatus) {
                        String campId = prize.optString("campId", "");
                        String prizeId = prize.optString("prizeId", "");
                        String prizeName = prize.optString("prizeName", "");
                        int consumePointAmount = prize.optInt("consumePointAmount", 0);
                        String prizeType = prize.optString("prizeType", "");

                        exchangePrizesList.add(new GameCenterExchangePrize(campId, prizeId, prizeName, consumePointAmount, prizeType));
                    }
                }
            }
        } catch (JSONException e) {
            Log.error(displayName + "解析 exchangePrizes 失败: " + e.getMessage());
        }
        return exchangePrizesList;
    }

    //兑换实现
    @SuppressLint("NewApi")
    private void exchangePrizes(int expireAmount, List<GameCenterExchangePrize> exchangePrizesList) {
        if (exchangePrizesList == null || exchangePrizesList.isEmpty()) {
            Log.other(displayName + "没有可兑换的奖励");
            return;
        }

        // 按照 consumePointAmount 从小到大排序
        exchangePrizesList.sort((o1, o2) -> Integer.compare(o1.getConsumePointAmount(), o2.getConsumePointAmount()));

        for (GameCenterExchangePrize prize : exchangePrizesList) {
            if (prize.getConsumePointAmount() <= expireAmount) {
                // 调用兑换方法，新增 prizeType 参数
                boolean success = exchangePrize(
                        prize.getCampId(),
                        prize.getPrizeId(),
                        prize.getPrizeName(),
                        prize.getConsumePointAmount(),
                        prize.getPrizeType()
                );
                if (success) {
                    Log.other(displayName + "成功兑换了 [" + prize.getPrizeName() + "]，消耗了 [" + prize.getConsumePointAmount() + "] 玩乐豆");
                    expireAmount -= prize.getConsumePointAmount();
                }
            }
        }
    }


    //兑换请求
    private boolean exchangePrize(String campId, String prizeId, String prizeName, int consumePointAmount, String prizeType) {
        try {
            // 构建请求体
            String requestBody;

            if ("SURPRISE_BOX".equals(prizeType) || "SURPRISE_BOX".equals(campId)) {
                // 抽奖的请求参数
                requestBody = "[{"
                        + "\"campId\":\"SURPRISE_BOX\","
                        + "\"prizeId\":\"SURPRISE_BOX\","
                        + "\"prizeType\":\"SURPRISE_BOX\","
                        + "\"source\":\"returngongge\""
                        + "}]";
            } else {
                // 兑换其他红包的参数
                requestBody = "[{"
                        + "\"campId\":\"" + campId + "\","
                        + "\"prizeId\":\"" + prizeId + "\","
                        + "\"source\":\"returngongge\""
                        + "}]";
            }

            // 发起请求
            String response = RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.doPointExchangePrize", requestBody);

            if (response == null || response.isEmpty()) {
                Log.other(displayName + "兑换 [" + prizeName + "] 失败");
                return false;
            }

            JSONObject json = new JSONObject(response);
            if (json.optBoolean("success")) {
                Log.other(displayName + "成功兑换了 [" + prizeName + "]");
                return true;
            } else {
                Log.other(displayName + "兑换 [" + prizeName + "] 失败: " + json.optString("message"));
                return false;
            }
        } catch (Exception e) {
            Log.error(displayName + "兑换 [" + prizeName + "] 异常: " + e.getMessage());
            return false;
        }
    }


}
