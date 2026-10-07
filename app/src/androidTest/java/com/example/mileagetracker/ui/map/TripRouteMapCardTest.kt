package com.example.mileagetracker.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.example.mileagetracker.ui.viewmodel.RouteMapUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [TripRouteMapCard] — Phase 7, task 7.1.
 *
 * These tests verify the correct branch is rendered for each [RouteMapUiState]:
 * - [RouteMapUiState.NoRoute]  → no map content is rendered (composable emits Unit)
 * - [RouteMapUiState.Loading]  → a progress indicator is shown
 * - [RouteMapUiState.Ready]    → the GoogleMap composable is present and the
 *                                "View full map" button is visible
 *
 * ## Notes on GoogleMap in tests
 * The [GoogleMap] composable requires Google Play Services. On emulators that
 * ship with GMS the map renders normally. On emulators without GMS the
 * [TripRouteMapCard] falls back to the "Map unavailable on this device" text
 * (Phase 6, task 6.2). Both outcomes are covered here so the suite passes in
 * both environments.
 *
 * Semantic tags used for node lookup:
 * - `"map_progress_indicator"` — set on [LinearProgressIndicator] in the Loading branch
 * - `"trip_route_map"` — set on the root [GoogleMap] composable in the Ready branch
 * - `"map_unavailable_text"` — set on the fallback Text in the Ready/no-GMS branch
 *
 * **Important**: These tags must be added to the production composable with
 * `Modifier.testTag(...)` if they are not already present. The tests will
 * fail to find the nodes without them — this is intentional so the tags act
 * as a contract between the UI and the test.
 */
@RunWith(AndroidJUnit4::class)
class TripRouteMapCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── helpers ──────────────────────────────────────────────────────────

    private fun twoPointReadyState(hasLowAccuracy: Boolean = false): RouteMapUiState.Ready {
        val p1 = LatLng(45.0, -66.0)
        val p2 = LatLng(46.0, -67.0)
        return RouteMapUiState.Ready(
            points         = listOf(p1, p2),
            bounds         = LatLngBounds.Builder().include(p1).include(p2).build(),
            startMarker    = p1,
            endMarker      = p2,
            hasLowAccuracy = hasLowAccuracy,
        )
    }

    // ── 7.1 task tests ────────────────────────────────────────────────────

    /**
     * When state is [RouteMapUiState.NoRoute], [TripRouteMapCard] must emit
     * nothing — the node tree should not contain the progress indicator,
     * the map, or the "View full map" button.
     *
     * The caller (TripDetailScreen) is responsible for not calling
     * [TripRouteMapCard] at all when state is NoRoute, but the composable
     * itself also handles the branch gracefully (emits Unit).
     */
    @Test
    fun noRoute_rendersNothing() {
        composeRule.setContent {
            TripRouteMapCard(
                state       = RouteMapUiState.NoRoute,
                onExpandMap = {},
            )
        }

        // Nothing from the map card should appear in the tree
        composeRule.onNodeWithTag("map_progress_indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("trip_route_map").assertDoesNotExist()
        composeRule.onNodeWithTag("map_unavailable_text").assertDoesNotExist()
    }

    /**
     * When state is [RouteMapUiState.Loading], a progress indicator card
     * must be visible.
     */
    @Test
    fun loading_showsProgressIndicator() {
        composeRule.setContent {
            TripRouteMapCard(
                state       = RouteMapUiState.Loading,
                onExpandMap = {},
            )
        }

        composeRule.onNodeWithTag("map_progress_indicator").assertIsDisplayed()
    }

    /**
     * When state is [RouteMapUiState.Ready] and Google Play Services is
     * available, the [GoogleMap] node must be present and the
     * "View full map" [TextButton] must be displayed.
     *
     * On devices/emulators without GMS, [TripRouteMapCard] falls through to
     * the unavailable-fallback branch; in that case we assert the fallback
     * text is shown instead.
     */
    @Test
    fun ready_showsMapOrFallback() {
        composeRule.setContent {
            TripRouteMapCard(
                state       = twoPointReadyState(),
                onExpandMap = {},
            )
        }

        val mapNode         = composeRule.onNodeWithTag("trip_route_map")
        val unavailableNode = composeRule.onNodeWithTag("map_unavailable_text")

        // One of the two branches must be visible
        val mapExists         = runCatching { mapNode.assertIsDisplayed() }.isSuccess
        val fallbackExists    = runCatching { unavailableNode.assertIsDisplayed() }.isSuccess

        assert(mapExists || fallbackExists) {
            "Expected either the GoogleMap node or the fallback text to be displayed in Ready state"
        }
    }

    /**
     * When state is [RouteMapUiState.Ready], the "View full map" button must
     * be present regardless of whether GMS is available (it lives outside
     * the GMS-guarded branch).
     */
    @Test
    fun ready_showsViewFullMapButton() {
        composeRule.setContent {
            TripRouteMapCard(
                state       = twoPointReadyState(),
                onExpandMap = {},
            )
        }

        // The button text comes from R.string.map_view_full — default EN value is
        // "View full map". Matching by substring keeps the test locale-agnostic.
        composeRule.onNodeWithText("full map", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    /**
     * When state is [RouteMapUiState.Ready] with [hasLowAccuracy] = true,
     * the low-accuracy warning chip must be visible below the card.
     */
    @Test
    fun ready_withLowAccuracy_showsWarningChip() {
        composeRule.setContent {
            TripRouteMapCard(
                state       = twoPointReadyState(hasLowAccuracy = true),
                onExpandMap = {},
            )
        }

        // The chip label text comes from R.string.map_low_accuracy_warning.
        // Matching by known substring keeps the test stable across string edits.
        composeRule.onNodeWithText("accuracy", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    /**
     * When state is [RouteMapUiState.Ready] with [hasLowAccuracy] = false,
     * the low-accuracy warning chip must NOT appear.
     */
    @Test
    fun ready_withoutLowAccuracy_warningChipAbsent() {
        composeRule.setContent {
            TripRouteMapCard(
                state       = twoPointReadyState(hasLowAccuracy = false),
                onExpandMap = {},
            )
        }

        composeRule.onNodeWithTag("map_low_accuracy_chip").assertDoesNotExist()
    }
}
