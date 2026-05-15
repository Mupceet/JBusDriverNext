# Search-Collection Centric Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign JBus from a 4-tab browsing app into a 2-tab (Home + Collection) search-and-favorites-centric movie index tool.

**Architecture:** Replace 4-tab bottom navigation (Movie/Actress/Genre/Collection) with 2 tabs. Home tab unifies movie browsing, actress browsing, and category filtering via segments + filter chips. Collection tab becomes a dedicated favorites manager. Search gets history persistence.

**Tech Stack:** Jetpack Compose, Material 3, Hilt, Room, SharedPreferences, Kotlin Coroutines

---

## Task 1: Add Required Vector Drawable Icons

**Files:**
- Create: `app/src/main/res/drawable/home_24px.xml`
- Create: `app/src/main/res/drawable/view_list_24px.xml`
- Create: `app/src/main/res/drawable/grid_view_24px.xml`
- Create: `app/src/main/res/drawable/check_24px.xml`

- [ ] **Step 1: Create icon XML files**

Create `home_24px.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="960" android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path android:fillColor="@android:color/white"
      android:pathData="M240,760h120v-240h240v240h120v-360L480,220 240,400v360zM160,840v-480l320,-240 320,240v480L520,840v-240h-80v240L160,840zM480,490Z"/>
</vector>
```

Create `view_list_24px.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="960" android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path android:fillColor="@android:color/white"
      android:pathData="M160,760v-80h640v80L160,760ZM160,580v-80h640v80L160,580ZM160,400v-80h640v80L160,400ZM160,220v-80h640v80L160,220Z"/>
</vector>
```

Create `grid_view_24px.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="960" android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path android:fillColor="@android:color/white"
      android:pathData="M360,560L360,560v0,0zM160,840v-280h280v280L160,840ZM160,520v-280h280v280L160,520ZM360,640v-80h-80v80h80ZM160,440h120v-120h-120v120ZM160,760h120v-120h-120v120ZM440,440v-120h-120v120h120ZM440,760v-120h-120v120h120ZM520,840v-280h280v280L520,840ZM520,520v-280h280v280L520,520ZM720,640v-80h-80v80h80ZM520,440h120v-120h-120v120ZM520,760h120v-120h-120v120ZM800,440v-120h-120v120h120ZM800,760v-120h-120v120h120Z"/>
</vector>
```

Create `check_24px.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="960" android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path android:fillColor="@android:color/white"
      android:pathData="M382,720L154,492l57,-57 171,171 367,-367 57,57 -424,424Z"/>
</vector>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/drawable/home_24px.xml app/src/main/res/drawable/view_list_24px.xml app/src/main/res/drawable/grid_view_24px.xml app/src/main/res/drawable/check_24px.xml
git commit -m "res: add home, view list, grid view, check vector icons"
```

---

## Task 2: Move GenreCategory to Shared Location

The `GenreCategory` data class is currently in `GenreListScreen.kt` but will be needed by the new `CategoryBottomSheet`. Move it to `UiModels.kt`.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/GenreListScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/GenreListViewModel.kt` (import path)

- [ ] **Step 1: Add GenreCategory to UiModels.kt**

Append before the closing `// endregion` or at end of file in `UiModels.kt`:

```kotlin
/** 类别分组（按大类如题材、场景等分组） */
data class GenreCategory(val title: String, val genres: List<GenreUiModel>)
```

- [ ] **Step 2: Delete the data class from GenreListScreen.kt**

`GenreListScreen.kt` only contains the `GenreCategory` data class (5 lines). Delete the entire file:

```bash
rm app/src/main/java/me/jbusdriver/modern/ui/movielist/GenreListScreen.kt
```

Find and fix all imports that reference the old location:
```bash
grep -rl "import me.jbusdriver.modern.ui.movielist.GenreCategory" app/src/
```

