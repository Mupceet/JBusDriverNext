# Image Viewer Enhancement Design

**Goal:** Enhance ImageViewScreen with unified image list, bottom thumbnail strip, and save/share actions.

## Feature 1: Unified Image List

**Current behavior:** Cover click passes `[cover]` only; sample click passes `[sample1, sample2, ...]` only. Two separate viewer sessions.

**New behavior:** `DetailContent` pre-builds `allImages = [cover] + samples.map { it.image }`. All image clicks pass this unified list:
- Cover click → `onImageClick(allImages, 0)`
- Sample click at index i → `onImageClick(allImages, i + 1)` (offset by 1 for cover)

**Files changed:** `MovieDetailScreen.kt` (DetailContent only)

## Feature 2: Bottom Thumbnail Strip

**Layout:** A `LazyRow` pinned at the bottom of the viewer, overlaid on the black background with semi-transparent background. Each thumbnail is a small `AsyncImage` (~48x36dp, rounded corners). The current page's thumbnail has a white border highlight.

**Interaction:** Clicking a thumbnail calls `pagerState.scrollToPage(index)`. The strip auto-scrolls to keep the current page visible via `LaunchedEffect(pagerState.currentPage)`.

**Thumbnails source:** Uses `images` list directly — the same URLs passed to the pager. For cover (first item), uses the cover URL as thumbnail. For samples, uses the same URLs (they're already sample images).

**Files changed:** `ImageViewScreen.kt`

## Feature 3: Save to Gallery & Share

**Save button (download icon):** Downloads current page's image via OkHttp, saves to Pictures directory via MediaStore `ContentValues` insert. Shows Toast on success/failure. Requires `WRITE_EXTERNAL_STORAGE` permission handling (only needed for API < 29; app minSdk is 28 so we need a check).

**Share button (share icon):** Downloads current page's image to cache dir via `FileProvider`, then launches `Intent.ACTION_SEND` with `createChooser`.

**Icon choices:** Only `material-icons-core` available. Use `Icons.Filled.Star` or similar for save — actually the available icons include `Favorite` (bookmark-style). For a download icon, we don't have one in core. Options: use `Create` (pencil) as save icon, or use text labels. Better: use `Icons.Filled.List` or `Icons.Filled.Share` (already used). Available icons: `Check`, `Close`, `DateRange`, `Edit`, `Email`, `Favorite`, `Info`, `List`, `LocationOn`, `Lock`, `Menu`, `MoreVert`, `Notifications`, `Person`, `Phone`, `Place`, `PlayArrow`, `Refresh`, `Search`, `Send`, `Settings`, `Share`, `ShoppingCart`, `Star`, `ThumbUp`, `Warning`.

For save: use `Star` icon (represents "save/favorite"). For share: use `Send` icon (represents "share/send out"), since `Share` is already used for copy in magnet items.

**Files changed:** `ImageViewScreen.kt` (UI + logic), `AndroidManifest.xml` (FileProvider config if needed)

## Scope

Single screen change (`ImageViewScreen.kt`) + one caller change (`MovieDetailScreen.kt`). No new files needed. Save/share logic lives in ImageViewScreen as composable-scope functions using `remember`ed coroutine scope.
