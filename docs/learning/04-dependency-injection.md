# 第 4 章：依赖注入与 Hilt

> 📖 本章你将学到：什么是依赖注入（DI）、为什么 Android 项目需要 DI、Hilt 的核心注解怎么用，以及本项目怎么把 ~20 个仓库/数据库串起来。
> 🔗 前置章节：[第 1 章 项目总览](01-project-overview.md)（了解包结构即可）
> 📁 项目对应目录：`data/di/`、`JBusApplication.kt`、所有 `*ViewModel.kt`

---

## 4.1 为什么需要依赖注入

第 1 章已经埋过一个雷："`val repo = MovieRepository()` 这种手动 `new` 写法测试难、单例满天飞"。本章就把这个雷拆掉。

想象你在写 `MovieListViewModel`，它需要 3 个依赖：网络客户端、缓存、数据库 DAO。最直觉的写法是在 VM 里自己造：

```kotlin
class MovieListViewModel : ViewModel() {
  private val net = NetClient()                    // 自己 new
  private val cache = CacheStore()                 // 自己 new
  private val dao = JBusDatabase.getInstance().historyDao()
  // ...
}
```

看起来没问题，但业务一演进，三个坑立刻冒出来：

### 痛点 1：测试时换不掉实现

想给这个 VM 写单测，发现它内部 `new` 了真的 `NetClient`——单测必须真发网络请求才能跑。**假的（Fake / Mock）实现塞不进去**，因为 `new` 写死在 VM 里。结果就是：测试又慢又脆，CI 时不时挂红。

### 痛点 2：10 个 VM 各 `new` 一份，单例又难测

App 里不止一个 VM。`MovieListViewModel`、`MovieDetailViewModel`、`SearchViewModel` 都要网络客户端。每个都 `new` 一份吗？复制粘贴 10 次以后，构造逻辑改一处就要改 10 处。

那抽个 `object NetClient` 单例？又回到痛点 1——单例全局共享，单测里换不掉。

### 痛点 3：需要 Context 的依赖怎么办

数据库 DAO 构造需要 `Context`。但 ViewModel **不应该持有 Activity 引用**（VM 的生命周期比 Activity 长，持有就内存泄漏）。结果就是：构造 DAO 需要 Context，VM 又不能拿 Context，手动 `new` 进了死胡同。

### DI 的核心思想

三个痛点的共同根因是：**"创建对象"和"使用对象"被混在了同一段代码里**。依赖注入（Dependency Injection, DI）就是把这两件事拆开：

> 类的构造函数声明"我需要什么"（参数），**不自己 `new`**；由一个叫"容器（Container）"的东西统一负责构造并传进来。

下面三节分别讲：DI 长什么样（§4.2）、Hilt 怎么自动化这件事（§4.3）、项目里具体怎么用（§4.4）。

---

## 4.2 依赖注入是什么

### 4.2.1 核心思想：参数声明依赖，不自己 new

把开头的例子改成 DI 风格，只是改一行：

```kotlin
class MovieListViewModel(
  private val net: NetClient,           // ← 不再自己 new，由外部传入
  private val cache: CacheStore,
  private val dao: HistoryDao
) : ViewModel()
```

VM 不关心 `net` 是真的 `NetClient` 还是测试用的 `FakeNetClient`，反正谁调它谁负责造好传进来。**测试时塞假的、生产时塞真的，VM 一行都不用改。**

但问题来了——"谁负责造"？总不能让 Composable 写 `MovieListViewModel(NetClient(), CacheStore(), dao)`，那只是把混乱从 VM 挪到 UI。所以还需要一个"统一负责构造"的角色：**容器（Container）**。

### 4.2.2 手写 DI 长什么样

不用任何框架，手写一个最简容器大概是这样：

