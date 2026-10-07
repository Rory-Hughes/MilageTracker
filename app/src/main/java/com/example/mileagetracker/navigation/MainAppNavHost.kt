package com.example.mileagetracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.ui.viewmodel.AuthViewModel
import com.example.mileagetracker.ui.viewmodel.DriveViewModel
import com.example.mileagetracker.ui.viewmodel.ExceptionsListViewModel
import com.example.mileagetracker.ui.viewmodel.ManualTripControlViewModel

@Composable
fun MainAppNavHost(
    navController: NavHostController,
    driveViewModel: DriveViewModel,
    manualTripViewModel: ManualTripControlViewModel,
    activeVehicles: List<VehicleEntity>,
    exceptionsViewModel: ExceptionsListViewModel,
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    accountingIntegrationsEnabled: Boolean,
    teamFeaturesEnabled: Boolean,
    currentTeamMember: TeamMember?,
    managedDrivers: List<TeamMember>,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = MainGraphRoutes.DASHBOARD,
        modifier = modifier,
    ) {
        addDashboardGraph(
            navController = navController,
            driveViewModel = driveViewModel,
        )
        addTripsGraph(
            navController = navController,
            driveViewModel = driveViewModel,
            manualTripViewModel = manualTripViewModel,
            activeVehicles = activeVehicles,
        )
        addReportsGraph(
            navController = navController,
            activeVehicles = activeVehicles,
            exceptionsViewModel = exceptionsViewModel,
            accountingIntegrationsEnabled = accountingIntegrationsEnabled,
        )
        addExpenseDestinations(navController = navController)
        addVehicleGraph(navController = navController)
        addAccountGraph(
            navController = navController,
            authViewModel = authViewModel,
            onSignOut = onSignOut,
            accountingIntegrationsEnabled = accountingIntegrationsEnabled,
        )
        addTeamGraph(
            navController = navController,
            teamFeaturesEnabled = teamFeaturesEnabled,
            currentTeamMember = currentTeamMember,
            managedDrivers = managedDrivers,
        )
    }
}
