package fansirsqi.xposed.sesame.task.otherTask2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
import fansirsqi.xposed.sesame.task.otherTask.CompletedKeyEnum;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.StringModelField;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.task.otherTask2.logisticsinteraction.baoguo;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import lombok.Getter;

public class OtherTask2 extends ModelTask {
    private static final String TAG = "⚔️其他任务2";
    // 任务执行线程池
    private static final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
    
    // 线程安全控制
    private final ReentrantLock executionLock = new ReentrantLock();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);


    @Override
    public String getName() {
        return "其他任务2";
    }
    protected Integer executeIntervalInt = 6000;  // 执行间隔
    private final StringModelField startTime = new StringModelField("startTime", "开始执行时间(关闭:-1)", "0700");
   //    private final BooleanModelField payAwardProd = new BooleanModelField("payAwardProd", "支付赚红包", false);
    private BooleanModelField memberTaskNew = new BooleanModelField("memberTaskNew", "会员任务", false);
    private BooleanModelField expressTask = new BooleanModelField("expressTask", "快递积分", false);
    private BooleanModelField privilegeTask = new BooleanModelField("privilegeTask", "青春特权", true);
    private BooleanModelField gameCenter = new BooleanModelField("gameCenter", "游戏中心浏览任务", false);
    private BooleanModelField gameCenterGold = new BooleanModelField("gameCenterGold", "游戏中心金币任务", false);
    private BooleanModelField monthTRA = new BooleanModelField("monthTRA", "月月赚转账红包", false);
    private BooleanModelField scholarship = new BooleanModelField("scholarship", "奖学金", false);
    private BooleanModelField touchPay = new BooleanModelField("touchpay", "碰一碰街区", false);
    //private BooleanModelField collectRedPacket = new BooleanModelField("collectRedPacket", "红包 | 集红包皮肤", false);
    private BooleanModelField studentAnswer = new BooleanModelField("studentAnswer", "学生模式(限时)| 答题道具", false);
    //private BooleanModelField harvestLimitedTime = new BooleanModelField("harvestLimitedTime", "丰收节(限时)", false);
    private BooleanModelField CampusPaiSign = new BooleanModelField("CampusPaiSign", "校园派|签到", false);
    private BooleanModelField CampusPaiTask = new BooleanModelField("CampusPaiTask", "校园派|任务", false);
    private BooleanModelField LifeMsgProd = new BooleanModelField("LifeMsgProd", "民生之家", false);
    private BooleanModelField baoGuo = new BooleanModelField("baoGuo", "包裹游历", false);
    private BooleanModelField SesameTree = new BooleanModelField("SesameTree", "芝麻树|任务", false);
    private BooleanModelField SesameTreeUpgrade = new BooleanModelField("SesameTreeUpgrade", "芝麻树|升级", false);
    //private BooleanModelField UgShooping = new BooleanModelField("UgShooping", "天天秒杀|天天领现金", false);
    private BooleanModelField rceduService = new BooleanModelField("rceduService", "多懂一点小程序|学分", false);
    private BooleanModelField sesameAlchemyMy = new BooleanModelField("sesameAlchemyMy","芝麻炼金",false);
    private BooleanModelField playConsultFacade = new BooleanModelField("playConsultFacade","会员|转盘",false);
    private IntegerModelField playConsultFacadeNum = new IntegerModelField("playConsultFacadeNum","会员|转盘-次数",10);
    private BooleanModelField topUpGoldTask = new BooleanModelField("topUpGoldTask", "充值金任务", false);
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(startTime);
        modelFields.addField(memberTaskNew);
        modelFields.addField(expressTask );
        modelFields.addField(privilegeTask );
        modelFields.addField(gameCenter);
        modelFields.addField(gameCenterGold);
        modelFields.addField(monthTRA);//月月赚
