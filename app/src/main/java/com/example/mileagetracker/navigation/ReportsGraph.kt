package com.example.mileagetracker.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.ui.viewmodel.ExceptionsListViewModel

fun NavGraphBuilder.addReportsGraph(
    navController: NavHostController,
    activeVehicles: List<VehicleEntity>,
    exceptionsViewModel: ExceptionsListViewModel,
    accountingIntegrationsEnabled: Boolean,
) {
    navigation(
        route = MainGraphRoutes.REPORTS,
        startDestination = Screen.Reports.route,
    ) {
        addReportsHomeDestination(
            navController = navController,
            activeVehicles = activeVehicles,
            accountingIntegrationsEnabled = accountingIntegrationsEnabled,
        )
        addExceptionsListDestination(
            navController = navController,
            exceptionsViewModel = exceptionsViewModel,
        )
        addCraLogbookDestination(navController = navController)
        addExportDestination(navController = navController)
        addAnnualSummaryDestination(
            navController = navController,
            activeVehicles = activeVehicles,
        )
        addOdometerCaptureDestination(navController = navController)
        addIntegrationsDestinations(
            navController = navController,
            accountingIntegrationsEnabled = accountingIntegrationsEnabled,
        )
        addReportHistoryAndSimplifiedDestinations(navController = navController)
    }
}
