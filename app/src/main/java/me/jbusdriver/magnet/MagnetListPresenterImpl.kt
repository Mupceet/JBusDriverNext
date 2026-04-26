package me.jbusdriver.magnet

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.kotlin.addTo
import me.jbusdriver.base.*
import me.jbusdriver.common.KLog
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.mvp.bean.ResultPageBean
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.base.mvp.presenter.AbstractRefreshLoadMorePresenterImpl
import org.jsoup.nodes.Document

class MagnetListPresenterImpl(private val magnetLoaderKey: String, private val keyword: String) :
    AbstractRefreshLoadMorePresenterImpl<MagnetListContract.MagnetListView, Magnet>(),
    MagnetListContract.MagnetListPresenter {


    override val model: BaseModel<Int, Document>
        get() = error("not call model")

    override fun stringMap(pageInfo: PageInfo, str: Document): List<Magnet> = error("not call stringMap")


    override fun loadData4Page(page: Int) {
        val curPage = PageInfo(page, page + 1)
        val cacheKey = "${magnetLoaderKey}_${keyword}_${curPage.activePage}"
        //page 1
        val cache = Flowable.concat(CacheLoader.justLru(cacheKey), CacheLoader.justDisk(cacheKey)).firstElement()
            .map { GSON.fromJson<List<Magnet>>(it) }.toFlowable()
        val loaderFormNet = Flowable.fromCallable {
            // Direct loader call via MagnetManager singleton
            return@fromCallable try {
                GSON.fromJson<List<Magnet>>(MagnetManager.getMagnets(magnetLoaderKey, keyword, page))
            } catch (e: Exception) {
                e.printStackTrace()
                KLog.w("loadMagnets error happen $-> $e")
                emptyList<Magnet>()
            }
        }.doOnNext {
            if (it.isNotEmpty() && page <= 1) {
                CacheLoader.cacheDisk(cacheKey to it)
                CacheLoader.cacheLru(cacheKey to it)
            }
        }
        Flowable.concat(cache, loaderFormNet).firstOrError().toFlowable()
            .map { ResultPageBean(curPage, it) }
            .compose(SchedulersCompat.io())
            .subscribeWith(ListDefaultSubscriber(curPage))
            .addTo(rxManager)
    }

    override fun lazyLoad() {
        onFirstLoad()
    }

    override fun hasLoadNext(): Boolean = MagnetManager.hasNext(magnetLoaderKey).also {
        if (!it) {
            lastPage = pageInfo.activePage
        }
    }

    override fun onRefresh() {
        (0..Math.max(pageInfo.activePage, pageInfo.nextPage)).onEach {
            CacheLoader.lru.remove("${magnetLoaderKey}_${keyword}_$it")
            CacheLoader.acache.remove("${magnetLoaderKey}_${keyword}_$it")
        }
        super.onRefresh()
    }

    override fun fetchMagLink(url: String) = MagnetManager.fetchMagLink(magnetLoaderKey, url)
}
