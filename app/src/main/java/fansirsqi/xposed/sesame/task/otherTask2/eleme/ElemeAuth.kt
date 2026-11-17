package fansirsqi.xposed.sesame.task.otherTask2.eleme

import android.webkit.CookieManager
import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 饿了么授权服务：管理饿了么小程序会话 Cookie，支持 72 小时无感刷新与多账号隔离
 */
object ElemeAuth {
    private const val TAG = "ElemeAuth"

    /** 饿了么外卖支付宝小程序 AppId */
    const val APP_ID = "2021005151656223"

    private const val KEY_COOKIE = "Eleme_Cookie"
    private const val KEY_EXPIRES = "Eleme_Cookie_Expires"
    private const val KEY_USER_ID = "Eleme_UserId"

    /** 默认小程序 User-Agent */
    const val DEFAULT_UA = "Mozilla/5.0 (Linux; U; Android 16; zh-CN; vivo V2238A Build/BP1A.250305.019) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/118.0.0.0 UCBrowser/16.3.8.1402 Mobile Safari/537.36 AlipayDefined(nt:wifi,ws:384|0|853) AliApp(AP/12.12.30.8000) AlipayClient/12.12.30.8000 Language/zh-Hans useStatusBar/true isConcaveScreen/false Region/CN Channel/vivo GCanvas/1.9.38 Nebula SDK/1.8.1000 Nebula Ariver/1.0.0"

    /** 提前 30 分钟刷新避免边界失效 */
    private const val EXPIRE_MARGIN_MS = 30 * 60 * 1000L

    @Volatile
    private var memoryUid: String? = null
    @Volatile
    private var memoryCookie: String = ""
    @Volatile
    private var memoryExpiresAt: Long = 0L

    fun getUidKey(prefix: String): String {
        val uid = UserMap.currentUid
        return if (!uid.isNullOrEmpty()) "${prefix}_$uid" else prefix
    }

    /**
     * 外部网络捕获注入真实 Cookie
     */
    @Synchronized
    fun saveCapturedCookie(captured: String) {
        if (captured.isBlank()) return
        if (!captured.contains("SID=") && !captured.contains("sid=") && !captured.contains("user_id=")) return

        val keyCookie = getUidKey(KEY_COOKIE)
        val keyExpires = getUidKey(KEY_EXPIRES)
        val now = System.currentTimeMillis()
        val expires = now + 72 * 3600 * 1000L

        memoryCookie = captured
        memoryExpiresAt = expires
        memoryUid = UserMap.currentUid

        DataStore.put(keyCookie, captured)
        DataStore.put(keyExpires, expires)
        Log.other("饿了么", "[授权] 已自动同步捕获的真机饿了么会话凭据")
    }

    /**
     * 获取有效的 Cookie，若过期或不存在则自动刷新（支持多账号数据隔离）
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun ensureCookie(force: Boolean = false): String {
        val currentUid = UserMap.currentUid
        if (currentUid != memoryUid) {
            memoryUid = currentUid
            memoryCookie = ""
            memoryExpiresAt = 0L
        }

        val keyCookie = getUidKey(KEY_COOKIE)
        val keyExpires = getUidKey(KEY_EXPIRES)
        val now = System.currentTimeMillis()

        if (!force) {
            if (memoryCookie.isNotEmpty() && memoryExpiresAt - now > EXPIRE_MARGIN_MS) {
                return memoryCookie
            }
            val diskCookie = DataStore.get(keyCookie, String::class.java)
            val diskExpires = DataStore.get(keyExpires, Long::class.java) ?: 0L
            if (!diskCookie.isNullOrEmpty() && diskExpires - now > EXPIRE_MARGIN_MS) {
                memoryCookie = diskCookie
                memoryExpiresAt = diskExpires
                return memoryCookie
            }
            // 尝试读取 WebView 宿主本地 Cookie
            val webviewCookie = getWebviewCookie()
            if (!webviewCookie.isNullOrEmpty() && (webviewCookie.contains("SID=") || webviewCookie.contains("sid="))) {
                memoryCookie = webviewCookie
                memoryExpiresAt = now + 72 * 3600 * 1000L
                DataStore.put(keyCookie, memoryCookie)
                DataStore.put(keyExpires, memoryExpiresAt)
                Log.other("饿了么", "[授权] 已读取本地 WebView Cookie 并初始化缓存")
                return memoryCookie
            }
        }

        // 调用 authCode 登录刷新
        val refreshed = refresh()
        if (refreshed.isNotEmpty()) {
            return refreshed
        }

        // 仅在非强制刷新模式下尽力而为返回现有 Cookie
        if (!force) {
            val fallback = memoryCookie.ifEmpty { DataStore.get(keyCookie, String::class.java).orEmpty() }
            if (fallback.isNotEmpty()) {
                return fallback
            }
        }
        return ""
    }

    /**
     * 仅只读读取缓存 Cookie
     */
    fun peekCookie(): String {
        val currentUid = UserMap.currentUid
        if (currentUid == memoryUid && memoryCookie.isNotEmpty()) return memoryCookie
        return DataStore.get(getUidKey(KEY_COOKIE), String::class.java).orEmpty()
    }

