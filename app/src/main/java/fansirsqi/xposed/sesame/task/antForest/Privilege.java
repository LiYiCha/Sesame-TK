package fansirsqi.xposed.sesame.task.antForest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.util.RandomUtil;

public class Privilege {
    private static final String TAG = "青春特权🌸";
    private static final String YOUTH_PRIVILEGE_PREFIX = "青春特权🌸";
    private static final String STUDENT_SIGN_PREFIX = "青春特权🧧";
    // 标记 & 前缀
    private static final String FLAG_RECEIVED = "youth_privilege_forest_received";
    private static final String FLAG_STUDENT_TASK = "youth_privilege_student_task";
    // 任务状态常量
    private static final String TASK_RECEIVED = "RECEIVED";
    private static final String TASK_FINISHED = "FINISHED";
    private static final String RPC_SUCCESS = "SUCCESS";

    // 签到时间常量
    private static final int SIGN_IN_START_HOUR = 5;
    private static final int SIGN_IN_END_HOUR = 10;

    // 青春特权任务配置
    private static final List<List<String>> YOUTH_TASKS = Arrays.asList(
            Arrays.asList("DNHZ_SL_college", "DAXUESHENG_SJK", "双击卡"),
            Arrays.asList("DXS_BHZ", "NENGLIANGZHAO_20230807", "保护罩"),
            Arrays.asList("DXS_JSQ", "JIASUQI_20230808", "加速器")
    );

    public static boolean youthPrivilege() {
        try {
            if (!Status.hasFlagToday("YouthPrivilegeSign")) return false;

            List<String> processResults = new ArrayList<>();
            for (List<String> task : YOUTH_TASKS) {
                processResults.addAll(processYouthPrivilegeTask(task));
            }

            boolean allSuccess = true;
            for (String result : processResults) {
                if (!"处理成功".equals(result)) {
                    allSuccess = false;
                    break;
                }
            }

            if (allSuccess) Status.setFlagToday("YouthPrivilegeSign");
            return allSuccess;
        } catch (Exception e) {
            Log.printStackTrace(TAG + "青春特权领取异常", e);
            return false;
        }
    }


    private static List<String> processYouthPrivilegeTask(List<String> taskConfig) throws JSONException {
        String queryParam = taskConfig.get(0);
        String receiveParam = taskConfig.get(1);
        String taskName = taskConfig.get(2);

        JSONArray taskList = getTaskList(queryParam);
        if (taskList == null || taskList.length()==0){
            return null;
        }
        return handleTaskList(taskList, receiveParam, taskName);
    }

    private static JSONArray getTaskList(String queryParam) throws JSONException {
        String response = AntForestRpcCall.queryTaskListV2(queryParam);
        JSONObject result = new JSONObject(response);
        if (!result.has("forestTasksNew")) {
            return null;
        }
        return result.getJSONArray("forestTasksNew")
                .getJSONObject(0)
                .getJSONArray("taskInfoList");
    }

    private static List<String> handleTaskList(JSONArray taskInfoList, String taskType, String taskName) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < taskInfoList.length(); i++) {
            JSONObject task = taskInfoList.optJSONObject(i);
            if (task == null) continue;

            JSONObject baseInfo = task.optJSONObject("taskBaseInfo");
            if (baseInfo == null) continue;

            String currentTaskType = baseInfo.optString("taskType");
            if (!taskType.equals(currentTaskType)) continue;

            processSingleTask(baseInfo, taskType, taskName, results);
        }
        return results;
    }

    private static void processSingleTask(JSONObject baseInfo, String taskType, String taskName, List<String> results) {
        String taskStatus = baseInfo.optString("taskStatus");
        if (TASK_RECEIVED.equals(taskStatus)) {
            Log.forest(YOUTH_PRIVILEGE_PREFIX + "[%s]已领取", taskName);
            return;
        }

        if (TASK_FINISHED.equals(taskStatus)) {
            handleFinishedTask(taskType, taskName, results);
        }
    }

    private static void handleFinishedTask(String taskType, String taskName, List<String> results) {
        try {
            JSONObject response = new JSONObject(AntForestRpcCall.receiveTaskAwardV2(taskType));
            String resultDesc = response.optString("desc");
            results.add(resultDesc);
            String logMessage = "处理成功".equals(resultDesc) ? "领取成功" : "领取结果：" + resultDesc;
            Log.forest(YOUTH_PRIVILEGE_PREFIX + "["+taskName+"]" + logMessage);
        } catch (JSONException e) {
            Log.printStackTrace(TAG + "奖励领取结果解析失败", e);
            results.add("处理异常");
        }
    }

    public static void studentSignInRedEnvelope() {
        try {
            if (!isSignInTimeValid()) {
                Log.runtime(STUDENT_SIGN_PREFIX + "5点前不执行签到");
                return;
            }

            if (Status.hasFlagToday(FLAG_STUDENT_TASK)) {
                Log.runtime(STUDENT_SIGN_PREFIX + "今日已完成签到");
                return;
            }

            processStudentSignIn();
        } catch (Exception e) {
            Log.printStackTrace(TAG + "学生签到异常", e);
        }
    }

    private static boolean isSignInTimeValid() {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return currentHour >= SIGN_IN_START_HOUR;
    }

    private static void processStudentSignIn() throws JSONException {
        String response = AntForestRpcCall.studentQqueryCheckInModel();
        JSONObject result = new JSONObject(response);

        if (!RPC_SUCCESS.equals(result.optString("resultCode"))) {
            Log.runtime(STUDENT_SIGN_PREFIX + "查询失败：" + result.optString("resultDesc"));
            return;
        }

        JSONObject checkInInfo = result.optJSONObject("studentCheckInInfo");
        if (checkInInfo == null || "DO_TASK".equals(checkInInfo.optString("action"))) {
            Status.setFlagToday(FLAG_STUDENT_TASK);
            return;
        }

        executeStudentSignIn();
    }

    private static void executeStudentSignIn() {
        try {
            String tag = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < SIGN_IN_END_HOUR
                    ? "double" : "single";

            JSONObject result = new JSONObject(AntForestRpcCall.studentCheckin());
            handleSignInResult(result, tag);
        } catch (JSONException e) {
            Log.printStackTrace(TAG + "签到结果解析失败", e);
        }
    }

    private static void handleSignInResult(JSONObject result, String tag) {
        String resultCode = result.optString("resultCode");
        String resultDesc = result.optString("resultDesc", "签到成功");

        if (RPC_SUCCESS.equals(resultCode)) {
            Status.setFlagToday("StudentSignIn");
            String logMessage = STUDENT_SIGN_PREFIX + tag + resultDesc;
            Log.forest(logMessage);
        } else {
            String errorMsg = resultDesc.contains("不匹配") ? resultDesc + "可能账户不符合条件" : resultDesc;
            String logMessage = STUDENT_SIGN_PREFIX + tag + "失败：" + errorMsg;
            Log.error(TAG, logMessage);
        }
    }

}
