package me.jbusdriver.modern.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.jbusdriver.modern.ui.detail.MovieDetailScreen
import java.net.URLDecoder

@Composable
fun JBusNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavigationKeys.ROUTE_MAIN
    ) {
        composable(NavigationKeys.ROUTE_MAIN) {
            MainScreen(
                onMovieClick = { movie ->
                    navController.navigate(NavigationKeys.movieDetailUrl(movie.link))
                }
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
                }
            )
        }
    }
}