```kotlin
// 手写容器：把所有依赖集中在一个 object 里构造
object AppContainer {
  val netClient = NetClient()                              // 单例
  val cacheStore = CacheStore()
  val jbusDb = JBusDatabase.getInstance()
  val movieRepo = MovieRepository(netClient, cacheStore)   // 注意构造顺序

  fun movieListViewModel() = MovieListViewModel(movieRepo, cacheStore, jbusDb.historyDao())
}

class MovieListActivity : Activity() {
  val vm = AppContainer.movieListViewModel()               // 取
}
```

能用，但很烦：

- **每加一个类都要在容器里写一行**——10 个 VM、20 个 Repository，容器会越长越可怕。
- **构造顺序要自己排**——`movieRepo` 依赖 `netClient`，必须先有 `netClient`，手工维护依赖图。
- **生命周期要自己管**——Activity 销毁后 VM 该不该留？单例和短生命周期对象怎么区分？全靠手写。

### 4.2.3 框架的作用：让 Hilt/Dagger 替你写容器

Hilt（基于 Dagger）就是替你**在编译期自动生成 `AppContainer` 那样的代码**。你只要：

1. 在类上标几个注解，告诉框架"谁能被构造"、"谁需要被注入"；
2. 在 Module 里告诉框架"接口对应哪个实现"、"第三方对象怎么造"。

剩下的——依赖图、构造顺序、作用域、生命周期——全部由 Hilt 在编译期分析后生成 Java/Kotlin 代码完成。**编译期就检查依赖是否齐全**，缺了绑定直接编译失败，而不是运行时崩。

> 一句话对比：手写容器是"运行时拼装"，Hilt 是"编译期生成拼装代码"。后者更快、更安全、写起来也更短。

---

## 4.3 最小示例：Hilt 四件套

Hilt 的核心就四个注解。下面用一个脱离项目的最小示例把它们走一遍，建立直觉；下一节再回到真实项目代码。

```kotlin
// 1️⃣ App 类标记 @HiltAndroidApp —— 触发 Hilt 在编译期生成整个 App 的依赖图
@HiltAndroidApp
class MyApp : Application()

// 2️⃣ 需要被注入的类，构造函数标 @Inject —— 告诉 Hilt "造我只要调这个构造函数"
class UserRepository @Inject constructor(
  private val api: UserApi        // 这个 api 哪来的？看下面 Module
)

// 3️⃣ Module 告诉 Hilt 怎么造那些"不能直接 @Inject constructor"的东西（接口、第三方对象）
@Module
@InstallIn(SingletonComponent::class)   // 这个 Module 装在 App 单例作用域里
object AppModule {
  @Provides                              // "我提供 UserApi，造法如下"
  fun provideUserApi(): UserApi = UserApi("https://example.com")
}

// 4️⃣ 接收注入：ViewModel 标 @HiltViewModel，构造函数标 @Inject
@HiltViewModel
class UserViewModel @Inject constructor(
  private val repo: UserRepository       // Hilt 自动构造 UserRepository（连带 UserApi）并传入
) : ViewModel()
```

四个注解一句话总结：

| 注解 | 作用 | 标在哪 |
|------|------|--------|
| `@HiltAndroidApp` | 触发整个 App 依赖图生成 | `Application` 子类 |
| `@Inject constructor(...)` | 告诉 Hilt "用这个构造函数造我" | 普通类、ViewModel 的构造函数 |
| `@Module + @InstallIn + @Provides/@Binds` | 告诉 Hilt "接口/第三方对象怎么造" | 一个 `Module` 类 |
| `@HiltViewModel` / `@AndroidEntryPoint` | 标记"我要接收注入"的入口 | ViewModel / Activity / Service |

记住这四个注解的分工，下面看项目里真实长什么样。

---

## 4.4 项目中怎么用：三个真实文件 + 两种 ViewModel

本节是全章重点。项目把 DI 拆成三个真实文件管理：`JBusApplication.kt`（入口）、`DataModule.kt`（接口→实现的绑定）、`DatabaseModule.kt`（Room 等带构造逻辑的对象）。再加上 ViewModel 的两种写法，全部依赖注入的场景就齐了。

