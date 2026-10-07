package com.example.mileagetracker.domain.usecase.drive

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.RateRepositoryImpl
import com.example.mileagetracker.data.repository.SettingsRepositoryImpl
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.model.ReportPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class GetTripSummaryUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var driveRepository: DriveRepositoryImpl
    private lateinit var rateRepository: RateRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var useCase: GetTripSummaryUseCase
    private lateinit var settingsFile: File
    private lateinit var settingsScope: CoroutineScope

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepository = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        rateRepository = RateRepositoryImpl(db.craRateDao())

        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsFile = File(context.cacheDir, "trip_summary_${System.nanoTime()}.preferences_pb")
        settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { settingsFile },
        )
        settingsRepository = SettingsRepositoryImpl(dataStore)

        useCase = GetTripSummaryUseCase(
            driveRepository = driveRepository,
            rateRepository = rateRepository,
            settingsRepository = settingsRepository,
        )
    }

    @After
    fun tearDown() {
        settingsScope.cancel()
        db.close()
        settingsFile.delete()
    }

    private suspend fun seedCraSettings(year: Int = 2026) {
        settingsRepository.setRateMode(RateMode.CRA_STANDARD)
        settingsRepository.setCraRateRegion(CraRateRegion.PROVINCES)
        rateRepository.insertRate(
            CraRateEntity(
                taxYear = year,
                firstTierRateCents = 73,
                additionalKmRateCents = 67,
                firstTierLimitKm = 5_000,
                sourceLabel = "Test seed",
                effectiveFrom = 1L,
            ),
        )
    }

    private suspend fun seedCustomSettings(rateDollars: Float) {
        settingsRepository.setRateMode(RateMode.CUSTOM)
        settingsRepository.setCustomRateDollars(rateDollars)
    }

    private fun drive(
        startedAt: Long,
        distanceKm: Float,
        tripCategory: TripCategory = TripCategory.BUSINESS,
        vehicleId: Int? = 1,
        driverUid: String = "driver-1",
    ) = DriveEntity(
        startedAt = startedAt,
        endedAt = startedAt + 60_000L,
        startLocation = "Start",
        endLocation = "End",
        distanceKm = distanceKm,
        vehicleId = vehicleId,
        driverUid = driverUid,
        tripCategory = tripCategory,
    )

    private fun utcMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
    ): Long = LocalDate.of(year, month, dayOfMonth)
        .atTime(12, 0)
        .atZone(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    @Test
    fun invoke_monthlyBucketsComeFromRealRoomData() = runTest {
        seedCraSettings(2026)

        driveRepository.saveDrive(drive(utcMillis(2026, 1, 10), 100f))
        driveRepository.saveDrive(drive(utcMillis(2026, 1, 15), 50f))
        driveRepository.saveDrive(drive(utcMillis(2026, 2, 5), 200f))
        driveRepository.saveDrive(drive(utcMillis(2026, 2, 12), 30f, tripCategory = TripCategory.PERSONAL))

        val summaries = useCase("driver-1", 2026, ReportPeriod.MONTHLY)

        assertEquals(2, summaries.size)
        assertEquals("January 2026", summaries[0].periodLabel)
        assertEquals(150f, summaries[0].businessKm, 0.001f)
        assertEquals(150f, summaries[0].totalKm, 0.001f)
        assertEquals(109.5f, summaries[0].estimatedReimbursement, 0.001f)
        assertEquals("February 2026", summaries[1].periodLabel)
        assertEquals(200f, summaries[1].businessKm, 0.001f)
        assertEquals(30f, summaries[1].personalKm, 0.001f)
        assertEquals(230f, summaries[1].totalKm, 0.001f)
        assertEquals(146f, summaries[1].estimatedReimbursement, 0.001f)
    }

    @Test
    fun invoke_monthlyReimbursementCrossesTierThresholdAcrossBuckets() = runTest {
        seedCraSettings(2026)

        driveRepository.saveDrive(drive(utcMillis(2026, 1, 1), 4_800f))
        driveRepository.saveDrive(drive(utcMillis(2026, 2, 1), 400f))

        val summaries = useCase("driver-1", 2026, ReportPeriod.MONTHLY)

        assertEquals(2, summaries.size)
        assertEquals(3_504f, summaries[0].estimatedReimbursement, 0.001f)
        assertEquals(280f, summaries[1].estimatedReimbursement, 0.001f)
    }

    @Test
    fun invoke_customRateSupportsVehicleFilterAndCraEligibleCategories() = runTest {
        seedCustomSettings(rateDollars = 0.50f)

        driveRepository.saveDrive(drive(utcMillis(2026, 1, 10), 100f, vehicleId = 1))
        driveRepository.saveDrive(
            drive(utcMillis(2026, 2, 10), 40f, tripCategory = TripCategory.MEDICAL, vehicleId = 1)
        )
        driveRepository.saveDrive(
            drive(utcMillis(2026, 3, 10), 20f, tripCategory = TripCategory.PERSONAL, vehicleId = 1)
        )
        driveRepository.saveDrive(drive(utcMillis(2026, 4, 10), 999f, vehicleId = 2))

        val summaries = useCase(
            driverUid = "driver-1",
            year = 2026,
            period = ReportPeriod.ANNUAL,
            vehicleId = 1,
            categories = TripCategory.craEligibleCategories,
        )

        val summary = summaries.single()
        assertEquals("2026", summary.periodLabel)
        assertEquals(160f, summary.totalKm, 0.001f)
        assertEquals(140f, summary.businessKm, 0.001f)
        assertEquals(20f, summary.personalKm, 0.001f)
        assertEquals(70f, summary.estimatedReimbursement, 0.001f)
        assertEquals(3, summary.tripCount)
        assertEquals(1, summary.vehicleId)
    }
}
