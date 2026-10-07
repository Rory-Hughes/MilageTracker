package com.example.mileagetracker.repository

import com.example.mileagetracker.data.local.dao.VehicleExpenseDao
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import com.example.mileagetracker.data.repository.VehicleExpenseRepositoryImpl
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VehicleExpenseRepositoryImplTest {

    private lateinit var dao: VehicleExpenseDao
    private lateinit var repository: VehicleExpenseRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk()
        repository = VehicleExpenseRepositoryImpl(dao)
    }

    private fun testExpense(id: Int = 0) = VehicleExpense(
        id = id,
        vehicleId = 1,
        category = ExpenseCategory.Fuel,
        amountCad = 50.0,
        date = 1000L,
        vendorName = "Shell",
        notes = "Gas",
        receiptAttachmentUri = null,
        gstHstAmount = 2.5,
        isBusinessUse = true,
        businessUseAmount = 0.0,
        createdAt = 1000L
    )

    @Test
    fun `save delegates to dao and returns expense with id`() = runTest {
        val expense = testExpense()
        coEvery { dao.insert(any()) } returns 8L

        val result = repository.save(expense)

        assertEquals(8, result.id)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `update returns true when dao reports rows affected`() = runTest {
        val expense = testExpense(id = 1)
        coEvery { dao.update(any()) } returns 1

        val result = repository.update(expense)

        assertTrue(result)
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        coEvery { dao.deleteById(5) } returns Unit

        repository.deleteById(5)

        coVerify(exactly = 1) { dao.deleteById(5) }
    }

    @Test
    fun `getById maps dao result to domain`() = runTest {
        val entity = VehicleExpenseEntity(
            id = 10,
            vehicleId = 1,
            categoryKey = "Fuel",
            amountCad = 60.0,
            date = 2000L,
            vendorName = "Esso",
            notes = "More gas",
            receiptAttachmentUri = null,
            gstHstAmount = 3.0,
            isBusinessUse = true,
            createdAt = 2000L
        )
        coEvery { dao.getById(10) } returns entity

        val result = repository.getById(10)

        assertEquals(10, result?.id)
        assertEquals(ExpenseCategory.Fuel, result?.category)
    }

    @Test
    fun `observeForVehicleInYear maps flow results`() = runTest {
        val entity = VehicleExpenseEntity(
            id = 1,
            vehicleId = 1,
            categoryKey = "Fuel",
            amountCad = 50.0,
            date = 1000L,
            vendorName = "Shell",
            notes = "Gas",
            receiptAttachmentUri = null,
            gstHstAmount = 2.5,
            isBusinessUse = true,
            createdAt = 1000L
        )
        every { dao.getForVehicleInYear(1, 0L, 2000L) } returns flowOf(listOf(entity))

        val result = repository.observeForVehicleInYear(1, 0L, 2000L).first()

        assertEquals(1, result.size)
        assertEquals(ExpenseCategory.Fuel, result[0].category)
    }

    @Test
    fun `getTotalForVehicleAndYear delegates to dao`() = runTest {
        coEvery { dao.getTotalForVehicleAndYear(1, 0L, 1000L) } returns 123.45

        val result = repository.getTotalForVehicleAndYear(1, 0L, 1000L)

        assertEquals(123.45, result, 0.001)
    }
}
