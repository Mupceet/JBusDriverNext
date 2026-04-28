package me.jbusdriver.modern.data

import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
import me.jbusdriver.base.fromJson
import me.jbusdriver.base.urlPath
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.mvp.bean.MovieDetail
import me.jbusdriver.mvp.bean.parseMovieDetails
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface MovieDetailRepository {
    suspend fun getMovieDetail(url: String): MovieDetail
}

@Singleton
class DefaultMovieDetailRepository @Inject constructor() : MovieDetailRepository {

    override suspend fun getMovieDetail(url: String): MovieDetail {
        val cacheKey = url.urlPath

        // Check LRU memory cache
        CacheLoader.lru.get(cacheKey)?.let {
            return GSON.fromJson<MovieDetail>(it) ?: error("Corrupt cache for $cacheKey")
        }

        // Check disk cache
        CacheLoader.acache.getAsString(cacheKey)?.let {
            GSON.fromJson<MovieDetail>(it)?.let { cached ->
                CacheLoader.lru.put(cacheKey, GSON.toJson(cached))
                return cached
            }
        }

        // Fetch from network
        val html = suspendCancellableCoroutine<String> { cont ->
            val disposable = JAVBusService.INSTANCE.get(url)
                .subscribe(
                    { html -> cont.resumeWith(Result.success(html)) },
                    { error -> cont.resumeWith(Result.failure(error)) }
                )
            cont.invokeOnCancellation { disposable.dispose() }
        }

        val doc = Jsoup.parse(html)
        val detail = parseMovieDetails(doc)

        // Cache to LRU + disk
        CacheLoader.cacheLruAndDisk(cacheKey to detail)

        return detail
    }
}
