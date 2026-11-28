# JBusDriver 论坛客户端设计文档 —— Discuz X3.4 对标

**日期**: 2026-11-24
**参考**: 《Discuz X3.4 原生 Android 客户端 技术架构与实施设计》（用户提供；纯客户端适配方案：HTTP / Jsoup / Kotlin / Jetpack Compose，无服务端权限）
**范围**: 论坛功能补充 + 架构设计改进点 + 调研方向；无权限或不具备条件的项目标记为遗留
**代码基线**: main @ aee463b0

---

## 一、背景与调研方法

参考文档的核心主张：在"只能访问论坛网站、不能改服务端"的约束下，以 OkHttp + Jsoup 适配现有 Web 协议；适配层集中（Adapter）；formhash / 隐藏字段 / 表单 action 一律动态提取；写操作走通用 Form Engine；以 PostNode 内容树隔离 Web 结构与 UI；WebView 仅承担验证码等人工流程；先 P0 协议摸底再大规模开发。

本项目的论坛模块已经是一个**成熟的只读客户端**（相当于参考文档 P1 阶段的完成态，部分能力超出其验收标准）。本文档不做从零设计，而是以参考文档为标尺做差距分析，给出下一步改进点。

调研方法（三方交叉）：

1. **参考文档全文研读**（11 章 + 附录，约 650 行）。
2. **代码摸底**：`domain/model/ForumModels.kt`、`data/parser/Forum*Parser.kt`、`data/repository/ForumRepository.kt`、`ui/forum/*`、`core/http/*` 全覆盖盘点。
3. **目标站点实测**（2026-11-24）——即参考文档 P0 协议摸底的第一轮。访问方式约束（须遵守）：**走代理 + 内置浏览器（IAB），先打开主站首页通过门禁（driver-verify/Turnstile），再进入 `/forum/`**。

---

## 二、目标站点协议摸底（P0 第一轮结果）

### 2.1 已验证结论

| # | 项目 | 实测结果 |
|---|------|----------|
| 1 | 论坛版本 | **Discuz! X3.4** 确认（移动版/搜索页页脚 "Powered by Discuz! X3.4, Copyright © 2001-2021, Tencent Cloud"）；桌面模板已定制（JavBus 页脚，版本号被移除） |
| 2 | 入口与路由 | `https://{base}/forum/`，桌面版为标准 Discuz 路由（相对路径）：`forum.php`、`member.php`、`home.php`、`search.php`、`misc.php` |
| 3 | JSON / 移动 API | `api/mobile/index.php?version=4&module=forumindex` 被站点 rewrite 为**触屏版 XHTML 页面**（`text/html`）→ JSON API 通道不存在，**HTML 是唯一数据通道**。触屏版 DOM 更轻，可作为潜在轻量解析通道（调研项 R1） |
| 4 | 登录 | 标准登录表单：`action=member.php?mod=logging&action=login&loginsubmit=yes&loginhash={动态}`；字段 `formhash / referer / loginfield(用户名\|UID) / username / password / questionid+answer(安全提问) / cookietime / seccodeverify(+seccodehash/seccodemodid)`。**seccode 图片验证码必现**（`misc.php?mod=seccode&action=update`），无 Turnstile/reCAPTCHA → 原生登录必须解决 seccode 展示与输入，或走 WebView 辅助登录（见 A2） |
| 5 | formhash | 登录页与帖子页对**游客也下发**（同会话稳定，实测多页一致）；论坛首页不下发。符合参考文档"动态提取、不硬编码"的要求 |
| 6 | 游客搜索 | **允许**：`GET search.php?mod=forum&srchtxt={kw}&searchsubmit=yes` → 302 至 `searchid` 结果页（实测生成 searchid=1987 并正常渲染结果列表/无结果提示） |
| 7 | 帖子页（游客） | 每页约 20 楼（实测 21 个 `post_*` 容器，含评分面板等非楼层节点）；fastpost 回帖表单对游客可见：`action=forum.php?mod=post&action=reply&fid={fid}&tid={tid}&replysubmit=yes&infloat=yes&handlekey=fastpost`，字段 `formhash / usesig / subject / message`。游客提交是否被服务端拒绝**未实测**（避免产生真实写操作） |
| 8 | 版块筛选全集 | `filter=typeid&typeid=` / `filter=sortid` / `filter=digest&digest=1`(精华) / `filter=specialtype&specialtype=reward`(悬赏) + `orderby=lastpost\|heats\|replies\|views\|dateline` + `filter=dateline&dateline=86400/172800/604800/2592000/7948800`(时间范围)。当前 App 已使用 typeid + 5 种 orderby；**digest / dateline 范围 / specialtype 未使用**（见 F1-4） |
| 9 | 其它可见入口 | `forum.php?mod=guide&view=sofa`（新帖导引，标准 X3.4 另有 new/hotdigest 视图）、`forum.php?mod=announcement&id=`（公告）、`home.php?mod=space&uid=`（用户空间）、`home.php?mod=space&do=favorite&view=me`（收藏，需登录） |
| 10 | 站点结构 | 综合交流区：老司機福利討論區 / 求福利帶帶我(fid=36) / 網站建議阿哩哩；会员 63 万+，在线 4 千+ |

