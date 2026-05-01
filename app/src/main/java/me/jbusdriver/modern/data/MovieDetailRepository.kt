package me.jbusdriver.modern.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.urlPath
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.remote.JAVBusService
import me.jbusdriver.modern.domain.model.MovieDetail
import me.jbusdriver.modern.domain.model.parseMovieDetails
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
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
            val request = Request.Builder().url(url).build()
            val call = NetClient.apiClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cont.resumeWith(Result.failure(e))
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            cont.resumeWith(Result.success(body))
                        } else {
                            cont.resumeWith(Result.failure(IllegalStateException("Empty response")))
                        }
                    } catch (e: Exception) {
                        cont.resumeWith(Result.failure(e))
                    }
                }
            })
        }

        val detail = withContext(Dispatchers.Default) {
            val doc = Jsoup.parse(html)
            parseMovieDetails(doc)
        }

        // Cache to LRU + disk
        CacheLoader.cacheLruAndDisk(cacheKey to detail)

        return detail
    }
}
