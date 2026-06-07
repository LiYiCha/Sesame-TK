package fansirsqi.xposed.sesame.hook.scheduler;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import fansirsqi.xposed.sesame.data.Config;
import fansirsqi.xposed.sesame.hook.context.AppContext;
import fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager;
import fansirsqi.xposed.sesame.hook.resource.ExecutorManager;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.util.maps.UserMap;

/**
 * 任务调度器
 * 负责管理任务执行器和调度器，处理任务调度逻辑
 */
public class TaskScheduler {
    private static final String TAG = TaskScheduler.class.getSimpleName();

    // 单例执行器 - 用于任务调度
    private static volatile ExecutorService taskExecutor;
    private static final Object executorLock = new Object();

    // 调度器 - 用于定时任务
    private static volatile ScheduledExecutorService scheduler;
    private static volatile ScheduledFuture<?> scheduledTask;
    private static final Object schedulerLock = new Object();

    // 执行状态控制
    private static final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private static final AtomicBoolean isScheduled = new AtomicBoolean(false);
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    // 最小执行间隔（毫秒）
    private static final long MIN_EXECUTION_INTERVAL = 2000L;
    // 优雅关闭超时时间（秒）
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static volatile long lastExecTime = 0;

    /**
     * 获取单例任务执行器（双重检查锁定）
     */
    private static ExecutorService getTaskExecutor() {
        if (isShuttingDown.get()) {
            return null;
        }

        ExecutorService executor = taskExecutor;
        if (executor == null || executor.isShutdown()) {
            synchronized (executorLock) {
                executor = taskExecutor;
                if (executor == null || executor.isShutdown()) {
                    taskExecutor = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "SesameTaskExecutor");
                        t.setDaemon(false);
                        return t;
                    });
                    executor = taskExecutor;
                    // 注册执行器用于资源泄漏检测
                    ExecutorManager.registerExecutor();
                }
            }
        }
        return executor;
    }

    /**
     * 获取单例调度器（双重检查锁定）
     */
    private static ScheduledExecutorService getScheduler() {
        if (isShuttingDown.get()) {
            return null;
        }

        ScheduledExecutorService sched = scheduler;
        if (sched == null || sched.isShutdown()) {
            synchronized (schedulerLock) {
                sched = scheduler;
                if (sched == null || sched.isShutdown()) {
                    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "SesameScheduler");
                        t.setDaemon(false);
                        return t;
                    });
                    sched = scheduler;
                    // 注册调度器用于资源泄漏检测
                    ExecutorManager.registerScheduler();
                }
            }
        }
        return sched;
    }

    /**
     * 优雅关闭执行器（带超时）
     * 使用 ExecutorManager 进行资源管理和泄漏检测
     */
    public static void shutdownExecutors() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            return;
        }

        try {
            // 先取消调度任务
            synchronized (schedulerLock) {
                if (scheduledTask != null) {
                    scheduledTask.cancel(false);
                    scheduledTask = null;
                }
                isScheduled.set(false);

                // 使用 ExecutorManager 优雅关闭调度器
                if (scheduler != null) {
                    ExecutorManager.shutdownScheduler(scheduler, "SesameScheduler", SHUTDOWN_TIMEOUT_SECONDS);
                    scheduler = null;
                }
            }

            synchronized (executorLock) {
                // 使用 ExecutorManager 优雅关闭执行器
                if (taskExecutor != null) {
                    ExecutorManager.shutdownExecutor(taskExecutor, "SesameTaskExecutor", SHUTDOWN_TIMEOUT_SECONDS);
                    taskExecutor = null;
                }
                isExecuting.set(false);
            }
        } finally {
            isShuttingDown.set(false);
        }
    }

    /**
     * 主任务执行逻辑
     */
    private static void executeMainTask() {
        try {
            if (isShuttingDown.get()) {
                return;
            }

            TaskCommon.update();
            if (TaskCommon.IS_MODULE_SLEEP_TIME) {
                Log.runtime("️💤跳过执行-休眠时间");
                return;
            }

            if (!LifecycleManager.isInit()) {
                Log.runtime("️🐣跳过执行-未初始化");
                return;
            }

            if (!Config.isLoaded()) {
                Log.runtime("️⚙跳过执行-用户模块配置未加载");
                return;
            }

            long currentTime = System.currentTimeMillis();
            if (lastExecTime + MIN_EXECUTION_INTERVAL > currentTime) {
                Log.runtime("执行间隔较短，跳过执行");
                scheduleNextExecution(currentTime);
                return;
            }

            String currentUid = UserMap.getCurrentUid();
            String targetUid = AppContext.getUserId();
            if (targetUid == null || !targetUid.equals(currentUid)) {
                Log.runtime("用户切换或为空，重新登录");
                LifecycleManager.reLogin();
                return;
            }

            Log.runtime("⚡ 开始执行");
            lastExecTime = currentTime;

            ModelTask.startAllTask(false);
            scheduleNextExecution(lastExecTime);

        } catch (Exception e) {
            Log.runtime(TAG, "❌执行异常");
            Log.printStackTrace(TAG, e);
            scheduleNextExecution(System.currentTimeMillis());
        }
    }

    /**
     * 调度定时执行（线程安全版本）
     */
    private static void scheduleNextExecution(long execTime) {
        // 防止重复调度
        if (!isScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            int checkInterval = BaseModel.getCheckInterval().getValue();
            long delayMillis = checkInterval;

            try {
                List<String> execAtTimeList = BaseModel.getExecAtTimeList().getValue();
                if (execAtTimeList != null && !execAtTimeList.isEmpty()) {
                    Calendar lastExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(execTime);
                    Calendar nextExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(execTime + checkInterval);

                    for (String execAtTime : execAtTimeList) {
                        Calendar execAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(execAtTime);
                        if (execAtTimeCalendar != null
                                && lastExecTimeCalendar.compareTo(execAtTimeCalendar) < 0
                                && nextExecTimeCalendar.compareTo(execAtTimeCalendar) > 0) {
                            delayMillis = execAtTimeCalendar.getTimeInMillis() - execTime;
                            Log.runtime("设置定时执行:" + execAtTime);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.runtime("execAtTime err：" + e.getMessage());
                Log.printStackTrace(TAG, e);
            }

            scheduleDelayedExecution(delayMillis);
        } catch (Exception e) {
            isScheduled.set(false);
            Log.runtime(TAG, "scheduleNextExecution err：" + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
    }

    /**
     * 使用调度器延迟执行任务
     */
    private static void scheduleDelayedExecution(long delayMillis) {
        delayMillis = Math.max(delayMillis, 1000L);

        synchronized (schedulerLock) {
            try {
                if (scheduledTask != null && !scheduledTask.isDone()) {
                    scheduledTask.cancel(false);
                    scheduledTask = null;
                }

                ScheduledExecutorService sched = getScheduler();
                if (sched == null || sched.isShutdown()) {
                    isScheduled.set(false);
                    return;
                }

                final long finalDelay = delayMillis;
                scheduledTask = sched.schedule(() -> {
                    isScheduled.set(false);
                    executeTaskWrapper();
                }, delayMillis, TimeUnit.MILLISECONDS);

                try {
                    Notify.updateNextExecText(System.currentTimeMillis() + finalDelay);
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            } catch (RejectedExecutionException e) {
                isScheduled.set(false);
            } catch (Exception e) {
                Log.printStackTrace(TAG, e);
                isScheduled.set(false);
            }
        }
    }

    /**
     * 包装执行主任务的方法，处理线程安全
     */
    private static void executeTaskWrapper() {
        if (isShuttingDown.get()) {
            return;
        }

        if (!isExecuting.compareAndSet(false, true)) {
            return;
        }

        ExecutorService executor = getTaskExecutor();
        if (executor == null || executor.isShutdown()) {
            isExecuting.set(false);
            return;
        }

        try {
            executor.submit(() -> {
                try {
                    executeMainTask();
                } finally {
                    isExecuting.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            isExecuting.set(false);
        }
    }

    /**
     * 执行任务（公共方法）
     */
    public static void executeTask() {
        executeTaskWrapper();
    }

    /**
     * 延迟执行任务（公共方法）
     */
    public static void executeDelayedTask(long delayMillis) {
        if (isShuttingDown.get()) {
            return;
        }

        delayMillis = Math.max(delayMillis, 1000L);

        synchronized (schedulerLock) {
            try {
                if (scheduledTask != null && !scheduledTask.isDone()) {
                    scheduledTask.cancel(false);
                    scheduledTask = null;
                }

                ScheduledExecutorService sched = getScheduler();
                if (sched == null || sched.isShutdown()) {
                    return;
                }

                isScheduled.set(true);
                final long finalDelay = delayMillis;
                scheduledTask = sched.schedule(() -> {
                    isScheduled.set(false);
                    executeTask();
                }, delayMillis, TimeUnit.MILLISECONDS);

                try {
                    Notify.updateNextExecText(System.currentTimeMillis() + finalDelay);
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            } catch (RejectedExecutionException e) {
                isScheduled.set(false);
            } catch (Exception e) {
                Log.printStackTrace(TAG, e);
                isScheduled.set(false);
            }
        }
    }

    /**
     * 取消调度任务
     */
    public static void cancelScheduledTask() {
        synchronized (schedulerLock) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
                scheduledTask = null;
            }
            isScheduled.set(false);
        }
    }

    /**
     * 检查是否正在执行
     */
    public static boolean isExecuting() {
        return isExecuting.get();
    }

    /**
     * 检查是否已调度
     */
    public static boolean isScheduled() {
        return isScheduled.get();
    }
}
