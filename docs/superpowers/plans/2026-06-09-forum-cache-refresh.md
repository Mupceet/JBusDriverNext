# Forum Cache Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Forum screens show cached content immediately, revalidate in the background, and update or prompt according to the user's reading context.

**Architecture:** Add reusable cache metadata APIs on top of the existing `CacheStore`, then add Flow-based Forum Repository methods that emit cached, fresh, and failure events. Forum ViewModels consume those events and expose small state additions for background refresh, pending fresh content, and user-visible messages.

**Tech Stack:** Kotlin, Coroutines Flow, Hilt, Jetpack Compose Material3, Gson, JUnit4, kotlinx-coroutines-test.

---

## File Map

- Create `app/src/main/java/me/jbusdriver/modern/core/cache/CacheModels.kt`: cache metadata models and TTL constants.
- Modify `app/src/main/java/me/jbusdriver/modern/core/cache/CacheStore.kt`: add metadata read/write helpers while preserving existing `lruCached()` and `persistentCached()`.
- Test `app/src/test/java/me/jbusdriver/modern/core/cache/CacheStoreMetadataTest.kt`: cache envelope, TTL, source, legacy JSON, parse failure.
- Modify `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt`: add Flow APIs, Forum cache policies, site-aware cache keys, and stale-while-revalidate orchestration.
- Modify `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`: implement `ForumCookiePersister` for Repository tests.
- Modify `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`: implement `ForumSettingsReader` for detail ViewModel tests.
- Modify `app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt`: implement `LoadedGifTracker` for detail ViewModel tests.
- Modify `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`: bind the new small interfaces to existing production classes.
- Test `app/src/test/java/me/jbusdriver/modern/data/ForumRepositoryCacheFlowTest.kt`: event ordering and failure behavior.
- Modify `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt`: consume cached load events and add pending refresh state.
- Test `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumCacheRefreshViewModelTest.kt`: ViewModel state transitions.
- Modify `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumBoardsScreen.kt`: optionally show lightweight revalidation state.
- Modify `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadListScreen.kt`: report top position and show "new posts available" prompt.
- Modify `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`: report top position and show "thread updated" prompt.

## Task 1: Add Reusable Cache Metadata APIs

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/core/cache/CacheModels.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/core/cache/CacheStore.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/core/cache/CacheStoreMetadataTest.kt`

- [ ] **Step 1: Write failing cache metadata tests**

Create `app/src/test/java/me/jbusdriver/modern/core/cache/CacheStoreMetadataTest.kt`:

```kotlin
package me.jbusdriver.modern.core.cache

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheStoreMetadataTest {
    data class Sample(val title: String)

    private class FakeCacheStore : CacheStore {
        val memory = linkedMapOf<String, String>()
        val disk = linkedMapOf<String, String>()

        override fun readMemory(key: String): String? = memory[key]
        override fun writeMemory(key: String, value: String) { memory[key] = value }
        override suspend fun readDisk(key: String): String? = disk[key]
        override suspend fun writeDisk(key: String, value: String) { disk[key] = value }
    }

    @Test
    fun `writeCached writes envelope to memory and disk`() = runTest {
        val store = FakeCacheStore()

        val entry = store.writeCached(
            key = "sample",
            value = Sample("fresh"),
            disk = true,
            nowMillis = { 1_000L }
        )

        assertEquals(Sample("fresh"), entry.value)
        assertEquals(1_000L, entry.storedAtMillis)
        assertEquals(CacheSource.Network, entry.source)
        assertFalse(entry.isExpired)
        assertTrue(store.memory.getValue("sample").contains("storedAtMillis"))
        assertEquals(store.memory.getValue("sample"), store.disk.getValue("sample"))
    }

    @Test
    fun `readCached returns memory entry before disk entry`() = runTest {
        val store = FakeCacheStore()
        store.writeCached("sample", Sample("disk"), disk = true, nowMillis = { 1_000L })
        store.memory.clear()
        store.writeDisk("sample", store.disk.getValue("sample"))

        val diskEntry = store.readCached<Sample>(
            key = "sample",
            ttlMillis = 10_000L,
            disk = true,
            nowMillis = { 2_000L }
        )

        assertEquals(Sample("disk"), diskEntry?.value)
        assertEquals(CacheSource.Disk, diskEntry?.source)

        val memoryEntry = store.readCached<Sample>(
            key = "sample",
            ttlMillis = 10_000L,
            disk = true,
            nowMillis = { 2_500L }
        )

        assertEquals(CacheSource.Memory, memoryEntry?.source)
    }

    @Test
    fun `readCached marks expired entries`() = runTest {
        val store = FakeCacheStore()
        store.writeCached("sample", Sample("old"), nowMillis = { 1_000L })

        val entry = store.readCached<Sample>(
            key = "sample",
            ttlMillis = 500L,
            nowMillis = { 2_000L }
        )

        assertTrue(entry?.isExpired == true)
    }

    @Test
    fun `readCached supports legacy plain json as expired cache`() = runTest {
        val store = FakeCacheStore()
        store.writeMemory("sample", """{"title":"legacy"}""")

        val entry = store.readCached<Sample>(
            key = "sample",
            ttlMillis = 10_000L,
            nowMillis = { 2_000L }
        )

        assertEquals(Sample("legacy"), entry?.value)
        assertEquals(0L, entry?.storedAtMillis)
        assertEquals(CacheSource.Memory, entry?.source)
        assertTrue(entry?.isExpired == true)
    }

    @Test
    fun `readCached returns null for malformed cache`() = runTest {
        val store = FakeCacheStore()
        store.writeMemory("sample", """{"storedAtMillis":1000,"payloadJson":"not-json"}""")

        val entry = store.readCached<Sample>(
            key = "sample",
            ttlMillis = 10_000L,
            nowMillis = { 2_000L }
        )

        assertNull(entry)
    }
}
```

- [ ] **Step 2: Run metadata tests and verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.core.cache.CacheStoreMetadataTest
```

Expected: compilation fails because `CacheEntry`, `CacheSource`, `CacheEnvelope`, `readCached`, and `writeCached` do not exist.

- [ ] **Step 3: Add cache metadata models**

Create `app/src/main/java/me/jbusdriver/modern/core/cache/CacheModels.kt`:

```kotlin
package me.jbusdriver.modern.core.cache

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

data class CacheEnvelope(
    val storedAtMillis: Long,
    val payloadJson: String
)

object ForumCacheTtl {
    const val HOME_MILLIS: Long = 5 * 60 * 1_000L
    const val THREAD_LIST_FIRST_PAGE_MILLIS: Long = 2 * 60 * 1_000L
    const val THREAD_LIST_NEXT_PAGE_MILLIS: Long = 5 * 60 * 1_000L
    const val THREAD_DETAIL_FIRST_PAGE_MILLIS: Long = 15 * 60 * 1_000L
    const val THREAD_DETAIL_NEXT_PAGE_MILLIS: Long = 10 * 60 * 1_000L
}
```

