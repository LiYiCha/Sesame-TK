package fansirsqi.xposed.sesame.ui.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fansirsqi.xposed.sesame.task.otherTask2.SeckillScheduler
import fansirsqi.xposed.sesame.util.Files
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MemberGood(
    val benefitId: String,
    val name: String,
    val itemId: String,
    val points: Int,
    val price: String,
    val skuId: String = "-1"
)

class SeckillActivity : ComponentActivity() {

    private val goodsList = mutableStateListOf<MemberGood>()
    private val isRefreshing = mutableStateOf(false)
    private val currentCategory = mutableStateOf("94000SR2025120515775004")

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isRefreshing.value = false
            when (intent?.action) {
                "fansirsqi.xposed.sesame.fetchMemberGoodsList.success" -> {
                    val deliveryId = intent.getStringExtra("deliveryId") ?: "94000SR2025120515775004"
                    Toast.makeText(this@SeckillActivity, "同步商品列表成功！", Toast.LENGTH_SHORT).show()
                    loadLocalGoods(deliveryId)
                }
                "fansirsqi.xposed.sesame.fetchMemberGoodsList.failed" -> {
                    Toast.makeText(this@SeckillActivity, "同步商品列表失败，请确保支付宝在运行中", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filter = IntentFilter().apply {
            addAction("fansirsqi.xposed.sesame.fetchMemberGoodsList.success")
            addAction("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        loadLocalGoods(currentCategory.value)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SeckillScreen(
                        goodsList = goodsList,
                        isRefreshing = isRefreshing.value,
                        onRefresh = { deliveryId, page ->
                            isRefreshing.value = true
                            currentCategory.value = deliveryId
                            val intent = Intent("com.eg.android.AlipayGphone.sesame.fetchMemberGoodsList").apply {
                                putExtra("deliveryId", deliveryId)
                                putExtra("pageNum", page)
                            }
                            sendBroadcast(intent)
                            Toast.makeText(this, "正在请求同步商品列表...", Toast.LENGTH_SHORT).show()
                        },
                        onTabSelected = { deliveryId ->
                            currentCategory.value = deliveryId
                            loadLocalGoods(deliveryId)
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }

    private fun loadLocalGoods(deliveryId: String) {
        val file = Files.getMemberGoodsListFile(deliveryId)
        if (file.exists()) {
            val content = Files.readFromFile(file)
            if (!content.isNullOrEmpty()) {
                val parsed = parseGoods(content)
                goodsList.clear()
                goodsList.addAll(parsed)
                return
            }
        }
        goodsList.clear()
    }

    private fun parseGoods(jsonStr: String): List<MemberGood> {
        val list = mutableListOf<MemberGood>()
        val seen = mutableSetOf<String>()
        try {
            val root = JSONObject(jsonStr)
            fun extract(obj: Any?) {
                when (obj) {
                    is JSONObject -> {
                        val benefitId = obj.optString("benefitId", "")
                        if (benefitId.isNotEmpty()) {
                            val name = obj.optString("name", "").takeIf { it.isNotEmpty() }
                                ?: obj.optString("benefitIntro", "").takeIf { it.isNotEmpty() }
                                ?: obj.optString("title", "")
                            val itemId = obj.optString("itemId", "")
                            val pointDisplay = obj.optJSONObject("pointPriceForDisplay")
                            var points = pointDisplay?.optInt("minPoint", 0) ?: obj.optInt("points", 0)
                            if (points == 0) {
                                points = obj.optInt("pointPrice", 0)
                            }
                            if (points == 0) {
                                points = pointDisplay?.optInt("maxPoint", 0) ?: 0
                            }
                            var price = pointDisplay?.optString("minAmount") 
                                ?: obj.optString("priceYuan").takeIf { it.isNotEmpty() }
                                ?: obj.optString("priceCent").takeIf { it.isNotEmpty() }
                                ?: "0.00"
                            
                            var skuId = "-1"
                            val simpleSkus = obj.optJSONArray("simpleSkus")
                            if (simpleSkus != null && simpleSkus.length() > 0) {
                                val firstSku = simpleSkus.optJSONObject(0)
                                if (firstSku != null) {
                                    skuId = firstSku.optString("sku_id", "").takeIf { it.isNotEmpty() }
                                        ?: firstSku.optString("skuId", "-1")
                                }
                            }
                            if (skuId == "-1") {
                                val skuInfoList = obj.optJSONArray("skuInfoList")
                                if (skuInfoList != null && skuInfoList.length() > 0) {
                                    val firstSku = skuInfoList.optJSONObject(0)
                                    if (firstSku != null) {
                                        skuId = firstSku.optString("skuId", "-1")
                                    }
                                }
                            }

                            if (itemId.isNotEmpty() && !seen.contains(benefitId)) {
                                seen.add(benefitId)
                                list.add(MemberGood(benefitId, name, itemId, points, price, skuId))
                            }
                        }
                        obj.keys().forEach { key ->
                            extract(obj.get(key))
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until obj.length()) {
                            extract(obj.get(i))
                        }
                    }
                }
            }
            extract(root)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeckillScreen(
    goodsList: List<MemberGood>,
    isRefreshing: Boolean,
    onRefresh: (deliveryId: String, page: Int) -> Unit,
    onTabSelected: (deliveryId: String) -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf(
        "日常抢兑" to "94000SR2025120515775004",
        "万分好物" to "94000SR2025120515776001",
        "联名周边" to "94000SR2025120515776002",
        "全部商品" to "94000SR2023102305988003"
    )

    var selectedTabIndex by remember { mutableStateOf(0) }
    var itemId by remember { mutableStateOf("") }
    var verifyPoint by remember { mutableStateOf("") }
    var skuId by remember { mutableStateOf("-1") }
    var generatedUrl by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var currentPage by remember { mutableStateOf(1) }

    // Dialog state
    var showScheduleDialog by remember { mutableStateOf(false) }
    var scheduleItemId by remember { mutableStateOf("") }
    var scheduleSkuId by remember { mutableStateOf("-1") }
    var schedulePoints by remember { mutableStateOf("") }
    var scheduleName by remember { mutableStateOf("") }
    var scheduleTimeStr by remember { mutableStateOf("") }
    var scheduleType by remember { mutableStateOf("H5") } // "H5" or "RPC"

    var seckillTasks by remember { mutableStateOf(listOf<JSONObject>()) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val loadTasks = {
        val list = mutableListOf<JSONObject>()
        try {
            val file = SeckillScheduler.getSeckillTasksFile()
            if (file.exists()) {
                val content = Files.readFromFile(file)
                if (!content.isNullOrEmpty()) {
                    val ja = JSONArray(content)
                    for (i in 0 until ja.length()) {
                        val jo = ja.optJSONObject(i)
                        if (jo != null) list.add(jo)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        seckillTasks = list
    }

    val saveTasks = { newTasks: List<JSONObject> ->
        try {
            val file = SeckillScheduler.getSeckillTasksFile()
            val ja = JSONArray()
            newTasks.forEach { ja.put(it) }
            Files.write2File(ja.toString(), file)
            seckillTasks = newTasks
            context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.syncSeckillTasks"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        loadTasks()
    }

    LaunchedEffect(selectedTabIndex) {
        currentPage = 1
        onTabSelected(categories[selectedTabIndex].second)
    }

    LaunchedEffect(itemId, verifyPoint, skuId) {
        if (itemId.isNotEmpty() && verifyPoint.isNotEmpty()) {
            val orderItemsJson = "[{\"itemId\":\"$itemId\",\"skuId\":\"$skuId\",\"number\":1}]"
            val encodedOrderItems = Uri.encode(orderItemsJson)
            val extJson = "{\"requestSourceInfo\":\"来源\"}"
            val encodedExtJson = Uri.encode(extJson)
            val tmallUrl = "https://pages.tmall.com/wow/wt/act/lm-pages?env=&extJson=$encodedExtJson&orderItems=$encodedOrderItems&verifyPoint=$verifyPoint&wh_page=buy"
            generatedUrl = "https://pages.tmall.com/wow/z/wt/act/alipay-login?goToUrl=${Uri.encode(tmallUrl)}"
        } else {
            generatedUrl = ""
        }
    }

    // Filtered list
    val filteredGoods = remember(goodsList, searchQuery) {
        if (searchQuery.isEmpty()) {
            goodsList
        } else {
            goodsList.filter { it.name.contains(searchQuery, ignoreCase = true) || it.itemId.contains(searchQuery) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会员抢兑直链与秒杀", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onRefresh(categories[selectedTabIndex].second, currentPage) },
                        enabled = !isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "同步商品")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Categories Tab
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEachIndexed { index, (name, _) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(name, fontSize = 14.sp) }
                    )
                }
            }

            // Outer Scrollable Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Manual input card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("手动配置商品参数", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = itemId,
                            onValueChange = { itemId = it },
                            label = { Text("商品 ID / 权益 ID (itemId)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = verifyPoint,
                                onValueChange = { verifyPoint = it },
                                label = { Text("所需积分 (points)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = skuId,
                                onValueChange = { skuId = it },
                                label = { Text("规格 ID (skuId)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        
                        if (verifyPoint.isNotEmpty() && itemId.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("快捷秒杀控制面板已就绪", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val alipaySchemeUrl = "alipays://platformapi/startapp?appId=20000067&url=${Uri.encode(generatedUrl)}"
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alipaySchemeUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "无法唤起支付宝: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("立即跳转", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(generatedUrl))
                                        Toast.makeText(context, "直链已复制！", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("复制直链", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        scheduleItemId = itemId
                                        scheduleSkuId = skuId
                                        schedulePoints = verifyPoint
                                        scheduleName = "自定义秒杀商品"
                                        scheduleType = "H5"
                                        val cal = Calendar.getInstance()
                                        cal.set(Calendar.MINUTE, 0)
                                        cal.set(Calendar.SECOND, 0)
                                        scheduleTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.time)
                                        showScheduleDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("定时秒杀", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索本地缓存的商品...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                // Goods List Container
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredGoods.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (isRefreshing) "正在同步支付宝商品列表..." else "没有找到商品", color = Color.Gray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onRefresh(categories[selectedTabIndex].second, currentPage) },
                                    enabled = !isRefreshing
                                ) {
                                    Text("同步列表")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(filteredGoods) { good ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            itemId = good.itemId
                                            verifyPoint = good.points.toString()
                                            skuId = good.skuId
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(good.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row {
                                            Text("ID: ${good.itemId}", fontSize = 10.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("积分: ${good.points}", fontSize = 10.sp, color = Color.Gray)
                                            if (good.skuId != "-1") {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("SKU: ${good.skuId}", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                val orderItemsJson = "[{\"itemId\":\"${good.itemId}\",\"skuId\":\"${good.skuId}\",\"number\":1}]"
                                                val encodedOrderItems = Uri.encode(orderItemsJson)
                                                val extJson = "{\"requestSourceInfo\":\"来源\"}"
                                                val encodedExtJson = Uri.encode(extJson)
                                                val tmallUrl = "https://pages.tmall.com/wow/wt/act/lm-pages?env=&extJson=$encodedExtJson&orderItems=$encodedOrderItems&verifyPoint=${good.points}&wh_page=buy"
                                                val finalUrl = "https://pages.tmall.com/wow/z/wt/act/alipay-login?goToUrl=${Uri.encode(tmallUrl)}"
                                                val alipaySchemeUrl = "alipays://platformapi/startapp?appId=20000067&url=${Uri.encode(finalUrl)}"
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alipaySchemeUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "无法唤起支付宝: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Text("直达", fontSize = 10.sp)
                                        }
                                        Button(
                                            onClick = {
                                                scheduleItemId = good.itemId
                                                scheduleSkuId = good.skuId
                                                schedulePoints = good.points.toString()
                                                scheduleName = good.name
                                                scheduleType = "H5"
                                                val cal = Calendar.getInstance()
                                                cal.set(Calendar.MINUTE, 0)
                                                cal.set(Calendar.SECOND, 0)
                                                scheduleTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.time)
                                                showScheduleDialog = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                        ) {
                                            Text("秒杀", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pagination (For master list "全部商品")
                if (selectedTabIndex == 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (currentPage > 1) {
                                    currentPage--
                                    onRefresh(categories[selectedTabIndex].second, currentPage)
                                }
                            },
                            enabled = currentPage > 1 && !isRefreshing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("上一页", fontSize = 11.sp)
                        }
                        Text("第 $currentPage 页", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                currentPage++
                                onRefresh(categories[selectedTabIndex].second, currentPage)
                            },
                            enabled = !isRefreshing && goodsList.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("下一页", fontSize = 11.sp)
                        }
                    }
                }

                // Active Tasks List Card
                if (seckillTasks.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .heightIn(max = 180.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⏰ 计划中的秒杀任务：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(seckillTasks) { task ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(task.optString("name", "未命名"), fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                            Text("时间: ${task.optString("seckillTime")} | 模式: ${task.optString("type")} | ID: ${task.optString("itemId")}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        IconButton(
                                            onClick = {
                                                val newTasks = seckillTasks.filter { it !== task }
                                                saveTasks(newTasks)
                                                Toast.makeText(context, "秒杀任务已取消", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "取消任务", tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Schedule Config Dialog Overlay
    if (showScheduleDialog) {
        Dialog(onDismissRequest = { showScheduleDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("设定秒杀定时任务", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("商品: $scheduleName", fontSize = 13.sp, maxLines = 1)
                    Text("ID: $scheduleItemId | SKU: $scheduleSkuId", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode Selector Row
                    Text("选择秒杀模式：", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { scheduleType = "H5" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scheduleType == "H5") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (scheduleType == "H5") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("前台 H5 (实物)", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { scheduleType = "RPC" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scheduleType == "RPC") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (scheduleType == "RPC") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("后台 RPC (虚拟)", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = scheduleTimeStr,
                        onValueChange = { scheduleTimeStr = it },
                        label = { Text("设定秒杀时间 (yyyy-MM-dd HH:mm:ss)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Quick Time Presets Helper
                    val presetTime = { offset: Int, hour: Int ->
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DAY_OF_YEAR, offset)
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        scheduleTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.time)
                    }

                    Text("快速选择时间：", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Presets Layout
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { presetTime(0, 10) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.weight(1f).height(28.dp)) {
                                Text("今日 10:00", fontSize = 10.sp)
                            }
                            Button(onClick = { presetTime(0, 14) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.weight(1f).height(28.dp)) {
                                Text("今日 14:00", fontSize = 10.sp)
                            }
                            Button(onClick = { presetTime(0, 20) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.weight(1f).height(28.dp)) {
                                Text("今日 20:00", fontSize = 10.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { presetTime(1, 10) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.weight(1f).height(28.dp)) {
                                Text("明日 10:00", fontSize = 10.sp)
                            }
                            Button(onClick = { presetTime(1, 14) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.weight(1f).height(28.dp)) {
                                Text("明日 14:00", fontSize = 10.sp)
                            }
                            Button(onClick = { presetTime(1, 20) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.weight(1f).height(28.dp)) {
                                Text("明日 20:00", fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showScheduleDialog = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                    val date = sdf.parse(scheduleTimeStr)
                                    if (date == null) {
                                        Toast.makeText(context, "日期格式错误", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val timeMillis = date.time
                                    if (timeMillis <= System.currentTimeMillis()) {
                                        Toast.makeText(context, "设定的时间不能早于当前时间", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    val newTask = JSONObject().apply {
                                        put("itemId", scheduleItemId)
                                        put("skuId", scheduleSkuId)
                                        put("points", schedulePoints.toIntOrNull() ?: 0)
                                        put("name", scheduleName)
                                        put("seckillTime", scheduleTimeStr)
                                        put("timeMillis", timeMillis)
                                        put("type", scheduleType)
                                    }

                                    val list = seckillTasks.toMutableList()
                                    // Remove duplicates of same itemId and time
                                    list.removeAll { it.optString("itemId") == scheduleItemId && it.optLong("timeMillis") == timeMillis }
                                    list.add(newTask)
                                    // Sort by time
                                    list.sortBy { it.optLong("timeMillis") }
                                    
                                    saveTasks(list)
                                    showScheduleDialog = false
                                    Toast.makeText(context, "⏰ 秒杀任务排期成功！", Toast.LENGTH_SHORT).show()

                                } catch (e: Exception) {
                                    Toast.makeText(context, "解析错误: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("确认排期")
                        }
                    }
                }
            }
        }
    }
}
