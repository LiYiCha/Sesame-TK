package com.updater.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
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
import android.widget.*
import com.updater.Updater
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdatePackage
import com.updater.model.UpdateSourceType
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * 客户端内置管理员登录与附加包一键上传挂钩对话框
 * - 支持管理员账号密码登录与持久化
 * - 支持选取本地 APK 自动提取大小与 MD5
 * - 无需填写复杂版本号，一键直传 R2 并自动作为附加包挂钩到当前模块
 */
object AdminUploadDialog {

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    fun show(activity: Activity, appId: String, onUploadSuccess: (() -> Unit)? = null) {
        val configManager = UpdaterConfigManager(activity)
        val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val brandColor = if (isNight) Color.parseColor("#4CAF50") else Color.parseColor("#2D5A27")
        val colorTextPrimary = if (isNight) Color.parseColor("#FFFFFF") else Color.parseColor("#212529")
        val colorTextSecondary = if (isNight) Color.parseColor("#AAAAAA") else Color.parseColor("#6C757D")
        val colorBorder = if (isNight) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
        val colorCardBg = if (isNight) Color.parseColor("#262626") else Color.parseColor("#F8F9FA")

        val cfSource = configManager.getSources().find { it.type == UpdateSourceType.CLOUDFLARE_R2 }
            ?: configManager.getSelectedSource()

        if (cfSource == null || cfSource.url.isBlank()) {
            Toast.makeText(activity, "请先在更新源设置中配置 Cloudflare 更新源地址", Toast.LENGTH_LONG).show()
            return
        }

        val baseHost = cfSource.url.trimEnd('/')

        var dialog: AlertDialog? = null
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 16))
        }

        fun dp(v: Int) = dp(activity, v)

        var renderLoginView: (() -> Unit)? = null
        var renderUploadView: (() -> Unit)? = null

        renderLoginView = {
            container.removeAllViews()

            val txtTitle = TextView(activity).apply {
                text = "管理员凭证验证"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextPrimary)
                setPadding(0, 0, 0, dp(6))
            }
            container.addView(txtTitle)

            val txtSub = TextView(activity).apply {
                text = "请输入 Cloudflare 网盘后台管理员账号与密码，验证成功后可直接在手机上上传附加包。"
                textSize = 12f
                setTextColor(colorTextSecondary)
                setPadding(0, 0, 0, dp(14))
            }
            container.addView(txtSub)

            // 账号输入框
            val etUsername = EditText(activity).apply {
                hint = "管理员账号 (默认 admin)"
                setText(configManager.adminUsername.ifEmpty { "admin" })
                textSize = 13f
                setTextColor(colorTextPrimary)
                setHintTextColor(colorTextSecondary)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val bg = GradientDrawable().apply {
                    setColor(colorCardBg)
                    setStroke(dp(1), colorBorder)
                    cornerRadius = dp(6).toFloat()
                }
                background = bg
            }
            container.addView(etUsername)

            val spacer1 = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(1, dp(10))
            }
            container.addView(spacer1)

            // 密码输入框
            val etPassword = EditText(activity).apply {
                hint = "管理员密码"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                textSize = 13f
                setTextColor(colorTextPrimary)
                setHintTextColor(colorTextSecondary)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val bg = GradientDrawable().apply {
                    setColor(colorCardBg)
                    setStroke(dp(1), colorBorder)
                    cornerRadius = dp(6).toFloat()
                }
                background = bg
            }
            container.addView(etPassword)

            val spacer2 = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(1, dp(16))
            }
            container.addView(spacer2)

            // 登录按钮
            val btnLogin = Button(activity).apply {
                text = "验证并登录"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                val bg = GradientDrawable().apply {
                    setColor(brandColor)
                    cornerRadius = dp(20).toFloat()
                }
                background = bg
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    val user = etUsername.text.toString().trim()
                    val pass = etPassword.text.toString().trim()
                    if (user.isEmpty() || pass.isEmpty()) {
                        Toast.makeText(activity, "请输入管理员账号和密码", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    isEnabled = false
                    text = "正在验证..."

                    val jsonBody = JSONObject().apply {
                        put("username", user)
                        put("password", pass)
                    }

                    val req = Request.Builder()
                        .url("$baseHost/api/login")
                        .post(RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString()))
                        .build()

                    client.newCall(req).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            handler.post {
                                isEnabled = true
                                text = "验证并登录"
                                Toast.makeText(activity, "网络连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val body = response.body?.string()
                            handler.post {
                                isEnabled = true
                                text = "验证并登录"
                                if (response.isSuccessful && body != null) {
                                    try {
                                        val resJson = JSONObject(body)
                                        val token = resJson.optString("token")
                                        if (token.isNotEmpty()) {
                                            configManager.adminToken = token
                                            configManager.adminUsername = user
                                            Toast.makeText(activity, "管理员验证成功！", Toast.LENGTH_SHORT).show()
                                            renderUploadView?.invoke()
                                            return@post
                                        }
                                    } catch (_: Exception) {}
                                }
                                Toast.makeText(activity, "登录失败：账号或密码错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }
            }
            container.addView(btnLogin)
        }

        renderUploadView = {
            container.removeAllViews()

            // 顶部状态栏
            val topBar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(12))
            }

            val txtAdminTag = TextView(activity).apply {
                text = "已认证: ${configManager.adminUsername}"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(brandColor)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                layoutParams = lp
            }
            topBar.addView(txtAdminTag)

            val btnLogout = TextView(activity).apply {
                text = "退出登录"
                textSize = 12f
                setTextColor(Color.parseColor("#EF4444"))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    configManager.logoutAdmin()
                    renderLoginView?.invoke()
                }
            }
            topBar.addView(btnLogout)
            container.addView(topBar)

            val txtTitle = TextView(activity).apply {
                text = "一键上传附加包到当前模块"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextPrimary)
                setPadding(0, 0, 0, dp(4))
            }
            container.addView(txtTitle)

            val txtDesc = TextView(activity).apply {
                text = "挂钩模块: $appId\n上传后将作为配套安装包（如 LSPatch 整合版、组件包）挂钩到当前已发布版本中。"
                textSize = 12f
                setTextColor(colorTextSecondary)
                setLineSpacing(dp(2).toFloat(), 1.0f)
                setPadding(0, 0, 0, dp(12))
            }
            container.addView(txtDesc)

            // 本地 APK 文件选择展示卡片
            var selectedFile: File? = null
            var selectedFileName = ""
            var selectedFileSize = 0L
            var selectedFileMd5 = ""

            val cardFile = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(colorCardBg)
                    setStroke(dp(1), colorBorder)
                    cornerRadius = dp(8).toFloat()
                }
                background = bg
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }

            val txtFileInfo = TextView(activity).apply {
                text = "暂未选取本地 APK 安装包"
                textSize = 12f
                setTextColor(colorTextSecondary)
            }
            cardFile.addView(txtFileInfo)

            // 选取本地路径输入框（支持直接粘贴路径或选择文件）
            val etPath = EditText(activity).apply {
                hint = "输入或粘贴手机内 APK 绝对路径"
                textSize = 12f
                setTextColor(colorTextPrimary)
                setHintTextColor(colorTextSecondary)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                val bg = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), colorBorder)
                    cornerRadius = dp(4).toFloat()
                }
                background = bg
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
                layoutParams = lp
            }
            cardFile.addView(etPath)

            // 附加包名称输入框
            val etPkgName = EditText(activity).apply {
                hint = "附加包显示名称 (例如: LSPatch 便携整合版)"
                textSize = 13f
                setTextColor(colorTextPrimary)
                setHintTextColor(colorTextSecondary)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                val bg = GradientDrawable().apply {
                    setColor(colorCardBg)
                    setStroke(dp(1), colorBorder)
                    cornerRadius = dp(6).toFloat()
                }
                background = bg
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(12)
                }
                layoutParams = lp
            }

            // 附加包描述输入框
            val etPkgDesc = EditText(activity).apply {
                hint = "附加包说明 (选填，如: 适合免 Root 运行)"
                textSize = 13f
                setTextColor(colorTextPrimary)
                setHintTextColor(colorTextSecondary)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                val bg = GradientDrawable().apply {
                    setColor(colorCardBg)
                    setStroke(dp(1), colorBorder)
                    cornerRadius = dp(6).toFloat()
                }
                background = bg
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
                layoutParams = lp
            }

            fun analyzeFile(f: File) {
                if (!f.exists() || f.length() == 0L) {
                    Toast.makeText(activity, "所选文件不存在或为空", Toast.LENGTH_SHORT).show()
                    return
                }
                selectedFile = f
                selectedFileName = f.name
                selectedFileSize = f.length()
                etPath.setText(f.absolutePath)
                if (etPkgName.text.isBlank()) {
                    val raw = f.nameWithoutExtension
                    etPkgName.setText(raw)
                }

                txtFileInfo.text = "已选: ${f.name}\n大小: ${formatSize(selectedFileSize)}\n正在快速校验哈希..."
                Thread {
                    val md5 = calculateMD5(f)
                    selectedFileMd5 = md5
                    handler.post {
                        txtFileInfo.text = "已选: ${f.name}\n大小: ${formatSize(selectedFileSize)} | MD5: ${md5.take(10)}..."
                    }
                }.start()
            }

            etPath.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val p = etPath.text.toString().trim()
                    if (p.isNotEmpty()) {
                        analyzeFile(File(p))
                    }
                }
            }

            // 选取按钮
            val btnBrowse = Button(activity).apply {
                text = "📱 扫描手机存储选取 APK"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(brandColor)
                val bg = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), brandColor)
                    cornerRadius = dp(6).toFloat()
                }
                background = bg
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
                    topMargin = dp(8)
                }
                layoutParams = lp
                setOnClickListener {
                    // 快速扫描常用下载目录与已下载包
                    val candidates = findCandidateApks(activity)
                    if (candidates.isNotEmpty()) {
                        val items = candidates.map { "${it.name} (${formatSize(it.length())})" }.toTypedArray()
                        AlertDialog.Builder(activity)
                            .setTitle("选择本地 APK 安装包")
                            .setItems(items) { _, which ->
                                analyzeFile(candidates[which])
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    } else {
                        Toast.makeText(activity, "可在上方输入框直接粘贴手机内 APK 绝对路径", Toast.LENGTH_LONG).show()
                    }
                }
            }
            cardFile.addView(btnBrowse)
            container.addView(cardFile)

            container.addView(etPkgName)
            container.addView(etPkgDesc)

            // 上传发布按钮
            val btnUpload = Button(activity).apply {
                text = "🚀 一键上传并发布到当前模块"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                val bg = GradientDrawable().apply {
                    setColor(brandColor)
                    cornerRadius = dp(20).toFloat()
                }
                background = bg
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
                    topMargin = dp(16)
                }
                layoutParams = lp
                setOnClickListener {
                    val file = selectedFile
                    if (file == null || !file.exists()) {
                        val manualPath = etPath.text.toString().trim()
                        if (manualPath.isNotEmpty() && File(manualPath).exists()) {
                            analyzeFile(File(manualPath))
                        } else {
                            Toast.makeText(activity, "请先选择或输入有效的 APK 文件路径", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                    }

                    val finalFile = selectedFile ?: return@setOnClickListener
                    val pkgTitle = etPkgName.text.toString().trim().ifEmpty { finalFile.nameWithoutExtension }
                    val pkgDesc = etPkgDesc.text.toString().trim()

                    val progressDialog = ProgressDialog(activity).apply {
                        setMessage("正在上传附加包至 Cloudflare R2...")
                        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                        max = 100
                        setCancelable(false)
                        show()
                    }

                    // 开始执行上传与挂钩发布流程
                    executeUploadAndPublish(
                        activity = activity,
                        baseHost = baseHost,
                        token = configManager.adminToken,
                        appId = appId,
                        file = finalFile,
                        pkgTitle = pkgTitle,
                        pkgDesc = pkgDesc,
                        progressDialog = progressDialog,
                        onSuccess = {
                            progressDialog.dismiss()
                            dialog?.dismiss()
                            Toast.makeText(activity, "🎉 附加包上传并挂钩成功！全网立即生效。", Toast.LENGTH_LONG).show()
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
        }

        if (configManager.isAdminLoggedIn) {
            renderUploadView?.invoke()
        } else {
            renderLoginView?.invoke()
        }

        dialog = AlertDialog.Builder(activity)
            .setView(container)
            .setNegativeButton("关闭", null)
            .create()

        dialog.show()
    }

    /**
     * 执行流式上传并在成功后自动追加包到现有发布清单中
     */
    private fun executeUploadAndPublish(
        activity: Activity,
        baseHost: String,
        token: String,
        appId: String,
        file: File,
        pkgTitle: String,
        pkgDesc: String,
        progressDialog: ProgressDialog,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val fileName = file.name
        val uploadUrl = "$baseHost/api/write/items/update/apk/$appId/$fileName"
        val downloadRelativeUrl = "/raw/update/apk/$appId/$fileName"

        // 1. 上传 APK 文件 (PUT 请求带 Authorization)
        val requestBody = file.asRequestBody("application/vnd.android.package-archive".toMediaTypeOrNull())
        val putReq = Request.Builder()
            .url(uploadUrl)
            .addHeader("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        client.newCall(putReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { onError("文件直传失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    handler.post { onError("文件直传服务端返回 HTTP ${response.code}") }
                    return
                }

                handler.post {
                    progressDialog.setMessage("文件上传完成，正在自动挂钩至模块清单...")
                }

                // 2. 拉取当前 App 的发布配置
                val checkUrl = "$baseHost/api/update?app_id=$appId"
                val getReq = Request.Builder().url(checkUrl).get().build()
                client.newCall(getReq).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        handler.post { onError("拉取原版本信息失败: ${e.message}") }
                    }

                    override fun onResponse(call: Call, getResp: Response) {
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

                        // 生成新附加包对象并追加
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
                            // 若同名文件则替换，否则保留
                            if (p.optString("downloadUrl") != downloadRelativeUrl) {
                                updatedPackages.put(p)
                            }
                        }
                        updatedPackages.put(newPackage)

                        // 3. 提交至 /api/admin/update/publish 保存挂钩
                        val publishPayload = JSONObject().apply {
                            put("appId", appId)
                            put("appName", appName)
                            put("latestVersionCode", latestVersionCode)
                            put("latestVersionName", latestVersionName)
                            put("updateLog", updateLog)
                            put("isForceUpdate", isForceUpdate)
                            put("packages", updatedPackages)
                        }

                        val postReq = Request.Builder()
                            .url("$baseHost/api/admin/update/publish")
                            .addHeader("Authorization", "Bearer $token")
                            .post(RequestBody.create("application/json".toMediaTypeOrNull(), publishPayload.toString()))
                            .build()

                        client.newCall(postReq).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                handler.post { onError("挂钩发布请求失败: ${e.message}") }
                            }

                            override fun onResponse(call: Call, postResp: Response) {
                                val postBody = postResp.body?.string()
                                handler.post {
                                    if (postResp.isSuccessful) {
                                        onSuccess()
                                    } else {
                                        onError("挂钩发布失败 HTTP ${postResp.code}: $postBody")
                                    }
                                }
                            }
                        })
                    }
                })
            }
        })
    }

    private fun findCandidateApks(context: Context): List<File> {
        val candidates = mutableListOf<File>()
        try {
            // 1. 内部更新下载目录
            val appDownloadDir = context.getExternalFilesDir("downloads")
            if (appDownloadDir != null && appDownloadDir.exists()) {
                candidates.addAll(appDownloadDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", true) } ?: emptyArray())
            }

            // 2. 公共 Download 目录
            val publicDownload = File("/storage/emulated/0/Download")
            if (publicDownload.exists() && publicDownload.isDirectory) {
                val list = publicDownload.listFiles { f -> f.isFile && f.name.endsWith(".apk", true) } ?: emptyArray()
                candidates.addAll(list.sortedByDescending { it.lastModified() }.take(15))
            }
        } catch (_: Exception) {}
        return candidates.distinctBy { it.absolutePath }
    }

    private fun calculateMD5(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            val digest = md.digest()
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var s = size.toDouble()
        var i = 0
        while (s >= 1024 && i < units.size - 1) {
            s /= 1024
            i++
        }
        return "%.1f %s".format(s, units[i])
    }

    private fun dp(context: Context, v: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
    }
}
