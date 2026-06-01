package fansirsqi.xposed.sesame.hook.resource

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.os.Build
import androidx.core.app.NotificationCompat
import fansirsqi.xposed.sesame.hook.context.AppContext
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 前台保活助手
 *
 * 在等待秒杀兑换期间，将宿主 Service 提升为前台服务（带通知），
 * 防止系统因内存回收杀掉进程，导致 AlarmManager 回调丢失、CountDownLatch 失效。
 *
 * 使用方式：
 * - 进入等待阶段时调用 [startForeground]
 * - 兑换完成 / 超时 / 异常退出时调用 [stopForeground]
 *
 * 注意：通知 ID 和 Channel 与 Notify.kt 中的主通知独立，互不干扰。
 */
object ForegroundHelper {
    private const val TAG = "ForegroundHelper"

    /** 独立的通知 ID，避免与 Notify.kt 的 NOTIFICATION_ID=99 冲突 */
    private const val KEEP_ALIVE_NOTIFICATION_ID = 97

    /** 独立的通知渠道 */
    private const val CHANNEL_ID = "fansirsqi.xposed.sesame.FLASH_SALE_KEEP_ALIVE"
    private const val CHANNEL_NAME = "⏰ 秒杀等待保活"

    private val isActive = AtomicBoolean(false)

    /**
     * 将宿主 Service 提升为前台服务。
     *
     * @param taskName 任务名称，用于通知标题（如 "青春特权EX🍰"）
     * @param targetTime 兑换目标时间戳，用于通知内容展示
     */
    @SuppressLint("ForegroundServiceType")
    @JvmStatic
    fun startForeground(taskName: String, targetTime: Long) {
        if (isActive.get()) {
            Log.runtime(TAG, "前台服务已在运行，跳过重复启动")
            return
        }
        try {
            val service = AppContext.getService()
            if (service == null) {
                Log.error(TAG, "无法获取 Service 上下文，放弃前台保活")
                return
            }
            val context = service.applicationContext ?: service

            // 确保通知渠道已创建（Android 8.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        enableLights(false)
                        enableVibration(false)
                        setShowBadge(false)
                        description = "秒杀任务等待期间保持进程存活"
                    }
                    manager?.createNotificationChannel(channel)
                }
            }

            val timeStr = TimeUtil.getCommonDate(targetTime)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setContentTitle("⏰ $taskName 等待兑换中(请保持通知栏常驻)")
                .setContentText("目标时间 $timeStr，请保持应用运行")
                .setSubText("芝麻粒")
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = 0
                try {
                    val pm = context.packageManager
                    val componentName = android.content.ComponentName(context, service.javaClass)
                    val info = pm.getServiceInfo(componentName, 0)
                    type = info.foregroundServiceType
                } catch (e: Throwable) {
                    Log.error(TAG, "获取服务信息失败: ${e.message}")
                }

                if (type != 0) {
                    try {
                        service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification, type)
                    } catch (e: Throwable) {
                        Log.error(TAG, "使用获取的 type ($type) 启动前台服务失败: ${e.message}")
                        tryStartFgsFallback(service, notification)
                    }
                } else {
                    tryStartFgsFallback(service, notification)
                }
            } else {
                service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification)
            }
            isActive.set(true)
            Log.other("$TAG ✅ 前台保活已启动: $taskName → $timeStr")
        } catch (e: Exception) {
            Log.error(TAG, "启动前台保活失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 停止前台保活，移除通知。
     * 安全调用：即使未启动也不会抛异常。
     */
    @JvmStatic
    fun stopForeground() {
        if (!isActive.compareAndSet(true, false)) {
            return // 没有在运行，无需停止
        }
        try {
            val service = AppContext.getService() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                service.stopForeground(true)
            }
            Log.other("$TAG 🛑 前台保活已停止")
        } catch (e: Exception) {
            Log.error(TAG, "停止前台保活失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    private fun tryStartFgsFallback(service: Service, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification, 1) // DATA_SYNC
            } catch (e1: Throwable) {
                try {
                    service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification, 0x40000000) // SPECIAL_USE
                } catch (e2: Throwable) {
                    try {
                        service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification, 0x00000100) // SHORT_SERVICE
                    } catch (e3: Throwable) {
                        try {
                            service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification)
                        } catch (e4: Throwable) {
                            Log.error(TAG, "所有前台服务启动方式均失败: ${e4.message}")
                        }
                    }
                }
            }
        } else {
            service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification)
        }
    }

    /**
     * 检查前台保活是否正在运行
     */
    @JvmStatic
    fun isRunning(): Boolean = isActive.get()
}
