package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SavePolicyTemplateUseCaseTest {

    private lateinit var repository: PolicyRepository
    private lateinit var useCase: SavePolicyTemplateUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = SavePolicyTemplateUseCase(repository)
    }

    @Test
    fun `saves new policy with generated id`() = runTest {
        val policy = PolicyTemplate(id = "", name = "Standard")
        coEvery { repository.savePolicy(any()) } returns Unit

        useCase(policy)

        coVerify { repository.savePolicy(match { it.id.isNotBlank() && it.name == "Standard" }) }
    }

    @Test
    fun `saves existing policy with same id`() = runTest {
        val policy = PolicyTemplate(id = "123", name = "Existing")
        coEvery { repository.savePolicy(any()) } returns Unit

        useCase(policy)

        coVerify { repository.savePolicy(match { it.id == "123" }) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on blank name`() = runTest {
        useCase(PolicyTemplate(id = "", name = "  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on custom mode without rate`() = runTest {
        useCase(PolicyTemplate(id = "", name = "Custom", rateMode = RateMode.CUSTOM, customRateCentsPerKm = null))
    }
}
