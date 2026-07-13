# Search Screen: Real-Time Local Collection Search

**Date:** 2026-07-12
**Status:** Approved

> **Revision (2026-07-13, commit `7ff9ea9`):** The merged-display model described below (`MovieList.headerMovies`, local rows pinned above online results in one scroll) was **replaced after review** with a simpler, **mutually-exclusive** model that avoids mixing and duplication. Local instant-search results now render **only while the user is composing** — i.e. the current input has not been submitted as an online search (`searchInput.trim() != uiState.query`). Once an online search is submitted, its results **fully replace** the view (no local section, no header). Editing the query again returns to the composing/local phase. `MovieList.headerMovies` was therefore **dropped** (the Task-5 addition reverted; `footerMovies` unchanged). Any text below that describes `headerMovies` or header-slot merging is superseded by this revision.

## Problem

The search screen only searches the remote site, triggered explicitly (Enter / type-chip tap / history-chip tap). Users who have already collected a movie must wait for a network round-trip to find it again, even though the collected data is fully on-device. Collection data is fast to search locally and contains everything needed to render a movie row (code, title, cover, date, tags, detail link), so it can power a real-time, type-as-you-go search that appears above the online results.

## Solution

Add a **local-collection search section** to the search screen that:

- Filters the user's collected movies **in real time as they type** (no submit needed), in-memory, via a normalized substring match on the movie's **code** and **title**.
- Renders the matches at the **top of the results area** (below the search-type chips, above the online results) as a normal movie list, reusing `MovieList` itself as the rendering component (not hand-rolled `MovieItem` calls).
- Is **only active when the selected search-type chip is 有码 (CENSORED) or 无码 (UNCENSORED)**, and is **linked to the chip**: 有码 searches only locally-collected censored movies, 无码 only uncensored — using the exact same `categoryId == 3 ⟺ uncensored` rule the Collection page already uses.

The online search flow is untouched (still on-submit). Local search is a separate, purely-derived reactive pipeline with no network and no DB schema change.

## Decisions (confirmed during brainstorming)

| Decision | Choice |
|---|---|
| Match fields | **Code + title** (substring on either) |
| Normalization | Lowercase + strip `-`, `_`, and whitespace; then substring-contains. e.g. `abc123` matches `ABC-123` / `ABC_0123`. Query of only separators → no match. |
| Result layout (revised) | Vertical `MovieList` showing **all** local matches, below the type chips. **Mutually exclusive with online results:** shown only while composing (`searchInput.trim() != uiState.query`); once an online search is submitted, online results fully replace it. |
| Chip gating | Local section appears **only** for `CENSORED` / `UNCENSORED` chips. |
| Chip linkage | `CENSORED` → local censored only (`categoryId != 3`); `UNCENSORED` → local uncensored only (`categoryId == 3`). Matches the Collection page's `filterByCensor`. |
| Rendering (revised) | Local results render via a standalone `MovieList` (with the `本地收藏` header) **only while composing**; online results (plain `MovieList`, no header) fully replace them once an online search is submitted. No `headerMovies` merging. |
| Ordering | Local results sorted by collect time, newest first (`createTime` desc). |

## Censor convention (load-bearing)

Collected movies carry their category id on `MovieUiModel.categoryId` (copied from `LinkItem.categoryId` during mapping). The app's established rule (`CollectionListViewModel.filterByCensor`, lines 368–373) is:

- `categoryId == 3` (`UncensoredMovieCategory`, `Category.kt:44`) ⟺ **uncensored**
- `categoryId != 3` (default `MovieCategory` id 1, and any user-created subcategories) ⟺ **censored**

This spec reuses that exact rule so "有码 / 无码" means the same thing on the search screen as on the Collection page.

## Changes

### New file: `ui/search/LocalMovieSearch.kt`

Pure, unit-testable normalization + matching helpers:

