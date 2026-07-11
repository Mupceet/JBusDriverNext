# 本地视频关联 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在影片详情页封面叠加播放图标，点击用系统播放器播放用户已下载、按番号命名放进选定文件夹的本地视频。

**Architecture:** 用户用 SAF 选定视频文件夹（持久化读取权限）。App 扫描文件夹，用番号提取正则把每个视频文件归到番号，结果以 `code → 文件列表` 存入独立 Room 库。详情页按番号瞬时查表（Flow），命中则在封面叠加播放图标；单文件直接跳转、多文件弹底部选择表。进前台时后台重扫 + 设置页手动重扫。系统播放器跳转用 `ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`。

**Tech Stack:** Kotlin、Jetpack Compose + Material3、Hilt、Room 2.8（KSP）、DataStore Preferences、kotlinx-coroutines、DocumentFile（SAF）、`lifecycle-process`（ProcessLifecycleOwner）。

## Global Constraints

- 包名 `me.jbusdriver.modern`；ViewModel 不暴露回调给 UI，用 StateFlow/Flow。
- 番号取自 `detail.headers.firstOrNull()?.value`（与现有 `MovieDetailViewModel.toCollectionMovie` 一致）。
- **不新增** manifest 存储权限：仅靠 SAF + `takePersistableUriPermission` + 每次启动 `FLAG_GRANT_READ_URI_PERMISSION`。
- 番号匹配用提取正则，归一化为大写后比较；索引 `code` 以大写存储，查询也大写。
- `rescan()` 在仓库内串行化（Mutex），并发调用（设置页 / 前台观察者）安全。
- 所有新可见字符串在 `res/values/strings.xml`（繁中）**和** `res/values-en/strings.xml`（英）各加一份。
- ProGuard：新 Room 实体非 Gson 模型，无需 keep 规则。
- 不写 SAF/Room 的 instrumented 测试；对 `VideoCodeMatcher`、`LocalVideoScanner`、`MovieDetailViewModel` 写单测。
- Gradle 需 `JAVA_HOME` 指向 Android Studio JBR（仓库构建前提）。
- 提交遵循 Conventional Commits（`feat:`/`test:`/`build:`/`refactor:`）；每任务结束提交一次。

## File Structure

**新增**
- `domain/model/LocalVideo.kt` — UI/领域模型 `LocalVideo`、`LocalVideoSummary`
- `data/localvideo/VideoCodeMatcher.kt` — 纯函数番号提取/匹配（单测覆盖）
- `data/localvideo/LocalVideoFileSource.kt` — `LocalVideoFileSource` 接口 + `ScannedFile` + `DocumentFileVideoFileSource` 实现
- `data/localvideo/LocalVideoScanner.kt` — 纯函数 `scanVideoFiles(...)`
- `data/localvideo/LocalVideoFolderStore.kt` — DataStore（tree URI + 文件夹名 + 上次扫描时间）+ 持久化权限
- `data/localvideo/VideoLauncher.kt` — `launchLocalVideo(context, video)`：发起系统播放器
- `data/localvideo/LocalVideoForegroundScanner.kt` — `@Singleton` `DefaultLifecycleObserver`，前台触发重扫
- `data/repository/LocalVideoRepository.kt` — 接口 + `DefaultLocalVideoRepository`（仓库内 Mutex 串行化）
- `data/db/entity/LocalVideoEntity.kt` — Room 实体
- `data/db/dao/LocalVideoDao.kt` — DAO（Flow 查询）
- `data/db/LocalVideoDatabase.kt` — 独立 Room 库
- `ui/localvideo/LocalVideoPickerSheet.kt` — 多文件底部选择表
- 测试：`data/localvideo/VideoCodeMatcherTest.kt`、`data/localvideo/LocalVideoScannerTest.kt`

**修改**
- `data/db/RoomDatabaseFactory.kt` — 加 `LOCAL_VIDEO_DB_NAME` + `buildLocalVideoDatabase`
- `data/di/DatabaseModule.kt` — 提供 `LocalVideoDatabase` + `LocalVideoDao`
- `data/di/DataModule.kt` — `@Binds LocalVideoRepository`
- `ui/detail/MovieDetailViewModel.kt` — 注入仓库；UiState 加 `localVideos`；订阅 `observeForCode`
- `ui/detail/MovieDetailScreen.kt` — 封面包 Box 叠加播放图标 + 选择表
- `ui/settings/SettingsViewModel.kt` — 注入仓库；暴露 summary/rescan/setFolder
- `ui/settings/SettingsScreen.kt` — 加 `LocalVideoCard`
- `ui/ModernMainActivity.kt` — 注册前台重扫观察者
- `app/build.gradle.kts` + `gradle/libs.versions.toml` — 加 `lifecycle-process`
- `res/values/strings.xml` + `res/values-en/strings.xml` — 新字符串
- 测试：`ui/detail/MovieDetailViewModelTest.kt` — 构造加第 4 个仓库参数 + 新测试

---

