package fansirsqi.xposed.sesame.ui.widget

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.newutil.defaultBlacklist
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TaskBlacklist
import fansirsqi.xposed.sesame.util.ToastUtil

object TaskBlacklistDialog {
    private const val TAG = "TaskBlacklistDialog"

    data class BlacklistItem(
        val key: String,
        val type: String, // "永久", "当日", "内置"
        val reason: String,
        val canDelete: Boolean
    )

    @JvmStatic
    fun show(context: Context) {
        // 1. 防御：Activity 销毁或正在退出时严禁弹窗，杜绝 WindowManager.BadTokenException
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            Log.error(TAG, "Activity 已销毁或正在结束，取消弹出黑名单管理")
            return
        }

        try {
            showInternal(context)
        } catch (t: Throwable) {
            Log.error(TAG, "打开任务黑名单管理弹窗异常: ${t.message}")
            try {
                Toast.makeText(context, "打开黑名单管理失败: ${t.message}", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
        }
    }

    private fun showInternal(context: Context) {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 16)
        }

        // 搜索过滤框
        val edtSearch = EditText(context).apply {
            hint = "🔍 搜索任务名称、ID 或原因..."
            textSize = 13f
            minHeight = 44
            setPadding(24, 16, 24, 16)
            setBackgroundResource(android.R.drawable.edit_text)
        }
        rootLayout.addView(edtSearch)

