# JBusDriver 代码检视闭环报告

**更新日期**: 2026-06-19
**检视范围**: `app/src/main`、`app/src/test`、Gradle 配置、架构整改记录
**检视目标**: 对照当前项目实际状态和 Android/Compose/Data layer/Coroutine 最佳实践，确认已闭环问题、剩余风险和后续优化优先级。

---

## 一、当前结论

当前没有发现新的 P0/P1 正确性问题，也没有发现会阻塞 debug/release 构建的 lint 或编译问题。Phase A/B/C 中面向正确性和架构边界的主要问题已经闭环：

1. 跨站点缓存隔离、`SiteConfig.awaitReady()`、搜索与列表请求竞态。
2. Forum WebView session 生命周期、线程边界和可等待销毁。
3. 收藏事务边界、导入回滚语义和 Hilt 数据库单入口。
4. 平台 IO 从 Composable 收口到 gateway/ViewModel。
5. movielist/forum stale-while-revalidate 状态更新迁移到 reducer，并增加纯函数测试。
6. lint/compiler 剩余项清理和依赖小版本升级。

后续不再是“必须立即修复的正确性缺陷”，而是持续工程化优化：减少全局状态、推动 i18n、继续拆分大文件、压缩 ViewModel 流程重复。

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
    删除 `lateinit var JBus`、`JBusManager.context/setContext`、`CacheLoader` 与 `NetClient.defaultFastUrl/siteConfig` 兼容入口。WebView 创建改由 `WebViewFactory` 注入 `@ApplicationContext`；默认缓存改由 `DefaultCacheStore` 注入 `@ApplicationContext`；收藏 mapper 显式接收 `baseUrl`；设置页通过 `SiteConfig` 更新运行时站点 URL。

13. **JBusManager 可变列表收口**
    `JBusManager.manager` 不再作为公开可变列表暴露；内部 Activity 弱引用列表改为私有实现细节，仅提供 `currentActivity` 与 `activeActivityCount` 只读查询 API，并用单测锁定不再生成公开 `getManager()`。

---

## 三、当前仍成立的问题

这些问题仍存在，但按当前证据不属于阻塞发布的正确性缺陷。

### 3.1 UI i18n 尚未系统完成

**位置**: `ui/**`

已有部分字符串迁移到 `strings.xml`，但仍能在 UI 层看到硬编码中文/英文文案，例如 `ForumThreadDetailScreen` 中的返回与复制提示、部分 icon contentDescription，以及若干 Toast/Dialog 文案。

**建议**: 按屏幕分批迁移到资源：

1. 优先迁移 Toast、contentDescription、Dialog/Button 文案。
2. 对计数类文案继续使用 `plurals`。
3. 对来自服务端或 domain model 的标题/分类名保持原样，不强行资源化。

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
rg -n "lateinit var JBus|JBusManager\.context|JBusManager\.setContext|NetClient\.defaultFastUrl|NetClient\.siteConfig|CacheLoader" app/src/main/java app/src/test/java
```

结论：

1. 生产代码未发现 `runBlocking`、`object DB`、`DB.xxx`、旧 `Uri.parse`、旧 `Color.parseColor`、旧 `MenuAnchorType` 回归。
2. `lateinit var JBus`、`JBusManager.context`、`JBusManager.setContext`、`JBusManager.manager`、`NetClient.defaultFastUrl`、`NetClient.siteConfig`、`CacheLoader` 未在生产代码中继续作为入口存在。`JBusManager` 仅保留 Activity lifecycle tracking 和只读查询 API。

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

1. 分屏幕推进 UI 文案资源化，优先 Toast、Dialog、contentDescription。
2. 继续拆分大文件中的纯 UI section，保持小步提交和单测覆盖。
