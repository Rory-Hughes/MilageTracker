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
class GetVehiclesUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var vehicleRepository: VehicleRepositoryImpl
    private lateinit var useCase: GetVehiclesUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        vehicleRepository = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase = GetVehiclesUseCase(vehicleRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vehicle(
        displayName: String,
        ownerUid: String = "u1",
        fiscalYear: Int,
        isActive: Boolean = true,
    ) = VehicleEntity(
        ownerUid = ownerUid,
        displayName = displayName,
        fiscalYear = fiscalYear,
        isActive = isActive,
    )

    @Test
    fun invoke_returnsAllVehiclesForOwner_includingInactive_sortedByYearDescThenName() = runTest {
        vehicleRepository.saveVehicle(vehicle(displayName = "Zulu", fiscalYear = 2025))
        vehicleRepository.saveVehicle(vehicle(displayName = "Alpha", fiscalYear = 2026, isActive = false))
        vehicleRepository.saveVehicle(vehicle(displayName = "Bravo", fiscalYear = 2026))
        vehicleRepository.saveVehicle(vehicle(displayName = "OtherUser", ownerUid = "u2", fiscalYear = 2027))

        val vehicles = useCase("u1").first()

        assertEquals(3, vehicles.size)
        assertEquals(
            listOf("Alpha", "Bravo", "Zulu"),
            vehicles.map { it.displayName },
        )
        assertEquals(listOf(2026, 2026, 2025), vehicles.map { it.fiscalYear })
        assertTrue(vehicles.any { !it.isActive })
        assertTrue(vehicles.all { it.ownerUid == "u1" })
    }

    @Test
    fun invoke_returnsEmpty_whenOwnerHasNoVehicles() = runTest {
        vehicleRepository.saveVehicle(vehicle(displayName = "OtherUser", ownerUid = "u2", fiscalYear = 2026))

        assertTrue(useCase("u1").first().isEmpty())
    }
}
