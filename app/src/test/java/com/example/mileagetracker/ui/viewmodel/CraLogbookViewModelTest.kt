package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.LogbookSummary
import com.example.mileagetracker.domain.usecase.drive.GetCraLogbookTripsUseCase
import com.example.mileagetracker.domain.usecase.vehicle.GetActiveVehiclesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class CraLogbookViewModelTest {

    private lateinit var viewModel: CraLogbookViewModel
    private lateinit var getCraLogbookTripsUseCase: GetCraLogbookTripsUseCase
    private lateinit var getActiveVehiclesUseCase: GetActiveVehiclesUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getCraLogbookTripsUseCase = mockk()
        getActiveVehiclesUseCase = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "u1"

        val summary = LogbookSummary(100f, 70f, 70f, null, 2025)
        coEvery { getCraLogbookTripsUseCase(any(), any(), any(), any()) } returns
            (emptyList<com.example.mileagetracker.domain.model.LogbookEntry>() to summary)
        every { getActiveVehiclesUseCase(any(), any()) } returns flowOf(
            listOf(
                VehicleEntity(id = 1, ownerUid = "u1", displayName = "Sedan", fiscalYear = 2026),
            )
        )

        viewModel = CraLogbookViewModel(
            getCraLogbookTripsUseCase = getCraLogbookTripsUseCase,
            getActiveVehiclesUseCase = getActiveVehiclesUseCase,
            currentUserProvider = currentUserProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading then Success`() = runTest {
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        assertEquals(CraLogbookUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CraLogbookUiState.Success)
        collector.cancel()
    }

    @Test
    fun `toggleCategory keeps at least one category`() = runTest {
        assertEquals(setOf(TripCategory.BUSINESS), viewModel.selectedCategories.value)

        viewModel.toggleCategory(TripCategory.BUSINESS)
        assertEquals(setOf(TripCategory.BUSINESS), viewModel.selectedCategories.value)

        viewModel.toggleCategory(TripCategory.MEDICAL)
        assertEquals(setOf(TripCategory.BUSINESS, TripCategory.MEDICAL), viewModel.selectedCategories.value)

        viewModel.toggleCategory(TripCategory.BUSINESS)
        assertEquals(setOf(TripCategory.MEDICAL), viewModel.selectedCategories.value)
    }

    @Test
    fun `selectYear updates state and triggers reload`() = runTest {
        viewModel.selectYear(2024)
        assertEquals(2024, viewModel.selectedYear.value)

        advanceUntilIdle()
        coEvery { getCraLogbookTripsUseCase(any(), 2024, any(), any()) }
    }

    @Test
    fun `active vehicles follow selected year and clear stale selected vehicle`() = runTest {
        every { getActiveVehiclesUseCase(any(), 2026) } returns flowOf(
            listOf(
                VehicleEntity(id = 10, ownerUid = "u1", displayName = "Current", fiscalYear = 2026),
            )
        )
        every { getActiveVehiclesUseCase(any(), 2024) } returns flowOf(
            listOf(
                VehicleEntity(id = 20, ownerUid = "u1", displayName = "Archive", fiscalYear = 2024),
            )
        )

        viewModel = CraLogbookViewModel(
            getCraLogbookTripsUseCase = getCraLogbookTripsUseCase,
            getActiveVehiclesUseCase = getActiveVehiclesUseCase,
            currentUserProvider = currentUserProvider,
        )
        advanceUntilIdle()

        viewModel.selectVehicle(10)
        assertEquals(10, viewModel.selectedVehicleId.value)

        viewModel.selectYear(2024)
        advanceUntilIdle()

        assertEquals(listOf(20), viewModel.activeVehicles.value.map { it.id })
        assertNull(viewModel.selectedVehicleId.value)
    }

    @Test
    fun `use case error maps to Error state`() = runTest {
        coEvery { getCraLogbookTripsUseCase(any(), any(), any(), any()) } throws
            RuntimeException("Database locked")

        viewModel = CraLogbookViewModel(
            getCraLogbookTripsUseCase = getCraLogbookTripsUseCase,
            getActiveVehiclesUseCase = getActiveVehiclesUseCase,
            currentUserProvider = currentUserProvider,
        )
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CraLogbookUiState.Error)
        assertEquals("Database locked", (state as CraLogbookUiState.Error).message)
        collector.cancel()
    }

    @Test
    fun `use case error with null message produces fallback message`() = runTest {
        coEvery { getCraLogbookTripsUseCase(any(), any(), any(), any()) } throws
            RuntimeException()

        viewModel = CraLogbookViewModel(
            getCraLogbookTripsUseCase = getCraLogbookTripsUseCase,
            getActiveVehiclesUseCase = getActiveVehiclesUseCase,
            currentUserProvider = currentUserProvider,
        )
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value as CraLogbookUiState.Error
        assertEquals("Failed to load logbook.", state.message)
        collector.cancel()
    }

    @Test
    fun `toggleCategory adds multiple categories`() = runTest {
        viewModel.toggleCategory(TripCategory.MEDICAL)
        viewModel.toggleCategory(TripCategory.CHARITY)

        assertEquals(
            setOf(TripCategory.BUSINESS, TripCategory.MEDICAL, TripCategory.CHARITY),
            viewModel.selectedCategories.value,
        )
    }

    @Test
    fun `toggleCategory removes middle category while keeping others`() = runTest {
        viewModel.toggleCategory(TripCategory.MEDICAL)
        viewModel.toggleCategory(TripCategory.CHARITY)
        assertEquals(3, viewModel.selectedCategories.value.size)

        viewModel.toggleCategory(TripCategory.MEDICAL)
        assertEquals(
            setOf(TripCategory.BUSINESS, TripCategory.CHARITY),
            viewModel.selectedCategories.value,
        )
    }

    @Test
    fun `toggling same category twice is identity for multi-selection`() = runTest {
        viewModel.toggleCategory(TripCategory.MEDICAL)
        val afterAdd = viewModel.selectedCategories.value

        viewModel.toggleCategory(TripCategory.MEDICAL)
        viewModel.toggleCategory(TripCategory.MEDICAL)
        assertEquals(afterAdd, viewModel.selectedCategories.value)
    }

    @Test
    fun `empty categories fall back to BUSINESS in use case call`() = runTest {
        val summary = LogbookSummary(100f, 70f, 70f, null, 2025)
        coEvery { getCraLogbookTripsUseCase(any(), any(), any(), any()) } returns
            (emptyList<com.example.mileagetracker.domain.model.LogbookEntry>() to summary)

        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        coVerify {
            getCraLogbookTripsUseCase(
                driverUid = "u1",
                year = any(),
                vehicleId = null,
                categories = listOf(TripCategory.BUSINESS),
            )
        }
        collector.cancel()
    }

    @Test
    fun `selectVehicle sets and clears vehicle filter`() = runTest {
        viewModel.selectVehicle(5)
        assertEquals(5, viewModel.selectedVehicleId.value)

        viewModel.selectVehicle(null)
        assertNull(viewModel.selectedVehicleId.value)
    }

    @Test
    fun `filterableCategories matches CRA eligible list`() {
        assertEquals(
            listOf(TripCategory.BUSINESS, TripCategory.MEDICAL, TripCategory.CHARITY),
            viewModel.filterableCategories,
        )
    }

    @Test
    fun `availableYears contains current year range`() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        assertEquals((currentYear - 5..currentYear + 1).toList(), viewModel.availableYears)
    }

    @Test
    fun `selecting vehicle that exists in active list is retained on year change`() = runTest {
        every { getActiveVehiclesUseCase(any(), any()) } returns flowOf(
            listOf(
                VehicleEntity(id = 5, ownerUid = "u1", displayName = "Both Years", fiscalYear = 2025),
            )
        )

        viewModel = CraLogbookViewModel(
            getCraLogbookTripsUseCase = getCraLogbookTripsUseCase,
            getActiveVehiclesUseCase = getActiveVehiclesUseCase,
            currentUserProvider = currentUserProvider,
        )
        advanceUntilIdle()

        viewModel.selectVehicle(5)
        viewModel.selectYear(2024)
        advanceUntilIdle()

        assertEquals(5, viewModel.selectedVehicleId.value)
    }

    @Test
    fun `changing year triggers new use case invocation`() = runTest {
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectYear(2023)
        advanceUntilIdle()

        coVerify {
            getCraLogbookTripsUseCase(
                driverUid = "u1",
                year = 2023,
                vehicleId = any(),
                categories = any(),
            )
        }
        collector.cancel()
    }

    @Test
    fun `Success state with entries exposes them correctly`() = runTest {
        val entries = listOf(
            com.example.mileagetracker.domain.model.LogbookEntry(
                tripId = 1, date = 1000L, origin = "Home", destination = "Office",
                category = com.example.mileagetracker.data.local.entity.TripCategory.BUSINESS,
                businessKm = 25f, vehicleName = "Sedan",
            ),
            com.example.mileagetracker.domain.model.LogbookEntry(
                tripId = 2, date = 2000L, origin = "Home", destination = "Hospital",
                category = com.example.mileagetracker.data.local.entity.TripCategory.MEDICAL,
                businessKm = 10f, vehicleName = "Sedan",
            ),
        )
        val summary = LogbookSummary(50f, 35f, 70f, null, 2025)
        coEvery { getCraLogbookTripsUseCase(any(), any(), any(), any()) } returns (entries to summary)

        viewModel = CraLogbookViewModel(
            getCraLogbookTripsUseCase = getCraLogbookTripsUseCase,
            getActiveVehiclesUseCase = getActiveVehiclesUseCase,
            currentUserProvider = currentUserProvider,
        )
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value as CraLogbookUiState.Success
        assertEquals(2, state.entries.size)
        assertEquals(listOf(1, 2), state.entries.map { it.tripId })
        assertEquals(50f, state.summary.totalKm)
        assertEquals(35f, state.summary.businessKm)
        collector.cancel()
    }
}
