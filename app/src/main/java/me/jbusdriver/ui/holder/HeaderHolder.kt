package me.jbusdriver.ui.holder

import android.content.Context
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.inflate
import me.jbusdriver.mvp.bean.Header
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.des
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.ui.activity.MovieListActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu

/**
 * Created by Administrator on 2017/5/9 0009.
 */
class HeaderHolder(context: Context) : BaseHolder(context) {


    val view by lazy {
        weakRef.get()?.let {
            it.inflate(R.layout.layout_detail_header).apply {
                val rvRecycleHeader = findViewById<RecyclerView>(R.id.rv_recycle_header)
                rvRecycleHeader.layoutManager = LinearLayoutManager(this.context)
                rvRecycleHeader.adapter = headAdapter
                rvRecycleHeader.isNestedScrollingEnabled = true
            }
        } ?: error("context ref is finish")
    }

    private val headAdapter = object : BaseQuickAdapter<Header, BaseViewHolder>(R.layout.layout_header_item, null) {
        override fun convert(holder: BaseViewHolder, item: Header) {
            holder.getView<TextView>(R.id.tv_head_value)?.apply {
                if (!TextUtils.isEmpty(item.link)) {
                    setTextColor(ResourcesCompat.getColor(this@apply.resources, R.color.colorPrimaryDark, null))
                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG

                    setOnClickListener {
                        MovieListActivity.start(it.context, item)
                    }

                } else {
                    setTextColor(ResourcesCompat.getColor(this@apply.resources, R.color.secondText, null))
                    paintFlags = 0
                    setOnClickListener(null)
                }
                setOnLongClickListener {
                    val action = LinkMenu.linkActions.filter {
                        when {
                            TextUtils.isEmpty(item.link) -> it.key == "复制"
                            CollectModel.has(item.convertDBItem()) -> it.key != "收藏"
                            else -> it.key != "取消收藏"
                        }
                    }.toMutableMap()

                    if (AppConfiguration.enableCategory) {
                        val ac = action.remove("收藏")
                        if (ac != null) {
                            action["收藏到分类..."] = ac
                        }
                    }

                    MaterialDialog(holder.itemView.context).show {
                        title(text = item.name)
                        message(text = item.des)
                        listItems(items = action.keys.toList()) { _, index, _ ->
                            action.values.toList().getOrNull(index)?.invoke(item)
                        }
                    }
                    return@setOnLongClickListener true

                }
            }
            holder.setText(R.id.tv_head_name, item.name)
                .setText(R.id.tv_head_value, item.value)
        }
    }

    fun init(data: List<Header>) {
        if (data.isEmpty()) view.findViewById<View>(R.id.tv_movie_head_none_tip).visibility = View.VISIBLE
        else {
            headAdapter.setList(data)
        }
    }

}
