# androidTest 覆盖率补充 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add connected instrumented tests that fill the Compose UI and Room persistence gaps left by the existing unit-test suite, and enable `enableAndroidTestCoverage` so device runs produce coverage data.

**Architecture:** Approach C from the spec — component-first Compose UI tests (state in, lambda capture out, no Hilt) plus in-memory Room DAO tests and a real 1→2 migration test. All tests run via `connectedDebugAndroidTest` on an emulator. No Hilt integration, no jacoco merge, no network.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room 2.8.4, JUnit4 4.13.2, `androidx.test.ext.junit` + `AndroidJUnit4`, `kotlinx-coroutines-test` (`runTest`), Compose UI test (`createComposeRule`), `androidx.room:room-testing` (`MigrationTestHelper`).

## Global Constraints

- **JDK 17 required.** `JAVA_HOME` must point at a JDK 17 (e.g. the Android Studio JBR at `%LOCALAPPDATA%\Programs\Android Studio\jbr`). All `./gradlew` commands assume this.
- **Emulator/device required for running tests.** Before any `connectedDebugAndroidTest` step, verify `adb devices` lists a device whose API ≥ `minSdk` (28). Compile-only gates use `assembleDebugAndroidTest` (no device needed).
- **Hermetic only.** No Hilt, no network, no real DataStore. Each DAO test uses a fresh in-memory DB closed in `@After`. Compose tests pass state explicitly and capture lambdas.
- **Scope boundaries.** Confine changes to the `debug` build type, the `androidTest` source set, the version catalog, and the **two minimal `private`→`internal` testability tweaks** documented in Tasks 6 and 14. Do **not** touch the `release` build type.
- **Conventions.** Package new tests to mirror the class under test. Use `context.getString(R.string.x)` for text expectations (locale-safe). Commit message prefixes follow the repo style (`test:`, `build:`, `refactor:`).
- **Spec deviations (carried into the plan):**
  1. The spec's screen-smoke target `MovieTabContent` calls `hiltViewModel()` internally and cannot be tested without Hilt. **Substitute:** `DetailContent` (Task 14), which takes pure data + callbacks, requiring a one-word `private`→`internal` bump.
  2. `COLLECT_MIGRATION_1_2` is `private` in `RoomDatabaseFactory.kt`. Task 6 bumps it to `internal` so the migration test exercises the **real** migration (single source of truth).

---

## File Structure

**Production source touched (minimal, testability only):**
- `app/src/main/java/me/jbusdriver/modern/data/db/RoomDatabaseFactory.kt` — `COLLECT_MIGRATION_1_2` visibility.
- `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt` — `DetailContent` visibility.

**Build config:**
- `app/build.gradle.kts` — `enableAndroidTestCoverage`, two `androidTestImplementation` deps.
- `gradle/libs.versions.toml` — `room-testing` entry.

**New androidTest sources (under `app/src/androidTest/java/me/jbusdriver/modern/`):**
- `test/Fixtures.kt` — entity + UI-model factories and in-memory DB builders. Consumed by all later tasks.
- `data/db/CategoryDaoTest.kt`
- `data/db/HistoryDaoTest.kt`
- `data/db/LinkItemDaoTest.kt`
- `data/db/CollectDatabaseMigrationTest.kt`
- `ui/components/StateViewsTest.kt`
- `ui/components/ErrorViewTest.kt`
- `ui/components/CollectButtonTest.kt`
- `ui/components/MovieFilterBarTest.kt`
- `ui/components/MovieListTest.kt`
- `ui/components/ActressGridTest.kt`
- `ui/detail/MagnetBottomSheetTest.kt`
- `ui/detail/DetailContentTest.kt` (screen smoke)

---

### Task 1: Build config — enable coverage + androidTest deps

**Files:**
- Modify: `gradle/libs.versions.toml` (add under `[libraries]`)
- Modify: `app/build.gradle.kts` (`defaultConfig`, `buildTypes.debug`, `dependencies`)

**Interfaces:**
- Produces: `libs.room.testing` catalog alias; `androidTestImplementation(libs.coroutines.test)`, `androidTestImplementation(libs.room.testing)` available to all later tasks.

