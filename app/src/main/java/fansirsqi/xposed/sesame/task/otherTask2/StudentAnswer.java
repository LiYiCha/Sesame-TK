package fansirsqi.xposed.sesame.task.otherTask2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.DataStore;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StudentAnswer {
    private static final String TAG = "答题📝";
    private static final String sceneCode = "WINTER_2025";
    private static String taskAppletId = "AP11323645"; //活动id

    // 任务失败计数器和黑名单机制
    private static final Set<String> taskBlackList = ConcurrentHashMap.newKeySet();
    private static final int MAX_FAILURE_COUNT = 2; // 最大失败次数阈值

    public void handle(){
        //查询学生答题活动详情
        queryActivity();
        //查询任务列表
        queryTaskList();
    }

    private void queryActivity(){
        String method = "alipay.mobileopl.activity.stu.winter2025.queryHomePage";
        // 修正参数中的常量名
        String params = "[{\"currentAppMode\":\"student\",\"sceneCode\":\""+sceneCode+"\"}]";
        try {
            JSONObject json = new JSONObject(RequestManager.requestString(method, params));
            if (json.optBoolean("success")){
                taskAppletId = json.optString("taskAppletId",taskAppletId);
            }
        } catch (JSONException e) {
            Log.error(TAG + "查询学生答题活动详情异常: " + e);
        }
    }

    private void queryTaskList() {
        Set<String> blackCache = DataStore.INSTANCE.get("blacklistedTasks_stuAnswer", Set.class);
        if(blackCache==null){
            blackCache =taskBlackList;
        }
        String method = "alipay.mobileopl.stu.common.queryTaskList";
        // 修正参数中的常量名
        String params = "[{\"bizCode\":\""+sceneCode+"\",\"taskAppletId\":\""+taskAppletId+"\"}]";
        try {
            JSONObject json = new JSONObject(RequestManager.requestString(method, params));
            if (json.optBoolean("success")){
                JSONArray promokernelTaskInfoList = json.optJSONArray("promokernelTaskInfoList");
                if (promokernelTaskInfoList != null) {
                    for (int i = 0; i < promokernelTaskInfoList.length(); i++){
                        JSONObject tasks = promokernelTaskInfoList.getJSONObject(i);
                        String taskId = tasks.getString("taskId");
                        String taskName = tasks.getString("taskName");
                        String taskCompleteReward = tasks.getString("taskCompleteReward");
                        String taskProcessType = tasks.getString("taskProcessType");

                        // 检查任务是否在黑名单中
                        if (taskBlackList.contains(taskId) || blackCache.contains(taskId)) {
                            //Log.runtime(TAG + "任务[" + taskName + "]在黑名单中，跳过执行");
                            continue;
                        }

                        if (!"FINISH".equals(taskProcessType)){
                            if (!doTask("signup",taskId)) {
                                Log.error(TAG+"任务[" + taskName+"]报名失败");
                            }
                            TimeUtil.sleep(RandomUtil.nextInt(15000,17000));
                            boolean result = doTask("send", taskId);
                            if (result){
                                Log.runtime(TAG+"完成[" + taskName+"]"+taskCompleteReward+"卡");
                                // 任务成功执行，从黑名单中移除（如果存在）
                                if (taskBlackList.contains(taskId)) {
                                    taskBlackList.remove(taskId);
                                    Log.runtime(TAG + "任务[" + taskName + "]执行成功，从黑名单中移除");
                                }
                            }else {
                                Log.error(TAG+"任务[" + taskName+"]执行失败");
                                handleTaskFailure(taskId);
                            }
                        }
                    }
                }
            }else {
                Log.error(TAG + "查询任务列表失败: " + json);
            }
        }catch (JSONException e){
            Log.error(TAG + "查询任务列表异常: " + e);
        }
    }

    private boolean doTask(String stageCode,String taskId){
        String method = "alipay.mobileopl.stu.common.taskSignOrTrigger";
        String params = "[{\"bizCode\":\""+sceneCode+"\",\"stageCode\":\""+stageCode+"\"," +
                "\"taskAppletId\":\""+taskAppletId+"\",\"taskId\":\""+taskId+"\"}]";
        try {
            JSONObject json = new JSONObject(RequestManager.requestString(method, params));
            if (!json.optBoolean("success")){
                return false;
            }
        } catch (JSONException e) {
            Log.error(TAG + "任务异常: " + e);
            return false;
        }
        return true;
    }

    /**
     * 处理任务失败情况，增加失败计数
     */
    private void handleTaskFailure(String taskId) {
        // 将任务加入黑名单
        taskBlackList.add(taskId);
        DataStore.INSTANCE.put("blacklistedTasks_stuAnswer", taskBlackList);
        Log.runtime(TAG + "任务[" + taskId + "]已加入黑名单");
    }
}
