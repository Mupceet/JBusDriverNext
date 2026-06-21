# Forum Floor Comments Design

## Context

Forum thread detail pages already parse and render the main post, thread-level comments, and paged floor replies. The reference HTML at `C:\Users\LGZ\Downloads\172059\快30了，母胎solo，真的压抑得快要变态了，到底要不要嫖... - 老司機福利討論區 - 老司機論壇.html` shows that each floor can also include its own Discuz comment block:

- Comments live under `div#comment_<pid>.cm`.
- Comment rows use `div.pstl`, with author/avatar data in `.psta` and text/time in `.psti`.
- Extra comment pages are linked with `forum.php?mod=misc&action=commentmore&tid=<tid>&pid=<pid>&page=<n>`.
- Comment pagination can appear before normal thread reply pagination, so comment pagination must remain isolated from `ForumThreadDetail.pageInfo`.

The goal is to show per-floor comments inside the thread detail UI without forcing the thread detail screen to eagerly load every comment page for every floor.

## User Experience

Each post card, including the first post and every reply floor, shows a compact comment preview at the bottom when that floor has comments.

- Show the first 3 comments inline.
- If the floor has more than 3 parsed comments or its comment pagination has a next page, show a text action: `查看更多点评`.
- If the floor has 1 to 3 comments and no next comment page, show only the inline comments.
- If the floor has no comments, show nothing.

Tapping `查看更多点评` opens a `ModalBottomSheet`.

The sheet shows:

- The selected floor's content at the top.
- The full loaded comment list below it.
- Automatic loading of the next comment page when the sheet list scrolls near the bottom.
- A loading spinner while fetching.
- A retry action when loading fails.
- `没有更多了` when all comment pages are loaded.

The bottom sheet pagination is independent of the thread reply pagination. Loading more comments must not change the main thread list's current reply page, scroll behavior, or fresh-data prompt state.

## Data Model

Add a floor-level identifier and comments to the domain models.

- Add `pid: Int` to the first-post representation in `ForumThreadDetail`.
- Add `pid: Int` to `ForumReply`.
- Keep the existing `Comment` shape for individual comments unless implementation finds a compatibility issue. Its fields already match the floor comment needs: `author`, `authorAvatar`, `content`, `time`.
- Add `commentPageInfo: PageInfo` to both the first post data on `ForumThreadDetail` and `ForumReply`.
- Add `comments: List<Comment>` to `ForumReply`.
- Keep `ForumThreadDetail.comments` for the first post's comments, but treat it as first-post comments rather than thread-level comments.
- Add a new result model:

```kotlin
data class ForumCommentPageResult(
    val pid: Int,
    val comments: List<Comment>,
    val pageInfo: PageInfo
)
```

Gson compatibility must be preserved because thread details are disk cached. New properties should have defaults where Kotlin allows it, or cache key versions should be bumped if defaults are not sufficient for deserializing legacy cache entries.

## Parsing

Extract shared comment parsing helpers in `ForumThreadParser.kt` or a small adjacent parser file if that keeps the existing file from growing too much.

Parsing responsibilities:

- Parse a post id from floor containers using stable ids such as `post_<pid>`, `postmessage_<pid>`, `comment_<pid>`, or nearby `post_rate_div_<pid>`.
- For each floor, find its own `div#comment_<pid>.cm`.
- Parse all `div.pstl` comment rows inside that block.
- Resolve relative avatar URLs using the forum base URL.
- Extract author from `.psta a.xw1`, `.psta a[href*=uid]`, or `.psti a.xi2`.
- Extract content from `.psti` while excluding author links and time spans.
- Extract time from `.psti .xg1 span[title]`, `.psti .xg1 span`, or `.psti .xg1` text.
- Parse comment pagination from the comment block's local `.pg` section, not the document-level reply pagination.
- Treat `.pg strong` as the active page and `.pg a.nxt[href*=commentmore]` as the next page.

Add a parser for fetched `commentmore` pages/fragments. It should accept either a full document or an HTML fragment and return `ForumCommentPageResult`.

The existing `parseForumPageInfo(doc)` must continue to ignore comment pagination links, preserving the already-tested behavior that comment pagination does not shadow reply pagination.

## Repository

Extend `ForumRepository` with a comment-page load path:

```kotlin
suspend fun loadFloorComments(
    tid: Int,
    pid: Int,
    page: Int,
    forceRefresh: Boolean = false
): ForumCommentPageResult
```

The implementation builds:

```text
<baseUrl>/forum/forum.php?mod=misc&action=commentmore&tid=<tid>&pid=<pid>&page=<page>
```

