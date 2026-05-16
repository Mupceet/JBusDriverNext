# Collection Export & Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add export/import for collected movies and actresses, with backward compatibility for the legacy MVP project format.

**Architecture:** New methods on `CollectRepository` handle serialization. `CollectCategoryScreen` adds a MoreVert menu with export/import options using Android's `ActivityResultContracts` for file creation/opening. Legacy format is detected by checking if the top-level JSON element is an array.

**Tech Stack:** Gson (already in project), Room DAOs, Compose `DropdownMenu`, `ActivityResultContracts`

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `data/CollectRepository.kt` | Add `exportCollectionsJson()` and `importCollectionsFromJson()` |
| Modify | `ui/movielist/CollectCategoryScreen.kt` | Add MoreVert menu with export/import, activity result launchers |
| Create | `res/drawable/more_vert_24px.xml` | Three-dot menu icon |

No new classes needed. Export/import data classes use inline Gson `JsonObject`/`JsonArray` manipulation to avoid defining throwaway models.

---

### Task 1: Add export/import to CollectRepository

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt`

- [ ] **Step 1: Add interface methods and imports**

Add to the `CollectRepository` interface (after `getCollectedActresses`):

```kotlin
/** Export all collected movies and actresses as a JSON string (new format v1) */
suspend fun exportCollectionsJson(): String

/**
 * Import collections from a JSON string.
 * Supports both new format (v1) and legacy MVP format.
 * Skips items whose key already exists in the database.
 *
 * @return [imported count, skipped count]
 */
