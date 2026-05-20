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

    private val ageVerified = AtomicBoolean(false)

    /**
     * 主站和论坛都会被重定向到 /doc/driver-verify 年龄验证页面。
     * 验证页面有表单 POST Submit=確認，但 cookie 实际由 JS 设置。
     * 尝试 POST 表单 + 直接注入 over18 cookie 双保险。
     */
    private suspend fun passAgeVerification() {
        if (ageVerified.get()) return
        try {
            val verifyUrl = "${NetClient.defaultFastUrl}/doc/driver-verify"
            KLog.d("[Forum] Attempting age verification: POST Submit=確認 to $verifyUrl", TAG)

            // POST the actual form: Submit=確認
            try {
                NetClient.postForm(verifyUrl, mapOf("Submit" to "確認"))
                KLog.d("[Forum] POST Submit=確認 done", TAG)
            } catch (e: Exception) {
                KLog.w("[Forum] POST failed: ${e.message}", TAG)
            }

            // Also directly set the over18 cookie (JS would do this)
            NetClient.setCookie("over18", "on")
            KLog.d("[Forum] Cookie over18=on set", TAG)

            ageVerified.set(true)
        } catch (e: Exception) {
            KLog.e("[Forum] Age verification failed: ${e.message}", e, TAG)
        }
    }

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        var doc = NetClient.fetchDocument(url)
        KLog.d("[Forum] document: title=${doc.title()}, location=${doc.location()}, length=${doc.html().length}", TAG)

        // Check if we got redirected to verification page
        if (doc.title().lowercase().contains("age verification") || doc.location().contains("driver-verify")) {
            KLog.w("[Forum] Got verification page, attempting to pass...", TAG)
            passAgeVerification()
            // Retry
            doc = NetClient.fetchDocument(url)
            KLog.d("[Forum] Retry after verification: title=${doc.title()}, location=${doc.location()}", TAG)
        }

        return doc
    }

    override suspend fun loadForumBoards(forceRefresh: Boolean): List<ForumBoard> {
        val url = "${NetClient.defaultFastUrl}/forum/forum.php"
        KLog.d("[Forum] loadForumBoards: url=$url, forceRefresh=$forceRefresh", TAG)
        return CacheLoader.lruCached("forum_boards", forceRefresh) {
            val doc = fetchForumDocument(url)
            val result = parseForumBoards(doc)
            KLog.d("[Forum] parseForumBoards result: ${result.size} boards", TAG)
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
