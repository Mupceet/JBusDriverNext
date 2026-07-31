# JBusDriver 代码检视闭环报告

**更新日期**: 2026-07-31
**检视范围**: `app/src/main`、`app/src/test`、Gradle 配置、架构整改记录
**检视目标**: 对照当前项目实际状态和 Android/Compose/Data layer/Coroutine 最佳实践，确认已闭环问题、剩余风险和后续优化优先级。

---

## 一、当前结论

当前没有发现新的 P0/P1 正确性问题，也没有发现会阻塞 debug/release 构建的 lint 或编译问题。Phase A/B/C 中面向正确性和架构边界的主要问题已经闭环；2026-07-31 复查轮（Phase D）进一步闭环了剩余的安全项与一批工程化问题：

1. 跨站点缓存隔离、`SiteConfig.awaitReady()`、搜索与列表请求竞态。
2. Forum WebView session 生命周期、线程边界和可等待销毁。
3. 收藏事务边界、导入回滚语义和 Hilt 数据库单入口。
4. 平台 IO 从 Composable 收口到 gateway/ViewModel。
5. movielist/forum stale-while-revalidate 状态更新迁移到 reducer，并增加纯函数测试。
6. lint/compiler 剩余项清理和依赖小版本升级。
7. `JAVBUS_AUTH_COOKIE` 构建支持移除（不再把会话凭证编入 release APK）。
8. Forum/Magnet 仓储补齐 `SiteConfig.awaitReady()`，消除冷启动默认域名竞态。
9. 剩余硬编码 UI 文案全部迁移到字符串资源。
10. 取消异常、loadMore 竞态、深链解析、FileCache 并发等一批 P3 加固。

后续不再是“必须立即修复的正确性缺陷”，而是持续工程化优化：继续拆分大文件、压缩 ViewModel 流程重复、补充 release minify smoke test。

---

## 二、已闭环问题

### Phase A

1. **SiteConfig 冷启动镜像恢复**
   `DefaultSiteConfig` 提供 `awaitReady()`，Repository 请求前等待配置 ready，并用同一个 baseUrl 快照构造 URL 与缓存 key。

2. **跨镜像缓存隔离**
   新增 `siteCacheKey(baseUrl, namespace, identity)`，网络派生缓存 key 包含站点身份，避免不同镜像共享旧数据。

3. **Search 请求竞态**
   `SearchViewModel` 使用 request generation + query/type identity，写回前统一校验，并重新抛出 `CancellationException`。

4. **Forum WebView session 生命周期**
   `ForumSessionManager.destroy()` 已改为 suspend API，调用方可等待销毁完成；`destroy()` 与 `fetchDocument()` 共用同一个 `Mutex` 边界。

5. **收藏事务边界**
   新增可注入 `CollectTransactionRunner`；收藏 toggle/import 通过注入事务执行，不再从 Repository 直接访问全局数据库入口。

6. **JVM URL 解析健壮性**
   `urlHost/urlPath` 在 Android `Uri` 不可用时回退到 `java.net.URI`，并保留无效 URL 抛错语义。

### Phase B

1. **List/Forum request generation**
   `LinkMovieListViewModel`、`MovieListViewModel`、`ForumThreadListViewModel` 已增加请求 identity，旧请求晚返回时不再覆盖新状态。

2. **LabSettings 取消语义**
   `LabSettingsViewModel` 对 `CancellationException` 重新抛出；扫描/验证取消不再映射为失败消息。

3. **收藏导入回滚语义**
   测试锁定“异常即整批回滚”：导入在单一事务内执行，任一 item 写入失败则整批回滚。

### Phase C

1. **剩余 List/Forum request identity**
   `ActressListViewModel`、`GenreListViewModel`、`ForumThreadDetailViewModel`、`ForumBoardsViewModel` 已补齐 request generation / identity。

2. **Screen Store 边界**
   `LabSettingsViewModel` 与 `UiPrefsViewModel` 提供语义化 UI state / intent 方法，Screen 不再直接访问 Store。

3. **数据库 Hilt 单入口**
   `DatabaseModule` 通过 `@ApplicationContext` 直接提供 `JBusDatabase` / `CollectDatabase`；`data/db/DB.kt` 已删除，生产代码未发现 `DB.xxx` 调用。

4. **`ILink.categoryId` 可变状态移除**
   domain model 不再暴露收藏分类；分类通过 `LinkItem.categoryId` 与 mapper 显式传递。

