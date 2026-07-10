# 本地视频管理：虚拟收藏 + 清理未收藏 + 详情删除

**Date:** 2026-07-10
**Status:** Approved
**Builds on:** `docs/superpowers/specs/2026-07-08-local-video-association-design.md`（番号扫描 + 封面播放图标，已实现于分支 `feat/local-video-association`）

## 问题（Problem）

`feat/local-video-association` 已实现"按番号扫描本地视频 + 详情封面播放图标 + 列表已下载角标 + 收藏筛选仅已下载"。但本地视频并不一定都已收藏，现有体验留有三个缺口：

1. **未收藏的本地视频不可见**：用户下载了影片却忘了收藏，这些视频在 App 内无处可寻（只有进入对应详情页才看到播放图标）。希望未收藏的本地视频也能出现在收藏页，视觉上与已收藏区分。
2. **无法清理未收藏的本地视频**：没有入口批量删除那些"下载了却没收藏/不想要"的文件。
3. **详情页无法删除本地视频**：取消收藏一个已有本地视频的影片时，文件成为孤儿；用户也无法主动从详情页删除（现有删除只有 `deleteAll()` 全清）。

此外，一个番号常对应**多个文件**（不同版本/分集），删除时需支持"全部删除"与"只删部分"。

## 方案总览（Solution）

三个能力不是孤立的，共用一组底层基建，一次做齐：

```
共享基建层
├─ LocalVideoDao：observeAll() / deleteByIds() / updateSnapshot()
├─ LocalVideoRepository：deleteVideos(ids)、snapshotMetadata(code,...)、observeAllGroupedByCode()
├─ SAF 单文件删除：DocumentsContract.deleteDocument(resolver, uri)
└─ LocalVideoSheet：LocalVideoPickerSheet 泛化为 Pick / DeleteMulti 两种 mode

三个能力
├─ 功能1 虚拟收藏：筛选开关 + 收藏页底部独立分区（极简卡片 + 看过即补全）
├─ 功能2 清理未收藏：该分区进入多选模式，删选中番号的所有文件
└─ 功能3 详情删除：AppBar 溢出菜单 + 取消收藏三选一对话框（多文件走多选表）
```

## 关键决策（Decisions）

| 维度 | 决策 |
|---|---|
| 未收藏卡片内容 | **极简 + 看过即补全**：初始只显番号+文件信息+通用缩略图；用户在详情页打开过该番号后，把标题/封面快照回填本地视频表，之后卡片显示真实封面（仍以虚线标记未收藏） |
| 虚拟卡片位置 | **筛选开关 + 独立分区**：`CollectionFilterSheet` 加开关"显示未收藏的本地视频"（默认关）；开时在已收藏列表之后渲染独立分区"未收藏的本地视频 (N)"；该分区兼作清理入口 |
| 取消收藏删除提示 | **三选一对话框 + 多选表**：[保留] / [删除全部(N)] / [选择部分…]（打开多选表）。详情页另加溢出菜单"删除本地视频"走独立删除路径 |
| 多选表 | 把 `LocalVideoPickerSheet` 泛化为 `LocalVideoSheet(mode)`，Pick 保持现状，DeleteMulti 加 Checkbox + 底部操作条 |
| 详情页事件 | 引入轻量 `SharedFlow<DetailOneShotEvent>` 驱动对话框/表（符合"ViewModel 用 Flow 而非回调"） |
| 未收藏判定 | ViewModel 内存过滤：`localVideoCodes - collectedCodes`（均小写归一），不引入跨库 SQL |
| 分区清理粒度 | **按 code**（整张卡片=一个番号=删其全部文件）；按文件粒度交给详情页 |
| SAF 删除 | `DocumentsContract.deleteDocument` 直接删子文档；与 `rescan` 经同一 Mutex 串行 |
| 元数据存储 | `t_local_video` 加 4 个可空列 `title/imageUrl/date/censorType`，迁移 1→2（显式 ALTER TABLE，非 destructive） |

## 数据流（Data Flow）

