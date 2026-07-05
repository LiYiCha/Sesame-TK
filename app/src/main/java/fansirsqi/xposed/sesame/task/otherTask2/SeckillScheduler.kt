package fansirsqi.xposed.sesame.task.otherTask2

import android.content.Context
import android.content.Intent
import android.net.Uri
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.hook.resource.WakeLockManager
import fansirsqi.xposed.sesame.hook.scheduler.AlarmScheduler
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object SeckillScheduler {
    private const val TAG = "SeckillScheduler"
    private const val CONFIG_FILE_NAME = "seckill_tasks.json"

    @JvmStatic
    fun getSeckillTasksFile(): File {
        val file = File(Files.CONFIG_DIR, CONFIG_FILE_NAME)
        if (!file.exists()) {
            try { file.createNewFile() } catch (e: Exception) {}
        }
        return file
    }

    @JvmStatic
    fun syncTasks(context: Context) {
        try {
            val file = getSeckillTasksFile()
            if (!file.exists()) return
            val content = Files.readFromFile(file)
            if (content.isNullOrEmpty()) return

            val ja = JSONArray(content)
            val now = System.currentTimeMillis()

            for (i in 0 until ja.length()) {
                val jo = ja.optJSONObject(i) ?: continue
                val itemId = jo.optString("itemId")
                val skuId = jo.optString("skuId", "-1")
                val points = jo.optInt("points")
                val timeStr = jo.optString("seckillTime")
                val timeMillis = jo.optLong("timeMillis", 0)
                val type = jo.optString("type", "H5")
                val name = jo.optString("name", "未知商品")

                // Determine alarm lead time offset based on type
                val offset = if (type == "RPC") 10000L else 3000L
                val alarmTriggerTime = timeMillis - offset

                if (alarmTriggerTime > now) {
                    val taskId = "seckill_${itemId}_${timeMillis}"
                    
                    // Register alarm in system.
                    // Pass backup callback but also handle in SesameReceiver statically
                    AlarmScheduler.scheduleExactAlarm(taskId, alarmTriggerTime) {
                        executeSeckillById(context, taskId)
                    }
                    Log.runtime(TAG, "⏰ 已排期 [${type}] 秒杀任务:【$name】，将在 [${TimeUtil.getCommonDate(alarmTriggerTime)}] 唤醒")
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "同步秒杀任务失败: ${e.message}")
        }
    }

    @JvmStatic
    fun executeSeckillById(context: Context, taskId: String) {
        try {
            val file = getSeckillTasksFile()
            if (!file.exists()) return
            val content = Files.readFromFile(file)
            if (content.isNullOrEmpty()) return

            val ja = JSONArray(content)
            var targetIndex = -1
            var targetTask: JSONObject? = null

            for (i in 0 until ja.length()) {
                val jo = ja.optJSONObject(i) ?: continue
                val itemId = jo.optString("itemId")
                val timeMillis = jo.optLong("timeMillis", 0)
                val expectedTaskId = "seckill_${itemId}_${timeMillis}"
                if (expectedTaskId == taskId) {
                    targetIndex = i
                    targetTask = jo
                    break
                }
            }

            if (targetTask != null) {
                val itemId = targetTask.optString("itemId")
                val skuId = targetTask.optString("skuId", "-1")
                val points = targetTask.optInt("points")
                val type = targetTask.optString("type", "H5")
                val name = targetTask.optString("name", "未知商品")
                val timeMillis = targetTask.optLong("timeMillis")

                // Execute the seckill task
                executeSeckill(context, itemId, skuId, points, type, name, timeMillis)

                // Delete executed task from config
                val newList = mutableListOf<JSONObject>()
                for (j in 0 until ja.length()) {
                    if (j != targetIndex) {
                        val jo = ja.optJSONObject(j)
                        if (jo != null) newList.add(jo)
                    }
                }
                val newJa = JSONArray()
                newList.forEach { newJa.put(it) }
                Files.write2File(newJa.toString(), file)

                // Re-sync remaining tasks
                syncTasks(context)
            }
        } catch (e: Exception) {
            Log.error(TAG, "根据TaskId执行秒杀任务异常: ${e.message}")
        }
    }

    private fun executeSeckill(
        context: Context,
        itemId: String,
        skuId: String,
        points: Int,
        type: String,
        name: String,
        timeMillis: Long
    ) {
        Log.runtime(TAG, "🚀 秒杀唤醒成功！正在执行【$name】秒杀任务，类型: $type")
        if (type == "H5") {
            // Foreground H5 Mode: launch scheme directly
            val orderItemsJson = "[{\"itemId\":\"$itemId\",\"skuId\":\"$skuId\",\"number\":1}]"
            val encodedOrderItems = Uri.encode(orderItemsJson)
            val extJson = "{\"requestSourceInfo\":\"来源\"}"
            val encodedExtJson = Uri.encode(extJson)
            val tmallUrl = "https://pages.tmall.com/wow/wt/act/lm-pages?env=&extJson=$encodedExtJson&orderItems=$encodedOrderItems&verifyPoint=$points&wh_page=buy"
            val finalUrl = "https://pages.tmall.com/wow/z/wt/act/alipay-login?goToUrl=${Uri.encode(tmallUrl)}"
            val alipaySchemeUrl = "alipays://platformapi/startapp?appId=20000067&url=${Uri.encode(finalUrl)}"

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alipaySchemeUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.runtime(TAG, "✅ 前台 H5 秒杀: 已成功唤起支付宝至商品抢兑结算页！")
            } catch (e: Exception) {
                Log.error(TAG, "前台 H5 秒杀: 唤起支付宝失败: ${e.message}")
            }
        } else {
            // Background RPC Mode: acquire WakeLock, loop precisely, and fire concurrently
            GlobalThreadPools.execute {
                try {
                    WakeLockManager.acquire(context, "Seckill_RPC_$itemId")
                    
                    // Fire 50ms early to compensate for network roundtrip delay
                    val fireTime = timeMillis - 50L
                    Log.runtime(TAG, "📡 后台 RPC 秒杀已保活唤醒，发包校准目标时间: ${TimeUtil.getCommonDate(fireTime)}")

                    // High precision polling wait
                    while (System.currentTimeMillis() < fireTime) {
                        val remain = fireTime - System.currentTimeMillis()
                        when {
                            remain > 1000L -> Thread.sleep(100)
                            remain > 100L -> Thread.sleep(10)
                            remain > 10L -> Thread.sleep(1)
                        }
                    }

                    Log.runtime(TAG, "🎯 射击时间已到！并发发送 5 次原生兑换 RPC 请求...")
                    
                    val params = "[{\"bizType\":\"MEMBER\",\"sourceId\":\"$itemId\",\"sourcePassMap\":{\"innerSource\":\"兑换\",\"source\":\"\",\"unid\":\"${UUID.randomUUID()}\"},\"sourceType\":\"ALIYUN\"}]"
                    
                    for (i in 1..5) {
                        GlobalThreadPools.execute {
                            try {
                                val res = RequestManager.requestString(
                                    "com.alipay.alipaymember.biz.rpc.component.h5.commonlimit.commonLimit",
                                    params
                                )
                                Log.runtime(TAG, "⚡ 并发 RPC #$i 返回结果: $res")
                            } catch (err: Exception) {
                                Log.error(TAG, "⚡ 并发 RPC #$i 发包失败: ${err.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.error(TAG, "后台 RPC 秒杀发包执行异常: ${e.message}")
                } finally {
                    WakeLockManager.release()
                }
            }
        }
    }
}
