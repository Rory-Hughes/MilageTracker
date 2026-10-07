package com.example.mileagetracker.data.local.dao

import android.database.sqlite.SQLiteConstraintException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [TripAuditDao] against a real (in-memory) SQLite
 * database. These exercise the actual SQL — chronological (oldest-first)
 * ordering, trip-id scoping, inclusive date-range windows, actor filtering, and
 * the `ABORT` conflict strategy that protects the append-only audit log.
 *
 * The audit table is append-only by contract (no `@Update`/`@Delete`), so these
 * tests focus on insert + the various read paths.
 */
@RunWith(AndroidJUnit4::class)
class TripAuditDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: TripAuditDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.tripAuditDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private fun auditRow(
        id: Int = 0,
        tripId: Int = 1,
        actorUid: String = "u1",
        eventType: AuditEventType = AuditEventType.CATEGORY_CHANGED,
        fieldName: String? = "trip_category",
        oldValue: String? = "UNCLASSIFIED",
        newValue: String? = "BUSINESS",
        changedAt: Long = 1_000L,
    ) = TripAuditEntity(
        id = id,
        tripId = tripId,
        actorUid = actorUid,
        eventType = eventType,
        fieldName = fieldName,
        oldValue = oldValue,
        newValue = newValue,
        changedAt = changedAt,
    )

    // ── insert + read-back ───────────────────────────────────────────────────

    @Test
    fun insert_thenGetOnce_roundTripsDiffFields() = runTest {
        dao.insertAuditRow(
            auditRow(
                eventType = AuditEventType.DISTANCE_UPDATED,
                fieldName = "distance_km",
                oldValue = "10.0",
                newValue = "15.5",
            ),
        )

        val loaded = dao.getAuditRowsForTripOnce(1).single()
        assertEquals(AuditEventType.DISTANCE_UPDATED, loaded.eventType)
        assertEquals("distance_km", loaded.fieldName)
        assertEquals("10.0", loaded.oldValue)
        assertEquals("15.5", loaded.newValue)
    }

    @Test
    fun insert_creationEvent_persistsNullDiffColumns() = runTest {
        // DRIVE_CREATED documents existence, not a field change → null diff columns.
        dao.insertAuditRow(
            auditRow(
                eventType = AuditEventType.DRIVE_CREATED,
                fieldName = null,
                oldValue = null,
                newValue = null,
            ),
        )

        val loaded = dao.getAuditRowsForTripOnce(1).single()
        assertEquals(AuditEventType.DRIVE_CREATED, loaded.eventType)
        assertNull(loaded.fieldName)
        assertNull(loaded.oldValue)
        assertNull(loaded.newValue)
    }

    @Test
    fun getAuditRowsForTripOnce_returnsEmpty_forUnknownTrip() = runTest {
        assertTrue(dao.getAuditRowsForTripOnce(999).isEmpty())
    }

    // ── per-trip ordering + scoping ──────────────────────────────────────────

    @Test
    fun getAuditRowsForTripOnce_ordersOldestFirst() = runTest {
        dao.insertAuditRow(auditRow(changedAt = 3_000L, newValue = "C"))
        dao.insertAuditRow(auditRow(changedAt = 1_000L, newValue = "A"))
        dao.insertAuditRow(auditRow(changedAt = 2_000L, newValue = "B"))

        val timeline = dao.getAuditRowsForTripOnce(1)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), timeline.map { it.changedAt })
        assertEquals(listOf("A", "B", "C"), timeline.map { it.newValue })
    }

    @Test
    fun getAuditRowsForTrip_flowReturnsOnlyRequestedTrip_chronologically() = runTest {
        dao.insertAuditRow(auditRow(tripId = 1, changedAt = 2_000L))
        dao.insertAuditRow(auditRow(tripId = 1, changedAt = 1_000L))
        dao.insertAuditRow(auditRow(tripId = 2, changedAt = 500L))

        val trip1 = dao.getAuditRowsForTrip(1).first()
        assertEquals(2, trip1.size)
        assertTrue(trip1.all { it.tripId == 1 })
        assertEquals(listOf(1_000L, 2_000L), trip1.map { it.changedAt }) // ASC
    }

    // ── date-range reads ─────────────────────────────────────────────────────

    @Test
    fun getAuditRowsByDateRange_appliesInclusiveBounds_acrossTrips_ordered() = runTest {
        dao.insertAuditRow(auditRow(tripId = 1, changedAt = 1_000L)) // lower bound, inclusive
        dao.insertAuditRow(auditRow(tripId = 2, changedAt = 2_000L)) // upper bound, inclusive
        dao.insertAuditRow(auditRow(tripId = 3, changedAt = 2_001L)) // just outside
        dao.insertAuditRow(auditRow(tripId = 4, changedAt = 999L))   // just outside

        val rows = dao.getAuditRowsByDateRange(fromMillis = 1_000L, toMillis = 2_000L)

        assertEquals(listOf(1_000L, 2_000L), rows.map { it.changedAt }) // ASC, spans trips
    }

    // ── actor reads ──────────────────────────────────────────────────────────

    @Test
    fun getAuditRowsByActor_filtersByActorWithinRange() = runTest {
        dao.insertAuditRow(auditRow(actorUid = "u1", changedAt = 1_200L))
        dao.insertAuditRow(auditRow(actorUid = "SYSTEM", changedAt = 1_300L)) // different actor
        dao.insertAuditRow(auditRow(actorUid = "u1", changedAt = 5_000L))     // out of range

        val rows = dao.getAuditRowsByActor("u1", fromMillis = 1_000L, toMillis = 2_000L)

        assertEquals(1, rows.size)
        assertEquals("u1", rows.single().actorUid)
        assertEquals(1_200L, rows.single().changedAt)
    }

    @Test
    fun getAuditRowsByActor_distinguishesSystemFromHumanEvents() = runTest {
        dao.insertAuditRow(auditRow(actorUid = "u1", changedAt = 1_100L))
        dao.insertAuditRow(auditRow(actorUid = "SYSTEM", changedAt = 1_200L))

        val systemRows = dao.getAuditRowsByActor("SYSTEM", fromMillis = 0L, toMillis = 10_000L)
        assertEquals(1, systemRows.size)
        assertEquals("SYSTEM", systemRows.single().actorUid)
    }

    // ── conflict strategy ────────────────────────────────────────────────────

    @Test
    fun insertAuditRow_withDuplicatePrimaryKey_aborts() = runTest {
        dao.insertAuditRow(auditRow(id = 42))
        val error = try {
            dao.insertAuditRow(auditRow(id = 42))
            null
        } catch (error: SQLiteConstraintException) {
            error
        }
        assertNotNull(error)
    }
}