### 2.2 待采集项（第一轮后遗留，部分已在第二轮完成）

- **R2**：登录成功后的 Cookie 集与登录态页面识别标志——**已完成（§2.3）**。
- **R3**：回帖/发帖真实提交的响应样本（inajax XML 成败分支）——**部分完成**：showmessage 错误页样本已采；成功响应被站点"头像门槛"阻塞，待账号设置头像后采集。附件上传协议、私信/通知页面——仍遗留。
- **R4**：高级搜索（用户/版块范围/时间条件）——主路径已验证（§2.3-5），高级条件未展开。

> 以上结论已按参考文档附录 A 的要求"实测化"；后续每轮协议变化应更新本文档第二章（即 A6 协议档案）。

### 2.3 第二轮实测结果（登录态，2026-11-24）

实测条件：内置浏览器内人工完成登录（含 seccode），同会话内同源 fetch 采样；样本**内存中脱敏后**存入 `app/src/test/resources/forum/fixtures/`（14 份，清单与脱敏规则见该目录 README.md）。

1. **会话与 Cookie（R2 完成）**：登录态 Cookie 为 `4fJN_2132_auth`（JS 可读、非 HttpOnly）与主站 `bus_auth`；游客会话另含 `sid/saltkey/lastvisit/lastact/lip/visitedfid/st_p/st_t` 等。**无 Cookie 裸请求**（forumdisplay/viewthread/forum.php）一律重定向到登录页——游客可读依赖门禁通过后的游客会话（`age`+`saltkey`），印证 `SessionCookieStore` 有效性判定语义。
2. **登录态识别（R2 完成）**：`a[href*="action=logout"]`（`member.php?mod=logging&action=logout&formhash=…`）仅登录态出现；`guide&view=my`（我的帖子）链接同。游客/登录两版 `forum.php` 已存 fixture 对照（`forum_index_logged_in.html` vs `forum_index_nocookie_login_redirect.html`）。
3. **写路径硬边界（R3 部分完成）**：站点要求**已设置头像才能回帖/发帖**——未设头像账号 GET 完整编辑器即返回标准 Discuz showmessage 错误页（`alert_error` 结构，已存 fixture `reply_editor_179228_logged_in.html`）；fastpost 区域渲染"您現在無權發帖"。
4. **fastpost 为运行时表单（R3 关键发现）**：静态 HTML 仅含 `formhash/usesig/subject` 隐藏字段与 submit；`message` 编辑器由 JS 按权限注入，`seccheck_fastpost`（验证码槽位）初始为空。**结论：写操作必须在共享 WebView 运行时会话中构造与提交（A3 据此修正，决策 D7），裸 OkHttp 拼 POST 不可行。**
5. **其它入口确认**：`guide&view=my` = 我的帖子（空态已采）；收藏页 `home.php?mod=space&do=favorite&view=me`（空态已采）；公开用户空间可访问（他人 uid 样本已存，F1-7 可行）；搜索登录态正常（"分享"结果页已采，F1-1 解析目标确定）；公告详情页结构已采（F1-3）。
6. **触屏版样本已存**（`wap_index.html`，6.9KB）供 R1 研究轻量通道。

### 2.4 第三轮实测结果（写权限确认，2026-11-24 晚）

