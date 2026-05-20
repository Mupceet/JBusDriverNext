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
     * 需要主动完成验证：GET 请求 driver-verify 页面中的确认链接来设置 cookie。
     */
    private suspend fun passAgeVerification() {
        if (ageVerified.get()) return
        try {
            // Step 1: 获取验证页面
            val verifyUrl = "${NetClient.defaultFastUrl}/doc/driver-verify"
            KLog.d("[Forum] Fetching age verification page: $verifyUrl", TAG)
            val doc = NetClient.fetchDocument(verifyUrl)

            KLog.d("[Forum] Verification page title: ${doc.title()}", TAG)
            KLog.d("[Forum] Verification page location: ${doc.location()}", TAG)

            // Log all links on the page
            val links = doc.select("a[href]")
            KLog.d("[Forum] Verification page links: ${links.size}", TAG)
            for (link in links) {
                KLog.d("[Forum]   <a> text='${link.text().trim()}' href='${link.attr("href")}'", TAG)
            }

            // Log all forms
            val forms = doc.select("form")
            KLog.d("[Forum] Verification page forms: ${forms.size}", TAG)
            for ((i, form) in forms.withIndex()) {
                KLog.d("[Forum]   form[$i] action='${form.attr("action")}' method='${form.attr("method")}'", TAG)
                form.select("input, button").forEach { el ->
                    KLog.d("[Forum]     <${el.tagName()}> name='${el.attr("name")}' type='${el.attr("type")}' value='${el.attr("value")}'", TAG)
                }
            }

            // Log all script urls
            doc.select("script[src]").forEach {
                KLog.d("[Forum]   script src='${it.attr("src")}'", TAG)
            }

            // Log body preview for manual analysis
            val bodyPreview = doc.body()?.html()?.take(5000) ?: ""
            KLog.d("[Forum] Verification body:\n$bodyPreview", TAG)

            // Step 2: Try common verification approaches
            // Approach A: Look for a link with over18 or confirm text
            val confirmLink = links.firstOrNull {
                val href = it.attr("href").lowercase()
                val text = it.text().trim().lowercase()
                href.contains("over18") || href.contains("confirm") || text.contains("enter") || text.contains("滿") || text.contains("18")
            }
            if (confirmLink != null) {
                val confirmHref = confirmLink.attr("href")
                val confirmUrl = if (confirmHref.startsWith("http")) confirmHref
                    else "${NetClient.defaultFastUrl}$confirmHref"
                KLog.d("[Forum] Found confirm link, GET: $confirmUrl", TAG)
                NetClient.fetchHtml(confirmUrl)
            }

            // Approach B: Try POST with over18=yes
            try {
                KLog.d("[Forum] Trying POST over18 to: $verifyUrl", TAG)
                NetClient.postForm(verifyUrl, mapOf("over18" to "yes"))
            } catch (_: Exception) {}

            ageVerified.set(true)
            KLog.d("[Forum] Age verification completed", TAG)
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
