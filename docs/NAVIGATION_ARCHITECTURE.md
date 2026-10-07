# Navigation Architecture

`MainAppShell` now owns only the authenticated shell chrome: drawer, app bar, and route-aware selection.
Destination registration lives under `app/src/main/java/com/example/mileagetracker/navigation/`.

## Graph layout

- `DashboardGraph.kt`: dashboard entry point and dashboard-driven navigation
- `TripsGraph.kt`: unclassified trips, manual tracking, trip detail, repair, audit, and full-map flows
- `ReportsGraph.kt`: reports, CRA logbook, export, report history, readiness exceptions, annual summary, odometer capture, expenses, OCR, and integrations
- `VehicleGraph.kt`: vehicle management
- `AccountGraph.kt`: profile, settings, classification rules, and rule form
- `TeamGraph.kt`: manager-only team flows

## Shell behavior

- Drawer selection is derived from the current navigation back stack instead of mutable screen state.
- App-bar titles are derived from the current destination route.
- Ambiguous cross-entry screens such as expense and integration detail keep their exact title, but do not force a drawer selection.

## Adding a destination

1. Add the route to `Screen.kt`.
2. Register the destination in the feature graph that owns the flow.
3. If the screen should affect drawer selection or app-bar titles, add it to `routeToScreen(...)` and `routeToDrawerScreen(...)`.
4. Add tests for route helpers when the new screen changes shell behavior.
