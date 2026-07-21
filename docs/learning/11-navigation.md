# 第 11 章：Navigation 3 与路由

> 📖 本章你将学到：Navigation 3 是什么、NavKey 路由怎么定义、怎么新增一个页面、转场动画怎么配、路由参数怎么传给 ViewModel。
> 🔗 前置章节：[第 3 章 单 Activity](03-single-activity.md)、[第 4 章 依赖注入](04-dependency-injection.md)（`@AssistedInject`）
> 📁 项目对应目录：`ui/Navigation.kt`、`ui/NavigationKeys.kt`

---

## 11.1 为什么需要导航库

第 3 章已经把"多 Activity"的架构拆掉了——全局只有一个 `ModernMainActivity`，所有"页面"都是 Compose 树上的 `@Composable` 函数。但留了一个尾巴：**这些 Composable 之间怎么切换？**

老写法里，"切换页面"就是 `startActivity(Intent(this, DetailActivity::class.java).putExtra("id", id))`。这一行代码看似简单，实际上背着四个雷：

### 痛点 1：参数传递弱

`putExtra("id", id)` 是**字符串键**——类型不安全，编译期查不出来：

- 把 `"id"` 拼错成 `"ld"`？编译不报，运行时 `getStringExtra("ld")` 返回 `null`，崩。
- 写 `putExtra("id", 42)` 结果另一边 `getLongExtra("id", 0L)`？类型不匹配，行为未定义。
- 想传一个复杂的对象？只能 `Serializable` / `Parcelable`，又要写一堆样板代码。

10 个页面、50 处 `putExtra` 之后，"哪个 key 是哪个类型"已经没人能背下来。

### 痛点 2：转场动画难统一

每个 Activity 各自 `overridePendingTransition(...)` 配自己的进出场动画。想做一套统一的"iOS 风格 slide + scale"风格？得在 10 个 Activity 里复制粘贴同一段代码。改一处风格改 10 处，新人很容易漏掉一两个页面，导致跳转动画忽左忽右，体验割裂。

### 痛点 3：返回栈混乱

`finish` vs `startActivity` 的 back stack 行为依赖于 `launchMode`、`FLAG_ACTIVITY_*`、`taskAffinity` 等十几个 flag 的组合。深链（deep link）回退行为尤其诡异——明明用户认为"从详情返回应该回到列表"，结果按 back 退出 App 了。

### 痛点 4：多 Activity 状态共享难

详情页要拿列表页的某个对象（比如当前选中的影片），老办法只有三条路：

1. 重新发请求拉一遍——浪费流量、UI 闪一下。
2. 存进 `object` 单例——又回到"单例难测试、生命周期失控"的老坑（第 4 章讲过）。
3. 用 `Intent` extras 拷贝一份——对象大就慢，且复杂对象要手写 `Parcelable`。

> Navigation 3 用**类型安全的路由对象（NavKey）+ 统一的 Compose 容器（NavDisplay）**解决这四个痛点。所有"页面"都是 Composable，跳转就是往一个 backStack 里 `add` 一个 NavKey——参数类型编译期检查、动画一处配齐、返回栈就是普通的 List、状态在同一个 Compose 树内自然共享。

第 3 章讲过"单 Activity 是什么"，本章就讲"单 Activity 里面的 UI 怎么互相切换"。

---

## 11.2 Navigation 3 是什么

Navigation 3（简称 Nav3，包名 `androidx.navigation3`）是 Jetpack 较新的导航库，专门为 Compose 单 Activity 架构设计。三个核心概念抓住就行：

### 1. NavKey —— 类型安全的路由标识

一个 `@Serializable` 的数据类或数据对象，代表"一个路由"。例如：

```kotlin
@Serializable
data class RouteDetail(val id: String) : NavKey       // 带参数：详情页
@Serializable
data object RouteHome : NavKey                        // 不带参数：首页（单例）
```

`NavKey` 是个空接口（marker interface），任何 `@Serializable` 类型都能当路由。**参数就是构造函数的字段**——`RouteDetail(id = "abc")` 一行就把参数传过去了，**编译期就知道类型对不对**，不会到运行时才崩。

### 2. NavDisplay + entry<RouteXxx> { } —— 路由容器

