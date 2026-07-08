# 本地视频关联：番号命名扫描 + 封面播放图标

**Date:** 2026-07-08
**Status:** Approved

## 问题（Problem）

用户常把对应影片的视频下载到本地（不同格式/版本）。希望：在影片详情页，若本地存在对应视频，就在封面大图正中覆盖一个播放图标，点击调用**系统视频播放器**打开（不内置播放器，最快实现播放）。需要一个把"番号 ↔ 本地视频文件"对应起来的机制。

## 方案总览（Solution）

采用 **按番号命名 + 文件夹扫描**：

- 用户把视频重命名为 `<番号>...<ext>`（如 `ABC-123.mp4`、`ABC-123_4K.mkv`），统一放进一个文件夹。
- 在 Lab 设置页用 SAF（`ACTION_OPEN_DOCUMENT_TREE`）选择该文件夹一次，App 持久化读取权限。
- App 扫描文件夹（DocumentFile 递归枚举），按**前缀宽松匹配规则**把文件归到番号，结果以 `code → 文件列表` 索引存入 Room。
- 扫描时机：选文件夹后立即扫一次；App 进入前台时后台重扫一次；Lab 设置页"重新扫描"按钮。
- 影片详情页用番号瞬时查表（`Flow`），命中则在封面叠加播放图标；单文件直接跳转系统播放器，多文件弹底部选择列表。
- 无需内置播放器；无需新增 manifest 存储权限（SAF + 持久化 tree 权限 + 每次启动 `FLAG_GRANT_READ_URI_PERMISSION`）。

## 关键决策（Decisions）

| 维度 | 决策 |
|---|---|
| 关联方式 | 按番号命名 + 文件夹扫描（非逐部手动关联） |
| 目录访问 | SAF `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`，可指向任意位置 |
| 匹配规则 | 前缀宽松匹配，大小写不敏感；番号后紧跟**非字母数字、非连字符**的分隔符才命中 |
| 多文件 | 弹底部选择列表（显示文件名/大小/格式） |
| 扫描/索引 | Room 索引；前台后台重扫 + 手动重扫；详情页瞬时查表 |
| 播放器 | 系统播放器（`ACTION_VIEW` + chooser），不内置 |

## 数据流（Data Flow）

```
[SAF 选文件夹] → 持久化 tree URI → LocalVideoFolderStore (DataStore)
                         │
                         ▼
        LocalVideoScanner (DocumentFile 递归枚举 + VideoCodeMatcher)
                         │  扫描结果
                         ▼
        LocalVideoDatabase (Room: code → 文件列表索引) ◄─ 前台重扫 / 手动重扫
                         │  Flow<List<LocalVideoEntity>>
                         ▼
        MovieDetailViewModel.observeForCode(code) → UiState.localVideos
                         │
        ┌────────────────┴───────────────────┐
        ▼                                       ▼
  详情封面 Box 叠加播放图标          点击 → (单文件直跳 / 多文件弹选择表)
                                              │
                                              ▼
                                 VideoLauncher: ACTION_VIEW + FLAG_GRANT_READ_URI_PERMISSION
```

番号取自详情：`detail.headers.firstOrNull()?.value`（与现有 `MovieDetailViewModel.toCollectionMovie`、`MovieDetailScreen.kt:149` 一致）。该值为空则不关联。

## 匹配规则（VideoCodeMatcher）

纯函数 `matches(fileName: String, code: String): Boolean`，大小写不敏感：

1. 取文件名去扩展名并 trim；code 也 trim；code 为空返回 false。
2. 文件名 == 番号（小写比较）→ 命中。
3. 否则文件名以番号开头时，看番号**后一个字符**：
   - 是分隔符（非 `a-z`、非 `0-9`、非 `-`）→ 命中；
   - 否则不命中。

| 文件名（code=ABC-123） | 结果 | 原因 |
|---|---|---|
| `ABC-123.mp4` | ✓ | 等于番号 |
| `abc-123.mkv` | ✓ | 等于（大小写不敏感） |
| `ABC-123_4K.mkv` | ✓ | 后继 `_` 分隔符 |
| `ABC-123 (1080p).mp4` | ✓ | 后继 空格 分隔符 |
| `ABC-123.1080p.mp4` | ✓ | 后继 `.` 分隔符 |
| `ABC-123-C.mp4` | ✗ | 后继 `-`（不同影片） |
| `ABC-123D.mp4` | ✗ | 后继字母（不同影片） |

索引中 `code` 以小写归一化存储，查询时也归一化。

## 变更明细（Changes）

### 新增文件