5. **收藏导入/导出平台 IO 收口**
   `CollectionDocumentGateway` 负责 `ContentResolver` 读写，Composable 不再直接处理文档 IO。

6. **图片保存/分享平台 IO 收口**
   `ImageMediaGateway` 与 `ImageActionsViewModel` 承接 MediaStore/FileProvider/bitmap 压缩逻辑。

7. **movielist SWR reducer 收口**
   `MovieList`、`LinkMovieList`、`ActressList`、`GenreList` 的 cached/fresh/failure 与 revalidate fresh 分支迁移到 reducer。

8. **LinkMovieList 女优头部子状态拆分**
   新增 `ActressHeaderState`，女优详情、加载、错误和收藏状态通过 `uiState.actressHeader` 渲染。

9. **Forum SWR reducer 收口**
   新增 `ForumBoardsStateReducers`、`ForumThreadListStateReducers`、`ForumThreadDetailStateReducers`，Forum cached/fresh/failure、pending/new-data 与 refresh failure 分支迁移到可单测纯函数。

10. **Lint 剩余项清理**
    修复 Compose modifier 参数顺序、Compose 状态读取、KTX API、Material3 deprecated alias、复数资源、未使用资源、Hilt qualifier 注解目标等 lint/compiler warning。

11. **依赖小版本升级**
    OkHttp 升级到 5.4.0，kotlinx-coroutines 升级到 1.11.0，KSP 升级到 2.3.9。Kotlin 2.4.0 已实测被当前 Hilt metadata 读取上限阻塞，暂缓升级，并在窄范围 lint ignore 中记录原因。

12. **全局 Context / Site URL 入口收口**
    删除 `lateinit var JBus`、`AppContext`、`JBusManager.context/setContext`、`CacheLoader` 与 `NetClient.defaultFastUrl/siteConfig` 兼容入口。`JBusApplication` 直接继承 `Application`；WebView 创建改由 `WebViewFactory` 注入 `@ApplicationContext`；默认缓存改由 `DefaultCacheStore` 注入 `@ApplicationContext`；收藏 mapper 显式接收 `baseUrl`；设置页通过 `SiteConfig` 更新运行时站点 URL。

13. **JBusManager 完全移除**
    复查确认没有生产代码读取 Activity tracker 后，删除 `JBusManager`、对应生命周期注册和单测。应用不再维护全局 Activity 弱引用列表。

---

### Phase D（2026-07-31）

1. **`JAVBUS_AUTH_COOKIE` 构建支持移除（安全）**
   删除 BuildConfig 字段、`local.properties`/环境变量读取与 OkHttp `bus_auth` 快路径；页面 HTML 一律走共享 WebView 会话，ajax 端点保留 OkHttp + WebView 回退。修复了该 cookie 会被编入 release APK 的问题，并同步更新 `gradle.properties` 与学习文档。

2. **Forum/Magnet 站点配置就绪**
   `DefaultForumRepository` 的 observe*/loadFloorComments 与 `DefaultMagnetRepository.fetchMagnets` 补齐 `siteConfig.awaitReady()`，并在就绪后再构建 URL/缓存 key，消除冷启动时用默认域名请求的竞态（与 Movie/Search/Detail 仓储对齐）。

3. **UI 硬编码文案迁移**
   迁移剩余 6 处硬编码 UI 文案（SettingsScreen、CollectCategoryScreen、ForumThreadDetailScreen）到字符串资源，中/英文案同步补齐。

4. **取消异常一致性**
   `MovieDetailViewModel` 与其余列表 ViewModel 的 `catch (Exception)` 统一先重抛 `CancellationException`，避免作用域取消被映射为错误消息/状态写入。

5. **loadMore 竞态**
   6 个列表 ViewModel 的 loadMore/loadMoreReplies 在启动协程前同步置位 `isLoadingMore`，防止快速连点重复请求同一页。

6. **调试与并发加固**
   `logListDiff` 在 release 下跳过整个 diff 计算（避免主线程无谓开销）；`FileCache` 读写/trim 加 `@Synchronized`；深链解析用 `runCatching` 兜底；`@IoDispatcher` 显式限定参数目标；删除 CategoryBottomSheet 注释死代码；修正 `setDataSourceType` 误导性 KDoc。

7. **收藏观察查询下推**
   `LinkItemDao.listByTypeFlow(dbType)` Flow 查询替代全表读取后的内存过滤。
---

## 三、当前仍成立的问题

这些问题仍存在，但按当前证据不属于阻塞发布的正确性缺陷。

