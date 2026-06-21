# 统一设置页重写设计（Settings Rewrite）

## Context（背景）

`dev/setting` 分支交付了一个能跑的设置功能 DEMO：统一设置页（外观 / 网络 / 备份与恢复），替换了旧的 `LabSettingsScreen`，并接入了主题集成、底部导航动态过滤、收藏变更自动备份。

DEMO 实现可用，但**数据层架构存在明显债**：`AppSettingsStore` 是 200 行的 God 对象（既存所有设置，又内嵌 WebView 镜像扫描逻辑）；`BackupManager` 一个 445 行类同时做序列化 + SAF + WebDAV 编排；`JBusTheme` 通过 `hiltViewModel<SettingsViewModel>()` 读主题，耦合过大；收藏层用 `Lazy<BackupManager>` 反向依赖备份层。

本设计的目标是**架构重做**：保留 DEMO 呈现的功能目标（三大块能力），从更清晰的分层模型重新搭数据层与集成方式。DEMO 仅作功能参考，重写在 `main` 干净基底上新建文件，不直接 cherry-pick 那批提交。

## Goals / Non-Goals

**Goals**
- 设置存储退化为薄单 DataStore（纯键值），职责单一
- 镜像扫描抽离为独立 `MirrorScanner` 服务
- 主题通过 `ThemeRepository` 与设置 VM 解耦
- 备份能力接口化（`BackupStorage`），本地 / WebDAV 同契约，可扩展
- 收藏↔备份单向解耦：收藏层只发事件，备份层消费
- 恢复体验提升：预检冲突 + 粗粒度策略选择（合并 / 覆盖 / 取消）
- 清理 DEMO 遗留的文件卫生与调试残留问题

**Non-Goals（本阶段不做）**
- 备份能力上 release（仍 `BuildConfig.DEBUG` 门控）
- WebDAV 客户端的健壮性增强（401 Authenticator 挑战-响应、路径 URL 编码）—— 最简实现先落地，踩到再补
- 恢复的逐项冲突裁决 UI —— 后续阶段（备份上 release 时）再做
- 既有用户设置迁移 —— 干净键名重开，debug 阶段设置重置可接受
- UI i18n 全面资源化 —— 列为既有技术债跟进项，不阻塞本阶段

## Decisions（已确认的关键决策）

| 维度 | 决定 |
|------|------|
| 目标 | 架构重做，三大功能（外观/网络/备份）作参考保留 |
| 备份定位 | 先 debug 验证，release 隐藏；可扩展但不追生产级 |
| WebDAV | 手写 over OkHttp（最简实现先落地），抽 `BackupStorage` 接口；防御性增强延后 |
| 设置存储 | 薄单 DataStore 纯键值；扫描抽 `MirrorScanner`；主题抽 `ThemeRepository` 解耦 VM |
| 迁移 | 不迁移，干净键名重开 |
| VM 形态 | 方案 A：服务抽取 + 单 `SettingsViewModel`（内部分域）+ 事件化自动备份 |
| 恢复冲突 | 粗粒度：预检冲突统计 + 三选一全局策略（合并/覆盖/取消） |

## Architecture & Layering（分层架构）

分层目标：单一职责 + 收藏↔备份单向解耦 + 主题与设置 VM 解耦 + 备份能力接口化可扩展。

```
┌─────────────────────────────────────────────────────────┐
│  UI 层                                                   │
│  SettingsScreen (3 cards)  SettingsViewModel  ThemeVM    │
│  Theme.kt (读 ThemeVM)                                   │
└───────────────┬──────────────────────────┬──────────────┘
                │ delegate / state          │ theme
┌───────────────▼──────────┐  ┌─────────────▼─────────────┐
│ 服务层                    │  │ ThemeRepository            │
│ MirrorScanner             │  │ (themeMode / dynamicColor) │
└───────────────┬──────────┘  └─────────────┬─────────────┘
                │ reachability                 │
┌───────────────▼─────────────────────────────▼───────────┐
│ 数据层                                                   │
│ AppSettingsStore (薄键值 DataStore)                       │
│ BackupManager ── BackupStorage (interface)               │
│        │              ├── LocalBackupStorage (SAF)       │
│        │              └── WebDavBackupStorage            │
│        │                       └── WebDavClient (最简)    │
│ BackupSerializer (纯函数)  BackupCoordinator (订阅事件)   │
│ CollectRepository ──collectionChanges: SharedFlow──▶     │
└─────────────────────────────────────────────────────────┘
```

