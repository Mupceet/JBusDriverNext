# 附录 A1：工程实践

> 📖 本附录你将学到：项目怎么构建出 debug / release 两个版本、ProGuard/R8 混淆为什么要 keep Gson 字段、怎么写单元测试和插桩测试、怎么用 LeakCanary 抓内存泄漏。
> 🔗 前置章节：建议先读完正文 12 章；如果跳读，第 4 章（Hilt）和第 12 章（Gson）相关性最强
> 📁 项目对应目录：`app/build.gradle.kts`、`app/proguard-rules.pro`、`app/src/test/`、`app/src/androidTest/`

---

## A1.1 Why：能写代码 ≠ 能上线

前面 12 章都在讲"代码怎么写"——Compose 怎么布局、Hilt 怎么注入、Flow 怎么收集。但工程实践中还有一类问题，跟具体业务无关，却能让一个本来"在我电脑上能跑"的项目直接装不进用户手机：

### 痛点 1：Debug 能跑 Release 崩

新人最常踩的坑：本地 debug 构建调试得好好的，编译 release APK 装到手机上——影片列表空白、详情页字段全 null、收藏功能失效。99% 是 **R8 混淆没 keep 关键字段**，Gson 反序列化失败。这不是代码写错，是构建配置的坑。

### 痛点 2：数据库 schema 改了忘升 version

给 Room 实体加个字段、改个类型，运行时直接崩——Room 在启动时校验 schema，发现和磁盘上的旧库不匹配就抛 `IllegalStateException`。更糟的是如果用了 `fallbackToDestructiveMigration`，老用户的收藏数据会被静默清空。

### 痛点 3：代码改完不知道有没有破坏其它地方

没有任何测试时，改一行代码只能"装到手机上点点看"。点得到的地方可能没问题，点不到的边界情况（分页到第 5 页、空数据、网络错误）就是上线后用户报 bug 才发现。工程化的做法是写测试，让机器替你点几百遍。

### 本附录讲四件事

> 工程化的四件事：**构建变体（debug/release）**、**ProGuard/R8 混淆**、**单元测试 / 插桩测试**、**Lint + LeakCanary 等工具**。本附录把项目里的实际配置讲一遍。

读完之后，你应该能自己跑 `./gradlew assembleRelease` 出一个能装的 release 包，看懂 `proguard-rules.pro` 里每一行的作用，并知道新增一个功能后该怎么补测试。

---

## A1.2 What：四个核心概念

### 1. Gradle 构建变体（Build Variants）

同一个项目可以构建出多个变体。Android 默认就有两个：

- **debug**：带调试工具、包名加 `.debug` 后缀、不开启混淆——可以和 release 版同时装在一台手机上调试
- **release**：启用混淆、压缩资源、签名——用来上架或分发

变体之间不仅包名不同，连 `BuildConfig` 里暴露给代码的常量都可以不一样（比如 debug 打开调试开关、release 关掉）。

### 2. ProGuard / R8

R8 是 Google 替代 ProGuard 的官方工具（项目里仍然把规则文件叫 "proguard-rules.pro"，但实际执行的是 R8）。它对字节码做三件事：

- **混淆（Obfuscate）**：把类名、方法名、字段名改成 `a`、`b`、`c`——防反编译，apk 体积变小
- **压缩（Shrink）**：删掉没用到的类和方法（`isShrinkResources = true` 还会删无用资源）
- **优化（Optimize）**：内联、死代码消除

问题来了：**Gson、Room 这类靠反射读字段名的库会被搞坏**。R8 把 `Movie.title` 字段名改成 `a` 后，Gson 反射找 `title` 就找不到。所以必须用 `-keep` 规则告诉 R8："这些类的字段名不许动"。

### 3. 单元测试 vs 插桩测试

| 类型 | 跑在哪 | 目录 | 速度 | 能测什么 |
|------|--------|------|------|----------|
| 单元测试（unit test） | JVM | `app/src/test/` | 几毫秒一个用例 | 纯 Kotlin 逻辑、ViewModel、Reducer |
| 插桩测试（instrumented test） | 设备/模拟器 | `app/src/androidTest/` | 几秒一个用例 | Compose UI、Room、真实 Android 框架 |

项目里绝大多数是单元测试（ViewModel、StateReducer、Repository 逻辑）；插桩测试只用于必须 Android 环境的场景（Compose 点击交互、DAO 真实数据库）。