`NavDisplay` 是个 Compose 容器，根据当前 backStack 顶部是哪个 NavKey，渲染对应的 Composable。每个路由注册一个 `entry<RouteXxx> { ... }` 块：

```kotlin
NavDisplay(
  backStack = backStack,
  entryProvider = entryProvider {
    entry<RouteHome> { HomeScreen(...) }
    entry<RouteDetail> { entry ->                       // ← entry.key 自动是 RouteDetail 类型
      DetailScreen(id = entry.key.id)
    }
  }
)
```

`entry<RouteDetail>` 的泛型参数就是 NavKey 的具体子类型——编译期就知道这个块拿到的是 `RouteDetail`，`entry.key.id` 直接可访问，不需要强转、不需要 `when` 分支判断。

### 3. rememberNavBackStack(RouteMain) —— 返回栈状态

```kotlin
val backStack = rememberNavBackStack(RouteMain)
```

这就是返回栈——本质是一个 `MutableList<NavKey>`，初始含一个元素（首页）。**push 一个 NavKey → 进栈；按 back → `removeLastOrNull()` 出栈。** 状态用 `rememberSaveable` 保存，**旋转屏幕不丢、进程被杀恢复后也不丢**（前提是 NavKey 都 `@Serializable`）。

### Intent vs NavKey 对比

| 维度 | Intent（老写法） | NavKey（Nav3 写法） |
|------|------------------|---------------------|
| 参数传递 | `putExtra("id", id)` 字符串键 | `RouteDetail(id)` 构造函数字段，类型安全 |
| 跳转方式 | `startActivity(intent)` | `backStack.add(RouteDetail(id))` |
| 容器 | 多个 Activity | 一个 Activity + `NavDisplay` |
| 转场动画 | 每个 Activity 各自 `overridePendingTransition` | `NavDisplay` 统一配 `transitionSpec` |
| 类型检查 | 运行时崩（key 拼错、类型不匹配） | 编译期检查 |
| 返回栈 | `launchMode` + flag 组合，行为难预测 | 一个 `List<NavKey>`，push/pop 顺序可预测 |
| Manifest | 每页都要 `<activity>` 声明 | 全程一个 `<activity>`，路由不进 Manifest |

> 一句话对比：Intent 是"运行时拼字符串跳转"，NavKey 是"编译期类型检查跳转"。前者写错 key 编译通过运行时崩，后者写错类型根本编译不过。

---

## 11.3 最小示例：两个页面 + 跳转

下面是一个脱离项目的最小例子——列表页点条目跳详情页，再按 back 返回。两段代码加起来不到 25 行：

```kotlin
// 1️⃣ 定义路由（统一放一个文件）
@Serializable
data object RouteHome : NavKey

@Serializable
data class RouteDetail(val id: String) : NavKey

// 2️⃣ 在主 Activity 里挂 NavDisplay
@Composable
fun AppNavigation() {
  val backStack = rememberNavBackStack(RouteHome)         // ← 返回栈，初始含 RouteHome
  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },            // ← 返回键出栈
    entryProvider = entryProvider {
      entry<RouteHome> {
        HomeScreen(onItemClick = { id ->
          backStack.add(RouteDetail(id))                  // ← push 一个路由，类型安全
        })
      }
      entry<RouteDetail> { entry ->
        val route = entry.key                              // ← route 自动是 RouteDetail 类型
        DetailScreen(id = route.id)                       // ← 直接 .id，强类型
      }
    }
  )
}
```

几个关键观察：

- **跳转 = `backStack.add(RouteDetail(id))`**。参数 `id` 就是构造函数字段，编译期检查类型。没有字符串 key、没有 `putExtra`。
- **路由分发 = `entry<RouteXxx> { entry -> ... }`**。`entry.key` 的类型就是 `RouteXxx`，可以直接访问字段。
- **返回 = `backStack.removeLastOrNull()`**。就是一个 List 操作，行为可预测。
- **没有 Manifest 改动**。新增一个页面不需要碰 `AndroidManifest.xml`。

这个例子就是项目里 `ui/Navigation.kt` 的极简骨架。下一节看项目里真实长了多少。

---

## 11.4 项目中怎么用：8 个路由 + iOS 风格转场 + @AssistedInject

