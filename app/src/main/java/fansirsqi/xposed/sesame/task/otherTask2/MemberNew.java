package fansirsqi.xposed.sesame.task.otherTask2;

import android.annotation.SuppressLint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.task.otherTask.BaseCommTask;
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum;
import fansirsqi.xposed.sesame.util.GlobalThreadPools;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import kotlinx.serialization.json.JsonObject;

public class MemberNew extends BaseCommTask {

    private final String TAG = "会员积分💎";
    private static final String SUCCESS = "success";
    private static final int MAX_RETRY_TIMES = 3;
    private static final int MAX_EXECUTE_ATTEMPTS = 3;
    private static final long COOLDOWN_ERROR_MS = 1 * 60 * 60 * 1000; // 1小时冷却（任务为空或1009错误）
    private static final long COOLDOWN_SUCCESS_MS = 30 * 60 * 1000; // 30分钟冷却（有任务完成）
    private static final String MEMBER_EXECUTION_COOLDOWN_FLAG = "MemberNew_LastExecution";
    
    // 任务执行状态标记
    private boolean hasCompletedTask = false;
    private boolean hasError1009 = false;
    private boolean hasEmptyTask = true;
    
    // 线程安全控制
    private final ReentrantLock executionLock = new ReentrantLock();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    public MemberNew() {
        this.displayName = "会员积分💎";
    }
    private boolean checkResponseError1009(JSONObject response) {
        if (response == null) return false;
        String errorMessage = response.optString("errorMessage", "");
        String resultDesc = response.optString("resultDesc", "");
        String errorTip = response.optString("errorTip", "");
        if (errorMessage.contains("人气太旺") || errorMessage.contains("请稍后再试") ||
            resultDesc.contains("人气太旺") || resultDesc.contains("请稍后再试") ||
            response.optInt("error", 0) == 1009 || "1009".equals(errorTip)) {
            hasError1009 = true;
            Log.error(TAG, "服务器限流，停止重试");
            return true;
        }
        return false;
    }

