package me.jbusdriver.modern.data.model

import me.jbusdriver.mvp.bean.Movie

data class PageInfo(
    val activePage: Int = 0,
    val nextPage: Int = 0,
    val referPages: List<Int> = emptyList()
)

val PageInfo.hasNext: Boolean
    inline get() = activePage < nextPage

data class MoviePageResult(
    val pageInfo: PageInfo,
    val movies: List<Movie>
)

data class ActressDetail(
    val name: String,
    val avatar: String,
    val info: List<String>
)
