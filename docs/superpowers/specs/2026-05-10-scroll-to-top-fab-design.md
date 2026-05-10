# Scroll-to-Top FAB Design

## Overview

Add a Samsung-style floating "scroll to top" button to all list screens. The button appears at the bottom-center of the list when the user scrolls past the first page, and smoothly scrolls back to the top when tapped.

## Visual Design

- **Shape**: Circle, 48dp diameter
- **Background**: White with elevation shadow
- **Icon**: `arrow_circle_up_24px` drawable, dark gray tint
- **Position**: Horizontally centered, 24dp from bottom of list area
- **Animation**: `AnimatedVisibility` with fadeIn/fadeOut + scale transition

## Components

### `ScrollToTopButton` (new)

File: `ui/components/ScrollToTopButton.kt`

A standalone Composable that renders the FAB and handles click-to-scroll:

- Accepts `visible: Boolean` and `onClick: () -> Unit`
- Renders a circular button with the arrow-up icon
- Wraps itself in `AnimatedVisibility` for show/hide transitions

### `rememberIsScrolledPastFirstPage` (new)

In the same file, two overloads:

- One for `LazyListState`: returns `true` when `firstVisibleItemIndex > 0`
- One for `LazyGridState`: same logic via `derivedStateOf`

Both use `derivedStateOf` to minimize recompositions.

### Integration into `MovieList`

The `MovieList` composable already wraps its LazyColumn/LazyVerticalGrid in a `Box`. Add the `ScrollToTopButton` as a second child in that Box, positioned at `Alignment.BottomCenter`.

The existing `LazyListState` / `LazyGridState` (already created internally via `rememberLazyListState()` / `rememberLazyGridState()`) feeds into `rememberIsScrolledPastFirstPage` to drive visibility.

### Integration into `ActressGrid`

Same pattern: wrap the existing grid in a `Box` (if not already), add `ScrollToTopButton` at `Alignment.BottomCenter`.

## Files Changed

| File | Change |
|------|--------|
| `ui/components/ScrollToTopButton.kt` | New file: button composable + scroll detection utilities |
| `ui/components/MovieList.kt` | Add FAB overlay using existing Box + scroll state |
| `ui/components/ActressGrid.kt` | Add FAB overlay with LazyGridState |

No Screen-level or ViewModel changes required.

## Behavior

1. User scrolls down in any movie list or actress list
2. Once `firstVisibleItemIndex > 0`, the button fades in at bottom-center
3. User taps the button
4. List smooth-scrolls to item index 0
5. As the list reaches the top, the button fades out

## Scope

- Applies to all screens using `MovieList` or `ActressGrid`: MovieListScreen, LinkMovieListScreen, CollectionListScreen, ActressListScreen
- Does NOT apply to `GenreListScreen` (uses `Column` + `verticalScroll`, no lazy list)
