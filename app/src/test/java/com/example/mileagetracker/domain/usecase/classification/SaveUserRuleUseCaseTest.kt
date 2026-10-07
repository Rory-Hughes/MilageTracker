package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.UserRule
import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SaveUserRuleUseCaseTest {

    private lateinit var repository: UserClassificationRuleRepository
    private lateinit var useCase: SaveUserRuleUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = SaveUserRuleUseCase(repository)
    }

    @Test
    fun `returns validation error when name is blank`() = runTest {
        val result = useCase(rule(name = ""))

        assertEquals(
            SaveUserRuleResult.ValidationError("Rule name must not be empty"),
            result,
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `returns validation error when keyword is blank`() = runTest {
        val result = useCase(rule(keyword = "   "))

        assertEquals(
            SaveUserRuleResult.ValidationError("Keyword must not be empty"),
            result,
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `returns validation error when confidence is below zero`() = runTest {
        val result = useCase(rule(confidence = -0.01f))

        assertEquals(
            SaveUserRuleResult.ValidationError("Confidence must be between 0 and 1"),
            result,
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `returns validation error when confidence is above one`() = runTest {
        val result = useCase(rule(confidence = 1.01f))

        assertEquals(
            SaveUserRuleResult.ValidationError("Confidence must be between 0 and 1"),
            result,
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `returns validation error when priority is not positive`() = runTest {
        val result = useCase(rule(priority = 0))

        assertEquals(
            SaveUserRuleResult.ValidationError("Priority must be a positive number"),
            result,
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `saves rule when validation passes`() = runTest {
        val input = rule()
        val saved = input.copy(id = 12)
        coEvery { repository.save(input) } returns saved

        val result = useCase(input)

        assertEquals(SaveUserRuleResult.Success(saved), result)
        coVerify(exactly = 1) { repository.save(input) }
    }

    private fun rule(
        id: Int = 0,
        name: String = "Client rule",
        keyword: String = "client",
        category: TripCategory = TripCategory.BUSINESS,
        confidence: Float = 0.75f,
        priority: Int = 50,
    ) = UserRule(
        id = id,
        name = name,
        keyword = keyword,
        category = category,
        confidence = confidence,
        priority = priority,
        isEnabled = true,
        createdAt = 1_700_000_000_000L,
    )
}
