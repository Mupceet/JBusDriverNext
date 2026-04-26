package me.jbusdriver.modern.data

import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
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
        // Check disk cache first
        val cacheKey = url.urlPath
        val cached = CacheLoader.acache.getAsString(cacheKey)
        if (!cached.isNullOrBlank()) {
            val cachedDetail = GSON.fromJson<MovieDetail>(cached)
            if (cachedDetail != null) return cachedDetail
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

        // Cache to disk
        CacheLoader.cacheDisk(cacheKey to detail)

        return detail
    }
}
