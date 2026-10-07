package com.example.mileagetracker.ui.screen

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.navigation.Screen
import com.example.mileagetracker.navigation.mainDrawerScreens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests for the navigation drawer extracted from MainAppShell.
 *
 * MainAppShell itself calls hiltViewModel() five times inline so it can't be tested without
 * Hilt instrumentation. The drawer is the part with actual logic — selection, team-section
 * gating, click → navigate wiring — and is testable in isolation once promoted to internal.
 *
 * A real NavController + minimal stub NavHost is wired in because the drawer's click handler
 * calls navController.navigate(); the stub registers each drawer route as a Text node tagged
 * with its route so the test can assert which destination is active after a click.
 */
@RunWith(AndroidJUnit4::class)
class MainAppDrawerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        drawerScreens: List<Screen> = mainDrawerScreens(
            teamFeaturesEnabled = false,
            isManager = false,
        ),
        selectedDrawerScreen: Screen? = Screen.Dashboard,
        teamFeaturesEnabled: Boolean = false,
        isManager: Boolean = false,
    ) {
        composeRule.setContent {
            val navController = rememberNavController()
            val scope = rememberCoroutineScope()
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)

            StubDestinationsHost(navController = navController, routes = drawerScreens.map { it.route })

            MainAppDrawerContent(
                drawerScreens = drawerScreens,
                selectedDrawerScreen = selectedDrawerScreen,
                navController = navController,
                scope = scope,
                drawerState = drawerState,
                teamFeaturesEnabled = teamFeaturesEnabled,
                isManager = isManager,
            )
        }
    }

    @Test
    fun soloDrawer_rendersAllStandardItems() {
        setContent()

        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Trips").assertIsDisplayed()
        composeRule.onNodeWithText("Track a Trip").assertIsDisplayed()
        composeRule.onNodeWithText("Reports").assertIsDisplayed()
        composeRule.onNodeWithText("Vehicles").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun soloDrawer_doesNotShowManagementSectionLabel() {
        setContent(teamFeaturesEnabled = false, isManager = false)

        composeRule.onAllNodesWithText("Management").assertCountEquals(0)
    }

    @Test
    fun soloDrawer_doesNotShowTeamDashboard() {
        setContent(teamFeaturesEnabled = false, isManager = false)

        composeRule.onAllNodesWithText("Team Dashboard").assertCountEquals(0)
    }

    @Test
    fun managerDrawer_showsManagementSectionLabel() {
        setContent(
            drawerScreens = mainDrawerScreens(teamFeaturesEnabled = true, isManager = true),
            teamFeaturesEnabled = true,
            isManager = true,
        )

        composeRule.onNodeWithText("Management").assertIsDisplayed()
    }

    @Test
    fun managerDrawer_showsTeamDashboardItem() {
        setContent(
            drawerScreens = mainDrawerScreens(teamFeaturesEnabled = true, isManager = true),
            teamFeaturesEnabled = true,
            isManager = true,
        )

        composeRule.onNodeWithText("Team Dashboard").assertIsDisplayed()
    }

    @Test
    fun featureFlagOff_hidesManagementLabelEvenForManagers() {
        // Team features disabled at the build level overrides the manager role.
        setContent(teamFeaturesEnabled = false, isManager = true)

        composeRule.onAllNodesWithText("Management").assertCountEquals(0)
    }

    @Test
    fun selectedDrawerScreen_isMarkedSelected() {
        setContent(selectedDrawerScreen = Screen.Reports)

        composeRule.onNodeWithText("Reports").assertIsSelected()
    }

    @Test
    fun clickingDrawerItem_navigatesToThatRoute() {
        setContent()

        // Before click: start destination (Dashboard route) is composed.
        composeRule.onNodeWithTag("dest-${Screen.Dashboard.route}").assertIsDisplayed()

        composeRule.onNodeWithText("Reports").performClick()
        composeRule.waitForIdle()

        // After click: NavHost re-composes the Reports destination.
        composeRule.onNodeWithTag("dest-${Screen.Reports.route}").assertIsDisplayed()
    }

    @Test
    fun clickingMultipleDrawerItems_navigatesEachTime() {
        setContent()

        composeRule.onNodeWithText("Vehicles").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dest-${Screen.VehicleManagement.route}").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dest-${Screen.Settings.route}").assertIsDisplayed()
    }

    @Test
    fun noSelectedScreen_stillRendersAllItems() {
        setContent(selectedDrawerScreen = null)

        // Negative-asserting "no item is selected" needs test tags on each item; at minimum
        // verify the drawer composes without throwing when nothing is highlighted.
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Reports").assertIsDisplayed()
    }
}

@Composable
private fun StubDestinationsHost(
    navController: NavHostController,
    routes: List<String>,
) {
    NavHost(navController = navController, startDestination = routes.first()) {
        routes.forEach { route ->
            composable(route) {
                Text(text = route, modifier = Modifier.testTag("dest-$route"))
            }
        }
    }
}