- [ ] **Step 1: Add `room-testing` to the version catalog**

In `gradle/libs.versions.toml`, under the `[libraries]` section, after the `room-compiler` line (line ~40), add:

```toml
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

- [ ] **Step 2: Enable `enableAndroidTestCoverage` on the debug build type**

In `app/build.gradle.kts`, inside the existing `debug { ... }` block (the block that already contains `enableUnitTestCoverage = true`), add the android-test line directly after it:

```kotlin
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
```

- [ ] **Step 3: Add the two androidTest dependencies**

In `app/build.gradle.kts`, in the `dependencies { ... }` block, find the existing androidTest Compose lines:

```kotlin
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
```

Add immediately after them:

```kotlin
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.room.testing)
```

- [ ] **Step 4: Verify the build still assembles (compile gate)**

Run: `./gradlew assembleDebug assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`. This confirms the catalog entry resolves and androidTest sources still compile.

- [ ] **Step 5: Verify connected test infra + coverage on the existing test (device required)**

Ensure a device is listed (`adb devices`), then run:
`./gradlew connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`. The existing `ForumPostContentTest` runs green, and a `.ec` coverage file is produced under `app/build/outputs/androidTest-results/connected/` (or `outputs/code-coverage/`). If `connectedDebugAndroidTest` reports no connected device, start an emulator and re-run.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: enable androidTest coverage and add room-testing/coroutines-test"
```

---

### Task 2: Shared fixtures (entities, UI models, in-memory DB builders)

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/test/Fixtures.kt`

**Interfaces:**
- Produces: `aCategory(...)`, `aLinkItem(...)`, `aHistory(...)`, `aMovie(...)`, `anActress(...)`, `aMagnet(...)`, `buildCollectDb(context)`, `buildJBusDb(context)` — used by Tasks 3–14.

- [ ] **Step 1: Create the fixtures file**

Create `app/src/androidTest/java/me/jbusdriver/modern/test/Fixtures.kt` with exactly:

```kotlin
package me.jbusdriver.modern.test

import android.content.Context
import androidx.room.Room
import me.jbusdriver.modern.data.db.CollectDatabase
import me.jbusdriver.modern.data.db.JBusDatabase
import me.jbusdriver.modern.data.db.entity.Category
import me.jbusdriver.modern.data.db.entity.History
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MagnetUiModel
import me.jbusdriver.modern.ui.MovieUiModel

/** androidTest 共用夹具：DB 实体、UI 模型工厂与内存数据库构造器。 */

fun aCategory(
    id: Int = 0,
    name: String = "cat",
    tree: String = "1/",
    order: Int = 0,
    pId: Int = -1
): Category = Category(id = id, name = name, tree = tree, order = order, pId = pId)

fun aLinkItem(
    dbType: Int = 1,
    key: String = "key",
    jsonStr: String = "{}",
    categoryId: Int = -1,
    createTime: Long = 0L,
    id: Int = 0
): LinkItem = LinkItem(
    id = id,
    dbType = dbType,
    key = key,
    jsonStr = jsonStr,
    categoryId = categoryId,
    createTime = createTime
)

fun aHistory(
    dbType: Int = 1,
    jsonStr: String = "{}",
    isAll: Int = 0,
    createTime: Long = 0L,
    id: Int = 0
): History = History(id = id, dbType = dbType, jsonStr = jsonStr, isAll = isAll, createTime = createTime)

fun aMovie(
    title: String = "movie",
    code: String = "CODE-001",
    link: String = "https://test/movie/1",
    imageUrl: String = "",
    date: String = "2024-01-01"
): MovieUiModel = MovieUiModel(title = title, imageUrl = imageUrl, code = code, date = date, link = link)

fun anActress(
    name: String = "actress",
    link: String = "https://test/actress/1",
    avatar: String = ""
): ActressUiModel = ActressUiModel(name = name, avatar = avatar, link = link)

fun aMagnet(
    name: String = "magnet-name",
    size: String = "1GB",
    date: String = "2024-01-01",
    link: String = "magnet:?xt=urn:btih:1"
): MagnetUiModel = MagnetUiModel(name = name, size = size, date = date, link = link)

