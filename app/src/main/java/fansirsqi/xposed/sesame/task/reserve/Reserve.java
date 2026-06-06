package fansirsqi.xposed.sesame.task.reserve;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import fansirsqi.xposed.sesame.entity.ReserveEntity;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectAndCountModelField;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.ResUtil;
import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.util.ThreadUtil;
public class Reserve extends ModelTask {
    private static final String TAG = Reserve.class.getSimpleName();
    @Override
    public String getName() {
        return "保护地";
    }
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }
    @Override
    public String getIcon() {
        return "Reserve.png";
    }
    private SelectAndCountModelField reserveList;
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(reserveList = new SelectAndCountModelField("reserveList", "保护地列表", new LinkedHashMap<>(), ReserveEntity::getList));
        return modelFields;
    }
    public boolean check() {
        if (TaskCommon.IS_ENERGY_TIME){
            Log.runtime("⏸ 当前为只收能量时间【"+ BaseModel.getEnergyTime().getValue() +"】，停止执行" + getName() + "任务！");
            return false;
        }else if (TaskCommon.IS_MODULE_SLEEP_TIME) {
            Log.runtime("💤 模块休眠时间【"+ BaseModel.getModelSleepTime().getValue() +"】停止执行" + getName() + "任务！");
            return false;
        } else {
            return true;
        }
    }
    public void run() {
        try {
            Log.runtime("开始保护地任务");
            animalReserve();
        } catch (Throwable t) {
            Log.runtime(TAG, "start.run err:");
            Log.printStackTrace(TAG, t);
        } finally {
            Log.runtime("保护地任务");
        }
    }
    private void animalReserve() {
        try {
            Log.runtime("开始执行-" + getName());
            String s = ReserveRpcCall.queryTreeItemsForExchange();
            if (s == null) {
                ThreadUtil.sleep(RandomUtil.delay());
                s = ReserveRpcCall.queryTreeItemsForExchange();
            }
            JSONObject jo = new JSONObject(s);
            if (ResUtil.checkResultCode(jo)) {
                JSONArray ja = jo.getJSONArray("treeItems");
                for (int i = 0; i < ja.length(); i++) {
                    jo = ja.getJSONObject(i);
                    if (!jo.has("projectType")) {
                        continue;
                    }
                    if (!"RESERVE".equals(jo.getString("projectType"))) {
                        continue;
                    }
                    if (!"AVAILABLE".equals(jo.getString("applyAction"))) {
                        continue;
                    }
                    String projectId = jo.getString("itemId");
                    String itemName = jo.getString("itemName");
                    Map<String, Integer> map = reserveList.getValue();
                    for (Map.Entry<String, Integer> entry : map.entrySet()) {
                        if (Objects.equals(entry.getKey(), projectId)) {
                            Integer count = entry.getValue();
                            if (count != null && count > 0 && Status.canReserveToday(projectId, count)) {
                                exchangeTree(projectId, itemName, count);
                            }
                            break;
                        }
                    }
                }
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "animalReserve err:");
            Log.printStackTrace(TAG, t);
        } finally {
            Log.runtime("结束执行-" + getName());
        }
    }
    private boolean queryTreeForExchange(String projectId) {
        try {
            String s = ReserveRpcCall.queryTreeForExchange(projectId);
            JSONObject jo = new JSONObject(s);
            if (ResUtil.checkResultCode(jo)) {
                String applyAction = jo.getString("applyAction");
                int currentEnergy = jo.getInt("currentEnergy");
                jo = jo.getJSONObject("exchangeableTree");
                if ("AVAILABLE".equals(applyAction)) {
                    if (currentEnergy >= jo.getInt("energy")) {
                        return true;
                    } else {
                        Log.forest("领保护地🏕️[" + jo.getString("projectName") + "]#能量不足停止申请");
                        return false;
                    }
                } else {
                    Log.forest("领保护地🏕️[" + jo.getString("projectName") + "]#似乎没有了");
                    return false;
                }
            } else {
                Log.runtime(jo.getString("resultDesc"));
                Log.runtime(s);
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "queryTreeForExchange err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    private void exchangeTree(String projectId, String itemName, int count) {
        int appliedTimes = 0;
        try {
            String s;
            JSONObject jo;
            boolean canApply = queryTreeForExchange(projectId);
            if (!canApply)
                return;
            for (int applyCount = 1; applyCount <= count; applyCount++) {
                s = ReserveRpcCall.exchangeTree(projectId);
                jo = new JSONObject(s);
                if (ResUtil.checkResultCode(jo)) {
                    int vitalityAmount = jo.optInt("vitalityAmount", 0);
                    appliedTimes = Status.getReserveTimes(projectId) + 1;
                    String str = "领保护地🏕️[" + itemName + "]#第" + appliedTimes + "次"
                            + (vitalityAmount > 0 ? "-活力值+" + vitalityAmount : "");
                    Log.forest(str);
                    Status.reserveToday(projectId, 1);
                } else {
                    Log.runtime(jo.getString("resultDesc"));
                    Log.runtime(jo.toString());
                    Log.forest("领保护地🏕️[" + itemName + "]#发生未知错误，停止申请");
                    // Statistics.reserveToday(projectId, count);
                    break;
                }
                ThreadUtil.sleep(300);
                canApply = queryTreeForExchange(projectId);
                if (!canApply) {
                    // Statistics.reserveToday(projectId, count);
                    break;
                } else {
                    ThreadUtil.sleep(300);
                }
                if (!Status.canReserveToday(projectId, count))
                    break;
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "exchangeTree err:");
            Log.printStackTrace(TAG, t);
        }
    }
}
