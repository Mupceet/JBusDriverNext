package me.jbusdriver.base.common

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import io.reactivex.rxjava3.core.Flowable
import me.jbusdriver.R
import me.jbusdriver.base.dpToPx
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.presenter.BasePresenter

abstract class AppBaseRecycleFragment<P : BasePresenter.BaseRefreshLoadMorePresenter<V>, V : BaseView.BaseListWithRefreshView, M> :
    AppBaseFragment<P, V>(), BaseView.BaseListWithRefreshView {

    abstract val swipeView: SwipeRefreshLayout?
    abstract val recycleView: RecyclerView
    abstract val layoutManager: RecyclerView.LayoutManager
    abstract val adapter: BaseQuickAdapter<M, in BaseViewHolder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun initWidget(rootView: View) {
        recycleView.layoutManager = layoutManager
        recycleView.adapter = adapter

        swipeView?.setColorSchemeResources(R.color.colorPrimary, R.color.colorPrimaryDark, R.color.colorPrimaryLight)
        swipeView?.setOnRefreshListener { mBasePresenter?.onRefresh() }

        adapter.loadMoreModule.setOnLoadMoreListener { mBasePresenter?.onLoadMore() }
    }

    override fun showLoading() {
        swipeView?.let {
            if (!it.isRefreshing) {
                it.post {
                    it.setProgressViewOffset(false, 0, viewContext.dpToPx(24f))
                    it.isRefreshing = true
                }
            }
        } ?: super.showLoading()
    }

    override fun dismissLoading() {
        swipeView?.let { it.post { it.isRefreshing = false } } ?: super.dismissLoading()
    }

    override fun showContents(data: List<*>) {
        @Suppress("UNCHECKED_CAST")
        (data as? MutableList<M> ?: data.toMutableList() as? MutableList<M>)?.let {
            adapter.addData(it)
        }
    }

    override fun loadMoreComplete() {
        adapter.loadMoreModule.loadMoreComplete()
    }

    override fun loadMoreEnd(clickable: Boolean) {
        if (!adapter.hasEmptyView() && adapter.data.isEmpty()) {
            adapter.setEmptyView(EmptyState.NoData(viewContext).getEmptyView())
        }
        adapter.loadMoreModule.loadMoreEnd(clickable)
    }

    override fun loadMoreFail() {
        if (!adapter.hasEmptyView() && adapter.data.isEmpty()) {
            adapter.setEmptyView(EmptyState.ErrorEmpty(viewContext).getEmptyView().apply {
                setOnClickListener { mBasePresenter?.onRefresh() }
            })
        }
        adapter.loadMoreModule.loadMoreFail()
    }

    override fun enableRefresh(bool: Boolean) {
        swipeView?.isEnabled = bool
    }

    override fun enableLoadMore(bool: Boolean) {
        (adapter as? com.chad.library.adapter.base.BaseQuickAdapter<*, *>)?.loadMoreModule?.isEnableLoadMore = bool
    }

    override fun getRequestParams(page: Int): Flowable<String> = Flowable.empty()

    override fun resetList() {
        adapter.setList(null)
    }

    override fun showError(e: Throwable?) {
        adapter.loadMoreModule.loadMoreFail()
    }

    sealed class EmptyState(val tip: String) {
        class NoData(val context: Context) : EmptyState("没有数据") {
            override fun getEmptyView(): View {
                return TextView(context).apply {
                    text = tip
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.MarginLayoutParams.MATCH_PARENT,
                        context.dpToPx(36f)
                    ).apply { gravity = Gravity.CENTER }
                }
            }
        }

        class ErrorEmpty(val context: Context) : EmptyState("加载失败") {
            override fun getEmptyView(): View {
                return TextView(context).apply {
                    text = tip
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.MarginLayoutParams.MATCH_PARENT,
                        context.dpToPx(36f)
                    ).apply { gravity = Gravity.CENTER }
                }
            }
        }

        abstract fun getEmptyView(): View
    }
}
