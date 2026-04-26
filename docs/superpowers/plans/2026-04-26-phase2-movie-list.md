# Phase 2: Movie List Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the home movie list (paginated, Jsoup-parsed, cached) to modern architecture as a Compose screen with ViewModel + Repository pattern.

**Architecture:** New code in `me.jbusdriver.modern/`. MovieRepository fetches HTML via Retrofit, parses with Jsoup, extracts pagination info. MovieListViewModel manages pagination state (page tracking, refresh, load-more, errors). MovieListScreen renders with LazyColumn + pull-to-refresh. Wraps existing `JAVBusService` and `CacheLoader`.

**Tech Stack:** Hilt, Compose + Material 3, ViewModel + StateFlow, Kotlin Coroutines/Flow, Jsoup, Retrofit (existing), Coil for images.

**Design Spec:** `docs/superpowers/specs/2026-04-26-architecture-migration-design.md` (Phase 2)

---

## How the Existing Movie List Works

1. **URL construction:** `${baseUrl}${type.prefix}${page}` where `type.prefix` is `/page/` for CENSORED, empty for others. Page 1 has no suffix.
2. **Network:** `JAVBusService.INSTANCE.get(url, existmag)` returns `Flowable<String>` (raw HTML).
3. **Parsing:** `Jsoup.parse(html)` → `Document`, then `loadMovieFromDoc(doc)` selects `.movie-box` elements and extracts title, imageUrl, code, date, link, tags.
4. **Pagination:** `parsePage(doc)` selects `.pagination .active > a` to get current/next page numbers.
5. **Caching:** First page cached in `CacheLoader.lru` + optional disk cache.
6. **State:** `PageInfo(activePage, nextPage, referPages)` tracks position. `hasNext` = `activePage < nextPage`.

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `app/src/main/java/me/jbusdriver/modern/data/MovieRepository.kt` | Interface + DefaultMovieRepository |
| Create | `app/src/main/java/me/jbusdriver/modern/data/model/MoviePageResult.kt` | Page result data class |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt` | @HiltViewModel with pagination state |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt` | Compose UI with LazyColumn |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieItem.kt` | Individual movie composable |
| Modify | `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt` | Add MovieRepository binding |
| Modify | `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt` | Add movie list route |
| Modify | `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt` | Add movie list composable |
| Create | `app/src/test/java/me/jbusdriver/modern/data/DefaultMovieRepositoryTest.kt` | Repository tests |
| Create | `app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt` | ViewModel tests |

---

### Task 1: Add Coil Image Loading Dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add Coil version to version catalog**

Add to `gradle/libs.versions.toml` under `[versions]`:
```toml
coil = "2.7.0"
```

Add to `gradle/libs.versions.toml` under `[libraries]`:
```toml
# Image Loading (Compose-native)
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
```

- [ ] **Step 2: Add Coil dependency to app/build.gradle.kts**

Add in the `dependencies {}` block after the Compose dependencies:
```kotlin
// Image Loading
implementation(libs.coil.compose)
```

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Coil for Compose image loading"
```

---

### Task 2: Create MoviePageResult Data Model

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/model/MoviePageResult.kt`

- [ ] **Step 1: Create the data model**

Create file `app/src/main/java/me/jbusdriver/modern/data/model/MoviePageResult.kt`:

```kotlin
package me.jbusdriver.modern.data.model

import me.jbusdriver.mvp.bean.Movie

data class PageInfo(
    val activePage: Int = 0,
    val nextPage: Int = 0,
    val referPages: List<Int> = emptyList()
)

val PageInfo.hasNext: Boolean
    inline get() = activePage < nextPage

