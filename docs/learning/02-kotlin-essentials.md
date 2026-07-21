# 第 2 章：现代 Kotlin 速览

> 📖 本章你将学到：项目里高频出现的 Kotlin 现代语法一次过完——data class、sealed、scope functions、null 安全、lambda、协程语法等。
> 🔗 前置章节：[第 1 章 项目总览](01-project-overview.md)（了解包结构即可）
> 📁 项目对应目录：`domain/model/`、`core/cache/`、`core/BaseExtension.kt`

---

## 2.1 为什么需要现代 Kotlin

第 1 章讲"现代 Android 三大范式"时埋了一个伏笔：所有范式都是用 Kotlin 写的。如果你只懂 Java 风格的 Kotlin（把 Kotlin 当 Java 写），读项目代码会处处卡壳——到处是 `?.`、`?:`、`apply`、`data class`、`when (x)`，不知道哪个是语法、哪个是约定。

先看 Kotlin 想解决 Java 的哪些痛点。

### 痛点 1：Java 写一个数据类太啰嗦

Java 里写一个"有 4 个字段的数据类"，你得写 getter、setter、`equals`、`hashCode`、`toString`，加起来 50 行起步：

```java
// Java：50 行只为表达 4 个字段
public class Movie {
    private String title;
    private String code;
    private String date;
    private String link;

    public Movie(String title, String code, String date, String link) { ... }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    // ... 还有 3 个字段同样的 setter/getter
    @Override public boolean equals(Object o) { ... }
    @Override public int hashCode() { ... }
    @Override public String toString() { ... }
}
```

字段一多，模板代码爆炸。更要命的是：业务演进时新增一个字段，equals/hashCode/toString 全得手动同步改——漏一处就有诡异 Bug。

### 痛点 2：null 是"十亿美元错误"

Java 里任何对象引用都可以是 `null`。编译器不会告诉你 `String title` 到底能不能为 null——只能靠约定、靠注释、靠运行时崩。`NullPointerException`（NPE）是 Java 工程师每天见到最多的异常，没有之一：

```java
// Java：每个调用都可能是地雷
String title = movie.getTags().get(0).getName().toLowerCase();
// ↑ 任意一环为 null 都 NPE，编译器不会提示
```

### 痛点 3：模式匹配弱、样板代码多

Java 写一个"根据状态分支处理"的逻辑，`switch` 只能匹配基本类型和字符串，更复杂的类型判断要靠 `instanceof + 强转`；集合操作（过滤、转换、分组）写出来也是一堆 `for + if + add`，意图被淹没。

> Kotlin 不是 Java 的语法糖。它把数据类、null 安全、模式匹配、高阶函数、协程等现代特性**直接做进了语言**，让代码更短、更安全、更表达意图。本章就把项目里高频出现的 8 个特性过一遍。

---

## 2.2 项目里高频出现的 8 个语法点

每个语法点用 3–5 行解释 + 一个最小示例。这是本章的主体，后续章节都会用到这些语法。

### 2.2.1 `data class` —— 一行声明一个数据类

`data class` 自动生成 `equals / hashCode / toString / copy / componentN`（解构）。Java 50 行的样板代码，Kotlin 一行搞定：

```kotlin
// 一行声明，编译器自动生成 equals/hashCode/toString/copy
data class Movie(
    val title: String,                  // val = 只读属性
    val code: String,
    val date: String,
    val tags: List<String>? = null      // 默认值 + 可空
)

val m1 = Movie("t", "AB-001", "2024-01-01")
val m2 = m1.copy(title = "新标题")       // copy 时只改想改的字段
val (title, code) = m1                  // 解构声明（componentN 自动生成）
```

### 2.2.2 `sealed class` / `sealed interface` —— 闭合类型层级

`sealed` 表示"所有子类型必须写在这同一个文件/包里"。配合 `when` 表达式，**编译器会强制你穷举所有分支**——以后新增一个子类型，所有 `when` 都会编译报错提醒你补上：

