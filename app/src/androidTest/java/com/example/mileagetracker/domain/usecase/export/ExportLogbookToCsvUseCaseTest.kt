package com.example.mileagetracker.domain.usecase.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class ExportLogbookToCsvUseCaseTest {

    private lateinit var context: Context
    private lateinit var db: MileageDatabase
    private lateinit var driveRepository: DriveRepositoryImpl
    private lateinit var vehicleRepository: VehicleRepositoryImpl
    private lateinit var buildLogbookExportData: BuildLogbookExportDataUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: ExportLogbookToCsvUseCase
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(
            context,
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepository = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        vehicleRepository = VehicleRepositoryImpl(db.vehicleDao(), db)
        currentUserProvider = mockk()
        every { currentUserProvider.requireUid() } returns "u1"
        buildLogbookExportData = BuildLogbookExportDataUseCase(
            driveRepository = driveRepository,
            vehicleRepository = vehicleRepository,
            currentUserProvider = currentUserProvider,
        )
        useCase = ExportLogbookToCsvUseCase(buildLogbookExportData, context)

        exportDir().deleteRecursively()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
        db.close()
        exportDir().deleteRecursively()
    }

    @Test
    fun invoke_generatesFilteredSortedCsv_fromRealRoomData() = runTest {
        val vehicleId = insertVehicle(displayName = "Fleet Car")
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 1, 2),
                endLocation = "Client Site",
                distanceKm = 5.5f,
                tripCategory = TripCategory.BUSINESS,
            ),
        )
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 1, 3),
                endLocation = "Gym",
                distanceKm = 7f,
                tripCategory = TripCategory.PERSONAL,
            ),
        )
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 1, 1),
                endLocation = "Downtown Office",
                distanceKm = 10f,
                tripCategory = TripCategory.BUSINESS,
                notes = "Needs receipt",
            ),
        )

        val result = useCase(
            LogbookExportRequest(
                vehicleId = vehicleId,
                taxYear = 2026,
                format = ExportFormat.CSV,
                categories = setOf(TripCategory.BUSINESS),
                includeRouteDetails = true,
            ),
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(ExportFormat.CSV, success.format)

        val file = File(requireNotNull(success.fileUri.path))
        assertTrue(file.exists())
        assertEquals(file.length(), success.fileSizeBytes)
        assertTrue(file.name.startsWith("logbook_vehicle_${vehicleId}_2026_"))

        val content = file.readText()
        assertTrue(content.startsWith("\uFEFFDate,Start Location,End Location"))
        assertTrue(content.contains("2026-01-01"))
        assertTrue(content.contains("2026-01-02"))
        assertFalse(content.contains("2026-01-03"))
        assertTrue(content.indexOf("2026-01-01") < content.indexOf("2026-01-02"))
        assertTrue(content.contains("SUBTOTAL"))
        assertTrue(content.contains("TOTALS"))
        assertTrue(content.contains("# Scope: Fleet Car"))
    }

    @Test
    fun invoke_canExportNonBusinessCategories_andBlankRouteColumns_whenRequested() = runTest {
        val vehicleId = insertVehicle(displayName = "Family Car")
        driveRepository.saveDrive(
            drive(
                vehicleId = vehicleId,
                startedAt = utcMillis(2026, 2, 1),
                startLocation = "Home",
                endLocation = "Clinic",
                distanceKm = 7f,
                tripCategory = TripCategory.PERSONAL,
            ),
        )

        val result = useCase(
            LogbookExportRequest(
                vehicleId = vehicleId,
                taxYear = 2026,
                format = ExportFormat.CSV,
                categories = setOf(TripCategory.PERSONAL),
                includeRouteDetails = false,
            ),
        )

        assertTrue(result is ExportResult.Success)
        val file = File(requireNotNull((result as ExportResult.Success).fileUri.path))
        val dataLine = file.readLines().first { it.startsWith("2026-02-01") }
        val columns = dataLine.split(",")

        assertEquals("", columns[1])
        assertEquals("", columns[2])
        assertEquals("Clinic", columns[3])
        assertEquals("Personal", columns[4]) // Category
        assertEquals("Family Car", columns[6]) // Vehicle
        assertTrue(file.readText().contains("# Selected categories: Personal"))
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
        startLocation: String = "Home",
        endLocation: String = "Office",
        distanceKm: Float,
        tripCategory: TripCategory,
        notes: String? = null,
    ) = DriveEntity(
        startedAt = startedAt,
        endedAt = startedAt + 60_000L,
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = distanceKm,
        vehicleId = vehicleId,
        driverUid = "u1",
        tripCategory = tripCategory,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        trackingSource = TrackingSource.AUTO_TRACKED,
        notes = notes,
    )

    private fun utcMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
    ): Long = LocalDate.of(year, month, dayOfMonth)
        .atTime(12, 0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    private fun exportDir() = File(context.cacheDir, "exports")
}
