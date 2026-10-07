package com.example.mileagetracker.domain.usecase.rate

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.model.OdometerCaptureType
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.OdometerRepository
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GetAnnualVehicleSummaryUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var odometerRepository: OdometerRepository
    private lateinit var rateRepository: RateRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var calculateReimbursement: CalculateReimbursementUseCase
    private lateinit var useCase: GetAnnualVehicleSummaryUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        odometerRepository = mockk()
        rateRepository = mockk()
        settingsRepository = mockk()
        calculateReimbursement = CalculateReimbursementUseCase(rateRepository, settingsRepository)
        useCase = GetAnnualVehicleSummaryUseCase(
            driveRepository,
            odometerRepository,
            rateRepository,
            settingsRepository,
            calculateReimbursement
        )

        coEvery { driveRepository.getAllDrivesForVehicleInYear(any(), any(), any()) } returns emptyList()
        coEvery { odometerRepository.getFirstCaptureForVehicleInYear(any(), any(), any()) } returns null
        coEvery { odometerRepository.getLastCaptureForVehicleInYear(any(), any(), any()) } returns null
        coEvery { odometerRepository.getOpeningEvidenceForVehicleYear(any(), any(), any(), any()) } returns null
        coEvery { odometerRepository.getClosingEvidenceForVehicleYear(any(), any(), any(), any()) } returns null
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)
        coEvery { rateRepository.getRateForYearOnce(any()) } answers {
            testRateEntity(year = firstArg())
        }
    }

    private fun testDrive(
        km: Float,
        category: TripCategory = TripCategory.BUSINESS,
        startedAt: Long = 1000L,
    ) = DriveEntity(
        id = 0,
        startedAt = startedAt,
        endedAt = 2000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = km,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = category,
        classificationStatus = ClassificationStatus.AUTO_CLASSIFIED,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1000L
    )

    private fun testOdo(km: Int) = OdometerCapture(
        id = 1,
        vehicleId = 1,
        captureDate = 1000L,
        readingKm = km,
        captureType = OdometerCaptureType.MANUAL,
        createdAt = 1000L
    )

    private fun testRateEntity(
        year: Int,
        firstTierRateCents: Int = 73,
        additionalKmRateCents: Int = 67,
        firstTierLimitKm: Int = 5000,
    ) = CraRateEntity(
        taxYear = year,
        firstTierRateCents = firstTierRateCents,
        additionalKmRateCents = additionalKmRateCents,
        firstTierLimitKm = firstTierLimitKm,
        sourceLabel = "Seed",
        effectiveFrom = 1000L,
    )

    @Test
    fun `invokes correctly with fully populated data`() = runTest {
        val drives = listOf(
            testDrive(100f, TripCategory.BUSINESS),
            testDrive(50f, TripCategory.PERSONAL)
        )
        coEvery { driveRepository.getAllDrivesForVehicleInYear(1, any(), any()) } returns drives
        coEvery { odometerRepository.getOpeningEvidenceForVehicleYear(1, 2025, any(), any()) } returns testOdo(10000)
        coEvery { odometerRepository.getClosingEvidenceForVehicleYear(1, 2025, any(), any()) } returns testOdo(10200)
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)
        coEvery { rateRepository.getRateForYearOnce(2025) } returns CraRateEntity(
            taxYear = 2025,
            firstTierRateCents = 70,
            additionalKmRateCents = 64,
            firstTierLimitKm = 5000,
            sourceLabel = "Seed",
            effectiveFrom = 1000L
        )

        val summary = useCase(1, 2025)

        assertEquals(100.0, summary.businessKm, 0.01)
        assertEquals(50.0, summary.personalKm, 0.01)
        assertEquals(150.0, summary.totalLoggedKm, 0.01)
        assertEquals(66.66, summary.businessPercent!!, 0.1)
        assertEquals(10000, summary.odometerOpenKm)
        assertEquals(10200, summary.odometerCloseKm)
        assertEquals(200, summary.odometerTotalKm)
        // 100km * 0.72 = 72.0
        assertEquals(72.0, summary.reimbursementDollars!!, 0.01)
    }

    @Test
    fun `returns null reimbursement if rate not seeded`() = runTest {
        coEvery { rateRepository.getRateForYearOnce(any()) } returns null

        val summary = useCase(1, 2025)

        assertNull(summary.reimbursementDollars)
        assertNull(summary.craRateUsed)
    }

    @Test
    fun `includes non business categories in total but not business personal splits`() = runTest {
        coEvery { driveRepository.getAllDrivesForVehicleInYear(any(), any(), any()) } returns listOf(
            testDrive(10f, TripCategory.BUSINESS),
            testDrive(20f, TripCategory.PERSONAL),
            testDrive(30f, TripCategory.COMMUTE),
            testDrive(40f, TripCategory.MEDICAL),
        )

        val summary = useCase(1, 2026)

        assertEquals(10.0, summary.businessKm, 0.001)
        assertEquals(20.0, summary.personalKm, 0.001)
        assertEquals(100.0, summary.totalLoggedKm, 0.001)
        assertEquals(4, summary.tripCount)
        assertEquals(1, summary.businessTripCount)
        assertEquals(10.0, summary.businessPercent!!, 0.001)
    }

    @Test
    fun `returns null business percent when no drives are logged`() = runTest {
        coEvery { driveRepository.getAllDrivesForVehicleInYear(any(), any(), any()) } returns emptyList()

        val summary = useCase(1, 2026)

        assertEquals(0.0, summary.totalLoggedKm, 0.001)
        assertEquals(0.0, summary.businessKm, 0.001)
        assertEquals(0.0, summary.personalKm, 0.001)
        assertNull(summary.businessPercent)
        assertEquals(0, summary.tripCount)
        assertEquals(0, summary.businessTripCount)
    }

    @Test
    fun `odometer total is null when opening capture is missing`() = runTest {
        coEvery { odometerRepository.getOpeningEvidenceForVehicleYear(any(), any(), any(), any()) } returns null
        coEvery { odometerRepository.getClosingEvidenceForVehicleYear(any(), any(), any(), any()) } returns testOdo(12_345)

        val summary = useCase(1, 2026)

        assertNull(summary.odometerOpenKm)
        assertEquals(12_345, summary.odometerCloseKm)
        assertNull(summary.odometerTotalKm)
    }

    @Test
    fun `odometer total is null when closing reading is lower than opening`() = runTest {
        coEvery { odometerRepository.getOpeningEvidenceForVehicleYear(any(), any(), any(), any()) } returns testOdo(20_000)
        coEvery { odometerRepository.getClosingEvidenceForVehicleYear(any(), any(), any(), any()) } returns testOdo(19_999)

        val summary = useCase(1, 2026)

        assertEquals(20_000, summary.odometerOpenKm)
        assertEquals(19_999, summary.odometerCloseKm)
        assertNull(summary.odometerTotalKm)
    }

    @Test
    fun `odometer total is zero when opening and closing readings are equal`() = runTest {
        coEvery { odometerRepository.getOpeningEvidenceForVehicleYear(any(), any(), any(), any()) } returns testOdo(15_000)
        coEvery { odometerRepository.getClosingEvidenceForVehicleYear(any(), any(), any(), any()) } returns testOdo(15_000)

        val summary = useCase(1, 2026)

        assertEquals(0, summary.odometerTotalKm)
    }

    @Test
    fun `returns null reimbursement when CRA region is not selected`() = runTest {
        coEvery { driveRepository.getAllDrivesForVehicleInYear(any(), any(), any()) } returns listOf(
            testDrive(100f, TripCategory.BUSINESS),
        )
        every { settingsRepository.getCraRateRegion() } returns flowOf(null)
        coEvery { rateRepository.getRateForYearOnce(2026) } returns testRateEntity(2026)

        val summary = useCase(1, 2026)

        assertNull(summary.reimbursementDollars)
        assertNull(summary.craRateUsed)
    }

    @Test
    fun `territorial region uses CRA catalog territorial rates over stored entity values`() = runTest {
        coEvery { driveRepository.getAllDrivesForVehicleInYear(any(), any(), any()) } returns listOf(
            testDrive(100f, TripCategory.BUSINESS),
        )
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.TERRITORIES)
        coEvery { rateRepository.getRateForYearOnce(2026) } returns testRateEntity(
            year = 2026,
            firstTierRateCents = 1,
            additionalKmRateCents = 2,
            firstTierLimitKm = 100,
        )

        val summary = useCase(1, 2026)

        assertEquals(77.0, summary.reimbursementDollars!!, 0.001)
        assertEquals(77, summary.craRateUsed!!.tier1RateCentsPerKm)
        assertEquals(71, summary.craRateUsed!!.tier2RateCentsPerKm)
        assertEquals(5_000, summary.craRateUsed!!.tier1ThresholdKm)
    }

    @Test
    fun `reimbursement uses running ytd across business drives and crosses tier threshold`() = runTest {
        coEvery { driveRepository.getAllDrivesForVehicleInYear(any(), any(), any()) } returns listOf(
            testDrive(3000f, TripCategory.BUSINESS, startedAt = 1_000L),
            testDrive(2500f, TripCategory.BUSINESS, startedAt = 2_000L),
        )
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)
        coEvery { rateRepository.getRateForYearOnce(2026) } returns testRateEntity(2026)

        val summary = useCase(1, 2026)

        // 3000*73 + (2000*73 + 500*67) = 398500 cents
        assertEquals(3_985.0, summary.reimbursementDollars!!, 0.001)
    }

    @Test
    fun `uses leap year millis boundaries when querying annual repositories`() = runTest {
        useCase(7, 2024)

        val expectedFrom = LocalDate.of(2024, 1, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val expectedTo = LocalDate.of(2025, 1, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli() - 1L

        coVerify { driveRepository.getAllDrivesForVehicleInYear(7, expectedFrom, expectedTo) }
        coVerify { odometerRepository.getOpeningEvidenceForVehicleYear(7, 2024, expectedFrom, expectedTo) }
        coVerify { odometerRepository.getClosingEvidenceForVehicleYear(7, 2024, expectedFrom, expectedTo) }
    }

    @Test
    fun `uses non leap year millis boundaries when querying annual repositories`() = runTest {
        useCase(7, 2025)

        val expectedFrom = LocalDate.of(2025, 1, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val expectedTo = LocalDate.of(2026, 1, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli() - 1L

        coVerify { driveRepository.getAllDrivesForVehicleInYear(7, expectedFrom, expectedTo) }
        coVerify { odometerRepository.getOpeningEvidenceForVehicleYear(7, 2025, expectedFrom, expectedTo) }
        coVerify { odometerRepository.getClosingEvidenceForVehicleYear(7, 2025, expectedFrom, expectedTo) }
    }
}
