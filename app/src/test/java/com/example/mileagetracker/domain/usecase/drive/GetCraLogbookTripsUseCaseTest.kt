package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class GetCraLogbookTripsUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase
    private lateinit var useCase: GetCraLogbookTripsUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        vehicleRepository = mockk()
        resolveDriveLocationsUseCase = mockk {
            every { requiresResolution(any()) } returns false
        }
        useCase = GetCraLogbookTripsUseCase(
            driveRepository,
            vehicleRepository,
            resolveDriveLocationsUseCase,
        )
    }

    private fun testDrive(
        id: Int,
        km: Float,
        category: TripCategory,
        startedAt: Long = 1000L * id,
        vehicleId: Int? = 1,
        purposeText: String? = null,
    ) = DriveEntity(
        id = id,
        startedAt = startedAt,
        endedAt = 2000L * id,
        startLocation = "A",
        endLocation = "B",
        distanceKm = km,
        vehicleId = vehicleId,
        driverUid = "u1",
        tripCategory = category,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        purposeText = purposeText,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1000L
    )

    private fun testVehicle(
        id: Int = 1,
        displayName: String = "My Truck",
        make: String? = "Ford",
        model: String? = "F150",
    ) = VehicleEntity(
        id = id,
        ownerUid = "u1",
        displayName = displayName,
        fiscalYear = 2025,
        make = make,
        model = model,
        year = 2022,
        ownershipType = VehicleOwnershipType.PERSONAL,
        isDefault = true,
        isActive = true,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `calculates summary correctly and maps entries`() = runTest {
        val drives = listOf(
            testDrive(1, 100f, TripCategory.BUSINESS),
            testDrive(2, 50f, TripCategory.PERSONAL)
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(1) } returns testVehicle()

        val (entries, summary) = useCase("u1", 2025)

        assertEquals(1, entries.size)
        assertEquals("My Truck", entries[0].vehicleName)
        assertEquals(100f, entries[0].businessKm)
        
        assertEquals(150f, summary.totalKm)
        assertEquals(100f, summary.businessKm)
        assertEquals(66.66f, summary.businessUsePercent, 0.1f)
    }

    @Test
    fun `default categories include only business entries`() = runTest {
        val drives = listOf(
            testDrive(1, 12f, TripCategory.BUSINESS),
            testDrive(2, 7f, TripCategory.MEDICAL),
            testDrive(3, 3f, TripCategory.PERSONAL),
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(any()) } returns testVehicle()

        val (entries, summary) = useCase("u1", 2026)

        assertEquals(1, entries.size)
        assertEquals(1, entries.single().tripId)
        assertEquals(22f, summary.totalKm)
        assertEquals(12f, summary.businessKm)
    }

    @Test
    fun `custom categories include all requested CRA eligible categories`() = runTest {
        val drives = listOf(
            testDrive(1, 10f, TripCategory.BUSINESS),
            testDrive(2, 5f, TripCategory.MEDICAL),
            testDrive(3, 2f, TripCategory.CHARITY),
            testDrive(4, 8f, TripCategory.PERSONAL),
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(any()) } returns testVehicle()

        val (entries, summary) = useCase(
            "u1",
            2026,
            categories = TripCategory.craEligibleCategories,
        )

        assertEquals(listOf(1, 2, 3), entries.map { it.tripId })
        assertEquals(25f, summary.totalKm)
        assertEquals(17f, summary.businessKm)
        assertEquals(68f, summary.businessUsePercent, 0.0001f)
    }

    @Test
    fun `entries are sorted chronologically oldest first`() = runTest {
        val drives = listOf(
            testDrive(10, 1f, TripCategory.BUSINESS, startedAt = 3_000L),
            testDrive(11, 1f, TripCategory.BUSINESS, startedAt = 1_000L),
            testDrive(12, 1f, TripCategory.BUSINESS, startedAt = 2_000L),
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(any()) } returns testVehicle()

        val (entries, _) = useCase("u1", 2026)

        assertEquals(listOf(11, 12, 10), entries.map { it.tripId })
    }

    @Test
    fun `entry category reflects the drive trip category`() = runTest {
        val drives = listOf(
            testDrive(1, 5f, TripCategory.MEDICAL, purposeText = "ignored free text"),
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(any()) } returns testVehicle()

        val (entries, _) = useCase(
            "u1",
            2026,
            categories = listOf(TripCategory.MEDICAL),
        )

        // The logbook now surfaces the category directly; purposeText is no
        // longer echoed into the row.
        assertEquals(TripCategory.MEDICAL, entries.single().category)
    }

    @Test
    fun `vehicle filter keeps only selected vehicle and summary carries vehicle id`() = runTest {
        val drives = listOf(
            testDrive(1, 10f, TripCategory.BUSINESS, vehicleId = 1),
            testDrive(2, 20f, TripCategory.BUSINESS, vehicleId = 2),
            testDrive(3, 30f, TripCategory.PERSONAL, vehicleId = 1),
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(1) } returns testVehicle(id = 1, displayName = "V1")

        val (entries, summary) = useCase("u1", 2026, vehicleId = 1)

        assertEquals(listOf(1), entries.map { it.tripId })
        assertEquals(40f, summary.totalKm)
        assertEquals(10f, summary.businessKm)
        assertEquals(1, summary.vehicleId)
    }

    @Test
    fun `blank display name falls back to make and model`() = runTest {
        val drives = listOf(testDrive(1, 4f, TripCategory.BUSINESS, vehicleId = 7))
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(7) } returns testVehicle(
            id = 7,
            displayName = "",
            make = "Toyota",
            model = "Corolla",
        )

        val (entries, _) = useCase("u1", 2026)

        assertEquals("Toyota Corolla", entries.single().vehicleName)
    }

    @Test
    fun `blank display and make model fall back to vehicle id label`() = runTest {
        val drives = listOf(testDrive(1, 4f, TripCategory.BUSINESS, vehicleId = 9))
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(9) } returns testVehicle(
            id = 9,
            displayName = "",
            make = "",
            model = "",
        )

        val (entries, _) = useCase("u1", 2026)

        assertEquals("Vehicle 9", entries.single().vehicleName)
    }

    @Test
    fun `missing vehicle lookup and null vehicle id share the same placeholder name`() = runTest {
        val drives = listOf(
            testDrive(1, 3f, TripCategory.BUSINESS, vehicleId = 7),
            testDrive(2, 2f, TripCategory.BUSINESS, vehicleId = null),
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(7) } returns null

        val (entries, _) = useCase("u1", 2026)

        assertEquals(2, entries.size)
        assertEquals(entries[0].vehicleName, entries[1].vehicleName)
        assertFalse(entries[0].vehicleName.isBlank())
    }

    @Test
    fun `vehicle lookup is cached per vehicle id`() = runTest {
        val drives = listOf(
            testDrive(1, 6f, TripCategory.BUSINESS, vehicleId = 1),
            testDrive(2, 7f, TripCategory.BUSINESS, vehicleId = 1),
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns drives
        coEvery { vehicleRepository.getVehicleById(1) } returns testVehicle(id = 1, displayName = "Cached")

        useCase("u1", 2026)

        coVerify(exactly = 1) { vehicleRepository.getVehicleById(1) }
    }
}
