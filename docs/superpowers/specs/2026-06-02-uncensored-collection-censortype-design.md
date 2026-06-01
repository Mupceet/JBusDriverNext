# Uncensored Collection censorType Propagation Design

**Date**: 2026-06-02
**Status**: Approved

## Problem

When collecting/favoriting movies, the uncensored (無碼) category is not reliably saved. The `categoryId` (1=censored, 3=uncensored) determines whether a movie appears in the uncensored filter on the collection screen. Currently, detection relies solely on genre tags (`"無碼" in genre.name`), which is unreliable — some uncensored movies lack this tag.

`RouteMovieDetail` already has a `censorType` parameter and `MovieDetailViewModel.loadDetail()` accepts it, but **no navigation entry point passes it**. All 5 entry paths pass `censorType = null`.

## Solution

Propagate `censorType` through the full navigation chain: from the source screen (which knows whether content is censored) through to `MovieDetailViewModel`, where it is used alongside genre detection as a dual signal for the uncensored flag.

**No data model changes** — Movie, MovieUiModel, LinkItem remain unchanged. Only navigation parameters and callback signatures change.

## Detailed Changes

### 1. NavigationKeys.kt

Add `censorType` to `RouteLinkMovies`:

```kotlin
data class RouteLinkMovies(
    val linkUrl: String,
    val title: String = "",
    val type: String = "",
    val avatar: String = "",
    val censorType: String? = null  // NEW
) : NavKey
```

`RouteMovieDetail` already has `censorType: String? = null` — no change needed.

### 2. Callback Signature Changes

All `onMovieClick` callbacks change from `(MovieUiModel) -> Unit` to `(MovieUiModel, String?) -> Unit`.

All `onActressClick` callbacks change from `(ActressUiModel) -> Unit` to `(ActressUiModel, String?) -> Unit`.

All `onGenreClick` callbacks change from `(GenreUiModel) -> Unit` to `(GenreUiModel, String?) -> Unit`.

The second parameter is the optional `censorType` string (`"UNCENSORED"` or `null`).

### 3. Navigation.kt

Each `entry` block passes `censorType` through to routes:

- **`entry<RouteMain>`**: `onMovieClick` passes `censorType` to `RouteMovieDetail`. `onActressClick` and `onGenreClick` pass `censorType` to `RouteLinkMovies`.
- **`entry<RouteSearch>`**: Same pattern — `censorType` flows from callback to routes.
- **`entry<RouteMovieDetail>`**: Passes `key.censorType` to `MovieDetailScreen`. Sub-clicks (related movies, actresses, genres) inherit and forward the same `censorType`.
- **`entry<RouteLinkMovies>`**: Passes `key.censorType` to `LinkMovieListScreen`. `onMovieClick` forwards it.

### 4. MainScreen.kt

Callback signatures updated to include `String?` for `censorType`.

In the `HorizontalPager` for movies, each page captures its `CensorFilter` and maps it:

```kotlin
val censorType = when (filter) {
    CensorFilter.UNCENSORED -> "UNCENSORED"
    else -> null
}
MovieListScreen(
    ...,
    onMovieClick = { movie -> onMovieClick(movie, censorType) }
)
```

Same pattern for actress pager — `actressCensorType` is inferred from `CensorFilter`.

Genre click uses the current `censorFilter` to determine `censorType`.

### 5. MovieListScreen.kt

`onMovieClick` signature changes to `(MovieUiModel, String?) -> Unit`. No other logic changes — it just forwards what MainScreen provides via closure.

### 6. components/MovieList.kt

`onMovieClick` signature changes to `(MovieUiModel, String?) -> Unit`. The call sites pass the movie and the censorType from the caller.

### 7. LinkMovieListScreen.kt

New parameter `censorType: String? = null`. When clicking a movie, passes this through:

```kotlin
onMovieClick = { movie -> onMovieClick(movie, censorType) }
```

### 8. SearchScreen.kt

Infers `censorType` from `SearchType`:

```kotlin
val censorType = when (uiState.searchType) {
    SearchType.UNCENSORED -> "UNCENSORED"
    else -> null
}
```

Passes to `onMovieClick` and `onActressClick` callbacks.

### 9. MovieDetailScreen.kt

New parameter `censorType: String? = null`.

Passes to ViewModel:
```kotlin
LaunchedEffect(movieUrl, censorType) {
    viewModel.loadDetail(movieUrl, censorType)
}
```

Inherits `censorType` to all sub-clicks:
- Related movies: `onMovieClick(movie, censorType)`
- Actress links: `onActressClick(actress, censorType)`
- Genre links: `onGenreClick(genre, censorType)`

### 10. ModernMainActivity.kt (Deep Links)

Infers `censorType` from URL path:
```kotlin
val censorType = if ("/uncensored/" in url) "UNCENSORED" else null
RouteMovieDetail(movieUrl = url, censorType = censorType)
```

### 11. CollectCategoryScreen.kt

Callback signature alignment — if it has `onMovieClick`, update to `(MovieUiModel, String?) -> Unit`. Pass `null` for censorType since collection items already have their `categoryId` set correctly.

## Files NOT Changed

- **`MovieDetailViewModel.kt`** — already supports `censorType` parameter and dual detection logic.
- **`Movie.kt` / `MovieUiModel`** — no model field additions needed.
- **`LinkItem` / `Category`** — storage logic unchanged.
- **`CollectionListViewModel.kt`** — filtering by `categoryId == 3` is correct.

## Test Scenarios

| # | Path | Expected categoryId |
|---|------|-------------------|
| 1 | Censored Tab → collect movie | 1 |
| 2 | Uncensored Tab → collect movie | 3 |
| 3 | Uncensored Tab → Actress → Actress movies → collect | 3 |
| 4 | Uncensored Tab → Genre → Genre movies → collect | 3 |
| 5 | Search (UNCENSORED type) → collect movie | 3 |
| 6 | Movie detail → related movie → collect (inherits censorType) | matches parent |
| 7 | Deep link with `/uncensored/` URL → collect | 3 |
| 8 | Censored movie but genre has "無碼" | 3 (genre fallback) |
