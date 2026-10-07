package com.example.mileagetracker.ui.expense

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Form-template test for AddExpenseScreen, applied to the `internal` ExpenseFormContent seam.
 *
 * The public `AddExpenseScreen` calls hiltViewModel() and also wires up an OCR-prefill side
 * effect that reads from NavController.savedStateHandle. We extract the form content piece
 * here; the OCR consumption path is structurally separate (a LaunchedEffect over four
 * SavedStateHandle StateFlows) and is left for its own focused test class.
 *
 * What's locked down:
 *  - Title is owned by the outer composable, not tested here; save button label is the
 *    in-content create-vs-edit indicator we cover instead.
 *  - All fields render and prefill from a populated state.
 *  - Each callback (amount, vendor, GST, notes, business-use toggle) fires correctly.
 *  - Scan Receipt button triggers nav to Screen.OcrReceipt (via a stub NavHost).
 *  - Date picker trigger and save callback fire.
 *  - CRA category note text appears in the form.
 *
 * Pattern note: ExpenseFormContent takes a NavController (only used by Scan Receipt). We wire
 * a minimal stub NavHost where the form is the start destination and Screen.OcrReceipt is a
 * tagged stub destination, so a click on Scan Receipt navigates and we assert by tag.
 */
@RunWith(AndroidJUnit4::class)
class AddExpenseScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val formRoute = "form_under_test"

    private fun setContent(
        state: ExpenseFormState = ExpenseFormState(vehicleId = 1),
        onAmountChanged: (String) -> Unit = {},
        onCategorySelected: (ExpenseCategory) -> Unit = {},
        onVendorChanged: (String) -> Unit = {},
        onGstHstChanged: (String) -> Unit = {},
        onBusinessUseChanged: (Boolean) -> Unit = {},
        onNotesChanged: (String) -> Unit = {},
        onShowDatePicker: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            val navController = rememberNavController()
            HostWithOcrStub(
                navController = navController,
                formContent = {
                    ExpenseFormContent(
                        navController = navController,
                        state = state,
                        dateLabel = "Jan 15, 2026",
                        categoryExpanded = false,
                        onCategoryExpandedChange = {},
                        onShowDatePicker = onShowDatePicker,
                        onAmountChanged = onAmountChanged,
                        onCategorySelected = onCategorySelected,
                        onVendorChanged = onVendorChanged,
                        onGstHstChanged = onGstHstChanged,
                        onBusinessUseChanged = onBusinessUseChanged,
                        onNotesChanged = onNotesChanged,
                        onSave = onSave,
                        innerPadding = PaddingValues(0.dp),
                    )
                },
            )
        }
    }

    // --- Field rendering ---

    @Test
    fun allFields_areRendered() {
        setContent()

        composeRule.onNodeWithText("Scan Receipt", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Amount (CAD)").assertIsDisplayed()
        composeRule.onNodeWithText("Category").assertIsDisplayed()
        composeRule.onNodeWithText("Date").assertIsDisplayed()
        composeRule.onNodeWithText("Vendor (optional)").assertIsDisplayed()
        composeRule.onNodeWithText("GST/HST Amount (optional)").assertIsDisplayed()
        composeRule.onNodeWithText("Business use?").assertIsDisplayed()
        composeRule.onNodeWithText("Notes (optional)").assertIsDisplayed()
    }

    @Test
    fun populatedState_prefillsFieldValues() {
        setContent(
            state = ExpenseFormState(
                vehicleId = 1,
                amountRaw = "45.67",
                category = ExpenseCategory.Fuel,
                vendorName = "Petro-Canada",
                gstHstRaw = "5.94",
                notes = "Highway 401 trip",
                isBusinessUse = true,
            ),
        )

        composeRule.onNodeWithText("45.67").assertIsDisplayed()
        composeRule.onNodeWithText("Fuel & Oil").assertIsDisplayed()
        composeRule.onNodeWithText("Petro-Canada").assertIsDisplayed()
        composeRule.onNodeWithText("5.94").assertIsDisplayed()
        composeRule.onNodeWithText("Highway 401 trip").assertIsDisplayed()
    }

    @Test
    fun dateLabel_rendersInDateField() {
        setContent()

        composeRule.onNodeWithText("Jan 15, 2026").assertIsDisplayed()
    }

    // --- Save button label variants ---

    @Test
    fun createMode_saveButtonSaysSaveExpense() {
        setContent(state = ExpenseFormState(vehicleId = 1, editingId = 0))

        composeRule.onNodeWithText("Save Expense").assertIsDisplayed()
        composeRule.onAllNodesWithText("Update Expense").assertCountEquals(0)
    }

    @Test
    fun editMode_saveButtonSaysUpdateExpense() {
        setContent(state = ExpenseFormState(vehicleId = 1, editingId = 17))

        composeRule.onNodeWithText("Update Expense").assertIsDisplayed()
        composeRule.onAllNodesWithText("Save Expense").assertCountEquals(0)
    }

    // --- Inline validation ---

    @Test
    fun amountError_rendersBelowField() {
        setContent(
            state = ExpenseFormState(
                vehicleId = 1,
                amountRaw = "abc",
                amountError = "Amount must be a positive number.",
            ),
        )

        composeRule.onNodeWithText("Amount must be a positive number.").assertIsDisplayed()
    }

    @Test
    fun noAmountError_supportingTextSlotIsClear() {
        setContent(state = ExpenseFormState(vehicleId = 1, amountRaw = "12.34"))

        composeRule.onAllNodesWithText("Amount must be a positive number.")
            .assertCountEquals(0)
    }

    // --- CRA note display ---

    @Test
    fun craNote_isShownForSelectedCategory() {
        setContent(
            state = ExpenseFormState(vehicleId = 1, category = ExpenseCategory.Parking),
        )

        composeRule.onNodeWithText(
            "Business parking fees are 100",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun craNote_updatesWhenCategoryChanges() {
        setContent(
            state = ExpenseFormState(vehicleId = 1, category = ExpenseCategory.Tolls),
        )

        composeRule.onNodeWithText("Bridge and highway tolls", substring = true)
            .assertIsDisplayed()
    }

    // --- Field onChange callbacks ---

    @Test
    fun typingAmount_invokesOnAmountChanged() {
        var lastAmount: String? = null
        setContent(onAmountChanged = { lastAmount = it })

        composeRule.onNodeWithText("Amount (CAD)").performTextInput("12.50")
        composeRule.waitForIdle()

        assertEquals("12.50", lastAmount)
    }

    @Test
    fun editingAmount_invokesOnAmountChangedWithReplacement() {
        var lastAmount: String? = null
        setContent(
            state = ExpenseFormState(vehicleId = 1, amountRaw = "10.00"),
            onAmountChanged = { lastAmount = it },
        )

        composeRule.onNodeWithText("10.00").performTextReplacement("25.75")
        composeRule.waitForIdle()

        assertEquals("25.75", lastAmount)
    }

    @Test
    fun typingVendor_invokesOnVendorChanged() {
        var lastVendor: String? = null
        setContent(onVendorChanged = { lastVendor = it })

        composeRule.onNodeWithText("Vendor (optional)").performTextInput("Shell")
        composeRule.waitForIdle()

        assertEquals("Shell", lastVendor)
    }

    @Test
    fun typingGstHst_invokesOnGstHstChanged() {
        var lastGst: String? = null
        setContent(onGstHstChanged = { lastGst = it })

        composeRule.onNodeWithText("GST/HST Amount (optional)").performTextInput("1.95")
        composeRule.waitForIdle()

        assertEquals("1.95", lastGst)
    }

    @Test
    fun typingNotes_invokesOnNotesChanged() {
        var lastNotes: String? = null
        setContent(onNotesChanged = { lastNotes = it })

        composeRule.onNodeWithText("Notes (optional)").performTextInput("Receipt #4421")
        composeRule.waitForIdle()

        assertEquals("Receipt #4421", lastNotes)
    }

    // --- Business-use toggle ---

    @Test
    fun businessUseToggle_reflectsTrueState() {
        setContent(state = ExpenseFormState(vehicleId = 1, isBusinessUse = true))

        composeRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun businessUseToggle_reflectsFalseState() {
        setContent(state = ExpenseFormState(vehicleId = 1, isBusinessUse = false))

        composeRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun togglingBusinessUse_invokesCallback() {
        var lastValue: Boolean? = null
        setContent(
            state = ExpenseFormState(vehicleId = 1, isBusinessUse = true),
            onBusinessUseChanged = { lastValue = it },
        )

        composeRule.onNode(isToggleable()).performClick()
        composeRule.waitForIdle()

        assertEquals(false, lastValue)
    }

    // --- Date picker trigger ---

    @Test
    fun changeDateButton_invokesOnShowDatePicker() {
        var shown = false
        setContent(onShowDatePicker = { shown = true })

        composeRule.onNodeWithText("Change").performClick()
        composeRule.waitForIdle()

        assertTrue(shown)
    }

    // --- Save callback ---

    @Test
    fun clickingSaveInCreateMode_invokesOnSave() {
        var saved = false
        setContent(
            state = ExpenseFormState(vehicleId = 1, editingId = 0),
            onSave = { saved = true },
        )

        composeRule.onNodeWithText("Save Expense").performClick()
        composeRule.waitForIdle()

        assertTrue(saved)
    }

    @Test
    fun clickingSaveInEditMode_invokesOnSave() {
        var saved = false
        setContent(
            state = ExpenseFormState(vehicleId = 1, editingId = 9),
            onSave = { saved = true },
        )

        composeRule.onNodeWithText("Update Expense").performClick()
        composeRule.waitForIdle()

        assertTrue(saved)
    }

    // --- Scan Receipt navigation ---

    @Test
    fun scanReceiptButton_navigatesToOcrReceipt() {
        setContent()

        // Before click: form route is composed.
        composeRule.onNodeWithTag("dest-$formRoute").assertIsDisplayed()

        composeRule.onNodeWithText("Scan Receipt", substring = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dest-${Screen.OcrReceipt.route}").assertIsDisplayed()
    }

    /**
     * Hosts the form under test as the start destination plus a tagged stub for
     * Screen.OcrReceipt, so clicking Scan Receipt navigates to a node the test can find.
     */
    @Composable
    private fun HostWithOcrStub(
        navController: NavHostController,
        formContent: @Composable () -> Unit,
    ) {
        NavHost(navController = navController, startDestination = formRoute) {
            composable(formRoute) {
                Box(modifier = Modifier.testTag("dest-$formRoute")) { formContent() }
            }
            composable(Screen.OcrReceipt.route) {
                Text(
                    text = Screen.OcrReceipt.route,
                    modifier = Modifier.testTag("dest-${Screen.OcrReceipt.route}"),
                )
            }
        }
    }
}
