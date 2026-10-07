package com.example.mileagetracker.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.mileagetracker.navigation.Screen.OdometerCapture
import com.example.mileagetracker.ui.screen.VehicleManagementScreen

fun NavGraphBuilder.addVehicleGraph(navController: NavHostController) {
    navigation(
        route = MainGraphRoutes.VEHICLES,
        startDestination = Screen.VehicleManagement.route,
    ) {
        composable(Screen.VehicleManagement.route) {
            VehicleManagementScreen(
                onRecordOdometer = { vehicle ->
                    navController.navigate(
                        OdometerCapture.buildRoute(
                            vehicleId = vehicle.id,
                            vehicleName = vehicle.displayName,
                        ),
                    )
                },
            )
        }
    }
}
