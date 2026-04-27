# Modern UI Navigation & Category Switching — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bottom navigation bar with 3 tabs (电影/搜索/设置) and category switching with ScrollableTabRow + FilterChip for the movie list screen.

**Architecture:** MainScreen becomes the new container with Scaffold + NavigationBar. MovieListScreen, SearchScreen, SettingsScreen lose their own Scaffolds and render as content inside MainScreen. MovieListScreen accepts a `DataSourceType` parameter that drives category switching via the ViewModel.

**Tech Stack:** Jetpack Compose, Material 3 (NavigationBar, ScrollableTabRow, FilterChip), Navigation Compose, Hilt ViewModel

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `modern/ui/MainScreen.kt` | Bottom nav container + category tabs + sub-chips |
| Modify | `modern/ui/movielist/MovieListScreen.kt` | Remove Scaffold, accept DataSourceType |
| Modify | `modern/ui/movielist/MovieListViewModel.kt` | Update setDataSourceType guard for initial load |
| Modify | `modern/ui/search/SearchScreen.kt` | Remove Scaffold, accept modifier |
| Modify | `modern/ui/settings/SettingsScreen.kt` | Remove Scaffold, accept modifier |
| Modify | `modern/ui/Navigation.kt` | Replace flat routes with MainScreen container |
| Modify | `modern/ui/NavigationKeys.kt` | Add ROUTE_MAIN, remove unused routes |

---

### Task 1: Update MovieListViewModel — fix setDataSourceType for initial load

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt:40-47`

The current `setDataSourceType` guard blocks when `type == dataSourceType`. When `MovieListScreen` uses `LaunchedEffect(dataSourceType)` for initial load, the first call with the default `CENSORED` type gets blocked. Fix: only skip if type matches AND data already exists.

- [ ] **Step 1: Update the guard in setDataSourceType**

In `MovieListViewModel.kt`, change the `setDataSourceType` method:

```kotlin
// Before:
fun setDataSourceType(type: DataSourceType) {
    if (dataSourceType != type) {
        dataSourceType = type
        currentPage = 0
        _uiState.value = MovieListUiState()
        loadFirstPage()
    }
}

