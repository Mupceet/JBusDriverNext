package me.jbusdriver.modern.ui

/**
 * 职责：Compose Navigation 路由常量和 URL 构建工具
 *
 * 使用场景：Navigation.kt 中定义路由、各 Screen 中构建导航目标
 * 线程：无状态，纯函数
 */
object NavigationKeys {
    const val ROUTE_MAIN = "main"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_SEARCH_WITH_TYPE = "search?defaultSearchType={defaultSearchType}"
    const val ROUTE_MOVIE_DETAIL = "movie_detail/{movieUrl}"
    const val ROUTE_IMAGE_VIEWER = "image_viewer/{images}?startIndex={startIndex}"
    const val ROUTE_LINK_MOVIES = "link_movies/{linkUrl}?title={title}&type={type}&avatar={avatar}"

    /** 构建电影详情页路由 URL，自动编码 movieUrl 参数 */
    fun movieDetailUrl(movieUrl: String) =
        "movie_detail/${java.net.URLEncoder.encode(movieUrl, "UTF-8")}"

    /**
     * 构建图片查看器路由 URL
     *
     * 多张图片用 ||| 分隔符连接（避免与 URL 编码冲突）
     */
    fun imageViewer(images: List<String>, startIndex: Int = 0): String {
        val encoded = images.joinToString("|||") { java.net.URLEncoder.encode(it, "UTF-8") }
        return "image_viewer/$encoded?startIndex=$startIndex"
    }

    /**
     * 构建链接电影列表页路由 URL（演员作品列表、类别电影列表等）
     *
     * @param linkUrl 演员或类别的链接 URL
     * @param title 页面标题
     * @param type 链接类型："actress" 或 "genre"
     * @param avatar 演员头像 URL（仅 actress 类型使用）
     */
    fun searchWithType(searchType: String): String = "search?defaultSearchType=$searchType"

    fun linkMovies(
        linkUrl: String,
        title: String = "",
        type: String = "",
        avatar: String = ""
    ): String {
        val encoded = java.net.URLEncoder.encode(linkUrl, "UTF-8")
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val encodedAvatar = java.net.URLEncoder.encode(avatar, "UTF-8")
        return "link_movies/$encoded?title=$encodedTitle&type=$type&avatar=$encodedAvatar"
    }
}
