# 本地视频管理（虚拟收藏 + 清理未收藏 + 详情删除）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已实现的"番号扫描 + 封面播放"基础上，增加三个本地视频管理能力：未收藏本地视频的虚拟收藏展示、批量清理未收藏、详情页删除（含取消收藏时的多文件选择提示），共享同一套删除/快照基建。

**Architecture:** 三层。数据层给 `t_local_video` 加 4 个可空元数据列（迁移 1→2）+ DAO 新查询 + SAF 单文件删除抽象；仓库层在 `LocalVideoRepository` 上加 `deleteVideos/snapshotMetadata/observeAllGroupedByCode`，与 `rescan` 经同一 `Mutex` 串行，删除/分组的纯逻辑抽为顶层函数单测；UI 层把 `LocalVideoPickerSheet` 泛化为多模式表，详情页加溢出菜单与三选一对话框，收藏页加筛选开关 + 独立分区（复用 `MovieUiModel.isVirtual` 与现有 `onMovieClick` 导航，不新增回调链）。

**Tech Stack:** Kotlin、Jetpack Compose + Material3、Hilt、Room 2.8 (KSP)、Coroutines、SAF `DocumentsContract`、JUnit4 + kotlinx-coroutines-test。

## Global Constraints

- 包名 `me.jbusdriver.modern`；namespace `me.jbusdriver`。
- 番号在本地视频表中以**大写**归一化存储（`VideoCodeMatcher` + `observeForCode(code.trim().uppercase())`），所有匹配一律 `.uppercase()`。
- 默认 `res/values/strings.xml` 为**繁體中文**；`res/values-en/strings.xml` 为英文。计数标签用 `<plurals>`（参照既有 `local_video_linked_count`）。瞬时 Toast/按钮可用带 `%1$d` 的普通 `<string>`。
- 文件大小格式化用 `android.text.format.Formatter.formatShortFileSize(context, size)`（项目既有用法；**不要**用 `FileUtil`，它只有 `createDir`）。
- ViewModel 不向 UI 暴露回调：用 `StateFlow` 暴露状态、`SharedFlow` 暴露一次性消息。
- Room 迁移走显式 `Migration`（**禁止** `fallbackToDestructiveMigration`，用户已扫描索引不能丢）。
- 测试约定：项目只对 **ViewModel（依赖仓库接口，可伪造）** 与 **纯函数** 写 JVM 单测；不写 Room/Repository-impl/SAF 的 instrumented 测试。因此本计划的仓库删除/分组逻辑抽为**纯函数**单测，仓库编排（DAO+Mutex+SAF）靠构建 + VM 契约测试 + 手测保证。
- 质量门：每个任务结束跑 `./gradlew assembleDebug`；涉及纯函数/VM 的任务跑 `./gradlew testDebugUnitTest`。提交前再跑 `lintDebug` 与 `assembleRelease`。
- 提交时先 `git status` 确认无无关文件（`.idea/`、工具配置等）。

## Spec deviations（相对 spec 2026-07-10 的实现期精简，已审）

1. **详情页一次性事件**：spec 写 `SharedFlow<DetailOneShotEvent>` 驱动对话框。实现改为：取消收藏的"是否弹对话框"判断直接由 Screen 读 `uiState.isCollected && uiState.localVideos.isNotEmpty()` 完成（纯状态判断，非回调，符合规则）；`SharedFlow` 仅保留用于**删除结果 Toast 消息**（`UserMessage`）。更少代码、同效果。
2. **虚拟卡片数据模型**：spec 写 `LocalVideoGroup` → `UncollectedVideoUiModel`。实现复用 `MovieUiModel` 加一个 `isVirtual: Boolean` 字段，`link = code` 直接走现有 `onMovieClick` 导航，**避免**在 MainScreen→CollectCategoryScreen→CollectionListScreen 间穿透新回调。`LocalVideoGroup` 仍保留作为仓库→VM 的中间模型。
3. **仓库可测性**：spec 未提及。`LocalVideoFolderStore` 是具体类（需 Context），无法在 JVM 伪造；故把删除成败映射、分组逻辑抽为纯函数 `planDeletion` / `groupLocalVideoEntities` 直接单测，仓库编排不写 instrumented 测试。
4. **文件大小**：spec 误写"复用 FileUtil"；实际用 `Formatter.formatShortFileSize`。

## File Structure

**新增**
- `app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoFileDeleter.kt` — `LocalVideoFileDeleter` 接口 + `DeleteFileResult` 枚举 + `DocumentFileVideoFileDeleter` 实现（SAF 删除）。
- `app/src/test/java/me/jbusdriver/modern/data/repository/LocalVideoRepositoryLogicTest.kt` — 纯函数 `planDeletion` / `groupLocalVideoEntities` 单测。
- `app/src/main/java/me/jbusdriver/modern/ui/UserMessage.kt` — `data class UserMessage(resId, args)`，供各 VM 的消息 SharedFlow 复用。

**改动**
- 数据层：`LocalVideoEntity.kt`、`LocalVideoDatabase.kt`、`RoomDatabaseFactory.kt`、`LocalVideoDao.kt`、`LocalVideo.kt`、`LocalVideoRepository.kt`、`DataModule.kt`。
- 详情：`MovieDetailViewModel.kt`、`MovieDetailScreen.kt`、`ui/localvideo/LocalVideoPickerSheet.kt`（重命名为 `LocalVideoSheet`）。
- 收藏：`CollectionFilterState.kt`、`CollectionFilterSheet.kt`、`CollectionListViewModel.kt`、`CollectionListScreen.kt`、`MovieList.kt`、`MovieListItems.kt`、`UiModels.kt`。
- 测试桩：5 个 VM 测试需补 `LocalVideoRepository` 新方法。

---

# Phase 1：共享基建 + 详情删除

## Task 1: 本地视频表加 4 个可空元数据列 + 迁移 1→2

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/entity/LocalVideoEntity.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/LocalVideoDatabase.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/RoomDatabaseFactory.kt`
- Generated (commit after build): `app/schemas/me.jbusdriver.modern.data.db.LocalVideoDatabase/2.json`

**Interfaces:**
- Produces: `LocalVideoEntity` 新增 `title/imageUrl/date/censorType: String?`；DB version 2；`LOCAL_VIDEO_MIGRATION_1_2`。

- [ ] **Step 1: 改 `LocalVideoEntity.kt`**，在 `scannedAt` 后追加四列：

```kotlin
@Entity(tableName = "t_local_video", indices = [Index("code")])
data class LocalVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** 命中的番号（大写归一化）。 */
    val code: String,
    /** 显示名（文件名，去路径）。 */
    val name: String,
    /** content:// 子文档 URI（凭 tree 持久权限可读）。 */
    val uri: String,
    val mime: String?,
    val size: Long,
    @ColumnInfo(name = "scannedAt") val scannedAt: Long,
    /** 快照：用户在详情页打开过该番号后回填的标题（未看过为 null → 极简卡片）。 */
    val title: String? = null,
    /** 快照：封面 URL。 */
    val imageUrl: String? = null,
    /** 快照：发行日期。 */
    val date: String? = null,
    /** 快照：该番号上次成功打开所在的域（"UNCENSORED" 或 null），回跳详情时复用。 */
    val censorType: String? = null,
)
```

- [ ] **Step 2: 改 `LocalVideoDatabase.kt`**，version 1→2：

```kotlin
@Database(entities = [LocalVideoEntity::class], version = 2, exportSchema = true)
abstract class LocalVideoDatabase : RoomDatabase() {
    abstract fun localVideoDao(): LocalVideoDao
}
```

- [ ] **Step 3: 改 `RoomDatabaseFactory.kt`**，在 `COLLECT_MIGRATION_1_2` 之后新增迁移并注册：

```kotlin
internal val LOCAL_VIDEO_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE t_local_video ADD COLUMN title TEXT")
        db.execSQL("ALTER TABLE t_local_video ADD COLUMN imageUrl TEXT")
        db.execSQL("ALTER TABLE t_local_video ADD COLUMN date TEXT")
        db.execSQL("ALTER TABLE t_local_video ADD COLUMN censorType TEXT")
    }
}
```

并把 `buildLocalVideoDatabase` 改为：

```kotlin
fun buildLocalVideoDatabase(context: Context): LocalVideoDatabase =
    Room.databaseBuilder(
        context,
        LocalVideoDatabase::class.java,
        LOCAL_VIDEO_DB_NAME
    ).addMigrations(LOCAL_VIDEO_MIGRATION_1_2).build()
