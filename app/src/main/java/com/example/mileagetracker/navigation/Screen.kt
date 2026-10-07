package com.example.mileagetracker.navigation

import com.example.mileagetracker.domain.model.IntegrationProvider
import java.net.URLEncoder

/**
 * App navigation destinations and shared route arguments.
 */
sealed class Screen(val route: String, val title: String = "") {

    companion object {
        const val ARG_DRIVE_ID = "driveId"
        const val ARG_VEHICLE_ID = "vehicleId"
        const val ARG_VEHICLE_NAME = "vehicleName"
        const val ARG_YEAR = "year"
        const val ARG_PROVIDER = "provider"
        const val ARG_RULE_ID = "ruleId"
        const val ARG_TRIP_ID = "tripId"

        const val OCR_AMOUNT = "ocr_amount"
        const val OCR_VENDOR = "ocr_vendor"
        const val OCR_DATE = "ocr_date"
        const val OCR_GST = "ocr_gst"
    }

    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Main : Screen("main")

    object Dashboard : Screen("dashboard", "Dashboard")
    object Unclassified : Screen("unclassified_drives", "Unclassified Drives")
    object Profile : Screen("profile", "Profile")
    object Settings : Screen("settings", "Settings")
    object PrivacyDashboard : Screen("privacy_dashboard", "Privacy Dashboard")
    object Reports : Screen("reports", "Reports")

    object ManualTrip : Screen("manual_trip", "Record Trip") {
        const val DeepLink = "mileagetracker://manual_trip"
    }

    object TrackingDiagnostics : Screen("tracking_diagnostics", "Trip Recording Health")
    object VehicleManagement : Screen("vehicle_management", "My Vehicles")

    object TripDetail : Screen("trip_detail/{$ARG_DRIVE_ID}", "Trip Detail") {
        fun buildRoute(driveId: Int) = "trip_detail/$driveId"
    }

    object ManualTripEntry : Screen("manual_trip_entry", "Add Missed Trip") {
        val routeWithArg = "manual_trip_entry?$ARG_DRIVE_ID={$ARG_DRIVE_ID}"
        fun buildRepairRoute(driveId: Int) = "manual_trip_entry?$ARG_DRIVE_ID=$driveId"
    }

    object CraLogbook : Screen("cra_logbook", "CRA Logbook")
    object Export : Screen("export", "Export")
    object ClassificationRules : Screen("classification_rules", "Auto-Classification Rules")

    object RuleForm : Screen("rule_form/{$ARG_RULE_ID}", "Rule") {
        const val createRoute = "rule_form/-1"
        fun buildEditRoute(id: Int) = "rule_form/$id"
    }

    object OdometerCapture : Screen("odometer_capture/{$ARG_VEHICLE_ID}", "Record Odometer") {
        val routeWithName =
            "odometer_capture/{$ARG_VEHICLE_ID}?$ARG_VEHICLE_NAME={$ARG_VEHICLE_NAME}"

        fun buildRoute(vehicleId: Int, vehicleName: String = "") =
            "odometer_capture/$vehicleId?$ARG_VEHICLE_NAME=${vehicleName.encodeUrl()}"
    }

    object AnnualVehicleSummary : Screen("annual_vehicle_summary", "Annual Summary")
    object ReportHistory : Screen("report_history", "Report History")
    object SimplifiedLogbook : Screen("simplified_logbook", "Simplified Logbook")
    object ExceptionsList : Screen("exceptions_list", "Incomplete Trips")

    object TripAudit : Screen("trip_audit/{$ARG_TRIP_ID}", "Edit History") {
        fun buildRoute(tripId: Int) = "trip_audit/$tripId"
    }

    object OcrReceipt : Screen("ocr_receipt", "Scan Receipt")

    object VehicleExpense : Screen(
        "vehicle_expense/{$ARG_VEHICLE_ID}?$ARG_YEAR={$ARG_YEAR}",
        "Expenses",
    ) {
        fun buildRoute(
            vehicleId: Int,
            year: Int = java.time.LocalDate.now().year,
        ) = "vehicle_expense/$vehicleId?$ARG_YEAR=$year"
    }

    object Integrations : Screen("integrations", "Integrations")

    object IntegrationDetail : Screen(
        "integration_detail/{$ARG_PROVIDER}",
        "Integration",
    ) {
        fun buildRoute(provider: IntegrationProvider) =
            "integration_detail/${provider.name}"
    }

    object PolicyTemplates : Screen("policy_templates", "Policy Templates")
    object DriverInvite : Screen("driver_invite", "Invite Team Member")
    object ApprovalQueue : Screen("approval_queue", "Approval Queue")
    object PeriodLock : Screen("period_lock", "Period Locks")
    object TeamDashboard : Screen("team_dashboard", "Team Dashboard")
    object ManagerAnalytics : Screen("manager_analytics", "Manager Analytics")

    object TripMap : Screen("trip_map/{$ARG_DRIVE_ID}", "Route Map") {
        fun createRoute(driveId: Int) = "trip_map/$driveId"
    }
}

private fun String.encodeUrl(): String = URLEncoder.encode(this, "UTF-8")