- [ ] **Step 4: Add typed metadata helpers to CacheStore**

Append these helpers to `app/src/main/java/me/jbusdriver/modern/core/cache/CacheStore.kt`, below `persistentCached()`:

```kotlin
suspend inline fun <reified T> CacheStore.readCached(
    key: String,
    ttlMillis: Long,
    disk: Boolean = true,
    noinline nowMillis: () -> Long = { System.currentTimeMillis() }
): CacheEntry<T>? {
    readMemory(key)?.let { json ->
        parseCacheEntry<T>(
            json = json,
            source = CacheSource.Memory,
            ttlMillis = ttlMillis,
            nowMillis = nowMillis
        )?.let { return it }
    }

    if (disk) {
        readDisk(key)?.let { json ->
            parseCacheEntry<T>(
                json = json,
                source = CacheSource.Disk,
                ttlMillis = ttlMillis,
                nowMillis = nowMillis
            )?.let { entry ->
                writeMemory(key, json)
                return entry
            }
        }
    }

    return null
}

suspend inline fun <reified T> CacheStore.writeCached(
    key: String,
    value: T,
    disk: Boolean = true,
    noinline nowMillis: () -> Long = { System.currentTimeMillis() }
): CacheEntry<T> {
    val storedAt = nowMillis()
    val envelope = CacheEnvelope(
        storedAtMillis = storedAt,
        payloadJson = GSON.toJson(value)
    )
    val json = GSON.toJson(envelope)
    writeMemory(key, json)
    if (disk) writeDisk(key, json)
    return CacheEntry(
        value = value,
        storedAtMillis = storedAt,
        source = CacheSource.Network,
        isExpired = false
    )
}

@PublishedApi
internal inline fun <reified T> parseCacheEntry(
    json: String,
    source: CacheSource,
    ttlMillis: Long,
    nowMillis: () -> Long
): CacheEntry<T>? {
    val envelope = runCatching { GSON.fromJson<CacheEnvelope>(json) }.getOrNull()
    val payloadJson = envelope?.payloadJson
    if (!payloadJson.isNullOrBlank()) {
        val value = runCatching { GSON.fromJson<T>(payloadJson) }.getOrNull() ?: return null
        val ageMillis = nowMillis() - envelope.storedAtMillis
        return CacheEntry(
            value = value,
            storedAtMillis = envelope.storedAtMillis,
            source = source,
            isExpired = ageMillis >= ttlMillis
        )
    }

    val legacyValue = runCatching { GSON.fromJson<T>(json) }.getOrNull() ?: return null
    return CacheEntry(
        value = legacyValue,
        storedAtMillis = 0L,
        source = source,
        isExpired = true
    )
}
```

- [ ] **Step 5: Run metadata tests and commit**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.core.cache.CacheStoreMetadataTest
```

Expected: PASS.

Commit:

```bash
git add app/src/main/java/me/jbusdriver/modern/core/cache/CacheModels.kt app/src/main/java/me/jbusdriver/modern/core/cache/CacheStore.kt app/src/test/java/me/jbusdriver/modern/core/cache/CacheStoreMetadataTest.kt
git commit -m "feat: add cache metadata helpers"
```

## Task 2: Add Forum Repository Cache Flow APIs

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/ForumRepositoryCacheFlowTest.kt`

- [ ] **Step 1: Write failing Repository flow tests**

Create `app/src/test/java/me/jbusdriver/modern/data/ForumRepositoryCacheFlowTest.kt`:

```kotlin
package me.jbusdriver.modern.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.writeCached
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumRepositoryCacheFlowTest {
    private class FakeCacheStore : CacheStore {
        val memory = linkedMapOf<String, String>()
        val disk = linkedMapOf<String, String>()
        override fun readMemory(key: String): String? = memory[key]
        override fun writeMemory(key: String, value: String) { memory[key] = value }
        override suspend fun readDisk(key: String): String? = disk[key]
        override suspend fun writeDisk(key: String, value: String) { disk[key] = value }
    }

    private class FakeSessionClient(
        private val fetch: suspend (String) -> Document
    ) : ForumSessionClient {
        override suspend fun warmUp() = Unit
        override suspend fun fetchDocument(url: String): Document = fetch(url)
        override fun destroy() = Unit
    }

    private class FakeCookiePersister : ForumCookiePersister {
        override suspend fun persistCookies() = Unit
    }

    private class FakeSiteConfig(override var baseUrl: String = "https://site.test") : SiteConfig {
        override fun resolve(pathOrUrl: String): String = pathOrUrl
    }

    private fun homeHtml(title: String) = Jsoup.parse(
        """
        <html><head><title>$title</title></head><body>
          <div id="category_1" class="bm">
            <div class="bm_h"><h2>Group</h2></div>
            <table><tbody>
              <tr><td><h2><a href="forum.php?mod=forumdisplay&fid=7">Board</a></h2></td></tr>
            </tbody></table>
          </div>
        </body></html>
        """.trimIndent()
    )

    @Test
    fun `observeForumBoards emits cached before fresh`() = runTest {
        val cache = FakeCacheStore()
        val cached = ForumHomeData(emptyList(), ForumHomeSummary(), emptyList())
        cache.writeCached(
            key = "forum:https://site.test:boards",
            value = cached,
            nowMillis = { 1_000L }
        )
        val repository = DefaultForumRepository(
            sessionClient = FakeSessionClient { homeHtml("fresh") },
            cookiePersister = FakeCookiePersister(),
            cacheStore = cache,
            siteConfig = FakeSiteConfig()
        )

        val events = repository.observeForumBoards(
            forceRefresh = false,
            revalidate = true,
            nowMillis = { 2_000L }
        ).toList()

        assertTrue(events[0] is CachedLoadEvent.Cached)
        assertTrue(events[1] is CachedLoadEvent.Fresh)
    }

    @Test
    fun `observeForumBoards emits cached failure when background refresh fails`() = runTest {
        val cache = FakeCacheStore()
        cache.writeCached(
            key = "forum:https://site.test:boards",
            value = ForumHomeData(emptyList(), ForumHomeSummary(), emptyList()),
            nowMillis = { 1_000L }
        )
        val repository = DefaultForumRepository(
            sessionClient = FakeSessionClient { throw IllegalStateException("offline") },
            cookiePersister = FakeCookiePersister(),
            cacheStore = cache,
            siteConfig = FakeSiteConfig()
        )

        val events = repository.observeForumBoards(revalidate = true).toList()

        val failure = events.last() as CachedLoadEvent.Failure
        assertTrue(failure.hadCachedValue)
        assertEquals("offline", failure.throwable.message)
    }
}
```

