# Phase 4: Collection System Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the legacy collection/favorites system to modern Compose UI — add collect/uncollect functionality to the movie detail screen and actress detail screen.

**Architecture:** Create a `CollectRepository` interface that wraps legacy `DB.linkDao` calls in suspend functions, inject it into ViewModels via Hilt. ViewModels track collection state in `StateFlow`. Screens render collection toggle as a top-bar icon button.

**Tech Stack:** Kotlin, Hilt DI, Room (existing `CollectDatabase`/`LinkItemDao`), Compose Material3, StateFlow

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `modern/data/CollectRepository.kt` | Coroutine-safe collection CRUD — wraps `DB.linkDao` |
| Modify | `modern/data/di/DataModule.kt` | Bind `CollectRepository` |
| Modify | `modern/ui/UiModels.kt` | Add `Movie`/`ActressInfo` → `LinkItem` conversion helpers |
| Modify | `modern/ui/detail/MovieDetailViewModel.kt` | Add `isCollected` state + `toggleCollect()` |
| Modify | `modern/ui/detail/MovieDetailScreen.kt` | Add collect icon button in TopAppBar |
| Modify | `modern/ui/movielist/LinkMovieListViewModel.kt` | Add `isCollected` state + `toggleCollect()` for actress |
| Modify | `modern/ui/movielist/LinkMovieListScreen.kt` | Add collect icon button in TopAppBar |

---

### Task 1: Create CollectRepository

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: Create `CollectRepository.kt`**

```kotlin
package me.jbusdriver.modern.data

import me.jbusdriver.db.DB
import me.jbusdriver.db.entity.LinkItem
import me.jbusdriver.mvp.bean.ActressDBType
import me.jbusdriver.mvp.bean.MovieDBType
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.Movie
import javax.inject.Inject
import javax.inject.Singleton

interface CollectRepository {
    suspend fun isCollected(dbType: Int, key: String): Boolean
    suspend fun addCollect(linkItem: LinkItem): Boolean
    suspend fun removeCollect(dbType: Int, key: String): Boolean
    suspend fun isMovieCollected(movie: Movie): Boolean
    suspend fun isActressCollected(actress: ActressInfo): Boolean
}

@Singleton
class DefaultCollectRepository @Inject constructor() : CollectRepository {

    override suspend fun isCollected(dbType: Int, key: String): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.hasByKey(dbType, key) >= 1
        }
    }

    override suspend fun addCollect(linkItem: LinkItem): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.insert(linkItem)
            true
        }
    }

    override suspend fun removeCollect(dbType: Int, key: String): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.delete(dbType, key) > 0
        }
    }

    override suspend fun isMovieCollected(movie: Movie): Boolean {
        return isCollected(MovieDBType, movie.link.trim('/').substringAfterLast('/'))
    }

    override suspend fun isActressCollected(actress: ActressInfo): Boolean {
        return isCollected(ActressDBType, actress.link.trim('/').substringAfterLast('/'))
    }
}
```

Note: `ILink.uniqueKey` returns `link.urlPath` which is the URL path segment. The `LinkItem.key` column uses this value. We use `link.trim('/').substringAfterLast('/')` to match the `urlPath` behavior for simplicity — this matches how `convertDBItem()` sets the key.

Actually, we should use the existing `uniqueKey` property directly. Let me correct:

```kotlin
package me.jbusdriver.modern.data

import me.jbusdriver.db.DB
import me.jbusdriver.db.entity.LinkItem
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.uniqueKey
import javax.inject.Inject
import javax.inject.Singleton

interface CollectRepository {
    suspend fun isCollected(linkItem: LinkItem): Boolean
    suspend fun addCollect(linkItem: LinkItem): Boolean
    suspend fun removeCollect(linkItem: LinkItem): Boolean

    suspend fun isMovieCollected(movie: Movie): Boolean
    suspend fun toggleMovieCollect(movie: Movie): Boolean
    suspend fun isActressCollected(actress: ActressInfo): Boolean
    suspend fun toggleActressCollect(actress: ActressInfo): Boolean
}

@Singleton
class DefaultCollectRepository @Inject constructor() : CollectRepository {

    override suspend fun isCollected(linkItem: LinkItem): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.hasByKey(linkItem.dbType, linkItem.key) >= 1
        }
    }

    override suspend fun addCollect(linkItem: LinkItem): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.insert(linkItem)
            true
        }
    }

    override suspend fun removeCollect(linkItem: LinkItem): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.delete(linkItem.dbType, linkItem.key) > 0
        }
    }

    override suspend fun isMovieCollected(movie: Movie): Boolean {
        return isCollected(movie.convertDBItem())
    }

    override suspend fun toggleMovieCollect(movie: Movie): Boolean {
        val item = movie.convertDBItem()
        return if (isCollected(item)) {
            removeCollect(item)
            false
        } else {
            addCollect(item)
            true
        }
    }

    override suspend fun isActressCollected(actress: ActressInfo): Boolean {
        return isCollected(actress.convertDBItem())
    }

    override suspend fun toggleActressCollect(actress: ActressInfo): Boolean {
        val item = actress.convertDBItem()
        return if (isCollected(item)) {
            removeCollect(item)
            false
        } else {
            addCollect(item)
            true
        }
    }
}
```

