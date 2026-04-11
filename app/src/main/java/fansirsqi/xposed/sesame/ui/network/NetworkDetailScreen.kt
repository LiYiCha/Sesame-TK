package fansirsqi.xposed.sesame.ui.network

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDetailScreen(
    viewModel: NetworkDetailViewModel,
    packet: CapturePacket,
    onBack: () -> Unit
) {
    val tabs = listOf("概览", "请求", "响应")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(packet.id) {
        viewModel.loadBodies(packet)
    }

    val statusColor = when {
        packet.responseCode in 200..299 -> Color(0xFF4CAF50)
        packet.responseCode in 400..499 -> Color(0xFFFF9800)
        packet.responseCode >= 500 -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.outline
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    packet.host ?: "数据包详情",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    packet.url ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = { Text(title, fontWeight = if(pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { page ->
            when (page) {
                0 -> OverviewTab(packet, statusColor)
                1 -> BodyTab(title = "Request Content", bodyFlow = viewModel.requestBody)
                2 -> ResponseTab(viewModel)
            }
        }
    }
}

@Composable
private fun OverviewTab(packet: CapturePacket, statusColor: Color) {
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            DetailCard(title = "基础状态", accentColor = statusColor) {
                InfoRow("URL", packet.url ?: "-", isFullWidth = true)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { InfoRow("Method", packet.method ?: "-") }
                    Box(modifier = Modifier.weight(1f)) { InfoRow("Status", packet.responseCode.toString(), valueColor = statusColor) }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { InfoRow("Duration", "${packet.duration}ms") }
                    Box(modifier = Modifier.weight(1f)) { InfoRow("Protocol", packet.protocol) }
                }
                InfoRow("Time", timeFormat.format(Date(packet.startTime)))
            }
        }

        if (!packet.requestHeaders.isNullOrEmpty()) {
            item {
                DetailCard(title = "请求头 (Request Headers)", accentColor = MaterialTheme.colorScheme.primary) {
                    packet.requestHeaders?.entries?.sortedBy { it.key }?.forEach { (k, v) -> 
                        InfoRow(k, v) 
                    }
                }
            }
        }

        if (!packet.responseHeaders.isNullOrEmpty()) {
            item {
                DetailCard(title = "响应头 (Response Headers)", accentColor = MaterialTheme.colorScheme.tertiary) {
                    packet.responseHeaders?.entries?.sortedBy { it.key }?.forEach { (k, v) -> 
                        InfoRow(k, v) 
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun BodyTab(title: String, bodyFlow: StateFlow<String?>) {
    val body by bodyFlow.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = body ?: "(Empty / Loading...)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ResponseTab(viewModel: NetworkDetailViewModel) {
    val image by viewModel.responseImage.collectAsState()
    val body by viewModel.responseBody.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (image != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = image!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().background(Color.White),
                    contentScale = ContentScale.Inside
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = body ?: "(Empty / No Content)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, accentColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(accentColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String, isFullWidth: Boolean = false, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = valueColor
                ),
                maxLines = if(isFullWidth) 10 else 2
            )
        }
    }
}
