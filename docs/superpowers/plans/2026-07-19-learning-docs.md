# JBusDriver 学习文档 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `docs/learning/` — a 14-file Chinese learning documentation set that teaches modern Android development (Compose, Hilt, Coroutines/Flow, Repository, Room, Navigation 3, caching) through the JBusDriver codebase, aimed at developers with basic Android knowledge but no modern-Android experience.

**Architecture:** One markdown file per topic, plus a `README.md` index. Every chapter follows the same 6-section template (Why → Concept → Minimal example → Project usage → Pitfalls → Summary). Code examples are simplified rewrites with `file_path:line` pointers to real project files; the examples don't depend on line numbers so they survive code churn.

**Tech Stack:** Markdown (GitHub-flavored + mermaid diagrams where needed). No code generation. The "test" for each task is a verification pass: markdown renders cleanly, all `file_path` references point to existing files, template structure is intact.

## Global Constraints

From the approved spec `docs/superpowers/specs/2026-07-19-learning-docs-design.md`:

- **Language:** Chinese body text; code, class names, API names, and file paths stay in English. Key terms show English on first occurrence, e.g. "依赖注入（Dependency Injection, DI）".
- **Style:** Each chapter follows the 6-section template (§4 of the spec). 300–600 lines of markdown per chapter.
- **Code in chapters:** Simplified rewrites (10–30 lines) with Chinese comments; below each snippet cite `📁 项目对应位置：path/to/File.kt:line`. In-text first-mention of a file uses `file_path:line_number` format; later mentions use bare filename.
- **File naming:** `NN-slug.md` for chapters (zero-padded number), `A1-engineering.md` for the appendix, `README.md` for the index.
- **No emojis except the template's three:** `📖` (你将学到), `🔗` (前置章节), `📁` (项目对应位置) in chapter headers; `🔍` (深入阅读) in the footer.
- **No git commits unless the user explicitly asks.** AGENTS.md overrides the writing-plans default. The plan still lists `git add`/`git commit` as the *final* step of each task — the implementer should run `git status` to show changes are staged, then **ask the user before committing**.
- **Verification gates between phases:** Phase 1 produces README + Ch.01 + Ch.04 as a style calibration batch. Do not start Phase 2 until the user signs off on Phase 1's style.

---

## File Structure

```
docs/learning/
├── README.md                       ← Task 1 (Phase 1)
├── 01-project-overview.md          ← Task 2 (Phase 1)
├── 04-dependency-injection.md      ← Task 3 (Phase 1) — written out of numeric order on purpose: 04 is the style-calibration sample for "从零讲起 + 项目实战"
├── 02-kotlin-essentials.md         ← Task 4  (Phase 2)
├── 03-single-activity.md           ← Task 5  (Phase 2)
├── 05-coroutines-flow.md           ← Task 6  (Phase 2)
├── 06-repository-pattern.md        ← Task 7  (Phase 2)
├── 07-network-and-parsing.md       ← Task 8  (Phase 2)
├── 08-cache-strategy.md            ← Task 9  (Phase 2)
├── 09-compose-basics.md            ← Task 10 (Phase 2)
├── 10-viewmodel-state.md           ← Task 11 (Phase 2)
├── 11-navigation.md                ← Task 12 (Phase 2)
├── 12-persistence-and-settings.md  ← Task 13 (Phase 2)
└── A1-engineering.md               ← Task 14 (Phase 2)
```

Task 15 is a cross-validation pass (no new files) — Phase 3.

---

## Phase 1 — Skeleton + Style Calibration

Goal: land the index page + two reference chapters whose style the rest of Phase 2 will mimic. **Stop after Task 3 and request user review.**

### Task 1: README index page

**Files:**
- Create: `docs/learning/README.md`

**Interfaces:**
- Produces: a stable list of 13 chapter filenames + reading paths that every later chapter's "前置章节" header and "下一站" footer will link back to. The chapter filenames in §3 of the spec are the contract.

- [ ] **Step 1: Draft the README outline**

Open `docs/learning/README.md` and write these section headers (content blocks come next):

```markdown
# JBusDriver 学习文档

> 给有 Android 基础、但没做过完整 App 的开发者。读完能看懂本项目 80% 代码，能新增简单功能。

## 📚 这套文档是什么
## 🎯 适合谁读 / 不适合谁读
## 🗺️ 完整章节目录
## 🧭 三条推荐阅读路径
## 🚀 快速上手：构建与运行
## 📌 与项目其他文档的边界
```

- [ ] **Step 2: Write "📚 这套文档是什么"**

3–5 sentences. Key points:
- 这是一套**面向人类学习者**的中文教程（区别于 `AGENTS.md` 给 AI 协作工具看）。
- 用本项目作为活教材，每个概念都讲"通用背景 + 项目里怎么用"。
- 不替代 Kotlin/Android 官方教程；假设你能读 Java/Kotlin、知道 Activity 是什么。

- [ ] **Step 3: Write "🎯 适合谁读 / 不适合谁读"**

Two short lists. **适合**：懂 Android 四大组件基础、能读 Java/Kotlin 代码、没做过完整 App、不了解 DI/Compose/协程等现代范式。**不适合**：完全没碰过 Android、想从零学 Kotlin 语法、想看 NDK/性能优化等高级主题。

- [ ] **Step 4: Write "🗺️ 完整章节目录"**

A table with columns: 编号 | 文件 | 主题 | 一句话简介. Use the exact 13 filenames from the spec's §3. Example rows:

```markdown
| 编号 | 文件 | 主题 | 一句话简介 |
|------|------|------|-----------|
| 01 | [01-project-overview.md](01-project-overview.md) | 项目总览与现代 Android 范式 | 这个 App 做什么、目录怎么组织、为什么不用 XML+Activity |
| 02 | [02-kotlin-essentials.md](02-kotlin-essentials.md) | 现代 Kotlin 速览 | data class / sealed / 协程语法等本项目高频用法 |
| ... | ... | ... | ... |
| A1 | [A1-engineering.md](A1-engineering.md) | 工程实践：构建、ProGuard、测试、调试 | Gradle 变体、混淆规则、单元测试、Lint |
```

Write all 13 rows. Do not abbreviate.

- [ ] **Step 5: Write "🧭 三条推荐阅读路径"**

Three numbered subsections, each 3–5 lines. From spec §3.6:
1. **从零学习者**：01 → 02 → 03 → … → 12 → A1（按编号顺序）。
2. **查漏补缺**：直接跳到想学的章节；每章开头有"前置章节"提示。
3. **想看完整数据流**：05（协程/Flow）→ 06（Repository）→ 07（网络）→ 08（缓存）→ 10（ViewModel）串起来看。

- [ ] **Step 6: Write "🚀 快速上手：构建与运行"**

