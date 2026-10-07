package com.example.mileagetracker.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
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
 * Room integration tests for [TripAttachmentDao] against a real (in-memory) SQLite
 * database. These exercise all-field round-trip, chronological ordering, trip-id
 * scoping via both Flow and one-shot reads, the attachment-count query, per-id and
 * bulk deletion, and the ABORT conflict strategy that prevents silent metadata
 * overwrite.
 *
 * `trip_attachments` has no `@ForeignKey` on `trip_id` (intentionally deferred —
 * see entity KDoc), so inserts do not need parent drive rows.
 */
@RunWith(AndroidJUnit4::class)
class TripAttachmentDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: TripAttachmentDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.tripAttachmentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun attachment(
        id: Int = 0,
        tripId: Int = 1,
        uri: String = "content://photos/receipt.jpg",
        mimeType: String = "image/jpeg",
        fileName: String = "receipt.jpg",
        createdAt: Long = 1_000L,
    ) = TripAttachmentEntity(
        id = id,
        tripId = tripId,
        uri = uri,
        mimeType = mimeType,
        fileName = fileName,
        createdAt = createdAt,
    )

    // ── insert + getAttachmentById ─────────────────────────────────────────────

    @Test
    fun insertAttachment_thenGetById_roundTripsAllFields() = runTest {
        val id = dao.insertAttachment(
            attachment(
                tripId = 42,
                uri = "content://files/form.pdf",
                mimeType = "application/pdf",
                fileName = "expense_form.pdf",
                createdAt = 9_000L,
            ),
        ).toInt()

        val loaded = dao.getAttachmentById(id)!!
        assertEquals(42, loaded.tripId)
        assertEquals("content://files/form.pdf", loaded.uri)
        assertEquals("application/pdf", loaded.mimeType)
        assertEquals("expense_form.pdf", loaded.fileName)
        assertEquals(9_000L, loaded.createdAt)
    }

    @Test
    fun getAttachmentById_returnsNull_forUnknownId() = runTest {
        assertNull(dao.getAttachmentById(999))
    }

    // ── getAttachmentsForTripOnce + ordering ──────────────────────────────────

    @Test
    fun getAttachmentsForTripOnce_returnsEmpty_forUnknownTrip() = runTest {
        assertTrue(dao.getAttachmentsForTripOnce(999).isEmpty())
    }

    @Test
    fun getAttachmentsForTripOnce_ordersChronologicallyAscending() = runTest {
        dao.insertAttachment(attachment(tripId = 1, createdAt = 3_000L, fileName = "C"))
        dao.insertAttachment(attachment(tripId = 1, createdAt = 1_000L, fileName = "A"))
        dao.insertAttachment(attachment(tripId = 1, createdAt = 2_000L, fileName = "B"))

        val names = dao.getAttachmentsForTripOnce(1).map { it.fileName }
        assertEquals(listOf("A", "B", "C"), names)
    }

    // ── getAttachmentsForTrip (Flow) + trip-id scoping ────────────────────────

    @Test
    fun getAttachmentsForTrip_flowReturnsOnlyRequestedTrip_chronologically() = runTest {
        dao.insertAttachment(attachment(tripId = 1, createdAt = 2_000L, fileName = "B"))
        dao.insertAttachment(attachment(tripId = 1, createdAt = 1_000L, fileName = "A"))
        dao.insertAttachment(attachment(tripId = 2, createdAt = 500L,   fileName = "Other"))

        val trip1 = dao.getAttachmentsForTrip(1).first()
        assertEquals(2, trip1.size)
        assertTrue(trip1.all { it.tripId == 1 })
        assertEquals(listOf("A", "B"), trip1.map { it.fileName })
    }

    // ── getAttachmentCountForTrip ─────────────────────────────────────────────

    @Test
    fun getAttachmentCountForTrip_returnsZero_forTripWithNoAttachments() = runTest {
        assertEquals(0, dao.getAttachmentCountForTrip(1).first())
    }

    @Test
    fun getAttachmentCountForTrip_reflectsInsertAndDelete() = runTest {
        val id1 = dao.insertAttachment(attachment(tripId = 1, createdAt = 1_000L)).toInt()
        val id2 = dao.insertAttachment(attachment(tripId = 1, createdAt = 2_000L)).toInt()
        dao.insertAttachment(attachment(tripId = 2, createdAt = 3_000L)) // different trip, must not count

        assertEquals(2, dao.getAttachmentCountForTrip(1).first())

        dao.deleteAttachment(id1)
        assertEquals(1, dao.getAttachmentCountForTrip(1).first())
    }

    // ── deleteAttachment (by id) ──────────────────────────────────────────────

    @Test
    fun deleteAttachment_removesOnlyThatRow() = runTest {
        val id = dao.insertAttachment(attachment(tripId = 1, createdAt = 1_000L)).toInt()
        dao.insertAttachment(attachment(tripId = 1, createdAt = 2_000L))

        dao.deleteAttachment(id)

        assertEquals(1, dao.getAttachmentsForTripOnce(1).size)
    }

    // ── deleteAllAttachmentsForTrip ───────────────────────────────────────────

    @Test
    fun deleteAllAttachmentsForTrip_removesOnlyThatTripsAttachments() = runTest {
        dao.insertAttachment(attachment(tripId = 1, createdAt = 1_000L))
        dao.insertAttachment(attachment(tripId = 1, createdAt = 2_000L))
        dao.insertAttachment(attachment(tripId = 2, createdAt = 3_000L))

        dao.deleteAllAttachmentsForTrip(1)

        assertTrue(dao.getAttachmentsForTripOnce(1).isEmpty())
        assertEquals(1, dao.getAttachmentsForTripOnce(2).size)
    }

    // ── ABORT conflict strategy ───────────────────────────────────────────────

    @Test
    fun insertAttachment_withDuplicatePrimaryKey_aborts() = runTest {
        dao.insertAttachment(attachment(id = 77, uri = "content://a"))
        val error = try {
            dao.insertAttachment(attachment(id = 77, uri = "content://b"))
            null
        } catch (error: SQLiteConstraintException) {
            error
        }
        assertNotNull(error)
        // Original row is unchanged.
        assertEquals("content://a", dao.getAttachmentById(77)!!.uri)
    }
}
