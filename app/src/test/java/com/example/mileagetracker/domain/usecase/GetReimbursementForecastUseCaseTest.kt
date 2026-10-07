package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.CraRateRegionRequiredException
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.domain.model.TripSummary
import com.example.mileagetracker.domain.model.ReportPeriod
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.drive.GetTripSummaryUseCase
import com.example.mileagetracker.domain.usecase.rate.RateNotSeededException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class GetReimbursementForecastUseCaseTest {

    private lateinit var getTripSummaryUseCase: GetTripSummaryUseCase
    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var rateRepository: RateRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: GetReimbursementForecastUseCase

    @Before
    fun setUp() {
        mockkStatic(Calendar::class)
        getTripSummaryUseCase = mockk()
        approvalRepository = mockk()
        policyRepository = mockk()
        rateRepository = mockk()
        settingsRepository = mockk()
        useCase = GetReimbursementForecastUseCase(
            getTripSummaryUseCase,
            approvalRepository,
            policyRepository,
            rateRepository,
            settingsRepository
        )

        coEvery { getTripSummaryUseCase(any(), any(), any()) } returns emptyList()
        every { approvalRepository.observePending() } returns flowOf(emptyList())
        coEvery { policyRepository.getById(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(Calendar::class)
    }

    private fun testDriver(id: String) = TeamMember(
        userId = id,
        email = "$id@example.com",
        displayName = "Driver $id",
        role = TeamRole.DRIVER,
        isActive = true
    )

    private fun mockGlobalRates(
        region: CraRateRegion = CraRateRegion.PROVINCES,
        rateMode: RateMode = RateMode.CRA_STANDARD,
        customRateDollars: Float = 0.70f,
    ) {
        every { settingsRepository.getCraRateRegion() } returns flowOf(region)
        every { settingsRepository.getRateMode() } returns flowOf(rateMode)
        every { settingsRepository.getCustomRateDollars() } returns flowOf(customRateDollars)
        coEvery { rateRepository.getRateForYearOnce(any()) } answers { CraRateEntity(
            taxYear = firstArg(),
            firstTierRateCents = 70,
            firstTierLimitKm = 5000,
            additionalKmRateCents = 64,
            sourceLabel = "Seed",
            effectiveFrom = 0L
        ) }
    }

    @Test
    fun `forecasts correctly for standard CRA rate and mid-month progress`() = runTest {
        mockGlobalRates(region = CraRateRegion.PROVINCES, rateMode = RateMode.CRA_STANDARD)
        val driver = testDriver("u1")

        // Mock current date to Jan 16th, 2025
        mockNow(2025, Calendar.JANUARY, 16)

        // January summary: 100km business
        val summary = TripSummary(
            periodLabel = "January 2025",
            periodType = ReportPeriod.MONTHLY,
            year = 2025,
            totalKm = 100f,
            businessKm = 100f,
            personalKm = 0f,
            tripCount = 1,
            estimatedReimbursement = 72f
        )
        coEvery { getTripSummaryUseCase("u1", 2025, ReportPeriod.MONTHLY) } returns listOf(summary)
        every { approvalRepository.observePending() } returns flowOf(emptyList())

        // Use Jan (0) 2025. 
        // Jan 16 / 31 = approx 0.516
        val forecasts = useCase(listOf(driver), year = 2025, month = Calendar.JANUARY)

        assertEquals(1, forecasts.size)
        val f = forecasts[0]
        assertEquals(100f, f.actualBusinessKm)
        // 100 / (16/31) = 193.75
        assertEquals(193.75f, f.projectedBusinessKm, 0.01f)
        assertEquals(72f, f.actualReimbursement, 0.01f)
        // 193.75 * 0.72 = 139.5
        assertEquals(139.5f, f.projectedReimbursement, 0.01f)
    }

    @Test
    fun `throws when CRA region has not been selected`() = runTest {
        every { settingsRepository.getCraRateRegion() } returns flowOf(null)

        try {
            useCase(listOf(testDriver("u1")), year = 2026, month = Calendar.JANUARY)
            fail("Expected CraRateRegionRequiredException")
        } catch (ex: CraRateRegionRequiredException) {
            assertTrue(ex.message!!.contains("Select provinces or territories"))
        }
    }

    @Test
    fun `throws when seeded rate row is missing for requested year`() = runTest {
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)
        coEvery { rateRepository.getRateForYearOnce(2026) } returns null

        try {
            useCase(listOf(testDriver("u1")), year = 2026, month = Calendar.JANUARY)
            fail("Expected RateNotSeededException")
        } catch (ex: RateNotSeededException) {
            assertEquals(2026, ex.taxYear)
        }
    }

    @Test
    fun `throws when CRA catalog does not contain requested year`() = runTest {
        mockGlobalRates(region = CraRateRegion.PROVINCES, rateMode = RateMode.CRA_STANDARD)
        coEvery { rateRepository.getRateForYearOnce(2040) } returns CraRateEntity(
            taxYear = 2040,
            firstTierRateCents = 99,
            firstTierLimitKm = 5000,
            additionalKmRateCents = 99,
            sourceLabel = "manual",
            effectiveFrom = 0L,
        )

        try {
            useCase(listOf(testDriver("u1")), year = 2040, month = Calendar.JANUARY)
            fail("Expected RateNotSeededException")
        } catch (ex: RateNotSeededException) {
            assertEquals(2040, ex.taxYear)
        }
    }

    @Test
    fun `uses territorial CRA rate for forecast and label`() = runTest {
        mockGlobalRates(region = CraRateRegion.TERRITORIES, rateMode = RateMode.CRA_STANDARD)
        val driver = testDriver("u1")
        mockNow(2026, Calendar.JANUARY, 16)
        coEvery { getTripSummaryUseCase("u1", 2026, ReportPeriod.MONTHLY) } returns listOf(
            summary(periodLabel = "January 2026", businessKm = 100f),
        )

        val forecasts = useCase(listOf(driver), year = 2026, month = Calendar.JANUARY)

        val forecast = forecasts.single()
        assertEquals("$0.77 / km (CRA prescribed allowance estimate)", forecast.rateLabel)
        assertEquals(77f, forecast.actualReimbursement, 0.01f)
        assertEquals(149.19f, forecast.projectedReimbursement, 0.01f)
    }

    @Test
    fun `uses global custom rate when enabled and positive`() = runTest {
        mockGlobalRates(region = CraRateRegion.PROVINCES, rateMode = RateMode.CUSTOM, customRateDollars = 0.55f)
        val driver = testDriver("u1")
        mockNow(2026, Calendar.JANUARY, 10)
        coEvery { getTripSummaryUseCase("u1", 2025, ReportPeriod.MONTHLY) } returns listOf(
            summary(periodLabel = "January 2025", businessKm = 10f),
        )

        val forecasts = useCase(listOf(driver), year = 2025, month = Calendar.JANUARY)

        val forecast = forecasts.single()
        assertEquals("Custom: $0.55 / km", forecast.rateLabel)
        assertEquals(5.5f, forecast.actualReimbursement, 0.01f)
        assertEquals(5.5f, forecast.projectedReimbursement, 0.01f)
    }

    @Test
    fun `falls back to CRA when global custom rate is non-positive`() = runTest {
        mockGlobalRates(region = CraRateRegion.PROVINCES, rateMode = RateMode.CUSTOM, customRateDollars = 0f)
        val driver = testDriver("u1")
        mockNow(2026, Calendar.FEBRUARY, 10)
        coEvery { getTripSummaryUseCase("u1", 2026, ReportPeriod.MONTHLY) } returns listOf(
            summary(periodLabel = "January 2026", businessKm = 10f),
        )

        val forecasts = useCase(listOf(driver), year = 2026, month = Calendar.JANUARY)

        val forecast = forecasts.single()
        assertEquals("$0.73 / km (CRA prescribed allowance estimate)", forecast.rateLabel)
        assertEquals(7.3f, forecast.actualReimbursement, 0.01f)
        assertEquals(7.3f, forecast.projectedReimbursement, 0.01f)
    }

    @Test
    fun `driver custom policy overrides global custom rate`() = runTest {
        mockGlobalRates(region = CraRateRegion.PROVINCES, rateMode = RateMode.CUSTOM, customRateDollars = 0.55f)
        val driver = testDriver("u1").copy(policyTemplateId = "p1")
        mockNow(2026, Calendar.FEBRUARY, 10)
        coEvery { policyRepository.getById("p1") } returns policyTemplate(
            id = "p1",
            rateMode = RateMode.CUSTOM,
            customRateCentsPerKm = 88,
        )
        coEvery { getTripSummaryUseCase("u1", 2026, ReportPeriod.MONTHLY) } returns listOf(
            summary(periodLabel = "January 2026", businessKm = 10f),
        )

        val forecasts = useCase(listOf(driver), year = 2026, month = Calendar.JANUARY)

        val forecast = forecasts.single()
        assertEquals("Custom: $0.88 / km", forecast.rateLabel)
        assertEquals(8.8f, forecast.actualReimbursement, 0.01f)
    }

    @Test
    fun `driver CRA policy still uses global custom rate`() = runTest {
        mockGlobalRates(region = CraRateRegion.PROVINCES, rateMode = RateMode.CUSTOM, customRateDollars = 0.55f)
        val driver = testDriver("u1").copy(policyTemplateId = "p2")
        mockNow(2026, Calendar.FEBRUARY, 10)
        coEvery { policyRepository.getById("p2") } returns policyTemplate(
            id = "p2",
            rateMode = RateMode.CRA_STANDARD,
            customRateCentsPerKm = null,
        )
        coEvery { getTripSummaryUseCase("u1", 2026, ReportPeriod.MONTHLY) } returns listOf(
            summary(periodLabel = "January 2026", businessKm = 10f),
        )

        val forecasts = useCase(listOf(driver), year = 2026, month = Calendar.JANUARY)

        val forecast = forecasts.single()
        assertEquals("Custom: $0.55 / km", forecast.rateLabel)
        assertEquals(5.5f, forecast.actualReimbursement, 0.01f)
    }

    @Test
    fun `driver custom policy without rate falls back to CRA estimate`() = runTest {
        mockGlobalRates(region = CraRateRegion.PROVINCES, rateMode = RateMode.CUSTOM, customRateDollars = 0.55f)
        val driver = testDriver("u1").copy(policyTemplateId = "p3")
        mockNow(2026, Calendar.FEBRUARY, 10)
        coEvery { policyRepository.getById("p3") } returns policyTemplate(
            id = "p3",
            rateMode = RateMode.CUSTOM,
            customRateCentsPerKm = null,
        )
        coEvery { getTripSummaryUseCase("u1", 2026, ReportPeriod.MONTHLY) } returns listOf(
            summary(periodLabel = "January 2026", businessKm = 10f),
        )

        val forecasts = useCase(listOf(driver), year = 2026, month = Calendar.JANUARY)

        val forecast = forecasts.single()
        assertEquals("$0.73 / km (CRA prescribed allowance estimate)", forecast.rateLabel)
        assertEquals(7.3f, forecast.actualReimbursement, 0.01f)
    }

    @Test
    fun `pending approvals only count for numeric matching driver uid`() = runTest {
        mockGlobalRates(region = CraRateRegion.PROVINCES, rateMode = RateMode.CRA_STANDARD)
        val numericDriver = testDriver("42")
        val stringDriver = testDriver("uid-x")
        mockNow(2026, Calendar.FEBRUARY, 10)
        coEvery { getTripSummaryUseCase("42", 2026, ReportPeriod.MONTHLY) } returns listOf(
            summary(periodLabel = "January 2026", businessKm = 10f),
        )
        coEvery { getTripSummaryUseCase("uid-x", 2026, ReportPeriod.MONTHLY) } returns listOf(
            summary(periodLabel = "January 2026", businessKm = 5f),
        )
        every { approvalRepository.observePending() } returns flowOf(
            listOf(
                pendingApproval(42),
                pendingApproval(42),
                pendingApproval(7),
            ),
        )

        val forecasts = useCase(listOf(numericDriver, stringDriver), year = 2026, month = Calendar.JANUARY)

        val numericForecast = forecasts.first { it.driverUid == "42" }
        val stringForecast = forecasts.first { it.driverUid == "uid-x" }
        assertEquals(2, numericForecast.pendingApprovalCount)
        assertEquals(0, stringForecast.pendingApprovalCount)
    }

    @Test
    fun `forecasts past month as fully elapsed`() = runTest {
        mockGlobalRates()
        val driver = testDriver("u1")
        // Today is Feb 15th
        mockNow(2025, Calendar.FEBRUARY, 15)
        
        // We are forecasting for January
        coEvery { getTripSummaryUseCase("u1", 2025, ReportPeriod.MONTHLY) } returns listOf(
            summary("January 2025", businessKm = 100f)
        )

        val forecasts = useCase(listOf(driver), year = 2025, month = Calendar.JANUARY)

        val f = forecasts.single()
        // Fraction should be 1.0, so projected == actual
        assertEquals(100f, f.actualBusinessKm)
        assertEquals(100f, f.projectedBusinessKm, 0.01f)
    }

    private fun mockNow(
        year: Int,
        month: Int,
        dayOfMonth: Int,
    ) {
        val mockCal = Calendar.getInstance()
        mockCal.set(year, month, dayOfMonth, 12, 0, 0)
        every { Calendar.getInstance() } returns mockCal
    }

    private fun summary(
        periodLabel: String,
        businessKm: Float,
    ) = TripSummary(
        periodLabel = periodLabel,
        periodType = ReportPeriod.MONTHLY,
        year = 2026,
        totalKm = businessKm,
        businessKm = businessKm,
        personalKm = 0f,
        tripCount = 1,
        estimatedReimbursement = 0f,
    )

    private fun policyTemplate(
        id: String,
        rateMode: RateMode,
        customRateCentsPerKm: Int?,
    ) = PolicyTemplate(
        id = id,
        name = "Policy $id",
        rateMode = rateMode,
        customRateCentsPerKm = customRateCentsPerKm,
    )

    private fun pendingApproval(submittedByUserId: Long) =
        com.example.mileagetracker.domain.model.ApprovalRequest(
            submittedByUserId = submittedByUserId,
            submittedAt = 1_700_000_000_000L,
            status = com.example.mileagetracker.domain.model.ApprovalStatus.PENDING,
            tripId = 1L,
        )
}
