package com.example.mileagetracker.data.repository

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.domain.model.TripAttachment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository integration tests for [AttachmentRepositoryImpl] against a real
 * (in-memory) SQLite database.
 *
 * ## Scope
 * [AttachmentRepositoryImpl.addAttachment] copies a file from a
 * `ContentResolver` URI into app-internal storage before inserting the DAO
 * row.  The file-copy path requires a real writable URI, so those tests are
 * excluded here.  This class covers:
 *
 * - **Validation guards** (fired *before* any ContentResolver call):
 *   - `require(mimeType.isNotBlank())` throws [IllegalArgumentException].
 *   - `require(tripId > 0)` throws [IllegalArgumentException] for 0 and −1.
 *
 * - **Read operations** backed by direct DAO inserts (no ContentResolver):
 *   - [getAttachmentsForTrip] returns a mapped [TripAttachment] list via Flow.
 *   - [getAttachmentsForTripOnce] returns the same list as a one-shot call.
 *   - Both are scoped to the requested `tripId`.
 *
 * - **Delete operations** — the [deleteFile] helper is silent on failure, so
 *   deleting an attachment whose URI points to a non-existent file succeeds
 *   without throwing:
 *   - [deleteAttachment] removes the DAO row.
 *   - [deleteAllAttachmentsForTrip] removes all rows for the given trip.
 */
