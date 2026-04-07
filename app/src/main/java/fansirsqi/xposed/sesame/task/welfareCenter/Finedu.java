package fansirsqi.xposed.sesame.task.welfareCenter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.util.maps.UserMap;

public class Finedu {
    private static final String TAG = "学分💯 ";
    private static final String TASK_ERROR_CACHE_PREFIX = "FineduTaskError_";
    private static final long TASK_ERROR_CACHE_DURATION = 12 * 60 * 60 * 1000; // 12小时缓存
    // 黑名单任务列表
    private static final Set<String> BLACKLISTED_TASKS = new HashSet<>(Arrays.asList(
            "解锁知识勋章",
            "完成今日测一测",
            "邀请好友来看看",
            "学习知识点30秒"
    ));
    private String beforeCredits = "0";

    public void handle() {
        try {
            if (Status.hasFlagToday("FineduComplete")) {
                return;
            }

            // 查询任务前的学分
            beforeCredits = queryUserCredit();

            queryUserInfo();
            queryTaskList();

            // 查询任务后的学分并计算增加数量
            String afterCredits = queryUserCredit();
            int before = Integer.parseInt(beforeCredits);
            int after = Integer.parseInt(afterCredits);
            int increase = after - before;

            Log.record(TAG, "总学分: " + afterCredits + " (增加: " + increase + " 学分)");
        } catch (NumberFormatException e) {
            Log.record(TAG, "学分数据格式错误");
        } catch (Exception e) {
            Log.error(TAG, "handle--未知错误: " + e.getMessage());
        }
    }

    /**
     * 查询用户总学分
     * @return 用户当前总学分
     */
    private String queryUserCredit() {
        try {
            String response = RequestManager.requestString(
                    "com.alipay.welfarefinedu.common.service.facade.credit.CreditQueryFacade.queryUserCredit",
                    "[{}]"
            );

            if (response == null || response.isEmpty()) {
                Log.error(TAG, "queryUserCredit--响应为空");
                return "0";
            }

            JSONObject creditResponse = new JSONObject(response);

            if (creditResponse.optBoolean("success")) {
                JSONObject creditContent = creditResponse.optJSONObject("content");
                if (creditContent != null) {
                    return creditContent.optString("credits", "0");
                }
            } else {
                Log.error(TAG, "queryUserCredit--请求失败: " + creditResponse.optString("message", "未知错误"));
            }
        } catch (JSONException e) {
            Log.error(TAG, "queryUserCredit--JSON解析错误: " + e);
        } catch (Exception e) {
            Log.error(TAG, "queryUserCredit--未知错误: " + e.getMessage());
        }
        return "0";
    }

    /**
     * 查询即将过期的学分信息
     * @return 即将过期的学分信息JSONObject
     */
    private JSONObject queryUserWillExpiredCredit() {
        try {
            String response = RequestManager.requestString(
                    "com.alipay.welfarefinedu.common.service.facade.credit.CreditQueryFacade.queryUserWillExpiredCredit",
                    "[{}]"
            );

            if (response == null || response.isEmpty()) {
                Log.error(TAG, "queryUserWillExpiredCredit--响应为空");
                return null;
            }

            return new JSONObject(response);
        } catch (JSONException e) {
            Log.error(TAG, "queryUserWillExpiredCredit--JSON解析错误: " + e);
        } catch (Exception e) {
            Log.error(TAG, "queryUserWillExpiredCredit--未知错误: " + e.getMessage());
        }
        return null;
    }

    private void queryUserInfo() {
        try {
            // 查询用户总学分已在handle()方法中查询过，这里直接使用
            String totalCredits = beforeCredits;
            Log.record(TAG, "用户总学分: " + totalCredits);

            TimeUtil.sleep(1000);

            // 检查是否需要查询即将过期的学分（距离过期小于等于5天）
            if (needQueryExpiredCredit()) {
                JSONObject expiredCreditResponse = queryUserWillExpiredCredit();

                if (expiredCreditResponse != null && expiredCreditResponse.optBoolean("success")) {
                    JSONObject expiredContent = expiredCreditResponse.optJSONObject("content");
                    if (expiredContent != null) {
                        String willExpiredCredits = expiredContent.optString("credits", "0");
                        String expiredTime = expiredContent.optString("expiredTime", "未知");
                        Log.record(TAG, "即将过期学分: " + willExpiredCredits + ",过期时间: " + expiredTime);
                    }
                }
            }
        } catch (Exception e) {
            Log.error(TAG, "queryUserInfo--未知错误: " + e.getMessage());
        }
    }