- [ ] **Step 2: Run Repository tests and verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.data.ForumRepositoryCacheFlowTest
```

Expected: compilation fails because `ForumCookiePersister`, `CachedLoadEvent`, `observeForumBoards`, and the optional `revalidate`/`nowMillis` parameters do not exist.

- [ ] **Step 3: Add CachedLoadEvent**

Add this model to `app/src/main/java/me/jbusdriver/modern/core/cache/CacheModels.kt`:

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

- [ ] **Step 4: Add ForumCookiePersister**

Modify `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`.

Add this interface above `ForumSessionManager`:

```kotlin
interface ForumCookiePersister {
    suspend fun persistCookies()
}
```

Change the class declaration:

```kotlin
class ForumSessionManager @Inject constructor(
    private val siteConfig: SiteConfig,
    private val cookieStore: SessionCookieStore
) : ForumCookiePersister {
```

Change the existing `persistCookies()` method:

```kotlin
override suspend fun persistCookies() {
    cookieStore.saveCookies(siteConfig.referer())
}
```

Modify `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`.

Add imports:

```kotlin
import me.jbusdriver.modern.data.ForumCookiePersister
import me.jbusdriver.modern.data.ForumSessionManager
```

Add this binding near the other Forum bindings:

```kotlin
@Binds
@Singleton
abstract fun bindForumCookiePersister(
    impl: ForumSessionManager
): ForumCookiePersister
```

- [ ] **Step 5: Extend ForumRepository interface**

Modify `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt` imports:

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.ForumCacheTtl
import me.jbusdriver.modern.core.cache.readCached
import me.jbusdriver.modern.core.cache.writeCached
```

Change the `DefaultForumRepository` constructor parameter:

```kotlin
class DefaultForumRepository @Inject constructor(
    private val sessionClient: ForumSessionClient,
    private val cookiePersister: ForumCookiePersister,
    private val cacheStore: CacheStore,
    private val siteConfig: SiteConfig
) : ForumRepository {
```

Change cookie persistence inside `fetchForumDocument()`:

```kotlin
cookiePersister.persistCookies()
```

Add these methods to `interface ForumRepository`:

```kotlin
fun observeForumBoards(
    forceRefresh: Boolean = false,
    revalidate: Boolean = true,
    nowMillis: () -> Long = { System.currentTimeMillis() }
): Flow<CachedLoadEvent<ForumHomeData>>

fun observeThreads(
    fid: Int,
    page: Int,
    typeId: Int? = null,
    forceRefresh: Boolean = false,
    revalidate: Boolean = true,
    nowMillis: () -> Long = { System.currentTimeMillis() }
): Flow<CachedLoadEvent<ForumThreadPageResult>>

fun observeThreadDetail(
    tid: Int,
    page: Int = 1,
    floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
    forceRefresh: Boolean = false,
    revalidate: Boolean = true,
    nowMillis: () -> Long = { System.currentTimeMillis() }
): Flow<CachedLoadEvent<ForumThreadDetail>>
```

- [ ] **Step 6: Implement site-aware Forum flow helpers**

Add these helpers inside `DefaultForumRepository`:

```kotlin
private fun forumCachePrefix(): String = "forum:${siteConfig.baseUrl}"

private fun forumBoardsCacheKey(): String = "${forumCachePrefix()}:boards"

private fun forumThreadsCacheKey(fid: Int, page: Int, typeId: Int?): String =
    "${forumCachePrefix()}:threads:$fid:$page:${typeId ?: "all"}"

private fun forumDetailCacheKey(tid: Int, page: Int, floorOrder: ForumFloorOrder): String =
    "${forumCachePrefix()}:detail:v2:$tid:$page:${floorOrder.name.lowercase()}"

private fun threadListTtl(page: Int): Long =
    if (page == 1) ForumCacheTtl.THREAD_LIST_FIRST_PAGE_MILLIS
    else ForumCacheTtl.THREAD_LIST_NEXT_PAGE_MILLIS

private fun threadDetailTtl(page: Int): Long =
    if (page == 1) ForumCacheTtl.THREAD_DETAIL_FIRST_PAGE_MILLIS
    else ForumCacheTtl.THREAD_DETAIL_NEXT_PAGE_MILLIS
```

Implement a reusable Flow helper inside `DefaultForumRepository`:

```kotlin
private inline fun <reified T> observeCached(
    key: String,
    ttlMillis: Long,
    disk: Boolean,
    forceRefresh: Boolean,
    revalidate: Boolean,
    noinline nowMillis: () -> Long,
    crossinline fetch: suspend () -> T
): Flow<CachedLoadEvent<T>> = flow {
    var emittedCache = false
    val cached = if (forceRefresh) null else cacheStore.readCached<T>(
        key = key,
        ttlMillis = ttlMillis,
        disk = disk,
        nowMillis = nowMillis
    )

    if (cached != null) {
        emittedCache = true
        emit(CachedLoadEvent.Cached(cached))
    }

    val shouldFetch = forceRefresh || cached == null || cached.isExpired || revalidate
    if (!shouldFetch) return@flow

    try {
        val fresh = fetch()
        val entry = cacheStore.writeCached(
            key = key,
            value = fresh,
            disk = disk,
            nowMillis = nowMillis
        )
        emit(CachedLoadEvent.Fresh(entry))
    } catch (throwable: Throwable) {
        emit(CachedLoadEvent.Failure(throwable, emittedCache))
    }
}
```

- [ ] **Step 7: Implement Forum observe methods**

Add these overrides in `DefaultForumRepository`:

```kotlin
override fun observeForumBoards(
    forceRefresh: Boolean,
    revalidate: Boolean,
    nowMillis: () -> Long
): Flow<CachedLoadEvent<ForumHomeData>> {
    val url = "${siteConfig.baseUrl}/forum/forum.php"
    return observeCached(
        key = forumBoardsCacheKey(),
        ttlMillis = ForumCacheTtl.HOME_MILLIS,
        disk = true,
        forceRefresh = forceRefresh,
        revalidate = revalidate,
        nowMillis = nowMillis
    ) {
        val doc = fetchForumDocument(url)
        parseForumHomeData(doc, siteConfig.baseUrl)
    }
}

override fun observeThreads(
    fid: Int,
    page: Int,
    typeId: Int?,
    forceRefresh: Boolean,
    revalidate: Boolean,
    nowMillis: () -> Long
): Flow<CachedLoadEvent<ForumThreadPageResult>> {
    val baseUrl = "${siteConfig.baseUrl}/forum/forum.php?mod=forumdisplay&fid=$fid&page=$page"
    val url = if (typeId != null) "$baseUrl&filter=typeid&typeid=$typeId" else baseUrl
    return observeCached(
        key = forumThreadsCacheKey(fid, page, typeId),
        ttlMillis = threadListTtl(page),
        disk = true,
        forceRefresh = forceRefresh,
        revalidate = revalidate && page == 1,
        nowMillis = nowMillis
    ) {
        val doc = fetchForumDocument(url)
        parseForumThreads(doc, siteConfig.baseUrl)
    }
}

override fun observeThreadDetail(
    tid: Int,
    page: Int,
    floorOrder: ForumFloorOrder,
    forceRefresh: Boolean,
    revalidate: Boolean,
    nowMillis: () -> Long
): Flow<CachedLoadEvent<ForumThreadDetail>> {
    val url = buildForumThreadDetailUrl(siteConfig.baseUrl, tid, page, floorOrder)
    return observeCached(
        key = forumDetailCacheKey(tid, page, floorOrder),
        ttlMillis = threadDetailTtl(page),
        disk = true,
        forceRefresh = forceRefresh,
        revalidate = revalidate && page == 1,
        nowMillis = nowMillis
    ) {
        val doc = fetchForumDocument(url)
        parseForumThreadDetail(doc, siteConfig.baseUrl)
    }
}
```

- [ ] **Step 8: Keep old suspend methods as wrappers**

Change existing methods to collect the fresh-or-cached value from the new APIs:

```kotlin
override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData =
    observeForumBoards(forceRefresh = forceRefresh, revalidate = false).firstValue()

override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult =
    observeThreads(fid, page, typeId, forceRefresh = forceRefresh, revalidate = false).firstValue()

override suspend fun loadThreadDetail(
    tid: Int,
    page: Int,
    floorOrder: ForumFloorOrder,
    forceRefresh: Boolean
): ForumThreadDetail =
    observeThreadDetail(tid, page, floorOrder, forceRefresh = forceRefresh, revalidate = false).firstValue()

private suspend fun <T> Flow<CachedLoadEvent<T>>.firstValue(): T {
    return when (val event = first()) {
        is CachedLoadEvent.Cached -> event.entry.value
        is CachedLoadEvent.Fresh -> event.entry.value
        is CachedLoadEvent.Failure -> throw event.throwable
    }
}
```

Add import:

```kotlin
import kotlinx.coroutines.flow.first
```

- [ ] **Step 9: Run Repository tests and commit**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.data.ForumRepositoryCacheFlowTest
```

Expected: PASS.

Commit:

```bash
git add app/src/main/java/me/jbusdriver/modern/core/cache/CacheModels.kt app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt app/src/test/java/me/jbusdriver/modern/data/ForumRepositoryCacheFlowTest.kt
git commit -m "feat: add forum cache refresh flows"
```

## Task 3: Update Forum Home ViewModel

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumCacheRefreshViewModelTest.kt`

- [ ] **Step 1: Write failing Forum home ViewModel tests**

Create `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumCacheRefreshViewModelTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.forum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.data.ForumFloorOrder
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForumCacheRefreshViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeForumRepository(
        val boardsFlow: Flow<CachedLoadEvent<ForumHomeData>>
    ) : ForumRepository {
        override fun observeForumBoards(forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long) = boardsFlow
        override fun observeThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumThreadPageResult>> = flow { error("not used") }
        override fun observeThreadDetail(tid: Int, page: Int, floorOrder: ForumFloorOrder, forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumThreadDetail>> = flow { error("not used") }
        override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData = error("not used")
        override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult = error("not used")
        override suspend fun loadThreadDetail(tid: Int, page: Int, floorOrder: ForumFloorOrder, forceRefresh: Boolean): ForumThreadDetail = error("not used")
        override fun destroySession() = Unit
    }

    private fun home(label: String) = ForumHomeData(
        banners = emptyList(),
        summary = ForumHomeSummary(),
        boardGroups = listOf(me.jbusdriver.modern.domain.model.ForumBoardGroup(label, emptyList()))
    )

    private fun cached(value: ForumHomeData, time: Long) = CachedLoadEvent.Cached(
        CacheEntry(value, time, CacheSource.Memory, isExpired = true)
    )

    private fun fresh(value: ForumHomeData, time: Long) = CachedLoadEvent.Fresh(
        CacheEntry(value, time, CacheSource.Network, isExpired = false)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `home applies cached then fresh data`() = runTest(dispatcher) {
        val repository = FakeForumRepository(flow {
            emit(cached(home("cached"), 1_000L))
            emit(fresh(home("fresh"), 2_000L))
        })

        val viewModel = ForumBoardsViewModel(repository)
        advanceUntilIdle()

        assertEquals("fresh", viewModel.uiState.value.groups.single().name)
        assertEquals(2_000L, viewModel.uiState.value.lastUpdatedAtMillis)
        assertFalse(viewModel.uiState.value.isRevalidating)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `home keeps cached data when background refresh fails`() = runTest(dispatcher) {
        val repository = FakeForumRepository(flow {
            emit(cached(home("cached"), 1_000L))
            emit(CachedLoadEvent.Failure(RuntimeException("offline"), hadCachedValue = true))
        })

        val viewModel = ForumBoardsViewModel(repository)
        advanceUntilIdle()

        assertEquals("cached", viewModel.uiState.value.groups.single().name)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRevalidating)
    }
}
```

- [ ] **Step 2: Run ViewModel tests and verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest
```

Expected: compilation fails because `ForumBoardsUiState.lastUpdatedAtMillis` and `isRevalidating` do not exist, and `ForumBoardsViewModel` still calls `loadForumBoards()`.

- [ ] **Step 3: Add home cache state fields**

Modify `ForumBoardsUiState` in `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt`:

```kotlin
data class ForumBoardsUiState(
    val banners: List<ForumBanner> = emptyList(),
    val summary: ForumHomeSummary = ForumHomeSummary(),
    val groups: List<ForumBoardGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val error: String? = null,
    val refreshMessage: String? = null
)
```

- [ ] **Step 4: Consume home cache events**

Add import:

```kotlin
import me.jbusdriver.modern.core.cache.CachedLoadEvent
```

Replace `ForumBoardsViewModel.loadBoards()` with:

```kotlin
fun loadBoards() {
    if (_uiState.value.isLoading || _uiState.value.isRevalidating) return
    viewModelScope.launch(Dispatchers.IO) {
        var hasContent = _uiState.value.groups.isNotEmpty()
        _uiState.update {
            it.copy(
                isLoading = !hasContent,
                isRevalidating = hasContent,
                error = null,
                refreshMessage = null
            )
        }
        repository.observeForumBoards(forceRefresh = false, revalidate = true).collect { event ->
            when (event) {
                is CachedLoadEvent.Cached -> {
                    hasContent = true
                    val data = event.entry.value
                    _uiState.update {
                        it.copy(
                            banners = data.banners,
                            summary = data.summary,
                            groups = data.boardGroups,
                            isLoading = false,
                            isRevalidating = true,
                            lastUpdatedAtMillis = event.entry.storedAtMillis
                        )
                    }
                }
                is CachedLoadEvent.Fresh -> {
                    val data = event.entry.value
                    _uiState.update {
                        it.copy(
                            banners = data.banners,
                            summary = data.summary,
                            groups = data.boardGroups,
                            isLoading = false,
                            isRevalidating = false,
                            lastUpdatedAtMillis = event.entry.storedAtMillis,
                            error = null
                        )
                    }
                }
                is CachedLoadEvent.Failure -> {
                    _uiState.update {
                        if (event.hadCachedValue || hasContent) {
                            it.copy(isLoading = false, isRevalidating = false)
                        } else {
                            it.copy(isLoading = false, isRevalidating = false, error = event.throwable.message ?: "載入失敗")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Update manual refresh to use flow**

Replace `ForumBoardsViewModel.refresh()` with:

```kotlin
fun refresh() {
    if (_uiState.value.isRefreshing) return
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
        repository.observeForumBoards(forceRefresh = true, revalidate = false).collect { event ->
            when (event) {
                is CachedLoadEvent.Cached -> Unit
                is CachedLoadEvent.Fresh -> {
                    val data = event.entry.value
                    _uiState.update {
                        it.copy(
                            banners = data.banners,
                            summary = data.summary,
                            groups = data.boardGroups,
                            isRefreshing = false,
                            lastUpdatedAtMillis = event.entry.storedAtMillis
                        )
                    }
                }
                is CachedLoadEvent.Failure -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = if (it.groups.isEmpty()) event.throwable.message ?: "載入失敗" else it.error,
                            refreshMessage = if (it.groups.isNotEmpty()) "刷新失敗" else null
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Run home ViewModel tests and commit**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest
```

Expected: PASS for the two home tests.

Commit:

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt app/src/test/java/me/jbusdriver/modern/ui/forum/ForumCacheRefreshViewModelTest.kt
git commit -m "feat: refresh forum home cache in background"
```

## Task 4: Update Thread List ViewModel and UI Prompt

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadListScreen.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumCacheRefreshViewModelTest.kt`

- [ ] **Step 1: Add failing thread list tests**

Append tests and helpers to `ForumCacheRefreshViewModelTest.kt`:

```kotlin
private class FakeThreadListRepository(
    val threadFlow: Flow<CachedLoadEvent<ForumThreadPageResult>>
) : ForumRepository {
    override fun observeForumBoards(forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumHomeData>> = flow { error("not used") }
    override fun observeThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long) = threadFlow
    override fun observeThreadDetail(tid: Int, page: Int, floorOrder: ForumFloorOrder, forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumThreadDetail>> = flow { error("not used") }
    override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData = error("not used")
    override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult = error("not used")
    override suspend fun loadThreadDetail(tid: Int, page: Int, floorOrder: ForumFloorOrder, forceRefresh: Boolean): ForumThreadDetail = error("not used")
    override fun destroySession() = Unit
}

private fun thread(id: Int, title: String) = me.jbusdriver.modern.domain.model.ForumThread(
    tid = id,
    typeId = 0,
    typeName = "",
    typeColor = "",
    title = title,
    author = "",
    authorUid = 0,
    authorAvatar = "",
    dateLine = "",
    viewCount = 0,
    replyCount = 0,
    lastReplyAuthor = "",
    lastReplyTime = "",
    images = emptyList(),
    isPinned = false,
    isDigest = false,
    pages = 1
)

private fun page(vararg threads: me.jbusdriver.modern.domain.model.ForumThread) =
    ForumThreadPageResult(threads.toList(), me.jbusdriver.modern.domain.model.PageInfo(1, 1, listOf(1)), emptyList())

@Test
fun `thread list stores fresh page when user is away from top`() = runTest(dispatcher) {
    val repository = FakeThreadListRepository(flow {
        emit(CachedLoadEvent.Cached(CacheEntry(page(thread(1, "cached")), 1_000L, CacheSource.Memory, true)))
        emit(CachedLoadEvent.Fresh(CacheEntry(page(thread(2, "fresh")), 2_000L, CacheSource.Network, false)))
    })

    val viewModel = ForumThreadListViewModel(repository, me.jbusdriver.modern.ui.RouteForumThreadList(7, "Board", null))
    viewModel.setAtTopForFreshUpdates(false)
    advanceUntilIdle()

    assertEquals("cached", viewModel.uiState.value.threads.single().title)
    assertEquals("fresh", viewModel.uiState.value.pendingFreshFirstPage?.threads?.single()?.title)
}

@Test
fun `thread list applies pending first page`() = runTest(dispatcher) {
    val repository = FakeThreadListRepository(flow {
        emit(CachedLoadEvent.Cached(CacheEntry(page(thread(1, "cached")), 1_000L, CacheSource.Memory, true)))
        emit(CachedLoadEvent.Fresh(CacheEntry(page(thread(2, "fresh")), 2_000L, CacheSource.Network, false)))
    })

    val viewModel = ForumThreadListViewModel(repository, me.jbusdriver.modern.ui.RouteForumThreadList(7, "Board", null))
    viewModel.setAtTopForFreshUpdates(false)
    advanceUntilIdle()

    viewModel.applyPendingFreshFirstPage()

    assertEquals("fresh", viewModel.uiState.value.threads.single().title)
    assertNull(viewModel.uiState.value.pendingFreshFirstPage)
}
```

- [ ] **Step 2: Run thread list tests and verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest
```

Expected: compilation fails because `pendingFreshFirstPage`, `setAtTopForFreshUpdates()`, and `applyPendingFreshFirstPage()` do not exist.

- [ ] **Step 3: Add thread list state fields**

Modify `ForumThreadListUiState`:

```kotlin
data class ForumThreadListUiState(
    val threads: List<ForumThread> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val currentTypeId: Int? = null,
    val typeFilters: List<ForumTypeFilter> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val pendingFreshFirstPage: ForumThreadPageResult? = null,
    val refreshMessage: String? = null,
    val error: String? = null,
    val hasMore: Boolean = true
)
```

- [ ] **Step 4: Add top-state and pending apply methods**

Inside `ForumThreadListViewModel`, add:

```kotlin
private var isAtTopForFreshUpdates: Boolean = true

fun setAtTopForFreshUpdates(isAtTop: Boolean) {
    isAtTopForFreshUpdates = isAtTop
}

fun applyPendingFreshFirstPage() {
    val pending = _uiState.value.pendingFreshFirstPage ?: return
    currentPage = 1
    _uiState.update {
        it.copy(
            threads = pending.threads,
            pageInfo = pending.pageInfo,
            typeFilters = pending.typeFilters.ifEmpty { it.typeFilters },
            hasMore = pending.pageInfo.hasNext,
            pendingFreshFirstPage = null,
            refreshMessage = null,
            lastUpdatedAtMillis = System.currentTimeMillis()
        )
    }
}
```

- [ ] **Step 5: Replace loadFirstPage flow handling**

Replace `loadFirstPage()` body with a flow collector:

```kotlin
fun loadFirstPage() {
    if (_uiState.value.isLoading || _uiState.value.isRevalidating) return
    currentPage = 1
    viewModelScope.launch(Dispatchers.IO) {
        var hasContent = _uiState.value.threads.isNotEmpty()
        _uiState.update {
            it.copy(
                isLoading = !hasContent,
                isRevalidating = hasContent,
                error = null,
                refreshMessage = null
            )
        }
        repository.observeThreads(fid, 1, _uiState.value.currentTypeId, forceRefresh = false, revalidate = true)
            .collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached -> {
                        hasContent = true
                        applyThreadFirstPage(event.entry.value, event.entry.storedAtMillis, markRevalidating = true)
                    }
                    is CachedLoadEvent.Fresh -> {
                        if (isAtTopForFreshUpdates) {
                            applyThreadFirstPage(event.entry.value, event.entry.storedAtMillis, markRevalidating = false)
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRevalidating = false,
                                    pendingFreshFirstPage = event.entry.value,
                                    refreshMessage = "有新帖子"
                                )
                            }
                        }
                    }
                    is CachedLoadEvent.Failure -> {
                        _uiState.update {
                            if (event.hadCachedValue || hasContent) {
                                it.copy(isLoading = false, isRevalidating = false)
                            } else {
                                it.copy(isLoading = false, isRevalidating = false, error = event.throwable.message ?: "載入失敗")
                            }
                        }
                    }
                }
            }
    }
}

private fun applyThreadFirstPage(
    result: ForumThreadPageResult,
    storedAtMillis: Long,
    markRevalidating: Boolean
) {
    currentPage = 1
    _uiState.update {
        it.copy(
            threads = result.threads,
            pageInfo = result.pageInfo,
            typeFilters = result.typeFilters.ifEmpty { it.typeFilters },
            isLoading = false,
            isRevalidating = markRevalidating,
            pendingFreshFirstPage = null,
            lastUpdatedAtMillis = storedAtMillis,
            hasMore = result.pageInfo.hasNext
        )
    }
}
```

- [ ] **Step 6: Update refresh, loadMore, and filter**

Change `refresh()` to force-refresh page 1 and clear later pages:

```kotlin
fun refresh() {
    if (_uiState.value.isRefreshing) return
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null, pendingFreshFirstPage = null) }
        repository.observeThreads(fid, 1, _uiState.value.currentTypeId, forceRefresh = true, revalidate = false)
            .collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached -> Unit
                    is CachedLoadEvent.Fresh -> {
                        currentPage = 1
                        val result = event.entry.value
                        _uiState.update {
                            it.copy(
                                threads = result.threads,
                                pageInfo = result.pageInfo,
                                typeFilters = result.typeFilters,
                                isRefreshing = false,
                                lastUpdatedAtMillis = event.entry.storedAtMillis,
                                hasMore = result.pageInfo.hasNext
                            )
                        }
                    }
                    is CachedLoadEvent.Failure -> {
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                error = if (it.threads.isEmpty()) event.throwable.message ?: "載入失敗" else it.error,
                                refreshMessage = if (it.threads.isNotEmpty()) "刷新失敗" else null
                            )
                        }
                    }
                }
            }
    }
}
```

Keep `loadMore()` using `repository.loadThreads(...)`. The old suspend wrapper now reads cache and does not background-revalidate, which is the right behavior for near-end pagination. On failure, preserve current behavior: reset `currentPage` and clear `isLoadingMore`.

In `filterByType(typeId)`, clear `pendingFreshFirstPage`:

```kotlin
_uiState.update {
    it.copy(
        currentTypeId = typeId,
        threads = emptyList(),
        pageInfo = PageInfo(),
        pendingFreshFirstPage = null,
        refreshMessage = null
    )
}
```

- [ ] **Step 7: Add UI prompt in ForumThreadListScreen**

In `ForumThreadListScreen.kt`, compute top state:

```kotlin
val isAtTop by remember {
    derivedStateOf {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 20
    }
}

