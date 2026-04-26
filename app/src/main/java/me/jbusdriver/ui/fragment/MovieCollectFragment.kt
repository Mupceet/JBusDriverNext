package me.jbusdriver.ui.fragment

import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.module.LoadMoreModule
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.toast
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.db.entity.Category
import me.jbusdriver.db.service.CategoryService
import me.jbusdriver.mvp.MovieCollectContract
import me.jbusdriver.mvp.bean.CollectLinkWrapper
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.MovieCategory
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.MovieCollectPresenterImpl
import me.jbusdriver.ui.activity.MovieDetailActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu
import me.jbusdriver.ui.holder.CollectDirEditHolder

class MovieCollectFragment :
    AppBaseRecycleFragment<MovieCollectContract.MovieCollectPresenter, MovieCollectContract.MovieCollectView, CollectLinkWrapper<Movie>>(),
    MovieCollectContract.MovieCollectView {

    private var srRefresh: SwipeRefreshLayout? = null
    private var rvRecycle: RecyclerView? = null

    override val swipeView: SwipeRefreshLayout? by lazy { srRefresh }
    override val recycleView: RecyclerView by lazy { rvRecycle!! }
    override val layoutManager: RecyclerView.LayoutManager by lazy { LinearLayoutManager(viewContext) }
    override val layoutId: Int = R.layout.layout_swipe_recycle

    override fun initWidget(rootView: View) {
        srRefresh = rootView.findViewById(R.id.sr_refresh)
        rvRecycle = rootView.findViewById(R.id.rv_recycle)
        super.initWidget(rootView)
    }

    override val adapter: BaseQuickAdapter<CollectLinkWrapper<Movie>, in BaseViewHolder> by lazy {
        object : BaseQuickAdapter<CollectLinkWrapper<Movie>, BaseViewHolder>(0, null), LoadMoreModule {

            override fun convert(holder: BaseViewHolder, item: CollectLinkWrapper<Movie>) {
                when (holder.itemViewType) {
                    -1 -> {
                        val movie = requireNotNull(item.linkBean)
                        holder.setText(R.id.tv_movie_title, movie.title)
                            .setText(R.id.tv_movie_date, movie.date)
                            .setText(R.id.tv_movie_code, movie.code)

                        val ivMovieImg = holder.getView<ImageView>(R.id.iv_movie_img)
                        Glide.with(viewContext).load(movie.imageUrl.toGlideNoHostUrl)
                            .placeholder(R.drawable.ic_image_error)
                            .error(R.drawable.ic_image_error).centerCrop()
                            .into(DrawableImageViewTarget(ivMovieImg))

                        holder.getView<View>(R.id.card_movie_item)?.setOnClickListener {
                            MovieDetailActivity.start(viewContext, movie)
                        }
                    }
                    else -> {
                        setFullSpan(holder)
                        holder.setText(
                            R.id.tv_nav_menu_name,
                            " ${if (item.isExpanded) "👇" else "👆"} ${item.category.name}"
                        )
                    }
                }
            }
        }.apply {
            setOnItemClickListener { _, view, position ->
                val data = this@MovieCollectFragment.adapter.data.getOrNull(position)
                    ?: return@setOnItemClickListener
                data.linkBean?.let { movie ->
                    MovieDetailActivity.start(viewContext, movie)
                } ?: run {
                    val tvName = view.findViewById<TextView>(R.id.tv_nav_menu_name)
                    tvName?.text = " ${if (data.isExpanded) "👇" else "👆"} ${data.category.name}"
                    val adapterPosition = this@MovieCollectFragment.adapter.headerLayoutCount + position
                    val adp = this@MovieCollectFragment.adapter
                    if (data.isExpanded) {
                        data.isExpanded = false
                        val subCount = data.subItems.size
                        for (i in 0 until subCount) { adp.data.removeAt(adapterPosition + 1) }
                        adp.notifyItemRangeRemoved(adapterPosition + 1, subCount)
                        adp.notifyItemChanged(adapterPosition)
                    } else {
                        data.isExpanded = true
                        adp.data.addAll(adapterPosition + 1, data.subItems)
                        adp.notifyItemRangeInserted(adapterPosition + 1, data.subItems.size)
                        adp.notifyItemChanged(adapterPosition)
                    }
                }
            }

            setOnItemLongClickListener { _, _, position ->
                (this@MovieCollectFragment.adapter.data.getOrNull(position)?.linkBean)?.let { movie ->
                    val action = LinkMenu.movieActions.toMutableMap()
                    if (AppConfiguration.enableCategory) {
                        val category = CategoryService.getById(movie.categoryId)
                        if (category != null) {
                            val all = mBasePresenter?.collectGroupMap?.keys ?: emptyList<Category>()
                            val last = all.filter { it != category }
                            if (last.isNotEmpty()) {
                                action["移到分类..."] = { link: Movie ->
                                    MaterialDialog(viewContext).show {
                                        title(text = "选择目录")
                                        listItemsSingleChoice(items = last.map { it.name }) { _, which, _ ->
                                            last.getOrNull(which)?.let { cat ->
                                                mBasePresenter?.setCategory(link, cat)
                                                mBasePresenter?.onRefresh()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    action.remove("收藏")
                    action["取消收藏"] = {
                        val result = CollectModel.removeCollect(it.convertDBItem())
                        if (result != null && result > 0) {
                            toast("取消收藏成功")
                            adapter.data.removeAt(position)
                            adapter.notifyItemRemoved(position)
                        } else {
                            toast("已经取消了")
                        }
                    }

                    MaterialDialog(viewContext).show {
                        title(text = movie.code)
                        message(text = movie.title)
                        listItems(items = action.keys.toList()) { _, text, _ ->
                            action[text.toString()]?.invoke(movie)
                        }
                    }
                }
                true
            }
        }
    }

    private val holder by lazy { CollectDirEditHolder(viewContext, MovieCategory) }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.action_collect_dir_edit)?.setOnMenuItemClickListener {
            holder.showDialogWithData(
                mBasePresenter?.collectGroupMap?.keys?.toList() ?: emptyList()
            ) { delActionsParams, addActionsParams ->
                if (delActionsParams.isNotEmpty()) {
                    delActionsParams.forEach { cat ->
                        try {
                            CategoryService.delete(cat.id)
                        } catch (e: Exception) {
                            toast("不能删除默认分类")
                        }
                    }
                }
                if (addActionsParams.isNotEmpty()) {
                    addActionsParams.forEach { cat ->
                        CategoryService.insert(cat)
                    }
                }
                mBasePresenter?.onRefresh()
            }
            true
        }
    }

    override fun createPresenter() = MovieCollectPresenterImpl()

    override fun showContents(data: List<*>) {
        super.showContents(data)
        if (AppConfiguration.enableCategory) {
            adapter.data.firstOrNull()?.let { first ->
                if (first.linkBean == null && !first.isExpanded) {
                    first.isExpanded = true
                    adapter.data.addAll(1, first.subItems)
                    adapter.notifyItemRangeInserted(1, first.subItems.size)
                    adapter.notifyItemChanged(0)
                }
            }
        }
    }

    companion object {
        fun newInstance() = MovieCollectFragment()
    }
}