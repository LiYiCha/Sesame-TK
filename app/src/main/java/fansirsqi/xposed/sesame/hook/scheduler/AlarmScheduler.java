package fansirsqi.xposed.sesame.hook.scheduler;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fansirsqi.xposed.sesame.hook.context.AppContext;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.TimeUtil;

/**
 * 闹钟调度器
 * 负责管理 AlarmManager 相关功能，处理定时唤醒任务
 */
public class AlarmScheduler {
    private static final String TAG = AlarmScheduler.class.getSimpleName();

    private static final Map<String, PendingIntent> wakenAtTimeAlarmMap = new ConcurrentHashMap<>();
    private static PendingIntent alarm0Pi;

    // 精确唤醒任务的回调和 PendingIntent 存储
    private static final Map<String, Runnable> exactAlarmCallbacks = new ConcurrentHashMap<>();
    private static final Map<String, PendingIntent> exactAlarmPendingIntents = new ConcurrentHashMap<>();

    /**
     * 设置定时唤醒闹钟
     */
    public static void setWakenAtTimeAlarm() {
        try {
            unsetWakenAtTimeAlarm();
            try {
                Context context = AppContext.getContext();
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                    new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                if (setAlarmTask(calendar.getTimeInMillis(), pendingIntent)) {
                    alarm0Pi = pendingIntent;
                    Log.runtime("⏰ 设置定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.runtime(TAG, "setWakenAt0 err:");
                Log.printStackTrace(TAG, e);
            }
            List<String> wakenAtTimeList = BaseModel.getWakenAtTimeList().getValue();
            if (wakenAtTimeList != null && !wakenAtTimeList.isEmpty()) {
                Calendar nowCalendar = Calendar.getInstance();
                Context context = AppContext.getContext();
                for (int i = 1, len = wakenAtTimeList.size(); i < len; i++) {
                    try {
                        String wakenAtTime = wakenAtTimeList.get(i);
                        Calendar wakenAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(wakenAtTime);
                        if (wakenAtTimeCalendar != null) {
                            if (wakenAtTimeCalendar.compareTo(nowCalendar) > 0) {
                                PendingIntent wakenAtTimePendingIntent = PendingIntent.getBroadcast(context, i,
                                    new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                                if (setAlarmTask(wakenAtTimeCalendar.getTimeInMillis(), wakenAtTimePendingIntent)) {
                                    String wakenAtTimeKey = i + "|" + wakenAtTime;
                                    wakenAtTimeAlarmMap.put(wakenAtTimeKey, wakenAtTimePendingIntent);
                                    Log.runtime("⏰ 设置定时唤醒:" + wakenAtTimeKey);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.runtime(TAG, "setWakenAtTime err:");
                        Log.printStackTrace(TAG, e);
                    }
                }
            }
        } catch (Exception e) {
            Log.runtime(TAG, "setWakenAtTimeAlarm err:");
            Log.printStackTrace(TAG, e);
        }
    }

    /**
     * 取消定时唤醒闹钟
     */
    public static void unsetWakenAtTimeAlarm() {
        try {
            for (Map.Entry<String, PendingIntent> entry : wakenAtTimeAlarmMap.entrySet()) {
                try {
                    String wakenAtTimeKey = entry.getKey();
                    PendingIntent wakenAtTimePendingIntent = entry.getValue();
                    if (unsetAlarmTask(wakenAtTimePendingIntent)) {
                        wakenAtTimeAlarmMap.remove(wakenAtTimeKey);
                        Log.runtime("⏰ 取消定时唤醒:" + wakenAtTimeKey);
                    }
                } catch (Exception e) {
                    Log.runtime(TAG, "unsetWakenAtTime err:");
                    Log.printStackTrace(TAG, e);
                }
            }
            try {
                if (unsetAlarmTask(alarm0Pi)) {
                    alarm0Pi = null;
                    Log.runtime("⏰ 取消定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.runtime(TAG, "unsetWakenAt0 err:");
                Log.printStackTrace(TAG, e);
            }
        } catch (Exception e) {
            Log.runtime(TAG, "unsetWakenAtTimeAlarm err:");
            Log.printStackTrace(TAG, e);
        }
    }

    /**
     * 设置闹钟任务
     */
    @SuppressLint({"ScheduleExactAlarm", "ObsoleteSdkInt", "MissingPermission"})
    private static Boolean setAlarmTask(long triggerAtMillis, PendingIntent operation) {
        try {
            Context context = AppContext.getContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
            Log.runtime("setAlarmTask triggerAtMillis:" +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(triggerAtMillis) +
                " operation:" + operation);
            return true;
        } catch (Throwable th) {
            Log.runtime(TAG, "setAlarmTask err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    /**
     * 取消闹钟任务
     */
    private static Boolean unsetAlarmTask(PendingIntent operation) {
        try {
            if (operation != null) {
                Context context = AppContext.getContext();
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(operation);
            }
            return true;
        } catch (Throwable th) {
            Log.runtime(TAG, "unsetAlarmTask err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    /**
     * 设置精确唤醒任务（公开方法，供其他模块使用）
     * 使用 AlarmManager.setExactAndAllowWhileIdle 确保在 Doze 模式下也能准时唤醒
     *
     * @param taskId 任务唯一标识，用于后续取消
     * @param triggerAtMillis 触发时间（毫秒时间戳）
     * @param callback 唤醒时执行的回调
     * @return 是否设置成功
     */
    @SuppressLint({"ScheduleExactAlarm", "ObsoleteSdkInt", "MissingPermission"})
    public static Boolean scheduleExactAlarm(String taskId, long triggerAtMillis, Runnable callback) {
        try {
            Context context = AppContext.getContext();
            if (context == null) {
                Log.error(TAG, "scheduleExactAlarm: context is null");
                return false;
            }

            // 创建一个唯一的 requestCode
            int requestCode = taskId.hashCode();

            // 存储回调
            exactAlarmCallbacks.put(taskId, callback);

            // 创建 PendingIntent
            Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.exactAlarm");
            intent.putExtra("taskId", taskId);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, getPendingIntentFlag());

            // 存储 PendingIntent 以便后续取消
            exactAlarmPendingIntents.put(taskId, pendingIntent);

            // 设置精确闹钟
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }

            Log.runtime("scheduleExactAlarm: " + taskId + " at " +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(triggerAtMillis));
            return true;
        } catch (Throwable th) {
            Log.error(TAG, "scheduleExactAlarm err: " + th.getMessage());
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    /**
     * 取消精确唤醒任务
     *
     * @param taskId 任务唯一标识
     * @return 是否取消成功
     */
    public static Boolean cancelExactAlarm(String taskId) {
        try {
            PendingIntent pendingIntent = exactAlarmPendingIntents.remove(taskId);
            exactAlarmCallbacks.remove(taskId);

            Context context = AppContext.getContext();
            if (pendingIntent != null && context != null) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(pendingIntent);
                Log.runtime("cancelExactAlarm: " + taskId);
                return true;
            }
        } catch (Throwable th) {
            Log.error(TAG, "cancelExactAlarm err: " + th.getMessage());
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    /**
     * 取消所有精确唤醒任务
     */
    public static void cancelAllExactAlarms() {
        try {
            for (String taskId : exactAlarmPendingIntents.keySet()) {
                cancelExactAlarm(taskId);
            }
            exactAlarmPendingIntents.clear();
            exactAlarmCallbacks.clear();
            Log.runtime("cancelAllExactAlarms: 已取消所有精确唤醒任务");
        } catch (Throwable th) {
            Log.error(TAG, "cancelAllExactAlarms err: " + th.getMessage());
        }
    }

    /**
     * 处理精确闹钟触发（内部方法，由广播接收器调用）
     */
    public static void handleExactAlarmTrigger(String taskId) {
        Runnable callback = exactAlarmCallbacks.remove(taskId);
        exactAlarmPendingIntents.remove(taskId);

        if (callback != null) {
            try {
                Log.runtime("handleExactAlarmTrigger: " + taskId);
                callback.run();
            } catch (Throwable th) {
                Log.error(TAG, "handleExactAlarmTrigger callback err: " + th.getMessage());
                Log.printStackTrace(TAG, th);
            }
        }
    }

    /**
     * 获取 PendingIntent 标志
     */
    @SuppressLint("ObsoleteSdkInt")
    private static int getPendingIntentFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
            return PendingIntent.FLAG_UPDATE_CURRENT;
        }
    }
}