本节是全章重点。项目把所有路由集中在 `NavigationKeys.kt` 一个文件，路由入口写在 `Navigation.kt`，路由参数通过 `@AssistedInject`（第 4 章 §4.4.4）传给 ViewModel。三个小节分别讲这三件事。

### 11.4.1 项目里的 8 个路由

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt:6`

```kotlin
@Serializable data object RouteMain : NavKey                              // 首页（底部导航 4 个 tab）
@Serializable data class RouteSearch(defaultSearchType: String = "") : NavKey    // 搜索页
@Serializable data class RouteMovieDetail(movieUrl: String, censorType: String? = null) : NavKey  // 影片详情
@Serializable data class RouteImageViewer(images: List<String>, startIndex: Int = 0) : NavKey    // 全屏看图
@Serializable data class RouteLinkMovies(                                 // 演员/类型/导演关联的影片列表
    linkUrl: String, title: String = "", type: String = "",
    avatar: String = "", censorType: String? = null
) : NavKey
@Serializable data class RouteForumThreadList(fid: Int, title: String = "", typeId: Int? = null) : NavKey  // 论坛板块
@Serializable data class RouteForumThreadDetail(tid: Int) : NavKey       // 论坛帖子
@Serializable data object RouteSettings : NavKey                         // 设置页
```

关键点：

1. **所有路由都 `@Serializable`**（kotlinx-serialization）。Nav3 用它做保存/恢复——旋转屏幕或进程被杀恢复时，整个 backStack 会被序列化存到 `rememberSaveable`，所有 NavKey 都必须可序列化才能恢复回来。这也是 `app/build.gradle.kts` 里 `kotlinx-serialization` 被"钉到 1.8.1"的原因（详见 AGENTS.md 的 Key Libraries）。
2. **没参数的用 `data object`**（单例，全局只有一个）：`RouteMain`、`RouteSettings`。**有参数的用 `data class`**：其余 6 个。
3. **路由参数就是构造函数字段**，类型安全。`RouteMovieDetail` 的 `movieUrl: String`、`RouteForumThreadDetail` 的 `tid: Int`——传错类型编译直接红。
4. **8 个路由全在同一个文件**。新增路由只改这一处。

> 📁 项目对应位置：`ui/NavigationKeys.kt` 是项目全部路由的总账。新人问"项目有几个页面、能不能跳到 X 页"——先看这个文件，所有可达路由都在这。

### 11.4.2 NavDisplay 的入口

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt:41`

```kotlin
@Composable
fun JBusNavigation(
    deepLinkFlow: StateFlow<NavKey?>? = null,
    deepLinkKey: NavKey? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(RouteMain)         // ← 返回栈，初始是首页

    // 处理外部 deep link（URL 打开 App）：把目标路由 push 进栈
    LaunchedEffect(deepLinkValue) { /* ... 见原文件 ... */ }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {                                  // ← 统一配置 iOS 风格转场
            slideInHorizontally(...) togetherWith (scaleOut(...) + fadeOut(...))
        },
        popTransitionSpec = {                               // ← 返回时的反向动画
            (scaleIn(...) + fadeIn(...)) togetherWith slideOutHorizontally(...)
        },
        entryDecorators = listOf(                           // ← 跨页面状态保持
            rememberSaveableStateHolderNavEntryDecorator(),   // 每个 entry 独立保存 Compose 状态
            rememberViewModelStoreNavEntryDecorator()         // 每个 entry 独立保存 ViewModel
        ),
        entryProvider = entryProvider {
            entry<RouteMain> { MainScreen(...) }            // ← 8 个 entry 块对应 8 个路由
            entry<RouteSearch>(metadata = metadata { /* 搜索页用上滑动画 */ }) { key ->
                SearchScreen(defaultSearchType = key.defaultSearchType, ...)
            }
            entry<RouteMovieDetail> { key ->
                MovieDetailScreen(movieUrl = key.movieUrl, censorType = key.censorType, ...)
            }
            entry<RouteImageViewer> { key -> ImageViewScreen(images = key.images, ...) }
            entry<RouteLinkMovies> { key -> LinkMovieListScreen(linkUrl = key.linkUrl, ...) }
            entry<RouteForumThreadList> { key -> ForumThreadListScreen(fid = key.fid, ...) }
            entry<RouteForumThreadDetail> { key -> ForumThreadDetailScreen(tid = key.tid, ...) }
            entry<RouteSettings> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
        }
    )
}
```

