package fansirsqi.xposed.sesame.ui.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fansirsqi.xposed.sesame.task.otherTask2.SeckillScheduler
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme

data class MemberGood(
    val benefitId: String,
    val name: String,
    val itemId: String,
    val points: Int,
    val price: String,
    val skuId: String = "-1",
    val actionUrl: String = "",
    val skuIds: List<String> = emptyList(), // Multi-specs support (formatted as "skuId|price|points")
    val exchangeStartTime: Long = 0 // 下次可兑换开始时间（nextExchangeStartTime，毫秒），0 表示未知
)

// 会员商品分类 Tab（deliveryId 对齐 temp/会员商品.log）
private val MEMBER_CATEGORIES = listOf(
    "日常抢兑" to "94000SR2025120515775004",
    "万分好物" to "94000SR2025120515776001",
    "联名周边" to "94000SR2025120515776002",
    "全部商品" to "94000SR2023102305988003"
)

// "全部商品"的 deliveryId（与 ExtendHandle 的 ALL_GOODS_DELIVERY_ID 保持一致）
private const val ALL_GOODS_DELIVERY_ID = "94000SR2023102305988003"

// "全部商品"的积分区间子 Tab（对齐 queryDeliveryZoneDetail 的 lowerPoint/upperPoint）
private val MEMBER_ZONE_NAMES = listOf("0-501分", "501-3000分", "3001-10000分", "10000分+")

// 描边输入框：基于 BasicTextField + DecorationBox，高度由 contentPadding 控制约 38dp，
@Composable
private fun CompactOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                placeholder = if (hint != null) {
                    { Text(hint, fontSize = 11.sp) }
                } else null
            )
        }
    )
}

class SeckillActivity : ComponentActivity() {

    private val goodsList = mutableStateListOf<MemberGood>()
    private val isRefreshing = mutableStateOf(false)
    private val currentCategory = mutableStateOf("94000SR2025120515775004")
    private val currentPage = mutableIntStateOf(1)
    private val currentZone = mutableIntStateOf(0) // "全部商品"的积分区间子 Tab 索引
    private val isSearchMode = mutableStateOf(false) // 是否展示服务端搜索结果
    private val allCachedGoods = mutableStateOf<List<MemberGood>>(emptyList()) // 本地全局搜索用（全部分类缓存）

    // 列表加载序号：后台解析完成后回主线程前校验，防止旧请求覆盖新请求（列表闪变）
    @Volatile
    private var loadSeq = 0

    private var pendingDirectJumpBenefitId: String? = null

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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private fun startTimeoutTimer(timeoutMs: Long = 15000L, message: String = "请求超时，请检查支付宝是否在后台运行") {
        cancelTimeoutTimer()
        val runnable = Runnable {
            if (isRefreshing.value) {
                isRefreshing.value = false
                Toast.makeText(this@SeckillActivity, message, Toast.LENGTH_LONG).show()
            }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, timeoutMs)
    }

