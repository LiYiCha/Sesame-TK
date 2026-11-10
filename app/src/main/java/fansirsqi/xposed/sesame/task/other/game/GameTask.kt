package fansirsqi.xposed.sesame.task.other.game

import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.internal.AlipayMiniMarkHelper
import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
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
    val requestsPerEgg: Int,
    val pf: String = "zfb"
) {
    Orchard_ncscc("农场-上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "nongchangleyuan", "1.0.2", 2),
    Farm_ddply("农场-对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "zhuangyuan", "1.0.14", 2),
    Forest_slxcc("森林-小车车", "2060170000363691", "zfb_slxcc", "slxcc_game_kaiche_every_10", "lianyun_senlin_leyuan", "1.0.1", 3),
    Forest_sljyd("森林-救援队(能量雨)", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "lianyun_senlin_leyuan", "1.0.1", 3),
    GoldenBean_ddply("金豆-对对碰", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "goldenbean", "1.0.15", 3, "alipay"),
    Member_ddply("会员-对对碰", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "xlyy_WJCNJT", "1.0.15", 3, "alipay"),
    GoldenBean_nccmx("金豆-吃草草", "2060170000355266", "zfb_nccmx", "nccmx_game_xiaochu_every_20", "goldenbean", "1.0.0", 2, "alipay");

    private var cachedToken: String? = null

    private fun postJson(urlString: String, jsonBody: String, authToken: String? = null): Pair<Int, String> {
        val mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version)
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("alipayMiniMark", mark)
            setRequestProperty("User-Agent", getDynamicUA())
            setRequestProperty("x-release-type", "ONLINE")
            setRequestProperty("referer", "https://$appId.hybrid.alipay-eco.com/$appId/$version/index.html")
            if (!authToken.isNullOrEmpty()) {
                setRequestProperty("authorization", authToken)
            }
        }
        return try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(jsonBody) }
            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "EMPTY"
            Pair(respCode, responseText)
        } finally {
            conn.disconnect()
        }
    }

    private fun login(): String? {
        return try {
            val authCode = AuthCodeHelper.getAuthCode(appId)
            if (authCode.isNullOrEmpty()) {
                Log.error(title, "❌ 无法获取授权码(authCode为空)，appId: $appId，请确认是否已在支付宝手动打开并授权过该小游戏")
                return null
            }
            val reqId = "${System.currentTimeMillis()}_${(1..350).random()}"
            val body = JSONObject().apply {
                put("v", version); put("code", authCode); put("pf", pf)
                put("reqId", reqId); put("gid", gid); put("version", version)
                if (gid == "zfb_nccmx") {
                    put("identid", gid)
                }
                put("scene", "")
            }.toString()

            val (respCode, responseText) = postJson("https://gamesapi2.aslk2018.com/v2/game/login", body)
            val resJson = JSONObject(responseText)
            if (resJson.optInt("code") == 1) {
                resJson.optJSONObject("data")?.optString("token")
            } else {
                Log.error(title, "❌ 登录接口报错 (HTTP $respCode): $responseText")
                null
            }
        } catch (e: Exception) {
            Log.printStackTrace(title, "🚨 登录过程抛出异常: ${e.message}", e)
            null
        }
    }

    fun report(eggCount: Int): Boolean {
        if (eggCount <= 0) return true
        val totalNeeded = eggCount * requestsPerEgg
        Log.runtime(title, "🚀 开始执行上报任务：目标 $eggCount 个蛋，需请求 $totalNeeded 次")

        cachedToken = login()
        if (cachedToken.isNullOrEmpty()) {
            Log.runtime(title, "⚠️ 无法获取有效的 Token，放弃上报任务")
            return false
        }

        var successCount = 0
        for (i in 1..totalNeeded) {
            if (!executeSingleReportWithRetry(i, totalNeeded)) {
                Log.runtime(title, "⚠️ 上报任务在第 $i 次执行时中断")
                break
            }
            successCount++
            if (i < totalNeeded) {
                CoroutineUtils.sleepCompat((1000..2000).random().toLong())
            }
        }
        Log.runtime(title, "🏁 上报任务执行完毕，成功 $successCount/$totalNeeded 次")
        return successCount > 0
    }

    private fun executeSingleReportWithRetry(current: Int, total: Int): Boolean {
        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            attempts++
            val result = executeSingleReportResult(current, total)
            if (result.success) return true

            if (result.isTokenInvalid) {
                Log.runtime(title, "🔑 检测到 Token 失效，尝试重新登录... (尝试次: $attempts)")
                cachedToken = login()
                if (cachedToken.isNullOrEmpty()) {
                    Log.runtime(title, "🔑 重新登录失败，终止重试")
                    return false
                }
            } else {
                Log.runtime(title, "⚠️ 第 $current 次上报失败: ${result.errorMsg} (尝试次: $attempts/$maxAttempts)")
            }

            if (attempts < maxAttempts) {
                CoroutineUtils.sleepCompat(1500)
            }
        }
        return false
    }

    private fun executeSingleReportResult(current: Int, total: Int): ReportResult {
        return try {
            val body = JSONObject().apply {
                put("v", version); put("version", version)
                put("reqId", "${System.currentTimeMillis()}_${(10..99).random()}")
                put("gid", gid); put("action_code", action); put("action_finish_channel", channel)
            }.toString()

            val (_, responseText) = postJson("https://gamesapi2.aslk2018.com/v2/zfb/taskReport", body, cachedToken)
            val resJson = JSONObject(responseText)
            val code = resJson.optInt("code")
            val msg = resJson.optString("msg", "")

            if (code == 1) {
                if (current % requestsPerEgg == 0) Log.other(title, "📈 进度: $current/$total (已达成 ${current / requestsPerEgg} / ${total / requestsPerEgg} 个蛋)")
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
