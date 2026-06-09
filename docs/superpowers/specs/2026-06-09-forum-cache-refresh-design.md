# Forum Cache Refresh Interaction Design

## Goal

Improve the Forum feature so cached content no longer forces users to pull-refresh every screen manually.

The new behavior is:

- Show cached content immediately when available.
- Revalidate in the background after the cached content is shown.
- Update the UI according to each page's reading context.
- Keep background refresh failures silent when cached content is already visible.
- Show clear feedback only for explicit user refresh failures or first-load failures with no cache.
- Extend the cache foundation in a reusable way so non-Forum features can adopt it later.

This spec covers Forum screens first. The cache APIs should be reusable, but Movie, Detail, Search, and Collection screens are outside this implementation pass.

## Current Behavior

`DefaultForumRepository` currently uses `CacheStore.lruCached()` for Forum home and thread lists, and `CacheStore.persistentCached()` for thread details. These helpers return plain values. When a cache hit occurs, the Repository returns immediately and no network refresh follows.

The three Forum UI areas already support manual pull-to-refresh:

- `ForumBoardsScreen`
- `ForumThreadListScreen`
- `ForumThreadDetailScreen`

Thread lists and thread details also support automatic load-more near the end of the list. The missing piece is cache freshness: cached first-page content can remain stale until the user refreshes manually.

## Cache Foundation

Add cache metadata so callers can distinguish cached data from fresh network data and know whether a cache entry is expired.

Recommended model:

```kotlin
data class CacheEntry<T>(
    val value: T,
    val storedAtMillis: Long,
    val source: CacheSource,
    val isExpired: Boolean
)

enum class CacheSource {
    Memory,
    Disk,
    Network
}
```

Persist cached values in an envelope instead of writing only the payload JSON:

```kotlin
data class CacheEnvelope(
    val storedAtMillis: Long,
    val payloadJson: String
)
```

`CacheStore` should gain reusable typed APIs:

```kotlin
suspend inline fun <reified T> CacheStore.readCached(
    key: String,
    ttlMillis: Long,
    disk: Boolean = true
): CacheEntry<T>?

suspend inline fun <reified T> CacheStore.writeCached(
    key: String,
    value: T,
    disk: Boolean = true
): CacheEntry<T>
```

The implementation should keep the existing `lruCached()` and `persistentCached()` helpers for current non-Forum callers.

### Legacy Cache Compatibility

Existing disk and memory cache values are plain payload JSON. The new reader should handle both formats:

- If the value parses as `CacheEnvelope`, use its `storedAtMillis`.
- If the value parses as legacy payload JSON, return it with `storedAtMillis = 0` and `isExpired = true`.

This lets old Forum detail caches continue to render immediately while still triggering background revalidation.

### Time Source

Use an injectable or parameterized time source for cache metadata and TTL checks. Tests should be able to advance time without sleeping.

## Forum Cache Policy

TTL only decides whether an entry is stale. It does not block showing cached content.

Forum entry screens should use stale-while-revalidate behavior: show cache first, then perform a background network refresh. Explicit pull-to-refresh always bypasses cache and writes the latest result.

Recommended TTL values:

| Data | Storage | TTL |
| --- | --- | --- |
| Forum home | Memory + Disk | 5 minutes |
| Thread list page 1 | Memory + Disk | 2 minutes |
| Thread list page 2+ | Memory + Disk or Memory only | 5 minutes |
| Thread detail page 1 | Memory + Disk | 15 minutes |
| Thread detail reply pages | Memory + Disk | 10 minutes |

Cache keys must include a site identifier, preferably the active `baseUrl`, so switching mirror URLs cannot read stale data from another site.

## Repository Data Flow

Add Flow-based Forum loading APIs while preserving the existing parser and network code.

Recommended event model:

```kotlin
sealed interface CachedLoadEvent<out T> {
    data class Cached<T>(val entry: CacheEntry<T>) : CachedLoadEvent<T>
    data class Fresh<T>(val entry: CacheEntry<T>) : CachedLoadEvent<T>
    data class Failure(
        val throwable: Throwable,
        val hadCachedValue: Boolean
    ) : CachedLoadEvent<Nothing>
}
```

Recommended Forum APIs:

```kotlin
fun observeForumBoards(
    forceRefresh: Boolean = false
): Flow<CachedLoadEvent<ForumHomeData>>

fun observeThreads(
    fid: Int,
    page: Int,
    typeId: Int?,
    forceRefresh: Boolean = false
): Flow<CachedLoadEvent<ForumThreadPageResult>>

fun observeThreadDetail(
    tid: Int,
    page: Int,
    floorOrder: ForumFloorOrder,
    forceRefresh: Boolean = false
): Flow<CachedLoadEvent<ForumThreadDetail>>
```

Flow behavior:

