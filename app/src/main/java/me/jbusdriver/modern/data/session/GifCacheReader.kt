package me.jbusdriver.modern.data.session

import android.content.Context
import androidx.core.net.toUri
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 查询 GIF 是否仍落在 Coil 磁盘缓存中。
 *
 * 背景：[LoadedGifTracker] 只记录"用户曾经加载过"的 GIF URL，但磁盘缓存可能因 LRU 淘汰
 * 或缓存被清理而失效。若仅凭"加载过"就直接渲染，Coil 会重新走网络下载，造成流量浪费。
 * 决定是否自动展示某个已加载 GIF 前，需用它二次确认磁盘缓存仍然存在。
 */
@Singleton
interface GifCacheReader {
    /** 返回 [urls] 中确实存在于 Coil 磁盘缓存的子集（主线程安全）。 */
    suspend fun presentInDiskCache(urls: Set<String>): Set<String>
}

@OptIn(ExperimentalCoilApi::class)
@Singleton
class CoilGifCacheReader @Inject constructor(
    @param:ApplicationContext private val context: Context
) : GifCacheReader {

    override suspend fun presentInDiskCache(urls: Set<String>): Set<String> {
        if (urls.isEmpty()) return emptySet()
        val diskCache = context.imageLoader.diskCache ?: return emptySet()
        return withContext(Dispatchers.IO) {
            // Coil 的 HttpUriFetcher 以 url（经 Uri 规整后）作为磁盘缓存 key，
            // 这里用同一规整方式查询，保证 key 与写入时一致。
            urls.filterTo(mutableSetOf()) { url ->
                val key = url.toUri().toString()
                diskCache.openSnapshot(key)?.let { snapshot ->
                    snapshot.close()
                    true
                } ?: false
            }
        }
    }
}