1. **等级制度（置顶帖 tid=15172，已存 fixture）**：站点实行人工审核等级制——**路人乘客（新注册）：不能发帖也不能回帖**；**駕校學員：可回帖不可发帖**；**實習司機起：可正常回帖与发帖**；老司機等可用签名。审核周期以年计（活跃度人工审核）。
2. **回帖权限链实测**：设置头像后"需设置头像"门槛即过，错误变为"您沒有權限在該版塊回帖，具體原因請點此查看置頂帖"（fid=2 与 fid=36 均拒）——即当前账号处于路人乘客级。**F2-1 回帖对当前测试账号不可实测**；协议层（fastpost action/字段/运行时注入机制）已摸清，待账号升级后可直接开发与采样。
3. **收藏协议（已验证）**：表单 `POST home.php?mod=spacecp&ac=favorite&type=thread&id={tid}&spaceuid=0`，字段 `formhash / favoritesubmit / description / referer`（fixture `favorite_form_thread.html`）。用户手动收藏一帖后"我的收藏"出现新行（行 = 刪除链接 + 主题标题 + 时间，fixture `favorites_logged_in_nonempty.html`）；帖子页按钮文案 `收藏{N}` 带计数。
4. **点赞/推荐协议（已验证）**：`GET forum.php?mod=misc&action=recommend&do=add&tid={tid}&hash={per-thread 令牌}`——hash 从帖子页链接提取；**GET 即生效**。用户手动点赞后帖子页按钮文案 `点贊27`（计数已含本次）；点赞后链接仍渲染（重复点赞由服务端拒绝）。权限表另有"允許參與評分/點評"项（当前组具体值未取到）。
5. **用户组权限项全集**已从 `spacecp&ac=usergroup` 页面确认（發新話題/發表回復/發起投票/評分/點評/附件上传等 47 项）。

### 2.5 游客态全量验证（第四轮，2026-11-24 晚）

方法：同一内置浏览器临时摘除 auth cookie（值暂存 localStorage、恢复后清除，未经对话传输），按标准流程**先过主站首页门禁**再走查论坛全部界面；真游客版 fixture 已入库（此前仅有无 Cookie 重定向版）。游客态判定标志：`discuz_uid=0`、无 `action=logout`、无 `guide&view=my`。

**能力矩阵（未登录 vs 已登录·路人乘客级）**：

| 能力 | 未登录（游客） | 已登录（路人乘客级） | 依据 |
|---|---|---|---|
| 浏览：首页 / 版块列表 / 主题列表 | ✅ | ✅ | 游客 fixture 全 200 |
| 帖子详情（楼层/引用/图片/点评查看） | ✅ | ✅ | `thread_179228_guest` |
| 论坛搜索 | ✅ | ✅ | 两态 searchid 实测 |
| 公开用户空间（他人主页） | ✅ | ✅ | uid=662631 两态 |
| 公告 | ✅ | ✅ | id=2 两态 |
| 导读公开视图（搶沙發等） | ✅ | ✅ | `guide_sofa_guest` |
| WAP 触屏版 | ✅ | ✅ | 两态 |
| 无 Cookie 裸访问（任何页面） | ❌ 一律重定向登录页 | — | `*_nocookie_login_redirect` |
| 导读-我的帖子（`guide&view=my`） | ❌ 提示页 | ✅ | 游客"提示信息"页 |
| 我的收藏列表（`space&do=favorite`） | ❌ 提示页 | ✅ | 同上 |
| 收藏按钮（渲染） | ✅ 按钮+计数（`收藏{N}`），操作需登录 | ✅ 已验证可用 | §2.4-3 |
| 点赞按钮（渲染） | ✅ 按钮+计数（`点贊{N}`），操作需登录 | ✅ 已验证可用 | §2.4-4 |
| fastpost 回帖框 | 显示"请先登录"类提示 | 显示"您現在無權發帖"（等级） | 两态文案不同 |
| 回帖 / 引用回复 | ❌ | ❌（等级制阻塞，§2.4-2） | |
| 发新主题 | ❌ | ❌（需實習司機级） | §2.4-1 |
| 私信（`spacecp&ac=pm`） | ❌ 提示页 | 入口存在（未采样，隐私遗留） | `pm_page_guest` |
| 评分 / 感谢 | 未渲染 | 未渲染（当前组无权限） | 权限表 + 页面 |
| 登录页访问 | ✅ | — | `login_page_guest` |

对 App 的直接含义：① F1 只读能力全部游客可用，现有匿名模式无损覆盖；② 收藏/点赞按钮可游客态渲染（自带计数），点击时引导登录（A2）；③ fastpost 两态文案不同，`ResponseInterpreter` 需区分"未登录"与"等级不足"两种提示。

