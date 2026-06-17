# JBusDriver 代码检视报告

**更新日期**: 2026-06-17
**检视范围**: `app/src/main`、`app/src/test`、Gradle/CI 配置、现有架构整改计划
**检视目标**: 结合当前项目实际状态，对照 Android 推荐架构、Compose UDF、Data layer、DI、协程取消与测试最佳实践，记录仍然成立的问题和优先级。

> 本文记录当前代码状态。Phase A 已开始落地，已修复项不再作为待办问题列入 P1。

---

## 一、总体结论

项目当前更准确的架构定位是：

> **单模块 MVVM + Jetpack Compose + StateFlow/UDF + Repository + Hilt**

这个方向与 Android 推荐架构一致。当前不建议为了 “Clean Architecture” 或 “MVI” 标签强行拆多模块、强行增加空转 use case，或把所有页面一次性改成统一 Action 框架。

当前仍影响质量的风险集中在四类：

1. **UI 边界仍可被绕过**：Screen 直接访问 Store，UI 层可见 Room entity。
2. **列表状态机重复**：Movie/List/Forum/Link 列表仍有旧请求、刷新、加载更多互相覆盖的空间。
3. **domain model 仍混入 UI/序列化/收藏可变状态**：`ILink.categoryId` 使多个 `@Immutable` 模型实际可变。
4. **测试真实性仍不均衡**：部分测试仍在验证 Fake 自身，而不是生产 Repository、Room SQL、缓存契约和取消传播。

已有修正应明确保留：Release workflow 已执行 `testDebugUnitTest lintDebug assembleRelease`；`Category` 已改为 data class；`ContentBlockTypeAdapter` ProGuard keep 路径已修正；`GifLoadTracker.removeFirst()` 当前未再命中。

---

## 二、Phase A 已修复

1. **SiteConfig 冷启动镜像恢复**：`DefaultSiteConfig` 增加 `awaitReady()`，通过 `SitePreferenceSource.currentSelectedBaseUrl()` 直接读取持久化值；Repository 请求前等待 ready，并使用同一个 baseUrl 快照构造 URL 与缓存 key。
2. **跨镜像缓存隔离**：新增 `siteCacheKey(baseUrl, namespace, identity)`，Movie/List、Search、MovieDetail、ActressDetail 等网络派生缓存 key 包含站点身份。
3. **Search 请求竞态**：`SearchViewModel` 增加 request generation + query/type identity；`search/refresh/loadMore/clearSearch` 写回前统一校验，并重新抛出 `CancellationException`。
4. **Forum WebView session 生命周期**：`ForumBoardsViewModel` 不再销毁应用级 session；`ForumSessionManager.destroy()` 切到 Main scope，并与 `fetchDocument()` 共用同一个 `Mutex` 边界。
5. **收藏事务边界**：新增可注入 `CollectTransactionRunner`，`toggleMovieCollect/toggleActressCollect/importCollectionsFromJson` 使用注入事务，不再从 Repository 直接访问全局 `DB.collectDatabase`。
6. **JVM URL 解析健壮性**：`urlHost/urlPath` 在 Android `Uri` 不可用时回退到 `java.net.URI`，避免 JVM unit test 的 Android stub 返回 null 后写入 non-null LRU。

覆盖测试：

```bash
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.core.site.SiteConfigTest" --tests "me.jbusdriver.modern.data.SiteCacheKeyTest" --tests "me.jbusdriver.modern.ui.search.SearchViewModelTest" --tests "me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest" --tests "me.jbusdriver.modern.data.CollectRepositoryTest" --console=plain
./gradlew.bat testDebugUnitTest --console=plain
```

---

## 三、P1：优先处理的正确性风险

### 3.1 List/Forum 分页请求仍缺少统一请求身份

**位置**: `ui/movielist/LinkMovieListViewModel.kt`、`ui/movielist/MovieListViewModel.kt`、Forum 列表相关 ViewModel

Search 已完成 request generation 修复，但多个列表 ViewModel 仍由首屏、revalidate、refresh、loadMore 分散维护状态。切换 source/filter/showAll 后，旧请求晚返回时仍可能污染新列表。

**建议**: 先选 `LinkMovieListViewModel` 作为代表页，引入 generation + source/filter identity。所有写回前校验 identity；`catch (Exception)` 中先重新抛出 `CancellationException`。验证通过后再迁移 Movie/Actress/Forum 列表。

### 3.2 LabSettings 取消扫描会被映射成失败

**位置**: `ui/settings/LabSettingsViewModel.kt`

`cancelScan()` 会取消 Job 并重置状态，但 `startScan()` / `startVerify()` 捕获所有 `Exception`。协程取消属于 `CancellationException`，如果被捕获并转成失败消息，用户取消会被误报。

**建议**:

```kotlin
catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // map real failure
}
```

### 3.3 收藏导入语义仍需产品确认