### 4. Lint + LeakCanary

- **Lint**：编译期静态检查——扫"未使用代码""硬编码字符串""过时 API"等问题。`./gradlew lintDebug` 触发，编译器警告级别的扩展版。
- **LeakCanary**：debug 运行时检测内存泄漏——App 跑一段时间，如果 Activity 销毁了还被某对象持有，通知栏会弹橙色 LeakCanary 通知，点进去看泄漏栈。只在 debug 构建启用（`debugImplementation`）。

---

## A1.3 最小示例：构建配置 + 一个测试

### 最简构建配置

```kotlin
// app/build.gradle.kts（简化版）
android {
  buildTypes {
    release {
      isMinifyEnabled = true                  // ← 开启 R8 混淆
      isShrinkResources = true                // ← 删除无用资源
      proguardFiles("proguard-rules.pro")     // ← 自定义 keep 规则
    }
    debug {
      applicationIdSuffix = ".debug"          // ← 包名加后缀（可与 release 共存）
      isMinifyEnabled = false                 // ← debug 不混淆
    }
  }
}
```

### 最简单元测试

```kotlin
// app/src/test/.../MyTest.kt
class MyTest {
  @Test
  fun `addition works`() {
    assertEquals(4, 2 + 2)       // ← 断言：期望值 vs 实际值
  }
}
```

跑一下 `./gradlew test`，IDE 报告通过。这就完成了"测试能跑"的最小心智模型。

---

## A1.4 项目中怎么用：四个真实场景

### A1.4.1 构建变体

📁 项目对应位置：`app/build.gradle.kts:98-123`

```kotlin
buildTypes {
  release {
    buildConfigField("boolean", "CACHE_REFRESH_TEST_MODE", "false")
    applicationIdSuffix = ".release"           // 包名加后缀（与 debug 共存）
    if (hasReleaseSigning) {
      signingConfig = signingConfigs.getByName("release")
    }
    isMinifyEnabled = true                     // ← 开启 R8 混淆
    isShrinkResources = true                   // ← 删除无用资源
    proguardFiles(
      getDefaultProguardFile("proguard-android-optimize.txt"),
      "proguard-rules.pro"                     // 项目自定义规则
    )
    manifestPlaceholders["allowBackup"] = "false"
    manifestPlaceholders["usesCleartextTraffic"] = "false"
  }
  debug {
    buildConfigField("boolean", "CACHE_REFRESH_TEST_MODE", cacheRefreshTestMode.toString())
    applicationIdSuffix = ".debug"
    isMinifyEnabled = false                    // debug 不混淆（快、好调试）
    enableUnitTestCoverage = true              // 代码覆盖率
    enableAndroidTestCoverage = true
    manifestPlaceholders["allowBackup"] = "true"
    manifestPlaceholders["usesCleartextTraffic"] = "true"
  }
}
```

几个关键点：

- **debug 和 release 可以同时装在一台手机上**——因为 `applicationIdSuffix` 让包名不一样（`me.jbus.debug` 和 `me.jbus.release`）。开发时你可以装着 debug 调试，同时打开 release 验证混淆后有没有崩。
- **release 才开启混淆**：debug 开混淆会拖慢增量编译，而且断点调试会因字节码被改名而失效。
- **`BuildConfig.CACHE_REFRESH_TEST_MODE`**：debug 里通过 `-PcacheRefreshTestMode=true` 这个 Gradle property 切换（详见 §A1.4.4 调试小贴士）。代码里读 `BuildConfig.CACHE_REFRESH_TEST_MODE` 就能拿到，相当于一个"运行时调试开关"。
- **APK 命名**：项目约定 `jbus_{buildType}_v{versionName}.apk`——debug → `jbus_debug_v1.20260719.apk`，release → `jbus_release_v1.20260719.apk`。

### A1.4.2 ProGuard 规则——为什么必须 keep Gson 字段

📁 项目对应位置：`app/proguard-Rules.pro:8-25`（Gson keep 规则起点）

```proguard
# Gson - reflection-based serialization
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

# CacheEnvelope 被序列化到磁盘，所有字段名都不能改
-keep class me.jbusdriver.modern.core.cache.CacheEnvelope {
    <fields>;
}

# 每个被 Gson 序列化的模型都要逐个列出来
-keep class me.jbusdriver.modern.domain.model.Movie {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.MovieDetail {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ActressInfo {
    !static !transient <fields>;
}
# ... 还有约 30 个模型类，逐个 -keep（详见 proguard-rules.pro）
```