### 2.6 用户组等级体系全量实测（第五轮，2026-11-27）

数据源：`home.php?mod=spacecp&ac=usergroup&gid=N` ×8（fixtures `forum_usergroup_gid*.html`）+ 置顶帖 tid=15172。页面结构：标签表 + 两张值表（我的主用户组 / 晋级用户组），布尔权限 = `data_valid.gif`/`data_invalid.gif` 图标；无 gid 的本人页是运行时权限解析目标（`forum_usergroup_self.html`）。

**等级阶梯（8 个普通用户组，gid 与里程下限实测）**：

| gid | 组名 | 里程下限 | 星级 | 关键变化（相对前一级） |
|-----|------|---------|------|----------------------|
| 21 | 路人乘客 | 0 | — | 新注册组：**不可回帖、不可发帖**，不可发短消息/加好友/点评 |
| 10 | 駕校學員 | 200 | ★ | **可回帖**、可发短消息、可点评（发帖仍不可） |
| 11 | 實習司機 | 400 | ★★ | **可发帖**、可加好友、可打招呼/表态/留言、可设回帖奖励 |
| 12 | 新手上路 | 700 | ★★ | 无实质变化 |
| 13 | 普通司機 | 1000 | ★★★ | 单附件上限 1000KB→1.95MB（上传仍全局禁用，无实效） |
| 14 | 老司機 | 1500 | ★★★ | **签名 300 字节**、自定义头衔、附件权限设置 |
| 15 | 飆車黨 | 3000 | ★★★ | **隐身** |
| 22 | 車神 | 6000 | ★★★ | 同飆車黨 |

特殊组（不参与阶梯）：限制會員 gid9；禁止發言 4 / 禁止訪問 5 / 禁止 IP 6 / 遊客 7 / 等待驗證 8 / QQ遊客 20；管理组 1 管理員 / 2 超級版主 / 3 版主 / 16 實習版主 / 17 網站編輯 / 18 信息監察員 / 19 審核員。

**全组恒定项**（与等级无关）：閱讀權限按级递增 10/10/20/30/50/70/90/90；下載附件、查看圖片全组允许；上傳附件/圖片、評分、標籤、多媒體代碼、發帖不受限制全组禁止；@上限 3、淘專輯 5；投票/悬赏/活动/辩论的组权限虽 ✓，但被「發新話題」门控，实效 = 實習司機起可用。阅读权限低于版块/主题要求的组，服务端直接不渲染对应内容，App 无需客户端门控。

**升级机制（置顶帖原文要点）**：升级靠**人工审核活跃度/回帖质量**，里程只是参考线（路人乘客→駕校學員"約兩年"）；灌水回帖会被删号、发帖质量差会被**降级回**路人乘客。故 App 不得按里程自动推断等级变化，只能以服务端页面为准。

**里程获取规则（站方积分策略）**：每天登錄 +1（1/天）；設置頭像 +1、郵箱認證 +1（各一次性）；發表回復 +1/+1（3/天）；發表主題 +10/+10（1/天）；加精華 +50/+50（不限）；發短消息 0/-30（不限）。满打满算日上限 +14（登錄1 + 回復3 + 主題10）。

**对 App 的含义（等级门控，已定案：随 F2 写功能一并实现）**：入口按权限隐藏——回帖入口需駕校學員+、发帖入口需實習司機+、短消息需駕校學員+、加好友需實習司機+、点评需駕校學員+；收藏/点赞与等级无关（登录即可，已实测）。运行时权限取自本人 usergroup 页（解析标签/值表对齐），配合登录态刷新；权限未知时显示入口（服务端错误提示兜底）。不按里程推断等级（人工审核制）。

---

## 三、现状对标（参考文档 ↔ 本项目）

