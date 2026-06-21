# Forum Floor Comments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-floor forum comment previews and a paged bottom sheet for viewing all comments on a selected floor.

**Architecture:** Treat floor comments as domain data owned by the forum detail feature. Parse comments from each post's local `comment_<pid>` block, fetch later pages through a dedicated repository method, keep bottom-sheet pagination in `ForumThreadDetailViewModel`, and render previews/sheet content in focused Compose helpers.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Hilt ViewModel, Coroutines, Jsoup, Gson-backed `CacheStore`, JUnit4, kotlinx-coroutines-test.

---

## File Structure

- Modify `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt`
  - Add `pid`, first-post `commentPageInfo`, reply `comments`, reply `commentPageInfo`, and `ForumCommentPageResult`.
  - Add defaults for new fields to preserve disk-cache Gson compatibility.
- Modify `app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt`
  - Add shared floor-comment parsing helpers.
  - Parse first-post/reply `pid`, comments, and local comment pagination.
  - Add `parseForumFloorComments(doc, baseUrl, pid)`.
- Modify `app/src/main/java/me/jbusdriver/modern/data/repository/ForumRepository.kt`
  - Add `loadFloorComments()`.
  - Build `commentmore` URL through the existing forum session client.
  - Cache by site, `tid`, `pid`, and page.
- Modify `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailViewModel.kt`
  - Add sheet state and actions for opening, dismissing, loading, and retrying floor comments.
  - Keep sheet request identity separate from thread-detail request identity.
- Modify `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`
  - Wire the sheet state into the screen.
  - Trigger sheet comment pagination from a sheet-local `LazyListState`.
- Modify `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailSections.kt`
  - Add `PostCommentsPreview`, reusable comment row rendering, and `FloorCommentsBottomSheet`.
  - Update first-post and reply cards to show previews.
- Modify `app/src/main/res/values/strings.xml`
  - Add `view_more_comments`, `floor_comments`, `retry_load_comments`.
- Modify `app/src/main/res/values-en/strings.xml`
  - Add English equivalents.
- Create `app/src/test/resources/forum/floor-comments.html`
  - Minimal fixture with first-post comments, reply comments, and both comment/thread pagination.
- Modify `app/src/test/java/me/jbusdriver/modern/data/parser/ForumThreadParserTest.kt`
  - Add parser tests for floor comments and commentmore fragments.
- Modify `app/src/test/java/me/jbusdriver/modern/data/ForumRepositoryCacheFlowTest.kt`
  - Add repository URL/cache behavior tests for `loadFloorComments()`.
- Modify `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailViewModelTest.kt`
  - Add sheet state tests.

---

### Task 1: Domain Model and Parser Tests

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt`
- Create: `app/src/test/resources/forum/floor-comments.html`
- Modify: `app/src/test/java/me/jbusdriver/modern/data/parser/ForumThreadParserTest.kt`

- [ ] **Step 1: Add the floor-comments fixture**

Create `app/src/test/resources/forum/floor-comments.html`:

```html
<html>
<body>
  <h1 class="ts"><span id="thread_subject">Thread With Floor Comments</span></h1>
  <div class="nthread_firstpostbox" id="post_4773811">
    <div class="nthread_firstpost">
      <div class="post_avatar"><img src="/avatars/op.jpg"></div>
    </div>
    <div class="authi">
      <a class="au" href="home.php?mod=space&amp;uid=316255">Original Poster</a>
      <span class="mr10">發表於 2026-6-8 02:15</span>
    </div>
    <table><tbody><tr><td class="t_f" id="postmessage_4773811">First post body</td></tr></tbody></table>
    <div id="comment_4773811" class="cm">
      <h3 class="psth cm">點評</h3>
      <div class="pstl">
        <div class="psta"><img src="/avatars/a.jpg"></div>
        <div class="psti"><a href="home.php?mod=space&amp;uid=1" class="xi2 xw1">Alice</a>&nbsp;first comment&nbsp;<span class="xg1">發表於 2026-6-9 08:59</span></div>
      </div>
      <div class="pstl">
        <div class="psta"><img src="/avatars/b.jpg"></div>
        <div class="psti"><a href="home.php?mod=space&amp;uid=2" class="xi2 xw1">Bob</a>&nbsp;second comment&nbsp;<span class="xg1"><span title="2026-6-9 09:00">昨天 09:00</span></span></div>
      </div>
      <div class="pstl">
        <div class="psta"><img src="/avatars/c.jpg"></div>
        <div class="psti"><a href="home.php?mod=space&amp;uid=3" class="xi2 xw1">Carol</a>&nbsp;third comment&nbsp;<span class="xg1">發表於 2026-6-9 09:01</span></div>
      </div>
      <div class="pstl">
        <div class="psta"><img src="/avatars/d.jpg"></div>
        <div class="psti"><a href="home.php?mod=space&amp;uid=4" class="xi2 xw1">Dan</a>&nbsp;fourth comment&nbsp;<span class="xg1">發表於 2026-6-9 09:02</span></div>
      </div>
      <div class="pgs mbm cl"><div class="pg">
        <strong>1</strong>
        <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=2" class="nxt" ajaxtarget="comment_4773811">下一頁</a>
      </div></div>
    </div>
    <div id="post_rate_div_4773811"></div>
  </div>

  <div class="nthread_postbox" id="post_4773820">
    <div class="pi">
      <a id="postnum4773820"><em>2</em><sup>#</sup></a>
      <div class="authi">
        <a class="xw1" href="home.php?mod=space&amp;uid=620784">Reply Author</a>
        <em id="authorposton4773820">發表於 2026-6-10 18:25</em>
      </div>
    </div>
    <div class="favatar"><div class="avatar"><img src="/avatars/reply.jpg"></div></div>
    <table><tbody><tr><td class="t_f" id="postmessage_4773820">Reply body</td></tr></tbody></table>
    <div id="comment_4773820" class="cm">
      <h3 class="psth xs1">點評</h3>
      <div class="pstl xs1 cl">
        <div class="psta vm">
          <a href="home.php?mod=space&amp;uid=5"><img src="/avatars/e.jpg"></a>
          <a href="home.php?mod=space&amp;uid=5" class="xi2 xw1">Eve</a>
        </div>
        <div class="psti">reply floor comment&nbsp;<span class="xg1">發表於 2026-6-10 20:00</span></div>
      </div>
    </div>
    <div id="post_rate_div_4773820"></div>
  </div>

  <div class="pgs mtm mbm cl"><div class="pg">
    <strong>1</strong>
    <a href="forum.php?mod=viewthread&amp;tid=172059&amp;page=2" class="nxt">下一頁</a>
  </div></div>
