package fansirsqi.xposed.sesame.ui.logviewer

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.newui.WatermarkView.Companion.install
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.LanguageUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import java.io.File

class HtmlViewerActivity : BaseActivity() {

    companion object {
        private const val TAG = "HtmlViewerActivity"
        private const val MENU_EXPORT = 1
        private const val MENU_CLEAR = 2
        private const val MENU_OPEN_BROWSER = 3
        private const val MENU_OPEN_LOG_DIR = 4
        private const val MENU_COPY_URL = 5
        private const val MENU_SCROLL_TOP = 6
        private const val MENU_SCROLL_BOTTOM = 7
    }

    private lateinit var mWebView: MyWebView
    private lateinit var progressBar: ProgressBar
    private lateinit var settings: WebSettings
    private var uri: Uri? = null
    private var canClear: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageUtil.setLocale(this)
        setContentView(R.layout.activity_html_viewer)
        install(this)

        // 初始化视图
        mWebView = findViewById(R.id.mwv_webview)
        progressBar = findViewById(R.id.pgb_webview)
        settings = mWebView.settings

        // 获取 Intent 数据
        intent?.let {
            uri = it.data
            canClear = it.getBooleanExtra("canClear", false)
        }

        // 配置 WebView
        setupWebView()
        configureWebViewSettings()
        setupWindowInsets()
    }

    /**
     * 配置 WebView 基础设置
     */
    private fun setupWebView() {
        try {
            // 基础安全设置
            settings.apply {
                javaScriptEnabled = false
                domStorageEnabled = false
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                textZoom = 85
                setAllowFileAccess(true)
                setAllowContentAccess(true)
            }

            // 夜间模式支持
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                try {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                } catch (e: Exception) {
                    Log.error(TAG, "设置夜间模式失败: ${e.message}")
                    Log.printStackTrace(TAG, e)
                }
            }

            // 设置样式
            progressBar.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.selection_color)
            )
            mWebView.setBackgroundColor(ContextCompat.getColor(this, R.color.background))

            // 设置 WebChromeClient
            mWebView.webChromeClient = createWebChromeClient()
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "WebView初始化异常: ", e)
        }
    }

    /**
     * 配置窗口边距
     */
    private fun setupWindowInsets() {
        val contentView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            mWebView.setPadding(
                mWebView.paddingLeft,
                mWebView.paddingTop,
                mWebView.paddingRight,
                systemBarsBottom
            )
            insets
        }
    }

    /**
     * 创建 WebChromeClient 用于处理进度和日志加载
     */
    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, progress: Int) {
                progressBar.progress = progress
                if (progress < 100) {
                    baseSubtitle = "Loading..."
                    progressBar.visibility = View.VISIBLE
                } else {
                    baseSubtitle = mWebView.title
                    progressBar.visibility = View.GONE

                    // 页面加载完成后，处理日志文件
                    handleLogFileLoading()
                }
            }
        }
    }

    /**
     * 处理日志文件加载
     */
    private fun handleLogFileLoading() {
        val currentUri = uri ?: return
        if (!"file".equals(currentUri.scheme, ignoreCase = true)) return

        val path = currentUri.path ?: return
        if (!path.endsWith(".log")) return

        // 在后台线程中读取文件
        Thread {
            try {
                val content = readAllTextSafe(path)
                val jsArg = toJsString(content)

                // 切回主线程执行 WebView 操作
                runOnUiThread {
                    try {
                        mWebView.evaluateJavascript("setFullText($jsArg)", null)
                        (mWebView as? MyWebView)?.startWatchingIncremental(path)
                    } catch (e: Exception) {
                        Log.error(TAG, "WebView 操作失败: ${e.message}")
                        Log.printStackTrace(TAG, e)
                    }
                }
            } catch (e: Exception) {
                Log.error(TAG, "后台读取文件失败: ${e.message}")
                Log.printStackTrace(TAG, e)

                // 即使文件读取失败，也要启动监听
                runOnUiThread {
                    try {
                        (mWebView as? MyWebView)?.startWatchingIncremental(path)
                    } catch (ex: Exception) {
                        Log.error(TAG, "启动文件监听失败: ${ex.message}")
                    }
                }
            }
        }.start()
    }

    /**
     * 安全读取文件内容（兼容所有 API 版本）
     */
    private fun readAllTextSafe(path: String): String {
        return try {
            File(path).readText(Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.error(TAG, "读取文件失败: ${t.message}")
            ""
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onResume() {
        super.onResume()
        try {
            if (uri != null) {
                val path = uri?.path ?: ""
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                if ("file".equals(uri?.scheme, ignoreCase = true) && path.endsWith(".log")) {
                    mWebView.loadUrl("file:///android_asset/log_viewer_legacy.html")
                } else {
                    mWebView.loadUrl(uri.toString())
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "WebView设置异常: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 配置 WebView 的设置项
     */
    private fun configureWebViewSettings() {
        try {
            val nextLine = intent?.getBooleanExtra("nextLine", true) ?: true
            settings.apply {
                textZoom = 85
                useWideViewPort = !nextLine
            }
        } catch (e: Exception) {
            Log.error(TAG, "配置 WebView 设置失败: ${e.message}")
        }
    }


    private fun copyUrl() {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val text = uri?.toString() ?: ""
            cm.setPrimaryClip(ClipData.newPlainText("url", text))
            ToastUtil.showToast(this, getString(R.string.copy_success))
        } catch (e: Exception) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            (mWebView as? MyWebView)?.stopWatchingIncremental()
        } catch (e: Exception) {
            Log.error(TAG, "停止文件监听失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            (mWebView as? MyWebView)?.stopWatchingIncremental()
            mWebView.apply {
                loadUrl("about:blank")
                stopLoading()
                clearHistory()
                removeAllViews()
                (parent as? android.view.ViewGroup)?.removeView(this)
                destroy()
            }
        } catch (e: Exception) {
            Log.error(TAG, "销毁 WebView 失败: ${e.message}")
        }
        super.onDestroy()
    }

    /**
     * 将字符串安全转为 JS 字面量
     */
    private fun toJsString(s: String?): String {
        if (s == null) return "''"
        val sb = StringBuilder(s.length + 16)
        sb.append('\'')
        for (c in s) {
            when (c) {
                '\'' -> sb.append("\\'")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u000C' -> sb.append("\\f")
                '\b' -> sb.append("\\b")
                else -> {
                    if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('\'')
        return sb.toString()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_EXPORT, 1, getString(R.string.export_file))
        if (canClear) {
            menu.add(0, MENU_CLEAR, 2, getString(R.string.clear_file))
        }
        menu.add(0, MENU_OPEN_BROWSER, 3, getString(R.string.open_with_other_browser))
        menu.add(0, MENU_OPEN_LOG_DIR, 4, "打开日志文件夹")
        menu.add(0, MENU_COPY_URL, 5, getString(R.string.copy_the_url))
        menu.add(0, MENU_SCROLL_TOP, 6, getString(R.string.scroll_to_top))
        menu.add(0, MENU_SCROLL_BOTTOM, 7, getString(R.string.scroll_to_bottom))
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_EXPORT -> exportFile()
            MENU_CLEAR -> clearFile()
            MENU_OPEN_BROWSER -> openWithBrowser()
            MENU_OPEN_LOG_DIR -> openLogDirectory()
            MENU_COPY_URL -> copyUrl()
            MENU_SCROLL_TOP -> mWebView.scrollTo(0, 0)
            MENU_SCROLL_BOTTOM -> mWebView.scrollToBottom()
        }
        return true
    }

    /**
     * 导出当前文件
     */
    private fun exportFile() {
        try {
            val currentUri = uri ?: run {
                Log.runtime(TAG, "URI 为 null！")
                return
            }

            val path = currentUri.path ?: run {
                Log.runtime(TAG, "路径为 null！")
                return
            }

            Log.runtime(TAG, "导出文件: $path")
            val exportFile = Files.exportFile(File(path), true)

            if (exportFile?.exists() == true) {
                ToastUtil.showToast(getString(R.string.file_exported) + exportFile.path)
            } else {
                Log.runtime(TAG, "导出失败，exportFile 对象为 null 或不存在！")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 清空当前文件
     */
    private fun clearFile() {
        try {
            val currentUri = uri ?: return
            val path = currentUri.path ?: return
            val file = File(path)

            if (Files.clearFile(file)) {
                ToastUtil.makeText(this, "文件已清空", Toast.LENGTH_SHORT).show()
                mWebView.reload()
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 使用外部浏览器（Chrome / Edge / 系统浏览器）打开当前 HTML 文件或 URL
     */
    private fun openWithBrowser() {
        val currentUri = uri ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val scheme = currentUri.scheme
                if (scheme.equals("file", ignoreCase = true)) {
                    val file = File(currentUri.path ?: "")
                    if (file.exists()) {
                        val contentUri = androidx.core.content.FileProvider.getUriForFile(
                            this@HtmlViewerActivity,
                            "$packageName.provider",
                            file
                        )
                        setDataAndType(contentUri, "text/html")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        setDataAndType(currentUri, "text/html")
                    }
                } else {
                    setDataAndType(currentUri, "text/html")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "选择浏览器打开"))
        } catch (e: Exception) {
            Log.error(TAG, "唤起浏览器打开失败: ${e.message}")
            ToastUtil.showToast("未找到可打开 HTML 的浏览器")
        }
    }

    /**
     * 打开日志目录（自动唤起系统/第三方文件管理器定位目录）
     */
    private fun openLogDirectory() {
        val logDir = Files.LOG_DIR
        if (!logDir.exists()) {
            try { logDir.mkdirs() } catch (_: Exception) {}
        }

        val relativePath = logDir.absolutePath.replaceFirst("^/storage/emulated/0/", "").replaceFirst("^/sdcard/", "")
        val encodedPath = Uri.encode("primary:$relativePath")
        val docUri = Uri.parse("content://com.android.externalstorage.documents/document/$encodedPath")

        val intents = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra("android.provider.extra.INITIAL_URI", docUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(logDir), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(logDir), "inode/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                Log.error(TAG, "尝试唤起文件管理器失败: ${e.message}")
            }
        }

        try {
            val chooser = Intent.createChooser(intents.last(), "选择文件管理器打开日志目录")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chooser)
        } catch (e: Exception) {
            ToastUtil.showToast(this, "打开日志目录: ${logDir.absolutePath}")
        }
    }
}