| 参考文档章节 | 本项目现状 | 评价 |
|---|---|---|
| 2 总体架构（多 Gradle module 分层） | 单 module 分包 `core/data/domain/ui` + Hilt DI | 结构等价；**不迁移多 module**（决策 D2，见 A7） |
| 2.1 技术选型 | Compose/OkHttp/Jsoup/Coil/Room/DataStore/Coroutines 全吻合；Paging3 未用 | 吻合；自研 SWR 分页已是项目特色，不引入 Paging3（D1） |
| 2.2 双通道（JSON API + HTML） | HTML 单通道；JSON API 实测不存在（§2.1-3） | 与参考文档的告诫完全一致；WAP 触屏版作为潜在第二通道（R1） |
| 3 协议适配集中 | 解析集中于 `data/parser/`；URL 构造在 `ForumThreadOrder`/`ForumFloorOrder` | 基本达标；**缺 Form 层（A1）与错误解释器（A3）** |
| 3.2 登录与 Session | 无登录；游客 cookie 持久化（saltkey/sid/age，`SessionCookieStore`） | 缺口 → F2 / A2 |
| 3.3 Form Engine | 无（formhash 从未被读取） | 缺口 → A1 |
| 4 HTTP/安全 | `CookieManagerCookieJar` 单一事实源（WebView 写、OkHttp 读）；隐藏 WebView 自动过 driver-verify 门禁 | 达标且有特色；写操作安全（防重复提交/凭据保护）未涉及 → A2/A3 |
| 5.1 Parser 分层 | `ForumHomeParser / ForumThreadParser / ForumPostParser / InlineParagraphParser` 按页面/结构分文件 | 达标；关键容器缺失时返回空列表而非可诊断失败 → A5 |
| 5.2 领域模型 | `ForumModels.kt` 覆盖 Board/Thread/Reply/Comment 等；缺 Attachment/Poll/User/PM 模型 | 读路径达标 |
| 5.3 PostNode 内容树 | `ContentBlock`（RichText/ListBlock/Image/Quote/RestrictedNotice）+ `TextPart` 对应参考 PostNode/InlineNode；Gson 多态适配器含旧格式兼容 | **达标且更细**（颜色/字号/内联表情/引用带 quotedPid）；缺 CodeBlock/附件/剧透/投票节点 → F1-5 |
| 6 页面与交互 | 首页/列表/详情齐备；楼层定位与缺口回填、GIF 节流、链接域内路由（影片/演员/磁链）为参考文档没有的能力 | **超出** |
| 6.1 信息架构（论坛/消息/我的 三 tab） | 论坛是影片 App 的一个 bottom tab（`showForumTab` 默认关） | 产品定位不同，参考文档 IA 不适用；功能按需融入现有导航 |
| 7 写操作（发帖/回复/附件/私信） | 无任何写操作 | 缺口 → F2/F3 |
| 9 阶段计划 | P0 第一轮完成（本文 §2）；P1 只读已达且超出 | — |
| 10 测试 | parser/reducer/VM 测试齐备，但 fixture 为手工构造样本；无"真实页面脱敏样本 + 结构断言"回归体系 | 部分 → A5 |
| 11 风险 | 桌面模板已定制（JavBus 页脚、版本号隐藏）→ selector 必须以实测为准 | 已在风险表复述 |

---

## 四、论坛功能补充（改进点）

分三级：**F1** 游客态即可做（纯读路径）；**F2** 依赖登录生态；**F3** 无权限遗留。

### F1 游客态增强（无新权限，建议优先）

| 编号 | 功能 | 要点 | 涉及 |
|---|---|---|---|
| F1-1 | **论坛搜索**（价值最高） | 入口：论坛首页搜索图标 → 复用现有搜索交互模式；协议：`GET search.php?mod=forum&srchtxt={kw}&searchsubmit=yes` → 302 `searchid` 结果页（已实测游客可用）。注意：① searchid 是服务端搜索缓存，短时间内翻页有效，缓存 key 应含关键词；② Discuz 有搜索频率限制（防刷），需节流 + 429/冷却提示；③ 结果列表结构需采一份真实 fixture | 新 Parser + Repository 方法 + 新 Screen/VM |
| F1-2 | 新帖导引 | `forum.php?mod=guide&view=sofa|new|hotdigest` 聚合"最新发布/最新回复/热门/精华"，复用 `ThreadCard` 与现有列表 SWR 骨架 | Parser + 列表复用 |
| F1-3 | 公告 | 首页公告条 + `forum.php?mod=announcement&id=` 详情页 | Parser + UI |
| F1-4 | 版块筛选补全 | 精华 `digest=1`、时间范围 `filter=dateline&dateline=`（今天/本周/本月）、悬赏 `specialtype=reward`；挂到现有 FilterChip 行与 `ThreadSortMenu` | `ForumThreadOrder`/URL 构造 + 缓存 key 扩展 |
| F1-5 | 内容节点补齐 | `div.blockcode` 代码块（含复制按钮）、附件 `dl.tattl`（大小/下载，下载走系统浏览器）、剧透 spoiler、投票只读渲染；`ContentBlock` 扩展 + Gson adapter 新类型 tag + 旧缓存兼容（沿用现有 legacy-tag 读取模式） | Parser + 模型 + 渲染 |
| F1-6 | 签名开关 | 当前 `.sign` 一律丢弃（`InlineStyle.kt`）；加设置项"显示签名"（默认关，保持现状） | Parser + Settings |
| F1-7 | 用户空间只读页 | `home.php?mod=space&uid=` → 头像/用户组/TA 的主题；作者名可点进入。游客态已验证可见（§2.5），登录/游客两版 fixture 已存 | 新 Parser/Screen |