</body>
</html>
```

- [ ] **Step 2: Add failing parser tests**

Append these imports to `ForumThreadParserTest.kt` if missing:

```kotlin
import me.jbusdriver.modern.domain.model.PageInfo
import org.jsoup.nodes.Document
```

Append these tests inside `class ForumThreadParserTest`:

```kotlin
@Test
fun `thread detail parses first post floor comments and comment pagination`() {
    val detail = parseForumThreadDetail(
        fixture(
            "floor-comments.html",
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=172059"
        ),
        "https://www.javbus.com"
    )

    assertEquals(4773811, detail.pid)
    assertEquals(4, detail.comments.size)
    assertEquals("Alice", detail.comments[0].author)
    assertEquals("https://www.javbus.com/avatars/a.jpg", detail.comments[0].authorAvatar)
    assertEquals("first comment", detail.comments[0].content)
    assertEquals("發表於 2026-6-9 08:59", detail.comments[0].time)
    assertEquals("2026-6-9 09:00", detail.comments[1].time)
    assertEquals(PageInfo(activePage = 1, nextPage = 2), detail.commentPageInfo)
}

@Test
fun `thread detail parses reply floor comments independently`() {
    val detail = parseForumThreadDetail(
        fixture(
            "floor-comments.html",
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=172059"
        ),
        "https://www.javbus.com"
    )

    val reply = detail.replies.single()
    assertEquals(4773820, reply.pid)
    assertEquals(1, reply.comments.size)
    assertEquals("Eve", reply.comments.single().author)
    assertEquals("reply floor comment", reply.comments.single().content)
    assertEquals(PageInfo(activePage = 1, nextPage = 1), reply.commentPageInfo)
}

@Test
fun `commentmore fragment parser returns comments and local page info`() {
    val doc = commentMoreDocument(
        """
        <div id="comment_4773811" class="cm">
          <div class="pstl">
            <div class="psta"><img src="/avatars/f.jpg"></div>
            <div class="psti"><a href="home.php?mod=space&amp;uid=6" class="xi2 xw1">Frank</a>&nbsp;page two comment&nbsp;<span class="xg1">發表於 2026-6-10 10:00</span></div>
          </div>
          <div class="pgs mbm cl"><div class="pg">
            <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=1" class="prev">上一頁</a>
            <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=1">1</a>
            <strong>2</strong>
          </div></div>
        </div>
        """.trimIndent()
    )

    val result = parseForumFloorComments(doc, "https://www.javbus.com", pid = 4773811)

    assertEquals(4773811, result.pid)
    assertEquals("Frank", result.comments.single().author)
    assertEquals("page two comment", result.comments.single().content)
    assertEquals(PageInfo(activePage = 2, nextPage = 2), result.pageInfo)
}
```

Append this helper near the existing `fixture()` helper:

```kotlin
private fun commentMoreDocument(html: String): Document =
    Jsoup.parse(
        html,
        "https://www.javbus.com/forum/forum.php?mod=misc&action=commentmore&tid=172059&pid=4773811&page=2"
    )
```

- [ ] **Step 3: Run parser tests and verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.parser.ForumThreadParserTest"
```