```
[本地视频索引 t_local_video] ──observeAll()──▶ LocalVideoRepository.observeAllGroupedByCode()
                                                     │  Flow<List<LocalVideoGroup>>
                                                     ▼
        CollectionListViewModel  ◄── collectedCodes = allMovies.map{ code.lowercase() }
            │  uncollectedLocalVideos = groups.filter { it.code !in collectedCodes }
            ▼
        CollectionListScreen  ──开关开──▶ "未收藏的本地视频 (N)" 分区（虚线卡片）
            │  点击卡片
            ▼
        RouteMovieDetail(movieUrl = code, censorType = entity.censorType ?: null)
            │  loadDetail 成功
            ▼
        MovieDetailViewModel.snapshotMetadata(code, title, imageUrl, date, censorType)
            │  幂等 UPDATE t_local_video ... WHERE code=?  ◄── Flow 自动重发 ◄── 虚拟卡片刷新出真实封面


[删除路径]
详情溢出菜单 / 取消收藏三选一 / 分区多选  ──▶  LocalVideoSheet(DeleteMulti) 多选
            │
            ▼
   confirmDialog(N 个, size) ──▶ LocalVideoRepository.deleteVideos(ids)
            │                        │ (Mutex 串行，与 rescan 互斥)
            ▼                        ▼
   DeleteResult(deleted, failed)   DocumentsContract.deleteDocument + dao.deleteByIds
            │
            ▼
   toast "已删除 X 个，Y 个失败"  +  Flow 自动更新各列表
```

## 数据模型变更（Data Model）

`t_local_video` 增加可空列（已收藏无关，仅未收藏虚拟卡片用到），Room 版本 1→2：

```kotlin
@Entity(tableName = "t_local_video", indices = [Index("code")])
data class LocalVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,            // 小写归一
    val name: String,
    val uri: String,             // tree 下子文档 content URI
    val mime: String?,
    val size: Long,
    val scannedAt: Long,
    // 新增（可空，旧数据为 null → 极简卡片）
    val title: String? = null,
    val imageUrl: String? = null,
    val date: String? = null,
    val censorType: String? = null,  // 记住该番号上次成功打开的域，回跳详情时复用
)
```

**迁移**：显式 `Migration(1, 2)`，4 条 `ALTER TABLE t_local_video ADD COLUMN ...`。**不**走 `fallbackToDestructiveMigration`——用户已扫描的索引不能丢。

**DAO 新增**：
- `@Query("SELECT * FROM t_local_video") fun observeAll(): Flow<List<LocalVideoEntity>>`
- `@Query("DELETE FROM t_local_video WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Int>)`
- 快照回填：`@Query("UPDATE t_local_video SET title=:t, imageUrl=:img, date=:d, censorType=:c WHERE code=:code") suspend fun updateSnapshot(code, t, img, d, c)`

**域模型**：
- `LocalVideo` 同步加 `title/imageUrl/date/censorType`（可空）。
- 新增 `LocalVideoGroup(code, title?, imageUrl?, date?, censorType?, files: List<LocalVideo>)` 供收藏页分区用。

**Repository 新增**：
- `fun observeAllGroupedByCode(): Flow<List<LocalVideoGroup>>`（observeAll 后按 code 分组，组内取首个非空 title/imageUrl/date/censorType 作为代表）。
- `suspend fun deleteVideos(ids: List<Int>): DeleteResult` —— 经 Mutex 串行；逐个 `DocumentsContract.deleteDocument`，成功（含文件已不存在的 FileNotFoundException）删 DB 行，失败（SecurityException 等）保留行并计入；返回 `DeleteResult(deleted: Int, failed: Int)`。
- `suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?)` —— 幂等 `updateSnapshot`。

## 功能1：虚拟收藏（Virtual Collection）

