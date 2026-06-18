# JBusDriver 代码检视报告

**更新日期**: 2026-06-18
**检视范围**: `app/src/main`、`app/src/test`、Gradle/CI 配置、现有架构整改计划
**检视目标**: 结合当前项目实际状态，对照 Android 推荐架构、Compose UDF、Data layer、DI、协程取消与测试最佳实践，记录仍然成立的问题和优先级。

---

## 一、总体结论

项目当前更准确的架构定位是：

> **单模块 MVVM + Jetpack Compose + StateFlow/UDF + Repository + Hilt**

这个方向与 Android 推荐架构一致。当前不建议为了 “Clean Architecture” 或 “MVI” 标签强行拆多模块、强行增加空转 use case，或把所有页面一次性改成统一 Action 框架。

当前仍影响质量的风险集中在两类：

1. **架构重复仍偏多**：列表 SWR reducer、一次性事件和局部 UI state producer 还没有统一收口。
2. **大型 UI/ViewModel 文件仍偏重**：Forum、Detail 与部分列表页面仍混合较多状态归约、事件处理和渲染细节，后续拆分应优先围绕真实复用点推进。

---

## 二、Phase A 已修复

1. **SiteConfig 冷启动镜像恢复**：`DefaultSiteConfig` 增加 `awaitReady()`，通过 `SitePreferenceSource.currentSelectedBaseUrl()` 直接读取持久化值；Repository 请求前等待 ready，并使用同一个 baseUrl 快照构造 URL 与缓存 key。
2. **跨镜像缓存隔离**：新增 `siteCacheKey(baseUrl, namespace, identity)`，Movie/List、Search、MovieDetail、ActressDetail 等网络派生缓存 key 包含站点身份。
3. **Search 请求竞态**：`SearchViewModel` 增加 request generation + query/type identity；`search/refresh/loadMore/clearSearch` 写回前统一校验，并重新抛出 `CancellationException`。
4. **Forum WebView session 生命周期**：`ForumBoardsViewModel` 不再销毁应用级 session；`ForumSessionManager.destroy()` 改为可等待的 suspend API，并与 `fetchDocument()` 共用同一个 `Mutex` 边界。
5. **收藏事务边界**：新增可注入 `CollectTransactionRunner`，`toggleMovieCollect/toggleActressCollect/importCollectionsFromJson` 使用注入事务，不再从 Repository 直接访问全局 `DB.collectDatabase`。
6. **JVM URL 解析健壮性**：`urlHost/urlPath` 在 Android `Uri` 不可用时回退到 `java.net.URI`；`urlHost` 仍保留无效 URL 抛错语义。

---

## 三、Phase B 已修复

1. **List/Forum request generation**：`LinkMovieListViewModel`、`MovieListViewModel`、`ForumThreadListViewModel` 增加请求 identity。旧 `refresh/loadMore/revalidate` 晚返回时，不再覆盖 source/filter/showAll/typeId 切换后的新列表。
2. **LabSettings 取消语义**：`LabSettingsViewModel` 对 `CancellationException` 重新抛出，取消扫描/验证不再映射为失败消息。新增 `LabSettingsStoreContract` 使 ViewModel 可用 fake store 测试。
3. **收藏导入回滚语义**：新增回归测试锁定当前语义：导入在单个事务内执行，任一 item 写入失败时整批回滚，不留下部分导入结果。

关键新增/扩展测试：

```bash
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.LinkMovieListViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.MovieListViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.settings.LabSettingsViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.data.CollectRepositoryTest" --console=plain
```

---

## 四、Phase C 已修复