Expected: FAIL because `ForumThreadDetail.pid`, `ForumThreadDetail.commentPageInfo`, `ForumReply.pid`, `ForumReply.comments`, `ForumReply.commentPageInfo`, and `parseForumFloorComments()` do not exist yet.

- [ ] **Step 4: Add domain model fields**

In `ForumModels.kt`, update `ForumThreadDetail`, add `ForumCommentPageResult`, and update `ForumReply`:

```kotlin
@Immutable
data class ForumThreadDetail(
    val tid: Int,
    val typeId: Int,
    val typeName: String,
    val typeColor: String,
    val title: String,
    val viewCount: Int,
    val replyCount: Int,
    val author: String,
    val authorUid: Int,
    val authorAvatar: String,
    val postTime: String,
    val contentBlocks: List<ContentBlock>,
    val comments: List<Comment>,
    val replies: List<ForumReply>,
    val pageInfo: PageInfo,
    val pid: Int = 0,
    val commentPageInfo: PageInfo = PageInfo()
)

@Immutable
data class ForumCommentPageResult(
    val pid: Int,
    val comments: List<Comment>,
    val pageInfo: PageInfo
)

@Immutable
data class ForumReply(
    val floor: Int,
    val author: String,
    val authorUid: Int,
    val authorAvatar: String,
    val authorGroup: String,
    val contentBlocks: List<ContentBlock>,
    val postTime: String,
    val isPinned: Boolean = false,
    val pid: Int = 0,
    val comments: List<Comment> = emptyList(),
    val commentPageInfo: PageInfo = PageInfo()
)
```

- [ ] **Step 5: Implement minimal parser support**

In `ForumThreadParser.kt`, add import:

```kotlin
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
```

In `parseForumThreadDetail`, compute first-post pid and comments before creating `ForumThreadDetail`:

```kotlin
val firstPostPid = parsePostPid(firstPost)
val firstPostCommentBlock = firstPostPid.takeIf { it != 0 }
    ?.let { firstPost?.selectFirst("div#comment_$it.cm") }
val firstPostCommentPage = parseForumCommentPageInfo(firstPostCommentBlock)
val comments = parseForumComments(firstPostCommentBlock, baseUrl)
```

Replace the existing `val comments = firstPost?.select("div.cm div.pstl")?...` block with the variables above.

Inside the reply map, compute pid and comments before constructing `ForumReply`:

```kotlin
val replyPid = parsePostPid(postBox)
val replyCommentBlock = replyPid.takeIf { it != 0 }
    ?.let { postBox.selectFirst("div#comment_$it.cm") }
val replyComments = parseForumComments(replyCommentBlock, baseUrl)
val replyCommentPageInfo = parseForumCommentPageInfo(replyCommentBlock)
```

Pass these values into `ForumReply`:

```kotlin
pid = replyPid,
comments = replyComments,
commentPageInfo = replyCommentPageInfo
```

Pass these values into `ForumThreadDetail`:

```kotlin
pid = firstPostPid,
commentPageInfo = firstPostCommentPage
```

Add these helpers near the bottom of `ForumThreadParser.kt`:

```kotlin
fun parseForumFloorComments(
    doc: Document,
    baseUrl: String,
    pid: Int
): ForumCommentPageResult {
    val root = doc.selectFirst("div#comment_$pid.cm") ?: doc.body()
    return ForumCommentPageResult(
        pid = pid,
        comments = parseForumComments(root, baseUrl),
        pageInfo = parseForumCommentPageInfo(root)
    )
}

private fun parsePostPid(postBox: Element?): Int {
    if (postBox == null) return 0
    return listOf(
        postBox.id(),
        postBox.selectFirst("td[id^=postmessage_]")?.id().orEmpty(),
        postBox.selectFirst("div[id^=comment_]")?.id().orEmpty(),
        postBox.selectFirst("div[id^=post_rate_div_]")?.id().orEmpty()
    ).firstNotNullOfOrNull { id ->
        Regex("(?:post_|postmessage_|comment_|post_rate_div_)(\\d+)").find(id)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    } ?: 0
}

private fun parseForumComments(root: Element?, baseUrl: String): List<Comment> =
    root?.select("div.pstl")?.mapNotNull { row ->
        val body = row.selectFirst(".psti") ?: return@mapNotNull null
        val authorElement = row.selectFirst(".psta a.xw1, .psta a[href*=uid], .psti a.xi2")
        val author = authorElement?.text()?.trim().orEmpty()
        val avatar = row.selectFirst(".psta img[src]")?.attr("src").orEmpty().wrapForumImage(baseUrl)
        val timeElement = body.selectFirst(".xg1 span[title], .xg1 span, .xg1")
        val time = timeElement?.attr("title")?.ifBlank { timeElement.text() }?.trim().orEmpty()
        val content = body.clone().also { clone ->
            clone.select("a.xi2, a.xw1, .xg1").remove()
        }.text().trim()

        if (author.isEmpty() && content.isEmpty()) return@mapNotNull null
        Comment(
            author = author,
            authorAvatar = avatar,
            content = content,
            time = time
        )
    } ?: emptyList()

private fun parseForumCommentPageInfo(root: Element?): PageInfo {
    if (root == null) return PageInfo(activePage = 1, nextPage = 1)
    val pager = root.select(".pg").lastOrNull() ?: return PageInfo(activePage = 1, nextPage = 1)
    val activePage = pager.selectFirst("strong")?.text()?.toIntOrNull() ?: 1
    val nextPage = pager.select("a.nxt[href*=commentmore]")
        .firstOrNull()
        ?.attr("href")
        ?.let { PAGE_PARAM.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?: activePage
    return PageInfo(activePage = activePage, nextPage = nextPage)
}
```

