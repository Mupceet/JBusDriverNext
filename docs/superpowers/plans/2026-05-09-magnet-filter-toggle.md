# Magnet Filter Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a toggle bar at the top of every movie list that switches between "已有磁力" and "全部影片" modes, showing item counts parsed from the server's `.alert-success` HTML element.

**Architecture:** Parse filter counts from `#resultshowmag` / `#resultshowall` HTML elements via a new parser function. Thread `showAll` boolean through repository → ViewModel → NetClient (which already supports the `existmag` Cookie). Display a `MovieFilterBar` composable in the existing `header` slot of `MovieList`.

**Tech Stack:** Kotlin, Jsoup HTML parsing, Jetpack Compose Material3, StateFlow

---

## File Structure

| File | Responsibility | Status |
|------|---------------|--------|
| `app/src/main/java/me/jbusdriver/modern/domain/model/MoviePageResult.kt` | `MovieFilterInfo` data class + `filterInfo` field on `MoviePageResult` | Modify |
| `app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt` | `parseMovieFilterInfo()` — parse `#resultshowmag` / `#resultshowall` | Modify |
| `app/src/main/java/me/jbusdriver/modern/data/MovieRepository.kt` | Add `showAll` to `loadPageByUrl` interface + impl; add `filterInfo` parsing to both load methods | Modify |
| `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt` | Add `showAll` / `filterInfo` state + `toggleShowAll()` | Modify |
| `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt` | Add `showAll` / `filterInfo` state + `toggleShowAll()` | Modify |
| `app/src/main/java/me/jbusdriver/modern/ui/components/MovieFilterBar.kt` | Toggle bar composable | Create |
| `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt` | Add `MovieFilterBar` to header | Modify |
| `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt` | Add `MovieFilterBar` to header | Modify |
| `app/src/test/java/me/jbusdriver/modern/data/DefaultMovieRepositoryTest.kt` | Update fake repo to match new `loadPageByUrl` signature | Modify |
| `app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt` | Add `toggleShowAll` test; update fake repo | Modify |
| `app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt` | Add `toggleShowAll` test; update fake repo | Modify |

---

### Task 1: Data Model — `MovieFilterInfo` and `MoviePageResult` update

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/MoviePageResult.kt`

- [ ] **Step 1: Add `MovieFilterInfo` data class and `filterInfo` field**

Append `MovieFilterInfo` data class after `ActressDetail` (after line 59). Add `filterInfo` field to `MoviePageResult`:

```kotlin
// After ActressDetail (line 59), add:

data class MovieFilterInfo(
    val magnetCount: Int,
    val totalCount: Int
)
```

In `MoviePageResult` data class (line 36-40), add `filterInfo` field:

```kotlin
data class MoviePageResult(
    val pageInfo: PageInfo,
    val movies: List<Movie>,
    val filterInfo: MovieFilterInfo? = null
)
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (all existing callers use default `null`)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/domain/model/MoviePageResult.kt
git commit -m "feat: add MovieFilterInfo model and filterInfo field to MoviePageResult"
```

---

### Task 2: Parser — `parseMovieFilterInfo()`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt`

The HTML structure to parse:
```html
<div class="alert alert-success alert-common">
  <p><b>...</b>&nbsp;：&nbsp;當前顯示<b><a id="resultshowmag"> 已有磁力 194 </a></b>部，可切換至<b><a id="resultshowall"> 全部影片 399 </a></b>部</p>
</div>
```

- [ ] **Step 1: Add `parseMovieFilterInfo()` function**

Add after the `// endregion` for region 磁力链接获取 (after line 281), before `// region URL 工具`:

```kotlin
// region 筛选信息解析

fun parseMovieFilterInfo(doc: Document): MovieFilterInfo? {
    val alert = doc.selectFirst(".alert-success") ?: return null
    val magnetText = alert.selectFirst("#resultshowmag")?.text() ?: return null
    val allText = alert.selectFirst("#resultshowall")?.text() ?: return null
    val magnetCount = magnetText.filter { it.isDigit() }.toIntOrNull() ?: return null
    val totalCount = allText.filter { it.isDigit() }.toIntOrNull() ?: return null
    return MovieFilterInfo(magnetCount, totalCount)
}

// endregion
```

