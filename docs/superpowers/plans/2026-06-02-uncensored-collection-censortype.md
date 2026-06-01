# Uncensored Collection censorType Propagation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Propagate `censorType` through all navigation entry points so that `MovieDetailViewModel` receives it and uses it alongside genre detection when collecting movies, fixing incorrect uncensored categorization.

**Architecture:** Add a `censorType: String?` parameter to `RouteLinkMovies` (already exists on `RouteMovieDetail`). Change all `onMovieClick`, `onActressClick`, `onGenreClick` callback signatures from `(XUiModel) -> Unit` to `(XUiModel, String?) -> Unit`. Each screen infers `censorType` from its context (DataSourceType, SearchType, inherited nav argument, or URL path for deep links) and passes it downstream. `MovieDetailViewModel` already supports `censorType` — no ViewModel changes needed.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation 3, Hilt

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `NavigationKeys.kt` | Modify | Add `censorType` field to `RouteLinkMovies` |
| `MainScreen.kt` | Modify | Update callback signatures; infer `censorType` from `CensorFilter` in pagers |
| `MovieListScreen.kt` | Modify | Update `onMovieClick` signature |
| `components/MovieList.kt` | Modify | Update `onMovieClick` signature |
| `components/ActressGrid.kt` | Modify | Update `onActressClick` signature |
| `LinkMovieListScreen.kt` | Modify | Accept `censorType`; pass through to `onMovieClick` |
| `SearchScreen.kt` | Modify | Infer `censorType` from `SearchType`; update callback signatures |
| `MovieDetailScreen.kt` | Modify | Accept `censorType`; pass to ViewModel; forward to sub-clicks |
| `Navigation.kt` | Modify | Wire `censorType` through all `entry` blocks |
| `CollectCategoryScreen.kt` | Modify | Update callback signatures |
| `CollectionListScreen.kt` | Modify | Update callback signatures |
| `ActressListScreen.kt` | Modify | Update callback signatures |
| `ModernMainActivity.kt` | Modify | Infer `censorType` from URL path for deep links |

**Files NOT changed:** `MovieDetailViewModel.kt` (already supports `censorType`), `Movie.kt`, `MovieUiModel`, `LinkItem`, `Category`.

---

### Task 1: Add `censorType` to `RouteLinkMovies`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt:27-32`

- [ ] **Step 1: Add the `censorType` field**

In `NavigationKeys.kt`, change the `RouteLinkMovies` data class at line 27:

```kotlin
@Serializable
data class RouteLinkMovies(
    val linkUrl: String,
    val title: String = "",
    val type: String = "",
    val avatar: String = "",
    val censorType: String? = null
) : NavKey
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt
git commit -m "feat: add censorType parameter to RouteLinkMovies"
```

---

### Task 2: Update `onMovieClick` signature in `MovieList` component

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt:66`

This is the leaf-level component. Update its signature so all consumers can pass `censorType`.

- [ ] **Step 1: Change the `onMovieClick` parameter and all call sites**

In `MovieList.kt`, change the function signature at line 66:

```kotlin
onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
```

Then update all call sites inside `MovieList` that invoke `onMovieClick`:

Line 101 (grid mode):
```kotlin
MovieGridItem(movie = movie, onClick = { onMovieClick(movie, null) })
```

Line 155 (compact list mode):
```kotlin
CompactMovieItem(
    movie = movie,
    onClick = { onMovieClick(movie, null) },
    isCollected = isCollected?.invoke(movie) == true,
    onToggleCollect = if (onToggleCollect != null) {{ onToggleCollect(movie) }} else null
)
```

Line 160 (normal list mode):
```kotlin
MovieItem(movie = movie, onClick = { onMovieClick(movie, null) })
```

Note: The `MovieList` component itself does not know `censorType` — callers that need to pass it will wrap the callback. The `null` here is a safe default for cases where `MovieList` is used without censor context (e.g., collection screen).

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt
git commit -m "refactor: update MovieList onMovieClick signature to accept censorType"
```

