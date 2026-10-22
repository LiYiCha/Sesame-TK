package fansirsqi.xposed.sesame.task.antForest

import android.net.Uri
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.internal.AlipayMiniMarkHelper
import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 森林无纸阅读：登录授权 → 选免费书 → 逐章阅读上报 → 达到当日收益目标置旗标
 * 单次运行内循环完成，阅读时长通过真实等待累积后上报；章节进度按账号存 DataStore，中断后下次续读
 */
object ForestRead {
    private const val TAG = "ForestRead"
    private const val FLAG_DONE = "forest::readDone"
    private const val APP_ID = "2021003114652763"
    private const val VERSION = "0.2.2410251510.53"
    private const val API = "https://m.zhangwenwh.com/api"
    private const val READ_INTERVAL_MS = 6 * 60 * 1000L
    private const val MAX_CHAPTERS = 30

    private class AuthExpired : Exception("登录态失效")

    private val running = AtomicBoolean(false)

    @Volatile
    private var runningUid = ""
    private val miniMark by lazy { AlipayMiniMarkHelper.getAlipayMiniMark(APP_ID, VERSION) }

    private val tokenKey: String
        get() = "forestRead_token::$runningUid"

    suspend fun run() {
        if (Status.hasFlagToday(FLAG_DONE) || !running.compareAndSet(false, true)) return
        // 运行期间固定账号键，防止中途切换账号把进度写到新账号名下
        runningUid = UserMap.currentUid.orEmpty()
        try {
            val token = DataStore.get(tokenKey, String::class.java).orEmpty()
            try {
                doWork(if (token.isBlank()) loginAndSave() else token)
            } catch (e: AuthExpired) {
                Log.error(TAG, "无纸阅读登录态失效，重新授权")
                doWork(loginAndSave())
            }
        } catch (t: Throwable) {
            if (t is CancellationException || t is InterruptedException) throw t
            Log.printStackTrace(TAG, "无纸阅读失败:", t)
        } finally {
            running.set(false)
        }
    }

    private fun loginAndSave(): String {
        val code = AuthCodeHelper.getAuthCode(APP_ID)
        require(!code.isNullOrBlank()) { "无纸阅读授权码为空" }
        val data = request("/authorization", "", JSONObject().put("auth_code", code)
            .put("app_id", APP_ID).put("cid", "7001")).getJSONObject("data")
        val token = data.getString("token")
        require(token.isNotBlank()) { "无纸阅读登录缺少 token" }
        DataStore.put(tokenKey, token)
        return token
    }

