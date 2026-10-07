package com.example.mileagetracker.navigation

import com.example.mileagetracker.domain.model.IntegrationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {

    @Test
    fun `TripDetail buildRoute returns correct string`() {
        assertEquals("trip_detail/42", Screen.TripDetail.buildRoute(42))
    }

    @Test
    fun `ManualTripEntry buildRepairRoute returns correct string`() {
        assertEquals("manual_trip_entry?driveId=7", Screen.ManualTripEntry.buildRepairRoute(7))
    }

    @Test
    fun `RuleForm buildEditRoute returns correct string`() {
        assertEquals("rule_form/5", Screen.RuleForm.buildEditRoute(5))
    }

    @Test
    fun `OdometerCapture buildRoute encodes vehicle name`() {
        // vehicleName with space should be encoded
        val route = Screen.OdometerCapture.buildRoute(vehicleId = 1, vehicleName = "My Car")
        assertEquals("odometer_capture/1?vehicleName=My+Car", route)
    }

    @Test
    fun `TripAudit buildRoute returns correct string`() {
        assertEquals("trip_audit/123", Screen.TripAudit.buildRoute(123))
    }

    @Test
    fun `TripMap createRoute returns correct string`() {
        assertEquals("trip_map/99", Screen.TripMap.createRoute(99))
    }

    @Test
    fun `Export route is stable`() {
        assertEquals("export", Screen.Export.route)
    }

    @Test
    fun `RuleForm createRoute uses -1 sentinel for create mode`() {
        assertEquals("rule_form/-1", Screen.RuleForm.createRoute)
    }

    @Test
    fun `VehicleExpense buildRoute defaults year to current calendar year`() {
        val route = Screen.VehicleExpense.buildRoute(vehicleId = 9)
        val currentYear = java.time.LocalDate.now().year
        assertEquals("vehicle_expense/9?year=$currentYear", route)
    }

    @Test
    fun `VehicleExpense buildRoute respects explicit year`() {
        assertEquals(
            "vehicle_expense/9?year=2024",
            Screen.VehicleExpense.buildRoute(vehicleId = 9, year = 2024),
        )
    }

    @Test
    fun `IntegrationDetail buildRoute encodes provider name`() {
        assertEquals(
            "integration_detail/QUICKBOOKS",
            Screen.IntegrationDetail.buildRoute(IntegrationProvider.QUICKBOOKS),
        )
        assertEquals(
            "integration_detail/PAYROLL_CSV",
            Screen.IntegrationDetail.buildRoute(IntegrationProvider.PAYROLL_CSV),
        )
    }

    @Test
    fun `OdometerCapture buildRoute handles empty vehicle name`() {
        assertEquals(
            "odometer_capture/1?vehicleName=",
            Screen.OdometerCapture.buildRoute(vehicleId = 1),
        )
    }

    @Test
    fun `OdometerCapture buildRoute encodes special characters in vehicle name`() {
        val route = Screen.OdometerCapture.buildRoute(vehicleId = 1, vehicleName = "Rory's #1 Truck")
        // URLEncoder encodes ' as %27, # as %23, spaces as +
        assertTrue("Route should encode apostrophes: $route", route.contains("%27"))
        assertTrue("Route should encode hash: $route", route.contains("%23"))
    }

    @Test
    fun `ManualTrip deep link matches expected scheme`() {
        assertEquals("mileagetracker://manual_trip", Screen.ManualTrip.DeepLink)
    }

    @Test
    fun `pre-shell screens have stable routes`() {
        assertEquals("onboarding", Screen.Onboarding.route)
        assertEquals("login", Screen.Login.route)
        assertEquals("main", Screen.Main.route)
    }

    @Test
    fun `argument placeholder names match Screen ARG constants`() {
        // Defensive: NavHost composables build typed args using these constants.
        // If a route template's placeholder name drifts, args won't deserialise.
        assertTrue(Screen.TripDetail.route.contains("{${Screen.ARG_DRIVE_ID}}"))
        assertTrue(Screen.TripAudit.route.contains("{${Screen.ARG_TRIP_ID}}"))
        assertTrue(Screen.RuleForm.route.contains("{${Screen.ARG_RULE_ID}}"))
        assertTrue(Screen.TripMap.route.contains("{${Screen.ARG_DRIVE_ID}}"))
        assertTrue(Screen.IntegrationDetail.route.contains("{${Screen.ARG_PROVIDER}}"))
    }
}