suspend fun importCollectionsFromJson(json: String): Pair<Int, Int>
```

- [ ] **Step 2: Implement export in DefaultCollectRepository**

Add imports at the top of the file:

```kotlin
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.core.toJsonString
```

Add implementation in `DefaultCollectRepository`:

```kotlin
override suspend fun exportCollectionsJson(): String {
    val movies = getCollectedMovies()
    val actresses = getCollectedActresses()

    val root = JsonObject().apply {
        addProperty("version", 1)
        addProperty("exportTime", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        add("movies", JsonArray().apply {
            movies.forEach { movie -> add(GSON.fromJson(movie.toJsonString(), JsonObject::class.java)) }
        })
        add("actresses", JsonArray().apply {
            actresses.forEach { actress -> add(GSON.fromJson(actress.toJsonString(), JsonObject::class.java)) }
        })
    }
    return GSON.toJson(root)
}
```

- [ ] **Step 3: Implement import in DefaultCollectRepository (new format + legacy detection)**

```kotlin
override suspend fun importCollectionsFromJson(json: String): Pair<Int, Int> {
    val element = GSON.fromJson(json, com.google.gson.JsonElement::class.java)
    return withContext(Dispatchers.IO) {
        if (element.isJsonArray) {
            importLegacyFormat(element.asJsonArray)
        } else {
            importNewFormat(element.asJsonObject)
        }
    }
}

private suspend fun importNewFormat(root: JsonObject): Pair<Int, Int> {
    var imported = 0
    var skipped = 0

    root.getAsJsonArray("movies")?.forEach { elem ->
        val movie = GSON.fromJson(elem, Movie::class.java)
        val item = movie.convertDBItem()
        if (DB.linkDao.hasByKey(item.dbType, item.key) >= 1) {
            skipped++
        } else {
            DB.linkDao.insert(item)
            imported++
        }
    }

    root.getAsJsonArray("actresses")?.forEach { elem ->
        val actress = GSON.fromJson(elem, ActressInfo::class.java)
        val item = actress.convertDBItem()
        if (DB.linkDao.hasByKey(item.dbType, item.key) >= 1) {
            skipped++
        } else {
            DB.linkDao.insert(item)
            imported++
        }
    }

    return imported to skipped
}

private suspend fun importLegacyFormat(array: JsonArray): Pair<Int, Int> {
    var imported = 0
    var skipped = 0

    array.forEach { elem ->
        val obj = elem.asJsonObject
        val type = obj.get("type")?.asInt ?: return@forEach
        val jsonStr = obj.get("jsonStr")?.asString ?: return@forEach

        when (type) {
            MovieDBType -> {
                // Legacy "detailUrl" maps directly to Movie.link via @SerializedName("detailUrl")
                val movie = GSON.fromJson(jsonStr, Movie::class.java) ?: return@forEach
                val item = movie.convertDBItem()
                if (DB.linkDao.hasByKey(item.dbType, item.key) >= 1) {
                    skipped++
                } else {
                    DB.linkDao.insert(item)
                    imported++
                }
            }
            ActressDBType -> {
                val actress = GSON.fromJson(jsonStr, ActressInfo::class.java) ?: return@forEach
                val item = actress.convertDBItem()
                if (DB.linkDao.hasByKey(item.dbType, item.key) >= 1) {
                    skipped++
                } else {
                    DB.linkDao.insert(item)
                    imported++
                }
            }
            // Ignore other types (Header, Genre, etc.)
        }
    }

    return imported to skipped
}
```

Note: `Movie.link` is annotated with `@SerializedName("detailUrl")`, so the legacy JSON field `"detailUrl"` maps directly — no field renaming needed.

- [ ] **Step 4: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt
git commit -m "feat: add export/import methods to CollectRepository with legacy format support"
```

---

### Task 2: Add more_vert drawable

**Files:**
- Create: `app/src/main/res/drawable/more_vert_24px.xml`

- [ ] **Step 1: Create the vector drawable**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M480,800Q447,800 423.5,776.5Q400,753 400,720Q400,687 423.5,663.5Q447,640 480,640Q513,640 536.5,663.5Q560,687 560,720Q560,753 536.5,776.5Q513,800 480,800ZM480,560Q447,560 423.5,536.5Q400,513 400,480Q400,447 423.5,423.5Q447,400 480,400Q513,400 536.5,423.5Q560,447 560,480Q560,513 536.5,536.5Q513,560 480,560ZM480,320Q447,320 423.5,296.5Q400,273 400,240Q400,207 423.5,183.5Q447,160 480,160Q513,160 536.5,183.5Q560,207 560,240Q560,273 536.5,296.5Q513,320 480,320Z" />
</vector>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/drawable/more_vert_24px.xml
git commit -m "feat: add more_vert_24px vector drawable"
```

---

### Task 3: Expose collectRepository in CollectionListViewModel

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`

- [ ] **Step 1: Make collectRepository public**

In `CollectionListViewModel`, change the constructor parameter visibility:

```kotlin
// From:
class CollectionListViewModel @Inject constructor(
    private val collectRepository: CollectRepository
) : ViewModel() {

// To:
class CollectionListViewModel @Inject constructor(
    val collectRepository: CollectRepository
) : ViewModel() {
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt
git commit -m "refactor: expose collectRepository in CollectionListViewModel for export/import"
```

---

### Task 4: Add export/import UI to CollectCategoryScreen

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt`

`CollectCategoryScreen` already creates a `CollectionListViewModel` as `movieVm`. After Task 3, we can access `movieVm.collectRepository`.

- [ ] **Step 1: Add all needed imports**

Add these imports at the top of `CollectCategoryScreen.kt`:

```kotlin
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jbusdriver.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

- [ ] **Step 2: Add state and launchers**

Inside the function body, after the existing `LaunchedEffect` blocks and before the `Column { ... }`, add:

```kotlin
val context = LocalContext.current
val scope = rememberCoroutineScope()
var showMenu by remember { mutableStateOf(false) }
val repo = movieVm.collectRepository

val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json")
) { uri: Uri? ->
    uri ?: return@rememberLauncherForActivityResult
    scope.launch {
        try {
            val json = withContext(Dispatchers.IO) { repo.exportCollectionsJson() }
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, "導出成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "導出失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri: Uri? ->
    uri ?: return@rememberLauncherForActivityResult
    scope.launch {
        try {
            val json = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("無法讀取檔案")
            }
            val (imported, skipped) = withContext(Dispatchers.IO) {
                repo.importCollectionsFromJson(json)
            }
            val msg = if (skipped > 0) "導入 $imported 項，跳過 $skipped 項" else "導入 $imported 項"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            movieVm.loadCollection(MovieDBType)
        } catch (e: Exception) {
            Toast.makeText(context, "導入失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

- [ ] **Step 3: Replace the "我的收藏" Text with a Row containing MoreVert menu**

Replace the existing `Text("我的收藏", ...)` block (inside the `Column`, before the `Row` of FilterChips):

```kotlin
// Replace this:
Text(
    "我的收藏",
    style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
)

// With this:
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        "我的收藏",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f)
    )
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                painterResource(R.drawable.more_vert_24px),
                contentDescription = "更多"
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("導出收藏") },
                onClick = {
                    showMenu = false
                    val filename = "jbus_backup_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json"
                    exportLauncher.launch(filename)
                }
            )
            DropdownMenuItem(
                text = { Text("導入收藏") },
                onClick = {
                    showMenu = false
                    importLauncher.launch(arrayOf("application/json"))
                }
            )
        }
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt
git commit -m "feat: add export/import menu to collection screen"
```

---

### Task 5: Manual smoke test

- [ ] **Step 1: Install debug APK**

Run: `./gradlew installDebug`

- [ ] **Step 2: Test export**

1. Add some movies and actresses to collection
2. Go to collection tab
3. Tap MoreVert → "導出收藏"
4. Save the file
5. Verify the JSON content has `version: 1`, `movies` array, `actresses` array

- [ ] **Step 3: Test import (new format)**

1. Remove some collected items
2. Tap MoreVert → "導入收藏"
3. Select the previously exported file
4. Verify Toast shows correct import count
5. Verify items reappear in collection

- [ ] **Step 4: Test import (legacy format)**

1. Use the `backup1778935259563.json` file
2. Tap MoreVert → "導入收藏"
3. Select the legacy file
4. Verify movies and actresses are imported correctly
5. Verify existing items are skipped (no duplicates)
