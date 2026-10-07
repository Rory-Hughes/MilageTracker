package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.PolicyTemplateDao
import com.example.mileagetracker.data.local.entity.PolicyTemplateEntity
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
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

class PolicyRepositoryTest {

    private lateinit var dao: PolicyTemplateDao
    private lateinit var repository: PolicyRepository

    @Before
    fun setUp() {
        dao = mockk()
        repository = PolicyRepository(dao)
    }

    private fun testDomain() = PolicyTemplate(
        id = "1",
        name = "Test",
        rateMode = RateMode.CRA_STANDARD
    )

    private fun testEntity() = PolicyTemplateEntity(
        id = "1",
        name = "Test",
        rateMode = "CRA_STANDARD",
        customRateCentsPerKm = null,
        eligibleCategories = emptyList(),
        excludeCommute = true,
        requirePurpose = false,
        requireAttachment = false,
        maxDailyKm = null
    )

    @Test
    fun `savePolicy delegates to dao`() = runTest {
        coEvery { dao.insertOrReplace(any()) } returns Unit
        repository.savePolicy(testDomain())
        coVerify { dao.insertOrReplace(match { it.id == "1" }) }
    }

    @Test
    fun `observePolicies maps results`() = runTest {
        every { dao.getAll() } returns flowOf(listOf(testEntity()))
        val result = repository.observePolicies().first()
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun `getById returns domain model`() = runTest {
        coEvery { dao.getById("1") } returns testEntity()
        val result = repository.getById("1")
        assertEquals("1", result?.id)
    }

    @Test
    fun `deletePolicy delegates to dao`() = runTest {
        coEvery { dao.deleteById("1") } returns Unit
        repository.deletePolicy("1")
        coVerify { dao.deleteById("1") }
    }
}