### 3.1 UI i18n 剩余硬编码文案已迁移（2026-07-31 闭环）

**位置**: `ui/**`

Phase D 已把剩余硬编码用户可见文案（`SettingsScreen` 的设置/主题模式/楼层浏览顺序/影片列表样式、`CollectCategoryScreen` 的更多设置、`ForumThreadDetailScreen` 的返回）迁移到 `values/strings.xml` + `values-en/strings.xml`。复查未发现新的硬编码 UI 文案。

**约定**: 新增 UI 文案继续使用字符串资源；计数类文案使用 `plurals`；服务端/domain model 提供的标题、分类名保持原样，不强行资源化。

### 3.2 大文件和 ViewModel 流程重复仍存在

**位置示例**:

1. `ui/components/MovieList.kt` 约 501 行。
2. `ui/forum/ForumPostContent.kt` 约 493 行。
3. `ui/movielist/LinkMovieListViewModel.kt` 约 470 行。
4. `ui/movielist/LinkMovieListScreen.kt` 约 465 行。
5. `ui/detail/MovieDetailScreen.kt` 约 458 行。
6. `ui/movielist/MovieListViewModel.kt` 约 449 行。
7. `ui/settings/LabSettingsScreen.kt` 约 436 行。
8. `data/MovieRepository.kt` 约 436 行。
9. `ui/forum/ForumBoardsScreen.kt` 约 435 行。
10. `ui/forum/ForumThreadDetailViewModel.kt` 约 403 行。

Reducer 已降低状态分支复杂度，但 `loadFirstPage/revalidate/loadMore/refresh` 这类协程流程在多个 ViewModel 中仍有相似结构。

**建议**: 只在真实重复稳定后抽象，优先拆纯 UI section 和可复用 reducer/helper；避免一次性做横切式大重构。

### 3.3 AGENTS.md 中的 Code Review Notes 已更新

**位置**: `AGENTS.md`

`AGENTS.md` 已移除 “Known Architectural Issues” 旧清单，改为当前状态和剩余非阻塞技术债，避免新会话把已闭环问题误判为现状。

**建议**: 后续每次闭环架构问题时同步更新 `docs/CODE_REVIEW.md` 与 `AGENTS.md`。

---

## 四、验证证据

最近一次本地质量门槛已通过：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
./gradlew.bat assembleRelease --console=plain
git diff --check
```

本轮复查补充执行：

```bash
rg -n "runBlocking|lateinit var JBus|object DB|DB\.|ContentResolver|Uri\.parse|Color\.parseColor|MenuAnchorType|@ApplicationContext" app/src/main/java app/src/test/java docs AGENTS.md
rg -n "lateinit var JBus|AppContext|JBusManager|NetClient\.defaultFastUrl|NetClient\.siteConfig|CacheLoader" app/src/main/java app/src/test/java
```

结论：

1. 生产代码未发现 `runBlocking`、`object DB`、`DB.xxx`、旧 `Uri.parse`、旧 `Color.parseColor`、旧 `MenuAnchorType` 回归。
2. `lateinit var JBus`、`AppContext`、`JBusManager`、`NetClient.defaultFastUrl`、`NetClient.siteConfig`、`CacheLoader` 未在生产代码中继续作为入口存在。

2026-07-31 Phase D 补充执行（全部通过）：

```bash
./gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain
./gradlew.bat assembleRelease --console=plain
git diff --check
```

补充核查：`JAVBUS_AUTH_COOKIE`/`bus_auth` 在生产代码（`app/src`）已无引用；`catch (e: Exception)` 均伴随 `CancellationException` 重抛（独立 catch 分支或内联 `if (e is CancellationException) throw e`）。

---

## 五、发布前建议门槛

合并前：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

发布前：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleRelease --console=plain
```

如果后续改动触及 R8/Gson/Forum 富文本反序列化，建议补一个 release minify smoke test，覆盖 `ContentBlock`、收藏导入 JSON、详情页缓存反序列化。

---

## 六、下一步优先级

1. 继续拆分大文件中的纯 UI section（`MovieList.kt`、`ForumPostContent.kt`、`LinkMovieListViewModel.kt`、`SettingsScreen.kt` 等），保持小步提交和单测覆盖。
2. 若改动触及 R8/Gson/Forum 富文本，补充 release minify smoke test（覆盖 `ContentBlock`、收藏导入 JSON、详情页缓存反序列化）。
3. 观察 ViewModel `loadFirstPage/revalidate/loadMore/refresh` 重复结构，仅在稳定后抽象共享 shape。
