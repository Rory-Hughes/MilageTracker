package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.model.ReportPeriod
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class GetTripSummaryUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var rateRepository: RateRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: GetTripSummaryUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        rateRepository = mockk()
        settingsRepository = mockk()
        useCase = GetTripSummaryUseCase(driveRepository, rateRepository, settingsRepository)
    }

    private fun testDrive(month: Int, km: Float, category: TripCategory = TripCategory.BUSINESS) = DriveEntity(
        id = 0,
        startedAt = Calendar.getInstance().apply { set(2025, month, 15) }.timeInMillis,
        endedAt = null,
        startLocation = "A",
        endLocation = "B",
        distanceKm = km,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = category,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1000L
    )

    private fun mockRates() {
        every { settingsRepository.getRateMode() } returns flowOf(RateMode.CRA_STANDARD)
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)
        coEvery { rateRepository.getRateForYearOnce(2025) } returns CraRateEntity(
            taxYear = 2025,
            firstTierRateCents = 70,
            additionalKmRateCents = 64,
            firstTierLimitKm = 5000,
            sourceLabel = "Seed",
            effectiveFrom = 1000L
        )
    }

    @Test
    fun `aggregateAnnual calculates correct totals`() = runTest {
        mockRates()
        val drives = listOf(
            testDrive(Calendar.JANUARY, 100f),
            testDrive(Calendar.FEBRUARY, 200f),
            testDrive(Calendar.MARCH, 50f, TripCategory.PERSONAL)
        )
        coEvery { driveRepository.getDrivesForAnnualSummary(any(), any(), any()) } returns drives

        val results = useCase("u1", 2025, ReportPeriod.ANNUAL)

        assertEquals(1, results.size)
        val summary = results[0]
        assertEquals(350f, summary.totalKm)
        assertEquals(300f, summary.businessKm)
        assertEquals(50f, summary.personalKm)
        assertEquals(3, summary.tripCount)
        // 300km * 0.72 = 216.0
        assertEquals(216f, summary.estimatedReimbursement, 0.01f)
    }

    @Test
    fun `aggregateMonthly buckets drives by month`() = runTest {
        mockRates()
        val drives = listOf(
            testDrive(Calendar.JANUARY, 100f),
            testDrive(Calendar.JANUARY, 50f),
            testDrive(Calendar.FEBRUARY, 200f)
        )
        coEvery { driveRepository.getDrivesForAnnualSummary(any(), any(), any()) } returns drives

        val results = useCase("u1", 2025, ReportPeriod.MONTHLY)

        assertEquals(2, results.size)
        assertEquals("January 2025", results[0].periodLabel)
        assertEquals(150f, results[0].businessKm)
        assertEquals("February 2025", results[1].periodLabel)
        assertEquals(200f, results[1].businessKm)
    }

    @Test
    fun `twoTierReimbursement across threshold works correctly`() = runTest {
        mockRates()
        // Drive in Jan: 4800km (Total YTD: 4800)
        // Drive in Feb: 400km  (Total YTD: 5200) -> 200 in tier 1, 200 in tier 2
        val drives = listOf(
            testDrive(Calendar.JANUARY, 4800f),
            testDrive(Calendar.FEBRUARY, 400f)
        )
        coEvery { driveRepository.getDrivesForAnnualSummary(any(), any(), any()) } returns drives

        val results = useCase("u1", 2025, ReportPeriod.MONTHLY)

        assertEquals(2, results.size)
        // Jan: 4800 * 0.72 = 3456.0
        assertEquals(3456f, results[0].estimatedReimbursement, 0.01f)
        // Feb: (200 * 0.72) + (200 * 0.66) = 144.0 + 132.0 = 276.0
        assertEquals(276f, results[1].estimatedReimbursement, 0.01f)
    }

    @Test
    fun `custom rate mode uses flat rate`() = runTest {
        every { settingsRepository.getRateMode() } returns flowOf(RateMode.CUSTOM)
        every { settingsRepository.getCustomRateDollars() } returns flowOf(0.50f)
        
        val drives = listOf(testDrive(Calendar.JANUARY, 100f))
        coEvery { driveRepository.getDrivesForAnnualSummary(any(), any(), any()) } returns drives

        val results = useCase("u1", 2025, ReportPeriod.ANNUAL)

        // 100km * 0.50 = 50.0
        assertEquals(50f, results[0].estimatedReimbursement, 0.01f)
    }
}
