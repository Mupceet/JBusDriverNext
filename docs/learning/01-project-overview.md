# 第 1 章：项目总览与现代 Android 范式

> 📖 本章你将学到：JBusDriver 这个 App 在做什么、它的代码是怎么组织的、为什么它和"老 Android"（XML + Activity）写法完全不一样。
> 🔗 前置章节：无
> 📁 项目对应目录：整个项目根目录 / `app/src/main/java/me/jbusdriver/modern/`

---

## 1.1 为什么需要"现代 Android 范式"

想象用 2014 年的方式写一个列表页：1 个 Activity + 1 个 XML 布局 + 1 个 Adapter + 1 个 AsyncTask + 手动 `findViewById`。然后业务告诉你列表数据要分页、要缓存、要响应主题切换、要处理屏幕旋转不丢状态——你开始写 800 行的 Activity。

这是上一代 Android 工程师每天面对的现实。老的命令式写法有三大痛点让你越写越累：

### 痛点 1：命令式 UI——状态散乱、生命周期坑

老写法里 UI 是"被命令"的：每次数据变了都要记得手动 `textView.setText(...)`、`recyclerView.notifyDataSetChanged()`。状态散落在 Adapter、Activity、Intent、Bundle 各处，谁也说不清"现在屏幕上显示的到底是哪一份数据"。

再加上 Activity 生命周期复杂——旋转屏幕重建、后台被杀回前台、`onDestroy` 时机不确定——很容易写出 NPE、显旧数据、闪一下再变等诡 Bug。一个典型的崩溃栈：

```
NullPointerException: findViewById(R.id.title) must not be null
  at OldActivity.onCreate(OldActivity.kt:15)
```

根因不是代码写错，而是**状态和 UI 没有绑死的机制**，全靠程序员记。

### 痛点 2：手动 `new`——测试难、单例满天飞

需要个网络客户端？`val net = NetClient()`。需要个数据库？`val db = DB.getInstance(this)`。10 个 Activity 都要？复制粘贴？还是抽个 `object` 单例？单例又测不了（不能换假的）。

当对象之间依赖关系越来越多，构造顺序、作用域、生命周期管理很快会变成一团乱麻：

- 想给 `MovieListViewModel` 写单测，发现它内部 `new` 了 `NetClient`，没法换成假数据源。
- 5 个 Activity 各自 `new` 一份缓存，缓存命中率几乎为零。
- 单例持有 `Context`，Activity 销毁了单例还在，内存泄漏。

### 痛点 3：异步回调——嵌套回调、Thread + Handler 容易泄漏

老写法里异步基本靠 `Thread + Handler` 或 `AsyncTask`。要串联 3 个网络请求？就 `request1 { request2 { request3 { runOnUiThread { updateUI() } } } }`，俗称"回调地狱（Callback Hell）"。

```kotlin
// 老写法：3 层嵌套，可读性几乎为零
Thread {
  val token = login()
  runOnUiThread {
    Thread {
      val data = fetch(token)
      runOnUiThread {
        Thread {
          val detail = loadDetail(data.id)
          runOnUiThread { showDetail(detail) }   // 4 层缩进
        }.start()
      }
    }.start()
  }
}.start()
```

更糟的是 Activity 销毁时线程还在跑，持有 Activity 引用就**内存泄漏**；忘记切主线程更新 UI 又会**崩**。

> 现代 Android 把这三件事分别用 **声明式 UI（Compose）**、**依赖注入（Hilt）**、**协程 + Flow** 来解决。本项目就是这三件套的完整实战。

---

## 1.2 现代 Android 的三大范式是什么

在动手之前，先认识三个名词。每个名词在本书后面都有专章细讲，这里只要建立大致印象。

### 1. 声明式 UI（Declarative UI）— Jetpack Compose

不再写"先 `findViewById`，再 `setText`"。改为**用函数描述 UI 长什么样**：UI 是状态的函数，状态变了 UI 自动变。

```kotlin
// 一句话描述 UI：name 一变，界面自动更新
@Composable fun Greeting(name: String) {
  Text("Hi, $name")
}
```

你不用关心"怎么更新"，框架会在状态变化时自动**重组（Recomposition）**，把受影响的 Composable 重新跑一遍。

### 2. 依赖注入（Dependency Injection, DI）— Hilt

不再在类里写 `val repo = MovieRepository()`。改为**在构造函数里声明"我需要什么"**，由一个"容器（Container）"负责构造并传进来。

```kotlin
// 不写 new，构造函数声明依赖
class MovieViewModel @Inject constructor(
  private val repo: MovieRepository   // ← 容器帮我造好传进来
) : ViewModel()
```

