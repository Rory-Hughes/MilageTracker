package com.example.mileagetracker.ui.screen

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.places.PlacesService
import com.example.mileagetracker.domain.repository.DirectionsRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.drive.CreateManualTripUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import com.example.mileagetracker.ui.viewmodel.ManualTripEntryViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [ManualTripEntryScreen] (create mode).
 *
 * The screen is driven by a real [ManualTripEntryViewModel] constructed with
 * mocked collaborators — the ViewModel is a plain (non-Android) `ViewModel`
 * after the I/O extraction refactor, so it can be instantiated directly in an
 * instrumented test without Hilt.
 *
 * These cover the two things a form screen most needs validated at the UI layer:
 * - **Field error display**: invalid input surfaces the inline supporting-text
 *   error and keeps the save action disabled.
 * - **Keyboard / input flow**: typing into a field updates the rendered value,
 *   and a fully-valid form enables the save button.
 *
 * Node lookup uses the `manual_trip_*` test tags added to the production
 * composable as a UI ↔ test contract (mirroring [com.example.mileagetracker.ui.map.TripRouteMapCardTest]).
 */
@RunWith(AndroidJUnit4::class)
class ManualTripEntryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val placesService: PlacesService = mockk {
        // Typing ≥3 chars triggers an autocomplete lookup; return nothing so no
        // dropdown is shown and the test stays focused on form behaviour.
        coEvery { autocomplete(any(), any()) } returns emptyList()
    }
    private val directionsRepository: DirectionsRepository = mockk(relaxed = true)
    private val driveRepository: DriveRepository = mockk(relaxed = true)
    private val createManualTripUseCase: CreateManualTripUseCase = mockk(relaxed = true)
    private val updateDriveUseCase: UpdateDriveUseCase = mockk(relaxed = true)
    private val currentUserProvider: CurrentUserProvider = mockk {
        every { requireUid() } returns "user-123"
    }

    private fun createViewModel() = ManualTripEntryViewModel(
        savedStateHandle = SavedStateHandle(), // no ARG_DRIVE_ID -> create mode
        driveRepository = driveRepository,
        createManualTripUseCase = createManualTripUseCase,
        updateDriveUseCase = updateDriveUseCase,
        placesService = placesService,
        directionsRepository = directionsRepository,
        currentUserProvider = currentUserProvider,
    )

    private fun setScreen(viewModel: ManualTripEntryViewModel) {
        composeRule.setContent {
            ManualTripEntryScreen(
                activeVehicles = emptyList(),
                onNavigateBack = {},
                onSaveSuccess = { _, _ -> },
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun emptyForm_saveButtonIsDisabled() {
        setScreen(createViewModel())

        composeRule.onNodeWithTag("manual_trip_save_button").assertIsNotEnabled()
    }

    @Test
    fun typingStartLocation_updatesRenderedField() {
        setScreen(createViewModel())

        composeRule.onNodeWithTag("manual_trip_start_field").performTextInput("Home Office")

        composeRule.onNodeWithTag("manual_trip_start_field").assertTextContains("Home Office")
    }

    @Test
    fun zeroDistance_showsInlineError() {
        setScreen(createViewModel())

        composeRule.onNodeWithTag("manual_trip_distance_field").performTextInput("0")

        // The error copy is hardcoded in the ViewModel's revalidated() check.
        composeRule.onNodeWithText("greater than 0", substring = true).assertExists()
    }

    @Test
    fun fullyValidForm_enablesSaveButton() {
        val viewModel = createViewModel()
        // Timestamps are set via date/time picker dialogs that are awkward to
        // drive in a UI test, so seed a valid start time directly; everything
        // else is entered through the rendered fields.
        viewModel.updateStartedAt(1_000L)
        setScreen(viewModel)

        composeRule.onNodeWithTag("manual_trip_start_field").performTextInput("Home")
        composeRule.onNodeWithTag("manual_trip_end_field").performTextInput("Client Site")
        composeRule.onNodeWithTag("manual_trip_distance_field").performTextInput("12.5")

        composeRule.onNodeWithTag("manual_trip_save_button").assertIsEnabled()
    }
}
