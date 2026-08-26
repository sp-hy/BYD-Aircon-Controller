package com.byd.tripstats.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.byd.tripstats.ui.screens.DashboardScreen
import com.byd.tripstats.ui.screens.BatteryDegradationScreen
import com.byd.tripstats.ui.screens.ChargingHistoryScreen
import com.byd.tripstats.ui.screens.ChargingDetailScreen
import com.byd.tripstats.ui.screens.RoutesScreen
import com.byd.tripstats.ui.screens.TagsScreen
import com.byd.tripstats.ui.screens.SeasonalAnalysisScreen
import com.byd.tripstats.ui.screens.TripGoalsScreen
import com.byd.tripstats.ui.screens.LocalBackupScreen
import com.byd.tripstats.ui.screens.SettingsScreen
import com.byd.tripstats.ui.screens.TripDetailScreen
import com.byd.tripstats.ui.screens.TripHistoryScreen
import com.byd.tripstats.ui.viewmodel.DashboardViewModel

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object TripHistory : Screen("trip_history")
    object TripDetail : Screen("trip_detail/{tripId}") {
        fun createRoute(tripId: Long) = "trip_detail/$tripId"
    }
    object Settings : Screen("settings")
    object LocalBackup : Screen("local_backup")
    object ChargingHistory : Screen("charging_history")
    object ChargingDetail : Screen("charging_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "charging_detail/$sessionId"
    }
    object BatteryDegradation : Screen("battery_degradation")
    object SeasonalAnalysis : Screen("seasonal_analysis")
    object Routes : Screen("routes")
    object Tags : Screen("tags")
    object TripGoals : Screen("trip_goals")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToHistory = {
                    navController.navigate(Screen.TripHistory.route)
                },
                onNavigateToCharging = {
                    navController.navigate(Screen.ChargingHistory.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToBatteryDegradation = {
                    navController.navigate(Screen.BatteryDegradation.route)
                }
            )
        }
        
        composable(Screen.TripHistory.route) {
            TripHistoryScreen(
                viewModel = viewModel,
                onTripClick = { tripId ->
                    navController.navigate(Screen.TripDetail.createRoute(tripId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSeasonalAnalysis = {
                    navController.navigate(Screen.SeasonalAnalysis.route)
                },
                onNavigateToRoutes = {
                    navController.navigate(Screen.Routes.route)
                },
                onNavigateToTags = {
                    navController.navigate(Screen.Tags.route)
                }
            )
        }
        
        composable(
            route = Screen.TripDetail.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            TripDetailScreen(
                tripId = tripId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBackup = {
                    navController.navigate(Screen.LocalBackup.route)
                },
                onNavigateToTripGoals = {
                    navController.navigate(Screen.TripGoals.route)
                }
            )
        }

        composable(Screen.LocalBackup.route) {
            LocalBackupScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ChargingHistory.route) {
            ChargingHistoryScreen(
                viewModel = viewModel,
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.ChargingDetail.createRoute(sessionId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ChargingDetail.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            ChargingDetailScreen(
                sessionId = sessionId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BatteryDegradation.route) {
            BatteryDegradationScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.SeasonalAnalysis.route) {
            SeasonalAnalysisScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Routes.route) {
            RoutesScreen(
                viewModel = viewModel,
                onTripClick = { tripId ->
                    navController.navigate(Screen.TripDetail.createRoute(tripId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Tags.route) {
            TagsScreen(
                viewModel = viewModel,
                onOpenInHistory = { tagId ->
                    viewModel.setTagFilter(setOf(tagId))
                    navController.navigate(Screen.TripHistory.route) {
                        popUpTo(Screen.TripHistory.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TripGoals.route) {
            TripGoalsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
