# 单元测试覆盖率分析与补齐计划

> 生成日期：2026-06-19 · 分支：codex/phase-a-fixes
> 数据来源：AGP `createDebugUnitTestCoverageReport`（JaCoCo XML），现有 44 个测试文件全部通过

---

## 1. 执行摘要

| 指标 | 当前值 |
|------|--------|
| **全局行覆盖率 (LINE)** | **25.5%**（2860 / 11210） |
| 方法覆盖率 (METHOD) | 30.6% |
| 类覆盖率 (CLASS) | 34.1%（235 / 689） |
| 分支覆盖率 (BRANCH) | 21.1% |
| **业务逻辑层行覆盖率**（排除 Compose UI + 生成代码） | **约 53%** |

**核心结论**：全局 25.5% 偏低，但这是**假象**——大量未覆盖代码是「不该用单测覆盖」的纯 Compose UI（~60 个 Screen/Components，约 4000+ 行）和 Room/Hilt 生成代码（`*_Impl`、`Dagger*`，约 800+ 行）。**真正的业务逻辑层（parser/repository/core/domain）覆盖率约 53%，是补齐的重点**，目标提升到 80%+。

---

## 2. 覆盖率现状

### 2.1 全局指标

| 指标 | missed | covered | 覆盖率 |
|------|--------|---------|--------|
| INSTRUCTION | 70729 | 19277 | 21.4% |
| BRANCH | 3364 | 901 | 21.1% |
| LINE | 8350 | 2860 | **25.5%** |
| COMPLEXITY | 3536 | 1036 | 22.7% |
| METHOD | 1862 | 820 | 30.6% |
| CLASS | 454 | 235 | 34.1% |

### 2.2 按包分布（行覆盖率，业务包）

| 覆盖率 | 包 | 性质 | 是否补齐 |
|--------|-----|------|---------|
| 100% | `data/cache` | 小工具 | — |
| 85.6% | `domain/model` | data class 为主 | 已较高 |
| 80.0% | `core/serialization` | JSON adapter | — |
| 70.2% | `core/site` | 站点配置 | — |
| 67.2% | `data/parser` | HTML 解析 | **部分补**（MovieHtmlParser 拖后腿） |
| 59.1% | `core/cache` | SWR 缓存 | **补** |
| 45.7% | `core` | Gson/File/Log 工具 | **补** |
| 41.5% | `ui/movielist` | ViewModel+Reducer（已测） | 增量补 |
| 34.6% | `data/db/entity` | Room entity | — |
| 29.6% | `data/repository` | 仓储 | **重点补** |
| 25.7% | `ui/forum` | ViewModel+Screen | 增量补 ViewModel |
| 24.0% | `ui/settings` | ViewModel | 增量补 |
| 19.1% | `data/db` | 含 LinkMappers | **补** |
| 18.9% | `ui/search` | ViewModel | 增量补 |
| 15.8% | `ui/image` | ViewModel | — |
| 11.5% | `data/mirror` | WebView 依赖 | 仅纯函数 |
| 11.5% | `ui/detail` | ViewModel+Screen | 增量补 |
| 10.3% | `ui`（根包） | Navigation/Activity | 不补（Compose） |
| 9.4% | `data/settings` | DataStore | 难单测 |
| 1.8% | `core/http` | OkHttp/WebView | 仅纯函数 |
| 0.8% | `modern`（根包） | Application/KLog | 不补 |
| 0% | `ui/theme` `ui/debug` `ui/components` `data/session` `data/db/dao` `data/di` `data/gateway` | Compose/接口/DI/WebView | 不补 |

### 2.3 关键纯逻辑类精确覆盖率（补齐候选）

| 类 | missed | covered | 覆盖率 | 难度 |
|----|--------|---------|--------|------|
| `MovieHtmlParser` | 83 | 3 | **3.5%** | 中（HTML fixture） |
| `SessionCookieStore` | 65 | 0 | **0%** | 中（纯函数部分） |
| `NetClient` | ~99 | 0 | **0%** | 中（部分纯函数） |
| `MirrorScanner` | ~100 | 0 | **0%** | 易（sortMirrorUrls） |
| `MoviePageFetcher` | 35 | 0 | **0%** | 中 |
| `LinkMappers` | 33 | 39 | 54.2% | 中 |
| `CollectionBackupCodec` | ~68 | ~22 | ~25.6% | 中（stub DAO） |
| `GsonExt` | ~25 | ~31 | ~35% | 中 |
| `MovieRepositoryUrls` | 17 | 0 | **0%** | **易**（纯字符串） |
| `ActressHtmlParser` | 15 | 0 | **0%** | 易（Jsoup） |
| `MovieDetailRepository` | 13 | 0 | **0%** | 中 |
| `FileUtil` | 12 | 0 | **0%** | 易（tmpdir） |
| `MagnetHtmlParser` | 10 | 0 | **0%** | 易（Jsoup） |
| `MovieRepositoryCacheKeys` | 5 | 0 | **0%** | **易**（纯字符串） |
| `GenreHtmlParser` | 5 | 0 | **0%** | 易（Jsoup） |