```kotlin
internal fun normalizeSearchText(input: String): String =
    input.lowercase().replace(Regex("[-_\\s]+"), "")

internal fun MovieUiModel.matchesLocal(query: String): Boolean {
    val q = normalizeSearchText(query)
    if (q.isEmpty()) return false // query was only separators / blank
    return normalizeSearchText(code).contains(q) ||
           normalizeSearchText(title).contains(q)
}
```

Optionally add a shared censor helper to avoid the magic `3` drifting between search and collection:

```kotlin
// UiModels.kt
val MovieUiModel.isUncensoredCollected: Boolean get() = categoryId == 3
```

`CollectionListViewModel.filterByCensor` may be migrated to use it (optional, behavior-identical).

### `data/repository/CollectRepository.kt`

Add a Flow counterpart to the existing one-shot `getCollectedLinkItems(dbType)` (interface + impl), backed by `linkItemDao.listAll()` (the only Flow DAO method):

```kotlin
fun observeCollectedLinkItems(dbType: Int): Flow<List<LinkItem>>
```

Impl: `linkItemDao.listAll().map { it.filter { d -> d.dbType == dbType } }.flowOn(Dispatchers.IO)` (wrapped the same way as the existing suspend methods).

### `data/db/LinkMappers.kt`

Extract the `LinkItem → MovieUiModel` mapping that `CollectionListViewModel.loadCollection` currently inlines (lines 166–169), so the new search path reuses it instead of duplicating it:

```kotlin
fun LinkItem.toMovieUiModel(baseUrl: String): MovieUiModel? =
    ((toILink(baseUrl) as? Movie)?.toUiModel())
        ?.copy(createTime = createTime, categoryId = categoryId)
```

`CollectionListViewModel` may be migrated to call it (optional, behavior-identical).

### `ui/search/SearchViewModel.kt`

