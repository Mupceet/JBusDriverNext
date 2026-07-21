# 第 12 章：Room、DataStore、Gson 序列化

> 📖 本章你将学到：项目怎么用 Room 存数据库、DataStore 存偏好、Gson 自定义 Adapter 处理多态序列化；以及为什么缓存的模型要 keep 字段避免被 R8 混淆。
> 🔗 前置章节：[第 4 章 依赖注入](04-dependency-injection.md)（`@Provides`）、[第 5 章 协程与 Flow](05-coroutines-flow.md)（StateFlow）
> 📁 项目对应目录：`data/db/`、`data/settings/`、`core/serialization/`、`core/GsonExt.kt`

---

## 12.1 为什么需要持久化和序列化

前面几章讲的数据都是"临时的"：网络拉下来 → 解析成对象 → 显示到 UI。但 App 一关，这些对象就全没了。一个真实的内容型 App 至少有三类数据需要"留下来"：

### 痛点 1：列表数据要持久化

用户收藏了 50 部影片、浏览过 200 条历史——关掉 App 再打开，这些**必须还在**。否则用户每次都要重新找，体验直接崩盘。

这种数据有共同特征：**结构化**（每条记录字段都一样）、**需要查询**（按分类、按时间）、**会变**（用户随时加新的、删旧的）。用纯文本文件存显然不合适——你要自己写"按分类查"、"分页"、"去重"的逻辑。这正是**关系型数据库**擅长的事。

### 痛点 2：设置项要存储

用户选了"深色主题"、"镜像站 A"、"列表样式 = 网格"、"自动加载 GIF 关"——这些**键值对**配置不能丢。下次启动要还原。

这种数据特征相反：**每个键对应一个值**（字符串、布尔、整数），不需要复杂查询，只需要"读 + 写"。用数据库就杀鸡用牛刀了。

### 痛点 3：缓存要序列化到磁盘

第 8 章会讲项目的 **SWR（Stale-While-Revalidate）** 缓存策略：列表/详情对象除了放内存，还要落盘——下次启动秒开。但内存里的对象是 `MovieDetail`、`ContentBlock` 这种**对象图**，磁盘只认字节。怎么把对象变成可存可读的字节、再从字节还原回对象？这就是**序列化**要解决的事。

> 项目用三个工具分工：**Room** 存结构化数据（收藏、历史）、**DataStore Preferences** 存键值对（设置项）、**Gson** 做对象序列化（缓存落盘、Entity 的 JSON 字段）。本章三节分别过一遍。

---

## 12.2 三个工具分别是什么

### 1. Room — SQLite 的封装

Android 自带 SQLite 数据库，但原生 API 要手写 SQL 字符串、手动 `cursor.getString(0)`、手动关连接、手动 migrations——又啰嗦又容易错。**Room** 在 SQLite 上加了一层注解：

- 用 `@Entity` 标记"这是个表"、`@PrimaryKey` 标主键、`@Index` 标索引；
- 用 `@Dao` 接口 + `@Query` / `@Insert` / `@Delete` 注解声明查询，**编译期生成实现**（SQL 写错直接编译失败）；
- **原生支持 `suspend` 函数和 `Flow<List<T>>`**——写操作用 `suspend`、读操作返回 `Flow`，表一变 Flow 自动发新数据，UI 自动刷新。

### 2. DataStore Preferences — SharedPreferences 的现代替代

老 Android 用 `SharedPreferences` 存键值对，它有三个硬伤：**同步 API 容易 ANR**（`apply()` 也是阻塞磁盘）、**类型不安全**（`getString("theme", null)` 一不小心就 NPE）、**不支持异步流**（改了值得手动通知）。

**DataStore Preferences** 解决了全部三点：完全异步（`suspend` + `Flow`）、键有类型（`stringPreferencesKey("theme")` 明确是字符串）、读出来直接是 `Flow<T>`（改了值 Flow 自动发新值）。**用法就是它的取代品**，没有理由再写新的 `SharedPreferences` 代码。

### 3. Gson + 自定义 TypeAdapter — JSON 序列化

**Gson** 是 Google 的 JSON 库，默认能把普通 `data class` 序列化成 JSON、再反序列化回来——通过反射读字段名。