### Task 1: 番号匹配纯函数（TDD）

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/localvideo/VideoCodeMatcher.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/localvideo/VideoCodeMatcherTest.kt`

**Interfaces:**
- Produces: `object VideoCodeMatcher { fun extractCode(fileName: String): String?; fun matchesCode(fileName: String, code: String): Boolean }`

- [ ] **Step 1: 写失败测试**

Create `app/src/test/java/me/jbusdriver/modern/data/localvideo/VideoCodeMatcherTest.kt`:

```kotlin
package me.jbusdriver.modern.data.localvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCodeMatcherTest {

    @Test
    fun extractCode_exactMatch() {
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("ABC-123.mp4"))
    }

    @Test
    fun extractCode_caseInsensitive_uppercases() {
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("abc-123.mkv"))
    }

    @Test
    fun extractCode_keepsAlphabeticSuffix_asDistinctCode() {
        // ABC-123-C 是另一部影片，应整体提取，不截成 ABC-123
        assertEquals("ABC-123-C", VideoCodeMatcher.extractCode("ABC-123-C.mp4"))
    }

    @Test
    fun extractCode_stopsAtSeparator() {
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("ABC-123_4K.mkv"))
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("ABC-123 (1080p).mp4"))
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("ABC-123.1080p.mp4"))
    }

    @Test
    fun extractCode_skipsLeadingBrackets() {
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("[ABC-123].mp4"))
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("[Group] ABC-123.mp4"))
    }

    @Test
    fun extractCode_returnsNullForNoCode() {
        assertNull(VideoCodeMatcher.extractCode("clip.mp4"))
        assertNull(VideoCodeMatcher.extractCode("4K-trailer.mp4"))
    }

    @Test
    fun matchesCode_table() {
        assertTrue(VideoCodeMatcher.matchesCode("ABC-123.mp4", "ABC-123"))
        assertTrue(VideoCodeMatcher.matchesCode("abc-123.mkv", "abc-123"))
        assertTrue(VideoCodeMatcher.matchesCode("ABC-123_4K.mkv", "ABC-123"))
        assertTrue(VideoCodeMatcher.matchesCode("ABC-123 (1080p).mp4", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesCode("ABC-123-C.mp4", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesCode("ABC-123D.mp4", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesCode("clip.mp4", "ABC-123"))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.localvideo.VideoCodeMatcherTest"`
Expected: FAIL（`VideoCodeMatcher` 未解析）

- [ ] **Step 3: 写最小实现**

Create `app/src/main/java/me/jbusdriver/modern/data/localvideo/VideoCodeMatcher.kt`:

```kotlin
package me.jbusdriver.modern.data.localvideo

/**
 * 番号提取/匹配规则（大小写不敏感，结果归一化为大写）。
 *
 * 提取正则在文件名（去扩展名）中找首个 `[字母]{2,6}-?[数字]{2,5}[字母数字-]*` 的片段，
 * 末尾再裁掉尾部连字符。这样：
 * - `ABC-123` / `ABC-123_4K` / `ABC-123 (1080p)` 都提取为 `ABC-123`（分隔符截断）；
 * - `ABC-123-C` / `ABC-123D` 整体提取为不同番号；
 * - 前导方括号（`[ABC-123]`、`[Group] ABC-123`）会被跳过。
 */
object VideoCodeMatcher {

    private val codeRegex = Regex("""[A-Za-z]{2,6}-?\d{2,5}[A-Za-z0-9-]*""")

    /** 从文件名中提取番号（大写），无法识别返回 null。 */
    fun extractCode(fileName: String): String? {
        val withoutExt = fileName.substringBeforeLast('.')
        val raw = codeRegex.find(withoutExt)?.value?.trimEnd('-') ?: return null
        if (raw.length < 3) return null
        return raw.uppercase()
    }

    /** 文件名是否属于给定番号（大小写不敏感）。 */
    fun matchesCode(fileName: String, code: String): Boolean {
        val target = code.trim()
        if (target.isBlank()) return false
        return extractCode(fileName)?.equals(target, ignoreCase = true) == true
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.localvideo.VideoCodeMatcherTest"`
Expected: PASS（7 个测试全过）

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/localvideo/VideoCodeMatcher.kt \
        app/src/test/java/me/jbusdriver/modern/data/localvideo/VideoCodeMatcherTest.kt
git commit -m "feat(local-video): add code extraction/matching rule"
```

---

### Task 2: i18n 字符串

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Produces: 字符串资源 `R.string.local_video`、`local_video_folder`、`local_video_folder_not_set`、`local_video_clear_folder`、`local_video_rescan`、`local_video_scanning`、`local_video_last_scan`、`local_video_linked_count`、`play_local_video`、`no_video_player`

- [ ] **Step 1: 在 `res/values/strings.xml` 末尾（`</resources>` 之前）追加**

```xml
    <!-- Local video association -->
    <string name="local_video">本地視頻</string>
    <string name="local_video_folder">視頻文件夾</string>
    <string name="local_video_folder_not_set">未選擇</string>
    <string name="local_video_clear_folder">清除文件夾</string>
    <string name="local_video_rescan">重新掃描</string>
    <string name="local_video_scanning">正在掃描…</string>
    <string name="local_video_last_scan">上次掃描：%1$s</string>
    <string name="local_video_linked_count">已關聯 %1$d 個視頻</string>
    <string name="play_local_video">播放本地視頻</string>
    <string name="no_video_player">未找到可播放視頻的應用</string>
```

- [ ] **Step 2: 在 `res/values-en/strings.xml` 末尾（`</resources>` 之前）追加**

```xml
    <!-- Local video association -->
    <string name="local_video">Local video</string>
    <string name="local_video_folder">Video folder</string>
    <string name="local_video_folder_not_set">Not selected</string>
    <string name="local_video_clear_folder">Clear folder</string>
    <string name="local_video_rescan">Rescan</string>
    <string name="local_video_scanning">Scanning…</string>
    <string name="local_video_last_scan">Last scan: %1$s</string>
    <string name="local_video_linked_count">%1$d videos linked</string>
    <string name="play_local_video">Play local video</string>
    <string name="no_video_player">No app found to play video</string>
```

- [ ] **Step 3: 确认编译通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL（资源校验通过）

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(local-video): add i18n strings"
```

---

### Task 3: Room 数据层（实体 / DAO / 库 / 工厂 / DI）

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/db/entity/LocalVideoEntity.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/db/dao/LocalVideoDao.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/db/LocalVideoDatabase.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/RoomDatabaseFactory.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DatabaseModule.kt`

**Interfaces:**
- Produces: `LocalVideoEntity`（表 `t_local_video`，列 `code` 带索引）、`LocalVideoDao`（`observeForCode(code): Flow<List<LocalVideoEntity>>`、`observeCount(): Flow<Int>`、`deleteAll()`、`insertAll(List)`）、`LocalVideoDatabase`、`buildLocalVideoDatabase(context)`

无单测（Room/DB 为接线，依赖 Android；按 spec 不写 instrumented 测试）。

- [ ] **Step 1: 创建实体 `LocalVideoEntity.kt`**

```kotlin
package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
)
```

- [ ] **Step 2: 创建 DAO `LocalVideoDao.kt`**

```kotlin
package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.jbusdriver.modern.data.db.entity.LocalVideoEntity

@Dao
interface LocalVideoDao {

    @Query("SELECT * FROM t_local_video WHERE code = :code ORDER BY name ASC")
    fun observeForCode(code: String): Flow<List<LocalVideoEntity>>

    @Query("SELECT COUNT(*) FROM t_local_video")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM t_local_video")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalVideoEntity>)
}
```

- [ ] **Step 3: 创建库 `LocalVideoDatabase.kt`**

```kotlin
package me.jbusdriver.modern.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jbusdriver.modern.data.db.dao.LocalVideoDao
import me.jbusdriver.modern.data.db.entity.LocalVideoEntity

@Database(entities = [LocalVideoEntity::class], version = 1, exportSchema = true)
abstract class LocalVideoDatabase : RoomDatabase() {
    abstract fun localVideoDao(): LocalVideoDao
}
```

- [ ] **Step 4: 在 `RoomDatabaseFactory.kt` 末尾追加常量与工厂函数**

在 `private const val COLLECT_DB_NAME = "collect.db"`（第 10 行）下方新增：

```kotlin
private const val LOCAL_VIDEO_DB_NAME = "local_video.db"
```

在文件末尾（`buildCollectDatabase` 之后）追加：

```kotlin
fun buildLocalVideoDatabase(context: Context): LocalVideoDatabase =
    Room.databaseBuilder(
        context,
        LocalVideoDatabase::class.java,
        LOCAL_VIDEO_DB_NAME
    ).build()
```

- [ ] **Step 5: 在 `DatabaseModule.kt` 注册库与 DAO**

在 `provideLinkItemDao(db: CollectDatabase)` 之后追加（保留 import 区追加 `LocalVideoDatabase`、`buildLocalVideoDatabase`、`LocalVideoDao`）：

```kotlin
    @Provides
    @Singleton
    fun provideLocalVideoDatabase(@ApplicationContext context: Context): LocalVideoDatabase =
        buildLocalVideoDatabase(context)

    @Provides
    fun provideLocalVideoDao(db: LocalVideoDatabase): LocalVideoDao = db.localVideoDao()
```

并在 `DatabaseModule.kt` import 区追加：

```kotlin
import me.jbusdriver.modern.data.db.LocalVideoDatabase
import me.jbusdriver.modern.data.db.buildLocalVideoDatabase
import me.jbusdriver.modern.data.db.dao.LocalVideoDao
```

- [ ] **Step 6: 确认编译通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL（KSP 生成 DAO 实现，schema 导出到 `app/schemas/`）

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/db/entity/LocalVideoEntity.kt \
        app/src/main/java/me/jbusdriver/modern/data/db/dao/LocalVideoDao.kt \
        app/src/main/java/me/jbusdriver/modern/data/db/LocalVideoDatabase.kt \
        app/src/main/java/me/jbusdriver/modern/data/db/RoomDatabaseFactory.kt \
        app/src/main/java/me/jbusdriver/modern/data/di/DatabaseModule.kt \
        app/schemas/
git commit -m "feat(local-video): add Room data layer for local video index"
```

---

### Task 4: 文件夹 DataStore 与持久化权限

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoFolderStore.kt`

**Interfaces:**
- Produces: `@Singleton class LocalVideoFolderStore @Inject constructor(@ApplicationContext context)`：
  - `val folderUri: Flow<String?>`、`val folderDisplayName: Flow<String?>`、`val lastScannedAt: Flow<Long?>`
  - `suspend fun currentFolderUri(): String?`
  - `suspend fun setFolder(uri: Uri)`（内部 `takePersistableUriPermission` 并写入 uri/显示名）
  - `suspend fun setLastScannedAt(epochMs: Long)`
  - `suspend fun clearFolder()`（释放权限并清除全部 key）

- [ ] **Step 1: 创建 `LocalVideoFolderStore.kt`**

```kotlin
package me.jbusdriver.modern.data.localvideo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val Context.localVideoDataStore by preferencesDataStore("local_video")

/**
 * 本地视频文件夹偏好：保存 SAF tree URI、显示名、上次扫描时间，并管理其持久化读取权限。
 */
@Singleton
class LocalVideoFolderStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.localVideoDataStore

    val folderUri: Flow<String?> = dataStore.data.map { it[KEY_FOLDER_URI] }
    val folderDisplayName: Flow<String?> = dataStore.data.map { it[KEY_FOLDER_NAME] }
    val lastScannedAt: Flow<Long?> = dataStore.data.map { it[KEY_LAST_SCAN] }

    suspend fun currentFolderUri(): String? =
        dataStore.data.first()[KEY_FOLDER_URI]

    /** 记录文件夹：申请持久化读权限，并写入 uri 与显示名。 */
    suspend fun setFolder(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val displayName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        dataStore.edit {
            it[KEY_FOLDER_URI] = uri.toString()
            if (displayName != null) it[KEY_FOLDER_NAME] = displayName
        }
    }

    suspend fun setLastScannedAt(epochMs: Long) {
        dataStore.edit { it[KEY_LAST_SCAN] = epochMs }
    }

    /** 清除文件夹：释放权限并删除全部 key。 */
    suspend fun clearFolder() = withContext(Dispatchers.IO) {
        currentFolderUri()?.let { uriStr ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uriStr),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        dataStore.edit {
            it.remove(KEY_FOLDER_URI)
            it.remove(KEY_FOLDER_NAME)
            it.remove(KEY_LAST_SCAN)
        }
    }

    private companion object {
        val KEY_FOLDER_URI = stringPreferencesKey("folder_uri")
        val KEY_FOLDER_NAME = stringPreferencesKey("folder_name")
        val KEY_LAST_SCAN = longPreferencesKey("last_scan")
    }
}
```

> 注：`documentfile` 依赖随 `androidx.documentfile:documentfile` 提供，需确认已在依赖中（见 Step 2）。

- [ ] **Step 2: 确认 `documentfile` 依赖存在，缺失则补**

Run: `./gradlew dependencies --configuration debugRuntimeClasspath` 并查找 `androidx.documentfile`。
若无：在 `gradle/libs.versions.toml` `[versions]` 加 `documentfile = "1.1.0"`，`[libraries]` 加 `documentfile = { group = "androidx.documentfile", name = "documentfile", version.ref = "documentfile" }`，并在 `app/build.gradle.kts` `dependencies` 加 `implementation(libs.documentfile)`。

- [ ] **Step 3: 确认编译通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoFolderStore.kt \
        app/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat(local-video): add folder DataStore with persisted SAF permission"
```

---

### Task 5: 文件枚举源 + 扫描纯函数（TDD）

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoFileSource.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoScanner.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/localvideo/LocalVideoScannerTest.kt`

**Interfaces:**
- Consumes: `VideoCodeMatcher.extractCode`（Task 1）
- Produces: `data class ScannedFile(name, uri, mime, size)`、`interface LocalVideoFileSource { suspend fun listVideoFiles(): List<ScannedFile> }`、`class DocumentFileVideoFileSource @Inject constructor(context, store)`、顶层 `fun scanVideoFiles(files: List<ScannedFile>, scannedAt: Long): List<LocalVideoEntity>`

- [ ] **Step 1: 写失败测试 `LocalVideoScannerTest.kt`**

```kotlin
package me.jbusdriver.modern.data.localvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVideoScannerTest {

    @Test
    fun scan_mapsFilesToEntitiesByExtractedCode() {
        val now = 1_700_000_000_000L
        val files = listOf(
            ScannedFile("ABC-001.mp4", "u1", "video/mp4", 10L),
            ScannedFile("ABC-001_4K.mkv", "u2", "video/x-matroska", 20L),
            ScannedFile("DEF-002.mp4", "u3", null, 30L),
            ScannedFile("clip.mp4", "u4", "video/mp4", 40L), // 无番号，丢弃
        )

        val entities = scanVideoFiles(files, now)

        assertEquals(3, entities.size)
        val abc = entities.filter { it.code == "ABC-001" }
        assertEquals(2, abc.size)
        assertTrue(abc.all { it.scannedAt == now })
        assertTrue(entities.any { it.code == "DEF-002" && it.uri == "u3" && it.size == 30L })
        assertTrue(entities.none { it.uri == "u4" })
    }

    @Test
    fun scan_uppercasesCodes() {
        val entities = scanVideoFiles(
            listOf(ScannedFile("abc-003.mp4", "u", null, 1L)),
            0L,
        )
        assertEquals("ABC-003", entities.single().code)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.localvideo.LocalVideoScannerTest"`
Expected: FAIL（`ScannedFile`/`scanVideoFiles` 未解析）

- [ ] **Step 3: 创建 `LocalVideoFileSource.kt`**

```kotlin
package me.jbusdriver.modern.data.localvideo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 扫描到的单个文件（与 Android 解耦，便于单测）。 */
data class ScannedFile(
    val name: String,
    val uri: String,
    val mime: String?,
    val size: Long,
)

/** 视频文件枚举源。生产实现走 DocumentFile；测试用假实现。 */
interface LocalVideoFileSource {
    suspend fun listVideoFiles(): List<ScannedFile>
}

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "ts", "webm", "m4v", "mpg", "mpeg", "3gp", "rmvb",
)

/** 基于 SAF DocumentFile 的递归枚举实现。 */
class DocumentFileVideoFileSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderStore: LocalVideoFolderStore,
) : LocalVideoFileSource {

    override suspend fun listVideoFiles(): List<ScannedFile> = withContext(Dispatchers.IO) {
        val treeUriStr = folderStore.currentFolderUri() ?: return@withContext emptyList()
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr)) ?: return@withContext emptyList()
        if (!root.canRead()) return@withContext emptyList()
        val out = mutableListOf<ScannedFile>()
        collectVideos(root, out)
        out
    }

    private fun collectVideos(dir: DocumentFile, out: MutableList<ScannedFile>) {
        dir.listFiles().forEach { f ->
            when {
                f.isDirectory -> collectVideos(f, out)
                f.isFile && isVideo(f) -> {
                    val name = f.name ?: return@forEach
                    out += ScannedFile(name, f.uri.toString(), f.type, f.length())
                }
            }
        }
    }

    private fun isVideo(f: DocumentFile): Boolean {
        f.type?.let { if (it.startsWith("video/")) return true }
        val ext = f.name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in VIDEO_EXTENSIONS
    }
}
```

- [ ] **Step 4: 创建 `LocalVideoScanner.kt`**

```kotlin
package me.jbusdriver.modern.data.localvideo

import me.jbusdriver.modern.data.db.entity.LocalVideoEntity

/**
 * 把扫描到的文件列表按番号映射为索引实体（纯函数，无 Android 依赖）。
 *
 * 无番号的文件被丢弃；同一番号的多个文件各自保留一条（供详情页弹选择表）。
 */
fun scanVideoFiles(files: List<ScannedFile>, scannedAt: Long): List<LocalVideoEntity> =
    files.mapNotNull { f ->
        VideoCodeMatcher.extractCode(f.name)?.let { code ->
            LocalVideoEntity(
                code = code,
                name = f.name,
                uri = f.uri,
                mime = f.mime,
                size = f.size,
                scannedAt = scannedAt,
            )
        }
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.localvideo.LocalVideoScannerTest"`
Expected: PASS（2 个测试）

- [ ] **Step 6: 确认整体编译**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoFileSource.kt \
        app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoScanner.kt \
        app/src/test/java/me/jbusdriver/modern/data/localvideo/LocalVideoScannerTest.kt
git commit -m "feat(local-video): add file source and scan function"
```

---

### Task 6: 领域模型 + 仓库 + 视频跳转 + DI 绑定

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/domain/model/LocalVideo.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/repository/LocalVideoRepository.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/localvideo/VideoLauncher.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

**Interfaces:**
- Consumes: `LocalVideoDao`（Task 3）、`LocalVideoFolderStore`（Task 4）、`LocalVideoFileSource` + `scanVideoFiles`（Task 5）
- Produces:
  - `data class LocalVideo(code, name, uri, mime, size)`、`data class LocalVideoSummary(linkedCount, lastScannedAt, folderDisplayName)`
  - `interface LocalVideoRepository { fun observeForCode(code): Flow<List<LocalVideo>>; fun observeSummary(): Flow<LocalVideoSummary>; fun hasFolder(): Flow<Boolean>; suspend fun setFolder(uri); suspend fun clearFolder(); suspend fun rescan(): Int }` + `DefaultLocalVideoRepository`（仓库内 `Mutex` 串行化）
  - `fun launchLocalVideo(context: Context, video: LocalVideo)`（Task 8 消费）

无单测（仓库编排依赖 DAO/Android；扫描纯逻辑已在 Task 5 覆盖）。

- [ ] **Step 1: 创建领域模型 `LocalVideo.kt`**

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
)

/** 本地视频功能在设置页的汇总展示。 */
data class LocalVideoSummary(
    val linkedCount: Int = 0,
    val lastScannedAt: Long? = null,
    val folderDisplayName: String? = null,
)
```

- [ ] **Step 2: 创建 `VideoLauncher.kt`**

```kotlin
package me.jbusdriver.modern.data.localvideo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.LocalVideo

/**
 * 用系统播放器打开本地视频。借 SAF tree 的持久化读权限向播放器授予读权限。
 */
fun launchLocalVideo(context: Context, video: LocalVideo) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(video.uri), video.mime ?: "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.play_local_video))
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_video_player, Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **Step 3: 创建仓库 `LocalVideoRepository.kt`**

```kotlin
package me.jbusdriver.modern.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jbusdriver.modern.data.db.dao.LocalVideoDao
import me.jbusdriver.modern.data.db.entity.LocalVideoEntity
import me.jbusdriver.modern.data.localvideo.LocalVideoFileSource
import me.jbusdriver.modern.data.localvideo.LocalVideoFolderStore
import me.jbusdriver.modern.data.localvideo.scanVideoFiles
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import javax.inject.Inject
import javax.inject.Singleton

interface LocalVideoRepository {
    fun observeForCode(code: String): Flow<List<LocalVideo>>
    fun observeSummary(): Flow<LocalVideoSummary>
    fun hasFolder(): Flow<Boolean>
    suspend fun setFolder(uri: Uri)
    suspend fun clearFolder()
    suspend fun rescan(): Int
}

@Singleton
class DefaultLocalVideoRepository @Inject constructor(
    private val dao: LocalVideoDao,
    private val folderStore: LocalVideoFolderStore,
    private val fileSource: LocalVideoFileSource,
) : LocalVideoRepository {

    // 串行化重扫，避免设置页与前台观察者并发重建索引。
    private val rescanMutex = Mutex()

    override fun observeForCode(code: String): Flow<List<LocalVideo>> =
        dao.observeForCode(code.trim().uppercase())
            .map { list -> list.map { it.toDomain() } }

    override fun observeSummary(): Flow<LocalVideoSummary> =
        combine(
            dao.observeCount(),
            folderStore.folderDisplayName,
            folderStore.lastScannedAt,
        ) { count, displayName, lastScan ->
            LocalVideoSummary(count, lastScan, displayName)
        }

    override fun hasFolder(): Flow<Boolean> =
        folderStore.folderUri.map { uriStr -> uriStr != null }

    override suspend fun setFolder(uri: Uri) {
        folderStore.setFolder(uri)
        rescan()
    }

    override suspend fun clearFolder() {
        folderStore.clearFolder()
        dao.deleteAll()
    }

    override suspend fun rescan(): Int = rescanMutex.withLock {
        if (folderStore.currentFolderUri() == null) return@withLock 0
        val files = fileSource.listVideoFiles()
        val now = System.currentTimeMillis()
        val entities = scanVideoFiles(files, now)
        dao.deleteAll()
        if (entities.isNotEmpty()) dao.insertAll(entities)
        folderStore.setLastScannedAt(now)
        entities.size
    }

    private fun LocalVideoEntity.toDomain() =
        LocalVideo(code = code, name = name, uri = uri, mime = mime, size = size)
}
```

- [ ] **Step 4: 在 `DataModule.kt` 绑定仓库与文件源**

在 `DataModule` 抽象类内追加两个绑定（仓库接口与文件源接口都需绑定到实现，因为消费方依赖接口）：

```kotlin
    @Binds
    @Singleton
    abstract fun bindLocalVideoRepository(impl: DefaultLocalVideoRepository): LocalVideoRepository

    @Binds
    @Singleton
    abstract fun bindLocalVideoFileSource(impl: DocumentFileVideoFileSource): LocalVideoFileSource
```

import：

```kotlin
import me.jbusdriver.modern.data.localvideo.DocumentFileVideoFileSource
import me.jbusdriver.modern.data.localvideo.LocalVideoFileSource
import me.jbusdriver.modern.data.repository.DefaultLocalVideoRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
```

> `LocalVideoFolderStore` 为 `@Singleton @Inject` 具体类，仓库直接依赖其具体类型，无需 `@Binds`。

- [ ] **Step 5: 确认编译通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/domain/model/LocalVideo.kt \
        app/src/main/java/me/jbusdriver/modern/data/repository/LocalVideoRepository.kt \
        app/src/main/java/me/jbusdriver/modern/data/localvideo/VideoLauncher.kt \
        app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt
git commit -m "feat(local-video): add repository, launcher, and DI binding"
```

---

### Task 7: 详情 ViewModel 集成本地视频（TDD）

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `LocalVideoRepository.observeForCode`（Task 6）
- Produces: `MovieDetailUiState.localVideos: List<LocalVideo>`；构造函数第 4 个参数 `localVideoRepository`

- [ ] **Step 1: 先更新测试（红）—— 加 stub 仓库与新测试**

在 `MovieDetailViewModelTest.kt` import 区加：

```kotlin
import android.net.Uri
import kotlinx.coroutines.flow.flowOf
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoSummary
```

在 `stubMagnetRepo` 之后加 stub：

```kotlin
    private val stubLocalVideoRepo = object : LocalVideoRepository {
        override fun observeForCode(code: String) =
            flowOf(listOf(LocalVideo("ABC-001", "ABC-001.mp4", "content://x/ABC-001", "video/mp4", 1L)))
        override fun observeSummary() = flowOf(LocalVideoSummary())
        override fun hasFolder() = flowOf(true)
        override suspend fun setFolder(uri: Uri) {}
        override suspend fun clearFolder() {}
        override suspend fun rescan() = 0
    }
```

把全部 5 处 `MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo)` / `MovieDetailViewModel(detailRepo, collectRepo, stubMagnetRepo)` 改为追加第 4 参 `stubLocalVideoRepo`（如 `MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo)`）。

在测试类末尾追加两个测试：

```kotlin
    @Test
    fun loadDetail_loadsLocalVideos() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        val videos = viewModel.uiState.value.localVideos
        assertEquals(1, videos.size)
        assertEquals("ABC-001", videos.first().code)
    }

    @Test
    fun loadDetail_noCode_leavesLocalVideosEmpty() = runTest(testDispatcher) {
        val detailNoCode = testDetail.copy(headers = emptyList())
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = detailNoCode
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo)

        viewModel.loadDetail("http://example.com/none")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.localVideos.isEmpty())
    }
```

并在 import 区加：

```kotlin
import org.junit.Assert.assertTrue
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest"`
Expected: FAIL（构造函数签名不匹配 / `localVideos` 不存在）

- [ ] **Step 3: 修改 `MovieDetailViewModel.kt`**

(a) import 区加：

```kotlin
import kotlinx.coroutines.Job
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.domain.model.LocalVideo
```

(b) `MovieDetailUiState` 末尾（`uc: String? = null` 之后）加字段：

```kotlin
    val localVideos: List<LocalVideo> = emptyList(),
```

(c) 构造函数加第 4 个参数：

```kotlin
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository,
    private val collectRepository: CollectRepository,
    private val magnetRepository: MagnetRepository,
    private val localVideoRepository: LocalVideoRepository,
) : ViewModel() {
```

(d) 在 `private var censorType: String? = null` 之后加：

```kotlin
    private var localVideoJob: Job? = null
```

(e) 在 `loadDetail` 的 try 块内、`_uiState.update { it.copy(isCollected = collected) }` 之后追加：

```kotlin
                val code = detail.headers.firstOrNull()?.value.orEmpty()
                loadLocalVideos(code)
```

(f) 在 `clearError()` 之前插入：

```kotlin
    private fun loadLocalVideos(code: String) {
        localVideoJob?.cancel()
        if (code.isBlank()) {
            _uiState.update { it.copy(localVideos = emptyList()) }
            return
        }
        localVideoJob = viewModelScope.launch {
            localVideoRepository.observeForCode(code).collect { videos ->
                _uiState.update { it.copy(localVideos = videos) }
            }
        }
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest"`
Expected: PASS（全部测试，含新增 2 个）

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt \
        app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt
git commit -m "feat(local-video): wire local videos into detail ViewModel"
```

---

### Task 8: 详情封面播放图标 + 选择表

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/localvideo/LocalVideoPickerSheet.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`

**Interfaces:**
- Consumes: `MovieDetailUiState.localVideos`（Task 7）、`launchLocalVideo`（Task 6）、`LocalVideo`（Task 6）
- Produces: `internal fun LocalVideoPickerSheet(videos, onPicked, onDismiss)`；封面叠加播放图标行为

- [ ] **Step 1: 创建 `LocalVideoPickerSheet.kt`**

```kotlin
package me.jbusdriver.modern.ui.localvideo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.format.Formatter
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.LocalVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalVideoPickerSheet(
    videos: List<LocalVideo>,
    onPicked: (LocalVideo) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.play_local_video),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(videos, key = { it.uri }) { video ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPicked(video) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
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
        }
    }
}
```

- [ ] **Step 2: 修改 `MovieDetailScreen.kt` —— `DetailContent` 增加参数与播放叠加**

(a) import 区加：

```kotlin
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import me.jbusdriver.modern.data.localvideo.launchLocalVideo
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.ui.localvideo.LocalVideoPickerSheet
```

(b) `DetailContent` 签名在 `hasMagnets: Boolean = false` 之后加参数：

```kotlin
    localVideos: List<LocalVideo> = emptyList(),
```

(c) 把封面 `item(key = "cover") { ... }` 整块（当前 265–291 行）替换为：

```kotlin
        // Cover image
        item(key = "cover") {
            var showVideoPicker by remember { mutableStateOf(false) }
            Box {
                AppAsyncImage(
                    model = detail.cover,
                    contentDescription = detail.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(coverAspectRatio)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImageClick(allImages, 0) }
                        .onSizeChanged { size -> coverHeight.intValue = size.height },
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            val drawable = state.result.drawable
                            val width = drawable.intrinsicWidth
                            val height = drawable.intrinsicHeight
                            if (width > 0 && height > 0) {
                                val real = width.toFloat() / height.toFloat()
                                if (shouldAdoptCoverRatio(real)) {
                                    coverAspectRatio = real
                                }
                            }
                        }
                    }
                )
                if (localVideos.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable {
                                if (localVideos.size == 1) launchLocalVideo(context, localVideos.first())
                                else showVideoPicker = true
                            }
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play_local_video),
                            tint = Color.White,
                            modifier = Modifier.size(56.dp).padding(16.dp),
                        )
                    }
                }
            }
            if (showVideoPicker) {
                LocalVideoPickerSheet(
                    videos = localVideos,
                    onPicked = {
                        showVideoPicker = false
                        launchLocalVideo(context, it)
                    },
                    onDismiss = { showVideoPicker = false },
                )
            }
        }
```

(d) 在 `MovieDetailScreen` 调用 `DetailContent(...)` 处（约 189–202 行），在 `hasMagnets = uiState.magnets.isNotEmpty()` 之后加：

```kotlin
                        localVideos = uiState.localVideos
```

- [ ] **Step 3: 确认编译通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/localvideo/LocalVideoPickerSheet.kt \
        app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt
git commit -m "feat(local-video): overlay play icon on detail cover with picker sheet"
```

---

### Task 9: 设置页本地视频卡片

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `LocalVideoRepository`（Task 6）、Task 2 字符串
- Produces: `SettingsViewModel.localVideoSummary`/`isScanningVideos`/`setLocalVideoFolder`/`clearLocalVideoFolder`/`rescanLocalVideos`；`LocalVideoCard` UI

无单测（设置页为 UI 接线；仓库逻辑在 Task 6、扫描在 Task 5 已覆盖）。

- [ ] **Step 1: 修改 `SettingsViewModel.kt`**

(a) import 区加：

```kotlin
import android.net.Uri
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.domain.model.LocalVideoSummary
```

(b) 构造函数加第 3 个参数：

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val store: AppSettingsContract,
    private val siteConfig: SiteConfig,
    private val localVideoRepository: LocalVideoRepository,
) : ViewModel() {
```

(c) 在 `scanState` 声明之后（`val scanState: StateFlow<ScanState> = ...` 之后）加：

```kotlin
    val localVideoSummary: StateFlow<LocalVideoSummary> =
        localVideoRepository.observeSummary()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalVideoSummary())

    private val _isScanningVideos = MutableStateFlow(false)
    val isScanningVideos: StateFlow<Boolean> = _isScanningVideos.asStateFlow()

    fun setLocalVideoFolder(uri: Uri) {
        viewModelScope.launch { localVideoRepository.setFolder(uri) }
    }

    fun clearLocalVideoFolder() {
        viewModelScope.launch { localVideoRepository.clearFolder() }
    }

    fun rescanLocalVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningVideos.value = true
            try {
                localVideoRepository.rescan()
            } finally {
                _isScanningVideos.value = false
            }
        }
    }
```

- [ ] **Step 2: 修改 `SettingsScreen.kt` —— 增加卡片与状态收集**

(a) import 区加：

```kotlin
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Date
import java.util.Locale
import me.jbusdriver.modern.domain.model.LocalVideoSummary
```

(b) 在 `SettingsScreen` 主体内，`val scanState by viewModel.scanState.collectAsStateWithLifecycle()` 之后加：

```kotlin
    val localVideoSummary by viewModel.localVideoSummary.collectAsStateWithLifecycle()
    val isScanningVideos by viewModel.isScanningVideos.collectAsStateWithLifecycle()

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.setLocalVideoFolder(it) } }
```

(c) 在 `NetworkCard(...)` 调用之后、`Spacer(Modifier.height(8.dp))` 之前插入：

```kotlin
            // === Local Video Card ===
            LocalVideoCard(
                summary = localVideoSummary,
                isScanning = isScanningVideos,
                onPickFolder = { pickFolderLauncher.launch(null) },
                onClearFolder = { viewModel.clearLocalVideoFolder() },
                onRescan = { viewModel.rescanLocalVideos() },
            )
```

(d) 在文件末尾 `//endregion` 之后追加 `LocalVideoCard`：

```kotlin
//region Local Video Card

@Composable
private fun LocalVideoCard(
    summary: LocalVideoSummary,
    isScanning: Boolean,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.public_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.local_video),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))

            // 当前文件夹（点击选择/更换）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickFolder)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.local_video_folder),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        summary.folderDisplayName
                            ?: stringResource(R.string.local_video_folder_not_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.folderDisplayName != null) {
                    OutlinedButton(onClick = onClearFolder) {
                        Text(stringResource(R.string.local_video_clear_folder))
                    }
                }
            }

            // 上次扫描时间 + 关联数
            summary.lastScannedAt?.let { ts ->
                Text(
                    stringResource(R.string.local_video_last_scan, formatTime(ts)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.local_video_linked_count, summary.linkedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // 重新扫描
            Button(
                onClick = onRescan,
                enabled = !isScanning && summary.folderDisplayName != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isScanning) {
                    Text(stringResource(R.string.local_video_scanning))
                } else {
                    Text(stringResource(R.string.local_video_rescan))
                }
            }
        }
    }
}

private fun formatTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(epochMs))

//endregion
```

> 注：`R.drawable.public_24px` 已在 `NetworkCard` 使用，复用。

- [ ] **Step 3: 确认编译通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt \
        app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt
git commit -m "feat(local-video): add local video card to settings"
```

---

### Task 10: 前台重扫观察者 + lifecycle-process 依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoForegroundScanner.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt`

**Interfaces:**
- Consumes: `LocalVideoRepository.rescan()`（Task 6）
- Produces: `@Singleton class LocalVideoForegroundScanner @Inject constructor(repository) : DefaultLifecycleObserver`

- [ ] **Step 1: 加 `lifecycle-process` 依赖**

在 `gradle/libs.versions.toml` 的 `[libraries]` 中，紧随 `lifecycle-viewmodel-navigation3 = ...` 之后加：

```toml
lifecycle-process = { group = "androidx.lifecycle", name = "lifecycle-process", version.ref = "lifecycle" }
```

在 `app/build.gradle.kts` 的 `dependencies` 中，紧随 `implementation(libs.lifecycle.viewmodel.navigation3)` 之后加：

```kotlin
    implementation(libs.lifecycle.process)
```

- [ ] **Step 2: 创建 `LocalVideoForegroundScanner.kt`**

```kotlin
package me.jbusdriver.modern.data.localvideo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.KLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听应用进入前台，触发本地视频索引重扫。
 *
 * 注册到 [androidx.lifecycle.ProcessLifecycleOwner]，ON_START（冷启动 + 从后台返回）各触发一次；
 * 视频不多、扫描快，故不做节流。重扫本身在仓库内串行化。
 */
@Singleton
class LocalVideoForegroundScanner @Inject constructor(
    private val repository: LocalVideoRepository,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            runCatching { repository.rescan() }
                .onFailure { KLog.w("Local video rescan failed: ${it.message}") }
        }
    }
}
```

- [ ] **Step 3: 在 `ModernMainActivity.kt` 注册观察者**

(a) import 区加：

```kotlin
import androidx.lifecycle.ProcessLifecycleOwner
import me.jbusdriver.modern.data.localvideo.LocalVideoForegroundScanner
```

(b) 在 `@Inject lateinit var browserSessionClient: BrowserSessionClient` 之后加：

```kotlin
    @Inject
    lateinit var localVideoForegroundScanner: LocalVideoForegroundScanner
```

(c) 在 `onCreate` 的 `handleIntent(intent)` 之后、`setContent { ... }` 之前加：

```kotlin
        ProcessLifecycleOwner.get().lifecycle.addObserver(localVideoForegroundScanner)
```

> `LifecycleRegistry.addObserver` 对同一实例重复添加是幂等的，配置变更重建 Activity 不会重复触发。

- [ ] **Step 4: 确认编译通过**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/me/jbusdriver/modern/data/localvideo/LocalVideoForegroundScanner.kt \
        app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt
git commit -m "feat(local-video): rescan on app foreground via ProcessLifecycleOwner"
```

---

### Task 11: 最终验证

**Files:** 无（仅验证）

- [ ] **Step 1: 跑全部单测**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL（含 `VideoCodeMatcherTest`、`LocalVideoScannerTest`、`MovieDetailViewModelTest`）

- [ ] **Step 2: 跑 lint**

Run: `./gradlew lintDebug`
Expected: BUILD SUCCESSFUL（无新增 fatal 错误）

- [ ] **Step 3: 跑 release 构建烟测（ProGuard/R8）**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL（新 Room 实体非 Gson 模型，无需 keep；确认未因新代码触发混淆错误）

- [ ] **Step 4: 手测路径（连真机/模拟器）**

1. 在文件管理器把若干视频重命名为 `<番号>.mp4` 等，集中到一个文件夹。
2. 打开 App → 收藏 → 更多设置 → 「本地視頻」卡片 → 选择该文件夹。
3. 确认"已關聯 N 個視頻"与上次扫描时间出现。
4. 进入任一已关联影片详情页 → 封面正中出现播放图标 → 点击：
   - 单文件：直接弹出系统播放器选择；选中播放。
   - 多文件：弹出底部选择表 → 选一项 → 系统播放器播放。
5. 进入未关联番号的详情页 → 封面无图标。
6. 退到后台再回前台 → 观察日志确认重扫触发。
7. 选择一个不可播放的 uri（如已删除文件）→ Toast「未找到可播放視頻的應用」。

Expected: 全部行为符合预期。

- [ ] **Step 5: 提交（若有验证修复）**

```bash
git add -A
git commit -m "test(local-video): verification fixes"
```

（无修复则跳过本步。）