    private fun cancelTimeoutTimer() {
        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            cancelTimeoutTimer()
            isRefreshing.value = false
            when (intent?.action) {
                "fansirsqi.xposed.sesame.fetchMemberGoodsList.success" -> {
                    val deliveryId = intent.getStringExtra("deliveryId") ?: "94000SR2025120515775004"
                    val zoneIndex = intent.getIntExtra("zoneIndex", -1)
                    Toast.makeText(this@SeckillActivity, "同步商品列表成功！", Toast.LENGTH_SHORT).show()
                    if (zoneIndex >= 0) {
                        loadLocalGoods(deliveryId, zoneIndex)
                    } else {
                        loadLocalGoods(deliveryId)
                    }
                }
                "fansirsqi.xposed.sesame.fetchMemberGoodsList.failed" -> {
                    val reason = intent.getStringExtra("reason")
                    val partial = intent.getBooleanExtra("partial", false)
                    if (partial) {
                        // 部分页同步失败：先加载已入库数据，并如实提示
                        val deliveryId = intent.getStringExtra("deliveryId") ?: "94000SR2025120515775004"
                        val zoneIndex = intent.getIntExtra("zoneIndex", -1)
                        if (zoneIndex >= 0) loadLocalGoods(deliveryId, zoneIndex) else loadLocalGoods(deliveryId)
                        Toast.makeText(this@SeckillActivity, "部分页同步失败，已加载已有数据，建议重新同步", Toast.LENGTH_LONG).show()
                    } else if ("no_more" == reason) {
                        Toast.makeText(this@SeckillActivity, "已是最后一页 / 没有更多商品了", Toast.LENGTH_SHORT).show()
                        if (currentPage.value > 1) {
                            currentPage.value--
                        }
                    } else {
                        Toast.makeText(this@SeckillActivity, "同步商品列表失败，请确保支付宝在运行中", Toast.LENGTH_LONG).show()
                    }
                }
                "fansirsqi.xposed.sesame.searchMemberGoods.success" -> {
                    val query = intent.getStringExtra("query")
                    isSearchMode.value = true
                    loadSearchGoods()
                    Toast.makeText(this@SeckillActivity, "服务端搜索完成${query?.let { "：$it" } ?: ""}", Toast.LENGTH_SHORT).show()
                }
                "fansirsqi.xposed.sesame.searchMemberGoods.failed" -> {
                    Toast.makeText(this@SeckillActivity, "服务端搜索失败，请确保支付宝在运行中", Toast.LENGTH_LONG).show()
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
                            if (currentCategory.value == ALL_GOODS_DELIVERY_ID) {
                                saveLocalGoodsWithUpdatedSku(currentCategory.value, currentZone.value)
                            } else {
                                saveLocalGoodsWithUpdatedSku(currentCategory.value)
                            }
                        }

                        // 直达按钮的自动跳转：规格回包后直接进入结算页（无规格则兜底详情页）
                        if (pendingDirectJumpBenefitId == benefitId) {
                            pendingDirectJumpBenefitId = null
                            val updatedGood = goodsList.firstOrNull { it.benefitId == benefitId }
                            if (updatedGood != null) {
                                if (fetchedSkuId != "-1" && fetchedSkuIds.isNotEmpty()) {
                                    openMemberGoodsTarget(updatedGood)
                                } else if (updatedGood.actionUrl.isNotEmpty()) {
                                    Toast.makeText(this@SeckillActivity, "该商品无规格信息，已打开详情页", Toast.LENGTH_SHORT).show()
                                    openAlipay(updatedGood.actionUrl)
                                } else {
                                    Toast.makeText(this@SeckillActivity, "该商品无规格且无详情链接，无法直达", Toast.LENGTH_SHORT).show()
                                }
                            }
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
            addAction("fansirsqi.xposed.sesame.searchMemberGoods.success")
            addAction("fansirsqi.xposed.sesame.searchMemberGoods.failed")
            addAction("fansirsqi.xposed.sesame.queryBenefitDetail.success")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        loadLocalGoods(currentCategory.value)
        // 全局搜索缓存较大，后台解析后回主线程赋值，避免阻塞首帧
        GlobalThreadPools.execute {
            val parsed = loadAllCachedGoods()
            runOnUiThread {
                allCachedGoods.value = parsed
            }
        }

        setContent {
            // 使用全局统一的 SesameTheme
            SesameTheme {
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
                            isSearchMode.value = false
                            currentCategory.value = deliveryId
                            startTimeoutTimer(15000L, "同步商品列表超时，请确保支付宝在后台运行")
                            val intent = Intent("com.eg.android.AlipayGphone.sesame.memberOperation").apply {
                                putExtra("operation", "FETCH_GOODS_LIST")
                                putExtra("deliveryId", deliveryId)
                                putExtra("pageNum", page)
                                if (deliveryId == ALL_GOODS_DELIVERY_ID) {
                                    putExtra("zoneIndex", currentZone.value)
                                }
                            }
                            sendBroadcast(intent)
                            Toast.makeText(this@SeckillActivity, "正在请求同步商品列表...", Toast.LENGTH_SHORT).show()
                        },
                        onTabSelected = { deliveryId ->
                            currentCategory.value = deliveryId
                            isSearchMode.value = false
                            if (deliveryId == ALL_GOODS_DELIVERY_ID) {
                                loadLocalGoods(deliveryId, currentZone.value)
                            } else {
                                loadLocalGoods(deliveryId)
                            }
                            // Reset selected spec list when switching tabs
                            itemId.value = ""
                            verifyPoint.value = ""
                            skuId.value = "-1"
                            selectedSkuIds.value = emptyList()
                        },
                        onZoneSelected = { zone ->
                            currentZone.value = zone
                            currentPage.value = 1
                            isSearchMode.value = false
                            loadLocalGoods(currentCategory.value, zone)
                        },
                        currentZone = currentZone.value,
                        allCachedGoods = allCachedGoods.value,
                        isSearchMode = isSearchMode.value,
                        onSearchQueryChange = { newQuery ->
                            if (newQuery.isEmpty() && isSearchMode.value) {
                                // 清空搜索词后退出搜索模式，恢复当前分类列表
                                isSearchMode.value = false
                                if (currentCategory.value == ALL_GOODS_DELIVERY_ID) {
                                    loadLocalGoods(currentCategory.value, currentZone.value)
                                } else {
                                    loadLocalGoods(currentCategory.value)
                                }
                            }
                        },
                        onServerSearch = { q ->
                            val query = q.trim()
                            if (query.isEmpty()) {
                                Toast.makeText(this@SeckillActivity, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
                            } else {
                                isRefreshing.value = true
                                startTimeoutTimer(15000L, "服务端搜索超时，请确保支付宝在后台运行")
                                val intent = Intent("com.eg.android.AlipayGphone.sesame.memberOperation").apply {
                                    putExtra("operation", "SEARCH_GOODS")
                                    putExtra("query", query)
                                    putExtra("pageNum", 1)
                                }
                                sendBroadcast(intent)
                                Toast.makeText(this@SeckillActivity, "正在服务端搜索：$query", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBack = { finish() },
                        onDirectJump = { good, quantity ->
                            val isPhysical = good.itemId.isNotEmpty() && good.itemId.all { it.isDigit() }
                            if (isPhysical && good.skuId == "-1") {
                                // 列表数据不带 skuId：先自动查规格，回包后自动进入结算页
                                pendingDirectJumpBenefitId = good.benefitId
                                val intent = Intent("com.eg.android.AlipayGphone.sesame.memberOperation").apply {
                                    putExtra("operation", "QUERY_BENEFIT_DETAIL")
                                    putExtra("benefitId", good.benefitId)
                                }
                                sendBroadcast(intent)
                                Toast.makeText(this@SeckillActivity, "正在获取规格，稍后自动进入结算页...", Toast.LENGTH_SHORT).show()
                            } else {
                                openMemberGoodsTarget(good, quantity)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimeoutTimer()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }

    private fun readMemberGoodsPool(): JSONObject {
        val file = Files.getMemberGoodsPoolFile()
        if (!file.exists()) return JSONObject()
        val content = Files.readFromFile(file)
        if (content.isNullOrEmpty()) return JSONObject()
        return try {
            JSONObject(content)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun readMemberGoodsLists(): JSONObject {
        val file = Files.getMemberGoodsListFile()
        if (!file.exists()) return JSONObject()
        val content = Files.readFromFile(file)
        if (content.isNullOrEmpty()) return JSONObject()
        return try {
            JSONObject(content)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** 唤起支付宝打开指定链接（scheme 直用，http(s) 包 startapp） */
    private fun openAlipay(url: String) {
        val scheme = if (url.startsWith("alipays://")) url
        else "alipays://platformapi/startapp?appId=20000067&url=${Uri.encode(url)}"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法唤起支付宝: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 打开商品目标页：有规格的实物走下单结算页，否则兜底 actionUrl 详情页 */
    private fun openMemberGoodsTarget(good: MemberGood, quantity: Int = 1) {
        val isPhysical = good.itemId.isNotEmpty() && good.itemId.all { it.isDigit() }
        val targetUrl = if (isPhysical && good.skuId != "-1") {
            val orderItemsJson = "[{\"itemId\":\"${good.itemId}\",\"skuId\":\"${good.skuId}\",\"number\":$quantity}]"
            val encodedOrderItems = Uri.encode(orderItemsJson)
            val encodedExtJson = Uri.encode("{\"requestSourceInfo\":\"来源\"}")
            val tmallUrl = "https://pages.tmall.com/wow/wt/act/lm-pages?env=&extJson=$encodedExtJson&orderItems=$encodedOrderItems&verifyPoint=${good.points}&wh_page=buy"
            "https://pages.tmall.com/wow/z/wt/act/alipay-login?goToUrl=${Uri.encode(tmallUrl)}"
        } else {
            good.actionUrl
        }
        if (targetUrl.isEmpty()) {
            Toast.makeText(this, "该商品无可用链接", Toast.LENGTH_SHORT).show()
            return
        }
        openAlipay(targetUrl)
    }

    /** 按列表 key 读取商品：listKey 形如 cat_<deliveryId> / zone_<deliveryId>_<zone> / search */
    private fun loadGoodsByListKey(listKey: String): List<MemberGood> {
        val goods = readMemberGoodsPool()
        val lists = readMemberGoodsLists()
        val listJo = lists.optJSONObject(listKey) ?: return emptyList()
        val ids = listJo.optJSONArray("ids") ?: return emptyList()
        // 一次性包装为数组再解析，避免逐商品 toString+JSONObject 反复序列化
        val wrapper = JSONArray()
        for (i in 0 until ids.length()) {
            val bid = ids.optString(i)
            val obj = goods.optJSONObject(bid) ?: continue
            wrapper.put(obj)
        }
        return parseGoods(wrapper)
    }

    private fun loadLocalGoods(deliveryId: String, zone: Int = -1) {
        val listKey = if (zone >= 0) "zone_${deliveryId}_$zone" else "cat_$deliveryId"
        loadGoodsInBackground(listKey)
    }

    private fun loadSearchGoods() {
        loadGoodsInBackground("search")
    }

    /** 后台解析列表并回主线程更新；用递增序号防止旧请求完成后覆盖新请求 */
    private fun loadGoodsInBackground(listKey: String) {
        val seq = ++loadSeq
        GlobalThreadPools.execute {
            val parsed = loadGoodsByListKey(listKey)
            runOnUiThread {
                if (seq != loadSeq) return@runOnUiThread
                goodsList.clear()
                goodsList.addAll(parsed)
            }
        }
    }

    /** 合并缓存中所有列表（含全部商品 4 个分区）的商品，供搜索框全局过滤 */
    private fun loadAllCachedGoods(): List<MemberGood> {
        val goods = readMemberGoodsPool()
        val lists = readMemberGoodsLists()
        val wrapper = JSONArray()
        val seen = mutableSetOf<String>()
        lists.keys().forEach { key ->
            val listJo = lists.optJSONObject(key) ?: return@forEach
            val ids = listJo.optJSONArray("ids") ?: return@forEach
            for (i in 0 until ids.length()) {
                val bid = ids.optString(i)
                if (!seen.add(bid)) continue
                val obj = goods.optJSONObject(bid) ?: continue
                wrapper.put(obj)
            }
        }
        return parseGoods(wrapper)
    }

    private fun saveLocalGoodsWithUpdatedSku(deliveryId: String, zone: Int = -1) {
        // 主线程先快照，避免后台线程遍历可变列表；文件 IO 与解析全部放后台
        val goodsSnapshot = goodsList.toList()
        GlobalThreadPools.execute {
            // 共享锁：与后台抓取保存互斥，避免并发"读-改-写"互相覆盖
            synchronized(Files.memberGoodsLock()) {
                try {
                    // 规格详情只更新商品池，不动列表索引
                    val file = Files.getMemberGoodsPoolFile()
                    val goods = readMemberGoodsPool()
                    goodsSnapshot.forEach { good ->
                        val obj = goods.optJSONObject(good.benefitId) ?: return@forEach
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
                        obj.put("skuInfoList", skus)
                        goods.put(good.benefitId, obj)
                    }
                    // 原子写，避免读端读到半截 JSON
                    Files.write2FileAtomic(goods.toString(), file)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /** 从任意 JSON 节点（商品对象或商品数组）递归提取商品，直接解析对象避免反复序列化 */
    private fun parseGoods(any: Any?): List<MemberGood> {
        val list = mutableListOf<MemberGood>()
        val seen = mutableSetOf<String>()
        try {
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
                            
                            val linkInfo = obj.optJSONObject("linkInfo")
                            var actionUrl = ""
                            if (linkInfo != null) {
                                actionUrl = linkInfo.optString("detailUrl", "")
                                if (actionUrl.isEmpty()) {
                                    actionUrl = linkInfo.optString("officialDetailUrl", "")
                                }
                                if (actionUrl.isEmpty()) {
                                    actionUrl = linkInfo.optString("jumpUrl", "")
                                }
                            }
                            if (actionUrl.isEmpty()) {
                                actionUrl = obj.optString("actionUrl", "")
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

                            // 无 itemId 的券类/虚拟权益也收录（可显示、可直达详情页），itemId 留空
                            if (!seen.contains(benefitId)) {
                                seen.add(benefitId)
                                val exchangeStartTime = obj.optLong("nextExchangeStartTime", 0L)
                                list.add(MemberGood(benefitId, name, itemId, points, price, skuId, actionUrl, skuIdsList, exchangeStartTime))
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
            extract(any)
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
    currentZone: Int,
    onZoneSelected: (zone: Int) -> Unit,
    allCachedGoods: List<MemberGood>,
    isSearchMode: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onServerSearch: (query: String) -> Unit,
    onBack: () -> Unit,
    onDirectJump: (good: MemberGood, quantity: Int) -> Unit
) {
    val categories = MEMBER_CATEGORIES

    var selectedTabIndex by remember { mutableStateOf(0) }
    var generatedUrl by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    // 当前选中商品的开抢时间（nextExchangeStartTime），用于排期预填
    var selectedExchangeStart by remember { mutableStateOf(0L) }

    val scheduleTimePrefill: (Long) -> Unit = { startMillis ->
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        if (startMillis > System.currentTimeMillis()) {
            onScheduleTimeStrChange(sdf.format(java.util.Date(startMillis)))
        } else {
            // 缓存时间已过期（列表是旧数据），回退到下一个整点
            val cal = Calendar.getInstance()
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            onScheduleTimeStrChange(sdf.format(cal.time))
        }
    }

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
            context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.memberOperation").apply {
                putExtra("operation", "SYNC_SECKILL_TASKS")
            })
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
    val filteredGoods = remember(goodsList, allCachedGoods, searchQuery, isSearchMode) {
        if (searchQuery.isEmpty()) {
            goodsList
        } else {
            // 服务端搜索模式：在搜索结果内过滤；本地搜索：跨全部分类缓存过滤
            val scope = if (isSearchMode) goodsList else allCachedGoods
            scope.filter { it.name.contains(searchQuery, ignoreCase = true) || it.itemId.contains(searchQuery) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会员商品", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("手动配置商品参数", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        CompactOutlinedField(
                            value = itemId,
                            onValueChange = onItemIdChange,
                            hint = "商品 ID / 权益 ID (itemId)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1.1f)) {
                                Text("所需积分", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                CompactOutlinedField(
                                    value = verifyPoint,
                                    onValueChange = onVerifyPointChange,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column(modifier = Modifier.weight(1.1f)) {
                                Text("规格 ID", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                CompactOutlinedField(
                                    value = skuId,
                                    onValueChange = onSkuIdChange,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column(modifier = Modifier.weight(0.8f)) {
                                Text("数量", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                CompactOutlinedField(
                                    value = quantityNumber,
                                    onValueChange = onQuantityNumberChange,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Specification Chips Selector
                        if (selectedSkuIds.size > 1) {
                            Spacer(modifier = Modifier.height(6.dp))
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
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("快捷秒杀控制面板已就绪", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
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
                                        scheduleTimePrefill(selectedExchangeStart)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            onSearchQueryChange(it)
                        },
                        placeholder = { Text("搜索本地缓存的商品...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { onServerSearch(searchQuery) },
                        enabled = !isRefreshing,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("服务端搜索", fontSize = 12.sp)
                    }
                }

                // "全部商品"积分区间子 Tab（各分区独立翻页）
                if (categories[selectedTabIndex].second == ALL_GOODS_DELIVERY_ID) {
                    ScrollableTabRow(
                        selectedTabIndex = currentZone,
                        edgePadding = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MEMBER_ZONE_NAMES.forEachIndexed { index, name ->
                            Tab(
                                selected = currentZone == index,
                                onClick = { onZoneSelected(index) },
                                text = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Goods List Container
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredGoods.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (isRefreshing) "正在同步支付宝商品列表..." else "没有找到商品", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
                                            selectedExchangeStart = good.exchangeStartTime
                                            
                                            // Trigger automatic background SKU lookup
                                            if (good.skuId == "-1") {
                                                Toast.makeText(context, "正在查询规格...", Toast.LENGTH_SHORT).show()
                                                val intent = Intent("com.eg.android.AlipayGphone.sesame.memberOperation").apply {
                                                    putExtra("operation", "QUERY_BENEFIT_DETAIL")
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
                                        if (good.exchangeStartTime > 0) {
                                            Text(
                                                "开抢: ${SimpleDateFormat("MM-dd HH:mm", LocalLocale.current.platformLocale).format(java.util.Date(good.exchangeStartTime))}",
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                color = if (good.exchangeStartTime > System.currentTimeMillis()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                        Row {
                                            Text("ID: ${good.itemId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("积分: ${good.points} + ${good.price}元", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (good.skuId != "-1") {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                if (good.skuIds.size > 1) "规格: ${good.skuIds.size}个" else "SKU: ${good.skuId}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                // 无规格的实物商品由 Activity 先自动查规格，回包后自动进入结算页
                                                onDirectJump(good, quantityNumber.toIntOrNull() ?: 1)
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
                                                selectedExchangeStart = good.exchangeStartTime
                                                
                                                // Trigger background SKU resolution
                                                if (good.skuId == "-1") {
                                                    val intent = Intent("com.eg.android.AlipayGphone.sesame.memberOperation").apply {
                                                        putExtra("operation", "QUERY_BENEFIT_DETAIL")
                                                        putExtra("benefitId", good.benefitId)
                                                    }
                                                    context.sendBroadcast(intent)
                                                }

                                                scheduleTimePrefill(good.exchangeStartTime)
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
                if (categories[selectedTabIndex].second == ALL_GOODS_DELIVERY_ID) {
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
                                            Text("时间: ${task.optString("seckillTime")} | 模式: ${task.optString("type")} | 数量: ${task.optInt("number", 1)} | ID: ${task.optString("itemId")}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(
                                            onClick = {
                                                val newTasks = seckillTasks.filter { it !== task }
                                                saveTasks(newTasks)
                                                Toast.makeText(context, "秒杀任务已取消", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "取消任务", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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
                    Text("ID: $scheduleItemId | SKU: $scheduleSkuId", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("RPC(非需支付)", fontSize = 11.sp)
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
                                        put("benefitId", activeBenefitId)
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