    private boolean checkBoxError1009(JSONObject response) {
        if (response == null) return false;
        String errorMessage = response.optString("errorMessage", "");
        String resultDesc = response.optString("resultDesc", "");
        String errorTip = response.optString("errorTip", "");
        if (errorMessage.contains("人气太旺") || errorMessage.contains("请稍后再试") ||
            resultDesc.contains("人气太旺") || resultDesc.contains("请稍后再试") ||
            response.optInt("error", 0) == 1009 || "1009".equals(errorTip)) {
            Log.error(TAG, "宝箱服务器限流，设置宝箱冷却2小时");
            Status.setTemporaryStatusWithExpiry("MemberNew_Box_1009", 2 * 60 * 60 * 1000);
            return true;
        }
        return false;
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    protected void handle() {
        // 检查冷却时间
        if (Status.hasTemporaryStatusValid(MEMBER_EXECUTION_COOLDOWN_FLAG)) {
            long remainingTime = Status.getTemporaryStatusRemainingMinutes(MEMBER_EXECUTION_COOLDOWN_FLAG);
            Log.runtime(TAG, "距离上次执行间隔，还需等待" + remainingTime + "分钟，跳过本次调用");
            return;
        }

        // 线程安全检查 - 防止并发执行
        if (!isRunning.compareAndSet(false, true)) {
            Log.runtime(TAG, "任务正在执行中，跳过本次调用");
            return;
        }
        
        executionLock.lock();
        try {
            Log.runtime(TAG, "开始执行会员积分任务");
            
            // 重置状态标记
            hasCompletedTask = false;
            hasError1009 = false;
            hasEmptyTask = true;
            
            boolean initSuccess = false;
            // 1. 初始化会员中心
            if (initMemberCenterSafe() && !hasError1009) {
                initSuccess = true;
                // 2. 处理签到
                if (handleSignInSafe() && !hasError1009) {
                    // 3. 处理任务列表
                    handleTaskListsSafe();
                }
            }
            
            // 宝箱任务独立运行，不受 hasError1009 控制
            handBox();
            
            if (initSuccess && !hasError1009) {
                // 4. 处理累积奖励
                handleTaskSuccessSafe();
                // 5. 查询积分
                queryPointCertSafe(1, 8, false);
            }
            
            // 根据执行结果设置不同的冷却时间
            long cooldownTime;
            String cooldownReason;
            if (hasError1009) {
                cooldownTime = COOLDOWN_ERROR_MS;
                cooldownReason = "触发1009错误";
            } else if (hasEmptyTask && !hasCompletedTask) {
                cooldownTime = COOLDOWN_ERROR_MS;
                cooldownReason = "任务列表为空";
            } else if (hasCompletedTask) {
                cooldownTime = COOLDOWN_SUCCESS_MS;
                cooldownReason = "有任务完成";
            } else {
                cooldownTime = COOLDOWN_ERROR_MS;
                cooldownReason = "无可执行任务";
            }

            Status.setTemporaryStatusWithExpiry(MEMBER_EXECUTION_COOLDOWN_FLAG, cooldownTime);
            Long nextAvailableTime = Status.getTemporaryStatusExpiry(MEMBER_EXECUTION_COOLDOWN_FLAG);
            if (nextAvailableTime != null) {
                Log.other(TAG, "会员积分任务执行完成（" + cooldownReason + "），下次可执行时间：" +
                          new SimpleDateFormat("HH:mm:ss").format(new java.util.Date(nextAvailableTime)));
            }

        } catch (Throwable th) {
            Log.error(TAG, "任务执行异常: " + th.getMessage());
            sleepRandomTime();
        } finally {
            isRunning.set(false);
            executionLock.unlock();
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }



    /**
     * 安全初始化会员中心
     */
    private boolean initMemberCenterSafe() {
        try {
            // 使用数组存储初始化方法，便于维护
            Runnable[] initTasks = {
                () -> AntMemberRpcCall.PlayConsultFacadeConsult(), // 1. 签到页初始化
                () -> AntMemberRpcCall.queryVajraPositionCarouselMessage(), // 2. 轮播消息
                () -> AntMemberRpcCall.queryVajraPositionCarouselMessageNew(), // 3. 金刚位信息
                () -> AntMemberRpcCall.commonTransFatigue(), // 4. 疲劳度查询
                () -> AntMemberRpcCall.queryReSignInCardInfo(), // 5. 补签卡查询
                () -> AntMemberRpcCall.queryCommonDeliveryInfo(), // 6. 投放配置查询
                () -> AntMemberRpcCall.batchQueryCommonDeliveryInfo(), // 7. 批量投放配置查询
                () -> AntMemberRpcCall.querySubscribeInfo(), // 8. 订阅状态查询
                () -> AntMemberRpcCall.queryGameEntranceInfo(), // 9. 游戏入口查询
                () -> AntMemberRpcCall.querySimpleIndex(), // 10. 大众会员积分查询
                () -> AntMemberRpcCall.queryGameTaskList(), // 11. 游戏中心任务列表
                () -> AntMemberRpcCall.queryMultiActivityDelivery(), // 12. 多活动投放咨询
                () -> AntMemberRpcCall.queryPointsTravelActivity(), // 13. 积分旅行咨询
                () -> AntMemberRpcCall.queryPointsJointActivity(), // 14. 积分联运咨询
                () -> AntMemberRpcCall.querySchoolPayActivity(), // 15. 支付活动咨询
                () -> AntMemberRpcCall.queryPayActivity() // 16. 支付活动咨询2
            };
            
            for (Runnable task : initTasks) {
                try {
                    task.run();
                    TimeUtil.sleep(RandomUtil.nextInt(1500, 3000)); // 随机延迟，模拟真实行为
                } catch (Exception e) {
                    Log.error(TAG, "初始化步骤失败: " + e.getMessage());
                }
            }
            return true;
        } catch (Exception e) {
            Log.error(TAG, "会员中心初始化失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 安全处理签到
     */
    private boolean handleSignInSafe() {
        try {
            if (!Status.hasFlagToday(CompletedKeyEnum.MemberSignIn.name())) {
                JSONObject response = new JSONObject(AntMemberRpcCall.queryMemberSigninCalendar());
                if (SUCCESS.equalsIgnoreCase(response.getString("resultCode"))) {
                    Log.other(this.displayName + "签到获得✅[" + response.getString("signinPoint") + 
                             "积分]#已签到" + response.getString("signinSumDay") + "天");
                    Status.setFlagToday(CompletedKeyEnum.MemberSignIn.name());
                } else {
                    Log.error(TAG, "签到失败: " + response.optString("resultDesc"));
                    checkResponseError1009(response);
                    TimeUtil.sleep((long) this.executeIntervalInt);
                    return false;
                }
            }
            TimeUtil.sleep(RandomUtil.nextInt(4000, 7000));
            return true;
        } catch (Exception e) {
            Log.error(TAG, "签到处理异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 安全处理任务列表
     */
    private void handleTaskListsSafe() {
        try {
            // 签到页任务列表
            signPageTaskListSafe();
            
            TimeUtil.sleep(RandomUtil.nextInt(4000, 7000));
            
            // 全部状态任务列表
            if (!Status.hasFlagToday("queryAllStatusTaskList")) {
                queryAllStatusTaskListSafe();
            }
            
            TimeUtil.sleep(RandomUtil.nextInt(4000, 7000));
            
            // 会员任务列表
//            if (!Status.hasFlagToday("memTaskListQueryFacade")) {
//                memTaskListQueryFacadeSafe();
//            }
        } catch (Exception e) {
            Log.error(TAG, "任务列表处理异常: " + e.getMessage());
        }
    }
    
    /**
     * 安全处理累积任务奖励
     */
    private void handleTaskSuccessSafe() {
        try {
            JSONObject response = new JSONObject(AntMemberRpcCall.queryAccumulateTask());
            if (response.optBoolean("success")) {
                JSONArray availableTaskProcessList = response.optJSONArray("availableTaskProcessList");
                if (availableTaskProcessList == null || availableTaskProcessList.length() == 0) {
                    return;
                }
                
                JSONObject processList = availableTaskProcessList.getJSONObject(0);
                String taskProcessId = String.valueOf(processList.optLong("taskProcessId"));
                int currentCount = processList.optInt("currentCount");
                int targetCount = processList.optInt("targetCount");
                JSONArray stageProcessList = processList.optJSONArray("stageProcessList");
                
                if (stageProcessList != null) {
                    for (int i = 0; i < stageProcessList.length(); i++) {
                        try {
                            JSONObject stageProcess = stageProcessList.getJSONObject(i);
                            String stageStatus = stageProcess.optString("stageStatus");
                            
                            if ("COMPLETE".equals(stageStatus)) {
                                String awardRelatedOutBizNo = stageProcess.optString("awardRelatedOutBizNo");
                                int awardPoint = stageProcess.optInt("awardPoint");
                                
                                JSONObject result = new JSONObject(AntMemberRpcCall.receivePointAward(taskProcessId, awardRelatedOutBizNo));
                                if (result.optBoolean("success")) {
                                    Log.other(this.displayName + "当前进度[" + currentCount + "/" + targetCount + "]处理阶段奖励");
                                    Log.other(this.displayName + "领取奖励✅+" + awardPoint + "积分");
                                } else {
                                    Log.error(TAG, "领取奖励失败: " + result);
                                }
                            }
                            TimeUtil.sleep(2354);
                        } catch (Exception e) {
                            Log.error(TAG, "处理阶段奖励异常: " + e.getMessage());
                        }
                    }
                }
            } else {
                Log.error(TAG, "查询累积任务失败: " + response);
            }
        } catch (Exception e) {
            Log.error(TAG, "处理累积任务异常: " + e.getMessage());
        }
    }
    /**
     * 安全处理BROWSE类型任务
     */
    private boolean doTaskSafe(JSONArray taskList) {
        boolean hasCompletedAnyTask = false;

        if (taskList == null || taskList.length() == 0) {
            return hasCompletedAnyTask;
        }

        for (int i = 0; i < taskList.length(); i++) {
            try {
                JSONObject taskItem = taskList.getJSONObject(i);
                if (taskItem == null) continue;

                // 处理广告任务
                if (taskItem.optBoolean("adTask") && "PROCESSING".equals(taskItem.optString("status"))) {
                    doAdTaskSafe(taskItem);
                    continue;
                }

                // 提取任务信息
                TaskInfo taskInfo = extractTaskInfoSafe(taskItem);
                if (taskInfo == null || taskInfo.retryCount <= 0) continue;
                
                // 执行任务
                for (int attempt = 0; attempt < Math.min(taskInfo.retryCount, MAX_EXECUTE_ATTEMPTS); attempt++) {
                    if (executeSingleTaskSafe(taskInfo, attempt)) {
                        hasCompletedAnyTask = true;
                    }
                    sleepRandomTime();
                }
                
                sleepRandomTime();
            } catch (Exception e) {
                Log.error(TAG, "处理任务异常: " + e.getMessage());
                TimeUtil.sleep(this.executeIntervalInt);
            }
        }

        TimeUtil.sleep(this.executeIntervalInt);
        return hasCompletedAnyTask;
    }

    /**
     * 安全处理广告任务
     */
    private void doAdTaskSafe(JSONObject taskItem) {
        try {
            String bizId = JsonUtil.getValueByPath(taskItem, "lightsAdExtMap.bizId");
            String entityType = JsonUtil.getValueByPath(taskItem, "lightsAdExtMap.entityType");
            String title = JsonUtil.getValueByPath(taskItem, "simpleTaskConfig.title");

            // 跳过下单购买任务
            if ("-1".equals(entityType)) {
                return;
            }
            
            // 完成广告任务
            if (!bizId.isEmpty()) {
                JSONObject response = new JSONObject(RequestManager.requestString(
                    "com.alipay.adtask.biz.mobilegw.service.task.finish",
                    "[{\"bizId\":\"" + bizId + "\",\"extendInfo\":{}}]"));
                
                sleepRandomTime();
                
                // 检查1009错误
                if (response.optInt("error", 0) == 1009) {
                    hasError1009 = true;
                    Log.error(TAG, "广告任务触发1009错误");
                    return;
                }
                
                if (response.optBoolean("success")) {
                    Object rewardInfo = JsonUtil.getValueByPathObject(response, "extendInfo.rewardInfo");
                    if (rewardInfo != null) {
                        JSONObject rewardData = (JSONObject) rewardInfo;
                        Log.other(this.displayName + "完成✅[" + title + "]+" + 
                                 rewardData.optString("rewardAmount") + rewardData.optString("rewardTypeName"));
                        hasCompletedTask = true; // 标记有任务完成
                        hasEmptyTask = false;
                    }
                }
            }
            GlobalThreadPools.sleep(RandomUtil.nextInt(5000, 10000));
        } catch (Exception e) {
            Log.error(TAG, "广告任务处理异常: " + e.getMessage());
        }
    }

    /**
     * 任务信息内部类
     */
    private static class TaskInfo {
        String taskName;
        Long taskId;
        String rewardPoints;
        String businessType;
        String businessId;
        int retryCount;
        int currentCount;
        int targetCount;
        boolean isHybrid;
    }
    
    /**
     * 安全提取任务信息
     */
    private TaskInfo extractTaskInfoSafe(JSONObject taskItem) {
        try {
            if (taskItem == null) return null;
            
            JSONObject taskConfig = taskItem.optJSONObject("taskConfigInfo");
            if (taskConfig == null) {
                Log.error(TAG, "任务配置信息为空");
                return null;
            }
            
            TaskInfo info = new TaskInfo();
            
            info.taskName = taskConfig.optString("name", "未知任务");
            info.taskId = taskConfig.optLong("id", -1L);
            
            JSONObject awardParam = taskConfig.optJSONObject("awardParam");
            info.rewardPoints = awardParam != null ? awardParam.optString("awardParamPoint", "0") : "0";
            
            JSONArray targetBusinessArray = taskConfig.optJSONArray("targetBusiness");
            if (targetBusinessArray != null && targetBusinessArray.length() > 0) {
                String targetBusiness = targetBusinessArray.getString(0);
                String[] split = targetBusiness.split("#");
                info.businessType = split.length > 2 ? split[1] : split[0];
                info.businessId = split.length > 2 ? split[2] : split[1];
            }
            
            info.isHybrid = taskItem.optBoolean("hybrid", false);
            if (info.isHybrid) {
                JSONObject extInfo = taskItem.optJSONObject("extInfo");
                if (extInfo != null) {
                    info.currentCount = extInfo.optInt("PERIOD_CURRENT_COUNT", 0);
                    info.targetCount = extInfo.optInt("PERIOD_TARGET_COUNT", 0);
                    info.retryCount = Math.max(1, info.targetCount - info.currentCount);
                } else {
                    info.retryCount = 1;
                }
            } else {
                info.retryCount = 1;
            }
            
            return info;
        } catch (Exception e) {
            Log.error(TAG, "提取任务信息异常: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 安全执行单个任务
     */
    private boolean executeSingleTaskSafe(TaskInfo taskInfo, int attempt) {
        try {
            if (taskInfo == null) return false;
            
            // 申请任务
            JSONObject applyResult = new JSONObject(AntMemberRpcCall.applyTask(taskInfo.taskName, taskInfo.taskId));
            
            // 检查1009错误
            if (applyResult.optInt("error", 0) == 1009) {
                hasError1009 = true;
                Log.error(TAG, "申请任务触发1009错误");
                return false;
            }
            
            if (!SUCCESS.equalsIgnoreCase(applyResult.optString("resultCode"))) {
                Log.error(TAG, "申请任务失败: " + applyResult.optString("resultDesc"));
                TimeUtil.sleep(RandomUtil.nextInt(5000, 7000));
                return false;
            }

            // 执行任务
            JSONObject executeResult = new JSONObject(AntMemberRpcCall.executeTask(taskInfo.businessId, taskInfo.businessType));
            
            // 检查1009错误
            if (executeResult.optInt("error", 0) == 1009) {
                hasError1009 = true;
                Log.error(TAG, "执行任务触发1009错误");
                return false;
            }
            
            if (SUCCESS.equalsIgnoreCase(executeResult.optString("resultCode"))) {
                String progress = taskInfo.isHybrid ? 
                    String.format(Locale.CHINA, "(%d/%d)", taskInfo.currentCount + attempt + 1, taskInfo.targetCount) : "";
                Log.other(this.displayName + "完成✅[" + taskInfo.taskName + progress + "]#" + taskInfo.rewardPoints + "积分");
                hasCompletedTask = true; // 标记有任务完成
                hasEmptyTask = false;
                return true;
            } else {
                Log.error(TAG, "执行任务失败: " + executeResult.optString("resultDesc"));
            }
        } catch (Exception e) {
            Log.error(TAG, "执行任务异常: " + e.getMessage());
        }
        return false;
    }


    /**
     * 安全处理OTHERS类型任务
     */
    private void doOtherTaskSafe(JSONArray taskList) {
        if (taskList == null || taskList.length() == 0) {
            Log.other(TAG, "OTHERS任务列表为空");
            return;
        }

        for (int i = 0; i < taskList.length(); i++) {
            try {
                JSONObject taskItem = taskList.optJSONObject(i);
                if (taskItem == null) continue;

                OtherTaskInfo taskInfo = extractOtherTaskInfoSafe(taskItem);
                if (taskInfo == null || taskInfo.needExecuteTimes <= 0) continue;
                
                // 只处理 uvChangeBusinessType 类型任务
                if ("uvChangeBusinessType".equalsIgnoreCase(taskInfo.businessType)) {
                    handleGameTaskSafe(taskInfo.taskName, taskInfo.taskId, taskInfo.awardPoint, taskInfo.targetBusinessArray);
                }
                
            } catch (Exception e) {
                Log.error(TAG, "Others任务处理异常: " + e.getMessage());
            }
            
            TimeUtil.sleep(this.executeIntervalInt);
        }
    }
    
    /**
     * OTHERS任务信息内部类
     */
    private static class OtherTaskInfo {
        String taskName;
        Long taskId;
        String awardPoint;
        String businessType;
        JSONArray targetBusinessArray;
        int needExecuteTimes;
    }
    
    /**
     * 安全提取OTHERS任务信息
     */
    private OtherTaskInfo extractOtherTaskInfoSafe(JSONObject taskItem) {
        try {
            if (taskItem == null) return null;
            
            JSONObject config = taskItem.optJSONObject("taskConfigInfo");
            if (config == null) {
                Log.error(TAG, "OTHERS任务配置为空，跳过");
                return null;
            }

            OtherTaskInfo info = new OtherTaskInfo();
            info.taskName = config.optString("name", "未知任务");
            info.taskId = config.optLong("id", -1L);
            info.businessType = config.optString("businessType", "");
            
            // 获取奖励积分
            JSONObject awardParam = config.optJSONObject("awardParam");
            info.awardPoint = awardParam != null ? awardParam.optString("awardParamPoint", "0") : "0";
            
            // 获取目标业务数组
            info.targetBusinessArray = config.optJSONArray("targetBusiness");
            if (info.targetBusinessArray == null || info.targetBusinessArray.length() == 0) {
                Log.error(TAG, "targetBusinessArray为空，跳过任务");
                return null;
            }
            
            // 计算需要执行的次数
            boolean isHybrid = taskItem.optBoolean("hybrid", false);
            if (isHybrid) {
                JSONObject extInfo = taskItem.optJSONObject("extInfo");
                if (extInfo != null) {
                    int currentCount = extInfo.optInt("PERIOD_CURRENT_COUNT", 0);
                    int targetCount = extInfo.optInt("PERIOD_TARGET_COUNT", 0);
                    info.needExecuteTimes = Math.max(0, targetCount - currentCount);
                }
            } else {
                info.needExecuteTimes = 1;
            }
            
            return info;
        } catch (Exception e) {
            Log.error(TAG, "Others任务数据解析失败: " + e.getMessage());
            return null;
        }
    }



    /**
     * 安全处理游戏任务
     */
    private void handleGameTaskSafe(String taskName, Long taskId, String awardPoint, JSONArray targetBusinessArray) {
        try {
            if (targetBusinessArray == null || targetBusinessArray.length() == 0) {
                Log.error(TAG, "游戏任务目标业务数组为空");
                return;
            }
            
            String[] split = targetBusinessArray.getString(0).split("#");
            String ngfeKey = split.length > 1 ? split[0] : "";
            
            for (int i = 0; i < Math.min(1, MAX_EXECUTE_ATTEMPTS); i++) {
                try {
                    // 申请任务
                    JSONObject applyResult = new JSONObject(AntMemberRpcCall.applyTask(taskName, taskId));
                    TimeUtil.sleep(this.executeIntervalInt);

                    if (!SUCCESS.equalsIgnoreCase(applyResult.optString("resultCode"))) {
                        Log.error(TAG, "游戏任务申请失败: " + applyResult.optString("resultDesc"));
                    } else {
                        Log.runtime(TAG, "使用applyTask2申请游戏任务");
                        JSONObject applyResult2 = new JSONObject(AntMemberRpcCall.applyTask2(taskId));
                        TimeUtil.sleep(this.executeIntervalInt);
                    }

                    // 执行NGFE更新
                    JSONObject executeResult = new JSONObject(AntMemberRpcCall.ngfeUpdate(ngfeKey));
                    TimeUtil.sleep(this.executeIntervalInt);

                    if (executeResult.optBoolean("success")) {
                        Log.other(this.displayName + "完成✅[" + taskName + "]#" + awardPoint + "积分");
                        break; // 成功后退出循环
                    } else {
                        Log.error(TAG, "NGFE更新失败: " + executeResult);
                    }
                } catch (Exception e) {
                    Log.error(TAG, "游戏任务执行异常: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.error(TAG, "处理游戏任务异常: " + e.getMessage());
        }
    }



    /**
     * 安全查询签到页任务列表并执行
     */
    private void signPageTaskListSafe() {
        int retryCount = 0;
        while (retryCount < MAX_RETRY_TIMES) {
            try {
                TimeUtil.sleep(RandomUtil.nextInt(5000, 7000));
                JSONObject response = new JSONObject(AntMemberRpcCall.signPageTaskList());
                TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
                
                if (response.optBoolean("success")) {
                    JSONObject resultData = response.optJSONObject("resultData");
                    if (resultData == null) {
                        retryCount++;
                        continue;
                    }
                    
                    JSONArray categoryTaskList = resultData.optJSONArray("categoryTaskList");
                    if (categoryTaskList == null) {
                        retryCount++;
                        continue;
                    }
                    
                    for (int i = 0; i < categoryTaskList.length(); i++) {
                        try {
                            JSONObject category = categoryTaskList.getJSONObject(i);
                            JSONArray taskArray = category.optJSONArray("taskProcessVOList");
                            String type = category.optString("type");
                            
                            if (taskArray == null || taskArray.length() == 0) {
                                continue;
                            }
                            
                            if ("OTHERS".equalsIgnoreCase(type)) {
                                doOtherTaskSafe(taskArray);
                            } else if ("BROWSE".equalsIgnoreCase(type)) {
                                doTaskSafe(taskArray);
                            }
                        } catch (Exception e) {
                            Log.error(TAG, "处理任务分类异常: " + e.getMessage());
                        }
                    }
                    
                    TimeUtil.sleep(RandomUtil.nextInt(1000, 2000));
                    return; // 成功处理完一次任务，退出循环
                    
                } else {
                    Log.error(TAG, "签到页任务列表请求失败: " + response);
                    if (checkResponseError1009(response)) {
                        break;
                    }
                    retryCount++;
                }
                
            } catch (Exception e) {
                Log.error(TAG, "签到页任务列表处理异常: " + e.getMessage());
                retryCount++;
            }
            
            if (retryCount < MAX_RETRY_TIMES) {
                TimeUtil.sleep(this.executeIntervalInt);
            }
        }
        
        if (retryCount >= MAX_RETRY_TIMES) {
            Log.error(TAG, "签到页任务列表处理重试次数已达上限");
        }
        
        sleepRandomTime();
    }


    /**
     * 安全查询积分证书
     */
    public void queryPointCertSafe(int page, int pageSize, boolean isRecursive) {
        try {
            JSONObject response = new JSONObject(AntMemberRpcCall.queryPointCert(page, pageSize));
            TimeUtil.sleep(RandomUtil.nextInt(3000, 5000));
            
            if (SUCCESS.equalsIgnoreCase(response.optString("resultCode"))) {
                boolean hasNextPage = response.optBoolean("hasNextPage");
                JSONArray certList = response.optJSONArray("certList");
                
                if (certList != null) {
                    for (int i = 0; i < certList.length(); i++) {
                        try {
                            JSONObject cert = certList.getJSONObject(i);
                            String bizTitle = cert.optString("bizTitle");
                            String certId = cert.optString("id");
                            int pointAmount = cert.optInt("pointAmount");
                            
                            TimeUtil.sleep(this.executeIntervalInt);
                            
                            JSONObject receiveResult = new JSONObject(AntMemberRpcCall.receivePointByUser(certId));
                            if (SUCCESS.equalsIgnoreCase(receiveResult.optString("resultCode"))) {
                                Log.other(this.displayName + "领取奖励✅[" + bizTitle + "]#" + pointAmount + "积分");
                            } else {
                                Log.error(TAG, "领取积分失败: " + receiveResult.optString("resultDesc"));
                            }
                        } catch (Exception e) {
                            Log.error(TAG, "处理积分证书异常: " + e.getMessage());
                        }
                    }
                }
                
                if (hasNextPage && isRecursive) {
                    queryPointCertSafe(page + 1, pageSize, isRecursive);
                }
                
                TimeUtil.sleep(this.executeIntervalInt);
            } else {
                Log.error(TAG, "查询积分证书失败: " + response.optString("resultDesc"));
                TimeUtil.sleep(this.executeIntervalInt);
            }
        } catch (Exception e) {
            Log.error(TAG, "查询积分证书异常: " + e.getMessage());
            TimeUtil.sleep(this.executeIntervalInt);
        }
    }

    public void memTaskListQueryFacade() {
        try {
            if (Status.hasFlagToday("memTaskListQueryFacade")) {
                return;
            }
            long time = System.currentTimeMillis();
            String params = "[{\"source\":\"antmember\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"spaceCode\":\"ant_member_xlight_task\",\"switchNormal\":true,\"taskTopConfigId\":\"\"}]";
            JSONObject requestString = new JSONObject(RequestManager.requestString("com.alipay.amic.memtask.h5.MemTaskListQueryFacade.signPageTaskList", params));
            if (!requestString.optBoolean("success")) {
                Status.setFlagToday("memTaskListQueryFacade");
                Log.error(TAG,"signPageTaskList任务列表出错❌:"+requestString);
                GlobalThreadPools.sleep(RandomUtil.nextInt(1000, 2000));
                return;
            }
            JSONArray jSONArray = requestString.getJSONObject("resultData").getJSONArray("adTaskList");
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject jSONObject = jSONArray.getJSONObject(i);
                String bizId = JsonUtil.getValueByPath(jSONObject, "lightsAdExtMap.bizId");
                String entityType = JsonUtil.getValueByPath(jSONObject, "lightsAdExtMap.entityType");
                String title = JsonUtil.getValueByPath(jSONObject, "simpleTaskConfig.title");

                // 跳过下单购买任务
                if (entityType.equals("-1")){
                    continue;
                }
                //完成广告任务
                if (!bizId.isEmpty()) {
                    JSONObject requestString2 = new JSONObject(RequestManager.requestString("com.alipay.adtask.biz.mobilegw.service.task.finish",
                            "[{\"bizId\":\"" + bizId + "\",\"extendInfo\":{}}]"));
                    //随机休眠一段时间
                    sleepRandomTime();
                    if(requestString2.optString("errorScene").equals("3601")){
                        Status.setFlagToday("memTaskListQueryFacade");
                        break;
                    }
                    if (requestString2.optBoolean("success")) {
                        Object valueByPathObject = JsonUtil.getValueByPathObject(requestString2, "extendInfo.rewardInfo");
                        if (valueByPathObject != null) {
                            requestString2 = (JSONObject) valueByPathObject;
                            Log.other(this.displayName + "完成✅[" + title + "]+" + requestString2.getString("rewardAmount") + requestString2.getString("rewardTypeName"));
                        }
                    }
                }
                GlobalThreadPools.sleep(RandomUtil.nextInt(5000, 10000));
            }
            GlobalThreadPools.sleep(RandomUtil.nextInt(2000, 3000));
        } catch (Exception e) {
            Log.error(TAG+"memTaskListQueryFacade err :" + e);
            GlobalThreadPools.sleep(RandomUtil.nextInt(2000, 3000));
        }finally {
            Status.setFlagToday("memTaskListQueryFacade");
        }
    }

    /**
     * 安全随机休眠
     */
    private void sleepRandomTime() {
        try {
            int sleepTime = RandomUtil.nextInt(15000, 20000);
            if (sleepTime > 0) {
                TimeUtil.sleep(sleepTime);
            } else {
                TimeUtil.sleep(15254);
            }
        } catch (Exception e) {
            Log.error(TAG, "随机休眠异常: " + e.getMessage());
        }
    }

    /**
     * 安全查询全部状态任务列表
     */
    private void queryAllStatusTaskListSafe() {
        try {
            JSONObject response = new JSONObject(AntMemberRpcCall.queryAllStatusTaskListNew());
            if (response.optBoolean("success")) {
                // 只有成功返回后，才设置今日已完成标记
                Status.setFlagToday("queryAllStatusTaskList");
                JSONObject resultData = response.optJSONObject("resultData");
                if (resultData != null && resultData.has("availableTaskList")) {
                    JSONArray availableTaskList = resultData.getJSONArray("availableTaskList");
                    if (doTaskSafe(availableTaskList)) {
                        // 递归处理剩余任务，但限制递归深度防止栈溢出
                        queryAllStatusTaskListSafe();
                    }
                    sleepRandomTime();
                    return;
                }
            } else {
                Log.error(TAG, "查询全部状态任务列表失败: " + response);
                checkResponseError1009(response);
                // 失败时不设置今日已完成标记，以便下次重新尝试
            }
            sleepRandomTime();
        } catch (Exception e) {
            Log.error(TAG, "查询全部状态任务列表异常: " + e.getMessage());
            TimeUtil.sleep(this.executeIntervalInt);
        }
    }

    /**
     * 安全查询会员任务列表
     */
    public void memTaskListQueryFacadeSafe() {
        try {
            if (Status.hasFlagToday("memTaskListQueryFacade")) {
                return;
            }
            
            String params = "[{\"source\":\"antmember\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"spaceCode\":\"ant_member_xlight_task\",\"switchNormal\":true,\"taskTopConfigId\":\"\"}]";
            JSONObject response = new JSONObject(RequestManager.requestString("com.alipay.amic.memtask.h5.MemTaskListQueryFacade.signPageTaskList", params));
            
            if (!response.optBoolean("success")) {
                Log.error(TAG, "会员任务列表请求失败: " + response);
                GlobalThreadPools.sleep(RandomUtil.nextInt(1000, 2000));
                return;
            }
            
            // 成功请求，设置今日已完成标记
            Status.setFlagToday("memTaskListQueryFacade");
            
            JSONObject resultData = response.optJSONObject("resultData");
            if (resultData == null) {
                Log.error(TAG, "会员任务结果数据为空");
                return;
            }
            
            JSONArray adTaskList = resultData.optJSONArray("adTaskList");
            if (adTaskList == null) {
                Log.error(TAG, "广告任务列表为空");
                return;
            }
            
            for (int i = 0; i < adTaskList.length(); i++) {
                try {
                    JSONObject adTask = adTaskList.getJSONObject(i);
                    String bizId = JsonUtil.getValueByPath(adTask, "lightsAdExtMap.bizId");
                    String entityType = JsonUtil.getValueByPath(adTask, "lightsAdExtMap.entityType");
                    String title = JsonUtil.getValueByPath(adTask, "simpleTaskConfig.title");

                    // 跳过下单购买任务
                    if ("-1".equals(entityType)) {
                        continue;
                    }
                    
                    // 完成广告任务
                    if (!bizId.isEmpty()) {
                        JSONObject taskResult = new JSONObject(RequestManager.requestString(
                            "com.alipay.adtask.biz.mobilegw.service.task.finish",
                            "[{\"bizId\":\"" + bizId + "\",\"extendInfo\":{}}]"));
                        
                        sleepRandomTime();
                        
                        if ("3601".equals(taskResult.optString("errorScene"))) {
                            break;
                        }
                        
                        if (taskResult.optBoolean("success")) {
                            Object rewardInfo = JsonUtil.getValueByPathObject(taskResult, "extendInfo.rewardInfo");
                            if (rewardInfo != null) {
                                JSONObject rewardData = (JSONObject) rewardInfo;
                                Log.other(this.displayName + "完成✅[" + title + "]+" + 
                                         rewardData.optString("rewardAmount") + rewardData.optString("rewardTypeName"));
                            }
                        }
                    }
                    GlobalThreadPools.sleep(RandomUtil.nextInt(5000, 10000));
                } catch (Exception e) {
                    Log.error(TAG, "处理广告任务异常: " + e.getMessage());
                }
            }
            
            GlobalThreadPools.sleep(RandomUtil.nextInt(2000, 3000));
        } catch (Exception e) {
            Log.error(TAG, "会员任务列表查询异常: " + e.getMessage());
            GlobalThreadPools.sleep(RandomUtil.nextInt(2000, 3000));
        }
    }

    // ==========完成宝箱任务
    /**
     * 处理宝箱任务（开宝箱 + 广告任务）
     */
    private void handBox() {
        if (Status.hasTemporaryStatusValid("MemberNew_Box_1009")) {
            Log.runtime(TAG, "宝箱限流冷却中，跳过宝箱任务");
            return;
        }
        try {
            // 1. 查询宝箱状态
            String queryResponse = AntMemberRpcCall.querySignFloatingBall();
            JSONObject json = new JSONObject(queryResponse);
            
            if (checkBoxError1009(json)) {
                return;
            }
            if (!json.optBoolean("success")) {
                Log.error(TAG, "查询宝箱状态失败: " + json.optString("resultDesc"));
                return;
            }
            
            String bizNo = json.optString("bizNo");
            JSONObject currentTaskInfo = json.optJSONObject("currentTaskInfo");
            
            if (currentTaskInfo == null) {
                Log.runtime(TAG, "宝箱任务信息为空");
                return;
            }
            
            // 2. 处理开宝箱
            String taskStatus = currentTaskInfo.optString("taskStatus");
            if ("PROCESSING".equals(taskStatus)) {
                String taskBizNo = currentTaskInfo.optString("bizNo");
                int awardNum = currentTaskInfo.optInt("awardNum");
                
                JSONObject triggerResult = new JSONObject(AntMemberRpcCall.triggerSignFloatingBall(taskBizNo));
                
                // 检查1009错误
                if (checkBoxError1009(triggerResult)) {
                    Log.error(TAG, "开宝箱触发1009错误");
                    return;
                }
                
                if (triggerResult.optBoolean("success")) {
                    Log.other(this.displayName + "开宝箱✅获得#" + awardNum + "积分");
                    hasCompletedTask = true;
                    hasEmptyTask = false;
                } else {
                    Log.error(TAG, "开宝箱失败: " + triggerResult.optString("resultDesc"));
                }
                
                TimeUtil.sleep(RandomUtil.nextInt(2000, 4000));
            }
            
            // 3. 处理宝箱广告任务（循环完成所有可用任务）
            if (!bizNo.isEmpty()) {
                handleAdBoxTasks(bizNo);
            }
            
        } catch (Exception e) {
            Log.error(TAG, "处理宝箱任务异常: " + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
    }

    /**
     * 处理宝箱广告任务（循环完成所有可用任务）
     */
    private void handleAdBoxTasks(String bizNo) {
        try {
            int completedCount = 0;
            String currentBizNo = bizNo;
            
            while (currentBizNo != null && !currentBizNo.isEmpty()) {
                // 查询广告任务情况
                JSONObject queryResult = new JSONObject(AntMemberRpcCall.querySignFloatingBallAdTask(currentBizNo));
                if (checkBoxError1009(queryResult)) {
                    break;
                }
                if (!queryResult.optBoolean("success")) {
                    Log.error(TAG, "查询宝箱广告任务失败: " + queryResult);
                    break;
                }
                
                JSONObject videoTaskInfo = queryResult.optJSONObject("videoTaskInfo");
                if (videoTaskInfo == null) {
                    Log.runtime(TAG, "宝箱广告任务信息为空,当前无广告");
                    break;
                }
                
                String taskStatus = videoTaskInfo.optString("taskStatus");
                String taskBizNo = videoTaskInfo.optString("bizNo");
                int awardNum = videoTaskInfo.optInt("awardNum");
                
                // 只处理PROCESSING状态的任务
                if (!"PROCESSING".equals(taskStatus)) {
                    break;
                }
                
                // 随机等待16-31秒，模拟真实观看广告
                int waitTime = RandomUtil.nextInt(16000, 31000);
                TimeUtil.sleep(waitTime);
                
                // 触发广告任务完成
                JSONObject triggerResult = new JSONObject(AntMemberRpcCall.triggerAdTask(taskBizNo));
                
                // 检查1009错误
                if (checkBoxError1009(triggerResult)) {
                    Log.error(TAG, "宝箱广告任务触发1009错误");
                    break;
                }
                
                if (triggerResult.optBoolean("success")) {
                    completedCount++;
                    Log.other(this.displayName + "完成✅[宝箱广告" + completedCount + "]#" + awardNum + "积分");
                    hasCompletedTask = true;
                    hasEmptyTask = false;
                    
                    // 检查是否有下一个任务
                    JSONObject nextVideoTaskInfo = triggerResult.optJSONObject("nextVideoTaskInfo");
                    if (nextVideoTaskInfo != null) {
                        String nextTaskStatus = nextVideoTaskInfo.optString("taskStatus");
                        String nextBizNo = nextVideoTaskInfo.optString("bizNo");
                        
                        if ("PROCESSING".equals(nextTaskStatus) && !nextBizNo.isEmpty()) {
                            currentBizNo = nextBizNo;
                            // 继续循环处理下一个任务
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    Log.error(TAG, "完成宝箱广告任务失败: " + triggerResult.optString("resultDesc"));
                    break;
                }
                
                // 防止无限循环，最多处理10个任务
                if (completedCount >= 10) {
                    Log.runtime(TAG, "已完成10个宝箱广告任务，停止处理");
                    break;
                }
            }
            
            if (completedCount > 0) {
                Log.runtime(TAG, "宝箱广告任务处理完成，共完成" + completedCount + "个任务");
            }
            
        } catch (Exception e) {
            Log.error(TAG, "处理宝箱广告任务异常: " + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
    }
}