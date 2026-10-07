package com.example.mileagetracker.domain.usecase.expense

import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveVehicleExpenseUseCaseTest {

    private lateinit var repository: VehicleExpenseRepository
    private lateinit var useCase: SaveVehicleExpenseUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = SaveVehicleExpenseUseCase(repository)
    }

    private fun testExpense(id: Int = 0) = VehicleExpense(
        id = id,
        vehicleId = 1,
        category = ExpenseCategory.Fuel,
        amountCad = 50.0,
        date = 1000L,
        vendorName = "Shell",
        notes = null,
        receiptAttachmentUri = null,
        gstHstAmount = null,
        isBusinessUse = true,
        businessUseAmount = 0.0,
        createdAt = 1000L
    )

    @Test
    fun `saves new expense and returns Success`() = runTest {
        val expense = testExpense(id = 0)
        coEvery { repository.save(expense) } returns expense.copy(id = 7)

        val result = useCase(expense)

        assertTrue(result is SaveVehicleExpenseUseCase.Result.Success)
        assertEquals(7, (result as SaveVehicleExpenseUseCase.Result.Success).expense.id)
    }

    @Test
    fun `updates existing expense and returns Success`() = runTest {
        val expense = testExpense(id = 10)
        coEvery { repository.update(expense) } returns true

        val result = useCase(expense)

        assertTrue(result is SaveVehicleExpenseUseCase.Result.Success)
        assertEquals(10, (result as SaveVehicleExpenseUseCase.Result.Success).expense.id)
        coVerify { repository.update(expense) }
    }

    @Test
    fun `returns ValidationError for zero amount`() = runTest {
        val result = useCase(testExpense().copy(amountCad = 0.0))
        assertTrue(result is SaveVehicleExpenseUseCase.Result.ValidationError)
        assertEquals(
            "Amount must be greater than zero.",
            (result as SaveVehicleExpenseUseCase.Result.ValidationError).reason,
        )
    }

    @Test
    fun `returns ValidationError for invalid vehicleId`() = runTest {
        val result = useCase(testExpense().copy(vehicleId = 0))
        assertTrue(result is SaveVehicleExpenseUseCase.Result.ValidationError)
    }
}
