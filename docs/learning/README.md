# JBusDriver 学习文档

> 给有 Android 基础、但没做过完整 App 的开发者。读完能看懂本项目 80% 代码，能新增简单功能。

## 📚 这套文档是什么

这是一套**面向人类学习者**的中文教程，与 `AGENTS.md`（给 AI 协作工具看的项目宪章）不同，这里讲的是"为什么这么写"。

我们用 JBusDriver 这个真实在跑的项目作为活教材：每个技术概念都按"通用背景 → 最小示例 → 项目里怎么用 → 常见坑"四步走，看完既能理解原理，也能直接去项目里找到对应代码。

本套文档不替代 Kotlin / Android 官方教程。我们假设你能读 Java / Kotlin 代码、知道 Activity / Context / Intent 是什么；不教 Kotlin 从零语法，也不深入 NDK / 性能优化等高级主题。

## 🎯 适合谁读 / 不适合谁读

**适合**：

- 懂 Android 四大组件基础（Activity、Service、BroadcastReceiver、ContentProvider）
- 能读 Java / Kotlin 代码，理解 `class`、`interface`、`lambda`
- 没做过完整 App，不清楚"从启动到运行"的完整链路
- 不了解依赖注入（DI）、Jetpack Compose、协程 / Flow、Repository 等现代范式
- 想在本项目里新增功能（设置项、列表页、详情页）但不知道从哪儿下手

**不适合**：

- 完全没碰过 Android 的新手（请先看官方 Android Basics 课程）
- 想从零学 Kotlin 语法（请先看 Kotlin 官方文档或 Kotlin Koans）
- 想研究 NDK / JNI、自定义 View 性能优化、字节码插桩等高级主题
- 想看 JS / HTML / CSS 抓取协议的细节（本项目目标站结构不展开）

## 🗺️ 完整章节目录

共 12 章 + 1 个附录，按 5 层结构组织（起步 → 架构 → 数据 → UI → 持久化与工程）。编号反映推荐阅读顺序，但可跳读。

| 编号 | 文件 | 主题 | 一句话简介 |
|------|------|------|-----------|
| 01 | [01-project-overview.md](01-project-overview.md) | 项目总览与现代 Android 范式 | 这个 App 做什么、目录怎么组织、为什么不用 XML + Activity |
| 02 | [02-kotlin-essentials.md](02-kotlin-essentials.md) | 现代 Kotlin 速览 | data class / sealed / scope functions / 协程语法等本项目高频用法 |
| 03 | [03-single-activity.md](03-single-activity.md) | 单 Activity 架构 | 为什么全局只有一个 Activity，UI 切换靠什么 |
| 04 | [04-dependency-injection.md](04-dependency-injection.md) | 依赖注入与 Hilt | `@HiltAndroidApp` / `@Inject` / `@Module` / `@Binds` 全家桶怎么用 |
| 05 | [05-coroutines-flow.md](05-coroutines-flow.md) | 协程与 Flow 异步编程 | `suspend` / `Flow` / `StateFlow` / `SharedFlow` 各自解决什么问题 |
| 06 | [06-repository-pattern.md](06-repository-pattern.md) | Repository 模式与项目数据流 | UI 为什么不直接调网络，Repository 在中间起什么作用 |
| 07 | [07-network-and-parsing.md](07-network-and-parsing.md) | 网络层与 HTML 解析 | OkHttp 怎么用、为什么用 Jsoup、WebView fallback 干什么 |
| 08 | [08-cache-strategy.md](08-cache-strategy.md) | 缓存与 SWR 策略 | LRU 内存缓存、磁盘缓存、Stale-While-Revalidate 是什么 |
| 09 | [09-compose-basics.md](09-compose-basics.md) | Jetpack Compose 基础 | `@Composable` / `remember` / `Modifier` / 重组（Recomposition） |
| 10 | [10-viewmodel-state.md](10-viewmodel-state.md) | ViewModel + StateFlow | MVVM 在 Compose 时代怎么写、ViewModel 不能做什么 |
| 11 | [11-navigation.md](11-navigation.md) | Navigation 3 与路由 | NavKey 是什么、怎么定义一个新页面、转场动画怎么配 |
| 12 | [12-persistence-and-settings.md](12-persistence-and-settings.md) | Room、DataStore、Gson 序列化 | 数据库怎么定义、设置怎么存、为什么需要自定义 Gson Adapter |
| A1 | [A1-engineering.md](A1-engineering.md) | 工程实践：构建、ProGuard、测试、调试 | Gradle 变体、混淆规则、单元测试、Lint、LeakCanary |

> 注：上表中的链接目标尚未全部创建（章节在后续任务中逐个产出）。文件名以 `NN-` 开头的是正文章节，`A1-` 是附录。

## 🧭 三条推荐阅读路径

1. **从零学习者**：按编号顺序读 `01 → 02 → 03 → … → 12 → A1`。这套路径从"项目总览"一路讲到"工程实践"，每章都建立在前面的概念上，读完能形成完整的现代 Android 心智模型。

2. **查漏补缺**：直接跳到你想学的章节。每章开头都有一行 `🔗 前置章节` 提示，如果遇到看不懂的术语，按提示回看对应章节即可。比如只想学 Hilt，直接看第 04 章，最多回看一下第 01 章的包结构。

3. **想看完整数据流**：按 `05 → 06 → 07 → 08 → 10` 串起来读。这五章合起来就是"一次列表请求从发出到屏幕刷新"的完整链路：协程（05）→ Repository（06）→ 网络（07）→ 缓存（08）→ ViewModel 状态（10）。

## 🚀 快速上手：构建与运行

下面四条命令覆盖 90% 的日常需求（取自 `AGENTS.md` §Build Commands）：

```bash
# Debug 构建，产物在 app/build/outputs/apk/debug/jbus_debug_v*.apk
./gradlew assembleDebug

# Release 构建（带 ProGuard / R8 混淆，需要签名配置）
./gradlew assembleRelease

# 跑全部单元测试
./gradlew test

# 跑单个测试类（速度更快，调试时常用）
./gradlew test --tests "me.jbusdriver.modern.ui.movielist.MovieListViewModelTest"
```

> 第一次跑会下载较多依赖，建议连 WiFi。其它命令（clean 构建、插桩测试、全部变体）见 `AGENTS.md`。

## 📌 与项目其他文档的边界

| 文档 | 给谁看 |
|------|--------|
| `AGENTS.md` | AI 协作工具（Codex / Claude Code 等）—— 项目宪章与构建规范 |
| `docs/CODE_REVIEW.md` | 维护者 —— 已知问题与技术债记录 |
| `docs/superpowers/specs/` & `plans/` | 维护者 —— 每个功能的设计与实施计划 |
| `docs/learning/`（本目录） | 人类学习者 —— 系统性教学材料 |

如果你是第一次来：先按"从零学习者"路径读完前两章建立心智模型，再决定是继续顺序读还是按主题跳读。
