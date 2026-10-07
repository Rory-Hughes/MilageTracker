package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCaptureType
import com.example.mileagetracker.domain.repository.VehicleDefaultSettingsRepository
import com.example.mileagetracker.domain.usecase.odometer.OdometerSaveResult
import com.example.mileagetracker.domain.usecase.odometer.SaveOdometerCaptureUseCase
import com.example.mileagetracker.domain.usecase.vehicle.DeleteVehicleUseCase
import com.example.mileagetracker.domain.usecase.vehicle.GetActiveVehiclesUseCase
import com.example.mileagetracker.domain.usecase.vehicle.GetVehiclesUseCase
import com.example.mileagetracker.domain.usecase.vehicle.SaveVehicleUseCase
import com.example.mileagetracker.domain.usecase.vehicle.SetDefaultVehicleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleManagementViewModelTest {

    private lateinit var viewModel: VehicleManagementViewModel
    private lateinit var getVehiclesUseCase: GetVehiclesUseCase
    private lateinit var getActiveVehiclesUseCase: GetActiveVehiclesUseCase
    private lateinit var saveVehicleUseCase: SaveVehicleUseCase
    private lateinit var saveOdometerCaptureUseCase: SaveOdometerCaptureUseCase
    private lateinit var setDefaultVehicleUseCase: SetDefaultVehicleUseCase
    private lateinit var deleteVehicleUseCase: DeleteVehicleUseCase
    private lateinit var vehicleDefaultSettingsRepository: VehicleDefaultSettingsRepository
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getVehiclesUseCase = mockk()
        getActiveVehiclesUseCase = mockk()
        saveVehicleUseCase = mockk()
        saveOdometerCaptureUseCase = mockk()
        setDefaultVehicleUseCase = mockk()
        deleteVehicleUseCase = mockk()
        vehicleDefaultSettingsRepository = mockk(relaxed = true)
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "u1"
        every { getVehiclesUseCase("u1") } returns flowOf(emptyList())
        every { getActiveVehiclesUseCase("u1", any()) } returns flowOf(emptyList())

        viewModel = VehicleManagementViewModel(
            getVehiclesUseCase,
            getActiveVehiclesUseCase,
            saveVehicleUseCase,
            saveOdometerCaptureUseCase,
            setDefaultVehicleUseCase,
            deleteVehicleUseCase,
            vehicleDefaultSettingsRepository,
            currentUserProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testVehicle(id: Int = 1) = VehicleEntity(
        id = id,
        ownerUid = "u1",
        displayName = "Car $id",
        fiscalYear = 2025,
        ownershipType = VehicleOwnershipType.PERSONAL
    )

    @Test
    fun `saveVehicle triggers SaveSuccess event`() = runTest {
        val vehicle = testVehicle()
        coEvery { saveVehicleUseCase(vehicle) } returns 1
        
        viewModel.saveVehicle(vehicle)
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(VehicleUiEvent.SaveSuccess, event)
    }

    @Test
    fun `saveVehicle with opening photo creates opening odometer capture`() = runTest {
        val vehicle = testVehicle(id = 0).copy(openingOdometerKm = 12345f)
        coEvery { saveVehicleUseCase(vehicle) } returns 9
        coEvery { saveOdometerCaptureUseCase(any()) } answers {
            OdometerSaveResult.Success(firstArg())
        }

        viewModel.saveVehicle(
            VehicleSaveRequest(
                vehicle = vehicle,
                openingOdometerPhotoUri = "content://odometer/opening",
            )
        )
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(VehicleUiEvent.SaveSuccess, event)
        coVerify {
            saveOdometerCaptureUseCase(match {
                it.vehicleId == 9 &&
                    it.readingKm == 12345 &&
                    it.taxYear == vehicle.fiscalYear &&
                    it.photoUri == "content://odometer/opening" &&
                    it.captureType == OdometerCaptureType.PHOTO_ASSISTED &&
                    it.boundaryType == OdometerBoundaryType.OPENING
            })
        }
    }

    @Test
    fun `deleteVehicle triggers DeleteSuccess event`() = runTest {
        val vehicle = testVehicle()
        coEvery { deleteVehicleUseCase(vehicle) } returns Unit

        viewModel.deleteVehicle(vehicle)
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(VehicleUiEvent.DeleteSuccess, event)
    }

    @Test
    fun `deleteVehicle triggers Error event on failure`() = runTest {
        val vehicle = testVehicle()
        coEvery { deleteVehicleUseCase(vehicle) } throws IllegalStateException("Has trips")

        viewModel.deleteVehicle(vehicle)
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue(event is VehicleUiEvent.Error)
        assertEquals("Has trips", (event as VehicleUiEvent.Error).message)
    }

    @Test
    fun `setDefault writes DataStore preference and triggers DefaultSet event`() = runTest {
        coEvery { setDefaultVehicleUseCase(any(), any(), any()) } returns Unit

        viewModel.setDefault(1)
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(VehicleUiEvent.DefaultSet, event)
        coVerify { setDefaultVehicleUseCase(1, "u1", any()) }
        coVerify { vehicleDefaultSettingsRepository.setDefaultVehicleId(1) }
    }

    @Test
    fun `deactivate clears DataStore default when vehicle was default`() = runTest {
        val vehicle = testVehicle().copy(isDefault = true)
        coEvery { saveVehicleUseCase(any()) } returns 1

        viewModel.deactivate(vehicle)
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(VehicleUiEvent.DeactivateSuccess, event)
        coVerify {
            saveVehicleUseCase(match {
                it.id == vehicle.id && !it.isActive && !it.isDefault
            })
        }
        coVerify { vehicleDefaultSettingsRepository.setDefaultVehicleId(null) }
    }

    @Test
    fun `deactivate does not touch DataStore default when vehicle was not default`() = runTest {
        val vehicle = testVehicle().copy(isDefault = false)
        coEvery { saveVehicleUseCase(any()) } returns 1

        viewModel.deactivate(vehicle)
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(VehicleUiEvent.DeactivateSuccess, event)
        coVerify(exactly = 0) { vehicleDefaultSettingsRepository.setDefaultVehicleId(any()) }
    }

    @Test
    fun `reactivate flips isActive back on and emits ReactivateSuccess`() = runTest {
        val vehicle = testVehicle().copy(isActive = false)
        coEvery { saveVehicleUseCase(any()) } returns 1

        viewModel.reactivate(vehicle)
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(VehicleUiEvent.ReactivateSuccess, event)
        coVerify {
            saveVehicleUseCase(match {
                it.id == vehicle.id && it.isActive && !it.isDefault
            })
        }
    }
}
