package fansirsqi.xposed.sesame.hook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fansirsqi.xposed.sesame.BuildConfig;
import fansirsqi.xposed.sesame.data.RunType;
import fansirsqi.xposed.sesame.data.ViewAppInfo;
import fansirsqi.xposed.sesame.entity.AlipayVersion;
import fansirsqi.xposed.sesame.hook.broadcast.SesameReceiver;
import fansirsqi.xposed.sesame.hook.context.AppContext;
import fansirsqi.xposed.sesame.hook.internal.AlipayMiniMarkHelper;
import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper;
import fansirsqi.xposed.sesame.hook.internal.LocationHelper;
import fansirsqi.xposed.sesame.hook.internal.SecurityBodyHelper;
import fansirsqi.xposed.sesame.hook.keepalive.SmartSchedulerManager;
import fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcVersion;
import fansirsqi.xposed.sesame.hook.scheduler.AlarmScheduler;
import fansirsqi.xposed.sesame.hook.scheduler.TaskScheduler;
import fansirsqi.xposed.sesame.data.General;
import fansirsqi.xposed.sesame.hook.skin.SkinHook;
import fansirsqi.xposed.sesame.hook.theme.ThemeHookV2;
import fansirsqi.xposed.sesame.util.Log;

public class ApplicationHook implements IXposedHookLoadPackage {
    //--------------------------------------------------------------
    // 公共API - 委托给新模块
    //--------------------------------------------------------------

    public static String requestString(String str, String str2) {
        return LifecycleManager.getRpcBridge().requestString(str, str2);
    }

    public static ClassLoader getClassLoader() {
        return AppContext.getClassLoader();
    }

    public static Context getAppContext() {
        return AppContext.getContext();
    }

    public static AlipayVersion getAlipayVersion() {
        return AppContext.getAlipayVersion();
    }

    public static Handler getMainHandler() {
        return AppContext.getMainHandler();
    }

    public static Boolean scheduleExactAlarm(String taskId, long triggerAtMillis, Runnable callback) {
        return AlarmScheduler.scheduleExactAlarm(taskId, triggerAtMillis, callback);
    }

    public static Boolean cancelExactAlarm(String taskId) {
        return AlarmScheduler.cancelExactAlarm(taskId);
    }

    public static void setOffline(boolean offline) {
        LifecycleManager.setOffline(offline);
    }

    public static Object getMicroApplicationContext() {
        return AppContext.getMicroApplicationContext();
    }

    public static Object getServiceObject(String service) {
        return AppContext.getServiceObject(service);
    }

    public static Object getUserObject() {
        return AppContext.getUserObject();
    }

    public static String getUserId() {
        return AppContext.getUserId();
    }

    public static void reLoginByBroadcast() {
        try {
            AppContext.getContext().sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.reLogin"));
        } catch (Throwable th) {
            Log.runtime(TAG, "sesame sendBroadcast reLogin err:");
            Log.printStackTrace(TAG, th);
        }
    }

    public static void restartByBroadcast() {
        try {
            AppContext.getContext().sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.restart"));
        } catch (Throwable th) {
            Log.runtime(TAG, "sesame sendBroadcast restart err:");
            Log.printStackTrace(TAG, th);
        }
    }

    //--------------------------------------------------------------
    // 内部字段
    //--------------------------------------------------------------

    static final String TAG = ApplicationHook.class.getSimpleName();
    //@Getter
    private static final String modelVersion = BuildConfig.VERSION_NAME;
    //@Getter
    private static volatile boolean hooked = false;
    //@Getter
    static final AtomicInteger reLoginCount = new AtomicInteger(0);

    // Explicit public getter methods for Kotlin compatibility
    public static boolean isHooked() {
        return hooked;
    }

    public static String getModelVersion() {
        return modelVersion;
    }

    public static AtomicInteger getReLoginCount() {
        return reLoginCount;
    }

    // Getter methods for delegated state
    public static boolean isInit() {
        return LifecycleManager.isInit();
    }

    public static boolean isOffline() {
        return LifecycleManager.isOffline();
    }

    public static RpcVersion getRpcVersion() {
        return LifecycleManager.getRpcVersion();
    }

    //--------------------------------------------------------------
    // Xposed Hook 入口
    //--------------------------------------------------------------

    /**
     *  优化方法
     */
    private final static Method deoptimizeMethod;
    static {
        Method m = null;
        try {
            m = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
        } catch (Throwable t) {
            XposedBridge.log("E/" + TAG + " " + android.util.Log.getStackTraceString(t));
        }
        deoptimizeMethod = m;
    }

