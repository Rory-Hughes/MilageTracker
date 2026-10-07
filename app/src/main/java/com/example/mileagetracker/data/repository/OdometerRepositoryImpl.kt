package com.example.mileagetracker.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.example.mileagetracker.data.local.dao.OdometerCaptureDao
import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.model.OdometerCaptureType
import com.example.mileagetracker.domain.repository.OdometerRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room + file-system backed implementation of [OdometerRepository].
 *
 * ## Photo storage
 * When [saveCapture] receives a [OdometerCapture] with a non-null
 * [OdometerCapture.photoUri] that is a `content://` camera temp URI,
 * the photo is copied into `files/odometer_photos/` inside the app's
 * internal private storage.  The internal `file://` URI is stored on the
 * entity so that the photo survives the camera temp file being cleaned up.
 *
 * If copying fails (e.g. the temp URI is already stale), the capture is
 * saved without a photo rather than throwing — this keeps the form flow
 * working even if the camera result is partially broken.
 *
 * ## Mapping
 * [OdometerCaptureEntity] ↔ [OdometerCapture] conversions are handled by
 * private extension functions at the bottom of this file.
 *
 * ## Task 5.2 additions
 * [getFirstCaptureForVehicleInYear] and [getLastCaptureForVehicleInYear]
 * delegate to the new year-bounded DAO queries to support annual odometer
 * open/close readings.
 */
@Singleton
class OdometerRepositoryImpl @Inject constructor(
    private val dao: OdometerCaptureDao,
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository,
    private val evidenceSyncStore: EvidenceSyncStore,
) : OdometerRepository {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    override suspend fun saveCapture(capture: OdometerCapture): OdometerCapture {
        val internalUri = capture.photoUri?.let { uriString ->
            copyPhotoToInternalStorage(uriString)
        }
        val entity = capture.toEntity().copy(
            photoUri = internalUri ?: capture.photoUri,
        )
        val rowId = dao.insert(entity)
        val saved = capture.copy(
            id = rowId.toInt(),
            photoUri = entity.photoUri,
        )
        syncCaptures(saved.vehicleId)
        return saved
    }

    override fun observeCapturesForVehicle(vehicleId: Int): Flow<List<OdometerCapture>> =
        dao.getForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    override suspend fun getLatestReading(vehicleId: Int): OdometerCapture? =
        dao.getLatestForVehicle(vehicleId)?.toDomain()

    // ── Task 5.2 ──────────────────────────────────────────────────────────────

    override suspend fun getFirstCaptureForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCapture? = withContext(Dispatchers.IO) {
        dao.getFirstCaptureForVehicleInYear(vehicleId, fromMillis, toMillis)?.toDomain()
    }

    override suspend fun getLastCaptureForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCapture? = withContext(Dispatchers.IO) {
        dao.getLastCaptureForVehicleInYear(vehicleId, fromMillis, toMillis)?.toDomain()
    }

    override suspend fun getOpeningEvidenceForVehicleYear(
        vehicleId: Int,
        taxYear: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCapture? = withContext(Dispatchers.IO) {
        dao.getOpeningCaptureForVehicleYear(vehicleId, taxYear)
            ?: dao.getClosingCaptureForVehicleYear(vehicleId, taxYear - 1)
            ?: dao.getFirstOtherCaptureForVehicleYear(vehicleId, taxYear, fromMillis, toMillis)
    }?.toDomain()

    override suspend fun getClosingEvidenceForVehicleYear(
        vehicleId: Int,
        taxYear: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCapture? = withContext(Dispatchers.IO) {
        dao.getClosingCaptureForVehicleYear(vehicleId, taxYear)
            ?: dao.getLastOtherCaptureForVehicleYear(vehicleId, taxYear, fromMillis, toMillis)
    }?.toDomain()

    // ── Photo helpers ──────────────────────────────────────────────────────────

    /**
     * Copies the photo at [sourceUriString] into
     * `<filesDir>/odometer_photos/<timestamp>.jpg`.
     *
     * @return The `file://` URI string of the internal copy, or `null` on failure.
     */
    private suspend fun copyPhotoToInternalStorage(sourceUriString: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "odometer_photos").also { it.mkdirs() }
                val dest = File(dir, "${System.currentTimeMillis()}.jpg")
                context.contentResolver
                    .openInputStream(sourceUriString.toUri())
                    ?.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
                dest.toURI().toString()
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun syncCaptures(vehicleId: Int) {
        if (!syncEnabled) return
        val items = dao.getForVehicleOnce(vehicleId).map { entity ->
            val remotePath = entity.photoUri?.let {
                evidenceSyncStore.upload(it, odometerRelativePath(entity))
            }
            OdometerCaptureSyncItem(entity = entity, remotePath = remotePath)
        }
        syncRepository.enqueueUpsert(
            entityType = SyncEntityTypes.ODOMETER_CAPTURES,
            entityId = vehicleId.toString(),
            payloadJson = items.toOdometerCaptureSyncPayloadJson(),
        )
    }

    private fun odometerRelativePath(entity: OdometerCaptureEntity): String =
        "odometer/${entity.vehicleId}/${entity.id}${photoExtension(entity.photoUri)}"

    private fun photoExtension(uriString: String?): String {
        val path = uriString?.toUri()?.lastPathSegment ?: return ".jpg"
        val extension = path.substringAfterLast('.', "")
        return if (extension.isBlank()) ".jpg" else ".$extension"
    }

    constructor(
        dao: OdometerCaptureDao,
        context: Context,
    ) : this(dao, context, NoOpSyncRepository, NoOpEvidenceSyncStore)
}

// ── Mapping extensions ─────────────────────────────────────────────────────

private fun OdometerCapture.toEntity() = OdometerCaptureEntity(
    id = id,
    vehicleId = vehicleId,
    captureDate = captureDate,
    readingKm = readingKm,
    taxYear = taxYear,
    photoUri = photoUri,
    captureType = captureType.name,
    boundaryType = boundaryType.name,
    notes = notes,
    createdAt = createdAt,
)

private fun OdometerCaptureEntity.toDomain() = OdometerCapture(
    id = id,
    vehicleId = vehicleId,
    captureDate = captureDate,
    readingKm = readingKm,
    taxYear = taxYear,
    photoUri = photoUri,
    captureType = runCatching { OdometerCaptureType.valueOf(captureType) }
        .getOrDefault(OdometerCaptureType.MANUAL),
    boundaryType = runCatching { OdometerBoundaryType.valueOf(boundaryType) }
        .getOrDefault(OdometerBoundaryType.OTHER),
    notes = notes,
    createdAt = createdAt,
)
