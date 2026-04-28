# Phase 1: Remove Legacy UI Code and Resources

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all legacy MVP UI code (Activities, Fragments, Adapters, Holders, Widgets), layout XML resources, and legacy manifest entries. Migrate shared enums to modern package first.

**Architecture:** First migrate DataSourceType and SearchType enums to `modern/domain/model/`, update all imports. Then delete entire `ui/` package and all layout XML files. Finally update AndroidManifest to make ModernMainActivity the launcher.

**Tech Stack:** Kotlin, Android Gradle

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `modern/domain/model/DataSourceType.kt` | Migrated enum |
| Create | `modern/domain/model/SearchType.kt` | Migrated enum |
| Modify | 10 modern source files | Update DataSourceType imports |
| Modify | 4 modern source files | Update SearchType imports |
| Modify | 2 mvp/bean files | Update imports (kept for Phase 2) |
| Modify | `modern/ui/settings/SettingsScreen.kt` | Remove SettingActivity button |
| Delete | 34 files under `ui/` | Legacy UI code |
| Delete | 49 files under `res/layout/` | Legacy layout XML |
| Modify | `AndroidManifest.xml` | Remove legacy activities, set launcher |

---

### Task 1: Migrate DataSourceType and SearchType enums

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/domain/model/DataSourceType.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/domain/model/SearchType.kt`

- [ ] **Step 1: Create directory and move DataSourceType.kt**

Create `app/src/main/java/me/jbusdriver/modern/domain/model/DataSourceType.kt`:
```kotlin
package me.jbusdriver.modern.domain.model

enum class DataSourceType(val key: String, val prefix: String = "/") {
    CENSORED("有碼", "/page/"),
    GENRE("有碼類別"),
    ACTRESSES("有碼女優"),

    UNCENSORED("無碼", "/page/"),
    UNCENSORED_GENRE("無碼類別"),
    UNCENSORED_ACTRESSES("無碼女優"),

    XYZ("歐美", "/page/"),
    XYZ_GENRE("xyz/genre"),
    XYZ_ACTRESSES("xyz/actresses"),

    GENRE_HD("高清"),
    Sub("字幕");
}
```

Create `app/src/main/java/me/jbusdriver/modern/domain/model/SearchType.kt`:
```kotlin
package me.jbusdriver.modern.domain.model

enum class SearchType(val title: String, val urlPathFormater: String) {
    CENSORED("有碼影片", "/search/%s"),
    UNCENSORED("無碼影片", "/uncensored/search/%s"),
    ACTRESS("女優", "/searchstar/%s"),
    DIRECTOR("導演", "/search/%s&DBtype=2"),
    MAKER("製作商", "/search/%s&DBtype=3"),
    PUBLISHER("發行商", "/search/%s&DBtype=4"),
    SERIES("系列", "/search/%s&DBtype=5")
}
```

- [ ] **Step 2: Update imports in all modern source files**

Replace `import me.jbusdriver.ui.data.enums.DataSourceType` with `import me.jbusdriver.modern.domain.model.DataSourceType` in:
- `modern/ui/MainScreen.kt`
- `modern/data/MovieRepository.kt`
- `modern/ui/movielist/ActressListScreen.kt`
- `modern/ui/movielist/GenreListViewModel.kt`
- `modern/ui/movielist/GenreListScreen.kt`
- `modern/ui/movielist/ActressListViewModel.kt`
- `modern/ui/movielist/MovieListScreen.kt`
- `modern/ui/movielist/MovieListViewModel.kt`

Replace `import me.jbusdriver.ui.data.enums.SearchType` with `import me.jbusdriver.modern.domain.model.SearchType` in:
- `modern/data/SearchRepository.kt`
- `modern/ui/search/SearchScreen.kt`
- `modern/ui/search/SearchViewModel.kt`

Update in kept mvp/bean files:
- `mvp/bean/Bean.kt` — update DataSourceType import
- `mvp/bean/Menu.kt` — update DataSourceType import

- [ ] **Step 3: Delete old enum files**

Delete:
- `app/src/main/java/me/jbusdriver/ui/data/enums/DataSourceType.kt`
- `app/src/main/java/me/jbusdriver/ui/data/enums/SearchType.kt`

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: migrate DataSourceType and SearchType enums to modern/domain/model"
```