//        modelFields.addField(payAwardProd);//支付赚红包
        modelFields.addField(scholarship);//奖学金
        modelFields.addField(touchPay);
        //modelFields.addField(collectRedPacket);
        modelFields.addField(studentAnswer);
        //modelFields.addField(harvestLimitedTime);
        modelFields.addField(CampusPaiSign);
        modelFields.addField(CampusPaiTask);
        modelFields.addField(LifeMsgProd);
        modelFields.addField(baoGuo);
        modelFields.addField(SesameTree);
        modelFields.addField(SesameTreeUpgrade);
        //modelFields.addField(UgShooping);
        modelFields.addField(rceduService);
        modelFields.addField(sesameAlchemyMy);
        modelFields.addField(playConsultFacade);
        modelFields.addField(playConsultFacadeNum);
        modelFields.addField(topUpGoldTask);
        //modelFields.addField(monthlyPhoneBill);
        return modelFields;
    }


    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER2;
    }

    @Override
    public String getIcon() {
        return "AntSports.png";
    }
    @Override
    public void runJava() {
        // 线程安全检查 - 防止并发执行
        if (!isRunning.compareAndSet(false, true)) {
            //Log.runtime(TAG, "任务正在执行中，跳过本次调用");
            return;
        }
        
        taskExecutor.execute(() -> {
            executionLock.lock();
            try {
                
                // 定义任务组
                List<TaskGroup> taskGroups = new ArrayList<>();

                // 第一组：核心任务
                taskGroups.add(new TaskGroup("核心任务", Arrays.asList(
                        new TaskWrapper("会员任务", () -> {
                            if (memberTaskNew.getValue()) {
                                if (!Status.hasTemporaryStatusValid("memberNewSignList")){
                                    new MemberNew().handle();
                                }
                            }
                        }),
                        new TaskWrapper("芝麻炼金", () -> {
                            if (sesameAlchemyMy.getValue()) {
                                new SesameAlchemy().run();
                            }
                        }),
                        new TaskWrapper("会员转盘", () -> {
                            if (playConsultFacade.getValue()) {
                                if (!Status.hasTemporaryStatusValid("MemberLuckyWheel_Cooldown")) {
                                    int num = playConsultFacadeNum.getValue();
                                    new PlayConsultFacade().handleAsync(num);
                                }
                            }
                        }),
                    new TaskWrapper("青春特权任务", () -> {
                        //青春特权任务
                        if (privilegeTask.getValue()) {
                            PrivilegeTask.Companion.executeProcessStudentTasks();
                            if (!Status.hasFlagToday(CompletedKeyEnum.taskPointPrize.name())) {
                                PrivilegeTask.Companion.taskPointPrize();
                            }
                        }
                    }),
                        new TaskWrapper("包裹游历",()->{
                            if (baoGuo.getValue()) {
                                baoguo.INSTANCE.handle();
                            }
                        }),

                        new TaskWrapper("月月赚任务", () -> {
                            if (monthTRA.getValue()) {
                                if (!Status.hasFlagToday(CompletedKeyEnum.MonthTask.name())) {
                                    new MonthTra().handle();
                                }
                            }
                        })

                )));

                // 第二组：常规任务
                taskGroups.add(new TaskGroup("常规任务", Arrays.asList(
                        new TaskWrapper("奖学金任务", () -> {
                            if (scholarship.getValue()) {
                                new Scholarship().handle();
                            }
                        }),
                        new TaskWrapper("芝麻树", () -> {
                            if (SesameTree.getValue()) {
                                new SesameTree().handle();
                                if (SesameTreeUpgrade.getValue()){
                                    new SesameTree().handleUpgradeTree();
                                }
                            }
                        }),
                        new TaskWrapper("校园派任务", () -> {
                            if (CampusPaiTask.getValue()) {
                                new CampusPai().campusPaiTask();
                            }
                        }),
                        new TaskWrapper("碰一碰街区", () -> {
                            if (touchPay.getValue()) {
                                new TouchPay().handle();
                            }
                        }),

                        new TaskWrapper("游戏中心", () -> {
                            if (gameCenter.getValue()) {
                                new GameCenter().handle();
                            }
                        }),
                        new TaskWrapper("游戏中心金币任务", () -> {
                            if (gameCenterGold.getValue()) {
                                new GameCenterGold().handle();
                            }
                        })
                )));

                // 第三组：扩展任务
                taskGroups.add(new TaskGroup("扩展任务", Arrays.asList(

                        new TaskWrapper("快递积分", () -> {
                            if (expressTask.getValue()) {
                                new KuaiDiFuLiJia().handle();
                            }
                        }),
                        new TaskWrapper("学生模式", () -> {
                            if (studentAnswer.getValue()) {
                                new StudentAnswer().handle();
                            }
                        }),
                        new TaskWrapper("校园派签到", () -> {
                            if (CampusPaiSign.getValue()) {
                                new CampusPai().handle();
                            }
                        })
                )));

                // 第四组：其他任务
                taskGroups.add(new TaskGroup("其他任务", Arrays.asList(

                        new TaskWrapper("民生之家", () -> {
                            if (LifeMsgProd.getValue()) {
                                new LifeMsgProd().handle();
                            }
                        }),

                        new TaskWrapper("多懂一点", () -> {
                            if (rceduService.getValue()) {
                                if (!Status.hasFlagToday("rceduService_handle")) {
                                    new RceduService().handle();
                                }
                            }
                        }),

                        new TaskWrapper("充值金任务", () -> {
                            if (topUpGoldTask.getValue()) {
                                new TopUpGold().handle();
                            }
                        })
                )));

                // 执行所有任务组
                executeTaskGroups(taskGroups);
                
                Log.runtime(TAG, "其他任务2执行完成");

            } catch (Throwable t) {
                Log.error(TAG+"运行出错："+t);
                Log.printStackTrace(t);
            } finally {
                isRunning.set(false);
                executionLock.unlock();
            }
        });
    }

    // 任务组执行方法
    private void executeTaskGroups(List<TaskGroup> taskGroups) {
        int totalGroups = taskGroups.size();
        int completedGroups = 0;
        
        for (int i = 0; i < totalGroups; i++) {
            TaskGroup group = taskGroups.get(i);
            try {
                // 更新通知显示当前执行的组
                Notify.setStatusTextExec(group.getGroupName());
                //Log.runtime(TAG, "开始执行[" + group.getGroupName() + "]，共" + group.getTasks().size() + "个任务");

                int completedTasks = 0;
                // 执行组内所有任务
                for (TaskWrapper task : group.getTasks()) {
                    try {
                        //Log.runtime(TAG, "执行任务: " + task.taskName());
                        task.execute();
                        completedTasks++;
                    } catch (Exception e) {
                        Log.error(TAG, "执行任务[" + task.taskName() + "]异常: " + e.getMessage());
                        // 继续执行其他任务，不因单个任务失败而中断
                    }
                }

                completedGroups++;
                //Log.runtime(TAG, "[" + group.getGroupName() + "]执行完成(" + completedTasks + "/" + group.getTasks().size() + "任务) [" + completedGroups + "/" + totalGroups + "组]");
                
                // 组间休息
                try {
                    Thread.sleep(5000); // 休息时间
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.error(TAG, "任务组间休息被中断");
                    break;
                }
                
            } catch (Exception e) {
                Log.error(TAG, "执行任务组[" + group.getGroupName() + "]异常: " + e.getMessage());
                // 继续执行下一个任务组
            }
        }
    }

    // 任务组类
    @Getter
    private static class TaskGroup {
        private final String groupName;
        private final List<TaskWrapper> tasks;

        public TaskGroup(String groupName, List<TaskWrapper> tasks) {
            this.groupName = groupName;
            this.tasks = tasks;
        }

    }

        // 任务包装类
        private record TaskWrapper(@Getter String taskName, Runnable task) {
        public void execute() {
                try {
                    if (task != null) {
                        task.run();
                    }
                } catch (Exception e) {
                    Log.error(TAG, "任务[" + taskName + "]执行异常: " + e.getMessage());
                    throw e; // 重新抛出异常以便上层处理
                }
            }
        }



}