**位置**: `data/CollectRepository.kt`

导入现在处于单个事务中，但产品语义还应明确：遇到坏数据时是整批回滚，还是跳过坏项并报告部分成功。当前实现更接近“异常即回滚”，需要用测试锁定。

**建议**: 为新格式、旧格式、重复项、坏 JSON、字段缺失、事务回滚分别补测试。若目标是部分成功，应在解析层显式收集 item-level error，而不是依赖异常中断。

---

## 四、P2：架构边界与可维护性问题

### 4.1 Screen 仍直接访问 DataStore/Store

**位置**: `ui/MainScreen.kt`、`ui/settings/LabSettingsScreen.kt`、`ui/search/SearchScreen.kt`、`ui/movielist/*Screen.kt`

多个 Screen 通过 `hiltViewModel<...>().store` 直接 collect Store Flow 或启动协程写 DataStore。`LabSettingsViewModel` 与 `UiPrefsViewModel` 也暴露 Store。这绕过 screen-level state holder，使 UI 层直接依赖 data source。

**建议**: ViewModel 暴露单一 `UiState` 和语义方法，例如 `setGrid()`、`setForumEnabled()`、`selectBaseUrl()`。Screen 只收集 ViewModel state，不直接接触 Store。

### 4.2 Data layer 仍存在全局 DB 与 Hilt 双轨

**位置**: `data/db/DB.kt`、`data/di/DatabaseModule.kt`

`CollectRepository` 已从全局 `DB` 迁出，但项目仍保留 `object DB` 懒加载和 Hilt `DatabaseModule` 双入口。长期会削弱测试替换能力，也让数据库 owner 难判断。

**建议**: 逐步收敛到 Hilt 单入口：`DatabaseModule` 使用 `Room.databaseBuilder(@ApplicationContext)` 提供 DB，Repository 只注入 DB/DAO。`object DB` 仅在迁移期作为兼容层。

### 4.3 `ILink.categoryId` 破坏 domain model 不可变性

**位置**: `domain/model/ILink.kt`、`domain/model/Movie.kt`、`domain/model/MovieDetail.kt`、`domain/model/PageLink.kt`、`domain/model/Magnet.kt`

多个 domain model 标注 `@Immutable`，但继承 `ILink` 后暴露 `var categoryId`。收藏分类是用户数据/数据库元数据，不属于影片、演员、磁力链接这些内容模型本身。

**建议**: `ILink` 只保留 `val link: String`。收藏元数据使用 `CollectedEntry<T>(item, categoryId, createdAtMillis)` 或 mapper 参数传递。

### 4.4 大型 ViewModel 与重复 SWR 状态机仍未收口

**位置**: `LinkMovieListViewModel.kt`、`MovieListViewModel.kt`、`ActressListViewModel.kt`、Forum 多个 ViewModel

`PagedSwrState` 已抽出部分 page tracker / at-top / fresh decision，但 loading/error/pending/revalidate/loadMore 的状态归约仍散落在多个 ViewModel。

**建议**: 抽取纯函数 reducer 或小型 state producer，先迁移一个代表性列表，再逐页推进。`LinkMovieListViewModel` 至少拆成 `LinkedMoviesState` 与 `ActressHeaderState` 两个子状态。

### 4.5 UI 层仍承担平台 IO 和媒体流程

**位置**: `ui/image/ImageViewScreen.kt`、`ui/movielist/CollectCategoryScreen.kt`

Composable 中直接执行 ContentResolver、MediaStore、FileProvider、文件读写、bitmap 压缩和异常映射。这些逻辑难测，且把平台 IO 与 UI 组合生命周期绑在一起。

**建议**: Activity Result launcher 可留在 Screen；实际文件读写、图片保存、分享文件准备放到 `CollectionDocumentGateway` / `ImageMediaGateway` 这类注入组件。结果归约为 ViewModel 中可确认消费的 `UserMessage`。

---

## 五、测试与质量门槛

优先补齐以下测试：

1. Movie/Link/Forum 列表切换 source/filter/showAll 后，旧请求不能写回。
2. LabSettings 取消扫描/验证不产生失败消息。
3. 收藏导入坏数据时的回滚或部分成功语义。
4. `ILink.categoryId` 移除后的收藏 mapper 回归测试。
5. minified release 对 Gson/Forum `ContentBlock` 反序列化的 smoke test。

建议本地合并前质量门槛：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

发布前质量门槛：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleRelease --console=plain
```

---

## 六、Phase B 建议顺序

1. 修复 List/Forum request generation 与取消传播。
2. 修复 LabSettings 取消语义。
3. 锁定收藏导入坏数据语义并补回滚测试。
4. 收敛 Screen 直接访问 Store 的入口。
5. 迁移剩余全局 `DB` 使用点。
6. 处理 `ILink.categoryId` 可变 domain 状态。