---

### Task 3: Update `onActressClick` signature in `ActressGrid` component

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/ActressGrid.kt:52`

- [ ] **Step 1: Change the `onActressClick` parameter and call site**

In `ActressGrid.kt`, change the function signature at line 52:

```kotlin
onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
```

Update the call site at line 84:

```kotlin
onClick = { onActressClick(actress, null) },
```

Same rationale as Task 2 — `ActressGrid` does not know `censorType`, callers wrap the callback.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/ActressGrid.kt
git commit -m "refactor: update ActressGrid onActressClick signature to accept censorType"
```

---

### Task 4: Update `MovieListScreen` callback signature

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt:30`

- [ ] **Step 1: Change `onMovieClick` parameter**

In `MovieListScreen.kt`, change line 30:

```kotlin
onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
```

The call site at line 80 already passes `onMovieClick` directly to `MovieList`. Since both now have the same signature `(MovieUiModel, String?) -> Unit`, no additional change is needed at the call site.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt
git commit -m "refactor: update MovieListScreen onMovieClick signature"
```

---

### Task 5: Update `ActressListScreen` callback signature

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/ActressListScreen.kt:33`

- [ ] **Step 1: Change `onActressClick` parameter**

In `ActressListScreen.kt`, change line 33:

```kotlin
onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
```

The call site at line 66 passes `onActressClick` directly to `ActressGrid`. Since both now have the same signature `(ActressUiModel, String?) -> Unit`, no additional change is needed at the call site.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/ActressListScreen.kt
git commit -m "refactor: update ActressListScreen onActressClick signature"
```

---

### Task 6: Update `CollectionListScreen` callback signatures

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt:29-30`

- [ ] **Step 1: Change callback parameters**

In `CollectionListScreen.kt`, change lines 29-30:

```kotlin
onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
```

The call sites at lines 71 and 91 pass these directly to `MovieList` and `ActressGrid` respectively. Signatures now match — no additional changes needed.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt
git commit -m "refactor: update CollectionListScreen callback signatures"
```

---

### Task 7: Update `CollectCategoryScreen` callback signatures

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt:57-58`

- [ ] **Step 1: Change callback parameters**

In `CollectCategoryScreen.kt`, change lines 57-58:

```kotlin
onMovieClick: (MovieUiModel, String?) -> Unit,
onActressClick: (ActressUiModel, String?) -> Unit,
```

The call sites at lines 221-222 pass these directly to `CollectionListScreen`. Signatures now match — no additional changes needed.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt
git commit -m "refactor: update CollectCategoryScreen callback signatures"
```

---

