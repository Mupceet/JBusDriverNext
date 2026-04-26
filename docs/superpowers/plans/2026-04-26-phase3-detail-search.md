# Phase 3: Movie Detail & Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the movie detail screen and search screen to modern architecture, enabling navigation from the movie list to detail and from detail to search.

**Architecture:** MovieDetailRepository wraps existing `JAVBusService` + `parseMovieDetails()` Jsoup parsing. MovieDetailViewModel manages detail state (loading, data, error). MovieDetailScreen is a Compose screen with collapsing toolbar cover, info sections, image samples, genres, actresses, and related movies. SearchScreen provides cross-source search with paginated results.

**Tech Stack:** Hilt, Compose + Material 3, ViewModel + StateFlow, Kotlin Coroutines/Flow, Jsoup, Coil, Navigation Compose.

**Design Spec:** `docs/superpowers/specs/2026-04-26-architecture-migration-design.md` (Phase 3)

---

## How the Existing Movie Detail Works

1. **Entry:** `MovieDetailActivity.start(context, movie)` passes a `Movie` parcelable.
2. **Fetch:** `MovieDetailPresenterImpl` calls `JAVBusService.INSTANCE.get(url)` → Jsoup parse → `parseMovieDetails(doc)`.
3. **Parse:** `parseMovieDetails()` extracts: title, cover, headers (code/date/director/studio/series), genres, actresses (name+avatar+link), image samples (thumb+full), related movies.
4. **Display:** Activity uses holders (HeaderHolder, ImageSampleHolder, ActressListHolder, GenresHolder, RelativeMovieHolder) to render sections.
5. **Cache:** First load checks disk cache (`CacheLoader.acache`), then network.
6. **Magnet:** Button opens `MagnetPagerListActivity` with movie code.

## How the Existing Search Works

1. **Entry:** `SearchResultActivity.start(context, searchWord)` passes keyword.
2. **Tabs:** `SearchResultPagesFragment` creates tabs per `SearchType` (CENSORED, UNCENSORED, ACTRESS).
3. **Each tab:** Uses `LinkedMovieListFragment` or `ActressListFragment` with `SearchLink(type, query)` as the data source.
4. **URL:** `SearchLink.link` constructs `${baseUrl}${type.urlPathFormater.format(query)}`.

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `app/src/main/java/me/jbusdriver/modern/data/MovieDetailRepository.kt` | Interface + DefaultMovieDetailRepository |
| Create | `app/src/main/java/me/jbusdriver/modern/data/SearchRepository.kt` | Interface + DefaultSearchRepository |
| Modify | `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt` | Add new repository bindings |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt` | @HiltViewModel with detail state |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt` | Compose UI for movie detail |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt` | Compose UI for search |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt` | @HiltViewModel with search state |
| Modify | `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt` | Add detail + search routes |
| Modify | `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt` | Add detail + search destinations |
| Create | `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt` | ViewModel tests |
| Create | `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt` | ViewModel tests |

---

### Task 1: Create MovieDetailRepository

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/MovieDetailRepository.kt`

- [ ] **Step 1: Create repository**

Create file `app/src/main/java/me/jbusdriver/modern/data/MovieDetailRepository.kt`:

```kotlin
package me.jbusdriver.modern.data

import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
import me.jbusdriver.base.urlPath
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.mvp.bean.MovieDetail
import me.jbusdriver.mvp.bean.parseMovieDetails
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface MovieDetailRepository {
    suspend fun getMovieDetail(url: String): MovieDetail
}

@Singleton
class DefaultMovieDetailRepository @Inject constructor() : MovieDetailRepository {