LaunchedEffect(isAtTop) {
    viewModel.setAtTopForFreshUpdates(isAtTop)
}
```

Inside the `Box`, above `PullToRefreshBox` or aligned near the top, add:

```kotlin
if (state.pendingFreshFirstPage != null) {
    AssistChip(
        onClick = {
            viewModel.applyPendingFreshFirstPage()
            scope.launch { listState.animateScrollToItem(0) }
        },
        label = { Text(state.refreshMessage ?: "有新帖子") },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
    )
}
```

Add imports if missing:

```kotlin
import androidx.compose.material3.AssistChip
```

- [ ] **Step 8: Run thread list tests and commit**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest
```

Expected: PASS.

Commit:

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadListScreen.kt app/src/test/java/me/jbusdriver/modern/ui/forum/ForumCacheRefreshViewModelTest.kt
git commit -m "feat: prompt before replacing forum thread lists"
```

## Task 5: Update Thread Detail ViewModel and UI Prompt

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumCacheRefreshViewModelTest.kt`

- [ ] **Step 1: Add failing thread detail tests**

Append to `ForumCacheRefreshViewModelTest.kt`:

```kotlin
private class FakeDetailRepository(
    val detailFlow: Flow<CachedLoadEvent<ForumThreadDetail>>
) : ForumRepository {
    override fun observeForumBoards(forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumHomeData>> = flow { error("not used") }
    override fun observeThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumThreadPageResult>> = flow { error("not used") }
    override fun observeThreadDetail(tid: Int, page: Int, floorOrder: ForumFloorOrder, forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long) = detailFlow
    override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData = error("not used")
    override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult = error("not used")
    override suspend fun loadThreadDetail(tid: Int, page: Int, floorOrder: ForumFloorOrder, forceRefresh: Boolean): ForumThreadDetail = error("not used")
    override fun destroySession() = Unit
}

private fun detail(title: String) = ForumThreadDetail(
    tid = 1,
    typeId = 0,
    typeName = "",
    typeColor = "",
    title = title,
    viewCount = 0,
    replyCount = 0,
    author = "",
    authorUid = 0,
    authorAvatar = "",
    postTime = "",
    contentBlocks = emptyList(),
    comments = emptyList(),
    replies = emptyList(),
    pageInfo = me.jbusdriver.modern.domain.model.PageInfo(1, 1, listOf(1))
)

private class FakeForumSettingsReader : me.jbusdriver.modern.data.ForumSettingsReader {
    override val autoLoadGifs = kotlinx.coroutines.flow.MutableStateFlow(false)
    override suspend fun currentForumFloorOrder(): ForumFloorOrder = ForumFloorOrder.REGULAR
}

private class FakeLoadedGifTracker : me.jbusdriver.modern.data.LoadedGifTracker {
    private val urls = linkedSetOf<String>()
    override suspend fun loadedUrls(): Set<String> = urls
    override suspend fun markLoaded(url: String) { urls += url }
}

@Test
fun `detail stores fresh result when user is away from top`() = runTest(dispatcher) {
    val repository = FakeDetailRepository(flow {
        emit(CachedLoadEvent.Cached(CacheEntry(detail("cached"), 1_000L, CacheSource.Disk, true)))
        emit(CachedLoadEvent.Fresh(CacheEntry(detail("fresh"), 2_000L, CacheSource.Network, false)))
    })
    val viewModel = ForumThreadDetailViewModel(
        repository = repository,
        forumSettingsReader = FakeForumSettingsReader(),
        loadedGifTracker = FakeLoadedGifTracker(),
        navKey = me.jbusdriver.modern.ui.RouteForumThreadDetail(1)
    )

    viewModel.setAtTopForFreshUpdates(false)
    advanceUntilIdle()

    assertEquals("cached", viewModel.uiState.value.detail?.title)
    assertEquals("fresh", viewModel.uiState.value.pendingFreshDetail?.title)
}
```

