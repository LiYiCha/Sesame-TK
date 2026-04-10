package fansirsqi.xposed.sesame.ui.network

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.hook.network.CaptureFileManager
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.util.ToastUtil
import androidx.core.content.ContextCompat
import fansirsqi.xposed.sesame.hook.network.CaptureTestData
import android.view.Menu
import android.view.MenuItem

/**
 * 抓包记录首页：显示日期分组
 */
class NetworkListActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private val dateFolders = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_list)

        baseTitle = "抓包历史记录"
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_back)
        toolbar.isTitleCentered = true
        
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DateAdapter()

        findViewById<View>(R.id.fab_clear).setOnClickListener {
            showClearConfirmDialog()
        }

        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_network_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_inject_test -> {
                CaptureTestData.injectDummyData()
                ToastUtil.showToast("已注入 3 条真实感测试数据")
                loadData()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadData() {
        dateFolders.clear()
        dateFolders.addAll(CaptureFileManager.getDailyFolders())
        recyclerView.adapter?.notifyDataSetChanged()
        
        if (dateFolders.isEmpty()) {
            ToastUtil.showToast("暂无抓包记录")
        }
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("确认清空")
            .setMessage("确定要删除所有的网络抓包记录吗？此操作不可恢复。")
            .setPositiveButton("清空") { _: DialogInterface, _: Int ->
                if (CaptureFileManager.clearAll()) {
                    ToastUtil.showToast("记录已清空")
                    loadData()
                } else {
                    ToastUtil.showToast("清空失败")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class DateAdapter : RecyclerView.Adapter<DateAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_network_date, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val date = dateFolders[position]
            holder.tvDate.text = date
            holder.itemView.setOnClickListener {
                val intent = Intent(this@NetworkListActivity, NetworkPacketListActivity::class.java)
                intent.putExtra("date", date)
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = dateFolders.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(R.id.tv_date)
        }
    }
}