> 已覆盖较好（参考，无需大改）：`ForumThreadParser` 85%、`ForumPostParser` 81%、`InlineParagraphParser` 82%、`ForumHomeParser` 77%、`UrlParserExt` 70%、`LogDiff` 67%、`InlineStyle` 94%。

### 2.4 已测 ViewModel/Repository 覆盖率（增量补齐候选）

| 类 | 覆盖率 | 现有测试 | 增量方向 |
|----|--------|---------|---------|
| `MovieRepository` | ~33–65% | DefaultMovieRepositoryTest | 缓存命中/失效/强制刷新分支 |
| `CollectRepository` | 11–72% | CollectRepositoryTest | toggle/add/remove 边界 |
| `ForumRepository` | ~40% | ForumRepositoryCacheFlowTest | 缓存流分支 |
| `SearchRepository` | ~56% | SearchRepositoryUrlTest | URL 构造边界 |
| `SearchViewModel` | ~47% | SearchViewModelTest | 错误态/空结果 |
| `ActressListViewModel` | ~48% | ActressListViewModelTest | 错误/分页 |
| `ForumThreadDetailViewModel` | ~46% | ForumThreadDetailViewModelTest | 错误态 |
| `MovieDetailViewModel` | ~73% | MovieDetailViewModelTest | 已较好 |
| `MovieListViewModel` | ~65% | MovieListViewModelTest | 已较好 |

---

## 3. 缺口分类

### 3.1 不补（理由充分）

- **纯 Compose UI**（~60 文件，~4000 行）：`*Screen.kt`、`components/*`、`theme/*`、`Navigation.kt`、`MainScreen.kt` 等。单测无意义，应走 **instrumented/UI test**（单独战线）。
- **Room/Hilt 生成代码**：`*_Dao_Impl.kt`、`*_Database_Impl.kt`、`DaggerJBusApplication_*`、`hilt_aggregated_deps`。生成产物，不该手测。
- **Room DAO 接口**（`CategoryDao`/`HistoryDao`/`LinkItemDao`）：需 instrumented test。
- **DataStore 强依赖**（`LabSettingsStore`/`SearchHistoryStore`/`UiPrefsStore`）：单测难，建议 Robolectric 或 instrumented。
- **WebView 强依赖**（`ForumSessionManager`/`BrowserSessionClient`/`WebViewFactory`/`ForumSessionClient`）：需 instrumented。
- **Android Gateway**（`CollectionDocumentGateway`/`ImageMediaGateway`）、**DI Module**（`DataModule`/`DatabaseModule`）：无业务逻辑或强 Android 依赖。

### 3.2 应补（纯逻辑、高价值、可单测）

见第 4 节分批计划。

### 3.3 已覆盖较好（仅增量）

`ForumThreadParser`、`ForumPostParser`、`InlineParagraphParser`、`InlineStyle`、`domain/model` 大部分、多数 ViewModel 主流程。

---

## 4. 补齐计划（分 5 批，按 ROI 排序）

> 风格遵循现有测试：**JUnit 4 + kotlinx-coroutines-test + 手写 stub/fake**（无 mock 库，`object : Repo by stub { }` 委托）。每个新测试文件对应一个被测类。

### Batch 1 — 解析器与字符串工具（高 ROI，易测）

| 被测类 | 目标覆盖率 | 测试要点 | 预计用例 |
|--------|-----------|---------|---------|
| `MovieHtmlParser` | 3.5% → 85% | `parseMovieDetails`（73 行核心）、`parsePageInfo`、`parseMovieFilterInfo`；用脱敏 HTML fixture 覆盖正常/缺字段/空文档 | 6–8 |
| `ActressHtmlParser` | 0% → 85% | `parseActressList`、`parseActressAttrs` | 3–4 |
| `MagnetHtmlParser` | 0% → 90% | `parseMagnets` 表格解析 | 2–3 |
| `GenreHtmlParser` | 0% → 90% | `parseGenreCategories` | 2 |
| `MovieRepositoryUrls` | 0% → 95% | 各 URL 拼接函数 + 边界（空/越界页码） | 4–5 |
| `MovieRepositoryCacheKeys` | 0% → 95% | 各缓存键生成 | 3 |
| `FileUtil` | 0% → 85% | `createDir`：新建/已存在目录/同名文件冲突，用 `@TemporaryFolder` 或系统 tmp | 3 |
| `GsonExt` | 35% → 80% | `parseDateOrNow` 多格式（时间戳/ISO/US 格式/非法）、`NullSafeFactory` 填充、Int 空安全、`fromJson<T>` 泛型 | 5–6 |

**预计：~30 个用例，9 个测试文件。** 业务逻辑层覆盖率提升约 +8 个百分点。

### Batch 2 — 收藏与备份核心（高价值，中难度）

| 被测类 | 目标覆盖率 | 测试要点 | 预计用例 |
|--------|-----------|---------|---------|
| `LinkMappers` | 54% → 85% | `stripUrlFields`/`restoreUrlFields` 对称性、`convertDBItem` 各类型、`uniqueKey`、`deserializeLink` | 5–6 |
| `CollectionBackupCodec` | 25% → 85% | `exportCollectionsJson`；`importNewFormat`（去重、URL 修复、categoryId 回退）；`importLegacyFormat`（type 分支）。stub `LinkItemDao`（记录 insert 调用） | 6–7 |

