package com.example.mileagetracker.domain.usecase.audit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [RecordTripAuditUseCase] against a real in-memory Room DB.
 *
 * These verify the use case methods write the expected row shapes and that
 * recordBatch enforces its non-empty contract before any persistence occurs.
 */
@RunWith(AndroidJUnit4::class)
class RecordTripAuditUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var auditRepo: AuditRepositoryImpl
    private lateinit var useCase: RecordTripAuditUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        auditRepo = AuditRepositoryImpl(db.tripAuditDao())
        useCase = RecordTripAuditUseCase(auditRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordCreation_writesDriveCreatedRowWithoutDiffFields() = runTest {
        useCase.recordCreation(tripId = 1, actorUid = "user-1")

        val rows = auditRepo.getAuditTrailForTripOnce(1)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(AuditEventType.DRIVE_CREATED, row.eventType)
        assertEquals("user-1", row.actorUid)
        assertNull(row.fieldName)
        assertNull(row.oldValue)
        assertNull(row.newValue)
    }

    @Test
    fun recordFieldChange_writesExpectedDiffPayload() = runTest {
        useCase.recordFieldChange(
            tripId = 7,
            actorUid = "admin",
            eventType = AuditEventType.CATEGORY_CHANGED,
            fieldName = "trip_category",
            oldValue = "PERSONAL",
            newValue = "BUSINESS",
        )

        val row = auditRepo.getAuditTrailForTripOnce(7).single()
        assertEquals(AuditEventType.CATEGORY_CHANGED, row.eventType)
        assertEquals("trip_category", row.fieldName)
        assertEquals("PERSONAL", row.oldValue)
        assertEquals("BUSINESS", row.newValue)
    }

    @Test
    fun recordBatch_insertsAllRows() = runTest {
        val rows = listOf(
            TripAuditEntity(
                tripId = 9,
                actorUid = "u1",
                eventType = AuditEventType.DRIVE_CREATED,
                changedAt = 1_000L,
            ),
            TripAuditEntity(
                tripId = 9,
                actorUid = "u1",
                eventType = AuditEventType.NOTE_UPDATED,
                newValue = "new note",
                changedAt = 2_000L,
            ),
        )

        useCase.recordBatch(rows)

        val saved = auditRepo.getAuditTrailForTripOnce(9)
        assertEquals(2, saved.size)
        assertTrue(saved.any { it.eventType == AuditEventType.DRIVE_CREATED && it.changedAt == 1_000L })
        assertTrue(
            saved.any {
                it.eventType == AuditEventType.NOTE_UPDATED && it.newValue == "new note" && it.changedAt == 2_000L
            }
        )
    }

    @Test
    fun recordBatch_emptyList_throwsAndWritesNothing() = runTest {
        try {
            useCase.recordBatch(emptyList())
            fail("Expected IllegalArgumentException for empty batch")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        assertTrue(auditRepo.getAuditRowsByDateRange(0L, Long.MAX_VALUE).isEmpty())
    }
}
