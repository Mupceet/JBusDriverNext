# Unified Error View Design

## Problem

Error states across 6 screens are inconsistent in style and behavior:
- All use `Box + Text(errorColor)` with no retry mechanism
- Only MovieDetailScreen has a retry button; CollectionListScreen and SearchScreen lack even pull-to-refresh
- No visual polish — plain error text with no icon or animation

## Solution

Create a reusable `ErrorView` composable with a Material icon, animated entrance, error message, and retry button. Replace all full-screen error states across the app.

## Component: `ErrorView`

**Location:** `ui/components/ErrorView.kt`

**Parameters:**
- `message: String` — error text to display
- `onRetry: () -> Unit` — retry callback
- `modifier: Modifier = Modifier`

**Layout** (centered Column):
1. `Icons.Outlined.CloudOff` — 48dp, `onSurfaceVariant` color
2. Error text — `bodyLarge`, `onSurfaceVariant`, top padding 16dp
3. OutlinedButton "重試" — top padding 16dp, calls `onRetry`

**Animation:** Icon and text enter with `fadeIn + scaleIn` (initialScale 0.8f, 300ms tween), triggered via `AnimatedVisibility(visible = true)`. Animation runs once on composition.

## Replacement Map

| Screen | Retry Action |
|--------|-------------|
| MovieListScreen | `viewModel.refresh()` |
| ActressListScreen | `viewModel.refresh()` |
| CollectionListScreen | `viewModel.loadCollection(dbType)` |
| LinkMovieListScreen | `viewModel.refresh()` |
| SearchScreen | `viewModel.search(query, searchType)` |
| MovieDetailScreen | `viewModel.loadDetail(movieUrl)` |

**Not replaced:**
- `ActressDetailErrorCard` in LinkMovieListScreen (inline header, not full-screen)
- Magnet error text in MovieDetailScreen (list-internal error)

## Files Changed

- **New:** `ui/components/ErrorView.kt`
- **Modified:** MovieListScreen, ActressListScreen, CollectionListScreen, LinkMovieListScreen, SearchScreen, MovieDetailScreen (replace error Box/Text with ErrorView)

## Dependencies

No new dependencies. Uses Compose built-in animation APIs (`AnimatedVisibility`, `fadeIn`, `scaleIn`, `tween`).
