package me.jbusdriver.mvp.presenter

import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.kotlin.addTo
import me.jbusdriver.base.SchedulersCompat
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.mvp.bean.ResultPageBean
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.base.mvp.presenter.AbstractRefreshLoadMorePresenterImpl
import me.jbusdriver.db.DB
import me.jbusdriver.db.entity.History
import me.jbusdriver.mvp.HistoryContract
import org.jsoup.nodes.Document

class HistoryPresenterImpl : AbstractRefreshLoadMorePresenterImpl<HistoryContract.HistoryView, History>(),
    HistoryContract.HistoryPresenter {

    private val pageSize = 20

    override fun loadData4Page(page: Int) {
        val totalCount = DB.historyDao.count()
        val totalPage = if (totalCount == 0) 1 else (totalCount + pageSize - 1) / pageSize
        val offset = (page - 1) * pageSize

        DB.historyDao.queryByLimit(pageSize, offset)
            .map { list: List<History> ->
                val nextPage = if (page >= totalPage) page else page + 1
                ResultPageBean<History>(
                    pageInfo.copy(activePage = page, nextPage = nextPage),
                    list
                )
            }
            .toFlowable(BackpressureStrategy.BUFFER)
            .compose(SchedulersCompat.io())
            .subscribeWith(object : ListDefaultSubscriber(PageInfo(page)) {

                override fun onNext(t: ResultPageBean<History>) {
                    super.onNext(t)
                    if (page >= totalPage) mView?.loadMoreEnd()
                    mView?.dismissLoading()
                    (page == 1).let {
                        if (it) mView?.enableLoadMore(true) else mView?.enableRefresh(true)
                    }
                }
            })
            .addTo(rxManager)
    }

    override fun clearHistory() {
        DB.historyDao.deleteAll()
    }

    override fun onRefresh() {
        loadData4Page(1)
    }

    override fun lazyLoad() {
        onFirstLoad()
    }

    override val model: BaseModel<Int, Document>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun stringMap(page: PageInfo, str: Document): List<History> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
