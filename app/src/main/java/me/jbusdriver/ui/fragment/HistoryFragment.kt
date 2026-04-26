package me.jbusdriver.ui.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.collection.ArrayMap
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.jbusdriver.base.SafeBaseQuickAdapter
import me.jbusdriver.R
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.db.entity.History
import me.jbusdriver.mvp.HistoryContract
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.ILink
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.SearchLink
import me.jbusdriver.mvp.bean.des
import me.jbusdriver.mvp.presenter.HistoryPresenterImpl
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment :
    AppBaseRecycleFragment<HistoryContract.HistoryPresenter, HistoryContract.HistoryView, History>(),
    HistoryContract.HistoryView {

    override fun createPresenter() = HistoryPresenterImpl()

    override val layoutId: Int = R.layout.layout_swipe_recycle

    private var srRefresh: SwipeRefreshLayout? = null
    private var rvRecycle: RecyclerView? = null

    override val swipeView: SwipeRefreshLayout? by lazy { srRefresh }
    override val recycleView: RecyclerView by lazy { rvRecycle!! }
    override val layoutManager: RecyclerView.LayoutManager by lazy { LinearLayoutManager(viewContext) }

    override fun initWidget(rootView: View) {
        srRefresh = rootView.findViewById(R.id.sr_refresh)
        rvRecycle = rootView.findViewById(R.id.rv_recycle)
        super.initWidget(rootView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: android.view.MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.add(Menu.NONE, Menu.NONE, 10, "清除历史记录").apply {
            setIcon(R.drawable.ic_delete_24dp)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                mBasePresenter?.clearHistory()
                adapter.setList(null)
                adapter.notifyDataSetChanged()
                true
            }
        }
    }

    override val adapter: BaseQuickAdapter<History, in BaseViewHolder> by lazy {
        object : SafeBaseQuickAdapter<History>(R.layout.layout_history_item) {

            val linkCache by lazy { ArrayMap<Int, ILink>() }
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

            override fun convert(holder: BaseViewHolder, item: History) {
                val itemLink = linkCache.getOrPut(item.hashCode()) { item.getLinkItem() }
                val appender = if (itemLink !is Movie && itemLink !is SearchLink) {
                    if (item.isAll != 0) "全部电影" else "已有种子电影"
                } else ""
                val tvDate = holder.getView<TextView>(R.id.tv_history_date)
                val tvTitle = holder.getView<TextView>(R.id.tv_history_title)
                tvDate?.text = format.format(item.createTime)
                tvTitle?.text = itemLink.des + appender

                val img by lazy {
                    (itemLink as? ActressInfo)?.avatar ?: (itemLink as? Movie)?.imageUrl ?: ""
                }

                if (img.isNotBlank()) {
                    val ivIcon = holder.getView<ImageView>(R.id.iv_history_icon)
                    ivIcon?.let {
                        it.visibility = View.VISIBLE
                        Glide.with(holder.itemView).load(img.toGlideNoHostUrl).into(it)
                    }
                } else {
                    val ivIcon = holder.getView<ImageView>(R.id.iv_history_icon)
                    ivIcon?.let {
                        if (it.visibility != View.GONE) it.visibility = View.GONE
                    }
                }
            }
        }.apply {
            setOnItemClickListener { _, view, position ->
                data.getOrNull(position)?.let { history ->
                    history.move(view.context)
                }
            }
        }
    }

    companion object {
        fun newInstance() = HistoryFragment()
    }
}