`repo` 是接口也好、单例也好、Mock 实现也好，使用方都不用关心——这就是 DI 的核心价值：**把"创建对象"和"使用对象"分开**。

### 3. 结构化并发（Structured Concurrency）— Kotlin Coroutines + Flow

不再写 `Thread { handler.post { ... } }`。改为**像写同步代码一样写异步**：`suspend fun load(): Data`；多个异步事件用 `Flow<T>` 串成一个数据流。

```kotlin
// 看起来像同步，实际上是异步的
suspend fun loadAll(): List<Movie> {
  val token = login()           // ← suspend，自动挂起不阻塞
  val data = fetch(token)
  return parse(data)
}
```

生命周期自动绑定到 `viewModelScope`，VM 销毁时协程自动取消，**不会泄漏**。

> 这三件事各自有专章（第 4 / 9 / 5 章），本章只是让你心里有个大致地图。

---

## 1.3 最小示例：老 vs 新

下面两段代码做的是同一件事：从仓库拿一段文字，显示到屏幕。注意它们在"创建对象""更新 UI""异步处理"三件事上的差别。

### ❌ 老写法：Activity 里手动初始化 + 设置状态

```kotlin
class OldActivity : Activity() {
  private var textView: TextView? = null
  private var repo: MovieRepository? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    textView = findViewById(R.id.title)        // 容易 NPE：忘了判空就崩
    repo = MovieRepository()                   // 手动 new：测试时换不掉

    Thread {                                   // 容易泄漏：Activity 销毁线程还在跑
      val data = repo!!.load()
      runOnUiThread { textView?.text = data }  // 状态散乱：UI 状态写在 Runnable 里
    }.start()
  }
}
```

四个隐患一眼可见：`findViewById` 返回可空、`MovieRepository()` 写死无法替换、`Thread` 不绑生命周期、`runOnUiThread` 手动切回主线程。再加一个"旋转屏幕 Activity 重建 → `textView` 又是 null" 的隐藏坑。

### ✅ 新写法（本项目风格，简化版）

```kotlin
// 1. ViewModel：状态集中 + 用协程
@HiltViewModel                                  // ← Hilt 帮你 new（第 4 章）
class MovieViewModel @Inject constructor(
  private val repo: MovieRepository             // ← 接口注入，不写 new
) : ViewModel() {
  private val _state = MutableStateFlow(UiState())   // ← 状态流（第 5 章）
  val state: StateFlow<UiState> = _state.asStateFlow()

  fun load() {                                  // ← 协程，不写 Thread
    viewModelScope.launch {
      _state.update { it.copy(data = repo.load()) }  // ← 状态集中：一处更新
    }
  }
}

// 2. Screen：UI 是状态的函数
@Composable                                     // ← 第 9 章
fun MovieScreen(vm: MovieViewModel = hiltViewModel()) {
  val state by vm.state.collectAsStateWithLifecycle()
  Text(text = state.data)                       // ← 状态变 → UI 自动刷新
}
```

### 关键差异对比

| 维度 | 老写法 | 新写法 |
|------|--------|--------|
| 创建对象 | `repo = MovieRepository()` | `@Inject constructor(repo: MovieRepository)` |
| 异步 | `Thread { runOnUiThread { ... } }` | `viewModelScope.launch { ... }` |
| 状态管理 | 散在多个 `var` + setText | 一个 `StateFlow<UiState>` |
| UI 更新 | 手动调用 `setText` | 状态变自动重组（Recomposition） |
| 屏幕旋转 | 状态全丢，需手动 `onSaveInstanceState` | VM 不销毁，状态自动保留 |
| 测试 | 几乎测不了 | 替换 repo 即可单测 |

> 📁 项目对应位置：这种"`@HiltViewModel + StateFlow + @Composable`"组合遍及 `ui/movielist/`、`ui/forum/`、`ui/detail/` 等目录，是本项目所有功能屏的标配。

---

## 1.4 项目中怎么用：JBusDriver 是个什么 App

### App 做什么的

JBusDriver 是一个**面向某影视网站的第三方 Android 客户端**。核心功能包括：

- 影片列表浏览与分页加载
- 影片详情页（含图片预览、磁力链接解析）
- 按演员 / 类型 / 导演等维度浏览
- 论坛板块与帖子阅读
- 本地收藏与自定义分类
- 本地视频文件（SAF）与影片信息关联

技术形态上是一个典型的"列表 + 详情 + 收藏 + 设置"内容型 App。本章只看技术结构，不涉及具体业务内容。

### 包结构概览

整个 App 的源码集中在 `app/src/main/java/me/jbusdriver/modern/` 下，按职责清晰分成四层：