### 4.4.1 App 入口：触发依赖图生成

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/JBusApplication.kt:24`

```kotlin
@HiltAndroidApp                  // ← 这一行就让 Hilt 在编译期为整个 App 生成依赖图
class JBusApplication : Application(), ImageLoaderFactory {

  @Inject lateinit var htmlClient: HtmlClient    // ← 字段注入；不用自己 new
  @Inject lateinit var siteConfig: SiteConfig

  override fun onCreate() {
    super.onCreate()
    // 此时依赖图已经构造完成，htmlClient / siteConfig 已可直接用
  }
}
```

`@HiltAndroidApp` 是整个 App 使用 Hilt 的总开关：标了它，Hilt 才会在编译期扫描所有 `@Module`、所有 `@Inject constructor`，把它们组装成一张完整的依赖图。Application 自己也能用 `@Inject lateinit var` 字段注入拿依赖（如这里的 `htmlClient`），但通常 VM 才是注入的主要场景。

### 4.4.2 Module：接口绑到实现（`@Binds`）

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt:68`

```kotlin
@Module                                     // ← 告诉 Hilt 这是一个装"绑定"的类
@InstallIn(SingletonComponent::class)       // ← 装在"App 全局单例"作用域里
abstract class DataModule {

  @Binds @Singleton                          // ← "把 MovieRepository 这个接口绑到 DefaultMovieRepository"
  abstract fun bindMovieRepository(
    impl: DefaultMovieRepository
  ): MovieRepository

  // 项目里有约 20 个这样的 @Binds 方法：SearchRepository、MagnetRepository、
  // ForumRepository、CollectRepository、ThemeRepository ……
}
```

**什么时候用 `@Binds`？** 当你有一个"接口 + 实现"对，且实现类的构造函数已经标了 `@Inject constructor` 时。`@Binds` 就是在告诉 Hilt："如果有人要 `MovieRepository` 这个接口类型，请给他 `DefaultMovieRepository` 的实例"。

本项目所有 Repository（共 ~20 个）都是"接口 + 实现"成对设计，所以全部用 `@Binds`。这是一种很值得借鉴的工程习惯——接口和实现分开，方便单测替换、方便日后换实现。

### 4.4.3 Module：构造带逻辑的对象（`@Provides`）

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/di/DatabaseModule.kt:24`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {                      // ← object（不是 abstract class）：@Provides 方法有函数体

  @Provides @Singleton
  fun provideJBusDatabase(@ApplicationContext context: Context): JBusDatabase =
    Room.databaseBuilder(context, JBusDatabase::class.java, "jbus.db").build()

  @Provides fun provideHistoryDao(db: JBusDatabase): HistoryDao = db.historyDao()
}
```

**为什么这里用 `@Provides` 而不是 `@Binds`？** Room 数据库不能改构造函数——它是抽象类，真正的实现由 Room 编译器在运行时生成。所以必须用 `@Provides` 在方法体里写出"造它的方式是 `Room.databaseBuilder(...).build()`"。

记忆口诀：

- **`@Binds`**：接口 → 实现，**无构造逻辑**，实现类自己有 `@Inject constructor`。方法是 `abstract`，类是 `abstract class`。
- **`@Provides`**：**有构造逻辑**（如 `Room.databaseBuilder`、`OkHttpClient.Builder`），或第三方类不能改构造函数。方法有函数体，类是 `object`。

另外注意 `@ApplicationContext context: Context`——这是 Hilt 内置的限定符（Qualifier），告诉 Hilt "给我 Application 的 Context"。VM 不该拿 Activity Context（会泄漏），需要 Context 时一律用 `@ApplicationContext`。

### 4.4.4 接收端：ViewModel 的两种写法

ViewModel 是项目里注入的主要场景。根据"有没有运行时才知道的参数"，分两种写法。

