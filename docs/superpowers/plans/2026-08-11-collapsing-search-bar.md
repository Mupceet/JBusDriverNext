# Collapsing Shared Search Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace the four per-tab search bars with a single shared search bar above the main tabs that hides as the list scrolls down, reappears as it scrolls up, and never conflicts with pull-to-refresh at the top.

**Architecture:** `MainScreen` owns one `CollapsingSearchBar` at the top of its content `Column`, driven by Material3 `TopAppBarDefaults.enterAlwaysScrollBehavior()`. The tab content is wrapped with `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`; the bar's visible height is `max(0, naturalHeight + state.heightOffset)` so it collapses continuously with scroll. Pull-to-refresh is safe because `PullToRefreshBox` sits inside the scroll hierarchy and consumes the pull-down delta before the enterAlways connection observes it; at the top the bar is always fully expanded. On tab switch the bar resets to expanded.

**Tech Stack:** Jetpack Compose, Material3 1.4.0 (`TopAppBarState`, `TopAppBarDefaults.enterAlwaysScrollBehavior`, `PullToRefreshBox`), Kotlin, Hilt.

**Testing note:** This is a layout/scroll-behavior change and the repo has no Compose UI test infrastructure, so no new unit tests are added. Verification = `./gradlew assembleDebug`, `./gradlew testDebugUnitTest` (regression), and the manual checklist in Task 5.

---

## File Structure

| File | Change |
|---|---|
| `app/src/main/java/me/jbusdriver/modern/ui/components/SearchBar.kt` | Add `CollapsingSearchBar` |
| `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt` | Add shared bar + scroll behavior; remove per-tab `onSettingsClick` wiring; reset on tab switch |
| `app/src/main/java/me/jbusdriver/modern/ui/MainTabContent.kt` | Remove `onSettingsClick` params + `SearchBarWithSettings` blocks |
| `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt` | Remove `SearchBarWithSettings` block + `onSettingsClick` param |

## Task 1: Add `CollapsingSearchBar` to `components/SearchBar.kt`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/SearchBar.kt`

- [x] **Step 1: Add imports**

Add to the import section of the file:

```kotlin
import androidx.compose.foundation.layout.Layout
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Constraints
```

- [x] **Step 2: Append the composable at the end of the file**

```kotlin

@Composable
fun CollapsingSearchBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    scrollState: TopAppBarState,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        content = {
            SearchBarWithSettings(
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp)
            )
        }
    ) { measurables, constraints ->
        val placeable = measurables[0].measure(
            constraints.copy(maxHeight = Constraints.Infinity)
        )
        val visibleHeight = (placeable.height + scrollState.heightOffset).coerceAtLeast(0)
        layout(constraints.maxWidth, visibleHeight) {
            placeable.place(0, 0)
        }
    }
}
```

Notes:
- The child is measured with unbounded max height so `placeable.height` is the search bar's natural height.
- `scrollState.heightOffset` is negative when collapsed (range `[heightOffsetLimit, 0]`); reading it in the measure block re-lays-out the bar on every scroll delta.
- `clipToBounds()` clips the bar as it shrinks.

- [x] **Step 3: Compile check**

Run: `./gradlew assembleDebug --console=plain -q`
Expected: BUILD SUCCESSFUL (exit 0).

