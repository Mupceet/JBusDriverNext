package me.jbusdriver.mvp.presenter

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import me.jbusdriver.base.SchedulersCompat
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.mvp.bean.hasNext
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.base.mvp.presenter.AbstractRefreshLoadMorePresenterImpl
import me.jbusdriver.mvp.bean.ICollectCategory
import me.jbusdriver.mvp.bean.ILink
import me.jbusdriver.db.DB
import me.jbusdriver.db.entity.Category
import me.jbusdriver.mvp.bean.ActressCategory
import me.jbusdriver.mvp.bean.LinkCategory
import me.jbusdriver.mvp.bean.MovieCategory
import me.jbusdriver.mvp.bean.MovieDBType
import me.jbusdriver.mvp.bean.ActressDBType
import me.jbusdriver.mvp.ActressCollectContract
import me.jbusdriver.mvp.MovieCollectContract
import me.jbusdriver.mvp.bean.CollectLinkWrapper
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.ui.data.AppConfiguration
import org.jsoup.nodes.Document


abstract class BaseAbsCollectPresenter<V : BaseView.BaseListWithRefreshView, T : ICollectCategory> :
    AbstractRefreshLoadMorePresenterImpl<V, T>(), BaseCollectPresenter<T> {


    protected open val pageSize = 20

    private val ancestor by lazy {
        when {
            this is MovieCollectContract.MovieCollectPresenter -> MovieCategory
            this is ActressCollectContract.ActressCollectPresenter -> ActressCategory
            else -> LinkCategory
        }
    }
    private val listData = mutableListOf<ILink>()

    private val pageNum
        get() = ((listData.size - 1) / pageSize) + 1

    override val collectGroupMap: MutableMap<Category, List<T>> = mutableMapOf()

    override val adapterDelegate: BaseCollectPresenter.CollectMultiTypeDelegate<T> =
        BaseCollectPresenter.CollectMultiTypeDelegate()


    private fun load(): Flowable<List<ILink>> = when {
        this is MovieCollectContract.MovieCollectPresenter ->
            Flowable.fromCallable { DB.linkDao.listByType(MovieDBType) }
        this is ActressCollectContract.ActressCollectPresenter ->
            Flowable.fromCallable { DB.linkDao.listByType(ActressDBType) }
        else ->
            Flowable.fromCallable { DB.linkDao.queryLink() }
    }.map { items -> items.mapNotNull { it.getLinkValue() } }

    override fun onFirstLoad() {
        //通过refresh加载，loadData4Page
        onRefresh()
    }

    override fun loadData4Page(page: Int) {
        //查询所有的分类 //优化:先查20个

        if (AppConfiguration.enableCategory) {
            //一次性加载完成
            Flowable.fromCallable {
                if (ancestor.id == null) emptyList<me.jbusdriver.db.entity.Category>()
                else DB.categoryDao.queryTreeByLike(ancestor.tree + "%").blockingFirst()
            }
                .flatMap { Flowable.fromIterable(it) }
                .map { cate ->
                    val parent = CollectLinkWrapper<T>(cate).apply {
                        adapterDelegate.needInjectType.add(level)
                    }
                    val list = DB.linkDao.queryByCategoryId(cate.id)
                    val items = mutableListOf<T>()
                    list.forEach {
                        val mapValue = it.getLinkValue() as? T
                        if (mapValue != null) {
                            parent.addSubItem(CollectLinkWrapper(cate, mapValue).apply {
                                adapterDelegate.needInjectType.add(level)
                            })
                            items.add(mapValue)
                        }
                    }
                    collectGroupMap[cate] = items
                    parent
                }

                .toList()
                .doOnSubscribe { mView?.showLoading() }
                .doAfterTerminate { mView?.dismissLoading() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy({
                    mView?.showError(it)
                }, {
                    mView?.resetList()
                    mView?.showContents(it)
                    mView?.loadMoreComplete()
                    mView?.loadMoreEnd()

                })
                .addTo(rxManager)

        } else {
            val next = if (page < pageNum) page + 1 else pageNum
            pageInfo = pageInfo.copy(activePage = page, nextPage = next)
            Flowable.just(pageInfo).map {
                val start = (pageInfo.activePage - 1) * pageSize
                val nextSize = start + pageSize
                val end = if (nextSize <= listData.size) nextSize else listData.size
                listData.subList(start, end).mapNotNull {
                    val data = it.convertDBItem().getLinkValue()
                    if (data != null) {
                        CollectLinkWrapper(null, data).apply {
                            adapterDelegate.needInjectType.add(level)
                        }
                    } else null
                }
            }.doOnSubscribe { mView?.showLoading() }
                .doAfterTerminate { mView?.dismissLoading() }
                .compose(SchedulersCompat.io())
                .subscribeBy({
                    mView?.showError(it)
                }, {
                    if (!pageInfo.hasNext) mView?.loadMoreEnd()
                }, {
                    mView?.showContents(it)
                    mView?.loadMoreComplete()
                }).addTo(rxManager)
        }


    }

    override fun onRefresh() {
        mView?.showLoading()
        listData.clear()
        if (AppConfiguration.enableCategory) {
            collectGroupMap.clear()
        }
        mView?.resetList()
        load().doOnNext { listData.addAll(it) }
            .compose(SchedulersCompat.io())
            .subscribe {
                loadData4Page(1)
            }.addTo(rxManager)
    }

    override val model: BaseModel<Int, Document>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun stringMap(page: PageInfo, str: Document): List<T> {
        TODO("not implemented") //To change body of created functions use File or Settings | File Templates.
    }


    override fun setCategory(t: T, category: Category) {
        require(t is ILink && category.id > 0)
        val dbItem = (t as ILink).convertDBItem().copy(categoryId = category.id)
        CollectModel.update(dbItem)
    }
}
