# Filter Switch Loading UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the full-screen spinner during magnet filter toggle with a lightweight top refresh indicator while keeping the existing list visible.

**Architecture:** Add `isFilterSwitching` state to both ViewModels. When toggling, keep old movies and set this flag. Screens use the flag to drive `PullToRefreshBox`'s indicator instead of showing the full-screen spinner. Loading handlers reset the flag on completion.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, StateFlow

---

### Task 1: MovieListViewModel — add isFilterSwitching state

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt`

- [ ] **Step 1: Update toggleShowAll test to verify new behavior**

In `MovieListViewModelTest.kt`, update the existing `toggleShowAll_reloadsMoviesWithShowAll` test (line 170). The test currently doesn't check whether movies are cleared. Add assertions for `isFilterSwitching` and movie preservation:

Replace the entire `toggleShowAll_reloadsMoviesWithShowAll` test with:

```kotlin
@Test
fun toggleShowAll_reloadsMoviesWithShowAll() {
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
    // Immediately after toggle: isFilterSwitching should be true, movies NOT cleared
    assertTrue(viewModel.uiState.value.isFilterSwitching)
    assertEquals(2, viewModel.uiState.value.movies.size) // old movies still present
    assertTrue(viewModel.uiState.value.showAll)

    advanceUntilIdle()
    Thread.sleep(100)
    advanceUntilIdle()
    assertTrue(showAllCapture)
    assertFalse(viewModel.uiState.value.isFilterSwitching)
    assertEquals(5, viewModel.uiState.value.filterInfo?.magnetCount)
}
```

- [ ] **Step 2: Add isFilterSwitching to MovieListUiState**

In `MovieListViewModel.kt`, add the field to `MovieListUiState` (after line 45, before the closing parenthesis):

```kotlin
    /** 是否正在切换筛选条件（保留旧列表，显示顶部刷新指示器） */
    val isFilterSwitching: Boolean = false
