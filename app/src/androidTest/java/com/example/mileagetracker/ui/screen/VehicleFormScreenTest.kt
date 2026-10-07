package com.example.mileagetracker.ui.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Form-screen test template. Same pattern works for RuleFormScreen, AddExpenseScreen,
 * ManualTripEntryScreen — anything that takes plain params and emits a built domain entity
 * via an onSave callback.
 *
 * What we lock down:
 *  - Title variant (Add vs Edit)
 *  - Field prefill from an existing entity
 *  - Required-field validation (Save disabled until form is valid)
 *  - Inline error messages
 *  - Input filtering (year digits-only, odometer digits+decimal)
 *  - Save callback emits a correctly-built entity (digits parsed, blanks nulled out,
 *    timestamp set, existing id preserved on edit)
 *  - Cancel callback
 */
@RunWith(AndroidJUnit4::class)
class VehicleFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        existingVehicle: VehicleEntity? = null,
        ownerUid: String = "owner-1",
        fiscalYear: Int = 2026,
        onSave: (VehicleEntity) -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        composeRule.setContent {
            VehicleFormScreen(
                existingVehicle = existingVehicle,
                ownerUid = ownerUid,
                fiscalYear = fiscalYear,
                onSave = { onSave(it.vehicle) },
                onCancel = onCancel,
            )
        }
    }

    // --- Title variants ---

    @Test
    fun newVehicle_showsAddTitle() {
        setContent(existingVehicle = null)

        composeRule.onNodeWithText("Add Vehicle").assertIsDisplayed()
    }

    @Test
    fun existingVehicle_showsEditTitle() {
        setContent(existingVehicle = sampleVehicle())

        composeRule.onNodeWithText("Edit Vehicle").assertIsDisplayed()
    }

    // --- Field rendering ---

    @Test
    fun allFields_areRendered() {
        setContent()

        composeRule.onNodeWithText("Vehicle Name").assertIsDisplayed()
        composeRule.onNodeWithText("Make").assertIsDisplayed()
        composeRule.onNodeWithText("Model").assertIsDisplayed()
        composeRule.onNodeWithText("Year").assertIsDisplayed()
        composeRule.onNodeWithText("Licence Plate").assertIsDisplayed()
        composeRule.onNodeWithText("Ownership Type").assertIsDisplayed()
    }

    @Test
    fun existingVehicle_prefillsFieldValues() {
        val vehicle = sampleVehicle(
            displayName = "Daily Driver",
            make = "Honda",
            model = "Civic",
            year = 2021,
            vehicleType = "ABC-123",
            openingOdometerKm = 12345f,
        )
        setContent(existingVehicle = vehicle)

        composeRule.onNodeWithText("Daily Driver").assertIsDisplayed()
        composeRule.onNodeWithText("Honda").assertIsDisplayed()
        composeRule.onNodeWithText("Civic").assertIsDisplayed()
        composeRule.onNodeWithText("2021").assertIsDisplayed()
        composeRule.onNodeWithText("ABC-123").assertIsDisplayed()
        composeRule.onNodeWithText("12345.0").assertIsDisplayed()
    }

    // --- Required-field validation ---

    @Test
    fun emptyName_saveButtonDisabled() {
        setContent(existingVehicle = null)

        composeRule.onNodeWithText("Save Vehicle").assertIsNotEnabled()
    }

    @Test
    fun emptyName_showsInlineError() {
        setContent(existingVehicle = null)

        composeRule.onNodeWithText("Enter a name for this vehicle.").assertIsDisplayed()
    }

    @Test
    fun nameProvided_saveButtonEnabled_andErrorCleared() {
        setContent(existingVehicle = null)

        composeRule.onNodeWithText("Vehicle Name").performTextInput("My Truck")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save Vehicle").assertIsEnabled()
        composeRule.onAllNodesWithText("Enter a name for this vehicle.").assertCountEquals(0)
    }

    // --- Input filtering ---

    @Test
    fun yearField_stripsNonDigits() {
        setContent()

        // Input length ≤ 4 passes the cap, then non-digits are filtered.
        composeRule.onNodeWithText("Year").performTextInput("20a1")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("201").assertIsDisplayed()
    }

    @Test
    fun yearField_capsAtFourDigits() {
        setContent()

        composeRule.onNodeWithText("Year").performTextInput("12345")
        composeRule.waitForIdle()

        // Input >4 chars is rejected entirely; field stays empty.
        composeRule.onAllNodesWithText("12345").assertCountEquals(0)
        composeRule.onAllNodesWithText("1234").assertCountEquals(0)
    }

    @Test
    fun openingOdometer_acceptsDigitsAndDecimalOnly() {
        setContent()

        composeRule.onNodeWithText("Opening odometer (km) — January 1")
            .performTextInput("1a2.3b4")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("12.34").assertIsDisplayed()
    }

    // --- onSave entity construction ---

    @Test
    fun saveNew_buildsVehicleWithOwnerAndFiscalYear() {
        var saved: VehicleEntity? = null
        setContent(
            ownerUid = "user-42",
            fiscalYear = 2026,
            onSave = { saved = it },
        )

        composeRule.onNodeWithText("Vehicle Name").performTextInput("My Truck")
        composeRule.onNodeWithText("Save Vehicle").performClick()
        composeRule.waitForIdle()

        assertNotNull("onSave should fire", saved)
        assertEquals("My Truck", saved!!.displayName)
        assertEquals("user-42", saved.ownerUid)
        assertEquals(2026, saved.fiscalYear)
        assertEquals(0, saved.id) // new vehicle: Room auto-id default
    }

    @Test
    fun saveNew_parsesYearAndOdometer() {
        var saved: VehicleEntity? = null
        setContent(onSave = { saved = it })

        composeRule.onNodeWithText("Vehicle Name").performTextInput("Sedan")
        composeRule.onNodeWithText("Year").performTextInput("2022")
        composeRule.onNodeWithText("Opening odometer (km) — January 1")
            .performTextInput("15000.5")
        composeRule.onNodeWithText("Save Vehicle").performClick()
        composeRule.waitForIdle()

        assertEquals(2022, saved!!.year)
        assertEquals(15000.5f, saved.openingOdometerKm)
    }

    @Test
    fun saveNew_blanksAreNulled() {
        var saved: VehicleEntity? = null
        setContent(onSave = { saved = it })

        composeRule.onNodeWithText("Vehicle Name").performTextInput("Just a name")
        composeRule.onNodeWithText("Save Vehicle").performClick()
        composeRule.waitForIdle()

        // Blank make/model/plate/year/odometer should serialise as null, not "".
        assertNull(saved!!.make)
        assertNull(saved.model)
        assertNull(saved.vehicleType)
        assertNull(saved.year)
        assertNull(saved.openingOdometerKm)
    }

    @Test
    fun saveExisting_preservesId() {
        val existing = sampleVehicle(id = 99, displayName = "Old Name")
        var saved: VehicleEntity? = null
        setContent(existingVehicle = existing, onSave = { saved = it })

        composeRule.onNodeWithText("Old Name").performTextReplacement("New Name")
        composeRule.onNodeWithText("Save Vehicle").performClick()
        composeRule.waitForIdle()

        assertEquals(99, saved!!.id)
        assertEquals("New Name", saved.displayName)
    }

    @Test
    fun saveNew_setsUpdatedAtTimestamp() {
        var saved: VehicleEntity? = null
        val before = System.currentTimeMillis()
        setContent(onSave = { saved = it })

        composeRule.onNodeWithText("Vehicle Name").performTextInput("Truck")
        composeRule.onNodeWithText("Save Vehicle").performClick()
        composeRule.waitForIdle()

        val after = System.currentTimeMillis()
        assertTrue(
            "updatedAt should be set to current time",
            saved!!.updatedAt in before..after,
        )
    }

    // --- onCancel ---

    @Test
    fun cancelButton_invokesOnCancel() {
        var cancelled = false
        setContent(onCancel = { cancelled = true })

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertTrue(cancelled)
    }

    private fun sampleVehicle(
        id: Int = 1,
        ownerUid: String = "owner-1",
        displayName: String = "Sample",
        make: String? = null,
        model: String? = null,
        year: Int? = null,
        vehicleType: String? = null,
        openingOdometerKm: Float? = null,
        fiscalYear: Int = 2026,
        ownershipType: VehicleOwnershipType = VehicleOwnershipType.PERSONAL,
    ) = VehicleEntity(
        id = id,
        ownerUid = ownerUid,
        displayName = displayName,
        make = make,
        model = model,
        year = year,
        vehicleType = vehicleType,
        openingOdometerKm = openingOdometerKm,
        fiscalYear = fiscalYear,
        ownershipType = ownershipType,
    )
}
