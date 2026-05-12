# UI Navigation Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure MainScreen from a flat 6-tab HorizontalPager into a bottom NavigationBar with 4 top-level categories, each with its own search bar and tabs.

**Architecture:** Single MainScreen with internal state switching via `BottomNavCategory` enum. Each category is an independent Composable wrapping existing list screens. State preserved across bottom-nav switches via `SaveableStateHolder`.

**Tech Stack:** Jetpack Compose, Material3 NavigationBar, HorizontalPager, Hilt ViewModel keying, SaveableStateHolder

---

## File Structure

**New files:**
- `app/src/main/java/me/jbusdriver/modern/ui/components/CategorySearchBar.kt` — Shared clickable search bar component
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieCategoryScreen.kt` — Movie category container
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/ActressCategoryScreen.kt` — Actress category container
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/GenreCategoryScreen.kt` — Genre category container (dual-layer tabs)
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt` — Collect category container
- `app/src/main/res/drawable/movie_24px.xml` — Movie icon for NavigationBar
- `app/src/main/res/drawable/category_24px.xml` — Category/label icon for NavigationBar

**Modified files:**
- `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt` — Complete rewrite
- `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt` — Add `defaultSearchType` param to search route
- `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt` — Update search route definition
- `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt` — Accept and apply defaultSearchType

**Existing icons (already in project):**
- `search_24px.xml` — Search icon (exists)
- `person_24px.xml` — Person icon for Actress tab (exists)
- `favorite_24px.xml` — Favorite icon for Collect tab (exists)

---

### Task 1: Add missing Material icons for NavigationBar

**Files:**
- Create: `app/src/main/res/drawable/movie_24px.xml`
- Create: `app/src/main/res/drawable/category_24px.xml`

- [ ] **Step 1: Create movie icon vector drawable**

Create `app/src/main/res/drawable/movie_24px.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M160,840Q127,840 103.5,816.5Q80,793 80,760L80,200Q80,167 103.5,143.5Q127,120 160,120L240,120L320,200L640,200L720,120L800,120Q833,120 856.5,143.5Q880,167 880,200L880,760Q880,793 856.5,816.5Q833,840 800,840L160,840ZM160,760L800,760L800,200L688,200L608,280L352,280L272,200L160,200L160,760ZM280,640L680,640L530,440L420,580L350,490L280,640Z"/>
</vector>
```

- [ ] **Step 2: Create category icon vector drawable**

Create `app/src/main/res/drawable/category_24px.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M320,440L480,280L640,440L560,440L560,640L400,640L400,440L320,440ZM200,760Q167,760 143.5,736.5Q120,713 120,680L120,280Q120,247 143.5,223.5Q167,200 200,200L760,200Q793,200 816.5,223.5Q840,247 840,280L840,680Q840,713 816.5,736.5Q793,760 760,760L200,760ZM200,680L760,680Q760,680 760,680Q760,680 760,680L760,280Q760,280 760,280Q760,280 760,280L200,280Q200,280 200,280Q200,280 200,280L200,680Q200,680 200,680Q200,680 200,680ZM200,680Q200,680 200,680Q200,680 200,680L200,280Q200,280 200,280Q200,280 200,280L200,680Z"/>
</vector>
```

- [ ] **Step 3: Build to verify icons compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/drawable/movie_24px.xml app/src/main/res/drawable/category_24px.xml
git commit -m "feat: add movie and category navigation bar icons"
```

---

### Task 2: Create CategorySearchBar shared component

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/components/CategorySearchBar.kt`

- [ ] **Step 1: Create the CategorySearchBar composable**

Create `app/src/main/java/me/jbusdriver/modern/ui/components/CategorySearchBar.kt`:

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.jbusdriver.R

@Composable
fun CategorySearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.search_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "搜索影片、演员...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/CategorySearchBar.kt
git commit -m "feat: add CategorySearchBar shared clickable search bar component"
```

---

### Task 3: Create MovieCategoryScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieCategoryScreen.kt`

- [ ] **Step 1: Create MovieCategoryScreen with search bar + 4 tabs**