## Task 2: Rework `MainScreen.kt` — shared bar + scroll behavior

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`

- [x] **Step 1: Add imports**

```kotlin
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
```

- [x] **Step 2: Create the scroll behavior and reset on tab switch**

Inside the `MainScreen` composable body, right before `Scaffold(`, add:

```kotlin
    // Shared search bar collapses when the active list scrolls down (Material enterAlways).
    val searchBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // A freshly selected tab starts with the search bar expanded.
    LaunchedEffect(selectedCategory) {
        searchBarScrollBehavior.state.heightOffset = 0f
    }
```

- [x] **Step 3: Replace the Scaffold content lambda**

Replace the `) { innerPadding -> ... }` content lambda of `Scaffold` with:

```kotlin
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CollapsingSearchBar(
                onSearchClick = { onSearchClick("") },
                onSettingsClick = onSettingsClick,
                scrollState = searchBarScrollBehavior.state
            )
            saveableStateHolder.SaveableStateProvider(selectedCategory) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                ) {
                    when (selectedCategory) {
                        BottomNavCategory.MOVIE -> MovieTabContent(
                            isGrid = isGrid,
                            toggleGrid = toggleGrid,
                            onSearchClick = onSearchClick,
                            onMovieClick = onMovieClick
                        )

                        BottomNavCategory.ACTRESS -> ActressTabContent(
                            onSearchClick = onSearchClick,
                            onActressClick = onActressClick
                        )

                        BottomNavCategory.COLLECT -> CollectCategoryScreen(
                            onMovieClick = onMovieClick,
                            onActressClick = onActressClick,
                            onSearchClick = onSearchClick
                        )

                        BottomNavCategory.FORUM -> {
                            ForumBoardsScreen(
                                onBoardClick = onForumBoardClick,
                                onThreadClick = onForumThreadClick
                            )
                        }
                    }
                }
            }
        }
    }
```

- [x] **Step 4: Compile check**

Run: `./gradlew assembleDebug --console=plain -q`
Expected: FAILS until Tasks 3-4 remove the leftover `onSettingsClick` usages.

## Task 3: Strip per-tab search bars in `MainTabContent.kt`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainTabContent.kt`

- [x] **Step 1: Remove the import**

Remove: `import me.jbusdriver.modern.ui.components.SearchBarWithSettings`

- [x] **Step 2: Remove `onSettingsClick` from `MovieTabContent`**

Remove the signature line:
```kotlin
    onSettingsClick: () -> Unit,
```

Remove the block (first statement after the `showCategorySheet` `if` block):
```kotlin
    SearchBarWithSettings(
        onSearchClick = { onSearchClick("") },
        onSettingsClick = onSettingsClick,
        modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp)
    )

```

- [x] **Step 3: Remove `onSettingsClick` from `ActressTabContent`**

Same two removals (signature line + `SearchBarWithSettings(...)` block).

- [x] **Step 4: Compile check**

Run: `./gradlew assembleDebug --console=plain -q`
Expected: BUILD SUCCESSFUL.

## Task 4: Strip the search bar in `CollectCategoryScreen.kt`

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt`

- [x] **Step 1: Remove the import**

Remove: `import me.jbusdriver.modern.ui.components.SearchBarWithSettings`

- [x] **Step 2: Remove the `onSettingsClick` param**

Remove from the signature: `    onSettingsClick: () -> Unit = {}`

- [x] **Step 3: Remove the `SearchBarWithSettings` block** (first child of the content `Column`):

```kotlin
        SearchBarWithSettings(
            onSearchClick = { onSearchClick("") },
            onSettingsClick = onSettingsClick,
            modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp)
        )

```

- [x] **Step 4: Compile check**

Run: `./gradlew assembleDebug --console=plain -q`
Expected: BUILD SUCCESSFUL.

## Task 5: Verify

- [x] **Step 1: Full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 2: Unit tests (regression)**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Manual checklist (device/emulator)**

1. MOVIE tab: scroll list down -> search bar collapses; scroll up -> bar reappears; at top, pull down -> refresh indicator works, bar stays visible.
2. ACTRESS tab: same as (1).
3. COLLECT tab: scroll -> bar collapses/reappears (no pull-to-refresh here; import/export menu still works).
4. FORUM tab: same as (1).
5. Switch tabs while the bar is collapsed -> bar resets to expanded on the new tab.
6. Settings icon on the shared bar opens settings from every tab.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/SearchBar.kt app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt app/src/main/java/me/jbusdriver/modern/ui/MainTabContent.kt app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt
git commit -m "feat(ui): collapse shared search bar on scroll across main tabs"
```

---

## Self-Review

- Spec coverage: shared bar above tabs (Task 2); scroll-linked hide/show (Tasks 1-2); pull-to-refresh non-conflict (architecture + Task 5 manual); tab-switch reset (Task 2 Step 2).
- No placeholders: every step contains exact code or commands.
- Type consistency: `searchBarScrollBehavior.state` is `TopAppBarState`; `CollapsingSearchBar(scrollState = ...)` matches the component signature; `heightOffset` setter is public on `TopAppBarState`.