Pull the exact commands from `AGENTS.md` §Build Commands. Provide a minimal 4-command list:
- `./gradlew assembleDebug` — Debug 构建到 `app/build/outputs/apk/debug/jbus_debug_v*.apk`
- `./gradlew assembleRelease` — Release 构建（带 ProGuard，需要签名配置）
- `./gradlew test` — 跑单元测试
- `./gradlew test --tests "me.jbusdriver.modern.ui.movielist.MovieListViewModelTest"` — 跑单个测试类

Add a one-line note: 第一次跑会下载较多依赖，建议连 WiFi。

- [ ] **Step 7: Write "📌 与项目其他文档的边界"**

A small table (4 rows) reproducing spec §5.3:

| 文档 | 给谁看 |
|------|--------|
| `AGENTS.md` | AI 协作工具（Codex / Claude Code 等） |
| `docs/CODE_REVIEW.md` | 维护者 — 已知问题与技术债 |
| `docs/superpowers/specs/` & `plans/` | 维护者 — 每个功能的设计与实施计划 |
| `docs/learning/`（本目录） | 人类学习者 — 系统性教学材料 |

- [ ] **Step 8: Verify the README renders and links resolve**

Run:
```bash
ls docs/learning/
```
Expected: only `README.md` exists (chapters come later, so the chapter links will 404 — that's fine; verify only that filenames in the table match the spec exactly).

Spot-check: open the file in any markdown preview (or just `grep -c '^| ' docs/learning/README.md`) and confirm the chapter table has 13 data rows.

- [ ] **Step 9: Commit (only if user has approved)**

Run `git status` and show the user the staged file. **Do not run `git commit` until the user confirms** — AGENTS.md overrides the writing-plans default. Suggested message if approved:

```bash
git add docs/learning/README.md
git commit -m "docs(learning): add README index for learning documentation"
```

---

### Task 2: Chapter 01 — 项目总览与现代 Android 范式

**Files:**
- Create: `docs/learning/01-project-overview.md`

**Interfaces:**
- Produces: the high-level mental model every later chapter assumes — readers learn the package layout, the "5-layer architecture" vocabulary (`core / data / domain / ui / di`), and the contrast between "old Android" (XML + Activity + manual singleton) and "modern Android" (Compose + single Activity + Hilt + Flow).
- Consumes: nothing (this is the entry chapter).

**Key project references to cite (verified from survey):**
- `JBusApplication.kt:24` — `@HiltAndroidApp` Application class
- `ui/ModernMainActivity.kt:22` — the only Activity, `@AndroidEntryPoint`
- Top-level package layout: `core/ data/ domain/model/ ui/`
- `app/build.gradle.kts` dependencies block — Compose BOM, Hilt, Room, OkHttp, Jsoup, Navigation3

- [ ] **Step 1: Write the chapter header (template §4)**

```markdown
# 第 1 章：项目总览与现代 Android 范式

> 📖 本章你将学到：JBusDriver 这个 App 在做什么、它的代码是怎么组织的、为什么它和"老 Android"（XML + Activity）写法完全不一样。
> 🔗 前置章节：无
> 📁 项目对应目录：整个项目根目录 / `app/src/main/java/me/jbusdriver/modern/`
```

- [ ] **Step 2: Write §1.1 为什么需要"现代 Android 范式"**

Contrast old vs new. Open with a concrete pain point: "想象用 2014 年的方式写一个列表页：1 个 Activity + 1 个 XML 布局 + 1 个 Adapter + 1 个 AsyncTask + 手动 findViewById。然后业务告诉你列表数据要分页、要缓存、要响应主题切换、要处理屏幕旋转不丢状态——你开始写 800 行的 Activity。"

Three short paragraphs:
1. 命令式 UI（XML + findViewById + setState）的痛点：状态散乱、生命周期坑、UI 和数据耦合。
2. 手动 `new` 创建对象的痛点：测试难、生命周期管理难、单例满天飞。
3. 异步回调的痛点：嵌套回调、Thread + Handler 容易泄漏。

End with: "现代 Android 把这三件事分别用 **声明式 UI（Compose）**、**依赖注入（Hilt）**、**协程 + Flow** 来解决。本项目就是这三件套的完整实战。"

- [ ] **Step 3: Write §1.2 现代 Android 的三大范式是什么**

Three short subsections (3–5 lines each), terms with English on first mention:
1. **声明式 UI（Declarative UI）— Jetpack Compose**：UI 是状态的函数，状态变 → UI 自动变，不再手动 setText。
2. **依赖注入（Dependency Injection, DI）— Hilt**：对象由"容器"创建和注入，不在类里 `new`，方便测试和替换。
3. **结构化并发（Structured Concurrency）— Kotlin Coroutines + Flow**：异步代码像同步一样写；Flow 是"冷流/热流"的数据流抽象。

Close with a pointer: 这三件事各自有专章（第 4 / 9 / 5 章），本章只是让你心里有个大致地图。

- [ ] **Step 4: Write §1.3 最小示例：老 vs 新**

Side-by-side comparison table (no real project code, generic pseudo-Kotlin):

```kotlin
// ❌ 老写法：Activity 里手动初始化 + 设置状态
class OldActivity : Activity() {
  private var textView: TextView? = null
  private var repo: MovieRepository? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    textView = findViewById(R.id.title)       // 容易 NPE
    repo = MovieRepository()                  // 手动 new，测试难
    Thread {                                   // 容易泄漏
      val data = repo!!.load()
      runOnUiThread { textView?.text = data }  // 状态散乱
    }.start()
  }
}
```

```kotlin
// ✅ 新写法（本项目风格，简化版）
@HiltViewModel                                   // 第 4 章
class MovieViewModel @Inject constructor(
  private val repo: MovieRepository              // Hilt 注入，不写 new
) : ViewModel() {
  private val _state = MutableStateFlow(UiState())  // 第 5 章
  val state: StateFlow<UiState> = _state.asStateFlow()

  fun load() {                                   // 协程，不写 Thread
    viewModelScope.launch {
      _state.update { it.copy(data = repo.load() ) }  // 状态集中
    }
  }
}

@Composable                                      // 第 9 章
fun MovieScreen(vm: MovieViewModel = hiltViewModel()) {
  val state by vm.state.collectAsStateWithLifecycle()
  Text(text = state.data)                        // 状态变 → UI 自动刷新
}
```

Below the code: `📁 项目对应位置：这种"`@HiltViewModel + StateFlow + @Composable`"组合遍及 `ui/movielist/`、`ui/forum/`、`ui/detail/` 等。

- [ ] **Step 5: Write §1.4 项目中怎么用：JBusDriver 是个什么 App**

Subsection 1: **App 做什么的**（3–5 行）。描述这是一个浏览某影视网站（JavBus）的第三方 Android 客户端，核心功能：影片列表/详情、演员、论坛、收藏、本地视频关联。**不要放截图、不要描述具体内容**——只讲技术结构。

Subsection 2: **包结构概览**。Reproduce the 4-layer package tree from `AGENTS.md` §Architecture, simplified to 2 levels:

```
📁 项目对应位置：app/src/main/java/me/jbusdriver/modern/
me.jbusdriver.modern/
├── JBusApplication.kt   ← App 入口（第 3 章）
├── core/                ← 基础设施：网络、缓存、序列化、调度器
├── data/                ← 数据层：仓库、数据库、解析器、DI 模块
├── domain/model/        ← 纯数据模型（不依赖 Android）
└── ui/                  ← UI 层：Activity、各功能屏的 Screen + ViewModel
```

One-sentence responsibility for each layer.

Subsection 3: **App 是怎么启动的**（迷你版流程图，用 mermaid）：

```mermaid
flowchart LR
  A[系统启动] --> B[JBusApplication.onCreate]
  B --> C[Hilt 注入依赖图]
  C --> D[ModernMainActivity 创建]
  D --> E[setContent { JBusTheme { JBusNavigation() } }]
  E --> F[Compose 渲染 MainScreen]
```

Cite `📁 项目对应位置：JBusApplication.kt:24` 与 `ui/ModernMainActivity.kt:22`。详细解释见第 3 章。

Subsection 4: **依赖了哪些库**。Reproduce a compact table from `app/build.gradle.kts`:

| 库 | 干什么 | 第几章细讲 |
|----|--------|----------|
| Jetpack Compose + Material3 | 声明式 UI | 第 9 章 |
| Hilt | 依赖注入 | 第 4 章 |
| Coroutines + Flow | 异步与数据流 | 第 5 章 |
| Room | 数据库（收藏、历史） | 第 12 章 |
| DataStore | 偏好设置存储 | 第 12 章 |
| OkHttp + Jsoup | 网络 + HTML 解析 | 第 7 章 |
| Navigation 3 | 路由导航 | 第 11 章 |
| Coil + Telephoto | 图片加载与缩放 | （略，自行了解） |
| Gson | JSON 序列化与缓存 | 第 12 章 |

- [ ] **Step 6: Write §1.5 常见误区与调试技巧**

Three numbered pitfalls:
1. **"为什么搜不到 findViewById？"** —— 因为项目全部用 Compose，没有 XML 布局。新手别在 `res/layout/` 下找东西。
2. **"为什么 ViewModel 没有 constructor() 调用？"** —— 因为它由 Hilt 创建，看不见 `new` 不代表没有创建过程，见第 4 章。
3. **"我想看 App 跑起来时调了哪些代码"** —— 在 `Logcat` 过滤 `JBus` 标签，或在 Android Studio Profiler 抓方法调用。

- [ ] **Step 7: Write §1.6 小结与下一站**

3–5 line recap: 本章建立了"现代 Android 三大范式 + 项目四层结构 + 启动流程"的心智模型。

Close with:
```
下一站：第 2 章 现代 Kotlin 速览 —— 项目里高频出现的 Kotlin 语法一次过完。
```

- [ ] **Step 8: Write footer**

```markdown
---
🔍 深入阅读：
- 项目架构总览：见 `AGENTS.md` §Architecture
- 已知技术债：见 `docs/CODE_REVIEW.md`
- 现代开发范式官方指南：https://developer.android.com/modern-android-development
```

- [ ] **Step 9: Verify**

Run:
```bash
ls -la docs/learning/01-project-overview.md
wc -l docs/learning/01-project-overview.md
```
Expected: file exists, line count between 300 and 600.

Spot-check: confirm both cited paths exist:
```bash
test -f app/src/main/java/me/jbusdriver/modern/JBusApplication.kt && echo OK1
test -f app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt && echo OK2
```

- [ ] **Step 10: Stage (do not auto-commit)**

```bash
git add docs/learning/01-project-overview.md
git status
```
Show the user the result. Suggested message if they approve: `docs(learning): add chapter 01 project overview`.

---

### Task 3: Chapter 04 — 依赖注入与 Hilt（style-calibration sample）

**Files:**
- Create: `docs/learning/04-dependency-injection.md`

**Interfaces:**
- Consumes: vocabulary from Ch.01 (`@HiltViewModel`, package structure).
- Produces: the canonical example of "从零讲起 + 项目实战" style that all Phase 2 chapters must follow. The user will calibrate the style on this task before approving Phase 2.

**Key project references to cite (verified from survey):**
- `JBusApplication.kt:24` — `@HiltAndroidApp` triggers graph generation
- `data/di/DataModule.kt:68-70` — `@Module @InstallIn(SingletonComponent::class) abstract class DataModule` with `@Binds` methods (e.g. `bindMovieRepository` at 92-97)
- `data/di/DatabaseModule.kt:24-27` — `object DatabaseModule` with `@Provides` for Room DB
- `ui/movielist/MovieListViewModel.kt:160-164` — plain `@HiltViewModel class MovieListViewModel @Inject constructor(...)`
- `ui/movielist/LinkMovieListViewModel.kt:106-112` & `:524-527` — AssistedInject + AssistedFactory for nav-arg-carrying routes

- [ ] **Step 1: Write chapter header**

```markdown
# 第 4 章：依赖注入与 Hilt

> 📖 本章你将学到：什么是依赖注入（DI）、为什么 Android 项目需要 DI、Hilt 的核心注解怎么用，以及本项目怎么把 ~20 个仓库/数据库串起来。
> 🔗 前置章节：[第 1 章 项目总览](01-project-overview.md)（了解包结构即可）
> 📁 项目对应目录：`data/di/`、`JBusApplication.kt`、所有 `*ViewModel.kt`
```

- [ ] **Step 2: Write §4.1 为什么需要依赖注入**

Open with a concrete pain scenario:
"假设 `MovieListViewModel` 需要 3 个依赖：网络客户端、缓存、数据库。最直觉的写法是 `class MovieListViewModel { private val net = NetClient() ... }`。问题来了："
- 测试时怎么换成假的 NetClient？—— 不能，因为 `new` 写死在 VM 里。
- 10 个 VM 都要网络客户端，每个都 `new` 一份吗？—— 单例？单例又难测试。
- 数据库 DAO 需要 Context，VM 不该持有 Context，怎么办？

结论："依赖注入（DI）就是**把'创建对象'这件事从'使用对象'的代码里拿出来**，交给一个'容器'统一管理。"

- [ ] **Step 3: Write §4.2 依赖注入是什么**

Three short subsections:
1. **核心思想**：类的构造函数声明"我需要什么"（参数），不自己 `new`。一个叫"容器（Container）"的东西负责构造并传入。
2. **手写 DI 长什么样**（10 行简化示例）：

   ```kotlin
   // 手写一个最简容器
   object AppContainer {
     val netClient = NetClient()
     val db = JBusDatabase.getInstance()
     val movieRepo = MovieRepository(netClient, db)
     fun movieViewModel() = MovieListViewModel(movieRepo)
   }
   
   class MovieListActivity : Activity() {
     val vm = AppContainer.movieViewModel()   // 取
   }
   ```

   "能用，但很烦：每加一个类都要在容器里写一行；构造顺序要自己排；生命周期（Activity 销毁后 VM 该不该留？）要自己管。"

3. **框架的作用**：Hilt/Dagger 就是替你自动生成 `AppContainer` 那样的代码——你只要标几个注解告诉它"谁能创建"、"谁需要"，剩下的编译期生成。

- [ ] **Step 4: Write §4.3 最小示例：Hilt 四件套**

Show the minimal set of annotations needed. Use a fictional simplified example first:

```kotlin
// 1️⃣ App 类标记 @HiltAndroidApp —— 触发 Hilt 生成容器
@HiltAndroidApp
class MyApp : Application()

// 2️⃣ 需要被注入的类，构造函数标 @Inject
class UserRepository @Inject constructor(
  private val api: UserApi        // 这个 api 哪来的？看下面 Module
)

// 3️⃣ Module 告诉 Hilt 怎么造那些不能 @Inject constructor 的东西（比如接口、第三方对象）
@Module
@InstallIn(SingletonComponent::class)   // 这个 Module 装在哪个"组件"里
object AppModule {
  @Provides                              // "我提供 UserApi"
  fun provideUserApi(): UserApi = UserApi("https://example.com")
}

// 4️⃣ 接收注入：Activity / ViewModel / Service 用 @Inject 或 @HiltViewModel
@HiltViewModel
class UserViewModel @Inject constructor(
  private val repo: UserRepository       // Hilt 自动构造并传入
) : ViewModel()
```

Four annotations to remember: `@HiltAndroidApp`、`@Inject constructor(...)`、`@Module + @InstallIn + @Provides/@Binds`、`@HiltViewModel`/`@AndroidEntryPoint`.

- [ ] **Step 5: Write §4.4 项目中怎么用 — 三个真实文件**

Subsection 1: **App 入口**:

```markdown
📁 项目对应位置：app/src/main/java/me/jbusdriver/modern/JBusApplication.kt:24
```

```kotlin
@HiltAndroidApp                  // ← 这一行就让 Hilt 在编译期为整个 App 生成依赖图
class JBusApplication : Application(), ImageLoaderFactory {
  @Inject lateinit var htmlClient: HtmlClient   // ← 注入；不用自己 new
  @Inject lateinit var siteConfig: SiteConfig
  // ...
}
```

Subsection 2: **Module：把接口绑到实现（`@Binds`）**:

```markdown
📁 项目对应位置：app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt:68
```

```kotlin
@Module                                     // ← 告诉 Hilt 这是一个装"绑定"的类
@InstallIn(SingletonComponent::class)       // ← 装在"App 单例"作用域里
abstract class DataModule {
  @Binds @Singleton                          // ← "把 MovieRepository 绑到 DefaultMovieRepository"
  abstract fun bindMovieRepository(impl: DefaultMovieRepository): MovieRepository
  
  // 项目里有约 20 个这样的 @Binds 方法：SearchRepository、MagnetRepository、ForumRepository...
}
```

Add a one-liner: `@Binds` 用于"接口 → 实现"；如果构造函数能直接 `@Inject`，就**不**需要 `@Binds`。本项目仓库全是接口+实现对，所以全用 `@Binds`。

Subsection 3: **Module：构造第三方对象（`@Provides`）**:

```markdown
📁 项目对应位置：app/src/main/java/me/jbusdriver/modern/data/di/DatabaseModule.kt:24
```

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {                      // ← object（不是 abstract class）因为 @Provides 是有函数体的
  @Provides @Singleton
  fun provideJBusDatabase(@ApplicationContext context: Context): JBusDatabase =
    Room.databaseBuilder(context, JBusDatabase::class.java, "jbus.db").build()
  
  @Provides fun provideHistoryDao(db: JBusDatabase): HistoryDao = db.historyDao()
}
```

Explain why `@Provides` not `@Binds`: Room 数据库不能改构造函数（它是抽象类，由 Room 在运行时生成实现），所以必须用 `@Provides` 告诉 Hilt "造它的方式是 `Room.databaseBuilder(...)`"。

Subsection 4: **接收端：ViewModel 的两种写法**:

```markdown
📁 项目对应位置：app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt:160
```

```kotlin
@HiltViewModel                                  // ← 普通写法：没有路由参数
class MovieListViewModel @Inject constructor(
  private val repository: MovieRepository,       // ← 接口，Hilt 通过 DataModule 找实现
  private val localVideoRepository: LocalVideoRepository
) : ViewModel()
```

```markdown
📁 项目对应位置：app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt:106
```

```kotlin
@HiltViewModel(assistedFactory = LinkMovieListViewModel.Factory::class)
class LinkMovieListViewModel @AssistedInject constructor(
  private val repository: MovieRepository,
  @Assisted private val navKey: RouteLinkMovies     // ← 路由参数，运行时才知道
) : ViewModel() {
  
  @AssistedFactory
  interface Factory {                                // ← Hilt 帮你生成实现
    fun create(navKey: RouteLinkMovies): LinkMovieListViewModel
  }
}
```

One-paragraph explanation: 普通 `@HiltViewModel` 用于没有运行时参数的 VM；带路由参数的 VM 用 `@AssistedInject` + `@AssistedFactory`，第 11 章讲导航时会再过一遍。

- [ ] **Step 6: Write §4.5 常见误区与调试技巧**

Four numbered pitfalls (high teaching value):

1. **"我标了 `@Inject` 但 Hilt 报错说找不到绑定"** —— 检查是不是接口类型。接口要 `@Binds` 或 `@Provides`，不能只 `@Inject constructor`。
2. **"`@Binds` 和 `@Provides` 用反"** —— `@Binds` 用于"接口→实现，无构造逻辑"；`@Provides` 用于"有构造逻辑（如 Room、OkHttp）"。混用的编译错误是 `@Binds method must be abstract`。
3. **"想拿 Context，怎么注入？"** —— 用 `@ApplicationContext context: Context` 或 `@ActivityContext`。直接拿 Activity 会内存泄漏。
4. **"ViewModel 里能注入 Activity 吗？"** —— **不能**，永远不能。VM 的生命周期比 Activity 长（旋转屏幕 Activity 销毁但 VM 留下）。需要 Context 就用 ApplicationContext。

- [ ] **Step 7: Write §4.6 小结与下一站**

Recap: DI = 把"创建对象"和"使用对象"分离；Hilt 用四个注解（`@HiltAndroidApp / @Inject / @Module+@InstallIn / @HiltViewModel`）自动化这件事；项目里 `data/di/` 两个 Module 串起全部仓库与数据库。

```
下一站：第 5 章 协程与 Flow —— 既然有了 Repository，它是怎么"异步"返回数据的？
```

- [ ] **Step 8: Write footer**

```markdown
---
🔍 深入阅读：
- Hilt 官方文档：https://dagger.dev/hilt/
- 项目所有绑定：`data/di/DataModule.kt`、`data/di/DatabaseModule.kt`
- AGENTS.md 里的代码规则："`@Binds` Repository 接口 → 实现"
```

- [ ] **Step 9: Verify Phase 1 sample**

Run:
```bash
wc -l docs/learning/04-dependency-injection.md
test -f app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt && echo OK1
test -f app/src/main/java/me/jbusdriver/modern/data/di/DatabaseModule.kt && echo OK2
test -f app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt && echo OK3
test -f app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt && echo OK4
```
Expected: 04 file between 300–600 lines; all four project files exist.

- [ ] **Step 10: Stage + request Phase 1 sign-off**

```bash
git add docs/learning/04-dependency-injection.md
git status
```

**Stop.** Tell the user: "Phase 1 完成（README + Ch.01 + Ch.04）。请通读这三份，告诉我风格、深度、长度是否符合预期。确认后我会按这套风格批量产出 Phase 2 的 11 章。"

Do NOT start Task 4 until the user approves Phase 1.

---

## Phase 2 — Batch Production

Goal: write the remaining 11 chapters following the style calibrated in Phase 1. Each chapter is one task. The implementer should re-read `docs/learning/04-dependency-injection.md` before starting any Phase 2 task to internalize the style.

**Cross-cutting verification steps that every Phase 2 task ends with:**
- `wc -l docs/learning/<file>` should report 300–600 lines.
- `test -f app/src/main/java/me/jbusdriver/modern/<cited paths>` should pass for every cited path.
- Run a quick `rg "📁 项目对应位置" docs/learning/<file>` and spot-check that the paths in 3–5 random citations exist.
- Run `git status` and show the user. **Do not auto-commit** — ask the user.

For brevity in this plan, Phase 2 tasks specify the **content outline and key project references** but not the full code-block text. The implementer writes each section following the template established in Phase 1's Ch.04 (6-section structure, simplified example + file pointer, pitfalls from CODE_REVIEW/AGENTS, recap + next-stop footer).

---

### Task 4: Chapter 02 — 现代 Kotlin 速览

**Files:**
- Create: `docs/learning/02-kotlin-essentials.md`

**Content outline:**
- §2.1 为什么需要现代 Kotlin 语法：Java 写起来啰嗦、null 不安全、模式匹配弱。
- §2.2 项目里高频出现的 8 个语法点（每个 3–5 行示例 + 一句话用途）：
  1. `data class` — 自动生成 equals/hashOf/toString/copy
  2. `sealed class/interface` — 闭合类型层级，配合 `when`
  3. `scope functions`：`let / run / apply / also / with` 的区别速记
  4. `?` / `?:` / `!!` — null 安全三件套
  5. `lambda` 与高阶函数（`map / filter / forEach / groupBy`）
  6. `by lazy` / `remember{}`（后者留到第 9 章）
  7. `suspend` 函数（一带而过，第 5 章细讲）
  8. `extension function`（项目里 `Context.sp` 等）
- §2.3 最小示例：一段同时用上面 5 个语法的代码。
- §2.4 项目里怎么用：每个语法给 1 个真实引用。References (verified from survey):
  - `domain/model/Movie.kt` — data class
  - `core/cache/CacheModels.kt:12-19` — `sealed interface CachedLoadEvent`
  - `core/BaseExtension.kt` — extension functions
  - 任何 `*ViewModel.kt` 的 `viewModelScope.launch { ... }` — suspend/lambda
- §2.5 常见误区：`!!` 滥用、`apply` 和 `let` 用反、在 `forEach` 里 `return` 想跳出外层。
- §2.6 小结 + 下一站（第 3 章）。

- [ ] **Step 1: Write all six template sections following the Ch.04 style**
- [ ] **Step 2: Verify** — `wc -l`, file-path checks
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 5: Chapter 03 — 单 Activity 架构

**Files:**
- Create: `docs/learning/03-single-activity.md`

**Content outline:**
- §3.1 为什么：多 Activity 的痛点（状态跨 Activity 难共享、转场动画受限、返回栈混乱）。
- §3.2 是什么：一个 Activity 承载所有 UI，UI 切换靠"导航"（Fragment/Compose Navigation/Nav3）。术语：Single-Activity Architecture。
- §3.3 最小示例：对比一个多 Activity 列表→详情，与单 Activity + Compose Nav。
- §3.4 项目中怎么用：
  - `📁 ui/ModernMainActivity.kt:22` — 全项目唯一 Activity，`@AndroidEntryPoint`，`setContent { JBusTheme { JBusNavigation() } }`
  - `📁 ui/Navigation.kt:41` — `JBusNavigation` 是 Compose 入口
  - `📁 JBusApplication.kt:24` — Application 启动流程
  - 用 mermaid 画一张 "系统启动 → Application → Activity → setContent → Composable" 的流程
- §3.5 常见误区：
  - 找不到其它 Activity？—— 没有了，全局只有一个
  - 想新增页面？—— 是新增 Composable + Route，不是新增 Activity（第 11 章细讲）
  - `@AndroidEntryPoint` 漏标 → Hilt 注入会崩
- §3.6 小结 + 下一站（第 4 章）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 6: Chapter 05 — 协程与 Flow 异步编程

**Files:**
- Create: `docs/learning/05-coroutines-flow.md`

**Content outline:**
- §5.1 为什么：回调地狱、Thread 难管理、生命周期难对齐。
- §5.2 是什么：
  - 协程（Coroutine）= 轻量级线程，可挂起（suspend）可恢复
  - `Flow<T>` = 冷流，类似"异步的 List"
  - `StateFlow<T>` = 热流，永远有值、与状态绑定
  - `SharedFlow<T>` = 热流，事件广播
  - 三者的差别用一张速查表
- §5.3 最小示例：`suspend fun` / `Flow` / `StateFlow` 各一个 10 行代码。
- §5.4 项目中怎么用：
  - `📁 data/repository/MovieRepository.kt:45` — `suspend fun loadPage(...)` 与 `fun observePage(...): Flow<...>`
  - `📁 ui/movielist/MovieListViewModel.kt:167-175` — `MutableStateFlow` + `asStateFlow()` + `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`
  - `📁 core/cache/CacheModels.kt:12-19` — `sealed interface CachedLoadEvent<out T>` 三态（Cached / Fresh / Failure）
- §5.5 常见误区（重要，对应 AGENTS.md 里的规则）：
  - VM 不要用 `LiveData` 或 callback；用 `StateFlow` / `SharedFlow`
  - `Flow` 收集必须配合 `lifecycleScope` / `viewModelScope`，否则泄漏
  - `stateIn` 别忘了 `SharingStarted.WhileSubscribed(5_000)`
  - `suspend` 函数不能在主线程调网络；切线程用 `withContext(Dispatchers.IO)`
- §5.6 小结 + 下一站（第 6 章）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 7: Chapter 06 — Repository 模式与项目数据流

**Files:**
- Create: `docs/learning/06-repository-pattern.md`

**Content outline:**
- §6.1 为什么：UI 直接调网络/数据库会导致：缓存策略散在 UI 里、测试 UI 必须联网、UI 和数据格式强耦合。
- §6.2 是什么：Repository 是"数据源的中介"——UI 问 Repository 要数据，Repository 决定从缓存、网络、数据库哪里取。术语：Repository Pattern。
- §6.3 最小示例：一段 `interface Repo + class RepoImpl + ViewModel 调用`。
- §6.4 项目中怎么用：
  - `📁 data/repository/MovieRepository.kt:35-181` — 接口（声明 `suspend load*` 与 `default observe*`）
  - `📁 data/repository/MovieRepository.kt:194-365` — `DefaultMovieRepository @Inject constructor(fetcher, cacheStore, siteConfig)` 实现
  - 数据流总览图（mermaid）：`Composable → ViewModel → Repository → (Cache + Net + Parser) → 数据模型`
  - 一段 §"为什么接口 + 实现分开"：方便测试 + 切换实现（Hilt 的 `@Binds` 已经用了）
- §6.5 常见误区：
  - Repository 不要返回 `Document`（Jsoup 类型）—— 必须返回领域模型，第 7 章会讲为什么
  - VM 别在多个地方直接调 NetClient —— 一律走 Repository
  - Repository 接口要稳，实现可以换（这就是项目里 DataModule `@Binds` 的意义）
- §6.6 小结 + 下一站（第 7 章）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 8: Chapter 07 — 网络层与 HTML 解析

**Files:**
- Create: `docs/learning/07-network-and-parsing.md`

**Content outline:**
- §7.1 为什么：移动网络不稳定、目标站有反爬、HTML 结构会变。
- §7.2 是什么：
  - `OkHttp` — HTTP 客户端
  - `Jsoup` — HTML 解析（类似 XML 的 DOM 操作）
  - 反爬策略：cookie + WebView fallback
- §7.3 最小示例：用 OkHttp 拿一段 HTML，用 Jsoup 提取一个标题。
- §7.4 项目中怎么用：
  - `📁 core/http/NetClient.kt:45` — `object NetClient`，共享 `okHttpClient`，`fetchDocument(url)` 把回调包成 `suspend`
  - `📁 core/http/HtmlClient.kt:31` — `DefaultHtmlClient @Inject constructor(browserSessionClient)`，决定走 OkHttp 还是 WebView
  - `📁 core/http/WebViewFactory.kt:9` — 创建 WebView 的工厂（Hilt 注入，方便测试 mock）
  - `📁 data/parser/MovieHtmlParser.kt:30` — `loadMovieFromDoc(doc, baseUrl): List<Movie>`，CSS 选择器 `.movie-box`
  - 关键点：解析是**纯函数**，没有副作用，输入 Document 输出领域模型
- §7.5 常见误区：
  - 别在主线程做 HTTP / Jsoup 解析
  - 解析器不要写状态（保持纯函数）
  - 新增解析要看目标站实际 HTML 结构，不是看代码
- §7.6 小结 + 下一站（第 8 章）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 9: Chapter 08 — 缓存与 SWR 策略

**Files:**
- Create: `docs/learning/08-cache-strategy.md`

**Content outline:**
- §8.1 为什么：每次进列表都下载很慢、流量贵；但缓存过期了又会显示老数据。
- §8.2 是什么：
  - LRU 内存缓存
  - 磁盘缓存（FileCache）
  - SWR（Stale-While-Revalidate）：先返回老数据让 UI 立即显示，后台同时拉新数据，到了再更新
- §8.3 最小示例：一段 `class Cache { memory + disk }` + 一段 SWR 的伪流程。
- §8.4 项目中怎么用：
  - `📁 core/cache/CacheStore.kt:23-28` — 接口
  - `📁 core/cache/CacheStore.kt:30-85` — `DefaultCacheStore`：LruCache + FileCache
  - `📁 core/cache/CacheStore.kt:87-100` — `lruCached()`（仅内存）
  - `📁 core/cache/CacheStore.kt:102-123` — `persistentCached()`（内存 + 磁盘）
  - `📁 core/cache/CacheStore.kt:209-260` — `observeCached()` SWR 核心
  - `📁 core/cache/PagedSwrState.kt` — 分页 SWR 的辅助类
  - mermaid 时序图：UI 订阅 → 立即收 Cached → 后台 fetch → 收 Fresh → 决定是否应用
- §8.5 常见误区：
  - 缓存键要包含 URL/参数，不要只写 "movies"（项目用 `SiteCacheKey` 加上站点）
  - SWR 的"应用 Fresh"要看用户是否在顶部（`AtTopGate`），别在用户滚动时打乱位置
  - 缓存对象必须可序列化（Gson），R8 混淆要 keep 字段（见 A1 章）
- §8.6 小结 + 下一站（第 9 章）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 10: Chapter 09 — Jetpack Compose 基础

**Files:**
- Create: `docs/learning/09-compose-basics.md`

**Content outline:**
- §9.1 为什么：XML + findViewById 状态散乱、易 NPE；命令式 UI 难测试。
- §9.2 是什么：
  - `@Composable` 函数 = UI 是状态的函数
  - `remember {}` / `mutableStateOf` = 记忆状态
  - `Modifier` = 链式样式
  - 重组（Recomposition）= 状态变 → 函数重新执行
- §9.3 最小示例：一段可独立运行的 Hello Compose（10-20 行），含 `remember`、`mutableStateOf`、`Column`、`Text`、`Button`。
- §9.4 项目中怎么用：
  - `📁 ui/components/CollectButton.kt:14-39` — 最简单的 Composable，看 `@Composable fun` + `IconButton` + `Icon`
  - `📁 ui/components/AppAsyncImage.kt:64-81` — 看参数怎么传、Modifier 怎么用
  - `📁 ui/theme/Theme.kt:112-163` — `JBusTheme` 是整个 App 的主题入口
  - 简要提一句 Material3 与 `MaterialTheme` 的关系（颜色/字体/形状）
- §9.5 常见误区（**这部分非常重要**，对应 AGENTS.md 多条规则）：
  - `remember` 和 `rememberSaveable` 区别
  - 不要在 `@Composable` 里写副作用（DB / 网络），用 `LaunchedEffect` / `SideEffect`
  - 列表用 `LazyColumn` + `key = { it.id }`，别忘了 key 否则滚动状态乱
  - ` mutableStateOf` 不要用 `var`，要用 `by remember { mutableStateOf(...) }` 或 `MutableStateFlow`
  - Compose 函数不能有副作用、不能依赖调用顺序
- §9.6 小结 + 下一站（第 10 章）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 11: Chapter 10 — ViewModel + StateFlow

**Files:**
- Create: `docs/learning/10-viewmodel-state.md`

**Content outline:**
- §10.1 为什么：UI 旋转屏幕就丢状态、把数据逻辑写在 Composable 里乱。
- §10.2 是什么：
  - `ViewModel` — 跨越配置变化（如屏幕旋转）持有状态
  - `StateFlow<T>` — 永远有当前值的状态流
  - `UiState` 数据类 — 集中所有 UI 状态
  - StateReducer 模式 — 把"老状态 + 事件 → 新状态"写成纯函数
- §10.3 最小示例：一段 `data class UiState + @HiltViewModel + StateFlow + 一个 reducer fun`。
- §10.4 项目中怎么用：
  - `📁 ui/movielist/MovieListViewModel.kt:160-528` — 主流程：`loadFirstPage` / `revalidate` / `loadMore` / `refresh`
  - `📁 ui/movielist/MovieListViewModel.kt:167-175` — `_uiState` / `uiState` / `stateIn(...)`
  - `📁 ui/movielist/MovieListStateReducers.kt:1-105` — reducer 扩展函数集
  - 模式：VM 调 Repository → 收 `CachedLoadEvent` → 调 `state.applyFirstPageFresh(entry)` → state 更新 → UI 自动刷新
- §10.5 常见误区（**AGENTS.md 明确规则**）：
  - **ViewModel 不能暴露 callback 给 UI**；用 `StateFlow` / `SharedFlow`
  - **不要在 VM 里持有 `Context` / `View` / `Activity`** —— 内存泄漏
  - **`StateFlow` 用 `asStateFlow()` 暴露给 UI**，`MutableStateFlow` 留在 VM 内部
  - **事件用 `SharedFlow`，状态用 `StateFlow`**（事件不能"重放"）
  - reducer 要纯函数，别在 reducer 里发请求
- §10.6 小结 + 下一站（第 11 章）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 12: Chapter 11 — Navigation 3 与路由

**Files:**
- Create: `docs/learning/11-navigation.md`

**Content outline:**
- §11.1 为什么：手动 `startActivity` + Intent 难传递复杂参数、转场动画难统一、返回栈混乱。
- §11.2 是什么：
  - Navigation 3（androidx.navigation3）—— Jetpack 较新的导航库
  - `NavKey` —— 类型安全的路由标识
  - `NavDisplay` —— 路由容器
  - 转场动画 —— iOS 风格的 slide / scale / fade
- §11.3 最小示例：定义两个 `NavKey` + 一个 `NavDisplay` + `entry<X>{}`，10-20 行。
- §11.4 项目中怎么用：
  - `📁 ui/NavigationKeys.kt:6-48` — 全部 8 个 `@Serializable NavKey`（RouteMain / RouteSearch / RouteMovieDetail / ...）
  - `📁 ui/Navigation.kt:41-292` — `JBusNavigation` 入口 + iOS 风格转场 + `entry<RouteXxx> { ... }` 模板
  - 路由带参数怎么传：`RouteMovieDetail(movieUrl, censorType)`
  - VM 怎么拿路由参数：`@AssistedInject` + `@Assisted navKey: RouteXxx`（回扣第 4 章）
- §11.5 常见误区：
  - 新增页面要改三处：NavigationKeys（新 key）+ Navigation.kt（新 `entry<...>`）+ 对应 Screen/ViewModel
  - 路由参数要 `@Serializable`，否则运行时崩
  - 转场动画的 `metadata` 别忘了传
- §11.6 小结 + 下一站（第 12 章）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 13: Chapter 12 — Room、DataStore、Gson 序列化

**Files:**
- Create: `docs/learning/12-persistence-and-settings.md`

**Content outline:**
- §12.1 为什么：列表数据要持久化（收藏、历史）、设置项要存储、缓存要序列化到磁盘。
- §12.2 是什么（三件并列）：
  - **Room** —— SQLite 的封装，注解定义实体和查询
  - **DataStore** —— 类型安全的 SharedPreferences 替代
  - **Gson + 自定义 TypeAdapter** —— 处理多态序列化
- §12.3 最小示例：三段独立代码，各 10 行。
- §12.4 项目中怎么用：
  - **Room**：
    - `📁 data/db/JBusDatabase.kt:18` 与 `data/db/CollectDatabase.kt:20` — `@Database` 注解
    - `📁 data/db/entity/LinkItem.kt:19-36` — `@Entity` 表定义
    - `📁 data/db/dao/CategoryDao.kt:17-48` 与 `HistoryDao.kt:16-51` — `@Dao` 接口，`@Query` 返回 `Flow<List<...>>`
    - Room 由 `DatabaseModule` 提供（回扣第 4 章）
  - **DataStore**：
    - `📁 data/settings/AppSettingsStore.kt:71-160` — `@Singleton class AppSettingsStore @Inject constructor(context, mirrorScanner)`，`flowOf(key, default)` + `stateIn(scope, Eagerly, default)`
    - `📁 data/settings/UiPrefsStore.kt:18-52` — UI 偏好（排序方式等）
  - **Gson**：
    - `📁 core/GsonExt.kt:32-53` — 全局 `GSON` 单例，注册 `ContentBlockAdapterFactory`
    - `📁 core/serialization/ContentBlockJsonAdapter.kt:20-123` — 多态 `ContentBlock` 的 TypeAdapter（forum 富文本）
    - 解释：缓存对象写到磁盘前要用 GSON 序列化，多态类型不能默认序列化（要自定义 adapter）
- §12.5 常见误区（**AGENTS.md 明确规则**）：
  - **R8/Gson 改动后要跑 release smoke test**，否则线上可能反序列化崩
  - **新增 Gson 模型要加 ProGuard keep**（见 A1 章）—— 字段名被混淆就反序列不出来
  - **删除/重命名字段要加 `@SerializedName` 别名**，否则老缓存读不回来
  - **DataStore 不要从主线程写** —— 用 `suspend` 函数
  - **Room 查询返回 Flow 会自动响应表变化**，不用手动 refresh
- §12.6 小结 + 下一站（附录 A1）。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

### Task 14: Appendix A1 — 工程实践：构建、ProGuard、测试、调试

**Files:**
- Create: `docs/learning/A1-engineering.md`

**Content outline:**
- §A1.1 为什么：能写代码 ≠ 能上线；构建、混淆、测试、调试是工程化必备。
- §A1.2 是什么（概览）：
  - Gradle 构建变体（debug / release）
  - ProGuard / R8 混淆
  - 单元测试 / 插桩测试
  - Lint / LeakCanary
- §A1.3 最小示例：
  - 一段 `app/build.gradle.kts` 节选，标出 `buildTypes { release { ... } }` 关键开关
  - 一段最简 JUnit 测试代码
- §A1.4 项目中怎么用：
  - **构建变体**：`📁 app/build.gradle.kts:98-123` — debug/release 差异（applicationIdSuffix、isMinifyEnabled、isShrinkResources、CACHE_REFRESH_TEST_MODE）
  - **ProGuard 规则**：`📁 app/proguard-rules.pro` — 重点解释 `-keep class me.jbusdriver.modern.domain.model.* { !static !transient <fields>; }` 这条为什么要保留所有 Gson 模型字段
  - **单元测试**：`📁 app/src/test/` 目录布局；提一个示例 `MovieListViewModelTest`
  - **插桩测试**：`📁 app/src/androidTest/`，需要模拟器
  - **APK 命名**：`jbus_{buildType}_v{versionName}.apk`
- §A1.5 常见误区（重要）：
  - Debug 能跑 Release 崩 → 99% 是混淆没 keep（Gson 字段、反射类）
  - 改了数据库 schema 没升 version → Room 立刻崩
  - 改了构建变体忘记改 BuildConfig 字段 → 测试模式不生效
  - **不要直接 commit 到 release 分支** —— 项目规范要求在 develop 或 feature 分支
- §A1.6 小结（最后一章）：
  - 回顾整套学习文档覆盖了什么
  - 推荐继续阅读：`AGENTS.md` / `docs/CODE_REVIEW.md` / 项目 spec & plan
- §A1.6 footer 的"深入阅读"放 Android Gradle Plugin 官方文档、ProGuard 手册、LeakCanary GitHub。

- [ ] **Step 1: Write six template sections following Ch.04 style**
- [ ] **Step 2: Verify**
- [ ] **Step 3: Stage + ask user to commit**

---

## Phase 3 — Cross-Validation

Goal: after all 13 chapters + README are written, do a single consistency pass.

### Task 15: Cross-validation pass

**Files:**
- Modify: `docs/learning/README.md` (final status update)
- Modify: any chapter with broken links/references (only if found)

- [ ] **Step 1: Check chapter-table alignment in README**

```bash
ls docs/learning/*.md
```
Manually compare against the table in `docs/learning/README.md`. All 14 files must appear, names must match exactly.

- [ ] **Step 2: Check `前置章节` chain**

For each chapter, grep the "🔗 前置章节" line:
```bash
rg "^> 🔗 前置章节" docs/learning/
```
Spot-check that referenced chapters exist and the chain makes sense (e.g., Ch.10 shouldn't reference Ch.12).

- [ ] **Step 3: Check `下一站` chain**

```bash
rg "下一站" docs/learning/
```
Confirm each chapter (except A1) points to a chapter that exists.

- [ ] **Step 4: Spot-check 5 random file_path references per chapter**

For 3 chapters chosen at random, extract the `📁 项目对应位置` paths and verify each exists:
```bash
rg "📁 项目对应位置" docs/learning/04-dependency-injection.md
# for each path that looks like app/src/...: test -f <path>
```
Report any broken references.

- [ ] **Step 5: Check terminology consistency**

Spot-check that key terms are translated consistently across chapters:
- Dependency Injection = 依赖注入（不混用"控制反转"作为同义词）
- Coroutines = 协程
- Composable = Composable / 可组合函数（任选其一并保持一致）
- Repository = Repository / 仓库（任选其一并保持一致）
- Recomposition = 重组

```bash
rg "依赖注入|控制反转" docs/learning/
rg "可组合函数|@Composable 函数|Composable 函数" docs/learning/
```
If inconsistencies found, fix them in place.

- [ ] **Step 6: Check no orphan emojis**

```bash
rg "[📖🔗📁🔍]" docs/learning/ | rg -v "📖 本章|🔗 前置|📁 项目对应位置|🔍 深入阅读"
```
Expected: empty (the four template emojis only appear in their template slots). The README has additional `📚 / 🎯 / 🗺️ / 🧭 / 🚀 / 📌` which are intentional.

- [ ] **Step 7: Final markdown render check**

Open `docs/learning/README.md` in a markdown preview (GitHub or Android Studio). Confirm:
- All chapter links are clickable (no 404 within docs/learning/)
- Tables render correctly
- Mermaid diagrams (if any) render

- [ ] **Step 8: Stage + final commit (if user approves)**

```bash
git status
git add docs/learning/
```
Show the user the final state. Suggest commit message:
```
docs(learning): complete 13-chapter learning documentation
```
Do NOT commit until user explicitly approves.

---

## Self-Review Notes

### Spec coverage

- §1 Reader profile & goals → addressed by Global Constraints + README content (Task 1, Steps 2–3)
- §2 Style guide → addressed by Global Constraints (binding on every task)
- §3 Chapter list (12 + A1) → Tasks 2–14, one per chapter
- §4 Per-chapter template → enforced in every chapter task's step list
- §5 File organization → File Structure block above + Task 1
- §6 Implementation path → Phase 1 (Tasks 1–3) → user gate → Phase 2 (Tasks 4–14) → Phase 3 (Task 15)
- §6 Acceptance criteria → end-of-task verification steps + Task 15 cross-validation

### Placeholder scan

No "TBD" / "TODO" / "implement later" in this plan. Phase 2 tasks specify **content outlines** rather than full code because the style must be calibrated against Phase 1 first — this is a deliberate two-phase design decision, not a placeholder. Phase 2 tasks do include: file references (with verified paths from the survey), section-by-section topic list, and the pitfalls each chapter must surface.

### Type consistency

- Chapter filenames in File Structure block match §3 of spec and Task 1's table exactly.
- Project file paths cited in chapter tasks were verified by the explore subagent survey before writing this plan.
- Task 3 (Ch.04) is intentionally written before Tasks 4–14 in the file structure section because Phase 1 writes Ch.04 before Ch.02/03. Numeric task order ≠ numeric chapter order; this is called out in the File Structure note.

### Scope

This plan covers one cohesive deliverable: a documentation set. Implementation is decomposed into 15 tasks with one user-gate after Phase 1. The plan is large but each task is independently reviewable and self-contained.
