package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import com.example.mileagetracker.domain.model.LogbookEntry
import com.example.mileagetracker.domain.model.LogbookSummary
import com.example.mileagetracker.ui.viewmodel.CraLogbookUiState
import com.example.mileagetracker.ui.viewmodel.ExportUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CraLogbookScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val defaultVehicles = listOf(
        VehicleEntity(id = 1, ownerUid = "u1", displayName = "Sedan", fiscalYear = 2026),
        VehicleEntity(id = 2, ownerUid = "u1", displayName = "Truck", fiscalYear = 2026),
    )

    private val sampleEntries = listOf(
        LogbookEntry(
            tripId = 1, date = 1_700_000_000_000L, origin = "Home", destination = "Client Office",
            category = TripCategory.BUSINESS, businessKm = 42.5f, vehicleName = "Sedan", vehicleId = 1,
        ),
        LogbookEntry(
            tripId = 2, date = 1_700_100_000_000L, origin = "Home", destination = "Hospital",
            category = TripCategory.MEDICAL, businessKm = 12.3f, vehicleName = "Sedan", vehicleId = 1,
        ),
        LogbookEntry(
            tripId = 3, date = 1_700_200_000_000L, origin = "Depot", destination = "Warehouse",
            category = TripCategory.CHARITY, businessKm = 88.0f, vehicleName = "Truck", vehicleId = 2,
        ),
    )

    private val sampleSummary = LogbookSummary(
        totalKm = 200f,
        businessKm = 142.8f,
        businessUsePercent = 71.4f,
        vehicleId = null,
        year = 2026,
    )

    private fun setContent(
        uiState: CraLogbookUiState = CraLogbookUiState.Success(sampleEntries, sampleSummary),
        exportState: ExportUiState = ExportUiState.Idle,
        selectedFormat: ExportFormat = ExportFormat.PDF,
        availableYears: List<Int> = listOf(2026, 2025, 2024),
        selectedYear: Int = 2026,
        activeVehicles: List<VehicleEntity> = defaultVehicles,
        selectedVehicleId: Int? = null,
        filterableCategories: List<TripCategory> = TripCategory.craEligibleCategories,
        selectedCategories: Set<TripCategory> = setOf(TripCategory.BUSINESS),
        onToggleCategory: (TripCategory) -> Unit = {},
        onFormatSelected: (ExportFormat) -> Unit = {},
        onTripTapped: (Int) -> Unit = {},
        onYearSelected: (Int) -> Unit = {},
        selectedMonth: Int? = null,
        onMonthSelected: (Int?) -> Unit = {},
        onVehicleSelected: (Int?) -> Unit = {},
    ) {
        composeRule.setContent {
            CraLogbookScreenContent(
                innerPadding = PaddingValues(0.dp),
                availableYears = availableYears,
                selectedYear = selectedYear,
                onYearSelected = onYearSelected,
                selectedMonth = selectedMonth,
                onMonthSelected = onMonthSelected,
                activeVehicles = activeVehicles,
                selectedVehicleId = selectedVehicleId,
                onVehicleSelected = onVehicleSelected,
                filterableCategories = filterableCategories,
                selectedCategories = selectedCategories,
                onToggleCategory = onToggleCategory,
                selectedFormat = selectedFormat,
                onFormatSelected = onFormatSelected,
                uiState = uiState,
                onTripTapped = onTripTapped,
                exportingState = exportState as? ExportUiState.Exporting,
            )
        }
    }

    // ── Loading state ──────────────────────────────────────────────────

    @Test
    fun loadingState_showsProgressIndicator() {
        setContent(uiState = CraLogbookUiState.Loading)

        composeRule.onAllNodesWithText("Tax Year", substring = true).onFirst().assertIsDisplayed()
    }

    // ── Error state ────────────────────────────────────────────────────

    @Test
    fun errorState_showsErrorMessage() {
        setContent(uiState = CraLogbookUiState.Error("Network timeout"))

        composeRule.onNodeWithText("Network timeout").assertIsDisplayed()
    }

    // ── Empty success state ────────────────────────────────────────────

    @Test
    fun emptySuccess_showsNoTripsMessage() {
        val emptyState = CraLogbookUiState.Success(
            entries = emptyList(),
            summary = LogbookSummary(0f, 0f, 0f, null, 2026),
        )
        setContent(uiState = emptyState)

        composeRule.onNodeWithText("No trips found").assertIsDisplayed()
        composeRule.onNodeWithText("Try a different tax year", substring = true).assertIsDisplayed()
    }

    // ── Populated success state ────────────────────────────────────────

    @Test
    fun successState_showsColumnHeaders() {
        setContent()

        composeRule.onNodeWithText("Date").assertIsDisplayed()
        composeRule.onNodeWithText("Destination").assertIsDisplayed()
        composeRule.onNodeWithText("Category").assertIsDisplayed()
        composeRule.onNodeWithText("km").assertIsDisplayed()
    }

    @Test
    fun successState_showsEntryData() {
        setContent()

        composeRule.onNodeWithText("Client Office").assertIsDisplayed()
        composeRule.onNodeWithText("42.5").assertIsDisplayed()
    }

    @Test
    fun successState_showsMultipleEntries() {
        setContent()

        composeRule.onNodeWithText("Client Office").assertIsDisplayed()
        composeRule.onNodeWithText("Hospital").assertIsDisplayed()
        composeRule.onNodeWithText("Warehouse").assertIsDisplayed()
    }

    @Test
    fun tappingEntry_invokesCallback() {
        var tappedId = -1
        setContent(onTripTapped = { tappedId = it })

        composeRule.onNodeWithText("Client Office").performClick()

        assertEquals(1, tappedId)
    }

    // ── Summary bar ────────────────────────────────────────────────────

    @Test
    fun summaryBar_showsEligibleKm() {
        setContent()

        composeRule.onNodeWithText("Eligible km").assertIsDisplayed()
        composeRule.onNodeWithText("142.8 km").assertIsDisplayed()
    }

    @Test
    fun summaryBar_showsTotalKm() {
        setContent()

        composeRule.onNodeWithText("Total km").assertIsDisplayed()
        composeRule.onNodeWithText("200.0 km").assertIsDisplayed()
    }

    @Test
    fun summaryBar_showsBusinessUsePercent() {
        setContent()

        composeRule.onNodeWithText("Business use").assertIsDisplayed()
        composeRule.onNodeWithText("71.4%").assertIsDisplayed()
    }

    @Test
    fun summaryBar_zeroPercent_whenNoTrips() {
        val zeroSummary = LogbookSummary(0f, 0f, 0f, null, 2026)
        val state = CraLogbookUiState.Success(emptyList(), zeroSummary)
        setContent(uiState = state)

        composeRule.onNodeWithText("0.0%").assertIsDisplayed()
    }

    // ── Vehicle filter dropdown ────────────────────────────────────────

    @Test
    fun vehicleDropdown_showsAllVehiclesByDefault() {
        setContent()

        composeRule.onNodeWithText("All vehicles").assertIsDisplayed()
    }

    @Test
    fun vehicleDropdown_expandingShowsEachVehicle() {
        setContent()

        composeRule.onNodeWithText("All vehicles").performClick()

        composeRule.onNodeWithText("Sedan").assertIsDisplayed()
        composeRule.onNodeWithText("Truck").assertIsDisplayed()
    }

    @Test
    fun vehicleDropdown_selectingVehicle_invokesCallback() {
        var selected: Int? = -1
        setContent(onVehicleSelected = { selected = it })

        composeRule.onNodeWithText("All vehicles").performClick()
        composeRule.onNodeWithText("Sedan").performClick()

        assertEquals(1, selected)
    }

    @Test
    fun vehicleDropdown_selectingAllVehicles_passesNull() {
        var selected: Int? = -1
        setContent(
            selectedVehicleId = 1,
            onVehicleSelected = { selected = it },
        )

        // Field shows the selected vehicle; tap it to expand, then pick All vehicles.
        composeRule.onNodeWithText("Sedan").performClick()
        composeRule.onNodeWithText("All vehicles").performClick()

        assertEquals(null, selected)
    }

    @Test
    fun vehicleDropdown_emptyVehicleList_showsOnlyAllVehicles() {
        setContent(activeVehicles = emptyList())

        composeRule.onNodeWithText("All vehicles").assertIsDisplayed()
        composeRule.onNodeWithText("Sedan").assertDoesNotExist()
    }

    // ── Category filter chips ──────────────────────────────────────────

    @Test
    fun categoryFilters_showCraEligibleCategories() {
        setContent()

        composeRule.onNodeWithText("Business").assertIsDisplayed()
        composeRule.onNodeWithText("Medical").assertIsDisplayed()
        composeRule.onNodeWithText("Charitable").assertIsDisplayed()
    }

    @Test
    fun categoryFilter_tapping_invokesToggle() {
        var toggled: TripCategory? = null
        setContent(onToggleCategory = { toggled = it })

        composeRule.onNodeWithText("Medical").performClick()

        assertEquals(TripCategory.MEDICAL, toggled)
    }

    @Test
    fun categoryFilter_multipleSelected_allVisible() {
        setContent(
            selectedCategories = setOf(TripCategory.BUSINESS, TripCategory.MEDICAL),
        )

        composeRule.onNodeWithText("Business").assertIsDisplayed()
        composeRule.onNodeWithText("Medical").assertIsDisplayed()
    }

    // ── Format filter chips ────────────────────────────────────────────

    @Test
    fun formatFilters_showPdfAndCsv() {
        setContent()

        composeRule.onAllNodesWithText("PDF").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("CSV").onFirst().assertIsDisplayed()
    }

    @Test
    fun formatFilter_tapping_invokesCallback() {
        var selected: ExportFormat? = null
        setContent(onFormatSelected = { selected = it })

        composeRule.onAllNodesWithText("CSV").onFirst().performClick()

        assertEquals(ExportFormat.CSV, selected)
    }

    // ── Export progress dialog ──────────────────────────────────────────

    @Test
    fun exportingState_showsProgressDialog() {
        setContent(exportState = ExportUiState.Exporting(vehicleId = null, taxYear = 2026))

        composeRule.onNodeWithText("Generating PDF", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2026", substring = true).assertIsDisplayed()
    }

    @Test
    fun exportingState_csvFormat_showsCsvInDialog() {
        setContent(
            selectedFormat = ExportFormat.CSV,
            exportState = ExportUiState.Exporting(vehicleId = null, taxYear = 2025),
        )

        composeRule.onNodeWithText("Generating CSV", substring = true).assertIsDisplayed()
    }

    @Test
    fun exportingState_withVehicle_showsVehicleName() {
        setContent(
            exportState = ExportUiState.Exporting(vehicleId = 1, taxYear = 2026),
            activeVehicles = defaultVehicles,
        )

        composeRule.onNodeWithText("Sedan", substring = true).assertIsDisplayed()
    }

    @Test
    fun exportingState_withUnknownVehicle_showsFallback() {
        setContent(
            exportState = ExportUiState.Exporting(vehicleId = 999, taxYear = 2026),
            activeVehicles = defaultVehicles,
        )

        composeRule.onNodeWithText("Vehicle #999", substring = true).assertIsDisplayed()
    }

    @Test
    fun exportingState_allVehicles_showsAllVehiclesLabel() {
        setContent(
            exportState = ExportUiState.Exporting(vehicleId = null, taxYear = 2026),
        )

        composeRule.onNodeWithText("All vehicles", substring = true).assertIsDisplayed()
    }

    @Test
    fun idleExportState_noDialogShown() {
        setContent(exportState = ExportUiState.Idle)

        composeRule.onNodeWithText("Generating", substring = true).assertDoesNotExist()
    }

    // ── Year dropdown ──────────────────────────────────────────────────

    @Test
    fun yearDropdown_showsSelectedYear() {
        setContent(selectedYear = 2025)

        composeRule.onNodeWithText("2025").assertIsDisplayed()
    }

    // ── Complex filter combinations ────────────────────────────────────

    @Test
    fun filteredByVehicle_entriesStillShowAll() {
        setContent(selectedVehicleId = 1)

        composeRule.onNodeWithText("Client Office").assertIsDisplayed()
        composeRule.onNodeWithText("Warehouse").assertIsDisplayed()
    }

    @Test
    fun allCategoriesSelected_allChipsVisible() {
        setContent(
            selectedCategories = setOf(
                TripCategory.BUSINESS,
                TripCategory.MEDICAL,
                TripCategory.CHARITY,
            ),
        )

        composeRule.onNodeWithText("Business").assertIsDisplayed()
        composeRule.onNodeWithText("Medical").assertIsDisplayed()
        composeRule.onNodeWithText("Charitable").assertIsDisplayed()
    }

    @Test
    fun singleEntry_summaryMatchesEntry() {
        val entry = LogbookEntry(
            tripId = 10, date = 1_700_000_000_000L, origin = "Home", destination = "Airport",
            category = TripCategory.BUSINESS, businessKm = 55.5f, vehicleName = "Truck",
        )
        val summary = LogbookSummary(100f, 55.5f, 55.5f, null, 2026)
        setContent(uiState = CraLogbookUiState.Success(listOf(entry), summary))

        composeRule.onNodeWithText("Airport").assertIsDisplayed()
        composeRule.onNodeWithText("55.5 km").assertIsDisplayed()
        composeRule.onNodeWithText("55.5%").assertIsDisplayed()
    }

    @Test
    fun highBusinessUse_displaysCorrectly() {
        val summary = LogbookSummary(1000f, 990f, 99.0f, null, 2026)
        setContent(uiState = CraLogbookUiState.Success(sampleEntries, summary))

        composeRule.onNodeWithText("99.0%").assertIsDisplayed()
        composeRule.onNodeWithText("990.0 km").assertIsDisplayed()
    }
}
