//package fansirsqi.xposed.sesame.task.antOrchard;
//import android.annotation.SuppressLint;
//import android.util.Base64;
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.util.Arrays;
//import java.util.List;
//import java.net.URLDecoder;
//import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
//import java.util.LinkedHashSet;
//import java.util.Set;
//
//import fansirsqi.xposed.sesame.entity.AlipayUser;
//import fansirsqi.xposed.sesame.model.BaseModel;
//import fansirsqi.xposed.sesame.model.ModelFields;
//import fansirsqi.xposed.sesame.model.ModelGroup;
//import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
//import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
//import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField;
//import fansirsqi.xposed.sesame.task.ModelTask;
//import fansirsqi.xposed.sesame.task.TaskCommon;
//import fansirsqi.xposed.sesame.util.Files;
//import fansirsqi.xposed.sesame.util.GlobalThreadPools;
//import fansirsqi.xposed.sesame.util.Log;
//import fansirsqi.xposed.sesame.util.TimeUtil;
//import fansirsqi.xposed.sesame.util.WuaUtilV2;
//import fansirsqi.xposed.sesame.util.maps.UserMap;
//import fansirsqi.xposed.sesame.util.RandomUtil;
//import fansirsqi.xposed.sesame.data.Status;
//import fansirsqi.xposed.sesame.util.ResChecker;
//import fansirsqi.xposed.sesame.data.StatusFlags;
//import fansirsqi.xposed.sesame.hook.internal.SecurityBodyHelper;
//import fansirsqi.xposed.sesame.util.Notify;
//import fansirsqi.xposed.sesame.newutil.TaskBlacklist;
//import fansirsqi.xposed.sesame.util.CoroutineUtils;
//
//public class AntOrchard extends ModelTask {
//  private static final String TAG = "🌱农场";
//  private String userId;
//  private String treeLevel;
//  private String[] wuaList;
//  private Integer executeIntervalInt;
//  private IntegerModelField executeInterval = new IntegerModelField("executeInterval", "执行间隔(毫秒)", 5000);
//  private BooleanModelField receiveOrchardTaskAward = new BooleanModelField("receiveOrchardTaskAward", "收取农场任务奖励", false);
//  private IntegerModelField orchardSpreadManureCount = new IntegerModelField("orchardSpreadManureCount", "农场每日施肥次数", 0);
//  private BooleanModelField batchHireAnimal = new BooleanModelField("batchHireAnimal", "一键捉鸡除草", false);
//  private SelectModelField dontHireList = new SelectModelField("dontHireList", "除草 | 不雇佣好友列表", new LinkedHashSet<>(), AlipayUser::getList);
//  private SelectModelField dontWeedingList = new SelectModelField("dontWeedingList", "除草 | 不除草好友列表", new LinkedHashSet<>(), AlipayUser::getList);
//  private SelectModelField assistFriendList = new SelectModelField("assistFriendList", "助力好友列表", new LinkedHashSet<>(), AlipayUser::getList);
//  // 助力好友列表
//  /**
//   * 获取任务名称
//   *
//   * @return 农场任务名称
//   */
//  @Override
//  public String getName() {
//    return "农场";
//  }
//  /**
//   * 获取任务分组
//   *
//   * @return 果园分组
//   */
//  @Override
//  public ModelGroup getGroup() {
//    return ModelGroup.ORCHARD;
//  }
//  /**
//   * 获取任务图标
//   *
//   * @return 农场任务图标文件名
//   */
//  @Override
//  public String getIcon() {
//    return "AntOrchard.png";
//  }
//  @Override
//  public ModelFields getFields() {
//    ModelFields modelFields = new ModelFields();
//    modelFields.addField(executeInterval);
//    modelFields.addField(receiveOrchardTaskAward);
//    modelFields.addField(orchardSpreadManureCount);
//    modelFields.addField(assistFriendList);
//    //modelFields.addField(batchHireAnimal);
//    //modelFields.addField(dontHireList);
//    //modelFields.addField(dontWeedingList);
//    return modelFields;
//  }
//  /**
//   * 检查任务是否可以执行
//   *
//   * @return 是否可以执行农场任务
//   */
//  @Override
//  public Boolean check() {
//    if (TaskCommon.IS_ENERGY_TIME){
//      Log.runtime(TAG,"⏸ 当前为只收能量时间【"+ BaseModel.getEnergyTime().getValue() +"】，停止执行" + getName() + "任务！");
//      return false;
//    }else if (TaskCommon.IS_MODULE_SLEEP_TIME) {
//      Log.runtime(TAG,"💤 模块休眠时间【"+ BaseModel.getModelSleepTime().getValue() +"】停止执行" + getName() + "任务！");
//      return false;
//    } else {
//      return true;
//    }
//  }
//  /**
//   * 执行农场任务的主要逻辑
//   */
//  @Override
//  public void runJava() {
//    try {
//      Log.runtime(TAG,"执行开始-" + getName());
//      if (executeInterval != null) {
//        executeIntervalInt = Math.max(executeInterval.getValue(), 500);
//      } else {
//        executeIntervalInt = 500; // 默认值
//        //Log.runtime(TAG, "executeInterval字段为null，使用默认执行间隔500ms");
//      }
//      String s = AntOrchardRpcCall.orchardIndex();
//      // 用于获取农场游戏中心(更新敲金蛋情况）
//      Thread.sleep(1000);
//      AntOrchardRpcCall.newQueryGameCenter();
//
//      JSONObject jo = new JSONObject(s);
//      if ("100".equals(jo.getString("resultCode"))) {
//        if (jo.optBoolean("userOpenOrchard")) {
//          JSONObject taobaoData = new JSONObject(jo.getString("taobaoData"));
//          treeLevel = Integer.toString(taobaoData.getJSONObject("gameInfo").getJSONObject("plantInfo").getJSONObject("seedStage").getInt("stageLevel"));
//          //JSONObject joo = new JSONObject(AntOrchardRpcCall.mowGrassInfo()); //查看除草信息
//            userId = UserMap.getCurrentUid();
//            if (jo.has("lotteryPlusInfo")) drawLotteryPlus(jo.getJSONObject("lotteryPlusInfo"));
//            extraInfoGet();
//
//            //如果有🥚 则进行砸🥚
//            JSONObject goldenEggInfo = jo.getJSONObject("goldenEggInfo");
//            int unsmashedGoldenEggs = goldenEggInfo.getInt("unsmashedGoldenEggs");
//            if (unsmashedGoldenEggs > 0) {
//                smashedGoldenEgg(unsmashedGoldenEggs);
//            }else {
//              // 砸金蛋 - 使用专门的接口获取金蛋信息
//                checkAndSmashGoldenEgg();
//            }
//
////            if (batchHireAnimal.getValue()) {
////              if (!joo.optBoolean("hireCountOnceLimit", true) && !joo.optBoolean("hireCountOneDayLimit", true)) batchHireAnimalRecommend();
////            }
//            if (receiveOrchardTaskAward != null && receiveOrchardTaskAward.getValue()) {
//              doOrchardDailyTask(userId);
//              handleAdTask();
//              triggerTbTask();
//            }
//            // 回访奖励
//            if (!Status.hasFlagToday(StatusFlags.FLAG_ANTORCHARD_WIDGET_DAILY_AWARD)) {
//              receiveOrchardVisitAward();
//            }
//            // 限时挑战
//            limitedTimeChallenge();
//
//            int orchardSpreadManureCountValue = (orchardSpreadManureCount != null) ? orchardSpreadManureCount.getValue() : 0;
//            if (orchardSpreadManureCountValue > 0 && Status.canSpreadManureToday(userId)) orchardSpreadManure();
//            if (orchardSpreadManureCountValue >= 3 && orchardSpreadManureCountValue < 10) {
//              querySubplotsActivity(3);
//            } else if (orchardSpreadManureCountValue >= 10) {
//              querySubplotsActivity(10);
//            }
//            // 助力
//            orchardAssistFriend();
//        } else {
//          getEnableField().setValue(false);
//          Log.other("请先开启芭芭农场！");
//        }
//      } else {
//        Log.runtime(TAG, jo.getString("resultDesc"));
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "start.run err:");
//      Log.printStackTrace(TAG, t);
//    }finally {
//      Log.runtime(TAG,"执行结束-" + getName());
//    }
//  }
//  private String getWua() {
//    // 生成标准WUA（使用当前时间戳）
//      return WuaUtilV2.generate();
//  }
//
//  private void orchardSpreadManure() {
//    try {
//      List<String> sourceList = Arrays.asList(
//        "DNHZ_NC_zhimajingnangSF",
//        "widget_shoufei",
//        "ch_appcenter__chsub_9patch"
//      );
//      int loopCount = 0;
//
//      // 获取今日已施肥次数
//      Integer totalWateredObj = Status.getIntFlagToday(StatusFlags.FLAG_ANTORCHARD_SpreadManure_Count);
//      int totalWatered = (totalWateredObj != null) ? totalWateredObj : 0;
//
//      // 检查是否已达到目标
//      if (totalWatered >= orchardSpreadManureCount.getValue()) {
//        Log.runtime(TAG, "今日已完成施肥目标：" + totalWatered + "/" + orchardSpreadManureCount.getValue());
//        return;
//      }
//
//      Log.runtime(TAG, "开始施肥任务，当前进度：" + totalWatered + "/" + orchardSpreadManureCount.getValue());
//
//      do {
//        try {
//          loopCount++;
//          if (loopCount > 20) {
//            Log.runtime(TAG, "循环次数达到上限 " + loopCount + "，避免任务时间过长");
//            return;
//          }
//
//          // 获取果园数据
//          JSONObject orchardIndexData = new JSONObject(AntOrchardRpcCall.orchardIndex());
//          if (!"100".equals(orchardIndexData.getString("resultCode"))) {
//            Log.error(TAG, orchardIndexData.getString("resultDesc"));
//            return;
//          }
//
//          JSONObject orchardTaobaoData = new JSONObject(orchardIndexData.getString("taobaoData"));
//          JSONObject gameInfo = orchardTaobaoData.getJSONObject("gameInfo");
//          JSONObject plantInfo = gameInfo.getJSONObject("plantInfo");
//
//          // 检查是否可以兑换
//          if (plantInfo.getBoolean("canExchange")) {
//            Log.farm("🎉 农场果树可兑换！");
//            Notify.sendNewNotification("芝麻粒TK提醒您：", "🎉 农场果树可兑换！");
//            return;
//          }
//
//          JSONObject seedStage = plantInfo.getJSONObject("seedStage");
//          treeLevel = String.valueOf(seedStage.getInt("stageLevel"));
//
//          JSONObject accountInfo = gameInfo.getJSONObject("accountInfo");
//          int happyPoint = accountInfo.getInt("happyPoint");
//          int wateringCost = accountInfo.getInt("wateringCost");
//          int wateringLeftTimes = accountInfo.getInt("wateringLeftTimes");
//
//          if (happyPoint < wateringCost) {
//            Log.runtime(TAG, "肥料不足: 当前 " + happyPoint + " < 消耗 " + wateringCost);
//            return;
//          }
//
//          if (wateringLeftTimes <= 0) {
//            Log.runtime(TAG, "今日剩余施肥次数为 0");
//            return;
//          }
//
//          int remainingTarget = orchardSpreadManureCount.getValue() - totalWatered;
//          if (remainingTarget <= 0) {
//            Log.runtime(TAG, "已达今日施肥目标：" + totalWatered + "/" + orchardSpreadManureCount.getValue());
//            return;
//          }
//
//          // 无需更改，一键施肥5次只加100明日奖励，施肥1次也加100，为了增加农场奖励，暂时不使用快速模式
//          boolean useQuickWater = false;
//          int actualWaterTimes = 1;
//
//          String wua = SecurityBodyHelper.getSecurityBodyData(4).toString();
//          String randomSource = sourceList.get(RandomUtil.nextInt(0, sourceList.size()));
//
//          JSONObject spreadManureData = new JSONObject(
//            AntOrchardRpcCall.orchardSpreadManure(wua, randomSource, useQuickWater)
//          );
//
//          if (!"100".equals(spreadManureData.getString("resultCode"))) {
//            Log.error(TAG, "农场施肥失败: " + spreadManureData.getString("resultDesc"));
//            return;
//          }
//
//          JSONObject spreadTaobaoData = new JSONObject(spreadManureData.getString("taobaoData"));
//          JSONObject currentStage = spreadTaobaoData.getJSONObject("currentStage");
//          double stageLevel = currentStage.getDouble("stageLevel");
//          double stageMaxLevel = currentStage.getDouble("stageMaxLevel");
//          double currentLevelProgressPercentage = currentStage.getDouble("currentLevelProgressPercentage");
//          String stageText = currentStage.getString("stageText");
//          int dailyAppWateringCount = spreadTaobaoData.getJSONObject("statistics").getInt("dailyAppWateringCount");
//
//          // 累加施肥次数
//          totalWatered += actualWaterTimes;
//          if (dailyAppWateringCount > 0) {
//            totalWatered = dailyAppWateringCount;
//          }
//          Status.setIntFlagToday(StatusFlags.FLAG_ANTORCHARD_SpreadManure_Count, dailyAppWateringCount);
//
//          String waterMethod = useQuickWater ? "x" + actualWaterTimes : "x1";
//          Log.farm("农场施肥💩[" + waterMethod + "] " + stageText + "|累计:" + totalWatered + " 今日:" + dailyAppWateringCount);
//
//          // 检查果树成长上限
//          if (stageLevel >= stageMaxLevel && currentLevelProgressPercentage >= 100.0) {
//            Log.runtime(TAG, "果树已达成长上限，停止施肥");
//            return;
//          }
//
//        } finally {
//          CoroutineUtils.sleepCompat(executeIntervalInt);
//        }
//      } while (totalWatered < orchardSpreadManureCount.getValue());
//
//      Log.runtime(TAG, "施肥任务完成，总计施肥: " + totalWatered + "/" + orchardSpreadManureCount.getValue());
//
//    } catch (Throwable t) {
//      Log.runtime(TAG, "orchardSpreadManure err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//  private void extraInfoGet() {
//    try {
//      String s = AntOrchardRpcCall.extraInfoGet();
//      JSONObject jo = new JSONObject(s);
//      if ("100".equals(jo.getString("resultCode"))) {
//        JSONObject data = jo.optJSONObject("data");
//        if (data == null) return;
//        JSONObject extraData = data.optJSONObject("extraData");
//        if (extraData == null) return;
//        JSONObject fertilizerPacket = extraData.optJSONObject("fertilizerPacket");
//        if (fertilizerPacket == null) return;
//
//        if (!"todayFertilizerWaitTake".equals(fertilizerPacket.optString("status"))) return;
//        int todayFertilizerNum = fertilizerPacket.optInt("todayFertilizerNum", 0);
//        JSONObject setResponse = new JSONObject(AntOrchardRpcCall.extraInfoSet());
//        if ("100".equals(setResponse.getString("resultCode"))) {
//          Log.farm("每日肥料💩[" + todayFertilizerNum + "g]");
//        } else {
//          Log.error(TAG, setResponse.toString());
//        }
//      } else {
//        Log.error(TAG, jo.toString());
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "extraInfoGet err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//  private void drawLotteryPlus(JSONObject lotteryPlusInfo) {
//    try {
//      if (!lotteryPlusInfo.has("userSevenDaysGiftsItem")) return;
//
//      String itemId = lotteryPlusInfo.getString("itemId");
//      JSONObject jo = lotteryPlusInfo.getJSONObject("userSevenDaysGiftsItem");
//      JSONArray ja = jo.getJSONArray("userEverydayGiftItems");
//
//      for (int i = 0; i < ja.length(); i++) {
//        JSONObject jo2 = ja.getJSONObject(i);
//        if (jo2.getString("itemId").equals(itemId)) {
//          if (!jo2.getBoolean("received")) {
//            JSONObject jo3 = new JSONObject(AntOrchardRpcCall.drawLottery());
//            if ("100".equals(jo3.getString("resultCode"))) {
//              JSONArray userEverydayGiftItems = jo3.getJSONObject("lotteryPlusInfo")
//                .getJSONObject("userSevenDaysGiftsItem")
//                .getJSONArray("userEverydayGiftItems");
//
//              for (int j = 0; j < userEverydayGiftItems.length(); j++) {
//                JSONObject jo4 = userEverydayGiftItems.getJSONObject(j);
//                if (jo4.getString("itemId").equals(itemId)) {
//                  int awardCount = jo4.optInt("awardCount", 1);
//                  Log.farm("七日礼包🎁[获得肥料]#" + awardCount + "g");
//                  break;
//                }
//              }
//            } else {
//              Log.runtime(TAG, jo3.toString());
//            }
//          } else {
//            Log.runtime(TAG, "七日礼包已领取");
//          }
//          break;
//        }
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "drawLotteryPlus err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 检查并砸金蛋
//   * 使用orchardIndexEgg接口获取金蛋信息
//   */
//  private void checkAndSmashGoldenEgg() {
//    try {
//      String response = AntOrchardRpcCall.orchardIndexEgg();
//      JSONObject jo = new JSONObject(response);
//
//      if (!"100".equals(jo.optString("resultCode"))) {
//        Log.runtime(TAG, "获取金蛋信息失败: " + jo.optString("resultDesc"));
//        return;
//      }
//
//      if (!jo.has("goldenEggInfo")) {
//        //Log.runtime(TAG, "暂无金蛋可砸");
//        return;
//      }
//
//      JSONObject goldenEggInfo = jo.getJSONObject("goldenEggInfo");
//      int unsmashedGoldenEggs = goldenEggInfo.optInt("unsmashedGoldenEggs", 0);
//
//      if (unsmashedGoldenEggs > 0) {
//        Log.runtime(TAG, "检测到金蛋🥚数量: " + unsmashedGoldenEggs);
//        smashedGoldenEgg(unsmashedGoldenEggs);
//      } else {
//        Log.runtime(TAG, "暂无金蛋可砸");
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "checkAndSmashGoldenEgg err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 砸金蛋功能
//   * @param count 可砸蛋数量
//   */
//  private void smashedGoldenEgg(int count) {
//    try {
//      String response = AntOrchardRpcCall.smashedGoldenEgg(count);
//      JSONObject jo = new JSONObject(response);
//
//      if (ResChecker.checkRes(TAG, jo)) {
//        // 解析 batchSmashedList
//        JSONArray batchSmashedList = jo.getJSONArray("batchSmashedList");
//        for (int i = 0; i < batchSmashedList.length(); i++) {
//          JSONObject smashedItem = batchSmashedList.getJSONObject(i);
//          int manureCount = smashedItem.optInt("manureCount", 0);
//          boolean jackpot = smashedItem.optBoolean("jackpot", false);
//
//          // 输出信息
//          Log.farm(TAG, "砸出肥料 🎖️: " + manureCount + " g" + (jackpot ? "（触发大奖）" : ""));
//        }
//
//        /*
//         // 可选：输出 goldenEggInfoVO 状态
//         JSONObject goldenEggInfo = jo.optJSONObject("goldenEggInfoVO");
//         if (goldenEggInfo != null) {
//             int smashedGoldenEggs = goldenEggInfo.optInt("smashedGoldenEggs", 0);
//             int unsmashedGoldenEggs = goldenEggInfo.optInt("unsmashedGoldenEggs", 0);
//             Log.forest(TAG, "已砸蛋: " + smashedGoldenEggs + ", 剩余可砸蛋: " + unsmashedGoldenEggs);
//         }
//         */
//
//      } else {
//        Log.runtime(TAG, jo.optString("resultDesc", "未知错误"));
//        Log.runtime(TAG, response);
//      }
//
//    } catch (Throwable t) {
//      Log.runtime(TAG, "smashedGoldenEgg err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//  private void doOrchardDailyTask(String userId) {
//    try {
//      String s = AntOrchardRpcCall.orchardListTask();
//      JSONObject jo = new JSONObject(s);
//      if (!"100".equals(jo.getString("resultCode"))) {
//        Log.runtime(jo.getString("resultCode"));
//        Log.runtime(s);
//        return;
//      }
//
//      // 处理签到任务
//      handleSignTask(jo);
//
//      // 处理普通任务列表
//      JSONArray jaTaskList = jo.getJSONArray("taskList");
//      for (int i = 0; i < jaTaskList.length(); i++) {
//        JSONObject task = jaTaskList.getJSONObject(i);
//        if (!"TODO".equals(task.getString("taskStatus"))) continue;
//
//        processTask(userId, task);
//        sleepAdTask();
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "doOrchardDailyTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 处理签到任务
//   * @param jo 任务数据
//   */
//  private void handleSignTask(JSONObject jo) {
//    try {
//      if (jo.has("signTaskInfo") && !Status.hasFlagToday("orchardSign")) {
//        JSONObject signTaskInfo = jo.getJSONObject("signTaskInfo");
//        orchardSign(signTaskInfo);
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "handleSignTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 处理单个任务
//   * @param userId 用户ID
//   * @param task 任务对象
//   */
//  private void processTask(String userId, JSONObject task) {
//    try {
//      String title = task.getJSONObject("taskDisplayConfig").optString("title", "未知任务");
//
//      // 根据不同任务类型进行处理
//      if (isActionTask(task)) {
//        handleActionTask(userId, task, title);
//      } else if ("ORCHARD_NORMAL_TAB3_NEW".equals(task.optString("groupId"))) {
//        handleVideoTask(userId, task, title);
//      } else if ("ZHUFANG3IN1".equals(task.optString("groupId"))) {
//        handleComponentVisitTask(task, title);
//      }
//
//      // 处理包含"玩30s"描述的任务
//      handlePlay30sTask(userId, task, title);
//    } catch (Throwable t) {
//      Log.runtime(TAG, "processTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 判断是否为动作类型任务
//   * @param task 任务对象
//   * @return 是否为动作类型任务
//   */
//  private boolean isActionTask(JSONObject task) throws JSONException {
//    String actionType = task.getString("actionType");
//    return "TRIGGER".equals(actionType) ||
//            "ADD_HOME".equals(actionType) ||
//            "PUSH_SUBSCRIBE".equals(actionType);
//  }
//
//  /**
//   * 处理动作类型任务
//   * @param userId 用户ID
//   * @param task 任务对象
//   * @param title 任务标题
//   */
//  private void handleActionTask(String userId, JSONObject task, String title) {
//    try {
//      String taskId = task.getString("taskId");
//      String sceneCode = task.getString("sceneCode");
//      JSONObject result = new JSONObject(AntOrchardRpcCall.finishTask(userId, sceneCode, taskId));
//      logTaskResult(result, title);
//    } catch (Throwable t) {
//      Log.runtime(TAG, "handleActionTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 处理视频浏览任务
//   * @param userId 用户ID
//   * @param task 任务对象
//   * @param title 任务标题
//   */
//  private void handleVideoTask(String userId, JSONObject task, String title) {
//    try {
//      String taskId = task.getString("taskId");
//      String sceneCode = task.getString("sceneCode");
//      JSONObject result = new JSONObject(AntOrchardRpcCall.finishTask(userId, sceneCode, taskId));
//      logTaskResult(result, title);
//    } catch (Throwable t) {
//      Log.runtime(TAG, "handleVideoTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 处理组件访问任务
//   * @param task 任务对象
//   * @param title 任务标题
//   */
//  private void handleComponentVisitTask(JSONObject task, String title) {
//    try {
//      JSONObject result = new JSONObject(AntOrchardRpcCall.receiveOrchardVisitAward());
//      logTaskResult(result, title);
//    } catch (Throwable t) {
//      Log.runtime(TAG, "handleComponentVisitTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 处理包含"玩30s"描述的任务
//   * @param userId 用户ID
//   * @param task 任务对象
//   * @param title 任务标题
//   */
//  private void handlePlay30sTask(String userId, JSONObject task, String title) {
//    try {
//      JSONObject taskDisplayConfig = task.getJSONObject("taskDisplayConfig");
//      String desc = taskDisplayConfig.optString("desc", "");
//      if (desc.contains("玩30s")) {
//        String taskId = task.getString("taskId");
//        String sceneCode = task.getString("sceneCode");
//        JSONObject result = new JSONObject(AntOrchardRpcCall.finishTask(userId, sceneCode, taskId));
//        logTaskResult(result, title);
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "handlePlay30sTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 记录任务执行结果
//   * @param result 任务执行结果
//   * @param title 任务标题
//   */
//  private void logTaskResult(JSONObject result, String title) {
//    try {
//      if (result.optBoolean("success")) {
//        Log.farm("农场任务🧾[" + title + "]");
//      } else {
//        Log.runtime(result.getString("desc"));
//        Log.runtime(result.toString());
//      }
//    } catch (JSONException e) {
//      Log.runtime(TAG, "logTaskResult err:");
//      Log.printStackTrace(TAG, e);
//    }
//  }
//
//  private void orchardSign(JSONObject signTaskInfo) {
//    try {
//      JSONObject currentSignItem = signTaskInfo.getJSONObject("currentSignItem");
//      if (!currentSignItem.getBoolean("signed")) {
//        JSONObject joSign = new JSONObject(AntOrchardRpcCall.orchardSign());
//        if ("100".equals(joSign.getString("resultCode"))) {
//          int awardCount = joSign.getJSONObject("signTaskInfo").getJSONObject("currentSignItem").getInt("awardCount");
//          Log.farm("农场签到📅[获得肥料]#" + awardCount + "g");
//          Status.setFlagToday("orchardSign");
//        } else {
//          Log.runtime(joSign.getString("resultDesc"), joSign.toString());
//        }
//      } else {
//        Log.runtime(TAG,"农场今日已签到");
//        Status.setFlagToday("orchardSign");
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "orchardSign err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//  private static void triggerTbTask() {
//    try {
//      String s = AntOrchardRpcCall.orchardListTask();
//      JSONObject jo = new JSONObject(s);
//      if ("100".equals(jo.getString("resultCode"))) {
//        JSONArray jaTaskList = jo.getJSONArray("taskList");
//        for (int i = 0; i < jaTaskList.length(); i++) {
//          jo = jaTaskList.getJSONObject(i);
//          if (!"FINISHED".equals(jo.getString("taskStatus"))) continue;
//          String title = jo.getJSONObject("taskDisplayConfig").getString("title");
//          int awardCount = jo.optInt("awardCount", 0);
//          String taskId = jo.getString("taskId");
//          String taskPlantType = jo.getString("taskPlantType");
//          jo = new JSONObject(AntOrchardRpcCall.triggerTbTask(taskId, taskPlantType));
//          if ("100".equals(jo.getString("resultCode"))) {
//            Log.farm("领取奖励🎖️[" + title + "]#" + awardCount + "g肥料");
//          } else {
//            Log.runtime(jo.getString("resultDesc"));
//            Log.runtime(jo.toString());
//          }
//        }
//      } else {
//        Log.runtime(jo.getString("resultDesc"));
//        Log.runtime(s);
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "triggerTbTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//  private void querySubplotsActivity(int taskRequire) {
//    try {
//      String s = AntOrchardRpcCall.querySubplotsActivity(treeLevel);
//      JSONObject jo = new JSONObject(s);
//      if ("100".equals(jo.getString("resultCode"))) {
//        JSONArray subplotsActivityList = jo.getJSONArray("subplotsActivityList");
//        for (int i = 0; i < subplotsActivityList.length(); i++) {
//          JSONObject jo2 = subplotsActivityList.getJSONObject(i);
//          if (!"WISH".equals(jo2.getString("activityType"))) continue;
//
//          String activityId = jo2.getString("activityId");
//          String status = jo2.getString("status");
//
//          if ("NOT_STARTED".equals(status)) {
//            String extend = jo2.getString("extend");
//            JSONObject jo3 = new JSONObject(extend);
//            JSONArray wishActivityOptionList = jo3.getJSONArray("wishActivityOptionList");
//            String optionKey = null;
//
//            for (int j = 0; j < wishActivityOptionList.length(); j++) {
//              JSONObject jo4 = wishActivityOptionList.getJSONObject(j);
//              if (taskRequire == jo4.getInt("taskRequire")) {
//                optionKey = jo4.getString("optionKey");
//                break;
//              }
//            }
//
//            if (optionKey != null) {
//              JSONObject jo5 = new JSONObject(AntOrchardRpcCall.triggerSubplotsActivity(activityId, "WISH", optionKey));
//              if ("100".equals(jo5.getString("resultCode"))) {
//                Log.farm("农场许愿✨[每日施肥" + taskRequire + "次]");
//              } else {
//                Log.runtime(TAG, jo5.getString("resultDesc"));
//              }
//            }
//          } else if ("FINISHED".equals(status)) {
//            JSONObject jo3 = new JSONObject(AntOrchardRpcCall.receiveOrchardRights(activityId, "WISH"));
//            if ("100".equals(jo3.getString("resultCode"))) {
//              Log.farm("许愿奖励✨[肥料" + jo3.getInt("amount") + "g]");
//              querySubplotsActivity(taskRequire);
//              return;
//            } else {
//              Log.runtime(TAG, jo3.getString("resultDesc"));
//            }
//          }
//        }
//      } else {
//        Log.runtime(TAG, jo.getString("resultDesc"));
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "querySubplotsActivity err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//  /**
//   * 创建动物信息JSON字符串。
//   *
//   * @param animalUserId   动物用户ID
//   * @param earnManureCount 赚取肥料数量
//   * @param groupId        组ID
//   * @param orchardUserId  果园用户ID
//   * @return 动物信息JSON字符串
//   */
//  private String createAnimalInfoJson(String animalUserId, int earnManureCount, String groupId, String orchardUserId) {
//    return "{\"animalUserId\":\"" + animalUserId + "\",\"earnManureCount\":" + earnManureCount + ",\"groupId\":\"" + groupId + "\",\"orchardUserId\":\"" + orchardUserId + "\"}";
//  }
//  /** 一键捉鸡除草 */
//  private void batchHireAnimalRecommend() {
//    try {
//      JSONObject jo = new JSONObject(AntOrchardRpcCall.batchHireAnimalRecommend(UserMap.getCurrentUid()));
//      if ("100".equals(jo.getString("resultCode"))) {
//        JSONArray recommendGroupList = jo.optJSONArray("recommendGroupList");
//        if (recommendGroupList != null && recommendGroupList.length() > 0) {
//          List<String> GroupList = new ArrayList<>();
//          for (int i = 0; i < recommendGroupList.length(); i++) {
//            jo = recommendGroupList.getJSONObject(i);
//            String animalUserId = jo.getString("animalUserId");
//            if (dontHireList.getValue().contains(animalUserId))
//              continue;
//            int earnManureCount = jo.getInt("earnManureCount");
//            String groupId = jo.getString("groupId");
//            String orchardUserId = jo.getString("orchardUserId");
//            if (dontWeedingList.getValue().contains(orchardUserId)) {
//              continue;
//            }
//            GroupList.add(createAnimalInfoJson(animalUserId, earnManureCount, groupId, orchardUserId));
//          }
//          if (!GroupList.isEmpty()) {
//            jo = new JSONObject(AntOrchardRpcCall.batchHireAnimal(GroupList));
//            if ("100".equals(jo.getString("resultCode"))) {
//              Log.farm("一键捉鸡🐣[除草]");
//            }
//          }
//        }
//      } else {
//        Log.runtime(jo.getString("resultDesc"));
//        Log.runtime(jo.toString());
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "batchHireAnimalRecommend err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//  // 助力好友
//  private void orchardAssistFriend() {
//    try {
//      if (!Status.canAntOrchardAssistFriendToday()) {
//        Log.runtime(TAG, "今日已助力，跳过农场助力");
//        return;
//      }
//
//      Set<String> friendSet = assistFriendList.getValue();
//      for (String uid : friendSet) {
//        String shareId = Base64.encodeToString(
//          (uid + "-" + RandomUtil.getRandomInt(5) + "ANTFARM_ORCHARD_SHARE_P2P").getBytes(),
//          Base64.NO_WRAP
//        );
//        String str = AntOrchardRpcCall.achieveBeShareP2P(shareId);
//        JSONObject jsonObject = new JSONObject(str);
//        CoroutineUtils.sleepCompat(800);
//        String name = UserMap.getMaskName(uid);
//
//        if (!ResChecker.checkRes(TAG, str)) {
//          String code = jsonObject.getString("code");
//          if ("600000027".equals(code)) {
//            Log.runtime(TAG, "农场助力💪今日助力他人次数上限");
//            Status.antOrchardAssistFriendToday();
//            return;
//          }
//          Log.error(TAG, "农场助力😔失败[" + name + "]" + jsonObject.optString("desc"));
//          continue;
//        }
//        Log.farm("农场助力💪[助力:" + name + "]");
//      }
//      Status.antOrchardAssistFriendToday();
//    } catch (Throwable t) {
//      Log.runtime(TAG, "orchardAssistFriend err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//  //===============广告任务======================
//  // 处理广告任务
//  private void handleAdTask() {
//    try {
//      JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardListTask2());
//      if (!"100".equals(jo.optString("resultCode"))) {
//        Log.error(TAG, "handleAdTask err:" + jo);
//        return;
//      }
////      JSONObject convertToManureTask = jo.optJSONObject("convertToManureTask");
////      if (convertToManureTask != null) {
////        if(convertToManureTask.optString("taskStatus").equals("TODO")){
////          if(!Status.hasFlagToday("AntOrchard_LUOYE_TASK")) {
////            //落叶任务
////            finishLuoyeTask();
////          }
////        }
////      }
//
//      JSONArray jaTaskList = jo.getJSONArray("taskList");
//      if (jaTaskList == null || jaTaskList.length() == 0) {
//        Log.farm(TAG, "任务列表为空");
//        return;
//      }
//
//      for (int i = 0; i < jaTaskList.length(); i++) {
//        jo = jaTaskList.getJSONObject(i);
//        if (!"TODO".equals(jo.getString("taskStatus"))) continue;
//
//        String title = jo.getJSONObject("taskDisplayConfig").optString("title", "未知任务");
//        String groupId = jo.optString("groupId");
//
//          switch (groupId) {
//            // 500肥料任务
//              case "70000" -> finish500Task(jo);
//
//              // 1000肥料任务
//              case "DENGHUO_BROWSE_1" -> finish1000Task(title);
//
//              // 1500肥料任务（最多执行3次）
//              case "DENGHUO_BROWSE_3" -> {
//                  for (int j = 0; j < 3; j++) {
//                      if (!finish1500Task(title)) {
//                          break;
//                      }
//                  }
//              }
//          }
//      }
//    } catch (Throwable t) {
//      Log.error(TAG, "handleAdTask err: " + t);
//    }
//  }
//
//
//
//
//  @SuppressLint("NewApi")
//  private void finish500Task(JSONObject task) {
//    try {
//      String title = task.getJSONObject("taskDisplayConfig").optString("title", "未知任务");
//      // 调用RPC获取广告任务（动态解析tokenFeeds与spaceCodeFeeds）
//      String targetUrl = task.getJSONObject("taskDisplayConfig").optString("targetUrl", "");
//      String decoded = targetUrl;
//      try { decoded = URLDecoder.decode(targetUrl, StandardCharsets.UTF_8); } catch (Throwable ignored) {}
//      String innerUrl = decoded;
//      int idx = decoded.indexOf("url=");
//      if (idx >= 0) {
//        innerUrl = decoded.substring(idx + 4);
//        // 对 url 参数执行最多两次安全解码，处理形如 %257C 的双重编码和加号问题
//        for (int k = 0; k < 2; k++) {
//          String before = innerUrl;
//          String safe = innerUrl.replace("+", "%2B");
//          try { innerUrl = URLDecoder.decode(safe, StandardCharsets.UTF_8); } catch (Throwable ignored) {}
//          if (innerUrl.equals(before)) break;
//        }
//      }
//      String referToken = "";
//      String spaceCodeFeeds = "BABA_FARM_TASK_task_70000";
//      int tIdx = innerUrl.indexOf("tokenFeeds=");
//      if (tIdx >= 0) {
//        int end = innerUrl.indexOf("&", tIdx);
//        String tokenRaw = end > tIdx ? innerUrl.substring(tIdx + 11, end) : innerUrl.substring(tIdx + 11);
//        // 对 tokenFeeds 进行最多两次安全解码，避免 “+” 被还原为空格
//        String tok = tokenRaw;
//        for (int k = 0; k < 2; k++) {
//          String safe = tok.replace("+", "%2B");
//          try { tok = URLDecoder.decode(safe, StandardCharsets.UTF_8); } catch (Throwable ignored) {}
//        }
//        referToken = tok;
//      }
//      int sIdx = innerUrl.indexOf("spaceCodeFeeds=");
//      if (sIdx >= 0) {
//        int end = innerUrl.indexOf("&", sIdx);
//        spaceCodeFeeds = end > sIdx ? innerUrl.substring(sIdx + 15, end) : innerUrl.substring(sIdx + 15);
//        try { spaceCodeFeeds = URLDecoder.decode(spaceCodeFeeds, StandardCharsets.UTF_8); } catch (Throwable ignored) {}
//      }
//      JSONObject jo = new JSONObject(AntOrchardRpcCall.getAdTaskXlight500Ad(referToken, spaceCodeFeeds));
//      String playingBizId = handleAdRes(jo);
//
//      if (!playingBizId.isEmpty()) {
//        for (int i = 0; i < 1; i++) {
//          sleepAdTask();
//          jo = new JSONObject(AntOrchardRpcCall.finishAdTaskXlight500Ad(playingBizId));
//          if (jo.has("errMsg")) {
//            if (jo.optString("errMsg", "").equalsIgnoreCase("OK")
//                    || jo.optBoolean("success", false)) {
//              Log.farm(TAG, "完成[" + title + "]");
//            } else {
//              Log.error(TAG, "[jo1]finish500Task err:" + jo);
//              return;
//            }
//          } else {
//            Log.error(TAG, "[jo2]finish500Task err:" + jo);
//            return;
//          }
//        }
//      }
//    } catch (Throwable t) {
//      Log.error(TAG, "finish500Task err:" + t);
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//
//
//  private void finish1000Task(String title){
//    try {
//      JSONObject jo = new JSONObject(AntOrchardRpcCall.getAdTaskXlight1000Ad());
//      String playingBizId = handleAdRes(jo);
//      if (!playingBizId.isEmpty()){
//          sleepAdTask();
//          jo = new JSONObject(AntOrchardRpcCall.finishAdTaskXlight1000Ad(playingBizId));
//          if (jo.has("errMsg")){
//            if(jo.optString("errMsg","").equalsIgnoreCase("OK")
//                    ||jo.optBoolean("success",false)) {
//              Log.farm(TAG, "完成[" + title + "]");
//            }else{
//              Log.error(TAG, "[jo1]finish1000Task err:" + jo);
//            }
//          }else {
//            Log.error(TAG, "[jo2]finish1000Task err:" + jo);
//          }
//      }
//    } catch (Throwable t) {
//      Log.error(TAG, "finish1000Task err:"+t);
//    }
//  }
//  private boolean finish1500Task(String title){
//    try {
//      JSONObject jo = new JSONObject(AntOrchardRpcCall.getAdTaskXlight1500Ad());
//      String playingBizId = handleAdRes(jo);
//      if (!playingBizId.isEmpty()){
//          sleepAdTask();
//          jo = new JSONObject(AntOrchardRpcCall.finishAdTaskXlight1500Ad(playingBizId));
//          if (jo.has("errMsg")){
//            if(jo.optString("errMsg","").equalsIgnoreCase("OK")
//                    ||jo.optBoolean("success",false)) {
//              Log.farm(TAG, "完成[" + title + "]");
//              return true;
//            }else{
//              Log.error(TAG, "[jo1]finish1500Task err:" + jo);
//            }
//          }else {
//            Log.error(TAG, "[jo2]finish1500Task err:" + jo);
//          }
//      }
//    } catch (Throwable t) {
//      Log.error(TAG, "finish1500Task err:"+t);
//    }
//    return false;
//  }
//  private String handleAdRes(JSONObject jo) throws JSONException {
//    if (jo.has("resData")){
//      jo = jo.getJSONObject("resData");
//      if(jo.optString("errorMsg","").equals("ok")) {
//        JSONObject playingResult = jo.optJSONObject("playingResult");
//          if (playingResult != null) {
//              return playingResult.optString("playingBizId","");
//          }
//      }else {
//        Log.error(TAG, "[resData]handleAdRes err:" + jo);
//        return "";
//      }
//    }else if(jo.optString("errorMsg","").equals("ok")||jo.has("playingResult")) {
//        JSONObject playingResult = jo.optJSONObject("playingResult");
//        if (playingResult != null) {
//            return playingResult.optString("playingBizId","");
//        }
//    }else{
//      Log.error(TAG, "handleAdRes err:" + jo);
//    }
//    return "";
//  }
//  private void sleepAdTask(){
//    try {
//      int i = RandomUtil.nextInt(15000, 17000);
//      if (i > 0) {
//        TimeUtil.sleep(i);
//      }else {
//        TimeUtil.sleep(15000);
//      }
//    } catch (Exception e) {
//      Log.error(TAG, "sleepAdTask err:"+e);
//    }
//  }
//
//  private void finishLuoyeTask() {
//    try {
//      AntOrchardRpcCall.orchardRefinedOperation();
//      TimeUtil.sleep(2351);
//      AntOrchardRpcCall.orchardIndexLuoye();
//      TimeUtil.sleep(2351);
//      JSONObject jo = new JSONObject(AntOrchardRpcCall.triggerSubplotsActivity());
//      if (jo.optBoolean("success")){
//        Log.farm(TAG, "完成[去森林领落叶肥料]");
//      }else {
//        Log.error(TAG, "finishLuoyeTask err:"+jo);
//      }
//      Status.setFlagToday("AntOrchard_LUOYE_TASK");
//    } catch (Throwable t) {
//      Log.error(TAG, "finishLuoyeTask err:"+t);
//    }
//  }
//
//  /**
//   * 领取回访奖励
//   */
//  private void receiveOrchardVisitAward() {
//    try {
//      // 定义奖励来源
//      String[][] awardSources = {
//        {"tmall", "upgrade_tmall_exchange_task"},
//        {"antfarm", "ANTFARM_ORCHARD_PLUS"},
//        {"widget", "widget_shoufei"}
//      };
//
//      boolean hasAwardReceived = false;
//
//      for (String[] source : awardSources) {
//        String diversionSource = source[0];
//        String sourceParam = source[1];
//
//        String response = AntOrchardRpcCall.receiveOrchardVisitAward(diversionSource, sourceParam);
//        JSONObject jo = new JSONObject(response);
//
//        if (!ResChecker.checkRes(TAG, jo)) {
//          Log.error(TAG, "领取回访奖励失败 (source=" + sourceParam + "): " + response);
//          continue;
//        }
//
//        JSONArray awardList = jo.optJSONArray("orchardVisitAwardList");
//        if (awardList == null || awardList.length() == 0) {
//          Log.runtime(TAG, "领取回访奖励完成 (source=" + sourceParam + "): 无奖励，可能已领取过");
//          continue;
//        }
//
//        for (int i = 0; i < awardList.length(); i++) {
//          JSONObject awardObj = awardList.optJSONObject(i);
//          if (awardObj == null) continue;
//
//          int awardCount = awardObj.optInt("awardCount", 0);
//          String awardDesc = awardObj.optString("awardDesc", "");
//          Log.farm("回访奖励[" + awardDesc + "] " + awardCount + " g肥料");
//          hasAwardReceived = true;
//        }
//      }
//
//      if (hasAwardReceived) {
//        Status.setFlagToday(StatusFlags.FLAG_ANTORCHARD_WIDGET_DAILY_AWARD);
//        Log.runtime(TAG, "回访奖励领取完成");
//      } else {
//        Log.runtime(TAG, "回访奖励已全部领取或无可领取奖励");
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "receiveOrchardVisitAward err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 限时挑战任务
//   */
//  private void limitedTimeChallenge() {
//    try {
//      // 1. 请求同步数据
//      String wua = SecurityBodyHelper.getSecurityBodyData(4).toString();
//      //Log.runtime(TAG, "限时奖励: set Wua " + wua);
//      String response = AntOrchardRpcCall.orchardSyncIndex(wua);
//      JSONObject root = new JSONObject(response);
//
//      if (!ResChecker.checkRes(TAG, root)) {
//        Log.error(TAG, "orchardSyncIndex 查询失败: " + response);
//        return;
//      }
//
//      // 2. 获取 limitedTimeChallenge
//      JSONObject challenge = root.optJSONObject("limitedTimeChallenge");
//      if (challenge == null) {
//        Log.error(TAG, "错误：limitedTimeChallenge 字段不存在或为 null");
//        return;
//      }
//
//      int currentRound = challenge.optInt("currentRound", 0);
//      if (currentRound <= 0) {
//        Log.error(TAG, "错误：currentRound 无效：" + currentRound);
//        return;
//      }
//
//      // 3. 获取任务数组
//      JSONArray taskArray = challenge.optJSONArray("limitedTimeChallengeTasks");
//      if (taskArray == null) {
//        Log.error(TAG, "错误：limitedTimeChallengeTasks 字段不存在或不是数组");
//        return;
//      }
//
//      int targetIdx = currentRound - 1;
//      if (targetIdx < 0 || targetIdx >= taskArray.length()) {
//        Log.error(TAG, "错误：当前轮数 " + currentRound + " 对应下标 " + targetIdx + " 超出数组长度: " + taskArray.length());
//        return;
//      }
//
//      // 4. 当前轮任务
//      JSONObject roundTask = taskArray.optJSONObject(targetIdx);
//      if (roundTask == null) {
//        Log.error(TAG, "错误：第 " + currentRound + " 轮任务不存在");
//        return;
//      }
//
//      boolean ongoing = roundTask.optBoolean("ongoing", false);
//      String mTaskStatus = roundTask.optString("taskStatus");
//      String mTaskId = roundTask.optString("taskId");
//      int mAwardCount = roundTask.optInt("awardCount", 0);
//
//      // 大任务已完成但未领取奖励
//      if ("FINISHED".equals(mTaskStatus) && ongoing) {
//        Log.runtime(TAG, "第 " + currentRound + " 轮 奖励未领取，尝试领取");
//
//        String awardResp = AntOrchardRpcCall.receiveTaskAward("ORCHARD_LIMITED_TIME_CHALLENGE", mTaskId);
//        JSONObject joo = new JSONObject(awardResp);
//
//        if (ResChecker.checkRes(TAG, joo)) {
//          Log.farm("第 " + currentRound + " 轮 限时任务🎁[肥料 * " + mAwardCount + "]");
//        } else {
//          String desc = joo.optString("desc", "未知错误");
//          Log.error(TAG, "农场 限时任务 错误：" + desc);
//        }
//        return;
//      }
//
//      if (!"TODO".equals(roundTask.optString("taskStatus"))) {
//        Log.error(TAG, "警告：第 " + currentRound + " 轮任务非 TODO，状态=" + roundTask.optString("taskStatus"));
//        return;
//      }
//
//      // 子任务
//      JSONArray childTasks = roundTask.optJSONArray("childTaskList");
//      if (childTasks == null) {
//        Log.error(TAG, "警告：第 " + currentRound + " 轮无子任务列表");
//        return;
//      }
//
//      Log.runtime(TAG, "开始处理第 " + currentRound + " 轮的 " + childTasks.length() + " 个子任务");
//
//      // 5. 遍历子任务
//      for (int i = 0; i < childTasks.length(); i++) {
//        JSONObject child = childTasks.optJSONObject(i);
//        if (child == null) {
//          Log.error(TAG, "警告：子任务索引 " + i + " 非 JSONObject，跳过");
//          continue;
//        }
//
//        String childTaskId = child.optString("taskId", "未知ID");
//        String actionType = child.optString("actionType");
//        String groupId = child.optString("groupId");
//        String taskStatus = child.optString("taskStatus");
//        String taskId = child.optString("taskId");
//        String sceneCode = child.optString("sceneCode");
//        int taskRequire = child.optInt("taskRequire", 0);
//        int taskProgress = child.optInt("taskProgress", 0);
//        int awardCount = child.optInt("awardCount", 0);
//
//        if (!"TODO".equals(taskStatus)) continue;
//        if ("GROUP_1_STEP_3_GAME_WZZT_30s".equals(groupId)) continue;
//        if ("GROUP_1_STEP_2_GAME_WZZT_30s".equals(groupId)) continue;
//
//        Log.runtime(TAG, "------ 开始处理子任务 " + i + " | ID=" + childTaskId + " ------");
//
//        switch (actionType) {
//          case "SPREAD_MANURE":
//            handleSpreadManureTask(taskRequire, taskProgress);
//            break;
//
//          case "GAME_CENTER":
//            handleGameCenterTask();
//            break;
//
//          case "VISIT":
//            handleVisitTask(child, sceneCode, groupId);
//            break;
//
//          default:
//            Log.error(TAG, "无法处理的任务类型：" + childTaskId + " | actionType=" + actionType);
//            break;
//        }
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "limitedTimeChallenge err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 处理施肥任务
//   */
//  private void handleSpreadManureTask(int taskRequire, int taskProgress) {
//    try {
//      int need = taskRequire - taskProgress;
//      if (need <= 0) {
//        //Log.runtime(TAG, "施肥任务无需操作（当前进度 >= 需求）");
//        return;
//      }
//
//      Log.runtime(TAG, "施肥任务需补充 " + need + " 次");
//      List<String> sourceList = Arrays.asList(
//        "DNHZ_NC_zhimajingnangSF",
//        "widget_shoufei",
//        "ch_appcenter__chsub_9patch"
//      );
//
//      for (int index = 0; index < need; index++) {
//        String wua = SecurityBodyHelper.getSecurityBodyData(4).toString();
//        String randomSource = sourceList.get(RandomUtil.nextInt(0, sourceList.size()));
//        String spreadResult = AntOrchardRpcCall.orchardSpreadManure(wua, randomSource);
//        Log.runtime(TAG, "施肥第 " + (index + 1) + " 次");
//
//        JSONObject resultJson = new JSONObject(spreadResult);
//        String resultCode = resultJson.optString("resultCode", "");
//        String resultDesc = resultJson.optString("resultDesc", "");
//
//        if (!"100".equals(resultCode)) {
//          Log.error(TAG, "农场 orchardSpreadManure 错误：" + resultDesc);
//          return;
//        }
//        GlobalThreadPools.sleep(executeIntervalInt);
//      }
//
//      Log.runtime(TAG, "施肥任务成功完成 " + need + " 次");
//    } catch (Throwable t) {
//      Log.runtime(TAG, "handleSpreadManureTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 处理游戏中心任务
//   */
//  private void handleGameCenterTask() {
//    try {
//      String r = AntOrchardRpcCall.noticeGame("2021004165643274");
//      JSONObject jr = new JSONObject(r);
//      if (ResChecker.checkRes(TAG, jr)) {
//        Log.runtime(TAG, "游戏任务触发成功 → 子任务应当自动完成");
//      } else {
//        Log.error(TAG, "游戏任务触发失败，返回: " + r);
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "handleGameCenterTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 处理浏览广告任务
//   */
//  @SuppressLint("NewApi")
//  private void handleVisitTask(JSONObject child, String sceneCode, String groupId) {
//    try {
//      JSONObject displayCfg = child.optJSONObject("taskDisplayConfig");
//      if (displayCfg == null) {
//        Log.error(TAG, "任务没有 taskDisplayConfig，无法继续");
//        return;
//      }
//
//      String targetUrl = displayCfg.optString("targetUrl", "");
//      if (targetUrl.isEmpty()) {
//        Log.error(TAG, "taskDisplayConfig.targetUrl 为空");
//        return;
//      }
//
//      // 提取完整的落地页URL
//      String finalUrl = getFullNestedUrl(targetUrl, "url");
//      if (finalUrl == null) finalUrl = "";
//
//      // 从完整URL中提取spaceCodeFeeds
//      String spaceCodeFeeds = null;
//      if (!finalUrl.isEmpty()) {
//        spaceCodeFeeds = extractParamFromUrl(finalUrl, "spaceCodeFeeds");
//      }
//
//      // 容错处理
//      String finalSpaceCode = spaceCodeFeeds != null ? spaceCodeFeeds : getParamValue(targetUrl, "spaceCodeFeeds");
//      if (finalSpaceCode == null || finalSpaceCode.isEmpty()) {
//        return;
//      }
//
//      // 触发广告任务
//      String pageFrom = "ch_url-https://render.alipay.com/p/yuyan/180020010001263018/game.html";
//      String session = "u_41ba1_2f33e";
//
//      String r = XLightRpcCall.INSTANCE.xlightPlugin(finalUrl, pageFrom, session, finalSpaceCode);
//      JSONObject jr = new JSONObject(r);
//
//      Log.runtime(TAG, "广告任务触发成功 → 即将调用 finishTask() 完成任务");
//
//      // 获取playingResult
//      JSONObject playingResult = null;
//      JSONObject resData = jr.optJSONObject("resData");
//      if (resData != null) {
//        playingResult = resData.optJSONObject("playingResult");
//      }
//      if (playingResult == null) {
//        playingResult = jr.optJSONObject("playingResult");
//      }
//
//      if (playingResult == null) {
//        Log.error(TAG, "playingResult 为空，无法 finishTask");
//        return;
//      }
//
//      String playingBizId = playingResult.optString("playingBizId", "");
//      if (playingBizId.isEmpty()) {
//        Log.error(TAG, "playingBizId 为空，无法 finishTask");
//        return;
//      }
//
//      JSONObject eventRewardDetail = playingResult.optJSONObject("eventRewardDetail");
//      JSONArray infoListArray = eventRewardDetail != null ? eventRewardDetail.optJSONArray("eventRewardInfoList") : null;
//
//      if (infoListArray == null || infoListArray.length() == 0) {
//        Log.error(TAG, "eventRewardInfoList 为空，无法 finishTask");
//        return;
//      }
//
//      JSONObject playEventInfo = infoListArray.getJSONObject(0);
//
//      String finishResult = XLightRpcCall.INSTANCE.finishTask(playingBizId, playEventInfo, sceneCode, groupId);
//      JSONObject fr = new JSONObject(finishResult);
//
//      if (ResChecker.checkRes(TAG, fr)) {
//        Log.runtime(TAG, "finishTask 完成成功 → 浏览广告任务完成");
//      } else {
//        Log.error(TAG, "finishTask 完成失败: " + finishResult);
//      }
//    } catch (Throwable t) {
//      Log.runtime(TAG, "handleVisitTask err:");
//      Log.printStackTrace(TAG, t);
//    }
//  }
//
//  /**
//   * 从URL中提取嵌套的完整URL
//   */
//  @SuppressLint("NewApi")
//  private String getFullNestedUrl(String url, String paramName) {
//    try {
//      String query = url.contains("?") ? url.substring(url.indexOf("?") + 1) : "";
//      if (query.isEmpty()) return null;
//
//      String decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.name());
//      String[] params = decodedQuery.split("&");
//      for (String param : params) {
//        String[] pair = param.split("=", 2);
//        if (pair.length == 2 && pair[0].equals(paramName)) {
//          return URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
//        }
//      }
//      return null;
//    } catch (Throwable t) {
//      return null;
//    }
//  }
//
//  /**
//   * 从URL中提取指定参数
//   */
//  @SuppressLint("NewApi")
//  private String extractParamFromUrl(String url, String paramName) {
//    try {
//      String query = url.contains("?") ? url.substring(url.indexOf("?") + 1) : "";
//      if (query.isEmpty()) return null;
//
//      String[] params = query.split("&");
//      for (String param : params) {
//        String[] pair = param.split("=", 2);
//        if (pair.length == 2 && pair[0].equals(paramName)) {
//          return URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
//        }
//      }
//      return null;
//    } catch (Throwable t) {
//      return null;
//    }
//  }
//
//  /**
//   * 获取URL参数值
//   */
//  @SuppressLint("NewApi")
//  private String getParamValue(String url, String key) {
//    try {
//      String query = url.contains("?") ? url.substring(url.indexOf("?") + 1) : "";
//      if (query.isEmpty()) return null;
//
//      String decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.name());
//      String[] params = decodedQuery.split("&");
//      for (String param : params) {
//        String[] pair = param.split("=", 2);
//        if (pair.length == 2 && pair[0].equals(key)) {
//          return URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
//        }
//      }
//      return null;
//    } catch (Throwable t) {
//      return null;
//    }
//  }
//
//  //===========================================
//}