- [ ] **Step 6: Run parser tests and verify they pass**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.parser.ForumThreadParserTest"
```

Expected: PASS.

- [ ] **Step 7: Commit parser and model work**

Run:

```bash
git status --short
git add app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt app/src/test/java/me/jbusdriver/modern/data/parser/ForumThreadParserTest.kt app/src/test/resources/forum/floor-comments.html
git status --short
git commit -m "feat: parse forum floor comments"
```

Expected: only the files listed above are staged before commit.

---

### Task 2: Repository Loading and Caching

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/repository/ForumRepository.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/data/ForumRepositoryCacheFlowTest.kt`

- [ ] **Step 1: Add failing repository tests**

In `ForumRepositoryCacheFlowTest.kt`, add imports if missing:

```kotlin
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
import me.jbusdriver.modern.domain.model.PageInfo
```

Append tests inside `class ForumRepositoryCacheFlowTest`:

```kotlin
@Test
fun `loadFloorComments builds commentmore url and returns parsed comments`() = runBlocking {
    val sessionClient = FakeSessionClient(commentMoreHtml(page = 2, nextPage = 3))
    val repository = repository(FakeCacheStore(), sessionClient)

    val result = repository.loadFloorComments(tid = 172059, pid = 4773811, page = 2)

    assertEquals(4773811, result.pid)
    assertEquals("Frank", result.comments.single().author)
    assertEquals(PageInfo(activePage = 2, nextPage = 3), result.pageInfo)
    assertEquals(1, sessionClient.urls.size)
    assertTrue(sessionClient.urls.single().contains("mod=misc&action=commentmore"))
    assertTrue(sessionClient.urls.single().contains("tid=172059"))
    assertTrue(sessionClient.urls.single().contains("pid=4773811"))
    assertTrue(sessionClient.urls.single().contains("page=2"))
}

@Test
fun `loadFloorComments caches by tid pid and page`() = runBlocking {
    val cacheStore = FakeCacheStore()
    val sessionClient = FakeSessionClient(commentMoreHtml(page = 2, nextPage = 2))
    val repository = repository(cacheStore, sessionClient)

    repository.loadFloorComments(tid = 172059, pid = 4773811, page = 2)
    repository.loadFloorComments(tid = 172059, pid = 4773811, page = 2)

    assertEquals(1, sessionClient.fetchCount)
    assertTrue(cacheStore.memory.keys.any { key ->
        key == "forum:https://example.test:floor-comments:v1:172059:4773811:2"
    })
}
```

Add helper near other HTML helpers:

```kotlin
private fun commentMoreHtml(page: Int, nextPage: Int): String {
    val nextLink = if (nextPage > page) {
        """<a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=$nextPage" class="nxt">下一頁</a>"""
    } else {
        ""
    }
    return """
        <html>
          <body>
            <div id="comment_4773811" class="cm">
              <div class="pstl">
                <div class="psta"><img src="/avatars/f.jpg"></div>
                <div class="psti"><a href="home.php?mod=space&amp;uid=6" class="xi2 xw1">Frank</a>&nbsp;page $page comment&nbsp;<span class="xg1">發表於 2026-6-10 10:00</span></div>
              </div>
              <div class="pgs mbm cl"><div class="pg">
                <strong>$page</strong>
                $nextLink
              </div></div>
            </div>
          </body>
        </html>
    """.trimIndent()
}
```

- [ ] **Step 2: Run repository tests and verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.ForumRepositoryCacheFlowTest"
```

Expected: FAIL because `ForumRepository.loadFloorComments()` does not exist.

- [ ] **Step 3: Add repository API and implementation**

In `ForumRepository.kt`, add import:

```kotlin
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
import me.jbusdriver.modern.data.parser.parseForumFloorComments
```

Add to `interface ForumRepository`:

```kotlin
suspend fun loadFloorComments(
    tid: Int,
    pid: Int,
    page: Int,
    forceRefresh: Boolean = false
): ForumCommentPageResult
```

Add to `DefaultForumRepository`:

```kotlin
override suspend fun loadFloorComments(
    tid: Int,
    pid: Int,
    page: Int,
    forceRefresh: Boolean
): ForumCommentPageResult {
    val url = "${siteConfig.baseUrl}/forum/forum.php?mod=misc&action=commentmore&tid=$tid&pid=$pid&page=$page"
    forumLogD("[Forum] loadFloorComments: url=$url")
    return cacheStore.observeCached(
        key = forumFloorCommentsCacheKey(tid, pid, page),
        ttlMillis = ForumCacheTtl.THREAD_DETAIL_NEXT_PAGE_MILLIS,
        disk = true,
        forceRefresh = forceRefresh,
        revalidate = false
    ) {
        val doc = fetchForumDocument(url)
        parseForumFloorComments(doc, siteConfig.baseUrl, pid)
    }.firstCachedOrFresh()
}
```

Add cache key helper:

```kotlin
private fun forumFloorCommentsCacheKey(tid: Int, pid: Int, page: Int): String =
    "${forumCachePrefix()}:floor-comments:v1:$tid:$pid:$page"
