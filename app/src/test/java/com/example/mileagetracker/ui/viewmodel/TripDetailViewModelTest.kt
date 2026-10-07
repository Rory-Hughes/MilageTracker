package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.android.gms.maps.model.LatLng
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.GetTripExceptionsUseCase
import com.example.mileagetracker.domain.usecase.calendar.GetCalendarHintsForTripUseCase
import com.example.mileagetracker.domain.usecase.drive.ClassifyTripUseCase
import com.example.mileagetracker.domain.usecase.drive.ResolveDriveLocationsUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import com.example.mileagetracker.domain.usecase.suggestion.ApplySuggestionUseCase
import com.example.mileagetracker.domain.usecase.suggestion.GetTripSuggestionsUseCase
import com.example.mileagetracker.navigation.Screen
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class TripDetailViewModelTest {

    private lateinit var viewModel: TripDetailViewModel
    private lateinit var driveRepository: DriveRepository
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var updateDriveUseCase: UpdateDriveUseCase
    private lateinit var resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase
    private lateinit var classifyTripUseCase: ClassifyTripUseCase
    private lateinit var getTripSuggestionsUseCase: GetTripSuggestionsUseCase
    private lateinit var applySuggestionUseCase: ApplySuggestionUseCase
    private lateinit var getCalendarHintsUseCase: GetCalendarHintsForTripUseCase
    private lateinit var getTripExceptionsUseCase: GetTripExceptionsUseCase
    private lateinit var currentUserProvider: CurrentUserProvider

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        driveRepository = mockk()
        vehicleRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        updateDriveUseCase = mockk()
        resolveDriveLocationsUseCase = mockk()
        classifyTripUseCase = mockk()
        getTripSuggestionsUseCase = mockk()
        applySuggestionUseCase = mockk()
        getCalendarHintsUseCase = mockk()
        getTripExceptionsUseCase = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "u1"
        every { getTripExceptionsUseCase("u1") } returns flowOf(emptyList())
        every { settingsRepository.getCalendarHintsEnabled() } returns flowOf(false)
        every { resolveDriveLocationsUseCase.requiresResolution(any()) } returns false
        coEvery { resolveDriveLocationsUseCase(any()) } answers { firstArg() }
        coEvery { updateDriveUseCase(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testDrive(
        id: Int = 1,
        status: ClassificationStatus = ClassificationStatus.PENDING,
        startLocation: String = "A",
        endLocation: String = "B",
    ) = DriveEntity(
        id = id,
        startedAt = 1_000L,
        endedAt = 2_000L,
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = 10f,
        vehicleId = null,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = status,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1_000L,
    )

    private fun initViewModel(driveId: Int? = 1) {
        val savedStateHandle = SavedStateHandle().apply {
            if (driveId != null) set(Screen.ARG_DRIVE_ID, driveId)
        }
        viewModel = TripDetailViewModel(
            savedStateHandle = savedStateHandle,
            driveRepository = driveRepository,
            vehicleRepository = vehicleRepository,
            settingsRepository = settingsRepository,
            updateDriveUseCase = updateDriveUseCase,
            resolveDriveLocationsUseCase = resolveDriveLocationsUseCase,
            classifyTripUseCase = classifyTripUseCase,
            getTripSuggestionsUseCase = getTripSuggestionsUseCase,
            applySuggestionUseCase = applySuggestionUseCase,
            getCalendarHintsUseCase = getCalendarHintsUseCase,
            getTripExceptionsUseCase = getTripExceptionsUseCase,
            currentUserProvider = currentUserProvider,
        )
    }

    private fun makePoint(lat: Double, lng: Double, index: Int = 0) = RoutePoint(
        id = index.toLong(),
        tripId = 1L,
        lat = lat,
        lng = lng,
        altitudeMetres = null,
        accuracyMetres = null,
        speedKph = null,
        timestampMs = 1_000_000L + index,
    )

    @Test
    fun `empty route emits no route`() = runTest {
        val state = emptyList<RoutePoint>().toRouteMapUiState()
        assertEquals(RouteMapUiState.NoRoute, state)
    }

    @Test
    fun `single point emits no route`() = runTest {
        val state = listOf(makePoint(45.0, -66.0)).toRouteMapUiState()
        assertEquals(RouteMapUiState.NoRoute, state)
    }

    @Test
    fun `two points emit ready with correct markers`() = runTest {
        val p1 = makePoint(45.0, -66.0, index = 0)
        val p2 = makePoint(46.0, -67.0, index = 1)
        val state = listOf(p1, p2).toRouteMapUiState()

        assertTrue(state is RouteMapUiState.Ready)
        val ready = state as RouteMapUiState.Ready
        assertEquals(LatLng(45.0, -66.0), ready.startMarker)
        assertEquals(LatLng(46.0, -67.0), ready.endMarker)
        assertEquals(2, ready.points.size)
    }

    @Test
    fun `loadDrive success updates uiState`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { driveRepository.getRoutePointsForDrive(1) } returns emptyList()
        coEvery { getTripSuggestionsUseCase(any(), any()) } returns emptyList()

        initViewModel(1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TripDetailUiState.Success)
        assertEquals(drive, (viewModel.uiState.value as TripDetailUiState.Success).drive)
    }

    @Test
    fun `coordinate endpoints are backfilled and reloaded`() = runTest {
        val unresolvedDrive = testDrive(
            startLocation = "44.6488, -63.5752",
            endLocation = "44.6713, -63.6103",
        )
        val resolvedDrive = unresolvedDrive.copy(
            startLocation = "1800 Lower Water St, Halifax, NS B3J 3R8",
            endLocation = "6960 Mumford Rd, Halifax, NS B3L 4P1",
        )

        coEvery { driveRepository.getDriveById(1) } returnsMany listOf(unresolvedDrive, resolvedDrive)
        coEvery { driveRepository.getRoutePointsForDrive(1) } returns emptyList()
        coEvery { getTripSuggestionsUseCase(any(), any()) } returns emptyList()
        every { resolveDriveLocationsUseCase.requiresResolution(unresolvedDrive) } returns true
        every { resolveDriveLocationsUseCase.requiresResolution(resolvedDrive) } returns false
        coEvery { resolveDriveLocationsUseCase(unresolvedDrive) } returns resolvedDrive
        coEvery {
            updateDriveUseCase(
                match {
                    it.id == resolvedDrive.id &&
                        it.startLocation == resolvedDrive.startLocation &&
                        it.endLocation == resolvedDrive.endLocation &&
                        it.updatedAt >= unresolvedDrive.createdAt
                },
                "u1",
            )
        } returns true

        initViewModel(1)
        advanceUntilIdle()

        assertEquals(
            resolvedDrive.startLocation,
            (viewModel.uiState.value as TripDetailUiState.Success).drive.startLocation,
        )
        coVerify(exactly = 1) { resolveDriveLocationsUseCase(unresolvedDrive) }
        coVerify(exactly = 1) { updateDriveUseCase(any(), "u1") }
    }

    @Test
    fun `readable addresses do not trigger backfill`() = runTest {
        val drive = testDrive(
            startLocation = "Halifax Waterfront",
            endLocation = "Mumford Terminal",
        )
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { driveRepository.getRoutePointsForDrive(1) } returns emptyList()
        coEvery { getTripSuggestionsUseCase(any(), any()) } returns emptyList()
        every { resolveDriveLocationsUseCase.requiresResolution(drive) } returns false

        initViewModel(1)
        advanceUntilIdle()

        coVerify(exactly = 0) { resolveDriveLocationsUseCase(any()) }
        coVerify(exactly = 0) { updateDriveUseCase(any(), any()) }
    }

    @Test
    fun `classifyTrip calls use case and reloads`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { driveRepository.getRoutePointsForDrive(1) } returns emptyList()
        coEvery { getTripSuggestionsUseCase(any(), any()) } returns emptyList()
        coEvery { classifyTripUseCase(1, TripCategory.BUSINESS, "Work", "u1") } returns true

        initViewModel(1)
        advanceUntilIdle()

        viewModel.classifyTrip(TripCategory.BUSINESS, "Work")
        advanceUntilIdle()

        coVerify { classifyTripUseCase(1, TripCategory.BUSINESS, "Work", "u1") }
    }
}
