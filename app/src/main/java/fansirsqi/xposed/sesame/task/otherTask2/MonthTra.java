package fansirsqi.xposed.sesame.task.otherTask2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask;
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class MonthTra extends BaseCommTask {
    private String displayName = "月月赚 💰";
    //存储已经完成的任务
    private static final Map<String, Boolean> completedTasksMap = new HashMap<>();
    @Override
    protected void handle() {
        try {
            String res = queryTaskList();
            if (res == null || res.isEmpty()){
                Log.other(displayName+"❌dubug--月月赚查询任务列表为空");
                return;
            }
            doTask(res);
        } catch (Exception e) {
            Log.error(displayName+"❌dubug--月月赚异常:" + e);
        }
    }


    //完成任务
    private void doTask(String res) {
        boolean allTasksCompleted = true;
        try {
            JSONObject r0 = new JSONObject(res);
            if (!r0.optBoolean("success")) {
                Log.error(displayName+"❌dubug--"+r0);
                return;
            }
            JSONArray taskDetailList = r0.getJSONArray("taskDetailList");
//            Log.other(displayName + "任务数量：" + taskDetailList.length()); //打印任务数量

            //检查任务状态
            for (int i2 = 0; i2 < taskDetailList.length(); i2++){
                JSONObject task = taskDetailList.getJSONObject(i2);
                String taskProcessStatus = task.optString("taskProcessStatus", null);//任务状态

                JSONObject taskBaseInfo = task.getJSONObject("taskBaseInfo");
                String appletType = taskBaseInfo.optString("appletType", null);
                if ("BROWSER".equals(appletType)){
                    if("NOT_DONE".equals(taskProcessStatus)||"NOT_SEND".equals(taskProcessStatus)){
//                        Log.other(displayName+"还有任务没有完成");
                        allTasksCompleted = false;
                        break;
                    }
                }
            }

            // 如果所有任务已完成，设置任务状态为完成今日不再执行
            if (allTasksCompleted) {
                Status.setFlagToday(CompletedKeyEnum.MonthTask.name());
                Log.other(displayName + "所有任务已完成🏆");
                return;
            }

            for (int i = 0; i < taskDetailList.length(); i++) {
                JSONObject task = taskDetailList.getJSONObject(i);

                String appletId = task.optString("taskId", null);
                String outbizno = appletId+TimeUtil.getFormattedDate("yyyyMMdd");
                String taskProcessStatus = task.optString("taskProcessStatus", null);//任务状态
                JSONObject taskBaseInfo = task.getJSONObject("taskBaseInfo");
                String appletName = taskBaseInfo.optString("appletName", null);
                String appletType = taskBaseInfo.optString("appletType", null);
                // 如果任一字段为空，跳过当前任务
                // 检查是否已处理过该任务
                if (completedTasksMap.containsKey(appletId)) {
                    continue;
                }
                if (!"BROWSER".equals(appletType)){
                    continue;
                }
                if (appletId == null || appletId.isEmpty()) {
                    Log.other(displayName+"❌dubug--doTask任务字段为空");
                    continue;
                }

                if ("NONE_SIGNUP".equals(taskProcessStatus)){
                    String res1 = taskTrigger(appletId, outbizno, "signup");
                }
                // 构造参数并提交请求
                String res2 = taskTrigger(appletId, outbizno, "send");
                // 处理响应结果
                if (res2 == null || res2.isEmpty()) {
                    Log.other(displayName+"["+appletName+"]❌dubug--月月赚请求异常");
                    continue;
                }

                JSONObject json = new JSONObject(res2);
                if (!json.getBoolean("success")) {
                    continue;
                }

                JSONArray prizeSendArray = json.getJSONArray("prizeSendInfo");
                if (prizeSendArray.length() == 0) {
                    Log.other(displayName + "❌ prizeSendInfo 数组为空");
                    continue;
                }

                JSONObject prizeSendInfo = prizeSendArray.getJSONObject(0); // 取第一个元素
                JSONObject extInfo = prizeSendInfo.getJSONObject("extInfo");
                String taskDesc = extInfo.optString("taskDesc");

                JSONObject price = prizeSendInfo.optJSONObject("price");
                double amount = price != null ? price.optDouble("amount") : 0.01;

                Log.other(displayName + "✅完成[" + taskDesc + "]+" + amount+"🧧");
                // 标记该任务为已完成
                completedTasksMap.put(appletId, true);
                TimeUtil.sleep(RandomUtil.nextInt(1000, 3000));
            }

        } catch (JSONException e) {
            Log.error(displayName+"❌dubug--月月赚JSON解析异常:" + e);
        }
    }

    //报名任务
    private String taskTrigger(String appletId, String outbizno, String stageCode) {
        try {
            String methos = "alipay.promoprod.applet.trigger";
            String params = "[{\"appletId\":\"" + appletId + "\",\"chInfo\":\"ch_appcollect__chsub_my-myFavorite\"," +
                    "\"outbizno\":\"" + outbizno + "\",\"stageCode\":\"" + stageCode + "\"," +
                    "\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iOMNZsn4dkXh\"}]";
            String res = RequestManager.requestString(methos, params);
            return res;
        } catch (Exception e) {
            Log.error(displayName+"❌dubug--月月赚报名任务异常:" + e);
            return null;
        }
    }

    //查询任务列表
    private String queryTaskList() {
        String methos = "alipay.promoprod.task.listQuery";
        String params = "[{\"chInfo\":\"ch_appcollect__chsub_my-myFavorite\",\"consultAccessFlag\":true,\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iOMNZsn4dkXh\"}]";
        return RequestManager.requestString(methos, params);
    }
}