    /**
     * 标记 Cookie 失效并彻底清除磁盘与内存缓存
     */
    @Synchronized
    fun invalidate() {
        memoryCookie = ""
        memoryExpiresAt = 0L
        DataStore.put(getUidKey(KEY_COOKIE), "")
        DataStore.put(getUidKey(KEY_EXPIRES), 0L)
    }

    /**
     * 校验会话状态并在失效时自动换票刷新自愈
     * @return 有效的会话 Cookie，若刷新失败或仍未登录则返回 null（调用方应立即优雅退出）
     */
    fun validateOrRefreshCookie(initialCookie: String): String? {
        val checkResult = LuckyStarRpcCall.checkLoginResult(initialCookie)
        if (checkResult.isSuccess && !checkResult.isNotLogin) {
            return initialCookie
        }

        Log.other("饿了么", "[授权] 检测到当前饿了么会话已失效，开始自动换票刷新自愈...")
        invalidate()
        val newCookie = ensureCookie(force = true)
        if (newCookie.isEmpty()) {
            Log.other("饿了么", "[授权] ⚠️ 自动换票刷新会话失败，请在支付宝手动打开并授权饿了么小程序")
            return null
        }

        val recheckResult = LuckyStarRpcCall.checkLoginResult(newCookie)
        if (recheckResult.isSuccess && !recheckResult.isNotLogin) {
            Log.other("饿了么", "[授权] ✅ 饿了么会话已自动自愈刷新成功")
            return newCookie
        } else {
            Log.other("饿了么", "[授权] ⚠️ 换票后会话校验仍未通过，请在支付宝手动打开饿了么小程序完成登录")
            return null
        }
    }

    /**
     * 判断当前账号是否具备有效饿了么凭据
     */
    @JvmStatic
    fun isBound(): Boolean {
        val cookie = peekCookie()
        return cookie.contains("USERID=") || cookie.contains("user_id=") || cookie.contains("SID=") || cookie.contains("sid=") || cookie.contains("bindTag=existed")
    }

    /**
     * CK 凭据状态结构化信息（供账号状态弹窗展示）
     */
    data class CookieStatus(
        val exists: Boolean,   // 是否存在凭据
        val valid: Boolean,    // 是否处于有效期内（已含 30 分钟提前刷新余量）
        val expiresAt: Long,   // 有效期截止时间戳（0 表示未知）
        val source: String     // 凭据来源：内存缓存 / 磁盘缓存 / 未获取
    )

    /**
     * 获取当前账号 CK 凭据的结构化状态（只读，不触发网络刷新）
     */
    @JvmStatic
    fun getCookieStatus(): CookieStatus {
        val currentUid = UserMap.currentUid
        val now = System.currentTimeMillis()
        // 内存缓存优先
        if (currentUid == memoryUid && memoryCookie.isNotEmpty()) {
            return CookieStatus(true, memoryExpiresAt - now > EXPIRE_MARGIN_MS, memoryExpiresAt, "内存缓存")
        }
        // 磁盘缓存（跨进程共享的持久化凭据）
        val diskCookie = DataStore.get(getUidKey(KEY_COOKIE), String::class.java).orEmpty()
        val diskExpires = DataStore.get(getUidKey(KEY_EXPIRES), Long::class.java) ?: 0L
        if (diskCookie.isNotEmpty()) {
            return CookieStatus(true, diskExpires - now > EXPIRE_MARGIN_MS, diskExpires, "磁盘缓存")
        }
        return CookieStatus(false, false, 0L, "未获取")
    }

