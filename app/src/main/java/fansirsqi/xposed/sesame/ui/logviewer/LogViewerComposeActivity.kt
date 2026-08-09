package fansirsqi.xposed.sesame.ui.logviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.LanguageUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.toArgb

/**
 * Compose 版本的日志查看器 Activity
 *
 * 功能特性：
 * - 使用 Jetpack Compose 构建原生 UI
 * - 虚拟滚动（LazyColumn）处理大量日志
 * - 实时日志更新
 * - 搜索和高亮（支持正则表达式）
 * - 日志级别过滤
 * - 关键字筛选
 * - 性能优化
 */
class LogViewerComposeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LogViewerComposeActivity"
    }

    private val viewModel: LogViewerViewModel by viewModels()
    private var uri: Uri? = null
    private var canClear: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageUtil.setLocale(this)

        // 设置透明状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 获取 Intent 数据
        intent?.let {
            uri = it.data
            canClear = it.getBooleanExtra("canClear", false)
        }

        // 智能日志文件加载与自动寻址保底
        resolveAndLoadLogFile(uri)

        setContent {
            SesameTheme {
                LogViewerScreen(
                    viewModel = viewModel,
                    canClear = canClear,
                    onExport = { exportFile() },
                    onClear = { clearFile() },
                    onOpenBrowser = { openWithBrowser() },
                    onOpenLogDirectory = { openLogDirectory() },
                    onBack = { finish() }
                )
            }
        }
    }

    /**
     * 智能解析并加载日志文件（支持 file://, content:// 以及 Null 自动缺省寻址）
     */
    private fun resolveAndLoadLogFile(targetUri: Uri?) {
        try {
            var targetFile: File? = null

            if (targetUri != null) {
                if ("file".equals(targetUri.scheme, ignoreCase = true)) {
                    targetUri.path?.let { targetFile = File(it) }
                } else if ("content".equals(targetUri.scheme, ignoreCase = true)) {
                    // content:// 协议支持：拉取 InputStream 缓存
                    val tempFile = File(cacheDir, "temp_content_log.log")
                    contentResolver.openInputStream(targetUri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tempFile.exists()) targetFile = tempFile
                }
            }

            // 缺省保底：若未解析到目标文件，自动寻址 LOG_DIR 目录下的最新日志文件
            if (targetFile == null || !targetFile.exists()) {
                val logDir = Files.LOG_DIR
                targetFile = logDir.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }
                    ?.maxByOrNull { it.lastModified() }
                    ?: File(logDir, "app.log")
            }

            targetFile?.let { file ->
                if (file.exists()) {
                    viewModel.loadFile(file)
                    viewModel.startWatchingFile(file.absolutePath)
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "解析加载日志文件异常: ${e.message}")
        }
    }

    /**
     * 加载日志文件
     */
    private fun loadLogFile(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                viewModel.loadFile(file)
                viewModel.startWatchingFile(path)
            }
        } catch (e: Exception) {
            Log.error(TAG, "加载日志文件失败: ${e.message}")
            ToastUtil.showToast(this, "加载日志文件失败")
        }
    }

    /**
     * 导出文件
     */
    private fun exportFile() {
        try {
            val currentUri = uri ?: return
            val path = currentUri.path ?: return
            val exportFile = Files.exportFile(File(path), true)

            if (exportFile?.exists() == true) {
                ToastUtil.showToast("文件已导出: ${exportFile.path}")
            } else {
                Log.runtime(TAG, "导出失败")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 清空文件
     */
    private fun clearFile() {
        try {
            val currentUri = uri ?: return
            val path = currentUri.path ?: return
            val file = File(path)

            if (Files.clearFile(file)) {
                ToastUtil.showToast(this, "文件已清空")
                viewModel.clearLog()
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 使用其他浏览器打开
     */
    private fun openWithBrowser() {
        val currentUri = uri ?: return
        val scheme = currentUri.scheme

        when {
            scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true) -> {
                val intent = Intent(Intent.ACTION_VIEW, currentUri)
                startActivity(intent)
            }
            scheme.equals("file", ignoreCase = true) -> {
                ToastUtil.showToast(this, "该文件不支持用浏览器打开")
            }
            else -> {
                ToastUtil.showToast(this, "不支持用浏览器打开")
            }
        }
    }

    /**
     * 选择其他应用打开日志目录（弹出系统应用选择面板）
     */
    private fun openLogDirectory() {
        val logDir = Files.LOG_DIR
        if (!logDir.exists()) {
            try { logDir.mkdirs() } catch (_: Exception) {}
        }

        // FileProvider 只能针对实际文件生成 valid content URI，因此优先选中目录下包含的日志文件
        val targetFile = logDir.listFiles()?.firstOrNull { it.isFile } ?: File(logDir, "app.log").apply {
            if (!exists()) try { createNewFile() } catch (_: Exception) {}
        }

        try {
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                targetFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "text/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "选择其他应用打开日志文件/目录")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chooser)
        } catch (e: Exception) {
            Log.error(TAG, "选择应用打开失败: ${e.message}")
            ToastUtil.showToast(this, "日志目录: ${logDir.absolutePath}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopWatchingFile()
    }
}

// LogViewerTheme removed as it is replaced by SesameTheme

/**
 * 日志查看器主屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    viewModel: LogViewerViewModel,
    canClear: Boolean,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenLogDirectory: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()

    // 拦截系统返回键：多选模式下返回先取消多选
    if (uiState.isSelectionMode) {
        BackHandler {
            viewModel.clearSelection()
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showFilterPanel by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val topBarBg = MaterialTheme.colorScheme.primaryContainer
    val topBarContent = MaterialTheme.colorScheme.onPrimaryContainer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志查看器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = topBarContent) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = topBarContent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarBg,
                    scrolledContainerColor = topBarBg
                ),
                actions = {
                    // 搜索按钮
                    IconButton(onClick = { viewModel.setSearchPanelVisible(!uiState.showSearchPanel) }) {
                        Icon(Icons.Rounded.Search, "搜索", tint = topBarContent)
                    }
                    // 筛选按钮
                    IconButton(onClick = { showFilterPanel = !showFilterPanel }) {
                        Icon(Icons.Rounded.FilterList, "筛选", tint = topBarContent)
                    }
                    // 齿轮按钮（点击弹出视图与文本控制面板）
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(Icons.Rounded.Settings, "视图设置", tint = topBarContent)
                    }
                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("放大文本 (+)") },
                            onClick = {
                                showSettingsMenu = false
                                viewModel.increaseFontSize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("缩小文本 (-)") },
                            onClick = {
                                showSettingsMenu = false
                                viewModel.decreaseFontSize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重置文本大小") },
                            onClick = {
                                showSettingsMenu = false
                                viewModel.resetFontSize()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (uiState.isHtmlMode) "Compose 视图" else "HTML 视图") },
                            onClick = {
                                showSettingsMenu = false
                                viewModel.toggleHtmlMode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (uiState.isSelectionMode) "退出多选模式" else "开启多选模式") },
                            onClick = {
                                showSettingsMenu = false
                                viewModel.toggleSelectionMode(!uiState.isSelectionMode)
                            }
                        )
                    }
                    // 更多菜单
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, "更多", tint = topBarContent)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("复制全部日志") },
                            onClick = {
                                showMenu = false
                                viewModel.copyAllLogsToClipboard(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出文件") },
                            onClick = {
                                showMenu = false
                                onExport()
                            }
                        )
                        if (canClear) {
                            DropdownMenuItem(
                                text = { Text("清空文件") },
                                onClick = {
                                    showMenu = false
                                    onClear()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("用浏览器打开") },
                            onClick = {
                                showMenu = false
                                onOpenBrowser()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("选择其他应用打开") },
                            onClick = {
                                showMenu = false
                                onOpenLogDirectory()
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            var webViewInstance by remember { mutableStateOf<android.webkit.WebView?>(null) }

            // 搜索面板
            if (uiState.showSearchPanel) {
                SearchPanel(
                    viewModel = viewModel,
                    uiState = uiState,
                    onDismiss = { viewModel.setSearchPanelVisible(false) }
                )
            }

            // 筛选面板
            if (showFilterPanel) {
                FilterPanel(
                    viewModel = viewModel,
                    uiState = uiState,
                    onDismiss = { showFilterPanel = false }
                )
            }

            // 滚动方向追踪：下滑隐藏悬浮组件，上滑/静止显示
            var scrollDirection by remember { mutableStateOf(ScrollDirection.IDLE) }
            LaunchedEffect(lazyListState) {
                var prevIdx = lazyListState.firstVisibleItemIndex
                var prevOff = lazyListState.firstVisibleItemScrollOffset
                snapshotFlow {
                    Triple(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset, lazyListState.isScrollInProgress)
                }.collect { (idx, off, scrolling) ->
                    scrollDirection = if (scrolling) {
                        when {
                            idx > prevIdx || (idx == prevIdx && off > prevOff) -> ScrollDirection.DOWN
                            idx < prevIdx || (idx == prevIdx && off < prevOff) -> ScrollDirection.UP
                            else -> scrollDirection
                        }
                    } else ScrollDirection.IDLE
                    prevIdx = idx; prevOff = off
                }
            }

            // WebView 渲染进程崩溃自愈标识
            var webViewCrashKey by remember { mutableIntStateOf(0) }


            // 仅在 WebView 存活时执行 JS，避免 "destroyed WebView" 崩溃
            fun runJsSafely(view: android.webkit.WebView?, script: () -> String) {
                val wv = view ?: return
                if (!wv.isAttachedToWindow) return
                wv.post { wv.evaluateJavascript(script(), null) }
            }

            // 状态栏（包含区分清晰的直达顶部与直达底部按钮）
            StatusBar(
                uiState = uiState,
                viewModel = viewModel,
                onScrollToTop = {
                    if (uiState.isHtmlMode) {
                        webViewInstance?.scrollTo(0, 0)
                    } else if (uiState.displayedLines.isNotEmpty()) {
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    }
                },
                onScrollToBottom = {
                    if (uiState.isHtmlMode) {
                        webViewInstance?.let { it.scrollTo(0, it.contentHeight) }
                    } else if (uiState.displayedLines.isNotEmpty()) {
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(uiState.displayedLines.size - 1)
                        }
                    }
                }
            )

            // 日志内容与悬浮组件
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                var safeWebView by remember { mutableStateOf<android.webkit.WebView?>(null) }

                fun createWebView(ctx: android.content.Context): android.webkit.WebView {
                    val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(ctx))
                        .addPathHandler("/logdata/", androidx.webkit.WebViewAssetLoader.PathHandler { _ ->
                            val inputStream = viewModel.getLogInputStream(ctx)
                            if (inputStream != null) {
                                val headers = HashMap<String, String>().apply {
                                    put("Access-Control-Allow-Origin", "*")
                                    put("Cache-Control", "no-store, no-cache, must-revalidate")
                                    put("Pragma", "no-cache")
                                }
                                android.webkit.WebResourceResponse("text/plain", "UTF-8", 200, "OK", headers, inputStream)
                            } else null
                        })
                        .build()

                    return android.webkit.WebView(ctx).apply {
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        // 禁用原生滚动条，使用自定义 WebViewScrollbar 替代（避免双滚动条）
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                request?.url?.let { url ->
                                    val intercepted = assetLoader.shouldInterceptRequest(url)
                                    if (intercepted != null) return intercepted
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onRenderProcessGone(
                                view: android.webkit.WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                Log.error("LogViewer", "WebView 渲染进程崩溃，启动全自动自我恢复机制...")
                                try { view?.destroy() } catch (_: Exception) {}
                                webViewInstance = null
                                webViewCrashKey++
                                return true
                            }
                        }

                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                                if (newProgress == 100) {
                                    runJsSafely(view) { "initLogBridge()" }
                                }
                            }
                        }
                        loadUrl("https://appassets.androidplatform.net/assets/log_viewer_legacy.html")
                        webViewInstance = this
                    }
                }

                // 1. 筛选/日志级别/字号更新时 -> 通知 H5 重新渲染文本
                LaunchedEffect(uiState.displayedLines, uiState.isHtmlMode) {
                    if (uiState.isHtmlMode) {
                        runJsSafely(safeWebView) { "initLogBridge()" }
                    }
                }

                // 2. 搜索词/模式改变时 -> 通知 H5 重新高亮
                LaunchedEffect(uiState.searchKeyword, uiState.isCaseSensitive, uiState.isRegexSearch, uiState.displayedLines, uiState.isHtmlMode) {
                    if (uiState.isHtmlMode) {
                        if (uiState.searchKeyword.isNotEmpty()) {
                            val kw = uiState.searchKeyword.replace("'", "\\'")
                            runJsSafely(safeWebView) { "highlightKeyword('$kw', ${uiState.isCaseSensitive}, ${uiState.isRegexSearch})" }
                        } else {
                            runJsSafely(safeWebView) { "clearH5Search()" }
                        }
                    }
                }

                // 3. 搜索索引改变 (点击"下一个 / 上一个") -> 通知 H5 平滑跳转
                LaunchedEffect(uiState.currentSearchIndex, uiState.isHtmlMode) {
                    if (uiState.isHtmlMode && uiState.currentSearchIndex >= 0 && uiState.searchKeyword.isNotEmpty()) {
                        runJsSafely(safeWebView) { "jumpSearchMatch(${uiState.currentSearchIndex})" }
                    }
                }

                // 4. 自动滚动开关变更 -> 同步到 H5
                LaunchedEffect(uiState.autoScroll, uiState.isHtmlMode) {
                    if (uiState.isHtmlMode) {
                        runJsSafely(safeWebView) { "setAutoScroll(${uiState.autoScroll})" }
                    }
                }

                // Compose 原生日志列表
                if (!uiState.isHtmlMode) {
                    LogContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        lazyListState = lazyListState
                    )

                    // 动态悬浮 RPC 大纲：向下滑动时自动隐藏，向上滑动/静止时自动显现
                    androidx.compose.animation.AnimatedVisibility(
                        visible = scrollDirection != ScrollDirection.DOWN,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 72.dp)
                    ) {
                        RequestNavigator(
                            viewModel = viewModel,
                            uiState = uiState,
                            lazyListState = lazyListState
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.isSelectionMode && scrollDirection != ScrollDirection.DOWN,
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        SelectionActionBar(
                            viewModel = viewModel,
                            uiState = uiState
                        )
                    }
                }

                // 预热全速呈现的 HTML WebView (0 秒秒切无白屏)
                if (uiState.isHtmlMode) {
                    val themePrimary = MaterialTheme.colorScheme.primary
                    val themeOnSurface = MaterialTheme.colorScheme.onSurface
                    key(webViewCrashKey) {
                        val scrollbar = remember { WebViewScrollbar(context) }
                        val sbW = dpToPx(context, 24)
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = {
                                val container = android.widget.FrameLayout(context)
                                // 每次进入 HTML 模式都创建全新 WebView，不复用已销毁的
                                val wv = createWebView(context)
                                safeWebView = wv
                                container.addView(wv, android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                ).apply { rightMargin = sbW })
                                container.addView(scrollbar, android.widget.FrameLayout.LayoutParams(
                                    sbW,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                ).apply { gravity = android.view.Gravity.END })
                                scrollbar.attachWebView(wv)
                                container
                            },
                            update = { container ->
                                val wv = container.getChildAt(0) as? android.webkit.WebView ?: return@AndroidView
                                val sb = container.getChildAt(1) as? WebViewScrollbar
                                sb?.setColors(themePrimary.toArgb(), themeOnSurface.toArgb())
                                wv.settings.textZoom = (uiState.fontSize * 9).coerceIn(40, 200)
                                runJsSafely(wv) { "initLogBridge()" }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        DisposableEffect(Unit) {
                            onDispose {
                                safeWebView?.let { wv ->
                                    try {
                                        wv.loadUrl("about:blank")
                                        wv.stopLoading()
                                        wv.clearHistory()
                                        wv.removeAllViews()
                                        (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                                        wv.destroy()
                                    } catch (_: Exception) {}
                                }
                                safeWebView = null
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class ScrollDirection { UP, DOWN, IDLE }

private fun dpToPx(context: android.content.Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}