    override suspend fun getMovieDetail(url: String): MovieDetail {
        // Check disk cache first
        val cacheKey = url.urlPath
        val cached = CacheLoader.acache.getAsString(cacheKey)
        if (!cached.isNullOrBlank()) {
            val cachedDetail = GSON.fromJson<MovieDetail>(cached)
            if (cachedDetail != null) return cachedDetail
        }

        // Fetch from network
        val html = suspendCancellableCoroutine<String> { cont ->
            val disposable = JAVBusService.INSTANCE.get(url)
                .subscribe(
                    { html -> cont.resumeWith(Result.success(html)) },
                    { error -> cont.resumeWith(Result.failure(error)) }
                )
            cont.invokeOnCancellation { disposable.dispose() }
        }

        val doc = Jsoup.parse(html)
        val detail = parseMovieDetails(doc)

        // Cache to disk
        CacheLoader.cacheDisk(cacheKey to detail)

        return detail
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/MovieDetailRepository.kt
git commit -m "feat(migration): add MovieDetailRepository with disk cache and Jsoup parsing"
```

---

### Task 2: Create SearchRepository

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/SearchRepository.kt`

- [ ] **Step 1: Create repository**

Create file `app/src/main/java/me/jbusdriver/modern/data/SearchRepository.kt`:

```kotlin
package me.jbusdriver.modern.data

import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.loadMovieFromDoc
import me.jbusdriver.mvp.bean.parseActressList
import me.jbusdriver.ui.data.enums.DataSourceType
import me.jbusdriver.ui.data.enums.SearchType
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface SearchRepository {
    suspend fun searchMovies(type: SearchType, query: String, page: Int): MoviePageResult
    suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>>
}

@Singleton
class DefaultSearchRepository @Inject constructor() : SearchRepository {

    override suspend fun searchMovies(type: SearchType, query: String, page: Int): MoviePageResult {
        val baseUrl = JAVBusService.defaultFastUrl
        val url = "${baseUrl}${type.urlPathFormater.format(query)}${if (page > 1) "/$page" else ""}"

        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)

        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
        val movies = loadMovieFromDoc(doc)

        return MoviePageResult(pageInfo, movies)
    }

    override suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>> {
        val baseUrl = JAVBusService.defaultFastUrl
        val type = SearchType.ACTRESS
        val url = "${baseUrl}${type.urlPathFormater.format(query)}${if (page > 1) "/$page" else ""}"

        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)

        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
        val actresses = parseActressList(doc)

        return pageInfo to actresses
    }

    private fun fetchHtml(url: String): String = suspendCancellableCoroutine { cont ->
        val disposable = JAVBusService.INSTANCE.get(url)
            .subscribe(
                { html -> cont.resumeWith(Result.success(html)) },
                { error -> cont.resumeWith(Result.failure(error)) }
            )
        cont.invokeOnCancellation { disposable.dispose() }
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
git add app/src/main/java/me/jbusdriver/modern/data/SearchRepository.kt
git commit -m "feat(migration): add SearchRepository with movie and actress search"
```

---

### Task 3: Update DataModule with New Bindings

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: Add bindings for both new repositories**

Read the existing DataModule.kt and add two new `@Binds` methods:

```kotlin
    @Binds
    @Singleton
    abstract fun bindMovieDetailRepository(
        impl: DefaultMovieDetailRepository
    ): MovieDetailRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: DefaultSearchRepository
    ): SearchRepository
```

Add imports for `DefaultMovieDetailRepository`, `MovieDetailRepository`, `DefaultSearchRepository`, `SearchRepository`.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt
git commit -m "feat(migration): add MovieDetailRepository and SearchRepository bindings to DataModule"
```

---

### Task 4: Create MovieDetailViewModel

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`

- [ ] **Step 1: Create ViewModel**

Create file `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`:

```kotlin
package me.jbusdriver.modern.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.mvp.bean.MovieDetail
import javax.inject.Inject

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val movieDetail: MovieDetail? = null,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    private var currentUrl: String = ""

    fun loadDetail(url: String) {
        if (_uiState.value.isLoading) return
        currentUrl = url
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.getMovieDetail(url)
                _uiState.update { it.copy(movieDetail = detail, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun refresh() {
        if (currentUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val detail = repository.getMovieDetail(currentUrl)
                _uiState.update { it.copy(movieDetail = detail, isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
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
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt
git commit -m "feat(migration): add MovieDetailViewModel with loading and refresh states"
```

---

### Task 5: Test MovieDetailViewModel

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`

- [ ] **Step 1: Write tests**

Create file `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.mvp.bean.Header
import me.jbusdriver.mvp.bean.MovieDetail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MovieDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testDetail = MovieDetail(
        title = "Test Movie",
        content = "Test description",
        cover = "http://cover.jpg",
        headers = listOf(Header("番号", "ABC-001", "")),
        genres = emptyList(),
        actress = emptyList(),
        imageSamples = emptyList(),
        relatedMovies = emptyList()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDetail_loadsMovieDetail() = runTest(testDispatcher) {
        val repository = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String) = testDetail
        }
        val viewModel = MovieDetailViewModel(repository)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.movieDetail)
        assertEquals("Test Movie", viewModel.uiState.value.movieDetail!!.title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadDetail_handlesError() = runTest(testDispatcher) {
        val repository = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String) =
                throw RuntimeException("Network error")
        }
        val viewModel = MovieDetailViewModel(repository)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Network error") == true)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_reloadsDetail() = runTest(testDispatcher) {
        var callCount = 0
        val repository = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String) = testDetail.also { callCount++ }
        }
        val viewModel = MovieDetailViewModel(repository)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, callCount)

        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt
git commit -m "test(migration): add MovieDetailViewModel tests (3 tests)"
```

---

### Task 6: Create MovieDetailScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`

- [ ] **Step 1: Create the detail screen composable**

