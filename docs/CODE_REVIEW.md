# JBusDriver 项目代码检视报告（合并版）

**检视日期**: 2026-06-13（合并 2026-06-12 及 2026-05-28 审查结果）
**检视范围**: 全项目架构、数据层、UI层、核心模块、i18n、重复代码、类划分
**状态**: 仅审查并记录意见，未修改业务代码

---

## 一、总体评价

项目采用 **MVVM + Jetpack Compose + Hilt DI** 架构，整体结构清晰，模块划分合理。使用了 Navigation 3、stale-while-revalidate 缓存策略等较新的技术方案。以下按严重程度分类列出所有发现的问题。

---

## 二、P0：必须立即处理

### 2.1 认证 Cookie 已提交到仓库

**文件**: `gradle.properties`, `app/build.gradle.kts:32-44`

`JAVBUS_AUTH_COOKIE` 值被写入 `BuildConfig`，debug/release 都会包含。任何能反编译 APK 的人都能取得该凭据。

**建议**: 从版本控制删除真实值，仅从 `local.properties`（已 gitignore）或环境变量注入。

### 2.2 默认 debug 构建启用"模拟缓存变化"

**文件**: `gradle.properties:29`, `app/build.gradle.kts:115`

`cacheRefreshTestMode=true` 导致 debug 构建中列表数据被主动 `drop(1)`，开发者看到的不是真实数据，且导致 4 个单元测试失败。

**建议**: 仓库默认值应为 `false`，仅在手工测试时通过 `-P` 传入。

---

## 三、P1：高优先级功能问题

### 3.1 HTTP 4xx/5xx 被当作成功页面解析并写入缓存

**文件**: `core/http/NetClient.kt:134-141`

只检查响应体是否为空，不检查 `response.isSuccessful`。非 2xx 错误页会被解析并覆盖缓存，UI 表现为"内容突然消失"。

**建议**: 网络层拒绝非 2xx 状态码；解析层增加业务标记校验。

### 3.2 无码影片外部链接无法进入详情页

**文件**: `ui/ModernMainActivity.kt:89-104`

`/uncensored/{code}` 有两段路径，但代码仅把单段路径识别为影片。无码深链失效。

**建议**: 先规范化 `uncensored`/`xyz` 前缀，再依据剩余路径识别影片或列表。

### 3.3 搜索请求存在竞态

**文件**: `ui/search/SearchViewModel.kt:125-169`

每次搜索启动独立协程，不取消前一个请求。旧结果可覆盖新关键词或在清空操作后重新出现。

**建议**: 维护单一搜索 Job 并在新搜索/清空时取消，或使用 `flatMapLatest`。

### 3.4 `SiteConfig` 构造函数中使用 `runBlocking`

**文件**: `core/site/SiteConfig.kt:22-24`

`@Singleton` 构造函数中 `runBlocking { labSettingsStore.selectedBaseUrl.first() }` 阻塞主线程，冷启动有 ANR 风险。

**建议**: 使用内存默认值启动，异步同步持久化值。

### 3.5 `ForumSessionClient.ensureSession()` 持有 Activity 引用

**文件**: `data/ForumSessionClient.kt:29-36`

从 `JBusManager` 弱引用列表获取 Activity，可能获取到已销毁但未 GC 的引用。WebView 在主线程创建时若 Activity 正在销毁，可能产生窗口泄漏。

**建议**: 添加生命周期检查；考虑使用 Application Context 创建 WebView。

### 3.6 `ForumSessionManager` WebView 线程安全

**文件**: `data/ForumSessionManager.kt`

`destroy()` 在任意线程调用，`fetchDocument()` 在主线程执行，`@Volatile webView` 无同步保护，可能并发执行。

**建议**: 使用 `Mutex` 保护 WebView 访问，确保 `destroy()` 在主线程执行。

### 3.7 搜索请求竞态（详细）

**文件**: `ui/search/SearchViewModel.kt:125-169`