Import needed: `MovieFilterInfo` is in the same `domain.model` package already star-imported.

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt
git commit -m "feat: add parseMovieFilterInfo to extract magnet/total counts from HTML"
```

---

### Task 3: Repository — Add `showAll` to `loadPageByUrl` and parse `filterInfo`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/MovieRepository.kt`

- [ ] **Step 1: Update `MovieRepository` interface**

Change `loadPageByUrl` signature (line 82-86) to add `showAll`:

```kotlin
suspend fun loadPageByUrl(
    url: String,
    page: Int,
    showAll: Boolean = false,
    forceRefresh: Boolean = false
): MoviePageResult
```

- [ ] **Step 2: Update `DefaultMovieRepository.loadPageByUrl` implementation**

In `DefaultMovieRepository.loadPageByUrl` (lines 176-190), add `showAll` parameter, pass to `fetchDocument`, include in cache key, and parse `filterInfo`:

```kotlin
override suspend fun loadPageByUrl(
    url: String,
    page: Int,
    showAll: Boolean,
    forceRefresh: Boolean
): MoviePageResult {
    val cacheKey = "page_${url.urlPath}_${showAll}_$page"

    return CacheLoader.lruCached(cacheKey, forceRefresh) {
        val fullUrl = if (page == 1) url else "$url/$page"
        val doc = NetClient.fetchDocument(fullUrl, showAll)
        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
        val movies = loadMovieFromDoc(doc)
        val filterInfo = parseMovieFilterInfo(doc)
        MoviePageResult(pageInfo, movies, filterInfo)
    }
}
```

Add the import for `parseMovieFilterInfo` to the existing import block (near line 12-14):
```kotlin
import me.jbusdriver.modern.data.parser.parseMovieFilterInfo
```

- [ ] **Step 3: Update `DefaultMovieRepository.loadPage` implementation**

In `loadPage` (lines 112-133), add `filterInfo` parsing and include `showAll` in cache key:

Change cache key from `"${type.key}_${showAll}_$page"` (already has showAll — good).

Add after `val movies = loadMovieFromDoc(doc)` (line 131):
```kotlin
val filterInfo = parseMovieFilterInfo(doc)
```

Change the return to:
```kotlin
MoviePageResult(pageInfo, movies, filterInfo)
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (default `showAll = false` preserves existing callers)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/MovieRepository.kt
git commit -m "feat: add showAll param to loadPageByUrl, parse MovieFilterInfo in both load methods"
```

---