data class MoviePageResult(
    val pageInfo: PageInfo,
    val movies: List<Movie>
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/model/MoviePageResult.kt
git commit -m "feat(migration): add MoviePageResult and PageInfo data classes"
```

---

### Task 3: Create MovieRepository

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/MovieRepository.kt`

- [ ] **Step 1: Create repository interface and default implementation**

Create file `app/src/main/java/me/jbusdriver/modern/data/MovieRepository.kt`:

```kotlin
package me.jbusdriver.modern.data

import androidx.collection.ArrayMap
import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
import me.jbusdriver.base.common.C
import me.jbusdriver.base.fromJson
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.loadMovieFromDoc
import me.jbusdriver.ui.data.enums.DataSourceType
import org.jsoup.Jsoup
import retrofit2.HttpException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

interface MovieRepository {
    suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean = false): MoviePageResult
}

@Singleton
class DefaultMovieRepository @Inject constructor() : MovieRepository {

    private val urls: ArrayMap<String, String>? by lazy {
        CacheLoader.acache.getAsString(C.Cache.BUS_URLS)?.let {
            GSON.fromJson<ArrayMap<String, String>>(it)
        }
    }

    override suspend fun loadPage(
        type: DataSourceType,
        page: Int,
        showAll: Boolean
    ): MoviePageResult {
        val baseUrl = urls?.get(type.key) ?: JAVBusService.defaultFastUrl
        val url = if (page == 1) baseUrl else "$baseUrl${type.prefix}$page"

        val service = JAVBusService.getInstance(baseUrl)
        val html = suspendCancellableCoroutine<String> { cont ->
            val disposable = service.get(url, if (showAll) "all" else "")
                .subscribe({ html ->
                    if (html.isNotBlank()) {
                        if (page == 1) CacheLoader.lru.put("${type.key}$showAll", html)
                        cont.resumeWith(Result.success(html))
                    } else {
                        cont.resumeWith(Result.failure(IllegalStateException("Empty response")))
                    }
                }, { error ->
                    cont.resumeWith(Result.failure(error))
                })
            cont.invokeOnCancellation { disposable.dispose() }
        }

        val doc = Jsoup.parse(html)

        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
        val movies = loadMovieFromDoc(doc)

        return MoviePageResult(pageInfo, movies)
    }

    private fun parsePageInfo(doc: org.jsoup.nodes.Document): PageInfo? {
        val current = doc.select(".pagination .active > a").attr("href")
        if (current.isNullOrEmpty()) return null

        val next = doc.select(".pagination .active ~ li > a").let {
            if (it.isEmpty()) current else it.attr("href")
        }
        val pages = doc.select(".pagination a:not([id])")
            .mapNotNull { it.attr("href").split("/").lastOrNull()?.toIntOrNull() }

        return PageInfo(
            activePage = current.split("/").lastOrNull()?.toIntOrNull() ?: 0,
            nextPage = next.split("/").lastOrNull()?.toIntOrNull() ?: 0,
            referPages = pages
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/MovieRepository.kt
git commit -m "feat(migration): add MovieRepository with Jsoup parsing and pagination"
```

---

### Task 4: Update DataModule with MovieRepository Binding

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: Add MovieRepository binding**

Update `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`:

```kotlin
package me.jbusdriver.modern.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.data.DefaultMovieRepository
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: DefaultSettingsRepository
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindMovieRepository(
        impl: DefaultMovieRepository
    ): MovieRepository
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt
git commit -m "feat(migration): add MovieRepository binding to DataModule"
```

---

### Task 5: Test MovieRepository

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/data/DefaultMovieRepositoryTest.kt`

- [ ] **Step 1: Write the test**

Create file `app/src/test/java/me/jbusdriver/modern/data/DefaultMovieRepositoryTest.kt`:

```kotlin
package me.jbusdriver.modern.data

import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.ui.data.enums.DataSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMovieRepositoryTest {

    private val repository = DefaultMovieRepository()

    @Test
    fun loadPage_returnsMoviePageResult() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertNotNull(result)
        assertTrue("Page should have active page >= 0", result.pageInfo.activePage >= 0)
    }

    @Test
    fun loadPage_firstPage_returnsMovies() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertTrue("First page should have movies", result.movies.isNotEmpty())
        if (result.movies.isNotEmpty()) {
            val movie = result.movies.first()
            assertTrue("Movie should have a title", movie.title.isNotBlank())
            assertTrue("Movie should have a code", movie.code.isNotBlank())
            assertTrue("Movie should have a link", movie.link.isNotBlank())
        }
    }

    @Test
    fun loadPage_pageInfo_hasCorrectActivePage() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertEquals("Active page should be 1", 1, result.pageInfo.activePage)
    }
}
```

**Note:** These tests make real network calls and depend on the service being available. They validate the full data flow (network → Jsoup → movies). If the network is unavailable, they will fail — that's expected for integration-style tests.

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.DefaultMovieRepositoryTest"`
Expected: Tests pass if network is available. May fail on CI without network.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/me/jbusdriver/modern/data/DefaultMovieRepositoryTest.kt
git commit -m "test(migration): add DefaultMovieRepository integration tests"
```

---

### Task 6: Create MovieListViewModel

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt`