```kotlin
sealed interface CachedLoadEvent<out T> {
    data class Cached<T>(val entry: CacheEntry<T>) : CachedLoadEvent<T>
    data class Fresh<T>(val entry: CacheEntry<T>) : CachedLoadEvent<T>
    data class Failure(val throwable: Throwable, val hadCachedValue: Boolean) : CachedLoadEvent<Nothing>
}

// when 强制穷举：少写一个分支编译就报错
fun describe(e: CachedLoadEvent<*>) = when (e) {
    is CachedLoadEvent.Cached -> "命中缓存"
    is CachedLoadEvent.Fresh  -> "新数据"
    is CachedLoadEvent.Failure -> "失败"
}
```

### 2.2.3 Scope functions —— `let / run / apply / also / with`

5 个作用域函数长得像，容易用反。一张速查表搞定：

| 函数 | 引用对象 | 返回值 | 典型场景 |
|------|---------|--------|---------|
| `let`  | `it` | lambda 结果 | 可空判断后用：`x?.let { ... }` |
| `run`  | `this` | lambda 结果 | 执行一段计算并返回结果 |
| `apply` | `this` | 对象自己 | 配置对象：`Button().apply { text = "..."; onClick = ... }` |
| `also` | `it` | 对象自己 | 链式调用里插一句副作用：`x.also { log(it) }` |
| `with`  | `this` | lambda 结果 | 对同一对象连续调多个方法 |

```kotlin
// apply：配置对象，返回它自己
val movie = Movie(...).apply {
    // this 指向 movie，连续设置
}

// let：处理可空，返回 lambda 结果
val title: String? = movie.title
val len = title?.let { it.length } ?: 0
```

### 2.2.4 Null 安全三件套：`?` / `?:` / `!!`

Kotlin 把"能不能为 null"做进了类型系统。`String` 一定非空，`String?` 才可能为 null。三个操作符处理可空：

```kotlin
val tags: List<String>? = movie.tags   // ? 标记可空类型

val first = tags?.firstOrNull()        // ?. 安全调用：tags 为 null 时整个表达式为 null
val size  = tags?.size ?: 0            // ?: Elvis 操作符：左边为 null 时取右边
val must  = tags!!                     // !! 断言非空：为 null 就抛 NPE（慎用！）
```

> 经验：`!!` 几乎总有更安全的替代（`?` / `?:` / `let { }`）。看到 `!!` 先停一下，问问自己是不是真的确定它非空。

### 2.2.5 Lambda 与集合高阶函数

Kotlin 里函数是一等公民——可以当参数传、当返回值用。集合上的高阶函数让"过滤、转换、分组"意图一目了然：

```kotlin
val movies: List<Movie> = ...

movies.filter { it.date.startsWith("2024") }      // 过滤：留下 2024 年的
     .map { it.code }                              // 转换：取出番号
     .sortedBy { it }                              // 排序
     .groupBy { it.first() }                       // 分组：按首字母
     .forEach { (initial, codes) ->                // 遍历：解构 key/value
         println("$initial: $codes")
     }

val first2024 = movies.firstOrNull { it.date.startsWith("2024") }  // 找到第一个或 null
```

### 2.2.6 `by lazy` —— 懒加载委托

`by lazy` 把属性的初始化推迟到第一次访问——且只算一次，后续访问直接返回缓存值。适合"构造时算太贵、又可能用不到"的场景：

```kotlin
class MyRepo {
    // 第一次访问时才执行 lambda；后续访问直接返回缓存值
    val heavyConfig: Config by lazy {
        loadConfigFromDisk()    // 这行只在第一次访问时执行
    }
}
```

### 2.2.7 `suspend` 函数 —— 异步的"同步写法"

`suspend` 函数是协程的基础语法——它可以"挂起"而不阻塞线程，等结果回来再继续。看起来像同步代码，实际是异步的。第 5 章会细讲，这里只要认识这个关键字：

```kotlin
// suspend = 这个函数可以挂起、不阻塞线程；只能在协程里调
suspend fun loadMovies(): List<Movie> {
    val html = fetchHtml("https://...")   // suspend，自动挂起等网络
    return parseMovies(html)
}

// 调用方必须在协程里
viewModelScope.launch {
    val movies = loadMovies()             // 看起来像同步，实际异步
}
```

### 2.2.8 扩展函数（Extension function）—— 给已有类加方法

