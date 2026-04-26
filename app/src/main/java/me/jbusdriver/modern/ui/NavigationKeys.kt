package me.jbusdriver.modern.ui

object NavigationKeys {
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_MOVIE_LIST = "movie_list"
    const val ROUTE_MOVIE_DETAIL = "movie_detail/{movieUrl}"
    const val ROUTE_SEARCH = "search"

    fun movieDetailUrl(movieUrl: String) = "movie_detail/${java.net.URLEncoder.encode(movieUrl, "UTF-8")}"
}
