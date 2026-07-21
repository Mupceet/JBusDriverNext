# JBusDriver 学习文档设计

> **设计日期**：2026-07-19
> **目标产物**：`docs/learning/` 目录下 13 个 markdown 文件 + 1 个 README 入口
> **状态**：待评审

---

## 1. 读者画像与学习目标

### 1.1 读者画像

- **有**：Android 基础（懂 Activity、Context、Intent、布局 XML、能读 Java/Kotlin 代码）
- **没有**：
  - 完整 App 开发经验（不了解项目从启动到运行的完整链路）
  - 现代范式认知（依赖注入、声明式 UI、协程/Flow、Repository、单 Activity 架构）

### 1.2 读完文档后，读者应能

1. 理解"为什么"现代 Android 这样写
   - 命令式 vs 声明式 UI
   - 手动 `new` vs 依赖注入
   - 回调地狱 vs 协程/Flow
2. 独立读懂本项目 80% 以上的代码（包括 Compose UI、Hilt 注入、Repository/Flow 数据流）
3. 在本项目里新增一个简单功能（例如：新增一个设置项、新增一个列表页面）
4. 知道项目里每个目录/文件大致是做什么的、找东西时该去哪儿看

### 1.3 不包含的目标（明确边界）

- 不教 Kotlin 从零语法（只在用到时简要说明）
- 不教 Android Studio 操作、Gradle 从零配置
- 不深入到原生 NDK、自定义 View 性能优化等高级主题
- 不涉及 JS/HTML/CSS 抓取协议的细节

---

## 2. 风格规范

| 维度 | 规范 |
|------|------|
| 语言 | 中文正文；代码、类名、API、文件路径保留英文 |
| 文件命名 | `01-xxx.md`、`02-xxx.md` … 前缀编号便于排序；编号反映推荐阅读顺序但不强制 |
| 入口 | `docs/learning/README.md` 作为索引页，列出全部章节、阅读路径、读者画像 |
| 章节结构 | 每章统一模板（见第 4 节） |
| 代码示例 | 优先用简化示例代码（去掉无关细节、加上中文注释）；示例下方列出 `📁 项目对应位置：path/to/File.kt` 供深入查看 |
| 文件引用 | 正文第一次提到某文件时用 `file_path:line_number` 格式（便于点击跳转）；后续提及可只用文件名 |
| 术语对照 | 关键术语首次出现时附英文原词，例如"依赖注入（Dependency Injection, DI）" |
| 长度 | 每章控制在 300-600 行 markdown 源码（不含代码块约 200-400 行正文） |
| 配图 | 仅在文字难以表达时才用 mermaid 流程图（如数据流、Compose 重组流程） |
| 维护策略 | 示例代码块本身是**简化重写**（不复制原代码、不标行号），代码变更时一般无需同步改示例；只需偶尔核对正文里的 `file_path:line` 仍指向正确位置 |

---

## 3. 章节列表与阅读顺序

按 5 层结构组织，共 12 章 + 1 个附录 = 13 个 markdown 文件。

### 3.1 一、起步篇（1-2 章）

| 编号 | 文件名 | 主题 | 核心问题 |
|------|--------|------|---------|
| 01 | `01-project-overview.md` | 项目总览与现代 Android 范式 | 这个 App 是做什么的？为什么不用 XML + Activity 写法？项目目录是怎么组织的？ |
| 02 | `02-kotlin-essentials.md` | 现代 Kotlin 速览 | `data class`、`sealed`、`scope functions`、`lambda`、`suspend`、`by lazy` 等本项目高频语法 |

### 3.2 二、架构篇（3-4 章）

| 编号 | 文件名 | 主题 | 核心问题 |
|------|--------|------|---------|
| 03 | `03-single-activity.md` | 单 Activity 架构 | 为什么要单 Activity？`ModernMainActivity` 是怎么启动的？ |
| 04 | `04-dependency-injection.md` | 依赖注入与 Hilt | 什么是 DI？为什么不用 `new`？`@Inject`/`@Module`/`@Provides`/`@HiltAndroidApp` 全家桶 |

