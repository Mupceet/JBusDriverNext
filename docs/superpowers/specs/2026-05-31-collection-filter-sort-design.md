# 收藏筛选与排序功能设计

## 背景

当前收藏功能仅按影片/演员两个 tab 分开展示，列表固定按数据库 ID 倒序（即收藏时间倒序）。用户无法按内容类型、发布日期、收藏时间进行筛选，也无法更改排序方式。

## 目标

为收藏列表添加筛选和排序能力，让用户可以快速定位已收藏的内容。

## 方案：纯内存筛选排序

从数据库加载全部收藏数据，使用 Kotlin 集合操作在内存中完成筛选和排序。

**选择理由**：典型用户的收藏量在几百到几千条之间，内存操作完全足够，无需引入 DB 迁移或复杂 SQL 查询。

## 数据来源

| 筛选/排序维度 | 数据来源 | 存储位置 |
|--------------|---------|---------|
| 有码/无码 | URL 路径前缀（`/uncensored/` = 无码，`/xyz/` = 欧美，其余 = 有码） | `LinkItem.key` |
| 发布日期 | Movie 的 `date` 字段 | `LinkItem.jsonStr` → 反序列化 |
| 收藏时间 | 收藏时间戳 | `LinkItem.createTime` |

## UI 设计

### 入口

收藏列表顶部 toolbar 区域增加一个筛选按钮，点击弹出 Bottom Sheet。

### Bottom Sheet 布局

```
┌─────────────────────────────────────┐
│  重置                 收藏时间倒序 ▾ │  ← 首行：重置(有筛选时显示) + 排序下拉
├─────────────────────────────────────┤
│  内容类型                           │
│  [全部] [有碼] [無碼]               │  ← Chip 单选，默认"全部"
│                                     │
│  发布日期                           │
│  [全部] [2026] [2025] [2024] [更早]  │  ← 年份 Chip，从数据动态生成
│  [全部] [1月] [2月] ... [12月]      │  ← 月份 Chip，选了年份后可选
│                                     │
│  收藏时间                           │
│  [全部] [2026] [2025] [2024] [更早]  │  ← 年份 Chip，从数据动态生成
└─────────────────────────────────────┘
```

### 排序下拉选项

- 收藏时间倒序（默认）
- 收藏时间正序
- 发布时间倒序
- 发布时间正序

### 交互规则

- **筛选即时生效**：点击 Chip 立即更新列表，无应用按钮
- **排序即时生效**：从下拉选择后立即更新列表
- **重置按钮**：仅在有非默认筛选条件时显示，点击将所有筛选恢复为"全部"
- **年份动态生成**：从实际收藏数据中提取年份列表，加一个"更早"兜底选项
- **月份可选**：只有选了年份后才展示月份选择行（选"全部"或"更早"时不展示月份）

### 按内容类型区分

| 功能 | 影片列表 | 演员列表 |
|------|---------|---------|
| 内容类型筛选（有碼/無碼） | ✅ | ❌ |
| 发布日期筛选 | ✅ | ❌ |
| 收藏时间筛选 | ✅ | ✅ |
| 排序：收藏时间 | ✅ | ✅ |
| 排序：发布时间 | ✅ | ❌ |

## 数据模型

### 新增状态类

```kotlin
enum class CensorFilter { ALL, CENSORED, UNCENSORED }

enum class SortOption(val label: String) {
    COLLECT_DESC("收藏时间倒序"),
    COLLECT_ASC("收藏时间正序"),
    PUBLISH_DESC("发布时间倒序"),
    PUBLISH_ASC("发布时间正序")
}

// 用 Int? 表示年份筛选：null = 全部，正整数 = 具体年份，-1 = 更早
// 例如 publishYear = 2025 表示筛选 2025 年，-1 表示 2024 年以前
val Int?.isEarlier: Boolean get() = this == -1

data class CollectionFilterState(
    val censorFilter: CensorFilter = CensorFilter.ALL,
    val publishYear: Int? = null,       // null = 全部, 正整数 = 年份, -1 = 更早
    val publishMonth: Int? = null,      // null = 全部, 1-12
    val collectYear: Int? = null,       // null = 全部, 正整数 = 年份, -1 = 更早
    val sortOption: SortOption = SortOption.COLLECT_DESC
)
```

### ViewModel 变更

在 `CollectionListViewModel` 中：

1. 新增 `filterState: StateFlow<CollectionFilterState>`
2. 新增 `availableYears: StateFlow<AvailableYears>`（从收藏数据动态提取年份）
3. 加载收藏数据后，根据 filterState 在内存中执行筛选和排序
4. 筛选/排序变更时立即重新计算结果列表

### 筛选逻辑

```kotlin
fun applyFilter(items: List<MovieUiModel>, filter: CollectionFilterState): List<MovieUiModel> {
    return items
        .filter { movie ->
            // 内容类型
            when (filter.censorFilter) {
                CensorFilter.CENSORED -> !movie.movie.link.urlPath.startsWith("/uncensored/")
                CensorFilter.UNCENSORED -> movie.movie.link.urlPath.startsWith("/uncensored/")
                CensorFilter.ALL -> true
            }
        }
        .filter { movie ->
            // 发布日期年份
            filter.publishYear?.let { year ->
                movie.movie.date.take(4).toIntOrNull()?.let { it == year } ?: false
            } ?: true
        }
        .filter { movie ->
            // 发布日期月份
            filter.publishMonth?.let { month ->
                movie.movie.date.substring(5, 7).toIntOrNull()?.let { it == month } ?: false
            } ?: true
        }
        .filter { movie ->
            // 收藏时间年份
            filter.collectYear?.let { year ->
                val cal = Calendar.getInstance().apply { timeInMillis = movie.createTime }
                cal.get(Calendar.YEAR) == year
            } ?: true
        }
        .sortedWith(
            when (filter.sortOption) {
                SortOption.COLLECT_DESC -> compareByDescending { it.createTime }
                SortOption.COLLECT_ASC -> compareBy { it.createTime }
                SortOption.PUBLISH_DESC -> compareByDescending { it.movie.date }
                SortOption.PUBLISH_ASC -> compareBy { it.movie.date }
            }
        )
}
```

## 涉及文件

| 文件 | 变更 |
|------|------|
| `ui/movielist/CollectionListScreen.kt` | 添加筛选按钮、Bottom Sheet UI |
| `ui/movielist/CollectionListViewModel.kt` | 添加筛选/排序状态和逻辑 |
| `ui/movielist/CollectCategoryScreen.kt` | 可能需要传递筛选回调 |
| `domain/model/Movie.kt` 或新文件 | 新增 `CensorFilter`、`SortOption`、`CollectionFilterState` |
| `data/CollectRepository.kt` | 新增 `getCollectedLinkItems()` 返回 `List<LinkItem>`（包含 `createTime`），而非仅返回反序列化后的 Movie 列表 |
| `ui/models/UiModels.kt` | `MovieUiModel` 新增 `createTime: Long` 字段，用于排序和收藏时间筛选 |

## 不在范围内

- 用户自定义分类/文件夹（未来独立迭代）
- 标签筛选
- 数据库迁移
- 导出/导入功能变更
