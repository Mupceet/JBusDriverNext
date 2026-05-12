# UI Navigation Restructure Design

Date: 2026-05-12

## Summary

Restructure the main screen from a flat 6-tab HorizontalPager into a bottom navigation bar with 4 top-level categories (Movie, Actress, Genre, Collect), each with its own top tabs and search bar. Preserve existing screens as reusable content within the new category containers.

## Current State

MainScreen uses `HorizontalPager` with 6 pages:
`有碼影片 | 有碼演員 | 無碼影片 | 無碼演員 | 收藏影片 | 收藏演員`

A single `SecondaryScrollableTabRow` at the top, search icon button on the right.

## Target State

### Bottom Navigation

Standard Material3 `NavigationBar` with 4 items:
- 电影 (Movie icon)
- 演员 (Person icon)
- 类别 (Category/Label icon)
- 收藏 (Favorite icon)

Switching between categories is internal state change within MainScreen, not a Navigation Graph route change. State is preserved via `SaveableStateHolder`.

### Each Category's Top Area

Every category has the same top area structure:
1. **Search bar** — full-width clickable bar with placeholder text "搜索影片、演员...", navigates to search route
2. **ScrollableTabRow** — horizontal tabs specific to the category

### Category Details

#### Movie (电影)
- Tabs: 有码 | 无码 | 高清 | 字幕
- Content: reuses existing `MovieListScreen` per tab
- DataSourceType logic unchanged from current code
- Search default: 有码影片 (SearchType.CENSORED)

#### Actress (演员)
- Tabs: 有码 | 无码
- Content: reuses existing `ActressListScreen` per tab
- Search default: 女优 (SearchType.ACTRESS)

#### Genre (类别)
- Outer tabs: 有码类别 | 无码类别
- Inner tabs: dynamic, from `parseGenreCategories()` result (e.g., 题材, 系列, 制作商, 发行商, 导演)
- Both tab rows are `ScrollableTabRow`
- Content: genre Chip list for the selected theme group; clicking a chip navigates to `LinkMovieListScreen`
- Data loading: prioritize local cache (`CacheLoader.persistentCached()`), then network refresh
- Search default: 有码影片 (SearchType.CENSORED)

#### Collect (收藏)
- Tabs: 电影 | 女优
- Content: reuses existing `CollectionListScreen` with `MovieDBType` / `ActressDBType`
- No censored/uncensored split — database `LinkItem` only has `dbType` (movie/actress), no source tracking
- Search default: 有码影片 (SearchType.CENSORED)

### Search Page Adaptation

`SearchScreen` receives new optional parameter `defaultSearchType: SearchType?`. When provided, the corresponding `FilterChip` is auto-selected on entry. Pre-selection mapping:

| Source Category | Default SearchType |
|----------------|-------------------|
| Movie | CENSORED |
| Actress | ACTRESS |
| Genre | CENSORED |
| Collect | CENSORED |

### State Preservation

- **Bottom nav switching**: `SaveableStateHolder` wraps each category Screen, preserving tab index and scroll position
- **Tab switching within category**: `HorizontalPager` with keyed ViewModels, consistent with existing pattern
- **Genre cache**: `CacheLoader.persistentCached()` for genre group data — show cache first, refresh from network

## Navigation Changes

Only the `search` route changes — adds optional `defaultSearchType` parameter. All other routes remain unchanged.

| Route | Change |
|-------|--------|
| `main` | No route change; MainScreen internal structure reworked |
| `search` | Add optional `defaultSearchType` parameter |
| `movie_detail`, `image_viewer`, `link_movies` | No change |

## File Changes

### New Files

| File | Purpose |
|------|---------|
| `ui/components/CategorySearchBar.kt` | Clickable search bar component, shared across all categories |
| `ui/movielist/MovieCategoryScreen.kt` | Movie category: search bar + 4 tabs + MovieListScreen |
| `ui/movielist/ActressCategoryScreen.kt` | Actress category: search bar + 2 tabs + ActressListScreen |
| `ui/movielist/GenreCategoryScreen.kt` | Genre category: search bar + dual-layer tabs + Chip list |
| `ui/movielist/CollectCategoryScreen.kt` | Collect category: search bar + 2 tabs + CollectionListScreen |

### Modified Files

| File | Change |
|------|--------|
| `MainScreen.kt` | Remove HorizontalPager + CategoryOption; add Scaffold + NavigationBar + when dispatch to 4 category screens |
| `Navigation.kt` | Add `defaultSearchType` optional parameter to search route |
| `NavigationKeys.kt` | Update search route definition for new parameter |
| `search/SearchScreen.kt` | Accept and apply `defaultSearchType` parameter |
| `movielist/GenreListScreen.kt` | Enable and adapt for dual-tab Chip display |
| `data/parser/HtmlParser.kt` | Possibly add uncensored genre URL path |

### Unchanged Files

All existing content screens (MovieListScreen, ActressListScreen, CollectionListScreen, LinkMovieListScreen, MovieDetailScreen, ImageViewScreen), all ViewModels, all Repositories, and all database layer code remain unchanged.

## Architecture

```
MainScreen (Scaffold)
├── bottomBar: NavigationBar (电影/演员/类别/收藏)
└── content: SaveableStateHolder
    ├── MovieCategoryScreen
    │   ├── CategorySearchBar
    │   ├── ScrollableTabRow: 有码|无码|高清|字幕
    │   └── HorizontalPager → MovieListScreen × 4
    ├── ActressCategoryScreen
    │   ├── CategorySearchBar
    │   ├── ScrollableTabRow: 有码|无码
    │   └── HorizontalPager → ActressListScreen × 2
    ├── GenreCategoryScreen
    │   ├── CategorySearchBar
    │   ├── ScrollableTabRow (outer): 有码类别|无码类别
    │   ├── ScrollableTabRow (inner): dynamic theme groups
    │   └── Content: Genre Chip list
    └── CollectCategoryScreen
        ├── CategorySearchBar
        ├── ScrollableTabRow: 电影|女优
        └── HorizontalPager → CollectionListScreen × 2
```

## Approach

Single MainScreen with internal state switching (no Navigation Graph changes for bottom nav). This minimizes changes to the existing navigation graph and keeps state preservation straightforward.
