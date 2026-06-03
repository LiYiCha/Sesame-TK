package fansirsqi.xposed.sesame.util

import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.internal.AlipayMiniMarkHelper
import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

enum class GameTask(
    val title: String,
    val appId: String,
    val gid: String,
    val action: String,
    val channel: String,
    val version: String,
    val requestsPerEgg: Int //完成1个🥚要多少次 为了防止网络崩溃 多加1次
) {
    Orchard_ncscc("农场上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "nongchangleyuan", "1.0.2", 2),
    Farm_ddply("对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "zhuangyuan", "1.0.14", 2),

    Forest_slxcc("森林小车车","2060170000363691","zfb_slxcc","slxcc_game_kaiche_every_10","lianyun_senlin_leyuan","1.0.1",3),
    Forest_sljyd("森林救援队(能量雨)", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "lianyun_senlin_leyuan", "1.0.1", 3);

    private var cachedToken: String? = null

    /**
     * 第一步：登录获取 Token 并缓存
     */
    private fun login(): String? {
        return try {
            val authCode = AuthCodeHelper.getAuthCode(appId)
            val mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version)
            val reqId = "${System.currentTimeMillis()}_${(1..350).random()}"

            val body = JSONObject().apply {
                put("v", version); put("code", authCode); put("pf", "zfb")
                put("reqId", reqId); put("gid", gid); put("version", version)
            }.toString()

            val conn = (URL("https://gamesapi2.aslk2018.com/v2/game/login").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("alipayMiniMark", mark)
                setRequestProperty("User-Agent", getDynamicUA())
                setRequestProperty("x-release-type", "ONLINE")
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            // 💡 改进：登录失败也要读错误流
            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "EMPTY"

            val resJson = JSONObject(responseText)
            if (resJson.optInt("code") == 1) {
                val token = resJson.optJSONObject("data")?.optString("token")
                //Log.record(title, "✅ 登录成功，Token 已获取")
                token
            } else {
                // Log.record(title, "❌ 登录接口报错 (Code $respCode): $responseText")
                null
            }
        } catch (e: Exception) {
            //Log.record(title, "🚨 登录过程抛出异常: ${e.message}")
            null
        }
    }

    /**
     * 外部调用：执行上报任务（同步阻塞，保证主任务顺序不乱）
     */
    fun report(eggCount: Int) {
        val totalNeeded = eggCount * (requestsPerEgg + 1) // 正常不需要加1，多1次确保网络请求不会错误
        Log.record(title, "🚀 开始执行上报任务：目标 $eggCount 个蛋，需请求 $totalNeeded 次")
        
        cachedToken = login()
        if (cachedToken.isNullOrEmpty()) {
            Log.record(title, "⚠️ 无法获取有效的 Token，放弃上报任务")
            return
        }

        var successCount = 0
        for (i in 1..totalNeeded) {
            // 执行单次上报，包含重试与Token失效重新登录逻辑
            if (!executeSingleReportWithRetry(i, totalNeeded)) {
                Log.record(title, "⚠️ 上报任务在第 $i 次执行时中断")
                break
            }
            successCount++
            if (i < totalNeeded) {
                // 协程安全延迟，增加防风控随机间隔（由3秒缩短至1-2秒）
                CoroutineUtils.sleepCompat((1000..2000).random().toLong())
            }
        }
        Log.record(title, "🏁 上报任务执行完毕，成功 $successCount/$totalNeeded 次")
    }

    private fun executeSingleReportWithRetry(current: Int, total: Int): Boolean {
        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            attempts++
            val result = executeSingleReportResult(current, total)
            if (result.success) {
                return true
            }

            if (result.isTokenInvalid) {
                Log.record(title, "🔑 检测到 Token 失效，尝试重新登录... (尝试次: $attempts)")
                cachedToken = login()
                if (cachedToken.isNullOrEmpty()) {
                    Log.record(title, "🔑 重新登录获取 Token 失败")
                }
            } else {
                Log.record(title, "⚠️ 第 $current 次上报失败: ${result.errorMsg} (尝试次: $attempts/$maxAttempts)")
            }

            if (attempts < maxAttempts) {
                CoroutineUtils.sleepCompat(1500)
            }
        }
        return false
    }

    private fun executeSingleReportResult(current: Int, total: Int): ReportResult {
        return try {
            val mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version)
            val body = JSONObject().apply {
                put("v", version); put("version", version)
                put("reqId", "${System.currentTimeMillis()}_${(10..99).random()}")
                put("gid", gid); put("action_code", action); put("action_finish_channel", channel)
            }.toString()

            val conn = (URL("https://gamesapi2.aslk2018.com/v2/zfb/taskReport").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("authorization", cachedToken)
                setRequestProperty("alipayMiniMark", mark)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", getDynamicUA())
                setRequestProperty("x-release-type", "ONLINE")
                setRequestProperty("referer", "https://$appId.hybrid.alipay-eco.com/$appId/$version/index.html")
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            // 读取响应码并捕获错误流
            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "NULL_RESPONSE"

            val resJson = JSONObject(responseText)
            val code = resJson.optInt("code")
            val msg = resJson.optString("msg", "")

            if (code == 1) {
                if (current % requestsPerEgg == 0) Log.other(title, "📈 进度: $current/$total (已达成 ${current/requestsPerEgg} 个蛋)")
                ReportResult(success = true)
            } else {
                val isTokenErr = msg.contains("token", ignoreCase = true) || msg.contains("auth", ignoreCase = true) || code == 401
                ReportResult(success = false, isTokenInvalid = isTokenErr, errorMsg = responseText)
            }
        } catch (e: Exception) {
            ReportResult(success = false, errorMsg = e.message ?: "Network error")
        }
    }

    private fun getDynamicUA(): String {
        val systemUa = System.getProperty("http.agent") ?: "Mozilla/5.0 (Linux; Android 15)"
        val alipayVer = ApplicationHook.getAlipayVersion()
        return "$systemUa NebulaSDK/1.8.100112 Nebula AliApp(AP/$alipayVer) AlipayClient/$alipayVer"
    }
}

private data class ReportResult(
    val success: Boolean,
    val isTokenInvalid: Boolean = false,
    val errorMsg: String = ""
)