```

- [ ] **Step 4: 构建确认生成 schema 2.json**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL；`app/schemas/me.jbusdriver.modern.data.db.LocalVideoDatabase/2.json` 生成（含四列）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/db/entity/LocalVideoEntity.kt \
  app/src/main/java/me/jbusdriver/modern/data/db/LocalVideoDatabase.kt \
  app/src/main/java/me/jbusdriver/modern/data/db/RoomDatabaseFactory.kt \
  app/schemas/me.jbusdriver.modern.data.db.LocalVideoDatabase/2.json
git commit -m "feat(local-video): add snapshot columns + migration 1->2"
```

> Room 迁移属 instrumented 范畴，项目惯例不写迁移单测（参照 CollectDatabase 的 `COLLECT_MIGRATION_1_2` 亦无单测）。加列迁移由编译期 schema 校验保证。

---

## Task 2: LocalVideoDao 新增查询

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/dao/LocalVideoDao.kt`

**Interfaces:**
- Produces（供 Task 5 仓库调用）：
  - `fun observeAll(): Flow<List<LocalVideoEntity>>`
  - `suspend fun findByIds(ids: List<Int>): List<LocalVideoEntity>`
  - `suspend fun deleteByIds(ids: List<Int>)`
  - `suspend fun updateSnapshot(code: String, title: String, imageUrl: String, date: String, censorType: String?)`

- [ ] **Step 1: 在 `LocalVideoDao` 接口内、`replaceAll` 之前插入四个方法**：

```kotlin
    @Query("SELECT * FROM t_local_video")
    fun observeAll(): Flow<List<LocalVideoEntity>>

    @Query("SELECT * FROM t_local_video WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Int>): List<LocalVideoEntity>

    @Query("DELETE FROM t_local_video WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query(
        "UPDATE t_local_video SET title = :title, imageUrl = :imageUrl, " +
            "date = :date, censorType = :censorType WHERE code = :code"
    )
    suspend fun updateSnapshot(
        code: String,
        title: String,
        imageUrl: String,
        date: String,
        censorType: String?
    )
```

- [ ] **Step 2: 构建通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL（Room KSP 校验 SQL 通过）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/db/dao/LocalVideoDao.kt
git commit -m "feat(local-video): add observeAll/findByIds/deleteByIds/updateSnapshot to DAO"
```

---

## Task 3: SAF 单文件删除抽象 + DI 绑定

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoFileDeleter.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

**Interfaces:**
- Produces：`LocalVideoFileDeleter.delete(uri: String): DeleteFileResult`（Task 5 仓库依赖）。`DeleteFileResult.{SUCCESS, NOT_FOUND, FAILED}`。

- [ ] **Step 1: 新建 `LocalVideoFileDeleter.kt`**：

```kotlin
package me.jbusdriver.modern.data.localvideo

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** 单个本地视频文件的删除结果。 */
enum class DeleteFileResult { SUCCESS, NOT_FOUND, FAILED }

/** SAF 文件删除抽象。 */
interface LocalVideoFileDeleter {
    suspend fun delete(uri: String): DeleteFileResult
}

/** 基于 DocumentsContract 的删除实现，凭 tree 持久权限直接删子文档。 */
class DocumentFileVideoFileDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalVideoFileDeleter {

    override suspend fun delete(uri: String): DeleteFileResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val deleted = DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
            when {
                deleted -> DeleteFileResult.SUCCESS
                fileExists(uri) -> DeleteFileResult.FAILED
                else -> DeleteFileResult.NOT_FOUND
            }
        } catch (_: FileNotFoundException) {
            DeleteFileResult.NOT_FOUND
        } catch (_: SecurityException) {
            DeleteFileResult.FAILED
        } catch (_: Exception) {
            DeleteFileResult.FAILED
        }
    }

    private fun fileExists(uri: String): Boolean = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.close()
        true
    } catch (_: Exception) {
        false
    }
}
```

- [ ] **Step 2: 在 `DataModule.kt` 绑定**，新增 import 与 `@Binds`：

import（按字母序插入既有 localvideo import 区）：
```kotlin
import me.jbusdriver.modern.data.localvideo.DocumentFileVideoFileDeleter
import me.jbusdriver.modern.data.localvideo.LocalVideoFileDeleter
```

在 `bindLocalVideoFileSource` 之后追加：
```kotlin
    @Binds
    @Singleton
    abstract fun bindLocalVideoFileDeleter(impl: DocumentFileVideoFileDeleter): LocalVideoFileDeleter
```

- [ ] **Step 3: 构建通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoFileDeleter.kt \
  app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt
git commit -m "feat(local-video): add LocalVideoFileDeleter SAF abstraction + Hilt binding"
```

> `DocumentFileVideoFileDeleter` 依赖 Android `ContentResolver`，不写 JVM 单测；删除的成败→去留映射逻辑由纯函数 `planDeletion`（Task 5）单测覆盖，SAF 删除本身手测。

---

## Task 4: LocalVideo 域模型扩展 + LocalVideoGroup + DeleteResult

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/LocalVideo.kt`

**Interfaces:**
- Produces：`LocalVideo` 加 `id/title/imageUrl/date/censorType`（均有默认值，不破坏既有构造点）；新 `LocalVideoGroup`；新 `DeleteResult`。

- [ ] **Step 1: 改写 `LocalVideo.kt`** 为：

```kotlin
package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable

/** 一条本地视频关联记录（一个番号可有多条，对应多版本/多格式）。 */
@Immutable
data class LocalVideo(
    val code: String,
    val name: String,
    val uri: String,
    val mime: String?,
    val size: Long,
    val id: Int = 0,
    val title: String? = null,
    val imageUrl: String? = null,
    val date: String? = null,
    val censorType: String? = null,
)

/** 按番号分组的本地视频（供收藏页未收藏分区使用）。 */
data class LocalVideoGroup(
    val code: String,
    val title: String?,
    val imageUrl: String?,
    val date: String?,
    val censorType: String?,
    val files: List<LocalVideo>,
)

/** 本地视频功能在设置页的汇总展示。 */
data class LocalVideoSummary(
    val linkedCount: Int = 0,
    val lastScannedAt: Long? = null,
    val folderDisplayName: String? = null,
)

/** 批量删除结果。 */
data class DeleteResult(val deleted: Int, val failed: Int)
```

- [ ] **Step 2: 构建通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL（既有 `LocalVideo(...)` 构造点因新字段有默认值仍编译）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/domain/model/LocalVideo.kt
git commit -m "feat(local-video): extend LocalVideo with snapshot fields; add LocalVideoGroup, DeleteResult"
```

---

## Task 5: LocalVideoRepository 删除/快照/分组 + 纯函数单测（TDD）

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/repository/LocalVideoRepository.kt`
- Create: `app/src/test/java/me/jbusdriver/modern/data/repository/LocalVideoRepositoryLogicTest.kt`
- Modify（补桩）：`SearchViewModelTest.kt`、`MovieListViewModelTest.kt`、`LinkMovieListViewModelTest.kt`、`CollectionListViewModelTest.kt`、`MovieDetailViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 DAO、Task 3 `LocalVideoFileDeleter`、Task 4 `LocalVideoGroup/DeleteResult`。
- Produces（供 Task 7/11/12）：`observeAllGroupedByCode()`、`deleteVideos(ids)`、`snapshotMetadata(...)`；纯函数 `groupLocalVideoEntities(...)`、`planDeletion(...)`、`DeletionPlan`。

- [ ] **Step 1: 写失败测试 `LocalVideoRepositoryLogicTest.kt`**（纯函数，无 Android 依赖）：

```kotlin
package me.jbusdriver.modern.data.repository

import me.jbusdriver.modern.data.db.entity.LocalVideoEntity
import me.jbusdriver.modern.data.localvideo.DeleteFileResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalVideoRepositoryLogicTest {

    private fun e(id: Int, code: String, title: String? = null, image: String? = null) =
        LocalVideoEntity(id = id, code = code, name = "$code.mp4", uri = "u$id", mime = "video/mp4", size = 1L, scannedAt = 0L, title = title, imageUrl = image)

    @Test
    fun groupLocalVideoEntities_groupsByCode_picksFirstNonNullRepresentative() {
        val groups = groupLocalVideoEntities(listOf(
            e(1, "ABC", title = "T", image = "img"),
            e(2, "ABC"),
            e(3, "DEF"),
        ))
        assertEquals(2, groups.size)
        val abc = groups.first { it.code == "ABC" }
        assertEquals("T", abc.title)
        assertEquals("img", abc.imageUrl)
        assertEquals(2, abc.files.size)
    }

    @Test
    fun planDeletion_successAndNotFoundRemoved_failedKept() {
        val entities = listOf(e(1, "A"), e(2, "B"), e(3, "C"))
        val results = listOf(DeleteFileResult.SUCCESS, DeleteFileResult.FAILED, DeleteFileResult.NOT_FOUND)
        val plan = planDeletion(entities, results)
        assertEquals(listOf(1, 3), plan.removedIds)
        assertEquals(1, plan.failed)
    }

    @Test
    fun planDeletion_empty() {
        val plan = planDeletion(emptyList(), emptyList())
        assertTrue(plan.removedIds.isEmpty())
        assertEquals(0, plan.failed)
    }
}
```
（补 import：`import org.junit.Assert.assertTrue`。）

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.repository.LocalVideoRepositoryLogicTest"`
Expected: FAIL（`groupLocalVideoEntities` / `planDeletion` 未解析）。

- [ ] **Step 3: 改 `LocalVideoRepository.kt`**

(a) 接口加三方法（在 `rescan` 声明后）：
```kotlin
    fun observeAllGroupedByCode(): Flow<List<LocalVideoGroup>>
    suspend fun deleteVideos(ids: List<Int>): DeleteResult
    suspend fun snapshotMetadata(
        code: String,
        title: String,
        imageUrl: String,
        date: String,
        censorType: String?
    )
```

(b) import 补：
```kotlin
import me.jbusdriver.modern.data.localvideo.DeleteFileResult
import me.jbusdriver.modern.data.localvideo.LocalVideoFileDeleter
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import kotlinx.coroutines.flow.map
```

(c) 实现类构造（在 `fileSource` 之后加 `deleter`）：
```kotlin
class DefaultLocalVideoRepository @Inject constructor(
    private val dao: LocalVideoDao,
    private val folderStore: LocalVideoFolderStore,
    private val fileSource: LocalVideoFileSource,
    private val deleter: LocalVideoFileDeleter,
) : LocalVideoRepository {
```

(d) 实现方法（放 `rescan` 之后、`toDomain` 之前）：
```kotlin
    override fun observeAllGroupedByCode(): Flow<List<LocalVideoGroup>> =
        dao.observeAll().map { groupLocalVideoEntities(it) }

    override suspend fun deleteVideos(ids: List<Int>): DeleteResult = rescanMutex.withLock {
        if (ids.isEmpty()) return@withLock DeleteResult(0, 0)
        val entities = dao.findByIds(ids)
        val results = entities.map { deleter.delete(it.uri) }
        val plan = planDeletion(entities, results)
        if (plan.removedIds.isNotEmpty()) dao.deleteByIds(plan.removedIds)
        DeleteResult(plan.removedIds.size, plan.failed)
    }

    override suspend fun snapshotMetadata(
        code: String,
        title: String,
        imageUrl: String,
        date: String,
        censorType: String?
    ) {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank()) return
        dao.updateSnapshot(normalized, title, imageUrl, date, censorType)
    }
```

(e) 更新 `toDomain`（传 id 与新字段）：
```kotlin
    private fun LocalVideoEntity.toDomain() = LocalVideo(
        id = id,
        code = code,
        name = name,
        uri = uri,
        mime = mime,
        size = size,
        title = title,
        imageUrl = imageUrl,
        date = date,
        censorType = censorType,
    )
```

(f) 文件末尾（类外）追加两个纯函数 + 数据类：
```kotlin

/** 删除计划：应从 DB 移除的 id，以及失败计数。 */
internal data class DeletionPlan(val removedIds: List<Int>, val failed: Int)

/** 把"逐文件删除结果"折叠为"应移除的 DB 行 + 失败数"。SUCCESS/NOT_FOUND → 移除；FAILED → 保留。 */
internal fun planDeletion(
    entities: List<LocalVideoEntity>,
    results: List<DeleteFileResult>,
): DeletionPlan {
    val removed = mutableListOf<Int>()
    var failed = 0
    for ((entity, result) in entities.zip(results)) {
        if (result == DeleteFileResult.FAILED) failed++ else removed += entity.id
    }
    return DeletionPlan(removed, failed)
}

/** 按番号分组，组内取首个非空快照字段作为代表（与排序）。纯函数，便于单测。 */
internal fun groupLocalVideoEntities(entities: List<LocalVideoEntity>): List<LocalVideoGroup> =
    entities.groupBy { it.code }
        .map { (code, list) ->
            LocalVideoGroup(
                code = code,
                title = list.firstNotNullOfOrNull { it.title },
                imageUrl = list.firstNotNullOfOrNull { it.imageUrl },
                date = list.firstNotNullOfOrNull { it.date },
                censorType = list.firstNotNullOfOrNull { it.censorType },
                files = list.map { e ->
                    LocalVideo(
                        id = e.id, code = e.code, name = e.name, uri = e.uri,
                        mime = e.mime, size = e.size, title = e.title, imageUrl = e.imageUrl,
                        date = e.date, censorType = e.censorType,
                    )
                },
            )
        }
        .sortedBy { it.code }
```

- [ ] **Step 4: 给 5 个 VM 测试桩补上新方法**。每个文件里 `object : LocalVideoRepository { ... }` 的闭合 `}` 前追加：

```kotlin
        override fun observeAllGroupedByCode() = flowOf(emptyList<LocalVideoGroup>())
        override suspend fun deleteVideos(ids: List<Int>) = DeleteResult(0, 0)
        override suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?) {}
```

并为每个测试文件补 import（按需）：
```kotlin
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.DeleteResult
```
（`flowOf` 多数文件已 import；未 import 则补 `import kotlinx.coroutines.flow.flowOf`。）

涉及文件：
- `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt`
- `app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt`
- `app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt`
- `app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt`
- `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`

- [ ] **Step 5: 运行测试通过**

Run: `./gradlew testDebugUnitTest`
Expected: 全绿（含新 `LocalVideoRepositoryLogicTest` 3 例 + 既有测试无回归）。

- [ ] **Step 6: 构建通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/repository/LocalVideoRepository.kt \
  app/src/test/java/me/jbusdriver/modern/data/repository/LocalVideoRepositoryLogicTest.kt \
  app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt \
  app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt \
  app/src/test/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModelTest.kt \
  app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt \
  app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt
git commit -m "feat(local-video): repo deleteVideos/snapshotMetadata/observeAllGroupedByCode + pure-fn tests"
```

---

## Task 6: LocalVideoPickerSheet 泛化为 LocalVideoSheet（Pick / DeleteMulti）

**Files:**
- Rename: `app/src/main/java/me/jbusdriver/modern/ui/localvideo/LocalVideoPickerSheet.kt` → `LocalVideoSheet.kt`（同包 `ui/localvideo/`，仅改类型名与文件名）
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`（调用点改为 Pick 模式）
- Modify: `app/src/main/res/values/strings.xml` + `values-en/strings.xml`（详情删除相关字符串，供 Task 8 复用）

**Interfaces:**
- Produces：`LocalVideoSheetMode { Pick, DeleteMulti }`；`LocalVideoSheet(videos, mode, onPicked, onSelected, onDismiss)`。Pick 模式签名兼容旧 `LocalVideoPickerSheet` 用法。

- [ ] **Step 1: 重写文件为 `LocalVideoSheet.kt`**（保留原包名 `me.jbusdriver.modern.ui.localvideo`）：

```kotlin
package me.jbusdriver.modern.ui.localvideo

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.LocalVideo

enum class LocalVideoSheetMode { Pick, DeleteMulti }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalVideoSheet(
    videos: List<LocalVideo>,
    mode: LocalVideoSheetMode = LocalVideoSheetMode.Pick,
    onPicked: (LocalVideo) -> Unit = {},
    onSelected: (List<LocalVideo>) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<LocalVideo>() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                stringResource(if (mode == LocalVideoSheetMode.Pick) R.string.play_local_video else R.string.local_video_delete_menu),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            if (mode == LocalVideoSheetMode.DeleteMulti) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { selected.clear(); selected.addAll(videos) }) {
                        Text(stringResource(R.string.local_video_select_all))
                    }
                    TextButton(onClick = {
                        val current = selected.toSet()
                        selected.clear(); selected.addAll(videos.filter { it !in current })
                    }) { Text(stringResource(R.string.local_video_invert)) }
                }
            }

            LazyColumn(Modifier.fillMaxWidth()) {
                items(videos, key = { it.uri }) { video ->
                    val isChecked = video in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (mode) {
                                    LocalVideoSheetMode.Pick -> onPicked(video)
                                    LocalVideoSheetMode.DeleteMulti -> {
                                        if (isChecked) selected.remove(video) else selected.add(video)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mode == LocalVideoSheetMode.DeleteMulti) {
                            Checkbox(checked = isChecked, onCheckedChange = {
                                if (it) selected.add(video) else selected.remove(video)
                            })
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            video.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Formatter.formatShortFileSize(context, video.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (mode == LocalVideoSheetMode.DeleteMulti) {
                val totalSize = selected.sumOf { it.size }
                Button(
                    onClick = { onSelected(selected.toList()); selected.clear() },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (selected.isEmpty()) stringResource(R.string.local_video_delete_selected)
                        else stringResource(R.string.local_video_delete_selected_count, selected.size, totalSize)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: 改 `MovieDetailScreen.kt` 调用点**。把 import：
```kotlin
import me.jbusdriver.modern.ui.localvideo.LocalVideoPickerSheet
```
改为：
```kotlin
import me.jbusdriver.modern.ui.localvideo.LocalVideoSheet
import me.jbusdriver.modern.ui.localvideo.LocalVideoSheetMode
```
并把封面区 `LocalVideoPickerSheet(...)` 调用改为 Pick 模式：
```kotlin
                LocalVideoSheet(
                    videos = localVideos,
                    mode = LocalVideoSheetMode.Pick,
                    onPicked = {
                        showVideoPicker = false
                        launchLocalVideo(context, it)
                    },
                    onDismiss = { showVideoPicker = false },
                )
```

- [ ] **Step 3: 新增字符串**（详情删除相关，供 Task 8 复用）。在 `values/strings.xml` 的 `<!-- Local video association -->` 区块末尾（`local_video_downloaded` 之后、`</resources>` 之前）追加：

```xml
    <string name="local_video_delete_menu">刪除本地視頻</string>
    <string name="local_video_delete_selected">刪除選中</string>
    <string name="local_video_select_all">全選</string>
    <string name="local_video_invert">反選</string>
    <string name="local_video_delete_all_failed">刪除失敗，請檢查文件夾權限</string>
    <string name="local_video_delete_selected_count">刪除 %1$d 項（共 %2$s）</string>
    <plurals name="local_video_deleted_count">
        <item quantity="other">已刪除 %1$d 個</item>
    </plurals>
    <string name="local_video_delete_partial">已刪除 %1$d 個，%2$d 個失敗</string>
    <string name="local_video_uncollect_title">已取消收藏</string>
    <string name="local_video_uncollect_message">是否同時刪除本地視頻？</string>
    <string name="local_video_uncollect_keep">保留本地視頻</string>
    <plurals name="local_video_uncollect_delete_all">
        <item quantity="other">刪除全部（%1$d）</item>
    </plurals>
    <string name="local_video_uncollect_select_some">選擇部分…</string>
    <string name="local_video_delete_confirm_title">刪除本地視頻？</string>
    <plurals name="local_video_delete_confirm_message">
        <item quantity="other">將刪除 %1$d 個文件。此操作不可撤銷。</item>
    </plurals>
```

在 `values-en/strings.xml` 对应 Local video 区块追加：
```xml
    <string name="local_video_delete_menu">Delete local video</string>
    <string name="local_video_delete_selected">Delete selected</string>
    <string name="local_video_select_all">Select all</string>
    <string name="local_video_invert">Invert</string>
    <string name="local_video_delete_all_failed">Delete failed. Check folder permissions.</string>
    <string name="local_video_delete_selected_count">Delete %1$d items (%2$s)</string>
    <plurals name="local_video_deleted_count">
        <item quantity="one">Deleted %1$d</item>
        <item quantity="other">Deleted %1$d</item>
    </plurals>
    <string name="local_video_delete_partial">Deleted %1$d, %2$d failed</string>
    <string name="local_video_uncollect_title">Uncollected</string>
    <string name="local_video_uncollect_message">Also delete the local video?</string>
    <string name="local_video_uncollect_keep">Keep files</string>
    <plurals name="local_video_uncollect_delete_all">
        <item quantity="one">Delete all (%1$d)</item>
        <item quantity="other">Delete all (%1$d)</item>
    </plurals>
    <string name="local_video_uncollect_select_some">Select some…</string>
    <string name="local_video_delete_confirm_title">Delete local videos?</string>
    <plurals name="local_video_delete_confirm_message">
        <item quantity="one">Will delete %1$d file. This cannot be undone.</item>
        <item quantity="other">Will delete %1$d files. This cannot be undone.</item>
    </plurals>
```

- [ ] **Step 4: 构建通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/localvideo/LocalVideoSheet.kt \
  app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
# 若旧文件名仍存在（重命名未自动删除），一并删除：
git rm -f app/src/main/java/me/jbusdriver/modern/ui/localvideo/LocalVideoPickerSheet.kt 2>/dev/null || true
git commit -m "feat(local-video): generalize picker into LocalVideoSheet (Pick/DeleteMulti) + strings"
```

> Compose UI 不写单测（项目惯例）。Task 8 接入 DeleteMulti 后手测。

---

## Task 7: MovieDetailViewModel 快照 + 取消收藏/删除方法 + 消息（TDD）

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/ui/UserMessage.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`

**Interfaces:**
- Consumes: Task 5 `snapshotMetadata/deleteVideos`。
- Produces（供 Task 8）：`messages: SharedFlow<UserMessage>`；方法 `uncollectKeepVideos()`、`uncollectDeleteAll()`、`uncollectDeleteSelected(ids)`、`deleteLocalVideos(ids)`。

- [ ] **Step 1: 新建 `ui/UserMessage.kt`**

```kotlin
package me.jbusdriver.modern.ui

/** ViewModel 发给 UI 的一次性用户消息（Toast/Snackbar）。 */
data class UserMessage(val resId: Int, val args: List<Any> = emptyList())
```

- [ ] **Step 2: 写失败测试**（追加到 `MovieDetailViewModelTest.kt`）。先在文件顶部 import 补：
```kotlin
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.DeleteResult
```
再追加两个测试（类内末尾 `}` 之前）：

```kotlin
    @Test
    fun loadDetail_snapshotsMetadataForCode() = runTest(testDispatcher) {
        var snapped: Triple<String, String, String>? = null
        val localRepo = object : LocalVideoRepository by stubLocalVideoRepo {
            override suspend fun snapshotMetadata(
                code: String, title: String, imageUrl: String, date: String, censorType: String?
            ) { snapped = Triple(code, title, imageUrl) }
        }
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, localRepo)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals("ABC-001", snapped?.first)
        assertEquals("Test Movie", snapped?.second)
        assertEquals("http://cover.jpg", snapped?.third)
    }

    @Test
    fun uncollectDeleteAll_uncollectsAndDeletesAllLocalVideoIds() = runTest(testDispatcher) {
        val videos = listOf(
            LocalVideo(code = "ABC-001", name = "a.mp4", uri = "content://x/1", mime = null, size = 1L, id = 7),
            LocalVideo(code = "ABC-001", name = "b.mp4", uri = "content://x/2", mime = null, size = 2L, id = 9),
        )
        var deletedIds: List<Int>? = null
        val localRepo = object : LocalVideoRepository by stubLocalVideoRepo {
            override fun observeForCode(code: String) = flowOf(videos)
            override suspend fun deleteVideos(ids: List<Int>): DeleteResult {
                deletedIds = ids
                return DeleteResult(ids.size, 0)
            }
        }
        val collectRepo = object : CollectRepository by stubCollectRepo {
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = false // 模拟取消收藏
        }
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, collectRepo, stubMagnetRepo, localRepo)
        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        viewModel.uncollectDeleteAll()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isCollected)
        assertEquals(listOf(7, 9), deletedIds)
    }
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest"`
Expected: FAIL（`snapshotMetadata` 未被调用 / `uncollectDeleteAll` 不存在）。