    /**
     * 判断是否需要查询即将过期的学分（距离过期小于等于5天）
     * @return 是否需要查询
     */
    private boolean needQueryExpiredCredit() {
        try {
            JSONObject expiredCreditResponse = queryUserWillExpiredCredit();

            if (expiredCreditResponse != null && expiredCreditResponse.optBoolean("success")) {
                JSONObject expiredContent = expiredCreditResponse.optJSONObject("content");
                if (expiredContent != null) {
                    String expiredTime = expiredContent.optString("expiredTime", "");
                    if (!expiredTime.isEmpty()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        Date expireDate = sdf.parse(expiredTime);
                        Date currentDate = new Date();

                        long diffInMillies = expireDate.getTime() - currentDate.getTime();
                        long diffInDays = diffInMillies / (1000 * 60 * 60 * 24);

                        return diffInDays <= 5;
                    }
                }
            }
        } catch (ParseException e) {
            Log.error(TAG + "needQueryExpiredCredit--日期解析错误: " + e);
        } catch (Exception e) {
            Log.error(TAG + "needQueryExpiredCredit--未知错误: " + e.getMessage());
        }
        return false;
    }

    private void queryTaskList() {
        try {
            if (Status.hasFlagToday("FineduComplete")) {
                return;
            }

            // 查询用户任务列表
            String responseStr = RequestManager.requestString(
                    "com.alipay.welfarefinedu.common.service.facade.task.TaskQueryFacade.selectUserTaskList",
                    "[{}]"
            );

            if (responseStr == null || responseStr.isEmpty()) {
                Log.record(TAG, "获取任务列表失败，响应为空");
                return;
            }

            JSONObject response = new JSONObject(responseStr);

            if (response == null) {
                Log.record(TAG, "获取任务列表失败，响应为空");
                return;
            }

            JSONArray taskList = response.optJSONArray("content");
            if (taskList == null || taskList.length() == 0) {
                Log.record(TAG, "没有找到待办任务");
                Status.setFlagToday("FineduComplete");
                return;
            }

            // 遍历并处理未完成的任务
            boolean hasUncompletedTask = false;
            int processedTaskCount = 0;

            for (int i = 0; i < taskList.length(); i++) {
                try {
                    JSONObject task = taskList.getJSONObject(i);
                    String status = task.optString("status");
                    String taskId = task.optString("taskId");
                    String taskName = task.optString("taskName");
                    //String taskType = task.optString("taskType");

                    // 只处理未完成的任务
                    if (!"COMPLETED".equals(status)) {
                        // 检查任务是否在错误缓存中
                        String taskErrorKey = TASK_ERROR_CACHE_PREFIX + taskId;
                        if (Status.hasTemporaryStatusValid(taskErrorKey)) {
                            //Log.record(TAG, "任务[" + taskName + "]已在错误缓存中，跳过执行");
                            continue;
                        }

                        // 检查是否为黑名单任务
                        if (BLACKLISTED_TASKS.contains(taskName)) {
                            //Log.record(TAG, "任务[" + taskName + "]已在黑名单中，跳过执行");
                            continue;
                        }

                        hasUncompletedTask = true;
                        boolean taskSuccess = handleTask(taskId, taskName);

                        // 如果任务执行失败，加入错误缓存
                        if (!taskSuccess) {
                            Status.setTemporaryStatusWithExpiry(taskErrorKey, TASK_ERROR_CACHE_DURATION);
                        }

                        processedTaskCount++;
                        TimeUtil.sleep(RandomUtil.nextInt(7000, 9000));
                    }
                } catch (JSONException e) {
                    Log.error(TAG, "解析任务项失败: " + e);
                    // 继续处理下一个任务
                    continue;
                }
            }

            // 如果没有未完成的任务，或者处理了所有任务但没有成功完成任何任务，
            // 或者所有未完成任务都是黑名单任务，则设置完成标志
            if (!hasUncompletedTask || processedTaskCount == 0) {
                Status.setFlagToday("FineduComplete");
            }
        } catch (JSONException e) {
            Log.error(TAG, "queryTaskList--JSON解析错误: " + e);
        } catch (Exception e) {
            Log.error(TAG, "queryTaskList--未知错误: " + e.getMessage());
        }
    }

    private JSONObject getUserDailyAttendanceRecord() {
        try {
            String apiName = "com.alipay.welfarefinedu.common.service.facade.knowledge.KnowledgeQueryFacade.getUserDailyAttendanceRecord";
            String responseStr = RequestManager.requestString(apiName, "[{}]");

            if (responseStr == null || responseStr.isEmpty()) {
                Log.error(TAG, "查询签到记录失败: 响应为空");
                return null;
            }

            return new JSONObject(responseStr);
        } catch (Exception e) {
            Log.error(TAG, "查询签到记录异常: " + e.getMessage());
            return null;
        }
    }


