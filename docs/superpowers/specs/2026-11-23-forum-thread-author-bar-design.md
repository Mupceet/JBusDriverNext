# Forum Thread Detail: Author Row in the Top Bar

**Date:** 2026-11-23
**Status:** Implemented (simplified after on-device review of the scroll-linked variant)

## Problem

The thread detail top bar put `detail.title` in its title slot, duplicating the title rendered at the top of the first post (`ThreadHeader`), and the first post's header carried the author row (avatar + name + level) that repeated on every floor.

## Solution

- The top bar title slot permanently shows the author row: 32dp circular avatar, bold `labelMedium` name (single line, end ellipsis), `labelSmall` primary-colored level when present. `ThreadAuthorRow` lives in `ForumThreadDetailSections.kt` and is the bar's only consumer.
- The thread title is no longer rendered in the bar; the header keeps it (title first, then the 發表時間 | 瀏覽·回覆 meta row).
- During loading / error (`detail == null`) the title slot is empty.
- No scroll linkage, no new state, nothing in the ViewModel.

## History

A scroll-linked variant (Approach A: author row fades into the bar as the first post's author row slides under it, progress = pure function of `LazyListState.layoutInfo`) was implemented and reviewed on device, then dropped by user decision in favor of the always-visible bar.

Two findings from that attempt worth keeping:

- `LazyListItemInfo.offset` is relative to the content area, **excluding top contentPadding** (item 0 at rest is 0, not the padding). Screen-space position = `offset − layoutInfo.viewportStartOffset`, where `viewportStartOffset` is always `-beforeContentPadding` (verified in foundation 1.13.0-beta01 sources, `LazyListMeasure.kt`).
- Before the first layout pass, `layoutInfo.visibleItemsInfo` is empty; a scroll-linked gate must treat that as "at top", not "scrolled out".

## Testing

No logic functions remain (pure UI). Existing forum unit tests stay green; verified on device via debug install.
