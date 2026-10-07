package com.example.mileagetracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.places.PlacesService
import com.example.mileagetracker.domain.repository.DirectionsRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.drive.CreateManualTripUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualTripEntryViewModelTest {

    private lateinit var viewModel: ManualTripEntryViewModel
    private lateinit var driveRepository: DriveRepository
    private lateinit var createManualTripUseCase: CreateManualTripUseCase
    private lateinit var updateDriveUseCase: UpdateDriveUseCase
    private lateinit var placesService: PlacesService
    private lateinit var directionsRepository: DirectionsRepository
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        driveRepository = mockk()
        createManualTripUseCase = mockk()
        updateDriveUseCase = mockk()
        placesService = mockk()
        directionsRepository = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "user-123"
        coEvery { placesService.autocomplete(any(), any()) } returns emptyList()

        viewModel = ManualTripEntryViewModel(
            savedStateHandle = SavedStateHandle(),
            driveRepository = driveRepository,
            createManualTripUseCase = createManualTripUseCase,
            updateDriveUseCase = updateDriveUseCase,
            placesService = placesService,
            directionsRepository = directionsRepository,
            currentUserProvider = currentUserProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `initial state is empty and invalid`() = runTest {
        val state = viewModel.formState.value
        assertEquals("", state.startLocation)
        assertEquals("", state.endLocation)
        assertFalse(state.isSaveEnabled)
        assertTrue(state.fieldErrors.isEmpty())
    }

    @Test
    fun `updateStartLocation updates state and revalidates`() = runTest {
        viewModel.updateStartLocation("Ho")
        assertEquals("Ho", viewModel.formState.value.startLocation)
        // revalidated() should set isSaveEnabled to false because other fields are missing
        assertFalse(viewModel.formState.value.isSaveEnabled)
    }

    @Test
    fun `updateEndLocation updates state`() = runTest {
        viewModel.updateEndLocation("Work")
        assertEquals("Work", viewModel.formState.value.endLocation)
    }

    @Test
    fun `updateDistanceKm updates state`() = runTest {
        viewModel.updateDistanceKm("12.3")
        assertEquals("12.3", viewModel.formState.value.distanceKmText)
    }

    @Test
    fun `updateCategory updates state`() = runTest {
        viewModel.updateCategory(TripCategory.MEDICAL)
        assertEquals(TripCategory.MEDICAL, viewModel.formState.value.category)
    }

    @Test
    fun `valid form enables save`() = runTest {
        viewModel.updateStartLocation("St")
        viewModel.updateEndLocation("En")
        viewModel.updateDistanceKm("10.5")
        viewModel.updateStartedAt(System.currentTimeMillis())
        viewModel.updateEndedAt(System.currentTimeMillis() + 1000)
        viewModel.updateCategory(TripCategory.BUSINESS)

        val state = viewModel.formState.value
        assertTrue("Errors: ${state.fieldErrors}", state.isSaveEnabled)
        assertTrue(state.fieldErrors.isEmpty())
    }

    @Test
    fun `save success triggers event`() = runTest {
        coEvery { createManualTripUseCase(any(), "user-123") } returns 42

        viewModel.updateStartLocation("St")
        viewModel.updateEndLocation("En")
        viewModel.updateDistanceKm("10.5")
        viewModel.updateStartedAt(1000L)
        viewModel.updateEndedAt(2000L)
        viewModel.updateCategory(TripCategory.BUSINESS)
        
        viewModel.save()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue(event is ManualTripEntryEvent.SaveSuccess)
        assertEquals(42, (event as ManualTripEntryEvent.SaveSuccess).driveId)
    }

    @Test
    fun `save failure triggers error event`() = runTest {
        coEvery { createManualTripUseCase(any(), any()) } throws IllegalArgumentException("Invalid data")

        viewModel.updateStartLocation("St")
        viewModel.updateEndLocation("En")
        viewModel.updateDistanceKm("10.5")
        viewModel.updateStartedAt(1000L)
        viewModel.updateEndedAt(2000L)
        viewModel.updateCategory(TripCategory.BUSINESS)

        viewModel.save()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue(event is ManualTripEntryEvent.Error)
        assertEquals("Invalid data", (event as ManualTripEntryEvent.Error).message)
    }
}