fun buildCollectDb(context: Context): CollectDatabase =
    Room.inMemoryDatabaseBuilder(context, CollectDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun buildJBusDb(context: Context): JBusDatabase =
    Room.inMemoryDatabaseBuilder(context, JBusDatabase::class.java)
        .allowMainThreadQueries()
        .build()
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/test/Fixtures.kt
git commit -m "test: add shared androidTest fixtures and in-memory DB builders"
```

---

### Task 3: CategoryDaoTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/data/db/CategoryDaoTest.kt`

**Interfaces:**
- Consumes: `buildCollectDb`, `aCategory`, `aCategory(id, name, tree, order)` from Task 2; `CollectDatabase.categoryDao()`.

- [ ] **Step 1: Write the test**

Create `app/src/androidTest/java/me/jbusdriver/modern/data/db/CategoryDaoTest.kt`:

```kotlin
package me.jbusdriver.modern.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.test.aCategory
import me.jbusdriver.modern.test.buildCollectDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CollectDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setup() {
        db = buildCollectDb(context)
        dao = db.categoryDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insert_then_findById() = runTest {
        val id = dao.insert(aCategory(name = "Movies", tree = "1/"))
        val found = dao.findById(id.toInt())
        assertEquals("Movies", found?.name)
    }

    @Test
    fun insert_ignores_duplicate_primary_key() = runTest {
        dao.insert(aCategory(id = 10, name = "A", tree = "1/"))
        val secondId = dao.insert(aCategory(id = 10, name = "B", tree = "1/"))
        assertEquals(-1L, secondId)
        assertEquals("A", dao.findById(10)?.name)
    }

    @Test
    fun queryTreeByLike_filters_prefix_and_orders_by_sort_order_desc() = runTest {
        dao.insertAll(
            listOf(
                aCategory(name = "c1", tree = "1/2", order = 1),
                aCategory(name = "c2", tree = "1/3", order = 5),
                aCategory(name = "other", tree = "9/1", order = 0)
            )
        )
        val result = dao.queryTreeByLike("1/%").first().map { it.name }
        assertEquals(listOf("c2", "c1"), result)
    }

    @Test
    fun update_changes_fields() = runTest {
        val id = dao.insert(aCategory(name = "old", tree = "1/")).toInt()
        dao.update(aCategory(id = id, name = "new", tree = "1/"))
        assertEquals("new", dao.findById(id)?.name)
    }

    @Test
    fun delete_removes_row() = runTest {
        val id = dao.insert(aCategory(name = "x", tree = "1/")).toInt()
        assertEquals(1, dao.delete(id))
        assertNull(dao.findById(id))
    }

    @Test
    fun queryTreeByLike_reflects_new_insert() = runTest {
        assertTrue(dao.queryTreeByLike("1/%").first().isEmpty())
        dao.insert(aCategory(name = "only", tree = "1/5"))
        assertEquals(listOf("only"), dao.queryTreeByLike("1/%").first().map { it.name })
    }
}
```

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.data.db.CategoryDaoTest`
Expected: all 6 tests PASS. A failure indicates a real DAO/query bug — investigate before proceeding.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/data/db/CategoryDaoTest.kt
git commit -m "test: cover CategoryDao CRUD, IGNORE conflict, tree prefix + ordering"
```

---

### Task 4: HistoryDaoTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/data/db/HistoryDaoTest.kt`

**Interfaces:**
- Consumes: `buildJBusDb`, `aHistory` from Task 2; `JBusDatabase.historyDao()`.

- [ ] **Step 1: Write the test**

Create `app/src/androidTest/java/me/jbusdriver/modern/data/db/HistoryDaoTest.kt`:

```kotlin
package me.jbusdriver.modern.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.test.aHistory
import me.jbusdriver.modern.test.buildJBusDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: JBusDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun setup() {
        db = buildJBusDb(context)
        dao = db.historyDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insert_and_count() = runTest {
        assertEquals(0, dao.count())
        dao.insert(aHistory(dbType = 1, jsonStr = "{}"))
        dao.insert(aHistory(dbType = 2, jsonStr = "{}"))
        assertEquals(2, dao.count())
    }

    @Test
    fun queryByLimit_orders_newest_first_and_paginates() = runTest {
        (1..5).forEach { dao.insert(aHistory(dbType = it, jsonStr = "{$it}")) }
        // ORDER BY id DESC → newest (id=5) first
        val page1 = dao.queryByLimit(size = 2, offset = 0).first().map { it.jsonStr }
        val page2 = dao.queryByLimit(size = 2, offset = 2).first().map { it.jsonStr }
        assertEquals(listOf("{5}", "{4}"), page1)
        assertEquals(listOf("{3}", "{2}"), page2)
    }

    @Test
    fun update_overwrites_fields() = runTest {
        val id = dao.insert(aHistory(dbType = 1, jsonStr = "old", isAll = 0)).toInt()
        dao.update(id = id, dbType = 2, jsonStr = "new", isAll = 1)
        val row = dao.queryByLimit(size = 1, offset = 0).first().first()
        assertEquals(2, row.dbType)
        assertEquals("new", row.jsonStr)
        assertEquals(1, row.isAll)
    }

    @Test
    fun deleteAll_then_resetAutoIncrement_restarts_ids() = runTest {
        dao.insertAll(listOf(aHistory(dbType = 1), aHistory(dbType = 2)))
        assertEquals(2, dao.count())
        dao.deleteAll()
        assertEquals(0, dao.count())
        dao.resetAutoIncrement()
        val newId = dao.insert(aHistory(dbType = 1)).toInt()
        assertEquals(1, newId)
    }

    @Test
    fun queryByLimit_reflects_deleteAll() = runTest {
        dao.insert(aHistory(dbType = 1, jsonStr = "{}"))
        assertFalse(dao.queryByLimit(size = 10, offset = 0).first().isEmpty())
        dao.deleteAll()
        assertTrue(dao.queryByLimit(size = 10, offset = 0).first().isEmpty())
    }
}
```

> **Note on `queryByLimit` semantics:** the DAO SQL is `LIMIT :offset, :size` with named params, so `queryByLimit(size = N, offset = M)` skips `M` rows then takes `N`, ordered `id DESC`. The pagination test above is written against that binding.

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.data.db.HistoryDaoTest`
Expected: all 5 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/data/db/HistoryDaoTest.kt
git commit -m "test: cover HistoryDao count, pagination, update, deleteAll + seq reset"
```

---

### Task 5: LinkItemDaoTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/data/db/LinkItemDaoTest.kt`

**Interfaces:**
- Consumes: `buildCollectDb`, `aLinkItem` from Task 2; `CollectDatabase.linkItemDao()`; `MovieDBType`/`ActressDBType` constants.

- [ ] **Step 1: Write the test**

Create `app/src/androidTest/java/me/jbusdriver/modern/data/db/LinkItemDaoTest.kt`:

```kotlin
package me.jbusdriver.modern.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.test.aLinkItem
import me.jbusdriver.modern.test.buildCollectDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkItemDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CollectDatabase
    private lateinit var dao: LinkItemDao

    @Before
    fun setup() {
        db = buildCollectDb(context)
        dao = db.linkItemDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun listByType_filters_and_orders_desc() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m1"))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m2"))
        dao.insert(aLinkItem(dbType = ActressDBType, key = "a1"))
        // ORDER BY id DESC → m2 before m1
        assertEquals(listOf("m2", "m1"), dao.listByType(MovieDBType).map { it.key })
    }

    @Test
    fun insert_ignores_duplicate_dbType_key() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "dup"))
        val second = dao.insert(aLinkItem(dbType = MovieDBType, key = "dup"))
        assertEquals(-1L, second)
        assertEquals(1, dao.hasByKey(MovieDBType, "dup"))
    }

    @Test
    fun insert_allows_same_key_under_different_dbType() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "shared"))
        val second = dao.insert(aLinkItem(dbType = ActressDBType, key = "shared"))
        assertTrue(second > 0)
    }

    @Test
    fun hasByKey_reports_presence() = runTest {
        assertEquals(0, dao.hasByKey(MovieDBType, "missing"))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "present"))
        assertTrue(dao.hasByKey(MovieDBType, "present") >= 1)
    }

    @Test
    fun queryLink_excludes_movie_and_actress_types() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "movie"))
        dao.insert(aLinkItem(dbType = ActressDBType, key = "actress"))
        dao.insert(aLinkItem(dbType = GenreDBType, key = "genre"))
        assertEquals(listOf(GenreDBType), dao.queryLink().map { it.dbType })
    }

    @Test
    fun queryByCategoryId_and_updateByCategoryId() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m1", categoryId = 5))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m2", categoryId = 5))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m3", categoryId = 9))
        assertEquals(listOf("m2", "m1"), dao.queryByCategoryId(5).map { it.key })

        val updated = dao.updateByCategoryId(categoryId = 5, dbType = MovieDBType, setId = 9)
        assertEquals(2, updated)
        assertEquals(0, dao.queryByCategoryId(5).size)
        assertEquals(3, dao.queryByCategoryId(9).size)
    }

    @Test
    fun delete_removes_specific_dbType_key() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "target"))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "keep"))
        assertEquals(1, dao.delete(MovieDBType, "target"))
        assertEquals(0, dao.hasByKey(MovieDBType, "target"))
        assertEquals(1, dao.hasByKey(MovieDBType, "keep"))
    }

    @Test
    fun listAll_reflects_insert_and_delete() = runTest {
        assertTrue(dao.listAll().first().isEmpty())
        dao.insert(aLinkItem(dbType = MovieDBType, key = "x"))
        assertEquals(listOf("x"), dao.listAll().first().map { it.key })
        dao.delete(MovieDBType, "x")
        assertTrue(dao.listAll().first().isEmpty())
    }
}
```

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.data.db.LinkItemDaoTest`
Expected: all 8 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/data/db/LinkItemDaoTest.kt
git commit -m "test: cover LinkItemDao CRUD, composite unique key, category moves"
```

---

### Task 6: CollectDatabase 1→2 migration test

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/RoomDatabaseFactory.kt:12` — visibility bump.
- Create: `app/src/androidTest/java/me/jbusdriver/modern/data/db/CollectDatabaseMigrationTest.kt`

**Interfaces:**
- Produces: `internal val COLLECT_MIGRATION_1_2` (consumed by this test; single source of truth for the migration).

- [ ] **Step 1: Expose the real migration to the test source set**

In `app/src/main/java/me/jbusdriver/modern/data/db/RoomDatabaseFactory.kt`, change the visibility of the migration constant from `private` to `internal`:

```kotlin
internal val COLLECT_MIGRATION_1_2 = object : Migration(1, 2) {
```

(The body is unchanged. `buildCollectDatabase` still references it normally.)

- [ ] **Step 2: Write the migration test**

Create `app/src/androidTest/java/me/jbusdriver/modern/data/db/CollectDatabaseMigrationTest.kt`:

```kotlin
package me.jbusdriver.modern.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证 CollectDatabase 1→2 迁移：
 * - v1 唯一索引在 `key` 单列；v2 改为 `(dbType, key)` 复合唯一索引。
 * - 迁移后：同 key 跨 dbType 允许；同 (dbType, key) 仍被拒绝。
 *
 * Schema 文件由 Room Gradle 插件的 `schemaDirectory` 导出在 `app/schemas`，
 * MigrationTestHelper（3 参构造）会通过插件注入的 schema 定位自动发现。
 */
@RunWith(AndroidJUnit4::class)
class CollectDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CollectDatabase::class.java.classLoader,
        FrameworkSQLiteOpenHelperFactory()
    )

    private val dbName = "collect-migration-test.db"

    @Test
    fun migrate1To2_allows_same_key_across_dbType_and_enforces_composite_unique() {
        // 在 v1 库写入一行 (dbType=1, key="shared")
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO t_link (categoryId, dbType, createTime, key, jsonStr) " +
                    "VALUES (-1, 1, 0, 'shared', '{}')"
            )
            close()
        }

        // 运行真实迁移并校验结果 schema 与 v2 一致
        val db = helper.runMigrationsAndValidate(dbName, 2, true, COLLECT_MIGRATION_1_2)

        // v2 复合唯一：同 key、不同 dbType 现在允许
        db.execSQL(
            "INSERT INTO t_link (categoryId, dbType, createTime, key, jsonStr) " +
                "VALUES (-1, 2, 0, 'shared', '{}')"
        )

        // 同 (dbType, key) 仍被拒绝（绕过 DAO 的 IGNORE，直接 SQL 触发约束）
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO t_link (categoryId, dbType, createTime, key, jsonStr) " +
                    "VALUES (-1, 1, 0, 'shared', '{}')"
            )
        }

        db.close()
    }
}
```

> **Schema discovery fallback (only if the test fails to find schemas):** the Room Gradle plugin normally wires the schema location for `MigrationTestHelper`. If the run errors with “schemas not found”, add inside `android { defaultConfig { ... } }` in `app/build.gradle.kts`:
> ```kotlin
> testInstrumentationRunnerArguments["room.schemaLocation"] = layout.projectDirectory.dir("schemas").asFile.path
> ```
> and re-run. This is a concrete fallback, not expected with the plugin active.

- [ ] **Step 3: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.data.db.CollectDatabaseMigrationTest`
Expected: the single test PASSES. If it fails on schema location, apply the fallback above.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/db/RoomDatabaseFactory.kt app/src/androidTest/java/me/jbusdriver/modern/data/db/CollectDatabaseMigrationTest.kt
git commit -m "test: cover CollectDatabase 1->2 migration composite unique index"
```

---

### Task 7: StateViewsTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/components/StateViewsTest.kt`

