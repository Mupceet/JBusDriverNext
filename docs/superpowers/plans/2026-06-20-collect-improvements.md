# 收藏界面改进 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让演员收藏支持区分有码/无码（复用 categoryId 分类，与影片对称），并让收藏时间筛选支持「年份→月份展开+置灰」（复用发布日期的 MonthChipRow）。

**Architecture:**
- 功能 1：新增 `UncensoredActressCategory(id=4)`；`toggleActressCollect` 增加 `categoryId` 参数（对齐 `toggleMovieCollect`）；演员收藏入口按 link URL（含 `/uncensored/`）选 categoryId；`ActressUiModel` 加 `categoryId` 字段以支持 `CensorFilter` 筛选。
- 功能 2：`CollectionFilterState` 加 `collectMonth`；ViewModel 计算可用月份并按月过滤；FilterSheet 收藏时间区域复用 `MonthChipRow`。
- 全程复用现有分类/筛选机制，旧数据不迁移（默认归有码 categoryId=2）。

**Tech Stack:** Kotlin、Jetpack Compose、Material3、Room、Hilt、JUnit4 + kotlinx-coroutines-test（手写 fake，无 mock 库）。

**Spec:** `docs/superpowers/specs/2026-06-20-collect-improvements-design.md`

---

## Phase A — 演员有码/无码分类

### Task A1: 新增 UncensoredActressCategory 定义

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/Category.kt`（在 `UncensoredMovieCategory` 后新增）

- [ ] **Step 1: 加定义**

在 `Category.kt` 第 44 行（`UncensoredMovieCategory` 之后、`ActressCategory` 之前或之后）插入：

```kotlin
/** 無碼演員收藏分类，ID = 4 */
val UncensoredActressCategory = Category("無碼演員分類", -1, "4/", Int.MAX_VALUE, id = 4)
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/domain/model/Category.kt
git commit -m "Add UncensoredActressCategory (id=4) for actress censor typing"
```

> 说明：参考影片的 `UncensoredMovieCategory(id=3)` 同样**不**加入 `AllFirstParentDBCategoryGroup`（该 map 的 key 是 dbType=2，演员的有码/无码靠显式传 categoryId 区分，不走 fallback）。

---

### Task A2: toggleActressCollect 增加 categoryId 参数（接口+实现+同步所有 stub）

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt:51,129`
- Modify（同步 override 签名）:
  - `app/src/test/java/me/jbusdriver/modern/test/TestFakes.kt:17`
  - `app/src/test/java/me/jbusdriver/modern/data/CollectRepositoryTest.kt:259`
  - `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt:52`
  - `app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt:85,122,158`
  - `app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt:52`
- Test: `app/src/test/java/me/jbusdriver/modern/data/CollectRepositoryTest.kt`

- [ ] **Step 1: 写失败测试**

在 `CollectRepositoryTest.kt` 的 `toggleActressCollect_removesWhenCollected` 测试之后加：

```kotlin
@Test
fun toggleActressCollect_storesProvidedCategoryId() = runTest {
    val dao = SimpleLinkItemDao()
    val repository = defaultRepository(dao, PassthroughTransactionRunner())
    val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link1")

    repository.toggleActressCollect(actress, categoryId = 4)

    assertEquals(4, dao.items.single().categoryId)
}

@Test
fun toggleActressCollect_defaultsToActressCategoryWhenCategoryIdNull() = runTest {
    val dao = SimpleLinkItemDao()
    val repository = defaultRepository(dao, PassthroughTransactionRunner())
    val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link1")

    repository.toggleActressCollect(actress, categoryId = null)

    assertEquals(2, dao.items.single().categoryId)
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "*CollectRepositoryTest" --console=plain`
Expected: 编译失败 —— `toggleActressCollect` 不接受 `categoryId` 参数。

- [ ] **Step 3: 改接口**

`CollectRepository.kt:51`：

