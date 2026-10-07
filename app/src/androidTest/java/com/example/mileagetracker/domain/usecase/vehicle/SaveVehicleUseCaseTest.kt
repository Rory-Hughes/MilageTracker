package com.example.mileagetracker.domain.usecase.vehicle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import com.example.mileagetracker.domain.validation.VehicleValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [SaveVehicleUseCase] against a real in-memory Room DB.
 *
 * These verify the create/update branching and that validation failures do not
 * leave behind partially persisted rows.
 */
@RunWith(AndroidJUnit4::class)
class SaveVehicleUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var useCase: SaveVehicleUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase = SaveVehicleUseCase(vehicleRepo, VehicleValidator())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vehicle(
        id: Int = 0,
        displayName: String = "My Civic",
        fiscalYear: Int = 2026,
        ownerUid: String = "u1",
    ) = VehicleEntity(
        id = id,
        ownerUid = ownerUid,
        displayName = displayName,
        fiscalYear = fiscalYear,
    )

    @Test
    fun invoke_newVehicle_insertsAndReturnsGeneratedId() = runTest {
        val savedId = useCase(vehicle(displayName = "Toyota Camry"))

        assertTrue(savedId > 0)
        val rows = vehicleRepo.getActiveVehicles("u1", 2026).first()
        assertEquals(1, rows.size)
        assertEquals(savedId, rows.single().id)
        assertEquals("Toyota Camry", rows.single().displayName)
    }

    @Test
    fun invoke_existingVehicle_updatesRowAndReturnsSameId() = runTest {
        val savedId = useCase(vehicle(displayName = "Original"))

        val returnedId = useCase(
            vehicle(
                id = savedId,
                displayName = "Updated",
                fiscalYear = 2026,
                ownerUid = "u1",
            ),
        )

        assertEquals(savedId, returnedId)
        val rows = vehicleRepo.getActiveVehicles("u1", 2026).first()
        assertEquals(1, rows.size)
        assertEquals("Updated", rows.single().displayName)
    }

    @Test
    fun invoke_blankDisplayName_throwsAndDoesNotInsert() = runTest {
        try {
            useCase(vehicle(displayName = "   "))
            fail("Expected IllegalArgumentException for blank display name")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        assertTrue(vehicleRepo.getActiveVehicles("u1", 2026).first().isEmpty())
    }

    @Test
    fun invoke_blankOwnerUid_throwsAndDoesNotInsert() = runTest {
        try {
            useCase(vehicle(ownerUid = ""))
            fail("Expected IllegalArgumentException for blank owner UID")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        assertTrue(vehicleRepo.getAllVehiclesForUser("").first().isEmpty())
        assertTrue(vehicleRepo.getAllVehiclesForUser("u1").first().isEmpty())
    }
}