`data/db/`
- **`LocalVideoDatabase.kt`** — 独立 Room DB（不并入 CollectDatabase，零迁移风险）。`@Database(entities = [LocalVideoEntity::class], version = 1, exportSchema = true)`。
- **`entity/LocalVideoEntity.kt`**：
  ```kotlin
  @Entity(tableName = "t_local_video", indices = [Index("code")])
  data class LocalVideoEntity(
      @PrimaryKey(autoGenerate = true) val id: Int = 0,
      val code: String,            // 命中的番号（小写归一化）
      val name: String,            // 显示名（文件名去路径）
      val uri: String,             // content:// 子文档 URI（凭 tree 持久权限可读）
      val mime: String?,
      val size: Long,
      val scannedAt: Long,
  )
  ```
- **`dao/LocalVideoDao.kt`**：
  - `@Query("SELECT * FROM t_local_video WHERE code = :code") fun observeForCode(code: String): Flow<List<LocalVideoEntity>>`
  - `@Query("DELETE FROM t_local_video") suspend fun deleteAll()`
  - `@Insert suspend fun insertAll(items: List<LocalVideoEntity>)`
  - `@Query("SELECT COUNT(*) FROM t_local_video") fun observeCount(): Flow<Int>`

`data/localvideo/`
- **`LocalVideoFolderStore.kt`** — 新 `preferencesDataStore("local_video")`，存 tree URI 字符串与 `lastScannedAt`。提供 `folderUri: Flow<String?>`、`setFolder(uri)`、`clearFolder()`、`lastScannedAt` 读写。设置文件夹时调用 `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`；清除时 `releasePersistableUriPermission`。
- **`VideoCodeMatcher.kt`** — 上述匹配纯函数 + 单测。
- **`LocalVideoScanner.kt`** — 持久 tree URI → `DocumentFile.fromTreeUri` → 递归 `listFiles()`（不设深度限制，视频预期不多）；对每个视频文件（按 mime/扩展名过滤为 video）用 `VideoCodeMatcher` 提取 code，命中则生成 `LocalVideoEntity`。把"文件枚举源"抽象为接口以便单测注入假源。扫描结果在 Room 事务内 `deleteAll() + insertAll()` 原子重建索引。
- **`LocalVideoRepository.kt`** — 接口 + 默认实现：
  - `fun observeForCode(code: String): Flow<List<LocalVideo>>`
  - `suspend fun rescan(): ScanResult`（无文件夹则返回未配置；否则扫描+重建索引+更新 lastScannedAt）
  - `fun hasFolder(): Flow<Boolean>`
  - `suspend fun setFolder(uri: Uri)` / `suspend fun clearFolder()`
  - `fun observeSummary(): Flow<LocalVideoSummary>`（count + lastScannedAt，供设置页）
- **`VideoLauncher.kt`** — `launch(context, uri, mime)`：组装 `Intent(ACTION_VIEW).setDataAndType(uri, mime ?: "video/*").addFlags(FLAG_GRANT_READ_URI_PERMISSION)`，`Intent.createChooser` 启动；`catch ActivityNotFoundException` → Toast。

`domain/model/`
- **`LocalVideo.kt`** — UI 模型：`data class LocalVideo(val code: String, val name: String, val uri: String, val mime: String?, val size: Long)`。附 `LocalVideoSummary(val count: Int, val lastScannedAt: Long?)`。

`ui/localvideo/`
- **`LocalVideoPickerSheet.kt`** — 多文件选择底部表（仿 `MagnetBottomSheet`），列出文件名/大小/格式，选中回调。单文件场景不弹此表。

### 改动文件

- **`MovieDetailViewModel.kt`**：
  - 注入 `LocalVideoRepository`。
  - `MovieDetailUiState` 增加 `val localVideos: List<LocalVideo> = emptyList()`。
  - `loadDetail` 成功拿到 detail 后，取 `code = detail.headers.firstOrNull()?.value.orEmpty()`；若非空，启动一个随 `loadDetail` 生命周期的采集（`viewModelScope` + 缓存/取消旧任务），`repository.observeForCode(code)` → 归一化小写 → 写入 `UiState.localVideos`。code 为空则保持空列表。
- **`MovieDetailScreen.kt`（封面区 265–291）**：把封面 `item(key = "cover")` 里的 `AppAsyncImage` 包进 `Box`；当 `localVideos.isNotEmpty()` 时叠加一个居中的半透明圆形 + `PlayArrow` 图标（`Modifier.matchParentSize()`）。点击行为：
  - `localVideos.size == 1` → `VideoLauncher.launch(...)`；
  - `size > 1` → 显示 `LocalVideoPickerSheet`，选中后启动。
  - `localVideos` 与回调沿现有参数链传入 `DetailContent`。