- [ ] **Step 1: Create ViewModel with pagination state**

Create file `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.data.model.hasNext
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.ui.data.enums.DataSourceType
import javax.inject.Inject

data class MovieListUiState(
    val movies: List<Movie> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true
)

@HiltViewModel
class MovieListViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieListUiState())
    val uiState: StateFlow<MovieListUiState> = _uiState.asStateFlow()

    private var currentPage = 0
    private var dataSourceType: DataSourceType = DataSourceType.CENSORED

    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType != type) {
            dataSourceType = type
            currentPage = 0
            _uiState.value = MovieListUiState()
            loadFirstPage()
        }
    }

    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = repository.loadPage(dataSourceType, 1)
                _uiState.update {
                    it.copy(
                        movies = result.movies,
                        pageInfo = result.pageInfo,
                        isLoading = false,
                        hasMore = result.pageInfo.hasNext,
                        error = if (result.movies.isEmpty()) "没有数据" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val result = repository.loadPage(dataSourceType, 1)
                _uiState.update {
                    it.copy(
                        movies = result.movies,
                        pageInfo = result.pageInfo,
                        isRefreshing = false,
                        hasMore = result.pageInfo.hasNext
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.pageInfo.nextPage
        if (nextPage <= currentPage) return

        currentPage = nextPage
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = repository.loadPage(dataSourceType, nextPage)
                _uiState.update {
                    it.copy(
                        movies = it.movies + result.movies,
                        pageInfo = result.pageInfo,
                        isLoadingMore = false,
                        hasMore = result.pageInfo.hasNext
                    )
                }
            } catch (e: Exception) {
                currentPage = state.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt
git commit -m "feat(migration): add MovieListViewModel with pagination (refresh, load-more, error handling)"
```

---

### Task 7: Test MovieListViewModel

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt`

- [ ] **Step 1: Write ViewModel tests**

Create file `app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.ui.data.enums.DataSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testMovies = listOf(
        Movie("Test Movie 1", "http://img1.jpg", "ABC-001", "2024-01-01", "http://link1"),
        Movie("Test Movie 2", "http://img2.jpg", "DEF-002", "2024-01-02", "http://link2")
    )

    private fun createViewModel(repository: MovieRepository): MovieListViewModel {
        return MovieListViewModel(repository)
    }

    @Test
    fun loadFirstPage_loadsMoviesAndPageInfo() = runTest(testDispatcher) {
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertEquals(testMovies, viewModel.uiState.value.movies)
        assertEquals(1, viewModel.uiState.value.pageInfo.activePage)
        assertTrue(viewModel.uiState.value.hasMore)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadMore_appendsMovies() = runTest(testDispatcher) {
        val page2Movies = listOf(
            Movie("Movie 3", "http://img3.jpg", "GHI-003", "2024-01-03", "http://link3")
        )
        var callCount = 0
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                when (page) {
                    1 -> MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies).also { callCount++ }
                    else -> MoviePageResult(PageInfo(2, 3, listOf(1, 2, 3)), page2Movies).also { callCount++ }
                }
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(2, callCount)

        assertEquals(testMovies + page2Movies, viewModel.uiState.value.movies)
        assertEquals(2, viewModel.uiState.value.pageInfo.activePage)
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun loadFirstPage_handlesError() = runTest(testDispatcher) {
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                throw RuntimeException("Network error")
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Network error") == true)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_resetsMovies() = runTest(testDispatcher) {
        var callCount = 0
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies).also { callCount++ }
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, callCount)
        assertEquals(testMovies, viewModel.uiState.value.movies)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun loadMore_doesNotLoadWhenNoMorePages() = runTest(testDispatcher) {
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                MoviePageResult(PageInfo(1, 1, listOf(1)), testMovies) // no next page
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasMore)

        viewModel.loadMore() // should be no-op
        advanceUntilIdle()

        assertEquals(testMovies, viewModel.uiState.value.movies)
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.movielist.MovieListViewModelTest"`
Expected: All 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt
git commit -m "test(migration): add MovieListViewModel tests (5 tests)"
```

---

### Task 8: Create MovieItem Composable

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieItem.kt`

- [ ] **Step 1: Create the movie item composable**

