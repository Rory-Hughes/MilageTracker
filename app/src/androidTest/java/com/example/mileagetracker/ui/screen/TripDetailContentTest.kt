package com.example.mileagetracker.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.ui.viewmodel.RouteMapUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        drive: DriveEntity = testDrive(),
        activeVehicles: List<VehicleEntity> = emptyList(),
        onClassify: (TripCategory, String?) -> Unit = { _, _ -> },
        onSaveNotes: (String) -> Unit = {},
        onAssignVehicle: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            TripDetailContent(
                drive = drive,
                route = emptyList(),
                routeMapState = RouteMapUiState.NoRoute,
                vehicle = null,
                activeVehicles = activeVehicles,
                topSuggestion = null,
                calendarHints = emptyList(),
                activeException = null,
                attachments = emptyList(),
                onApplySuggestion = {},
                onDismissSuggestion = {},
                onApplyCalendarHint = {},
                onDismissCalendarHints = {},
                onResolveException = {},
                onDismissException = {},
                onClassify = onClassify,
                onAssignVehicle = onAssignVehicle,
                onSaveNotes = onSaveNotes,
                onUpdateEndpoints = { _, _ -> },
                onAddAttachment = {},
                onDeleteAttachment = {},
                onViewAttachment = {},
                onExpandMap = {},
            )
        }
    }

    @Test
    fun headerCard_showsLocationsAndDistance() {
        setContent(
            drive = testDrive(
                start = "123 Maple St",
                end = "456 Oak Ave",
                distanceKm = 27.3f,
            ),
        )

        composeRule.onNodeWithText("123 Maple St").assertIsDisplayed()
        composeRule.onNodeWithText("456 Oak Ave").assertIsDisplayed()
    }

    @Test
    fun categoryChips_showsAllSelectableCategories() {
        setContent()

        composeRule.onNodeWithText("Business").assertIsDisplayed()
        composeRule.onNodeWithText("Personal").assertIsDisplayed()
        composeRule.onNodeWithText("Medical").assertIsDisplayed()
    }

    @Test
    fun tappingCategoryChip_invokesClassifyCallback() {
        var classifiedCategory: TripCategory? = null
        setContent(
            drive = testDrive(category = TripCategory.UNCLASSIFIED),
            onClassify = { category, _ -> classifiedCategory = category },
        )

        composeRule.onNodeWithText("Business").performClick()

        assertEquals(TripCategory.BUSINESS, classifiedCategory)
    }

    @Test
    fun notesSection_displaysExistingNotes() {
        setContent(drive = testDrive(notes = "Client meeting"))

        composeRule.onNodeWithText("Client meeting").assertIsDisplayed()
    }

    @Test
    fun notesSection_saveButtonInvokesCallback() {
        var savedNotes: String? = null
        setContent(onSaveNotes = { savedNotes = it })

        composeRule.onNodeWithText("Optional notes", substring = true).performTextInput("Test note")
        composeRule.onNodeWithText("Save").performClick()

        assertTrue(savedNotes?.contains("Test note") == true)
    }

    @Test
    fun routeSummary_hiddenWhenNoRoutePoints() {
        setContent()

        composeRule.onNodeWithText("Route Recorded", substring = true).assertDoesNotExist()
    }

    @Test
    fun routeSummary_shownWhenRoutePointsExist() {
        val points = listOf(
            RoutePoint(
                tripId = 1, lat = 45.0, lng = -75.0,
                altitudeMetres = null, accuracyMetres = null,
                speedKph = null, timestampMs = 1000L,
            ),
            RoutePoint(
                tripId = 1, lat = 45.1, lng = -75.1,
                altitudeMetres = null, accuracyMetres = null,
                speedKph = null, timestampMs = 2000L,
            ),
        )
        composeRule.setContent {
            TripDetailContent(
                drive = testDrive(),
                route = points,
                routeMapState = RouteMapUiState.NoRoute,
                vehicle = null,
                activeVehicles = emptyList(),
                topSuggestion = null,
                calendarHints = emptyList(),
                activeException = null,
                attachments = emptyList(),
                onApplySuggestion = {},
                onDismissSuggestion = {},
                onApplyCalendarHint = {},
                onDismissCalendarHints = {},
                onResolveException = {},
                onDismissException = {},
                onClassify = { _, _ -> },
                onAssignVehicle = {},
                onSaveNotes = {},
                onUpdateEndpoints = { _, _ -> },
                onAddAttachment = {},
                onDeleteAttachment = {},
                onViewAttachment = {},
                onExpandMap = {},
            )
        }

        composeRule.onNodeWithText("GPS points captured", substring = true).assertIsDisplayed()
    }

    @Test
    fun reimbursementAmount_shownWhenPositive() {
        setContent(drive = testDrive(reimbursement = 12.50f))

        composeRule.onNodeWithText("$12.50", substring = true).assertIsDisplayed()
    }

    @Test
    fun reimbursementAmount_hiddenWhenNull() {
        setContent(drive = testDrive(reimbursement = null))

        composeRule.onNodeWithText("Reimbursement", substring = true).assertDoesNotExist()
    }

    private fun testDrive(
        id: Int = 1,
        start: String = "Start",
        end: String = "End",
        distanceKm: Float = 15f,
        category: TripCategory = TripCategory.UNCLASSIFIED,
        notes: String? = null,
        reimbursement: Float? = null,
    ) = DriveEntity(
        id = id,
        startedAt = System.currentTimeMillis() - 3_600_000,
        endedAt = System.currentTimeMillis(),
        startLocation = start,
        endLocation = end,
        distanceKm = distanceKm,
        tripCategory = category,
        classificationStatus = ClassificationStatus.PENDING,
        driverUid = "test-uid",
        notes = notes,
        reimbursementAmountSnapshot = reimbursement,
    )
}
