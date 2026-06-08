# Forum Floor Order: Segmented Button Redesign

**Date:** 2026-06-09
**Status:** Approved

## Problem

The forum floor order setting in `LabSettingsScreen` uses two full-width `Button`/`OutlinedButton` controls side by side. This looks visually heavy and disproportionate for a simple two-option toggle. Additionally, the label text ("楼层浏览顺序") uses Simplified Chinese while all other text in the screen uses Traditional Chinese.

## Solution

Replace the two-button layout with a Material 3 `SingleChoiceSegmentedButtonRow`, and fix the text to Traditional Chinese.

## Changes

### `LabSettingsScreen.kt`

1. **Remove** the `FloorOrderButton` composable (lines 240–262) — no longer needed.

2. **Replace** the floor order section in `ForumCard` (lines 215–236) with:

```kotlin
Column {
    Text("樓層瀏覽順序", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = forumFloorOrder == ForumFloorOrder.REGULAR,
            onClick = { onForumFloorOrderChange(ForumFloorOrder.REGULAR) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) {
            Text("正序")
        }
        SegmentedButton(
            selected = forumFloorOrder == ForumFloorOrder.REVERSE,
            onClick = { onForumFloorOrderChange(ForumFloorOrder.REVERSE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) {
            Text("倒序")
        }
    }
}
```

3. **Add imports:**
   - `androidx.compose.material3.SegmentedButton`
   - `androidx.compose.material3.SegmentedButtonDefaults`
   - `androidx.compose.material3.SingleChoiceSegmentedButtonRow`

4. **Remove unused imports** that were only used by `FloorOrderButton`:
   - `Button` (if no longer used elsewhere in this file — check `UrlSelectionCard` first, it still uses `Button`)
   - `OutlinedButton` (same check — still used in `UrlSelectionCard`)

### Text corrections

| Current (Simplified) | Corrected (Traditional) |
|---|---|
| 楼层浏览顺序 | 樓層瀏覽順序 |

## No changes to

- `ForumFloorOrder` enum — labels are the same in both scripts
- `LabSettingsStore` — data layer unchanged
- Any other settings or screens

## Visual outcome

A compact capsule-shaped segmented button row with two options. The selected option gets a filled background with a checkmark icon (Material 3 default behavior). The unselected option shows a lighter outline style. Width fills the card, height is minimal — much less visual weight than the previous two large buttons.
