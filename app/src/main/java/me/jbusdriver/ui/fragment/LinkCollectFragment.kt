package me.jbusdriver.ui.fragment

import android.graphics.Paint
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.jbusdriver.base.SafeBaseQuickAdapter
import me.jbusdriver.R
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.dpToPx
import me.jbusdriver.base.toast
import me.jbusdriver.db.entity.Category
import me.jbusdriver.db.service.CategoryService
import me.jbusdriver.mvp.LinkCollectContract
import me.jbusdriver.mvp.bean.CollectLinkWrapper
import me.jbusdriver.mvp.bean.ILink
import me.jbusdriver.mvp.bean.LinkCategory
import me.jbusdriver.mvp.bean.SearchLink
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.des
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.LinkCollectPresenterImpl
import me.jbusdriver.ui.activity.MovieListActivity
import me.jbusdriver.ui.activity.SearchResultActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu
import me.jbusdriver.ui.holder.CollectDirEditHolder

class LinkCollectFragment :
    AppBaseRecycleFragment<LinkCollectContract.LinkCollectPresenter, LinkCollectContract.LinkCollectView, CollectLinkWrapper<ILink>>(),
    LinkCollectContract.LinkCollectView {

    private var srRefresh: SwipeRefreshLayout? = null
    private var rvRecycle: RecyclerView? = null

    override val swipeView: SwipeRefreshLayout? by lazy { srRefresh }
    override val recycleView: RecyclerView by lazy { rvRecycle!! }
    override val layoutManager: RecyclerView.LayoutManager by lazy { LinearLayoutManager(viewContext) }

    override val adapter: BaseQuickAdapter<CollectLinkWrapper<ILink>, in BaseViewHolder> by lazy {
        object : SafeBaseQuickAdapter<CollectLinkWrapper<ILink>>(0, null) {

            override fun convert(holder: BaseViewHolder, collect: CollectLinkWrapper<ILink>) {
                when (holder.itemViewType) {
                    -1 -> {
                        val item = requireNotNull(collect.linkBean)
                        val desc = item.des.split(" ")
                        val tvValue = holder.getView<TextView>(R.id.tv_head_value)
                        tvValue?.apply {
                            setTextColor(ResourcesCompat.getColor(resources, R.color.colorPrimaryDark, null))
                            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                        }
                        val dp8 = viewContext.dpToPx(8f)
                        holder.itemView.setPadding(dp8 * 2, dp8, dp8 * 2, dp8)
                        holder.setText(R.id.tv_head_name, desc.firstOrNull())
                        holder.setText(R.id.tv_head_value, desc.lastOrNull())
                    }
                    else -> {
                        setFullSpan(holder)
                        holder.setText(
                            R.id.tv_nav_menu_name,
                            " ${if (collect.isExpanded) "👇" else "👆"} ${collect.category.name}"
                        )
                    }
                }
            }
        }.apply {
            setOnItemClickListener { _, view, position ->
                val data = this@LinkCollectFragment.adapter.data.getOrNull(position)
                    ?: return@setOnItemClickListener
                data.linkBean?.let { link ->
                    if (link is SearchLink) {
                        SearchResultActivity.start(viewContext, link.query)
                    } else MovieListActivity.start(viewContext, link)
                } ?: run {
                    val tvName = view.findViewById<TextView>(R.id.tv_nav_menu_name)
                    tvName?.text = " ${if (data.isExpanded) "👇" else "👆"} ${data.category.name}"
                    val adapterPosition = this@LinkCollectFragment.adapter.headerLayoutCount + position
                    val adp = this@LinkCollectFragment.adapter
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
                (this@LinkCollectFragment.adapter.data.getOrNull(position)?.linkBean)?.let { link ->
                    val action = LinkMenu.linkActions.toMutableMap()
                    action.remove("收藏")
                    if (AppConfiguration.enableCategory) {
                        val category = CategoryService.getById(link.categoryId)
                        if (category != null) {
                            val all = mBasePresenter?.collectGroupMap?.keys ?: emptyList<Category>()
                            val last = all.filter { it != category }
                            if (last.isNotEmpty()) {
                                action["移到分类..."] = { l: ILink ->
                                    MaterialDialog(viewContext).show {
                                        title(text = "选择目录")
                                        listItemsSingleChoice(items = last.map { it.name }) { _, which, _ ->
                                            last.getOrNull(which)?.let { cat ->
                                                mBasePresenter?.setCategory(l, cat)
                                                mBasePresenter?.onRefresh()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                        message(text = link.des)
                        listItems(items = action.keys.toList()) { _, text, _ ->
                            action[text.toString()]?.invoke(link)
                        }
                    }
                }
                true
            }
        }
    }

    override val layoutId: Int = R.layout.layout_swipe_recycle

    override fun initWidget(rootView: View) {
        srRefresh = rootView.findViewById(R.id.sr_refresh)
        rvRecycle = rootView.findViewById(R.id.rv_recycle)
        super.initWidget(rootView)
    }

    override fun createPresenter() = LinkCollectPresenterImpl()
    private val holder by lazy { CollectDirEditHolder(viewContext, LinkCategory) }

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
        fun newInstance() = LinkCollectFragment()
    }
}