- **Inject** `CollectRepository` and `SiteConfig` (mirrors `CollectionListViewModel`'s dependencies).
- **Add** a live-input flow that is **decoupled from the committed `uiState.query`** (so real-time typing does not perturb the online-search state machine):

```kotlin
private val liveQuery = MutableStateFlow("")

private val collectedMovies: StateFlow<List<MovieUiModel>> =
    collectRepository.observeCollectedLinkItems(MovieDBType)
        .map { items -> items.mapNotNull { it.toMovieUiModel(siteConfig.baseUrl) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

val localResults: StateFlow<List<MovieUiModel>> =
    combine(collectedMovies, liveQuery, uiState.map { it.searchType }) { items, q, type ->
        val wantUncensored = when (type) {
            SearchType.UNCENSORED -> true        // categoryId == 3
            SearchType.CENSORED   -> false       // categoryId != 3
            else -> return@combine emptyList()   // non-movie chip: no local results
        }
        items
            .filter { it.matchesLocal(q) && (it.categoryId == 3) == wantUncensored }
            .sortedByDescending { it.createTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

fun onSearchInputChanged(text: String) { liveQuery.value = text }
```

- **Clear** `liveQuery` inside the existing `clearSearch()` so the X button hides the local section.
- **Remove** the currently-uncalled `setQuery()` (dead code, per `AGENTS.md`).
- No race-safety machinery needed: `localResults` is pure derived state, no network.

### `ui/components/MovieList.kt`

Add a `headerMovies` parameter, **symmetric to the existing `footerMovies`**, default empty (zero impact on other callers: Collection, LinkMovieList, etc.):

```kotlin
headerMovies: List<MovieUiModel> = emptyList(),
```

Emit it right after the `header` slot and before the main `movies`, in **both** modes, applying `isDownloaded` but not `longPressMenu` (same treatment as `footerMovies`):

- **List mode** (after the `if (header != null) item { header() }` block):
  ```kotlin
  if (headerMovies.isNotEmpty()) {
      itemsIndexed(headerMovies, key = { i, m -> "header_${i}_${m.link}" }) { _, m ->
          MovieItem(movie = m, onClick = { onMovieClick(m, null) },
                    isDownloaded = isDownloaded?.invoke(m) == true)
      }
  }
  ```
- **Grid mode** (after the header `item(span = { GridItemSpan(maxLineSpan) })`), emitting `MovieGridItem` analogously (mirror the `footerMovies` grid block).

### `ui/search/SearchScreen.kt`

- **Collect** `val localResults by viewModel.localResults.collectAsStateWithLifecycle()`.
- **Gate visibility:**
  ```kotlin
  val isMovieChip = uiState.searchType == SearchType.CENSORED ||
                    uiState.searchType == SearchType.UNCENSORED
  val localVisible = isMovieChip &&
                     searchInput.trim().isNotBlank() &&
                     localResults.isNotEmpty()
  ```
- **Drive local search from any input change** (typing, clear, history-chip tap, committed-query sync) with a single entry point:
  ```kotlin
  LaunchedEffect(searchInput) { viewModel.onSearchInputChanged(searchInput) }
  ```
  (The existing `LaunchedEffect(uiState.query)` that syncs the committed query into `searchInput` stays; its update still flows through the above effect.)
- **New `LocalCollectHeader(count: Int)`** composable: a label row `本地收藏 · N 部` (count via plurals), styled like a section header.
- **Local-item click censor** is chip-derived (the filter already guarantees uniformity): `CENSORED → null`, `UNCENSORED → "UNCENSORED"`. The `censorType` used in the restructure below is the existing screen-level val (`SearchScreen.kt:78–81`, already `null` / `"UNCENSORED"` from `uiState.searchType`), so online and local rows share it.
- **Restructure the results `when` (currently lines 209–339):**

  ```kotlin
  val onlineIsMovieList = !uiState.isLoading && uiState.error == null &&
                          !isActress && hasResults

  when {
      // Main case: online movie results exist → one scroll, local pinned at top via headerMovies.
      onlineIsMovieList -> MovieList(
          movies = uiState.results,
          header = if (localVisible) { { LocalCollectHeader(localResults.size) } } else null,
          headerMovies = if (localVisible) localResults else emptyList(),
          hasMore = uiState.hasMore,
          isLoadingMore = uiState.isLoadingMore,
          onLoadMore = { viewModel.loadMore() },
          onMovieClick = { m, _ -> onMovieClick(m, censorType) },
          isGrid = isGrid,
          isDownloaded = { it.code.uppercase() in downloadedCodes },
          modifier = dismissKeyboardModifier
      )

      // Movie chip but no online movie list yet (typing / loading / no-results / error):
      // local section is its own MovieList (internal scroll → never overflows), status below.
      localVisible -> Column(Modifier.fillMaxSize()) {
          MovieList(
              movies = localResults,
              header = { LocalCollectHeader(localResults.size) },
              hasMore = false,
              onMovieClick = { m, _ -> onMovieClick(m, censorType) },
              isGrid = isGrid,
              isDownloaded = { it.code.uppercase() in downloadedCodes },
              modifier = Modifier.weight(1f)
          )
          // Compact status footer for the online branch (the full history UI is NOT
          // repeated here — it stays in the `else` branch below for the empty-query case):
          //   loading → small spinner; error → ErrorView; otherwise → "按回车联网搜索" hint.
          OnlineStatus(uiState)
      }

      // Non-movie chip, or localVisible false → today's behavior, unchanged.
      else -> when {
          uiState.isLoading -> { /* full-screen spinner */ }
          uiState.error != null && !hasResults -> ErrorView(...)
          !hasResults && uiState.query.isBlank() -> { /* history / hint */ }
          !hasResults -> { /* no-results text */ }
          isActress -> ActressGrid(...)
      }
  }
  ```

  Because local is gated on a movie chip, the `isActress` (grid) branch can never co-occur with `localVisible`, so there is no local-section-above-ActressGrid nesting case to handle.

### `app/src/main/res/values*/strings.xml`

- New string `local_collect = "本地收藏"`.
- New plurals `local_collect_count` → `%d 部` (Chinese uses `quantity="other"` only; add `quantity="other"` for `values/` and `values-zh`/`values-zh-rTW` as the existing strings do).

## Data flow

1. User types → `searchInput` updates (existing text-field state).
2. `LaunchedEffect(searchInput)` → `onSearchInputChanged` → `liveQuery`.
3. `combine(collectedMovies, liveQuery, searchType)` re-derives `localResults` (normalize + substring on code/title, plus censor filter from the chip), sorted by `createTime` desc.
4. `collectedMovies` is live on `t_link` (via `listAll()` Flow), so collecting / uncollecting / re-categorizing from elsewhere updates local results automatically.
5. Online search is unchanged; when its movie results arrive, the local section rides as `headerMovies` inside the same `MovieList` (single scroll). Clearing the query (X) empties `liveQuery` → local section disappears.

## Interaction & edge cases

- **Empty collection / empty query / separator-only query** → `localResults` empty → section not shown (falls through to today's behavior).
- **Non-movie chip** (`ACTRESS` / `DIRECTOR` / `MAKER` / `PUBLISHER` / `SERIES`) → no local section at all.
- **Many matches** → fine in both paths: the `onlineIsMovieList` path shares one scroll; the standalone path is a real `MovieList` with `weight(1f)` and scrolls internally. No clipping, no `heightIn` cap needed.
- **Censor consistency** → local results use the same `categoryId == 3 ⟺ uncensored` rule as the Collection page, so a movie shown under 无码 here is the same one shown under 无码 there.
- **Tapping a local result** → reuses the existing `onMovieClick` → `RouteMovieDetail(movieUrl, censorType)` navigation; the detail link is rehydrated to a full URL by `toILink(baseUrl)`, identical to the Collection screen.
- **Race safety** → not needed (no network in the local pipeline). The existing online-search request-identity guards are untouched.
- **Downloaded badge** → local rows honor `isDownloaded` (code in `downloadedCodes`), same as online rows.

## Testing

- **Unit — `LocalMovieSearch`**: `normalizeSearchText` cases (`ABC-123`→`abc123`, `ABC_0123`→`abc0123`, `" a B_c "`→`abc`); `matchesLocal` true/false cases (code substring, title substring, case-insensitive, separator-only query → false, empty title, blank query → false).
- **Unit — `SearchViewModel.localResults`**: inject a fake `CollectRepository` whose `observeCollectedLinkItems` emits a fixed set spanning censored/uncensored; drive `onSearchInputChanged` and `searchType`; assert correct normalization matching, censor filtering per chip, `createTime` desc ordering, and empty result for non-movie chips. Verify it does not touch `uiState.query` / online search.
- **Build**: `./gradlew assembleDebug`. No Gson / R8 / ProGuard / DB-schema changes, so no release smoke test required per `AGENTS.md`.
- **Manual**: type a code fragment and a title fragment; switch 有码 ↔ 无码 and confirm the local subset changes; confirm local updates instantly while typing (no submit); confirm collecting/uncollecting a movie elsewhere reflects on return; confirm local rows navigate to detail and show the downloaded badge correctly in both light and dark themes.

## No changes to

- Online search trigger model (still on-submit), `SearchRepository`, request-identity guards, or paging.
- `t_link` schema, DAO queries (other than consuming the existing `listAll()` Flow), or any Room migration.
- The Collection, LinkMovieList, Actress, Forum, or detail screens (the optional `toMovieUiModel` / `isUncensoredCollected` migrations are behavior-identical and may be skipped to minimize blast radius).
- Navigation routes.

## Out of scope (recorded for later, not in this work)

- Matching against `tags`, actress name, or other fields beyond code + title.
- More aggressive normalization (e.g. leading-zero variants like `ABC-123` ↔ `ABC-0123`, or stripping all punctuation) — `基础归一化` was chosen for predictability.
- DB-side search (DAO `LIKE` / FTS4 / denormalized columns) — only justified if collection sizes grow far beyond the typical range.
- A horizontal local-results strip or a capped "show more" variant (vertical `全部` was chosen).
- Showing local results under non-movie chips (actress/director/maker/publisher/series).