The movie tabs map to existing `DataSourceType` values:
- 有码 → `DataSourceType.CENSORED`
- 无码 → `DataSourceType.UNCENSORED`
- 高清 → `DataSourceType.GENRE_HD`
- 字幕 → `DataSourceType.Sub`

Create `app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieCategoryScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.CategorySearchBar

private data class MovieTab(
    val title: String,
    val dataSourceType: DataSourceType
)

private val MovieTabs = listOf(
    MovieTab("有码", DataSourceType.CENSORED),
    MovieTab("无码", DataSourceType.UNCENSORED),
    MovieTab("高清", DataSourceType.GENRE_HD),
    MovieTab("字幕", DataSourceType.Sub)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieCategoryScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { MovieTabs.size }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        CategorySearchBar(onClick = onSearchClick)

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 8.dp,
            indicator = {
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(pagerState.currentPage, matchContentSize = false)
                )
            },
            divider = {}
        ) {
            MovieTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            tab.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val tab = MovieTabs[page]
            val active = pagerState.settledPage == page
            MovieListScreen(
                dataSourceType = tab.dataSourceType,
                active = active,
                onMovieClick = onMovieClick,
                viewModel = hiltViewModel(key = "movie_tab_$page")
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieCategoryScreen.kt
git commit -m "feat: add MovieCategoryScreen with search bar and 4 tabs"
```

---

### Task 4: Create ActressCategoryScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/ActressCategoryScreen.kt`

- [ ] **Step 1: Create ActressCategoryScreen with search bar + 2 tabs**

Create `app/src/main/java/me/jbusdriver/modern/ui/movielist/ActressCategoryScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.components.CategorySearchBar

private data class ActressTab(
    val title: String,
    val dataSourceType: DataSourceType
)

private val ActressTabs = listOf(
    ActressTab("有码", DataSourceType.ACTRESSES),
    ActressTab("无码", DataSourceType.UNCENSORED_ACTRESSES)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActressCategoryScreen(
    onActressClick: (ActressUiModel) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { ActressTabs.size }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        CategorySearchBar(onClick = onSearchClick)

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 8.dp,
            indicator = {
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(pagerState.currentPage, matchContentSize = false)
                )
            },
            divider = {}
        ) {
            ActressTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            tab.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val tab = ActressTabs[page]
            val active = pagerState.settledPage == page
            ActressListScreen(
                dataSourceType = tab.dataSourceType,
                active = active,
                onActressClick = onActressClick,
                viewModel = hiltViewModel(key = "actress_tab_$page")
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/ActressCategoryScreen.kt
git commit -m "feat: add ActressCategoryScreen with search bar and 2 tabs"
```

---

### Task 5: Create GenreCategoryScreen (dual-layer tabs)

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/GenreCategoryScreen.kt`

This is the most complex screen — it has outer tabs (有码/无码) and inner tabs (dynamic theme groups from `parseGenreCategories`). The inner tabs are titles from `GenreCategory.title`, and the content shows `AssistChip` list for the selected group.

- [ ] **Step 1: Create GenreCategoryScreen**

Create `app/src/main/java/me/jbusdriver/modern/ui/movielist/GenreCategoryScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.modern.ui.components.CategorySearchBar

private data class GenreSourceTab(
    val title: String,
    val dataSourceType: DataSourceType
)

