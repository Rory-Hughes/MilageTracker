package com.example.mileagetracker.repository

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.VehicleDao
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class VehicleRepositoryImplTest {

    private lateinit var vehicleDao: VehicleDao
    private lateinit var db: MileageDatabase
    private lateinit var repository: VehicleRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        vehicleDao = mockk()
        db = mockk()
        repository = VehicleRepositoryImpl(vehicleDao, db)

        coEvery { db.withTransaction(any<suspend () -> Unit>()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun testVehicle(
        id: Int = 0,
        ownerUid: String = "user-1",
        fiscalYear: Int = 2025,
        isDefault: Boolean = false,
        isActive: Boolean = true,
    ) = VehicleEntity(
        id = id,
        ownerUid = ownerUid,
        displayName = "Toyota Corolla",
        fiscalYear = fiscalYear,
        make = "Toyota",
        model = "Corolla",
        year = 2020,
        ownershipType = VehicleOwnershipType.PERSONAL,
        isDefault = isDefault,
        isActive = isActive,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test
    fun `saveVehicle returns inserted id`() = runTest {
        val vehicle = testVehicle()
        coEvery { vehicleDao.insertVehicle(vehicle) } returns 7L

        assertEquals(7, repository.saveVehicle(vehicle))
        coVerify(exactly = 1) { vehicleDao.insertVehicle(vehicle) }
    }

    @Test
    fun `updateVehicle stamps updatedAt before delegating`() = runTest {
        val captured = slot<VehicleEntity>()
        coEvery { vehicleDao.updateVehicle(capture(captured)) } returns Unit

        val before = System.currentTimeMillis()
        repository.updateVehicle(testVehicle(id = 3))
        val after = System.currentTimeMillis()

        assertTrue(captured.captured.updatedAt in before..after)
    }

    @Test
    fun `deactivateVehicle delegates id and current timestamp to dao`() = runTest {
        val capturedId = slot<Int>()
        val capturedTs = slot<Long>()
        coEvery {
            vehicleDao.deactivateVehicle(capture(capturedId), capture(capturedTs))
        } returns Unit

        val before = System.currentTimeMillis()
        repository.deactivateVehicle(9)
        val after = System.currentTimeMillis()

        assertEquals(9, capturedId.captured)
        assertTrue(capturedTs.captured in before..after)
    }

    @Test
    fun `setDefaultVehicle clears year then sets isDefault on target`() = runTest {
        val vehicle = testVehicle(id = 2, ownerUid = "user-1", fiscalYear = 2025)
        val captured = slot<VehicleEntity>()

        coEvery { vehicleDao.clearDefaultForYear("user-1", 2025, any()) } returns Unit
        coEvery { vehicleDao.getVehicleById(2) } returns vehicle
        coEvery { vehicleDao.updateVehicle(capture(captured)) } returns Unit

        repository.setDefaultVehicle(vehicleId = 2, ownerUid = "user-1", fiscalYear = 2025)

        coVerify(exactly = 1) { vehicleDao.clearDefaultForYear("user-1", 2025, any()) }
        assertTrue(captured.captured.isDefault)
        assertEquals(2, captured.captured.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setDefaultVehicle throws when vehicle not found`() = runTest {
        coEvery { vehicleDao.clearDefaultForYear(any(), any(), any()) } returns Unit
        coEvery { vehicleDao.getVehicleById(99) } returns null

        repository.setDefaultVehicle(vehicleId = 99, ownerUid = "user-1", fiscalYear = 2025)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setDefaultVehicle throws when vehicle is inactive`() = runTest {
        val inactive = testVehicle(id = 4, isActive = false)

        coEvery { vehicleDao.clearDefaultForYear(any(), any(), any()) } returns Unit
        coEvery { vehicleDao.getVehicleById(4) } returns inactive

        repository.setDefaultVehicle(vehicleId = 4, ownerUid = "user-1", fiscalYear = 2025)
    }

    @Test
    fun `deleteVehicle delegates to dao`() = runTest {
        val vehicle = testVehicle(id = 10)
        coEvery { vehicleDao.deleteVehicle(vehicle) } returns Unit

        repository.deleteVehicle(vehicle)

        coVerify(exactly = 1) { vehicleDao.deleteVehicle(vehicle) }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Read Paths
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `getVehicleById delegates to dao`() = runTest {
        val vehicle = testVehicle(id = 5)
        coEvery { vehicleDao.getVehicleById(5) } returns vehicle

        val result = repository.getVehicleById(5)

        assertEquals(vehicle, result)
    }

    @Test
    fun `getDefaultVehicle delegates to dao`() = runTest {
        val vehicle = testVehicle(id = 7, isDefault = true)
        coEvery { vehicleDao.getDefaultVehicle("u1", 2025) } returns vehicle

        val result = repository.getDefaultVehicle("u1", 2025)

        assertEquals(vehicle, result)
    }

    @Test
    fun `getTripCountForVehicle delegates to dao`() = runTest {
        coEvery { vehicleDao.countDrivesForVehicle(123) } returns 42

        val result = repository.getTripCountForVehicle(123)

        assertEquals(42, result)
    }

    @Test
    fun `getActiveVehicles returns flow from dao`() {
        val expected = mockk<Flow<List<VehicleEntity>>>()
        every { vehicleDao.getActiveVehicles("u1", 2025) } returns expected

        val result = repository.getActiveVehicles("u1", 2025)

        assertEquals(expected, result)
    }

    @Test
    fun `getAllVehiclesForUser returns flow from dao`() {
        val expected = mockk<Flow<List<VehicleEntity>>>()
        every { vehicleDao.getAllVehiclesForUser("u1") } returns expected

        val result = repository.getAllVehiclesForUser("u1")

        assertEquals(expected, result)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Error Handling
    // ───────────────────────────────────────────────────────────────────────────

    @Test(expected = RuntimeException::class)
    fun `saveVehicle propagates exceptions`() = runTest {
        coEvery { vehicleDao.insertVehicle(any()) } throws RuntimeException("DB error")
        repository.saveVehicle(testVehicle())
    }

    @Test(expected = RuntimeException::class)
    fun `updateVehicle propagates exceptions`() = runTest {
        coEvery { vehicleDao.updateVehicle(any()) } throws RuntimeException("DB error")
        repository.updateVehicle(testVehicle(id = 1))
    }

    @Test(expected = RuntimeException::class)
    fun `deactivateVehicle propagates exceptions`() = runTest {
        coEvery { vehicleDao.deactivateVehicle(any(), any()) } throws RuntimeException("DB error")
        repository.deactivateVehicle(1)
    }
}