但**多态类型**它搞不定。比如项目的 `ContentBlock` 是个 sealed 类型，有 5 个子类（`RichText` / `ListBlock` / `Image` / `Quote` / `RestrictedNotice`），字段完全不同。一段 JSON 长这样：

```json
{ "type": "image", "url": "https://...", "width": 200 }
```

默认 Gson 看到 `ContentBlock` 这个父类型，根本不知道该造哪个子类。**解决方法**就是写一个自定义 `TypeAdapter`，告诉它"先读 `type` 字段，再根据值决定反序列化成哪个子类"。

> 三个工具的分工一句话总结：**Room 管"查询"（按分类、按时间）、DataStore 管"读写单个值"、Gson 管"对象 ↔ 字节"**。下面三节用最小示例感受一下，然后回到真实项目代码。

---

## 12.3 最小示例：三段独立代码

脱离项目用三段最简代码把每个工具的 API 走一遍，建立直觉。下一节再回到真实项目代码。

### 12.3.1 Room：`@Entity` + `@Dao`

```kotlin
// 1️⃣ 定义表：一行 data class = 一张表
@Entity(tableName = "note")
data class Note(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val content: String
)

// 2️⃣ 定义查询：DAO 是个接口，注解告诉 Room SQL
@Dao
interface NoteDao {
  // 读：返回 Flow，表一变自动重发
  @Query("SELECT * FROM note ORDER BY id DESC")
  fun observeAll(): Flow<List<Note>>

  // 写：suspend，不阻塞主线程
  @Insert
  suspend fun insert(note: Note): Long
}
```

不需要写任何 SQL 执行代码——Room 在编译期读这些注解，自动生成 `NoteDao_Impl`。SQL 语法错（比如表名拼错）直接编译失败。

### 12.3.2 DataStore：声明 + 读 + 写

```kotlin
// 1️⃣ 声明一个 DataStore 文件（每个文件存一组键值对）
private val Context.dataStore by preferencesDataStore("settings")

private val THEME_KEY = stringPreferencesKey("theme")

// 2️⃣ 读：把 Flow 转成 StateFlow，UI 直接 collect
val theme: StateFlow<String> = context.dataStore.data
  .map { it[THEME_KEY] ?: "system" }
  .stateIn(scope, SharingStarted.Eagerly, "system")

// 3️⃣ 写：suspend 函数 + edit { }
suspend fun setTheme(value: String) {
  context.dataStore.edit { it[THEME_KEY] = value }
}
```

`map { }` 把 `Flow<Preferences>` 转成 `Flow<String>`，`stateIn` 把冷流转热流（详见第 5 章）。一旦 `edit` 写了新值，所有订阅 `theme` 的地方会立刻收到新值。

### 12.3.3 Gson 自定义 `TypeAdapter`

```kotlin
// 假设 Shape 有 Circle / Square 两个子类，靠 "kind" 字段区分
class ShapeTypeAdapter : TypeAdapter<Shape>() {
  override fun write(out: JsonWriter, value: Shape?) {
    out.beginObject()
    when (value) {
      is Shape.Circle -> { out.name("kind").value("circle"); out.name("r").value(value.r) }
      is Shape.Square -> { out.name("kind").value("square"); out.name("w").value(value.w) }
    }
    out.endObject()
  }

  override fun read(reader: JsonReader): Shape? {
    reader.beginObject()
    val kind = if (reader.nextName() == "kind") reader.nextString() else return null
    val result = when (kind) {
      "circle" -> Shape.Circle(reader.nextInt())      // 读 r
      "square" -> Shape.Square(reader.nextInt())      // 读 w
      else -> null
    }
    reader.endObject()
    return result
  }
}
```

注册到 `GsonBuilder().registerTypeAdapter(Shape::class.java, ShapeTypeAdapter())` 之后，Gson 遇到 `Shape` 类型就走你写的逻辑。

---

## 12.4 项目中怎么用

本节是全章重点，按三个工具分三块。每块给出项目的真实文件 + 简化代码。

### 12.4.1 Room：三个数据库 + Entity + DAO

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/db/JBusDatabase.kt:18` 与 `data/db/CollectDatabase.kt:20`

```kotlin
// 历史 DB：单表，版本 1
@Database(entities = [History::class], version = 1, exportSchema = true)
abstract class JBusDatabase : RoomDatabase() {
  abstract fun historyDao(): HistoryDao
}

