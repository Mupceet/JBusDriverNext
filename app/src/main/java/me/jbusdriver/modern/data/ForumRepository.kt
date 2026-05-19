package me.jbusdriver.modern.data

import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.parser.parseForumBoards
import me.jbusdriver.modern.data.parser.parseForumThreadDetail
import me.jbusdriver.modern.data.parser.parseForumThreads
import me.jbusdriver.modern.domain.model.ForumBoard
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import javax.inject.Inject
import javax.inject.Singleton

interface ForumRepository {
    suspend fun loadForumBoards(forceRefresh: Boolean = false): List<ForumBoard>
    suspend fun loadThreads(fid: Int, page: Int, typeId: Int? = null, forceRefresh: Boolean = false): ForumThreadPageResult
    suspend fun loadThreadDetail(tid: Int, page: Int = 1, forceRefresh: Boolean = false): ForumThreadDetail
}

@Singleton
class DefaultForumRepository @Inject constructor() : ForumRepository {

    override suspend fun loadForumBoards(forceRefresh: Boolean): List<ForumBoard> {
        val url = "${NetClient.defaultFastUrl}/forum/forum.php"
        return CacheLoader.lruCached("forum_boards", forceRefresh) {
            val doc = NetClient.fetchDocument(url)
            parseForumBoards(doc)
        }
    }

    override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult {
        val cacheKey = "forum_threads_${fid}_${page}_${typeId ?: "all"}"
        return CacheLoader.lruCached(cacheKey, forceRefresh) {
            val baseUrl = "${NetClient.defaultFastUrl}/forum/forum.php?mod=forumdisplay&fid=$fid&page=$page"
            val url = if (typeId != null) "$baseUrl&filter=typeid&typeid=$typeId" else baseUrl
            val doc = NetClient.fetchDocument(url)
            parseForumThreads(doc)
        }
    }

    override suspend fun loadThreadDetail(tid: Int, page: Int, forceRefresh: Boolean): ForumThreadDetail {
        val cacheKey = "forum_detail_${tid}_$page"
        return CacheLoader.persistentCached(cacheKey) {
            val url = "${NetClient.defaultFastUrl}/forum/forum.php?mod=viewthread&tid=$tid&page=$page"
            val doc = NetClient.fetchDocument(url)
            parseForumThreadDetail(doc)
        }
    }
}
