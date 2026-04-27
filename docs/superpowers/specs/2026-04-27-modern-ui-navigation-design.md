# Modern UI Navigation & Category Switching Design

## Problem

The modern Compose UI has three gaps compared to the MVP interface:

1. **No navigation entry points** — SearchScreen and SettingsScreen have routes defined but no UI elements to navigate to them.
2. **No category switching** — MovieListScreen is hardcoded to `DataSourceType.CENSORED`. The MVP version provides 5 category groups (有碼/無碼/欧美/高清/字幕) with sub-categories (电影/女优/类别浏览).
3. **$stable crash** — Already fixed by adding `@Immutable` to `MovieDetail` and related data classes. Not in scope for this spec.

## Design

### Navigation Architecture

```
ModernMainActivity
└── JBusNavigation (NavHost)
    ├── main_screen (Scaffold + NavigationBar)
    │   ├── MovieListTab
    │   │   ├── ScrollableTabRow: 有碼 | 無碼 | 欧美 | 高清 | 字幕
    │   │   ├── ChipRow (dynamic per tab): 电影 | 女优 | 类别
    │   │   └── MovieListScreen (reused for all DataSourceTypes)
    │   ├── SearchScreen
    │   └── SettingsScreen
    └── movie_detail/{movieUrl}
```

### Component Changes

#### 1. New: `MainScreen.kt`

A top-level container composable with:
- `Scaffold` with `NavigationBar` at the bottom (3 items: 电影, 搜索, 设置)
- Internal state tracking selected bottom tab
- Top bar shows current page title
- When "电影" tab is selected, the content area shows the category tabs + movie list

For the movie tab specifically:
- `ScrollableTabRow` with 5 main category tabs, each mapping to a `CategoryGroup`:
  - 有碼 → `[CENSORED, ACTRESSES, GENRE]`
  - 無碼 → `[UNCENSORED, UNCENSORED_ACTRESSES, UNCENSORED_GENRE]`
  - 欧美 → `[XYZ, XYZ_ACTRESSES, XYZ_GENRE]`
  - 高清 → `[GENRE_HD]` (no sub-chips)
  - 字幕 → `[Sub]` (no sub-chips)
- Below the tabs: a row of `FilterChip` showing sub-categories for the selected tab (only for 有碼/無碼/欧美, hidden for 高清/字幕)
- Below the chips: `MovieListScreen` content area

When a sub-category chip is selected, the corresponding `DataSourceType` is passed to `MovieListViewModel.setDataSourceType()`.

#### 2. Modify: `Navigation.kt`

- Replace the current flat route structure with `main_screen` as the start destination
- `main_screen` renders `MainScreen` composable
- `movie_detail` route remains unchanged for full-screen detail view
- SearchScreen and SettingsScreen are no longer separate routes — they are tabs within MainScreen

#### 3. Modify: `MovieListScreen.kt`

- Remove its own `Scaffold` and `TopAppBar` (MainScreen provides the scaffold)
- Accept `DataSourceType` as a parameter from parent
- When `DataSourceType` changes, call `viewModel.setDataSourceType(type)`
- Keep existing pull-to-refresh, pagination, and movie item rendering

#### 4. Modify: `MovieListViewModel.kt`

- No structural changes needed — `setDataSourceType()` already exists and works
- Ensure initial load uses the passed-in `DataSourceType` rather than defaulting to CENSORED

### Data Flow

```
User taps "無碼" tab
  → MainScreen updates selectedCategoryGroup state
  → Sub-chips update to [电影, 女优, 类别]
  → Default sub-chip "电影" is auto-selected
  → DataSourceType.UNCENSORED is passed to MovieListScreen
  → MovieListScreen calls viewModel.setDataSourceType(UNCENSORED)
  → ViewModel resets state and loads page 1 with UNCENSORED data
```

### Category Mapping

```kotlin
data class CategoryGroup(
    val name: String,          // Display name for tab
    val subCategories: List<SubCategory>
)

data class SubCategory(
    val name: String,          // Display name for chip
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
```

### Files to Create

| File | Purpose |
|------|---------|
| `modern/ui/MainScreen.kt` | Bottom navigation container + category tabs + sub-chips |

### Files to Modify

| File | Change |
|------|--------|
| `modern/ui/Navigation.kt` | Replace flat routes with `main_screen` container |
| `modern/ui/movielist/MovieListScreen.kt` | Remove Scaffold, accept DataSourceType parameter |
| `modern/ui/NavigationKeys.kt` | Add `ROUTE_MAIN` constant, remove unused routes |

### Out of Scope

- Actress/Genre dedicated list screens (Phase 2)
- Favorites/bookmarks integration
- Search history
- Advanced filtering/sorting
- Theme customization
