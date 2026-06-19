package me.jbusdriver.modern.core.cache

import me.jbusdriver.BuildConfig

data class CacheEntry<T>(
    val value: T,
    val storedAtMillis: Long,
    val source: CacheSource,
    val isExpired: Boolean
)

sealed interface CachedLoadEvent<out T> {
    data class Cached<T>(val entry: CacheEntry<T>) : CachedLoadEvent<T>
    data class Fresh<T>(val entry: CacheEntry<T>) : CachedLoadEvent<T>
    data class Failure(
        val throwable: Throwable,
        val hadCachedValue: Boolean
    ) : CachedLoadEvent<Nothing>
}

enum class CacheSource {
    Memory,
    Disk,
    Network
}

data class CacheEnvelope(
    val storedAtMillis: Long,
    val payloadJson: String
)

object ForumCacheTtl {
    val HOME_MILLIS: Long = cacheRefreshTtl(10_000L, 5 * 60_000L)
    val THREAD_LIST_FIRST_PAGE_MILLIS: Long = cacheRefreshTtl(10_000L, 2 * 60_000L)
    val THREAD_LIST_NEXT_PAGE_MILLIS: Long = cacheRefreshTtl(30_000L, 5 * 60_000L)
    val THREAD_DETAIL_FIRST_PAGE_MILLIS: Long = cacheRefreshTtl(10_000L, 15 * 60_000L)
    val THREAD_DETAIL_NEXT_PAGE_MILLIS: Long = cacheRefreshTtl(30_000L, 10 * 60_000L)
}

object MovieCacheTtl {
    val MOVIE_LIST_FIRST_PAGE_MILLIS: Long = cacheRefreshTtl(10_000L, 60 * 60_000L)
    val MOVIE_LIST_NEXT_PAGE_MILLIS: Long = cacheRefreshTtl(30_000L, 60 * 60_000L)
    val ACTRESS_LIST_FIRST_PAGE_MILLIS: Long = cacheRefreshTtl(10_000L, 12 * 60 * 60_000L)
    val ACTRESS_LIST_NEXT_PAGE_MILLIS: Long = cacheRefreshTtl(30_000L, 12 * 60 * 60_000L)
    val GENRE_CATEGORIES_MILLIS: Long = cacheRefreshTtl(15_000L, 24 * 60 * 60_000L)
    val MOVIE_BY_URL_FIRST_PAGE_MILLIS: Long = cacheRefreshTtl(10_000L, 60 * 60_000L)
    val MOVIE_BY_URL_NEXT_PAGE_MILLIS: Long = cacheRefreshTtl(30_000L, 60 * 60_000L)
}

private fun cacheRefreshTtl(testMillis: Long, productionMillis: Long): Long =
    if (BuildConfig.CACHE_REFRESH_TEST_MODE) testMillis else productionMillis

fun <T> List<T>.simulateCacheRefreshChange(): List<T> =
    if (BuildConfig.CACHE_REFRESH_TEST_MODE && size >= 3) drop(1) else this