### F2 依赖登录生态（前置：A2 认证层）

| 编号 | 功能 | 要点 |
|---|---|---|
| F2-1 | **回帖** | 协议已摸清（§2.1-7 / §2.3-4）：`POST forum.php?mod=post&action=reply&...&replysubmit=yes&infloat=yes&handlekey=fastpost`；`message` 编辑器为运行时注入，须走共享 WebView 会话提交（A3/D7）。**阻塞：站点等级制——路人乘客不可回帖（§2.4），当前账号待人工审核升级后才能开发实测** |
| F2-2 | 引用回复 | 按目标站点实际要求生成 quote 标记/隐藏字段（参考文档 7.2：不要只在 UI 显示引用而未提交）。与 F2-1 同被等级制阻塞 |
| F2-3 | **收藏** | 协议已验证（§2.4-3）：`POST spacecp&ac=favorite&type=thread&id={tid}`，字段 formhash/favoritesubmit/description/referer；"我的收藏"页 `space&do=favorite&view=me`（非空 fixture 已存）。**可直接开发** |
| F2-4 | **点赞/推荐** | 协议已验证（§2.4-4）：`GET misc&action=recommend&do=add&tid={tid}&hash={令牌}`（GET 即生效）；帖子页按钮 `点贊{N}` 带计数。**可直接开发**（注意防重复触发） |

未登录时点按写操作入口 → 引导登录（A2），不做静默失败。

### F3 无权限遗留（暂不实施，记录触发条件）

| 编号 | 功能 | 遗留原因 / 触发条件 |
|---|---|---|
| F3-1 | 新主题/编辑/删除 | 站点等级制：需**實習司機**级（人工审核）才能发帖（§2.4-1）；且 App 定位为阅读器，优先级最低 |
| F3-2 | 私信子系统 | 隐私敏感 + 协议未采集；参考文档 7.4 要求独立域单独设计，成本高（触发：用户明确提出且提供测试账号） |
| F3-3 | 通知/提醒 | 需登录（`home.php?mod=space&do=notice`） |
| F3-4 | 附件上传 | 上传协议未采集；无发帖场景则无需求 |
| F3-5 | 评分/感谢 | 游客不可见协议（`post_rate_div` 目前仅用于 pid 识别） |
| F3-6 | 注册 | 不在 App 内做（体验+合规） |

---

## 五、架构设计改进点

### A1 Form Engine（动态表单解析）
参考文档 3.3。新增 `data/parser/FormParser.kt` + 领域模型：

```kotlin
data class ParsedForm(
    val action: String,
    val method: String,
    val hiddenFields: Map<String, String>,   // formhash/loginhash/referer...
    val fields: List<FormField>,
    val submitFields: Map<String, String>,
    val enctype: String? = null,
)
```

职责：从页面提取 form action/隐藏字段/输入字段/submit 值；提交时"选对 form、合并用户输入"，不无条件提交全字段。同时把 **formhash 提取**固化为独立函数（当前代码完全未读取 formhash）。仅服务 F2 写操作，不引入 UI 依赖。

### A2 认证层（推荐：WebView 辅助登录优先）
参考文档 1.3/3.2 的定位：WebView 承担验证码等人工流程，不承担主要论坛展示。结合本项目现状：

