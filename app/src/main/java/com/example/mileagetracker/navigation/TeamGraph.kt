package com.example.mileagetracker.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.example.mileagetracker.domain.model.TeamMember

fun NavGraphBuilder.addTeamGraph(
    navController: NavHostController,
    teamFeaturesEnabled: Boolean,
    currentTeamMember: TeamMember?,
    managedDrivers: List<TeamMember>,
) {
    navigation(
        route = MainGraphRoutes.TEAM,
        startDestination = Screen.TeamDashboard.route,
    ) {
        addTeamDashboardDestination(
            navController = navController,
            teamFeaturesEnabled = teamFeaturesEnabled,
            currentTeamMember = currentTeamMember,
            managedDrivers = managedDrivers,
        )
        addManagerAnalyticsDestination(
            navController = navController,
            teamFeaturesEnabled = teamFeaturesEnabled,
            currentTeamMember = currentTeamMember,
            managedDrivers = managedDrivers,
        )
        addApprovalQueueDestination(
            teamFeaturesEnabled = teamFeaturesEnabled,
            currentTeamMember = currentTeamMember,
        )
        addPeriodLockDestination(
            teamFeaturesEnabled = teamFeaturesEnabled,
            currentTeamMember = currentTeamMember,
        )
        addDriverInviteDestination(
            navController = navController,
            teamFeaturesEnabled = teamFeaturesEnabled,
        )
        addPolicyTemplatesDestination(
            navController = navController,
            teamFeaturesEnabled = teamFeaturesEnabled,
        )
    }
}
