package fansirsqi.xposed.sesame.hook.context;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.os.Handler;

import de.robv.android.xposed.XposedHelpers;
import fansirsqi.xposed.sesame.entity.AlipayVersion;
import fansirsqi.xposed.sesame.util.Log;
import lombok.Getter;

/**
 * 应用上下文管理器
 * 负责管理应用的核心上下文对象和提供全局访问点
 */
public class AppContext {
    private static final String TAG = AppContext.class.getSimpleName();

    private static ClassLoader classLoader = null;

    @Getter
    private static Object microApplicationContextObject = null;

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

    /**
     * 获取 MicroApplicationContext 对象
     */
    public static Object getMicroApplicationContext() {
        if (microApplicationContextObject == null) {
            try {
                Class<?> alipayApplicationClass = XposedHelpers.findClass(
                        "com.alipay.mobile.framework.AlipayApplication", classLoader
                );
                Object alipayApplicationInstance = XposedHelpers.callStaticMethod(
                        alipayApplicationClass, "getInstance"
                );
                if (alipayApplicationInstance == null) {
                    return null;
                }
                microApplicationContextObject = XposedHelpers.callMethod(
                        alipayApplicationInstance, "getMicroApplicationContext"
                );
            } catch (Throwable t) {
                Log.printStackTrace(t);
            }
        }
        return microApplicationContextObject;
    }

    /**
     * 根据服务接口名称获取服务对象
     */
    public static Object getServiceObject(String service) {
        try {
            return XposedHelpers.callMethod(getMicroApplicationContext(), "findServiceByInterface", service);
        } catch (Throwable th) {
            Log.runtime(TAG, "getServiceObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    /**
     * 获取用户对象
     */
    public static Object getUserObject() {
        try {
            return XposedHelpers.callMethod(
                    getServiceObject(XposedHelpers.findClass("com.alipay.mobile.personalbase.service.SocialSdkContactService", classLoader).getName()),
                    "getMyAccountInfoModelByLocal");
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    /**
     * 获取用户ID
     */
    public static String getUserId() {
        try {
            Object userObject = getUserObject();
            if (userObject != null) {
                return (String) XposedHelpers.getObjectField(userObject, "userId");
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserId err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    /**
     * 重置 MicroApplicationContext（用于重新初始化）
     */
    public static void resetMicroApplicationContext() {
        microApplicationContextObject = null;
    }
}
