# Directory Restructuring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure package directories so dependency direction is `ui → data → domain → core`, each directory has a single clear responsibility.

**Architecture:** Move files to their architecturally correct layer. Bean.kt is split into DB types (data/db), mappers (data/db), and navigation models (domain/model). HtmlParser moves from domain to data. Small files are merged. Global.kt is split.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Jsoup

---

### Task 1: Move urlHost/urlPath to domain/model/UrlExt.kt

Removes domain→core dependency for URL parsing utils.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/domain/model/UrlExt.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/core/Global.kt` (remove urlHost/urlPath/urlCache)
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/MovieDetail.kt` (remove core.urlHost import)
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/Bean.kt` (remove core.urlPath import)
- Modify: `app/src/main/java/me/jbusdriver/modern/data/MovieRepository.kt` (change import)
- Modify: `app/src/main/java/me/jbusdriver/modern/data/MovieDetailRepository.kt` (change import)

- [ ] **Step 1: Create domain/model/UrlExt.kt**

```kotlin
package me.jbusdriver.modern.domain.model

import android.net.Uri
import androidx.collection.LruCache
import androidx.core.net.toUri

/** URL 解析结果缓存 */
private val urlCache by lazy { LruCache<String, Uri>(512) }

/** 从 URL 字符串提取 host 部分（scheme://host） */
val String.urlHost: String
    get() = (urlCache.get(this) ?: let {
        val uri = Uri.parse(this)
        urlCache.put(this, uri)
        uri
    }).let {
        checkNotNull(it)
        "${it.scheme}://${it.host}"
    }

/** 从 URL 字符串提取路径部分（不含 scheme 和 host） */
val String.urlPath: String
    get() = (urlCache[this] ?: let {
        val uri = this.toUri()
        urlCache.put(this, uri)
        uri
    }).path ?: ""
```

- [ ] **Step 2: Remove urlHost/urlPath/urlCache from core/Global.kt**

In `core/Global.kt`, delete these items:
- `import android.net.Uri`
- `import androidx.collection.LruCache`
- `import androidx.core.net.toUri`
- `private val urlCache by lazy { ... }`
- `val String.urlHost: String ...`
- `val String.urlPath: String ...`

After removal, Global.kt should contain only: `GSON` and `createDir()` with their remaining imports (`GsonBuilder`, `JsonDeserializer`, `Log`, `Date`, `File`, `STATIC`, `TRANSIENT`).

- [ ] **Step 3: Update MovieDetail.kt import**

Remove `import me.jbusdriver.modern.core.urlHost` — no import needed since `urlHost` is now in the same package `domain.model`.

- [ ] **Step 4: Update Bean.kt import**

Remove `import me.jbusdriver.modern.core.urlPath` — same package.

- [ ] **Step 5: Update MovieRepository.kt import**

Change: `import me.jbusdriver.modern.core.urlPath` → `import me.jbusdriver.modern.domain.model.urlPath`

- [ ] **Step 6: Update MovieDetailRepository.kt import**

Change: `import me.jbusdriver.modern.core.urlPath` → `import me.jbusdriver.modern.domain.model.urlPath`

- [ ] **Step 7: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```
refactor: move urlHost/urlPath from core to domain/model/UrlExt.kt
```

---

### Task 2: Split Global.kt into GsonExt.kt + FileUtil.kt, delete Global.kt

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/core/GsonExt.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/core/FileUtil.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/core/BaseExtension.kt` (remove Gson extensions)
- Delete: `app/src/main/java/me/jbusdriver/modern/core/Global.kt`

- [ ] **Step 1: Create core/GsonExt.kt**

Move `GSON` instance from Global.kt. Move `Gson.fromJson<T>()` and `Any?.toJsonString()` from BaseExtension.kt.

```kotlin
package me.jbusdriver.modern.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Modifier.STATIC
import java.lang.reflect.Modifier.TRANSIENT
import java.util.Date

val GSON by lazy {
    GsonBuilder().excludeFieldsWithModifiers(TRANSIENT, STATIC)
        .registerTypeAdapter(Int::class.java, JsonDeserializer<Int> { json, _, _ ->
            if (json.isJsonNull || json.asString.isEmpty()) {
                return@JsonDeserializer null
            }
            try {
                return@JsonDeserializer json.asInt
            } catch (e: NumberFormatException) {
                return@JsonDeserializer null
            }
        }).registerTypeAdapter(Date::class.java, JsonDeserializer { json, _, _ ->
            try {
                return@JsonDeserializer Date(json.asJsonPrimitive.asString)
            } catch (e: Exception) {
                return@JsonDeserializer Date()
            }
        }).serializeNulls().create()
}

inline fun <reified T> Gson.fromJson(json: String): T? =
    this.fromJson<T>(json, object : TypeToken<T>() {}.type)

fun Any?.toJsonString(): String = GSON.toJson(this)
```

