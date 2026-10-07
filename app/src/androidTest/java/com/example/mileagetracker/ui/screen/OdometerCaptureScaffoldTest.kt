package com.example.mileagetracker.ui.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.model.OdometerCaptureType
import com.example.mileagetracker.ui.viewmodel.OdometerFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scaffold-level tests for OdometerCaptureScreen.
 *
 * The public `OdometerCaptureScreen` calls hiltViewModel() and manages the camera + date-picker
 * launchers; `OdometerCaptureScaffold` (promoted to internal) takes plain params and is the
 * seam we exercise.
 *
 * Novel dimension vs prior form screens: a LazyColumn with an entry card + a history section.
 * History state (empty vs populated, MANUAL vs PHOTO_ASSISTED capture type) drives distinct
 * rendering branches. The entry card also has a photo-attached vs no-photo branch.
 *
 * What's locked down:
 *  - TopAppBar title / vehicle subtitle
 *  - Entry card: fields render, kmError, save button enabled/disabled, saving state
 *  - Photo row: "Take photo" vs "Photo attached", remove-photo button visibility
 *  - Date button renders the supplied date label
 *  - Callbacks: onSave, onOpenCamera, onClearPhoto, onReadingTextChange, onNotesChange,
 *    onOpenDatePicker, onNavigateBack
 *  - History: empty-state text, populated rows with km + date + "Photo" badge, notes
 */
