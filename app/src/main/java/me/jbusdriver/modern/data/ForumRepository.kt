package me.jbusdriver.modern.data

import kotlinx.coroutines.flow.Flow
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.ForumCacheTtl
import me.jbusdriver.modern.core.cache.firstCachedOrFresh
import me.jbusdriver.modern.core.cache.observeCached
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.parser.parseForumHomeData
import me.jbusdriver.modern.data.parser.parseForumThreadDetail
import me.jbusdriver.modern.data.parser.parseForumThreads
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumRepo"

private fun forumLogD(message: String) {
    runCatching { KLog.d(message, TAG) }
}

private fun forumLogE(message: String) {
    runCatching { KLog.e(message, TAG) }
}

interface ForumRepository {
    suspend fun loadForumBoards(forceRefresh: Boolean = false): ForumHomeData
    suspend fun loadThreads(
        fid: Int,
        page: Int,
        typeId: Int? = null,
        forceRefresh: Boolean = false
    ): ForumThreadPageResult

    suspend fun loadThreadDetail(
        tid: Int,
        page: Int = 1,
        floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
        forceRefresh: Boolean = false
    ): ForumThreadDetail

    fun observeForumBoards(
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<ForumHomeData>>

    fun observeThreads(
        fid: Int,
        page: Int,
        typeId: Int? = null,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<ForumThreadPageResult>>

    fun observeThreadDetail(
        tid: Int,
        page: Int = 1,
        floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<ForumThreadDetail>>
}

@Singleton
class DefaultForumRepository @Inject constructor(
    private val sessionClient: ForumSessionClient,
    private val cookiePersister: ForumCookiePersister,
    private val cacheStore: CacheStore,
    private val siteConfig: SiteConfig
) : ForumRepository {

    private val cookiesPersistedForForum = AtomicBoolean(false)

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        val doc = sessionClient.fetchDocument(url)
        forumLogD("[Forum] fetched: title=${doc.title()}, length=${doc.html().length}")
        // Persist cookies after first successful forum page fetch
        // to capture Discuz! session cookies (4fJN_2132_*)
        if (cookiesPersistedForForum.compareAndSet(false, true)) {
            try {
                cookiePersister.persistCookies()
            } catch (e: Exception) {
                forumLogE("[Forum] Failed to persist cookies: ${e.message}")
                cookiesPersistedForForum.set(false)
            }
        }
        return doc
    }

    override fun observeForumBoards(
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumHomeData>> {
        val url = "${siteConfig.baseUrl}/forum/forum.php"
        forumLogD("[Forum] loadForumBoards: url=$url")
        return cacheStore.observeCached(
            key = forumBoardsCacheKey(),
            ttlMillis = ForumCacheTtl.HOME_MILLIS,
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = revalidate,
            nowMillis = nowMillis,
            isCacheable = { it.boardGroups.isNotEmpty() }
        ) {
            val doc = fetchForumDocument(url)
            val result = parseForumHomeData(doc, siteConfig.baseUrl)
            forumLogD("[Forum] parseForumHomeData: ${result.banners.size} banners, ${result.boardGroups.sumOf { it.boards.size }} boards")
            result
        }
    }

    override fun observeThreads(
        fid: Int,
        page: Int,
        typeId: Int?,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumThreadPageResult>> {
        val baseUrl = "${siteConfig.baseUrl}/forum/forum.php?mod=forumdisplay&fid=$fid&page=$page"
        val url = if (typeId != null) "$baseUrl&filter=typeid&typeid=$typeId" else baseUrl
        forumLogD("[Forum] loadThreads: url=$url")
        return cacheStore.observeCached(
            key = forumThreadsCacheKey(fid, page, typeId),
            ttlMillis = threadListTtl(page),
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = revalidate && page == 1,
            nowMillis = nowMillis
        ) {
            val doc = fetchForumDocument(url)
            parseForumThreads(doc, siteConfig.baseUrl)
        }
    }

    override fun observeThreadDetail(
        tid: Int,
        page: Int,
        floorOrder: ForumFloorOrder,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumThreadDetail>> {
        val url = buildForumThreadDetailUrl(siteConfig.baseUrl, tid, page, floorOrder)
        forumLogD("[Forum] loadThreadDetail: url=$url")
        return cacheStore.observeCached(
            key = forumDetailCacheKey(tid, page, floorOrder),
            ttlMillis = threadDetailTtl(page),
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = revalidate && page == 1,
            nowMillis = nowMillis
        ) {
            val doc = fetchForumDocument(url)
            parseForumThreadDetail(doc, siteConfig.baseUrl)
        }
    }

    override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData =
        observeForumBoards(forceRefresh = forceRefresh, revalidate = false).firstCachedOrFresh()

    override suspend fun loadThreads(
        fid: Int,
        page: Int,
        typeId: Int?,
        forceRefresh: Boolean
    ): ForumThreadPageResult =
        observeThreads(
            fid,
            page,
            typeId,
            forceRefresh = forceRefresh,
            revalidate = false
        ).firstCachedOrFresh()

    override suspend fun loadThreadDetail(
        tid: Int,
        page: Int,
        floorOrder: ForumFloorOrder,
        forceRefresh: Boolean
    ): ForumThreadDetail =
        observeThreadDetail(
            tid,
            page,
            floorOrder,
            forceRefresh = forceRefresh,
            revalidate = false
        ).firstCachedOrFresh()

    private fun forumCachePrefix(): String = "forum:${siteConfig.baseUrl}"

    private fun forumBoardsCacheKey(): String = "${forumCachePrefix()}:boards"

    private fun forumThreadsCacheKey(fid: Int, page: Int, typeId: Int?): String =
        "${forumCachePrefix()}:threads:$fid:$page:${typeId ?: "all"}"

    private fun forumDetailCacheKey(tid: Int, page: Int, floorOrder: ForumFloorOrder): String =
        "${forumCachePrefix()}:detail:v2:$tid:$page:${floorOrder.name.lowercase()}"

    private fun threadListTtl(page: Int): Long =
        if (page == 1) ForumCacheTtl.THREAD_LIST_FIRST_PAGE_MILLIS
        else ForumCacheTtl.THREAD_LIST_NEXT_PAGE_MILLIS

    private fun threadDetailTtl(page: Int): Long =
        if (page == 1) ForumCacheTtl.THREAD_DETAIL_FIRST_PAGE_MILLIS
        else ForumCacheTtl.THREAD_DETAIL_NEXT_PAGE_MILLIS

}