**写法 1：普通 `@HiltViewModel`（无运行时参数）**

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt:160`

```kotlin
@HiltViewModel                                  // ← 普通写法：没有路由参数
class MovieListViewModel @Inject constructor(
  private val repository: MovieRepository,       // ← 接口，Hilt 通过 DataModule 找到实现
  private val localVideoRepository: LocalVideoRepository
) : ViewModel()
```

Hilt 看到 `@HiltViewModel + @Inject constructor`，就自动构造两个 Repository（连带它们的所有依赖：`NetClient`、`CacheStore`、`JBusDatabase`……）传进这个 VM。**整个过程编译期完成，运行时零反射。**

**写法 2：`@AssistedInject`（带运行时参数）**

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt:106`

```kotlin
@HiltViewModel(assistedFactory = LinkMovieListViewModel.Factory::class)
class LinkMovieListViewModel @AssistedInject constructor(
  private val repository: MovieRepository,        // ← 框架提供（编译期就知道）
  @Assisted private val navKey: RouteLinkMovies   // ← 路由参数，运行时才知道
) : ViewModel() {

  @AssistedFactory
  interface Factory {                             // ← Hilt 帮你生成这个 Factory 的实现
    fun create(navKey: RouteLinkMovies): LinkMovieListViewModel
  }
}
```

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/movielist/LinkMovieListViewModel.kt:524`（`@AssistedFactory` 定义处）

什么时候用 `@AssistedInject`？当 VM 需要**运行时才知道的参数**时——比如这里的 `navKey: RouteLinkMovies`，它包含从哪个演员/分类跳过来的链接 URL。这种参数 Hilt 没法在编译期决定，所以需要你提供一个 `@AssistedFactory`，由调用方（导航代码）在跳转时把 `navKey` 传进去。第 11 章讲导航时会再过一遍这个机制。

记忆对比：

| 维度 | `@HiltViewModel + @Inject` | `@HiltViewModel + @AssistedInject` |
|------|---------------------------|-----------------------------------|
| 适用 | 无运行时参数的 VM | 带路由参数 / 调用方传入参数的 VM |
| 构造函数 | `@Inject constructor(repo: ...)` | `@AssistedInject constructor(repo: ..., @Assisted key: ...)` |
| 额外代码 | 无 | 需定义 `@AssistedFactory interface Factory` |
| 项目例子 | `MovieListViewModel` | `LinkMovieListViewModel`、`MovieDetailViewModel` 等 |

---

## 4.5 常见误区与调试技巧

新手用 Hilt 最容易栽在这四个坑上。前两个是概念性错误，后两个是 Android 特有的生命周期陷阱。

### 误区 1：标了 `@Inject` 但 Hilt 报错"找不到绑定"

**症状**：编译报错 `[Dagger/MissingBinding]` 之类，告诉你某个类型找不到 Provider。

**原因**：你要注入的是**接口类型**（比如 `MovieRepository`），但只在实现类 `DefaultMovieRepository` 上标了 `@Inject constructor`。Hilt 不会自动把实现和接口关联起来。

**怎么修**：在 `DataModule` 里加一个 `@Binds` 方法把接口绑到实现。或者，如果你注入的是具体类（不是接口），就在它的构造函数上加 `@Inject constructor`。

### 误区 2：`@Binds` 和 `@Provides` 用反

**症状**：编译报错 `@Binds method must be abstract` 或者 `@Provides method can not be abstract`。

**原因**：把两个注解的语义搞混了。记住口诀：

- **`@Binds`** 用于"接口 → 实现"且无构造逻辑——方法体没有，方法必须是 `abstract`，所在类是 `abstract class`。
- **`@Provides`** 用于"有构造逻辑"（如 `Room.databaseBuilder(...)`、`OkHttpClient.Builder()`）——方法体必须写出怎么造，所在类是 `object`。

如果 `@Binds` 方法里写了 `= impl` 这种函数体，或者 `@Provides` 方法标成了 `abstract`，就会触发上面的错误。

### 误区 3：想拿 Context，怎么注入？

**错误写法**：直接把 `Activity` 当作依赖传给 VM——立刻内存泄漏。

**正确写法**：用 Hilt 预置的限定符：

```kotlin
// ✅ 推荐：拿 Application 级别的 Context
class MyRepository @Inject constructor(
  @ApplicationContext private val context: Context
) { /* ... */ }