---

### Task 2: Remove legacy SettingsScreen button and SettingActivity dependency

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Remove SettingActivity reference from SettingsScreen.kt**

Remove line 3 (`import android.content.Intent`), line 34 (`import me.jbusdriver.ui.activity.SettingActivity`), and lines 65-77 (the "其他设置" section with the button).

The remaining file should be:
```kotlin
package me.jbusdriver.modern.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "数据源",
            style = MaterialTheme.typography.titleLarge
        )

        UrlSelector(
            currentUrl = uiState.baseUrl,
            availableUrls = uiState.availableUrls,
            isUpdating = uiState.isUpdating,
            onUrlSelected = { viewModel.updateUrl(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlSelector(
    currentUrl: String,
    availableUrls: List<String>,
    isUpdating: Boolean,
    onUrlSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentUrl,
                onValueChange = {},
                readOnly = true,
                label = { Text("当前地址") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableUrls.forEach { url ->
                    DropdownMenuItem(
                        text = { Text(url) },
                        onClick = {
                            onUrlSelected(url)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (isUpdating) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "可用源: ${availableUrls.size} 个",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt
git commit -m "refactor: remove legacy SettingActivity button from SettingsScreen"
```

---

### Task 3: Delete all legacy UI code

**Files:**
- Delete: entire `app/src/main/java/me/jbusdriver/ui/` directory (34 files remaining after enum migration)

- [ ] **Step 1: Delete ui/ directory**

```bash
rm -rf app/src/main/java/me/jbusdriver/ui/
```

This removes: activity/, fragment/, adapter/, holder/, widget/, data/, task/ — all 34 remaining legacy files.

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (some mvp/presenter files may reference ui/ imports — if so, those are Phase 2 files and will show errors. Check and report.)

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: remove all legacy MVP UI code (activities, fragments, adapters, holders, widgets)"
```

---

### Task 4: Delete layout XML resources and update AndroidManifest

**Files:**
- Delete: all files in `app/src/main/res/layout/`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Delete all layout XML files**

```bash
rm -rf app/src/main/res/layout/
```

- [ ] **Step 2: Update AndroidManifest.xml**

Replace entire AndroidManifest.xml with:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <application
        android:name="me.jbusdriver.modern.JBusApplication"
        android:allowBackup="true"
        android:usesCleartextTraffic="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.JBus"
        tools:targetApi="31">

        <activity
            android:name="me.jbusdriver.modern.ui.ModernMainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

Key changes: Removed all 7 legacy activities, removed LoadCollectService, removed SplashActivity theme, made ModernMainActivity the LAUNCHER with exported=true.

- [ ] **Step 3: Check for splash theme references**

Check if `res/values/themes.xml` or `res/values/styles.xml` references `Theme.JBus.Splash` — if so, remove that style entry (it's only used by deleted SplashActivity).

- [ ] **Step 4: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: delete legacy layout XML, update manifest to use ModernMainActivity as launcher"
```

---

### Task 5: Final verification

- [ ] **Step 1: Verify no references to deleted code remain**

Run: `grep -r "me.jbusdriver.ui" app/src/main/java/me/jbusdriver/modern/`
Expected: No matches (modern code has no legacy ui imports)

Run: `grep -r "R.layout" app/src/main/java/me/jbusdriver/modern/`
Expected: No matches

- [ ] **Step 2: Install and smoke test**

Run: `./gradlew installDebug`
Verify on device:
1. App launches directly into main screen (no splash)
2. Movie list loads
3. Movie detail loads
4. Search works
5. Settings screen shows only URL selector
6. Category dropdown works
7. Collection lists work

- [ ] **Step 3: Final commit if fixes needed**

---

## Self-Review

**1. Spec coverage:**
- Migrate DataSourceType/SearchType: Task 1
- Remove SettingsScreen legacy button: Task 2
- Delete ui/ package: Task 3
- Delete layout XML: Task 4
- Update AndroidManifest: Task 4
- Make ModernMainActivity launcher: Task 4
- Verification: Task 5

**2. Placeholder scan:** No TBD/TODO. All code shown.

**3. Type consistency:** Enum contents are identical, only package name changed. Import updates are mechanical string replacements.