- [ ] **Step 2: Run detail tests and verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest
```

Expected: compilation fails because `ForumSettingsReader`, `LoadedGifTracker`, `pendingFreshDetail`, and `setAtTopForFreshUpdates()` do not exist.

- [ ] **Step 3: Add small testable interfaces for detail dependencies**

Modify `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`.

Add this interface above `LabSettingsStore`:

```kotlin
interface ForumSettingsReader {
    val autoLoadGifs: StateFlow<Boolean>
    suspend fun currentForumFloorOrder(): ForumFloorOrder
}
```

Change the class declaration:

```kotlin
class LabSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) : ForumSettingsReader {
```

Change the existing members:

```kotlin
override val autoLoadGifs: StateFlow<Boolean> = dataStore.data.map { it[KEY_AUTO_LOAD_GIFS] ?: false }
    .stateIn(scope, SharingStarted.Eagerly, false)

override suspend fun currentForumFloorOrder(): ForumFloorOrder =
    ForumFloorOrder.fromPreferenceValue(dataStore.data.first()[KEY_FORUM_FLOOR_ORDER])
```

Modify `app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt`.

Add this interface above `GifLoadTracker`:

```kotlin
interface LoadedGifTracker {
    suspend fun loadedUrls(): Set<String>
    suspend fun markLoaded(url: String)
}
```

Change the class declaration:

```kotlin
class GifLoadTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : LoadedGifTracker {
```

Change the existing methods:

```kotlin
override suspend fun loadedUrls(): Set<String> {
    return dataStore.data.map { it[URLS] ?: emptySet() }.first()
}

override suspend fun markLoaded(url: String) {
    dataStore.edit { prefs ->
        val current = prefs[URLS] ?: emptySet()
        val updated = if (current.size >= MAX_CACHE) {
            current.toList().takeLast(MAX_CACHE - 1).toSet() + url
        } else {
            current + url
        }
        prefs[URLS] = updated
    }
}
```

Modify `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`.

Add imports:

```kotlin
import me.jbusdriver.modern.data.ForumSettingsReader
import me.jbusdriver.modern.data.GifLoadTracker
import me.jbusdriver.modern.data.LabSettingsStore
import me.jbusdriver.modern.data.LoadedGifTracker
```

Add bindings:

```kotlin
@Binds
@Singleton
abstract fun bindForumSettingsReader(
    impl: LabSettingsStore
): ForumSettingsReader

@Binds
@Singleton
abstract fun bindLoadedGifTracker(
    impl: GifLoadTracker
): LoadedGifTracker
```

- [ ] **Step 4: Change ForumThreadDetailViewModel constructor to interfaces**

Modify `ForumThreadDetailViewModel` constructor:

```kotlin
class ForumThreadDetailViewModel @AssistedInject constructor(
    private val repository: ForumRepository,
    private val forumSettingsReader: me.jbusdriver.modern.data.ForumSettingsReader,
    private val loadedGifTracker: me.jbusdriver.modern.data.LoadedGifTracker,
    @Assisted private val navKey: RouteForumThreadDetail
) : ViewModel() {
```

Change GIF and settings calls:

```kotlin
val autoLoadGifs: StateFlow<Boolean> = forumSettingsReader.autoLoadGifs

private suspend fun loadPersistedGifUrls(): Set<String> {
    return loadedGifTracker.loadedUrls()
}

private suspend fun persistGifUrls(urls: Set<String>) {
    for (url in urls) loadedGifTracker.markLoaded(url)
}
```

Change default floor order loading:

```kotlin
val defaultOrder = forumSettingsReader.currentForumFloorOrder()
```

- [ ] **Step 5: Add detail state fields**

Modify `ForumThreadDetailUiState`:

```kotlin
data class ForumThreadDetailUiState(
    val detail: ForumThreadDetail? = null,
    val floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val pendingFreshDetail: ForumThreadDetail? = null,
    val refreshMessage: String? = null,
    val error: String? = null,
    val isLoadingMore: Boolean = false,
    val isChangingFloorOrder: Boolean = false
)
```

- [ ] **Step 6: Add top-state and pending apply methods to detail ViewModel**

Inside `ForumThreadDetailViewModel`, add:

```kotlin
private var isAtTopForFreshUpdates: Boolean = true

fun setAtTopForFreshUpdates(isAtTop: Boolean) {
    isAtTopForFreshUpdates = isAtTop
}

fun applyPendingFreshDetail() {
    val pending = _uiState.value.pendingFreshDetail ?: return
    currentPage = 1
    _uiState.update {
        it.copy(
            detail = pending,
            pendingFreshDetail = null,
            refreshMessage = null,
            lastUpdatedAtMillis = System.currentTimeMillis()
        )
    }
}
```

- [ ] **Step 7: Replace loadDetail flow handling**

Change `loadDetail()` to collect `observeThreadDetail()`:

```kotlin
fun loadDetail(forceRefresh: Boolean = false, showLoading: Boolean = true) {
    if (showLoading && _uiState.value.isLoading) return
    val floorOrder = _uiState.value.floorOrder
    viewModelScope.launch(Dispatchers.IO) {
        var hasContent = _uiState.value.detail != null
        _uiState.update {
            when {
                showLoading && !hasContent -> it.copy(isLoading = true, error = null, refreshMessage = null)
                showLoading -> it.copy(isRevalidating = true, error = null, refreshMessage = null)
                else -> it.copy(error = null, isChangingFloorOrder = true, refreshMessage = null)
            }
        }
        repository.observeThreadDetail(tid, currentPage, floorOrder, forceRefresh = forceRefresh, revalidate = showLoading)
            .collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached -> {
                        hasContent = true
                        _uiState.update {
                            it.copy(
                                detail = event.entry.value,
                                isLoading = false,
                                isRevalidating = true,
                                isChangingFloorOrder = false,
                                lastUpdatedAtMillis = event.entry.storedAtMillis
                            )
                        }
                    }
                    is CachedLoadEvent.Fresh -> {
                        if (isAtTopForFreshUpdates || forceRefresh || !showLoading) {
                            _uiState.update {
                                it.copy(
                                    detail = event.entry.value,
                                    isLoading = false,
                                    isRevalidating = false,
                                    isChangingFloorOrder = false,
                                    pendingFreshDetail = null,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRevalidating = false,
                                    isChangingFloorOrder = false,
                                    pendingFreshDetail = event.entry.value,
                                    refreshMessage = "帖子已更新"
                                )
                            }
                        }
                    }
                    is CachedLoadEvent.Failure -> {
                        _uiState.update {
                            if (event.hadCachedValue || hasContent) {
                                it.copy(isLoading = false, isRevalidating = false, isChangingFloorOrder = false)
                            } else {
                                it.copy(isLoading = false, isRevalidating = false, isChangingFloorOrder = false, error = event.throwable.message ?: "載入失敗")
                            }
                        }
                    }
                }
            }
    }
}
```

- [ ] **Step 8: Update manual refresh and pagination reset**

Change `refresh()` so it resets to page 1:

```kotlin
fun refresh() {
    if (_uiState.value.isRefreshing) return
    val floorOrder = _uiState.value.floorOrder
    currentPage = 1
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null, pendingFreshDetail = null) }
        repository.observeThreadDetail(tid, 1, floorOrder, forceRefresh = true, revalidate = false)
            .collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached -> Unit
                    is CachedLoadEvent.Fresh -> {
                        _uiState.update {
                            it.copy(
                                detail = event.entry.value,
                                isRefreshing = false,
                                lastUpdatedAtMillis = event.entry.storedAtMillis
                            )
                        }
                    }
                    is CachedLoadEvent.Failure -> {
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                error = if (it.detail == null) event.throwable.message ?: "載入失敗" else it.error,
                                refreshMessage = if (it.detail != null) "刷新失敗" else null
                            )
                        }
                    }
                }
            }
    }
}
```

Keep `loadMoreReplies()` appending replies and leave load-more failures local.

- [ ] **Step 9: Add UI prompt in ForumThreadDetailScreen**

In `ForumThreadDetailScreen.kt`, compute top state:

```kotlin
val isAtTop by remember {
    derivedStateOf {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 20
    }
}

