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

/** 个人主页（自/他复用；主題/回覆/收藏/資料 页签就地切换）。uid 来自账号态或作者点击。 */
@Serializable
data class RouteUserSpace(
    val uid: Int
) : NavKey

@Serializable
data object RouteSettings : NavKey

@Serializable
data object RouteSiteManager : NavKey

/** 内置浏览器兜底打开网页（替代跳系统浏览器）。 */
@Serializable
data class RouteBrowser(
    val url: String,
    val title: String = ""
) : NavKey

/**
 * 内置浏览器承载的 LTX 人机验证：加载 [url]（LTX 站点首页）让 SPA 过 Turnstile，
 * 探测到门禁通过即导出会话并自动返回列表页（见 Navigation 中该路由的接线）。
 */
@Serializable
data class RouteLtxVerify(
    val url: String,
    val title: String = ""
) : NavKey
