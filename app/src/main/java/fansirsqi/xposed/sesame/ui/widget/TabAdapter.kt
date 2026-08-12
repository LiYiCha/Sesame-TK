package fansirsqi.xposed.sesame.ui.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import fansirsqi.xposed.sesame.R

class TabAdapter(private val context: Context, private val titles: MutableList<String?>, private val listener: OnTabClickListener) :
    RecyclerView.Adapter<TabAdapter.ViewHolder?>() {
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = titles[position]
        holder.textView.setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
        val isSelected = (selectedPosition == position)
        holder.itemView.isSelected = isSelected
        
        if (isSelected) {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            holder.textView.setTextColor(typedValue.data)
        } else {
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
        }

        holder.itemView.setOnClickListener { v: View ->
            if (position != selectedPosition) {
                listener.onTabClick(position)
                setSelectedPosition(position)
            }
        }
    }

    override fun getItemCount(): Int {
        return titles.size
    }

    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView = itemView.findViewById(R.id.tab_text)
    }

    interface OnTabClickListener {
        fun onTabClick(position: Int)
    }
}