```
📁 项目对应位置：app/src/main/java/me/jbusdriver/modern/
me.jbusdriver.modern/
├── JBusApplication.kt    ← App 入口（第 3 章）
├── KLog.kt               ← 日志工具
├── core/                 ← 基础设施：网络、缓存、序列化、调度器
│   ├── http/             ← OkHttp 客户端、WebView 会话、HTML 抓取
│   ├── cache/            ← LRU + 磁盘缓存、SWR 数据流
│   ├── serialization/    ← Gson 多态适配器
│   └── site/             ← 站点 URL 配置（SiteConfig）
├── data/                 ← 数据层：仓库、数据库、解析器、DI 模块
│   ├── repository/       ← 仓库接口 + 实现（MovieRepository 等）
│   ├── db/               ← Room 数据库、DAO、实体
│   ├── parser/           ← HTML → 领域模型解析（Jsoup）
│   ├── settings/         ← DataStore 偏好、主题、UI 偏好
│   └── di/               ← Hilt 模块（DataModule、DatabaseModule）
├── domain/model/         ← 纯数据模型（不依赖 Android）
│   ├── Movie.kt          ← Movie / MovieDetail
│   ├── ILink.kt          ← 链接抽象、分页
│   └── ForumModels.kt    ← 论坛领域模型
└── ui/                   ← UI 层：Activity、各功能屏的 Screen + ViewModel
    ├── ModernMainActivity.kt   ← 唯一 Activity（@AndroidEntryPoint）
    ├── MainScreen.kt           ← 底部导航
    ├── Navigation.kt           ← Nav3 路由入口
    ├── components/             ← 通用 Composable（MovieList、SearchBar 等）
    ├── movielist/              ← 影片列表屏 + ViewModel
    ├── detail/                 ← 影片详情屏 + ViewModel
    ├── forum/                  ← 论坛屏 + ViewModel
    ├── search/                 ← 搜索屏 + ViewModel
    └── settings/               ← 设置屏 + ViewModel
```

每层各一句话职责说明：

- **`core/`**：与业务无关的工具集——HTTP 客户端、HTML 解析辅助、磁盘/内存缓存、Gson 配置、SiteConfig 站点 URL 管理。任何 App 都能用上类似的一层。
- **`data/`**：业务数据的搬运工——`repository/` 仓库、`db/` Room 数据库、`parser/` HTML→领域模型解析、`settings/` DataStore 偏好、`di/` Hilt 模块。
- **`domain/model/`**：纯 Kotlin 数据类（`Movie`、`MovieDetail`、`ForumPost` 等），不依赖 Android 框架，方便单测。
- **`ui/`**：所有屏幕的 Composable + ViewModel。`ModernMainActivity.kt` 是唯一 Activity，每个功能屏有独立子目录。

> **关键约束**：依赖方向严格"从外向内"——`ui/` 可以依赖 `data/` 和 `domain/model/`；`data/` 可以依赖 `core/` 和 `domain/model/`；但 `domain/model/` 不依赖任何外层（保持纯净）。这样模型可以单测、UI 可以替换。

### App 是怎么启动的

从用户点图标到看见首页，调用链是这样走的：

```mermaid
flowchart LR
  A[系统启动] --> B[JBusApplication.onCreate]
  B --> C[Hilt 注入依赖图]
  C --> D[ModernMainActivity 创建]
  D --> E["setContent { JBusTheme { JBusNavigation() } }"]
  E --> F[Compose 渲染 MainScreen]
```

每一步发生了什么：

1. **`JBusApplication.onCreate`** 是整个 App 的入口。`@HiltAndroidApp` 注解让 Hilt 在这一刻完成依赖图的构造——所有 `@Module`、`@Inject` 在此时被串联起来。Application 还初始化了 Coil 图片加载器。
2. **`ModernMainActivity.onCreate`** 调用 `setContent { JBusTheme { JBusNavigation() } }`，把 Compose 树挂到这个 Activity 上。从此以后 UI 的世界就是 Compose 的，Activity 退居幕后。
3. **`JBusNavigation()`** 是 Compose 入口，根据当前路由渲染对应的 Screen。每个 Screen 又各自取自己的 `hiltViewModel()`。

> 📁 项目对应位置：`JBusApplication.kt:24` 是 `@HiltAndroidApp` 的标注处；`ui/ModernMainActivity.kt:22` 是 `@AndroidEntryPoint` 与 `setContent` 的所在。详细解释见第 3 章。

### 依赖了哪些库

下表把 `app/build.gradle.kts:155-226` 的关键依赖按"做什么用 / 第几章细讲"两栏整理：