**Interfaces:**
- Consumes: `EmptyStateView(message)`, `NoMoreItemsView()` from `ui/components/StateViews.kt`; `R.string.no_more`.

- [ ] **Step 1: Write the test**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import org.junit.Rule
import org.junit.Test

class StateViewsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyState_shows_message() {
        composeRule.setContent {
            MaterialTheme { EmptyStateView(message = "no data here") }
        }
        composeRule.onNodeWithText("no data here").assertIsDisplayed()
    }

    @Test
    fun noMoreItems_shows_resource_text() {
        composeRule.setContent {
            MaterialTheme { NoMoreItemsView() }
        }
        composeRule.onNodeWithText(context.getString(R.string.no_more)).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.ui.components.StateViewsTest`
Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/ui/components/StateViewsTest.kt
git commit -m "test: cover EmptyStateView and NoMoreItemsView rendering"
```

---

### Task 8: ErrorViewTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/components/ErrorViewTest.kt`

**Interfaces:**
- Consumes: `ErrorView(message, onRetry)`; `R.string.retry`.

- [ ] **Step 1: Write the test**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ErrorViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shows_message_and_retry_invokes_callback() {
        var retried = false
        composeRule.setContent {
            MaterialTheme { ErrorView(message = "boom", onRetry = { retried = true }) }
        }
        composeRule.onNodeWithText("boom").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()
        assertTrue(retried)
    }
}
```

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.ui.components.ErrorViewTest`
Expected: 1 test PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/ui/components/ErrorViewTest.kt
git commit -m "test: cover ErrorView message render and retry callback"
```

---

### Task 9: CollectButtonTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/components/CollectButtonTest.kt`