- **`LabSettingsScreen.kt`**：新增"本地视频"分组：
  - 视频文件夹（显示当前文件夹显示名 / "未选择"；点击启动 `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`，授权后存 URI + rescan）。
  - 重新扫描按钮（触发 rescan，显示"正在扫描…/完成，已关联 N 个"）。
  - 只读：上次扫描时间、已关联数量（来自 `observeSummary()`）。
- **`ModernMainActivity.kt`**：`onCreate` 注册 `ProcessLifecycleOwner.get().lifecycle` 观察者；`ON_START` 且 `hasFolder()` 为真时启动后台 `rescan()`（`viewModelScope` 或应用级协程，取消上一次未完成任务）。首次未设文件夹跳过。
- **`data/di/DatabaseModule.kt`**：`@Provides` `LocalVideoDatabase`（`@ApplicationContext`）+ `LocalVideoDao`。
- **`data/di/DataModule.kt`**：`@Binds` `LocalVideoRepository` 接口 → 实现。`LocalVideoScanner`/`LocalVideoFolderStore`/`VideoLauncher` 为 `@Inject constructor` 具体类，直接可注入。
- **`app/build.gradle.kts`**：若未引入，加 `androidx.lifecycle:lifecycle-process` 依赖（ProcessLifecycleOwner）。
- **`res/values/strings.xml` + `res/values-en/strings.xml`**：新增下述字符串（中/英各一份）。

## 跳转播放（VideoLauncher）

```kotlin
fun launch(context: Context, uri: String, mime: String?) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri.toUri(), mime ?: "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.play_local_video)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_video_player, Toast.LENGTH_SHORT).show()
    }
}
```

子文档 URI 借 tree 的持久权限可向系统播放器授予读权限。

## 权限与清单（Permissions）

**无需新增 `<uses-permission>`**。SAF 不要求 manifest 存储权限；凭 `takePersistableUriPermission`（tree）+ 每次启动 `FLAG_GRANT_READ_URI_PERMISSION`。ProGuard/R8：新 Room 实体非 Gson 模型，无需 keep 规则；若后续序列化 `LocalVideo` 再补。

## 边界与错误处理（Edge Cases）

- code 为空（`headers.firstOrNull()?.value` 为空）→ 不关联，封面无图标。
- 文件夹未设置 / 被移动或重命名 → 索引清空，详情页无图标；设置页提示重选文件夹。
- 重扫期间详情页读到的旧索引即时可用，扫完通过 Flow 自动更新。
- 递归扫描不设深度限制（视频预期不多）；超大目录的节流留待未来。
- 多文件匹配同一番号 → 详情点击弹选择表，用户选具体文件。
- 系统无可用播放器 → Toast 提示。

## i18n 字符串（Strings）

中/英 `strings.xml` 各加：

| name | zh | en |
|---|---|---|
| `local_video` | 本地视频 | Local video |
| `local_video_folder` | 视频文件夹 | Video folder |
| `local_video_folder_not_set` | 未选择 | Not selected |
| `local_video_select_folder` | 选择视频文件夹 | Select video folder |
| `local_video_rescan` | 重新扫描 | Rescan |
| `local_video_last_scan` | 上次扫描：%1$s | Last scan: %1$s |
| `local_video_linked_count` | 已关联 %1$d 个视频 | %1$d videos linked |
| `local_video_scanning` | 正在扫描… | Scanning… |
| `local_video_scan_done` | 扫描完成，已关联 %1$d 个 | Scan done, %1$d linked |
| `play_local_video` | 播放本地视频 | Play local video |
| `no_video_player` | 未找到可播放视频的应用 | No app found to play video |

数量标签使用 `%1$d`（CLAUDE.md 建议复数用 plurals；此处先按单数占位，必要时改 plurals）。

## 测试（Testing）

- **单测 `VideoCodeMatcher`**：上表全部命中/排除用例。
- **单测 Scanner 转换逻辑**：把文件枚举抽象成接口，注入假源，断言 code→entries 映射与事务性重建（清空+插入）正确，大小写归一化正确。
- **单测 `MovieDetailViewModel`**：假 `LocalVideoRepository` 发射列表，断言 `UiState.localVideos` 正确更新；code 为空时不订阅、不关联。
- 不做 SAF/Room 的 instrumented 重测（成本高、收益低）。

## 范围（Scope）

**包含**：选文件夹（SAF）、建 Room 索引、前台重扫 + 手动重扫、详情封面播放图标、多文件选择表、系统播放器跳转、前缀宽松匹配规则、Lab 设置入口、i18n、单测。

**不包含（未来）**：列表卡片"已下载"角标、内置播放器、播放进度记忆、自动重命名助手、多文件夹、扫描深度配置、plurals 化数量标签。