**关键解耦点**
- **主题**：`JBusTheme` → `ThemeViewModel` → `ThemeRepository` → `AppSettingsStore`。不再 `hiltViewModel<SettingsViewModel>()`。
- **收藏↔备份**：`CollectRepository` 只 emit 事件；`BackupCoordinator` 单向消费。收藏层零备份依赖（删除 DEMO 里 4 处 `.also { backupManagerLazy?.get()?.autoBackupIfNeeded() }`）。
- **备份扩展**：新增云盘只需实现 `BackupStorage`，`BackupManager` 不改。

## Package Structure（包结构）

```
data/
  settings/
    AppSettingsStore.kt        ★ 重做：薄单 DataStore，纯键值 StateFlow，不含扫描
    ThemeRepository.kt          ★ 新增：interface + impl，读 themeMode/dynamicColor
  mirror/
    MirrorScanner.kt            ★ 新增：WebView 发现 + 并发可达性验证，自带 scanState
  backup/
    BackupManager.kt            ★ 重做：序列化(v2)+委托 storage+keep-latest，恢复预检冲突
    BackupSerializer.kt         ★ 新增：v2 JSON 构建/解析，纯函数
    BackupStorage.kt            ★ 新增：统一接口
    LocalBackupStorage.kt       ★ 新增：SAF 实现
    WebDavBackupStorage.kt      ★ 新增：WebDAV 实现
    BackupCoordinator.kt        ★ 新增：@Singleton，订阅 CollectRepository 事件 → 触发自动备份
    webdav/
      WebDavClient.kt            ★ 重做（最简）：OkHttp + Basic Auth，PUT/GET/PROPFIND/DELETE/MKCOL
      WebDavClientFactory.kt     ★ 保留
  CollectRepository.kt          ★ 改：暴露 collectionChanges: SharedFlow，移除 Lazy<BackupManager>
ui/
  settings/
    SettingsScreen.kt           ★ 保留 3 卡结构（外观/网络/备份）
    SettingsViewModel.kt        ★ 重做：单 VM，分组状态，纯委托，无业务逻辑
    ThemeViewModel.kt            ★ 新增：薄 VM，JBusTheme 读它而非 SettingsViewModel
ui/theme/Theme.kt               ★ 改：读 ThemeViewModel
ui/MainScreen.kt                ★ 改：tab 过滤源从新 store 读
ui/Navigation.kt + NavigationKeys.kt  ★ RouteSettings 保留
JBusApplication.kt              ★ 改：logStoragePaths 限定 DEBUG 或删除；启动 BackupCoordinator
```

## Component Contracts（组件契约）

### AppSettingsStore（薄键值层）
`@Singleton`，注入 `@ApplicationContext`。每项设置 = `StateFlow<T>` + `suspend setX()`，`SharingStarted.Eagerly`。**零扫描逻辑。**

干净键名：
- 外观：`theme_mode`、`dynamic_color`、`show_movie_tab`、`show_actress_tab`
- 论坛：`show_forum_tab`、`auto_load_gifs`、`forum_floor_order`
- 网络：`selected_base_url`、`cached_mirror_urls`
- 备份：`auto_backup_enabled`、`backup_target`、`keep_latest_only`、`local_backup_uri`、`webdav_server_url`、`webdav_username`、`webdav_password`、`webdav_folder`、`webdav_device_name`

### ThemeRepository
`@Singleton`，注入 `AppSettingsStore`。暴露 `themeMode: StateFlow<ThemeMode>` 与 `dynamicColor: StateFlow<Boolean>`。给 `ThemeViewModel` / `JBusTheme` 用，隔离主题访问。

