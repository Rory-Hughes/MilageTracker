package com.example.mileagetracker.domain.usecase.attachment

import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.repository.AttachmentRepository
import javax.inject.Inject

/**
 * Deletes an attachment from both the database and internal file storage.
 *
 * Delegates entirely to [AttachmentRepository.deleteAttachment] which handles
 * the two-step process: remove the physical file first, then delete the
 * metadata row.  File deletion failures are suppressed by the repository;
 * the row is always removed.
 *
 * @param attachmentRepository Repository that handles delete logic.
 */
class DeleteAttachmentUseCase @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
) {
    /**
     * @param attachment The [TripAttachment] to delete.
     */
    suspend operator fun invoke(attachment: TripAttachment) {
        attachmentRepository.deleteAttachment(attachment)
    }
}
