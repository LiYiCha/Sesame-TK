package fansirsqi.xposed.sesame.ui.network

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Color as AndroidColor
import android.graphics.Typeface

/**
 * 抓包详情页：展示请求概览、请求体、响应体
 */
class NetworkDetailActivity : BaseActivity() {

    private lateinit var packet: CapturePacket
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_detail)

        packet = intent.getSerializableExtra("packet") as? CapturePacket ?: return finish()
        baseTitle = "详情: ${packet.host}"
        toolbar.setNavigationOnClickListener { finish() }

        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)

        viewPager.adapter = DetailPagerAdapter()
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "概览"
                1 -> "请求"
                2 -> "响应"
                else -> ""
            }
        }.attach()
    }

    inner class DetailPagerAdapter : RecyclerView.Adapter<DetailPagerAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val frameLayout = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            return ViewHolder(frameLayout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val container = holder.itemView as ViewGroup
            container.removeAllViews()
            
            val context = container.context
            val textView = TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setPadding(32, 32, 32, 32)
                textSize = 13f
                setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE
            }

            when (position) {
                0 -> { // Overview
                    val recyclerView = RecyclerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        layoutManager = LinearLayoutManager(context)
                        clipToPadding = false
                        setPadding(0, 16, 0, 16)
                    }
                    
                    val cards = mutableListOf<DetailCard>()
                    
                    // 1. General Info
                    cards.add(DetailCard("通用信息", listOf(
                        "URL" to (packet.url ?: "-"),
                        "Method" to (packet.method ?: "-"),
                        "Status" to packet.responseCode.toString(),
                        "Duration" to "${packet.duration}ms",
                        "Start Time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(packet.startTime))
                    )))
                    
                    // 2. Request Headers
                    if (!packet.requestHeaders.isNullOrEmpty()) {
                        cards.add(DetailCard("请求头 (Headers)", packet.requestHeaders!!.map { it.key to it.value }))
                    }
                    
                    // 3. Response Headers
                    if (!packet.responseHeaders.isNullOrEmpty()) {
                        cards.add(DetailCard("响应头 (Headers)", packet.responseHeaders!!.map { it.key to it.value }))
                    }
                    
                    recyclerView.adapter = CardAdapter(cards)
                    container.addView(recyclerView)
                }
                1 -> { // Request Body
                    val scrollView = NestedScrollView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        clipToPadding = false
                        setPadding(0, 16, 0, 16)
                    }
                    
                    val cardView = LayoutInflater.from(context).inflate(R.layout.item_detail_card, scrollView, false)
                    cardView.findViewById<TextView>(R.id.tv_card_title).text = "请求载荷 (Request Body)"
                    val contentContainer = cardView.findViewById<LinearLayout>(R.id.ll_card_content)
                    
                    val contentTextView = TextView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        textSize = 13f
                        setTextColor(AndroidColor.parseColor("#333333"))
                        setTextIsSelectable(true)
                        typeface = android.graphics.Typeface.MONOSPACE
                    }
                    
                    if (packet.requestBodyFile != null) {
                        try {
                            val file = File(packet.requestBodyFile)
                            if (file.exists()) {
                                var content = file.readText()
                                try {
                                    val map = JsonUtil.parseObject(content, Any::class.java)
                                    content = JsonUtil.formatJson(map)
                                } catch (e: Exception) {}
                                contentTextView.text = content
                            } else {
                                contentTextView.text = "请求体文件不存在"
                            }
                        } catch (e: Exception) {
                            contentTextView.text = "读取请求体异常: ${e.message}"
                        }
                    } else {
                        contentTextView.text = "(无请求体)"
                    }
                    
                    contentContainer.addView(contentTextView)
                    scrollView.addView(cardView)
                    container.addView(scrollView)
                }
                2 -> { // Response Body
                    val scrollView = NestedScrollView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        clipToPadding = false
                        setPadding(0, 16, 0, 16)
                    }
                    
                    if (packet.isImage && packet.responseBodyFile != null) {
                        val imageView = ImageView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            adjustViewBounds = true
                            setPadding(16, 16, 16, 16)
                        }
                        try {
                            val bitmap = BitmapFactory.decodeFile(packet.responseBodyFile)
                            if (bitmap != null) {
                                imageView.setImageBitmap(bitmap)
                            } else {
                                val errorTv = TextView(context).apply { text = "图片解码失败"; setTextIsSelectable(true) }
                                container.addView(errorTv)
                                return
                            }
                        } catch (e: Exception) {
                            val errorTv = TextView(context).apply { text = "图片加载异常: ${e.message}"; setTextIsSelectable(true) }
                            container.addView(errorTv)
                            return
                        }
                        container.addView(imageView)
                    } else {
                        val cardView = LayoutInflater.from(context).inflate(R.layout.item_detail_card, scrollView, false)
                        cardView.findViewById<TextView>(R.id.tv_card_title).text = "响应载荷 (Response Body)"
                        val contentContainer = cardView.findViewById<LinearLayout>(R.id.ll_card_content)
                        
                        val contentTextView = TextView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            textSize = 13f
                            setTextColor(AndroidColor.parseColor("#333333"))
                            setTextIsSelectable(true)
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        
                        if (packet.responseBodyFile != null) {
                            try {
                                val file = File(packet.responseBodyFile)
                                if (file.exists()) {
                                    var content = file.readText()
                                    try {
                                        val map = JsonUtil.parseObject(content, Any::class.java)
                                        content = JsonUtil.formatJson(map)
                                    } catch (e: Exception) {}
                                    contentTextView.text = content
                                } else {
                                    contentTextView.text = "响应体文件不存在"
                                }
                            } catch (e: Exception) {
                                contentTextView.text = "读取响应体异常: ${e.message}"
                            }
                        } else {
                            contentTextView.text = "(无响应体)"
                        }
                        
                        contentContainer.addView(contentTextView)
                        scrollView.addView(cardView)
                        container.addView(scrollView)
                    }
                }
            }
        }

        override fun getItemCount(): Int = 3

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }

    // --- Structured Card Adapter for Overview ---

    data class DetailCard(val title: String, val pairs: List<Pair<String, String>>)

    inner class CardAdapter(private val cards: List<DetailCard>) : RecyclerView.Adapter<CardAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val card = cards[position]
            holder.tvTitle.text = card.title
            
            holder.llContent.removeAllViews()
            val inflater = LayoutInflater.from(holder.itemView.context)
            card.pairs.forEach { (k, v) ->
                val rowView = inflater.inflate(R.layout.item_detail_row, holder.llContent, false)
                rowView.findViewById<TextView>(R.id.tv_row_key).text = k
                rowView.findViewById<TextView>(R.id.tv_row_value).text = v
                holder.llContent.addView(rowView)
            }
        }

        override fun getItemCount(): Int = cards.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_card_title)
            val llContent: LinearLayout = view.findViewById(R.id.ll_card_content)
        }
    }
}
