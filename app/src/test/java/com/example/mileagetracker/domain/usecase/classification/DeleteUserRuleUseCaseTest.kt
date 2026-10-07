package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeleteUserRuleUseCaseTest {

    private lateinit var repository: UserClassificationRuleRepository
    private lateinit var useCase: DeleteUserRuleUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = DeleteUserRuleUseCase(repository)
    }

    @Test
    fun `delegates delete to repository`() = runTest {
        coJustRun { repository.deleteById(44) }

        useCase(44)

        coVerify(exactly = 1) { repository.deleteById(44) }
    }
}
