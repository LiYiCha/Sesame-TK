package fansirsqi.xposed.sesame.util;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import fansirsqi.xposed.sesame.R;
import fansirsqi.xposed.sesame.model.BaseModel;
public class ToastUtil {
    private static Context appContext;
    /**
     * 初始化全局 Context。建议在 Application 类中调用。
     *
     * @param context 应用上下文
     */
    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }
    /**
     * 获取当前环境的 Context
     *
     * @return Context
     */
    private static Context getContext() {
        if (appContext == null) {
            try {
                Context ctx = fansirsqi.xposed.sesame.hook.context.AppContext.getAppContext();
                if (ctx != null) {
                    appContext = ctx.getApplicationContext();
                }
            } catch (Throwable ignored) {}
        }
        if (appContext == null) {
            try {
                Context ctx = fansirsqi.xposed.sesame.hook.ApplicationHook.getAppContext();
                if (ctx != null) {
                    appContext = ctx.getApplicationContext();
                }
            } catch (Throwable ignored) {}
        }
        return appContext;
    }
    /**
     * 显示自定义 Toast
     *
     * @param message 显示的消息
     */
    public static void showToast(String message) {
        Context ctx = getContext();
        if (ctx != null) {
            showToast(ctx, message);
        } else {
            Log.runtime("ToastUtil", "Context 未初始化，无法弹出 Toast: " + message);
        }
    }
    /**
     * 显示自定义 Toast
     *
     * @param context 上下文
     * @param message 显示的消息
     */
    @SuppressLint("InflateParams")
    @SuppressWarnings("deprecation")
    public static void showToast(Context context, String message) {
        if (context == null) {
            context = getContext();
        }
        if (context == null) {
            Log.runtime("ToastUtil", "Context 为空，无法弹出 Toast: " + message);
            return;
        }
        Log.runtime("try showToast: " + message);
        try {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.toast, null);
            TextView toastText = layout.findViewById(R.id.toast_text);
            toastText.setText(message);
            
            Toast toast = new Toast(context);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setGravity(toast.getGravity(), toast.getXOffset(), BaseModel.getToastOffsetY().getValue());
            toast.setView(layout);
            toast.show();
        } catch (Exception e) {
            Log.printStackTrace(e);
            // 回退到原生Toast
            try {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        }
    }
    /**
     * 创建自定义 Toast
     *
     * @param message  显示的消息
     * @param duration 显示时长
     * @return Toast 对象
     */
    public static Toast makeText(String message, int duration) {
        return makeText(getContext(), message, duration);
    }
    /**
     * 创建自定义 Toast
     *
     * @param context  上下文
     * @param message  显示的消息
     * @param duration 显示时长
     * @return Toast 对象
     */
    @SuppressWarnings("deprecation")
    public static Toast makeText(Context context, String message, int duration) {
        if (context == null) {
            context = getContext();
        }
        if (context == null) {
            Log.runtime("ToastUtil", "Context 为空，无法创建 Toast: " + message);
            return null;
        }
        try {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            @SuppressLint("InflateParams")
            View layout = inflater.inflate(R.layout.toast, null);
            TextView toastText = layout.findViewById(R.id.toast_text);
            toastText.setText(message);
            
            Toast toast = new Toast(context);
            toast.setDuration(duration);
            toast.setGravity(toast.getGravity(), toast.getXOffset(), BaseModel.getToastOffsetY().getValue());
            toast.setView(layout);
            return toast;
        } catch (Exception e) {
            Log.printStackTrace(e);
            // 回退到原生Toast
            return Toast.makeText(context, message, duration);
        }
    }
    public static void showToastWithDelay(Context context, String message, int delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> makeText(context, message, Toast.LENGTH_SHORT).show(), delayMillis);
    }
    public static void showToastWithDelay(String message, int delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> makeText(message, Toast.LENGTH_SHORT).show(), delayMillis);
    }
}