- [ ] **Step 4: 改 `MovieDetailViewModel.kt`**

import 补：
```kotlin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.ui.UserMessage
```

类内 `_uiState` 之后加消息流：
```kotlin
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()
```

在 `loadDetail` 中、`loadLocalVideos(code)` 之后插入快照（fire-and-forget，失败不影响详情）：
```kotlin
                loadLocalVideos(code)
                // 看过即补全：把标题/封面/日期回填到本地视频表
                viewModelScope.launch {
                    runCatching {
                        localVideoRepository.snapshotMetadata(
                            code = movie.code,
                            title = movie.title,
                            imageUrl = movie.imageUrl,
                            date = movie.date,
                            censorType = censorType,
                        )
                    }
                }
```

保留原 `toggleCollect()` 不变，在其后追加：
```kotlin
    /** 取消收藏并保留本地视频。 */
    fun uncollectKeepVideos() = doUncollect(deleteIds = null)

    /** 取消收藏并删除该番号全部本地视频。 */
    fun uncollectDeleteAll() {
        val ids = _uiState.value.localVideos.map { it.id }
        doUncollect(deleteIds = ids)
    }

    /** 取消收藏并删除选中的本地视频。 */
    fun uncollectDeleteSelected(ids: List<Int>) = doUncollect(deleteIds = ids)

    /** 不改动收藏状态，仅删除指定本地视频（详情页溢出菜单入口）。 */
    fun deleteLocalVideos(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            emitDeleteResult(localVideoRepository.deleteVideos(ids))
        }
    }

    private fun doUncollect(deleteIds: List<Int>?) {
        val detail = _uiState.value.movieDetail ?: return
        viewModelScope.launch {
            val movie = detail.toCollectionMovie(currentUrl)
            val categoryId = if (censorType == "UNCENSORED") UncensoredMovieCategory.id ?: 3 else null
            val newState = collectRepository.toggleMovieCollect(movie, categoryId)
            _uiState.update { it.copy(isCollected = newState) }
            if (deleteIds != null && !newState) {
                emitDeleteResult(localVideoRepository.deleteVideos(deleteIds))
            }
        }
    }

    private suspend fun emitDeleteResult(result: DeleteResult) {
        when {
            result.deleted > 0 && result.failed > 0 ->
                _messages.emit(UserMessage(R.string.local_video_delete_partial, listOf(result.deleted, result.failed)))
            result.deleted > 0 ->
                _messages.emit(UserMessage(R.string.local_video_deleted_count, listOf(result.deleted)))
            result.failed > 0 ->
                _messages.emit(UserMessage(R.string.local_video_delete_all_failed))
        }
    }
```

