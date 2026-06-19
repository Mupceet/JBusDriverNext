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
    floorOrder: ForumFloorOrder
): String {
    val url = "$baseUrl/forum/forum.php?mod=viewthread&tid=$tid&page=$page"
    return when (floorOrder) {
        ForumFloorOrder.REGULAR -> url
        ForumFloorOrder.REVERSE -> "$url&ordertype=1"
    }
}

fun forumThreadDetailCacheKey(
    tid: Int,
    page: Int,
    floorOrder: ForumFloorOrder
): String = "forum_detail_v2_${tid}_${page}_${floorOrder.cacheValue}"