**Interfaces:**
- Consumes: `CollectButton(isCollected, onToggle)`; `R.string.collect` (contentDescription when uncollected).

- [ ] **Step 1: Write the test**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollectButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reflects_uncollected_state_and_fires_toggle_on_click() {
        var toggles = 0
        composeRule.setContent {
            MaterialTheme {
                CollectButton(isCollected = false, onToggle = { toggles++ })
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.collect))
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, toggles)
    }
}
```

> **Note:** `CollectButton` also shows a `Toast` on click; that is harmless under instrumented tests and does not affect the assertion.

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.ui.components.CollectButtonTest`
Expected: 1 test PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/ui/components/CollectButtonTest.kt
git commit -m "test: cover CollectButton state and toggle callback"
```

---

### Task 10: MovieFilterBarTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/components/MovieFilterBarTest.kt`

**Interfaces:**
- Consumes: `MovieFilterBar(magnetCount, totalCount, showAll, onToggle)`; `R.string.magnet_count`, `R.string.all_movies_count` (format strings taking one int).

- [ ] **Step 1: Write the test**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovieFilterBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun clicking_inactive_segment_fires_toggle() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                MovieFilterBar(
                    magnetCount = 3,
                    totalCount = 10,
                    showAll = false,
                    onToggle = { toggled = true }
                )
            }
        }
        // showAll=false → "all movies" segment inactive → clicking it toggles
        composeRule.onNodeWithText(context.getString(R.string.all_movies_count, 10)).performClick()
        assertTrue(toggled)
    }

    @Test
    fun clicking_active_segment_does_not_toggle() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                MovieFilterBar(
                    magnetCount = 3,
                    totalCount = 10,
                    showAll = false,
                    onToggle = { toggled = true }
                )
            }
        }
        // showAll=false → "magnet" segment active → clicking it is a no-op
        composeRule.onNodeWithText(context.getString(R.string.magnet_count, 3)).performClick()
        assertFalse(toggled)
    }
}
```

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.ui.components.MovieFilterBarTest`
Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/ui/components/MovieFilterBarTest.kt
git commit -m "test: cover MovieFilterBar active/inactive segment toggle behavior"
```

---

### Task 11: MovieListTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/components/MovieListTest.kt`

