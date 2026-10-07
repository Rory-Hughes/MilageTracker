package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository integration tests for [AuditRepositoryImpl] against a real
 * (in-memory) SQLite database.
 *
 * The primary value here is testing the **changedAt ownership** contract:
 * [AuditRepositoryImpl.recordEvent] must set [TripAuditEntity.changedAt] to
 * the wall-clock time at *insert*, not at the time the caller built the
 * request.  This is tested by bracketing the call with `currentTimeMillis()`
 * snapshots and asserting the stored timestamp falls within the window.
 *
 * Additional tests confirm:
 * - Optional fields (`fieldName`, `oldValue`, `newValue`) persist correctly.
 * - [DRIVE_CREATED] events leave all diff fields null.
 * - [insertAuditRow] accepts a caller-supplied entity and stores it verbatim.
 * - [getAuditTrailForTrip] and [getAuditTrailForTripOnce] scope to tripId.
 * - [getAuditRowsByDateRange] uses inclusive bounds.
 * - Multiple events for the same trip accumulate as separate rows.
 */
@RunWith(AndroidJUnit4::class)
class AuditRepositoryImplTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: AuditRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = AuditRepositoryImpl(db.tripAuditDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── recordEvent: changedAt set at insert time ──────────────────────────────

    @Test
    fun recordEvent_setsChangedAt_toCurrentTimeAtInsert() = runTest {
        val before = System.currentTimeMillis()

        repository.recordEvent(
            tripId    = 1,
            actorUid  = "u1",
            eventType = AuditEventType.DRIVE_CREATED,
        )

        val after = System.currentTimeMillis()

        val rows = repository.getAuditTrailForTripOnce(tripId = 1)
        assertEquals(1, rows.size)
        val changedAt = rows.single().changedAt
        assertTrue(
            "changedAt ($changedAt) must be within [$before, $after]",
            changedAt in before..after,
        )
    }

    @Test
    fun recordEvent_withAllOptionalFields_persistsCorrectly() = runTest {
        repository.recordEvent(
            tripId    = 42,
            actorUid  = "admin",
            eventType = AuditEventType.CATEGORY_CHANGED,
            fieldName = "trip_category",
            oldValue  = "UNCLASSIFIED",
            newValue  = "BUSINESS",
        )

        val rows = repository.getAuditTrailForTripOnce(42)
        assertEquals(1, rows.size)
        with(rows.single()) {
            assertEquals(42, tripId)
            assertEquals("admin", actorUid)
            assertEquals(AuditEventType.CATEGORY_CHANGED, eventType)
            assertEquals("trip_category", fieldName)
            assertEquals("UNCLASSIFIED", oldValue)
            assertEquals("BUSINESS", newValue)
        }
    }

    @Test
    fun recordEvent_driveCreated_leavesAllDiffFieldsNull() = runTest {
        repository.recordEvent(
            tripId    = 7,
            actorUid  = "u1",
            eventType = AuditEventType.DRIVE_CREATED,
        )

        val row = repository.getAuditTrailForTripOnce(7).single()
        assertNull(row.fieldName)
        assertNull(row.oldValue)
        assertNull(row.newValue)
    }

    // ── insertAuditRow: caller-supplied entity ─────────────────────────────────

    @Test
    fun insertAuditRow_persists_callerSuppliedEntityVerbatim() = runTest {
        val entity = TripAuditEntity(
            tripId    = 5,
            actorUid  = "svc",
            eventType = AuditEventType.MANUAL_ENTRY,
            changedAt = 1_234_567_890L,
        )

        repository.insertAuditRow(entity)

        val rows = repository.getAuditTrailForTripOnce(5)
        assertEquals(1, rows.size)
        assertEquals(1_234_567_890L, rows.single().changedAt)
        assertEquals(AuditEventType.MANUAL_ENTRY, rows.single().eventType)
        assertEquals("svc", rows.single().actorUid)
    }

    // ── getAuditTrailForTrip (Flow) ────────────────────────────────────────────

    @Test
    fun getAuditTrailForTrip_emitsOnlyRowsForThatTripId() = runTest {
        repository.recordEvent(tripId = 1, actorUid = "u1", eventType = AuditEventType.DRIVE_CREATED)
        repository.recordEvent(tripId = 2, actorUid = "u2", eventType = AuditEventType.MANUAL_ENTRY)
        repository.recordEvent(tripId = 1, actorUid = "u1", eventType = AuditEventType.CATEGORY_CHANGED)

        val trail = repository.getAuditTrailForTrip(1).first()
        assertEquals(2, trail.size)
        assertTrue(trail.all { it.tripId == 1 })
    }

    @Test
    fun getAuditTrailForTrip_emitsEmpty_forTripWithNoEvents() = runTest {
        assertTrue(repository.getAuditTrailForTrip(999).first().isEmpty())
    }

    // ── getAuditTrailForTripOnce ───────────────────────────────────────────────

    @Test
    fun getAuditTrailForTripOnce_scopesToTripId() = runTest {
        repository.recordEvent(tripId = 10, actorUid = "u1", eventType = AuditEventType.NOTE_UPDATED)
        repository.recordEvent(tripId = 20, actorUid = "u2", eventType = AuditEventType.PURPOSE_UPDATED)

        val rows = repository.getAuditTrailForTripOnce(10)
        assertEquals(1, rows.size)
        assertEquals(10, rows.single().tripId)
        assertEquals(AuditEventType.NOTE_UPDATED, rows.single().eventType)
    }

    // ── getAuditRowsByDateRange ────────────────────────────────────────────────

    @Test
    fun getAuditRowsByDateRange_returnsRowsWithinInclusiveBounds() = runTest {
        // Use insertAuditRow so we control changedAt precisely.
        repository.insertAuditRow(
            TripAuditEntity(
                tripId = 1,
                actorUid = "u",
                eventType = AuditEventType.DRIVE_CREATED,
                changedAt = 1_000L,
            ),
        )
        repository.insertAuditRow(
            TripAuditEntity(
                tripId = 2,
                actorUid = "u",
                eventType = AuditEventType.MANUAL_ENTRY,
                changedAt = 2_000L,
            ),
        )
        repository.insertAuditRow(
            TripAuditEntity(
                tripId = 3,
                actorUid = "u",
                eventType = AuditEventType.NOTE_UPDATED,
                changedAt = 3_001L,
            ),
        )

        val rows = repository.getAuditRowsByDateRange(fromMillis = 1_000L, toMillis = 3_000L)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.changedAt in 1_000L..3_000L })
    }

    @Test
    fun getAuditRowsByDateRange_excludesRowsOutsideBounds() = runTest {
        repository.insertAuditRow(
            TripAuditEntity(
                tripId = 1,
                actorUid = "u",
                eventType = AuditEventType.DRIVE_CREATED,
                changedAt = 500L,
            ),
        )
        repository.insertAuditRow(
            TripAuditEntity(
                tripId = 2,
                actorUid = "u",
                eventType = AuditEventType.MANUAL_ENTRY,
                changedAt = 3_500L,
            ),
        )

        val rows = repository.getAuditRowsByDateRange(fromMillis = 1_000L, toMillis = 3_000L)
        assertTrue(rows.isEmpty())
    }

    // ── append-only: multiple events for the same trip ────────────────────────

    @Test
    fun recordEvent_appendsMultipleRows_forSameTripId() = runTest {
        repository.recordEvent(tripId = 5, actorUid = "u", eventType = AuditEventType.DRIVE_CREATED)
        repository.recordEvent(tripId = 5, actorUid = "u", eventType = AuditEventType.VEHICLE_ASSIGNED, newValue = "3")
        repository.recordEvent(tripId = 5, actorUid = "u", eventType = AuditEventType.CATEGORY_CHANGED,
            oldValue = "PERSONAL", newValue = "BUSINESS")

        val trail = repository.getAuditTrailForTripOnce(5)
        assertEquals(3, trail.size)
        val types = trail.map { it.eventType }.toSet()
        assertTrue(types.contains(AuditEventType.DRIVE_CREATED))
        assertTrue(types.contains(AuditEventType.VEHICLE_ASSIGNED))
        assertTrue(types.contains(AuditEventType.CATEGORY_CHANGED))
    }
}
