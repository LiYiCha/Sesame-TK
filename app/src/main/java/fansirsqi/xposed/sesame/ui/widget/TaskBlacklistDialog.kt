package fansirsqi.xposed.sesame.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.newutil.defaultBlacklist
import fansirsqi.xposed.sesame.util.TaskBlacklist
import fansirsqi.xposed.sesame.util.ToastUtil

object TaskBlacklistDialog {

    data class BlacklistItem(
        val key: String,
        val type: String, // "永久", "当日", "内置"
        val reason: String,
        val canDelete: Boolean
    )

    @JvmStatic
    fun show(context: Context) {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 16)
        }

        val tvSummary = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(tvSummary)

        val listView = ListView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            divider = ContextCompat.getDrawable(context, android.R.drawable.divider_horizontal_bright)
            dividerHeight = 1
        }
        rootLayout.addView(listView)

        fun loadItems(): List<BlacklistItem> {
            val reasons = TaskBlacklist.getAllReasons()
            val perm = TaskBlacklist.getPermanentList()
            val daily = TaskBlacklist.getDailyList()
            val items = mutableListOf<BlacklistItem>()

            perm.forEach { key ->
                val reason = reasons[key] ?: "自动/手动加入永久黑名单"
                items.add(BlacklistItem(key, "永久", reason, true))
            }
            daily.forEach { key ->
                if (perm.none { it.equals(key, ignoreCase = true) }) {
                    val reason = reasons[key] ?: "当日频控超限 (次日自动重置)"
                    items.add(BlacklistItem(key, "当日", reason, true))
                }
            }
            defaultBlacklist.forEach { key ->
                if (items.none { it.key.equals(key, ignoreCase = true) }) {
                    items.add(BlacklistItem(key, "内置", "官方风控/不支持规则任务", false))
                }
            }
            return items
        }

        var currentItems = loadItems().toMutableList()

        class BlacklistAdapter : BaseAdapter() {
            override fun getCount(): Int = currentItems.size
            override fun getItem(position: Int): Any = currentItems[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val item = currentItems[position]
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(12, 20, 12, 20)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val titleLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val tvTag = TextView(context).apply {
                    text = "[${item.type}]"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(
                        when (item.type) {
                            "永久" -> Color.parseColor("#E53935")
                            "当日" -> Color.parseColor("#FB8C00")
                            else -> Color.parseColor("#757575")
                        }
                    )
                    setPadding(0, 0, 12, 0)
                }
                titleLayout.addView(tvTag)

                val tvTitle = TextView(context).apply {
                    text = item.key
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
                }
                titleLayout.addView(tvTitle)
                infoLayout.addView(titleLayout)

                val tvReason = TextView(context).apply {
                    text = "原因: ${item.reason}"
                    textSize = 12f
                    setTextColor(Color.GRAY)
                    setPadding(0, 6, 0, 0)
                }
                infoLayout.addView(tvReason)
                row.addView(infoLayout)

                if (item.canDelete) {
                    val btnDelete = Button(context).apply {
                        text = "移除"
                        textSize = 12f
                        setTextColor(Color.RED)
                        setBackgroundResource(R.drawable.dialog_list_button)
                        setPadding(16, 0, 16, 0)
                        setOnClickListener {
                            TaskBlacklist.removeFromBlacklist(item.key, item.key)
                            ToastUtil.makeText(context, "已移出黑名单: ${item.key}", Toast.LENGTH_SHORT).show()
                            currentItems = loadItems().toMutableList()
                            tvSummary.text = "共 ${currentItems.size} 个黑名单任务 (永久: ${TaskBlacklist.getPermanentList().size}, 当日: ${TaskBlacklist.getDailyList().size})"
                            notifyDataSetChanged()
                        }
                    }
                    row.addView(btnDelete)
                }

                return row
            }
        }

        val adapter = BlacklistAdapter()
        listView.adapter = adapter
        tvSummary.text = "共 ${currentItems.size} 个黑名单任务 (永久: ${TaskBlacklist.getPermanentList().size}, 当日: ${TaskBlacklist.getDailyList().size})"

        MaterialAlertDialogBuilder(context)
            .setTitle("🚫 任务黑名单管理")
            .setView(rootLayout)
            .setPositiveButton("关闭", null)
            .setNeutralButton("清空用户黑名单") { _, _ ->
                MaterialAlertDialogBuilder(context)
                    .setTitle("确认清空")
                    .setMessage("确定要清空所有自定义和当日黑名单任务吗？清空后被拦截的任务将在下次运行时重新尝试。")
                    .setPositiveButton("确认清空") { _, _ ->
                        TaskBlacklist.clearBlacklist()
                        ToastUtil.makeText(context, "已清空所有用户任务黑名单", Toast.LENGTH_SHORT).show()
                        currentItems = loadItems().toMutableList()
                        tvSummary.text = "共 ${currentItems.size} 个黑名单任务"
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("手动添加") { _, _ ->
                val input = EditText(context).apply {
                    hint = "输入要拉黑的任务名称或任务ID"
                    setPadding(40, 30, 40, 30)
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle("手动添加黑名单")
                    .setView(input)
                    .setPositiveButton("添加") { _, _ ->
                        val text = input.text.toString().trim()
                        if (text.isNotEmpty()) {
                            TaskBlacklist.addToBlacklist(text, text)
                            ToastUtil.makeText(context, "已加入黑名单: $text", Toast.LENGTH_SHORT).show()
                            currentItems = loadItems().toMutableList()
                            tvSummary.text = "共 ${currentItems.size} 个黑名单任务"
                            adapter.notifyDataSetChanged()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .show()
    }
}
