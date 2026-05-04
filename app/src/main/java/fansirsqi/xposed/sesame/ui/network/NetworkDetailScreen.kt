package fansirsqi.xposed.sesame.ui.network

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.ui.theme.app.SesameColors
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
    val clipboardManager = LocalClipboardManager.current
    
    LaunchedEffect(packet.id) {
        viewModel.loadBodies(packet)
    }

    val statusColor = SesameColors.getStatusColor(packet.responseCode)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                TopAppBar(
                    title = {
                        Text(
                            packet.host ?: "数据包详情",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(packet.url ?: "")) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "复制 URL", modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.White,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { 
                                Text(
                                    title, 
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if(pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ) 
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
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
                1 -> BodyTab(bodyFlow = viewModel.requestBody, themeColor = SesameColors.Secondary)
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
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            SectionTitle("基础信息")
            InfoDocumentRow("请求地址", packet.url ?: "-", isFullWidth = true)
            Row {
                Box(modifier = Modifier.weight(1f)) { InfoDocumentRow("方法", packet.method ?: "-") }
                Box(modifier = Modifier.weight(1f)) { InfoDocumentRow("状态码", if(packet.responseCode == 0) "PENDING" else packet.responseCode.toString(), valueColor = statusColor) }
            }
            Row {
                Box(modifier = Modifier.weight(1f)) { InfoDocumentRow("耗时", "${packet.duration}ms") }
                Box(modifier = Modifier.weight(1f)) { InfoDocumentRow("协议", packet.protocol) }
            }
            InfoDocumentRow("开始时间", timeFormat.format(Date(packet.startTime)))
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (!packet.requestHeaders.isNullOrEmpty()) {
            item {
                SectionTitle("请求头")
                packet.requestHeaders?.entries?.sortedBy { it.key }?.forEach { (k, v) -> 
                    InfoDocumentRow(k, v) 
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (!packet.responseHeaders.isNullOrEmpty()) {
            item {
                SectionTitle("响应头")
                packet.responseHeaders?.entries?.sortedBy { it.key }?.forEach { (k, v) -> 
                    InfoDocumentRow(k, v) 
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BodyTab(bodyFlow: StateFlow<String?>, themeColor: Color) {
    val body by bodyFlow.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E4E8))
            ) {
                SelectionContainer {
                    Text(
                        text = body ?: "(Empty / Loading...)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF2D3436)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
        
        if (!body.isNullOrBlank()) {
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(body!!)) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("复制内容")
            }
        }
    }
}

@Composable
private fun ResponseTab(viewModel: NetworkDetailViewModel) {
    val image by viewModel.responseImage.collectAsState()
    val body by viewModel.responseBody.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (image != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E4E8))
                ) {
                    Image(
                        bitmap = image!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        contentScale = ContentScale.Inside
                    )
                }
            } else {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E4E8))
                ) {
                    SelectionContainer {
                        Text(
                            text = body ?: "(Empty / No Content)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFF2D3436)
                            ),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }

        if (!body.isNullOrBlank() && image == null) {
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(body!!)) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SesameColors.Success)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("复制响应")
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun InfoDocumentRow(key: String, value: String, isFullWidth: Boolean = false, valueColor: Color = Color(0xFF2D3436)) {
    Column(modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB2BEC3),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    fontSize = 13.sp
                ),
                maxLines = if(isFullWidth) 10 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}
