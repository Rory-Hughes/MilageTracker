package com.example.mileagetracker.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.ExceptionType
import com.example.mileagetracker.domain.model.IntegrationProvider
import com.example.mileagetracker.domain.model.ReportReadiness
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.ui.integrations.IntegrationDetailScreen
import com.example.mileagetracker.ui.integrations.IntegrationsScreen
import com.example.mileagetracker.ui.screen.AnnualVehicleSummaryScreen
import com.example.mileagetracker.ui.screen.CraLogbookScreen
import com.example.mileagetracker.ui.screen.ExceptionsListScreen
import com.example.mileagetracker.ui.screen.ExportScreen
import com.example.mileagetracker.ui.screen.OdometerCaptureScreen
import com.example.mileagetracker.ui.screen.ReportHistoryScreen
import com.example.mileagetracker.ui.screen.ReportsScreen
import com.example.mileagetracker.ui.screen.SimplifiedLogbookGuidanceScreen
import com.example.mileagetracker.ui.viewmodel.ExceptionsListViewModel
import com.example.mileagetracker.ui.viewmodel.ReportsUiState
import com.example.mileagetracker.ui.viewmodel.ReportsViewModel

internal fun NavGraphBuilder.addReportsHomeDestination(
    navController: NavHostController,
    activeVehicles: List<VehicleEntity>,
    accountingIntegrationsEnabled: Boolean,
) {
    composable(Screen.Reports.route) {
        val reportsViewModel: ReportsViewModel = hiltViewModel()
        ReportsScreen(
            activeVehicles = activeVehicles,
            onOpenLogbook = { navController.navigate(Screen.CraLogbook.route) },
            onOpenExport = { navController.navigate(Screen.Export.route) },
            onOpenHistory = { navController.navigate(Screen.ReportHistory.route) },
            onOpenExpenses = { vehicleId, year ->
                navController.navigate(Screen.VehicleExpense.buildRoute(vehicleId, year))
            },
            onOpenIntegrations = { navController.navigate(Screen.Integrations.route) },
            showIntegrations = accountingIntegrationsEnabled,
            onOpenExceptions = { navController.navigate(Screen.ExceptionsList.route) },
            viewModel = reportsViewModel,
        )
    }
}