不修改类的源码，也能给它加方法。这是 Kotlin 复用代码的核心机制之一：

```kotlin
// 给 Context 加一个 paste() 方法，调用就像原生方法
fun Context.paste(): String? {
    val cmb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cmb.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
}

// 调用处：
val text = context.paste()    // 看起来是 Context 自带的方法
```

> 这 8 个语法点是项目代码的"基础词汇表"。下面把它们合到一段代码里看效果。

---

## 2.3 最小示例：5 个语法合在一起

下面这段虚构代码同时用到了 `data class`、`sealed`、`scope function`、null 安全、`lambda + 高阶函数`、`by lazy`、`suspend`、扩展函数 8 个特性。读一遍能过完本章所有语法：

```kotlin
// 1️⃣ data class：自动生成 equals/hashCode/copy
data class Movie(val code: String, val date: String, val tags: List<String>? = null)

// 2️⃣ sealed：闭合类型层级，配合 when 强制穷举
sealed interface LoadState<out T> {
    data class Ok<T>(val data: T) : LoadState<T>
    data class Err(val msg: String) : LoadState<Nothing>
}

// 8️⃣ 扩展函数：给 Movie 加一个属性
val Movie.year: Int get() = date.take(4).toIntOrNull() ?: 0

class MovieRepo {
    // 6️⃣ by lazy：第一次访问时才构造，后续直接用缓存
    val parser by lazy { MovieParser() }

    // 7️⃣ suspend：异步函数，看起来像同步
    suspend fun loadByYear(year: Int): LoadState<List<Movie>> {
        val raw = parser.fetch(year)              // 网络请求
        // 5️⃣ lambda + 高阶函数：过滤、分组、排序
        val grouped = raw
            .filter { it.year == year }            // 过滤
            .sortedBy { it.code }                  // 排序
            .groupBy { it.tags?.firstOrNull() ?: "未分类" }  // 分组
        return LoadState.Ok(grouped.values.flatten())
    }
}

fun describe(state: LoadState<List<Movie>>) {
    // 2️⃣ 配合 when：编译器强制穷举 Ok / Err
    val msg = when (state) {
        is LoadState.Ok -> {
            // 3️⃣ scope function：let 在可空/转换场景用
            // 4️⃣ null 安全：?: 给默认值
            state.data.firstOrNull()?.let { "共 ${state.data.size} 部，首部：${it.code}" } ?: "空"
        }
        is LoadState.Err -> "失败：${state.msg}"
    }
    println(msg)
}
```

每个语法点旁边都有数字编号——回到 §2.2 对应小节复习一遍。

---

## 2.4 项目中怎么用：8 个语法点的真实出处

本节每个语法点给一个真实项目引用，方便你打开源码对照。

### `data class` —— 领域模型全是它

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/domain/model/Movie.kt:26`

项目所有领域模型（`Movie`、`MovieDetail`、`Header`、`Genre`、`ActressInfo`……）都是 `data class`。这是 Kotlin 里表达"数据载体"的标准写法。配合 `@SerializedName`（Gson 注解）保证序列化字段名稳定。

### `sealed interface` —— 缓存加载三态

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/core/cache/CacheModels.kt:12`

项目用 `sealed interface CachedLoadEvent<out T>` 表达**缓存加载的三种状态**：`Cached`（命中缓存）、`Fresh`（新数据来了）、`Failure`（失败）。配合 `when`，调用方必须显式处理每一种情况，不会漏掉失败分支。第 8 章讲缓存策略会重点用到这里。

### Scope functions —— 配置对象用 `apply`

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/db/dao/CategoryDao.kt`

数据库 DAO、OkHttp 客户端等"需要连续配置多个属性"的场景，项目普遍用 `apply` 链。例如 `ArrayMap` 工厂函数：

```kotlin
fun <K, V> arrayMapof(vararg pairs: Pair<K, V>): ArrayMap<K, V> =
    ArrayMap<K, V>(pairs.size).apply { putAll(pairs) }   // apply：构造完配置一下，返回自己
```

### Null 安全 —— 项目里几乎看不到 `!!`

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/core/BaseExtension.kt:41`

