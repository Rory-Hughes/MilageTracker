package com.example.mileagetracker.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.mileagetracker.ui.screen.DashboardScreen
import com.example.mileagetracker.ui.viewmodel.DriveViewModel

fun NavGraphBuilder.addDashboardGraph(
    navController: NavHostController,
    driveViewModel: DriveViewModel,
) {
    navigation(
        route = MainGraphRoutes.DASHBOARD,
        startDestination = Screen.Dashboard.route,
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = driveViewModel,
                onNavigateToUnclassified = {
                    navController.navigate(Screen.Unclassified.route)
                },
                onNavigateToDiagnostics = {
                    navController.navigate(Screen.TrackingDiagnostics.route)
                },
                onNavigateToManualTrip = {
                    navController.navigate(Screen.ManualTrip.route) {
                        launchSingleTop = true
                    }
                },
                onTripTapped = { driveId ->
                    navController.navigate(Screen.TripDetail.buildRoute(driveId))
                },
            )
        }
    }
}
