package fansirsqi.xposed.sesame.task.otherTask2;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.otherTask2.ZhimaTreeRpcCall;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.ResChecker;

public class ZhimaTree extends ModelTask {
    private static final String TAG = ZhimaTree.class.getSimpleName();

    @Override
    public String getName() {
        return "芝麻树";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.SPORTS;
    }

    @Override
    public String getIcon() {
        return "ZhimaTree.png";
    }

    private final BooleanModelField enableZhimaTree = new BooleanModelField("enableZhimaTree", "开启芝麻树任务", false);

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(enableZhimaTree);
        return modelFields;
    }

    @Override
    public boolean check() {
        return enableZhimaTree.getValue();
    }

    public void run() {
        try {
            if (!enableZhimaTree.getValue()) {
                return;
            }

            // 1. 执行首页的所有任务 (包括浏览任务和复访任务)
            doHomeTasks();

            // 2. 执行常规列表任务 (赚净化值列表)
            doRentGreenTasks();

            // 3. 消耗净化值进行净化
            doPurification();

        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 处理首页返回的任务 (含浏览任务和状态列表任务)
     */
    private void doHomeTasks() {
        try {
            String res = ZhimaTreeRpcCall.zhimaTreeHomePage();
            if (res == null) return;

            JSONObject json = new JSONObject(res);
            if (ResChecker.checkRes(TAG, json)) {
                JSONObject result = json.optJSONObject("extInfo");
                if (result == null) return;
                JSONObject queryResult = result.optJSONObject("zhimaTreeHomePageQueryResult");
                if (queryResult == null) return;

                // 1. 处理 browseTaskList (如：芝麻树首页每日_浏览任务)
                JSONArray browseList = queryResult.optJSONArray("browseTaskList");
                if (browseList != null) {
                    for (int i = 0; i < browseList.length(); i++) {
                        processSingleTask(browseList.getJSONObject(i));
                    }
                }

                // 2. 处理 taskStatusList (如：芝麻树复访任务70净化值)
                JSONArray statusList = queryResult.optJSONArray("taskStatusList");
                if (statusList != null) {
                    for (int i = 0; i < statusList.length(); i++) {
                        processSingleTask(statusList.getJSONObject(i));
                    }
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
    }

    /**
     * 处理赚净化值列表任务
     */
    private void doRentGreenTasks() {
        try {
            String res = ZhimaTreeRpcCall.queryRentGreenTaskList();
            if (res == null) return;

            JSONObject json = new JSONObject(res);
            if (ResChecker.checkRes(TAG, json)) {
                JSONObject extInfo = json.optJSONObject("extInfo");
                if (extInfo == null) return;

                JSONObject taskDetailListObj = extInfo.optJSONObject("taskDetailList");
                if (taskDetailListObj == null) return;

                JSONArray tasks = taskDetailListObj.optJSONArray("taskDetailList");
                if (tasks == null) return;

                for (int i = 0; i < tasks.length(); i++) {
                    processSingleTask(tasks.getJSONObject(i));
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
    }

    /**
     * 处理单个任务对象的逻辑
     */
    private void processSingleTask(JSONObject task) {
        try {
            JSONObject taskBaseInfo = task.optJSONObject("taskBaseInfo");
            if (taskBaseInfo == null) return;

            String taskId = taskBaseInfo.optString("appletId");
            // 有些任务ID在taskId字段，有些在appletId，做个兼容
            if (taskId == null || taskId.isEmpty()) {
                taskId = task.optString("taskId");
            }

            String title = taskBaseInfo.optString("appletName");
            if (title.isEmpty()) title = taskBaseInfo.optString("title", taskId);

            String status = task.optString("taskProcessStatus");

            // 过滤掉明显无法自动完成的任务（如包含邀请、下单、开通），但保留复访任务
            if (title.contains("邀请") || title.contains("下单") || title.contains("开通")) {
                return;
            }

            // 解析奖励信息
            String prizeName = getPrizeName(task);

            if ("NOT_DONE".equals(status) || "SIGNUP_COMPLETE".equals(status)) {
                // SIGNUP_COMPLETE 通常表示已报名但未做，或者对于复访任务表示可以去完成
                Log.record("芝麻树🌳[开始任务] " + title + (prizeName.isEmpty() ? "" : " (" + prizeName + ")"));
                if (performTask(taskId, title, prizeName)) {
                    // 任务完成
                }
            } else if ("TO_RECEIVE".equals(status)) {
                // 待领取状态
                if (doTaskAction(taskId, "receive")) {
                    String logMsg = "芝麻树🌳[领取奖励] " + title + " #" + (prizeName.isEmpty() ? "奖励已领取" : prizeName);
                    Log.forest(logMsg); // 输出到 forest
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
    }

    /**
     * 执行任务动作：去完成 -> 等待 -> 领取
     */
    private boolean performTask(String taskId, String title, String prizeName) {
        try {
            // 发送"去完成"指令
            if (doTaskAction(taskId, "send")) {
                int waitTime = 16000; // 默认等待16秒，覆盖大多数浏览任务
                if (title.contains("复访")) waitTime = 3000; // 复访任务通常不需要太久

                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // 发送"领取"指令
                if (doTaskAction(taskId, "receive")) {
                    String logMsg = "芝麻树🌳[完成任务] " + title + " #" + (prizeName.isEmpty() ? "奖励已领取" : prizeName);
                    Log.forest(logMsg); // 这里输出到 forest
                    return true;
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }

    /**
     * 获取任务奖励名称
     */
    private String getPrizeName(JSONObject task) {
        String prizeName = "";
        try {
            JSONArray prizes = task.optJSONArray("validPrizeDetailDTO");
            if (prizes == null || prizes.length() == 0) {
                prizes = task.optJSONArray("prizeDetailDTOList");
            }

            if (prizes != null && prizes.length() > 0) {
                JSONObject prizeBase = prizes.getJSONObject(0).optJSONObject("prizeBaseInfoDTO");
                if (prizeBase != null) {
                    String rawName = prizeBase.optString("prizeName", "");

                    if (rawName.contains("能量")) {
                        Pattern p = Pattern.compile("(森林)?能量(\\d+g?)");
                        Matcher m = p.matcher(rawName);
                        if (m.find()) {
                            prizeName = m.group(0);
                        } else {
                            prizeName = rawName;
                        }
                    } else if (rawName.contains("净化值")) {
                        Pattern p = Pattern.compile("(\\d+净化值|净化值\\d+)");
                        Matcher m = p.matcher(rawName);
                        if (m.find()) {
                            prizeName = m.group(1);
                        } else {
                            prizeName = rawName;
                        }
                    } else {
                        prizeName = rawName;
                    }
                }
            }

            // 如果没找到 PrizeDTO，尝试从 taskExtProps 解析
            if (prizeName.isEmpty()) {
                JSONObject taskExtProps = task.optJSONObject("taskExtProps");
                if (taskExtProps != null && taskExtProps.has("TASK_MORPHO_DETAIL")) {
                    JSONObject detail = new JSONObject(taskExtProps.getString("TASK_MORPHO_DETAIL"));
                    String val = detail.optString("finishOneTaskGetPurificationValue", "");
                    if (!val.isEmpty() && !"0".equals(val)) {
                        prizeName = val + "净化值";
                    }
                }
            }
        } catch (Exception ignore) {}
        return prizeName;
    }

    private boolean doTaskAction(String taskId, String stageCode) {
        try {
            String s = ZhimaTreeRpcCall.rentGreenTaskFinish(taskId, stageCode);
            if (s == null) return false;
            JSONObject json = new JSONObject(s);
            return ResChecker.checkRes(TAG, json);
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
            return false;
        }
    }

    /**
     * 净化逻辑
     */
    private void doPurification() {
        try {
            String homeRes = ZhimaTreeRpcCall.zhimaTreeHomePage();
            if (homeRes == null) return;

            JSONObject homeJson = new JSONObject(homeRes);
            if (!ResChecker.checkRes(TAG, homeJson)) return;

            JSONObject result = homeJson.optJSONObject("extInfo").optJSONObject("zhimaTreeHomePageQueryResult");
            if (result == null) return;

            int currentScore = result.optInt("purificationScore", result.optInt("currentCleanNum", 0));
            String treeCode = "ZHIMA_TREE";
            if (result.has("trees")) {
                JSONArray trees = result.getJSONArray("trees");
                if (trees.length() > 0) {
                    treeCode = trees.getJSONObject(0).optString("treeCode", "ZHIMA_TREE");
                }
            }

            if (currentScore <= 0) {
                return;
            }

            Log.forest("芝麻树🌳[开始净化] 当前净化值: " + currentScore);

            while (currentScore > 0) {
                String cleanRes = ZhimaTreeRpcCall.zhimaTreeCleanAndPush(treeCode);
                if (cleanRes == null) break;

                JSONObject cleanJson = new JSONObject(cleanRes);
                if (ResChecker.checkRes(TAG, cleanJson)) {
                    JSONObject extInfo = cleanJson.optJSONObject("extInfo");

                    currentScore -= 100;

                    int newScore = -1;
                    int growthValue = -1;

                    if (extInfo != null) {
                        // 优先解析 CleanAndPushResult
                        JSONObject cleanResult = extInfo.optJSONObject("zhimaTreeCleanAndPushResult");
                        if (cleanResult != null) {
                            newScore = cleanResult.optInt("purificationScore", -1);
                            JSONObject treeInfo = cleanResult.optJSONObject("currentTreeInfo");
                            if (treeInfo != null) {
                                // 使用 scoreSummary 作为成长值
                                growthValue = treeInfo.optInt("scoreSummary", -1);
                            }
                        } else if (extInfo.has("purificationScore")) {
                            // 兼容旧逻辑或异常情况
                            newScore = extInfo.getInt("purificationScore");
                        }
                    }

                    if (newScore != -1) currentScore = newScore;

                    String growthLog = (growthValue != -1) ? " 当前成长值:" + growthValue : "";
                    Log.forest("芝麻树🌳[净化成功] 剩余净化值:" + Math.max(0, currentScore) + growthLog + "✅");

                    Thread.sleep(1500);
                } else {
                    break;
                }
            }

        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
    }
}