# Magnet Filter Toggle Design

## Problem

All movie list pages (home tabs, actress filmography, genre/publisher lists) currently show only movies with magnet links. The old JBusDriver project supports switching between "已有磁力" (movies with magnets) and "全部影片" (all movies) via a toggle bar parsed from the server's `.alert-success` HTML element. The new Compose version needs the same capability.

## Goal

Add a toggle bar at the top of every movie list that shows the count of magnet-available vs. all movies, and allows switching between the two modes. The toggle state is sent to the server via the `existmag` Cookie header (already supported by `NetClient`).

## Design

### Data Layer

#### New model: `MovieFilterInfo`

```kotlin
data class MovieFilterInfo(
    val magnetCount: Int,   // e.g. 194
    val totalCount: Int     // e.g. 399
)
```

Defined in `MoviePageResult.kt` alongside existing models.

#### New parser function in `HtmlParser.kt`

```kotlin
fun parseMovieFilterInfo(doc: Document): MovieFilterInfo?
```

Parses the `.alert-success` element:
- Extracts magnet count from `#resultshowmag` text (digits after "已有磁力")
- Extracts total count from `#resultshowall` text (digits after "全部影片")
- Returns `null` if the alert element is absent (page doesn't support filtering)

#### `MoviePageResult` update

Add `filterInfo: MovieFilterInfo? = null` field.

#### `MovieRepository` updates

- `loadPageByUrl(url, page, showAll, forceRefresh)` — add `showAll: Boolean = false` parameter. Pass to `NetClient.fetchDocument(fullUrl, showAll)`. Parse `filterInfo` from doc. Include `showAll` in cache key.
- `loadPage(type, page, showAll, forceRefresh)` — already has `showAll`. Add `filterInfo` parsing.

### ViewModel Layer

#### `LinkMovieListUiState` additions

```kotlin
val showAll: Boolean = false,
val filterInfo: MovieFilterInfo? = null
```

#### `LinkMovieListViewModel` additions

- Store `showAll` in state
- Pass `showAll` to `repository.loadPageByUrl(url, page, showAll)` in load/refresh methods
- `toggleShowAll()`: flip `showAll`, clear movies, reset page, reload

#### `MovieListUiState` additions

Same `showAll` and `filterInfo` fields.

#### `MovieListViewModel` additions

- Same pattern: pass `showAll` to `repository.loadPage(type, page, showAll)`
- `toggleShowAll()` method

### UI Layer

#### New composable: `MovieFilterBar`

Location: `ui/components/MovieFilterBar.kt`

A horizontal toggle bar with two segments:
- Left: "已有磁力 (N)" — highlighted when `showAll == false`
- Right: "全部影片 (N)" — highlighted when `showAll == true`

The active segment gets a filled background (theme primary container), the inactive segment is transparent with muted text. Clicking the inactive segment calls `onToggle`.

#### Integration

**`LinkMovieListScreen`**: Add `MovieFilterBar` inside the `MovieList` header lambda, after the actress detail card (if present). Only shown when `filterInfo != null`.

**`MovieListScreen`**: Add `MovieFilterBar` as the `MovieList` header. Only shown when `filterInfo != null`.

## Files Changed

| File | Change |
|------|--------|
| `domain/model/MoviePageResult.kt` | Add `MovieFilterInfo` data class, `filterInfo` field to `MoviePageResult` |
| `data/parser/HtmlParser.kt` | Add `parseMovieFilterInfo()` function |
| `data/MovieRepository.kt` | Add `showAll` to `loadPageByUrl`, add `filterInfo` parsing to both load methods |
| `ui/movielist/LinkMovieListViewModel.kt` | Add `showAll`, `filterInfo` state + `toggleShowAll()` |
| `ui/movielist/MovieListViewModel.kt` | Add `showAll`, `filterInfo` state + `toggleShowAll()` |
| `ui/components/MovieFilterBar.kt` | New file — toggle bar composable |
| `ui/movielist/LinkMovieListScreen.kt` | Add `MovieFilterBar` to header |
| `ui/movielist/MovieListScreen.kt` | Add `MovieFilterBar` to header |
