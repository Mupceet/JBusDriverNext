# androidTest 覆盖率补充设计

- 日期：2026-07-03
- 状态：待评审
- 作者：结对 brainstorm
- 关联分支：`feat/browser-session-drop-bus-auth`

## 1. 背景与动机

项目当前测试以 JVM 单元测试（`app/src/test`）为主，已有约 70 个 UT 文件，较好地覆盖了
parser、repository（基于 fake）、state reducer、ViewModel、mapper、cache、domain model 等纯逻辑层。

但插桩测试（`app/src/androidTest`）几乎为空：仅有一个 `ForumPostContentTest.kt`
（纯 Compose UI 测试，使用 `createComposeRule()`，不依赖 Hilt）。这导致两类代码长期没有真实测试保护：

1. **Compose UI 层**：屏幕与组件的渲染、交互、导航回调、对话框。
2. **Room 持久层**：DAO 真实查询、Flow 发射、schema 迁移。目前 DAO 只通过纯 mapper 测试间接覆盖。

整体覆盖率因此偏低。本设计在**模拟器可用**的前提下，补充插桩测试以填补这两块空白。

## 2. 目标 / 非目标

**目标（v1）**

- 用真实插桩测试覆盖全部 3 个 DAO（`CategoryDao`、`HistoryDao`、`LinkItemDao`）及 `CollectDatabase` 的 1→2 迁移。
- 补充约 7 个组件级 Compose UI 测试 + 1 个屏幕级 smoke 测试。
- 打开 `enableAndroidTestCoverage`，让设备端运行可产出 `.ec` 覆盖率数据。
- 建立可复用的插桩测试范式（fixtures、hermetic DB、选择器约定），供后续扩展。

**非目标（后续阶段）**

- Hilt `@HiltAndroidTest` 集成测试（需自定义 `HiltTestApplication` + `AndroidJUnitRunner`）。
- 屏幕级 ViewModel-fake 端到端测试（Approach B，需把 `hiltViewModel()` 提参的小重构）。
- DataStore / WebView / Coil 等平台依赖型 core 的插桩测试。
- Robolectric 迁移（让部分测试回到 JVM）。
- jacoco 合并报告（UT + androidTest 统一报告）。

## 3. 方案选择

考虑过三种结构：

- **A. 组件优先 Compose + 完整 DAO 覆盖**：纯 Compose 组件/无状态 composable 测试 + 内存 DB 的 DAO 测试。可靠、低 flake，但不测真实屏幕接线。
- **B. 屏幕级 Compose（ViewModel fake）**：整屏渲染、断言端到端流程。覆盖更真实，但屏幕当前内部调 `hiltViewModel()`，需要提参重构，flake 更高。
- **C. 混合（选定）**：v1 采用 A 作为骨架，并额外加少量屏幕级 smoke 测试（针对已经是 `internal` 的内层 composable，零重构）。完整屏幕级（B）作为后续阶段。

**决策：方案 C。** 与选定的覆盖层（Compose UI + Room DAO）一致，避免 Hilt/自定义 runner 的基础设施成本，
复用已被 `ForumPostContentTest` 验证的范式，在不做前期重构的前提下同时拿到组件覆盖与少量屏幕级覆盖。

运行器选择：**connected `androidTest`（模拟器）**，对 Compose 最忠实；Robolectric 作为后续可选加速项。

## 4. 基础设施变更

### 4.1 `app/build.gradle.kts`

- `buildTypes.debug` 增加 `enableAndroidTestCoverage = true`（不引入 jacoco 合并）。
- `dependencies` 增补：
  - `androidTestImplementation(libs.coroutines.test)`（用于 `runTest`；当前仅 `testImplementation`）。
  - `androidTestImplementation(libs.room.testing)`（用于 `MigrationTestHelper`）。
- 让迁移测试可读取导出的 schema JSON：
  ```kotlin
  android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
  ```
  （Room 插件已配置 `schemaDirectory("$projectDir/schemas")`，schema 文件已存在。）

> 不新增 `androidx.test:runner/core/rules`：Compose 测试由 `createComposeRule()`（已具备）提供 host Activity；
> DAO 测试用 `runTest` + 直接构建 DB，无需额外 rule。如后续屏幕级测试需要 `ActivityScenario` 再按需补加。

### 4.2 `gradle/libs.versions.toml`

- 新增 `room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }`（复用 `room` 版本号）。
- `coroutines-test` 已存在，仅多加一处 `androidTestImplementation` 引用。

### 4.3 Fixtures

- 新建 `app/src/androidTest/java/me/jbusdriver/modern/test/Fixtures.kt`，提供实体工厂：
  - `aCategory(id = ..., tree = ..., sortOrder = ..., name = ...)`
  - `aLinkItem(dbType = ..., key = ..., categoryId = ...)`
  - `aHistory(dbType = ..., jsonStr = ..., isAll = ...)`
- 若日后与 `app/src/test/.../test/TestFakes.kt` 出现重复，再提升到 `src/testFixtures`（AGP 支持的共享 sourceSet）。v1 保持简单。

## 5. Room DAO 与迁移测试

通用约定：每个测试用 `Room.inMemoryDatabaseBuilder(context, DbClass::class.java).allowMainThreadQueries().build()`，
`@After` 中 `db.close()`；测试体用 `runTest`，必要时 `withContext(Dispatchers.IO)`。
Flow 用「先采集当前值、触发变更后重新采集」的方式断言响应。

