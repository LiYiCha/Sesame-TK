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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MemberGood(
    val benefitId: String,
    val name: String,
    val itemId: String,
    val points: Int,
    val price: String
)

class SeckillActivity : ComponentActivity() {

    private val goodsList = mutableStateListOf<MemberGood>()
    private val isRefreshing = mutableStateOf(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isRefreshing.value = false
            when (intent?.action) {
                "fansirsqi.xposed.sesame.fetchMemberGoodsList.success" -> {
                    Toast.makeText(this@SeckillActivity, "同步商品列表成功！", Toast.LENGTH_SHORT).show()
                    loadLocalGoods()
                }
                "fansirsqi.xposed.sesame.fetchMemberGoodsList.failed" -> {
                    Toast.makeText(this@SeckillActivity, "同步商品列表失败，请确保支付宝正在后台运行并且已登录", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register receiver for broadcast
        val filter = IntentFilter().apply {
            addAction("fansirsqi.xposed.sesame.fetchMemberGoodsList.success")
            addAction("fansirsqi.xposed.sesame.fetchMemberGoodsList.failed")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        loadLocalGoods()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SeckillScreen(
                        goodsList = goodsList,
                        isRefreshing = isRefreshing.value,
                        onRefresh = {
                            isRefreshing.value = true
                            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.fetchMemberGoodsList"))
                            Toast.makeText(this, "正在请求支付宝同步商品列表...", Toast.LENGTH_SHORT).show()
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
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun loadLocalGoods() {
        val file = Files.getMemberGoodsListFile()
        if (file.exists()) {
            val content = Files.readFromFile(file)
            if (!content.isNullOrEmpty()) {
                val parsed = parseGoods(content)
                goodsList.clear()
                goodsList.addAll(parsed)
            }
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
                            
                            if (itemId.isNotEmpty() && !seen.contains(benefitId)) {
                                seen.add(benefitId)
                                list.add(MemberGood(benefitId, name, itemId, points, price))
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
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var itemId by remember { mutableStateOf("") }
    var verifyPoint by remember { mutableStateOf("") }
    var generatedUrl by remember { mutableStateOf("") }
    
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(itemId, verifyPoint) {
        if (itemId.isNotEmpty() && verifyPoint.isNotEmpty()) {
            val orderItemsJson = "[{\"itemId\":\"$itemId\",\"skuId\":\"-1\",\"number\":1}]"
            val encodedOrderItems = Uri.encode(orderItemsJson)
            val extJson = "{\"requestSourceInfo\":\"来源\"}"
            val encodedExtJson = Uri.encode(extJson)
            val tmallUrl = "https://pages.tmall.com/wow/wt/act/lm-pages?env=&extJson=$encodedExtJson&orderItems=$encodedOrderItems&verifyPoint=$verifyPoint&wh_page=buy"
            generatedUrl = "https://pages.tmall.com/wow/z/wt/act/alipay-login?goToUrl=${Uri.encode(tmallUrl)}"
        } else {
            generatedUrl = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会员抢兑直链生成", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
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
                .padding(16.dp)
        ) {
            // 输入卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("手动配置商品参数", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = itemId,
                        onValueChange = { itemId = it },
                        label = { Text("天猫商品 ID (itemId)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = verifyPoint,
                        onValueChange = { verifyPoint = it },
                        label = { Text("所需积分 (verifyPoint)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 直链输出卡片
            if (generatedUrl.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("生成的免密抢兑直链：", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(generatedUrl, fontSize = 12.sp, maxLines = 3)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.align(Alignment.End)) {
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
                                }
                            ) {
                                Text("立即跳转")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedUrl))
                                    Toast.makeText(context, "直链已复制到剪贴板，请发到支付宝聊天记录里点击", Toast.LENGTH_LONG).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("复制直链")
                            }
                        }
                    }
                }
            }

            // 本地商品列表
            Text("可选的商品列表 (点击自动填充)：", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (goodsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("本地尚无商品列表", color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRefresh, enabled = !isRefreshing) {
                            Text("立即从支付宝同步")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(goodsList) { good ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    itemId = good.itemId
                                    verifyPoint = good.points.toString()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(good.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row {
                                    Text("天猫ID: ${good.itemId}", fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("积分: ${good.points}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val orderItemsJson = "[{\"itemId\":\"${good.itemId}\",\"skuId\":\"-1\",\"number\":1}]"
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
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("立即跳转", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
