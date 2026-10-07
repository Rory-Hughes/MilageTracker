package com.example.mileagetracker.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.ui.policy.PolicyTemplateScreen
import com.example.mileagetracker.ui.team.ApprovalQueueScreen
import com.example.mileagetracker.ui.team.DriverInviteScreen
import com.example.mileagetracker.ui.team.ManagerAnalyticsScreen
import com.example.mileagetracker.ui.team.PeriodLockScreen
import com.example.mileagetracker.ui.team.TeamDashboardScreen

internal fun NavGraphBuilder.addTeamDashboardDestination(
    navController: NavHostController,
    teamFeaturesEnabled: Boolean,
    currentTeamMember: TeamMember?,
    managedDrivers: List<TeamMember>,
) {
    composable(Screen.TeamDashboard.route) {
        val member = currentTeamMember
        if (teamFeaturesEnabled && member != null) {
            TeamDashboardScreen(
                currentUser = member,
                managedDrivers = managedDrivers,
                onNavigateToApprovalQueue = {
                    navController.navigate(Screen.ApprovalQueue.route)
                },
                onNavigateToAnalytics = {
                    navController.navigate(Screen.ManagerAnalytics.route)
                },
                onNavigateToPeriodLock = {
                    navController.navigate(Screen.PeriodLock.route)
                },
            )
        } else {
            DisabledFeatureMessage("Team features")
        }
    }
}

internal fun NavGraphBuilder.addManagerAnalyticsDestination(
    navController: NavHostController,
    teamFeaturesEnabled: Boolean,
    currentTeamMember: TeamMember?,
    managedDrivers: List<TeamMember>,
) {
    composable(Screen.ManagerAnalytics.route) {
        val member = currentTeamMember
        if (teamFeaturesEnabled && member != null) {
            ManagerAnalyticsScreen(
                currentUser = member,
                managedDrivers = managedDrivers,
                onNavigateUp = { navController.popBackStack() },
            )
        } else {
            DisabledFeatureMessage("Team analytics")
        }
    }
}

internal fun NavGraphBuilder.addApprovalQueueDestination(
    teamFeaturesEnabled: Boolean,
    currentTeamMember: TeamMember?,
) {
    composable(Screen.ApprovalQueue.route) {
        if (teamFeaturesEnabled) {
            ApprovalQueueScreen(
                reviewerUserId = currentTeamMember?.userId?.toLongOrNull() ?: 0L,
            )
        } else {
            DisabledFeatureMessage("Team approvals")
        }
    }
}

internal fun NavGraphBuilder.addPeriodLockDestination(
    teamFeaturesEnabled: Boolean,
    currentTeamMember: TeamMember?,
) {
    composable(Screen.PeriodLock.route) {
        if (teamFeaturesEnabled) {
            PeriodLockScreen(
                managerUserId = currentTeamMember?.userId?.toLongOrNull() ?: 0L,
            )
        } else {
            DisabledFeatureMessage("Team period locks")
        }
    }
}

internal fun NavGraphBuilder.addDriverInviteDestination(
    navController: NavHostController,
    teamFeaturesEnabled: Boolean,
) {
    composable(Screen.DriverInvite.route) {
        if (teamFeaturesEnabled) {
            DriverInviteScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        } else {
            DisabledFeatureMessage("Team invites")
        }
    }
}

internal fun NavGraphBuilder.addPolicyTemplatesDestination(
    navController: NavHostController,
    teamFeaturesEnabled: Boolean,
) {
    composable(Screen.PolicyTemplates.route) {
        if (teamFeaturesEnabled) {
            PolicyTemplateScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        } else {
            DisabledFeatureMessage("Team policy templates")
        }
    }
}