```kotlin
/**
 * 切换演员收藏状态
 *
 * @param categoryId 分类 ID；null 时使用默认演员分类（有码=2）。
 *                   无码演员传 [me.jbusdriver.modern.domain.model.UncensoredActressCategory] 的 id(4)。
 * @return 切换后的状态：true=已收藏，false=未收藏
 */
suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int? = null): Boolean
```

- [ ] **Step 4: 改 DefaultCollectRepository 实现**

`CollectRepository.kt:129`（对齐 `toggleMovieCollect` 的模式）：

```kotlin
override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?): Boolean {
    val item = if (categoryId != null) actress.convertDBItem(categoryId) else actress.convertDBItem()
    return transactionRunner.withTransaction {
        val exists = linkDao.hasByKey(item.dbType, item.key) >= 1
        if (exists) {
            linkDao.delete(item.dbType, item.key)
            false
        } else {
            linkDao.insert(item)
            true
        }
    }
}
```

- [ ] **Step 5: 同步所有测试 stub 的 override 签名**

每个 `override suspend fun toggleActressCollect(actress: ActressInfo)...` 都加 `categoryId: Int?` 参数（override 不写默认值）。各处改为：

```kotlin
override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?): Boolean = true
```

具体文件行：
- `TestFakes.kt:17`（`StubCollectRepository`）
- `CollectRepositoryTest.kt:259`（`FakeCollectRepository` 内联实现，保持原有 add/remove 逻辑，签名加 `categoryId: Int?`）
- `MovieDetailViewModelTest.kt:52`
- `CollectionListViewModelTest.kt:85,122,158`（三处）
- `LinkMovieListViewModelTest.kt:52`

`CollectRepositoryTest.kt:259` 的 `FakeCollectRepository` 改为（保留原逻辑，仅签名加参数）：

```kotlin
override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?): Boolean {
    return if (actress.link in collectedActresses) {
        collectedActresses.remove(actress.link)
        false
    } else {
        collectedActresses[actress.link] = actress
        true
    }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "*CollectRepositoryTest" --console=plain`
Expected: PASS（含新增的 2 个 categoryId 测试）

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt \
        app/src/test/java/me/jbusdriver/modern/test/TestFakes.kt \
        app/src/test/java/me/jbusdriver/modern/data/CollectRepositoryTest.kt \
        app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt \
        app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt \
        app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt
git commit -m "Add categoryId param to toggleActressCollect, align with movies"
```

---

### Task A3: LinkMovieListViewModel 按 link URL 判定并传 categoryId

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt:422-435`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

在 `LinkMovieListViewModelTest.kt` 加（参考该文件现有构造 viewModel 的 helper；下例假设现有 helper 能注入带记录能力的 repo，若现有 stub 是 `= true` 内联形式，替换为下面记录版）：

```kotlin
@Test
fun toggleActressCollect_classifiesUncensoredWhenLinkContainsUncensored() = runTest(testDispatcher) {
    val repo = RecordingCollectRepository()
    val viewModel = buildViewModel(linkUrl = "https://example.test/uncensored/star/alice", repo = repo)
    // 触发 actressHeader.detail 已加载（参考现有测试的加载方式）
    viewModel.onActressDetailLoaded(ActressDetailUiModel("Alice", "avatar", emptyList()))

    viewModel.toggleActressCollect()
    advanceUntilIdle()

    assertEquals(4, repo.lastActressCategoryId)
}

@Test
fun toggleActressCollect_classifiesCensoredWhenLinkHasNoUncensored() = runTest(testDispatcher) {
    val repo = RecordingCollectRepository()
    val viewModel = buildViewModel(linkUrl = "https://example.test/star/alice", repo = repo)
    viewModel.onActressDetailLoaded(ActressDetailUiModel("Alice", "avatar", emptyList()))

    viewModel.toggleActressCollect()
    advanceUntilIdle()

    assertEquals(2, repo.lastActressCategoryId)
}
```

并加记录用 stub（放测试类内部）：

