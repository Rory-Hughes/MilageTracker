package com.example.mileagetracker.domain.model

/**
 * Domain model for a file attached to a drive trip.
 *
 * This is a pure data class that lives in the domain layer and is
 * mapped from [TripAttachmentEntity] by [AttachmentRepositoryImpl].
 * The UI and use-cases always operate on this type — never on the
 * entity directly.
 *
 * ## Field mapping to entity
 * | Domain field    | Entity column  |
 * |-----------------|----------------|
 * | [id]            | `id`           |
 * | [tripId]        | `trip_id`      |
 * | [fileUri]       | `uri`          |
 * | [mimeType]      | `mime_type`    |
 * | [fileName]      | `file_name`    |
 * | [capturedAt]    | `created_at`   |
 *
 * @param id         Row primary key; 0 before the entity is persisted.
 * @param tripId     ID of the [DriveEntity] this attachment belongs to.
 * @param fileUri    Android content URI string for the stored file.
 * @param mimeType   MIME type, e.g. `"image/jpeg"` or `"application/pdf"`.
 * @param fileName   Display name shown in the UI attachment strip.
 * @param capturedAt Unix epoch millis when the attachment was created.
 */
data class TripAttachment(
    val id: Int,
    val tripId: Int,
    val fileUri: String,
    val mimeType: String,
    val fileName: String,
    val capturedAt: Long,
) {
    /** True when this attachment is an image type (jpeg, png, webp, gif). */
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    /** True when this attachment is a PDF document. */
    val isPdf: Boolean
        get() = mimeType == "application/pdf"
}
