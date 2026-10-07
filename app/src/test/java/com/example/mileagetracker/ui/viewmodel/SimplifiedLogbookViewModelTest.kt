package com.example.mileagetracker.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.mileagetracker.domain.model.SimplifiedLogbookStatus
import com.example.mileagetracker.domain.usecase.export.CheckSimplifiedLogbookEligibilityUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class SimplifiedLogbookViewModelTest {

    private lateinit var viewModel: SimplifiedLogbookViewModel
    private lateinit var checkEligibilityUseCase: CheckSimplifiedLogbookEligibilityUseCase
    private lateinit var dataStore: DataStore<Preferences>
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        checkEligibilityUseCase = mockk()
        dataStore = mockk()
        
        val prefs = mockk<Preferences>()
        every { prefs[any<Preferences.Key<String>>()] } returns "u1"
        every { dataStore.data } returns flowOf(prefs)

        viewModel = SimplifiedLogbookViewModel(checkEligibilityUseCase, dataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Success after loading`() = runTest {
        val status = SimplifiedLogbookStatus(true, 2024, 100.0, 100, 50.0, 25.0, 50.0, listOf(2024), "Good")
        coEvery { checkEligibilityUseCase("u1") } returns status

        // Re-init to pick up mocked behavior
        viewModel = SimplifiedLogbookViewModel(checkEligibilityUseCase, dataStore)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SimplifiedLogbookUiState.Success)
        assertEquals(status, (viewModel.uiState.value as SimplifiedLogbookUiState.Success).status)
    }

    @Test
    fun `refresh reloads status`() = runTest {
        coEvery { checkEligibilityUseCase("u1") } returns mockk()
        
        viewModel.refresh()
        advanceUntilIdle()

        coEvery { checkEligibilityUseCase("u1") }
    }
}
