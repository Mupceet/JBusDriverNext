package me.jbusdriver.modern.data

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.JBusManager
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.parser.parseForumBoards
import me.jbusdriver.modern.data.parser.parseForumThreadDetail
import me.jbusdriver.modern.data.parser.parseForumThreads
import me.jbusdriver.modern.domain.model.ForumBoardGroup
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumRepo"

interface ForumRepository {
    suspend fun loadForumBoards(forceRefresh: Boolean = false): List<ForumBoardGroup>
    suspend fun loadThreads(fid: Int, page: Int, typeId: Int? = null, forceRefresh: Boolean = false): ForumThreadPageResult
    suspend fun loadThreadDetail(tid: Int, page: Int = 1, forceRefresh: Boolean = false): ForumThreadDetail
    fun destroySession()
}

@Singleton
class DefaultForumRepository @Inject constructor(
    private val sessionManager: ForumSessionManager
) : ForumRepository {

    private suspend fun ensureForumSession() {
        if (sessionManager.isInitialized()) return
        val activity = JBusManager.manager
            .mapNotNull { it.get() }
            .firstOrNull { !it.isFinishing && !it.isDestroyed }
            ?: throw IllegalStateException("No valid activity available for forum session init")
        sessionManager.ensureSession(activity)
    }

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        ensureForumSession()
        val doc = sessionManager.fetchDocument(url)
        KLog.d("[Forum] fetched: title=${doc.title()}, length=${doc.html().length}", TAG)
        return doc
    }

    override fun destroySession() {
        sessionManager.destroy()
    }

    override suspend fun loadForumBoards(forceRefresh: Boolean): List<ForumBoardGroup> {
        val url = "${NetClient.defaultFastUrl}/forum/forum.php"
        KLog.d("[Forum] loadForumBoards: url=$url", TAG)
        return CacheLoader.lruCached("forum_boards", forceRefresh) {
            val doc = fetchForumDocument(url)
            val result = parseForumBoards(doc)
            KLog.d("[Forum] parseForumBoards: ${result.sumOf { it.boards.size }} boards in ${result.size} groups", TAG)
            result
        }
    }

    override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult {
        val cacheKey = "forum_threads_${fid}_${page}_${typeId ?: "all"}"
        val baseUrl = "${NetClient.defaultFastUrl}/forum/forum.php?mod=forumdisplay&fid=$fid&page=$page"
        val url = if (typeId != null) "$baseUrl&filter=typeid&typeid=$typeId" else baseUrl
        KLog.d("[Forum] loadThreads: url=$url", TAG)
        return CacheLoader.lruCached(cacheKey, forceRefresh) {
            val doc = fetchForumDocument(url)
            parseForumThreads(doc)
        }
    }

    override suspend fun loadThreadDetail(tid: Int, page: Int, forceRefresh: Boolean): ForumThreadDetail {
        val cacheKey = "forum_detail_${tid}_$page"
        val url = "${NetClient.defaultFastUrl}/forum/forum.php?mod=viewthread&tid=$tid&page=$page"
        KLog.d("[Forum] loadThreadDetail: url=$url", TAG)
        return CacheLoader.persistentCached(cacheKey, forceRefresh) {
            val doc = fetchForumDocument(url)
            parseForumThreadDetail(doc)
        }
    }
}
