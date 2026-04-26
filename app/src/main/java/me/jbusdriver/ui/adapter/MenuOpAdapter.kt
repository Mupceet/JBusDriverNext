package me.jbusdriver.ui.adapter

import android.view.View
import android.widget.CompoundButton
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chad.library.adapter.base.entity.MultiItemEntity
import me.jbusdriver.R
import me.jbusdriver.mvp.bean.Expand_Type_Head
import me.jbusdriver.mvp.bean.Expand_Type_Item
import me.jbusdriver.mvp.bean.MenuOp
import me.jbusdriver.mvp.bean.MenuOpHead

class MenuOpAdapter(data: MutableList<MultiItemEntity>) : BaseMultiItemQuickAdapter<MultiItemEntity, BaseViewHolder>(data) {

    init {
        addItemType(Expand_Type_Head, R.layout.layout_menu_op_head)
        addItemType(Expand_Type_Item, R.layout.layout_menu_op_item)
    }

    override fun convert(holder: BaseViewHolder, item: MultiItemEntity) {
        when (item.itemType) {
            Expand_Type_Head -> {
                (item as? MenuOpHead)?.let { head ->
                    holder.setText(R.id.tv_nav_menu_name, " ${if (head.isExpanded) "👆" else "👉"} ${head.name}")
                    holder.itemView.setOnClickListener {
                        val pos = holder.bindingAdapterPosition
                        if (pos == -1) return@setOnClickListener
                        if (head.isExpanded) {
                            // collapse: remove sub items
                            val subCount = head.subItems.size
                            head.isExpanded = false
                            for (i in 0 until subCount) {
                                data.removeAt(pos + 1)
                            }
                            notifyItemRangeRemoved(pos + 1, subCount)
                            holder.setText(R.id.tv_nav_menu_name, " 👉 ${head.name}")
                        } else {
                            // expand: add sub items
                            head.isExpanded = true
                            data.addAll(pos + 1, head.subItems)
                            notifyItemRangeInserted(pos + 1, head.subItems.size)
                            holder.setText(R.id.tv_nav_menu_name, " 👆 ${head.name}")
                        }
                    }
                }
            }
            Expand_Type_Item -> {
                (item as? MenuOp)?.let {
                    holder.setText(R.id.tv_menu_op_name, it.name)
                    holder.itemView.findViewById<CompoundButton>(R.id.cb_nav_menu)?.isChecked = it.isHow
                }
            }
        }
    }
}