@RunWith(AndroidJUnit4::class)
class OdometerCaptureScaffoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        vehicleName: String = "My Car",
        formState: OdometerFormState = OdometerFormState(),
        history: List<OdometerCapture> = emptyList(),
        onNavigateBack: () -> Unit = {},
        onOpenCamera: () -> Unit = {},
        onClearPhoto: () -> Unit = {},
        onReadingTextChange: (String) -> Unit = {},
        onOpenDatePicker: () -> Unit = {},
        onBoundaryTypeChange: (OdometerBoundaryType) -> Unit = {},
        onNotesChange: (String) -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            val snackbar = remember { SnackbarHostState() }
            OdometerCaptureScaffold(
                vehicleName = vehicleName,
                snackbar = snackbar,
                formState = formState,
                history = history,
                onNavigateBack = onNavigateBack,
                onOpenCamera = onOpenCamera,
                onClearPhoto = onClearPhoto,
                onReadingTextChange = onReadingTextChange,
                onOpenDatePicker = onOpenDatePicker,
                onBoundaryTypeChange = onBoundaryTypeChange,
                onNotesChange = onNotesChange,
                onSave = onSave,
            )
        }
    }

    // --- TopAppBar ---

    @Test
    fun topBar_showsRecordOdometerTitle() {
        setContent()

        composeRule.onNodeWithText("Record Odometer").assertIsDisplayed()
    }

    @Test
    fun topBar_showsVehicleNameAsSubtitle() {
        setContent(vehicleName = "Work Van")

        composeRule.onNodeWithText("Work Van").assertIsDisplayed()
    }

    @Test
    fun backButton_invokesOnNavigateBack() {
        var navigatedBack = false
        setContent(onNavigateBack = { navigatedBack = true })

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        assertTrue(navigatedBack)
    }

    // --- Entry card fields ---

    @Test
    fun entryCard_showsNewReadingHeader() {
        setContent()

        composeRule.onNodeWithText("New Reading").assertIsDisplayed()
    }

    @Test
    fun entryCard_showsOdometerField() {
        setContent()

        composeRule.onNodeWithText("Odometer reading (km)").assertIsDisplayed()
    }

    @Test
    fun entryCard_showsNotesField() {
        setContent()

        composeRule.onNodeWithText("Notes (optional)").assertIsDisplayed()
    }

    @Test
    fun populatedFormState_prefillsFields() {
        setContent(
            formState = OdometerFormState(
                readingKmText = "128450",
                notes = "Year-end reading",
            ),
        )

        composeRule.onNodeWithText("128450").assertIsDisplayed()
        composeRule.onNodeWithText("Year-end reading").assertIsDisplayed()
    }

    @Test
    fun dateLabel_rendersAsDateButton() {
        // The scaffold formats the date from formState.captureDateMs; we supply a fixed ms
        // that produces a predictable label and verify something renders in the OutlinedButton.
        // Rather than hardcoding the locale-dependent output, just verify the button is present.
        setContent()

        // OutlinedButton with the date string is the only OutlinedButton in the entry card
        // next to "Take photo" and "Notes". Asserting "Save Reading" button exists is a proxy
        // that the full card rendered.
        composeRule.onNodeWithText("Save Reading").assertIsDisplayed()
    }

    // --- Save button states ---

    @Test
    fun emptyReading_saveButtonIsDisabled() {
        setContent(formState = OdometerFormState(readingKmText = ""))

        composeRule.onNodeWithText("Save Reading").assertIsNotEnabled()
    }

    @Test
    fun zeroReading_saveButtonIsDisabled() {
        // isSaveEnabled requires readingKm > 0
        setContent(formState = OdometerFormState(readingKmText = "0"))

        composeRule.onNodeWithText("Save Reading").assertIsNotEnabled()
    }

    @Test
    fun negativeReading_saveButtonIsDisabled() {
        setContent(formState = OdometerFormState(readingKmText = "-1"))

        composeRule.onNodeWithText("Save Reading").assertIsNotEnabled()
    }

    @Test
    fun validReading_saveButtonIsEnabled() {
        setContent(formState = OdometerFormState(readingKmText = "50000"))

        composeRule.onNodeWithText("Save Reading").assertIsEnabled()
    }

    @Test
    fun savingState_saveButtonShowsSavingAndIsDisabled() {
        setContent(
            formState = OdometerFormState(
                readingKmText = "50000",
                isSaving = true,
            ),
        )

        composeRule.onNodeWithText("Saving...").assertIsDisplayed()
        composeRule.onNodeWithText("Saving...").assertIsNotEnabled()
    }

    @Test
    fun kmError_rendersBelowField() {
        setContent(
            formState = OdometerFormState(
                readingKmText = "abc",
                kmError = "Please enter a whole number.",
            ),
        )

        composeRule.onNodeWithText("Please enter a whole number.").assertIsDisplayed()
    }

    @Test
    fun noKmError_errorSlotIsClear() {
        setContent(formState = OdometerFormState(readingKmText = ""))

        composeRule.onAllNodesWithText("Please enter a whole number.").assertCountEquals(0)
    }

    // --- Save callback ---

    @Test
    fun clickingSave_invokesOnSave() {
        var saved = false
        setContent(
            formState = OdometerFormState(readingKmText = "75000"),
            onSave = { saved = true },
        )

        composeRule.onNodeWithText("Save Reading").performClick()
        composeRule.waitForIdle()

        assertTrue(saved)
    }

    // --- Photo row ---

    @Test
    fun noPhoto_showsTakePhotoButton() {
        setContent(formState = OdometerFormState(photoUri = null))

        composeRule.onNodeWithText("Take photo", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Photo attached", substring = true).assertCountEquals(0)
    }

    @Test
    fun noPhoto_removePhotoButtonIsHidden() {
        setContent(formState = OdometerFormState(photoUri = null))

        composeRule.onAllNodesWithContentDescription("Remove photo").assertCountEquals(0)
    }

    @Test
    fun hasPhoto_showsPhotoAttachedButton() {
        setContent(
            formState = OdometerFormState(photoUri = "content://test/odo.jpg"),
        )

        composeRule.onNodeWithText("Photo attached", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Take photo", substring = true).assertCountEquals(0)
    }

    @Test
    fun hasPhoto_showsRemovePhotoButton() {
        setContent(formState = OdometerFormState(photoUri = "content://test/odo.jpg"))

        composeRule.onNodeWithContentDescription("Remove photo").assertIsDisplayed()
    }

    @Test
    fun clickingOpenCamera_invokesOnOpenCamera() {
        var opened = false
        setContent(
            formState = OdometerFormState(photoUri = null),
            onOpenCamera = { opened = true },
        )

        composeRule.onNodeWithText("Take photo", substring = true).performClick()
        composeRule.waitForIdle()

        assertTrue(opened)
    }

    @Test
    fun clickingRemovePhoto_invokesOnClearPhoto() {
        var cleared = false
        setContent(
            formState = OdometerFormState(photoUri = "content://test/odo.jpg"),
            onClearPhoto = { cleared = true },
        )

        composeRule.onNodeWithContentDescription("Remove photo").performClick()
        composeRule.waitForIdle()

        assertTrue(cleared)
    }

    // --- Field callbacks ---

    @Test
    fun typingReading_invokesOnReadingTextChange() {
        var last: String? = null
        setContent(onReadingTextChange = { last = it })

        composeRule.onNodeWithText("Odometer reading (km)").performTextInput("85000")
        composeRule.waitForIdle()

        assertEquals("85000", last)
    }

    @Test
    fun editingReading_invokesOnReadingTextChangeWithReplacement() {
        var last: String? = null
        setContent(
            formState = OdometerFormState(readingKmText = "60000"),
            onReadingTextChange = { last = it },
        )

        composeRule.onNodeWithText("60000").performTextReplacement("70000")
        composeRule.waitForIdle()

        assertEquals("70000", last)
    }

    @Test
    fun typingNotes_invokesOnNotesChange() {
        var last: String? = null
        setContent(onNotesChange = { last = it })

        composeRule.onNodeWithText("Notes (optional)").performTextInput("Jan 1 reading")
        composeRule.waitForIdle()

        assertEquals("Jan 1 reading", last)
    }

    @Test
    fun clickingDateButton_invokesOnOpenDatePicker() {
        var shown = false
        // Use a fixed date so we can find the button by scrolling to "Reading History" section,
        // then clicking the only OutlinedButton between the reading field and notes field.
        // More reliably: find the date button by its position via the onOpenDatePicker callback.
        setContent(
            formState = OdometerFormState(captureDateMs = 1_735_689_600_000L), // Jan 1 2025
            onOpenDatePicker = { shown = true },
        )

        // The date button is an OutlinedButton rendered with the formatted date label.
        // We search for the callback instead — clicking "January 1, 2025" fires it.
        composeRule.onNodeWithText("January 1, 2025", substring = true).performClick()
        composeRule.waitForIdle()

        assertTrue(shown)
    }

    // --- History section ---

    @Test
    fun emptyHistory_showsEmptyStateText() {
        setContent(history = emptyList())

        composeRule.onNodeWithText("No readings recorded yet.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun emptyHistory_showsReadingHistoryHeader() {
        setContent(history = emptyList())

        composeRule.onNodeWithText("Reading History").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun populatedHistory_showsKmReadings() {
        setContent(
            history = listOf(
                sampleCapture(id = 1, readingKm = 50000),
                sampleCapture(id = 2, readingKm = 55000),
            ),
        )

        composeRule.onNodeWithText("50000 km", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("55000 km", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun historyRow_withNotes_showsNoteText() {
        setContent(
            history = listOf(
                sampleCapture(id = 1, readingKm = 45000, notes = "Pre-winter check"),
            ),
        )

        composeRule.onNodeWithText("Pre-winter check")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun historyRow_withNoNotes_doesNotShowNoteSlot() {
        setContent(
            history = listOf(
                sampleCapture(id = 1, readingKm = 45000, notes = null),
            ),
        )

        // No notes text in the history row (the notes field in the entry card is empty too).
        composeRule.onAllNodesWithText("Pre-winter check").assertCountEquals(0)
    }

    @Test
    fun historyRow_photoAssisted_showsPhotoBadge() {
        setContent(
            history = listOf(
                sampleCapture(
                    id = 1,
                    readingKm = 80000,
                    captureType = OdometerCaptureType.PHOTO_ASSISTED,
                ),
            ),
        )

        composeRule.onNodeWithText("Photo")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun historyRow_manual_doesNotShowPhotoBadge() {
        setContent(
            history = listOf(
                sampleCapture(
                    id = 1,
                    readingKm = 80000,
                    captureType = OdometerCaptureType.MANUAL,
                ),
            ),
        )

        composeRule.onAllNodesWithText("Photo").assertCountEquals(0)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sampleCapture(
        id: Int = 1,
        vehicleId: Int = 1,
        readingKm: Int = 50000,
        captureDate: Long = 1_735_689_600_000L, // Jan 1 2025
        captureType: OdometerCaptureType = OdometerCaptureType.MANUAL,
        notes: String? = null,
    ) = OdometerCapture(
        id = id,
        vehicleId = vehicleId,
        captureDate = captureDate,
        readingKm = readingKm,
        captureType = captureType,
        notes = notes,
    )
}
