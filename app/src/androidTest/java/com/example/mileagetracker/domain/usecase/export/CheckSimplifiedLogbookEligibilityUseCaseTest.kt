package com.example.mileagetracker.domain.usecase.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.domain.model.SimplifiedLogbookStatus
import com.example.mileagetracker.domain.repository.SettingsRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Integration tests for [CheckSimplifiedLogbookEligibilityUseCase] against
 * a real (in-memory) Room database.
 *
 * ## Eligibility algorithm under test
 * 1. Scan prior years (2019 to currentYear − 1 inclusive).
 * 2. A year qualifies as a full base year when trip count ≥
 *    [SimplifiedLogbookStatus.MIN_BASE_YEAR_TRIPS] (50).
 * 3. If no qualifying year → return [SimplifiedLogbookStatus.ineligible].
 * 4. From qualifying years, prefer the most recent as the default base year.
 * 5. `requiredSampleKm` = base-year business km × 25% (3-month proxy).
 * 6. `currentSampleKm` = sum of BUSINESS km in the current calendar year.
 * 7. `sampleProgressPercent` = (currentSampleKm / requiredSampleKm) × 100,
 *    capped at 100.0.
 *
 * ## Timezone note
 * Year boundaries inside the use case are computed with
 * `Calendar.getInstance()` in the device's local timezone.  Test drives
 * are inserted at mid-year (June 15) in the same local timezone so they
 * always fall within the target year regardless of the device's offset.
 *
 * ## Current-year assumption
 * The use case reads `Calendar.getInstance().get(Calendar.YEAR)` at
 * invocation time.  These tests assume the device clock shows 2026 (the
 * year in which the tests were authored).  Base-year drives are in 2024
 * and 2025, both within the scan window [2019, 2025].
 */
@RunWith(AndroidJUnit4::class)
class CheckSimplifiedLogbookEligibilityUseCaseTest {

    private lateinit var db:       MileageDatabase
    private lateinit var useCase:  CheckSimplifiedLogbookEligibilityUseCase
    private lateinit var driveRepo: DriveRepositoryImpl

    private val driverUid = "u1"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo = DriveRepositoryImpl(db.driveDao(), db.routePointDao())

