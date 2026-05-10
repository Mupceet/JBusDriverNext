# Filter Switch Loading UX Design

## Overview

When toggling between "已有磁力" and "全部影片", the current behavior clears the entire list and shows a full-screen loading spinner. Since the list already has content, this transition is jarring. The new behavior keeps the old list visible and shows a lightweight top refresh indicator instead.

## Current Flow (Problem)

1. User taps filter toggle
2. `toggleShowAll()` clears `movies = emptyList()` and sets `isLoading = true`
3. Screen's `when` block matches `isLoading`, shows full-screen spinner
4. New data arrives, list reappears

## New Flow (Target)

1. User taps filter toggle
2. `toggleShowAll()` keeps old movies, sets `isFilterSwitching = true`
3. Screen shows the list with `PullToRefreshBox`'s top indicator spinning
4. New data arrives, list content is replaced in place, indicator disappears

## Changes

### ViewModels

Both `MovieListViewModel` and `LinkMovieListViewModel` need identical changes.

**UiState** — add field:
```kotlin
val isFilterSwitching: Boolean = false
```

**toggleShowAll()** — change from:
```kotlin
fun toggleShowAll() {
    val newState = !_uiState.value.showAll
    _uiState.update { it.copy(showAll = newState, movies = emptyList()) }
    currentPage = 0
    loadFirstPage()
}
```
to:
```kotlin
fun toggleShowAll() {
    val newState = !_uiState.value.showAll
    _uiState.update { it.copy(showAll = newState, isFilterSwitching = true) }
    currentPage = 0
    loadFirstPage()
}
```

No longer clears movies. Uses `isFilterSwitching` instead of relying on `isLoading`.

**loadFirstPage()** — the loading/success/error handlers must also reset `isFilterSwitching = false` alongside `isLoading = false`.

For `MovieListViewModel`: update `loadMovies()` lambdas to include `isFilterSwitching = false` in success and error state updates.

For `LinkMovieListViewModel`: update `loadFirstPage()` success and catch blocks to include `isFilterSwitching = false`.

### Screens

Both `MovieListScreen` and `LinkMovieListScreen` need identical changes.

**when block** — change from:
```kotlin
when {
    uiState.isLoading -> { /* full screen spinner */ }
    uiState.error != null && uiState.movies.isEmpty() -> { /* error */ }
    else -> { /* list */ }
}
```
to:
```kotlin
when {
    uiState.isLoading && uiState.movies.isEmpty() -> { /* full screen spinner, first load only */ }
    uiState.error != null && uiState.movies.isEmpty() -> { /* error */ }
    else -> { /* list, shown even during filter switch */ }
}
```

**PullToRefreshBox** — change `isRefreshing` from:
```kotlin
isRefreshing = uiState.isRefreshing,
```
to:
```kotlin
isRefreshing = uiState.isRefreshing || uiState.isFilterSwitching,
```

This makes the top refresh indicator spin during filter switching.

## Files Changed

| File | Change |
|------|--------|
| `ui/movielist/MovieListViewModel.kt` | Add `isFilterSwitching` to state, modify `toggleShowAll()` and loading handlers |
| `ui/movielist/LinkMovieListViewModel.kt` | Same changes |
| `ui/movielist/MovieListScreen.kt` | Modify `when` condition and `isRefreshing` |
| `ui/movielist/LinkMovieListScreen.kt` | Same changes |

## Scope

- Only affects the magnet filter toggle ("已有磁力" / "全部影片")
- Pull-to-refresh gesture behavior unchanged
- Initial load still shows full-screen spinner
- Error handling unchanged
