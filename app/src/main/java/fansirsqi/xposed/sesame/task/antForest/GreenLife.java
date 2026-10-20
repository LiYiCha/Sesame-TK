
package fansirsqi.xposed.sesame.task.antForest;

import fansirsqi.xposed.sesame.util.CoroutineUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.ResChecker;
public class GreenLife {
    public static final String TAG = GreenLife.class.getSimpleName();
    /** 森林集市 */
    public static void ForestMarket(String sourceType) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.consultForSendEnergyByAction(sourceType));
            if (ResChecker.checkRes(TAG + "[consultSend]", jo)) {
                JSONObject data = jo.getJSONObject("data");
                if (data.optBoolean("canSendEnergy", false)) {
                    CoroutineUtils.sleepCompat(1000);
                    jo = new JSONObject(AntForestRpcCall.sendEnergyByAction(sourceType));
                    if (ResChecker.checkRes(TAG + "[sendEnergy]", jo)) {
                        data = jo.getJSONObject("data");
                        if (data.optBoolean("canSendEnergy", false)) {
                            int receivedEnergyAmount = data.getInt("receivedEnergyAmount");
                            Log.forest("集市逛街🛍[获得:能量" + receivedEnergyAmount + "g]");
                        }
                    }
                }
            } else {
                Log.runtime(TAG, jo.getJSONObject("data").getString("resultCode"));
                CoroutineUtils.sleepCompat(300);
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "sendEnergyByAction err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /** 森林集市绿色知识打卡：查询→完成→回查确认 */
    public static void checkIn() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.retrieveCurrentMarketActivity());
            if (!ResChecker.checkRes(TAG + "[checkIn]", jo)) return;
            JSONObject data = jo.optJSONObject("data");
            if (data == null || !data.has("currentActivity")) return;
            JSONObject task = data.optJSONObject("currentTask");
            if (task == null || task.optBoolean("checkInCompleted")) return;
            String taskId = task.optString("taskTemplateId");
            if (taskId.isEmpty()) return;

            CoroutineUtils.sleepCompat(1000);
            JSONObject finish = new JSONObject(AntForestRpcCall.finishCurrentMarketTask(taskId));
            if (!ResChecker.checkRes(TAG + "[finishCheckIn]", finish)) {
                Log.error(TAG, "森林集市打卡失败 raw=" + finish);
                return;
            }
            JSONArray prizes = finish.optJSONObject("data") == null ? null : finish.getJSONObject("data").optJSONArray("prizes");
            StringBuilder names = new StringBuilder();
            if (prizes != null) {
                for (int i = 0; i < prizes.length(); i++) {
                    String name = prizes.optJSONObject(i) == null ? "" : prizes.getJSONObject(i).optString("name");
                    if (!name.isEmpty()) names.append(names.length() == 0 ? name : "、" + name);
                }
            }
            Log.forest("森林集市打卡✅" + (names.length() > 0 ? " 奖励：" + names : ""));

            CoroutineUtils.sleepCompat(1000);
            JSONObject again = new JSONObject(AntForestRpcCall.retrieveCurrentMarketActivity());
            JSONObject againTask = again.optJSONObject("data") == null ? null : again.getJSONObject("data").optJSONObject("currentTask");
            if (againTask != null && !againTask.optBoolean("checkInCompleted")) {
                Log.runtime(TAG, "森林集市打卡回查未确认完成，下轮重试");
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "checkIn err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /** 绿色租赁：查询浏览奖励资格→领取能量→回查 */
    public static void collectRentEnergy() {
        try {
            JSONObject chance = new JSONObject(AntForestRpcCall.checkUserSecondSceneChance());
            if (!ResChecker.checkRes(TAG + "[rentChance]", chance)) return;
            JSONObject resultObject = chance.optJSONObject("resultObject");
            if (resultObject != null && resultObject.has("canSendEnergy") && !resultObject.optBoolean("canSendEnergy")) return;

            CoroutineUtils.sleepCompat(1000);
            JSONObject response = new JSONObject(AntForestRpcCall.generateRentEnergy());
            if (!ResChecker.checkRes(TAG + "[rentEnergy]", response)) return;
            JSONObject genObject = response.optJSONObject("resultObject");
            int amount = genObject != null && genObject.optJSONObject("energyGenerated") != null
                    ? genObject.getJSONObject("energyGenerated").optInt("zulinshangpinliulan", 0) : 0;
            Log.forest(amount > 0 ? "绿色租赁浏览产生能量 " + amount + "g" : "绿色租赁浏览已受理，回查奖励资格");

            CoroutineUtils.sleepCompat(1000);
            JSONObject refreshed = new JSONObject(AntForestRpcCall.checkUserSecondSceneChance());
            ResChecker.checkRes(TAG + "[rentRecheck]", refreshed);
        } catch (Throwable t) {
            Log.runtime(TAG, "collectRentEnergy err:");
            Log.printStackTrace(TAG, t);
        }
    }
}
