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

    /**
     * Fetches forum document, handling age verification redirect.
     * If the page redirects to /doc/driver-verify, submits the verification
     * form and retries the original request.
     */
    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        var doc = NetClient.fetchDocument(url)
        val title = doc.title().lowercase()

        if (title.contains("age verification") || doc.location().contains("driver-verify")) {
            KLog.w("[Forum] Age verification page detected for $url", TAG)
            KLog.w("[Forum] Verification page title: ${doc.title()}", TAG)
            KLog.w("[Forum] Verification page location: ${doc.location()}", TAG)

            // Log form details for diagnosis
            val forms = doc.select("form")
            KLog.d("[Forum] Verification page forms: ${forms.size}", TAG)
            for ((index, form) in forms.withIndex()) {
                KLog.d("[Forum] Form[$index]: action=${form.attr("action")}, method=${form.attr("method")}", TAG)
                val inputs = form.select("input")
                for (input in inputs) {
                    KLog.d("[Forum]   input: name=${input.attr("name")}, type=${input.attr("type")}, value=${input.attr("value")}", TAG)
                }
                val buttons = form.select("button, input[type=submit], a.btn")
                for (btn in buttons) {
                    KLog.d("[Forum]   button: tag=${btn.tagName()}, text=${btn.text().trim()}, href=${btn.attr("href")}", TAG)
                }
            }

            // Log the first 3000 chars of body for analysis
            val bodyText = doc.body()?.html()?.take(3000) ?: ""
            KLog.d("[Forum] Verification page body preview:\n$bodyText", TAG)

            // Try to submit verification: look for a link/button that confirms age
            val confirmLink = doc.select("a[href*=driver-verify], a.btn, button").firstOrNull()
            val formAction = forms.firstOrNull()?.attr("action")
            KLog.d("[Forum] confirmLink: ${confirmLink?.tagName()} href=${confirmLink?.attr("href")}", TAG)
            KLog.d("[Forum] formAction: $formAction", TAG)

            // Try submitting by hitting the driver-verify URL with over18=yes
            val verifyUrl = "${NetClient.defaultFastUrl}/doc/driver-verify"
            try {
                KLog.d("[Forum] Attempting age verification POST: $verifyUrl", TAG)
                NetClient.postForm(verifyUrl, mapOf("over18" to "yes", "referer" to url))
                // Retry original request
                doc = NetClient.fetchDocument(url)
                KLog.d("[Forum] After verification retry: title=${doc.title()}", TAG)
            } catch (e: Exception) {
                KLog.e("[Forum] Verification submit failed: ${e.message}", e, TAG)
            }
        }

        return doc
    }

    override suspend fun loadForumBoards(forceRefresh: Boolean): List<ForumBoard> {
        val url = "${NetClient.defaultFastUrl}/forum/forum.php"
        KLog.d("[Forum] loadForumBoards: url=$url, forceRefresh=$forceRefresh", TAG)
        return CacheLoader.lruCached("forum_boards", forceRefresh) {
            val doc = fetchForumDocument(url)
            KLog.d("[Forum] document fetched: title=${doc.title()}, htmlLength=${doc.html().length}", TAG)
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
            KLog.d("[Forum] threads document: title=${doc.title()}, htmlLength=${doc.html().length}", TAG)
            parseForumThreads(doc)
        }
    }

    override suspend fun loadThreadDetail(tid: Int, page: Int, forceRefresh: Boolean): ForumThreadDetail {
        val cacheKey = "forum_detail_${tid}_$page"
        val url = "${NetClient.defaultFastUrl}/forum/forum.php?mod=viewthread&tid=$tid&page=$page"
        KLog.d("[Forum] loadThreadDetail: url=$url", TAG)
        return CacheLoader.persistentCached(cacheKey) {
            val doc = fetchForumDocument(url)
            KLog.d("[Forum] detail document: title=${doc.title()}, htmlLength=${doc.html().length}", TAG)
            parseForumThreadDetail(doc)
        }
    }
}
