package fansirsqi.xposed.sesame.task.otherTask2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

/**
 * 蚂蚁投资者教育基地--奖学金
 */
public class Scholarship extends BaseCommTask {

    private static final String TAG = "奖学金 💵";
    @Override
    protected void handle() {
        try {
            if (!Status.hasFlagToday("ScholarshipTask")) {
                //queryAvatar();
                initUserInfo();
                TimeUtil.sleep(RandomUtil.nextInt(13000, 15000));
                doAllTask();
                TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
                for (int i = 0; i < 3; i++) {
                    processTask();
                    TimeUtil.sleep(RandomUtil.nextInt(5000, 7000));
                }
                queryUserInfo();
            }
        } catch (Exception e) {
            Log.error(TAG+"❌handle--异常:" + e);
        }
    }

    private void doAllTask() {
        String s = queryAllTask();
        int doTaskCount = 0;
        if (s!=null && !s.isEmpty()){
            try {
               JSONObject json = new JSONObject(s);
               if (json.optBoolean("success")){
                   JSONArray taskData = json.optJSONArray("taskData");
                   for (int i = 0; i < taskData.length(); i++){
                       JSONObject task = taskData.getJSONObject(i);
                       String taskProcessStatus = task.optString("taskProcessStatus");
                       if ("NOT_DONE".equalsIgnoreCase(taskProcessStatus)){
                           String taskId = task.optString("taskId");
                           String title = task.optString("title");
                           String count = task.optString("count");
                           String result = doTask(taskId);
                           JSONObject json2 = new JSONObject(result);
                           if (json2.optBoolean("success")) {
                               Log.other(TAG + "完成[" + title + "]✅获得["+count+"]奖学金");
                           } else {
                               Log.other(TAG + "完成[" + title + "]❌");
                           }
                           doTaskCount++;
                           Thread.sleep(RandomUtil.nextInt(5000, 8000));
                       }
                       if (doTaskCount >= 7){
                           Status.setFlagToday("ScholarshipTask");
                           return;
                       }
                   }
               }
            }catch (Exception e){
                Log.other(TAG + "完成全部任务失败：" + e.getMessage());
            }
        }
    }

    private void processTask() {
        //初始化
        initUserInfo();
        TimeUtil.sleep(RandomUtil.nextInt(5000, 7000));
        //查询任务
        String result = queryTask();
        TimeUtil.sleep(RandomUtil.nextInt(5000, 7000));
        if (result == null || result.isEmpty()) {
            Log.other(TAG+"查询任务为空");
            return;
        }

        try {
            JSONObject res = new JSONObject(result);
            if (res == null || !res.optBoolean("success")) {
                Log.other(TAG + "接口返回失败：" + res.optString("message", "未知错误"));
                return;
            }

            // 安全获取 data 对象
            JSONObject data = res.optJSONObject("data");
            if (data == null) {
                Log.other(TAG + "data 为 null，无法继续执行,响应:"+res);
                return;
            }
            // 安全获取 userInfo 对象
            JSONObject userInfo = data.optJSONObject("userInfo");
            if (userInfo!=null){
                String status = userInfo.optString("status","");
                if (!status.isEmpty() && "FREE".equals(status)){

                }else {
                    Log.other(TAG + "进入未打开小程序并进入或者今日已经完成");
                    //Status.setFlagToday("ScholarshipTask");
                    return;
                }
            }
            // 安全获取 prizeInfo 对象
            JSONObject prizeInfo = data.optJSONObject("prizeInfo");
            if (prizeInfo == null) {
                Log.other(TAG + "prizeInfo 为 null，无任务可执行");
                return;
            }

            // 安全获取 drawResult 对象
            JSONObject drawResult = prizeInfo.optJSONObject("drawResult");
            if (drawResult == null) {
                Log.other(TAG + "drawResult 为 null，无法获取任务信息");
                return;
            }

            // 安全获取 taskMorphoDetail 对象
            JSONObject taskMorphoDetail = drawResult.optJSONObject("taskMorphoDetail");
            if (taskMorphoDetail == null) {
                Log.other(TAG + "taskMorphoDetail 为 null，无任务详情");
                return;
            }

            // 安全获取 taskId 和 title
            String taskId = taskMorphoDetail.optString("taskId");
            String title = taskMorphoDetail.optString("title");
            String count = taskMorphoDetail.optString("count");

            if (taskId == null || taskId.isEmpty()) {
                Log.other(TAG + "❌ 无 taskId");
                return;
            }

            String s = doTask(taskId);
            JSONObject s2 = new JSONObject(s);
            if (s2.optBoolean("success")) {
                Log.other(TAG + "完成[" + title + "]✅获得["+count+"]奖学金");
            } else {
                Log.other(TAG + "完成[" + title + "]❌");
            }
        } catch (JSONException e) {
            Log.printStackTrace(TAG, e);
        }
    }