### 3.3 三、数据篇（5-8 章）

| 编号 | 文件名 | 主题 | 核心问题 |
|------|--------|------|---------|
| 05 | `05-coroutines-flow.md` | 协程与 Flow 异步编程 | 协程解决什么问题？`suspend`/`Flow`/`StateFlow`/`SharedFlow` 区别？ |
| 06 | `06-repository-pattern.md` | Repository 模式与项目数据流 | Repository 是什么？为什么 UI 不直接调网络？项目数据流总览 |
| 07 | `07-network-and-parsing.md` | 网络层与 HTML 解析 | OkHttp 怎么用？为什么用 Jsoup？`HtmlClient` 与 `WebViewFactory` 的角色 |
| 08 | `08-cache-strategy.md` | 缓存与 SWR 策略 | LRU、磁盘缓存、`CacheStore`、Stale-While-Revalidate 是什么意思 |

### 3.4 四、UI 篇（9-11 章）

| 编号 | 文件名 | 主题 | 核心问题 |
|------|--------|------|---------|
| 09 | `09-compose-basics.md` | Jetpack Compose 基础 | `@Composable`、`remember`、`state`、重组、`Modifier` |
| 10 | `10-viewmodel-state.md` | ViewModel + StateFlow | MVVM 在 Compose 时代怎么写？VM 不能做什么？ |
| 11 | `11-navigation.md` | Navigation 3 与路由 | NavKey 是什么？怎么定义一个新页面？ |

### 3.5 五、持久化与工程篇（12 章 + 附录）

| 编号 | 文件名 | 主题 | 核心问题 |
|------|--------|------|---------|
| 12 | `12-persistence-and-settings.md` | Room、DataStore、Gson 序列化 | Room 怎么定义？DataStore 与 SharedPreferences 区别？为什么需要自定义 Gson Adapter？ |
| A1 | `A1-engineering.md`（附录） | 工程实践：构建、ProGuard、测试、调试 | Gradle 变体、ProGuard 规则、单元测试、Lint、LeakCanary 等 |

### 3.6 推荐阅读路径

- **从零学习者**：01 → 02 → 03 → … → 12 → A1（顺序读）
- **只想了解某项技术**：直接跳到对应章节（每章开头有"前置章节"提示）
- **想看完整数据流**：05 → 06 → 07 → 08 → 10 串起来看

---

## 4. 每章统一模板

为了保证 12 章风格一致、降低读者认知负担，每章都遵循同一个模板：

```markdown
# 第 N 章：{章节标题}

> 📖 本章你将学到：{1-3 句话概括}
> 🔗 前置章节：{章节编号列表，无则写"无"}
> 📁 项目对应目录：{主要涉及的源码目录路径}

## N.1 为什么需要 {这个技术}                  ← 痛点驱动
   （讲清楚没有这个技术时遇到什么问题，用反面例子对比）

## N.2 {这个技术} 是什么                       ← 概念入门
   （用最简洁的话定义；术语首次出现附英文）

## N.3 最小示例                                ← 从零写一遍
   （简化到 10-30 行的示例代码，加中文注释）

## N.4 项目中怎么用                            ← 项目实战
   📁 项目对应位置：path/to/File.kt
   （先指出关键文件，再贴简化代码片段，最后说明它在系统中的角色）

## N.5 常见误区与调试技巧                      ← 经验沉淀
   （列举 2-4 个常见坑，例如"VM 里别暴露回调"、"Flow 别忘了 stateIn"）

## N.6 小结与下一站                            ← 收尾
   （3-5 行回顾；指向下一章或相关章节）

---
🔍 深入阅读：
- 官方文档链接
- 项目内相关代码位置
```