每次 `search()` 启动独立协程，不取消前一个请求。快速输入、切换类型或清空时，旧请求可能最后完成并覆盖新结果。

**建议**: 维护单一 `searchJob`，新搜索时 `cancel()` 旧 Job；使用 `query` 快照校验。

---

## 四、P2：中优先级问题

### 4.1 收藏写入结果和 UI 状态可能不一致

**文件**: `data/CollectRepository.kt:103-107, 120-128`

Room `insert()` 返回冲突值 `-1` 时仍返回 `true`；toggle 操作"先查询再增删"非原子。

**建议**: 以 DAO 返回值决定成功状态；toggle 下沉为事务或改用明确 add/remove。

### 4.2 镜像验证声称并发上限为 6，实际全部并发

**文件**: `data/LabSettingsStore.kt:233-255`

注释写明 `concurrency = 6`，实现却对所有 URL 直接 `async(Dispatchers.IO)`，无 Semaphore 控制。

**建议**: 落实固定并发上限；对 URL 做去重和协议校验。

### 4.3 ViewModel 代码大量重复

**文件**: 多个 ViewModel

以下模式在至少 5 个 ViewModel 中重复：
- `loadFirstPage()` / `revalidate()` / `loadMore()` / `refresh()` 四段式结构
- `CachedLoadEvent.Cached/Fresh/Failure` 状态处理
- `pendingFreshResult` + Snackbar 新数据提示机制

**涉及文件**:
- `MovieListViewModel.kt` (429行)
- `LinkMovieListViewModel.kt`
- `ActressListViewModel.kt`
- `GenreListViewModel.kt`
- `ForumViewModels.kt` (822行)

**建议**: 提供 `CachedViewModel` 基类或 `CachedDataDelegate`，封装公共 stale-while-revalidate 逻辑。

### 4.4 `logMovieDiff` / `logThreadDiff` / `logReplyDiff` 三个几乎相同的 diff 日志函数

**文件**:
- `MovieListViewModel.kt:28-59` — `logMovieDiff()`
- `ForumViewModels.kt:37-71` — `logThreadDiff()`
- `ForumViewModels.kt:73-103` — `logReplyDiff()`

三个函数逻辑完全相同：比较新旧列表，输出新增/移除/变更条目。仅类型参数不同。

**建议**: 统一为一个泛型版本 `<T> fun logListDiff(old: List<T>, new: List<T>, context: String, key: (T) -> Any, describe: (T) -> String)`。

### 4.5 `ForumViewModels.kt` 文件过大（822行）

**文件**: `ui/forum/ForumViewModels.kt`

单个文件包含 3 个 ViewModel + 3 个 UiState 数据类 + 3 个 diff 日志函数。文件过长，难以导航和维护。

**建议**: 拆分为 `ForumBoardsViewModel.kt`、`ForumThreadListViewModel.kt`、`ForumThreadDetailViewModel.kt`。

### 4.6 `MainScreen.kt` Composable 函数过大（378行）

**文件**: `ui/MainScreen.kt`

包含影片/演员/论坛/收藏四个 Tab 的全部逻辑，嵌套层级深，状态管理复杂（censorFilter、selectedGenreLinks、genreLinkMemory、showCategorySheet 等）。

**建议**: 将每个 Tab 提取为独立 Composable（`MovieTabContent`、`ActressTabContent` 等）。

### 4.7 `CollectRepository` 中 domain model 有可变字段

**文件**: `data/CollectRepository.kt:208, 222`

`Movie.categoryId` 和 `ActressInfo.categoryId` 是 `@Transient` 的 `var` 字段，import 时手动赋值。破坏 domain model 不可变性。

**建议**: 将 `categoryId` 改为构造函数参数或在 UI 层单独传递。

### 4.8 `CollectionListViewModel` 暴露 `collectRepository` 为 public

**文件**: `ui/movielist/CollectionListViewModel.kt:57`

