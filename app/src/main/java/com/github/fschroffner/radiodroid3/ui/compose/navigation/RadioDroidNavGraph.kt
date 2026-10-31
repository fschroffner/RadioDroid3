package com.github.fschroffner.radiodroid3.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Top-level destination definitions for Jetpack Compose Navigation.
 * Prepares the architecture for migrating legacy Fragments to pure Compose screens.
 */
sealed class RadioScreen(val route: String) {
    data object Stations : RadioScreen("stations")
    data object Starred : RadioScreen("starred")
    data object History : RadioScreen("history")
    data object Categories : RadioScreen("categories")
    data object Settings : RadioScreen("settings")
    data object PlayerFull : RadioScreen("player_full")
}

@Composable
fun RadioDroidNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = RadioScreen.Stations.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(RadioScreen.Stations.route) {
            // Destination for Browse / Stations list
        }
        composable(RadioScreen.Starred.route) {
            // Destination for Favorites screen
        }
        composable(RadioScreen.History.route) {
            // Destination for History screen
        }
        composable(RadioScreen.Categories.route) {
            // Destination for Categories / Tags screen
        }
        composable(RadioScreen.Settings.route) {
            // Destination for Settings screen
        }
        composable(RadioScreen.PlayerFull.route) {
            // Destination for Expanded Player screen
        }
    }
}
