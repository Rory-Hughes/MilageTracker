package com.example.mileagetracker.domain.usecase.export

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.OdometerRepositoryImpl
import com.example.mileagetracker.data.repository.RateRepositoryImpl
import com.example.mileagetracker.data.repository.SettingsRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.GetAnnualVehicleSummaryUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ExportLogbookToPdfUseCaseTest {

    private lateinit var context: Context
    private lateinit var db: MileageDatabase
    private lateinit var driveRepository: DriveRepositoryImpl
    private lateinit var vehicleRepository: VehicleRepositoryImpl
    private lateinit var odometerRepository: OdometerRepositoryImpl
    private lateinit var rateRepository: RateRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var calculateReimbursement: CalculateReimbursementUseCase
    private lateinit var annualSummary: GetAnnualVehicleSummaryUseCase
    private lateinit var buildLogbookExportData: BuildLogbookExportDataUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: ExportLogbookToPdfUseCase
    private lateinit var settingsFile: File
    private lateinit var settingsScope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(
            context,
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepository = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        vehicleRepository = VehicleRepositoryImpl(db.vehicleDao(), db)
        odometerRepository = OdometerRepositoryImpl(db.odometerCaptureDao(), context)
        rateRepository = RateRepositoryImpl(db.craRateDao())

        settingsFile = File(
            context.cacheDir,
            "export_pdf_${System.nanoTime()}.preferences_pb",
        )
        settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { settingsFile },
        )
        settingsRepository = SettingsRepositoryImpl(dataStore)
        calculateReimbursement = CalculateReimbursementUseCase(
            rateRepository = rateRepository,
            settingsRepository = settingsRepository,
        )
        annualSummary = GetAnnualVehicleSummaryUseCase(
            driveRepository = driveRepository,
            odometerRepository = odometerRepository,
            rateRepository = rateRepository,
            settingsRepository = settingsRepository,
            calculateReimbursement = calculateReimbursement,
        )
        currentUserProvider = mockk()
        every { currentUserProvider.requireUid() } returns "u1"
        buildLogbookExportData = BuildLogbookExportDataUseCase(
            driveRepository = driveRepository,
            vehicleRepository = vehicleRepository,
            currentUserProvider = currentUserProvider,
        )
        useCase = ExportLogbookToPdfUseCase(
            buildLogbookExportData = buildLogbookExportData,
            annualSummary = annualSummary,
            context = context,
        )

        exportDir().deleteRecursively()
    }

    @After
    fun tearDown() {
        settingsScope.cancel()
        settingsFile.delete()
        db.close()
        exportDir().deleteRecursively()
    }

    @Test
    fun invoke_generatesReadablePdfFile_onDevice() = runTest {
        settingsRepository.setCraRateRegion(CraRateRegion.PROVINCES)
        seedRate(taxYear = 2026)

        val vehicleId = insertVehicle(displayName = "Mileage Car")
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 3, 1),
                distanceKm = 12.5f,
                tripCategory = TripCategory.BUSINESS,
            ),
        )
        odometerRepository.saveCapture(
            OdometerCapture(
                vehicleId = vehicleId,
                captureDate = utcMillis(2026, 1, 1),
                readingKm = 10_000,
            ),
        )
        odometerRepository.saveCapture(
            OdometerCapture(
                vehicleId = vehicleId,
                captureDate = utcMillis(2026, 12, 31),
                readingKm = 10_250,
            ),
        )

        val result = useCase(
            LogbookExportRequest(
                vehicleId = vehicleId,
                taxYear = 2026,
                format = ExportFormat.PDF,
                categories = setOf(TripCategory.BUSINESS),
                includeRouteDetails = false,
            ),
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(ExportFormat.PDF, success.format)

        val file = File(requireNotNull(success.fileUri.path))
        assertTrue(file.exists())
        assertTrue(file.length() > 0L)
        assertEquals(file.length(), success.fileSizeBytes)
        assertEquals("%PDF", pdfHeader(file))
        assertEquals(2, pageCount(file))
    }

    @Test
    fun invoke_generatesGroupedAllVehiclePdf_withCombinedCoverAndSections() = runTest {
        settingsRepository.setCraRateRegion(CraRateRegion.PROVINCES)
        seedRate(taxYear = 2026)

        val sedanId = insertVehicle(displayName = "Sedan")
        val truckId = insertVehicle(displayName = "Truck")
        driveRepository.saveDrive(
            drive(
                vehicleId = sedanId,
                startedAt = utcMillis(2026, 1, 1),
                distanceKm = 5f,
                tripCategory = TripCategory.BUSINESS,
                endLocation = "Sedan Stop",
            ),
        )
        driveRepository.saveDrive(
            drive(
                vehicleId = truckId,
                startedAt = utcMillis(2026, 1, 2),
                distanceKm = 6f,
                tripCategory = TripCategory.BUSINESS,
                endLocation = "Truck Stop",
            ),
        )

        val result = useCase(
            LogbookExportRequest(
                vehicleId = null,
                taxYear = 2026,
                format = ExportFormat.PDF,
                categories = setOf(TripCategory.BUSINESS),
                includeRouteDetails = false,
            ),
        )

        assertTrue(result is ExportResult.Success)
        val file = File(requireNotNull((result as ExportResult.Success).fileUri.path))
        assertTrue(file.name.startsWith("logbook_all_vehicles_2026_"))
        assertEquals(3, pageCount(file))
    }

    @Test
    fun invoke_addsExtraTripPage_whenTripRowsExceedSinglePageCapacity() = runTest {
        settingsRepository.setCraRateRegion(CraRateRegion.PROVINCES)
        seedRate(taxYear = 2026)

        val vehicleId = insertVehicle(displayName = "Fleet Van")
        repeat(33) { index ->
            driveRepository.saveDrive(
                drive(
                    vehicleId = vehicleId,
                    startedAt = LocalDate.of(2026, 1, 1)
                        .plusDays(index.toLong())
                        .atTime(12, 0)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli(),
                    distanceKm = 1f,
                    tripCategory = TripCategory.BUSINESS,
                    endLocation = "Stop ${index + 1}",
                ),
            )
        }

        val result = useCase(
            LogbookExportRequest(
                vehicleId = vehicleId,
                taxYear = 2026,
                format = ExportFormat.PDF,
                categories = setOf(TripCategory.BUSINESS),
                includeRouteDetails = false,
            ),
        )

        assertTrue(result is ExportResult.Success)
        val file = File(requireNotNull((result as ExportResult.Success).fileUri.path))
        assertEquals(3, pageCount(file))
    }

    private suspend fun seedRate(
        taxYear: Int,
        firstTierRateCents: Int = 70,
        additionalKmRateCents: Int = 64,
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
        displayName: String,
        ownerUid: String = "u1",
        fiscalYear: Int = 2026,
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
        startedAt: Long,
        distanceKm: Float,
        tripCategory: TripCategory,
        endLocation: String = "Office",
    ) = DriveEntity(
        startedAt = startedAt,
        endedAt = startedAt + 60_000L,
        startLocation = "Home",
        endLocation = endLocation,
        distanceKm = distanceKm,
        vehicleId = vehicleId,
        driverUid = "u1",
        tripCategory = tripCategory,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        trackingSource = TrackingSource.AUTO_TRACKED,
    )

    private fun utcMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
    ): Long = LocalDate.of(year, month, dayOfMonth)
        .atTime(12, 0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    private fun pdfHeader(file: File): String {
        val bytes = ByteArray(4)
        file.inputStream().use { input ->
            input.read(bytes)
        }
        return String(bytes, Charsets.US_ASCII)
    }

    private fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.pageCount
            }
        }

    private fun exportDir() = File(context.cacheDir, "exports")
}
