package fansirsqi.xposed.sesame.task.otherTask2;


import android.annotation.SuppressLint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

/**
 * 碰一碰街区,每日初始3体力值
 */
public  class TouchPay extends BaseCommTask {
    private static final String TAG = "碰一碰街区 🌊";
    private static final Map<String,String> methods = new HashMap<>(){
        {
            put("sign", "alipay.ofpgrowth.ngames.signIn.sign");//签到
            put("userInfo", "alipay.ofpgrowth.ngames.homepage.query");//用户信息
            put("taskList", "alipay.ofpgrowth.ngames.task.recall");//任务列表
            put("prize", "alipay.ofpgrowth.ngames.exchange.prize.recall");//奖品列表
            put("trigger", "alipay.promoprod.applet.trigger");//任务
            put("gameStart", "alipay.ofpgrowth.ngames.game.start");//赚身价开始
            put("gameSettle", "alipay.ofpgrowth.ngames.game.settle");//赚身价完成
            put("getAq", "alipay.smcp.ae.task.scene");//获取题目
            put("answer", "alipay.smcp.ae.task.answer");//答题
            put("finish", "alipay.smcp.ae.task.finish.v2");//提交全部答案
        }
    };
    private static final String COMMON_PARAM = "[{\"mock\":false}]";
    private static final Map<String,String> TASKTYPE = new HashMap<>(){
        {
            put("video", "WATCH_VIDEO");//看视频
            //put("sub", "SUBSCRIBE_MESSAGE");//订阅（终身一次，不做）
            //put("pay", "PENGYIPENG");//碰一碰支付（不能做）
            put("share", "SHARE");//邀请好友每日五次
            put("aq", "QUESTION");//答题
        }
    };
    // 游戏名称映射
    private static final Map<String,String> GAME_NAMES = new HashMap<>(){
        {
            put("hitBlueRing", "点击碰一碰");
            put("flipMenu", "翻牌");
        }
    };
    private static final String STAGE_CODE_SIGNUP = "signup";//领取
    private static final String STAGE_CODE_SEND = "send";//提交
    private static Integer currentHp =0; //当前体力值
    private static Integer currentWealthAmount =0;  //当前身价

    @Override
    protected void handle() {
        try {
            if (Status.hasFlagToday("TouchPays") || Status.hasFlagToday("TouchPay_queryUserInfo")){
                return;
            }
            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
            if (!queryUserInfo()){
                return;
            }
            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
            if (!Status.hasFlagToday("TouchPaySign")){
                sign();
            }
            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
            queryTaskList();
            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
            queryUserInfo();
            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
            taskGame();
        } catch (Exception e) {
            Log.error(TAG+"❌handle--异常:" + e);
        }
    }

    /**
     * 查询用户信息
     */
    private boolean queryUserInfo(){
        try {
            JSONObject response = new JSONObject(RequestManager.requestString(methods.get("userInfo"), COMMON_PARAM));
            if (!response.optBoolean("success")){
                Log.error(TAG + "查询用户信息失败: " + response);
                Status.setFlagToday("TouchPay_queryUserInfo");
                return false;
            }
            JSONObject data = response.optJSONObject("data");
            currentHp = data.optInt("currentHp");
            currentWealthAmount = data.optInt("currentWealthAmount");
            Log.other(TAG + "当前体力值["+currentHp+"] 当前身价["+currentWealthAmount+"]");
            return true;
        }catch (JSONException e){
            Log.error(TAG + "查询用户信息异常: " + e.getMessage());
            Status.setFlagToday("TouchPay_queryUserInfo");
            return false;
        }
    }
    /**
     * 签到
     */
    private void sign() {
        try {
            JSONObject response = new JSONObject(RequestManager.requestString(methods.get("sign"), COMMON_PARAM));
            if (response.optBoolean("success")) {
                JSONObject data = response.optJSONObject("data");
                if (data != null) {
                    JSONObject prize = data.optJSONObject("prize");
                    if (prize != null) {
                        JSONObject customMemo = prize.optJSONObject("customMemo");
                        if (customMemo != null) {
                            String PRIZE_AMOUNT = customMemo.optString("PRIZE_AMOUNT");
                            Log.other(TAG + "签到成功+" + PRIZE_AMOUNT + "身价");
                            return;
                        }
                    }
                }
            } else {
                Log.error(TAG + "签到失败: " + response);
            }
            Status.setFlagToday("TouchPaySign");
        } catch (Exception e) { // 捕获所有异常
            Log.error(TAG + "签到异常: " + e.getMessage());
        }
    }


