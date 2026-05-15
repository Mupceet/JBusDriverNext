# JBus Search-Collection Centric Redesign

Date: 2026-05-16

## Summary

Redesign JBus from a browsing-oriented movie resource app into a **search + collection-centric movie index tool**. The server provides only daily-refreshed movie lists without ratings or advanced metadata, so the app's value lies in helping users efficiently find, organize, and revisit content through powerful search and local favorites.

## Design Decisions

| Area | Decision |
|------|----------|
| App positioning | Search + collection-centric movie index tool |
| Tab structure | 2 tabs: Home + Collection |
| Search entry | Prominent theme-colored search bar at top of home page, tapping opens full-screen search |
| Censored/Uncensored | Merged into filter chips (All / Censored / Uncensored) |
| Genre/Category | Integrated into movie segment as "Category▾" chip, opens bottom sheet; default single-select, optional multi-select (AND logic, IDs joined with "-" in URL) |
| List style | Compact list (default) with grid toggle, quick-favorite star on each row |
| Collection org | Segmented switch: Movie favorites / Actress favorites |
| Search UX | Full-screen search page with 6 search types + persistent history |

## Navigation Structure

### Bottom Navigation Bar
2 tabs, clean flat design:
- **首页** (Home) — Browse and discover
- **收藏** (Collection) — Local favorites

No FAB. Search is accessed via the prominent theme-colored search bar at the top of the home screen.

## Screens

### 1. Home Tab (首页)

**Layout (top to bottom):**

1. **Search bar** — Theme-colored rounded pill, text "搜索影片、演员、类别...", tapping navigates to full-screen search page
2. **Segmented control** — 影片 | 演员 (mutually exclusive, persists across switches)
3. **Filter chips row** — Horizontal scrolling: 全部 | 有码 | 无码 | 类别▾
4. **Content area** — Depends on selected segment:

**影片 segment:**
- Section label: "今日新片" (or filtered result label)
- View toggle (top right): 列表 / 网格
- **Compact list (default):** Thumbnail (52×70dp) + title (1 line) + date + tags + star button
- **Grid mode:** 2-column card grid with poster thumbnails
- Pull-to-refresh, infinite scroll

**演员 segment:**
- Section label: "热门演员"
- 4-column circular avatar grid (64dp avatars)
- Name label below each avatar
- Pull-to-refresh, infinite scroll

**Filter chips behavior:**
- 全部 / 有码 / 无码: mutually exclusive, only shown in 影片 segment
- 类别▾: only shown in 影片 segment, opens category bottom sheet
- 演员 segment shows only: 全部 / 有码 / 无码

**Category Bottom Sheet:**
- Slides up from bottom with drag handle
- Title: "选择类别" + selected count badge + "重置" button
- Categories grouped by server-returned groups (题材, 場景, 角色, etc.)
- **Multi-select:** Default is single-select. A toggle (e.g. "多选" switch) enables multi-select mode. In multi-select mode, tapping a chip toggles selection, selected chips use theme color + ✓ mark. Multiple selections use AND logic — genre IDs are joined with "-" in the URL path (e.g. `/genre/1n-10`)
- "套用筛选" confirm button at bottom
- When categories selected, filter chip changes from "类别▾" to "类别(n)" showing count
- Pull-down to dismiss (cancel)

### 2. Full-Screen Search Page

**Entry:** Tap search bar on home → slides up as full-screen overlay

**Layout:**
1. **Active search bar** — Back arrow (←) + text input + clear button (✕)
2. **Search type chips** — Horizontal scroll: 影片 | 女優 | 導演 | 製作商 | 發行商 | 系列 (default: 影片)
3. **Before input:** Search history (tag-style chips, local persistence) + "清除" button
4. **After submit:** Search results displayed (triggered by keyboard submit/enter, not as-you-type)
   - 影片/導演/製作商/發行商/系列 types → movie list (same compact style as home)
   - 女優 type → actress grid