// 收藏 DB：双表（分类 + 收藏项），版本 2
@Database(entities = [Category::class, LinkItem::class], version = 2, exportSchema = true)
abstract class CollectDatabase : RoomDatabase() {
  abstract fun categoryDao(): CategoryDao
  abstract fun linkItemDao(): LinkItemDao
}
```

关键点：

- **项目有多个独立数据库**——历史、收藏、本地视频各自独立的 entity、DAO、版本号。分库的好处是耦合低：改收藏 schema 不会动到历史。
- `exportSchema = true` 让 Room 把 schema JSON 导出到 `app/schemas/`，用于后续写 migration。
- 这些 `RoomDatabase` 抽象类**由 `DatabaseModule` 通过 `@Provides` 提供实例**（回扣第 4 章 §4.4.3——Room 不能用 `@Binds`，因为它有构造逻辑 `Room.databaseBuilder(...).build()`）。

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/db/entity/LinkItem.kt:19`

```kotlin
@Entity(
  tableName = "t_link",
  indices = [Index(value = ["dbType", "key"], unique = true)]   // 复合唯一索引
)
data class LinkItem(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  @ColumnInfo(name = "categoryId") val categoryId: Int = -1,
  @ColumnInfo(name = "dbType") val dbType: Int,        // MOVIE / ACTRESS / GENRE ...
  @ColumnInfo(name = "createTime") val createTime: Long = 0,
  val key: String,                                     // 业务 key（URL 路径）
  @ColumnInfo(name = "jsonStr") val jsonStr: String    // 完整对象的 JSON（Gson 序列化后存）
)
```

关键点：

- **复合唯一索引** `["dbType", "key"]` 保证同一类型下同一 key 不会重复——同一部影片不会被收藏两次。这是数据库层兜底，业务层就算忘了判重也不会出问题。
- `jsonStr` 字段把完整对象（`Movie` / `Actress`）JSON 化后整段塞进去——避免每次业务模型加字段都改表 schema。`jsonStr` 就是 Gson 的产物，§12.4.3 会展开。

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/db/dao/CategoryDao.kt:17` 与 `HistoryDao.kt:16`

```kotlin
@Dao
interface CategoryDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(category: Category): Long              // suspend 写

  @Query("SELECT * FROM t_category WHERE tree LIKE :like ORDER BY sort_order DESC")
  fun queryTreeByLike(like: String): Flow<List<Category>>   // Flow 读，表变自动重发

  @Query("DELETE FROM t_category WHERE id = :id")
  suspend fun delete(id: Int): Int                           // suspend 删
}

@Dao
interface HistoryDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(history: History): Long

  @Query("SELECT * FROM t_history ORDER BY id DESC LIMIT :size OFFSET :offset")
  fun queryByLimit(size: Int, offset: Int): Flow<List<History>>   // 分页 + Flow
}
```

关键点（项目的硬规则）：

- **写操作一律 `suspend fun`**——异步不阻塞主线程。
- **读操作一律返回 `Flow<List<T>>`**——表一变，Flow 自动发新数据。ViewModel 把它转成 `StateFlow` 后 UI 自动刷新（结合第 5 章）。
- **不需要手动调 `refresh()`**——Room 帮你做。用户加一条收藏，收藏列表立刻多一条，全靠 Flow 自动响应。
- `@Insert(onConflict = IGNORE)` 配合 `@PrimaryKey` 和复合唯一索引——重复插入不会崩、不会留重复行，安全。

### 12.4.2 DataStore：设置项存储

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt:71`

```kotlin
private val Context.appSettingsDataStore by preferencesDataStore("app_settings")

@Singleton
class AppSettingsStore @Inject constructor(
  @ApplicationContext private val context: Context,
  private val mirrorScanner: MirrorScanner
) : AppSettingsContract {

  private val dataStore = context.appSettingsDataStore
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  // 读：把 Preferences Flow → 想要类型的 Flow → StateFlow
  private fun <T> flowOf(key: Preferences.Key<T>, default: T): StateFlow<T> =
    dataStore.data.map { it[key] ?: default }
      .stateIn(scope, SharingStarted.Eagerly, default)

  // 写：suspend + edit { }
  private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
    dataStore.edit { it[key] = value }
  }

  // 暴露给 UI 的设置项
  override val selectedBaseUrl: StateFlow<String> =
    flowOf(KEY_SELECTED_BASE_URL, DEFAULT_SITE_URL)

  override suspend fun selectUrl(url: String) =
    put(KEY_SELECTED_BASE_URL, url.trimEnd('/'))
}
```

