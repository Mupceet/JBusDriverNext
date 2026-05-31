# Collection Filter & Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add filter and sort capabilities to the collection list so users can find collected content by type, date, and time.

**Architecture:** Pure in-memory filtering/sorting. ViewModel loads all collected items from Room via new `getCollectedLinkItems()`, stores raw lists, and applies filter/sort using Kotlin collection operations. A Bottom Sheet provides the filter/sort UI. No database migration required.

**Tech Stack:** Jetpack Compose Material3 (ModalBottomSheet, FilterChip, DropdownMenu), Kotlin Coroutines, Room, Hilt

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `ui/movielist/CollectionFilterState.kt` | CensorFilter enum, SortOption enum, CollectionFilterState data class, AvailableYears data class |
| Modify | `ui/UiModels.kt` | Add `createTime: Long` to MovieUiModel and ActressUiModel |
| Modify | `data/CollectRepository.kt` | Add `getCollectedLinkItems(dbType)` to interface and implementation |
| Rewrite | `ui/movielist/CollectionListViewModel.kt` | Store raw data, filter/sort logic, available years computation |
| Create | `ui/movielist/CollectionFilterSheet.kt` | Bottom Sheet composable with filter chips and sort dropdown |
| Modify | `ui/movielist/CollectionListScreen.kt` | Add filter button, wire Bottom Sheet to ViewModel |

---

### Task 1: Create filter/sort state classes

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterState.kt`

- [ ] **Step 1: Create the state file**

```kotlin
package me.jbusdriver.modern.ui.movielist

/**
 * 内容类型筛选（有码/无码）
 */
enum class CensorFilter { ALL, CENSORED, UNCENSORED }

/**
 * 排序选项。
 *
 * [label] 为 Bottom Sheet 排序下拉中显示的文本。
 * [movieOptions] 影片列表可用的排序选项。
 * [actressOptions] 演员列表可用的排序选项（仅收藏时间）。
 */
enum class SortOption(val label: String) {
    COLLECT_DESC("收藏时间倒序"),
    COLLECT_ASC("收藏时间正序"),
    PUBLISH_DESC("发布时间倒序"),
    PUBLISH_ASC("发布时间正序");

    companion object {
        val movieOptions: List<SortOption> = entries
        val actressOptions: List<SortOption> = listOf(COLLECT_DESC, COLLECT_ASC)
    }
}

/**
 * 收藏列表的筛选和排序状态。
 *
 * 年份字段用 Int? 表示：null = 全部，正整数 = 具体年份，-1 = 更早（早于数据中最早年份）。
 */
data class CollectionFilterState(
    val censorFilter: CensorFilter = CensorFilter.ALL,
    val publishYear: Int? = null,
    val publishMonth: Int? = null,
    val collectYear: Int? = null,
    val sortOption: SortOption = SortOption.COLLECT_DESC
) {
    /** 是否有非默认的筛选条件（排序不算） */
    val hasActiveFilters: Boolean
        get() = censorFilter != CensorFilter.ALL
                || publishYear != null
                || publishMonth != null
                || collectYear != null

    /** 激活的筛选条件数量（用于筛选按钮上的 badge） */
    val activeFilterCount: Int
        get() = listOf(
            censorFilter != CensorFilter.ALL,
            publishYear != null,
            publishMonth != null,
            collectYear != null
        ).count { it }
}

/**
 * 从收藏数据中动态提取的可用年份列表（降序）。
 *
 * 用于生成筛选面板中的年份 Chip。
 */
data class AvailableYears(
    val publishYears: List<Int> = emptyList(),
    val collectYears: List<Int> = emptyList()
)
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterState.kt
git commit -m "feat: add CollectionFilterState, SortOption, AvailableYears for collection filter"
```

---

### Task 2: Add createTime to UI models

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt`

Both `MovieUiModel` and `ActressUiModel` need a `createTime` field for sort and filter by collection time. Default value `0L` keeps existing callers working.

- [ ] **Step 1: Add createTime to MovieUiModel**

In `UiModels.kt`, change `MovieUiModel` from:

```kotlin
@Immutable
data class MovieUiModel(
    val title: String,
    val imageUrl: String,
    val code: String,
    val date: String,
    val link: String,
    val tags: List<String> = emptyList()
)
```

to:

```kotlin
@Immutable
data class MovieUiModel(
    val title: String,
    val imageUrl: String,
    val code: String,
    val date: String,
    val link: String,
    val tags: List<String> = emptyList(),
    val createTime: Long = 0L
)
```

- [ ] **Step 2: Add createTime to ActressUiModel**

In `UiModels.kt`, change `ActressUiModel` from:

```kotlin
@Immutable
data class ActressUiModel(val name: String, val avatar: String, val link: String)
```

to:

```kotlin
@Immutable
data class ActressUiModel(val name: String, val avatar: String, val link: String, val createTime: Long = 0L)
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt
git commit -m "feat: add createTime to MovieUiModel and ActressUiModel for collection filter/sort"
```

---

### Task 3: Add getCollectedLinkItems to repository

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt`

The existing `getCollectedMovies()` returns `List<Movie>` which loses `createTime`. Add a method that returns raw `LinkItem` so the ViewModel can preserve `createTime`.

- [ ] **Step 1: Add interface method**

In `CollectRepository` interface, after `getCollectedActresses()`, add:

```kotlin
    /** 获取指定类型的原始收藏数据（包含 createTime），用于筛选和排序 */
    suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem>
```

- [ ] **Step 2: Add implementation**

In `DefaultCollectRepository`, after `getCollectedActresses()`, add:

```kotlin
    override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> {
        return withContext(Dispatchers.IO) {
            linkDao.listByType(dbType)
        }
    }
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt
git commit -m "feat: add getCollectedLinkItems to CollectRepository for filter/sort support"
```

---

### Task 4: Rewrite CollectionListViewModel with filter/sort logic

**Files:**
- Rewrite: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`

Major changes:
- Load raw `LinkItem` via `getCollectedLinkItems()`, preserve `createTime`
- Add `filterState` and `availableYears` to UI state
- Add `updateFilter()` method that re-applies filter/sort immediately
- Filter/sort logic uses private extension functions

- [ ] **Step 1: Replace the entire file**

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.urlPath
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import me.jbusdriver.modern.ui.toUiModel
import java.util.Calendar
import javax.inject.Inject

/**
 * 收藏列表页的 UI 状态。
 *
 * [movies] 和 [actresses] 为经过筛选和排序后的展示列表。
 * [movieCount] 和 [actressCount] 为未筛选的总数（用于 tab badge）。
 */
data class CollectionListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val actresses: List<ActressUiModel> = emptyList(),
    val movieCount: Int = 0,
    val actressCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val filterState: CollectionFilterState = CollectionFilterState(),
    val availableYears: AvailableYears = AvailableYears()
)

/**
 * 收藏列表页 ViewModel。
 *
 * 职责：从本地数据库加载用户收藏，支持筛选和排序。
 * 筛选和排序在内存中完成，不涉及数据库查询变更。
 *
 * @param collectRepository 收藏仓库，负责查询本地收藏数据
 */