        // SettingsRepository is declared in the constructor but not used by the
        // use case body (annotated @Suppress("UnusedPrivateMember")), so a
        // relaxed mock is sufficient.
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)

        useCase = CheckSimplifiedLogbookEligibilityUseCase(driveRepo, settingsRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Returns an epoch-millis timestamp for June 15 noon in the device's local
     * timezone for [year].  This date is safely within the year in all timezones
     * from UTC−12 to UTC+14.
     */
    private fun midYear(year: Int): Long = Calendar.getInstance().apply {
        set(year, Calendar.JUNE, 15, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Epoch-millis timestamp for June 1 noon in [year] (used for 2026 sample drives). */
    private fun earlyYear(year: Int): Long = Calendar.getInstance().apply {
        set(year, Calendar.JUNE, 1, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private suspend fun insertDrive(
        year:        Int         = 2024,
        distanceKm:  Float       = 10f,
        category:    TripCategory = TripCategory.BUSINESS,
        useEarlyTs:  Boolean     = false,
    ) {
        val ts = if (useEarlyTs) earlyYear(year) else midYear(year)
        driveRepo.saveDrive(
            DriveEntity(
                startedAt            = ts,
                endedAt              = ts + 3_600_000L,
                distanceKm           = distanceKm,
                driverUid            = driverUid,
                startLocation        = "A",
                endLocation          = "B",
                tripCategory         = category,
                classificationStatus = ClassificationStatus.USER_CONFIRMED,
                trackingSource       = TrackingSource.AUTO_TRACKED,
            )
        )
    }

    /** Insert [n] business drives of [distanceKm] each in [year]. */
    private suspend fun insertBusinessDrives(
        n:          Int,
        year:       Int   = 2024,
        distanceKm: Float = 10f,
    ) {
        repeat(n) { insertDrive(year = year, distanceKm = distanceKm) }
    }

    // ── Ineligible paths ───────────────────────────────────────────────────────

    @Test
    fun invoke_noHistoricalData_returnsIneligible() = runTest {
        val status = useCase(driverUid)

        assertFalse(status.isEligible)
        assertNull(status.baseYear)
        assertEquals(0.0, status.baseYearBusinessUsePercent ?: 0.0, 0.001)
        assertEquals(0, status.baseYearTripCount)
        assertTrue(status.availableBaseYears.isEmpty())
    }

    @Test
    fun invoke_49TripsInPriorYear_remainsIneligible() = runTest {
        // One short of the 50-trip threshold.
        insertBusinessDrives(n = SimplifiedLogbookStatus.MIN_BASE_YEAR_TRIPS - 1, year = 2024)

        val status = useCase(driverUid)

        assertFalse(status.isEligible)
        assertNull(status.baseYear)
    }

    // ── Eligible path ──────────────────────────────────────────────────────────

    @Test
    fun invoke_exactly50TripsIn2024_returnsEligible() = runTest {
        insertBusinessDrives(n = SimplifiedLogbookStatus.MIN_BASE_YEAR_TRIPS, year = 2024)

        val status = useCase(driverUid)

        assertTrue(status.isEligible)
    }

    @Test
    fun invoke_eligibleYear_baseYearIsCorrect() = runTest {
        insertBusinessDrives(n = 50, year = 2024)

        val status = useCase(driverUid)

        assertEquals(2024, status.baseYear)
    }

    @Test
    fun invoke_eligibleYear_baseYearTripCountIsCorrect() = runTest {
        insertBusinessDrives(n = 60, year = 2024)

        val status = useCase(driverUid)

        assertEquals(60, status.baseYearTripCount)
    }

    // ── Business-use percentage ────────────────────────────────────────────────

    @Test
    fun invoke_businessUsePercent_computedCorrectly() = runTest {
        // 40 BUSINESS @ 10 km + 10 PERSONAL @ 10 km = 50 trips, 400 biz / 500 total = 80%
        insertBusinessDrives(n = 40, year = 2024, distanceKm = 10f)
        repeat(10) { insertDrive(year = 2024, distanceKm = 10f, category = TripCategory.PERSONAL) }

        val status = useCase(driverUid)

        assertTrue(status.isEligible)
        assertEquals(80.0, status.baseYearBusinessUsePercent!!, 0.5)
    }

    // ── Multiple eligible years ────────────────────────────────────────────────

    @Test
    fun invoke_multipleEligibleYears_mostRecentSelectedAsBaseYear() = runTest {
        // 2023 and 2024 both qualify; 2024 should be selected.
        insertBusinessDrives(n = 50, year = 2023)
        insertBusinessDrives(n = 50, year = 2024)

        val status = useCase(driverUid)

        assertEquals(2024, status.baseYear)
    }

    @Test
    fun invoke_multipleEligibleYears_allListedInAvailableBaseYears() = runTest {
        insertBusinessDrives(n = 50, year = 2023)
        insertBusinessDrives(n = 50, year = 2024)

        val status = useCase(driverUid)

        assertTrue("availableBaseYears should contain 2024",
            status.availableBaseYears.contains(2024))
        assertTrue("availableBaseYears should contain 2023",
            status.availableBaseYears.contains(2023))
        assertEquals(2, status.availableBaseYears.size)
    }

    @Test
    fun invoke_availableBaseYears_sortedDescending() = runTest {
        insertBusinessDrives(n = 50, year = 2022)
        insertBusinessDrives(n = 50, year = 2023)
        insertBusinessDrives(n = 50, year = 2024)

        val status = useCase(driverUid)

        // availableBaseYears should be [2024, 2023, 2022]
        assertEquals(listOf(2024, 2023, 2022), status.availableBaseYears)
    }

    // ── Current-year sample progress ───────────────────────────────────────────

    @Test
    fun invoke_currentYearBusiness_accumulatesInCurrentSampleKm() = runTest {
        // Need a qualifying base year first.
        insertBusinessDrives(n = 50, year = 2024)

        // Insert business drives in the current year (2026).
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        repeat(5) { insertDrive(year = currentYear, distanceKm = 20f, useEarlyTs = true) }

        val status = useCase(driverUid)

        assertEquals(100.0, status.currentSampleKm, 0.5)  // 5 × 20 km = 100 km
    }

    @Test
    fun invoke_currentYearSampleKm_presentEvenWhenIneligible() = runTest {
        // No qualifying base year, but current-year drives should still count.
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        repeat(3) { insertDrive(year = currentYear, distanceKm = 15f, useEarlyTs = true) }

        val status = useCase(driverUid)

        assertFalse(status.isEligible)
        assertEquals(45.0, status.currentSampleKm, 0.5)  // 3 × 15 km
    }

    // ── Sample progress percentage ─────────────────────────────────────────────

    @Test
    fun invoke_sampleProgressCappedAt100WhenKmExceedsRequired() = runTest {
        // Base year: 50 business drives @ 10 km = 500 business km.
        // requiredSampleKm = 500 × 0.25 = 125 km.
        // Current year: 200 km business → progress = 200/125 × 100 = 160% → capped to 100.
        insertBusinessDrives(n = 50, year = 2024, distanceKm = 10f)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        repeat(20) { insertDrive(year = currentYear, distanceKm = 10f, useEarlyTs = true) }

        val status = useCase(driverUid)

        assertEquals(100.0, status.sampleProgressPercent, 0.001)
    }

    @Test
    fun invoke_requiredSampleKm_isQuarterOfBaseYearBusinessKm() = runTest {
        // 50 trips @ 20 km = 1000 business km → required sample = 250 km.
        insertBusinessDrives(n = 50, year = 2024, distanceKm = 20f)

        val status = useCase(driverUid)

        assertEquals(250.0, status.requiredSampleKm, 0.5)
    }
}