```kotlin
private class RecordingCollectRepository : CollectRepository {
    var lastActressCategoryId: Int? = null
    override suspend fun isCollected(linkItem: LinkItem) = false
    override suspend fun addCollect(linkItem: LinkItem) = true
    override suspend fun removeCollect(linkItem: LinkItem) = true
    override suspend fun isMovieCollected(movie: Movie) = false
    override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
    override suspend fun isActressCollected(actress: ActressInfo) = false
    override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?): Boolean {
        lastActressCategoryId = categoryId
        return true
    }
    override suspend fun getCollectedMovies() = emptyList<Movie>()
    override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
    override suspend fun getCollectedLinkItems(dbType: Int) = emptyList<LinkItem>()
    override suspend fun exportCollectionsJson() = "{}"
    override suspend fun importCollectionsFromJson(json: String) = 0 to 0
}
```

> 注：`buildViewModel` / `onActressDetailLoaded` 的精确形式以现有 `LinkMovieListViewModelTest` 为准——若该测试文件已有等价的 ViewModel 构造与 detail 注入方式，直接复用；`lastActressCategoryId` 断言是本测试核心。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "*LinkMovieListViewModelTest" --console=plain`
Expected: FAIL —— `lastActressCategoryId` 为 null（当前调用未传 categoryId）。

- [ ] **Step 3: 改 LinkMovieListViewModel.toggleActressCollect**

`LinkMovieListViewModel.kt:422`：

```kotlin
fun toggleActressCollect() {
    val actressDetail = _uiState.value.actressHeader.detail ?: return
    viewModelScope.launch {
        val isUncensored = linkUrl.contains("/uncensored/")
        val categoryId = if (isUncensored) {
            me.jbusdriver.modern.domain.model.UncensoredActressCategory.id
        } else {
            me.jbusdriver.modern.domain.model.ActressCategory.id
        }
        val actress = ActressInfo(
            name = actressDetail.name,
            avatar = actressDetail.avatar,
            link = linkUrl
        )
        val newState = collectRepository.toggleActressCollect(actress, categoryId)
        _uiState.update {
            it.copy(actressHeader = it.actressHeader.withCollected(newState))
        }
    }
}
```

> **前提验证（实现时务必做）**：抓一个无码女优列表页（`/uncensored/actresses`），确认 `.avatar-box` 的 href 带 `/uncensored/`。若不带，把判定改为入口 censorType 继承（与影片同款），只改这一处。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "*LinkMovieListViewModelTest" --console=plain`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt \
        app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt
git commit -m "Classify actress collect as censored/uncensored via link URL"
```

---

### Task A4: 演员支持 CensorFilter（ActressUiModel.categoryId + 筛选逻辑）

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt:55-60`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt:113,226-231,250`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

在 `CollectionListViewModelTest.kt` 加（参考现有 `loadCollection_actressType_loadsActresses` 的 ViewModel 构造与 repo stub 模式）：

```kotlin
@Test
fun loadCollection_actressType_filtersByCensor() = runTest(testDispatcher) {
    val collectRepo = object : CollectRepository {
        override suspend fun isCollected(linkItem: LinkItem) = false
        override suspend fun addCollect(linkItem: LinkItem) = true
        override suspend fun removeCollect(linkItem: LinkItem) = true
        override suspend fun isMovieCollected(movie: Movie) = false
        override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
        override suspend fun isActressCollected(actress: ActressInfo) = false
        override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
        override suspend fun getCollectedMovies() = emptyList<Movie>()
        override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
        override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
            if (dbType == ActressDBType) listOf(
                LinkItem(dbType = ActressDBType, key = "censored", jsonStr = CENSORED_JSON, categoryId = 2, createTime = 1L),
                LinkItem(dbType = ActressDBType, key = "uncensored", jsonStr = UNCENSORED_JSON, categoryId = 4, createTime = 2L)
            ) else emptyList()
        override suspend fun exportCollectionsJson() = "{}"
        override suspend fun importCollectionsFromJson(json: String) = 0 to 0
    }
    val viewModel = buildViewModel(collectRepo)  // 现有 helper
    viewModel.loadCollection(ActressDBType)
    viewModel.updateFilter(CollectionFilterState(censorFilter = CensorFilter.UNCENSORED))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(1, state.actresses.size)
}

