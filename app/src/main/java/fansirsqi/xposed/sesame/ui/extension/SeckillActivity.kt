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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MemberGood(
    val benefitId: String,
    val name: String,
    val itemId: String,
    val points: Int,
    val price: String,
    val skuId: String = "-1",
    val actionUrl: String = "",
    val skuIds: List<String> = emptyList() // Multi-specs support (formatted as "skuId|price|points")
)

class SeckillActivity : ComponentActivity() {

    private val goodsList = mutableStateListOf<MemberGood>()
    private val isRefreshing = mutableStateOf(false)
    private val currentCategory = mutableStateOf("94000SR2025120515775004")
    private val currentPage = mutableStateOf(1)

    // Unified State at Activity Level
    private val itemId = mutableStateOf("")
    private val verifyPoint = mutableStateOf("")
    private val skuId = mutableStateOf("-1")
    private val quantityNumber = mutableStateOf("1")
    private val activeBenefitId = mutableStateOf("")
    private val selectedSkuIds = mutableStateOf<List<String>>(emptyList())

    // Dialog states
    private val showScheduleDialog = mutableStateOf(false)
    private val scheduleItemId = mutableStateOf("")
    private val scheduleSkuId = mutableStateOf("-1")
    private val schedulePoints = mutableStateOf("")
    private val scheduleName = mutableStateOf("")
    private val scheduleTimeStr = mutableStateOf("")
    private val scheduleType = mutableStateOf("H5")

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
                    val reason = intent.getStringExtra("reason")
                    if ("no_more" == reason) {
                        Toast.makeText(this@SeckillActivity, "已是最后一页 / 没有更多商品了", Toast.LENGTH_SHORT).show()
                        if (currentPage.value > 1) {
                            currentPage.value--
                        }
                    } else {
                        Toast.makeText(this@SeckillActivity, "同步商品列表失败，请确保支付宝在运行中", Toast.LENGTH_LONG).show()
                    }
                }
                "fansirsqi.xposed.sesame.queryBenefitDetail.success" -> {
                    val benefitId = intent.getStringExtra("benefitId")
                    val fetchedSkuId = intent.getStringExtra("skuId")
                    val fetchedSkuIds = intent.getStringArrayListExtra("skuIds") ?: emptyList<String>()
                    if (benefitId != null && fetchedSkuId != null) {
                        Toast.makeText(this@SeckillActivity, "已自动获取规格列表: 共有 ${fetchedSkuIds.size} 个规格", Toast.LENGTH_SHORT).show()
                        
                        // Update active UI states directly
                        if (activeBenefitId.value == benefitId) {
                            skuId.value = fetchedSkuId
                            scheduleSkuId.value = fetchedSkuId
                            selectedSkuIds.value = fetchedSkuIds
                            if (fetchedSkuIds.isNotEmpty()) {
                                val parts = fetchedSkuIds[0].split("|")
                                if (parts.size >= 3) {
                                    verifyPoint.value = parts[2]
                                    schedulePoints.value = parts[2]
                                }
                            }
                        }

                        var updated = false
                        for (i in 0 until goodsList.size) {
                            val good = goodsList[i]
                            if (good.benefitId == benefitId) {
                                goodsList[i] = good.copy(skuId = fetchedSkuId, skuIds = fetchedSkuIds)
                                updated = true
                            }
                        }
                        if (updated) {
                            saveLocalGoodsWithUpdatedSku(currentCategory.value)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filter = IntentFilter().apply {
            addAction("fansirsqi.xposed.sesame.fetchMemberGoodsList.success")
            addAction("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed")
            addAction("fansirsqi.xposed.sesame.queryBenefitDetail.success")
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
                        currentPage = currentPage.value,
                        onPageChange = { currentPage.value = it },
                        itemId = itemId.value,
                        onItemIdChange = { itemId.value = it },
                        verifyPoint = verifyPoint.value,
                        onVerifyPointChange = { verifyPoint.value = it },
                        skuId = skuId.value,
                        onSkuIdChange = { skuId.value = it },
                        quantityNumber = quantityNumber.value,
                        onQuantityNumberChange = { quantityNumber.value = it },
                        activeBenefitId = activeBenefitId.value,
                        onActiveBenefitIdChange = { activeBenefitId.value = it },
                        selectedSkuIds = selectedSkuIds.value,
                        onSelectedSkuIdsChange = { selectedSkuIds.value = it },
                        showScheduleDialog = showScheduleDialog.value,
                        onShowScheduleDialogChange = { showScheduleDialog.value = it },
                        scheduleItemId = scheduleItemId.value,
                        onScheduleItemIdChange = { scheduleItemId.value = it },
                        scheduleSkuId = scheduleSkuId.value,
                        onScheduleSkuIdChange = { scheduleSkuId.value = it },
                        schedulePoints = schedulePoints.value,
                        onSchedulePointsChange = { schedulePoints.value = it },
                        scheduleName = scheduleName.value,
                        onScheduleNameChange = { scheduleName.value = it },
                        scheduleTimeStr = scheduleTimeStr.value,
                        onScheduleTimeStrChange = { scheduleTimeStr.value = it },
                        scheduleType = scheduleType.value,
                        onScheduleTypeChange = { scheduleType.value = it },
                        onRefresh = { deliveryId, page ->
                            isRefreshing.value = true
                            currentCategory.value = deliveryId
                            val intent = Intent("com.eg.android.AlipayGphone.sesame.fetchMemberGoodsList").apply {
                                putExtra("deliveryId", deliveryId)
                                putExtra("pageNum", page)
                            }
                            sendBroadcast(intent)
                            Toast.makeText(this@SeckillActivity, "正在请求同步商品列表...", Toast.LENGTH_SHORT).show()
                        },
                        onTabSelected = { deliveryId ->
                            currentCategory.value = deliveryId
                            loadLocalGoods(deliveryId)
                            // Reset selected spec list when switching tabs
                            itemId.value = ""
                            verifyPoint.value = ""
                            skuId.value = "-1"
                            selectedSkuIds.value = emptyList()
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

    private fun saveLocalGoodsWithUpdatedSku(deliveryId: String) {
        try {
            val file = Files.getMemberGoodsListFile(deliveryId)
            val root = JSONObject()
            val ja = JSONArray()
            goodsList.forEach { good ->
                val jo = JSONObject().apply {
                    put("benefitId", good.benefitId)
                    put("name", good.name)
                    put("itemId", good.itemId)
                    put("pointPrice", good.points)
                    put("priceYuan", good.price)
                    put("actionUrl", good.actionUrl)
                    
                    val skus = JSONArray().apply {
                        if (good.skuIds.isNotEmpty()) {
                            good.skuIds.forEach { specStr ->
                                val parts = specStr.split("|")
                                if (parts.size >= 3) {
                                    put(JSONObject().apply {
                                        put("skuId", parts[0])
                                        put("price", parts[1])
                                        put("points", parts[2])
                                    })
                                }
                            }
                        } else {
                            put(JSONObject().apply {
                                put("skuId", good.skuId)
                            })
                        }
                    }
                    put("skuInfoList", skus)
                }
                ja.put(jo)
            }
            root.put("benefits", ja)
            Files.write2File(root.toString(), file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                            
                            // Robust Multi-tier point extraction (fixing Elvis 0 bug)
                            var points = 0
                            val pointDisplay = obj.optJSONObject("pointPriceForDisplay")
                            if (pointDisplay != null) {
                                points = pointDisplay.optInt("minPoint", 0)
                                if (points == 0) {
                                    points = pointDisplay.optInt("maxPoint", 0)
                                }
                            }
                            if (points == 0) {
                                points = obj.optInt("points", 0)
                            }
                            if (points == 0) {
                                points = obj.optInt("pointPrice", 0)
                            }
                            if (points == 0) {
                                val pricePresentation = obj.optJSONObject("pricePresentation")
                                if (pricePresentation != null) {
                                    points = pricePresentation.optInt("point", 0)
                                }
                            }
                            if (points == 0) {
                                val purePoint = obj.optJSONObject("purePointForDisplay")
                                if (purePoint != null) {
                                    val levels = listOf("primary", "golden", "platinum", "diamond")
                                    for (lvl in levels) {
                                        val lvlObj = purePoint.optJSONObject(lvl)
                                        if (lvlObj != null) {
                                            points = lvlObj.optInt("minPoint", 0)
                                            if (points == 0) {
                                                points = lvlObj.optInt("maxPoint", 0)
                                            }
                                            if (points != 0) break
                                        }
                                    }
                                }
                            }

                            // Robust Multi-tier price extraction
                            var price = ""
                            if (pointDisplay != null) {
                                price = pointDisplay.optString("minAmount", "")
                                if (price.isEmpty()) {
                                    price = pointDisplay.optString("maxAmount", "")
                                }
                            }
                            if (price.isEmpty()) {
                                price = obj.optString("priceYuan", "")
                            }
                            if (price.isEmpty()) {
                                price = obj.optString("channelPrice", "")
                            }
                            if (price.isEmpty()) {
                                val pricePresentation = obj.optJSONObject("pricePresentation")
                                if (pricePresentation != null) {
                                    price = pricePresentation.optString("yuan", "")
                                }
                            }
                            if (price.isEmpty()) {
                                price = "0.00"
                            }
                            
                            var actionUrl = obj.optString("actionUrl", "")
                            if (actionUrl.isEmpty()) {
                                val linkInfo = obj.optJSONObject("linkInfo")
                                if (linkInfo != null) {
                                    actionUrl = linkInfo.optString("jumpUrl", "")
                                }
                            }
                            
                            var skuId = "-1"
                            val skuIdsList = mutableListOf<String>()
                            
                            val skuInfoList = obj.optJSONArray("skuInfoList")
                            if (skuInfoList != null && skuInfoList.length() > 0) {
                                for (k in 0 until skuInfoList.length()) {
                                    val skuObj = skuInfoList.optJSONObject(k)
                                    if (skuObj != null) {
                                        val sId = skuObj.optString("skuId", "-1")
                                        if (sId != "-1") {
                                            if (skuId == "-1") {
                                                skuId = sId
                                            }
                                            val sPrice = skuObj.optString("price", price)
                                            val sPoints = skuObj.optString("points", points.toString())
                                            skuIdsList.add("$sId|$sPrice|$sPoints")
                                        }
                                    }
                                }
                            }
                            
                            if (skuId == "-1") {
                                val simpleSkus = obj.optJSONArray("simpleSkus")
                                if (simpleSkus != null && simpleSkus.length() > 0) {
                                    val firstSku = simpleSkus.optJSONObject(0)
                                    if (firstSku != null) {
                                        skuId = firstSku.optString("sku_id", "").takeIf { it.isNotEmpty() }
                                            ?: firstSku.optString("skuId", "-1")
                                        skuIdsList.add("$skuId|$price|$points")
                                    }
                                }
                            }

                            if (itemId.isNotEmpty() && !seen.contains(benefitId)) {
                                seen.add(benefitId)
                                list.add(MemberGood(benefitId, name, itemId, points, price, skuId, actionUrl, skuIdsList))
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
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    itemId: String,
    onItemIdChange: (String) -> Unit,
    verifyPoint: String,
    onVerifyPointChange: (String) -> Unit,
    skuId: String,
    onSkuIdChange: (String) -> Unit,
    quantityNumber: String,
    onQuantityNumberChange: (String) -> Unit,
    activeBenefitId: String,
    onActiveBenefitIdChange: (String) -> Unit,
    selectedSkuIds: List<String>,
    onSelectedSkuIdsChange: (List<String>) -> Unit,
    showScheduleDialog: Boolean,
    onShowScheduleDialogChange: (Boolean) -> Unit,
    scheduleItemId: String,
    onScheduleItemIdChange: (String) -> Unit,
    scheduleSkuId: String,
    onScheduleSkuIdChange: (String) -> Unit,
    schedulePoints: String,
    onSchedulePointsChange: (String) -> Unit,
    scheduleName: String,
    onScheduleNameChange: (String) -> Unit,
    scheduleTimeStr: String,
    onScheduleTimeStrChange: (String) -> Unit,
    scheduleType: String,
    onScheduleTypeChange: (String) -> Unit,
    onRefresh: (deliveryId: String, page: Int) -> Unit,
    onTabSelected: (deliveryId: String) -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf(
        "精选日常" to "94000SR2025110615412003",
        "万分好物" to "94000SR2025110615412004",
        "联名周边" to "94000SR2025120515776002",
        "全部商品" to "94000SR2023102305988003"
    )

    var selectedTabIndex by remember { mutableStateOf(0) }
    var generatedUrl by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

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
        onPageChange(1)
        onTabSelected(categories[selectedTabIndex].second)
    }

    LaunchedEffect(itemId, verifyPoint, skuId, quantityNumber) {
        if (itemId.isNotEmpty() && verifyPoint.isNotEmpty()) {
            val numVal = quantityNumber.toIntOrNull() ?: 1
            val orderItemsJson = "[{\"itemId\":\"$itemId\",\"skuId\":\"$skuId\",\"number\":$numVal}]"
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
                            onValueChange = onItemIdChange,
                            label = { Text("商品 ID / 权益 ID (itemId)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = verifyPoint,
                                onValueChange = onVerifyPointChange,
                                label = { Text("所需积分") },
                                modifier = Modifier.weight(1.1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = skuId,
                                onValueChange = onSkuIdChange,
                                label = { Text("规格 ID") },
                                modifier = Modifier.weight(1.1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = quantityNumber,
                                onValueChange = onQuantityNumberChange,
                                label = { Text("数量") },
                                modifier = Modifier.weight(0.8f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        // Specification Chips Selector
                        if (selectedSkuIds.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("已发现该商品有多个规格，点击快速选择：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedSkuIds.forEach { specStr ->
                                    val parts = specStr.split("|")
                                    if (parts.size >= 3) {
                                        val sId = parts[0]
                                        val sPrice = parts[1]
                                        val sPoints = parts[2]
                                        val isSelected = skuId == sId
                                        
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                onSkuIdChange(sId)
                                                onVerifyPointChange(sPoints)
                                                // Sync to schedule dialog if visible
                                                if (showScheduleDialog) {
                                                    onScheduleSkuIdChange(sId)
                                                    onSchedulePointsChange(sPoints)
                                                }
                                            },
                                            label = { Text("${sPoints}分 + ${sPrice}元", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
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
                                        onScheduleItemIdChange(itemId)
                                        onScheduleSkuIdChange(skuId)
                                        onSchedulePointsChange(verifyPoint)
                                        onScheduleNameChange("自定义秒杀商品")
                                        onScheduleTypeChange("H5")
                                        val cal = Calendar.getInstance()
                                        cal.set(Calendar.MINUTE, 0)
                                        cal.set(Calendar.SECOND, 0)
                                        onScheduleTimeStrChange(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.time))
                                        onShowScheduleDialogChange(true)
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
                                            onItemIdChange(good.itemId)
                                            onVerifyPointChange(good.points.toString())
                                            onSkuIdChange(good.skuId)
                                            onActiveBenefitIdChange(good.benefitId)
                                            onSelectedSkuIdsChange(good.skuIds)
                                            
                                            // Trigger automatic background SKU lookup
                                            if (good.skuId == "-1") {
                                                Toast.makeText(context, "正在查询规格...", Toast.LENGTH_SHORT).show()
                                                val intent = Intent("com.eg.android.AlipayGphone.sesame.queryBenefitDetail").apply {
                                                    putExtra("benefitId", good.benefitId)
                                                }
                                                context.sendBroadcast(intent)
                                            }
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
                                            Text("积分: ${good.points} + ${good.price}元", fontSize = 10.sp, color = Color.Gray)
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
                                                val isPhysical = good.itemId.all { it.isDigit() }
                                                val targetUrl = if (isPhysical && good.skuId != "-1") {
                                                    val numVal = quantityNumber.toIntOrNull() ?: 1
                                                    val orderItemsJson = "[{\"itemId\":\"${good.itemId}\",\"skuId\":\"${good.skuId}\",\"number\":$numVal}]"
                                                    val encodedOrderItems = Uri.encode(orderItemsJson)
                                                    val extJson = "{\"requestSourceInfo\":\"来源\"}"
                                                    val encodedExtJson = Uri.encode(extJson)
                                                    val tmallUrl = "https://pages.tmall.com/wow/wt/act/lm-pages?env=&extJson=$encodedExtJson&orderItems=$encodedOrderItems&verifyPoint=${good.points}&wh_page=buy"
                                                    "https://pages.tmall.com/wow/z/wt/act/alipay-login?goToUrl=${Uri.encode(tmallUrl)}"
                                                } else {
                                                    if (good.actionUrl.isNotEmpty()) good.actionUrl else ""
                                                }
                                                
                                                val scheme = if (targetUrl.startsWith("alipays://")) {
                                                    targetUrl
                                                } else {
                                                    "alipays://platformapi/startapp?appId=20000067&url=${Uri.encode(targetUrl)}"
                                                }
                                                
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).apply {
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
                                                onScheduleItemIdChange(good.itemId)
                                                onScheduleSkuIdChange(good.skuId)
                                                onSchedulePointsChange(good.points.toString())
                                                onScheduleNameChange(good.name)
                                                onScheduleTypeChange("H5")
                                                onActiveBenefitIdChange(good.benefitId)
                                                onSelectedSkuIdsChange(good.skuIds)
                                                
                                                // Trigger background SKU resolution
                                                if (good.skuId == "-1") {
                                                    val intent = Intent("com.eg.android.AlipayGphone.sesame.queryBenefitDetail").apply {
                                                        putExtra("benefitId", good.benefitId)
                                                    }
                                                    context.sendBroadcast(intent)
                                                }
                                                
                                                val cal = Calendar.getInstance()
                                                cal.set(Calendar.MINUTE, 0)
                                                cal.set(Calendar.SECOND, 0)
                                                onScheduleTimeStrChange(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.time))
                                                onShowScheduleDialogChange(true)
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
                                    val prevPage = currentPage - 1
                                    onPageChange(prevPage)
                                    onRefresh(categories[selectedTabIndex].second, prevPage)
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
                                val nextPage = currentPage + 1
                                onPageChange(nextPage)
                                onRefresh(categories[selectedTabIndex].second, nextPage)
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
                                            Text("时间: ${task.optString("seckillTime")} | 模式: ${task.optString("type")} | 数量: ${task.optInt("number", 1)} | ID: ${task.optString("itemId")}", fontSize = 10.sp, color = Color.Gray)
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
        Dialog(onDismissRequest = { onShowScheduleDialogChange(false) }) {
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
                            onClick = { onScheduleTypeChange("H5") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scheduleType == "H5") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (scheduleType == "H5") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("前台 H5 (实物)", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onScheduleTypeChange("RPC") },
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
                        onValueChange = onScheduleTimeStrChange,
                        label = { Text("设定秒杀时间 (yyyy-MM-dd HH:mm:ss)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Specification Chips Selector inside Schedule Dialog
                    if (selectedSkuIds.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("选择秒杀规格：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedSkuIds.forEach { specStr ->
                                val parts = specStr.split("|")
                                if (parts.size >= 3) {
                                    val sId = parts[0]
                                    val sPrice = parts[1]
                                    val sPoints = parts[2]
                                    val isSelected = scheduleSkuId == sId
                                    
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            onScheduleSkuIdChange(sId)
                                            onSchedulePointsChange(sPoints)
                                            // Sync back to manual input fields
                                            onSkuIdChange(sId)
                                            onVerifyPointChange(sPoints)
                                        },
                                        label = { Text("${sPoints}分 + ${sPrice}元", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Quick Time Presets Helper
                    val presetTime = { offset: Int, hour: Int ->
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DAY_OF_YEAR, offset)
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        onScheduleTimeStrChange(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.time))
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
                        TextButton(onClick = { onShowScheduleDialogChange(false) }) {
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

                                    val numVal = quantityNumber.toIntOrNull() ?: 1
                                    val newTask = JSONObject().apply {
                                        put("itemId", scheduleItemId)
                                        put("skuId", scheduleSkuId)
                                        put("points", schedulePoints.toIntOrNull() ?: 0)
                                        put("name", scheduleName)
                                        put("seckillTime", scheduleTimeStr)
                                        put("timeMillis", timeMillis)
                                        put("type", scheduleType)
                                        put("number", numVal)
                                    }

                                    val list = seckillTasks.toMutableList()
                                    // Remove duplicates of same itemId and time
                                    list.removeAll { it.optString("itemId") == scheduleItemId && it.optLong("timeMillis") == timeMillis }
                                    list.add(newTask)
                                    // Sort by time
                                    list.sortBy { it.optLong("timeMillis") }
                                    
                                    saveTasks(list)
                                    onShowScheduleDialogChange(false)
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