    /**
     * 查询任务列表
     */
    private void queryTaskList(){
        try {
            JSONObject response = new JSONObject(RequestManager.requestString(methods.get("taskList"), COMMON_PARAM));
            if (response.optBoolean("success")){
                JSONObject data = response.optJSONObject("data");
                JSONArray tasks = data.optJSONArray("tasks");
                for (int i = 0; i < tasks.length(); i++) {
                    String title = tasks.optJSONObject(i).optString("title");
                    String taskCenterKey = tasks.optJSONObject(i).optString("taskCenterKey");
                    String taskInstanceId = tasks.optJSONObject(i).optString("taskInstanceId");
                    String taskType = tasks.optJSONObject(i).optString("taskType");
                    String status = tasks.optJSONObject(i).optString("status");
                    TimeUtil.sleep(RandomUtil.nextInt(3000,4000));

                    //看视频
                    if (TASKTYPE.get("video").equals(taskType)&& status.equals("VALID")){
                        taskComplate(taskInstanceId, STAGE_CODE_SIGNUP, taskCenterKey);
                        TimeUtil.sleep(RandomUtil.nextInt(13000,14000));
                        if(taskComplate(taskInstanceId, STAGE_CODE_SEND, taskCenterKey)){
                            Log.other(TAG + "完成["+title+"]");
                        }
                    }
                    //邀请好友
                    if (TASKTYPE.get("share").equals(taskType) && status.equals("VALID")){
                        // 从title中提取数字，格式 "邀请好友加入碰碰街区 (0/5)"
                        int maxCount = 5; // 默认值
                        int currentCount = 0; // 默认值
                        try {
                            // 提取括号中的数字 "(*/*)"
                            int start = title.lastIndexOf("(");
                            int end = title.lastIndexOf(")");
                            if (start != -1 && end != -1 && end > start) {
                                String numbers = title.substring(start + 1, end); // 提取括号内的内容
                                String[] parts = numbers.split("/");
                                if (parts.length == 2) {
                                    currentCount = Integer.parseInt(parts[0]); // 当前完成数
                                    maxCount = Integer.parseInt(parts[1]); // 总数
                                }
                            }
                        } catch (Exception e) {
                            Log.error(TAG + "解析title数字异常: " + e.getMessage());
                        }
                        TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
                        // 计算还需要执行的次数
                        int needCount = maxCount - currentCount;
                        Log.other(TAG + "邀请任务进度[" + currentCount + "/" + maxCount + "]");
                        // 领取任务
                        taskComplate(taskInstanceId, STAGE_CODE_SIGNUP, taskCenterKey);
                        for (int j = 0; j < needCount; j++) {
                            TimeUtil.sleep(RandomUtil.nextInt(20000, 30000));
                            if (taskComplate(taskInstanceId, STAGE_CODE_SEND, taskCenterKey)) {
                                Log.other(TAG + "邀请好友成功[" + (j+1) + "/" + needCount + "]");
                            }
                            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
                        }
                    }
                    //答题
                    if(TASKTYPE.get("aq").equals(taskType) && status.equals("VALID")){
                        taskComplate(taskInstanceId, STAGE_CODE_SIGNUP, taskCenterKey);
                        TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
                        processAq();
                    }
                }
            }
        }catch (JSONException e){
            Log.error(TAG + "查询任务列表异常: " + e.getMessage());
        }
    }
    /**
     * 触发任务
     * @param appletId 参数1
     * @param stageCode 任务状态
     * @param taskCenterKey 参数2
     * @return true:任务完成 false:任务失败
     */
    private boolean taskComplate(String appletId,String stageCode, String taskCenterKey){
        try {
            String param = "[{\"appletId\":\""+appletId+"\",\"stageCode\":\""+stageCode+"\",\"taskCenInfo\":\""+taskCenterKey+"\"}]";
            JSONObject response = new JSONObject(RequestManager.requestString(methods.get("trigger"), param));
            if (response.optBoolean("success")){
                return true;
            } else if (response.has("errorMsg")) {
                Log.other(TAG + "任务失败: " + response.optString("errorMsg", "未知错误"));
            } else {
                Log.error(TAG + "任务失败: " + response);
            }
        }catch (JSONException e){
            Log.error(TAG + "完成任务异常: " + e.getMessage());
            return false;
        }
        return false;
    }


