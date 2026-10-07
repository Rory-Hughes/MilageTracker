package com.example.mileagetracker.ui.expense

import androidx.lifecycle.SavedStateHandle
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import com.example.mileagetracker.domain.usecase.expense.GetVehicleExpensesUseCase
import com.example.mileagetracker.domain.usecase.expense.SaveVehicleExpenseUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleExpenseViewModelTest {

    private lateinit var viewModel: VehicleExpenseViewModel
    private lateinit var saveUseCase: SaveVehicleExpenseUseCase
    private lateinit var getUseCase: GetVehicleExpensesUseCase
    private lateinit var repository: VehicleExpenseRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        saveUseCase = mockk()
        getUseCase = mockk()
        repository = mockk()

        every { getUseCase(any(), any(), any()) } returns flowOf(emptyList())
        
        val savedStateHandle = SavedStateHandle(mapOf("vehicleId" to 10))
        viewModel = VehicleExpenseViewModel(savedStateHandle, saveUseCase, getUseCase, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct vehicleId`() {
        assertEquals(10, viewModel.uiState.value.vehicleId)
        assertEquals(0.0, viewModel.businessPercent.value, 0.0)
    }

    @Test
    fun `saveExpense success resets form and calls callback`() = runTest {
        val expense = VehicleExpense(
            id = 0, vehicleId = 10, category = ExpenseCategory.Fuel,
            amountCad = 50.0, date = 1000L, isBusinessUse = true, createdAt = 0L
        )
        coEvery { saveUseCase(any()) } returns SaveVehicleExpenseUseCase.Result.Success(expense.copy(id = 1))

        var callbackCalled = false
        viewModel.onAmountChanged("50.00")
        viewModel.onDateChanged(1000L)
        
        viewModel.saveExpense { callbackCalled = true }
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertEquals("", viewModel.uiState.value.amountRaw)
    }

    @Test
    fun `deleteExpense delegates to repository`() = runTest {
        coEvery { repository.deleteById(5) } returns Unit
        viewModel.deleteExpense(5)
        advanceUntilIdle()
        coVerify { repository.deleteById(5) }
    }

    @Test
    fun `setBusinessPercent updates state and triggers getUseCase`() = runTest {
        val collector = backgroundScope.launch { viewModel.expenses.collect {} }
        viewModel.setBusinessPercent(75.0)
        assertEquals(75.0, viewModel.businessPercent.value, 0.0)
        
        advanceUntilIdle()
        coVerify { getUseCase(10, any(), 75.0) }
        collector.cancel()
    }
}