| 库 | 干什么 | 第几章细讲 |
|----|--------|----------|
| Jetpack Compose + Material3 | 声明式 UI 框架 + 设计系统 | 第 9 章 |
| Hilt | 依赖注入 | 第 4 章 |
| Coroutines + Flow | 异步与数据流 | 第 5 章 |
| Room | 数据库（收藏、历史） | 第 12 章 |
| DataStore Preferences | 偏好设置存储 | 第 12 章 |
| OkHttp + Jsoup | 网络 + HTML 解析 | 第 7 章 |
| Navigation 3 | 路由导航 | 第 11 章 |
| Coil + Telephoto | 图片加载与缩放 | （略，自行了解） |
| Gson | JSON 序列化与缓存 | 第 12 章 |
| kotlinx-serialization | Nav3 路由 key 序列化 | 第 11 章 |
| LeakCanary（仅 debug） | 内存泄漏检测 | 第 A1 章 |
| Lottie | 动画 | （略，自行了解） |

> 📁 项目对应位置：完整依赖清单见 `app/build.gradle.kts:155`（`dependencies { ... }` 块）。版本管理走 Gradle version catalog（`gradle/libs.versions.toml`），不在 `build.gradle.kts` 里写死版本号。

---

## 1.5 常见误区与调试技巧

新手第一次进项目最容易卡在这三个问题上：

### 误区 1：找不到 `findViewById`？

**症状**：想改 UI，习惯性去 `res/layout/` 找 XML 布局，结果什么都找不到。

**原因**：项目**全部用 Compose**，没有 XML 布局，`res/layout/` 下几乎没有业务布局文件。

**怎么找**：UI 写法在 `ui/<功能屏>/<XxxScreen>.kt` 里找 `@Composable fun`。例如：

- 想看影片列表的 UI → `ui/movielist/MovieListScreen.kt`
- 想看影片详情的 UI → `ui/detail/MovieDetailScreen.kt`
- 想看通用按钮、卡片 → `ui/components/` 下找

把"找 XML"的肌肉记忆切换成"找 Composable"，这是新人最大的认知切换。

### 误区 2：ViewModel 没有 `constructor()` 调用？

**症状**：在 Composable 里看到 `hiltViewModel()`，但搜不到哪里 `new` 了 ViewModel。

**原因**：ViewModel 由 **Hilt 创建**。你在 Composable 里写 `hiltViewModel()`，Hilt 在背后帮你调用 `@Inject constructor(...)` 并把依赖传进去。看不见 `new` 不代表没有创建过程——只是创建过程被框架接管了。

**怎么验证**：在 ViewModel 的 `init { }` 块里加一行日志，跑一下 App，Logcat 里就会看到它被构造了。详见第 4 章。

### 误区 3：想看 App 跑起来时调了哪些代码？

**症状**：代码读得云里雾里，想知道实际运行时到底走了哪条分支。

**调试手段（从轻到重）**：

1. **Logcat 过滤 `JBus` 标签**：项目自定义日志大多用这个 tag，能直接看到关键调用路径。
2. **断点 + Debug 模式**：在 ViewModel 方法或 Repository 方法下断点，看调用栈。
3. **Profiler → CPU**：抓方法调用序列，看哪个方法耗时多少。
4. **网络请求**：给 OkHttp 加 `HttpLoggingInterceptor`（项目里已配置），看请求 URL 和响应。

> 小技巧：第一次跑 App 时把 logcat level 调到 Debug，过滤 `JBus|Hilt|OkHttp` 三个 tag，基本能看清启动期发生了什么。

---

## 1.6 小结与下一站

本章建立了一张"现代 Android 心智地图"：

- **三大范式**：声明式 UI（Compose）+ 依赖注入（Hilt）+ 结构化并发（协程 + Flow）。三件套各自解决一个老 Android 的痛点。
- **项目四层结构**：`core/` 基础设施、`data/` 数据层、`domain/model/` 领域模型、`ui/` UI 层。依赖方向严格"从外向内"。
- **启动流程**：`JBusApplication.onCreate` → Hilt 注入 → `ModernMainActivity.onCreate` → `setContent { JBusNavigation() }` → Compose 渲染。
- **依赖库一览**：Compose / Hilt / Coroutines / Room / DataStore / OkHttp+Jsoup / Nav3 / Coil+Telephoto / Gson。

之后每章都会落在这个骨架上——读第 4 章时你会回来看 `data/di/`；读第 9 章时会回来看 `ui/`；读第 3 章时会把这里的启动流程再展开讲一遍。如果现在你还看不懂代码细节，没关系——这正是后面 12 章要解决的事。

```
下一站：第 2 章 现代 Kotlin 速览 —— 项目里高频出现的 Kotlin 语法一次过完。
```

---

🔍 深入阅读：
- 项目架构总览：见 `AGENTS.md` §Architecture
- 已知技术债与代码评审报告：见 `docs/CODE_REVIEW.md`
- 现代开发范式官方指南：https://developer.android.com/modern-android-development
