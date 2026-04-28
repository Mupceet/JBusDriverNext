# Home Screen Category Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two-level category UI (ScrollableTabRow + FilterChip) with a compact TopAppBar title DropdownMenu, add collection lists as a new main category.

**Architecture:** Replace `CategoryGroups` nested structure with a flat `CategoryOption` list. TopAppBar title becomes clickable to show a DropdownMenu with 8 grouped options. Collection lists load from local Room DB via `CollectRepository` into a new `CollectionListViewModel` + `CollectionListScreen`.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 DropdownMenu), Hilt, Room, StateFlow

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `modern/ui/MainScreen.kt` | Remove tabs/chips, add DropdownMenu, route to collection screen |
| Modify | `modern/data/CollectRepository.kt` | Add `getCollectedMovies()` and `getCollectedActresses()` |
| Create | `modern/ui/movielist/CollectionListScreen.kt` | Display collected movies or actresses from local DB |
| Create | `modern/ui/movielist/CollectionListViewModel.kt` | Load collected items from Room, convert to UI models |

---

### Task 1: Add Collection Query Methods to CollectRepository

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt`

- [ ] **Step 1: Add query methods to CollectRepository interface and implementation**

Add imports at top of file:
```kotlin
import me.jbusdriver.mvp.bean.ActressDBType
import me.jbusdriver.mvp.bean.MovieDBType
```

Add two methods to `CollectRepository` interface (after line 19):
```kotlin
    suspend fun getCollectedMovies(): List<Movie>
    suspend fun getCollectedActresses(): List<ActressInfo>
```

Add implementations to `DefaultCollectRepository` (after line 72):
```kotlin
    override suspend fun getCollectedMovies(): List<Movie> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.listByType(MovieDBType).mapNotNull { it.getLinkValue() as? Movie }
        }
    }

    override suspend fun getCollectedActresses(): List<ActressInfo> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.listByType(ActressDBType).mapNotNull { it.getLinkValue() as? ActressInfo }
        }
    }
```

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt
git commit -m "feat: add getCollectedMovies and getCollectedActresses to CollectRepository"
```

---

### Task 2: Create CollectionListViewModel

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`

- [ ] **Step 1: Create CollectionListViewModel.kt**

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import me.jbusdriver.modern.ui.toUiModel
import me.jbusdriver.mvp.bean.ActressDBType
import me.jbusdriver.mvp.bean.MovieDBType
import javax.inject.Inject

data class CollectionListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val actresses: List<ActressUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CollectionListViewModel @Inject constructor(
    private val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionListUiState())
    val uiState: StateFlow<CollectionListUiState> = _uiState.asStateFlow()

    fun loadCollection(dbType: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, movies = emptyList(), actresses = emptyList()) }
            try {
                if (dbType == MovieDBType) {
                    val movies = collectRepository.getCollectedMovies().map { it.toUiModel() }
                    _uiState.update { it.copy(movies = movies, isLoading = false) }
                } else {
                    val actresses = collectRepository.getCollectedActresses().map { it.toActressUiModel() }
                    _uiState.update { it.copy(actresses = actresses, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载收藏失败") }
            }
        }
    }

    fun refresh(dbType: Int) {
        loadCollection(dbType)
    }
}
```

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt
git commit -m "feat: add CollectionListViewModel for loading local collection data"
```

---

### Task 3: Create CollectionListScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt`

- [ ] **Step 1: Create CollectionListScreen.kt**

This screen reuses the existing `MovieItem` from `MovieListScreen` for movies, and displays actresses in a grid similar to `ActressListScreen`.

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.mvp.bean.ActressDBType
import me.jbusdriver.mvp.bean.MovieDBType