```

- [ ] **Step 4: Update test fakes for the new interface method**

In `ForumThreadDetailViewModelTest.kt`, update `FakeForumDetailRepository` with a temporary stub so compilation succeeds until Task 3 expands it:

```kotlin
override suspend fun loadFloorComments(
    tid: Int,
    pid: Int,
    page: Int,
    forceRefresh: Boolean
): ForumCommentPageResult = error("not used")
```

Add import:

```kotlin
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
```

- [ ] **Step 5: Run repository tests and verify they pass**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.ForumRepositoryCacheFlowTest"
```

Expected: PASS.

- [ ] **Step 6: Commit repository work**

Run:

```bash
git status --short
git add app/src/main/java/me/jbusdriver/modern/data/repository/ForumRepository.kt app/src/test/java/me/jbusdriver/modern/data/ForumRepositoryCacheFlowTest.kt app/src/test/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailViewModelTest.kt
git status --short
git commit -m "feat: load forum floor comments"
```

Expected: only repository and affected tests are staged.

---

### Task 3: ViewModel Sheet State

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailViewModel.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailViewModelTest.kt`

- [ ] **Step 1: Add failing ViewModel tests**

In `ForumThreadDetailViewModelTest.kt`, add imports:

```kotlin
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
import me.jbusdriver.modern.domain.model.ForumReply
```

Append tests inside `ForumThreadDetailViewModelTest`:

```kotlin
@Test
fun `openCommentsSheet uses parsed reply comments and content`() = runTest(testDispatcher) {
    val repository = FakeForumDetailRepository(CompletableDeferred())
    val viewModel = ForumThreadDetailViewModel(
        repository = repository,
        forumSettingsReader = FakeForumSettingsReader(),
        loadedGifTracker = FakeLoadedGifTracker(),
        siteConfig = FakeSiteConfig("https://forum.example.test/root"),
        navKey = RouteForumThreadDetail(42)
    )
    advanceUntilIdle()

    viewModel.openReplyCommentsSheet(floor = 2)

    val sheet = viewModel.uiState.value.commentSheet
    assertEquals(4773820, sheet?.pid)
    assertEquals("2樓", sheet?.floorLabel)
    assertEquals("Reply Author", sheet?.author)
    assertEquals("inline comment", sheet?.comments?.single()?.content)
    assertEquals(false, sheet?.isLoadingMore)
}

@Test
fun `loadMoreFloorComments appends comments and updates page info`() = runTest(testDispatcher) {
    val repository = FakeForumDetailRepository(CompletableDeferred())
    val viewModel = ForumThreadDetailViewModel(
        repository = repository,
        forumSettingsReader = FakeForumSettingsReader(),
        loadedGifTracker = FakeLoadedGifTracker(),
        siteConfig = FakeSiteConfig("https://forum.example.test/root"),
        navKey = RouteForumThreadDetail(42)
    )
    advanceUntilIdle()

    viewModel.openFirstPostCommentsSheet()
    viewModel.loadMoreFloorComments()
    advanceUntilIdle()

    val sheet = viewModel.uiState.value.commentSheet
    assertEquals(listOf("first post inline", "loaded page 2"), sheet?.comments?.map { it.content })
    assertEquals(PageInfo(activePage = 2, nextPage = 2), sheet?.pageInfo)
    assertEquals(false, sheet?.isLoadingMore)
}