### Task 4: ViewModel — `LinkMovieListViewModel` showAll state

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt`

- [ ] **Step 1: Add fields to `LinkMovieListUiState`**

In `LinkMovieListUiState` (line 27-50), add after `isCollected`:

```kotlin
val showAll: Boolean = false,
val filterInfo: MovieFilterInfo? = null
```

Add import:
```kotlin
import me.jbusdriver.modern.domain.model.MovieFilterInfo
```

- [ ] **Step 2: Pass `showAll` to repository calls**

In `loadFirstPage()` (line 120-140), change:
```kotlin
val result = repository.loadPageByUrl(linkUrl, 1)
```
to:
```kotlin
val result = repository.loadPageByUrl(linkUrl, 1, showAll = _uiState.value.showAll)
```

In the success update block, add `filterInfo`:
```kotlin
_uiState.update {
    it.copy(
        movies = result.movies.map { m -> m.toUiModel() },
        pageInfo = result.pageInfo,
        isLoading = false,
        hasMore = result.pageInfo.hasNext,
        error = if (result.movies.isEmpty()) "沒有數據" else null,
        filterInfo = result.filterInfo
    )
}
```

In `loadMore()` (lines 148-172), change:
```kotlin
val result = repository.loadPageByUrl(linkUrl, nextPage)
```
to:
```kotlin
val result = repository.loadPageByUrl(linkUrl, nextPage, showAll = _uiState.value.showAll)
```

In the success update, add `filterInfo`:
```kotlin
_uiState.update {
    it.copy(
        movies = it.movies + result.movies.map { m -> m.toUiModel() },
        pageInfo = result.pageInfo,
        isLoadingMore = false,
        hasMore = result.pageInfo.hasNext,
        filterInfo = result.filterInfo ?: it.filterInfo
    )
}
```

Note: `loadMore` uses `?: it.filterInfo` to keep existing filterInfo if server stops sending it on later pages.

In `refresh()` (lines 180-202), change:
```kotlin
val result = repository.loadPageByUrl(linkUrl, 1, forceRefresh = true)
```
to:
```kotlin
val result = repository.loadPageByUrl(linkUrl, 1, showAll = _uiState.value.showAll, forceRefresh = true)
```

In the refresh success update, add `filterInfo`:
```kotlin
_uiState.update {
    it.copy(
        movies = result.movies.map { m -> m.toUiModel() },
        pageInfo = result.pageInfo,
        isRefreshing = false,
        hasMore = result.pageInfo.hasNext,
        filterInfo = result.filterInfo
    )
}
```

- [ ] **Step 3: Add `toggleShowAll()` method**

Add after `toggleActressCollect()` (after line 257):

```kotlin
fun toggleShowAll() {
    val newState = !_uiState.value.showAll
    _uiState.update { it.copy(showAll = newState, movies = emptyList()) }
    currentPage = 0
    loadFirstPage()
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt
git commit -m "feat: add showAll/filterInfo state and toggleShowAll to LinkMovieListViewModel"
```

---

### Task 5: ViewModel — `MovieListViewModel` showAll state

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt`

- [ ] **Step 1: Add fields to `MovieListUiState`**

In `MovieListUiState` (lines 26-41), add after `hasMore`:

```kotlin
val showAll: Boolean = false,
val filterInfo: MovieFilterInfo? = null
```

Add imports:
```kotlin
import me.jbusdriver.modern.domain.model.MovieFilterInfo
```

- [ ] **Step 2: Pass `showAll` to repository calls**

In `loadMovies()` private method (lines 165-183), change:
```kotlin
val result = repository.loadPage(dataSourceType, page, forceRefresh = forceRefresh)
```
to:
```kotlin
val result = repository.loadPage(dataSourceType, page, showAll = _uiState.value.showAll, forceRefresh = forceRefresh)
```

- [ ] **Step 3: Add `filterInfo` to success handlers**

In `loadFirstPage()` success lambda (lines 99-106), add `filterInfo`:
```kotlin
onSuccess = { result, state ->
    state.copy(
        movies = result.movies.map { it.toUiModel() },
        pageInfo = result.pageInfo,
        isLoading = false,
        hasMore = result.pageInfo.hasNext,
        error = if (result.movies.isEmpty()) "沒有數據" else null,
        filterInfo = result.filterInfo
    )
},
```

In `refresh()` success lambda (lines 123-130), add `filterInfo`:
```kotlin
onSuccess = { result, state ->
    state.copy(
        movies = result.movies.map { it.toUiModel() },
        pageInfo = result.pageInfo,
        isRefreshing = false,
        hasMore = result.pageInfo.hasNext,
        filterInfo = result.filterInfo
    )
},
```

In `loadMore()` success lambda (lines 153-159), add `filterInfo`:
```kotlin
onSuccess = { result, state ->
    state.copy(
        movies = state.movies + result.movies.map { it.toUiModel() },
        pageInfo = result.pageInfo,
        isLoadingMore = false,
        hasMore = result.pageInfo.hasNext,
        filterInfo = result.filterInfo ?: state.filterInfo
    )
},
```

- [ ] **Step 4: Add `toggleShowAll()` method**

Add after `clearError()` (after line 189):

```kotlin
fun toggleShowAll() {
    val newState = !_uiState.value.showAll
    _uiState.update { it.copy(showAll = newState, movies = emptyList()) }
    currentPage = 0
    loadFirstPage()
}
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt
git commit -m "feat: add showAll/filterInfo state and toggleShowAll to MovieListViewModel"
```

---

### Task 6: UI — `MovieFilterBar` composable

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieFilterBar.kt`

- [ ] **Step 1: Create `MovieFilterBar.kt`**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MovieFilterBar(
    magnetCount: Int,
    totalCount: Int,
    showAll: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterSegment(
                text = "已有磁力 ($magnetCount)",
                active = !showAll,
                onClick = { if (showAll) onToggle() },
                modifier = Modifier.weight(1f)
            )
            FilterSegment(
                text = "全部影片 ($totalCount)",
                active = showAll,
                onClick = { if (!showAll) onToggle() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterSegment(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/MovieFilterBar.kt
git commit -m "feat: add MovieFilterBar composable for magnet/all toggle"
```

---

### Task 7: UI — Integrate `MovieFilterBar` in `LinkMovieListScreen`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt`

- [ ] **Step 1: Add `MovieFilterBar` to the header**

In the `else` branch (line 145-167), modify the `header` lambda to include `MovieFilterBar` after the actress detail card. The current code is:

```kotlin
val header: (@Composable () -> Unit)? = if (type == "actress") {
    {
        val actress = uiState.actressDetail
        val actressError = uiState.actressError
        when {
            actress != null -> ActressDetailCard(actress)
            uiState.isLoadingActress -> ActressDetailLoadingPlaceholder()
            actressError != null -> ActressDetailErrorCard(actressError)
        }
    }
} else null
```

Replace with:

```kotlin
val filterBar: (@Composable () -> Unit)? = uiState.filterInfo?.let { info ->
    {
        MovieFilterBar(
            magnetCount = info.magnetCount,
            totalCount = info.totalCount,
            showAll = uiState.showAll,
            onToggle = { viewModel.toggleShowAll() }
        )
    }
}

val header: (@Composable () -> Unit)? = when {
    type == "actress" && filterBar != null -> {
        {
            val actress = uiState.actressDetail
            val actressError = uiState.actressError
            when {
                actress != null -> ActressDetailCard(actress)
                uiState.isLoadingActress -> ActressDetailLoadingPlaceholder()
                actressError != null -> ActressDetailErrorCard(actressError)
            }
            filterBar()
        }
    }
    type == "actress" -> {
        {
            val actress = uiState.actressDetail
            val actressError = uiState.actressError
            when {
                actress != null -> ActressDetailCard(actress)
                uiState.isLoadingActress -> ActressDetailLoadingPlaceholder()
                actressError != null -> ActressDetailErrorCard(actressError)
            }
        }
    }
    filterBar != null -> filterBar
    else -> null
}
```

Add import:
```kotlin
import me.jbusdriver.modern.ui.components.MovieFilterBar
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt
git commit -m "feat: integrate MovieFilterBar in LinkMovieListScreen header"
```

---

### Task 8: UI — Integrate `MovieFilterBar` in `MovieListScreen`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt`

- [ ] **Step 1: Add `MovieFilterBar` as header to `MovieList`**

In the `else` branch (lines 58-65), wrap `MovieList` with header. Current code:

```kotlin
else -> {
    MovieList(
        movies = uiState.movies,
        hasMore = uiState.hasMore,
        isLoadingMore = uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() },
        onMovieClick = onMovieClick
    )
}
```

Replace with:

```kotlin
else -> {
    val filterBar: (@Composable () -> Unit)? = uiState.filterInfo?.let { info ->
        {
            MovieFilterBar(
                magnetCount = info.magnetCount,
                totalCount = info.totalCount,
                showAll = uiState.showAll,
                onToggle = { viewModel.toggleShowAll() }
            )
        }
    }
    MovieList(
        movies = uiState.movies,
        hasMore = uiState.hasMore,
        isLoadingMore = uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() },
        onMovieClick = onMovieClick,
        header = filterBar
    )
}
```

Add imports:
```kotlin
import me.jbusdriver.modern.ui.components.MovieFilterBar
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt
git commit -m "feat: integrate MovieFilterBar in MovieListScreen header"
```

---

### Task 9: Tests — Update existing tests to match new signatures

**Files:**
- Modify: `app/src/test/java/me/jbusdriver/modern/data/DefaultMovieRepositoryTest.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt`

- [ ] **Step 1: Update `DefaultMovieRepositoryTest.kt` fake repo**

The fake repo's `loadPageByUrl` signature needs `showAll` parameter (line 44):

```kotlin
override suspend fun loadPageByUrl(url: String, page: Int, showAll: Boolean, forceRefresh: Boolean) =
    MoviePageResult(PageInfo(page, page + 1, listOf(page, page + 1)), fakeMovies)
```

- [ ] **Step 2: Update `LinkMovieListViewModelTest.kt` fake repo**

The fake repo's `loadPageByUrl` signature needs `showAll` parameter (line 57):

```kotlin
override suspend fun loadPageByUrl(url: String, page: Int, showAll: Boolean, forceRefresh: Boolean) =
    onLoadPageByUrl(url, page)
```

- [ ] **Step 3: Update `MovieListViewModelTest.kt` fake repo**

The fake repo's `loadPageByUrl` signature needs `showAll` parameter (line 44):

```kotlin
override suspend fun loadPageByUrl(url: String, page: Int, showAll: Boolean, forceRefresh: Boolean) =
    MoviePageResult(PageInfo(), emptyList())
```

- [ ] **Step 4: Add `toggleShowAll` test to `LinkMovieListViewModelTest.kt`**

Add after the `refresh_reloadsMovies` test (after line 119):

```kotlin
@Test
fun toggleShowAll_reloadsMoviesWithShowAll() = runTest(testDispatcher) {
    var showAllCapture = false
    val repository = object : MovieRepository {
        override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean, forceRefresh: Boolean) =
            MoviePageResult(PageInfo(), emptyList())
        override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
            emptyList<ActressInfo>() to PageInfo()
        override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) = emptyList<GenreCategory>()
        override suspend fun loadPageByUrl(url: String, page: Int, showAll: Boolean, forceRefresh: Boolean): MoviePageResult {
            showAllCapture = showAll
            return MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies, MovieFilterInfo(5, 10))
        }
        override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? = null
    }
    val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, SavedStateHandle())

    viewModel.setLink("http://example.com/star/abc")
    advanceUntilIdle()
    assertFalse(showAllCapture)

    viewModel.toggleShowAll()
    advanceUntilIdle()
    assertTrue(showAllCapture)
    assertTrue(viewModel.uiState.value.showAll)
    assertEquals(5, viewModel.uiState.value.filterInfo?.magnetCount)
    assertEquals(10, viewModel.uiState.value.filterInfo?.totalCount)
}
```

Add import:
```kotlin
import me.jbusdriver.modern.domain.model.MovieFilterInfo
```

- [ ] **Step 5: Add `toggleShowAll` test to `MovieListViewModelTest.kt`**

Add after the `loadMore_doesNotLoadWhenNoMorePages` test (after line 167):

```kotlin
@Test
fun toggleShowAll_reloadsMoviesWithShowAll() = runTest(testDispatcher) {
    var showAllCapture = false
    val repository = object : MovieRepository {
        override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean, forceRefresh: Boolean): MoviePageResult {
            showAllCapture = showAll
            return MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies, MovieFilterInfo(5, 10))
        }
        override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
            emptyList<ActressInfo>() to PageInfo()
        override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) = emptyList<GenreCategory>()
        override suspend fun loadPageByUrl(url: String, page: Int, showAll: Boolean, forceRefresh: Boolean) =
            MoviePageResult(PageInfo(), emptyList())
        override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? = null
    }
    val viewModel = MovieListViewModel(repository)

    viewModel.setDataSourceType(DataSourceType.CENSORED)
    advanceUntilIdle()
    Thread.sleep(100)
    advanceUntilIdle()
    assertFalse(showAllCapture)

    viewModel.toggleShowAll()
    advanceUntilIdle()
    Thread.sleep(100)
    advanceUntilIdle()
    assertTrue(showAllCapture)
    assertTrue(viewModel.uiState.value.showAll)
    assertEquals(5, viewModel.uiState.value.filterInfo?.magnetCount)
}
```

Add import:
```kotlin
import me.jbusdriver.modern.domain.model.MovieFilterInfo
```

- [ ] **Step 6: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/test/
git commit -m "test: update fake repos and add toggleShowAll tests for both ViewModels"
```

---

### Task 10: Final verification

- [ ] **Step 1: Run full build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 3: Manual smoke test**

Install debug APK on device. Navigate to:
1. Home tab movie list — verify `MovieFilterBar` appears with magnet/total counts
2. Tap "全部影片" segment — verify list reloads with all movies
3. Navigate to an actress page — verify `MovieFilterBar` appears below actress detail card
4. Tap "已有磁力" segment — verify list reloads with magnet-only movies
5. Pull to refresh — verify `showAll` state is preserved