1. **剩余 List/Forum request identity**：`ActressListViewModel`、`GenreListViewModel`、`ForumThreadDetailViewModel`、`ForumBoardsViewModel` 已补齐 request generation / identity。旧 `refresh/loadMore/revalidate/loadDetail` 晚返回时会被丢弃，不再覆盖新状态。
2. **Screen Store 边界**：`LabSettingsViewModel` 新增 `LabSettingsUiState` 与 `setForumEnabled/setAutoLoadGifs/setForumFloorOrder/selectUrl` 等语义方法；`UiPrefsViewModel` 新增 `UiPrefsUiState` 与 `setGrid/toggleGrid`。相关 Screen 不再直接访问 `LabSettingsStoreContract` 或 `UiPrefsStore`。
3. **数据库 Hilt 单入口推进**：新增 `RoomDatabaseFactory` 复用 Room 构建和迁移定义；`DatabaseModule` 改为通过 `@ApplicationContext` 直接提供 `JBusDatabase` / `CollectDatabase`。`object DB` 保留为遗留兼容层，不再作为 Hilt provider 的来源。
4. **`ILink.categoryId` 可变状态移除**：`ILink` 只保留 `val link`，Movie/Actress/Genre/Header/PageLink/Magnet 等 domain model 不再暴露收藏分类。收藏分类改由 `LinkItem.categoryId`、`convertDBItem(categoryId)` 参数和导出 mapper 显式传递。
5. **收藏导入/导出平台 IO 收敛**：新增 `CollectionDocumentGateway`，`CollectCategoryViewModel` 负责导入/导出 document URI 流程；`CollectCategoryScreen` 只保留 Activity Result launcher 和结果提示，不再直接读写 `ContentResolver`。
6. **图片保存/分享平台 IO 收敛**：新增 `ImageMediaGateway` 与 `ImageActionsViewModel`，图片查看页只触发保存/分享 intent 并消费消息；MediaStore、FileProvider、bitmap 压缩和异常映射移出 Composable。
7. **MovieList SWR reducer 代表性迁移**：新增 `MovieListStateReducers`，将首页 cached/fresh/failure 与 revalidate fresh 状态归约抽成可单测纯函数；`MovieListViewModel` 保留请求 identity、日志和分页副作用。
8. **LinkMovieList 列表 SWR reducer 迁移**：新增 `LinkMovieListStateReducers`，将关联影片列表的首页加载、revalidate fresh 与 breadcrumb title 归约移出 ViewModel；女优详情/收藏子状态仍保留在后续拆分范围内。

关键新增/扩展测试：

```bash
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.ActressListViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.GenreListViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumThreadDetailViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumCacheRefreshViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.settings.LabSettingsViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.settings.UiPrefsViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.image.ImageActionsViewModelTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.MovieListStateReducerTest" --console=plain
./gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.movielist.LinkMovieListStateReducerTest" --console=plain
```

---

## 五、P1：剩余正确性风险

### 5.1 收藏导入坏数据的产品语义仍需确认

**位置**: `data/CollectRepository.kt`

当前已经用测试锁定“异常即整批回滚”。如果产品希望“跳过坏项并报告部分成功”，需要显式调整解析层和结果模型，而不是依赖异常中断。

**建议**: 保持当前回滚语义，除非 UI 需要展示 item-level import error。

---

## 六、P2：架构边界与可维护性问题

### 6.1 `object DB` 仍作为迁移期兼容层存在

**位置**: `data/db/DB.kt`、`data/di/DatabaseModule.kt`

`DatabaseModule` 已改为通过 `@ApplicationContext` 直接构建数据库，Repository/DAO 注入走 Hilt。`object DB` 仍保留公开属性，作为遗留调用点兼容层。

**建议**: 后续新增代码禁止使用 `DB.xxx`。确认无遗留调用后，可删除 `object DB` 或将其降级到 debug/test-only 辅助。

### 6.2 大型 ViewModel 与重复 SWR 状态机仍需继续收口

**位置**: `ActressListViewModel.kt`、`GenreListViewModel.kt`、Forum 多个 ViewModel；`LinkMovieListViewModel.kt` 的女优详情/收藏子状态

`MovieListViewModel` 和 `LinkMovieListViewModel` 的列表 SWR 归约已迁到 reducer 文件，但 `ActressListViewModel`、`GenreListViewModel` 和 Forum 多个 ViewModel 仍保留相似的 loading/error/pending/revalidate/loadMore 分支。`LinkMovieListViewModel` 也仍混有女优详情与收藏状态。

**建议**: 沿用 reducer 方式逐页迁移，下一步优先处理 `ActressListViewModel` 或将 `LinkMovieListViewModel` 的 `ActressHeaderState` 独立出来。

---

## 七、测试与质量门槛

优先补齐以下测试：

1. 剩余列表/详情 ViewModel 的旧请求隔离测试。
2. `ILink.categoryId` 移除后的收藏 mapper 回归测试。
3. minified release 对 Gson/Forum `ContentBlock` 反序列化的 smoke test。
4. Screen 不直接访问 Store 后的 UI state / intent 测试。

建议本地合并前质量门槛：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

发布前质量门槛：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleRelease --console=plain
```

---

## 八、后续建议顺序

1. 抽取 SWR reducer / state producer，降低 ViewModel 重复。
2. 继续拆分大型 Forum / Detail ViewModel 与 Screen 文件。