// 偶尔：只在 Activity 范围里需要时
class MyHelper @Inject constructor(
  @ActivityContext private val context: Context
) { /* ... */ }
```

`@ApplicationContext` 给的是 App 级别的 Context，生命周期和 App 一样长，安全。`@ActivityContext` 只能用在标了 `@ActivityScoped` 的类里，给的是当前 Activity 的 Context。

### 误区 4：ViewModel 里能注入 Activity 吗？

**答案：永远不能。** 这是最容易被忽视的红线。

ViewModel 的生命周期比 Activity 长——旋转屏幕时 Activity 销毁重建，但 VM 留下来继续给新 Activity 用。如果 VM 持有 Activity 引用，旧 Activity 就无法被 GC，**经典的内存泄漏**。

| 类型 | VM 能注入吗 | 备注 |
|------|-----------|------|
| `Activity` / `Fragment` / `View` | ❌ 永远不能 | 生命周期比 VM 短，泄漏 |
| `Context`（`@ApplicationContext`） | ✅ 可以 | App 级别，安全 |
| `Application` | ✅ 可以 | 但通常用 `@ApplicationContext` 就够了 |
| Repository / DataSource / 普通类 | ✅ 可以 | 只要它们自己不持有 Activity |

如果某个工具类真的需要 `Context`，优先让它接受 `@ApplicationContext`，然后在 `@Module` 里 `@Provides` 出来。VM 永远只通过这种"被框架管理"的方式间接拿到 Context，而不是直接注入 Activity。

> 小技巧：如果怀疑有泄漏，开 debug 构建跑一下——项目已经集成 LeakCanary，会在通知栏弹出泄漏报告。详见附录 A1。

---

## 4.6 小结与下一站

本章从一个痛点出发——"手动 `new` 让对象创建和使用耦合，导致测试难、单例满天飞、Context 没法管"——并把依赖注入（DI）作为解法走了一遍：

- **DI 的核心**：把"创建对象"和"使用对象"分离，由容器统一构造。
- **Hilt 四件套**：`@HiltAndroidApp`（触发）+ `@Inject constructor(...)`（声明可造）+ `@Module + @InstallIn + @Provides/@Binds`（绑定接口与第三方）+ `@HiltViewModel` / `@AndroidEntryPoint`（接收注入）。
- **项目落地**：`JBusApplication.kt` 标 `@HiltAndroidApp`；`DataModule.kt` 用 ~20 个 `@Binds` 串起所有 Repository 接口与实现；`DatabaseModule.kt` 用 `@Provides` 处理 Room 这种带构造逻辑的对象；ViewModel 分普通 `@HiltViewModel` 与带路由参数的 `@AssistedInject` 两种。
- **红线**：ViewModel 永远不能注入 Activity；需要 Context 一律用 `@ApplicationContext`。

读完本章，你应该能看懂项目里所有 `@Inject` 出现的地方，并且知道新增一个 Repository 时要去 `DataModule.kt` 加一行 `@Binds`。

```
下一站：第 5 章 协程与 Flow —— 既然有了 Repository，它是怎么"异步"返回数据的？
```

---

🔍 深入阅读：
- Hilt 官方文档：https://dagger.dev/hilt/
- 项目所有绑定：`data/di/DataModule.kt`、`data/di/DatabaseModule.kt`
- AGENTS.md 里的代码规则："`@Binds` Repository 接口 → 实现"