- **触发**：`CollectionFilterSheet` 增开关 chip"显示未收藏的本地视频"，状态进 `CollectionFilterState.showUncollectedLocal: Boolean`（默认 false）。仅出现在影片 tab。
- **呈现**：开关开时，`CollectionListScreen` 在已收藏列表之后渲染独立分区"未收藏的本地视频 (N)"。分区卡片为 `MovieItem`/`MovieGridItem` 的**虚线变体**（虚线边框 + 通用视频缩略图；若该 code 已被快照补全则显示真实封面，仍保留虚线标记未收藏）。
- **与其它筛选正交**：censor/年/月/"仅已下载"只作用于已收藏列表；分区恒为"所有未收藏本地视频 code"，不受这些筛选影响。`movieCount` tab badge 仍只算已收藏。
- **点击卡片**→ `RouteMovieDetail(movieUrl = code, censorType = entity.censorType ?: null)`。
- **看过即补全**：`MovieDetailViewModel.loadDetail` 成功后调 `snapshotMetadata(...)` 幂等回填该 code 下所有 entity；Flow 重发→虚拟卡片刷新出真实封面（仍虚线）。已收藏影片回填无害（其卡片走 LinkItem）。
- **空态**：未配置文件夹 / 无本地视频 → 分区不显示（开关亦无意义）。

## 功能2：清理未收藏（Cleanup）

- 分区进入**多选模式**：长按卡片，或分区头"清理"按钮。
- 选中单元 = **整张卡片 = 一个 code = 删其全部文件**。
- 底部操作条"已选 N 项（共 size）→ 删除"。点删除弹确认对话框（数量+总大小）→ `deleteVideos(ids)`（ids = 选中 code 的全部文件 id）。
- 提供"全选/反选"。删除后分区列表经 Flow 自动更新。

## 功能3：详情删除 + 取消收藏提示（Detail Delete）

- **AppBar 新增溢出菜单 `⋮`**（仿 `CollectCategoryScreen` 的 `DropdownMenu`），仅当 `localVideos.isNotEmpty()` 时显示"删除本地视频"项：
  - 单文件 → 确认对话框 → 删。
  - 多文件 → `LocalVideoSheet(DeleteMulti)` 多选（全选/反选 + 选中数与总大小）→ 确认 → 删。
- **取消收藏拦截**（`MovieDetailViewModel.toggleCollect`）：当前已收藏且 `localVideos.isNotEmpty()` 时，不直接 toggle，而是发一次性事件弹**三选一对话框**"已取消收藏，是否删除本地视频？"：
  - `[保留]` → 仅取消收藏，保留文件。
  - `[删除全部 (N)]` → 取消收藏 + 删该 code 全部文件。
  - `[选择部分…]` → 取消收藏 + 打开 `LocalVideoSheet(DeleteMulti)` 勾选部分文件删。
  - 未收藏 / 无本地视频 → 走原 toggle，无打扰。
- **多选表泛化**：`LocalVideoPickerSheet` → `LocalVideoSheet(mode: LocalVideoSheetMode)`。`Pick` 保持现状（点一项回调播放）；`DeleteMulti` 每项加 Checkbox + 底部操作条 `[删除选中(N, size)] [取消]`。共享同一套文件列表 UI（名称/大小/格式）。
- **一次性事件**：`MovieDetailViewModel` 增 `val oneShotEvents: SharedFlow<DetailOneShotEvent>`，事件如 `ConfirmUncollectDelete(code, fileCount, totalSize)`、`OpenDeleteSheet(code)`。Screen `LaunchedEffect` 收事件弹对应 UI。

## SAF 删除机制（Deletion）

`LocalVideoEntity.uri` 是 tree 下子文档 content URI。删除：

```kotlin
suspend fun deleteVideos(ids: List<Int>): DeleteResult = mutex.withLock {
    val entities = dao.findByIds(ids)
    var deleted = 0; var failed = 0
    val toRemoveFromDb = mutableListOf<Int>()
    entities.forEach { e ->
        val ok = try {
            DocumentsContract.deleteDocument(contentResolver, Uri.parse(e.uri)) ||
                !fileExists(e.uri)   // 返回 false 时复查文件是否已不在
        } catch (_: FileNotFoundException) { true }
          catch (_: SecurityException) { false }
          catch (_: Exception) { false }
        if (ok) { toRemoveFromDb += e.id; deleted++ } else { failed++ }
    }
    if (toRemoveFromDb.isNotEmpty()) dao.deleteByIds(toRemoveFromDb)
    DeleteResult(deleted, failed)
}
```

