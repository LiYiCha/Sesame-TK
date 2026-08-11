package fansirsqi.xposed.sesame.hook.lifecycle;

import android.annotation.SuppressLint;
import android.app.Service;
import android.os.Handler;
import android.os.PowerManager;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
    private static volatile Class<?> cachedFastJsonClass = null;
    private static final java.util.Map<Object, Object[]> rpcHookMap = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String modelVersion = fansirsqi.xposed.sesame.BuildConfig.VERSION_NAME;
    private static int retryCount = 0;

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
                Log.runtime("💤 模块休眠中,停止初始化");
                return false;
            }
            destroyHandler(force);
            if (force) {
                String userId = AppContext.getUserId();
                if (userId == null) {
                    String activeUser = fansirsqi.xposed.sesame.util.Files.getActiveUser();
                    if (activeUser != null && retryCount < 5) {
                        retryCount++;
                        Log.runtime("有已保存的活跃用户(" + activeUser + ")，但当前获取为null，可能是服务未就绪，将在5秒后重试(" + retryCount + "/5)...");
                        AppContext.getMainHandler().postDelayed(() -> {
                            if (!init) {
                                initHandler(force);
                            }
                        }, 5000);
                    } else {
                        Log.runtime("用户未登录");
                        Toast.show("用户未登录");
                    }
                    return false;
                }
                retryCount = 0; // 重置重试计数器
                fansirsqi.xposed.sesame.util.Files.saveActiveUser(userId);

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
                Log.runtime(startMsg);
                Log.runtime("⚙️模块版本：" + modelVersion);
                Log.runtime("📦应用版本：" + AppContext.getAlipayVersion().getVersionString());
                Config.load(userId);
                if (!Config.isLoaded()) {
                    Log.runtime("用户模块配置加载失败");
                    Toast.show("用户模块配置加载失败");
                    return false;
                }
                // ！！所有权限申请应该放在加载配置之后
                //闹钟权限申请
                if (!PermissionUtil.checkAlarmPermissions()) {
                    Log.runtime("❌ 目标应用无闹钟权限");
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
                    Log.runtime("目标应用无始终在后台运行权限");
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
                    Log.runtime("❌ 芝麻粒已禁用");
                    Toast.show("❌ 芝麻粒已禁用");
                    Notify.setStatusTextDisabled();
                    return false;
                }
                // 保持唤醒锁，防止设备休眠（使用 WakeLockManager 自动管理）
                if (BaseModel.getStayAwake().getValue()) {
                    WakeLockManager.acquire(service, service.getClass().getName());
                }
                AlarmScheduler.setWakenAtTimeAlarm();
                rpcBridge = new NewRpcBridge();
                rpcBridge.load();
                rpcVersion = rpcBridge.getVersion();
                //抓包调试模式
                if (BaseModel.getDebugMode().getValue()) {
                    setupRpcDebugHooks();
                }
                
                // 全面网络捕获与拦截 (HttpCaptureHook & NetworkHook)
                if (BaseModel.enableHttpCapture.getValue()) {
                    fansirsqi.xposed.sesame.hook.network.HttpCaptureHook.setup(AppContext.getClassLoader());
                    fansirsqi.xposed.sesame.hook.network.NetworkHook.setupHooks(AppContext.getClassLoader());
                }
                // 延迟注册动态 bundle 及登录界面 Hook
                try {
                    fansirsqi.xposed.sesame.hook.core.modules.MiscHookModule.delayRegisterBundleHooks(AppContext.getClassLoader());
                } catch (Throwable t) {
                    Log.runtime(TAG, "delayRegisterBundleHooks 失败: " + t.getMessage());
                }
                // 启动所有模型
                Model.bootAllModel(AppContext.getClassLoader());
                Status.load(userId);
                DataCache.INSTANCE.load();
                DataStore.INSTANCE.init(Files.CONFIG_DIR);
                updateDay(userId);

                String successMsg = "芝麻粒-TK 加载成功✨";
                Log.runtime(successMsg);
                Toast.show(successMsg);

            }
            offline = false;
            TaskScheduler.setStopped(false);
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
                    rpcRequestUnhook = null;
                }
                if (rpcInvocationUnhook != null) {
                    try {
                        rpcInvocationUnhook.unhook();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    rpcInvocationUnhook = null;
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
                Log.runtime("初始化日期为：" + dayCalendar.get(Calendar.YEAR) + "-" + (dayCalendar.get(Calendar.MONTH) + 1) + "-" + dayCalendar.get(Calendar.DAY_OF_MONTH));
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
                Log.runtime("日期更新为：" + nowYear + "-" + (nowMonth + 1) + "-" + nowDay);
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
//        try {
//            //FriendWatch.updateDay(userId);
//        } catch (Exception e) {
//            Log.printStackTrace(e);
//        }
    }

    private static volatile boolean isAlipayLoginActive = false;
    private static long lastReLoginTime = 0;

    public static void setAlipayLoginActive(boolean active) {
        isAlipayLoginActive = active;
        Log.runtime("LifecycleManager", "AlipayLogin 活跃状态变更为: " + active);
    }

    /**
     * 重新登录
     */
    public static void reLogin() {
        if (isAlipayLoginActive) {
            Log.runtime("LifecycleManager", "AlipayLogin 登录页面已处于活跃状态，忽略重复拉起请求");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastReLoginTime < 30000) {
            Log.runtime("LifecycleManager", "重新登录请求过于频繁，忽略本次拉起");
            return;
        }
        lastReLoginTime = currentTime;

        Handler mainHandler = AppContext.getMainHandler();
        mainHandler.post(
                () -> {
                    try {
                        execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), 180_000));
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                        intent.setClassName(General.PACKAGE_NAME, General.CURRENT_USING_ACTIVITY);
                        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        offline = true;
                        AppContext.getContext().startActivity(intent);
                    } catch (Throwable t) {
                        Log.runtime("LifecycleManager", "拉起登录页面异常: " + t.getMessage());
                    }
                });
    }

    private static boolean logReceiverRegistered = false;
    private static XC_MethodHook.Unhook rpcInvocationUnhook = null;

    public static boolean isMainProcess() {
        try {
            android.content.Context context = AppContext.getAppContext();
            if (context == null) return true;
            String processName = getProcessName(context);
            return context.getPackageName().equals(processName);
        } catch (Throwable t) {
            return true;
        }
    }

    private static String getProcessName(android.content.Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return android.app.Application.getProcessName();
        }
        try {
            Class<?> clazz = Class.forName("android.app.ActivityThread");
            Object currentActivityThread = clazz.getDeclaredMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Method getProcessName = clazz.getDeclaredMethod("getProcessName");
            return (String) getProcessName.invoke(currentActivityThread);
        } catch (Exception e) {
            return context.getPackageName();
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public static void registerCaptureLogReceiver() {
        if (logReceiverRegistered) return;
        android.content.Context context = AppContext.getAppContext();
        if (context == null) return;
        if (!isMainProcess()) return;
        try {
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                    if ("fansirsqi.xposed.sesame.WRITE_CAPTURE_LOG".equals(intent.getAction())) {
                        String logMessage = intent.getStringExtra("log_message");
                        if (logMessage != null) {
                            Log.capture(logMessage);
                        }
                    }
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter("fansirsqi.xposed.sesame.WRITE_CAPTURE_LOG");
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            logReceiverRegistered = true;
            Log.runtime(TAG, "Registered WRITE_CAPTURE_LOG receiver successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "Register WRITE_CAPTURE_LOG receiver err: " + t.getMessage());
        }
    }

    public static void writeCaptureLog(String logMessage) {
        if (LifecycleManager.isUselessRpcLog(logMessage)) {
            return;
        }
        android.content.Context context = AppContext.getAppContext();
        if (context == null) {
            Log.capture(logMessage);
            return;
        }
        if (isMainProcess()) {
            Log.capture(logMessage);
        } else {
            try {
                android.content.Intent intent = new android.content.Intent("fansirsqi.xposed.sesame.WRITE_CAPTURE_LOG");
                intent.putExtra("log_message", logMessage);
                context.sendBroadcast(intent);
            } catch (Throwable t) {
                Log.capture(logMessage);
            }
        }
    }

    private static String resolveRpcOperation(String opType, String paramsJson) {
        if (opType == null) return "";
        String resolved = opType;
        if ("alipay.client.executerpc".equalsIgnoreCase(opType) && paramsJson != null) {
            try {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("\\[\\s*\\\"([^\\\"]+)\\\"")
                        .matcher(paramsJson);
                if (matcher.find()) {
                    resolved = matcher.group(1);
                }
            } catch (Throwable ignored) {
                // Ignore
            }
        }
        return resolved == null ? "" : resolved;
    }

    private static boolean isUselessRpcLog(String logMessage) {
        if (logMessage == null || logMessage.isEmpty()) return false;
        try {
            String method = "";
            String params = "";
            for (String line : logMessage.split("\\n")) {
                if (line.startsWith("Method: ")) {
                    method = line.substring("Method: ".length()).trim();
                } else if (line.startsWith("Params: ")) {
                    params = line.substring("Params: ".length()).trim();
                }
            }
            return isUselessRpc(method, params);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 设置RPC调试钩子
     */
    @SuppressLint("WakelockTimeout")
    public static void setupRpcDebugHooks() {
        registerCaptureLogReceiver();
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
                            String Method = String.valueOf(args[0]);
                            if (LifecycleManager.isUselessRpc(Method)) {
                                return;
                            }
                            // args[4] 是 @BindingRequest JSONObject，包含完整请求体
                            // 需要从中提取 requestData 字段获取真正的业务参数
                            String paramsStr = "";
                            try {
                                Object requestJson = args[4];
                                if (requestJson != null) {
                                    // 先尝试提取 requestData 字段（真正的业务参数）
                                    Object requestData = XposedHelpers.callMethod(requestJson, "get", "requestData");
                                    if (requestData != null) {
                                        paramsStr = String.valueOf(requestData);
                                    } else {
                                        // requestData 为 null，输出完整 JSONObject
                                        paramsStr = String.valueOf(XposedHelpers.callMethod(requestJson, "toJSONString"));
                                    }
                                }
                            } catch (Throwable t) {
                                // 反射失败时回退到直接 toString
                                paramsStr = args[4] != null ? args[4].toString() : "null";
                            }
                            Object[] recordArray = new Object[4];
                            recordArray[0] = System.currentTimeMillis();
                            recordArray[1] = args[0];
                            recordArray[2] = paramsStr;
                            rpcHookMap.put(object, recordArray);
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
                            Object[] recordArray = rpcHookMap.remove(callback);

                            if (recordArray != null && param.args.length > 0 && param.args[0] != null) {
                                String TimeStamp = String.valueOf(recordArray[0]);
                                String Method = String.valueOf(recordArray[1]);
                                String Params = String.valueOf(recordArray[2]);
                                String rawData = param.args[0].toString();

                                // 处理RPC响应数据并提取关键信息
                                if (BaseModel.getAutoTokenEnabled().getValue()) {
                                    RpcResponseHandler.handle(Method, rawData);
                                }

                                String logMessage = "\n[H5] ========================>\n" + 
                                        "TimeStamp: " + TimeStamp + "\n" + 
                                        "Method: " + Method + "\n" + 
                                        "Params: " + Params + "\n" + 
                                        "Data: " + rawData + "\n" + 
                                        "<========================\n";
                                writeCaptureLog(logMessage);
                            }
                        }
                    });
            Log.runtime(TAG, "hook record response successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "hook record response err:");
            Log.printStackTrace(TAG, t);
        }
        // Hook底层的 RpcInvocationHandler (拦截小游戏等底层RPC请求)
        try {
            ClassLoader classLoader = AppContext.getClassLoader();
            Class<?> rpcHandlerClass = XposedHelpers.findClassIfExists("com.alipay.mobile.common.rpc.RpcInvocationHandler", classLoader);
            if (rpcHandlerClass != null) {
                rpcInvocationUnhook = XposedHelpers.findAndHookMethod(rpcHandlerClass, "invoke", Object.class, java.lang.reflect.Method.class, Object[].class,
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Method method = (Method) param.args[1];
                            String opType = "";
                            try {
                                Class<?> operationTypeAnnClass = XposedHelpers.findClassIfExists("com.alipay.mobile.framework.service.annotation.OperationType", classLoader);
                                if (operationTypeAnnClass != null) {
                                    Annotation ann = method.getAnnotation((Class<? extends Annotation>) operationTypeAnnClass);
                                    if (ann != null) {
                                        opType = (String) XposedHelpers.callMethod(ann, "value");
                                    }
                                }
                            } catch (Throwable t) {
                                // 忽略
                            }
                            if (opType == null || opType.isEmpty()) {
                                opType = method.getName();
                            }
                            String realOpType = opType;
                            Object[] rpcArgs = (Object[]) param.args[2];
                            boolean isH5Rpc = "alipay.client.executerpc".equalsIgnoreCase(opType) || "executeRPC".equalsIgnoreCase(method.getName());

                            if (isH5Rpc && rpcArgs != null && rpcArgs.length > 0 && rpcArgs[0] != null) {
                                try {
                                    realOpType = String.valueOf(rpcArgs[0]);
                                } catch (Throwable ignored) {
                                }
                            }
                            XposedHelpers.setAdditionalInstanceField(param, "opType", realOpType);
                            XposedHelpers.setAdditionalInstanceField(param, "startTime", System.currentTimeMillis());
                            
                            if (LifecycleManager.isUselessRpc(opType) || LifecycleManager.isUselessRpc(realOpType)) {
                                return;
                            }
                            
                            // 序列化入参：对于 H5 RPC (executeRPC)，args[1] 为真正从 H5 传上来的 JSON 字符串 requestData！
                            String paramsJson = "";
                            if (isH5Rpc && rpcArgs != null && rpcArgs.length >= 2 && rpcArgs[1] != null) {
                                paramsJson = String.valueOf(rpcArgs[1]);
                            } else {
                                try {
                                    if (rpcArgs != null) {
                                        Class<?> jsonClass = cachedFastJsonClass;
                                        if (jsonClass == null) {
                                            jsonClass = classLoader.loadClass("com.alibaba.fastjson.JSON");
                                            cachedFastJsonClass = jsonClass;
                                        }
                                        paramsJson = (String) XposedHelpers.callStaticMethod(jsonClass, "toJSONString", (Object) rpcArgs);
                                    }
                                } catch (Throwable t) {
                                    try {
                                        if (rpcArgs != null) {
                                            java.util.List<String> list = new java.util.ArrayList<>();
                                            for (Object arg : rpcArgs) {
                                                list.add(arg == null ? "null" : arg.toString());
                                            }
                                            paramsJson = list.toString();
                                        } else {
                                            paramsJson = "[]";
                                        }
                                    } catch (Throwable ignored) {
                                        paramsJson = "[]";
                                    }
                                }
                            }
                            XposedHelpers.setAdditionalInstanceField(param, "paramsJson", paramsJson);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String opType = (String) XposedHelpers.getAdditionalInstanceField(param, "opType");
                            if (opType == null) return;
                            if (LifecycleManager.isUselessRpc(opType)) return;
                            
                            Long startTime = (Long) XposedHelpers.getAdditionalInstanceField(param, "startTime");
                            if (startTime == null) startTime = System.currentTimeMillis();
                            
                            String paramsJson = (String) XposedHelpers.getAdditionalInstanceField(param, "paramsJson");
                            if (paramsJson == null) paramsJson = "[]";
                            
                            String responseJson = "";
                            if (param.hasThrowable()) {
                                responseJson = "Error: " + param.getThrowable().toString();
                            } else {
                                try {
                                    Object result = param.getResult();
                                    if (result != null) {
                                        Class<?> jsonClass = cachedFastJsonClass;
                                        if (jsonClass == null) {
                                            jsonClass = classLoader.loadClass("com.alibaba.fastjson.JSON");
                                            cachedFastJsonClass = jsonClass;
                                        }
                                        responseJson = (String) XposedHelpers.callStaticMethod(jsonClass, "toJSONString", result);
                                    } else {
                                        responseJson = "null";
                                    }
                                } catch (Throwable t) {
                                    responseJson = "Error serializing: " + t.toString();
                                }
                            }
                            
                            String logMessage = "\n[BOTTOM] ========================>\n" + 
                                    "TimeStamp: " + startTime + "\n" + 
                                    "Method: " + opType + "\n" + 
                                    "Params: " + paramsJson + "\n" + 
                                    "Data: " + responseJson + "\n" + 
                                    "<========================\n";
                            
                            writeCaptureLog(logMessage);
                        }
                    });
                Log.runtime(TAG, "hook RpcInvocationHandler successfully");
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "hook RpcInvocationHandler err: " + t.getMessage());
        }
    }

    private static boolean isUselessRpc(String opType, String paramsJson) {
        return isUselessRpc(resolveRpcOperation(opType, paramsJson));
    }

    private static boolean isUselessRpc(String opType) {
        if (opType == null || opType.isEmpty()) return false;
        String lower = opType.toLowerCase();

        try {
            String filterKeywords = DataStore.INSTANCE.get("httpCaptureFilter", String.class);
            if (filterKeywords == null) {
                filterKeywords = "log.alipay.com,mdap.alipay.com,diagnose.alipay.com,alipay.client.executerpc,alipay.client.interfere.config.get,alipay.client.getDynamicBundle,alipay.client.getUnionResource";
            }
            if (filterKeywords != null && !filterKeywords.trim().isEmpty()) {
                String[] keywords = filterKeywords.split(",");
                for (String kw : keywords) {
                    String trimmed = kw.trim();
                    if (!trimmed.isEmpty() && lower.contains(trimmed.toLowerCase())) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore
        }

        return lower.contains("wireless.audit")
                || lower.contains("locate.service")
                || lower.contains("uploadlog")
                || lower.contains("log.upload")
                || lower.contains("behavior.logs")
                || lower.contains("behaviorlog")
                || lower.contains("diagnose")
                || lower.contains("reportactive")
                || lower.contains("monitor")
                || lower.contains("alipay.client")
                || lower.contains("telemetry");
    }

    /**
     * 获取 RPC Bridge
     */
    public static RpcBridge getRpcBridge() {
        return rpcBridge;
    }
}
