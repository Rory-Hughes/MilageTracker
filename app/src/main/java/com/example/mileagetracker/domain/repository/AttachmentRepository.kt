package com.example.mileagetracker.domain.repository

import android.net.Uri
import com.example.mileagetracker.domain.model.TripAttachment
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for trip receipt/attachment operations.
 *
 * ## Storage model
 * Implementations are responsible for copying the picked file from its
 * source [Uri] into app-internal storage (`filesDir/attachments/`) before
 * persisting metadata, so the attachment survives the source URI being
 * revoked (e.g. after the picker Activity closes on older Android versions).
 *
 * ## Immutability
 * Once inserted, an attachment row is immutable.  Replacing a file is
 * modelled as [deleteAttachment] + [addAttachment].
 */
interface AttachmentRepository {

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Copy the file at [sourceUri] into app-internal storage, then insert a
     * [TripAttachment] metadata row.
     *
     * @param tripId    The drive trip this attachment belongs to.
     * @param sourceUri The content or file URI returned by the picker / camera.
     * @param mimeType  MIME type of the source file.
     * @param fileName  Display name to store (resolved from the content resolver
     *                  by the caller or use-case before calling here).
     * @return The fully-persisted [TripAttachment] including its assigned [TripAttachment.id].
     * @throws IllegalArgumentException if [mimeType] is blank or [tripId] is invalid.
     */
    suspend fun addAttachment(
        tripId: Int,
        sourceUri: Uri,
        mimeType: String,
        fileName: String,
    ): TripAttachment

    /**
     * Delete the attachment row and remove the copied file from internal storage.
     *
     * This is a no-op if the row no longer exists.  File deletion failures are
     * logged but do not surface as exceptions — the row is always removed from
     * the database regardless of file system state.
     *
     * @param attachment The [TripAttachment] to remove.
     */
    suspend fun deleteAttachment(attachment: TripAttachment)

    /**
     * Delete all attachments for a trip (used on drive hard-delete / GDPR erasure).
     * Files in internal storage are also removed.
     *
     * @param tripId The trip whose attachments should all be removed.
     */
    suspend fun deleteAllAttachmentsForTrip(tripId: Int)

    // ───────────────────────────────────────────────────────────────────────────
    // Reads
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Live [Flow] of all attachments for the given trip, ordered
     * by [TripAttachment.capturedAt] ascending.
     *
     * The trip-detail screen collects this to reactively update the
     * thumbnail strip when the user adds or removes attachments.
     */
    fun getAttachmentsForTrip(tripId: Int): Flow<List<TripAttachment>>

    /**
     * One-shot fetch of all attachments for a trip.
     * Used in export flows where a [Flow] collector would be wasteful.
     */
    suspend fun getAttachmentsForTripOnce(tripId: Int): List<TripAttachment>
}
