package me.jbusdriver.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.flexbox.FlexboxLayoutManager
import me.jbusdriver.R
import me.jbusdriver.base.GSON
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.common.C
import me.jbusdriver.base.fromJson
import me.jbusdriver.base.toJsonString
import me.jbusdriver.mvp.GenreListContract
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.mvp.presenter.GenreListPresenterImpl
import me.jbusdriver.ui.adapter.GenreAdapter

class GenreListFragment :
    AppBaseRecycleFragment<GenreListContract.GenreListPresenter, GenreListContract.GenreListView, Genre>(),
    GenreListContract.GenreListView {

    override fun createPresenter() = GenreListPresenterImpl()

    override val layoutId: Int = R.layout.layout_swipe_recycle

    private var srRefresh: SwipeRefreshLayout? = null
    private var rvRecycle: RecyclerView? = null

    override val swipeView: SwipeRefreshLayout? by lazy { srRefresh }
    override val recycleView: RecyclerView by lazy { rvRecycle!! }
    override val layoutManager: RecyclerView.LayoutManager by lazy {
        FlexboxLayoutManager(requireContext()).apply {
            isAutoMeasureEnabled = true
        }
    }

    override fun initWidget(rootView: View) {
        srRefresh = rootView.findViewById(R.id.sr_refresh)
        rvRecycle = rootView.findViewById(R.id.rv_recycle)
        super.initWidget(rootView)
    }

    override val adapter = GenreAdapter()

    override val data by lazy {
        arguments?.getString(C.BundleKey.Key_1)?.let { GSON.fromJson<List<Genre>>(it) }
            ?: emptyList()
    }

    companion object {
        fun newInstance(genres: List<Genre>) = GenreListFragment().apply {
            arguments = Bundle().apply {
                putString(C.BundleKey.Key_1, genres.toJsonString())
            }
        }
    }
}
