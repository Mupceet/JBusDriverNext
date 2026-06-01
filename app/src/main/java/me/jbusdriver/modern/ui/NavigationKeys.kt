package me.jbusdriver.modern.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object RouteMain : NavKey

@Serializable
data class RouteSearch(
    val defaultSearchType: String = ""
) : NavKey

@Serializable
data class RouteMovieDetail(
    val movieUrl: String,
    val censorType: String? = null
) : NavKey

@Serializable
data class RouteImageViewer(
    val images: List<String>,
    val startIndex: Int = 0
) : NavKey

@Serializable
data class RouteLinkMovies(
    val linkUrl: String,
    val title: String = "",
    val type: String = "",
    val avatar: String = "",
    val censorType: String? = null
) : NavKey

@Serializable
data class RouteForumThreadList(
    val fid: Int,
    val title: String = "",
    val typeId: Int? = null
) : NavKey

@Serializable
data class RouteForumThreadDetail(
    val tid: Int
) : NavKey

@Serializable
data object RouteLabSettings : NavKey
