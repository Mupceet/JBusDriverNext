# Scroll-to-Top FAB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Samsung-style floating scroll-to-top button to all list and grid screens, visible when scrolled past the first item.

**Architecture:** Create a shared `ScrollToTopButton` composable and `rememberIsScrolledPastFirstPage` scroll-detection utility. Integrate into `MovieList` and `ActressGrid` by wrapping their lazy components in a `Box` and overlaying the FAB at `Alignment.BottomCenter`.

**Tech Stack:** Jetpack Compose, Material3

---

### Task 1: Create ScrollToTopButton component

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/components/ScrollToTopButton.kt`

- [ ] **Step 1: Create the new file with button composable and scroll detection utilities**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .size(48.dp)
        ) {
            Icon(
                painter = painterResource(me.jbusdriver.modern.R.drawable.arrow_circle_up_24px),
                contentDescription = "回到顶部",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun rememberIsScrolledPastFirstPage(listState: LazyListState): Boolean {
    return remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }.value
}

@Composable
fun rememberIsScrolledPastFirstPage(gridState: LazyGridState): Boolean {
    return remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 }
    }.value
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/ScrollToTopButton.kt
git commit -m "feat: add ScrollToTopButton composable and scroll detection utilities"
```

---

### Task 2: Integrate FAB into MovieList

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`

The current `MovieList` renders `LazyVerticalGrid` or `LazyColumn` directly without a `Box` wrapper. We need to wrap each in a `Box` so we can overlay the FAB, and add `rememberCoroutineScope` for smooth scrolling.

- [ ] **Step 1: Add imports**

Add these imports at the top of `MovieList.kt`:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

No other imports needed — `ScrollToTopButton` and `rememberIsScrolledPastFirstPage` are in the same package. `Alignment`, `Box`, `Modifier.padding` etc. are already imported.

- [ ] **Step 2: Replace the grid branch (lines 60-99)**

Replace the entire `if (isGrid) { ... }` block with:

```kotlin
    if (isGrid) {
        val gridState = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        val showScrollToTop = rememberIsScrolledPastFirstPage(gridState)

        LaunchedEffect(gridState) {
            snapshotFlow {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = gridState.layoutInfo.totalItemsCount
                lastVisible >= totalItems - 3
            }.collect { nearEnd ->
                if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                if (header != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { header() }
                }
                itemsIndexed(movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
                    MovieGridItem(movie = movie, onClick = { onMovieClick(movie) })
                }
                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (!hasMore && movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("沒有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            ScrollToTopButton(
                visible = showScrollToTop,
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
```

- [ ] **Step 3: Replace the list branch (lines 100-138)**

Replace the `else { ... }` block with:

```kotlin
    } else {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val showScrollToTop = rememberIsScrolledPastFirstPage(listState)

        LaunchedEffect(listState) {
            snapshotFlow {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                lastVisible >= totalItems - 3
            }.collect { nearEnd ->
                if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (header != null) {
                    item { header() }
                }
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
                            Text("沒有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            ScrollToTopButton(
                visible = showScrollToTop,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt
git commit -m "feat: add scroll-to-top FAB to MovieList"
```

---

### Task 3: Integrate FAB into ActressGrid

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/ActressGrid.kt`

The current `ActressGrid` renders `LazyVerticalGrid` directly without a `Box` wrapper. Same pattern as Task 2.

- [ ] **Step 1: Add imports to ActressGrid.kt**

Add these imports at the top of the file (after the existing imports):

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Wrap in Box and add FAB**

Replace the body of `ActressGrid` (lines 50-107) with:

```kotlin
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showScrollToTop = rememberIsScrolledPastFirstPage(gridState)

    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 6
        }.collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(95.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(actresses, key = { _, actress -> actress.link }) { _, actress ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onActressClick(actress) }
                ) {
                    ActressAvatar(
                        avatarUrl = actress.avatar,
                        contentDescription = actress.name,
                        size = 90.dp,
                        onClick = { onActressClick(actress) }
                    )
                    Text(
                        text = actress.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (!hasMore && actresses.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("沒有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        ScrollToTopButton(
            visible = showScrollToTop,
            onClick = { scope.launch { gridState.animateScrollToItem(0) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/ActressGrid.kt
git commit -m "feat: add scroll-to-top FAB to ActressGrid"
```

---

### Task 4: Manual verification

- [ ] **Step 1: Install debug APK on device/emulator**

Run: `./gradlew installDebug`

- [ ] **Step 2: Verify on all affected screens**

Checklist:
1. Home tab (有碼/無碼) - scroll down in both list and grid mode, confirm FAB appears at bottom center
2. Tap FAB - confirm smooth scroll to top and FAB fades out
3. Actress tab - same verification
4. Collection screen (收藏) - same verification for both movie and actress tabs
5. Link movie list (演员详情/分类列表) - same verification
6. Confirm FAB does NOT appear on GenreListScreen

- [ ] **Step 3: Final commit if any polish needed**
