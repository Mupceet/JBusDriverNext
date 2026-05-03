# MovieList Grid Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a grid layout mode to MovieList with vertical cards (image-above-text), using LazyVerticalGrid with adaptive column sizing, and merge MovieItem.kt into MovieList.kt.

**Architecture:** Merge MovieItem.kt and MovieList.kt into a single MovieList.kt containing MovieList, MovieItem (existing list card), and MovieGridItem (new grid card). MovieList gains an `isGrid` parameter to switch between LazyColumn and LazyVerticalGrid. LinkMovieListScreen is refactored to use MovieList instead of hand-rolling its own LazyColumn.

**Tech Stack:** Jetpack Compose, LazyVerticalGrid, FlowRow, Material3

---

### Task 1: Merge MovieItem.kt into MovieList.kt

Merge the two component files into one, preserving all existing behavior.

**Files:**
- Delete: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieItem.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt` (update import)

- [ ] **Step 1: Write merged MovieList.kt**

Replace `MovieList.kt` with the merged content containing both `MovieList` and `MovieItem` composables. The file becomes:

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.jbusdriver.modern.ui.MovieUiModel

/**
 * 可复用的影片列表组件。
 *
 * @param isGrid false = LazyColumn 列表模式, true = LazyVerticalGrid 网格模式
 */
@Composable
fun MovieList(
    movies: List<MovieUiModel>,
    hasMore: Boolean = true,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onMovieClick: (MovieUiModel) -> Unit = {},
    isGrid: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
            MovieItem(movie = movie, onClick = { onMovieClick(movie) })
        }
        if (isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (!hasMore && movies.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("没有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 影片列表中的单个影片条目卡片（横排，左图右文）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieItem(
    movie: MovieUiModel,
    onClick: (MovieUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onClick(movie) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
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
                    .width(80.dp)
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
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (movie.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        movie.tags.forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = movie.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (movie.date.isNotBlank()) {
                        Text(
                            text = movie.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Delete old MovieItem.kt**

```bash
git rm app/src/main/java/me/jbusdriver/modern/ui/components/MovieItem.kt
```

- [ ] **Step 3: Update LinkMovieListScreen.kt import**

In `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt`, the import at line 53 is already `import me.jbusdriver.modern.ui.components.MovieItem`. Since both files are in the same package (`me.jbusdriver.modern.ui.components`), the import still works — no change needed. But the `KDoc` reference in MovieItem needs updating:

In `MovieList.kt` (the merged file), update the MovieItem KDoc reference from `[me.jbusdriver.modern.ui.movielist.LinkMovieListScreen]` to `[LinkMovieListScreen]` — actually this reference was in the old MovieItem.kt. In the merged file above, the KDoc is simplified and no longer references it externally, so this is already handled.

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (no compile errors from the merge)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt
git rm app/src/main/java/me/jbusdriver/modern/ui/components/MovieItem.kt
git commit -m "refactor: merge MovieItem.kt into MovieList.kt"
```

---

### Task 2: Add MovieGridItem composable

Add the new grid card composable to the merged MovieList.kt.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`

- [ ] **Step 1: Add MovieGridItem composable**

Append the following composable at the end of `MovieList.kt`, after the existing `MovieItem` function:

```kotlin
/**
 * 网格模式下的影片卡片（竖排，上图下文）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieGridItem(
    movie: MovieUiModel,
    onClick: (MovieUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onClick(movie) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            AsyncImage(
                model = movie.imageUrl,
                contentDescription = movie.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (movie.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        movie.tags.forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = movie.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (movie.date.isNotBlank()) {
                        Text(
                            text = movie.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
```

No new imports needed — all are already present from MovieItem.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt
git commit -m "feat: add MovieGridItem composable for grid layout mode"
```

---

### Task 3: Add LazyVerticalGrid branch to MovieList

Wire up the `isGrid` parameter in MovieList to switch between LazyColumn and LazyVerticalGrid.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`

- [ ] **Step 1: Add LazyVerticalGrid imports**

Add these imports to the import block in `MovieList.kt`:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
```

- [ ] **Step 2: Replace MovieList body with isGrid branching**

Replace the entire `MovieList` composable function with:

```kotlin
/**
 * 可复用的影片列表组件。
 *
 * @param isGrid false = LazyColumn 列表模式, true = LazyVerticalGrid 网格模式
 */