- [ ] **Step 2: Register in DataModule.kt**

Add to `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`:

Add import:
```kotlin
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.DefaultCollectRepository
```

Add binding method inside the `DataModule` class:
```kotlin
    @Binds
    @Singleton
    abstract fun bindCollectRepository(
        impl: DefaultCollectRepository
    ): CollectRepository
```

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt
git commit -m "feat: add CollectRepository for modern collection system"
```

---

### Task 2: Add Collection State to MovieDetailViewModel

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`

- [ ] **Step 1: Update MovieDetailUiState to include collection state**

In `MovieDetailViewModel.kt`, update `MovieDetailUiState` data class to add `isCollected` field:

```kotlin
data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val movieDetail: MovieDetailUiModel? = null,
    val error: String? = null,
    val magnets: List<MagnetUiModel> = emptyList(),
    val isLoadingMagnets: Boolean = false,
    val magnetsError: String? = null,
    val hasMoreMagnets: Boolean = true,
    val isCollected: Boolean = false
)
```

- [ ] **Step 2: Add CollectRepository injection and collection methods to ViewModel**

Add imports:
```kotlin
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.mvp.bean.Movie
```

Update constructor to inject `CollectRepository`:
```kotlin
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository,
    private val collectRepository: CollectRepository
) : ViewModel() {
```

Add method to check collection state after detail loads. Modify `loadDetail` to also check collection:

After the line `_uiState.update { it.copy(movieDetail = detail.toUiModel(), isLoading = false) }` in `loadDetail`, add:
```kotlin
                    // Check collection state
                    val movie = Movie(
                        title = detail.title,
                        imageUrl = detail.cover,
                        code = detail.headers.firstOrNull()?.value ?: "",
                        date = "",
                        link = url
                    )
                    val collected = collectRepository.isMovieCollected(movie)
                    _uiState.update { it.copy(isCollected = collected) }
```

Add toggle method:
```kotlin
    fun toggleCollect() {
        val detail = _uiState.value.movieDetail ?: return
        val url = currentUrl
        viewModelScope.launch {
            val movie = Movie(
                title = detail.title,
                imageUrl = detail.cover,
                code = detail.headers.firstOrNull()?.value ?: "",
                date = "",
                link = url
            )
            val newState = collectRepository.toggleMovieCollect(movie)
            _uiState.update { it.copy(isCollected = newState) }
        }
    }
```

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt
git commit -m "feat: add collection state to MovieDetailViewModel"
```

---

### Task 3: Add Collection Icon to MovieDetailScreen

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`

- [ ] **Step 1: Add collect icon button to TopAppBar**

Add imports:
```kotlin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
```

In `MovieDetailScreen` composable, get the context:
```kotlin
    val context = LocalContext.current
```

Add this line after `val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()`:
```kotlin
    val detail = uiState.movieDetail
```

Then remove the duplicate `val detail = uiState.movieDetail` that's inside the Scaffold lambda (keep the one at the top level of the composable).

In the `TopAppBar` block, add an `actions` block after `navigationIcon`:
```kotlin
                actions = {
                    if (detail != null) {
                        IconButton(onClick = {
                            viewModel.toggleCollect()
                            val msg = if (!uiState.isCollected) "收藏成功" else "已取消收藏"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = if (uiState.isCollected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (uiState.isCollected) "取消收藏" else "收藏",
                                tint = if (uiState.isCollected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
```

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt
git commit -m "feat: add collect/uncollect icon to movie detail screen"
```

---

### Task 4: Add Collection State to LinkMovieListViewModel (Actress)

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt`

