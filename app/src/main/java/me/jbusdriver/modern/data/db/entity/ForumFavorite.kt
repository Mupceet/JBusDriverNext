package me.jbusdriver.modern.data.db.entity

import androidx.room.Entity

/**
 * 论坛帖子收藏（`t_forum_favorite` 表）：服务端"我的收藏"的本地镜像，
 * 用于进入帖子时即时判定已收藏状态（免网络查询、不受收藏首页分页限制）。
 *
 * 数据来源：个人主页收藏页解析结果按页 upsert（正向知识只增不删，
 * 服务端删除需等下次收藏页同步覆盖）；登出清表防跨账号串数据。
 */
@Entity(tableName = "t_forum_favorite", primaryKeys = ["uid", "tid"])
data class ForumFavorite(
    /** 收藏者（登录账号）uid，与 tid 共同构成主键。 */
    val uid: Int,
    /** 帖子 id。 */
    val tid: Int,
    /** 服务端收藏记录 id（删除接口用），未知为 0。 */
    val favId: Long,
    /** 帖子标题（展示用快照）。 */
    val title: String,
    /** 收藏时间（服务端文案快照）。 */
    val time: String,
)