`Context.paste()` 从剪贴板读文本，每一步都可能为 null，全程用 `?:` 和 `?.`：

```kotlin
fun Context.paste(): String? {
    val cmb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cmb.primaryClip ?: return null                    // ?: 命中 null 直接返回
    return if (clip.itemCount > 0)
        clip.getItemAt(0).coerceToText(this)?.toString()         // ?. 安全调用
    else null
}
```

项目代码评审（`docs/CODE_REVIEW.md`）也把"`!!` 滥用"列为常见坏味道，详见 §2.5。

### Lambda + 高阶函数 —— 解析器的主战场

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/parser/MovieHtmlParser.kt`

HTML 解析把节点列表用 `map { }` 转成领域模型、用 `filter { }` 剔除无效条目、用 `firstOrNull { }` 找首个匹配——整套都是函数式风格。第 7 章细讲。

### `by lazy` —— 全局单例推迟初始化

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/core/GsonExt.kt:32`

全局 `GSON` 单例用 `by lazy` 推迟到第一次访问才构造，避免 App 启动时初始化所有单例：

```kotlin
val GSON by lazy {
    GsonBuilder()
        .registerTypeAdapterFactory(ContentBlockAdapterFactory)
        .create()
}
```

类似用法还有 `core/cache/CacheStore.kt:36` 的 `memoryCache by lazy { initMemoryCache() }`、`core/http/NetClient.kt:93` 的 `okHttpClient by lazy { ... }`。

### `suspend` + `viewModelScope.launch` —— ViewModel 全靠它

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/ui/movielist/MovieListViewModel.kt:294`

项目所有 ViewModel 用 `viewModelScope.launch { }` 启动协程，在里面调 `suspend` 函数：

```kotlin
fun loadFirstPage() {
    firstPageJob = viewModelScope.launch {        // 协程作用域绑定 VM 生命周期
        val events = repository.observeFirstPage() // suspend / Flow 调用
        events.collect { event ->
            // 处理 CachedLoadEvent（回扣 §2.4 sealed interface）
        }
    }
}
```

第 5 章会展开讲协程与 Flow。

### 扩展函数 —— `BaseExtension.kt` 集中放通用扩展

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/core/BaseExtension.kt`

文件里集中定义了 `Context.copy()`、`Context.paste()`、`Context.packageInfo`、`Int.MB` 等扩展。**给已有类加方法而不修改它**——这是 Kotlin 复用代码的核心机制，项目大量使用。

> 📁 项目对应位置：项目里高频用法的代表文件：`domain/model/`（data class 集中地）、`core/cache/CacheModels.kt`（sealed）、`core/BaseExtension.kt`（扩展函数）。

---

## 2.5 常见误区与调试技巧

新手第一次读 Kotlin 代码最容易栽在这三个坑上。

### 误区 1：`!!` 滥用——编译能过但运行崩

**症状**：代码写完编译通过，跑起来某个时机突然 `NullPointerException`。

**原因**：`!!` 是"断言非空"——它告诉编译器"我保证它不是 null"，编译器就放行了；但运行时如果真的是 null，立刻抛 NPE。`!!` **绕过了 Kotlin 的类型系统保护**，把 null 安全退化回 Java 水平。

**怎么修**：看到 `!!` 先停下来问问"我真的 100% 确定它非空吗？"通常有更安全的替代：

```kotlin
// ❌ 滥用 !!
val title = movie!!.title                // 万一 movie 为 null 就崩
val first = tags!![0]                    // 万一 tags 为 null 或空就崩

// ✅ 用 ? / ?: / let 处理可空
val title = movie?.title ?: "未知"       // 安全：null 时取默认值
val first = tags?.firstOrNull()          // 安全：null 或空时返回 null
movie?.let { doSomething(it) }           // 安全：只在非空时执行
```

**经验**：项目代码评审明确把"`!!` 滥用"列为坏味道。除了极少数框架边界（如 `lateinit var`、`onCreate` 之后必非空的字段），生产代码应尽量避免 `!!`。

### 误区 2：`apply` 和 `let` 用反

**症状**：写完 `val result = obj.apply { transform(it) }`，发现 `result` 不是变换后的值，而是 `obj` 自己。