Repository 作为 `val`（public）暴露，破坏封装性。

**建议**: 改为 `private val`，测试时通过 Hilt 测试组件注入。

### 4.9 `HtmlClient` fetchHtml/fetchDocument 重复重试逻辑

**文件**: `core/http/HtmlClient.kt:27-55`

`fetchHtml` 和 `fetchDocument` 都有几乎相同的 driver-verify 重试逻辑。

**建议**: 提取为通用 `fetchWithVerifyFallback()` 方法。

### 4.10 `GifLoadTracker` DataStore 溢出处理

**文件**: `data/GifLoadTracker.kt:31-39`

`stringSetPreferencesKey` 不保证顺序。`takeLast` 删除的可能不是最旧记录。

**建议**: 使用 `listPreferencesKey` 替代，或改用 Room。

---

## 五、重复 UI 组件问题

### 5.1 重复的加载中/空态/错误态模式

以下模式在至少 **12 个 Screen 文件**中重复出现：

| 模式 | 出现次数 | 涉及文件 |
|------|---------|---------|
| `CircularProgressIndicator()` 居中加载 | 16处 | MovieListScreen, ActressListScreen, LinkMovieListScreen, ForumThreadListScreen, ForumThreadDetailScreen, ForumBoardsScreen, SearchScreen, MovieDetailScreen, CollectionListScreen, ImageViewScreen |
| `state.error ?: "內容為空"` + `"下拉刷新重試"` | 5处 | ForumThreadListScreen:204, ForumThreadDetailScreen:292, ForumBoardsScreen:111 |
| `"沒有更多了"` 底部提示 | 3处 | ActressGrid:100, MovieList:116, MovieList:175 |
| `"載入失敗，請重試"` ErrorView | 5处 | MovieListScreen:118, ActressListScreen:105, MovieDetailScreen:198, SearchScreen:265, CollectionListScreen:54 |
| TopAppBar + 返回按钮 | 8处 | 几乎所有二级页面 |

**建议**: 
- 提取 `LoadingView`、`EmptyStateView`、`ErrorRetryView` 通用组件
- 提取 `ScreenScaffold` 组件封装 TopAppBar + 返回按钮 + 内容区

### 5.2 重复的收藏按钮逻辑

以下位置都有相同的收藏/取消收藏按钮 + Toast 逻辑：
- `MovieDetailScreen.kt:165-175`
- `LinkMovieListScreen.kt:193-203`
- `MovieList.kt:450-456`

**建议**: 提取 `CollectButton` 组件。

### 5.3 重复的分享按钮逻辑

以下位置都有相同的分享 Intent 构建：
- `MovieDetailScreen.kt:145-164`
- `LinkMovieListScreen.kt:180-191`
- `ForumThreadDetailScreen.kt:149-159`
- `ImageViewScreen.kt:310-320`

**建议**: 提取 `shareText(context, text)` 工具函数 + `ShareButton` 组件。

---

## 六、多语言 / i18n 问题

### 6.1 无字符串资源化

**文件**: `app/src/main/res/values/strings.xml` — 仅包含 `app_name`

整个项目的 UI 文本全部以硬编码繁体中文写在 Kotlin 代码中。**未使用任何 `stringResource()` 或 `getString()` 调用**。

统计发现的硬编码中文字符串（去重后）：

| 类别 | 字符串示例 | 出现次数 |
|------|-----------|---------|
| 状态提示 | "載入中..."、"沒有數據"、"內容為空"、"載入失敗"、"下拉刷新重試"、"沒有更多了" | 32处 |
| 操作反馈 | "已複製"、"已複製標題"、"已複製磁力連結"、"收藏成功"、"已取消收藏" | 20+处 |
| 菜单/按钮 | "返回"、"分享"、"複製"、"關閉"、"篩選"、"導出收藏"、"導入收藏" | 30+处 |
| 页面标题 | "實驗室"、"我的收藏"、"搜索歷史"、"精彩评论"、"點評" | 10+处 |
| 分类标签 | "有碼"、"無碼"、"影片"、"演員"、"論壇"、"收藏" | 10+处 |
| 错误信息 | "導出失敗"、"導入失敗"、"搜尋失敗"、"未找到可處理的應用" | 10+处 |

