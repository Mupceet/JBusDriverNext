# Search Local Collection Search — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real-time, type-as-you-go search over the user's collected movies to the search screen, shown above the online results, gated to the 有码/无码 chips and linked to the chip's censor type.

**Architecture:** A purely reactive, in-memory pipeline in `SearchViewModel`: observe collected movies as a `Flow` (`CollectRepository.observeCollectedLinkItems`), combine with a live input `StateFlow` (decoupled from the committed search query) and the current search type, then filter by a normalized substring match on code+title plus the chip's censor. The local section renders through `MovieList` itself — via a new `headerMovies` slot (mirroring `footerMovies`) in the movie-results case, and as a standalone `MovieList` otherwise. No DB schema change, no Gson/R8 change, online search flow untouched.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Kotlin Coroutines/Flow, Room, Hilt, JUnit4 + kotlinx-coroutines-test.

## Global Constraints

(spec-wide rules every task implicitly obeys)

- **Normalization rule (exact):** `query.lowercase().replace(Regex("[-_\\s]+"), "")`, then substring-contains on code and title. So `abc123` matches `ABC-123` / `ABC_0123`. A query that normalizes to empty (only separators/blank) matches nothing.
- **Censor rule (exact, reuse Collection page's):** `categoryId == 3` (`UncensoredMovieCategory`) ⟺ uncensored; `categoryId != 3` ⟺ censored. 有码 chip → censored only; 无码 chip → uncensored only; any other chip → no local results.
- **Chip gate:** local section is shown only when the selected chip is `SearchType.CENSORED` or `SearchType.UNCENSORED` AND the live input is non-blank AND there is ≥1 match.
- **Rendering:** local rows are emitted by `MovieList` (new `headerMovies` slot, or a standalone `MovieList`). Do **not** hand-compose `MovieItem` for the local section.
- **i18n:** all new user-facing strings go in `app/src/main/res/values/strings.xml` (Traditional Chinese) and `app/src/main/res/values-en/strings.xml` (English); counts use `<plurals>` with positional `%1$d`.
- **Scope fences:** do NOT modify the online search flow (`SearchRepository`, request-identity guards, paging), `t_link` schema/DAO SQL, any Room migration, or other screens. Removing the unused `SearchViewModel.setQuery()` is in scope (dead code).
- **Build gate:** run `./gradlew assembleDebug` before every commit. No Gson/R8 changes → no release smoke test required.
- **Dependency direction:** the `LinkItem → MovieUiModel` mapper lives in the UI layer (`ui/UiModels.kt`), because `MovieUiModel` is a UI model and the `data` layer must not depend on `ui`. (The `data.db` `toILink` it calls is the allowed `ui → data` direction.)

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/java/me/jbusdriver/modern/ui/search/LocalMovieSearch.kt` | **Create** | Pure `normalizeSearchText()` + `MovieUiModel.matchesLocal()` |
| `app/src/test/java/me/jbusdriver/modern/ui/search/LocalMovieSearchTest.kt` | **Create** | Unit tests for normalization + matching |
| `app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt` | **Modify** | Add `LinkItem.toMovieUiModel(baseUrl)` + `MovieUiModel.isUncensoredCollected` |
| `app/src/test/java/me/jbusdriver/modern/ui/MovieUiModelMapperTest.kt` | **Create** | Unit test for `toMovieUiModel` / `isUncensoredCollected` |
| `app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt` | **Modify** | Add `observeCollectedLinkItems(dbType): Flow<List<LinkItem>>` (interface default + impl) |
| `app/src/test/java/me/jbusdriver/modern/data/CollectRepositoryTest.kt` | **Modify** | Test `observeCollectedLinkItems` filters by dbType |
| `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt` | **Modify** | Inject `CollectRepository`+`SiteConfig`; `liveQuery`/`collectedMovies`/`localResults`; `onSearchInputChanged`; clear `liveQuery` in `clearSearch`; remove `setQuery` |
| `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt` | **Modify** | Update constructor calls; add local-results tests |
| `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt` | **Modify** | Add `headerMovies: List<MovieUiModel>` param (list + grid), mirror `footerMovies` |
| `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt` | **Modify** | Collect `localResults`; gate; `LocalCollectHeader`; `LaunchedEffect`; restructure results `when` |
| `app/src/main/res/values/strings.xml` | **Modify** | Add `local_collect`, `search_press_enter_hint`, plurals `local_collect_count` |
| `app/src/main/res/values-en/strings.xml` | **Modify** | English equivalents |

Task dependency order: **1 → 2 → 3 → 4 → 5 → 6**. Tasks 1–4 are pure-logic with unit tests (TDD). Tasks 5–6 are Compose UI verified by compile + manual check.

---

### Task 1: Normalization + matching helper

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/search/LocalMovieSearch.kt`
- Create: `app/src/test/java/me/jbusdriver/modern/ui/search/LocalMovieSearchTest.kt`

**Interfaces:**
- Produces: `internal fun normalizeSearchText(input: String): String` and `internal fun MovieUiModel.matchesLocal(query: String): Boolean` (both in package `me.jbusdriver.modern.ui.search`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/me/jbusdriver/modern/ui/search/LocalMovieSearchTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.search

import me.jbusdriver.modern.ui.MovieUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMovieSearchTest {

    private fun movie(code: String = "", title: String = "") =
        MovieUiModel(title = title, imageUrl = "", code = code, date = "", link = "")

    @Test
    fun `normalize lowercases and strips dash underscore and whitespace`() {
        assertEquals("abc123", normalizeSearchText("ABC-123"))
        assertEquals("abc0123", normalizeSearchText("ABC_0123"))
        assertEquals("abc123", normalizeSearchText("ABC 123"))
        assertEquals("abc", normalizeSearchText(" a B_c "))
        assertEquals("", normalizeSearchText("- _ -"))
        assertEquals("", normalizeSearchText(""))
    }

    @Test
    fun `matches code by normalized substring, case and separator insensitive`() {
        assertTrue(movie(code = "ABC-123").matchesLocal("abc123"))
        assertTrue(movie(code = "ABC-123").matchesLocal("ABC_123"))
        assertTrue(movie(code = "ABC_0123").matchesLocal("abc0123"))
        assertTrue(movie(code = "ABC-123").matchesLocal("abc"))
    }

    @Test
    fun `matches title by normalized substring`() {
        assertTrue(movie(title = "女教師的課外授業").matchesLocal("女教師"))
        assertTrue(movie(title = "My Great Title").matchesLocal("great title"))
    }

    @Test
    fun `does not match when neither code nor title contains query`() {
        assertFalse(movie(code = "ABC-123", title = "Hello").matchesLocal("xyz"))
    }

    @Test
    fun `separator-only or blank query never matches`() {
        val m = movie(code = "ABC-123", title = "Hello")
        assertFalse(m.matchesLocal("-"))
        assertFalse(m.matchesLocal("_"))
        assertFalse(m.matchesLocal(" - _ "))
        assertFalse(m.matchesLocal(""))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.search.LocalMovieSearchTest"`
Expected: COMPILATION FAILURE — `normalizeSearchText` / `matchesLocal` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/me/jbusdriver/modern/ui/search/LocalMovieSearch.kt`:

```kotlin
package me.jbusdriver.modern.ui.search

import me.jbusdriver.modern.ui.MovieUiModel

/**
 * 本地收藏搜索的归一化与匹配工具（纯函数，便于单测）。
 *
 * 归一化规则：转小写，去掉所有 `-`、`_`、空白字符；随后按"子串包含"匹配番号或标题。
 * 例：`abc123` 可命中 `ABC-123` / `ABC_0123`。
 */
internal fun normalizeSearchText(input: String): String =
    input.lowercase().replace(Regex("[-_\\s]+"), "")

/**
 * 判断该影片是否匹配本地搜索查询 [query]（对番号 code 与标题 title 做归一化子串匹配）。
 * 查询归一化后为空（仅由分隔符组成或为空）时返回 false。
 */
internal fun MovieUiModel.matchesLocal(query: String): Boolean {
    val q = normalizeSearchText(query)
    if (q.isEmpty()) return false
    return normalizeSearchText(code).contains(q) ||
        normalizeSearchText(title).contains(q)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.search.LocalMovieSearchTest"`
Expected: all 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/search/LocalMovieSearch.kt \
        app/src/test/java/me/jbusdriver/modern/ui/search/LocalMovieSearchTest.kt
git commit -m "feat(search): add normalized local-collection search matcher"
```

---

### Task 2: LinkItem → MovieUiModel mapper + censor helper

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt` (add imports + two extensions after the existing `Movie.toUiModel()` at line 74)
- Create: `app/src/test/java/me/jbusdriver/modern/ui/MovieUiModelMapperTest.kt`

**Interfaces:**
- Consumes: `LinkItem.toILink(baseUrl)` from `data.db.LinkMappers` (returns `ILink?`), `Movie.toUiModel()` (already in this file).
- Produces: `fun LinkItem.toMovieUiModel(baseUrl: String): MovieUiModel?` and `val MovieUiModel.isUncensoredCollected: Boolean` (package `me.jbusdriver.modern.ui`).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/me/jbusdriver/modern/ui/MovieUiModelMapperTest.kt`:

```kotlin
package me.jbusdriver.modern.ui

import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.domain.model.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieUiModelMapperTest {

    @Test
    fun `LinkItem to MovieUiModel restores urls with base url and keeps metadata`() {
        val movie = Movie(
            title = "Movie",
            imageUrl = "https://old.test/images/cover.jpg",
            code = "ABC-001",
            date = "2026-06-19",
            link = "https://old.test/movies/ABC-001"
        )
        val item = movie.convertDBItem(categoryId = 3).copy(createTime = 1234L)

        val ui = item.toMovieUiModel("https://mirror.test")

        assertEquals("ABC-001", ui?.code)
        assertEquals("Movie", ui?.title)
        assertEquals("https://mirror.test/movies/ABC-001", ui?.link)
        assertEquals("https://mirror.test/images/cover.jpg", ui?.imageUrl)
        assertEquals(1234L, ui?.createTime)
        assertEquals(3, ui?.categoryId)
        assertTrue(ui?.isUncensoredCollected == true)
    }

    @Test
    fun `toMovieUiModel returns null for invalid json`() {
        val item = Movie("M", "http://x", "ABC-1", "2024-01-01", "http://l")
            .convertDBItem().copy(jsonStr = "{")
        assertNull(item.toMovieUiModel("https://mirror.test"))
    }

    @Test
    fun `isUncensoredCollected is false for default censored category`() {
        val ui = Movie("M", "http://x", "ABC-1", "2024-01-01", "http://l")
            .convertDBItem(categoryId = 1).toMovieUiModel("https://x")
        assertFalse(ui?.isUncensoredCollected == true)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.MovieUiModelMapperTest"`
Expected: COMPILATION FAILURE — `toMovieUiModel` / `isUncensoredCollected` unresolved.

- [ ] **Step 3: Add the mapper + helper to `UiModels.kt`**

In `app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt`, add these imports at the top (with the existing `import me.jbusdriver.modern.domain.model.*` block):

```kotlin
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.domain.model.UncensoredMovieCategory
```

Then insert immediately after the existing `fun Movie.toUiModel() = ...` line (line 74), inside the `// region Domain → UI 转换扩展` region:

```kotlin
/** 该收藏影片是否为无码（categoryId == [UncensoredMovieCategory].id == 3）。与收藏页 filterByCensor 同一规则。 */
val MovieUiModel.isUncensoredCollected: Boolean
    get() = categoryId == (UncensoredMovieCategory.id ?: 3)

/**
 * 将收藏的 [LinkItem]（dbType=Movie）映射为可渲染的 [MovieUiModel]，
 * 保留 [LinkItem.createTime] 与 [LinkItem.categoryId]（供排序与审查类型判断）。
 * 反序列化失败（jsonStr 损坏）时返回 null。
 */
fun LinkItem.toMovieUiModel(baseUrl: String): MovieUiModel? =
    ((toILink(baseUrl) as? Movie)?.toUiModel())
        ?.copy(createTime = createTime, categoryId = categoryId)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.MovieUiModelMapperTest"`
Expected: all 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt \
        app/src/test/java/me/jbusdriver/modern/ui/MovieUiModelMapperTest.kt
git commit -m "feat(ui): add LinkItem.toMovieUiModel mapper and censor helper"
```

---

### Task 3: CollectRepository.observeCollectedLinkItems

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/data/CollectRepositoryTest.kt`

**Interfaces:**
- Consumes: `LinkItemDao.listAll(): Flow<List<LinkItem>>` (already exists).
- Produces: `fun observeCollectedLinkItems(dbType: Int): Flow<List<LinkItem>>` on `CollectRepository`. The interface gets a **default** returning `flowOf(emptyList())` (mirrors the existing `updateMovieCategory` default-for-test-stubs pattern at lines 51–54), so existing fakes (`StubCollectRepository`, `CollectRepositoryTest.FakeCollectRepository`) keep compiling unchanged. `DefaultCollectRepository` overrides it.

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/me/jbusdriver/modern/data/CollectRepositoryTest.kt`, add the import:

```kotlin
import kotlinx.coroutines.flow.first
```

Add this test method inside `class CollectRepositoryTest` (it reuses the file's existing `SimpleLinkItemDao`, `defaultRepository(...)`, and `PassthroughTransactionRunner()`):

```kotlin
    @Test
    fun defaultRepository_observeCollectedLinkItems_filtersByDbType() = runTest {
        val dao = SimpleLinkItemDao()
        val repository = defaultRepository(dao, PassthroughTransactionRunner())
        dao.items += Movie("M1", "http://x", "ABC-001", "2024-01-01", "http://l1").convertDBItem()
        dao.items += ActressInfo("Alice", "http://a", "http://l2").convertDBItem()

        val movies = repository.observeCollectedLinkItems(MovieDBType).first()

        assertEquals(1, movies.size)
        assertEquals(MovieDBType, movies.single().dbType)
    }
```

(`Movie`, `ActressInfo`, `convertDBItem`, `MovieDBType`, `runTest`, `assertEquals` are already imported in this test file.) The two assertions suffice: from a movie + an actress inserted, only the movie (`dbType == MovieDBType`) is emitted.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.CollectRepositoryTest.defaultRepository_observeCollectedLinkItems_filtersByDbType"`
Expected: COMPILATION FAILURE — `observeCollectedLinkItems` unresolved.

- [ ] **Step 3: Add the method to the interface and the default implementation**

In `app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt`, add imports at the top:

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
```

Add to the `interface CollectRepository` body, right after the existing `getCollectedLinkItems(dbType)` declaration (around line 75):

```kotlin
    /**
     * 以 Flow 观察指定类型的收藏数据（实时反映收藏增删 / 改分类）。
     *
     * 默认实现返回空 Flow，仅用于让测试桩/伪实现免于逐一实现（与 [updateMovieCategory] 同理）；
     * 生产实现见 [DefaultCollectRepository.observeCollectedLinkItems]。
     */
    fun observeCollectedLinkItems(dbType: Int): Flow<List<LinkItem>> = flowOf(emptyList())
```

Add the override inside `class DefaultCollectRepository`, right after `getCollectedLinkItems` (around line 182):

```kotlin
    override fun observeCollectedLinkItems(dbType: Int): Flow<List<LinkItem>> =
        linkDao.listAll().map { items -> items.filter { it.dbType == dbType } }
```

(No `flowOn(IO)` — Room `listAll()` already executes the query off-main; the `map` filter is trivial and keeping it on the collector dispatcher makes the test deterministic under `runTest`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.CollectRepositoryTest"`
Expected: all tests PASS (including the new one).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt \
        app/src/test/java/me/jbusdriver/modern/data/CollectRepositoryTest.kt
git commit -m "feat(collect): add observeCollectedLinkItems Flow to CollectRepository"
```

---

### Task 4: SearchViewModel local-results pipeline

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `CollectRepository.observeCollectedLinkItems(dbType)` (Task 3), `LinkItem.toMovieUiModel(baseUrl)` + `MovieUiModel.isUncensoredCollected` (Task 2), `MovieUiModel.matchesLocal(query)` (Task 1), `SiteConfig.baseUrl`.
- Produces: `val localResults: StateFlow<List<MovieUiModel>>` and `fun onSearchInputChanged(text: String)` on `SearchViewModel`. Also: constructor now takes `collectRepository: CollectRepository, siteConfig: SiteConfig`; `clearSearch()` also clears the live query; `setQuery(...)` is removed.

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt`:

Add imports (`flowOf`, `Movie`, `SearchType`, `PageInfo`, `ActressInfo`, `MoviePageResult`, `runTest`, `advanceUntilIdle`, `runCurrent` are already imported in this file — skip those):

```kotlin
import kotlinx.coroutines.launch
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.test.StubCollectRepository
import me.jbusdriver.modern.ui.MovieUiModel
```

Add these helpers inside `class SearchViewModelTest` (near the existing `stubLocalVideoRepo` / `fakeHistoryStore`):

```kotlin
    private fun fakeSiteConfig() = object : SiteConfig {
        override var baseUrl: String = "https://example.test"
        override fun resolve(pathOrUrl: String): String = pathOrUrl
    }

    private fun stubSearchRepository() = object : SearchRepository {
        override suspend fun searchMovies(
            type: SearchType, query: String, page: Int, forceRefresh: Boolean
        ) = MoviePageResult(PageInfo(), emptyList())

        override suspend fun searchActresses(
            query: String, page: Int
        ): Pair<PageInfo, List<ActressInfo>> = PageInfo() to emptyList()
    }

    private fun makeViewModel(
        repository: SearchRepository,
        collectRepository: CollectRepository = StubCollectRepository()
    ) = SearchViewModel(
        repository, fakeHistoryStore(), stubLocalVideoRepo, collectRepository, fakeSiteConfig()
    )
```

Replace every existing construction `SearchViewModel(repository, fakeHistoryStore(), stubLocalVideoRepo)` (7 occurrences, in `search_loadsResults`, `search_handlesError`, `search_emptyQuery_doesNotLoad`, `actressSearch_loadsActressResultsAndClearsMovieResults`, `loadMore_appendsNextPageAndThenStopsWhenNoMore`, `loadMoreErrorKeepsExistingResultsAndReportsError`, `staleRefreshResultDoesNotOverwriteNewSearch`) with `makeViewModel(repository)`.

Add the new local-results tests:

```kotlin
    @Test
    fun localResults_matchByCodeAndFilterByCensorChip() = runTest(testDispatcher) {
        val censored = Movie("Cen", "http://c.jpg", "ABC-001", "2024-01-01", "http://lc")
            .convertDBItem(categoryId = 1)
        val uncensored = Movie("Un", "http://u.jpg", "ABC-002", "2024-01-02", "http://lu")
            .convertDBItem(categoryId = 3)
        val collectRepo = object : StubCollectRepository() {
            override fun observeCollectedLinkItems(dbType: Int) =
                flowOf(listOf(censored, uncensored))
        }
        val viewModel = makeViewModel(stubSearchRepository(), collectRepo)

        val collected = mutableListOf<List<MovieUiModel>>()
        val job = launch { viewModel.localResults.collect { collected += it } }
        runCurrent()

        viewModel.onSearchInputChanged("abc")
        advanceUntilIdle()
        // default chip = CENSORED -> only the censored one
        assertEquals(listOf("ABC-001"), collected.last().map { it.code })

        viewModel.setSearchType(SearchType.UNCENSORED)
        advanceUntilIdle()
        assertEquals(listOf("ABC-002"), collected.last().map { it.code })

        viewModel.setSearchType(SearchType.ACTRESS)
        advanceUntilIdle()
        assertTrue(collected.last().isEmpty())

        job.cancel()
    }

    @Test
    fun localResults_normalizeQueryMatchTitleAndSortByCreateTimeDesc() = runTest(testDispatcher) {
        val older = Movie("Old Title", "http://o.jpg", "ABC-001", "2024-01-01", "http://lo")
            .convertDBItem(categoryId = 1).copy(createTime = 1000L)
        val newer = Movie("New Title", "http://n.jpg", "ABC-002", "2024-01-02", "http://ln")
            .convertDBItem(categoryId = 1).copy(createTime = 2000L)
        val collectRepo = object : StubCollectRepository() {
            override fun observeCollectedLinkItems(dbType: Int) = flowOf(listOf(older, newer))
        }
        val viewModel = makeViewModel(stubSearchRepository(), collectRepo)

        val collected = mutableListOf<List<MovieUiModel>>()
        val job = launch { viewModel.localResults.collect { collected += it } }
        runCurrent()

        // separator-insensitive code match
        viewModel.onSearchInputChanged("ABC_002")
        advanceUntilIdle()
        assertEquals(listOf("ABC-002"), collected.last().map { it.code })

        // title substring matches both; sorted newest-collected first
        viewModel.onSearchInputChanged("title")
        advanceUntilIdle()
        assertEquals(listOf("ABC-002", "ABC-001"), collected.last().map { it.code })

        job.cancel()
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.search.SearchViewModelTest"`
Expected: COMPILATION FAILURE — `SearchViewModel` constructor mismatch (missing `collectRepository`, `siteConfig`) and/or `localResults`/`onSearchInputChanged` unresolved.

- [ ] **Step 3: Update `SearchViewModel`**

In `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`:

Add imports:

```kotlin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.ui.isUncensoredCollected
import me.jbusdriver.modern.ui.toMovieUiModel
```

Change the constructor to add the two dependencies:

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val historyStore: SearchHistoryStore,
    private val localVideoRepository: LocalVideoRepository,
    private val collectRepository: CollectRepository,
    private val siteConfig: SiteConfig,
) : ViewModel() {
```

Add the local-search pipeline immediately after the existing `downloadedCodes` declaration (after line 86):

```kotlin
    /** 用户实时输入（与已提交的 [SearchUiState.query] 解耦，避免干扰在线搜索状态机） */
    private val liveQuery = MutableStateFlow("")

    /** 全部收藏影片（MovieUiModel），随收藏库变化实时更新 */
    private val collectedMovies: StateFlow<List<MovieUiModel>> =
        collectRepository.observeCollectedLinkItems(MovieDBType)
            .map { items -> items.mapNotNull { it.toMovieUiModel(siteConfig.baseUrl) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 本地收藏搜索结果：仅在有码/无码 chip 下，按归一化子串匹配番号+标题，
     * 并按 chip 审查类型过滤（有码=categoryId!=3，无码=categoryId==3），按收藏时间倒序。
     * 其它 chip（演员/导演等）下为空。
     */
    val localResults: StateFlow<List<MovieUiModel>> =
        combine(collectedMovies, liveQuery, uiState.map { it.searchType }) { items, query, type ->
            val wantUncensored = when (type) {
                SearchType.UNCENSORED -> true
                SearchType.CENSORED -> false
                else -> return@combine emptyList()
            }
            items
                .filter { it.matchesLocal(query) && it.isUncensoredCollected == wantUncensored }
                .sortedByDescending { it.createTime }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 用户输入变化时驱动本地实时搜索（不触发联网） */
    fun onSearchInputChanged(text: String) {
        liveQuery.value = text
    }
```

In `clearSearch()`, add `liveQuery.value = ""` (clears the local section when the user taps ✕). The function becomes:

```kotlin
    fun clearSearch() {
        searchJob?.cancel()
        requestGeneration += 1
        activeIdentity = null
        liveQuery.value = "" // 清空实时本地搜索
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                actressResults = emptyList(),
                error = null,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false
            )
        }
    }
```

Delete the unused `setQuery(...)` function (current lines 327–336):

```kotlin
    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.search.SearchViewModelTest"`
Expected: all tests PASS (existing 7 + 2 new). If a "setQuery unresolved" compile error appears elsewhere, search the repo for `setQuery(` callers — there should be none in production; if found, leave it and flag it.

- [ ] **Step 5: Build + commit**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt \
        app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt
git commit -m "feat(search): add real-time local-collection results to SearchViewModel"
```

---

### Task 5: MovieList.headerMovies slot

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`

**Interfaces:**
- Produces: a new `headerMovies: List<MovieUiModel> = emptyList()` parameter on `MovieList`, emitted as real lazy items right after the existing `header` slot and before the main `movies` — in both list and grid modes — mirroring the existing `footerMovies` (applies `isDownloaded`, not `longPressMenu`). Default empty → zero impact on existing callers (Collection, LinkMovieList, etc.).

No unit test (Compose UI). Verified by compile (`assembleDebug`) here, and by the integrated screen in Task 6.

- [ ] **Step 1: Add the parameter**

In `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`, add the parameter to the `MovieList(...)` signature, immediately above the existing `footerMovies` param (line 53):

```kotlin
    headerMovies: List<MovieUiModel> = emptyList(),
```

- [ ] **Step 2: Emit headerMovies in grid mode**

In the **grid** branch (`if (useGrid) { ... }`), immediately after the existing `header` block (after line 88, before `itemsIndexed(movies, ...)` at line 89), insert:

```kotlin
                if (headerMovies.isNotEmpty()) {
                    itemsIndexed(
                        headerMovies,
                        key = { index, movie -> "header_${index}_${movie.link}" }
                    ) { _, movie ->
                        MovieGridItem(
                            movie = movie,
                            onClick = { onMovieClick(movie, null) },
                            isDownloaded = isDownloaded?.invoke(movie) == true
                        )
                    }
                }
```

- [ ] **Step 3: Emit headerMovies in list mode**

In the **list** branch (`else { ... }`), immediately after the existing `header` block (after line 173, before `itemsIndexed(movies, ...)` at line 174), insert:

```kotlin
                if (headerMovies.isNotEmpty()) {
                    itemsIndexed(
                        headerMovies,
                        key = { index, movie -> "header_${index}_${movie.link}" }
                    ) { _, movie ->
                        MovieItem(
                            movie = movie,
                            onClick = { onMovieClick(movie, null) },
                            isDownloaded = isDownloaded?.invoke(movie) == true
                        )
                    }
                }
```

(`itemsIndexed` resolves to the grid variant in grid scope and the list variant in list scope; both are already imported at the top of this file — lines 15 and 17.)

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (No behavior change yet — `headerMovies` defaults to empty for all existing callers.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt
git commit -m "feat(ui): add headerMovies slot to MovieList (mirror of footerMovies)"
```

---

### Task 6: SearchScreen wiring + strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`

**Interfaces:**
- Consumes: `viewModel.localResults` + `viewModel.onSearchInputChanged` (Task 4), `MovieList.headerMovies` (Task 5).

- [ ] **Step 1: Add string resources**

In `app/src/main/res/values/strings.xml`, in the `<!-- Search -->` block that contains `<string name="no_results">...` (around line 69), add:

```xml
    <string name="search_press_enter_hint">按回車聯網搜尋</string>
    <string name="local_collect">本地收藏</string>
    <plurals name="local_collect_count">
        <item quantity="other">%1$d 部</item>
    </plurals>
```

In `app/src/main/res/values-en/strings.xml`, in the matching `<!-- Search -->` block (around line 69), add:

```xml
    <string name="search_press_enter_hint">Press enter to search online</string>
    <string name="local_collect">Local collection</string>
    <plurals name="local_collect_count">
        <item quantity="one">%1$d movie</item>
        <item quantity="other">%1$d movies</item>
    </plurals>
```

- [ ] **Step 2: Add the `pluralStringResource` import**

In `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`, add to the imports:

```kotlin
import androidx.compose.ui.res.pluralStringResource
```

- [ ] **Step 3: Collect `localResults`, compute visibility, drive live query**

In `SearchScreen(...)`, immediately after `val downloadedCodes by viewModel.downloadedCodes.collectAsStateWithLifecycle()` (line 77), add:

```kotlin
    val localResults by viewModel.localResults.collectAsStateWithLifecycle()
```

Immediately after `val isGrid = uiPrefsState.isGrid` (line 84), add:

```kotlin
    val isMovieChip = uiState.searchType == SearchType.CENSORED ||
        uiState.searchType == SearchType.UNCENSORED
    val localVisible = isMovieChip &&
        searchInput.trim().isNotBlank() &&
        localResults.isNotEmpty()
    val localHeader: (@Composable () -> Unit)? =
        if (localVisible) { { LocalCollectHeader(localResults.size) } } else null
```

Immediately after the existing `LaunchedEffect(uiState.query) { ... }` block (after line 104), add:

```kotlin
    LaunchedEffect(searchInput) {
        viewModel.onSearchInputChanged(searchInput)
    }
```

- [ ] **Step 4: Add the `LocalCollectHeader` composable**

At the end of the file (after the closing `}` of `SearchScreen`), add:

```kotlin
@Composable
private fun LocalCollectHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.local_collect),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            pluralStringResource(R.plurals.local_collect_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 5: Restructure the results `when` block**

Replace the entire results block — from `// Results` (line 204) through the closing of the `when` block (line 339) — with the following. (It keeps every original branch identical, and adds two new top branches + `header`/`headerMovies` to the movie-list branch.)

```kotlin
        // Results
        val isActress = uiState.searchType == SearchType.ACTRESS
        val hasResults =
            if (isActress) uiState.actressResults.isNotEmpty() else uiState.results.isNotEmpty()
        val onlineIsMovieList = !uiState.isLoading && uiState.error == null &&
            !isActress && hasResults

        when {
            // 主场景：联网影片结果就绪 → 单条滚动，本地命中通过 headerMovies 置顶
            onlineIsMovieList -> MovieList(
                movies = uiState.results,
                header = localHeader,
                headerMovies = if (localVisible) localResults else emptyList(),
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = { viewModel.loadMore() },
                onMovieClick = { movie, _ -> onMovieClick(movie, censorType) },
                isGrid = isGrid,
                isDownloaded = { it.code.uppercase() in downloadedCodes },
                modifier = dismissKeyboardModifier
            )

            // 影片 chip 但还没有联网影片结果（输入中/加载/无结果/错误）：
            // 本地独立 MovieList 占主体（自带滚动，任意命中数不溢出），下方小块状态
            localVisible -> Column(modifier = Modifier.fillMaxSize()) {
                MovieList(
                    movies = localResults,
                    header = { LocalCollectHeader(localResults.size) },
                    hasMore = false,
                    onMovieClick = { movie, _ -> onMovieClick(movie, censorType) },
                    isGrid = isGrid,
                    isDownloaded = { it.code.uppercase() in downloadedCodes },
                    modifier = Modifier
                        .weight(1f)
                        .then(dismissKeyboardModifier)
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        uiState.isLoading -> CircularProgressIndicator()
                        uiState.error != null -> Text(
                            stringResource(uiState.error ?: R.string.search_failed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Text(
                            stringResource(R.string.search_press_enter_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && !hasResults -> {
                ErrorView(
                    message = stringResource(uiState.error ?: R.string.search_failed),
                    onRetry = { doSearch() }
                )
            }

            !hasResults && uiState.query.isBlank() -> {
                if (searchHistory.isNotEmpty()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.search_history),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isDeletingHistory) {
                                Row {
                                    TextButton(onClick = {
                                        viewModel.clearHistory()
                                        isDeletingHistory = false
                                    }) {
                                        Text(stringResource(R.string.delete_all), fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { isDeletingHistory = false }) {
                                        Text(stringResource(R.string.done), fontSize = 12.sp)
                                    }
                                }
                            } else {
                                IconButton(onClick = { isDeletingHistory = true }) {
                                    Icon(
                                        painterResource(R.drawable.delete_24px),
                                        contentDescription = stringResource(R.string.delete),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            searchHistory.forEach { query ->
                                if (isDeletingHistory) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(query, fontSize = 12.sp)
                                                Icon(
                                                    painterResource(R.drawable.close_24px),
                                                    contentDescription = stringResource(R.string.delete),
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable {
                                                            viewModel.removeHistoryItem(
                                                                query
                                                            )
                                                        },
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = {
                                            searchInput = query
                                            focusManager.clearFocus()
                                            viewModel.search(query, uiState.searchType)
                                        },
                                        label = { Text(query, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            !hasResults -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            isActress -> ActressGrid(
                actresses = uiState.actressResults,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = { viewModel.loadMore() },
                onActressClick = { actress, _ -> onActressClick(actress, censorType) },
                modifier = dismissKeyboardModifier
            )

            // 兜底：联网影片结果（刷新中/带错误但仍有结果时），同样支持 headerMovies
            else -> MovieList(
                movies = uiState.results,
                header = localHeader,
                headerMovies = if (localVisible) localResults else emptyList(),
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = { viewModel.loadMore() },
                onMovieClick = { movie, _ -> onMovieClick(movie, censorType) },
                isGrid = isGrid,
                isDownloaded = { it.code.uppercase() in downloadedCodes },
                modifier = dismissKeyboardModifier
            )
        }
    }
}
```

- [ ] **Step 6: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Manual verification (run the app)**

Launch on the emulator and confirm:

1. With ≥1 collected movie, go to Search, ensure the 有码 chip is selected, type a code fragment (e.g. `abc`) — collected movies whose code/title match appear in a `本地收藏 · N 部` section at the top, instantly (no Enter needed).
2. Type a separator-variant (`abc-123`, `abc_123`) — same movies match.
3. Switch to 无码 chip — the local section now shows only uncensored collected matches.
4. Switch to 女優 chip — the local section disappears entirely.
5. Tap Enter to run the online search — online results appear below the local section in the same scroll (local stays pinned at top via `headerMovies`).
6. Tap a local result — navigates to the movie detail screen with the correct censor type.
7. Tap ✕ to clear the input — the local section disappears.
8. Local rows show the "已下載" badge when the code has an associated local video.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt
git commit -m "feat(search): show real-time local collection results above online results"
```

---

## Final verification

After Task 6:

- [ ] Run the full unit-test suite: `./gradlew test` — all green.
- [ ] Run `./gradlew assembleDebug` — BUILD SUCCESSFUL.
- [ ] (Optional, if ProGuard/forum touched in future) — no Gson/R8 changes in this work, so no release smoke test required per `AGENTS.md`.

## Notes for the implementer

- **Why `setQuery` is removed:** it was never called from the UI (verified during design). Real-time input now flows through the separate `liveQuery` via `onSearchInputChanged`, deliberately decoupled from `uiState.query` so typing does not disturb the online-search state machine.
- **Why the mapper is in `ui/`, not `data/db`:** `MovieUiModel` is a UI-layer type; `data` must not depend on `ui`. The mapper calls `data.db.toILink` (allowed `ui → data` direction).
- **Why `observeCollectedLinkItems` has an interface default:** avoids editing every test fake/stub (`StubCollectRepository`, `CollectRepositoryTest.FakeCollectRepository`); mirrors the existing `updateMovieCategory` default-for-stubs pattern.
- **Censor consistency:** the local filter uses `isUncensoredCollected` (`categoryId == 3`), the same rule as `CollectionListViewModel.filterByCensor`, so 有码/无码 mean the same thing on Search and Collection. (Migrating `CollectionListViewModel` to also use `isUncensoredCollected` is optional and intentionally out of scope.)
