package me.jbusdriver.ui.fragment

import android.content.res.Resources
import android.graphics.Bitmap
import android.text.TextUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.jbusdriver.base.SafeBaseQuickAdapter
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.kotlin.addTo
import me.jbusdriver.R
import me.jbusdriver.base.SimpleSubscriber
import me.jbusdriver.base.SchedulersCompat
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.toast
import me.jbusdriver.common.KLog
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.db.entity.Category
import me.jbusdriver.db.service.CategoryService
import me.jbusdriver.mvp.ActressCollectContract
import me.jbusdriver.mvp.bean.ActressCategory
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.CollectLinkWrapper
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.ActressCollectPresenterImpl
import me.jbusdriver.ui.activity.MovieListActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu
import me.jbusdriver.ui.holder.CollectDirEditHolder
import java.util.*


class ActressCollectFragment :
    AppBaseRecycleFragment<ActressCollectContract.ActressCollectPresenter, ActressCollectContract.ActressCollectView, CollectLinkWrapper<ActressInfo>>(),
    ActressCollectContract.ActressCollectView {

    private var srRefresh: SwipeRefreshLayout? = null
    private var rvRecycle: RecyclerView? = null

    override val swipeView: SwipeRefreshLayout? by lazy { srRefresh }
    override val recycleView: RecyclerView by lazy { rvRecycle!! }
    override val layoutManager: RecyclerView.LayoutManager by lazy {
        StaggeredGridLayoutManager(2, OrientationHelper.VERTICAL)
    }

    override val adapter: BaseQuickAdapter<CollectLinkWrapper<ActressInfo>, in BaseViewHolder> by lazy {
        object : SafeBaseQuickAdapter<CollectLinkWrapper<ActressInfo>>(0, null) {
            private val random = Random()
            private fun randomNum(number: Int) = Math.abs(random.nextInt() % number)

            override fun convert(holder: BaseViewHolder, item: CollectLinkWrapper<ActressInfo>) {
                when (holder.itemViewType) {
                    -1 -> {
                        val actress = requireNotNull(item.linkBean)

                        val ivAvatar = holder.getView<ImageView>(R.id.iv_actress_avatar)
                        Glide.with(holder.itemView).asBitmap().load(actress.avatar.toGlideNoHostUrl)
                            .error(R.drawable.ic_image_error)
                            .into(object : BitmapImageViewTarget(ivAvatar) {
                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                    Flowable.just(resource).map {
                                        Palette.from(resource).generate()
                                    }.compose(SchedulersCompat.io())
                                        .subscribeWith(object : SimpleSubscriber<Palette>() {
                                            override fun onNext(t: Palette) {
                                                super.onNext(t)
                                                val swatch = listOfNotNull(
                                                    t.lightMutedSwatch,
                                                    t.lightVibrantSwatch,
                                                    t.vibrantSwatch,
                                                    t.mutedSwatch
                                                )
                                                if (swatch.isNotEmpty()) {
                                                    swatch[randomNum(swatch.size)].let { s ->
                                                        holder.setBackgroundColor(R.id.tv_actress_name, s.rgb)
                                                        holder.setTextColor(R.id.tv_actress_name, s.bodyTextColor)
                                                    }
                                                }
                                            }
                                        })
                                        .addTo(rxManager)
                                    super.onResourceReady(resource, transition)
                                }
                            })
                        holder.setText(R.id.tv_actress_name, actress.name)
                        holder.setText(R.id.tv_actress_tag, actress.tag)
                        val tvTag = holder.getView<TextView>(R.id.tv_actress_tag)
                        tvTag?.visibility = if (TextUtils.isEmpty(actress.tag)) View.GONE else View.VISIBLE
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
                val data = this@ActressCollectFragment.adapter.data.getOrNull(position)
                    ?: return@setOnItemClickListener
                data.linkBean?.let { act ->
                    MovieListActivity.start(viewContext, act)
                } ?: run {
                    val tvName = view.findViewById<TextView>(R.id.tv_nav_menu_name)
                    tvName?.text = " ${if (data.isExpanded) "👇" else "👆"} ${data.category.name}"
                    val adapterPosition = this@ActressCollectFragment.adapter.headerLayoutCount + position
                    val adp = this@ActressCollectFragment.adapter
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
                    (layoutManager as StaggeredGridLayoutManager).invalidateSpanAssignments()
                }
            }

            setOnItemLongClickListener { _, _, position ->
                (this@ActressCollectFragment.adapter.data.getOrNull(position)?.linkBean)?.let { act ->
                    val action = LinkMenu.actressActions.toMutableMap()
                    action.remove("收藏")
                    if (AppConfiguration.enableCategory) {
                        val category = CategoryService.getById(act.categoryId)
                        if (category != null) {
                            val all = mBasePresenter?.collectGroupMap?.keys ?: emptyList<Category>()
                            val last = all.filter { it != category }
                            if (last.isNotEmpty()) {
                                action["移到分类..."] = { link: ActressInfo ->
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
                        title(text = act.name)
                        listItems(items = action.keys.toList()) { _, text, _ ->
                            action[text.toString()]?.invoke(act)
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

    private val holder by lazy { CollectDirEditHolder(viewContext, ActressCategory) }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.action_collect_dir_edit)?.setOnMenuItemClickListener {
            holder.showDialogWithData(
                mBasePresenter?.collectGroupMap?.keys?.toList() ?: emptyList()
            ) { delActionsParams, addActionsParams ->
                KLog.d("$delActionsParams $addActionsParams")
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

    override fun createPresenter() = ActressCollectPresenterImpl()

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
        fun newInstance() = ActressCollectFragment()
    }
}