关键点：

- **每个 Store 一个独立 `preferencesDataStore` 文件**——`AppSettingsStore` 用 `"app_settings"`、`UiPrefsStore` 用 `"ui_prefs"`，按职责拆分。一个文件塞所有键会变成巨型全局变量。
- **读用 `flowOf(key, default)` 帮助方法**——内部 `dataStore.data.map { it[key] ?: default }.stateIn(scope, Eagerly, default)`。`Eagerly` 是因为设置项是 App 全局状态、需要立刻有值（用户随时可能读，不能等 UI 订阅才启动）。
- **写用 `suspend fun` + `edit { }`**——`edit` 是原子的（内部用 `Mutex` 保证并发安全），不在主线程调用，不会 ANR。
- **`AppSettingsContract` 是窄接口**——Store 实现它，UI 通过接口注入而不是直接依赖 Store 类（依赖倒置，方便单测换 Fake）。

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/data/settings/UiPrefsStore.kt:18` —— UI 偏好（列表排序方式）同样的模式：`movieSortOption` / `actressSortOption` 两个 `StateFlow<String>` + 一个 `suspend fun setSortOption(...)`。读 `Flow.map.stateIn`、写 `edit { }`。

### 12.4.3 Gson：多态序列化

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/core/GsonExt.kt:32`

```kotlin
val GSON by lazy {
  GsonBuilder()
    .excludeFieldsWithModifiers(TRANSIENT, STATIC)         // 排除 transient/static（配合 Room @Transient）
    .registerTypeAdapter(Int::class.java, intDeserializer) // Int 空安全（空 JSON 返回 null 不崩）
    .registerTypeAdapter(Date::class.java, dateDeserializer)
    .registerTypeAdapterFactory(NullSafeFactory)           // Kotlin 非空集合字段 null → 空集合兜底
    .registerTypeAdapterFactory(ContentBlockAdapterFactory)// ← 多态工厂（ContentBlock）
    .serializeNulls()
    .create()
}
```

关键点：

- **全局单例 `GSON`**——全项目共用一个 Gson 实例（构造一次，注册完所有 adapter）。
- **`excludeFieldsWithModifiers(TRANSIENT, STATIC)`**——和 Room 配合：Room 实体里标了 `@Transient` 的字段（不存数据库）也不会被 Gson 序列化到 `jsonStr`，避免字段重复。
- **`NullSafeFactory`**——Gson 通过反射绕过 Kotlin 构造函数，会把非空集合字段（如 `val tags: List<String>`）写成 `null`，调用方一访问就 NPE。这个工厂在反序列化后把 null 集合替换成空集合，是项目踩过坑后加的兜底。
- **`ContentBlockAdapterFactory`**——多态序列化的核心，下面展开。

📁 项目对应位置：`app/src/main/java/me/jbusdriver/modern/core/serialization/ContentBlockJsonAdapter.kt:20`

```kotlin
class ContentBlockTypeAdapter(private val gson: Gson) : TypeAdapter<ContentBlock>() {

  override fun write(out: JsonWriter, value: ContentBlock?) {
    val json = JsonObject()
    when (value) {
      is ContentBlock.RichText -> {
        json.addProperty("type", "richtext")              // 写判别字段
        json.add("paragraphs", gson.toJsonTree(value.paragraphs))  // 再写子类特有字段
      }
      is ContentBlock.Image -> {
        json.addProperty("type", "image")
        json.addProperty("url", value.url)
        if (value.width > 0) json.addProperty("width", value.width)
      }
      // ... ListBlock / Quote / RestrictedNotice 同理
    }
    gson.toJson(json, out)
  }

  override fun read(reader: JsonReader): ContentBlock? {
    val json = JsonParser.parseReader(reader).asJsonObject
    return when (json.string("type")) {                   // 读判别字段
      "richtext"   -> ContentBlock.RichText(gson.fromJson(json["paragraphs"], ...))
      "image"      -> ContentBlock.Image(url = json.string("url"), width = json.int("width"), ...)
      "list"       -> ContentBlock.ListBlock(...)
      "quote"      -> ContentBlock.Quote(...)
      "restricted" -> ContentBlock.RestrictedNotice(...)
      else -> null
    }
  }
}

object ContentBlockAdapterFactory : TypeAdapterFactory {
  override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? =
    if (ContentBlock::class.java.isAssignableFrom(type.rawType))
      @Suppress("UNCHECKED_CAST")
      ContentBlockTypeAdapter(gson) as TypeAdapter<T>
    else null
}
```

