package com.example.mileagetracker.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainGraphRoutesTest {

    @Test
    fun `mainDrawerScreens includes team dashboard for managers when enabled`() {
        val screens = mainDrawerScreens(teamFeaturesEnabled = true, isManager = true)

        assertEquals(Screen.TeamDashboard, screens.last())
    }

    @Test
    fun `mainDrawerScreens omits team dashboard when disabled`() {
        val screens = mainDrawerScreens(teamFeaturesEnabled = false, isManager = true)

        assertNull(screens.firstOrNull { it == Screen.TeamDashboard })
    }

    @Test
    fun `routeToScreen maps report history route`() {
        assertEquals(Screen.ReportHistory, routeToScreen(Screen.ReportHistory.route))
    }

    @Test
    fun `routeToDrawerScreen maps privacy dashboard to settings`() {
        assertEquals(Screen.Settings, routeToDrawerScreen(Screen.PrivacyDashboard.route))
    }

    @Test
    fun `routeToDrawerScreen maps trip detail to unclassified`() {
        assertEquals(Screen.Unclassified, routeToDrawerScreen(Screen.TripDetail.route))
    }

    @Test
    fun `routeToDrawerScreen leaves ambiguous expense flow unselected`() {
        assertNull(routeToDrawerScreen(Screen.VehicleExpense.route))
    }
}