**为什么要这么写？** Gson 用**反射**读字段名：JSON 里写 `{"title": "..."}`，Gson 就在对象里找一个叫 `title` 的字段。R8 把字段名改成 `a` 后，反射就找不到 `title` 字段了——反序列化结果是个全 null 的对象，运行时崩或显示空数据。

**`!static !transient <fields>` 的含义**：保留所有"非 static、非 transient"的字段名。`static` 字段不属于实例状态，`transient` 是开发者明确表示"不要序列化"，剩下的全保留——这正符合 Gson 的语义。

**最经典的翻车场景**：

1. 你给 `domain/model/` 加了个新数据类 `Xxx`
2. 通过 Gson 把它写到磁盘缓存
3. debug 跑得好好的（没开混淆）
4. 编译 release APK 装到手机——读缓存时全是 null

99% 是这个新类没在 `proguard-rules.pro` 里 `-keep`。

📁 项目对应位置：项目目前**逐个显式 keep 了** `domain.model.*` 下所有 Gson 模型（约 30 个类）。**新增模型时必须手动加一行 `-keep`**，或者用 `@Keep` 注解标注整个类。AGENTS.md §Code Quality Rules 明确要求："ProGuard/R8 keep rules must cover all Gson model classes; add `@Keep` or rules proactively."

> 为什么不用 `domain.model.**` 通配符一把梭？显式列出每个类更安全——你能清楚知道哪些类走了 Gson，加新类时也会被 review 到。通配符虽然省事，但容易意外 keep 一些不需要的类，或者漏掉一些放在别处的 Gson 模型（比如 `core.cache.CacheEnvelope`、`data.SessionCookieStore$PersistedCookie`）。

### A1.4.3 单元测试与插桩测试

📁 项目对应位置：`app/src/test/java/me/jbusdriver/modern/ui/movielist/MovieListViewModelTest.kt:156`

```kotlin
class MovieListViewModelTest {

  @Test
  fun loadFirstPage_loadsMoviesAndPageInfo() = runTest(testDispatcher) {
    // 1. 准备一个假 repository（实现 MovieRepository 接口，返回硬编码数据）
    val repository = fullFakeRepo { _, _ ->
      MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
    }
    // 2. 构造 ViewModel，把假 repo 塞进去
    viewModel = MovieListViewModel(repository, stubLocalVideoRepo)

    // 3. 调被测方法
    viewModel.loadFirstPage()
    advanceUntilIdle()                          // 让协程跑完

    // 4. 断言 UI 状态符合预期
    assertEquals(2, viewModel.uiState.value.movies.size)
    assertEquals(1, viewModel.uiState.value.pageInfo.activePage)
    assertTrue(viewModel.uiState.value.hasMore)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }
}
```

关键点：

- **`runTest(testDispatcher) { }`** 用虚拟时间跑协程——`advanceUntilIdle()` 让挂起的协程立即恢复，几毫秒跑完一个用例
- **fake repository**：实现同一个 `MovieRepository` 接口，返回硬编码数据。这正是第 4 章讲的"接口 + 实现分开"的价值——VM 依赖接口，测试就能塞假的
- **断言 UI 状态**：VM 把所有状态暴露成 `StateFlow`，测试直接读 `vm.uiState.value` 检查。**ViewModel 永远不暴露回调给 UI，只暴露状态**——这条规则让测试变得极其简单（详见 AGENTS.md §Code Quality Rules）

插桩测试跑在真实设备或模拟器上，适合测 Compose UI 交互：

📁 项目对应位置：`app/src/androidTest/java/me/jbusdriver/modern/ui/components/`

```kotlin
@RunWith(AndroidJUnit4::class)
class MovieListTest {
  @get:Rule val composeRule = createAndroidComposeRule<ModernMainActivity>()

  @Test
  fun tap_movie_navigates_to_detail() {
    composeRule.onNodeWithText("某影片").performClick()
    composeRule.onNodeWithText("详情").assertIsDisplayed()
  }
}
```

关键点：插桩测试需要 `./gradlew connectedAndroidTest` + 真实设备/模拟器；慢但能测端到端行为（启动 Activity、Compose 渲染、点击、导航）。

测试命令（来自 `AGENTS.md` §Build Commands）：

