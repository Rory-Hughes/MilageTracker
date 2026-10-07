package com.example.mileagetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus

@Entity(tableName = "approval_requests")
data class ApprovalRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long?,
    val expenseId: Long?,
    val submittedByUserId: Long,
    val submittedAt: Long,
    val status: String,
    val reviewedByUserId: Long?,
    val reviewedAt: Long?,
    val reviewComment: String?,
) {
    fun toDomain() = ApprovalRequest(
        id = id,
        tripId = tripId,
        expenseId = expenseId,
        submittedByUserId = submittedByUserId,
        submittedAt = submittedAt,
        status = ApprovalStatus.valueOf(status),
        reviewedByUserId = reviewedByUserId,
        reviewedAt = reviewedAt,
        reviewComment = reviewComment,
    )

    companion object {
        fun fromDomain(d: ApprovalRequest) = ApprovalRequestEntity(
            id = d.id,
            tripId = d.tripId,
            expenseId = d.expenseId,
            submittedByUserId = d.submittedByUserId,
            submittedAt = d.submittedAt,
            status = d.status.name,
            reviewedByUserId = d.reviewedByUserId,
            reviewedAt = d.reviewedAt,
            reviewComment = d.reviewComment,
        )
    }
}
