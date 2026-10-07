package com.example.mileagetracker.domain.usecase.attachment

import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.repository.AttachmentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns a live [Flow] of all [TripAttachment] rows for a given trip,
 * ordered by [TripAttachment.capturedAt] ascending.
 *
 * The [AttachmentViewModel] collects this Flow so the thumbnail strip
 * updates reactively whenever an attachment is added or removed.
 *
 * @param attachmentRepository Repository that provides the underlying Flow.
 */
class GetAttachmentsForTripUseCase @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
) {
    /**
     * @param tripId The drive trip whose attachments to observe.
     * @return A [Flow] that emits the current list and any subsequent changes.
     */
    operator fun invoke(tripId: Int): Flow<List<TripAttachment>> =
        attachmentRepository.getAttachmentsForTrip(tripId)
}