**影响**:
1. 无法切换语言（即使系统语言切换，UI 始终显示繁体中文）
2. 无法在无障碍模式下被 TalkBack 正确朗读
3. 无法通过翻译工具批量翻译
4. 修改任何文案都需要改代码并重新编译

**建议**: 
- 将所有 UI 字符串提取到 `res/values-zh-rTW/strings.xml`
- 同时创建 `res/values/strings.xml`（英文默认值）
- 代码中使用 `stringResource(R.string.xxx)` 引用
- contentDescription 也应字符串资源化以支持无障碍

### 6.2 混用繁体和简体

部分字符串为繁体（"導出"、"導入"、"複製"、"實驗室"），部分为简体（"收藏成功"中的"功"、"返回"中的"回"），但总体偏繁体。

**建议**: 统一为繁体中文作为默认，简体作为 `res/values-zh-rCN/strings.xml` 翻译。

---

## 七、文件过长问题

| 文件 | 行数 | 问题 |
|------|------|------|
| `ForumViewModels.kt` | 822 | 3个ViewModel + 3个UiState + 3个diff函数 |
| `MovieDetailScreen.kt` | 833 | 含DetailContent + 5个section组件 + MagnetBottomSheet + MagnetItem |
| `ForumThreadDetailScreen.kt` | 603 | 含ThreadHeader + RepliesHeader + CommentsSection + ReplyItem + FloorContentDialog |
| `MainScreen.kt` | 378 | 4个Tab内容全部内联 |
| `MovieListViewModel.kt` | 429 | 含429行的loadFirstPage/revalidate/loadMore/refresh |
| `ForumPostParser.kt` | 318 | 文本解析逻辑复杂 |
| `LabSettingsStore.kt` | 303 | 含扫描/验证/排序逻辑 |
| `CollectionListViewModel.kt` | 287 | 含筛选/排序扩展函数 |
| `LinkMovieListScreen.kt` | 422 | 含完整的女优详情页 |
| `SearchScreen.kt` | 373 | 含搜索历史/结果/实验入口 |

**建议**: 按上述 4.5、4.6 节建议拆分。

---

## 八、类划分不合理问题

### 8.1 `ForumViewModels.kt` — 3 个 ViewModel 在同一文件

三个独立的 ViewModel（`ForumBoardsViewModel`、`ForumThreadListViewModel`、`ForumThreadDetailViewModel`）及其 UiState 全部放在一个文件中。

**建议**: 每个 ViewModel 独立一个文件。

### 8.2 `ForumPostParser.kt` — 解析器混合了内联样式处理

`PostContentParser` 和 `InlineParagraphParser` 两个类在同一文件中，且 `InlineStyle`、`InlineParagraphParser` 等辅助类也混在其中。

**建议**: 将 `InlineStyle` 和 `InlineParagraphParser` 提取为独立文件。

### 8.3 `LabSettingsStore.kt` — 职责过多

单个文件包含：
- DataStore 偏好读写（forumEnabled、autoLoadGifs、floorOrder、baseUrl）
- WebView 扫描逻辑（scanMirrorUrls）
- 并发验证逻辑（verifyUrlsParallel）
- URL 排序逻辑（sortMirrorUrls）
- 3 个数据类（MirrorUrl、ScanState、ScanPhase）
- ForumSettingsReader 接口

**建议**: 将扫描/验证逻辑提取到独立的 `MirrorScanner` 类；将 `ScanState`/`MirrorUrl` 提取到独立文件。

### 8.4 `CollectCategoryScreen.kt` — 包含导入导出逻辑