- [ ] **Step 2: Create core/FileUtil.kt**

```kotlin
package me.jbusdriver.modern.core

import android.util.Log
import java.io.File

private const val TAG = "FileUtil"

fun createDir(collectDir: String): String? {
    File(collectDir.trim()).let {
        try {
            if (!it.exists() && it.mkdirs()) return collectDir
            if (it.exists()) {
                if (it.isDirectory) {
                    return collectDir
                } else {
                    it.delete()
                    createDir(collectDir)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "createDir error", e)
        }
    }
    return null
}
```

- [ ] **Step 3: Remove Gson extensions from BaseExtension.kt**

In `core/BaseExtension.kt`, delete:
- `import com.google.gson.Gson`
- `import com.google.gson.reflect.TypeToken`
- The entire `// region Gson 扩展` section (the `fromJson` and `toJsonString` functions)

Also remove the empty `// region 屏幕尺寸` marker if present.

- [ ] **Step 4: Delete core/Global.kt**

All its contents have been moved to GsonExt.kt, FileUtil.kt, or UrlExt.kt (Task 1).

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (all imports resolve because GSON, fromJson, toJsonString, createDir stay in `me.jbusdriver.modern.core` package)

- [ ] **Step 6: Commit**

```
refactor: split Global.kt into GsonExt.kt and FileUtil.kt
```

---

### Task 3: Merge ICollectCategory into ILink.kt

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/ILink.kt`
- Delete: `app/src/main/java/me/jbusdriver/modern/domain/model/ICollectCategory.kt`

- [ ] **Step 1: Update ILink.kt**

Replace the file content with:

```kotlin
package me.jbusdriver.modern.domain.model

import java.io.Serializable

interface ILink : Serializable {
    val link: String
    var categoryId: Int
}
```

The `ICollectCategory` interface is inlined — no other file imports it directly.

- [ ] **Step 2: Delete ICollectCategory.kt**

Delete `app/src/main/java/me/jbusdriver/modern/domain/model/ICollectCategory.kt`.

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
refactor: merge ICollectCategory into ILink interface
```

---

### Task 4: Split Bean.kt into DBTypes + LinkMappers + PageLink

This is the largest task. Bean.kt's contents are split into three new files across two layers, entity conversion logic is externalized, and Bean.kt is deleted.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/db/DBTypes.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/db/LinkMappers.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/domain/model/PageLink.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/entity/History.kt` (remove getLinkItem)
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/entity/LinkItem.kt` (remove getLinkValue + doGet)
- Modify: `app/src/main/java/me/jbusdriver/modern/data/CollectRepository.kt` (update imports)
- Modify: All files importing DB type constants from `domain.model` → `data.db`
- Delete: `app/src/main/java/me/jbusdriver/modern/domain/model/Bean.kt`

- [ ] **Step 1: Create data/db/DBTypes.kt**

```kotlin
package me.jbusdriver.modern.data.db

const val MovieDBType = 1
const val ActressDBType = 2
const val HeaderDBType = 3
const val GenreDBType = 4
const val SearchLinkDBType = 5
const val PageLinkDBType = 6
```

Note: `Expand_Type_Head`, `Expand_Type_Item`, `AllDBType`, and `ILink.des` are unused and dropped.

- [ ] **Step 2: Create domain/model/PageLink.kt**

```kotlin
package me.jbusdriver.modern.domain.model

import me.jbusdriver.modern.core.http.NetClient

data class PageLink(val page: Int, val title: String, override val link: String) : ILink {
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
}

data class SearchLink(val type: SearchType, var query: String) : ILink {
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10

    override val link: String
        get() = "${NetClient.defaultFastUrl}${type.urlPathFormater.format(query)}"
}
```

- [ ] **Step 3: Create data/db/LinkMappers.kt**

Contains ILink→DB mapping extensions and entity→ILink deserialization.

```kotlin
package me.jbusdriver.modern.data.db

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.data.db.entity.History
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.*

val ILink.DBtype: Int
    inline get() = when (this) {
        is Movie -> MovieDBType
        is ActressInfo -> ActressDBType
        is Header -> HeaderDBType
        is Genre -> GenreDBType
        is SearchLink -> SearchLinkDBType
        is PageLink -> PageLinkDBType
        else -> error("$this has no matched class for DBtype")
    }

val ILink.uniqueKey: String
    inline get() = when (this) {
        is SearchLink -> query
        else -> link.urlPath
    }

fun ILink.convertDBItem() = LinkItem(
    dbType = this.DBtype,
    createTime = System.currentTimeMillis(),
    key = this.uniqueKey,
    jsonStr = this.toJsonString(),
    categoryId = when {
        this.categoryId > 0 -> categoryId
        else -> AllFirstParentDBCategoryGroup[this.DBtype]?.id ?: LinkCategory.id ?: -1
    }
)

