package com.example.mileagetracker.repository

import com.example.mileagetracker.data.local.dao.DriveDao
import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveRepositoryImplTest {

    private lateinit var driveDao: DriveDao
    private lateinit var routePointDao: RoutePointDao
    private lateinit var repository: DriveRepositoryImpl

    @Before
    fun setUp() {
        driveDao = mockk()
        routePointDao = mockk(relaxed = true)
        repository = DriveRepositoryImpl(driveDao, routePointDao)
    }

    private fun minimalDrive(
        id: Int = 0,
        driverUid: String = "user-1",
        vehicleId: Int? = null,
    ) = DriveEntity(
        id = id,
        startedAt = 1_000L,
        endedAt = 2_000L,
        startLocation = "123 Main St",
        endLocation = "456 Oak Ave",
        distanceKm = 12.5f,
        vehicleId = vehicleId,
        driverUid = driverUid,
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test
    fun `saveDrive returns inserted row id`() = runTest {
        val drive = minimalDrive()
        coEvery { driveDao.insertDrive(drive) } returns 42L

        val result = repository.saveDrive(drive)

        assertEquals(42, result)
        coVerify(exactly = 1) { driveDao.insertDrive(drive) }
    }

    @Test
    fun `saveDrive with first auto-increment id returns 1`() = runTest {
        val drive = minimalDrive()
        coEvery { driveDao.insertDrive(drive) } returns 1L

        assertEquals(1, repository.saveDrive(drive))
    }

    @Test
    fun `updateDrive stamps updatedAt before delegating to dao`() = runTest {
        val captured = slot<DriveEntity>()
        coEvery { driveDao.updateDrive(capture(captured)) } returns Unit

        val drive = minimalDrive(id = 5, driverUid = "user-1")
        val before = System.currentTimeMillis()
        repository.updateDrive(drive)
        val after = System.currentTimeMillis()

        coVerify(exactly = 1) { driveDao.updateDrive(any()) }
        assertTrue(captured.captured.updatedAt in before..after)
    }

    @Test
    fun `deleteDrive calls softDeleteDrive with current timestamp`() = runTest {
        val capturedId = slot<Int>()
        val capturedTs = slot<Long>()
        coEvery {
            driveDao.softDeleteDrive(capture(capturedId), capture(capturedTs))
        } returns Unit

        val before = System.currentTimeMillis()
        repository.deleteDrive(7)
        val after = System.currentTimeMillis()

        assertEquals(7, capturedId.captured)
        assertTrue(capturedTs.captured in before..after)
    }

    @Test
    fun `assignVehicleToDrive returns true and updates vehicleId`() = runTest {
        val existing = minimalDrive(id = 10, vehicleId = null)
        val updated = slot<DriveEntity>()

        coEvery { driveDao.getDriveById(10) } returns existing
        coEvery { driveDao.updateDrive(capture(updated)) } returns Unit

        val result = repository.assignVehicleToDrive(driveId = 10, vehicleId = 3)

        assertTrue(result)
        assertEquals(3, updated.captured.vehicleId)
    }

    @Test
    fun `assignVehicleToDrive returns false when drive does not exist`() = runTest {
        coEvery { driveDao.getDriveById(99) } returns null

        val result = repository.assignVehicleToDrive(driveId = 99, vehicleId = 1)

        assertFalse(result)
        coVerify(exactly = 0) { driveDao.updateDrive(any()) }
    }

    @Test
    fun `assignVehicleToDrive with null vehicleId clears assignment`() = runTest {
        val existing = minimalDrive(id = 11, vehicleId = 5)
        val updated = slot<DriveEntity>()

        coEvery { driveDao.getDriveById(11) } returns existing
        coEvery { driveDao.updateDrive(capture(updated)) } returns Unit

        val result = repository.assignVehicleToDrive(driveId = 11, vehicleId = null)

        assertTrue(result)
        assertNull(updated.captured.vehicleId)
    }

    @Test
    fun `assignVehicleToDrive updates updatedAt timestamp`() = runTest {
        val existing = minimalDrive(id = 12, vehicleId = null)
        val updated = slot<DriveEntity>()

        coEvery { driveDao.getDriveById(12) } returns existing
        coEvery { driveDao.updateDrive(capture(updated)) } returns Unit

        val before = System.currentTimeMillis()
        repository.assignVehicleToDrive(driveId = 12, vehicleId = 4)
        val after = System.currentTimeMillis()

        assertTrue(updated.captured.updatedAt in before..after)
    }

    // ───────────────────────────────────────────────────────────────────────────────
    // Read Path Tests
    // ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `getDriveById delegates to dao`() = runTest {
        val drive = minimalDrive(id = 88)
        coEvery { driveDao.getDriveById(88) } returns drive

        val result = repository.getDriveById(88)

        assertEquals(drive, result)
        coVerify(exactly = 1) { driveDao.getDriveById(88) }
    }

    @Test
    fun `getAllDrives delegates to dao`() {
        val expected = mockk<Flow<List<DriveEntity>>>()
        every { driveDao.getAllDrives("user-1") } returns expected

        val result = repository.getAllDrives("user-1")

        assertEquals(expected, result)
    }

    @Test
    fun `getUnclassifiedDrives delegates to dao`() {
        val expected = mockk<Flow<List<DriveEntity>>>()
        every { driveDao.getUnclassifiedDrives("user-1") } returns expected

        val result = repository.getUnclassifiedDrives("user-1")

        assertEquals(expected, result)
    }

    @Test
    fun `getDrivesByVehicle delegates to dao`() {
        val expected = mockk<Flow<List<DriveEntity>>>()
        every { driveDao.getDrivesByVehicle(123) } returns expected

        val result = repository.getDrivesByVehicle(123)

        assertEquals(expected, result)
    }

    @Test
    fun `getBusinessDrivesByDateRange delegates to dao`() {
        val expected = mockk<Flow<List<DriveEntity>>>()
        every { driveDao.getBusinessDrivesByDateRange("u1", 100L, 200L) } returns expected

        val result = repository.getBusinessDrivesByDateRange("u1", 100L, 200L)

        assertEquals(expected, result)
    }

    @Test
    fun `getDrivesForAnnualSummary delegates to dao`() = runTest {
        val expected = listOf(minimalDrive())
        coEvery { driveDao.getDrivesForAnnualSummary("u1", 0L, 1000L) } returns expected

        val result = repository.getDrivesForAnnualSummary("u1", 0L, 1000L)

        assertEquals(expected, result)
    }

    @Test
    fun `getYearToDateBusinessDistance delegates to dao`() {
        val expected = mockk<Flow<Float?>>()
        every { driveDao.getYearToDateBusinessDistance("u1", 0L, 1000L) } returns expected

        val result = repository.getYearToDateBusinessDistance("u1", 0L, 1000L)

        assertEquals(expected, result)
    }

    @Test
    fun `getDrivesInTimeWindow delegates to dao`() = runTest {
        val expected = listOf(minimalDrive())
        coEvery {
            driveDao.getDrivesInTimeWindow("u1", 100L, 200L, 9)
        } returns expected

        val result = repository.getDrivesInTimeWindow("u1", 100L, 200L, 9)

        assertEquals(expected, result)
    }

    @Test
    fun `getBusinessDrivesForVehicleInYear delegates to dao`() = runTest {
        val expected = listOf(minimalDrive())
        coEvery {
            driveDao.getBusinessDrivesForVehicleInYear(5, 100L, 200L)
        } returns expected

        val result = repository.getBusinessDrivesForVehicleInYear(5, 100L, 200L)

        assertEquals(expected, result)
    }

    @Test
    fun `getAllDrivesForVehicleInYear delegates to dao`() = runTest {
        val expected = listOf(minimalDrive())
        coEvery {
            driveDao.getAllDrivesForVehicleInYear(5, 100L, 200L)
        } returns expected

        val result = repository.getAllDrivesForVehicleInYear(5, 100L, 200L)

        assertEquals(expected, result)
    }

    @Test
    fun `getRoutePointsForDrive maps dao entities to domain`() = runTest {
        val entities = listOf(
            RoutePointEntity(
                id = 1,
                tripId = 10,
                lat = 1.0,
                lng = 2.0,
                altitudeMetres = null,
                accuracyMetres = null,
                speedKph = null,
                timestampMs = 1000L
            )
        )
        coEvery { routePointDao.getPointsForTripOnce(10L) } returns entities

        val result = repository.getRoutePointsForDrive(10)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals(10L, result[0].tripId)
        assertEquals(1.0, result[0].lat, 0.0)
    }
}
