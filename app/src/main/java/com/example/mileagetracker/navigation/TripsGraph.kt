package com.example.mileagetracker.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.ui.viewmodel.DriveViewModel
import com.example.mileagetracker.ui.viewmodel.ManualTripControlViewModel

fun NavGraphBuilder.addTripsGraph(
    navController: NavHostController,
    driveViewModel: DriveViewModel,
    manualTripViewModel: ManualTripControlViewModel,
    activeVehicles: List<VehicleEntity>,
) {
    navigation(
        route = MainGraphRoutes.TRIPS,
        startDestination = Screen.Unclassified.route,
    ) {
        addUnclassifiedDestination(
            navController = navController,
            driveViewModel = driveViewModel,
        )
        addManualTripControlDestination(manualTripViewModel = manualTripViewModel)
        addTrackingDiagnosticsDestination()
        addTripDetailDestination(
            navController = navController,
            activeVehicles = activeVehicles,
        )
        addManualTripEntryDestination(
            navController = navController,
            activeVehicles = activeVehicles,
        )
        addTripAuditDestination(navController = navController)
        addTripMapDestination(navController = navController)
    }
}