**Interfaces:**
- Consumes: `MovieList(movies, onMovieClick)` (defaults fine; list mode renders `MovieItem` which shows `movie.title`); `aMovie` from Task 2.

- [ ] **Step 1: Write the test**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.jbusdriver.modern.test.aMovie
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MovieListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_titles_and_click_fires_onMovieClick() {
        val m1 = aMovie(title = "First Movie", code = "AAA-001")
        val m2 = aMovie(title = "Second Movie", code = "AAA-002")
        var clicked = ""
        composeRule.setContent {
            MaterialTheme {
                MovieList(movies = listOf(m1, m2), onMovieClick = { movie, _ -> clicked = movie.title })
            }
        }
        composeRule.onNodeWithText("First Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Second Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Second Movie").performClick()
        assertEquals("Second Movie", clicked)
    }
}
```

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.ui.components.MovieListTest`
Expected: 1 test PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/ui/components/MovieListTest.kt
git commit -m "test: cover MovieList rendering and item click callback"
```

---

### Task 12: ActressGridTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/components/ActressGridTest.kt`

**Interfaces:**
- Consumes: `ActressGrid(actresses, onActressClick)` (renders `ActressGridItem` showing `actress.name`); `anActress` from Task 2.

- [ ] **Step 1: Write the test**

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.jbusdriver.modern.test.anActress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActressGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_names_and_click_fires_onActressClick() {
        val a1 = anActress(name = "Alpha")
        val a2 = anActress(name = "Beta")
        var clicked = ""
        composeRule.setContent {
            MaterialTheme {
                ActressGrid(actresses = listOf(a1, a2), onActressClick = { actress, _ -> clicked = actress.name })
            }
        }
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").performClick()
        assertEquals("Beta", clicked)
    }
}
```

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.ui.components.ActressGridTest`
Expected: 1 test PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/ui/components/ActressGridTest.kt
git commit -m "test: cover ActressGrid rendering and item click callback"
```

---

### Task 13: MagnetBottomSheetTest

**Files:**
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/detail/MagnetBottomSheetTest.kt`