Create file `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.mvp.bean.Header
import me.jbusdriver.mvp.bean.ImageSample
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.MovieDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieUrl: String,
    onMovieClick: (Movie) -> Unit = {},
    onActressClick: (ActressInfo) -> Unit = {},
    onGenreClick: (Genre) -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(movieUrl) {
        viewModel.loadDetail(movieUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(uiState.movieDetail?.title ?: "加载中...") })
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDetail(movieUrl) }) {
                            Text("重试")
                        }
                    }
                }
            }
            uiState.movieDetail != null -> {
                DetailContent(
                    detail = uiState.movieDetail!!,
                    padding = padding,
                    onMovieClick = onMovieClick,
                    onActressClick = onActressClick,
                    onGenreClick = onGenreClick,
                    onImageClick = onImageClick
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: MovieDetail,
    padding: PaddingValues,
    onMovieClick: (Movie) -> Unit,
    onActressClick: (ActressInfo) -> Unit,
    onGenreClick: (Genre) -> Unit,
    onImageClick: (List<String>, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cover image
        AsyncImage(
            model = detail.cover,
            contentDescription = detail.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        // Title
        Text(
            text = detail.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Description
        if (detail.content.isNotBlank()) {
            Text(
                text = detail.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Headers (info rows)
        if (detail.headers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    detail.headers.forEach { header ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = header.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                text = header.value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Genres
        if (detail.genres.isNotEmpty()) {
            GenreSection(genres = detail.genres, onGenreClick = onGenreClick)
        }

        // Image Samples
        if (detail.imageSamples.isNotEmpty()) {
            ImageSampleSection(
                samples = detail.imageSamples,
                onImageClick = onImageClick
            )
        }

        // Actresses
        if (detail.actress.isNotEmpty()) {
            ActressSection(actresses = detail.actress, onActressClick = onActressClick)
        }

        // Related Movies
        if (detail.relatedMovies.isNotEmpty()) {
            RelatedMovieSection(movies = detail.relatedMovies, onMovieClick = onMovieClick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreSection(genres: List<Genre>, onGenreClick: (Genre) -> Unit) {
    Column {
        Text("类别", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            genres.forEach { genre ->
                AssistChip(
                    onClick = { onGenreClick(genre) },
                    label = { Text(genre.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun ImageSampleSection(
    samples: List<ImageSample>,
    onImageClick: (List<String>, Int) -> Unit
) {
    Column {
        Text("截图", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(samples.size) { index ->
                val sample = samples[index]
                AsyncImage(
                    model = sample.thumb,
                    contentDescription = sample.title,
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val images = samples.map { it.image }
                            onImageClick(images, index)
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun ActressSection(actresses: List<ActressInfo>, onActressClick: (ActressInfo) -> Unit) {
    Column {
        Text("演员", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(actresses) { actress ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onActressClick(actress) }
                ) {
                    AsyncImage(
                        model = actress.avatar,
                        contentDescription = actress.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = actress.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedMovieSection(movies: List<Movie>, onMovieClick: (Movie) -> Unit) {
    Column {
        Text("推荐", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(movies) { movie ->
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onMovieClick(movie) }
                ) {
                    AsyncImage(
                        model = movie.imageUrl,
                        contentDescription = movie.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = movie.code,
                        style = MaterialTheme.typography.labelSmall,
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
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt
git commit -m "feat(migration): add MovieDetailScreen with cover, info, samples, actresses, genres, related movies"
```

---

### Task 7: Create SearchViewModel and SearchScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`

- [ ] **Step 1: Create SearchViewModel**

Create file `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`:

```kotlin
package me.jbusdriver.modern.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.SearchRepository
import me.jbusdriver.modern.data.model.hasNext
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.ui.data.enums.SearchType
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val searchType: SearchType = SearchType.CENSORED,
    val results: List<Movie> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(query: String, type: SearchType = SearchType.CENSORED) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(query = query, searchType = type, isLoading = true, error = null, results = emptyList(), currentPage = 1)
            }
            try {
                val result = repository.searchMovies(type, query, 1)
                _uiState.update {
                    it.copy(
                        results = result.movies,
                        isLoading = false,
                        hasMore = result.pageInfo.hasNext,
                        currentPage = result.pageInfo.activePage
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.currentPage + 1

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = repository.searchMovies(state.searchType, state.query, nextPage)
                _uiState.update {
                    it.copy(
                        results = it.results + result.movies,
                        isLoadingMore = false,
                        hasMore = result.pageInfo.hasNext,
                        currentPage = result.pageInfo.activePage
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    fun setSearchType(type: SearchType) {
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            search(query, type)
        } else {
            _uiState.update { it.copy(searchType = type) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
```

- [ ] **Step 2: Create SearchScreen**

