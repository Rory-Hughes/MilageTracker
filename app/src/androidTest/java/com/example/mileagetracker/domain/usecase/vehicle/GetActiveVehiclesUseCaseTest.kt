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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetActiveVehiclesUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var vehicleRepository: VehicleRepositoryImpl
    private lateinit var useCase: GetActiveVehiclesUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        vehicleRepository = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase = GetActiveVehiclesUseCase(vehicleRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vehicle(
        displayName: String,
        ownerUid: String = "u1",
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

    @Test
    fun invoke_returnsOnlyActiveVehicles_forOwnerAndYear_defaultFirstThenName() = runTest {
        vehicleRepository.saveVehicle(vehicle(displayName = "Bravo", isDefault = false))
        vehicleRepository.saveVehicle(vehicle(displayName = "Alpha", isDefault = true))
        vehicleRepository.saveVehicle(vehicle(displayName = "Charlie", isActive = false))
        vehicleRepository.saveVehicle(vehicle(displayName = "Delta", fiscalYear = 2025))
        vehicleRepository.saveVehicle(vehicle(displayName = "Echo", ownerUid = "u2"))

        val vehicles = useCase("u1", 2026).first()

        assertEquals(2, vehicles.size)
        assertEquals(listOf("Alpha", "Bravo"), vehicles.map { it.displayName })
        assertTrue(vehicles.all { it.isActive })
        assertTrue(vehicles.all { it.ownerUid == "u1" && it.fiscalYear == 2026 })
    }

    @Test
    fun invoke_returnsEmpty_whenNoActiveVehiclesMatch() = runTest {
        vehicleRepository.saveVehicle(vehicle(displayName = "Inactive", isActive = false))
        vehicleRepository.saveVehicle(vehicle(displayName = "WrongYear", fiscalYear = 2025))

        assertTrue(useCase("u1", 2027).first().isEmpty())
    }
}