@HiltViewModel
class CollectionListViewModel @Inject constructor(
    val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionListUiState())
    val uiState: StateFlow<CollectionListUiState> = _uiState.asStateFlow()

    /** 未筛选的原始影片数据 */
    private var allMovies: List<MovieUiModel> = emptyList()
    /** 未筛选的原始演员数据 */
    private var allActresses: List<ActressUiModel> = emptyList()
    private var currentDbType: Int = MovieDBType

    /**
     * 加载指定类型的收藏列表。
     *
     * 同时加载两种类型的数据（用于 tab badge 计数和收藏时间年份提取），
     * 但仅对 [dbType] 对应的列表应用当前筛选。
     */
    fun loadCollection(dbType: Int) {
        currentDbType = dbType
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val movieItems = collectRepository.getCollectedLinkItems(MovieDBType)
                val actressItems = collectRepository.getCollectedLinkItems(ActressDBType)

                allMovies = movieItems.mapNotNull { item ->
                    ((item.toILink() as? Movie)?.toUiModel())
                        ?.copy(createTime = item.createTime)
                }
                allActresses = actressItems.mapNotNull { item ->
                    ((item.toILink() as? ActressInfo)?.toActressUiModel())
                        ?.copy(createTime = item.createTime)
                }

                updateAvailableYears()
                applyFilterAndSort()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入收藏失敗") }
            }
        }
    }

    /**
     * 更新筛选/排序条件，立即重新计算展示列表。
     */
    fun updateFilter(filterState: CollectionFilterState) {
        _uiState.update { it.copy(filterState = filterState) }
        applyFilterAndSort()
    }

    /**
     * 从收藏中移除指定电影。
     */
    fun removeMovie(movie: MovieUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val linkItem = LinkItem(
                    dbType = MovieDBType,
                    key = movie.link.urlPath,
                    jsonStr = "",
                    createTime = System.currentTimeMillis()
                )
                collectRepository.removeCollect(linkItem)
                loadCollection(MovieDBType)
            } catch (_: Exception) {}
        }
    }

    /**
     * 从收藏中移除指定演员。
     */
    fun removeActress(actress: ActressUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val linkItem = LinkItem(
                    dbType = ActressDBType,
                    key = actress.link.urlPath,
                    jsonStr = "",
                    createTime = System.currentTimeMillis()
                )
                collectRepository.removeCollect(linkItem)
                loadCollection(ActressDBType)
            } catch (_: Exception) {}
        }
    }

    /**
     * 刷新收藏列表。
     */
    fun refresh(dbType: Int) {
        loadCollection(dbType)
    }

    // region 内部方法

    /** 从原始数据中提取可用年份，用于生成筛选 Chip */
    private fun updateAvailableYears() {
        val publishYears = allMovies
            .mapNotNull { it.date.take(4).toIntOrNull() }
            .distinct()
            .sortedDescending()

        val collectYears = (allMovies + allActresses)
            .map { it.createTime.toYear() }
            .distinct()
            .sortedDescending()

        _uiState.update { it.copy(availableYears = AvailableYears(publishYears, collectYears)) }
    }

    /** 对原始数据应用当前筛选和排序，更新 UI 状态 */
    private fun applyFilterAndSort() {
        val filter = _uiState.value.filterState
        val years = _uiState.value.availableYears

        val filteredMovies = allMovies
            .filterByCensor(filter.censorFilter)
            .filterByPublishYear(filter.publishYear, years.publishYears)
            .filterByPublishMonth(filter.publishMonth)
            .filterByCollectYear(filter.collectYear, years.collectYears) { it.createTime }
            .sortedWith(filter.sortOption.toMovieComparator())

        val filteredActresses = allActresses
            .filterByCollectYear(filter.collectYear, years.collectYears) { it.createTime }
            .sortedWith(
                if (filter.sortOption in SortOption.actressOptions) filter.sortOption.toActressComparator()
                else SortOption.COLLECT_DESC.toActressComparator()
            )

        _uiState.update {
            it.copy(
                movies = filteredMovies,
                actresses = filteredActresses,
                movieCount = allMovies.size,
                actressCount = allActresses.size,
                isLoading = false
            )
        }
    }

// endregion
}

// region 筛选扩展函数

private fun List<MovieUiModel>.filterByCensor(censor: CensorFilter): List<MovieUiModel> =
    when (censor) {
        CensorFilter.ALL -> this
        CensorFilter.CENSORED -> filter { !it.link.urlPath.startsWith("/uncensored/") }
        CensorFilter.UNCENSORED -> filter { it.link.urlPath.startsWith("/uncensored/") }
    }

private fun List<MovieUiModel>.filterByPublishYear(
    year: Int?,
    available: List<Int>
): List<MovieUiModel> =
    if (year == null) this
    else filter { m ->
        val mYear = m.date.take(4).toIntOrNull() ?: return@filter false
        if (year == -1) mYear < (available.minOrNull() ?: Int.MAX_VALUE)
        else mYear == year
    }

private fun List<MovieUiModel>.filterByPublishMonth(month: Int?): List<MovieUiModel> =
    if (month == null) this
    else filter { m ->
        m.date.length >= 7 && m.date.substring(5, 7).toIntOrNull() == month
    }

private fun <T> List<T>.filterByCollectYear(
    year: Int?,
    available: List<Int>,
    getTime: (T) -> Long
): List<T> =
    if (year == null) this
    else filter { item ->
        val itemYear = getTime(item).toYear()
        if (year == -1) itemYear < (available.minOrNull() ?: Int.MAX_VALUE)
        else itemYear == year
    }

private fun SortOption.toMovieComparator(): Comparator<MovieUiModel> = when (this) {
    SortOption.COLLECT_DESC -> compareByDescending { it.createTime }
    SortOption.COLLECT_ASC -> compareBy { it.createTime }
    SortOption.PUBLISH_DESC -> compareByDescending { it.date }
    SortOption.PUBLISH_ASC -> compareBy { it.date }
}