    /**
     * 通过 auth_code 调用 eaLogin.do 换取饿了么 72 小时登录凭据
     */
    private fun refresh(): String {
        var conn: HttpURLConnection? = null
        try {
            Log.other("饿了么", "[授权] 开始申请饿了么授权码 [$APP_ID]...")
            val authCode = AuthCodeHelper.getAuthCode(APP_ID)
            if (authCode.isNullOrEmpty()) {
                Log.other("饿了么", "[授权] 获取饿了么授权码失败")
                return ""
            }

            Log.other("饿了么", "[授权] 获取授权码成功，换取会话凭据...")
            val postData = "appEntrance=alipay_sg&appId=$APP_ID&appName=eleme" +
                    "&authorizationCode=${URLEncoder.encode(authCode, "UTF-8")}" +
                    "&isMobile=true&lang=zh_CN&needPassWebViewCookie=false&returnUrl=&type=alipay_mini_program"
            val url = URL("https://ipassport.ele.me/mini_program/eaLogin.do?_bx-m=0.0.15")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                doInput = true
                setRequestProperty("Connection", "close")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                setRequestProperty("User-Agent", DEFAULT_UA)
                setRequestProperty("Referer", "https://$APP_ID.hybrid.alipay-eco.com/")
                setRequestProperty("Origin", "https://$APP_ID.hybrid.alipay-eco.com")
            }

            conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val respText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            val setCookies = conn.headerFields["Set-Cookie"] ?: conn.headerFields["set-cookie"] ?: emptyList()
            val cookieMap = mutableMapOf<String, String>()

            // 载入历史 cookie 做基础融合
            val existing = peekCookie()
            if (existing.isNotEmpty()) {
                existing.split(";").forEach { item ->
                    val pair = item.trim().split("=", limit = 2)
                    if (pair.size == 2) {
                        cookieMap[pair[0].trim()] = pair[1].trim()
                    }
                }
            }

            // 合并 Set-Cookie
            for (header in setCookies) {
                val parts = header.split(";")
                if (parts.isNotEmpty()) {
                    val kv = parts[0].trim().split("=", limit = 2)
                    if (kv.size == 2) {
                        cookieMap[kv[0].trim()] = kv[1].trim()
                    }
                }
            }

            val json = runCatching { JSONObject(respText) }.getOrNull()
            val contentObj = json?.optJSONObject("content")
            val dataObj = contentObj?.optJSONObject("data") ?: json?.optJSONObject("data")

            // 如果返回了 iframeRedirectUrl，尝试跟随请求获取跳转 Cookie
            val iframeRedirect = dataObj?.optBoolean("iframeRedirect", false) ?: false
            val iframeUrl = dataObj?.optString("iframeRedirectUrl").orEmpty()
            if (iframeRedirect && iframeUrl.isNotEmpty()) {
                Log.other("饿了么", "[授权] 检测到绑定验证引导，跟随链接获取凭据...")
                followRedirectForCookies(iframeUrl, cookieMap)
            }

            // 从 content.data 提取所有票据并压入 Cookie
            if (dataObj != null) {
                val it = dataObj.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    val value = dataObj.optString(key)
                    if (value.isNotEmpty() && value != "null" && !key.contains("Redirect")) {
                        cookieMap[key] = value
                    }
                }
            }

            val expires = dataObj?.optLong("expires") ?: (System.currentTimeMillis() + 72 * 3600 * 1000L)
            val userId = dataObj?.optString("USERID")?.takeIf { it.isNotEmpty() }
                ?: dataObj?.optString("user_id")?.takeIf { it.isNotEmpty() }
                ?: dataObj?.optString("userId").orEmpty()
            val sid = dataObj?.optString("SID")?.takeIf { it.isNotEmpty() }
                ?: dataObj?.optString("sid").orEmpty()

            if (sid.isNotEmpty()) {
                cookieMap["SID"] = sid
                cookieMap["sid"] = sid
            }
            if (userId.isNotEmpty()) {
                cookieMap["USERID"] = userId
                cookieMap["user_id"] = userId
            }

