package fansirsqi.xposed.sesame.ui.network

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.ui.theme.app.SesameColors
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetworkPacketListScreen(
    viewModel: NetworkPacketViewModel,
    onBack: () -> Unit,
    onPacketClick: (CapturePacket) -> Unit
) {
    val packets by viewModel.displayPackets.collectAsState()
    val stats by viewModel.statistics.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val blacklist by viewModel.blacklist.collectAsState()
    
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBlacklistSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    val appBarBg = MaterialTheme.colorScheme.primaryContainer
    val appBarContent = MaterialTheme.colorScheme.onPrimaryContainer

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            @Composable
            fun AppBarIconWithText(
                icon: @Composable () -> Unit,
                label: String,
                onClick: () -> Unit
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ).padding(horizontal = 8.dp)
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        icon()
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = appBarContent.copy(alpha = 0.7f)
                    )
                }
            }

            TopAppBar(
                title = {
                    if (isSearchActive) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            onSearchClose = {
                                isSearchActive = false
                                viewModel.updateSearchQuery("")
                            }
                        )
                    } else {
                        Column {
                            Text("流量抓包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appBarContent)
                            Text("实时监控应用网络请求", style = MaterialTheme.typography.labelSmall, color = appBarContent.copy(alpha = 0.6f))
                        }
                    }
                },
                navigationIcon = {
                    AppBarIconWithText(
                        icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = appBarContent, modifier = Modifier.size(20.dp)) },
                        label = "返回",
                        onClick = onBack
                    )
                },
                actions = {
                    if (!isSearchActive) {
                        AppBarIconWithText(
                            icon = { Icon(Icons.Rounded.Search, null, tint = appBarContent, modifier = Modifier.size(20.dp)) },
                            label = "搜索",
                            onClick = { isSearchActive = true }
                        )
                    }
                    
                    Box {
                        AppBarIconWithText(
                            icon = { Icon(Icons.Rounded.MoreVert, null, tint = appBarContent, modifier = Modifier.size(20.dp)) },
                            label = "更多",
                            onClick = { showMenu = true }
                        )
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("新建模拟请求", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    val emptyPacket = CapturePacket(
                                        id = UUID.randomUUID().toString(),
                                        url = "https://",
                                        method = "GET",
                                        startTime = System.currentTimeMillis()
                                    )
                                    onPacketClick(emptyPacket)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("历史捕获记录") },
                            leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                            onClick = { 
                                showMenu = false
                                showHistoryDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("生成测试数据") },
                            leadingIcon = { Icon(Icons.Rounded.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                            onClick = {
                                showMenu = false
                                viewModel.addTestData()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("过滤配置") },
                            leadingIcon = { Icon(Icons.Rounded.FilterAlt, contentDescription = null) },
                            onClick = { 
                                showBlacklistSheet = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("自动滚动 (${if(autoScroll) "开" else "关"})") },
                            leadingIcon = { Icon(if(autoScroll) Icons.Rounded.VerticalAlignBottom else Icons.Rounded.VerticalAlignTop, contentDescription = null) },
                            onClick = { 
                                viewModel.toggleAutoScroll()
                                showMenu = false
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("清除当前", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { 
                                viewModel.clearCurrentDateLogs()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("清空所有历史", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { 
                                viewModel.clearAllHistory()
                                showMenu = false
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarBg)
        )
    }
) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Dashboard Summary
            DashboardHeader(stats)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading && packets.isEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), color = MaterialTheme.colorScheme.primary)
                } else if (packets.isEmpty()) {
                    EmptyStateView()
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    
                    // 自动滚动到顶部
                    LaunchedEffect(packets.size) {
                        if (autoScroll && packets.isNotEmpty() && !isLoading) {
                            listState.animateScrollToItem(0)
                        }
                    }

                    // 触底加载更多
                    val shouldLoadMore = remember {
                        derivedStateOf {
                            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                                ?: return@derivedStateOf false
                            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
                        }
                    }

                    LaunchedEffect(shouldLoadMore.value) {
                        if (shouldLoadMore.value && hasMore && !isLoading) {
                            viewModel.loadMore()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(packets, key = { it.id }) { packet ->
                            var showPacketMenu by remember { mutableStateOf(false) }
                            
                            Box {
                                ModernPacketItem(
                                    packet = packet,
                                    onClick = { onPacketClick(packet) },
                                    onLongClick = { showPacketMenu = true }
                                )
                                
                                DropdownMenu(
                                    expanded = showPacketMenu,
                                    onDismissRequest = { showPacketMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("拉黑该域名 (${packet.host})") },
                                        leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            viewModel.toggleBlacklist(packet.host)
                                            showPacketMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("仅看该域名") },
                                        leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
                                        onClick = {
                                            viewModel.updateSearchQuery(packet.host)
                                            showPacketMenu = false
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (hasMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        val historyDates = remember { viewModel.getDailyFolders() }
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("历史捕获记录") },
            text = {
                if (historyDates.isEmpty()) {
                    Text("暂无历史记录", modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(historyDates) { date ->
                            ListItem(
                                headlineContent = { Text(date) },
                                leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    viewModel.loadData(date)
                                    showHistoryDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showBlacklistSheet) {
        BlacklistBottomSheet(
            blacklist = blacklist,
            onDismiss = { showBlacklistSheet = false },
            onAdd = { viewModel.toggleBlacklist(it) },
            onRemove = { viewModel.toggleBlacklist(it) },
            onRename = { old, new -> viewModel.renameBlacklist(old, new) },
            sheetState = sheetState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlacklistBottomSheet(
    blacklist: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    sheetState: SheetState
) {
    var editingDomain by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "过滤配置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }
            
            Text(
                "包含以下关键词的域名将被排除在列表之外 (点击可编辑)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            var newDomain by remember { mutableStateOf("") }
            
            OutlinedTextField(
                value = newDomain,
                onValueChange = { newDomain = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入域名关键词 (如 alipay.com)") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (newDomain.isNotBlank()) {
                                onAdd(newDomain.trim())
                                newDomain = ""
                            }
                        },
                        enabled = newDomain.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加")
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (blacklist.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无黑名单", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    blacklist.forEach { domain ->
                        InputChip(
                            selected = false,
                            onClick = { editingDomain = domain },
                            label = { Text(domain) },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp).clickable { onRemove(domain) }
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            if (editingDomain != null) {
                var text by remember { mutableStateOf(editingDomain!!) }
                AlertDialog(
                    onDismissRequest = { editingDomain = null },
                    title = { Text("编辑过滤关键词") },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (text.isNotBlank()) {
                                    onRename(editingDomain!!, text.trim())
                                }
                                editingDomain = null
                            }
                        ) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingDomain = null }) { Text("取消") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text("推荐过滤", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            val presets = listOf("log.alipay.com", "mdap.alipay.com", "diagnose.alipay.com", "mobilegw.alipay.com")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.filter { !blacklist.contains(it) }.forEach { preset ->
                    AssistChip(
                        onClick = { onAdd(preset) },
                        label = { Text(preset) },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(stats: NetworkPacketViewModel.NetworkStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("总请求", stats.total.toString(), Icons.Rounded.Sync, SesameColors.Primary, Modifier.weight(1f))
        StatCard("成功率", "${stats.successRate}%", Icons.Rounded.Toll, SesameColors.Success, Modifier.weight(1f))
        StatCard("异常", stats.error.toString(), Icons.Rounded.BugReport, SesameColors.Error, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun ModernPacketItem(
    packet: CapturePacket,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val statusColor = SesameColors.getStatusColor(packet.responseCode)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModernMethodBadge(packet.method ?: "GET")
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = packet.host ?: "unknown",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${packet.duration}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = packet.url ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Box(modifier = Modifier.size(4.dp).background(statusColor, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if(packet.responseCode == 0) "PENDING" else packet.responseCode.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontSize = 9.sp
                        )
                    }
                }
                
                if (packet.isImage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(12.dp), tint = SesameColors.Secondary)
                }

                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(packet.startTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
fun ModernMethodBadge(method: String) {
    val color = when (method.uppercase()) {
        "GET" -> SesameColors.MethodGet
        "POST" -> SesameColors.MethodPost
        "PUT" -> SesameColors.MethodPut
        "DELETE" -> SesameColors.MethodDelete
        else -> SesameColors.MethodOther
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = method.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearchClose: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜索请求...", fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = { IconButton(onClick = onSearchClose) { Icon(Icons.Rounded.Close, contentDescription = null) } },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
    )
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("还没有捕获到网络包", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
    }
}
