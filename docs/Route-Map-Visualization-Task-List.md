# Route Map Visualization — Task List

## Context

`RoutePointEntity` already stores `lat`, `lng`, `altitudeMetres`, `accuracyMetres`,
`speedKph`, and `timestampMs` for every GPS fix, indexed on `(trip_id, timestamp_ms)`
for ordered retrieval. `RoutePointDao.getPointsForTrip()` exposes a `Flow` of
chronologically ordered points. `TripDetailViewModel` already collects this flow
and passes the `List<RoutePoint>` into `TripDetailContent`. The only missing piece is
the map render itself — the current placeholder card in `TripDetailScreen` reads
"Map view coming soon".

---

## Phase 1 — Dependency & Configuration

- [ ] **1.1** Add the Google Maps Compose dependency to `app/build.gradle.kts`:
  ```kotlin
  implementation("com.google.maps.android:maps-compose:4.x.x")
  implementation("com.google.android.gms:play-services-maps:19.x.x")
  ```
- [ ] **1.2** Add your Google Maps API key to `local.properties`: MAPS_API_KEY=YOUR_KEY_HERE

- [ ] **1.3** Read the key in `app/build.gradle.kts` via `buildConfigField` and
  inject it into `AndroidManifest.xml` using `manifestPlaceholders`:
```kotlin
manifestPlaceholders["MAPS_API_KEY"] = properties["MAPS_API_KEY"] ?: ""
```
- [ ] **1.4** Add the `<meta-data>` Maps API key entry to `AndroidManifest.xml`
  inside `<application>`:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```
- [ ] **1.5** Confirm `MAPS_API_KEY` is added to `.gitignore` / not committed to
  version control (use `secrets-gradle-plugin` or equivalent if not already in
  place).

---

## Phase 2 — Domain & ViewModel

- [ ] **2.1** Verify `TripDetailViewModel` already exposes `route: StateFlow<List<RoutePoint>>`
  — if it is loaded inline inside `uiState`, extract it to a dedicated top-level
  `StateFlow` to keep map state independent of the rest of the form state.
- [ ] **2.2** Add a `RouteMapUiState` sealed class with three states:
- `NoRoute` — trip has zero GPS points (manual entry)
- `Loading` — points are being fetched
- `Ready(points: List<LatLng>, bounds: LatLngBounds, startMarker: LatLng, endMarker: LatLng)`
- [ ] **2.3** In `TripDetailViewModel`, derive `RouteMapUiState` from the existing
  `route` flow — convert `RoutePoint` → `LatLng`, compute `LatLngBounds` via
  `LatLngBounds.Builder`, and emit `Ready` once points are non-empty.
- [ ] **2.4** Add a unit test in `TripDetailViewModelTest` (or create it) covering:
- Empty route → emits `NoRoute`
- Single point → emits `NoRoute` (cannot draw a polyline)
- Two or more points → emits `Ready` with correct bounds

---

## Phase 3 — Composable Map Component

- [ ] **3.1** Create `ui/map/TripRouteMapCard.kt` — a self-contained `@Composable`
  that accepts `RouteMapUiState` and renders the appropriate UI:
- `NoRoute` → show the existing "Route recorded" summary card unchanged
- `Loading` → show a `LinearProgressIndicator` inside the card
- `Ready` → render the `GoogleMap` composable with polyline and markers
- [ ] **3.2** Inside the `Ready` branch of `TripRouteMapCard`, implement:
- A `GoogleMap` with `uiSettings` disabling scroll/zoom gestures (the card is
  embedded in a scrollable `Column`; use a "tap to expand" pattern instead)
- A `Polyline` drawn from `RouteMapUiState.Ready.points` in the app's
  `primary` colour
- A green `Marker` at `startMarker` labelled "Start"
- A red `Marker` at `endMarker` labelled "End"
- `CameraPositionState` initialised via `CameraUpdateFactory.newLatLngBounds`
  using `RouteMapUiState.Ready.bounds` with a `64.dp` padding
- [ ] **3.3** Add a `modifier: Modifier` parameter to `TripRouteMapCard` and set
  a fixed `height(220.dp)` default so the card fits naturally in the scroll column.
- [ ] **3.4** Add a "View full map" `TextButton` below the map card that fires an
  `onExpandMap: () -> Unit` callback — this will be wired to a full-screen map
  screen in Phase 4.

---

## Phase 4 — Full-Screen Map Screen

- [ ] **4.1** Create `ui/screen/TripMapScreen.kt` — a full-screen map that receives
  `driveId: Int` as a nav argument and loads `RouteMapUiState` from
  `TripDetailViewModel` (or a lightweight `TripMapViewModel` if preferred).
- [ ] **4.2** In the full-screen map, enable all `MapUiSettings` (zoom controls,
  compass, scroll gestures).
- [ ] **4.3** Display a `TopAppBar` with a back arrow and the trip date as the title.
- [ ] **4.4** Add the `TripMap` route to `navigation/Screen.kt`:
```kotlin
object TripMap : Screen("trip_map/{driveId}") {
    fun createRoute(driveId: Int) = "trip_map/$driveId"
}
```
- [ ] **4.5** Register `TripMapScreen` in `NavHost` inside `AppNavGraph.kt` (or
  equivalent navigation file), accepting `driveId` as an `Int` argument.
- [ ] **4.6** Wire the `onExpandMap` callback in `TripDetailScreen` to navigate
  to `Screen.TripMap.createRoute(driveId)`.

---

## Phase 5 — French Localisation

- [ ] **5.1** Add French strings for all new UI text to
  `res/values-fr/strings.xml` and `res/values-fr-rCA/strings.xml`:
- `"Départ"` (Start marker label)
- `"Arrivée"` (End marker label)
- `"Voir la carte complète"` (View full map button)
- `"Carte de l'itinéraire"` (Map card title)
- `"Aucune donnée GPS"` (No GPS data fallback)

---

## Phase 6 — Edge Cases & Polish

- [ ] **6.1** Handle the `NoRoute` state gracefully in `TripDetailScreen` — keep
  the existing route summary card ("X GPS points captured") and do not render
  `TripRouteMapCard` at all when `state is NoRoute`.
- [ ] **6.2** Handle Google Play Services unavailability — wrap the `GoogleMap`
  composable in a `try/catch` and show a fallback `ElevatedCard` with the text
  "Map unavailable on this device" if the Maps SDK fails to initialise.
- [ ] **6.3** Add a low-accuracy visual indicator — if any `RoutePoint.accuracyMetres`
  exceeds `50f`, show a small warning chip below the map: "⚠ GPS accuracy low on
  parts of this route".
- [ ] **6.4** Ensure the map card does not render during PDF/screenshot export —
  check the existing `ExportViewModel` / export pipeline and skip the map composable
  in any export-rendered layout to avoid bitmap capture issues.

---

## Phase 7 — Testing

- [x] **7.1** Add a `TripRouteMapCardTest` (Compose UI test) verifying:
- `NoRoute` state renders the summary card, not the map
- `Ready` state renders the `GoogleMap` composable
- [x] **7.2** Add an integration test for `TripDetailViewModel` confirming that
  inserting `RoutePointEntity` rows into the in-memory Room database causes the
  map state to transition from `Loading` → `Ready`.
- [ ] **7.3** Manual test checklist:
- [ ] Trip with GPS route shows polyline in card and full-screen view
- [ ] Manual entry trip (no route points) shows no map card
- [ ] French locale displays all strings correctly
- [ ] Back navigation from full-screen map returns to `TripDetailScreen`
- [ ] App does not crash when Play Services is unavailable (emulator without GMS)
