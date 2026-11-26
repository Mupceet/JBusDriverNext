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
        // 正序必须显式携带 ordertype=2：部分帖子服务端默认就是倒序，缺省参数会被当作倒序渲染。
        ForumFloorOrder.REGULAR -> "$withAuthor&ordertype=2"
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
 * 服务端会 302 到目标楼层所在分页；两种顺序都显式携带 ordertype 让落地页与显示顺序一致
 * （部分帖子服务端默认就是倒序，正序落地页缺省参数会被当作倒序）。
 */
fun buildForumFindPostUrl(
    baseUrl: String,
    tid: Int,
    pid: Int,
    floorOrder: ForumFloorOrder
): String {
    val url = "$baseUrl/forum/forum.php?mod=redirect&goto=findpost&pid=$pid&ptid=$tid"
    return when (floorOrder) {
        ForumFloorOrder.REGULAR -> "$url&ordertype=2"
        ForumFloorOrder.REVERSE -> "$url&ordertype=1"
    }
}
