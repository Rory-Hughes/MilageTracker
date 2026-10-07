package com.example.mileagetracker.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.mileagetracker.data.local.dao.TripAttachmentDao
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.repository.AttachmentRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room + filesystem implementation of [AttachmentRepository].
 *
 * ## File storage strategy
 * On [addAttachment] the source [Uri] is copied into:
 * ```
 * <filesDir>/attachments/<tripId>/<timestamp>_<sanitisedFileName>
 * ```
 * This is app-internal storage — no `READ_EXTERNAL_STORAGE` permission is
 * required to read it, and the file survives the picker URI being revoked.
 *
 * ## Dispatcher policy
 * All DAO and IO operations run on [Dispatchers.IO].  Flow reads delegate
 * to Room which emits on its own IO executor.
 *
 * @param context         Application context for [filesDir] access.
 * @param attachmentDao   DAO for reading/writing [TripAttachmentEntity] rows.
 */
@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentDao: TripAttachmentDao,
    private val syncRepository: SyncRepository,
    private val evidenceSyncStore: EvidenceSyncStore,
) : AttachmentRepository {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    private val tag = "AttachmentRepository"

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    override suspend fun addAttachment(
        tripId: Int,
        sourceUri: Uri,
        mimeType: String,
        fileName: String,
    ): TripAttachment = withContext(Dispatchers.IO) {
        require(mimeType.isNotBlank()) { "mimeType must not be blank" }
        require(tripId > 0) { "tripId must be a valid positive integer" }

        // Copy file into internal storage.
        val destFile = copyToInternalStorage(tripId, sourceUri, fileName)

        val entity = TripAttachmentEntity(
            tripId = tripId,
            uri = destFile.toURI().toString(),
            mimeType = mimeType,
            fileName = fileName,
            createdAt = System.currentTimeMillis(),
        )
        val rowId = attachmentDao.insertAttachment(entity)

        // Reload the row to get the auto-generated PK.
        val saved = attachmentDao.getAttachmentById(rowId.toInt())
            ?: throw IllegalStateException("Attachment insert returned rowId $rowId but row not found")

        syncTripAttachments(tripId)
        saved.toDomain()
    }

    override suspend fun deleteAttachment(attachment: TripAttachment): Unit =
        withContext(Dispatchers.IO) {
            // Remove physical file first; failure is non-fatal.
            deleteFile(attachment.fileUri)
            attachmentDao.deleteAttachment(attachment.id)
            syncTripAttachments(attachment.tripId)
        }

    override suspend fun deleteAllAttachmentsForTrip(tripId: Int): Unit =
        withContext(Dispatchers.IO) {
            val rows = attachmentDao.getAttachmentsForTripOnce(tripId)
            rows.forEach { deleteFile(it.uri) }
            attachmentDao.deleteAllAttachmentsForTrip(tripId)
            syncRepository.enqueueDelete(SyncEntityTypes.TRIP_ATTACHMENTS, tripId.toString())
        }

    // ───────────────────────────────────────────────────────────────────────────
    // Reads
    // ───────────────────────────────────────────────────────────────────────────

    override fun getAttachmentsForTrip(tripId: Int): Flow<List<TripAttachment>> =
        attachmentDao.getAttachmentsForTrip(tripId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAttachmentsForTripOnce(tripId: Int): List<TripAttachment> =
        withContext(Dispatchers.IO) {
            attachmentDao.getAttachmentsForTripOnce(tripId).map { it.toDomain() }
        }

    // ───────────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Copies the file at [sourceUri] into `<filesDir>/attachments/<tripId>/`
     * using the content resolver, and returns the destination [File].
     */
    private fun copyToInternalStorage(tripId: Int, sourceUri: Uri, fileName: String): File {
        val safeFileName = "${System.currentTimeMillis()}_${fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
        val dir = File(context.filesDir, "attachments/$tripId").also { it.mkdirs() }
        val dest = File(dir, safeFileName)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Could not open input stream for URI: $sourceUri")

        return dest
    }

    /**
     * Deletes the file at [uriString] from internal storage.
     * Failures are logged but never re-thrown; the caller proceeds regardless.
     */
    private fun deleteFile(uriString: String) {
        try {
            val file = File(java.net.URI.create(uriString))
            if (file.exists()) file.delete()
        } catch (error: IllegalArgumentException) {
            Log.w(tag, "Failed to delete attachment file at $uriString: ${error.message}")
        } catch (error: SecurityException) {
            Log.w(tag, "Failed to delete attachment file at $uriString: ${error.message}")
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Entity → Domain mapping
    // ───────────────────────────────────────────────────────────────────────────

    private fun TripAttachmentEntity.toDomain() = TripAttachment(
        id = this.id,
        tripId = this.tripId,
        fileUri = this.uri,
        mimeType = this.mimeType,
        fileName = this.fileName,
        capturedAt = this.createdAt,
    )

    private suspend fun syncTripAttachments(tripId: Int) {
        if (!syncEnabled) return
        val attachments = attachmentDao.getAttachmentsForTripOnce(tripId).map { entity ->
            TripAttachmentSyncItem(
                entity = entity,
                remotePath = evidenceSyncStore.upload(entity.uri, attachmentRelativePath(entity)),
            )
        }
        syncRepository.enqueueUpsert(
            entityType = SyncEntityTypes.TRIP_ATTACHMENTS,
            entityId = tripId.toString(),
            payloadJson = attachments.toTripAttachmentsPayloadJson(),
        )
    }

    private fun attachmentRelativePath(entity: TripAttachmentEntity): String =
        "trip-attachments/${entity.tripId}/${entity.id}_${sanitizeFileName(entity.fileName)}"

    private fun sanitizeFileName(fileName: String): String =
        fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    constructor(
        context: Context,
        attachmentDao: TripAttachmentDao,
    ) : this(context, attachmentDao, NoOpSyncRepository, NoOpEvidenceSyncStore)
}
