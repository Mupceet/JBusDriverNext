package me.jbusdriver.modern.data

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.parser.parseForumBoards
import me.jbusdriver.modern.data.parser.parseForumThreadDetail
import me.jbusdriver.modern.data.parser.parseForumThreads
import me.jbusdriver.modern.domain.model.ForumBoard
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumRepo"

interface ForumRepository {
    suspend fun loadForumBoards(forceRefresh: Boolean = false): List<ForumBoard>
    suspend fun loadThreads(fid: Int, page: Int, typeId: Int? = null, forceRefresh: Boolean = false): ForumThreadPageResult
    suspend fun loadThreadDetail(tid: Int, page: Int = 1, forceRefresh: Boolean = false): ForumThreadDetail
}

@Singleton
class DefaultForumRepository @Inject constructor() : ForumRepository {

    private val sessionInitialized = AtomicBoolean(false)

    /**
     * 论坛需要 Discuz! session cookies 才能访问。
     * 流程：先 POST 表单完成年龄验证 → 服务器通过 Set-Cookie 设置 Discuz! session →
     * CookieJar 保存 → 后续请求由拦截器自动合并。
     */
    private suspend fun ensureForumSession() {
        if (sessionInitialized.get()) return
        try {
            val verifyUrl = "${NetClient.defaultFastUrl}/doc/driver-verify"
            KLog.d("[Forum] Initializing forum session: POST Submit=確認", TAG)

            // POST the age verification form — server returns Set-Cookie with Discuz! session
            NetClient.postForm(verifyUrl, mapOf("Submit" to "確認"))
            KLog.d("[Forum] POST verification done", TAG)

            sessionInitialized.set(true)
        } catch (e: Exception) {
            KLog.e("[Forum] Session init failed: ${e.message}", e, TAG)
        }
    }

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        ensureForumSession()
        val doc = NetClient.fetchDocument(url)
        KLog.d("[Forum] fetched: title=${doc.title()}, length=${doc.html().length}", TAG)

        // If still on verification page, log and return as-is
        if (doc.title().lowercase().contains("age verification")) {
            KLog.w("[Forum] Still on verification page — POST may not have set the right cookies", TAG)
        }

        return doc
    }

    override suspend fun loadForumBoards(forceRefresh: Boolean): List<ForumBoard> {
        val url = "${NetClient.defaultFastUrl}/forum/forum.php"
        KLog.d("[Forum] loadForumBoards: url=$url", TAG)
        return CacheLoader.lruCached("forum_boards", forceRefresh) {
            val doc = fetchForumDocument(url)
            val result = parseForumBoards(doc)
            KLog.d("[Forum] parseForumBoards: ${result.size} boards", TAG)
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
        return CacheLoader.persistentCached(cacheKey) {
            val doc = fetchForumDocument(url)
            parseForumThreadDetail(doc)
        }
    }
}
