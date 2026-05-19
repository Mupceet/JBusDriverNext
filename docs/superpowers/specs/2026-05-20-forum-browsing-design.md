# 论坛浏览功能设计

## 概述

在 JBus App 中集成论坛（老司機論壇）的浏览功能。采用纯 HTML 解析方式，复用现有 NetClient + Jsoup + CacheLoader 架构。仅实现只读浏览，不涉及登录、发帖、回复等操作。

## 导航与页面结构

底部导航新增第4个 Tab「论坛」，与影片/演员/收藏并列。

3层页面导航：

| 页面 | Route | URL |
|------|-------|-----|
| 论坛首页 | `RouteForum` | `{baseUrl}/forum/forum.php` |
| 帖子列表 | `RouteForumThreadList(fid)` | `{baseUrl}/forum/forum.php?mod=forumdisplay&fid={fid}&page={page}&filter=typeid&typeid={typeid}` |
| 帖子详情 | `RouteForumThreadDetail(tid)` | `{baseUrl}/forum/forum.php?mod=viewthread&tid={tid}&page={page}` |

## 数据模型

```
ForumBoard — 板块（首页卡片）
  id: Int              — fid (2=福利討論區, 36=求福利帶帶我, 37=網站建議)
  name: String
  description: String
  todayPosts: Int
  totalThreads: String — "3萬" / "9萬"
  totalPosts: String
  lastPost: LastPost

LastPost
  title: String
  author: String
  time: String

ForumThread — 帖子（列表项）
  tid: Int
  typeId: Int          — 分类ID (7=交流, 8=日本, 9=韓國, 10=欧美, 11=国产, 12=动漫, 13=性息, 14=字幕)
  typeName: String
  typeColor: String    — "#007E33" 等
  title: String
  author: String
  authorUid: Int
  authorAvatar: String
  dateLine: String
  viewCount: Int
  replyCount: Int
  lastReplyAuthor: String
  lastReplyTime: String
  images: List<String> — 缩略图URL列表
  isPinned: Boolean
  isDigest: Boolean
  pages: Int

ForumThreadDetail — 帖子详情
  tid: Int
  typeId: Int
  typeName: String
  typeColor: String
  title: String
  viewCount: Int
  replyCount: Int
  author: String
  authorUid: Int
  authorAvatar: String
  postTime: String
  content: String             — 正文纯文本
  contentImages: List<String> — 正文图片URL
  comments: List<Comment>     — 点评
  replies: List<ForumReply>
  pageInfo: PageInfo

Comment — 点评
  author: String
  authorAvatar: String
  content: String
  time: String

ForumReply — 回复
  floor: Int
  author: String
  authorUid: Int
  authorAvatar: String
  authorGroup: String — "老司機", "駕校學員"
  content: String     — 纯文本
  contentImages: List<String>
  postTime: String

ForumThreadPageResult
  threads: List<ForumThread>
  pageInfo: PageInfo
  typeFilters: List<ForumTypeFilter>

ForumTypeFilter
  typeId: Int
  name: String
  color: String
  count: Int
```

## HTML 解析

在 `HtmlParser.kt` 中新增三个解析函数：

### parseForumBoards(doc: Document): List<ForumBoard>

- 解析 `div.fl.bm` 下 `div[id^=category_]` 内的 `table.fl_tb > tbody > tr`
- 每行提取：`td.fl_icn a img` → 板块图标, `td h2 a` → 名称和fid, `td p.xg2` → 描述, `td.fl_i span` → 帖子/回复统计, `td.fl_by .forumlist` → 最新帖子
- 板块列表为固定结构：综合交流区（福利討論區、求福利帶帶我、網站建議阿哩哩）

### parseForumThreads(doc: Document): Pair<List<ForumThread>, PageInfo>

- 分类 Tab 从 `ul#thread_types > li > a` 解析，提取 typeid、名称、颜色、帖子数
- 帖子列表从 `#threadlisttableid` 中 `tbody[id^=stickthread_]` 和 `tbody[id^=normalthread_]` 解析
- 每个帖子提取：`div.post_avatar img[src]` → 头像, `.post_infolist_tit em a` → 分类标签, `.post_infolist_tit a.s` → 标题和tid, `.author a` → 作者, `.dateline span` → 时间, `.views` → 浏览数, `.reply` → 回复数, 缩略图从 `.post_infolist_tit img[src]` 提取
- 分页从 `.pg` 区域解析（与现有 `parsePageInfo` 类似）
- 置顶帖从 `tbody[id^=stickthread_]` 识别，精华帖从 `img[alt=recommend]` 识别

### parseForumThreadDetail(doc: Document): ForumThreadDetail

- 标题从 `h1.ts span#thread_subject` 或 `.nthread_info h1.ts` 提取
- 正文从第一个 `.nthread_firstpostbox` 中 `td.t_f` 提取：
  - 纯文本：递归遍历子节点，`<br>` 转换为 `\n`，`<img>` 收集 URL，`<font>` 标签取文本内容
  - 图片：`td.t_f img.zoom[src]` 提取所有图片 URL
- 点评从 `div.cm .pstl` 提取：`.psta img[src]` → 头像, `.psta a.xi2` → 作者, `.psti` 第一个文本节点 → 内容, `.psti .xg1 span` → 时间
- 回复从 `.nthread_postbox` (非 firstpostbox) 中提取：
  - 楼层：`a.postnum_* em` → 楼层号
  - 作者信息：`div.favatar .avatar img[src]` → 头像, `.pls a.xw1` → 作者名
  - 用户组：`.pls em a` → 用户组名
  - 内容：`td.t_f` 同正文的处理方式
  - 时间：`em[id^=authorposton] span[title]` → 时间

