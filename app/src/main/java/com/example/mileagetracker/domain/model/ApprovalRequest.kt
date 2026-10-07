package com.example.mileagetracker.domain.model

/**
 * Domain model representing a driver's request for a trip or expense to be reviewed.
 */
data class ApprovalRequest(
    val id: Long = 0,
    /** Exactly one of tripId / expenseId will be non-null. */
    val tripId: Long? = null,
    val expenseId: Long? = null,
    val submittedByUserId: Long,
    val submittedAt: Long,
    val status: ApprovalStatus,
    val reviewedByUserId: Long? = null,
    val reviewedAt: Long? = null,
    val reviewComment: String? = null,
)

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    RETURNED,
}