```bash
./gradlew test                              # 全部单元测试
./gradlew connectedAndroidTest              # 全部插桩测试（需设备/模拟器）
./gradlew test --tests "me.jbusdriver.modern.ui.movielist.MovieListViewModelTest"   # 单个测试类
```

### A1.4.4 LeakCanary + Lint + 调试小贴士

📁 项目对应位置：`app/build.gradle.kts:187` 的 `debugImplementation(libs.leakcanary)`

- **LeakCanary**：debug 构建自动启用，不需要写任何代码。App 跑一段时间，如果发生内存泄漏（比如 Activity 销毁了还被某个单例持有），通知栏会弹橙色 LeakCanary 通知，点进去看泄漏栈和引用链。这是排查第 4 章讲的那种"VM 持有 Activity"泄漏最有效的工具。
- **Lint**：`./gradlew lintDebug`，编译期扫"未使用代码""硬编码字符串""过时 API"等问题。CI 跑一遍能挡掉很多低级错误。

**调试小贴士**（汇总前面各章提到的）：

1. **Logcat 过滤 `JBus` 看自定义日志**——项目的 `KLog.kt` 默认用这个 tag（详见第 1 章 §1.5）
2. **调试 Hilt 注入**：在 VM 的 `init { }` 块加一行 `Log.d("JBus", "构造 ViewModel: $this")`，跑一下看是否被构造、构造几次（详见第 4 章 §4.5）
3. **调试网络**：OkHttp 已配 `HttpLoggingInterceptor`，Logcat 过滤 `OkHttp` 看请求 URL 和响应体（详见第 7 章）
4. **调试缓存 SWR**：用 `./gradlew assembleDebug -PcacheRefreshTestMode=true` 装一个"SWR 短 TTL"测试构建，几秒就能看到缓存刷新流程（详见 `TEST_CASES.md`）
5. **调试 Compose 重组**：Android Studio 的 Layout Inspector 可以看 Compose 树，并标注哪些 Composable 在频繁重组

---

## A1.5 常见误区与避坑

### 误区 1：Debug 能跑 Release 崩

**症状**：release APK 装上手机后，影片列表空白、详情页字段全 null、收藏功能失效。Logcat 看到 Gson 反序列化警告。

**根因**：99% 是某个 Gson 模型没在 `proguard-rules.pro` 里 `-keep`，R8 把字段名改成 `a/b/c` 后反射失败。

**怎么修**：

1. 找到那个模型类（通常在 `domain/model/` 下，或 `core/cache/` 下）
2. 在 `proguard-rules.pro` 加一行 `-keep class xxx.YyyClass { !static !transient <fields>; }`
3. 重新 `./gradlew assembleRelease` 验证

**AGENTS.md 规则**："After any Gson, ProGuard, or R8 changes, verify debug and release behavior for representative JSON payloads."——改完混淆规则**必须**跑 release smoke test 验证代表性 JSON 能正确反序列化。

### 误区 2：改了数据库 schema 没升 version

**症状**：App 启动直接崩，`IllegalStateException: Room cannot verify the data integrity`。

**根因**：你给 Room 实体加了字段、删了字段、改了字段类型，但 `@Database(version = N)` 还是旧版本号。Room 启动时校验 schema 不匹配就立刻崩。

**怎么修**：

1. 把 `@Database(version = N)` 升到 `N+1`
2. 写一个 `Migration(N, N+1) { execute("ALTER TABLE ...") }`，在 `DatabaseModule` 里通过 `addMigrations()` 注册
3. 或者，如果只是开发期不在乎数据：用 `fallbackToDestructiveMigration()` 让 Room 清空旧库重建——**但绝不能用在生产**，老用户的数据会丢

### 误区 3：改了构建变体忘记改 BuildConfig 字段

**症状**：你以为打开了"缓存刷新测试模式"，但 App 行为没变化。

**根因**：你在代码里读 `BuildConfig.CACHE_REFRESH_TEST_MODE`，但构建时没传 `-PcacheRefreshTestMode=true`，或者改了取值逻辑却没改 `build.gradle.kts:115` 那行 `buildConfigField(...)`。

**怎么排查**：在代码里加一行 `Log.d("JBus", "test mode = ${BuildConfig.CACHE_REFRESH_TEST_MODE}")`，跑起来看实际值是多少。

### 误区 4：不要直接 commit 到 `release` 分支

**项目规范**（`AGENTS.md` §Build & Commit Hygiene）：

