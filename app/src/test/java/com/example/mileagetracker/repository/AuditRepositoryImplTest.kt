package com.example.mileagetracker.repository

import com.example.mileagetracker.data.local.dao.TripAuditDao
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuditRepositoryImplTest {

    private lateinit var tripAuditDao: TripAuditDao
    private lateinit var repository: AuditRepositoryImpl

    @Before
    fun setUp() {
        tripAuditDao = mockk()
        repository = AuditRepositoryImpl(tripAuditDao)
    }

    // ------ insertAuditRow -------------------------------------------------

    @Test
    fun `insertAuditRow delegates to dao exactly once`() = runTest {
        val row = TripAuditEntity(
            tripId = 1,
            actorUid = "user-1",
            eventType = AuditEventType.DRIVE_CREATED,
            changedAt = System.currentTimeMillis(),
        )
        coEvery { tripAuditDao.insertAuditRow(row) } returns 1L

        repository.insertAuditRow(row)

        coVerify(exactly = 1) { tripAuditDao.insertAuditRow(row) }
    }

    // ------ recordEvent ----------------------------------------------------

    @Test
    fun `recordEvent builds entity with correct tripId and eventType`() = runTest {
        val captured = slot<TripAuditEntity>()
        coEvery { tripAuditDao.insertAuditRow(capture(captured)) } returns 1L

        repository.recordEvent(
            tripId = 5,
            actorUid = "user-abc",
            eventType = AuditEventType.DRIVE_CREATED,
        )

        assertEquals(5, captured.captured.tripId)
        assertEquals("user-abc", captured.captured.actorUid)
        assertEquals(AuditEventType.DRIVE_CREATED, captured.captured.eventType)
    }

    @Test
    fun `recordEvent sets changedAt to current wall-clock time`() = runTest {
        val captured = slot<TripAuditEntity>()
        coEvery { tripAuditDao.insertAuditRow(capture(captured)) } returns 1L

        val before = System.currentTimeMillis()
        repository.recordEvent(
            tripId = 1,
            actorUid = "user-1",
            eventType = AuditEventType.DRIVE_CREATED,
        )
        val after = System.currentTimeMillis()

        assertTrue(captured.captured.changedAt in before..after)
    }

    @Test
    fun `recordEvent with field-change args persists old and new values`() = runTest {
        val captured = slot<TripAuditEntity>()
        coEvery { tripAuditDao.insertAuditRow(capture(captured)) } returns 1L

        repository.recordEvent(
            tripId = 3,
            actorUid = "user-1",
            eventType = AuditEventType.CATEGORY_CHANGED,
            fieldName = "trip_category",
            oldValue = "UNCLASSIFIED",
            newValue = "BUSINESS",
        )

        assertEquals("trip_category", captured.captured.fieldName)
        assertEquals("UNCLASSIFIED", captured.captured.oldValue)
        assertEquals("BUSINESS", captured.captured.newValue)
    }

    @Test
    fun `recordEvent without optional args leaves field values null`() = runTest {
        val captured = slot<TripAuditEntity>()
        coEvery { tripAuditDao.insertAuditRow(capture(captured)) } returns 1L

        repository.recordEvent(
            tripId = 2,
            actorUid = "user-1",
            eventType = AuditEventType.DRIVE_CREATED,
        )

        assertNull(captured.captured.fieldName)
        assertNull(captured.captured.oldValue)
        assertNull(captured.captured.newValue)
    }

    @Test
    fun `recordEvent with partial args persists values correctly`() = runTest {
        val captured = slot<TripAuditEntity>()
        coEvery { tripAuditDao.insertAuditRow(capture(captured)) } returns 1L

        repository.recordEvent(
            tripId = 4,
            actorUid = "user-1",
            eventType = AuditEventType.CATEGORY_CHANGED,
            newValue = "BUSINESS",
        )

        assertNull(captured.captured.fieldName)
        assertNull(captured.captured.oldValue)
        assertEquals("BUSINESS", captured.captured.newValue)
    }

    // ------ Read paths -----------------------------------------------------

    @Test
    fun `getAuditTrailForTripOnce returns dao result`() = runTest {
        val rows = listOf(
            TripAuditEntity(
                tripId = 10,
                actorUid = "user-1",
                eventType = AuditEventType.DRIVE_CREATED,
                changedAt = 5_000L,
            )
        )
        coEvery { tripAuditDao.getAuditRowsForTripOnce(10) } returns rows

        val result = repository.getAuditTrailForTripOnce(10)

        assertEquals(rows, result)
    }

    @Test
    fun `getAuditTrailForTrip delegates to dao`() {
        val expectedFlow = mockk<Flow<List<TripAuditEntity>>>()
        every { tripAuditDao.getAuditRowsForTrip(10) } returns expectedFlow

        val result = repository.getAuditTrailForTrip(10)

        assertEquals(expectedFlow, result)
        verify { tripAuditDao.getAuditRowsForTrip(10) }
    }

    @Test
    fun `getAuditRowsByDateRange delegates range to dao`() = runTest {
        coEvery { tripAuditDao.getAuditRowsByDateRange(100L, 200L) } returns emptyList()

        val result = repository.getAuditRowsByDateRange(100L, 200L)

        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { tripAuditDao.getAuditRowsByDateRange(100L, 200L) }
    }

    // ------ Error Handling -------------------------------------------------

    @Test(expected = RuntimeException::class)
    fun `insertAuditRow propagates exceptions from dao`() = runTest {
        val row = TripAuditEntity(
            tripId = 1,
            actorUid = "u",
            eventType = AuditEventType.DRIVE_CREATED,
            changedAt = 1L
        )
        coEvery { tripAuditDao.insertAuditRow(row) } throws RuntimeException("DB Error")

        repository.insertAuditRow(row)
    }

    @Test(expected = RuntimeException::class)
    fun `recordEvent propagates exceptions from dao`() = runTest {
        coEvery { tripAuditDao.insertAuditRow(any()) } throws RuntimeException("DB Error")

        repository.recordEvent(1, "u", AuditEventType.DRIVE_CREATED)
    }

    @Test(expected = RuntimeException::class)
    fun `getAuditTrailForTripOnce propagates exceptions from dao`() = runTest {
        coEvery { tripAuditDao.getAuditRowsForTripOnce(any()) } throws RuntimeException("DB Error")

        repository.getAuditTrailForTripOnce(10)
    }

    @Test(expected = RuntimeException::class)
    fun `getAuditRowsByDateRange propagates exceptions from dao`() = runTest {
        coEvery { tripAuditDao.getAuditRowsByDateRange(any(), any()) } throws RuntimeException("DB Error")

        repository.getAuditRowsByDateRange(100L, 200L)
    }
}