几个关键观察：

1. **`entry<RouteXxx> { key -> ... }` 里 `key` 自动是强类型**。比如在 `entry<RouteMovieDetail>` 块里，`key` 就是 `RouteMovieDetail` 类型，可以直接 `key.movieUrl` / `key.censorType`——不需要强转，不需要 `when (key) { is RouteMovieDetail -> ... }` 分支判断。这是 Nav3 相对老式 Compose Navigation 最大的改进。
2. **`transitionSpec` / `popTransitionSpec` 是统一的 iOS 风格转场**：进入时新页面从右侧滑入 + 老页面 scale 缩小 + fade；返回时反向。一处定义、所有路由生效。
3. **`metadata = metadata { ... }` 给特定路由覆盖动画**。`RouteSearch` 单独配了"从底部上滑"动画（搜索页是个全屏 sheet 风格），其它路由走默认。这就是 §11.5 会讲的"转场动画 metadata"机制。
4. **`entryDecorators` 让每个页面的 ViewModel 和 SaveableState 独立保存**——离开页面再回来，列表的滚动位置、VM 里的状态都还在。这两个 decorator 是 Nav3 推荐的标配，项目两个都加上了。
5. **跳转通过回调向上传递**：每个 Screen 接 `onMovieClick`、`onBack` 这种 lambda 参数，由 `JBusNavigation` 在 entry 块里实现——实现里就是 `backStack.add(RouteMovieDetail(movie.link, censorType))`。这种"事件向上提、路由在容器层做"的写法保持 Screen 自身不依赖 backStack，方便复用与测试。

> 📁 项目对应位置：`ui/Navigation.kt:111`（`entryProvider { entry<RouteXxx> { ... } }` 全部注册处）；`ui/Navigation.kt:82`（`NavDisplay` 容器本体，含转场配置）。

### 11.4.3 路由参数怎么传给 ViewModel：@AssistedInject

`entry<RouteMovieDetail>` 里拿到的 `key.movieUrl` 是一个字符串，到了 `MovieDetailScreen` 之后怎么交给 `MovieDetailViewModel`？直接 `hiltViewModel()` 是拿不到这个参数的——因为 Hilt 的 `@Inject constructor` 只能注入编译期就知道的依赖（Repository、DataStore 等），**运行时才知道的路由参数怎么办**？

这就是第 4 章 §4.4.4 埋的伏笔——`@AssistedInject` + `@AssistedFactory`。回扣过来：

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt:106`

```kotlin
@HiltViewModel(assistedFactory = LinkMovieListViewModel.Factory::class)
class LinkMovieListViewModel @AssistedInject constructor(
    private val repository: MovieRepository,                // ← 框架提供（编译期就知道）
    private val collectRepository: CollectRepository,
    private val localVideoRepository: LocalVideoRepository,
    @Assisted private val navKey: RouteLinkMovies          // ← 路由参数，运行时才知道
) : ViewModel() {

    @AssistedFactory
    interface Factory {                                     // ← Hilt 帮你生成这个 Factory 的实现
        fun create(navKey: RouteLinkMovies): LinkMovieListViewModel
    }
}
```

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt:524`（`@AssistedFactory` 定义处）

