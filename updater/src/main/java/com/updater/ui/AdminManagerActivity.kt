package com.updater.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
 * 安装包与发布管理独立页面
 * - 管理员登录鉴权
 * - 查询当前模块全部已发布的 APK 安装包列表
 * - 支持删除误上传的安装包（同时物理删除网盘文件并自动更新发布清单）
 * - 支持本地选取 APK 并通过 8MB 分片上传至 R2 网盘并自动发布挂钩
 */
class AdminManagerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_ID = "app_id"
        private const val REQUEST_CODE_PICK_APK = 8902
        private const val CHUNK_SIZE = 8 * 1024 * 1024L // 8MB 分片
    }

    private lateinit var configManager: UpdaterConfigManager
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private var targetAppId: String = ""
    private var matchedAppId: String = ""
    private var baseHost: String = ""

    // UI 主题调色板
    private lateinit var palette: ThemeUtils.M3Palette
    private var colorBg: Int = 0
    private var colorCard: Int = 0
    private var colorTextPrimary: Int = 0
    private var colorTextSecondary: Int = 0
    private var colorBrand: Int = 0
    private var colorBorder: Int = 0
    private var colorCardInner: Int = 0
    private var colorError: Int = 0

    private lateinit var rootLayout: LinearLayout
    private lateinit var statusBarSpacer: View
    private lateinit var contentContainer: LinearLayout

    // 当前选中的待上传文件
    private var selectedUploadFile: File? = null
    private var txtSelectedFileInfo: TextView? = null
    private var etUploadPkgName: EditText? = null
    private var etUploadPkgDesc: EditText? = null
    private var btnSelectFile: TextView? = null

    // 缓存当前清单原始数据
    private var currentAppJson: JSONObject = JSONObject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configManager = UpdaterConfigManager(this)
        targetAppId = intent.getStringExtra(EXTRA_APP_ID) ?: packageName
        if (targetAppId.isBlank()) targetAppId = packageName
        matchedAppId = targetAppId

        initThemeColors()
        setupBaseHost()

        rootLayout = createRootLayout()
        setContentView(rootLayout)
        setupSystemBar()

        renderCurrentState()
    }

    private fun setupBaseHost() {
        val cfSource = configManager.getSources().find { it.type == UpdateSourceType.CLOUDFLARE_R2 }
            ?: configManager.getSelectedSource()
        baseHost = (cfSource?.url ?: "").trimEnd('/')
    }

    private fun initThemeColors() {
        palette = ThemeUtils.M3Palette(this)
        colorBg = palette.background
        colorCard = palette.surface
        colorTextPrimary = palette.onSurface
        colorTextSecondary = palette.onSurfaceVariant
        colorBrand = palette.primary
        colorBorder = palette.outline
        colorCardInner = palette.surfaceVariant
        colorError = palette.error
    }

    private fun setupSystemBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isNight = palette.isNight
        insetsController.isAppearanceLightStatusBars = !isNight
        insetsController.isAppearanceLightNavigationBars = !isNight

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            statusBarSpacer.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                statusInsets.top.coerceAtLeast(1)
            )
            insets
        }
    }

    private fun createRootLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        statusBarSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(24))
            setBackgroundColor(colorCard)
        }
        root.addView(statusBarSpacer)

        // 顶部标题栏
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52)).apply {
                elevation = dpToPx(1.5f).toFloat()
            }
            setBackgroundColor(colorCard)
            setPadding(dpToPx(6), 0, dpToPx(12), 0)
        }

        // 返回按钮
        val btnBack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(dpToPx(6), 0, dpToPx(8), 0)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            layoutParams = lp

            val normalBg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.TRANSPARENT)
            }
            val pressedBg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(palette.primaryContainer)
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
                addState(intArrayOf(), normalBg)
            }
            isClickable = true
            isFocusable = true

            val ivArrow = ImageView(this@AdminManagerActivity).apply {
                val arrowSize = dpToPx(18)
                setImageDrawable(BackArrowDrawable(colorTextPrimary, dpToPx(2.2f).toFloat()))
                val ivLp = LinearLayout.LayoutParams(arrowSize, arrowSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    rightMargin = dpToPx(2)
                }
                layoutParams = ivLp
            }

            val txtLabel = TextView(this@AdminManagerActivity).apply {
                text = "返回"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextPrimary)
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
            }

            addView(ivArrow)
            addView(txtLabel)
            setOnClickListener { finish() }
        }
        titleBar.addView(btnBack)

        val txtTitle = TextView(this).apply {
            text = "安装包与发布管理"
            textSize = 16f
            setTextColor(colorTextPrimary)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                leftMargin = dpToPx(6)
                rightMargin = dpToPx(6)
                gravity = Gravity.CENTER_VERTICAL
            }
            layoutParams = lp
        }
        titleBar.addView(txtTitle)

        // 顶部操作区（刷新、退出登录按钮）
        val rightActionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnRefresh = createHeaderButton("刷新", isPrimary = true) {
            if (configManager.isAdminLoggedIn) {
                loadPublishedPackages()
            } else {
                renderCurrentState()
            }
        }
        rightActionLayout.addView(btnRefresh)

        titleBar.addView(rightActionLayout)
        root.addView(titleBar)

        // 可滚动的容器
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(28))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scrollView.addView(contentContainer)
        root.addView(scrollView)

        return root
    }

    private fun createHeaderButton(label: String, isPrimary: Boolean, onClick: () -> Unit): TextView {
        val bg = GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            if (isPrimary) {
                setColor(colorBrand)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(1), colorBorder)
            }
        }
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isPrimary) palette.onPrimary else colorTextPrimary)
            gravity = Gravity.CENTER
            background = bg
            isClickable = true
            isFocusable = true
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32))
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun renderCurrentState() {
        contentContainer.removeAllViews()

        if (baseHost.isBlank()) {
            renderMissingConfigSourceView()
            return
        }

        if (!configManager.isAdminLoggedIn) {
            renderLoginView()
        } else {
            renderDashboardView()
            loadPublishedPackages()
        }
    }

    private fun renderMissingConfigSourceView() {
        val card = createM3Card()
        val txt = TextView(this).apply {
            text = "未配置 Cloudflare 更新源，请先在更新源设置中添加有效的 Cloudflare 节点链接。"
            textSize = 14f
            setTextColor(colorError)
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }
        card.addView(txt)
        contentContainer.addView(card)
    }

    // --- 管理员登录表单 ---
    private fun renderLoginView() {
        val card = createM3Card()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(20))
        }

        val txtTitle = TextView(this).apply {
            text = "管理员登录"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
            setPadding(0, 0, 0, dpToPx(6))
        }
        layout.addView(txtTitle)

        val txtSub = TextView(this).apply {
            text = "请输入 Cloudflare 网盘后台管理员账号与密码以管理安装包。"
            textSize = 12f
            setTextColor(colorTextSecondary)
            setPadding(0, 0, 0, dpToPx(14))
        }
        layout.addView(txtSub)

        val etUser = createM3Input("账号").apply {
            setText(configManager.adminUsername.ifEmpty { "admin" })
        }
        layout.addView(etUser)

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dpToPx(10)) }
        layout.addView(spacer1)

        val etPass = createM3Input("密码", isPassword = true)
        layout.addView(etPass)

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dpToPx(16)) }
        layout.addView(spacer2)

        val btnLogin = TextView(this).apply {
            text = "登 录"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.onPrimary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(10).toFloat()
                setColor(colorBrand)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44))
            setOnClickListener {
                val u = etUser.text.toString().trim()
                val p = etPass.text.toString().trim()
                if (u.isEmpty() || p.isEmpty()) {
                    Toast.makeText(this@AdminManagerActivity, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                doLogin(u, p, this)
            }
        }
        layout.addView(btnLogin)

        card.addView(layout)
        contentContainer.addView(card)
    }

    private fun doLogin(user: String, pass: String, btn: TextView) {
        btn.isEnabled = false
        btn.text = "正在登录..."

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
                    if (isFinishing || isDestroyed) return@post
                    btn.isEnabled = true
                    btn.text = "登 录"
                    Toast.makeText(this@AdminManagerActivity, "网络连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val isSuccess = response.isSuccessful
                response.close()

                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    btn.isEnabled = true
                    btn.text = "登 录"
                    if (isSuccess && !body.isNullOrBlank()) {
                        try {
                            val resJson = JSONObject(body)
                            val token = resJson.optString("token")
                            if (token.isNotEmpty()) {
                                configManager.adminToken = token
                                configManager.adminUsername = user
                                Toast.makeText(this@AdminManagerActivity, "登录成功", Toast.LENGTH_SHORT).show()
                                renderCurrentState()
                                return@post
                            }
                        } catch (_: Exception) {}
                    }
                    Toast.makeText(this@AdminManagerActivity, "账号或密码错误", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // --- 管理员主视图：已发布包列表与上传新包 ---
    private var packageListContainer: LinearLayout? = null
    private var txtPackageCount: TextView? = null

    private fun renderDashboardView() {
        contentContainer.removeAllViews()

        // 1. 管理员状态与退出横条
        val topStatusCard = createM3Card()
        val topLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
        }

        val txtAdminInfo = TextView(this).apply {
            text = "管理员: ${configManager.adminUsername}  (关联模块: $targetAppId)"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorBrand)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topLayout.addView(txtAdminInfo)

        val btnLogout = TextView(this).apply {
            text = "退出登录"
            textSize = 12f
            setTextColor(colorError)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                configManager.logoutAdmin()
                renderCurrentState()
            }
        }
        topLayout.addView(btnLogout)
        topStatusCard.addView(topLayout)
        contentContainer.addView(topStatusCard)

        val spacer0 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dpToPx(12)) }
        contentContainer.addView(spacer0)

        // 2. 上传新安装包卡片（增）
        renderUploadCard()

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dpToPx(14)) }
        contentContainer.addView(spacer1)

        // 3. 已发布安装包列表卡片（查、删）
        val listHeaderCard = createM3Card()
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
        }

        txtPackageCount = TextView(this).apply {
            text = "已发布安装包列表"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
        }
        headerLayout.addView(txtPackageCount)

        val txtTip = TextView(this).apply {
            text = "误上传或废弃的安装包可直接点击右侧删除，将自动同步删除网盘物理 APK 并更新清单。"
            textSize = 11f
            setTextColor(colorTextSecondary)
            setPadding(0, dpToPx(2), 0, 0)
        }
        headerLayout.addView(txtTip)
        listHeaderCard.addView(headerLayout)
        contentContainer.addView(listHeaderCard)

        packageListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8)
            }
        }
        contentContainer.addView(packageListContainer)
    }

    private fun renderUploadCard() {
        val card = createM3Card()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(14))
        }

        val txtTitle = TextView(this).apply {
            text = "上传新安装包"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
        }
        layout.addView(txtTitle)

        // 选中的文件详情展示框
        val fileBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(colorCardInner)
                setStroke(dpToPx(1), colorBorder)
            }
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8)
            }
            layoutParams = lp
        }

        txtSelectedFileInfo = TextView(this).apply {
            text = "未选择本地 APK 文件"
            textSize = 12f
            setTextColor(colorTextSecondary)
        }
        fileBox.addView(txtSelectedFileInfo)

        btnSelectFile = TextView(this).apply {
            text = "选择本地 APK"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorBrand)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), colorBrand)
                setColor(Color.TRANSPARENT)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(36)).apply {
                topMargin = dpToPx(8)
            }
            layoutParams = lp
            setOnClickListener { requestPickApk() }
        }
        fileBox.addView(btnSelectFile)
        layout.addView(fileBox)

        etUploadPkgName = createM3Input("包显示名称 (如: 主程序 / 扩展包)").apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(10)
            }
            layoutParams = lp
        }
        layout.addView(etUploadPkgName)

        etUploadPkgDesc = createM3Input("更新说明 (选填)").apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(6)
            }
            layoutParams = lp
        }
        layout.addView(etUploadPkgDesc)

        val btnStartUpload = TextView(this).apply {
            text = "开始分片上传并挂钩发布"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.onPrimary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(10).toFloat()
                setColor(colorBrand)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(42)).apply {
                topMargin = dpToPx(12)
            }
            layoutParams = lp
            setOnClickListener {
                startUploadProcess()
            }
        }
        layout.addView(btnStartUpload)

        card.addView(layout)
        contentContainer.addView(card)
    }

    // --- 加载已发布安装包列表 (优先直连管理员无缓存接口) ---
    private fun loadPublishedPackages() {
        val token = configManager.adminToken
        val hasToken = token.isNotBlank()

        // 优先使用管理员接口（直连 R2，无 CDN 缓存，数据最真实最新）
        val url = if (hasToken) {
            "$baseHost/api/admin/update/publish?_t=${System.currentTimeMillis()}"
        } else {
            "$baseHost/api/update?app_id=$targetAppId&_t=${System.currentTimeMillis()}"
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            .addHeader("Pragma", "no-cache")

        if (hasToken) {
            reqBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    Toast.makeText(this@AdminManagerActivity, "获取安装包清单失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val isSuccess = response.isSuccessful
                response.close()

                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    if (isSuccess && !body.isNullOrBlank()) {
                        try {
                            val resJson = JSONObject(body)
                            var appObj: JSONObject? = null
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
                                                targetAppId.contains(k, ignoreCase = true)) {
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

                            if (appObj != null) {
                                currentAppJson = appObj
                                val packages = appObj.optJSONArray("packages") ?: JSONArray()
                                renderPackageItems(packages)
                            } else {
                                currentAppJson = JSONObject()
                                renderPackageItems(JSONArray())
                            }
                            return@post
                        } catch (e: Exception) {
                            Toast.makeText(this@AdminManagerActivity, "解析安装包清单异常: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // 若管理员接口未获取到，回退尝试公开查询接口
                    if (hasToken) {
                        fallbackLoadFromPublicApi()
                    } else {
                        currentAppJson = JSONObject()
                        renderPackageItems(JSONArray())
                    }
                }
            }
        })
    }

    private fun fallbackLoadFromPublicApi() {
        val publicUrl = "$baseHost/api/update?app_id=$targetAppId&_t=${System.currentTimeMillis()}"
        val req = Request.Builder()
            .url(publicUrl)
            .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            .get()
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    currentAppJson = JSONObject()
                    renderPackageItems(JSONArray())
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val isSuccess = response.isSuccessful
                response.close()

                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    if (isSuccess && !body.isNullOrBlank()) {
                        try {
                            val resJson = JSONObject(body)
                            currentAppJson = resJson
                            val packages = resJson.optJSONArray("packages") ?: JSONArray()
                            renderPackageItems(packages)
                            return@post
                        } catch (_: Exception) {}
                    }
                    currentAppJson = JSONObject()
                    renderPackageItems(JSONArray())
                }
            }
        })
    }

    private fun renderPackageItems(packages: JSONArray) {
        val container = packageListContainer ?: return
        container.removeAllViews()

        val count = packages.length()
        txtPackageCount?.text = "已发布安装包列表 ($count)"

        if (count == 0) {
            val emptyCard = createM3Card()
            val txtEmpty = TextView(this).apply {
                text = "当前模块暂无已发布的安装包"
                textSize = 13f
                setTextColor(colorTextSecondary)
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            }
            emptyCard.addView(txtEmpty)
            container.addView(emptyCard)
            return
        }

        for (i in 0 until count) {
            val pkg = packages.optJSONObject(i) ?: continue
            val pkgCard = createPackageItemView(pkg, i)
            container.addView(pkgCard)

            if (i < count - 1) {
                val itemSpacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(1, dpToPx(8))
                }
                container.addView(itemSpacer)
            }
        }
    }

    private fun createPackageItemView(pkg: JSONObject, index: Int): View {
        val card = createM3Card()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
        }

        val pkgName = pkg.optString("packageName", "安装包")
        val pkgDesc = pkg.optString("description", "")
        val downloadUrl = pkg.optString("downloadUrl", "")
        val sizeBytes = pkg.optLong("apkSize", 0)
        val md5 = pkg.optString("apkMd5", "")

        // 顶部信息与删除按钮
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameView = TextView(this).apply {
            text = pkgName
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                rightMargin = dpToPx(8)
            }
            layoutParams = lp
        }
        topRow.addView(nameView)

        val btnDelete = TextView(this).apply {
            text = "删除"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorError)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(1), colorError)
            }
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                confirmDeletePackage(pkg, index)
            }
        }
        topRow.addView(btnDelete)
        layout.addView(topRow)

        if (pkgDesc.isNotBlank()) {
            val descView = TextView(this).apply {
                text = pkgDesc
                textSize = 12f
                setTextColor(colorTextSecondary)
                setPadding(0, dpToPx(4), 0, 0)
            }
            layout.addView(descView)
        }

        val metaView = TextView(this).apply {
            val sizeStr = formatSize(sizeBytes)
            val md5Short = if (md5.length > 8) md5.take(8) else md5
            text = "大小: $sizeStr  |  MD5: $md5Short\n路径: $downloadUrl"
            textSize = 11f
            setTextColor(colorTextSecondary)
            setPadding(0, dpToPx(6), 0, 0)
        }
        layout.addView(metaView)

        card.addView(layout)
        return card
    }

    // --- 执行删除操作（联动物理文件与清单发布） ---
    private fun confirmDeletePackage(pkg: JSONObject, index: Int) {
        val pkgName = pkg.optString("packageName", "安装包")
        val downloadUrl = pkg.optString("downloadUrl", "")

        AlertDialog.Builder(this)
            .setTitle("确认删除安装包")
            .setMessage("确定要删除【$pkgName】吗？\n\n• 网盘存储桶中的物理 APK 文件将被同步删除\n• 更新发布清单将同步移除此项，客户端更新列表不再显示")
            .setPositiveButton("确定删除") { _, _ ->
                executeDeletePackage(pkg, downloadUrl, index)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeDeletePackage(targetPkg: JSONObject, downloadUrl: String, targetIndex: Int) {
        val token = configManager.adminToken
        if (token.isBlank()) {
            Toast.makeText(this, "未登录或登录已过期，请先登录", Toast.LENGTH_SHORT).show()
            renderCurrentState()
            return
        }

        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("正在删除")
            .setMessage("正在从网盘物理删除文件并更新发布清单...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        Thread {
            try {
                // 1. 安全解析并对网盘路径逐段编码，彻底避免非法中文字符异常
                var subPath = downloadUrl.trim()
                if (subPath.startsWith("http://") || subPath.startsWith("https://")) {
                    try {
                        val uri = Uri.parse(subPath)
                        subPath = uri.path ?: subPath
                    } catch (_: Exception) {}
                }
                if (subPath.startsWith("/raw/")) {
                    subPath = subPath.removePrefix("/raw/")
                }
                subPath = subPath.trimStart('/')

                // 2. 发送 DELETE 请求物理删除网盘文件
                if (subPath.isNotBlank()) {
                    val encodedSubPath = subPath.split("/").joinToString("/") { segment ->
                        try {
                            val decoded = java.net.URLDecoder.decode(segment, "UTF-8")
                            java.net.URLEncoder.encode(decoded, "UTF-8").replace("+", "%20")
                        } catch (_: Exception) {
                            segment
                        }
                    }
                    val deleteFileUrl = "$baseHost/api/write/items/$encodedSubPath"
                    try {
                        val delReq = Request.Builder()
                            .url(deleteFileUrl)
                            .addHeader("Authorization", "Bearer $token")
                            .delete()
                            .build()
                        val delResp = client.newCall(delReq).execute()
                        delResp.close()
                    } catch (e: Exception) {
                        // 即使物理文件不在网盘中（已删或不存在），依然继续清理清单
                    }
                }

                // 3. 核心：从 packages 清单中精准移除目标项 (按索引绝对优先排除，双重保险排除)
                val oldPackages = currentAppJson.optJSONArray("packages") ?: JSONArray()
                val updatedPackages = JSONArray()
                val targetUrl = targetPkg.optString("downloadUrl")
                val targetId = targetPkg.optString("packageId")

                for (i in 0 until oldPackages.length()) {
                    if (i == targetIndex) {
                        continue // 精准排除选中的索引项
                    }
                    val p = oldPackages.getJSONObject(i)
                    val pUrl = p.optString("downloadUrl")
                    val pId = p.optString("packageId")
                    if (targetId.isNotBlank() && pId == targetId && pUrl == targetUrl) {
                        continue
                    }
                    updatedPackages.put(p)
                }

                // 4. 构建包含所有服务端必填字段的 publishPayload，杜绝 400 校验错误
                val actualId = if (matchedAppId.isNotBlank()) matchedAppId else targetAppId
                val appName = currentAppJson.optString("appName").ifBlank { "芝麻-TK" }
                val rawCode = currentAppJson.optInt("latestVersionCode", 0)
                val latestVersionCode = if (rawCode > 0) rawCode else 35
                val latestVersionName = currentAppJson.optString("latestVersionName").ifBlank { "0.5.0" }
                val updateLog = currentAppJson.optString("updateLog", "")
                val isForce = currentAppJson.optBoolean("isForceUpdate", false)
                val apkDir = currentAppJson.optString("apkUploadDir", "update/apk/$actualId")

                val publishPayload = JSONObject().apply {
                    put("appId", actualId)
                    put("appName", appName)
                    put("latestVersionCode", latestVersionCode)
                    put("latestVersionName", latestVersionName)
                    put("updateLog", updateLog)
                    put("isForceUpdate", isForce)
                    put("apkUploadDir", apkDir)
                    put("packages", updatedPackages)
                }

                val publishUrl = "$baseHost/api/admin/update/publish"
                val publishReq = Request.Builder()
                    .url(publishUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(publishPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val publishResp = client.newCall(publishReq).execute()
                val isPublishOk = publishResp.isSuccessful
                val publishCode = publishResp.code
                val respBodyStr = publishResp.body?.string() ?: ""
                publishResp.close()

                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    try { loadingDialog.dismiss() } catch (_: Exception) {}
                    if (isPublishOk) {
                        Toast.makeText(this@AdminManagerActivity, "删除成功！", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        // 乐观即时更新本地 UI，卡片瞬间消失
                        currentAppJson.put("packages", updatedPackages)
                        renderPackageItems(updatedPackages)
                        // 静默后台重新拉取对齐
                        loadPublishedPackages()
                    } else {
                        var errDetail = "HTTP $publishCode"
                        try {
                            val errJson = JSONObject(respBodyStr)
                            val serverMsg = errJson.optString("error").ifBlank { errJson.optString("message") }
                            if (serverMsg.isNotBlank()) errDetail += " ($serverMsg)"
                        } catch (_: Exception) {}
                        if (publishCode == 404) {
                            errDetail += " - 管理员权限不足或登录凭据已失效，请尝试退出重新登录"
                        }
                        AlertDialog.Builder(this@AdminManagerActivity)
                            .setTitle("删除失败")
                            .setMessage("服务端更新清单失败: $errDetail")
                            .setPositiveButton("确定", null)
                            .show()
                        loadPublishedPackages()
                    }
                }

            } catch (e: Exception) {
                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    try { loadingDialog.dismiss() } catch (_: Exception) {}
                    AlertDialog.Builder(this@AdminManagerActivity)
                        .setTitle("删除异常")
                        .setMessage("执行异常: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    // --- 文件选取与分片上传 ---
    private fun requestPickApk() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择 APK 文件"), REQUEST_CODE_PICK_APK)
        } catch (_: Exception) {
            try {
                val docIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(docIntent, REQUEST_CODE_PICK_APK)
            } catch (e2: Exception) {
                Toast.makeText(this, "调起文件选择器失败: ${e2.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_APK && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: data.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return
            handlePickedUri(uri)
        }
    }

    private fun handlePickedUri(uri: Uri) {
        Thread {
            try {
                var fileName = "upload.apk"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIdx)
                    }
                }

                val tempDir = File(cacheDir, "admin_upload")
                if (!tempDir.exists()) tempDir.mkdirs()
                val targetFile = File(tempDir, fileName)

                contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    selectedUploadFile = targetFile
                    val sizeStr = formatSize(targetFile.length())
                    val md5 = calculateMD5(targetFile)

                    var appLabel = ""
                    try {
                        val pInfo = packageManager.getPackageArchiveInfo(targetFile.absolutePath, 0)
                        if (pInfo != null) {
                            appLabel = pInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: ""
                        }
                    } catch (_: Throwable) {}

                    handler.post {
                        if (isFinishing || isDestroyed) return@post
                        btnSelectFile?.text = "更换 APK 文件"
                        txtSelectedFileInfo?.text = "已选: $fileName\n大小: $sizeStr  |  MD5: ${md5.take(8)}..."
                        if (etUploadPkgName?.text.isNullOrBlank()) {
                            etUploadPkgName?.setText(appLabel.ifBlank { targetFile.nameWithoutExtension })
                        }
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    Toast.makeText(this@AdminManagerActivity, "解析本地文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun startUploadProcess() {
        val file = selectedUploadFile
        if (file == null || !file.exists() || file.length() == 0L) {
            Toast.makeText(this, "请先选择有效的 APK 文件", Toast.LENGTH_SHORT).show()
            return
        }

        val token = configManager.adminToken
        if (token.isBlank()) {
            Toast.makeText(this, "管理员登录已过期，请重新登录", Toast.LENGTH_SHORT).show()
            renderCurrentState()
            return
        }

        val pkgTitle = etUploadPkgName?.text?.toString()?.trim()?.ifEmpty { file.nameWithoutExtension } ?: file.nameWithoutExtension
        val pkgDesc = etUploadPkgDesc?.text?.toString()?.trim() ?: ""

        val progressDialog = M3ProgressDialog(this).apply {
            setMessage("准备分片直传...")
            setProgress(0)
            show()
        }

        Thread {
            val fileName = file.name
            val uploadPath = "update/apk/$targetAppId/$fileName"
            val downloadRelativeUrl = "/raw/update/apk/$targetAppId/$fileName"
            val totalBytes = file.length()

            try {
                // 1. 初始化分片上传: POST /api/write/items/$uploadPath?uploads
                val initUrl = "$baseHost/api/write/items/$uploadPath?uploads"
                val initReq = Request.Builder()
                    .url(initUrl)
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
                    handler.post {
                        if (isFinishing || isDestroyed) return@post
                        try { progressDialog.dismiss() } catch (_: Exception) {}
                        Toast.makeText(this@AdminManagerActivity, "初始化分片上传失败: HTTP $initCode", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val initJson = JSONObject(initBody)
                val uploadId = initJson.optString("uploadId")
                if (uploadId.isEmpty()) {
                    handler.post {
                        if (isFinishing || isDestroyed) return@post
                        try { progressDialog.dismiss() } catch (_: Exception) {}
                        Toast.makeText(this@AdminManagerActivity, "服务端未返回 uploadId", Toast.LENGTH_LONG).show()
                    }
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

                        handler.post {
                            if (isFinishing || isDestroyed) return@post
                            val percent = if (totalBytes > 0) ((uploadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0
                            progressDialog.setProgress(percent)
                            progressDialog.setMessage("正在分片直传 ($partNumber/$totalParts)...")
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
                            handler.post {
                                if (isFinishing || isDestroyed) return@post
                                try { progressDialog.dismiss() } catch (_: Exception) {}
                                Toast.makeText(this@AdminManagerActivity, "分片 $partNumber 传输失败: HTTP $code", Toast.LENGTH_LONG).show()
                            }
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

                // 3. 完成合并
                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    progressDialog.setProgress(100)
                    progressDialog.setMessage("所有分片已就绪，正在服务端合并...")
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
                    handler.post {
                        if (isFinishing || isDestroyed) return@post
                        try { progressDialog.dismiss() } catch (_: Exception) {}
                        Toast.makeText(this@AdminManagerActivity, "分片合并失败: HTTP $completeCode", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    progressDialog.setMessage("正在自动挂钩至模块清单...")
                }

                // 4. 挂钩至清单并发布
                val existingPackages = currentAppJson.optJSONArray("packages") ?: JSONArray()
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

                val actualId = if (matchedAppId.isNotBlank()) matchedAppId else targetAppId
                val appName = currentAppJson.optString("appName").ifBlank { "芝麻-TK" }
                val rawCode = currentAppJson.optInt("latestVersionCode", 0)
                val latestVersionCode = if (rawCode > 0) rawCode else 35
                val latestVersionName = currentAppJson.optString("latestVersionName").ifBlank { "0.5.0" }
                val updateLog = currentAppJson.optString("updateLog", "")
                val isForce = currentAppJson.optBoolean("isForceUpdate", false)
                val apkDir = currentAppJson.optString("apkUploadDir", "update/apk/$actualId")

                val publishPayload = JSONObject().apply {
                    put("appId", actualId)
                    put("appName", appName)
                    put("latestVersionCode", latestVersionCode)
                    put("latestVersionName", latestVersionName)
                    put("updateLog", updateLog)
                    put("isForceUpdate", isForce)
                    put("apkUploadDir", apkDir)
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
                val respBodyStr = publishResp.body?.string() ?: ""
                publishResp.close()

                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    try { progressDialog.dismiss() } catch (_: Exception) {}
                    if (publishSuccess) {
                        Toast.makeText(this@AdminManagerActivity, "上传并挂钩发布成功！", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        // 重置上传框
                        selectedUploadFile = null
                        txtSelectedFileInfo?.text = "未选择本地 APK 文件"
                        btnSelectFile?.text = "选择本地 APK"
                        etUploadPkgName?.setText("")
                        etUploadPkgDesc?.setText("")
                        // 乐观即时更新
                        currentAppJson.put("packages", updatedPackages)
                        renderPackageItems(updatedPackages)
                        loadPublishedPackages()
                    } else {
                        var errDetail = "HTTP $publishCode"
                        try {
                            val errJson = JSONObject(respBodyStr)
                            val serverMsg = errJson.optString("error").ifBlank { errJson.optString("message") }
                            if (serverMsg.isNotBlank()) errDetail += " ($serverMsg)"
                        } catch (_: Exception) {}
                        AlertDialog.Builder(this@AdminManagerActivity)
                            .setTitle("挂钩发布失败")
                            .setMessage("服务端返回错误: $errDetail")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }

            } catch (e: Exception) {
                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    try { progressDialog.dismiss() } catch (_: Exception) {}
                    Toast.makeText(this@AdminManagerActivity, "上传异常: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // --- 界面与辅助工具方法 ---

    private fun createM3Card(): FrameLayout {
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(colorCard)
                setStroke(dpToPx(1), colorBorder)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun createM3Input(hintText: String, isPassword: Boolean = false): EditText {
        val normalBg = GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            setStroke(dpToPx(1), colorBorder)
            setColor(colorCardInner)
        }
        val focusedBg = GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            setStroke(dpToPx(1.5f), colorBrand)
            setColor(colorCard)
        }
        return EditText(this).apply {
            hint = hintText
            if (isPassword) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            textSize = 13f
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextSecondary)
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            background = normalBg
            setOnFocusChangeListener { _, hasFocus ->
                background = if (hasFocus) focusedBg else normalBg
            }
        }
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

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    // 进度对话框
    private class M3ProgressDialog(activity: Activity) {
        private val dialog: AlertDialog
        private val txtMessage: TextView
        private val progressBar: ProgressBar

        init {
            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 20))
                setBackgroundColor(ThemeUtils.M3Palette(activity).surface)
            }

            txtMessage = TextView(activity).apply {
                textSize = 14f
                setTextColor(ThemeUtils.M3Palette(activity).onSurface)
                setPadding(0, 0, 0, dp(activity, 12))
            }
            layout.addView(txtMessage)

            progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 8))
                layoutParams = lp
            }
            layout.addView(progressBar)

            dialog = AlertDialog.Builder(activity)
                .setView(layout)
                .setCancelable(false)
                .create()
        }

        fun setMessage(msg: String) {
            txtMessage.text = msg
        }

        fun setProgress(progress: Int) {
            progressBar.progress = progress
        }

        fun show() {
            dialog.show()
        }

        fun dismiss() {
            dialog.dismiss()
        }

        private fun dp(context: Context, v: Int): Int {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
        }
    }

    // 轻量级居中矢量返回箭头
    private class BackArrowDrawable(private val color: Int, private val strokeWidthPx: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@BackArrowDrawable.color
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            path.reset()
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val arrowW = w * 0.36f
            val arrowH = h * 0.50f
            path.moveTo(cx + arrowW * 0.45f, cy - arrowH * 0.5f)
            path.lineTo(cx - arrowW * 0.55f, cy)
            path.lineTo(cx + arrowW * 0.45f, cy + arrowH * 0.5f)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