- **方案（两段式）**：
  1. 第一阶段——应用内可见 `BrowserScreen`（已有）打开 `member.php?mod=logging&action=login`，用户人工完成（含 seccode、可选 cookietime）；`CookieManagerCookieJar` 的"WebView 写、OkHttp 读"单一事实源使登录 cookie 自动对全部网络层生效，**无需自研登录表单与验证码**。
  2. 捕获与持久化：扩展 `SessionCookieStore` 追踪 auth cookie（键名已实测：`4fJN_2132_auth`，另有主站 `bus_auth`；§2.3-1）。
  3. 登录态检测：拉取 `forum.php` 判断 `a[href*="action=logout"]` / `guide&view=my` 标志（§2.3-2，登录/游客 fixture 已存对照）；`ForumRepository` 暴露 `observeLoginState()`。另需处理"无 Cookie → 登录页重定向"的会话失效分支（解析器须可判定，勿当空数据，见 A5）。
  4. 退出登录：清 auth cookie 回落游客态。
- **安全决策（D6）**：auth cookie 属敏感数据（参考文档 4.3）。默认仅在内存/当前 WebView 会话保留；"保持登入"作为用户显式选项，持久化时调研 Keystore 加密（`EncryptedFile`/DataStore+Keystore 封装）。日志一律不得输出 cookie/formhash（沿用现有脱敏习惯）。
- 第二阶段（可选增强）：原生登录表单 + seccode 图片拉取（`misc.php?mod=seccode&action=update&idhash=...`）+ 用户输入，减少跳转。仅当 WebView 流程体验不佳再做。

### A3 写操作 Action Client + 错误解释器
参考文档 4.2/7.2/8.1：

- `ForumReplyClient`：在共享 WebView 会话内构造并 POST fastpost（§2.3-4：`message` 编辑器为运行时注入，裸 OkHttp 不可行；沿用 `BrowserSessionManager.fetchAjaxDocument` 的同源 XHR 通道），成功后重读主题详情确认（参考文档验收：写入成功后可从服务器重新读取确认）。
- **提交状态锁**：提交中禁用按钮/防重复点击（非幂等 POST 禁自动重试）。
- `ResponseInterpreter`：统一映射错误 → 网络错误 / HTTP 错误 / 未登录 / 验证码 / 权限不足 / 频率限制 / 业务失败，输出 `UserMessage`（现有一次性事件通道）。错误页结构已实采（showmessage `alert_error`，如"需设置头像"；登录重定向页另有 fixture），完整成败分支待 R3 成功样本。

### A4 轻量解析通道调研（R1）
触屏版（WAP）DOM 明显轻于桌面定制模板，可能提升列表类页面解析稳定性与体积。风险：触屏/桌面切换依赖 cookie（`mobile=yes`），**会污染现有共享 WebView 会话**——需在 `BrowserSessionManager` 侧验证隔离方案（独立 cookie 域或用后复原）后才可启用。结论前不动现有读路径。

### A5 测试体系升级（fixture 回归）
参考文档 10.1：真实页面脱敏 fixture 库已建立（2026-11-24，14 份，`app/src/test/resources/forum/fixtures/`，覆盖登录态/无 Cookie 登录重定向/showmessage 错误页/空态/搜索结果/公开用户空间/WAP 形态；清单与脱敏规则见该目录 README.md）。下一步把 fixture 接入单测并建立"结构断言"——关键容器缺失或收到登录重定向时返回**可诊断的 ParseFailure 而非静默空列表**（当前空列表行为会让上层显示"无数据"，难以区分模板改版与会话失效）。现有 parser 测试（如 706 行的 `ForumThreadParserTest`）继续保留为构造样本单测。

### A6 协议档案版本化
本文档 §2 即首版协议档案；后续协议相关变更（新参数、模板改版、新增入口）更新 §2 并注明日期。真实请求样本与 selector 清单不入库敏感值（cookie/账号）。

### A7 模块边界：不迁移多 Gradle module
参考文档建议多 module（app/core/data/domain/feature/design）。本项目为单 module 分包结构，迁移收益低、成本高（AGENTS.md：最小改动原则）。等价约束以**包边界**执行：写操作（FormParser/ReplyClient/ResponseInterpreter）全部收在 `data/parser`、`data/gateway`、`core/http` 新文件内，不渗透 UI 层；UI 只消费领域模型与结果类型。

### A8 协议调试页（debug variant）
参考文档 9.1 的 P0 建议：debug 构建内置"输入 URL → 查看 HTTP 状态/编码/表单字段/cookie 状态/解析结果对照"页面，显著提升 selector 与协议调试效率。仅 `debugImplementation`/debug sourceSet，不进 release。

