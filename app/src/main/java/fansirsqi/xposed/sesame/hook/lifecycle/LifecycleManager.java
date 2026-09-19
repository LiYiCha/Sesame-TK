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
import fansirsqi.xposed.sesame.hook.core.modules.MiscHookModule;
import fansirsqi.xposed.sesame.hook.network.HttpCaptureHook;
import fansirsqi.xposed.sesame.hook.network.NetworkHook;
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
import fansirsqi.xposed.sesame.util.CaptureFilter;
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
    private static final java.util.Map<String, Long> h5BridgePending = new java.util.concurrent.ConcurrentHashMap<>();

    private static String normalizeReqDataString(Object reqData) {
        if (reqData == null) return "";
        if (reqData instanceof byte[]) {
            try {
                return new String((byte[]) reqData, java.nio.charset.StandardCharsets.UTF_8).trim();
            } catch (Throwable ignored) {}
        }
        return String.valueOf(reqData).trim();
    }

    private static String extractCoreParamsSignature(Object obj) {
        if (obj == null) return "";
        try {
            if (obj instanceof org.json.JSONObject) {
                org.json.JSONObject jo = (org.json.JSONObject) obj;
                Object reqData = jo.opt("requestData");
                if (reqData != null) return normalizeReqDataString(reqData);
            }
            if (obj instanceof java.util.Map) {
                Object reqData = ((java.util.Map<?, ?>) obj).get("requestData");
                if (reqData != null) return normalizeReqDataString(reqData);
            }
            if (obj.getClass().getName().contains("JSONObject")) {
                try {
                    Object reqData = XposedHelpers.callMethod(obj, "get", "requestData");
                    if (reqData != null) return normalizeReqDataString(reqData);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return normalizeReqDataString(obj);
    }

    private static void markH5BridgePending(String method, Object requestContext) {
        if (method == null || method.isEmpty()) return;
        long now = System.currentTimeMillis();
        // 1. 核心参数签名
        String coreParams = extractCoreParamsSignature(requestContext);
        if (!coreParams.isEmpty()) {
            h5BridgePending.put(method + "@" + coreParams.hashCode(), now);
        }
        // 2. 兜底记录方法名
        h5BridgePending.put(method, now);
        if (h5BridgePending.size() > 60) {
            h5BridgePending.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
        }
    }

    private static boolean isH5HandledByBridge(String method, Object reqData) {
        if (method == null || method.isEmpty()) return false;
        long now = System.currentTimeMillis();
        // 1. 优先按“方法名 + 核心业务参数签名”精确匹配去重
        if (reqData != null) {
            String norm = normalizeReqDataString(reqData);
            if (!norm.isEmpty()) {
                String keyWithParam = method + "@" + norm.hashCode();
                Long time = h5BridgePending.get(keyWithParam);
                if (time != null && (now - time) < 10000) {
                    return true;
                }
            }
        }
        // 2. 兜底匹配：同一方法在 5 秒内由上层 Bridge 接管
        Long time = h5BridgePending.get(method);
        return time != null && (now - time) < 5000;
    }

    private static void removeH5BridgePending(String method, Object requestContext) {
        if (method == null) return;
        h5BridgePending.remove(method);
        if (requestContext != null) {
            String core = extractCoreParamsSignature(requestContext);
            if (!core.isEmpty()) {
                h5BridgePending.remove(method + "@" + core.hashCode());
            }
        }
    }

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
            if (TaskScheduler.isStopped() && (force == null || !force)) {
                Log.runtime(TAG, "⏸ 任务已被用户停止，跳过 initHandler 自动重载与执行");
                return false;
            }
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
                //抓包调试模式（需在 DataStore 初始化后开启，保证过滤配置可读取）
                DataStore.INSTANCE.init(Files.CONFIG_DIR);
                if (BaseModel.getDebugMode().getValue()) {
                    setupRpcDebugHooks();
                }
                
                // 全面网络捕获与拦截 (HttpCaptureHook & NetworkHook)
                if (BaseModel.enableHttpCapture.getValue()) {
                    HttpCaptureHook.setup(AppContext.getClassLoader());
                    NetworkHook.setupHooks(AppContext.getClassLoader());
                }
                // 延迟注册动态 bundle 及登录界面 Hook
                try {
                    MiscHookModule.delayRegisterBundleHooks(AppContext.getClassLoader());
                } catch (Throwable t) {
                    Log.runtime(TAG, "delayRegisterBundleHooks 失败: " + t.getMessage());
                }
                // 启动所有模型
                Model.bootAllModel(AppContext.getClassLoader());
                Status.load(userId);
                DataCache.INSTANCE.load();
                updateDay(userId);

                String successMsg = "芝麻粒-TK 加载成功✨";
                Log.runtime(successMsg);
                Toast.show(successMsg);

            }
            offline = false;
            if (TaskScheduler.isStopped()) {
                // 用户已通过停止广播显式停止任务：保持停止粘性，只完成初始化不启动任务，
                // 防止 restart 广播/服务重建等 force=true 路径绕过停止态复活任务；
                // 只有 rerun 广播（先 setStopped(false)）才能恢复任务执行
                Log.runtime(TAG, "⏸ 任务已被用户停止，初始化完成但保持停止状态，跳过任务启动");
            } else {
                execHandler();
            }
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
    public static void stopHandler() {
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
        if ("alipay.client.executerpc".equalsIgnoreCase(opType) || "alipay.client.executerpc.bytes".equalsIgnoreCase(opType)) {
            if (paramsJson != null && !paramsJson.isEmpty()) {
                try {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("\\[\\s*\\\"([^\\\"]+)\\\"")
                            .matcher(paramsJson);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return opType;
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
            if (method.isEmpty()) return false;
            // 说明：如果日志中的 Method 已经被解析为具体的业务接口（如 com.alipay.xxx），
            // 直接校验该业务接口是否在黑名单中，避免被通用包装类名（alipay.client.executerpc）中的 "alipay.client" 误杀。
            if (!"alipay.client.executerpc".equalsIgnoreCase(method) && !"alipay.client.executerpc.bytes".equalsIgnoreCase(method)) {
                return isUselessRpc(method);
            }
            return isUselessRpc(resolveRpcOperation(method, params));
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
        
        // 1. Hook H5/小程序层 RpcBridgeExtension 的所有 rpc 重载方法
        try {
            ClassLoader classLoader = AppContext.getClassLoader();
            XC_MethodHook h5RpcHook = new XC_MethodHook() {
                @SuppressLint("WakelockTimeout")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object[] args = param.args;
                    if (args == null || args.length == 0) return;
                    String method = String.valueOf(args[0]);
                    if (LifecycleManager.isUselessRpc(method)) {
                        return;
                    }
                    Object callback = args[args.length - 1];
                    // 智能寻找参数对象（排除方法名与最后的callback）
                    Object requestContext = null;
                    for (int i = 1; i < args.length - 1; i++) {
                        if (args[i] != null) {
                            requestContext = args[i];
                            break;
                        }
                    }
                    if (requestContext == null && args.length > 1 && args[1] != null) {
                        requestContext = args[1];
                    }
                    // 标记当前 H5 请求已由上层 Bridge 接管，通知底层 Hook 3 勿重复打印 [BOTTOM]
                    markH5BridgePending(method, requestContext);
                    
                    Object[] recordArray = new Object[3];
                    recordArray[0] = System.currentTimeMillis();
                    recordArray[1] = method;
                    recordArray[2] = requestContext;
                    rpcHookMap.put(callback, recordArray);
                }
            };

            String[] bridgeClasses = new String[] {
                    "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension",
                    "com.alibaba.ariver.jsapi.rpc.RpcBridgeExtension"
            };

            for (String clsName : bridgeClasses) {
                Class<?> cls = XposedHelpers.findClassIfExists(clsName, classLoader);
                if (cls != null) {
                    de.robv.android.xposed.XposedBridge.hookAllMethods(cls, "rpc", h5RpcHook);
                    Log.runtime(TAG, "hook " + clsName + ".rpc successfully");
                }
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "hook record request err:");
            Log.printStackTrace(TAG, t);
        }

        // 2. Hook H5/小程序层 DefaultBridgeCallback 回调
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
                                String timeStamp = String.valueOf(recordArray[0]);
                                String method = String.valueOf(recordArray[1]);
                                String params = String.valueOf(recordArray[2]);
                                String rawData = param.args[0].toString();

                                removeH5BridgePending(method, recordArray[2]);

                                // 处理RPC响应数据并提取关键信息
                                if (BaseModel.getAutoTokenEnabled().getValue()) {
                                    RpcResponseHandler.handle(method, rawData);
                                }

                                String logMessage = "\n[H5] ========================>\n" + 
                                        "TimeStamp: " + timeStamp + "\n" + 
                                        "Method: " + method + "\n" + 
                                        "Params: " + params + "\n" + 
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

        // 3. Hook底层的 RpcInvocationHandler (拦截小游戏等底层RPC请求)
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
                            boolean isH5Rpc = "alipay.client.executerpc".equalsIgnoreCase(opType) 
                                    || "alipay.client.executerpc.bytes".equalsIgnoreCase(opType) 
                                    || "executeRPC".equalsIgnoreCase(method.getName());

                            if (isH5Rpc && rpcArgs != null && rpcArgs.length > 0 && rpcArgs[0] != null) {
                                try {
                                    realOpType = String.valueOf(rpcArgs[0]);
                                } catch (Throwable ignored) {
                                }
                            }

                            // 黑名单过滤检查（H5 仅针对 realOpType 进行业务黑名单判定）
                            boolean needFilter = isH5Rpc ? LifecycleManager.isUselessRpc(realOpType) : LifecycleManager.isUselessRpc(opType);
                            if (needFilter) {
                                XposedHelpers.setAdditionalInstanceField(param, "rpc_skip", true);
                                return;
                            }

                            // 协同精准去重：若当前 H5 请求已由上层 RpcBridgeExtension (Hook 1 & 2) 完整接管，
                            // 提取底层核心入参 rpcArgs[1]，与上层 requestData 进行核心签名比对；若一致底层直接跳过
                            Object reqData = (rpcArgs != null && rpcArgs.length >= 2) ? rpcArgs[1] : null;
                            if (isH5Rpc && isH5HandledByBridge(realOpType, reqData)) {
                                XposedHelpers.setAdditionalInstanceField(param, "rpc_skip", true);
                                return;
                            }

                            XposedHelpers.setAdditionalInstanceField(param, "isH5Rpc", isH5Rpc);
                            XposedHelpers.setAdditionalInstanceField(param, "opType", realOpType);
                            XposedHelpers.setAdditionalInstanceField(param, "startTime", System.currentTimeMillis());
                            
                            // 3. 序列化入参
                            String paramsJson = "";
                            try {
                                if (isH5Rpc && reqData != null) {
                                    if (reqData instanceof String) {
                                        paramsJson = (String) reqData;
                                    } else if (reqData instanceof byte[]) {
                                        try {
                                            paramsJson = new String((byte[]) reqData, java.nio.charset.StandardCharsets.UTF_8);
                                        } catch (Throwable e) {
                                            paramsJson = String.valueOf(reqData);
                                        }
                                    } else {
                                        try {
                                            Class<?> jsonClass = cachedFastJsonClass;
                                            if (jsonClass == null) {
                                                jsonClass = classLoader.loadClass("com.alibaba.fastjson.JSON");
                                                cachedFastJsonClass = jsonClass;
                                            }
                                            paramsJson = (String) XposedHelpers.callStaticMethod(jsonClass, "toJSONString", reqData);
                                        } catch (Throwable e) {
                                            try {
                                                paramsJson = reflectDump(reqData);
                                            } catch (Throwable ex) {
                                                paramsJson = String.valueOf(reqData);
                                            }
                                        }
                                    }
                                    if (paramsJson == null || paramsJson.isEmpty()) {
                                        paramsJson = String.valueOf(reqData);
                                    }
                                } else if (rpcArgs != null && rpcArgs.length > 0) {
                                    try {
                                        Class<?> jsonClass = cachedFastJsonClass;
                                        if (jsonClass == null) {
                                            jsonClass = classLoader.loadClass("com.alibaba.fastjson.JSON");
                                            cachedFastJsonClass = jsonClass;
                                        }
                                        paramsJson = (String) XposedHelpers.callStaticMethod(jsonClass, "toJSONString", (Object) rpcArgs);
                                    } catch (Throwable e) {
                                        java.util.List<String> list = new java.util.ArrayList<>();
                                        for (Object arg : rpcArgs) {
                                            if (arg == null) {
                                                list.add("null");
                                            } else {
                                                try {
                                                    String dumped = reflectDump(arg);
                                                    list.add(dumped != null ? dumped : String.valueOf(arg));
                                                } catch (Throwable ex) {
                                                    list.add(String.valueOf(arg));
                                                }
                                            }
                                        }
                                        paramsJson = list.toString();
                                    }
                                } else {
                                    paramsJson = "[]";
                                }
                            } catch (Throwable t) {
                                if (rpcArgs != null && rpcArgs.length > 0) {
                                    try {
                                        java.util.List<String> list = new java.util.ArrayList<>();
                                        for (Object arg : rpcArgs) {
                                            list.add(arg == null ? "null" : String.valueOf(arg));
                                        }
                                        paramsJson = list.toString();
                                    } catch (Throwable ignored) {
                                        paramsJson = "[]";
                                    }
                                } else {
                                    paramsJson = "[]";
                                }
                            }
                            XposedHelpers.setAdditionalInstanceField(param, "paramsJson", paramsJson);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // 检查跳过标志（去重或已过滤）
                            if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(param, "rpc_skip"))) {
                                return;
                            }

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
                            
                            boolean isH5 = Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(param, "isH5Rpc"));
                            if (isH5) {
                                // 兜底处理未经过上层 Bridge 的底层小游戏等 RPC 请求的 Token 提取
                                if (BaseModel.getAutoTokenEnabled().getValue()) {
                                    RpcResponseHandler.handle(opType, responseJson);
                                }
                            }

                            String logPrefix = isH5 ? "[H5]" : "[BOTTOM]";
                            String logMessage = "\n" + logPrefix + " ========================>\n" + 
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

    private static boolean isUselessRpc(String opType) {
        return CaptureFilter.INSTANCE.isFiltered(opType);
    }

    private static String reflectDump(Object obj) {
        return reflectDump(obj, 0);
    }

    private static String reflectDump(Object obj, int depth) {
        if (obj == null) return "null";
        if (depth > 3) return "\"[MAX_DEPTH]\"";
        try {
            if (obj instanceof String) return "\"" + obj + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
            if (obj.getClass().isEnum()) return "\"" + obj.toString() + "\"";

            if (obj.getClass().isArray()) {
                StringBuilder sb = new StringBuilder("[");
                int length = java.lang.reflect.Array.getLength(obj);
                for (int i = 0; i < length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(reflectDump(java.lang.reflect.Array.get(obj, i), depth + 1));
                }
                sb.append("]");
                return sb.toString();
            }

            if (obj instanceof java.util.Collection) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Object item : (java.util.Collection<?>) obj) {
                    if (!first) sb.append(", ");
                    sb.append(reflectDump(item, depth + 1));
                    first = false;
                }
                sb.append("]");
                return sb.toString();
            }

            if (obj instanceof java.util.Map) {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) obj).entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append("\"").append(String.valueOf(entry.getKey())).append("\": ");
                    sb.append(reflectDump(entry.getValue(), depth + 1));
                    first = false;
                }
                sb.append("}");
                return sb.toString();
            }

            StringBuilder sb = new StringBuilder("{");
            Class<?> clazz = obj.getClass();
            boolean first = true;
            while (clazz != null && clazz != Object.class) {
                java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    if (!first) sb.append(", ");
                    sb.append("\"").append(field.getName()).append("\": ");
                    sb.append(reflectDump(val, depth + 1));
                    first = false;
                }
                clazz = clazz.getSuperclass();
            }
            sb.append("}");
            return sb.toString();
        } catch (Throwable t) {
            return "\"[Exception in reflectDump: " + t.getMessage() + "]\"";
        }
    }

    /**
     * 获取 RPC Bridge
     */
    public static RpcBridge getRpcBridge() {
        return rpcBridge;
    }
}