文件中包含 Activity Result Launcher、文件 I/O 操作和 Toast 反馈，这些本应在 ViewModel 中处理。

**建议**: 将导入导出逻辑完全移到 `CollectCategoryViewModel` 中。

### 8.5 `MovieListViewModel.kt` — genreUrl 混合职责

ViewModel 同时管理 `dataSourceType`（按类型加载）和 `genreUrl`（按 URL 加载）两种模式，通过 `if (genreUrl != null)` 分支处理。

**建议**: 拆分为 `MovieListViewModel`（按类型）和 `GenreMovieListViewModel`（按 URL），或使用策略模式。

---

## 九、架构建议

### 9.1 缺少统一的错误处理策略

各 ViewModel 错误处理方式不一致：有的显示 `error` 状态，有的显示 Snackbar，有的静默忽略（`catch (_: Exception) {}`）。

**建议**: 定义统一的 `ErrorEvent` 或使用 `Channel<Error>` 向 UI 层传递错误。

### 9.2 缺少单元测试覆盖的模块

以下核心模块缺少单元测试：
- `ForumSessionManager`（WebView 相关）
- `HtmlClient`（重试逻辑）
- `SiteConfig`（URL 解析）
- `CollectRepository`（数据库操作）
- `ForumPostParser`（论坛内容解析）
- `ForumRepository`（论坛数据）
- 所有 ViewModel 的 `revalidate` 和 `loadMore` 逻辑

### 9.3 缺少深链路由单元测试

`ModernMainActivity.resolveJavbusRoute()` 的各种 URL 路径（有码、无码、XYZ、分类、演员）无测试覆盖。

### 9.4 缺少 KDoc 文档

大部分公共 API 缺少 KDoc 文档。建议为 Repository 接口方法、Domain model 字段、ViewModel 状态转换补充文档。

---

## 十、已修复问题确认

以下问题在 `2026-05-28-code-review-remediation.md` 中已有修复计划，截至 2026-06-13 已全部核实实施：

| 问题 | 修复计划 | 状态 |
|------|---------|------|
| ForumSessionManager 恢复 Cookie 后未创建 WebView | Task 1 | ✅ 已修复（`ensureWebViewCreated()` + 恢复分支调用） |
| WebView 协程取消路径不完整 | Task 2 | ✅ 已修复（`WebViewHelper` 两处 + `ForumSessionManager` 的 `invokeOnCancellation` 清理） |
| Activity 销毁时未 destroy 浏览器会话 | Task 3 | ✅ 已修复（`BrowserSessionClient.destroy()` + `ModernMainActivity.onDestroy` 调用） |
| 搜索 URL 编码问题 | Task 4 | ✅ 已修复 |
| 收藏日期提取不一致 | Task 5 | ✅ 已修复（统一 `toCollectionMovie` 转换） |
| 收藏唯一性索引不含 dbType | Task 6 | ✅ 已修复（`[dbType, key]` 唯一索引 + DB v2 迁移） |
| FileCache key 哈希碰撞 | Task 7 | ✅ 已修复（SHA-256 文件名 + 旧 key 回退读取） |

---

## 十一、总结

| 优先级 | 数量 | 主要涉及 |
|-------|------|---------|
| P0 | 2 | Cookie 泄露、debug 模拟模式 |
| P1 | 6 | HTTP 状态码、深链、搜索竞态、runBlocking、Activity 引用、WebView 线程安全 |
| P2 | 10 | 代码重复、文件过大、封装性、DataStore 溢出 |
| 重复 UI | 3类 | 加载/空态/错误态、收藏按钮、分享按钮 |
| i18n | 严重 | 全部硬编码中文，无字符串资源化 |
| 文件过长 | 10个 | ForumViewModels(822行)、MovieDetailScreen(833行) 等 |
| 类划分 | 5处 | ForumViewModels 混放、LabSettingsStore 职责过多等 |
| 测试缺口 | 7个模块 | ForumSessionManager、HtmlClient、SiteConfig 等 |

