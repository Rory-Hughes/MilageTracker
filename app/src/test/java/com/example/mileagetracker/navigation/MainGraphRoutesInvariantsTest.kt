package com.example.mileagetracker.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant tests for the navigation route maps. These guard against the silent-failure mode
 * where someone adds or renames a Screen and forgets to update the routing tables, which would
 * make the drawer highlight wrong or break a navigate() call without any compile-time error.
 */
class MainGraphRoutesInvariantsTest {

    private val managerDrawer = mainDrawerScreens(teamFeaturesEnabled = true, isManager = true)
    private val soloDrawer = mainDrawerScreens(teamFeaturesEnabled = false, isManager = false)

    @Test
    fun `every drawer screen resolves through routeToScreen`() {
        managerDrawer.forEach { screen ->
            assertEquals(
                "Drawer screen $screen has route ${screen.route} not registered in routeToScreen",
                screen,
                routeToScreen(screen.route),
            )
        }
    }

    @Test
    fun `every drawer screen route maps back to itself via routeToDrawerScreen`() {
        managerDrawer.forEach { screen ->
            assertEquals(
                "Drawer screen $screen should map to itself in routeToDrawerScreen",
                screen,
                routeToDrawerScreen(screen.route),
            )
        }
    }

    @Test
    fun `solo build excludes team dashboard`() {
        assertNull(soloDrawer.firstOrNull { it == Screen.TeamDashboard })
    }

    @Test
    fun `non-manager build excludes team dashboard even when feature flag is on`() {
        val nonManager = mainDrawerScreens(teamFeaturesEnabled = true, isManager = false)
        assertNull(nonManager.firstOrNull { it == Screen.TeamDashboard })
    }

    @Test
    fun `drawer order is dashboard then trips then manual trip`() {
        assertEquals(Screen.Dashboard, soloDrawer[0])
        assertEquals(Screen.Unclassified, soloDrawer[1])
        assertEquals(Screen.ManualTrip, soloDrawer[2])
    }

    @Test
    fun `routeToScreen returns null for null input`() {
        assertNull(routeToScreen(null))
    }

    @Test
    fun `routeToScreen returns null for unknown route`() {
        assertNull(routeToScreen("not-a-real-route"))
    }

    @Test
    fun `routeToDrawerScreen returns null for null input`() {
        assertNull(routeToDrawerScreen(null))
    }

    @Test
    fun `routeToDrawerScreen returns null for unrecognised routes`() {
        assertNull(routeToDrawerScreen("not-a-real-route"))
    }

    // --- Reports drawer rollup ---

    @Test
    fun `reports sub-routes all map to Reports drawer entry`() {
        val reportsRoutes = listOf(
            Screen.Reports.route,
            Screen.CraLogbook.route,
            Screen.Export.route,
            Screen.AnnualVehicleSummary.route,
            Screen.OdometerCapture.routeWithName,
            Screen.ReportHistory.route,
            Screen.SimplifiedLogbook.route,
            Screen.ExceptionsList.route,
        )
        reportsRoutes.forEach { route ->
            assertSame(
                "Route $route should highlight the Reports drawer entry",
                Screen.Reports,
                routeToDrawerScreen(route),
            )
        }
    }

    // --- Unclassified drawer rollup ---

    @Test
    fun `trip flow routes all map to Unclassified drawer entry`() {
        val tripRoutes = listOf(
            Screen.Unclassified.route,
            Screen.TripDetail.route,
            Screen.ManualTripEntry.routeWithArg,
            Screen.TripAudit.route,
            Screen.TripMap.route,
        )
        tripRoutes.forEach { route ->
            assertSame(
                "Route $route should highlight the Unclassified drawer entry",
                Screen.Unclassified,
                routeToDrawerScreen(route),
            )
        }
    }

    // --- Settings drawer rollup ---

    @Test
    fun `settings sub-routes all map to Settings drawer entry`() {
        val settingsRoutes = listOf(
            Screen.Settings.route,
            Screen.PrivacyDashboard.route,
            Screen.ClassificationRules.route,
            Screen.RuleForm.route,
        )
        settingsRoutes.forEach { route ->
            assertSame(
                "Route $route should highlight the Settings drawer entry",
                Screen.Settings,
                routeToDrawerScreen(route),
            )
        }
    }

    // --- Team drawer rollup ---

    @Test
    fun `team management routes all map to TeamDashboard drawer entry`() {
        val teamRoutes = listOf(
            Screen.TeamDashboard.route,
            Screen.ManagerAnalytics.route,
            Screen.ApprovalQueue.route,
            Screen.PeriodLock.route,
            Screen.DriverInvite.route,
            Screen.PolicyTemplates.route,
        )
        teamRoutes.forEach { route ->
            assertSame(
                "Route $route should highlight the TeamDashboard drawer entry",
                Screen.TeamDashboard,
                routeToDrawerScreen(route),
            )
        }
    }

    // --- Manual trip / tracking diagnostics rollup ---

    @Test
    fun `tracking diagnostics maps to ManualTrip drawer entry`() {
        assertSame(Screen.ManualTrip, routeToDrawerScreen(Screen.TrackingDiagnostics.route))
    }

    // --- Standalone flows that intentionally do NOT highlight a drawer entry ---

    @Test
    fun `expense and integration flows do not highlight any drawer entry`() {
        // These are reached via deep links from cards/snackbars rather than the drawer,
        // so leaving the drawer unselected is the intended behaviour.
        assertNull(routeToDrawerScreen(Screen.VehicleExpense.route))
        assertNull(routeToDrawerScreen(Screen.OcrReceipt.route))
        assertNull(routeToDrawerScreen(Screen.Integrations.route))
        assertNull(routeToDrawerScreen(Screen.IntegrationDetail.route))
    }

    // --- Targets reachable only via routeToScreen, not the drawer ---

    @Test
    fun `argument-bearing routes are registered for back-stack lookups`() {
        // These never appear in the drawer (they need args) but must be resolvable
        // so the TopAppBar can show their localised title from the back stack entry.
        listOf(
            Screen.TripDetail.route,
            Screen.TripAudit.route,
            Screen.TripMap.route,
            Screen.RuleForm.route,
            Screen.OdometerCapture.routeWithName,
            Screen.VehicleExpense.route,
            Screen.IntegrationDetail.route,
            Screen.ManualTripEntry.routeWithArg,
        ).forEach { route ->
            assertNotNull(
                "Argument-bearing route $route must be registered in routeToScreen",
                routeToScreen(route),
            )
        }
    }

    @Test
    fun `Onboarding and Login are not in the main drawer`() {
        // These are pre-shell routes, owned by the activity-level nav host, not the drawer.
        assertTrue(soloDrawer.none { it == Screen.Onboarding || it == Screen.Login })
        assertTrue(managerDrawer.none { it == Screen.Onboarding || it == Screen.Login })
    }
}
