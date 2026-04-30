package me.jbusdriver.modern.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.jbusdriver.modern.ui.detail.MovieDetailScreen
import me.jbusdriver.modern.ui.image.ImageViewScreen
import me.jbusdriver.modern.ui.movielist.LinkMovieListScreen
import me.jbusdriver.modern.ui.search.SearchScreen
import java.net.URLDecoder

private const val ANIM_DURATION = 300

@Composable
fun JBusNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavigationKeys.ROUTE_MAIN,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ANIM_DURATION)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ANIM_DURATION)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_DURATION)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_DURATION)) }
    ) {
        composable(NavigationKeys.ROUTE_MAIN) {
            MainScreen(
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                },
                onActressClick = { actress ->
                    navController.navigate(
                        NavigationKeys.linkMovies(actress.link, actress.name, type = "actress", avatar = actress.avatar)
                    )
                },
                onGenreClick = { genre ->
                    navController.navigate(
                        NavigationKeys.linkMovies(genre.link, genre.name, type = "genre")
                    )
                },
                onSearchClick = {
                    navController.navigate(NavigationKeys.ROUTE_SEARCH)
                }
            )
        }
        composable(NavigationKeys.ROUTE_SEARCH) {
            SearchScreen(
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                },
                onActressClick = { actress ->
                    navController.navigate(
                        NavigationKeys.linkMovies(actress.link, actress.name, type = "actress", avatar = actress.avatar)
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
                        NavigationKeys.linkMovies(actress.link, actress.name, type = "actress", avatar = actress.avatar)
                    )
                },
                onGenreClick = { genre ->
                    navController.navigate(
                        NavigationKeys.linkMovies(genre.link, genre.name, type = "genre")
                    )
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
            val linkUrl = URLDecoder.decode(backStackEntry.arguments?.getString("linkUrl") ?: "", "UTF-8")
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val avatar = URLDecoder.decode(backStackEntry.arguments?.getString("avatar") ?: "", "UTF-8")
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
