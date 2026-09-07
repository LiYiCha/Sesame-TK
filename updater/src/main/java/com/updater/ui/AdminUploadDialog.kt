package com.updater.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdateSourceType
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 客户端内置管理员登录与附加包分片上传挂钩对话框 (遵循 Material 3 规范)
 * - 调起系统 SAF 文件选择器选取本地 APK (免权限，拒绝手动粘贴路径)
 * - 采用 R2 分片上传 (Multipart Upload 8MB 分片)，彻底解决 413 Payload Too Large 限制
 * - 上传完成后自动挂钩到当前模块发布清单
 */
object AdminUploadDialog {

    const val REQUEST_CODE_PICK_APK = 8901
    private const val CHUNK_SIZE = 8 * 1024 * 1024L // 8MB 分片

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val handler = Handler(Looper.getMainLooper())

    private var currentDialog: AlertDialog? = null
    private var onFilePickedListener: ((File) -> Unit)? = null

    fun show(activity: Activity, appId: String, onUploadSuccess: (() -> Unit)? = null) {
        val configManager = UpdaterConfigManager(activity)
        val palette = ThemeUtils.M3Palette(activity)
        val colorSurface = palette.surface
        val colorSurfaceVariant = palette.surfaceVariant
        val colorPrimary = palette.primary
        val colorOnPrimary = palette.onPrimary
        val colorOnSurface = palette.onSurface
        val colorOnSurfaceVariant = palette.onSurfaceVariant
        val colorOutline = palette.outline

        val cfSource = configManager.getSources().find { it.type == UpdateSourceType.CLOUDFLARE_R2 }
            ?: configManager.getSelectedSource()

        if (cfSource == null || cfSource.url.isBlank()) {
            Toast.makeText(activity, "请先在更新源设置中配置 Cloudflare 更新源", Toast.LENGTH_SHORT).show()
            return
        }

        val baseHost = cfSource.url.trimEnd('/')

        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 18))
        }
        scrollView.addView(container)

        fun dp(v: Int) = dp(activity, v)

        fun createM3Input(hintText: String, isPassword: Boolean = false): EditText {
            val normalBg = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), colorOutline)
                setColor(palette.surfaceVariant)
            }
            val focusedBg = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1.5f.toInt().coerceAtLeast(1)), colorPrimary)
                setColor(palette.surface)
            }
            return EditText(activity).apply {
                hint = hintText
                if (isPassword) {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                textSize = 14f
                setTextColor(colorOnSurface)
                setHintTextColor(colorOnSurfaceVariant)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = normalBg
                setOnFocusChangeListener { _, hasFocus ->
                    background = if (hasFocus) focusedBg else normalBg
                }
            }
        }

        var renderLoginView: (() -> Unit)? = null
        var renderUploadView: (() -> Unit)? = null

        // 1. 管理员登录视图
        renderLoginView = {
            container.removeAllViews()

            val txtTitle = TextView(activity).apply {
                text = "管理员登录"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorOnSurface)
                setPadding(0, 0, 0, dp(14))
            }
            container.addView(txtTitle)

            val etUsername = createM3Input("账号").apply {
                setText(configManager.adminUsername.ifEmpty { "admin" })
            }
            container.addView(etUsername)

            val spacer1 = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(10)) }
            container.addView(spacer1)

            val etPassword = createM3Input("密码", isPassword = true)
            container.addView(etPassword)

            val spacer2 = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(18)) }
            container.addView(spacer2)

            val btnLogin = MaterialButton(activity).apply {
                text = "登录"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                cornerRadius = dp(10)
                setBackgroundColor(colorPrimary)
                setTextColor(colorOnPrimary)
                insetTop = 0
                insetBottom = 0
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
                setOnClickListener {
                    val user = etUsername.text.toString().trim()
                    val pass = etPassword.text.toString().trim()
                    if (user.isEmpty() || pass.isEmpty()) {
                        Toast.makeText(activity, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    isEnabled = false
                    text = "正在登录..."

                    val jsonBody = JSONObject().apply {
                        put("username", user)
                        put("password", pass)
                    }

                    val req = Request.Builder()
                        .url("$baseHost/api/login")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()

                    client.newCall(req).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            handler.post {
                                isEnabled = true
                                text = "登录"
                                Toast.makeText(activity, "网络连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val body = response.body?.string()
                            handler.post {
                                isEnabled = true
                                text = "登录"
                                if (response.isSuccessful && body != null) {
                                    try {
                                        val resJson = JSONObject(body)
                                        val token = resJson.optString("token")
                                        if (token.isNotEmpty()) {
                                            configManager.adminToken = token
                                            configManager.adminUsername = user
                                            Toast.makeText(activity, "登录成功", Toast.LENGTH_SHORT).show()
                                            renderUploadView?.invoke()
                                            return@post
                                        }
                                    } catch (_: Exception) {}
                                }
                                Toast.makeText(activity, "账号或密码错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }
            }
            container.addView(btnLogin)

            val btnClose = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "关闭"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                cornerRadius = dp(10)
                setTextColor(colorOnSurface)
                strokeColor = ColorStateList.valueOf(colorOutline)
                insetTop = 0
                insetBottom = 0
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    topMargin = dp(10)
                }
                layoutParams = lp
                setOnClickListener { currentDialog?.dismiss() }
            }
            container.addView(btnClose)
        }

        // 2. 上传管理视图
        renderUploadView = {
            container.removeAllViews()

            // 顶部状态条
            val topBar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(12))
            }

            val txtAdminTag = TextView(activity).apply {
                text = configManager.adminUsername
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorPrimary)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            topBar.addView(txtAdminTag)

            val btnLogout = TextView(activity).apply {
                text = "退出"
                textSize = 13f
                setTextColor(palette.error)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    configManager.logoutAdmin()
                    renderLoginView?.invoke()
                }
            }
            topBar.addView(btnLogout)
            container.addView(topBar)

            val txtTitle = TextView(activity).apply {
                text = "上传安装包"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorOnSurface)
                setPadding(0, 0, 0, dp(4))
            }
            container.addView(txtTitle)

            val txtDesc = TextView(activity).apply {
                text = "关联模块: $appId"
                textSize = 13f
                setTextColor(colorOnSurfaceVariant)
                setPadding(0, 0, 0, dp(12))
            }
            container.addView(txtDesc)

            // 选中的文件详情展示卡片 (MaterialCardView)
            var selectedFile: File? = null

            val cardFile = MaterialCardView(activity).apply {
                radius = dp(12).toFloat()
                strokeWidth = dp(1)
                strokeColor = colorOutline
                setCardBackgroundColor(colorSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val cardContent = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }

            val txtFileInfo = TextView(activity).apply {
                text = "未选择本地 APK 文件"
                textSize = 13f
                setTextColor(colorOnSurfaceVariant)
            }
            cardContent.addView(txtFileInfo)

            // 选择本地 APK 按钮
            val btnPickFile = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "选择本地 APK"
                textSize = 13f
                cornerRadius = dp(8)
                setTextColor(colorPrimary)
                strokeColor = android.content.res.ColorStateList.valueOf(colorPrimary)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
                    topMargin = dp(10)
                }
                layoutParams = lp
                setOnClickListener {
                    requestPickApk(activity)
                }
            }
            cardContent.addView(btnPickFile)
            cardFile.addView(cardContent)
            container.addView(cardFile)

            // 附加包显示名称输入框
            val etPkgName = createM3Input("包名称").apply {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(14)
                }
                layoutParams = lp
            }
            container.addView(etPkgName)

            // 附加包描述输入框
            val etPkgDesc = createM3Input("说明 (选填)").apply {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
                layoutParams = lp
            }
            container.addView(etPkgDesc)

            // 设置选中文件回调处理器
            onFilePickedListener = { file ->
                selectedFile = file
                val sizeStr = formatSize(file.length())
                txtFileInfo.text = "已选: ${file.name}\n大小: $sizeStr\n正在校验哈希..."
                btnPickFile.text = "更换 APK 文件"

                // 自动填充包名建议
                try {
                    val pInfo = activity.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    if (pInfo != null && etPkgName.text.isBlank()) {
                        val appLabel = pInfo.applicationInfo?.loadLabel(activity.packageManager)?.toString()
                        etPkgName.setText(appLabel?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension)
                    }
                } catch (_: Throwable) {}

                Thread {
                    val md5 = calculateMD5(file)
                    handler.post {
                        txtFileInfo.text = "已选: ${file.name}\n大小: $sizeStr | MD5: ${md5.take(10)}..."
                    }
                }.start()
            }

            // 上传发布按钮
            val btnUpload = MaterialButton(activity).apply {
                text = "上传并发布"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                cornerRadius = dp(10)
                setBackgroundColor(colorPrimary)
                setTextColor(colorOnPrimary)
                insetTop = 0
                insetBottom = 0
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
                    topMargin = dp(16)
                }
                layoutParams = lp
                setOnClickListener {
                    val file = selectedFile
                    if (file == null || !file.exists() || file.length() == 0L) {
                        Toast.makeText(activity, "请先选择有效的 APK 文件", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val pkgTitle = etPkgName.text.toString().trim().ifEmpty { file.nameWithoutExtension }
                    val pkgDesc = etPkgDesc.text.toString().trim()

                    val progressDialog = M3ProgressDialog(activity).apply {
                        setMessage("准备上传...")
                        setProgress(0)
                        show()
                    }

                    // 开始执行 R2 分片直传流程 (8MB 分片，彻底杜绝 413 错误)
                    executeMultipartUploadAndPublish(
                        activity = activity,
                        baseHost = baseHost,
                        token = configManager.adminToken,
                        appId = appId,
                        file = file,
                        pkgTitle = pkgTitle,
                        pkgDesc = pkgDesc,
                        progressDialog = progressDialog,
                        onSuccess = {
                            progressDialog.dismiss()
                            currentDialog?.dismiss()
                            Toast.makeText(activity, "上传并发布成功", Toast.LENGTH_SHORT).show()
                            onUploadSuccess?.invoke()
                        },
                        onError = { errMsg ->
                            progressDialog.dismiss()
                            Toast.makeText(activity, "上传失败: $errMsg", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            container.addView(btnUpload)

            val btnClose = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "关闭"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                cornerRadius = dp(10)
                setTextColor(colorOnSurface)
                strokeColor = ColorStateList.valueOf(colorOutline)
                insetTop = 0
                insetBottom = 0
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    topMargin = dp(8)
                }
                layoutParams = lp
                setOnClickListener { currentDialog?.dismiss() }
            }
            container.addView(btnClose)
        }

        if (configManager.isAdminLoggedIn) {
            renderUploadView?.invoke()
        } else {
            renderLoginView?.invoke()
        }

        currentDialog = MaterialAlertDialogBuilder(activity)
            .setView(scrollView)
            .create()

        currentDialog?.show()
    }

    /**
     * 调起系统原生 SAF 文件选择器（免权限，直接从系统文件管理器选取 APK）
     */
    fun requestPickApk(activity: Activity) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            activity.startActivityForResult(Intent.createChooser(intent, "选择 APK 文件"), REQUEST_CODE_PICK_APK)
        } catch (e: Exception) {
            try {
                val docIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                activity.startActivityForResult(docIntent, REQUEST_CODE_PICK_APK)
            } catch (e2: Exception) {
                Toast.makeText(activity, "调起文件选择器失败: ${e2.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 在 Activity 中收到 SAF 选择结果时调用
     */
    fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_PICK_APK && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: data.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return
            importApkFromUri(activity, uri)
        }
    }

    private fun importApkFromUri(activity: Activity, uri: Uri) {
        Thread {
            try {
                var fileName = "temp_${System.currentTimeMillis()}.apk"
                activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val n = cursor.getString(nameIndex)
                            if (!n.isNullOrBlank()) fileName = n
                        }
                    }
                }
                if (!fileName.endsWith(".apk", ignoreCase = true)) {
                    fileName += ".apk"
                }

                val cacheDir = File(activity.cacheDir, "apk_upload_temp").apply { mkdirs() }
                val destFile = File(cacheDir, fileName)

                activity.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (destFile.exists() && destFile.length() > 0) {
                    handler.post {
                        onFilePickedListener?.invoke(destFile)
                    }
                } else {
                    handler.post {
                        Toast.makeText(activity, "文件读取为空", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(activity, "导入文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * 执行 R2 分片上传流程 (8MB 一片，彻底解决 Cloudflare 413 错误)
     */
    private fun executeMultipartUploadAndPublish(
        activity: Activity,
        baseHost: String,
        token: String,
        appId: String,
        file: File,
        pkgTitle: String,
        pkgDesc: String,
        progressDialog: M3ProgressDialog,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            val fileName = file.name
            val uploadPath = "update/apk/$appId/$fileName"
            val downloadRelativeUrl = "/raw/update/apk/$appId/$fileName"
            val totalBytes = file.length()

            try {
                // 1. 发起分片上传初始化: POST /api/write/items/$uploadPath?uploads
                val initUrl = "$baseHost/api/write/items/$uploadPath?uploads"
                val initReq = Request.Builder()
                    .url(initUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/vnd.android.package-archive")
                    .post("".toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val initResp = client.newCall(initReq).execute()
                val initBody = initResp.body?.string()
                if (!initResp.isSuccessful || initBody.isNullOrBlank()) {
                    val code = initResp.code
                    val err = if (code == 401) "HTTP 401 (未授权: 需重新登录)" else "HTTP $code"
                    handler.post { onError("初始化分片上传失败: $err") }
                    return@Thread
                }

                val initJson = JSONObject(initBody)
                val uploadId = initJson.optString("uploadId")
                if (uploadId.isEmpty()) {
                    handler.post { onError("服务端未返回有效的 uploadId") }
                    return@Thread
                }

                // 2. 依次读取分片并上传 (每片 8MB，避免 413 Request Entity Too Large)
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

                        handler.post {
                            val percent = if (totalBytes > 0) ((uploadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0
                            progressDialog.setProgress(percent)
                            progressDialog.setMessage("正在分片上传 ($partNumber/$totalParts)...")
                        }

                        val partUrl = "$baseHost/api/write/items/$uploadPath?partNumber=$partNumber&uploadId=$uploadId"
                        val partReq = Request.Builder()
                            .url(partUrl)
                            .addHeader("Authorization", "Bearer $token")
                            .put(buffer.toRequestBody("application/vnd.android.package-archive".toMediaTypeOrNull()))
                            .build()

                        val partResp = client.newCall(partReq).execute()
                        val code = partResp.code
                        val isSuccess = partResp.isSuccessful
                        val etag = partResp.header("etag") ?: partResp.header("ETag") ?: ""
                        partResp.close()

                        if (!isSuccess) {
                            val msg = if (code == 413) "HTTP 413 (分片超限)" else "HTTP $code"
                            handler.post { onError("分片 $partNumber 上传失败: $msg") }
                            return@Thread
                        }

                        val partObj = JSONObject().apply {
                            put("partNumber", partNumber)
                            put("etag", etag)
                        }
                        uploadedPartsArray.put(partObj)
                        uploadedBytes += thisPartSize
                    }
                } finally {
                    raf.close()
                }

                // 3. 完成分片合并: POST /api/write/items/$uploadPath?uploadId=$uploadId
                handler.post {
                    progressDialog.setProgress(100)
                    progressDialog.setMessage("所有分片已上传，正在服务端合并...")
                }

                val completeUrl = "$baseHost/api/write/items/$uploadPath?uploadId=$uploadId"
                val completePayload = JSONObject().apply {
                    put("parts", uploadedPartsArray)
                }

                val completeReq = Request.Builder()
                    .url(completeUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(completePayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val completeResp = client.newCall(completeReq).execute()
                val completeSuccess = completeResp.isSuccessful
                val completeCode = completeResp.code
                completeResp.close()

                if (!completeSuccess) {
                    handler.post { onError("分片合并失败: HTTP $completeCode") }
                    return@Thread
                }

                handler.post {
                    progressDialog.setMessage("文件直传成功，正在自动挂钩至模块清单...")
                }

                // 4. 拉取当前 App 现有清单并追加新包，提交发布
                val checkUrl = "$baseHost/api/update?app_id=$appId"
                val getReq = Request.Builder().url(checkUrl).get().build()
                val getResp = client.newCall(getReq).execute()
                val getBody = getResp.body?.string()
                val currentApp = try {
                    JSONObject(getBody ?: "{}")
                } catch (_: Exception) {
                    JSONObject()
                }

                val appName = currentApp.optString("appName", "模块应用")
                val latestVersionCode = currentApp.optInt("latestVersionCode", 1)
                val latestVersionName = currentApp.optString("latestVersionName", "1.0.0")
                val updateLog = currentApp.optString("updateLog", "")
                val isForceUpdate = currentApp.optBoolean("isForceUpdate", false)

                val existingPackages = currentApp.optJSONArray("packages") ?: JSONArray()
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

                val publishPayload = JSONObject().apply {
                    put("appId", appId)
                    put("appName", appName)
                    put("latestVersionCode", latestVersionCode)
                    put("latestVersionName", latestVersionName)
                    put("updateLog", updateLog)
                    put("isForceUpdate", isForceUpdate)
                    put("packages", updatedPackages)
                }

                val publishUrl = "$baseHost/api/admin/update/publish"
                val publishReq = Request.Builder()
                    .url(publishUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(publishPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val publishResp = client.newCall(publishReq).execute()
                val publishSuccess = publishResp.isSuccessful
                val publishCode = publishResp.code
                publishResp.close()

                if (publishSuccess) {
                    handler.post { onSuccess() }
                } else {
                    handler.post { onError("挂钩发布失败: HTTP $publishCode") }
                }

            } catch (e: Exception) {
                handler.post { onError("上传异常: ${e.message}") }
            }
        }.start()
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

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var sizeD = size.toDouble()
        var i = 0
        while (sizeD >= 1024 && i < units.size - 1) {
            sizeD /= 1024
            i++
        }
        return String.format("%.1f %s", sizeD, units[i])
    }



    private fun dp(context: Context, v: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * 符合 Material 3 视觉规范的原生安全上传进度指示弹窗 (无任何 Theme 崩溃风险)
     */
    class M3ProgressDialog(activity: Activity) {
        private val dialog: AlertDialog
        private val txtMsg: TextView
        private val indicator: ProgressBar

        init {
            val d = { v: Int ->
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).toInt()
            }

            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(24), d(20), d(24), d(24))
            }

            val p = ThemeUtils.M3Palette(activity)
            val colorOnSurface = p.onSurface
            val colorOnSurfaceVariant = p.onSurfaceVariant
            val colorPrimary = p.primary

            val titleView = TextView(activity).apply {
                text = "上传附加包"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorOnSurface)
                setPadding(0, 0, 0, d(12))
            }
            layout.addView(titleView)

            txtMsg = TextView(activity).apply {
                text = "正在准备分片上传..."
                textSize = 14f
                setTextColor(colorOnSurfaceVariant)
                setPadding(0, 0, 0, d(16))
            }
            layout.addView(txtMsg)

            indicator = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
                isIndeterminate = false
                val progressBg = GradientDrawable().apply {
                    setColor(p.surfaceVariant)
                    cornerRadius = d(4).toFloat()
                }
                val progressFg = GradientDrawable().apply {
                    setColor(colorPrimary)
                    cornerRadius = d(4).toFloat()
                }
                val clipFg = android.graphics.drawable.ClipDrawable(progressFg, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
                val layer = android.graphics.drawable.LayerDrawable(arrayOf(progressBg, clipFg)).apply {
                    setId(0, android.R.id.background)
                    setId(1, android.R.id.progress)
                }
                progressDrawable = layer
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(6))
            }
            layout.addView(indicator)

            dialog = try {
                MaterialAlertDialogBuilder(activity)
                    .setView(layout)
                    .setCancelable(false)
                    .create()
            } catch (_: Throwable) {
                AlertDialog.Builder(activity)
                    .setView(layout)
                    .setCancelable(false)
                    .create()
            }
        }

        fun show() = dialog.show()
        fun dismiss() = dialog.dismiss()
        fun setProgress(p: Int) { indicator.progress = p }
        fun setMessage(msg: String) { txtMsg.text = msg }
    }
}
