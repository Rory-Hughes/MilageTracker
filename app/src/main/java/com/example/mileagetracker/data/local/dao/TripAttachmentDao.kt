package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [TripAttachmentEntity].
 *
 * ## Storage contract reminder
 * This DAO manages **metadata only**.  Deleting a row here does NOT delete
 * the underlying file from the device.  Callers (use-cases in the domain
 * layer) are responsible for releasing the persistent URI permission and,
 * where appropriate, deleting the file from storage before or after calling
 * [deleteAttachment] or [deleteAllAttachmentsForTrip].
 *
 * ## No @Update
 * Attachment rows are effectively immutable after insert.  Replacing a file
 * is modelled as delete-then-insert, keeping the metadata history clean.
 */
@Dao
interface TripAttachmentDao {

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Insert attachment metadata after the file has been stored and a
     * persistent URI permission has been granted.
     *
     * Returns the new row’s `rowId` so the caller can confirm the insert
     * or immediately load the saved entity in tests.
     *
     * [OnConflictStrategy.ABORT] surfaces a duplicate-PK insert as an
     * exception rather than silently overwriting existing metadata.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachment(attachment: TripAttachmentEntity): Long

    /**
     * Delete the metadata row for a single attachment by its primary key.
     *
     * **Does not** delete the underlying file or release the URI permission.
     * The caller must handle file cleanup before or after this call.
     */
    @Query("DELETE FROM trip_attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Int)

    /**
     * Delete all attachment metadata rows for a given trip.
     *
     * Used when a drive is hard-deleted (GDPR erasure) or when the user
     * removes all attachments in bulk.  As with [deleteAttachment], file
     * cleanup is the caller’s responsibility.
     *
     * Returns the number of rows deleted so the caller can release the
     * correct number of URI permissions.
     */
    @Query("DELETE FROM trip_attachments WHERE trip_id = :tripId")
    suspend fun deleteAllAttachmentsForTrip(tripId: Int)

    // ───────────────────────────────────────────────────────────────────────────
    // Reads
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Fetch a single attachment row by its primary key.
     * Returns `null` if the row does not exist.
     *
     * Used by the attachment viewer to resolve metadata before opening the
     * file URI, and in tests to confirm an insert.
     */
    @Query("SELECT * FROM trip_attachments WHERE id = :id")
    suspend fun getAttachmentById(id: Int): TripAttachmentEntity?

    /**
     * All attachments for a trip, ordered by [TripAttachmentEntity.createdAt]
     * ascending so the list is always chronological.
     *
     * Returns a [Flow] so the attachment strip in the trip-detail screen
     * updates reactively when the user adds or removes an attachment.
     *
     * @param tripId the [TripAttachmentEntity.tripId] to fetch rows for.
     */
    @Query(
        """
        SELECT * FROM trip_attachments
        WHERE  trip_id = :tripId
        ORDER BY created_at ASC
    """
    )
    fun getAttachmentsForTrip(tripId: Int): Flow<List<TripAttachmentEntity>>

    /**
     * One-shot fetch of all attachments for a trip, ordered chronologically.
     * Use this in export flows or use-cases where a [Flow] collector would
     * be wasteful.
     *
     * @param tripId the trip to fetch attachments for.
     */
    @Query(
        """
        SELECT * FROM trip_attachments
        WHERE  trip_id = :tripId
        ORDER BY created_at ASC
    """
    )
    suspend fun getAttachmentsForTripOnce(tripId: Int): List<TripAttachmentEntity>

    /**
     * Count of attachments for a trip.
     *
     * Used by the trip list and dashboard to render an attachment badge
     * (e.g. a paperclip icon with count) without loading full metadata rows.
     * Returns `0` when no attachments exist.
     *
     * @param tripId the trip to count attachments for.
     */
    @Query("SELECT COUNT(*) FROM trip_attachments WHERE trip_id = :tripId")
    fun getAttachmentCountForTrip(tripId: Int): Flow<Int>
}