@Composable
fun MovieList(
    movies: List<MovieUiModel>,
    hasMore: Boolean = true,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onMovieClick: (MovieUiModel) -> Unit = {},
    isGrid: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isGrid) {
        val gridState = rememberLazyGridState()

        LaunchedEffect(gridState) {
            snapshotFlow {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = gridState.layoutInfo.totalItemsCount
                lastVisible >= totalItems - 3
            }.collect { nearEnd ->
                if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            state = gridState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
                MovieGridItem(movie = movie, onClick = { onMovieClick(movie) })
            }
            if (isLoadingMore) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (!hasMore && movies.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("没有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } else {
        val listState = rememberLazyListState()

        LaunchedEffect(listState) {
            snapshotFlow {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                lastVisible >= totalItems - 3
            }.collect { nearEnd ->
                if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
        ) {
            itemsIndexed(movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
                MovieItem(movie = movie, onClick = { onMovieClick(movie) })
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (!hasMore && movies.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("没有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
```

Note: The grid branch uses `GridItemSpan(maxLineSpan)` for footer items (loading indicator and "no more" text) so they span the full width instead of a single column.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt
git commit -m "feat: add isGrid parameter to MovieList with LazyVerticalGrid support"
```

---

### Task 4: Refactor LinkMovieListScreen to use MovieList

LinkMovieListScreen currently hand-rolls its own LazyColumn with MovieItem. Refactor it to use the MovieList component, passing the actress header as content before the list.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt`

- [ ] **Step 1: Rewrite LinkMovieListScreen's else branch**

In `LinkMovieListScreen.kt`, replace the `else` block (lines 150–209) that contains the hand-rolled LazyColumn. The actress header items need to stay outside MovieList because MovieList doesn't know about them. The approach: keep the outer `PullToRefreshBox` → `when` structure, but replace the LazyColumn with a Column containing the actress header + MovieList:

Replace lines 150–209 (the entire `else { ... }` block) with:

```kotlin
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Actress detail header (outside MovieList)
                        if (type == "actress") {
                            val actress = uiState.actressDetail
                            val actressError = uiState.actressError
                            when {
                                actress != null -> ActressDetailCard(actress)
                                uiState.isLoadingActress -> ActressDetailLoadingPlaceholder()
                                actressError != null -> ActressDetailErrorCard(actressError)
                            }
                        }

                        MovieList(
                            movies = uiState.movies,
                            hasMore = uiState.hasMore,
                            isLoadingMore = uiState.isLoadingMore,
                            onLoadMore = { viewModel.loadMore() },
                            onMovieClick = onMovieClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
```

- [ ] **Step 2: Clean up unused imports**

Remove these now-unused imports from `LinkMovieListScreen.kt`:

```kotlin
// Remove these:
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import me.jbusdriver.modern.ui.components.MovieItem
```

The `PaddingValues` import can stay since it might be used elsewhere in the file (check first — it's used only in the removed LazyColumn, so remove it too):

```kotlin
// Also remove:
import androidx.compose.foundation.layout.PaddingValues
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt
git commit -m "refactor: LinkMovieListScreen uses MovieList component instead of hand-rolled LazyColumn"
```

---

### Task 5: Visual verification

Manually verify both list and grid modes render correctly.

**Files:** None (manual testing)

- [ ] **Step 1: Test list mode (isGrid = false)**

Temporarily set `isGrid = true` in one consumer (e.g., MovieListScreen) for testing:

In `MovieListScreen.kt`, change the MovieList call to pass `isGrid = true`:

```kotlin
MovieList(
    movies = uiState.movies,
    hasMore = uiState.hasMore,
    isLoadingMore = uiState.isLoadingMore,
    onLoadMore = { viewModel.loadMore() },
    onMovieClick = onMovieClick,
    isGrid = true  // temporary for testing
)
```

- [ ] **Step 2: Build, install, and verify grid renders**

Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`

Open the app, navigate to movie list. Verify:
- Grid cards show cover image on top, text below
- Tags flow right-aligned
- Code and date on same line, left/right aligned
- Columns adapt to screen width (2 columns portrait, more in landscape)
- Scroll and load-more works correctly
- Loading spinner and "no more" text span full width

- [ ] **Step 3: Revert test change and commit**

Revert `isGrid = true` back to `isGrid = false` (or just remove it since false is the default). No commit needed if already reverted.

---

### Task 6: Final cleanup and commit design docs

- [ ] **Step 1: Ensure all consumers compile with isGrid = false (default)**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — all four screens work with default list mode

- [ ] **Step 2: Commit design docs**

```bash
git add docs/superpowers/
git commit -m "docs: add MovieList grid mode design spec and implementation plan"
```