### 4.1 模板的几个要点

1. **痛点驱动（N.1）** —— 用"没有 X 会怎样"开篇，避免直接堆概念
2. **三层递进（N.2 → N.3 → N.4）** —— 概念 → 通用示例 → 项目实战
3. **简化示例 + 文件指引** —— 示例代码不依赖具体行号，方便代码变更后维护
4. **误区与调试（N.5）** —— 这是项目代码评审报告（`docs/CODE_REVIEW.md`）和 `AGENTS.md` 里沉淀的经验，最有教学价值
5. **小结与下一站（N.6）** —— 让读者随时知道"我在哪、下一步去哪"

---

## 5. 文件组织与索引

### 5.1 目录结构

```
docs/learning/
├── README.md                       ← 入口索引页
├── 01-project-overview.md
├── 02-kotlin-essentials.md
├── 03-single-activity.md
├── 04-dependency-injection.md
├── 05-coroutines-flow.md
├── 06-repository-pattern.md
├── 07-network-and-parsing.md
├── 08-cache-strategy.md
├── 09-compose-basics.md
├── 10-viewmodel-state.md
├── 11-navigation.md
├── 12-persistence-and-settings.md
└── A1-engineering.md
```

### 5.2 README.md 内容大纲

- 文档目的与读者画像（引用本设计文档）
- 完整章节目录（带一句话简介）
- 三条推荐阅读路径：
  - 从零学习者路径
  - 查漏补缺路径
  - 完整数据流路径
- 项目快速上手命令（构建、测试、运行）
- 与其他文档的定位区别说明

### 5.3 与其他文档的边界

| 文档 | 定位 |
|------|------|
| `AGENTS.md` | 给 AI 协作工具看的项目宪章 |
| `docs/CODE_REVIEW.md` | 已知问题与技术债记录 |
| `docs/superpowers/specs/` & `plans/` | 每个功能的设计与实施计划 |
| `docs/learning/`（本系列） | 给人类学习者的系统性教学材料 |

---

## 6. 实现路径与验收

### 6.1 实现策略

一次性写完 13 个文件不现实（篇幅大、风格容易漂移），采用分阶段策略：

1. **第一阶段：骨架与示范**
   - 写 `README.md` 骨架（含完整目录、阅读路径）
   - 写第 01 章（项目总览）—— 奠定整体基调
   - 写第 04 章（Hilt）—— 作为"从零讲起 + 项目实战"风格的典型示范
   - 收集反馈、校准风格

2. **第二阶段：批量产出**
   - 按章节顺序产出其余 11 章
   - 每写完一章同步更新 README.md 状态

3. **第三阶段：交叉校验**
   - 检查所有 `file_path:line` 引用是否仍然准确
   - 检查每章"前置章节"链路是否正确
   - 检查术语翻译一致性

### 6.2 验收标准

1. 每章遵循第 4 节模板的 6 段结构
2. 每章代码示例都能在项目里找到对应文件（`file_path:line` 引用准确）
3. README.md 的章节列表与实际文件一一对应
4. 章节之间的"前置章节"提示链路正确
5. markdown 渲染正常（在 GitHub / Android Studio 里都能正确显示）
6. 中文表述通顺、无错别字、术语翻译一致（同一术语全文统一一种译法）

---

## 7. 开放问题

实现期间需要确认的细节：

- **第 02 章 Kotlin 速览**：是否需要包含一个简单的 `MainActivity.kt` 让读者练习？（暂定：不需要，避免与第 03 章重复）
- **第 09 章 Compose 基础**：是否需要包含一个完整可运行的最小 Compose 例子？（暂定：需要，10-20 行 `@Composable` 函数）
- **第 A1 章附录**：是否需要包含如何用 `adb` 抓日志、如何使用 Profiler？（暂定：包含简短指引，不展开）

这些可以在写对应章节时再决定，不阻塞整体设计。