    static void deoptimizeMethod(Class<?> c) throws InvocationTargetException, IllegalAccessException {
        for (Method m : c.getDeclaredMethods()) {
            if (deoptimizeMethod != null && m.getName().equals("makeApplicationInner")) {
                deoptimizeMethod.invoke(null, m);
                if (BuildConfig.DEBUG)
                    XposedBridge.log("D/" + TAG + " Deoptimized " + m.getName());
            }
        }
    }

    /**
     * hook入口
     * @param lpparam
     */
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("fansirsqi.xposed.sesame".equals(lpparam.packageName)) {
            try {
                // 首先尝试使用目标应用的ClassLoader加载
                Class<?> viewAppInfoClass = lpparam.classLoader.loadClass("fansirsqi.xposed.sesame.data.ViewAppInfo");
                // 注意：ViewAppInfo.kt 中的方法是 setRunType，而不是 setRunTypeByCode
                XposedHelpers.callStaticMethod(viewAppInfoClass, "setRunType", RunType.ACTIVE);
            } catch (ClassNotFoundException e) {
                // 如果找不到类，直接使用当前模块的类
                try {
                    ViewAppInfo.setRunType(RunType.ACTIVE);
                } catch (Exception ex) {
                    Log.printStackTrace(ex);
                }
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        } else if (General.PACKAGE_NAME.equals(lpparam.packageName) && General.PACKAGE_NAME.equals(lpparam.processName)) {
            // 添加应用启动检测
            if (hooked) return;
            AppContext.setClassLoader(lpparam.classLoader);

            // 定义需要检查的类
            String[] requiredClasses = {
                    "com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate",
                    "com.alipay.mobile.quinox.LauncherActivity",
                    "com.alipay.mobile.quinox.LauncherApplication",
                    "com.alipay.mobile.common.fgbg.FgBgMonitorImpl",
                    "com.alipay.mobile.common.transport.utils.MiscUtils"
            };

            // 使用ClassChecker检查类是否存在
            ClassChecker.waitForClasses(lpparam.classLoader, requiredClasses, allClassesExist -> {
                if (allClassesExist) {
                    Log.runtime(TAG, "所有必需类已加载，开始执行hook");
                    performAlipayHook(lpparam);
                } else {
                    Log.runtime(TAG, "等待类加载超时或部分类未找到，仍然尝试执行hook");
                    performAlipayHook(lpparam);
                }
            });
        }
    }

