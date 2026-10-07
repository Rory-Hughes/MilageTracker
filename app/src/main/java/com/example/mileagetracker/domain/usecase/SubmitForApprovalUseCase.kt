package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import javax.inject.Inject

/**
 * Creates a new [ApprovalRequest] for a trip or expense and persists it as PENDING.
 *
 * Exactly one of [tripId] / [expenseId] must be non-null; passing both or neither throws
 * [IllegalArgumentException].
 */
class SubmitForApprovalUseCase @Inject constructor(
    private val approvalRepository: ApprovalRepository,
) {
    suspend operator fun invoke(
        submittedByUserId: Long,
        tripId: Long? = null,
        expenseId: Long? = null,
    ): Result<Long> {
        if ((tripId == null) == (expenseId == null)) {
            return Result.failure(
                IllegalArgumentException("Provide exactly one of tripId or expenseId.")
            )
        }
        val request = ApprovalRequest(
            tripId = tripId,
            expenseId = expenseId,
            submittedByUserId = submittedByUserId,
            submittedAt = System.currentTimeMillis(),
            status = ApprovalStatus.PENDING,
        )
        return runCatching { approvalRepository.insertRequest(request) }
    }
}
