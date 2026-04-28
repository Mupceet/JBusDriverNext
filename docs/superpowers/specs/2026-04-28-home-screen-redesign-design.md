# Home Screen Category Redesign

## Context
Current home screen wastes vertical space with a two-level category system (ScrollableTabRow + FilterChip) that shows 4 main categories (有码/无码/高清/字幕) each with up to 3 sub-categories. In practice, 高清 and 字幕 categories have little value. The layout leaves insufficient room for the actual content list.

## Design

### New Category Structure
Replace 4 main categories with 3:
- **有码** → 电影 / 演员 / 类别
- **无码** → 电影 / 演员 / 类别
- **收藏** → 电影 / 演员

Remove 高清 and 字幕 categories entirely.

### TopAppBar Title Dropdown
- TopAppBar title shows current selection as "主分类 · 子分类" (e.g. "有码 · 电影") with a small dropdown arrow
- Clicking the title opens a `DropdownMenu` with all 8 options, grouped by main category with dividers
- Selecting an option switches the content immediately
- Dropdown items layout:

```
有码 · 电影          ← highlighted
有码 · 演员
有码 · 类别
─────────────
无码 · 电影
无码 · 演员
无码 · 类别
─────────────
收藏 · 电影
收藏 · 演员
```

### Remove Old Category UI
- Remove `ScrollableTabRow` (main category tabs)
- Remove `FilterChip` row (sub-category chips)
- Remove `CategoryGroups` data structure
- Replace with a flat list of `CategoryOption` items

### Collection Lists
- **收藏 · 电影**: Load from `DB.linkDao.listByType(MovieDBType)`, deserialize JSON to `Movie` objects, display as `MovieListScreen`
- **收藏 · 演员**: Load from `DB.linkDao.listByType(ActressDBType)`, deserialize JSON to `ActressInfo` objects, display as `ActressListScreen`
- Collection lists are local-only (no network pagination), load all items then paginate locally
- Empty state: show "还没有收藏" message

### Data Model

```kotlin
data class CategoryOption(
    val group: String,      // "有码", "无码", "收藏"
    val name: String,       // "电影", "演员", "类别"
    val dataSourceType: DataSourceType? = null,  // null for collection types
    val isCollection: Boolean = false,
    val collectionDbType: Int = 0  // MovieDBType or ActressDBType
)
```

### State Management
- Replace `selectedCategoryIndex` + `selectedSubCategoryIndex` with single `selectedOptionIndex`
- `CategoryOptions` flat list replaces nested `CategoryGroups`
- `DropdownMenu` expanded state controlled by `showCategoryMenu` boolean

## Files to modify
- `modern/ui/MainScreen.kt` — remove tabs/chips, add DropdownMenu, add collection loading
- `modern/ui/UiModels.kt` — add `CategoryOption` if needed

## Files to reference
- `modern/data/CollectRepository.kt` — for `DB.linkDao.listByType()` calls
- `db/DB.kt` — `DB.linkDao` accessor
- `mvp/bean/ILink.kt` — `MovieDBType`, `ActressDBType` constants, `convertDBItem()`
- `db/entity/LinkItem.kt` — `LinkItem` entity, `getLinkValue()` deserialization
