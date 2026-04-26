package me.jbusdriver.mvp.presenter

import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.base.mvp.presenter.AbstractRefreshLoadMorePresenterImpl
import me.jbusdriver.mvp.bean.ILink
import me.jbusdriver.mvp.HotRecommendContract
import org.jsoup.nodes.Document
import java.util.concurrent.atomic.AtomicInteger

class HotRecommendPresenterImpl : AbstractRefreshLoadMorePresenterImpl<HotRecommendContract.HotRecommendView, ILink>(),
    HotRecommendContract.HotRecommendPresenter {

    private val count = AtomicInteger(1)

    override val model: BaseModel<Int, Document>
        get() = error("not call model")

    override fun stringMap(pageInfo: PageInfo, str: Document) = error("not call stringMap")

    override fun onFirstLoad() {
        loadData4Page(count.get())
    }

    override fun loadData4Page(page: Int) {
        mView?.showLoading()
        mView?.showContents(emptyList<ILink>())
        mView?.loadMoreEnd(false)
        mView?.dismissLoading()
    }

    override fun onLoadMore() {
        if (lastPage < count.incrementAndGet()) {
            count.set(1)
        }
        loadData4Page(count.get())
    }

    override fun hasLoadNext() = false
    override fun onRefresh() {
        if (lastPage < count.get()) {
            count.set(1)
        }
        rxManager.clear()
        loadData4Page(count.get())
    }
}