        // 统计摘要条
        val tvSummary = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(8, 12, 8, 12)
        }
        rootLayout.addView(tvSummary)

        // 空视图提示
        val tvEmpty = TextView(context).apply {
            text = "暂无匹配的任务黑名单"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 48)
            visibility = View.GONE
        }

        // 列表控件及最大高度容器（严格限制最大高度为屏幕的 60%，彻底解决 Dialog 测量死循环与无限拉伸）
        val maxListHeight = (context.resources.displayMetrics.heightPixels * 0.60f).toInt().coerceAtLeast(300)
        val listContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxListHeight
            )
        }

        val listView = ListView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            divider = ContextCompat.getDrawable(context, android.R.drawable.divider_horizontal_bright)
            dividerHeight = 1
            isFastScrollEnabled = true
        }
        listContainer.addView(listView)
        listContainer.addView(tvEmpty)
        rootLayout.addView(listContainer)

        fun loadAllItems(): List<BlacklistItem> {
            return try {
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
                items
            } catch (e: Exception) {
                Log.error(TAG, "加载黑名单数据失败: ${e.message}")
                emptyList()
            }
        }

        class ViewHolder(
            val row: LinearLayout,
            val tvTag: TextView,
            val tvTitle: TextView,
            val tvReason: TextView,
            val btnDelete: Button
        )

        class SafeBlacklistAdapter : BaseAdapter() {
            private val allList = mutableListOf<BlacklistItem>()
            private val displayList = mutableListOf<BlacklistItem>()
            private var currentFilter: String = ""

            fun update(newItems: List<BlacklistItem>, filterText: String = currentFilter) {
                allList.clear()
                allList.addAll(newItems)
                currentFilter = filterText.trim().lowercase()
                applyFilter()
            }

            fun setFilter(filterText: String) {
                currentFilter = filterText.trim().lowercase()
                applyFilter()
            }

            private fun applyFilter() {
                displayList.clear()
                if (currentFilter.isEmpty()) {
                    displayList.addAll(allList)
                } else {
                    displayList.addAll(allList.filter {
                        it.key.lowercase().contains(currentFilter) ||
                                it.reason.lowercase().contains(currentFilter) ||
                                it.type.lowercase().contains(currentFilter)
                    })
                }
                notifyDataSetChanged()
                tvEmpty.visibility = if (displayList.isEmpty()) View.VISIBLE else View.GONE
                updateSummaryText()
            }

            private fun updateSummaryText() {
                val permCount = allList.count { it.type == "永久" }
                val dailyCount = allList.count { it.type == "当日" }
                val filterSuffix = if (currentFilter.isNotEmpty()) " (过滤后: ${displayList.size})" else ""
                tvSummary.text = "共 ${allList.size} 个黑名单任务$filterSuffix (永久: $permCount, 当日: $dailyCount)"
            }

            override fun getCount(): Int = displayList.size

            override fun getItem(position: Int): Any? =
                if (position in displayList.indices) displayList[position] else null

            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val holder: ViewHolder
                val rowView: View

                if (convertView != null && convertView.tag is ViewHolder) {
                    rowView = convertView
                    holder = convertView.tag as ViewHolder
                } else {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(12, 18, 12, 18)
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

                    val tag = TextView(context).apply {
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(0, 0, 10, 0)
                    }
                    titleLayout.addView(tag)

                    val title = TextView(context).apply {
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
                    }
                    titleLayout.addView(title)
                    infoLayout.addView(titleLayout)

                    val reason = TextView(context).apply {
                        textSize = 12f
                        setTextColor(Color.GRAY)
                        setPadding(0, 4, 0, 0)
                    }
                    infoLayout.addView(reason)
                    row.addView(infoLayout)

                    val btn = Button(context).apply {
                        text = "移除"
                        textSize = 12f
                        setTextColor(Color.parseColor("#E53935"))
                        setBackgroundResource(R.drawable.dialog_list_button)
                        setPadding(16, 0, 16, 0)
                    }
                    row.addView(btn)

                    holder = ViewHolder(row, tag, title, reason, btn)
                    row.tag = holder
                    rowView = row
                }

                if (position in displayList.indices) {
                    val item = displayList[position]
                    holder.tvTag.text = "[${item.type}]"
                    holder.tvTag.setTextColor(
                        when (item.type) {
                            "永久" -> Color.parseColor("#E53935")
                            "当日" -> Color.parseColor("#FB8C00")
                            else -> Color.parseColor("#757575")
                        }
                    )
                    holder.tvTitle.text = item.key
                    holder.tvReason.text = "原因: ${item.reason}"

                    if (item.canDelete) {
                        holder.btnDelete.visibility = View.VISIBLE
                        holder.btnDelete.setOnClickListener {
                            try {
                                TaskBlacklist.removeFromBlacklist(item.key, item.key)
                                ToastUtil.makeText(context, "已移出黑名单: ${item.key}", Toast.LENGTH_SHORT).show()
                                update(loadAllItems())
                            } catch (e: Exception) {
                                Log.error(TAG, "移出黑名单异常: ${e.message}")
                            }
                        }
                    } else {
                        holder.btnDelete.visibility = View.GONE
                        holder.btnDelete.setOnClickListener(null)
                    }
                }

                return rowView
            }
        }

        val adapter = SafeBlacklistAdapter()
        listView.adapter = adapter
        adapter.update(loadAllItems())

        // 绑定搜索输入框联动
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.setFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        MaterialAlertDialogBuilder(context)
            .setTitle("🚫 任务黑名单管理")
            .setView(rootLayout)
            .setPositiveButton("关闭", null)
            .setNeutralButton("清空用户黑名单") { _, _ ->
                if (context is Activity && (context.isFinishing || context.isDestroyed)) return@setNeutralButton
                try {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("确认清空")
                        .setMessage("确定要清空所有自定义和当日黑名单任务吗？清空后被拦截的任务将在下次运行时重新尝试。")
                        .setPositiveButton("确认清空") { _, _ ->
                            try {
                                TaskBlacklist.clearBlacklist()
                                ToastUtil.makeText(context, "已清空所有用户任务黑名单", Toast.LENGTH_SHORT).show()
                                adapter.update(loadAllItems())
                            } catch (e: Exception) {
                                Log.error(TAG, "清空黑名单异常: ${e.message}")
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } catch (t: Throwable) {
                    Log.error(TAG, "弹出清空确认框异常: ${t.message}")
                }
            }
            .setNegativeButton("手动添加") { _, _ ->
                if (context is Activity && (context.isFinishing || context.isDestroyed)) return@setNegativeButton
                try {
                    val input = EditText(context).apply {
                        hint = "输入要拉黑的任务名称或任务ID(至少2字符)"
                        setPadding(40, 30, 40, 30)
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle("手动添加黑名单")
                        .setView(input)
                        .setPositiveButton("添加") { _, _ ->
                            val text = input.text.toString().trim()
                            if (text.length >= 2) {
                                try {
                                    TaskBlacklist.addToBlacklist(text, text)
                                    ToastUtil.makeText(context, "已加入黑名单: $text", Toast.LENGTH_SHORT).show()
                                    adapter.update(loadAllItems())
                                } catch (e: Exception) {
                                    Log.error(TAG, "手动添加黑名单异常: ${e.message}")
                                }
                            } else {
                                ToastUtil.makeText(context, "添加失败：任务关键词至少2个字符", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } catch (t: Throwable) {
                    Log.error(TAG, "弹出手动添加框异常: ${t.message}")
                }
            }
            .show()
    }
}