---

## 六、实施路线（映射参考文档 P0–P3）

| 阶段 | 内容 | 对应 |
|---|---|---|
| P0 协议摸底 | 两轮完成（§2.1 游客态、§2.3 登录态）；R3 成功样本待账号设置头像；fixture 库已建立（A5） | — |
| P1 只读增强 | **F1 全部**；建议顺序：F1-1 搜索 → F1-4 筛选补全 → F1-5 内容节点 → F1-2/3/7；并行 A5/A6/A8 | 纯读路径，无依赖 |
| P2 互动能力 | A1 + A2 + A3 → F2-1 回帖 → F2-2 引用 → F2-3 收藏 | 依赖 R2/R3 采样 |
| P3 产品化 | 草稿保留（回帖失败不丢内容）、提交状态完善、异常恢复 | 并入 F2 细节 |

---

## 七、风险与决策记录

| 风险 | 影响 | 缓解 |
|---|---|---|
| 桌面模板定制（已去除版本号/自有页脚） | selector 与标准 X3.4 偏差 | 一切以实测为准；A5 fixture 回归；A6 档案 |
| seccode 验证码 | 原生登录无法全自动 | A2 WebView 辅助登录优先 |
| CSRF formhash | 写操作被拒 | 每次提交前从页面动态提取（A1），不长期缓存 |
| 搜索频率限制 | 429/冷却 | 节流 + 冷却提示 + searchid 短缓存 |
| 反爬/driver-verify 门禁 | 请求被拦 | 已由隐藏 WebView 会话解决（现状达标） |
| 镜像切换 | 会话/缓存串号 | 已有 siteCacheKey 镜像隔离（现状达标） |
| auth cookie 泄漏 | 账号安全 | D6：默认不持久化；持久化需显式 opt-in + Keystore 调研 |
| 触屏版 cookie 污染 | A4 破坏现有会话 | 隔离验证先行，结论前不启用 |

**已确定决策**：
- D1 不引入 Paging3/Retrofit（现有 SWR/OkHttp 满足；参考文档也仅列为"可选/建议"）。
- D2 不迁移多 Gradle module，以包边界约束代替（A7）。
- D3 登录采用 WebView 辅助优先，原生表单登录为后续增强（A2）。
- D4 JSON API 通道经实测不存在，HTML 为唯一数据通道；触屏版仅作调研（A4/R1）。
- D5 写操作最小集 = 回帖 + 引用；发帖/私信/通知/附件上传遗留（F3）。
- D6 auth cookie 默认不落盘，"保持登入"显式 opt-in 并调研 Keystore。
- D7 写操作（回帖）统一走共享 WebView 运行时会话构造与提交（§2.3-4）；不实现裸 OkHttp 表单 POST 路径。

---

## 八、调研方向清单（汇总）

| 编号 | 方向 | 依赖 | 状态 |
|---|---|---|---|
| R1 | 触屏版轻量解析通道（DOM 对比 + 会话隔离方案） | WAP 样本已存（`fixtures/wap_index.html`） | 待调研（A4） |
| R2 | 登录态协议采集：auth cookie 键名、登录标志 selector、权限边界 | 已完成（§2.3） | ✅ 完成 |
| R3 | 写操作响应样本：回帖 inajax 成败分支、错误页特征 | 错误页两级已采（头像门槛→等级门槛）；**收藏/点赞已由用户手动执行并验证**（§2.4-3/4）；回帖成功样本待账号升级（人工审核制） | 大部分完成 |
| R4 | 高级搜索（用户/版块/时间范围）与用户空间游客可见性 | 用户空间两态可见、搜索两态可用均已验证（§2.5）；仅高级搜索条件未展开 | 基本完成 |
| R5 | 附件下载鉴权（aid 链接游客/登录行为） | 登录态 + 权限 | 遗留 |
| R6 | 参考实现借鉴：DiscuzHub（参考文档附录 B）；另本机已有 JbForum-ref 分析（其登录生态/链接识别可与 A2/A3 对照，MIT 许可） | 无 | 按需 |

---

*本文档由三方调研合成（参考设计文档 + 代码摸底 + 站点实测）。实施前按 AGENTS.md 工作流先确认方案再动工；F2/F3 涉及写操作与隐私，务必先完成 R2/R3 采样。*
