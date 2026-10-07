package com.example.mileagetracker.ui.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.ui.viewmodel.RuleFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Form-template test for RuleFormScreen, applied to the `internal` RuleFormScaffold seam.
 *
 * 'fun createComposeRule(effectContext: CoroutineContext = ...): ComposeContentTestRule' is deprecated.
 * Use `androidx.compose.ui.test.junit4.v2.createComposeRule`
 * The public `RuleFormScreen` calls hiltViewModel() so we can't drive it directly; the scaffold
 * takes a plain `RuleFormState` plus callback lambdas, which is the seam we exercise.
 *
 * Same structure as VehicleFormScreenTest:
 *  - Title variants (New vs Edit) via formState.editingId
 *  - Field rendering and prefill from a populated state
 *  - Inline error display when formState carries *Error fields
 *  - Each field's onChange callback fires with the correct value
 *  - Save button label changes between create / edit
 *  - Save button invokes onSave
 *  - Back navigation invokes onNavigateBack
 *  - Enabled toggle reflects state and emits change events
 */
@RunWith(AndroidJUnit4::class)
class RuleFormScaffoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        formState: RuleFormState = RuleFormState(),
        onNavigateBack: () -> Unit = {},
        onNameChange: (String) -> Unit = {},
        onKeywordChange: (String) -> Unit = {},
        onCategoryChange: (TripCategory) -> Unit = {},
        onConfidenceChange: (String) -> Unit = {},
        onPriorityChange: (String) -> Unit = {},
        onEnabledChange: (Boolean) -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            RuleFormScaffold(
                formState = formState,
                snackbarHostState = snackbarHostState,
                onNavigateBack = onNavigateBack,
                onNameChange = onNameChange,
                onKeywordChange = onKeywordChange,
                onCategoryChange = onCategoryChange,
                onConfidenceChange = onConfidenceChange,
                onPriorityChange = onPriorityChange,
                onEnabledChange = onEnabledChange,
                onSave = onSave,
            )
        }
    }

    // --- Title variants (create vs edit) ---

    @Test
    fun createMode_showsNewRuleTitle() {
        setContent(formState = RuleFormState(editingId = null))

        composeRule.onNodeWithText("New Rule").assertIsDisplayed()
    }

    @Test
    fun editMode_showsEditRuleTitle() {
        setContent(formState = RuleFormState(editingId = 42))

        composeRule.onNodeWithText("Edit Rule").assertIsDisplayed()
    }

    @Test
    fun createMode_saveButtonSaysCreateRule() {
        setContent(formState = RuleFormState(editingId = null))

        composeRule.onNodeWithText("Create Rule").assertIsDisplayed()
        composeRule.onAllNodesWithText("Save Changes").assertCountEquals(0)
    }

    @Test
    fun editMode_saveButtonSaysSaveChanges() {
        setContent(formState = RuleFormState(editingId = 42))

        composeRule.onNodeWithText("Save Changes").assertIsDisplayed()
        composeRule.onAllNodesWithText("Create Rule").assertCountEquals(0)
    }

    // --- Field rendering ---

    @Test
    fun allFields_areRendered() {
        setContent()

        composeRule.onNodeWithText("Rule name").assertIsDisplayed()
        composeRule.onNodeWithText("Keyword").assertIsDisplayed()
        composeRule.onNodeWithText("Confidence (%)").assertIsDisplayed()
        composeRule.onNodeWithText("Priority").assertIsDisplayed()
        composeRule.onNodeWithText("Enabled").assertIsDisplayed()
    }

    @Test
    fun populatedState_prefillsFieldValues() {
        setContent(
            formState = RuleFormState(
                editingId = 7,
                name = "Client ABC",
                keyword = "abc",
                category = TripCategory.BUSINESS,
                confidencePct = "85",
                priority = "10",
                isEnabled = true,
            ),
        )

        composeRule.onNodeWithText("Client ABC").assertIsDisplayed()
        composeRule.onNodeWithText("abc").assertIsDisplayed()
        composeRule.onNodeWithText("85").assertIsDisplayed()
        composeRule.onNodeWithText("10").assertIsDisplayed()
    }

    @Test
    fun defaultState_usesDefaultConfidenceAndPriority() {
        setContent()

        composeRule.onNodeWithText("75").assertIsDisplayed()
        composeRule.onNodeWithText("50").assertIsDisplayed()
    }

    // --- Inline validation errors ---

    @Test
    fun nameError_rendersBelowField() {
        setContent(
            formState = RuleFormState(nameError = "Name cannot be empty"),
        )

        composeRule.onNodeWithText("Name cannot be empty").assertIsDisplayed()
    }

    @Test
    fun keywordError_rendersBelowField() {
        setContent(
            formState = RuleFormState(keywordError = "Keyword too short"),
        )

        composeRule.onNodeWithText("Keyword too short").assertIsDisplayed()
    }

    @Test
    fun confidenceError_rendersBelowField() {
        setContent(
            formState = RuleFormState(confidenceError = "Must be 0–100"),
        )

        composeRule.onNodeWithText("Must be 0–100").assertIsDisplayed()
    }

    @Test
    fun priorityError_rendersBelowField() {
        setContent(
            formState = RuleFormState(priorityError = "Priority must be positive"),
        )

        composeRule.onNodeWithText("Priority must be positive").assertIsDisplayed()
    }

    @Test
    fun noErrors_supportingTextSlotsAreClear() {
        setContent(formState = RuleFormState())

        // None of the error strings used elsewhere should appear.
        composeRule.onAllNodesWithText("Name cannot be empty").assertCountEquals(0)
        composeRule.onAllNodesWithText("Keyword too short").assertCountEquals(0)
    }

    // --- Field onChange callbacks ---

    @Test
    fun typingName_invokesOnNameChange() {
        var lastName: String? = null
        setContent(onNameChange = { lastName = it })

        composeRule.onNodeWithText("Rule name").performTextInput("Hospital")
        composeRule.waitForIdle()

        assertEquals("Hospital", lastName)
    }

    @Test
    fun typingKeyword_invokesOnKeywordChange() {
        var lastKeyword: String? = null
        setContent(onKeywordChange = { lastKeyword = it })

        composeRule.onNodeWithText("Keyword").performTextInput("hospital")
        composeRule.waitForIdle()

        assertEquals("hospital", lastKeyword)
    }

    @Test
    fun editingConfidence_invokesOnConfidenceChange() {
        var lastConfidence: String? = null
        setContent(
            formState = RuleFormState(confidencePct = "75"),
            onConfidenceChange = { lastConfidence = it },
        )

        composeRule.onNodeWithText("75").performTextReplacement("90")
        composeRule.waitForIdle()

        assertEquals("90", lastConfidence)
    }

    @Test
    fun editingPriority_invokesOnPriorityChange() {
        var lastPriority: String? = null
        setContent(
            formState = RuleFormState(priority = "50"),
            onPriorityChange = { lastPriority = it },
        )

        composeRule.onNodeWithText("50").performTextReplacement("5")
        composeRule.waitForIdle()

        assertEquals("5", lastPriority)
    }

    // --- Enabled toggle ---

    @Test
    fun enabledToggle_reflectsTrueState() {
        setContent(formState = RuleFormState(isEnabled = true))

        // The "Enabled" Text and the Switch are sibling composables; Toggle state lives on the
        // Switch's semantics node, found via the isToggleable matcher (only one in this scaffold).
        composeRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun enabledToggle_reflectsFalseState() {
        setContent(formState = RuleFormState(isEnabled = false))

        composeRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun togglingEnabled_invokesCallback() {
        var lastEnabled: Boolean? = null
        setContent(
            formState = RuleFormState(isEnabled = true),
            onEnabledChange = { lastEnabled = it },
        )

        composeRule.onNode(isToggleable()).performClick()
        composeRule.waitForIdle()

        assertEquals(false, lastEnabled)
    }

    // --- Save & back ---

    @Test
    fun clickingSave_invokesOnSave() {
        var saved = false
        setContent(onSave = { saved = true })

        composeRule.onNodeWithText("Create Rule").performClick()
        composeRule.waitForIdle()

        assertTrue(saved)
    }

    @Test
    fun clickingSaveInEditMode_invokesOnSave() {
        var saved = false
        setContent(
            formState = RuleFormState(editingId = 5),
            onSave = { saved = true },
        )

        composeRule.onNodeWithText("Save Changes").performClick()
        composeRule.waitForIdle()

        assertTrue(saved)
    }

    @Test
    fun backButton_invokesOnNavigateBack() {
        var navigatedBack = false
        setContent(onNavigateBack = { navigatedBack = true })

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        assertTrue(navigatedBack)
    }
}
