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
import fansirsqi.xposed.sesame.data.Config;
import fansirsqi.xposed.sesame.data.General;
import fansirsqi.xposed.sesame.entity.AlipayVersion;
import fansirsqi.xposed.sesame.hook.context.AppContext;
import fansirsqi.xposed.sesame.hook.core.HookModuleManager;
import fansirsqi.xposed.sesame.hook.core.modules.AlipayCoreModule;
import fansirsqi.xposed.sesame.hook.core.modules.CaptchaModule;
import fansirsqi.xposed.sesame.hook.core.modules.SkinThemeModule;
import fansirsqi.xposed.sesame.hook.core.modules.MiscHookModule;
import fansirsqi.xposed.sesame.hook.core.modules.LifecycleModule;
import fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcVersion;
import fansirsqi.xposed.sesame.hook.scheduler.AlarmScheduler;
import fansirsqi.xposed.sesame.util.GlobalThreadPools;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.XpFrameworkDetector;

public class ApplicationHook implements IXposedHookLoadPackage {
    static final String TAG = ApplicationHook.class.getSimpleName();
    private static final String modelVersion = BuildConfig.VERSION_NAME;
    private static volatile boolean hooked = false;
    static final AtomicInteger reLoginCount = new AtomicInteger(0);

    //--------------------------------------------------------------
    // 公共API - 委托给新模块
    //--------------------------------------------------------------

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
        startHook(lpparam.packageName, lpparam.processName, lpparam.classLoader, lpparam);
    }

    /**
     * 现代入口（LibXposedRuntime）或其他未直接提供 lpparam 的入口调用。
     */
    public void startHook(String packageName, String processName, ClassLoader classLoader) {
        startHook(packageName, processName, classLoader, null);
    }

    /**
     * 两个入口（传统 xposed_init 与 libxposed 现代管线）共用的启动路径。
     * 优先使用外部传入的 authentic lpparam；当且仅当 originParam 为 null 时回退尝试合成。
     */
    public void startHook(String packageName, String processName, ClassLoader classLoader, XC_LoadPackage.LoadPackageParam originParam) {
        registerModules();
        if ("fansirsqi.xposed.sesame".equals(packageName)) {
            try {
                try {
                    String fw = XpFrameworkDetector.detectTraditional(classLoader, null);
                    fansirsqi.xposed.sesame.data.ViewAppInfo.xpFrameworkVersion = fw;
                } catch (Throwable ignored) {}

                // 首先尝试使用目标应用的ClassLoader加载
                Class<?> viewAppInfoClass = classLoader.loadClass("fansirsqi.xposed.sesame.data.ViewAppInfo");
                // 注意：ViewAppInfo.kt 中的方法是 setRunType，而不是 setRunTypeByCode
                XposedHelpers.callStaticMethod(viewAppInfoClass, "setRunType", fansirsqi.xposed.sesame.data.RunType.ACTIVE);
                try {
                    String fw = XpFrameworkDetector.detectTraditional(classLoader, null);
                    XposedHelpers.setStaticObjectField(viewAppInfoClass, "xpFrameworkVersion", fw);
                } catch (Throwable ignored) {}
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
        } else if (General.PACKAGE_NAME.equals(packageName) && General.PACKAGE_NAME.equals(processName)) {
            if (hooked) return;
            AppContext.setClassLoader(classLoader);

            // 最早期安装宿主崩溃记录器，覆盖启动窗口（早于任何模块 hook 与配置加载）
            CrashRecorder.install();

            // 识别并输出 XP 框架环境（绝不抛出任何未捕获异常）
            try {
                if (originParam != null) {
                    XpFrameworkDetector.detectTraditional(classLoader, null);
                }
                Log.runtime(TAG, XpFrameworkDetector.getLogDescription() + " | 宿主: " + packageName);
            } catch (Throwable ignored) {}

            // MTOP 实例捕获 hook：独立于抓包开关无条件安装，供游乐记等主动发 MTOP 的任务复用宿主实例
            try {
                MtopSender.installCaptureHook(classLoader);
            } catch (Throwable t) {
                Log.runtime(TAG, "MtopSender installCaptureHook 异常: " + t.getMessage());
                Log.printStackTrace(TAG, t);
            }

            // 1. 立即分发模块 LoadPackage 与 Hook Application.attach，确保 AppContext 与生命周期在启动时 100% 挂载成功
            XC_LoadPackage.LoadPackageParam lpparam = originParam != null ? originParam : newLoadPackageParam(packageName, processName, classLoader);
            try {
                if (lpparam != null) {
                    HookModuleManager.INSTANCE.dispatchHandleLoadPackage(lpparam);
                }
                performAlipayHook(lpparam, packageName, processName, classLoader);
            } catch (Throwable t) {
                Log.runtime(TAG, "dispatchHandleLoadPackage / performAlipayHook 失败: " + t.getMessage());
                Log.printStackTrace(TAG, t);
            }

            // 移除 ClassChecker.waitForClasses 轮询死循环，避免冷启动类加载锁争用与超时自杀
        }
    }

    private XC_LoadPackage.LoadPackageParam newLoadPackageParam(String packageName, String processName, ClassLoader classLoader) {
        XC_LoadPackage.LoadPackageParam param = null;

        // 尝试 1: 扫描并调用已声明的构造函数（适配不同 Xposed 运行时与 Stub）
        try {
            java.lang.reflect.Constructor<?>[] ctors = XC_LoadPackage.LoadPackageParam.class.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> ctor : ctors) {
                try {
                    ctor.setAccessible(true);
                    Class<?>[] paramTypes = ctor.getParameterTypes();
                    Object[] args = new Object[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (paramTypes[i].isPrimitive()) {
                            if (paramTypes[i] == boolean.class) args[i] = false;
                            else if (paramTypes[i] == int.class) args[i] = 0;
                            else if (paramTypes[i] == long.class) args[i] = 0L;
                            else args[i] = (byte) 0;
                        } else {
                            args[i] = null;
                        }
                    }
                    param = (XC_LoadPackage.LoadPackageParam) ctor.newInstance(args);
                    if (param != null) break;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        // 尝试 2: 构造函数皆不可用时，通过反射 Unsafe.allocateInstance 绕过构造器分配（Android ART 原生支持）
        if (param == null) {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                java.lang.reflect.Field field = unsafeClass.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                Object unsafe = field.get(null);
                if (unsafe != null) {
                    java.lang.reflect.Method allocateMethod = unsafeClass.getMethod("allocateInstance", Class.class);
                    param = (XC_LoadPackage.LoadPackageParam) allocateMethod.invoke(unsafe, XC_LoadPackage.LoadPackageParam.class);
                }
            } catch (Throwable ignored) {
            }
        }

        if (param != null) {
            param.packageName = packageName;
            param.processName = processName;
            param.classLoader = classLoader;
            return param;
        }

        Log.runtime(TAG, "警告: 无法合成 LoadPackageParam 实例");
        return null;
    }

    private void performAlipayHook(XC_LoadPackage.LoadPackageParam lpparam, String packageName, String processName, ClassLoader classLoader) {
        ClassLoader cl = (lpparam != null && lpparam.classLoader != null) ? lpparam.classLoader : classLoader;
        String pkg = (lpparam != null && lpparam.packageName != null) ? lpparam.packageName : packageName;
        String proc = (lpparam != null && lpparam.processName != null) ? lpparam.processName : processName;

        try {
            // Hook Application.attach
            XposedHelpers.findAndHookMethod("android.app.Application", cl, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Context context = (Context) param.args[0];
                            HookModuleManager.INSTANCE.dispatchPreAppAttach(context, cl);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context context = (Context) param.args[0];
                            // 框架可能在 install 后设置默认 handler 顶掉记录器，此处补检重装
                            CrashRecorder.ensureInstalled();
                            AppContext.setContext(context);
                            AppContext.setMainHandler(new Handler(Looper.getMainLooper()));

                            // context 现已可用，二次增强探测 NPatch/LSPatch PackageManager metadata（安全防御）
                            try {
                                XpFrameworkDetector.detectTraditional(cl, context);
                            } catch (Throwable ignored) {}

                            // 跨进程 IPC (getPackageInfo) 延后到后台线程，确保宿主 attach 0 毫秒放行不卡死
                            GlobalThreadPools.INSTANCE.execute(() -> {
                                try {
                                    AlipayVersion version = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                                    AppContext.setAlipayVersion(version);
                                } catch (Exception e) {
                                    Log.runtime(TAG, "获取版本失败: " + e.getMessage());
                                }
                            });

                            HookModuleManager.INSTANCE.dispatchPostAppAttach(context, cl);

                            // 子进程处理：根据各自开关开启网络及RPC调试抓包
                            // attach 在主线程，Config.load 有磁盘 IO，连同 hook 安装一起移到后台执行
                            if (!General.PACKAGE_NAME.equals(proc)) {
                                GlobalThreadPools.INSTANCE.execute(() -> {
                                    try {
                                        if (!Config.isLoaded()) {
                                            Config.load("");
                                        }
                                    } catch (Throwable t) {
                                        // 忽略
                                    }

                                    try {
                                        LifecycleManager.initCaptureSwitches();
                                        Log.runtime(TAG, "Subprocess initCaptureSwitches success");
                                    } catch (Throwable t) {
                                        Log.runtime(TAG, "Subprocess initCaptureSwitches err: " + t.getMessage());
                                    }
                                });
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.runtime(TAG, "hook attach err");
            Log.printStackTrace(TAG, t);
        }

        hooked = true;
        Log.runtime(TAG, "load success: " + pkg);
    }
}
