package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable

/**
 * 论坛登录态（协议依据 docs/FORUM_DESIGN.md §2.3-2 / §2.5）：
 * 站点页面无 discuz_username 变量，用户名/头像/用户组取自 `home.php?mod=space` 自空间页。
 */
@Immutable
sealed interface ForumLoginState {
    data object Anonymous : ForumLoginState
    data class LoggedIn(
        val uid: Int,
        val username: String,
        val avatarUrl: String? = null,
        val groupName: String? = null,
    ) : ForumLoginState
}

/** 登录页动态表单数据（action 含 loginhash；seccodehash 取自 updateseccode JS）。 */
@Immutable
data class ForumLoginForm(
    val action: String,
    val formhash: String,
    val referer: String,
    val seccodeHash: String,
)

/** 一次可提交的登录挑战：表单数据 + seccode 验证码图片字节。 */
@Immutable
data class ForumLoginChallenge(
    val form: ForumLoginForm,
    val captchaImage: ByteArray,
)

@Immutable
data class ForumProfileField(val label: String, val value: String)

/** 用户资料页（home.php?mod=space[&uid=]）。 */
@Immutable
data class ForumUserProfile(
    val uid: Int,
    val username: String,
    val avatarUrl: String? = null,
    val groupName: String = "",
    /** 等级货币里程（用户组 tip），与 [mileageToNextLevel] 组成升级进度条。 */
    val mileage: Int? = null,
    /** 距下一级还差的里程，解析失败为 null。 */
    val mileageToNextLevel: Int? = null,
    /** 基本信息字段（郵箱狀態/性別/生日/已用空間/里程/金錢）。 */
    val basics: List<ForumProfileField> = emptyList(),
    /** 活跃状态字段（#pbbs：在線時間/註冊時間/最後訪問/IP/上次活動/上次發表/時區，按值条件渲染）。 */
    val activity: List<ForumProfileField> = emptyList(),
    /** 統計信息行计数（好友數/回帖數/主題數），解析失败为 null。 */
    val friendCount: Int? = null,
    val replyCount: Int? = null,
    val topicCount: Int? = null,
)

/** 用户主题/回复列表行（home.php?mod=space&do=thread）。 */
@Immutable
data class ForumUserThread(
    val tid: Int,
    val title: String,
    val boardName: String,
    val time: String,
    /** 回复数（td.num 的 a）；表头行/解析失败为 0。 */
    val replyCount: Int = 0,
    /** 查看数（td.num 的 em）。 */
    val viewCount: Int = 0,
    /** 最后回复人（td.by 的 cite）。旧缓存 JSON 无此字段，Gson 置 null，须可空。 */
    val lastReplyAuthor: String? = null,
    /** 回复视图（type=reply）下该用户在此帖的回帖摘要；主题视图无此结构为 null。旧缓存兼容须可空。 */
    val replies: List<String>? = null,
)

@Immutable
data class ForumUserThreadPageResult(
    val threads: List<ForumUserThread>,
    val pageInfo: PageInfo,
)

/** 收藏列表行（home.php?mod=space&do=favorite&view=me）。 */
@Immutable
data class ForumFavoriteThread(
    val favId: Long,
    val tid: Int,
    val title: String,
    val time: String,
)

@Immutable
data class ForumFavoritePageResult(
    val favorites: List<ForumFavoriteThread>,
    val pageInfo: PageInfo,
)

/** 用户主题列表类型（home.php?mod=space&do=thread 的 type 参数）。 */
enum class ForumUserThreadType(val param: String) {
    THREAD("thread"),
    REPLY("reply");
}

/**
 * 用户组写权限（内置等级表，不解析 usergroup 页）。
 *
 * 站点为人工审核等级制（docs/FORUM_DESIGN.md §2.6 全量实测）：组权限恒定、
 * 不随里程变动，故按组名直接映射。回帖需駕校學員+、发帖/加好友需實習司機+、
 * 短消息/点评需駕校學員+；收藏/点赞与等级无关（登录即可）。
 * 组名未知（null/空/未收录名）时全部放开——显示入口，由服务端错误提示兜底。
 */
@Immutable
data class ForumUserPermissions(
    val groupName: String,
    val canReply: Boolean,
    val canPostThread: Boolean,
    val canSendMessage: Boolean,
    val canAddFriend: Boolean,
    val canComment: Boolean,
) {
    companion object {

        /** 写权限全禁的特殊组（禁言/限制/等待验证/游客类，§2.6 特殊组清单）。 */
        private val WRITE_DISABLED_GROUPS = setOf(
            "限制會員", "禁止發言", "禁止訪問", "禁止IP", "等待驗證", "QQ遊客", "遊客"
        )

        fun forGroupName(groupName: String?): ForumUserPermissions {
            val group = groupName?.trim().orEmpty()
            return when {
                group.isEmpty() -> allGranted(group)
                group == "路人乘客" -> noneGranted(group)
                group == "駕校學員" -> ForumUserPermissions(
                    groupName = group,
                    canReply = true,
                    canPostThread = false,
                    canSendMessage = true,
                    canAddFriend = false,
                    canComment = true,
                )

                WRITE_DISABLED_GROUPS.contains(group) -> noneGranted(group)
                // 實習司機及以上、管理組，及未收录组名（放开入口，服务端兜底）
                else -> allGranted(group)
            }
        }

        private fun allGranted(group: String) = ForumUserPermissions(
            groupName = group,
            canReply = true,
            canPostThread = true,
            canSendMessage = true,
            canAddFriend = true,
            canComment = true,
        )

        private fun noneGranted(group: String) = ForumUserPermissions(
            groupName = group,
            canReply = false,
            canPostThread = false,
            canSendMessage = false,
            canAddFriend = false,
            canComment = false,
        )
    }
}
