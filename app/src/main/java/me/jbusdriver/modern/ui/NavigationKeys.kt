package me.jbusdriver.modern.ui

object NavigationKeys {
    const val ROUTE_MAIN = "main"
    const val ROUTE_MOVIE_DETAIL = "movie_detail/{movieUrl}"

    fun movieDetailUrl(movieUrl: String) = "movie_detail/${java.net.URLEncoder.encode(movieUrl, "UTF-8")}"
}
