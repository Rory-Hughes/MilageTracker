package com.example.mileagetracker.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: DashboardScreenState = defaultState(),
        onNavigateToUnclassified: () -> Unit = {},
        onNavigateToDiagnostics: () -> Unit = {},
        onNavigateToManualTrip: () -> Unit = {},
    ) {
        composeRule.setContent {
            DashboardScreenContent(
                state = state,
                onNavigateToUnclassified = onNavigateToUnclassified,
                onNavigateToDiagnostics = onNavigateToDiagnostics,
                onNavigateToManualTrip = onNavigateToManualTrip,
                onTripTapped = {},
                onClearBatteryDismissal = {},
                onDismissBatteryWarning = {},
            )
        }
    }

    @Test
    fun ytdEstimateCard_showsFormattedDeductions() {
        setContent(state = defaultState(formattedDeductions = "$123.45", canShowEstimate = true))

        composeRule.onNodeWithText("$123.45").assertIsDisplayed()
    }

    @Test
    fun unclassifiedBanner_showsCount_andNavigates() {
        var navigated = false
        setContent(
            state = defaultState(unclassifiedCount = 5),
            onNavigateToUnclassified = { navigated = true },
        )

        composeRule.onNodeWithText("5", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Done", substring = true).performClick()
        assertTrue(navigated)
    }

    @Test
    fun unclassifiedBanner_hiddenWhenCountIsZero() {
        setContent(state = defaultState(unclassifiedCount = 0))

        composeRule.onNodeWithText("Unclassified", substring = true).assertDoesNotExist()
    }

    @Test
    fun recentTrips_displaysLocationsAndDistance() {
        val trips = listOf(
            testDrive(id = 1, start = "Home", end = "Office", distanceKm = 14.2f),
        )
        setContent(state = defaultState(recentTrips = trips))

        composeRule.onNodeWithText("Home → Office", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("14.2 km", substring = true).assertIsDisplayed()
    }

    @Test
    fun recentTrips_emptyState_showsNoTripsMessage() {
        setContent(state = defaultState(recentTrips = emptyList()))

        composeRule.onNodeWithText("No trips", substring = true).assertIsDisplayed()
    }

    @Test
    fun batteryBanner_shownWhenFlagged() {
        setContent(state = defaultState(showBatteryBanner = true))

        composeRule.onAllNodesWithText("Battery", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun batteryBanner_hiddenWhenNotFlagged() {
        setContent(state = defaultState(showBatteryBanner = false))

        composeRule.onNodeWithText("Battery Optimization", substring = true).assertDoesNotExist()
    }

    private fun defaultState(
        unclassifiedCount: Int = 0,
        showBatteryBanner: Boolean = false,
        showOtherBanner: Boolean = false,
        otherIssueCount: Int = 0,
        ytdDistance: Float = 1_200f,
        formattedDeductions: String = "$0.00",
        canShowEstimate: Boolean = true,
        kmRemaining: Float = 3_800f,
        progress: Float = 0.24f,
        firstTierDollars: Double? = 0.72,
        additionalDollars: Double? = 0.66,
        recentTrips: List<DriveEntity> = emptyList(),
    ) = DashboardScreenState(
        unclassifiedCount = unclassifiedCount,
        showBatteryBanner = showBatteryBanner,
        showOtherBanner = showOtherBanner,
        otherIssueCount = otherIssueCount,
        ytdDistance = ytdDistance,
        formattedDeductions = formattedDeductions,
        canShowEstimate = canShowEstimate,
        kmRemaining = kmRemaining,
        progress = progress,
        firstTierDollars = firstTierDollars,
        additionalDollars = additionalDollars,
        recentTrips = recentTrips,
    )

    private fun testDrive(
        id: Int = 1,
        start: String = "A",
        end: String = "B",
        distanceKm: Float = 10f,
        category: TripCategory = TripCategory.BUSINESS,
    ) = DriveEntity(
        id = id,
        startedAt = System.currentTimeMillis() - 3_600_000,
        endedAt = System.currentTimeMillis(),
        startLocation = start,
        endLocation = end,
        distanceKm = distanceKm,
        tripCategory = category,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        driverUid = "test-uid",
    )
}
