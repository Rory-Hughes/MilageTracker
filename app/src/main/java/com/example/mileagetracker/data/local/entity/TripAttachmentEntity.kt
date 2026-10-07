package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores metadata for a file attached to a drive trip.
 *
 * Typical attachments include:
 * - A photo of a parking receipt or toll ticket
 * - A scanned expense form or mileage log page
 * - Any PDF or image the user wants to associate with a trip for CRA purposes
 *
 * ## Storage model
 * This entity stores **metadata only**.  The actual file bytes are managed by
 * the Android content provider system (e.g. `FileProvider`, `MediaStore`, or
 * a cloud URI).  [uri] holds the content URI string that uniquely identifies
 * the file on the device.  The app must hold a persistent URI permission
 * grant (via `ContentResolver.takePersistableUriPermission`) before storing
 * the URI here, otherwise the URI becomes inaccessible after a reboot.
 *
 * ## Immutability
 * Attachment metadata rows are effectively immutable after insert.  If the
 * user replaces a file, the old row is deleted and a new row is inserted.
 * There is no update path.
 *
 * ## Deletion behaviour
 * Deleting an attachment row removes only the metadata.  Callers in the
 * domain layer ([com.example.mileagetracker.data.local.dao.TripAttachmentDao] via a use-case) are responsible for
 * also releasing the URI permission and, where appropriate, deleting the
 * underlying file from storage before removing the row.
 */
@Entity(
    tableName = "trip_attachments",
    indices = [
        Index("trip_id"),
        Index("created_at"),
    ],
)
data class TripAttachmentEntity(

    // ── Identity ──────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // ── Trip reference ────────────────────────────────────────────────────────

    /**
     * The [DriveEntity.id] this attachment belongs to.
     *
     * Note: @ForeignKey is intentionally omitted here and will be declared in
     * Task 6.1 once all entities are registered together in [MileageDatabase]
     * and a migration exists.  Referential integrity is enforced by the
     * attachment use-cases in the domain layer.
     */
    @ColumnInfo(name = "trip_id")
    val tripId: Int,

    // ── File reference ────────────────────────────────────────────────────────

    /**
     * Android content URI string pointing to the attached file, e.g.
     * `"content://com.example.mileagetracker.provider/attachments/receipt_1234.jpg"`.
     *
     * The URI must have a persistent permission grant before this row is
     * inserted.  Call `ContentResolver.takePersistableUriPermission` with
     * `Intent.FLAG_GRANT_READ_URI_PERMISSION` (and write if needed) prior
     * to inserting, otherwise the URI becomes inaccessible after a reboot.
     */
    @ColumnInfo(name = "uri")
    val uri: String,

    /**
     * MIME type of the attached file, e.g. `"image/jpeg"`, `"image/png"`,
     * `"application/pdf"`.
     *
     * Used to choose the correct viewer intent when the user taps the
     * attachment, and to render an appropriate thumbnail placeholder in the
     * UI before the file is loaded.
     */
    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    /**
     * Display name of the file, e.g. `"receipt_2026-05-17.jpg"`.
     *
     * This is the name shown in the UI attachment list.  It is captured at
     * insert time from the content resolver (`OpenableColumns.DISPLAY_NAME`)
     * and stored here so the UI can show a meaningful label even if the
     * underlying URI becomes temporarily unavailable.
     */
    @ColumnInfo(name = "file_name")
    val fileName: String,

    // ── Timestamp ─────────────────────────────────────────────────────────────

    /**
     * Unix epoch millis when this attachment metadata row was inserted.
     * Always set at insert time; must never be updated.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
