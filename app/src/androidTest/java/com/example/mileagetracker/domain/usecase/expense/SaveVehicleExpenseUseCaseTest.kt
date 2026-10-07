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

@RunWith(AndroidJUnit4::class)
class SaveVehicleExpenseUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: VehicleExpenseRepositoryImpl
    private lateinit var useCase: SaveVehicleExpenseUseCase
    private var vehicleId: Int = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = VehicleExpenseRepositoryImpl(db.vehicleExpenseDao())
        useCase = SaveVehicleExpenseUseCase(repository)

        vehicleId = db.vehicleDao().insertVehicle(
            VehicleEntity(
                ownerUid = "u1",
                displayName = "Test Car",
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
        id: Int = 0,
        vehicleId: Int = this.vehicleId,
        amountCad: Double = 50.0,
        date: Long = 1_000L,
    ) = VehicleExpense(
        id = id,
        vehicleId = vehicleId,
        category = ExpenseCategory.Fuel,
        amountCad = amountCad,
        date = date,
        vendorName = "Shell",
        isBusinessUse = true,
        createdAt = 1_000L,
    )

    @Test
    fun invoke_newExpense_persistsAndReturnsAssignedId() = runTest {
        val result = useCase(expense())

        assertTrue(result is SaveVehicleExpenseUseCase.Result.Success)
        val saved = (result as SaveVehicleExpenseUseCase.Result.Success).expense
        assertTrue(saved.id > 0)

        val stored = repository.getById(saved.id)!!
        assertEquals(vehicleId, stored.vehicleId)
        assertEquals(50.0, stored.amountCad, 0.001)
        assertEquals("Shell", stored.vendorName)
    }

    @Test
    fun invoke_existingExpense_updatesStoredRow() = runTest {
        val inserted = repository.save(expense())

        val result = useCase(inserted.copy(amountCad = 99.0, vendorName = "Esso"))

        assertTrue(result is SaveVehicleExpenseUseCase.Result.Success)
        val stored = repository.getById(inserted.id)!!
        assertEquals(99.0, stored.amountCad, 0.001)
        assertEquals("Esso", stored.vendorName)
    }

    @Test
    fun invoke_zeroAmount_returnsValidationError_andWritesNothing() = runTest {
        val result = useCase(expense(amountCad = 0.0))

        assertTrue(result is SaveVehicleExpenseUseCase.Result.ValidationError)
        assertEquals(
            "Amount must be greater than zero.",
            (result as SaveVehicleExpenseUseCase.Result.ValidationError).reason,
        )
        assertTrue(repository.observeForVehicle(vehicleId).first().isEmpty())
    }

    @Test
    fun invoke_invalidVehicleOrDate_returnsValidationError_andWritesNothing() = runTest {
        val badVehicle = useCase(expense(vehicleId = 0))
        val badDate = useCase(expense(date = 0L))

        assertTrue(badVehicle is SaveVehicleExpenseUseCase.Result.ValidationError)
        assertTrue(badDate is SaveVehicleExpenseUseCase.Result.ValidationError)
        assertTrue(repository.observeForVehicle(vehicleId).first().isEmpty())
    }
}