### MirrorScanner
`@Singleton`，注入 `AppSettingsStore` + `NetClient`。
- `suspend fun scan(seedUrl: String, state: MutableStateFlow<ScanState>)`
- `suspend fun verify(state: MutableStateFlow<ScanState>)`
- 拥有 `ScanState` / `ScanPhase` / `MirrorUrl` 数据类
- WebView 在 `Dispatchers.Main`，可达性验证在 `Dispatchers.IO`（并发 6）

### BackupStorage（接口）
```kotlin
interface BackupStorage {
    suspend fun write(name: String, data: ByteArray): Result<String>   // 返回写入路径/URI
    suspend fun list(): Result<List<BackupFileInfo>>
    suspend fun read(name: String): Result<ByteArray>
    suspend fun delete(name: String): Result<Unit>
    fun describe(): String   // UI 展示："本地 / <path>" 或 "WebDAV / <server>"
}
```
- `LocalBackupStorage`（SAF，需 `Context` + 持久化 URI）
- `WebDavBackupStorage`（用 `WebDavClient`）
- 两者都通过 `list()` + `delete()` 实现 keep-latest 清理

### BackupSerializer（纯函数）
```kotlin
fun buildV2(collections: String, settings: Map<String, String>, history: List<HistoryDto>, deviceName: String): String
fun parse(json: String): BackupPayload   // 含 version 检测：无 version → v1，version==2 → v2
```

### BackupManager
`@Singleton`，注入 `CollectRepository` + `AppSettingsStore` + `HistoryDao` + `BackupStorage` 工厂 + `Context`。
- `suspend fun performBackup(target: BackupTarget): Result<BackupResult>`
- `suspend fun checkRestoreConflicts(payload: BackupPayload): RestoreConflictReport`（预检，按收藏 key / 设置键 / 历史 URL+时间戳 比对现有数据）
- `suspend fun performRestore(payload: BackupPayload, strategy: RestoreStrategy): Result<RestoreResult>`
- `suspend fun autoBackupIfNeeded()`（`autoBackupEnabled` 关则直接返回）

### BackupCoordinator
`@Singleton`，注入 `Lazy<CollectRepository>` + `Lazy<BackupManager>`。订阅 `collectRepository.collectionChanges` → `backupManager.autoBackupIfNeeded()`。app 启动时由 `JBusApplication` 触发订阅。

### CollectRepository
新增 `val collectionChanges: SharedFlow<CollectionChangeEvent>`，增/删/切换收藏时 emit。**删除 `Lazy<BackupManager>` 注入与 4 处 `.also` 触发。**

### SettingsViewModel
单 VM，注入 `AppSettingsStore` + `MirrorScanner` + `BackupManager` + `WebDavClientFactory`。
- 暴露外观 / 网络 / 备份状态（收集自 store）+ `scanState`（委托 scanner）+ `backupState` + `webdavTestState` + `restoreConflict`（恢复预检结果）
- 函数：`setX`（转发 store）、`startScan`/`cancelScan`/`startVerify`/`selectUrl`（转发 scanner）、`backup`、`restoreFromFile`、`applyRestore(strategy)`、`testWebDavConnection`
- **无业务逻辑，纯委托 + 状态整形**

### ThemeViewModel
薄 VM，注入 `ThemeRepository`，暴露 `themeMode` + `dynamicColor`。供 `JBusTheme` 用。

## Data Flow（数据流）

### 设置读写
- 读：UI `collectAsStateWithLifecycle()` 订阅 `AppSettingsStore` 各 `StateFlow`，改值即重组。
- 写：UI 调 `vm.setX(v)` → `store.setX(v)` → `dataStore.edit{}` → StateFlow 推新值 → 重组。

### 主题同步
`JBusTheme` → `hiltViewModel<ThemeViewModel>()` → `ThemeRepository.themeMode/dynamicColor` → 选 `colorScheme`；`SideEffect` 里按 `darkTheme` 设状态栏/导航栏图标色 + `window` 背景色（沿用 DEMO 的 window chrome 同步，避免转场闪屏）。改主题即时生效。

