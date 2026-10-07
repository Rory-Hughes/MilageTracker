package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ApprovalStatus
import javax.inject.Inject

/**
 * Updates an [ApprovalRequest] with the manager's decision.
 *
 * @param requestId       The ID of the [ApprovalRequest] to update.
 * @param reviewerUserId  The manager's user ID.
 * @param newStatus       One of [ApprovalStatus.APPROVED], [REJECTED], or [RETURNED].
 * @param comment         Optional review comment (required for REJECTED / RETURNED).
 */
class ApproveRejectTripUseCase @Inject constructor(
    private val approvalRepository: ApprovalRepository,
) {
    suspend operator fun invoke(
        requestId: Long,
        reviewerUserId: Long,
        newStatus: ApprovalStatus,
        comment: String? = null,
    ): Result<Unit> = runCatching {
        validateDecision(newStatus, comment)
        val existing = approvalRepository.getById(requestId)
            ?: throw NoSuchElementException("ApprovalRequest $requestId not found.")

        approvalRepository.updateRequest(
            existing.copy(
                status = newStatus,
                reviewedByUserId = reviewerUserId,
                reviewedAt = System.currentTimeMillis(),
                reviewComment = comment,
            ),
        )
    }
}

private fun validateDecision(
    newStatus: ApprovalStatus,
    comment: String?,
) {
    require(newStatus != ApprovalStatus.PENDING) {
        "Cannot set status back to PENDING."
    }
    require(
        !requiresReviewComment(newStatus) || !comment.isNullOrBlank(),
    ) {
        "A comment is required when rejecting or returning a request."
    }
}

private fun requiresReviewComment(newStatus: ApprovalStatus): Boolean =
    newStatus == ApprovalStatus.REJECTED || newStatus == ApprovalStatus.RETURNED