Create file `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.ui.movielist.MovieItem
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.ui.data.enums.SearchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieClick: (Movie) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchInput by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("搜索") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search input
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                label = { Text("搜索") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                trailingIcon = {
                    Text(
                        "搜索",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .let {
                                if (searchInput.isNotBlank()) {
                                    it.padding(end = 8.dp)
                                } else it
                            }
                    )
                }
            )

            // Search type chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SearchType.entries.forEach { type ->
                    FilterChip(
                        selected = uiState.searchType == type,
                        onClick = { viewModel.setSearchType(type) },
                        label = { Text(type.title, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            // Results
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.error ?: "搜索失败", color = Color.Red)
                    }
                }
                uiState.results.isEmpty() && uiState.query.isBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("输入关键词开始搜索", color = Color.Gray)
                    }
                }
                else -> {
                    val listState = rememberLazyListState()

                    androidx.compose.runtime.LaunchedEffect(listState, uiState.hasMore) {
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
                        items(uiState.results, key = { it.link }) { movie ->
                            MovieItem(movie = movie, onClick = { onMovieClick(movie) })
                        }
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt
git commit -m "feat(migration): add SearchScreen and SearchViewModel with type filter and pagination"
```

---

### Task 8: Test SearchViewModel

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt`

- [ ] **Step 1: Write tests**

Create file `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.SearchRepository
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.ui.data.enums.SearchType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testMovies = listOf(
        Movie("Result 1", "http://img1.jpg", "ABC-001", "2024-01-01", "http://link1")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun search_loadsResults() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(type: SearchType, query: String, page: Int) =
                MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
            override suspend fun searchActresses(query: String, page: Int) =
                PageInfo() to emptyList()
        }
        val viewModel = SearchViewModel(repository)

        viewModel.search("test")
        advanceUntilIdle()

        assertEquals(testMovies, viewModel.uiState.value.results)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hasMore)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun search_handlesError() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(type: SearchType, query: String, page: Int) =
                throw RuntimeException("Search failed")
            override suspend fun searchActresses(query: String, page: Int) =
                PageInfo() to emptyList()
        }
        val viewModel = SearchViewModel(repository)

        viewModel.search("test")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Search failed") == true)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun search_emptyQuery_doesNotLoad() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(type: SearchType, query: String, page: Int) =
                error("Should not be called")
            override suspend fun searchActresses(query: String, page: Int) =
                error("Should not be called")
        }
        val viewModel = SearchViewModel(repository)

        viewModel.search("")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.search.SearchViewModelTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt
git commit -m "test(migration): add SearchViewModel tests (3 tests)"
```

---

### Task 9: Update Navigation

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`

- [ ] **Step 1: Add routes to NavigationKeys**

Update `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`:

```kotlin
package me.jbusdriver.modern.ui

object NavigationKeys {
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_MOVIE_LIST = "movie_list"
    const val ROUTE_MOVIE_DETAIL = "movie_detail/{movieUrl}"
    const val ROUTE_SEARCH = "search"

    fun movieDetailUrl(movieUrl: String) = "movie_detail/$movieUrl"
}
```

- [ ] **Step 2: Add destinations to Navigation**

Update `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`:

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.jbusdriver.modern.ui.detail.MovieDetailScreen
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.search.SearchScreen
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
            MovieListScreen(
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                }
            )
        }
        composable(
            route = NavigationKeys.ROUTE_MOVIE_DETAIL,
            arguments = listOf(navArgument("movieUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val movieUrl = backStackEntry.arguments?.getString("movieUrl") ?: ""
            MovieDetailScreen(
                movieUrl = movieUrl,
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                }
            )
        }
        composable(NavigationKeys.ROUTE_SEARCH) {
            SearchScreen(
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                }
            )
        }
        composable(NavigationKeys.ROUTE_SETTINGS) {
            SettingsScreen()
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt
git commit -m "feat(migration): add movie detail and search routes to navigation"
```

---

### Task 10: Build, Test, and Verify

**Files:** None (verification only)

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 3: Final commit with any fixes**

```bash
git add -A
git commit -m "fix(migration): address Phase 3 verification issues"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** Phase 3 from design spec — movie detail screen (complex layout, linked data) and search screen (cross-source search)
- [x] **Placeholder scan:** No TBD, TODO — all code is concrete
- [x] **Type consistency:** `MovieDetailRepository.getMovieDetail()` returns `MovieDetail` matching ViewModel/Screen. `SearchRepository.searchMovies()` returns `MoviePageResult` matching SearchViewModel. `NavigationKeys.movieDetailUrl()` matches the route pattern. All existing types (`Movie`, `MovieDetail`, `ActressInfo`, `Genre`, `ImageSample`, `Header`, `SearchType`) reused from existing codebase.
- [x] **File paths:** All new files under `app/src/main/java/me/jbusdriver/modern/`
- [x] **Coexistence:** No changes to existing MVP code. Repositories wrap existing `JAVBusService` and parsing functions.
