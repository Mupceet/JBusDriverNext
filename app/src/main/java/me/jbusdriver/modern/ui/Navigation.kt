package me.jbusdriver.modern.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.settings.SettingsScreen

@Composable
fun JBusNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavigationKeys.ROUTE_MOVIE_LIST
    ) {
        composable(NavigationKeys.ROUTE_MOVIE_LIST) {
            MovieListScreen()
        }
        composable(NavigationKeys.ROUTE_SETTINGS) {
            SettingsScreen()
        }
    }
}
