package fansirsqi.xposed.sesame.hook;


import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fansirsqi.xposed.sesame.BuildConfig;
import fansirsqi.xposed.sesame.data.General;
import fansirsqi.xposed.sesame.entity.AlipayVersion;
import fansirsqi.xposed.sesame.hook.context.AppContext;
import fansirsqi.xposed.sesame.hook.core.HookModuleManager;
import fansirsqi.xposed.sesame.hook.core.modules.AlipayCoreModule;
import fansirsqi.xposed.sesame.hook.core.modules.CaptchaModule;
import fansirsqi.xposed.sesame.hook.core.modules.SkinThemeModule;
import fansirsqi.xposed.sesame.hook.core.modules.NetworkModule;
import fansirsqi.xposed.sesame.hook.core.modules.MiscHookModule;
import fansirsqi.xposed.sesame.hook.core.modules.LifecycleModule;
import fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcVersion;
import fansirsqi.xposed.sesame.hook.scheduler.AlarmScheduler;
import fansirsqi.xposed.sesame.util.Log;

public class ApplicationHook implements IXposedHookLoadPackage {
    static final String TAG = ApplicationHook.class.getSimpleName();
    private static final String modelVersion = BuildConfig.VERSION_NAME;
    private static volatile boolean hooked = false;
    static final AtomicInteger reLoginCount = new AtomicInteger(0);

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
            Log.printStackTrace(TAG, th);
        }
    }

    public static void restartByBroadcast() {
        try {
            AppContext.getContext().sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.restart"));
        } catch (Throwable th) {
            Log.printStackTrace(TAG, th);
        }
    }

    public static boolean isHooked() { return hooked; }
    public static String getModelVersion() { return modelVersion; }
    public static AtomicInteger getReLoginCount() { return reLoginCount; }
    public static boolean isInit() { return LifecycleManager.isInit(); }
    public static boolean isOffline() { return LifecycleManager.isOffline(); }
    public static RpcVersion getRpcVersion() { return LifecycleManager.getRpcVersion(); }

    private static boolean modulesRegistered = false;

    private void registerModules() {
        if (modulesRegistered) return;
        HookModuleManager.INSTANCE.registerModule(new AlipayCoreModule());
        HookModuleManager.INSTANCE.registerModule(new CaptchaModule());
        HookModuleManager.INSTANCE.registerModule(new SkinThemeModule());
        HookModuleManager.INSTANCE.registerModule(new NetworkModule());
        HookModuleManager.INSTANCE.registerModule(new MiscHookModule());
        HookModuleManager.INSTANCE.registerModule(new LifecycleModule());
        modulesRegistered = true;
    }

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
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        registerModules();
        if ("fansirsqi.xposed.sesame".equals(lpparam.packageName)) {
            try {
                // 首先尝试使用目标应用的ClassLoader加载
                Class<?> viewAppInfoClass = lpparam.classLoader.loadClass("fansirsqi.xposed.sesame.data.ViewAppInfo");
                // 注意：ViewAppInfo.kt 中的方法是 setRunType，而不是 setRunTypeByCode
                XposedHelpers.callStaticMethod(viewAppInfoClass, "setRunType", fansirsqi.xposed.sesame.data.RunType.ACTIVE);
            } catch (ClassNotFoundException e) {
                // 如果找不到类，直接使用当前模块的类
                try {
                    fansirsqi.xposed.sesame.data.ViewAppInfo.setRunType(fansirsqi.xposed.sesame.data.RunType.ACTIVE);
                } catch (Exception ex) {
                    Log.printStackTrace(ex);
                }
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        } else if (General.PACKAGE_NAME.equals(lpparam.packageName) && General.PACKAGE_NAME.equals(lpparam.processName)) {
            if (hooked) return;
            AppContext.setClassLoader(lpparam.classLoader);

            // 1. 立即分发模块 LoadPackage 与 Hook Application.attach，确保 AppContext 与生命周期在启动时 100% 挂载成功
            HookModuleManager.INSTANCE.dispatchHandleLoadPackage(lpparam);
            performAlipayHook(lpparam);

            String[] requiredClasses = {
                    "com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate",
                    "com.alipay.mobile.quinox.LauncherActivity",
                    "com.alipay.mobile.quinox.LauncherApplication",
                    "com.alipay.mobile.common.fgbg.FgBgMonitorImpl",
                    "com.alipay.mobile.common.transport.utils.MiscUtils"
            };

            ClassChecker.waitForClasses(lpparam.classLoader, requiredClasses, allClassesExist -> {
                if (allClassesExist) {
                    Log.runtime(TAG, "所有必需类已就绪");
                } else {
                    Log.runtime(TAG, "等待类加载超时或部分类未找到");
                }
            });
        }
    }

    private void performAlipayHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Application.attach
            XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Context context = (Context) param.args[0];
                            HookModuleManager.INSTANCE.dispatchPreAppAttach(context, lpparam.classLoader);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context context = (Context) param.args[0];
                            AppContext.setContext(context);
                            AppContext.setMainHandler(new Handler(Looper.getMainLooper()));
                            
                            try {
                                AlipayVersion version = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                                AppContext.setAlipayVersion(version);
                            } catch (Exception e) {
                                Log.runtime(TAG, "获取版本失败: " + e.getMessage());
                            }

                            HookModuleManager.INSTANCE.dispatchPostAppAttach(context, lpparam.classLoader);

                            // 子进程处理：根据各自开关开启网络及RPC调试抓包
                            if (!General.PACKAGE_NAME.equals(lpparam.processName)) {
                                try {
                                    if (!fansirsqi.xposed.sesame.data.Config.isLoaded()) {
                                        fansirsqi.xposed.sesame.data.Config.load("");
                                    }
                                } catch (Throwable t) {
                                    // 忽略
                                }

                                if (fansirsqi.xposed.sesame.model.BaseModel.getDebugMode().getValue()) {
                                    try {
                                        LifecycleManager.setupRpcDebugHooks();
                                        Log.runtime(TAG, "Subprocess setupRpcDebugHooks success");
                                    } catch (Throwable t) {
                                        Log.runtime(TAG, "Subprocess setupRpcDebugHooks err: " + t.getMessage());
                                    }
                                }

                                if (fansirsqi.xposed.sesame.model.BaseModel.enableHttpCapture.getValue()) {
                                    try {
                                        fansirsqi.xposed.sesame.hook.network.HttpCaptureHook.setup(lpparam.classLoader);
                                        fansirsqi.xposed.sesame.hook.network.NetworkHook.setupHooks(lpparam.classLoader);
                                        Log.runtime(TAG, "Subprocess HttpCaptureHook setup success");
                                    } catch (Throwable t) {
                                        Log.runtime(TAG, "Subprocess HttpCaptureHook setup err: " + t.getMessage());
                                    }
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.runtime(TAG, "hook attach err");
            Log.printStackTrace(TAG, t);
        }

        hooked = true;
        Log.runtime(TAG, "load success: " + lpparam.packageName);
    }
}
