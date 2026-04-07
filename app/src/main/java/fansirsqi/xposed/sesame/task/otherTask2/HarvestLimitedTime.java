package fansirsqi.xposed.sesame.task.otherTask2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HarvestLimitedTime extends OtherTask2{
    private static final String TAG = "丰收节🌾";
    private static final String METHOD = "alipay.promoprod.play.trigger";//通用方法
    // 任务黑名单机制
    private static final Set<String> taskBlackList = ConcurrentHashMap.newKeySet();
    public void handle() {
        if (Status.hasFlagToday("HarvestComplete")){
            return;
        }
        //查询用户信息
        queryUserInfo();
        //签到
        signIn();
        //查询任务列表并完成
        queryTaskListAndComplete();
        //检查逛一逛剩余任务
        checkAndCompleteRemainingTasks();
    }

    private void queryTaskListAndComplete() {
        queryTaskList();
    }

    private void queryUserInfo() {
        try {
            String params = "[{\"extInfo\":{\"alipayMainSearch\":\"false\",\"shareChannel\":\"\",\"shareUserToken\":\"\"},\"operation\":\"VITALITY_DIRECT_POPUP_LIST\",\"playInfo\":\"SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc\"}]";
            JSONObject json = new JSONObject(RequestManager.requestString(METHOD, params));
            if (json.optBoolean("success")) {
                JSONObject extInfo = json.optJSONObject("extInfo");
                JSONObject homepageResult = extInfo.optJSONObject("homepageResult");
                String delayedExchangeEndDate = homepageResult.optString("delayedExchangeEndDate");
                String receivedTotalAmount = homepageResult.optString("receivedTotalAmount");
                Log.other(TAG+"用户[" + receivedTotalAmount + "元]最迟兑换:" + delayedExchangeEndDate);
            }else {
                Log.error(TAG+"查询用户信息失败: "+json);
            }
            TimeUtil.sleep(RandomUtil.nextInt(2000,3000));
        }catch (Exception e){
            Log.error(TAG+"查询用户信息失败: "+e);
        }
    }

    //登录/签到奖励
    private void signIn(){
        try {
            if (Status.hasFlagToday("HarvestLimitedTimeSign")){
                return;
            }
            String params = "[{\"extInfo\":{\"prizeSource\":\"widget\"},\"operation\":\"VITALITY_PRIZE_GRANT\",\"playInfo\":\"SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc\"}]";
            JSONObject s = new JSONObject(RequestManager.requestString(METHOD, params));
            if (s.optBoolean("success")){
                Log.other(TAG+"签到成功");
            }else {
                Log.other(TAG+"签到失败: "+s);
            }
            Status.setFlagToday("HarvestLimitedTimeSign");
            TimeUtil.sleep(RandomUtil.nextInt(2000,3000));
        }catch (JSONException  e){
            Log.other(TAG+"签到奖励失败: "+e);
        }
    }



    // 查询任务列表
    private void queryTaskList() {
        try {
            String params = "[{\"extInfo\":{\"sysTriger\":\"\"},\"operation\":\"INTENSIFY_TASK_LIST_QUERY\",\"playInfo\":\"SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc\"}]";
            JSONObject s = new JSONObject(RequestManager.requestString(METHOD, params));
            TimeUtil.sleep(RandomUtil.nextInt(2000, 3000));
            if (s.optBoolean("success")) {
                JSONObject extInfo = s.optJSONObject("extInfo");
                JSONObject intensifyTaskListQueryResult = extInfo.optJSONObject("intensifyTaskListQueryResult");
                //大额元气任务
                JSONArray feedsTaskInfoList = intensifyTaskListQueryResult.optJSONArray("feedsTaskInfoList");
                for (int i = 0; i < feedsTaskInfoList.length(); i++) {
                    JSONObject task = feedsTaskInfoList.getJSONObject(i);
                    String appletId = task.optString("appletId", "");
                    String taskCenId = task.optString("taskCenId", "");
                    String taskScene = task.optString("taskScene", "");
                    String taskStatus = task.optString("taskStatus", "");

                    // 检查任务是否在黑名单中
                    if (taskBlackList.contains(taskCenId)) {
                        //Log.other(TAG + "任务[" + taskCenId + "]已在黑名单中，跳过执行");
                        continue;
                    }

                    //WATCH_VIDEOS是看视频/短剧
                    if (taskScene.equals("FIND_GOOD_TASTE") || taskStatus.equals("RECEIVE_SUCCESS")) {
                        //跳过下单任务或者已完成任务
                        continue;
                    }

                    //根据任务状态处理任务
                    if (taskStatus.equals("NONE_SIGNUP")) {
                        //领取并完成任务
                        doTask(appletId, taskCenId, true, taskScene);
                    } else if (taskStatus.equals("SIGNUP_COMPLETE") || taskStatus.equals("PARTLY_DONE")) {
                        //直接完成任务
                        doTask(appletId, taskCenId, false, taskScene);
                    }

                    //获取完成次数信息（用于判断视频/短剧任务是否完成）
                    String periodCurrentCompleteNum = task.optString("periodCurrentCompleteNum", "0");
                    String periodTotalCompleteNum = task.optString("periodTotalCompleteNum", "0");

                    //如果是视频任务且未完成足够次数，则继续完成
                    if (taskScene.equals("WATCH_VIDEOS")) {
                        int current = Integer.parseInt(periodCurrentCompleteNum);
                        int total = Integer.parseInt(periodTotalCompleteNum);
                        //如果已完成次数小于总次数，继续完成任务
                        if (current < total) {
                            //根据任务类型确定需要完成的次数
                            for (int j = current; j < total; j++) {
                                //每次完成都需要重新领取
                                if (!doTask(appletId, taskCenId, true, taskScene)) {
                                    // 如果任务执行失败，跳出循环
                                    break;
                                }
                            }
                        }
                    }
                }

                //逛一逛得元气
                JSONArray intensifyTaskInfoList = intensifyTaskListQueryResult.optJSONArray("intensifyTaskInfoList");
                for (int i = 0; i < intensifyTaskInfoList.length(); i++) {
                    JSONObject task = intensifyTaskInfoList.getJSONObject(i);
                    String appletId = task.optString("appletId", "");
                    String taskCenId = task.optString("taskCenId", "");
                    String taskType = task.optString("taskType", "");
                    String taskStatus = task.optString("taskStatus", "");

                    // 检查任务是否在黑名单中
                    if (taskBlackList.contains(taskCenId)) {
                        //Log.other(TAG + "任务[" + taskCenId + "]已在黑名单中，跳过执行");
                        continue;
                    }

                    if (!taskType.equals("BROWSER")) {
                        continue;
                    }

                    //处理浏览任务
                    if (taskStatus.equals("NONE_SIGNUP")) {
                        //领取并完成任务
                        doTask(appletId, taskCenId, true, "BROWSER");
                    } else if (taskStatus.equals("SIGNUP_COMPLETE")) {
                        //直接完成任务
                        doTask(appletId, taskCenId, false, "BROWSER");
                    }
                }
            }
        } catch (JSONException e) {
            Log.other(TAG + "查询任务列表失败: " + e);
        }
    }

    private boolean doTask(String appletId, String taskCenId, boolean isSignUp, String taskScene) {
        try {
            // 检查任务是否在黑名单中
            if (taskBlackList.contains(taskCenId)) {
                //Log.other(TAG + "任务[" + taskCenId + "]已在黑名单中，跳过执行");
                return false;
            }

            //领取任务
            if (isSignUp) {
                //动态构建领取任务参数
                JSONObject signupParam = new JSONObject();
                signupParam.put("appletId", appletId);

                JSONObject extInfo = new JSONObject();
                extInfo.put("ALIPAY_APP_VERSION", "10.7.26");
                signupParam.put("extInfo", extInfo);

                // 动态构建日期
                String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                signupParam.put("outBizNo", currentDate);
                signupParam.put("playInfo", "SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc");
                signupParam.put("source", "promoplaycenter");
                signupParam.put("stageCode", "signup");
                signupParam.put("taskCenId", taskCenId);

                String params = "[" + signupParam.toString() + "]";
                JSONObject s = new JSONObject(RequestManager.requestString("alipay.promoprod.applet.trigger", params));
                if (!s.optBoolean("success")) {
                    Log.error(TAG + "领取任务失败: " + s);
                    // 检查是否是特定错误，如果是则加入黑名单
                    handleTaskError(taskCenId, s);
                    return false;
                }
            }

            // 根据任务类型动态休眠
            if ("WATCH_VIDEOS".equals(taskScene)) {
                // 视频/短剧任务休眠45-60秒
                TimeUtil.sleep(RandomUtil.nextInt(45000, 60000));
            } else {
                // 浏览任务休眠16-20秒
                TimeUtil.sleep(RandomUtil.nextInt(16000, 20000));
            }

            //提交任务 - 动态构建参数
            JSONObject submitParam = new JSONObject();
            submitParam.put("appletId", appletId);
            submitParam.put("operation", "APPLET_TRIGGER");
            submitParam.put("playInfo", "SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc");
            submitParam.put("stageCode", "send");
            submitParam.put("taskCenId", taskCenId);

            String params = "[" + submitParam.toString() + "]";
            JSONObject s = new JSONObject(RequestManager.requestString("alipay.promoprod.applet.trigger", params));
            if (s.optBoolean("success")) {
                JSONObject appletBaseConfigDTO = s.optJSONObject("appletBaseConfigDTO");
                String appletName = appletBaseConfigDTO.optString("appletName", "未知任务");
                Log.other(TAG + "完成[" + appletName + "]");
                return true;
            } else {
                Log.error(TAG + "提交任务失败: " + s);
                // 检查是否是特定错误，如果是则加入黑名单
                handleTaskError(taskCenId, s);
                return false;
            }
        } catch (JSONException e) {
            Log.error(TAG + "doTask 任务失败: " + e);
            return false;
        }
    }

    // 添加错误处理方法
    private void handleTaskError(String taskCenId, JSONObject response) {
        try {
            String errorMsg = response.optString("errorMsg", "");
            String errorCode = response.optString("errorCode", "");

            // 检查是否是需要加入黑名单的错误
            if (errorMsg.contains("任务报名记录不存在或已过期") ||
                    errorMsg.contains("活动中奖账户日维度次数超过限制") ||
                    errorCode.equals("10002901") ||
                    errorCode.equals("10001011")) {

                // 将任务加入黑名单
                taskBlackList.add(taskCenId);
                Log.other(TAG + "任务[" + taskCenId + "]已加入黑名单，原因: " + errorMsg);
            }
        } catch (Exception e) {
            Log.error(TAG + "处理任务错误失败: " + e);
        }
    }

    // 优化检查剩余任务的方法
    private void checkAndCompleteRemainingTasks() {
        boolean hasUncompletedTask = true;
        int maxAttempts = 2; // 最大尝试次数，防止无限循环
        int attempts = 0;

        while (hasUncompletedTask && attempts < maxAttempts) {
            hasUncompletedTask = false;
            attempts++;

            try {
                String params = "[{\"extInfo\":{\"sysTriger\":\"\"},\"operation\":\"INTENSIFY_TASK_LIST_QUERY\",\"playInfo\":\"SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc\"}]";
                JSONObject s = new JSONObject(RequestManager.requestString(METHOD, params));
                if (s.optBoolean("success")) {
                    JSONObject extInfo = s.optJSONObject("extInfo");
                    JSONObject intensifyTaskListQueryResult = extInfo.optJSONObject("intensifyTaskListQueryResult");

                    //只处理逛一逛得元气任务
                    JSONArray intensifyTaskInfoList = intensifyTaskListQueryResult.optJSONArray("intensifyTaskInfoList");

                    for (int i = 0; i < intensifyTaskInfoList.length(); i++) {
                        JSONObject task = intensifyTaskInfoList.getJSONObject(i);
                        String appletId = task.optString("appletId", "");
                        String taskCenId = task.optString("taskCenId", "");
                        String taskType = task.optString("taskType", "");
                        String taskStatus = task.optString("taskStatus", "");

                        // 检查任务是否在黑名单中
                        if (taskBlackList.contains(taskCenId)) {
                            continue;
                        }

                        if (!taskType.equals("BROWSER")) {
                            continue;
                        }

                        //检查是否有未完成的浏览任务
                        if (!taskStatus.equals("RECEIVE_SUCCESS")) {
                            hasUncompletedTask = true;
                            //处理浏览任务
                            if (taskStatus.equals("NONE_SIGNUP")) {
                                //领取并完成任务
                                if (!doTask(appletId, taskCenId, true, "BROWSER")) {
                                    // 如果任务执行失败，继续下一个任务而不是跳出
                                    continue;
                                }
                            } else if (taskStatus.equals("SIGNUP_COMPLETE")) {
                                //直接完成任务
                                if (!doTask(appletId, taskCenId, false, "BROWSER")) {
                                    // 如果任务执行失败，继续下一个任务而不是跳出
                                    continue;
                                }
                            }
                        }
                    }

                    if (!hasUncompletedTask) {
                        Log.other(TAG + "所有逛一逛任务已完成");
                        Status.setFlagToday("HarvestComplete");
                    }
                }
            } catch (JSONException e) {
                Log.error(TAG + "检查剩余任务失败: " + e);
                break; // 出错时退出循环
            }
        }
    }

    //逛一逛刷新任务
    private void refreshTask(){
        try {
            //传入的是appletId
            String params = "[{\"extInfo\":{\"currentIntensifyTaskIdList\":[\"AP11305104\",\"AP12304967\",\"AP15305022\"],\"refreshIntensify\":\"true\",\"sysTriger\":\"\"},\"operation\":\"INTENSIFY_TASK_LIST_QUERY\",\"playInfo\":\"SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc\"}]";
            JSONObject s = new JSONObject(RequestManager.requestString(METHOD, params));
        } catch (JSONException e){
            Log.error(TAG+"刷新任务失败: "+e);
        }
    }

    //单次抓元气奖励
    private void getSingleTask(){
        try {
            String params = "[{\"extInfo\":{\"selectType\":\"FIND_GOOD_PRICE\"},\"operation\":\"VITALITY_FEEDS_QUERY\",\"playInfo\":\"SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc\"}]";
            JSONObject s = new JSONObject(RequestManager.requestString(METHOD, params));
        } catch (JSONException e){
            Log.error(TAG+"单次抓元气奖励失败: "+e);
        }
    }

    //未知实际用途方法
    private void test1(){
        String params = "[{\"operation\":\"VITALITY_HORSE_LAMP_QUERY\",\"playInfo\":\"SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc\"}]";
        RequestManager.requestString(METHOD, params);
    }

    //查询绿植信息
    private void queryPlantInfo(){
        String params = "[{\"extInfo\":{\"assetRefresh\":[\"all\"]},\"operation\":\"VITALITY_HOME_PAGE_RENDER\",\"playInfo\":\"SwbtxJSo8ON%2B9mYMLZzuZ%2F%2Fp2DX6mzrc\"}]";
        RequestManager.requestString(METHOD, params);
    }
}