LaunchedEffect(isAtTop) {
    viewModel.setAtTopForFreshUpdates(isAtTop)
}
```

Inside the `Box`, add:

```kotlin
if (state.pendingFreshDetail != null) {
    AssistChip(
        onClick = {
            viewModel.applyPendingFreshDetail()
            scope.launch { listState.animateScrollToItem(0) }
        },
        label = { Text(state.refreshMessage ?: "帖子已更新") },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
    )
}
```

Add import:

```kotlin
import androidx.compose.material3.AssistChip
```

- [ ] **Step 10: Run detail tests and commit**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest
```

Expected: PASS.

Commit:

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt app/src/test/java/me/jbusdriver/modern/ui/forum/ForumCacheRefreshViewModelTest.kt
git commit -m "feat: prompt before replacing forum thread detail"
```

## Task 6: Wire Forum Home UI Feedback

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumBoardsScreen.kt`

- [ ] **Step 1: Add lightweight revalidation indicator**

In `ForumBoardsScreen.kt`, wrap existing content in `Box` if needed and add:

```kotlin
if (state.isRevalidating && state.groups.isNotEmpty()) {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
    )
}
```

Add import:

```kotlin
import androidx.compose.material3.LinearProgressIndicator
```

- [ ] **Step 2: Add manual refresh failure message hook**

