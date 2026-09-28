package fansirsqi.xposed.sesame.task.otherTask2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.util.maps.UserMap;

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

    // 按真实抓包顺序模拟用户进入小程序首页的完整浏览序列
    private void initUserInfo() {
        try {
            String uid = UserMap.getCurrentUid();
            // 1. 首页奖学金余额
            RequestManager.requestString("com.alipay.promobffweb.needle.equity.queryAccount", "[null]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 2. 发现页内容（响应中的推荐标签用于后续频道浏览）
            String discovery = RequestManager.requestString("com.alipay.rceducenter.biz.gateway.queryDiscoveryPage", "[{\"pageCode\":\"FIND_PAGE\"}]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 3. 运势盒咨询（进入首页即触发）
            RequestManager.requestString("com.alipay.promobffweb.needle.wiki.invokeGzoneReact",
                    "[{\"jsonArgs\":{\"extInfo\":{\"mode\":\"PURE\"},\"sceneCode\":\"EDUCATION_LUCKYBOX\"},\"methodId\":\"consult\",\"source\":\"FORTUNE\"}]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 4. 首页论坛活动
            RequestManager.requestString("com.alipay.mfinsnsprod.service.facade.api.forumactivity.ForumActivityDetailFacade.queryForumActivityDetailBySceneCode",
                    "[{\"sceneCode\":\"TEACHING_HOME\"}]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 5. 用户券商信息
            RequestManager.requestString("com.alipay.promobffweb.needle.wiki.getSecuUser", "[null]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 6. 推荐频道标签关系
            RequestManager.requestString("com.alipay.rceducenter.biz.gateway.queryLabelRelation",
                    "[{\"labelCode\":\"\",\"labelCodeList\":[],\"outerCode\":\"RECOMMEND\",\"outerType\":\"CHANNEL\",\"params\":{},\"status\":\"ONLINE\",\"topFlag\":\"\",\"uid\":\"" + uid + "\"}]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 7. 百科订阅状态
            RequestManager.requestString("com.alipay.promobffweb.needle.wiki.querySubscribe",
                    "[{\"bizId\":\"MYTJ_GAME_DEFAULT_BIZ\",\"bizType\":\"MYTJ_GAME_DEFAULT_SCENE\",\"tenantId\":\"BAIKE\"}]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 8. 知识活动
            RequestManager.requestString("com.alipay.rceducenter.biz.gateway.queryKnowledgeActivity",
                    "[{\"metaId\":\"1\",\"userId\":\"" + uid + "\"}]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 9. 全部任务列表（首页加载即查询）
            RequestManager.requestString("com.alipay.promobffweb.needle.wiki.queryAllTasks", "[{}]");
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            // 10. 推荐频道详情页：按发现页推荐标签依次浏览
            for (String labelCode : extractLabelCodes(discovery)) {
                RequestManager.requestString("com.alipay.rceducenter.biz.gateway.queryChannelDetailPage",
                        "[{\"channelCode\":\"RECOMMEND\",\"labelCode\":\"" + labelCode + "\",\"pageNo\":1,\"pageSize\":10,\"uid\":\"" + uid + "\"}]");
                TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            }
        } catch (Throwable th) {
            Log.error(TAG + "initUserInfo error: " + th);
        }
    }

    // 从发现页响应的精选专题 tag 中提取推荐标签 code，最多 3 个（对应真实用户点选标签浏览行为）
    private List<String> extractLabelCodes(String discoveryResponse) {
        List<String> codes = new ArrayList<>();
        try {
            if (discoveryResponse == null || discoveryResponse.isEmpty()) return codes;
            JSONObject json = new JSONObject(discoveryResponse);
            JSONObject result = json.optJSONObject("result");
            if (result == null) return codes;
            JSONArray components = result.optJSONArray("componentInstanceDTOS");
            if (components == null) return codes;
            for (int i = 0; i < components.length() && codes.size() < 3; i++) {
                JSONObject config = components.getJSONObject(i).optJSONObject("instanceConfig");
                if (config == null) continue;
                JSONArray details = config.optJSONArray("instanceDetails");
                if (details == null) continue;
                for (int j = 0; j < details.length() && codes.size() < 3; j++) {
                    String tag = details.getJSONObject(j).optString("tag", "");
                    if (tag.isEmpty()) continue;
                    JSONArray tagArr = new JSONArray(tag);
                    for (int k = 0; k < tagArr.length() && codes.size() < 3; k++) {
                        String code = tagArr.getJSONObject(k).optString("code", "");
                        if (!code.isEmpty() && !codes.contains(code)) codes.add(code);
                    }
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return codes;
    }


    private String doTask(String taskId){
        String params = "[{\"appletId\":\"AP16171913\",\"stageCode\":\"send\",\"taskId\":\""+taskId+"\"}]";
        return RequestManager.requestString("com.alipay.promobffweb.needle.equity.triggerTask",params);
    }
    //查询单个任务（进入程序后才能查询）
    private String queryTask(){
        String params = "[{\"jsonArgs\":{\"extInfo\":{\"mode\":\"PURE\"},\"sceneCode\":\"EDUCATION_LUCKYBOX\"},\"methodId\":\"trigger\",\"source\":\"FORTUNE\"}]";
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