internal fun NavGraphBuilder.addExceptionsListDestination(
    navController: NavHostController,
    exceptionsViewModel: ExceptionsListViewModel,
) {
    composable(Screen.ExceptionsList.route) { backStackEntry ->
        val reportsEntry = remember(backStackEntry) {
            navController.getBackStackEntry(Screen.Reports.route)
        }
        val reportsViewModel: ReportsViewModel = hiltViewModel(reportsEntry)
        val reportsUiState by reportsViewModel.uiState.collectAsStateWithLifecycle()
        val currentExceptions by exceptionsViewModel.exceptions.collectAsStateWithLifecycle()
        val screenData = remember(reportsUiState, currentExceptions) {
            buildExceptionsListScreenData(
                reportsUiState = reportsUiState,
                exceptions = currentExceptions,
            )
        }
        ExceptionsListScreen(
            incompleteTrips = screenData.incompleteTrips,
            exceptions = screenData.exceptions,
            onTripTapped = { driveId ->
                navController.navigate(Screen.TripDetail.buildRoute(driveId))
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

internal fun buildExceptionsListScreenData(
    reportsUiState: ReportsUiState,
    exceptions: List<TripException>,
): ExceptionsListScreenData {
    val successState = reportsUiState as? ReportsUiState.Success
    val incompleteTrips = successState
        ?.readinessList
        ?.filter { !it.isReady }
        .orEmpty()
    val reportTripIds = successState?.reportTripIds.orEmpty()
    val filteredExceptions = exceptions.filter { exception ->
        exception.tripId in reportTripIds &&
            exception.exceptionType != ExceptionType.INCOMPLETE_DATA
    }
    return ExceptionsListScreenData(
        incompleteTrips = incompleteTrips,
        exceptions = filteredExceptions,
    )
}

internal data class ExceptionsListScreenData(
    val incompleteTrips: List<ReportReadiness>,
    val exceptions: List<TripException>,
)

internal fun NavGraphBuilder.addCraLogbookDestination(navController: NavHostController) {
    composable(Screen.CraLogbook.route) {
        CraLogbookScreen(
            onNavigateBack = { navController.popBackStack() },
            onTripTapped = { driveId ->
                navController.navigate(Screen.TripDetail.buildRoute(driveId))
            },
            onOpenHistory = { navController.navigate(Screen.ReportHistory.route) },
            onOpenSimplifiedLogbook = {
                navController.navigate(Screen.SimplifiedLogbook.route)
            },
            onOpenAnnualSummary = {
                navController.navigate(Screen.AnnualVehicleSummary.route)
            },
        )
    }
}

internal fun NavGraphBuilder.addExportDestination(navController: NavHostController) {
    composable(Screen.Export.route) {
        ExportScreen(onNavigateBack = { navController.popBackStack() })
    }
}

internal fun NavGraphBuilder.addAnnualSummaryDestination(
    navController: NavHostController,
    activeVehicles: List<VehicleEntity>,
) {
    composable(Screen.AnnualVehicleSummary.route) {
        AnnualVehicleSummaryScreen(
            activeVehicles = activeVehicles,
            onNavigateBack = { navController.popBackStack() },
            onOpenHistory = { navController.navigate(Screen.ReportHistory.route) },
            onOpenOdometer = { vehicleId ->
                navController.navigate(
                    Screen.OdometerCapture.buildRoute(
                        vehicleId = vehicleId,
                        vehicleName = activeVehicles.vehicleNameFor(vehicleId),
                    ),
                )
            },
        )
    }
}

internal fun NavGraphBuilder.addOdometerCaptureDestination(navController: NavHostController) {
    composable(
        route = Screen.OdometerCapture.routeWithName,
        arguments = listOf(
            navArgument(Screen.ARG_VEHICLE_ID) { type = NavType.IntType },
            navArgument(Screen.ARG_VEHICLE_NAME) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStackEntry ->
        OdometerCaptureScreen(
            vehicleName = backStackEntry.arguments
                ?.getString(Screen.ARG_VEHICLE_NAME)
                .orEmpty(),
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

internal fun NavGraphBuilder.addIntegrationsDestinations(
    navController: NavHostController,
    accountingIntegrationsEnabled: Boolean,
) {
    composable(Screen.Integrations.route) {
        if (accountingIntegrationsEnabled) {
            IntegrationsScreen(
                onNavigateToDetail = { provider ->
                    navController.navigate(Screen.IntegrationDetail.buildRoute(provider))
                },
            )
        } else {
            DisabledFeatureMessage("Accounting integrations")
        }
    }

    composable(
        route = Screen.IntegrationDetail.route,
        arguments = listOf(
            navArgument(Screen.ARG_PROVIDER) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val providerName = backStackEntry.arguments
            ?.getString(Screen.ARG_PROVIDER)
            .orEmpty()
        val provider = IntegrationProvider.entries
            .firstOrNull { it.name == providerName }
            ?: IntegrationProvider.PAYROLL_CSV
        if (accountingIntegrationsEnabled) {
            IntegrationDetailScreen(
                provider = provider,
                onNavigateBack = { navController.popBackStack() },
            )
        } else {
            DisabledFeatureMessage("Accounting integrations")
        }
    }
}

internal fun NavGraphBuilder.addReportHistoryAndSimplifiedDestinations(
    navController: NavHostController,
) {
    composable(Screen.ReportHistory.route) {
        ReportHistoryScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(Screen.SimplifiedLogbook.route) {
        SimplifiedLogbookGuidanceScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

private fun List<VehicleEntity>.vehicleNameFor(vehicleId: Int): String {
    val vehicle = firstOrNull { it.id == vehicleId } ?: return ""
    return vehicle.displayName.ifBlank {
        listOfNotNull(vehicle.make, vehicle.model).joinToString(" ")
    }
}