**Interfaces:**
- Consumes: `MagnetBottomSheet(uiState: MovieDetailUiState, onDismiss)` (internal, same package); `MovieDetailUiState(magnets = ...)`; `R.string.no_magnet`, `R.string.magnet_links`; `aMagnet` from Task 2.

- [ ] **Step 1: Write the test**

```kotlin
package me.jbusdriver.modern.ui.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import me.jbusdriver.modern.test.aMagnet
import me.jbusdriver.modern.ui.MovieDetailUiState
import org.junit.Rule
import org.junit.Test

class MagnetBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun empty_state_shows_no_magnet_notice() {
        composeRule.setContent {
            MaterialTheme { MagnetBottomSheet(uiState = MovieDetailUiState(), onDismiss = {}) }
        }
        composeRule.onNodeWithText(context.getString(R.string.no_magnet)).assertExists()
    }

    @Test
    fun list_state_shows_title_and_magnet_names() {
        val state = MovieDetailUiState(magnets = listOf(aMagnet(name = "MAG-NAME-1")))
        composeRule.setContent {
            MaterialTheme { MagnetBottomSheet(uiState = state, onDismiss = {}) }
        }
        composeRule.onNodeWithText(context.getString(R.string.magnet_links)).assertExists()
        composeRule.onNodeWithText("MAG-NAME-1").assertExists()
    }
}
```

