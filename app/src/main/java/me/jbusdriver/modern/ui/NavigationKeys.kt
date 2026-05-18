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
    val movieUrl: String
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
    val avatar: String = ""
) : NavKey
