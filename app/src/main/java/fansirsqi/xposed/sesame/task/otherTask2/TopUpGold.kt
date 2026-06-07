package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.internal.AlipayMiniMarkHelper
import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TopUpGold {
    private val TAG = "💰充值金任务"
    private val APP_ID = "2021004113642010"
    private val VERSION = "0.2.2605251828.32"
    private val REFERER = "https://$APP_ID.hybrid.alipay-eco.com/$APP_ID/$VERSION/index.html#pages/index/index"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private var cachedToken: String? = null
    }

    fun handle() {
        val hour = TimeUtil.getHourOfDay()
        if (hour < 7) return

        try {
            val userId = UserMap.currentUid
            if (userId.isNullOrEmpty()) return

            val token = getValidToken(userId)
            if (token.isNullOrEmpty()) {
                Log.runtime(TAG, "⚠️ 无法获取有效的 Token，放弃执行")
                return
            }

            // 1. 每日签到：先查询状态，未签到且功能可用时才执行
            if (!Status.hasFlagToday("topUpGold_signed_in")) {
                val signInData = querySignInStatus(token, userId)
                when {
                    signInData == null -> {
                        // 查询失败（可能已下架），设置 flag 跳过
                        Log.other(TAG, "查询签到无结果（可能已下架），跳过今日签到")
                        Status.setFlagToday("topUpGold_signed_in")
                    }
                    signInData.optBoolean("signinToday", false) -> {
                        // 今日已签到
                        Log.other(TAG, "查询到今日已签到")
                        Status.setFlagToday("topUpGold_signed_in")
                    }
                    else -> {
                        // 未签到，执行签到
                        executeSignIn(token, userId)
                        Status.setFlagToday("topUpGold_signed_in")
                    }
                }
                Thread.sleep(RandomUtil.nextGaussianLong(1500, 3000))
            }

            var completedCount = 0
            val maxTasks = 15
            var loopCount = 0

            while (loopCount < maxTasks) {
                loopCount++

                val listJson = queryTaskListRaw(token, userId)
                if (listJson == null || !listJson.optBoolean("success", false)) break

                val taskList = listJson.optJSONArray("data")
                if (taskList == null || taskList.length() == 0) break

                // 寻找第一个待完成且符合规则 of 浏览任务
                var targetTask: JSONObject? = null
                for (i in 0 until taskList.length()) {
                    val task = taskList.optJSONObject(i) ?: continue
                    val taskId = task.optString("taskId", "")
                    val taskName = task.optString("taskName", "")
                    val taskType = task.optString("taskType", "")
                    val bizTaskType = task.optString("bizTaskType", "")
                    val taskStatus = task.optString("taskStatus", "")
                    val buttonText = task.optString("buttonText", "")

                    if ("FINISHED" == taskStatus || "已完成" == buttonText) continue
                    if ("BROWSER" != taskType) continue
                    if (taskName.contains("灯火") || "denghuoClickType" == bizTaskType) continue
                    if (taskName.contains("订阅") || taskName.contains("消息通知")) continue
                    if (taskName.contains("回收") || taskName.contains("办卡") || taskName.contains("下单") || taskName.contains("月卡") || taskName.contains("打车") || taskName.contains("宽带")) continue

                    targetTask = task
                    break
                }

                if (targetTask == null) break

                val taskId = targetTask.optString("taskId", "")
                val taskName = targetTask.optString("taskName", "")
                val taskBrowseTime = if (targetTask.has("taskBrowseTime") && !targetTask.isNull("taskBrowseTime")) {
                    targetTask.optInt("taskBrowseTime")
                } else {
                    15
                }

//                Log.other(TAG, "⏳ [$loopCount/$maxTasks] 正在执行: $taskName")

                val taskRecordId = executeSignup(token, userId, taskId, taskName)
                if (taskRecordId.isNullOrEmpty()) break

                val sleepSeconds = maxOf(taskBrowseTime, 5)
                Thread.sleep(sleepSeconds * 1000L)

                val completeSuccess = queryTaskStatus(token, userId, taskId, taskRecordId)
                if (completeSuccess) {
                    Log.other(TAG, "✅ 任务 $taskName 完成")
                    completedCount++
                } else {
                    Log.other(TAG, "❌ 任务 $taskName 失败")
                }

                // 每次完成一定量任务后要随机延迟多一点 (每完成3个，休息10-15秒；否则休息3-4.5秒)
                if (completedCount > 0 && completedCount % 3 == 0) {
                    val longSleep = RandomUtil.nextGaussianLong(10000, 15000)
                    Log.other(TAG, "☕ 已完成 $completedCount 个任务，随机休眠 ${longSleep / 1000} 秒...")
                    Thread.sleep(longSleep)
                } else {
                    Thread.sleep(RandomUtil.nextGaussianLong(3000, 4500))
                }
            }

        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    private fun getValidToken(userId: String): String? {
        val token = cachedToken
        if (!token.isNullOrEmpty() && checkTokenValid(token, userId)) {
            return token
        }
        // Token无效或为空，重新获取
        return loginAndGetToken(userId)
    }

    private fun checkTokenValid(token: String, userId: String): Boolean {
        return try {
            val result = queryTaskListRaw(token, userId)
            result != null && result.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }

    private fun loginAndGetToken(userId: String): String? {
        try {
//            Log.runtime(TAG, "🔑 开始重新获取 Token...")
            val authCode = AuthCodeHelper.getAuthCode(APP_ID)
            if (authCode.isNullOrEmpty()) {
                Log.error(TAG, "获取 authCode 失败")
                return null
            }

            val miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(APP_ID, VERSION)
            val url = "https://gdbizweb.alipay-eco.com/goduck/getToken?authCode=$authCode&version=3&channelSource=self"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept-Charset", "UTF-8")
                .addHeader("Referer", REFERER)
                .addHeader("x-release-type", "ONLINE")
                .addHeader("userid", userId)
                .addHeader("alipayminimark", miniMark)
                .addHeader("User-Agent", getUA())
                .addHeader("Accept", "*/*")
                .addHeader("x-allow-afts-limit", "true")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.error(TAG, "Token 接口请求失败, HTTP Code: ${response.code}")
                    return null
                }

                val bodyText = response.body?.string() ?: ""
                val json = JSONObject(bodyText)
                if (json.optBoolean("success", false)) {
                    val data = json.optJSONObject("data")
                    val token = data?.optString("token", "") ?: ""
                    if (token.isNotEmpty()) {
                        cachedToken = token
                        Log.runtime(TAG, "✅ Token 获取成功: $token")
                        
                        // 模拟小程序正常加载行为以防止风控
                        simulatePostLogin(token, userId, miniMark)
                        return token
                    }
                }
                Log.error(TAG, "解析 Token 失败: $bodyText")
            }
        } catch (e: Exception) {
            Log.error(TAG, "获取 Token 异常: ${e.message}")
        }
        return null
    }

    /**
     * 模拟登录后的正常数据同步及UI查询，模拟正常人行为防止风控
     */
    private fun simulatePostLogin(token: String, userId: String, miniMark: String) {
        try {
            // 1. Task Sync
            val syncUrl = "https://gdbizweb.alipay-eco.com/gdbizweb/task/sync?channelSource=self&token=$token&version=3"
            val mediaType = "application/json".toMediaType()
            val syncBody = "{\"chinfo\":\"default\",\"taskType\":\"channel_rewards\"}".toRequestBody(mediaType)
            val syncRequest = Request.Builder()
                .url(syncUrl)
                .post(syncBody)
                .addHeader("Accept-Charset", "UTF-8")
                .addHeader("Referer", REFERER)
                .addHeader("x-release-type", "ONLINE")
                .addHeader("userid", userId)
                .addHeader("alipayminimark", miniMark)
                .addHeader("User-Agent", getUA())
                .addHeader("Accept", "*/*")
                .addHeader("x-allow-afts-limit", "true")
                .build()

            client.newCall(syncRequest).execute().close()
            Thread.sleep(1000)

            // 2. UI Info
            val uiUrl = "https://gdbizweb.alipay-eco.com/gdbizweb/ui/info?channelSource=self&token=$token&version=3"
            val uiRequest = Request.Builder()
                .url(uiUrl)
                .get()
                .addHeader("Accept-Charset", "UTF-8")
                .addHeader("Referer", REFERER)
                .addHeader("x-release-type", "ONLINE")
                .addHeader("userid", userId)
                .addHeader("alipayminimark", miniMark)
                .addHeader("User-Agent", getUA())
                .addHeader("Accept", "*/*")
                .addHeader("x-allow-afts-limit", "true")
                .build()

            client.newCall(uiRequest).execute().close()
        } catch (e: Exception) {
            Log.error(TAG, "模拟启动行为异常（非关键失败，忽略）: ${e.message}")
        }
    }

    /**
     * 查询签到状态，返回 data 对象；失败或不可用时返回 null
     */
    private fun querySignInStatus(token: String, userId: String): JSONObject? {
        try {
            val miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(APP_ID, VERSION)
            val url = "https://gdbizweb.alipay-eco.com/gdbizweb/task/signin/query?channelSource=self&token=$token&version=3"
            val mediaType = "application/json".toMediaType()
            val body = "{\"channelSource\":\"self\"}".toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept-Charset", "UTF-8")
                .addHeader("Referer", REFERER)
                .addHeader("x-release-type", "ONLINE")
                .addHeader("userid", userId)
                .addHeader("alipayminimark", miniMark)
                .addHeader("User-Agent", getUA())
                .addHeader("Accept", "*/*")
                .addHeader("x-allow-afts-limit", "true")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyText = response.body.string()
                    val json = JSONObject(bodyText)
                    if (json.optBoolean("success", false)) {
                        return json.optJSONObject("data")
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "查询签到状态异常: ${e.message}")
        }
        return null
    }

    private fun executeSignIn(token: String, userId: String): Boolean {
        try {
            val miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(APP_ID, VERSION)
            val url = "https://gdbizweb.alipay-eco.com/gdbizweb/task/signin/execute/v3?channelSource=self&token=$token&version=3"
            val mediaType = "application/json".toMediaType()
            val body = "{\"channelSource\":\"self\"}".toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept-Charset", "UTF-8")
                .addHeader("Referer", REFERER)
                .addHeader("x-release-type", "ONLINE")
                .addHeader("userid", userId)
                .addHeader("alipayminimark", miniMark)
                .addHeader("User-Agent", getUA())
                .addHeader("Accept", "*/*")
                .addHeader("x-allow-afts-limit", "true")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyText = response.body.string()
                    val json = JSONObject(bodyText)
                    if (json.optBoolean("success", false)) {
                        Log.other(TAG, "✅ 每日签到成功!")
                        return true
                    }
                    Log.other(TAG, "❌ 每日签到业务返回失败: $bodyText")
                } else {
                    Log.other(TAG, "❌ 每日签到接口调用失败: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "每日签到异常: ${e.message}")
        }
        return false
    }

    private fun queryTaskListRaw(token: String, userId: String): JSONObject? {
        try {
            val miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(APP_ID, VERSION)
            val url = "https://gdbizweb.alipay-eco.com/gdbizweb/task/list/query?channelSource=self&token=$token&version=3"
            val mediaType = "application/json".toMediaType()
            val body = "{}".toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept-Charset", "UTF-8")
                .addHeader("Referer", REFERER)
                .addHeader("x-release-type", "ONLINE")
                .addHeader("userid", userId)
                .addHeader("alipayminimark", miniMark)
                .addHeader("User-Agent", getUA())
                .addHeader("Accept", "*/*")
                .addHeader("x-allow-afts-limit", "true")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyText = response.body?.string() ?: ""
                    return JSONObject(bodyText)
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "查询任务列表接口异常: ${e.message}")
        }
        return null
    }

    private fun executeSignup(token: String, userId: String, taskId: String, taskName: String): String? {
        try {
            val miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(APP_ID, VERSION)
            val url = "https://gdbizweb.alipay-eco.com/gdbizweb/task/signup?channelSource=self&token=$token&version=3"
            val mediaType = "application/json".toMediaType()
            
            val signupJson = JSONObject().apply {
                put("needSignUp", true)
                put("taskId", taskId)
                put("taskName", taskName)
                put("type", "BROWSER")
                put("acType", "")
            }
            val body = signupJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept-Charset", "UTF-8")
                .addHeader("Referer", REFERER)
                .addHeader("x-release-type", "ONLINE")
                .addHeader("userid", userId)
                .addHeader("alipayminimark", miniMark)
                .addHeader("User-Agent", getUA())
                .addHeader("Accept", "*/*")
                .addHeader("x-allow-afts-limit", "true")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyText = response.body?.string() ?: ""
                    val json = JSONObject(bodyText)
                    if (json.optBoolean("success", false)) {
                        return json.optString("data", "")
                    }
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "报名任务 $taskName 异常: ${e.message}")
        }
        return null
    }

    private fun queryTaskStatus(token: String, userId: String, taskId: String, taskRecordId: String): Boolean {
        try {
            val miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(APP_ID, VERSION)
            val url = "https://gdbizweb.alipay-eco.com/gdbizweb/task/status/query?channelSource=self&token=$token&version=3"
            val mediaType = "application/json".toMediaType()
            
            val statusJson = JSONObject().apply {
                put("taskId", taskId)
                put("taskRecordId", taskRecordId)
            }
            val body = statusJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept-Charset", "UTF-8")
                .addHeader("Referer", REFERER)
                .addHeader("x-release-type", "ONLINE")
                .addHeader("userid", userId)
                .addHeader("alipayminimark", miniMark)
                .addHeader("User-Agent", getUA())
                .addHeader("Accept", "*/*")
                .addHeader("x-allow-afts-limit", "true")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyText = response.body?.string() ?: ""
                    val json = JSONObject(bodyText)
                    return json.optBoolean("success", false)
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "查询任务状态 $taskId 异常: ${e.message}")
        }
        return false
    }

    private fun getUA(): String {
        val systemUa = System.getProperty("http.agent") ?: "Mozilla/5.0 (Linux; Android 15)"
        val alipayVer = ApplicationHook.getAlipayVersion() ?: "10.8.50"
        return "$systemUa Version/4.0 Chrome/126.0.6478.122 MYWeb/1.3.126.260313173624 UWS/3.22.2.9999 UCBS/3.22.2.9999_220000000000 Mobile Safari/537.36 NebulaSDK/1.8.100112 Nebula AlipayDefined(nt:WIFI,ws:407|0|3.0) AliApp(AP/$alipayVer) AlipayClient/$alipayVer Language/zh-Hans isConcaveScreen/true Region/CNAriver/$alipayVer ChannelId(6) DTN/2.0"
    }
}