- [ ] **Step 1: Update LinkMovieListUiState to include collection state**

In `LinkMovieListViewModel.kt`, update `LinkMovieListUiState`:

```kotlin
data class LinkMovieListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val actressDetail: ActressDetailUiModel? = null,
    val isLoadingActress: Boolean = false,
    val isCollected: Boolean = false
)
```

- [ ] **Step 2: Inject CollectRepository and add collection methods**

Add imports:
```kotlin
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.mvp.bean.ActressInfo
```

Update constructor:
```kotlin
@HiltViewModel
class LinkMovieListViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val collectRepository: CollectRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
```

Add a method to toggle collection for the actress. When the actress detail is loaded, also check collection state. Modify `loadActressDetail()` — after the state update where `actressDetail` is set, add collection check:

In `loadActressDetail()`, after `_uiState.update { it.copy(actressDetail = ..., isLoadingActress = false) }`, add:
```kotlin
                    val actress = ActressInfo(
                        name = detail.name,
                        avatar = detail.avatar,
                        link = linkUrl
                    )
                    val collected = collectRepository.isActressCollected(actress)
                    _uiState.update { it.copy(isCollected = collected) }
```

Add toggle method:
```kotlin
    fun toggleActressCollect() {
        val actressDetail = _uiState.value.actressDetail ?: return
        viewModelScope.launch {
            val actress = ActressInfo(
                name = actressDetail.name,
                avatar = actressDetail.avatar,
                link = linkUrl
            )
            val newState = collectRepository.toggleActressCollect(actress)
            _uiState.update { it.copy(isCollected = newState) }
        }
    }
```

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt
git commit -m "feat: add collection state to LinkMovieListViewModel for actress"
```

---

### Task 5: Add Collection Icon to LinkMovieListScreen (Actress)

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt`

- [ ] **Step 1: Add collect icon button to TopAppBar (only for actress type)**

Add imports:
```kotlin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
```

In `LinkMovieListScreen`, get the context:
```kotlin
    val context = LocalContext.current
```

In the `TopAppBar` block, add an `actions` block after `navigationIcon`:
```kotlin
                actions = {
                    if (type == "actress" && uiState.actressDetail != null) {
                        IconButton(onClick = {
                            viewModel.toggleActressCollect()
                            val msg = if (!uiState.isCollected) "收藏成功" else "已取消收藏"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = if (uiState.isCollected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (uiState.isCollected) "取消收藏" else "收藏",
                                tint = if (uiState.isCollected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
```

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt
git commit -m "feat: add collect/uncollect icon to actress detail screen"
```

---

### Task 6: Final Build Verification

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual smoke test checklist**

On device/emulator, verify:
1. Open a movie detail → heart icon shows outlined (not collected)
2. Tap heart → turns filled red, toast "收藏成功"
3. Back and re-enter same movie → heart still filled (persisted)
4. Tap filled heart → turns outlined, toast "已取消收藏"
5. Navigate to actress detail (tap an actress from movie detail) → heart icon shows
6. Tap heart → collects actress, tap again → uncollects
7. Back and re-enter actress → collection state persisted
8. Genre list page → no heart icon (not applicable)

- [ ] **Step 3: Final commit if any fixes needed**

---

## Self-Review

**1. Spec coverage:** The user requested collection migration for movie detail and actress detail. Tasks 1-5 cover all of this.

**2. Placeholder scan:** No TBD/TODO/fill-in-later. All code is concrete.

**3. Type consistency:**
- `CollectRepository` interface matches `DefaultCollectRepository` implementation
- `MovieDetailViewModel` uses `CollectRepository` (injected via Hilt)
- `LinkMovieListViewModel` uses `CollectRepository` (injected via Hilt)
- `Movie` constructor matches `(title, imageUrl, code, date, link)` from `Movie.kt:12`
- `ActressInfo` constructor matches `(name, avatar, link)` from `MovieDetail.kt:47`
- `convertDBItem()` is an extension on `ILink` which both `Movie` and `ActressInfo` implement
- `uniqueKey` returns `link.urlPath` — `LinkItem.key` uses this
- `LinkItemDao.hasByKey(dbType, key)` matches our calls