**原因**：把两个作用域函数的语义搞混了。记住——

- **`apply`** 返回**对象自己**（用于"配置对象"）；
- **`let`** 返回 **lambda 的结果**（用于"转换 / 处理可空"）。

```kotlin
// ❌ apply 用错：想算 length 却拿到了原字符串
val len = "hello".apply { length }       // len 是 "hello"，不是 5

// ✅ let 才对：返回 lambda 结果
val len = "hello".let { it.length }      // len 是 5

// ✅ apply 的正确场景：配置对象
val builder = OkHttpClient.Builder().apply {
    connectTimeout(10, TimeUnit.SECONDS)
    addInterceptor(loggingInterceptor)
}.build()                                // 返回配置好的 builder
```

**速记口诀**：**"配对象用 apply，转结果用 let"**。其他三个（`run / also / with`）出现频率低，遇到再查表即可。

### 误区 3：在 `forEach` 里 `return`，以为跳出的是外层函数

**症状**：在 `forEach` 里 `return` 想结束外层函数，结果外层函数继续执行了。

**原因**：`forEach` 接收的是一个 lambda，**lambda 里的 `return` 默认只跳出 lambda 自身**（也就是跳过当前元素），不会结束外层函数：

```kotlin
fun findBug() {
    listOf(1, 2, 3).forEach {
        if (it == 2) return              // ⚠️ 只跳过当前元素，不会结束 findBug
        println(it)
    }
    println("这一行照样执行")             // ← 仍然会打印
}
```

**怎么修**：三种选择，按场景挑——

```kotlin
// 1. 用 label 显式跳出外层（不推荐，可读性差）
fun findBug() {
    listOf(1, 2, 3).forEach {
        if (it == 2) return@findBug      // ← 显式跳出 findBug
        println(it)
    }
}

// 2. 改用 for 循环（最直观）
fun findBug() {
    for (it in listOf(1, 2, 3)) {
        if (it == 2) return              // ← 真的结束 findBug
        println(it)
    }
}

// 3. 用 firstOrNull / any / none 等高阶函数替代命令式控制流（推荐）
fun findBug() {
    val target = listOf(1, 2, 3).firstOrNull { it == 2 }
    if (target != null) return
}
```

> 经验：函数式风格优先用"高阶函数 + 表达式"替代"循环 + return"，意图更清晰、Bug 更少。

---

## 2.6 小结与下一站

本章把项目里高频出现的 Kotlin 现代语法一次过完：

- **`data class`**：一行声明数据类，自动生成 equals/hashCode/toString/copy。
- **`sealed class/interface`**：闭合类型层级，配合 `when` 强制穷举——项目用 `CachedLoadEvent` 表达缓存三态。
- **Scope functions**：`apply` 配对象、`let` 转结果、`also` 插副作用。
- **Null 安全**：`?` 标记可空、`?.` 安全调用、`?:` 给默认、`!!` 慎用。
- **Lambda + 高阶函数**：`map / filter / forEach / groupBy / firstOrNull / sortedBy` 让集合操作意图清晰。
- **`by lazy`**：懒加载委托，单例/重对象推迟到首次访问。
- **`suspend`**：协程基础语法，异步代码同步写法（第 5 章细讲）。
- **扩展函数**：给已有类加方法不修改它，`BaseExtension.kt` 集中放通用扩展。

读完本章，你应该能：
- 看懂项目里 90% 的 Kotlin 语法；
- 知道每个语法解决什么问题、什么场景该用哪个；
- 看到 `?.` `?:` `apply` `when` 这些符号不再卡壳。

如果某个语法点还觉得陌生，建议打开 §2.4 列出的对应项目源码读 10 分钟——这些语法在真实代码里反复出现，多看几次就熟了。

```
下一站：第 3 章 单 Activity 架构 —— 看 App 入口是怎么组织的，为什么全项目只有一个 Activity。
```

---

🔍 深入阅读：
- Kotlin 官方文档：https://kotlinlang.org/docs/home.html
- Kotlin Koans（互动练习）：https://play.kotlinlang.org/koans/
- 项目内高频用法的代表文件：`domain/model/`、`core/cache/CacheModels.kt`、`core/BaseExtension.kt`