然后在对应的 `entry<RouteLinkMovies> { }` 块（或者 Screen 里）调用时，通过 `creationCallback` 把路由参数传进 Factory：

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListScreen.kt:75`

```kotlin
@Composable
fun LinkMovieListScreen(
    linkUrl: String, title: String = "", type: String = "", avatarUrl: String = "",
    /* ... 其它参数 ... */
    viewModel: LinkMovieListViewModel = hiltViewModel<LinkMovieListViewModel, LinkMovieListViewModel.Factory>(
        creationCallback = { factory ->                     // ← Hilt 给你 Factory 实例
            factory.create(
                RouteLinkMovies(                            // ← 把路由参数塞进 NavKey 传进去
                    linkUrl, title, type, avatarUrl
                )
            )
        }
    )
) {
    /* collect uiState, 渲染列表 */
}
```

文字解释清楚链路：

1. **VM 构造函数声明 `@Assisted navKey: RouteLinkMovies`**——告诉 Hilt "这个参数我运行时才知道，请把它留给 Factory"。
2. **`@AssistedFactory interface Factory { fun create(navKey): VM }`**——Hilt 在编译期为这个 Factory 生成实现，自动处理其它"编译期就知道"的依赖（Repository 等）。
3. **调用方（Screen/entry 块）用 `hiltViewModel(..., creationCallback = { factory -> factory.create(...) })`**——把运行时参数传进 Factory，Factory 再调用 VM 的 `@AssistedInject constructor`。

**这就是项目所有带参数 VM 的统一写法**——`LinkMovieListViewModel`、`MovieDetailViewModel`、`ForumThreadListViewModel`、`ForumThreadDetailViewModel` 都是这个套路。参数少的（比如 `RouteForumThreadDetail(tid)`）就一个字段，参数多的（比如 `RouteLinkMovies` 有 5 个字段）就一个 NavKey 包起来传。

| 维度 | 普通 `@HiltViewModel` | `@AssistedInject` ViewModel |
|------|----------------------|----------------------------|
| 适用 | 无运行时参数（`MovieListViewModel`、`SearchViewModel` 等） | 带路由参数（`MovieDetailViewModel`、`LinkMovieListViewModel` 等） |
| 构造函数 | `@Inject constructor(repo: ...)` | `@AssistedInject constructor(repo: ..., @Assisted navKey: RouteXxx)` |
| 额外代码 | 无 | 需定义 `@AssistedFactory interface Factory` |
| 调用方 | `hiltViewModel()` | `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(navKey) })` |

---

## 11.5 常见误区与调试技巧

新人第一次在项目里新增页面，最容易卡在这三个坑上。

### 误区 1：新增页面要改三处

**症状**：业务要求新增一个"播放历史"页面。新人写好了 `HistoryScreen.kt` 和 `HistoryViewModel.kt`，但跳过去的时候什么都不显示，或者直接编译失败。

**原因**：项目里新增一个可达页面需要同步改**三处**，少一处都跳不过去——

| 第几处 | 改什么 | 文件 |
|--------|--------|------|
| 1 | 定义新 NavKey 类（`@Serializable data class RouteHistory(...) : NavKey`） | `ui/NavigationKeys.kt` |
| 2 | 注册新 `entry<RouteHistory> { key -> HistoryScreen(...) }` 块 | `ui/Navigation.kt` |
| 3 | 写对应的 `HistoryScreen` Composable + `HistoryViewModel` | `ui/history/HistoryScreen.kt` 等 |

第 1 处漏了——`backStack.add(RouteHistory(...))` 编译就过不了（找不到这个类）。第 2 处漏了——路由 push 进栈了但 `NavDisplay` 没有 entry 块匹配，屏幕空白。第 3 处漏了——entry 块里调的 `HistoryScreen` 不存在，编译过不了。

**怎么修**：把这三步当成一个清单（checklist）。新人新增页面时按这三步走，缺一不可。改完之后 `./gradlew assembleDebug` 跑一遍——编译过了基本就稳了。

📁 项目对应位置：`ui/NavigationKeys.kt`（路由定义）+ `ui/Navigation.kt:111`（entry 注册块）+ `ui/<功能屏>/`（Composable + ViewModel）

### 误区 2：路由参数忘了 `@Serializable`

**症状**：新增了一个路由 `data class RouteHistory(val items: List<Item>) : NavKey`（其中 `Item` 是个普通 data class），运行时进页面就崩，logcat 报 `Serializer has not been found for type Item`。

**原因**：Nav3 用 kotlinx-serialization 把整个 backStack 序列化到 `rememberSaveable` 里。**所有 NavKey 类、它们的所有字段、字段的字段……都必须可序列化。** 基本类型（`String`、`Int`、`List<String>` 等）自动支持；自定义类型要标 `@Serializable`。

**怎么修**：

1. NavKey 类本身要 `@Serializable`（这步一般不会漏）。
2. NavKey 的字段如果是自定义类型，那个类型也要 `@Serializable`。
3. 不确定时，参考 `NavigationKeys.kt` 里的 8 个路由——它们全是基本类型字段（`String` / `Int` / `List<String>` / `String?`），所以只标 NavKey 类自己就够。

> 实战建议：**路由参数尽量只放基本类型**——URL、ID、标题这种字符串/数字。要传的复杂对象（比如整个 Movie 模型）不要塞路由里，应该让目标页 VM 按路由里的 ID 自己去 Repository 拉。这也是为什么 `RouteMovieDetail` 只放 `movieUrl: String`，而不是放整个 `Movie` 对象。

### 误区 3：转场动画的 `metadata` 别忘了传

**症状**：想给某个路由配独立的转场动画（比如搜索页要从底部上滑），写了 `metadata = metadata { put(NavDisplay.TransitionKey) { ... } }`，但跳转时还是用默认动画。

**原因**：`NavDisplay` 的 `transitionSpec` 是**默认**配置，所有 entry 共用。要给某个 entry 单独配，需要用 `entry<RouteXxx>(metadata = ...) { ... }` 的具名参数形式把动画塞进 metadata——`NavDisplay` 会从这个 entry 的 metadata 里取出 `TransitionKey`、`PopTransitionKey`、`PredictivePopTransitionKey` 三个 key，覆盖默认。

**怎么修**：照抄 `ui/Navigation.kt:150-182`（`RouteSearch` 的 metadata 块）的模板——三个 TransitionKey 都配上，分别对应"进入""返回""手势预测返回"三种场景。

📁 项目对应位置：`ui/Navigation.kt:150`（`RouteSearch` 用 metadata 配独立动画的范例）

> 小技巧：默认的 `transitionSpec` 已经是 iOS 风格的 slide + scale + fade，足够大多数页面用。**只有动画风格明显不同**（比如搜索页是全屏 sheet、图片查看器是 fade）的页面才需要单独配 metadata，不要滥用。

---

## 11.6 小结与下一站

本章从第 3 章（单 Activity）的尾巴出发，把"单 Activity 里面的 Composable 怎么互相切换"讲清楚：

- **Nav3 三件套**：`NavKey`（类型安全的路由对象，`@Serializable`）+ `NavDisplay`（Compose 容器，按 backStack 顶渲染 Composable）+ `rememberNavBackStack`（一个 List，push/pop）。
- **项目 8 个路由全在 `ui/NavigationKeys.kt`**：`RouteMain` / `RouteSearch` / `RouteMovieDetail` / `RouteImageViewer` / `RouteLinkMovies` / `RouteForumThreadList` / `RouteForumThreadDetail` / `RouteSettings`。新增路由就改这一个文件。
- **路由入口 `ui/Navigation.kt:41`**：`JBusNavigation` 用 `NavDisplay` + 8 个 `entry<RouteXxx> { ... }` 块把路由和 Composable 串起来。`entry<RouteXxx> { key -> ... }` 里 `key` 是强类型，直接访问字段。
- **路由参数传给 VM**：用第 4 章的 `@AssistedInject` + `@AssistedFactory`，调用方通过 `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(navKey) })` 把运行时参数传进去。项目所有带参数的 VM（`MovieDetailViewModel`、`LinkMovieListViewModel` 等）都是这个套路。
- **新增一个页面的三步清单**：① `NavigationKeys.kt` 加 `@Serializable NavKey`；② `Navigation.kt` 加 `entry<RouteXxx> { ... }`；③ `ui/<功能屏>/` 加 `XxxScreen` + `XxxViewModel`。漏一处都跳不过去。
- **转场动画统一配**：`NavDisplay` 的 `transitionSpec` 是默认 iOS 风格；个别路由要单独配动画用 `entry<RouteXxx>(metadata = metadata { ... })`。

读完本章，你应该能独立在项目里新增一个页面：定义路由、写 Composable、注册 entry、配 VM（必要时上 `@AssistedInject`），并把跳转按钮接到 `backStack.add(RouteXxx(...))` 上。

```
下一站：第 12 章 Room、DataStore、Gson 序列化 —— 数据怎么持久化到磁盘？设置项怎么存？为什么缓存需要自定义 Gson Adapter？
```

---

🔍 深入阅读：
- Navigation 3 文档：https://developer.android.com/guide/navigation
- 类型安全路由最佳实践：https://developer.android.com/guide/navigation/type-safety
- 项目全部路由定义：`ui/NavigationKeys.kt`
- 项目路由入口：`ui/Navigation.kt`（`JBusNavigation` + 8 个 `entry<RouteXxx>` 块）
- AssistedInject 文档：https://dagger.dev/dev-guide/assisted-injection.html
