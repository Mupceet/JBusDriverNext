# 论坛协议 Fixtures（Discuz X3.4 实采样本）

**采集日期**: 2026-11-24
**采集方式**: 内置浏览器登录态会话内 `fetch()`（同源带 Cookie），**内存中脱敏后落盘**；原始未脱敏内容从未写入磁盘。
**用途**: `data/parser/` 各解析器与后续协议适配（A1 Form Engine / A3 ResponseInterpreter）的回归测试资源；JUnit 单测可经 classpath 读取：`src/test/resources/forum/fixtures/`。

## 脱敏规则（所有文件已应用）

| 原值 | 占位符 |
|---|---|
| `formhash=<val>`（URL 与 hidden input） | `fixt4hash` |
| `loginhash=<val>` | `fixthash` |
| `seccodehash` hidden value | `fixtseccode` |
| `idhash=<val>`（seccode 刷新参数） | `fixtidhash` |
| 采集账号用户名（明文与 URL 编码形式） | `fixture_user` |
| 采集账号 UID（`space-uid-` / `uid=` / `authorid=` / `discuz_uid`） | `999999` |

> ⚠️ 新增 fixture 必须先过同样的脱敏；不得加入未脱敏的 Cookie、formhash、账号信息。其它用户的公开内容（用户名/主题/头像 URL）属于站点公开数据，予以保留。

## 文件清单

| 文件 | 来源 URL（相对 `/forum/`） | 状态 | 说明 / 测试用途 |
|---|---|---|---|
| `forum_index_logged_in.html` | `forum.php` | 登录 | 首页解析（版块分组/横幅/三栏摘要）+ 登录态标志（`action=logout`、`guide&view=my`） |
| `forum_index_nocookie_login_redirect.html` | `forum.php` | 无 Cookie | **无 Cookie 请求被重定向到登录页**——会话失效/门禁行为样本；解析器收到此结构应判定"需要重建会话"而非"空数据" |
| `login_page_guest.html` | `member.php?mod=logging&action=login&referer=` | 游客 | 登录表单完整结构：`loginhash` action、`loginfield/username/password/questionid/answer/cookietime/seccodeverify(+seccodehash/seccodemodid)` |
| `board_fid36_logged_in.html` | `forum.php?mod=forumdisplay&fid=36` | 登录 | 版块主题列表解析（置顶/普通主题、typeid 筛选条、排序条、分页）；104KB，最完整列表样本 |
| `board_fid36_nocookie_login_redirect.html` | 同上 | 无 Cookie | 登录重定向样本 |
| `thread_179228_logged_in.html` | `forum.php?mod=viewthread&tid=179228` | 登录 | 帖子详情解析（楼层/头像/用户组/引用/图片/点评）；含 fastpost 表单（见下方"写路径结论"） |
| `thread_179228_nocookie_login_redirect.html` | 同上 | 无 Cookie | 登录重定向样本 |
| `reply_editor_179228_logged_in.html` | `forum.php?mod=post&action=reply&fid=36&tid=179228&repquote=0` | 登录（被拦） | **标准 Discuz showmessage 错误页**（`alert_error` + `<p>` 消息 + 返回链接）："需要設置自己的頭像後才能進行本操作"——站点级发帖权限边界 + ResponseInterpreter 错误页结构样本 |
| `favorites_logged_in.html` | `home.php?mod=space&do=favorite&view=me` | 登录 | 收藏列表**空态**（"還沒有…"）；含账号个人数据，已脱敏用户名 |
| `guide_my_logged_in.html` | `forum.php?mod=guide&view=my` | 登录 | "我的帖子"**空态**；`guide&view=my` 即我的主题入口 |
| `search_result_logged_in.html` | `search.php?mod=forum&srchtxt=分享&searchsubmit=yes` → 302 `searchid` | 登录 | 搜索结果页结构（F1-1 论坛搜索的解析目标） |
| `announcement_2_logged_in.html` | `forum.php?mod=announcement&id=2` | 登录 | 公告详情页（F1-3） |
| `user_space_public.html` | `home.php?mod=space&uid=662631` | 登录 | 公开用户空间（他人资料页，F1-7；含个人资料字段与主题列表结构） |
| `wap_index.html` | `api/mobile/index.php?version=4&module=forumindex` | 登录 | 触屏版（WAP/XHTML）首页，6.9KB 轻量 DOM——R1 轻量解析通道研究素材 |
| `reply_editor_denied_level_requirement.html` | `forum.php?mod=post&action=reply&fid=2&tid=179227` | 登录（等级拦截） | 设置头像后的第二级错误页："沒有權限在該版塊回帖…點此查看置頂帖"——与头像门槛错误页构成权限链两级样本 |
| `favorite_form_thread.html` | `home.php?mod=spacecp&ac=favorite&type=thread&id=179227` | 登录 | **收藏表单**完整结构：action=`spacecp&ac=favorite&type=thread&id={tid}&spaceuid=0`，字段 `formhash/favoritesubmit/description/referer`（F2-3 POST 目标） |
| `pinned_permission_rules.html` | `forum.php?mod=viewthread&tid=15172` | 公开 | 站点等级制度置顶帖：路人乘客不可回帖发帖 / 駕校學員可回帖 / 實習司機可发帖（人工审核制，§2.4） |
| `favorites_logged_in_nonempty.html` | `home.php?mod=space&do=favorite&view=me` | 登录（已收藏一帖） | 收藏列表**非空态**：行结构 = 刪除链接 + 主题标题 + 时间（F2-3 列表解析目标；用户手动收藏后采集验证） |
| `forum_index_guest.html` | `forum.php` | **真游客** | 游客版首页（`discuz_uid=0`、无 logout/我的帖子链接）——与登录版对照的登录态判定样本 |
| `board_fid36_guest.html` | `forum.php?mod=forumdisplay&fid=36` | 真游客 | 游客版版块列表（与登录版同构） |
| `thread_179228_guest.html` | `forum.php?mod=viewthread&tid=179228` | 真游客 | 游客版帖子详情；fastpost 显示"请先登录"（登录无权限时为"您現在無權發帖"）；点贊/收藏按钮带计数渲染 |
| `search_result_guest.html` | `search.php?mod=forum&srchtxt=分享&searchsubmit=yes` | 真游客 | 游客搜索结果页 |
| `user_space_guest.html` | `home.php?mod=space&uid=662631` | 真游客 | 游客可见的公开用户空间 |
| `guide_sofa_guest.html` | `forum.php?mod=guide&view=sofa` | 真游客 | 导读-搶沙發（F1-2） |
| `wap_index_guest.html` | `api/mobile/index.php?version=4&module=forumindex` | 真游客 | 游客 WAP 触屏版 |
| `forum_usergroup_gid{21,10,11,12,13,14,15,22}.html` | `home.php?mod=spacecp&ac=usergroup&gid=N` | 登录 | **八个普通用户组的权限页**（2026-11-27）：页面为「标签表 + 两张值表」（我的主用户组 / 晋级用户组）三表并排结构；布尔权限值单元格是 `data_valid.gif`/`data_invalid.gif` 图标，分区标题行（帖子相關/家園相關/附件相關）的值 = 组名。等级阶梯与权限矩阵见 docs/FORUM_DESIGN.md §2.6 |
| `forum_usergroup_self.html` | `home.php?mod=spacecp&ac=usergroup`（无 gid） | 登录 | **本人用户组页**——运行时权限解析目标：首表含「我的主用戶組 - <组名>」标题行与「里程: N」行，值表即本人权限。**等级门控（按权限隐藏入口）的数据源** |