Create file `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieItem.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.jbusdriver.mvp.bean.Movie

@Composable
fun MovieItem(
    movie: Movie,
    onClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick(movie) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            AsyncImage(
                model = movie.imageUrl,
                contentDescription = movie.title,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = movie.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (movie.date.isNotBlank()) {
                    Text(
                        text = movie.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (!movie.tags.isNullOrEmpty()) {
                    Text(
                        text = movie.tags.take(3).joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieItem.kt
git commit -m "feat(migration): add MovieItem composable with image, title, code, date, tags"
```

---

### Task 9: Create MovieListScreen Composable

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt`

- [ ] **Step 1: Create the full movie list screen**

Create file `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.mvp.bean.Movie

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    title: String = "电影列表",
    onMovieClick: (Movie) -> Unit = {},
    viewModel: MovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState.movies.isEmpty() && !uiState.isLoading) {
            viewModel.loadFirstPage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) })
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.movies.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error ?: "加载失败", color = androidx.compose.ui.graphics.Color.Red)
                    }
                }
                else -> {
                    val listState = rememberLazyListState()

                    // Load more when scrolled to bottom
                    LaunchedEffect(listState, uiState.hasMore) {
                        snapshotFlow {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            lastVisible >= totalItems - 3
                        }.collect { nearEnd ->
                            if (nearEnd && uiState.hasMore && !uiState.isLoadingMore) {
                                viewModel.loadMore()
                            }
                        }
                    }

                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(uiState.movies, key = { it.link }) { movie ->
                            MovieItem(
                                movie = movie,
                                onClick = { onMovieClick(movie) }
                            )
                        }

                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        if (!uiState.hasMore && uiState.movies.isNotEmpty()) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "没有更多了",
                                        color = androidx.compose.ui.graphics.Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt
git commit -m "feat(migration): add MovieListScreen with pull-to-refresh, load-more, and error states"
```

---

### Task 10: Update Navigation to Include Movie List

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`

- [ ] **Step 1: Add movie list route to NavigationKeys**

Update `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`:

```kotlin
package me.jbusdriver.modern.ui

object NavigationKeys {
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_MOVIE_LIST = "movie_list"
}
```

- [ ] **Step 2: Add movie list destination to Navigation**

Update `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`:

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.settings.SettingsScreen

@Composable
fun JBusNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavigationKeys.ROUTE_MOVIE_LIST
    ) {
        composable(NavigationKeys.ROUTE_MOVIE_LIST) {
            MovieListScreen()
        }
        composable(NavigationKeys.ROUTE_SETTINGS) {
            SettingsScreen()
        }
    }
}
```

**Note:** Start destination changed from settings to movie_list — this makes ModernMainActivity the primary app entry with the movie list as the main screen. The settings screen is still accessible.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt
git commit -m "feat(migration): add movie list route and set as start destination"
```

---

### Task 11: Build, Test, and Verify

**Files:** None (verification only)

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 3: Verify on device**

Run: `./gradlew installDebug`
Manually verify:
1. App launches → SplashActivity → MainActivity (unchanged)
2. Click "设置" in nav header → opens ModernMainActivity with MovieListScreen
3. Movies load with images, title, code, date
4. Pull down to refresh → reloads first page
5. Scroll to bottom → loads more pages
6. Movie items display correctly with images via Coil
7. Settings screen still accessible via navigation

- [ ] **Step 4: Final commit with any fixes**

```bash
git add -A
git commit -m "fix(migration): address Phase 2 verification issues"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** Phase 2 from design spec — movie list screen with pagination, Jsoup parsing, caching, Compose UI
- [x] **Placeholder scan:** No TBD, TODO — all code is concrete
- [x] **Type consistency:** `MovieRepository.loadPage()` returns `MoviePageResult` matching ViewModel usage. `PageInfo` defined in Task 2 matches usage in Tasks 3, 5, 6, 7. `MovieListUiState` fields match between Task 6 ViewModel and Task 9 Screen. `Movie` data class reused from existing `me.jbusdriver.mvp.bean.Movie`.
- [x] **File paths:** All new files under `app/src/main/java/me/jbusdriver/modern/`
- [x] **Coexistence:** No changes to existing MVP code. Repository wraps `JAVBusService` and `CacheLoader` via `suspendCancellableCoroutine` bridging RxJava to coroutines.
