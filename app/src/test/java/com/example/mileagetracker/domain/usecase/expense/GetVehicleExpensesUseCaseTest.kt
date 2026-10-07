package com.example.mileagetracker.domain.usecase.expense

import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetVehicleExpensesUseCaseTest {

    private lateinit var repository: VehicleExpenseRepository
    private lateinit var useCase: GetVehicleExpensesUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = GetVehicleExpensesUseCase(repository)
    }

    private fun testExpense(category: ExpenseCategory, amount: Double, isBusiness: Boolean) = VehicleExpense(
        id = 1,
        vehicleId = 10,
        category = category,
        amountCad = amount,
        date = 1000L,
        vendorName = "Test",
        notes = null,
        receiptAttachmentUri = null,
        gstHstAmount = null,
        isBusinessUse = isBusiness,
        businessUseAmount = 0.0,
        createdAt = 1000L
    )

    @Test
    fun `injects full amount for business parking and tolls`() = runTest {
        val parking = testExpense(ExpenseCategory.Parking, 10.0, true)
        val tolls = testExpense(ExpenseCategory.Tolls, 5.0, true)
        
        every { repository.observeForVehicle(10) } returns flowOf(listOf(parking, tolls))

        val result = useCase(10, businessPercent = 50.0).first()

        assertEquals(10.0, result[0].businessUseAmount, 0.001)
        assertEquals(5.0, result[1].businessUseAmount, 0.001)
    }

    @Test
    fun `injects percentage for other categories`() = runTest {
        val fuel = testExpense(ExpenseCategory.Fuel, 100.0, true)
        
        every { repository.observeForVehicle(10) } returns flowOf(listOf(fuel))

        val result = useCase(10, businessPercent = 60.0).first()

        assertEquals(60.0, result[0].businessUseAmount, 0.001)
    }

    @Test
    fun `filters by year if provided`() = runTest {
        val fuel = testExpense(ExpenseCategory.Fuel, 100.0, true)
        
        // UseCase calculates year bounds internally.
        every { repository.observeForVehicleInYear(10, any(), any()) } returns flowOf(listOf(fuel))

        val result = useCase(10, year = 2025, businessPercent = 100.0).first()

        assertEquals(1, result.size)
        assertEquals(100.0, result[0].businessUseAmount, 0.001)
    }
}