- 文件已不存在（FileNotFoundException / 返回 false 且复查不存在）→ 成功，删 DB 行。
- 权限丢失（SecurityException）→ 失败，保留行，计入。
- 与 `rescan` 经同一 `Mutex` 串行，避免重扫把正在删的索引重建脏写。
- 全部失败（tree 权限整体失效）→ 提示"删除失败，请检查文件夹权限"，引导去设置重选文件夹。

## 权限与清单（Permissions）

无需新增 `<uses-permission>`。删除复用既有 SAF tree 持久权限 + `FLAG_GRANT_READ_URI_PERMISSION` 机制；`DocumentsContract.deleteDocument` 在持有 tree 写权限（`takePersistableUriPermission` 已含读写）下可直接删子文档。

**ProGuard/Gson**：无新 Gson 模型；快照字段为纯 String；`LocalVideoEntity` 是 Room 生成代码。无需新 keep 规则。

## 边界与错误处理（Edge Cases）

| 情况 | 处理 |
|---|---|
| 文件已被外部删除 | deleteDocument 抛 FileNotFoundException / 返回 false 且复查不存在 → 成功，删 DB 行 |
| 文件夹权限被吊销/移动 | SecurityException → 失败计数，保留行，提示重选文件夹 |
| 多文件部分成功 | 删成功的行、留失败的行；toast"已删除 X 个，Y 个失败"；残留文件如已未收藏会出现在虚拟分区（状态自洽） |
| 重扫与删除并发 | 经 Repository Mutex 串行；snapshot 按 code 幂等 UPDATE，重扫重插同 code 后仍命中 |
| 未配置文件夹 / 无本地视频 | 虚拟分区不显示 |
| 虚拟卡片跳详情遇 404（无码片进了有码域） | 详情页正常加载/错误态；censorType 字段记住上次成功域，回跳优先用；自动跨域回退留作未来 |
| 取消收藏选"删除全部"但部分失败 | 已取消收藏（不可逆），残留文件留在虚拟分区 |
| 详情页无 localVideos | 溢出菜单不显示"删除本地视频"；toggleCollect 走原逻辑 |

## i18n 字符串（Strings）

中/英 `strings.xml` 各加；数量标签按 CLAUDE.md 用 `<plurals>`：

| name | zh | en |
|---|---|---|
| `local_video_show_uncollected` | 显示未收藏的本地视频 | Show uncollected local videos |
| `local_video_uncollected_section` | 未收藏的本地视频 | Uncollected local videos |
| `local_video_cleanup` | 清理 | Clean up |
| `local_video_delete_selected` | 删除选中 | Delete selected |
| `local_video_delete_menu` | 删除本地视频 | Delete local video |
| `local_video_delete_confirm_title` | 删除本地视频？ | Delete local videos? |
| `local_video_uncollect_title` | 已取消收藏 | Uncollected |
| `local_video_uncollect_message` | 是否同时删除本地视频？ | Also delete the local video? |
| `local_video_uncollect_keep` | 保留本地视频 | Keep files |
| `local_video_select_all` | 全选 | Select all |
| `local_video_invert` | 反选 | Invert |
| `local_video_delete_all_failed` | 删除失败，请检查文件夹权限 | Delete failed. Check folder permissions. |

`<plurals>`（zh/英各一份）：
- `local_video_uncollected_section_count`：未收藏的本地视频（%1$d 项）/ Uncollected local videos (%1$d)
- `local_video_delete_confirm_message`：将删除 %1$d 个文件，共 %2$s。此操作不可撤销。 / Will delete %1$d files (%2$s). This cannot be undone.
- `local_video_uncollect_delete_all`：删除全部（%1$d）/ Delete all (%1$d)
- `local_video_delete_selected_count_size`：删除 %1$d 项（共 %2$s）/ Delete %1$d items (%2$s)
- `local_video_delete_result`：已删除 %1$d 个，%2$d 个失败 / Deleted %1$d, %2$d failed
- `local_video_selected_count_size`：已选 %1$d 项（共 %2$s）/ %1$d selected (%2$s)

文件大小格式化复用现有 `FileUtil`。

## 测试（Testing）

单测为主；跳过 SAF/Room instrumented（成本高、收益低，遵循 CLAUDE.md）。

