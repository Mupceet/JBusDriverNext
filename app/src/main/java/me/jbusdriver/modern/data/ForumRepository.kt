package me.jbusdriver.modern.data

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.lruCached
import me.jbusdriver.modern.core.cache.persistentCached
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

interface ForumRepository {
    suspend fun loadForumBoards(forceRefresh: Boolean = false): ForumHomeData
    suspend fun loadThreads(fid: Int, page: Int, typeId: Int? = null, forceRefresh: Boolean = false): ForumThreadPageResult
    suspend fun loadThreadDetail(tid: Int, page: Int = 1, forceRefresh: Boolean = false): ForumThreadDetail
    fun destroySession()
}

@Singleton
class DefaultForumRepository @Inject constructor(
    private val sessionClient: ForumSessionClient,
    private val sessionManager: ForumSessionManager,
    private val cacheStore: CacheStore,
    private val siteConfig: SiteConfig
) : ForumRepository {

    private val cookiesPersistedForForum = AtomicBoolean(false)

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        val doc = sessionClient.fetchDocument(url)
        KLog.d("[Forum] fetched: title=${doc.title()}, length=${doc.html().length}", TAG)
        // Persist cookies after first successful forum page fetch
        // to capture Discuz! session cookies (4fJN_2132_*)
        if (cookiesPersistedForForum.compareAndSet(false, true)) {
            try {
                sessionManager.persistCookies()
            } catch (e: Exception) {
                KLog.e("[Forum] Failed to persist cookies: ${e.message}", TAG)
                cookiesPersistedForForum.set(false)
            }
        }
        return doc
    }

    override fun destroySession() {
        sessionClient.destroy()
        cookiesPersistedForForum.set(false)
    }

    override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData {
        val url = "${siteConfig.baseUrl}/forum/forum.php"
        KLog.d("[Forum] loadForumBoards: url=$url", TAG)
        return cacheStore.lruCached("forum_boards", forceRefresh) {
            val doc = fetchForumDocument(url)
            val result = parseForumHomeData(doc, siteConfig.baseUrl)
            KLog.d("[Forum] parseForumHomeData: ${result.banners.size} banners, ${result.boardGroups.sumOf { it.boards.size }} boards", TAG)
            result
        }
    }

    override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult {
        val cacheKey = "forum_threads_${fid}_${page}_${typeId ?: "all"}"
        val baseUrl = "${siteConfig.baseUrl}/forum/forum.php?mod=forumdisplay&fid=$fid&page=$page"
        val url = if (typeId != null) "$baseUrl&filter=typeid&typeid=$typeId" else baseUrl
        KLog.d("[Forum] loadThreads: url=$url", TAG)
        return cacheStore.lruCached(cacheKey, forceRefresh) {
            val doc = fetchForumDocument(url)
            parseForumThreads(doc, siteConfig.baseUrl)
        }
    }

    override suspend fun loadThreadDetail(tid: Int, page: Int, forceRefresh: Boolean): ForumThreadDetail {
        val cacheKey = "forum_detail_v2_${tid}_$page"
        val url = "${siteConfig.baseUrl}/forum/forum.php?mod=viewthread&tid=$tid&page=$page"
        KLog.d("[Forum] loadThreadDetail: url=$url", TAG)
        return cacheStore.persistentCached(cacheKey, forceRefresh) {
            val doc = fetchForumDocument(url)
            parseForumThreadDetail(doc, siteConfig.baseUrl)
        }
    }
}
