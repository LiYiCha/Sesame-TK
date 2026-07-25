package fansirsqi.xposed.sesame.hook.broadcast;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Objects;

import fansirsqi.xposed.sesame.hook.ExtendHandle;
import fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager;
import fansirsqi.xposed.sesame.hook.rpc.debug.DebugRpc;
import fansirsqi.xposed.sesame.hook.scheduler.AlarmScheduler;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.StringUtil;
import fansirsqi.xposed.sesame.util.maps.UserMap;

/**
 * 芝麻粒广播接收器
 * 负责处理应用内的广播消息
 */
public class SesameReceiver extends BroadcastReceiver {
    private static final String TAG = SesameReceiver.class.getSimpleName();

    // 回调接口
    private static BroadcastCallback callback;

    /**
     * 广播回调接口
     */
    public interface BroadcastCallback {
        /**
         * 初始化处理器
         * @param force 是否强制初始化
         */
        void onInitHandler(boolean force);

        /**
         * 重新登录
         */
        void onReLogin();
    }

    /**
     * 设置广播回调
     */
    public static void setCallback(BroadcastCallback cb) {
        callback = cb;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.runtime("sesame 查看广播:" + action + " intent:" + intent);
        if (action != null) {
            switch (action) {
                case "com.eg.android.AlipayGphone.sesame.restart":
                    if (callback != null) {
                        callback.onInitHandler(true);
                    }
                    break;
                case "com.eg.android.AlipayGphone.sesame.execute":
                    if (callback != null) {
                        callback.onInitHandler(false);
                    }
                    break;
                case "com.eg.android.AlipayGphone.sesame.reLogin":
                    if (callback != null) {
                        callback.onReLogin();
                    }
                    break;
                case "com.eg.android.AlipayGphone.sesame.status":
                    try {
                        context.sendBroadcast(new Intent("fansirsqi.xposed.sesame.status"));
                    } catch (Throwable th) {
                        Log.runtime(TAG, "sesame sendBroadcast status err:");
                        Log.printStackTrace(TAG, th);
                    }
                    break;
                case "com.eg.android.AlipayGphone.sesame.rpctest":
                    try {
                        String method = intent.getStringExtra("method");
                        String data = intent.getStringExtra("data");
                        String type = intent.getStringExtra("type");
                        DebugRpc rpcInstance = new DebugRpc(context);
                        rpcInstance.start(method, data, type);
                    } catch (Throwable th) {
                        Log.runtime(TAG, "sesame 测试RPC请求失败:");
                        Log.printStackTrace(TAG, th);
                    }
                    break;
                case "com.eg.android.AlipayGphone.sesame.exportTheme":
                    try {
                        String userId = intent.getStringExtra("userId");
                        kotlin.Pair<Boolean, String> res = fansirsqi.xposed.sesame.hook.theme.ThemeManager.INSTANCE.exportThemesDirectly(userId);
                        Log.other("ThemeManager", "⚡ 收到 UI 跨进程 IPC 广播，主题导出结果: " + res.getSecond());
                    } catch (Throwable th) {
                        Log.error("ThemeManager", "❌ 跨进程 IPC 导出主题异常: " + th.getMessage());
                    }
                    break;
                case "com.eg.android.AlipayGphone.sesame.rerun":
                    // 处理重新运行或继续运行逻辑
                    String actionType = intent.getStringExtra("actionType");
                    if (actionType == null) {
                        actionType = "continue"; // 默认为继续运行
                    }
                    ExtendHandle.handleReRun(actionType);
                    break;
                case "com.eg.android.AlipayGphone.sesame.checkStatus":
                    // 处理状态检测逻辑
                    ExtendHandle.handleCheckStatus(context);
                    break;
                case "com.eg.android.AlipayGphone.sesame.fetchMemberGoodsList":
                    String deliveryId = intent.getStringExtra("deliveryId");
                    if (deliveryId == null || deliveryId.isEmpty()) {
                        deliveryId = "94000SR2025120515775004";
                    }
                    int pageNum = intent.getIntExtra("pageNum", 1);
                    ExtendHandle.handleFetchMemberGoodsList(context, deliveryId, pageNum);
                    break;
                case "com.eg.android.AlipayGphone.sesame.queryBenefitDetail":
                    try {
                        String benefitId = intent.getStringExtra("benefitId");
                        if (benefitId != null && !benefitId.isEmpty()) {
                            ExtendHandle.handleQueryBenefitDetail(context, benefitId);
                        }
                    } catch (Throwable th) {
                        Log.error(TAG, "查询规格详情异常: " + th.getMessage());
                    }
                    break;
                case "com.eg.android.AlipayGphone.sesame.syncSeckillTasks":
                    fansirsqi.xposed.sesame.task.otherTask2.SeckillScheduler.syncTasks(context);
                    break;
                case "com.eg.android.AlipayGphone.sesame.exactAlarm":
                    // 处理精确唤醒任务
                    String taskId = intent.getStringExtra("taskId");
                    if (taskId != null) {
                        if (taskId.startsWith("seckill_")) {
                            fansirsqi.xposed.sesame.task.otherTask2.SeckillScheduler.INSTANCE.executeSeckillById(context, taskId);
                        } else {
                            AlarmScheduler.handleExactAlarmTrigger(taskId);
                            if (taskId.startsWith("WAKEUP_")) {
                                Log.runtime(TAG, "⏰ 检测到内置兑换任务精确唤醒，以防内存丢失启动全局任务...");
                                if (callback != null) {
                                    callback.onInitHandler(false);
                                }
                            }
                        }
                    }
                    break;
                default:
                    Log.runtime(TAG, "未知广播: " + action);
                    break;
            }
        }
    }

    /**
     * 注册广播接收器以监听支付宝相关动作
     *
     * @param context 应用程序上下文
     * @param callback 广播回调接口
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public static void register(Context context, BroadcastCallback callback) {
        try {
            setCallback(callback);
            IntentFilter intentFilter = getIntentFilter();
            SesameReceiver receiver = new SesameReceiver();

            // 根据Android SDK版本注册广播接收器
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 在Android 13及以上版本，注册广播接收器并指定其可以被其他应用发送的广播触发
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                // 在Android 13以下版本，注册广播接收器
                context.registerReceiver(receiver, intentFilter);
            }
            Log.runtime("注册广播成功");
            Log.runtime(TAG, "hook registerBroadcastReceiver successfully");
        } catch (Throwable th) {
            Log.error("注册广播失败");
            Log.runtime(TAG, "hook registerBroadcastReceiver err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 获取 IntentFilter
     */
    @NonNull
    private static IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.restart"); // 重启支付宝服务的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.execute"); // 执行特定命令的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.reLogin"); // 重新登录支付宝的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.status"); // 查询支付宝状态的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.rpctest"); // 调试RPC的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.rpcresponse"); // 调试RPC的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.rerun"); // 重新执行任务
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.checkStatus"); // 状态检测
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.exactAlarm"); // 精确唤醒任务
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.fetchMemberGoodsList"); // 同步会员商品列表
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.syncSeckillTasks"); // 同步定时秒杀任务
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.queryBenefitDetail"); // 查询权益详情以捕获规格
        return intentFilter;
    }
}