**优先修复建议**:
1. P0: Cookie 泄露 + debug 模拟模式
2. P1: HTTP 状态码校验 + 搜索竞态 + runBlocking
3. i18n: 字符串资源化（影响面最广）
4. 重复 UI: 提取通用组件（减少约 30% 重复代码）
5. 文件拆分: ForumViewModels + MainScreen + MovieDetailScreen

---

## 十二、本轮修复进度（自 82436b0 起）

**更新日期**: 2026-06-13

### ✅ 已完成

| 分类 | 问题 | 处理 |
|------|------|------|
| P0 | 2.1 Cookie 泄露 | 改为从 `local.properties` / Gradle property / 环境变量注入，移除仓库内明文 |
| P0 | 2.2 debug 模拟缓存 | 改为 `-PcacheRefreshTestMode` 显式传入，仓库默认 false |
| P1 | 3.1 HTTP 状态码 / 3.2 深链 / 3.3+3.7 搜索竞态 / 3.4 runBlocking | 全部修复 |
| P1 | 3.5 Activity 引用 / 3.6 WebView 线程安全 | 生命周期校验 + `Mutex` 保护 |
| P2 | 4.1 toggle 原子化 / 4.2 并发上限 / 4.4 logDiff 泛型化 / 4.9 HtmlClient 重试去重 | 全部修复 |
| P2 | 4.5 ForumViewModels 拆分 / 4.6 MainScreen 拆分 | 每屏独立文件 |
| P2 | 4.7 domain model 不可变 / 4.8 封装性 / 4.10 GifLoadTracker 顺序存储 | 全部修复 |
| 重复 UI | 5.1 LoadingView/EmptyStateView/ErrorView / 5.2 CollectButton / 5.3 ShareButton | 通用组件已提取并迁移 |
| 类划分 | 8.1 ForumViewModels / 8.2 ForumPostParser / 8.3 MirrorScanner / 8.4 CollectCategoryScreen / 8.5 MovieListViewModel | 全部拆分/职责下沉（8.5 用 `MoviePageSource` 策略封装 by-type / by-url 两种加载模式） |
| §10 | Task 1–7（ForumSession/Cookie/索引/FileCache 等） | 已核实全部实施 |
| i18n | 6.1 字符串资源化基础 | 建 `strings.xml`(en) + `values-zh-rTW`，已迁移 11 个文件 |

### 🔄 进行中 / 部分完成

| 分类 | 问题 | 现状 |
|------|------|------|
| i18n | 6.1 | 已迁移 MainTabContent、StateViews、CollectButton、ShareButton、ForumThreadDetailScreen、LabSettingsScreen、SearchScreen、CollectCategoryScreen、CollectionFilterSheet、LinkMovieListScreen、ImageViewScreen；尚有 MovieDetailScreen 等约 20+ 文件（含 ViewModel 内文案，需经 `context.getString` 或事件化处理） |
| 文件过长 | 七 | MovieDetailScreen(833)、ForumThreadDetailScreen(603) 仍未拆分 |
| P2 | 4.3 SWR 四段式重复 | 提取 `core/cache/PagedSwrState` 工具（`PageTracker`/`AtTopGate`/`decideFreshRevalidate`），已迁移 MovieList/LinkMovieList/ActressList 三个分页 ViewModel；reducer 仍内联保留各 VM 字段差异。暂缓 ForumThreadList（`distinctBy`+typeFilters+首屏 pending 差异）、Genre/ForumBoards（非分页）、ForumThreadDetail（定制） |

### ⬜ 待处理

| 分类 | 问题 |
|------|------|
| i18n | 6.2 繁简混用统一（依赖 6.1 完成） |
| 架构 | 9.1 统一错误处理策略 |
| 测试/文档 | 9.2–9.4 ForumSessionManager/HtmlClient/SiteConfig/CollectRepository/ForumRepository 等单元测试与 KDoc |

