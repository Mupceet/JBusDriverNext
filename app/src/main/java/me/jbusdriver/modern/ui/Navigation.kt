package me.jbusdriver.modern.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.jbusdriver.modern.ui.settings.SettingsScreen

@Composable
fun JBusNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavigationKeys.ROUTE_SETTINGS
    ) {
        composable(NavigationKeys.ROUTE_SETTINGS) {
            SettingsScreen()
        }
    }
}
