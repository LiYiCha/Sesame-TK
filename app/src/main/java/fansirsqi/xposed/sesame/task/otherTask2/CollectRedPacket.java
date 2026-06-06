package fansirsqi.xposed.sesame.task.otherTask2;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.util.RandomUtil;

/**
 * 集红包任务处理器
 * 处理支付宝集红包相关任务的领取和完成
 */
public class CollectRedPacket {
    private static final String TAG = "集红包";
    private static String ACTIVITY_ID = "0901panghu001"; //活动ID

    // 任务错误计数器，用于黑名单管理
    private static final Map<String, Integer> taskErrorCount = new HashMap<>();

    // 任务黑名单
    private static final Map<String, Boolean> taskBlacklist = new HashMap<>();
    // 验证状态标志

    public void handle() {
        try {
            long hour = TimeUtil.getHourOfDay();
            if (hour < 7 ) {
                return;
            }
            // 如果验证失败则不继续执行
            if (!queryActivityDetail()) {
                return;
            }
            queryActivity();
            queryTaskList();
            triggerHbTemplate();
        } catch (Exception e) {
            Log.error(TAG + "处理集红包任务异常: " + e.getMessage());
        }
    }


    /**
     * 查询集红包活动详情
     */
    private boolean queryActivityDetail(){
        try {
            String method = "alipay.giftinocenter.gift.userTemplateActivity.queryActivityDetail";
            String params = "[{\"activityId\":\""+ACTIVITY_ID+"\",\"chInfo\":\"fm_DZFWpush\"}]";
            JSONObject json = new JSONObject(RequestManager.requestString(method, params));
            // 检查是否需要用户验证
            if (!json.optBoolean("success")) {
                String errorMessage = json.optString("errorMessage", "");
                if (errorMessage.contains("为了保障您的操作安全，请进行验证后继续")) {
                    Notify.sendNewNotification("集红包", "为了保障您的操作安全，请进行验证后继续(5分钟后重试)");
                    // 等待5分钟再尝试一次
                    TimeUtil.sleep(5 * 60 * 1000);
                    // 重新请求
                    json = new JSONObject(RequestManager.requestString(method, params));
                    if (!json.optBoolean("success")) {
                        Log.runtime(TAG + "验证后重试仍失败，退出执行");
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.error(TAG + "查询集红包活动详情异常: " + e.getMessage());
            return false;
        }
    }



    /**
     * 查询任务列表
     */
    private void queryTaskList() {
        try {
            String method = "alipay.promoprod.task.listQuery";
            String params = "[{\"consultAccessFlag\":true,\"extInfo\":{\"activityId\":\""+ACTIVITY_ID+"\"},\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iD2rxYzHult2\"}]";
            JSONObject json = new JSONObject(RequestManager.requestString(method, params));
            if (json.optBoolean("success")){
                JSONArray taskDetailList = json.optJSONArray("taskDetailList");
                for (int i = 0; i < taskDetailList.length(); i++) {
                    JSONObject task = taskDetailList.getJSONObject(i);
                    String taskId = task.optString("taskId");//就是appletId
                    String taskProcessStatus = task.optString("taskProcessStatus");
                    String taskType = task.optString("taskType");
                    boolean needSignUp = task.optBoolean("needSignUp",true);

                    if (taskType.equals("CALL_APP_XLIGHT")||taskType.equals("TINY_GAME_VIEW_XLIGHT")){
                        continue;
                    }
                    if (taskProcessStatus.equals("NOT_DONE")){
                        doTask(taskId,needSignUp);
                    }
                }
            }
        } catch (Exception e) {
            Log.error(TAG + "查询集红包任务列表异常: " + e.getMessage());
        }
    }



    /**
     * 完成任务
     */
    private void doTask(String appletId, boolean needSignUp){
        // 检查任务是否在黑名单中
        if (taskBlacklist.getOrDefault(appletId, false)) {
            //Log.other(TAG + "任务[" + appletId + "]已在黑名单中，跳过执行");
            return;
        }

        try {
            String method = "alipay.promoprod.applet.trigger";
            if(needSignUp) {
                String outBizNo1 = appletId + System.currentTimeMillis();
                //领取任务
                String params = "[{\"appletId\":\""+appletId+"\",\"chinfo\":\"fm_PHHBSYkv\",\"outBizNo\":\""+outBizNo1+"\"," +
                        "\"source\":\"giftinocenter\",\"stageCode\":\"signup\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iD2rxYzHult2\"}]";
                JSONObject s1 = new JSONObject(RequestManager.requestString(method, params));
                if (!s1.optBoolean("success")){
                    Log.error(TAG+"领取任务失败: "+s1);
                    // 增加错误计数
                    incrementTaskErrorCount(appletId);
                    return;
                }
            }
            TimeUtil.sleep(RandomUtil.nextInt(16000,25000));
            String outBizNo2 = appletId + System.currentTimeMillis();
            //提交任务
            String params2 = "[{\"appletId\":\""+appletId+"\",\"chinfo\":\"fm_PHHBSYkv\",\"outBizNo\":\""+outBizNo2+"\"," +
                    "\"stageCode\":\"send\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iD2rxYzHult2\"}]";
            JSONObject s2 = new JSONObject(RequestManager.requestString(method, params2));
            if (s2.optBoolean("success")){
                JSONArray prizeSendInfo = s2.optJSONArray("prizeSendInfo");
                JSONObject data = prizeSendInfo.getJSONObject(0);
                JSONObject extInfo = data.optJSONObject("extInfo");
                String taskMainTitle = extInfo.optString("taskMainTitle", "未知任务");
                Log.other(TAG+"✅完成["+taskMainTitle+"]");
                // 任务成功完成，清除错误计数
                taskErrorCount.remove(appletId);
            } else {
                Log.error(TAG+"提交任务失败: "+s2);
                // 增加错误计数
                incrementTaskErrorCount(appletId);
            }
            TimeUtil.sleep(RandomUtil.nextInt(1600,2500));
        }catch (Exception e){
            Log.error(TAG + "处理集红包任务异常: " + e);
            // 增加错误计数
            incrementTaskErrorCount(appletId);
        }
    }

    /**
     * 增加任务错误计数并处理黑名单逻辑
     * @param appletId 任务ID
     */
    private void incrementTaskErrorCount(String appletId) {
        int errorCount = taskErrorCount.getOrDefault(appletId, 0) + 1;
        taskErrorCount.put(appletId, errorCount);

        // 如果错误次数达到2次，加入黑名单
        if (errorCount >= 2) {
            taskBlacklist.put(appletId, true);
            Log.runtime(TAG + "任务[" + appletId + "]已加入黑名单");
        }
    }


    /**
     * 抽奖
     */
    private void triggerHbTemplate(){
        try{
            String methodQuery = "alipay.promoprod.cert.query";
            String paramsQuery = "[{\"campInfoList\":[\"ybjsS1zkl74qzITzjxHI%2FTmmvQqlvad5\"]}]";
            JSONObject json = new JSONObject(RequestManager.requestString(methodQuery, paramsQuery));
            if (json.optBoolean("success")){
                JSONArray certDetailList = json.optJSONArray("certDetailList");
                JSONObject data = certDetailList.optJSONObject(0);
                int remainUseCount = data.optInt("remainUseCount");
                while(remainUseCount > 0){
                    String methodDo = "alipay.giftinocenter.gift.userTemplateActivity.triggerHbTemplate";
                    String paramsDp = "[{\"activityId\":\""+ACTIVITY_ID+"\",\"outBizNo\":\"4c94e799-d31e-97aa-68cd-a318bc171d9c\"}]";
                    JSONObject json2 = new JSONObject(RequestManager.requestString(methodDo, paramsDp));
                    if (json2.optBoolean("success")){
                        JSONObject data2 = json2.optJSONObject("data");
                        JSONArray prizeSendInfo = data2.optJSONArray("prizeSendInfo");
                        JSONObject data3 = prizeSendInfo.getJSONObject(0);
                        String prizeName = data3.optString("prizeName");
                        Log.other(TAG+"✅抽奖成功["+prizeName+"]");
                        remainUseCount--;
                    }else{
                        Log.error(TAG+"❌抽奖失败:"+json2);
                        break;
                    }
                    TimeUtil.sleep(RandomUtil.nextInt(3000,5500));
                }
            }

        }catch (Exception e){
            Log.error(TAG + "抽奖异常: " + e);
        }
    }

    /**
     * 查询集红包活动
     */
    private void queryActivity() {
        String parmas = "[{\"preBiz\":\"index\",\"prodCode\":\"CROWD_COMMON_CASH\",\"subPreBiz\":\"APP\"}]";
        String response = RequestManager.requestString("alipay.giftinocenter.gift.userTemplate.new.query", parmas);
        try {
            JSONObject jsonObject = new JSONObject(response);
            if (!jsonObject.optBoolean("success", false)) {
                JSONObject userTemplateVO = jsonObject.optJSONObject("userTemplateVO");
                JSONObject allExt = userTemplateVO.optJSONObject("allExt");
                String activityId = allExt.optString("activityId");
                if (!activityId.isEmpty()){
                    ACTIVITY_ID = activityId;
                }
            }
        } catch (Exception e) {
            Log.error(TAG + "查询集红包活动异常: " + e.getMessage());
        }
    }


}
