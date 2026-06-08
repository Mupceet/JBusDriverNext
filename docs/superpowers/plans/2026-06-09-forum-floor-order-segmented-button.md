# Forum Floor Order Segmented Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two-button floor order toggle with a Material 3 `SingleChoiceSegmentedButtonRow` and fix Simplified Chinese text to Traditional Chinese.

**Architecture:** Single-file UI change in `LabSettingsScreen.kt`. Remove the `FloorOrderButton` composable, replace the floor order section in `ForumCard` with `SingleChoiceSegmentedButtonRow`, fix text to Traditional Chinese.

**Tech Stack:** Jetpack Compose, Material 3 SegmentedButton API

---

### Task 1: Replace floor order UI with SegmentedButton

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt`

- [ ] **Step 1: Add new imports at the top of the file (after existing material3 imports)**

Add these three imports:

```kotlin
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
```

- [ ] **Step 2: Replace the floor order section inside `ForumCard` (lines 215–236)**

Replace this block:

```kotlin
            Column {
                Text("楼层浏览顺序", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloorOrderButton(
                        text = "正序",
                        selected = forumFloorOrder == ForumFloorOrder.REGULAR,
                        modifier = Modifier.weight(1f),
                        onClick = { onForumFloorOrderChange(ForumFloorOrder.REGULAR) }
                    )
                    FloorOrderButton(
                        text = "倒序",
                        selected = forumFloorOrder == ForumFloorOrder.REVERSE,
                        modifier = Modifier.weight(1f),
                        onClick = { onForumFloorOrderChange(ForumFloorOrder.REVERSE) }
                    )
                }
            }
```

With:

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

Note: This also fixes "楼层浏览顺序" → "樓層瀏覽順序" (Simplified → Traditional Chinese).

- [ ] **Step 3: Remove the `FloorOrderButton` composable (lines 240–262)**

Delete the entire `FloorOrderButton` function:

```kotlin
@Composable
private fun FloorOrderButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(text)
        }
    }
}
```

- [ ] **Step 4: Verify no unused imports remain**

`Button` and `OutlinedButton` are still used in `UrlSelectionCard` (lines 393–406), so keep those imports. No imports need removal.

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt
git commit -m "Redesign forum floor order as segmented button, fix Traditional Chinese text"
```
