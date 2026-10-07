package com.example.mileagetracker.domain.usecase

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.data.repository.RateRepositoryImpl
import com.example.mileagetracker.data.repository.SettingsRepositoryImpl
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.domain.usecase.drive.GetTripSummaryUseCase
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

/**
 * Room/DataStore-backed integration tests for [GetReimbursementForecastUseCase].
 *
 * This class keeps the focus on the composed behaviour:
 * - real Room summary aggregation through [GetTripSummaryUseCase]
 * - real pending-approval reads from [ApprovalRepository]
 * - real policy lookup through [PolicyRepository]
 * - real preference persistence through [SettingsRepositoryImpl]
 */
@RunWith(AndroidJUnit4::class)
class GetReimbursementForecastUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var driveRepository: DriveRepositoryImpl
    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var rateRepository: RateRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var getTripSummaryUseCase: GetTripSummaryUseCase
    private lateinit var useCase: GetReimbursementForecastUseCase
    private lateinit var settingsFile: File
    private lateinit var settingsScope: CoroutineScope

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepository = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        approvalRepository = ApprovalRepository(db.approvalRequestDao(), db.periodLockDao())
        policyRepository = PolicyRepository(db.policyTemplateDao())
        rateRepository = RateRepositoryImpl(db.craRateDao())

        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsFile = File(context.cacheDir, "forecast_settings_${System.nanoTime()}.preferences_pb")
        settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { settingsFile },
        )
        settingsRepository = SettingsRepositoryImpl(dataStore)

        getTripSummaryUseCase = GetTripSummaryUseCase(
            driveRepository = driveRepository,
            rateRepository = rateRepository,
            settingsRepository = settingsRepository,
        )
        useCase = GetReimbursementForecastUseCase(
            getTripSummaryUseCase = getTripSummaryUseCase,
            approvalRepository = approvalRepository,
            policyRepository = policyRepository,
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

    private suspend fun seedForecastPrereqs(
        customRateDollars: Float = 0.55f,
        rateMode: RateMode = RateMode.CUSTOM,
    ) {
        settingsRepository.setCraRateRegion(CraRateRegion.PROVINCES)
        settingsRepository.setRateMode(rateMode)
        settingsRepository.setCustomRateDollars(customRateDollars)
        rateRepository.insertRate(
            CraRateEntity(
                taxYear = 2026,
                firstTierRateCents = 73,
                firstTierLimitKm = 5_000,
                additionalKmRateCents = 67,
                sourceLabel = "Test seed",
                effectiveFrom = 1L,
            ),
        )
    }

    private fun driver(
        userId: String,
        policyTemplateId: String? = null,
    ) = TeamMember(
        userId = userId,
        email = "$userId@example.com",
        displayName = "Driver $userId",
        role = TeamRole.DRIVER,
        policyTemplateId = policyTemplateId,
    )

    private fun drive(
        driverUid: String,
        startedAt: Long,
        distanceKm: Float,
    ) = DriveEntity(
        startedAt = startedAt,
        startLocation = "Start",
        endLocation = "End",
        distanceKm = distanceKm,
        driverUid = driverUid,
        tripCategory = TripCategory.BUSINESS,
    )

    private fun approval(
        submittedByUserId: Long,
        status: ApprovalStatus = ApprovalStatus.PENDING,
    ) = ApprovalRequest(
        tripId = 1L,
        submittedByUserId = submittedByUserId,
        submittedAt = 1_700_000_000_000L,
        status = status,
    )

    private fun policy(
        id: String,
        customRateCentsPerKm: Int,
    ) = PolicyTemplate(
        id = id,
        name = "Policy $id",
        rateMode = RateMode.CUSTOM,
        customRateCentsPerKm = customRateCentsPerKm,
    )

    private fun timestamp(
        year: Int,
        month: Int,
        dayOfMonth: Int,
    ): Long = java.util.Calendar.getInstance().apply {
        set(year, month, dayOfMonth, 12, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun invoke_usesRealRoomSummaryAndPendingApprovals() = runTest {
        seedForecastPrereqs(customRateDollars = 0.55f)

        driveRepository.saveDrive(
            drive(
                driverUid = "42",
                startedAt = timestamp(2026, java.util.Calendar.JANUARY, 10),
                distanceKm = 10f,
            ),
        )
        // Different month should not affect the January forecast.
        driveRepository.saveDrive(
            drive(
                driverUid = "42",
                startedAt = timestamp(2026, java.util.Calendar.FEBRUARY, 3),
                distanceKm = 99f,
            ),
        )
        approvalRepository.insertRequest(approval(42))
        approvalRepository.insertRequest(approval(42))
        approvalRepository.insertRequest(approval(7))
        approvalRepository.insertRequest(approval(42, status = ApprovalStatus.APPROVED))

        val forecasts = useCase(
            drivers = listOf(driver("42")),
            year = 2026,
            month = java.util.Calendar.JANUARY,
        )

        val forecast = forecasts.single()
        assertEquals("42", forecast.driverUid)
        assertEquals("Driver 42", forecast.driverDisplayName)
        assertEquals("January 2026", forecast.periodLabel)
        assertEquals(10f, forecast.actualBusinessKm, 0.001f)
        assertEquals(10f, forecast.projectedBusinessKm, 0.001f)
        assertEquals(5.5f, forecast.actualReimbursement, 0.001f)
        assertEquals(5.5f, forecast.projectedReimbursement, 0.001f)
        assertEquals(2, forecast.pendingApprovalCount)
        assertEquals("Custom: $0.55 / km", forecast.rateLabel)
    }

    @Test
    fun invoke_policyCustomRateOverridesGlobalCustomRate() = runTest {
        seedForecastPrereqs(customRateDollars = 0.55f)
        policyRepository.savePolicy(policy("policy-1", customRateCentsPerKm = 88))

        driveRepository.saveDrive(
            drive(
                driverUid = "99",
                startedAt = timestamp(2026, java.util.Calendar.JANUARY, 12),
                distanceKm = 10f,
            ),
        )
        driveRepository.saveDrive(
            drive(
                driverUid = "42",
                startedAt = timestamp(2026, java.util.Calendar.JANUARY, 12),
                distanceKm = 10f,
            ),
        )

        val forecasts = useCase(
            drivers = listOf(
                driver("99", policyTemplateId = "policy-1"),
                driver("42"),
            ),
            year = 2026,
            month = java.util.Calendar.JANUARY,
        )

        val policyForecast = forecasts.first { it.driverUid == "99" }
        val globalForecast = forecasts.first { it.driverUid == "42" }

        assertEquals("Custom: $0.88 / km", policyForecast.rateLabel)
        assertEquals(8.8f, policyForecast.actualReimbursement, 0.001f)
        assertEquals("Custom: $0.55 / km", globalForecast.rateLabel)
        assertEquals(5.5f, globalForecast.actualReimbursement, 0.001f)
    }
}