为什么需要这套东西？因为论坛帖子里一段内容可能是：一段富文本（`richtext`）、一组图片（`image`）、一个引用块（`quote`）、一组列表项（`list`）、或一段"需要登录才能看"的提示（`restricted`）。HTML 解析后产出的是一个 `List<ContentBlock>`，5 种子类字段完全不同。**默认 Gson 看到 `ContentBlock` 这个 sealed 父类型，根本不知道该造哪个子类**——必须靠自定义 Adapter 先读 `"type"` 字段再分支。

📁 项目对应位置：缓存的 `CacheEnvelope`（第 8 章 SWR）落盘时也用这个全局 `GSON.toJson(...)` 序列化；读回来用 `GSON.fromJson<CacheEnvelope<T>>(json)`。**凡是项目里需要"对象 ↔ 字节"互相转换的地方，都走这一个 `GSON` 单例**。

---

## 12.5 常见误区与调试技巧

新手在持久化 / 序列化上最容易栽这五个坑。这五条全部对应 `AGENTS.md` 里的明确规则，是项目的硬约束。

### 误区 1：R8 / Gson 改动后没跑 release smoke test

**症状**：debug 构建跑得好好的，发 release 包到用户手机上，反序列化时崩 `JsonSyntaxException` 或字段全是 null。

**原因**：Gson 用**反射读字段名**。release 构建开启 R8 / ProGuard 后，类名和字段名会被混淆成 `a`、`b`、`c`——`MovieDetail.title` 字段在 release 里可能叫 `a`，Gson 拿着 JSON 里的 `"title"` 找字段，找不到就只能给 null。debug 不混淆所以测不出来。

**怎么修**：凡是新增 Gson 模型或改了字段，**一定要跑一次 release 构建**，用真实的 JSON payload 测一遍反序列化（详见附录 A1 的 ProGuard 规则）。

### 误区 2：新增 Gson 模型忘了加 ProGuard keep

**症状**：release 包里某些模型字段反序列化成 null，但其他模型正常。

**怎么修**：所有会被 Gson 序列化的模型类（domain model、CacheEnvelope、ContentBlock 子类等）**必须加 ProGuard keep 规则**：

```proguard
-keep class me.jbusdriver.modern.domain.model.** { !static !transient <fields>; }
-keep @androidx.annotation.Keep class * { <fields>; }
```

或者直接在类上标 `@Keep`。项目的 `AGENTS.md` 写得明白——"ProGuard/R8 keep rules must cover all Gson model classes; add `@Keep` or rules proactively"。

### 误区 3：删除/重命名字段没加 `@SerializedName` 别名

**症状**：把 `MovieDetail` 的 `movieName` 字段重命名成 `title`，老用户升级后**之前缓存的详情页**反序列化回来，`title` 字段是空——因为缓存 JSON 里写的是 `"movieName": "..."`。

**怎么修**：保留老字段名做 `alternate`：

```kotlin
data class MovieDetail(
  @SerializedName(value = "title", alternate = ["movieName"])
  val title: String
)
```

Gson 会优先读 `title`，找不到再尝试 `movieName`。这样老缓存读得回来，新写入用新字段名。**项目硬规则**：删/重命名 Gson 字段必须加别名，否则老缓存读不回来（用户感知就是"收藏突然空了"）。

### 误区 4：DataStore 从主线程写

**症状**：在 Composable 里 `scope.launch { dataStore.edit { ... } }` 或者直接同步访问，崩 `IllegalStateException` 或 ANR。

**原因**：DataStore 的 `edit` 是 `suspend` 函数，**只能在协程或另一个 `suspend` 里调**。在主线程直接调会阻塞磁盘 IO，ANR。读端也一样——`dataStore.data.first()` 是 suspend，不能在 Composable 里直接同步取（要用 `collectAsStateWithLifecycle` 订阅）。