    private suspend fun doWork(token: String) {
        var progress = energy(token)
        var current = progress.getInt("current")
        val total = progress.getInt("total")
        require(current >= 0 && total > 0) { "无纸阅读收益进度无效: $progress" }
        if (current >= total) {
            Status.setFlagToday(FLAG_DONE)
            DataStore.remove(stateKey)
            Log.forest("无纸阅读今日进度 $current/$total 已达成")
            return
        }

        val restored = loadState()
        var bookId = restored?.optString("bookId").orEmpty()
        var bookName = restored?.optString("bookName").orEmpty()
        var nextChapterId = restored?.optString("nextChapterId").orEmpty()
        val finished = restored?.optJSONArray("finished")?.let { arr ->
            (0 until arr.length()).mapTo(hashSetOf()) { arr.optString(it) }
        } ?: hashSetOf()
        var needPick = bookId.isNotBlank() // 恢复的书需先校验仍在免费列表
        var chapters = 0
        while (current < total && chapters < MAX_CHAPTERS) {
            if (bookId.isBlank() || needPick) {
                val freeBooks = queryFreeBooks(token, finished)
                val book = freeBooks[bookId] ?: freeBooks.values.firstOrNull()
                if (book == null) {
                    Log.runtime(TAG, "无纸阅读暂无未读免费书籍")
                    return
                }
                val pickedId = book.getString("book_id")
                if (pickedId != bookId) {
                    bookId = pickedId
                    nextChapterId = ""
                }
                bookName = book.optString("name", bookId)
                needPick = false
            }
            val chapter = chapter(token, bookId, nextChapterId)
            val chapterId = chapter.getString("id")
            require(chapterId.isNotBlank() && chapter.has("next_id")) { "无纸阅读章节结构不完整: $chapter" }
            val started = System.currentTimeMillis()
            delay(READ_INTERVAL_MS)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - started)
            report(token, bookId, chapterId, seconds)
            recent(token, bookId, chapterId, chapter.optString("name"))
            chapters++

            progress = energy(token)
            val now = progress.getInt("current")
            val gain = now - current
            if (gain <= 0 && now < total) {
                Log.runtime(TAG, "无纸阅读上报后收益未推进，当前 $now/$total，下次运行重试")
                return
            }
            if (gain > 0) {
                Log.forest("无纸阅读[$bookName] 获得${gain}g能量，进度 $now/$total")
            }
            current = now
            val next = chapter.optString("next_id")
            if (next.isBlank() || next == "0" || next == "null") {
                finished.add(bookId)
                bookId = ""
                bookName = ""
            } else {
                nextChapterId = next
            }
            saveState(bookId, bookName, nextChapterId, finished)
        }
        if (current >= total) {
            Status.setFlagToday(FLAG_DONE)
            DataStore.remove(stateKey)
            Log.forest("无纸阅读今日进度 $current/$total 已达成")
        } else {
            Log.runtime(TAG, "无纸阅读本轮阅读 $chapters 章，进度 $current/$total，等待下次运行继续")
        }
    }

    private val stateKey: String
        get() = "forestRead_state::$runningUid"

    /** 读取当日阅读进度，跨天丢弃 */
    private fun loadState(): JSONObject? {
        val raw = DataStore.get(stateKey, String::class.java)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val state = JSONObject(raw)
            if (TimeUtil.isSameDay(state.optLong("day"), System.currentTimeMillis())) state else null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveState(bookId: String, bookName: String, nextChapterId: String, finished: Set<String>) {
        val state = JSONObject().put("day", System.currentTimeMillis())
            .put("bookId", bookId).put("bookName", bookName).put("nextChapterId", nextChapterId)
            .put("finished", JSONArray(finished.toList()))
        DataStore.put(stateKey, state.toString())
    }

    /** 取免费书籍（price=0），优先上次未读完的书 */
    private fun queryFreeBooks(token: String, finished: Set<String>): LinkedHashMap<String, JSONObject> {
        val catalog = request("/index/index?type=1", token).getJSONArray("data")
        val freeBooks = linkedMapOf<String, JSONObject>()
        for (i in 0 until catalog.length()) {
            val resources = catalog.getJSONObject(i).optJSONArray("block_resource") ?: continue
            for (j in 0 until resources.length()) {
                val book = resources.getJSONObject(j)
                val id = book.optString("book_id")
                if (id.isNotBlank() && book.optString("price").toBigDecimalOrNull()?.signum() == 0 && id !in finished) {
                    freeBooks[id] = book
                }
            }
        }
        return freeBooks
    }

    private fun energy(token: String): JSONObject =
        request("/user/forestPro", token).getJSONObject("data")

    private fun chapter(token: String, bookId: String, chapterId: String): JSONObject {
        val path = "/book/chapter?book_id=${Uri.encode(bookId)}" +
            if (chapterId.isBlank()) "" else "&chapter_id=${Uri.encode(chapterId)}"
        return request(path, token).getJSONObject("data")
    }

    private fun report(token: String, bookId: String, chapterId: String, seconds: Long) {
        require(seconds > 0)
        request("/user/updateWel", token, JSONObject().put("book_id", bookId)
            .put("chapter_id", chapterId.toLongOrNull() ?: chapterId).put("time", seconds))
    }

    private fun recent(token: String, bookId: String, chapterId: String, chapterName: String) {
        request("/book/set_read_recently", token, JSONObject().put("book_id", bookId)
            .put("chapter_id", chapterId.toLongOrNull() ?: chapterId).put("chapter_name", chapterName))
    }

    private fun request(path: String, token: String, body: JSONObject? = null): JSONObject {
        val connection = URL("$API$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            System.getProperty("http.agent")?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("User-Agent", it)
            }
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
            connection.setRequestProperty("alipayminimark", miniMark)
            connection.setRequestProperty("Referer", "https://$APP_ID.hybrid.alipay-eco.com/$APP_ID/$VERSION/index.html")
            connection.setRequestProperty("x-release-type", "ONLINE")
            if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            val bytes = body?.toString()?.toByteArray(Charsets.UTF_8)
            if (bytes != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val httpCode = connection.responseCode
            val stream = if (httpCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (httpCode == 401) throw AuthExpired()
            check(httpCode in 200..299) { "无纸阅读 HTTP=$httpCode path=$path" }
            val response = JSONObject(raw)
            check(response.has("code")) { "无纸阅读响应缺少业务状态 path=$path" }
            when (response.getInt("code")) {
                401 -> throw AuthExpired()
                2000 -> return response
                else -> throw IllegalStateException("无纸阅读请求失败 path=$path code=${response.getInt("code")}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