**预计：~13 个用例，2 个测试文件。** 备份/迁移路径是用户数据安全关键。

### Batch 3 — Repository 缓存与错误分支（增量补已有测试）

| 被测类 | 目标覆盖率 | 测试要点 | 预计用例 |
|--------|-----------|---------|---------|
| `MovieRepository` | 33% → 70% | 缓存命中/失效/`forceRefresh`、Lru vs 持久化路径、网络异常回退 | 4–5 |
| `CollectRepository` | 部分 11% → 70% | `toggleMovieCollect`/`toggleActressCollect` 边界、`isCollected`、import/export 转发 | 4 |
| `ForumRepository` | 40% → 70% | 缓存流命中/失效/刷新 | 3 |
| `SearchRepository` | 56% → 80% | URL 构造边界（已有 UrlTest，补搜索结果解析路径） | 2–3 |

**预计：~14 个用例（部分并入现有文件）。**

### Batch 4 — 网络会话层可测纯函数（易，部分可测）

| 被测类 | 目标覆盖率 | 测试要点 | 预计用例 |
|--------|-----------|---------|---------|
| `SessionCookieStore` | 0% → 40% | `parseCookieString`、`isSessionValid`（过期判断）。其余依赖 CookieManager 不测 | 3 |
| `MirrorScanner` | 0% → 30% | `sortMirrorUrls`。WebView/HTTP 部分不测 | 2 |
| `WebViewHelper` | 0% → 部分 | `unescapeJsString` 纯算法（十六进制/Unicode 转义） | 2 |
| `NetClient` | 0% → 25% | URL 处理/可达性纯函数（其余 OkHttp 单例不测） | 2 |
| `MoviePageFetcher` | 0% → 60% | `fetchGenreCategories` 去重逻辑，stub `HtmlClient` | 2–3 |

**预计：~11 个用例。** 这类只补纯函数，不强求高覆盖（Android 框架部分留给 instrumented）。

### Batch 5 — ViewModel 错误/边界路径（增量）

| 被测类 | 目标覆盖率 | 测试要点 | 预计用例 |
|--------|-----------|---------|---------|
| `SearchViewModel` | 47% → 80% | 空结果、网络错误、分页边界 | 3 |
| `ActressListViewModel` | 48% → 80% | 错误态、分页、刷新 | 2–3 |
| `ForumThreadDetailViewModel` | 46% → 80% | 错误态、刷新 | 2 |
| `MovieDetailRepository` | 0% → 60% | 集成：缓存+网络，stub 各层 | 3 |

**预计：~10 个用例（并入现有文件）。**

### 全量汇总

| 批次 | 新增测试文件 | 新增用例 | 业务逻辑层覆盖率贡献 |
|------|------------|---------|-------------------|
| Batch 1 | 9 | ~30 | +8 pp |
| Batch 2 | 2 | ~13 | +5 pp |
| Batch 3 | 并入现有 | ~14 | +6 pp |
| Batch 4 | 5 | ~11 | +3 pp |
| Batch 5 | 并入现有 | ~10 | +4 pp |
| **合计** | **~16 新文件** | **~78 用例** | **53% → ~80%** |

---

## 5. 预期效果

- **业务逻辑层行覆盖率：53% → 约 80%**（实质性提升，这是单测真正的价值）。
- **全局行覆盖率：25.5% → 约 30%**（提升有限，因为 Compose UI 占总代码 ~40% 且不纳入单测）。
- 若要显著提升**全局**数字，需另起 **Compose UI instrumented test** 战线（`androidTest`），属本计划之外。

---

## 6. 基础设施建议

本次为产出报告，临时在 `app/build.gradle.kts` 的 `debug` 块加了 `enableUnitTestCoverage = true`。该配置会让每次 `testDebugUnitTest` 都注入 JaCoCo agent 采集覆盖率（约 5–10% 开销）。两个选项：

- **选项 A（推荐）**：保留 `enableUnitTestCoverage = true`，日常无感，需要覆盖率时直接 `./gradlew createDebugUnitTestCoverageReport`。
- **选项 B**：移除该行，改用独立可开关的 JaCoCo task，仅在 CI/需要时启用，日常测试零开销。

> 请确认保留 A 还是改 B。

复用解析脚本：`app/build/cov_report.ps1`（PowerShell 解析 JaCoCo XML，输出全局/包/类三层 CSV 风格数据）。

---

## 7. 验证方法

每完成一批后：
```bash
./gradlew testDebugUnitTest createDebugUnitTestCoverageReport --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File app/build/cov_report.ps1
```
核对对应类覆盖率是否达标，确保无回归（全部测试通过）。

---

## 8. 执行顺序建议

Batch 1 → Batch 2 → Batch 3 → Batch 4 → Batch 5（按 ROI 递减，前期见效快）。

**等待用户确认本计划 + 基础设施选项（A/B）后，再开始编写测试代码。**
