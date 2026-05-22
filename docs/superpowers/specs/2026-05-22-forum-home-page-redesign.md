# Forum Home Page Redesign

## Goal

Add a carousel and tabbed thread preview section to the forum home page, above the existing board categories. All data comes from the same `forum.php` page — no additional network requests needed.

## Layout (top → bottom, single scrollable column)

### 1. Carousel — 10 banner images

Auto-rotating pager with thread title overlaid at the bottom.

**Data model:**
```kotlin
data class ForumBanner(
    val tid: Int,
    val title: String,
    val imageUrl: String
)
```

**HTML selector:** `ul.slideshow > li`
- Image: `a.biaoqicn_imga > img` → `src` attribute (prepend base URL for relative paths)
- Title: `p.biaoqicn_title` → text content
- Thread link: `a[href*=viewthread]` → extract `tid` from href

**UI:** HorizontalPager with 16:9 aspect ratio, bottom gradient overlay with title text and dot indicator. Auto-advance every 4 seconds. Tap navigates to thread detail.

### 2. Tabbed Thread List — 3 tabs, 7 items each

Horizontal tab row: 最新主題 / 最新回復 / 熱點話題. Each tab shows a list of 7 thread items.

**Data model:**
```kotlin
data class ForumSummaryThread(
    val tid: Int,
    val title: String,
    val author: String
)

data class ForumHomeSummary(
    val latestThreads: List<ForumSummaryThread>,
    val latestReplies: List<ForumSummaryThread>,
    val hotTopics: List<ForumSummaryThread>
)
```

**HTML selectors:**
- 最新主題: `#con_NewOne_1 .sideMenu > h3`
- 最新回復: `#con_NewOne_2 .sideMenu > h3`
- 熱點話題: `#con_NewOne_3 .sideMenu > h3`

Each `h3` contains:
- Author: `em > a[href*=space]` → text
- Thread: `a[href*=viewthread]` → `title` attribute + extract `tid` from href

**UI:** TabRow with 3 tabs. Each item shows author name (small, muted) + thread title (single line, ellipsis). Tap navigates to thread detail.

### 3. Board Categories — existing

The current board groups (綜合交流區 + 福利討論分類) remain unchanged at the bottom.

## Data Flow

**Parser:** Add `parseForumHomeData(doc: Document)` to `HtmlParser.kt` that returns:
```kotlin
data class ForumHomeData(
    val banners: List<ForumBanner>,
    val summary: ForumHomeSummary,
    val boardGroups: List<ForumBoardGroup>
)
```

This replaces the current `parseForumBoards(doc)` call. The parser extracts all three sections from the same document.

**Repository:** `ForumRepository.loadForumBoards()` now returns `ForumHomeData` instead of `List<ForumBoardGroup>`. The method still fetches the same URL (`forum.php`), same caching strategy.

**ViewModel:** `ForumBoardsUiState` gains `banners` and `summary` fields alongside existing `groups`.

**Screen:** `ForumBoardsScreen` renders carousel → tabbed list → board groups in a single `LazyColumn`.

## Files to Modify

1. `ForumModels.kt` — add `ForumBanner`, `ForumSummaryThread`, `ForumHomeSummary`, `ForumHomeData`
2. `HtmlParser.kt` — add `parseForumHomeData()`, deprecate `parseForumBoards()`
3. `ForumRepository.kt` — update `loadForumBoards()` return type
4. `ForumViewModels.kt` — update `ForumBoardsUiState` and ViewModel logic
5. `ForumBoardsScreen.kt` — add carousel and tabbed list composables

## Out of Scope

- 熱門主題 (hot topics with thumbnails)
- 精選內容 (featured content)
- Pull-to-refresh already exists, applies to all sections