    private void initUserInfo() {
        //
        String m1 = "com.alipay.promobffweb.needle.wiki.getSecuUser";
        String r1 = RequestManager.requestString(m1, "[null]");
        TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
        //2
        String m2 = "com.alipay.promobffweb.needle.wiki.invokeGzoneReact";
        String s2 = RequestManager.requestString(m2, "[{\"jsonArgs\":{\"extInfo\":{\"mode\":\"PURE\"},\"sceneCode\":\"EDUCATION_LUCKYBOX\"},\"methodId\":\"consult\",\"source\":\"FORTUNE\"}]");
        TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
        //3
        String m3 = "com.alipay.rceducenter.biz.gateway.fetchChannelEduContent";
        String s3 = RequestManager.requestString(m3, "[{\"channelCode\":\"RECOMMEND\",\"pageNo\":1,\"pageSize\":10,\"params\":{\"needKnowledgeData\":\"true\",\"needUserData\":\"true\"},\"uid\":\"\"}]");
        TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
        //4
        String m4 = "com.alipay.rceducenter.biz.gateway.queryChannelDetail";
        String s4 = RequestManager.requestString(m4, "[{\"channelCode\":\"RECOMMEND\",\"params\":{\"needKnowledgeData\":\"true\"},\"uid\":\"\"}]");
        TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
        //5
        String m5 = "com.alipay.promobffweb.needle.wiki.queryPendantList";
        String s5 = RequestManager.requestString(m5, "[{}]");
        TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
        //queryAvatar();
    }


    private String doTask(String taskId){
        String params = "[{\"appletId\":\"AP16171913\",\"stageCode\":\"send\",\"taskId\":\""+taskId+"\"}]";
        return RequestManager.requestString("com.alipay.promobffweb.needle.equity.triggerTask",params);
    }
    //查询单个任务（进入程序后才能查询）
    private String queryTask(){
        String params = "[{\"jsonArgs\":{\"extInfo\":{\"mode\":\"PURE\",\"source\":\"\"},\"sceneCode\":\"EDUCATION_LUCKYBOX\"},\"methodId\":\"trigger\",\"source\":\"FORTUNE\"}]";
        return RequestManager.requestString("com.alipay.promobffweb.needle.wiki.invokeGzoneReact",params);
    }
    //查询用户信息
    private void queryUserInfo(){
        String method  = "com.alipay.promobffweb.needle.equity.queryAccount";
        String data = "[{\"param\":{\"TEMPLATE_VERSION\":\"WALLET\"}}]";
        String s = RequestManager.requestString(method, data);
        try{
            JSONObject json = new JSONObject(s);
            if (json.optBoolean("success")){
                String availableAmount = json.optString("availableAmount");
                Status.setFlagToday("ScholarshipTask");
                Log.other(TAG+"用户奖学金余额:"+availableAmount+"奖学金");
            }
        }catch (JSONException e){
            Log.error(displayName+"查询用户信息错误json error");
        } catch (Exception e) {
            Log.error(displayName+"查询用户信息错误 error");
        }
    }
    //查询形像信息
    private void queryAvatar(){
        String method = "com.alipay.openapi.jsapi.standard.invoke";
        String data = "[{\"appId\":\"2021001187659055\",\"bizContent\":{\"extInfo\":\"{\\\"caller\\\":\\\"sdk\\\",\\\"avatarAppVersion\\\":\\\"1.0.0\\\",\\\"deviceLevel\\\":\\\"high\\\"}\",\"node\":\"indexRender\",\"scene\":\"investmentEducation\"},\"method\":\"queryAvatarData\"}]";
        String s = RequestManager.requestString(method, data);
    }
    //查询全部任务
    private String queryAllTask(){
        String method = "com.alipay.promobffweb.needle.wiki.queryAllTasks";
        return RequestManager.requestString(method,"[{}]");
    }
}
