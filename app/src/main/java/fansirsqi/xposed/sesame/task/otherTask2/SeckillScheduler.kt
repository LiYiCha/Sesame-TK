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
            try {
                file.parentFile?.mkdirs()
                file.createNewFile()
            } catch (e: Exception) {}
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
            val activeTasks = JSONArray()
            var hasExpired = false

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
                    activeTasks.put(jo)
                    val taskId = "seckill_${itemId}_${timeMillis}"
                    
                    // Register alarm in system
                    AlarmScheduler.scheduleExactAlarm(taskId, alarmTriggerTime) {
                        executeSeckillById(context, taskId)
                    }
                    Log.runtime(TAG, "⏰ 已排期 [${type}] 秒杀任务:【$name】，将在 [${TimeUtil.getCommonDate(alarmTriggerTime)}] 唤醒")
                } else {
                    hasExpired = true
                }
            }

            if (hasExpired) {
                Files.write2File(activeTasks.toString(), file)
                Log.runtime(TAG, "🧹 已自动清理过期秒杀任务")
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
                val benefitId = targetTask.optString("benefitId", "")
                val itemId = targetTask.optString("itemId")
                val skuId = targetTask.optString("skuId", "-1")
                val points = targetTask.optInt("points")
                val type = targetTask.optString("type", "H5")
                val name = targetTask.optString("name", "未知商品")
                val timeMillis = targetTask.optLong("timeMillis")
                val number = targetTask.optInt("number", 1)

                // Execute the seckill task
                executeSeckill(context, benefitId, itemId, skuId, points, type, name, timeMillis, number)

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
        benefitId: String,
        itemId: String,
        skuId: String,
        points: Int,
        type: String,
        name: String,
        timeMillis: Long,
        number: Int = 1
    ) {
        Log.runtime(TAG, "🚀 秒杀唤醒成功！正在执行【$name】秒杀任务，类型: $type")
        if (type == "H5") {
            // Foreground H5 Mode: launch scheme directly
            val orderItemsJson = "[{\"itemId\":\"$itemId\",\"skuId\":\"$skuId\",\"number\":$number}]"
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
                    while (true) {
                        val remain = fireTime - System.currentTimeMillis()
                        if (remain <= 0) break
                        if (remain > 1000) {
                            Thread.sleep(100)
                        } else if (remain > 100) {
                            Thread.sleep(10)
                        } else if (remain > 10) {
                            Thread.sleep(1)
                        } else {
                            // Spin-lock (busy wait) for the last 10 milliseconds
                        }
                    }

                    Log.runtime(TAG, "🎯 射击时间已到！并发发送 5 次原生兑换 RPC 请求...")
                    
                    for (i in 1..5) {
                        val requestUnid = UUID.randomUUID().toString()
                        val params = "[{\"bizType\":\"MEMBER\",\"sourceId\":\"$benefitId\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"$requestUnid\"},\"sourceType\":\"ALIYUN\"}]"
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
                    Log.error(TAG, "后台 RPC 秒杀异常: ${e.message}")
                } finally {
                    WakeLockManager.release()
                }
            }
        }
    }
}
