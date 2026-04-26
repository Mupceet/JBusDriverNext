package me.jbusdriver.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.SchedulersCompat
import me.jbusdriver.base.browse
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.common.C
import me.jbusdriver.base.copy
import me.jbusdriver.base.toast
import me.jbusdriver.common.KLog
import me.jbusdriver.magnet.Magnet
import me.jbusdriver.magnet.MagnetListContract.MagnetListPresenter
import me.jbusdriver.magnet.MagnetListContract.MagnetListView
import me.jbusdriver.magnet.MagnetListPresenterImpl

const val MagnetFormatPrefix = "magnet:?xt=urn:btih:"

class MagnetListFragment : AppBaseRecycleFragment<MagnetListPresenter, MagnetListView, Magnet>(), MagnetListView {

    private val keyword by lazy { arguments?.getString(C.BundleKey.Key_1) ?: error("need keyword") }
    private val magnetLoaderKey by lazy {
        arguments?.getString(C.BundleKey.Key_2) ?: error("need magnet loaderKey")
    }

    override fun createPresenter() = MagnetListPresenterImpl(magnetLoaderKey, keyword)

    override val layoutId: Int = R.layout.comp_magnet_layout_swipe_recycle

    private var compMagnetSrRefresh: SwipeRefreshLayout? = null
    private var compMagnetRvRecycle: RecyclerView? = null

    override val swipeView: SwipeRefreshLayout? by lazy { compMagnetSrRefresh }
    override val recycleView: RecyclerView by lazy { compMagnetRvRecycle!! }
    override val layoutManager: RecyclerView.LayoutManager by lazy { LinearLayoutManager(viewContext) }

    override fun initWidget(rootView: View) {
        compMagnetSrRefresh = rootView.findViewById(R.id.comp_magnet_sr_refresh)
        compMagnetRvRecycle = rootView.findViewById(R.id.comp_magnet_rv_recycle)
        super.initWidget(rootView)
    }

    override val adapter: BaseQuickAdapter<Magnet, in BaseViewHolder> by lazy {
        object : BaseQuickAdapter<Magnet, BaseViewHolder>(R.layout.comp_magnet_layout_magnet_item) {

            override fun convert(holder: BaseViewHolder, item: Magnet) {
                holder.setText(R.id.comp_magnet_tv_magnet_title, item.name)
                    .setText(R.id.comp_magnet_tv_magnet_date, item.date)
                    .setText(R.id.comp_magnet_tv_magnet_size, item.size)
                    addChildClickViewIds(R.id.comp_magnet_iv_magnet_copy)
            }
        }.apply {
            fun tryGetMagnetLink(mag: Magnet): Flowable<String> {
                return Flowable.just(mag).flatMap { mag ->
                    if (!mag.link.startsWith(MagnetFormatPrefix)) {
                        Flowable.fromCallable {
                            mBasePresenter?.fetchMagLink(mag.link) ?: mag.link
                        }
                    } else {
                        Flowable.just(mag.link)
                    }
                }
            }

            setOnItemClickListener { _, _, position ->
                data.getOrNull(position)?.let { magnet ->
                    showMagnetLoading()
                    tryGetMagnetLink(magnet)
                        .compose(SchedulersCompat.io()).subscribeBy {
                            this@MagnetListFragment.adapter.setData(position, magnet.copy(link = it))
                            KLog.d("magnet $it")
                            viewContext.browse(it) {
                                placeDialogHolder?.dismiss()
                            }
                        }.addTo(rxManager)
                }
            }

            setOnItemChildClickListener { _, view, position ->
                data.getOrNull(position)?.let { magnet ->
                    when (view.id) {
                        R.id.comp_magnet_iv_magnet_copy -> {
                            tryGetMagnetLink(magnet).compose(SchedulersCompat.io()).subscribeBy { url ->
                                this@MagnetListFragment.adapter.setData(position, magnet.copy(link = url))
                                view.context.apply {
                                    copy(url)
                                    toast("复制成功")
                                }
                            }.addTo(rxManager)
                        }
                    }
                }
            }
        }
    }

    private fun showMagnetLoading() {
        placeDialogHolder = MaterialDialog(viewContext).show {
            message(text = "正在查询磁力信息...")
        }
    }

    override fun onPause() {
        super.onPause()
        placeDialogHolder?.dismiss()
    }

    companion object {
        fun newInstance(keyword: String, loaderKey: String) = MagnetListFragment().apply {
            arguments = Bundle().apply {
                putString(C.BundleKey.Key_1, keyword)
                putString(C.BundleKey.Key_2, loaderKey)
            }
        }
    }
}
