package com.example.mileagetracker.repository

import com.example.mileagetracker.data.local.dao.UserClassificationRuleDao
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import com.example.mileagetracker.data.repository.UserClassificationRuleRepositoryImpl
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.UserRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UserClassificationRuleRepositoryImplTest {

    private lateinit var dao: UserClassificationRuleDao
    private lateinit var repository: UserClassificationRuleRepositoryImpl
    private val currentUserProvider = object : CurrentUserProvider {
        override fun currentUser() = CurrentUser("u1", "u1@example.com", null, null)
    }

    @Before
    fun setUp() {
        dao = mockk()
        repository = UserClassificationRuleRepositoryImpl(dao, currentUserProvider)
    }

    private fun testRule(id: Int = 0) = UserRule(
        id = id,
        name = "Test Rule",
        keyword = "Work",
        category = TripCategory.BUSINESS,
        confidence = 0.8f,
        priority = 50,
        isEnabled = true,
        createdAt = 1000L
    )

    @Test
    fun `save inserts new rule and returns it with id`() = runTest {
        val rule = testRule(id = 0)
        coEvery { dao.upsert(any()) } returns 5L

        val result = repository.save(rule)

        assertEquals(5, result.id)
        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun `save updates existing rule and returns it with same id`() = runTest {
        val rule = testRule(id = 10)
        coEvery { dao.upsert(any()) } returns 10L

        val result = repository.save(rule)

        assertEquals(10, result.id)
    }

    @Test
    fun `observeAll maps dao entities to domain`() = runTest {
        val entity = UserClassificationRuleEntity(
            id = 1,
            ownerUid = "u1",
            name = "R1",
            keyword = "K1",
            category = "BUSINESS",
            confidence = 0.7f,
            priority = 10,
            isEnabled = true,
            createdAt = 1000L
        )
        every { dao.observeAll("u1") } returns flowOf(listOf(entity))

        val result = repository.observeAll().first()

        assertEquals(1, result.size)
        assertEquals("R1", result[0].name)
        assertEquals(TripCategory.BUSINESS, result[0].category)
    }

    @Test
    fun `getEnabledRules maps dao entities to domain`() = runTest {
        val entity = UserClassificationRuleEntity(
            id = 2,
            ownerUid = "u1",
            name = "R2",
            keyword = "K2",
            category = "PERSONAL",
            confidence = 0.9f,
            priority = 20,
            isEnabled = true,
            createdAt = 2000L
        )
        coEvery { dao.getEnabledRules("u1") } returns listOf(entity)

        val result = repository.getEnabledRules()

        assertEquals(1, result.size)
        assertEquals(TripCategory.PERSONAL, result[0].category)
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        coEvery { dao.deleteById("u1", 3) } returns Unit

        repository.deleteById(3)

        coVerify(exactly = 1) { dao.deleteById("u1", 3) }
    }

    @Test
    fun `mapper falls back to CUSTOM for unknown category`() = runTest {
        val entity = UserClassificationRuleEntity(
            id = 1,
            ownerUid = "u1",
            name = "R1",
            keyword = "K1",
            category = "UNKNOWN_CAT",
            confidence = 0.7f,
            priority = 10,
            isEnabled = true,
            createdAt = 1000L
        )
        every { dao.observeAll("u1") } returns flowOf(listOf(entity))

        val result = repository.observeAll().first()

        assertEquals(TripCategory.CUSTOM, result[0].category)
    }
}