companion object {
    private const val CENSORED_JSON = """{"name":"A","avatar":"/a.jpg","link":"/star/a"}"""
    private const val UNCENSORED_JSON = """{"name":"B","avatar":"/b.jpg","link":"/uncensored/star/b"}"""
}
```

> 注：`buildViewModel` 以现有 helper 为准；JSON 须与 `ActressInfo` 字段（name/avatar/link）匹配，路径用相对形式（与 `stripUrlFields`/`restoreUrlFields` 一致）。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "*CollectionListViewModelTest" --console=plain`
Expected: FAIL —— 演员未按 censorFilter 过滤（`ActressUiModel` 无 categoryId，filterByCensor 未作用于演员）。

- [ ] **Step 3: ActressUiModel 加 categoryId 字段**

`UiModels.kt:55`：

```kotlin
/** 演员的 UI 模型 */
@Immutable
data class ActressUiModel(
    val name: String,
    val avatar: String,
    val link: String,
    val createTime: Long = 0L,
    val categoryId: Int = 2
)
```

- [ ] **Step 4: loadCollection 复制 categoryId**

`CollectionListViewModel.kt:111-114`：

```kotlin
allActresses = actressItems.mapNotNull { item ->
    ((item.toILink(baseUrl) as? ActressInfo)?.toActressUiModel())
        ?.copy(createTime = item.createTime, categoryId = item.categoryId)
}
```

- [ ] **Step 5: 加演员 filterByCensor 并在 applyFilterAndSort 应用**

`CollectionListViewModel.kt`，在现有 `filterByCensor`（MovieUiModel 版，第 250 行附近）之后加：

```kotlin
private fun List<ActressUiModel>.filterByCensor(censor: CensorFilter): List<ActressUiModel> =
    when (censor) {
        CensorFilter.ALL -> this
        CensorFilter.CENSORED -> filter { it.categoryId != 4 }
        CensorFilter.UNCENSORED -> filter { it.categoryId == 4 }
    }
```

`applyFilterAndSort` 的 `filteredActresses`（第 226 行）改为：

```kotlin
val filteredActresses = allActresses
    .filterByCensor(filter.censorFilter)
    .filterByCollectYear(filter.collectYear, years.collectYears) { it.createTime }
    .sortedWith(
        if (filter.sortOption in SortOption.actressOptions) filter.sortOption.toActressComparator()
        else SortOption.COLLECT_DESC.toActressComparator()
    )
```

- [ ] **Step 6: 跑测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "*CollectionListViewModelTest" --console=plain`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt \
        app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt \
        app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt
git commit -m "Support CensorFilter for actresses via categoryId"
```

---

### Task A5: FilterSheet 把 CensorFilter 扩展到演员（UI）

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt:103-158`

- [ ] **Step 1: 调整结构 —— CensorFilter 移出 `if (dbType==MovieDBType)`，发布日期保留仅影片**

`CollectionFilterSheet.kt`，把第 103-158 行的整块替换为（CensorFilter 区域提到 if 之前，发布日期留在 if 内）：

```kotlin
// ── Censor filter (movie + actress) ──
FilterSectionLabel(stringResource(R.string.content_type))
Spacer(Modifier.padding(top = 6.dp))
FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    CensorChip(
        label = stringResource(R.string.all),
        selected = filterState.censorFilter == CensorFilter.ALL,
        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.ALL)) }
    )
    CensorChip(
        label = stringResource(R.string.censored),
        selected = filterState.censorFilter == CensorFilter.CENSORED,
        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.CENSORED)) }
    )
    CensorChip(
        label = stringResource(R.string.uncensored),
        selected = filterState.censorFilter == CensorFilter.UNCENSORED,
        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.UNCENSORED)) }
    )
}