### 5.1 `CategoryDaoTest`
- `insert` + `findById` 命中。
- `insert` 主键冲突走 `IGNORE`（返回 -1，不抛异常）。
- `queryTreeByLike("1/")` 前缀过滤正确；`ORDER BY sort_order DESC` 顺序正确。
- `update` 后再 `findById` 字段已变。
- `delete(id)` 后 `findById` 为 null。
- Flow 响应：插入后重新采集到新元素。

### 5.2 `HistoryDaoTest`
- `insert` + `count` 一致；`insertAll` 批量。
- `queryByLimit(size, offset)` 分页 + `ORDER BY id DESC`（最新在前）顺序正确，跨页无重叠/遗漏。
- `update(id, dbType, jsonStr, isAll)` 覆盖对应字段。
- `deleteAll()` 后 `count() == 0`；`resetAutoIncrement()` 后新插入 id 从 1 开始（`sqlite_sequence` 重置）。
- Flow 响应：删除后采集到空列表。

### 5.3 `LinkItemDaoTest`
- `insert` 命中；复合唯一键 `(dbType, key)` 冲突走 `IGNORE`。
- `listAll()` Flow 响应；`listByType(dbType)` 过滤正确。
- `queryLink()` 只返回 `dbType NOT IN (1,2)`。
- `queryByCategoryId(categoryId)` 过滤正确。
- `updateByCategoryId(categoryId, dbType, setId)` 批量改分类、且只影响指定 dbType。
- `hasByKey(dbType, key)` 存在返回 ≥1、不存在返回 0。
- `delete(dbType, key)` 后 `hasByKey` 为 0，且不影响其它行。

### 5.4 `CollectDatabaseMigrationTest`
- 用 `MigrationTestHelper` 加载 v1 schema（assets 指向 `app/schemas`）。
- 在 v1 库上插入若干 `t_link` 行（模拟旧的单列 `key` 索引状态），运行 1→2 迁移：
  - 断言唯一索引 `index_t_link_dbType_key` 存在；
  - 断言该唯一约束被强制（插入重复 `(dbType, key)` 应失败）。
- 另测一条「从 v1 直接 open 到 v2」的完整路径，确保迁移后 DB 可正常读写。

> 实现细节：Room 2.8 的 `MigrationTestHelper` 构造签名以实际 API 为准；
> schema 路径通过 4.1 的 androidTest assets 配置提供，必要时配合 `testInstrumentationRunnerArguments` 的 `room.schemaLocation`。

## 6. Compose UI 测试

通用约定：`createComposeRule()`，显式传入状态，把回调记录到一个 recorder（`var clicked by mutableStateOf<...>(null)` 或列表）。
不引入 Hilt、不触网络。选择器优先用 `onNodeWithText` / `onNodeWithContentDescription`（字符串资源已存在），
仅当文本动态时（头像、图片槽位）才加 `testTag`，并就地注释说明。

### 6.1 组件级
- **`StateViewsTest`**：渲染 loading / empty / error 三态（最简单，无状态，作为首个范式）。
- **`MovieListTest`**：渲染列表 / 空 / 错误；点击项时 `onMovieClick` 以正确索引触发。
- **`ActressGridTest`**：渲染网格；`onActressClick` 触发。
- **`CollectButtonTest`**：反映 `isCollected` 状态；点击触发 toggle 回调。
- **`MovieFilterBarTest`**：渲染当前筛选；切换选择触发回调。
- **`ErrorViewTest`**：显示 message；点击重试触发回调。
- **`MagnetBottomSheetTest`**：loading / 列表 / 空三态切换正确。

### 6.2 屏幕级 smoke（零重构）
- **`MovieTabContentTest`**：`MovieTabContent` 已是 `internal`，可直接测试。渲染 loading → content → 分页尾态。

## 7. 约定与稳定性

- **Hermetic**：每个测试用内存 DB，`@After` 关闭；不触碰真实 DataStore / 网络 / Hilt。
- **运行**：`./gradlew connectedDebugAndroidTest`（需已连接模拟器/真机）。在 README/AGENTS.md 记录此前置条件。
- **Flake 控制**：Compose 测试用 `rule.waitForIdle()`，必要时手动推进时钟；用选择器断言，不依赖 sleep/计时。
- **验收**：所有新增测试在模拟器上通过；`enableAndroidTestCoverage` 在设备端产出 `.ec`。

## 8. 风险

- **Room 2.8 `MigrationTestHelper` API 差异**：构造签名 / schema 定位方式可能需微调。缓解：实现时以 Room 2.8.4 实际 API 为准，必要时用 `testInstrumentationRunnerArguments`。
- **组件缺少稳定标识**：部分组件若无可断言文本，需补 `testTag`（少量、就地注释）。属预期内小改动。
- **`enableAndroidTestCoverage` 对运行时长的影响**：插桩插桩会略微减慢测试。v1 用例数有限，可接受。
- **模拟器依赖**：connected 测试需设备，CI 需配模拟器。后续若需在无设备 CI 上跑，再评估 Robolectric。

## 9. 验收清单

- [ ] `app/build.gradle.kts` 与 `libs.versions.toml` 增改到位，`./gradlew assembleDebug` 通过。
- [ ] 4 个 DAO/迁移测试、8 个 Compose UI 测试全部在模拟器上通过。
- [ ] `connectedDebugAndroidTest` 产出 `.ec` 覆盖率数据。
- [ ] Fixtures 与范式文档化（测试头部注释 / AGENTS.md 补一行运行说明）。
- [ ] 不引入 Hilt 测试依赖、不改动 `release` 构建。