> **Note:** assertions use `assertExists()` (not `assertIsDisplayed()`) because `ModalBottomSheet` animates on show; `assertExists` avoids animation-timing flake.

- [ ] **Step 2: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.ui.detail.MagnetBottomSheetTest`
Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/me/jbusdriver/modern/ui/detail/MagnetBottomSheetTest.kt
git commit -m "test: cover MagnetBottomSheet empty and list states"
```

---

### Task 14: DetailContent screen smoke (spec-substituted screen test)

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt:233` — visibility bump.
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/detail/DetailContentTest.kt`

**Interfaces:**
- Produces: `internal fun DetailContent(...)` (screen-level composable now reachable from androidTest).
- Consumes: `MovieDetailUiModel`, `HeaderUiModel`.

- [ ] **Step 1: Expose `DetailContent` to the test source set**

In `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`, change the signature at line ~233 from:

```kotlin
private fun DetailContent(
```

to:

```kotlin
internal fun DetailContent(
```

(Body unchanged. It remains the single detail-body implementation; only visibility changes so the screen smoke can render it with pure data, no Hilt.)

- [ ] **Step 2: Write the screen smoke test**

Create `app/src/androidTest/java/me/jbusdriver/modern/ui/detail/DetailContentTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.jbusdriver.modern.ui.HeaderUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import org.junit.Rule
import org.junit.Test

class DetailContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_title_and_code_header() {
        val detail = MovieDetailUiModel(
            title = "Smoke Title",
            content = "",
            cover = "",
            headers = listOf(HeaderUiModel("識別碼", "SMOKE-001")),
            genres = emptyList(),
            actresses = emptyList(),
            imageSamples = emptyList(),
            relatedMovies = emptyList()
        )
        composeRule.setContent {
            MaterialTheme {
                DetailContent(
                    detail = detail,
                    padding = PaddingValues(0.dp),
                    onMovieClick = { },
                    onActressClick = { },
                    onGenreClick = { },
                    onHeaderClick = { },
                    onImageClick = { _, _ -> },
                    onMagnetClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Smoke Title").assertIsDisplayed()
        composeRule.onNodeWithText("SMOKE-001").assertIsDisplayed()
    }
}
```

Add the dp import at the top of the file with the other imports:

```kotlin
import androidx.compose.ui.unit.dp
```

> **Note:** `cover = ""` means `AppAsyncImage` renders only its placeholder background (no network); the smoke asserts on the title and code header text, not the image.

- [ ] **Step 3: Compile gate**

Run: `./gradlew assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL` (confirms the visibility bump + test compile).

- [ ] **Step 4: Run the test (device required)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.jbusdriver.modern.ui.detail.DetailContentTest`
Expected: 1 test PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt app/src/androidTest/java/me/jbusdriver/modern/ui/detail/DetailContentTest.kt
git commit -m "test: add DetailContent screen smoke (bump visibility for testability)"
```

---

## Final Verification

- [ ] **Full connected suite (device required):** `./gradlew connectedDebugAndroidTest` — all new + existing tests PASS.
- [ ] **Compile/release gate:** `./gradlew assembleDebug assembleRelease` — both succeed (confirms the two visibility tweaks did not affect production builds).
- [ ] **Coverage artifact:** confirm `.ec` data is produced under `app/build/outputs/` for the connected run (from `enableAndroidTestCoverage`).
- [ ] **Docs touch-up:** add a one-line note to `AGENTS.md` Testing section that connected tests require a running emulator (`adb devices`) and are run with `./gradlew connectedDebugAndroidTest`.
