package me.jbusdriver.modern.data.settings

/**
 * Sort order for a forum thread list. Maps to the Discuz `orderby` query param.
 *
 * The default ([LASTPOST]) is equivalent to the site's bare URL behaviour
 * (no `orderby` param).
 */
enum class ForumThreadOrder(
    val preferenceValue: String,
    val cacheValue: String
) {
    LASTPOST("lastpost", "lastpost"),
    DATELINE("dateline", "dateline"),
    HEATS("heats", "heats"),
    REPLIES("replies", "replies"),
    VIEWS("views", "views");

    companion object {
        fun fromPreferenceValue(value: String?): ForumThreadOrder =
            entries.firstOrNull { it.preferenceValue == value } ?: LASTPOST
    }
}

/**
 * Builds a `mod=forumdisplay` thread list URL.
 */
fun buildForumThreadListUrl(
    baseUrl: String,
    fid: Int,
    page: Int,
    typeId: Int?,
    threadOrder: ForumThreadOrder
): String {
    val displayUrl = "$baseUrl/forum/forum.php?mod=forumdisplay&fid=$fid&page=$page"
    val url = if (typeId != null) "$displayUrl&filter=typeid&typeid=$typeId" else displayUrl
    return "$url&orderby=${threadOrder.preferenceValue}"
}