@Composable
fun CollectionListScreen(
    dbType: Int,
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CollectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(dbType) {
        viewModel.loadCollection(dbType)
    }

    when {
        uiState.isLoading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
            }
        }
        dbType == MovieDBType -> {
            if (uiState.movies.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = modifier.fillMaxSize()) {
                    itemsIndexed(uiState.movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
                        MovieItem(movie = movie, onClick = { onMovieClick(movie) })
                    }
                }
            }
        }
        dbType == ActressDBType -> {
            if (uiState.actresses.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.actresses, key = { index, actress -> "${index}_${actress.link}" }) { _, actress ->
                        ActressGridItem(actress = actress, onClick = { onActressClick(actress) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ActressGridItem(
    actress: ActressUiModel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = actress.avatar,
            contentDescription = actress.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = actress.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt
git commit -m "feat: add CollectionListScreen for displaying collected movies and actresses"
```

---

### Task 4: Rewrite MainScreen with DropdownMenu

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`

Replace the entire file content.

- [ ] **Step 1: Rewrite MainScreen.kt**

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import me.jbusdriver.modern.ui.movielist.ActressListScreen
import me.jbusdriver.modern.ui.movielist.CollectionListScreen
import me.jbusdriver.modern.ui.movielist.GenreListScreen
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.search.SearchScreen
import me.jbusdriver.modern.ui.settings.SettingsScreen
import me.jbusdriver.mvp.bean.ActressDBType
import me.jbusdriver.mvp.bean.MovieDBType
import me.jbusdriver.ui.data.enums.DataSourceType

data class CategoryOption(
    val group: String,
    val name: String,
    val dataSourceType: DataSourceType? = null,
    val collectionDbType: Int = 0
)

val CategoryOptions = listOf(
    CategoryOption("有码", "电影", DataSourceType.CENSORED),
    CategoryOption("有码", "演员", DataSourceType.ACTRESSES),
    CategoryOption("有码", "类别", DataSourceType.GENRE),
    CategoryOption("无码", "电影", DataSourceType.UNCENSORED),
    CategoryOption("无码", "演员", DataSourceType.UNCENSORED_ACTRESSES),
    CategoryOption("无码", "类别", DataSourceType.UNCENSORED_GENRE),
    CategoryOption("收藏", "电影", collectionDbType = MovieDBType),
    CategoryOption("收藏", "演员", collectionDbType = ActressDBType),
)

private val genreTypes = setOf(
    DataSourceType.GENRE,
    DataSourceType.UNCENSORED_GENRE
)

@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {}
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedOptionIndex by rememberSaveable { mutableIntStateOf(0) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val currentOption = CategoryOptions[selectedOptionIndex]
    val topBarTitle = when (selectedTabIndex) {
        0 -> "${currentOption.group} · ${currentOption.name}"
        1 -> "搜索"
        2 -> "设置"
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedTabIndex == 0) {
                        Box {
                            Row(
                                modifier = Modifier.clickable { showCategoryMenu = !showCategoryMenu },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    topBarTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    " ▾",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                CategoryOptions.forEachIndexed { index, option ->
                                    if (index > 0 && option.group != CategoryOptions[index - 1].group) {
                                        HorizontalDivider()
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            val isSelected = index == selectedOptionIndex
                                            Text(
                                                "${option.group} · ${option.name}",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedOptionIndex = index
                                            showCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(topBarTitle)
                    }
                }
            )
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
                    val dsType = currentOption.dataSourceType
                    when {
                        dsType != null && (dsType == DataSourceType.ACTRESSES || dsType == DataSourceType.UNCENSORED_ACTRESSES) -> ActressListScreen(
                            dataSourceType = dsType,
                            onActressClick = onActressClick,
                            modifier = Modifier.weight(1f)
                        )
                        dsType != null && dsType in genreTypes -> GenreListScreen(
                            dataSourceType = dsType,
                            onGenreClick = onGenreClick,
                            modifier = Modifier.weight(1f)
                        )
                        dsType != null -> MovieListScreen(
                            dataSourceType = dsType,
                            onMovieClick = onMovieClick,
                            modifier = Modifier.weight(1f)
                        )
                        else -> CollectionListScreen(
                            dbType = currentOption.collectionDbType,
                            onMovieClick = onMovieClick,
                            onActressClick = onActressClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
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

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt
git commit -m "feat: replace category tabs/chips with TopAppBar DropdownMenu"
```

---

### Task 5: Final Build Verification

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual smoke test checklist**

On device/emulator, verify:
1. Home screen shows "有码 · 电影" title with "▾" arrow, no tabs/chips above the list
2. Click title → DropdownMenu appears with 8 options in 3 groups
3. Select "有码 · 演员" → actress grid loads
4. Select "有码 · 类别" → genre list loads
5. Select "无码 · 电影" → uncensored movie list loads
6. Select "收藏 · 电影" → shows "还没有收藏" if empty, or collected movies
7. Select "收藏 · 演员" → shows "还没有收藏" if empty, or collected actresses
8. Search and Settings tabs still work normally
9. Bottom navigation still switches between 3 tabs

- [ ] **Step 3: Final commit if any fixes needed**

---

## Self-Review

**1. Spec coverage:**
- New category structure (有码/无码/收藏): Task 4 `CategoryOptions`
- TopAppBar title dropdown: Task 4 DropdownMenu in TopAppBar
- Remove ScrollableTabRow + FilterChip: Task 4 (replaced entirely)
- Collection lists (movies + actresses): Tasks 1-3 (repo methods + ViewModel + Screen)
- Empty state "还没有收藏": Task 3 CollectionListScreen
- Dropdown grouped by main category with dividers: Task 4

**2. Placeholder scan:** No TBD/TODO. All code is concrete.

**3. Type consistency:**
- `CategoryOption.collectionDbType` is `Int` → passed to `CollectionListScreen(dbType: Int)` → used in `CollectionListViewModel.loadCollection(dbType: Int)` → compared against `MovieDBType`/`ActressDBType`
- `CategoryOption.dataSourceType` is `DataSourceType?` → null for collection types → `when` branch falls through to `CollectionListScreen`
- `CollectRepository.getCollectedMovies()` returns `List<Movie>` → mapped to `List<MovieUiModel>` via `.toUiModel()`
- `CollectRepository.getCollectedActresses()` returns `List<ActressInfo>` → mapped to `List<ActressUiModel>` via `.toActressUiModel()`
- `MovieItem` is used from `MovieListScreen.kt` — it's a `@Composable` function in the same package, accessible without import
