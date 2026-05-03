# MovieList Grid Mode Design

## Overview

为 MovieList 组件新增网格列表模式，以竖向卡片（上图下文）展示影片，使用 `LazyVerticalGrid` + `Adaptive` 实现横向自适应列数。同时合并 MovieItem.kt 和 MovieList.kt 为单文件。

## File Changes

### Merge: MovieItem.kt + MovieList.kt → MovieList.kt

将 `ui/components/MovieItem.kt` 和 `ui/components/MovieList.kt` 合并为单个 `ui/components/MovieList.kt`。

文件内容：
- `MovieList` composable — 列表容器，新增 `isGrid` 参数
- `MovieItem` composable — 现有列表卡片（横排，左图右文）
- `MovieGridItem` composable — 新增网格卡片（竖排，上图下文）

删除旧 `MovieItem.kt` 文件。

### MovieList Signature

```kotlin
@Composable
fun MovieList(
    movies: List<MovieUiModel>,
    hasMore: Boolean = true,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onMovieClick: (MovieUiModel) -> Unit = {},
    isGrid: Boolean = false,
    modifier: Modifier = Modifier
)
```

**Internal logic:**
- `isGrid = false` → `LazyColumn` + `MovieItem`（现有行为不变）
- `isGrid = true` → `LazyVerticalGrid(GridCells.Adaptive(minSize = 150.dp))` + `MovieGridItem`

加载更多、空状态、"没有更多了"在两种模式下行为一致。

### MovieGridItem Layout

```
┌─────────────────┐
│   Cover (2:3)    │
├─────────────────┤
│ Title (3 lines)  │
│      tag tag tag │  FlowRow, Alignment.End
│ code     date    │  Row, SpaceBetween
└─────────────────┘
```

**Details:**
- Cover: `aspectRatio(2f / 3f)`, `ContentScale.Crop`, `fillMaxWidth()`
- Title: `bodyMedium`, `maxLines = 3`, `TextOverflow.Ellipsis`
- Tags: `FlowRow`, `horizontalArrangement = spacedBy(4.dp, Alignment.End)`, same chip style as MovieItem
- Code + Date: `Row(Arrangement.SpaceBetween)`, code in primary color, date in onSurface 60% alpha
- Card padding: `horizontal = 4.dp, vertical = 4.dp` (tighter than list mode for grid density)

### Consumer Updates

四个消费页面传入 `isGrid` 参数，当前全部传 `false`：

1. **MovieListScreen** (`ui/movielist/MovieListScreen.kt`) — 直接传参
2. **LinkMovieListScreen** (`ui/movielist/LinkMovieListScreen.kt`) — 改写为使用 `MovieList` 组件替代当前手写的 `LazyColumn` + `MovieItem`
3. **SearchScreen** (`ui/search/SearchScreen.kt`) — 直接传参
4. **CollectionListScreen** (`ui/movielist/CollectionListScreen.kt`) — 直接传参

### Import Updates

所有引用 `me.jbusdriver.modern.ui.components.MovieItem` 的文件更新为从 `MovieList.kt` 导入（同一包名，无需改 import path，但需确认无遗漏）。

## Out of Scope

- 不添加 UI 切换入口（列表/网格 toggle）
- 不持久化布局偏好
- LinkMovieListScreen 顶部的演员信息卡不在本次范围内
- 不修改 MovieUiModel 数据结构
