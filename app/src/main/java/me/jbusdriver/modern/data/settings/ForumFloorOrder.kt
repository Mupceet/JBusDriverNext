package me.jbusdriver.modern.data.settings

enum class ForumFloorOrder(
    val preferenceValue: String,
    val cacheValue: String
) {
    REGULAR("regular", "regular"),
    REVERSE("reverse", "reverse");

    companion object {
        fun fromPreferenceValue(value: String?): ForumFloorOrder =
            entries.firstOrNull { it.preferenceValue == value } ?: REGULAR
    }
}

fun buildForumThreadDetailUrl(
    baseUrl: String,
    tid: Int,
    page: Int,
    floorOrder: ForumFloorOrder,
    authorUid: Int? = null
): String {
    val url = "$baseUrl/forum/forum.php?mod=viewthread&tid=$tid&page=$page"
    val withAuthor = if (authorUid != null && authorUid > 0) "$url&authorid=$authorUid" else url
    return when (floorOrder) {
        ForumFloorOrder.REGULAR -> withAuthor
        ForumFloorOrder.REVERSE -> "$withAuthor&ordertype=1"
    }
}

fun forumThreadDetailCacheKey(
    tid: Int,
    page: Int,
    floorOrder: ForumFloorOrder
): String = "forum_detail_v2_${tid}_${page}_${floorOrder.cacheValue}"

/**
 * 构建 findpost 重定向链接（引用楼层的原始跳转目标）。
 * 服务端会 302 到目标楼层所在分页；REVERSE 附带 ordertype 让落地页与显示顺序一致。
 */
fun buildForumFindPostUrl(
    baseUrl: String,
    tid: Int,
    pid: Int,
    floorOrder: ForumFloorOrder
): String {
    val url = "$baseUrl/forum/forum.php?mod=redirect&goto=findpost&pid=$pid&ptid=$tid"
    return if (floorOrder == ForumFloorOrder.REVERSE) "$url&ordertype=1" else url
}