- **`LocalVideoRepository`**（假 DAO）：`deleteVideos` 成败映射正确（FileNotFoundException→成功、SecurityException→失败、部分成功删对行）；`snapshotMetadata` 按 code 幂等回填；`observeAllGroupedByCode` 分组 + 小写归一 + 代表字段取值。
- **`CollectionListViewModel`**：开关开→`uncollectedLocalVideos` = 不在 collectedCodes 的分组；collectedCodes 小写归一；开关关/无本地视频时分区隐藏；`movieCount` 不被污染；多选删除传对 ids。
- **`MovieDetailViewModel`**：loadDetail 成功才 snapshot（失败不写）；toggleCollect 在"已收藏+有本地视频"时发 `ConfirmUncollectDelete` 事件，否则走原 toggle；三条分支（保留/删全部/选部分）调用正确。
- **`LocalVideoSheet`** 两种 mode 渲染：Compose UI 走预览/手测，不写单测。

质量门：`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleRelease`。

## 范围（Scope）

**包含**（一份 spec，按价值/依赖分三期落地）：

- **Phase 1｜共享基建 + 详情删除**：DAO 增 `observeAll/deleteByIds/updateSnapshot`；`deleteVideos` + SAF 删除（Mutex 串行）；`LocalVideoSheet(DeleteMulti)`；详情溢出菜单；取消收藏三选一；`DetailOneShotEvent` SharedFlow；迁移 1→2。UI 足迹最小、价值最高。
- **Phase 2｜虚拟收藏**：`snapshotMetadata`；`observeAllGroupedByCode`；`CollectionFilterSheet` 开关；收藏页未收藏分区 + 虚线卡片；code→详情跳转。
- **Phase 3｜清理**：分区多选模式 + 批量确认删除（与 Phase 2 共用分区 UI，可合并入 Phase 2）。

**不包含（YAGNI）**：Lab 设置"一键删全部未收藏"（分区全选已覆盖）；code→URL 自动跨域回退；收藏分区内按文件粒度（详情页已覆盖）；网络拉封面（已否决）；删除撤销/回收站（确认对话框兜底）；扫描深度配置。

## 变更文件清单（Affected Files）

**新增**
- `ui/components/LocalVideoSheet.kt`（由 `LocalVideoPickerSheet.kt` 泛化而来；旧文件迁移或重命名）
- `domain/model/LocalVideoGroup.kt`（或并入 `LocalVideo.kt`）

**改动**
- `data/db/entity/LocalVideoEntity.kt`：加 4 列。
- `data/db/dao/LocalVideoDao.kt`：`observeAll` / `deleteByIds` / `updateSnapshot` / `findByIds`。
- `data/db/LocalVideoDatabase.kt`：version 1→2 + `Migration(1,2)`；`app/schemas/.../2.json` 导出。
- `data/repository/LocalVideoRepository.kt`：`deleteVideos` / `snapshotMetadata` / `observeAllGroupedByCode` + 复用既有 Mutex。
- `domain/model/LocalVideo.kt`：加 4 可空字段。
- `ui/detail/MovieDetailViewModel.kt`：注入删除/snapshot；`toggleCollect` 拦截；`DetailOneShotEvent` SharedFlow；`loadDetail` 成功后 snapshot。
- `ui/detail/MovieDetailScreen.kt`：AppBar 溢出菜单；三选一对话框；`LocalVideoSheet(DeleteMulti)`；收集 `oneShotEvents`。
- `ui/movielist/CollectionListViewModel.kt`：`uncollectedLocalVideos` 状态；选中态；`deleteSelected`。
- `ui/movielist/CollectionListScreen.kt`：未收藏分区 + 虚线卡片 + 多选操作条。
- `ui/movielist/CollectionFilterSheet.kt` + `CollectionFilterState.kt`：`showUncollectedLocal` 开关。
- `ui/components/MovieListItems.kt`：`MovieItem`/`MovieGridItem` 虚线变体（`isVirtual` 参数）。
- `ui/localvideo/LocalVideoPickerSheet.kt`：泛化为 `LocalVideoSheet(mode)`（或新建并迁移调用点）。
- `res/values/strings.xml` + `res/values-en/strings.xml`：上表字符串 + plurals。
