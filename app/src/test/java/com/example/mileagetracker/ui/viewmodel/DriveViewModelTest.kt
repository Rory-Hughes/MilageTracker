package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.drive.ClassifyTripUseCase
import com.example.mileagetracker.domain.usecase.drive.GetUnclassifiedDrivesUseCase
import com.example.mileagetracker.domain.usecase.drive.ResolveDriveLocationsUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.GetCraRateForYearUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveViewModelTest {

    private lateinit var viewModel: DriveViewModel
    private lateinit var getUnclassifiedDrivesUseCase: GetUnclassifiedDrivesUseCase
    private lateinit var updateDriveUseCase: UpdateDriveUseCase
    private lateinit var resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase
    private lateinit var classifyTripUseCase: ClassifyTripUseCase
    private lateinit var calculateReimbursementUseCase: CalculateReimbursementUseCase
    private lateinit var getCraRateForYearUseCase: GetCraRateForYearUseCase
    private lateinit var driveRepository: DriveRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getUnclassifiedDrivesUseCase = mockk()
        updateDriveUseCase = mockk()
        resolveDriveLocationsUseCase = mockk()
        classifyTripUseCase = mockk(relaxed = true)
        calculateReimbursementUseCase = mockk(relaxed = true)
        getCraRateForYearUseCase = mockk()
        driveRepository = mockk()
        settingsRepository = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "u1"
        every { getUnclassifiedDrivesUseCase("u1") } returns flowOf(emptyList())
        every { driveRepository.getAllDrives("u1") } returns flowOf(emptyList())
        every { driveRepository.getYearToDateBusinessDistance(any(), any(), any()) } returns flowOf(0f)
        every { settingsRepository.getCraRateRegion() } returns flowOf(null)
        every { settingsRepository.getRateMode() } returns flowOf(RateMode.CRA_STANDARD)
        every { resolveDriveLocationsUseCase.requiresResolution(any()) } returns false
        coEvery { resolveDriveLocationsUseCase(any()) } answers { firstArg() }
        coEvery { updateDriveUseCase(any(), any()) } returns true

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unclassifiedUiState initial state is Loading then Success`() = runTest {
        val collector = backgroundScope.launch { viewModel.unclassifiedUiState.collect {} }

        advanceUntilIdle()

        assertTrue(viewModel.unclassifiedUiState.value is UnclassifiedUiState.Success)
        collector.cancel()
    }

    @Test
    fun `recentTrips takes first 3 drives`() = runTest {
        val drives = List(5) { id ->
            drive(
                id = id,
                tripCategory = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.AUTO_CLASSIFIED,
            )
        }
        every { driveRepository.getAllDrives("u1") } returns flowOf(drives)

        viewModel = createViewModel()
        val collector = backgroundScope.launch { viewModel.recentTrips.collect {} }

        advanceUntilIdle()

        assertEquals(3, viewModel.recentTrips.value.size)
        collector.cancel()
    }

    @Test
    fun `ytdDeductions is computed live from business distance, not per-trip snapshots`() = runTest {
        every {
            driveRepository.getYearToDateBusinessDistance(any(), any(), any())
        } returns flowOf(40f)
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)
        coEvery {
            calculateReimbursementUseCase(any(), 40f, any())
        } returns reimbursementResult(amountCents = 2920)

        viewModel = createViewModel()
        val collector = backgroundScope.launch { viewModel.ytdDeductions.collect {} }

        advanceUntilIdle()

        // 40 km × $0.73 = $29.20 — derived from the distance aggregate, never
        // from reimbursementAmountSnapshot.
        assertEquals(29.20f, viewModel.ytdDeductions.value, 0.001f)
        coVerify { calculateReimbursementUseCase(any(), 40f, any()) }
        collector.cancel()
    }

    @Test
    fun `ytdDeductions is zero when no business distance`() = runTest {
        every {
            driveRepository.getYearToDateBusinessDistance(any(), any(), any())
        } returns flowOf(0f)
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)

        viewModel = createViewModel()
        val collector = backgroundScope.launch { viewModel.ytdDeductions.collect {} }

        advanceUntilIdle()

        assertEquals(0f, viewModel.ytdDeductions.value, 0.001f)
        coVerify(exactly = 0) { calculateReimbursementUseCase(any(), any(), any()) }
        collector.cancel()
    }

    @Test
    fun `coordinate endpoints are backfilled for unclassified drives`() = runTest {
        val unresolvedDrive = drive(
            id = 7,
            startLocation = "44.6488, -63.5752",
            endLocation = "44.6713, -63.6103",
            tripCategory = TripCategory.UNCLASSIFIED,
            classificationStatus = ClassificationStatus.PENDING,
        )
        val resolvedDrive = unresolvedDrive.copy(
            startLocation = "1800 Lower Water St, Halifax, NS B3J 3R8",
            endLocation = "6960 Mumford Rd, Halifax, NS B3L 4P1",
        )
        every { getUnclassifiedDrivesUseCase("u1") } returns flowOf(listOf(unresolvedDrive))
        every { resolveDriveLocationsUseCase.requiresResolution(unresolvedDrive) } returns true
        coEvery { resolveDriveLocationsUseCase(unresolvedDrive) } returns resolvedDrive

        viewModel = createViewModel()
        val collector = backgroundScope.launch { viewModel.unclassifiedUiState.collect {} }

        advanceUntilIdle()

        coVerify { updateDriveUseCase(resolvedDrive, "u1") }
        collector.cancel()
    }

    @Test
    fun `human readable locations do not trigger background backfill`() = runTest {
        val readableDrive = drive(
            id = 8,
            startLocation = "Halifax Waterfront",
            endLocation = "Mumford Terminal",
            tripCategory = TripCategory.UNCLASSIFIED,
            classificationStatus = ClassificationStatus.PENDING,
        )
        every { getUnclassifiedDrivesUseCase("u1") } returns flowOf(listOf(readableDrive))
        every { resolveDriveLocationsUseCase.requiresResolution(readableDrive) } returns false

        viewModel = createViewModel()
        val collector = backgroundScope.launch { viewModel.unclassifiedUiState.collect {} }

        advanceUntilIdle()

        coVerify(exactly = 0) { updateDriveUseCase(any(), any()) }
        collector.cancel()
    }

    private fun createViewModel() = DriveViewModel(
        getUnclassifiedDrivesUseCase = getUnclassifiedDrivesUseCase,
        updateDriveUseCase = updateDriveUseCase,
        resolveDriveLocationsUseCase = resolveDriveLocationsUseCase,
        classifyTripUseCase = classifyTripUseCase,
        calculateReimbursementUseCase = calculateReimbursementUseCase,
        getCraRateForYearUseCase = getCraRateForYearUseCase,
        driveRepository = driveRepository,
        settingsRepository = settingsRepository,
        currentUserProvider = currentUserProvider,
    )

    private fun drive(
        id: Int,
        startLocation: String = "A",
        endLocation: String = "B",
        tripCategory: TripCategory = TripCategory.BUSINESS,
        classificationStatus: ClassificationStatus = ClassificationStatus.AUTO_CLASSIFIED,
    ) = DriveEntity(
        id = id,
        startedAt = 1000L,
        endedAt = 2000L,
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = 10f,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = tripCategory,
        classificationStatus = classificationStatus,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1000L,
    )

    private fun reimbursementResult(amountCents: Int) = ReimbursementResult(
        taxYear = 2026,
        newDistanceKm = 0f,
        ytdKmBefore = 0f,
        firstTierKm = 0,
        secondTierKm = 0,
        firstTierLimitKm = 5_000,
        firstTierRateCents = 73,
        additionalRateCents = 67,
        amountCents = amountCents,
    )
}