// After:
fun setDataSourceType(type: DataSourceType) {
    if (dataSourceType == type && _uiState.value.movies.isNotEmpty()) return
    dataSourceType = type
    currentPage = 0
    _uiState.value = MovieListUiState()
    loadFirstPage()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt
git commit -m "refactor: update setDataSourceType guard to allow initial load"
```

---

### Task 2: Refactor MovieListScreen — remove Scaffold, accept DataSourceType

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt`

Remove the Scaffold/TopAppBar (MainScreen provides them). Add `dataSourceType` and `modifier` parameters. Replace `LaunchedEffect(Unit)` with `LaunchedEffect(dataSourceType)`.

- [ ] **Step 1: Rewrite MovieListScreen**

Replace the entire content of `MovieListScreen.kt` with:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.ui.data.enums.DataSourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    dataSourceType: DataSourceType = DataSourceType.CENSORED,
    onMovieClick: (MovieUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(dataSourceType) {
        viewModel.setDataSourceType(dataSourceType)
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.movies.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.error ?: "加载失败", color = Color.Red)
                }
            }
            else -> {
                val listState = rememberLazyListState()

                LaunchedEffect(listState, uiState.hasMore) {
                    snapshotFlow {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val totalItems = listState.layoutInfo.totalItemsCount
                        lastVisible >= totalItems - 3
                    }.collect { nearEnd ->
                        if (nearEnd && uiState.hasMore && !uiState.isLoadingMore) {
                            viewModel.loadMore()
                        }
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(uiState.movies, key = { it.link }) { movie ->
                        MovieItem(
                            movie = movie,
                            onClick = { onMovieClick(movie) }
                        )
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    if (!uiState.hasMore && uiState.movies.isNotEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "没有更多了",
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListScreen.kt
git commit -m "refactor: MovieListScreen removes Scaffold, accepts DataSourceType param"
```

---

### Task 3: Refactor SearchScreen — remove Scaffold, accept modifier

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`

Remove Scaffold/TopAppBar. Add `modifier` parameter. Pass `modifier` to the root Column.

- [ ] **Step 1: Rewrite SearchScreen**

Replace the entire content of `SearchScreen.kt` with:

```kotlin
package me.jbusdriver.modern.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.ui.movielist.MovieItem
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.ui.data.enums.SearchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieClick: (MovieUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Search input
        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            label = { Text("搜索") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            trailingIcon = {
                Text(
                    "搜索",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        )

        // Search type chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SearchType.entries.forEach { type ->
                FilterChip(
                    selected = uiState.searchType == type,
                    onClick = { viewModel.setSearchType(type) },
                    label = { Text(type.title, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }

        // Results
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "搜索失败", color = Color.Red)
                }
            }
            uiState.results.isEmpty() && uiState.query.isBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键词开始搜索", color = Color.Gray)
                }
            }
            else -> {
                val listState = rememberLazyListState()

                LaunchedEffect(listState, uiState.hasMore) {
                    snapshotFlow {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val totalItems = listState.layoutInfo.totalItemsCount
                        lastVisible >= totalItems - 3
                    }.collect { nearEnd ->
                        if (nearEnd && uiState.hasMore && !uiState.isLoadingMore) {
                            viewModel.loadMore()
                        }
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(uiState.results, key = { it.link }) { movie ->
                        MovieItem(movie = movie, onClick = { onMovieClick(movie) })
                    }
                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt
git commit -m "refactor: SearchScreen removes Scaffold, accepts modifier param"
```

---

### Task 4: Refactor SettingsScreen — remove Scaffold, accept modifier

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`

Remove Scaffold/TopAppBar. Add `modifier` parameter. Also fix the deprecated `hiltViewModel` import (`androidx.hilt.lifecycle.viewmodel.compose` → `androidx.hilt.navigation.compose.hiltViewModel`) to match the rest of the codebase.

- [ ] **Step 1: Rewrite SettingsScreen**

Replace the entire content of `SettingsScreen.kt` with:

```kotlin
package me.jbusdriver.modern.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import me.jbusdriver.ui.activity.SettingActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "数据源",
            style = MaterialTheme.typography.titleLarge
        )

        UrlSelector(
            currentUrl = uiState.baseUrl,
            availableUrls = uiState.availableUrls,
            isUpdating = uiState.isUpdating,
            onUrlSelected = { viewModel.updateUrl(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "其他设置",
            style = MaterialTheme.typography.titleLarge
        )

        Button(
            onClick = {
                context.startActivity(Intent(context, SettingActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("更多设置（旧版）")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlSelector(
    currentUrl: String,
    availableUrls: List<String>,
    isUpdating: Boolean,
    onUrlSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentUrl,
                onValueChange = {},
                readOnly = true,
                label = { Text("当前地址") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableUrls.forEach { url ->
                    DropdownMenuItem(
                        text = { Text(url) },
                        onClick = {
                            onUrlSelected(url)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (isUpdating) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "可用源: ${availableUrls.size} 个",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt
git commit -m "refactor: SettingsScreen removes Scaffold, accepts modifier param, fixes hiltViewModel import"
```

---

### Task 5: Create MainScreen — bottom nav + category tabs

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`

This is the main integration point. It contains:
- Category data model (CategoryGroup, SubCategory)
- Bottom NavigationBar with 3 tabs
- ScrollableTabRow for main categories
- FilterChip row for sub-categories
- MovieListScreen wired to the selected DataSourceType

Note: Material Icons core (`Icons.Default.Home`, `Search`, `Settings`) is included transitively via `compose-material3`. No new dependency needed.

- [ ] **Step 1: Create MainScreen.kt**

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.search.SearchScreen
import me.jbusdriver.modern.ui.settings.SettingsScreen
import me.jbusdriver.ui.data.enums.DataSourceType

data class CategoryGroup(
    val name: String,
    val subCategories: List<SubCategory>
)

data class SubCategory(
    val name: String,
    val dataSourceType: DataSourceType
)

val CategoryGroups = listOf(
    CategoryGroup("有碼", listOf(
        SubCategory("电影", DataSourceType.CENSORED),
        SubCategory("女优", DataSourceType.ACTRESSES),
        SubCategory("类别", DataSourceType.GENRE),
    )),
    CategoryGroup("無碼", listOf(
        SubCategory("电影", DataSourceType.UNCENSORED),
        SubCategory("女优", DataSourceType.UNCENSORED_ACTRESSES),
        SubCategory("类别", DataSourceType.UNCENSORED_GENRE),
    )),
    CategoryGroup("欧美", listOf(
        SubCategory("电影", DataSourceType.XYZ),
        SubCategory("演员", DataSourceType.XYZ_ACTRESSES),
        SubCategory("类别", DataSourceType.XYZ_GENRE),
    )),
    CategoryGroup("高清", listOf(
        SubCategory("电影", DataSourceType.GENRE_HD),
    )),
    CategoryGroup("字幕", listOf(
        SubCategory("电影", DataSourceType.Sub),
    )),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedSubCategoryIndex by rememberSaveable { mutableIntStateOf(0) }

    val currentGroup = CategoryGroups[selectedCategoryIndex]
    val currentSubCategory = currentGroup.subCategories[selectedSubCategoryIndex]

    val topBarTitle = when (selectedTabIndex) {
        0 -> currentGroup.name
        1 -> "搜索"
        2 -> "设置"
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(topBarTitle) })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "电影") },
                    label = { Text("电影") },
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    label = { Text("搜索") },
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 }
                )
            }
        }
    ) { padding ->
        when (selectedTabIndex) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Main category tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedCategoryIndex,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        CategoryGroups.forEachIndexed { index, group ->
                            Tab(
                                selected = selectedCategoryIndex == index,
                                onClick = {
                                    selectedCategoryIndex = index
                                    selectedSubCategoryIndex = 0
                                },
                                text = { Text(group.name) }
                            )
                        }
                    }

                    // Sub-category chips (only show if group has >1 sub-category)
                    val subCategories = currentGroup.subCategories
                    if (subCategories.size > 1) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            subCategories.forEachIndexed { index, sub ->
                                FilterChip(
                                    selected = selectedSubCategoryIndex == index,
                                    onClick = { selectedSubCategoryIndex = index },
                                    label = {
                                        Text(
                                            sub.name,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }

                    // Movie list
                    MovieListScreen(
                        dataSourceType = currentSubCategory.dataSourceType,
                        onMovieClick = onMovieClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            1 -> SearchScreen(
                onMovieClick = onMovieClick,
                modifier = Modifier.padding(padding)
            )
            2 -> SettingsScreen(
                modifier = Modifier.padding(padding)
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt
git commit -m "feat: add MainScreen with bottom navigation and category tabs"
```

---

### Task 6: Update Navigation and NavigationKeys

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`

Replace the flat route structure. `main` becomes the start destination containing MainScreen. Search and Settings are no longer separate routes (they're tabs in MainScreen). `movie_detail` remains unchanged.

- [ ] **Step 1: Update NavigationKeys.kt**

Replace the entire content with:

```kotlin
package me.jbusdriver.modern.ui

object NavigationKeys {
    const val ROUTE_MAIN = "main"
    const val ROUTE_MOVIE_DETAIL = "movie_detail/{movieUrl}"

    fun movieDetailUrl(movieUrl: String) = "movie_detail/${java.net.URLEncoder.encode(movieUrl, "UTF-8")}"
}
```

- [ ] **Step 2: Update Navigation.kt**

Replace the entire content with:

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.jbusdriver.modern.ui.detail.MovieDetailScreen
import java.net.URLDecoder

@Composable
fun JBusNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavigationKeys.ROUTE_MAIN
    ) {
        composable(NavigationKeys.ROUTE_MAIN) {
            MainScreen(
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                }
            )
        }
        composable(
            route = NavigationKeys.ROUTE_MOVIE_DETAIL,
            arguments = listOf(navArgument("movieUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("movieUrl") ?: ""
            val movieUrl = URLDecoder.decode(encodedUrl, "UTF-8")
            MovieDetailScreen(
                movieUrl = movieUrl,
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                }
            )
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt
git commit -m "feat: update navigation to use MainScreen as container"
```

---

### Task 7: Build and verify

- [ ] **Step 1: Run debug build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Verify no compile errors**

Check the output for any compilation errors. Fix if needed.

- [ ] **Step 3: Commit if any fixes were needed**

---

## Known Limitations

- **Actress/Genre types reuse MovieRepository**: The `MovieRepository.loadPage()` URL construction doesn't properly handle actress/genre DataSourceTypes. These tabs will load movie data instead of actress/genre data. This is intentional for the scaffold phase — dedicated screens will be added in Phase 2.
- **No data persistence across category switches**: Switching categories resets the list. The ViewModel doesn't cache per-category data.
- **Search input in SearchScreen**: The search input doesn't have a submit action wired — the existing code only has a trailing "搜索" text label, not a clickable button. This is a pre-existing issue, not introduced by this plan.