## Repository 层

```
ForumRepository (interface)
  suspend fun loadForumBoards(): List<ForumBoard>
  suspend fun loadThreads(fid: Int, page: Int, typeId: Int? = null): ForumThreadPageResult
  suspend fun loadThreadDetail(tid: Int, page: Int = 1): ForumThreadDetail

DefaultForumRepository (@Singleton)
  - loadForumBoards(): CacheLoader.lruCached("forum_boards") { ... }
  - loadThreads(): CacheLoader.lruCached("forum_threads_${fid}_${page}_${typeId}") { ... }
  - loadThreadDetail(): CacheLoader.persistentCached("forum_detail_${tid}_${page}") { ... }
```

URL 拼接：`${NetClient.defaultFastUrl}/forum/forum.php?mod=...`

## ViewModel 层

```
ForumBoardsViewModel (@HiltViewModel)
  - ForumRepository via @Inject
  - uiState: StateFlow<ForumBoardsUiState>
  - loadBoards(), refresh()

ForumThreadListViewModel (@HiltViewModel)
  - ForumRepository via @Inject
  - fid: Int via SavedStateHandle
  - uiState: StateFlow<ForumThreadListUiState>
  - loadFirstPage(), loadMore(), refresh(), filterByType(typeId)

ForumThreadDetailViewModel (@HiltViewModel)
  - ForumRepository via @Inject
  - tid: Int via SavedStateHandle
  - uiState: StateFlow<ForumThreadDetailUiState>
  - loadDetail(), refresh(), loadMoreReplies()
```

### UI State

```kotlin
data class ForumBoardsUiState(
    val boards: List<ForumBoard> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ForumThreadListUiState(
    val threads: List<ForumThread> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val currentTypeId: Int? = null,
    val typeFilters: List<ForumTypeFilter> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

data class ForumThreadDetailUiState(
    val detail: ForumThreadDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

## DI 绑定

`DataModule.kt` 新增：
```kotlin
@Binds @Singleton
abstract fun bindForumRepository(impl: DefaultForumRepository): ForumRepository
```

## UI 组件

### 新增文件

```
ui/forum/
  ForumBoardsScreen.kt       — 论坛首页（板块卡片列表）
  ForumThreadListScreen.kt   — 帖子列表页
  ForumThreadDetailScreen.kt — 帖子详情页
  ForumViewModels.kt         — 3个 ViewModel
  ForumUiState.kt            — UI State 模型
```

### 论坛首页 (ForumBoardsScreen)

- 顶部统计栏：今日/昨日/帖子/会员数
- LazyColumn 板块卡片列表，每张卡片：名称 + 描述 + 今日发帖数 + 最新帖子预览（标题 · 作者 · 时间）
- 点击卡片 → `RouteForumThreadList(fid)`
- PullToRefreshBox 下拉刷新

### 帖子列表 (ForumThreadListScreen)

- TopAppBar：板块名称 + 返回按钮
- 横滑 FilterChip 分类筛选：全部/交流/日本/韓國/欧美/国产/动漫/性息/字幕
- LazyColumn 帖子卡片：
  - 左侧头像（32dp 圆形）
  - 右侧：分类标签（彩色小标签）+ 精华/置顶标记 + 标题 + 缩略图行（最多3张，80dp 宽）+ 底部元信息（作者 · 时间 | 浏览 回复）
- 置顶帖带置顶图标，排在列表顶部
- 分页加载（滑到底部自动加载）
- 点击帖子 → `RouteForumThreadDetail(tid)`
- PullToRefreshBox 下拉刷新

### 帖子详情 (ForumThreadDetailScreen)

- TopAppBar：返回按钮，标题
- 标题区：分类标签 + 标题 + 作者 · 时间 · 浏览数 · 回复数
- 正文区：
  - 纯文本段落，行间距 1.6
  - 图片全宽渲染（Coil），上方 8dp 间距
  - 图片点击跳转 `RouteImageViewer` 放大查看
- 点评区（如有）：标题「點評」+ 横向头像(24dp) + 用户名 + 内容 + 时间
- 回复列表：标题「精彩評論 (N)」+ 每条回复：头像(32dp) + 用户名 · 用户组 · 楼层号 · 时间 + 内容
- 分页加载回复（滑到底部加载下一页）
- 错误状态：错误提示 + 重试按钮

### 图片处理

- 列表页缩略图：Coil 加载，`size(150, 100)` 限制
- 详情页正文图片：Coil 加载原图，`fillMaxWidth()`，保持宽高比
- 头像：复用 Coil 加载 + 圆形裁剪，失败显示默认头像
- 所有图片点击跳转 `RouteImageViewer` 放大查看

### MainScreen Tab 集成

`MainScreen.kt` 底部导航栏新增第4项：
- 图标：`Icons.Outlined.Forum`
- 标签：「论坛」
- 内容：`ForumBoardsScreen`

## 不在范围内

- 登录/注册
- 发帖/回复/点评
- 搜索论坛
- 收藏帖子
- 用户个人主页
- 私信
- 论坛公告详情页（公告仅作为首页展示项）
- 精华/热门/最新排序筛选（帖子列表页仅支持分类 Tab 筛选）
