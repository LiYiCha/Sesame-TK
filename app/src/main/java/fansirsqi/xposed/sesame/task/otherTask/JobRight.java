package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class JobRight extends BaseCommTask{
    private static String method = "alipay.imasp.program.programInvoke";
    private String displayName = "就业|积分💼";
    
    // 静态锁对象，确保所有JobRight实例的handle方法同步执行
    private static final Object HANDLE_LOCK = new Object();
    // 标志位，防止同一实例重复进入handle
    private volatile boolean handling = false;

    private final Set<String> skippedTasks = Collections.synchronizedSet(new HashSet<>());
    @Override
    protected void handle() throws JSONException {
        // 检查是否正在处理中
        if (handling) {
            //Log.other(displayName + "正在执行中，跳过本次调用");
            return;
        }
        
        // 使用静态锁确保同一时间只有一个handle执行
        synchronized (HANDLE_LOCK) {
            if (handling) {
                Log.other(displayName + "正在执行中，跳过本次调用");
                return;
            }
            
            try {
                handling = true;
                doHandle();
            } finally {
                handling = false;
            }
        }
    }
    
    private void doHandle() throws JSONException {
        long hour = TimeUtil.getHourOfDay();
        if (hour < 7 ) {
            return;
        }
        if (!queryIndex()){
            return;
        }
        if(!Status.hasTemporaryStatusValid("JobRightTaskTemp")) {
            String sign = "";
            if (!Status.hasFlagToday(CompletedKeyEnum.JobRightSign.name())) {
                sign = Sign();
            }
            if (sign.equals("false")) {
                return;
            }
            if (!Status.hasFlagToday("JobRightTask")) {
                doTask();
            }
        }
        
        // 任务完成后尝试兑换
        doExchangeIfPossible();
    }

    private String queryPoints() {
        try {
            String s = RequestManager.requestString("com.alipay.govbizweb.biz.payslip.marketing.rpc.index", "[null]");
            JSONObject json = new JSONObject(s);
            if (json.optBoolean("success")) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    return data.optString("availableCount", "");
                }
            }
        } catch (Exception e) {
            Log.error(displayName + "查询积分异常");
        }
        return null;
    }
    
    private boolean queryIndex() {
        String points = queryPoints();
        //availableCount = points;
        return points != null;
    }

    private boolean doExchange(String campId, String prizeId){
        try{
            TimeUtil.sleep(RandomUtil.nextInt(5000, 10000));
            String method = "com.alipay.govbizweb.biz.payslip.marketing.rpc.doExchange";
            String params = "[{\"campId\":\"" + campId + "\",\"prizeId\":\"" + prizeId + "\"}]";
            
            String response = RequestManager.requestString(method, params);
            if (response == null || response.isEmpty()) {
                Log.error(displayName + "兑换红包失败: 返回值为空");
                return false;
            }
            
            try{
                JSONObject json = new JSONObject(response);
                if(json.optBoolean("success")){
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        String exchangeResult = data.optString("exchangeResult", "");
                        if ("SUCCESS".equals(exchangeResult)) {
                            //Log.other(displayName + "兑换成功: campId=" + campId + ", prizeId=" + prizeId);
                            return true;
                        } else {
                            Log.error(displayName + "兑换失败: " + data);
                            return false;
                        }
                    }
                }
                Log.error(displayName + "兑换失败: " + json);
                return false;
            } catch (JSONException e){
                Log.error(displayName + "解析兑换结果失败: " + e.getMessage());
                return false;
            }
        }catch (Exception e){
            Log.error(displayName+"兑换红包出错"+e);
            return false;
        }
    }

    private JSONArray queryExchange(){
        try{
            TimeUtil.sleep(RandomUtil.nextInt(3000, 6000));
            String response = RequestManager.requestString("com.alipay.govbizweb.biz.payslip.marketing.rpc.queryExchange","[null]");
            if (response == null || response.isEmpty()) {
                Log.error(displayName + "查询兑换列表失败: 返回值为空");
                return null;
            }
            
            JSONObject json = new JSONObject(response);
            if(json.optBoolean("success")){
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    return data.optJSONArray("exchangeList");
                }
            }
            return null;
        } catch (Exception e){
            Log.error(displayName+"查询兑换列表异常"+e);
            return null;
        }
    }

    private void doTask() {
        // 查询任务列表
        String taskResult = query();
        if (taskResult == null || taskResult.isEmpty()) {
            Log.error(displayName + "查询任务失败: 返回值为空");
            return;
        }
        TimeUtil.sleep(RandomUtil.nextInt(5000, 7000));

        try {
            // 解析 JSON 响应
            JSONObject jsonObject = new JSONObject(taskResult);

            // 获取任务列表
            JSONArray playTaskOrderInfoList = (JSONArray) JsonUtil.getValueByPathObject(jsonObject,
                    "components.independent_component_task_reward_01961455_independent_component_task_reward_query.content.playTaskOrderInfoList");

            if (playTaskOrderInfoList == null || playTaskOrderInfoList.length() == 0) {
                Log.other(displayName + "未获取到任务列表");
                return;
            }
            boolean allTasksFinished = true;
            for (int i = 0; i < playTaskOrderInfoList.length(); i++) {
                JSONObject task = playTaskOrderInfoList.getJSONObject(i);
                String taskStatus = task.optString("taskStatus", "");
                if (!taskStatus.equals("finish")) {
                    allTasksFinished = false;
                    break;
                }
            }
            if (allTasksFinished) {
                Status.setFlagToday("JobRightTask");
            }

            // 遍历任务列表
            int retryCount = 0;
            for (int i = 0; i < playTaskOrderInfoList.length(); i++) {
                JSONObject task = playTaskOrderInfoList.getJSONObject(i);

                // 提取任务 code 和状态
                String taskCode = task.optString("code", "");
                String taskStatus = task.optString("taskStatus", "");
                JSONObject extInfo = task.optJSONObject("extInfo");
                String activityName;
                if (extInfo != null) {
                    // 提取 activityName
                    activityName = extInfo.optString("activityName", "未知任务");
                } else {
                    activityName = "未知任务名";
                }

                if (skippedTasks.contains(taskCode)){
                    continue;
                }
                // 只处理 taskStatus 为 "init" 的任务
                if (!"finish".equals(taskStatus)) {
                    TimeUtil.sleep(RandomUtil.nextInt(20000,32000));
                    // 调用 subTask 完成任务
                    String recordNoNew = apply(taskCode);
                    if (recordNoNew == null || recordNoNew.isEmpty()) {
                        retryCount++;
                        if (retryCount >= 2){
                            Log.other(displayName + "领取任务失败: 两次机会都不过验证，等五分钟后重试吧!");
                            Status.setTemporaryStatusWithExpiry("JobRightTaskTemp", 1000 * 60 * 5);
                            return;
                        }
                        TimeUtil.sleep(RandomUtil.nextInt(70000, 10000));
                        continue;
                    }
                    //浏览岗位任务
                    if (activityName.equalsIgnoreCase("浏览3个岗位")){
                        browseTask();
                        continue;
                    }
                    //普通任务
                    subTask(taskCode,recordNoNew);
                } else {
                    //Log.runtime(displayName + "跳过任务:" + activityName + ",状态:" + taskStatus);
                }
            }
        } catch (JSONException e) {
            Log.error(displayName + "解析任务列表失败: " + e.getMessage());
        }
    }


    private void subTask(String taskCode,String recordNo) {
        Long outBizNo = System.currentTimeMillis();
        String s = RequestManager.requestString(method,
                "[{\"components\":{\"independent_component_task_reward_01961455_independent_component_task_reward_process\":" +
                        "{\"code\":\""+taskCode+"\",\"outBizNo\":"+outBizNo+",\"recordNo\":\""+recordNo+"\"}}," +
                        "\"operationParamIdentify\":\"independent_component_program2024121902034600\",\"source\":\"job-right-center\"}]");

        if (s == null || s.isEmpty()) {
            Log.error(displayName + "提交任务失败: 返回值为空");
            return;
        }

        try {
            JSONObject json = new JSONObject(s);

            // 检查 isSuccess 字段
            if (!json.optBoolean("isSuccess", false)) {
                Log.error(displayName + "提交任务失败: isSuccess=false");
                skippedTasks.add(taskCode);
                return;
            }

            // 获取 claimedTask 的 displayInfo
            JSONObject content = json.optJSONObject("components")
                    .optJSONObject("independent_component_task_reward_01961455_independent_component_task_reward_process")
                    .optJSONObject("content");

            if (content == null) {
                Log.error(displayName + "未获取到任务内容");
                return;
            }

            JSONObject processedTask = content.optJSONObject("processedTask");
            if (processedTask == null) {
                Log.error(displayName + "未获取到 processedTask");
                Status.setFlagToday("JobRightTask");
                return;
            }

            JSONObject displayInfo = processedTask.optJSONObject("displayInfo");
            if (displayInfo == null) {
                Log.error(displayName + "未获取到 displayInfo");
                return;
            }

            // 提取 activityName 和 activityValue
            String activityName = displayInfo.optString("activityName", "未知任务");
            int activityValue = displayInfo.optInt("activityValue", 0);

            // 打印结果
            Log.other(displayName + "完成[" + activityName + "]工分[+" + activityValue+"]⭐");

        } catch (JSONException e) {
            Log.error(displayName + "解析任务结果失败: " + e.getMessage());
        }
    }

    private String apply(String taskCode) {
        String recordNo = "";
        String data = "[{\"components\":{\"independent_component_task_reward_01961455_independent_component_task_reward_apply\":" +
                "{\"code\":\"" + taskCode + "\"}},\"deviceInfo\":{},\"operationParamIdentify\":\"independent_component_program2024121902034600\",\"source\":\"job-right-center\"}]";

        String s = RequestManager.requestString(method, data);
        if (s == null || s.isEmpty()) {
            Log.error(displayName + "申请任务失败: HTTP响应为空");
            return recordNo;
        }

        try {
            JSONObject json = new JSONObject(s);
            if (!json.optBoolean("isSuccess", false)) {
                Log.error(displayName + "申请任务失败:"+json);
                return recordNo;
            }

            JSONObject components = json.optJSONObject("components");
            if (components == null) {
                Log.error(displayName + "申请任务失败: components字段缺失");
                return recordNo;
            }

            JSONObject applyComponent = components.optJSONObject(
                    "independent_component_task_reward_01961455_independent_component_task_reward_apply");
            if (applyComponent == null) {
                Log.error(displayName + "申请任务失败: 组件字段缺失");
                return recordNo;
            }

            JSONObject content = applyComponent.optJSONObject("content");
            if (content == null) {
                Log.error(displayName + "申请任务失败: content字段缺失");
                return recordNo;
            }

            JSONObject claimedTask = content.optJSONObject("claimedTask");
            if (claimedTask == null) {
                Log.error(displayName + "申请任务失败: claimedTask字段缺失");
                return recordNo;
            }

            // 提取核心字段
            recordNo = claimedTask.optString("recordNo", "");
            if (recordNo.isEmpty()) {
                Log.error(displayName + "申请任务失败: recordNo字段缺失");
                return recordNo;
            }
        } catch (JSONException e) {
            Log.error(displayName + "申请任务失败: JSON解析异常 - " + e.getMessage());
            Log.error(displayName + "原始响应: " + s); // 输出原始响应便于调试
        }
        return recordNo;
    }

    private String query() {
        return RequestManager.requestString(method,
                "[{\"channel\":\"job-right-center\",\"components\":{\"independent_component_task_reward_01961455_independent_component_task_reward_query\":{}},\"deviceInfo\":{},\"operationParamIdentify\":\"independent_component_program2024121902034600\",\"source\":\"job-right-center\"}]");
    }
    
    private final Object exchangeLock = new Object();
    
    private void doExchangeIfPossible() {
        // 线程安全控制
        synchronized (exchangeLock) {
            try {
                // 已兑换过则跳过
                if (Status.hasFlagToday("JobRightExchange")) return;
                
                // 查询最新积分
                String points = queryPoints();
                if (points == null) {
                    Log.error(displayName + "查询积分失败");
                    Status.setFlagToday("JobRightExchange");
                    return;
                }
                
                // 解析当前积分
                int currentPoints;
                try {
                    currentPoints = Integer.parseInt(points);
                } catch (Exception e) {
                    Log.error(displayName + "积分解析错误");
                    Status.setFlagToday("JobRightExchange");
                    return;
                }
                
                // 查询兑换列表
                JSONArray exchangeList = queryExchange();
                if (exchangeList == null || exchangeList.length() == 0) {
                    Log.other(displayName + "无兑换列表");
                    Status.setFlagToday("JobRightExchange");
                    return;
                }
                
                // 查找可兑换的现金红包
                JSONObject bestPrize = null;
                int bestPrice = Integer.MAX_VALUE;
                String bestName = "";
                boolean hasValidPrizes = false;
                
                for (int i = 0; i < exchangeList.length(); i++) {
                    JSONObject prize = exchangeList.getJSONObject(i);
                    String prizeName = prize.optString("prizeName", "");
                    String prizeType = prize.optString("prizeType", "");
                    String unitPrice = prize.optString("unitPrice", "");
                    
                    // 必须是现金红包类型
                    if (!"VCP_CASH_PRIZE".equals(prizeType)) continue;
                    
                    // 解析所需积分
                    int price;
                    try {
                        price = Integer.parseInt(unitPrice);
                    } catch (Exception e) {
                        continue;
                    }
                    
                    // 检查名称是否包含关键词
                    String lowerName = prizeName.toLowerCase();
                    if (!lowerName.contains("现金") && 
                        !lowerName.contains("红包") && 
                        !lowerName.contains("cash")) {
                        continue;
                    }
                    
                    hasValidPrizes = true;
                    
                    // 检查积分是否足够且价格合适
                    if (currentPoints >= price && price < bestPrice) {
                        bestPrize = prize;
                        bestPrice = price;
                        bestName = prizeName;
                    }
                }
                
                // 没有有效奖品或积分不足
                if (!hasValidPrizes || bestPrize == null) {
                    //Log.other(displayName + "积分不足或无现金红包");
                    Status.setFlagToday("JobRightExchange");
                    return;
                }
                
                // 执行兑换
                if (doExchange(bestPrize.optString("campId"), bestPrize.optString("prizeId"))) {
                    Log.other(displayName + "兑换成功: " + bestName + " (" + bestPrice + "分)");
                    Status.setFlagToday("JobRightExchange");
                }
                
            } catch (Exception e) {
                Log.error(displayName + "兑换异常");
                Status.setFlagToday("JobRightExchange"); // 异常时也设置状态
            }
        }
    }
    
    private void browseTask() {
        String queryList = "com.shangshu.govbizwebdeploy.biz.job.jobinfo.rpc.getJobInfoList";
        String params = "[{\"cityCode\":\"450100\",\"filterConfigCode\":\"default\",\"jobListFilter\":{\"jobTypeList\":[]},\"pageNum\":1,\"pageSize\":10,\"tabType\":\"DEFAULT\",\"version\":\"1001\"}]";

        String reportAction = "com.shangshu.govbizwebdeploy.biz.rpc.job.reportAction";
        String trigger = "com.shangshu.govbizwebdeploy.biz.rpc.job.activity.trigger";

        String s1 = RequestManager.requestString(queryList, params);
        if (s1 == null || s1.isEmpty()) {
            Log.other(displayName + "[浏览三个岗位]查询任务失败: 响应为空");
            return;
        }
        try {
            //获取工作id
            JSONObject  json = new JSONObject(s1);
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                Log.other(displayName + "[浏览三个岗位]查询任务失败: data为空");
                return;
            }
            JSONArray pageData = data.optJSONArray("pageData");
            if (pageData == null) {
                Log.other(displayName + "[浏览三个岗位]查询任务失败: pageData为空");
                return;
            }
            for (int i = 0; i < 3; i++){
                JSONObject page = pageData.getJSONObject(i);
                String jobId = page.optString("jobId");
                String params1 = "[{\"actionType\":\"REC_CLICK\",\"cityCode\":110100,\"jobId\":\""+jobId+"\",\"version\":\"1001\"}]";
                //提交浏览行为
                JSONObject s2 = new JSONObject(RequestManager.requestString(reportAction, params1));
                if (s2.optBoolean("success", false)) {
                    //提交点击行为
                    JSONObject s = new JSONObject(RequestManager.requestString(trigger, "[{\"activityType\":\"CLICK_JOB_DETAIL\",\"version\":\"1001\"}]"));
                    if (s.optBoolean("success", false)) {
                        Log.other(displayName + "浏览[" + page.optString("jobName") + "]成功");
                    }
                }else {
                    Log.error(displayName + "提交[浏览三个岗位]失败: " + s2);
                }
                TimeUtil.sleep(5000);
            }
        }catch (JSONException e){
            Log.error(displayName + "[浏览三个岗位]查询任务失败: " + e.getMessage());
        }

    }
    private String Sign() {
        //返回空不用管，返回false表示需要验证
        String code = "";
        String params = "[{\"components\":{\"independent_component_sign_in_01961456_independent_component_sign_in_recall\":{}},\"deviceInfo\":{},\"operationParamIdentify\":\"independent_component_program2024121902034600\",\"source\":\"job-right-center\"}]";
        String s0 = RequestManager.requestString(method,params);
        if (s0 == null || s0.isEmpty()) {
            Log.other(displayName + "查询签到信息失败: 返回值为空");
            return "";
        }

        try {
            // 解析签到模板信息
            JSONObject jsonObject = new JSONObject(s0);
            // 检查是否需要用户验证
            if (!jsonObject.optBoolean("isSuccess")) {
                String errorMessage = jsonObject.optString("errorMessage", "");
                if (errorMessage.contains("为了保障您的操作安全，请进行验证后继续")) {
                    Notify.sendNewNotification("工作积分", "为了保障您的操作安全，请进行验证通过后继续(3分钟后重试)");
                    // 等待3分钟再尝试一次
                    TimeUtil.sleep(180000);
                    // 重新请求
                    jsonObject = new JSONObject(RequestManager.requestString(method, params));
                    if (!jsonObject.optBoolean("success")) {
                        Log.runtime(TAG + "验证后重试仍失败，退出执行");
                        return "false";
                    }
                } else {
                    //其他情况放行
                }
            }

            JSONArray playSignInOrderInfoList = (JSONArray) JsonUtil.getValueByPathObject(jsonObject,
                    "components.independent_component_sign_in_01961456_independent_component_sign_in_recall.content.playSignInOrderInfoList");

            if (playSignInOrderInfoList == null || playSignInOrderInfoList.length() == 0) {
                Log.other(displayName + "未获取到签到模板信息");
                return "";
            }

            // 获取第一个签到模板的 code
            JSONObject firstTemplate = playSignInOrderInfoList.getJSONObject(0);
            JSONObject playSignInTemplateInfo = firstTemplate.optJSONObject("playSignInTemplateInfo");

            if (playSignInTemplateInfo == null) {
                Log.other(displayName + "签到模板信息缺失");
                return "";
            }

            code = playSignInTemplateInfo.optString("code", "");
            if (code.isEmpty()) {
                Log.other(displayName + "签到模板 code 缺失");
                return "";
            }

        } catch (JSONException e) {
            Log.other(displayName + "查询签到信息失败: " + e.getMessage());
            return "";
        }

        // 提交签到请求
        String s = RequestManager.requestString(method,
                "[{\"components\":{\"independent_component_sign_in_01961456_independent_component_sign_in\":" +
                        "{\"code\":\"" + code + "\"}},\"deviceInfo\":{},\"operationParamIdentify\":" +
                        "\"independent_component_program2024121902034600\",\"source\":\"job-right-center\"}]");

        if (s == null || s.isEmpty()) {
            Log.other(displayName + "签到提交失败: 返回值为空");
            return "";
        }

        try {
            JSONObject json = new JSONObject(s);

            // 检查签到是否成功
            if (!json.optBoolean("isSuccess", false)) {
                Log.other(displayName + "签到提交失败: isSuccess=false");
                return "";
            }

            // 获取签到结果信息
            JSONObject playSignInResultInfo = (JSONObject) JsonUtil.getValueByPathObject(json,
                    "components.independent_component_sign_in_01961456_independent_component_sign_in.content.playSignInResultInfo");

            if (playSignInResultInfo == null) {
                Log.other(displayName + "签到结果信息缺失");
                Status.setFlagToday(CompletedKeyEnum.JobRightSign.name());
                return "";
            }

            // 获取签到周期实例信息
            JSONObject playSignInCycleInstanceInfo = playSignInResultInfo.optJSONObject("playSignInCycleInstanceInfo");
            if (playSignInCycleInstanceInfo == null) {
                Log.other(displayName + "签到周期实例信息缺失");
                return "";
            }

            // 获取签到统计信息
            String signCount = playSignInCycleInstanceInfo.optString("accumulativeSignInCount", "0");
            String continuousSignCount = playSignInCycleInstanceInfo.optString("continuousSignInCount", "0");

            // 获取签到周期日期
            String cycleStartDate = playSignInCycleInstanceInfo.optString("cycleStartDate", "未知");
            String cycleEndDate = playSignInCycleInstanceInfo.optString("cycleEndDate", "未知");

            // 打印签到结果
            Log.other(displayName + "签到成功: 累计签到[" + signCount +
                    "]连续签到[" + continuousSignCount +
                    "]当前周期[" + cycleStartDate + "-" + cycleEndDate+"]");
            Status.setFlagToday(CompletedKeyEnum.JobRightSign.name());
        } catch (JSONException e) {
            Log.other(displayName + "签到失败: " + e.getMessage());
        }
        return "";
    }
}