    /**
     * 赚身价游戏
     * --便利店 点击碰一碰/炸弹游戏
     * --餐厅 翻菜单
     * --学校
     */
    @SuppressLint("NewApi")
    private void taskGame(){
        try {
            if (currentHp <= 0){
                Log.other(TAG + "当前体力值不足，无法进行游戏");
                Status.setFlagToday("TouchPays");
                return;
            }

            // 定义游戏列表
            String[] gameNames = {"hitBlueRing", "flipMenu"};

            // 根据体力值循环玩游戏
            while (currentHp > 0) {
                // 随机选择一个游戏
                String gameName = gameNames[RandomUtil.nextInt(0, gameNames.length)];
                String displayName = GAME_NAMES.getOrDefault(gameName, gameName); // 获取中文名称

                // 开始游戏
                String param1 = "[{\"gameName\":\"" + gameName + "\"}]";
                JSONObject response = new JSONObject(RequestManager.requestString(methods.get("gameStart"), param1));
                TimeUtil.sleep(RandomUtil.nextInt(5000, 6000));

                if (response.optBoolean("success")){
                    JSONObject data = response.optJSONObject("data");
                    String recordId = data.getString("recordId");

                    TimeUtil.sleep(RandomUtil.nextInt(15000, 20000));

                    // 根据不同游戏设置不同分数
                    int score;
                    if ("hitBlueRing".equals(gameName)) {
                        score = RandomUtil.nextInt(1800, 1900); // 点击碰一碰
                    } else if ("flipMenu".equals(gameName)) {
                        score = 600; // 翻菜单
                    } else {
                        score = RandomUtil.nextInt(500, 1000); // 默认分数
                    }

                    String param2 = "[{\"extend\":{\"score\":" + score + ",\"scoreGrade\":\"S\"},\"gameName\":\"" + gameName + "\"," +
                            "\"startRecordId\":\"" + recordId + "\"}]";
                    JSONObject response2 = new JSONObject(RequestManager.requestString(methods.get("gameSettle"), param2));

                    if (response2.optBoolean("success")){
                        JSONObject data2 = response2.optJSONObject("data");
                        int incrWealthAmount = data2.optInt("incrWealthAmount", 0);
                        int currentWealthAmount = data2.optInt("currentWealthAmount", 0);
                        currentHp = data2.optInt("currentHp", 0); // 更新体力值
                        Log.other(TAG + "[" + displayName + "]赚身价成功,获得[" + incrWealthAmount + "]身价,当前身价[" + currentWealthAmount + "]剩余体力[" + currentHp + "]");
                    }
                } else if (response.has("errorMsg")) {
                    Log.other(TAG + "[" + displayName + "]赚身价失败: " + response.optString("errorMsg", "未知错误"));
                    // 如果是体力不足的错误，直接跳出循环
                    if (response.optString("errorMsg").contains("体力") || response.optString("errorMsg").contains("不足")) {
                        break;
                    }
                } else {
                    Log.error(TAG + "[" + displayName + "]赚身价失败: " + response);
                    return;
                }

                // 游戏间间隔
                TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            }
            Status.setFlagToday("TouchPays");
        } catch (JSONException e){
            Log.error(TAG + "赚身价异常: " + e.getMessage());
        }
    }

