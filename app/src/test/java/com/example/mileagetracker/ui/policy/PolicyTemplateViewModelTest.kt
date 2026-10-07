package com.example.mileagetracker.ui.policy

import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.usecase.SavePolicyTemplateUseCase
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolicyTemplateViewModelTest {

    private lateinit var viewModel: PolicyTemplateViewModel
    private lateinit var repository: PolicyRepository
    private lateinit var savePolicyUseCase: SavePolicyTemplateUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        savePolicyUseCase = mockk()

        every { repository.observePolicies() } returns flowOf(emptyList())
        viewModel = PolicyTemplateViewModel(repository, savePolicyUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads policies`() = runTest {
        val policies = listOf(PolicyTemplate(id = "1", name = "Standard"))
        every { repository.observePolicies() } returns flowOf(policies)
        
        // Re-init
        viewModel = PolicyTemplateViewModel(repository, savePolicyUseCase)
        val collector = backgroundScope.launch { viewModel.policies.collect {} }
        advanceUntilIdle()
        
        assertEquals(policies, viewModel.policies.value)
        collector.cancel()
    }

    @Test
    fun `savePolicy calls use case`() = runTest {
        val policy = PolicyTemplate(id = "2", name = "New")
        coEvery { savePolicyUseCase(policy) } returns Unit
        
        viewModel.savePolicy(policy)
        advanceUntilIdle()
        
        coVerify { savePolicyUseCase(policy) }
    }

    @Test
    fun `deletePolicy calls repository`() = runTest {
        coEvery { repository.deletePolicy("3") } returns Unit
        
        viewModel.deletePolicy("3")
        advanceUntilIdle()
        
        coVerify { repository.deletePolicy("3") }
    }
}
