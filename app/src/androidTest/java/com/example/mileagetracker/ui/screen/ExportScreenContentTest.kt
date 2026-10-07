package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.ExportFormat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        availableYears: List<Int> = listOf(2026, 2025),
        selectedYear: Int = 2026,
        activeVehicles: List<VehicleEntity> = emptyList(),
        selectedVehicleId: Int? = null,
        selectedCategories: Set<TripCategory> = setOf(TripCategory.BUSINESS),
        filterableCategories: List<TripCategory> = listOf(
            TripCategory.BUSINESS,
            TripCategory.PERSONAL,
            TripCategory.MEDICAL,
        ),
        selectedFormat: ExportFormat = ExportFormat.PDF,
        isExporting: Boolean = false,
        onExport: () -> Unit = {},
        onFormatSelected: (ExportFormat) -> Unit = {},
    ) {
        composeRule.setContent {
            ExportScreenContent(
                innerPadding = PaddingValues(0.dp),
                availableYears = availableYears,
                selectedYear = selectedYear,
                onYearSelected = {},
                activeVehicles = activeVehicles,
                selectedVehicleId = selectedVehicleId,
                onVehicleSelected = {},
                selectedCategories = selectedCategories,
                filterableCategories = filterableCategories,
                onToggleCategory = {},
                selectedFormat = selectedFormat,
                onFormatSelected = onFormatSelected,
                isExporting = isExporting,
                onExport = onExport,
            )
        }
    }

    @Test
    fun allSections_areVisible() {
        setContent()

        composeRule.onAllNodesWithText("Tax Year", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Vehicle", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Business", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("PDF", substring = true).assertIsDisplayed()
    }

    @Test
    fun selectedYear_isDisplayed() {
        setContent(selectedYear = 2025)

        composeRule.onNodeWithText("2025").assertIsDisplayed()
    }

    @Test
    fun allVehiclesChip_selectedWhenNoVehicleId() {
        setContent(selectedVehicleId = null)

        composeRule.onNodeWithText("All vehicles", substring = true).assertIsDisplayed()
    }

    @Test
    fun categoryChips_showFilterableCategories() {
        setContent(
            filterableCategories = listOf(
                TripCategory.BUSINESS,
                TripCategory.PERSONAL,
                TripCategory.MEDICAL,
            ),
        )

        composeRule.onNodeWithText("Business").assertIsDisplayed()
        composeRule.onNodeWithText("Personal").assertIsDisplayed()
        composeRule.onNodeWithText("Medical").assertIsDisplayed()
    }

    @Test
    fun formatToggle_showsPdfAndCsv() {
        setContent()

        composeRule.onNodeWithText("PDF", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("CSV", substring = true).assertIsDisplayed()
    }

    @Test
    fun exportButton_invokesCallback() {
        var exported = false
        setContent(onExport = { exported = true })

        composeRule.onNodeWithText("PDF", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Export", substring = true).performClick()

        assertTrue(exported)
    }

    @Test
    fun exportingState_showsProgressIndicator() {
        setContent(isExporting = true)

        composeRule.onNodeWithText("Generating", substring = true).assertIsDisplayed()
    }

    @Test
    fun vehicleChips_showVehicleNames() {
        val vehicles = listOf(
            VehicleEntity(id = 1, ownerUid = "u1", displayName = "Sedan", fiscalYear = 2026),
            VehicleEntity(id = 2, ownerUid = "u1", displayName = "Truck", fiscalYear = 2026),
        )
        setContent(activeVehicles = vehicles)

        composeRule.onNodeWithText("Sedan", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Truck", substring = true).assertIsDisplayed()
    }
}