### 镜像扫描
`vm.startScan()` → `MirrorScanner.scan(seedUrl, scanState)`：
1. `DISCOVERING`：seed + `cachedMirrorUrls` 去重作种子，WebView 逐个加载、JS 抽取 `<strong>防屏蔽地址/永久域名</strong>` 旁的链接
2. `VERIFYING`：对发现集合并发(6) `NetClient.checkReachable` 测延迟
3. `DONE`：按「默认主机优先 → 可达优先 → 延迟升序」排序，写回 `cached_mirror_urls`
- `cancelScan()` 协程取消，保留已发现的种子
- `startVerify()` 跳过发现阶段，直接对 cached 重测
- UI：`NetworkCard` 的 `ExposedDropdownMenuBox` 显示 URL + 延迟/不可达标签，扫描时进度条 + `scanned/total` 文本

### 手动备份
`vm.backup()` → `BackupManager.performBackup(target)`：
1. 收集：`CollectRepository.exportCollectionsJson()` + `AppSettingsStore` 非敏感设置 + `HistoryDao` 分批读
2. `BackupSerializer.buildV2(...)` → 字符串
3. 按 `target` 选 storage：`LOCAL`→`LocalBackupStorage`、`WEBDAV`→`WebDavBackupStorage`、`BOTH`→两者
4. 文件名 `jbus_backup_yyyy-MM-dd_HH-mm-ss.json`，`storage.write()`
5. `keepLatestOnly` 开 → `storage.list()` 按 mtime 排序，`delete()` 除最新外
6. `backupState.lastResult` = 写入路径；`BackupCard` 底部显示「上次备份」

### 恢复（含冲突预检）
SAF `OpenDocument` 选 JSON → 读文本 → `BackupSerializer.parse()` → `BackupManager.checkRestoreConflicts(payload)`：
- **无冲突**：直接 `performRestore(payload, MERGE)`，报告结果
- **有冲突**：VM 把 `RestoreConflictReport` 暴露给 UI → `AlertDialog` 显示各类冲突数 + 三动作：
  - 保留现有（`MERGE`：跳过冲突项 = 合并）
  - 用备份覆盖（`OVERWRITE`：替换冲突项）
  - 取消（中止）
- 按策略 `performRestore(payload, strategy)` → 报告真实导入/覆盖数
- 收藏：`CollectRepository.importCollectionsJson(json, strategy)`（扩既有 skip-only 为带策略）；设置：按键写回（敏感项不入）；历史：按 URL+时间戳去重，`OVERWRITE` 时替换

### 自动备份（事件化解耦）
用户增/删/切收藏 → `DefaultCollectRepository` emit `CollectionChangeEvent` → `collectionChanges: SharedFlow` → `BackupCoordinator` 收到 → `BackupManager.autoBackupIfNeeded()`：
- `autoBackupEnabled` 关 → 直接返回
- 开 → 走与手动备份相同的收集/序列化/写入/清理流程
- 与 UI 无关，静默执行

## Error Handling（错误处理）

| 路径 | 失败处理 |
|------|---------|
| WebDAV 测试/上传 | `Result.failure` → `webdavTestState.error` / `backupState.error`，显示原始信息（最简客户端，不预设重试/挑战） |
| 本地备份写 | 未选目录(URI 空) → 明确错误「請先選擇本地備份路徑」；IO 失败 → `backupState.error`，不留半成品 |
| 恢复解析 | 非 JSON / 版本未知 → `backupState.error`「備份檔格式無效」 |
| 恢复中途 | 收藏导入按策略，单条失败不中断；最终汇总真实导入/覆盖数 |
| 扫描 | 单种子失败 → 记日志跳过，不中断整轮；全部失败 → `scanState.error` |
| 自动备份 | 后台失败 → **静默记日志**，不打扰用户（不影响收藏操作本身） |
| 主题 | 设置读取异常 → 回落默认（系统主题/动态色开），不崩 |

**核心原则**：所有 storage/scan 操作走 `Result` 或 StateFlow error 字段；UI 永远拿到「成功结果 or 明确错误」，不暴露半状态。自动备份例外——后台静默。

## Testing（测试策略）