    //hook方法执行
    private void performAlipayHook(XC_LoadPackage.LoadPackageParam lpparam) {
        //hook Application类的attach方法
        try {
            // Hook验证码关闭功能（需要在应用初始化之前就Hook配置写入）
            try {
                CaptchaHook.INSTANCE.setupHook(AppContext.getClassLoader());
                //Log.runtime(TAG, "验证码Hook系统已初始化");
            } catch (Throwable t) {
                Log.runtime(TAG, "验证码Hook初始化失败");
                Log.printStackTrace(TAG, t);
            }
            // 在Hook Application.attach 之前，先 deoptimize LoadedApk.makeApplicationInner
            try {
                Class<?> loadedApkClass = AppContext.getClassLoader().loadClass("android.app.LoadedApk");
                deoptimizeMethod(loadedApkClass);
            } catch (Throwable t) {
                Log.runtime(TAG, "deoptimize makeApplicationInner err:");
                Log.printStackTrace(TAG, t);
            }
            // 使用Xposed框架hook Application类的attach方法
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // 获取attach方法的第一个参数，即Context对象
                            Context context = (Context) param.args[0];
                            AppContext.setContext(context);
                            try {
                                // 通过Context对象获取支付宝应用的版本信息
                                AlipayVersion version = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                                AppContext.setAlipayVersion(version);
                            } catch (Exception e) {
                                Log.runtime(TAG, "获取目标应用版本信息失败");
                                Log.printStackTrace(e);
                            }
                            // 处理主题操作
                            try {
                                fansirsqi.xposed.sesame.hook.theme.ThemeManager.INSTANCE.handleThemeOperations();
                                // 如果启用了主题模块，启动监控
                                if (fansirsqi.xposed.sesame.model.BaseModel.getEnableMonitorSkinModule().getValue()) {
                                    fansirsqi.xposed.sesame.hook.theme.ThemeManager.INSTANCE.startOperationMonitor();
                                }
                            } catch (Throwable t) {
                                Log.runtime(TAG, "主题操作处理异常");
                                Log.printStackTrace(TAG, t);
                            }

                            super.afterHookedMethod(param);
                        }
                    });
        } catch (Throwable t) {
            Log.runtime(TAG, "hook attach err");
            Log.printStackTrace(TAG, t);
        }
        //hook "com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate" 类的matchVersion方法
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate", AppContext.getClassLoader(), "matchVersion",
                    AppContext.getClassLoader().loadClass(General.H5PAGE_NAME), Map.class, String.class,
                    XC_MethodReplacement.returnConstant(false));
            Log.runtime(TAG, "hook matchVersion successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "hook matchVersion err");
            Log.printStackTrace(TAG, t);
        }

        // 初始化皮肤模块 hooks
        try {
            SkinHook.setupHooks(AppContext.getClassLoader());
        } catch (Throwable t) {
            Log.runtime(TAG, "皮肤模块初始化异常:"+t);
            Log.printStackTrace(TAG, t);
        }

        // 初始化主题Hook模块（动态版本）
        try {
            ThemeHookV2.setupHooks(AppContext.getClassLoader());
        } catch (Throwable t) {
            Log.runtime(TAG, "主题Hook模块初始化异常:"+t);
            Log.printStackTrace(TAG, t);
        }

        // Hook CDPB 服务以防止 NoClassDefFoundError 导致闪退
        try {
            Class<?> cdpbServiceClass = XposedHelpers.findClassIfExists(
                "com.alipay.cdpb.api.CDPBService",
                AppContext.getClassLoader()
            );

            if (cdpbServiceClass != null) {
                Method is3PTSpacesMethod = null;
                try {
                    is3PTSpacesMethod = XposedHelpers.findMethodExact(
                        cdpbServiceClass,
                        "is3PTSpaces"
                    );
                } catch (Throwable ignored) {
                    // 方法不存在，静默跳过
                }

                if (is3PTSpacesMethod != null) {
                    XposedBridge.hookMethod(is3PTSpacesMethod, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            Log.runtime(TAG, "hook CDPB 服务异常"+t);
            Log.printStackTrace(TAG, t);
        }

        //hook "com.alipay.mobile.quinox.LauncherActivity" 类的onResume方法
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", AppContext.getClassLoader(), "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Log.runtime(TAG, "Activity onResume");
                            final Activity activity = (Activity) param.thisObject;

                            // 使用 Handler.post 延迟执行，避免在 onResume 中触发 Fragment 事务
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String targetUid = AppContext.getUserId();
                                        if (targetUid == null) {
                                            Log.record("用户未登录");
                                            Toast.show("用户未登录");
                                            return;
                                        }
                                        if (!LifecycleManager.isInit()) {
                                            if (LifecycleManager.initHandler(true)) {
                                                // Init successful
                                            }
                                            return;
                                        }
                                        String currentUid = fansirsqi.xposed.sesame.util.maps.UserMap.getCurrentUid();
                                        if (!targetUid.equals(currentUid)) {
                                            if (currentUid != null) {
                                                LifecycleManager.initHandler(true);
                                                Log.record("用户已切换");
                                                Toast.show("用户已切换");
                                                return;
                                            }
                                            fansirsqi.xposed.sesame.util.maps.UserMap.initUser(targetUid);
                                        }
                                        if (LifecycleManager.isOffline()) {
                                            LifecycleManager.setOffline(false);
                                            TaskScheduler.executeTask();
                                            // 延迟 finish() 调用，避免 Fragment 事务递归问题
                                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        activity.finish();
                                                        Log.runtime(TAG, "Activity reLogin");
                                                    } catch (Throwable t) {
                                                        Log.printStackTrace(TAG, t);
                                                    }
                                                }
                                            }, 300);
                                        }
                                    } catch (Throwable t) {
                                        Log.runtime(TAG, "onResume 延迟执行异常");
                                        Log.printStackTrace(TAG, t);
                                    }
                                }
                            });
                        }
                    });
            Log.runtime(TAG, "hook login successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "hook login err");
            Log.printStackTrace(TAG, t);
        }
        //hook "android.app.Service" 类的onCreate方法
        try {
            XposedHelpers.findAndHookMethod("android.app.Service", AppContext.getClassLoader(), "onCreate",
                    new XC_MethodHook() {
                        @SuppressLint("WakelockTimeout")
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Service appService = (Service) param.thisObject;
                            if (!General.CURRENT_USING_SERVICE.equals(appService.getClass().getCanonicalName())) {
                                return;
                            }
                            Log.runtime(TAG, "Service onCreate");
                            AppContext.setContext(appService.getApplicationContext());
                            AppContext.setService(appService);
                            AppContext.setMainHandler(new Handler(Looper.getMainLooper()));

                            // 在 Service onCreate 时初始化 Helper（此时应用已完全启动）
                            if (AppContext.getClassLoader() != null) {
                                SecurityBodyHelper.INSTANCE.init(AppContext.getClassLoader());
                                LocationHelper.INSTANCE.init(AppContext.getClassLoader());
//                                // 异步获取位置信息
//                                LocationHelper.INSTANCE.requestLocation(locationJson -> {
//                                    Log.debug(TAG, "📍 获取到位置信息: " + locationJson);
//                                });
                                // 协程调度器
                                SmartSchedulerManager.INSTANCE.initialize(appService.getApplicationContext());
                                // 获取小程序标记
                                AlipayMiniMarkHelper.INSTANCE.init(AppContext.getClassLoader());
                                // 位置
                                LocationHelper.INSTANCE.init(AppContext.getClassLoader());
                                // 授权码
                                AuthCodeHelper.INSTANCE.init(AppContext.getClassLoader());
                                AuthCodeHelper.INSTANCE.getAuthCode("2021005114632037");
                            }

                            // 注册广播接收器，使用回调接口
                            SesameReceiver.register(appService, new SesameReceiver.BroadcastCallback() {
                                @Override
                                public void onInitHandler(boolean force) {
                                    LifecycleManager.initHandler(force);
                                }

                                @Override
                                public void onReLogin() {
                                    LifecycleManager.reLogin();
                                }
                            });

                            if (LifecycleManager.initHandler(true)) {
                                // Init successful
                            }
                        }
                    });
            Log.runtime(TAG, "hook service onCreate successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "hook service onCreate err");
            Log.printStackTrace(TAG, t);
        }
        //hook "android.app.Service" 类的onDestroy方法
        try {
            XposedHelpers.findAndHookMethod("android.app.Service", AppContext.getClassLoader(), "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Service service = (Service) param.thisObject;
                            if (!General.CURRENT_USING_SERVICE.equals(service.getClass().getCanonicalName()))
                                return;
                            Log.record("目标应用前台服务被销毁");
                            Toast.show("目标应用前台服务被销毁");
                            LifecycleManager.destroyHandler(true);

                            // 明确设置状态为禁用
                            try {
                                Class<?> viewAppInfoClass = param.args[0].getClass().getClassLoader().loadClass("fansirsqi.xposed.sesame.data.ViewAppInfo");
                                XposedHelpers.callStaticMethod(viewAppInfoClass, "setRunType", RunType.DISABLE.getCode());
                            } catch (ClassNotFoundException e) {
                                try {
                                    ViewAppInfo.setRunType(RunType.DISABLE);
                                } catch (Exception ex) {
                                    Log.printStackTrace(ex);
                                }
                            } catch (Exception e) {
                                Log.printStackTrace(e);
                            }

                            restartByBroadcast();
                        }
                    });
        } catch (Throwable t) {
            Log.runtime(TAG, "hook service onDestroy err");
            Log.printStackTrace(TAG, t);
        }
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", AppContext.getClassLoader(), "isInBackground",
                    XC_MethodReplacement.returnConstant(false));
        } catch (Throwable t) {
            Log.runtime(TAG, "hook FgBgMonitorImpl method 1 err");
            Log.printStackTrace(TAG, t);
        }
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", AppContext.getClassLoader(), "isInBackground",
                    boolean.class, XC_MethodReplacement.returnConstant(false));
        } catch (Throwable t) {
            Log.runtime(TAG, "hook FgBgMonitorImpl method 2 err");
            Log.printStackTrace(TAG, t);
        }
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", AppContext.getClassLoader(), "isInBackgroundV2",
                    XC_MethodReplacement.returnConstant(false));
        } catch (Throwable t) {
            Log.runtime(TAG, "hook FgBgMonitorImpl method 3 err");
            Log.printStackTrace(TAG, t);
        }
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", AppContext.getClassLoader(), "isAtFrontDesk",
                    AppContext.getClassLoader().loadClass("android.content.Context"), XC_MethodReplacement.returnConstant(true));
            Log.runtime(TAG, "hook MiscUtils successfully");
        } catch (Throwable t) {
            Log.runtime(TAG, "hook MiscUtils err");
            Log.printStackTrace(TAG, t);
        }
        hooked = true;
        Log.runtime(TAG, "load success: " + lpparam.packageName);
    }
}