private fun SortOption.toActressComparator(): Comparator<ActressUiModel> = when (this) {
    SortOption.COLLECT_DESC -> compareByDescending { it.createTime }
    SortOption.COLLECT_ASC -> compareBy { it.createTime }
    else -> compareByDescending { it.createTime }
}

/** 将毫秒时间戳转换为年份 */
private fun Long.toYear(): Int =
    Calendar.getInstance().apply { timeInMillis = this@toYear }.get(Calendar.YEAR)

// endregion
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt
git commit -m "feat: rewrite CollectionListViewModel with in-memory filter/sort logic"
```

---

### Task 5: Create CollectionFilterSheet composable

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt`

Bottom Sheet UI with:
- Top row: reset button (left, visible when filters active) + sort dropdown (right)
- Content type chips (movie-only)
- Publish date year/month chips (movie-only, month row shows only when a year is selected)
- Collect time year chips (both types)
- All clicks apply immediately via `onFilterChange`

- [ ] **Step 1: Create the composable file**

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.modern.data.db.MovieDBType

/**
 * 收藏筛选 Bottom Sheet。
 *
 * 首行：重置按钮（左，仅在有筛选条件时显示）+ 排序下拉（右）。
 * 主区域：筛选 Chip，点击即时生效。
 *
 * @param dbType 当前列表类型（MovieDBType 或 ActressDBType），决定显示哪些筛选维度
 * @param filterState 当前筛选/排序状态
 * @param availableYears 从数据中提取的可用年份
 * @param onFilterChange 筛选变更回调（即时生效）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionFilterSheet(
    dbType: Int,
    filterState: CollectionFilterState,
    availableYears: AvailableYears,
    onFilterChange: (CollectionFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp)
        ) {
            // ── Top row: Reset + Sort dropdown ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (filterState.hasActiveFilters) {
                    TextButton(onClick = { onFilterChange(CollectionFilterState()) }) {
                        Text("重置")
                    }
                }
                Spacer(Modifier.weight(1f))
                SortDropdown(
                    dbType = dbType,
                    current = filterState.sortOption,
                    onSelect = { onFilterChange(filterState.copy(sortOption = it)) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Censor filter (movie only) ──
            if (dbType == MovieDBType) {
                FilterSectionLabel("内容类型")
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CensorChip(
                        label = "全部",
                        selected = filterState.censorFilter == CensorFilter.ALL,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.ALL)) }
                    )
                    CensorChip(
                        label = "有碼",
                        selected = filterState.censorFilter == CensorFilter.CENSORED,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.CENSORED)) }
                    )
                    CensorChip(
                        label = "無碼",
                        selected = filterState.censorFilter == CensorFilter.UNCENSORED,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.UNCENSORED)) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Publish date (movie only) ──
                FilterSectionLabel("发布日期")
                Spacer(Modifier.height(6.dp))
                YearChipRow(
                    selectedYear = filterState.publishYear,
                    years = availableYears.publishYears,
                    onSelect = { year ->
                        onFilterChange(filterState.copy(publishYear = year, publishMonth = null))
                    }
                )

                // Month row: only show when a specific year is selected (not "全部" or "更早")
                if (filterState.publishYear != null && filterState.publishYear > 0) {
                    Spacer(Modifier.height(8.dp))
                    MonthChipRow(
                        selectedMonth = filterState.publishMonth,
                        onSelect = { month ->
                            onFilterChange(filterState.copy(publishMonth = month))
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            // ── Collect time (both) ──
            FilterSectionLabel("收藏时间")
            Spacer(Modifier.height(6.dp))
            YearChipRow(
                selectedYear = filterState.collectYear,
                years = availableYears.collectYears,
                onSelect = { year ->
                    onFilterChange(filterState.copy(collectYear = year))
                }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// region Internal composables

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CensorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 14.sp) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearChipRow(
    selectedYear: Int?,
    years: List<Int>,
    onSelect: (Int?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedYear == null,
            onClick = { onSelect(null) },
            label = { Text("全部", fontSize = 13.sp) }
        )
        years.forEach { year ->
            FilterChip(
                selected = selectedYear == year,
                onClick = { onSelect(year) },
                label = { Text(year.toString(), fontSize = 13.sp) }
            )
        }
        if (years.isNotEmpty()) {
            FilterChip(
                selected = selectedYear == -1,
                onClick = { onSelect(-1) },
                label = { Text("更早", fontSize = 13.sp) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthChipRow(
    selectedMonth: Int?,
    onSelect: (Int?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selectedMonth == null,
            onClick = { onSelect(null) },
            label = { Text("全部", fontSize = 12.sp) }
        )
        (1..12).forEach { month ->
            FilterChip(
                selected = selectedMonth == month,
                onClick = { onSelect(month) },
                label = { Text("${month}月", fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun SortDropdown(
    dbType: Int,
    current: SortOption,
    onSelect: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = if (dbType == MovieDBType) SortOption.movieOptions else SortOption.actressOptions

    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(current.label, fontSize = 13.sp)
            Spacer(Modifier.width(2.dp))
            Text("▾", fontSize = 10.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontSize = 14.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// endregion
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt
git commit -m "feat: add CollectionFilterSheet Bottom Sheet composable"
```

---

### Task 6: Wire filter button into CollectionListScreen

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt`

Add a filter chip button at the top of the list. When tapped, opens the `CollectionFilterSheet`. Only shows when there are items in the collection (based on total count, not filtered count).

- [ ] **Step 1: Replace the entire file**

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ActressGrid
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel

@Composable
fun CollectionListScreen(
    dbType: Int,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    viewModel: CollectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isGrid by hiltViewModel<UiPrefsViewModel>().store.isGrid.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(dbType, active) {
        if (active) {
            viewModel.loadCollection(dbType)
        }
    }

    // Show filter button when there are items (use total count, not filtered)
    val hasItems = if (dbType == MovieDBType) uiState.movieCount > 0 else uiState.actressCount > 0

    Column(modifier = modifier.fillMaxSize()) {
        // Filter bar
        if (hasItems) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.filterState.hasActiveFilters,
                    onClick = { showFilterSheet = true },
                    label = {
                        if (uiState.filterState.hasActiveFilters) {
                            Text("筛选 (${uiState.filterState.activeFilterCount})", fontSize = 12.sp)
                        } else {
                            Text("筛选", fontSize = 12.sp)
                        }
                    }
                )
            }
        }

        // Content
        when {
            uiState.isLoading && uiState.movies.isEmpty() && uiState.actresses.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.movies.isEmpty() && uiState.actresses.isEmpty() -> {
                ErrorView(
                    message = "載入失敗，請重試",
                    onRetry = { viewModel.loadCollection(dbType) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            dbType == MovieDBType -> {
                if (uiState.movies.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (hasItems) "没有匹配的筛选结果" else "還沒有收藏",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    MovieList(
                        movies = uiState.movies,
                        onMovieClick = onMovieClick,
                        isCollected = { true },
                        onToggleCollect = { viewModel.removeMovie(it) },
                        isGrid = isGrid,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            dbType == ActressDBType -> {
                if (uiState.actresses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (hasItems) "没有匹配的筛选结果" else "還沒有收藏",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    ActressGrid(
                        actresses = uiState.actresses,
                        onActressClick = onActressClick,
                        onActressLongClick = { viewModel.removeActress(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        CollectionFilterSheet(
            dbType = dbType,
            filterState = uiState.filterState,
            availableYears = uiState.availableYears,
            onFilterChange = { viewModel.updateFilter(it) },
            onDismiss = { showFilterSheet = false }
        )
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt
git commit -m "feat: add filter button and Bottom Sheet to collection list screen"
```

---

### Task 7: Build and verify end-to-end

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual verification checklist**

Install the debug APK on a device or emulator and verify:

1. **收藏 tab 正常显示** — 影片和演员列表正常加载
2. **筛选按钮出现** — 有收藏数据时顶部出现「筛选」按钮
3. **点击筛选打开 Bottom Sheet** — 显示排序下拉 + 筛选区域
4. **内容类型筛选** — 选择「有碼」或「無碼」后列表立即更新
5. **发布日期筛选** — 选择年份后列表更新，月份行出现
6. **收藏时间筛选** — 选择年份后列表更新
7. **排序下拉** — 切换排序选项后列表立即更新
8. **重置按钮** — 有筛选条件时出现，点击清除所有筛选
9. **筛选 badge** — 激活筛选后按钮显示数量
10. **空筛选结果** — 筛选无匹配时显示「没有匹配的筛选结果」
11. **演员 tab 筛选** — 仅显示收藏时间筛选和收藏时间排序

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat: complete collection filter and sort feature"
```