@Test
fun `loadMoreFloorComments failure keeps existing comments visible`() = runTest(testDispatcher) {
    val repository = FakeForumDetailRepository(
        staleRefresh = CompletableDeferred(),
        commentError = RuntimeException("comment failed")
    )
    val viewModel = ForumThreadDetailViewModel(
        repository = repository,
        forumSettingsReader = FakeForumSettingsReader(),
        loadedGifTracker = FakeLoadedGifTracker(),
        siteConfig = FakeSiteConfig("https://forum.example.test/root"),
        navKey = RouteForumThreadDetail(42)
    )
    advanceUntilIdle()

    viewModel.openFirstPostCommentsSheet()
    viewModel.loadMoreFloorComments()
    advanceUntilIdle()

    val sheet = viewModel.uiState.value.commentSheet
    assertEquals(listOf("first post inline"), sheet?.comments?.map { it.content })
    assertEquals(R.string.load_failed, sheet?.error)
    assertEquals(false, sheet?.isLoadingMore)
}
```

Update `FakeForumDetailRepository` constructor:

```kotlin
private class FakeForumDetailRepository(
    private val staleRefresh: CompletableDeferred<ForumThreadDetail>,
    private val commentError: Throwable? = null
) : ForumRepository {
```

Replace its `loadFloorComments` stub with:

```kotlin
override suspend fun loadFloorComments(
    tid: Int,
    pid: Int,
    page: Int,
    forceRefresh: Boolean
): ForumCommentPageResult {
    commentError?.let { throw it }
    return ForumCommentPageResult(
        pid = pid,
        comments = listOf(comment("loaded page $page")),
        pageInfo = PageInfo(activePage = page, nextPage = page)
    )
}
```

Replace the existing `detail()` helper at the bottom with this richer helper:

```kotlin
private fun detail(
    title: String,
    floorOrder: ForumFloorOrder,
    page: Int = 1
): ForumThreadDetail = ForumThreadDetail(
    tid = 42,
    typeId = 0,
    typeName = floorOrder.name,
    typeColor = "",
    title = title,
    viewCount = 0,
    replyCount = 1,
    author = "Original Poster",
    authorUid = 0,
    authorAvatar = "",
    postTime = "",
    contentBlocks = listOf(ContentBlock.RichText(listOf())),
    comments = listOf(comment("first post inline")),
    replies = listOf(
        ForumReply(
            floor = 2,
            author = "Reply Author",
            authorUid = 0,
            authorAvatar = "",
            authorGroup = "",
            contentBlocks = listOf(ContentBlock.RichText(listOf())),
            postTime = "",
            pid = 4773820,
            comments = listOf(comment("inline comment")),
            commentPageInfo = PageInfo(activePage = 1, nextPage = 1)
        )
    ),
    pageInfo = PageInfo(activePage = page, nextPage = page, referPages = listOf(page)),
    pid = 4773811,
    commentPageInfo = PageInfo(activePage = 1, nextPage = 2)
)

private fun comment(content: String): Comment =
    Comment(
        author = "Commenter",
        authorAvatar = "",
        content = content,
        time = "2026-6-10"
    )
```

- [ ] **Step 2: Run ViewModel tests and verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumThreadDetailViewModelTest"
```

Expected: FAIL because `commentSheet`, `openReplyCommentsSheet()`, `openFirstPostCommentsSheet()`, and `loadMoreFloorComments()` do not exist.

- [ ] **Step 3: Add sheet state types**

In `ForumThreadDetailViewModel.kt`, add imports:

```kotlin
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
```

Add state type above `ForumThreadDetailUiState`:

```kotlin
data class FloorCommentSheetState(
    val pid: Int,
    val floor: Int?,
    val floorLabel: String,
    val author: String,
    val contentBlocks: List<ContentBlock>,
    val comments: List<Comment>,
    val pageInfo: PageInfo,
    val isLoadingMore: Boolean = false,
    val error: Int? = null
)
```

Add field to `ForumThreadDetailUiState`:

```kotlin
val commentSheet: FloorCommentSheetState? = null
```

- [ ] **Step 4: Add sheet actions**

Inside `ForumThreadDetailViewModel`, add:

```kotlin
fun openFirstPostCommentsSheet() {
    val detail = _uiState.value.detail ?: return
    if (detail.pid == 0) return
    _uiState.update {
        it.copy(
            commentSheet = FloorCommentSheetState(
                pid = detail.pid,
                floor = null,
                floorLabel = "樓主",
                author = detail.author,
                contentBlocks = detail.contentBlocks,
                comments = detail.comments,
                pageInfo = detail.commentPageInfo
            )
        )
    }
}

fun openReplyCommentsSheet(floor: Int) {
    val reply = _uiState.value.detail?.replies?.firstOrNull { it.floor == floor } ?: return
    if (reply.pid == 0) return
    _uiState.update {
        it.copy(
            commentSheet = FloorCommentSheetState(
                pid = reply.pid,
                floor = reply.floor,
                floorLabel = "${reply.floor}樓",
                author = reply.author,
                contentBlocks = reply.contentBlocks,
                comments = reply.comments,
                pageInfo = reply.commentPageInfo
            )
        )
    }
}

fun dismissCommentsSheet() {
    _uiState.update { it.copy(commentSheet = null) }
}

fun loadMoreFloorComments() {
    val sheet = _uiState.value.commentSheet ?: return
    if (sheet.isLoadingMore || !sheet.pageInfo.hasNext) return
    val nextPage = sheet.pageInfo.nextPage
    viewModelScope.launch {
        _uiState.update {
            val current = it.commentSheet ?: return@update it
            if (current.pid != sheet.pid || current.isLoadingMore) it
            else it.copy(commentSheet = current.copy(isLoadingMore = true, error = null))
        }
        try {
            val result = repository.loadFloorComments(tid, sheet.pid, nextPage)
            _uiState.update {
                val current = it.commentSheet ?: return@update it
                if (current.pid != sheet.pid) return@update it
                it.copy(
                    commentSheet = current.copy(
                        comments = current.comments + result.comments,
                        pageInfo = result.pageInfo,
                        isLoadingMore = false,
                        error = null
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                val current = it.commentSheet ?: return@update it
                if (current.pid != sheet.pid) return@update it
                it.copy(
                    commentSheet = current.copy(
                        isLoadingMore = false,
                        error = R.string.load_failed
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 5: Run ViewModel tests and verify they pass**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumThreadDetailViewModelTest"
```

Expected: PASS.

- [ ] **Step 6: Commit ViewModel work**

Run:

```bash
git status --short
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailViewModel.kt app/src/test/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailViewModelTest.kt
git status --short
git commit -m "feat: manage forum floor comment sheet state"
```

Expected: only ViewModel and ViewModel tests are staged.

---

### Task 4: Compose UI for Previews and Bottom Sheet

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailSections.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Add string resources**

Add to `app/src/main/res/values/strings.xml` in the Forum section:

```xml
<string name="view_more_comments">查看更多点评</string>
<string name="floor_comments">点评</string>
<string name="retry_load_comments">重新加载点评</string>
```

Add to `app/src/main/res/values-en/strings.xml` in the Forum section:

```xml
<string name="view_more_comments">View more reviews</string>
<string name="floor_comments">Reviews</string>
<string name="retry_load_comments">Retry reviews</string>
```

- [ ] **Step 2: Add comments preview and reusable comment row**

In `ForumThreadDetailSections.kt`, replace the body of `CommentsSection` with a call to a reusable row helper:

```kotlin
@Composable
internal fun CommentsSection(comments: List<Comment>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.comment),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            comments.forEach { comment ->
                ForumCommentRow(comment = comment)
            }
        }
    }
}
```

Add below it:

```kotlin
@Composable
private fun ForumCommentRow(
    comment: Comment,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(bottom = 8.dp)) {
        AsyncImage(
            model = comment.authorAvatar,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "${comment.author}  ${comment.time}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                comment.content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
```

Add `PostCommentsPreview`:

```kotlin
@Composable
internal fun PostCommentsPreview(
    comments: List<Comment>,
    pageInfo: PageInfo,
    onViewMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (comments.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            stringResource(R.string.floor_comments),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        comments.take(3).forEach { comment ->
            ForumCommentRow(comment = comment)
        }
        if (comments.size > 3 || pageInfo.hasNext) {
            TextButton(onClick = onViewMore) {
                Text(stringResource(R.string.view_more_comments))
            }
        }
    }
}
```

Add import:

```kotlin
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
```

- [ ] **Step 3: Update ReplyItem to show comment preview**

Change `ReplyItem` signature:

```kotlin
internal fun ReplyItem(
    reply: ForumReply,
    onImageClick: (List<String>, Int) -> Unit,
    loadedGifUrls: Set<String> = emptySet(),
    autoLoadGifs: Boolean = false,
    onLoadGif: (String) -> Unit = {},
    onLoadAllGifs: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onViewComments: (ForumReply) -> Unit = {}
)
```

After `ForumPostContent(...)` inside `ReplyItem`, add:

```kotlin
PostCommentsPreview(
    comments = reply.comments,
    pageInfo = reply.commentPageInfo,
    onViewMore = { onViewComments(reply) }
)
```

- [ ] **Step 4: Add FloorCommentsBottomSheet**

In `ForumThreadDetailSections.kt`, add imports:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
```

Add:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FloorCommentsBottomSheet(
    sheet: FloorCommentSheetState,
    loadedGifUrls: Set<String>,
    autoLoadGifs: Boolean,
    onImageClick: (List<String>, Int) -> Unit,
    onLoadGif: (String) -> Unit,
    onLoadAllGifs: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(nearEnd, sheet.pageInfo.nextPage, sheet.isLoadingMore) {
        if (nearEnd && sheet.pageInfo.hasNext && !sheet.isLoadingMore) {
            onLoadMore()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        FloorCommentsSheetContent(
            sheet = sheet,
            listState = listState,
            loadedGifUrls = loadedGifUrls,
            autoLoadGifs = autoLoadGifs,
            onImageClick = onImageClick,
            onLoadGif = onLoadGif,
            onLoadAllGifs = onLoadAllGifs,
            onRetry = onRetry
        )
    }
}

@Composable
private fun FloorCommentsSheetContent(
    sheet: FloorCommentSheetState,
    listState: LazyListState,
    loadedGifUrls: Set<String>,
    autoLoadGifs: Boolean,
    onImageClick: (List<String>, Int) -> Unit,
    onLoadGif: (String) -> Unit,
    onLoadAllGifs: () -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    "${sheet.floorLabel}  ${sheet.author}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                ForumPostContent(
                    blocks = sheet.contentBlocks,
                    onImageClick = onImageClick,
                    modifier = Modifier.padding(top = 8.dp),
                    loadedGifUrls = loadedGifUrls,
                    autoLoadGifs = autoLoadGifs,
                    onLoadGif = onLoadGif,
                    onLoadAllGifs = onLoadAllGifs
                )
            }
        }
        item(key = "comments_title") {
            Text(
                stringResource(R.string.floor_comments),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(sheet.comments, key = { "${it.author}-${it.time}-${it.content.hashCode()}" }) { comment ->
            ForumCommentRow(comment = comment)
        }
        item(key = "footer") {
            when {
                sheet.isLoadingMore -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                sheet.error != null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.retry_load_comments))
                    }
                }

                !sheet.pageInfo.hasNext -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.no_more),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

Also add runtime imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
```

- [ ] **Step 5: Wire first-post preview, reply preview, and sheet in screen**

In `ForumThreadDetailScreen.kt`, where the first-post content `Card` renders `ForumPostContent`, add after `ForumPostContent(...)`:

```kotlin
PostCommentsPreview(
    comments = detail.comments,
    pageInfo = detail.commentPageInfo,
    onViewMore = viewModel::openFirstPostCommentsSheet,
    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
)
```

In the `ReplyItem` call, add:

```kotlin
onViewComments = { reply -> viewModel.openReplyCommentsSheet(reply.floor) }
```

Near the existing `dialogBlocks?.let` block, add:

```kotlin
state.commentSheet?.let { sheet ->
    FloorCommentsBottomSheet(
        sheet = sheet,
        loadedGifUrls = loadedGifUrls,
        autoLoadGifs = autoLoadGifs,
        onImageClick = onImageClick,
        onLoadGif = { viewModel.onLoadGif(it) },
        onLoadAllGifs = { viewModel.onLoadAllGifs() },
        onLoadMore = { viewModel.loadMoreFloorComments() },
        onRetry = { viewModel.loadMoreFloorComments() },
        onDismiss = { viewModel.dismissCommentsSheet() }
    )
}
```

- [ ] **Step 6: Run UI compile check**

Run:

```bash
./gradlew assembleDebug
```

Expected: PASS. If Compose import or API errors appear, fix only the files in this task and rerun `assembleDebug`.

- [ ] **Step 7: Commit UI work**

Run:

```bash
git status --short
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailSections.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git status --short
git commit -m "feat: show forum floor comments"
```

Expected: only UI and string files are staged.

---

### Task 5: Integration Verification and Cleanup

**Files:**
- Modify only files already touched in Tasks 1-4 if verification exposes compile/test issues.

- [ ] **Step 1: Run targeted parser tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.parser.ForumThreadParserTest"
```

Expected: PASS.

- [ ] **Step 2: Run repository tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.ForumRepositoryCacheFlowTest"
```

Expected: PASS.

- [ ] **Step 3: Run ViewModel tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumThreadDetailViewModelTest"
```

Expected: PASS.

- [ ] **Step 4: Run debug build**

Run:

```bash
./gradlew assembleDebug
```

Expected: PASS.

- [ ] **Step 5: Inspect for dead code and accidental hard-coded UI strings**

Run:

```bash
rg -n "查看更多点评|重新加载点评|View more reviews|Retry reviews" app/src/main/java app/src/main/res
```

Expected: Chinese/English visible strings appear only in `app/src/main/res/values/strings.xml` and `app/src/main/res/values-en/strings.xml`.

Run:

```bash
rg -n "parseForumComments|parseForumCommentPageInfo|FloorCommentSheetState|openReplyCommentsSheet|openFirstPostCommentsSheet" app/src/main/java app/src/test/java
```

Expected: each new helper or action has at least one production use and relevant test coverage.

- [ ] **Step 6: Commit cleanup fixes if any**

If Step 5 required edits, run:

```bash
git status --short
git add app/src/main/java app/src/main/res app/src/test
git status --short
git commit -m "fix: polish forum floor comments"
```

Expected: skip this commit if there are no cleanup edits.

- [ ] **Step 7: Final status check**

Run:

```bash
git status --short
```

Expected: no unstaged or staged files unless the user has unrelated pre-existing changes.

---

## Self-Review Notes

- Spec coverage: model, parser, repository, ViewModel sheet state, UI preview/sheet, strings, tests, and debug build are covered.
- Scope: this plan stays inside forum thread detail and does not redesign forum navigation or broad list/reducer architecture.
- Type consistency: `ForumCommentPageResult`, `FloorCommentSheetState`, `commentPageInfo`, `loadFloorComments`, `openFirstPostCommentsSheet`, `openReplyCommentsSheet`, and `loadMoreFloorComments` are introduced before later tasks use them.
- No implementation task should rewrite unrelated mojibake strings. New visible UI text must be resource-backed.
