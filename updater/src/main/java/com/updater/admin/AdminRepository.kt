package com.updater.admin

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

/**
 * 安装包与发布管理的数据层（纯业务，无 UI 依赖）
 *
 * 职责：
 * - 管理员登录鉴权（/api/login → token）
 * - 拉取已发布安装包清单（优先管理员直连接口，失败回退公开查询接口，含 appId 模糊匹配）
 * - 删除安装包（网盘物理 DELETE + 清单重发布）
 * - 8MB 分片上传 APK 并自动挂钩发布
 * - 从系统文件选择器拷贝 APK 到缓存并解析元信息（大小 / MD5 / 应用标签）
 *
 * 所有方法均在后台线程执行，回调统一切回主线程；结果用 kotlin.Result 承载，
 * 失败 message 为可直接展示给用户的中文描述。
 */
class AdminRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val CHUNK_SIZE = 8 * 1024 * 1024L // 8MB 分片
    }

    private val main = Handler(Looper.getMainLooper())

    /** 用中文描述构造失败结果（统一包装为异常类型） */
    private fun <T> fail(message: String): Result<T> = Result.failure(IllegalStateException(message))

    /** 一次清单拉取后的服务端状态快照 */
    data class Snapshot(
        val matchedAppId: String,      // 实际匹配到的 appId（可能与请求值大小写/别名不同）
        val appJson: JSONObject,       // 清单根对象（含 appName / latestVersionCode / packages 等）
        val packages: List<JSONObject> // packages 数组的列表化视图
    )

    /** 从系统选择器拷贝到缓存后的 APK 元信息 */
    data class PickedApk(
        val file: File,
        val fileName: String,
        val sizeBytes: Long,
        val md5: String,
        val appLabel: String
    )

    // ------------------------------------------------------------------
    // 管理员登录
    // ------------------------------------------------------------------

    fun login(baseHost: String, username: String, password: String, cb: (Result<String>) -> Unit) {
        val jsonBody = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val req = Request.Builder()
            .url("$baseHost/api/login")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { cb(fail("网络连接失败: ${e.message}")) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val isSuccess = response.isSuccessful
                response.close()
                main.post {
                    val token = if (isSuccess && !body.isNullOrBlank()) {
                        try { JSONObject(body).optString("token") } catch (_: Exception) { "" }
                    } else ""
                    if (token.isNotEmpty()) cb(Result.success(token))
                    else cb(fail("账号或密码错误"))
                }
            }
        })
    }

    // ------------------------------------------------------------------
    // 清单加载（管理员接口优先，公开接口回退）
    // ------------------------------------------------------------------

    fun loadSnapshot(baseHost: String, targetAppId: String, token: String, cb: (Result<Snapshot>) -> Unit) {
        val hasToken = token.isNotBlank()

        Thread {
            var result: Snapshot? = null
            var lastError = "获取安装包清单失败"

            fun fetchAndParse(url: String, withAuth: Boolean, onDone: (Boolean) -> Unit) {
                val builder = Request.Builder()
                    .url(url)
                    .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                    .addHeader("Pragma", "no-cache")
                if (withAuth) builder.addHeader("Authorization", "Bearer $token")
                client.newCall(builder.build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) = onDone(false)
                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string()
                        val ok = response.isSuccessful
                        response.close()
                        if (ok && !body.isNullOrBlank()) {
                            try {
                                result = parseSnapshot(JSONObject(body), targetAppId)
                            } catch (_: Exception) {}
                        }
                        onDone(result != null)
                    }
                })
            }

            if (hasToken) {
                // 管理员接口：直连 R2 无 CDN 缓存，数据最真实
                val latch = java.util.concurrent.CountDownLatch(1)
                fetchAndParse("$baseHost/api/admin/update/publish?_t=${System.currentTimeMillis()}", true) { ok ->
                    if (!ok) lastError = "解析安装包清单异常"
                    latch.countDown()
                }
                latch.await()

                if (result == null) {
                    // 回退公开查询接口
                    val latch2 = java.util.concurrent.CountDownLatch(1)
                    fetchAndParse("$baseHost/api/update?app_id=$targetAppId&_t=${System.currentTimeMillis()}", false) { _ ->
                        latch2.countDown()
                    }
                    latch2.await()
                }

                main.post {
                    result?.let { cb(Result.success(it)) }
                        ?: cb(fail(lastError))
                }
                return@Thread
            }

            // 未登录：仅公开接口
            fetchAndParse("$baseHost/api/update?app_id=$targetAppId&_t=${System.currentTimeMillis()}", false) { _ ->
                main.post {
                    result?.let { cb(Result.success(it)) }
                        ?: cb(fail(lastError))
                }
            }
        }.start()
    }

    /** 解析清单响应：兼容 {apps:{appId:{...}}} 聚合格式与根节点直载格式，含 appId 模糊匹配 */
    private fun parseSnapshot(resJson: JSONObject, targetAppId: String): Snapshot {
        var appObj: JSONObject? = null
        var matchedAppId = targetAppId
        if (resJson.has("apps")) {
            val appsObj = resJson.optJSONObject("apps")
            if (appsObj != null) {
                if (appsObj.has(targetAppId)) {
                    appObj = appsObj.optJSONObject(targetAppId)
                    matchedAppId = targetAppId
                } else {
                    val keys = appsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k.equals(targetAppId, ignoreCase = true) ||
                            k.contains("sesame", ignoreCase = true) ||
                            targetAppId.contains(k, ignoreCase = true)
                        ) {
                            appObj = appsObj.optJSONObject(k)
                            matchedAppId = k
                            break
                        }
                    }
                    if (appObj == null && appsObj.length() == 1) {
                        val onlyKey = appsObj.keys().next()
                        appObj = appsObj.optJSONObject(onlyKey)
                        matchedAppId = onlyKey
                    }
                }
            }
        } else {
            appObj = resJson
        }

        val finalApp = appObj ?: JSONObject()
        return Snapshot(
            matchedAppId = matchedAppId,
            appJson = finalApp,
            packages = finalApp.optJSONArray("packages").toObjList()
        )
    }

    // ------------------------------------------------------------------
    // 删除安装包（物理删除 + 清单重发布）
    // ------------------------------------------------------------------

    fun deletePackage(
        baseHost: String,
        token: String,
        targetAppId: String,
        snapshot: Snapshot,
        pkg: JSONObject,
        index: Int,
        cb: (Result<Snapshot>) -> Unit
    ) {
        if (token.isBlank()) {
            main.post { cb(fail("未登录或登录已过期，请先登录")) }
            return
        }

        Thread {
            try {
                // 1. 安全解析并对网盘路径逐段编码，彻底避免非法中文字符异常
                val downloadUrl = pkg.optString("downloadUrl", "")
                var subPath = downloadUrl.trim()
                if (subPath.startsWith("http://") || subPath.startsWith("https://")) {
                    try {
                        subPath = subPath.toUri().path ?: subPath
                    } catch (_: Exception) {}
                }
                if (subPath.startsWith("/raw/")) {
                    subPath = subPath.removePrefix("/raw/")
                }
                subPath = subPath.trimStart('/')

                // 2. DELETE 物理删除网盘文件（即使文件不存在也继续清理清单）
                if (subPath.isNotBlank()) {
                    val encodedSubPath = subPath.split("/").joinToString("/") { segment ->
                        try {
                            val decoded = java.net.URLDecoder.decode(segment, "UTF-8")
                            java.net.URLEncoder.encode(decoded, "UTF-8").replace("+", "%20")
                        } catch (_: Exception) {
                            segment
                        }
                    }
                    try {
                        val delReq = Request.Builder()
                            .url("$baseHost/api/write/items/$encodedSubPath")
                            .addHeader("Authorization", "Bearer $token")
                            .delete()
                            .build()
                        client.newCall(delReq).execute().close()
                    } catch (_: Exception) {}
                }

                // 3. 从 packages 清单中精准移除目标项（按索引绝对优先排除，双重保险排除）
                val oldPackages = snapshot.appJson.optJSONArray("packages") ?: JSONArray()
                val updatedPackages = JSONArray()
                val targetUrl = pkg.optString("downloadUrl")
                val targetId = pkg.optString("packageId")
                for (i in 0 until oldPackages.length()) {
                    if (i == index) continue
                    val p = oldPackages.getJSONObject(i)
                    if (targetId.isNotBlank() && p.optString("packageId") == targetId &&
                        p.optString("downloadUrl") == targetUrl
                    ) continue
                    updatedPackages.put(p)
                }

                // 4. 重发布清单
                val publishResp = client.newCall(
                    publishRequest(baseHost, token, snapshot.appJson, snapshot.matchedAppId, targetAppId, updatedPackages)
                ).execute()
                val ok = publishResp.isSuccessful
                val code = publishResp.code
                val respBody = publishResp.body?.string() ?: ""
                publishResp.close()

                if (!ok) {
                    main.post { cb(fail(buildPublishError("服务端更新清单失败", code, respBody))) }
                    return@Thread
                }

                val newAppJson = JSONObject(snapshot.appJson.toString()).put("packages", updatedPackages)
                main.post {
                    cb(Result.success(Snapshot(snapshot.matchedAppId, newAppJson, updatedPackages.toObjList())))
                }
            } catch (e: Exception) {
                main.post { cb(fail("执行异常: ${e.message}")) }
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // 文件选取 → 缓存拷贝 + 元信息解析
    // ------------------------------------------------------------------

    fun pickApk(context: Context, uri: Uri, cb: (Result<PickedApk>) -> Unit) {
        Thread {
            try {
                var fileName = "upload.apk"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIdx)
                    }
                }

                val tempDir = File(context.cacheDir, "admin_upload")
                if (!tempDir.exists()) tempDir.mkdirs()
                val targetFile = File(tempDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    main.post { cb(fail("解析本地文件失败: 文件为空")) }
                    return@Thread
                }

                var appLabel = ""
                try {
                    val pInfo = context.packageManager.getPackageArchiveInfo(targetFile.absolutePath, 0)
                    if (pInfo != null) {
                        appLabel = pInfo.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: ""
                    }
                } catch (_: Throwable) {}

                main.post {
                    cb(
                        Result.success(
                            PickedApk(
                                file = targetFile,
                                fileName = fileName,
                                sizeBytes = targetFile.length(),
                                md5 = calculateMD5(targetFile),
                                appLabel = appLabel
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                main.post { cb(fail("解析本地文件失败: ${e.message}")) }
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // 分片上传 + 挂钩发布
    // ------------------------------------------------------------------

    fun uploadAndPublish(
        baseHost: String,
        token: String,
        targetAppId: String,
        snapshot: Snapshot,
        picked: PickedApk,
        pkgTitle: String,
        pkgDesc: String,
        onProgress: (percent: Int, message: String) -> Unit,
        cb: (Result<Snapshot>) -> Unit
    ) {
        if (token.isBlank()) {
            main.post { cb(fail("管理员登录已过期，请重新登录")) }
            return
        }

        Thread {
            val file = picked.file
            val uploadPath = "update/apk/$targetAppId/${file.name}"
            val downloadRelativeUrl = "/raw/update/apk/$targetAppId/${file.name}"
            val totalBytes = file.length()

            try {
                // 1. 初始化分片上传: POST /api/write/items/$uploadPath?uploads
                val initReq = Request.Builder()
                    .url("$baseHost/api/write/items/$uploadPath?uploads")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/vnd.android.package-archive")
                    .post("".toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val initResp = client.newCall(initReq).execute()
                val initBody = initResp.body?.string()
                val initCode = initResp.code
                val isInitOk = initResp.isSuccessful
                initResp.close()

                if (!isInitOk || initBody.isNullOrBlank()) {
                    main.post { cb(fail("初始化分片上传失败: HTTP $initCode")) }
                    return@Thread
                }
                val uploadId = JSONObject(initBody).optString("uploadId")
                if (uploadId.isEmpty()) {
                    main.post { cb(fail("服务端未返回 uploadId")) }
                    return@Thread
                }

                // 2. 分片上传 (8MB 一片)
                val totalParts = ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                val uploadedPartsArray = JSONArray()
                val raf = RandomAccessFile(file, "r")
                try {
                    var uploadedBytes = 0L
                    for (partNumber in 1..totalParts) {
                        val offset = (partNumber - 1) * CHUNK_SIZE
                        val thisPartSize = minOf(CHUNK_SIZE, totalBytes - offset).toInt()
                        val buffer = ByteArray(thisPartSize)
                        raf.seek(offset)
                        raf.readFully(buffer)

                        val percent = if (totalBytes > 0) {
                            ((uploadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
                        } else 0
                        main.post { onProgress(percent, "正在分片直传 ($partNumber/$totalParts)...") }

                        val partReq = Request.Builder()
                            .url("$baseHost/api/write/items/$uploadPath?partNumber=$partNumber&uploadId=$uploadId")
                            .addHeader("Authorization", "Bearer $token")
                            .put(buffer.toRequestBody("application/vnd.android.package-archive".toMediaTypeOrNull()))
                            .build()

                        val partResp = client.newCall(partReq).execute()
                        val code = partResp.code
                        val isSuccess = partResp.isSuccessful
                        val etag = partResp.header("etag") ?: partResp.header("ETag") ?: ""
                        partResp.close()

                        if (!isSuccess) {
                            main.post { cb(fail("分片 $partNumber 传输失败: HTTP $code")) }
                            return@Thread
                        }
                        uploadedPartsArray.put(JSONObject().apply {
                            put("partNumber", partNumber)
                            put("etag", etag)
                        })
                        uploadedBytes += thisPartSize
                    }
                } finally {
                    raf.close()
                }

                // 3. 服务端合并
                main.post { onProgress(100, "所有分片已就绪，正在服务端合并...") }
                val completePayload = JSONObject().apply { put("parts", uploadedPartsArray) }
                val completeResp = client.newCall(
                    Request.Builder()
                        .url("$baseHost/api/write/items/$uploadPath?uploadId=$uploadId")
                        .addHeader("Authorization", "Bearer $token")
                        .post(completePayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                ).execute()
                val completeSuccess = completeResp.isSuccessful
                val completeCode = completeResp.code
                completeResp.close()
                if (!completeSuccess) {
                    main.post { cb(fail("分片合并失败: HTTP $completeCode")) }
                    return@Thread
                }

                // 4. 挂钩至清单并发布（同 downloadUrl 的旧项自动替换）
                main.post { onProgress(100, "正在自动挂钩至模块清单...") }
                val existingPackages = snapshot.appJson.optJSONArray("packages") ?: JSONArray()
                val newPackage = JSONObject().apply {
                    put("packageId", "pkg_${System.currentTimeMillis()}")
                    put("packageName", pkgTitle)
                    put("subDir", "")
                    put("downloadUrl", downloadRelativeUrl)
                    put("apkSize", file.length())
                    put("apkMd5", calculateMD5(file))
                    put("description", pkgDesc)
                }
                val updatedPackages = JSONArray()
                for (i in 0 until existingPackages.length()) {
                    val p = existingPackages.getJSONObject(i)
                    if (p.optString("downloadUrl") != downloadRelativeUrl) {
                        updatedPackages.put(p)
                    }
                }
                updatedPackages.put(newPackage)

                val publishResp = client.newCall(
                    publishRequest(baseHost, token, snapshot.appJson, snapshot.matchedAppId, targetAppId, updatedPackages)
                ).execute()
                val publishSuccess = publishResp.isSuccessful
                val publishCode = publishResp.code
                val respBodyStr = publishResp.body?.string() ?: ""
                publishResp.close()

                if (!publishSuccess) {
                    main.post { cb(fail(buildPublishError("服务端返回错误", publishCode, respBodyStr))) }
                    return@Thread
                }

                val newAppJson = JSONObject(snapshot.appJson.toString()).put("packages", updatedPackages)
                main.post {
                    cb(Result.success(Snapshot(snapshot.matchedAppId, newAppJson, updatedPackages.toObjList())))
                }
            } catch (e: Exception) {
                main.post { cb(fail("上传异常: ${e.message}")) }
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private fun buildPublishError(prefix: String, code: Int, respBody: String): String {
        var detail = "$prefix: HTTP $code"
        try {
            val errJson = JSONObject(respBody)
            val serverMsg = errJson.optString("error").ifBlank { errJson.optString("message") }
            if (serverMsg.isNotBlank()) detail += " ($serverMsg)"
        } catch (_: Exception) {}
        if (code == 404) detail += " - 管理员权限不足或登录凭据已失效，请尝试退出重新登录"
        return detail
    }

    /** 构建包含所有服务端必填字段的 publish 请求，杜绝 400 校验错误 */
    private fun publishRequest(
        baseHost: String,
        token: String,
        appJson: JSONObject,
        matchedAppId: String,
        targetAppId: String,
        packages: JSONArray
    ): Request {
        val actualId = matchedAppId.ifBlank { targetAppId }
        val appName = appJson.optString("appName").ifBlank { "芝麻-TK" }
        val rawCode = appJson.optInt("latestVersionCode", 0)
        val latestVersionCode = if (rawCode > 0) rawCode else 35
        val latestVersionName = appJson.optString("latestVersionName").ifBlank { "0.5.0" }
        val payload = JSONObject().apply {
            put("appId", actualId)
            put("appName", appName)
            put("latestVersionCode", latestVersionCode)
            put("latestVersionName", latestVersionName)
            put("updateLog", appJson.optString("updateLog", ""))
            put("isForceUpdate", appJson.optBoolean("isForceUpdate", false))
            put("apkUploadDir", appJson.optString("apkUploadDir", "update/apk/$actualId"))
            put("packages", packages)
        }
        return Request.Builder()
            .url("$baseHost/api/admin/update/publish")
            .addHeader("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
    }

    private fun calculateMD5(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun JSONArray.toObjList(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}