    /**
     * 答题
     */
    private void processAq(){
        try {
            //get
            String getParam = "[{\"sceneId\":\"PENGYIXIA_SPECIAL_ACTIVITIES\",\"version\":\"3.2\"}]";
            JSONObject getResponse = new JSONObject(RequestManager.requestString(methods.get("getAq"), getParam));
            if (!getResponse.optBoolean("success")){
                Log.error(TAG + "查询答题列表失败: " + getResponse.optString("errorMsg", "未知错误"));
                return;
            }
            JSONObject data = getResponse.optJSONObject("data");
            JSONArray taskList = data.optJSONArray("taskList");

            // 创建一个列表来存储答题信息
            JSONArray taskAnswers = new JSONArray();
            long currentTimeMs = System.currentTimeMillis();
            long currentTimeSec = currentTimeMs / 1000; // 转换为秒级时间戳

            for (int i = 0; i < taskList.length(); i++){
                JSONObject task = taskList.getJSONObject(i);
                int taskId = task.optInt("id");
                String appId = task.optString("appId", "2019012563168070");

                //answer
                String answerParam = "[{\"taskAnswers\":[{\"answer\":\"A\",\"taskId\":"+taskId+"}],\"version\":\"3.2\"}]";
                JSONObject answerResponse = new JSONObject(RequestManager.requestString(methods.get("answer"), answerParam));

                // 检查答题响应是否成功
                if (!answerResponse.optBoolean("success")) {
                    Log.error(TAG + "答题失败, 题目ID: " + taskId + ", 错误: " + answerResponse.optString("errorMsg", "未知错误"));
                    continue;
                }

                // 收集答题信息，包括答题时的时间戳(使用秒级时间戳)
                JSONObject taskAnswer = new JSONObject();
                taskAnswer.put("answer", "A");
                taskAnswer.put("appId", appId);
                taskAnswer.put("taskId", taskId);
                taskAnswer.put("userTaskLogId", currentTimeSec + i); // 使用秒级时间戳
                taskAnswers.put(taskAnswer);
                TimeUtil.sleep(RandomUtil.nextInt(1000, 2000));
            }
            TimeUtil.sleep(RandomUtil.nextInt(1000, 2000));
            // 动态生成日期
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            String currentDate = sdf.format(new java.util.Date());
            String batchTaskId = "PENGYIXIA_SPECIAL_ACTIVITIES-" + currentDate;
            long sceneStartTime = System.currentTimeMillis();

            // 构建提交参数
            JSONObject finishParamObj = new JSONObject();
            finishParamObj.put("batchTaskId", batchTaskId);
            finishParamObj.put("dropOut", false);
            finishParamObj.put("sceneId", "PENGYIXIA_SPECIAL_ACTIVITIES");
            finishParamObj.put("sceneSource", "illustration");
            finishParamObj.put("sceneStartTime", sceneStartTime); // 使用毫秒级时间戳
            finishParamObj.put("taskAnswers", taskAnswers);
            finishParamObj.put("version", "3.2");

            JSONArray finishParamArray = new JSONArray();
            finishParamArray.put(finishParamObj);

            JSONObject finishResponse = new JSONObject(RequestManager.requestString(methods.get("finish"), finishParamArray.toString()));

            if (!finishResponse.optBoolean("success")) {
                Log.error(TAG + "提交答题结果失败: " + finishResponse.optString("errorMsg", "未知错误"));
            } else {
                Log.other(TAG + "提交答题结果成功");
            }


        } catch (JSONException e){
            Log.error(TAG + "查询任务列表异常: " + e.getMessage());
        }

    }
}
