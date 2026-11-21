package fansirsqi.xposed.sesame.hook.context;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.os.Handler;

import fansirsqi.xposed.sesame.entity.AlipayVersion;
import lombok.Getter;

/**
 * 应用上下文管理器。
 *
 * 本类只持有纯 Android 对象（Context、Handler、ClassLoader、版本信息）与 hook 状态标记，
 * 不引用任何 de.robv.* 类型，也不做任何目标应用反射。模块设置页、Toast 等非注入进程可以安全加载它；
 * 需要 XposedHelpers 的支付宝反射方法放在 ApplicationHook。
 *
 * 这不是靠 try-catch 兜住 NoClassDefFoundError，而是通过消除依赖避免它发生。
 */
public class AppContext {
    private static final String TAG = AppContext.class.getSimpleName();

    private static ClassLoader classLoader = null;

    @Getter
    @SuppressLint("StaticFieldLeak")
    private static Context context = null;

    private static AlipayVersion alipayVersion = new AlipayVersion("");

    @SuppressLint("StaticFieldLeak")
    private static Service service;

    @Getter
    private static Handler mainHandler;

    /**
     * hook 是否已生效。
     * 该标记放在本类（纯 Android 依赖）而非 ApplicationHook，是为了让设置页、Toast、用户数据等
     * 非 hook 层代码无需加载 ApplicationHook（其实现了 de.robv.android.xposed.IXposedHookLoadPackage），
     * 否则在 NPatch 本地模式等缺少 Xposed 类的进程里会抛 NoClassDefFoundError 直接闪退。
     */
    private static volatile boolean hooked = false;

    /** hook 状态查询（安全，不触发任何 Xposed 类解析） */
    public static boolean isHooked() {
        return hooked;
    }

    /** 由 hook 入口在注入成功后置位 */
    public static void setHooked(boolean value) {
        hooked = value;
    }

    /**
     * 获取 ClassLoader（显式方法，确保 Kotlin 可以访问）
     */
    public static ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * 设置 ClassLoader
     */
    public static void setClassLoader(ClassLoader loader) {
        classLoader = loader;
    }

    /**
     * 设置应用上下文
     */
    public static void setContext(Context ctx) {
        context = ctx;
    }

    /**
     * 获取应用上下文（显式方法，确保 Kotlin 可以访问）
     */
    public static Context getContext() {
        return context;
    }

    /**
     * 设置支付宝版本
     */
    public static void setAlipayVersion(AlipayVersion version) {
        alipayVersion = version;
    }

    /**
     * 获取支付宝版本（显式方法，确保 Kotlin 可以访问）
     */
    public static AlipayVersion getAlipayVersion() {
        return alipayVersion;
    }

    /**
     * 设置 Service
     */
    public static void setService(Service svc) {
        service = svc;
    }

    /**
     * 获取 Service
     */
    public static Service getService() {
        return service;
    }

    /**
     * 设置主线程 Handler
     */
    public static void setMainHandler(Handler handler) {
        mainHandler = handler;
    }

    /**
     * 获取应用上下文（显式方法，确保 Kotlin 可以访问）
     */
    public static Context getAppContext() {
        return context;
    }
}
