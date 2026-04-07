package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONObject;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class DayDaySave extends BaseCommTask {

    @Override
    protected void handle() {
        if (!Status.hasFlagToday(CompletedKeyEnum.DayDaySave.name())) {
            index();
            collection();
            Status.setFlagToday(CompletedKeyEnum.DayDaySave.name());
        }
    }

    public DayDaySave() {
        this.displayName = "蛋定生财💸";
    }

    private void index() {
        try {
            JSONObject response = requestString("com.alipay.ficcscenepromobff.needle.daydaysave.index", "\"bizScenario\": \"FL\"");
            if (response != null) {
                JSONObject result = response.getJSONObject("result");
                // 处理签到
                if (!(result.optBoolean("hasSignIn") || requestStringAllNew("com.alipay.ficcscenepromobff.needle.daydaysave.signIn", "[null]") == null)) {
                    Log.other(this.displayName + "签到成功");
                }

                // 处理任务能量
                Object taskEnergyObj = JsonUtil.getValueByPathObject(result, "energyBubbleList.taskEnergy");
                if (taskEnergyObj != null) {
                    JSONArray taskEnergyArray = (JSONArray) taskEnergyObj;

                    for (int i = 0; i < taskEnergyArray.length(); i++) {
                        TimeUtil.sleep(RandomUtil.nextInt(3000,5000));
                        JSONObject taskItem = taskEnergyArray.getJSONObject(i);
                        String taskId = taskItem.getString("taskId");
                        String taskStatus = taskItem.getString("status");
                        String taskType = taskItem.getString("taskType");
                        JSONObject modalConfig = taskItem.optJSONObject("modalConfig");
                        String modalTitle = modalConfig.optString("modalTitle");

                        // 只处理非"to_receive"状态的任务
                        if ("not_done".equals(taskStatus) && taskType.equals("gyg")) {
                            // 完成任务
                            String comParams = "[{\"playActionCode\":\"TASK_COMPLETE\",\"playEntrance\":\"STABLE_INTERACT_TASK_LIST\",\"taskId\":\""+taskId+"\"}]";
                            JSONObject completeResult = new JSONObject(RequestManager.requestString("com.alipay.ficcscenepromobff.promosdk2024.task.complete", comParams));

                            if (completeResult.optBoolean("success")) {
                                Log.other(this.displayName + "完成[" + modalTitle + "]");
                            } else {
                                Log.error(this.displayName + "[.index]完成任务失败/活动不存在:"+completeResult);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.error(this.displayName + "[.index]异常"+th);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private void collection() {
        try {
            JSONObject response = requestString("com.alipay.ficcscenepromobff.needle.daydaysave.index", "\"bizScenario\": \"FL\"");
            if (response != null) {
                JSONObject result = response.getJSONObject("result");
                Object normalEnergyObj = JsonUtil.getValueByPathObject(result, "energyBubbleList.normalEnergy");

                if (normalEnergyObj != null) {
                    JSONArray amountList = new JSONArray();
                    JSONArray voucherIdList = new JSONArray();
                    JSONArray nameList = new JSONArray();

                    JSONArray normalEnergyArray = (JSONArray) normalEnergyObj;
                    for (int i = 0; i < normalEnergyArray.length(); i++) {
                        JSONObject energyItem = normalEnergyArray.getJSONObject(i);
                        amountList.put(energyItem.getString("amount"));
                        voucherIdList.put(energyItem.getString("id"));
                        nameList.put(energyItem.getString("name"));
                    }

                    if (voucherIdList.length() != 0) {
                        JSONObject progress = result.getJSONObject("progress");
                        JSONObject collectionParams = new JSONObject();
                        collectionParams.put("amountList", amountList);
                        collectionParams.put("isAdvancedUser", true);
                        collectionParams.put("prePhases", progress);
                        collectionParams.put("voucherIdList", voucherIdList);

                        String paramsStr = collectionParams.toString().substring(1, collectionParams.toString().length() - 1);

                        if (requestString("com.alipay.ficcscenepromobff.needle.daydaysave.collection", paramsStr) != null) {
                            Log.other(this.displayName + "领取奖励[" + nameList + "]+" + amountList);
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.error(this.displayName + "[.collection]异常"+ th);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }

    private void peakIndex() {
        try {
            JSONObject response = requestString("com.alipay.ficcscenepromobff.needle.daydaysavePeak.index", "\"bizScenario\": \"Dianfengsai_zhoucubanner\"");
            if (response != null) {
                JSONObject result = response.getJSONObject("result");
                Object taskListObj = JsonUtil.getValueByPathObject(result, "taskList");

                if (taskListObj != null) {
                    JSONArray taskList = (JSONArray) taskListObj;
                    String actionCode = "TASK_COMPLETE";
                    String entrance = "STABLE_INTERACT_RANKING_TASK_LIST";

                    for (int i = 0; i < taskList.length(); i++) {
                        JSONObject taskItem = taskList.getJSONObject(i);
                        String taskId = taskItem.getString("taskId");
                        String taskStatus = taskItem.getString("status");
                        String taskTitle = taskItem.getString("title");

                        // 构建参数
                        String params = "\"playEntrance\": \"" + entrance + "\",\"taskId\": \"" + taskId + "\",\"playActionCode\": \"" + actionCode + "\"";
                        JSONObject completeResult = requestString("com.alipay.ficcscenepromobff.promosdk2024.task.complete", params);

                        if (completeResult != null) {
                            Log.other(this.displayName + "完成巅峰赛任务[" + taskTitle + "]");
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.error(this.displayName + "[.peakIndex]异常"+th);
        } finally {
            TimeUtil.sleep((long) this.executeIntervalInt);
        }
    }


}