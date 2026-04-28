package me.jbusdriver.modern.ui

object NavigationKeys {
    const val ROUTE_MAIN = "main"
    const val ROUTE_MOVIE_DETAIL = "movie_detail/{movieUrl}"
    const val ROUTE_IMAGE_VIEWER = "image_viewer/{images}?startIndex={startIndex}"
    const val ROUTE_LINK_MOVIES = "link_movies/{linkUrl}?title={title}&type={type}&avatar={avatar}"

    fun movieDetailUrl(movieUrl: String) = "movie_detail/${java.net.URLEncoder.encode(movieUrl, "UTF-8")}"

    fun imageViewer(images: List<String>, startIndex: Int = 0): String {
        val encoded = images.joinToString("|||") { java.net.URLEncoder.encode(it, "UTF-8") }
        return "image_viewer/$encoded?startIndex=$startIndex"
    }

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
