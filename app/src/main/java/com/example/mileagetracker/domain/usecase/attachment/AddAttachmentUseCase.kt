package com.example.mileagetracker.domain.usecase.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.repository.AttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Copies a picked file into internal storage and persists its metadata
 * as a [TripAttachment] row.
 *
 * ## What this use-case does
 * 1. Resolves [fileName] and [mimeType] from the content resolver if the
 *    caller has not supplied them (both fall back to safe defaults).
 * 2. Delegates the actual copy + insert to [AttachmentRepository.addAttachment].
 * 3. Returns the persisted [TripAttachment] including its database-assigned [TripAttachment.id].
 *
 * ## Android version note
 * On Android 13+ the picker returns a content URI with `READ_MEDIA_IMAGES`
 * access.  On older versions `READ_EXTERNAL_STORAGE` is used.  Either way
 * the content resolver can open the stream within this use-case's lifecycle;
 * the repository copies the bytes out immediately so the external URI is
 * not stored anywhere.
 *
 * @param context              Application context for content resolver access.
 * @param attachmentRepository Repository that handles copy + persist.
 */
class AddAttachmentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentRepository: AttachmentRepository,
) {
    /**
     * @param tripId    The drive trip to attach this file to.
     * @param sourceUri The URI returned by the photo/gallery picker.
     * @param mimeType  MIME type — if blank, resolved from the content resolver.
     * @param fileName  Display name — if blank, resolved from the content resolver.
     * @return The saved [TripAttachment].
     */
    suspend operator fun invoke(
        tripId: Int,
        sourceUri: Uri,
        mimeType: String = "",
        fileName: String = "",
    ): TripAttachment {
        val resolvedMime = mimeType.ifBlank {
            context.contentResolver.getType(sourceUri) ?: "application/octet-stream"
        }
        val resolvedName = fileName.ifBlank { resolveFileName(sourceUri) }

        return attachmentRepository.addAttachment(
            tripId = tripId,
            sourceUri = sourceUri,
            mimeType = resolvedMime,
            fileName = resolvedName,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveFileName(uri: Uri): String {
        // Try content resolver display name first.
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        // Fall back to the last path segment.
        return uri.lastPathSegment ?: "attachment"
    }
}