1. Read cache unless `forceRefresh` is true.
2. If cache exists, emit `Cached(entry)` immediately.
3. Fetch network data when `forceRefresh` is true, cache is missing, cache is expired, or the caller requests revalidation on entry.
4. On network success, write cache and emit `Fresh(entry)`.
5. On network failure, emit `Failure(throwable, hadCachedValue = true)` if cached content was emitted; otherwise emit `Failure(throwable, hadCachedValue = false)`.

`fetchForumDocument()`, parser functions, and Discuz session cookie persistence should remain in the Repository path and keep their current behavior.

## ViewModel State

Add only the state needed for cache-aware interaction:

```kotlin
val lastUpdatedAtMillis: Long? = null
val isRevalidating: Boolean = false
val refreshMessage: String? = null
```

Thread list also needs a pending first-page refresh result:

```kotlin
val pendingFreshFirstPage: ForumThreadPageResult? = null
```

Thread detail may use a pending detail result when the user is reading away from the top:

```kotlin
val pendingFreshDetail: ForumThreadDetail? = null
```

ViewModels should consume `CachedLoadEvent` and distinguish:

- Initial cache display.
- Background revalidation.
- Fresh network success.
- Background failure with cache.
- First-load failure without cache.
- Explicit user refresh failure.

The UI can report whether the list is currently near the top through explicit events or method parameters. ViewModels should not directly depend on `LazyListState`.

## Page Interaction

### Forum Home

On entry:

1. Show cached home data immediately when available.
2. Revalidate in the background.
3. Replace the visible content automatically when fresh data arrives.

Background refresh failure with cached content is silent. Manual pull-to-refresh failure displays feedback but keeps the current content.

### Thread List

On entry:

1. Show cached first page immediately when available.
2. Revalidate page 1 in the background.
3. If the user is still at the top, apply fresh page 1 automatically.
4. If the user has scrolled away from the top, store the fresh page in `pendingFreshFirstPage` and show a lightweight "new posts available" prompt.

When the user accepts the prompt:

- Replace the first page.
- Clear already-loaded later pages.
- Reset `currentPage = 1`.
- Scroll to the top.

Manual pull-to-refresh should force-refresh page 1 and clear later pages to avoid mixing old pagination with a new first page.

Load-more behavior remains near-end automatic. Load-more failures should stop the loading indicator without turning the whole page into an error state.

Type filter changes use a distinct `fid + typeId` cache key, show cached data for that filter if available, and then revalidate.

### Thread Detail

On entry:

1. Show cached detail immediately when available.
2. Revalidate page 1 in the background.
3. Apply fresh title/main-post changes directly.
4. For reply changes while the user is away from the top, prefer a lightweight "thread updated" prompt before replacing visible content.

Manual pull-to-refresh should force-refresh page 1 and reset later reply pagination.

Floor order changes are explicit user actions. They should keep using distinct cache keys and show local loading while the selected order reloads.

Background refresh failure with cached content is silent. Manual refresh failure displays feedback but keeps the cached detail visible.

## Error Handling

Use these rules consistently:

- No cache and first network load fails: show an error empty state with pull-to-refresh retry.
- Cache exists and background refresh fails: stay silent.
- Cache exists and manual refresh fails: show lightweight feedback and retain visible content.
- Load-more fails: stop the load-more indicator and keep existing items.
- Cache parse fails: treat as cache miss and continue to network fetch.

## Testing

Add focused unit tests around the new behavior.

Cache tests:

- Envelope read/write.
- TTL expiration.
- Memory versus disk source.
- Legacy plain JSON compatibility.
- Cache miss when payload cannot be parsed.

Repository flow tests:

- Cached value emits before fresh value.
- No cache emits only fresh on network success.
- Cached value plus network failure emits `Failure(hadCachedValue = true)`.
- No cache plus network failure emits `Failure(hadCachedValue = false)`.
- Force refresh skips cache read and writes fresh data.

ViewModel tests:

- Forum home applies fresh background data automatically.
- Thread list applies fresh first page when the UI is at the top.
- Thread list stores pending first page when the UI is away from the top.
- Manual thread list refresh clears later pages.
- Thread detail retains cached content when background refresh fails.
- Manual refresh failures produce user-visible feedback without clearing content.

Existing Forum parser tests should remain unchanged unless model serialization requires a small compatibility test update.

Verification commands:

```bash
./gradlew test
./gradlew assembleDebug
```

## Implementation Boundaries

In scope:

- Cache metadata/envelope support.
- Reusable typed cache read/write APIs.
- Forum Repository Flow APIs.
- Forum ViewModel state and event handling.
- Forum UI prompts for pending fresh data.
- Unit tests for cache, Repository flows, and ViewModel behavior.

Out of scope:

- Migrating non-Forum screens to the new cache APIs.
- Replacing existing list handling with Paging.
- Changing Forum HTML parsers beyond serialization compatibility.
- Adding persistent user settings for TTL values.
