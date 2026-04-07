package fansirsqi.xposed.sesame.hook.lifecycle;

import android.annotation.SuppressLint;
import android.app.Service;
import android.os.Handler;
import android.os.PowerManager;

import java.util.Calendar;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import fansirsqi.xposed.sesame.data.Config;
import fansirsqi.xposed.sesame.data.DataCache;
import fansirsqi.xposed.sesame.data.General;
import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.hook.RpcResponseHandler;
import fansirsqi.xposed.sesame.hook.context.AppContext;
import fansirsqi.xposed.sesame.hook.resource.WakeLockManager;
import fansirsqi.xposed.sesame.hook.rpc.bridge.NewRpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.OldRpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcVersion;
import fansirsqi.xposed.sesame.hook.rpc.intervallimit.RpcIntervalLimit;
import fansirsqi.xposed.sesame.hook.scheduler.AlarmScheduler;
import fansirsqi.xposed.sesame.hook.scheduler.TaskScheduler;
import fansirsqi.xposed.sesame.hook.Toast;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.Model;
import fansirsqi.xposed.sesame.util.DataStore;
import fansirsqi.xposed.sesame.task.BaseTask;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.PermissionUtil;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import lombok.Getter;

/**
 * 生命周期管理器
 * 负责管理模块的初始化、销毁和执行逻辑
 */
public class LifecycleManager {
    private static final String TAG = LifecycleManager.class.getSimpleName();

    //@Getter
    private static volatile boolean init = false;
    //@Getter
    private static volatile boolean offline = false;

    static volatile Calendar dayCalendar;
    static {
        dayCalendar = Calendar.getInstance();
        dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
        dayCalendar.set(Calendar.MINUTE, 0);
        dayCalendar.set(Calendar.SECOND, 0);
    }

    private static PowerManager.WakeLock wakeLock;  // 保留用于兼容性，实际管理由 WakeLockManager 负责
    private static BaseTask mainTask;
    static RpcBridge rpcBridge;
    //@Getter
    private static RpcVersion rpcVersion;
    private static XC_MethodHook.Unhook rpcRequestUnhook;
    private static XC_MethodHook.Unhook rpcResponseUnhook;
    private static final java.util.Map<Object, Object[]> rpcHookMap = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String modelVersion = fansirsqi.xposed.sesame.BuildConfig.VERSION_NAME;

    /**
     * 设置离线状态
     */
    public static void setOffline(boolean offline) {
        LifecycleManager.offline = offline;
    }

    /**
     * 获取初始化状态（显式公共方法，确保 Kotlin 可以访问）
     */
    public static boolean isInit() {
        return init;
    }

    /**
     * 获取离线状态（显式公共方法，确保 Kotlin 可以访问）
     */
    public static boolean isOffline() {
        return offline;
    }

    /**
     * 获取 RPC 版本（显式公共方法，确保 Kotlin 可以访问）
     */
    public static RpcVersion getRpcVersion() {
        return rpcVersion;
    }