    /**
     * 处理具体任务
     * @param taskId 任务ID
     * @param taskName 任务名称
     * @return 任务是否执行成功
     */
    private boolean handleTask(String taskId, String taskName) {
        try {
            // 检查是否为黑名单任务
            if (BLACKLISTED_TASKS.contains(taskName)) {
                return false;
            }

            String requestData;
            String apiName = "com.alipay.welfarefinedu.common.service.facade.task.TaskOperateFacade.handleTask";

            if (taskId.equals("learn")) {
                // 学习任务
                //requestData = buildLearnRequest(taskId);
                return false;
            } else if (taskId.equals("knowledgeCalendar") &&!Status.hasFlagToday("KnowledgeCalendarSign")) {
                // 签到任务
                return handleKnowledgeCalendarTask(taskId, taskName);
            } else {
                // 其他任务
                requestData = "[{\"bizId\": \"" + taskId + "\",\"taskId\": \"" + taskId + "\"}]";
            }

            // 执行任务请求
            return executeTaskRequest(apiName, requestData, taskName);

        } catch (Exception e) {
            Log.error(TAG, "任务[" + taskName + "]处理异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理签到任务
     */
    private boolean handleKnowledgeCalendarTask(String taskId, String taskName) {
        // 查询签到记录
        JSONObject attendanceRecord = getUserDailyAttendanceRecord();
        if (attendanceRecord == null || !attendanceRecord.optBoolean("success")) {
            Log.error(TAG, "获取签到记录失败");
            return false;
        }

        JSONArray content = attendanceRecord.optJSONArray("content");
        if (content == null || content.length() == 0) {
            Log.record(TAG, "没有签到记录");
            return false;
        }

        // 获取当前日期并查找未签到记录
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String knowledgeId = findUnattendedRecord(content, today);

        if (knowledgeId == null) {
            Log.record(TAG, "未找到可签到记录");
            return false;
        }

        // 构造签到请求
        String requestData = "[{\"bizId\":\"" + knowledgeId + "\",\"taskId\":\"knowledgeCalendar\",\"uid\":\"" + UserMap.getCurrentUid() + "\"}]";
        String apiName = "com.alipay.welfarefinedu.common.service.facade.task.TaskOperateFacade.handleTask";
        Status.setFlagToday("KnowledgeCalendarSign");
        return executeTaskRequest(apiName, requestData, taskName);
    }

    /**
     * 查找未签到记录
     */
    private String findUnattendedRecord(JSONArray content, String today) {
        for (int i = 0; i < content.length(); i++) {
            try {
                JSONObject record = content.getJSONObject(i);
                String calendarDate = record.optString("calendarDate");
                boolean attendance = record.optBoolean("attendance", false);

                if (calendarDate.equals(today) && !attendance) {
                    return record.optString("knowledgeId");
                }
            } catch (JSONException e) {
                Log.error(TAG, "解析签到记录异常: " + e);
            }
        }
        return null;
    }

    /**
     * 构造学习任务请求
     */
    private String buildLearnRequest(String taskId) {
        return "[{\"bizId\":\"K_2025090122827843\",\"taskId\":\"" + taskId + "\",\"uid\":\"" + UserMap.getCurrentUid() + "\"}]";
    }

    /**
     * 执行任务请求
     */
    private boolean executeTaskRequest(String apiName, String requestData, String taskName) {
        String responseStr = RequestManager.requestString(apiName, requestData);

        if (responseStr == null || responseStr.isEmpty()) {
            Log.error(TAG, "任务[" + taskName + "]执行失败: 响应为空");
            return false;
        }

        try {
            JSONObject result = new JSONObject(responseStr);
            if (result.optBoolean("success")) {
                Log.record(TAG, "完成[" + taskName + "]");
                return true;
            } else {
                String errorMsg = result.optString("message", "未知错误");
                Log.error(TAG, "任务[" + taskName + "]执行失败: " + errorMsg);

                if (errorMsg.contains("task token analysis failed") ||
                        errorMsg.contains("服务器异常")) {
                    BLACKLISTED_TASKS.add(taskName);
                    Log.record(TAG, "任务[" + taskName + "]已添加到黑名单");
                }
                return false;
            }
        } catch (JSONException e) {
            Log.error(TAG, "任务[" + taskName + "]JSON解析错误: " + e);
            return false;
        }
    }

}
