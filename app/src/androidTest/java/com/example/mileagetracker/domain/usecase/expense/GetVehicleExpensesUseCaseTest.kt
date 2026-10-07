package com.example.mileagetracker.domain.usecase.expense

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.VehicleExpenseRepositoryImpl
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class GetVehicleExpensesUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: VehicleExpenseRepositoryImpl
    private lateinit var useCase: GetVehicleExpensesUseCase
    private var vehicleId: Int = 0
    private var otherVehicleId: Int = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = VehicleExpenseRepositoryImpl(db.vehicleExpenseDao())
        useCase = GetVehicleExpensesUseCase(repository)

        vehicleId = db.vehicleDao().insertVehicle(
            VehicleEntity(
                ownerUid = "u1",
                displayName = "Primary",
                fiscalYear = 2026,
                isActive = true,
            ),
        ).toInt()
        otherVehicleId = db.vehicleDao().insertVehicle(
            VehicleEntity(
                ownerUid = "u1",
                displayName = "Other",
                fiscalYear = 2026,
                isActive = true,
            ),
        ).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun expense(
        date: Long,
        amountCad: Double,
        category: ExpenseCategory,
        isBusinessUse: Boolean = true,
        vehicleId: Int = this.vehicleId,
    ) = VehicleExpense(
        vehicleId = vehicleId,
        category = category,
        amountCad = amountCad,
        date = date,
        vendorName = "Vendor",
        isBusinessUse = isBusinessUse,
        createdAt = 1_000L,
    )

    private fun utcDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

    @Test
    fun invoke_businessParkingAndTolls_useFullAmount() = runTest {
        repository.save(expense(utcDay(2026, 1, 10), 10.0, ExpenseCategory.Parking, isBusinessUse = true))
        repository.save(expense(utcDay(2026, 1, 11), 5.0, ExpenseCategory.Tolls, isBusinessUse = true))

        val result = useCase(vehicleId, businessPercent = 50.0).first()

        assertEquals(2, result.size)
        assertEquals(5.0, result[0].businessUseAmount, 0.001)
        assertEquals(10.0, result[1].businessUseAmount, 0.001)
        assertTrue(result.all { it.vehicleId == vehicleId })
    }

    @Test
    fun invoke_otherCategories_useClampedBusinessPercent() = runTest {
        repository.save(expense(utcDay(2026, 2, 1), 100.0, ExpenseCategory.Fuel))
        repository.save(expense(utcDay(2026, 2, 2), 20.0, ExpenseCategory.Parking, isBusinessUse = false))

        val result = useCase(vehicleId, businessPercent = 150.0).first()

        assertEquals(2, result.size)
        assertEquals(20.0, result[0].businessUseAmount, 0.001)
        assertEquals(100.0, result[1].businessUseAmount, 0.001)
    }

    @Test
    fun invoke_yearFilter_returnsOnlyRequestedYear_forVehicle() = runTest {
        repository.save(expense(utcDay(2025, 12, 31), 10.0, ExpenseCategory.Fuel))
        repository.save(expense(utcDay(2026, 1, 1), 20.0, ExpenseCategory.Fuel))
        repository.save(expense(utcDay(2026, 7, 1), 30.0, ExpenseCategory.Maintenance))
        repository.save(expense(utcDay(2027, 1, 1), 40.0, ExpenseCategory.Fuel))
        repository.save(expense(utcDay(2026, 8, 1), 999.0, ExpenseCategory.Fuel, vehicleId = otherVehicleId))

        val result = useCase(vehicleId, year = 2026, businessPercent = 50.0).first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.date in utcDay(2026, 1, 1)..utcDay(2026, 12, 31) + 86_399_000L })
        assertEquals(15.0, result[0].businessUseAmount, 0.001)
        assertEquals(10.0, result[1].businessUseAmount, 0.001)
    }
}
