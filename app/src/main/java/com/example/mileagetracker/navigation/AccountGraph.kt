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
import androidx.navigation.navigation
import com.example.mileagetracker.ui.screen.ClassificationRulesScreen
import com.example.mileagetracker.ui.screen.PrivacyDashboardScreen
import com.example.mileagetracker.ui.screen.ProfileScreen
import com.example.mileagetracker.ui.screen.RuleFormScreen
import com.example.mileagetracker.ui.screen.SettingsScreen
import com.example.mileagetracker.ui.screen.SettingsScreenActions
import com.example.mileagetracker.ui.viewmodel.AuthViewModel
import com.example.mileagetracker.ui.viewmodel.ClassificationRulesViewModel

fun NavGraphBuilder.addAccountGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    accountingIntegrationsEnabled: Boolean,
) {
    navigation(
        route = MainGraphRoutes.ACCOUNT,
        startDestination = Screen.Profile.route,
    ) {
        addProfileDestination(authViewModel = authViewModel, onSignOut = onSignOut)
        addSettingsDestination(
            navController = navController,
            accountingIntegrationsEnabled = accountingIntegrationsEnabled,
        )
        addPrivacyDashboardDestination()
        addClassificationRulesDestination(navController = navController)
        addRuleFormDestination(navController = navController)
    }
}

private fun NavGraphBuilder.addProfileDestination(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
) {
    composable(Screen.Profile.route) {
        ProfileScreen(
            authViewModel = authViewModel,
            onSignOut = onSignOut,
        )
    }
}

private fun NavGraphBuilder.addSettingsDestination(
    navController: NavHostController,
    accountingIntegrationsEnabled: Boolean,
) {
    composable(Screen.Settings.route) {
        SettingsScreen(
            actions = SettingsScreenActions(
                onNavigateToClassificationRules = {
                    navController.navigate(Screen.ClassificationRules.route)
                },
                onNavigateToExpenses = { vehicleId, year ->
                    navController.navigate(Screen.VehicleExpense.buildRoute(vehicleId, year))
                },
                onNavigateToPrivacyDashboard = {
                    navController.navigate(Screen.PrivacyDashboard.route)
                },
                onNavigateToIntegrations = {
                    navController.navigate(Screen.Integrations.route)
                },
                showIntegrations = accountingIntegrationsEnabled,
            ),
        )
    }
}

private fun NavGraphBuilder.addPrivacyDashboardDestination() {
    composable(Screen.PrivacyDashboard.route) {
        PrivacyDashboardScreen()
    }
}

private fun NavGraphBuilder.addClassificationRulesDestination(
    navController: NavHostController,
) {
    composable(Screen.ClassificationRules.route) {
        val rulesViewModel: ClassificationRulesViewModel = hiltViewModel()
        ClassificationRulesScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreateRule = {
                navController.navigate(Screen.RuleForm.createRoute)
            },
            onEditRule = { rule ->
                navController.navigate(Screen.RuleForm.buildEditRoute(rule.id))
            },
            viewModel = rulesViewModel,
        )
    }
}

private fun NavGraphBuilder.addRuleFormDestination(
    navController: NavHostController,
) {
    composable(
        route = Screen.RuleForm.route,
        arguments = listOf(
            navArgument(Screen.ARG_RULE_ID) {
                type = NavType.IntType
                defaultValue = -1
            },
        ),
    ) { backStackEntry ->
        val rulesEntry = remember(backStackEntry) {
            navController.getBackStackEntry(Screen.ClassificationRules.route)
        }
        val rulesViewModel: ClassificationRulesViewModel = hiltViewModel(rulesEntry)
        val ruleId = backStackEntry.arguments?.getInt(Screen.ARG_RULE_ID) ?: -1
        val uiState by rulesViewModel.uiState.collectAsStateWithLifecycle()
        val ruleToEdit = if (ruleId > 0) {
            uiState.userRules.firstOrNull { it.id == ruleId }
        } else {
            null
        }
        RuleFormScreen(
            ruleToEdit = ruleToEdit,
            onNavigateBack = { navController.popBackStack() },
            viewModel = rulesViewModel,
        )
    }
}
