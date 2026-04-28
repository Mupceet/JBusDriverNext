# Detail & List Screen Improvements

## Context
6 issues affecting usability: broken actress/genre lists, slow navigation, redundant detail header, duplicate description, no image preview, no magnet links.

## Design

### 1. Actress/Genre "没有数据"
- Add `ActressListScreen` (avatar + name list) and `GenreListScreen` (chip grid) composables
- In `MovieRepository`, handle ACTRESSES/GENRE types with correct URL construction and parsing (`parseActressList()` / `.genre-box`)
- `MainScreen` routes to the correct screen composable based on `DataSourceType`

### 2. Navigation animation too long
- Set shorter transitions on `NavHost`: `slideInHorizontally` 300ms enter, 200ms exit

### 3. Redundant header info → sticky info bar
- Use scroll state to detect when cover scrolls out of view
- When cover hidden, show a sticky info bar (code + title) below TopAppBar using `LazyColumn.stickyHeader`

### 4. Duplicate description
- Remove standalone `content` section from `DetailContent`
- In headers rendering, detect "描述" key and render as multi-line body text

### 5. Image preview
- New full-screen `ImageViewerScreen`: `HorizontalPager` with pinch-to-zoom, index indicator
- Cover click → single image viewer
- Screenshot click → multi-image viewer starting at clicked index
- Implemented as NavHost route or full-screen Dialog

### 6. Magnet links BottomSheet
- Add magnet loading to `MovieDetailViewModel` via `MagnetManager`
- "查看磁力链接" button at detail bottom
- `ModalBottomSheet` with magnet list (name, size, date)
- Click to copy magnet link to clipboard

## Files to modify
- `modern/data/MovieRepository.kt` — handle actress/genre types
- `modern/ui/MainScreen.kt` — route to correct screen
- `modern/ui/Navigation.kt` — shorter transitions, image viewer route
- `modern/ui/detail/MovieDetailScreen.kt` — sticky header, remove dup description, magnet button
- `modern/ui/detail/MovieDetailViewModel.kt` — magnet loading
- `modern/ui/UiModels.kt` — magnet UI model

## New files
- `modern/ui/movielist/ActressListScreen.kt`
- `modern/ui/movielist/GenreListScreen.kt`
- `modern/ui/image/ImageViewerScreen.kt`