**正确做法**：所有写操作包在 Store 的 `suspend fun` 里，调用方在自己的协程（`viewModelScope.launch`）里调：

```kotlin
// ✅ Store 暴露 suspend 写方法
suspend fun setTheme(value: String) = dataStore.edit { it[KEY] = value }

// ✅ VM 在协程里调
viewModelScope.launch { settingsStore.setTheme("dark") }
```

### 误区 5：Room 查询返回 Flow 还手动 refresh

**症状**：写完 `dao.insert(item)` 后又调了一次 `loadList()` 重新查——代码冗余，列表还可能闪一下。

**为什么不需要**：Room 的 `@Query` 返回 `Flow<List<T>>` 是**响应式的**——表里任何数据变化（insert / update / delete）都会让 Flow 自动发新值。**手动 refresh 不仅多余，还会触发额外的 SQL 查询浪费性能**。

**正确做法**：VM 里把 DAO 的 Flow 直接 `stateIn` 转成 `StateFlow`，UI 订阅即可。写操作只管写，读自动更新。

```kotlin
// VM：DAO Flow → StateFlow
val categories: StateFlow<List<Category>> =
  categoryDao.queryTreeByLike("$parentId/")
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

// 写：只管 insert，不需要手动 reload
fun addCategory(name: String) {
  viewModelScope.launch { categoryDao.insert(Category(name = name, ...)) }
}
```

> 小技巧：拿不准某个 Flow 是不是真的"自动刷新"时，在 DAO 方法上加断点 / 日志，写一条数据看是不是自动重新查询了一次。如果是，就说明 Flow 工作正常——别再写多余的 reload 调用。

---

## 12.6 小结与下一站

本章把项目里三种"让数据留下来"的机制走了一遍：

- **Room** —— SQLite 的封装。**3 个独立数据库**（历史、收藏、本地视频），每个有 `@Entity` 表 + `@Dao` 接口；**写操作 `suspend`、读操作返回 `Flow<List<T>>`**——表变 Flow 自动发新值，不需要手动 refresh；复合唯一索引兜底去重。
- **DataStore Preferences** —— SharedPreferences 的现代替代。**每个 Store 一个 `preferencesDataStore` 文件**；读用 `data.map { }.stateIn(scope, Eagerly, default)`、写用 `suspend fun + edit { }`；完全异步、不会 ANR。
- **Gson + 自定义 `TypeAdapter`** —— JSON 序列化。**全局 `GSON` 单例**注册多个工厂：`NullSafeFactory` 给 Kotlin 非空集合兜底、`ContentBlockAdapterFactory` 处理多态 sealed 类型（5 个子类靠 `"type"` 字段分支）；缓存的 `CacheEnvelope` 落盘、Entity 的 `jsonStr` 字段都走它。
- **ProGuard / R8 红线**：所有 Gson 模型必须 keep 字段（`@Keep` 或 `-keep ... { <fields>; }`）；删/重命名字段必须加 `@SerializedName(alternate = [...])` 老字段别名；任何 Gson / R8 改动都要跑 release 构建用真实 JSON 测一遍反序列化。

读完本章，你应该能看懂项目里所有 `@Entity` / `@Dao` / `preferencesDataStore` / `TypeAdapter` 出现的地方，并且知道新增一个数据库表 / 设置项 / 缓存模型时该改哪几个文件。

```
下一站：附录 A1 工程实践 —— Gradle 构建变体、ProGuard / R8 规则、单元测试配置、Lint 检查；
        附录 A2 调试与性能 —— LeakCanary、Profiler、网络抓包等排障手段。
```

---

🔍 深入阅读：
- Room 官方文档：https://developer.android.com/training/data-storage/room
- DataStore 官方文档：https://developer.android.com/topic/libraries/architecture/datastore
- Gson 用户指南：https://github.com/google/gson
- 项目所有数据库：`app/src/main/java/me/jbusdriver/modern/data/db/`
- 项目所有 Store：`app/src/main/java/me/jbusdriver/modern/data/settings/`
- 全局 Gson 配置：`app/src/main/java/me/jbusdriver/modern/core/GsonExt.kt`、`core/serialization/ContentBlockJsonAdapter.kt`