if (dbType == MovieDBType) {
    Spacer(Modifier.padding(top = 16.dp))

    // ── Publish date (movie only) ──
    FilterSectionLabel(stringResource(R.string.release_date))
    Spacer(Modifier.padding(top = 6.dp))
    YearChipRow(
        selectedYear = filterState.publishYear,
        years = availableYears.publishYears,
        onSelect = { year ->
            onFilterChange(
                filterState.copy(
                    publishYear = year,
                    publishMonth = null
                )
            )
        }
    )

    if (filterState.publishYear != null && filterState.publishYear > 0) {
        Spacer(Modifier.padding(top = 8.dp))
        MonthChipRow(
            selectedMonth = filterState.publishMonth,
            availableMonths = availablePublishMonths,
            onSelect = { month ->
                if (month == null || month in availablePublishMonths) {
                    onFilterChange(filterState.copy(publishMonth = month))
                }
            }
        )
    }
}

Spacer(Modifier.padding(top = 16.dp))
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 手动验证**

Run app → 收藏页 → 演员 Tab → 筛选 → 确认「内容类型」出现全部/有码/无码；选「无码」后列表只剩无码演员。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt
git commit -m "Show CensorFilter for actresses in collection filter sheet"
```

---

## Phase B — 收藏时间月份展开

### Task B1: CollectionFilterState 加 collectMonth

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterState.kt:36-58`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionFilterStateTest.kt`（若无，新建于同目录）

- [ ] **Step 1: 写失败测试**

新建或追加到 `CollectionFilterStateTest.kt`：

```kotlin
package me.jbusdriver.modern.ui.movielist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFilterStateTest {
    @Test
    fun collectMonth_countsAsActiveFilter() {
        val state = CollectionFilterState(collectYear = 2026, collectMonth = 6)
        assertTrue(state.hasActiveFilters)
        assertEquals(2, state.activeFilterCount)
    }

    @Test
    fun defaultState_hasNoCollectMonth() {
        val state = CollectionFilterState()
        assertFalse(state.hasActiveFilters)
        assertEquals(0, state.activeFilterCount)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "*CollectionFilterStateTest" --console=plain`
Expected: 编译失败 —— `CollectionFilterState` 构造不接受 `collectMonth`。

- [ ] **Step 3: 加字段**

`CollectionFilterState.kt:36`：

```kotlin
data class CollectionFilterState(
    val censorFilter: CensorFilter = CensorFilter.ALL,
    val publishYear: Int? = null,
    val publishMonth: Int? = null,
    val collectYear: Int? = null,
    val collectMonth: Int? = null,
    val sortOption: SortOption = SortOption.COLLECT_DESC
) {
    val hasActiveFilters: Boolean
        get() = censorFilter != CensorFilter.ALL
                || publishYear != null
                || publishMonth != null
                || collectYear != null
                || collectMonth != null

    val activeFilterCount: Int
        get() = listOf(
            censorFilter != CensorFilter.ALL,
            publishYear != null,
            publishMonth != null,
            collectYear != null,
            collectMonth != null
        ).count { it }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "*CollectionFilterStateTest" --console=plain`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterState.kt \
        app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionFilterStateTest.kt
git commit -m "Add collectMonth to CollectionFilterState"
```

---

### Task B2: ViewModel 计算可用收藏月份并按月过滤

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`（UiState、applyFilterAndSort、扩展函数区）
- Test: `app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

在 `CollectionListViewModelTest.kt` 加（复用 A4 的 repo stub 模式，给两条不同月份的 createTime）：

```kotlin
@Test
fun loadCollection_collectTime_filtersByCollectMonth() = runTest(testDispatcher) {
    val mayMillis = mktime(2026, 5, 10)
    val juneMillis = mktime(2026, 6, 1)
    val collectRepo = object : CollectRepository {
        override suspend fun isCollected(linkItem: LinkItem) = false
        override suspend fun addCollect(linkItem: LinkItem) = true
        override suspend fun removeCollect(linkItem: LinkItem) = true
        override suspend fun isMovieCollected(movie: Movie) = false
        override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
        override suspend fun isActressCollected(actress: ActressInfo) = false
        override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
        override suspend fun getCollectedMovies(): List<Movie> = listOf(
            Movie("T", "/m.jpg", "ABC-1", "2026-05-10", "/m1").let { it }
        ).let { emptyList() }  // 影片留空，聚焦演员
        override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
        override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
            if (dbType == MovieDBType) listOf(
                LinkItem(dbType = MovieDBType, key = "a", jsonStr = MOVIE_JSON, categoryId = 1, createTime = mayMillis),
                LinkItem(dbType = MovieDBType, key = "b", jsonStr = MOVIE_JSON, categoryId = 1, createTime = juneMillis)
            ) else emptyList()
        override suspend fun exportCollectionsJson() = "{}"
        override suspend fun importCollectionsFromJson(json: String) = 0 to 0
    }
    val viewModel = buildViewModel(collectRepo)
    viewModel.loadCollection(MovieDBType)
    viewModel.updateFilter(CollectionFilterState(collectYear = 2026, collectMonth = 6))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(1, state.movies.size)  // 只剩 6 月那条
    assertTrue(state.availableCollectMonths.contains(6))
}

private fun mktime(year: Int, month: Int, day: Int): Long =
    java.util.Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, 12, 0, 0)
    }.timeInMillis

companion object {
    private const val MOVIE_JSON = """{"title":"T","imageUrl":"/m.jpg","code":"ABC-1","date":"2026-05-10","link":"/m1"}"""
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "*CollectionListViewModelTest" --console=plain`
Expected: FAIL —— `availableCollectMonths` / `filterByCollectMonth` 不存在。

- [ ] **Step 3: 加 toMonth 扩展**

`CollectionListViewModel.kt` 扩展函数区，在 `Long.toYear()`（第 300 行）后加：

```kotlin
/** 将毫秒时间戳转换为月份（1-12） */
private fun Long.toMonth(): Int =
    Calendar.getInstance().apply { timeInMillis = this@toMonth }.get(Calendar.MONTH) + 1
```

- [ ] **Step 4: 加 filterByCollectMonth 扩展**

扩展函数区加：

```kotlin
private fun <T> List<T>.filterByCollectMonth(month: Int?, getTime: (T) -> Long): List<T> =
    if (month == null) this
    else filter { getTime(it).toMonth() == month }
```

- [ ] **Step 5: UiState 加 availableCollectMonths + applyFilterAndSort 计算并过滤**

`CollectionListUiState`（第 37 行）加字段：

```kotlin
data class CollectionListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val actresses: List<ActressUiModel> = emptyList(),
    val movieCount: Int = 0,
    val actressCount: Int = 0,
    val isLoading: Boolean = false,
    val error: Int? = null,
    val filterState: CollectionFilterState = CollectionFilterState(),
    val availableYears: AvailableYears = AvailableYears(),
    val availablePublishMonths: Set<Int> = emptySet(),
    val availableCollectMonths: Set<Int> = emptySet()
)
```

`applyFilterAndSort`（第 205 行）在算完 `availableMonths` 后加 `availableCollectMonths`，并在 filteredMovies/filteredActresses 链中加 `.filterByCollectMonth`：

```kotlin
val availableCollectMonths = if (filter.collectYear != null && filter.collectYear > 0) {
    val times = if (currentDbType == MovieDBType) {
        allMovies.map { it.createTime }
    } else {
        allActresses.map { it.createTime }
    }
    times.filter { it.toYear() == filter.collectYear }.map { it.toMonth() }.toSet()
} else emptySet()

val filteredMovies = allMovies
    .filterByCensor(filter.censorFilter)
    .filterByPublishYear(filter.publishYear, years.publishYears)
    .filterByPublishMonth(filter.publishMonth)
    .filterByCollectYear(filter.collectYear, years.collectYears) { it.createTime }
    .filterByCollectMonth(filter.collectMonth) { it.createTime }
    .sortedWith(filter.sortOption.toMovieComparator())

val filteredActresses = allActresses
    .filterByCensor(filter.censorFilter)
    .filterByCollectYear(filter.collectYear, years.collectYears) { it.createTime }
    .filterByCollectMonth(filter.collectMonth) { it.createTime }
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
        availablePublishMonths = availableMonths,
        availableCollectMonths = availableCollectMonths,
        isLoading = false
    )
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "*CollectionListViewModelTest" --console=plain`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt \
        app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt
git commit -m "Compute available collect months and filter by collect month"
```

---

### Task B3: FilterSheet 收藏时间区域展开月份 + 调用方传参

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt:49-56,160-169`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt:253`

- [ ] **Step 1: FilterSheet 签名加 availableCollectMonths 参数**

`CollectionFilterSheet.kt:49`：

```kotlin
fun CollectionFilterSheet(
    dbType: Int,
    filterState: CollectionFilterState,
    availableYears: AvailableYears,
    availablePublishMonths: Set<Int>,
    availableCollectMonths: Set<Int>,
    onFilterChange: (CollectionFilterState) -> Unit,
    onDismiss: () -> Unit
) {
```

- [ ] **Step 2: 收藏时间区域 YearChipRow 重置 collectMonth + 展开MonthChipRow**

`CollectionFilterSheet.kt:160-169`（收藏时间区域）替换为：

```kotlin
// ── Collect time (both) ──
FilterSectionLabel(stringResource(R.string.collect_time))
Spacer(Modifier.padding(top = 6.dp))
YearChipRow(
    selectedYear = filterState.collectYear,
    years = availableYears.collectYears,
    onSelect = { year ->
        onFilterChange(filterState.copy(collectYear = year, collectMonth = null))
    }
)

if (filterState.collectYear != null && filterState.collectYear > 0) {
    Spacer(Modifier.padding(top = 8.dp))
    MonthChipRow(
        selectedMonth = filterState.collectMonth,
        availableMonths = availableCollectMonths,
        onSelect = { month ->
            if (month == null || month in availableCollectMonths) {
                onFilterChange(filterState.copy(collectMonth = month))
            }
        }
    )
}
```

- [ ] **Step 3: 调用方传新参数**

`CollectCategoryScreen.kt:253` 的 `CollectionFilterSheet(...)` 调用，加 `availableCollectMonths = uiState.availableCollectMonths`（变量名以该文件实际 uiState 取值为准）。

- [ ] **Step 4: 验证编译**

Run: `./gradlew compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 手动验证**

Run app → 收藏页 → 任一 Tab → 筛选 → 收藏时间选某年 → 确认展开 12 个月、无数据月份置灰不可点；切年份后月份重置。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt \
        app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt
git commit -m "Expand collect-time filter with month drill-down (grey out empty)"
```

---

## 完成验证

- [ ] **全量测试**：`./gradlew testDebugUnitTest --console=plain` → BUILD SUCCESSFUL
- [ ] **手动端到端**：
  - 演员收藏：从无码女优入口收藏演员 → 收藏列表无码筛选能看到；有码入口收藏 → 归有码。
  - 收藏时间筛选：选年份后月份展开、置灰正确；影片与演员 Tab 都生效。
  - 旧数据：升级后旧演员收藏仍显示（归有码 categoryId=2），不崩溃。

---

## Self-Review（plan 作者自查记录）

- **Spec 覆盖**：功能1（分类定义 A1、收集流程 A2/A3、筛选 A4/A5、导入导出自动跟随无需单独 task、前提验证写在 A3）✓；功能2（State B1、ViewModel B2、UI B3）✓。
- **类型一致性**：`toggleActressCollect(actress, categoryId: Int?)` 全 plan 一致；`UncensoredActressCategory.id` 即 4；`ActressUiModel.categoryId` 默认 2；`availableCollectMonths: Set<Int>` 在 UiState / FilterSheet / 测试一致。
- **占位符**：无 TBD/TODO；UI 手动验证步骤为明确操作指引（非占位）。
- **连锁覆盖**：`toggleActressCollect` 签名变更的 6 处 override 在 A2 Step 5 全部列出。
