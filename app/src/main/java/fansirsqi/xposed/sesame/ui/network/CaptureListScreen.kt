package fansirsqi.xposed.sesame.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import fansirsqi.xposed.sesame.hook.network.model.CaptureRecord
import fansirsqi.xposed.sesame.ui.theme.app.SesameColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureListScreen(
    viewModel: CaptureListViewModel,
    onBack: () -> Unit,
    onRecordClick: (CaptureRecord) -> Unit
) {
    val records by viewModel.displayRecords.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val isGlobal by viewModel.isGlobalSearch.collectAsState()
    val globalResults by viewModel.globalSearchResults.collectAsState()
    val blacklist by viewModel.blacklist.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showBlacklist by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // 统计数据
    val total = records.size
    val success = records.count { it.statusCode in 200..299 }
    val error = records.count { it.statusCode >= 400 || it.statusCode == 0 }

    val appBarBg = MaterialTheme.colorScheme.primaryContainer
    val appBarContent = MaterialTheme.colorScheme.onPrimaryContainer

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索请求...", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { IconButton(onClick = { isSearchActive = false; viewModel.updateSearchQuery("") }) { Icon(Icons.Rounded.Close, null) } },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    } else {
                        Column {
                            Text("网络抓包", fontWeight = FontWeight.Bold, color = appBarContent)
                            Text("实时监控应用网络请求", style = MaterialTheme.typography.labelSmall, color = appBarContent.copy(alpha = 0.6f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = appBarContent)
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Rounded.Search, "搜索", tint = appBarContent)
                        }
                        IconButton(onClick = {
                            viewModel.isGlobalSearch.value = !isGlobal
                            if (!isGlobal) viewModel.searchAllDates(searchQuery.ifEmpty { "*" })
                        }) {
                            Icon(
                                if (isGlobal) Icons.Rounded.Public else Icons.Rounded.Today,
                                if (isGlobal) "全局" else "当天",
                                tint = if (isGlobal) SesameColors.Primary else appBarContent
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, "更多", tint = appBarContent)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("历史记录") },
                                    leadingIcon = { Icon(Icons.Rounded.History, null) },
                                    onClick = { showMenu = false; showHistory = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("过滤配置") },
                                    leadingIcon = { Icon(Icons.Rounded.FilterAlt, null) },
                                    onClick = { showMenu = false; showBlacklist = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("自动滚动 (${if (autoScroll) "开" else "关"})") },
                                    leadingIcon = { Icon(Icons.Rounded.VerticalAlignBottom, null) },
                                    onClick = { viewModel.toggleAutoScroll(); showMenu = false }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("清除当前", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { viewModel.clearCurrentDate(); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("清空所有", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { viewModel.clearAll(); showMenu = false }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBg)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 统计仪表盘
            DashboardHeader(total, success, error)

            // 分类筛选
            CategoryFilterBar(
                categories = viewModel.getAllCategories(),
                selected = categoryFilter,
                onSelect = { viewModel.setCategoryFilter(it) }
            )

            // 列表
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val displayList = if (isGlobal) globalResults else records

                if (isLoading && displayList.isEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                } else if (displayList.isEmpty()) {
                    EmptyState()
                } else {
                    val listState = rememberLazyListState()

                    LaunchedEffect(displayList.size) {
                        if (autoScroll && displayList.isNotEmpty() && !isLoading) {
                            listState.animateScrollToItem(0)
                        }
                    }

                    val shouldLoadMore = remember {
                        derivedStateOf {
                            val lastItem = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                            lastItem.index >= listState.layoutInfo.totalItemsCount - 5
                        }
                    }

                    LaunchedEffect(shouldLoadMore.value) {
                        if (shouldLoadMore.value && hasMore && !isLoading && !isGlobal) {
                            viewModel.loadMore()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(displayList, key = { it.id }) { record ->
                            RecordItem(
                                record = record,
                                onClick = { onRecordClick(record) },
                                onBlockHost = { host -> viewModel.toggleBlacklist(host) },
                                onFilterHost = { host -> viewModel.updateSearchQuery(host) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (hasMore && !isGlobal) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 历史记录对话框
    if (showHistory) {
        val dates = remember { viewModel.getDates() }
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("历史捕获记录") },
            text = {
                if (dates.isEmpty()) Text("暂无记录") else LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(dates) { date ->
                        ListItem(
                            headlineContent = { Text(date) },
                            leadingContent = { Icon(Icons.Rounded.Event, null) },
                            modifier = Modifier.clickable { viewModel.loadData(date); showHistory = false }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("关闭") } }
        )
    }

    // 黑名单配置
    if (showBlacklist) {
        BlacklistSheet(
            blacklist = blacklist,
            onDismiss = { showBlacklist = false },
            onAdd = { viewModel.toggleBlacklist(it) },
            onRemove = { viewModel.toggleBlacklist(it) }
        )
    }
}

// ── Dashboard ─────────────────────────────────

@Composable
private fun DashboardHeader(total: Int, success: Int, error: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("总请求", total.toString(), Icons.Rounded.Sync, SesameColors.Primary, Modifier.weight(1f))
        StatCard("成功", success.toString(), Icons.Rounded.CheckCircle, SesameColors.Success, Modifier.weight(1f))
        StatCard("异常", error.toString(), Icons.Rounded.Error, SesameColors.Error, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ── Category Filter ──────────────────────────

@Composable
private fun CategoryFilterBar(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部", fontSize = 11.sp) }
            )
        }
        items(categories) { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(if (selected == cat) null else cat) },
                label = { Text(cat, fontSize = 11.sp) }
            )
        }
    }
}

// ── Record Item ──────────────────────────────

@Composable
private fun RecordItem(
    record: CaptureRecord,
    onClick: () -> Unit,
    onBlockHost: (String) -> Unit,
    onFilterHost: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val statusColor = SesameColors.getStatusColor(record.statusCode)
    val timeStr = remember(record.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Method badge
                MethodBadge(record.method)
                Spacer(Modifier.width(8.dp))
                // Category badge
                if (record.category.isNotEmpty() && record.category != "其他") {
                    Surface(
                        color = SesameColors.Primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            record.category,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            color = SesameColors.Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                // Host
                Text(
                    text = record.host,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Duration
                Text(
                    "${record.duration}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                // Context menu
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Rounded.MoreVert, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("过滤该域名") },
                            leadingIcon = { Icon(Icons.Rounded.FilterList, null, modifier = Modifier.size(18.dp)) },
                            onClick = { onFilterHost(record.host); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("拉黑 ${record.host}", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                            onClick = { onBlockHost(record.host); showMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // URL preview
            Text(
                text = record.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status code
                Surface(color = statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Box(modifier = Modifier.size(4.dp).background(statusColor, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = statusLabel(record.statusCode),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontSize = 9.sp
                        )
                    }
                }
                // Truncation warning
                if (record.isTruncated) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Warning, null, modifier = Modifier.size(12.dp), tint = SesameColors.Warning)
                }
                // Error
                if (record.errorMessage != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ErrorOutline, null, modifier = Modifier.size(12.dp), tint = SesameColors.Error)
                }
                Spacer(Modifier.weight(1f))
                Text(text = timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val color = when (method.uppercase()) {
        "GET" -> SesameColors.MethodGet
        "POST" -> SesameColors.MethodPost
        "PUT" -> SesameColors.MethodPut
        "DELETE" -> SesameColors.MethodDelete
        else -> SesameColors.MethodOther
    }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
        Text(
            method.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun statusLabel(code: Int): String = when {
    code == 0 -> "PENDING"
    code in 200..299 -> "$code OK"
    code in 300..399 -> "$code REDIR"
    code in 400..499 -> "$code ERR"
    else -> "$code"
}

// ── Blacklist Sheet ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BlacklistSheet(
    blacklist: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var newDomain by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("过滤配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("包含以下关键词的域名将被排除", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 16.dp))

            OutlinedTextField(
                value = newDomain,
                onValueChange = { newDomain = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入域名关键词") },
                trailingIcon = {
                    IconButton(onClick = { if (newDomain.isNotBlank()) { onAdd(newDomain.trim()); newDomain = "" } }, enabled = newDomain.isNotBlank()) {
                        Icon(Icons.Rounded.Add, "添加")
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(16.dp))

            if (blacklist.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("暂无黑名单", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    blacklist.forEach { domain ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(domain) },
                            trailingIcon = { Icon(Icons.Rounded.Close, "删除", modifier = Modifier.size(16.dp).clickable { onRemove(domain) }) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("推荐过滤", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            val presets = listOf("log.alipay.com", "mdap.alipay.com", "diagnose.alipay.com")
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.filter { !blacklist.contains(it) }.forEach { preset ->
                    AssistChip(onClick = { onAdd(preset) }, label = { Text(preset) }, leadingIcon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp)) })
                }
            }
        }
    }
}

// ── Empty State ──────────────────────────────

@Composable
private fun EmptyState() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.CloudOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))
        Text("还没有捕获到网络包", color = MaterialTheme.colorScheme.outline)
    }
}