| 单元 | 测什么 |
|------|--------|
| `AppSettingsStore` | 各键默认值 + setter 生效 |
| `BackupSerializer` | `buildV2` 结构正确；`parse` 识别 v2 与 v1(无 version) |
| `BackupManager`（核心） | fake `BackupStorage` + fake repos：①预检 `RestoreConflictReport` 计数正确 ②`performBackup` 写入 + keep-latest 删除正确的旧文件 ③三种恢复策略的实际导入/覆盖数 |
| `BackupStorage` 契约 | 共享抽象测试，覆盖 `Local`(临时目录) 与 `WebDav`(MockWebServer) 的 write/list/read/delete 往返 |
| `WebDavClient` | MockWebServer 验证最简 PUT/GET/PROPFIND/DELETE/MKCOL（不测挑战-响应） |
| `MirrorScanner` | 抽出纯逻辑（JS 结果→URL 解析、排序比较器）单测；WebView/网络部分手动 |
| `CollectRepository` | 增/删/切换时 emit `CollectionChangeEvent` |
| `BackupCoordinator` | fake `SharedFlow` → `autoBackupIfNeeded` 开关开时调用、关时不调用 |
| `SettingsViewModel` | fake services → 委托 + 状态整形 + 冲突 report 上抛 |

**质量门**：`testDebugUnitTest` + `lintDebug` + `assembleDebug` + `assembleRelease`（release 跑 Gson/备份 JSON 冒烟）。

## Layout / Interaction（布局/交互：沿用 DEMO + 小修正）

- 三卡布局（外观 / 网络 / 备份与恢复）、入口（收藏菜单「更多設置」→ `RouteSettings`）、Backup 卡 `BuildConfig.DEBUG` 门控 —— **不变**
- 整行可点 Switch、`DropdownMenu` 选择、`AnimatedVisibility` 条件子面板、SAF 选择器 —— **沿用**
- keep-latest 开关文案写清：「僅保留最新一份，刪除其餘」
- **新增冲突弹窗**：恢复预检发现冲突时，`AlertDialog` 显示各类冲突数 + 三动作（保留现有 / 用备份覆盖 / 取消）
- **i18n**：既有技术债（AGENTS.md 已记），新可见文案应走资源/plurals —— 列为后续跟进项，不阻塞本阶段

## File Hygiene（文件卫生，重写时一并清理）

- 删除误提交的 `.superpowers/brainstorm/*`（visual companion 产物、`server.pid`、`server-stopped`）
- `JBusApplication.logStoragePaths()` 限定 `BuildConfig.DEBUG` 或从生产路径移除
- 确保无 `LabSettings*` 残留引用
- 重写落在 `main` 干净基底上新建文件，dev/setting 仅作功能参考（不直接 cherry-pick 那批提交）

## Verification（验收清单）

1. `./gradlew assembleDebug` 编译通过
2. `./gradlew test` 全部通过
3. `./gradlew assembleRelease` 通过（release 跑 Gson/备份 JSON 冒烟）
4. 手动：收藏菜单「更多設置」进入设置页
5. 手动：切换主题模式（系统/亮/暗）即时生效，状态栏图标色同步
6. 手动：开关 影片/演员/论坛 标签可见性，底部导航即时增删；隐藏当前标签时自动切走
7. 手动：论坛 ON → 子设置（自动载入动图、楼层顺序）可见；OFF → 隐藏
8. 手动：镜像扫描 → 进度 → URL 列表带延迟/不可达标签 → 选 URL 生效
9. 手动：本地备份(SAF)生成有效 JSON，文件名带时间戳
10. 手动：恢复无冲突 → 直接导入并报告；恢复有冲突 → 弹窗三选一 → 按策略导入并报告真实数
11. 手动：keep-latest 开 → 仅保留最新一份
12. 手动：自动备份开 → 收藏变更后静默生成备份；关 → 不触发
13. 手动：WebDAV 测试连接成功/失败有明确反馈（debug 构建）

## Out of Scope / 后续阶段

- WebDAV 客户端健壮性（401 Authenticator、路径编码）—— 踩到再做
- 恢复逐项冲突裁决 UI —— 备份上 release 时再做
- 备份能力 release 可见 —— 单独评估
- UI 全面 i18n —— 既有技术债跟进