            val finalCookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (finalCookie.isNotEmpty() && (cookieMap.containsKey("SID") || cookieMap.containsKey("sid") || cookieMap.containsKey("USERID") || cookieMap.containsKey("user_id"))) {
                memoryCookie = finalCookie
                memoryExpiresAt = expires
                val keyCookie = getUidKey(KEY_COOKIE)
                val keyExpires = getUidKey(KEY_EXPIRES)
                val keyUserId = getUidKey(KEY_USER_ID)
                val keyUsername = getUidKey("Eleme_Username")

                DataStore.put(keyCookie, finalCookie)
                DataStore.put(keyExpires, expires)
                if (userId.isNotEmpty()) DataStore.put(keyUserId, userId)
                val username = dataObj?.optString("username").orEmpty()
                if (username.isNotEmpty()) DataStore.put(keyUsername, username)

                Log.runtime(TAG, "已成功刷新饿了么登录态，有效期至: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(expires))}")
                return finalCookie
            } else {
                if (iframeRedirect) {
                    invalidate()
                    Log.runtime(TAG, "换取登录态失败(当前账号未绑定饿了么或存在冲突待确认)，自动关闭任务: 响应: $respText")
                } else {
                    Log.other("饿了么", "[授权] 换取登录态未返回有效 Cookie: $respText")
                }
            }
        } catch (t: Throwable) {
            Log.other("饿了么", "[授权] 刷新饿了么登录态异常: ${t.message}")
        } finally {
            conn?.disconnect()
        }
        return ""
    }

    private fun followRedirectForCookies(urlStr: String, cookieMap: MutableMap<String, String>) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                instanceFollowRedirects = false
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", DEFAULT_UA)
                if (cookieMap.isNotEmpty()) {
                    setRequestProperty("Cookie", cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            val headers = conn.headerFields["Set-Cookie"] ?: conn.headerFields["set-cookie"] ?: emptyList()
            for (header in headers) {
                val parts = header.split(";")
                if (parts.isNotEmpty()) {
                    val kv = parts[0].trim().split("=", limit = 2)
                    if (kv.size == 2) {
                        cookieMap[kv[0].trim()] = kv[1].trim()
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            conn?.disconnect()
        }
    }

    private fun getWebviewCookie(): String? {
        return runCatching {
            CookieManager.getInstance().getCookie("https://waimai-guide.ele.me")
                ?: CookieManager.getInstance().getCookie(".ele.me")
        }.getOrNull()
    }

    /**
     * 构造通用的请求头
     */
    fun getCommonHeaders(cookie: String): Map<String, String> {
        return mapOf(
            "Cookie" to cookie,
            "x-tap" to "ap",
            "Referer" to "https://$APP_ID.hybrid.alipay-eco.com/",
            "x-ele-ua" to "RenderWay/miniProgram MiniAppId/$APP_ID MiniAppVersion/12.1.53 Channel/mini_app",
            "User-Agent" to DEFAULT_UA
        )
    }

    /**
     * 获取当前账号运行摘要信息
     */
    fun getAccountSummary(): String {
        val alipayMask = UserMap.getCurrentMaskName().ifEmpty { "当前用户" }
        val currentUid = UserMap.currentUid
        val alipayUid = if (!currentUid.isNullOrEmpty()) mask(currentUid, 4, 4) else "未知UID"
        val elemeUser = DataStore.get(getUidKey("Eleme_Username"), String::class.java).orEmpty()
        val elemeUid = DataStore.get(getUidKey(KEY_USER_ID), String::class.java).orEmpty()

        val elemeInfo = if (elemeUser.isNotEmpty() || elemeUid.isNotEmpty()) {
            val userStr = if (elemeUser.isNotEmpty()) mask(elemeUser, 3, 3) else "已绑定"
            val uidStr = if (elemeUid.isNotEmpty()) mask(elemeUid, 3, 3) else "已同步"
            "饿了么账号: $userStr (UID: $uidStr)"
        } else {
            "饿了么: 登录中"
        }
        return "支付宝: $alipayMask ($alipayUid) | $elemeInfo"
    }

    private fun mask(str: String?, prefixLen: Int = 3, suffixLen: Int = 3): String {
        if (str.isNullOrEmpty()) return ""
        if (str.length <= prefixLen + suffixLen) return str
        val prefix = str.take(prefixLen)
        val suffix = str.takeLast(suffixLen)
        return "$prefix***$suffix"
    }
}
