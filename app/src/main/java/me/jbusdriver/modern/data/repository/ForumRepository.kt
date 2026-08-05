package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.ForumCacheTtl
import me.jbusdriver.modern.core.cache.firstCachedOrFresh
import me.jbusdriver.modern.core.cache.observeCached
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.parser.parseForumFloorComments
import me.jbusdriver.modern.data.parser.parseForumHomeData
import me.jbusdriver.modern.data.parser.parseForumThreadDetail
import me.jbusdriver.modern.data.parser.parseForumThreads
import me.jbusdriver.modern.core.http.BrowserCookiePersister
import me.jbusdriver.modern.core.http.BrowserSessionClient
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.buildForumThreadDetailUrl
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
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
        authorUid: Int? = null,
        forceRefresh: Boolean = false
    ): ForumThreadDetail

    suspend fun loadFloorComments(
        tid: Int,
        pid: Int,
        page: Int,
        forceRefresh: Boolean = false
    ): ForumCommentPageResult

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
        authorUid: Int? = null,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<ForumThreadDetail>>
}

@Singleton
class DefaultForumRepository @Inject constructor(
    private val sessionClient: BrowserSessionClient,
    private val cookiePersister: BrowserCookiePersister,
    private val cacheStore: CacheStore,
    private val siteConfig: SiteConfig
) : ForumRepository {

    private val cookiesPersistedForForum = AtomicBoolean(false)

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        val doc = sessionClient.fetchDocument(url)
        forumLogD("[Forum] fetched: title=${doc.title()}, length=${doc.html().length}")
        persistCookiesAfterFetch()
        return doc
    }

    private suspend fun fetchForumAjaxDocument(
        url: String,
        referer: String
    ): org.jsoup.nodes.Document {
        val doc = sessionClient.fetchAjaxDocument(url, referer)
        forumLogD("[Forum] ajax fetched: title=${doc.title()}, length=${doc.html().length}")
        persistCookiesAfterFetch()
        return doc
    }

    private suspend fun persistCookiesAfterFetch() {
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
    }

    override fun observeForumBoards(
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumHomeData>> = flow {
        // 等待站点配置就绪，避免冷启动时用默认域名构建 URL/缓存 key（用户可能已选镜像）。
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = "$baseUrl/forum/forum.php"
        forumLogD("[Forum] loadForumBoards: url=$url")
        emitAll(
            cacheStore.observeCached(
                key = forumBoardsCacheKey(baseUrl),
                ttlMillis = ForumCacheTtl.HOME_MILLIS,
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = revalidate,
                nowMillis = nowMillis,
                isCacheable = { it.boardGroups.isNotEmpty() }
            ) {
                val doc = fetchForumDocument(url)
                val result = parseForumHomeData(doc, baseUrl)
                forumLogD("[Forum] parseForumHomeData: ${result.banners.size} banners, ${result.boardGroups.sumOf { it.boards.size }} boards")
                result
            }
        )
    }

    override fun observeThreads(
        fid: Int,
        page: Int,
        typeId: Int?,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumThreadPageResult>> = flow {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val displayUrl = "$baseUrl/forum/forum.php?mod=forumdisplay&fid=$fid&page=$page"
        val url = if (typeId != null) "$displayUrl&filter=typeid&typeid=$typeId" else displayUrl
        forumLogD("[Forum] loadThreads: url=$url")
        emitAll(
            cacheStore.observeCached(
                key = forumThreadsCacheKey(baseUrl, fid, page, typeId),
                ttlMillis = threadListTtl(page),
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = revalidate && page == 1,
                nowMillis = nowMillis
            ) {
                val doc = fetchForumDocument(url)
                parseForumThreads(doc, baseUrl)
            }
        )
    }

    override fun observeThreadDetail(
        tid: Int,
        page: Int,
        floorOrder: ForumFloorOrder,
        authorUid: Int?,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumThreadDetail>> = flow {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = buildForumThreadDetailUrl(baseUrl, tid, page, floorOrder, authorUid)
        forumLogD("[Forum] loadThreadDetail: url=$url")
        emitAll(
            cacheStore.observeCached(
                key = forumDetailCacheKey(baseUrl, tid, page, floorOrder, authorUid),
                ttlMillis = threadDetailTtl(page),
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = revalidate && page == 1,
                nowMillis = nowMillis
            ) {
                val doc = fetchForumDocument(url)
                parseForumThreadDetail(doc, baseUrl)
            }
        )
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
        authorUid: Int?,
        forceRefresh: Boolean
    ): ForumThreadDetail =
        observeThreadDetail(
            tid,
            page,
            floorOrder,
            authorUid = authorUid,
            forceRefresh = forceRefresh,
            revalidate = false
        ).firstCachedOrFresh()

    override suspend fun loadFloorComments(
        tid: Int,
        pid: Int,
        page: Int,
        forceRefresh: Boolean
    ): ForumCommentPageResult {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = "$baseUrl/forum/forum.php?mod=misc&action=commentmore&tid=$tid&pid=$pid&page=$page&inajax=1&ajaxtarget=comment_$pid"
        val referer = "$baseUrl/forum/forum.php?mod=viewthread&tid=$tid"
        forumLogD("[Forum] loadFloorComments: url=$url")
        return cacheStore.observeCached(
            key = forumFloorCommentsCacheKey(baseUrl, tid, pid, page),
            ttlMillis = ForumCacheTtl.THREAD_DETAIL_NEXT_PAGE_MILLIS,
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = false
        ) {
            val doc = fetchForumAjaxDocument(url, referer)
            parseForumFloorComments(doc, baseUrl, pid)
        }.firstCachedOrFresh()
    }

    private fun forumCachePrefix(baseUrl: String): String = "forum:$baseUrl"

    private fun forumBoardsCacheKey(baseUrl: String): String = "${forumCachePrefix(baseUrl)}:boards"

    private fun forumThreadsCacheKey(
        baseUrl: String,
        fid: Int,
        page: Int,
        typeId: Int?
    ): String = "${forumCachePrefix(baseUrl)}:threads:$fid:$page:${typeId ?: "all"}"

    private fun forumDetailCacheKey(
        baseUrl: String,
        tid: Int,
        page: Int,
        floorOrder: ForumFloorOrder,
        authorUid: Int?
    ): String {
        val base = "${forumCachePrefix(baseUrl)}:detail:v3:$tid:$page:${floorOrder.name.lowercase()}"
        return if (authorUid != null && authorUid > 0) "$base:author$authorUid" else base
    }

    private fun forumFloorCommentsCacheKey(
        baseUrl: String,
        tid: Int,
        pid: Int,
        page: Int
    ): String = "${forumCachePrefix(baseUrl)}:floor-comments:v2:$tid:$pid:$page"

    private fun threadListTtl(page: Int): Long =
        if (page == 1) ForumCacheTtl.THREAD_LIST_FIRST_PAGE_MILLIS
        else ForumCacheTtl.THREAD_LIST_NEXT_PAGE_MILLIS

    private fun threadDetailTtl(page: Int): Long =
        if (page == 1) ForumCacheTtl.THREAD_DETAIL_FIRST_PAGE_MILLIS
        else ForumCacheTtl.THREAD_DETAIL_NEXT_PAGE_MILLIS

}