Update each file to use `import me.jbusdriver.modern.ui.GenreCategory` instead.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: move GenreCategory to UiModels.kt for shared access"
```

---

## Task 3: Create SearchHistoryStore

A SharedPreferences-based store for persisting search history across sessions.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/SearchHistoryStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: Create SearchHistoryStore.kt**

Use `JBus` (the global Application context from `AppContext.kt`) for SharedPreferences access:

```kotlin
package me.jbusdriver.modern.data

import android.content.SharedPreferences
import me.jbusdriver.modern.AppContext.JBus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryStore @Inject constructor() {
    private val prefs: SharedPreferences =
        JBus.getSharedPreferences("search_history", 0)

    fun getHistory(): List<String> {
        return prefs.getStringSet(KEY_HISTORY, emptySet())?.toList() ?: emptyList()
    }

    fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = prefs.getStringSet(KEY_HISTORY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(query)
        if (current.size > MAX_HISTORY) {
            val toRemove = current.toList().drop(MAX_HISTORY)
            toRemove.forEach { current.remove(it) }
        }
        prefs.edit().putStringSet(KEY_HISTORY, current).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        private const val KEY_HISTORY = "search_history_queries"
        private const val MAX_HISTORY = 20
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/SearchHistoryStore.kt
git commit -m "feat: add SearchHistoryStore for persistent search history"
```

---

## Task 4: Extend MovieListViewModel for URL-Based Loading

Add genre URL loading support so the HomeScreen can load category-filtered movies.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt`

- [ ] **Step 1: Add genreUrl field and setGenreUrl method**

In `MovieListViewModel`, add a `genreUrl` field after `dataSourceType`:

```kotlin
/** 当前的类别 URL（类别筛选模式下使用），为 null 时使用 dataSourceType 加载 */
private var genreUrl: String? = null
```

Add a new method after `setDataSourceType`:

```kotlin
fun setGenreUrl(url: String?) {
    if (genreUrl == url && _uiState.value.movies.isNotEmpty()) return
    genreUrl = url
    currentPage = 0
    _uiState.value = MovieListUiState()
    loadFirstPage()
}
```

Modify the private `loadMovies` function to branch on `genreUrl`:

Replace the `try` block inside `loadMovies`:
```kotlin
try {
    val result = if (genreUrl != null) {
        repository.loadPageByUrl(genreUrl!!, page, showAll = _uiState.value.showAll, forceRefresh = forceRefresh)
    } else {
        repository.loadPage(dataSourceType, page, showAll = _uiState.value.showAll, forceRefresh = forceRefresh)
    }
    _uiState.update { onSuccess(result, it) }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt
git commit -m "feat: add URL-based loading to MovieListViewModel for genre filtering"
```

---

## Task 5: Create CategoryBottomSheet Composable

A bottom sheet for selecting genre categories with default single-select and optional multi-select (AND logic, IDs joined with "-").

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/components/CategoryBottomSheet.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.R
import me.jbusdriver.modern.ui.GenreCategory
import me.jbusdriver.modern.ui.GenreUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBottomSheet(
    categories: List<GenreCategory>,
    selectedGenres: Set<GenreUiModel>,
    onSelectionChange: (Set<GenreUiModel>) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMultiSelect by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Handle
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(32.dp).height(4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp)
            ) {}
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("選擇類別", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedGenres.isNotEmpty()) {
                    Text(
                        "已選 ${selectedGenres.size}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(12.dp))
                }
                TextButton(onClick = { onSelectionChange(emptySet()) }) {
                    Text("重置", fontSize = 12.sp)
                }
            }
        }

        // Multi-select toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("多選", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = isMultiSelect,
                onCheckedChange = {
                    isMultiSelect = it
                    if (!it && selectedGenres.size > 1) {
                        // Keep only the last selected
                        onSelectionChange(selectedGenres.lastOrNull()?.let { setOf(it) } ?: emptySet())
                    }
                },
                modifier = Modifier.height(24.dp)
            )
        }

        // Genre groups
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories, key = { it.title }) { group ->
                Text(
                    group.title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    group.genres.forEach { genre ->
                        val isSelected = genre in selectedGenres
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newSelection = if (isMultiSelect) {
                                    if (isSelected) selectedGenres - genre else selectedGenres + genre
                                } else {
                                    if (isSelected) emptySet() else setOf(genre)
                                }
                                onSelectionChange(newSelection)
                            },
                            label = {
                                Text(genre.name, fontSize = 12.sp)
                                if (isSelected) {
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        painter = painterResource(R.drawable.check_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // Apply button
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("套用篩選", fontWeight = FontWeight.SemiBold)
        }
    }
}
```

Note: This uses `ModalBottomSheet` from Material 3. The caller is responsible for wrapping in `ModalBottomSheet()`.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/CategoryBottomSheet.kt
git commit -m "feat: add CategoryBottomSheet with single/multi-select genre filtering"
```

---

## Task 6: Update MovieList with Compact Mode and Star Button

Add compact list mode with star/favorite button to the `MovieList` component.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`

- [ ] **Step 1: Add parameters to MovieList composable**

Update `MovieList` signature to add:
```kotlin
@Composable
fun MovieList(
    movies: List<MovieUiModel>,
    hasMore: Boolean = true,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onMovieClick: (MovieUiModel) -> Unit = {},
    isGrid: Boolean = false,
    compact: Boolean = false,
    isCollected: ((MovieUiModel) -> Boolean)? = null,
    onToggleCollect: ((MovieUiModel) -> Unit)? = null,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
)
```

- [ ] **Step 2: Update list mode rendering to use compact variant**

In the `else` branch (non-grid, `LazyColumn`), change the `itemsIndexed` call to use the compact item when `compact` is true:

```kotlin
itemsIndexed(movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
    if (compact) {
        CompactMovieItem(
            movie = movie,
            onClick = { onMovieClick(movie) },
            isCollected = isCollected?.invoke(movie) == true,
            onToggleCollect = if (onToggleCollect != null) {{ onToggleCollect(movie) }} else null
        )
    } else {
        MovieItem(movie = movie, onClick = { onMovieClick(movie) })
    }
}
```

- [ ] **Step 3: Create CompactMovieItem composable**

Add inside `MovieList.kt` after `MovieGridItem`:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactMovieItem(
    movie: MovieUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCollected: Boolean = false,
    onToggleCollect: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = movie.imageUrl,
                contentDescription = movie.title,
                modifier = Modifier
                    .width(52.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = movie.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (movie.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        movie.tags.take(3).forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            if (onToggleCollect != null) {
                IconButton(
                    onClick = onToggleCollect,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isCollected) R.drawable.favorite_fill_24px else R.drawable.favorite_24px
                        ),
                        contentDescription = if (isCollected) "取消收藏" else "收藏",
                        tint = if (isCollected) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
```

Add missing imports at top of `MovieList.kt`:
```kotlin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import me.jbusdriver.R
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt
git commit -m "feat: add compact movie item with star/favorite button"
```

---

## Task 7: Update MovieListScreen to Pass Compact/Collect Params

Wire the new MovieList parameters through MovieListScreen.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt`

- [ ] **Step 1: Add parameters to MovieListScreen**

Update the function signature:
```kotlin
@Composable
fun MovieListScreen(
    dataSourceType: DataSourceType = DataSourceType.CENSORED,
    active: Boolean = true,
    onMovieClick: (MovieUiModel) -> Unit = {},
    compact: Boolean = false,
    isCollected: ((MovieUiModel) -> Boolean)? = null,
    onToggleCollect: ((MovieUiModel) -> Unit)? = null,
    isGrid: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: MovieListViewModel = hiltViewModel()
)
```

Pass them through to `MovieList`:
```kotlin
MovieList(
    movies = uiState.movies,
    hasMore = uiState.hasMore,
    isLoadingMore = uiState.isLoadingMore,
    onLoadMore = { viewModel.loadMore() },
    onMovieClick = onMovieClick,
    isGrid = isGrid,
    compact = compact,
    isCollected = isCollected,
    onToggleCollect = onToggleCollect,
    header = filterBar
)
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt
git commit -m "feat: wire compact/collect params through MovieListScreen"
```

---

## Task 8: Create HomeScreen

The unified home screen with search bar, segment control, filter chips, and category bottom sheet.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/HomeScreen.kt`

- [ ] **Step 1: Create HomeScreen composable**

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.components.CategoryBottomSheet
import me.jbusdriver.modern.ui.movielist.ActressListScreen
import me.jbusdriver.modern.ui.movielist.GenreListViewModel
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.movielist.MovieListViewModel

private enum class HomeSegment { MOVIE, ACTRESS }

private enum class CensorFilter(val label: String) {
    ALL("全部"), CENSORED("有碼"), UNCENSORED("無碼")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var segment by remember { mutableStateOf(HomeSegment.MOVIE) }
    var censorFilter by remember { mutableStateOf(CensorFilter.ALL) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var selectedGenres by remember { mutableStateOf<Set<GenreUiModel>>(emptySet()) }
    var isGrid by remember { mutableStateOf(false) }

    // Genre data for bottom sheet
    val genreViewModel: GenreListViewModel = hiltViewModel()
    val genreState by genreViewModel.uiState.collectAsStateWithLifecycle()

    // Load genre data based on censor filter
    LaunchedEffect(censorFilter) {
        val genreType = when (censorFilter) {
            CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_GENRE
            else -> DataSourceType.GENRE
        }
        genreViewModel.setDataSourceType(genreType)
    }

    // Category bottom sheet
    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false }
        ) {
            CategoryBottomSheet(
                categories = genreState.genreCategories,
                selectedGenres = selectedGenres,
                onSelectionChange = { selectedGenres = it },
                onDismiss = { showCategorySheet = false },
                onApply = { showCategorySheet = false }
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        Surface(
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.search_24px),
                    contentDescription = "搜尋",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "搜索影片、演員、類別...",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }

        // Segment control: 影片 | 演员
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf("影片" to HomeSegment.MOVIE, "演員" to HomeSegment.ACTRESS).forEach { (label, seg) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { segment = seg }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        fontWeight = if (segment == seg) FontWeight.Bold else FontWeight.Normal,
                        color = if (segment == seg) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    if (segment == seg) {
                        Divider(
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }
            }
        }

        // Filter chips row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CensorFilter.entries.forEach { filter ->
                FilterChip(
                    selected = censorFilter == filter,
                    onClick = { censorFilter = filter },
                    label = { Text(filter.label, fontSize = 12.sp) }
                )
            }
            if (segment == HomeSegment.MOVIE) {
                FilterChip(
                    selected = selectedGenres.isNotEmpty(),
                    onClick = { showCategorySheet = true },
                    label = {
                        Text(
                            if (selectedGenres.isEmpty()) "類別▾"
                            else "類別(${selectedGenres.size})",
                            fontSize = 12.sp
                        )
                    },
                    trailingIcon = if (selectedGenres.isEmpty()) {
                        { Icon(painterResource(R.drawable.category_24px), null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        // Content area
        Box(modifier = Modifier.fillMaxSize()) {
            when (segment) {
                HomeSegment.MOVIE -> {
                    // Build genre URL if categories selected
                    val genreUrl = if (selectedGenres.isNotEmpty()) {
                        selectedGenres.joinToString("-") { it.link.trimEnd('/').substringAfterLast("/") }
                            .let { ids ->
                                val base = if (censorFilter == CensorFilter.UNCENSORED) "uncensored/" else ""
                                "/${base}genre/$ids"
                            }
                    } else null

                    if (genreUrl != null) {
                        // URL-based movie list for category filter
                        val genreVm: MovieListViewModel = hiltViewModel(key = "genre_$genreUrl")
                        LaunchedEffect(genreUrl) { genreVm.setGenreUrl(genreUrl) }
                        MovieListScreen(
                            active = true,
                            onMovieClick = onMovieClick,
                            compact = true,
                            isGrid = isGrid,
                            modifier = Modifier.fillMaxSize(),
                            viewModel = genreVm
                        )
                    } else {
                        val dataSourceType = when (censorFilter) {
                            CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED
                            else -> DataSourceType.CENSORED
                        }
                        MovieListScreen(
                            dataSourceType = dataSourceType,
                            active = true,
                            onMovieClick = onMovieClick,
                            compact = true,
                            isGrid = isGrid,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // View toggle
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 4.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = { isGrid = false },
                            containerColor = if (!isGrid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!isGrid) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(painterResource(R.drawable.view_list_24px), "列表", modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        SmallFloatingActionButton(
                            onClick = { isGrid = true },
                            containerColor = if (isGrid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isGrid) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(painterResource(R.drawable.grid_view_24px), "網格", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                HomeSegment.ACTRESS -> {
                    val actressType = when (censorFilter) {
                        CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_ACTRESSES
                        else -> DataSourceType.ACTRESSES
                    }
                    ActressListScreen(
                        dataSourceType = actressType,
                        active = true,
                        onActressClick = onActressClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
```

Note: The genre URL construction extracts the genre ID from the link (e.g., `/genre/1n` → `1n`) and joins multiple IDs with `-` for AND logic. The URL prefix handles censored vs uncensored paths.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (may have unused import warnings, fix as needed)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/HomeScreen.kt
git commit -m "feat: create HomeScreen with search bar, segments, filter chips, category sheet"
```

---

## Task 9: Redesign CollectCategoryScreen

Redesign collection tab with title, pill-style segmented switch with counts, timestamps, long-press delete, and empty state.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt`

- [ ] **Step 1: Update CollectionListUiState with counts**

In `CollectionListViewModel.kt`, add counts to the UI state:

```kotlin
data class CollectionListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val actresses: List<ActressUiModel> = emptyList(),
    val movieCount: Int = 0,
    val actressCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

Update `loadCollection` to also load counts. After loading the primary list, also query the count for the other type:

```kotlin
fun loadCollection(dbType: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            when (dbType) {
                MovieDBType -> {
                    val movies = repository.getCollectedMovies().map { it.toUiModel() }
                    val actressCount = repository.getCollectedActresses().size
                    _uiState.update {
                        it.copy(
                            movies = movies,
                            movieCount = movies.size,
                            actressCount = actressCount,
                            isLoading = false,
                            actresses = emptyList()
                        )
                    }
                }
                ActressDBType -> {
                    val actresses = repository.getCollectedActresses().map { it.toActressUiModel() }
                    val movieCount = repository.getCollectedMovies().size
                    _uiState.update {
                        it.copy(
                            actresses = actresses,
                            actressCount = actresses.size,
                            movieCount = movieCount,
                            isLoading = false,
                            movies = emptyList()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入失敗") }
        }
    }
}
```

Note: This makes two DB calls per load. For better performance, add `getMovieCount()` and `getActressCount()` to `CollectRepository` that use `SELECT COUNT(*)` instead of loading full lists. But for now, since collections are small, this is acceptable.

- [ ] **Step 2: Update CollectionListScreen with timestamps and long-press delete**

In `CollectionListScreen.kt`, add long-press support. When `dbType == MovieDBType`, use `compact = true` and show empty state. Add a `onLongPress` handler that removes the item:

```kotlin
// In CollectionListScreen, after loading movies successfully:
MovieList(
    movies = uiState.movies,
    hasMore = false,
    isLoadingMore = false,
    compact = true,
    isCollected = { true }, // all items in collection are collected
    onToggleCollect = { movie ->
        // Remove from collection
        viewModel.removeFromCollection(movie)
    },
    onMovieClick = onMovieClick
)
```

Add `removeFromCollection` to `CollectionListViewModel`:

```kotlin
fun removeFromCollection(movie: MovieUiModel) {
    viewModelScope.launch(Dispatchers.IO) {
        val domainMovie = me.jbusdriver.modern.domain.model.Movie(
            title = movie.title,
            imageUrl = movie.imageUrl,
            code = movie.code,
            date = movie.date,
            link = movie.link
        )
        repository.removeCollect(domainMovie.convertDBItem().also { it.dbType = MovieDBType })
        loadCollection(MovieDBType)
    }
}

fun removeActressFromCollection(actress: ActressUiModel) {
    viewModelScope.launch(Dispatchers.IO) {
        val domainActress = me.jbusdriver.modern.domain.model.ActressInfo(
            name = actress.name,
            avatar = actress.avatar,
            link = actress.link
        )
        repository.removeCollect(domainActress.convertDBItem().also { it.dbType = ActressDBType })
        loadCollection(ActressDBType)
    }
}
```

Note: Check `LinkMappers.kt` for the exact `convertDBItem()` extension. The ILink interface has `dbType` mapped via extension. Adapt accordingly.

For the empty state, when the list is empty after loading:
```kotlin
if (uiState.movies.isEmpty() && !uiState.isLoading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("還沒有收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            // The parent CollectCategoryScreen can handle navigation
        }
    }
}
```

- [ ] **Step 3: Redesign CollectCategoryScreen with pill switch**

Replace the existing `CollectCategoryScreen` content. Remove the `ScrollableTabRow` + `HorizontalPager` and use a pill-style segmented switch:

```kotlin
@Composable
fun CollectCategoryScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onGoHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0=movies, 1=actresses
    val saveableStateHolder = rememberSaveableStateHolder()

    Column(modifier = modifier.fillMaxSize()) {
        // Title
        Text(
            "我的收藏",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Pill-style segmented switch
        val movieViewModel: CollectionListViewModel = hiltViewModel()
        val movieState by movieViewModel.uiState.collectAsStateWithLifecycle()
        val movieCount = movieState.movieCount
        val actressCount = movieState.actressCount

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(modifier = Modifier.padding(3.dp)) {
                repeat(2) { index ->
                    val label = if (index == 0) "影片 ($movieCount)" else "演員 ($actressCount)"
                    Surface(
                        modifier = Modifier.weight(1f).clickable { selectedTab = index },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTab == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(vertical = 8.dp).align(Alignment.CenterHorizontally),
                            color = if (selectedTab == index) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Content
        saveableStateHolder.SaveableStateProvider(selectedTab) {
            when (selectedTab) {
                0 -> CollectionListScreen(
                    dbType = MovieDBType,
                    active = true,
                    onMovieClick = onMovieClick,
                    onActressClick = onActressClick
                )
                1 -> CollectionListScreen(
                    dbType = ActressDBType,
                    active = true,
                    onMovieClick = onMovieClick,
                    onActressClick = onActressClick
                )
            }
        }
    }
}
```

Add missing imports:
```kotlin
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt
git commit -m "feat: redesign collection screen with pill switch, counts, timestamps, delete"
```

---

## Task 10: Update SearchScreen with History

Add search history section to the search screen.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`

- [ ] **Step 1: Add SearchHistoryStore to SearchViewModel**

In `SearchViewModel.kt`, inject `SearchHistoryStore`:

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val historyStore: SearchHistoryStore
) : ViewModel() {
```

Add history state and methods:

```kotlin
private val _searchHistory = MutableStateFlow(historyStore.getHistory())
val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

fun clearHistory() {
    historyStore.clearHistory()
    _searchHistory.value = emptyList()
}

// In the search() method, after a successful search, add:
fun search(query: String, type: SearchType) {
    // ... existing search logic ...
    historyStore.addQuery(query)
    _searchHistory.value = historyStore.getHistory()
}
```

- [ ] **Step 2: Add history UI to SearchScreen**

In `SearchScreen.kt`, before the search results (when query is blank), show history:

```kotlin
val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

// In the content area, when searchInput is blank:
if (searchInput.isBlank()) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("搜索歷史", fontWeight = FontWeight.SemiBold)
            if (searchHistory.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text("清除", fontSize = 12.sp)
                }
            }
        }
        if (searchHistory.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                searchHistory.forEach { query ->
                    SuggestionChip(
                        onClick = {
                            searchInput = query
                            viewModel.search(query, currentSearchType)
                        },
                        label = { Text(query, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt
git commit -m "feat: add search history persistence to SearchScreen"
```

---

## Task 11: Update MainScreen to 2-Tab Structure

Replace the 4-tab navigation with 2 tabs (Home + Collection) and wire the new HomeScreen.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`

- [ ] **Step 1: Rewrite MainScreen**

Replace the entire content of `MainScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.ui.movielist.CollectCategoryScreen

enum class BottomNavCategory { HOME, COLLECT }

private data class BottomNavItem(
    val category: BottomNavCategory,
    val label: String,
    val iconRes: Int
)

private val BottomNavItems = listOf(
    BottomNavItem(BottomNavCategory.HOME, "首頁", R.drawable.home_24px),
    BottomNavItem(BottomNavCategory.COLLECT, "收藏", R.drawable.favorite_24px)
)

@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {},
    onSearchClick: (String) -> Unit = {}
) {
    var selectedCategory by rememberSaveable { mutableStateOf(BottomNavCategory.HOME) }
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(modifier = Modifier.height(64.dp)) {
                BottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = selectedCategory == item.category,
                        onClick = { selectedCategory = item.category },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { innerPadding ->
        saveableStateHolder.SaveableStateProvider(selectedCategory) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedCategory) {
                    BottomNavCategory.HOME -> HomeScreen(
                        onMovieClick = onMovieClick,
                        onActressClick = onActressClick,
                        onSearchClick = { onSearchClick("") }
                    )
                    BottomNavCategory.COLLECT -> CollectCategoryScreen(
                        onMovieClick = onMovieClick,
                        onActressClick = onActressClick,
                        onGoHome = { selectedCategory = BottomNavCategory.HOME }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt
git commit -m "feat: restructure MainScreen to 2-tab navigation (Home + Collection)"
```

---

## Task 12: Build Verification and Manual Testing

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install and verify on device/emulator**

Test these flows:
1. App launches → Home tab shown with search bar, 影片/演員 segment, filter chips
2. Tap search bar → full-screen search page opens
3. Switch 影片 ↔ 演員 segment → content changes appropriately
4. Filter chips 全部/有碼/無碼 → data source changes
5. Tap 類別▾ → bottom sheet opens with genre categories
6. Select category → movie list filters by genre
7. Enable multi-select → select multiple genres → URL contains `-` joined IDs
8. Toggle 列表/網格 → list style changes
9. Tap star button on movie → collection toggles
10. Switch to 收藏 tab → shows collected items with counts
11. Tap search → type query → history saved → go back → history visible
12. All existing flows still work: movie detail, actress page, image viewer

- [ ] **Step 3: Fix any build or runtime issues**

Address compiler errors, crashes, or visual glitches discovered during testing.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "fix: resolve issues from search-collection redesign verification"
```

---

## Task 13: Cleanup Unused Code

Remove files and code that are no longer used after the redesign.

**Files:**
- Remove content: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieCategoryScreen.kt` (replaced by HomeScreen)
- Remove content: `app/src/main/java/me/jbusdriver/modern/ui/movielist/ActressCategoryScreen.kt` (replaced by HomeScreen)
- Remove content: `app/src/main/java/me/jbusdriver/modern/ui/movielist/GenreCategoryScreen.kt` (replaced by CategoryBottomSheet)

- [ ] **Step 1: Search for remaining references to removed screens**

```bash
grep -r "MovieCategoryScreen\|ActressCategoryScreen\|GenreCategoryScreen" app/src/
```

Remove any remaining imports or references. These screens are no longer used since `MainScreen` now uses `HomeScreen` directly.

- [ ] **Step 2: Delete unused screen files**

Delete the files (they are fully replaced by HomeScreen and CategoryBottomSheet):
- `MovieCategoryScreen.kt`
- `ActressCategoryScreen.kt`
- `GenreCategoryScreen.kt`

If `GenreListScreen.kt` only contains the moved `GenreCategory` data class, delete it too.

- [ ] **Step 3: Build to verify nothing is broken**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "cleanup: remove replaced category screens (Movie/Actress/Genre)"
```