fun History.toILink(): ILink = when (dbType) {
    MovieDBType -> GSON.fromJson<Movie>(jsonStr)!!
    ActressDBType -> GSON.fromJson<ActressInfo>(jsonStr)!!
    HeaderDBType -> GSON.fromJson<Header>(jsonStr)!!
    GenreDBType -> GSON.fromJson<Genre>(jsonStr)!!
    SearchLinkDBType -> GSON.fromJson<SearchLink>(jsonStr)!!
    PageLinkDBType -> GSON.fromJson<PageLink>(jsonStr)!!
    else -> error("$dbType : $jsonStr has no matched class")
}

fun LinkItem.toILink(): ILink? {
    return kotlin.runCatching {
        val link = deserializeLink(dbType, jsonStr)
        link.categoryId = this.categoryId
        link
    }.onFailure {
        KLog.w("error toILink : $this")
    }.getOrNull()
}

private fun deserializeLink(type: Int, jsonStr: String): ILink = when (type) {
    MovieDBType -> GSON.fromJson<Movie>(jsonStr)!!
    ActressDBType -> GSON.fromJson<ActressInfo>(jsonStr)!!
    HeaderDBType -> GSON.fromJson<Header>(jsonStr)!!
    GenreDBType -> GSON.fromJson<Genre>(jsonStr)!!
    SearchLinkDBType -> GSON.fromJson<SearchLink>(jsonStr)!!
    PageLinkDBType -> GSON.fromJson<PageLink>(jsonStr)!!
    else -> error("$type : $jsonStr has no matched class")
}
```

- [ ] **Step 4: Update entity files — remove conversion methods**

In `data/db/entity/History.kt`:
- Remove all imports of `domain.model.*` DB type constants and model classes
- Remove `core.GSON` and `core.fromJson` imports
- Remove the `getLinkItem()` method from the data class body
- Keep only: Room annotations, `data class History(...)` with fields only

In `data/db/entity/LinkItem.kt`:
- Remove all imports of `domain.model.*` DB type constants and model classes
- Remove `core.GSON`, `core.fromJson`, `KLog` imports
- Remove the `getLinkValue()` method and the `private fun doGet()` function
- Keep only: Room annotations, `data class LinkItem(...)` with fields only

- [ ] **Step 5: Update all DB type constant imports**

Replace `import me.jbusdriver.modern.domain.model.MovieDBType` → `import me.jbusdriver.modern.data.db.MovieDBType` (and same pattern for ActressDBType, GenreDBType, HeaderDBType, etc.) in these files:

- `data/CollectRepository.kt` (MovieDBType, ActressDBType)
- `ui/MainScreen.kt` (MovieDBType, ActressDBType)
- `ui/movielist/CollectionListScreen.kt` (MovieDBType, ActressDBType)
- `ui/movielist/CollectionListViewModel.kt` (MovieDBType, ActressDBType)
- `data/db/entity/History.kt` (MovieDBType, ActressDBType, HeaderDBType, GenreDBType, SearchLinkDBType, PageLinkDBType) — if any remain after Step 4
- `data/db/entity/LinkItem.kt` (MovieDBType, ActressDBType, HeaderDBType, GenreDBType, SearchLinkDBType, PageLinkDBType) — if any remain after Step 4

- [ ] **Step 6: Update conversion call sites**

In `data/CollectRepository.kt`:
- Change `it.getLinkValue()` → `it.toILink()` (2 occurrences)
- Add import: `import me.jbusdriver.modern.data.db.toILink`

- [ ] **Step 7: Delete domain/model/Bean.kt**

All contents have been distributed to DBTypes.kt, LinkMappers.kt, and PageLink.kt.

- [ ] **Step 8: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```
refactor: split Bean.kt into DBTypes, LinkMappers, PageLink; externalize entity conversion
```

---

### Task 5: Move HtmlParser.kt to data/parser/

**Files:**
- Move: `domain/model/HtmlParser.kt` → `data/parser/HtmlParser.kt`
- Modify: `data/MovieRepository.kt` (update imports)
- Modify: `data/MovieDetailRepository.kt` (update imports)
- Modify: `data/SearchRepository.kt` (update imports)

- [ ] **Step 1: Create data/parser/HtmlParser.kt**

Copy the current content of `domain/model/HtmlParser.kt`, change the package declaration to:

```kotlin
package me.jbusdriver.modern.data.parser
```

The rest of the file content stays identical. Imports of `core.http.NetClient` are now correct (data → core).

- [ ] **Step 2: Update import in data/MovieRepository.kt**

Change all `import me.jbusdriver.modern.domain.model.*` that reference parser functions:
- `import me.jbusdriver.modern.domain.model.loadMovieFromDoc` → `import me.jbusdriver.modern.data.parser.loadMovieFromDoc`
- `import me.jbusdriver.modern.domain.model.parseActressAttrs` → `import me.jbusdriver.modern.data.parser.parseActressAttrs`
- `import me.jbusdriver.modern.domain.model.parseActressList` → `import me.jbusdriver.modern.data.parser.parseActressList`
- `import me.jbusdriver.modern.domain.model.parseGenreCategories` → `import me.jbusdriver.modern.data.parser.parseGenreCategories`
- `import me.jbusdriver.modern.domain.model.parsePageInfo` → `import me.jbusdriver.modern.data.parser.parsePageInfo`

- [ ] **Step 3: Update import in data/MovieDetailRepository.kt**

Change: `import me.jbusdriver.modern.domain.model.parseMovieDetails` → `import me.jbusdriver.modern.data.parser.parseMovieDetails`

- [ ] **Step 4: Update imports in data/SearchRepository.kt**

Change:
- `import me.jbusdriver.modern.domain.model.loadMovieFromDoc` → `import me.jbusdriver.modern.data.parser.loadMovieFromDoc`
- `import me.jbusdriver.modern.domain.model.parseActressList` → `import me.jbusdriver.modern.data.parser.parseActressList`
- `import me.jbusdriver.modern.domain.model.parsePageInfo` → `import me.jbusdriver.modern.data.parser.parsePageInfo`

- [ ] **Step 5: Delete domain/model/HtmlParser.kt**

- [ ] **Step 6: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```
refactor: move HtmlParser from domain/model to data/parser
```

---

### Task 6: Move SDCardDatabaseContext to data/db/

**Files:**
- Move: `core/db/SDCardDatabaseContext.kt` → `data/db/SDCardDatabaseContext.kt`
- Modify: `data/db/DB.kt` (update import)

- [ ] **Step 1: Create data/db/SDCardDatabaseContext.kt**

Copy content from `core/db/SDCardDatabaseContext.kt`, change package to:

```kotlin
package me.jbusdriver.modern.data.db
```

- [ ] **Step 2: Update import in data/db/DB.kt**

Change: `import me.jbusdriver.modern.core.db.SDCardDatabaseContext` → `import me.jbusdriver.modern.data.db.SDCardDatabaseContext`

Since SDCardDatabaseContext is now in the same package as DB.kt, this import can be removed entirely.

- [ ] **Step 3: Delete core/db/SDCardDatabaseContext.kt and core/db/ directory**

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
refactor: move SDCardDatabaseContext from core/db to data/db
```