    /**
     * 初始化处理器
     */
    @SuppressLint("WakelockTimeout")
    public static synchronized Boolean initHandler(Boolean force) {
        try {
            TaskCommon.update();
            Service service = AppContext.getService();
            if (service == null) {
                return false;
            }
            if (TaskCommon.IS_MODULE_SLEEP_TIME) {
                Log.record("💤 模块休眠中,停止初始化");
                return false;
            }
            destroyHandler(force);
            if (force) {
                String userId = AppContext.getUserId();
                if (userId == null) {
                    Log.record("用户未登录");
                    Toast.show("用户未登录");
                    return false;
                }

                // 在确保支付宝相关类加载后再初始化 UserMap
                try {
                    UserMap.initUser(userId);
                    Log.runtime("UserMap initialized successfully");
                } catch (Exception e) {
                    Log.runtime("Failed to initialize UserMap: " + e);
                }
                // 启动所有模型
                Model.initAllModel();
                String startMsg = "芝麻粒-TK 开始初始化...";
                Log.record(startMsg);
                Log.record("⚙️模块版本：" + modelVersion);
                Log.record("📦应用版本：" + AppContext.getAlipayVersion().getVersionString());
                Config.load(userId);
                if (!Config.isLoaded()) {
                    Log.record("用户模块配置加载失败");
                    Toast.show("用户模块配置加载失败");
                    return false;
                }
                // ！！所有权限申请应该放在加载配置之后
                //闹钟权限申请
                if (!PermissionUtil.checkAlarmPermissions()) {
                    Log.record("❌ 目标应用无闹钟权限");
                    Handler mainHandler = AppContext.getMainHandler();
                    mainHandler.postDelayed(
                            () -> {
                                if (!PermissionUtil.checkOrRequestAlarmPermissions(AppContext.getContext())) {
                                    Toast.show("请授予目标应用使用闹钟权限");
                                }
                            },
                            2000);
                    return false;
                }
                // 检查并请求后台运行权限
                if (BaseModel.getBatteryPerm().getValue() && !init && !PermissionUtil.checkBatteryPermissions()) {
                    Log.record("目标应用无始终在后台运行权限");
                    Handler mainHandler = AppContext.getMainHandler();
                    mainHandler.postDelayed(
                            () -> {
                                if (!PermissionUtil.checkOrRequestBatteryPermissions(AppContext.getContext())) {
                                    Toast.show("请授予目标应用始终在后台运行权限");
                                }
                            },
                            2000);
                }
                Notify.start(service);
                if (!Objects.requireNonNull(Model.getModel(BaseModel.class)).getEnableField().getValue()) {
                    Log.record("❌ 芝麻粒已禁用");
                    Toast.show("❌ 芝麻粒已禁用");
                    Notify.setStatusTextDisabled();
                    return false;
                }
                // 保持唤醒锁，防止设备休眠（使用 WakeLockManager 自动管理）
                if (BaseModel.getStayAwake().getValue()) {
                    WakeLockManager.acquire(service, service.getClass().getName());
                }
                AlarmScheduler.setWakenAtTimeAlarm();
                if (BaseModel.getNewRpc().getValue()) {
                    rpcBridge = new NewRpcBridge();
                } else {
                    rpcBridge = new OldRpcBridge();
                }
                rpcBridge.load();
                rpcVersion = rpcBridge.getVersion();
                //抓包调试模式
                if (BaseModel.getNewRpc().getValue() && BaseModel.getDebugMode().getValue()) {
                    setupRpcDebugHooks();
                }
                // 启动所有模型
                Model.bootAllModel(AppContext.getClassLoader());
                Status.load(userId);
                DataCache.INSTANCE.load();
                DataStore.INSTANCE.init(Files.CONFIG_DIR);
                updateDay(userId);

                String successMsg = "芝麻粒-TK 加载成功✨";
                Log.record(successMsg);
                Toast.show(successMsg);

            }
            offline = false;
            execHandler();
            init = true;
            return true;
        } catch (Throwable th) {
            Log.runtime(TAG, "startHandler err:");
            Log.printStackTrace(TAG, th);
            Toast.show("芝麻粒加载失败 🎃");
            return false;
        }
    }

