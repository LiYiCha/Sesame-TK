package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.entity.AlipayUser;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.StringModelField;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import lombok.Getter;

public class OtherTask extends ModelTask {
    private static final String TAG = "🔥其他任务🔥";
    /**
     * 🎉 💼 🏆 🔥 ⭐ 💡 🎯 🚀 🧠  ✅  ❌  ⚠️ 👈
     * 📈 🧾 💵 🏦 💰 🧧 🎁 💸 🌦️  👑 💎 🛡️ 🔑
     * 🌟👑✨💎 🛡️⚔️🔥🎯🌈💫🎵🎶🌠🌞🌙
     * 🌌🎇🎆🪐☄️🌊🏔️🌄🌲🍃🦋🌸🐾🐉🔥🦅 Peak 😎
     * 🎓 毕业帽 - 最直接关联学习和教育
     * 📚 书籍 - 代表知识和学习
     * 📖 打开的书 - 学习和阅读
     * 📝 笔记本 - 学习记录
     * ✏️ 铅笔 - 学习工具
     * 🖊️ 钢笔 - 学习工具
     * 📓 笔记本 - 学习记录
     * 📔 笔记本 - 学习资料
     * 📕 书本 - 知识来源
     * 成就/奖励类符号：
     * 🏅 奖牌 - 成就和荣誉
     * 🏆 奖杯 - 获得的成就
     * 🥇 金牌 - 第一和优秀
     * 🥈 银牌 - 第二名
     * 🥉 铜牌 - 第三名
     * 🏅 军功章 - 成就表彰
     * 🎖️ 勋章 - 荣誉和成就
     * 星级/评分类符号：
     * 📍 标记 - 重点和关注
     * 其他相关符号：
     * 💯 百分 - 满分成绩
     * ✅ 对勾 - 完成和通过
     * 📊 图表 - 成绩和统计
     * 📈 上升图表 - 进步和提升
     * 🏃‍♂️ 跑步的人 - 表示运动、跑步锻炼
     * 🚶‍♂️ 走路的人 - 表示步行、行走运动
     * 🏋️ 举重 - 表示健身锻炼
     * 🚴‍♂️ 骑自行车 - 表示骑行运动
     * 能量相关图标：
     * ⚡ 闪电 - 表示能量、电力
     * 🔋 电池 - 表示能量储存
     * 💪 肌肉 - 表示力量、能量
     * ✨ 闪亮星星 - 表示能量、光芒
     * 结合运动和能量的推荐组合：
     * 🏃‍♂️⚡ - 跑步+闪电（运动产生能量）
     * 🚶‍♂️💪 - 步行+肌肉（运动增强力量）
     * 🏋️✨ - 举重+星星（锻炼释放能量）
     */
    // 固定大小线程池（根据设备CPU核心数调整）
    private static final int CORE_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    private static final ExecutorService executor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            CORE_POOL_SIZE,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100), // 更大任务队列
            new ThreadPoolExecutor.CallerRunsPolicy()
    );



    @Override
    public String getName() {
        return "其他任务";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public String getIcon() {
        return "AntSports.png";
    }

    protected Integer executeIntervalInt = 5000;  // 执行间隔
    private final StringModelField startTime = new StringModelField("startTime", "开始执行时间(关闭:-1)", "0600");
    private final IntegerModelField executeInterval = new IntegerModelField("executeInterval", "执行间隔(毫秒)", executeIntervalInt);
    public final BooleanModelField executePair = new BooleanModelField("executePair", "并行执行", false);
    public final BooleanModelField contentInteract = new BooleanModelField("contentInteract", "看视频领红包", false);
    public final BooleanModelField contentDayTask = new BooleanModelField("contentDayTask", "视频|每日任务", false);
    //-------------------------------------------------------------------------
    //------------------------------------------------------------------------
    @Getter
    private final static BooleanModelField fishpondAngle = new BooleanModelField("fishpondAngle", "福气鱼塘-自动钓鱼", false);
    @Getter
    private static StringModelField fishpondToken = new StringModelField("fishpondToken", "福气鱼塘钓鱼Token", "");
    private final BooleanModelField promoprodRedEnvelope = new BooleanModelField("promoprodRedEnvelope", "实体红包", false);
    private final BooleanModelField fundapplication = new BooleanModelField("fundapplication", "摇红包", false);
    private final BooleanModelField salaryday = new BooleanModelField("salaryday", "红包雨", false);
    private final BooleanModelField yebExpGold = new BooleanModelField("yebExpGold", "体验金", false);
    private final BooleanModelField antFishpond = new BooleanModelField("antFishpond", "福气鱼塘", false);
    private final SelectModelField antFishpondList = new SelectModelField("antFishpondList", "福气鱼塘邀请好友", new LinkedHashSet<>(), AlipayUser::getList);
    private final BooleanModelField hundredTimesDiscountCard = new BooleanModelField("hundredTimesDiscountCard", "百次立减卡", false);
    private final BooleanModelField neverland = new BooleanModelField("neverland", "悦动健康岛", false);
    @Getter
    private final static BooleanModelField neverLandJump = new BooleanModelField("neverLandJump", "悦动健康岛|自动跳一跳", false);
    @Getter
    private final static IntegerModelField neverLandJumpTIme = new IntegerModelField("neverLandJumpTIme", "健康岛|次数", 0);
    @Getter
    private final static BooleanModelField neverLandJumpLess = new BooleanModelField("neverLandJumpLess", "健康岛|不设置完成状态", false);
    private final BooleanModelField luckCode = new BooleanModelField("luckcode", "收益天天乐", false);
    private final BooleanModelField goldbean = new BooleanModelField("goldbean", "天天来财", false);
    private final BooleanModelField goldTicket = new BooleanModelField("goldTicket", "黄金票", false);
    private final BooleanModelField huabeijin = new BooleanModelField("huabeijin", "花呗金", false);
    private final BooleanModelField travelDeals = new BooleanModelField("travelDeals", "出行特惠", false);
    private final BooleanModelField jobRight = new BooleanModelField("jobRight", "就业|积分", false);
    private final BooleanModelField huaCard = new BooleanModelField("hauCard", "花花卡", false);
    private final BooleanModelField luckCard = new BooleanModelField("luckCard", "好运卡", false);
    private final BooleanModelField yebSceneBff = new BooleanModelField("yebSceneBff", "余额宝养鱼", false);
    private final BooleanModelField dayDaySave = new BooleanModelField("dayDaySave", "蛋定生财", false);


    //------------------------------------------------------------------------

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(startTime);  // 开始执行时间
        modelFields.addField(executeInterval); // 执行间隔
        modelFields.addField(executePair); // 并发执行
        //modelFields.addField(contentInteract);  // 看视频领红包
        //modelFields.addField(contentDayTask);  // 视频|每日任务
        //modelFields.addField(contentInteractCount); // 视频线程
        modelFields.addField(antFishpond);  // 鱼塘
        modelFields.addField(fishpondAngle);  // 鱼塘自动钓鱼
        modelFields.addField(antFishpondList);  // 鱼塘邀请好友
        modelFields.addField(fishpondToken);  // 鱼塘token
        modelFields.addField(promoprodRedEnvelope);  // 实体红包
        //modelFields.addField(fundapplication);  // 摇红包
        modelFields.addField(salaryday);  // 红包雨
        modelFields.addField(yebExpGold);  // 体验金
        //modelFields.addField(hundredTimesDiscountCard);  // 百次立减
        modelFields.addField(neverland);  // 悦动健康
        modelFields.addField(neverLandJump);  // 悦动健康跳一跳
        modelFields.addField(neverLandJumpTIme);  // 悦动健康跳一跳 次数
        modelFields.addField(neverLandJumpLess);  // 悦动健康跳一跳 不设置完成状态
        modelFields.addField(luckCode);  // 收益天天乐
        modelFields.addField(goldbean);  // 天天来财
        modelFields.addField(goldTicket);  // 黄金票
        modelFields.addField(huabeijin);  // 花呗金
        modelFields.addField(travelDeals);  // 出行特惠
        modelFields.addField(jobRight);  // 工作中心积分
        modelFields.addField(huaCard);  // 花花卡
        modelFields.addField(luckCard);  // 好运卡
        modelFields.addField(yebSceneBff); // 余额宝养鱼
        modelFields.addField(dayDaySave);// 蛋定生财
        return modelFields;
    }

    @Override
    public boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.runtime("⏸ 当前为只收能量时间【" + BaseModel.getEnergyTime().getValue() + "】，停止执行" + getName() + "任务！");
            return false;
        } else if (TaskCommon.IS_MODULE_SLEEP_TIME) {
            Log.runtime("💤 模块休眠时间【" + BaseModel.getModelSleepTime().getValue() + "】停止执行" + getName() + "任务！");
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void runJava() {
        executeIntervalInt = Math.max(executeInterval.getValue(), executeIntervalInt);

//        // 分组执行任务
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            CompletableFuture.runAsync(() -> executeGroup1(), executor);
//        }else {
//            new Thread(() -> executeGroup1()).start();
//        }
//        TimeUtil.sleep(RandomUtil.nextInt(5000,7000));
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            CompletableFuture.runAsync(() -> executeGroup2(), executor);
//        }else {
//            new Thread(() -> executeGroup2()).start();
//        }
//        TimeUtil.sleep(RandomUtil.nextInt(7000,9000));
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            CompletableFuture.runAsync(() -> executeGroup3(), executor);
//        }else{
//            new Thread(() -> executeGroup3()).start();
//        }
//        // 按优先级顺序执行
//        executeGroup1(); // 高优先级任务
//        executeGroup2(); // 中优先级任务
//        TimeUtil.sleep(8000);
//        executeGroup3(); // 低优先级任务

        //任务组之间完全并行，互不干扰
        if (executePair.getValue()) {
            // 任务组1：青春特权兑换、看视频领红包
            executor.submit(() -> {
                try {
                    executeGroup1();
                } catch (Throwable t) {
                    Log.error(TAG + "任务组1--error:" + t.getMessage());
                    Log.printStackTrace(t);
                }
            });
            TimeUtil.sleep(RandomUtil.nextLong(5500, 9600));
            // 任务组2：花呗金、好运卡等
            executor.submit(() -> {
                try {
                    executeGroup2();
                } catch (Throwable t) {
                    Log.error(TAG + "任务组2--error:" + t.getMessage());
                    Log.printStackTrace(t);
                }
            });
            TimeUtil.sleep(RandomUtil.nextLong(9500, 15600));
            // 任务组3：低优先级任务
            executor.submit(() -> {
                try {
                    executeGroup3();
                } catch (Throwable t) {
                    Log.error(TAG + "任务组3--error:" + t.getMessage());
                    Log.printStackTrace(t);
                }
            });
        }else {
            //顺序-------------------------------------------------------
            // 任务组1：青春特权兑换、看视频领红包
            Future<?> future1 = executor.submit(() -> {
                try {
                    executeGroup1();
                } catch (Throwable t) {
                    Log.error(TAG + "任务组1--error:" + t.getMessage());
                    Log.printStackTrace(t);
                }
            });

            // 任务组2：花呗金、好运卡等
            Future<?> future2 = executor.submit(() -> {
                try {
                    executeGroup2();
                } catch (Throwable t) {
                    Log.error(TAG + "任务组2--error:" + t.getMessage());
                    Log.printStackTrace(t);
                }
            });

            // 任务组3：低优先级任务
            Future<?> future3 = executor.submit(() -> {
                try {
                    executeGroup3();
                } catch (Throwable t) {
                    Log.error(TAG + "任务组3--error:" + t.getMessage());
                    Log.printStackTrace(t);
                }
            });

            // 异步等待任务组完成，不阻塞主线程
            new Thread(() -> {
                try {
                    future1.get(); // 同步等待
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
                try {
                    future2.get(); // 同步等待
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
                try {
                    future3.get(); // 同步等待
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }).start();
        }
    }

    // 组1：青春特权兑换、看视频领红包
    private void executeGroup1() {
        try {

            try {
                // 出行特惠
                if (travelDeals.getValue()) {
                    new TravelDeals().run(executeIntervalInt);
                }
            } catch (Exception e) {
                Log.error(TAG + "出行特惠--error:" + e);
            }
            try {
                // 工作积分红包
                if (jobRight.getValue()) {
                    new JobRight().handle();
                }
            } catch (Exception e) {
                Log.error(TAG + "就业|积分红包--error:" + e);
            }
            try {
                // 悦动健康
                if (neverland.getValue()) {
                    new NeverLand().run(executeIntervalInt, new LinkedHashMap<String, Object>() {{
                        put("neverLandJump", OtherTask.this.neverLandJump.getValue());
                    }});
                }
            } catch (Exception e) {
                Log.error(TAG + "悦动健康--error:" + e);
            }

        } catch (Throwable t) {
            Log.error(TAG + "任务组1--error:" + t.getMessage());
            Log.printStackTrace(t);
        }
    }

    // 组2：
    private void executeGroup2() {
        try {
//            try {
//                // 看视频领红包
//                if (!Status.hasFlagToday(CompletedKeyEnum.VIDEOCOMPLETE.name())) {
//                    if (contentInteract.getValue()) {
//                        new ContentInteract(this).run(executeIntervalInt, new LinkedHashMap<String, Object>() {{
//                            put("contentInteract", OtherTask.this.contentInteract.getValue());
//                        }});
//                    }
//                }
//            } catch (Exception e) {
//                Log.error(TAG + "刷视频领红包--error:" + e);
//            }
//            try {
//                //视频|每日任务
//                if (contentDayTask.getValue()) {
//                    new ContentDayTask().handle();
//                }
//            } catch (Exception e) {
//                Log.error(TAG + "低优先级任务--error:" + e);
//            }
        try {
            // 花呗金
            if (huabeijin.getValue()) {
                new HuaBeiJin().handle();
            }
        } catch (Exception e) {
            Log.error(TAG + "花呗金--error:" + e);
        }
        try {
            // 好运卡
            if (luckCard.getValue()) {
                new LuckCard().run(executeIntervalInt);
            }
        } catch (Exception e) {
            Log.error(TAG + "好运卡--error:" + e);
        }
        try {
            // 花花卡
            if (huaCard.getValue()) {
                if (!Status.hasFlagToday("HuaHuaKa_TaskCompleted")) {
                    new HuaHuaKa().run(executeIntervalInt);
                }
            }
        } catch (Exception e) {
            Log.error(TAG + "花花卡--error:" + e);
        }
        try {
            // 红包雨
            if (salaryday.getValue()) {
                new Salaryday().handle();
            }
        } catch (Exception e) {
            Log.error(TAG + "红包雨--error:" + e);
        }

            try {
                // 鱼塘
                if (antFishpond.getValue().booleanValue()) {
                    new AntFishpond().run(executeIntervalInt.intValue(), new LinkedHashMap<String, Object>() {
                        {
                            put("antFishpondList", OtherTask.this.antFishpondList.getValue());
                            put("fishpondAngle", OtherTask.fishpondAngle.getValue());
                        }
                    });
                }
            } catch (Exception e) {
                Log.error(TAG + "鱼塘--error:" + e);
            }

        try {
            // 实体红包
            if (promoprodRedEnvelope.getValue()) {
                promoprodTaskList();
            }
        } catch (Exception e) {
            Log.error(TAG + "实体红包--error:" + e);
        }
        } catch (Throwable t) {
            Log.error(TAG + "任务组2--error:" + t.getMessage());
            Log.printStackTrace(t);
        }
    }

    // 组3：体验金、百次立减、红包雨、摇红包、实体红包、收益天天乐、黄金票、农场任务、花呗金
    private void executeGroup3() {
        try {
            try {
                // 体验金
                if (yebExpGold.getValue()) {
                    new YebExpGold().handle(executeIntervalInt);
                }
            } catch (Exception e) {
                Log.error(TAG + "体验金--error:" + e);
            }

            try {
                // 余额宝养鱼
                if (yebSceneBff.getValue()) {
                    new YebSceneBffish().handle();
                }
            } catch (Exception e) {
                Log.error(TAG + "余额宝养鱼--error:" + e);
            }

            try {
                // 蛋定生财
                if (dayDaySave.getValue()) {
                    new DayDaySave().handle();
                }
            } catch (Exception e) {
                Log.error(TAG + "蛋定生财--error:" + e);
            }

            try {
                // 百次立减
                if (hundredTimesDiscountCard.getValue()) {
                    new HundredTimesDiscountCard().handle();
                }
            } catch (Exception e) {
                Log.error(TAG + "百次立减--error:" + e);
            }
            try {
                // 摇红包
                if (fundapplication.getValue()) {
                    new FundApplication().handle(executeIntervalInt);
                }
            } catch (Exception e) {
                Log.error(TAG + "摇红包--error:" + e);
            }
            try {
                // 收益天天乐
                if (luckCode.getValue()) {
                    new LuckyCode().handle();
                }
            } catch (Exception e) {
                Log.error(TAG + "收益天天乐--error:" + e);
            }
            try {
                // 天天来财
                if (goldbean.getValue()) {
                    new GoldBean().run();
                }
            } catch (Exception e) {
                Log.error(TAG + "天天来财--error:" + e);
            }

            try {
                // 黄金票
                if (goldTicket.getValue()) {
                    if (!Status.hasFlagToday("GoldTicket_TaskCompleted")) {
                        new GoldTicket().handle();
                    }
                }
            } catch (Exception e) {
                Log.error(TAG + "黄金票--error:" + e);
            }


        } catch (Throwable t) {
            Log.error(TAG + "任务组3--error:" + t.getMessage());
            Log.printStackTrace(t);
        }
    }

    //-----------------------------------------------------------------------
    private void promoprodTaskList() throws JSONException {
        JSONObject jSONObject;
        JSONArray jSONArray;
        int length;
        jSONObject = new JSONObject(OtherTaskRpcCall.queryTaskList());
        if (jSONObject.getBoolean("success") && (length = (jSONArray = jSONObject.getJSONArray("taskDetailList")).length()) != 0) {
            for (int i = 0; i < length; i++) {
                JSONObject jSONObject2 = jSONArray.getJSONObject(i);
                String string = jSONObject2.getString("taskProcessStatus");
                String string2 = jSONObject2.getString("taskType");
                if (!"RECEIVE_SUCCESS".equals(string) && !"TRANSFORMER".equals(string2)) {
                    if (!"SIGNUP_COMPLETE".equals(string)) {
                        JSONObject jSONObject3 = new JSONObject(OtherTaskRpcCall.signup(JsonUtil.getValueByPath(jSONObject2, "taskParticipateExtInfo.gplusItem"), jSONObject2.getString("taskId")));
                        if (!jSONObject3.getBoolean("success")) {
                            Log.error(TAG + ".queryTaskList.signup" + jSONObject3.optString("errorMsg"));
                        }
                        TimeUtil.sleep(executeIntervalInt);
                    }
                    JSONObject jSONObject4 = new JSONObject(OtherTaskRpcCall.complete(jSONObject2.getString("taskId")));
                    if (!jSONObject4.getBoolean("success")) {
                        Log.error(TAG + ".queryTaskList.complete" + jSONObject4.optString("errorMsg"));
                    } else {
                        Log.other("实体红包🍷获取[" + JsonUtil.getValueByPath(jSONObject4, "appletBaseConfigDTO.appletName") + "]" + JsonUtil.getValueByPath(jSONObject4, "prizeSendInfo.price.prizePrice") + "元");
                        TimeUtil.sleep(executeIntervalInt);
                    }
                }
            }
        }
    }
    //-----------------------------------------------------------------------

}
