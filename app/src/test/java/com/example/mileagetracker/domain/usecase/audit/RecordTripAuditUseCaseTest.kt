package com.example.mileagetracker.domain.usecase.audit

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.domain.repository.AuditRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RecordTripAuditUseCaseTest {

    private lateinit var auditRepository: AuditRepository
    private lateinit var useCase: RecordTripAuditUseCase

    @Before
    fun setUp() {
        auditRepository = mockk()
        useCase = RecordTripAuditUseCase(auditRepository)
    }

    @Test
    fun `recordCreation delegates to repository`() = runTest {
        coEvery { auditRepository.recordEvent(any(), any(), any()) } returns Unit

        useCase.recordCreation(1, "user-1")

        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "user-1",
                eventType = AuditEventType.DRIVE_CREATED
            )
        }
    }

    @Test
    fun `recordFieldChange delegates to repository with all fields`() = runTest {
        coEvery { auditRepository.recordEvent(any(), any(), any(), any(), any(), any()) } returns Unit

        useCase.recordFieldChange(
            tripId = 1,
            actorUid = "user-1",
            eventType = AuditEventType.CATEGORY_CHANGED,
            fieldName = "trip_category",
            oldValue = "PERSONAL",
            newValue = "BUSINESS"
        )

        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "user-1",
                eventType = AuditEventType.CATEGORY_CHANGED,
                fieldName = "trip_category",
                oldValue = "PERSONAL",
                newValue = "BUSINESS"
            )
        }
    }

    @Test
    fun `recordEvent delegates to repository`() = runTest {
        coEvery { auditRepository.recordEvent(any(), any(), any()) } returns Unit

        useCase.recordEvent(1, "user-1", AuditEventType.ATTACHMENT_ADDED)

        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "user-1",
                eventType = AuditEventType.ATTACHMENT_ADDED
            )
        }
    }

    @Test
    fun `recordBatch delegates each row to repository`() = runTest {
        val rows = listOf(
            TripAuditEntity(tripId = 1, actorUid = "u1", eventType = AuditEventType.DRIVE_CREATED),
            TripAuditEntity(tripId = 1, actorUid = "u1", eventType = AuditEventType.CATEGORY_CHANGED)
        )
        coEvery { auditRepository.insertAuditRow(any()) } returns Unit

        useCase.recordBatch(rows)

        coVerify(exactly = 1) { auditRepository.insertAuditRow(rows[0]) }
        coVerify(exactly = 1) { auditRepository.insertAuditRow(rows[1]) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recordBatch throws on empty list`() = runTest {
        useCase.recordBatch(emptyList())
    }
}