```

- [ ] **Step 3: Modify toggleShowAll()**

In `MovieListViewModel.kt`, replace the `toggleShowAll()` method (lines 203-208):

```kotlin
fun toggleShowAll() {
    val newState = !_uiState.value.showAll
    _uiState.update { it.copy(showAll = newState, isFilterSwitching = true) }
    currentPage = 0
    loadFirstPage()
}
```

Key change: `movies = emptyList()` removed, `isFilterSwitching = true` added.

- [ ] **Step 4: Reset isFilterSwitching in loadFirstPage() handlers**

In `MovieListViewModel.kt`, update the `loadFirstPage()` method (lines 98-116). Add `isFilterSwitching = false` to both `onSuccess` and `onError` lambdas:

Replace `loadFirstPage()` with:

```kotlin
fun loadFirstPage() {
    if (_uiState.value.isLoading) return
    currentPage = 1
    loadMovies(
        page = 1,
        loadingFlag = { copy(isLoading = true, error = null) },
        onSuccess = { result, state ->
            state.copy(
                movies = result.movies.map { it.toUiModel() },
                pageInfo = result.pageInfo,
                isLoading = false,
                isFilterSwitching = false,
                hasMore = result.pageInfo.hasNext,
                error = if (result.movies.isEmpty()) "沒有數據" else null,
                filterInfo = result.filterInfo
            )
        },
        onError = { e, state -> state.copy(isLoading = false, isFilterSwitching = false, error = e.message ?: "載入失敗") }
    )
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.movielist.MovieListViewModelTest"`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt
git commit -m "feat: add isFilterSwitching state to MovieListViewModel"
```

---

### Task 2: LinkMovieListViewModel — add isFilterSwitching state

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt`

- [ ] **Step 1: Update toggleShowAll test to verify new behavior**

In `LinkMovieListViewModelTest.kt`, update the existing `toggleShowAll_reloadsMoviesWithShowAll` test (line 123). Replace the entire test with:

```kotlin
@Test
fun toggleShowAll_reloadsMoviesWithShowAll() {
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
    // Immediately after toggle: isFilterSwitching should be true, movies NOT cleared
    assertTrue(viewModel.uiState.value.isFilterSwitching)
    assertEquals(1, viewModel.uiState.value.movies.size) // old movies still present
    assertTrue(viewModel.uiState.value.showAll)

    advanceUntilIdle()
    assertTrue(showAllCapture)
    assertFalse(viewModel.uiState.value.isFilterSwitching)
    assertEquals(5, viewModel.uiState.value.filterInfo?.magnetCount)
    assertEquals(10, viewModel.uiState.value.filterInfo?.totalCount)
}
```

- [ ] **Step 2: Add isFilterSwitching to LinkMovieListUiState**

In `LinkMovieListViewModel.kt`, add the field to `LinkMovieListUiState` (after line 54, before the closing parenthesis):

```kotlin
    /** 是否正在切换筛选条件（保留旧列表，显示顶部刷新指示器） */
    val isFilterSwitching: Boolean = false
```

- [ ] **Step 3: Modify toggleShowAll()**

In `LinkMovieListViewModel.kt`, replace the `toggleShowAll()` method (lines 272-277):

```kotlin
fun toggleShowAll() {
    val newState = !_uiState.value.showAll
    _uiState.update { it.copy(showAll = newState, isFilterSwitching = true) }
    currentPage = 0
    loadFirstPage()
}
```

- [ ] **Step 4: Reset isFilterSwitching in loadFirstPage() handlers**

In `LinkMovieListViewModel.kt`, replace the `loadFirstPage()` method (lines 125-146):

```kotlin
fun loadFirstPage() {
    if (_uiState.value.isLoading || linkUrl.isBlank()) return
    currentPage = 1
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val result = repository.loadPageByUrl(linkUrl, 1, showAll = _uiState.value.showAll)
            _uiState.update {
                it.copy(
                    movies = result.movies.map { m -> m.toUiModel() },
                    pageInfo = result.pageInfo,
                    isLoading = false,
                    isFilterSwitching = false,
                    hasMore = result.pageInfo.hasNext,
                    error = if (result.movies.isEmpty()) "沒有數據" else null,
                    filterInfo = result.filterInfo
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, isFilterSwitching = false, error = e.message ?: "載入失敗") }
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.movielist.LinkMovieListViewModelTest"`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt
git commit -m "feat: add isFilterSwitching state to LinkMovieListViewModel"
```

---

### Task 3: Update MovieListScreen UI logic

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt`

- [ ] **Step 1: Update when block condition and isRefreshing**

In `MovieListScreen.kt`, change the `PullToRefreshBox` `isRefreshing` parameter (line 41):

From:
```kotlin
isRefreshing = uiState.isRefreshing,
```
To:
```kotlin
isRefreshing = uiState.isRefreshing || uiState.isFilterSwitching,
```

Change the `when` block's first condition (line 46):

From:
```kotlin
uiState.isLoading -> {
```
To:
```kotlin
uiState.isLoading && uiState.movies.isEmpty() -> {
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt
git commit -m "feat: show top refresh indicator during filter switch in MovieListScreen"
```

---

### Task 4: Update LinkMovieListScreen UI logic

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt`

- [ ] **Step 1: Update when block condition and isRefreshing**

In `LinkMovieListScreen.kt`, change the `PullToRefreshBox` `isRefreshing` parameter (line 126):

From:
```kotlin
isRefreshing = uiState.isRefreshing,
```
To:
```kotlin
isRefreshing = uiState.isRefreshing || uiState.isFilterSwitching,
```

Change the `when` block's first condition (line 133):

From:
```kotlin
uiState.isLoading -> {
```
To:
```kotlin
uiState.isLoading && uiState.movies.isEmpty() -> {
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt
git commit -m "feat: show top refresh indicator during filter switch in LinkMovieListScreen"
```

---

### Task 5: Run all tests and verify

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 2: Install and manually verify**

Run: `./gradlew installDebug`

Checklist:
1. Open app, go to a movie list with filter bar
2. Toggle "已有磁力" → "全部影片": list should stay visible, top refresh indicator should appear briefly, then content replaces
3. Toggle back: same behavior
4. Pull-to-refresh: should work as before
5. Navigate to actress detail page with filter bar, repeat steps 2-4
6. Initial load should still show full-screen spinner
