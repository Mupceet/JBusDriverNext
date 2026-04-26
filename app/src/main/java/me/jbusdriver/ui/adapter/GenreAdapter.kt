package me.jbusdriver.ui.adapter

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.module.LoadMoreModule
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.mvp.bean.ILink
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.des
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.ui.activity.MovieListActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu

/**
 * Created by Administrator on 2017/7/30.
 */
open class GenreAdapter : BaseQuickAdapter<Genre, BaseViewHolder>(R.layout.layout_genre_item, null), LoadMoreModule {


    override fun convert(holder: BaseViewHolder, item: Genre) {
        holder.setText(R.id.tv_movie_genre, item.name)
        (holder.getView<TextView>(R.id.tv_movie_genre).background as? GradientDrawable)?.apply {
            setColor(holder.itemView.resources.getColor(R.color.colorPrimary))
        }
    }

    init {


        setOnItemClickListener { _, view, position ->
            data.getOrNull(position)?.let { genre ->
                MovieListActivity.start(view.context, genre)
            }
        }

        setOnItemLongClickListener { adapter, view, position ->
            (adapter.data.getOrNull(position) as? Genre)?.let { item ->
                val action = (if (CollectModel.has((item as ILink).convertDBItem())) LinkMenu.linkActions.minus("收藏")
                else LinkMenu.linkActions.minus("取消收藏")).toMutableMap()

                if (AppConfiguration.enableCategory) {
                    val ac = action.remove("收藏")
                    if (ac != null) {
                        action["收藏到分类..."] = ac
                    }
                }

                MaterialDialog(view.context).show {
                    title(text = item.name)
                    message(text = item.des)
                    listItems(items = action.keys.toList()) { _, index, _ ->
                        action.values.toList().getOrNull(index)?.invoke(item)
                    }
                }
            }
            true
        }
    }
}
