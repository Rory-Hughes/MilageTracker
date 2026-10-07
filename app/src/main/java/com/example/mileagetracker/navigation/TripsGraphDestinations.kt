package com.example.mileagetracker.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.ui.audit.TripAuditScreen
import com.example.mileagetracker.ui.screen.ManualTripControlScreen
import com.example.mileagetracker.ui.screen.ManualTripEntryScreen
import com.example.mileagetracker.ui.screen.TrackingDiagnosticsScreen
import com.example.mileagetracker.ui.screen.TripDetailScreen
import com.example.mileagetracker.ui.screen.TripMapScreen
import com.example.mileagetracker.ui.screen.UnclassifiedScreen
import com.example.mileagetracker.ui.viewmodel.DriveViewModel
import com.example.mileagetracker.ui.viewmodel.ManualTripControlViewModel
import com.example.mileagetracker.ui.viewmodel.TripAuditViewModel

internal fun NavGraphBuilder.addUnclassifiedDestination(
    navController: NavHostController,
    driveViewModel: DriveViewModel,
) {
    composable(Screen.Unclassified.route) {
        UnclassifiedScreen(
            viewModel = driveViewModel,
            onTripTapped = { driveId ->
                navController.navigate(Screen.TripDetail.buildRoute(driveId))
            },
            onAddMissedTrip = {
                navController.navigate(Screen.ManualTripEntry.route)
            },
        )
    }
}

internal fun NavGraphBuilder.addManualTripControlDestination(
    manualTripViewModel: ManualTripControlViewModel,
) {
    composable(
        route = Screen.ManualTrip.route,
        deepLinks = listOf(
            navDeepLink { uriPattern = Screen.ManualTrip.DeepLink },
        ),
    ) {
        ManualTripControlScreen(viewModel = manualTripViewModel)
    }
}

internal fun NavGraphBuilder.addTrackingDiagnosticsDestination() {
    composable(Screen.TrackingDiagnostics.route) {
        TrackingDiagnosticsScreen()
    }
}

internal fun NavGraphBuilder.addTripDetailDestination(
    navController: NavHostController,
    activeVehicles: List<VehicleEntity>,
) {
    composable(
        route = Screen.TripDetail.route,
        arguments = listOf(
            navArgument(Screen.ARG_DRIVE_ID) {
                type = NavType.IntType
            },
        ),
    ) {
        TripDetailScreen(
            activeVehicles = activeVehicles,
            onNavigateBack = { navController.popBackStack() },
            onRepairTrip = { driveId ->
                navController.navigate(Screen.ManualTripEntry.buildRepairRoute(driveId))
            },
            onViewAuditHistory = { driveId ->
                navController.navigate(Screen.TripAudit.buildRoute(driveId))
            },
            onExpandMap = { driveId ->
                navController.navigate(Screen.TripMap.createRoute(driveId))
            },
        )
    }
}

internal fun NavGraphBuilder.addManualTripEntryDestination(
    navController: NavHostController,
    activeVehicles: List<VehicleEntity>,
) {
    composable(
        route = Screen.ManualTripEntry.routeWithArg,
        arguments = listOf(
            navArgument(Screen.ARG_DRIVE_ID) {
                type = NavType.IntType
                defaultValue = -1
            },
        ),
    ) {
        ManualTripEntryScreen(
            activeVehicles = activeVehicles,
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { _, _ ->
                navController.popBackStack(
                    route = Screen.Unclassified.route,
                    inclusive = false,
                )
            },
        )
    }
}

internal fun NavGraphBuilder.addTripAuditDestination(
    navController: NavHostController,
) {
    composable(
        route = Screen.TripAudit.route,
        arguments = listOf(
            navArgument(Screen.ARG_TRIP_ID) {
                type = NavType.IntType
            },
        ),
    ) {
        val auditViewModel: TripAuditViewModel = hiltViewModel()
        val auditUiState by auditViewModel.uiState.collectAsStateWithLifecycle()
        TripAuditScreen(
            uiState = auditUiState,
            onNavigateBack = { navController.popBackStack() },
            onRetry = { },
        )
    }
}

internal fun NavGraphBuilder.addTripMapDestination(
    navController: NavHostController,
) {
    composable(
        route = Screen.TripMap.route,
        arguments = listOf(
            navArgument(Screen.ARG_DRIVE_ID) {
                type = NavType.IntType
            },
        ),
    ) {
        TripMapScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