5. **Back navigation:** ← button or system back returns to home

**Search history:**
- Persisted locally via Room or SharedPreferences
- Displayed as rounded tag chips
- Tap a history tag to re-search immediately
- "清除" clears all history

### 3. Collection Tab (收藏)

**Layout:**
1. **Title:** "我的收藏" (large, 20sp, bold)
2. **Segmented switch** — Pill-style toggle: 影片 (n) | 演員 (n), shows count in each segment
3. **Sort label:** "按收藏時間排序" + view toggle (列表/网格)
4. **Content:**
   - 影片 segment: compact movie list with star indicator and "收藏於 X天前" timestamp
   - 演員 segment: circular avatar grid with name labels

**Interactions:**
- **Long press** on item → confirmation dialog → remove from collection
- **Empty state:** illustration + "去发现" button navigating to home
- View preference shared with home tab

### 4. Movie Detail Page (unchanged core)

Keep existing functionality:
- Cover image, title, metadata headers
- Actress list (tappable → actress page)
- Genre chips (tappable → genre movie list)
- Image samples (tappable → full-screen viewer)
- Magnet links section with filter toggle
- Collect toggle in top bar

### 5. Actress Page (LinkMovieListScreen for actresses)

Keep existing functionality:
- Actress detail card (avatar + info)
- Collect toggle for actress
- Movie list below

### 6. Genre Movie List (LinkMovieListScreen for genres)

Keep existing functionality:
- Title from genre name
- Movie list with filter bar

### 7. Image Viewer (unchanged)

Keep existing full-screen gallery with zoom.

## Interaction Patterns

### Quick Favorite (Star Button)
- Available on every movie row in compact list mode
- Single tap toggles favorite state
- Brief scale animation feedback
- Filled star (⭐) = collected, outline star (☆) = not collected
- Toast confirmation on state change

### View Toggle (List / Grid)
- Available on home (影片 segment) and collection (影片 segment)
- State persisted across app sessions
- Shared preference between home and collection

### Pull-to-Refresh
- Available on all list screens
- Refreshes data from server (home) or database (collection)

### Infinite Scroll
- Auto-loads next page when 3 items from bottom
- Loading indicator at bottom during fetch

## Data Flow

```
Home Tab:
  Search bar → Navigation → Full-screen search page
  Filter chips → ViewModel filter state → Repository query
  Category chip → Bottom sheet → Multi-select → ViewModel filter
  List/Grid toggle → SharedPreferences → UI recomposition

Search Page:
  Input + type → SearchRepository.search(query, type) → Results
  History → SharedPreferences read/write

Collection Tab:
  Room DB query → CollectRepository → ViewModel → UI
  Long press → Delete from Room → UI update
```

## Components to Modify/Create

### Modified
- `MainScreen.kt` — 2-tab structure instead of 4
- `Navigation.kt` — Update routes and bottom nav
- `MovieCategoryScreen.kt` — Merge into home tab with segment + filters
- `ActressCategoryScreen.kt` — Merge into home tab as actress segment
- `GenreCategoryScreen.kt` — Remove as standalone tab, become bottom sheet
- `CollectCategoryScreen.kt` — Redesign with segmented switch + counts
- `SearchScreen.kt` — Add search history, refine layout
- `UiModels.kt` — Add collection timestamp field

### New
- `CategoryBottomSheet.kt` — Multi-select category bottom sheet component
- `HomeScreen.kt` — New unified home screen with segments + filters + search bar
- `SearchHistoryStore.kt` — Local persistence for search history

### Removed
- Bottom navigation FAB component
- Genre tab as standalone screen

## Scope Notes

- Movie detail, actress page, genre movie list, and image viewer remain largely unchanged
- The redesign focuses on navigation structure, home screen layout, and collection screen
- All existing repository/data layer code is reused as-is
- Hilt DI structure remains the same, only ViewModel composition changes
