package com.example.mileagetracker.domain.model

/**
 * Domain model representing a locked reporting period.
 * A locked period prevents further edits to trips/expenses within that month (or year when
 * [periodMonth] is null).
 */
data class PeriodLock(
    val id: Long = 0,
    val periodYear: Int,
    /** Null means the entire year is locked. */
    val periodMonth: Int? = null,
    val lockedByUserId: Long,
    val lockedAt: Long,
)
