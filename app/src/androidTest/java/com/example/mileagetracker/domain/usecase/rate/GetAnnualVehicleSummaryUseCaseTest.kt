package com.example.mileagetracker.domain.usecase.rate

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.OdometerRepositoryImpl
import com.example.mileagetracker.data.repository.RateRepositoryImpl
import com.example.mileagetracker.data.repository.SettingsRepositoryImpl
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.OdometerCapture
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
class GetAnnualVehicleSummaryUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var driveRepository: DriveRepositoryImpl
    private lateinit var odometerRepository: OdometerRepositoryImpl
    private lateinit var rateRepository: RateRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var calculateReimbursementUseCase: CalculateReimbursementUseCase
    private lateinit var useCase: GetAnnualVehicleSummaryUseCase
    private lateinit var settingsFile: File
    private lateinit var settingsScope: CoroutineScope

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepository = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        odometerRepository = OdometerRepositoryImpl(
            dao = db.odometerCaptureDao(),
            context = ApplicationProvider.getApplicationContext(),
        )
        rateRepository = RateRepositoryImpl(db.craRateDao())

        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsFile = File(context.cacheDir, "annual_vehicle_summary_${System.nanoTime()}.preferences_pb")
        settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { settingsFile },
        )
        settingsRepository = SettingsRepositoryImpl(dataStore)
        calculateReimbursementUseCase = CalculateReimbursementUseCase(rateRepository, settingsRepository)
        useCase = GetAnnualVehicleSummaryUseCase(
            driveRepository = driveRepository,
            odometerRepository = odometerRepository,
            rateRepository = rateRepository,
            settingsRepository = settingsRepository,
            calculateReimbursement = calculateReimbursementUseCase,
        )
    }

    @After
    fun tearDown() {
        settingsScope.cancel()
        db.close()
        settingsFile.delete()
    }

    private suspend fun seedRate(
        taxYear: Int = 2026,
        firstTierRateCents: Int = 73,
        additionalKmRateCents: Int = 67,
        firstTierLimitKm: Int = 5_000,
    ) {
        rateRepository.insertRate(
            CraRateEntity(
                taxYear = taxYear,
                firstTierRateCents = firstTierRateCents,
                additionalKmRateCents = additionalKmRateCents,
                firstTierLimitKm = firstTierLimitKm,
                sourceLabel = "Test seed",
                effectiveFrom = 1L,
            ),
        )
    }

    private suspend fun insertVehicle(
        fiscalYear: Int = 2026,
        ownerUid: String = "owner-1",
        displayName: String = "Test Vehicle",
    ): Int = db.vehicleDao().insertVehicle(
        VehicleEntity(
            ownerUid = ownerUid,
            displayName = displayName,
            fiscalYear = fiscalYear,
            isActive = true,
        ),
    ).toInt()

    private fun drive(
        vehicleId: Int,
        driverUid: String = "driver-1",
        startedAt: Long,
        distanceKm: Float,
        tripCategory: TripCategory,
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

    private fun capture(
        vehicleId: Int,
        captureDate: Long,
        readingKm: Int,
    ) = OdometerCapture(
        vehicleId = vehicleId,
        captureDate = captureDate,
        readingKm = readingKm,
    )

    private fun utcMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
    ): Long = LocalDate.of(year, month, dayOfMonth)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    @Test
    fun invoke_returnsAnnualSummaryFromRealRoomData() = runTest {
        settingsRepository.setCraRateRegion(CraRateRegion.PROVINCES)
        seedRate(2026)

        val vehicleId = insertVehicle(fiscalYear = 2026)
        val otherVehicleId = insertVehicle(fiscalYear = 2026, displayName = "Other Vehicle")

        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 1, 10),
                distanceKm = 100f,
                tripCategory = TripCategory.BUSINESS,
            ),
        )
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 2, 12),
                distanceKm = 50f,
                tripCategory = TripCategory.PERSONAL,
            ),
        )
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 3, 14),
                distanceKm = 30f,
                tripCategory = TripCategory.COMMUTE,
            ),
        )
        driveRepository.saveDrive(
            drive(
                vehicleId = otherVehicleId,
                startedAt = utcMillis(2026, 4, 1),
                distanceKm = 999f,
                tripCategory = TripCategory.BUSINESS,
            ),
        )

        odometerRepository.saveCapture(capture(vehicleId, utcMillis(2026, 1, 1), 10_000))
        odometerRepository.saveCapture(capture(vehicleId, utcMillis(2026, 12, 31), 10_200))
        odometerRepository.saveCapture(capture(otherVehicleId, utcMillis(2026, 12, 31), 99_999))

        val summary = useCase(vehicleId = vehicleId, taxYear = 2026)

        assertEquals(10_000, summary.odometerOpenKm)
        assertEquals(10_200, summary.odometerCloseKm)
        assertEquals(200, summary.odometerTotalKm)
        assertEquals(100.0, summary.businessKm, 0.001)
        assertEquals(50.0, summary.personalKm, 0.001)
        assertEquals(180.0, summary.totalLoggedKm, 0.001)
        assertEquals(55.55, summary.businessPercent!!, 0.1)
        assertEquals(73.0, summary.reimbursementDollars!!, 0.001)
        assertEquals(3, summary.tripCount)
        assertEquals(1, summary.businessTripCount)
    }

    @Test
    fun invoke_territorialRegionOverridesStoredEntityValues() = runTest {
        settingsRepository.setCraRateRegion(CraRateRegion.TERRITORIES)
        seedRate(
            taxYear = 2026,
            firstTierRateCents = 1,
            additionalKmRateCents = 2,
            firstTierLimitKm = 100,
        )

        val vehicleId = insertVehicle(fiscalYear = 2026)
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 1, 10),
                distanceKm = 100f,
                tripCategory = TripCategory.BUSINESS,
            ),
        )

        val summary = useCase(vehicleId = vehicleId, taxYear = 2026)

        assertEquals(77.0, summary.reimbursementDollars!!, 0.001)
        val craRateUsed = summary.craRateUsed ?: error("Expected CRA rate for territorial summary")
        assertEquals(77, craRateUsed.tier1RateCentsPerKm)
        assertEquals(71, craRateUsed.tier2RateCentsPerKm)
        assertEquals(5_000, craRateUsed.tier1ThresholdKm)
    }

    @Test
    fun invoke_crossesCraThresholdAcrossMultipleBusinessDrives() = runTest {
        settingsRepository.setCraRateRegion(CraRateRegion.PROVINCES)
        seedRate(2026)

        val vehicleId = insertVehicle(fiscalYear = 2026)
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 2, 1),
                distanceKm = 2_500f,
                tripCategory = TripCategory.BUSINESS,
            ),
        )
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 1, 1),
                distanceKm = 3_000f,
                tripCategory = TripCategory.BUSINESS,
            ),
        )

        val summary = useCase(vehicleId = vehicleId, taxYear = 2026)

        assertEquals(5_500.0, summary.businessKm, 0.001)
        assertEquals(3_985.0, summary.reimbursementDollars!!, 0.001)
        assertEquals(2, summary.businessTripCount)
    }
}
