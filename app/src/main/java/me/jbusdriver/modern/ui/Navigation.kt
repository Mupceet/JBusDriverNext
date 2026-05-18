/**
 * 职责：Compose Navigation 路由图定义
 *
 * 使用场景：JBusNavigation() 在 ModernMainActivity 中作为根 Composable 调用，
 * 管理所有页面的导航关系和转场动画
 *
 * 路由结构：
 * main → 电影/演员/收藏 Tab 页面
 * search → 搜索页
 * movie_detail/{movieUrl} → 电影详情页
 * image_viewer/{images} → 全屏图片查看器
 * link_movies/{linkUrl} → 演员作品/类别电影列表页
 */
package me.jbusdriver.modern.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import kotlinx.coroutines.flow.StateFlow
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.jbusdriver.modern.ui.detail.MovieDetailScreen
import me.jbusdriver.modern.ui.image.ImageViewScreen
import me.jbusdriver.modern.ui.movielist.LinkMovieListScreen
import me.jbusdriver.modern.ui.search.SearchScreen
import java.net.URLDecoder

private const val ANIM_DURATION = 350
private const val ANIM_DURATION_SEARCH = 400

@Composable
fun JBusNavigation(
    navController: NavHostController = rememberNavController(),
    deepLinkFlow: StateFlow<String?>? = null,
    deepLinkUri: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val deepLinkValue = deepLinkFlow?.collectAsState()?.value ?: deepLinkUri
    LaunchedEffect(deepLinkValue) {
        deepLinkValue?.let { route ->
            if (route == NavigationKeys.ROUTE_MAIN) {
                navController.popBackStack(NavigationKeys.ROUTE_MAIN, inclusive = false)
            } else {
                val currentRoute = navController.currentDestination?.route ?: ""
                when {
                    currentRoute.startsWith("search") -> {
                        navController.popBackStack()
                        navController.navigate(route)
                    }
                    currentRoute.startsWith("image_viewer") -> {
                        navController.popBackStack()
                        navController.navigate(route)
                    }
                    else -> navController.navigate(route)
                }
            }
            onDeepLinkConsumed()
        }
    }
    NavHost(
        navController = navController,
        startDestination = NavigationKeys.ROUTE_MAIN,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(ANIM_DURATION)
            )
        },
        exitTransition = {
            scaleOut(targetScale = 0.9f, animationSpec = tween(ANIM_DURATION)) +
                fadeOut(tween(ANIM_DURATION))
        },
        popEnterTransition = {
            scaleIn(initialScale = 0.9f, animationSpec = tween(ANIM_DURATION)) +
                fadeIn(tween(ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(ANIM_DURATION)
            )
        }
    ) {
        composable(NavigationKeys.ROUTE_MAIN) {
            MainScreen(
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                },
                onActressClick = { actress ->
                    navController.navigate(
                        NavigationKeys.linkMovies(
                            actress.link,
                            actress.name,
                            type = "actress",
                            avatar = actress.avatar
                        )
                    )
                },
                onGenreClick = { genre ->
                    navController.navigate(
                        NavigationKeys.linkMovies(genre.link, genre.name, type = "genre")
                    )
                },
                onSearchClick = { searchType ->
                    navController.navigate(NavigationKeys.searchWithType(searchType))
                }
            )
        }
        composable(
            route = NavigationKeys.ROUTE_SEARCH_WITH_TYPE,
            arguments = listOf(
                navArgument("defaultSearchType") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    tween(ANIM_DURATION_SEARCH)
                ) + fadeIn(tween(ANIM_DURATION_SEARCH))
            },
            exitTransition = {
                scaleOut(targetScale = 0.9f, animationSpec = tween(ANIM_DURATION)) +
                    fadeOut(tween(ANIM_DURATION))
            },
            popEnterTransition = {
                scaleIn(initialScale = 0.9f, animationSpec = tween(ANIM_DURATION)) +
                    fadeIn(tween(ANIM_DURATION))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    tween(ANIM_DURATION_SEARCH)
                ) + fadeOut(tween(ANIM_DURATION_SEARCH))
            }
        ) { backStackEntry ->
            val defaultSearchType = backStackEntry.arguments?.getString("defaultSearchType") ?: ""
            SearchScreen(
                defaultSearchType = defaultSearchType,
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                },
                onActressClick = { actress ->
                    navController.navigate(
                        NavigationKeys.linkMovies(
                            actress.link,
                            actress.name,
                            type = "actress",
                            avatar = actress.avatar
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = NavigationKeys.ROUTE_MOVIE_DETAIL,
            arguments = listOf(navArgument("movieUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("movieUrl") ?: ""
            val movieUrl = URLDecoder.decode(encodedUrl, "UTF-8")
            MovieDetailScreen(
                movieUrl = movieUrl,
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                },
                onImageClick = { images, startIndex ->
                    navController.navigate(NavigationKeys.imageViewer(images, startIndex))
                },
                onActressClick = { actress ->
                    navController.navigate(
                        NavigationKeys.linkMovies(
                            actress.link,
                            actress.name,
                            type = "actress",
                            avatar = actress.avatar
                        )
                    )
                },
                onGenreClick = { genre ->
                    navController.navigate(
                        NavigationKeys.linkMovies(genre.link, genre.name, type = "genre")
                    )
                },
                onHeaderClick = { header ->
                    if (header.link.isNotBlank()) {
                        navController.navigate(
                            NavigationKeys.linkMovies(header.link, header.name + ": " + header.value, type = "header")
                        )
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = NavigationKeys.ROUTE_IMAGE_VIEWER,
            arguments = listOf(
                navArgument("startIndex") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val imagesJson = backStackEntry.arguments?.getString("images") ?: ""
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            val images = imagesJson.split("|||").filter { it.isNotBlank() }
            ImageViewScreen(
                images = images,
                startIndex = startIndex,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = NavigationKeys.ROUTE_LINK_MOVIES,
            arguments = listOf(
                navArgument("linkUrl") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("type") { type = NavType.StringType; defaultValue = "" },
                navArgument("avatar") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val linkUrl =
                URLDecoder.decode(backStackEntry.arguments?.getString("linkUrl") ?: "", "UTF-8")
            val title =
                URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val avatar =
                URLDecoder.decode(backStackEntry.arguments?.getString("avatar") ?: "", "UTF-8")
            LinkMovieListScreen(
                linkUrl = linkUrl,
                title = title,
                type = type,
                avatarUrl = avatar,
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