@RunWith(AndroidJUnit4::class)
class AttachmentRepositoryImplTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: AttachmentRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = AttachmentRepositoryImpl(
            context       = ApplicationProvider.getApplicationContext(),
            attachmentDao = db.tripAttachmentDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private suspend fun insertEntity(
        tripId: Int = 1,
        uri: String = "file:///fake/attachment.jpg",
        mimeType: String = "image/jpeg",
        fileName: String = "receipt.jpg",
        createdAt: Long = 1_000L,
    ): Int {
        return db.tripAttachmentDao().insertAttachment(
            TripAttachmentEntity(
                tripId    = tripId,
                uri       = uri,
                mimeType  = mimeType,
                fileName  = fileName,
                createdAt = createdAt,
            )
        ).toInt()
    }

    // ── addAttachment: validation guards ──────────────────────────────────────

    @Test
    fun addAttachment_blankMimeType_throwsIllegalArgument() = runTest {
        try {
            repository.addAttachment(
                tripId    = 1,
                sourceUri = Uri.EMPTY,
                mimeType  = "   ",
                fileName  = "test.jpg",
            )
            fail("Expected IllegalArgumentException for blank mimeType")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Message should mention mimeType",
                e.message!!.contains("mimeType", ignoreCase = true),
            )
        }
    }

    @Test
    fun addAttachment_emptyMimeType_throwsIllegalArgument() = runTest {
        val error = try {
            repository.addAttachment(
                tripId    = 1,
                sourceUri = Uri.EMPTY,
                mimeType  = "",
                fileName  = "test.jpg",
            )
            fail("Expected IllegalArgumentException for empty mimeType")
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertNotNull(error)
    }

    @Test
    fun addAttachment_tripIdZero_throwsIllegalArgument() = runTest {
        try {
            repository.addAttachment(
                tripId    = 0,
                sourceUri = Uri.EMPTY,
                mimeType  = "image/jpeg",
                fileName  = "test.jpg",
            )
            fail("Expected IllegalArgumentException for tripId = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Message should mention tripId",
                e.message!!.contains("tripId", ignoreCase = true),
            )
        }
    }

    @Test
    fun addAttachment_negativeTripId_throwsIllegalArgument() = runTest {
        val error = try {
            repository.addAttachment(
                tripId    = -1,
                sourceUri = Uri.EMPTY,
                mimeType  = "image/jpeg",
                fileName  = "test.jpg",
            )
            fail("Expected IllegalArgumentException for negative tripId")
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertNotNull(error)
    }

    // ── getAttachmentsForTrip (Flow) ──────────────────────────────────────────

    @Test
    fun getAttachmentsForTrip_emitsEmptyList_whenNoAttachments() = runTest {
        assertTrue(repository.getAttachmentsForTrip(tripId = 1).first().isEmpty())
    }

    @Test
    fun getAttachmentsForTrip_emitsMappedDomainObjects() = runTest {
        insertEntity(tripId = 1, mimeType = "image/jpeg", fileName = "receipt.jpg", createdAt = 1_000L)

        val list = repository.getAttachmentsForTrip(tripId = 1).first()
        assertEquals(1, list.size)
        val attachment = list.single()
        assertEquals(1, attachment.tripId)
        assertEquals("image/jpeg", attachment.mimeType)
        assertEquals("receipt.jpg", attachment.fileName)
        assertEquals(1_000L, attachment.capturedAt)
        assertTrue(attachment.id > 0)
    }

    @Test
    fun getAttachmentsForTrip_scopedToTripId_doesNotReturnOtherTripsAttachments() = runTest {
        insertEntity(tripId = 1)
        insertEntity(tripId = 2)
        insertEntity(tripId = 1)

        val trip1List = repository.getAttachmentsForTrip(tripId = 1).first()
        assertEquals(2, trip1List.size)
        assertTrue(trip1List.all { it.tripId == 1 })

        val trip2List = repository.getAttachmentsForTrip(tripId = 2).first()
        assertEquals(1, trip2List.size)
        assertEquals(2, trip2List.single().tripId)
    }

    // ── getAttachmentsForTripOnce ─────────────────────────────────────────────

    @Test
    fun getAttachmentsForTripOnce_returnsAllAttachmentsForTrip() = runTest {
        insertEntity(tripId = 5, fileName = "a.pdf")
        insertEntity(tripId = 5, fileName = "b.jpg")
        insertEntity(tripId = 9, fileName = "other.jpg")

        val list = repository.getAttachmentsForTripOnce(tripId = 5)
        assertEquals(2, list.size)
        assertTrue(list.all { it.tripId == 5 })
    }

    @Test
    fun getAttachmentsForTripOnce_returnsEmpty_forTripWithNoAttachments() = runTest {
        assertTrue(repository.getAttachmentsForTripOnce(tripId = 99).isEmpty())
    }

    // ── deleteAttachment ──────────────────────────────────────────────────────

    @Test
    fun deleteAttachment_removesRow_getAttachmentsEmitsEmpty() = runTest {
        val rowId = insertEntity(tripId = 3)

        // Build a TripAttachment domain object to pass to deleteAttachment.
        val attachment = TripAttachment(
            id         = rowId,
            tripId     = 3,
            fileUri    = "file:///non_existent_file.jpg", // deleteFile is silent on failure
            mimeType   = "image/jpeg",
            fileName   = "receipt.jpg",
            capturedAt = 1_000L,
        )

        repository.deleteAttachment(attachment)

        assertTrue(repository.getAttachmentsForTrip(tripId = 3).first().isEmpty())
    }

    @Test
    fun deleteAttachment_nonExistentFile_doesNotThrow() = runTest {
        val rowId = insertEntity(tripId = 4, uri = "file:///path/that/does/not/exist/file.jpg")
        val attachment = TripAttachment(
            id         = rowId,
            tripId     = 4,
            fileUri    = "file:///path/that/does/not/exist/file.jpg",
            mimeType   = "image/jpeg",
            fileName   = "missing.jpg",
            capturedAt = 1_000L,
        )

        // deleteFile swallows exceptions — must not propagate.
        repository.deleteAttachment(attachment) // no exception expected

        assertTrue(repository.getAttachmentsForTrip(tripId = 4).first().isEmpty())
    }

    // ── deleteAllAttachmentsForTrip ───────────────────────────────────────────

    @Test
    fun deleteAllAttachmentsForTrip_removesAllRowsForThatTrip() = runTest {
        insertEntity(tripId = 7, uri = "file:///a.jpg")
        insertEntity(tripId = 7, uri = "file:///b.jpg")
        insertEntity(tripId = 8, uri = "file:///c.jpg") // different trip — must survive

        repository.deleteAllAttachmentsForTrip(tripId = 7)

        assertTrue(repository.getAttachmentsForTrip(tripId = 7).first().isEmpty())
        assertEquals(1, repository.getAttachmentsForTrip(tripId = 8).first().size)
    }

    @Test
    fun deleteAllAttachmentsForTrip_noAttachments_isNoop() = runTest {
        repository.deleteAllAttachmentsForTrip(tripId = 99) // must not throw
    }

    // ── domain mapping: all fields ────────────────────────────────────────────

    @Test
    fun getAttachmentsForTripOnce_allFieldsMapCorrectly() = runTest {
        insertEntity(
            tripId    = 10,
            uri       = "file:///docs/scan.pdf",
            mimeType  = "application/pdf",
            fileName  = "scan.pdf",
            createdAt = 99_000L,
        )

        val attachment = repository.getAttachmentsForTripOnce(10).single()
        assertEquals(10, attachment.tripId)
        assertEquals("file:///docs/scan.pdf", attachment.fileUri)
        assertEquals("application/pdf", attachment.mimeType)
        assertEquals("scan.pdf", attachment.fileName)
        assertEquals(99_000L, attachment.capturedAt)
    }
}
