package fansirsqi.xposed.sesame.ui.logviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.LanguageUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import kotlinx.coroutines.launch
import java.io.File

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

        // 加载日志文件
        uri?.let { currentUri ->
            if ("file".equals(currentUri.scheme, ignoreCase = true)) {
                currentUri.path?.let { path ->
                    if (path.endsWith(".log")) {
                        loadLogFile(path)
                    }
                }
            }
        }

        setContent {
            SesameTheme {
                LogViewerScreen(
                    viewModel = viewModel,
                    canClear = canClear,
                    onExport = { exportFile() },
                    onClear = { clearFile() },
                    onOpenBrowser = { openWithBrowser() },
                    onBack = { finish() }
                )
            }
        }
    }

    /**
     * 加载日志文件
     */
    private fun loadLogFile(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                val content = file.readText(Charsets.UTF_8)
                viewModel.setFullText(content)
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
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showSearchPanel by remember { mutableStateOf(false) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var showLevelFilter by remember { mutableStateOf(false) }

    val mintBg = Color(0xFFE9F5E9)
    val deepGreen = Color(0xFF2D5A27)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志查看器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = deepGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = deepGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = mintBg,
                    scrolledContainerColor = mintBg
                ),
                actions = {
                    // 搜索按钮
                    IconButton(onClick = { showSearchPanel = !showSearchPanel }) {
                        Icon(Icons.Rounded.Search, "搜索", tint = deepGreen)
                    }
                    // 筛选按钮
                    IconButton(onClick = { showFilterPanel = !showFilterPanel }) {
                        Icon(Icons.Rounded.FilterList, "筛选", tint = deepGreen)
                    }
                    // 日志级别过滤按钮
                    IconButton(onClick = { showLevelFilter = !showLevelFilter }) {
                        Icon(Icons.Rounded.Settings, "日志级别", tint = deepGreen)
                    }
                    // 更多菜单
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, "更多", tint = deepGreen)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("放大文本") },
                            onClick = {
                                showMenu = false
                                viewModel.increaseFontSize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("缩小文本") },
                            onClick = {
                                showMenu = false
                                viewModel.decreaseFontSize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重置文本大小") },
                            onClick = {
                                showMenu = false
                                viewModel.resetFontSize()
                            }
                        )
                        Divider()
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
            // 搜索面板
            if (showSearchPanel) {
                SearchPanel(
                    viewModel = viewModel,
                    uiState = uiState,
                    onDismiss = { showSearchPanel = false }
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

            // 日志级别过滤面板
            if (showLevelFilter) {
                LogLevelFilterPanel(
                    viewModel = viewModel,
                    uiState = uiState,
                    onDismiss = { showLevelFilter = false }
                )
            }

            // 状态栏
            StatusBar(uiState = uiState, viewModel = viewModel)

            // 日志内容
            LogContent(
                viewModel = viewModel,
                uiState = uiState
            )
        }
    }
}
