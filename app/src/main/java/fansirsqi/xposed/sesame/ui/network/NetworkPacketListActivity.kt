package fansirsqi.xposed.sesame.ui.network

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.hook.network.CaptureFileManager
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.util.CoroutineUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * 抓包列表页：显示指定日期的所有流量包
 */
class NetworkPacketListActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var etSearch: EditText
    private var dateStr: String = ""
    
    private val allPackets = mutableListOf<CapturePacket>()
    private val displayPackets = mutableListOf<CapturePacket>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_packet_list)

        dateStr = intent.getStringExtra("date") ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        baseTitle = if (dateStr == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) "今日抓包" else dateStr
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.isTitleCentered = true

        etSearch = findViewById(R.id.et_search)
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = PacketAdapter()
        

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 100, 100, "查看历史")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 100) {
            startActivity(Intent(this, NetworkListActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadData() {
        if (dateStr.isEmpty()) {
            dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
        
        CoroutineUtils.runOnIO {
            val packets = CaptureFileManager.getPacketsForDate(dateStr)
            runOnUiThread {
                allPackets.clear()
                allPackets.addAll(packets)
                displayPackets.clear()
                displayPackets.addAll(allPackets)
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun filter(query: String) {
        displayPackets.clear()
        if (query.isEmpty()) {
            displayPackets.addAll(allPackets)
        } else {
            val queries = query.lowercase().split(" ").filter { it.isNotEmpty() }
            displayPackets.addAll(allPackets.filter { packet ->
                queries.all { q ->
                    packet.url?.lowercase()?.contains(q) == true || 
                    packet.host?.lowercase()?.contains(q) == true ||
                    packet.method?.lowercase()?.contains(q) == true
                }
            })
        }
        recyclerView.adapter?.notifyDataSetChanged()
    }

    inner class PacketAdapter : RecyclerView.Adapter<PacketAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_network_packet, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val packet = displayPackets[position]
            
            holder.tvMethod.text = packet.method
            holder.tvCode.text = packet.responseCode.toString()
            holder.tvHost.text = packet.host
            holder.tvPath.text = packet.url?.substringAfter(packet.host ?: "", "") ?: ""
            holder.tvDuration.text = "${packet.duration}ms"
            holder.tvTime.text = timeFormat.format(Date(packet.startTime))

            // 状态指示器和状态码着色
            val codeColorRes = when (packet.responseCode) {
                in 200..299 -> android.R.color.holo_green_dark
                in 300..399 -> android.R.color.holo_blue_dark
                in 400..599 -> android.R.color.holo_red_dark
                else -> android.R.color.darker_gray
            }
            val color = ContextCompat.getColor(this@NetworkPacketListActivity, codeColorRes)
            holder.tvCode.setTextColor(color)
            holder.vIndicator.setBackgroundColor(color)

            holder.itemView.setOnClickListener {
                val intent = Intent(this@NetworkPacketListActivity, NetworkDetailActivity::class.java)
                intent.putExtra("packet", packet)
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = displayPackets.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvMethod: TextView = view.findViewById(R.id.tv_method)
            val tvCode: TextView = view.findViewById(R.id.tv_code)
            val tvHost: TextView = view.findViewById(R.id.tv_host)
            val tvPath: TextView = view.findViewById(R.id.tv_path)
            val tvDuration: TextView = view.findViewById(R.id.tv_duration)
            val tvTime: TextView = view.findViewById(R.id.tv_time)
            val vIndicator: View = view.findViewById(R.id.view_status_indicator)
        }
    }
}