- "Never commit directly to `release`; work on `develop` or a feature branch."
- "Run a build check (`./gradlew assembleDebug`) before committing to catch compilation errors."

新人最容易在 `main` 或 `release` 上直接改代码 push——一旦出错会影响所有人的构建。正确做法是切一个 feature 分支，改完跑 `./gradlew assembleDebug` 确认编译通过，再开 PR。

---

## A1.6 小结：这套学习文档带你看过了什么

这是本套学习文档的最后一章。到这里你已经走完了从"完全不懂这个项目"到"能动手改一个功能"的全路径。回顾一下：

### 全书结构

| 章节 | 主题 | 解决的问题 |
|------|------|-----------|
| 起步篇（第 1-2 章） | 项目总览、Kotlin 速览 | "这个项目在做什么、用的是什么语言" |
| 架构篇（第 3-4 章） | 单 Activity、依赖注入 | "为什么没有 10 个 Activity、为什么看不到 `new`" |
| 数据篇（第 5-8 章） | 协程 / Flow、Repository、网络、缓存 SWR | "数据是怎么异步加载、缓存、刷新的" |
| UI 篇（第 9-11 章） | Compose、ViewModel + State、Navigation | "界面是怎么写出来的、状态怎么管、页面怎么跳" |
| 持久化与工程（第 12 章 + 本附录） | Room / DataStore / Gson、构建 / 测试 / 调试 | "数据怎么存到磁盘、项目怎么构建上线" |

### 一句话回顾每一章的核心

1. **项目总览**——Compose + Hilt + 协程三件套，项目分 `core/data/domain/ui` 四层
2. **Kotlin 速览**——`data class`、`sealed`、`suspend`、扩展函数、作用域函数
3. **单 Activity**——`ModernMainActivity` 挂 `setContent { JBusNavigation() }`
4. **依赖注入**——`@HiltAndroidApp` + `@Inject constructor` + `@Module`；VM 永远不注入 Activity
5. **协程与 Flow**——`viewModelScope.launch`、`StateFlow`、结构化并发
6. **Repository 模式**——接口 + 实现，VM 只依赖接口（测试能塞假的）
7. **网络与解析**——`HtmlClient` 抓 HTML → Jsoup 解析 → 领域模型
8. **缓存策略**——SWR（stale-while-revalidate）：先发缓存、后台拉新、择机刷新
9. **Compose 基础**——`@Composable`、`remember`、状态提升
10. **ViewModel 与状态**——`StateFlow<UiState>` 集中状态、单向数据流
11. **导航**——Nav3 的 `NavKey` 路由、`@AssistedInject` 传路由参数
12. **持久化与设置**——Room 收藏、DataStore 偏好、Gson 序列化
13. **附录 A1（本章）**——构建变体、ProGuard、测试、LeakCanary

### 接下来推荐你看

- **通读 `AGENTS.md`**——项目所有规则的权威出处：架构、包结构、构建命令、代码质量规则、技术债清单
- **看 `docs/CODE_REVIEW.md`**——已修复的 P0/P1 问题、剩余的非阻塞性技术债
- **翻 `docs/superpowers/specs/` 和 `plans/`**——每个功能的设计与实施历史，看真实的设计权衡
- **想加新功能时**，先在本书找相关章节（比如"加个列表页"→ 第 6 / 9 / 10 章），再打开对应代码目录验证理解

### 最关键的忠告

> 这套文档带你建了心智模型，但**真正的能力来自动手**——挑一个简单功能（新增设置项、改个列表项样式、加个 Toast 提示），从 ViewModel 写到 UI，跑通调试，再回头看代码就顺了。

第一次改可能要花一整天——查接口、查注解、改完编译报错再排查。这是正常的。改完三个小功能之后，你会发现这套架构的节奏感：状态在 `StateFlow`、逻辑在 VM、数据走 Repository、UI 是状态的函数。届时你就不需要再翻文档了。

祝你写得开心。

```
（全文完）
```

---

🔍 深入阅读：
- Android Gradle Plugin 文档：https://developer.android.com/build
- ProGuard / R8 手册：https://www.guardsquare.com/manual/home
- LeakCanary：https://square.github.io/leakcanary/
- 测试 Android 应用：https://developer.android.com/training/testing
- 项目工程化脚本：`scripts/`（如有）
- 项目测试目录：`app/src/test/`、`app/src/androidTest/`
- 项目规则总集：`AGENTS.md`
