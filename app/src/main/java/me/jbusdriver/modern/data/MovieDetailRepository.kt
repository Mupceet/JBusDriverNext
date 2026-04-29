package me.jbusdriver.modern.data

import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.urlPath
import me.jbusdriver.modern.data.remote.JAVBusService
import me.jbusdriver.modern.domain.model.MovieDetail
import me.jbusdriver.modern.domain.model.parseMovieDetails
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface MovieDetailRepository {
    suspend fun getMovieDetail(url: String, forceRefresh: Boolean = false): MovieDetail
}

@Singleton
class DefaultMovieDetailRepository @Inject constructor() : MovieDetailRepository {

    override suspend fun getMovieDetail(url: String, forceRefresh: Boolean): MovieDetail {
        val cacheKey = url.urlPath

        if (!forceRefresh) {
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
