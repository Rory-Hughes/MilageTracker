package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mileagetracker.BuildConfig
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.navigation.MainAppNavHost
import com.example.mileagetracker.navigation.Screen
import com.example.mileagetracker.navigation.localizedTitle
import com.example.mileagetracker.navigation.mainDrawerScreens
import com.example.mileagetracker.navigation.showsMainTopBar
import com.example.mileagetracker.navigation.routeToDrawerScreen
import com.example.mileagetracker.navigation.routeToScreen
import com.example.mileagetracker.ui.team.TeamSessionViewModel
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.AuthViewModel
import com.example.mileagetracker.ui.viewmodel.DriveViewModel
import com.example.mileagetracker.ui.viewmodel.ExceptionsListViewModel
import com.example.mileagetracker.ui.viewmodel.ManualTripControlViewModel
import com.example.mileagetracker.ui.viewmodel.VehicleManagementViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
) {
    val driveViewModel: DriveViewModel = hiltViewModel()
    val manualTripViewModel: ManualTripControlViewModel = hiltViewModel()
    val vehicleViewModel: VehicleManagementViewModel = hiltViewModel()
    val exceptionsViewModel: ExceptionsListViewModel = hiltViewModel()
    val teamSessionViewModel: TeamSessionViewModel = hiltViewModel()

    val currentTeamMember by teamSessionViewModel.currentMember.collectAsStateWithLifecycle()
    val managedDrivers by teamSessionViewModel.managedDrivers.collectAsStateWithLifecycle()
    val activeVehicles by vehicleViewModel.activeVehicles.collectAsStateWithLifecycle()

    val teamFeaturesEnabled = BuildConfig.FEATURE_TEAM
    val accountingIntegrationsEnabled = BuildConfig.FEATURE_ACCOUNTING_INTEGRATIONS
    val isManager = currentTeamMember?.role.let {
        it == TeamRole.MANAGER || it == TeamRole.ADMIN
    }

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerScreens = mainDrawerScreens(teamFeaturesEnabled, isManager)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = routeToScreen(currentRoute) ?: Screen.Dashboard
    val selectedDrawerScreen = routeToDrawerScreen(currentRoute)

    MainAppShellLayout(
        drawerState = drawerState,
        drawerScreens = drawerScreens,
        selectedDrawerScreen = selectedDrawerScreen,
        currentScreen = currentScreen,
        navController = navController,
        scope = scope,
        teamFeaturesEnabled = teamFeaturesEnabled,
        isManager = isManager,
        driveViewModel = driveViewModel,
        manualTripViewModel = manualTripViewModel,
        activeVehicles = activeVehicles,
        exceptionsViewModel = exceptionsViewModel,
        authViewModel = authViewModel,
        onSignOut = onSignOut,
        accountingIntegrationsEnabled = accountingIntegrationsEnabled,
        currentTeamMember = currentTeamMember,
        managedDrivers = managedDrivers,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppShellLayout(
    drawerState: androidx.compose.material3.DrawerState,
    drawerScreens: List<Screen>,
    selectedDrawerScreen: Screen?,
    currentScreen: Screen,
    navController: NavHostController,
    scope: CoroutineScope,
    teamFeaturesEnabled: Boolean,
    isManager: Boolean,
    driveViewModel: DriveViewModel,
    manualTripViewModel: ManualTripControlViewModel,
    activeVehicles: List<VehicleEntity>,
    exceptionsViewModel: ExceptionsListViewModel,
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    accountingIntegrationsEnabled: Boolean,
    currentTeamMember: TeamMember?,
    managedDrivers: List<TeamMember>,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainAppDrawerContent(
                drawerScreens = drawerScreens,
                selectedDrawerScreen = selectedDrawerScreen,
                navController = navController,
                scope = scope,
                drawerState = drawerState,
                teamFeaturesEnabled = teamFeaturesEnabled,
                isManager = isManager,
            )
        },
    ) {
        MainAppScaffold(
            currentScreen = currentScreen,
            scope = scope,
            drawerState = drawerState,
            navController = navController,
            driveViewModel = driveViewModel,
            manualTripViewModel = manualTripViewModel,
            activeVehicles = activeVehicles,
            exceptionsViewModel = exceptionsViewModel,
            authViewModel = authViewModel,
            onSignOut = onSignOut,
            accountingIntegrationsEnabled = accountingIntegrationsEnabled,
            teamFeaturesEnabled = teamFeaturesEnabled,
            currentTeamMember = currentTeamMember,
            managedDrivers = managedDrivers,
        )
    }
}

@Composable
internal fun MainAppDrawerContent(
    drawerScreens: List<Screen>,
    selectedDrawerScreen: Screen?,
    navController: NavHostController,
    scope: CoroutineScope,
    drawerState: androidx.compose.material3.DrawerState,
    teamFeaturesEnabled: Boolean,
    isManager: Boolean,
) {
    ModalDrawerSheet {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(Spacing.lg),
            style = MaterialTheme.typography.headlineMedium,
        )
        HorizontalDivider()

        if (teamFeaturesEnabled && isManager) {
            ManagementSectionLabel()
        }

        drawerScreens.forEach { screen ->
            MainAppDrawerItem(
                screen = screen,
                selected = screen == selectedDrawerScreen,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                    scope.launch { drawerState.close() }
                },
            )
        }
    }
}

@Composable
private fun ManagementSectionLabel() {
    Text(
        text = "Management",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun MainAppDrawerItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            if (screen == Screen.TeamDashboard) {
                Icon(
                    imageVector = Icons.Filled.Dashboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = screen.localizedTitle(),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            } else {
                Text(screen.localizedTitle())
            }
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffold(
    currentScreen: Screen,
    scope: CoroutineScope,
    drawerState: androidx.compose.material3.DrawerState,
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
) {
    Scaffold(
        topBar = {
            if (currentScreen.showsMainTopBar) {
                TopAppBar(
                    title = { Text(currentScreen.localizedTitle()) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.action_open_menu),
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        MainAppNavHost(
            navController = navController,
            driveViewModel = driveViewModel,
            manualTripViewModel = manualTripViewModel,
            activeVehicles = activeVehicles,
            exceptionsViewModel = exceptionsViewModel,
            authViewModel = authViewModel,
            onSignOut = onSignOut,
            accountingIntegrationsEnabled = accountingIntegrationsEnabled,
            teamFeaturesEnabled = teamFeaturesEnabled,
            currentTeamMember = currentTeamMember,
            managedDrivers = managedDrivers,
            modifier = Modifier.padding(paddingValues),
        )
    }
}
