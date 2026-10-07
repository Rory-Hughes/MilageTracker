package com.example.mileagetracker.domain.usecase.vehicle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [SetDefaultVehicleUseCase] against a real in-memory Room DB.
 *
 * The repository method behind this use case performs a clear-and-promote flow
 * inside a single Room transaction. These tests verify the success path and,
 * more importantly, that failures do not leave the owner/year without a default.
 */
@RunWith(AndroidJUnit4::class)
class SetDefaultVehicleUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var useCase: SetDefaultVehicleUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase = SetDefaultVehicleUseCase(vehicleRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vehicle(
        ownerUid: String = "u1",
        displayName: String,
        fiscalYear: Int = 2026,
        isActive: Boolean = true,
        isDefault: Boolean = false,
    ) = VehicleEntity(
        ownerUid = ownerUid,
        displayName = displayName,
        fiscalYear = fiscalYear,
        isActive = isActive,
        isDefault = isDefault,
    )

    private suspend fun insertVehicle(
        ownerUid: String = "u1",
        displayName: String,
        fiscalYear: Int = 2026,
        isActive: Boolean = true,
        isDefault: Boolean = false,
    ): Int = vehicleRepo.saveVehicle(
        vehicle(
            ownerUid = ownerUid,
            displayName = displayName,
            fiscalYear = fiscalYear,
            isActive = isActive,
            isDefault = isDefault,
        )
    )

    @Test
    fun invoke_noExistingDefault_promotesTargetVehicle() = runTest {
        val targetId = insertVehicle(displayName = "Truck")

        useCase(targetId, ownerUid = "u1", fiscalYear = 2026)

        val saved = vehicleRepo.getVehicleById(targetId)!!
        assertTrue(saved.isDefault)
    }

    @Test
    fun invoke_existingDefault_promotesTargetAndClearsOnlySameOwnerYear() = runTest {
        val originalDefaultId = insertVehicle(displayName = "Original", isDefault = true)
        val targetId = insertVehicle(displayName = "Replacement", isDefault = false)
        val otherYearId = insertVehicle(displayName = "Prior Year", fiscalYear = 2025, isDefault = true)
        val otherOwnerId = insertVehicle(displayName = "Other Owner", ownerUid = "u2", isDefault = true)

        useCase(targetId, ownerUid = "u1", fiscalYear = 2026)

        val activeDefaults = vehicleRepo.getActiveVehicles("u1", 2026).first().filter { it.isDefault }
        assertEquals(1, activeDefaults.size)
        assertEquals(targetId, activeDefaults.single().id)
        assertFalse(vehicleRepo.getVehicleById(originalDefaultId)!!.isDefault)
        assertTrue(vehicleRepo.getVehicleById(otherYearId)!!.isDefault)
        assertTrue(vehicleRepo.getVehicleById(otherOwnerId)!!.isDefault)
    }

    @Test
    fun invoke_missingVehicle_throwsAndPreservesExistingDefault() = runTest {
        val defaultId = insertVehicle(displayName = "Original", isDefault = true)
        insertVehicle(displayName = "Secondary", isDefault = false)

        try {
            useCase(vehicleId = 9999, ownerUid = "u1", fiscalYear = 2026)
            fail("Expected IllegalArgumentException for missing vehicle")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        val activeDefaults = vehicleRepo.getActiveVehicles("u1", 2026).first().filter { it.isDefault }
        assertEquals(1, activeDefaults.size)
        assertEquals(defaultId, activeDefaults.single().id)
    }

    @Test
    fun invoke_inactiveVehicle_throwsAndPreservesExistingDefault() = runTest {
        val defaultId = insertVehicle(displayName = "Original", isDefault = true)
        val inactiveId = insertVehicle(displayName = "Inactive", isActive = false, isDefault = false)

        try {
            useCase(vehicleId = inactiveId, ownerUid = "u1", fiscalYear = 2026)
            fail("Expected IllegalArgumentException for inactive vehicle")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("inactive", ignoreCase = true))
        }

        val activeDefaults = vehicleRepo.getActiveVehicles("u1", 2026).first().filter { it.isDefault }
        assertEquals(1, activeDefaults.size)
        assertEquals(defaultId, activeDefaults.single().id)
        assertFalse(vehicleRepo.getVehicleById(inactiveId)!!.isDefault)
    }
}
