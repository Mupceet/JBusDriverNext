# GenreCategoryScreen Sticky Header Redesign

Date: 2026-05-13

## Summary

Replace GenreCategoryScreen's dual-layer tabs with a single-tab + LazyColumn sticky header layout. All genre groups display in one continuous scrollable list with group titles as sticky headers.

## Problem

Current implementation has two stacked `ScrollableTabRow`s (source + theme group), which causes:
- Excessive vertical space consumed by navigation chrome
- Visual confusion from two tab rows
- Low information density (only one group visible at a time)
- Janky tab switching when loading dynamic data

## Solution

Remove the inner `ScrollableTabRow` entirely. Replace with `LazyColumn` + `stickyHeader`:

- Outer tab (有码类别 / 无码类别) stays unchanged
- All `GenreCategory` groups render in a single `LazyColumn`
- Each group: `stickyHeader` with group title, `item` with `FlowRow` of `AssistChip`s
- `LazyColumn` lazy-loads at group granularity — only visible groups are composed
- Pull-to-refresh preserved

## Performance

~5-8 groups with 10-50 chips each (~100-300 total). LazyColumn renders only 2-3 visible groups (~30-100 chips) at any time.

## File Changes

| File | Change |
|------|--------|
| `ui/movielist/GenreCategoryScreen.kt` | Remove inner ScrollableTabRow, add LazyColumn with stickyHeader |

No other files change.