### Task 8: Update `LinkMovieListScreen` — accept `censorType` and wire it through

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt:73-82`

This screen receives `censorType` from navigation and passes it when clicking movies.

- [ ] **Step 1: Add `censorType` parameter and update `onMovieClick` signature**

Change the function signature at line 73:

```kotlin
fun LinkMovieListScreen(
    linkUrl: String,
    title: String = "",
    type: String = "",
    avatarUrl: String = "",
    censorType: String? = null,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    ...
```

- [ ] **Step 2: Pass `censorType` when clicking movies**

At line 220, change the `MovieList` call to wrap `onMovieClick`:

```kotlin
MovieList(
    movies = uiState.movies,
    hasMore = uiState.hasMore,
    isLoadingMore = uiState.isLoadingMore,
    onLoadMore = { viewModel.loadMore() },
    onMovieClick = { movie -> onMovieClick(movie, censorType) },
    isGrid = isGrid,
    modifier = Modifier.fillMaxSize(),
    header = header
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt
git commit -m "feat: LinkMovieListScreen accepts and forwards censorType"
```

---

### Task 9: Update `SearchScreen` — infer `censorType` from `SearchType`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt:75-76`

- [ ] **Step 1: Change callback signatures**

At lines 75-76, change:

```kotlin
onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
```

- [ ] **Step 2: Infer `censorType` and wrap callbacks**

Inside the `SearchScreen` function body, after the `uiState` val (around line 81), add:

```kotlin
val censorType = when (uiState.searchType) {
    SearchType.UNCENSORED -> "UNCENSORED"
    else -> null
}
```

Then at line 349-366 where `ActressGrid` and `MovieList` are called, wrap the callbacks:

For `ActressGrid` (line 349):
```kotlin
ActressGrid(
    actresses = uiState.actressResults,
    hasMore = uiState.hasMore,
    isLoadingMore = uiState.isLoadingMore,
    onLoadMore = { viewModel.loadMore() },
    onActressClick = { actress -> onActressClick(actress, censorType) },
    modifier = dismissKeyboardModifier
)
```

For `MovieList` (line 358):
```kotlin
MovieList(
    movies = uiState.results,
    hasMore = uiState.hasMore,
    isLoadingMore = uiState.isLoadingMore,
    onLoadMore = { viewModel.loadMore() },
    onMovieClick = { movie -> onMovieClick(movie, censorType) },
    isGrid = isGrid,
    modifier = dismissKeyboardModifier
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt
git commit -m "feat: SearchScreen infers censorType from SearchType"
```

---

### Task 10: Update `MovieDetailScreen` — accept `censorType`, pass to ViewModel, forward to sub-clicks

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt:105-121`

This is the most critical change. The screen receives `censorType` from navigation and uses it for:
1. Passing to ViewModel via `loadDetail`
2. Forwarding to all sub-clicks (related movies, actresses, genres)

- [ ] **Step 1: Add `censorType` parameter and update callback signatures**

Change the function signature at lines 105-113:

```kotlin
fun MovieDetailScreen(
    movieUrl: String,
    censorType: String? = null,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
    onGenreClick: (GenreUiModel, String?) -> Unit = { _, _ -> },
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onHeaderClick: (HeaderUiModel, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: MovieDetailViewModel = hiltViewModel()
)
```

Note: `onHeaderClick` also gets `censorType` because headers (director, studio, etc.) navigate to `RouteLinkMovies` which needs it.

- [ ] **Step 2: Pass `censorType` to ViewModel**

Change line 119-121:

```kotlin
LaunchedEffect(movieUrl, censorType) {
    viewModel.loadDetail(movieUrl, censorType)
}
```

Also update the retry call at line 198 (inside `ErrorView`):

```kotlin
onRetry = { viewModel.loadDetail(movieUrl, censorType) }
```

- [ ] **Step 3: Forward `censorType` to `DetailContent` and its sub-clicks**

Change the `DetailContent` call at lines 203-216 to wrap callbacks with `censorType`:

```kotlin
DetailContent(
    detail = detail,
    padding = PaddingValues(),
    onMovieClick = { movie -> onMovieClick(movie, censorType) },
    onActressClick = { actress -> onActressClick(actress, censorType) },
    onGenreClick = { genre -> onGenreClick(genre, censorType) },
    onHeaderClick = { header -> onHeaderClick(header, censorType) },
    onImageClick = onImageClick,
    onMagnetClick = {
        showMagnetSheet = true
    },
    isLoadingMagnets = uiState.isLoadingMagnets,
    hasMagnets = uiState.magnets.isNotEmpty()
)
```

- [ ] **Step 4: Update `DetailContent` internal callbacks to match new signatures**

The `DetailContent` private function (line 247) has its own callback parameters. Update their signatures:

```kotlin
private fun DetailContent(
    detail: MovieDetailUiModel,
    padding: PaddingValues,
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onGenreClick: (GenreUiModel) -> Unit,
    onHeaderClick: (HeaderUiModel) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onMagnetClick: () -> Unit,
    isLoadingMagnets: Boolean = false,
    hasMagnets: Boolean = false
)
```

These stay single-argument because `MovieDetailScreen` wraps them with `censorType` in Step 3 — the wrapping happens at the call site, so `DetailContent` and all its internal sections (`GenreSection`, `ActressSection`, `RelatedMovieSection`) remain unchanged.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt
git commit -m "feat: MovieDetailScreen accepts censorType, passes to ViewModel and sub-clicks"
```

---

### Task 11: Update `MainScreen` — infer `censorType` from `CensorFilter` and update callback signatures

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt:79-85,247-285,320-339,342-346`

This is the largest change. `MainScreen` is where `CensorFilter` is known and must be converted to `censorType`.

- [ ] **Step 1: Update `MainScreen` function signature**

Change lines 79-85:

```kotlin
fun MainScreen(
    onMovieClick: (MovieUiModel, String?) -> Unit,
    onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
    onGenreClick: (GenreUiModel, String?) -> Unit = { _, _ -> },
    onSearchClick: (String) -> Unit = {},
    onForumBoardClick: (me.jbusdriver.modern.domain.model.ForumBoard) -> Unit = {},
    onForumThreadClick: (Int) -> Unit = {}
)
```

- [ ] **Step 2: Wrap movie clicks in the movie pager**

Inside the movie `HorizontalPager` (around lines 262-284), both the genre-url branch and the normal branch need to wrap `onMovieClick`:

For the genre-url branch (line 265):
```kotlin
val censorType = when (filter) {
    CensorFilter.UNCENSORED -> "UNCENSORED"
    else -> null
}
MovieListScreen(
    active = true,
    onMovieClick = { movie -> onMovieClick(movie, censorType) },
    modifier = Modifier.fillMaxSize(),
    viewModel = genreVm
)
```

For the normal branch (line 277):
```kotlin
val censorType = when (filter) {
    CensorFilter.UNCENSORED -> "UNCENSORED"
    else -> null
}
val vm: MovieListViewModel = hiltViewModel(key = "pager_$filter")
MovieListScreen(
    dataSourceType = dataSourceType,
    active = true,
    onMovieClick = { movie -> onMovieClick(movie, censorType) },
    modifier = Modifier.fillMaxSize(),
    viewModel = vm
)
```

Note: compute `censorType` once at the top of the pager page block and use it for both branches.

- [ ] **Step 3: Wrap actress clicks in the actress pager**

Inside the actress `HorizontalPager` (around line 326), wrap `onActressClick`:

```kotlin
val actressCensorType = when (filter) {
    CensorFilter.UNCENSORED -> "UNCENSORED"
    else -> null
}
ActressListScreen(
    dataSourceType = actressType,
    active = true,
    onActressClick = { actress -> onActressClick(actress, actressCensorType) },
    modifier = Modifier.fillMaxSize(),
    viewModel = vm
)
```

- [ ] **Step 4: Update `CollectCategoryScreen` call**

At line 342, pass `null` for censorType since collection items already have `categoryId`:

```kotlin
BottomNavCategory.COLLECT -> CollectCategoryScreen(
    onMovieClick = { movie, _ -> onMovieClick(movie, null) },
    onActressClick = { actress, _ -> onActressClick(actress, null) },
    onGoHome = { selectedCategory = BottomNavCategory.MOVIE }
)
```

Wait — `CollectCategoryScreen` now has `(MovieUiModel, String?) -> Unit` callbacks. But in the `Navigation.kt` entry, we need to route from here. The `MainScreen` simply passes its own `onMovieClick` down. The key insight: `CollectCategoryScreen` receives `(MovieUiModel, String?) -> Unit` from `MainScreen`, and passes it through to `CollectionListScreen`. When a collection movie is clicked, the `censorType` passed will be whatever the `MovieList` inside `CollectionListScreen` provides — which is `null` (since `MovieList` passes `null` by default). This is correct: collection items already have their `categoryId` set.

So the collect section at line 342 simply becomes:

```kotlin
BottomNavCategory.COLLECT -> CollectCategoryScreen(
    onMovieClick = onMovieClick,
    onActressClick = onActressClick,
    onGoHome = { selectedCategory = BottomNavCategory.MOVIE }
)
```

No wrapping needed — signatures already match.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt
git commit -m "feat: MainScreen infers censorType from CensorFilter and passes to callbacks"
```

---

### Task 12: Wire `censorType` through all `Navigation.kt` entry blocks

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt:109-273`

This is the central wiring task. Every `entry` block must pass `censorType` through to routes and screens.

- [ ] **Step 1: Update `entry<RouteMain>`**

Lines 110-139. Change callbacks:

```kotlin
entry<RouteMain> {
    MainScreen(
        onMovieClick = { movie, censorType ->
            backStack.add(RouteMovieDetail(movie.link, censorType))
        },
        onActressClick = { actress, censorType ->
            backStack.add(
                RouteLinkMovies(
                    actress.link,
                    actress.name,
                    type = "actress",
                    avatar = actress.avatar,
                    censorType = censorType
                )
            )
        },
        onGenreClick = { genre, censorType ->
            backStack.add(
                RouteLinkMovies(genre.link, genre.name, type = "genre", censorType = censorType)
            )
        },
        onSearchClick = { searchType ->
            backStack.add(RouteSearch(searchType))
        },
        onForumBoardClick = { board ->
            backStack.add(RouteForumThreadList(board.id, board.name, board.typeId))
        },
        onForumThreadClick = { tid ->
            backStack.add(RouteForumThreadDetail(tid))
        }
    )
}
```

- [ ] **Step 2: Update `entry<RouteSearch>`**

Lines 174-192. Change callbacks:

```kotlin
) { key ->
    SearchScreen(
        defaultSearchType = key.defaultSearchType,
        onMovieClick = { movie, censorType ->
            backStack.add(RouteMovieDetail(movie.link, censorType))
        },
        onActressClick = { actress, censorType ->
            backStack.add(
                RouteLinkMovies(
                    actress.link,
                    actress.name,
                    type = "actress",
                    avatar = actress.avatar,
                    censorType = censorType
                )
            )
        },
        onBack = { backStack.removeLastOrNull() },
        onLabSettingsClick = { backStack.add(RouteLabSettings) }
    )
}
```

- [ ] **Step 3: Update `entry<RouteMovieDetail>`**

Lines 194-230. Pass `censorType` to `MovieDetailScreen` and forward it to sub-clicks:

```kotlin
entry<RouteMovieDetail> { key ->
    MovieDetailScreen(
        movieUrl = key.movieUrl,
        censorType = key.censorType,
        onMovieClick = { movie, censorType ->
            backStack.add(RouteMovieDetail(movie.link, censorType))
        },
        onImageClick = { images, startIndex ->
            backStack.add(RouteImageViewer(images, startIndex))
        },
        onActressClick = { actress, censorType ->
            backStack.add(
                RouteLinkMovies(
                    actress.link,
                    actress.name,
                    type = "actress",
                    avatar = actress.avatar,
                    censorType = censorType
                )
            )
        },
        onGenreClick = { genre, censorType ->
            backStack.add(
                RouteLinkMovies(genre.link, genre.name, type = "genre", censorType = censorType)
            )
        },
        onHeaderClick = { header, censorType ->
            if (header.link.isNotBlank()) {
                backStack.add(
                    RouteLinkMovies(
                        header.link,
                        header.name + ": " + header.value,
                        type = "header",
                        censorType = censorType
                    )
                )
            }
        },
        onBack = { backStack.removeLastOrNull() }
    )
}
```

- [ ] **Step 4: Update `entry<RouteLinkMovies>`**

Lines 239-249. Pass `censorType` from route key to screen and forward:

```kotlin
entry<RouteLinkMovies> { key ->
    LinkMovieListScreen(
        linkUrl = key.linkUrl,
        title = key.title,
        type = key.type,
        avatarUrl = key.avatar,
        censorType = key.censorType,
        onMovieClick = { movie, censorType ->
            backStack.add(RouteMovieDetail(movie.link, censorType))
        },
        onBack = { backStack.removeLastOrNull() }
    )
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt
git commit -m "feat: wire censorType through all Navigation entry blocks"
```

---

### Task 13: Update `ModernMainActivity` deep link handling

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt:77-103`

- [ ] **Step 1: Infer `censorType` from URL path and pass to routes**

In `resolveJavbusRoute`, add a helper to detect uncensored URLs and pass `censorType` to both `RouteMovieDetail` and `RouteLinkMovies`.

Change the function at line 77:

```kotlin
private fun resolveJavbusRoute(url: String): NavKey {
    val path = java.net.URL(url).path.orEmpty().trimEnd('/')
    val segments = path.split("/").filter { it.isNotBlank() }
    val isUncensored = "/uncensored/" in url || path.startsWith("/uncensored")
    val censorType = if (isUncensored) "UNCENSORED" else null

    if (segments.isEmpty() || segments.singleOrNull() in listOf("uncensored", "xyz")) {
        return RouteMain
    }
    if (segments.last() in listOf("genre", "actresses")) {
        return RouteMain
    }
    if (segments.size == 1) {
        return RouteMovieDetail(movieUrl = url, censorType = censorType)
    }

    val subPath = if (segments[0] in listOf("uncensored", "xyz")) segments[1] else segments[0]
    if (subPath in listOf("star", "genre", "director", "studio", "label", "series", "publisher")) {
        val type = when (subPath) {
            "star" -> "actress"
            "genre" -> "genre"
            else -> "header"
        }
        val title = segments.last()
        return RouteLinkMovies(linkUrl = url, title = title, type = type, censorType = censorType)
    }

    return RouteMain
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt
git commit -m "feat: infer censorType from URL path in deep link handling"
```

---

### Task 14: Build verification

- [ ] **Step 1: Run debug build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no compilation errors. All callback signature changes should compile cleanly.

- [ ] **Step 2: Fix any compilation errors**

If any file has a signature mismatch (e.g., a call site that still uses the old single-argument callback), fix it by adding the `String?` parameter.

Common patterns for fixes:
- Where `(MovieUiModel) -> Unit` was passed directly, now needs `(MovieUiModel, String?) -> Unit`
- If a lambda `{ movie -> ... }` is used where the new signature expects two params, change to `{ movie, censorType -> ... }`
- If defaulting is desired, use `{ movie, _ -> ... }`

---

## Self-Review Checklist

### Spec Coverage

| Spec Requirement | Task |
|-----------------|------|
| Add `censorType` to `RouteLinkMovies` | Task 1 |
| Update all `onMovieClick` signatures | Tasks 2, 4, 6, 7, 8, 9, 10, 11 |
| Update all `onActressClick` signatures | Tasks 3, 5, 6, 7, 9, 10, 11 |
| Update all `onGenreClick` signatures | Tasks 10, 11 |
| Navigation wires `censorType` | Task 12 |
| MainScreen infers from CensorFilter | Task 11 |
| SearchScreen infers from SearchType | Task 9 |
| MovieDetailScreen accepts and forwards | Task 10 |
| LinkMovieListScreen accepts and forwards | Task 8 |
| Deep link infers from URL path | Task 13 |
| MovieDetailViewModel unchanged | ✓ confirmed |
| Build passes | Task 14 |

### Placeholder Scan

No TBD, TODO, or "implement later" patterns found.

### Type Consistency

- `censorType` is consistently `String?` across all signatures
- Value is always `"UNCENSORED"` or `null` — no other string values used
- Callback signatures are `(XUiModel, String?) -> Unit` with default `{ _, _ -> }` consistently
