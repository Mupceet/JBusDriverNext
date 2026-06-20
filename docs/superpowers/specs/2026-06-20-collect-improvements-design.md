# 收藏界面改进设计

> 日期：2026-06-20
> 状态：待实现
> 范围：收藏界面两项改进——(1) 演员收藏支持区分有码/无码；(2) 收藏时间筛选展开月份

## 背景

收藏界面当前状态：

- **影片收藏**已支持有码/无码区分——靠 `categoryId`（1=有码、3=无码），判定来源是入口 `censorType`（从「无码」Tab 或无码影片详情进入即判为无码），并可按 `CensorFilter` 筛选。收藏时从详情页采集 `code`/`date` 等元信息存入 `Movie`。
- **演员收藏**固定 `categoryId=2`，**无类型区分**；`ActressInfo` 只存 `name/avatar/link`，详情页 `ActressDetail.info` 被丢弃。
- **筛选**中「发布日期」已有「年份 → 展开 12 个月 → 无数据月份置灰」的完整逻辑（`MonthChipRow`）；「收藏时间」只有年份选择，**无月份展开**。

本设计让演员对齐影片的分类机制，并让收藏时间筛选复用发布日期的月份展开逻辑。

## 决策记录

| 决策点 | 选择 |
|--------|------|
| 有码/无码判定来源 | 从演员自身 link URL（含 `/uncensored/` = 无码） |
| 元信息采集范围 | 只加类型，**不采集** `ActressDetail.info` 等其他元信息 |
| 类型存储方式 | 复用 `categoryId` 分类（与影片对称），新增 `UncensoredActressCategory(id=4)` |
| 旧收藏数据 | **不迁移**，旧演员收藏保持 `categoryId=2`（默认归有码） |

---

## 功能 1：演员有码/无码分类

### 目标

演员收藏像影片一样，收藏时按来源自动归类到「有码演员 / 无码演员」分类，并在收藏列表按 `CensorFilter` 筛选。

### 数据层

- `domain/model/Category.kt`：
  - 新增 `UncensoredActressCategory`（`id=4`，名称如「無碼演員」），结构与 `UncensoredMovieCategory` 对称。
  - `AllFirstParentDBCategoryGroup` 增加 `4 → UncensoredActressCategory`。
- `ActressInfo`（定义于 `domain/model/MovieDetail.kt`）**不改**——类型完全靠 `categoryId` 表达，与影片一致。

### 收集流程（对齐影片 `toggleMovieCollect(movie, categoryId)`）

- 收藏入口 `LinkMovieListViewModel.toggleActressCollect`：
  - 判定 `val isUncensored = actress.link.contains("/uncensored/")`。
  - `categoryId = if (isUncensored) UncensoredActressCategory.id else ActressCategory.id`（缺省有码=2）。
  - 调用 `collectRepository.toggleActressCollect(actress, categoryId)`。
- `CollectRepository.toggleActressCollect`：接口与 `DefaultCollectRepository` 实现增加 `categoryId: Int?` 参数（对齐 `toggleMovieCollect(movie, categoryId)`）。
- `LinkMappers.convertDBItem(categoryId)`：已是通用接口，演员用传入 `categoryId`；`defaultCategoryId` 的演员分支保持 `ActressCategory.id`（2），保证缺省/旧入口归有码。

### 筛选 UI

- `CollectionFilterSheet`：`CensorFilter`（全部/有码/无码）当前仅影片显示，改为**影片与演员都显示**。
- `CollectionListViewModel`：演员分支按 `categoryId`（2/4）映射 `CensorFilter`，复用影片的 `filterByCensor` 思路。

### 导入导出 / 兼容

- `CollectionBackupCodec`：新分类随 `categoryId` 自动跟随，无需专门改动。
- 旧导出文件没有 id=4 的演员条目，导入按原值（2）落地 → 旧数据自然归有码，符合「不迁移」决策。

### 前提验证（实现第一步）

- 抓一个无码女优列表页（如 `/uncensored/actresses`），确认 `.avatar-box` 的 href 确实带 `/uncensored/` 前缀。
- **若不带**：回退到「入口 censorType 继承」（影片同款逻辑，从进入演员页的 Tab/影片类型判定）。此回退只改判定处一处，不影响其余设计。

### 涉及文件

- `app/src/main/java/me/jbusdriver/modern/domain/model/Category.kt`
- `app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt`（接口 + `DefaultCollectRepository`）
- `app/src/main/java/me/jbusdriver/modern/data/db/LinkMappers.kt`
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt`（`toggleActressCollect`）
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt`
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`
- 测试：`app/src/test/.../test/TestFakes.kt`（`StubCollectRepository` 同步 `categoryId` 参数）

---

## 功能 2：收藏时间月份展开

### 目标

收藏时间筛选选定年份后，展开该年 12 个月，无数据月份置灰，与发布日期筛选体验一致。

### 现状

- 发布日期：`CollectionFilterSheet` 有 `MonthChipRow`，`CollectionListViewModel` 计算 `availablePublishMonths` 并 `filterByPublishMonth`。
- 收藏时间：只有 `YearChipRow` 与 `CollectionFilterState.collectYear`，无月份。

### 设计

- `CollectionFilterState`：加 `collectMonth: Int? = null`；更新 `hasActiveFilters` / `activeFilterCount`。
- `CollectionListViewModel`：
  - 新增 `availableCollectMonths: Set<Int>`——选定 `collectYear` 后，从该年收藏项 `createTime`（Long 时间戳）算出有数据的月份集合（用 `Calendar` 取 `MONTH`，复用现有 `Long.toYear()` 同套时间工具）。
  - 新增 `filterByCollectMonth(month) { it.createTime }`，在 `applyFilterAndSort` 中应用。
- `CollectionFilterSheet`：收藏时间区域，当 `collectYear != null && collectYear > 0` 时展开 `MonthChipRow`（**复用发布日期同款组件**），传 `availableCollectMonths`；置灰逻辑（`enabled=false + alpha=0.38`）已内置于 `MonthChipRow`。
- 年份切换时重置 `collectMonth = null`（参考发布日期同款行为）。

### 边界

- 收藏时间年份均为实际年份（从 `createTime` 提取），不存在 `-1 更早`；故所有选定年份都可展开月份。
- 影片与演员共用此筛选（收藏时间筛选本就共用），无需分别处理。

### 涉及文件

- `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterState.kt`
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionListViewModel.kt`
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectionFilterSheet.kt`

---

## 测试要点

### 功能 1

- URL 判定：有码 link → `categoryId=2`；无码 link（含 `/uncensored/`）→ `categoryId=4`。
- `CensorFilter` 筛选演员：ALL/CENSORED/UNCENSORED 分别返回正确子集。
- `convertDBItem`：演员用传入 `categoryId`；缺省归 2。
- 导入导出：旧文件（演员 `categoryId=2`）导入后仍为 2；新导出含 4。

### 功能 2

- `availableCollectMonths`：给定年份 + `createTime` 集合，算出正确月份集合。
- `filterByCollectMonth`：按月过滤准确。
- 置灰：无数据月份 `enabled=false`。
- 年份切换重置 `collectMonth`。

---

## 范围外（YAGNI）

- 不采集 `ActressDetail.info` 等演员详细元信息。
- 不做旧演员收藏数据迁移。
- 不为演员新增按身高/出生等属性的结构化筛选。
- 不改动影片侧已有的有码/无码逻辑。