    /**
     * 销毁处理器
     */
    public static synchronized void destroyHandler(Boolean force) {
        try {
            if (force) {
                // 关闭执行器和调度器
                TaskScheduler.shutdownExecutors();

                Service service = AppContext.getService();
                if (service != null) {
                    stopHandler();
                    BaseModel.destroyData();
                    Status.unload();
                    Notify.stop();
                    RpcIntervalLimit.INSTANCE.clearIntervalLimit();
                    Config.unload();
                    Model.destroyAllModel();
                    UserMap.unload();
                }

                // 重置 mainTask
                mainTask = null;

                if (rpcResponseUnhook != null) {
                    try {
                        rpcResponseUnhook.unhook();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                }
                if (rpcRequestUnhook != null) {
                    try {
                        rpcRequestUnhook.unhook();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                }
                // 释放 WakeLock（使用 WakeLockManager 自动管理）
                WakeLockManager.release();
                if (rpcBridge != null) {
                    rpcVersion = null;
                    rpcBridge.unload();
                    rpcBridge = null;
                }
                init = false;
            } else {
                ModelTask.stopAllTask();
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "stopHandler err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 执行处理器
     */
    static void execHandler() {
        // 确保 mainTask 已初始化
        if (mainTask == null) {
            mainTask = BaseTask.newInstance("MAIN_TASK", TaskScheduler::executeTask);
        }
        mainTask.startTask(false);
    }

    /**
     * 延迟执行处理器
     */
    static void execDelayedHandler(long delayMillis) {
        TaskScheduler.executeDelayedTask(delayMillis);
    }

    /**
     * 停止处理器
     */
    private static void stopHandler() {
        if (mainTask != null) {
            mainTask.stopTask();
        }
        ModelTask.stopAllTask();
        TaskScheduler.cancelScheduledTask();
    }

    /**
     * 更新日期
     */
    public static void updateDay(String userId) {
        Calendar nowCalendar = Calendar.getInstance();
        try {
            // 修复空指针异常：确保dayCalendar不为null
            if (dayCalendar == null) {
                dayCalendar = (Calendar) nowCalendar.clone();
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                dayCalendar.set(Calendar.MINUTE, 0);
                dayCalendar.set(Calendar.SECOND, 0);
                Log.record("初始化日期为：" + dayCalendar.get(Calendar.YEAR) + "-" + (dayCalendar.get(Calendar.MONTH) + 1) + "-" + dayCalendar.get(Calendar.DAY_OF_MONTH));
                AlarmScheduler.setWakenAtTimeAlarm();
                return;
            }

            int nowYear = nowCalendar.get(Calendar.YEAR);
            int nowMonth = nowCalendar.get(Calendar.MONTH);
            int nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH);
            if (dayCalendar.get(Calendar.YEAR) != nowYear || dayCalendar.get(Calendar.MONTH) != nowMonth || dayCalendar.get(Calendar.DAY_OF_MONTH) != nowDay) {
                dayCalendar = (Calendar) nowCalendar.clone();
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                dayCalendar.set(Calendar.MINUTE, 0);
                dayCalendar.set(Calendar.SECOND, 0);
                Log.record("日期更新为：" + nowYear + "-" + (nowMonth + 1) + "-" + nowDay);
                AlarmScheduler.setWakenAtTimeAlarm();
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Status.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            //FriendWatch.updateDay(userId);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /**
     * 重新登录
     */
    public static void reLogin() {
        Handler mainHandler = AppContext.getMainHandler();
        mainHandler.post(
                () -> {
                    execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), 180_000));
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    intent.setClassName(General.PACKAGE_NAME, General.CURRENT_USING_ACTIVITY);
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    offline = true;
                    AppContext.getContext().startActivity(intent);
                });
    }

    /**
     * 设置RPC调试钩子
     */
    @SuppressLint("WakelockTimeout")
    private static void setupRpcDebugHooks() {
        try {
            ClassLoader classLoader = AppContext.getClassLoader();

            rpcRequestUnhook = XposedHelpers.findAndHookMethod(
                    "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension", classLoader
                    , "rpc"
                    , String.class, boolean.class, boolean.class, String.class, classLoader.loadClass(General.JSON_OBJECT_NAME), String.class, classLoader.loadClass(General.JSON_OBJECT_NAME), boolean.class, boolean.class, int.class, boolean.class, String.class, classLoader.loadClass("com.alibaba" +
                            ".ariver.app.api.App"), classLoader.loadClass("com.alibaba.ariver.app.api.Page"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.extension.BridgeCallback")
                    , new XC_MethodHook() {
                        @SuppressLint("WakelockTimeout")
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object[] args = param.args;
                            Object object = args[15];
                            Object[] recordArray = new Object[4];
                            recordArray[0] = System.currentTimeMillis();
                            recordArray[1] = args[0];
                            recordArray[2] = args[4];
                            rpcHookMap.put(object, recordArray);
                        }

                        @SuppressLint("WakelockTimeout")
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object object = param.args[15];
                            Object[] recordArray = rpcHookMap.remove(object);
                            if (recordArray != null) {
                                String TimeStamp = String.valueOf(recordArray[0]);
                                String Method = String.valueOf(recordArray[1]);
                                String Params = String.valueOf(recordArray[2]);
                                Object rawDataObj = recordArray[3];

                                // 处理RPC响应数据
                                if (BaseModel.getAutoTokenEnabled().getValue()) {
                                    RpcResponseHandler.handle(Method, Params);
                                }

                                // 只有在 rawDataObj 不为 null 时才记录日志
                                if (rawDataObj != null) {
                                    String rawData = String.valueOf(rawDataObj);
                                    String logMessage = "\n========================>\n" + "TimeStamp: " + TimeStamp + "\n" + "Method: " + Method + "\n" + "Params: " + Params + "\n" + "Data: " + rawData + "\n<========================\n";
                                    Log.capture(logMessage);
                                }
                            } else {
                                Log.capture("delete record ID: " + object.hashCode());
                            }
                        }
                    });
            Log.runtime(TAG, "hook record request successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "hook record request err:");
            Log.printStackTrace(TAG, t);
        }
        try {
            ClassLoader classLoader = AppContext.getClassLoader();
            rpcResponseUnhook = XposedHelpers.findAndHookMethod(
                    "com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback", classLoader
                    , "sendJSONResponse"
                    , classLoader.loadClass(General.JSON_OBJECT_NAME)
                    , new XC_MethodHook() {
                        @SuppressLint("WakelockTimeout")
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object callback = param.thisObject;
                            Object[] recordArray = rpcHookMap.get(callback);

                            if (recordArray != null && param.args.length > 0) {
                                recordArray[3] = param.args[0].toString();
                            }
                        }
                    });
            Log.runtime(TAG, "hook record response successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "hook record response err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 获取 RPC Bridge
     */
    public static RpcBridge getRpcBridge() {
        return rpcBridge;
    }
}
