package me.jbusdriver.ui.fragment

import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.dpToPx
import me.jbusdriver.base.urlHost
import me.jbusdriver.base.urlPath
import me.jbusdriver.base.toast
import me.jbusdriver.base.common.C
import me.jbusdriver.common.isEndWithXyzHost
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.bean.ILink
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.ui.activity.MovieDetailActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu
import me.jbusdriver.ui.data.enums.DataSourceType


abstract class AbsMovieListFragment : LinkableListFragment<Movie>() {

    override val type: DataSourceType by lazy {
        arguments?.getSerializable(MOVIE_LIST_DATA_TYPE) as? DataSourceType ?: let {
            (arguments?.getSerializable(C.BundleKey.Key_1) as? ILink)?.let { link ->
                val path = link.link.urlPath
                val t = when {
                    link.link.urlHost.isEndWithXyzHost -> {
                        when {
                            path.startsWith("genre") -> DataSourceType.GENRE
                            path.startsWith("star") -> DataSourceType.ACTRESSES
                            else -> DataSourceType.CENSORED
                        }
                    }
                    else -> {
                        when {
                            path.startsWith("uncensored") -> {
                                when {
                                    path.startsWith("uncensored/genre") -> DataSourceType.GENRE
                                    path.startsWith("uncensored/star") -> DataSourceType.ACTRESSES
                                    else -> DataSourceType.CENSORED
                                }
                            }
                            else -> {
                                when {
                                    path.startsWith("genre") -> DataSourceType.GENRE
                                    path.startsWith("star") -> DataSourceType.ACTRESSES
                                    else -> DataSourceType.CENSORED
                                }
                            }
                        }
                    }
                }
                t
            } ?: DataSourceType.CENSORED
        }
    }

    override val adapter: BaseQuickAdapter<Movie, in BaseViewHolder> by lazy {
        object : BaseQuickAdapter<Movie, BaseViewHolder>(0, null) {

            private val Movie.isInValid
                inline get() = TextUtils.isEmpty(code) && TextUtils.isEmpty(link)

            override fun getItemViewType(position: Int): Int {
                val item = data.getOrNull(position) ?: return 1
                return when {
                    item.isInValid -> -1
                    recyclerView?.layoutManager is LinearLayoutManager -> OrientationHelper.VERTICAL
                    recyclerView?.layoutManager is StaggeredGridLayoutManager -> OrientationHelper.HORIZONTAL
                    else -> 1
                }
            }

            override fun onCreateDefViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
                val layout = when (viewType) {
                    -1 -> R.layout.layout_pager_section_item
                    OrientationHelper.VERTICAL -> R.layout.layout_page_line_movie_item
                    else -> R.layout.layout_page_line_movie_item_hor
                }
                return createBaseViewHolder(parent, layout)
            }

            private val dp8 by lazy { this@AbsMovieListFragment.viewContext.dpToPx(8f) }
            private val backColors = listOf(0xff2195f3.toInt(), 0xff4caf50.toInt(), 0xffff0030.toInt())

            private fun genLp() = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                this@AbsMovieListFragment.viewContext.dpToPx(24f)
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }

            override fun convert(holder: BaseViewHolder, item: Movie) {
                when (holder.itemViewType) {
                    -1 -> {
                        setFullSpan(holder)
                        holder.setText(R.id.tv_page_num, item.title)
                        val currentPage = item.title.toIntOrNull()
                        if (currentPage != null) {
                            holder.setGone(
                                R.id.tv_load_prev, mBasePresenter?.isPrevPageLoaded(currentPage) ?: true
                            )
                            holder.getView<View>(R.id.tv_load_prev)?.setOnClickListener {
                                mBasePresenter?.jumpToPage(currentPage - 1)
                            }
                        }
                    }
                    OrientationHelper.HORIZONTAL, OrientationHelper.VERTICAL -> {
                        when (pageMode) {
                            AppConfiguration.PageMode.Page -> holder.setGone(R.id.v_line, true)
                            AppConfiguration.PageMode.Normal -> holder.setGone(R.id.v_line, false)
                        }

                        holder.setText(R.id.tv_movie_title, item.title)
                            .setText(R.id.tv_movie_date, item.date)
                            .setText(R.id.tv_movie_code, item.code)

                        val ivMovieImg = holder.getView<android.widget.ImageView>(R.id.iv_movie_img)
                        Glide.with(this@AbsMovieListFragment).load(item.imageUrl.toGlideNoHostUrl)
                            .placeholder(R.drawable.ic_image_error)
                            .error(R.drawable.ic_image_error).centerCrop()
                            .into(DrawableImageViewTarget(ivMovieImg))

                        val llHot = holder.getView<LinearLayout>(R.id.ll_movie_hot)
                        llHot?.removeAllViews()
                        item.tags?.mapIndexed { index, tag ->
                            val tv = (LayoutInflater.from(viewContext).inflate(R.layout.tv_movie_tag, llHot, false) as TextView).apply {
                                text = tag
                                if (holder.itemViewType == OrientationHelper.HORIZONTAL) {
                                    textSize = 11f
                                }
                                setPadding(dp8, 0, dp8, 0)
                                background = GradientDrawable().apply {
                                    setColor(backColors.getOrNull(index % 3) ?: backColors.first())
                                    cornerRadius = if (holder.itemViewType == OrientationHelper.HORIZONTAL) {
                                        dp8 * 1.5f
                                    } else {
                                        dp8 * 2f
                                    }
                                }
                                layoutParams = genLp().apply {
                                    if (holder.itemViewType == OrientationHelper.VERTICAL) {
                                        leftMargin = dp8
                                    } else {
                                        rightMargin = dp8
                                    }
                                }
                            }
                            llHot?.addView(tv)
                        }

                        holder.getView<View>(R.id.card_movie_item)?.let { card ->
                            card.setOnClickListener {
                                MovieDetailActivity.start(viewContext, item)
                            }
                            card.setOnLongClickListener {
                                val action =
                                    (if (CollectModel.has(item.convertDBItem())) LinkMenu.movieActions.minus("收藏")
                                    else LinkMenu.movieActions.minus("取消收藏")).toMutableMap()
                                if (AppConfiguration.enableCategory) {
                                    val ac = action.remove("收藏")
                                    if (ac != null) {
                                        action["收藏到分类..."] = ac
                                    }
                                }
                                MaterialDialog(viewContext).show {
                                    title(text = item.code)
                                    message(text = item.title)
                                    listItems(items = action.keys.toList()) { _, text, _ ->
                                        action[text.toString()]?.invoke(item)
                                    }
                                }
                                true
                            }
                        }
                    }
                }
            }
        }.apply {
            setOnItemClickListener { _, _, position ->
                (this@AbsMovieListFragment.adapter.data.getOrNull(position) as? Movie)?.let {
                    val viewType = this@AbsMovieListFragment.adapter.getItemViewType(position)
                    if (viewType == -1) {
                        mBasePresenter?.currentPageInfo?.let { info ->
                            if (info.referPages.isNotEmpty()) showPageDialog(info)
                        }
                    }
                }
            }
        }
    }

    override fun insertData(pos: Int, data: List<*>) {
        adapter.addData(pos, data.filterIsInstance<Movie>().toMutableList())
    }

    override fun moveTo(pos: Int) {
        layoutManager.scrollToPosition(adapter.headerLayoutCount + pos)
    }

    companion object {
        const val MOVIE_LIST_DATA_TYPE = "movie:list:data:type"
    }
}