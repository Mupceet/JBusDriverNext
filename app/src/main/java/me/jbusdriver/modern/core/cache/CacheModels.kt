package me.jbusdriver.modern.core.cache

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
    const val HOME_MILLIS: Long = 10 * 1_000L
    const val THREAD_LIST_FIRST_PAGE_MILLIS: Long = 10 * 1_000L
    const val THREAD_LIST_NEXT_PAGE_MILLIS: Long = 10 * 1_000L
    const val THREAD_DETAIL_FIRST_PAGE_MILLIS: Long = 10 * 1_000L
    const val THREAD_DETAIL_NEXT_PAGE_MILLIS: Long = 10 * 1_000L
}