---

### Task 7: Move Magnet.kt to domain/model/

**Files:**
- Move: `data/magnet/Magnet.kt` → `domain/model/Magnet.kt`
- Modify: `ui/UiModels.kt` (update import)
- Modify: `ui/detail/MovieDetailViewModel.kt` (update import)

- [ ] **Step 1: Create domain/model/Magnet.kt**

Copy content from `data/magnet/Magnet.kt`, change package to:

```kotlin
package me.jbusdriver.modern.domain.model
```

Remove the now-unnecessary imports of `domain.model.ILink` and `domain.model.LinkCategory` (same package).

- [ ] **Step 2: Update import in ui/UiModels.kt**

Change: `import me.jbusdriver.modern.data.magnet.Magnet` → `import me.jbusdriver.modern.domain.model.Magnet`

- [ ] **Step 3: Update import in ui/detail/MovieDetailViewModel.kt**

Change: `import me.jbusdriver.modern.data.magnet.Magnet` → `import me.jbusdriver.modern.domain.model.Magnet`

- [ ] **Step 4: Delete data/magnet/Magnet.kt**

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
refactor: move Magnet data class from data/magnet to domain/model
```

---

## Verification

After all tasks, the dependency direction should be:

```
ui → data → domain → core
  ↘       ↘        ↘
   → domain  → core
```

No file in `domain/model/` should import from `data.*` or `core.http.*` (except `SearchLink.link` which uses `NetClient.defaultFastUrl` — domain→core is acceptable per the architecture).

### Final file count by directory:

```
core/              5 files (GsonExt, FileUtil, BaseExtension, CacheLoader, JBusManager, + FileCache)
core/http/         1 file  (NetClient)
domain/model/     10 files (ILink, Movie, MovieDetail, MoviePageResult, Magnet, PageLink, Category, SearchType, DataSourceType, UrlExt)
data/parser/       1 file  (HtmlParser)
data/db/           7 files (DB, DBTypes, LinkMappers, SDCardDatabaseContext, JBusDatabase, CollectDatabase, + dao/ + entity/)
data/magnet/       2 files (MagnetManager, IMagnetLoader, + loaders/)
data/              4 files (repositories)
data/di/           2 files (modules)
```
