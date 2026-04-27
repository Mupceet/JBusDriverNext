package me.jbusdriver.modern.ui

object NavigationKeys {
    const val ROUTE_MAIN = "main"
    const val ROUTE_MOVIE_DETAIL = "movie_detail/{movieUrl}"
    const val ROUTE_IMAGE_VIEWER = "image_viewer/{images}?startIndex={startIndex}"

    fun movieDetailUrl(movieUrl: String) = "movie_detail/${java.net.URLEncoder.encode(movieUrl, "UTF-8")}"

    fun imageViewer(images: List<String>, startIndex: Int = 0): String {
        val encoded = images.joinToString("|||") { java.net.URLEncoder.encode(it, "UTF-8") }
        return "image_viewer/$encoded?startIndex=$startIndex"
    }
}