> 游客态采集方式：同浏览器临时摘除 auth cookie（先过主站首页门禁），采毕恢复登录态并验证；"真游客"样本与 `*_nocookie_login_redirect`（无任何 Cookie）不同——后者是会话失效/门禁未过的形态。

## 写路径结论（第二轮实测，详见 docs/FORUM_DESIGN.md §2.3）

1. **无 Cookie 裸请求**（forumdisplay/viewthread/forum.php）一律 302 到登录页；游客可读依赖门禁通过后的游客会话（`age` + `saltkey`），与 App `SessionCookieStore` 的有效性判定一致。
2. **fastpost 静态 HTML 无 `message` 字段**：表单仅含 `formhash/usesig/subject` 隐藏字段 + submit；正文编辑器由运行时按权限注入（无权限时渲染"您現在無權發帖"）。**写操作必须在 WebView 运行时环境构造/提交，裸 OkHttp 拼 POST 不可行。**
3. **站点要求已设置头像才能回帖/发帖**（`alert_error` 提示页）；未设头像的账号连编辑器都不注入。真实回帖成功响应样本（R3）需待账号设置头像后采集。
4. 登录态判定标志：`a[href*="action=logout"]` 存在；`guide&view=my` 链接仅登录态出现。
5. 帖子页操作按钮带计数：**`点贊{N}`**（recommend）、**`收藏{N}`**（favorite）；收藏与点赞已由用户手动执行验证（计数变化 + 收藏列表出现新行）。

## 结构覆盖与待补素材

已覆盖：签名（`class="sign"`，版块/帖子样本）、附件图（`attachimg`，帖子样本）、引用/图片/列表/点评（帖子样本）、筛选与排序条（版块样本）、搜索结果结构、错误页、登录重定向页、空态页、用户组权限页。

**待补**（当前样本未出现，后续遇到对应帖时可增量采集）：代码块 `blockcode`、附件列表 `dl.tattl`、投票 `pollsm`、缩放图 `img.zoomfile`。

## 采集脚本教训

- 脱敏正则必须覆盖 **`&amp;formhash=`** 形态：退出链接等 `href` 属性里的 formhash 经 HTML 转义，`[?&]formhash=` 匹配不到（2026-11-27 用户组页批次曾泄漏 `6769da07`，已全量补脱敏并复查全部存量 fixtures）。稳妥做法是落盘前直接按值全局替换，或统一 `formhash=[a-f0-9]+`。
- 用户组页三表并排（标签 47 行 + 值表 48 行）：值表比标签表多出的是首部附加行（我的主用户组 = 标题行 + 「里程: N」行；晋级用户组 = 「您升級到此用戶組還需里程 N」行），对齐时整体后移一行。
