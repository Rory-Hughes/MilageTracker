package com.example.mileagetracker.repository

import com.example.mileagetracker.data.local.dao.ReportHistoryDao
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import com.example.mileagetracker.data.repository.ReportHistoryRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ReportHistoryRepositoryImplTest {

    private lateinit var dao: ReportHistoryDao
    private lateinit var repository: ReportHistoryRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk()
        repository = ReportHistoryRepositoryImpl(dao)
    }

    private fun testReport(id: Int = 0) = ReportHistoryEntity(
        id = id,
        format = "PDF",
        year = 2025,
        vehicleId = 1,
        generatedAt = 1000L,
        fileUri = "file:///path/$id",
        fileSizeBytes = 1024L
    )

    @Test
    fun `save delegates to dao`() = runTest {
        val report = testReport()
        coEvery { dao.insert(report) } returns 7L

        val result = repository.save(report)

        assertEquals(7, result)
    }

    @Test
    fun `getAll returns flow from dao`() {
        val expected = flowOf(emptyList<ReportHistoryEntity>())
        every { dao.getAll() } returns expected

        val result = repository.getAll()

        assertEquals(expected, result)
    }

    @Test
    fun `getById delegates to dao`() = runTest {
        val report = testReport(id = 5)
        coEvery { dao.getById(5) } returns report

        val result = repository.getById(5)

        assertEquals(report, result)
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        coEvery { dao.deleteById(3) } returns Unit

        repository.deleteById(3)

        coVerify(exactly = 1) { dao.deleteById(3) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deleteOlderThan throws for recent threshold`() = runTest {
        val threshold = System.currentTimeMillis() - 1000L // Way too recent
        repository.deleteOlderThan(threshold)
    }

    @Test
    fun `deleteOlderThan delegates to dao for old threshold`() = runTest {
        val oldThreshold = System.currentTimeMillis() - (7L * 365 * 24 * 60 * 60 * 1000) // 7 years
        coEvery { dao.deleteOlderThan(oldThreshold) } returns Unit

        repository.deleteOlderThan(oldThreshold)

        coVerify(exactly = 1) { dao.deleteOlderThan(oldThreshold) }
    }
}