private val GenreSourceTabs = listOf(
    GenreSourceTab("有码类别", DataSourceType.GENRE),
    GenreSourceTab("无码类别", DataSourceType.UNCENSORED_GENRE)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreCategoryScreen(
    onGenreClick: (GenreUiModel) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSourceIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedThemeIndex by rememberSaveable { mutableIntStateOf(0) }

    // One ViewModel per source tab (有码/无码)
    val viewModelKey = "genre_source_$selectedSourceIndex"
    val viewModel: GenreListViewModel = hiltViewModel(key = viewModelKey)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedSourceIndex) {
        viewModel.setDataSourceType(GenreSourceTabs[selectedSourceIndex].dataSourceType)
    }

    // Clamp inner tab when data changes
    val themeGroups = uiState.genreCategories
    LaunchedEffect(themeGroups) {
        if (selectedThemeIndex >= themeGroups.size) {
            selectedThemeIndex = 0
        }
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        CategorySearchBar(onClick = onSearchClick)

        // Outer tabs: 有码类别 / 无码类别
        ScrollableTabRow(
            selectedTabIndex = selectedSourceIndex,
            edgePadding = 8.dp,
            indicator = {
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(selectedSourceIndex, matchContentSize = false)
                )
            },
            divider = {}
        ) {
            GenreSourceTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedSourceIndex == index,
                    onClick = {
                        selectedSourceIndex = index
                        selectedThemeIndex = 0
                    },
                    text = {
                        Text(
                            tab.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = if (selectedSourceIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedSourceIndex == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }

        // Inner tabs: dynamic theme groups (题材, 系列, ...)
        if (themeGroups.isNotEmpty()) {
            val clampedIndex = selectedThemeIndex.coerceIn(0, themeGroups.size - 1)
            ScrollableTabRow(
                selectedTabIndex = clampedIndex,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                indicator = {
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(clampedIndex, matchContentSize = false)
                    )
                },
                divider = {}
            ) {
                themeGroups.forEachIndexed { index, category ->
                    Tab(
                        selected = clampedIndex == index,
                        onClick = { selectedThemeIndex = index },
                        text = {
                            Text(
                                category.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                fontWeight = if (clampedIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (clampedIndex == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        // Content: genre chips for selected theme group
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                uiState.error != null && themeGroups.isEmpty() -> {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(uiState.error ?: "載入失敗", color = MaterialTheme.colorScheme.error)
                    }
                }
                themeGroups.isNotEmpty() && clampedIndex in themeGroups.indices -> {
                    val genres = themeGroups[clampedIndex].genres
                    FlowRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        genres.forEach { genre ->
                            AssistChip(
                                onClick = { onGenreClick(genre) },
                                label = {
                                    Text(genre.name, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
```

**Note:** The `collectAsStateWithLifecycle` import requires `import androidx.lifecycle.compose.collectAsStateWithLifecycle`. The `rememberSaveable` import requires `import androidx.compose.runtime.saveable.rememberSaveable`.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/GenreCategoryScreen.kt
git commit -m "feat: add GenreCategoryScreen with dual-layer tabs and chip list"
```

---

### Task 6: Create CollectCategoryScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt`

- [ ] **Step 1: Create CollectCategoryScreen with search bar + 2 tabs**

Create `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.CategorySearchBar

private data class CollectTab(
    val title: String,
    val dbType: Int
)

private val CollectTabs = listOf(
    CollectTab("电影", MovieDBType),
    CollectTab("女优", ActressDBType)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectCategoryScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { CollectTabs.size }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        CategorySearchBar(onClick = onSearchClick)

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 8.dp,
            indicator = {
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(pagerState.currentPage, matchContentSize = false)
                )
            },
            divider = {}
        ) {
            CollectTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            tab.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val tab = CollectTabs[page]
            val active = pagerState.settledPage == page
            CollectionListScreen(
                dbType = tab.dbType,
                active = active,
                onMovieClick = onMovieClick,
                onActressClick = onActressClick,
                viewModel = hiltViewModel(key = "collect_tab_$page")
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt
git commit -m "feat: add CollectCategoryScreen with search bar and 2 tabs"
```

---

### Task 7: Update NavigationKeys and Navigation for defaultSearchType

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`

- [ ] **Step 1: Add search route with defaultSearchType parameter**

In `NavigationKeys.kt`, add a new constant and builder function. Keep the existing `ROUTE_SEARCH` for backward compatibility but add a new route pattern:

Replace the existing `ROUTE_SEARCH` constant and add a route builder:

```kotlin
// In NavigationKeys object, change:
const val ROUTE_SEARCH = "search"

// Add after ROUTE_SEARCH:
const val ROUTE_SEARCH_WITH_TYPE = "search?defaultSearchType={defaultSearchType}"

fun searchWithType(searchType: String): String = "search?defaultSearchType=$searchType"
```

- [ ] **Step 2: Update Navigation.kt to accept and pass defaultSearchType**

In `Navigation.kt`, change the `search` composable to accept the optional parameter and pass it to `SearchScreen`:

Replace the existing `composable(NavigationKeys.ROUTE_SEARCH, ...)` block with:

```kotlin
composable(
    route = NavigationKeys.ROUTE_SEARCH_WITH_TYPE,
    enterTransition = { /* keep existing animation */ },
    exitTransition = { /* keep existing animation */ },
    popEnterTransition = { /* keep existing animation */ },
    popExitTransition = { /* keep existing animation */ },
    arguments = listOf(
        navArgument("defaultSearchType") {
            type = NavType.StringType
            defaultValue = ""
        }
    )
) { backStackEntry ->
    val defaultSearchType = backStackEntry.arguments?.getString("defaultSearchType") ?: ""
    SearchScreen(
        defaultSearchType = defaultSearchType,
        onMovieClick = { movie ->
            navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
        },
        onActressClick = { actress ->
            navController.navigate(
                NavigationKeys.linkMovies(
                    actress.link,
                    actress.name,
                    type = "actress",
                    avatar = actress.avatar
                )
            )
        },
        onBack = { navController.popBackStack() }
    )
}
```

The full animation blocks remain identical to the current code. Only the route and arguments change, plus the `defaultSearchType` parameter passed to `SearchScreen`.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (SearchScreen will fail if not yet updated — see Task 8)

- [ ] **Step 4: Commit together with Task 8 changes**

---

### Task 8: Update SearchScreen to accept defaultSearchType

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`

- [ ] **Step 1: Add defaultSearchType parameter to SearchScreen**

Add a `defaultSearchType` parameter and a `LaunchedEffect` to apply it on first composition.

In `SearchScreen.kt`, add the parameter to the function signature:

```kotlin
@Composable
fun SearchScreen(
    defaultSearchType: String = "",
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
```

Add a `LaunchedEffect` right after the existing `LaunchedEffect(uiState.query)` block to apply the default search type once:

```kotlin
LaunchedEffect(defaultSearchType) {
    if (defaultSearchType.isNotBlank()) {
        try {
            val type = SearchType.valueOf(defaultSearchType)
            viewModel.setSearchType(type)
        } catch (_: IllegalArgumentException) {
            // Ignore invalid search type
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit (combined with Task 7)**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt \
        app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt \
        app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt
git commit -m "feat: add defaultSearchType parameter to search route and SearchScreen"
```

---

### Task 9: Rewrite MainScreen with bottom NavigationBar

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`

This is the core task — replace the entire MainScreen with the new bottom navigation structure.

- [ ] **Step 1: Rewrite MainScreen.kt**

Replace the entire content of `MainScreen.kt` with:

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.SearchType
import me.jbusdriver.modern.ui.movielist.ActressCategoryScreen
import me.jbusdriver.modern.ui.movielist.CollectCategoryScreen
import me.jbusdriver.modern.ui.movielist.GenreCategoryScreen
import me.jbusdriver.modern.ui.movielist.MovieCategoryScreen

enum class BottomNavCategory {
    MOVIE, ACTRESS, GENRE, COLLECT
}

private data class BottomNavItem(
    val category: BottomNavCategory,
    val label: String,
    val iconRes: Int,
    val defaultSearchType: SearchType
)

private val BottomNavItems = listOf(
    BottomNavItem(BottomNavCategory.MOVIE, "电影", R.drawable.movie_24px, SearchType.CENSORED),
    BottomNavItem(BottomNavCategory.ACTRESS, "演员", R.drawable.person_24px, SearchType.ACTRESS),
    BottomNavItem(BottomNavCategory.GENRE, "类别", R.drawable.category_24px, SearchType.CENSORED),
    BottomNavItem(BottomNavCategory.COLLECT, "收藏", R.drawable.favorite_24px, SearchType.CENSORED)
)

@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    BottomNavMainScreen(
        onMovieClick = onMovieClick,
        onActressClick = onActressClick,
        onGenreClick = onGenreClick,
        onSearchClick = onSearchClick
    )
}

@Composable
private fun BottomNavMainScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onGenreClick: (GenreUiModel) -> Unit,
    onSearchClick: (String) -> Unit
) {
    var selectedCategory by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(BottomNavCategory.MOVIE)
    }
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
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
                        label = { Text(item.label) }
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
                    BottomNavCategory.MOVIE -> MovieCategoryScreen(
                        onMovieClick = onMovieClick,
                        onSearchClick = { onSearchClick(SearchType.CENSORED.name) }
                    )
                    BottomNavCategory.ACTRESS -> ActressCategoryScreen(
                        onActressClick = onActressClick,
                        onSearchClick = { onSearchClick(SearchType.ACTRESS.name) }
                    )
                    BottomNavCategory.GENRE -> GenreCategoryScreen(
                        onGenreClick = onGenreClick,
                        onSearchClick = { onSearchClick(SearchType.CENSORED.name) }
                    )
                    BottomNavCategory.COLLECT -> CollectCategoryScreen(
                        onMovieClick = onMovieClick,
                        onActressClick = onActressClick,
                        onSearchClick = { onSearchClick(SearchType.CENSORED.name) }
                    )
                }
            }
        }
    }
}
```

**Required imports** (add these at the top):
```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
```

**Key changes:**
- `onSearchClick` callback signature in `BottomNavMainScreen` changes from `() -> Unit` to `(String) -> Unit` to pass the `SearchType.name` to the navigation
- `SaveableStateProvider` wraps each category screen keyed by `BottomNavCategory` enum, preserving tab state across switches
- `Scaffold` provides `innerPadding` to avoid overlap with NavigationBar

- [ ] **Step 2: Update Navigation.kt to use new onSearchClick signature**

In `Navigation.kt`, update the `MainScreen` call in the `ROUTE_MAIN` composable. The `onSearchClick` now passes a `defaultSearchType` string:

```kotlin
onSearchClick = { searchType ->
    navController.navigate(NavigationKeys.searchWithType(searchType))
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual test — launch app and verify**

- App opens to Movie tab (bottom nav shows 电影 selected)
- Tap actor/演员 bottom item → shows Actress tab with 有码/无码 tabs
- Tap 类别 bottom item → shows Genre with dual tabs
- Tap 收藏 bottom item → shows Collect with 电影/女优 tabs
- Switch between bottom items → each category remembers its tab position
- Search bar visible at top of each category → tapping navigates to search page
- Search page shows correct pre-selected search type

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt
git commit -m "feat: restructure MainScreen with bottom NavigationBar and 4 category screens"
```

---

### Task 10: Clean up removed code and verify full build

**Files:**
- Verify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt` (CategoryOption, CategoryPagerScreen removed)
- Verify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt` (search route updated)

- [ ] **Step 1: Verify CategoryOption and CategoryPagerScreen are removed from MainScreen.kt**

The old `CategoryOption`, `CategoryOptions`, `genreTypes`, and `CategoryPagerScreen` should no longer exist in `MainScreen.kt`. If they still remain as dead code, remove them.

- [ ] **Step 2: Verify no references to removed code**

Run: `grep -r "CategoryPagerScreen\|CategoryOption\|CategoryOptions" app/src/main/java/`
Expected: No matches (these were internal to MainScreen.kt)

- [ ] **Step 3: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit cleanup if needed**

```bash
git add -A
git commit -m "chore: clean up removed CategoryOption and CategoryPagerScreen"
```
(Only if there are remaining cleanup changes)

---

## Self-Review Checklist

- [x] **Spec coverage:** Each section in the spec maps to a task:
  - Bottom NavigationBar → Task 9
  - MovieCategoryScreen → Task 3
  - ActressCategoryScreen → Task 4
  - GenreCategoryScreen → Task 5
  - CollectCategoryScreen → Task 6
  - CategorySearchBar → Task 2
  - Search defaultSearchType → Tasks 7 & 8
  - State preservation → Task 9 (SaveableStateHolder)
  - Icons → Task 1
- [x] **Placeholder scan:** No TBDs, TODOs, or vague instructions
- [x] **Type consistency:** All DataSourceType, SearchType, dbType constants match existing code
- [x] **Import statements:** Called out explicitly where non-obvious
- [x] **ViewModel keying:** Consistent pattern `"<category>_tab_$page"` across all screens
