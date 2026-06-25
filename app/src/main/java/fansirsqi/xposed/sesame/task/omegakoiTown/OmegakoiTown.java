package fansirsqi.xposed.sesame.task.omegakoiTown;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import fansirsqi.xposed.sesame.data.RuntimeInfo;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.util.Log;
public class OmegakoiTown extends ModelTask {
    private static final String TAG = OmegakoiTown.class.getSimpleName();
    @Override
    public String getName() {
        return "小镇";
    }
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }
    @Override
    public ModelFields getFields() {
        return new ModelFields();
    }
    @Override
    public String getIcon() {
        return "OmegakoiTown.png";
    }
    public enum RewardType {
        gold, diamond, dyestuff, rubber, glass, certificate, shipping, tpuPhoneCaseCertificate,
        glassPhoneCaseCertificate, canvasBagCertificate, notebookCertificate, box, paper, cotton;
        public static final CharSequence[] rewardNames = {
                "金币", "钻石", "颜料", "橡胶",
                "玻璃", "合格证", "包邮券", "TPU手机壳合格证",
                "玻璃手机壳合格证", "帆布袋合格证", "记事本合格证",
                "快递包装盒", "纸张", "棉花"};
        public CharSequence rewardName() {
            return rewardNames[ordinal()];
        }
    }
    public enum HouseType {
        houseTrainStation("火车站"),
        houseStop("停车场"),
        houseBusStation("公交站"),
        houseGas("加油站"),
        houseSchool("学校"),
        houseService("服务大厅"),
        houseHospital("医院"),
        housePolice("警察局"),
        houseBank("银行"),
        houseRecycle("回收站"),
        houseWasteTreatmentPlant("垃圾处理厂"),
        houseMetro("地铁站"),
        houseKfc("快餐店"),
        houseManicureShop("美甲店"),
        housePhoto("照相馆"),
        house5g("移动营业厅"),
        houseGame("游戏厅"),
        houseLucky("运气屋"),
        housePrint("打印店"),
        houseBook("书店"),
        houseGrocery("杂货店"),
        houseScience("科普馆"),
        housemarket1("菜场"),
        houseMcd("汉堡店"),
        houseStarbucks("咖啡厅"),
        houseRestaurant("餐馆"),
        houseFruit("水果店"),
        houseDessert("甜品店"),
        houseClothes("服装店"),
        zhiketang("支课堂"),
        houseFlower("花店"),
        houseMedicine("药店"),
        housePet("宠物店"),
        houseChick("庄园"),
        houseFamilyMart("全家便利店"),
        houseHouse("平房"),
        houseFlat("公寓"),
        houseVilla("别墅"),
        houseResident("居民楼"),
        housePowerPlant("风力发电站"),
        houseWaterPlant("自来水厂"),
        houseDailyChemicalFactory("日化厂"),
        houseToyFactory("玩具厂"),
        houseSewageTreatmentPlant("污水处理厂"),
        houseSports("体育馆"),
        houseCinema("电影院"),
        houseCotton("新疆棉花厂"),
        houseMarket("超市"),
        houseStadium("游泳馆"),
        houseHotel("酒店"),
        housebusiness("商场"),
        houseOrchard("果园"),
        housePark("公园"),
        houseFurnitureFactory("家具厂"),
        houseChipFactory("芯片厂"),
        houseChemicalPlant("化工厂"),
        houseThermalPowerPlant("火电站"),
        houseExpressStation("快递驿站"),
        houseDormitory("宿舍楼"),
        houseCanteen("食堂"),
        houseAdministrationBuilding("行政楼"),
        houseGourmetPalace("美食城"),
        housePaperMill("造纸厂"),
        houseAuctionHouse("拍卖行"),
        houseCatHouse("喵小馆"),
        houseStarPickingPavilion("神秘研究所");
        HouseType(String name) {
        }
    }

    @Override
    public void runJava() {
        try {
            Log.other("开始执行-" + getName());
            RuntimeInfo.getInstance().put("omegakoiTown", System.currentTimeMillis());
            getUserTasks();
            getSignInStatus();
            houseProduct();
        } catch (Throwable t) {
            Log.runtime(TAG, "start.run err:");
            Log.printStackTrace(TAG, t);
        } finally {
            Log.other("结束执行-" + getName());
        }
    }
    private void getUserTasks() {
        try {
            String s = OmegakoiTownRpcCall.getUserTasks();
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject result = jo.getJSONObject("result");
                JSONArray tasks = result.getJSONArray("tasks");
                for (int i = 0; i < tasks.length(); i++) {
                    jo = tasks.getJSONObject(i);
                    boolean done = jo.getBoolean("done");
                    boolean hasRewarded = jo.getBoolean("hasRewarded");
                    if (done && !hasRewarded) {
                        JSONObject task = jo.getJSONObject("task");
                        String name = task.getString("name");
                        String taskId = task.getString("taskId");
                        if ("dailyBuild".equals(taskId))
                            continue;
                        int amount = task.getJSONObject("reward").getInt("amount");
                        String itemId = task.getJSONObject("reward").getString("itemId");
                        try {
                            RewardType rewardType = RewardType.valueOf(itemId);
                            jo = new JSONObject(OmegakoiTownRpcCall.triggerTaskReward(taskId));
                            if (jo.optBoolean("success")) {
                                Log.other("小镇任务🌇[" + name + "]#" + amount + "[" + rewardType.rewardName() + "]");
                            }
                        } catch (Throwable th) {
                            Log.runtime(TAG, "spec RewardType:" + itemId + ";未知的类型");
                        }
                    }
                }
            } else {
                Log.runtime(jo.getString("resultDesc"));
                Log.runtime(s);
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "getUserTasks err:");
            Log.printStackTrace(TAG, t);
        }
    }
    private void getSignInStatus() {
        try {
            String s = OmegakoiTownRpcCall.getSignInStatus();
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                boolean signed = jo.getJSONObject("result").getBoolean("signed");
                if (!signed) {
                    jo = new JSONObject(OmegakoiTownRpcCall.signIn());
                    JSONObject diffItem = jo.getJSONObject("result").getJSONArray("diffItems").getJSONObject(0);
                    int amount = diffItem.getInt("amount");
                    String itemId = diffItem.getString("itemId");
                    RewardType rewardType = RewardType.valueOf(itemId);
                    Log.other("小镇签到[" + rewardType.rewardName() + "]#" + amount);
                }
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "getSignInStatus err:");
            Log.printStackTrace(TAG, t);
        }
    }
    private void houseProduct() {
        try {
            String s = OmegakoiTownRpcCall.houseProduct();
            JSONObject jo = new JSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject result = jo.getJSONObject("result");
                JSONArray userHouses = result.getJSONArray("userHouses");
                for (int i = 0; i < userHouses.length(); i++) {
                    jo = userHouses.getJSONObject(i);
                    JSONObject extraInfo = jo.getJSONObject("extraInfo");
                    if (!extraInfo.has("toBeCollected"))
                        continue;
                    JSONArray toBeCollected = extraInfo.optJSONArray("toBeCollected");
                    if (toBeCollected != null && toBeCollected.length() > 0) {
                        double amount = toBeCollected.getJSONObject(0).getDouble("amount");
                        if (amount < 500)
                            continue;
                        String houseId = jo.getString("houseId");
                        long id = jo.getLong("id");
                        jo = new JSONObject(OmegakoiTownRpcCall.collect(houseId, id));
                        if (jo.optBoolean("success")) {
                            HouseType houseType = HouseType.valueOf(houseId);
                            String itemId = jo.getJSONObject("result").getJSONArray("rewards").getJSONObject(0)
                                    .getString("itemId");
                            RewardType rewardType = RewardType.valueOf(itemId);
                            NumberFormat numberFormat = NumberFormat.getNumberInstance();
                            ((DecimalFormat) numberFormat).applyPattern("#.00");
                            String formattedAmount = numberFormat.format(amount);
                            Log.other("小镇收金🌇[" + houseType.name() + "]#" + formattedAmount
                                    + rewardType.rewardName());
                        }
                    }
                }
            } else {
                Log.runtime(jo.getString("resultDesc"));
                Log.runtime(s);
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "getUserTasks err:");
            Log.printStackTrace(TAG, t);
        }
    }
}
