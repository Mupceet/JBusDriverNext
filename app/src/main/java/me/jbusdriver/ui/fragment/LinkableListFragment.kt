package me.jbusdriver.ui.fragment

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.input
import android.text.InputType
import android.text.TextUtils
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.getSp
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.saveSp
import me.jbusdriver.base.toast
import me.jbusdriver.base.inflate
import me.jbusdriver.common.KLog
import me.jbusdriver.common.RxBus
import me.jbusdriver.mvp.LinkListContract
import me.jbusdriver.mvp.bean.PageChangeEvent
import me.jbusdriver.ui.activity.SearchResultActivity
import me.jbusdriver.ui.data.AppConfiguration

abstract class LinkableListFragment<T> :
    AppBaseRecycleFragment<LinkListContract.LinkListPresenter, LinkListContract.LinkListView, T>(),
    LinkListContract.LinkListView {

    override val layoutId: Int = R.layout.layout_swipe_recycle

    private var srRefresh: SwipeRefreshLayout? = null
    private var rvRecycle: RecyclerView? = null

    override val swipeView: SwipeRefreshLayout? by lazy { srRefresh }
    override val recycleView: RecyclerView by lazy { rvRecycle!! }
    override val layoutManager: RecyclerView.LayoutManager
        get() = when (currentLayoutType) {
            OrientationHelper.VERTICAL -> layoutManagers.getOrPut(OrientationHelper.VERTICAL) {
                LinearLayoutManager(viewContext)
            }
            OrientationHelper.HORIZONTAL -> layoutManagers.getOrPut(OrientationHelper.HORIZONTAL) {
                StaggeredGridLayoutManager(2, OrientationHelper.VERTICAL)
            }
            else -> LinearLayoutManager(viewContext)
        }

    private val layoutManagers = hashMapOf<Int, RecyclerView.LayoutManager>()

    private var currentLayoutType = getSp("layout_type")?.toIntOrNull()
        ?: OrientationHelper.VERTICAL

    override fun initWidget(rootView: View) {
        srRefresh = rootView.findViewById(R.id.sr_refresh)
        rvRecycle = rootView.findViewById(R.id.rv_recycle)
        super.initWidget(rootView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindRx()
    }

    private fun bindRx() {
        RxBus.toFlowable(PageChangeEvent::class.java)
            .subscribeBy(onNext = {
                activity?.invalidateOptionsMenu()
                mBasePresenter?.onRefresh()
            }).addTo(rxManager)
    }

    override fun restoreState(bundle: Bundle) {
        super.restoreState(bundle)
        val all = bundle.getBoolean(MENU_SHOW_ALL, false)
        tempSaveBundle.putBoolean(MENU_SHOW_ALL, all)
        if (all) {
            mBasePresenter?.setAll(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)
        menu.getItem(0)?.let {
            val mSearchView = MenuItemCompat.getActionView(it) as SearchView

            mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    if (TextUtils.isEmpty(query)) toast("关键字不能为空!")
                    gotoSearchResult(query)
                    return true
                }

                override fun onQueryTextChange(newText: String) = false
            })
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_show_all)?.isChecked = tempSaveBundle.getBoolean(MENU_SHOW_ALL, false)
        menu.findItem(R.id.action_jump)?.let {
            it.isVisible = AppConfiguration.pageMode == AppConfiguration.PageMode.Page
        }
    }

    protected open fun gotoSearchResult(query: String) {
        SearchResultActivity.start(viewContext, query)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.action_show_all -> {
                item.isChecked = !item.isChecked
                if (item.isChecked) item.title = "已发布" else item.title = "全部电影"
                mBasePresenter?.setAll(item.isChecked)
                mBasePresenter?.loadData4Page(1)
                tempSaveBundle.putBoolean(MENU_SHOW_ALL, item.isChecked)
            }
            R.id.action_jump -> {
                mBasePresenter?.currentPageInfo?.let {
                    showPageDialog(it)
                }
            }
            R.id.action_switch_layout -> {
                val pos = when (val lm = recycleView.layoutManager) {
                    is LinearLayoutManager -> lm.findFirstVisibleItemPosition()
                    is StaggeredGridLayoutManager -> lm.findFirstCompletelyVisibleItemPositions(
                        intArrayOf(0, 0)
                    ).firstOrNull() ?: 0
                    else -> 0
                }
                currentLayoutType =
                    if (currentLayoutType == OrientationHelper.HORIZONTAL) OrientationHelper.VERTICAL else OrientationHelper.HORIZONTAL
                saveSp("layout_type", currentLayoutType.toString())
                recycleView.layoutManager = layoutManager
                recycleView.adapter = adapter
                recycleView.layoutManager?.scrollToPosition(pos)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(MENU_SHOW_ALL, tempSaveBundle.getBoolean(MENU_SHOW_ALL, false))
    }

    protected fun showPageDialog(info: PageInfo) {
        if (info.referPages.isEmpty()) return
        if (info.referPages.size == 1 && info.referPages.first() == 1) {
            toast("当前共一页")
            return
        }
        val seekView = viewContext.inflate(R.layout.layout_seek_page)
        val bsbSeekPage = seekView.findViewById<SeekBar>(R.id.bsb_seek_page)
        bsbSeekPage?.apply {
            try {
                val max = this.javaClass.getDeclaredField("mMax")
                max?.isAccessible = true
                max?.setFloat(this, info.referPages.last().toFloat())

                this.javaClass.getDeclaredMethod("initConfigByPriority").also {
                    it.isAccessible = true
                    it.invoke(this)
                }

                setProgress(info.activePage)
                this@apply.post {
                    this.invalidate()
                    this.requestLayout()
                }
            } catch (e: Exception) {
                KLog.w("error :$e")
            }
        }
        MaterialDialog(viewContext).show {
            customView(view = seekView, scrollable = false)
            neutralButton(text = "输入页码") {
                showEditDialog(info)
            }
            positiveButton(text = "跳转") {
                bsbSeekPage?.progress?.let {
                    mBasePresenter?.jumpToPage(it)
                    adapter.loadMoreModule.loadMoreComplete()
                }
            }
        }
    }

    private fun showEditDialog(info: PageInfo) {
        MaterialDialog(viewContext).show {
            title(text = "输入页码:")
            input(hint = "输入跳转页码", prefill = "", allowEmpty = false, inputType = InputType.TYPE_CLASS_NUMBER) { dialog, text ->
                text.toString().toIntOrNull()?.let {
                    if (it < 1) {
                        toast("必须输入大于0的整数!")
                        return@input
                    }
                    mBasePresenter?.jumpToPage(it)
                    dialog.dismiss()
                } ?: let {
                    toast("必须输入数字!")
                }
            }
            cancelOnTouchOutside(false)
            neutralButton(text = "选择页码") {
                showPageDialog(info)
            }
        }
    }

    override val pageMode: Int
        get() = AppConfiguration.pageMode

    companion object {
        const val MENU_SHOW_ALL = "action_show_all"
    }
}
