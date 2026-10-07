package com.example.mileagetracker.domain.usecase.export

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BuildLogbookExportDataUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: BuildLogbookExportDataUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        vehicleRepository = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "uid-1"
        every { currentUserProvider.currentUser() } returns CurrentUser(
            uid = "uid-1",
            email = null,
            displayName = null,
            createdAtMillis = null,
        )

        useCase = BuildLogbookExportDataUseCase(
            driveRepository = driveRepository,
            vehicleRepository = vehicleRepository,
            currentUserProvider = currentUserProvider,
        )
    }

    @Test
    fun `invoke scopes single vehicle and keeps total km denominator`() = runTest {
        coEvery { vehicleRepository.getVehicleById(1) } returns vehicle(id = 1, name = "Sedan")
        coEvery { driveRepository.getDrivesForAnnualSummary("uid-1", any(), any()) } returns listOf(
            drive(id = 1, vehicleId = 1, distanceKm = 10f, category = TripCategory.BUSINESS),
            drive(id = 2, vehicleId = 1, distanceKm = 5f, category = TripCategory.PERSONAL),
            drive(id = 3, vehicleId = 2, distanceKm = 20f, category = TripCategory.BUSINESS),
        )

        val result = useCase(
            LogbookExportRequest(
                vehicleId = 1,
                taxYear = 2026,
                format = ExportFormat.CSV,
                categories = setOf(TripCategory.BUSINESS),
            )
        )

        assertEquals("Sedan", result.scopeLabel)
        assertEquals("vehicle_1", result.scopeToken)
        assertEquals(1, result.sections.size)
        assertEquals("Sedan", result.sections.single().label)
        assertEquals(10.0, result.sections.single().totals.selectedKm, 0.001)
        assertEquals(15.0, result.sections.single().totals.totalKm, 0.001)
        assertEquals(66.666, result.sections.single().totals.selectedUsePercent, 0.01)
        assertEquals(10.0, result.totals.selectedKm, 0.001)
        assertEquals(15.0, result.totals.totalKm, 0.001)
    }

    @Test
    fun `invoke groups all vehicles alphabetically and places unassigned last`() = runTest {
        coEvery {
            vehicleRepository.getVehicleById(1)
        } returns vehicle(
            id = 1,
            name = "",
            make = "Alpha",
            model = "Coupe",
        )
        coEvery {
            vehicleRepository.getVehicleById(2)
        } returns vehicle(
            id = 2,
            name = "Bravo"
        )
        coEvery {
            driveRepository.getDrivesForAnnualSummary("uid-1", any(), any())
        } returns listOf(
            drive(id = 1, vehicleId = 2, distanceKm = 4f, category = TripCategory.BUSINESS),
            drive(id = 2, vehicleId = null, distanceKm = 3f, category = TripCategory.BUSINESS),
            drive(id = 3, vehicleId = 1, distanceKm = 5f, category = TripCategory.BUSINESS),
        )

        val result = useCase(
            LogbookExportRequest(
                vehicleId = null,
                taxYear = 2026,
                format = ExportFormat.PDF,
                categories = setOf(TripCategory.BUSINESS),
            )
        )

        assertEquals("All vehicles", result.scopeLabel)
        assertEquals(listOf("Alpha Coupe", "Bravo", "Unassigned"), result.sections.map { it.label })
    }

    @Test
    fun `invoke keeps empty single vehicle section when selected categories exclude its trips`() = runTest {
        coEvery { vehicleRepository.getVehicleById(4) } returns vehicle(id = 4, name = "Family Van")
        coEvery { driveRepository.getDrivesForAnnualSummary("uid-1", any(), any()) } returns listOf(
            drive(id = 8, vehicleId = 4, distanceKm = 7.5f, category = TripCategory.PERSONAL),
        )

        val result = useCase(
            LogbookExportRequest(
                vehicleId = 4,
                taxYear = 2026,
                format = ExportFormat.CSV,
                categories = setOf(TripCategory.BUSINESS),
            )
        )

        assertEquals(1, result.sections.size)
        assertTrue(result.sections.single().trips.isEmpty())
        assertEquals(0.0, result.sections.single().totals.selectedKm, 0.001)
        assertEquals(7.5, result.sections.single().totals.totalKm, 0.001)
        assertEquals(0.0, result.sections.single().totals.selectedUsePercent, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invoke fails when requested vehicle does not exist`() = runTest {
        coEvery { vehicleRepository.getVehicleById(99) } returns null

        useCase(
            LogbookExportRequest(
                vehicleId = 99,
                taxYear = 2026,
                format = ExportFormat.PDF,
            )
        )
    }

    @Test
    fun `invoke defaults empty category filter to business and caches vehicle lookup by id`() = runTest {
        coEvery { vehicleRepository.getVehicleById(1) } returns vehicle(id = 1, name = "Cache Car")
        coEvery { driveRepository.getDrivesForAnnualSummary("uid-1", any(), any()) } returns listOf(
            drive(id = 1, vehicleId = 1, distanceKm = 2f, category = TripCategory.BUSINESS),
            drive(id = 2, vehicleId = 1, distanceKm = 3f, category = TripCategory.MEDICAL),
        )

        val result = useCase(
            LogbookExportRequest(
                vehicleId = null,
                taxYear = 2026,
                format = ExportFormat.CSV,
                categories = emptySet(),
            )
        )

        assertEquals(listOf(TripCategory.BUSINESS), result.categories)
        assertEquals(1, result.sections.size)
        assertEquals(1, result.sections.single().trips.size)
        coVerify(exactly = 1) { vehicleRepository.getVehicleById(1) }
    }

    private fun vehicle(
        id: Int,
        name: String,
        make: String? = null,
        model: String? = null,
    ) = VehicleEntity(
        id = id,
        ownerUid = "uid-1",
        displayName = name,
        make = make,
        model = model,
        fiscalYear = 2026,
    )

    private fun drive(
        id: Int,
        vehicleId: Int?,
        distanceKm: Float,
        category: TripCategory,
    ) = DriveEntity(
        id = id,
        startedAt = 1_700_000_000_000L + id,
        endedAt = 1_700_000_060_000L + id,
        startLocation = "Home",
        endLocation = "Office $id",
        distanceKm = distanceKm,
        vehicleId = vehicleId,
        driverUid = "uid-1",
        tripCategory = category,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        trackingSource = TrackingSource.AUTO_TRACKED,
    )
}
