# JBusDriver 代码检视闭环报告

**更新日期**: 2026-06-19
**检视范围**: `app/src/main`、`app/src/test`、Gradle/CI 配置、架构整改计划
**检视目标**: 结合当前项目实际状态，对照 Android 推荐架构、Compose UDF、Data layer、DI、协程取消与测试最佳实践，记录已经完成闭环的问题、验证证据和后续可选优化。

---

## 一、总体结论

项目当前架构定位保持为：

> **单模块 MVVM + Jetpack Compose + StateFlow/UDF + Repository + Hilt**

这个方向与 Android 推荐架构一致。当前 Phase A/B/C 的主要正确性和架构边界问题已经闭环：请求竞态、跨站点缓存、WebView session 生命周期、收藏事务、平台 IO、数据库入口、列表/论坛 SWR 状态归约等问题均已修复或测试锁定。

后续工作应从“纠错”转为“持续简化”：大型 Screen 文件仍可按真实复用点继续拆分，但当前不再承载未闭环的正确性或架构边界问题。

---

## 二、Phase A 已修复

1. **SiteConfig 冷启动镜像恢复**：`DefaultSiteConfig` 增加 `awaitReady()`；Repository 请求前等待 ready，并使用同一个 baseUrl 快照构造 URL 与缓存 key。
2. **跨镜像缓存隔离**：新增 `siteCacheKey(baseUrl, namespace, identity)`，网络派生缓存 key 包含站点身份。
3. **Search 请求竞态**：`SearchViewModel` 增加 request generation + query/type identity，写回前统一校验并重新抛出 `CancellationException`。
4. **Forum WebView session 生命周期**：`ForumSessionManager.destroy()` 改为可等待的 suspend API，并与 `fetchDocument()` 共用同一个 `Mutex` 边界。
5. **收藏事务边界**：新增可注入 `CollectTransactionRunner`，收藏 toggle/import 使用注入事务，不再从 Repository 直接访问全局数据库入口。
6. **JVM URL 解析健壮性**：`urlHost/urlPath` 在 Android `Uri` 不可用时回退到 `java.net.URI`，并保留无效 URL 抛错语义。

---

## 三、Phase B 已修复

1. **List/Forum request generation**：`LinkMovieListViewModel`、`MovieListViewModel`、`ForumThreadListViewModel` 增加请求 identity，旧请求晚返回时不再覆盖新状态。
2. **LabSettings 取消语义**：`LabSettingsViewModel` 对 `CancellationException` 重新抛出；扫描/验证取消不再映射为失败消息。
3. **收藏导入回滚语义**：测试锁定“异常即整批回滚”：导入在单一事务内执行，任一 item 写入失败则整批回滚。

---

## 四、Phase C 已修复

1. **剩余 List/Forum request identity**：`ActressListViewModel`、`GenreListViewModel`、`ForumThreadDetailViewModel`、`ForumBoardsViewModel` 已补齐 request generation / identity。
2. **Screen Store 边界**：`LabSettingsViewModel` 与 `UiPrefsViewModel` 提供语义化 UI state / intent 方法，Screen 不再直接访问 Store。
3. **数据库 Hilt 单入口**：`DatabaseModule` 通过 `@ApplicationContext` 直接提供 `JBusDatabase` / `CollectDatabase`；确认无 `DB.xxx` 调用后删除遗留 `object DB`。
4. **`ILink.categoryId` 可变状态移除**：domain model 不再暴露收藏分类，分类通过 `LinkItem.categoryId` 与 mapper 显式传递。
5. **收藏导入/导出平台 IO 收口**：新增 `CollectionDocumentGateway`，Composable 不再直接读写 `ContentResolver`。
6. **图片保存/分享平台 IO 收口**：新增 `ImageMediaGateway` 与 `ImageActionsViewModel`，MediaStore/FileProvider/bitmap 压缩移出 Composable。
7. **movielist SWR reducer 收口**：`MovieList`、`LinkMovieList`、`ActressList`、`GenreList` 的 cached/fresh/failure 与 revalidate fresh 分支已迁移到 reducer 文件。
8. **LinkMovieList 女优头部子状态拆分**：新增 `ActressHeaderState`，女优详情、加载、错误和收藏状态通过 `uiState.actressHeader` 渲染。
9. **Forum SWR reducer 收口**：新增 `ForumBoardsStateReducers`、`ForumThreadListStateReducers`、`ForumThreadDetailStateReducers`，Forum cached/fresh/failure、pending/new-data 与 refresh failure 分支已迁移到可单测纯函数。
10. **Lint 剩余项清理**：修复 Compose modifier 参数顺序、Compose 资源读取、KTX API、Material3 deprecated alias、复数资源、未使用资源、Hilt qualifier 注解目标等 lint/compiler warning；`lintDebug` 当前无 error/warning。
11. **依赖小版本升级**：OkHttp 升级到 5.4.0，kotlinx-coroutines 升级到 1.11.0，KSP 升级到 2.3.9，并通过 debug 编译/lint 验证。Kotlin 2.4.0 已实测被当前 Hilt metadata 读取上限阻塞，暂缓升级并用窄范围 lint ignore 记录原因。

---

## 五、正确性风险闭环

### 5.1 收藏导入坏数据语义

**位置**: `data/CollectRepository.kt`

当前产品语义已锁定为“异常即整批回滚”。如果未来要支持“跳过坏项并报告部分成功”，应作为新需求显式修改解析层、结果模型和 UI 展示，而不是依赖异常中断。

### 5.2 数据库全局入口

**位置**: `data/di/DatabaseModule.kt`、`data/db/JBusDatabase.kt`、`data/db/CollectDatabase.kt`

生产入口已统一为 Hilt provider；`data/db/DB.kt` 已删除。数据库类 KDoc 与 `AGENTS.md` 已同步移除 DB 单例描述。

### 5.3 Forum / movielist 状态归约

**位置**: `ui/movielist/*StateReducers.kt`、`ui/forum/*StateReducers.kt`

ViewModel 保留 Repository 调用、日志、分页和请求 identity 副作用；状态更新由 reducer 负责，并通过纯函数测试覆盖。

---

## 六、关键验证

已新增或扩展的关键测试：

1. 列表/详情 ViewModel 旧请求隔离测试。
2. 收藏 mapper、导入/导出与事务回滚测试。
3. movielist 与 forum reducer 纯函数测试。
4. Screen 不直接访问 Store 后的 UI state / intent 测试。

本地合并前质量门槛：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

发布前质量门槛：

```bash
./gradlew.bat testDebugUnitTest lintDebug assembleRelease --console=plain
```

---

## 七、后续可选优化

1. 继续按真实复用点拆分大型 Screen 文件，优先处理纯 UI section。
2. 为 release minify 增加 Gson/Forum `ContentBlock` 反序列化 smoke test，作为发布前质量门槛的一部分。