Use the existing `ForumSessionClient` so comment requests share the forum session and cookies. Cache comment pages through `CacheStore` with a site-aware key such as:

```text
forum:<baseUrl>:floor-comments:v1:<tid>:<pid>:<page>
```

Use a short TTL aligned with non-first thread detail pages. This keeps repeated sheet opens responsive without pretending comments are immutable.

## ViewModel State

Keep comment sheet state inside `ForumThreadDetailViewModel` so UI remains state-driven and does not own network work.

Suggested state:

```kotlin
data class FloorCommentSheetState(
    val floorKey: FloorKey,
    val author: String,
    val floorLabel: String,
    val contentBlocks: List<ContentBlock>,
    val comments: List<Comment>,
    val pageInfo: PageInfo,
    val isLoadingMore: Boolean = false,
    val error: Int? = null
)
```

`FloorKey` can identify either the first post or a reply by `pid` and floor number. It should use `pid` for network loading because `commentmore` requires `pid`.

ViewModel actions:

- `openCommentsSheet(floor)` initializes the sheet from comments already parsed on the thread detail page.
- `dismissCommentsSheet()` clears the sheet state.
- `loadMoreFloorComments()` loads the next comment page for the selected `pid` if there is one and no request is already in flight.
- On success, append comments and update the sheet `PageInfo`.
- On failure, keep existing comments visible and expose a retryable error.

The sheet comment loader should have its own in-flight guard. It should not call `beginRequest()` for thread detail loading, because comment pagination must not invalidate active thread detail or reply-pagination requests.

## UI Components

Update `ForumThreadDetailScreen.kt` and `ForumThreadDetailSections.kt` in focused steps.

Add `PostCommentsPreview`:

- Receives `comments`, `pageInfo`, and `onViewMore`.
- Renders at most 3 comments.
- Uses compact typography and avatar styling consistent with the existing `CommentsSection`.
- Shows `查看更多点评` when `comments.size > 3 || pageInfo.hasNext`.

Update the first-post card and `ReplyItem`:

- Place `PostCommentsPreview` after `ForumPostContent`.
- For replies, pass `reply.comments` and `reply.commentPageInfo`.
- For the first post, pass `detail.comments` and the first post comment page info.

Add `FloorCommentsBottomSheet`:

- Use Material3 `ModalBottomSheet` and `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
- Use a sheet-local `LazyColumn`.
- Render the selected floor content first, then comments.
- Use `derivedStateOf` on the sheet list state to trigger `loadMoreFloorComments()` near the end.
- Show spinner, retry text button, and no-more text as footer states.

Strings should be added to `strings.xml` and `values-en/strings.xml` rather than hard-coded:

- `view_more_comments`: `查看更多点评` / `View more reviews`
- `floor_comments`: `点评` / `Reviews`
- `retry_load_comments`: `重新加载点评` / `Retry reviews`

The existing mojibake seen in terminal output appears to be an encoding display issue. Do not rewrite unrelated string resources as part of this feature.

## Testing

Parser tests:

- Add a fixture derived from the provided reference HTML, reduced to a first post and one reply with comment blocks.
- Verify first-post comments are parsed with `pid`, first 3 comments available, and comment page info detects the next page.
- Verify reply-floor comments are parsed independently.
- Verify `parseForumThreadDetail(...).pageInfo` still follows thread reply pagination, not comment pagination.
- Verify `parseForumFloorComments(...)` handles a `commentmore` fragment and returns the correct `PageInfo`.

Repository tests:

- Verify `loadFloorComments()` builds a site-aware `commentmore` URL and parses the response through the session client path.
- Verify cache keys include `tid`, `pid`, and page.

ViewModel tests:

- Opening the sheet uses already parsed comments and content.
- Loading more appends comments and updates `pageInfo`.
- Loading more is ignored when already loading or no next page exists.
- Failure keeps existing sheet content visible and exposes a retry state.

Verification commands after implementation:

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.parser.ForumThreadParserTest"
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumThreadDetailViewModelTest"
./gradlew assembleDebug
```

## Trade-offs

This design intentionally loads later comment pages only after the user opens a floor sheet. That makes the main thread detail page fast and avoids network fan-out across many floors.

The main cost is a slightly larger domain model and ViewModel state. That cost is worthwhile because floor comments are first-class thread-detail data: they need parsing, caching, pagination, and UI rendering with predictable state ownership.

The implementation should avoid a broad forum UI refactor. `ForumThreadDetailScreen.kt`, `ForumThreadDetailSections.kt`, `ForumThreadDetailViewModel.kt`, `ForumRepository.kt`, and `ForumThreadParser.kt` are already known large or central files, so changes should be narrow and helper extraction should happen only where it directly supports floor comments.
