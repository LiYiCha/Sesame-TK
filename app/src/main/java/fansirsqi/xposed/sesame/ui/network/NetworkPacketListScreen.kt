package fansirsqi.xposed.sesame.ui.network

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.ui.theme.app.SesameColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPacketListScreen(
    viewModel: NetworkPacketViewModel,
    onBack: () -> Unit,
    onPacketClick: (CapturePacket) -> Unit
) {
    val packets by viewModel.displayPackets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val stats by viewModel.statistics.collectAsState()
    
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
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
                            Text("流量抓包", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                            Text("实时监控应用网络请求", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "搜索")
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("历史记录") },
                            leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                            onClick = { 
                                showHistoryDialog = true
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                    
                    LaunchedEffect(packets.size) {
                        if (autoScroll && packets.isNotEmpty()) {
                            listState.animateScrollToItem(0)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(packets, key = { it.id }) { packet ->
                            ModernPacketItem(
                                packet = packet,
                                onClick = { onPacketClick(packet) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
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
fun ModernPacketItem(packet: CapturePacket, onClick: () -> Unit) {
    val statusColor = SesameColors.getStatusColor(packet.responseCode)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