Show manual refresh feedback as a small top chip:

```kotlin
if (state.refreshMessage != null) {
    AssistChip(
        onClick = { },
        label = { Text(state.refreshMessage) },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
    )
}
```

Add import:

```kotlin
import androidx.compose.material3.AssistChip
```

Add a ViewModel method to clear the message:

```kotlin
fun consumeRefreshMessage() {
    _uiState.update { it.copy(refreshMessage = null) }
}
```

Clear it from the screen with:

```kotlin
LaunchedEffect(state.refreshMessage) {
    if (state.refreshMessage != null) {
        kotlinx.coroutines.delay(2_000L)
        viewModel.consumeRefreshMessage()
    }
}
```

- [ ] **Step 3: Build compile check and commit**

Run:

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

Commit:

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumBoardsScreen.kt app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt
git commit -m "feat: show forum cache refresh feedback"
```

## Task 7: Final Regression and Cleanup

**Files:**
- Review: `app/src/main/java/me/jbusdriver/modern/core/cache/CacheStore.kt`
- Review: `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt`
- Review: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt`
- Review: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumBoardsScreen.kt`
- Review: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadListScreen.kt`
- Review: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.core.cache.CacheStoreMetadataTest --tests me.jbusdriver.modern.data.ForumRepositoryCacheFlowTest --tests me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run existing Forum tests**

Run:

```bash
./gradlew testDebugUnitTest --tests me.jbusdriver.modern.data.parser.ForumThreadParserTest --tests me.jbusdriver.modern.data.ForumThreadOrderTest --tests me.jbusdriver.modern.ui.forum.ForumThreadDetailStateTest --tests me.jbusdriver.modern.ui.forum.ForumPlainTextTest --tests me.jbusdriver.modern.ui.forum.ForumThreadTitleTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full unit test suite**

Run:

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Build debug APK**

Run:

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual behavior check**

Run the debug app and verify:

- Forum home opens with cached data and updates after background refresh.
- Background failure with cached data does not replace content with an error.
- Thread list shows the "有新帖子" prompt when fresh data arrives after scrolling away from the top.
- Tapping the prompt applies the fresh first page and scrolls to top.
- Thread detail shows the "帖子已更新" prompt when fresh detail arrives after scrolling away from the top.
- Manual pull-to-refresh failures keep visible cached content.
- Load-more still appends replies or threads without clearing the page on failure.

- [ ] **Step 6: Confirm working tree state**

Run:

```bash
git status --short
```

Expected: no output after all previous task commits are complete.
