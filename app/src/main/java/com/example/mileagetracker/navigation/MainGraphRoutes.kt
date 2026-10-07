package com.example.mileagetracker.navigation

object MainGraphRoutes {
    const val DASHBOARD = "dashboard_graph"
    const val TRIPS = "trips_graph"
    const val REPORTS = "reports_graph"
    const val VEHICLES = "vehicles_graph"
    const val ACCOUNT = "account_graph"
    const val TEAM = "team_graph"
}

fun mainDrawerScreens(
    teamFeaturesEnabled: Boolean,
    isManager: Boolean,
): List<Screen> {
    val screens = mutableListOf(
        Screen.Dashboard,
        Screen.Unclassified,
        Screen.ManualTrip,
        Screen.Reports,
        Screen.VehicleManagement,
        Screen.Profile,
        Screen.Settings,
    )
    if (teamFeaturesEnabled && isManager) {
        screens += Screen.TeamDashboard
    }
    return screens
}

fun routeToScreen(route: String?): Screen? = route?.let(routeToScreenMap::get)

fun routeToDrawerScreen(route: String?): Screen? = route?.let(drawerRouteToScreenMap::get)

private val routeToScreenMap: Map<String, Screen> = listOf(
    Screen.Dashboard.route to Screen.Dashboard,
    Screen.Unclassified.route to Screen.Unclassified,
    Screen.ManualTrip.route to Screen.ManualTrip,
    Screen.TrackingDiagnostics.route to Screen.TrackingDiagnostics,
    Screen.Reports.route to Screen.Reports,
    Screen.CraLogbook.route to Screen.CraLogbook,
    Screen.Export.route to Screen.Export,
    Screen.VehicleManagement.route to Screen.VehicleManagement,
    Screen.Profile.route to Screen.Profile,
    Screen.Settings.route to Screen.Settings,
    Screen.PrivacyDashboard.route to Screen.PrivacyDashboard,
    Screen.ClassificationRules.route to Screen.ClassificationRules,
    Screen.RuleForm.route to Screen.RuleForm,
    Screen.TripDetail.route to Screen.TripDetail,
    Screen.ManualTripEntry.routeWithArg to Screen.ManualTripEntry,
    Screen.AnnualVehicleSummary.route to Screen.AnnualVehicleSummary,
    Screen.OdometerCapture.routeWithName to Screen.OdometerCapture,
    Screen.VehicleExpense.route to Screen.VehicleExpense,
    Screen.OcrReceipt.route to Screen.OcrReceipt,
    Screen.Integrations.route to Screen.Integrations,
    Screen.IntegrationDetail.route to Screen.IntegrationDetail,
    Screen.ReportHistory.route to Screen.ReportHistory,
    Screen.SimplifiedLogbook.route to Screen.SimplifiedLogbook,
    Screen.ExceptionsList.route to Screen.ExceptionsList,
    Screen.TripAudit.route to Screen.TripAudit,
    Screen.TeamDashboard.route to Screen.TeamDashboard,
    Screen.ManagerAnalytics.route to Screen.ManagerAnalytics,
    Screen.ApprovalQueue.route to Screen.ApprovalQueue,
    Screen.PeriodLock.route to Screen.PeriodLock,
    Screen.DriverInvite.route to Screen.DriverInvite,
    Screen.PolicyTemplates.route to Screen.PolicyTemplates,
    Screen.TripMap.route to Screen.TripMap,
).toMap()

private val drawerRouteToScreenMap: Map<String, Screen> = buildMap {
    registerDrawerRoutes(
        drawerScreen = Screen.Dashboard,
        routes = listOf(Screen.Dashboard.route),
    )
    registerDrawerRoutes(
        drawerScreen = Screen.Unclassified,
        routes = listOf(
            Screen.Unclassified.route,
            Screen.TripDetail.route,
            Screen.ManualTripEntry.routeWithArg,
            Screen.TripAudit.route,
            Screen.TripMap.route,
        ),
    )
    registerDrawerRoutes(
        drawerScreen = Screen.ManualTrip,
        routes = listOf(
            Screen.ManualTrip.route,
            Screen.TrackingDiagnostics.route,
        ),
    )
    registerDrawerRoutes(
        drawerScreen = Screen.Reports,
        routes = listOf(
            Screen.Reports.route,
            Screen.CraLogbook.route,
            Screen.Export.route,
            Screen.AnnualVehicleSummary.route,
            Screen.OdometerCapture.routeWithName,
            Screen.ReportHistory.route,
            Screen.SimplifiedLogbook.route,
            Screen.ExceptionsList.route,
        ),
    )
    registerDrawerRoutes(
        drawerScreen = Screen.VehicleManagement,
        routes = listOf(Screen.VehicleManagement.route),
    )
    registerDrawerRoutes(
        drawerScreen = Screen.Profile,
        routes = listOf(Screen.Profile.route),
    )
    registerDrawerRoutes(
        drawerScreen = Screen.Settings,
        routes = listOf(
            Screen.Settings.route,
            Screen.PrivacyDashboard.route,
            Screen.ClassificationRules.route,
            Screen.RuleForm.route,
        ),
    )
    registerDrawerRoutes(
        drawerScreen = Screen.TeamDashboard,
        routes = listOf(
            Screen.TeamDashboard.route,
            Screen.ManagerAnalytics.route,
            Screen.ApprovalQueue.route,
            Screen.PeriodLock.route,
            Screen.DriverInvite.route,
            Screen.PolicyTemplates.route,
        ),
    )
}

private fun MutableMap<String, Screen>.registerDrawerRoutes(
    drawerScreen: Screen,
    routes: List<String>,
) {
    routes.forEach { route ->
        put(route, drawerScreen)
    }
}