- [ ] **Step 5: 运行测试通过**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest"`
Expected: 全绿（含 2 个新测试 + 既有无回归）。

- [ ] **Step 6: 全量构建**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/UserMessage.kt \
  app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt \
  app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt
git commit -m "feat(local-video): detail VM snapshot-on-view + uncollect/delete + messages"
```

---

## Task 8: 详情页溢出菜单 + 取消收藏三选一 + 删除表 + Toast

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`

**Interfaces:**
- Consumes: Task 6 `LocalVideoSheet(DeleteMulti)`、Task 7 VM 方法与 `messages`、Task 6 字符串。

- [ ] **Step 1: 顶部 import 补充**（按需，勿与既有重复）

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
```

- [ ] **Step 2: 在 `MovieDetailScreen` 中 `val uiState by ...` 之后增加状态与消息收集**

```kotlin
    val context = LocalContext.current
    var showDeleteSheet by remember { mutableStateOf(false) }
    var showUncollectDialog by remember { mutableStateOf(false) }
    /** -1 = 多选表来自"取消收藏-选择部分"；>=1 = 待确认删除的文件数；0 = 无 */
    var pendingDeleteCount by remember { mutableStateOf(0) }
    var pendingDeleteIds by remember { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            val text = if (msg.args.isEmpty()) {
                context.getString(msg.resId)
            } else {
                val q = (msg.args.firstOrNull() as? Number)?.toInt() ?: 1
                context.resources.getQuantityString(msg.resId, q, *msg.args.toTypedArray())
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
```
> 说明：所有删除结果消息都走 plurals（`local_video_deleted_count` / `local_video_delete_partial`）或无参字符串（`local_video_delete_all_failed`）。`local_video_delete_partial` 含两参但仍是 plurals（quantity=deleted），`getQuantityString(resId, q, *args)` 把 `%1$d/%2$d` 一并填入。

- [ ] **Step 3: 改 `actions` 块**。在现有 `CollectButton(...)` 之后、`actions` 闭合 `}` 之前插入溢出菜单（仅当有本地视频）：

```kotlin
                        if (uiState.localVideos.isNotEmpty()) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        painterResource(R.drawable.more_vert_24px),
                                        contentDescription = stringResource(R.string.local_video_delete_menu)
                                    )
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.local_video_delete_menu)) },
                                        onClick = {
                                            menuExpanded = false
                                            val videos = uiState.localVideos
                                            if (videos.size == 1) {
                                                pendingDeleteIds = listOf(videos.first().id)
                                                pendingDeleteCount = 1
                                            } else {
                                                pendingDeleteCount = 0
                                                showDeleteSheet = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
```

- [ ] **Step 4: 改 `CollectButton` 的 `onToggle`**，拦截"已收藏且有本地视频"的取消收藏：

```kotlin
                        CollectButton(
                            isCollected = uiState.isCollected,
                            onToggle = {
                                if (uiState.isCollected && uiState.localVideos.isNotEmpty()) {
                                    showUncollectDialog = true
                                } else {
                                    viewModel.toggleCollect()
                                }
                            }
                        )
```

- [ ] **Step 5: 在 `MovieDetailScreen` 末尾（既有 `if (showMagnetSheet) {...}` 之后）追加三个对话框/表**

```kotlin
    // 删除确认（单文件，或多选表回填后）
    if (pendingDeleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = emptyList(); pendingDeleteCount = 0 },
            title = { Text(stringResource(R.string.local_video_delete_confirm_title)) },
            text = {
                Text(context.resources.getQuantityString(
                    R.string.local_video_delete_confirm_message, pendingDeleteIds.size, pendingDeleteIds.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLocalVideos(pendingDeleteIds)
                    pendingDeleteIds = emptyList(); pendingDeleteCount = 0
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = emptyList(); pendingDeleteCount = 0 }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 取消收藏三选一：保留 / 删除全部(N) / 选择部分…（仅 N>1 时出现"选择部分"）
    if (showUncollectDialog) {
        val videoCount = uiState.localVideos.size
        AlertDialog(
            onDismissRequest = { showUncollectDialog = false }, // 取消 = 保持收藏
            title = { Text(stringResource(R.string.local_video_uncollect_title)) },
            text = { Text(stringResource(R.string.local_video_uncollect_message)) },
            confirmButton = {
                Row {
                    if (videoCount > 1) {
                        TextButton(onClick = {
                            showUncollectDialog = false
                            pendingDeleteCount = -1
                            showDeleteSheet = true
                        }) { Text(stringResource(R.string.local_video_uncollect_select_some)) }
                    }
                    TextButton(onClick = {
                        showUncollectDialog = false
                        viewModel.uncollectDeleteAll()
                    }) {
                        Text(context.resources.getQuantityString(
                            R.string.local_video_uncollect_delete_all, videoCount, videoCount))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUncollectDialog = false
                    viewModel.uncollectKeepVideos()
                }) { Text(stringResource(R.string.local_video_uncollect_keep)) }
            }
        )
    }

    // 多选删除表（来自溢出菜单的多文件，或来自"取消收藏-选择部分"）
    if (showDeleteSheet) {
        val fromUncollectPartial = pendingDeleteCount == -1
        LocalVideoSheet(
            videos = uiState.localVideos,
            mode = LocalVideoSheetMode.DeleteMulti,
            onSelected = { picked ->
                showDeleteSheet = false
                val ids = picked.map { it.id }
                if (fromUncollectPartial) {
                    viewModel.uncollectDeleteSelected(ids)
                    pendingDeleteCount = 0
                } else if (ids.isNotEmpty()) {
                    pendingDeleteIds = ids
                    pendingDeleteCount = ids.size
                    // 触发上面的删除确认对话框
                }
            },
            onDismiss = { showDeleteSheet = false; pendingDeleteCount = 0 },
        )
    }
```

- [ ] **Step 6: 补 `confirm` 字符串**。`cancel`（取消）已存在于 `values/strings.xml`，直接复用 `R.string.cancel`；`confirm` 尚不存在，新增（values 繁体 + values-en）：
```xml
<string name="confirm">確定</string>
```
en：
```xml
<string name="confirm">Confirm</string>
```

- [ ] **Step 7: 构建通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 8: 手测（真机/模拟器）**

- Lab 设置选一个含某番号 ≥2 个文件的文件夹并扫描。
- 详情页 AppBar 出现 `⋮`；点"刪除本地視頻"→多选表→选 1 项→确认→Toast"已刪除 1 個"。
- 已收藏影片点♥：弹三选一→"保留"仅取消收藏；"刪除全部(N)"取消收藏并删全部；"選擇部分"开多选表→选若干→取消收藏并删选中。

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(local-video): detail overflow delete + uncollect 3-way dialog + multi-select sheet"
```

> Phase 1 完成，可在此处发布一个可用的"详情删除"版本。

---

# Phase 2：虚拟收藏

## Task 9: MovieUiModel.isVirtual + 虚线卡片

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieListItems.kt`

**Interfaces:**
- Produces：`MovieUiModel.isVirtual: Boolean = false`；`MovieItem/MovieGridItem` 的 `isVirtual` 参数 + 虚线绘制。

- [ ] **Step 1: `UiModels.kt` 给 `MovieUiModel` 加字段**（`isVirtual` 默认 false，不破坏既有构造）：

```kotlin
@Immutable
data class MovieUiModel(
    val title: String,
    val imageUrl: String,
    val code: String,
    val date: String,
    val link: String,
    val tags: List<String> = emptyList(),
    val createTime: Long = 0L,
    val categoryId: Int = 1,
    val isVirtual: Boolean = false,
)
```

- [ ] **Step 2: `MovieListItems.kt` 顶部加虚线绘制 Modifier**（import `androidx.compose.ui.graphics.PathEffect`、`androidx.compose.ui.graphics.drawscope.Stroke`、`androidx.compose.ui.geometry.CornerRadius`、`androidx.compose.ui.draw.drawWithContent`）：

```kotlin
private fun Modifier.dashedVirtualBorder(color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)),
        cornerRadius = CornerRadius(12f, 12f),
    )
}
```

- [ ] **Step 3: `MovieItem` 与 `MovieGridItem` 加 `isVirtual: Boolean = false` 参数**，并在根 `Card` 的 `modifier` 上条件应用：

`MovieItem`：
```kotlin
fun MovieItem(
    movie: MovieUiModel,
    onClick: (MovieUiModel) -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false,
    isVirtual: Boolean = false,
) {
    Card(
        onClick = { onClick(movie) },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .let { if (isVirtual) it.dashedVirtualBorder(MaterialTheme.colorScheme.outline) else it },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { /* 原内容不变 */ }
```
`MovieGridItem` 同理加 `isVirtual` 参数与同样的 `.let { ... }`（虚线 cornerRadius 与 `RoundedCornerShape(8.dp)` 协调即可）。

- [ ] **Step 4: 构建通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt \
  app/src/main/java/me/jbusdriver/modern/ui/components/MovieListItems.kt
git commit -m "feat(local-video): MovieUiModel.isVirtual + dashed card variant"
```

---

## Task 10: 收藏筛选开关 showUncollectedLocal

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterState.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt`
- Modify: `app/src/main/res/values/strings.xml` + `values-en/strings.xml`

**Interfaces:**
- Produces：`CollectionFilterState.showUncollectedLocal`，计入 `hasActiveFilters`/`activeFilterCount`；FilterSheet 影片 tab 增开关 chip。

- [ ] **Step 1: `CollectionFilterState.kt`** 加字段并更新两个统计：

```kotlin
data class CollectionFilterState(
    val censorFilter: CensorFilter = CensorFilter.ALL,
    val publishYear: Int? = null,
    val publishMonth: Int? = null,
    val collectYear: Int? = null,
    val collectMonth: Int? = null,
    val onlyDownloaded: Boolean = false,
    val showUncollectedLocal: Boolean = false,
    val sortOption: SortOption = SortOption.COLLECT_DESC
) {
    val hasActiveFilters: Boolean
        get() = censorFilter != CensorFilter.ALL
                || publishYear != null
                || publishMonth != null
                || collectYear != null
                || collectMonth != null
                || onlyDownloaded
                || showUncollectedLocal

    val activeFilterCount: Int
        get() = listOf(
            censorFilter != CensorFilter.ALL,
            publishYear != null,
            publishMonth != null,
            collectYear != null,
            collectMonth != null,
            onlyDownloaded,
            showUncollectedLocal
        ).count { it }
}
```

- [ ] **Step 2: `CollectionFilterSheet.kt`** 在既有 All/Downloaded 两个 FilterChip 的 FlowRow 里追加第三个 chip：

```kotlin
                        FilterChip(
                            selected = filterState.showUncollectedLocal,
                            onClick = { onFilterChange(filterState.copy(showUncollectedLocal = !filterState.showUncollectedLocal)) },
                            label = { Text(stringResource(R.string.local_video_show_uncollected), fontSize = 12.sp) }
                        )
```

- [ ] **Step 3: 新增字符串**（values 繁体 + values-en）：
```xml
<string name="local_video_show_uncollected">顯示未收藏的本地視頻</string>
```
en：
```xml
<string name="local_video_show_uncollected">Show uncollected local videos</string>
```

- [ ] **Step 4: 构建通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterState.kt \
  app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(local-video): collection filter toggle for uncollected local videos"
```

---

## Task 11: 收藏页未收藏分区数据 + 渲染（TDD）

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt`

**Interfaces:**
- Consumes: Task 5 `observeAllGroupedByCode()`、Task 9 `MovieUiModel.isVirtual`、Task 10 `showUncollectedLocal`。
- Produces：`CollectionListUiState.uncollectedVideos: List<MovieUiModel>`（每个 `isVirtual=true, link=code`）。

- [ ] **Step 1: 写失败测试**（追加到 `CollectionListViewModelTest.kt`，类内末尾）。先 import：
```kotlin
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.LocalVideo
import org.junit.Assert.assertTrue
```
追加：
```kotlin
    @Test
    fun showUncollectedLocal_listsCodesNotInCollection() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = listOf(Movie("Collected", "i", "ABC-001", "2024-01-01", "link1"))
            override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                if (dbType == MovieDBType) listOf(Movie("Collected", "i", "ABC-001", "2024-01-01", "link1").toLinkItem()) else emptyList()
            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        val localRepo = object : LocalVideoRepository {
            override fun observeForCode(code: String) = flowOf(emptyList<LocalVideo>())
            override fun observeDownloadedCodes() = flowOf(emptySet<String>())
            override fun observeSummary() = flowOf(LocalVideoSummary())
            override fun hasFolder() = flowOf(true)
            override suspend fun setFolder(uri: android.net.Uri) {}
            override suspend fun clearFolder() {}
            override suspend fun rescan() = 0
            override fun observeAllGroupedByCode() = flowOf(
                listOf(
                    LocalVideoGroup("ABC-001", null, null, null, null, emptyList()), // 已收藏 → 不出现
                    LocalVideoGroup("DEF-002", "DEF Title", "http://def", null, null, emptyList()), // 未收藏 → 出现
                )
            )
            override suspend fun deleteVideos(ids: List<Int>) = DeleteResult(0, 0)
            override suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?) {}
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), localRepo)

        viewModel.loadCollection(MovieDBType)
        advanceUntilIdle(); Thread.sleep(500); advanceUntilIdle()
        viewModel.updateFilter(CollectionFilterState(showUncollectedLocal = true))
        advanceUntilIdle()

        val uncollected = viewModel.uiState.value.uncollectedVideos
        assertEquals(1, uncollected.size)
        assertEquals("DEF-002", uncollected.first().code)
        assertTrue(uncollected.first().isVirtual)
        assertEquals("DEF Title", uncollected.first().title)
        // movieCount 仅含已收藏，未被污染
        assertEquals(1, viewModel.uiState.value.movieCount)
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.CollectionListViewModelTest"`
Expected: FAIL（`uncollectedVideos` 字段不存在）。

- [ ] **Step 3: 改 `CollectionListViewModel.kt`**

`CollectionListUiState` 加字段：
```kotlin
    val uncollectedVideos: List<MovieUiModel> = emptyList(),
```

import：
```kotlin
import me.jbusdriver.modern.domain.model.LocalVideoGroup
```

类内加字段：
```kotlin
    /** 按番号分组的全部本地视频（来自 localVideoRepository）。 */
    private var allLocalVideoGroups: List<LocalVideoGroup> = emptyList()
```

在 `init { ... }` 块内、既有 `downloadedCodes.collect { ... }` 之后追加：
```kotlin
        viewModelScope.launch {
            localVideoRepository.observeAllGroupedByCode().collect { groups ->
                allLocalVideoGroups = groups
                applyFilterAndSort()
            }
        }
```

在 `applyFilterAndSort()` 中、`_uiState.update { it.copy(...) }` 之前计算未收藏，并在该 `copy(...)` 内补 `uncollectedVideos = uncollectedVideos`：
```kotlin
        val showUncollected = filter.showUncollectedLocal && currentDbType == MovieDBType
        val collectedCodes = allMovies.map { it.code.uppercase() }.toSet()
        val uncollectedVideos = if (showUncollected) {
            allLocalVideoGroups
                .filter { it.code.uppercase() !in collectedCodes }
                .map { g ->
                    MovieUiModel(
                        title = g.title ?: g.code,
                        imageUrl = g.imageUrl.orEmpty(),
                        code = g.code,
                        date = g.date.orEmpty(),
                        link = g.code, // 番号即 URL 路径，走现有 onMovieClick 导航
                        isVirtual = true,
                    )
                }
        } else emptyList()
```
（在下方 `_uiState.update { it.copy( ... ) }` 中加入 `uncollectedVideos = uncollectedVideos,`。）

- [ ] **Step 4: 给 `MovieList` 加 `footer` 参数**

签名（`header` 旁）：
```kotlin
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
```
列表分支（LazyColumn）在末尾 `!hasMore` 块之后、`LazyColumn` 闭合之前：
```kotlin
                if (footer != null) { item { footer() } }
```
网格分支对应位置加全 span：
```kotlin
                if (footer != null) { item(span = { GridItemSpan(maxLineSpan) }) { footer() } }
```

- [ ] **Step 5: 改 `CollectionListScreen.kt` 渲染未收藏分区**。影片分支的 `MovieList(...)` 调用加 `footer`：

```kotlin
                MovieList(
                    movies = uiState.movies,
                    onMovieClick = onMovieClick,
                    isCollected = { true },
                    onToggleCollect = { viewModel.removeMovie(it) },
                    isDownloaded = { it.code.uppercase() in downloadedCodes },
                    isGrid = isGrid,
                    footer = {
                        if (uiState.filterState.showUncollectedLocal && uiState.uncollectedVideos.isNotEmpty()) {
                            UncollectedLocalVideoSection(
                                videos = uiState.uncollectedVideos,
                                onMovieClick = onMovieClick,
                            )
                        }
                    },
                    modifier = modifier
                )
```

文件末尾新增分区 Composable（`onMovieClick: (MovieUiModel, String?) -> Unit`，censorType 传 null）：

```kotlin
@Composable
private fun UncollectedLocalVideoSection(
    videos: List<MovieUiModel>,
    onMovieClick: (MovieUiModel, String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            stringResource(R.string.local_video_show_uncollected),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        videos.forEach { v ->
            MovieItem(
                movie = v,
                onClick = { onMovieClick(v, null) },
                isVirtual = true,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
```
补 import：`androidx.compose.foundation.layout.Column`、`androidx.compose.ui.unit.dp`、`me.jbusdriver.modern.ui.components.MovieItem`、`me.jbusdriver.R`。

- [ ] **Step 6: 运行测试通过 + 构建**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.CollectionListViewModelTest"`
Expected: 全绿。
Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: 手测**

收藏页影片 tab → 筛选 → 开"顯示未收藏的本地視頻" → 底部出现虚线卡片（未看过的为纯色块+虚线；在详情页打开过该番号后回到收藏页，封面出现）。点击 → 进入详情页。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt \
  app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt \
  app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt \
  app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt
git commit -m "feat(local-video): collection page uncollected local video section"
```

---

# Phase 3：清理未收藏

## Task 12: 分区多选删除（TDD）

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt`
- Modify: `app/src/main/res/values/strings.xml` + `values-en/strings.xml`

**Interfaces:**
- Consumes: Task 5 `deleteVideos`、Task 11 `allLocalVideoGroups`/`uncollectedVideos`、Task 7 `UserMessage`。
- Produces：`CollectionListUiState.uncollectedSelection/uncollectedInSelectionMode`、`messages: SharedFlow<UserMessage>`；VM 方法 `enterUncollectedSelection/toggleUncollectedSelected/selectAllUncollected/exitUncollectedSelection/deleteSelectedUncollected`。

- [ ] **Step 1: 写失败测试**（追加到 `CollectionListViewModelTest.kt`）：

```kotlin
    @Test
    fun deleteSelectedUncollected_deletesAllFilesOfSelectedCodes() = runTest(testDispatcher) {
        val videos = listOf(
            LocalVideo(code = "DEF-002", name = "a.mp4", uri = "u1", mime = null, size = 1L, id = 11),
            LocalVideo(code = "DEF-002", name = "b.mp4", uri = "u2", mime = null, size = 2L, id = 12),
            LocalVideo(code = "GHI-003", name = "c.mp4", uri = "u3", mime = null, size = 3L, id = 13),
        )
        var deletedIds: List<Int>? = null
        val localRepo = object : LocalVideoRepository {
            override fun observeForCode(code: String) = flowOf(emptyList<LocalVideo>())
            override fun observeDownloadedCodes() = flowOf(emptySet<String>())
            override fun observeSummary() = flowOf(LocalVideoSummary())
            override fun hasFolder() = flowOf(true)
            override suspend fun setFolder(uri: android.net.Uri) {}
            override suspend fun clearFolder() {}
            override suspend fun rescan() = 0
            override fun observeAllGroupedByCode() = flowOf(
                listOf(
                    LocalVideoGroup("DEF-002", null, null, null, null, listOf(videos[0], videos[1])),
                    LocalVideoGroup("GHI-003", null, null, null, null, listOf(videos[2])),
                )
            )
            override suspend fun deleteVideos(ids: List<Int>): DeleteResult { deletedIds = ids; return DeleteResult(ids.size, 0) }
            override suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?) {}
        }
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = emptyList<Movie>()
            override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
            override suspend fun getCollectedLinkItems(dbType: Int) = emptyList<LinkItem>()
            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), localRepo)
        viewModel.loadCollection(MovieDBType)
        advanceUntilIdle(); Thread.sleep(500); advanceUntilIdle()
        viewModel.updateFilter(CollectionFilterState(showUncollectedLocal = true))
        advanceUntilIdle()

        viewModel.toggleUncollectedSelected("DEF-002")
        viewModel.deleteSelectedUncollected()
        advanceUntilIdle()

        assertEquals(setOf(11, 12), deletedIds?.toSet())
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.CollectionListViewModelTest"`
Expected: FAIL（方法/字段不存在）。

- [ ] **Step 3: 改 `CollectionListViewModel.kt`**

`CollectionListUiState` 加字段：
```kotlin
    val uncollectedInSelectionMode: Boolean = false,
    val uncollectedSelection: Set<String> = emptySet(),
```

import：
```kotlin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.jbusdriver.modern.ui.UserMessage
import me.jbusdriver.R
```

类内加消息流：
```kotlin
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()
```

加方法（放 `removeMovie` 附近）：
```kotlin
    fun enterUncollectedSelection() {
        _uiState.update { it.copy(uncollectedInSelectionMode = true) }
    }

    fun exitUncollectedSelection() {
        _uiState.update { it.copy(uncollectedInSelectionMode = false, uncollectedSelection = emptySet()) }
    }

    fun toggleUncollectedSelected(code: String) {
        _uiState.update { s ->
            val sel = if (code in s.uncollectedSelection) s.uncollectedSelection - code else s.uncollectedSelection + code
            s.copy(uncollectedSelection = sel)
        }
    }

    fun selectAllUncollected() {
        _uiState.update { it.copy(uncollectedSelection = it.uncollectedVideos.map { v -> v.code }.toSet()) }
    }

    fun deleteSelectedUncollected() {
        val selectedUpper = _uiState.value.uncollectedSelection.map { it.uppercase() }.toSet()
        if (selectedUpper.isEmpty()) return
        val ids = allLocalVideoGroups
            .filter { it.code.uppercase() in selectedUpper }
            .flatMap { it.files.map { f -> f.id } }
        viewModelScope.launch {
            val result = localVideoRepository.deleteVideos(ids)
            _uiState.update { it.copy(uncollectedInSelectionMode = false, uncollectedSelection = emptySet()) }
            when {
                result.deleted > 0 && result.failed > 0 ->
                    _messages.emit(UserMessage(R.string.local_video_delete_partial, listOf(result.deleted, result.failed)))
                result.deleted > 0 ->
                    _messages.emit(UserMessage(R.string.local_video_deleted_count, listOf(result.deleted)))
                result.failed > 0 ->
                    _messages.emit(UserMessage(R.string.local_video_delete_all_failed))
            }
        }
    }
```

- [ ] **Step 4: 改 `CollectionListScreen.kt`**。顶层收集消息 → Toast（仿 Task 8 的 `LaunchedEffect`），并把 `UncollectedLocalVideoSection` 扩展为支持选择模式。Section 头："清理"按钮进入选择；选择中显示"全選"+ "刪除選中"；点"刪除選中"弹 Compose 确认对话框。

```kotlin
@Composable
private fun UncollectedLocalVideoSection(
    videos: List<MovieUiModel>,
    inSelectionMode: Boolean,
    selection: Set<String>,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    confirmMessage: String,
    onConfirmDelete: () -> Unit,
    onMovieClick: (MovieUiModel, String?) -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.local_video_delete_confirm_title)) },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onConfirmDelete() }) {
                    Text(stringResource(R.string.local_video_delete_selected))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.local_video_show_uncollected),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!inSelectionMode) {
                TextButton(onClick = onEnterSelection) { Text(stringResource(R.string.local_video_cleanup)) }
            } else {
                Row {
                    TextButton(onClick = onSelectAll) { Text(stringResource(R.string.local_video_select_all)) }
                    TextButton(onClick = onExitSelection) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = { if (selection.isNotEmpty()) showConfirm = true },
                        enabled = selection.isNotEmpty(),
                    ) { Text(stringResource(R.string.local_video_delete_selected)) }
                }
            }
        }
        videos.forEach { v ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inSelectionMode) {
                    Checkbox(checked = v.code in selection, onCheckedChange = { onToggleSelected(v.code) })
                }
                Box(Modifier.weight(1f)) {
                    MovieItem(
                        movie = v,
                        onClick = {
                            if (inSelectionMode) onToggleSelected(v.code) else onMovieClick(v, null)
                        },
                        isVirtual = true,
                    )
                }
            }
        }
    }
}
```

> 上面这段 `UncollectedLocalVideoSection` **替换** Task 11 中定义的同名只读版（签名从 `(videos, onMovieClick)` 扩展为带选择模式）。

在 `CollectionListScreen` 顶层（`val uiState by ...` 附近）取 context 并收集消息：
```kotlin
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            val text = if (msg.args.isEmpty()) context.getString(msg.resId)
            else {
                val q = (msg.args.firstOrNull() as? Number)?.toInt() ?: 1
                context.resources.getQuantityString(msg.resId, q, *msg.args.toTypedArray())
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
```
并把 Task 11 在 `MovieList(...)` 传入的 `footer`（只读分区版）替换为支持选择模式的调用：
```kotlin
                    footer = {
                        if (uiState.filterState.showUncollectedLocal && uiState.uncollectedVideos.isNotEmpty()) {
                            UncollectedLocalVideoSection(
                                videos = uiState.uncollectedVideos,
                                inSelectionMode = uiState.uncollectedInSelectionMode,
                                selection = uiState.uncollectedSelection,
                                confirmMessage = context.resources.getQuantityString(
                                    R.string.local_video_delete_confirm_message,
                                    uiState.uncollectedSelection.size,
                                    uiState.uncollectedSelection.size,
                                ),
                                onEnterSelection = viewModel::enterUncollectedSelection,
                                onExitSelection = viewModel::exitUncollectedSelection,
                                onToggleSelected = viewModel::toggleUncollectedSelected,
                                onSelectAll = viewModel::selectAllUncollected,
                                onConfirmDelete = viewModel::deleteSelectedUncollected,
                                onMovieClick = onMovieClick,
                            )
                        }
                    },
```

补 import：`androidx.compose.foundation.layout.Arrangement`、`androidx.compose.foundation.layout.Box`、`androidx.compose.foundation.layout.Column`、`androidx.compose.foundation.layout.Row`、`androidx.compose.material3.AlertDialog`、`androidx.compose.material3.Checkbox`、`androidx.compose.material3.TextButton`、`androidx.compose.runtime.LaunchedEffect`、`androidx.compose.runtime.getValue`、`androidx.compose.runtime.mutableStateOf`、`androidx.compose.runtime.remember`、`androidx.compose.runtime.setValue`、`androidx.compose.ui.Alignment`、`androidx.compose.ui.platform.LocalContext`、`androidx.compose.ui.unit.dp`、`android.widget.Toast`、`me.jbusdriver.R`、`me.jbusdriver.modern.ui.MovieUiModel`、`me.jbusdriver.modern.ui.components.MovieItem`。

- [ ] **Step 5: 新增字符串**（values + values-en）：
```xml
<string name="local_video_cleanup">清理</string>
```
en：`<string name="local_video_cleanup">Clean up</string>`

- [ ] **Step 6: 运行测试通过 + 构建**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.CollectionListViewModelTest"`
Expected: 全绿。
Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: 手测**

未收藏分区头点"清理"→卡片出现 Checkbox；勾选若干→"刪除選中"→确认→文件删除、Toast、列表刷新、退出选择模式。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt \
  app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListScreen.kt \
  app/src/test/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModelTest.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(local-video): multi-select cleanup for uncollected local videos"
```

---

## Task 13: 收尾——质量门 + 自检

- [ ] **Step 1: 全量测试**
Run: `./gradlew testDebugUnitTest`
Expected: 全绿。

- [ ] **Step 2: Lint + Debug 构建**
Run: `./gradlew lintDebug assembleDebug`
Expected: BUILD SUCCESSFUL，无新增 error。

- [ ] **Step 3: Release 构建（ProGuard/R8）**
Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL。（本特性无新 Gson 模型；`LocalVideoEntity` 为 Room 生成代码；快照字段为纯 String，无需新 keep 规则。）

- [ ] **Step 4: 死代码清理**
- `git grep LocalVideoPickerSheet` 应无结果。
- 确认 `UiModels.kt`、`MovieListItems.kt` 无遗留旧签名。

- [ ] **Step 5: 端到端手测清单**
- 详情：单文件/多文件删除；取消收藏三选一（保留/全删/选部分）；未收藏或无本地视频时♥直接切换。
- 收藏：开关显隐未收藏分区；看过即补全；虚拟卡片点击进详情；分区多选删除。
- 迁移：在已装旧版本（v1）上升级到新版本，确认 `t_local_video` 数据保留、扫描索引不丢。

- [ ] **Step 6: 提交（若 Step 4 有清理）**
```bash
git add -A
git status   # 确认仅相关文件
git commit -m "chore(local-video): cleanup dead code after management features"
```

---

## Spec coverage 自检

| Spec 要求 | 任务 |
|---|---|
| t_local_video 加 title/imageUrl/date/censorType + 迁移 | Task 1 |
| DAO observeAll/findByIds/deleteByIds/updateSnapshot | Task 2 |
| SAF 单文件删除（DocumentsContract + Mutex 串行） | Task 3 + Task 5 |
| LocalVideoGroup / DeleteResult 域模型 | Task 4 |
| repo deleteVideos/snapshotMetadata/observeAllGroupedByCode | Task 5 |
| LocalVideoSheet(Pick/DeleteMulti) | Task 6 |
| 详情快照-on-view | Task 7 |
| 详情溢出菜单删除 | Task 8 |
| 取消收藏三选一 + 多文件选择 | Task 8 |
| 删除结果 Toast（SharedFlow 消息） | Task 7 + Task 8 |
| 虚拟卡片虚线视觉 | Task 9 |
| 筛选开关 showUncollectedLocal | Task 10 |
| 收藏页未收藏分区 + 看过即补全 + 点击进详情 | Task 11 |
| 清理未收藏多选删除 | Task 12 |
| i18n（繁中/英，plurals） | Task 6/8/10/12 |
| 单测（纯函数/VM） | Task 5/7/11/12 |
| 质量门 testDebugUnitTest/lint/assembleRelease | Task 13 |
